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
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import java.net.URL
import java.net.HttpURLConnection
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
    private var lastResults: List<OnlineRecipe> = emptyList()
    private var photoLabel: TextView? = null
    private val dateFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", java.util.Locale.ITALIAN)

    companion object {
        private const val PHOTO_REQUEST = 401
        private val categories = arrayOf("Primo", "Secondo", "Piatto unico", "Dolce")
        private val difficulties = arrayOf("Facile", "Media", "Difficile")
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
            val planned = today.filter { it.meal == meal }
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
        screen(if (existing == null) "Nuova ricetta" else "Modifica ricetta",
            "Le ricette pianificate vengono conservate nel ricettario.")
        action("📅 " + selectedDate.format(dateFormatter) + " · Cambia giorno") {
            chooseDate { showEditor(existing, initialAlternative) }
        }
        val mealPicker = dropdown("Pasto", arrayOf("Pranzo", "Cena"), existing?.meal ?: selectedMeal)
        val categoryPicker = dropdown("Categoria", categories, existing?.category ?: selectedCategory)
        val alternativePicker = dropdown("Tipo di scelta", arrayOf("Principale", "Alternativa"),
            if (existing?.alternative == true || initialAlternative) "Alternativa" else "Principale")
        val titleField = input("Nome della ricetta *", existing?.title ?: suggestedTitle)
        val ingredientsField = input("Ingredienti (uno per riga)", existing?.ingredients ?: searchIngredients, true)
        val instructionsField = input("Preparazione / procedimento", existing?.instructions ?: "", true)
        val difficultyPicker = dropdown("Difficoltà", difficulties, existing?.difficulty ?: searchDifficulty)
        val durationPicker = dropdown("Tempo di preparazione", durations, existing?.duration ?: searchDuration)
        val urlField = input("Link originale (facoltativo)", existing?.url ?: suggestedUrl)
        photoLabel = label(if (selectedPhoto.isBlank()) "Nessuna foto selezionata" else "📷 Foto compressa e salvata", 14f)
        if (selectedPhoto.isNotBlank()) addPhotoPreview(selectedPhoto)
        action("📷 Scegli foto dal telefono (compressione automatica)") {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
            startActivityForResult(Intent.createChooser(intent, "Seleziona foto"), PHOTO_REQUEST)
        }
        action("✅ Salva nel calendario e nel ricettario") {
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
                day = selectedDate.toString(),
                meal = mealPicker.selectedItem.toString(),
                category = categoryPicker.selectedItem.toString(),
                title = title,
                ingredients = ingredientsField.text.toString().trim(),
                instructions = instructionsField.text.toString().trim(),
                difficulty = difficultyPicker.selectedItem.toString(),
                duration = durationPicker.selectedItem.toString(),
                url = url,
                alternative = alternativePicker.selectedItem.toString() == "Alternativa",
                imagePath = selectedPhoto
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

    private fun openWeb(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: Exception) {
            Toast.makeText(this, "Non è disponibile un browser", Toast.LENGTH_LONG).show()
        }
    }

    private fun searchUrl(domain: String, query: String): String =
        Uri.parse("https://www.bing.com/search").buildUpon()
            .appendQueryParameter("q", "site:$domain ricette $query")
            .build().toString()

    /** WebView con navigazione consentita sui siti selezionati e sul motore di ricerca. */
    private fun openInside(url: String, title: String = "Ricetta originale") {
        if (!trusted(url) && !url.startsWith("https://www.bing.com/search?")) {
            Toast.makeText(this, "Link non autorizzato", Toast.LENGTH_LONG).show()
            return
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(249, 250, 245))
        }
        val header = TextView(this).apply {
            text = "🍽️ $title"; textSize = 18f
            setTextColor(Color.rgb(33, 65, 43))
            setPadding(dp(15), dp(10), dp(15), dp(8))
            setTypeface(null, Typeface.BOLD)
        }
        root.addView(header)
        val toolbar = LinearLayout(this)
        val view = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(v: WebView?, request: WebResourceRequest?): Boolean {
                    val link = request?.url?.toString() ?: return true
                    val host = request.url.host?.lowercase() ?: ""
                    val allowedSearch = request.url.scheme == "https" &&
                        (host == "www.bing.com" || host == "bing.com")
                    if (trusted(link) || allowedSearch) return false
                    Toast.makeText(this@MainActivity,
                        "Sono consentiti solo i siti di cucina selezionati", Toast.LENGTH_SHORT).show()
                    return true
                }
                override fun onPageFinished(v: WebView?, url: String?) {
                    if (url != null && trusted(url)) {
                        header.text = "🍽️ " + (v?.title ?: title).take(95)
                    }
                }
            }
        }
        fun toolbarButton(caption: String, callback: () -> Unit) {
            val button = Button(this).apply {
                text = caption; textSize = 12f; isAllCaps = false
                setOnClickListener { callback() }
            }
            toolbar.addView(button, LinearLayout.LayoutParams(0, dp(55), 1f))
        }
        toolbarButton("← Indietro") {
            view.destroy()
            if (lastResults.isNotEmpty()) showRecipeChoices(lastResults) else showSearch()
        }
        toolbarButton("＋ Aggiungi al menù") {
            val current = view.url ?: url
            if (trusted(current)) {
                val candidateTitle = view.title?.substringBefore(" - ")?.trim()
                    ?.takeIf { it.isNotBlank() } ?: title
                view.destroy()
                showMealChoice(candidateTitle, current)
            } else {
                Toast.makeText(this, "Apri prima una ricetta di uno dei siti consentiti",
                    Toast.LENGTH_LONG).show()
            }
        }
        root.addView(toolbar)
        root.addView(view, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        view.loadUrl(url)
    }

    private fun showMealChoice(title: String, link: String) {
        val options = arrayOf("Pranzo · Principale", "Pranzo · Alternativa",
            "Cena · Principale", "Cena · Alternativa")
        AlertDialog.Builder(this).setTitle("Dove inserire la ricetta?")
            .setItems(options) { _, position ->
                selectedMeal = if (position < 2) "Pranzo" else "Cena"
                showEditor(initialAlternative = position % 2 == 1,
                    suggestedTitle = title, suggestedUrl = link)
            }.setNegativeButton("Annulla", null).show()
    }

    private fun showRecipeChoices(items: List<OnlineRecipe>) {
        lastResults = items
        val scroll = ScrollView(this)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(12))
        }
        scroll.addView(column)
        items.forEachIndexed { index, recipe ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(13), dp(12), dp(13), dp(12))
                background = shape(Color.rgb(241, 247, 235), 12f)
            }
            column.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
            fun rowLabel(value: String, big: Boolean = false) {
                val label = TextView(this).apply {
                    text = value
                    textSize = if (big) 17f else 13f
                    if (big) setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.rgb(31, 78, 49))
                    setPadding(0, dp(3), 0, dp(3))
                }
                card.addView(label)
            }
            rowLabel("${index + 1}. ${recipe.title}", true)
            rowLabel(recipe.site + " · " + (recipe.minutes?.let { "$it min" } ?: "Tempo n.d.") +
                " · " + recipe.difficulty.ifBlank { "Difficoltà n.d." })
            if (recipe.rating != null) {
                rowLabel("⭐ ${recipe.rating}/5 · " +
                    (recipe.feedbackCount?.let { "$it feedback" } ?: "Feedback non indicati"))
            } else rowLabel("Valutazioni non disponibili")
            if (recipe.description.isNotBlank()) rowLabel(recipe.description)
            if (recipe.photoUrl.startsWith("https://")) {
                val image = ImageView(this).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    contentDescription = "Foto della ricetta ${recipe.title}"
                }
                card.addView(image, LinearLayout.LayoutParams(-1, dp(145)))
                // Anteprima temporanea: nessuna copia persistente delle fotografie di terzi.
                Thread {
                    try {
                        val connection = URL(recipe.photoUrl).openConnection() as HttpURLConnection
                        connection.connectTimeout = 6500; connection.readTimeout = 6500
                        val buffer = java.io.ByteArrayOutputStream()
                        connection.inputStream.use { stream ->
                            val chunk = ByteArray(8192)
                            while (buffer.size() < 800_000) {
                                val n = stream.read(chunk)
                                if (n < 0) break
                                buffer.write(chunk, 0, n)
                            }
                        }
                        connection.disconnect()
                        val bytes = buffer.toByteArray()
                        val photo = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (photo != null) runOnUiThread { image.setImageBitmap(photo) }
                    } catch (_: Exception) { /* fotografia non disponibile */ }
                }.start()
            }
            val open = Button(this).apply {
                text = "👁 Leggi dentro l'app"; isAllCaps = false
                setOnClickListener { openInside(recipe.url, recipe.title) }
            }
            card.addView(open)
            val add = Button(this).apply {
                text = "＋ Scegli per il calendario"; isAllCaps = false
                setOnClickListener { showMealChoice(recipe.title, recipe.url) }
            }
            card.addView(add)
        }
        AlertDialog.Builder(this).setTitle("🍽️ Ricette trovate (${items.size}/10)")
            .setView(scroll).setPositiveButton("Chiudi", null)
            .setNeutralButton("Nuova ricerca") { _, _ -> showSearch() }
            .show()
    }

    private fun showSearch() {
        screen("Trova cosa cucinare", "Risultati e ricette consultabili dentro OggiSiMangia...")
        label("Quali ingredienti hai a disposizione?", 17f, true)
        val ingredients = input("Es. pasta, zucchine, uova, parmigiano", searchIngredients, true)
        val category = dropdown("Cerchi un...", categories, searchCategory)
        val difficulty = dropdown("Difficoltà desiderata", difficulties, searchDifficulty)
        val duration = dropdown("Tempo a disposizione", durations, searchDuration)
        fun remember(): Boolean {
            searchIngredients = ingredients.text.toString().trim()
            searchCategory = category.selectedItem.toString()
            searchDifficulty = difficulty.selectedItem.toString()
            searchDuration = duration.selectedItem.toString()
            if (searchIngredients.isBlank()) {
                ingredients.error = "Indica almeno un ingrediente"
                return false
            }
            return true
        }
        action("🔎 Trova fino a 10 ricette con foto e valutazioni") {
            if (!remember()) return@action
            val progress = ProgressBar(this)
            val dialog = AlertDialog.Builder(this).setTitle("Ricerca ricette in corso")
                .setMessage("Consulto le fonti selezionate. La disponibilità dei dati dipende dai siti.")
                .setView(progress)
                .setNegativeButton("Annulla", null).create()
            dialog.show()
            val query = searchIngredients
            val cat = searchCategory
            val diff = searchDifficulty
            val dur = searchDuration
            Thread {
                val results = try { RecipeDiscovery.search(query, cat, diff, dur) }
                    catch (_: Exception) { emptyList() }
                runOnUiThread {
                    if (!dialog.isShowing) return@runOnUiThread
                    dialog.dismiss()
                    if (results.isNotEmpty()) showRecipeChoices(results)
                    else AlertDialog.Builder(this)
                        .setTitle("Nessuna anteprima disponibile")
                        .setMessage("I siti o il servizio di ricerca non hanno restituito dati leggibili. Puoi comunque cercare e leggere le ricette senza uscire dall'app.")
                        .setPositiveButton("Apri ricerca interna") { _, _ ->
                            openInside(searchUrl("giallozafferano.it", query + " " + cat),
                                "Ricerca ricette")
                        }.setNegativeButton("Chiudi", null).show()
                }
            }.start()
        }
        label("Le ricette con più feedback verificabili appaiono per prime. I tempi e le valutazioni mancanti sono indicati come non disponibili.", 13f)
        label("Oppure scegli dove cercare (sempre dentro l'app):", 15f, true)
        listOf(
            "GialloZafferano" to "giallozafferano.it",
            "Il Cucchiaio d'Argento" to "cucchiaio.it",
            "Fatto in Casa da Benedetta" to "fattoincasadabenedetta.it"
        ).forEach { (name, domain) ->
            action("🔎 Cerca su $name") {
                if (remember()) openInside(searchUrl(domain, searchIngredients + " " + searchCategory),
                    "Ricerca $name")
            }
        }
        action("＋ Inserisci una ricetta manualmente") {
            if (remember()) showEditor()
        }
        action("📚 Apri ricettario") { showArchive() }
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
        label("📅 " + r.day + "   ⏱ " + r.duration + "   👩‍🍳 " + r.difficulty, 15f)
        label("INGREDIENTI", 17f, true)
        label(r.ingredients.ifBlank { "Non annotati" })
        label("PREPARAZIONE", 17f, true)
        label(r.instructions.ifBlank { "Consulta la ricetta originale, se disponibile." })
        if (r.url.isNotBlank() && trusted(r.url)) {
            action("🌐 Apri la ricetta originale") { openWeb(r.url) }
        }
        action("✏️ Modifica questa ricetta") {
            selectedDate = LocalDate.parse(r.day)
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
