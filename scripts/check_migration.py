"""Exercise SQLite v1/v2/v3/v4 to v5 migrations without losing stored recipes."""
import re
import sqlite3
from pathlib import Path
source = Path('app/src/main/java/it/oggisimangia/app/RecipeDb.kt').read_text()
schema = re.search(r'CREATE TABLE recipes \((.*?)\n            \)', source, re.S).group(1)
columns = [line.strip().rstrip(',') for line in schema.splitlines() if line.strip()]
new_v2 = {'originalInstructions', 'saveMode'}
new_v3 = {'prepTime','cookTime','totalTime','restTime','portions','sourceImageUrl','nutrition'}
new_v4 = {'description','cuisine','equipment','tips','notes','storage','variations','allergens','tags','temperature','author','cost','isPersonal'}
new_v5 = {'scanPages', 'dishPhotos', 'scanOcrText'}
for version in [1, 2, 3, 4]:
    excluded = new_v5 | (new_v4 if version < 4 else set()) | (new_v3 if version < 3 else set()) | (new_v2 if version == 1 else set())
    old_columns = [c for c in columns if c.split()[0] not in excluded]
    db = sqlite3.connect(':memory:')
    db.execute('CREATE TABLE recipes (' + ','.join(old_columns) + ')')
    db.execute("INSERT INTO recipes (id,day,meal,category,title,ingredients,instructions,imagePath) VALUES (7,'2026-10-21','Cena','Primo','Risotto','200 g riso','Cuoci 18 minuti','/photos/riso.jpg')")
    if version == 2:
        db.execute("UPDATE recipes SET originalInstructions='Cuoci 18 minuti', saveMode='Entrambe'")
    before = db.execute('SELECT * FROM recipes').fetchone()
    for target, body in re.findall(r'if \(oldVersion < (\d+)\) \{(.*?)\n        \}', source, re.S):
        if version < int(target):
            for statement in re.findall(r'db.execSQL\("(ALTER TABLE [^"]+)"\)', body):
                db.execute(statement)
    after = db.execute('SELECT * FROM recipes').fetchone()
    assert after[:len(before)] == before, (version, before, after)
    names = [r[1] for r in db.execute('PRAGMA table_info(recipes)')]
    assert names == [c.split()[0] for c in columns]
    for field in new_v3:
        if version < 3: assert after[names.index(field)] == ''
    if version < 4:
        for field in new_v4 - {'isPersonal'}:
            assert after[names.index(field)] == ''
        assert after[names.index('isPersonal')] == 0
    assert after[names.index('scanPages')] == '[]'
    assert after[names.index('dishPhotos')] == '[]'
    assert after[names.index('scanOcrText')] == ''
    db.execute("UPDATE recipes SET prepTime='10 min',cookTime='18 min',portions='4',meal='Entrambi',sourceImageUrl='https://example.org/riso.jpg', tips='Usa il brodo caldo', notes='Ricetta della nonna', isPersonal=1 WHERE id=7")
    db.execute("INSERT INTO recipes (day,meal,category,title) VALUES ('','Pranzo','Primo','Solo archivio')")
    db.execute("INSERT INTO recipes (day,meal,category,title) VALUES ('2026-11-01','Cena','Secondo','Altro mese')")
    assert db.execute("SELECT day,meal FROM recipes WHERE day>=? AND day<=?", ('2026-10-01','2026-10-31')).fetchall() == [('2026-10-21','Entrambi')]
    assert db.execute('SELECT prepTime,cookTime,portions,imagePath,tips,notes,isPersonal FROM recipes WHERE id=7').fetchone() == ('10 min','18 min','4','/photos/riso.jpg','Usa il brodo caldo','Ricetta della nonna',1)
    db.execute("UPDATE recipes SET scanPages='[\"/photos/scan1.jpg\"]', dishPhotos='[]', scanOcrText='testo OCR' WHERE id=7")
    assert db.execute('SELECT scanPages,scanOcrText FROM recipes WHERE id=7').fetchone() == ('[\"/photos/scan1.jpg\"]','testo OCR')
    print(f'PASS: v{version} -> v5 preserves recipes/photos; OCR pages and manual metadata work.')
