package it.oggisimangia.app

import org.junit.Assert.*
import org.junit.Test

class RecipeSearchTest {
    @org.junit.After fun resetSources() { RecipeSources.configure(RecipeSources.defaults) }
    private fun item(title: String, url: String, rating: Double? = null, count: Int? = null) =
        WebRecipe(title, "", url, "Fonte", rating = rating, feedbackCount = count,
            ingredients = "100 g pasta\n200 g zucchine")

    @Test fun allElevenSourcesAreAllowedButNotHostedBlogsOrLookalikes() {
        assertEquals(11, RecipeSources.sites.size)
        RecipeSources.sites.values.forEach { assertTrue(RecipeSources.trusted("https://www.$it/ricetta/test")) }
        assertTrue(RecipeSources.trusted("https://ricette.giallozafferano.it/Pasta.html"))
        assertFalse(RecipeSources.trusted("https://blog.giallozafferano.it/autore/pasta"))
        assertFalse(RecipeSources.trusted("https://giallozafferano.it.example.org/"))
        assertFalse(RecipeSources.trusted("http://www.misya.info/ricetta/"))
    }
    @Test fun priorityNeverResurrectsIrrelevantGialloRecipes() {
        val relevant = item("Pasta zucchine", "https://www.misya.info/ricetta/pasta.htm", 5.0, 500)
        val giallo = item("Pasta zucchine", "https://ricette.giallozafferano.it/Pasta.html", 4.0, 100)
        val unrelated = item("Torta cioccolato", "https://ricette.giallozafferano.it/Torta.html",5.0,10000)
        val result = RecipeRanking.order(listOf(relevant, unrelated, giallo), "zucchine")
        assertEquals(listOf(giallo, relevant), result)
    }
    @Test fun manyPositiveVotesOutrankOnePerfectVoteAndMissingRatingsStayUnknown() {
        val few = item("Pasta zucchine", "https://ricetta.it/pasta-a", 5.0, 1)
        val many = item("Pasta zucchine", "https://ricetta.it/pasta-b",4.8,400)
        val unknown = item("Pasta zucchine", "https://ricetta.it/pasta-c")
        assertEquals(listOf(many,few,unknown), RecipeRanking.order(listOf(unknown,few,many),"zucchine"))
        assertEquals(-1.0, RecipeRanking.confidence(5.0,null),0.0)
    }
    @Test fun rssKeepsOnlyTheQueriedSource() {
        val xml = """<rss><channel><item><link>https://www.misya.info/ricetta/pasta.htm</link></item>
            <item><link>https://www.cookaround.com/ricetta/pasta.html</link></item>
            <item><link>https://blog.giallozafferano.it/x/pasta</link></item></channel></rss>"""
        assertEquals(listOf("https://www.misya.info/ricetta/pasta.htm"), RecipeDiscovery.rssLinks(xml,"misya.info"))
    }
    @Test fun structuredRecipeProvidesNormalizedRatingVoteCountAndIngredients() {
        val html = """<script type="application/ld+json">{"@graph":[{"@type":"Recipe",
            "name":"Pasta verde", "recipeIngredient":["200 g zucchine","100 g pasta"],
            "recipeInstructions":["Cuocere la pasta e unire le zucchine."], "recipeCategory":"Primi piatti",
            "totalTime":"PT25M", "aggregateRating":{"ratingValue":9,"worstRating":1,"bestRating":10,"ratingCount":120}}]}</script>"""
        val r = RecipeDiscovery.preview(html,"https://www.tavolartegusto.it/ricetta/pasta/","Tavolartegusto","zucchine")!!
        assertEquals(1,r.relevance)
        assertEquals(25,r.minutes)
        assertEquals("Primo",r.category)
        assertEquals(120,r.feedbackCount)
        assertEquals(4.555555,r.rating!!,0.00001)
        assertNull(RecipeDiscovery.preview(html,"https://www.tavolartegusto.it/ricetta/pasta/","Tavolartegusto","cioccolato"))
    }
    @Test fun articlesAndCommentsCannotBecomeRatedRecipes() {
        assertNull(RecipeDiscovery.preview("<title>Pasta</title>","https://www.agrodolce.it/pasta","Agrodolce","pasta"))
        val html = """<script type="application/ld+json">{"@type":"Recipe","name":"Pasta zucchine","commentCount":500}</script>"""
        assertNull(RecipeDiscovery.preview(html,"https://www.agrodolce.it/pasta","Agrodolce","zucchine"))
    }
    @Test fun userPriorityOverridesDefaultAndNewDomainsParticipate() {
        val sites = linkedMapOf("Nuova fonte" to "ricette.example.org", "Misya" to "misya.info")
        RecipeSources.defaults.forEach { (name,domain) -> if(domain !in sites.values) sites[name]=domain }
        RecipeSources.configure(sites)
        val custom=item("Pasta zucchine","https://ricette.example.org/pasta",4.5,100)
        val giallo=item("Pasta zucchine","https://ricette.giallozafferano.it/Pasta.html",5.0,1000)
        val misya=item("Pasta zucchine","https://www.misya.info/ricetta/pasta.htm",4.8,500)
        assertTrue(RecipeSources.trusted(custom.url))
        assertEquals(listOf(custom,misya,giallo),RecipeRanking.order(listOf(giallo,misya,custom),"zucchine"))
        assertEquals("esempio.it",RecipeSources.normalizeDomain("https://www.esempio.it/ricetta/pasta"))
        assertNull(RecipeSources.normalizeDomain("https://127.0.0.1/"))
        assertNull(RecipeSources.normalizeDomain("https://user:pass@esempio.it/"))
    }
}
