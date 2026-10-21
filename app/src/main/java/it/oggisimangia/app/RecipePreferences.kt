package it.oggisimangia.app

/** Soft search preferences never exclude recipes whose ingredient list has been validated. */
object RecipePreferences {
    fun notices(recipe: WebRecipe, category: String, difficulty: String, duration: String): List<String> {
        val result = mutableListOf<String>()
        if (category.isNotBlank()) {
            if (recipe.category.isBlank()) result += "Categoria non indicata dalla fonte (preferita: $category)."
            else if (!recipe.category.equals(category, true))
                result += "Categoria: cercavi $category, questa ricetta è ${recipe.category}."
        }
        if (difficulty.isNotBlank() && difficulty != "Non disponibile") {
            if (recipe.difficulty.isBlank())
                result += "Difficoltà non indicata dalla fonte (preferita: $difficulty)."
            else if (!recipe.difficulty.equals(difficulty, true) &&
                !(difficulty == "Facile" && recipe.difficulty.equals("Molto facile", true)))
                result += "Difficoltà: cercavi $difficulty, questa ricetta è ${recipe.difficulty}."
        }
        val maximum = when (duration) { "Volante" -> 15; "Veloce" -> 30; "Media" -> 60; else -> null }
        if (maximum != null) {
            if (recipe.minutes == null)
                result += "Tempo non indicato dalla fonte (preferito: massimo $maximum minuti)."
            else if (recipe.minutes > maximum)
                result += "Tempo: cercavi massimo $maximum minuti, questa ricetta ne richiede ${recipe.minutes}."
        }
        return result
    }
}
