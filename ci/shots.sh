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
printf "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map><long name=\"start_balance\" value=\"250000\"/><long name=\"budget\" value=\"180000\"/></map>\n" \
  | adb shell "run-as $PKG sh -c 'cat > shared_prefs/settings.xml'"
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

adb shell am start -n $PKG/.MainActivity; sleep 5
shot home
adb shell input swipe 540 1900 540 700 500; shot home_scrolled
adb shell input swipe 540 700 540 1900 500
tap "Счета"; shot home_accounts
tap "Цели"; shot home_goals
tap "Операции"
tap "Обзор"; shot overview
tap "История"; shot history
tap "Ещё"; shot more
adb shell input swipe 540 1900 540 700 500; shot more_scrolled
adb shell input swipe 540 700 540 1900 500
tap "Автоучёт банков"; shot auto
adb shell input keyevent 4
tap "Главная"
tap "− Расход"; shot sheet_manual
adb shell input keyevent 4
tap "Kofemaniya"; tap "Кофемания"; shot sheet_edit
adb shell input keyevent 4
adb shell am start -n $PKG/.VoiceActivity --es demo_text "кофе 500 рублей"; shot voice_result
adb shell am start -n $PKG/.VoiceActivity --es demo_partial "такси триста пятьдесят"; shot voice_listen
exit 0
