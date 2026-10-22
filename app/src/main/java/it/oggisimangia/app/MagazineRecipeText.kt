package it.oggisimangia.app

/**
 * Offline, role-aware OCR post-processing. OCR is probabilistic; never invent
 * quantities, temperatures or unseen steps. Every generated field is editable.
 */
object MagazineRecipeText {
    const val WHOLE = "intera"
    const val INGREDIENTS = "ingredienti"
    const val STEPS = "preparazione"
    const val DETAILS = "dettagli"

    data class Section(val kind: String, val text: String)
    data class Guess(
        val title: String,
        val ingredients: String,
        val steps: String,
        val prepTime: String = "",
        val cookTime: String = "",
        val totalTime: String = "",
        val restTime: String = "",
        val difficulty: String = "",
        val portions: String = ""
    )

    private val ingHeading = Regex(
        "^(?:ingredienti|occorrente|cosa serve|per gli ingredienti|lista ingredienti|" +
            "per [0-9]+ persone|dosi per [0-9]+ persone)\\s*[:\\-.]?$", RegexOption.IGNORE_CASE)
    private val stepHeading = Regex(
        "^(?:preparazione|procedimento|esecuzione|come si prepara|istruzioni|metodo|" +
            "come fare|passaggi)\\s*[:\\-.]?$", RegexOption.IGNORE_CASE)
    private val detailsHeading = Regex("^(?:tempi|informazioni|difficolt[aà]|scheda ricetta)\\s*[:\\-.]?$",
        RegexOption.IGNORE_CASE)
    private val bullet = Regex("^(?:[•●○◦▪■⁃·*✓✔►▶-]\\s*)+")
    private val stepsPrefix = Regex("^\\s*(?:passo\\s*)?[0-9]{1,2}\\s*[.)\\-:]\\s*", RegexOption.IGNORE_CASE)
    private val quantity = Regex(
        "^\\s*(?:[0-9]+(?:[.,/][0-9]+)?\\s*(?:kg|gr?|g|ml|cl|dl|l|cucchiai?|cucchiaini?|" +
            "spicchi?|fette?|pizzich[oi]|pz|uova|tazze?|bustine?|rametti?|foglie?|pezz[oi])?\\b|" +
            "q\\.?\\s*b\\.?\\b)", RegexOption.IGNORE_CASE)
    private val irrelevant = Regex(
        "^(?:pag(?:ina)?\\.?\\s*[0-9]{1,4}(?:\\s*(?:di|/)\\s*[0-9]+)?|" +
            "[0-9]{1,4}|pubblicit[aà]|advertisement|sponsorizzat[oa]|scansiona.*qr|" +
            "seguici\\s+su.*|www\\.[^\\s]+|https?://[^\\s]+|©.*|copyright.*|" +
            "iscriviti\\s+alla\\s+newsletter.*|abbonati.*|prezzo\\s+[0-9].*)$",
        RegexOption.IGNORE_CASE
    )
    private val info = Regex(
        "^(?:tempo\\s+(?:di\\s+)?)?(preparazione|cottura|totale|riposo|lievitazione|" +
            "difficolt[aà]|porzioni|dosi|persone|tempo)\\s*[:=\\-]?\\s*(.*?)\\s*$",
        RegexOption.IGNORE_CASE
    )
    private val duration = Regex(
        "\\b(?:[0-9]{1,3}(?:\\s*(?:h|ore?)\\s*(?:e\\s*)?)?)?\\s*[0-9]{1,3}\\s*(?:min(?:uti?)?\\.?|ore?|h)\\b",
        RegexOption.IGNORE_CASE
    )
    private val italicFooter = Regex(
        "^(?:foto|fotografie|testi|ricetta\\s+di|a\\s+cura\\s+di|servizio\\s+di)\\s*[:–-]",
        RegexOption.IGNORE_CASE
    )

    private fun normalizedLines(raw: String): List<String> = raw.lineSequence()
        .map { it.trim().replace(Regex("\\s+"), " ") }
        .filter { it.isNotBlank() && !irrelevant.matches(it) && !italicFooter.containsMatchIn(it) }
        .toList()

    private fun heading(line: String): String? = when {
        ingHeading.matches(line) -> INGREDIENTS
        stepHeading.matches(line) -> STEPS
        detailsHeading.matches(line) -> DETAILS
        else -> null
    }

    private fun titleOf(lines: List<String>): String {
        val head = lines.takeWhile { heading(it) == null }.take(6)
            .filterNot { irrelevant.matches(it) || info.matches(it) || quantity.containsMatchIn(it) ||
                it.endsWith(".") || it.startsWith("•") || it.startsWith("–") }
        if (head.isEmpty()) return ""
        val selected = mutableListOf<String>()
        for (line in head) {
            if (line.length !in 2..75) break
            if (selected.isNotEmpty() && (line.firstOrNull()?.isLowerCase() != true &&
                    !selected.last().endsWith("di", true) && !selected.last().endsWith("con", true)
                    && !selected.last().endsWith("alle", true) && !selected.last().endsWith("alla", true))) break
            selected.add(line)
            if (selected.joinToString(" ").length > 100 || selected.size >= 3) break
        }
        return selected.joinToString(" ").take(130).trim()
    }

    private fun itemsFromDedicated(lines: List<String>): List<String> {
        val out = mutableListOf<String>()
        for (line in lines) {
            if (heading(line) != null || info.matches(line)) continue
            val cleaned = line.replace(bullet, "").trim()
            if (cleaned.isBlank() || irrelevant.matches(cleaned)) continue
            // Doses usually start with a quantity, or may be preceded by a bullet.
            // Without a dedicated ingredient photo, require an explicit heading.
            if (out.isNotEmpty() && out.last().endsWith("-") && !quantity.containsMatchIn(cleaned)) {
                out[out.lastIndex] = out.last().dropLast(1) + cleaned
            } else out.add(cleaned)
        }
        return out.distinct().take(60)
    }

    private fun extractFromFull(lines: List<String>, kind: String): List<String> {
        var mode = ""
        val collected = mutableListOf<String>()
        lines.forEach { line ->
            heading(line)?.let { mode = it; return@forEach }
            if (mode == kind) collected += line
        }
        return collected
    }

    private fun paragraphs(lines: List<String>): List<String> {
        val out = mutableListOf<String>()
        var current = ""
        fun flush() {
            val value = current.trim()
            if (value.isNotBlank()) out += value
            current = ""
        }
        lines.forEach { line ->
            if (heading(line) != null || info.matches(line)) return@forEach
            val numbered = stepsPrefix.containsMatchIn(line)
            val clean = line.replace(stepsPrefix, "").replace(bullet, "").trim()
            if (clean.isBlank()) return@forEach
            if (numbered || (current.isNotBlank() && current.endsWith(".") &&
                        clean.firstOrNull()?.isUpperCase() == true)) flush()
            current = (if (current.isBlank()) "" else " ") + clean
        }
        flush()
        return out.take(40)
    }

    fun suggest(text: String): Guess = suggestSections(listOf(Section(WHOLE, text)))

    fun suggestSections(pages: List<Section>): Guess {
        val whole = pages.filter { it.kind == WHOLE }.flatMap { normalizedLines(it.text) }
        val ingredientDetail = pages.filter { it.kind == INGREDIENTS }.flatMap { normalizedLines(it.text) }
        val stepDetail = pages.filter { it.kind == STEPS }.flatMap { normalizedLines(it.text) }
        val detailLines = pages.filter { it.kind == DETAILS }.flatMap { normalizedLines(it.text) }
        val all = pages.flatMap { normalizedLines(it.text) }
        val title = titleOf(whole).ifBlank { titleOf(detailLines) }
            .ifBlank { titleOf(all) }
        val rawIngredients = if (ingredientDetail.isNotEmpty()) ingredientDetail
            else extractFromFull(whole, INGREDIENTS)
        val ingredientItems = itemsFromDedicated(rawIngredients)
        val rawSteps = if (stepDetail.isNotEmpty()) stepDetail
            else extractFromFull(whole, STEPS)
                .ifEmpty { if (ingredientItems.isEmpty()) whole.dropWhile { it != title }.drop(1) else emptyList() }
        val parsedSteps = paragraphs(rawSteps)
        val values = mutableMapOf<String, String>()
        for (line in detailLines + whole) {
            val match = info.matchEntire(line) ?: continue
            val key = match.groupValues[1].lowercase()
            val value = match.groupValues[2].trim()
            if (value.isBlank()) continue
            val normalizedKey = when {
                key.startsWith("preparazione") -> "prep"
                key.startsWith("cottura") -> "cook"
                key.startsWith("totale") || key == "tempo" -> "total"
                key.startsWith("riposo") || key.startsWith("lievitazione") -> "rest"
                key.startsWith("difficolt") -> "difficulty"
                else -> "portions"
            }
            if (normalizedKey == "difficulty") {
                Regex("facile|media|medio|difficile", RegexOption.IGNORE_CASE)
                    .find(value)?.value?.let {
                        values.putIfAbsent(normalizedKey,
                            if (it.equals("medio", true)) "Media" else it.replaceFirstChar(Char::uppercase))
                    }
            } else if (normalizedKey == "portions") {
                Regex("[0-9]+\\s*(?:persone|porzioni)?", RegexOption.IGNORE_CASE)
                    .find(value)?.value?.let { values.putIfAbsent(normalizedKey, it.trim()) }
            } else {
                duration.find(value)?.value?.let { values.putIfAbsent(normalizedKey, it.trim()) }
            }
        }
        return Guess(
            title, ingredientItems.joinToString("\n"), RecipeSteps.numbered(parsedSteps),
            values["prep"].orEmpty(), values["cook"].orEmpty(), values["total"].orEmpty(),
            values["rest"].orEmpty(), values["difficulty"].orEmpty(), values["portions"].orEmpty()
        )
    }
}
