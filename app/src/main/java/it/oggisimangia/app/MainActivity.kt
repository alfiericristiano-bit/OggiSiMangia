package it.oggisimangia.app

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.graphics.Rect
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.View
import android.widget.*
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import java.net.HttpURLConnection
import java.net.URL
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

class MainActivity : Activity() {
    private lateinit var db: RecipeDb
    private lateinit var sourcePreferences: SourcePreferences
    private var pendingReport: File? = null
    private lateinit var content: LinearLayout
    private var selectedDate = LocalDate.now()
    private var selectedMeal = "Pranzo"
    private var selectedAlternative = false
    private var searchForDay = false
    private var selectedCategory = "Primo"
    private var searchIngredients = ""
    private var searchDifficulty = "Facile"
    private var searchDuration = "Veloce"
    private var searchCategory = "Primo"
    private var selectedPhoto = ""
    private var photoPreview: ImageView? = null
    private var pendingCameraPath: String? = null
    private var lastSearchResults: List<WebRecipe> = emptyList()
    private var lastSearchOffset = 0
    private var photoLabel: TextView? = null
    private val expandedMeals = mutableSetOf<String>()
    private var currentPageKey = ""
    private var currentPageRestore: (() -> Unit)? = null
    private val pageHistory = ArrayDeque<() -> Unit>()
    private var goingBack = false
    private val dateFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", java.util.Locale.ITALIAN)

    companion object {
        private const val PHOTO_REQUEST = 401
        private const val CAMERA_REQUEST = 403
        private const val PDF_REQUEST = 402
        private val categories = arrayOf("Antipasto", "Primo", "Secondo", "Contorno", "Piatto unico", "Dolce")
        private val difficulties = arrayOf("Facile", "Media", "Difficile", "Non disponibile")
        private val durations = arrayOf("Volante", "Veloce", "Media", "Lunga")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingCameraPath = savedInstanceState?.getString("pending_camera_path")
        db = RecipeDb(this)
        sourcePreferences = SourcePreferences(this)
        RecipeSources.configure(sourcePreferences.load())
        window.statusBarColor = Color.rgb(15, 98, 86)
        window.navigationBarColor = Color.rgb(15, 98, 86)
        showCalendar()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("pending_camera_path", pendingCameraPath)
        super.onSaveInstanceState(outState)
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun hideKeyboard(view: View? = currentFocus) {
        val focus = view ?: return
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(focus.windowToken, 0)
        if (focus is EditText) focus.clearFocus()
    }

    /** Dismiss the keyboard whenever tapping outside the current entry field. */
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            val focused = currentFocus
            if (focused is EditText) {
                val bounds = Rect()
                focused.getGlobalVisibleRect(bounds)
                if (!bounds.contains(ev.rawX.toInt(), ev.rawY.toInt())) hideKeyboard(focused)
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    /** FamilyChat visual tokens, used only by the UI; stored recipe data remains unchanged. */
    private val familyTeal = Color.rgb(20, 125, 114)
    private val familyDeep = Color.rgb(21, 103, 91)
    private val familyInk = Color.rgb(35, 61, 57)
    private val familyPale = Color.rgb(234, 244, 241)
    private fun shape(color: Int, radius: Float = 16f): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(radius.toInt()).toFloat() }
    private fun outlined(color: Int = Color.WHITE, radius: Float = 12f): GradientDrawable =
        shape(color, radius).apply { setStroke(dp(1), Color.rgb(199, 216, 210)) }

    private fun screen(title: String, subtitle: String, key: String, restore: () -> Unit) {
        if (!goingBack && currentPageKey.isNotEmpty() && currentPageKey != key) {
            currentPageRestore?.let { pageHistory.addLast(it) }
            if (pageHistory.size > 16) pageHistory.removeFirst()
        }
        currentPageKey = key
        currentPageRestore = restore
        val background = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(244, 247, 247))
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(22))
        }
        scroll.addView(content)
        background.addView(scroll)
        setContentView(background)
        // FamilyChat-inspired teal brand header with rounded corners and native controls.
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(13), dp(10), dp(13), dp(11))
            this.background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(familyTeal, Color.rgb(32, 165, 140))).apply {
                cornerRadius = dp(22).toFloat()
            }
            elevation = dp(3).toFloat()
        }
        hero.addView(TextView(this).apply {
            text = "🍝"; textSize = 26f; gravity = Gravity.CENTER
            this.background = shape(Color.argb(45, 255, 255, 255), 15f)
        }, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(8) })
        val headerText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        headerText.addView(TextView(this).apply {
            text = "OggiSiMangia..."; textSize = 21f
            setTextColor(Color.WHITE); setTypeface(null, Typeface.BOLD)
        })
        headerText.addView(TextView(this).apply {
            text = title; textSize = 15f; setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
        })
        headerText.addView(TextView(this).apply {
            text = subtitle; textSize = 11f
            setTextColor(Color.rgb(235, 251, 244)); maxLines = 3
        })
        hero.addView(headerText, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
        if (key != "calendar") {
            val back = Button(this).apply {
                text = "‹  Indietro"
                textSize = 14f; isAllCaps = false; gravity = Gravity.CENTER_VERTICAL
                setTextColor(familyDeep)
                background = outlined(Color.WHITE, 16f)
                setOnClickListener { goBack() }
            }
            content.addView(back, LinearLayout.LayoutParams(-1, dp(44)).apply { bottomMargin = dp(6) })
        }
        navigation(title)
    }

    private fun goBack() {
        hideKeyboard()
        val previous = if (pageHistory.isEmpty()) null else pageHistory.removeLast()
        goingBack = true
        try {
            if (previous != null) previous() else showCalendar()
        } finally { goingBack = false }
    }

    @Deprecated("Android back navigation")
    override fun onBackPressed() { goBack() }

    private fun label(value: String, size: Float = 16f, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value; textSize = size
            setTextColor(familyInk)
            if (bold) setTypeface(null, Typeface.BOLD)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            content.addView(this)
        }

    private fun action(value: String, onClick: () -> Unit) {
        val btn = Button(this).apply {
            text = value; isAllCaps = false; textSize = 14f
            setTextColor(familyDeep)
            background = outlined(familyPale, 17f)
            elevation = dp(1).toFloat()
            setOnClickListener { hideKeyboard(); onClick() }
        }
        content.addView(btn, LinearLayout.LayoutParams(-1, dp(44)).apply {
            bottomMargin = dp(5)
        })
    }

    private fun primaryAction(value: String, onClick: () -> Unit) {
        val btn = Button(this).apply {
            text = value; isAllCaps = false; textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(familyDeep, Color.rgb(20, 150, 126))).apply {
                cornerRadius = dp(19).toFloat()
            }
            elevation = dp(4).toFloat()
            setOnClickListener { hideKeyboard(); onClick() }
        }
        content.addView(btn, LinearLayout.LayoutParams(-1, dp(54)).apply {
            topMargin = dp(7); bottomMargin = dp(9)
        })
    }

    /** Two related actions on one row; 48 dp touch targets remain accessible. */
    private fun actionRow(first: String, firstClick: () -> Unit,
                          second: String, secondClick: () -> Unit) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(first to firstClick, second to secondClick).forEachIndexed { index, entry ->
            val button = Button(this).apply {
                text = entry.first; isAllCaps = false; textSize = 13f
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(familyDeep)
                background = outlined(familyPale, 17f)
                setOnClickListener { hideKeyboard(); entry.second() }
            }
            row.addView(button, LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                if (index == 0) marginEnd = dp(6)
            })
        }
        content.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(6)
        })
    }

    private fun input(hint: String, initial: String = "", multi: Boolean = false): EditText =
        EditText(this).apply {
            this.hint = hint
            setText(initial)
            textSize = 16f
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = outlined(Color.WHITE, 15f)
            setTextColor(familyInk)
            setHintTextColor(Color.rgb(105,129,120))
            if (multi) {
                minLines = 3
                gravity = Gravity.TOP
                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            } else {
                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                setSingleLine(true)
                imeOptions = EditorInfo.IME_ACTION_DONE
                setOnEditorActionListener { view, actionId, event ->
                    if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_NEXT ||
                        (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP)) {
                        hideKeyboard(view)
                        true
                    } else false
                }
            }
            content.addView(this, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(7)
            })
        }

    private fun dropdown(caption: String, choices: Array<String>, value: String): Spinner {
        label(caption, 14f, true)
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, choices)
        spinner.setSelection(choices.indexOf(value).coerceAtLeast(0))
        spinner.background = outlined(Color.WHITE, 15f)
        spinner.setPadding(dp(9), 0, dp(9), 0)
        content.addView(spinner, LinearLayout.LayoutParams(-1, dp(44)).apply { bottomMargin = dp(5) })
        return spinner
    }

    private fun navigation(title: String) {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = outlined(Color.WHITE, 15f)
        }
        val active = when {
            title.contains("calendario", true) -> 0
            title.contains("trova", true) || title.contains("ricerca", true) -> 1
            title.contains("ricettario", true) -> 2
            else -> -1
        }
        listOf("📅 Calendario", "🔎 Cerca", "📚 Ricettario").forEachIndexed { index, name ->
            val b = Button(this).apply {
                text = name; isAllCaps = false; textSize = 11f
                setPadding(dp(2), 0, dp(2), 0)
                background = shape(if (active == index) Color.rgb(229,246,239) else Color.WHITE, 11f)
                setTextColor(if (active == index) familyDeep else Color.rgb(93,118,112))
                setTypeface(null, if (active == index) Typeface.BOLD else Typeface.NORMAL)
                setOnClickListener {
                    hideKeyboard()
                    when (index) {
                        0 -> showCalendar()
                        1 -> showSearch()
                        else -> showArchive()
                    }
                }
            }
            nav.addView(b, LinearLayout.LayoutParams(0, dp(44), 1f))
        }
        content.addView(nav, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
    }

    private fun chooseDate(after: () -> Unit) {
        DatePickerDialog(this, { _, year, month, day ->
            selectedDate = LocalDate.of(year, month + 1, day)
            after()
        }, selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth).show()
    }

    private fun showCalendar() {
        screen("Il calendario dei pasti", "Organizza pranzo e cena, anche per i prossimi giorni.",
            "calendar") { showCalendar() }
        searchForDay = false
        selectedAlternative = false
        label("Tocca un giorno per vederne i pasti. Tieni premuto come da impostazioni Android per aggiungere una ricetta.", 13f)
        action("ⓘ Come funziona il calendario") {
            AppHints.showAgain(this, "calendar_long_press", "Come funziona il calendario",
                "Tocca una data per scegliere il giorno e vedere pranzo e cena. " +
                "Tieni premuta la data con una pressione prolungata per aggiungere una ricetta: " +
                "potrai scegliere pranzo, cena e principale o alternativa.")
        }
        val calendar = MealCalendar(this, selectedDate, { month -> db.monthMarkers(month) }, { day ->
            selectedDate = day
            showCalendar()
        }, { day -> confirmDaySearch(day) })
        content.addView(calendar, LinearLayout.LayoutParams(-1, -2))
        content.post {
            AppHints.showOnce(this, "calendar_long_press", "Come funziona il calendario",
                "Tocca una data per scegliere il giorno e vedere pranzo e cena. " +
                "Tieni premuta la data con una pressione prolungata per aggiungere una ricetta: " +
                "potrai scegliere pranzo, cena e principale o alternativa.")
        }
        label(selectedDate.format(dateFormatter).replaceFirstChar { it.uppercase() }, 17f, true)
        actionRow("◀ Precedente", { selectedDate = selectedDate.minusDays(1); expandedMeals.clear(); showCalendar() },
            "Successivo ▶", { selectedDate = selectedDate.plusDays(1); expandedMeals.clear(); showCalendar() })
        actionRow("↻ Oggi", { selectedDate = LocalDate.now(); expandedMeals.clear(); showCalendar() },
            "🗑 Svuota giorno", { confirmClearDay() })
        val today = db.onDay(selectedDate.toString())
        listOf("Pranzo", "Cena").forEach { meal ->
            val planned = today.filter { it.meal == meal || it.meal == "Entrambi" }
            val expanded = meal in expandedMeals
            val section = Button(this).apply {
                text = (if (meal == "Pranzo") "☀️ PRANZO" else "🌙 CENA") +
                    "  ·  " + planned.size + (if (expanded) "  ⌃" else "  ⌄")
                textSize = 17f; isAllCaps = false
                gravity = Gravity.CENTER_VERTICAL
                setTypeface(null, Typeface.BOLD)
                setTextColor(familyDeep)
                background = outlined(familyPale, 17f)
                setOnClickListener {
                    if (expanded) expandedMeals.remove(meal) else expandedMeals.add(meal)
                    showCalendar()
                }
            }
            content.addView(section, LinearLayout.LayoutParams(-1, dp(46)).apply {
                topMargin = dp(5); bottomMargin = dp(4)
            })
            if (planned.isEmpty()) label("Nessun piatto assegnato.", 13f)
            planned.forEach { recipe ->
                action((if (recipe.alternative) "↪ " else "• ") + recipe.title) { showDetail(recipe.id) }
                if (expanded) {
                    actionRow("✏️ Modifica", { changePlanning(recipe) },
                        "🗑 Dal giorno", { removeFromDay(recipe) })
                    action("📋 Clona in un altro giorno") { cloneRecipeToDay(recipe) }
                }
            }
            if (expanded) {
                actionRow("＋ Aggiungi", {
                    selectedMeal = meal; showEditor(planByDefault = true)
                }, "🔎 Alternativa", { startMealSearch(meal, true) })
            }
        }
        actionRow("🔎 Cerca ricetta", { showSearch() },
            "＋ Nuova ricetta", { showEditor() })
        action("PDF e stampa del giorno") {
            report("Menu del giorno", selectedDate.format(dateFormatter), db.onDay(selectedDate.toString()))
        }
    }

    /** Unplan a recipe without deleting its saved ingredients, steps or photograph. */
    private fun removeFromDay(recipe: Recipe) {
        AlertDialog.Builder(this).setTitle("Rimuovere dal giorno?")
            .setMessage("«" + recipe.title + "» non comparirà più nel giorno " +
                recipe.day + ". Resterà nel ricettario.")
            .setPositiveButton("Rimuovi dal giorno") { _, _ ->
                db.save(recipe.copy(day = ""))
                showCalendar()
            }.setNegativeButton("Annulla", null).show()
    }

    private fun confirmClearDay() {
        val recipes = db.onDay(selectedDate.toString())
        if (recipes.isEmpty()) {
            Toast.makeText(this, "Il giorno è già vuoto", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this).setTitle("Svuotare questo giorno?")
            .setMessage("Rimuovere " + recipes.size + " piatti dal " +
                selectedDate.format(dateFormatter) +
                "? Le ricette rimarranno tutte nel ricettario.")
            .setPositiveButton("Svuota giorno") { _, _ ->
                recipes.forEach { db.save(it.copy(day = "")) }
                expandedMeals.clear()
                showCalendar()
            }.setNegativeButton("Annulla", null).show()
    }

    /** Update a saved appointment in place, preserving the recipe and its photographs. */
    private fun changePlanning(recipe: Recipe) {
        val meals = arrayOf("Pranzo", "Cena", "Entrambi")
        val types = arrayOf("Principale", "Alternativa")
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(10), dp(18), dp(10))
        }
        fun picker(title: String, choices: Array<String>, initial: String): Spinner {
            form.addView(TextView(this).apply { text = title; textSize = 16f })
            return Spinner(this).apply {
                adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, choices)
                setSelection(choices.indexOf(initial).coerceAtLeast(0))
                form.addView(this, LinearLayout.LayoutParams(-1, dp(54)))
            }
        }
        val meal = picker("Pasto", meals, recipe.meal)
        val type = picker("Tipo", types, if (recipe.alternative) "Alternativa" else "Principale")
        val category = picker("Categoria del piatto", categories, recipe.category)
        AlertDialog.Builder(this).setTitle("Modifica assegnazione")
            .setMessage(recipe.title + "\n" + recipe.day)
            .setView(form)
            .setPositiveButton("Salva") { _, _ ->
                db.save(recipe.copy(meal = meal.selectedItem.toString(),
                    alternative = type.selectedItem.toString() == "Alternativa",
                    category = category.selectedItem.toString()))
                Toast.makeText(this, "Assegnazione aggiornata", Toast.LENGTH_SHORT).show()
                if (recipe.day.isNotBlank()) selectedDate = LocalDate.parse(recipe.day)
                showCalendar()
            }.setNegativeButton("Annulla", null).show()
    }

    /** Make independent copies on another calendar date, repeating as often as desired. */
    private fun cloneRecipeToDay(recipe: Recipe) {
        chooseDate {
            val destination = selectedDate.toString()
            chooseMeal { meal, alternative ->
                db.save(recipe.copy(id = 0L, day = destination, meal = meal, alternative = alternative))
                AlertDialog.Builder(this).setTitle("Ricetta copiata")
                    .setMessage(recipe.title + "\n" + destination + " · " + meal +
                        " · " + (if (alternative) "Alternativa" else "Principale"))
                    .setPositiveButton("Copia in un altro giorno") { _, _ -> cloneRecipeToDay(recipe) }
                    .setNegativeButton("Torna al calendario") { _, _ -> showCalendar() }
                    .show()
            }
        }
    }

    private fun confirmDaySearch(day: LocalDate) {
        AlertDialog.Builder(this).setTitle("Aggiungi ricetta a questo giorno?")
            .setMessage(day.format(dateFormatter))
            .setNegativeButton("No", null)
            .setPositiveButton("Sì") { _, _ ->
                AlertDialog.Builder(this).setTitle("Per quale pasto?")
                    .setItems(arrayOf("Pranzo", "Cena")) { _, index ->
                        selectedDate = day
                        val meal = if (index == 0) "Pranzo" else "Cena"
                        val hasMain = db.onDay(day.toString()).any { !it.alternative && (it.meal == meal || it.meal == "Entrambi") }
                        var alternative = hasMain
                        AlertDialog.Builder(this).setTitle("$meal · Tipo di ricetta")
                            .setSingleChoiceItems(arrayOf("Principale", "Alternativa"), if (hasMain) 1 else 0) { _, choice -> alternative = choice == 1 }
                            .setPositiveButton("Cerca ricetta") { _, _ -> startMealSearch(meal, alternative) }
                            .setNegativeButton("Annulla", null).show()
                    }
                    .setNegativeButton("Annulla", null).show()
            }.show()
    }

    private fun startMealSearch(meal: String, alternative: Boolean) {
        selectedMeal = meal
        selectedAlternative = alternative
        searchForDay = true
        showSearch()
    }

    private fun showEditor(existing: Recipe? = null, initialAlternative: Boolean = false,
                           suggestedTitle: String = "", suggestedUrl: String = "",
                           planByDefault: Boolean = false) {
        selectedPhoto = existing?.imagePath ?: ""
        photoPreview = null
        screen(if (existing == null || existing.id == 0L) "Scrivi la tua ricetta" else "Modifica ricetta",
            "Compila i campi che conosci. Nome, ingredienti e preparazione sono obbligatori.",
            "editor-${existing?.id ?: 0L}") {
            showEditor(existing, initialAlternative, suggestedTitle, suggestedUrl, planByDefault)
        }
        label("INFORMAZIONI GENERALI", 20f, true)
        val titleField = input("Nome del piatto *", existing?.title ?: suggestedTitle)
        val descriptionField = input("Descrizione breve / presentazione", existing?.description.orEmpty(), true)
        val categoryPicker = dropdown("Portata", categories, existing?.category ?: selectedCategory)
        val difficultyPicker = dropdown("Difficoltà", difficulties, existing?.difficulty ?: searchDifficulty)
        val cuisineField = input("Tipo di cucina / provenienza (es. italiana)", existing?.cuisine.orEmpty())
        val portionsField = input("Dosi / numero persone (es. 4 persone)", existing?.portions.orEmpty())
        val authorField = input("Autore della ricetta (facoltativo)", existing?.author.orEmpty())
        val costField = input("Costo indicativo (facoltativo)", existing?.cost.orEmpty())
        val tagsField = input("Etichette (es. vegetariano, senza glutine)", existing?.tags.orEmpty())
        val allergensField = input("Allergeni (facoltativo)", existing?.allergens.orEmpty())

        label("INGREDIENTI *", 20f, true)
        label("Una riga per ingrediente, con quantità e unità di misura. Puoi aggiungere o togliere righe.", 13f)
        val ingredientRows = RecipeListFields(this, existing?.ingredients ?: searchIngredients, false)
        content.addView(ingredientRows)

        label("PREPARAZIONE · PASSAGGI NUMERATI *", 20f, true)
        label("Inserisci ogni passaggio separatamente. L'app mantiene la numerazione anche se ne elimini uno.", 13f)
        val stepRows = RecipeListFields(this, existing?.instructions.orEmpty(), true)
        content.addView(stepRows)

        label("TEMPI E COTTURA", 20f, true)
        val prepField = input("Tempo di preparazione (es. 15 min)", existing?.prepTime.orEmpty())
        val cookField = input("Tempo di cottura (es. 25 min)", existing?.cookTime.orEmpty())
        val restField = input("Riposo / lievitazione (es. 2 ore)", existing?.restTime.orEmpty())
        val totalField = input("Tempo totale (facoltativo)", existing?.totalTime.orEmpty())
        val temperatureField = input("Temperatura forno / cottura (es. 180 °C)", existing?.temperature.orEmpty())
        val savedDuration = existing?.duration ?: searchDuration
        val durationPicker = dropdown("Fascia di tempo", (durations.toList() + savedDuration).distinct().toTypedArray(), savedDuration)

        label("CONSIGLI E ALTRE INFORMAZIONI", 20f, true)
        val equipmentField = input("Attrezzatura / utensili necessari", existing?.equipment.orEmpty(), true)
        val tipsField = input("Consigli e accorgimenti", existing?.tips.orEmpty(), true)
        val variationsField = input("Varianti e sostituzioni", existing?.variations.orEmpty(), true)
        val storageField = input("Conservazione e possibilità di congelare", existing?.storage.orEmpty(), true)
        val nutritionField = input("Valori nutrizionali (se conosciuti)", existing?.nutrition.orEmpty(), true)
        val notesField = input("Note personali", existing?.notes.orEmpty(), true)
        val urlField = input("Link di riferimento (facoltativo)", existing?.url ?: suggestedUrl)
        val hasImportedText = !existing?.originalInstructions.isNullOrBlank()
        var originalField: EditText? = null
        var modePicker: Spinner? = null
        if (hasImportedText) {
            label("PROCEDIMENTO IMPORTATO DALLA FONTE", 18f, true)
            originalField = input("Testo originale della ricetta", existing?.originalInstructions.orEmpty(), true)
            modePicker = dropdown("Modalità di salvataggio", arrayOf("Semplificata","Originale","Entrambe"),
                existing?.saveMode ?: "Entrambe")
        }

        label("FOTOGRAFIA DEL PIATTO", 20f, true)
        photoLabel = label("La foto viene compressa in JPEG (massimo 960 px) e salvata sul telefono.", 13f)
        photoPreview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Anteprima della foto della ricetta"
        }
        content.addView(photoPreview, LinearLayout.LayoutParams(-1, dp(140)).apply { bottomMargin = dp(6) })
        refreshPhotoPreview()
        actionRow("📷 Fotocamera", { launchCamera() }, "🖼️ Galleria", {
            startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE)
            }, PHOTO_REQUEST)
        })
        action("✕ Rimuovi foto dalla ricetta") {
            selectedPhoto = ""
            refreshPhotoPreview()
        }

        label("ARCHIVIAZIONE E CALENDARIO", 20f, true)
        val mealPicker = dropdown("Pasto", arrayOf("Pranzo","Cena","Entrambi"), existing?.meal ?: selectedMeal)
        val alternativePicker = dropdown("Tipo di scelta", arrayOf("Principale","Alternativa"),
            if (existing?.alternative == true || initialAlternative ||
                (existing == null && selectedAlternative)) "Alternativa" else "Principale")
        val planned = CheckBox(this).apply {
            text = "Aggiungi anche al calendario"
            isChecked = existing?.day?.isNotBlank() == true || (existing == null && (planByDefault || searchForDay))
        }
        content.addView(planned)
        if (existing?.day?.isNotBlank() == true) {
            try { selectedDate = LocalDate.parse(existing.day) } catch (_: Exception) { }
        }
        val dateButton = Button(this).apply {
            text = "📅 " + selectedDate.format(dateFormatter) + " · Cambia giorno"
            isAllCaps = false
            setOnClickListener {
                hideKeyboard()
                chooseDate { text = "📅 " + selectedDate.format(dateFormatter) + " · Cambia giorno" }
            }
        }
        content.addView(dateButton)

        action("✅ Salva ricetta nel ricettario") {
            val title = titleField.text.toString().trim()
            if (title.isBlank()) {
                titleField.error = "Indica il nome del piatto"
                titleField.requestFocus()
                return@action
            }
            if (!ingredientRows.requireOne("Aggiungi almeno un ingrediente")) return@action
            if (stepRows.entries().isEmpty() && originalField?.text.isNullOrBlank()) {
                stepRows.requireOne("Inserisci almeno un passaggio della preparazione")
                return@action
            }
            val url = urlField.text.toString().trim()
            if (url.isNotBlank() && !trusted(url)) {
                urlField.error = "Inserisci un link HTTPS pubblico e completo"
                urlField.requestFocus()
                return@action
            }
            val mode = modePicker?.selectedItem?.toString() ?: existing?.saveMode ?: "Semplificata"
            val record = Recipe(
                id = existing?.id ?: 0L,
                day = if (planned.isChecked) selectedDate.toString() else "",
                meal = mealPicker.selectedItem.toString(),
                category = categoryPicker.selectedItem.toString(),
                title = title,
                ingredients = ingredientRows.value(),
                instructions = stepRows.value(),
                difficulty = difficultyPicker.selectedItem.toString(),
                duration = durationPicker.selectedItem.toString(),
                url = url,
                alternative = alternativePicker.selectedItem.toString() == "Alternativa",
                imagePath = selectedPhoto,
                originalInstructions = originalField?.text?.toString()?.trim() ?: existing?.originalInstructions.orEmpty(),
                saveMode = mode,
                prepTime = prepField.text.toString().trim(),
                cookTime = cookField.text.toString().trim(),
                totalTime = totalField.text.toString().trim(),
                restTime = restField.text.toString().trim(),
                portions = portionsField.text.toString().trim(),
                sourceImageUrl = existing?.sourceImageUrl.orEmpty(),
                nutrition = nutritionField.text.toString().trim(),
                description = descriptionField.text.toString().trim(),
                cuisine = cuisineField.text.toString().trim(),
                equipment = equipmentField.text.toString().trim(),
                tips = tipsField.text.toString().trim(),
                notes = notesField.text.toString().trim(),
                storage = storageField.text.toString().trim(),
                variations = variationsField.text.toString().trim(),
                allergens = allergensField.text.toString().trim(),
                tags = tagsField.text.toString().trim(),
                temperature = temperatureField.text.toString().trim(),
                author = authorField.text.toString().trim(),
                cost = costField.text.toString().trim(),
                isPersonal = existing?.isPersonal ?: (suggestedUrl.isBlank() && existing?.url.isNullOrBlank())
            )
            try {
                db.save(record)
                Toast.makeText(this, "Ricetta salvata nel ricettario", Toast.LENGTH_SHORT).show()
                searchIngredients = ""
                showCalendar()
            } catch (_: Exception) {
                Toast.makeText(this, "Salvataggio non riuscito: riprova senza uscire dalla scheda", Toast.LENGTH_LONG).show()
            }
        }
        action("← Torna indietro senza salvare") { showCalendar() }
    }

    private fun refreshPhotoPreview() {
        val file = selectedPhoto.takeIf { it.isNotBlank() }?.let { File(it) }
        if (file != null && file.isFile) {
            val image = BitmapFactory.decodeFile(file.absolutePath)
            photoPreview?.setImageBitmap(image)
            photoPreview?.visibility = if (image != null) View.VISIBLE else View.GONE
            photoLabel?.text = if (image != null) "📷 Foto compressa, pronta da salvare" else "Foto non leggibile"
        } else {
            photoPreview?.setImageDrawable(null)
            photoPreview?.visibility = View.GONE
            photoLabel?.text = "Nessuna foto selezionata (opzionale)"
        }
    }

    private fun launchCamera() {
        try {
            val folder = File(cacheDir, "camera").apply { mkdirs() }
            val file = File(folder, UUID.randomUUID().toString() + ".jpg")
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this, packageName + ".fileprovider", file)
            pendingCameraPath = file.absolutePath
            val capture = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, uri)
                clipData = android.content.ClipData.newRawUri("photo", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            startActivityForResult(capture, CAMERA_REQUEST)
        } catch (_: Exception) {
            pendingCameraPath?.let { File(it).delete() }
            pendingCameraPath = null
            Toast.makeText(this, "Fotocamera non disponibile su questo dispositivo", Toast.LENGTH_LONG).show()
        }
    }

    private fun trusted(url: String): Boolean = RecipeLink.isValid(url)


    /** La lettura della ricetta originale resta nell'app (WebView), non in Chrome. */
    private fun openWeb(url: String) {
        if (!trusted(url)) {
            Toast.makeText(this, "Indirizzo non autorizzato", Toast.LENGTH_LONG).show()
            return
        }
        val web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?, request: WebResourceRequest?
                ): Boolean = request?.url?.toString()?.let { !trusted(it) } ?: true
            }
            loadUrl(url)
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Ricetta originale · dentro OggiSiMangia...")
            .setView(web)
            .setNegativeButton("Chiudi", null)
            .create()
        dialog.setOnDismissListener {
            web.stopLoading()
            web.destroy()
        }
        dialog.show()
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.heightPixels * 0.84).toInt()
        )
    }

    private fun showSearch() {
        screen("Trova cosa cucinare", "Scopri le proposte senza uscire dall'app.",
            "search") { showSearch() }
        label("Giorno scelto: " + selectedDate.format(dateFormatter), 16f, true)
        if (searchForDay) label(selectedMeal + " · " + if (selectedAlternative) "Alternativa" else "Principale", 16f, true)
        actionRow("🔗 Ho un link", { showRecipeLink() }, "📅 Pasti del giorno", { showCalendar() })
        label("Quali ingredienti hai a disposizione?", 17f, true)
        label("Una casella per ingrediente. Massimo 10 ingredienti.", 13f)
        val ingredients = IngredientInputs(this, searchIngredients) { searchIngredients = it }
        content.addView(ingredients)
        val category = dropdown("Cerchi un...", categories, searchCategory)
        val difficulty = dropdown("Difficoltà desiderata", difficulties, searchDifficulty)
        val duration = dropdown("Tempo a disposizione", durations, searchDuration)
        action("Priorità dei siti e nuove fonti") {
            searchIngredients = ingredients.values().joinToString(", ")
            searchCategory = category.selectedItem.toString()
            searchDifficulty = difficulty.selectedItem.toString()
            searchDuration = duration.selectedItem.toString()
            showSourceSettings()
        }
        label("Ingredienti obbligatori, anche singolare/plurale e preparazioni equivalenti. " +
            "Categoria, difficoltà e tempi sono preferenze: gli scostamenti vengono segnalati.", 13f)

        primaryAction("🔎 CERCA RICETTE NEI SITI") {
            searchIngredients = ingredients.query() ?: return@action
            searchCategory = category.selectedItem.toString()
            searchDifficulty = difficulty.selectedItem.toString()
            searchDuration = duration.selectedItem.toString()
            val progress = AlertDialog.Builder(this)
                .setTitle("Ricerca delle ricette")
                .setMessage("Consulto i siti secondo le priorità impostate...")
                .setCancelable(false)
                .create()
            progress.show()
            val selectedIngredients = searchIngredients
            val selectedCategory = searchCategory
            val selectedDifficulty = searchDifficulty
            val selectedDuration = searchDuration
            Thread {
                val results = try {
                    val executor = java.util.concurrent.Executors.newFixedThreadPool(2)
                    try {
                        val jobs = listOf(
                            java.util.concurrent.Callable { RecipeFinder.search(selectedIngredients, selectedCategory, selectedDifficulty, selectedDuration) },
                            java.util.concurrent.Callable { RecipeDiscovery.search(selectedIngredients, selectedCategory, selectedDifficulty, selectedDuration) }
                        )
                        val all = executor.invokeAll(jobs, 80, java.util.concurrent.TimeUnit.SECONDS).flatMap {
                            try { if (it.isCancelled) emptyList() else it.get() } catch (_: Exception) { emptyList() }
                        }
                        // Prefer enriched Recipe data for duplicates, then apply one common order.
                        val unique = all.groupBy { it.url.substringBefore('?').substringBefore('#').trimEnd('/') }
                            .values.map { duplicates -> duplicates.maxBy { r ->
                                (if (r.rating != null) 2 else 0) + (if (r.feedbackCount != null) 2 else 0) + r.relevance
                            } }
                        RecipeRanking.order(unique, selectedIngredients)
                    } finally { executor.shutdownNow() }
                } catch (_: Exception) { emptyList() }
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    progress.dismiss()
                    if (results.isEmpty()) {
                        AlertDialog.Builder(this)
                            .setTitle("Nessuna ricetta verificabile")
                            .setMessage("Non ho potuto verificare ricette con tutti gli ingredienti richiesti. " +
                                "Questo non significa che non esistano: alcuni siti potrebbero " +
                                "non rispondere o bloccare la consultazione automatica. " +
                                "Puoi riprovare, cercare direttamente sui siti oppure importare un link.")
                            .setNeutralButton("Cerca sui siti") { _, _ -> showSitePicker(selectedIngredients) }
                            .setPositiveButton("Chiudi", null).show()
                    } else showRecipeResults(results)
                }
            }.start()
        }
        actionRow("↻ Azzera ingredienti", {
            searchIngredients = ""
            showSearch()
        }, "📚 Ricettario", { showArchive() })
        action("＋ Nuova ricetta") { showEditor() }
    }

    private fun showSourceSettings() {
        screen("Priorità dei siti", "L'ordine viene memorizzato per le prossime ricerche.",
            "sources") { showSourceSettings() }
        label("Le fonti con priorità più alta vengono prima nei risultati compatibili. Seguono pertinenza e valutazioni degli utenti, quando disponibili.", 14f)
        action("Aggiungi un sito") {
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18),dp(8),dp(18),dp(8)) }
            val name = EditText(this).apply { hint = "Nome del sito"; contentDescription = "Nome del sito" }
            val address = EditText(this).apply {
                hint = "Sito, es. https://www.esempio.it"; contentDescription = "Indirizzo del sito"
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            }
            box.addView(name); box.addView(address)
            val dialog = AlertDialog.Builder(this).setTitle("Nuova fonte di ricette").setView(box)
                .setPositiveButton("Memorizza", null).setNegativeButton("Annulla", null).create()
            dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val domain = RecipeSources.normalizeDomain(address.text.toString())
                val title = name.text.toString().trim().ifBlank { domain.orEmpty() }
                when {
                    domain == null -> address.error = "Inserisci un dominio pubblico HTTPS valido"
                    domain in RecipeSources.sites.values -> address.error = "Questo sito è già presente"
                    title in RecipeSources.sites.keys -> name.error = "Scegli un nome diverso"
                    else -> {
                        sourcePreferences.save(LinkedHashMap(RecipeSources.sites).apply { put(title,domain) })
                        dialog.dismiss(); showSourceSettings()
                    }
                }
            } }
            dialog.show()
        }
        val entries = RecipeSources.sites.entries.toList()
        if (entries.isEmpty()) {
            label("Nessuna fonte attiva. Aggiungi un sito o ripristina quelli iniziali per cercare nuove ricette.", 14f)
        }
        label("Puoi eliminare singolarmente qualsiasi sito: non verrà più consultato. " +
            "La modifica rimane anche dopo il riavvio e gli aggiornamenti.", 13f)
        val names = entries.map { it.key }.toTypedArray()
        entries.forEachIndexed { position, entry ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val spinner = Spinner(this).apply {
                adapter = ArrayAdapter(this@MainActivity,
                    android.R.layout.simple_spinner_dropdown_item, names)
                setSelection(position)
                background = outlined(Color.WHITE, 15f)
                setPadding(dp(9), 0, dp(5), 0)
                contentDescription = "${position + 1}ª priorità: " + entry.key
            }
            spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: AdapterView<*>?) { }
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, selected: Int, id: Long) {
                    if (selected == position) return
                    val reordered = entries.toMutableList()
                    java.util.Collections.swap(reordered, position, selected)
                    sourcePreferences.save(reordered.associateTo(linkedMapOf()) { it.key to it.value })
                    showSourceSettings()
                }
            }
            row.addView(TextView(this).apply {
                text = "${position + 1}."
                textSize = 14f
                setTextColor(familyDeep)
            }, LinearLayout.LayoutParams(dp(25), -2))
            row.addView(spinner, LinearLayout.LayoutParams(0, dp(44), 1f))
            val remove = Button(this).apply {
                text = "🗑"; textSize = 15f
                isAllCaps = false
                contentDescription = "Elimina sito " + entry.key
                background = outlined(familyPale, 16f)
                setOnClickListener {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("Eliminare il sito?")
                        .setMessage(entry.key + "\n" + entry.value +
                            "\nNon sarà più consultato nelle ricerche.")
                        .setPositiveButton("Elimina sito") { _, _ ->
                            val remaining = LinkedHashMap(RecipeSources.sites)
                            remaining.remove(entry.key)
                            sourcePreferences.save(remaining)
                            showSourceSettings()
                        }
                        .setNegativeButton("Annulla", null).show()
                }
            }
            row.addView(remove, LinearLayout.LayoutParams(dp(48), dp(44)).apply {
                marginStart = dp(5)
            })
            content.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(5)
            })
        }
        action("Ripristina siti e ordine iniziali") {
            val restored = LinkedHashMap(RecipeSources.defaults)
            RecipeSources.sites.forEach { (name,domain) -> if (domain !in restored.values) restored[name] = domain }
            sourcePreferences.save(restored); showSourceSettings()
        }
        action("← Torna alla ricerca") { showSearch() }
    }

    private fun report(title: String, subtitle: String, recipes: List<Recipe>) {
        RecipeReports.show(this, title, subtitle, recipes) { file ->
            pendingReport = file
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "application/pdf"; putExtra(Intent.EXTRA_TITLE,file.name)
            }, PDF_REQUEST)
        }
    }

    private fun showRecipeLink() {
        screen("Aggiungi una ricetta dal link", "Per il " + selectedDate.format(dateFormatter),
            "link") { showRecipeLink() }
        label("Incolla il link della pagina della ricetta.", 16f)
        val link = input("https://…")
        link.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        link.isSingleLine = true
        action("Leggi la ricetta dal link") {
            val url = link.text.toString().trim()
            if (!RecipeLink.isValid(url)) {
                link.error = "Inserisci un link HTTPS completo alla ricetta"
                return@action
            }
            lastSearchResults = emptyList()
            lastSearchOffset = 0
            showOnlineDetail(WebRecipe("Ricetta dal link", "", url,
                java.net.URI(url).host.removePrefix("www.")), imported = true)
        }
        action("← Torna alla ricerca") { showSearch() }
    }

    private fun showSitePicker(query: String) {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val choices = RecipeSources.sites.keys.toTypedArray()
        if (choices.isEmpty()) {
            Toast.makeText(this, "Aggiungi almeno una fonte nelle impostazioni", Toast.LENGTH_LONG).show()
            return
        }
        val links = RecipeSources.sites.values.map { domain ->
            "https://www.bing.com/search?q=" + java.net.URLEncoder.encode("site:$domain ricetta $query", "UTF-8")
        }
        AlertDialog.Builder(this).setTitle("Scegli il sito")
            .setItems(choices) { _, index -> openWeb(links[index]) }
            .setNegativeButton("Chiudi", null).show()
    }

    private fun showRecipeResults(items: List<WebRecipe>, offset: Int = 0) {
        if (items.isEmpty()) return
        val start = offset.coerceIn(0, items.lastIndex)
        val page = items.drop(start).take(10)
        val end = start + page.size
        lastSearchResults = items
        lastSearchOffset = start
        val scroll = ScrollView(this)
        val rows = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(10))
        }
        scroll.addView(rows)
        val popup = AlertDialog.Builder(this)
            .setTitle("Ricette ${start + 1}–$end di ${items.size}")
            .setView(scroll)
            .setNegativeButton("Chiudi", null)
            .setPositiveButton("Prossime 10") { _, _ ->
                showRecipeResults(items, end)
            }
            .setNeutralButton("Precedenti 10") { _, _ ->
                showRecipeResults(items, (start - 10).coerceAtLeast(0))
            }
            .create()
        for (recipe in page) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(12))
                background = shape(Color.rgb(242, 248, 245), 12f)
            }
            val heading = TextView(this).apply {
                text = recipe.title
                textSize = 17f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.rgb(21, 103, 91))
            }
            row.addView(heading)
            val differences = RecipePreferences.notices(recipe, searchCategory, searchDifficulty, searchDuration)
            if (differences.isNotEmpty()) {
                row.addView(TextView(this).apply {
                    text = differences.joinToString("\n") { "⚠ " + it }
                    textSize = 13f
                    setTextColor(Color.rgb(139, 79, 15))
                    setPadding(0, dp(4), 0, dp(4))
                })
            }
            if (recipe.imageUrl.isNotBlank()) {
                val preview = ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription = "Anteprima: " + recipe.title
                }
                row.addView(preview, LinearLayout.LayoutParams(-1, dp(130)))
                fetchPreview(recipe.imageUrl, preview)
            }
            val details = listOfNotNull(
                recipe.source,
                recipe.minutes?.let { "⏱ " + it + " min" },
                recipe.difficulty.takeIf { it.isNotBlank() },
                recipe.rating?.let { "⭐ " + it },
                recipe.feedbackCount?.let { it.toString() + " voti/recensioni" }
            ).joinToString(" · ")
            row.addView(TextView(this).apply {
                text = details
                textSize = 13f
                setTextColor(Color.DKGRAY)
            })
            if (recipe.summary.isNotBlank()) {
                row.addView(TextView(this).apply {
                    text = recipe.summary
                    setPadding(0, dp(5), 0, dp(5))
                    textSize = 14f
                })
            }
            val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            buttons.addView(Button(this).apply {
                text = "👀 Scheda"
                isAllCaps = false
                setOnClickListener { popup.dismiss(); showOnlineDetail(recipe) }
            }, LinearLayout.LayoutParams(0, dp(44), 1f))
            buttons.addView(Button(this).apply {
                text = "💾 Salva"
                isAllCaps = false
                setOnClickListener {
                    popup.dismiss()
                    showOnlineDetail(recipe)
                }
            }, LinearLayout.LayoutParams(0, dp(44), 1f))
            row.addView(buttons)
            rows.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(12)
            })
        }
        if (end == items.size) {
            rows.addView(TextView(this).apply {
                text = "Non ci sono altre ricette disponibili per questa ricerca. Puoi cambiare ingredienti o filtri."
                textSize = 14f
                setPadding(dp(8), dp(8), dp(8), dp(8))
            })
        }
        popup.show()
        popup.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = end < items.size
        popup.getButton(AlertDialog.BUTTON_NEUTRAL).visibility = if (start > 0) View.VISIBLE else View.GONE
        popup.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.heightPixels * 0.83).toInt()
        )
    }

    private fun showOnlineDetail(sourceRecipe: WebRecipe, imported: Boolean = false) {
        val progress = AlertDialog.Builder(this).setTitle("Leggo la ricetta")
            .setMessage("Recupero ingredienti e procedimento dalla fonte...").setCancelable(false).create()
        progress.show()
        Thread {
            val result = try { RecipeReader.read(sourceRecipe.url) } catch (_: Exception) {
                RecipeText(notice = "Questa fonte non è leggibile adesso. Puoi comunque salvare il link.")
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                progress.dismiss()
                val recipe = if (imported) sourceRecipe.copy(
                    title = result.title.ifBlank { sourceRecipe.title },
                    category = "", difficulty = "Non disponibile"
                ) else sourceRecipe
                screen(recipe.title, recipe.source, "online-" + recipe.url) {
                    showOnlineDetail(sourceRecipe, imported)
                }
                if (!imported) {
                    RecipePreferences.notices(recipe, searchCategory, searchDifficulty, searchDuration)
                        .forEach { label("⚠ " + it, 13f) }
                }
                label("Giorno scelto: " + selectedDate.format(dateFormatter), 14f)
                val imageUrl = result.imageUrl.ifBlank { recipe.imageUrl }
                if (imageUrl.isNotBlank()) {
                    val photo = ImageView(this).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        contentDescription = "Foto della ricetta da " + recipe.source
                    }
                    content.addView(photo, LinearLayout.LayoutParams(-1, dp(200)))
                    fetchPreview(imageUrl, photo)
                    label("Foto: " + recipe.source, 12f)
                }
                val details = onlineDraft(recipe, result, "Entrambe")
                showRecipeMetadata(details)
                action("📅 Aggiungi al calendario") {
                    chooseDate {
                        chooseMeal { meal, alternative ->
                            chooseSaveMode { mode ->
                                prepareOnlineDraft(recipe, result, mode) { draft ->
                                    db.save(draft.copy(day = selectedDate.toString(), meal = meal, alternative = alternative))
                                    Toast.makeText(this, "Ricetta aggiunta al calendario", Toast.LENGTH_SHORT).show()
                                    showCalendar()
                                }
                            }
                        }
                    }
                }
                label("INGREDIENTI", 17f, true)
                label(result.ingredients.ifBlank { "Non disponibili: consulta l'originale." })
                label("PREPARAZIONE SEMPLIFICATA", 17f, true)
                label(result.summary.ifBlank { recipe.summary.ifBlank { "Sintesi non disponibile." } })
                label(result.notice, 13f)
                action("📄 Mostra procedimento originale disponibile") {
                    AlertDialog.Builder(this).setTitle("Procedimento originale")
                        .setMessage(result.original.ifBlank { "Non disponibile offline: apri il sito originale." })
                        .setPositiveButton("Chiudi", null).show()
                }
                action("🌐 Vedi ricetta originale sul sito") {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(recipe.url)))
                }
                action("💾 Salva ricetta") { chooseOnlineRecipe(recipe, result) }
                if (lastSearchResults.isNotEmpty()) {
                    action("← Torna ai risultati") { showRecipeResults(lastSearchResults, lastSearchOffset) }
                }
                action("🔎 Modifica ricerca") { showSearch() }
            }
        }.start()
    }

    private fun showRecipeMetadata(recipe: Recipe) {
        val details = listOf(
            "Dosi" to recipe.portions, "Preparazione" to recipe.prepTime,
            "Cottura" to recipe.cookTime, "Tempo totale" to recipe.totalTime, "Riposo / attesa" to recipe.restTime,
            "Valori nutrizionali" to recipe.nutrition,
            "Cucina / provenienza" to recipe.cuisine, "Autore" to recipe.author,
            "Temperatura" to recipe.temperature, "Costo indicativo" to recipe.cost,
            "Etichette" to recipe.tags, "Allergeni" to recipe.allergens
        ).filter { it.second.isNotBlank() }
        details.forEach { (caption, value) -> label("$caption: $value", 14f) }
        if (details.isEmpty()) label("Dosi e tempi dettagliati non disponibili dalla fonte.", 14f)
        if (recipe.totalTime.isBlank() && recipe.prepTime.isBlank() && recipe.cookTime.isBlank()) {
            label("Tempo: " + recipe.duration, 14f)
        }
    }

    private fun chooseSaveMode(after: (String) -> Unit) {
        val choices = arrayOf("Semplificata", "Originale", "Entrambe")
        AlertDialog.Builder(this).setTitle("Quale versione vuoi conservare?")
            .setItems(choices) { _, choice -> after(choices[choice]) }
            .setNegativeButton("Annulla", null).show()
    }

    private fun chooseMeal(after: (String, Boolean) -> Unit) {
        val choices = arrayOf("Pranzo · Principale", "Pranzo · Alternativa",
            "Cena · Principale", "Cena · Alternativa", "Entrambi · Principale", "Entrambi · Alternativa")
        var selection = (when (selectedMeal) { "Cena" -> 2; "Entrambi" -> 4; else -> 0 }) +
            (if (selectedAlternative) 1 else 0)
        AlertDialog.Builder(this).setTitle("Inserisci il " + selectedDate.format(dateFormatter))
            .setSingleChoiceItems(choices, selection) { _, index -> selection = index }
            .setPositiveButton("Aggiungi") { _, _ ->
                selectedMeal = when (selection / 2) { 1 -> "Cena"; 2 -> "Entrambi"; else -> "Pranzo" }
                selectedAlternative = selection % 2 == 1
                after(selectedMeal, selectedAlternative)
            }
            .setNegativeButton("Annulla", null).show()
    }

    private fun planSavedRecipe(recipe: Recipe) { cloneRecipeToDay(recipe) }

    private fun onlineDraft(recipe: WebRecipe, text: RecipeText, mode: String) = Recipe(
        day = if (searchForDay) selectedDate.toString() else "", meal = selectedMeal,
        category = recipe.category.takeIf { it in categories } ?: searchCategory,
        title = recipe.title, ingredients = text.ingredients,
        instructions = if (mode != "Originale") text.summary.ifBlank { recipe.summary } else "",
        difficulty = recipe.difficulty.takeIf { it in difficulties } ?: "Non disponibile",
        duration = text.totalTime.ifBlank { text.time }.ifBlank { recipe.minutes?.let { "$it min" } ?: "Non disponibile" },
        url = recipe.url, alternative = selectedAlternative, imagePath = "",
        originalInstructions = if (mode != "Semplificata") text.original else "", saveMode = mode,
        prepTime = text.prepTime, cookTime = text.cookTime, totalTime = text.totalTime,
        restTime = text.restTime, portions = text.portions,
        sourceImageUrl = text.imageUrl.ifBlank { recipe.imageUrl }, nutrition = text.nutrition
    )

    private fun prepareOnlineDraft(recipe: WebRecipe, text: RecipeText, mode: String, after: (Recipe) -> Unit) {
        val draft = onlineDraft(recipe, text, mode)
        val progress = AlertDialog.Builder(this).setTitle("Preparo il salvataggio")
            .setMessage("Salvo una copia compressa della foto, se disponibile...").setCancelable(false).create()
        progress.show()
        Thread {
            val path = try { RecipePhotoStore.save(this, draft.sourceImageUrl) } catch (_: Exception) { "" }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                progress.dismiss()
                if (draft.sourceImageUrl.isNotBlank() && path.isBlank()) {
                    Toast.makeText(this, "Foto non scaricabile: salvo comunque la ricetta e il link.", Toast.LENGTH_LONG).show()
                }
                after(draft.copy(imagePath = path))
            }
        }.start()
    }

    private fun chooseOnlineRecipe(recipe: WebRecipe, text: RecipeText) {
        chooseSaveMode { mode -> prepareOnlineDraft(recipe, text, "Entrambe") { draft -> showEditor(draft.copy(saveMode = mode)) } }
    }

    private fun fetchPreview(url: String, target: ImageView) {
        if (!url.startsWith("https://")) return
        Thread {
            val bitmap = try { RecipePhotoStore.load(url) } catch (_: Exception) { null } ?: return@Thread
            runOnUiThread {
                if (!isFinishing && !isDestroyed && target.isAttachedToWindow) target.setImageBitmap(bitmap)
                else bitmap.recycle()
            }
        }.start()
    }

    private fun showArchive(search: String = "", category: String = "Tutte",
                            difficulty: String = "Tutte", order: String = "Nome A–Z", ingredientQuery: String = "") {
        screen("Il mio ricettario", "Ricette salvate sul telefono, consultabili anche offline.",
            "archive") { showArchive(search, category, difficulty, order, ingredientQuery) }
        val queryField = input("Cerca per nome o parola chiave", search)
        label("Ingredienti: la ricetta deve contenerli tutti", 14f, true)
        label("Una casella per ingrediente. Massimo 10 ingredienti.", 13f)
        val ingredientField = IngredientInputs(this, ingredientQuery)
        content.addView(ingredientField)
        val categoryPicker = dropdown("Categoria", arrayOf("Tutte") + categories, category)
        val difficultyPicker = dropdown("Difficoltà", arrayOf("Tutte") + difficulties, difficulty)
        val orderPicker = dropdown("Ordina", arrayOf("Nome A–Z", "Data (recenti)", "Difficoltà"), order)
        action("Applica filtri") {
            val selectedIngredients = ingredientField.query(allowEmpty = true) ?: return@action
            showArchive(queryField.text.toString().trim(),
                categoryPicker.selectedItem.toString(),
                difficultyPicker.selectedItem.toString(),
                orderPicker.selectedItem.toString(), selectedIngredients)
        }
        val items = db.archive(search, category, difficulty, order, ingredientQuery)
        label("Risultati: " + items.size, 17f, true)
        if (items.isEmpty()) label("Nessuna ricetta trovata. Cambia i filtri o aggiungine una.", 14f)
        items.forEach { r ->
            action("🍴 " + r.title + "\n" + r.category + " · " + r.difficulty + " · " + r.duration) {
                showDetail(r.id)
            }
            action("📅 Pianifica «" + r.title + "»") { planSavedRecipe(r) }
        }
        action("＋ Nuova ricetta") { showEditor() }
    }

    private fun addPhotoPreview(path: String) {
        val file = File(path)
        if (!file.exists()) return
        val bitmap = BitmapFactory.decodeFile(path) ?: return
        val photo = ImageView(this).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "Foto della ricetta"
        }
        content.addView(photo, LinearLayout.LayoutParams(-1, dp(145)).apply { bottomMargin = dp(8) })
    }

    private fun showDetail(id: Long) {
        val r = db.get(id) ?: run { showCalendar(); return }
        screen(r.title, r.meal + " · " + r.category + " · " +
            (if (r.alternative) "Alternativa" else "Principale"),
            "detail-" + id) { showDetail(id) }
        if (r.imagePath.isNotBlank()) addPhotoPreview(r.imagePath)
        label("📅 " + r.day.ifBlank { "Solo ricettario" } + "   ⏱ " + r.duration + "   👩‍🍳 " + r.difficulty, 15f)
        showRecipeMetadata(r)
        if (r.description.isNotBlank()) { label("DESCRIZIONE", 17f, true); label(r.description) }
        label("INGREDIENTI", 17f, true)
        label(r.ingredients.ifBlank { "Non annotati" })
        label("PREPARAZIONE", 17f, true)
        if (r.instructions.isNotBlank()) label(r.instructions)
        if (r.originalInstructions.isNotBlank()) {
            label("PROCEDIMENTO ORIGINALE", 17f, true)
            label(r.originalInstructions)
        }
        if (r.instructions.isBlank() && r.originalInstructions.isBlank()) label("Consulta la ricetta originale, se disponibile.")
        listOf("ATTREZZATURA" to r.equipment, "CONSIGLI" to r.tips,
            "VARIANTI" to r.variations, "CONSERVAZIONE" to r.storage,
            "NOTE PERSONALI" to r.notes).forEach { (heading, value) ->
            if (value.isNotBlank()) { label(heading, 17f, true); label(value) }
        }
        if (r.url.isNotBlank() && trusted(r.url)) {
            action("🌐 Leggi la ricetta nell’app") { openWeb(r.url) }
        }
        action("PDF e stampa della ricetta") { report("Scheda ricetta", r.title, listOf(r)) }
        action("✏️ Modifica questa ricetta") {
            if (r.day.isNotBlank()) selectedDate = LocalDate.parse(r.day)
            showEditor(r)
        }
        if (r.day.isNotBlank()) {
            actionRow("✏️ Cambia assegnazione", { changePlanning(r) },
                "🗑 Dal giorno", { removeFromDay(r) })
        }
        action("📋 Clona su altri giorni del calendario") { cloneRecipeToDay(r) }
        action("🗑️ Elimina questa voce") {
            AlertDialog.Builder(this).setTitle("Eliminare la ricetta?")
                .setMessage("La voce sarà rimossa dal calendario e dal ricettario. Operazione non reversibile.")
                .setPositiveButton("Elimina") { _, _ ->
                    db.remove(id)
                    showCalendar()
                }
                .setNegativeButton("Annulla", null).show()
        }
        action("← Torna al calendario") { showCalendar() }
    }

    @Deprecated("Activity result used for system photo picker compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PDF_REQUEST && resultCode == RESULT_OK) {
            val file = pendingReport ?: return
            val uri = data?.data ?: return
            try {
                contentResolver.openOutputStream(uri)?.use { output -> file.inputStream().use { it.copyTo(output) } }
                    ?: error("Destinazione non disponibile")
                Toast.makeText(this, "PDF salvato", Toast.LENGTH_LONG).show()
            } catch (_: Exception) { Toast.makeText(this, "Impossibile salvare il PDF", Toast.LENGTH_LONG).show() }
            pendingReport = null
        }
        if (requestCode == PHOTO_REQUEST && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                selectedPhoto = compressPhoto(uri)
                refreshPhotoPreview()
                Toast.makeText(this, "Foto compressa e pronta", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(this, "Impossibile leggere la foto", Toast.LENGTH_LONG).show()
            }
        }
        if (requestCode == CAMERA_REQUEST) {
            val temporary = pendingCameraPath?.let { File(it) }
            pendingCameraPath = null
            try {
                if (resultCode == RESULT_OK && temporary?.isFile == true && temporary.length() > 0L) {
                    selectedPhoto = compressPhoto(Uri.fromFile(temporary))
                    refreshPhotoPreview()
                    Toast.makeText(this, "Foto scattata, compressa e pronta", Toast.LENGTH_SHORT).show()
                } else if (resultCode == RESULT_OK) {
                    Toast.makeText(this, "La fotocamera non ha restituito una foto", Toast.LENGTH_LONG).show()
                }
            } catch (_: Exception) {
                Toast.makeText(this, "Impossibile elaborare la foto della fotocamera", Toast.LENGTH_LONG).show()
            } finally { temporary?.delete() }
        }
    }

    private fun compressPhoto(uri: Uri): String {
        val resolver = contentResolver
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { stream -> BitmapFactory.decodeStream(stream, null, opts) }
        var scale = 1
        while (opts.outWidth / scale > 960 || opts.outHeight / scale > 960) scale *= 2
        val decoding = BitmapFactory.Options().apply { inSampleSize = scale }
        val bitmap = resolver.openInputStream(uri).use { stream ->
            BitmapFactory.decodeStream(stream, null, decoding)
        } ?: throw IllegalStateException("Foto non valida")
        val ratio = minOf(1.0, 960.0 / maxOf(bitmap.width, bitmap.height))
        val resized = if (ratio < 1.0) Bitmap.createScaledBitmap(bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1), true) else bitmap
        val folder = File(filesDir, "photos").apply { mkdirs() }
        val file = File(folder, UUID.randomUUID().toString() + ".jpg")
        FileOutputStream(file).use { resized.compress(Bitmap.CompressFormat.JPEG, 75, it) }
        if (resized !== bitmap) bitmap.recycle()
        resized.recycle()
        return file.absolutePath
    }

    override fun onDestroy() {
        db.close()
        super.onDestroy()
    }
}


