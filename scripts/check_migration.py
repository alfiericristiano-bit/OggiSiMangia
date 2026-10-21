"""Exercise actual migration statements against a populated v1 SQLite database."""
import re
import sqlite3
from pathlib import Path

source = Path('app/src/main/java/it/oggisimangia/app/RecipeDb.kt').read_text()
db = sqlite3.connect(':memory:')
schema = re.search(r'CREATE TABLE recipes \((.*?)\n            \)', source, re.S).group(1)
old_schema = re.sub(r",\s*originalInstructions TEXT.*?saveMode TEXT NOT NULL DEFAULT 'Semplificata'", '', schema, flags=re.S)
db.execute('CREATE TABLE recipes (' + old_schema + ')')
db.execute("INSERT INTO recipes (id, day, meal, category, title, ingredients, instructions, imagePath) VALUES (7, '2026-10-21', 'Cena', 'Primo', 'Risotto', '200 g riso', 'Cuoci 18 minuti', '/photos/riso.jpg')")
before = db.execute('SELECT * FROM recipes').fetchone()
for statement in re.findall(r'db.execSQL\("(ALTER TABLE [^"]+)"\)', source):
    db.execute(statement)
after = db.execute('SELECT * FROM recipes').fetchone()
assert after[:len(before)] == before, (before, after)
assert after[-2:] == ('', 'Semplificata'), after
db.execute("UPDATE recipes SET originalInstructions='Cuoci 18 minuti', saveMode='Entrambe' WHERE id=7")
assert db.execute('SELECT COUNT(*) FROM recipes').fetchone()[0] == 1
fresh = sqlite3.connect(':memory:')
fresh.execute('CREATE TABLE recipes (' + schema + ')')
assert [r[1] for r in fresh.execute('PRAGMA table_info(recipes)')] == [r[1] for r in db.execute('PRAGMA table_info(recipes)')]
print('PASS: populated v1 migration preserves recipe, quantities, calendar and photo; fresh schema matches.')
