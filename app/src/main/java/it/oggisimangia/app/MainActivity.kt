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
import org.json.JSONArray
import org.json.JSONObject
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class MainActivity : Activity() {
    private lateinit var db: RecipeDb
    private lateinit var sourcePreferences: SourcePreferences
    private lateinit var nano: LocalNanoAssistant
    private var nanoRequestedText = ""
    private var pendingReport: File? = null
    private lateinit var content: LinearLayout
    private lateinit var screenRoot: LinearLayout
    private var selectedDate = LocalDate.now()
    private var selectedMeal = "Pranzo"
    private var selectedAlternative = false
    private var searchForDay = false
    private var selectedCategory = "Primo"
    private var searchIngredients = ""
    private var searchNaturalPrompt = ""
    private var searchDifficulty = "Facile"
    private var searchDuration = "Veloce"
    private var searchCategory = "Primo"
    private var searchType = "Qualsiasi"
    private var selectedPhoto = ""
    private val magazinePages = mutableListOf<String>()
    private val scanPageRoles = linkedMapOf<String, String>()
    private val scanPageTexts = linkedMapOf<String, String>()
    private var pendingGalleryRole = MagazineRecipeText.WHOLE
    private var editorPrep: EditText? = null
    private var editorCook: EditText? = null
    private var editorRest: EditText? = null
    private var editorTotal: EditText? = null
    private var editorPortions: EditText? = null
    private var editorDifficulty: Spinner? = null
    private var autoTitle = ""
    private var autoIngredients = ""
    private var autoSteps = ""
    private var autoPrep = ""
    private var autoCook = ""
    private var autoRest = ""
    private var autoTotal = ""
    private var autoPortions = ""
    private var autoDifficulty = ""
    private val dishPhotoChoices = linkedMapOf<String, Boolean>()
    private var magazinePagesContainer: LinearLayout? = null
    private var dishPhotosContainer: LinearLayout? = null
    private var editorTitle: EditText? = null
    private var editorIngredients: RecipeListFields? = null
    private var editorSteps: RecipeListFields? = null
    private var editorOcr: EditText? = null
    private var pendingCameraKind = "cover"
    private val scanOcrResults = mutableMapOf<String, String>()
    private val processedOcrPages = mutableSetOf<String>()
    private val pendingOcrPages = mutableSetOf<String>()
    private var ocrStatusLabel: TextView? = null
    private var scanSession = 0
    private var photoPreview: ImageView? = null
    private var pendingCameraPath: String? = null
    private var lastSearchResults: List<WebRecipe> = emptyList()
    private var lastSearchOffset = 0
    private val siteMorePages = mutableMapOf<String, Int>()
    private val siteResultsExhausted = mutableSetOf<String>()
    private var searchResultGeneration = 0
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
        private const val MAGAZINE_PAGES_REQUEST = 404
        private const val DISH_PHOTOS_REQUEST = 405
        private val categories = arrayOf("Antipasto", "Primo", "Secondo", "Contorno", "Piatto unico", "Dolce")
        private val difficulties = arrayOf("Facile", "Media", "Difficile", "Non disponibile")
        private val durations = arrayOf("Volante", "Veloce", "Media", "Lunga")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingCameraPath = savedInstanceState?.getString("pending_camera_path")
        pendingCameraKind = savedInstanceState?.getString("pending_camera_kind") ?: "cover"
        db = RecipeDb(this)
        sourcePreferences = SourcePreferences(this)
        RecipeSources.configure(sourcePreferences.load())
        nano = LocalNanoAssistant(this)
        nano.refresh()
        window.statusBarColor = Color.rgb(45, 91, 61)
        window.navigationBarColor = Color.rgb(45, 91, 61)
        showSplash()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("pending_camera_path", pendingCameraPath)
        outState.putString("pending_camera_kind", pendingCameraKind)
        super.onSaveInstanceState(outState)
    }


    /** Opening illustration; returning users go back to their calendar. */
    private fun showSplash() {
        currentPageKey = "splash"
        pageHistory.clear()
        val layout = onboardingRoot("", "Le tue ricette ogni giorno")
        layout.addView(TextView(this).apply {
            text = "━━━━━  ●"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(familyDeep)
        }, LinearLayout.LayoutParams(-1, dp(38)))
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!isFinishing && !isDestroyed) {
                if (displayName().isBlank()) showNameIntroduction() else showCalendar()
            }
        }, 650L)
    }

    private fun displayName(): String = getSharedPreferences("user_profile", MODE_PRIVATE)
        .getString("display_name", "").orEmpty().trim()

    private fun onboardingRoot(title: String, subtitle: String): LinearLayout {
        val wrapper = ScrollView(this).apply { isFillViewport = true }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(32), dp(24), dp(28))
            setBackgroundColor(Color.rgb(255, 250, 238))
        }
        wrapper.addView(layout)
        setContentView(wrapper)
        layout.addView(TextView(this).apply {
            text = "OggiSiMangia..."
            textSize = 27f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.rgb(45, 91, 61))
            gravity = Gravity.CENTER
        })
        layout.addView(TextView(this).apply {
            text = title
            textSize = 24f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.rgb(35, 70, 49))
            gravity = Gravity.CENTER
            setPadding(0, dp(22), 0, dp(4))
        })
        layout.addView(TextView(this).apply {
            text = subtitle
            textSize = 15f
            setTextColor(Color.rgb(82, 103, 76))
            gravity = Gravity.CENTER
        })
        if (title.isNotBlank()) {
            layout.addView(Button(this).apply {
                text = "? Guida"
                isAllCaps = false
                contentDescription = "Apri guida di OggiSiMangia"
                setTextColor(Color.rgb(21, 123, 52))
                background = outlined(Color.WHITE, 14f)
                setOnClickListener { GuideDialog.show(this@MainActivity) }
            }, LinearLayout.LayoutParams(dp(132), dp(46)).apply { topMargin = dp(9) })
        }
        val image = ImageView(this).apply {
            setImageResource(R.drawable.app_icon_clean)
            contentDescription = "Calendario, pasta e basilico di OggiSiMangia"
        }
        layout.addView(image, LinearLayout.LayoutParams(dp(202), dp(202)).apply {
            topMargin = dp(18); bottomMargin = dp(16)
        })
        image.alpha = 0.3f
        image.scaleX = 0.78f
        image.scaleY = 0.78f
        image.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(650L).start()
        return layout
    }

    private fun showNameIntroduction() {
        currentPageKey = "onboarding-name"
        pageHistory.clear()
        val layout = onboardingRoot("Come ti chiami?",
            "Così posso darti il benvenuto ogni volta che apri l’app!")
        val nameInput = EditText(this).apply {
            hint = "Il tuo nome..."
            setSingleLine(true)
            textSize = 18f
            setText(displayName())
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_FLAG_CAP_WORDS
            background = outlined(Color.WHITE, 16f)
            setPadding(dp(16), dp(11), dp(16), dp(11))
            contentDescription = "Il tuo nome"
        }
        layout.addView(nameInput, LinearLayout.LayoutParams(-1, dp(54)))
        layout.addView(Button(this).apply {
            text = "Continua  →"
            isAllCaps = false
            textSize = 17f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            background = shape(Color.rgb(64, 121, 65), 18f)
            setOnClickListener {
                val value = nameInput.text.toString().trim().replace(Regex("\\s+"), " ")
                if (value.isBlank() || value.length > 40) {
                    nameInput.error = "Inserisci un nome (massimo 40 caratteri)"
                    nameInput.requestFocus()
                } else {
                    getSharedPreferences("user_profile", MODE_PRIVATE).edit()
                        .putString("display_name", value).apply()
                    hideKeyboard(nameInput)
                    showPersonalWelcome(value)
                }
            }
        }, LinearLayout.LayoutParams(-1, dp(55)).apply { topMargin = dp(20) })
    }

    private fun showPersonalWelcome(name: String = displayName()) {
        currentPageKey = "onboarding-welcome"
        val layout = onboardingRoot("Benvenuto, $name!",
            "Da oggi organizzare i tuoi pasti sarà più semplice e gustoso!")
        layout.addView(TextView(this).apply {
            text = "📅  Pianifica i pasti    🍝  Scopri ricette    📷  Digitalizza le riviste"
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(55, 92, 63))
        })
        layout.addView(Button(this).apply {
            text = "Inizia  →"
            isAllCaps = false
            textSize = 17f
            setTextColor(Color.WHITE)
            background = shape(Color.rgb(64, 121, 65), 18f)
            setOnClickListener { pageHistory.clear(); showCalendar() }
        }, LinearLayout.LayoutParams(-1, dp(55)).apply { topMargin = dp(23) })
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

    /** Palette used by the approved thirteen-screen mockup. */
    private val familyTeal = Color.rgb(41, 163, 65)
    private val familyDeep = Color.rgb(21, 123, 52)
    private val familyInk = Color.rgb(15, 47, 48)
    private val familyPale = Color.rgb(239, 248, 237)
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
            setBackgroundColor(Color.rgb(255, 250, 241))
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(22))
        }
        scroll.addView(content)
        screenRoot = background
        background.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(background)
        // Compact white top bar and rounded cards from the approved previews.
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(7), dp(12), dp(8))
            this.background = outlined(Color.WHITE, 17f)
        }
        hero.addView(ImageView(this).apply {
            setImageResource(R.drawable.app_icon_clean)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "OggiSiMangia: calendario e piatto"
        }, LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(9) })
        val headerText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        headerText.addView(TextView(this).apply {
            text = title; textSize = 19f
            setTextColor(familyInk); setTypeface(null, Typeface.BOLD)
        })
        if (subtitle.isNotBlank()) headerText.addView(TextView(this).apply {
            text = subtitle; textSize = 11f
            setTextColor(Color.rgb(88, 103, 94)); maxLines = 2
        })
        hero.addView(headerText, LinearLayout.LayoutParams(0, -2, 1f))
        // Contextual help is a non-destructive overlay, preserving unfinished edits.
        hero.addView(Button(this).apply {
            text = "? Guida"
            isAllCaps = false
            textSize = 12f
            setTextColor(familyDeep)
            contentDescription = "Apri guida della schermata " + title
            this.background = outlined(familyPale, 13f)
            setOnClickListener { GuideDialog.show(this@MainActivity, key) }
        }, LinearLayout.LayoutParams(dp(70), dp(46)).apply { marginStart = dp(5) })
        content.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
        if (key != "calendar") {
            val back = Button(this).apply {
                text = "‹  Indietro"
                textSize = 14f; isAllCaps = false; gravity = Gravity.CENTER_VERTICAL
                setTextColor(familyDeep)
                this.background = outlined(Color.WHITE, 16f)
                setOnClickListener { goBack() }
            }
            content.addView(back, LinearLayout.LayoutParams(-1, dp(44)).apply { bottomMargin = dp(6) })
        }
        navigation(key)
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

    private fun primaryAction(value: String, onClick: () -> Unit): Button {
        val btn = Button(this).apply {
            text = value; isAllCaps = false; textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(51, 181, 58), Color.rgb(18, 136, 60))).apply {
                cornerRadius = dp(19).toFloat()
            }
            elevation = dp(4).toFloat()
            setOnClickListener { hideKeyboard(); onClick() }
        }
        content.addView(btn, LinearLayout.LayoutParams(-1, dp(54)).apply {
            topMargin = dp(7); bottomMargin = dp(9)
        })
        return btn
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

    /** Fixed five-tab bottom navigation, outside the scrollable content. */
    private fun navigation(key: String) {
        val nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(3), dp(4), dp(3), dp(3))
            background = outlined(Color.WHITE, 13f)
            elevation = dp(4).toFloat()
        }
        val active = when {
            key == "calendar" -> 0
            key == "archive" || key.startsWith("detail-") -> 1
            key.startsWith("editor-") -> 2
            key == "summary" -> 3
            key == "sources" -> 4
            else -> -1
        }
        val destinations = listOf(
            "▦" to "Calendario", "♧" to "Ricette", "⊕" to "Aggiungi",
            "▤" to "Riepilogo", "•••" to "Altro"
        )
        destinations.forEachIndexed { index, destination ->
            val selected = index == active
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                contentDescription = destination.second
                isClickable = true
                isFocusable = true
                background = shape(if (selected) familyPale else Color.WHITE, 12f)
                setOnClickListener {
                    hideKeyboard()
                    when (index) {
                        0 -> showCalendar()
                        1 -> showArchive()
                        2 -> showEditor()
                        3 -> showSummary()
                        else -> showSourceSettings()
                    }
                }
            }
            cell.addView(TextView(this).apply {
                text = destination.first
                gravity = Gravity.CENTER
                textSize = if (index == 4) 18f else 23f
                setTextColor(if (selected) familyDeep else Color.rgb(92, 101, 96))
            })
            cell.addView(TextView(this).apply {
                text = destination.second
                gravity = Gravity.CENTER
                textSize = 9f
                setTextColor(if (selected) familyDeep else Color.rgb(92, 101, 96))
                setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
            })
            nav.addView(cell, LinearLayout.LayoutParams(0, dp(58), 1f).apply {
                if (index > 0) marginStart = dp(2)
            })
        }
        screenRoot.addView(nav, LinearLayout.LayoutParams(-1, -2))
    }

    private fun chooseDate(after: () -> Unit) {
        DatePickerDialog(this, { _, year, month, day ->
            selectedDate = LocalDate.of(year, month + 1, day)
            after()
        }, selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth).show()
    }

    private fun showCalendar() {
        screen("Calendario", "Organizza pranzo e cena, anche per i prossimi giorni.",
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
        primaryAction("⌕  Cerca ricetta") { showSearch() }
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
                recipeListCard(recipe,
                    if (recipe.alternative) "Alternativa · " + recipe.category else recipe.category
                ) { showDetail(recipe.id) }
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
        action("👤 " + displayName().ifBlank { "Il mio profilo" } + " · Cambia nome") {
            showNameIntroduction()
        }
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
        magazinePages.clear()
        magazinePages.addAll(photoPaths(existing?.scanPages))
        scanPageRoles.clear()
        scanPageRoles.putAll(photoRoles(existing?.scanPages))
        scanPageTexts.clear()
        nanoRequestedText = ""
        autoTitle = ""; autoIngredients = ""; autoSteps = ""
        autoPrep = ""; autoCook = ""; autoRest = ""; autoTotal = ""; autoPortions = ""; autoDifficulty = ""
        dishPhotoChoices.clear()
        dishPhotoChoices.putAll(photoChoices(existing?.dishPhotos))
        photoPreview = null
        scanSession++
        scanOcrResults.clear()
        pendingOcrPages.clear()
        processedOcrPages.clear()
        processedOcrPages.addAll(magazinePages)
        ocrStatusLabel = null
        magazinePagesContainer = null
        dishPhotosContainer = null
        screen(if (existing == null || existing.id == 0L) "Nuova ricetta da foto" else "Modifica ricetta",
            "Compila i campi che conosci. Nome, ingredienti e preparazione sono obbligatori.",
            "editor-${existing?.id ?: 0L}") {
            showEditor(existing, initialAlternative, suggestedTitle, suggestedUrl, planByDefault)
        }
        label("INFORMAZIONI GENERALI", 20f, true)
        val titleField = input("Nome del piatto *", existing?.title ?: suggestedTitle)
        editorTitle = titleField
        label("RICETTA DA RIVISTA · SCANSIONE E OCR", 19f, true)
        label("🤖 " + nano.statusText(), 13f)
        action("ⓘ Come viene usata l'AI locale?") {
            AlertDialog.Builder(this)
                .setTitle("Privacy e AI sul dispositivo")
                .setMessage("Se Gemini Nano è già disponibile, OggiSiMangia usa " +
                    "automaticamente l'AI locale per organizzare il testo OCR. " +
                    "Nessuna fotografia o ricetta viene inviata a servizi AI online. " +
                    "Se Nano non è compatibile, resta attivo l'OCR ML Kit tradizionale. " +
                    "Il download di Nano è consentito solo dopo conferma esplicita " +
                    "su rete Wi-Fi non a consumo. Controlla sempre il risultato.")
                .setPositiveButton("Ho capito", null).show()
        }
        label("Segui le fasi nell'ordine: 1) pagina intera (anche più pagine); 2) primo piano degli ingredienti; 3) preparazione; 4) tempi, difficoltà e dosi. L'OCR locale parte dopo ogni foto. Manteniamo sia le fotografie originali sia tutti i testi correggibili.", 13f)
        label("1 · PAGINA INTERA DELLA RICETTA", 15f, true)
        actionRow("📷 Fotografa pagina", { launchCamera("scan:" + MagazineRecipeText.WHOLE) },
            "🖼️ Importa pagine", { chooseMagazineFiles(MAGAZINE_PAGES_REQUEST, MagazineRecipeText.WHOLE) })
        label("2 · INGREDIENTI (foto ravvicinata)", 15f, true)
        actionRow("📷 Foto ingredienti", { launchCamera("scan:" + MagazineRecipeText.INGREDIENTS) },
            "🖼️ Importa ingredienti", { chooseMagazineFiles(MAGAZINE_PAGES_REQUEST, MagazineRecipeText.INGREDIENTS) })
        label("3 · PREPARAZIONE (foto ravvicinata)", 15f, true)
        actionRow("📷 Foto preparazione", { launchCamera("scan:" + MagazineRecipeText.STEPS) },
            "🖼️ Importa preparazione", { chooseMagazineFiles(MAGAZINE_PAGES_REQUEST, MagazineRecipeText.STEPS) })
        label("4 · TEMPI, DOSI E DIFFICOLTÀ", 15f, true)
        actionRow("📷 Foto dati", { launchCamera("scan:" + MagazineRecipeText.DETAILS) },
            "🖼️ Importa dati", { chooseMagazineFiles(MAGAZINE_PAGES_REQUEST, MagazineRecipeText.DETAILS) })
        magazinePagesContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(magazinePagesContainer)
        refreshMagazinePages()
        label("TESTO RICONOSCIUTO (MODIFICABILE)", 15f, true)
        val ocrField = input("Testo OCR delle pagine, correggibile anche prima di salvare", existing?.scanOcrText.orEmpty(), true)
        editorOcr = ocrField
        ocrStatusLabel = label("OCR pronto: ogni pagina fotografata verrà letta automaticamente e aggiunta qui sotto.", 13f)
        action("📝 Ricostruisci i campi dalla scansione (controlla prima di salvare)") {
            AlertDialog.Builder(this).setTitle("Usare il testo OCR?")
                .setMessage("I campi della ricetta saranno compilati con suggerimenti automatici. Controlla sempre dosi e preparazione: il riconoscimento può sbagliare.")
                .setPositiveButton("Compila") { _, _ -> applyMagazineSuggestions(false) }
                .setNegativeButton("Annulla", null).show()
        }
        label("FOTO DEL PIATTO · SCEGLI QUALI TENERE", 16f, true)
        label("Aggiungi le fotografie che desideri; una spunta per scegliere quali conservare. Puoi includere anche una pagina intera fra le immagini.", 13f)
        dishPhotosContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(dishPhotosContainer)
        refreshDishPhotos()
        actionRow("📷 Fotografa piatto", { launchCamera("dish") },
            "🖼️ Scegli più foto", { chooseMagazineFiles(DISH_PHOTOS_REQUEST) })
        val descriptionField = input("Descrizione breve / presentazione", existing?.description.orEmpty(), true)
        val categoryPicker = dropdown("Portata", categories, existing?.category ?: selectedCategory)
        val difficultyPicker = dropdown("Difficoltà", difficulties, existing?.difficulty ?: searchDifficulty)
        editorDifficulty = difficultyPicker
        val cuisineField = input("Tipo di cucina / provenienza (es. italiana)", existing?.cuisine.orEmpty())
        val portionsField = input("Dosi / numero persone (es. 4 persone)", existing?.portions.orEmpty())
        editorPortions = portionsField
        val authorField = input("Autore della ricetta (facoltativo)", existing?.author.orEmpty())
        val costField = input("Costo indicativo (facoltativo)", existing?.cost.orEmpty())
        val tagsField = input("Etichette (es. vegetariano, senza glutine)", existing?.tags.orEmpty())
        val allergensField = input("Allergeni (facoltativo)", existing?.allergens.orEmpty())

        label("INGREDIENTI *", 20f, true)
        label("Una riga per ingrediente, con quantità e unità di misura. Puoi aggiungere o togliere righe.", 13f)
        val ingredientRows = RecipeListFields(this, existing?.ingredients ?: searchIngredients, false)
        editorIngredients = ingredientRows
        content.addView(ingredientRows)

        label("PREPARAZIONE · PASSAGGI NUMERATI *", 20f, true)
        label("Inserisci ogni passaggio separatamente. L'app mantiene la numerazione anche se ne elimini uno.", 13f)
        val stepRows = RecipeListFields(this, existing?.instructions.orEmpty(), true)
        editorSteps = stepRows
        content.addView(stepRows)

        label("TEMPI E COTTURA", 20f, true)
        val prepField = input("Tempo di preparazione (es. 15 min)", existing?.prepTime.orEmpty())
        val cookField = input("Tempo di cottura (es. 25 min)", existing?.cookTime.orEmpty())
        val restField = input("Riposo / lievitazione (es. 2 ore)", existing?.restTime.orEmpty())
        val totalField = input("Tempo totale (facoltativo)", existing?.totalTime.orEmpty())
        editorPrep = prepField; editorCook = cookField
        editorRest = restField; editorTotal = totalField
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
        content.addView(photoPreview, LinearLayout.LayoutParams(dp(124), dp(100)).apply { bottomMargin = dp(6) })
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
            if (magazinePages.isEmpty() && !ingredientRows.requireOne("Aggiungi almeno un ingrediente")) return@action
            if (magazinePages.isEmpty() && stepRows.entries().isEmpty() && originalField?.text.isNullOrBlank()) {
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
                scanPages = JSONArray().apply {
                    magazinePages.forEach { path ->
                        put(JSONObject().put("path", path)
                            .put("role", scanPageRoles[path] ?: MagazineRecipeText.WHOLE))
                    }
                }.toString(),
                dishPhotos = JSONObject(dishPhotoChoices as Map<*, *>).toString(),
                scanOcrText = ocrField.text.toString().trim(),
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


    // Old archives contain ["path.jpg"]; new ones contain {path,role}.
    private fun photoPaths(json: String?): List<String> = try {
        val data = JSONArray(json ?: "[]")
        (0 until data.length()).mapNotNull { at ->
            (data.optJSONObject(at)?.optString("path") ?: data.optString(at))
                .takeIf(String::isNotBlank)
        }
    } catch (_: Exception) { emptyList() }

    private fun photoRoles(json: String?): Map<String, String> = try {
        val data = JSONArray(json ?: "[]")
        (0 until data.length()).mapNotNull { at ->
            data.optJSONObject(at)?.let { obj ->
                obj.optString("path").takeIf(String::isNotBlank)?.let { path ->
                    path to obj.optString("role", MagazineRecipeText.WHOLE)
                }
            }
        }.toMap()
    } catch (_: Exception) { emptyMap() }

    private fun photoChoices(json: String?): Map<String, Boolean> = try {
        val data = JSONObject(json ?: "{}")
        data.keys().asSequence().associateWith { data.optBoolean(it, false) }
    } catch (_: Exception) { emptyMap() }

    private fun chooseMagazineFiles(request: Int, role: String = MagazineRecipeText.WHOLE) {
        pendingGalleryRole = role
        startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }, request)
    }

    private fun previewThumb(path: String, label: String): ImageView = ImageView(this).apply {
        val options = BitmapFactory.Options().apply { inSampleSize = 4 }
        setImageBitmap(BitmapFactory.decodeFile(path, options))
        scaleType = ImageView.ScaleType.CENTER_CROP
        contentDescription = label
    }

    private fun refreshMagazinePages() {
        val host = magazinePagesContainer ?: return
        host.removeAllViews()
        magazinePages.forEachIndexed { index, path ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(previewThumb(path, "Pagina " + (index + 1) + " della rivista"),
                LinearLayout.LayoutParams(dp(70), dp(92)))
            val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            info.addView(TextView(this).apply {
                text = "Foto " + (index + 1) + " · " +
                    when (scanPageRoles[path]) {
                        MagazineRecipeText.INGREDIENTS -> "Ingredienti"
                        MagazineRecipeText.STEPS -> "Preparazione"
                        MagazineRecipeText.DETAILS -> "Tempi, dosi e difficoltà"
                        else -> "Pagina intera"
                    }
                textSize = 14f
                setTextColor(familyInk)
            })
            info.addView(Button(this).apply {
                text = "✕ Elimina pagina"; isAllCaps = false
                setOnClickListener {
                    magazinePages.remove(path)
                    scanPageRoles.remove(path)
                    scanPageTexts.remove(path)
                    pendingOcrPages.remove(path)
                    scanOcrResults.remove(path)
                    processedOcrPages.remove(path)
                    appendCompletedOcrPages()
                    refreshMagazinePages()
                }
            })
            info.addView(Button(this).apply {
                text = "＋ Usa come immagine"; isAllCaps = false
                setOnClickListener {
                    dishPhotoChoices[path] = true
                    if (selectedPhoto.isBlank()) { selectedPhoto = path; refreshPhotoPreview() }
                    refreshDishPhotos()
                }
            })
            row.addView(info, LinearLayout.LayoutParams(0, -2, 1f))
            host.addView(row)
        }
    }

    private fun refreshDishPhotos() {
        val host = dishPhotosContainer ?: return
        host.removeAllViews()
        dishPhotoChoices.toMap().forEach { (path, saved) ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(previewThumb(path, "Fotografia del piatto"), LinearLayout.LayoutParams(dp(72), dp(85)))
            val options = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            options.addView(CheckBox(this).apply {
                text = "Conserva questa foto"
                isChecked = saved
                setOnCheckedChangeListener { _, enabled ->
                    dishPhotoChoices[path] = enabled
                    if (!enabled && selectedPhoto == path) {
                        selectedPhoto = dishPhotoChoices.entries.firstOrNull { it.value }?.key ?: ""
                        refreshPhotoPreview()
                    }
                }
            })
            options.addView(Button(this).apply {
                text = "Usa come copertina"; isAllCaps = false
                setOnClickListener {
                    dishPhotoChoices[path] = true
                    selectedPhoto = path
                    refreshPhotoPreview()
                    refreshDishPhotos()
                }
            })
            options.addView(Button(this).apply {
                text = "✕ Rimuovi foto"; isAllCaps = false
                setOnClickListener {
                    dishPhotoChoices.remove(path)
                    if (selectedPhoto == path) {
                        selectedPhoto = dishPhotoChoices.entries.firstOrNull { it.value }?.key ?: ""
                        refreshPhotoPreview()
                    }
                    refreshDishPhotos()
                }
            })
            row.addView(options, LinearLayout.LayoutParams(0, -2, 1f))
            host.addView(row)
        }
    }

    private fun addMagazineImage(uri: Uri, kind: String) {
        try {
            val path = compressPhoto(uri, if (kind == "scan") 2000 else 960)
            if (kind.startsWith("scan")) {
                if (magazinePages.size >= 20) {
                    File(path).delete()
                    Toast.makeText(this, "Massimo 20 pagine per ricetta", Toast.LENGTH_LONG).show()
                    return
                }
                magazinePages.add(path)
                scanPageRoles[path] = kind.substringAfter(':', MagazineRecipeText.WHOLE)
                pendingOcrPages.add(path)
                refreshMagazinePages()
                updateOcrStatus()
                recognizeMagazinePage(path, magazinePages.size, scanSession)
            } else {
                dishPhotoChoices[path] = true
                if (selectedPhoto.isBlank()) { selectedPhoto = path; refreshPhotoPreview() }
                refreshDishPhotos()
            }
        } catch (_: Exception) {
            Toast.makeText(this, "Immagine non leggibile: riprova", Toast.LENGTH_LONG).show()
        }
    }

    /** OCR page headers include photo roles so user corrections are also re-interpreted. */
    private fun editableScanSections(raw: String): List<MagazineRecipeText.Section> {
        val marker = Regex("^Pagina\\s+[0-9]+\\s*\\((intera|ingredienti|preparazione|dettagli)\\)\\s*$",
            RegexOption.IGNORE_CASE)
        val sections = mutableListOf<MagazineRecipeText.Section>()
        var role = MagazineRecipeText.WHOLE
        val body = StringBuilder()
        fun flush() {
            if (body.isNotBlank()) sections.add(MagazineRecipeText.Section(role, body.toString()))
            body.clear()
        }
        raw.lines().forEach { line ->
            val match = marker.matchEntire(line.trim())
            if (match != null) { flush(); role = match.groupValues[1].lowercase() }
            else body.append(line).append('\n')
        }
        flush()
        return sections
    }

    private fun applyMagazineSuggestions(onlyMissing: Boolean) {
        val text = editorOcr?.text?.toString().orEmpty()
        if (text.isBlank()) {
            if (!onlyMissing) Toast.makeText(this,
                "Nessun testo OCR disponibile. Le fotografie restano salvabili.",
                Toast.LENGTH_LONG).show()
            return
        }
        val sections = editableScanSections(text)
        val guess = MagazineRecipeText.suggestSections(sections)
        fun update(field: EditText?, value: String, previous: String, save: (String) -> Unit) {
            if (value.isNotBlank() && field != null &&
                (!onlyMissing || field.text.isNullOrBlank() ||
                    (previous.isNotBlank() && field.text.toString() == previous))) {
                field.setText(value)
                save(value)
            }
        }
        update(editorTitle, guess.title, autoTitle) { autoTitle = it }
        if (guess.ingredients.isNotBlank() && editorIngredients != null &&
            (!onlyMissing || editorIngredients!!.entries().isEmpty() ||
                (autoIngredients.isNotBlank() && editorIngredients!!.value() == autoIngredients))) {
            editorIngredients?.replaceValues(guess.ingredients)
            autoIngredients = guess.ingredients
        }
        if (guess.steps.isNotBlank() && editorSteps != null &&
            (!onlyMissing || editorSteps!!.entries().isEmpty() ||
                (autoSteps.isNotBlank() && editorSteps!!.value() == autoSteps))) {
            editorSteps?.replaceValues(guess.steps)
            autoSteps = guess.steps
        }
        update(editorPrep, guess.prepTime, autoPrep) { autoPrep = it }
        update(editorCook, guess.cookTime, autoCook) { autoCook = it }
        update(editorRest, guess.restTime, autoRest) { autoRest = it }
        update(editorTotal, guess.totalTime, autoTotal) { autoTotal = it }
        update(editorPortions, guess.portions, autoPortions) { autoPortions = it }
        if (guess.difficulty.isNotBlank()) editorDifficulty?.let { picker ->
            if (!onlyMissing || autoDifficulty == picker.selectedItem?.toString() ||
                (autoDifficulty.isBlank() && picker.selectedItem?.toString() == searchDifficulty)) {
                val position = difficulties.indexOf(guess.difficulty)
                if (position >= 0) {
                    picker.setSelection(position)
                    autoDifficulty = guess.difficulty
                }
            }
        }
    }

    /** Gemini Nano suggestions are never written to the saved raw OCR.
     * A field that the user changed after OCR was generated stays untouched.
     */
    private fun applyNanoGuess(guess: MagazineRecipeText.Guess) {
        fun offer(field: EditText?, value: String, previous: String, remember: (String)->Unit) {
            if (field == null || value.isBlank()) return
            val current = field.text.toString()
            if (current.isBlank() || (previous.isNotBlank() && current == previous)) {
                field.setText(value)
                remember(value)
            }
        }
        offer(editorTitle, guess.title, autoTitle) { autoTitle = it }
        if (guess.ingredients.isNotBlank() && editorIngredients != null &&
            (editorIngredients!!.entries().isEmpty() ||
             (autoIngredients.isNotBlank() && editorIngredients!!.value() == autoIngredients))) {
            editorIngredients?.replaceValues(guess.ingredients)
            autoIngredients = guess.ingredients
        }
        if (guess.steps.isNotBlank() && editorSteps != null &&
            (editorSteps!!.entries().isEmpty() ||
             (autoSteps.isNotBlank() && editorSteps!!.value() == autoSteps))) {
            editorSteps?.replaceValues(guess.steps)
            autoSteps = guess.steps
        }
        offer(editorPrep, guess.prepTime, autoPrep) { autoPrep = it }
        offer(editorCook, guess.cookTime, autoCook) { autoCook = it }
        offer(editorRest, guess.restTime, autoRest) { autoRest = it }
        offer(editorTotal, guess.totalTime, autoTotal) { autoTotal = it }
        offer(editorPortions, guess.portions, autoPortions) { autoPortions = it }
        if (guess.difficulty.isNotBlank()) editorDifficulty?.let { picker ->
            if (autoDifficulty == picker.selectedItem?.toString() ||
                (autoDifficulty.isBlank() && picker.selectedItem?.toString() == searchDifficulty)) {
                difficulties.indexOf(guess.difficulty).takeIf { it >= 0 }?.let {
                    picker.setSelection(it)
                    autoDifficulty = guess.difficulty
                }
            }
        }
    }

    private fun tryNanoEnhance(session: Int) {
        if (!nano.enabled || nano.state != LocalNanoAssistant.State.READY) return
        val raw = editorOcr?.text?.toString().orEmpty()
        if (raw.isBlank() || raw == nanoRequestedText) return
        nanoRequestedText = raw
        val editor = editorOcr
        nano.improveOcr(raw) { guess ->
            if (session == scanSession && currentPageKey.startsWith("editor-") &&
                editor === editorOcr && guess != null) {
                applyNanoGuess(guess)
                ocrStatusLabel?.text = "✓ OCR migliorato con AI locale Gemini Nano. " +
                    "Verifica i campi: puoi correggere ogni informazione."
            }
        }
    }

    private fun showAiSettings() {
        screen("AI ricerca e OCR", "Nessun assistente vocale; ricerca e OCR disponibili.",
            "ai-settings") { showAiSettings() }
        label("✨ AI per ricerca e riconoscimento ricette", 19f, true)
        label(nano.statusText(), 15f, true)
        label(DeviceAiSupport.report(nano), 14f)
        val enabled = CheckBox(this).apply {
            text = "Usa Gemini Nano per ricerca testuale e OCR, quando disponibile"
            isChecked = nano.enabled
            setOnCheckedChangeListener { _, checked ->
                nano.enabled = checked
                showAiSettings()
            }
        }
        content.addView(enabled)
        label("Le foto vengono elaborate dall'OCR ML Kit sul telefono. " +
            "Gemini Nano, se supportato, riorganizza soltanto il testo OCR, " +
            "senza caricare immagini o ricette sui server AI. " +
            "Non è attiva alcuna AI online, né è necessaria una chiave API.", 14f)
        label("Il modello può essere grande: NON viene mai avviato un download " +
            "da OggiSiMangia senza il tuo consenso esplicito e una connessione Wi-Fi " +
            "non a consumo. Non è possibile utilizzare automaticamente ogni AI " +
            "proprietaria del telefono se il produttore non offre API compatibili.", 13f)
        action("↻ Ricontrolla compatibilità del telefono") {
            nano.refresh()
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (currentPageKey == "ai-settings") showAiSettings()
            }, 1800L)
        }
        if (nano.enabled && nano.state == LocalNanoAssistant.State.DOWNLOADABLE) {
            action("⬇ Installa il modello Gemini Nano (conferma richiesta)") {
                if (!nano.isUnmeteredWifi()) {
                    AlertDialog.Builder(this)
                        .setTitle("Serve una rete Wi-Fi non a consumo")
                        .setMessage("Collega il telefono a una rete Wi-Fi con Internet " +
                            "non a consumo, poi riprova. Nessun download è stato avviato.")
                        .setPositiveButton("OK", null).show()
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("Scaricare Gemini Nano?")
                        .setMessage("Il modello è gratuito ma può occupare molto spazio. " +
                            "Sarà scaricato usando la connessione Wi-Fi attuale. " +
                            "Nessuna foto verrà caricata online. Confermi il download?")
                        .setNegativeButton("Annulla", null)
                        .setPositiveButton("Sì, scarica") { _, _ ->
                            nano.downloadAfterConsent { success ->
                                if (currentPageKey == "ai-settings") showAiSettings()
                                Toast.makeText(this,
                                    if (success) "Modello Nano pronto all'uso"
                                    else "Download non completato. OCR ML Kit resta disponibile.",
                                    Toast.LENGTH_LONG).show()
                            }
                        }.show()
                }
            }
        }
        action("← Torna alle fonti di ricerca") { showSourceSettings() }
    }

    private fun updateOcrStatus() {
        val total = magazinePages.size
        val remaining = pendingOcrPages.size
        ocrStatusLabel?.text = if (remaining > 0) {
            "⏳ OCR in corso su $remaining pagina/e di $total. Il testo compare qui appena pronto."
        } else if (total > 0) {
            "✓ OCR completato. Controlla il testo: puoi correggerlo liberamente."
        } else {
            "Fotografa una pagina e il riconoscimento partirà immediatamente."
        }
    }

    /** Append in capture order; text already corrected manually is not overwritten. */
    private fun appendCompletedOcrPages() {
        while (true) {
            val nextPath = magazinePages.firstOrNull { it !in processedOcrPages } ?: break
            val text = scanOcrResults.remove(nextPath) ?: break
            processedOcrPages.add(nextPath)
            if (text.isNotBlank()) {
                val current = editorOcr?.text?.toString().orEmpty()
                val page = magazinePages.indexOf(nextPath) + 1
                scanPageTexts[nextPath] = text
                val role = scanPageRoles[nextPath] ?: MagazineRecipeText.WHOLE
                editorOcr?.append((if (current.isBlank()) "" else "\n\n") +
                    "Pagina $page ($role)\n" + text.trim())
                applyMagazineSuggestions(true)
            }
        }
        updateOcrStatus()
        if (pendingOcrPages.isEmpty()) tryNanoEnhance(scanSession)
    }

    private fun recognizeMagazinePage(path: String, page: Int, session: Int) {
        val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val input = InputImage.fromFilePath(this, Uri.fromFile(File(path)))
            client.process(input)
                .addOnSuccessListener { result ->
                    if (session == scanSession && path in magazinePages) {
                        pendingOcrPages.remove(path)
                        scanOcrResults[path] = result.text
                        appendCompletedOcrPages()
                        if (result.text.isBlank())
                            Toast.makeText(this, "Pagina $page fotografata, testo non rilevato.", Toast.LENGTH_LONG).show()
                    }
                }
                .addOnFailureListener {
                    if (session == scanSession && path in magazinePages) {
                        pendingOcrPages.remove(path)
                        scanOcrResults[path] = ""
                        appendCompletedOcrPages()
                        Toast.makeText(this, "OCR pagina $page non disponibile; foto conservata.", Toast.LENGTH_LONG).show()
                    }
                }
                .addOnCompleteListener { client.close() }
        } catch (_: Exception) {
            client.close()
            if (session == scanSession && path in magazinePages) {
                pendingOcrPages.remove(path)
                scanOcrResults[path] = ""
                appendCompletedOcrPages()
            }
        }
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

    private fun launchCamera(kind: String = "cover") {
        try {
            val folder = File(cacheDir, "camera").apply { mkdirs() }
            val file = File(folder, UUID.randomUUID().toString() + ".jpg")
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this, packageName + ".fileprovider", file)
            pendingCameraPath = file.absolutePath
            pendingCameraKind = kind
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

    private fun showSearch(autoSearch: Boolean = false) {
        screen("Cerca ricetta", "Scopri nuove ricette dai tuoi ingredienti.",
            "search") { showSearch() }
        label("Giorno scelto: " + selectedDate.format(dateFormatter), 16f, true)
        if (searchForDay) label(selectedMeal + " · " + if (selectedAlternative) "Alternativa" else "Principale", 16f, true)
        actionRow("🔗 Ho un link", { showRecipeLink() }, "📅 Pasti del giorno", { showCalendar() })
        label("✨ AI per cercare ricette (testo, senza microfono)", 17f, true)
        label("Scrivi liberamente cosa vuoi cucinare. L'AI locale, se supportata " +
            "dal telefono, ti aiuta a riconoscere gli ingredienti. Altrimenti l'app " +
            "li ricava direttamente dal testo. Potrai controllarli prima della ricerca.", 13f)
        val naturalRequest = EditText(this).apply {
            hint = "Es. Vorrei pasta con melanzane e pomodoro"
            contentDescription = "Descrivi in testo la ricetta che cerchi"
            setSingleLine(false)
            maxLines = 3
            textSize = 15f
            setText(searchNaturalPrompt)
            setPadding(dp(12), dp(9), dp(12), dp(9))
            background = outlined(Color.WHITE, 13f)
        }
        content.addView(naturalRequest, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(5)
        })
        action("✨ AI · Interpreta richiesta scritta") {
            val request = naturalRequest.text.toString().trim()
            if (request.isBlank()) {
                naturalRequest.error = "Scrivi cosa vorresti cucinare"
            } else {
                searchNaturalPrompt = request
                val fallback = RecipeSearchIntent.ingredients(request)
                fun populate(values: List<String>) {
                    if (currentPageKey != "search" || isFinishing || isDestroyed) return
                    if (values.isEmpty()) {
                        Toast.makeText(this,
                            "Indica almeno un ingrediente, per esempio pasta e melanzane.",
                            Toast.LENGTH_LONG).show()
                        return
                    }
                    searchIngredients = values.joinToString(", ")
                    showSearch()
                    Toast.makeText(this,
                        "Ingredienti individuati: ${values.joinToString(", ")}. Controlla e premi Cerca ricette.",
                        Toast.LENGTH_LONG).show()
                }
                nano.suggestSearchIngredients(request) { proposed ->
                    runOnUiThread { populate(proposed ?: fallback) }
                }
            }
        }
        label("Quali ingredienti hai a disposizione?", 17f, true)
        label("Primo campo: ingrediente principale obbligatorio (es. pasta, riso, pollo, patate). Massimo 10 ingredienti: gli altri sono preferenze; le ricette con corrispondenza parziale vengono segnalate.", 13f)
        val ingredients = IngredientInputs(this, searchIngredients) { searchIngredients = it }
        content.addView(ingredients)
        val category = dropdown("Portata", categories, searchCategory)
        val type = dropdown("Tipologia alimentare", RecipeTypeFilter.options, searchType)
        val difficulty = dropdown("Difficoltà desiderata", difficulties, searchDifficulty)
        val duration = dropdown("Tempo a disposizione", durations, searchDuration)
        action("Priorità dei siti e nuove fonti") {
            searchIngredients = ingredients.values().joinToString(", ")
            searchCategory = category.selectedItem.toString()
            searchType = type.selectedItem.toString()
            searchDifficulty = difficulty.selectedItem.toString()
            searchDuration = duration.selectedItem.toString()
            showSourceSettings()
        }
        label("Ingrediente principale obbligatorio. Le ricette con tutti gli ingredienti sono preferite; quelle parziali mostrano le differenze. Il filtro vegetariano/vegano e i filtri allergeni sono indicativi, non certificazioni.", 13f)

        val searchButton = primaryAction("⌕  Cerca ricette") search@{
            searchIngredients = ingredients.query() ?: return@search
            searchResultGeneration++
            val generation = searchResultGeneration
            siteMorePages.clear()
            siteResultsExhausted.clear()
            lastSearchResults = emptyList()
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
                        RecipeRanking.orderFlexible(unique, selectedIngredients)
                    } finally { executor.shutdownNow() }
                } catch (_: Exception) { emptyList() }
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    progress.dismiss()
                    if (generation == searchResultGeneration) showRecipeResults(results)
                }
            }.start()
        }
        if (autoSearch) searchButton.post { if (searchButton.isAttachedToWindow) searchButton.performClick() }
        actionRow("↻ Azzera ingredienti", {
            searchIngredients = ""
            showSearch()
        }, "📚 Ricettario", { showArchive() })
        action("＋ Nuova ricetta") { showEditor() }
    }

    private fun showSourceSettings() {
        screen("Fonti di ricerca", "L'ordine viene memorizzato per le prossime ricerche.",
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
                setPadding(dp(7), dp(6), dp(7), dp(6))
                background = outlined(Color.WHITE, 15f)
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
        action("❔ Guida completa: spiegazioni di schede e pulsanti") {
            GuideDialog.show(this)
        }
        action("✨ Impostazioni AI ricerca e OCR") { showAiSettings() }
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


    private fun filterByType(items: List<WebRecipe>): List<WebRecipe> {
        if (searchType == "Qualsiasi") return items
        return items.filter {
            RecipeTypeFilter.match(it.ingredients, searchType) != false
        }
    }

    /** First show the configured sites separately in their saved priority order. */
    private fun showRecipeResults(items: List<WebRecipe>, offset: Int = 0) {
        lastSearchResults = items
        lastSearchOffset = 0
        screen("Risultati per sito", "Ricette raggruppate per fonte, senza taglio globale a 10.",
            "recipe-sites") { showRecipeResults(lastSearchResults) }
        val visible = filterByType(items)
        val confirmedCount = visible.count { it.verified }
        val completeCount = visible.count { it.verified &&
            RecipeValidation.containsEvery(it.ingredients, searchIngredients) }
        label("Ingrediente principale: " + RecipeValidation.mainIngredient(searchIngredients), 15f, true)
        label("Tipologia richiesta: " + searchType, 14f, true)
        RecipeTypeFilter.warning(searchType).takeIf { it.isNotBlank() }?.let { label("⚠ " + it, 13f) }
        label(completeCount.toString() + " ricette con tutti gli ingredienti · " +
            (confirmedCount - completeCount) + " con ingredienti parziali", 14f)
        label(confirmedCount.toString() + " schede con ingredienti leggibili · " +
            (visible.size - confirmedCount) + " collegamenti da controllare", 16f, true)
        label("Ricette singole, ordinate per fonte e per ingrediente principale. La dicitura 'Da controllare' appare solo se non è stato possibile leggere la pagina: gli ingredienti secondari restano da verificare.", 13f)
        actionRow("🔎 Modifica ricerca", { showSearch() },
            "⚙ Fonti e priorità", { showSourceSettings() })
        if (visible.isEmpty()) {
            label("Nessuna ricetta verificata per gli ingredienti indicati. Alcuni siti potrebbero bloccare la lettura automatica: puoi richiedere altre pagine per un sito oppure aprirlo manualmente.", 14f)
        }
        if (RecipeSources.sites.isEmpty()) {
            action("＋ Aggiungi un sito di ricette") { showSourceSettings() }
        }
        RecipeSources.sites.entries.forEachIndexed { index, (name, domain) ->
            val siteRecipes = RecipeSourcePages.forSource(visible, domain)
            label((index + 1).toString() + "ª fonte · " + name, 18f, true)
            label(domain + " · " + siteRecipes.count { it.verified &&
                RecipeValidation.containsEvery(it.ingredients, searchIngredients) } +
                " complete · " + siteRecipes.count { it.verified &&
                !RecipeValidation.containsEvery(it.ingredients, searchIngredients) } +
                " parziali · " + siteRecipes.count { !it.verified } + " da controllare", 13f)
            if (siteRecipes.isNotEmpty()) {
                siteRecipes.take(3).forEach { item ->
                    label((if (item.verified) "✅ " else "🔎 ") + item.title, 14f)
                }
                action("📚 Sfoglia " + name + " (" + siteRecipes.size + " proposte)") {
                    showSiteRecipeResults(name, domain, 0)
                }
            } else {
                label("Non ho recuperato proposte da questa fonte: il sito o il motore di ricerca potrebbero limitare l'accesso. Puoi cercare su altre pagine o aprire il sito.", 13f)
            }
            if (name !in siteResultsExhausted) {
                action("＋ Cerca altre ricette su " + name) {
                    searchMoreRecipesOnSite(name, domain)
                }
            }
            action("🌐 Cerca manualmente su " + name) {
                openWeb(RecipeSearchQueries.bing(domain, searchIngredients))
            }
        }
    }

    /** Pagination belongs to one website, not the complete search. */
    private fun showSiteRecipeResults(site: String, domain: String, offset: Int = 0) {
        val items = RecipeSourcePages.forSource(filterByType(lastSearchResults), domain)
        if (items.isEmpty()) {
            showRecipeResults(lastSearchResults)
            return
        }
        val start = offset.coerceIn(0, items.lastIndex)
        val page = items.drop(start).take(10)
        val end = start + page.size
        lastSearchOffset = start
        val scroll = ScrollView(this)
        val rows = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(10))
        }
        scroll.addView(rows)
        val popup = AlertDialog.Builder(this)
            .setTitle(site + " · " + (start + 1) + "–" + end + " di " + items.size)
            .setView(scroll)
            .setNegativeButton("Tutti i siti") { _, _ -> showRecipeResults(lastSearchResults) }
            .setPositiveButton("Prossime 10") { _, _ ->
                if (end < items.size) showSiteRecipeResults(site, domain, end)
                else searchMoreRecipesOnSite(site, domain)
            }
            .setNeutralButton("Precedenti 10") { _, _ ->
                showSiteRecipeResults(site, domain, (start - 10).coerceAtLeast(0))
            }.create()
        for (recipe in page) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(12))
                background = outlined(Color.WHITE, 15f)
            }
            row.addView(TextView(this).apply {
                text = (if (recipe.verified) "✅ " else "🔎 ") + recipe.title
                textSize = 17f
                setTypeface(null, Typeface.BOLD); setTextColor(familyDeep)
            })
            row.addView(TextView(this).apply {
                val found = RecipeValidation.foundIngredients(recipe.ingredients, searchIngredients)
                val pending = RecipeValidation.missingIngredients(recipe.ingredients, searchIngredients)
                text = if (recipe.verified) {
                    (if (pending.isEmpty()) "✓ Corrispondenza completa. " else "⚠ Corrispondenza parziale. ") +
                        "Ingredienti trovati: " + found.joinToString(", ") +
                        (if (pending.isEmpty()) "" else "\nNon trovati: " + pending.joinToString(", "))
                } else {
                    "Da controllare: pagina non leggibile durante la ricerca. " +
                        "Ingrediente principale indicato dal titolo: " +
                        RecipeValidation.mainIngredient(searchIngredients) +
                        (if (pending.isNotEmpty()) "\nIngredienti da verificare: " + pending.joinToString(", ") else "")
                }
                textSize = 13f
                setTextColor(if (recipe.verified) familyDeep else Color.rgb(140, 84, 20))
            })
            val differences = if (recipe.verified) RecipePreferences.notices(
                recipe, searchCategory, searchDifficulty, searchDuration) else emptyList()
            if (recipe.verified && searchType != "Qualsiasi") row.addView(TextView(this).apply {
                text = "Tipologia: " + searchType + " (verifica sull'elenco pubblicato)"
                textSize = 12f; setTextColor(familyDeep)
            })
            if (differences.isNotEmpty()) row.addView(TextView(this).apply {
                text = differences.joinToString("\n") { "⚠ " + it }
                textSize = 13f; setTextColor(Color.rgb(139, 79, 15))
            })
            if (recipe.imageUrl.isNotBlank()) {
                val preview = ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription = "Anteprima: " + recipe.title
                }
                row.addView(preview, LinearLayout.LayoutParams(dp(108), dp(90)).apply { topMargin = dp(6); bottomMargin = dp(5) })
                fetchPreview(recipe.imageUrl, preview)
            }
            row.addView(TextView(this).apply {
                text = listOfNotNull(
                    recipe.minutes?.let { "⏱ " + it + " min" },
                    recipe.difficulty.takeIf { it.isNotBlank() },
                    recipe.rating?.let { "⭐ " + it },
                    recipe.feedbackCount?.let { it.toString() + " voti/recensioni" }
                ).joinToString(" · ")
                textSize = 13f; setTextColor(Color.DKGRAY)
            })
            if (recipe.summary.isNotBlank()) row.addView(TextView(this).apply {
                text = recipe.summary; textSize = 14f
                setPadding(0, dp(5), 0, dp(5))
            })
            row.addView(Button(this).apply {
                text = "Apri ricetta · Salva o pianifica →"
                isAllCaps = false
                setTextColor(Color.WHITE)
                background = shape(familyDeep, 13f)
                setOnClickListener { popup.dismiss(); showOnlineDetail(recipe) }
            }, LinearLayout.LayoutParams(-1, dp(44)))
            rows.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        }
        popup.show()
        popup.getButton(AlertDialog.BUTTON_POSITIVE).text =
            if (end < items.size) "Prossime 10" else "Cerca altre"
        popup.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled =
            end < items.size || site !in siteResultsExhausted
        popup.getButton(AlertDialog.BUTTON_NEUTRAL).visibility =
            if (start > 0) View.VISIBLE else View.GONE
        popup.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.heightPixels * 0.83).toInt()
        )
    }

    /** Discover more on the selected website only; all other results remain available. */
    private fun searchMoreRecipesOnSite(site: String, domain: String) {
        if (RecipeSources.sites[site] != domain) return
        val generation = searchResultGeneration
        val nextPage = siteMorePages[site] ?: 1
        val ingredients = searchIngredients
        val progress = AlertDialog.Builder(this)
            .setTitle("Altre ricette da " + site)
            .setMessage("Consulto la pagina successiva di questa fonte...")
            .setCancelable(false).create()
        progress.show()
        Thread {
            val more = try { RecipeDiscovery.searchNextPage(ingredients, site, domain, nextPage) }
                catch (_: Exception) { emptyList() }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                progress.dismiss()
                if (generation != searchResultGeneration || RecipeSources.sites[site] != domain) return@runOnUiThread
                siteMorePages[site] = nextPage + 1
                val old = lastSearchResults.map {
                    it.url.substringBefore('?').substringBefore('#').trimEnd('/')
                }.toSet()
                val fresh = more.filter {
                    it.url.substringBefore('?').substringBefore('#').trimEnd('/') !in old
                }
                if (fresh.isEmpty()) {
                    siteResultsExhausted.add(site)
                    Toast.makeText(this, "Non sono disponibili altre ricette verificate su " + site +
                        ". Prova la ricerca manuale.", Toast.LENGTH_LONG).show()
                } else {
                    val oldCount = lastSearchResults.count { RecipeSources.belongs(it.url, domain) }
                    lastSearchResults = RecipeRanking.orderFlexible(lastSearchResults + fresh, ingredients)
                    showSiteRecipeResults(site, domain, (oldCount / 10) * 10)
                }
                if (fresh.isEmpty()) showRecipeResults(lastSearchResults)
            }
        }.start()
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
                val publishedIngredients = result.ingredients.ifBlank { recipe.ingredients }
                val fullMatch = publishedIngredients.isNotBlank() &&
                    RecipeValidation.containsEvery(publishedIngredients, searchIngredients)
                if (!imported && publishedIngredients.isNotBlank()) {
                    label("Ingredienti trovati: " +
                        RecipeValidation.foundIngredients(publishedIngredients, searchIngredients)
                            .joinToString(", ").ifBlank { "nessuno di quelli richiesti" }, 13f)
                    val missing = RecipeValidation.missingIngredients(publishedIngredients, searchIngredients)
                    if (missing.isNotEmpty()) label("Ingredienti non trovati: " + missing.joinToString(", "), 13f)
                }
                if (!recipe.verified && !fullMatch) {
                    label("⚠ Ingredienti non verificati per tutti i termini cercati. Controlla la ricetta originale prima di salvarla o assegnarla al calendario.", 14f, true)
                } else if (!recipe.verified && fullMatch) {
                    label("✅ Ingredienti richiesti trovati nella scheda letta ora.", 14f)
                }
                if (!imported && (recipe.verified || fullMatch)) {
                    RecipePreferences.notices(recipe, searchCategory, searchDifficulty, searchDuration)
                        .forEach { label("⚠ " + it, 13f) }
                }
                if (!imported) {
                    label("Link diretto alla ricetta:", 14f, true)
                    action("🌐 Apri la ricetta originale su " + recipe.source) {
                        openWeb(recipe.url)
                    }
                    label(recipe.url, 12f)
                    if (searchType != "Qualsiasi") label("Tipologia richiesta: " + searchType +
                        ". " + RecipeTypeFilter.warning(searchType), 13f)
                }
                label("Giorno scelto: " + selectedDate.format(dateFormatter), 14f)
                val imageUrl = result.imageUrl.ifBlank { recipe.imageUrl }
                if (imageUrl.isNotBlank()) {
                    val photo = ImageView(this).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        contentDescription = "Foto della ricetta da " + recipe.source
                    }
                    content.addView(photo, LinearLayout.LayoutParams(-1, dp(132)))
                    fetchPreview(imageUrl, photo)
                    label("Foto: " + recipe.source, 12f)
                }
                val details = onlineDraft(recipe, result, "Entrambe")
                showRecipeMetadata(details)
                action(if (fullMatch || imported) "📅 Aggiungi al calendario" else "📅 Controlla e pianifica la ricetta") {
                    if (!fullMatch && !imported) {
                        prepareOnlineDraft(recipe, result, "Entrambe") { draft ->
                            showEditor(draft, planByDefault = true)
                        }
                        return@action
                    }
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
                label(publishedIngredients.ifBlank { "Non disponibili: consulta l'originale." })
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
                    action("← Torna ai risultati") { showRecipeResults(lastSearchResults) }
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

    /** Compact day overview and statistics derived from existing records. */
    private fun showSummary(statistics: Boolean = false) {
        screen("Riepilogo", "I tuoi giorni e pasti pianificati.", "summary") { showSummary(statistics) }
        actionRow("Giorni pianificati", { showSummary(false) },
            "Statistiche", { showSummary(true) })
        val all = db.archive("", "Tutte", "Tutte", "Data (recenti)")
        val scheduled = all.filter { it.day.isNotBlank() }
        val days = scheduled.groupBy { it.day }.toSortedMap(compareByDescending { it })
        val lunches = scheduled.count { it.meal == "Pranzo" || it.meal == "Entrambi" }
        val dinners = scheduled.count { it.meal == "Cena" || it.meal == "Entrambi" }
        label("📅  Giorni pianificati: " + days.size, 18f, true)
        label("☀  Pranzi pianificati: " + lunches, 17f, true)
        label("🌙  Cene pianificate: " + dinners, 17f, true)
        if (statistics) {
            label("📚  Ricette nel ricettario: " + all.size, 17f, true)
            label("🍽  Pasti complessivi: " + (lunches + dinners), 17f, true)
            label("Le ricette alternative sono comprese nei conteggi.", 13f)
        } else {
            label("Ultimi giorni", 19f, true)
            if (days.isEmpty()) label("Nessun giorno pianificato: scegli una data nel calendario.", 14f)
            days.entries.take(20).forEach { (day, recipes) ->
                action("📅 " + day + "    " + recipes.joinToString(" · ") { it.title }) {
                    selectedDate = LocalDate.parse(day)
                    showCalendar()
                }
            }
        }
    }

    private fun showArchive(search: String = "", category: String = "Tutte",
                            difficulty: String = "Tutte", order: String = "Nome A–Z", ingredientQuery: String = "") {
        screen("Le mie ricette", "Ricette salvate sul telefono, consultabili anche offline.",
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
            recipeListCard(r, r.category + " · " + r.difficulty + " · " + r.duration) {
                showDetail(r.id)
            }
            action("📅 Pianifica «" + r.title + "»") { planSavedRecipe(r) }
        }
        action("＋ Nuova ricetta") { showEditor() }
    }

    /** Compact card used by both meal calendar and saved recipe lists. */
    private fun recipeListCard(recipe: Recipe, subtitle: String, onClick: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(9), dp(7), dp(9), dp(7))
            background = outlined(Color.WHITE, 15f)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        if (recipe.imagePath.isNotBlank() && File(recipe.imagePath).exists()) {
            val thumb = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = shape(familyPale, 10f)
                clipToOutline = true
                contentDescription = "Foto: " + recipe.title
                val image = BitmapFactory.decodeFile(recipe.imagePath,
                    BitmapFactory.Options().apply { inSampleSize = 4 })
                setImageBitmap(image)
            }
            row.addView(thumb, LinearLayout.LayoutParams(dp(57), dp(57)).apply {
                marginEnd = dp(11)
            })
        }
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply {
            text = recipe.title
            textSize = 16f
            setTextColor(familyInk)
            setTypeface(null, Typeface.BOLD)
            maxLines = 2
        })
        texts.addView(TextView(this).apply {
            text = subtitle
            textSize = 12f
            setTextColor(Color.rgb(88, 103, 94))
        })
        row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(TextView(this).apply {
            text = "›"
            textSize = 25f
            setTextColor(familyDeep)
        })
        content.addView(row, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = dp(7)
        })
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
        content.addView(photo, LinearLayout.LayoutParams(dp(126), dp(100)).apply { bottomMargin = dp(8) })
    }

    private fun showDetail(id: Long) {
        val r = db.get(id) ?: run { showCalendar(); return }
        screen(r.title, r.meal + " · " + r.category + " · " +
            (if (r.alternative) "Alternativa" else "Principale"),
            "detail-" + id) { showDetail(id) }
        if (r.imagePath.isNotBlank()) addPhotoPreview(r.imagePath)
        val scanned = photoPaths(r.scanPages)
        if (scanned.isNotEmpty()) {
            label("PAGINE DELLA RIVISTA · " + scanned.size, 17f, true)
            scanned.forEachIndexed { index, path ->
                label("Pagina " + (index + 1), 13f, true)
                addPhotoPreview(path)
            }
        }
        val savedPhotos = photoChoices(r.dishPhotos).filterValues { it }.keys
        if (savedPhotos.isNotEmpty()) {
            label("FOTOGRAFIE SCELTE DEL PIATTO", 17f, true)
            savedPhotos.filter { it != r.imagePath }.forEach { addPhotoPreview(it) }
        }
        if (r.scanOcrText.isNotBlank()) {
            label("TRASCRIZIONE OCR ORIGINALE", 17f, true)
            label(r.scanOcrText)
        }
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
        if ((requestCode == MAGAZINE_PAGES_REQUEST || requestCode == DISH_PHOTOS_REQUEST) && resultCode == RESULT_OK) {
            val kind = if (requestCode == MAGAZINE_PAGES_REQUEST)
                "scan:" + pendingGalleryRole else "dish"
            val uris = mutableListOf<Uri>()
            data?.clipData?.let { clip ->
                for (index in 0 until clip.itemCount.coerceAtMost(20)) uris.add(clip.getItemAt(index).uri)
            }
            if (uris.isEmpty()) data?.data?.let { uris.add(it) }
            uris.forEach { addMagazineImage(it, kind) }
        }
        if (requestCode == CAMERA_REQUEST) {
            val kind = pendingCameraKind
            val temporary = pendingCameraPath?.let { File(it) }
            pendingCameraPath = null
            pendingCameraKind = "cover"
            try {
                if (resultCode == RESULT_OK && temporary?.isFile == true && temporary.length() > 0L) {
                    if (kind.startsWith("scan") || kind == "dish") addMagazineImage(Uri.fromFile(temporary), kind)
                    else {
                        selectedPhoto = compressPhoto(Uri.fromFile(temporary))
                        refreshPhotoPreview()
                        Toast.makeText(this, "Foto scattata, compressa e pronta", Toast.LENGTH_SHORT).show()
                    }
                } else if (resultCode == RESULT_OK) {
                    Toast.makeText(this, "La fotocamera non ha restituito una foto", Toast.LENGTH_LONG).show()
                }
            } catch (_: Exception) {
                Toast.makeText(this, "Impossibile elaborare la foto della fotocamera", Toast.LENGTH_LONG).show()
            } finally { temporary?.delete() }
        }
    }

    private fun compressPhoto(uri: Uri, maxEdge: Int = 960): String {
        val resolver = contentResolver
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { stream -> BitmapFactory.decodeStream(stream, null, opts) }
        var scale = 1
        while (opts.outWidth / scale > maxEdge || opts.outHeight / scale > maxEdge) scale *= 2
        val decoding = BitmapFactory.Options().apply { inSampleSize = scale }
        val bitmap = resolver.openInputStream(uri).use { stream ->
            BitmapFactory.decodeStream(stream, null, decoding)
        } ?: throw IllegalStateException("Foto non valida")
        // Phone cameras often store rotation as EXIF instead of rotated pixel data.
        // Correct it before compressing: otherwise ML Kit reads a sideways magazine.
        val rotation = try {
            contentResolver.openInputStream(uri)?.use { stream ->
                val exif = androidx.exifinterface.media.ExifInterface(stream)
                when (exif.getAttributeInt(
                    androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
                )) {
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (_: Exception) { 0f }
        val upright = if (rotation != 0f) {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height,
                android.graphics.Matrix().apply { postRotate(rotation) }, true)
        } else bitmap
        val ratio = minOf(1.0, maxEdge.toDouble() / maxOf(upright.width, upright.height))
        val resized = if (ratio < 1.0) Bitmap.createScaledBitmap(upright,
            (upright.width * ratio).toInt().coerceAtLeast(1),
            (upright.height * ratio).toInt().coerceAtLeast(1), true) else upright
        val folder = File(filesDir, "photos").apply { mkdirs() }
        val file = File(folder, UUID.randomUUID().toString() + ".jpg")
        FileOutputStream(file).use { resized.compress(Bitmap.CompressFormat.JPEG, 75, it) }
        if (resized !== upright) upright.recycle()
        if (upright !== bitmap) bitmap.recycle()
        resized.recycle()
        return file.absolutePath
    }


    override fun onDestroy() {
        nano.close()
        db.close()
        super.onDestroy()
    }
}


