import sys
skin, mode = sys.argv[1], sys.argv[2]
cats = '[{"name":"Рыбалка","income":false,"kw":["удочк"]}]'.replace('"', '&quot;')
print("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>")
print('<map><long name="start_balance" value="250000"/><long name="budget" value="180000"/>'
      f'<string name="skin">{skin}</string><string name="theme_mode">{mode}</string>'
      f'<string name="custom_cats">{cats}</string></map>')
