/* Aplicação: acesso por PIN ou biometria. Só conhece o domínio (CofrePin) e as portas ArmazemCofre e Biometria;
   quem guarda o cofre e mostra o leitor de digital/rosto é a infra.
   Fluxo: sem cofre → criar PIN (2x) → mostrar o código de recuperação uma vez → oferecer biometria → liberado.
          com cofre → biometria (se ativa) ou PIN (com espera após 5 erros) → liberado; "esqueci o PIN" → código → PIN novo.
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

// Porta: biometria do aparelho (no Android: chave do Keystore que só abre com digital/rosto forte, BIOMETRIC_STRONG).
// A chave de dados fica cifrada por ela; o PIN continua valendo como alternativa.
interface Biometria {
    fun disponivel(): Boolean            // o aparelho tem biometria forte cadastrada
    fun ativa(): Boolean                 // já foi ativada neste app
    fun recusada(): Boolean              // a pessoa disse "agora não"
    fun recusar()
    suspend fun ativar(dek: String): Boolean   // mostra o leitor; false = cancelou
    suspend fun abrir(): String?               // mostra o leitor e devolve a chave; null = cancelou
    fun desativar()
}
class BiometriaInvalidada : Exception("A biometria do aparelho mudou. Entre com o PIN e ative de novo.")

// Chave de dados em memória enquanto o app está liberado. O repositório lê daqui; bloquear zera.
class Sessao {
    @Volatile var chave: SecretKey? = null
        private set
    fun abrir(dek: String) { chave = CriptoCasa.chaveDados(dek) }
    fun dek(): String? = chave?.encoded?.let { java.util.Base64.getEncoder().encodeToString(it) }
    fun fechar() { chave = null }
}

sealed interface EstadoAcesso {
    data object Carregando : EstadoAcesso
    data class CriarPin(val confirmar: String? = null, val erro: String? = null) : EstadoAcesso   // confirmar = 1º PIN digitado
    data class MostrarCodigo(val codigo: String) : EstadoAcesso
    data class Desbloquear(val erro: String? = null, val esperaAte: Long = 0, val biometria: Boolean = false) : EstadoAcesso
    data object OferecerBiometria : EstadoAcesso
    data class Recuperar(val erro: String? = null) : EstadoAcesso
    data class NovoPin(val confirmar: String? = null, val erro: String? = null) : EstadoAcesso
    data object Verificando : EstadoAcesso
    data object Liberado : EstadoAcesso
}

class AcessoViewModel(
    private val armazem: ArmazemCofre,
    private val bio: Biometria,
    private val iteracoes: Int = CriptoCasa.ITERACOES,
) : ViewModel() {
    val sessao = Sessao()
    private val _estado = MutableStateFlow<EstadoAcesso>(EstadoAcesso.Carregando)
    val estado: StateFlow<EstadoAcesso> = _estado
    private var dekRecuperada: String? = null   // entre o código de recuperação e o PIN novo

    init { viewModelScope.launch { _estado.value = withContext(Dispatchers.IO) { telaInicial() } } }

    private fun telaInicial(): EstadoAcesso = if (armazem.ler() == null) EstadoAcesso.CriarPin() else desbloquear()
    private fun bioAtiva() = runCatching { bio.ativa() && bio.disponivel() }.getOrDefault(false)
    private fun desbloquear(erro: String? = null) = EstadoAcesso.Desbloquear(erro, esperaAte(armazem.erros()), bioAtiva())
    // Depois de entrar com o PIN: oferece a biometria uma vez, se o aparelho tem e a pessoa ainda não decidiu.
    private fun liberadoOuOferta(): EstadoAcesso =
        if (runCatching { bio.disponivel() && !bio.ativa() && !bio.recusada() }.getOrDefault(false)) EstadoAcesso.OferecerBiometria else EstadoAcesso.Liberado
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
    fun codigoGuardado() { if (_estado.value is EstadoAcesso.MostrarCodigo) _estado.value = liberadoOuOferta() }

    /* ---------- abrir ---------- */
    fun pinDigitado(pin: String) {
        val atual = _estado.value as? EstadoAcesso.Desbloquear ?: return
        if (System.currentTimeMillis() < atual.esperaAte) return
        verificar {
            val cofre = armazem.ler() ?: return@verificar EstadoAcesso.CriarPin()
            try {
                sessao.abrir(CofrePin.abrirComPin(cofre, pin))
                armazem.gravarErros(Erros())
                liberadoOuOferta()
            } catch (e: ErroCripto) {
                val erros = Erros(armazem.erros().seguidos + 1, System.currentTimeMillis()).also(armazem::gravarErros)
                desbloquear(if (esperaAte(erros) > 0) "PIN incorreto. Aguarde para tentar de novo." else "PIN incorreto.")
            }
        }
    }

    /* ---------- esqueci o PIN ---------- */
    fun esqueciPin() { if (_estado.value is EstadoAcesso.Desbloquear) _estado.value = EstadoAcesso.Recuperar() }
    fun voltarAoPin() { dekRecuperada = null; _estado.value = desbloquear() }
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
            // PIN novo: a biometria antiga continua válida (a chave de dados é a mesma).
            liberadoOuOferta()
        }
    }

    /* ---------- biometria ---------- */
    fun usarBiometria() {
        if (_estado.value !is EstadoAcesso.Desbloquear || !bioAtiva()) return
        viewModelScope.launch {
            _estado.value = try {
                val dek = bio.abrir() ?: return@launch          // cancelou: continua na tela do PIN
                sessao.abrir(dek); armazem.gravarErros(Erros())
                EstadoAcesso.Liberado
            } catch (e: BiometriaInvalidada) {
                bio.desativar(); desbloquear(e.message)
            } catch (e: Exception) {
                desbloquear("Biometria indisponível agora. Use o PIN.")
            }
        }
    }
    // Ativar: depois do PIN (oferta) ou pela tela de segurança, com o app aberto.
    fun ativarBiometria(aoTerminar: (String?) -> Unit = {}) {
        val dek = sessao.dek() ?: return aoTerminar("Abra o app com o PIN antes.")
        viewModelScope.launch {
            val msg = try { if (bio.ativar(dek)) null else "Ativação cancelada." }
                      catch (e: Exception) { "Não consegui ativar a biometria: ${e.message}" }
            if (_estado.value == EstadoAcesso.OferecerBiometria) _estado.value = EstadoAcesso.Liberado
            aoTerminar(msg)
        }
    }
    fun recusarBiometria() {
        runCatching { bio.recusar() }
        if (_estado.value == EstadoAcesso.OferecerBiometria) _estado.value = EstadoAcesso.Liberado
    }
    fun desativarBiometria() { runCatching { bio.desativar() } }
    fun biometriaDisponivel() = runCatching { bio.disponivel() }.getOrDefault(false)
    fun biometriaAtiva() = runCatching { bio.ativa() }.getOrDefault(false)

    /* ---------- bloquear ---------- */
    fun bloquear() {
        if (_estado.value != EstadoAcesso.Liberado && _estado.value != EstadoAcesso.OferecerBiometria) return
        sessao.fechar()
        _estado.value = desbloquear()
    }

    // PBKDF2 de 600 mil iterações leva ~1 s: fora da thread da tela, com "Verificando…".
    private fun verificar(passo: () -> EstadoAcesso) {
        _estado.value = EstadoAcesso.Verificando
        viewModelScope.launch { _estado.value = withContext(Dispatchers.Default) { passo() } }
    }

    override fun onCleared() { sessao.fechar() }
}
