package ru.riz.dohod

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

private val pad = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp)

/* ═════════════════════════════ ГЛАВНАЯ ═════════════════════════════ */

@Composable
fun HomeScreen(
    all: List<Tx>,
    pendingCount: Int,
    onVoice: () -> Unit,
    onManual: (Boolean) -> Unit,
    onEdit: (Tx) -> Unit,
    onSettings: () -> Unit,
    onBalance: () -> Unit,
    onBudget: () -> Unit,
    onPerson: (String) -> Unit,
    onHistory: () -> Unit,
    onAuto: () -> Unit
) {
    val ctx = LocalContext.current
    val p = LocalPal.current
    var tab by remember { mutableStateOf(0) }
    var monthOffset by remember { mutableStateOf(0) }
    var monthOpen by remember { mutableStateOf(false) }

    val from = Stats.startOfMonthOffset(monthOffset)
    val to = Stats.startOfMonthOffset(monthOffset + 1)
    val month = Stats.between(all, from, to)
    val inMonth = all.filter { it.ts in from until to }
    val balance = Stats.balance(ctx, all)
    val net = month.balance

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .rise(0)
                        .heightIn(min = 44.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        Modifier
                            .weight(1f)
                            .clickable(onClick = onBalance)
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Основной счёт", color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.width(8.dp))
                        Ic(R.drawable.ic_chevron_down, p.muted, 16.dp)
                    }
                    IconBox(R.drawable.ic_sliders, onSettings)
                }
            }

            // Баланс
            item {
                CardBox(Modifier.rise(70)) {
                    Text("Баланс на " + Stats.todayLong(), color = p.muted, fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    BigAmount(balance, 46)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Delta(net >= 0, if (net >= 0) p.green else p.red)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            signed(net).removePrefix("+"), color = if (net >= 0) p.green else p.red, fontSize = 14.sp,
                            fontWeight = FontWeight.Medium, style = TextStyle(fontFeatureSettings = TNUM)
                        )
                        Text(" " + Stats.monthIn(monthOffset).replace("в ", "за ").let { monthFor(monthOffset) }, color = p.muted, fontSize = 14.sp)
                    }
                    if (month.expense > 0 || month.income > 0) {
                        Spacer(Modifier.height(14.dp))
                        val share = if (month.income > 0) month.expense.toFloat() / month.income else 1f
                        Row {
                            Text("Расход " + rub(month.expense), color = p.muted, fontSize = 13.sp, modifier = Modifier.weight(1f), style = TextStyle(fontFeatureSettings = TNUM))
                            Text(
                                if (month.income > 0) "${(share * 100).toInt()}% от дохода" else "доходов нет",
                                color = p.muted, fontSize = 13.sp
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        ProgressLine(share)
                    }
                }
            }

            // Кнопки
            item {
                Column(Modifier.rise(140), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GoldButton("Сказать вслух", Modifier.fillMaxWidth(), icon = R.drawable.ic_mic, onClick = onVoice)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GhostButton("+ Доход", Modifier.weight(1f)) { onManual(true) }
                        GhostButton("− Расход", Modifier.weight(1f)) { onManual(false) }
                    }
                }
            }

            if (pendingCount > 0) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(p.fill)
                            .clickable(onClick = onAuto)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Dot(p.gold)
                        Spacer(Modifier.width(10.dp))
                        Text("Банк прислал операций: $pendingCount. Проверьте", color = p.text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Ic(R.drawable.ic_chevron_right, p.muted, 16.dp)
                    }
                }
            }

            // Вкладки
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .drawBehind { drawRect(p.sep, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 1.dp.toPx()), size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx())) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.weight(1f)) { UnderlineTabs(listOf("Операции", "Счета", "Цели"), tab, { tab = it }) }
                    Row(
                        Modifier
                            .clickable { monthOpen = true }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(Stats.monthNameOffset(monthOffset), color = p.muted, fontSize = 14.sp)
                        Spacer(Modifier.width(4.dp))
                        Ic(R.drawable.ic_chevron_down, p.muted, 14.dp)
                    }
                }
            }

            when (tab) {
                0 -> {
                    if (inMonth.isEmpty()) {
                        item {
                            EmptyNote("Записей пока нет.\nНажмите «Сказать вслух» и скажите, например, «кофе 500». Или включите автоучёт: операции Сбера и Т-Банка будут записываться сами.")
                        }
                    } else {
                        item {
                            Column(Modifier.padding(top = 0.dp)) {
                                inMonth.take(40).forEachIndexed { i, t -> TxRow(t, showSep = i > 0) { onEdit(t) } }
                                if (inMonth.size > 40) {
                                    Text(
                                        "Вся история ›", color = p.gold, fontSize = 15.sp,
                                        modifier = Modifier
                                            .clickable(onClick = onHistory)
                                            .padding(vertical = 14.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                1 -> item { AccountsTab(all, from, to, onPerson) }
                else -> item { GoalsTab(month, monthOffset, onBudget) }
            }
        }

        MonthSheet(monthOpen, monthOffset, onPick = { monthOffset = it; monthOpen = false }, onDismiss = { monthOpen = false })
    }
}

private fun monthFor(offset: Int): String = "за " + Stats.monthNameOffset(offset).lowercase().let {
    // «за октябрь»
    it
}

@Composable
private fun AccountsTab(all: List<Tx>, from: Long, to: Long, onPerson: (String) -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPal.current
    val sources = listOf(Source.SBER to "Сбербанк", Source.TBANK to "Т-Банк", Source.VOICE to "Голос и вручную")
    Column {
        sources.forEachIndexed { i, (src, name) ->
            val txs = all.filter { (if (src == Source.VOICE) (it.source == Source.VOICE || it.source == Source.MANUAL) else it.source == src) && it.ts in from until to }
            val s = Stats.summary(txs, 0)
            val bal = if (src == Source.VOICE) null else Prefs.bankBalance(ctx, src)
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (i > 0) Modifier.drawBehind { drawRect(p.sep, size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx())) } else Modifier)
                    .heightIn(min = 64.dp)
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(name, color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text(
                        if (bal != null) "Остаток по банку " + rub(bal.first) else "операций: ${txs.size}",
                        color = p.muted, fontSize = 13.sp, style = TextStyle(fontFeatureSettings = TNUM)
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(signed(s.balance), color = if (s.balance >= 0) p.green else p.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, style = TextStyle(fontFeatureSettings = TNUM))
                    Text("за месяц", color = p.muted, fontSize = 13.sp)
                }
            }
        }

        val people = Stats.people(all)
        if (people.isNotEmpty()) {
            val owed = people.filter { it.owesMe > 0 }.sumOf { it.owesMe }
            val owe = people.filter { it.owesMe < 0 }.sumOf { -it.owesMe }
            Spacer(Modifier.height(18.dp))
            CardBox(padding = 16.dp) {
                Row {
                    Column(Modifier.weight(1f)) {
                        Text("Вам должны", color = p.muted, fontSize = 13.sp)
                        Text(rub(owed), style = serif(22.sp, p.text))
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Вы должны", color = p.muted, fontSize = 13.sp)
                        Text(rub(owe), style = serif(22.sp, if (owe > 0) p.red else p.text))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            people.forEachIndexed { i, s ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (i > 0) Modifier.drawBehind { drawRect(p.sep, size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx())) } else Modifier)
                        .heightIn(min = 56.dp)
                        .clickable { onPerson(s.name) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(s.name, color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        Text(
                            when {
                                s.owesMe > 0 -> "должен вам"
                                s.owesMe < 0 -> "вы должны"
                                else -> "отдали ${rub(s.given)} · получили ${rub(s.received)}"
                            }, color = p.muted, fontSize = 13.sp, maxLines = 1
                        )
                    }
                    if (s.owesMe != 0L) {
                        Text(rub(kotlin.math.abs(s.owesMe)), color = if (s.owesMe > 0) p.green else p.red, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(6.dp))
                    Ic(R.drawable.ic_chevron_right, p.muted, 14.dp)
                }
            }
        }
    }
}

@Composable
private fun GoalsTab(month: Summary, monthOffset: Int, onBudget: () -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPal.current
    val budget = Prefs.budget(ctx)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CardBox {
            Text("Свободно " + monthFor(monthOffset).replace("за ", "в ").let { Stats.monthIn(monthOffset) }, color = p.muted, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            BigAmount(month.balance, 34)
            Spacer(Modifier.height(12.dp))
            KvRow("Доход", rub(month.income), showSep = false)
            KvRow("Расход", rub(month.expense))
        }
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Бюджет на месяц", color = p.muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(if (budget > 0) "Изменить" else "Задать", color = p.gold, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable(onClick = onBudget).padding(6.dp))
            }
            if (budget > 0) {
                val left = budget - month.expense
                Spacer(Modifier.height(4.dp))
                Text(rub(month.expense) + " из " + rub(budget), style = serif(26.sp, p.text))
                Spacer(Modifier.height(10.dp))
                ProgressLine(month.expense.toFloat() / budget, color = if (left < 0) p.red else p.gold)
                Spacer(Modifier.height(10.dp))
                val perDay = if (left > 0 && monthOffset == 0) left / daysLeft() else 0L
                Text(
                    when {
                        left < 0 -> "Бюджет превышен на " + rub(-left)
                        monthOffset == 0 -> "Осталось ${rub(left)}, можно тратить ≈ ${rub(perDay)} в день"
                        else -> "Осталось " + rub(left)
                    },
                    color = if (left < 0) p.red else p.text, fontSize = 14.sp
                )
            } else {
                Spacer(Modifier.height(6.dp))
                Text("Задайте сумму, и приложение покажет, сколько можно тратить в день до конца месяца.", color = p.muted, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
    }
}

private fun daysLeft(): Int {
    val c = Calendar.getInstance()
    return c.getActualMaximum(Calendar.DAY_OF_MONTH) - c.get(Calendar.DAY_OF_MONTH) + 1
}

/* ═════════════════════════════ ОБЗОР ═════════════════════════════ */

@Composable
fun OverviewScreen(all: List<Tx>, onCategory: (String) -> Unit) {
    val p = LocalPal.current
    var period by remember { mutableStateOf(1) } // 0 неделя, 1 месяц, 2 год

    val now = System.currentTimeMillis()
    // начала периодов: текущий и 5 предыдущих
    val starts: List<Long> = (5 downTo 0).map { back -> periodStart(period, -back) }
    val ends: List<Long> = starts.drop(1) + now
    val values = starts.indices.map { i -> Stats.between(all, starts[i], ends[i]).expense }
    val labels = starts.indices.map { i -> periodLabel(period, starts[i], ends[i]) }

    val curFrom = starts.last()
    val cur = Stats.between(all, curFrom, now + 1)
    // сравнение с тем же отрезком прошлого периода
    val elapsed = now - curFrom
    val prevFrom = starts[4]
    val prev = Stats.between(all, prevFrom, prevFrom + elapsed)
    val diff = if (prev.expense > 0) ((cur.expense - prev.expense) * 100 / prev.expense) else null

    val cats = all.filter { !it.income && it.ts >= curFrom }
        .groupBy { it.category }
        .map { (k, v) -> k to v.sumOf { it.amount } }
        .sortedByDescending { it.second }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { ScreenTitle("Обзор") }
                UnderlineTabs(listOf("Неделя", "Месяц", "Год"), period, { period = it }, gap = 18.dp)
            }
        }
        item {
            Column(Modifier.rise(0), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    when (period) {
                        0 -> "Расход за эту неделю"
                        1 -> "Расход " + Stats.monthIn(0) + ", на " + Stats.dayOfMonth() + " число"
                        else -> "Расход в " + Calendar.getInstance().get(Calendar.YEAR) + " году"
                    },
                    color = p.muted, fontSize = 14.sp
                )
                BigAmount(cur.expense, 40)
                if (diff != null) {
                    val less = diff <= 0
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Delta(up = !less, color = if (less) p.green else p.red)
                        Spacer(Modifier.width(6.dp))
                        Text("${kotlin.math.abs(diff)}%", color = if (less) p.green else p.red, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(
                            (if (less) " меньше, чем " else " больше, чем ") + when (period) {
                                0 -> "на прошлой неделе"
                                1 -> Stats.monthIn(-1)
                                else -> "в прошлом году"
                            },
                            color = p.muted, fontSize = 14.sp
                        )
                    }
                }
            }
        }
        item {
            CardBox(Modifier.rise(80), padding = 14.dp) {
                BarChart(values, labels, current = values.size - 1)
            }
        }
        item {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Доход", color = p.muted, fontSize = 13.sp)
                    Text(rub(cur.income), color = p.text, fontSize = 17.sp, fontWeight = FontWeight.Medium, style = TextStyle(fontFeatureSettings = TNUM))
                }
                Column(Modifier.weight(1f)) {
                    Text("Свободно", color = p.muted, fontSize = 13.sp)
                    Text(signed(cur.balance), color = if (cur.balance >= 0) p.green else p.red, fontSize = 17.sp, fontWeight = FontWeight.Medium, style = TextStyle(fontFeatureSettings = TNUM))
                }
            }
        }
        item {
            Column {
                Row(Modifier.padding(bottom = 4.dp)) {
                    Text("Категория", color = p.muted, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text("Сумма и доля", color = p.muted, fontSize = 14.sp)
                }
                if (cats.isEmpty()) EmptyNote("Расходов за этот период нет")
                cats.forEach { (name, v) ->
                    val share = if (cur.expense > 0) v * 1000 / cur.expense else 0
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .drawBehind { drawRect(p.sep, size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx())) }
                            .heightIn(min = 48.dp)
                            .clickable { onCategory(name) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(name, color = p.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Text(rub(v), color = p.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, style = TextStyle(fontFeatureSettings = TNUM))
                        Text(
                            "${share / 10},${share % 10}%", color = p.muted, fontSize = 14.sp,
                            modifier = Modifier.width(58.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End
                        )
                    }
                }
            }
        }
    }
}

private fun periodStart(period: Int, offset: Int): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    when (period) {
        0 -> {
            val shift = (get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
            add(Calendar.DAY_OF_YEAR, -shift)
            add(Calendar.WEEK_OF_YEAR, offset)
        }
        1 -> { set(Calendar.DAY_OF_MONTH, 1); add(Calendar.MONTH, offset) }
        else -> { set(Calendar.DAY_OF_YEAR, 1); add(Calendar.YEAR, offset) }
    }
}.timeInMillis

private fun periodLabel(period: Int, from: Long, to: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = from }
    return when (period) {
        0 -> {
            val d1 = c.get(Calendar.DAY_OF_MONTH)
            val e = Calendar.getInstance().apply { timeInMillis = to - 1 }
            "$d1–${e.get(Calendar.DAY_OF_MONTH)}"
        }
        1 -> listOf("янв", "фев", "мар", "апр", "май", "июн", "июл", "авг", "сен", "окт", "ноя", "дек")[c.get(Calendar.MONTH)]
        else -> c.get(Calendar.YEAR).toString()
    }
}

/* ═════════════════════════════ ИСТОРИЯ ═════════════════════════════ */

@Composable
fun HistoryScreen(all: List<Tx>, initialQuery: String, onEdit: (Tx) -> Unit) {
    val p = LocalPal.current
    var query by remember(initialQuery) { mutableStateOf(initialQuery) }
    var filter by remember { mutableStateOf(0) }
    val q = query.trim().lowercase()
    val shown = all.filter { t ->
        (filter == 0 || (filter == 1 && !t.income) || (filter == 2 && t.income)) &&
            (q.isEmpty() || t.text.lowercase().contains(q) || t.category.lowercase().contains(q) ||
                t.person.lowercase().contains(q) || t.amount.toString().contains(q) ||
                Source.label(t.source).lowercase().contains(q))
    }
    val groups = shown.groupBy { Stats.dayLabel(it.ts) }.toList()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Box(Modifier.heightIn(min = 44.dp), contentAlignment = Alignment.CenterStart) { ScreenTitle("История") } }
        item { Field(query, { query = it }, "Поиск: кафе, Сбер, Марат, 500", Modifier.fillMaxWidth(), leading = R.drawable.ic_search) }
        item { UnderlineTabs(listOf("Все", "Расходы", "Доходы"), filter, { filter = it }) }
        if (q.isNotEmpty() && shown.isNotEmpty()) {
            item {
                val s = Stats.summary(shown, 0)
                Text("Найдено ${shown.size}: расход ${rub(s.expense)}, доход ${rub(s.income)}", color = p.muted, fontSize = 13.sp)
            }
        }
        if (shown.isEmpty()) item { EmptyNote(if (all.isEmpty()) "Записей пока нет" else "Ничего не найдено") }
        groups.forEach { (day, txs) ->
            item(key = "h_$day") {
                Column(Modifier.padding(top = 10.dp)) {
                    val s = Stats.summary(txs, 0)
                    Row {
                        Text(day, color = p.muted, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        if (s.expense > 0) Text("−" + rub(s.expense), color = p.muted, fontSize = 13.sp, style = TextStyle(fontFeatureSettings = TNUM))
                    }
                    txs.forEachIndexed { i, t -> TxRow(t, showSep = i > 0) { onEdit(t) } }
                }
            }
        }
    }
}

/* ═════════════════════════════ АВТОУЧЁТ ═════════════════════════════ */

@Composable
fun AutoScreen(all: List<Tx>, version: Int, onChanged: () -> Unit, onEdit: (Tx) -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPal.current
    val access = remember(version) { AutoAccounting.hasAccess(ctx) }
    val pending = remember(version) { Repo.pending(ctx) }
    var flags by remember { mutableStateOf(0) } // для перерисовки после переключений
    fun flag(k: String, d: Boolean) = Prefs.flag(ctx, k, d).also { flags.hashCode() }
    fun set(k: String, v: Boolean) { Prefs.setFlag(ctx, k, v); flags++ }

    val monthFrom = Stats.startOfMonth()
    val autoTx = all.filter { it.source == Source.SBER || it.source == Source.TBANK }
    val autoMonth = autoTx.filter { it.ts >= monthFrom }
    var sample by remember { mutableStateOf("") }
    var sampleResult by remember { mutableStateOf<Tx?>(null) }
    var sampleTried by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = pad, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Box(Modifier.heightIn(min = 44.dp), contentAlignment = Alignment.CenterStart) { ScreenTitle("Автоучёт") } }

        // Статус
        item {
            CardBox(Modifier.rise(0)) {
                if (!access) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(p.muted)
                        Spacer(Modifier.width(10.dp))
                        Text("Выключен", color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Разрешите приложению читать уведомления. Тогда покупки, переводы и зачисления из Сбербанка и Т-Банка будут записываться сами, без голоса и ручного ввода.",
                        color = p.muted, fontSize = 14.sp, lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    GoldButton("Включить доступ к уведомлениям", Modifier.fillMaxWidth()) {
                        try {
                            ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (_: Exception) {
                            ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                    if (Build.VERSION.SDK_INT >= 33) {
                        Spacer(Modifier.height(10.dp))
                        GhostButton("Переключатель серый? Нажмите сюда", Modifier.fillMaxWidth()) {
                            Toast.makeText(ctx, "Нажмите ⋮ вверху справа → «Разрешить ограниченные настройки», затем вернитесь сюда", Toast.LENGTH_LONG).show()
                            ctx.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName))
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Android запрещает этот доступ приложениям, установленным из файла, пока вы не разрешите «ограниченные настройки» в сведениях о приложении.",
                            color = p.muted, fontSize = 12.sp, lineHeight = 17.sp
                        )
                    }
                } else {
                    val on = flag(Prefs.AUTO_ON, true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Dot(if (on) p.green else p.muted)
                        Spacer(Modifier.width(10.dp))
                        Text(if (on) "Работает" else "На паузе", color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Записано из банков " + Stats.monthIn(0), color = p.muted, fontSize = 14.sp)
                    Text(animatedAmount(autoMonth.size.toLong()).toString(), style = serif(34.sp, p.text))
                    Text(
                        "операций: расход ${rub(autoMonth.filter { !it.income }.sumOf { it.amount })}, доход ${rub(autoMonth.filter { it.income }.sumOf { it.amount })}",
                        color = p.muted, fontSize = 13.sp, lineHeight = 18.sp
                    )
                    val last = Prefs.lastAuto(ctx)
                    if (last > 0) Text("Последняя: " + Stats.shortDate(last).lowercase(), color = p.muted, fontSize = 13.sp)
                }
            }
        }

        // Ожидают подтверждения
        if (pending.isNotEmpty()) {
            item {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Ждут подтверждения: ${pending.size}", color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text("Принять все", color = p.gold, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.clickable {
                            pending.forEach { (t, _) -> Repo.add(ctx, t); Repo.removePending(ctx, t.id) }
                            onChanged()
                        }.padding(8.dp))
                    }
                    pending.forEachIndexed { i, (t, _) ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .then(if (i > 0) Modifier.drawBehind { drawRect(p.sep, size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx())) } else Modifier)
                                .heightIn(min = 64.dp)
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(t.text, color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                Text(t.category + " · " + Source.label(t.source) + " · " + Stats.shortDate(t.ts), color = p.muted, fontSize = 13.sp, maxLines = 1)
                            }
                            Text(signedRub(t), color = if (t.income) p.green else p.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.width(8.dp))
                            Box(
                                Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(p.gold)
                                    .clickable { Repo.add(ctx, t); Repo.removePending(ctx, t.id); onChanged() },
                                contentAlignment = Alignment.Center
                            ) { Ic(R.drawable.ic_check, p.onGold, 20.dp) }
                            Spacer(Modifier.width(6.dp))
                            Box(
                                Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(p.fill)
                                    .clickable { Repo.removePending(ctx, t.id); onChanged() },
                                contentAlignment = Alignment.Center
                            ) { Ic(R.drawable.ic_close, p.muted, 18.dp) }
                        }
                    }
                }
            }
        }

        // Настройки источников
        item {
            Column {
                SectionCaption("Источники", Modifier.padding(bottom = 2.dp))
                ToggleRow("Автоучёт включён", null, flag(Prefs.AUTO_ON, true), showSep = false) { set(Prefs.AUTO_ON, it) }
                ToggleRow("Сбербанк", "Приложение СберБанк Онлайн и SMS с номера 900", flag(Prefs.AUTO_SBER, true)) { set(Prefs.AUTO_SBER, it) }
                ToggleRow("Т-Банк", "Приложение Т-Банк (Тинькофф) и его SMS", flag(Prefs.AUTO_TBANK, true)) { set(Prefs.AUTO_TBANK, it) }
                ToggleRow("Читать SMS от банков", "Если уведомления в приложении банка выключены", flag(Prefs.AUTO_SMS, true)) { set(Prefs.AUTO_SMS, it) }
                ToggleRow("Подтверждать перед записью", "Операции сначала попадут в список «Ждут подтверждения»", flag(Prefs.AUTO_CONFIRM, false)) { set(Prefs.AUTO_CONFIRM, it) }
            }
        }

        // Остатки по банкам
        val bals = listOf(Source.SBER, Source.TBANK).mapNotNull { s -> Prefs.bankBalance(ctx, s)?.let { s to it } }
        if (bals.isNotEmpty()) {
            item {
                CardBox(padding = 16.dp) {
                    Text("Остаток по последнему уведомлению", color = p.muted, fontSize = 13.sp)
                    bals.forEachIndexed { i, (s, b) ->
                        KvRow(Source.label(s) + " · " + Stats.shortDate(b.second).lowercase(), rub(b.first), showSep = i > 0)
                    }
                }
            }
        }

        // Проверка на примере
        item {
            CardBox(padding = 16.dp) {
                Text("Проверить на примере", color = p.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text("Вставьте текст уведомления или SMS банка, и приложение покажет, как его поймёт.", color = p.muted, fontSize = 13.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(10.dp))
                Field(sample, { sample = it; sampleTried = false }, "Покупка 500р KOFEMANIYA Баланс: 12 345р", Modifier.fillMaxWidth(), singleLine = false)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GhostButton("Разобрать", Modifier.weight(1f)) {
                        val bank = if (sample.lowercase().let { it.contains("тинькофф") || it.contains("т-банк") || it.contains("доступно") }) Source.TBANK else Source.SBER
                        sampleResult = AutoAccounting.process(ctx, bank, sample, test = true)
                        sampleTried = true
                    }
                    val r = sampleResult
                    if (r != null && sampleTried) {
                        GhostButton("Записать", Modifier.weight(1f), color = p.gold) {
                            Repo.add(ctx, r.copy(ts = System.currentTimeMillis()))
                            sample = ""; sampleResult = null; sampleTried = false
                            onChanged()
                            Toast.makeText(ctx, "Записано", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                if (sampleTried) {
                    Spacer(Modifier.height(10.dp))
                    val r = sampleResult
                    if (r == null) {
                        Text("Не похоже на операцию: нет суммы или это код/реклама.", color = p.red, fontSize = 14.sp)
                    } else {
                        KvRow("Тип", if (r.income) "Доход" else "Расход", showSep = false)
                        KvRow("Сумма", signedRub(r))
                        KvRow("Категория", r.category)
                        KvRow("Название", r.text)
                        if (r.person.isNotBlank()) KvRow("Человек", r.person)
                    }
                }
            }
        }

        // Последние из банков
        if (autoTx.isNotEmpty()) {
            item {
                Column {
                    SectionCaption("Последние из банков")
                    autoTx.take(15).forEachIndexed { i, t -> TxRow(t, showSep = i > 0) { onEdit(t) } }
                }
            }
        }

        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(p.fill)
                    .padding(14.dp)
            ) {
                Text(
                    "Уведомления разбираются только на этом телефоне и никуда не отправляются. Коды подтверждения, пароли и реклама банка игнорируются. На Xiaomi, Huawei и Honor разрешите приложению автозапуск, чтобы оно не засыпало.",
                    color = p.muted, fontSize = 13.sp, lineHeight = 18.sp
                )
            }
        }
    }
}
