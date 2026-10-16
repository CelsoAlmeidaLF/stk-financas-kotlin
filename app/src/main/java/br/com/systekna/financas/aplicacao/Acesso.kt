/* Aplicação: acesso por PIN. Só conhece o domínio (CofrePin) e a porta ArmazemCofre; quem guarda o cofre é a infra.
   Fluxo: sem cofre → criar PIN (2x) → mostrar o código de recuperação uma vez → liberado.
          com cofre → digitar o PIN (com espera após 5 erros) → liberado; "esqueci o PIN" → código → PIN novo.
   Liberado: a chave de dados fica só na memória (Sessao); bloquear apaga. */
package br.com.systekna.financas.aplicacao

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.systekna.financas.dominio.Cofre
import br.com.systekna.financas.dominio.CofrePin
import br.com.systekna.financas.dominio.CriptoCasa
import br.com.systekna.financas.dominio.ErroCripto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.crypto.SecretKey

// Porta: onde o cofre e o contador de erros ficam guardados (no Android: arquivo privado cifrado pelo Keystore).
interface ArmazemCofre {
    fun ler(): Cofre?
    fun gravar(c: Cofre)
    fun erros(): Erros
    fun gravarErros(e: Erros)
}
data class Erros(val seguidos: Int = 0, val ultimoEm: Long = 0)

// Chave de dados em memória enquanto o app está liberado. O repositório lê daqui; bloquear zera.
class Sessao {
    @Volatile var chave: SecretKey? = null
        private set
    fun abrir(dek: String) { chave = CriptoCasa.chaveDados(dek) }
    fun fechar() { chave = null }
}

sealed interface EstadoAcesso {
    data object Carregando : EstadoAcesso
    data class CriarPin(val confirmar: String? = null, val erro: String? = null) : EstadoAcesso   // confirmar = 1º PIN digitado
    data class MostrarCodigo(val codigo: String) : EstadoAcesso
    data class Desbloquear(val erro: String? = null, val esperaAte: Long = 0) : EstadoAcesso
    data class Recuperar(val erro: String? = null) : EstadoAcesso
    data class NovoPin(val confirmar: String? = null, val erro: String? = null) : EstadoAcesso
    data object Verificando : EstadoAcesso
    data object Liberado : EstadoAcesso
}

class AcessoViewModel(private val armazem: ArmazemCofre, private val iteracoes: Int = CriptoCasa.ITERACOES) : ViewModel() {
    val sessao = Sessao()
    private val _estado = MutableStateFlow<EstadoAcesso>(EstadoAcesso.Carregando)
    val estado: StateFlow<EstadoAcesso> = _estado
    private var dekRecuperada: String? = null   // entre o código de recuperação e o PIN novo

    init { viewModelScope.launch { _estado.value = withContext(Dispatchers.IO) { telaInicial() } } }

    private fun telaInicial(): EstadoAcesso =
        if (armazem.ler() == null) EstadoAcesso.CriarPin() else EstadoAcesso.Desbloquear(esperaAte = esperaAte(armazem.erros()))
    private fun esperaAte(e: Erros) = if (CofrePin.esperaMs(e.seguidos) == 0L) 0 else e.ultimoEm + CofrePin.esperaMs(e.seguidos)

    /* ---------- criar ---------- */
    fun pinCriado(pin: String) {
        val atual = _estado.value as? EstadoAcesso.CriarPin ?: return
        if (atual.confirmar == null) { _estado.value = EstadoAcesso.CriarPin(confirmar = pin); return }
        if (atual.confirmar != pin) { _estado.value = EstadoAcesso.CriarPin(erro = "Os PINs não coincidem. Comece de novo."); return }
        verificar {
            val n = CofrePin.criar(pin, iteracoes)
            armazem.gravar(n.cofre); armazem.gravarErros(Erros())
            sessao.abrir(n.dek)
            EstadoAcesso.MostrarCodigo(n.codigo)
        }
    }
    fun codigoGuardado() { if (_estado.value is EstadoAcesso.MostrarCodigo) _estado.value = EstadoAcesso.Liberado }

    /* ---------- abrir ---------- */
    fun pinDigitado(pin: String) {
        val atual = _estado.value as? EstadoAcesso.Desbloquear ?: return
        if (System.currentTimeMillis() < atual.esperaAte) return
        verificar {
            val cofre = armazem.ler() ?: return@verificar EstadoAcesso.CriarPin()
            try {
                sessao.abrir(CofrePin.abrirComPin(cofre, pin))
                armazem.gravarErros(Erros())
                EstadoAcesso.Liberado
            } catch (e: ErroCripto) {
                val erros = Erros(armazem.erros().seguidos + 1, System.currentTimeMillis()).also(armazem::gravarErros)
                val ate = esperaAte(erros)
                EstadoAcesso.Desbloquear(if (ate > 0) "PIN incorreto. Aguarde para tentar de novo." else "PIN incorreto.", ate)
            }
        }
    }

    /* ---------- esqueci o PIN ---------- */
    fun esqueciPin() { if (_estado.value is EstadoAcesso.Desbloquear) _estado.value = EstadoAcesso.Recuperar() }
    fun voltarAoPin() { dekRecuperada = null; _estado.value = EstadoAcesso.Desbloquear(esperaAte = esperaAte(armazem.erros())) }
    fun codigoDigitado(codigo: String) {
        if (_estado.value !is EstadoAcesso.Recuperar) return
        verificar {
            val cofre = armazem.ler() ?: return@verificar EstadoAcesso.CriarPin()
            try { dekRecuperada = CofrePin.abrirComCodigo(cofre, codigo); EstadoAcesso.NovoPin() }
            catch (e: ErroCripto) { EstadoAcesso.Recuperar("Código de recuperação incorreto.") }
        }
    }
    fun novoPinDigitado(pin: String) {
        val atual = _estado.value as? EstadoAcesso.NovoPin ?: return
        val dek = dekRecuperada ?: return voltarAoPin()
        if (atual.confirmar == null) { _estado.value = EstadoAcesso.NovoPin(confirmar = pin); return }
        if (atual.confirmar != pin) { _estado.value = EstadoAcesso.NovoPin(erro = "Os PINs não coincidem. Comece de novo."); return }
        verificar {
            armazem.gravar(CofrePin.trocarPin(armazem.ler()!!, dek, pin)); armazem.gravarErros(Erros())
            sessao.abrir(dek); dekRecuperada = null
            EstadoAcesso.Liberado
        }
    }

    /* ---------- bloquear ---------- */
    fun bloquear() {
        if (_estado.value !is EstadoAcesso.Liberado) return
        sessao.fechar()
        _estado.value = EstadoAcesso.Desbloquear(esperaAte = esperaAte(armazem.erros()))
    }

    // PBKDF2 de 600 mil iterações leva ~1 s: fora da thread da tela, com "Verificando…".
    private fun verificar(passo: () -> EstadoAcesso) {
        _estado.value = EstadoAcesso.Verificando
        viewModelScope.launch { _estado.value = withContext(Dispatchers.Default) { passo() } }
    }

    override fun onCleared() { sessao.fechar() }
}
