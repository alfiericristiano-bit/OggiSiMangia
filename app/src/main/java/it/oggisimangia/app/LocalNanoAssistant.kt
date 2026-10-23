package it.oggisimangia.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Only Google's on-device AICore/ML Kit interface is allowed here.
 * No online LLM endpoint, API key, uploaded photo or cloud inference.
 * Never initiate an AICore model download without a separate user confirmation.
 */
class LocalNanoAssistant(private val context: Context) {
    companion object {
        private const val PREFS = "local_ai_settings"
        private const val ENABLED = "local_ai_enabled"
    }

    enum class State { CHECKING, READY, DOWNLOADABLE, DOWNLOADING, UNAVAILABLE, ERROR }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val settings = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val client: GenerativeModel? by lazy {
        try { Generation.getClient() } catch (_: Exception) { null }
    }

    var state = State.CHECKING
        private set
    var stateListener: ((State) -> Unit)? = null

    var enabled: Boolean
        get() = settings.getBoolean(ENABLED, true)
        set(value) {
            settings.edit().putBoolean(ENABLED, value).apply()
            if (value) refresh()
            else notifyState()
        }

    fun isUnmeteredWifi(): Boolean {
        val mgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = mgr.activeNetwork ?: return false
        val caps = mgr.getNetworkCapabilities(network) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) &&
            !mgr.isActiveNetworkMetered
    }

    fun statusText(): String {
        if (!enabled) return "AI locale disattivata: solo OCR ML Kit."
        return when (state) {
            State.CHECKING -> "Verifica AI locale in corso; OCR ML Kit disponibile."
            State.READY -> "Gemini Nano già presente: interpretazione AI locale attiva."
            State.DOWNLOADABLE -> "Gemini Nano compatibile ma non scaricato; OCR ML Kit attivo."
            State.DOWNLOADING -> "Download autorizzato del modello in corso; OCR ML Kit utilizzabile."
            State.UNAVAILABLE -> "Gemini Nano non disponibile su questo dispositivo; OCR ML Kit attivo."
            State.ERROR -> "AI locale temporaneamente non accessibile; OCR ML Kit attivo."
        }
    }

    private fun notifyState() { stateListener?.invoke(state) }
    private fun setState(s: State) { state = s; notifyState() }

    fun refresh() {
        if (!enabled) { setState(State.UNAVAILABLE); return }
        setState(State.CHECKING)
        scope.launch {
            val model = client
            if (model == null) { setState(State.UNAVAILABLE); return@launch }
            try {
                setState(when (model.checkStatus()) {
                    FeatureStatus.AVAILABLE -> State.READY
                    FeatureStatus.DOWNLOADABLE -> State.DOWNLOADABLE
                    FeatureStatus.DOWNLOADING -> State.DOWNLOADING
                    else -> State.UNAVAILABLE
                })
            } catch (_: Exception) { setState(State.ERROR) }
        }
    }

    /** Call only AFTER the user clicked a confirmation dialog on Wi-Fi. */
    fun downloadAfterConsent(onResult: (Boolean) -> Unit) {
        if (!enabled || state != State.DOWNLOADABLE || !isUnmeteredWifi()) {
            onResult(false)
            return
        }
        scope.launch {
            try {
                val model = client ?: run { setState(State.UNAVAILABLE); onResult(false); return@launch }
                // Check again immediately before starting; network may have changed.
                if (model.checkStatus() != FeatureStatus.DOWNLOADABLE || !isUnmeteredWifi()) {
                    refresh(); onResult(false); return@launch
                }
                setState(State.DOWNLOADING)
                var successful = false
                model.download().collect { progress ->
                    when (progress) {
                        DownloadStatus.DownloadCompleted -> { successful = true; setState(State.READY) }
                        is DownloadStatus.DownloadFailed -> setState(State.ERROR)
                        else -> Unit
                    }
                }
                if (!successful) refresh()
                onResult(successful)
            } catch (_: Exception) { setState(State.ERROR); onResult(false) }
        }
    }

    /** AI refines the already extracted OCR; original photo and OCR text are preserved.
     * No device downloads or cloud requests are performed by this method.
     */
    fun improveOcr(extractedText: String, onResult: (MagazineRecipeText.Guess?) -> Unit) {
        if (!enabled || state != State.READY || extractedText.isBlank()) {
            onResult(null); return
        }
        // AICore Prompt API supports < 4000 input tokens. Keep ample margin.
        val text = extractedText.take(6400)
        val prompt = """
            Sei un assistente che TRASCRIVE ricette italiane di riviste.
            Il seguente testo OCR contiene sezioni «Pagina N (intera|ingredienti|preparazione|dettagli)».
            Usa solo informazioni LETTERALMENTE presenti. Non inventare nomi, quantità, tempi,
            difficoltà, dosi o istruzioni. Preferisci le fotografie ravvicinate alle pagine intere.
            Ignora pubblicità, numeri di pagina, intestazioni editoriali e testi fuori ricetta.
            Ricongiungi titoli e frasi interrotti su più righe. Mantieni ordine e quantità
            degli ingredienti e i passaggi di preparazione. Non aggiungere contenuto plausibile.
            Rispondi SOLO con JSON valido, senza markdown, chiavi:
            titolo (stringa), ingredienti (array di stringhe),
            preparazione (array di passaggi), tempo_preparazione, tempo_cottura,
            tempo_totale, tempo_riposo, difficolta, porzioni (stringhe, vuote se assenti).
            OCR:
            $text
        """.trimIndent()
        scope.launch {
            try {
                val model = client ?: run { onResult(null); return@launch }
                if (model.checkStatus() != FeatureStatus.AVAILABLE) {
                    setState(State.UNAVAILABLE); onResult(null); return@launch
                }
                val answer = model.generateContent(prompt).candidates.firstOrNull()?.text.orEmpty()
                onResult(NanoRecipeResponse.parse(answer, text))
            } catch (_: Exception) { onResult(null) }
        }
    }

    /** Optional on-device interpretation of goals that the deterministic parser couldn't parse.
     * Returns only a suggested allowlisted command; callers must parse and confirm again.
     * Does not download a model, access external servers or execute any command.
     */
    fun interpretVoiceGoal(request: String, onResult: (String?) -> Unit) {
        if (!enabled || state != State.READY || request.isBlank()) {
            onResult(null)
            return
        }
        val prompt = """
            Sei il traduttore di intenzioni VOCALI per l'app Android OggiSiMangia.
            Non esegui operazioni, non inventi ricette, contenuti, date o preferenze.
            Trasforma una richiesta dell'utente in UNO o PIÙ comandi CANONICI italiani
            (separali con «e poi», massimo sei), scegliendo solo da:
            - Apri il calendario
            - Apri il ricettario
            - Apri la ricerca
            - Mostra il riepilogo
            - Apri le fonti
            - Nuova ricetta
            - Mostra oggi / Mostra domani
            - Cosa mangio oggi / Cosa mangio domani / Dimmi cosa ho per cena oggi
            - Cerca ricette con INGREDIENTE PRINCIPALE e altri ingredienti
            - Organizzami la cena di domani con INGREDIENTE e INGREDIENTE
            - Pianifica NOME RICETTA per pranzo/cena oggi/domani
            Conserva esattamente ingredienti, ricetta, pasto e giorno presenti nella richiesta.
            Non supporre ricette o ingredienti non detti e non aggiungere date non specificate.
            Quando qualcosa manca o vi è conflitto, rispondi soltanto CHIEDI: seguito
            da una breve domanda. Non inventare comandi distruttivi o fuori dall'app.
            Se invece la richiesta è chiara, rispondi soltanto COMANDI: seguito
            dai comandi supportati, su una sola riga.
            Richiesta: ${request.take(500)}
        """.trimIndent()
        scope.launch {
            try {
                val model = client ?: run { onResult(null); return@launch }
                if (model.checkStatus() != FeatureStatus.AVAILABLE) {
                    refresh()
                    onResult(null)
                    return@launch
                }
                val output = model.generateContent(prompt).candidates.firstOrNull()?.text.orEmpty().trim()
                val firstLine = output.lineSequence().firstOrNull().orEmpty().trim()
                val proposed = if (firstLine.startsWith("COMANDI:", ignoreCase = true))
                    firstLine.substringAfter(':').trim() else ""
                onResult(proposed.takeIf { it.isNotBlank() && it.length <= 650 })
            } catch (_: Exception) {
                onResult(null)
            }
        }
    }

    fun close() {
        scope.cancel()
        stateListener = null
        try { client?.close() } catch (_: Exception) { }
    }
}
