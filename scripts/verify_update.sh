#!/usr/bin/env bash
set -euo pipefail
package=it.oggisimangia.app
baseline=build/update-check/previous-beta7-debug.apk
new=app/build/outputs/apk/debug/app-debug.apk
test -s "$baseline"
test -s "$new"

# Verify true in-place restoration from installed beta7 (code 34) to
# 1.10.0-ripartenza (code 35), no uninstall or application-storage clearing.
adb install "$baseline"
old_code=$(adb shell dumpsys package "$package" | sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' | head -1)
test "$old_code" = 34
adb shell run-as "$package" mkdir -p files/photos databases
adb shell "run-as $package sh -c 'echo dati-conservati > files/update-check.txt'"
adb shell "run-as $package sh -c 'echo foto-conservata > files/photos/update-photo.txt'"

mkdir -p build/update-check
python3 - <<'PY'
from pathlib import Path
import sqlite3, re
source = Path('app/src/main/java/it/oggisimangia/app/RecipeDb.kt').read_text()
match = re.search(r'CREATE TABLE recipes \((.*?)\n            \)', source, re.S)
assert match, 'Cannot locate v1.10 recipe schema'
dbfile = Path('build/update-check/previous-recipes.db')
dbfile.unlink(missing_ok=True)
with sqlite3.connect(dbfile) as db:
    db.execute('CREATE TABLE recipes (' + match.group(1) + ')')
    db.execute("INSERT INTO recipes(day,meal,category,title) VALUES(?,?,?,?)",
               ("2026-10-30","Cena","Primo","DATI_DA_CONSERVARE_BETA7"))
    db.execute('PRAGMA user_version=5')
print('Prepared persistent recipe fixture using original v1.10 schema v5')
PY
adb push build/update-check/previous-recipes.db /data/local/tmp/oggisimangia-preupgrade.db
adb shell run-as "$package" cp /data/local/tmp/oggisimangia-preupgrade.db databases/oggisimangia.db

adb install -r "$new"
new_code=$(adb shell dumpsys package "$package" | sed -n 's/.*versionCode=\([0-9]*\).*/\1/p' | head -1)
test "$new_code" = 35
test "$(adb shell run-as "$package" cat files/update-check.txt | tr -d '\r')" = dati-conservati
test "$(adb shell run-as "$package" cat files/photos/update-photo.txt | tr -d '\r')" = foto-conservata

adb shell am start -W -n "$package/.MainActivity"
adb shell am force-stop "$package"
adb exec-out run-as "$package" cat databases/oggisimangia.db > build/update-check/upgraded-recipes.db
python3 - <<'PY'
import sqlite3
with sqlite3.connect('build/update-check/upgraded-recipes.db') as db:
    rows = db.execute("SELECT day,meal,category,title FROM recipes WHERE title=?",
        ('DATI_DA_CONSERVARE_BETA7',)).fetchall()
    assert rows == [('2026-10-30','Cena','Primo','DATI_DA_CONSERVARE_BETA7')], rows
    assert db.execute('PRAGMA user_version').fetchone()[0] == 5
print('PASS: beta7 recipe survived real in-place restart to v1.10 code 35')
PY
echo 'PASS: versionCode 34 -> 35; photos, files, and recipes preserved.'

# Original v1.10 UI and search smoke regression.
python3 scripts/calendar_smoke.py
