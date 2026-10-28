package ru.riz.dohod

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/* ═══════════════════ Циферблат ═══════════════════ */

private val Platinum = Color(0xFFD5DBD6)
private val Lume = Color(0xFFF3E9B8)
private val HandRed = Color(0xFFE4553A)
private val Mint = Color(0xFF7FD1AE)
private val DialInk = Color(0xFFF4F0E1)
private val DialMute = Color(0xFF9FB8AC)

private fun DrawScope.polar(cx: Float, cy: Float, r: Float, a: Float) = Offset(cx + r * cos(a), cy + r * sin(a))

/**
 * Главный экран скина «Циферблат»: баланс в центре, по краю по штриху на день (длина = траты дня),
 * дуга показывает, какая часть бюджета (или дохода) потрачена, внизу два малых циферблата.
 */
@Composable
fun DialHero(
    balance: Long, income: Long, expense: Long, budget: Long, net: Long,
    daily: List<Long>, today: Int, monthName: String, dateChip: String,
    onBalance: () -> Unit, modifier: Modifier = Modifier
) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val base = if (budget > 0) budget else income
    val frac = if (base > 0) (expense.toFloat() / base).coerceIn(0f, 1f) else 0f
    val arc by animateFloatAsState(if (started) frac else 0f, tween(1100), label = "arc")
    val shown = animatedAmount(balance)

    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        val side = min(maxWidth.value, 372f)
        val k = side / 358f
        Box(Modifier.size(side.dp).clip(CircleShape).clickable(onClick = onBalance)) {
            Canvas(Modifier.fillMaxSize()) {
                val s = size.width / 358f
                val c = Offset(179f * s, 179f * s)
                // оправа: платина
                drawCircle(
                    Brush.sweepGradient(
                        listOf(Color(0xFFF2F4F1), Color(0xFF8E958F), Color(0xFFE9ECE8), Color(0xFF6D746F), Color(0xFFEEF1ED), Color(0xFF8A918B), Color(0xFFF2F4F1)),
                        center = c
                    ),
                    radius = 179f * s, center = c
                )
                // циферблат
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color(0xFF1D5646), Color(0xFF12392F), Color(0xFF0A2620)),
                        center = Offset(c.x, c.y - 40f * s), radius = 200f * s
                    ),
                    radius = 171f * s, center = c
                )
                drawCircle(Color.Black.copy(alpha = 0.45f), radius = 171f * s, center = c, style = Stroke(1.5f * s))
                // тонкая розетка (гильоше)
                for (i in 0 until 60) {
                    val a = (i / 60f * 2 * PI).toFloat()
                    drawCircle(
                        Color.White.copy(alpha = 0.045f), radius = 78f * s,
                        center = Offset(c.x + 46f * s * cos(a), c.y + 46f * s * sin(a)), style = Stroke(0.7f * s)
                    )
                }
                // штрихи-дни
                val days = max(daily.size, 28)
                val mx = max(1L, daily.maxOrNull() ?: 1L).toFloat()
                for (d in 0 until days) {
                    val a = (d.toFloat() / days * 2 * PI - PI / 2).toFloat()
                    val v = daily.getOrNull(d) ?: -1L
                    val isToday = d == today
                    var len = 5f
                    var col = Platinum
                    var alpha = 0.22f
                    if (v >= 0L && (today < 0 || d <= today)) {
                        len = if (v == 0L) 6f else 7f + 21f * sqrt(v / mx)
                        alpha = if (v > 0L) 0.9f else 0.45f
                    }
                    if (isToday) { col = Lume; alpha = 1f }
                    drawLine(col.copy(alpha = alpha), polar(c.x, c.y, 160f * s, a), polar(c.x, c.y, (160f - len) * s, a), strokeWidth = 2.6f * s, cap = StrokeCap.Round)
                    if (isToday) drawLine(HandRed, polar(c.x, c.y, 160f * s, a), polar(c.x, c.y, 154f * s, a), strokeWidth = 2.8f * s, cap = StrokeCap.Round)
                }
                // дорожка и дуга расходов
                drawCircle(Color.White.copy(alpha = 0.13f), radius = 121f * s, center = c, style = Stroke(2f * s))
                if (arc > 0f) {
                    drawArc(
                        Lume, startAngle = -90f, sweepAngle = 360f * arc, useCenter = false,
                        topLeft = Offset(c.x - 121f * s, c.y - 121f * s), size = Size(242f * s, 242f * s),
                        style = Stroke(2.6f * s, cap = StrokeCap.Round)
                    )
                }
                // метка темпа: где мы по календарю
                if (today >= 0) {
                    val a = ((today + 1f) / days * 2 * PI - PI / 2).toFloat()
                    drawLine(HandRed, polar(c.x, c.y, 128f * s, a), polar(c.x, c.y, 114f * s, a), strokeWidth = 2.4f * s, cap = StrokeCap.Round)
                }
            }

            // окошко с датой
            Box(
                Modifier.align(Alignment.TopCenter).offset(y = (82f * k).dp).size((66f * k).dp, (22f * k).dp)
                    .clip(RoundedCornerShape(5.dp)).background(Color.Black.copy(alpha = 0.38f))
                    .border(1.dp, Platinum.copy(alpha = 0.28f), RoundedCornerShape(5.dp)),
                contentAlignment = Alignment.Center
            ) { Text(dateChip, color = Color(0xFFEDE7D4), fontSize = (12f * k).sp, fontWeight = FontWeight.Medium) }

            Column(
                Modifier.align(Alignment.TopCenter).offset(y = (110f * k).dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("баланс", color = DialMute, fontSize = (12.5f * k).sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        (if (balance < 0) "−" else "") + grp(shown),
                        style = TextStyle(
                            fontFamily = DialNum, fontSize = (48f * k).sp, color = DialInk,
                            letterSpacing = (-1.6f * k).sp, fontFeatureSettings = "tnum, lnum"
                        ),
                        maxLines = 1
                    )
                    Text(" ₽", style = TextStyle(fontFamily = DialNum, fontSize = (26f * k).sp, color = DialMute), modifier = Modifier.padding(bottom = (6f * k).dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (net >= 0) "▲ " else "▼ ", color = if (net >= 0) Mint else Color(0xFFF08B74), fontSize = (10f * k).sp)
                    Text(
                        grp(net) + " ₽ за " + monthName, color = if (net >= 0) Mint else Color(0xFFF08B74),
                        fontSize = (12.5f * k).sp, fontWeight = FontWeight.Medium
                    )
                }
            }

            SubDial(
                "доход", grp(income), if (income > 0) 0.77f else 0f, Mint, k,
                Modifier.align(Alignment.TopStart).offset(x = ((179f - 58f - 34f) * k).dp, y = ((179f + 66f - 34f + 8f) * k).dp)
            )
            SubDial(
                "расход", grp(expense), if (income > 0) (expense.toFloat() / income).coerceIn(0f, 1f) else 0f, Lume, k,
                Modifier.align(Alignment.TopStart).offset(x = ((179f + 58f - 34f) * k).dp, y = ((179f + 66f - 34f + 8f) * k).dp)
            )
        }
    }
}

@Composable
private fun SubDial(label: String, value: String, f: Float, color: Color, k: Float, modifier: Modifier) {
    Box(modifier.size((68f * k).dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.width / 2
            drawCircle(Color.Black.copy(alpha = 0.30f), radius = r)
            drawCircle(Platinum.copy(alpha = 0.30f), radius = r, style = Stroke(1.dp.toPx()))
            val inset = r - 7.dp.toPx() * k
            drawArc(
                Color.White.copy(alpha = 0.12f), 135f, 270f, false,
                topLeft = Offset(r - inset, r - inset), size = Size(inset * 2, inset * 2),
                style = Stroke(2.2.dp.toPx() * k, cap = StrokeCap.Round)
            )
            if (f > 0f) drawArc(
                color, 135f, 270f * f, false,
                topLeft = Offset(r - inset, r - inset), size = Size(inset * 2, inset * 2),
                style = Stroke(2.6.dp.toPx() * k, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = DialMute, fontSize = (10f * k).sp)
            Text(value, color = DialInk, fontSize = (11.5f * k).sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
        }
    }
}

/** Короткая дата для окошка циферблата: «ср 28». */
fun dialDateChip(monthOffset: Int): String {
    val c = Calendar.getInstance()
    if (monthOffset != 0) c.add(Calendar.MONTH, monthOffset)
    val df = if (monthOffset == 0) java.text.SimpleDateFormat("EEE d", Locale("ru")) else java.text.SimpleDateFormat("LLL", Locale("ru"))
    return df.format(c.time).lowercase().trimEnd('.')
}

/* ═══════════════════ Книга ═══════════════════ */

/** Двойная линия, как в бухгалтерской книге. */
@Composable
fun DoubleRule(modifier: Modifier = Modifier, color: Color = LocalPal.current.text) {
    Box(modifier.fillMaxWidth().height(5.dp).drawBehind {
        drawRect(color, Offset(0f, 0f), Size(size.width, 1.5.dp.toPx()))
        drawRect(color, Offset(0f, size.height - 0.8.dp.toPx()), Size(size.width, 0.8.dp.toPx()))
    })
}

@Composable
fun LedgerHero(
    balance: Long, income: Long, expense: Long, net: Long, daily: List<Long>, today: Int,
    onBalance: () -> Unit, modifier: Modifier = Modifier
) {
    val p = LocalPal.current
    Column(modifier.fillMaxWidth()) {
        DoubleRule(color = p.text)
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onBalance).padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Остаток на счетах", color = p.muted, fontSize = 14.sp)
            Spacer(Modifier.width(4.dp))
            Ic(R.drawable.ic_chevron_right, p.muted, 14.dp)
        }
        BigAmount(balance, 58)
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                .drawBehind {
                    drawRect(p.text, Offset(0f, 0f), Size(size.width, 0.8.dp.toPx()))
                    drawRect(p.text, Offset(0f, size.height - 0.8.dp.toPx()), Size(size.width, 0.8.dp.toPx()))
                }
        ) {
            LedgerCol("Доход", "+" + grp(income), p.green, Modifier.weight(1f))
            Box(Modifier.width(0.8.dp).fillMaxHeight().background(p.line))
            LedgerCol("Расход", "−" + grp(expense), p.gold, Modifier.weight(1f))
            Box(Modifier.width(0.8.dp).fillMaxHeight().background(p.line))
            LedgerCol("Итог месяца", (if (net >= 0) "+" else "−") + grp(net), p.text, Modifier.weight(1f))
        }
        if (daily.isNotEmpty() && daily.any { it > 0 }) {
            Spacer(Modifier.height(14.dp))
            Canvas(Modifier.fillMaxWidth().height(46.dp)) {
                val mx = max(1L, daily.maxOrNull() ?: 1L).toFloat()
                val n = daily.size
                val step = size.width / n
                for (i in 0 until n) {
                    val x = step * (i + 0.5f)
                    val h = max(2.dp.toPx(), daily[i] / mx * (size.height - 6.dp.toPx()))
                    val hot = i == today
                    drawLine(
                        if (hot) p.gold else p.text, Offset(x, size.height - 1.dp.toPx()), Offset(x, size.height - 1.dp.toPx() - h),
                        strokeWidth = if (hot) 2.6.dp.toPx() else 1.3.dp.toPx()
                    )
                }
                drawRect(p.text, Offset(0f, size.height - 0.8.dp.toPx()), Size(size.width, 0.8.dp.toPx()))
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text("1", color = p.muted, fontSize = 12.sp)
                Text("расход по дням", color = p.muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                Text("${daily.size}", color = p.muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun LedgerCol(label: String, value: String, color: Color, modifier: Modifier) {
    val p = LocalPal.current
    Column(modifier.padding(vertical = 9.dp).padding(start = 12.dp)) {
        Text(label, color = p.muted, fontSize = 12.sp)
        Text(value, style = lora(20.sp, color, (-0.4).sp), maxLines = 1, softWrap = false)
    }
}

/** Пунктирная линия-выноска между названием и суммой. */
@Composable
fun Leader(modifier: Modifier = Modifier) {
    val p = LocalPal.current
    Box(
        modifier.height(1.dp).drawBehind {
            drawLine(
                p.muted.copy(alpha = 0.7f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.5.dp.toPx(), 3.dp.toPx()), 0f),
                cap = StrokeCap.Round
            )
        }
    )
}

/* ═══════════════════ Микрофон в нижней панели ═══════════════════ */

/** Заводная головка (Циферблат) или печать (Книга). Нажатие обрабатывает вызывающий. */
@Composable
fun DockMic(p: Pal, modifier: Modifier = Modifier) {
    if (p.skin == Skin.DIAL) {
        Box(modifier.size(76.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val c = Offset(size.width / 2, size.height / 2)
                val r = size.width / 2
                drawCircle(p.bg, radius = r)
                val n = 60
                for (i in 0 until n) {
                    val a = (i.toFloat() / n * 2 * PI).toFloat()
                    drawLine(
                        if (i % 2 == 0) Color(0xFFE3E7E3) else Color(0xFF6F7771),
                        Offset(c.x + (r - 11.dp.toPx()) * cos(a), c.y + (r - 11.dp.toPx()) * sin(a)),
                        Offset(c.x + (r - 4.dp.toPx()) * cos(a), c.y + (r - 4.dp.toPx()) * sin(a)),
                        strokeWidth = 2.6.dp.toPx()
                    )
                }
                drawCircle(
                    Brush.radialGradient(listOf(Color(0xFFF6F8F5), Color(0xFFB8BEB9), Color(0xFF8A918B)), center = Offset(c.x - 8.dp.toPx(), c.y - 10.dp.toPx()), radius = r),
                    radius = r - 13.dp.toPx(), center = c
                )
                drawCircle(HandRed, radius = 2.6.dp.toPx(), center = Offset(c.x, c.y - (r - 19.dp.toPx())))
            }
            Ic(R.drawable.ic_mic, Color(0xFF12302A), 28.dp)
        }
    } else {
        Box(
            modifier.size(76.dp).clip(CircleShape).background(p.bg).border(1.5.dp, p.text, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(62.dp).clip(CircleShape).background(p.gold), contentAlignment = Alignment.Center) {
                Ic(R.drawable.ic_mic, p.onGold, 28.dp)
            }
        }
    }
}

/* ═══════════════════ Выбор оформления (раздел «Ещё») ═══════════════════ */

@Composable
fun SkinPicker() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val p = LocalPal.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCaption("Оформление")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Skin.values().forEach { s ->
                val on = ThemeState.skin == s
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .border(if (on) 2.dp else 1.dp, if (on) p.gold else p.line, RoundedCornerShape(14.dp))
                        .clickable { ThemeState.pickSkin(ctx, s) }
                        .padding(10.dp)
                ) {
                    SkinSwatch(s, p.dark)
                    Spacer(Modifier.height(8.dp))
                    Text(s.title, color = p.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(s.hint, color = p.muted, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.fill).padding(3.dp)
        ) {
            ThemeMode.values().forEach { m ->
                val on = ThemeState.mode == m
                Box(
                    Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp))
                        .background(if (on) p.bg else Color.Transparent)
                        .clickable { ThemeState.pickMode(ctx, m) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(m.title, color = if (on) p.text else p.muted, fontSize = 14.sp, fontWeight = if (on) FontWeight.Medium else FontWeight.Normal)
                }
            }
        }
    }
}

/** Маленькая картинка скина для выбора: показывает его самого в текущем режиме. */
@Composable
private fun SkinSwatch(s: Skin, dark: Boolean) {
    val pal = palFor(s, dark)
    Canvas(Modifier.fillMaxWidth().height(86.dp).clip(RoundedCornerShape(10.dp))) {
        drawRect(pal.bg)
        if (s == Skin.DIAL) {
            val c = Offset(size.width / 2, size.height / 2)
            val r = size.height / 2 - 6.dp.toPx()
            drawCircle(Platinum, radius = r, center = c)
            drawCircle(Color(0xFF12392F), radius = r - 3.dp.toPx(), center = c)
            for (i in 0 until 28) {
                val a = (i / 28f * 2 * PI).toFloat()
                val l = if (i % 3 == 0) 7.dp.toPx() else 4.dp.toPx()
                drawLine(Platinum.copy(alpha = 0.8f), polar(c.x, c.y, r - 5.dp.toPx(), a), polar(c.x, c.y, r - 5.dp.toPx() - l, a), 1.4.dp.toPx())
            }
            drawArc(Lume, -90f, 230f, false, Offset(c.x - r * 0.62f, c.y - r * 0.62f), Size(r * 1.24f, r * 1.24f), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
            drawLine(Lume, Offset(c.x - r * 0.3f, c.y), Offset(c.x + r * 0.3f, c.y), 3.dp.toPx(), StrokeCap.Round)
        } else {
            val ink = pal.text
            drawRect(ink, Offset(10.dp.toPx(), 12.dp.toPx()), Size(size.width - 20.dp.toPx(), 1.5.dp.toPx()))
            drawRect(ink, Offset(10.dp.toPx(), 16.dp.toPx()), Size(size.width - 20.dp.toPx(), 0.8.dp.toPx()))
            drawRect(ink, Offset(10.dp.toPx(), 26.dp.toPx()), Size(size.width * 0.5f, 10.dp.toPx()))
            for (i in 0 until 3) {
                val y = 48.dp.toPx() + i * 11.dp.toPx()
                drawRect(ink.copy(alpha = 0.55f), Offset(10.dp.toPx(), y), Size(size.width * 0.28f, 2.dp.toPx()))
                drawLine(pal.muted, Offset(size.width * 0.4f, y + 1.dp.toPx()), Offset(size.width - 38.dp.toPx(), y + 1.dp.toPx()), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 5f)))
                drawRect(pal.gold, Offset(size.width - 32.dp.toPx(), y), Size(22.dp.toPx(), 2.dp.toPx()))
            }
        }
    }
}
