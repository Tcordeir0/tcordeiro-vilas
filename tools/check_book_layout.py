"""Verifica a paginação dos livros-guia com as larguras reais da fonte do Minecraft.
Regras (iguais ao ecrã do livro: área 114x128, título 14, imagem 66, receita 58, linha 9):
  - o título cabe numa linha;
  - numa página com receita/imagem, todo o texto cabe ANTES delas (senão o texto salta para a página seguinte e
    fica depois da receita, o que confunde);
  - numa página só de texto, o texto cabe na página (128 px: título + 12 linhas).
Uso: python3 check_book_layout.py <client.jar> <ficheiro .java com PAGES> <lang.json> <prefixo das chaves>
Sai com código 1 se houver problemas.
"""
import io
import json
import re
import sys
import zipfile

from PIL import Image

JAR, JAVA, LANG, PREFIX = sys.argv[1:5]
TW, TH, TITLE, IMG, RECIPE, LINE = 114, 128, 14, 66, 58, 9

# larguras dos glifos ASCII (ascii.png: 16x16 células de 8x8) — como o jogo calcula: última coluna com píxel + 1, +1 de espaço
with zipfile.ZipFile(JAR) as z:
    font = json.load(z.open("assets/minecraft/font/include/default.json"))
    prov = next(p for p in font["providers"] if p.get("file", "").endswith("ascii.png"))
    img = Image.open(io.BytesIO(z.read("assets/minecraft/textures/font/ascii.png"))).convert("RGBA")
cw, ch = img.width // 16, img.height // 16
widths = {}
for row, chars in enumerate(prov["chars"]):
    for col, c in enumerate(chars):
        last = -1
        for x in range(cw):
            if any(img.getpixel((col * cw + x, row * ch + y))[3] > 0 for y in range(ch)):
                last = x
        widths[c] = (last + 2) if last >= 0 else 4
widths[" "] = 4


def w(text, bold=False):
    return sum(widths.get(c, 6) + (1 if bold else 0) for c in text)


def wrap(text):
    lines = []
    for para in text.split("\n"):
        cur = ""
        for word in para.split(" "):
            cand = word if not cur else cur + " " + word
            if w(cand) <= TW:
                cur = cand
            else:
                if cur:
                    lines.append(cur)
                cur = word
        lines.append(cur)
    return lines


src = open(JAVA).read()
pages = re.findall(r'new Page\("([a-z0-9_]+)",\s*(null|"[a-z0-9_]+"),\s*List\.of\(([^)]*)\)\)', src)
lang = json.load(open(LANG))
problems = 0
for key, image, recipes in pages:
    n_rec = len([r for r in recipes.split(",") if r.strip()])
    title = lang.get(f"{PREFIX}.{key}.title")
    text = lang.get(f"{PREFIX}.{key}.text")
    if title is None:
        continue  # página especial (ex.: tabela de preços)
    if w(title, bold=True) > TW:
        print(f"  ✗ {key}: título largo demais ({w(title, True)}px > {TW})")
        problems += 1
    lines = wrap(text) if text else []
    free = TH - TITLE - (IMG if image != "null" else 0) - n_rec * RECIPE
    fit = max(0, free // LINE)
    if (n_rec or image != "null") and len(lines) > fit:
        print(f"  ✗ {key}: {len(lines)} linhas mas só cabem {fit} antes da receita/imagem")
        problems += 1
    elif len(lines) > fit:
        print(f"  ✗ {key}: {len(lines)} linhas mas a página só leva {fit}")
        problems += 1
print(f"{LANG.split('/')[-1]}: {len(pages)} páginas, {problems} problemas")
sys.exit(1 if problems else 0)
