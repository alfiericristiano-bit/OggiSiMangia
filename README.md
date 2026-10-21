# 🍽️ OggiSiMangia...

Android 1.2.0 (versionCode 4).

- Un solo Cerca consulta GialloZafferano, Il Cucchiaio d'Argento e Fatto in Casa da Benedetta, mostrando fino a dieci risultati nel popup.
- Scheda interna con ingredienti, dosi, tempo e sintesi estrattiva dei passaggi pubblicati in formato Recipe JSON-LD. La sintesi conserva frasi con quantità, tempi e riferimenti di cottura; il testo completo resta consultabile.
- Vedi ricetta originale sul sito apre il browser su richiesta.
- Salvataggio Semplificata / Originale / Entrambe, con ingredienti e link. Originale significa testo del procedimento leggibile offline, non copia grafica dell'intera pagina.
- Portate: antipasto, primo, secondo, contorno, piatto unico e dolce. Pasto: pranzo, cena o entrambi. Calendario facoltativo, con scelta principale o alternativa.
- Migrazione SQLite v1 → v2 senza eliminare le ricette esistenti.

## Limiti

Le fonti possono bloccare richieste o cambiare struttura. Non sono garantiti dieci risultati né recensioni per ogni ricetta. I conteggi pubblicati orientano l'ordinamento; non certificano la ricetta. Dati mancanti e riassunti non disponibili vengono dichiarati. Non vengono aggirati accessi riservati. Ingredienti e testi salvati dall'utente sono conservati localmente; le immagini dei siti non vengono archiviate automaticamente.

## Build e firma

Actions esegue test del lettore, verifica della migrazione, lint e assembleDebug e pubblica APK e report. L'APK debug è una compilazione di prova: la firma temporanea non garantisce aggiornamento sopra gli APK precedenti.

La build release supporta una chiave persistente fornita esclusivamente tramite i secret GitHub Actions ANDROID_KEYSTORE_BASE64, ANDROID_STORE_PASSWORD, ANDROID_KEY_ALIAS e ANDROID_KEY_PASSWORD. Nessuna chiave viene aggiunta al repository. Quando tutti i secret sono configurati viene pubblicato anche l'APK release.

L'applicationId resta it.oggisimangia.app e il versionCode aumenta. Per aggiornare l'app installata servono la stessa chiave privata e il medesimo certificato della versione già presente. La chiave delle vecchie build effimere potrebbe non essere recuperabile: non disinstallare l'app con dati importanti per superare un errore di firma. Configurazione dei secret, confronto dei certificati e prova di aggiornamento in-place restano necessari prima di distribuire un aggiornamento come compatibile.
