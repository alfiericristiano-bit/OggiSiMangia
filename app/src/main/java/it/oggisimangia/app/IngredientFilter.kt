package it.oggisimangia.app

object IngredientFilter {
    fun matches(ingredients: String, query: String): Boolean {
        val terms = query.split(',', ';', '\n').map { it.trim() }.filter { it.isNotBlank() }
        return terms.isEmpty() || terms.any { ingredients.contains(it, ignoreCase = true) }
    }
}
