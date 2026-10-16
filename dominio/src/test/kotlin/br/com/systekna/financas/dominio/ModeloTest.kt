/* Porta de test/modelo.test.js: mesmos casos, mesmos números esperados. */
package br.com.systekna.financas.dominio

import br.com.systekna.financas.dominio.Modelo as M
import kotlin.test.*

class ModeloTest {
    private fun lanc(tipo: String, v: Long, cat: String = "Outros", autor: String? = null) =
        Lancamento(tipo, "x", v, "2026-10-07", cat, autorNome = autor)

    @Test fun `parseValor entende ponto de milhar sem vírgula`() {
        assertEquals(150000, M.parseValor("1.500"))
        assertEquals(150050, M.parseValor("1.500,50"))
        assertEquals(123456700, M.parseValor("1.234.567"))
        assertEquals(4590, M.parseValor("45,9"))
        assertEquals(4590, M.parseValor("R$ 45,90"))
        assertEquals(150050, M.parseValor("1500.5"))
        assertEquals(1, M.parseValor("0,01"))
        for (ruim in listOf("", "abc", "-10", "1,2,3", "1,234", "12.3456")) assertNull(M.parseValor(ruim), ruim)
    }

    @Test fun `dividirParcelas não perde centavos`() {
        assertEquals(listOf(334L, 333L, 333L), M.dividirParcelas(1000, 3))
        assertEquals(99999, M.dividirParcelas(99999, 7).sum())
    }

    @Test fun `addMes e mesesEntre atravessam o ano`() {
        assertEquals("2027-02", M.addMes("2026-11", 3))
        assertEquals("2025-12", M.addMes("2026-01", -1))
        assertEquals(6, M.mesesEntre("2026-10", "2027-04"))
    }

    @Test fun `mesDaFatura respeita fechamento e vencimento`() {
        assertEquals("2026-10", M.mesDaFatura("2026-10-04", 5, 12))
        assertEquals("2026-11", M.mesDaFatura("2026-10-05", 5, 12)) // no dia do fechamento já vai para a próxima
        assertEquals("2026-11", M.mesDaFatura("2026-10-20", 28, 5))
        assertEquals("2027-02", M.mesDaFatura("2026-12-29", 28, 5))
        assertEquals(M.DatasFatura("2026-10-28", "2026-11-05"), M.datasDaFatura(Cartao("k", "K", 0, 28, 5), "2026-11"))
        assertEquals(M.DatasFatura("2026-02-28", "2026-03-10"), M.datasDaFatura(Cartao("k", "K", 0, 31, 10), "2026-03"))
    }

    @Test fun `parcelas no mês e limite usado`() {
        val compra = Compra("c1", "x", 1000, "2026-10-01", "Compras", "k1", 3, "2026-10", "Celso")
        assertEquals(listOf(2 to 333L), M.parcelasNoMes(listOf(compra), "2026-11").map { it.parcela to it.valorParcela })
        assertEquals(0, M.parcelasNoMes(listOf(compra), "2027-01").size)
        assertEquals(0, M.parcelasNoMes(listOf(compra), "2026-11", "outro").size)
        assertEquals(666, M.limiteUsado(Cartao("k1", "K", 0, 5, 12, listOf("2026-10")), listOf(compra)))
    }

    @Test fun `resumoMes soma entradas, despesas e parcelas do cartão`() {
        val compra = Compra("c", "x", 3000, "2026-10-01", "Compras", "k", 1, "2026-10", "Celso")
        val r = M.resumoMes(
            listOf(lanc("entrada", 500000), lanc("despesa", 10000, "Mercado", "Ana")),
            listOf(M.ParcelaNoMes(compra, 1, 3000))
        )
        assertEquals(500000, r.entradas)
        assertEquals(13000, r.despesas)
        assertEquals(487000, r.saldo)
        assertEquals(listOf("Ana" to 10000L, "Celso" to 3000L), r.porPessoa)
    }

    @Test fun `dívidas, investimentos e metas`() {
        val d = listOf(
            Divida("A", "ativa", parcelasTotal = 48, parcelasPagas = 18, valorParcelaCentavos = 98000),
            Divida("B", "negativada", valorCentavos = 150000),
            Divida("C", "quitada", valorCentavos = 999)
        )
        assertEquals(M.ResumoDividas(30 * 98000L + 150000, 98000, 1), M.resumoDividas(d))
        val inv = M.resumoInvestimentos(listOf(Investimento("CDB", "a", 100000, 110000), Investimento("Cripto", "b", 50000, 40000)))
        assertEquals(0, inv.rendimento)
        assertEquals("CDB", inv.porTipo[0].first)
        val p = M.progressoMeta(Meta("Viagem", "Viagem", 2000000, 820000, "2027-10"), "2026-10")
        assertEquals(12, p.meses)
        assertEquals((1180000L + 11) / 12, p.porMes) // Math.ceil(1180000 / 12)
    }

    @Test fun `limpar valida o que grava e descarta o que é inválido`() {
        val ok = Validacao.limpar("lancamento", mapOf("tipo" to "despesa", "descricao" to " Mercado ", "valorCentavos" to 4590L, "data" to "2026-10-07", "categoria" to "Mercado", "extra" to "x"))
        assertEquals(mapOf("tipo" to "despesa", "descricao" to "Mercado", "valorCentavos" to 4590L, "data" to "2026-10-07", "categoria" to "Mercado"), ok)
        fun campo(tipo: String, m: Map<String, Any?>) = assertFailsWith<ErroValidacao> { Validacao.limpar(tipo, m) }.campo
        assertEquals("valorCentavos", campo("lancamento", ok + ("valorCentavos" to 0L)))
        assertEquals("data", campo("lancamento", ok + ("data" to "2026-02-30")))
        assertEquals("valorCentavos", campo("lancamento", ok + ("valorCentavos" to 45.9)))
        assertEquals("parcelasPagas", campo("divida", mapOf("credor" to "Banco", "status" to "ativa", "parcelasTotal" to 10L, "parcelasPagas" to 11L)))
        assertEquals("fechamentoDia", campo("cartao", mapOf("nome" to "X", "limiteCentavos" to 1L, "fechamentoDia" to 0L, "vencimentoDia" to 10L)))
        assertNull(Validacao.limparOuNulo("meta", mapOf("nome" to "Viagem")))
        val cartao = Validacao.limpar("cartao", mapOf("nome" to "Nu", "limiteCentavos" to 600000L, "fechamentoDia" to 5L, "vencimentoDia" to 12L, "pagas" to listOf("2026-11", "2026-10", "2026-10")))
        assertEquals(listOf("2026-10", "2026-11"), cartao["pagas"])
    }

    @Test fun `limpar guarda quem lançou (autorUid) e rejeita uid inválido`() {
        val base = mapOf("tipo" to "despesa", "descricao" to "Mercado", "valorCentavos" to 4590L, "data" to "2026-10-07", "categoria" to "Mercado")
        assertEquals("abcDEF123_-x", Validacao.limpar("lancamento", base + ("autorUid" to "abcDEF123_-x"))["autorUid"])
        assertFalse("autorUid" in Validacao.limpar("lancamento", base))
        assertEquals("autorUid", assertFailsWith<ErroValidacao> { Validacao.limpar("lancamento", base + ("autorUid" to "a/b")) }.campo)
    }

    // Só no Kotlin: a trava de 2 dias do firestore.rules, agora como regra do domínio.
    @Test fun `trava de 2 dias`() {
        val criado = 1_000_000L
        assertFalse(M.travado(criado, criado + M.TRAVA_MS))
        assertTrue(M.travado(criado, criado + M.TRAVA_MS + 1))
    }
}
