/* Porta de test/cripto-casa.test.js: mesmos casos. */
package br.com.systekna.financas.dominio

import br.com.systekna.financas.dominio.CriptoCasa as C
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.test.*

class CriptoCasaTest {
    private inline fun code(bloco: () -> Unit): String? = assertFailsWith<ErroCripto> { bloco() }.code

    @Test fun `palavras BIP39 - vetores conhecidos (128 bits + checagem)`() {
        assertEquals(List(11) { "abacate" } + C.PALAVRAS[3], C.palavrasDaEntropia(ByteArray(16)))
        assertEquals(List(11) { "zumbido" } + C.PALAVRAS[2037], C.palavrasDaEntropia(ByteArray(16) { -1 }))
        val ida = C.aleatorio(16)
        assertContentEquals(ida, C.entropiaDasPalavras(C.palavrasDaEntropia(ida).joinToString(" ")))
    }

    @Test fun `palavras - aceita maiúsculas, acentos e 4 letras, recusa erro de digitação e ordem trocada`() {
        val (palavras, hash) = C.novasPalavras("c")
        val misturada = palavras.mapIndexed { i, w -> if (i % 2 == 1) w.take(4).uppercase() else w }.joinToString("   ")
        assertEquals(hash, C.hashDaEntrada("  $misturada\n"))
        assertEquals(hash, C.hashDaEntrada(palavras.joinToString(" ").replaceFirst("a", "á")))
        assertEquals(hash, C.hashDaEntrada(hash.lowercase().replace("-", " "))) // a própria hash também entra
        assertEquals(11, assertFailsWith<ErroCripto> { C.hashDaEntrada(palavras.take(11).joinToString(" ")) }.lidas)
        assertContains(assertFailsWith<ErroCripto> { C.hashDaEntrada((listOf("xyzw") + palavras.drop(1)).joinToString(" ")) }.message!!, "1ª palavra")
        val trocada = listOf(palavras[1], palavras[0]) + palavras.drop(2)
        if (trocada != palavras) {
            val e = runCatching { C.hashDaEntrada(trocada.joinToString(" ")) }.exceptionOrNull()
            // Como no PWA: a checagem de 4 bits deixa passar ~1 em 16 trocas; aí a hash sai diferente.
            if (e == null) assertNotEquals(hash, C.hashDaEntrada(trocada.joinToString(" "))) else assertEquals("PALAVRAS_INVALIDAS", (e as ErroCripto).code)
        }
    }

    @Test fun `palavras, hash e código abrem a mesma chave, outra casa ou palavras erradas não`() {
        val p = C.novasPalavras("casa1")
        assertTrue(Regex("^([A-Z2-9]{4}-){5}[A-Z2-9]{4}$").matches(p.hash))
        assertTrue(Regex("^[0-9A-F]{4}-[0-9A-F]{4}$").matches(p.selo))
        val (dek, cripto, codigo) = C.criarCriptoCasa("casa1", p)
        assertEquals(p.selo, cripto.selo)
        assertEquals(dek, C.abrirComPalavras("casa1", cripto, p.palavras.joinToString(" ")))
        assertEquals(dek, C.abrirComPalavras("casa1", cripto, p.hash))
        assertEquals(dek, C.abrirComCodigo("casa1", cripto, codigo.lowercase().replace("-", " ")))
        val outra = C.novasPalavras("casa1")
        assertEquals("SEGREDO_INCORRETO", code { C.abrirComPalavras("casa1", cripto, outra.palavras.joinToString(" ")) })
        assertEquals("SEGREDO_INCORRETO", code { C.abrirComPalavras("outra", cripto, p.hash) }) // presa à casa
        assertEquals("SEGREDO_INCORRETO", code { C.abrirComCodigo("casa1", cripto, C.novoCodigo()) })
        val doc = Json.encodeToString(cripto)
        for (segredo in listOf(dek, p.hash, p.palavras.joinToString(" "), codigo)) assertFalse(segredo in doc, "nada em claro no Firestore")
    }

    @Test fun `frase antiga (casa sem selo) não abre mais, o código de recuperação abre`() {
        val p = C.novasPalavras("c")
        val (dek, cripto, codigo) = C.criarCriptoCasa("c", p)
        val antiga = cripto.copy(selo = null)
        assertContains(assertFailsWith<ErroCripto> { C.abrirComPalavras("c", antiga, p.hash) }.message!!, "desativada")
        assertEquals(dek, C.abrirComCodigo("c", antiga, codigo))
    }

    @Test fun `trocar palavras e gerar novo código mantêm a mesma chave`() {
        val p1 = C.novasPalavras("c")
        val (dek, cripto, codigo) = C.criarCriptoCasa("c", p1)
        val p2 = C.novasPalavras("c")
        val c2 = C.trocarPalavras("c", cripto, dek, p2)
        assertEquals(p2.selo, c2.selo)
        assertEquals(dek, C.abrirComPalavras("c", c2, p2.palavras.joinToString(" ")))
        assertFailsWith<ErroCripto> { C.abrirComPalavras("c", c2, p1.hash) }
        assertEquals(dek, C.abrirComCodigo("c", c2, codigo)) // código antigo continua até ser trocado
        val (c3, novo) = C.novoCodigoRecuperacao("c", c2, dek)
        assertEquals(dek, C.abrirComCodigo("c", c3, novo))
        assertFailsWith<ErroCripto> { C.abrirComCodigo("c", c3, codigo) }
    }

    @Test fun `registro cifrado só abre no mesmo lugar (AAD)`() {
        val k = C.chaveDados(C.criarCriptoCasa("c", C.novasPalavras("c")).dek)
        val valor = buildJsonObject { put("descricao", "Aluguel"); put("valorCentavos", 185000) }
        val caixa = C.selar(k, valor, C.aad("c", "lancamentos", "d1"))
        assertFalse("Aluguel" in caixa.ct)
        assertEquals(valor, C.abrir(k, caixa, C.aad("c", "lancamentos", "d1")))
        assertFails { C.abrir(k, caixa, C.aad("c", "lancamentos", "d2")) }
        val adulterado = caixa.copy(ct = caixa.ct.dropLast(4) + if (caixa.ct.endsWith("AAAA")) "BBBB" else "AAAA")
        assertFails { C.abrir(k, adulterado, C.aad("c", "lancamentos", "d1")) }
    }

    @Test fun `kidDe é estável por chave e muda com a chave`() {
        val a = C.criarCriptoCasa("c", C.novasPalavras("c")); val b = C.criarCriptoCasa("c", C.novasPalavras("c"))
        assertTrue(Regex("^[0-9A-F]{8}$").matches(C.kidDe(a.dek)))
        assertEquals(C.kidDe(a.dek), C.kidDe(a.dek))
        assertNotEquals(C.kidDe(a.dek), C.kidDe(b.dek))
        assertEquals(C.kidDe(a.dek), a.cripto.kid) // gravado na casa
        assertEquals(a.cripto.kid, C.trocarPalavras("c", a.cripto, a.dek, C.novasPalavras("c")).kid) // trocar as palavras mantém a chave
    }

    @Test fun `envelope - certificado com senha abre a chave, código novo vale e o antigo não, envelope novo invalida o anterior`() {
        val p = C.novasPalavras("c")
        val (dek, cripto, antigo) = C.criarCriptoCasa("c", p)
        val e1 = C.novoEnvelope("c", cripto, dek, "senha-forte-1", casaNome = "Casa")
        assertTrue(Regex("^[0-9A-F]{4}(-[0-9A-F]{4}){3}$").matches(e1.hash))
        assertEquals(e1.hash, e1.cripto.cert)
        assertEquals(cripto.selo, e1.cripto.selo)
        assertFalse(dek in e1.arquivo.toString())
        val aberto = C.abrirArquivoCertificado(e1.arquivo.toString(), "senha-forte-1")
        assertEquals("c", aberto.casaId); assertEquals(e1.hash, aberto.hash)
        assertEquals(dek, C.abrirComCertificado("c", e1.cripto, aberto.cert))
        assertEquals(dek, C.abrirComCodigo("c", e1.cripto, e1.codigo))
        assertEquals("SEGREDO_INCORRETO", code { C.abrirComCodigo("c", e1.cripto, antigo) })
        assertEquals(dek, C.abrirComPalavras("c", e1.cripto, p.hash)) // palavras seguem valendo
        assertEquals("SEGREDO_INCORRETO", code { C.abrirArquivoCertificado(e1.arquivo, "errada!!") })
        assertEquals("CERT_INVALIDO", code { C.abrirArquivoCertificado(buildJsonObject { put("tipo", "outro") }, "x") })
        assertEquals("SEGREDO_INCORRETO", code { C.abrirComCertificado("outra", e1.cripto, aberto.cert) })
        assertFailsWith<ErroCripto> { C.novoEnvelope("c", cripto, dek, "curta") }
        val e2 = C.novoEnvelope("c", e1.cripto, dek, "senha-forte-2")
        assertEquals("SEGREDO_INCORRETO", code { C.abrirComCertificado("c", e2.cripto, aberto.cert) })
        // trocar palavras ou gerar código depois mantém o certificado
        val t = C.trocarPalavras("c", e2.cripto, dek, C.novasPalavras("c"))
        val c2 = C.abrirArquivoCertificado(e2.arquivo, "senha-forte-2").cert
        assertEquals(dek, C.abrirComCertificado("c", t, c2))
        assertEquals(dek, C.abrirComCertificado("c", C.novoCodigoRecuperacao("c", t, dek).first, c2))
    }
}
