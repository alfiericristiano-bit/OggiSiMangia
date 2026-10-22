package it.oggisimangia.app

import java.net.URI

/** Published ingredients, not snippets or titles, determine verified matching. */
object RecipeValidation {
    private val editorialTitle = Regex(
        "^(?:come\\s+(?:congelare|conservare|pulire|tagliare|surgelare|fare|cucinare|preparare)|" +
            "cosa\\s+si\\s+pu[oò]|guida\\b|consigli\\b|trucchi\\b|errori\\b|" +
            "tutto\\s+(?:quello|ci[oò])|quanto\\s+tempo|i\\s+segreti\\b)",
        RegexOption.IGNORE_CASE
    )
    // Index pages and article collections must never appear as individual dishes.
    private val collectionTitle = Regex(
        "\\b(?:ricette|ricettario|raccolta|raccolte|catalogo|idee\\s+per\\s+cucinare|" +
            "tutti\\s+i\\s+piatti|tutte\\s+le\\s+ricette|selezione\\s+di\\s+piatti)\\b",
        RegexOption.IGNORE_CASE
    )
    private val nonRecipePaths = listOf(
        "/ricerca", "/search", "/category/", "/categoria/", "/categorie/",
        "/tag/", "/tags/", "/author/", "/autori/", "/page/", "/privacy",
        "/contatti", "/collections/", "/raccolte/", "/ingredienti/",
        "/ricette-cat", "/cerca/", "/archivio/", "/blog/"
    )

    /** Require a plausible single recipe and exclude portals, guides and collections. */
    fun isLikelyRecipeLink(title: String, url: String): Boolean {
        if (title.length !in 5..150 || editorialTitle.containsMatchIn(title.trim()) ||
            collectionTitle.containsMatchIn(title)) return false
        val path = try { URI(url).path.orEmpty().lowercase().trimEnd('/') }
            catch (_: Exception) { return false }
        if (path.length < 5 || nonRecipePaths.any { it in path }) return false
        val slug = path.substringAfterLast('/').substringBeforeLast('.')
        if (slug.length < 4 || slug in setOf("ricette", "ricetta", "recipe", "recipes",
                "home", "pasta", "primi", "secondi", "dolci", "cucina")) return false
        if (slug.contains("ricette-") || slug.contains("-ricette") ||
            slug.contains("tutte-le-") || slug.contains("ricettario")) return false
        return true
    }

    /** Main ingredient is the first requested term. Confirm by published ingredient list,
     * and use title only to prioritize candidates whose content cannot yet be read.
     */
    fun mainIngredient(requested: String): String = RecipeSearchQueries.terms(requested)
        .firstOrNull().orEmpty()

    fun mentionsMainInTitle(title: String, requested: String): Boolean {
        val main = mainIngredient(requested)
        if (main.isBlank()) return false
        val lower = title.lowercase()
        if (main.lowercase() == "pasta") {
            val shapes = Regex(
                "\\b(?:spaghetti|spaghettoni|penne|fusilli|rigatoni|linguine|" +
                "tagliatelle|tagliolini|farfalle|orecchiette|maccheroni|bucatini|" +
                "lasagne|lasagna|cannelloni|pappardelle|gnocchetti|tortellini|ravioli)\\b"
            )
            if (shapes.containsMatchIn(lower)) return true
            // A cake made with shortcrust pastry is NOT a pasta-based main dish.
            if (Regex("\\bpasta\\s+(?:frolla|sfoglia|brisee|bris[eè]|fillo|phyllo|madre|" +
                "di\\s+(?:zucchero|mandorle|nocciole|pane)|per\\s+pizza)\\b")
                .containsMatchIn(lower)) return false
        }
        if (main.lowercase() == "riso" && Regex("\\brisott[oi]\\b").containsMatchIn(lower)) return true
        return IngredientMatcher.containsEvery(title, main)
    }

    /** Match all requested ingredients, including the first/primary one, in structured data. */
    fun containsEvery(published: String, requested: String): Boolean =
        IngredientMatcher.containsEvery(published, requested)

    fun foundIngredients(published: String, requested: String): List<String> =
        RecipeSearchQueries.terms(requested).filter {
            published.isNotBlank() && IngredientMatcher.containsEvery(published, it)
        }

    fun missingIngredients(published: String, requested: String): List<String> =
        RecipeSearchQueries.terms(requested).filterNot {
            published.isNotBlank() && IngredientMatcher.containsEvery(published, it)
        }

    fun isDish(title: String, published: String, instructions: String): Boolean =
        title.isNotBlank() && !editorialTitle.containsMatchIn(title.trim()) &&
            !collectionTitle.containsMatchIn(title) &&
            published.lines().count { it.isNotBlank() } >= 2 && instructions.isNotBlank()
}
