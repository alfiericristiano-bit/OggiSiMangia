# 🍽️ OggiSiMangia...

Android 1.2.2 (versionCode 6).

- Risultati sfogliabili in gruppi di dieci: Prossime 10, Precedenti 10 e indicazione dell'intervallo. Nessun doppione della stessa URL; l'ultima pagina può contenerne meno di dieci. Tornando dalla scheda si ritrova il gruppo consultato. La navigazione usa le ricette leggibili già raccolte nella ricerca, senza nuove attese di rete a ogni pagina.\n- Un solo Cerca consulta GialloZafferano, Il Cucchiaio d'Argento e Fatto in Casa da Benedetta, mostrando dieci risultati per pagina nel popup.
- Scheda interna con ingredienti, dosi, tempo e sintesi estrattiva dei passaggi pubblicati in formato Recipe JSON-LD. La sintesi conserva frasi con quantità, tempi e riferimenti di cottura; il testo completo resta consultabile.
- Vedi ricetta originale sul sito apre il browser su richiesta.
- Salvataggio Semplificata / Originale / Entrambe, con ingredienti e link. Originale significa testo del procedimento leggibile offline, non copia grafica dell'intera pagina.
- Portate: antipasto, primo, secondo, contorno, piatto unico e dolce. Pasto: pranzo, cena o entrambi. Calendario facoltativo, con scelta principale o alternativa.
- Migrazione SQLite v1 → v2 senza eliminare le ricette esistenti.

## Limiti

Le fonti possono bloccare richieste o cambiare struttura. Non sono garantiti dieci risultati né recensioni per ogni ricetta. I conteggi pubblicati orientano l'ordinamento; non certificano la ricetta. Dati mancanti e riassunti non disponibili vengono dichiarati. Non vengono aggirati accessi riservati. Ingredienti e testi salvati dall'utente sono conservati localmente; le immagini dei siti non vengono archiviate automaticamente.

## Build e firma persistente (1.2.1)

La distribuzione pubblica soltanto APK release firmati con la chiave persistente, dopo test, lint e prova di aggiornamento in-place su emulatore. Il controllo installa due versioni firmate con la stessa chiave e verifica che un file nel sandbox dell'app resti presente dopo adb install -r.

Sono obbligatori cinque secret Actions: ANDROID_KEYSTORE_BASE64, ANDROID_STORE_PASSWORD, ANDROID_KEY_ALIAS, ANDROID_KEY_PASSWORD e ANDROID_CERT_SHA256. Quest'ultimo contiene il fingerprint pubblico SHA-256 del certificato e impedisce di pubblicare accidentalmente con un'altra chiave. Secret assenti o certificato diverso bloccano la distribuzione; non esiste fallback a firma temporanea.

La chiave privata non deve essere inserita nel repository, nei log, nelle cache Actions o negli artifact. Conservare una copia di sicurezza cifrata indipendente, con password gestita in modo sicuro. La chiave viene estratta in un file temporaneo del runner con permessi limitati ed eliminata al termine della build.

L'applicationId resta it.oggisimangia.app. Il versionCode di distribuzione aumenta; la proprietà verificationVersionCode è riservata al test su emulatore. La prima installazione con questa nuova chiave richiede di rimuovere le precedenti build debug firmate diversamente, con perdita dei dati: l'utente ha autorizzato questa transizione. Gli aggiornamenti successivi devono mantenere la stessa chiave e aumentare il versionCode.

Firma persistente configurata nei secret Actions. La build 39552763647 ha superato test, lint, controllo del certificato e aggiornamento in-place su emulatore Android 29 (versionCode 5 → 6), conservando un file di prova nel sandbox. Questa verifica della firma rimane valida come baseline; ogni nuova build ripete il test con versionCode crescente. Anche la firma crittografica APK v2 e il digest del file scaricato sono stati verificati.

Certificato pubblico SHA-256: 3d788e2a2dfeeaa5039c6c0422b6f8068a709f7c9672d14635ceecbc18114c95. La copia riservata di recupero è conservata separatamente dal repository.

