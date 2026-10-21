package it.oggisimangia.app

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup

data class RecipeText(
    val ingredients: String = "",
    val original: String = "",
    val summary: String = "",
    val portions: String = "",
    val time: String = "",
    val notice: String = "",
    val prepTime: String = "",
    val cookTime: String = "",
    val totalTime: String = "",
    val restTime: String = "",
    val imageUrl: String = "",
    val nutrition: String = "",
    val title: String = ""
)

/** Reads only explicitly published Recipe data; never guesses quantities or temperatures. */
object RecipeReader {
    fun read(url: String): RecipeText {
        RecipeLink.requirePublic(url)
        var address = url
        var response = Jsoup.connect(address).userAgent("Mozilla/5.0 (Android; OggiSiMangia/1.2)")
            .timeout(12000).maxBodySize(3_000_000).followRedirects(false).execute()
        var redirects = 0
        while (response.statusCode() in 300..399 && redirects++ < 3) {
            address = java.net.URL(java.net.URL(address), response.header("Location") ?: error("Redirect senza destinazione")).toString()
            RecipeLink.requirePublic(address)
            response = Jsoup.connect(address).userAgent("Mozilla/5.0 (Android; OggiSiMangia/1.2)")
                .timeout(12000).maxBodySize(3_000_000).followRedirects(false).execute()
        }
        require(response.statusCode() in 200..299)
        return parseDocument(response.parse(), address)
    }

    internal fun parseDocument(doc: org.jsoup.nodes.Document, address: String): RecipeText {
        val recipes = mutableListOf<JSONObject>()
        fun visit(value: Any?) {
            when (value) {
                is JSONArray -> for (i in 0 until value.length()) visit(value.opt(i))
                is JSONObject -> {
                    val type = value.opt("@type")
                    val types = if (type is JSONArray) (0 until type.length()).map { type.optString(it) }
                        else listOf(type?.toString().orEmpty())
                    if (types.any { it.substringAfterLast('/').equals("Recipe", true) }) recipes.add(value)
                    val keys = value.keys()
                    while (keys.hasNext()) visit(value.opt(keys.next()))
                }
            }
        }
        for (script in doc.select("script[type=application/ld+json]")) {
            try { visit(JSONTokener(script.data()).nextValue()) } catch (_: Exception) { }
        }
        val fallbackImage = doc.select("meta[property=og:image]").firstOrNull()?.attr("content").orEmpty()
        fun imageUrl(raw: String): String = try {
            if (raw.isBlank()) "" else java.net.URL(java.net.URL(address), raw).toString()
                .takeIf { RecipeLink.isValid(it) }.orEmpty()
        } catch (_: Exception) { "" }
        val fallbackTitle = doc.select("meta[property=og:title]").firstOrNull()?.attr("content")
            ?.takeIf { it.isNotBlank() } ?: doc.title()
        val recipe = recipes.maxByOrNull { it.optJSONArray("recipeIngredient")?.length() ?: 0 }
            ?: return RecipeText(title = fallbackTitle,
                notice = "Il sito non fornisce una ricetta leggibile automaticamente. Puoi salvare il link e completare i dati manualmente.",
                imageUrl = imageUrl(fallbackImage))
        val result = parse(recipe)
        return result.copy(title = result.title.ifBlank { fallbackTitle },
            imageUrl = imageUrl(result.imageUrl.ifBlank { fallbackImage }))
    }

    internal fun parse(recipe: JSONObject): RecipeText {
        fun clean(value: String) = Jsoup.parse(value).text().trim()
        fun strings(value: Any?): List<String> = when (value) {
            is JSONArray -> (0 until value.length()).flatMap { strings(value.opt(it)) }
            is String -> listOf(clean(value)).filter { it.isNotBlank() }
            is JSONObject -> strings(value.opt("text")).ifEmpty { strings(value.opt("itemListElement")) }
            else -> emptyList()
        }
        val ingredients = strings(recipe.opt("recipeIngredient")).joinToString("\n")
        val steps = strings(recipe.opt("recipeInstructions"))
        val original = steps.mapIndexed { i, step -> "${i + 1}. $step" }.joinToString("\n\n")
        // Extractive outline: each complete first sentence is preserved, with the full text available.
        val summary = steps.mapIndexed { i, step ->
            val sentences = step.split(Regex("(?<=[.!?])\\s+(?=[A-ZÀ-Ü])"))
            val essential = Regex("[0-9]|cuoc|cottura|forno|fuoco|minut|raffredd|frigg|boll", RegexOption.IGNORE_CASE)
            "${i + 1}. " + sentences.filterIndexed { index, sentence -> index == 0 || essential.containsMatchIn(sentence) }.joinToString(" ")
        }.joinToString("\n\n")
        fun value(key: String) = recipe.optString(key).takeUnless { it == "null" }.orEmpty()
        fun duration(key: String): String {
            val raw = value(key)
            return try { "${java.time.Duration.parse(raw).toMinutes()} min" } catch (_: Exception) { raw }
        }
        fun image(value: Any?): String = when (value) {
            is String -> value
            is JSONObject -> value.optString("url").ifBlank { value.optString("contentUrl") }
            is JSONArray -> (0 until value.length()).firstNotNullOfOrNull { image(value.opt(it)).takeIf { url -> url.isNotBlank() } }.orEmpty()
            else -> ""
        }
        val nutrients = recipe.optJSONObject("nutrition")
        val nutrition = listOf("calories" to "Energia", "proteinContent" to "Proteine",
            "carbohydrateContent" to "Carboidrati", "fatContent" to "Grassi").mapNotNull { (key, caption) ->
            nutrients?.optString(key)?.takeIf { it.isNotBlank() && it != "null" }?.let { "$caption: $it" }
        }.joinToString(" · ")
        return RecipeText(
            ingredients = ingredients, original = original, summary = summary,
            portions = strings(recipe.opt("recipeYield")).joinToString(", "),
            time = duration("totalTime").ifBlank { duration("cookTime") },
            notice = if (steps.isEmpty()) "Procedimento non disponibile: consulta l'originale."
                else "Sintesi automatica: consulta il testo completo per tutti i dettagli e le indicazioni di cottura.",
            prepTime = duration("prepTime"), cookTime = duration("cookTime"), totalTime = duration("totalTime"),
            restTime = duration("restTime").ifBlank { duration("additionalTime") },
            imageUrl = image(recipe.opt("image")), nutrition = nutrition, title = clean(value("name"))
        )
    }
}
