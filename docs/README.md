# OggiSiMangia... — Anteprima web su Vercel

Questa directory è l'anteprima browser interattiva del progetto Android. **Non** esegue un APK e **non** sincronizza il database SQLite Android con le ricette nel browser.

## Funzioni provabili

Calendario pranzo/cena (compreso tocco prolungato di oltre 2 secondi), ricettario, filtro con TUTTI gli ingredienti richiesti, ricette manuali con passaggi numerati e fotografie compresse, modifica/duplicazione delle assegnazioni in altri giorni, fonti di ricerca aggiungibili/riordinabili/eliminabili, tema chiaro/scuro, salvataggio locale, import/export JSON e stampa dal browser.

La ricerca automatica online sui siti esterni e le API native Android (es. stampa Android e storage SQLite) **non** sono disponibili nella versione web. I dati inseriti nell'anteprima rimangono nel `localStorage` di quel browser. Non sono salvati su Vercel: se si cancellano i dati del browser si possono perdere, quindi usare «Esporta backup JSON» per conservarli. L'importazione JSON riguarda soltanto la versione web.

## Pubblicazione una tantum con Vercel

Prerequisito: il tuo account Vercel ha già GitHub collegato, con accesso al repository privato `alfiericristiano-bit/OggiSiMangia`.

1. Apri **https://vercel.com/new** e scegli **Add New → Project** (oppure la schermata Import Git Repository).
2. Seleziona **Import** accanto al repository `alfiericristiano-bit/OggiSiMangia`. Se non è visibile, aggiorna in Vercel l'accesso dell'integrazione GitHub a quel repository, senza renderlo pubblico.
3. Come nome del progetto suggerito usa `oggisimangia-web` (se libero). Scegli come **Root Directory la radice `./`**, non `docs`.
4. Il file `vercel.json` già inserito alla radice imposta **Framework Preset: Other**, **Build Command: nessuno** e **Output Directory: docs**. Controlla questi valori se Vercel mostra impostazioni divergenti; non serve installare pacchetti né impostare variabili d'ambiente.
5. Premi **Deploy**. Vercel pubblicherà `docs/index.html` sulla sua URL di produzione. Salva il link reale mostrato da Vercel: **è quello permanente**, non il link di una preview temporanea del singolo commit.

Dopo il primo import, Vercel esegue autonomamente i deployment dei commit sul ramo `main`, mantenendo il medesimo indirizzo di produzione. Le pull request possono ricevere URL di anteprima separati. Non occorre compilare l'APK per aggiornare questa versione web.

## Verifiche

GitHub Actions esegue `node --check docs/app.js`, `node scripts/test_pages_preview.cjs` e `node scripts/test_vercel_config.cjs` sui cambiamenti web.

Per ogni aggiornamento: mantenere l'anteprima Vercel aggiornata tramite il collegamento GitHub. **Non** estrarre o allegare l'APK a ogni modifica: farlo solo se l'utente scrive «apk».

Il repository Android può rimanere privato anche quando il sito web è raggiungibile da un URL pubblico. Non inserire chiavi private, dati reali degli utenti o credenziali nella cartella `docs`.

## Simulazione smartphone obbligatoria
L'anteprima Vercel è dedicata soltanto allo smartphone: anche su PC mostra un telefono virtuale largo circa 390 px e alto 844 px; menu mobile in basso, niente sidebar desktop. Su smartphone usa la dimensione reale del viewport. Per ogni modifica sostanziale proporre il link diretto Vercel e l'opzione APK, senza superare le quote automatiche e senza allegare l'APK a meno che l'utente lo chieda scrivendo «apk».
