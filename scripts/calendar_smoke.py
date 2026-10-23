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
adb('shell','settings','put','secure','show_ime_with_hard_keyboard','1')
out = Path('build/calendar-check'); out.mkdir(parents=True, exist_ok=True)
source = Path('app/src/main/java/it/oggisimangia/app/RecipeDb.kt').read_text()
schema = re.search(r'CREATE TABLE recipes \((.*?)\n            \)', source, re.S).group(1)
fixture = out / 'fixture.db'
fixture.unlink(missing_ok=True)
db = sqlite3.connect(fixture)
db.execute('CREATE TABLE recipes (' + schema + ')')
# Fixture already has the newest schema; never run ALTER TABLE twice.
version = re.search(r'SQLiteOpenHelper\(context, "oggisimangia\.db", null, (\d+)\)', source)
assert version, 'Database schema version not found'
db.execute('PRAGMA user_version=' + version.group(1))
today = adb('shell', 'date', '+%F').decode().strip()
for meal, title, ingredients in [('Pranzo', 'Pasta alle zucchine', 'pasta, zucchine'), ('Cena', 'Frittata', '2 uova, sale')]:
    db.execute('INSERT INTO recipes (day,meal,category,title,ingredients,prepTime,cookTime,portions) VALUES (?,?,?,?,?,?,?,?)',
               (today, meal, 'Primo' if meal == 'Pranzo' else 'Secondo', title, ingredients, '10 min', '20 min', '2 persone'))
db.execute("INSERT INTO recipes (day,meal,category,title,ingredients) VALUES ('','Pranzo','Primo','Ricetta alternativa','riso')")
db.execute("UPDATE recipes SET originalInstructions=? WHERE title='Pasta alle zucchine'", ('\n'.join(f'{i}. Mescola gli ingredienti e cuoci per 10 minuti, poi lascia riposare prima di servire.' for i in range(1,75)),))
db.commit(); db.close()
adb('shell', 'am', 'force-stop', package)
adb('push', str(fixture), '/data/local/tmp/calendar-fixture.db')
adb('shell', 'run-as', package, 'mkdir', '-p', 'databases')
adb('shell', 'run-as', package, 'cp', '/data/local/tmp/calendar-fixture.db', 'databases/oggisimangia.db')
adb('shell', 'am', 'start', '-W', '-n', package + '/.MainActivity')
# New users go through the persisted profile gate before the calendar (fixture uses empty preferences).
import time
def intro_nodes(label):
    adb('shell', 'rm', '-f', '/sdcard/intro.xml')
    adb('shell', 'uiautomator', 'dump', '/sdcard/intro.xml')
    raw = adb('exec-out', 'cat', '/sdcard/intro.xml')
    (out / (label + '.xml')).write_bytes(raw)
    return list(ET.fromstring(raw).iter('node'))

def intro_tap(nodes, expected):
    node = next(n for n in nodes if expected in n.attrib.get('text', '') or
                expected in n.attrib.get('content-desc', ''))
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.attrib['bounds']))
    adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))

time.sleep(0.6)
intro = intro_nodes('onboarding-first')
if any('Come ti chiami?' in n.attrib.get('text', '') for n in intro):
    intro_tap(intro, 'Il tuo nome')
    adb('shell', 'input', 'text', 'Tester')
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
    intro_tap(intro_nodes('onboarding-typed'), 'Continua')
    welcome = intro_nodes('onboarding-welcome')
    assert any('Benvenuto, Tester' in n.attrib.get('text', '') for n in welcome), 'Welcome did not use saved name'
    intro_tap(welcome, 'Inizia')
    time.sleep(0.7)
    completed = intro_nodes('onboarding-complete')
    # The calendar immediately displays its first-use help dialog, which overlays the page title.
    assert any(('Calendario' in n.attrib.get('text', '') or
                'Come funziona il calendario' in n.attrib.get('text', '')) for n in completed), 'Calendar or its first-use help did not open after onboarding'
    print('PASS: first-run personalized name, welcome and preserved Android profile.')

# The new first-run help is part of the UI: verify and dismiss it, opting out persistently.
import time
time.sleep(0.7)
adb('shell', 'uiautomator', 'dump', '/sdcard/calendar-help.xml')
help_data = adb('exec-out', 'cat', '/sdcard/calendar-help.xml')
(out / 'calendar-help.xml').write_bytes(help_data)
help_nodes = list(ET.fromstring(help_data).iter('node'))
assert any(n.attrib.get('text') == 'Come funziona il calendario' for n in help_nodes), 'Calendar help popup missing'
def help_tap(text):
    node = next(n for n in help_nodes if n.attrib.get('text','').casefold() == text.casefold())
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.attrib['bounds']))
    adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
help_tap('Non mostrare più questo messaggio')
help_tap('Ho capito')
time.sleep(0.3)
adb('shell', 'uiautomator', 'dump', '/sdcard/calendar.xml')
xml = adb('exec-out', 'cat', '/sdcard/calendar.xml')
(out / 'calendar.xml').write_bytes(xml)
nodes = list(ET.fromstring(xml).iter('node'))
assert any('Pranzo e Cena' in n.attrib.get('content-desc','') for n in nodes), 'Missing lunch/dinner day markers'
(out / 'calendar.png').write_bytes(adb('exec-out', 'screencap', '-p'))
print('PASS: calendar starts with SQLite recipes and exposes both meal markers on the correct day.')

# Exercise native Android-standard long press, No, meal/type context and SQLite planning.
import time

def snapshot(name):
    # A failed uiautomator dump can leave yesterday's check.xml in place.
    # Remove it first, then retry until a fresh, parseable hierarchy exists.
    last_error = None
    for attempt in range(5):
        adb('shell', 'rm', '-f', '/sdcard/check.xml')
        try:
            adb('shell', 'uiautomator', 'dump', '/sdcard/check.xml')
            data = adb('exec-out', 'cat', '/sdcard/check.xml')
            nodes = list(ET.fromstring(data).iter('node'))
            if not nodes:
                raise AssertionError('Empty UI hierarchy')
            (out / (name + '.xml')).write_bytes(data)
            return nodes
        except (subprocess.CalledProcessError, ET.ParseError, AssertionError) as error:
            last_error = error
            time.sleep(0.5)
    raise AssertionError(f'Could not obtain a fresh UI hierarchy ({name}): {last_error}')

def restart_home(name):
    adb('shell', 'am', 'force-stop', package)
    adb('shell', 'am', 'start', '-W', '-n', package + '/.MainActivity')
    for attempt in range(10):
        nodes = snapshot(name)
        if any(n.attrib.get('text') == 'Calendario' for n in nodes):
            return nodes
        time.sleep(0.4)
    raise AssertionError('Calendar did not become ready after app restart: ' + name)

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
    # Buttons may include an icon before a stable, spoken label.
    return next(n for n in nodes if n.attrib.get('text','').casefold() == text.casefold()
                or n.attrib.get('text','').casefold().endswith(' '+text.casefold()))

def find_scrolling(text, name):
    for attempt in range(12):
        current = snapshot(name)
        matches = [n for n in current if (n.attrib.get('text') == text
                   or n.attrib.get('content-desc') == text)
                   and n.attrib.get('bounds') != '[0,0][0,0]']
        if matches: return matches[0]
        adb('shell', 'input', 'swipe','160','530','160','220','300')
    raise AssertionError('Missing UI text: '+text)

def find_clickable_containing(fragment, name):
    """Search the actual scrollable screen, not only the first viewport."""
    for attempt in range(14):
        current=snapshot(name+'-'+str(attempt))
        matches=[n for n in current if fragment in n.attrib.get('text','')
                 and n.attrib.get('clickable')=='true'
                 and n.attrib.get('bounds') != '[0,0][0,0]']
        if matches:
            return matches[0]
        adb('shell','input','swipe','170','580','170','220','320')
    raise AssertionError('Missing clickable control after scrolling: '+fragment)

def db_rows():
    raw = adb('exec-out','run-as',package,'cat','databases/oggisimangia.db')
    target=out/'saved.db';target.write_bytes(raw)
    with sqlite3.connect(target) as connection:
        return connection.execute('SELECT title,day,meal,alternative FROM recipes ORDER BY id').fetchall()

cell = next(n for n in nodes if 'Pranzo e Cena' in n.attrib.get('content-desc',''))
tap(cell)
home = snapshot('tap-day')
assert any(n.attrib.get('text') == 'Calendario' for n in home)
assert not any(n.attrib.get('text') == 'Cerca ricetta' for n in home)
cell = next(n for n in home if 'Pranzo e Cena' in n.attrib.get('content-desc',''))
assert cell.attrib.get('selected') == 'true'
hold(cell, 120)
short = snapshot('short-hold')
assert not any(n.attrib.get('text') == 'Aggiungi ricetta a questo giorno?' for n in short)
cell = next(n for n in short if 'Pranzo e Cena' in n.attrib.get('content-desc',''))
hold(cell, 900)
confirm = snapshot('hold-confirm')
tap(text_node(confirm, 'No'))
unchanged = snapshot('hold-no')
assert any(n.attrib.get('text') == 'Calendario' for n in unchanged)
assert len(db_rows()) == 3
cell = next(n for n in unchanged if 'Pranzo e Cena' in n.attrib.get('content-desc',''))
hold(cell, 900)
tap(text_node(snapshot('hold-again'), 'Sì'))
tap(text_node(snapshot('meal'), 'Pranzo'))
kind = snapshot('kind')
assert text_node(kind,'Alternativa').attrib.get('checked') == 'true', 'Existing main meal should propose an alternative'
tap(text_node(kind,'Cerca ricetta'))
search = snapshot('search')
assert any(n.attrib.get('text') == 'Cerca ricetta' for n in search)
assert any(n.attrib.get('text') == 'Pranzo · Alternativa' for n in search)
assert any('Massimo 10 ingredienti' in n.attrib.get('text','') for n in search)
button = text_node(search,'Ho un link')
tap(button)
link = snapshot('recipe-link')
assert any(n.attrib.get('text') == 'Aggiungi una ricetta dal link' for n in link)
(out / 'recipe-link.png').write_bytes(adb('exec-out', 'screencap', '-p'))
# Reuse a saved recipe to test scheduling without depending on external sites.
nav = next(n for n in link if n.attrib.get('content-desc') == 'Ricette' and n.attrib.get('clickable') == 'true')
tap(nav)
button=find_scrolling('📅 Pianifica «Ricetta alternativa»','archive')
tap(button)
picker=snapshot('date-picker')
ok=next(n for n in picker if n.attrib.get('resource-id') == 'android:id/button1')
tap(ok)
meal=snapshot('save-meal')
assert text_node(meal,'Pranzo · Alternativa').attrib.get('checked') == 'true'
tap(text_node(meal,'Aggiungi'))
cloned=snapshot('clone-confirmed')
assert any(n.attrib.get('text') == 'Ricetta copiata' for n in cloned), 'Cloning confirmation missing'
tap(text_node(cloned,'Torna al calendario'))
rows=db_rows()
assert rows[-1] == ('Ricetta alternativa',today,'Pranzo',1), rows
button=find_scrolling('Ricetta alternativa','home-alternative')
assert button is not None
(out / 'home-alternative.png').write_bytes(adb('exec-out','screencap','-p'))
print('PASS: tap selects day; 120ms short press does not prompt; 900ms native long press prompts; No preserves data; existing main proposes alternative.')
print('PASS: lunch/alternative context reaches search and saved-recipe planning; SQLite and the home dashboard show the saved alternative.')

# Native PDF output and the Android print UI (no physical print is sent).
tap(find_scrolling('PDF e stampa del giorno','report-action'))
ready=snapshot('report-ready')
assert any(n.attrib.get('text') == 'Report PDF pronto' for n in ready)
files=adb('shell','run-as',package,'ls','cache/reports').decode().splitlines()
assert files
pdf=adb('exec-out','run-as',package,'cat','cache/reports/'+files[-1])
assert pdf.startswith(b'%PDF') and len(pdf)>1000
(out/'menu-test.pdf').write_bytes(pdf)
tap(text_node(ready,'Stampa'))
print_ui=snapshot('print-ui')
assert any('printspooler' in n.attrib.get('package','') for n in print_ui), 'Android print UI did not open'
adb('shell','input','keyevent','KEYCODE_BACK')
print('PASS: native multipage PDF generated and Android print service opened without submitting a print job.')

# Configure source priorities and a custom domain, then verify persistence after process restart.
home=restart_home('preferences-home')
assert not any(n.attrib.get('text')=='Come funziona il calendario' for n in home), 'Help opt-out was not saved'
tap(find_clickable_containing('Cerca', 'calendar-search-control'))
tap(find_scrolling('Priorità dei siti e nuove fonti','source-action'))
settings=snapshot('source-settings')
spinner=next(n for n in settings if n.attrib.get('content-desc','').startswith('1ª priorità:'))
tap(spinner)
choices=snapshot('source-choices')
tap(text_node(choices,'Misya'))
settings=snapshot('source-swapped')
tap(text_node(settings,'Aggiungi un sito'))
form=snapshot('source-form')
name=next(n for n in form if n.attrib.get('content-desc')=='Nome del sito')
tap(name);adb('shell','input','text','FonteTest')
form=snapshot('source-name')
address=next(n for n in form if n.attrib.get('content-desc')=='Indirizzo del sito')
tap(address);adb('shell','input','text','https://example.org')
adb('shell','input','keyevent','KEYCODE_BACK')
tap(text_node(snapshot('source-filled'),'Memorizza'))
# A disliked source can be deleted and stays gone after the app process restarts.
tap(find_scrolling('Elimina sito GialloZafferano','source-delete'))
confirm_delete=snapshot('source-delete-confirm')
assert any(n.attrib.get('text') == 'Eliminare il sito?' for n in confirm_delete)
tap(text_node(confirm_delete,'Elimina sito'))
home=restart_home('persist-home')
tap(find_clickable_containing('Cerca', 'calendar-search-control'))
# The first ingredient row grows to ten independent fields.
for index in range(1,11):
    current=snapshot('ingredients-'+str(index))
    field=next((n for n in current if n.attrib.get('content-desc')==('Ingrediente principale' if index == 1 else 'Ingrediente '+str(index))),None)
    for attempt in range(8):
        if field is not None: break
        adb('shell','input','swipe','160','530','160','220','300')
        current=snapshot('ingredients-scroll')
        field=next((n for n in current if n.attrib.get('content-desc')==('Ingrediente principale' if index == 1 else 'Ingrediente '+str(index))),None)
    assert field is not None
    tap(field);adb('shell','input','text','ingrediente'+str(index));adb('shell','input','keyevent','KEYCODE_BACK')
    if index<10: tap(find_scrolling('＋ Ingrediente','ingredient-add'))
last=snapshot('ingredients-limit')
limit=next((n for n in last if n.attrib.get('text')=='Massimo 10'),None)
if limit is None: limit=find_scrolling('Massimo 10','ingredient-limit-scroll')
assert limit.attrib.get('enabled')=='false'
tap(find_scrolling('Priorità dei siti e nuove fonti','source-persist-action'))
settings=snapshot('sources-restored')
first=next(n for n in settings if n.attrib.get('content-desc','').startswith('1ª priorità:'))
# Inspect the selected spinner descendant and the persisted authoritative preference file.
prefs=adb('exec-out','run-as',package,'cat','shared_prefs/recipe_sources.xml')
root=ET.fromstring(prefs)
import json
ordered=json.loads(next(e.text for e in root if e.attrib.get('name')=='ordered'))
assert ordered[0]['name']=='Misya',ordered
assert all(r['domain']!='giallozafferano.it' for r in ordered), 'Deleted source restored on restart'
assert any(r['domain']=='example.org' and r['name']=='FonteTest' for r in ordered)
assert first is not None
print('PASS: source priority, added and deleted domains survive process restart; ingredient fields reach ten.')
print('PASS: calendar guidance popup can be dismissed with Ho capito; do-not-show choice survives restart.')
