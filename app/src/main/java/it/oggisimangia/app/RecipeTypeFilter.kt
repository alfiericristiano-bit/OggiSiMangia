package it.oggisimangia.app

import java.text.Normalizer
import java.util.Locale

/**
 * A cautious ingredient-based classification, not a nutritional certification.
 * Every result remains visible if ingredients are unavailable for verification.
 */
object RecipeTypeFilter {
    val options = arrayOf("Qualsiasi", "Vegetariana", "Vegana", "Con carne", "Con pesce",
        "Senza glutine (da verificare)", "Senza lattosio (da verificare)")
    private fun normalized(s: String): String =
        Normalizer.normalize(s.lowercase(Locale.ITALIAN), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
    private fun has(s: String, pattern: String) = Regex(
        "(?<![a-z])(?:$pattern)(?![a-z])", RegexOption.IGNORE_CASE
    ).containsMatchIn(normalized(s))
    private val meat = "pollo|gallina|tacchino|manzo|vitello|maiale|salsiccia|pancetta|guanciale|speck|prosciutto|carne|salame|mortadella|bresaola|agnello|coniglio|brodo di carne|brodo di pollo|lardo"
    private val fish = "pesce|tonno|salmone|merluzzo|acciugh[ae]|alici|sgombro|gamber[oi]|gamberetti|cozz[ae]|vongol[ae]|calamar[oi]|polp[oi]|seppia|orata|branzino|sardina|sardine|frutti di mare|salsa di pesce"
    private val animal = "uov[oa]|albume|tuorlo|latte|burro|panna|formaggi[oi]?|parmigiano|grana|pecorino|mozzarella|ricotta|yogurt|miele|gelatina animale|colla di pesce|latticini"
    private val gluten = "farina di grano|farina 00|farina 0|frumento|semola|orzo|farro|segale|couscous|bulgur|seitan|pasta|spaghetti|penne|tagliatelle|pane|pangrattato|pizza|biscotti|lasagn[ae]"
    private val dairy = "latte|burro|panna|formaggi[oi]?|parmigiano|grana|pecorino|mozzarella|ricotta|yogurt|lattosio|latticini"

    /** Null: no readable ingredients. True: no obvious disqualifier (still verify manually).
     * False: explicit published ingredient disqualifies the chosen type.
     */
    fun match(ingredients: String, type: String): Boolean? {
        if (type == "Qualsiasi") return true
        if (ingredients.isBlank()) return null
        return when (type) {
            "Vegetariana" -> !has(ingredients, meat) && !has(ingredients, fish)
            "Vegana" -> !has(ingredients, meat) && !has(ingredients, fish) && !has(ingredients, animal)
            "Con carne" -> has(ingredients, meat)
            "Con pesce" -> has(ingredients, fish)
            "Senza glutine (da verificare)" -> !has(ingredients, gluten)
            "Senza lattosio (da verificare)" -> !has(ingredients, dairy)
            else -> null
        }
    }
    fun warning(type: String): String = when (type) {
        "Qualsiasi" -> ""
        "Senza glutine (da verificare)", "Senza lattosio (da verificare)" ->
            "Verifica ingredienti, etichette, contaminazioni e preparazioni: il filtro NON certifica l'assenza di allergeni."
        "Vegetariana", "Vegana" ->
            "Classificazione indicativa dall'elenco ingredienti; verifica brodi, salse, gelatine e ingredienti composti."
        else -> "Verifica sempre l'elenco ingredienti originale."
    }
}
