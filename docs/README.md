# Anteprima web OggiSiMangia...

Questa directory contiene una **versione browser interattiva** del progetto Android, utilizzabile con GitHub Pages. NON esegue il file APK e non condivide automaticamente SQLite o le foto con Android.

Funzioni disponibili: calendario pranzo/cena e alternative; pressione di oltre 2 secondi sul giorno; ricettario con ingredienti in modalità AND; inserimento manuale con passaggi numerati e foto compresse; modifica/clonazione dei pasti; gestione delle fonti; backup JSON; stampa tramite browser.

Dati: sono conservati esclusivamente nel localStorage del browser, senza servizi esterni. L'esportazione JSON permette il recupero e il trasferimento manuale **solo nella versione web**, non nell'APK Android.

Ricerca online in siti esterni, lettura JSON-LD, sveglie/notifiche, stampa tramite servizio Android e integrazione con SQLite non sono disponibili nella versione Pages.

## Attivare Pages

1. Apri il repository su GitHub e vai in **Settings → Pages**.
2. In **Build and deployment → Source** scegli **GitHub Actions**.
3. Verifica che la GitHub Action **Anteprima web GitHub Pages** completi il deployment; la pagina apparirà nell'indirizzo indicato da GitHub.

**Repository privato:** GitHub Pages richiede un piano GitHub Pro / Team / Enterprise, oppure un repository pubblico se si usa GitHub Free. Non cambiare la visibilità di questo repository senza una decisione esplicita.

Ogni push sul ramo `main` che modifica `docs/**` pubblicherà automaticamente la nuova anteprima una volta abilitato Pages; test sintattici e smoke test sono previsti anche per le pull request.

Gli APK Android continueranno ad essere compilati dal workflow Android; l'APK verrà recuperato e allegato in chat soltanto alla richiesta testuale «apk» dell'utente.
