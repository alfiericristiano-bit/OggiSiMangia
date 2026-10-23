package it.oggisimangia.app

/**
 * Context-sensitive next steps based on actual application screens and their
 * existing actions. The assistant never invents navigation destinations or
 * directly writes/deletes user data merely because it offered a suggestion.
 */
object AiNextStep {
    data class Option(val label: String, val spokenCommand: String)

    fun suggestions(screen: String, previous: VoiceCommand? = null): List<Option> {
        val options = when {
            screen == "calendar" -> listOf(
                Option("Controlla i pasti di oggi", "Cosa mangio oggi"),
                Option("Cerca una ricetta", "Apri la ricerca"),
                Option("Apri il ricettario", "Apri il ricettario")
            )
            screen.startsWith("search") || screen == "results" -> listOf(
                Option("Cerca con un ingrediente", "Apri la ricerca"),
                Option("Scegli fra le ricette salvate", "Apri il ricettario"),
                Option("Rivedi le fonti", "Apri le fonti")
            )
            screen.startsWith("editor") || screen == "ocr" -> listOf(
                Option("Apri il ricettario", "Apri il ricettario"),
                Option("Consulta il calendario", "Apri il calendario")
            )
            screen == "archive" -> listOf(
                Option("Controlla i pasti di domani", "Cosa mangio domani"),
                Option("Cerca nuove ricette", "Apri la ricerca"),
                Option("Apri il calendario", "Apri il calendario")
            )
            screen == "sources" || screen == "ai-settings" -> listOf(
                Option("Cerca una ricetta", "Apri la ricerca"),
                Option("Apri il calendario", "Apri il calendario")
            )
            screen == "summary" -> listOf(
                Option("Consulta il calendario", "Apri il calendario"),
                Option("Cerca ricette", "Apri la ricerca")
            )
            else -> listOf(
                Option("Apri il calendario", "Apri il calendario"),
                Option("Apri il ricettario", "Apri il ricettario")
            )
        }
        val extra = when (previous) {
            is VoiceCommand.PlanSaved, is VoiceCommand.AutoPlan ->
                Option("Verifica il calendario aggiornato", "Apri il calendario")
            is VoiceCommand.Search, is VoiceCommand.GuidedMealSearch ->
                Option("Consulta anche il ricettario", "Apri il ricettario")
            is VoiceCommand.ReadMeals, is VoiceCommand.ShowDay ->
                Option("Cerca una ricetta per il giorno", "Apri la ricerca")
            else -> null
        }
        return (listOfNotNull(extra) + options).distinctBy { it.spokenCommand }.take(3)
    }
}
