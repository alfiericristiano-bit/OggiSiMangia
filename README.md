# 🍽️ OggiSiMangia...

**Versione Android 1.1.0 — ricerca integrata sperimentale**

App Android per organizzare i pasti e tenere il ricettario personale.

## Cosa puoi fare
- Calendario con pranzo/cena, primo/secondo/piatto unico/dolce, ricetta principale e alternativa.
- Ricerca per ingredienti, categoria, difficoltà e durata.
- **Nuovo:** schermata di ricerca che tenta di mostrare fino a 10 schede con titolo, breve descrizione, fotografia, tempo, valutazione e numero di feedback **quando forniti dalle fonti**. Selezione ordinata per numero di feedback noto, poi attinenza.
- **Nuovo:** lettore interno WebView per leggere la pagina originale senza aprire Chrome; accesso ai soli siti selezionati.
- Scelta da una scheda o dalla pagina web aperta nell'app: **pranzo/cena** e **principale/alternativa**; salvataggio di titolo e link nel calendario.
- Ricettario SQLite locale con ricerca e filtri. Foto scelte dalla galleria compresse.
- Apertura delle ricette salvate **dentro l'app**.

## Limiti trasparenti della ricerca 1.1.0
Il recupero delle anteprime usa, senza chiavi API, i risultati RSS pubblici di Bing limitati ai domini:
**GialloZafferano**, **Il Cucchiaio d'Argento**, **Fatto in Casa da Benedetta**.
Per ogni URL attendibile legge i metadati pubblici Schema.org Recipe e Open Graph.
Questo servizio non garantisce 10 risultati e può bloccare/ridurre le risposte; le schede non sono sempre disponibili.
Se la ricerca strutturata non trova risultati, rimane la **ricerca web interna** e la navigazione alle pagine originali, senza browser esterno.
Non vengono inventati rating, tempi o immagini. La difficoltà non è filtrabile con precisione dove manca nei metadati. Le foto di anteprima sono temporanee e non si salvano automaticamente nel database; i contenuti integrali restano consultabili sul sito originale tramite WebView, per rispettare le condizioni delle fonti.

## Compilazione GitHub
La compilazione avviene nella scheda [Actions](../../actions/workflows/android.yml).
Workflow **Android APK**: produce l'Artifact `OggiSiMangia-v1.1.0-APK`, contenente `OggiSiMangia-v1.1.0-debug.apk`.
Non occorre Android Studio.

**Attenzione agli aggiornamenti:** l'APK debug è firmato dalla chiave temporanea del runner e può risultare incompatibile con quello già installato. **Non disinstallare l'app contenente ricette importanti** prima di averne salvato i dati: questa versione non integra ancora un backup. Per aggiornamenti affidabili occorre introdurre una chiave di firma persistente gestita nei Secrets GitHub.
