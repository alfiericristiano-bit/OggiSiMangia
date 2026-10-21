# 🍽️ OggiSiMangia...

## Regola globale – APK e anteprima Vercel smartphone

Per ogni modifica **sostanziale** proporre sempre **entrambi i percorsi**: link diretto dell'anteprima Vercel in produzione e opzione per l'APK Android. URL di produzione della demo: **https://oggisimangia.vercel.app/**. Specificare la versione effettiva e distinguere la demo browser dall'APK.

**Regola specifica di questo progetto da preservare:** non scaricare, estrarre o allegare APK automaticamente; quando l'utente scrive **«apk»** consegnare il file installabile diretto più recente verificato, con la versione nel nome.

L'anteprima web è **sempre solo in formato smartphone** (PC: cornice e area circa **390 × 844 px**, scorrimento interno; sul telefono reale: tutto il viewport). Niente layout desktop/tablet. Dati dell'anteprima confinati nel browser e non sincronizzati con Android.

**Risparmio quote:** effettuare un'unica pubblicazione Vercel per blocco sostanziale su `main`, raggruppare i piccoli aggiustamenti e usare GitHub Actions senza verifiche online ripetute e superflue. Il monitoraggio approfondito online è lanciato solo manualmente con il workflow `Verifica sito Vercel online`.

Android 1.5.3 (versionCode 16).

- Ricerca: le interrogazioni ai siti provano prima ogni ingrediente seguito da virgola (anche il singolo), poi varianti senza virgola finale / senza punteggiatura. Consultazione parallela dei siti e lettura RSS/HTML di ricerca quando disponibile; la verifica degli ingredienti e dei passaggi resta rigorosa.\n- In caso di risposta vuota l'app chiarisce che alcuni siti possono bloccare la consultazione e che il messaggio non prova l'assenza di ricette.\n\n- Gli ingredienti sono obbligatori e confrontati con le forme singolari, plurali e preparazioni esplicite nella ricetta pubblicata; categoria, difficoltà e tempi sono invece preferenze segnalate sulle schede senza escludere ricette valide.\n- Interfaccia più compatta e pulsanti collegati disposti orizzontalmente.\n

- Restyling di Android e anteprima web ispirato al progetto FamilyChat: gradiente verde acqua #147D72 → #20A58C, navigazione orizzontale compatta, superfici chiare arrotondate, pulsanti e moduli coerenti. Nessun cambiamento al database o ai dati delle ricette.
- Pressione prolungata calendario: su Android viene usato il gesto nativo con tempo gestito dal sistema e dalle impostazioni di accessibilità (senza timer personalizzato di 2 secondi); la versione Vercel usa il tempo web equivalente di circa 500 ms.


- Ricette personali: editor completo con titolo, descrizione, portata, difficoltà, provenienza, porzioni, autore, costo, etichette, allergeni, ingredienti in righe con quantità/unità, procedimento numerato aggiungibile ed eliminabile, tempi di preparazione/cottura/riposo/totale, temperatura, attrezzatura, consigli, varianti, conservazione, valori nutrizionali e note personali. Obbligatori nome, un ingrediente e un passaggio; possibilità di salvare solo nel ricettario o anche nel calendario.
- Foto personali da fotocamera Android (FileProvider privato, senza salvare l'originale esternamente) o selezione dalla galleria; compressione JPEG massima 960 px; rimozione e anteprima nell'editor; le foto delle vecchie ricette restano valide.
- Database SQLite v4 aggiunge campi conservativi e migra automaticamente da v1/v2/v3. I nuovi dati sono visualizzati anche nella scheda ricetta e nei report PDF. La firma e l'identificativo Android rimangono invariati.


- Nelle impostazioni delle fonti si possono eliminare singolarmente siti predefiniti o aggiunti dall'utente con conferma. Non vengono più interrogati, nemmeno dalla ricerca diretta; le eliminazioni restano dopo riavvii e aggiornamenti. L'elenco vuoto è consentito; il comando di ripristino riporta i siti iniziali.
- Regola consegna: dopo le modifiche continuare compilazione e verifiche GitHub, ma **NON scaricare, estrarre o allegare APK automaticamente**. Recuperare e allegare direttamente il file .apk in chat esclusivamente quando l'utente scrive «apk». La versione resta nel nome; il collegamento alla pagina GitHub non sostituisce l'APK scaricabile quando richiesto.


- Le spiegazioni facoltative sono popup compatti con «Ho capito» e casella «Non mostrare più questo messaggio». Le scelte sono memorizzate nelle SharedPreferences dell'app (resistono a riavvii e aggiornamenti senza cancellazione dati); ogni spiegazione appare al più una volta a sessione, salvo richiamo manuale. Il tooltip nativo dei giorni è rimosso; una voce «Come funziona il calendario» permette di rileggere le istruzioni. La regola si estende ai futuri tooltip; non riguarda conferme, avvisi ed errori.


- La tastiera si chiude su Invio/Fatto e quando si toccano i pulsanti o si esce dal campo di inserimento.
- La ricerca dei piatti verifica i dati Recipe JSON-LD: richiede una ricetta vera, con procedimento e tutti gli ingredienti richiesti presenti nella lista ingredienti; titoli, descrizioni e guide non sono prove valide. Anche il filtro del ricettario locale richiede tutti gli ingredienti.
- Su una ricetta già presente in un giorno del calendario, Cambia assegnazione modifica pasto (Pranzo/Cena/Entrambi), principale/alternativa e categoria, senza duplicare. Clona su altri giorni crea copie indipendenti, scegliendo data e pasto/tipo, e permette di ripetere per più giorni.


- Tocco breve su un giorno: seleziona il giorno e mostra i pasti nella home. Pressione lunga standard Android (durata definita dal sistema): conferma Sì/No, poi pranzo/cena e principale/alternativa. No non modifica selezione o dati. Movimento, scorrimento e annullamento interrompono la pressione.
- Se il pasto ha già una ricetta principale, viene preselezionata Alternativa. Il contesto giorno/pasto/tipo è mantenuto nella ricerca, importazione link, editor e conferma di pianificazione. Le alternative salvate appaiono nella home sotto il relativo pasto con etichetta Alternativa; pulsanti dedicati permettono di cercarne altre.
- Ingredienti in caselle separate: una inizialmente, pulsante Aggiungi ingrediente accanto all’ultima casella, fino a 10. Una sola voce per casella; vale sia per ricerca online sia per ricettario.
- Priorità fonti configurabile con un menu a tendina per ciascuna posizione. Scelta di un sito già assegnato: scambio automatico, senza duplicati. Ordine e siti aggiunti dall’utente sono salvati in SharedPreferences; all’avvio vengono ripristinati prima delle ricerche. GialloZafferano rimane primo nell’ordine iniziale. Nuovi domini HTTPS entrano nella ricerca per dominio e nel parser Recipe JSON-LD; disponibilità e dati dipendono dal sito. Nessun accesso riservato viene aggirato.
- Report PDF A4 del giorno e della singola ricetta: pasti principali/alternative, foto locale, dosi e tempi, ingredienti, procedimenti salvati e fonte. Testo impaginato su più pagine, intestazione e numero pagina. Salva PDF usa il selettore documenti Android; Stampa apre il servizio Android con supporto alla selezione delle pagine. La stampante deve essere disponibile nel servizio di stampa del telefono.
- "Ho già trovato la ricetta da aggiungere": importazione da un link HTTPS pubblico, anche di altri siti, nella stessa scheda interna della ricerca. Titolo, foto, ingredienti, procedimento e metadati provengono dai dati pubblicati; in assenza di dati si può salvare il link e completare la scheda manualmente.
- Risultati sfogliabili in gruppi di dieci: Prossime 10, Precedenti 10 e indicazione dell'intervallo. Nessun doppione della stessa URL; l'ultima pagina può contenerne meno di dieci. Tornando dalla scheda si ritrova il gruppo consultato. La navigazione usa le ricette leggibili già raccolte nella ricerca, senza nuove attese di rete a ogni pagina.\n- Foto della fonte nella scheda e copia offline compressa (JPEG, dimensione massima 960 px) al salvataggio. Se il download fallisce viene conservata comunque la ricetta.
- Preparazione, cottura, totale, eventuale riposo, dosi e valori nutrizionali vengono conservati separatamente quando pubblicati dalla fonte. Tempi mancanti non vengono dedotti.
- Aggiungi al calendario apre la scelta del giorno e del pasto, con ricetta principale o alternativa. La griglia mensile legge il database e distingue pranzo (arancione) e cena (blu), anche sullo stesso giorno.
- Ricettario con filtro ingredienti OR: basta almeno uno tra quelli indicati, separati da virgola, punto e virgola o riga. Ogni ricetta trovata può essere inserita nel giorno scelto dal calendario.
- Migrazione dello schema SQLite v1/v2 → v3 preservando le ricette e le foto esistenti.
- Ricerca su 11 fonti: GialloZafferano, Cucchiaio d’Argento, Fatto in casa da Benedetta, Misya, Cookaround, La Cucina Italiana, Tavolartegusto, Ricetta.it, Sale&Pepe, RicetteDalMondo, Agrodolce. Fonti centralizzate in RecipeSources. Ogni ricerca tenta tutte le fonti via ricerca pubblica per dominio e verifica delle pagine Recipe JSON-LD; le tre fonti originali hanno anche ricerca diretta. Quote per fonte e richieste concorrenti con scadenza. Nessun aggiramento dei blocchi dei siti.
- Fonti nell’ordine configurato (GialloZafferano primo per impostazione iniziale), poi: in ogni gruppo corrispondenza ingredienti, media ponderata (prior 3,5/5 con peso 20), numero di voti/recensioni. Rating normalizzati a 5 in base alla scala dichiarata; commenti e visualizzazioni non diventano voti. Ricette prive di rating restano disponibili dopo quelle valutate a parità di pertinenza. Dieci risultati per pagina. La ricerca non garantisce copertura completa di ciascun sito.
- Scheda interna con ingredienti, dosi, tempo e sintesi estrattiva dei passaggi pubblicati in formato Recipe JSON-LD. La sintesi conserva frasi con quantità, tempi e riferimenti di cottura; il testo completo resta consultabile.
- Vedi ricetta originale sul sito apre il browser su richiesta.
- Salvataggio Semplificata / Originale / Entrambe, con ingredienti e link. Originale significa testo del procedimento leggibile offline, non copia grafica dell'intera pagina.
- Portate: antipasto, primo, secondo, contorno, piatto unico e dolce. Pasto: pranzo, cena o entrambi. Calendario facoltativo, con scelta principale o alternativa.

## Limiti

Le fonti possono bloccare richieste o cambiare struttura. Non sono garantiti dieci risultati né recensioni per ogni ricetta. I conteggi pubblicati orientano l'ordinamento; non certificano la ricetta. Dati mancanti e riassunti non disponibili vengono dichiarati. Non vengono aggirati accessi riservati. Ingredienti e testi salvati dall'utente sono conservati localmente; le foto della fonte vengono conservate in copia compressa solo quando si sceglie di salvare o pianificare la ricetta, mantenendo il riferimento alla fonte.

## Anteprima web su Vercel — aggiornamento automatico

La cartella `docs/` contiene l'anteprima browser funzionante di **OggiSiMangia...**, separata dall'app Android. Si possono provare calendario, ricettario, creazione delle ricette con foto compressa, ingredienti richiesti tutti insieme, clonazione e modifica dei pasti, gestione delle fonti e backup JSON. Le funzioni native Android e la ricerca ricette online non sono replicate: i dati sono locali al browser e **non** sincronizzati con l'APK.

**Provider di anteprima preferito: Vercel**. Il file `vercel.json` nella radice del repository specifica framework statico, nessun comando di build e cartella pubblicata `docs`. Il repository GitHub rimane privato. Per l'attivazione una tantum, in Vercel aprire **Add New → Project**, importare `alfiericristiano-bit/OggiSiMangia` (GitHub già connesso), impostare **Root Directory `./`** / radice del repository e fare **Deploy**. Vercel legge `vercel.json` e pubblica direttamente `docs/index.html`. Una volta importato, i commit su `main` aggiorneranno automaticamente il medesimo indirizzo di produzione `.vercel.app`.

La GitHub Action `.github/workflows/pages.yml` ora verifica l'anteprima e `vercel.json`, ma **non** pubblica su GitHub Pages. Non è necessario attivare GitHub Pages né rendere pubblico il repository. Il nome URL effettivo viene attribuito e mostrato da Vercel solo dopo il primo Deploy; non presumere che un indirizzo sia già attivo.

Istruzioni: `docs/README.md`. La compilazione APK rimane separata e il recupero del file avviene **solo su richiesta «apk»**.

## Regola permanente di distribuzione APK nella chat

1. Dopo ogni modifica al codice, avviare GitHub Actions e verificare test, lint, firma persistente e aggiornamento in-place senza perdita dei dati. Pubblicare un Artifact con il numero di versione.
2. **Non scaricare, estrarre né allegare l'APK a ogni compilazione.** Presentare in chat l'esito e la versione verificata, senza intraprendere la fase di recupero file.
3. **Soltanto quando l'utente scrive «apk»**, recuperare l'Artifact più recente con esito positivo, estrarre il file `.apk` e allegarlo direttamente come file scaricabile in chat, senza sostituirlo con un semplice collegamento alla pagina GitHub.
4. Non dichiarare l'APK disponibile se non è stato effettivamente recuperato. Se il recupero diretto non è tecnicamente possibile, descrivere la limitazione e indicare il percorso GitHub verificato.
5. Mantenere identici `applicationId` e certificato di firma e aumentare `versionCode` a ogni nuova release; conservare ricette, calendario e preferenze durante gli aggiornamenti.

## Build e firma persistente (1.2.1)

La distribuzione pubblica soltanto APK release firmati con la chiave persistente, dopo test, lint e prova di aggiornamento in-place su emulatore. Il controllo installa due versioni firmate con la stessa chiave e verifica che un file nel sandbox dell'app resti presente dopo adb install -r.

Sono obbligatori cinque secret Actions: ANDROID_KEYSTORE_BASE64, ANDROID_STORE_PASSWORD, ANDROID_KEY_ALIAS, ANDROID_KEY_PASSWORD e ANDROID_CERT_SHA256. Quest'ultimo contiene il fingerprint pubblico SHA-256 del certificato e impedisce di pubblicare accidentalmente con un'altra chiave. Secret assenti o certificato diverso bloccano la distribuzione; non esiste fallback a firma temporanea.

La chiave privata non deve essere inserita nel repository, nei log, nelle cache Actions o negli artifact. Conservare una copia di sicurezza cifrata indipendente, con password gestita in modo sicuro. La chiave viene estratta in un file temporaneo del runner con permessi limitati ed eliminata al termine della build.

L'applicationId resta it.oggisimangia.app. Il versionCode di distribuzione aumenta; la proprietà verificationVersionCode è riservata al test su emulatore. La prima installazione con questa nuova chiave richiede di rimuovere le precedenti build debug firmate diversamente, con perdita dei dati: l'utente ha autorizzato questa transizione. Gli aggiornamenti successivi devono mantenere la stessa chiave e aumentare il versionCode.

Firma persistente configurata nei secret Actions. La build 39552763647 ha superato test, lint, controllo del certificato e aggiornamento in-place su emulatore Android 29 (versionCode 5 → 6), conservando un file di prova nel sandbox. Questa verifica della firma rimane valida come baseline; ogni nuova build ripete il test con versionCode crescente. Anche la firma crittografica APK v2 e il digest del file scaricato sono stati verificati.

Certificato pubblico SHA-256: 3d788e2a2dfeeaa5039c6c0422b6f8068a709f7c9672d14635ceecbc18114c95. La copia riservata di recupero è conservata separatamente dal repository.

