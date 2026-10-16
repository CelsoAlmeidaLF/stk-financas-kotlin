/* Componentes de tela compartilhados: fundo com orbes, cartão de vidro, campo de texto/senha e botões. */
package br.com.systekna.financas.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val Forma = RoundedCornerShape(22.dp)

@Composable
internal fun Fundo(conteudo: @Composable BoxScope.() -> Unit) {
    val c = LocalCores.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(c.ceu))
            .drawBehind {
                drawCircle(Brush.radialGradient(listOf(c.orbe1, Color.Transparent), Offset(size.width * .15f, 0f), size.width * .9f), size.width * .9f, Offset(size.width * .15f, 0f))
                drawCircle(Brush.radialGradient(listOf(c.orbe2, Color.Transparent), Offset(size.width, size.height * .25f), size.width * .7f), size.width * .7f, Offset(size.width, size.height * .25f))
            },
        content = conteudo,
    )
}

@Composable
internal fun Vidro(modifier: Modifier = Modifier, padding: Dp = 16.dp, conteudo: @Composable ColumnScope.() -> Unit) {
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

// senha = true: esconde o texto e mostra o botão de olho aberto/fechado para ver o que foi digitado.
@Composable
internal fun Campo(
    rotulo: String, valor: String, mudar: (String) -> Unit, erro: String?, modifier: Modifier = Modifier.fillMaxWidth(),
    teclado: KeyboardType = KeyboardType.Text, monoTexto: Boolean = false, dica: String? = null, senha: Boolean = false,
) {
    val c = LocalCores.current
    var ver by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = valor, onValueChange = mudar, label = { Text(rotulo) }, singleLine = true, modifier = modifier,
        placeholder = if (dica != null) { { Text(dica, color = c.faint) } } else null,
        isError = erro != null, supportingText = if (erro != null) { { Text(erro) } } else null,
        textStyle = if (monoTexto) mono(15) else MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(keyboardType = if (senha && !ver) KeyboardType.Password else teclado, imeAction = ImeAction.Next, autoCorrectEnabled = !senha),
        visualTransformation = if (senha && !ver) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (senha) { {
            IconButton(onClick = { ver = !ver }) { Icon(if (ver) Icones.olhoFechado else Icones.olho, if (ver) "Ocultar" else "Mostrar", tint = c.soft) }
        } } else null,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = c.field, unfocusedContainerColor = c.field, errorContainerColor = c.field,
            focusedBorderColor = c.accent, unfocusedBorderColor = c.line, focusedLabelColor = c.accent, unfocusedLabelColor = c.soft,
            cursorColor = c.accent, focusedTextColor = c.ink, unfocusedTextColor = c.ink,
        ),
    )
}

@Composable
internal fun Botao(texto: String, icone: ImageVector?, modifier: Modifier = Modifier, principal: Boolean = true, habilitado: Boolean = true, aoTocar: () -> Unit) {
    val c = LocalCores.current
    val fundo = if (principal) c.gloss() else Brush.verticalGradient(listOf(c.field, c.field))
    val cor = if (principal) c.onAccent else c.ink
    Row(
        modifier.fillMaxWidth().height(48.dp).clip(CircleShape).background(fundo)
            .then(if (principal) Modifier else Modifier.border(1.dp, c.line, CircleShape))
            .clickable(enabled = habilitado, role = Role.Button, onClick = aoTocar)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        val alfa = if (habilitado) 1f else .45f
        if (icone != null) { Icon(icone, null, tint = cor.copy(alpha = alfa), modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(texto, style = mono(14, FontWeight.SemiBold), color = cor.copy(alpha = alfa))
    }
}
