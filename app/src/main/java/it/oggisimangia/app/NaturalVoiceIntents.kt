package it.oggisimangia.app

import java.text.Normalizer
import java.time.LocalDate
import java.util.Locale

/**
 * Offline Italian phrase interpretation for common actions and variants.
 * Never trusts a free-form phrase as executable code: output is an allowlisted
 * VoiceCommand. Uncertain requests stay Unknown for clarification / local AI.
 */
object NaturalVoiceIntents {
    fun normalize(input: String): String = Normalizer.normalize(
        input.lowercase(Locale.ITALIAN), Normalizer.Form.NFD
    ).replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^a-z0-9\\s]"), " ")
        .replace(Regex("\\s+"), " ").trim()

    private fun present(text: String, vararg alternatives: String) =
        alternatives.any { Regex("\\b" + it + "\\b").containsMatchIn(text) }

    fun parse(spoken: String, today: LocalDate): VoiceCommand? {
        val s = normalize(spoken)
        if (s.isBlank()) return null
        // Negations are not authorizations; never infer the opposite intention.
        if (Regex("\\b(?:non|evita|senza)\\s+(?:aprire|aprirmi|apriresti|cercare|salvare|aggiungere|modificare|premere|toccare)\\b")
                .containsMatchIn(s)) return VoiceCommand.Unknown

        val day = when {
            present(s, "dopodomani") -> today.plusDays(2)
            present(s, "domani") -> today.plusDays(1)
            present(s, "ieri") -> today.minusDays(1)
            else -> today
        }
        val meal = when {
            present(s, "cena", "cene", "stasera", "serata") -> "Cena"
            present(s, "pranzo", "pranzi", "mezzogiorno") -> "Pranzo"
            else -> null
        }

        // Filtering is a deterministic operation on supported UI filter values.
        val filter = Regex("\\b(?:imposta|metti|seleziona|scegli|applica|filtra|cambia|preferisco|voglio)\\b")
            .containsMatchIn(s) && (present(s, "filtro", "filtri", "difficolta", "tempo", "durata",
                "tipologia", "tipo", "portata", "vegetariano", "vegetariana", "vegano", "vegana") ||
                present(s, "facile", "media", "difficile", "volante", "veloce", "lunga"))
        if (filter) {
            val kind = when {
                present(s, "difficolta", "facile", "difficile") -> VoiceCommand.Filter.DIFFICULTY
                present(s, "tempo", "durata", "veloce", "volante", "lunga") ->
                    VoiceCommand.Filter.DURATION
                present(s, "tipologia", "vegetariano", "vegetariana", "vegano", "vegana") ->
                    VoiceCommand.Filter.TYPE
                present(s, "portata", "primo", "secondo", "dolce", "contorno") ->
                    VoiceCommand.Filter.CATEGORY
                else -> null
            }
            if (kind != null) {
                val value = when (kind) {
                    VoiceCommand.Filter.DIFFICULTY -> when {
                        present(s,"facile","semplice") -> "Facile"
                        present(s,"difficile","complessa") -> "Difficile"
                        present(s,"media","intermedia") -> "Media"
                        else -> ""
                    }
                    VoiceCommand.Filter.DURATION -> when {
                        present(s,"volante","rapidissima") -> "Volante"
                        present(s,"veloce","rapida","breve") -> "Veloce"
                        present(s,"lunga") -> "Lunga"
                        present(s,"media") -> "Media"
                        else -> ""
                    }
                    VoiceCommand.Filter.TYPE -> when {
                        present(s,"vegetariano","vegetariana") -> "Vegetariana"
                        present(s,"vegano","vegana") -> "Vegana"
                        else -> ""
                    }
                    VoiceCommand.Filter.CATEGORY -> when {
                        present(s,"primo") -> "Primo"
                        present(s,"secondo") -> "Secondo"
                        present(s,"dolce") -> "Dolce"
                        present(s,"contorno") -> "Contorno"
                        present(s,"antipasto") -> "Antipasto"
                        else -> ""
                    }
                }
                if (value.isNotBlank()) return VoiceCommand.SetFilter(kind, value)
            }
        }

        // Reading a meal must take precedence over a coincidental mention of the
        // calendar, recipes or search. "Stasera" implies today's dinner.
        if (present(s, "mangiamo", "mangio", "previsto", "preparato", "menu", "menù") &&
            present(s, "cosa", "che", "quale", "dimmi", "sapere", "fammi", "mostra", "vedere")) {
            return VoiceCommand.ReadMeals(day, meal)
        }
        if (s.matches(Regex("(?:che|cosa)\\s+(?:si\\s+)?mangia\\s+(?:oggi|domani|stasera|a pranzo|a cena).*")))
            return VoiceCommand.ReadMeals(day, meal)

        // Natural requests for an ingredient search, with more than one verb.
        val searchGoal = present(s, "ricette", "ricetta", "piatti", "piatto", "cucinare", "preparare") &&
            present(s, "cerca", "cercami", "trova", "trovami", "consiglia", "consigliami",
                "proponi", "proponimi", "idee", "idee per", "vorrei", "voglio", "cerco",
                "qualcosa", "mostrami", "fammi vedere")
        if (searchGoal) {
            val part = Regex("\\b(?:con|a base di|che contengono|usando|utilizzando)\\s+(.+)$")
                .find(s)?.groupValues?.getOrNull(1)
            if (part != null) {
                val raw = part.replace(Regex("\\s+(?:per\\s+)?(?:oggi|domani|dopodomani|stasera)$"), "")
                    .replace(Regex("\\s+(?:a|per)\\s+(?:pranzo|cena)$"), "")
                    .trim()
                val values = raw.split(Regex("\\s*(?:,|\\s+e\\s+)\\s*"))
                    .map { it.trim() }.filter { it.isNotBlank() }.distinct().take(10)
                if (values.isNotEmpty()) return if (meal != null) {
                    VoiceCommand.GuidedMealSearch(values, meal, day)
                } else VoiceCommand.Search(values)
            }
        }

        // UI control verbs: dynamically resolve to existing on-screen buttons.
        // "Salva", "fotografa" and "stampa" are not guessed if absent on this page.
        val buttonPhrases = listOf(
            Regex("^(?:premi|premimi|tocca|clicca|schiaccia|pigia|aziona|attiva|usa|seleziona|scegli)\\s+(?:il|la|lo|i|le|un|una)?\\s*(?:pulsante|tasto|bottone|opzione|voce)?\\s*(.+)$"),
            Regex("^(?:fai|esegui|avvia)\\s+(?:la|il|una|un)?\\s*(stampa|pdf|scansione|fotografia|foto|ricerca|salvataggio|copia|report).*$")
        )
        for (regex in buttonPhrases) {
            val choice = regex.find(s)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            if (choice.isNotBlank()) return VoiceCommand.PressButton(choice)
        }
        if (s.matches(Regex("(?:stampa|stampami|esporta|crea|genera)\\s+(?:il|la|un|una)?\\s*(?:report|pdf|menu del giorno|scheda).*")))
            return VoiceCommand.PressButton("pdf stampa")
        if (s.matches(Regex("(?:scatta|fotografa|acquisisci|importa)\\s+(?:una|la|le|i|un)?\\s*(?:foto|fotografia|immagine|pagina|ingredienti|preparazione).*"))) {
            val target = when {
                present(s, "ingredienti") -> "foto ingredienti"
                present(s, "preparazione", "procedimento") -> "foto preparazione"
                present(s, "piatto") -> "fotografa piatto"
                present(s, "galleria", "archivio foto") -> "galleria"
                else -> "fotografa pagina"
            }
            return VoiceCommand.PressButton(target)
        }
        if (s.matches(Regex("(?:salva|memorizza|registra)\\s+(?:la|questa|il)?\\s*(?:ricetta|piatto).*")))
            return VoiceCommand.PressButton("salva ricetta")

        // Flexible page navigation aliases, no hidden app/system operations.
        if (present(s,"ricettario","archivio", "ricette salvate", "raccolta ricette",
                "ricette personali", "mie ricette") &&
            present(s,"apri","mostra","vedere","vediamo","consulta","consultare",
                "fammi","portami","vai","voglio","vorrei","ritorna","torna"))
            return VoiceCommand.Open(VoiceCommand.Destination.ARCHIVE)
        if (present(s,"siti", "fonti", "priorita", "sorgenti") &&
            present(s,"apri","mostra","gestisci","configura","vedere","cambia","modifica",
                "impostazioni","vai","portami","vorrei","voglio"))
            return VoiceCommand.Open(VoiceCommand.Destination.SOURCES)
        if (present(s,"calendario", "agenda", "giorni pianificati", "pianificazione") &&
            present(s,"apri","mostra","consulta","fammi","vai","portami","vedere",
                "visualizza","ritorna","torna","vorrei","voglio"))
            return VoiceCommand.Open(VoiceCommand.Destination.CALENDAR)
        if (present(s,"ricetta nuova","nuova ricetta","creare una ricetta","inserire una ricetta") &&
            present(s,"apri","voglio","vorrei","crea","aggiungi","inserisci","mostra"))
            return VoiceCommand.Open(VoiceCommand.Destination.EDITOR)
        if (present(s,"cerca","ricerca","ricercare","trovare") &&
            present(s,"pagina","schermata","sezione","apri","fammi","vai","vorrei","voglio") &&
            !present(s,"con","usando","a base di"))
            return VoiceCommand.Open(VoiceCommand.Destination.SEARCH)
        if (present(s,"guida","aiuto","istruzioni") &&
            present(s,"apri","leggi","mostra","vedere","spiega","fammi"))
            return VoiceCommand.Help

        return null
    }
}
