/* Telas de acesso: criar o PIN, abrir com o PIN, código de recuperação (mostrado uma vez) e "esqueci o PIN". */
package br.com.systekna.financas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.systekna.financas.aplicacao.AcessoViewModel
import br.com.systekna.financas.aplicacao.EstadoAcesso
import kotlinx.coroutines.delay

@Composable
fun TelaAcesso(vm: AcessoViewModel) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    Fundo {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            when (val e = estado) {
                EstadoAcesso.Carregando, EstadoAcesso.Verificando, EstadoAcesso.Liberado -> Verificando()
                is EstadoAcesso.CriarPin -> Teclado(
                    e, if (e.confirmar == null) "Crie seu PIN" else "Confirme o PIN",
                    if (e.confirmar == null) "Escolha 6 números. Eles abrem o app e protegem os dados deste aparelho." else "Digite o mesmo PIN mais uma vez.",
                    e.erro, aoCompletar = vm::pinCriado,
                )
                is EstadoAcesso.MostrarCodigo -> CodigoRecuperacao(e.codigo, vm::codigoGuardado)
                is EstadoAcesso.Desbloquear -> Teclado(
                    e, "Dados protegidos", "Digite seu PIN para abrir o app.", e.erro, esperaAte = e.esperaAte,
                    aoCompletar = vm::pinDigitado, link = "Esqueci o PIN" to vm::esqueciPin,
                )
                is EstadoAcesso.Recuperar -> Recuperar(e.erro, vm::codigoDigitado, vm::voltarAoPin)
                is EstadoAcesso.NovoPin -> Teclado(
                    e, if (e.confirmar == null) "Novo PIN" else "Confirme o novo PIN",
                    if (e.confirmar == null) "Escolha 6 números novos." else "Digite o mesmo PIN mais uma vez.",
                    e.erro, aoCompletar = vm::novoPinDigitado, link = "Cancelar" to vm::voltarAoPin,
                )
            }
        }
    }
}

@Composable
private fun Verificando() {
    val c = LocalCores.current
    CircularProgressIndicator(color = c.accent)
    Spacer(Modifier.height(16.dp))
    Text("Verificando…", style = MaterialTheme.typography.bodyMedium, color = c.soft)
}

@Composable
private fun Selo(titulo: String, texto: String) {
    val c = LocalCores.current
    Box(Modifier.size(56.dp).clip(CircleShape).background(c.accentSoft), contentAlignment = Alignment.Center) {
        Icon(Icones.escudo, null, tint = c.accent, modifier = Modifier.size(28.dp))
    }
    Spacer(Modifier.height(14.dp))
    Text(titulo, style = MaterialTheme.typography.headlineSmall, color = c.ink, textAlign = TextAlign.Center)
    Spacer(Modifier.height(6.dp))
    Text(texto, style = MaterialTheme.typography.bodyMedium, color = c.soft, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 320.dp))
}

// Teclado numérico do PIN. `chave` zera o que foi digitado quando a tela muda (ex.: da 1ª para a 2ª digitação).
@Composable
private fun Teclado(
    chave: Any, titulo: String, texto: String, erro: String?, aoCompletar: (String) -> Unit,
    esperaAte: Long = 0, link: Pair<String, () -> Unit>? = null,
) {
    val c = LocalCores.current
    var pin by remember(chave) { mutableStateOf("") }
    var ver by remember { mutableStateOf(false) }
    var agora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(esperaAte) { while (System.currentTimeMillis() < esperaAte) { agora = System.currentTimeMillis(); delay(500) }; agora = System.currentTimeMillis() }
    val espera = ((esperaAte - agora + 999) / 1000).coerceAtLeast(0)
    val livre = espera == 0L

    Selo(titulo, texto)
    Spacer(Modifier.height(22.dp))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics { contentDescription = "${pin.length} de 6 números digitados" }) {
        Spacer(Modifier.width(48.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(6) { i ->
                if (ver && i < pin.length) Text(pin[i].toString(), style = mono(20, FontWeight.SemiBold), color = c.ink, modifier = Modifier.width(14.dp), textAlign = TextAlign.Center)
                else Box(Modifier.size(14.dp).clip(CircleShape).background(if (i < pin.length) c.accent else c.line))
            }
        }
        IconButton(onClick = { ver = !ver }) { Icon(if (ver) Icones.olhoFechado else Icones.olho, if (ver) "Ocultar PIN" else "Mostrar PIN", tint = c.soft) }
    }
    Spacer(Modifier.height(10.dp))
    Text(
        when { !livre -> "Muitas tentativas. Aguarde ${espera}s."; erro != null -> erro; else -> " " },
        color = c.saida, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(10.dp))
    val teclas = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "apagar")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        teclas.chunked(3).forEach { linha ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                linha.forEach { t ->
                    val mod = Modifier.size(72.dp).clip(CircleShape)
                    when (t) {
                        "" -> Spacer(Modifier.size(72.dp))
                        "apagar" -> Box(mod.clickable(enabled = pin.isNotEmpty(), role = Role.Button) { pin = pin.dropLast(1) }, contentAlignment = Alignment.Center) {
                            Icon(Icones.apagar, "Apagar", tint = c.soft)
                        }
                        else -> Box(
                            mod.background(c.field).border(1.dp, c.line, CircleShape)
                                .clickable(enabled = livre, role = Role.Button) {
                                    if (pin.length < 6) pin += t
                                    if (pin.length == 6) { val p = pin; pin = ""; aoCompletar(p) }
                                },
                            contentAlignment = Alignment.Center,
                        ) { Text(t, style = mono(24), color = if (livre) c.ink else c.faint) }
                    }
                }
            }
        }
    }
    if (link != null) {
        Spacer(Modifier.height(18.dp))
        TextButton(onClick = link.second) { Text(link.first, color = c.soft) }
    }
}

@Composable
private fun CodigoRecuperacao(codigo: String, guardei: () -> Unit) {
    val c = LocalCores.current
    val area = LocalClipboardManager.current
    var copiado by remember { mutableStateOf(false) }
    Selo("Guarde o código de recuperação", "Se esquecer o PIN, este código é a única forma de voltar aos dados. Ele não aparece de novo. Anote em papel ou num gerenciador de senhas.")
    Spacer(Modifier.height(20.dp))
    Vidro(Modifier.widthIn(max = 420.dp)) {
        Text(codigo, style = mono(17, FontWeight.SemiBold), color = c.ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        Botao(if (copiado) "COPIADO" else "COPIAR CÓDIGO", Icones.copiar, principal = false) { area.setText(AnnotatedString(codigo)); copiado = true }
        Spacer(Modifier.height(10.dp))
        Botao("GUARDEI O CÓDIGO", Icones.chave, aoTocar = guardei)
    }
}

@Composable
private fun Recuperar(erro: String?, enviar: (String) -> Unit, voltar: () -> Unit) {
    val c = LocalCores.current
    var codigo by remember { mutableStateOf("") }
    Selo("Esqueci o PIN", "Digite o código de recuperação que você guardou ao criar o PIN. Depois você escolhe um PIN novo.")
    Spacer(Modifier.height(20.dp))
    Vidro(Modifier.widthIn(max = 420.dp)) {
        Campo("Código de recuperação", codigo, { codigo = it }, erro, monoTexto = true, senha = true)
        Spacer(Modifier.height(10.dp))
        Botao("CONTINUAR", Icones.chave, habilitado = codigo.isNotBlank()) { enviar(codigo) }
    }
    Spacer(Modifier.height(12.dp))
    TextButton(onClick = voltar) { Text("Voltar ao PIN", color = c.soft) }
}
