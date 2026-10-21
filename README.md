# 🍽️ OggiSiMangia...

**Android 1.1.0 — anteprime ricette integrate (versione di prova)**

App Android per organizzare i pasti (pranzo e cena) con ricetta principale e alternativa, e conservare un ricettario locale.

## Novità 1.1.0
- Ricerca per ingredienti, categoria, difficoltà e tempo: fino a **10 schede** dentro un popup dell'app.
- Ogni scheda prova a mostrare **nome, fonte, sintesi, foto, tempo, difficoltà e valutazioni/feedback quando effettivamente pubblicati**.
- L'ordinamento dà precedenza al numero di feedback *quando verificabile* e poi all'attinenza con gli ingredienti.
- Pulsante **Leggi**: pagina originale aperta nel visualizzatore WebView **interno** all'app, senza lanciare Chrome.
- Pulsante **Scegli**: specifica pranzo/cena e ricetta principale/alternativa, poi apre la scheda da completare e salvare nel calendario.
- Se una fonte non restituisce schede leggibili, è disponibile la **ricerca diretta nel WebView interno** dei siti selezionati.

## Fonti e limiti
Ricerca on-demand tra pagine pubbliche di **GialloZafferano**, **Il Cucchiaio d'Argento** e **Fatto in Casa da Benedetta**. Le anteprime sono sperimentali: le strutture delle pagine possono cambiare o i siti possono bloccare l'estrazione, quindi **non è garantito** trovare 10 risultati, tutte le foto, tutte le valutazioni o un ordinamento per feedback completo. L'app non inventa dati assenti.

Gli ingredienti e i procedimenti integrali di altre testate **non vengono copiati automaticamente**. Si salva il titolo e il link originale; i tuoi ingredienti, appunti e foto personali possono essere aggiunti nell'editor. Le fotografie delle anteprime sono caricate temporaneamente e non archiviate senza autorizzazione.

## Funzioni conservate dalla 1.0.0
- Calendario mensile, pranzo/cena, primi/secondi/piatti unici/dolci.
- Ricetta principale e alternativa per ogni pasto.
- Archivio SQLite locale con filtri per categoria e difficoltà, ricerca e riutilizzo.
- Foto dalla galleria compresse automaticamente in JPEG per il ricettario personale.

## Build solo su GitHub
Le compilazioni sono nella scheda [Actions](../../actions/workflows/android.yml). Quando una build è verde, GitHub pubblica l'Artifact `OggiSiMangia-v1.1.0-APK` con `OggiSiMangia-v1.1.0-debug.apk`.

**Attenzione:** gli APK debug generati da runner diversi possono avere firme differenti e richiedere la disinstallazione prima di aggiornare. **Non disinstallare** una versione con ricette importanti senza un backup: l'esportazione dati e la firma persistente non sono ancora state implementate.
