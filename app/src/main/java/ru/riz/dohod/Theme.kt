package ru.riz.dohod

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Палитра «графит и шампань» из утверждённого макета. */
data class Pal(
    val dark: Boolean,
    val bg: Color,      // фон экрана
    val card: Color,    // карточки
    val fill: Color,    // вторичные кнопки, поля
    val line: Color,    // рамки карточек
    val sep: Color,     // разделители строк
    val bar: Color,     // неактивные столбцы графиков
    val text: Color,
    val muted: Color,
    val gold: Color,    // акцент: шампань
    val onGold: Color,
    val green: Color,   // рост, доход
    val red: Color      // долги, превышение
) {
    // совместимость со старыми экранами
    val sepColor get() = sep
    val blue get() = gold
    val orange get() = gold
}

val GraphitePal = Pal(
    dark = true,
    bg = Color(0xFF0B0D10),
    card = Color(0xFF12161B),
    fill = Color(0xFF1A1F26),
    line = Color(0xFF252B33),
    sep = Color(0xFF1B1F26),
    bar = Color(0xFF3A414C),
    text = Color(0xFFF1EEE7),
    muted = Color(0xFF8E959F),
    gold = Color(0xFFD9B96F),
    onGold = Color(0xFF1A1407),
    green = Color(0xFF67CBA0),
    red = Color(0xFFE59A8C)
)

val LocalPal = staticCompositionLocalOf { GraphitePal }

/** Шрифт для крупных сумм (засечки, как в макете). */
val SerifNum = FontFamily(
    Font(R.font.lora_regular, FontWeight.Normal),
    Font(R.font.lora_medium, FontWeight.Medium)
)

fun serif(size: TextUnit, color: Color, spacing: TextUnit = 0.sp) = TextStyle(
    fontFamily = SerifNum, fontSize = size, color = color, letterSpacing = spacing,
    fontFeatureSettings = "lnum, tnum"
)

/** Табличные цифры для сумм в списках. */
const val TNUM = "tnum, lnum"

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val p = GraphitePal
    CompositionLocalProvider(LocalPal provides p) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = p.gold, onPrimary = p.onGold, background = p.bg, onBackground = p.text,
                surface = p.card, onSurface = p.text, surfaceVariant = p.fill, onSurfaceVariant = p.muted,
                error = p.red, outline = p.line
            ),
            content = content
        )
    }
}
