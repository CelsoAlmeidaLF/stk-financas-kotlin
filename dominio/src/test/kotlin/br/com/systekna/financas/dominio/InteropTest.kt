/* Interoperabilidade com o PWA.
   1) PWA → Kotlin: interop-js.json foi gerado pelo cripto-casa.js real (scripts/gerar-interop.mjs); o Kotlin abre tudo.
   2) Kotlin → PWA: o Kotlin cifra e grava build/interop-kotlin.json; scripts/conferir-interop.mjs abre com o JS. */
package br.com.systekna.financas.dominio

import br.com.systekna.financas.dominio.CriptoCasa as C
import kotlinx.serialization.json.*
import java.io.File
import kotlin.test.*

class InteropTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val js: JsonObject = Json.parseToJsonElement(
        InteropTest::class.java.getResource("/interop-js.json")!!.readText()
    ).jsonObject
    private fun txt(o: JsonObject, k: String) = o[k]!!.jsonPrimitive.content
    private val casaId = txt(js, "casaId")
    private val dek = txt(js, "dek")
    private val palavras = js["palavras"]!!.jsonArray.map { it.jsonPrimitive.content }

    @Test fun `vetores BIP39, hash e selo iguais aos do PWA`() {
        for (v in js["vetores"]!!.jsonArray.map { it.jsonObject }) {
            val e = txt(v, "entropiaHex").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            assertEquals(v["palavras"]!!.jsonArray.map { it.jsonPrimitive.content }, C.palavrasDaEntropia(e))
            assertEquals(txt(v, "hash"), C.hashDaEntropia(e))
            assertEquals(txt(v, "selo"), C.seloDe(casaId, txt(v, "hash")))
        }
        assertEquals(txt(js, "hash"), C.hashDaEntrada(palavras.joinToString(" ")))
        assertEquals(txt(js, "kid"), C.kidDe(dek))
    }

    @Test fun `Kotlin abre a casa criada pelo PWA (palavras, hash, código e certificado)`() {
        val cripto = json.decodeFromJsonElement<C.Config>(js["cripto"]!!)
        assertEquals(dek, C.abrirComPalavras(casaId, cripto, palavras.joinToString(" ")))
        assertEquals(dek, C.abrirComCodigo(casaId, cripto, txt(js, "codigo")))
        val env = js["envelope"]!!.jsonObject
        val criptoEnv = json.decodeFromJsonElement<C.Config>(env["cripto"]!!)
        val cert = C.abrirArquivoCertificado(env["arquivo"]!!.jsonObject, txt(env, "senha")) // senha com "ç" e "ã"
        assertEquals(txt(env, "hash"), cert.hash)
        assertEquals(dek, C.abrirComCertificado(casaId, criptoEnv, cert.cert))
        assertEquals(dek, C.abrirComCodigo(casaId, criptoEnv, txt(env, "codigo")))
    }

    @Test fun `Kotlin decifra e valida um lançamento gravado pelo PWA`() {
        val reg = js["registro"]!!.jsonObject
        val caixa = json.decodeFromJsonElement<Caixa>(reg["caixa"]!!)
        val aberto = C.abrir(C.chaveDados(dek), caixa, txt(reg, "contexto"))
        assertEquals(reg["valor"], aberto)
        val l = Lancamento.de(Validacao.limpar("lancamento", JsonMapa.mapa(aberto)))
        assertEquals("Pão de açúcar", l.descricao)
        assertEquals(4590, l.valorCentavos)
        assertFails { C.abrir(C.chaveDados(dek), caixa, C.aad(casaId, "lancamentos", "outroDoc")) }
    }

    @Test fun `Kotlin cifra para o PWA abrir`() {
        val casa = "casaKotlin01"
        val p = C.novasPalavras(casa)
        val nova = C.criarCriptoCasa(casa, p)
        val env = C.novoEnvelope(casa, nova.cripto, nova.dek, "senha-ção-kotlin", casaNome = "Casa Kotlin")
        val valor = JsonMapa.paraJson(Lancamento("entrada", "Salário de outubro", 512345, "2026-10-05", "Salário", autorNome = "Celso").paraMapa())
        val contexto = C.aad(casa, "lancamentos", "docK1")
        val saida = buildJsonObject {
            put("casaId", casa); put("palavras", JsonArray(p.palavras.map(::JsonPrimitive))); put("hash", p.hash)
            put("codigo", nova.codigo); put("dek", nova.dek); put("cripto", Json.encodeToJsonElement(nova.cripto))
            put("envelope", buildJsonObject {
                put("senha", "senha-ção-kotlin"); put("arquivo", env.arquivo); put("codigo", env.codigo); put("cripto", Json.encodeToJsonElement(env.cripto))
            })
            put("registro", buildJsonObject { put("contexto", contexto); put("caixa", Json.encodeToJsonElement(C.selar(C.chaveDados(nova.dek), valor, contexto))); put("valor", valor) })
        }
        System.getProperty("interop.saida")?.let { File(it).apply { parentFile.mkdirs() }.writeText(saida.toString()) }
    }
}
