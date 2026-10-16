#!/usr/bin/env bash
# Build local do APK: testa o domínio (22 testes) e compila o release assinado.
# Precisa: JDK 17, Gradle 8.9+ (no PATH ou GRADLE=...) e Android SDK (ANDROID_HOME, padrão ~/Android/Sdk).
set -euo pipefail
cd "$(dirname "$0")/.."
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
GRADLE="${GRADLE:-$(command -v gradle || ls -d "$HOME"/.gradle/wrapper/dists/gradle-8.*-bin/*/gradle-8.*/bin/gradle 2>/dev/null | tail -1)}"
[ -x "$GRADLE" ] || { echo "Gradle 8.9+ não encontrado (defina GRADLE=/caminho/bin/gradle)"; exit 1; }

# Assinatura de release: chave própria fora do repositório (nunca commitar).
KEYSTORE="${STK_KEYSTORE:-$HOME/.android-keys/systekna-release.jks}"
KEYPASS="${STK_KEYPASS_FILE:-$HOME/.android-keys/systekna-release.pass}"
[ -f "$KEYSTORE" ] && [ -f "$KEYPASS" ] || { echo "Falta a chave de assinatura ($KEYSTORE e $KEYPASS)"; exit 1; }
BT="$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)"
assinar() { # $1 = APK sem assinatura, $2 = APK final
  mkdir -p dist
  "$BT/zipalign" -f -p 4 "$1" dist/alinhado.apk
  "$BT/apksigner" sign --ks "$KEYSTORE" --ks-key-alias systekna --ks-pass "file:$KEYPASS" --out "$2" dist/alinhado.apk
  rm -f dist/alinhado.apk "$2.idsig"
  "$BT/apksigner" verify "$2"
  echo "APK: $(pwd)/$2"
}

"$GRADLE" --no-daemon :dominio:test :app:assembleRelease

assinar app/build/outputs/apk/release/app-release-unsigned.apk dist/financas-casa-kotlin.apk
