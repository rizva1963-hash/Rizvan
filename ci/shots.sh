#!/usr/bin/env bash
# Снимки экранов на эмуляторе Android 15 (по ним проверяем вёрстку, отступы под часы и жестовую полосу).
PKG=ru.riz.dohod
OUT=out/shots
mkdir -p "$OUT"
adb install -r app/build/outputs/apk/debug/app-debug.apk || exit 1
adb shell pm grant $PKG android.permission.RECORD_AUDIO
adb shell am force-stop $PKG
adb shell run-as $PKG mkdir -p files shared_prefs
python3 ci/seed.py "$(date +%s)000" | adb shell "run-as $PKG sh -c 'cat > files/transactions.json'"
setprefs() { # $1 skin, $2 mode
  adb shell am force-stop $PKG
  python3 ci/prefs.py "$1" "$2" | adb shell "run-as $PKG sh -c 'cat > shared_prefs/settings.xml'"
}
adb shell cmd notification allow_listener $PKG/$PKG.BankListener || true
adb shell wm size; adb shell wm density
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell settings put system accelerometer_rotation 0

shot() { sleep 2.5; adb exec-out screencap -p > "$OUT/$1.png"; }
tap() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1
  xy=$(python3 ci/tap.py "$1")
  if [ -n "$xy" ]; then adb shell input tap $xy; else echo "NOT FOUND: $1"; fi
}


for combo in dial:dark dial:light ledger:light ledger:dark; do
  sk=${combo%%:*}; md=${combo##*:}; c=${sk}_${md}
  setprefs $sk $md
  adb shell am start -n $PKG/.MainActivity; sleep 8
  shot home_$c
  adb shell input swipe 540 1900 540 700 500; shot home_scrolled_$c
  adb shell input swipe 540 700 540 1900 500
  tap "Обзор"; shot overview_$c
  tap "История"; shot history_$c
  tap "Ещё"; shot more_$c
  adb shell am force-stop $PKG
  adb shell "am start -n $PKG/.VoiceActivity --es demo_text 'кофе 500 рублей'"; shot voice_$c
  adb shell am force-stop $PKG
done

# Режим «как в системе» при тёмной системной теме
setprefs ledger system
adb shell cmd uimode night yes
adb shell am start -n $PKG/.MainActivity; sleep 8
shot home_ledger_system_night
adb shell cmd uimode night no
adb shell am force-stop $PKG

# Остальные экраны и сценарии на скине по умолчанию
setprefs dial dark
adb shell am start -n $PKG/.MainActivity; sleep 8
tap "Счета"; shot home_accounts
tap "Цели"; shot home_goals
tap "Операции"
tap "Ещё"
adb shell input swipe 540 1900 540 700 500; shot more_scrolled
tap "Свои категории"; shot sheet_cats
adb shell input keyevent 4
adb shell input swipe 540 700 540 1900 500
tap "Автоучёт банков"; shot auto
adb shell input keyevent 4
tap "Главная"
tap "− Расход"; shot sheet_manual
adb shell input keyevent 4
tap "Кофемания"; shot sheet_edit
adb shell input keyevent 4
adb shell am force-stop $PKG

# Автозапись: фраза должна записаться сама, без нажатий
adb shell "am start -n $PKG/.VoiceActivity --es demo_text 'кофе 777 рублей' --ez demo_auto true"; sleep 6
adb shell "run-as $PKG cat files/transactions.json" > /tmp/tx_after.json
if grep -q "777" /tmp/tx_after.json; then echo "AUTOSAVE OK" > $OUT/autosave.txt; else echo "AUTOSAVE FAILED" > $OUT/autosave.txt; fi
adb shell am force-stop $PKG

# Своя категория: «Рыбалка» должна узнаваться в голосе
adb shell "am start -n $PKG/.VoiceActivity --es demo_text 'рыбалка 3000 рублей' --ez demo_auto true"; sleep 6
adb shell "run-as $PKG cat files/transactions.json" > /tmp/tx_after2.json
if grep -q "Рыбалка" /tmp/tx_after2.json; then echo "CUSTOM CATEGORY OK" > $OUT/custom.txt; else echo "CUSTOM CATEGORY FAILED" > $OUT/custom.txt; fi
adb shell am force-stop $PKG
adb shell "am start -n $PKG/.VoiceActivity --es demo_partial 'такси триста пятьдесят'"; shot voice_listen
exit 0
