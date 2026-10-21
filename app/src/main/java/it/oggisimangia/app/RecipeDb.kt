package it.oggisimangia.app

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Recipe(
    val id: Long = 0,
    val day: String,
    val meal: String,
    val category: String,
    val title: String,
    val ingredients: String,
    val instructions: String,
    val difficulty: String,
    val duration: String,
    val url: String,
    val alternative: Boolean,
    val imagePath: String,
    val originalInstructions: String = "",
    val saveMode: String = "Semplificata"
)

class RecipeDb(context: Context) : SQLiteOpenHelper(context, "oggisimangia.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE recipes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                day TEXT NOT NULL,
                meal TEXT NOT NULL,
                category TEXT NOT NULL,
                title TEXT NOT NULL,
                ingredients TEXT NOT NULL DEFAULT '',
                instructions TEXT NOT NULL DEFAULT '',
                difficulty TEXT NOT NULL DEFAULT 'Facile',
                duration TEXT NOT NULL DEFAULT 'Veloce',
                url TEXT NOT NULL DEFAULT '',
                alternative INTEGER NOT NULL DEFAULT 0,
                imagePath TEXT NOT NULL DEFAULT '',
                originalInstructions TEXT NOT NULL DEFAULT '',
                saveMode TEXT NOT NULL DEFAULT 'Semplificata'
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX by_day ON recipes(day)")
        db.execSQL("CREATE INDEX by_title ON recipes(title)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE recipes ADD COLUMN originalInstructions TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE recipes ADD COLUMN saveMode TEXT NOT NULL DEFAULT 'Semplificata'")
        }
    }

    private fun parse(c: Cursor): Recipe = Recipe(
        id = c.getLong(c.getColumnIndexOrThrow("id")),
        day = c.getString(c.getColumnIndexOrThrow("day")),
        meal = c.getString(c.getColumnIndexOrThrow("meal")),
        category = c.getString(c.getColumnIndexOrThrow("category")),
        title = c.getString(c.getColumnIndexOrThrow("title")),
        ingredients = c.getString(c.getColumnIndexOrThrow("ingredients")),
        instructions = c.getString(c.getColumnIndexOrThrow("instructions")),
        difficulty = c.getString(c.getColumnIndexOrThrow("difficulty")),
        duration = c.getString(c.getColumnIndexOrThrow("duration")),
        url = c.getString(c.getColumnIndexOrThrow("url")),
        alternative = c.getInt(c.getColumnIndexOrThrow("alternative")) == 1,
        imagePath = c.getString(c.getColumnIndexOrThrow("imagePath")),
        originalInstructions = c.getString(c.getColumnIndexOrThrow("originalInstructions")),
        saveMode = c.getString(c.getColumnIndexOrThrow("saveMode"))
    )

    fun save(r: Recipe): Long {
        val v = ContentValues().apply {
            put("day", r.day)
            put("meal", r.meal)
            put("category", r.category)
            put("title", r.title)
            put("ingredients", r.ingredients)
            put("instructions", r.instructions)
            put("difficulty", r.difficulty)
            put("duration", r.duration)
            put("url", r.url)
            put("alternative", if (r.alternative) 1 else 0)
            put("imagePath", r.imagePath)
            put("originalInstructions", r.originalInstructions)
            put("saveMode", r.saveMode)
        }
        return if (r.id == 0L) writableDatabase.insertOrThrow("recipes", null, v)
        else {
            writableDatabase.update("recipes", v, "id=?", arrayOf(r.id.toString()))
            r.id
        }
    }

    fun get(id: Long): Recipe? =
        readableDatabase.query("recipes", null, "id=?", arrayOf(id.toString()), null, null, null).use {
            if (it.moveToFirst()) parse(it) else null
        }

    fun onDay(day: String): List<Recipe> =
        readableDatabase.query("recipes", null, "day=?", arrayOf(day), null, null,
            "meal ASC, alternative ASC, category ASC").use { c ->
            buildList { while (c.moveToNext()) add(parse(c)) }
        }

    fun archive(search: String, category: String, difficulty: String, order: String): List<Recipe> {
        val all = readableDatabase.query("recipes", null, null, null, null, null, null).use { c ->
            buildList { while (c.moveToNext()) add(parse(c)) }
        }
        val filtered = all.filter {
            (category == "Tutte" || it.category == category) &&
            (difficulty == "Tutte" || it.difficulty == difficulty) &&
            (search.isBlank() || (it.title + " " + it.ingredients).contains(search, ignoreCase = true))
        }
        return when (order) {
            "Data (recenti)" -> filtered.sortedWith(compareByDescending<Recipe> { it.day }.thenByDescending { it.id })
            "Difficoltà" -> filtered.sortedWith(compareBy<Recipe> { listOf("Facile", "Media", "Difficile").indexOf(it.difficulty) }.thenBy { it.title.lowercase() })
            else -> filtered.sortedBy { it.title.lowercase() }
        }
    }

    fun remove(id: Long): Boolean =
        writableDatabase.delete("recipes", "id=?", arrayOf(id.toString())) > 0
}

