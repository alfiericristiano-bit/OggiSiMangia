#!/usr/bin/env bash
set -euo pipefail
for name in ANDROID_KEYSTORE_BASE64 ANDROID_STORE_PASSWORD ANDROID_KEY_ALIAS ANDROID_KEY_PASSWORD ANDROID_CERT_SHA256; do
  if [ -z "${!name:-}" ]; then
    echo "::error::Secret mancante: $name. Nessun APK distribuibile viene pubblicato."
    exit 1
  fi
done
umask 077
printf '%s' "$ANDROID_KEYSTORE_BASE64" | base64 --decode > "$ANDROID_KEYSTORE_PATH"
keytool -exportcert -keystore "$ANDROID_KEYSTORE_PATH" -storepass:env ANDROID_STORE_PASSWORD -alias "$ANDROID_KEY_ALIAS" -file "$RUNNER_TEMP/signing-cert.der" > /dev/null
actual=$(sha256sum "$RUNNER_TEMP/signing-cert.der" | cut -d ' ' -f1)
expected=$(printf '%s' "$ANDROID_CERT_SHA256" | tr -d ': \r\n' | tr '[:upper:]' '[:lower:]')
if [ "$actual" != "$expected" ]; then
  echo '::error::Il certificato è cambiato: distribuzione bloccata per evitare aggiornamenti incompatibili.'
  exit 1
fi
echo "Certificato di firma verificato: $actual"
