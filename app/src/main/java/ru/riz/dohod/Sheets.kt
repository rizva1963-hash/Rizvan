package ru.riz.dohod

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

/** Держим последнее значение, чтобы шторка уезжала вместе с содержимым. */
@Composable
fun <T> rememberLast(value: T?): T? {
    val holder = remember { mutableStateOf<T?>(null) }
    if (value != null && holder.value != value) holder.value = value
    return holder.value
}

/* ───────── Редактирование записи ───────── */

@Composable
fun EditSheet(tx: Tx?, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPal.current
    val t = rememberLast(tx)
    Sheet(visible = tx != null, onDismiss = onDismiss) {
        if (t == null) return@Sheet
        var amount by remember(t.id) { mutableStateOf(t.amount.toString()) }
        var income by remember(t.id) { mutableStateOf(t.income) }
        var cat by remember(t.id) { mutableStateOf(t.category) }
        var title by remember(t.id) { mutableStateOf(txTitle(t)) }
        var person by remember(t.id) { mutableStateOf(t.person) }
        var askDelete by remember(t.id) { mutableStateOf(false) }

        fun save() {
            val a = amount.filter { it.isDigit() }.toLongOrNull() ?: 0L
            if (a <= 0) { Toast.makeText(ctx, "Введите сумму", Toast.LENGTH_SHORT).show(); return }
            val kind = if (cat != "Долги") Kind.NORMAL else if (income) {
                if (t.kind == Kind.BORROW || t.kind == Kind.REPAY_IN) t.kind else Kind.REPAY_IN
            } else {
                if (t.kind == Kind.LEND || t.kind == Kind.REPAY_OUT) t.kind else Kind.LEND
            }
            val newText = if (title.isNotBlank() && title != txTitle(t)) title.trim() else t.text
            Repo.update(ctx, t.copy(amount = a, income = income, category = cat, person = person.trim(), kind = kind, text = newText))
            onChanged(); onDismiss()
        }

        SheetHeader("Запись", "Отмена", "Готово", onLeft = onDismiss, onRight = { save() })
        Field(amount, { amount = it.filter { c -> c.isDigit() } }, "Сумма", Modifier.fillMaxWidth(), numeric = true, big = true)
        Spacer(Modifier.height(10.dp))
        UnderlineTabs(listOf("Расход", "Доход"), if (income) 1 else 0, {
            val ni = it == 1
            if (ni != income) {
                income = ni
                val names = if (income) Categories.incomeNames else Categories.expenseNames
                if (cat !in names) cat = if (income) "Доход" else "Прочее"
            }
        })
        Spacer(Modifier.height(12.dp))
        Text("Название", color = p.muted, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Field(title, { title = it }, "Например, Кофе", Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        Text("Категория", color = p.muted, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        ChipGrid(if (income) Categories.incomeNames else Categories.expenseNames, cat) { cat = it }
        Spacer(Modifier.height(14.dp))
        Text("Человек", color = p.muted, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        Field(person, { person = it }, "Необязательно", Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        Text(
            Source.label(t.source) + " · " + Stats.shortDate(t.ts) + (if (t.source == Source.VOICE) " · «${t.text}»" else ""),
            color = p.muted, fontSize = 13.sp
        )
        Spacer(Modifier.height(18.dp))
        if (!askDelete) GhostButton("Удалить запись", Modifier.fillMaxWidth(), color = p.red) { askDelete = true }
        else GhostButton("Точно удалить", Modifier.fillMaxWidth(), color = p.red) {
            Repo.delete(ctx, t.id); onChanged(); onDismiss()
        }
    }
}

/* ───────── Ручной ввод (+ Доход / − Расход) ───────── */

@Composable
fun ManualSheet(income: Boolean?, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPal.current
    val inc = rememberLast(income)
    Sheet(visible = income != null, onDismiss = onDismiss) {
        if (inc == null) return@Sheet
        var amount by remember(income) { mutableStateOf("") }
        var cat by remember(income) { mutableStateOf(if (inc) "Зарплата" else "Продукты") }
        var title by remember(income) { mutableStateOf("") }

        fun save() {
            val a = amount.filter { it.isDigit() }.toLongOrNull() ?: 0L
            if (a <= 0) { Toast.makeText(ctx, "Введите сумму", Toast.LENGTH_SHORT).show(); return }
            val now = System.currentTimeMillis()
            Repo.add(
                ctx, Tx(
                    id = now * 1000 + Random.nextInt(1000), ts = now, amount = a, income = inc, category = cat,
                    text = title.trim().ifBlank { cat }, source = Source.MANUAL
                )
            )
            onChanged(); onDismiss()
            Toast.makeText(ctx, (if (inc) "+" else "−") + rub(a) + " записано", Toast.LENGTH_SHORT).show()
        }

        SheetHeader(if (inc) "Доход" else "Расход", "Отмена", "Записать", onLeft = onDismiss, onRight = { save() })
        Field(amount, { amount = it.filter { c -> c.isDigit() } }, "0 ₽", Modifier.fillMaxWidth(), numeric = true, big = true, onDone = { save() })
        Spacer(Modifier.height(12.dp))
        Field(title, { title = it }, if (inc) "Откуда, например, «Аванс»" else "На что, например, «Кофе»", Modifier.fillMaxWidth())
        Spacer(Modifier.height(14.dp))
        Text("Категория", color = p.muted, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        ChipGrid(if (inc) Categories.incomeNames else Categories.expenseNames, cat) { cat = it }
        Spacer(Modifier.height(18.dp))
        GoldButton("Записать", Modifier.fillMaxWidth()) { save() }
    }
}

/* ───────── Текущий баланс ───────── */

@Composable
fun BalanceSheet(visible: Boolean, all: List<Tx>, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPal.current
    Sheet(visible = visible, onDismiss = onDismiss) {
        var value by remember(visible) { mutableStateOf(Stats.balance(ctx, all).let { if (it > 0) it.toString() else "" }) }
        fun save() {
            val v = value.filter { it.isDigit() }.toLongOrNull() ?: 0L
            val net = all.sumOf { if (it.income) it.amount else -it.amount }
            Prefs.setStartBalance(ctx, v - net)
            onChanged(); onDismiss()
        }
        SheetHeader("Основной счёт", "Отмена", "Готово", onLeft = onDismiss, onRight = { save() })
        Text("Сколько сейчас денег на всех счетах и наличными?", color = p.text, fontSize = 16.sp)
        Spacer(Modifier.height(10.dp))
        Field(value, { value = it.filter { c -> c.isDigit() } }, "Например, 2 486 300", Modifier.fillMaxWidth(), numeric = true, big = true, onDone = { save() })
        Spacer(Modifier.height(8.dp))
        Text("Дальше баланс будет меняться сам с каждой записью.", color = p.muted, fontSize = 13.sp)
        val bals = listOf(Source.SBER, Source.TBANK).mapNotNull { s -> Prefs.bankBalance(ctx, s)?.let { s to it.first } }
        if (bals.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            val sum = bals.sumOf { it.second }
            GhostButton("Взять из банков: " + rub(sum), Modifier.fillMaxWidth()) { value = sum.toString() }
        }
    }
}

/* ───────── Бюджет ───────── */

@Composable
fun BudgetSheet(visible: Boolean, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    val p = LocalPal.current
    Sheet(visible = visible, onDismiss = onDismiss) {
        var value by remember(visible) { mutableStateOf(Prefs.budget(ctx).let { if (it > 0) it.toString() else "" }) }
        fun save() {
            Prefs.setBudget(ctx, value.filter { it.isDigit() }.toLongOrNull() ?: 0L)
            onChanged(); onDismiss()
        }
        SheetHeader("Бюджет на месяц", "Отмена", "Готово", onLeft = onDismiss, onRight = { save() })
        Field(value, { value = it.filter { c -> c.isDigit() } }, "Например, 300 000", Modifier.fillMaxWidth(), numeric = true, big = true, onDone = { save() })
        Spacer(Modifier.height(8.dp))
        Text("Покажу, сколько осталось и сколько можно тратить в день.", color = p.muted, fontSize = 13.sp)
        if (Prefs.budget(ctx) > 0) {
            Spacer(Modifier.height(16.dp))
            GhostButton("Убрать бюджет", Modifier.fillMaxWidth(), color = p.red) { Prefs.setBudget(ctx, 0); onChanged(); onDismiss() }
        }
    }
}

/* ───────── Выбор месяца ───────── */

@Composable
fun MonthSheet(visible: Boolean, current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val p = LocalPal.current
    Sheet(visible = visible, onDismiss = onDismiss) {
        SheetHeader("Месяц", "Закрыть", null, onLeft = onDismiss)
        (0 downTo -11).forEachIndexed { i, off ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(if (i > 0) Modifier.drawBehind { drawRect(p.sep, size = androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx())) } else Modifier)
                    .heightIn(min = 52.dp)
                    .clickable { onPick(off) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    Stats.monthNameOffset(off) + if (off == 0) " (текущий)" else "", fontSize = 16.sp,
                    color = if (off == current) p.gold else p.text,
                    fontWeight = if (off == current) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )
                if (off == current) Ic(R.drawable.ic_check, p.gold, 18.dp)
            }
        }
    }
}

/* ───────── Настройки ───────── */

@Composable
fun SettingsSheet(
    visible: Boolean,
    count: Int,
    onDismiss: () -> Unit,
    onBalance: () -> Unit,
    onBudget: () -> Unit,
    onCsv: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onClear: () -> Unit
) {
    val ctx = LocalContext.current
    val p = LocalPal.current
    Sheet(visible = visible, onDismiss = onDismiss) {
        SheetHeader("Настройки", "Закрыть", null, onLeft = onDismiss)
        KvRow("Текущий баланс", rub(Stats.balance(ctx, Repo.all(ctx))), showSep = false, chevron = true) { onDismiss(); onBalance() }
        KvRow("Бюджет на месяц", Prefs.budget(ctx).let { if (it > 0) rub(it) else "не задан" }, chevron = true) { onDismiss(); onBudget() }
        KvRow("Экспорт в Excel", "CSV", chevron = true) { onCsv() }
        KvRow("Сохранить резервную копию", "", chevron = true) { onBackup() }
        KvRow("Восстановить из копии", "", chevron = true) { onDismiss(); onRestore() }
        KvRow("Удалить все записи", "", valueColor = p.red, chevron = true) { onDismiss(); onClear() }
        Spacer(Modifier.height(16.dp))
        Text("Как говорить", color = p.muted, fontSize = 13.sp)
        Spacer(Modifier.height(6.dp))
        listOf(
            "кофе 500 · такси 350 · бензин 2000",
            "получил зарплату 600 тысяч",
            "даю зарплату Сулейману 50000",
            "дал в долг Марату 5000 · Марат вернул 5000",
            "перевёл маме 30000"
        ).forEach { Text("«$it»", color = p.text, fontSize = 15.sp, modifier = Modifier.padding(vertical = 5.dp)) }
        Spacer(Modifier.height(14.dp))
        Text("Записей: $count. Данные хранятся только на этом телефоне.", color = p.muted, fontSize = 13.sp)
        Text("Доход/Расход · версия " + appVersion(ctx), color = p.muted, fontSize = 13.sp)
    }
}

fun appVersion(ctx: android.content.Context): String = try {
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
} catch (_: Exception) { "" }

/* ───────── Человек ───────── */

@Composable
fun PersonSheet(name: String?, all: List<Tx>, onDismiss: () -> Unit, onEdit: (Tx) -> Unit) {
    val p = LocalPal.current
    val n = rememberLast(name)
    Sheet(visible = name != null, onDismiss = onDismiss) {
        if (n == null) return@Sheet
        val txs = all.filter { it.person.trim().equals(n.trim(), ignoreCase = true) }
        val s = Stats.people(txs).firstOrNull()
        SheetHeader(n, "Закрыть", null, onLeft = onDismiss)
        if (s != null) {
            CardBox(padding = 16.dp) {
                Text(
                    when {
                        s.owesMe > 0 -> "Должен вам"
                        s.owesMe < 0 -> "Вы должны"
                        else -> "Долгов нет"
                    }, color = p.muted, fontSize = 14.sp
                )
                if (s.owesMe != 0L) Text(rub(kotlin.math.abs(s.owesMe)), style = serif(34.sp, if (s.owesMe > 0) p.green else p.red))
                Spacer(Modifier.height(8.dp))
                KvRow("Вы отдали", rub(s.given), showSep = false)
                KvRow("Вы получили", rub(s.received))
            }
            Spacer(Modifier.height(10.dp))
        }
        txs.forEachIndexed { i, t -> TxRow(t, showSep = i > 0) { onEdit(t) } }
        Spacer(Modifier.height(8.dp))
        Text("Чтобы записать возврат, скажите: «$n вернул 5000»", color = p.muted, fontSize = 13.sp)
    }
}

/* ───────── Подтверждение ───────── */

@Composable
fun ConfirmSheet(visible: Boolean, title: String, text: String, action: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val p = LocalPal.current
    Sheet(visible = visible, onDismiss = onDismiss) {
        Text(title, color = p.text, fontSize = 20.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text(text, color = p.muted, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp))
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton(action, Modifier.fillMaxWidth(), color = p.red, onClick = onConfirm)
            GoldButton("Отмена", Modifier.fillMaxWidth(), onClick = onDismiss)
        }
    }
}
