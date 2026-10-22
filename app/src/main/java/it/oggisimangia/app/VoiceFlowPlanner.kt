package it.oggisimangia.app

import java.time.LocalDate
import java.util.Locale

/** A bounded, inspectable plan. Unknown/contradictory instructions are never silently skipped. */
data class VoicePlan(
    val commands: List<VoiceCommand>,
    val clarification: String? = null,
    val conflict: String? = null
) {
    val needsConfirmation: Boolean get() = commands.any { it is VoiceCommand.PlanSaved }
}

object VoiceFlowPlanner {
    private val separator = Regex("\\s*(?:[;]|\\b(?:e poi|poi|quindi|successivamente|dopodiche)\\b)\\s*",
        RegexOption.IGNORE_CASE)

    fun build(spoken: String, today: LocalDate = LocalDate.now()): VoicePlan {
        val statements = spoken.split(separator).map { it.trim() }.filter { it.isNotBlank() }
        if (statements.isEmpty()) return VoicePlan(emptyList(), "Non ho sentito un comando. Puoi ripetere?")
        if (statements.size > 6) return VoicePlan(emptyList(),
            "La sequenza supera sei comandi: dividila in più richieste.")
        // Ask for the missing part of an actionable goal rather than guessing.
        for ((index, raw) in statements.withIndex()) {
            val clause = raw.lowercase(Locale.ITALIAN).trim()
            if (Regex("^(?:organizza|organizzami|prepara|preparami|pianifica|proponi|suggerisci)\\\\b").containsMatchIn(clause) &&
                Regex("\\\\b(?:pranzo|cena)\\\\b").containsMatchIn(clause) &&
                Regex("^(?:organizza|organizzami|prepara|preparami|proponi|suggerisci)").containsMatchIn(clause) &&
                !Regex("\\\\b(?:con|a base di|usando)\\\\s+").containsMatchIn(clause)) {
                return VoicePlan(emptyList(),
                    "Nel passaggio ${index + 1}, quale ingrediente principale vuoi usare per questo pasto?")
            }
            if (Regex("^(?:pianifica|metti|aggiungi|programma)\\\\s+").containsMatchIn(clause) &&
                !Regex("\\\\b(?:pranzo|cena)\\\\b").containsMatchIn(clause)) {
                return VoicePlan(emptyList(),
                    "Nel passaggio ${index + 1}, vuoi pianificare la ricetta per pranzo o per cena?")
            }
            if (Regex("\\\\b(?:oggi|domani|dopodomani)\\\\b").containsMatchIn(clause) &&
                Regex("\\\\bieri\\\\b").containsMatchIn(clause)) {
                return VoicePlan(emptyList(),
                    "Nel passaggio ${index + 1}, hai indicato giorni in conflitto: quale giorno intendi?")
            }
        }
        val actions = statements.map { VoiceCommands.parse(it, today) }
        val unknown = actions.indexOfFirst { it == VoiceCommand.Unknown }
        if (unknown >= 0) return VoicePlan(emptyList(),
            "Non ho capito il passaggio ${unknown + 1}: «${statements[unknown]}». Puoi riformularlo?")
        val planning = actions.filterIsInstance<VoiceCommand.PlanSaved>()
        // Never silently schedule the same dish twice in the same meal/date.
        val duplicate = planning.groupBy { Triple(it.title.lowercase(Locale.ITALIAN), it.meal, it.day) }
            .entries.firstOrNull { it.value.size > 1 }
        if (duplicate != null) return VoicePlan(emptyList(), conflict =
            "Hai richiesto più volte lo stesso piatto per lo stesso pasto. Vuoi correggere la sequenza?")
        val sameMeal = planning.groupBy { it.day to it.meal }
            .entries.firstOrNull { it.value.size > 1 }
        if (sameMeal != null) return VoicePlan(emptyList(), conflict =
            "Hai richiesto più ricette per lo stesso pasto e giorno. Dimmi quale sarà la principale " +
                "e quale l'alternativa, oppure organizziamole una alla volta.")
        val searches = actions.filterIsInstance<VoiceCommand.Search>()
        if (actions.dropLast(1).any { it is VoiceCommand.Search || it is VoiceCommand.GuidedMealSearch })
            return VoicePlan(emptyList(), conflict =
                "Una ricerca online deve essere l'ultimo passaggio: devo attendere che tu scelga una ricetta.")
        if (searches.size > 1 && searches.map { it.ingredients.first() }.distinct().size > 1)
            return VoicePlan(emptyList(), conflict =
                "Hai indicato ingredienti principali diversi nella stessa ricerca. Dimmi quale vuoi usare.")
        return VoicePlan(actions)
    }

    fun description(command: VoiceCommand): String = when (command) {
        is VoiceCommand.Open -> when (command.destination) {
            VoiceCommand.Destination.CALENDAR -> "Apri calendario"
            VoiceCommand.Destination.ARCHIVE -> "Apri ricettario"
            VoiceCommand.Destination.SEARCH -> "Apri ricerca ricette"
            VoiceCommand.Destination.EDITOR -> "Apri nuova ricetta"
            VoiceCommand.Destination.SUMMARY -> "Mostra riepilogo"
            VoiceCommand.Destination.SOURCES -> "Apri fonti di ricerca"
        }
        is VoiceCommand.GuidedMealSearch -> "Organizza ${command.meal.lowercase()} del ${command.day} cercando ricette con ingrediente principale ${command.ingredients.first()}"
        is VoiceCommand.Search -> "Cerca ricette con ingrediente principale ${command.ingredients.first()}" +
            (if (command.ingredients.size > 1) " e ${command.ingredients.drop(1).joinToString(", ")}" else "")
        is VoiceCommand.ShowDay -> "Mostra calendario del ${command.day}"
        is VoiceCommand.ReadMeals -> "Leggi ${command.meal?.lowercase() ?: "pasti"} del ${command.day}"
        is VoiceCommand.PlanSaved -> "Pianifica ${command.title} per ${command.meal.lowercase()} del ${command.day}"
        VoiceCommand.Help -> "Mostra i comandi disponibili"
        VoiceCommand.Unknown -> "Non riconosciuto"
    }

    fun suggestion(command: VoiceCommand): String = when (command) {
        is VoiceCommand.Search, is VoiceCommand.GuidedMealSearch ->
            "Ora puoi scegliere una ricetta verificata e aggiungerla al calendario."
        is VoiceCommand.ShowDay, is VoiceCommand.ReadMeals ->
            "Vuoi cercare una ricetta per questo giorno o consultare il ricettario?"
        is VoiceCommand.Open -> when (command.destination) {
            VoiceCommand.Destination.ARCHIVE -> "Puoi chiedermi di pianificare una ricetta salvata."
            VoiceCommand.Destination.SEARCH -> "Dimmi l'ingrediente principale: per esempio «cerca ricette con pollo e patate»."
            VoiceCommand.Destination.CALENDAR -> "Puoi chiedermi «cosa mangio domani?»."
            else -> "Puoi darmi un altro comando o una sequenza con «e poi»."
        }
        is VoiceCommand.PlanSaved -> "Vuoi consultare i pasti di quel giorno?"
        else -> "Puoi darmi un comando oppure concatenarne più di uno con «e poi»."
    }
}
