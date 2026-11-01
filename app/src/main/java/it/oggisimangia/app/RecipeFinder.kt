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
    val feedbackCount: Int? = null,
    val relevance: Int = 0,
    val ingredients: String = "",
    val verified: Boolean = true
)

object RecipeFinder {
    fun isTrusted(url: String): Boolean = RecipeSources.trusted(url)

    fun search(
        ingredients: String,
        category: String,
        difficulty: String,
        duration: String
    ): List<WebRecipe> {
        val queries = RecipeSearchQueries.variants(ingredients)
        if (queries.isEmpty()) return emptyList()
        val sources = RecipeSources.sites.keys.toList()

        // Fetch editorial sites in parallel: trying the comma fallback must not
        // triple the waiting time for a single offline/blocked site.
        val sourcePool = java.util.concurrent.Executors.newFixedThreadPool(6)
        val collected = try {
            val calls = sources.map { name -> java.util.concurrent.Callable {
                val candidates = linkedMapOf<String, WebRecipe>()
                val main = RecipeValidation.mainIngredient(ingredients).lowercase()
                val entryPoints = if (main == "pollo") when (name) {
                    "Misya" -> listOf("https://www.misya.info/ricette-pollo.htm", "https://www.misya.info/ricette-petto-di-pollo.htm")
                    "Il Cucchiaio d'Argento" -> listOf("https://www.cucchiaio.it/ricette/carne_pollo~1/")
                    else -> emptyList()
                } else emptyList()
                for (address in (entryPoints + queries.map { RecipeSearchQueries.editorial(name, it) }).distinct()) {
                    try {
                        val document = Jsoup.connect(address)
                            .userAgent("Mozilla/5.0 (Android; OggiSiMangia/1.5.3)")
                            .referrer("https://www.google.com/")
                            .timeout(7000).maxBodySize(1_800_000).followRedirects(true).get()
                        for (recipe in extract(document, name).take(12)) {
                            candidates.putIfAbsent(recipe.url.substringBefore('?'), recipe)
                        }
                    } catch (_: Exception) {
                        // Retry with the next punctuation variant.
                    }
                    if (candidates.size >= 15) break
                }
                candidates.values.toList()
            } }
            sourcePool.invokeAll(calls, 27, java.util.concurrent.TimeUnit.SECONDS)
                .flatMap { try { if (it.isCancelled) emptyList() else it.get() }
                    catch (_: Exception) { emptyList() } }
                .distinctBy { it.url.substringBefore('?') }
        } finally { sourcePool.shutdownNow() }

        // Source cards are not enough: only published Recipe JSON-LD with
        // the full ingredient list AND cooking steps may become a result.
        val pool = java.util.concurrent.Executors.newFixedThreadPool(6)
        val verified = try {
            // Interleave the three editorial websites. Previously 18 Giallo hits
            // occupied every worker before other sites were checked.
            val sourcesInOrder = sources.map { site ->
                collected.filter { it.source == site }.take(10)
            }
            val balanced = RecipeDiscovery.roundRobin(sourcesInOrder)
            val calls = balanced.map { candidate -> java.util.concurrent.Callable {
                try {
                    val html = Jsoup.connect(candidate.url)
                        .userAgent("Mozilla/5.0 (Android; OggiSiMangia/1.8.1)")
                        .timeout(7500).maxBodySize(2_000_000).get().outerHtml()
                    RecipeDiscovery.preview(html, candidate.url, candidate.source, ingredients, allowPartial = true)
                } catch (_: Exception) {
                    if (RecipeValidation.mentionsMainInTitle(candidate.title, ingredients))
                        candidate.copy(verified = false) else null
                }
            } }
            pool.invokeAll(calls, 39, java.util.concurrent.TimeUnit.SECONDS)
                .mapIndexedNotNull { index, future ->
                    try {
                        if (future.isCancelled) {
                            balanced[index].takeIf {
                                RecipeValidation.mentionsMainInTitle(it.title, ingredients)
                            }?.copy(verified = false)
                        } else future.get()
                    } catch (_: Exception) {
                        balanced[index].takeIf {
                            RecipeValidation.mentionsMainInTitle(it.title, ingredients)
                        }?.copy(verified = false)
                    }
                }
        } finally { pool.shutdownNow() }

        // Category, difficulty and duration remain preferences, not exclusions.
        return RecipeRanking.orderFlexible(verified, ingredients)
    }

    private fun extract(doc: Document, source: String): List<WebRecipe> {
        val found = linkedMapOf<String, WebRecipe>()
        for (anchor in doc.select("h2 a[href], h3 a[href], h4 a[href], article a[href], .recipe-card a[href], [class*=recipe] a[href], a[href*=\"/ricetta/\"], a[href*=\"/ricette/\"]")) {
            val title = anchor.text().ifBlank { anchor.attr("title") }.ifBlank { anchor.select("img").firstOrNull()?.attr("alt").orEmpty() }.trim().replace(Regex("\\s+"), " ")
            val url = anchor.absUrl("href")
            if (title.length !in 6..130 || !isTrusted(url) ||
                !RecipeValidation.isLikelyRecipeLink(title, url) ||
                url.contains("/ricerca-ricette/") || url.contains("/category/") ||
                url.contains("/categorie/")) continue
            val host = Uri.parse(url).host?.lowercase().orEmpty()
            val expectedDomain = RecipeSources.sites[source]?.lowercase().orEmpty()
            if (expectedDomain.isBlank() || (host != expectedDomain && host != "www.$expectedDomain" && !host.endsWith(".$expectedDomain"))) continue
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
                "[itemprop=ratingCount], [itemprop=reviewCount]"
            ).firstOrNull()?.let { it.attr("content").ifBlank { it.text() } }.orEmpty()
            val feedbacks = Regex("\\d+").find(feedbackString)?.value?.toIntOrNull()

            found.putIfAbsent(url.substringBefore('?'), WebRecipe(
                title, summary, url, source, imageUrl, recipeCategory, diff,
                mins, rating, feedbacks, verified = false
            ))
            if (found.size >= 18) break
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


