/* Tokens do aero-fin.css (paleta financeira) em Compose: claro e escuro, Space Grotesk + IBM Plex Mono. */
package br.com.systekna.financas.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import br.com.systekna.financas.R

@Immutable
data class Cores(
    val escuro: Boolean,
    val page: Color, val ink: Color, val soft: Color, val faint: Color, val line: Color,
    val glassTop: Color, val glassBot: Color, val brilho: Color, val field: Color,
    val accent: Color, val accent2: Color, val accentSoft: Color, val onAccent: Color,
    val entrada: Color, val saida: Color, val warn: Color, val info: Color,
    val ceu: List<Color>, val orbe1: Color, val orbe2: Color,
)

private val Claro = Cores(
    escuro = false,
    page = Color(0xFFEEF4F0), ink = Color(0xFF0E1210), soft = Color(0xFF4A5E54), faint = Color(0xFF82948A), line = Color(0x140E1210),
    glassTop = Color(0xA8FFFFFF), glassBot = Color(0x6BFFFFFF), brilho = Color(0x99FFFFFF), field = Color(0xB3FFFFFF),
    accent = Color(0xFF2F9A63), accent2 = Color(0xFF6FCF97), accentSoft = Color(0x296FCF97), onAccent = Color(0xFF0E1210),
    entrada = Color(0xFF2F9A63), saida = Color(0xFFC25A5A), warn = Color(0xFFB08A32), info = Color(0xFF4F7FB0),
    ceu = listOf(Color(0xFFDCEDE3), Color(0xFFEEF4F0), Color(0xFFF6FAF7)), orbe1 = Color(0x736FCF97), orbe2 = Color(0x477FA6CF),
)

private val Escuro = Cores(
    escuro = true,
    page = Color(0xFF0E1210), ink = Color(0xFFEAF0EC), soft = Color(0xFF8FA098), faint = Color(0xFF66766E), line = Color(0xFF2C3733),
    glassTop = Color(0x94202824), glassBot = Color(0x6B171D1A), brilho = Color(0x1FFFFFFF), field = Color(0x990E1210),
    accent = Color(0xFF6FCF97), accent2 = Color(0xFF4A8A67), accentSoft = Color(0x1F6FCF97), onAccent = Color(0xFF0E1210),
    entrada = Color(0xFF6FCF97), saida = Color(0xFFE08585), warn = Color(0xFFE0C185), info = Color(0xFF7FA6CF),
    ceu = listOf(Color(0xFF131A16), Color(0xFF0E1210), Color(0xFF0A0D0B)), orbe1 = Color(0x336FCF97), orbe2 = Color(0x1F7FA6CF),
)

val LocalCores = staticCompositionLocalOf { Claro }

val SpaceGrotesk = FontFamily(
    Font(R.font.space_grotesk_400, FontWeight.Normal),
    Font(R.font.space_grotesk_500, FontWeight.Medium),
    Font(R.font.space_grotesk_700, FontWeight.Bold),
)
val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_400, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_500, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_600, FontWeight.SemiBold),
)

// Números, valores e botões em mono, como no PWA.
fun mono(tamanho: Int, peso: FontWeight = FontWeight.Medium) = TextStyle(fontFamily = PlexMono, fontWeight = peso, fontSize = tamanho.sp)

@Composable
fun TemaFinancas(escuro: Boolean = isSystemInDarkTheme(), conteudo: @Composable () -> Unit) {
    val c = if (escuro) Escuro else Claro
    val esquema = (if (escuro) darkColorScheme() else lightColorScheme()).copy(
        primary = c.accent, onPrimary = c.onAccent, background = c.page, onBackground = c.ink,
        surface = c.page, onSurface = c.ink, onSurfaceVariant = c.soft, outline = c.line, error = c.saida,
    )
    val base = Typography()
    val tipografia = Typography(
        headlineSmall = base.headlineSmall.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium),
        bodyLarge = base.bodyLarge.copy(fontFamily = SpaceGrotesk),
        bodyMedium = base.bodyMedium.copy(fontFamily = SpaceGrotesk),
        bodySmall = base.bodySmall.copy(fontFamily = SpaceGrotesk),
        labelLarge = base.labelLarge.copy(fontFamily = PlexMono, fontWeight = FontWeight.Medium),
        labelMedium = base.labelMedium.copy(fontFamily = SpaceGrotesk),
    )
    CompositionLocalProvider(LocalCores provides c) {
        MaterialTheme(colorScheme = esquema, typography = tipografia, content = conteudo)
    }
}

// Vidro: gradiente translúcido de cima para baixo (o desfoque real do backdrop-filter fica para a próxima etapa, com Haze).
fun Cores.vidro() = Brush.verticalGradient(listOf(glassTop, glassBot))
fun Cores.gloss() = Brush.verticalGradient(
    if (escuro) listOf(Color(0xFF9BE3B8), Color(0xFF6FCF97), Color(0xFF4A8A67), Color(0xFF5FB585))
    else listOf(Color(0xFFA8EBC4), Color(0xFF6FCF97), Color(0xFF3FA06C), Color(0xFF57BC85))
)
