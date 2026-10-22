package it.oggisimangia.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class VoiceCommandsTest {
    private val today = LocalDate.of(2026, 10, 22)

    @Test fun searchRequiresFirstMainIngredient() {
        assertEquals(
            VoiceCommand.Search(listOf("pasta", "pomodoro", "melanzane")),
            VoiceCommands.parse("Cerca ricette con pasta, pomodoro e melanzane", today)
        )
        assertEquals(
            VoiceCommand.Search(listOf("pollo", "patate")),
            VoiceCommands.parse("Consigliami ricette a base di pollo e patate", today)
        )
    }

    @Test fun searchWithoutIngredientsOpensForm() {
        assertEquals(VoiceCommand.Open(VoiceCommand.Destination.SEARCH),
            VoiceCommands.parse("Cerca ricette", today))
    }

    @Test fun readMealAndDay() {
        assertEquals(VoiceCommand.ReadMeals(today.plusDays(1), null),
            VoiceCommands.parse("Cosa mangio domani?", today))
        assertEquals(VoiceCommand.ReadMeals(today, "Cena"),
            VoiceCommands.parse("Dimmi cosa ho per cena oggi", today))
    }

    @Test fun planSavedRecipeNeedsExplicitMeal() {
        assertEquals(
            VoiceCommand.PlanSaved("pasta al pomodoro", "Cena", today.plusDays(1)),
            VoiceCommands.parse("Pianifica pasta al pomodoro a cena domani", today)
        )
        assertEquals(
            VoiceCommand.PlanSaved("risotto ai funghi", "Pranzo", today),
            VoiceCommands.parse("Aggiungi risotto ai funghi per pranzo oggi", today)
        )
    }

    @Test fun browsingAndHelp() {
        assertEquals(VoiceCommand.ShowDay(today.plusDays(2)),
            VoiceCommands.parse("Vai al giorno dopodomani", today))
        assertEquals(VoiceCommand.Open(VoiceCommand.Destination.ARCHIVE),
            VoiceCommands.parse("Apri il ricettario", today))
        assertEquals(VoiceCommand.Open(VoiceCommand.Destination.SOURCES),
            VoiceCommands.parse("Mostra le fonti di ricerca", today))
        assertEquals(VoiceCommand.Help, VoiceCommands.parse("Aiuto", today))
    }

    @Test fun unknownAndDestructiveCommandsNeverExecute() {
        assertEquals(VoiceCommand.Unknown, VoiceCommands.parse("Elimina tutte le ricette", today))
        assertEquals(VoiceCommand.Unknown, VoiceCommands.parse("Cancella il database", today))
    }

    @Test fun destructiveRequestsWithNavigationWordsAreNotInterpretedAsOpen() {
        assertEquals(VoiceCommand.Unknown, VoiceCommands.parse("Elimina il ricettario", today))
        assertEquals(VoiceCommand.Unknown, VoiceCommands.parse("Svuota il calendario", today))
        assertEquals(VoiceCommand.Unknown, VoiceCommands.parse("Cancella tutte le fonti", today))
    }

    @Test fun relativeDayDoesNotBecomeAnIngredient() {
        assertEquals(VoiceCommand.Search(listOf("pasta", "pomodoro")),
            VoiceCommands.parse("Cerca ricette con pasta e pomodoro per domani", today))
    }
}
