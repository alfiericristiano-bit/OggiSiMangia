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
    private lateinit var content: LinearLayout
    private var selectedDate = LocalDate.now()
    private var selectedMeal = "Pranzo"
    private var selectedCategory = "Primo"
    private var searchIngredients = ""
    private var searchDifficulty = "Facile"
    private var searchDuration = "Veloce"
    private var searchCategory = "Primo"
    private var selectedPhoto = ""
    private var lastSearchResults: List<WebRecipe> = emptyList()
    private var lastSearchOffset = 0
    private var photoLabel: TextView? = null
    private val dateFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", java.util.Locale.ITALIAN)

    companion object {
        private const val PHOTO_REQUEST = 401
        private val categories = arrayOf("Antipasto", "Primo", "Secondo", "Contorno", "Piatto unico", "Dolce")
        private val difficulties = arrayOf("Facile", "Media", "Difficile", "Non disponibile")
        private val durations = arrayOf("Volante", "Veloce", "Media", "Lunga")
        private val allowedDomains = listOf("giallozafferano.it", "cucchiaio.it", "fattoincasadabenedetta.it")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = RecipeDb(this)
        window.statusBarColor = Color.rgb(31, 78, 49)
        window.navigationBarColor = Color.rgb(31, 78, 49)
        showCalendar()
    }

    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun shape(color: Int, radius: Float = 16f): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(radius.toInt()).toFloat() }

    private fun screen(title: String, subtitle: String) {
        val background = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(249, 250, 245))
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(32))
        }
        scroll.addView(content)
        background.addView(scroll)
        setContentView(background)
        label("🍽️ OggiSiMangia...", 27f, true)
        label(title, 21f, true)
        label(subtitle, 14f)
        navigation()
    }

    private fun label(value: String, size: Float = 16f, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value; textSize = size
            setTextColor(Color.rgb(33, 65, 43))
            if (bold) setTypeface(null, Typeface.BOLD)
            setPadding(dp(4), dp(8), dp(4), dp(8))
            content.addView(this)
        }

    private fun action(value: String, onClick: () -> Unit) {
        val btn = Button(this).apply {
            text = value; isAllCaps = false; textSize = 15f
            setTextColor(Color.rgb(22, 59, 35))
            background = shape(Color.rgb(226, 241, 226), 13f)
            setOnClickListener { onClick() }
        }
        content.addView(btn, LinearLayout.LayoutParams(-1, dp(53)).apply {
            bottomMargin = dp(9)
        })
    }

    private fun input(hint: String, initial: String = "", multi: Boolean = false): EditText =
        EditText(this).apply {
            this.hint = hint
            setText(initial)
            textSize = 16f
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.WHITE)
            if (multi) {
                minLines = 3
                gravity = Gravity.TOP
                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            } else {
                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            }
            content.addView(this, LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = dp(12)
            })
        }

    private fun dropdown(caption: String, choices: Array<String>, value: String): Spinner {
        label(caption, 14f, true)
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, choices)
        spinner.setSelection(choices.indexOf(value).coerceAtLeast(0))
        content.addView(spinner, LinearLayout.LayoutParams(-1, dp(48)))
        return spinner
    }

    private fun navigation() {
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("📅 Calendario", "🔎 Cerca", "📚 Ricettario").forEachIndexed { index, name ->
            val b = Button(this).apply {
                text = name; isAllCaps = false; textSize = 11f
                setPadding(0, 0, 0, 0)
                setOnClickListener {
                    when (index) {
                        0 -> showCalendar()
                        1 -> showSearch()
                        else -> showArchive()
                    }
                }
            }
            nav.addView(b, LinearLayout.LayoutParams(0, dp(53), 1f))
        }
        content.addView(nav)
    }

    private fun chooseDate(after: () -> Unit) {
        DatePickerDialog(this, { _, year, month, day ->
            selectedDate = LocalDate.of(year, month + 1, day)
            after()
        }, selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth).show()
    }

    private fun showCalendar() {
        screen("Il calendario dei pasti", "Organizza pranzo e cena, anche per i prossimi giorni.")
        val calendar = CalendarView(this).apply {
            firstDayOfWeek = java.util.Calendar.MONDAY
            date = selectedDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            setOnDateChangeListener { _, year, month, day ->
                selectedDate = LocalDate.of(year, month + 1, day)
                showCalendar()
            }
        }
        content.addView(calendar, LinearLayout.LayoutParams(-1, dp(310)))
        label(selectedDate.format(dateFormatter).replaceFirstChar { it.uppercase() }, 18f, true)
        action("◀ Giorno precedente") { selectedDate = selectedDate.minusDays(1); showCalendar() }
        action("Giorno successivo ▶") { selectedDate = selectedDate.plusDays(1); showCalendar() }
        val today = db.onDay(selectedDate.toString())
        listOf("Pranzo", "Cena").forEach { meal ->
            label(if (meal == "Pranzo") "☀️ PRANZO" else "🌙 CENA", 20f, true)
            val planned = today.filter { it.meal == meal || it.meal == "Entrambi" }
            if (planned.isEmpty()) label("Ancora nessun piatto selezionato.", 14f)
            planned.forEach { recipe ->
                action((if (recipe.alternative) "🔄 Alternativa · " else "🍴 ") +
                    recipe.category + " · " + recipe.title) {
                    showDetail(recipe.id)
                }
            }
            action("＋ Aggiungi ricetta per " + meal.lowercase()) {
                selectedMeal = meal
                showEditor()
            }
        }
        action("🔎 Cerca una nuova ricetta") { showSearch() }
    }

    private fun showEditor(existing: Recipe? = null, initialAlternative: Boolean = false,
                           suggestedTitle: String = "", suggestedUrl: String = "") {
        selectedPhoto = existing?.imagePath ?: ""
        screen(if (existing == null || existing.id == 0L) "Nuova ricetta" else "Modifica ricetta",
            "Le ricette pianificate vengono conservate nel ricettario.")
        val mealPicker = dropdown("Pasto", arrayOf("Pranzo", "Cena", "Entrambi"), existing?.meal ?: selectedMeal)
        val categoryPicker = dropdown("Categoria", categories, existing?.category ?: selectedCategory)
        val alternativePicker = dropdown("Tipo di scelta", arrayOf("Principale", "Alternativa"),
            if (existing?.alternative == true || initialAlternative) "Alternativa" else "Principale")
        val titleField = input("Nome della ricetta *", existing?.title ?: suggestedTitle)
        val ingredientsField = input("Ingredienti (uno per riga)", existing?.ingredients ?: searchIngredients, true)
        val modePicker = dropdown("Versione da conservare", arrayOf("Semplificata", "Originale", "Entrambe"),
            existing?.saveMode ?: "Semplificata")
        val instructionsField = input("Preparazione semplificata / appunti", existing?.instructions ?: "", true)
        val originalField = input("Procedimento originale (testo disponibile)", existing?.originalInstructions ?: "", true)
        label("L'originale offline conserva il testo leggibile, non l'impaginazione del sito. Il link resta sempre disponibile.", 13f)
        val difficultyPicker = dropdown("Difficoltà", difficulties, existing?.difficulty ?: searchDifficulty)
        val savedDuration = existing?.duration ?: searchDuration
        val durationPicker = dropdown("Tempo di preparazione", (durations.toList() + savedDuration).distinct().toTypedArray(), savedDuration)
        val urlField = input("Link originale (facoltativo)", existing?.url ?: suggestedUrl)
        photoLabel = label(if (selectedPhoto.isBlank()) "Nessuna foto selezionata" else "📷 Foto compressa e salvata", 14f)
        if (selectedPhoto.isNotBlank()) addPhotoPreview(selectedPhoto)
        action("📷 Scegli foto dal telefono (compressione automatica)") {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
            startActivityForResult(Intent.createChooser(intent, "Seleziona foto"), PHOTO_REQUEST)
        }
        val planned = CheckBox(this).apply {
            text = "Inserisci anche nel calendario"
            isChecked = existing == null || existing.day.isNotBlank()
        }
        content.addView(planned)
        val dateButton = Button(this).apply {
            text = "📅 " + selectedDate.format(dateFormatter) + " · Cambia giorno"
            isAllCaps = false
            setOnClickListener { chooseDate { text = "📅 " + selectedDate.format(dateFormatter) + " · Cambia giorno" } }
        }
        content.addView(dateButton)
        action("✅ Salva ricetta") {
            val title = titleField.text.toString().trim()
            val url = urlField.text.toString().trim()
            if (title.isBlank()) {
                titleField.error = "Inserisci il nome della ricetta"
                return@action
            }
            if (url.isNotBlank() && !trusted(url)) {
                urlField.error = "Sono ammessi solo link dei siti selezionati, con HTTPS"
                return@action
            }
            db.save(Recipe(
                id = existing?.id ?: 0L,
                day = if (planned.isChecked) selectedDate.toString() else "",
                meal = mealPicker.selectedItem.toString(),
                category = categoryPicker.selectedItem.toString(),
                title = title,
                ingredients = ingredientsField.text.toString().trim(),
                instructions = if (modePicker.selectedItem.toString() != "Originale") instructionsField.text.toString().trim() else "",
                difficulty = difficultyPicker.selectedItem.toString(),
                duration = durationPicker.selectedItem.toString(),
                url = url,
                alternative = alternativePicker.selectedItem.toString() == "Alternativa",
                imagePath = selectedPhoto,
                originalInstructions = if (modePicker.selectedItem.toString() != "Semplificata") originalField.text.toString().trim() else "",
                saveMode = modePicker.selectedItem.toString()
            ))
            Toast.makeText(this, "Ricetta salvata", Toast.LENGTH_SHORT).show()
            searchIngredients = ""
            showCalendar()
        }
        action("← Indietro") { showCalendar() }
    }

    private fun trusted(url: String): Boolean = try {
        val parsed = Uri.parse(url)
        val host = parsed.host?.lowercase() ?: ""
        parsed.scheme == "https" && allowedDomains.any { host == it || host.endsWith("." + it) }
    } catch (_: Exception) { false }


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
        screen("Trova cosa cucinare", "Scopri le proposte senza uscire dall'app.")
        label("Quali ingredienti hai a disposizione?", 17f, true)
        val ingredients = input("Es. pasta, zucchine, uova, parmigiano", searchIngredients, true)
        val category = dropdown("Cerchi un...", categories, searchCategory)
        val difficulty = dropdown("Difficoltà desiderata", difficulties, searchDifficulty)
        val duration = dropdown("Tempo a disposizione", durations, searchDuration)
        label("Le schede mostrano solo le informazioni disponibili sui siti originali. " +
            "I filtri vengono applicati quando i dati sono presenti.", 13f)

        action("🔎 Mostra fino a 10 ricette") {
            searchIngredients = ingredients.text.toString().trim()
            searchCategory = category.selectedItem.toString()
            searchDifficulty = difficulty.selectedItem.toString()
            searchDuration = duration.selectedItem.toString()
            if (searchIngredients.isBlank()) {
                ingredients.error = "Indica almeno un ingrediente"
                return@action
            }
            val progress = AlertDialog.Builder(this)
                .setTitle("Ricerca delle ricette")
                .setMessage("Consulto i siti selezionati...")
                .setCancelable(false)
                .create()
            progress.show()
            val selectedIngredients = searchIngredients
            val selectedCategory = searchCategory
            val selectedDifficulty = searchDifficulty
            val selectedDuration = searchDuration
            Thread {
                val results = try {
                    val primary = RecipeFinder.search(selectedIngredients, selectedCategory,
                        selectedDifficulty, selectedDuration)
                    val backup = if (primary.size <= 10) {
                        try {
                            RecipeDiscovery.search(selectedIngredients, selectedCategory,
                                selectedDifficulty, selectedDuration).map { r ->
                                WebRecipe(r.title, r.description, r.url, r.site,
                                    r.photoUrl, selectedCategory, r.difficulty,
                                    r.minutes, r.rating, r.feedbackCount)
                            }
                        } catch (_: Exception) { emptyList() }
                    } else emptyList()
                    (primary + backup).distinctBy { it.url.substringBefore('?').substringBefore('#').trimEnd('/') }
                        .sortedWith(compareByDescending<WebRecipe> { it.feedbackCount ?: -1 }
                            .thenByDescending {
                                val title = it.title.lowercase()
                                selectedIngredients.split(',', ';', '\n')
                                    .count { term -> title.contains(term.trim().lowercase()) }
                            })
                } catch (_: Exception) { emptyList() }
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    progress.dismiss()
                    if (results.isEmpty()) {
                        AlertDialog.Builder(this)
                            .setTitle("Nessuna scheda disponibile")
                            .setMessage("I siti non hanno restituito schede compatibili. " +
                                "Puoi comunque consultare la loro ricerca all'interno dell'app.")
                            .setPositiveButton("Apri ricerca interna") { _, _ ->
                                showSitePicker(selectedIngredients)
                            }
                            .setNegativeButton("Chiudi", null).show()
                    } else showRecipeResults(results)
                }
            }.start()
        }
        action("＋ Registra una ricetta manualmente") { showEditor() }
        action("📚 Apri ricettario") { showArchive() }
    }

    private fun showSitePicker(query: String) {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val choices = arrayOf("GialloZafferano", "Il Cucchiaio d'Argento",
            "Fatto in Casa da Benedetta")
        val links = arrayOf(
            "https://www.giallozafferano.it/ricerca-ricette/" +
                encoded.replace("+", "%2B") + "/",
            "https://www.cucchiaio.it/?s=" + encoded,
            "https://www.fattoincasadabenedetta.it/?s=" + encoded
        )
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
                background = shape(Color.rgb(239, 246, 236), 12f)
            }
            val heading = TextView(this).apply {
                text = recipe.title
                textSize = 17f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.rgb(31, 78, 49))
            }
            row.addView(heading)
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
                recipe.feedbackCount?.let { it.toString() + " valutazioni/commenti" }
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
            }, LinearLayout.LayoutParams(0, dp(48), 1f))
            buttons.addView(Button(this).apply {
                text = "💾 Salva"
                isAllCaps = false
                setOnClickListener {
                    popup.dismiss()
                    showOnlineDetail(recipe)
                }
            }, LinearLayout.LayoutParams(0, dp(48), 1f))
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

    private fun showOnlineDetail(recipe: WebRecipe) {
        val progress = AlertDialog.Builder(this).setTitle("Leggo la ricetta")
            .setMessage("Recupero ingredienti e procedimento dalla fonte...").setCancelable(false).create()
        progress.show()
        Thread {
            val result = try { RecipeReader.read(recipe.url) } catch (_: Exception) {
                RecipeText(notice = "Questa fonte non è leggibile adesso. Puoi comunque salvare il link.")
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                progress.dismiss()
                screen(recipe.title, recipe.source)
                label("Dosi: " + result.portions.ifBlank { "non disponibili" }, 14f)
                label("Tempo pubblicato: " + result.time.ifBlank { recipe.minutes?.let { "$it min" } ?: "non disponibile" }, 14f)
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

    private fun chooseOnlineRecipe(recipe: WebRecipe, text: RecipeText) {
        val choices = arrayOf("Semplificata", "Originale", "Entrambe")
        AlertDialog.Builder(this).setTitle("Quale versione vuoi salvare?")
            .setItems(choices) { _, choice ->
                selectedCategory = recipe.category.takeIf { it in categories } ?: searchCategory
                showEditor(Recipe(
                    day = "", meal = selectedMeal, category = selectedCategory,
                    title = recipe.title,
                    ingredients = listOfNotNull(text.portions.takeIf { it.isNotBlank() }?.let { "Dosi: $it" },
                        text.time.takeIf { it.isNotBlank() }?.let { "Tempo: $it" }, text.ingredients.takeIf { it.isNotBlank() }).joinToString("\n\n"),
                    instructions = text.summary.ifBlank { recipe.summary },
                    difficulty = recipe.difficulty.takeIf { it in difficulties } ?: "Non disponibile",
                    duration = text.time.ifBlank { recipe.minutes?.let { "$it min" } ?: "Non disponibile" }, url = recipe.url, alternative = false, imagePath = "",
                    originalInstructions = text.original,
                    saveMode = choices[choice]
                ))
            }.setNegativeButton("Annulla", null).show()
    }

    private fun fetchPreview(url: String, target: ImageView) {
        if (!url.startsWith("https://")) return
        Thread {
            var connection: HttpURLConnection? = null
            try {
                connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 6000
                connection.instanceFollowRedirects = true
                connection.connect()
                if (connection.responseCode !in 200..299 ||
                    connection.contentLengthLong > 3_000_000L) return@Thread
                val bitmap = connection.inputStream.use { stream ->
                    BitmapFactory.decodeStream(stream, null,
                        BitmapFactory.Options().apply { inSampleSize = 4 })
                } ?: return@Thread
                runOnUiThread {
                    if (!isFinishing && !isDestroyed) target.setImageBitmap(bitmap)
                    else bitmap.recycle()
                }
            } catch (_: Exception) {
                // Immagine opzionale: una fonte esterna può non permetterne la lettura.
            } finally { connection?.disconnect() }
        }.start()
    }

    private fun showArchive(search: String = "", category: String = "Tutte",
                            difficulty: String = "Tutte", order: String = "Nome A–Z") {
        screen("Il mio ricettario", "Ricette salvate sul telefono, consultabili anche offline.")
        val queryField = input("Cerca per nome o ingrediente", search)
        val categoryPicker = dropdown("Categoria", arrayOf("Tutte") + categories, category)
        val difficultyPicker = dropdown("Difficoltà", arrayOf("Tutte") + difficulties, difficulty)
        val orderPicker = dropdown("Ordina", arrayOf("Nome A–Z", "Data (recenti)", "Difficoltà"), order)
        action("Applica filtri") {
            showArchive(queryField.text.toString().trim(),
                categoryPicker.selectedItem.toString(),
                difficultyPicker.selectedItem.toString(),
                orderPicker.selectedItem.toString())
        }
        val items = db.archive(search, category, difficulty, order)
        label("Risultati: " + items.size, 17f, true)
        if (items.isEmpty()) label("Nessuna ricetta trovata. Cambia i filtri o aggiungine una.", 14f)
        items.forEach { r ->
            action("🍴 " + r.title + "\n" + r.category + " · " + r.difficulty + " · " + r.duration) {
                showDetail(r.id)
            }
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
        content.addView(photo, LinearLayout.LayoutParams(-1, dp(175)).apply { bottomMargin = dp(10) })
    }

    private fun showDetail(id: Long) {
        val r = db.get(id) ?: run { showCalendar(); return }
        screen(r.title, r.meal + " · " + r.category + " · " +
            (if (r.alternative) "Alternativa" else "Principale"))
        if (r.imagePath.isNotBlank()) addPhotoPreview(r.imagePath)
        label("📅 " + r.day.ifBlank { "Solo ricettario" } + "   ⏱ " + r.duration + "   👩‍🍳 " + r.difficulty, 15f)
        label("INGREDIENTI", 17f, true)
        label(r.ingredients.ifBlank { "Non annotati" })
        label("PREPARAZIONE", 17f, true)
        if (r.instructions.isNotBlank()) label(r.instructions)
        if (r.originalInstructions.isNotBlank()) {
            label("PROCEDIMENTO ORIGINALE", 17f, true)
            label(r.originalInstructions)
        }
        if (r.instructions.isBlank() && r.originalInstructions.isBlank()) label("Consulta la ricetta originale, se disponibile.")
        if (r.url.isNotBlank() && trusted(r.url)) {
            action("🌐 Leggi la ricetta nell’app") { openWeb(r.url) }
        }
        action("✏️ Modifica questa ricetta") {
            if (r.day.isNotBlank()) selectedDate = LocalDate.parse(r.day)
            showEditor(r)
        }
        action("📅 Pianifica di nuovo questa ricetta") {
            val options = arrayOf("Pranzo · Principale", "Pranzo · Alternativa",
                "Cena · Principale", "Cena · Alternativa")
            AlertDialog.Builder(this).setTitle("Dove vuoi inserirla il " + selectedDate.toString() + "?")
                .setItems(options) { _, position ->
                    db.save(r.copy(id = 0L, day = selectedDate.toString(),
                        meal = if (position < 2) "Pranzo" else "Cena",
                        alternative = position % 2 == 1))
                    Toast.makeText(this, "Ricetta pianificata", Toast.LENGTH_SHORT).show()
                    showCalendar()
                }.setNegativeButton("Annulla", null).show()
        }
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
        if (requestCode == PHOTO_REQUEST && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                selectedPhoto = compressPhoto(uri)
                photoLabel?.text = "📷 Foto salvata e compressa (max 960 px, JPEG)"
                Toast.makeText(this, "Foto acquisita", Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(this, "Impossibile leggere la foto", Toast.LENGTH_LONG).show()
            }
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


