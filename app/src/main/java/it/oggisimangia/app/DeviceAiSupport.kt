package it.oggisimangia.app

import android.os.Build

/**
 * Manufacturer-independent AI capability report. Do not mistake an installed
 * consumer assistant for an embeddable local LLM: only an actual API status is
 * evidence of access. Adding future OEM adapters does not affect the fallback.
 */
object DeviceAiSupport {
    fun report(nano: LocalNanoAssistant): String {
        val maker = Build.MANUFACTURER.orEmpty().trim().ifBlank { "Non identificato" }
        val model = Build.MODEL.orEmpty().trim().ifBlank { "modello sconosciuto" }
        val family = when {
            maker.contains("xiaomi", true) || maker.contains("redmi", true) ->
                "Xiaomi / Redmi"
            maker.contains("samsung", true) -> "Samsung Galaxy"
            else -> maker
        }
        val capability = if (!nano.enabled) "disabilitato nelle impostazioni" else when (nano.state) {
            LocalNanoAssistant.State.READY -> "disponibile: prima scelta per l'interpretazione"
            LocalNanoAssistant.State.DOWNLOADABLE -> "compatibile, ma richiede download autorizzato"
            LocalNanoAssistant.State.DOWNLOADING -> "modello in download autorizzato"
            LocalNanoAssistant.State.CHECKING -> "verifica delle API in corso"
            LocalNanoAssistant.State.UNAVAILABLE -> "API locale non disponibile su questo dispositivo"
            LocalNanoAssistant.State.ERROR -> "verifica non riuscita; riprovare"
        }
        return "Telefono: $family — $model.\n" +
            "AI integrabile tramite API Android (Gemini Nano): $capability.\n" +
            "Se non è accessibile, la ricerca ricette e OCR ML Kit restano utilizzabili. " +
            "Le funzioni AI della marca (es. HyperAI o Galaxy AI) non sono automaticamente " +
            "interrogabili in background: occorre un'API ufficiale compatibile. " +
            "Il rilevamento si basa sulle capacità reali, non su un elenco fisso di marche."
    }
}
