# Build APK firmato con la chiave ORIGINALE, Windows PowerShell 5.1+
# Esempio:
#   $env:ANDROID_KEYSTORE_PATH="C:\\PercorsoPrivato\\oggisimangia.jks"
#   $env:ANDROID_STORE_PASSWORD="..."
#   $env:ANDROID_KEY_ALIAS="..."
#   $env:ANDROID_KEY_PASSWORD="..."
#   $env:ANDROID_CERT_SHA256="impronta_SHA256_del_certificato"
#   .\\scripts\\build_windows_signed.ps1
# ATTENZIONE: non salvare credenziali nel repository, non condividere schermate con password.
$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
foreach ($name in @("ANDROID_KEYSTORE_PATH","ANDROID_STORE_PASSWORD","ANDROID_KEY_ALIAS","ANDROID_KEY_PASSWORD","ANDROID_CERT_SHA256")) {
    $value = [Environment]::GetEnvironmentVariable($name)
    if ([string]::IsNullOrWhiteSpace($value)) { throw "Variabile mancante: $name. Firma bloccata." }
}
if (-not (Test-Path -LiteralPath $env:ANDROID_KEYSTORE_PATH -PathType Leaf)) {
    throw "Keystore originale non trovato. Non creare una chiave nuova."
}
if (-not (Get-Command keytool -ErrorAction SilentlyContinue)) { throw "keytool non trovato: configura JDK 17 nel PATH." }
if (-not (Get-Command gradle -ErrorAction SilentlyContinue)) { throw "Gradle non trovato: installa Gradle 8.11.1 e configura il PATH." }
$certificateFile = Join-Path ([IO.Path]::GetTempPath()) ("oggisimangia-cert-" + [Guid]::NewGuid().ToString("N") + ".der")
try {
    & keytool -exportcert -keystore $env:ANDROID_KEYSTORE_PATH -storepass:env ANDROID_STORE_PASSWORD -alias $env:ANDROID_KEY_ALIAS -file $certificateFile
    if ($LASTEXITCODE -ne 0) { throw "Impossibile verificare il certificato originale." }
    $actual = (Get-FileHash -LiteralPath $certificateFile -Algorithm SHA256).Hash.ToLowerInvariant()
    $expected = ($env:ANDROID_CERT_SHA256 -replace '[^a-fA-F0-9]', '').ToLowerInvariant()
    if ($actual -ne $expected) { throw "Impronta certificato diversa: build interrotta per sicurezza." }
    Write-Host "Firma originale verificata."
    & gradle --no-daemon :app:testDebugUnitTest :app:lintDebug
    if ($LASTEXITCODE -ne 0) { throw "Test o lint falliti. APK non generato." }
    & gradle --no-daemon :app:assembleRelease
    if ($LASTEXITCODE -ne 0) { throw "Compilazione release fallita." }
    $apk = "app/build/outputs/apk/release/app-release.apk"
    if (-not (Test-Path -LiteralPath $apk)) { throw "APK non trovato." }
    Write-Host "APK release firmato: $apk"
    Write-Host "Prima di installarlo, verifica versionCode, backup e compatibilita con la versione esistente."
} finally {
    Remove-Item -LiteralPath $certificateFile -Force -ErrorAction SilentlyContinue
}
