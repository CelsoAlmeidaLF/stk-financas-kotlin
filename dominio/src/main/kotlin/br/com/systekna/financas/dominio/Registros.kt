/* Registros do domínio. Os nomes dos campos são os mesmos do JSON cifrado do PWA. */
package br.com.systekna.financas.dominio

data class Lancamento(
    val tipo: String, // "entrada" ou "despesa"
    val descricao: String,
    val valorCentavos: Long,
    val data: String,
    val categoria: String,
    val autorUid: String? = null,
    val autorNome: String? = null,
    val obs: String? = null,
) {
    companion object {
        // Recebe a saída de Validacao.limpar("lancamento", …).
        fun de(m: Map<String, Any?>) = Lancamento(
            m["tipo"] as String, m["descricao"] as String, m["valorCentavos"] as Long, m["data"] as String,
            m["categoria"] as String, m["autorUid"] as String?, m["autorNome"] as String?, m["obs"] as String?
        )
    }
    fun paraMapa(): Map<String, Any?> = linkedMapOf(
        "tipo" to tipo, "descricao" to descricao, "valorCentavos" to valorCentavos, "data" to data,
        "categoria" to categoria, "autorUid" to autorUid, "autorNome" to autorNome, "obs" to obs
    ).filterValues { it != null }
}

data class Compra(
    val id: String,
    val descricao: String,
    val valorCentavos: Long,
    val data: String,
    val categoria: String,
    val cartaoId: String,
    val parcelas: Int,
    val mesInicial: String,
    val autorNome: String? = null,
)

data class Cartao(
    val id: String,
    val nome: String,
    val limiteCentavos: Long,
    val fechamentoDia: Int,
    val vencimentoDia: Int,
    val pagas: List<String> = emptyList(),
)

data class Divida(
    val credor: String,
    val status: String,
    val valorCentavos: Long? = null,
    val parcelasTotal: Int? = null,
    val parcelasPagas: Int? = null,
    val valorParcelaCentavos: Long? = null,
)

data class Investimento(val tipo: String, val nome: String, val aplicadoCentavos: Long, val atualCentavos: Long)

data class Meta(val nome: String, val tipo: String, val alvoCentavos: Long, val guardadoCentavos: Long? = null, val prazo: String? = null)
