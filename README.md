# 🍽️ OggiSiMangia...

**Versione Android: 1.0.0 — primo prototipo funzionale**

App Android in italiano per programmare i pasti e conservare un ricettario personale.

## Funzionalità disponibili
- Calendario mensile con selezione della data, navigazione ai giorni precedenti/successivi
- Pranzo e cena, primo, secondo, piatto unico e dolce
- Per ogni ricetta: selezione **principale** o **alternativa**
- Inserimento di ingredienti, preparazione, difficoltà e tempo disponibile
- Ricerca guidata per ingredienti che apre risultati web limitati a **GialloZafferano**, **Il Cucchiaio d'Argento** e **Fatto in Casa da Benedetta**
- Ricette pianificate in **SQLite locale**, consultabili anche offline
- Ricettario con filtri per categoria e difficoltà, ricerca per nome/ingredienti e ordinamenti
- Apertura della ricetta originale dal link salvato
- Foto prese dalla galleria del telefono, compresse in JPEG (lato lungo fino a 960px, qualità 75)
- Possibilità di ripianificare una ricetta esistente oppure eliminarla

## Ricerca web: stato reale
La versione 1.0.0 **non** scarica automaticamente 10 ricette né classifica le proposte per numero di recensioni. La funzione Cerca apre ricerche mirate nel browser, perché per i 10 risultati in-app con valutazioni attendibili serve una fonte dati autorizzata e verificabile. L'importazione automatica integrale di testi e fotografie di terzi richiede anche di rispettare licenze e condizioni dei siti; per ora testi e foto possono essere inseriti manualmente e il link originale viene conservato.

## Compilazione e download dell'APK — tutto su GitHub
1. Vai alla scheda [Actions](../../actions/workflows/android.yml) del repository.
2. Seleziona l'ultima esecuzione **Android APK**.
3. Se l'esito è verde, in **Artifacts** apri OggiSiMangia-v1.0.0-APK.
4. Scarica lo ZIP generato da GitHub e trova **OggiSiMangia-v1.0.0-debug.apk**.
5. Installa l'APK sul dispositivo Android, autorizzando se richiesto le installazioni da questa origine.

Il workflow esegue assembleDebug su GitHub Actions, senza usare Android Studio. L'APK è una build debug firmata con la chiave di sviluppo del runner: futuri aggiornamenti potrebbero richiedere una firma persistente. **Non disinstallare l'app se contiene ricette importanti**: disinstallandola potresti perdere il database locale.

## In sviluppo per le versioni successive
- Risultati ricette in-app (10 proposte) con foto, sintesi, rating verificabili, tempi e filtro avanzato
- Importazione conforme di ingredienti/procedimento/foto con autorizzazione dei fornitori
- Backup/esportazione del ricettario per evitare perdita dei dati
- Interfaccia grafica più ricca e icona personalizzata
