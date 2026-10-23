package it.oggisimangia.app

import java.text.Normalizer
import java.util.Locale

/** Safe offline fallback for written AI-assisted recipe searches. */
object RecipeSearchIntent {
    fun normalize(input: String): String = Normalizer.normalize(
        input.lowercase(Locale.ITALIAN), Normalizer.Form.NFD
    ).replace(Regex("\\p{M}+"), "").replace(Regex("\\s+"), " ").trim()

    private val opening = Regex(
        "^(?:(?:per favore|mi piacerebbe|vorrei|voglio|cerca|cercami|trova|consigliami|fammi vedere|mostrami|preparami|cerco|dammi)\\s+)+"
    )
    private val generic = Regex(
        "^(?:(?:un|una|il|la|dei|delle|qualche|qualcosa|ricette|ricetta|piatto|piatti|idea|idee|di|da|per|cucinare|preparare)\\s+)+"
    )

    private fun removeBoilerplate(text: String): String {
        var value = text.trim()
        repeat(5) {
            val stripped = value.replace(opening, "").replace(generic, "").trim()
            if (stripped == value) return value
            value = stripped
        }
        return value
    }

    fun ingredients(input: String): List<String> {
        val phrase = normalize(input).take(500)
        if (phrase.isBlank()) return emptyList()
        if (phrase in setOf("per favore", "cerca", "cercami", "cerco", "ricette", "ricetta")) return emptyList()
        if (Regex("\\b(?:con|a base di|usando)\\s*$").containsMatchIn(phrase)) return emptyList()
        val split = Regex("\\b(?:con|a base di|usando|utilizzando|che contengono)\\s+")
            .find(phrase)
        val front = split?.let { removeBoilerplate(phrase.substring(0, it.range.first)) }.orEmpty()
        val candidates = split?.let {
            listOfNotNull(front.takeIf { value ->
                value.isNotBlank() && !Regex(
                    "^(?:qualcosa|qualcosa di|semplice|facile|veloce|vegetariano|vegetariana|per pranzo|per cena)$"
                ).matches(value)
            }, phrase.substring(it.range.last + 1))
        } ?: listOf(removeBoilerplate(phrase))

        return candidates.flatMap { original ->
            original
                .replace(Regex("\\s+(?:per\\s+)?(?:oggi|domani|dopodomani|stasera)$"), "")
                .replace(Regex("\\s+(?:che sia|qualcosa di|possibilmente)\\s+(?:facile|veloce|rapida|rapido|semplice|vegetariana|vegana).*"), "")
                .replace(Regex("\\s+(?:in|entro)\\s+\\d+\\s*(?:minuti|min)$"), "")
                .replace(Regex("\\s+(?:per|a)\\s+(?:pranzo|cena)$"), "")
                .trim(' ', '.', '?', '!', ',', ';')
                .split(Regex("\\s*(?:,|;|\\s+e\\s+)\\s*"))
                .map { it.trim() }
                .filter {
                    it.isNotBlank() && it.length <= 65 &&
                        it !in setOf("ricette", "ricetta", "piatto", "piatti", "cosa", "qualcosa", "ricerca")
                }
        }.distinct().take(10)
    }
}
