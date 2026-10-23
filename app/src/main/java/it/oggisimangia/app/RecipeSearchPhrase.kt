package it.oggisimangia.app

import java.text.Normalizer
import java.util.Locale

/** A safe local fallback for free-text recipe searching when Gemini Nano is unavailable. */
object RecipeSearchPhrase {
    fun ingredients(request: String): List<String> {
        val normalized = Normalizer.normalize(
            request.lowercase(Locale.ITALIAN), Normalizer.Form.NFD
        ).replace(Regex("\\p{M}+"), "")
        val match = Regex("\\b(?:a base di|con|usando|utilizzando)\\s+(.+)$")
            .find(normalized)
        val specific = match?.groupValues?.get(1)
            ?: normalized.replace(Regex("^(?:vorrei|voglio|cerco|cerca|trovami|cercami|consigliami)\\s+"), "")
                .replace(Regex("^(?:una?|delle|dei|qualche)\\s+(?:ricette?|piatti?|idee)\\s*(?:di|per)?\\s*"), "")
        val staples = Regex("^(?:pasta|riso|pollo|patate|pesce|carne)\\s+$")
            .find(normalized.substringBefore(" con "))?.value?.trim()
        val parts = specific
            .replace(Regex("\\s+(?:per|a)\\s+(?:pranzo|cena)(?:\\s+(?:oggi|domani))?$"), "")
            .replace(Regex("\\s+(?:oggi|domani|stasera)$"), "")
            .split(Regex("\\s*(?:,|;|\\s+e\\s+)\\s*"))
            .map { it.trim(' ', '.', ',', '?', '!') }
            .filter { it.length in 2..60 }
        return (listOfNotNull(staples) + parts).distinct().take(10)
    }
}
