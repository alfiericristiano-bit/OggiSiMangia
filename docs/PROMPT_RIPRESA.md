# Prompt da incollare in una nuova chat ChatGPT Free

Sto sviluppando OggiSiMangia..., app Android Kotlin per ricette e calendario pasti.
Repository GitHub: https://github.com/alfiericristiano-bit/OggiSiMangia
Branch di sviluppo: artifact/restart-v1.10.0-20261028.
Leggi prima docs/CONTINUITA_CHATGPT_FREE.md, README.md e i sorgenti che ti indico. Non supporre che gli ultimi commit siano stati compilati.

Regole imprescindibili:
- Non perdere i dati esistenti (ricette, foto, calendario, preferenze, backup locali).
- applicationId: it.oggisimangia.app; non cambiare il certificato di firma.
- VersionCode va aumentato per nuovi APK, mantenendo la firma originale.
- Niente Firebase e niente assistente vocale; mantenere OCR locale e ricerca.
- Ricerca ingredienti separata da ricerca per nome, categoria/sottocategoria e «Non so cosa cucinare».
- Ingredienti preferiti vs obbligatori, ingredienti esclusi, priorità dei siti configurabili.
- Non inventare verifiche di ingredienti non leggibili; distinguere risultati verificati e da controllare.
- Vercel è solo anteprima web smartphone e non equivale all'APK Android.
- Non avviare GitHub Actions né fare deploy senza che te lo chieda; contenere i costi.
- Preferire modifiche mirate e riepilogo TXT; non richiedere ZIP completi inutilmente.
- Dopo cambi sostanziali indicare stato reale di APK firmato e anteprima Vercel.
- Se non puoi accedere direttamente a GitHub, fornisci file o patch applicabili manualmente e istruzioni Windows 11.

Prima di modificare il codice, controlla l'ultimo commit sul branch e proponi una sequenza di correzioni verificabili. Al momento la ricerca per nome e per piatto è da collaudare; il cambio dinamico delle sottocategorie è incompleto; la modalità sorpresa non garantisce ancora proposte realmente diverse. Non dichiarare questi punti risolti senza verifica.
