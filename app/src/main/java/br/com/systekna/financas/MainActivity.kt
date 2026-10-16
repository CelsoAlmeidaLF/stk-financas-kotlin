package br.com.systekna.financas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.systekna.financas.aplicacao.LancamentosViewModel
import br.com.systekna.financas.infra.AppDatabase
import br.com.systekna.financas.infra.RepositorioRoom
import br.com.systekna.financas.ui.TelaLancamentos
import br.com.systekna.financas.ui.TemaFinancas

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Composição: a infraestrutura (Room) entra na aplicação pela porta RepositorioLancamentos.
        val fabrica = viewModelFactory {
            initializer { LancamentosViewModel(RepositorioRoom(AppDatabase.get(applicationContext).lancamentoDao())) }
        }
        setContent {
            TemaFinancas {
                TelaLancamentos(viewModel(factory = fabrica))
            }
        }
    }
}
