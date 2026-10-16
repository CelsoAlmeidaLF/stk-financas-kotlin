/* Tela única do piloto: lançamentos do mês (saldo, novo lançamento, por categoria, extrato). */
package br.com.systekna.financas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.systekna.financas.aplicacao.Formulario
import br.com.systekna.financas.aplicacao.LancamentoSalvo
import br.com.systekna.financas.aplicacao.LancamentosViewModel
import br.com.systekna.financas.dominio.Modelo

private val Forma = RoundedCornerShape(22.dp)

@Composable
fun TelaLancamentos(vm: LancamentosViewModel) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val c = LocalCores.current

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(c.ceu))
            .drawBehind {
                drawCircle(Brush.radialGradient(listOf(c.orbe1, Color.Transparent), Offset(size.width * .15f, 0f), size.width * .9f), size.width * .9f, Offset(size.width * .15f, 0f))
                drawCircle(Brush.radialGradient(listOf(c.orbe2, Color.Transparent), Offset(size.width, size.height * .25f), size.width * .7f), size.width * .7f, Offset(size.width, size.height * .25f))
            }
    ) {
        LazyColumn(
            Modifier.fillMaxSize().safeDrawingPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Cabecalho(estado.mes, vm::mudarMes) }
            item { CartaoSaldo(estado.resumo) }
            item { NovoLancamento(estado.mes, vm::lancar) }
            if (estado.resumo.porCategoria.isNotEmpty()) item { PorCategoria(estado.resumo) }
            item { Text("Extrato", style = MaterialTheme.typography.titleMedium, color = c.soft, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) }
            if (estado.itens.isEmpty()) item {
                Vidro { Text("Nenhum lançamento neste mês.", color = c.faint, modifier = Modifier.padding(4.dp)) }
            }
            items(estado.itens, key = { it.id }) { ItemExtrato(it, vm::excluir) }
            item { Rodape() }
        }
    }
}

@Composable
private fun Vidro(modifier: Modifier = Modifier, padding: Dp = 16.dp, conteudo: @Composable ColumnScope.() -> Unit) {
    val c = LocalCores.current
    Column(
        modifier
            .fillMaxWidth()
            .shadow(10.dp, Forma, ambientColor = Color(0x33143C28), spotColor = Color(0x55143C28))
            .clip(Forma)
            .background(c.vidro())
            .border(1.dp, Brush.verticalGradient(listOf(c.brilho, c.line)), Forma)
            .padding(padding),
        content = conteudo,
    )
}

@Composable
private fun Cabecalho(mes: String, mudarMes: (Int) -> Unit) {
    val c = LocalCores.current
    Column(Modifier.padding(horizontal = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Finanças da Casa", style = MaterialTheme.typography.bodySmall, color = c.soft)
                Text("Lançamentos", style = MaterialTheme.typography.headlineSmall, color = c.ink)
            }
            Text(
                "PILOTO KOTLIN", style = mono(11), color = c.accent,
                modifier = Modifier.clip(CircleShape).background(c.accentSoft).padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { mudarMes(-1) }) { Icon(Icones.anterior, "Mês anterior", tint = c.ink) }
            Text(
                Modelo.mesLabel(mes).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium, color = c.ink,
                modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            IconButton(onClick = { mudarMes(1) }) { Icon(Icones.proximo, "Próximo mês", tint = c.ink) }
        }
    }
}

@Composable
private fun CartaoSaldo(r: Modelo.ResumoMes) {
    val c = LocalCores.current
    Vidro(padding = 20.dp) {
        Text("Saldo do mês", style = MaterialTheme.typography.bodyMedium, color = c.soft)
        Text(Modelo.brl(r.saldo), style = mono(32, FontWeight.SemiBold), color = if (r.saldo < 0) c.saida else c.ink)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Indicador("Entradas", r.entradas, Icones.sobe, c.entrada, Modifier.weight(1f))
            Indicador("Despesas", r.despesas, Icones.desce, c.saida, Modifier.weight(1f))
        }
    }
}

@Composable
private fun Indicador(rotulo: String, valor: Long, icone: ImageVector, cor: Color, modifier: Modifier) {
    val c = LocalCores.current
    Row(modifier.clip(RoundedCornerShape(14.dp)).background(c.field).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icone, null, tint = cor, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(rotulo, style = MaterialTheme.typography.bodySmall, color = c.soft)
            Text(Modelo.brl(valor), style = mono(14), color = cor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun NovoLancamento(mes: String, lancar: (Formulario) -> br.com.systekna.financas.dominio.ErroValidacao?) {
    val c = LocalCores.current
    var tipo by rememberSaveable { mutableStateOf("despesa") }
    var descricao by rememberSaveable { mutableStateOf("") }
    var valor by rememberSaveable { mutableStateOf("") }
    // Data sugerida: hoje, se o mês aberto é o atual; senão o dia 1 do mês aberto.
    val hoje = Modelo.hojeISO()
    var data by rememberSaveable(mes) { mutableStateOf(LancamentosViewModel.brDeIso(if (Modelo.mesDe(hoje) == mes) hoje else "$mes-01")) }
    val categorias = if (tipo == "entrada") Modelo.CAT_ENTRADA else Modelo.CAT_DESPESA
    var categoria by rememberSaveable(tipo) { mutableStateOf(categorias.first()) }
    var erro by remember { mutableStateOf<Pair<String, String>?>(null) }

    Vidro {
        Text("Novo lançamento", style = MaterialTheme.typography.titleMedium, color = c.ink)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().clip(CircleShape).background(c.field).padding(4.dp)) {
            for ((valorTipo, rotulo) in listOf("despesa" to "Despesa", "entrada" to "Entrada")) {
                val ativo = tipo == valorTipo
                val cor = if (valorTipo == "entrada") c.entrada else c.saida
                Row(
                    Modifier.weight(1f).clip(CircleShape)
                        .background(if (ativo) cor.copy(alpha = .16f) else Color.Transparent)
                        .clickable(role = Role.Tab) { tipo = valorTipo; erro = null }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (valorTipo == "entrada") Icones.setaCima else Icones.setaBaixo, null, tint = if (ativo) cor else c.faint, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(rotulo, style = mono(13), color = if (ativo) cor else c.soft)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Campo("Descrição", descricao, { descricao = it; erro = null }, erro?.takeIf { it.first == "descricao" }?.second)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Campo("Valor (R$)", valor, { valor = it; erro = null }, erro?.takeIf { it.first == "valorCentavos" }?.second, Modifier.weight(1f), KeyboardType.Decimal, monoTexto = true)
            Campo("Data", data, { data = it; erro = null }, erro?.takeIf { it.first == "data" }?.second, Modifier.weight(1f), KeyboardType.Number, monoTexto = true, dica = "dd/mm/aaaa")
        }
        Spacer(Modifier.height(10.dp))
        Text("Categoria", style = MaterialTheme.typography.bodySmall, color = c.soft)
        Spacer(Modifier.height(6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(categorias) { cat ->
                val ativo = cat == categoria
                Text(
                    cat, style = MaterialTheme.typography.bodyMedium, color = if (ativo) c.onAccent else c.ink,
                    modifier = Modifier.clip(CircleShape)
                        .background(if (ativo) c.accent2 else c.field)
                        .clickable(role = Role.RadioButton) { categoria = cat }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                        .semantics { contentDescription = if (ativo) "$cat, selecionada" else cat }
                )
            }
        }
        erro?.takeIf { it.first !in setOf("descricao", "valorCentavos", "data") }?.let {
            Spacer(Modifier.height(8.dp)); Text(it.second, color = c.saida, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth().height(48.dp).clip(CircleShape).background(c.gloss())
                .clickable(role = Role.Button) {
                    val e = lancar(Formulario(tipo, descricao, valor, data, categoria))
                    if (e == null) { descricao = ""; valor = ""; erro = null } else erro = e.campo to (e.message ?: "Confira os campos.")
                },
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icones.mais, null, tint = c.onAccent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("LANÇAR", style = mono(14, FontWeight.SemiBold), color = c.onAccent)
        }
    }
}

@Composable
private fun Campo(
    rotulo: String, valor: String, mudar: (String) -> Unit, erro: String?, modifier: Modifier = Modifier.fillMaxWidth(),
    teclado: KeyboardType = KeyboardType.Text, monoTexto: Boolean = false, dica: String? = null,
) {
    val c = LocalCores.current
    OutlinedTextField(
        value = valor, onValueChange = mudar, label = { Text(rotulo) }, singleLine = true, modifier = modifier,
        placeholder = if (dica != null) { { Text(dica, color = c.faint) } } else null,
        isError = erro != null, supportingText = if (erro != null) { { Text(erro) } } else null,
        textStyle = if (monoTexto) mono(15) else MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(keyboardType = teclado, imeAction = ImeAction.Next),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = c.field, unfocusedContainerColor = c.field, errorContainerColor = c.field,
            focusedBorderColor = c.accent, unfocusedBorderColor = c.line, focusedLabelColor = c.accent, unfocusedLabelColor = c.soft,
            cursorColor = c.accent, focusedTextColor = c.ink, unfocusedTextColor = c.ink,
        ),
    )
}

@Composable
private fun PorCategoria(r: Modelo.ResumoMes) {
    val c = LocalCores.current
    val maior = r.porCategoria.first().second.coerceAtLeast(1)
    Vidro {
        Text("Despesas por categoria", style = MaterialTheme.typography.titleMedium, color = c.ink)
        Spacer(Modifier.height(10.dp))
        for ((cat, v) in r.porCategoria) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(cat, style = MaterialTheme.typography.bodyMedium, color = c.ink, modifier = Modifier.weight(1f))
                Text(Modelo.brl(v), style = mono(13), color = c.soft)
            }
            Spacer(Modifier.height(4.dp))
            Box(Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(c.line)) {
                Box(Modifier.fillMaxWidth(v.toFloat() / maior).fillMaxHeight().clip(CircleShape).background(c.saida.copy(alpha = .85f)))
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ItemExtrato(item: LancamentoSalvo, excluir: (LancamentoSalvo) -> Unit) {
    val c = LocalCores.current
    val l = item.lancamento
    val entrada = l.tipo == "entrada"
    val cor = if (entrada) c.entrada else c.saida
    var confirmar by remember { mutableStateOf(false) }
    Vidro(padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(cor.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
                Icon(if (entrada) Icones.setaCima else Icones.setaBaixo, null, tint = cor, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(l.descricao, style = MaterialTheme.typography.bodyLarge, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${Modelo.ddmm(l.data)} · ${l.categoria}", style = mono(12, FontWeight.Normal), color = c.faint)
            }
            Text((if (entrada) "+" else "−") + Modelo.brl(l.valorCentavos), style = mono(14), color = cor)
            if (item.travado) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    Icon(Icones.cadeado, "Travado: criado há mais de 2 dias", tint = c.faint, modifier = Modifier.size(18.dp))
                }
            } else {
                IconButton(onClick = { confirmar = true }) { Icon(Icones.lixeira, "Excluir ${l.descricao}", tint = c.soft, modifier = Modifier.size(20.dp)) }
            }
        }
    }
    if (confirmar) AlertDialog(
        onDismissRequest = { confirmar = false },
        icon = { Icon(Icones.alerta, null, tint = c.warn) },
        title = { Text("Excluir lançamento?") },
        text = { Text("“${l.descricao}” (${Modelo.brl(l.valorCentavos)}). Depois de 2 dias da criação ele não pode mais ser excluído.") },
        confirmButton = { TextButton(onClick = { confirmar = false; excluir(item) }) { Text("EXCLUIR", color = c.saida) } },
        dismissButton = { TextButton(onClick = { confirmar = false }) { Text("CANCELAR") } },
    )
}

@Composable
private fun Rodape() {
    val c = LocalCores.current
    Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icones.escudo, null, tint = c.faint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text("Dados só neste aparelho · regras e cripto iguais às do PWA", style = MaterialTheme.typography.bodySmall, color = c.faint)
    }
}
