package it.oggisimangia.app

import org.junit.Assert.*
import org.junit.Test

class DishSearchTest {
    @Test fun categoriesAreDistinctAndHaveSubcategories() {
        assertTrue(DishSearch.categories.size >= 9)
        assertTrue(DishSearch.categories.all { it.subcategories.isNotEmpty() })
    }
    @Test fun dishSearchIsNotHardcodedToChickenOrCelery() {
        val p = DishSearch.plan(DishSearch.Request(DishSearch.Mode.DISH,
            "Primi piatti", "Riso e risotti", "funghi", listOf("zafferano")))
        assertTrue(p.queries.any { it.contains("risotto") && it.contains("funghi") })
        assertEquals("funghi, zafferano", p.preferredIngredients)
        assertEquals("", p.requiredIngredients)
    }
    @Test fun requiredIngredientsRemainRequired() {
        val p = DishSearch.plan(DishSearch.Request(DishSearch.Mode.DISH,
            "Secondi piatti", "Pesce", "salmone", listOf("limone"),
            DishSearch.IngredientRule.REQUIRED))
        assertEquals("salmone, limone", p.requiredIngredients)
        assertTrue(p.queries.any { it.contains("salmone") })
    }
    @Test fun surpriseWorksWithoutIngredients() {
        val p = DishSearch.plan(DishSearch.Request(DishSearch.Mode.SURPRISE))
        assertTrue(p.queries.isNotEmpty())
        assertEquals("", p.requiredIngredients)
    }
    @Test fun existingIngredientModeRemainsAvailable() {
        val p = DishSearch.plan(DishSearch.Request(DishSearch.Mode.INGREDIENTS,
            baseIngredient = "zucchine", extraIngredients = listOf("patate")))
        assertEquals("zucchine, patate", p.requiredIngredients)
    }
}
