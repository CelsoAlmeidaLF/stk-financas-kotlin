/* Regras do domínio, sem interface e sem banco. Porta de src/js/modelo.js do PWA.
   Valores sempre em centavos (Long). Datas em texto ISO ("2026-10-07") e meses em "2026-10", como no PWA,
   para que os registros cifrados sejam os mesmos nos dois apps. */
package br.com.systekna.financas.dominio

import java.text.NumberFormat
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

object Modelo {
    val CAT_DESPESA = listOf("Mercado", "Moradia", "Contas", "Transporte", "Saúde", "Educação", "Lazer", "Restaurante", "Compras", "Assinaturas", "Pets", "Outros")
    val CAT_ENTRADA = listOf("Salário", "Freelance", "Rendimentos", "Reembolso", "Presente", "Outros")
    val TIPOS_INVEST = listOf("CDB", "Tesouro Direto", "LCI/LCA", "Poupança", "Cripto", "Ações", "FIIs", "Previdência", "Outros")
    val TIPOS_META = listOf("Reserva de emergência", "Viagem", "Empreendimento", "Casa", "Carro", "Educação", "Outros")
    val STATUS_DIVIDA = listOf("ativa", "negativada", "quitada")
    const val MAX_PARCELAS = 48

    private val PT_BR: Locale = Locale.forLanguageTag("pt-BR")

    /* ---------- valores ---------- */
    private val SIMBOLOS = Regex("[R$\\s]")
    private val INVALIDO = Regex("[^\\d.,]")
    private val MILHAR = Regex("^\\d{1,3}\\.\\d{3}$")
    private val DECIMAL = Regex("^\\d+(\\.\\d{1,2})?$")

    // Aceita "1.500", "1.500,50", "1500,5", "1500.50", "R$ 45,90". Devolve centavos ou null.
    fun parseValor(v: String?): Long? {
        var s = (v ?: "").trim().replace(SIMBOLOS, "")
        if (s.isEmpty() || INVALIDO.containsMatchIn(s)) return null
        if (',' in s) s = s.replace(".", "").replaceFirst(",", ".")
        else if (s.count { it == '.' } > 1 || MILHAR.matches(s)) s = s.replace(".", "")
        if (!DECIMAL.matches(s)) return null
        val partes = s.split(".")
        val inteiro = partes[0].toLongOrNull() ?: return null
        return inteiro * 100 + partes.getOrElse(1) { "" }.padEnd(2, '0').toLong()
    }

    fun brl(c: Long?): String = NumberFormat.getCurrencyInstance(PT_BR).format((c ?: 0) / 100.0)
    fun valorCampo(c: Long?): String =
        if (c == null || c == 0L) "" else NumberFormat.getNumberInstance(PT_BR).apply { minimumFractionDigits = 2; maximumFractionDigits = 2 }.format(c / 100.0)

    // Divide um total em n parcelas; os centavos que sobram vão para as primeiras.
    fun dividirParcelas(total: Long, n: Int): List<Long> {
        val base = Math.floorDiv(total, n.toLong())
        val resto = total - base * n
        return List(n) { i -> base + if (i < resto) 1 else 0 }
    }

    /* ---------- datas ---------- */
    private fun pad(n: Int) = n.toString().padStart(2, '0')
    fun isoData(d: LocalDate): String = d.toString()
    fun hojeISO(): String = isoData(LocalDate.now())
    fun mesDe(iso: String): String = iso.substring(0, 7)
    private fun anoMes(mes: String): Pair<Int, Int> = mes.split("-").let { it[0].toInt() to it[1].toInt() }
    fun addMes(mes: String, n: Int): String {
        val (y, m) = anoMes(mes)
        val t = y * 12 + (m - 1) + n
        return "${Math.floorDiv(t, 12)}-${pad(Math.floorMod(t, 12) + 1)}"
    }
    fun diasNoMes(mes: String): Int = anoMes(mes).let { (y, m) -> YearMonth.of(y, m).lengthOfMonth() }
    private fun diaNoMes(mes: String, dia: Int) = "$mes-${pad(minOf(dia, diasNoMes(mes)))}"
    fun mesesEntre(de: String, ate: String): Int {
        val (ay, am) = anoMes(de); val (by, bm) = anoMes(ate)
        return (by - ay) * 12 + (bm - am)
    }
    fun mesLabel(mes: String): String = YearMonth.parse(mes).format(DateTimeFormatter.ofPattern("MMMM 'de' yyyy", PT_BR))
    fun ddmm(iso: String?): String = if (iso.isNullOrEmpty()) "" else "${iso.substring(8, 10)}/${iso.substring(5, 7)}"

    /* ---------- cartão ---------- */
    // Mês de vencimento da fatura em que cai uma compra. Compra no dia do fechamento ou depois vai para a próxima.
    fun mesDaFatura(dataISO: String, fechamentoDia: Int, vencimentoDia: Int): String {
        val dia = dataISO.substring(8, 10).toInt()
        val fechaEm = addMes(mesDe(dataISO), if (dia >= fechamentoDia) 1 else 0)
        return addMes(fechaEm, if (vencimentoDia <= fechamentoDia) 1 else 0)
    }

    data class DatasFatura(val fecha: String, val vence: String)
    fun datasDaFatura(cartao: Cartao, mesVencimento: String): DatasFatura {
        val mesFecha = addMes(mesVencimento, if (cartao.vencimentoDia <= cartao.fechamentoDia) -1 else 0)
        return DatasFatura(diaNoMes(mesFecha, cartao.fechamentoDia), diaNoMes(mesVencimento, cartao.vencimentoDia))
    }

    data class Parcela(val mes: String, val n: Int, val de: Int, val valorCentavos: Long)
    fun parcelasDaCompra(compra: Compra): List<Parcela> =
        dividirParcelas(compra.valorCentavos, compra.parcelas).mapIndexed { i, v -> Parcela(addMes(compra.mesInicial, i), i + 1, compra.parcelas, v) }

    data class ParcelaNoMes(val compra: Compra, val parcela: Int, val valorParcela: Long) {
        val categoria get() = compra.categoria
        val autorNome get() = compra.autorNome
    }
    // Parcelas de todas as compras que caem num mês de fatura (opcionalmente de um cartão).
    fun parcelasNoMes(compras: List<Compra>, mes: String, cartaoId: String? = null): List<ParcelaNoMes> =
        compras.mapNotNull { c ->
            if (cartaoId != null && c.cartaoId != cartaoId) return@mapNotNull null
            val i = mesesEntre(c.mesInicial, mes)
            if (i < 0 || i >= c.parcelas) null else ParcelaNoMes(c, i + 1, dividirParcelas(c.valorCentavos, c.parcelas)[i])
        }

    // Limite usado = todas as parcelas de faturas ainda não pagas.
    fun limiteUsado(cartao: Cartao, compras: List<Compra>): Long {
        val pagas = cartao.pagas.toSet()
        return compras.filter { it.cartaoId == cartao.id }
            .sumOf { c -> parcelasDaCompra(c).filter { it.mes !in pagas }.sumOf { it.valorCentavos } }
    }

    /* ---------- resumos ---------- */
    data class ResumoMes(
        val entradas: Long, val despesas: Long, val saldo: Long,
        val porCategoria: List<Pair<String, Long>>, val porPessoa: List<Pair<String, Long>>
    )
    fun resumoMes(lancamentos: List<Lancamento>, parcelas: List<ParcelaNoMes>): ResumoMes {
        var entradas = 0L; var despesas = 0L
        val porCategoria = LinkedHashMap<String, Long>(); val porPessoa = LinkedHashMap<String, Long>()
        fun somaDespesa(cat: String, pessoa: String?, v: Long) {
            despesas += v
            porCategoria.merge(cat, v, Long::plus)
            porPessoa.merge(pessoa?.ifEmpty { null } ?: "—", v, Long::plus)
        }
        for (l in lancamentos) if (l.tipo == "entrada") entradas += l.valorCentavos else somaDespesa(l.categoria, l.autorNome, l.valorCentavos)
        for (p in parcelas) somaDespesa(p.categoria, p.autorNome, p.valorParcela)
        fun ordenar(o: Map<String, Long>) = o.entries.map { it.key to it.value }.sortedByDescending { it.second }
        return ResumoMes(entradas, despesas, entradas - despesas, ordenar(porCategoria), ordenar(porPessoa))
    }

    fun saldoDivida(d: Divida): Long {
        if (d.status == "quitada") return 0
        val total = d.parcelasTotal ?: 0; val parcela = d.valorParcelaCentavos ?: 0
        if (total != 0 && parcela != 0L) return maxOf(0, total - (d.parcelasPagas ?: 0)) * parcela
        return d.valorCentavos ?: 0
    }
    data class ResumoDividas(val total: Long, val mensal: Long, val negativadas: Int)
    fun resumoDividas(dividas: List<Divida>): ResumoDividas {
        val abertas = dividas.filter { it.status != "quitada" }
        return ResumoDividas(
            total = abertas.sumOf { saldoDivida(it) },
            mensal = abertas.filter { it.status == "ativa" && (it.parcelasTotal ?: 0) != 0 && (it.parcelasPagas ?: 0) < it.parcelasTotal!! }
                .sumOf { it.valorParcelaCentavos ?: 0 },
            negativadas = dividas.count { it.status == "negativada" }
        )
    }

    data class ResumoInvestimentos(val aplicado: Long, val atual: Long, val rendimento: Long, val pct: Double, val porTipo: List<Pair<String, Long>>)
    fun resumoInvestimentos(lista: List<Investimento>): ResumoInvestimentos {
        val aplicado = lista.sumOf { it.aplicadoCentavos }; val atual = lista.sumOf { it.atualCentavos }
        val porTipo = LinkedHashMap<String, Long>()
        for (i in lista) porTipo.merge(i.tipo, i.atualCentavos, Long::plus)
        return ResumoInvestimentos(
            aplicado, atual, atual - aplicado, if (aplicado != 0L) (atual - aplicado).toDouble() / aplicado else 0.0,
            porTipo.entries.map { it.key to it.value }.sortedByDescending { it.second }
        )
    }

    data class ProgressoMeta(val pct: Double, val faltam: Long, val meses: Int?, val porMes: Long?)
    fun progressoMeta(m: Meta, mesAtual: String): ProgressoMeta {
        val guardado = m.guardadoCentavos ?: 0; val faltam = maxOf(0, m.alvoCentavos - guardado)
        val meses = m.prazo?.let { maxOf(0, mesesEntre(mesAtual, it)) }
        val porMes = meses?.takeIf { it > 0 }?.let { Math.floorDiv(faltam + it - 1, it.toLong()) }
        return ProgressoMeta(minOf(1.0, guardado.toDouble() / m.alvoCentavos), faltam, meses, porMes)
    }

    /* ---------- trava de 2 dias ----------
       Mesma regra do firestore.rules: lançamentos e compras não mudam nem saem 2 dias depois de criados. */
    const val TRAVA_MS = 2L * 24 * 60 * 60 * 1000
    fun travado(criadoEmMs: Long, agoraMs: Long = System.currentTimeMillis()): Boolean = agoraMs > criadoEmMs + TRAVA_MS
}
