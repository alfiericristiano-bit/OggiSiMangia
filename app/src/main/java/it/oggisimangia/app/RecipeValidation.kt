package it.oggisimangia.app

/** Verify cooking dishes only from published structured recipe data, never page snippets. */
object RecipeValidation {
    private val editorialTitle = Regex(
        "^(?:come\\s+(?:congelare|conservare|pulire|tagliare|surgelare|fare|cucinare|preparare)|" +
            "cosa\\s+si\\s+pu[oò]|guida\\b|consigli\\b|trucchi\\b|errori\\b|" +
            "tutto\\s+(?:quello|ci[oò])|quanto\\s+tempo|i\\s+segreti\\b)",
        RegexOption.IGNORE_CASE
    )

    /** Every query ingredient must occur as an independent term in recipeIngredient. */
    fun containsEvery(published: String, requested: String): Boolean {
        val terms = RecipeRanking.terms(requested).distinctBy { it.lowercase() }
        if (terms.isEmpty()) return true
        if (published.isBlank()) return false
        return terms.all { term ->
            val words = term.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            val regex = Regex(
                "(?<![\\p{L}])" + words.joinToString("\\s+") { Regex.escape(it) } + "(?![\\p{L}])",
                RegexOption.IGNORE_CASE
            )
            published.lines().any { regex.containsMatchIn(it) }
        }
    }

    /** Reject editorial guides, partial metadata, and pages without actual cooking steps. */
    fun isDish(title: String, published: String, instructions: String): Boolean =
        title.isNotBlank() && !editorialTitle.containsMatchIn(title.trim()) &&
            published.lines().count { it.isNotBlank() } >= 2 && instructions.isNotBlank()
}
