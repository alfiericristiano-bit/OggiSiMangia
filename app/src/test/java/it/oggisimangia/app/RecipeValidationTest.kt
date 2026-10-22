package it.oggisimangia.app

import org.junit.Assert.*
import org.junit.Test

class RecipeValidationTest {
    @Test fun strictMatchingNeedsEveryPublishedIngredient() {
        val list = "200 g risotto\n3 carciofi\n30 g burro"
        assertTrue(RecipeValidation.containsEvery(list, "carciofi, burro"))
        assertFalse(RecipeValidation.containsEvery(list, "riso, carciofi"))
        assertFalse(RecipeValidation.containsEvery(list, "burro, limone"))
    }

    @Test fun descriptionsDoNotCountForMatching() {
        val html = """<script type="application/ld+json">{
            "@type":"Recipe","name":"Risotto ai carciofi",
            "description":"Con carciofi, riso e limone.",
            "recipeIngredient":["200 g riso","3 carciofi","olio"],
            "recipeInstructions":["Cuocere il riso, pulire i carciofi e unirli."]}</script>"""
        val url = "https://www.cucchiaio.it/ricetta/risotto-ai-carciofi/"
        assertNull(RecipeDiscovery.preview(html,url,"Il Cucchiaio d'Argento","riso, limone"))
        assertNotNull(RecipeDiscovery.preview(html,url,"Il Cucchiaio d'Argento","riso, carciofi"))
    }

    @Test fun italianFormsAndProcessedIngredientsAreAccepted() {
        val lines = "150 g lenticchie rosse\n200 ml passata di pomodoro\n1 cipolla"
        assertTrue(RecipeValidation.containsEvery(lines, "lenticchia, pomodori"))
        assertTrue(RecipeValidation.containsEvery(lines, "lenticchie, pomodoro"))
        assertTrue(RecipeValidation.containsEvery(lines, "lenticchia, cipolle"))
        assertFalse(RecipeValidation.containsEvery(lines, "lenticchia, carciofi"))
        assertTrue(RecipeValidation.containsEvery("100 g crema di pomodoro\n200 g lenticchie", "pomodori"))
    }

    @Test fun genericGuidesAndIncompleteRecipesAreRejected() {
        assertFalse(RecipeValidation.isDish("Come congelare i carciofi", "carciofi\nacqua", "Congelare"))
        assertFalse(RecipeValidation.isDish("Come fare un risotto", "riso\nacqua", "Cuocere"))
        assertFalse(RecipeValidation.isDish("Risotto ai carciofi", "", "Cuocere"))
        assertFalse(RecipeValidation.isDish("Risotto ai carciofi", "riso\ncarciofi", ""))
        assertTrue(RecipeValidation.isDish("Risotto ai carciofi", "riso\ncarciofi", "Cuocere 20 minuti"))
    }

    @Test fun rankingNeverAcceptsDescriptionOnly() {
        val url = "https://ricette.giallozafferano.it/Risotto-ai-carciofi.html"
        val missing = WebRecipe("Risotto carciofi", "Con riso e carciofi", url, "Giallo", ingredients = "riso\nburro")
        val matching = missing.copy(ingredients = "riso\ncarciofi")
        assertEquals(listOf(matching), RecipeRanking.order(listOf(missing, matching),"riso, carciofi"))
    }
    @Test fun genericIndexPagesAreNotIndividualRecipes() {
        assertFalse(RecipeValidation.isLikelyRecipeLink("Tutte le ricette",
            "https://www.giallozafferano.it/ricette/"))
        assertFalse(RecipeValidation.isLikelyRecipeLink(
            "Pasta: ricette veloci - Le ricette di GialloZafferano",
            "https://www.giallozafferano.it/ricette/pasta-ricette-veloci"))
        assertFalse(RecipeValidation.isLikelyRecipeLink("Le ricette di pollo",
            "https://www.misya.info/ricette/ricette-di-pollo"))
        assertFalse(RecipeValidation.isLikelyRecipeLink("Primi piatti",
            "https://www.giallozafferano.it/ricette-cat/Primi-piatti"))
        assertTrue(RecipeValidation.isLikelyRecipeLink("Pasta e fagioli",
            "https://ricette.giallozafferano.it/Pasta-e-fagioli.html"))
    }

    @Test fun primaryPastaNeverMeansShortcrustPastry() {
        assertFalse(RecipeValidation.mentionsMainInTitle("Strudel di mele con pasta frolla", "pasta"))
        assertFalse(RecipeValidation.containsEvery("250 g pasta sfoglia\n2 mele\nburro",
            "pasta, mele"))
        assertTrue(RecipeValidation.mentionsMainInTitle("Spaghetti alla norma", "pasta, melanzane"))
        assertTrue(RecipeValidation.mentionsMainInTitle("Risotto ai funghi", "riso, funghi"))
        assertTrue(RecipeValidation.containsEvery("250 g spaghetti\n2 melanzane",
            "spaghetti, melanzane"))
    }

    @Test fun unverifiedMustMentionMainAndMustNotBeACollection() {
        val url = "https://ricette.giallozafferano.it/"
        val candidates = listOf(
            WebRecipe("Tutte le ricette", "", url+"Tutte-le-ricette.html",
                "GialloZafferano", verified=false),
            WebRecipe("Strudel di mele con pasta frolla", "", url+"Strudel.html",
                "GialloZafferano", verified=false),
            WebRecipe("Pasta e fagioli", "", url+"Pasta-e-fagioli.html",
                "GialloZafferano", verified=false),
            WebRecipe("Pasta al pomodoro", "", url+"Pasta-al-pomodoro.html",
                "GialloZafferano", ingredients="200 g pasta\n200 g pomodori", verified=true),
            WebRecipe("Pasta senza pomodoro", "", url+"Pasta-senza-pomodoro.html",
                "GialloZafferano", ingredients="200 g pasta\n100 g burro", verified=true)
        )
        val found = RecipeRanking.order(candidates,"pasta, pomodoro")
        assertEquals(2,found.size)
        assertEquals(listOf("Pasta al pomodoro","Pasta e fagioli"), found.map { it.title })
        assertTrue(found[0].verified)
        assertFalse(found[1].verified)
    }

    @Test fun mainAndSecondaryAreReportedIndividually() {
        val published = "300 g riso\n150 g piselli\n1 cipolla"
        assertEquals("riso",RecipeValidation.mainIngredient("riso, pollo, cipolla"))
        assertEquals(listOf("riso", "cipolla"), RecipeValidation.foundIngredients(published,
            "riso, pollo, cipolla"))
        assertEquals(listOf("pollo"),RecipeValidation.missingIngredients(published,
            "riso, pollo, cipolla"))
    }

}
