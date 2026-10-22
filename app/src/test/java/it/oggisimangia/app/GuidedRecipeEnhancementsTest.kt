package it.oggisimangia.app

import org.junit.Assert.*
import org.junit.Test

class GuidedRecipeEnhancementsTest {
    @Test fun dietaryCategoriesAreAdvisoryAndUsePublishedIngredients() {
        val legumes = "250 g ceci\n150 g pomodori\nolio"
        assertEquals(true, RecipeTypeFilter.match(legumes, "Vegana"))
        assertEquals(true, RecipeTypeFilter.match(legumes, "Vegetariana"))
        assertEquals(false, RecipeTypeFilter.match("320 g pasta\n100 g pancetta", "Vegetariana"))
        assertEquals(false, RecipeTypeFilter.match("400 g riso\n50 g parmigiano", "Vegana"))
        assertEquals(true, RecipeTypeFilter.match("200 g salmone\n40 g limone", "Con pesce"))
        assertNull(RecipeTypeFilter.match("", "Vegetariana"))
        assertTrue(RecipeTypeFilter.warning("Senza glutine (da verificare)").contains("NON certifica"))
    }

    @Test fun completeAndPartialSearchKeepOnlyPrimary() {
        val url="https://ricette.giallozafferano.it/"
        fun recipe(name:String,tail:String)=WebRecipe(name,"",url+tail,"GialloZafferano",
            ingredients=when(name){
                "Risotto ai funghi"->"320 g riso\n150 g funghi\n40 g burro"
                "Risotto allo zafferano"->"320 g riso\n1 bustina zafferano"
                else->"250 g pasta\n150 g funghi"
            })
        val hits=RecipeRanking.orderFlexible(listOf(recipe("Risotto allo zafferano","Risotto-zafferano.html"),
            recipe("Pasta ai funghi","Pasta-funghi.html"),recipe("Risotto ai funghi","Risotto-funghi.html")),
            "riso, funghi")
        assertEquals(listOf("Risotto ai funghi","Risotto allo zafferano"),hits.map { it.title })
        assertEquals(listOf("funghi"), RecipeValidation.missingIngredients(hits[1].ingredients,
            "riso, funghi"))
    }

    @Test fun OCRReconstructsMultiLineTitleBulletsAndDetails() {
        val pages=listOf(
            MagazineRecipeText.Section(MagazineRecipeText.WHOLE,
                "23\nSpaghetti con\nmelanzane e\npomodoro\nIngredienti\n• 350 g spaghetti\n• 2 melanzane\nPreparazione\n1. Taglia le melanzane.\n2. Cuoci la pasta.\nPubblicità"),
            MagazineRecipeText.Section(MagazineRecipeText.INGREDIENTS,
                "Ingredienti\n● 350 g spaghetti\n○ 2 melanzane\n▪ 200 g pomodori\nPagina 24"),
            MagazineRecipeText.Section(MagazineRecipeText.STEPS,
                "Preparazione\n1. Lava e taglia le melanzane.\n2. Cuoci la pasta in acqua bollente\nsalata per 10 minuti.\n3. Unisci i pomodori."),
            MagazineRecipeText.Section(MagazineRecipeText.DETAILS,
                "Preparazione: 15 min\nCottura: 30 min\nDifficoltà: Facile\nDosi: 4 persone")
        )
        val guess=MagazineRecipeText.suggestSections(pages)
        assertEquals("Spaghetti con melanzane e pomodoro", guess.title)
        assertTrue(guess.ingredients.contains("200 g pomodori"))
        assertFalse(guess.ingredients.contains("Pagina"))
        assertTrue(guess.steps.contains("acqua bollente salata"))
        assertEquals("15 min",guess.prepTime)
        assertEquals("30 min",guess.cookTime)
        assertEquals("Facile",guess.difficulty)
        assertEquals("4 persone",guess.portions)
        assertFalse(guess.steps.contains("Pubblicità"))
    }
}
