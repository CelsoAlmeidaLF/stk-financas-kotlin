package br.com.systekna.financas

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.systekna.financas.aplicacao.AcessoViewModel
import br.com.systekna.financas.aplicacao.EstadoAcesso
import br.com.systekna.financas.aplicacao.LancamentosViewModel
import br.com.systekna.financas.infra.AppDatabase
import br.com.systekna.financas.infra.BiometriaAndroid
import br.com.systekna.financas.infra.CofreKeystore
import br.com.systekna.financas.infra.RepositorioCifrado
import br.com.systekna.financas.ui.TelaAcesso
import br.com.systekna.financas.ui.TelaLancamentos
import br.com.systekna.financas.ui.TemaFinancas

// FragmentActivity: o leitor de biometria (BiometricPrompt) precisa dela.
class MainActivity : FragmentActivity() {
    // Composição (hexagonal): a infraestrutura (Keystore, Room) entra na aplicação pelas portas.
    // Uma só no processo: o ViewModel sobrevive à rotação da tela, e a biometria precisa apontar para a tela atual.
    private val biometria get() = biometriaApp ?: BiometriaAndroid(applicationContext).also { biometriaApp = it }
    private val acesso: AcessoViewModel by viewModels { viewModelFactory { initializer { AcessoViewModel(CofreKeystore(applicationContext), biometria) } } }
    private val repo by lazy { RepositorioCifrado(AppDatabase.get(applicationContext).registroDao(), acesso.sessao) }
    private var saiuEm = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        biometria.tela = this
        // Dados financeiros: sem print de tela e sem miniatura na lista de apps recentes.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        // Bloqueia se o app ficou mais de 1 minuto em segundo plano (o seletor de arquivos do backup leva menos que isso).
        lifecycle.addObserver(LifecycleEventObserver { _, evento ->
            when (evento) {
                Lifecycle.Event.ON_STOP -> saiuEm = SystemClock.elapsedRealtime()
                Lifecycle.Event.ON_START -> { if (saiuEm > 0 && SystemClock.elapsedRealtime() - saiuEm > 60_000) acesso.bloquear(); saiuEm = 0 }
                else -> {}
            }
        })
        setContent {
            TemaFinancas {
                val estado by acesso.estado.collectAsStateWithLifecycle()
                if (estado is EstadoAcesso.Liberado) {
                    LaunchedEffect(Unit) { runCatching { repo.migrarAntigos() } }
                    val vm: LancamentosViewModel = viewModel(factory = viewModelFactory { initializer { LancamentosViewModel(repo) } })
                    TelaLancamentos(vm, acesso)
                } else TelaAcesso(acesso)
            }
        }
    }

    override fun onDestroy() {
        if (biometriaApp?.tela === this) biometriaApp?.tela = null
        super.onDestroy()
    }

    private companion object { var biometriaApp: BiometriaAndroid? = null }
}
