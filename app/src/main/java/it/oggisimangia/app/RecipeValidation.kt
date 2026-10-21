package it.oggisimangia.app

/** Verify cooking dishes only from published structured recipe data, never page snippets. */
object RecipeValidation {
    private val editorialTitle = Regex(
        "^(?:come\\s+(?:congelare|conservare|pulire|tagliare|surgelare|fare|cucinare|preparare)|" +
            "cosa\\s+si\\s+pu[oò]|guida\\b|consigli\\b|trucchi\\b|errori\\b|" +
            "tutto\\s+(?:quello|ci[oò])|quanto\\s+tempo|i\\s+segreti\\b)",
        RegexOption.IGNORE_CASE
    )

    /** Match all requested ingredients in published recipeIngredient, including Italian variants. */
    fun containsEvery(published: String, requested: String): Boolean =
        IngredientMatcher.containsEvery(published, requested)

    /** Reject editorial guides, partial metadata, and pages without actual cooking steps. */
    fun isDish(title: String, published: String, instructions: String): Boolean =
        title.isNotBlank() && !editorialTitle.containsMatchIn(title.trim()) &&
            published.lines().count { it.isNotBlank() } >= 2 && instructions.isNotBlank()
}
