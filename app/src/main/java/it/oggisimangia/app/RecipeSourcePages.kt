package it.oggisimangia.app

/** Source-specific groups, in the order chosen by the user (never globally truncated). */
object RecipeSourcePages {
    fun forSource(items: List<WebRecipe>, domain: String): List<WebRecipe> =
        items.filter { RecipeSources.belongs(it.url, domain) }

    fun page(items: List<WebRecipe>, domain: String, offset: Int, size: Int = 10): List<WebRecipe> =
        forSource(items, domain).drop(offset.coerceAtLeast(0)).take(size.coerceAtLeast(1))

    fun counts(items: List<WebRecipe>): List<Pair<String, Int>> =
        RecipeSources.sites.map { (name, domain) -> name to forSource(items, domain).size }
}
