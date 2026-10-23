package it.oggisimangia.app

import java.time.LocalDate

/**
 * A grounded, deterministic map from current screen and its real controls
 * to a suggested next action. No model guesses a button or writes data here.
 * The user can say "sì" to accept an available step or give a different command.
 */
data class VoiceUiContext(
    val screen: String,
    val day: LocalDate,
    val meal: String,
    val savedRecipeCount: Int
)
data class VoiceNextStep(val message: String, val command: VoiceCommand? = null)

object VoiceNextStepAdvisor {
    fun suggest(last: VoiceCommand?, ui: VoiceUiContext): VoiceNextStep {
        return when (last) {
            is VoiceCommand.PlanSaved ->
                VoiceNextStep("Vuoi vedere il pasto aggiornato sul calendario?",
                    VoiceCommand.ReadMeals(last.day, last.meal))
            is VoiceCommand.AutoPlan ->
                VoiceNextStep("Vuoi verificare nel calendario il primo giorno pianificato?",
                    VoiceCommand.ReadMeals(last.start, last.meal))
            is VoiceCommand.Search, is VoiceCommand.GuidedMealSearch ->
                VoiceNextStep("Nei risultati puoi aprire una ricetta, verificarne gli ingredienti e poi scegliere Salva o Pianifica. Dimmi il prossimo comando.")
            is VoiceCommand.ReadMeals, is VoiceCommand.ShowDay ->
                VoiceNextStep("Vuoi aprire Cerca ricetta per completare il menù?",
                    VoiceCommand.Open(VoiceCommand.Destination.SEARCH))
            is VoiceCommand.Open -> when (last.destination) {
                VoiceCommand.Destination.CALENDAR ->
                    VoiceNextStep("Vuoi controllare i pasti del giorno selezionato?",
                        VoiceCommand.ReadMeals(ui.day, null))
                VoiceCommand.Destination.ARCHIVE ->
                    if (ui.savedRecipeCount >= 3)
                        VoiceNextStep("Hai ricette nel tuo ricettario. Vuoi organizzare le cene dei prossimi tre giorni usando quelle già salvate?",
                            VoiceCommand.AutoPlan("Cena", LocalDate.now().plusDays(1), 3))
                    else VoiceNextStep("Nel ricettario puoi aprire una ricetta, modificarla o scegliere Pianifica. Dimmi il nome del piatto che vuoi programmare.")
                VoiceCommand.Destination.SEARCH ->
                    VoiceNextStep("Inserisci l'ingrediente principale e, se vuoi, gli altri ingredienti. Poi avvia Cerca ricette. Puoi dettarmi per esempio: cerca ricette con patate e zucchine.")
                VoiceCommand.Destination.EDITOR ->
                    VoiceNextStep("Puoi scrivere una ricetta o fotografarne la pagina per avviare l'OCR. Controlla i campi, poi usa Salva ricetta.")
                VoiceCommand.Destination.SUMMARY ->
                    VoiceNextStep("Vuoi aprire il calendario e vedere i pasti?",
                        VoiceCommand.Open(VoiceCommand.Destination.CALENDAR))
                VoiceCommand.Destination.SOURCES ->
                    VoiceNextStep("Puoi ordinare le priorità e aggiungere siti. Vuoi tornare a Cerca ricetta?",
                        VoiceCommand.Open(VoiceCommand.Destination.SEARCH))
            }
            VoiceCommand.Help ->
                VoiceNextStep("Vuoi aprire il calendario per iniziare?",
                    VoiceCommand.Open(VoiceCommand.Destination.CALENDAR))
            VoiceCommand.Unknown, null -> when (ui.screen) {
                "calendar" -> VoiceNextStep("Vuoi cercare una ricetta?",
                    VoiceCommand.Open(VoiceCommand.Destination.SEARCH))
                "archive" -> VoiceNextStep("Nel ricettario puoi aprire o pianificare le ricette salvate. Dimmi quale vuoi usare.")
                "search" -> VoiceNextStep("Dimmi l'ingrediente principale da cercare.")
                "sources" -> VoiceNextStep("Vuoi tornare alla ricerca?",
                    VoiceCommand.Open(VoiceCommand.Destination.SEARCH))
                "summary" -> VoiceNextStep("Vuoi aprire il calendario?",
                    VoiceCommand.Open(VoiceCommand.Destination.CALENDAR))
                "editor", "ocr" -> VoiceNextStep("Controlla ingredienti, preparazione e foto prima di premere Salva.")
                else -> VoiceNextStep("Posso aprire Calendario, Ricette, Ricerca e le altre schede. Dimmi cosa vuoi fare.")
            }
        }
    }
}
