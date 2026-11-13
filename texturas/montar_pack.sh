#!/bin/sh
# Monta o pacote de recursos «tvilas_animacoes.zip» (gestos dos aldeões por cima do Fresh Animations).
#   - o que é 100 % nosso está aqui: tvilas_animacoes/pack.mcmeta e tvilas_animacoes/gestos.json;
#   - as animações base são do Fresh Animations (villager_animations.jpm) e ficam FORA do Git, em
#     ~/Cods/assets_pessoais/texturas/tvilas_animacoes/fa/. O pacote leva esse .jpm com as nossas somas no FIM da lista (assim correm
#     depois das do FA, que senão as apagavam); o villager.jem continua a ser o do FA ou do FA+Details, que o usa.
# Uso: sh texturas/montar_pack.sh   → texturas/tvilas_animacoes.zip (no .gitignore; vai no zip dos amigos, junto com o FA)
set -e
DIR=$(cd "$(dirname "$0")" && pwd)
FA="${FA_BASE:-$HOME/Cods/assets_pessoais/texturas/tvilas_animacoes/fa}"
[ -f "$FA/villager_animations.jpm" ] || { echo "falta $FA/villager_animations.jpm (cópia do do Fresh Animations)"; exit 1; }
TMP=$(mktemp -d)
mkdir -p "$TMP/assets/minecraft/optifine/cem"
cp "$DIR/tvilas_animacoes/pack.mcmeta" "$TMP/"
python3 - "$FA/villager_animations.jpm" "$DIR/tvilas_animacoes/gestos.json" "$TMP/assets/minecraft/optifine/cem/villager_animations.jpm" <<'PY'
import json, sys
jpm = json.load(open(sys.argv[1]))
g = {k: v for k, v in json.load(open(sys.argv[2])).items() if not k.startswith("_")}
jpm.setdefault("animations", []).append(g)  # no FIM: correm depois das do FA
json.dump(jpm, open(sys.argv[3], "w"), separators=(",", ":"))
PY
rm -f "$DIR/tvilas_animacoes.zip"
(cd "$TMP" && zip -q -r -X "$DIR/tvilas_animacoes.zip" pack.mcmeta assets)
rm -rf "$TMP"
echo "pacote: $DIR/tvilas_animacoes.zip ($(wc -c < "$DIR/tvilas_animacoes.zip") bytes)"
