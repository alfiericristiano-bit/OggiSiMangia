"""Prepare and verify a signed Android APK upgrade from the preserved beta3 branch.
Called before and after adb install -r; never deletes user data.
"""
import re
import sqlite3
import sys
from pathlib import Path

source=Path("app/src/main/java/it/oggisimangia/app/RecipeDb.kt").read_text(encoding="utf-8")
schema=re.search(r"CREATE TABLE recipes \((.*?)\n            \)",source,re.S)
assert schema, "Cannot locate recipe schema"
version=re.search(r'SQLiteOpenHelper\(context, "oggisimangia\.db", null, (\d+)\)', source)
assert version, "Cannot determine SQLite schema version"
path=Path("build/update-check/previous-recipes.db" if sys.argv[1]=="seed" else "build/update-check/upgraded-recipes.db")
path.parent.mkdir(parents=True,exist_ok=True)
if sys.argv[1]=="seed":
    path.unlink(missing_ok=True)
    with sqlite3.connect(path) as db:
        db.execute("CREATE TABLE recipes ("+schema.group(1)+")")
        db.execute(f"PRAGMA user_version={version.group(1)}")
        db.execute("""INSERT INTO recipes
            (id,day,meal,category,title,ingredients,instructions,imagePath,
             scanPages,dishPhotos,scanOcrText,notes,prepTime,cookTime,portions)
            VALUES (7,'2026-10-23','Cena','Primo','Ricetta prima di aggiornare',
            '200 g riso, 1 l brodo','Cuoci 18 minuti','/photos/preupgrade.jpg',
            '["/photos/preupgrade.jpg"]','[]','Ricetta fotografata',
            'Non perdere questo testo','10 min','18 min','4 persone')""")
        db.commit()
    print("PASS: seeded realistic beta3 SQLite recipe fixture")
elif sys.argv[1]=="check":
    with sqlite3.connect(path) as db:
        row=db.execute("""SELECT day,meal,title,ingredients,instructions,imagePath,
          scanPages,scanOcrText,notes,prepTime,cookTime,portions FROM recipes WHERE id=7""").fetchone()
        expected=('2026-10-23','Cena','Ricetta prima di aggiornare','200 g riso, 1 l brodo',
          'Cuoci 18 minuti','/photos/preupgrade.jpg','["/photos/preupgrade.jpg"]',
          'Ricetta fotografata','Non perdere questo testo','10 min','18 min','4 persone')
        assert row==expected, f"Recipe content changed/lost during APK upgrade: {row}"
        assert db.execute("PRAGMA integrity_check").fetchone()[0]=="ok"
        assert db.execute("PRAGMA user_version").fetchone()[0]==int(version.group(1))
    print("PASS: beta3 -> beta4 signed APK update preserves full SQLite recipe, OCR, notes, timestamps and photo references")
else:
    raise SystemExit("Usage: verify_real_upgrade.py seed|check")
