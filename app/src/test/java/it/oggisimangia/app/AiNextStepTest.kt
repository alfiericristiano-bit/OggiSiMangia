package it.oggisimangia.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class AiNextStepTest {
    @Test fun calendarSuggestsActionsWithExecutableCommands() {
        val choices = AiNextStep.suggestions("calendar")
        assertEquals("Cosa mangio oggi", choices.first().spokenCommand)
        assertTrue(choices.size in 1..3)
        choices.forEach {
            val plan = VoiceFlowPlanner.build(it.spokenCommand, LocalDate.of(2026, 10, 23))
            assertNull(plan.clarification)
            assertNull(plan.conflict)
            assertTrue(plan.commands.isNotEmpty())
            assertFalse(plan.needsConfirmation)
        }
    }

    @Test fun recipeSearchSuggestsArchiveAndSources() {
        val choices = AiNextStep.suggestions("search")
        assertTrue(choices.any { it.spokenCommand == "Apri il ricettario" })
        assertTrue(choices.any { it.spokenCommand == "Apri le fonti" })
    }

    @Test fun plannedMealOffersCalendarFirstWithoutWriting() {
        val choices = AiNextStep.suggestions(
            "editor-new", VoiceCommand.PlanSaved("Risotto", "Cena", LocalDate.of(2026, 10, 24)))
        assertEquals("Apri il calendario", choices.first().spokenCommand)
        assertEquals(choices.distinctBy { it.spokenCommand }, choices)
    }
}
