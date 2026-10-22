package it.oggisimangia.app

import org.json.JSONObject
import java.text.Normalizer

/** Generative responses are untrusted drafts. Retain original OCR, pictures, and user edits. */
object NanoRecipeResponse {
    fun parse(answer: String, originalOcr: String): MagazineRecipeText.Guess? {
        val json = answer.substringAfter("{", "").substringBeforeLast("}", "")
        if (json.isBlank()) return null
        return try {
            val root = JSONObject("{" + json + "}")
            fun norm(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), " ").trim()
            val source = norm(originalOcr)
            val sourceWords = source.split(" ").filter { it.isNotBlank() }.toSet()
            fun anchored(s: String): Boolean {
                val words = norm(s).split(" ").filter { it.length >= 4 }
                return words.isNotEmpty() && words.count { it in sourceWords } * 2 >= words.size
            }
            fun str(k: String): String = root.optString(k, "").take(300).trim()
            fun array(k: String): List<String> {
                val a = root.optJSONArray(k) ?: return emptyList()
                return (0 until minOf(a.length(), 50)).map { a.optString(it).trim() }
                    .filter { it.isNotBlank() && anchored(it) && it.length <= 900 }
            }
            fun literal(k: String): String = str(k).takeIf {
                it.isNotBlank() && source.contains(norm(it))
            }.orEmpty()
            val title = str("titolo").takeIf { anchored(it) }.orEmpty()
            val ingredients = array("ingredienti").joinToString("\n")
            val steps = array("preparazione")
            if (title.isBlank() && ingredients.isBlank() && steps.isEmpty()) return null
            MagazineRecipeText.Guess(
                title, ingredients, steps.mapIndexed { index, step -> (index + 1).toString() + ". " + step }.joinToString("\n"),
                literal("tempo_preparazione"), literal("tempo_cottura"),
                literal("tempo_totale"), literal("tempo_riposo"),
                str("difficolta").takeIf { it.lowercase() in setOf("facile","media","difficile") }
                    ?.replaceFirstChar(Char::uppercase).orEmpty(), literal("porzioni")
            )
        } catch (_: Exception) { null }
    }
}
