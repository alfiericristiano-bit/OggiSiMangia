package it.oggisimangia.app

import org.junit.Assert.*
import org.junit.Test

class RecipeStepsTest {
    @Test fun numbersAreSequentialAfterEditsAndBlanksAreIgnored() {
        assertEquals("1. Tritare le verdure\n2. Cuocere 15 minuti",
            RecipeSteps.numbered(listOf("Tritare le verdure", "  ", "Cuocere 15 minuti")))
    }
    @Test fun existingNumberedRecipesRemainEditable() {
        val old = "1. Tritare le verdure\n2) Rosolare\n3. Cuocere"
        assertEquals(listOf("Tritare le verdure","Rosolare","Cuocere"), RecipeSteps.split(old))
    }
    @Test fun legacyFreeFormStepsAlsoOpen() {
        assertEquals(listOf("Tagliare","Cuocere"),RecipeSteps.split("Tagliare\nCuocere"))
    }
}
