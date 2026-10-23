package it.oggisimangia.app

import java.text.Normalizer
import java.util.Locale

/**
 * Zero-network fallback for typed cooking requests. Always previews the
 * recognized ingredients before launching an editorial-site search.
 */
object RecipeSearchIntent {
    fun normalize(input: String): String = Normalizer.normalize(
        input.lowercase(Locale.ITALIAN), Normalizer.Form.NFD
    ).replace(Regex("\\p{M}+"), "").replace(Regex("\\s+"), " ").trim()

    fun ingredients(input: String): List<String> {
        val phrase = normalize(input).take(500)
        if (phrase.isBlank()) return emptyList()
        val after = Regex(
            "(?:\\b(?:con|a base di|usando|utilizzando|che contengono)\\s+)(.+)$"
        ).find(phrase)?.groupValues?.getOrNull(1) ?: phrase
            .replace(Regex("^(?:vorrei|voglio|cerca|cercami|trova|consigliami|fammi vedere|mostrami|preparami|ricette|una|un|il|la|dei|delle|piatti|piatto|ricetta|per)\\s+"), "")
        val cleaned = after
            .replace(Regex("\\s+(?:per\\s+)?(?:oggi|domani|dopodomani|stasera)$"), "")
            .replace(Regex("\\s+(?:che sia|qualcosa di|possibilmente)\\s+(?:facile|veloce|rapida|rapido|semplice|vegetariana|vegana).*"), "")
            .replace(Regex("\\s+(?:in|entro)\\s+\\d+\\s*(?:minuti|min)$"), "")
            .replace(Regex("\\s+(?:per|a)\\s+(?:pranzo|cena)$"), "")
            .trim(' ', '.', '?', '!', ',', ';')
        return cleaned.split(Regex("\\s*(?:,|;|\\s+e\\s+)\\s*"))
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length <= 65 }
            .distinct().take(10)
    }
}
