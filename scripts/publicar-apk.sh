#!/usr/bin/env bash
# Publica dist/financas-casa-kotlin.apk numa Release do GitHub (gera antes com scripts/build-apk.sh).
# Uso: scripts/publicar-apk.sh <versão>   ex.: scripts/publicar-apk.sh v0.1.0
# O link fixo da última versão (para abrir no celular) fica no README.
set -euo pipefail
cd "$(dirname "$0")/.."
VERSAO="${1:?informe a versão, ex.: v0.1.0}"
APK="dist/financas-casa-kotlin.apk"
[ -f "$APK" ] || { echo "Falta $APK: rode scripts/build-apk.sh"; exit 1; }
gh release create "$VERSAO" "$APK" -R CelsoAlmeidaLF/stk-financas-kotlin \
  --title "$VERSAO" --notes "APK assinado com a chave de release da Systekna. Baixe e abra no celular: o Android pede para permitir a instalação de fontes desconhecidas." --latest
echo "Link: https://github.com/CelsoAlmeidaLF/stk-financas-kotlin/releases/latest/download/financas-casa-kotlin.apk"
