/* Validação dos registros. Porta de limpar/ESQUEMAS de src/js/modelo.js.
   A mesma função valida o que se grava (erro com o campo) e limpa o que se lê (registro inválido é descartado).
   Trabalha sobre Map<String, Any?> porque é isso que sai do JSON decifrado; números chegam como Long ou Double. */
package br.com.systekna.financas.dominio

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

class ErroValidacao(val campo: String, mensagem: String) : IllegalArgumentException(mensagem)

object Validacao {
    private enum class T { TEXTO, ENUM, CENTAVOS, INT, DATA, MES, ID, MESES }
    private class Regra(
        val tipo: T, val obrig: Boolean = false, val max: Int = 0, val min: Int = 0,
        val positivo: Boolean = false, val valores: List<String> = emptyList()
    )
    private fun texto(max: Int, obrig: Boolean = false) = Regra(T.TEXTO, obrig, max = max)
    private fun int(min: Int, max: Int, obrig: Boolean = false) = Regra(T.INT, obrig, min = min, max = max)

    private val ESQUEMAS: Map<String, Map<String, Regra>> = mapOf(
        "lancamento" to linkedMapOf(
            "tipo" to Regra(T.ENUM, true, valores = listOf("entrada", "despesa")), "descricao" to texto(120, true),
            "valorCentavos" to Regra(T.CENTAVOS, true, positivo = true), "data" to Regra(T.DATA, true), "categoria" to texto(40, true),
            "autorUid" to Regra(T.ID), "autorNome" to texto(60), "obs" to texto(300)
        ),
        "compra" to linkedMapOf(
            "descricao" to texto(120, true), "valorCentavos" to Regra(T.CENTAVOS, true, positivo = true), "data" to Regra(T.DATA, true),
            "categoria" to texto(40, true), "cartaoId" to Regra(T.ID, true), "parcelas" to int(1, Modelo.MAX_PARCELAS, true),
            "mesInicial" to Regra(T.MES, true), "autorUid" to Regra(T.ID), "autorNome" to texto(60), "obs" to texto(300)
        ),
        "cartao" to linkedMapOf(
            "nome" to texto(40, true), "final" to texto(4), "limiteCentavos" to Regra(T.CENTAVOS, true),
            "fechamentoDia" to int(1, 31, true), "vencimentoDia" to int(1, 31, true), "pagas" to Regra(T.MESES)
        ),
        "divida" to linkedMapOf(
            "credor" to texto(60, true), "descricao" to texto(120), "status" to Regra(T.ENUM, true, valores = Modelo.STATUS_DIVIDA),
            "valorCentavos" to Regra(T.CENTAVOS), "parcelasTotal" to int(0, 600), "parcelasPagas" to int(0, 600),
            "valorParcelaCentavos" to Regra(T.CENTAVOS), "vencimentoDia" to int(1, 31), "obs" to texto(300)
        ),
        "investimento" to linkedMapOf(
            "tipo" to Regra(T.ENUM, true, valores = Modelo.TIPOS_INVEST), "nome" to texto(80, true), "instituicao" to texto(60),
            "aplicadoCentavos" to Regra(T.CENTAVOS, true), "atualCentavos" to Regra(T.CENTAVOS, true), "atualizadoEm" to Regra(T.DATA),
            "obs" to texto(300)
        ),
        "meta" to linkedMapOf(
            "nome" to texto(80, true), "tipo" to Regra(T.ENUM, true, valores = Modelo.TIPOS_META),
            "alvoCentavos" to Regra(T.CENTAVOS, true, positivo = true), "guardadoCentavos" to Regra(T.CENTAVOS),
            "prazo" to Regra(T.MES), "obs" to texto(300)
        ),
    )
    private val ROTULOS = mapOf(
        "descricao" to "a descrição", "valorCentavos" to "o valor", "data" to "a data", "categoria" to "a categoria", "cartaoId" to "o cartão",
        "parcelas" to "as parcelas", "nome" to "o nome", "limiteCentavos" to "o limite", "fechamentoDia" to "o dia de fechamento",
        "vencimentoDia" to "o dia de vencimento", "credor" to "o credor", "status" to "a situação", "tipo" to "o tipo",
        "aplicadoCentavos" to "o valor aplicado", "atualCentavos" to "o valor atual", "alvoCentavos" to "o valor da meta",
        "parcelasTotal" to "o total de parcelas", "parcelasPagas" to "as parcelas pagas", "valorParcelaCentavos" to "o valor da parcela",
        "prazo" to "o prazo"
    )

    private val DATA_ISO = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT)
    private val FORMA_DATA = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val FORMA_MES = Regex("^\\d{4}-(0[1-9]|1[0-2])$")
    private val FORMA_ID = Regex("^[A-Za-z0-9_-]{1,64}$")
    private const val MAX_SEGURO = 9007199254740991L // Number.MAX_SAFE_INTEGER

    private fun dataOK(s: String) = FORMA_DATA.matches(s) && runCatching { LocalDate.parse(s, DATA_ISO) }.isSuccess
    private fun mesOK(s: String) = FORMA_MES.matches(s)

    // Número inteiro no sentido do JS (Number.isInteger): 4590 e 4590.0 valem; 45.9 não.
    private fun inteiro(v: Any?): Long? = when (v) {
        is Int -> v.toLong()
        is Long -> v
        is Double -> if (v.isFinite() && v == Math.rint(v) && kotlin.math.abs(v) <= MAX_SEGURO) v.toLong() else null
        else -> null
    }

    fun limpar(tipoRegistro: String, entrada: Map<String, Any?>?): Map<String, Any?> {
        val esquema = ESQUEMAS[tipoRegistro] ?: throw IllegalArgumentException("Tipo de registro desconhecido.")
        if (entrada == null) throw IllegalArgumentException("Registro inválido.")
        val out = LinkedHashMap<String, Any?>()
        for ((campo, o) in esquema) {
            val rotulo = ROTULOS[campo] ?: campo
            fun falha(msg: String): Nothing = throw ErroValidacao(campo, msg)
            val v = entrada[campo]
            if (v == null || v == "") { if (o.obrig) falha("Informe $rotulo."); continue }
            out[campo] = when (o.tipo) {
                T.TEXTO -> {
                    val s = v.toString().trim()
                    if (o.obrig && s.isEmpty()) falha("Informe $rotulo.")
                    if (s.length > o.max) falha("Use até ${o.max} caracteres.")
                    if (s.isEmpty()) continue
                    s
                }
                T.ENUM -> if (v is String && v in o.valores) v else falha("Escolha $rotulo.")
                T.CENTAVOS -> {
                    val c = inteiro(v)
                    if (c == null || c < 0 || c > 10_000_000_000_000L) falha("Valor inválido em $rotulo.")
                    if (o.positivo && c == 0L) falha("Informe $rotulo maior que zero.")
                    c
                }
                T.INT -> {
                    val n = inteiro(v)
                    if (n == null || n < o.min || n > o.max) falha("Use um número de ${o.min} a ${o.max} em $rotulo.")
                    n.toInt()
                }
                T.DATA -> if (v is String && dataOK(v)) v else falha("Data inválida em $rotulo.")
                T.MES -> if (v is String && mesOK(v)) v else falha("Mês inválido em $rotulo.")
                T.ID -> if (v is String && FORMA_ID.matches(v)) v else falha("Escolha $rotulo.")
                T.MESES -> {
                    if (v !is List<*> || v.size > 600 || !v.all { it is String && mesOK(it) }) falha("Lista de meses inválida.")
                    v.map { it as String }.distinct().sorted()
                }
            }
        }
        if (tipoRegistro == "divida") {
            val total = out["parcelasTotal"] as Int? ?: 0
            if ((out["parcelasPagas"] as Int? ?: 0) > total && total != 0) throw ErroValidacao("parcelasPagas", "Parcelas pagas não podem passar do total.")
        }
        return out
    }

    fun limparOuNulo(tipoRegistro: String, entrada: Map<String, Any?>?): Map<String, Any?>? =
        try { limpar(tipoRegistro, entrada) } catch (_: IllegalArgumentException) { null }
}
