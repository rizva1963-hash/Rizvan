import json, sys, time
now = int(sys.argv[1])
D = 86400000
def day0(ms):
    t = time.localtime(ms / 1000)
    return int(time.mktime((t.tm_year, t.tm_mon, t.tm_mday, 0, 0, 0, 0, 0, -1)) * 1000)
today = day0(now)
rows = [
 (0, 9, 12, 450, 0, "Кафе", "Кофе 450", "", "voice"),
 (0, 8, 30, 1870, 0, "Продукты", "Пятёрочка", "", "sber"),
 (1, 21, 5, 640, 0, "Транспорт", "Такси 640", "", "voice"),
 (1, 12, 0, 180000, 1, "Зарплата", "Зарплата", "", "tbank"),
 (1, 13, 41, 1250, 0, "Кафе", "Кофемания", "", "sber"),
 (2, 18, 20, 780, 0, "Здоровье", "Аптека", "", "voice"),
 (2, 15, 2, 5000, 0, "Люди", "Марат С.", "Марат С.", "sber"),
 (2, 11, 15, 2100, 0, "Кафе", "Яндекс Еда", "", "tbank"),
 (3, 10, 0, 3400, 0, "Транспорт", "Бензин", "", "voice"),
 (3, 19, 30, 7800, 0, "Продукты", "Магнит", "", "sber"),
 (4, 14, 10, 12000, 1, "Перевод", "Иван И.", "Иван И.", "sber"),
 (5, 9, 45, 2600, 0, "Дом и связь", "Мегафон", "", "tbank"),
 (6, 17, 5, 9900, 0, "Одежда", "Lamoda", "", "tbank"),
 (7, 20, 15, 1500, 0, "Развлечения", "Кино", "", "voice"),
 (9, 12, 0, 150, 1, "Кешбэк", "Кешбэк", "", "tbank"),
]
out = []
for i, (d, h, m, amt, inc, cat, text, person, src) in enumerate(rows):
    ts = today - d * D + (h * 60 + m) * 60000
    if ts > now: ts = now - (i + 1) * 60000
    out.append(dict(id=ts * 1000 + i, ts=ts, amount=amt, income=bool(inc), category=cat, text=text, person=person, kind="normal", source=src))
print(json.dumps(out, ensure_ascii=False))
