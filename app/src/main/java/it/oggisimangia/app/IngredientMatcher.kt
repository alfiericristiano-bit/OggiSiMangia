package it.oggisimangia.app

import java.text.Normalizer
import java.util.Locale

/**
 * Compares actual recipeIngredient lines, never page titles, search snippets or descriptions.
 * An ingredient must be explicitly documented, but its Italian singular/plural and
 * commonly named processed forms (e.g. passata di pomodoro) are accepted.
 */
object IngredientMatcher {
    private fun clean(value: String): String = Normalizer.normalize(
        value.lowercase(Locale.ITALIAN), Normalizer.Form.NFD
    ).replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9]+"), " ").trim()

    private val relatedForms = listOf(
        setOf("pomodoro", "pomodori", "pomodorino", "pomodorini", "datterino", "datterini", "ciliegino", "ciliegini"),
        setOf("lenticchia", "lenticchie"),
        setOf("zucchina", "zucchine", "zucchino", "zucchini"),
        setOf("carciofo", "carciofi"),
        setOf("cece", "ceci"),
        setOf("fagiolo", "fagioli"),
        setOf("patata", "patate"),
        setOf("uovo", "uova"),
        setOf("fungo", "funghi"),
        setOf("cipolla", "cipolle"),
        setOf("melanzana", "melanzane"),
        setOf("pisello", "piselli"),
        setOf("peperone", "peperoni")
    )

    private val forms = relatedForms.flatMapIndexed { i, values -> values.map { it to ("ingredient:" + i) } }.toMap()

    private fun canonical(value: String): String {
        val word = clean(value)
        forms[word]?.let { return it }
        // Exact common-Italian -o/-i and -a/-e inflections only; no substring matching.
        if (word.length >= 5) return when {
            word.endsWith("i") -> word.dropLast(1) + "o"
            word.endsWith("e") -> word.dropLast(1) + "a"
            else -> word
        }
        return word
    }

    private fun tokens(value: String): List<String> = clean(value)
        .split(' ').filter { it.isNotBlank() }

    private val ignored = setOf("di", "del", "della", "delle", "dei", "degli", "con", "per", "da")

    private fun lineContainsIngredient(line: String, requested: String): Boolean {
        val actual = tokens(line).map(::canonical).toSet()
        val wanted = tokens(requested).filterNot { it in ignored || it.all(Char::isDigit) }
        if (wanted.isEmpty()) return false
        return wanted.all { canonical(it) in actual }
    }

    fun containsEvery(ingredients: String, requested: String): Boolean {
        val terms = RecipeRanking.terms(requested)
        if (terms.isEmpty()) return true
        if (ingredients.isBlank()) return false
        val lines = ingredients.lines().filter { it.isNotBlank() }
        return terms.all { query -> lines.any { lineContainsIngredient(it, query) } }
    }
}
