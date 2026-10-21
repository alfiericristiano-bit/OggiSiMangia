"""Seed an emulator-only database, verify meal markers and capture the calendar."""
import datetime
import re
import sqlite3
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

def adb(*args):
    return subprocess.check_output(['adb', *args])

assert adb('shell', 'getprop', 'ro.kernel.qemu').decode().strip() == '1', 'Emulator required'
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
db.execute("INSERT INTO recipes (day,meal,category,title,ingredients) VALUES ('','Pranzo','Primo','Ricetta alternativa','riso')")
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

# Exercise tap, the >2 second threshold, No, meal/type context and actual SQLite planning.
import time

def snapshot(name):
    adb('shell', 'uiautomator', 'dump', '/sdcard/check.xml')
    data = adb('exec-out', 'cat', '/sdcard/check.xml')
    (out / (name + '.xml')).write_bytes(data)
    return list(ET.fromstring(data).iter('node'))

def center(node):
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.attrib['bounds']))
    return str((x1+x2)//2), str((y1+y2)//2)

def tap(node):
    adb('shell', 'input', 'tap', *center(node))
    time.sleep(0.4)

def hold(node, duration):
    x,y = center(node)
    adb('shell', 'input', 'swipe', x,y,x,y,str(duration))
    time.sleep(0.4)

def text_node(nodes, text):
    return next(n for n in nodes if n.attrib.get('text') == text)

def find_scrolling(text, name):
    for attempt in range(12):
        current = snapshot(name)
        matches = [n for n in current if n.attrib.get('text') == text and n.attrib.get('bounds') != '[0,0][0,0]']
        if matches: return matches[0]
        adb('shell', 'input', 'swipe','160','530','160','220','300')
    raise AssertionError('Missing UI text: '+text)

def db_rows():
    raw = adb('exec-out','run-as',package,'cat','databases/oggisimangia.db')
    target=out/'saved.db';target.write_bytes(raw)
    with sqlite3.connect(target) as connection:
        return connection.execute('SELECT title,day,meal,alternative FROM recipes ORDER BY id').fetchall()

cell = next(n for n in nodes if 'Pranzo e Cena' in n.attrib.get('content-desc',''))
tap(cell)
home = snapshot('tap-day')
assert any(n.attrib.get('text') == 'Il calendario dei pasti' for n in home)
assert not any(n.attrib.get('text') == 'Trova cosa cucinare' for n in home)
cell = next(n for n in home if 'Pranzo e Cena' in n.attrib.get('content-desc',''))
assert cell.attrib.get('selected') == 'true'
hold(cell, 1200)
short = snapshot('short-hold')
assert not any(n.attrib.get('text') == 'Aggiungi ricetta a questo giorno?' for n in short)
cell = next(n for n in short if 'Pranzo e Cena' in n.attrib.get('content-desc',''))
hold(cell, 2400)
confirm = snapshot('hold-confirm')
tap(text_node(confirm, 'No'))
unchanged = snapshot('hold-no')
assert any(n.attrib.get('text') == 'Il calendario dei pasti' for n in unchanged)
assert len(db_rows()) == 3
cell = next(n for n in unchanged if 'Pranzo e Cena' in n.attrib.get('content-desc',''))
hold(cell, 2400)
tap(text_node(snapshot('hold-again'), 'Sì'))
tap(text_node(snapshot('meal'), 'Pranzo'))
kind = snapshot('kind')
assert text_node(kind,'Alternativa').attrib.get('checked') == 'true', 'Existing main meal should propose an alternative'
tap(text_node(kind,'Cerca ricetta'))
search = snapshot('search')
assert any(n.attrib.get('text') == 'Trova cosa cucinare' for n in search)
assert any(n.attrib.get('text') == 'Pranzo · Alternativa' for n in search)
assert any('Massimo 10 ingredienti' in n.attrib.get('text','') for n in search)
button = text_node(search,'Ho già trovato la ricetta da aggiungere')
tap(button)
link = snapshot('recipe-link')
assert any(n.attrib.get('text') == 'Aggiungi una ricetta dal link' for n in link)
(out / 'recipe-link.png').write_bytes(adb('exec-out', 'screencap', '-p'))
# Reuse a saved recipe to test scheduling without depending on external sites.
nav = next(n for n in link if 'Ricettario' in n.attrib.get('text','') and n.attrib.get('clickable') == 'true')
tap(nav)
button=find_scrolling('📅 Pianifica «Ricetta alternativa»','archive')
tap(button)
picker=snapshot('date-picker')
ok=next(n for n in picker if n.attrib.get('resource-id') == 'android:id/button1')
tap(ok)
meal=snapshot('save-meal')
assert text_node(meal,'Pranzo · Alternativa').attrib.get('checked') == 'true'
tap(text_node(meal,'Aggiungi'))
rows=db_rows()
assert rows[-1] == ('Ricetta alternativa',today,'Pranzo',1), rows
button=find_scrolling('🔄 Alternativa · Primo · Ricetta alternativa','home-alternative')
assert button is not None
(out / 'home-alternative.png').write_bytes(adb('exec-out','screencap','-p'))
print('PASS: tap selects day; 1200ms does not prompt; 2400ms prompts; No preserves data; existing main proposes alternative.')
print('PASS: lunch/alternative context reaches search and saved-recipe planning; SQLite and the home dashboard show the saved alternative.')
