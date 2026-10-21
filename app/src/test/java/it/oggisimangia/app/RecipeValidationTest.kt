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
}
