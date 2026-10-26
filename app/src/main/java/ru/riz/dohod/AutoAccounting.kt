package ru.riz.dohod

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/** Системный сервис: Android передаёт сюда все уведомления, мы берём только банковские. */
class BankListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            AutoAccounting.handle(applicationContext, sbn)
        } catch (_: Throwable) {
            // никогда не роняем сервис из-за странного уведомления
        }
    }
}

object AutoAccounting {

    // Мессенджеры, в которых приходят SMS от банков
    private val smsApps = listOf(
        "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms",
        "com.android.messaging", "com.miui.mms", "com.huawei.message", "com.oneplus.mms",
        "com.coloros.mms", "ru.yandex.messenger", "com.motorola.messaging"
    )

    private val sberSenders = listOf("900", "sberbank", "сбербанк", "сбер", "sber", "сбербанк онлайн")
    private val tbankSenders = listOf("t-bank", "т-банк", "tinkoff", "тинькофф", "tbank", "т банк")

    /** Какой банк прислал уведомление (или null, если не банк / источник выключен). */
    fun detectBank(ctx: Context, pkg: String, title: String): String? {
        val p = pkg.lowercase()
        val t = title.lowercase().trim()
        val bank = when {
            p.contains("sber") -> Source.SBER
            p.contains("tinkoff") || p.contains("tbank") || p.contains("idamob") -> Source.TBANK
            isSmsApp(p) && sberSenders.any { t == it || t.startsWith("$it ") } -> Source.SBER
            isSmsApp(p) && tbankSenders.any { t.contains(it) } -> Source.TBANK
            else -> null
        } ?: return null
        val viaSms = isSmsApp(p)
        if (viaSms && !Prefs.flag(ctx, Prefs.AUTO_SMS, true)) return null
        if (bank == Source.SBER && !Prefs.flag(ctx, Prefs.AUTO_SBER, true)) return null
        if (bank == Source.TBANK && !Prefs.flag(ctx, Prefs.AUTO_TBANK, true)) return null
        return bank
    }

    private fun isSmsApp(p: String) = p in smsApps || p.contains("mms") || p.contains("messag") || p.contains("sms")

    // Защита от дублей: банк часто присылает и push, и SMS об одной операции
    private val recent = ArrayDeque<Triple<String, Long, String>>() // подпись, время, текст

    fun handle(ctx: Context, sbn: StatusBarNotification) {
        if (!Prefs.flag(ctx, Prefs.AUTO_ON, true)) return
        if (sbn.packageName == ctx.packageName) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val ex = n.extras ?: return
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val text = big ?: ex.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val lines = ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n") { it.toString() } ?: ""

        val bank = detectBank(ctx, sbn.packageName, title) ?: return
        val body = if (lines.isNotBlank() && text.isBlank()) lines else text
        val isSms = isSmsApp(sbn.packageName.lowercase())
        val raw = if (isSms) body else "$title\n$body"
        process(ctx, bank, raw)
    }

    /** Разбор и запись. Возвращает созданную запись (или null). Используется и для ручной проверки текста. */
    @Synchronized
    fun process(ctx: Context, bank: String, raw: String, test: Boolean = false): Tx? {
        val op = BankParser.parse(raw) ?: return null
        if (op.balance != null && !test) Prefs.setBankBalance(ctx, bank, op.balance)

        val now = System.currentTimeMillis()
        val sig = "${op.income}|${op.amount}"
        val norm = BankParser.normalize(raw)
        while (recent.isNotEmpty() && now - recent.first().second > 10 * 60_000) recent.removeFirst()
        if (!test) {
            // тот же текст за 10 минут — это обновление уведомления; та же сумма за 3 минуты — push + SMS
            if (recent.any { it.third == norm || (it.first == sig && now - it.second < 3 * 60_000) }) return null
            recent.addLast(Triple(sig, now, norm))
        }

        val tx = Tx.fromBank(op, bank, raw)
        if (test) return tx
        if (Prefs.flag(ctx, Prefs.AUTO_CONFIRM, false)) {
            Repo.addPending(ctx, tx, norm)
        } else {
            Repo.add(ctx, tx)
        }
        Prefs.setLastAuto(ctx)
        Widgets.refreshAll(ctx)
        return tx
    }

    /** Выдан ли приложению доступ к уведомлениям. */
    fun hasAccess(ctx: Context): Boolean {
        val flat = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
        val me = ComponentName(ctx, BankListener::class.java).flattenToString()
        return flat.split(':').any { it.equals(me, ignoreCase = true) || it.startsWith(ctx.packageName + "/") }
    }
}
