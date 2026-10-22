package it.oggisimangia.app

import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class RecipeSourcePagesTest {
    @After fun reset() { RecipeSources.configure(RecipeSources.defaults) }

    @Test fun moreThanTenOnFirstSiteDoesNotHideOtherSources() {
        RecipeSources.configure(linkedMapOf(
            "GialloZafferano" to "giallozafferano.it",
            "Misya" to "misya.info"
        ))
        val giallo = (1..24).map { i ->
            WebRecipe("Pasta n. $i", "", "https://ricette.giallozafferano.it/Pasta-$i.html",
                "GialloZafferano", ingredients = "100 g pasta")
        }
        val misya = (1..16).map { i ->
            WebRecipe("Pasta altra n. $i", "", "https://www.misya.info/ricetta/pasta-$i.htm",
                "Misya", ingredients = "100 g pasta")
        }
        val mixed = giallo + misya
        assertEquals(listOf("GialloZafferano" to 24, "Misya" to 16),
            RecipeSourcePages.counts(mixed))
        assertEquals(10, RecipeSourcePages.page(mixed,"giallozafferano.it",0).size)
        assertEquals(10, RecipeSourcePages.page(mixed,"giallozafferano.it",10).size)
        assertEquals(4, RecipeSourcePages.page(mixed,"giallozafferano.it",20).size)
        assertEquals(10, RecipeSourcePages.page(mixed,"misya.info",0).size)
        assertEquals(6, RecipeSourcePages.page(mixed,"misya.info",10).size)
        assertTrue(RecipeSourcePages.page(mixed,"misya.info",0).all { it.source == "Misya" })
    }

    @Test fun hiddenOrDeletedWebsiteDoesNotReappear() {
        RecipeSources.configure(linkedMapOf("Misya" to "misya.info"))
        val recipes = listOf(
            WebRecipe("Pasta", "", "https://ricette.giallozafferano.it/Test.html", "GialloZafferano"),
            WebRecipe("Pasta", "", "https://www.misya.info/ricetta/pasta.htm", "Misya")
        )
        assertEquals(listOf("Misya" to 1), RecipeSourcePages.counts(recipes))
    }
}
