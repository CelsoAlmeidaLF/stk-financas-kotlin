/* Ícones do stk-pkg-financ-icons.js (traço Lucide 24x24, ISC) como ImageVector: mesmo desenho do PWA, sem emoji.
   Desenhados em preto; o Icon() aplica a cor (equivale ao currentColor). */
package br.com.systekna.financas.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private fun lucide(nome: String, vararg caminhos: String): ImageVector =
    ImageVector.Builder(nome, 24.dp, 24.dp, 24f, 24f).apply {
        for (d in caminhos) addPath(
            pathData = addPathNodes(d), stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
        )
    }.build()

object Icones {
    val mais = lucide("plus", "M5 12h14", "M12 5v14")
    val lixeira = lucide("trash", "M3 6h18", "M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6", "M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2", "M10 11v6", "M14 11v6")
    val cadeado = lucide("lock", "M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a2 2 0 0 1 2-2z", "M7 11V7a5 5 0 0 1 10 0v4")
    val anterior = lucide("chevron-left", "m15 18-6-6 6-6")
    val proximo = lucide("chevron-right", "m9 18 6-6-6-6")
    val sobe = lucide("trending-up", "M22 7 13.5 15.5 8.5 10.5 2 17", "M16 7h6v6")
    val desce = lucide("trending-down", "M22 17 13.5 8.5 8.5 13.5 2 7", "M16 17h6v-6")
    val setaCima = lucide("arrow-up", "m5 12 7-7 7 7", "M12 19V5")
    val setaBaixo = lucide("arrow-down", "M12 5v14", "m19 12-7 7-7-7")
    val escudo = lucide(
        "shield-check",
        "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z",
        "m9 12 2 2 4-4"
    )
    val olho = lucide("eye", "M2.06 12.35a1 1 0 0 1 0-.7 10.75 10.75 0 0 1 19.88 0 1 1 0 0 1 0 .7 10.75 10.75 0 0 1-19.88 0", "M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0z")
    val olhoFechado = lucide(
        "eye-off",
        "M10.73 5.08A10.43 10.43 0 0 1 12 5c4.97 0 8.58 3.33 9.94 6.65a1 1 0 0 1 0 .7 10.75 10.75 0 0 1-1.44 2.49",
        "M14.08 14.16a3 3 0 0 1-4.24-4.24", "M17.48 17.5A10.75 10.75 0 0 1 2.06 12.35a1 1 0 0 1 0-.7 10.75 10.75 0 0 1 4.45-5.14", "m2 2 20 20"
    )
    val apagar = lucide("delete", "M10 5a2 2 0 0 0-1.34.52l-5.67 5.72a1 1 0 0 0 0 1.52l5.67 5.72A2 2 0 0 0 10 19h9a2 2 0 0 0 2-2V7a2 2 0 0 0-2-2z", "m12 9 6 6", "m18 9-6 6")
    val copiar = lucide("copy", "M10 8h10a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H10a2 2 0 0 1-2-2V10a2 2 0 0 1 2-2z", "M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2")
    val chave = lucide("key-round", "M2.59 18.41A2 2 0 0 0 2 19.83V21a1 1 0 0 0 1 1h3a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h1a1 1 0 0 0 1-1v-1a1 1 0 0 1 1-1h.17a2 2 0 0 0 1.42-.59l.81-.81a6.5 6.5 0 1 0-4.24-4.24z", "M16.5 7.5h.01")
    val backup = lucide("archive", "M3 3h18a1 1 0 0 1 1 1v3a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z", "M4 8v11a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8", "M10 12h4")
    val baixar = lucide("download", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "m7 10 5 5 5-5", "M12 15V3")
    val subir = lucide("upload", "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4", "m17 8-5-5-5 5", "M12 3v12")
    val fechar = lucide("x", "M18 6 6 18", "m6 6 12 12")
    val alerta = lucide("alert", "m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3", "M12 9v4", "M12 17h.01")
}
