package it.oggisimangia.app

import org.junit.Assert.*
import org.junit.Test

class RecipeSearchIntentTest {
    @Test fun writtenRequestKeepsPrincipalIngredient() {
        assertEquals(listOf("pasta", "melanzane", "pomodoro"),
            RecipeSearchIntent.ingredients("Vorrei pasta con melanzane e pomodoro"))
    }

    @Test fun requestWithGenericRecipeWordsDoesNotTreatThemAsIngredients() {
        assertEquals(listOf("pollo", "patate"),
            RecipeSearchIntent.ingredients("Cercami una ricetta con pollo e patate per cena"))
    }

    @Test fun punctuationAndMultipleIngredientsRemainSeparate() {
        assertEquals(listOf("pasta", "pomodoro", "melanzane"),
            RecipeSearchIntent.ingredients("Cerca ricette con pasta, pomodoro e melanzane"))
    }

    @Test fun tooManyIngredientsAreLimitedAndEmptyPromptDoesNothing() {
        assertTrue(RecipeSearchIntent.ingredients("").isEmpty())
        assertTrue(RecipeSearchIntent.ingredients("per favore").isEmpty())
        val ten = (1..12).joinToString(", ") { "ingrediente$it" }
        assertEquals(10, RecipeSearchIntent.ingredients("Ricette con $ten").size)
    }

    @Test fun quickDinnerWithoutDeclaredIngredientsDoesNotInventOne() {
        assertTrue(RecipeSearchIntent.ingredients("cerco una ricetta con").isEmpty())
    }
}
