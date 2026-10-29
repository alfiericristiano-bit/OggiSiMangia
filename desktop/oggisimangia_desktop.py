"""OggiSiMangia Desktop — Windows 11. Python 3.11+, standard library only.
Local SQLite, calendar, recipe archive, sources, web search links, JSON backup.
This is a separate desktop implementation; Android database is not modified.
"""
import datetime as dt
import json
import os
import sqlite3
import sys
import tkinter as tk
import urllib.parse
from pathlib import Path
from tkinter import filedialog, messagebox, ttk

APP = "OggiSiMangia"
DATA_DIR = Path(os.getenv("LOCALAPPDATA", str(Path.home()))) / APP
DB = DATA_DIR / "desktop.sqlite3"
DEFAULT_SOURCES = [
    ("GialloZafferano", "giallozafferano.it"),
    ("Misya", "misya.info"),
    ("Cucchiaio d'Argento", "cucchiaio.it"),
    ("Fatto in Casa da Benedetta", "fattoincasadabenedetta.it"),
    ("Sale&Pepe", "salepepe.it"),
    ("ButtaLaPasta", "buttalapasta.it"),
]


def connect():
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    db = sqlite3.connect(DB)
    db.execute("PRAGMA foreign_keys=ON")
    db.executescript("""
    CREATE TABLE IF NOT EXISTS recipes(
      id INTEGER PRIMARY KEY, title TEXT NOT NULL, category TEXT NOT NULL DEFAULT '',
      ingredients TEXT NOT NULL DEFAULT '', instructions TEXT NOT NULL DEFAULT '',
      url TEXT NOT NULL DEFAULT '', notes TEXT NOT NULL DEFAULT '',
      photo TEXT NOT NULL DEFAULT '', created TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
    );
    CREATE TABLE IF NOT EXISTS meals(
      day TEXT NOT NULL, meal TEXT NOT NULL, slot TEXT NOT NULL,
      recipe_id INTEGER NOT NULL REFERENCES recipes(id) ON DELETE CASCADE,
      PRIMARY KEY(day,meal,slot)
    );
    CREATE TABLE IF NOT EXISTS sources(
      name TEXT PRIMARY KEY, domain TEXT NOT NULL, enabled INTEGER NOT NULL DEFAULT 1
    );
    """)
    if not db.execute("SELECT 1 FROM sources LIMIT 1").fetchone():
        db.executemany("INSERT INTO sources(name,domain) VALUES (?,?)", DEFAULT_SOURCES)
    db.commit()
    return db


class Desktop(tk.Tk):
    def __init__(self):
        super().__init__()
        self.db = connect()
        self.title("OggiSiMangia... — Desktop")
        self.geometry("1120x740")
        self.minsize(850, 570)
        self.day = tk.StringVar(value=dt.date.today().isoformat())
        self.meal = tk.StringVar(value="Pranzo")
        self.slot = tk.StringVar(value="Principale")
        self.search = tk.StringVar()
        self.status = tk.StringVar(value="Dati salvati localmente · " + str(DB))
        self.selected_id = None
        self.build()
        self.refresh()

    def build(self):
        bar = ttk.Frame(self, padding=12)
        bar.pack(fill="x")
        ttk.Label(bar, text="OggiSiMangia...", font=("Segoe UI", 21, "bold")).pack(side="left")
        for label, command in [
            ("Esporta backup", self.export), ("Importa backup", self.import_backup),
            ("Fonti", self.sources), ("Cerca online", self.search_online)
        ]:
            ttk.Button(bar, text=label, command=command).pack(side="right", padx=4)
        pane = ttk.Panedwindow(self, orient="horizontal")
        pane.pack(fill="both", expand=True, padx=12, pady=6)
        left = ttk.Frame(pane, padding=8)
        right = ttk.Frame(pane, padding=8)
        pane.add(left, weight=2)
        pane.add(right, weight=3)

        ttk.Label(left, text="Calendario pasti", font=("Segoe UI", 15, "bold")).pack(anchor="w")
        dates = ttk.Frame(left)
        dates.pack(fill="x", pady=7)
        ttk.Button(dates, text="◀", width=3, command=lambda: self.shift_day(-1)).pack(side="left")
        ttk.Entry(dates, textvariable=self.day, width=13).pack(side="left", padx=4)
        ttk.Button(dates, text="▶", width=3, command=lambda: self.shift_day(1)).pack(side="left")
        ttk.Button(dates, text="Vai", command=self.refresh).pack(side="left", padx=4)
        self.meals = ttk.Treeview(left, columns=("meal", "slot", "title"), show="headings", height=9)
        for key, label, width in [("meal", "Pasto", 75), ("slot", "Scelta", 95), ("title", "Ricetta", 190)]:
            self.meals.heading(key, text=label)
            self.meals.column(key, width=width)
        self.meals.pack(fill="x", pady=7)
        controls = ttk.Frame(left)
        controls.pack(fill="x")
        ttk.Combobox(controls, textvariable=self.meal, values=["Pranzo", "Cena"], state="readonly", width=10).pack(side="left")
        ttk.Combobox(controls, textvariable=self.slot, values=["Principale", "Alternativa"], state="readonly", width=12).pack(side="left", padx=4)
        ttk.Button(controls, text="Pianifica ricetta", command=self.assign).pack(side="left", padx=4)
        ttk.Button(left, text="Rimuovi pasto selezionato", command=self.remove_meal).pack(anchor="w", pady=8)
        ttk.Label(left, text="La data si inserisce nel formato AAAA-MM-GG.", foreground="#555").pack(anchor="w")

        ttk.Label(right, text="Ricettario", font=("Segoe UI", 15, "bold")).pack(anchor="w")
        searchrow = ttk.Frame(right)
        searchrow.pack(fill="x", pady=7)
        ttk.Entry(searchrow, textvariable=self.search).pack(side="left", fill="x", expand=True)
        ttk.Button(searchrow, text="Filtra", command=self.refresh).pack(side="left", padx=4)
        ttk.Button(searchrow, text="Nuova ricetta", command=lambda: self.edit(None)).pack(side="left")
        self.recipes = ttk.Treeview(right, columns=("id", "title", "category"), show="headings")
        for key, label, width in [("id", "ID", 45), ("title", "Ricetta", 320), ("category", "Categoria", 125)]:
            self.recipes.heading(key, text=label)
            self.recipes.column(key, width=width, stretch=(key == "title"))
        self.recipes.pack(fill="both", expand=True)
        self.recipes.bind("<Double-1>", lambda _: self.edit(self.recipe_id()))
        actions = ttk.Frame(right)
        actions.pack(fill="x", pady=8)
        ttk.Button(actions, text="Apri / Modifica", command=lambda: self.edit(self.recipe_id())).pack(side="left")
        ttk.Button(actions, text="Elimina", command=self.delete_recipe).pack(side="left", padx=5)
        ttk.Button(actions, text="Apri link originale", command=self.open_recipe_url).pack(side="left")
        ttk.Label(self, textvariable=self.status, padding=7).pack(fill="x")

    def valid_day(self):
        try:
            return dt.date.fromisoformat(self.day.get()).isoformat()
        except ValueError:
            messagebox.showerror("Data", "Inserisci una data valida: AAAA-MM-GG")
            return None

    def shift_day(self, days):
        date = self.valid_day()
        if date:
            self.day.set((dt.date.fromisoformat(date) + dt.timedelta(days=days)).isoformat())
            self.refresh()

    def recipe_id(self):
        selected = self.recipes.selection()
        return int(selected[0]) if selected else None

    def refresh(self):
        for item in self.recipes.get_children():
            self.recipes.delete(item)
        term = "%" + self.search.get().strip() + "%"
        rows = self.db.execute("SELECT id,title,category FROM recipes WHERE title LIKE ? OR ingredients LIKE ? ORDER BY title", (term, term))
        for row in rows:
            self.recipes.insert("", "end", iid=str(row[0]), values=row)
        for item in self.meals.get_children():
            self.meals.delete(item)
        day = self.valid_day()
        if day:
            rows = self.db.execute("""SELECT m.meal,m.slot,r.title FROM meals m JOIN recipes r ON r.id=m.recipe_id
                WHERE m.day=? ORDER BY m.meal,m.slot""", (day,))
            for row in rows:
                self.meals.insert("", "end", values=row)

    def edit(self, recipe_id):
        row = self.db.execute("SELECT title,category,ingredients,instructions,url,notes,photo FROM recipes WHERE id=?", (recipe_id,)).fetchone() if recipe_id else None
        window = tk.Toplevel(self)
        window.title("Ricetta" if recipe_id else "Nuova ricetta")
        window.geometry("690x660")
        window.transient(self)
        form = ttk.Frame(window, padding=14)
        form.pack(fill="both", expand=True)
        fields = {}
        names = ["Titolo", "Categoria", "Ingredienti", "Preparazione", "Link originale", "Note", "Percorso foto"]
        for i, name in enumerate(names):
            ttk.Label(form, text=name).pack(anchor="w")
            if name in ("Ingredienti", "Preparazione", "Note"):
                widget = tk.Text(form, height=4 if name == "Preparazione" else 3, wrap="word")
                widget.insert("1.0", row[i] if row else "")
            else:
                widget = ttk.Entry(form)
                widget.insert(0, row[i] if row else "")
            widget.pack(fill="x", pady=(0, 6))
            fields[name] = widget

        def value(name):
            w = fields[name]
            return w.get("1.0", "end-1c").strip() if isinstance(w, tk.Text) else w.get().strip()

        def save():
            values = [value(n) for n in names]
            if not values[0]:
                messagebox.showerror("Ricetta", "Inserisci il titolo.", parent=window)
                return
            if recipe_id:
                self.db.execute("""UPDATE recipes SET title=?,category=?,ingredients=?,instructions=?,
                    url=?,notes=?,photo=? WHERE id=?""", (*values, recipe_id))
            else:
                self.db.execute("""INSERT INTO recipes(title,category,ingredients,instructions,url,notes,photo)
                    VALUES (?,?,?,?,?,?,?)""", values)
            self.db.commit()
            window.destroy()
            self.refresh()

        ttk.Button(form, text="Salva ricetta", command=save).pack(anchor="e", pady=5)

    def assign(self):
        day, rid = self.valid_day(), self.recipe_id()
        if not day or not rid:
            messagebox.showinfo("Pianificazione", "Seleziona prima una ricetta nel ricettario.")
            return
        self.db.execute("INSERT OR REPLACE INTO meals(day,meal,slot,recipe_id) VALUES(?,?,?,?)",
                        (day, self.meal.get(), self.slot.get(), rid))
        self.db.commit()
        self.refresh()

    def remove_meal(self):
        selection = self.meals.selection()
        day = self.valid_day()
        if not selection or not day:
            return
        meal, slot, _ = self.meals.item(selection[0], "values")
        if messagebox.askyesno("Rimuovi pasto", "Rimuovere questa pianificazione? La ricetta rimane salvata."):
            self.db.execute("DELETE FROM meals WHERE day=? AND meal=? AND slot=?", (day, meal, slot))
            self.db.commit()
            self.refresh()

    def delete_recipe(self):
        rid = self.recipe_id()
        if rid and messagebox.askyesno("Elimina ricetta", "Eliminare la ricetta e le sue pianificazioni?"):
            self.db.execute("DELETE FROM recipes WHERE id=?", (rid,))
            self.db.commit()
            self.refresh()

    def open_recipe_url(self):
        rid = self.recipe_id()
        if rid:
            row = self.db.execute("SELECT url FROM recipes WHERE id=?", (rid,)).fetchone()
            if row and row[0].startswith(("https://", "http://")):
                import webbrowser
                webbrowser.open(row[0])

    def search_online(self):
        import webbrowser
        popup = tk.Toplevel(self)
        popup.title("Cerca ricette online")
        popup.geometry("480x330")
        ttk.Label(popup, text="Nome del piatto o ingredienti").pack(pady=8)
        term = ttk.Entry(popup, width=52)
        term.insert(0, self.search.get())
        term.pack(pady=4)
        excluded = ttk.Entry(popup, width=52)
        ttk.Label(popup, text="Ingredienti da evitare (opzionali, da verificare sulla fonte)").pack()
        excluded.pack(pady=4)
        ttk.Label(popup, text="Apre ricerche sul browser: nessuna verifica automatica degli ingredienti.", wraplength=440).pack(pady=8)

        def go():
            query = term.get().strip()
            if not query:
                return
            sites = self.db.execute("SELECT domain FROM sources WHERE enabled=1 ORDER BY name").fetchall()
            if not sites:
                messagebox.showwarning("Fonti", "Nessuna fonte attiva.")
                return
            for (domain,) in sites:
                text = f"site:{domain} ricetta {query}"
                for ingredient in excluded.get().split(","):
                    if ingredient.strip():
                        text += " -" + ingredient.strip()
                webbrowser.open("https://www.google.com/search?q=" + urllib.parse.quote(text))
            popup.destroy()

        ttk.Button(popup, text="Cerca nelle fonti attive", command=go).pack(pady=8)

    def sources(self):
        win = tk.Toplevel(self)
        win.title("Fonti configurate")
        win.geometry("550x440")
        rows = ttk.Frame(win, padding=12)
        rows.pack(fill="both", expand=True)
        for name, domain, enabled in self.db.execute("SELECT name,domain,enabled FROM sources ORDER BY name").fetchall():
            state = tk.BooleanVar(value=bool(enabled))
            def update(n=name, v=state):
                self.db.execute("UPDATE sources SET enabled=? WHERE name=?", (int(v.get()), n))
                self.db.commit()
            ttk.Checkbutton(rows, text=f"{name} — {domain}", variable=state, command=update).pack(anchor="w", pady=3)
        ttk.Label(rows, text="Le ricerche aprono il browser; non effettuano scraping dei siti.", wraplength=500).pack(anchor="w", pady=10)

    def export(self):
        path = filedialog.asksaveasfilename(title="Esporta backup desktop", defaultextension=".json",
            initialfile="OggiSiMangia-desktop-backup.json", filetypes=[("JSON", "*.json")])
        if not path:
            return
        data = {"format": "oggisimangia-desktop-v1", "exported": dt.datetime.now().isoformat(),
                "recipes": [dict(zip(("id","title","category","ingredients","instructions","url","notes","photo","created"), r))
                            for r in self.db.execute("SELECT id,title,category,ingredients,instructions,url,notes,photo,created FROM recipes")],
                "meals": [dict(zip(("day","meal","slot","recipe_id"), r))
                          for r in self.db.execute("SELECT day,meal,slot,recipe_id FROM meals")],
                "sources": [dict(zip(("name","domain","enabled"), r))
                            for r in self.db.execute("SELECT name,domain,enabled FROM sources")]}
        Path(path).write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
        messagebox.showinfo("Backup", "Backup desktop salvato. Le foto esterne non sono incorporate nel JSON.")

    def import_backup(self):
        path = filedialog.askopenfilename(title="Importa backup desktop", filetypes=[("JSON", "*.json")])
        if not path:
            return
        try:
            data = json.loads(Path(path).read_text(encoding="utf-8"))
            if data.get("format") != "oggisimangia-desktop-v1":
                raise ValueError("Formato non supportato. Il backup Android non è ancora compatibile.")
            recipes, meals, sources = data["recipes"], data["meals"], data["sources"]
            if not isinstance(recipes, list) or not isinstance(meals, list) or not isinstance(sources, list):
                raise ValueError("Backup non valido")
            if not messagebox.askyesno("Importa", "Sostituire tutti i dati desktop con quelli del backup? Prima esportane una copia."):
                return
            with self.db:
                self.db.execute("DELETE FROM meals")
                self.db.execute("DELETE FROM recipes")
                self.db.execute("DELETE FROM sources")
                for x in recipes:
                    self.db.execute("""INSERT INTO recipes(id,title,category,ingredients,instructions,url,notes,photo,created)
                        VALUES (?,?,?,?,?,?,?,?,?)""", tuple(x[k] for k in ("id","title","category","ingredients","instructions","url","notes","photo","created")))
                for x in meals:
                    self.db.execute("INSERT INTO meals(day,meal,slot,recipe_id) VALUES(?,?,?,?)",
                                    tuple(x[k] for k in ("day","meal","slot","recipe_id")))
                for x in sources:
                    self.db.execute("INSERT INTO sources(name,domain,enabled) VALUES(?,?,?)",
                                    tuple(x[k] for k in ("name","domain","enabled")))
            self.refresh()
        except (OSError, ValueError, KeyError, TypeError, sqlite3.Error) as exc:
            messagebox.showerror("Importazione", str(exc))


if __name__ == "__main__":
    Desktop().mainloop()
