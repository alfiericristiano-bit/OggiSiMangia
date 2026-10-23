package it.oggisimangia.app

import java.text.Normalizer
import java.util.Locale

/**
 * Matches an explicitly named visible application control, not arbitrary code
 * or screen coordinates. Partial name ambiguity must be handled by caller.
 */
object VoiceUiMatcher {
    private val noise = setOf("il","la","lo","gli","le","i","un","una","uno",
        "pulsante","bottone","tasto","opzione","voce","per","di","del","della",
        "dello","al","alla","nel","nella","sul","su","mi","suo")

    private fun normalize(text: String): String = Normalizer.normalize(
        text.lowercase(Locale.ITALIAN), Normalizer.Form.NFD
    ).replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ").trim()

    private fun word(word: String): String = when(word) {
        "fotografa", "fotografia", "scatta", "scattare" -> "foto"
        "scanner", "acquisisci", "scansiona" -> "foto"
        "salvataggio", "memorizza", "memorizzare" -> "salva"
        "stampa", "stampami", "stampare" -> "pdf"
        "cercare", "ricerca", "ricercami", "trova" -> "cerca"
        "impostazione", "impostazioni" -> "imposta"
        "pasto", "pasti" -> "giorno"
        "ricette" -> "ricetta"
        else -> word
    }

    fun tokens(text: String): Set<String> = normalize(text).split(' ')
        .filter { it.isNotEmpty() && it !in noise }
        .map(::word).toSet()

    fun matchingIndices(spokenTarget: String, candidates: List<String>): List<Int> {
        val target=tokens(spokenTarget)
        if(target.isEmpty()) return emptyList()
        val scored=candidates.mapIndexedNotNull { index,name ->
            val words=tokens(name)
            if(words.isEmpty()) return@mapIndexedNotNull null
            val common=target.intersect(words).size
            val coverage=common.toDouble()/target.size
            val score = when {
                words == target -> 1000
                words.containsAll(target) -> 800 - (words.size-target.size)*10
                coverage >= .8 && common >= 2 -> 600 + common*4 - (words.size-common)*8
                else -> -1
            }
            if(score >= 0) index to score else null
        }.sortedWith(compareByDescending<Pair<Int,Int>> { it.second }.thenBy { it.first })
        if(scored.isEmpty()) return emptyList()
        val top=scored.first().second
        // A short target such as "salva" may refer to several buttons. Caller
        // must ask rather than choosing one from an arbitrary presentation order.
        return scored.filter { it.second >= top-20 }.map { it.first }
    }

    fun isUnsafe(label: String): Boolean {
        val w=tokens(label)
        return w.any { it in setOf("elimina","cancella","svuota","azzera","resetta",
            "ripristina","rimuovi","sovrascrivi","formatta") }
    }
}
