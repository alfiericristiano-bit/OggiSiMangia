"""Exercise actual v1/v2 to v3 migration statements on populated SQLite databases."""
import re
import sqlite3
from pathlib import Path
source = Path('app/src/main/java/it/oggisimangia/app/RecipeDb.kt').read_text()
schema = re.search(r'CREATE TABLE recipes \((.*?)\n            \)', source, re.S).group(1)
columns = [line.strip().rstrip(',') for line in schema.splitlines() if line.strip()]
new_v2 = {'originalInstructions', 'saveMode'}
new_v3 = {'prepTime','cookTime','totalTime','restTime','portions','sourceImageUrl','nutrition'}
for version in [1, 2]:
    excluded = new_v3 | (new_v2 if version == 1 else set())
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
        assert after[names.index(field)] == ''
    db.execute("UPDATE recipes SET prepTime='10 min',cookTime='18 min',portions='4',meal='Entrambi',sourceImageUrl='https://example.org/riso.jpg' WHERE id=7")
    db.execute("INSERT INTO recipes (day,meal,category,title) VALUES ('','Pranzo','Primo','Solo archivio')")
    db.execute("INSERT INTO recipes (day,meal,category,title) VALUES ('2026-11-01','Cena','Secondo','Altro mese')")
    assert db.execute("SELECT day,meal FROM recipes WHERE day>=? AND day<=?", ('2026-10-01','2026-10-31')).fetchall() == [('2026-10-21','Entrambi')]
    assert db.execute('SELECT prepTime,cookTime,portions,imagePath FROM recipes WHERE id=7').fetchone() == ('10 min','18 min','4','/photos/riso.jpg')
    print(f'PASS: v{version} -> v3 preserves existing recipe and photo; metadata and month filter work.')
