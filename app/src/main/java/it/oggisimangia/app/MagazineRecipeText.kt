package it.oggisimangia.app

/** Conservative OCR suggestions: never silently invent ingredients or recipe instructions. */
object MagazineRecipeText {
    data class Guess(val title: String, val ingredients: String, val steps: String)

    private val ingredientHeader = Regex("^(ingredienti|occorrente|cosa serve|per gli ingredienti|dosi\\s+per)(?:\\s*[:\\-].*)?$", RegexOption.IGNORE_CASE)
    private val procedureHeader = Regex("^(preparazione|procedimento|esecuzione|come si prepara|istruzioni|metodo)(?:\\s*[:\\-].*)?$", RegexOption.IGNORE_CASE)
    private val quantity = Regex("^(?:[0-9]+(?:[.,][0-9]+)?\\s*(?:g|gr|kg|ml|cl|l|dl|cucchiai?|cucchiaini?|pz|uova|spicchi|fette)?\\b|q\\.?b\\.?|[-•*]\\s+)", RegexOption.IGNORE_CASE)

    fun suggest(text: String): Guess {
        val lines = text.lineSequence().map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotBlank() && !it.startsWith("Pagina ", ignoreCase = true) }.toList()
        val title = lines.firstOrNull {
            it.length in 5..75 && !ingredientHeader.matches(it) && !procedureHeader.matches(it) &&
                !quantity.containsMatchIn(it) && !it.endsWith(".") && !it.startsWith("www.")
        }.orEmpty()
        var mode = ""
        val ingredients = mutableListOf<String>()
        val instructions = mutableListOf<String>()
        for (line in lines) {
            if (ingredientHeader.matches(line)) { mode = "ingredients"; continue }
            if (procedureHeader.matches(line)) { mode = "steps"; continue }
            if (line == title && instructions.isEmpty() && ingredients.isEmpty()) continue
            when (mode) {
                "ingredients" -> ingredients.add(line.removePrefix("•").trim())
                "steps" -> instructions.add(line.replace(Regex("^\\d+[.)]\\s*"), ""))
            }
        }
        // When headings are absent, leave ingredients for human review rather than guessing.
        val rawSteps = if (instructions.isNotEmpty()) instructions
            else if (ingredients.isEmpty()) lines.filter { it != title }
            else emptyList()
        return Guess(title, ingredients.joinToString("\n"),
            RecipeSteps.numbered(rawSteps))
    }
}
