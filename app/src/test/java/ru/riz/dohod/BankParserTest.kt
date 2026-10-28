package ru.riz.dohod

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class BankParserTest {
    private fun p(s: String): BankOp {
        val op = BankParser.parse(s)
        println("$s  =>  $op")
        assertNotNull("не распознано: $s", op)
        return op!!
    }

    @Test fun sberSms() {
        val o = p("MIR-1234 14:05 Покупка 500р KOFEMANIYA Баланс: 12 345.67р")
        assertEquals(500L, o.amount); assertFalse(o.income); assertTrue((o.balance ?: 0L) in 12345L..12346L)
    }
    @Test fun sberPush() {
        val o = p("Покупка, карта *1234\n500 ₽, Пятёрочка. Доступно 12 345,67 ₽")
        assertEquals(500L, o.amount); assertFalse(o.income); assertEquals("Продукты", o.category)
    }
    @Test fun salary() {
        val o = p("Зачисление зарплаты 80 000 ₽")
        assertEquals(80000L, o.amount); assertTrue(o.income); assertEquals("Зарплата", o.category)
    }
    @Test fun transferIn() {
        val o = p("Перевод от Ивана И. 5 000 ₽")
        assertEquals(5000L, o.amount); assertTrue(o.income)
    }
    @Test fun transferOut() {
        val o = p("Перевод 5000р Марат С.")
        assertEquals(5000L, o.amount); assertFalse(o.income)
    }
    @Test fun topUp() {
        val o = p("Пополнение, счет RUB. 5 000 ₽. Доступно 7 000 ₽")
        assertEquals(5000L, o.amount); assertTrue(o.income)
    }
    @Test fun codeSkipped() {
        assertNull(BankParser.parse("Код подтверждения 1234. Никому не сообщайте. Покупка 500 ₽"))
    }

    private fun none(s: String) { assertNull("должно быть пропущено: $s", BankParser.parse(s)) }

    @Test fun promoCashbackIsNotIncome() {
        none("Вы можете получить кэшбэк 1000 ₽ за покупки по карте")
        none("Т-Банк: кэшбэк до 1000 ₽ за покупки в октябре. Подключите предложение")
        none("Получите 1000 ₽ кэшбэка за первую покупку")
        none("Кэшбэк 1000 ₽ ждёт вас. Успейте активировать")
        none("Вернём 1000 ₽ за покупки от 3000 ₽")
        none("Кэшбэк 1000 ₽ будет начислен 5 ноября")
        none("Предлагаем кэшбэк 1000 ₽ на любимые категории")
        none("Кэшбэк 1000 ₽")
        none("Бонус до 1000 ₽ за приглашённого друга")
    }

    @Test fun realCashbackIsIncome() {
        val a = p("Вам начислен кэшбэк 150 ₽")
        assertTrue(a.income); assertEquals(150L, a.amount); assertEquals("Кешбэк", a.category)
        val b = p("Начислен кэшбэк за покупки 1 000 ₽. Доступно 20 000 ₽")
        assertTrue(b.income); assertEquals(1000L, b.amount)
        val c = p("Зачисление кешбэка 320 ₽")
        assertTrue(c.income); assertEquals(320L, c.amount)
    }

    @Test fun dateWithDoIsNotPromo() {
        val o = p("Покупка 700 ₽ Магнит. Баланс 5 000 ₽. Карта действует до 10/28")
        assertEquals(700L, o.amount); assertFalse(o.income)
    }
}
