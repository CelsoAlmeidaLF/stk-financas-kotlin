/* Backup cifrado: exportar (senha 2x → o Android pergunta onde salvar: Downloads, Drive…) e restaurar (arquivo + senha).
   O arquivo é escrito e lido pelo seletor de arquivos do sistema: o app não pede permissão de armazenamento. */
package br.com.systekna.financas.ui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.systekna.financas.aplicacao.LancamentosViewModel
import br.com.systekna.financas.dominio.BackupCifrado
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TelaBackup(vm: LancamentosViewModel, fechar: () -> Unit) {
    val c = LocalCores.current
    val ctx = LocalContext.current
    val escopo = rememberCoroutineScope()
    var ocupado by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<Pair<Boolean, String>?>(null) } // (ok, texto)

    // Exportar
    var senha by remember { mutableStateOf("") }
    var senha2 by remember { mutableStateOf("") }
    var erroSenha by remember { mutableStateOf<String?>(null) }
    val salvar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        ocupado = true; aviso = null
        escopo.launch {
            aviso = try {
                val texto = vm.exportar(senha)
                withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(texto.toByteArray(Charsets.UTF_8)) } }
                senha = ""; senha2 = ""
                true to "Backup salvo. Guarde a senha: sem ela o arquivo não abre."
            } catch (e: Exception) { false to "Não salvei: ${e.message}" }
            ocupado = false
        }
    }

    // Restaurar
    var arquivo by remember { mutableStateOf<Pair<String, String>?>(null) } // (nome, conteúdo)
    var senhaArq by remember { mutableStateOf("") }
    var substituir by remember { mutableStateOf(false) }
    val abrir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        escopo.launch {
            arquivo = runCatching {
                withContext(Dispatchers.IO) {
                    val nome = ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "backup"
                    val texto = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                    require(texto.size < 20_000_000) { "Arquivo grande demais." }
                    nome to texto.toString(Charsets.UTF_8)
                }
            }.getOrElse { aviso = false to "Não consegui ler o arquivo: ${it.message}"; null }
        }
    }

    Dialog(onDismissRequest = { if (!ocupado) fechar() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().padding(16.dp).widthIn(max = 520.dp)) {
            Vidro {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icones.backup, null, tint = c.accent)
                        Spacer(Modifier.width(10.dp))
                        Text("Backup cifrado", style = MaterialTheme.typography.titleMedium, color = c.ink, modifier = Modifier.weight(1f))
                        IconButton(onClick = fechar, enabled = !ocupado) { Icon(Icones.fechar, "Fechar", tint = c.soft) }
                    }
                    Text("Os dados ficam só neste celular. Exporte um backup de vez em quando e guarde fora dele (Drive, e-mail, computador).",
                        style = MaterialTheme.typography.bodySmall, color = c.soft)

                    Spacer(Modifier.height(16.dp))
                    Text("Exportar", style = MaterialTheme.typography.titleMedium, color = c.ink)
                    Text("Escolha uma senha só para o backup (mínimo ${BackupCifrado.SENHA_MINIMA} caracteres). Ela é pedida para restaurar, em qualquer aparelho.",
                        style = MaterialTheme.typography.bodySmall, color = c.soft)
                    Spacer(Modifier.height(8.dp))
                    Campo("Senha do backup", senha, { senha = it; erroSenha = null }, null, senha = true)
                    Spacer(Modifier.height(6.dp))
                    Campo("Repita a senha", senha2, { senha2 = it; erroSenha = null }, erroSenha, senha = true)
                    Spacer(Modifier.height(8.dp))
                    Botao("EXPORTAR BACKUP", Icones.baixar, habilitado = !ocupado) {
                        erroSenha = when {
                            !BackupCifrado.senhaValida(senha) -> "Use pelo menos ${BackupCifrado.SENHA_MINIMA} caracteres."
                            senha != senha2 -> "As senhas não coincidem."
                            else -> null
                        }
                        if (erroSenha == null) salvar.launch("financas-casa-kotlin-backup-${br.com.systekna.financas.dominio.Modelo.hojeISO()}.json")
                    }

                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider(color = c.line)
                    Spacer(Modifier.height(16.dp))
                    Text("Restaurar", style = MaterialTheme.typography.titleMedium, color = c.ink)
                    Text("Troca todos os lançamentos deste celular pelos do backup.", style = MaterialTheme.typography.bodySmall, color = c.soft)
                    Spacer(Modifier.height(8.dp))
                    Botao(arquivo?.first ?: "ESCOLHER ARQUIVO", Icones.subir, principal = false, habilitado = !ocupado) { abrir.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
                    if (arquivo != null) {
                        Spacer(Modifier.height(8.dp))
                        Campo("Senha do backup", senhaArq, { senhaArq = it }, null, senha = true)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(substituir, { substituir = it }, colors = CheckboxDefaults.colors(checkedColor = c.accent))
                            Text("Substituir os lançamentos deste celular", style = MaterialTheme.typography.bodyMedium, color = c.ink)
                        }
                        Botao("RESTAURAR", Icones.subir, habilitado = !ocupado && substituir && senhaArq.isNotEmpty()) {
                            ocupado = true; aviso = null
                            escopo.launch {
                                aviso = try {
                                    val n = vm.restaurar(arquivo!!.second, senhaArq)
                                    arquivo = null; senhaArq = ""; substituir = false
                                    true to "$n lançamentos restaurados."
                                } catch (e: Exception) { false to (e.message ?: "Não consegui restaurar.") }
                                ocupado = false
                            }
                        }
                    }

                    if (ocupado) { Spacer(Modifier.height(12.dp)); LinearProgressIndicator(Modifier.fillMaxWidth(), color = c.accent) }
                    aviso?.let { (ok, texto) ->
                        Spacer(Modifier.height(12.dp))
                        Text(texto, style = MaterialTheme.typography.bodyMedium, color = if (ok) c.entrada else c.saida)
                    }
                }
            }
        }
    }
}
