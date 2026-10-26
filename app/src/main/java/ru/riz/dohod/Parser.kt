package ru.riz.dohod

import kotlin.math.roundToLong

data class Parsed(
    val amount: Long,
    val isIncome: Boolean,
    val category: String,
    val person: String = "",
    val kind: String = Kind.NORMAL
)

/** Тип операции: обычная или долговая (для вкладки «Люди»). */
object Kind {
    const val NORMAL = "normal"
    const val LEND = "lend"           // я дал в долг
    const val BORROW = "borrow"       // я занял
    const val REPAY_IN = "repay_in"   // мне вернули
    const val REPAY_OUT = "repay_out" // я вернул долг
}

/**
 * Разбор фразы: «выпил кофе потратил 500» → расход 500, Кафе.
 * Понимает цифры, «2 тысячи», «1.5к», «пятьсот», «полторы тысячи», «косарь».
 */
object Parser {

    private val tokenRe = Regex("""\d+(?:[.,]\d+)?|[a-zа-я]+""")

    private val units = mapOf(
        "ноль" to 0, "один" to 1, "одна" to 1, "одну" to 1, "два" to 2, "две" to 2,
        "три" to 3, "четыре" to 4, "пять" to 5, "шесть" to 6, "семь" to 7, "восемь" to 8,
        "девять" to 9, "десять" to 10, "одиннадцать" to 11, "двенадцать" to 12,
        "тринадцать" to 13, "четырнадцать" to 14, "пятнадцать" to 15, "шестнадцать" to 16,
        "семнадцать" to 17, "восемнадцать" to 18, "девятнадцать" to 19, "двадцать" to 20,
        "тридцать" to 30, "сорок" to 40, "пятьдесят" to 50, "шестьдесят" to 60,
        "семьдесят" to 70, "восемьдесят" to 80, "девяносто" to 90, "сто" to 100,
        "двести" to 200, "триста" to 300, "четыреста" to 400, "пятьсот" to 500,
        "шестьсот" to 600, "семьсот" to 700, "восемьсот" to 800, "девятьсот" to 900
    )
    private val thousand = setOf(
        "тысяча", "тысячи", "тысяч", "тысячу", "тыс", "тыщ", "тыща", "тыщи", "тыщу",
        "к", "k", "косарь", "косаря", "косарей", "кусок", "куска", "кусков"
    )
    // Слова, которые сами по себе означают 1000
    private val bare1000 = setOf("тысяча", "тысячу", "косарь")
    private val million = setOf("миллион", "миллиона", "миллионов", "млн", "лям", "ляма", "лямов")
    private val currency = setOf("рубль", "рубля", "рублей", "руб", "р", "рэ")

    private val incomeStems = listOf(
        "получил", "зарплат", "зп", "доход", "заработ", "плюс", "пришл", "пришел", "аванс",
        "преми", "кешбек", "кешбэк", "кэшбек", "кэшбэк", "подарили", "подарила", "подарил", "скинули", "перевели",
        "начисл", "пенси", "стипенд", "прибыл", "продал", "вернули", "выручк", "гонорар",
        "оплатили", "заплатили"
    )
    private val expenseStems = listOf(
        "потрат", "купил", "заплатил", "оплатил", "минус", "отдал", "расход", "спустил",
        "перевел", "скинул", "выпил", "съел", "поел", "заправ"
    )

    // Глаголы «я отдаю деньги» — всегда расход, даже если рядом слово «зарплата»
    private val outVerbs = listOf(
        "даю", "дал", "дала", "отдал", "отдаю", "выдал", "выдала", "передал", "передала",
        "перевел", "перевела", "перевожу", "скинул", "скинула", "отправил", "отправила",
        "выплатил", "выплатила", "заплатил", "заплатила", "оплатил", "оплатила",
        "подарил", "подарила", "одолжил", "одолжила", "дарю", "плачу", "заплачу"
    )
    private val returnVerbs = listOf("вернул", "вернула", "вернули", "возврат", "возвратил")

    private fun isPlural(w: String) = w.endsWith("ли") // «перевели», «дали» — это сделали мне

    fun parse(raw: String): Parsed? {
        val text = raw.lowercase()
            .replace('ё', 'е')
            .replace(Regex("""(\d)[\s\u00A0\u202F](?=\d{3}(?!\d))"""), "$1") // «2 500» → «2500»
            .replace(Regex("""(\d)[.,](?=\d{3}(?!\d))"""), "$1")              // «5.000», «1,500» → тысячи, не дробь
            .replace(Regex("""(\d)[.,](?=\d{3}(?!\d))"""), "$1")              // повтор для «1.000.000»
        val tokens = tokenRe.findAll(text).map { it.value }.toList()

        val amount = extractAmount(tokens) ?: return null
        if (amount <= 0) return null

        fun has(stems: List<String>) = tokens.any { w -> stems.any { w.startsWith(it) } }
        fun word(vararg ws: String) = tokens.any { it in ws }

        val who = People.extract(raw)
        val person = who?.name ?: ""
        val toMe = word("мне", "нам")
        val debtWord = tokens.any { it.startsWith("долг") }
        val outIdx = tokens.indexOfFirst { w -> !isPlural(w) && outVerbs.any { w.startsWith(it) } }
        val outVerb = outIdx >= 0
        val retIdx = tokens.indexOfFirst { w -> returnVerbs.any { w.startsWith(it) } }
        val subjectGives = who?.isSubject == true // «Марат дал 5000» — Марат отдаёт мне

        // 1) Возврат долга
        if (retIdx >= 0 || (debtWord && tokens.any { it.startsWith("отдал") })) {
            val toMeReturn = toMe || subjectGives || tokens.getOrNull(retIdx)?.let { isPlural(it) } == true
            return if (toMeReturn) Parsed(amount, true, "Долги", person, Kind.REPAY_IN)
            else Parsed(amount, false, "Долги", person, Kind.REPAY_OUT)
        }
        // 2) Я занял: «занял у Марата 5000», «взял в долг у брата»
        val takeVerb = word("занял", "заняла", "взял", "взяла", "одолжил", "одолжила", "занимаю")
        if (takeVerb && (word("у") || (debtWord && toMe))) {
            return Parsed(amount, true, "Долги", person, Kind.BORROW)
        }
        // 3) Я дал в долг: «дал в долг Марату», «одолжил другу», «занял Марату»
        if ((debtWord && outVerb && !toMe) || word("одолжил", "одолжила") ||
            (word("занял", "заняла") && person.isNotEmpty())
        ) {
            return if (toMe || subjectGives) Parsed(amount, true, "Долги", person, Kind.BORROW)
            else Parsed(amount, false, "Долги", person, Kind.LEND)
        }
        // 4) Я отдаю деньги: «даю зарплату Сулейману 5000», «перевёл маме 3000»
        if (outVerb && !toMe && !subjectGives) {
            val cat = when {
                has(listOf("зарплат", "зп", "аванс", "преми", "оплату труда")) -> "Сотрудникам"
                has(listOf("подар")) -> "Подарки"
                person.isNotEmpty() -> "Люди"
                else -> Categories.expenseCategory(tokens)
            }
            return Parsed(amount, false, cat, person)
        }
        // 5) Мне дали / перевели / получил от кого-то
        if ((outVerb && (toMe || subjectGives)) || (word("от") && person.isNotEmpty())) {
            val c = Categories.incomeCategory(tokens)
            return Parsed(amount, true, if (c == "Доход") "Перевод" else c, person)
        }

        // 6) Обычная фраза: «кофе 500», «зарплата 80 тысяч»
        val incomeWords = tokens.filter { w -> incomeStems.any { w.startsWith(it) } }.toSet()
        val incomeHit = incomeWords.isNotEmpty()
        val expenseHit = tokens.any { w -> w !in incomeWords && expenseStems.any { w.startsWith(it) } }
        val isIncome = incomeHit && !expenseHit

        val category = if (isIncome) Categories.incomeCategory(tokens) else Categories.expenseCategory(tokens)
        return Parsed(amount, isIncome, category, person)
    }

    private fun extractAmount(tokens: List<String>): Long? {
        val numbers = mutableListOf<Double>()
        var total = 0.0
        var cur = 0.0
        var inNum = false

        fun flush() {
            if (inNum) {
                val v = total + cur
                if (v > 0) numbers.add(v)
            }
            total = 0.0; cur = 0.0; inNum = false
        }

        for (t in tokens) {
            val d = t.replace(',', '.').toDoubleOrNull()
            when {
                d != null -> {
                    if (inNum && cur > 0) flush()
                    cur += d; inNum = true
                }
                units.containsKey(t) -> {
                    val v = units.getValue(t).toDouble()
                    if (inNum && cur > 0 && !combinable(cur, v)) flush()
                    cur += v; inNum = true
                }
                t == "полтора" || t == "полторы" -> {
                    if (inNum && cur > 0) flush()
                    cur += 1.5; inNum = true
                }
                t in thousand -> {
                    if (inNum || t in bare1000) {
                        total += (if (cur == 0.0) 1.0 else cur) * 1_000
                        cur = 0.0; inNum = true
                    }
                }
                t in million -> {
                    if (inNum || t.startsWith("лям")) {
                        total += (if (cur == 0.0) 1.0 else cur) * 1_000_000
                        cur = 0.0; inNum = true
                    }
                }
                t in currency -> flush()
                else -> flush()
            }
        }
        flush()
        // Если чисел несколько («2 кофе за 500») — берём наибольшее
        return numbers.maxOrNull()?.roundToLong()
    }

    /** «сто» + «двадцать» + «пять» складываются, «пять» + «пять» — нет. */
    private fun combinable(cur: Double, v: Double): Boolean {
        val place = when {
            cur % 100.0 == 0.0 -> 100.0
            cur % 10.0 == 0.0 -> 10.0
            else -> 1.0
        }
        return v < place && (place != 10.0 || v < 10.0)
    }
}
