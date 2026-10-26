package ru.riz.dohod

/**
 * Находит в фразе человека: «даю деньги другу Марату 5000» → «Марат»,
 * «перевёл маме 3000» → «Мама», «Марат вернул 5000» → «Марат» (он — подлежащее).
 */
object People {

    data class Who(val name: String, val isSubject: Boolean)

    private val wordRe = Regex("""[A-Za-zА-Яа-яЁё]+""")

    // родство/роль в любом падеже → именительный
    private val relations: Map<String, String> = buildMap {
        fun r(base: String, vararg forms: String) { forms.forEach { put(it, base) }; put(base.lowercase(), base) }
        r("Друг", "другу", "друга", "другом")
        r("Подруга", "подруге", "подруги", "подругу")
        r("Брат", "брату", "брата", "братом", "братишке", "братишка")
        r("Сестра", "сестре", "сестры", "сестру", "сестренке")
        r("Мама", "маме", "мамы", "маму", "матери", "мать")
        r("Папа", "папе", "папы", "папу", "отцу", "отца", "отец")
        r("Жена", "жене", "жены", "жену", "супруге")
        r("Муж", "мужу", "мужа", "супругу")
        r("Сын", "сыну", "сына", "сыночку")
        r("Дочь", "дочке", "дочери", "дочки", "дочку", "дочь")
        r("Сотрудник", "сотруднику", "сотрудника", "сотрудникам")
        r("Рабочий", "рабочему", "рабочего", "рабочим", "рабочие")
        r("Мастер", "мастеру", "мастера")
        r("Сосед", "соседу", "соседа")
        r("Коллега", "коллеге", "коллеги")
        r("Дядя", "дяде", "дяди", "дядю")
        r("Тётя", "тете", "тети", "тетю", "тетя")
        r("Бабушка", "бабушке", "бабушки", "бабушку")
        r("Дедушка", "дедушке", "дедушки", "дедушку")
        r("Племянник", "племяннику", "племянника")
        r("Водитель", "водителю", "водителя")
        r("Ребята", "ребятам", "ребят")
    }

    // Слова, которые распознавание пишет с большой буквы, но это не имена
    private val notNames = setOf(
        "я", "мы", "мне", "нам", "сегодня", "вчера", "даю", "дал", "дала", "отдал", "отдала", "получил",
        "получила", "перевел", "перевела", "скинул", "скинула", "купил", "купила", "потратил", "потратила",
        "заплатил", "заплатила", "оплатил", "оплатила", "вернул", "вернула", "вернули", "занял", "заняла",
        "одолжил", "одолжила", "подарил", "подарила", "зарплата", "зарплату", "кофе", "такси", "плюс",
        "минус", "рублей", "рубля", "тысяч", "тысячи", "долг", "долга", "в", "на", "за", "у", "от", "для",
        "яндекс", "озон", "ozon", "wildberries", "вайлдберриз", "авито", "сбер", "сбербанк", "тинькофф",
        "пятерочка", "магнит", "ашан", "лента", "макдональдс", "kfc", "бургер", "выпил", "выпила", "поел",
        "доход", "расход", "деньги", "денег", "аванс", "премия", "премию", "выдал", "выдала", "передал"
    )

    private val subjectVerbs = listOf(
        "вернул", "дал", "перевел", "скинул", "отдал", "одолжил", "заплатил", "подарил", "прислал", "отправил"
    )

    fun extract(raw: String): Who? {
        val words = wordRe.findAll(raw).map { it.value }.toList()
        if (words.isEmpty()) return null
        val low = words.map { it.lowercase().replace('ё', 'е') }

        fun isVerb(i: Int) = low.getOrNull(i)?.let { w -> subjectVerbs.any { w.startsWith(it) } } == true

        // 1) Имя с большой буквы (не первое слово — его распознавание пишет с заглавной всегда)
        for (i in words.indices) {
            val w = words[i]
            val l = low[i]
            if (w.length < 3 || !w[0].isUpperCase() || l in notNames || relations.containsKey(l)) continue
            if (w.all { it.isUpperCase() }) continue // аббревиатуры
            if (i == 0 && !isVerb(1)) continue
            val prev = low.getOrNull(i - 1)
            val (base, oblique) = baseForm(w, prev)
            val subject = !oblique && prev !in setOf("у", "от", "для") && isVerb(i + 1)
            return Who(base, subject)
        }
        // 2) Родственник/роль: «маме», «брат вернул»
        for (i in low.indices) {
            val base = relations[low[i]] ?: continue
            val nominative = low[i] == base.lowercase().replace('ё', 'е')
            return Who(base, nominative && isVerb(i + 1))
        }
        return null
    }

    /** «Андрею» → «Андрей», «Игорю» → «Игорь» */
    private fun soft(w: String): String = if ((w.getOrNull(w.length - 2)?.lowercaseChar() ?: 'x') in "аеёиоуыэюя") "й" else "ь"

    /** «Марату» → «Марат», «Маше» → «Маша», «у Марата» → «Марат». Второе значение — косвенный падеж. */
    private fun baseForm(w: String, prev: String?): Pair<String, Boolean> {
        val n = w.length
        if (prev == "у" || prev == "от" || prev == "для") {
            return when {
                w.endsWith("ии") -> w.dropLast(1) + "я" to true
                w.endsWith("ы") || w.endsWith("и") -> w.dropLast(1) + "а" to true
                w.endsWith("а") && n > 3 -> w.dropLast(1) to true
                w.endsWith("я") && n > 3 -> w.dropLast(1) + soft(w) to true
                else -> w to true
            }
        }
        return when {
            w.endsWith("у") && n > 3 -> w.dropLast(1) to true
            w.endsWith("ю") && n > 3 -> w.dropLast(1) + soft(w) to true
            w.endsWith("ие") -> w.dropLast(1) + "я" to true
            w.endsWith("е") && n > 3 -> w.dropLast(1) + "а" to true
            else -> w to false
        }
    }
}
