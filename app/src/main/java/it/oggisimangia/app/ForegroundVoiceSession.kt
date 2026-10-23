package it.oggisimangia.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast

/**
 * A foreground-only conversational speech session. The system speech service
 * delivers one utterance at a time; we restart it after the app completes the
 * corresponding command, without making the user tap "Parla" again.
 *
 * No permanent/background recording. The Android OS controls microphone
 * permission, privacy indicator and the speech-service implementation.
 */
class ForegroundVoiceSession(
    private val activity: Activity,
    private val onCommand: (String) -> Unit,
    private val onState: (Boolean) -> Unit
) {
    companion object {
        const val PERMISSION_REQUEST = 520
        private const val MAX_SILENT_RESTARTS = 10
    }

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var pendingStart: Runnable? = null
    private var retries = 0
    private var isCapturing = false
    var active: Boolean = false
        private set

    fun toggle() {
        if (active) stop()
        else start()
    }

    fun start() {
        if (active || activity.isFinishing || activity.isDestroyed) return
        if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), PERMISSION_REQUEST)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(activity) &&
            !(Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(activity))) {
            Toast.makeText(activity,
                "Nessun riconoscimento vocale disponibile. Usa la tastiera.",
                Toast.LENGTH_LONG).show()
            return
        }
        active = true
        retries = 0
        onState(true)
        Toast.makeText(activity,
            "Conversazione avviata. Di' «fermati» per interrompere.",
            Toast.LENGTH_SHORT).show()
        resume(0)
    }

    fun onPermissionResult(granted: Boolean) {
        if (granted) start()
        else Toast.makeText(activity,
            "Per parlare con l'assistente occorre autorizzare il microfono.",
            Toast.LENGTH_LONG).show()
    }

    /** Restart only after TTS is over or after an action has finished. */
    fun resume(delayMillis: Long = 400L) {
        if (!active) return
        pendingStart?.let(handler::removeCallbacks)
        val scheduled = Runnable {
            pendingStart = null
            if (!active || isCapturing || activity.isFinishing || activity.isDestroyed) return@Runnable
            beginListening()
        }
        pendingStart = scheduled
        handler.postDelayed(scheduled, delayMillis)
    }

    private fun beginListening() {
        if (!active || isCapturing) return
        try {
            if (recognizer == null) {
                recognizer = if (Build.VERSION.SDK_INT >= 31 &&
                    SpeechRecognizer.isOnDeviceRecognitionAvailable(activity)) {
                    SpeechRecognizer.createOnDeviceSpeechRecognizer(activity)
                } else SpeechRecognizer.createSpeechRecognizer(activity)
                recognizer?.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() { retries = 0 }
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onPartialResults(partialResults: Bundle?) {}
                    override fun onEvent(eventType: Int, params: Bundle?) {}

                    override fun onResults(results: Bundle?) {
                        isCapturing = false
                        if (!active) return
                        retries = 0
                        val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull { it.isNotBlank() }?.trim()
                        if (heard == null) resume()
                        else onCommand(heard)
                    }

                    override fun onError(error: Int) {
                        isCapturing = false
                        if (!active) return
                        if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ||
                            error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
                            error == SpeechRecognizer.ERROR_CLIENT) {
                            retries++
                            if (retries <= MAX_SILENT_RESTARTS) resume(700L)
                            else {
                                Toast.makeText(activity,
                                    "Ascolto terminato dopo un lungo periodo senza comandi.",
                                    Toast.LENGTH_LONG).show()
                                stop()
                            }
                        } else {
                            Toast.makeText(activity,
                                "Il riconoscimento vocale si è interrotto (codice $error).",
                                Toast.LENGTH_LONG).show()
                            stop()
                        }
                    }
                })
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "it-IT")
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            isCapturing = true
            recognizer?.startListening(intent)
        } catch (error: Exception) {
            isCapturing = false
            Toast.makeText(activity, "Microfono non disponibile: " +
                (error.message ?: "servizio non avviato"), Toast.LENGTH_LONG).show()
            stop()
        }
    }

    /** Also called in Activity.onStop, avoiding background microphone use. */
    fun stop() {
        active = false
        pendingStart?.let(handler::removeCallbacks)
        pendingStart = null
        isCapturing = false
        try { recognizer?.cancel() } catch (_: Exception) {}
        try { recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
        retries = 0
        onState(false)
    }
}
