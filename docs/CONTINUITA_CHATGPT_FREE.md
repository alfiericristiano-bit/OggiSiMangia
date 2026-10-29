# OggiSiMangia... — Kit di continuità (ChatGPT Free)
Ultimo aggiornamento documentazione: 29 ottobre 2026.

## 1. Dove si trova il progetto
- Repository: https://github.com/alfiericristiano-bit/OggiSiMangia
- Branch di sviluppo attuale: `artifact/restart-v1.10.0-20261028`
- Ultimo commit del codice al momento della stesura: `c663ee35109f5f3ac208b379f78ed96c9597a44b`
- Anteprima browser: https://oggisimangia.vercel.app/ — **simulazione web**, non è il codice Android e non dimostra il funzionamento delle ricerche native.
- Identificativo Android: `it.oggisimangia.app`.
- `versionCode` attuale nel sorgente: 35; `versionName`: `1.10.0-ripartenza`.
- La versione Android attuale **non è stata compilata né verificata** dopo le modifiche recenti.
- L'APK installato non viene modificato da un commit GitHub.

## 2. Riprendere il lavoro con ChatGPT Free
In una nuova chat incollare il testo in `docs/PROMPT_RIPRESA.md` e, se gli strumenti GitHub non sono disponibili, allegare i file sorgente interessati oppure un archivio del repository senza segreti. Chiedere modifiche mirate, file completi o patch e test; applicarle in Android Studio o tramite GitHub. Suddividere interventi grandi in attività piccole, indicando sempre branch e commit di partenza. Non dichiarare un APK pronto senza una build effettiva.

## 3. Compilazione indipendente da ChatGPT su Windows 11
1. Installare Android Studio e i componenti Android SDK per compileSdk 35. Usare JDK 17.
2. Da GitHub: `Code > Download ZIP` per una copia del branch, oppure `git clone https://github.com/alfiericristiano-bit/OggiSiMangia.git` e `git switch artifact/restart-v1.10.0-20261028`.
3. Aprire la cartella del progetto in Android Studio, attendere la sincronizzazione Gradle. Il repository non contiene attualmente `gradlew.bat`; serve Gradle 8.11.1 installato o configurato in Android Studio.
4. Eseguire i test `gradle --no-daemon :app:testDebugUnitTest :app:lintDebug` dalla cartella progetto, con Java/Gradle nel PATH. Se Gradle non è riconosciuto, configurare l'installazione locale prima di procedere.
5. Solo dopo test e correzioni, eseguire `gradle --no-daemon :app:assembleDebug` per una build di prova. **Non installare una debug APK firmata con chiave diversa sopra l'app già installata.**
6. Per un APK aggiornabile, recuperare **la stessa chiave di firma originale** e usare `scripts/build_windows_signed.ps1` (vedere sotto). Se manca, fermarsi e recuperarla; non generarne una nuova fingendo che sia compatibile.
7. Verificare su un telefono di prova, con backup locale JSON già esportato, che dati, foto, calendario e ricette sopravvivano a `adb install -r`.

## 4. Firma Android: fondamentale
Il file keystore (.jks), alias, password e impronta SHA-256 del certificato **non sono nel repository**. Il workflow GitHub usa i secret `ANDROID_KEYSTORE_BASE64`, `ANDROID_STORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`, `ANDROID_CERT_SHA256`.
- Il secret GitHub non è normalmente leggibile in chiaro: se non hai una copia del .jks originale, va recuperata da un backup sicuro.
- Non caricare keystore, password, `.env` o file contenenti credenziali su GitHub, né incollarli in ChatGPT.
- Conservare due copie cifrate della chiave, separate, e verificare che le credenziali siano disponibili.
- Lo script Windows richiede variabili di ambiente impostate **solo nella sessione corrente**, senza memorizzare le password in file.
- Controlla l'impronta del certificato con `keytool`; se non corrisponde, blocca la build.

## 5. GitHub Actions e Vercel
- Workflow Android: `.github/workflows/android.yml`; attivazione manuale `workflow_dispatch`.
- Non lanciare workflow finché non si vuole effettivamente usare la quota GitHub Actions.
- Il workflow corrente dipende da secret di firma, test, SDK/emulatore e dal branch storico beta7; **non è ancora stato riverificato** dopo gli ultimi cambiamenti.
- L'anteprima Vercel è distinta dall'APK; aggiornare la demo soltanto quando serve, senza presumere che le funzioni native siano presenti nel browser.
- Le quote GitHub e Vercel sono indipendenti dal piano ChatGPT.

## 6. Stato funzionale e debiti tecnici
- Ricerca ingredienti: mantenere fino a 10 ingredienti e primo ingrediente principale.
- Ricerca per categoria/sottocategoria, ricerca per nome e modalità sorpresa: aggiunte nel codice, ma non compilate/verificate.
- Esclusioni: i risultati con lista ingredienti illeggibile devono essere segnalati come **non verificati**, non sicuri.
- «Altre idee»: oggi ruota risultati; non garantisce varietà semantica o storico anti-ripetizioni.
- Cambio categoria: l'aggiornamento automatico delle sottocategorie e la conservazione di tutti i campi **restano da completare**.
- La ricerca per nome usa `RecipeDiscovery.searchTopic`; verificare filtraggio e pertinenza dei risultati prima del rilascio.
- Preservare ricettario, foto OCR locale, calendario, fonti personalizzate e backup locali; niente assistente vocale.
- Nessuna nuova compilazione Android è stata eseguita per questa revisione. Non promettere APK pronto.

## 7. Check-list prima di un aggiornamento
- [ ] `git status` pulito e branch corretto
- [ ] Test unitari e lint superati
- [ ] Ricerca ingredienti, per nome, per piatto, sorpresa e filtri provati
- [ ] Controllo fonti, deduplica, categorie e risultati non verificati
- [ ] Identificativo applicazione invariato
- [ ] `versionCode` incrementato rispetto alla versione installata (non usare override di test per il rilascio)
- [ ] Certificato originale verificato
- [ ] Backup JSON locale effettuato
- [ ] Installazione `adb install -r` senza perdita di dati verificata
- [ ] Link APK e versione reale comunicati solo dopo build riuscita

## 8. Salvare una copia offline
Da GitHub, scegliere il branch giusto e `Code > Download ZIP`, poi conservare lo ZIP in una cartella personale di backup. Lo ZIP contiene il sorgente ma **non** i secret GitHub, il keystore o gli APK storici. Fare backup separati e sicuri di questi elementi. Conservare anche un'esportazione JSON dall'app.
