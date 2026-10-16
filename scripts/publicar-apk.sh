#!/usr/bin/env bash
# Publica dist/financas-casa-kotlin.apk numa Release do GitHub (gera antes com scripts/build-apk.sh).
# Uso: scripts/publicar-apk.sh <versão>   ex.: scripts/publicar-apk.sh v0.1.0
# Também copia o APK para a página de download (repo stk-financas-android, em ../pagina-apk
# ou PAGINA_DIR) e publica a página: é de lá que o celular baixa.
set -euo pipefail
cd "$(dirname "$0")/.."
VERSAO="${1:?informe a versão, ex.: v0.1.0}"
APK="dist/financas-casa-kotlin.apk"
[ -f "$APK" ] || { echo "Falta $APK: rode scripts/build-apk.sh"; exit 1; }
gh release create "$VERSAO" "$APK" -R CelsoAlmeidaLF/stk-financas-kotlin \
  --title "$VERSAO" --notes "APK assinado com a chave de release da Systekna. Baixe e abra no celular: o Android pede para permitir a instalação de fontes desconhecidas." --latest
echo "Link: https://github.com/CelsoAlmeidaLF/stk-financas-kotlin/releases/latest/download/financas-casa-kotlin.apk"

PAGINA="${PAGINA_DIR:-../pagina-apk}"
if [ -d "$PAGINA/.git" ]; then
  cp "$APK" "$PAGINA/apk/financas-casa-kotlin.apk"
  python3 - "$PAGINA/apk/versoes.json" "financas-casa-kotlin.apk" "$VERSAO" "$(stat -c %s "$APK")" <<'PY'
import json, sys, datetime
arq, nome, versao, tamanho = sys.argv[1:]
try: v = json.load(open(arq))
except FileNotFoundError: v = {}
v[nome] = {"versao": versao, "data": datetime.date.today().isoformat(), "tamanho": int(tamanho)}
open(arq, "w").write(json.dumps(v, indent=2, ensure_ascii=False) + "\n")
PY
  git -C "$PAGINA" add apk && git -C "$PAGINA" commit -qm "financas-casa-kotlin $VERSAO" && git -C "$PAGINA" push -q
  echo "Página: https://celsoalmeidalf.github.io/stk-financas-android/"
else
  echo "Aviso: página de download não encontrada em $PAGINA (defina PAGINA_DIR); o APK ficou só na Release."
fi
