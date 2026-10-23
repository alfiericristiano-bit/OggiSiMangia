#!/usr/bin/env bash
set -euo pipefail
package=it.oggisimangia.app
baseline=build/update-check/previous-112-debug.apk
new=app/build/outputs/apk/debug/app-debug.apk
test -s "$baseline"
test -s "$new"

# Sign both the previously working v1.12 and new v1.13 with the same
# persistent keystore, then install using normal Android in-place upgrade.
adb install "$baseline"
previous=$(adb shell dumpsys package "$package" | sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' | head -1)
test -n "$previous"
adb shell run-as "$package" mkdir -p files/photos databases
adb shell "run-as $package sh -c 'echo dati-conservati > files/update-check.txt'"
adb shell "run-as $package sh -c 'echo foto-conservata > files/photos/update-photo.txt'"
python3 scripts/verify_real_upgrade.py seed
adb push build/update-check/previous-recipes.db /data/local/tmp/oggisimangia-preupgrade.db
adb shell run-as "$package" cp /data/local/tmp/oggisimangia-preupgrade.db databases/oggisimangia.db

# -r updates the existing installation without clearing application storage.
adb install -r "$new"
current=$(adb shell dumpsys package "$package" | sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' | head -1)
test -n "$current"
test "$current" -gt "$previous"
test "$(adb shell run-as "$package" cat files/update-check.txt | tr -d '\r')" = dati-conservati
test "$(adb shell run-as "$package" cat files/photos/update-photo.txt | tr -d '\r')" = foto-conservata

adb shell am start -W -n "$package/.MainActivity"
adb shell am force-stop "$package"
adb exec-out run-as "$package" cat databases/oggisimangia.db > build/update-check/upgraded-recipes.db
python3 scripts/verify_real_upgrade.py check
echo "PASS: versionCode $previous -> $current; signed APK upgraded over v1.12 without uninstall; recipe, OCR, file and profile storage retained."

# Run full UI smoke after the realistic upgrade regression.
python3 scripts/calendar_smoke.py
