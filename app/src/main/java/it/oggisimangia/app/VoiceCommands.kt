package it.oggisimangia.app

import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale

/** Allowlisted, deterministic Italian commands. Spoken text is never executed as code. */
sealed interface VoiceCommand {
    data class Open(val destination: Destination) : VoiceCommand
    data class Search(val ingredients: List<String>) : VoiceCommand
    data class GuidedMealSearch(val ingredients: List<String>, val meal: String, val day: LocalDate) : VoiceCommand
    data class ShowDay(val day: LocalDate) : VoiceCommand
    data class ReadMeals(val day: LocalDate, val meal: String?) : VoiceCommand
    data class PlanSaved(val title: String, val meal: String, val day: LocalDate) : VoiceCommand
    data object Help : VoiceCommand
    data object Unknown : VoiceCommand

    enum class Destination { CALENDAR, ARCHIVE, SEARCH, EDITOR, SUMMARY, SOURCES }
}

object VoiceCommands {
    private fun normalized(text: String) = Normalizer.normalize(
        text.lowercase(Locale.ITALIAN).trim(), Normalizer.Form.NFD
    ).replace(Regex("\\p{M}+"), "").replace(Regex("\\s+"), " ")

    private fun date(text: String, today: LocalDate): LocalDate {
        val words = normalized(text)
        return when {
            Regex("\\bdopodomani\\b").containsMatchIn(words) -> today.plusDays(2)
            Regex("\\bdomani\\b").containsMatchIn(words) -> today.plusDays(1)
            Regex("\\bieri\\b").containsMatchIn(words) -> today.minusDays(1)
            else -> today
        }
    }

    fun parse(spoken: String, today: LocalDate = LocalDate.now()): VoiceCommand {
        val text = normalized(spoken).trim(' ', '.', '!', '?')
        if (text.isBlank()) return VoiceCommand.Unknown
        if (Regex("\\b(aiuto|help|comandi|cosa sai fare|come funziona)\\b").containsMatchIn(text)) return VoiceCommand.Help

        val guided = Regex(
            "^(?:organizza|organizzami|prepara|preparami|proponi|suggerisci|suggeriscimi|cerca|trova)" +
                ".*?\\\\b(pranzo|cena)\\\\b.*?\\\\b(?:con|a base di|usando)\\\\s+(.+)$"
        ).matchEntire(text)
        if (guided != null) {
            val ingredients = guided.groupValues[2]
                .split(Regex("\\\\s*(?:,|;|\\\\s+e\\\\s+)\\\\s*"))
                .map { it.trim() }.filter { it.isNotBlank() }.distinct().take(10)
            if (ingredients.isNotEmpty()) return VoiceCommand.GuidedMealSearch(
                ingredients, guided.groupValues[1].replaceFirstChar { it.uppercase() }, date(text, today)
            )
        }

        val plan = Regex(
            "^(?:aggiungi|metti|pianifica|inserisci|programma)\\s+(?:la ricetta\\s+|il piatto\\s+)?" +
                "(.+?)\\s+(?:a|per|nel|nella)\\s+(pranzo|cena)" +
                "(?:\\s+(?:di|per))?\\s*(?:oggi|domani|dopodomani|ieri)?$"
        ).matchEntire(text)
        if (plan != null) {
            val name = plan.groupValues[1].trim()
            if (name.isNotBlank()) return VoiceCommand.PlanSaved(
                name, plan.groupValues[2].replaceFirstChar { it.uppercase() }, date(text, today)
            )
        }

        val meal = Regex("\\b(pranzo|cena)\\b").find(text)?.value?.replaceFirstChar { it.uppercase() }
        if (Regex("(?:cosa|che cosa|che|quale|quali).*(?:mangio|mangiamo|cucino|cuciniamo|ho per|e previsto|ce per)|(?:dimmi|leggi|elenca).*(?:pasti|menu|pranzo|cena)").containsMatchIn(text) ||
            Regex("^(?:menu|pasti)(?:\\s+(?:di|per))?\\s*(?:oggi|domani|dopodomani)?$").matches(text)) {
            return VoiceCommand.ReadMeals(date(text, today), meal)
        }

        val searchVerb = Regex("^(?:cerca|trova|suggerisci|suggeriscimi|consigliami|mostrami|proponimi)\\b").containsMatchIn(text)
        if (searchVerb) {
            val part = Regex("\\b(?:con|a base di|usando|di)\\s+(.+)$").find(text)?.groupValues?.get(1)
            if (part != null && part.isNotBlank()) {
                val cleaned = part.replace(Regex("\\s+(?:per\\s+)?(?:pranzo|cena)(?:\\s+(?:oggi|domani))?$"), "")
                val items = cleaned.split(Regex("\\s*(?:,|;|\\s+e\\s+)\\s*"))
                    .map { it.trim().trim('.', ',', ';') }
                    .filter { it.isNotBlank() }.distinct().take(10)
                if (items.isNotEmpty()) return VoiceCommand.Search(items)
            }
            if (Regex("\\b(ricette|ricetta|piatti|cucinare|ingrediente)\\b").containsMatchIn(text))
                return VoiceCommand.Open(VoiceCommand.Destination.SEARCH)
        }

        if (Regex("^(?:vai|passa|mostra|apri|spostati|seleziona).*(?:giorno|oggi|domani|dopodomani|ieri)").containsMatchIn(text)) {
            return VoiceCommand.ShowDay(date(text, today))
        }

        if (Regex("\\b(fonti|siti|priorita)\\b").containsMatchIn(text))
            return VoiceCommand.Open(VoiceCommand.Destination.SOURCES)
        if (Regex("\\b(riepilogo|statistiche)\\b").containsMatchIn(text))
            return VoiceCommand.Open(VoiceCommand.Destination.SUMMARY)
        if (Regex("\\b(nuova ricetta|aggiungi ricetta|fotografa ricetta|scansiona ricetta|scanner)\\b").containsMatchIn(text))
            return VoiceCommand.Open(VoiceCommand.Destination.EDITOR)
        if (Regex("\\b(ricettario|mie ricette|ricette salvate)\\b").containsMatchIn(text))
            return VoiceCommand.Open(VoiceCommand.Destination.ARCHIVE)
        if (Regex("\\b(calendario|agenda)\\b").containsMatchIn(text))
            return VoiceCommand.Open(VoiceCommand.Destination.CALENDAR)
        if (Regex("\\b(cerca|ricerca|ingredienti)\\b").containsMatchIn(text))
            return VoiceCommand.Open(VoiceCommand.Destination.SEARCH)
        return VoiceCommand.Unknown
    }
}
