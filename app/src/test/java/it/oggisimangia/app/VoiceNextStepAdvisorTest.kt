package it.oggisimangia.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class VoiceNextStepAdvisorTest {
    private val day = LocalDate.of(2026, 10, 23)
    private fun ui(screen: String, recipes: Int = 0) =
        VoiceUiContext(screen, day, "Cena", recipes)

    @Test fun calendarOffersRealReadActionForSelectedDay() {
        val suggestion = VoiceNextStepAdvisor.suggest(
            VoiceCommand.Open(VoiceCommand.Destination.CALENDAR), ui("calendar"))
        assertEquals(VoiceCommand.ReadMeals(day, null), suggestion.command)
    }

    @Test fun archiveUsesAutoPlanOnlyWithEnoughSavedRecipes() {
        val empty = VoiceNextStepAdvisor.suggest(
            VoiceCommand.Open(VoiceCommand.Destination.ARCHIVE), ui("archive", 0))
        assertNull(empty.command)
        val available = VoiceNextStepAdvisor.suggest(
            VoiceCommand.Open(VoiceCommand.Destination.ARCHIVE), ui("archive", 4))
        assertTrue(available.command is VoiceCommand.AutoPlan)
        assertTrue(VoicePlan(listOf(available.command!!)).needsConfirmation)
    }

    @Test fun resultsNeverPretendToSelectOrSaveAnUnknownRecipe() {
        val suggestion = VoiceNextStepAdvisor.suggest(
            VoiceCommand.Search(listOf("pasta")), ui("results", 10))
        assertNull(suggestion.command)
        assertTrue(suggestion.message.contains("Salva"))
    }

    @Test fun planningOffersReadingScheduledDay() {
        val scheduled = VoiceNextStepAdvisor.suggest(
            VoiceCommand.PlanSaved("Risotto", "Cena", day), ui("calendar", 2))
        assertEquals(VoiceCommand.ReadMeals(day, "Cena"), scheduled.command)
    }

    @Test fun sourceSettingsReturnToAvailableSearchScreen() {
        assertEquals(
            VoiceCommand.Open(VoiceCommand.Destination.SEARCH),
            VoiceNextStepAdvisor.suggest(
                VoiceCommand.Open(VoiceCommand.Destination.SOURCES), ui("sources")).command)
    }
}
