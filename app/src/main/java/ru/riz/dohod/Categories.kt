package ru.riz.dohod

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Своя категория пользователя: название, тип и слова, по которым она узнаётся в голосе и в банковских текстах. */
data class CustomCat(val name: String, val income: Boolean, val keywords: List<String>)

object Categories {

    private var custom: List<CustomCat> = emptyList()

    fun load(ctx: Context) {
        custom = try {
            val a = JSONArray(Prefs.str(ctx, "custom_cats", "[]"))
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                CustomCat(
                    o.getString("name"), o.optBoolean("income"),
                    o.optJSONArray("kw")?.let { k -> (0 until k.length()).map { i -> k.getString(i) } } ?: emptyList()
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun save(ctx: Context) {
        val a = JSONArray()
        custom.forEach { c ->
            a.put(JSONObject().put("name", c.name).put("income", c.income).put("kw", JSONArray(c.keywords)))
        }
        Prefs.setStr(ctx, "custom_cats", a.toString())
    }

    fun customList(): List<CustomCat> = custom

    /** false, если такое имя уже есть среди встроенных или своих. */
    fun addCustom(ctx: Context, name: String, income: Boolean, keywords: List<String>): Boolean {
        val n = name.trim().replaceFirstChar { it.uppercase() }
        if (n.isBlank() || allNames().any { it.equals(n, ignoreCase = true) }) return false
        custom = custom + CustomCat(n, income, keywords.map { it.trim().lowercase() }.filter { it.length >= 3 })
        save(ctx)
        return true
    }

    fun removeCustom(ctx: Context, name: String) {
        custom = custom.filter { it.name != name }
        save(ctx)
    }

    private fun allNames() = baseExpense + baseIncome + custom.map { it.name }

    private fun stem(w: String): String {
        val x = w.trim().lowercase()
        return if (x.length >= 6) x.dropLast(2) else if (x.length >= 4) x.dropLast(1) else x
    }

    private fun customMatch(tokens: List<String>, income: Boolean): String? {
        for (c in custom) {
            if (c.income != income) continue
            val stems = (listOf(c.name) + c.keywords).map { stem(it) }.filter { it.length >= 3 }
            if (tokens.any { w -> stems.any { w.startsWith(it) } }) return c.name
        }
        return null
    }


    // Порядок важен: первая совпавшая категория выигрывает
    private val expense: List<Pair<String, List<String>>> = listOf(
        "Кафе" to listOf(
            "кофе", "кафе", "ресторан", "обед", "ужин", "завтрак", "шаурм", "шаверм", "пицц",
            "бургер", "макдон", "kfc", "столов", "чай", "капучино", "латте", "суши", "ролл",
            "шашлык", "хинкал", "чуду", "выпил", "поел", "съел", "доставк"
        ),
        "Продукты" to listOf(
            "продукт", "магазин", "хлеб", "молок", "мясо", "овощ", "фрукт", "пятерочк",
            "магнит", "ашан", "лент", "перекрест", "еда", "еду", "яйц", "сахар", "рынок", "рынк"
        ),
        "Транспорт" to listOf(
            "такси", "бензин", "заправ", "топлив", "метан", "пропан", "метро", "автобус",
            "маршрутк", "проезд", "парковк", "мойк", "шиномонтаж", "запчаст", "масло", "штраф",
            "билет", "машин"
        ),
        "Дом и связь" to listOf(
            "квартплат", "жкх", "свет", "электричеств", "интернет", "коммунал", "аренд", "газ",
            "вода", "воду", "телефон", "связь", "мобильн", "ремонт"
        ),
        "Здоровье" to listOf("аптек", "лекарств", "таблет", "врач", "клиник", "анализ", "стоматолог"),
        "Одежда" to listOf(
            "одежд", "обув", "кроссовк", "куртк", "футболк", "штан", "джинс", "рубашк", "ботин"
        ),
        "Дети" to listOf("дет", "ребен", "сад", "школ", "игрушк", "памперс"),
        "Красота" to listOf("стрижк", "парикмах", "барбер", "салон", "космет"),
        "Бизнес" to listOf("офис", "бизнес", "налог", "бухгалтер", "реклам", "поставщик", "закупк", "склад"),
        "Развлечения" to listOf("кино", "игр", "подписк", "развлеч", "бар", "кальян", "боулинг", "охот"),
        "Подарки" to listOf("подар", "цвет", "букет"),
    )

    private val emoji = mapOf(
        "Кафе" to "☕", "Продукты" to "🛒", "Транспорт" to "🚕", "Дом и связь" to "🏠",
        "Здоровье" to "💊", "Одежда" to "👕", "Дети" to "🧸", "Развлечения" to "🎬",
        "Подарки" to "🎁", "Красота" to "💈", "Прочее" to "📦",
        "Зарплата" to "💼", "Подарок" to "🎁", "Перевод" to "💸", "Кешбэк" to "🔁",
        "Продажа" to "🏷️", "Доход" to "💰",
        "Люди" to "👥", "Сотрудникам" to "👷", "Долги" to "🤝", "Остальное" to "•••",
        "Возврат" to "↩️", "Наличные" to "💵", "Бизнес" to "🏢"
    )

    private val baseExpense: List<String> = expense.map { it.first } + listOf("Люди", "Сотрудникам", "Наличные", "Долги", "Прочее")
    private val baseIncome: List<String> = listOf("Зарплата", "Перевод", "Подарок", "Продажа", "Возврат", "Кешбэк", "Долги", "Доход")

    val expenseNames: List<String> get() = baseExpense.dropLast(1) + custom.filter { !it.income }.map { it.name } + "Прочее"
    val incomeNames: List<String> get() = baseIncome.dropLast(1) + custom.filter { it.income }.map { it.name } + "Доход"

    /** Цвет категории (ARGB) — для диаграммы и значков */
    fun color(category: String): Long = when (category) {
        "Кафе" -> 0xFFFFB547
        "Продукты" -> 0xFF3DDC84
        "Транспорт" -> 0xFF4DA3FF
        "Дом и связь" -> 0xFFA78BFA
        "Здоровье" -> 0xFFFF6B9A
        "Одежда" -> 0xFF2DD4BF
        "Дети" -> 0xFFFACC15
        "Красота" -> 0xFFE879F9
        "Развлечения" -> 0xFFFB7185
        "Подарки" -> 0xFFF97316
        "Зарплата" -> 0xFF3DDC84
        "Подарок" -> 0xFFF97316
        "Перевод" -> 0xFF4DA3FF
        "Продажа" -> 0xFFFACC15
        "Кешбэк" -> 0xFF2DD4BF
        "Доход" -> 0xFFA3E635
        "Люди" -> 0xFF64D2FF
        "Сотрудникам" -> 0xFFFFD60A
        "Долги" -> 0xFFBF5AF2
        else -> 0xFF8A8F98
    }

    fun emoji(category: String): String = emoji[category] ?: "📦"

    fun expenseCategory(tokens: List<String>): String {
        customMatch(tokens, false)?.let { return it }
        for ((name, stems) in expense) {
            if (tokens.any { w -> stems.any { w.startsWith(it) } }) return name
        }
        return "Прочее"
    }

    fun incomeCategory(tokens: List<String>): String {
        customMatch(tokens, true)?.let { return it }
        fun has(vararg s: String) = tokens.any { w -> s.any { w.startsWith(it) } }
        return when {
            has("зарплат", "зп", "аванс", "преми", "гонорар") -> "Зарплата"
            has("подар") -> "Подарок"
            has("кешбек", "кешбэк", "кэшбек", "кэшбэк") -> "Кешбэк"
            has("перевели", "скинули") -> "Перевод"
            has("продал", "выручк") -> "Продажа"
            else -> "Доход"
        }
    }
}
