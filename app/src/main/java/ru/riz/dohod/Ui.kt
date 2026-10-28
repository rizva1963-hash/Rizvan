package ru.riz.dohod

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Плавная кривая из макета: cubic-bezier(.16,1,.3,1). */
val EaseOutExpo = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
val EasePress = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** Появление снизу с задержкой (как .in1/.in2/.in3 в макете). */
@Composable
fun Modifier.rise(delayMs: Int = 0): Modifier {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // Если у человека отключены анимации, показываем сразу: иначе блок мог бы остаться невидимым
    val off = remember {
        try {
            android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (_: Exception) { false }
    }
    val a = remember { Animatable(if (off) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (off) return@LaunchedEffect
        kotlinx.coroutines.delay(delayMs.toLong())
        a.animateTo(1f, tween(600, easing = EaseOutExpo))
    }
    return this.graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * 14.dp.toPx()
    }
}

/** Лёгкое сжатие при нажатии. */
@Composable
fun Modifier.pressable(onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) 0.97f else 1f, tween(160, easing = EasePress), label = "press")
    return this
        .graphicsLayer { scaleX = s; scaleY = s }
        .clickable(interactionSource = src, indication = null, onClick = onClick)
}

/** Сумма, которая «набегает» при появлении и при смене значения. */
@Composable
fun animatedAmount(value: Long): Long {
    val a = remember { Animatable(0f) }
    LaunchedEffect(value) { a.animateTo(value.toFloat(), tween(900, easing = EaseOutExpo)) }
    return if (a.isRunning) a.value.toLong() else value
}

/** 2486300 → «2 486 300» */
fun grp(v: Long): String = kotlin.math.abs(v).toString().reversed().chunked(3).joinToString(" ").reversed()

fun signed(v: Long): String = (if (v < 0) "−" else if (v > 0) "+" else "") + rub(v)

@Composable
fun Ic(res: Int, tint: Color, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Icon(painterResource(res), contentDescription = null, tint = tint, modifier = modifier.size(size))
}

/** Крупная сумма с засечками: «2 486 300 ₽» где ₽ меньше и серый. */
@Composable
fun BigAmount(value: Long, size: Int, negative: Boolean = value < 0, animate: Boolean = true) {
    val p = LocalPal.current
    val shown = if (animate) animatedAmount(value) else value
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            (if (negative) "−" else "") + grp(shown),
            style = serif(size.sp, p.text, (-size * 0.026f).sp),
            maxLines = 1
        )
        Text(" ₽", style = serif((size * 0.6f).sp, p.muted), modifier = Modifier.padding(bottom = (size * 0.08f).dp))
    }
}

/** Треугольник роста/падения. */
@Composable
fun Delta(up: Boolean, color: Color) {
    Canvas(Modifier.size(10.dp)) {
        val path = Path().apply {
            if (up) {
                moveTo(size.width / 2, size.height * 0.1f); lineTo(size.width * 0.92f, size.height * 0.8f); lineTo(size.width * 0.08f, size.height * 0.8f)
            } else {
                moveTo(size.width / 2, size.height * 0.9f); lineTo(size.width * 0.92f, size.height * 0.2f); lineTo(size.width * 0.08f, size.height * 0.2f)
            }
            close()
        }
        drawPath(path, color)
    }
}

/** Главная кнопка цвета шампань. */
@Composable
fun GoldButton(text: String, modifier: Modifier = Modifier, icon: Int? = null, enabled: Boolean = true, onClick: () -> Unit) {
    val p = LocalPal.current
    Row(
        modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (enabled) p.gold else p.fill)
            .pressable { if (enabled) onClick() }
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Ic(icon, if (enabled) p.onGold else p.muted)
            Spacer(Modifier.width(10.dp))
        }
        Text(text, color = if (enabled) p.onGold else p.muted, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Вторичная кнопка: графит с рамкой. */
@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, icon: Int? = null, color: Color? = null, onClick: () -> Unit) {
    val p = LocalPal.current
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(p.fill)
            .border(1.dp, p.line, RoundedCornerShape(8.dp))
            .pressable(onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Ic(icon, color ?: p.text, 18.dp)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = color ?: p.text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

/** Карточка из макета: #12161B, рамка #252B33, радиус 8. */
@Composable
fun CardBox(modifier: Modifier = Modifier, padding: Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPal.current
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(p.card)
            .border(BorderStroke(1.dp, p.line), RoundedCornerShape(8.dp))
            .padding(padding),
        content = content
    )
}

/** Тонкая полоса прогресса с плавным заполнением. */
@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier, color: Color? = null) {
    val p = LocalPal.current
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(900, easing = EaseOutExpo), label = "bar")
    Box(
        modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(p.line)
    ) {
        if (f > 0.001f) {
            Box(
                Modifier
                    .fillMaxWidth(f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color ?: p.gold)
            )
        }
    }
}

/** Вкладки с подчёркиванием (Операции | Счета | Цели). */
@Composable
fun UnderlineTabs(items: List<String>, selected: Int, onSelect: (Int) -> Unit, gap: Dp = 22.dp) {
    val p = LocalPal.current
    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
        items.forEachIndexed { i, label ->
            val active = i == selected
            Box(
                Modifier
                    .heightIn(min = 44.dp)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(i) }
                    .drawBehind {
                        if (active) drawRect(p.text, topLeft = Offset(0f, size.height - 2.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width, 2.dp.toPx()))
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label, fontSize = 15.sp,
                    color = if (active) p.text else p.muted,
                    fontWeight = if (active) FontWeight.Medium else FontWeight.Normal
                )
            }
        }
    }
}

/** Отступ внизу списков: под док-панель с микрофоном и жестовую полосу. */
val LocalDockPad = androidx.compose.runtime.staticCompositionLocalOf { 96.dp }

/** Монограмма категории: ритм списка и быстрый взгляд на тип записи. */
@Composable
fun CategoryBadge(t: Tx) {
    val p = LocalPal.current
    Box(
        Modifier
            .size(40.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(p.fill),
        contentAlignment = Alignment.Center
    ) {
        Text(
            t.category.firstOrNull()?.uppercase() ?: "•",
            style = serif(18.sp, if (t.income) p.green else p.gold)
        )
    }
}

/** Строка операции: монограмма, название и категория слева, сумма и время справа. */
@Composable
fun TxRow(t: Tx, showSep: Boolean = false, showDate: Boolean = false, onClick: () -> Unit) {
    val p = LocalPal.current
    val bank = t.source == Source.SBER || t.source == Source.TBANK
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        CategoryBadge(t)
        Column(Modifier.weight(1f)) {
            Text(txTitle(t), color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (bank) t.category + ", " + Source.label(t.source) else t.category,
                color = p.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                signedRub(t), fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                color = if (t.income) p.green else p.text,
                style = TextStyle(fontFeatureSettings = TNUM)
            )
            Text(if (showDate) Stats.shortDate(t.ts) else Stats.time(t.ts), color = p.muted, fontSize = 13.sp, style = TextStyle(fontFeatureSettings = TNUM))
        }
    }
}

/** Один день: заголовок с итогом расходов и записи под ним. */
@Composable
fun DayBlock(day: String, txs: List<Tx>, onEdit: (Tx) -> Unit) {
    val p = LocalPal.current
    val spent = txs.filter { !it.income }.sumOf { it.amount }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .drawBehind { drawRect(p.sep, topLeft = Offset(0f, size.height - 1.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx())) }
                .padding(bottom = 8.dp)
        ) {
            Text(day, color = p.text, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (spent > 0) Text("−" + rub(spent), color = p.muted, fontSize = 14.sp, style = TextStyle(fontFeatureSettings = TNUM))
        }
        txs.forEach { t -> TxRow(t) { onEdit(t) } }
    }
}

/** Короткое название записи: «Кофе», «Марат», «Kofemaniya». */
fun txTitle(t: Tx): String {
    if (t.source == Source.SBER || t.source == Source.TBANK) return t.text
    if (t.person.isNotBlank()) return t.person
    val words = t.text.replace(Regex("""[\d.,]+\s*(₽|руб\w*|р\.?|тыс\w*|к)?"""), " ")
        .split(Regex("""\s+""")).filter { it.length > 1 }
    val skip = setOf("выпил", "выпила", "потратил", "потратила", "купил", "купила", "заплатил", "заплатила", "на", "за",
        "рублей", "рубля", "руб", "я", "даю", "дал", "получил", "получила", "оплатил", "мне")
    val useful = words.filter { it.lowercase() !in skip }
    return (useful.take(3).joinToString(" ").ifBlank { t.category }).replaceFirstChar { it.uppercase() }
}

@Composable
fun SectionCaption(text: String, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    Text(text, color = p.muted, fontSize = 14.sp, modifier = modifier)
}

@Composable
fun ScreenTitle(text: String) {
    val p = LocalPal.current
    Text(text, color = p.text, fontSize = 24.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.3).sp)
}

/** Поле ввода в стиле макета. */
@Composable
fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    numeric: Boolean = false,
    big: Boolean = false,
    singleLine: Boolean = true,
    leading: Int? = null,
    onDone: (() -> Unit)? = null
) {
    val p = LocalPal.current
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(p.fill)
            .border(1.dp, p.line, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = if (big) 14.dp else 12.dp),
        verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top
    ) {
        if (leading != null) {
            Ic(leading, p.muted, 18.dp)
            Spacer(Modifier.width(10.dp))
        }
        Box(Modifier.weight(1f)) {
            val style = if (big) serif(30.sp, p.text) else TextStyle(color = p.text, fontSize = 16.sp)
            if (value.isEmpty()) Text(placeholder, style = style.copy(color = p.muted), maxLines = if (singleLine) 1 else 4)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                minLines = if (singleLine) 1 else 3,
                textStyle = style,
                cursorBrush = SolidColor(p.gold),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
                    imeAction = if (singleLine) ImeAction.Done else ImeAction.Default
                ),
                keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Строка-переключатель для настроек. */
@Composable
fun ToggleRow(title: String, subtitle: String? = null, checked: Boolean, showSep: Boolean = true, onChange: (Boolean) -> Unit) {
    val p = LocalPal.current
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (showSep) Modifier.drawBehind { drawRect(p.sep, size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx())) } else Modifier)
            .heightIn(min = 56.dp)
            .clickable { onChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = p.text, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, color = p.muted, fontSize = 13.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = p.onGold, checkedTrackColor = p.gold,
                uncheckedThumbColor = p.muted, uncheckedTrackColor = p.fill, uncheckedBorderColor = p.line
            )
        )
    }
}

/** Строка «подпись — значение ›» для карточек и настроек. */
@Composable
fun KvRow(label: String, value: String, showSep: Boolean = true, valueColor: Color? = null, chevron: Boolean = false, onClick: (() -> Unit)? = null, labelColor: Color? = null) {
    val p = LocalPal.current
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (showSep) Modifier.drawBehind { drawRect(p.sep, size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx())) } else Modifier)
            .heightIn(min = 48.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = labelColor ?: p.muted, fontSize = if (labelColor != null) 16.sp else 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = valueColor ?: p.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, style = TextStyle(fontFeatureSettings = TNUM))
        if (chevron) {
            Spacer(Modifier.width(6.dp))
            Ic(R.drawable.ic_chevron_right, p.muted, 14.dp)
        }
    }
}

/** Чипы выбора категории. */
@Composable
fun ChipGrid(items: List<String>, selected: String, onSelect: (String) -> Unit) {
    val p = LocalPal.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { c ->
                    val active = c == selected
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (active) p.gold else p.fill)
                            .border(1.dp, if (active) p.gold else p.line, RoundedCornerShape(8.dp))
                            .pressable { onSelect(c) }
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            c, color = if (active) p.onGold else p.text, fontSize = 14.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Нижняя шторка. */
@Composable
fun Sheet(visible: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPal.current
    if (visible) BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(tween(220)), exit = fadeOut(tween(200))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xB3000000))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            )
        }
        AnimatedVisibility(
            visible,
            modifier = Modifier.align(Alignment.BottomCenter).statusBarsPadding(),
            enter = slideInVertically(tween(420, easing = EaseOutExpo)) { it },
            exit = slideOutVertically(tween(240)) { it }
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(p.card)
                    .border(1.dp, p.line, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 24.dp)
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(p.line)
                    )
                }
                Spacer(Modifier.height(14.dp))
                content()
            }
        }
    }
}

/** Шапка шторки: «Отмена — Заголовок — Готово». */
@Composable
fun SheetHeader(title: String, left: String? = "Отмена", right: String? = null, onLeft: () -> Unit = {}, onRight: () -> Unit = {}) {
    val p = LocalPal.current
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(bottom = 8.dp)
    ) {
        if (left != null) {
            Text(
                left, color = p.muted, fontSize = 16.sp,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clickable(onClick = onLeft)
                    .padding(vertical = 10.dp)
            )
        }
        Text(title, color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.Center))
        if (right != null) {
            Text(
                right, color = p.gold, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clickable(onClick = onRight)
                    .padding(vertical = 10.dp)
            )
        }
    }
}

/** Столбчатая диаграмма: серые столбцы, текущий — шампань; плавно растут. */
@Composable
fun BarChart(values: List<Long>, labels: List<String>, current: Int, height: Dp = 112.dp, axis: Boolean = true) {
    val p = LocalPal.current
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val niceMax = niceCeil(max)
    val grow = remember(values) { Animatable(0f) }
    LaunchedEffect(values) { grow.animateTo(1f, tween(800, easing = EaseOutExpo)) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (axis) {
                Column(
                    Modifier
                        .width(36.dp)
                        .height(height),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    listOf(niceMax, niceMax / 2, 0L).forEach {
                        Text(short(it), color = p.muted, fontSize = 11.sp)
                    }
                }
            }
            Canvas(
                Modifier
                    .weight(1f)
                    .height(height)
            ) {
                val n = values.size.coerceAtLeast(1)
                val gap = 10.dp.toPx()
                val pad = 4.dp.toPx()
                val w = (size.width - pad * 2 - gap * (n - 1)) / n
                drawRect(p.sep, Offset(0f, 0f), androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()))
                drawRect(p.sep, Offset(0f, size.height / 2), androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()))
                drawRect(p.line, Offset(0f, size.height - 1.dp.toPx()), androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()))
                values.forEachIndexed { i, v ->
                    val h = (v.toFloat() / niceMax * size.height * grow.value).coerceAtLeast(if (v > 0) 2f else 0f)
                    val x = pad + i * (w + gap)
                    drawRoundRect(
                        color = if (i == current) p.gold else p.bar,
                        topLeft = Offset(x, size.height - h),
                        size = androidx.compose.ui.geometry.Size(w, h),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (axis) Spacer(Modifier.width(36.dp))
            Row(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                labels.forEachIndexed { i, l ->
                    Text(
                        l, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, maxLines = 1,
                        color = if (i == current) p.text else p.muted,
                        fontWeight = if (i == current) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }
    }
}

/** Округление шкалы вверх: 268 000 → 300 000. */
fun niceCeil(v: Long): Long {
    if (v <= 0) return 1
    var m = 1L
    while (m * 10 <= v) m *= 10
    val steps = listOf(1L, 2L, 3L, 5L, 10L)
    for (s in steps) if (s * m >= v) return s * m
    return 10 * m
}

/** 300000 → «300к», 1500000 → «1,5м». */
fun short(v: Long): String = when {
    v >= 1_000_000 -> (if (v % 1_000_000 == 0L) (v / 1_000_000).toString() else String.format("%.1f", v / 1_000_000.0).replace('.', ',')) + "м"
    v >= 1_000 -> (v / 1_000).toString() + "к"
    else -> v.toString()
}

@Composable
fun EmptyNote(text: String) {
    val p = LocalPal.current
    Text(
        text, color = p.muted, fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 22.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp, horizontal = 12.dp)
    )
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 2))
            .background(color)
    )
}

@Composable
fun IconBox(res: Int, onClick: () -> Unit) {
    val p = LocalPal.current
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Ic(res, p.muted) }
}

@Suppress("unused")
private val keepWidthIn = Modifier.widthIn(min = 0.dp)

/**
 * «Ритм месяца»: по тонкой полоске на каждый день, высота показывает расход за день.
 * Сегодняшний день (или самый дорогой, если месяц прошёл) подсвечен шампанью.
 */
@Composable
fun MonthRhythm(daily: List<Long>, highlight: Int, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    val grow = remember { Animatable(0f) }
    LaunchedEffect(daily) { grow.animateTo(1f, tween(900, easing = EaseOutExpo)) }
    val peak = (daily.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Canvas(modifier.fillMaxWidth().height(40.dp)) {
        val n = daily.size
        if (n == 0) return@Canvas
        val gap = 3.dp.toPx()
        val bw = (size.width - gap * (n - 1)) / n
        for (i in 0 until n) {
            val v = daily[i]
            val h = if (v <= 0L) 3.dp.toPx() else (size.height * (0.14f + 0.86f * v / peak)) * grow.value.coerceAtLeast(0.05f)
            val col = when {
                i == highlight -> p.gold
                v > 0L -> p.bar
                else -> p.line
            }
            drawRoundRect(
                color = col,
                topLeft = Offset(i * (bw + gap), size.height - h),
                size = androidx.compose.ui.geometry.Size(bw, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(bw / 2, bw / 2)
            )
        }
    }
}
