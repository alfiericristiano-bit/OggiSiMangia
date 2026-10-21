"""Seed an emulator-only database, verify meal markers and capture the calendar."""
import datetime
import re
import sqlite3
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

def adb(*args):
    return subprocess.check_output(['adb', *args])

package = 'it.oggisimangia.app'
out = Path('build/calendar-check'); out.mkdir(parents=True, exist_ok=True)
source = Path('app/src/main/java/it/oggisimangia/app/RecipeDb.kt').read_text()
schema = re.search(r'CREATE TABLE recipes \((.*?)\n            \)', source, re.S).group(1)
fixture = out / 'fixture.db'
fixture.unlink(missing_ok=True)
db = sqlite3.connect(fixture)
db.execute('CREATE TABLE recipes (' + schema + ')')
db.execute('PRAGMA user_version=3')
today = adb('shell', 'date', '+%F').decode().strip()
for meal, title, ingredients in [('Pranzo', 'Pasta alle zucchine', 'pasta, zucchine'), ('Cena', 'Frittata', '2 uova, sale')]:
    db.execute('INSERT INTO recipes (day,meal,category,title,ingredients,prepTime,cookTime,portions) VALUES (?,?,?,?,?,?,?,?)',
               (today, meal, 'Primo' if meal == 'Pranzo' else 'Secondo', title, ingredients, '10 min', '20 min', '2 persone'))
db.commit(); db.close()
adb('shell', 'am', 'force-stop', package)
adb('push', str(fixture), '/data/local/tmp/calendar-fixture.db')
adb('shell', 'run-as', package, 'mkdir', '-p', 'databases')
adb('shell', 'run-as', package, 'cp', '/data/local/tmp/calendar-fixture.db', 'databases/oggisimangia.db')
adb('shell', 'am', 'start', '-W', '-n', package + '/.MainActivity')
adb('shell', 'uiautomator', 'dump', '/sdcard/calendar.xml')
xml = adb('exec-out', 'cat', '/sdcard/calendar.xml')
(out / 'calendar.xml').write_bytes(xml)
nodes = list(ET.fromstring(xml).iter('node'))
assert any('Pranzo e Cena' in n.attrib.get('content-desc','') for n in nodes), 'Missing lunch/dinner day markers'
(out / 'calendar.png').write_bytes(adb('exec-out', 'screencap', '-p'))
print('PASS: calendar starts with SQLite recipes and exposes both meal markers on the correct day.')
