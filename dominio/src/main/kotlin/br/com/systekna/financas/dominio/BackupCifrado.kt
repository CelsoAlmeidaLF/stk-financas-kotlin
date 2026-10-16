/* Backup cifrado: arquivo JSON com todos os lançamentos, cifrado com uma senha escolhida na hora de exportar.
   - PBKDF2-HMAC-SHA256 (600 mil iterações) da senha → AES-GCM 256 sobre a lista de lançamentos.
   - Sem a senha o arquivo não abre; qualquer alteração no arquivo também faz a abertura falhar (GCM).
   - Na volta, cada lançamento passa pela Validacao do domínio: um arquivo adulterado não injeta dado inválido.
   A senha do backup é independente do PIN: o arquivo abre em outro aparelho, com outro PIN. */
package br.com.systekna.financas.dominio

import kotlinx.serialization.json.*
import java.time.Instant
import javax.crypto.Cipher

object BackupCifrado {
    const val FORMATO = "financas-casa-kotlin-backup"
    const val VERSAO = 1
    private const val CTX = "financas-kotlin:v1:backup"
    const val SENHA_MINIMA = 8

    data class Item(val lancamento: Lancamento, val criadoEm: Long)

    fun senhaValida(senha: String) = senha.length >= SENHA_MINIMA
    private fun erro(msg: String, code: String? = "BACKUP_INVALIDO") = ErroCripto(code, msg)

    fun gerar(itens: List<Item>, senha: String, iteracoes: Int = CriptoCasa.ITERACOES, agora: Instant = Instant.now()): String {
        if (!senhaValida(senha)) throw erro("A senha do backup precisa de pelo menos $SENHA_MINIMA caracteres.", "SENHA_FRACA")
        val lista = JsonArray(itens.map { JsonMapa.paraJson(it.lancamento.paraMapa() + ("criadoEm" to it.criadoEm)) })
        val salt = CriptoCasa.aleatorio(16); val iv = CriptoCasa.aleatorio(12)
        val ct = CriptoCasa.gcm(Cipher.ENCRYPT_MODE, CriptoCasa.kek(senha, salt, iteracoes), iv, CTX, lista.toString().toByteArray(Charsets.UTF_8))
        return buildJsonObject {
            put("formato", FORMATO); put("versao", VERSAO); put("criadoEm", agora.toString())
            put("registros", itens.size); put("iter", iteracoes)
            put("caixa", buildJsonObject { put("salt", CriptoCasa.b64(salt)); put("iv", CriptoCasa.b64(iv)); put("ct", CriptoCasa.b64(ct)) })
        }.toString()
    }

    fun abrir(texto: String, senha: String): List<Item> {
        val arq = runCatching { Json.parseToJsonElement(texto).jsonObject }.getOrNull() ?: throw erro("Este arquivo não é um backup do Finanças da Casa.")
        fun txt(o: JsonObject?, k: String) = (o?.get(k) as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (txt(arq, "formato") != FORMATO) throw erro("Este arquivo não é um backup do Finanças da Casa.")
        val versao = (arq["versao"] as? JsonPrimitive)?.intOrNull ?: throw erro("Backup sem versão.")
        if (versao > VERSAO) throw erro("Backup de uma versão mais nova do app. Atualize o app primeiro.")
        val iter = (arq["iter"] as? JsonPrimitive)?.intOrNull?.takeIf { it in 1..10_000_000 } ?: throw erro("Backup corrompido.")
        val caixa = arq["caixa"] as? JsonObject
        val salt = txt(caixa, "salt"); val iv = txt(caixa, "iv"); val ct = txt(caixa, "ct")
        if (salt == null || iv == null || ct == null) throw erro("Backup corrompido.")
        val claro = try {
            CriptoCasa.gcm(Cipher.DECRYPT_MODE, CriptoCasa.kek(senha, CriptoCasa.unb64(salt), iter), CriptoCasa.unb64(iv), CTX, CriptoCasa.unb64(ct))
        } catch (_: Exception) { throw erro("Senha do backup incorreta (ou arquivo alterado).", "SEGREDO_INCORRETO") }
        val lista = runCatching { Json.parseToJsonElement(claro.toString(Charsets.UTF_8)).jsonArray }.getOrNull() ?: throw erro("Backup corrompido.")
        return lista.mapIndexed { i, e ->
            val m = runCatching { JsonMapa.mapa(e) }.getOrNull() ?: throw erro("Registro ${i + 1} do backup é inválido.")
            val criadoEm = (m["criadoEm"] as? Long)?.takeIf { it > 0 } ?: throw erro("Registro ${i + 1} do backup é inválido.")
            val limpo = try { Validacao.limpar("lancamento", m - "criadoEm") }
                        catch (v: IllegalArgumentException) { throw erro("Registro ${i + 1} do backup é inválido: ${v.message}") }
            Item(Lancamento.de(limpo), criadoEm)
        }
    }
}
