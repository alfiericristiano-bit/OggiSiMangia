package it.oggisimangia.app

/** Offline, versioned user guide. Content must describe implemented actions, not planned features. */
object HelpGuide {
    data class ButtonHelp(val name: String, val explanation: String)
    data class Page(
        val key: String,
        val title: String,
        val introduction: String,
        val buttons: List<ButtonHelp>,
        val note: String = ""
    )

    val pages = listOf(
        Page("start", "Da dove comincio?",
            "OggiSiMangia... serve a cercare ricette, conservarle e organizzare pranzo e cena.",
            listOf(
                ButtonHelp("Calendario", "Scegli il giorno, consulta i pasti e prepara il tuo menù."),
                ButtonHelp("Ricette", "Apri il ricettario già salvato sul telefono e consulta o modifica le schede."),
                ButtonHelp("Aggiungi", "Scrivi una ricetta oppure fotografala da una rivista."),
                ButtonHelp("Riepilogo", "Vedi quanti giorni, pranzi e cene hai pianificato."),
                ButtonHelp("Altro", "Imposta i siti di ricerca e consulta le opzioni AI locale."),
                ButtonHelp("? Guida", "Apre queste spiegazioni in qualsiasi scheda, senza cancellare quello che stai scrivendo."),
                ButtonHelp("🎙 Assistente vocale", "Tocca il microfono per iniziare subito a parlare. Tienilo premuto per digitare istruzioni o recuperare sequenze memorizzate.")
            ), "Le ricette Android sono salvate sul telefono. L'anteprima Vercel è separata e non sincronizza i tuoi dati."),

        Page("calendar", "Calendario e pasti",
            "Un tocco su una data la seleziona. Una pressione prolungata apre l'aggiunta di una ricetta per quel giorno.",
            listOf(
                ButtonHelp("‹ / › accanto al mese", "Vai al mese precedente o successivo."),
                ButtonHelp("Giorno del calendario", "Toccalo per selezionare la data e vedere Pranzo e Cena; tienilo premuto per aggiungere una ricetta."),
                ButtonHelp("Puntini sul giorno", "Arancione = pranzo pianificato; blu = cena pianificata."),
                ButtonHelp("Cerca ricetta", "Apre la ricerca online per ingredienti."),
                ButtonHelp("Precedente / Successivo", "Passa al giorno prima o dopo."),
                ButtonHelp("Oggi", "Ritorna alla data odierna."),
                ButtonHelp("PRANZO / CENA", "Tocca la barra per mostrare o nascondere gli strumenti del pasto."),
                ButtonHelp("Aggiungi / Alternativa", "Aggiungi una preparazione o cerca un piatto alternativo."),
                ButtonHelp("Svuota giorno", "Toglie le assegnazioni dal giorno, ma NON elimina le ricette dal ricettario."),
                ButtonHelp("PDF e stampa del giorno", "Genera il menù in PDF e permette di salvarlo o stamparlo.")
            ), "Per aggiungere un piatto al calendario scegli il giorno, Pranzo o Cena e Principale o Alternativa."),

        Page("search", "Ricerca online: ingredienti e filtri",
            "La prima casella è l'Ingrediente principale (per esempio pasta, pollo o patate). Puoi aggiungere fino a nove altri ingredienti.",
            listOf(
                ButtonHelp("Ingrediente principale", "È obbligatorio e deve essere presente nella ricetta."),
                ButtonHelp("+ Ingrediente", "Aggiunge un altro campo, fino a dieci ingredienti totali."),
                ButtonHelp("X su un ingrediente aggiunto", "Rimuove quel campo. Il primo, principale, resta disponibile."),
                ButtonHelp("Portata", "Seleziona Antipasto, Primo, Secondo, Contorno, Piatto unico o Dolce."),
                ButtonHelp("Tipologia alimentare", "Filtra in modo indicativo: vegetariana, vegana, carne, pesce e altre tipologie."),
                ButtonHelp("Difficoltà / Tempo", "Indica le tue preferenze; eventuali differenze sono segnalate."),
                ButtonHelp("Cerca ricette", "Avvia la ricerca nelle fonti configurate."),
                ButtonHelp("Priorità dei siti e nuove fonti", "Cambia l'ordine dei siti e aggiungine di nuovi."),
                ButtonHelp("Ho un link", "Incolla l'indirizzo completo di una singola ricetta.")
            ), "Le ricette con tutti gli ingredienti richiesti sono preferite. I risultati parziali indicano quelli mancanti. I filtri 'senza glutine/lattosio' NON certificano gli allergeni."),

        Page("results", "Risultati e link alle ricette",
            "I risultati sono separati per sito e puoi vedere più di dieci ricette, a gruppi successivi.",
            listOf(
                ButtonHelp("Sfoglia [sito]", "Apre le ricette trovate su quel sito."),
                ButtonHelp("Prossime 10 / Precedenti 10", "Passa alla pagina successiva o precedente dello stesso sito."),
                ButtonHelp("Cerca altre ricette", "Cerca nuove proposte esclusivamente nella fonte selezionata."),
                ButtonHelp("Corrispondenza completa", "Tutti gli ingredienti richiesti risultano nell'elenco della ricetta."),
                ButtonHelp("Corrispondenza parziale", "Mancano alcuni ingredienti secondari: controlla quali."),
                ButtonHelp("Da controllare", "Non è stato possibile leggere automaticamente l'intera pagina. Apri la ricetta prima di usarla."),
                ButtonHelp("Apri ricetta · Salva o pianifica", "Mostra la singola ricetta, il link diretto alla fonte e le opzioni di salvataggio."),
                ButtonHelp("Salva ricetta / Aggiungi al calendario", "Conserva la ricetta oppure assegnala a una data e a un pasto.")
            ), "Una raccolta o l'indice delle ricette non equivale a una scheda di ricetta. Verifica sempre ingredienti e quantità prima di cucinare."),

        Page("archive", "Il mio ricettario",
            "Qui trovi le ricette salvate sul telefono. Puoi consultarle anche senza Internet.",
            listOf(
                ButtonHelp("Cerca per nome", "Filtra le ricette usando una parola del titolo."),
                ButtonHelp("Ingredienti", "Mostra solo le ricette salvate contenenti tutti gli ingredienti inseriti."),
                ButtonHelp("Categoria / Difficoltà", "Seleziona le caratteristiche che vuoi trovare."),
                ButtonHelp("Ordina", "Scegli l'ordine alfabetico, le più recenti o la difficoltà."),
                ButtonHelp("Applica filtri", "Aggiorna l'elenco in base ai criteri."),
                ButtonHelp("Apri una ricetta", "Mostra ingredienti, procedimento, foto, tempi, note e link originale."),
                ButtonHelp("Pianifica", "Copia la ricetta nel calendario, scegliendo giorno e pasto."),
                ButtonHelp("Modifica / Elimina", "Cambia la ricetta oppure eliminala dopo conferma."),
                ButtonHelp("Clona su altri giorni", "Crea una nuova assegnazione senza perdere la ricetta di partenza.")
            ), "Eliminare una ricetta dal ricettario è diverso dal rimuoverla soltanto dal calendario."),

        Page("editor", "Scrivere e salvare una ricetta",
            "Puoi compilare una nuova scheda a mano o partire dalle foto di una rivista.",
            listOf(
                ButtonHelp("Nome del piatto", "Scrivi il titolo della ricetta; è obbligatorio."),
                ButtonHelp("Portata / Difficoltà", "Scegli la categoria e la difficoltà."),
                ButtonHelp("Ingredienti", "Usa una riga per ogni ingrediente, conservando dosi e unità."),
                ButtonHelp("Preparazione", "Scrivi i passaggi separatamente; l'app li numera."),
                ButtonHelp("Tempi e cottura", "Inserisci preparazione, cottura, riposo e tempo totale."),
                ButtonHelp("Fotocamera / Galleria", "Aggiunge una fotografia del piatto."),
                ButtonHelp("Aggiungi anche al calendario", "Se attivo, salva e assegna la ricetta alla data e al pasto scelti."),
                ButtonHelp("Salva ricetta nel ricettario", "Conserva la scheda sul telefono."),
                ButtonHelp("Torna indietro senza salvare", "Esce dalla modifica senza memorizzare le modifiche non salvate.")
            ), "Controlla i campi obbligatori prima di salvare. Aprire la Guida non cancella ciò che hai già scritto."),

        Page("ocr", "Fotografare le ricette (OCR)",
            "Scatta le foto in ordine: prima la pagina intera (anche più pagine), poi i dettagli ravvicinati.",
            listOf(
                ButtonHelp("1 · Fotografa pagina", "Inquadra l'intera pagina della rivista, compreso il titolo."),
                ButtonHelp("2 · Foto ingredienti", "Riprendi da vicino l'elenco con simboli, quantità e unità di misura."),
                ButtonHelp("3 · Foto preparazione", "Fotografa tutti i passaggi; se sono su più pagine, aggiungi altre foto."),
                ButtonHelp("4 · Foto dati", "Fotografa dosi, tempi, difficoltà e informazioni utili."),
                ButtonHelp("Importa pagine / ingredienti / preparazione / dati", "Usa fotografie già presenti nel telefono."),
                ButtonHelp("Testo riconosciuto (modificabile)", "L'OCR ML Kit legge il testo automaticamente: correggi gli errori."),
                ButtonHelp("Ricostruisci i campi dalla scansione", "Distribuisce titolo, ingredienti, preparazione e tempi negli appositi campi."),
                ButtonHelp("Fotografa piatto / Conserva questa foto", "Aggiunge e seleziona le fotografie da conservare."),
                ButtonHelp("Usa come copertina", "Sceglie l'immagine che rappresenta la ricetta.")
            ), "Se Gemini Nano è supportato può aiutare a riordinare il testo sul dispositivo. Non inventare ingredienti o passaggi: verifica sempre la scheda finale."),

        Page("summary", "Riepilogo dei pasti",
            "È una vista di controllo per capire cosa hai già programmato.",
            listOf(
                ButtonHelp("Giorni pianificati", "Mostra i giorni per cui hai salvato un menù."),
                ButtonHelp("Statistiche", "Conta ricette, pranzi, cene e pasti pianificati."),
                ButtonHelp("Tocca un giorno", "Riapre quel giorno nel Calendario, con i suoi pasti.")
            ), "Anche le alternative possono essere incluse nei conteggi."),

        Page("sources", "Fonti di ricerca e priorità",
            "Puoi decidere quali siti consultare e in quale ordine cercare.",
            listOf(
                ButtonHelp("Numero di priorità / elenco", "Sposta un sito verso una posizione più alta o più bassa."),
                ButtonHelp("Aggiungi un sito", "Inserisci un nome e un dominio pubblico HTTPS."),
                ButtonHelp("Cestino accanto a un sito", "Toglie la fonte dalle ricerche dopo conferma."),
                ButtonHelp("Ripristina siti e ordine iniziali", "Ripropone le fonti predefinite."),
                ButtonHelp("AI locale e privacy", "Apre lo stato e le impostazioni di Gemini Nano e dell'OCR.")
            ), "Un sito può non essere leggibile automaticamente: in quel caso l'app segnala i risultati non verificati."),

        Page("ai", "AI locale, compatibilità e privacy",
            "L'OCR ML Kit funziona sul telefono. Gemini Nano viene utilizzato soltanto dove è realmente supportato.",
            listOf(
                ButtonHelp("Verifica AI del dispositivo", "Rileva il modello del telefono e la disponibilità reale dell'API Gemini Nano. La presenza di un'app AI Xiaomi o Samsung non autorizza automaticamente l'accesso interno."),
                ButtonHelp("Usa automaticamente Gemini Nano", "Abilita o disabilita l'aiuto automatico dell'AI locale."),
                ButtonHelp("Ricontrolla compatibilità", "Controlla nuovamente se il telefono dispone di Gemini Nano."),
                ButtonHelp("Installa modello Gemini Nano", "Compare solo quando il modello è scaricabile: serve una conferma esplicita e Wi-Fi non a consumo."),
                ButtonHelp("AI non disponibile", "L'app continua normalmente usando l'OCR ML Kit tradizionale."),
                ButtonHelp("Correzione del testo", "Puoi sempre controllare e correggere i dati ricavati dalle fotografie.")
            ), "Nessuna fotografia o ricetta viene inviata a un servizio AI cloud. L'assistente usa il motore disponibile tramite API e, altrimenti, i comandi integrati. Non tutte le AI dei produttori sono accessibili dalle altre app."),

        Page("pdf", "PDF, stampa e dati",
            "Puoi creare un PDF del giorno o della singola ricetta e usare la stampa Android.",
            listOf(
                ButtonHelp("PDF e stampa del giorno", "Crea un riepilogo del menù della data selezionata."),
                ButtonHelp("PDF e stampa della ricetta", "Crea una scheda stampabile della ricetta aperta."),
                ButtonHelp("Salva PDF", "Scegli dove conservare il file sul telefono."),
                ButtonHelp("Stampa", "Apre il servizio di stampa Android se è disponibile."),
                ButtonHelp("Ricette sul telefono", "I dati dell'APK restano locali. Installando un aggiornamento sopra l'app si mantengono i dati, se firma e identificativo sono invariati.")
            ), "L'anteprima web ha dati separati dall'app Android; non è un sistema di backup.")
    )

    fun forScreen(key: String): Int {
        val topic = when {
            key == "calendar" -> "calendar"
            key == "search" || key == "link" -> "search"
            key == "recipe-sites" || key.startsWith("online-") -> "results"
            key == "archive" || key.startsWith("detail-") -> "archive"
            key.startsWith("editor-") -> "editor"
            key == "summary" -> "summary"
            key == "sources" -> "sources"
            key == "ai-settings" -> "ai"
            else -> "start"
        }
        return pages.indexOfFirst { it.key == topic }.coerceAtLeast(0)
    }

    fun indexForKey(key: String): Int = pages.indexOfFirst { it.key == key }
}
