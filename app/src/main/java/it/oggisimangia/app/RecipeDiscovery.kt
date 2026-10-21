package it.oggisimangia.app

import android.net.Uri
import android.text.Html
import android.util.Xml
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Anteprime brevi da pagine pubbliche: il procedimento completo resta sul sito dell'autore. */
data class OnlineRecipe(
    val title: String,
    val url: String,
    val site: String,
    val description: String = "",
    val photoUrl: String = "",
    val rating: Double? = null,
    val feedbackCount: Int? = null,
    val minutes: Int? = null,
    val difficulty: String = "",
    val relevance: Int = 0
)

object RecipeDiscovery {
    private val domains = linkedMapOf(
        "GialloZafferano" to "giallozafferano.it",
        "Il Cucchiaio d'Argento" to "cucchiaio.it",
        "Fatto in Casa da Benedetta" to "fattoincasadabenedetta.it"
    )

    fun trusted(link: String): Boolean = try {
        val uri = Uri.parse(link)
        val host = uri.host?.lowercase() ?: ""
        uri.scheme.equals("https", true) &&
            domains.values.any { host == it || host.endsWith(".$it") }
    } catch (_: Exception) { false }

    private fun text(html: String) = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
        .toString().replace(Regex("\\s+"), " ").trim()

    private fun download(url: String, limit: Int = 1_200_000): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; OggiSiMangia/1.1)")
        conn.setRequestProperty("Accept", "text/html,application/xml;q=0.9,*/*;q=0.8")
        return try {
            if (conn.responseCode !in 200..299) return ""
            conn.inputStream.use { stream ->
                val bytes = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var count = 0
                while (true) {
                    val n = stream.read(buffer)
                    if (n < 0) break
                    count += n
                    if (count > limit) break
                    bytes.write(buffer, 0, n)
                }
                bytes.toString("UTF-8")
            }
        } finally { conn.disconnect() }
    }

    private fun rssLinks(site: String, query: String): List<String> {
        // RSS pubblico: nessuna API privata o chiave incorporata nell'APK.
        val url = Uri.parse("https://www.bing.com/search").buildUpon()
            .appendQueryParameter("q", "site:$site ricetta $query")
            .appendQueryParameter("format", "rss").build().toString()
        val rss = download(url, 550_000)
        if (rss.isBlank()) return emptyList()
        val parser = Xml.newPullParser()
        parser.setInput(rss.reader())
        val links = mutableListOf<String>()
        var inItem = false
        var inLink = false
        var event = parser.eventType
        while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
            when (event) {
                org.xmlpull.v1.XmlPullParser.START_TAG -> {
                    if (parser.name.equals("item", true)) inItem = true
                    if (inItem && parser.name.equals("link", true)) inLink = true
                }
                org.xmlpull.v1.XmlPullParser.TEXT -> if (inItem && inLink) {
                    val candidate = parser.text.trim()
                    if (trusted(candidate)) links.add(candidate)
                }
                org.xmlpull.v1.XmlPullParser.END_TAG -> {
                    if (parser.name.equals("link", true)) inLink = false
                    if (parser.name.equals("item", true)) inItem = false
                }
            }
            event = parser.next()
        }
        return links.distinct().take(9)
    }

    private fun meta(html: String, name: String): String {
        val tags = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(html)
        for (match in tags) {
            val tag = match.value
            if (!tag.contains("og:$name", true) &&
                !tag.contains("name=\"$name\"", true) &&
                !tag.contains("name='$name'", true)) continue
            val value = Regex("content\\s*=\\s*[\"']([^\"']*)[\"']", RegexOption.IGNORE_CASE)
                .find(tag)?.groupValues?.getOrNull(1) ?: continue
            return text(value)
        }
        return ""
    }

    private fun findRecipe(node: Any?): JSONObject? {
        when (node) {
            is JSONArray -> for (i in 0 until node.length()) {
                val found = findRecipe(node.opt(i))
                if (found != null) return found
            }
            is JSONObject -> {
                val type = node.opt("@type")?.toString() ?: ""
                if (type.contains("Recipe", true)) return node
                for (key in listOf("@graph", "mainEntity", "itemListElement")) {
                    val found = findRecipe(node.opt(key))
                    if (found != null) return found
                }
            }
        }
        return null
    }

    private fun image(value: Any?): String = when (value) {
        is String -> value
        is JSONObject -> value.optString("url").ifBlank { value.optString("contentUrl") }
        is JSONArray -> if (value.length() > 0) image(value.opt(0)) else ""
        else -> ""
    }

    private fun minutes(value: String): Int? {
        val iso = Regex("P(?:\\d+D)?T(?:(\\d+)H)?(?:(\\d+)M)?", RegexOption.IGNORE_CASE)
            .find(value)
        if (iso != null) {
            val hours = iso.groupValues[1].toIntOrNull() ?: 0
            val mins = iso.groupValues[2].toIntOrNull() ?: 0
            return hours * 60 + mins
        }
        return null
    }

    private fun preview(link: String, site: String, terms: List<String>): OnlineRecipe? {
        if (!trusted(link)) return null
        val html = download(link)
        if (html.isBlank()) return null
        var schema: JSONObject? = null
        val script = Regex("<script\\b[^>]*type\\s*=\\s*[\"']application/ld\\+json[\"'][^>]*>(.*?)</script>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        for (match in script.findAll(html)) {
            try {
                val raw = match.groupValues[1].trim()
                val node: Any = if (raw.startsWith("[")) JSONArray(raw) else JSONObject(raw)
                schema = findRecipe(node)
                if (schema != null) break
            } catch (_: Exception) { /* altre strutture JSON-LD non valide */ }
        }
        val recipe = schema
        val title = recipe?.optString("name")?.takeIf { it.isNotBlank() }
            ?: meta(html, "title").ifBlank { return null }
        val ratingObject = recipe?.optJSONObject("aggregateRating")
        val rating = ratingObject?.optString("ratingValue")?.replace(",", ".")?.toDoubleOrNull()
        val count = ratingObject?.optString("ratingCount")?.toIntOrNull()
            ?: ratingObject?.optString("reviewCount")?.toIntOrNull()
        val total = minutes(recipe?.optString("totalTime") ?: "")
            ?: run {
                val prep = minutes(recipe?.optString("prepTime") ?: "")
                val cook = minutes(recipe?.optString("cookTime") ?: "")
                if (prep != null || cook != null) (prep ?: 0) + (cook ?: 0) else null
            }
        val desc = text(recipe?.optString("description").orEmpty())
            .ifBlank { meta(html, "description") }.take(220)
        val img = image(recipe?.opt("image")).ifBlank { meta(html, "image") }
        val difficulty = recipe?.optString("difficulty").orEmpty()
        val ingredientData = recipe?.optJSONArray("recipeIngredient")?.toString().orEmpty()
        val relevance = terms.count {
            title.contains(it, true) || ingredientData.contains(it, true) || desc.contains(it, true)
        }
        return OnlineRecipe(text(title), link, site, desc, img, rating, count,
            total, difficulty, relevance)
    }

    fun search(ingredients: String, category: String, difficulty: String,
               duration: String): List<OnlineRecipe> {
        val terms = ingredients.split(',', ';', '\n').map { it.trim().lowercase() }
            .filter { it.length >= 2 }
        val query = (ingredients.replace('\n', ' ') + " " + category).trim()
        val links = mutableListOf<Pair<String, String>>()
        for ((site, domain) in domains) {
            try { rssLinks(domain, query).forEach { links.add(site to it) } }
            catch (_: Exception) { /* il singolo sito può non rispondere */ }
        }
        val executor = Executors.newFixedThreadPool(4)
        try {
            val jobs = links.distinctBy { it.second }.take(24).map { (site, link) ->
                executor.submit<OnlineRecipe?> {
                    try { preview(link, site, terms) } catch (_: Exception) { null }
                }
            }
            val all = jobs.mapNotNull {
                try { it.get(12, TimeUnit.SECONDS) } catch (_: Exception) { null }
            }
            val maxMinutes = when (duration) {
                "Volante" -> 15; "Veloce" -> 30; "Media" -> 60; else -> Int.MAX_VALUE
            }
            return all.filter { item ->
                (item.minutes == null || item.minutes <= maxMinutes) &&
                    (item.difficulty.isBlank() || item.difficulty.contains(difficulty, true))
            }.sortedWith(compareByDescending<OnlineRecipe> { it.feedbackCount ?: -1 }
                .thenByDescending { it.relevance }
                .thenBy { it.title }).take(10)
        } finally { executor.shutdownNow() }
    }
}
