package it.oggisimangia.app

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

/** Natural variants across UI destinations, filters, search, sourcing and safety. */
class NaturalVoiceIntentsTest {
    private val today = LocalDate.of(2026, 10, 23)

    private fun understood(spoken: String): VoiceCommand {
        val plan = VoiceFlowPlanner.build(spoken, today)
        assertNull("Clarification for: $spoken -> ${plan.clarification}", plan.clarification)
        assertNull("Conflict for: $spoken -> ${plan.conflict}", plan.conflict)
        assertEquals("Commands for: $spoken", 1, plan.commands.size)
        return plan.commands.single()
    }

    @Test fun manyArchivePhrasingsMeanSameNavigation() {
        listOf("Apri il ricettario", "Vorrei vedere le mie ricette",
            "Mostrami le ricette salvate", "Portami all'archivio",
            "Fammi vedere il ricettario").forEach {
            assertEquals(it, VoiceCommand.Open(VoiceCommand.Destination.ARCHIVE), understood(it))
        }
    }

    @Test fun speechFindsRecipesWithoutOneRigidSyntax() {
        val expected=VoiceCommand.Search(listOf("pollo","patate"))
        listOf("Cerca ricette con pollo e patate", "Vorrei una ricetta con pollo e patate",
            "Mi trovi ricette con pollo e patate",
            "Consigliami piatti con pollo e patate").forEach {
            assertEquals(it,expected,understood(it))
        }
    }

    @Test fun eveningMealAndCalendarDayAreParsed() {
        assertEquals(VoiceCommand.ReadMeals(today,"Cena"), understood("Che cosa mangiamo stasera?"))
        assertEquals(VoiceCommand.ReadMeals(today.plusDays(1),null),
            understood("Cosa mangio domani?"))
    }

    @Test fun filtersAndIngredientsAreAssignedWithoutSearch() {
        assertEquals(VoiceCommand.SetFilter(VoiceCommand.Filter.DIFFICULTY, "Facile"),
            understood("Imposta difficoltà facile"))
        assertEquals(VoiceCommand.SetFilter(VoiceCommand.Filter.DURATION, "Veloce"),
            understood("Metti il tempo veloce"))
        assertEquals(VoiceCommand.SetFilter(VoiceCommand.Filter.TYPE, "Vegetariana"),
            understood("Seleziona vegetariano"))
        assertEquals(VoiceCommand.SetIngredients(listOf("riso","funghi")),
            understood("Imposta ingrediente principale riso e funghi"))
    }

    @Test fun existingButtonsCanBeNamedInSeveralWays() {
        listOf("Premi il tasto salva ricetta", "Tocca il pulsante salva ricetta",
            "Memorizza la ricetta").forEach {
            assertEquals(it, VoiceCommand.PressButton("salva ricetta"),
                understood(it))
        }
        assertEquals(VoiceCommand.PressButton("pdf stampa"),
            understood("Stampa report del giorno"))
        assertEquals(VoiceCommand.PressButton("ingrediente"),
            understood("Aggiungi un ingrediente"))
        assertEquals(VoiceCommand.PressButton("foto ingredienti"),
            understood("Fotografa ingredienti"))
    }

    @Test fun prioritiesAreSpecificAndNeverGuessSite() {
        assertEquals(VoiceCommand.SourcePriority("misya", 1),
            understood("Metti Misya al primo posto"))
        assertEquals(VoiceCommand.SourcePriority("giallo zafferano", 2),
            understood("Sposta Giallo Zafferano in seconda posizione"))
    }

    @Test fun uncertainOrDestructiveLanguageDoesNotExecute() {
        listOf("Non aprire il calendario", "Cancella tutte le ricette",
            "Elimina l'archivio").forEach { spoken ->
            val plan=VoiceFlowPlanner.build(spoken,today)
            assertTrue("Unsafe request must not execute: $spoken",plan.commands.isEmpty())
        }
    }

    @Test fun buttonMatchingRejectsAmbiguityAndUnsafeButtons() {
        val labels=listOf("✅ Salva ricetta nel ricettario", "📷 Foto ingredienti",
            "📷 Foto preparazione", "🗑 Elimina questa voce")
        assertEquals(listOf(0),VoiceUiMatcher.matchingIndices("salva la ricetta",labels))
        assertEquals(listOf(1),VoiceUiMatcher.matchingIndices("fotografa ingredienti",labels))
        assertTrue(VoiceUiMatcher.isUnsafe(labels[3]))
        assertFalse(VoiceUiMatcher.isUnsafe(labels[0]))
        assertTrue(VoiceUiMatcher.matchingIndices("fotografa",labels).size>1)
    }
}
