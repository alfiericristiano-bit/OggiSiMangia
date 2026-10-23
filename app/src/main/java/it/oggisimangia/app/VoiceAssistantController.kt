package it.oggisimangia.app

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Push-to-talk control: never records in the background and never sends commands to an LLM. */
class VoiceAssistantController(
    private val activity: Activity,
    private val executor: (VoiceCommand, (String) -> Unit) -> Unit,
    private val aiInterpret: (String, (String?) -> Unit) -> Unit = { _, callback -> callback(null) }
) {
    companion object {
        const val SPEECH_REQUEST = 519
        private const val PREFS = "voice_assistant_v1"
        private const val SAVED = "saved_flows"
    }

    private var speaker: TextToSpeech? = null
    private var speakerReady = false
    private val prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)

    /**
     * Consent stored only after an explicit toggle in AI Settings. Non-destructive
     * commands execute immediately; calendar writes can be auto-executed only
     * after consent and still pass allowlist, duplicate and conflict checks.
     */
    var autonomousCalendarEnabled: Boolean
        get() = prefs.getBoolean("autonomous_calendar_writes", false)
        set(value) { prefs.edit().putBoolean("autonomous_calendar_writes", value).apply() }

    init {
        speaker = TextToSpeech(activity) { result ->
            speakerReady = result == TextToSpeech.SUCCESS
            if (speakerReady) speaker?.language = Locale.ITALIAN
        }
    }

    fun speak(message: String) {
        if (speakerReady && prefs.getBoolean("speak", true))
            speaker?.speak(message.take(500), TextToSpeech.QUEUE_FLUSH, null, "oggisimangia-voice")
    }

    fun open() {
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(8))
        }
        val instructions = TextView(activity).apply {
            text = "Parlami in italiano. Posso cercare ricette, consultare i pasti, aprire sezioni e pianificare ricette già salvate. Per più azioni usa «e poi». Ti chiederò conferma prima di cambiare il calendario."
            textSize = 15f
        }
        layout.addView(instructions)
        val input = EditText(activity).apply {
            hint = "Scrivi oppure detta un comando…"
            minLines = 2
            maxLines = 4
            contentDescription = "Comando per l'assistente"
        }
        layout.addView(input)
        val sample = TextView(activity).apply {
            text = "Esempio: «Cerca ricette con pasta, pomodoro e melanzane»\n«Cosa mangio domani?»\n«Pianifica risotto ai funghi a cena domani»\n«Apri il ricettario e poi mostra il calendario»"
            textSize = 13f
        }
        layout.addView(sample)
        layout.addView(CheckBox(activity).apply {
            text = "Leggi ad alta voce risposte e suggerimenti"
            isChecked = prefs.getBoolean("speak", true)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("speak", checked).apply()
                if (!checked) speaker?.stop()
            }
        })
        val dialog = AlertDialog.Builder(activity)
            .setTitle("🎙 Assistente OggiSiMangia...")
            .setView(layout)
            .setPositiveButton("Interpreta", null)
            .setNeutralButton("🎙 Parla", null)
            .setNegativeButton("Chiudi", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val text = input.text.toString().trim()
                if (text.isEmpty()) input.error = "Pronuncia o scrivi un comando"
                else { dialog.dismiss(); accept(text) }
            }
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                dialog.dismiss()
                listen()
            }
        }
        val savedButton = Button(activity).apply {
            text = "Sequenze salvate"
            isAllCaps = false
            setOnClickListener { dialog.dismiss(); showSaved() }
        }
        layout.addView(savedButton)
        dialog.show()
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()

    fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "it-IT")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Cosa vuoi fare in OggiSiMangia...?")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            // Hint, not a guarantee: the phone's selected speech service may still use the network.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        }
        try {
            activity.startActivityForResult(intent, SPEECH_REQUEST)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(activity, "Riconoscimento vocale non disponibile. Usa la tastiera.", Toast.LENGTH_LONG).show()
            open()
        } catch (_: SecurityException) {
            Toast.makeText(activity, "Microfono non disponibile. Usa la tastiera.", Toast.LENGTH_LONG).show()
            open()
        }
    }

    fun onSpeechResult(resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) return
        val heard = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull { it.isNotBlank() } ?: return
        accept(heard)
    }

    private fun askClarification(problem: String, spoken: String) {
        speak(problem)
        AlertDialog.Builder(activity).setTitle("Chiarimento necessario")
            .setMessage(problem + "\n\nComando: «" + spoken.take(300) + "»")
            .setPositiveButton("Riformula") { _, _ -> open() }
            .setNegativeButton("Annulla", null).show()
    }

    private fun accept(spoken: String, refined: Boolean = false) {
        val plan = VoiceFlowPlanner.build(spoken)
        val problem = plan.clarification ?: plan.conflict
        if (problem != null) {
            if (!refined && plan.clarification != null) {
                val progress = AlertDialog.Builder(activity)
                    .setTitle("Interpretazione AI locale")
                    .setMessage("Verifico se l'AI già disponibile sul telefono può proporre una sequenza sicura...")
                    .setNegativeButton("Annulla", null).create()
                var active = true
                progress.setOnDismissListener { active = false }
                progress.show()
                aiInterpret(spoken) { alternative ->
                    activity.runOnUiThread {
                        if (!active || activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                        progress.dismiss()
                        val suggested = alternative?.let { VoiceFlowPlanner.build(it) }
                        if (suggested != null && suggested.clarification == null &&
                            suggested.conflict == null && suggested.commands.isNotEmpty() &&
                            alternative != spoken) {
                            // LiteRT/Gemini Nano may only propose allowlisted commands.
                            // They are re-parsed and executed automatically if read-only,
                            // or if this user explicitly enabled safe calendar writes.
                            accept(alternative!!, true)
                        } else askClarification(problem, spoken)
                    }
                }
                return
            }
            askClarification(problem, spoken)
            return
        }
        if (!plan.needsConfirmation || autonomousCalendarEnabled) {
            runPlan(plan)
            return
        }
        val summary = plan.commands.mapIndexed { i, cmd ->
            "${i + 1}. ${VoiceFlowPlanner.description(cmd)}"
        }.joinToString("\n")
        val message = summary + if (plan.needsConfirmation)
            "\n\nLa pianificazione creerà nuove assegnazioni nel calendario. Nessuna ricetta sarà cancellata."
        else "\n\nI comandi verranno eseguiti nell'ordine indicato."
        val dialog = AlertDialog.Builder(activity).setTitle("Piano dell'assistente")
            .setMessage(message)
            .setPositiveButton(if (plan.needsConfirmation) "Conferma ed esegui" else "Esegui", null)
            .setNegativeButton("Correggi") { _, _ -> open() }
            .setNeutralButton("Memorizza sequenza") { _, _ -> askSave(spoken) }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                dialog.dismiss()
                runPlan(plan)
            }
        }
        dialog.show()
    }

    private fun runPlan(plan: VoicePlan) {
        fun step(index: Int, results: MutableList<String>) {
            if (index >= plan.commands.size) {
                val last = plan.commands.lastOrNull()
                val closing = (results.lastOrNull() ?: "Operazioni completate.") +
                    "\n\n" + (last?.let { VoiceFlowPlanner.suggestion(it) } ?: "")
                speak(closing)
                val suggestedAction: VoiceCommand? = when (last) {
                    is VoiceCommand.PlanSaved -> VoiceCommand.ReadMeals(last.day, last.meal)
                    is VoiceCommand.ReadMeals, is VoiceCommand.ShowDay ->
                        VoiceCommand.Open(VoiceCommand.Destination.SEARCH)
                    else -> null
                }
                val response = AlertDialog.Builder(activity).setTitle("Assistente: operazioni completate")
                    .setMessage(results.joinToString("\n") + "\n\n" +
                        (last?.let { VoiceFlowPlanner.suggestion(it) } ?: ""))
                    .setPositiveButton("Altro comando") { _, _ -> open() }
                    .setNegativeButton("Chiudi", null)
                if (suggestedAction != null) response.setNeutralButton("Passo suggerito") { _, _ ->
                    executor(suggestedAction) { message ->
                        speak(message)
                        AlertDialog.Builder(activity).setTitle("Passo completato")
                            .setMessage(message)
                            .setPositiveButton("Altro comando") { _, _ -> open() }
                            .setNegativeButton("Chiudi", null).show()
                    }
                }
                response.show()
                return
            }
            executor(plan.commands[index]) { response ->
                // Executor is asynchronous when another user confirmation is required.
                results.add("${index + 1}. " + response)
                // "Annullato" blocks a whole plan to avoid continuing after a declined write.
                if (response.startsWith("Annullato") || response.startsWith("Impossibile")) {
                    speak(response)
                    AlertDialog.Builder(activity).setTitle("Sequenza interrotta")
                        .setMessage(results.joinToString("\n"))
                        .setPositiveButton("Chiudi", null).show()
                } else step(index + 1, results)
            }
        }
        step(0, mutableListOf())
    }

    private fun saved(): JSONArray = try {
        JSONArray(prefs.getString(SAVED, "[]"))
    } catch (_: Exception) { JSONArray() }

    private fun askSave(spoken: String) {
        val box = EditText(activity).apply {
            hint = "Nome della sequenza"
            setText("Sequenza " + (saved().length() + 1))
            setSingleLine(true)
        }
        AlertDialog.Builder(activity).setTitle("Memorizza più comandi insieme")
            .setMessage("Verrà salvato sul telefono il testo dei comandi. Ogni riutilizzo mostrerà nuovamente il piano e le conferme.")
            .setView(box)
            .setPositiveButton("Memorizza") { _, _ ->
                val name = box.text.toString().trim()
                if (name.isBlank()) return@setPositiveButton
                val old = saved()
                val next = JSONArray()
                val start = (old.length() - 11).coerceAtLeast(0)
                for (i in start until old.length())
                    old.optJSONObject(i)?.let { next.put(it) }
                next.put(JSONObject().put("name", name.take(50)).put("spoken", spoken.take(800)))
                prefs.edit().putString(SAVED, next.toString()).apply()
                Toast.makeText(activity, "Sequenza salvata sul telefono", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Annulla", null).show()
    }

    private fun showSaved() {
        val array = saved()
        if (array.length() == 0) {
            AlertDialog.Builder(activity).setTitle("Sequenze salvate")
                .setMessage("Non hai ancora memorizzato sequenze.")
                .setPositiveButton("Nuovo comando") { _, _ -> open() }
                .setNegativeButton("Chiudi", null).show()
            return
        }
        val names = (0 until array.length()).map { i ->
            array.optJSONObject(i)?.optString("name").orEmpty()
        }.toTypedArray()
        AlertDialog.Builder(activity).setTitle("Sequenze memorizzate")
            .setItems(names) { _, index ->
                val obj = array.optJSONObject(index) ?: return@setItems
                val savedText = obj.optString("spoken")
                AlertDialog.Builder(activity).setTitle(obj.optString("name"))
                    .setMessage(savedText)
                    .setPositiveButton("Rivedi ed esegui") { _, _ -> accept(savedText) }
                    .setNeutralButton("Elimina sequenza") { _, _ ->
                        AlertDialog.Builder(activity).setTitle("Conferma eliminazione")
                            .setMessage("Eliminare solo questa sequenza salvata?")
                            .setPositiveButton("Elimina") { _, _ ->
                                val next = JSONArray()
                                for (i in 0 until array.length())
                                    if (i != index) array.optJSONObject(i)?.let { next.put(it) }
                                prefs.edit().putString(SAVED, next.toString()).apply()
                                showSaved()
                            }.setNegativeButton("Annulla", null).show()
                    }.setNegativeButton("Chiudi", null).show()
            }.setNegativeButton("Indietro") { _, _ -> open() }.show()
    }

    fun close() {
        speaker?.stop()
        speaker?.shutdown()
        speaker = null
    }
}
