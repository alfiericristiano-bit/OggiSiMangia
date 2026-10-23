package it.oggisimangia.app

import java.text.Normalizer
import java.util.Locale

/**
 * Only explicit, whole-utterance controls alter the microphone or confirm a
 * calendar write. Partial matches must never accidentally approve a plan.
 */
object VoiceConversationPolicy {
    enum class Decision { STOP, CONFIRM, REJECT, COMMAND }

    private fun normalized(value: String): String =
        Normalizer.normalize(value.trim().lowercase(Locale.ITALIAN), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[.!?,;]+$"), "")
            .replace(Regex("\\s+"), " ")

    fun classify(spoken: String, awaitingConfirmation: Boolean): Decision {
        val words = normalized(spoken)
        if (words in setOf(
            "fermati", "stop", "basta", "smetti di ascoltare", "interrompi ascolto",
            "interrompi l ascolto", "chiudi assistente", "chiudi la conversazione",
            "termina conversazione", "termina la conversazione"
        )) return Decision.STOP

        if (awaitingConfirmation) {
            if (words in setOf("si", "confermo", "conferma", "esegui", "procedi", "va bene",
                    "ok esegui", "si procedi")) return Decision.CONFIRM
            if (words in setOf("no", "annulla", "non confermo", "non eseguire",
                    "lascia stare")) return Decision.REJECT
        }
        return Decision.COMMAND
    }
}
