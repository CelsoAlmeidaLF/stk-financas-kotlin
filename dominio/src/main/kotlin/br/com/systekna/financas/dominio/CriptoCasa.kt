/* Criptografia de ponta a ponta da casa. Porta de src/js/cripto-casa.js, byte a byte compatível com o PWA:
   o que um app cifra o outro abre (teste InteropTest).
   - Uma chave de dados (DEK, AES-GCM 256) por casa cifra todos os registros.
   - A DEK fica no Firestore só embrulhada: pelas palavras da casa e pelo código de recuperação (PBKDF2-SHA256).
   - Palavras da casa: 12 palavras BIP39 em português; delas sai a "hash da casa" (24 letras), que embrulha a DEK.
   - Envelope do administrador: certificado de 32 bytes, num arquivo cifrado com senha.
   - Cada registro é cifrado com AAD = "casaId/coleção/docId": um texto cifrado não pode ser trocado de lugar.
   Só JCA padrão (javax.crypto), disponível no JVM e no Android. O PBKDF2 é feito sobre HMAC-SHA256 com a senha
   em UTF-8, exatamente como o WebCrypto, sem depender de como cada provedor converte char[] em bytes. */
package br.com.systekna.financas.dominio

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class ErroCripto(val code: String?, mensagem: String, val lidas: Int? = null) : Exception(mensagem)

@Serializable
data class Caixa(val salt: String? = null, val iv: String, val ct: String)

object CriptoCasa {
    const val ITERACOES = 600_000

    val PALAVRAS: List<String> by lazy {
        CriptoCasa::class.java.getResourceAsStream("/bip39-pt.txt")!!.bufferedReader(Charsets.UTF_8).readLines().filter { it.isNotBlank() }
            .also { check(it.size == 2048) { "Lista BIP39 incompleta." } }
    }

    private val rnd = SecureRandom()
    private fun utf8(s: String) = s.toByteArray(Charsets.UTF_8)
    private fun b64(b: ByteArray): String = Base64.getEncoder().encodeToString(b)
    private fun unb64(s: String): ByteArray = Base64.getDecoder().decode(s)
    fun aleatorio(n: Int) = ByteArray(n).also { rnd.nextBytes(it) }
    private fun sha256(b: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(b)
    private fun hex(b: ByteArray) = b.joinToString("") { "%02X".format(it.toInt() and 0xFF) }

    // Código de recuperação: 20 bytes em base32 (sem 0/O/1/I), grupos de 4.
    private const val B32 = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private fun bitsDe(b: ByteArray) = b.joinToString("") { (it.toInt() and 0xFF).toString(2).padStart(8, '0') }
    private fun base32(b: ByteArray): String {
        val bits = bitsDe(b); val out = StringBuilder()
        var i = 0
        while (i + 5 <= bits.length) { out.append(B32[bits.substring(i, i + 5).toInt(2)]); i += 5 }
        return out.chunked(4).filter { it.length == 4 }.joinToString("-")
    }
    fun novoCodigo(): String = base32(aleatorio(20))
    fun normalizarCodigo(c: String?): String = (c ?: "").uppercase().replace(Regex("[^A-Z0-9]"), "").chunked(4).joinToString("-")

    /* ---------- palavras da casa (BIP39) ---------- */
    private fun semAcento(s: String) = Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("[\\u0300-\\u036f]"), "")
    private fun erroPalavras(msg: String, lidas: Int? = null) = ErroCripto("PALAVRAS_INVALIDAS", msg, lidas)

    fun palavrasDaEntropia(entropia: ByteArray): List<String> {
        require(entropia.size == 16) { "Entropia deve ter 16 bytes." }
        val bits = bitsDe(entropia) + bitsDe(sha256(entropia)).substring(0, 4)
        return List(12) { i -> PALAVRAS[bits.substring(i * 11, i * 11 + 11).toInt(2)] }
    }

    // Aceita maiúsculas, acentos, espaços extras e só as 4 primeiras letras de cada palavra.
    fun entropiaDasPalavras(texto: String?): ByteArray {
        val lidas = semAcento(texto ?: "").lowercase().split(Regex("[^a-z]+")).filter { it.isNotEmpty() }
        if (lidas.size != 12) throw erroPalavras("São 12 palavras; você digitou ${lidas.size}.", lidas.size)
        val bits = StringBuilder()
        lidas.forEachIndexed { i, p ->
            val n = PALAVRAS.indexOfFirst { w -> w == p || (p.length == 4 && w.startsWith(p)) }
            if (n < 0) throw erroPalavras("A ${i + 1}ª palavra (“$p”) não está na lista.")
            bits.append(n.toString(2).padStart(11, '0'))
        }
        val entropia = ByteArray(16) { i -> bits.substring(i * 8, i * 8 + 8).toInt(2).toByte() }
        if (bitsDe(sha256(entropia)).substring(0, 4) != bits.substring(128)) throw erroPalavras("As palavras não conferem. Veja se a ordem e a grafia estão certas.")
        return entropia
    }

    fun hashDaEntropia(entropia: ByteArray): String = base32(sha256(utf8("financas-casa:hash:v1:") + entropia).copyOf(15))
    fun ehHash(texto: String?): Boolean = Regex("^[A-HJ-NP-Z2-9]{24}$").matches((texto ?: "").uppercase().replace(Regex("[\\s-]"), ""))
    // Palavras ou hash → hash da casa (forma única, com hífens).
    fun hashDaEntrada(texto: String?): String = if (ehHash(texto)) normalizarCodigo(texto) else hashDaEntropia(entropiaDasPalavras(texto))

    fun seloDe(casaId: String, hash: String): String = hex(sha256(utf8("financas-casa:selo:$casaId:$hash")).copyOf(4)).let { it.take(4) + "-" + it.drop(4) }
    fun kidDe(dek: String): String = hex(sha256(utf8("financas-casa:kid:$dek")).copyOf(4))

    data class Palavras(val palavras: List<String>, val hash: String, val selo: String)
    fun novasPalavras(casaId: String): Palavras {
        val entropia = aleatorio(16)
        try {
            val hash = hashDaEntropia(entropia)
            return Palavras(palavrasDaEntropia(entropia), hash, seloDe(casaId, hash))
        } finally { entropia.fill(0) }
    }

    /* ---------- PBKDF2-HMAC-SHA256 → chave AES de 256 bits ---------- */
    private fun pbkdf2(segredo: ByteArray, salt: ByteArray, iteracoes: Int): ByteArray {
        // HMAC completa a chave com zeros: chave vazia (que o SecretKeySpec recusa) equivale a um byte 0x00.
        val chave = if (segredo.isEmpty()) byteArrayOf(0) else segredo
        val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(chave, "HmacSHA256")) }
        var u = mac.doFinal(salt + byteArrayOf(0, 0, 0, 1)) // um bloco só: 32 bytes = saída do SHA-256
        val t = u.copyOf()
        repeat(iteracoes - 1) {
            u = mac.doFinal(u)
            for (j in t.indices) t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
        }
        return t
    }
    private fun kek(segredo: String, salt: ByteArray, iteracoes: Int): SecretKey =
        SecretKeySpec(pbkdf2(utf8(segredo), salt, iteracoes), "AES")

    private fun gcm(modo: Int, chave: SecretKey, iv: ByteArray, aad: String, dados: ByteArray): ByteArray =
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(modo, chave, GCMParameterSpec(128, iv)); updateAAD(utf8(aad)); doFinal(dados)
        }

    private fun embrulhar(dekB64: String, segredo: String, contexto: String): Caixa {
        val salt = aleatorio(16); val iv = aleatorio(12)
        return Caixa(b64(salt), b64(iv), b64(gcm(Cipher.ENCRYPT_MODE, kek(segredo, salt, ITERACOES), iv, contexto, unb64(dekB64))))
    }
    private fun desembrulhar(caixa: Caixa?, segredo: String, contexto: String, iteracoes: Int): String {
        if (caixa?.salt == null) throw ErroCripto(null, "Configuração de criptografia inválida.")
        try {
            val raw = gcm(Cipher.DECRYPT_MODE, kek(segredo, unb64(caixa.salt), iteracoes), unb64(caixa.iv), contexto, unb64(caixa.ct))
            if (raw.size != 32) throw AEADBadTagException()
            return b64(raw)
        } catch (_: Exception) {
            throw ErroCripto(
                "SEGREDO_INCORRETO",
                when {
                    contexto.endsWith("frase") -> "Palavras ou hash da casa incorretos."
                    contexto.endsWith("admin") -> "Certificado do administrador incorreto."
                    else -> "Código de recuperação incorreto."
                }
            )
        }
    }
    private fun ctx(casaId: String, parte: String) = "financas-casa:v1:$casaId:$parte"

    /* Bloco `cripto` do documento da casa no Firestore. */
    @Serializable
    data class Config(
        val v: Int, val iter: Int, val selo: String? = null, val kid: String? = null,
        val frase: Caixa? = null, val recup: Caixa? = null, val admin: Caixa? = null, val cert: String? = null,
    )
    private fun conferir(c: Config?) {
        if (c == null || c.v != 1 || c.iter < 100_000) throw ErroCripto(null, "Configuração de criptografia inválida.")
    }
    private fun incorreto() = ErroCripto("SEGREDO_INCORRETO", "Palavras ou hash não são desta casa.")

    data class NovaCasa(val dek: String, val cripto: Config, val codigo: String)
    fun criarCriptoCasa(casaId: String, p: Palavras): NovaCasa {
        val raw = aleatorio(32)
        try {
            val dek = b64(raw); val codigo = novoCodigo()
            val cripto = Config(1, ITERACOES, p.selo, kidDe(dek), embrulhar(dek, p.hash, ctx(casaId, "frase")), embrulhar(dek, codigo, ctx(casaId, "recup")))
            return NovaCasa(dek, cripto, codigo)
        } finally { raw.fill(0) }
    }
    // Aceita as 12 palavras ou a hash da casa.
    fun abrirComPalavras(casaId: String, cripto: Config, entrada: String): String {
        conferir(cripto)
        if (cripto.selo == null) throw ErroCripto(null, "A frase antiga foi desativada. Use o código de recuperação.")
        val hash = hashDaEntrada(entrada)
        if (seloDe(casaId, hash) != cripto.selo) throw incorreto() // confere antes das 600 mil iterações
        return desembrulhar(cripto.frase, hash, ctx(casaId, "frase"), cripto.iter)
    }
    fun abrirComCodigo(casaId: String, cripto: Config, codigo: String): String {
        conferir(cripto)
        return desembrulhar(cripto.recup, normalizarCodigo(codigo), ctx(casaId, "recup"), cripto.iter)
    }
    fun trocarPalavras(casaId: String, cripto: Config, dek: String, p: Palavras): Config =
        cripto.copy(iter = ITERACOES, selo = p.selo, kid = kidDe(dek), frase = embrulhar(dek, p.hash, ctx(casaId, "frase")))
    fun novoCodigoRecuperacao(casaId: String, cripto: Config, dek: String): Pair<Config, String> {
        val codigo = novoCodigo()
        return cripto.copy(recup = embrulhar(dek, codigo, ctx(casaId, "recup"))) to codigo
    }

    /* ---------- envelope do administrador ---------- */
    private fun ctxEnvelope(casaId: String) = "financas-casa:envelope:v1:$casaId"
    private val ISO_MS = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)
    fun hashCertificado(casaId: String, cert: String): String =
        hex(sha256(utf8("financas-casa:cert:$casaId:$cert")).copyOf(8)).chunked(4).joinToString("-")

    data class Envelope(val cripto: Config, val arquivo: JsonObject, val codigo: String, val hash: String)
    fun novoEnvelope(casaId: String, cripto: Config, dek: String, senha: String, casaNome: String = "", emitidoPor: String = ""): Envelope {
        if (senha.length < 8) throw ErroCripto(null, "A senha do certificado deve ter pelo menos 8 caracteres.")
        val raw = aleatorio(32); val cert = b64(raw); raw.fill(0)
        val hash = hashCertificado(casaId, cert); val codigo = novoCodigo(); val emitido = ISO_MS.format(Instant.now())
        val salt = aleatorio(16); val iv = aleatorio(12)
        val conteudo = buildJsonObject { put("cert", cert); put("casaId", casaId); put("emitido", emitido) }.toString()
        val ct = gcm(Cipher.ENCRYPT_MODE, kek(senha, salt, ITERACOES), iv, ctxEnvelope(casaId), utf8(conteudo))
        val arquivo = buildJsonObject {
            put("tipo", "financas-casa/certificado-admin"); put("v", 1); put("casaId", casaId); put("casaNome", casaNome)
            put("emitidoPor", emitidoPor); put("emitido", emitido); put("hash", hash); put("iter", ITERACOES)
            put("salt", b64(salt)); put("iv", b64(iv)); put("ct", b64(ct))
        }
        val novo = cripto.copy(
            kid = kidDe(dek), cert = hash, admin = embrulhar(dek, cert, ctx(casaId, "admin")), recup = embrulhar(dek, codigo, ctx(casaId, "recup"))
        )
        return Envelope(novo, arquivo, codigo, hash)
    }

    data class CertificadoAberto(val cert: String, val casaId: String, val hash: String)
    fun abrirArquivoCertificado(arquivo: String, senha: String): CertificadoAberto =
        abrirArquivoCertificado(runCatching { Json.parseToJsonElement(arquivo) as? JsonObject }.getOrNull(), senha)
    fun abrirArquivoCertificado(a: JsonObject?, senha: String): CertificadoAberto {
        fun txt(k: String) = (a?.get(k) as? JsonPrimitive)?.takeIf { it.isString }?.content
        val iter = (a?.get("iter") as? JsonPrimitive)?.intOrNull
        if (a == null || txt("tipo") != "financas-casa/certificado-admin" || (a["v"] as? JsonPrimitive)?.intOrNull != 1 || txt("casaId") == null || iter == null || iter < 100_000)
            throw ErroCripto("CERT_INVALIDO", "Este arquivo não é um certificado do Finanças da Casa.")
        val casaId = txt("casaId")!!
        val dados = try {
            val raw = gcm(Cipher.DECRYPT_MODE, kek(senha, unb64(txt("salt")!!), iter), unb64(txt("iv")!!), ctxEnvelope(casaId), unb64(txt("ct")!!))
            Json.parseToJsonElement(raw.toString(Charsets.UTF_8)).jsonObject
        } catch (_: Exception) { throw ErroCripto("SEGREDO_INCORRETO", "Senha do certificado incorreta.") }
        val cert = (dados["cert"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if ((dados["casaId"] as? JsonPrimitive)?.contentOrNull != casaId || cert == null) throw ErroCripto("CERT_INVALIDO", "Certificado adulterado.")
        return CertificadoAberto(cert, casaId, hashCertificado(casaId, cert))
    }
    fun abrirComCertificado(casaId: String, cripto: Config, cert: String): String {
        conferir(cripto)
        if (cripto.admin == null || cripto.cert == null) throw ErroCripto("SEM_CERT", "Esta casa não tem certificado do administrador.")
        if (hashCertificado(casaId, cert) != cripto.cert)
            throw ErroCripto("SEGREDO_INCORRETO", "Este certificado não é o atual desta casa (foi gerado outro depois dele, ou é de outra casa).")
        return desembrulhar(cripto.admin, cert, ctx(casaId, "admin"), cripto.iter)
    }

    /* ---------- registros ---------- */
    fun chaveDados(dek: String): SecretKey = SecretKeySpec(unb64(dek), "AES")
    fun aad(casaId: String, colecao: String, docId: String) = "$casaId/$colecao/$docId"

    fun selar(chave: SecretKey, valor: JsonElement, contexto: String): Caixa {
        val iv = aleatorio(12)
        return Caixa(iv = b64(iv), ct = b64(gcm(Cipher.ENCRYPT_MODE, chave, iv, contexto, utf8(valor.toString()))))
    }
    fun abrir(chave: SecretKey, caixa: Caixa?, contexto: String): JsonElement {
        if (caixa == null) throw ErroCripto(null, "Registro sem criptografia.")
        val dados = gcm(Cipher.DECRYPT_MODE, chave, unb64(caixa.iv), contexto, unb64(caixa.ct))
        return Json.parseToJsonElement(dados.toString(Charsets.UTF_8))
    }
}
