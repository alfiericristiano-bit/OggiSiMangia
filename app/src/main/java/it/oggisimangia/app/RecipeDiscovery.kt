package it.oggisimangia.app

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.net.URLEncoder
import java.time.Duration
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Bounded, parallel discovery across every selected site, followed by Recipe validation. */
object RecipeDiscovery {
    private fun download(url: String): String {
        var address = url
        repeat(4) {
            RecipeLink.requirePublic(address)
            val response = Jsoup.connect(address).userAgent("Mozilla/5.0 (Android; OggiSiMangia/1.3)")
                .timeout(6500).maxBodySize(2_000_000).ignoreContentType(true)
                .followRedirects(false).execute()
            if (response.statusCode() in 200..299) return response.body()
            require(response.statusCode() in 300..399)
            address = java.net.URL(java.net.URL(address), response.header("Location") ?: error("Redirect")).toString()
            require(RecipeSources.trusted(address) || RecipeSources.domain(address) == "www.bing.com")
        }
        error("Troppi reindirizzamenti")
    }

    internal fun rssLinks(xml: String, domain: String): List<String> = Jsoup.parse(xml, "", Parser.xmlParser())
        .select("item > link").map { it.text().trim() }.filter { RecipeSources.belongs(it, domain) }.distinct().take(4)

    /** RSS is preferred; some search responses come back as HTML instead. */
    internal fun searchLinks(body: String, domain: String): List<String> {
        val rss = rssLinks(body, domain)
        if (rss.isNotEmpty()) return rss
        return Jsoup.parse(body).select("li.b_algo h2 a[href], #b_results h2 a[href]")
            .map { it.attr("href").trim() }
            .filter { RecipeSources.belongs(it, domain) }.distinct().take(4)
    }

    internal fun schema(html: String): JSONObject? {
        val found = mutableListOf<JSONObject>()
        fun visit(node: Any?) {
            when (node) {
                is JSONArray -> for (i in 0 until node.length()) visit(node.opt(i))
                is JSONObject -> {
                    val type = node.opt("@type")
                    val types = if (type is JSONArray) (0 until type.length()).map { type.optString(it) } else listOf(type?.toString().orEmpty())
                    if (types.any { it.substringAfterLast('/').equals("Recipe", true) }) found.add(node)
                    val keys = node.keys()
                    while (keys.hasNext()) visit(node.opt(keys.next()))
                }
            }
        }
        Jsoup.parse(html).select("script[type=application/ld+json]").forEach {
            try { visit(JSONTokener(it.data()).nextValue()) } catch (_: Exception) { }
        }
        return found.maxByOrNull { it.optJSONArray("recipeIngredient")?.length() ?: 0 }
    }

    internal fun preview(html: String, link: String, site: String, ingredients: String): WebRecipe? {
        if (!RecipeSources.trusted(link)) return null
        val recipe = schema(html) ?: return null // Reject articles and category pages.
        fun clean(s: String) = Jsoup.parse(s).text()
        val title = clean(recipe.optString("name"))
        if (title.isBlank()) return null
        val reader = RecipeReader.parse(recipe)
        val description = clean(recipe.optString("description")).take(240)
        if (!RecipeValidation.isDish(title, reader.ingredients, reader.original) ||
            !RecipeValidation.containsEvery(reader.ingredients, ingredients)) return null
        val relevance = RecipeRanking.relevance(reader.ingredients, ingredients)
        val aggregate = recipe.optJSONObject("aggregateRating")
        fun number(key: String) = aggregate?.optString(key)?.replace(',', '.')?.toDoubleOrNull()
        val raw = number("ratingValue")
        val best = number("bestRating") ?: 5.0
        val worst = number("worstRating") ?: 1.0
        val rating = if (raw != null && best > worst && raw in worst..best)
            (1.0 + 4.0 * (raw-worst)/(best-worst)).coerceIn(1.0,5.0) else null
        val count = (number("ratingCount") ?: number("reviewCount"))?.takeIf { it > 0 && it <= Int.MAX_VALUE }?.toInt()
        val mins = try { Duration.parse(recipe.optString("totalTime")).toMinutes().takeIf { it in 1..Int.MAX_VALUE }?.toInt() }
            catch (_: Exception) { null }
        val categoryText = recipe.opt("recipeCategory")?.toString().orEmpty().lowercase()
        val category = when {
            "antipast" in categoryText || "appetizer" in categoryText -> "Antipasto"
            "primi" in categoryText || "primo" in categoryText || "first course" in categoryText -> "Primo"
            "second" in categoryText || "main course" in categoryText -> "Secondo"
            "contorn" in categoryText || "side dish" in categoryText -> "Contorno"
            "dolc" in categoryText || "dessert" in categoryText || "cake" in categoryText -> "Dolce"
            "piatt" in categoryText && "unic" in categoryText -> "Piatto unico"
            else -> ""
        }
        val difficulty = when (recipe.optString("difficulty").lowercase()) {
            "facile", "molto facile", "bassa", "molto bassa", "easy" -> "Facile"
            "media", "medium" -> "Media"
            "difficile", "alta", "hard" -> "Difficile"
            else -> ""
        }
        val image = try { if (reader.imageUrl.isBlank()) "" else java.net.URL(java.net.URL(link), reader.imageUrl).toString().takeIf { it.startsWith("https://") }.orEmpty() }
            catch (_: Exception) { "" }
        return WebRecipe(title, description, link, site, image, category, difficulty, mins, rating, count, relevance, reader.ingredients)
    }

    fun search(ingredients: String, category: String, difficulty: String, duration: String): List<WebRecipe> {
        val queries = RecipeSearchQueries.variants(ingredients)
        if (queries.isEmpty()) return emptyList()
        // One task per configured source. A punctuation fallback should not
        // prevent sources at the end of the user's priority list from running.
        val executor = Executors.newFixedThreadPool(12)
        try {
            val searches = RecipeSources.sites.toList().map { (site, domain) -> Callable {
                val found = linkedSetOf<String>()
                for (query in queries) {
                    try {
                        found += searchLinks(download(RecipeSearchQueries.bing(domain, query)), domain)
                    } catch (_: Exception) {
                        // Try a form without the trailing comma if this one fails.
                    }
                    if (found.size >= 4) break
                }
                found.take(4).map { site to it }
            } }
            // Keep a bounded network budget so UI search does not hang.
            val links = executor.invokeAll(searches, 25, TimeUnit.SECONDS).flatMap {
                try { if (it.isCancelled) emptyList() else it.get() } catch (_: Exception) { emptyList() }
            }.distinctBy { it.second }
            val reads = links.map { (site, link) -> Callable {
                try { preview(download(link), link, site, ingredients) } catch (_: Exception) { null }
            } }
            val all = executor.invokeAll(reads, 43, TimeUnit.SECONDS).mapNotNull {
                try { if (it.isCancelled) null else it.get() } catch (_: Exception) { null }
            }
            return RecipeRanking.order(all, ingredients)
        } finally { executor.shutdownNow() }
    }
}
