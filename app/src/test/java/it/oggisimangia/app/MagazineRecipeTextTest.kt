package it.oggisimangia.app

import org.junit.Assert.*
import org.junit.Test

class MagazineRecipeTextTest {
    @Test fun parsesMagazineSections() {
        val raw = """
            Pagina 1
            Lasagne alle verdure
            Ingredienti
            300 g lasagne
            2 zucchine
            100 g formaggio
            Preparazione
            1. Lava le zucchine.
            2. Disponi a strati e cuoci 30 minuti.
        """.trimIndent()
        val guess = MagazineRecipeText.suggest(raw)
        assertEquals("Lasagne alle verdure", guess.title)
        assertTrue(guess.ingredients.contains("2 zucchine"))
        assertTrue(guess.steps.contains("1. Lava le zucchine."))
        assertTrue(guess.steps.contains("2. Disponi a strati"))
    }

    @Test fun doesNotInventIngredientsWithoutHeader() {
        val guess = MagazineRecipeText.suggest("Pollo al limone\nTaglia il pollo.\nCuoci 15 minuti.")
        assertEquals("Pollo al limone", guess.title)
        assertTrue(guess.ingredients.isBlank())
        assertTrue(guess.steps.contains("Taglia il pollo"))
    }
}
