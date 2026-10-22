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
        .select("item > link").map { it.text().trim() }.filter { RecipeSources.belongs(it, domain) }.distinct().take(12)

    /** RSS is preferred; some search responses come back as HTML instead. */
    internal fun searchLinks(body: String, domain: String): List<String> {
        val rss = rssLinks(body, domain)
        if (rss.isNotEmpty()) return rss
        return Jsoup.parse(body).select("li.b_algo h2 a[href], #b_results h2 a[href]")
            .map { it.attr("href").trim() }
            .filter { RecipeSources.belongs(it, domain) }.distinct().take(12)
    }


    internal data class Candidate(val url: String, val title: String, val site: String)

    private fun acceptableLink(link: String, domain: String): Boolean {
        if (!RecipeSources.belongs(link, domain)) return false
        val path = try { java.net.URI(link).path.orEmpty().lowercase() } catch (_: Exception) { return false }
        if (path.length < 7) return false
        return listOf("/ricerca", "/search", "/category/", "/categoria/", "/categorie/",
            "/tag/", "/author/", "/autori/", "/page/", "/privacy", "/contatti").none { it in path }
    }

    private fun cleanCandidateTitle(raw: String, link: String): String {
        val title = Jsoup.parse(raw).text().trim()
            .substringBefore(" - Ricetta").substringBefore(" | ").substringBefore(" - Misya")
            .trim()
        if (title.length in 6..130) return title
        val slug = try { java.net.URI(link).path.substringAfterLast('/').ifBlank {
            java.net.URI(link).path.trimEnd('/').substringAfterLast('/')
        } } catch (_: Exception) { "" }
        return slug.substringBefore('.').replace('-', ' ').replace('_', ' ')
            .trim().replaceFirstChar { it.uppercase() }.take(130)
    }

    /** RSS/HTML may provide a valid recipe link even when the site blocks metadata reading.
     * Such hits remain unverified and are NEVER claimed to match all ingredient lists.
     */
    internal fun candidates(body: String, site: String, domain: String): List<Candidate> {
        val found = linkedMapOf<String, Candidate>()
        fun add(url: String, title: String) {
            val normalized = url.trim().substringBefore('#')
            if (!acceptableLink(normalized, domain)) return
            val name = cleanCandidateTitle(title, normalized)
            if (!RecipeValidation.isLikelyRecipeLink(name, normalized)) return
            found.putIfAbsent(normalized.substringBefore('?'),
                Candidate(normalized, name, site))
        }
        val rss = Jsoup.parse(body, "", Parser.xmlParser())
        rss.select("item").forEach { item ->
            add(item.selectFirst("link")?.text().orEmpty(),
                item.selectFirst("title")?.text().orEmpty())
        }
        if (found.isEmpty()) {
            val html = Jsoup.parse(body)
            html.select("li.b_algo h2 a[href], #b_results h2 a[href], article h2 a[href], .result a[href]").forEach {
                add(it.attr("href"), it.text())
            }
        }
        return found.values.take(16)
    }

    /** Interleave sources before HTTP verification so one site cannot consume all time slots. */
    internal fun <T> roundRobin(groups: List<List<T>>): List<T> {
        val largest = groups.maxOfOrNull { it.size } ?: 0
        return (0 until largest).flatMap { index -> groups.mapNotNull { it.getOrNull(index) } }
    }

    private fun pendingPreview(candidate: Candidate, ingredients: String): WebRecipe? =
        if (RecipeValidation.mentionsMainInTitle(candidate.title, ingredients))
            WebRecipe(candidate.title, "", candidate.url, candidate.site, verified = false)
        else null

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

    internal fun preview(html: String, link: String, site: String, ingredients: String,
                         allowPartial: Boolean = false): WebRecipe? {
        if (!RecipeSources.trusted(link)) return null
        val recipe = schema(html) ?: return null // Reject articles and category pages.
        fun clean(s: String) = Jsoup.parse(s).text()
        val title = clean(recipe.optString("name"))
        if (!RecipeValidation.isLikelyRecipeLink(title, link)) return null
        val reader = RecipeReader.parse(recipe)
        val description = clean(recipe.optString("description")).take(240)
        if (!RecipeValidation.isDish(title, reader.ingredients, reader.original) ||
            !(if (allowPartial) RecipeValidation.containsEvery(reader.ingredients,
                RecipeValidation.mainIngredient(ingredients))
              else RecipeValidation.containsEvery(reader.ingredients, ingredients))) return null
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


    /** Query only the selected source, returning verified recipes first plus separately labelled candidates. */
    fun searchNextPage(ingredients: String, site: String, domain: String, page: Int): List<WebRecipe> {
        require(page >= 1)
        require(RecipeSources.sites[site] == domain) { "Fonte non configurata" }
        val query = RecipeSearchQueries.variants(ingredients).lastOrNull() ?: return emptyList()
        val url = RecipeSearchQueries.bing(domain, query, page * 10)
        val rssHits = try { candidates(download(url), site, domain) }
            catch (_: Exception) { emptyList() }
        val hits = if (rssHits.isNotEmpty()) rssHits else try {
            candidates(download(url.replace("&format=rss", "")), site, domain)
        } catch (_: Exception) { emptyList() }
        val pool = Executors.newFixedThreadPool(6)
        return try {
            val jobs = hits.take(12).map { candidate -> Callable {
                try {
                    preview(download(candidate.url), candidate.url, candidate.site, ingredients, allowPartial = true)
                } catch (_: Exception) {
                    // Network/robots/timeouts: only a plausible dish using the main
                    // ingredient is shown as genuinely not yet verified.
                    pendingPreview(candidate, ingredients)
                }
            } }
            val checked = pool.invokeAll(jobs, 29, TimeUnit.SECONDS).mapIndexedNotNull { index, future ->
                try {
                    if (future.isCancelled) pendingPreview(hits[index], ingredients) else future.get()
                } catch (_: Exception) { pendingPreview(hits[index], ingredients) }
            }
            RecipeRanking.orderFlexible(checked, ingredients)
        } finally { pool.shutdownNow() }
    }

    fun search(ingredients: String, category: String, difficulty: String, duration: String): List<WebRecipe> {
        val queries = RecipeSearchQueries.variants(ingredients)
        val sites = RecipeSources.sites.toList()
        if (queries.isEmpty() || sites.isEmpty()) return emptyList()
        val executor = Executors.newFixedThreadPool(12)
        try {
            // Candidate discovery and page verification have separate budgets.
            val discoveryJobs = sites.map { (site, domain) -> Callable {
                val found = linkedMapOf<String, Candidate>()
                // Start with a normal query; commas and RSS syntax can suppress other sites.
                for (query in queries.reversed()) {
                    try {
                        val url = RecipeSearchQueries.bing(domain, query)
                        var retrieved = candidates(download(url), site, domain)
                        if (retrieved.isEmpty()) {
                            retrieved = try { candidates(download(url.replace("&format=rss", "")), site, domain) }
                                catch (_: Exception) { emptyList() }
                        }
                        retrieved.forEach { found.putIfAbsent(it.url.substringBefore('?'), it) }
                    } catch (_: Exception) { /* Continue with another query; don't discard other sites. */ }
                    if (found.size >= 8) break
                }
                found.values.take(8)
            } }
            val groups = executor.invokeAll(discoveryJobs, 28, TimeUnit.SECONDS).map {
                try { if (it.isCancelled) emptyList() else it.get() }
                catch (_: Exception) { emptyList() }
            }
            // Round-robin ensures the first verification wave covers every source.
            val candidates = roundRobin(groups)
                .distinctBy { it.url.substringBefore('?').substringBefore('#').trimEnd('/') }
            val checks = candidates.map { candidate -> Callable {
                try {
                    preview(download(candidate.url), candidate.url, candidate.site, ingredients, allowPartial = true)
                } catch (_: Exception) {
                    pendingPreview(candidate, ingredients)
                }
            } }
            val futures = executor.invokeAll(checks, 43, TimeUnit.SECONDS)
            val all = futures.mapIndexedNotNull { index, future ->
                try {
                    if (future.isCancelled) pendingPreview(candidates[index], ingredients)
                    else future.get()
                } catch (_: Exception) { pendingPreview(candidates[index], ingredients) }
            }
            return RecipeRanking.orderFlexible(all, ingredients)
        } finally { executor.shutdownNow() }
    }
}
