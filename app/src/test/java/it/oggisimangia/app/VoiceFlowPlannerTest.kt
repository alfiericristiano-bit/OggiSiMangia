package it.oggisimangia.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class VoiceFlowPlannerTest {
    private val today = LocalDate.of(2026, 10, 22)

    @Test fun multipleCommandsRemainInOrder() {
        val plan = VoiceFlowPlanner.build("Apri il ricettario e poi mostra il calendario", today)
        assertNull(plan.clarification)
        assertNull(plan.conflict)
        assertEquals(listOf(
            VoiceCommand.Open(VoiceCommand.Destination.ARCHIVE),
            VoiceCommand.Open(VoiceCommand.Destination.CALENDAR)
        ), plan.commands)
        assertFalse(plan.needsConfirmation)
    }

    @Test fun stopOnUnknownInsteadOfExecutingKnownPrefix() {
        val plan = VoiceFlowPlanner.build(
            "Apri il calendario e poi cancella tutte le ricette", today)
        assertTrue(plan.commands.isEmpty())
        assertTrue(plan.clarification!!.contains("passaggio 2"))
    }

    @Test fun multidayArchivedMealPlanIsBoundedAndMustBeAuthorized() {
        val plan = VoiceFlowPlanner.build("Organizzami le cene dei prossimi tre giorni", today)
        assertNull(plan.clarification)
        assertEquals(
            listOf(VoiceCommand.AutoPlan("Cena", today.plusDays(1), 3)),
            plan.commands
        )
        assertTrue(plan.needsConfirmation)
        assertEquals(
            VoiceCommand.Unknown,
            VoiceCommands.parse("Organizzami le cene dei prossimi dodici giorni", today)
        )
    }

    @Test fun automaticPlannerNeverAcceptsDestructiveInstructions() {
        val plan = VoiceFlowPlanner.build(
            "Organizzami le cene dei prossimi tre giorni e poi elimina tutto", today)
        assertTrue(plan.commands.isEmpty())
        assertNotNull(plan.clarification)
    }

    @Test fun multipleWritesAlwaysRequireConfirmation() {
        val plan = VoiceFlowPlanner.build(
            "Pianifica risotto ai funghi per pranzo domani e poi apri il calendario", today)
        assertNull(plan.clarification)
        assertTrue(plan.needsConfirmation)
        assertEquals(2, plan.commands.size)
    }

    @Test fun conflictingDuplicatedWritesRequireClarification() {
        val plan = VoiceFlowPlanner.build(
            "Pianifica risotto ai funghi per pranzo domani e poi pianifica risotto ai funghi per pranzo domani", today)
        assertNotNull(plan.conflict)
        assertTrue(plan.commands.isEmpty())
    }

    @Test fun searchMustBeFinalActionForAsynchronousResults() {
        val plan = VoiceFlowPlanner.build(
            "Cerca ricette con pollo e patate e poi apri il calendario", today)
        assertNotNull(plan.conflict)
        assertTrue(plan.commands.isEmpty())
    }

    @Test fun guidedMealGoalSetsDayMealAndIngredient() {
        val plan = VoiceFlowPlanner.build("Organizzami la cena di domani con pollo e patate", today)
        assertEquals(listOf(VoiceCommand.GuidedMealSearch(
            listOf("pollo","patate"), "Cena", today.plusDays(1)
        )), plan.commands)
        assertNull(plan.clarification)
    }

    @Test fun missingMainIngredientAsksQuestion() {
        val plan = VoiceFlowPlanner.build("Organizzami il pranzo di domani", today)
        assertTrue(plan.commands.isEmpty())
        assertTrue(plan.clarification.orEmpty().contains("ingrediente principale"))
        assertTrue(plan.clarification.orEmpty().contains("passaggio 1"))
    }

    @Test fun missingMealAsksQuestion() {
        val plan = VoiceFlowPlanner.build("Pianifica pasta al pomodoro domani", today)
        assertTrue(plan.commands.isEmpty())
        assertTrue(plan.clarification.orEmpty().contains("pranzo o per cena"))
        assertTrue(plan.clarification.orEmpty().contains("passaggio 1"))
    }

    @Test fun conflictingDayAsksQuestion() {
        val plan = VoiceFlowPlanner.build("Cosa mangio domani e ieri?", today)
        assertTrue(plan.commands.isEmpty())
        assertTrue(plan.clarification.orEmpty().contains("giorni in conflitto"))
    }
}
