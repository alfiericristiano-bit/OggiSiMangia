# OggiSiMangia... Desktop (Windows 11)

**Prima versione sperimentale**, separata dal progetto Android. Non modifica l'APK, i database Android o la configurazione di firma.

## Funzioni già nel codice
- Programma desktop con finestra ridimensionabile, mouse e tastiera.
- Archivio ricette SQLite locale; creazione, modifica, ricerca ed eliminazione.
- Pianificazione giornaliera pranzo/cena con ricetta principale e alternativa.
- Fonti ricette configurabili e ricerca tramite browser nelle fonti attive.
- Esportazione/importazione di backup JSON **solo del formato desktop**.
- Percorso immagine associabile alla ricetta; non è ancora un gestore completo di fotografie.

## Limitazioni importanti
- **Non è ancora la replica funzionale dell'app Android**: OCR, anteprima PDF, importazione dei backup Android, ricerca integrata con verifica delle ricette, calendario mensile, filtri completi e «Sorprendimi» sono ancora da implementare.
- La ricerca apre Google nel browser per ciascuna fonte; non estrae ricette né verifica automaticamente ingredienti/allergeni.
- I backup JSON desktop non includono i file immagine esterni; spostando le foto i percorsi possono diventare invalidi.
- Il codice non è ancora stato provato su Windows 11 e non è stato generato un EXE.
- Non avviare GitHub Actions per questo progetto fino ad autorizzazione esplicita.

## Avvio locale (Python)
1. Installare Python 3.11 o successivo da python.org, con Tkinter.
2. Nella cartella del repository: `python desktop/oggisimangia_desktop.py`.
3. I dati vengono conservati in `%LOCALAPPDATA%\\OggiSiMangia\\desktop.sqlite3`.
4. Prima di reinstallare o modificare il programma, esportare il backup JSON dall'interfaccia.

## Generazione EXE locale (senza GitHub Actions)
Da PowerShell nella radice del repository:

```powershell
py -m venv .venv-desktop
.\\.venv-desktop\\Scripts\\python.exe -m pip install --upgrade pip pyinstaller
.\\.venv-desktop\\Scripts\\pyinstaller.exe --noconfirm --clean --onefile --windowed --name OggiSiMangia-Desktop desktop/oggisimangia_desktop.py
```

Risultato atteso: `dist/OggiSiMangia-Desktop.exe`. Non è un installer firmato digitalmente: Windows SmartScreen potrebbe mostrare un avviso. **Non distribuire prima dei test.** Il programma usa solo librerie Python standard; PyInstaller serve unicamente a impacchettare l'EXE.

## Prossimi passi
1. Test avvio/chiusura su Windows 11, persistenza dati e backup.
2. Calendario mensile, editor foto e OCR Windows.
3. Ricerca integrata, filtri avanzati e modalità «Sorprendimi».
4. Definizione formato comune Android/Desktop con migrazione verificata.
5. Installer Windows e aggiornamenti in-place.
