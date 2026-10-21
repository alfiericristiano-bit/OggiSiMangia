package it.oggisimangia.app

import java.net.URI

object RecipeSources {
    val sites = linkedMapOf(
        "GialloZafferano" to "giallozafferano.it",
        "Il Cucchiaio d'Argento" to "cucchiaio.it",
        "Fatto in Casa da Benedetta" to "fattoincasadabenedetta.it",
        "Misya" to "misya.info",
        "Cookaround" to "cookaround.com",
        "La Cucina Italiana" to "lacucinaitaliana.it",
        "Tavolartegusto" to "tavolartegusto.it",
        "Ricetta.it" to "ricetta.it",
        "Sale&Pepe" to "salepepe.it",
        "RicetteDalMondo" to "ricettedalmondo.it",
        "Agrodolce" to "agrodolce.it"
    )
    fun domain(url: String): String = try { URI(url).host?.lowercase().orEmpty() } catch (_: Exception) { "" }
    fun isGiallo(url: String): Boolean = domain(url) in setOf("ricette.giallozafferano.it", "www.giallozafferano.it", "giallozafferano.it")
    fun trusted(url: String): Boolean = try {
        val uri = URI(url)
        val host = domain(url)
        uri.scheme == "https" && uri.rawUserInfo == null && (uri.port == -1 || uri.port == 443) &&
            sites.values.any { host == it || host == "www.$it" || (it == "giallozafferano.it" && host == "ricette.$it") }
    } catch (_: Exception) { false }
    fun belongs(url: String, domain: String): Boolean = trusted(url) &&
        (this.domain(url) == domain || this.domain(url) == "www.$domain" ||
            (domain == "giallozafferano.it" && isGiallo(url)))
}

object RecipeRanking {
    fun terms(ingredients: String) = ingredients.split(',', ';', '\n').map { it.trim() }.filter { it.isNotBlank() }
    fun relevance(text: String, ingredients: String) = terms(ingredients).count { text.contains(it, true) }
    // A published rating with many votes outweighs a perfect rating with only one vote.
    fun confidence(rating: Double?, count: Int?): Double {
        if (rating == null || count == null || count <= 0 || rating !in 0.0..5.0) return -1.0
        return (rating * count.toDouble() + 3.5 * 20) / (count.toDouble() + 20)
    }
    fun order(items: List<WebRecipe>, ingredients: String): List<WebRecipe> = items
        .filter { maxOf(it.relevance, relevance(it.title + " " + it.summary, ingredients)) > 0 }
        .sortedWith(compareByDescending<WebRecipe> { RecipeSources.isGiallo(it.url) }
            .thenByDescending { maxOf(it.relevance, relevance(it.title + " " + it.summary, ingredients)) }
            .thenByDescending { confidence(it.rating, it.feedbackCount) }
            .thenByDescending { it.feedbackCount ?: -1 }
            .thenBy { it.title.lowercase() })
}
