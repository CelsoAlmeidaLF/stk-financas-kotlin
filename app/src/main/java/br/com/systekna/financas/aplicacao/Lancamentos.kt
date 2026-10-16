/* Aplicação: casos de uso da tela de lançamentos. Só conhece o domínio e a porta do repositório,
   nunca o Room ou o Compose (hexagonal: a infraestrutura pluga aqui pelo RepositorioLancamentos). */
package br.com.systekna.financas.aplicacao

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.systekna.financas.dominio.BackupCifrado
import br.com.systekna.financas.dominio.CriptoCasa
import br.com.systekna.financas.dominio.ErroValidacao
import br.com.systekna.financas.dominio.Lancamento
import br.com.systekna.financas.dominio.Modelo
import br.com.systekna.financas.dominio.Validacao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LancamentoSalvo(val id: String, val lancamento: Lancamento, val criadoEm: Long) {
    val travado get() = Modelo.travado(criadoEm)
}

// Porta: quem guarda os lançamentos (no Android: SQLite via Room, cada registro cifrado com a chave do cofre).
interface RepositorioLancamentos {
    fun doMes(mes: String): Flow<List<LancamentoSalvo>>
    suspend fun inserir(l: Lancamento)
    suspend fun excluir(id: String)
    suspend fun todos(): List<LancamentoSalvo>
    suspend fun substituirTudo(itens: List<BackupCifrado.Item>)
}

data class Formulario(val tipo: String, val descricao: String, val valor: String, val data: String, val categoria: String)

data class EstadoLancamentos(
    val mes: String,
    val itens: List<LancamentoSalvo> = emptyList(),
    val resumo: Modelo.ResumoMes = Modelo.resumoMes(emptyList(), emptyList()),
)

class LancamentosViewModel(private val repo: RepositorioLancamentos) : ViewModel() {
    private val mes = MutableStateFlow(Modelo.mesDe(Modelo.hojeISO()))

    @OptIn(ExperimentalCoroutinesApi::class)
    val estado: StateFlow<EstadoLancamentos> = mes
        .flatMapLatest { m -> repo.doMes(m).map { itens -> EstadoLancamentos(m, itens, Modelo.resumoMes(itens.map { it.lancamento }, emptyList())) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EstadoLancamentos(mes.value))

    fun mudarMes(n: Int) { mes.update { Modelo.addMes(it, n) } }

    // Valida com as mesmas regras do PWA; devolve o erro (com o campo) ou null se gravou.
    fun lancar(f: Formulario): ErroValidacao? {
        val centavos = Modelo.parseValor(f.valor)
        if (f.valor.isNotBlank() && centavos == null) return ErroValidacao("valorCentavos", "Valor inválido. Use, por exemplo, 45,90.")
        return try {
            val limpo = Validacao.limpar(
                "lancamento",
                mapOf("tipo" to f.tipo, "descricao" to f.descricao, "valorCentavos" to centavos, "data" to isoDeBr(f.data), "categoria" to f.categoria)
            )
            val l = Lancamento.de(limpo)
            viewModelScope.launch { repo.inserir(l) }
            null
        } catch (e: ErroValidacao) { e }
    }

    fun excluir(item: LancamentoSalvo) {
        if (item.travado) return // a interface já esconde o botão; a regra vale aqui também
        viewModelScope.launch { repo.excluir(item.id) }
    }

    /* ---------- backup ---------- */
    // Devolve o JSON cifrado com a senha (o arquivo é gravado pela tela, onde o usuário escolher).
    suspend fun exportar(senha: String, iteracoes: Int = CriptoCasa.ITERACOES): String = withContext(Dispatchers.Default) {
        BackupCifrado.gerar(repo.todos().map { BackupCifrado.Item(it.lancamento, it.criadoEm) }, senha, iteracoes)
    }
    // Abre com a senha, valida cada registro e troca todos os lançamentos deste aparelho. Devolve quantos entraram.
    suspend fun restaurar(texto: String, senha: String): Int = withContext(Dispatchers.Default) {
        val itens = BackupCifrado.abrir(texto, senha)
        repo.substituirTudo(itens)
        itens.size
    }

    companion object {
        private val BR = Regex("^(\\d{2})/(\\d{2})/(\\d{4})$")
        fun isoDeBr(s: String): String = BR.matchEntire(s.trim())?.destructured?.let { (d, m, a) -> "$a-$m-$d" } ?: s.trim()
        fun brDeIso(iso: String): String = "${iso.substring(8, 10)}/${iso.substring(5, 7)}/${iso.substring(0, 4)}"
    }
}
