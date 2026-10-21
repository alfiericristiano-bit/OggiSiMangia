#!/usr/bin/env bash
set -euo pipefail
package=it.oggisimangia.app
adb install app/build/outputs/apk/debug/app-debug.apk
adb shell run-as "$package" mkdir -p files
adb shell "run-as $package sh -c 'echo dati-conservati > files/update-check.txt'"
current=$(adb shell dumpsys package "$package" | sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' | head -1)
test -n "$current"
next=$((current + 1))
gradle --no-daemon :app:assembleDebug -PverificationVersionCode="$next"
adb install -r app/build/outputs/apk/debug/app-debug.apk
test "$(adb shell run-as "$package" cat files/update-check.txt | tr -d '\r')" = dati-conservati
adb shell dumpsys package "$package" | grep "versionCode=$next "
echo 'PASS: aggiornamento installato senza disinstallare; dati locali conservati.'

python3 scripts/calendar_smoke.py
