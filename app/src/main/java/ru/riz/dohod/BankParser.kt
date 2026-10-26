package ru.riz.dohod

import kotlin.math.roundToLong

/** Результат разбора банковского уведомления. */
data class BankOp(
    val amount: Long,
    val income: Boolean,
    val category: String,
    val title: String,       // что показать в списке: магазин, человек или тип операции
    val person: String = "",
    val balance: Long? = null // остаток по счёту, если банк его прислал
)

/**
 * Разбор текстов уведомлений и SMS Сбера и Т-Банка.
 * Примеры, которые понимает:
 *  «MIR-1234 14:05 Покупка 500р KOFEMANIYA Баланс: 12 345.67р»
 *  «Покупка, карта *1234» + «500 ₽, Пятёрочка. Доступно 12 345,67 ₽»
 *  «Зачисление зарплаты 80 000 ₽»,  «Перевод от Ивана И. 5 000 ₽»,  «Перевод 5000р Марат С.»
 */
object BankParser {

    private val money = Regex(
        """(?<![\d.,])(\d{1,3}(?: \d{3})+|\d+)(?:[.,](\d{1,2}))? ?(₽|руб(?:лей|ля|ль)?\.?|р\.?|rub|rur)(?![а-яёa-z])""",
        RegexOption.IGNORE_CASE
    )

    private val balanceWords = listOf("баланс", "доступно", "остаток", "ост.", "бал.", "доступный", "на счете", "на счёте")

    private val skipWords = listOf(
        "код", "пароль", "никому не сообщ", "не сообщайте", "отказ", "отклонен", "отклонён", "недостаточно",
        "вход в ", "одобрен", " акци", "скидк", "предлагаем", "оформите", "заявк", "подтвердите",
        "не прошла", "операция отменена", "запрос на", "будет списан", "напоминаем", "задолженност",
        "рассрочк", "подписка будет", "выписк"
    )

    private val incomeKeys = listOf(
        "зачислен", "пополнен", "поступлен", "входящий перевод", "перевод от", "вам перевели", "вам отправили",
        "получен перевод", "перевод получен", "получен", "зарплат", "аванс", "возврат", "отмена покупки",
        "кешбэк", "кэшбэк", "кешбек", "кэшбек", "cashback", "начислен", "поступил"
    )

    private val expenseKeys = listOf(
        "покупка", "оплата", "оплачен", "списан", "снятие", "выдача наличных", "исходящий перевод", "вы перевели",
        "перевод", "платёж", "платеж", "автоплат", "комиссия", "отправлен", "оплатили", "выдача"
    )

    private val brands: List<Pair<String, List<String>>> = listOf(
        "Продукты" to listOf(
            "pyaterochka", "5ka", "пятерочка", "perekrestok", "перекресток", "magnit", "магнит", "lenta", "лента",
            "auchan", "ashan", "ашан", "vkusvill", "вкусвилл", "dixy", "diksi", "дикси", "okey", "окей", "spar",
            "globus", "глобус", "verny", "svetofor", "светофор", "monetka", "samokat", "самокат", "lavka", "лавка",
            "produkty", "продукт", "market", "маркет", "gipermarket", "supermarket", "магазин"
        ),
        "Кафе" to listOf(
            "coffee", "kofe", "кофе", "cafe", "kafe", "кафе", "restoran", "restaurant", "ресторан", "kfc", "rostics",
            "mcdonald", "vkusno", "вкусно", "burger", "бургер", "shaurma", "шаурм", "dodo", "додо", "pizza", "пицц",
            "sushi", "суши", "starbucks", "chaihona", "чайхон", "stolovaya", "столов", "eda.yandex", "яндекс еда",
            "delivery club", "bar ", "pub", "khinkal", "хинкал", "teremok", "теремок", "shokoladnitsa", "кофемания"
        ),
        "Транспорт" to listOf(
            "taxi", "такси", "yandex go", "yandex.go", "yandexgo", "uber", "citymobil", "ситимобил", "azs", "азс",
            "lukoil", "лукойл", "rosneft", "роснефть", "gazprom", "газпромнефть", "tatneft", "татнефть", "shell",
            "transport", "транспорт", "parking", "парковк", "avtodor", "автодор", "rzd", "ржд", "aeroflot",
            "аэрофлот", "pobeda", "s7 ", "metro", "метро", "troika", "тройка", "benzin", "бензин"
        ),
        "Здоровье" to listOf("apteka", "аптек", "pharm", "zdravcity", "клиник", "clinic", "medic", "стомат", "doctor"),
        "Дом и связь" to listOf(
            "mts", "мтс", "beeline", "билайн", "megafon", "мегафон", "tele2", "теле2", "rostelecom", "ростелеком",
            "жкх", "жку", "gku", "энерго", "energo", "водоканал", "газ ", "internet", "интернет", "domru", "дом.ру",
            "kvartplata", "квартплат", "коммунал"
        ),
        "Одежда" to listOf("lamoda", "ламода", "sportmaster", "спортмастер", "gloria", "ostin", "остин", "befree", "zara"),
        "Развлечения" to listOf(
            "kino", "кино", "cinema", "okko", "ivi", "kinopoisk", "кинопоиск", "yandex plus", "яндекс плюс",
            "spotify", "steam", "playstation", "boulling", "боулинг"
        )
    )

    /** Чистим неразрывные пробелы и лишние пробелы, сохраняя переносы строк как точку. */
    fun normalize(s: String): String =
        s.replace(' ', ' ').replace(' ', ' ').replace(' ', ' ').replace(' ', ' ')
            .replace(Regex("""[\r\n]+"""), ". ")
            .replace(Regex(""" {2,}"""), " ")
            .trim()

    fun parse(raw: String): BankOp? {
        val text = normalize(raw)
        if (text.isBlank()) return null
        val low = text.lowercase().replace('ё', 'е')

        val spaced = " $low"
        if (skipWords.any { spaced.contains(it) }) return null

        // Направление: какое ключевое слово встречается раньше
        val inIdx = incomeKeys.mapNotNull { k -> low.indexOf(k).takeIf { it >= 0 } }.minOrNull()
        val outIdx = expenseKeys.mapNotNull { k -> low.indexOf(k).takeIf { it >= 0 } }.minOrNull()
        val income = when {
            inIdx == null && outIdx == null -> return null
            inIdx == null -> false
            outIdx == null -> true
            else -> inIdx <= outIdx
        }

        // Суммы: первая «не баланс» — операция, первая «баланс» — остаток
        var amount: Long? = null
        var amountEnd = -1
        var amountStart = -1
        var balance: Long? = null
        for (m in money.findAll(text)) {
            val value = toRub(m.groupValues[1], m.groupValues[2])
            val before = low.substring(maxOf(0, m.range.first - 22), m.range.first)
            val isBalance = balanceWords.any { before.contains(it) }
            if (isBalance) {
                if (balance == null) balance = value
            } else if (amount == null && value > 0) {
                amount = value
                amountStart = m.range.first
                amountEnd = m.range.last + 1
            }
        }
        if (amount == null) return null

        // Человек в переводе
        var person = Regex("""(?<![а-яёa-zА-ЯЁA-Z])(?:от|отправитель:?)\s+([А-ЯЁA-Z][а-яёa-z]+(?:\s+[А-ЯЁA-Z][а-яёa-z]*\.?)?)""")
            .find(text)?.groupValues?.get(1)?.trim()?.trimEnd('.')?.let { fromGenitive(it) } ?: ""

        // Магазин/получатель: текст после суммы до «Баланс/Доступно», иначе — до суммы
        var tail = text.substring(amountEnd)
        val cut = balanceWords.mapNotNull { w -> tail.lowercase().indexOf(w).takeIf { it >= 0 } }.minOrNull()
        if (cut != null) tail = tail.substring(0, cut)
        var merchant = cleanMerchant(tail)
        if (merchant.isEmpty()) merchant = cleanMerchant(stripKeywords(text.substring(0, amountStart)))

        val isTransfer = low.contains("перевод") || low.contains("перевели") || low.contains("отправил")
        if (person.isEmpty() && isTransfer && looksLikeName(merchant)) person = merchant.trimEnd('.')

        val category = if (income) incomeCategory(low, person) else expenseCategory(low, merchant, person, isTransfer)
        val title = when {
            person.isNotEmpty() -> person
            merchant.isNotEmpty() -> prettify(merchant)
            else -> defaultTitle(low, income)
        }
        return BankOp(amount, income, category, title, person, balance)
    }

    private fun toRub(intPart: String, frac: String): Long {
        val whole = intPart.replace(" ", "").toLongOrNull() ?: 0L
        val kop = if (frac.isEmpty()) 0.0 else ("0." + frac).toDouble()
        return (whole + kop).roundToLong()
    }

    // Без \b: на Android границы слов с кириллицей работают по-разному, поэтому явные проверки букв
    private val noise = Regex(
        "(?i)(?<![a-zа-яё])(mir|visa|maestro|mastercard|mc|мир|карта|карты|картой|карту|счет|счёт|счета|rub|по сбп|через сбп|сбп)(?![a-zа-яё])[-\\s*]*\\d*" +
            "|\\*\\d{2,}|(?<!\\d)\\d{1,2}:\\d{2}(?!\\d)|(?<!\\d)\\d{2}\\.\\d{2}(?:\\.\\d{2,4})?(?!\\d)|[-*]\\d{4}(?!\\d)"
    )

    private fun stripKeywords(s: String): String {
        var r = s
        (incomeKeys + expenseKeys + listOf("в магазине", "магазин", "операция", "успешно", "на сумму", "сумма"))
            .sortedByDescending { it.length }
            .forEach { k -> r = r.replace(Regex(Regex.escape(k) + "[а-яё]*", RegexOption.IGNORE_CASE), " ") }
        return r
    }

    private fun cleanMerchant(s: String): String {
        var r = noise.replace(s, " ")
        r = r.replace(Regex("""^[\s.,:;·•\-—–]+|[\s.,:;·•\-—–]+$"""), "")
        r = r.replace(Regex("""\s{2,}"""), " ").trim()
        r = r.removePrefix("в ").removePrefix("В ").trim()
        if (r.length > 40) r = r.substring(0, 40).trim()
        return if (r.count { it.isLetter() } >= 2) r else ""
    }

    /** «Ивана И» → «Иван И.», «Марины» → «Марина»; служебные слова после имени отбрасываем. */
    private fun fromGenitive(s: String): String {
        val parts = s.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (parts.isEmpty()) return ""
        val lowSecond = parts.getOrNull(1)?.lowercase() ?: ""
        val keepSecond = parts.size > 1 && (parts[1].length <= 2 ||
            (balanceWords.none { lowSecond.startsWith(it.trimEnd('.')) } &&
                incomeKeys.none { lowSecond.startsWith(it) } && expenseKeys.none { lowSecond.startsWith(it) }))
        val first = parts[0]
        val base = when {
            first.length > 3 && first.endsWith("ии") -> first.dropLast(1) + "я"
            first.length > 3 && (first.endsWith("ы") || first.endsWith("и")) -> first.dropLast(1) + "а"
            first.length > 3 && first.endsWith("а") -> first.dropLast(1)
            first.length > 3 && first.endsWith("я") -> first.dropLast(1) + "й"
            else -> first
        }
        return if (keepSecond) {
            val second = parts[1].let { if (it.length == 1) "$it." else it }
            "$base $second"
        } else base
    }

    private fun looksLikeName(s: String): Boolean =
        Regex("""^[А-ЯЁ][а-яё]+(\s+[А-ЯЁ]\.?)?(\s+[А-ЯЁ][а-яё]+)?\.?$""").matches(s.trim())

    /** «PYATEROCHKA 1234» → «Pyaterochka 1234», русский текст оставляем. */
    private fun prettify(s: String): String =
        if (s.any { it in 'a'..'z' || it in 'а'..'я' }) s
        else s.lowercase().split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

    private fun incomeCategory(low: String, person: String): String = when {
        low.contains("зарплат") || low.contains("аванс") || low.contains("заработн") -> "Зарплата"
        low.contains("кешбэк") || low.contains("кэшбэк") || low.contains("кешбек") || low.contains("кэшбек") ||
            low.contains("cashback") -> "Кешбэк"
        low.contains("возврат") || low.contains("отмена покупки") -> "Возврат"
        person.isNotEmpty() || low.contains("перевод") || low.contains("перевели") -> "Перевод"
        else -> "Доход"
    }

    private fun expenseCategory(low: String, merchant: String, person: String, isTransfer: Boolean): String {
        if (low.contains("снятие") || low.contains("выдача наличных") || low.contains("банкомат") || low.contains("atm")) {
            return "Наличные"
        }
        if (isTransfer && person.isNotEmpty()) return "Люди"
        val m = merchant.lowercase().replace('ё', 'е')
        for ((cat, keys) in brands) {
            if (keys.any { m.contains(it) }) return cat
        }
        for ((cat, keys) in brands) {
            if (keys.any { low.contains(it) }) return cat
        }
        val tokens = Regex("""[a-zа-я]+""").findAll(m).map { it.value }.toList()
        val byWords = Categories.expenseCategory(tokens)
        if (byWords != "Прочее") return byWords
        return if (isTransfer) "Люди" else "Прочее"
    }

    private fun defaultTitle(low: String, income: Boolean): String = when {
        low.contains("зарплат") -> "Зарплата"
        low.contains("кешбэк") || low.contains("кэшбэк") -> "Кешбэк"
        low.contains("снятие") || low.contains("наличн") -> "Снятие наличных"
        low.contains("перевод") -> if (income) "Входящий перевод" else "Перевод"
        low.contains("пополнен") -> "Пополнение"
        low.contains("оплата") -> "Оплата"
        income -> "Зачисление"
        else -> "Покупка"
    }
}
