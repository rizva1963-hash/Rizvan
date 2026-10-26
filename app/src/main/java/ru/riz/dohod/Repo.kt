package ru.riz.dohod

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random

data class Tx(
    val id: Long,
    val ts: Long,
    val amount: Long,
    val income: Boolean,
    val category: String,
    val text: String,
    val person: String = "",
    val kind: String = Kind.NORMAL,
    val source: String = Source.VOICE
) {
    companion object {
        fun create(p: Parsed, raw: String): Tx {
            val now = System.currentTimeMillis()
            return Tx(
                id = now * 1000 + Random.nextInt(1000),
                ts = now,
                amount = p.amount,
                income = p.isIncome,
                category = p.category,
                text = raw.trim().replaceFirstChar { it.uppercase() },
                person = p.person,
                kind = p.kind,
                source = Source.VOICE
            )
        }

        fun fromBank(op: BankOp, source: String, raw: String): Tx {
            val now = System.currentTimeMillis()
            return Tx(
                id = now * 1000 + Random.nextInt(1000),
                ts = now,
                amount = op.amount,
                income = op.income,
                category = op.category,
                text = op.title,
                person = op.person,
                kind = Kind.NORMAL,
                source = source
            )
        }
    }
}

/** Откуда пришла запись. */
object Source {
    const val VOICE = "voice"
    const val MANUAL = "manual"
    const val SBER = "sber"
    const val TBANK = "tbank"

    fun label(s: String): String = when (s) {
        SBER -> "Сбер"
        TBANK -> "Т-Банк"
        MANUAL -> "Вручную"
        else -> "Голос"
    }
}

/** Хранилище: один JSON-файл во внутренней памяти приложения. */
object Repo {
    private const val FILE = "transactions.json"
    private var cache: MutableList<Tx>? = null

    @Synchronized
    fun all(ctx: Context): List<Tx> = load(ctx).sortedByDescending { it.ts }

    @Synchronized
    fun add(ctx: Context, tx: Tx) {
        load(ctx).add(tx); save(ctx)
    }

    @Synchronized
    fun update(ctx: Context, tx: Tx) {
        val list = load(ctx)
        val i = list.indexOfFirst { it.id == tx.id }
        if (i >= 0) { list[i] = tx; save(ctx) }
    }

    @Synchronized
    fun delete(ctx: Context, id: Long) {
        load(ctx).removeAll { it.id == id }; save(ctx)
    }

    @Synchronized
    fun clear(ctx: Context) {
        load(ctx).clear(); save(ctx)
    }

    private fun toJson(t: Tx): JSONObject = JSONObject()
        .put("id", t.id).put("ts", t.ts).put("amount", t.amount)
        .put("income", t.income).put("category", t.category).put("text", t.text)
        .put("person", t.person).put("kind", t.kind).put("source", t.source)

    private fun fromJson(o: JSONObject) = Tx(
        o.getLong("id"), o.getLong("ts"), o.getLong("amount"),
        o.getBoolean("income"), o.getString("category"), o.optString("text"),
        o.optString("person", ""), o.optString("kind", Kind.NORMAL), o.optString("source", Source.VOICE)
    )

    // ── Очередь автоучёта (когда включено «Подтверждать перед записью») ──
    private const val PENDING = "pending.json"
    private var pendingCache: MutableList<Pair<Tx, String>>? = null

    @Synchronized
    fun pending(ctx: Context): List<Pair<Tx, String>> = loadPending(ctx).sortedByDescending { it.first.ts }

    @Synchronized
    fun addPending(ctx: Context, tx: Tx, raw: String) {
        loadPending(ctx).add(tx to raw); savePending(ctx)
    }

    @Synchronized
    fun removePending(ctx: Context, id: Long) {
        loadPending(ctx).removeAll { it.first.id == id }; savePending(ctx)
    }

    private fun loadPending(ctx: Context): MutableList<Pair<Tx, String>> {
        pendingCache?.let { return it }
        val list = mutableListOf<Pair<Tx, String>>()
        val f = File(ctx.filesDir, PENDING)
        if (f.exists()) {
            try {
                val arr = JSONArray(f.readText())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(fromJson(o) to o.optString("raw"))
                }
            } catch (_: Exception) { }
        }
        pendingCache = list
        return list
    }

    private fun savePending(ctx: Context) {
        val arr = JSONArray()
        pendingCache.orEmpty().forEach { (t, raw) -> arr.put(toJson(t).put("raw", raw)) }
        File(ctx.filesDir, PENDING).writeText(arr.toString())
    }

    @Synchronized
    fun exportJson(ctx: Context): String {
        val arr = JSONArray()
        load(ctx).forEach { arr.put(toJson(it)) }
        return JSONObject().put("app", "dohod").put("version", 2).put("items", arr).toString(2)
    }

    /** Добавляет записи из резервной копии, не дублируя уже существующие. Возвращает число новых. */
    @Synchronized
    fun importJson(ctx: Context, json: String): Int {
        val trimmed = json.trim()
        val arr = if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed).getJSONArray("items")
        val list = load(ctx)
        val ids = list.map { it.id }.toHashSet()
        var added = 0
        for (i in 0 until arr.length()) {
            val t = fromJson(arr.getJSONObject(i))
            if (ids.add(t.id)) { list.add(t); added++ }
        }
        save(ctx)
        return added
    }

    private fun load(ctx: Context): MutableList<Tx> {
        cache?.let { return it }
        val list = mutableListOf<Tx>()
        val f = File(ctx.filesDir, FILE)
        if (f.exists()) {
            try {
                val arr = JSONArray(f.readText())
                for (i in 0 until arr.length()) list.add(fromJson(arr.getJSONObject(i)))
            } catch (_: Exception) {
                f.copyTo(File(ctx.filesDir, "$FILE.broken"), overwrite = true)
            }
        }
        cache = list
        return list
    }

    private fun save(ctx: Context) {
        val arr = JSONArray()
        cache.orEmpty().forEach { arr.put(toJson(it)) }
        val tmp = File(ctx.filesDir, "$FILE.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(File(ctx.filesDir, FILE))
    }
}

/** Настройки приложения и автоучёта. */
object Prefs {
    private fun p(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    fun budget(ctx: Context): Long = p(ctx).getLong("budget", 0L)
    fun setBudget(ctx: Context, v: Long) = p(ctx).edit().putLong("budget", v).apply()

    /** Начальный остаток: баланс = начальный остаток + все доходы − все расходы. */
    fun startBalance(ctx: Context): Long = p(ctx).getLong("start_balance", 0L)
    fun setStartBalance(ctx: Context, v: Long) = p(ctx).edit().putLong("start_balance", v).apply()

    fun flag(ctx: Context, key: String, def: Boolean): Boolean = p(ctx).getBoolean(key, def)
    fun setFlag(ctx: Context, key: String, v: Boolean) = p(ctx).edit().putBoolean(key, v).apply()

    const val AUTO_ON = "auto_on"
    const val AUTO_SBER = "auto_sber"
    const val AUTO_TBANK = "auto_tbank"
    const val AUTO_SMS = "auto_sms"
    const val AUTO_CONFIRM = "auto_confirm"

    fun bankBalance(ctx: Context, source: String): Pair<Long, Long>? {
        val ts = p(ctx).getLong("bal_ts_$source", 0L)
        if (ts == 0L) return null
        return p(ctx).getLong("bal_$source", 0L) to ts
    }

    fun setBankBalance(ctx: Context, source: String, v: Long) =
        p(ctx).edit().putLong("bal_$source", v).putLong("bal_ts_$source", System.currentTimeMillis()).apply()

    fun lastAuto(ctx: Context): Long = p(ctx).getLong("last_auto", 0L)
    fun setLastAuto(ctx: Context) = p(ctx).edit().putLong("last_auto", System.currentTimeMillis()).apply()
}

data class Summary(val income: Long, val expense: Long) {
    val balance get() = income - expense
}

/** Сводка по человеку: сколько дал, получил и кто кому должен. */
data class PersonStat(
    val name: String,
    val given: Long,
    val received: Long,
    val owesMe: Long, // >0 — человек должен мне, <0 — я должен ему
    val last: Long,
    val count: Int
)

object Stats {
    private val ru = Locale("ru")

    fun startOfDay(offsetDays: Int = 0): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, offsetDays)
    }.timeInMillis

    fun startOfWeek(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        val shift = (get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
        add(Calendar.DAY_OF_YEAR, -shift)
    }.timeInMillis

    fun startOfMonth(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun startOfYear(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Сколько дней осталось до конца месяца, включая сегодня. */
    fun daysLeftInMonth(): Int {
        val c = Calendar.getInstance()
        return c.getActualMaximum(Calendar.DAY_OF_MONTH) - c.get(Calendar.DAY_OF_MONTH) + 1
    }

    fun summary(list: List<Tx>, from: Long): Summary {
        val p = list.filter { it.ts >= from }
        return Summary(p.filter { it.income }.sumOf { it.amount }, p.filter { !it.income }.sumOf { it.amount })
    }

    fun people(list: List<Tx>): List<PersonStat> =
        list.filter { it.person.isNotBlank() }
            .groupBy { it.person.trim().lowercase() }
            .map { (_, txs) ->
                val owes = txs.sumOf {
                    when (it.kind) {
                        Kind.LEND, Kind.REPAY_OUT -> it.amount
                        Kind.BORROW, Kind.REPAY_IN -> -it.amount
                        else -> 0L
                    }
                }
                PersonStat(
                    name = txs.maxByOrNull { it.ts }!!.person.trim(),
                    given = txs.filter { !it.income }.sumOf { it.amount },
                    received = txs.filter { it.income }.sumOf { it.amount },
                    owesMe = owes,
                    last = txs.maxOf { it.ts },
                    count = txs.size
                )
            }
            .sortedByDescending { it.last }

    fun balance(ctx: Context, list: List<Tx>): Long =
        Prefs.startBalance(ctx) + list.sumOf { if (it.income) it.amount else -it.amount }

    fun startOfMonthOffset(offset: Int): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.MONTH, offset)
    }.timeInMillis

    fun between(list: List<Tx>, from: Long, to: Long): Summary {
        val p = list.filter { it.ts in from until to }
        return Summary(p.filter { it.income }.sumOf { it.amount }, p.filter { !it.income }.sumOf { it.amount })
    }

    fun monthShort(offset: Int): String =
        SimpleDateFormat("LLL", ru).format(Date(startOfMonthOffset(offset))).trimEnd('.').lowercase()

    fun monthNameOffset(offset: Int): String =
        SimpleDateFormat("LLLL", ru).format(Date(startOfMonthOffset(offset))).replaceFirstChar { it.uppercase() }

    /** «в октябре», «в сентябре» — предложный падеж месяца. */
    fun monthIn(offset: Int): String {
        val names = listOf("январе", "феврале", "марте", "апреле", "мае", "июне", "июле", "августе",
            "сентябре", "октябре", "ноябре", "декабре")
        val c = Calendar.getInstance().apply { timeInMillis = startOfMonthOffset(offset) }
        return "в " + names[c.get(Calendar.MONTH)]
    }

    fun dayOfMonth(): Int = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)

    fun shortDate(ts: Long): String = when {
        ts >= startOfDay() -> "Сегодня, " + time(ts)
        ts >= startOfDay(-1) -> "Вчера, " + time(ts)
        else -> SimpleDateFormat("d MMM", ru).format(Date(ts)).trimEnd('.')
    }

    fun todayLong(): String = SimpleDateFormat("d MMMM", ru).format(Date())

    fun monthName(): String =
        SimpleDateFormat("LLLL", ru).format(Date()).replaceFirstChar { it.uppercase() }

    fun todayTitle(): String =
        SimpleDateFormat("EEEE, d MMMM", ru).format(Date()).replaceFirstChar { it.uppercase() }

    fun dayLabel(ts: Long): String = when {
        ts >= startOfDay() -> "Сегодня"
        ts >= startOfDay(-1) -> "Вчера"
        else -> SimpleDateFormat("d MMMM, EEEE", ru).format(Date(ts))
    }

    fun time(ts: Long): String = SimpleDateFormat("HH:mm", ru).format(Date(ts))
    fun date(ts: Long): String = SimpleDateFormat("dd.MM.yyyy", ru).format(Date(ts))
}

/** 12500 → «12 500 ₽» */
fun rub(v: Long): String {
    val s = kotlin.math.abs(v).toString().reversed().chunked(3).joinToString(" ").reversed()
    return "$s ₽"
}

fun signedRub(t: Tx): String = (if (t.income) "+" else "−") + rub(t.amount)
