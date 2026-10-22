package it.oggisimangia.app

import java.net.URLEncoder

/**
 * Separate the human-entered ingredient list from queries sent to external sites.
 * Retain each ingredient as one term for verification; try the comma-terminated
 * form first (as used in older successful searches), then less restrictive forms.
 */
object RecipeSearchQueries {
    fun terms(ingredients: String): List<String> =
        ingredients.split(',', ';', '\n').map { it.trim() }.filter { it.isNotBlank() }.take(10)

    fun variants(ingredients: String): List<String> {
        val terms = terms(ingredients)
        if (terms.isEmpty()) return emptyList()
        return listOf(
            terms.joinToString(", ", postfix = ","),
            terms.joinToString(", "),
            terms.joinToString(" ")
        ).distinct()
    }

    fun bing(domain: String, query: String, offset: Int = 0): String {
        val text = "site:$domain ricetta $query"
        return "https://www.bing.com/search?q=" + URLEncoder.encode(text, "UTF-8") + "&format=rss" +
            (if (offset > 0) "&first=" + (offset + 1) else "")
    }

    fun editorial(name: String, query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        return when (name) {
            "GialloZafferano" -> "https://www.giallozafferano.it/ricerca-ricette/" +
                encoded.replace("+", "%20") + "/"
            "Fatto in Casa da Benedetta" -> "https://www.fattoincasadabenedetta.it/?s=$encoded"
            "Il Cucchiaio d'Argento" -> "https://www.cucchiaio.it/?s=$encoded"
            else -> error("Fonte diretta non supportata: $name")
        }
    }
}
