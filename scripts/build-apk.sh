#!/usr/bin/env bash
# Build local do APK: testa o domínio (22 testes) e compila o debug.
# Precisa: JDK 17, Gradle 8.9+ (no PATH ou GRADLE=...) e Android SDK (ANDROID_HOME, padrão ~/Android/Sdk).
set -euo pipefail
cd "$(dirname "$0")/.."
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
GRADLE="${GRADLE:-$(command -v gradle || ls -d "$HOME"/.gradle/wrapper/dists/gradle-8.*-bin/*/gradle-8.*/bin/gradle 2>/dev/null | tail -1)}"
[ -x "$GRADLE" ] || { echo "Gradle 8.9+ não encontrado (defina GRADLE=/caminho/bin/gradle)"; exit 1; }

"$GRADLE" --no-daemon :dominio:test :app:assembleDebug

mkdir -p dist
cp app/build/outputs/apk/debug/app-debug.apk dist/financas-casa-kotlin.apk
echo "APK: $(pwd)/dist/financas-casa-kotlin.apk"
