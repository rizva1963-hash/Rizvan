package ru.riz.dohod

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import android.content.Context
import androidx.core.view.WindowCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

enum class Skin(val id: String, val title: String, val hint: String) {
    DIAL("dial", "Циферблат", "Зелёный, как часы. Баланс на циферблате"),
    LEDGER("ledger", "Книга", "Бумага, засечки, строки как в бухгалтерской книге")
}

enum class ThemeMode(val id: String, val title: String) {
    SYSTEM("system", "Как в системе"), LIGHT("light", "Светлая"), DARK("dark", "Тёмная")
}

/** Выбранное оформление. Хранится в настройках, читается из любого экрана. */
object ThemeState {
    var loaded = false
    var skin by mutableStateOf(Skin.DIAL)
    var mode by mutableStateOf(ThemeMode.SYSTEM)

    fun load(ctx: Context) {
        skin = Skin.values().firstOrNull { it.id == Prefs.str(ctx, "skin", "dial") } ?: Skin.DIAL
        mode = ThemeMode.values().firstOrNull { it.id == Prefs.str(ctx, "theme_mode", "system") } ?: ThemeMode.SYSTEM
    }

    fun pickSkin(ctx: Context, s: Skin) { skin = s; Prefs.setStr(ctx, "skin", s.id) }
    fun pickMode(ctx: Context, m: ThemeMode) { mode = m; Prefs.setStr(ctx, "theme_mode", m.id) }
}

/** Палитра: четыре набора (два скина, светлая и тёмная версии каждого). */
data class Pal(
    val skin: Skin,
    val dark: Boolean,
    val bg: Color,      // фон экрана
    val card: Color,    // карточки
    val fill: Color,    // вторичные кнопки, поля
    val line: Color,    // рамки карточек
    val sep: Color,     // разделители строк
    val bar: Color,     // неактивные столбцы графиков
    val text: Color,
    val muted: Color,
    val gold: Color,    // акцент
    val onGold: Color,
    val green: Color,   // рост, доход
    val red: Color      // долги, превышение
) {
    val sepColor get() = sep
    val blue get() = gold
    val orange get() = gold
}

val DialDark = Pal(
    Skin.DIAL, true,
    bg = Color(0xFF0A211A), card = Color(0xFF0F2B23), fill = Color(0xFF173B31), line = Color(0xFF285044),
    sep = Color(0xFF16372E), bar = Color(0xFF3D6A5B), text = Color(0xFFF1EEDF), muted = Color(0xFF8FA79C),
    gold = Color(0xFFF3E9B8), onGold = Color(0xFF0A211A), green = Color(0xFF7FD1AE), red = Color(0xFFF08B74)
)
val DialLight = Pal(
    Skin.DIAL, false,
    bg = Color(0xFFEBF1EA), card = Color(0xFFF7FAF5), fill = Color(0xFFDDE8DE), line = Color(0xFFC3D3C7),
    sep = Color(0xFFD3DFD5), bar = Color(0xFFA9C2B5), text = Color(0xFF0E2A22), muted = Color(0xFF58766A),
    gold = Color(0xFF1B5B47), onGold = Color(0xFFF3F8F2), green = Color(0xFF1C7A56), red = Color(0xFFC0432B)
)
val LedgerLight = Pal(
    Skin.LEDGER, false,
    bg = Color(0xFFF1EBDD), card = Color(0xFFE9E1CE), fill = Color(0xFFE2D9C3), line = Color(0xFFCFC5AD),
    sep = Color(0xFFD6CCB5), bar = Color(0xFFB9AE94), text = Color(0xFF1B1A17), muted = Color(0xFF766E5C),
    gold = Color(0xFFB8432A), onGold = Color(0xFFF7F1E3), green = Color(0xFF2F5D43), red = Color(0xFFB8432A)
)
val LedgerDark = Pal(
    Skin.LEDGER, true,
    bg = Color(0xFF1A1814), card = Color(0xFF221F1A), fill = Color(0xFF2C2821), line = Color(0xFF3C372D),
    sep = Color(0xFF2E2A22), bar = Color(0xFF5B5442), text = Color(0xFFEFE7D3), muted = Color(0xFF9D947D),
    gold = Color(0xFFE2724F), onGold = Color(0xFF1A1814), green = Color(0xFF86BA9A), red = Color(0xFFE2724F)
)

fun palFor(skin: Skin, dark: Boolean): Pal = when (skin) {
    Skin.DIAL -> if (dark) DialDark else DialLight
    Skin.LEDGER -> if (dark) LedgerDark else LedgerLight
}

val LocalPal = staticCompositionLocalOf { DialDark }

/** Шрифты для крупных сумм: «Книга» на Lora с засечками, «Циферблат» на тонком Inter Display. */
val SerifNum = FontFamily(
    Font(R.font.lora_regular, FontWeight.Normal),
    Font(R.font.lora_medium, FontWeight.Medium)
)
val DialNum = FontFamily(
    Font(R.font.inter_display_light, FontWeight.Normal),
    Font(R.font.inter_display_regular, FontWeight.Medium)
)

fun serif(size: TextUnit, color: Color, spacing: TextUnit = 0.sp) = TextStyle(
    fontFamily = if (ThemeState.skin == Skin.DIAL) DialNum else SerifNum,
    fontSize = size, color = color, letterSpacing = spacing,
    fontFeatureSettings = "lnum, tnum"
)

/** Всегда с засечками (заголовки «Книги»). */
fun lora(size: TextUnit, color: Color, spacing: TextUnit = 0.sp) = TextStyle(
    fontFamily = SerifNum, fontSize = size, color = color, letterSpacing = spacing,
    fontFeatureSettings = "lnum, tnum"
)

/** Табличные цифры для сумм в списках. */
const val TNUM = "tnum, lnum"

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    androidx.compose.runtime.remember { if (!ThemeState.loaded) { ThemeState.load(ctx); ThemeState.loaded = true }; 0 }
    val dark = when (ThemeState.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val p = palFor(ThemeState.skin, dark)
    val view = LocalView.current
    SideEffect {
        val w = (view.context as? Activity)?.window
        if (w != null) {
            val c = WindowCompat.getInsetsController(w, view)
            c.isAppearanceLightStatusBars = !dark
            c.isAppearanceLightNavigationBars = !dark
            w.decorView.setBackgroundColor(p.bg.toArgb())
        }
    }
    CompositionLocalProvider(LocalPal provides p) {
        MaterialTheme(
            typography = androidx.compose.material3.Typography(
                bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 1.35.em, letterSpacing = 0.sp)
            ),
            colorScheme = (if (dark) darkColorScheme() else lightColorScheme()).copy(
                primary = p.gold, onPrimary = p.onGold, background = p.bg, onBackground = p.text,
                surface = p.card, onSurface = p.text, surfaceVariant = p.fill, onSurfaceVariant = p.muted,
                error = p.red, outline = p.line
            ),
            content = content
        )
    }
}
