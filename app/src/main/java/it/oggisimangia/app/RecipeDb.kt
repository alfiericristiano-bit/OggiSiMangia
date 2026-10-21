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
    val saveMode: String = "Semplificata",
    val prepTime: String = "",
    val cookTime: String = "",
    val totalTime: String = "",
    val restTime: String = "",
    val portions: String = "",
    val sourceImageUrl: String = "",
    val nutrition: String = "",
    val description: String = "",
    val cuisine: String = "",
    val equipment: String = "",
    val tips: String = "",
    val notes: String = "",
    val storage: String = "",
    val variations: String = "",
    val allergens: String = "",
    val tags: String = "",
    val temperature: String = "",
    val author: String = "",
    val cost: String = "",
    val isPersonal: Boolean = false
)

class RecipeDb(context: Context) : SQLiteOpenHelper(context, "oggisimangia.db", null, 4) {
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
                saveMode TEXT NOT NULL DEFAULT 'Semplificata',
                prepTime TEXT NOT NULL DEFAULT '',
                cookTime TEXT NOT NULL DEFAULT '',
                totalTime TEXT NOT NULL DEFAULT '',
                restTime TEXT NOT NULL DEFAULT '',
                portions TEXT NOT NULL DEFAULT '',
                sourceImageUrl TEXT NOT NULL DEFAULT '',
                nutrition TEXT NOT NULL DEFAULT '',
                description TEXT NOT NULL DEFAULT '',
                cuisine TEXT NOT NULL DEFAULT '',
                equipment TEXT NOT NULL DEFAULT '',
                tips TEXT NOT NULL DEFAULT '',
                notes TEXT NOT NULL DEFAULT '',
                storage TEXT NOT NULL DEFAULT '',
                variations TEXT NOT NULL DEFAULT '',
                allergens TEXT NOT NULL DEFAULT '',
                tags TEXT NOT NULL DEFAULT '',
                temperature TEXT NOT NULL DEFAULT '',
                author TEXT NOT NULL DEFAULT '',
                cost TEXT NOT NULL DEFAULT '',
                isPersonal INTEGER NOT NULL DEFAULT 0
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
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE recipes ADD COLUMN prepTime TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE recipes ADD COLUMN cookTime TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE recipes ADD COLUMN totalTime TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE recipes ADD COLUMN restTime TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE recipes ADD COLUMN portions TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE recipes ADD COLUMN sourceImageUrl TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE recipes ADD COLUMN nutrition TEXT NOT NULL DEFAULT ''")
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE recipes ADD COLUMN description TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE recipes ADD COLUMN cuisine TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE recipes ADD COLUMN equipment TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE recipes ADD COLUMN tips TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE recipes ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE recipes ADD COLUMN storage TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE recipes ADD COLUMN variations TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE recipes ADD COLUMN allergens TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE recipes ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE recipes ADD COLUMN temperature TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE recipes ADD COLUMN author TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE recipes ADD COLUMN cost TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE recipes ADD COLUMN isPersonal INTEGER NOT NULL DEFAULT 0")
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
        saveMode = c.getString(c.getColumnIndexOrThrow("saveMode")),
        prepTime = c.getString(c.getColumnIndexOrThrow("prepTime")),
        cookTime = c.getString(c.getColumnIndexOrThrow("cookTime")),
        totalTime = c.getString(c.getColumnIndexOrThrow("totalTime")),
        restTime = c.getString(c.getColumnIndexOrThrow("restTime")),
        portions = c.getString(c.getColumnIndexOrThrow("portions")),
        sourceImageUrl = c.getString(c.getColumnIndexOrThrow("sourceImageUrl")),
        nutrition = c.getString(c.getColumnIndexOrThrow("nutrition")),
        description = c.getString(c.getColumnIndexOrThrow("description")),
        cuisine = c.getString(c.getColumnIndexOrThrow("cuisine")),
        equipment = c.getString(c.getColumnIndexOrThrow("equipment")),
        tips = c.getString(c.getColumnIndexOrThrow("tips")),
        notes = c.getString(c.getColumnIndexOrThrow("notes")),
        storage = c.getString(c.getColumnIndexOrThrow("storage")),
        variations = c.getString(c.getColumnIndexOrThrow("variations")),
        allergens = c.getString(c.getColumnIndexOrThrow("allergens")),
        tags = c.getString(c.getColumnIndexOrThrow("tags")),
        temperature = c.getString(c.getColumnIndexOrThrow("temperature")),
        author = c.getString(c.getColumnIndexOrThrow("author")),
        cost = c.getString(c.getColumnIndexOrThrow("cost")),
        isPersonal = c.getInt(c.getColumnIndexOrThrow("isPersonal")) == 1
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
            put("prepTime", r.prepTime)
            put("cookTime", r.cookTime)
            put("totalTime", r.totalTime)
            put("restTime", r.restTime)
            put("portions", r.portions)
            put("sourceImageUrl", r.sourceImageUrl)
            put("nutrition", r.nutrition)
            put("description", r.description)
            put("cuisine", r.cuisine)
            put("equipment", r.equipment)
            put("tips", r.tips)
            put("notes", r.notes)
            put("storage", r.storage)
            put("variations", r.variations)
            put("allergens", r.allergens)
            put("tags", r.tags)
            put("temperature", r.temperature)
            put("author", r.author)
            put("cost", r.cost)
            put("isPersonal", if (r.isPersonal) 1 else 0)
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

    fun monthMarkers(month: java.time.YearMonth): Map<String, Set<String>> =
        readableDatabase.query("recipes", arrayOf("day", "meal"), "day>=? AND day<=?",
            arrayOf(month.atDay(1).toString(), month.atEndOfMonth().toString()), null, null, null).use { c ->
            val result = linkedMapOf<String, MutableSet<String>>()
            while (c.moveToNext()) {
                val meals = result.getOrPut(c.getString(0)) { linkedSetOf() }
                if (c.getString(1) == "Entrambi") meals.addAll(listOf("Pranzo", "Cena")) else meals.add(c.getString(1))
            }
            result
        }

    fun archive(search: String, category: String, difficulty: String, order: String, ingredientQuery: String = ""): List<Recipe> {
        val all = readableDatabase.query("recipes", null, null, null, null, null, null).use { c ->
            buildList { while (c.moveToNext()) add(parse(c)) }
        }
        val filtered = all.filter {
            (category == "Tutte" || it.category == category) &&
            IngredientFilter.matches(it.ingredients, ingredientQuery) &&
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


