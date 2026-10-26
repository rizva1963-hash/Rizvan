package ru.riz.dohod

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import android.widget.RemoteViews
import java.util.Calendar

/* ─────────── 13 виджетов. Каждый класс — отдельный пункт в списке виджетов лаунчера. ─────────── */

abstract class BaseWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) = Widgets.refreshAll(ctx)
}

class VoiceSmallWidget : BaseWidget()     // 1×1 Голос
class TodayWidget : BaseWidget()          // 1×1 Сегодня
class BalanceSmallWidget : BaseWidget()   // 2×1 Баланс
class RecordWidget : BaseWidget()         // 2×1 Кнопка записи
class MonthWidget : BaseWidget()          // 2×1 Расход за месяц
class FreeWidget : BaseWidget()           // 2×2 Остаток месяца
class RecentWidget : BaseWidget()         // 2×2 Последние записи
class MoneyWidget : BaseWidget()          // 4×1 Полоса: баланс, сегодня, голос
class QuickWidget : BaseWidget()          // 4×1 Быстрый ввод
class BalanceWidget : BaseWidget()        // 4×2 Баланс, голос и две последние записи
class WeeksWidget : BaseWidget()          // 4×2 Расход по неделям
class CategoriesWidget : BaseWidget()     // 4×2 Три крупнейшие категории
class ChartWidget : BaseWidget()          // 4×3 Обзор: остаток, 6 месяцев, голос

object Widgets {
    private const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    private const val TEXT = 0xFFF1EEE7.toInt()
    private const val MUTED = 0xFF8E959F.toInt()
    private const val GOLD = 0xFFD9B96F.toInt()
    private const val BAR = 0xFF3A414C.toInt()
    private const val GREEN = 0xFF67CBA0.toInt()
    private const val RED = 0xFFE59A8C.toInt()

    /** Всё, что нужно виджетам, считаем один раз. */
    private class Data(ctx: Context) {
        val all = Repo.all(ctx)
        val now = System.currentTimeMillis()
        val month = Stats.summary(all, Stats.startOfMonth())
        val today = Stats.summary(all, Stats.startOfDay())
        val balance = Stats.balance(ctx, all)
        val monthIn = Stats.monthIn(0)                       // «в октябре»
        val monthName = Stats.monthName().lowercase()        // «октябрь»
        val recent = all.take(4)
        val cats = all.filter { !it.income && it.ts >= Stats.startOfMonth() }
            .groupBy { it.category }.map { (k, v) -> k to v.sumOf { it.amount } }.sortedByDescending { it.second }
    }

    fun refreshAll(ctx: Context) {
        val mgr = AppWidgetManager.getInstance(ctx)
        val d by lazy { Data(ctx) }
        fun upd(cls: Class<*>, build: (Data) -> RemoteViews) {
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, cls))
            if (ids.isNotEmpty()) {
                val v = try { build(d) } catch (e: Exception) { null } ?: return
                ids.forEach { mgr.updateAppWidget(it, v) }
            }
        }
        upd(VoiceSmallWidget::class.java) { voice1x1(ctx) }
        upd(TodayWidget::class.java) { today1x1(ctx, it) }
        upd(BalanceSmallWidget::class.java) { balance2x1(ctx, it) }
        upd(RecordWidget::class.java) { record2x1(ctx) }
        upd(MonthWidget::class.java) { month2x1(ctx, it) }
        upd(FreeWidget::class.java) { free2x2(ctx, it) }
        upd(RecentWidget::class.java) { recent2x2(ctx, it) }
        upd(MoneyWidget::class.java) { strip4x1(ctx, it) }
        upd(QuickWidget::class.java) { quick4x1(ctx) }
        upd(BalanceWidget::class.java) { balance4x2(ctx, it) }
        upd(WeeksWidget::class.java) { weeks4x2(ctx, it) }
        upd(CategoriesWidget::class.java) { cats4x2(ctx, it) }
        upd(ChartWidget::class.java) { overview4x3(ctx, it) }
    }

    // ── нажатия
    private fun voice(ctx: Context, preset: String? = null, code: Int = 100): PendingIntent {
        val i = Intent(ctx, VoiceActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (preset != null) i.putExtra("preset_category", preset)
        return PendingIntent.getActivity(ctx, code, i, FLAGS)
    }

    private fun app(ctx: Context, tab: Int = 0): PendingIntent = PendingIntent.getActivity(
        ctx, 200 + tab,
        Intent(ctx, MainActivity::class.java).putExtra("tab", tab)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        FLAGS
    )

    private fun rv(ctx: Context, layout: Int) = RemoteViews(ctx.packageName, layout)
    private fun money(v: Long) = (if (v < 0) "−" else "") + rub(v)
    private fun shortVal(t: Tx) = (if (t.income) "+" else "−") + grp(t.amount)
    private fun progress(v: RemoteViews, id: Int, frac: Float) =
        v.setProgressBar(id, 1000, (frac.coerceIn(0f, 1f) * 1000).toInt(), false)

    // ── 1×1
    private fun voice1x1(ctx: Context) = rv(ctx, R.layout.widget_voice_1x1).apply {
        setOnClickPendingIntent(R.id.w_root, voice(ctx))
    }

    private fun today1x1(ctx: Context, d: Data) = rv(ctx, R.layout.widget_today_1x1).apply {
        setTextViewText(R.id.t_val, "−" + rub(d.today.expense))
        setOnClickPendingIntent(R.id.w_root, app(ctx, 2))
    }

    // ── 2×1
    private fun balance2x1(ctx: Context, d: Data) = rv(ctx, R.layout.widget_balance_2x1).apply {
        setTextViewText(R.id.t_val, money(d.balance))
        setOnClickPendingIntent(R.id.w_root, app(ctx, 0))
    }

    private fun record2x1(ctx: Context) = rv(ctx, R.layout.widget_record_2x1).apply {
        setOnClickPendingIntent(R.id.w_root, voice(ctx))
    }

    private fun month2x1(ctx: Context, d: Data) = rv(ctx, R.layout.widget_month_2x1).apply {
        setTextViewText(R.id.t_label, "Расход, " + d.monthName)
        setTextViewText(R.id.t_val, rub(d.month.expense))
        progress(this, R.id.p_bar, if (d.month.income > 0) d.month.expense.toFloat() / d.month.income else 0f)
        setOnClickPendingIntent(R.id.w_root, app(ctx, 1))
    }

    // ── 2×2
    private fun free2x2(ctx: Context, d: Data) = rv(ctx, R.layout.widget_free_2x2).apply {
        setTextViewText(R.id.t_label, "Свободно " + d.monthIn)
        setTextViewText(R.id.t_val, money(d.month.balance))
        setTextColor(R.id.t_val, if (d.month.balance < 0) RED else TEXT)
        setTextViewText(R.id.t_inc, rub(d.month.income))
        setTextViewText(R.id.t_exp, rub(d.month.expense))
        progress(this, R.id.p_bar, if (d.month.income > 0) d.month.expense.toFloat() / d.month.income else 0f)
        setOnClickPendingIntent(R.id.w_root, app(ctx, 1))
    }

    private fun recent2x2(ctx: Context, d: Data) = rv(ctx, R.layout.widget_recent_2x2).apply {
        val rows = listOf(
            Triple(R.id.r1, R.id.r1_name, R.id.r1_val), Triple(R.id.r2, R.id.r2_name, R.id.r2_val),
            Triple(R.id.r3, R.id.r3_name, R.id.r3_val), Triple(R.id.r4, R.id.r4_name, R.id.r4_val)
        )
        rows.forEachIndexed { i, (row, name, value) ->
            val t = d.recent.getOrNull(i)
            if (t == null) setViewVisibility(row, View.INVISIBLE) else {
                setViewVisibility(row, View.VISIBLE)
                setTextViewText(name, txTitle(t))
                setTextViewText(value, shortVal(t))
                setTextColor(value, if (t.income) GREEN else TEXT)
            }
        }
        setViewVisibility(R.id.t_empty, if (d.recent.isEmpty()) View.VISIBLE else View.GONE)
        setOnClickPendingIntent(R.id.w_root, app(ctx, 2))
    }

    // ── 4×1
    private fun strip4x1(ctx: Context, d: Data) = rv(ctx, R.layout.widget_strip_4x1).apply {
        setTextViewText(R.id.t_bal, money(d.balance))
        setTextViewText(R.id.t_today, "−" + rub(d.today.expense))
        setOnClickPendingIntent(R.id.w_root, app(ctx, 0))
        setOnClickPendingIntent(R.id.b_mic, voice(ctx))
    }

    private fun quick4x1(ctx: Context) = rv(ctx, R.layout.widget_quick_4x1).apply {
        setOnClickPendingIntent(R.id.q1, voice(ctx, "Кафе", 101))
        setOnClickPendingIntent(R.id.q2, voice(ctx, "Продукты", 102))
        setOnClickPendingIntent(R.id.q3, voice(ctx, "Транспорт", 103))
        setOnClickPendingIntent(R.id.q4, voice(ctx, null, 104))
    }

    // ── 4×2
    private fun balance4x2(ctx: Context, d: Data) = rv(ctx, R.layout.widget_balance_4x2).apply {
        setTextViewText(R.id.t_bal, money(d.balance))
        val net = d.month.balance
        setTextViewText(R.id.t_arrow, if (net >= 0) "▲" else "▼")
        setTextColor(R.id.t_arrow, if (net >= 0) GREEN else RED)
        setTextViewText(R.id.t_delta, rub(net))
        setTextColor(R.id.t_delta, if (net >= 0) GREEN else RED)
        setTextViewText(R.id.t_dl, " за " + d.monthName)
        val rows = listOf(
            listOf(R.id.r1, R.id.r1_name, R.id.r1_date, R.id.r1_val),
            listOf(R.id.r2, R.id.r2_name, R.id.r2_date, R.id.r2_val)
        )
        rows.forEachIndexed { i, ids ->
            val t = d.recent.getOrNull(i)
            if (t == null) setViewVisibility(ids[0], View.INVISIBLE) else {
                setViewVisibility(ids[0], View.VISIBLE)
                setTextViewText(ids[1], txTitle(t))
                setTextViewText(ids[2], Stats.shortDate(t.ts).substringBefore(",").lowercase())
                setTextViewText(ids[3], (if (t.income) "+" else "−") + rub(t.amount))
                setTextColor(ids[3], if (t.income) GREEN else TEXT)
            }
        }
        setOnClickPendingIntent(R.id.w_root, app(ctx, 0))
        setOnClickPendingIntent(R.id.b_mic, voice(ctx))
    }

    private fun weeks4x2(ctx: Context, d: Data) = rv(ctx, R.layout.widget_weeks_4x2).apply {
        val c = Calendar.getInstance()
        val today = c.get(Calendar.DAY_OF_MONTH)
        val monthStart = Stats.startOfMonth()
        val dayMs = 24L * 60 * 60 * 1000
        val buckets = (today - 1) / 7 + 1
        val values = mutableListOf<Long>()
        val labels = mutableListOf<String>()
        for (b in 0 until buckets) {
            val from = monthStart + b * 7 * dayMs
            val lastDay = if (b == buckets - 1) today else (b + 1) * 7
            val to = monthStart + lastDay * dayMs
            values += Stats.between(d.all, from, to).expense
            labels += "${b * 7 + 1}–$lastDay"
        }
        setTextViewText(R.id.t_label, "Расход по неделям, " + d.monthName)
        setTextViewText(R.id.t_total, rub(d.month.expense))
        setImageViewBitmap(R.id.i_chart, bars(values, labels, values.size - 1, showValues = true, w = 640, h = 230))
        setOnClickPendingIntent(R.id.w_root, app(ctx, 1))
    }

    private fun cats4x2(ctx: Context, d: Data) = rv(ctx, R.layout.widget_cats_4x2).apply {
        setTextViewText(R.id.t_label, "Категории, " + d.monthName)
        val total = d.month.expense.coerceAtLeast(1)
        val rows = listOf(
            listOf(R.id.c1, R.id.c1_name, R.id.c1_val, R.id.c1_pct, R.id.c1_bar),
            listOf(R.id.c2, R.id.c2_name, R.id.c2_val, R.id.c2_pct, R.id.c2_bar),
            listOf(R.id.c3, R.id.c3_name, R.id.c3_val, R.id.c3_pct, R.id.c3_bar)
        )
        rows.forEachIndexed { i, ids ->
            val c = d.cats.getOrNull(i)
            if (c == null) setViewVisibility(ids[0], View.INVISIBLE) else {
                setViewVisibility(ids[0], View.VISIBLE)
                setTextViewText(ids[1], c.first)
                setTextViewText(ids[2], rub(c.second))
                val share = c.second * 1000 / total
                setTextViewText(ids[3], "${share / 10},${share % 10}%")
                progress(this, ids[4], c.second.toFloat() / total)
            }
        }
        setViewVisibility(R.id.t_empty, if (d.cats.isEmpty()) View.VISIBLE else View.GONE)
        setOnClickPendingIntent(R.id.w_root, app(ctx, 1))
    }

    // ── 4×3
    private fun overview4x3(ctx: Context, d: Data) = rv(ctx, R.layout.widget_overview_4x3).apply {
        setTextViewText(R.id.t_label, "Свободно " + d.monthIn)
        setTextViewText(R.id.t_free, money(d.month.balance))
        setTextColor(R.id.t_free, if (d.month.balance < 0) RED else TEXT)
        setTextViewText(R.id.t_inc, "Доход " + rub(d.month.income))
        setTextViewText(R.id.t_exp, "Расход " + rub(d.month.expense))
        val values = (-5..0).map { off ->
            Stats.between(d.all, Stats.startOfMonthOffset(off), Stats.startOfMonthOffset(off + 1)).expense
        }
        val labels = (-5..0).map { Stats.monthShort(it).take(3) }
        setImageViewBitmap(R.id.i_chart, bars(values, labels, 5, showValues = false, w = 640, h = 190))
        setOnClickPendingIntent(R.id.w_info, app(ctx, 1))
        setOnClickPendingIntent(R.id.i_chart, app(ctx, 1))
        setOnClickPendingIntent(R.id.b_voice, voice(ctx))
    }

    /** Рисуем столбики в картинку: серые, текущий — шампань; подписи снизу, суммы сверху. */
    private fun bars(values: List<Long>, labels: List<String>, current: Int, showValues: Boolean, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val n = values.size.coerceAtLeast(1)
        val labelH = 34f
        val valueH = if (showValues) 34f else 0f
        val gap = if (n <= 4) 28f else 20f
        val bw = (w - gap * (n - 1)) / n
        val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L).toFloat()
        val plotH = h - labelH - valueH - 8f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 24f; textAlign = Paint.Align.CENTER }
        values.forEachIndexed { i, v ->
            val x = i * (bw + gap)
            val bh = if (v > 0) (v / max * plotH).coerceAtLeast(4f) else 3f
            val top = valueH + 4f + (plotH - bh)
            p.color = if (i == current) GOLD else BAR
            c.drawRoundRect(RectF(x, top, x + bw, valueH + 4f + plotH), 6f, 6f, p)
            c.drawRect(RectF(x, valueH + 4f + plotH - 6f, x + bw, valueH + 4f + plotH), p)
            tp.color = if (i == current) TEXT else MUTED
            tp.typeface = if (i == current) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            c.drawText(labels.getOrElse(i) { "" }, x + bw / 2, h - 8f, tp)
            if (showValues && v > 0) c.drawText(grp(v), x + bw / 2, top - 8f, tp)
        }
        return bmp
    }
}
