package it.oggisimangia.app

import android.net.Uri
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder

/**
 * Cerca schede sintetiche su siti editoriali noti. Non copia ricette integrali e non
 * inventa recensioni o durate non presenti nei dati pubblicati.
 * I parser sono best-effort: i siti possono modificare l'HTML o bloccare le richieste.
 */
data class WebRecipe(
    val title: String,
    val summary: String,
    val url: String,
    val source: String,
    val imageUrl: String = "",
    val category: String = "",
    val difficulty: String = "",
    val minutes: Int? = null,
    val rating: Double? = null,
    val feedbackCount: Int? = null
)

object RecipeFinder {
    private val acceptedHosts = listOf(
        "giallozafferano.it", "cucchiaio.it", "fattoincasadabenedetta.it"
    )

    fun isTrusted(url: String): Boolean = try {
        val uri = Uri.parse(url)
        val host = uri.host?.lowercase() ?: ""
        uri.scheme == "https" && acceptedHosts.any { host == it || host.endsWith(".$it") }
    } catch (_: Exception) { false }

    fun search(
        ingredients: String,
        category: String,
        difficulty: String,
        duration: String
    ): List<WebRecipe> {
        val terms = ingredients.split(',', ';', '\n')
            .map { it.trim() }.filter { it.isNotBlank() }.take(6)
        if (terms.isEmpty()) return emptyList()

        val query = terms.joinToString(" ")
        val encoded = URLEncoder.encode(query, "UTF-8")
        val sources = listOf(
            Pair("GialloZafferano",
                "https://www.giallozafferano.it/ricerca-ricette/" +
                    encoded.replace("+", "%2B") + "/"),
            Pair("Fatto in Casa da Benedetta",
                "https://www.fattoincasadabenedetta.it/?s=$encoded"),
            Pair("Il Cucchiaio d'Argento", "https://www.cucchiaio.it/?s=$encoded")
        )

        val collected = linkedMapOf<String, WebRecipe>()
        // Prima fonte: pagine di ricerca editoriali con dati di recensione quando esposti.
        // Consulta sempre tutte e tre le fonti prima di ordinare i risultati.
        for ((name, address) in sources) {
            try {
                val document = Jsoup.connect(address)
                    .userAgent("Mozilla/5.0 (compatible; OggiSiMangia/1.1; Android)")
                    .referrer("https://www.google.com/")
                    .timeout(8500)
                    .maxBodySize(1_800_000)
                    .followRedirects(true)
                    .get()
                for (recipe in extract(document, name)) {
                    if (collected.size >= 45) break
                    collected.putIfAbsent(recipe.url.substringBefore('?'), recipe)
                }
            } catch (_: Exception) {
                // Una fonte può non consentire lettura automatica: nessun dato inventato.
            }
        }

        val maxMinutes = when (duration) {
            "Volante" -> 15
            "Veloce" -> 30
            "Media" -> 60
            else -> Int.MAX_VALUE
        }
        fun rank(r: WebRecipe): Int {
            val text = (r.title + " " + r.summary).lowercase()
            return terms.count { t -> text.contains(t.lowercase()) }
        }
        // I dati di difficoltà e tempo non sempre sono forniti dalla fonte.
        // In quel caso la scheda resta visibile con l'attributo "non disponibile".
        return collected.values.filter { r ->
            (r.minutes == null || r.minutes <= maxMinutes) &&
                (r.difficulty.isBlank() || difficulty == "Difficile" ||
                    r.difficulty.equals(difficulty, true) ||
                    (difficulty == "Facile" && r.difficulty == "Molto facile")) &&
                (r.category.isBlank() || category.isBlank() ||
                    r.category.equals(category, true))
        }.sortedWith(
            compareByDescending<WebRecipe> { it.feedbackCount ?: -1 }
                .thenByDescending { rank(it) }
                .thenByDescending { it.rating ?: -1.0 }
        ).take(10)
    }

    private fun extract(doc: Document, source: String): List<WebRecipe> {
        val found = linkedMapOf<String, WebRecipe>()
        for (anchor in doc.select("h2 a[href], h3 a[href]")) {
            val title = anchor.text().trim().replace(Regex("\\s+"), " ")
            val url = anchor.absUrl("href")
            if (title.length !in 6..130 || !isTrusted(url) ||
                url.contains("/ricerca-ricette/") || url.contains("/category/") ||
                url.contains("/categorie/")) continue
            val host = Uri.parse(url).host?.lowercase().orEmpty()
            if (source == "GialloZafferano" &&
                host != "ricette.giallozafferano.it" && host != "www.giallozafferano.it") continue

            val card = findCard(anchor)
            val summary = card.select("p").map { it.text().trim() }
                .firstOrNull { it.length in 35..400 }?.take(230).orEmpty()
            val img = card.select("img").firstOrNull()
            val imageUrl = img?.let {
                listOf("data-src", "src", "data-lazy-src").firstNotNullOfOrNull { key ->
                    it.absUrl(key).takeIf { link -> link.startsWith("https://") }
                }
            }.orEmpty()
            val snippet = card.text().take(800)
            val mins = Regex("(\\d{1,3})\\s*(?:min(?:uti)?|min\\.)\\b", RegexOption.IGNORE_CASE)
                .find(snippet)?.groupValues?.getOrNull(1)?.toIntOrNull()
            val diff = Regex("molto facile|facile|media|difficile", RegexOption.IGNORE_CASE)
                .find(snippet)?.value.orEmpty().replaceFirstChar { it.uppercase() }
            val recipeCategory = when {
                snippet.contains("Antipasti", true) -> "Antipasto"
                snippet.contains("Contorni", true) -> "Contorno"
                snippet.contains("Primi piatti", true) -> "Primo"
                snippet.contains("Secondi piatti", true) -> "Secondo"
                snippet.contains("Piatti unici", true) -> "Piatto unico"
                snippet.contains("Dolci", true) -> "Dolce"
                else -> ""
            }
            val ratingString = card.select("[itemprop=ratingValue], [aria-label*=stelle], .rating")
                .firstOrNull()?.let { it.attr("content").ifBlank { it.text() } }.orEmpty()
            val rating = Regex("[0-5][,.][0-9]").find(ratingString)
                ?.value?.replace(',', '.')?.toDoubleOrNull()
            val feedbackString = card.select(
                "[itemprop=reviewCount], [itemprop=ratingCount], a[href*=comment], a[href*=recension]"
            ).firstOrNull()?.let { it.attr("content").ifBlank { it.text() } }.orEmpty()
            val feedbacks = Regex("\\d+").find(feedbackString)?.value?.toIntOrNull()

            found.putIfAbsent(url.substringBefore('?'), WebRecipe(
                title, summary, url, source, imageUrl, recipeCategory, diff,
                mins, rating, feedbacks
            ))
            if (found.size >= 35) break
        }
        return found.values.toList()
    }

    private fun findCard(anchor: Element): Element {
        var node = anchor.parent() ?: return anchor
        repeat(5) {
            val paragraph = node.select("p").firstOrNull { it.text().length > 35 }
            if (paragraph != null && node.text().length < 1600) return node
            node = node.parent() ?: return node
        }
        return anchor.parent() ?: anchor
    }
}

