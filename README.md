# 🍽️ OggiSiMangia...

Android 1.3.2 (versionCode 9).

- Tocco su un giorno della home: apre la ricerca mantenendo la data scelta, con accesso ai pasti già pianificati.
- "Ho già trovato la ricetta da aggiungere": importazione da un link HTTPS pubblico, anche di altri siti, nella stessa scheda interna della ricerca. Titolo, foto, ingredienti, procedimento e metadati provengono dai dati pubblicati; in assenza di dati si può salvare il link e completare la scheda manualmente.
- Risultati sfogliabili in gruppi di dieci: Prossime 10, Precedenti 10 e indicazione dell'intervallo. Nessun doppione della stessa URL; l'ultima pagina può contenerne meno di dieci. Tornando dalla scheda si ritrova il gruppo consultato. La navigazione usa le ricette leggibili già raccolte nella ricerca, senza nuove attese di rete a ogni pagina.\n- Foto della fonte nella scheda e copia offline compressa (JPEG, dimensione massima 960 px) al salvataggio. Se il download fallisce viene conservata comunque la ricetta.
- Preparazione, cottura, totale, eventuale riposo, dosi e valori nutrizionali vengono conservati separatamente quando pubblicati dalla fonte. Tempi mancanti non vengono dedotti.
- Aggiungi al calendario apre la scelta del giorno e del pasto, con ricetta principale o alternativa. La griglia mensile legge il database e distingue pranzo (arancione) e cena (blu), anche sullo stesso giorno.
- Ricettario con filtro ingredienti OR: basta almeno uno tra quelli indicati, separati da virgola, punto e virgola o riga. Ogni ricetta trovata può essere inserita nel giorno scelto dal calendario.
- Migrazione dello schema SQLite v1/v2 → v3 preservando le ricette e le foto esistenti.
- Ricerca su 11 fonti: GialloZafferano, Cucchiaio d’Argento, Fatto in casa da Benedetta, Misya, Cookaround, La Cucina Italiana, Tavolartegusto, Ricetta.it, Sale&Pepe, RicetteDalMondo, Agrodolce. Fonti centralizzate in RecipeSources. Ogni ricerca tenta tutte le fonti via ricerca pubblica per dominio e verifica delle pagine Recipe JSON-LD; le tre fonti originali hanno anche ricerca diretta. Quote per fonte e richieste concorrenti con scadenza. Nessun aggiramento dei blocchi dei siti.
- Prima le ricette pertinenti di GialloZafferano, poi le altre: in ogni gruppo corrispondenza ingredienti, media ponderata (prior 3,5/5 con peso 20), numero di voti/recensioni. Rating normalizzati a 5 in base alla scala dichiarata; commenti e visualizzazioni non diventano voti. Ricette prive di rating restano disponibili dopo quelle valutate a parità di pertinenza. Dieci risultati per pagina. La ricerca non garantisce copertura completa di ciascun sito.
- Scheda interna con ingredienti, dosi, tempo e sintesi estrattiva dei passaggi pubblicati in formato Recipe JSON-LD. La sintesi conserva frasi con quantità, tempi e riferimenti di cottura; il testo completo resta consultabile.
- Vedi ricetta originale sul sito apre il browser su richiesta.
- Salvataggio Semplificata / Originale / Entrambe, con ingredienti e link. Originale significa testo del procedimento leggibile offline, non copia grafica dell'intera pagina.
- Portate: antipasto, primo, secondo, contorno, piatto unico e dolce. Pasto: pranzo, cena o entrambi. Calendario facoltativo, con scelta principale o alternativa.

## Limiti

Le fonti possono bloccare richieste o cambiare struttura. Non sono garantiti dieci risultati né recensioni per ogni ricetta. I conteggi pubblicati orientano l'ordinamento; non certificano la ricetta. Dati mancanti e riassunti non disponibili vengono dichiarati. Non vengono aggirati accessi riservati. Ingredienti e testi salvati dall'utente sono conservati localmente; le foto della fonte vengono conservate in copia compressa solo quando si sceglie di salvare o pianificare la ricetta, mantenendo il riferimento alla fonte.

## Build e firma persistente (1.2.1)

La distribuzione pubblica soltanto APK release firmati con la chiave persistente, dopo test, lint e prova di aggiornamento in-place su emulatore. Il controllo installa due versioni firmate con la stessa chiave e verifica che un file nel sandbox dell'app resti presente dopo adb install -r.

Sono obbligatori cinque secret Actions: ANDROID_KEYSTORE_BASE64, ANDROID_STORE_PASSWORD, ANDROID_KEY_ALIAS, ANDROID_KEY_PASSWORD e ANDROID_CERT_SHA256. Quest'ultimo contiene il fingerprint pubblico SHA-256 del certificato e impedisce di pubblicare accidentalmente con un'altra chiave. Secret assenti o certificato diverso bloccano la distribuzione; non esiste fallback a firma temporanea.

La chiave privata non deve essere inserita nel repository, nei log, nelle cache Actions o negli artifact. Conservare una copia di sicurezza cifrata indipendente, con password gestita in modo sicuro. La chiave viene estratta in un file temporaneo del runner con permessi limitati ed eliminata al termine della build.

L'applicationId resta it.oggisimangia.app. Il versionCode di distribuzione aumenta; la proprietà verificationVersionCode è riservata al test su emulatore. La prima installazione con questa nuova chiave richiede di rimuovere le precedenti build debug firmate diversamente, con perdita dei dati: l'utente ha autorizzato questa transizione. Gli aggiornamenti successivi devono mantenere la stessa chiave e aumentare il versionCode.

Firma persistente configurata nei secret Actions. La build 39552763647 ha superato test, lint, controllo del certificato e aggiornamento in-place su emulatore Android 29 (versionCode 5 → 6), conservando un file di prova nel sandbox. Questa verifica della firma rimane valida come baseline; ogni nuova build ripete il test con versionCode crescente. Anche la firma crittografica APK v2 e il digest del file scaricato sono stati verificati.

Certificato pubblico SHA-256: 3d788e2a2dfeeaa5039c6c0422b6f8068a709f7c9672d14635ceecbc18114c95. La copia riservata di recupero è conservata separatamente dal repository.

