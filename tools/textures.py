#!/usr/bin/env python3
"""Texturas das Vilas do Tcordeiro, desenhadas por código (PIL) — arte original, sem copiar píxeis de outros mods nem do jogo.

  - blocos 16×16: Mesa do Prefeito, Forno do Padeiro, Banca de Flores, Balcão da Taverna, Bancada do Apicultor;
  - camadas de profissão dos aldeões 64×64 (mesmo mapa UV do modelo do aldeão vanilla): Prefeito (cartola, faixa e colar),
    Padeiro, Florista, Taberneiro, Apicultor — também para o aldeão zumbi;
  - ícone do mod 128×128.

Uso: python3 tools/textures.py   (determinístico: a mesma semente dá sempre os mesmos píxeis)
"""
import json
import os
import random

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX = os.path.join(ROOT, "src", "main", "resources", "assets", "tvilas", "textures")


def save(img, rel):
    path = os.path.join(TEX, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (c[3] if len(c) > 3 else 255,)


def noisy(size, base, amp, seed):
    rnd = random.Random(seed)
    img = Image.new("RGBA", size, base)
    px = img.load()
    for y in range(size[1]):
        for x in range(size[0]):
            px[x, y] = shade(base, 1 + rnd.uniform(-amp, amp))
    return img


def planks(seed, base):
    """Tábuas horizontais (4 px cada) com juntas escuras e veios."""
    img = noisy((16, 16), base, 0.06, seed)
    d = ImageDraw.Draw(img)
    rnd = random.Random(seed + 1)
    for row in range(4):
        y = row * 4 + 3
        d.line([(0, y), (15, y)], fill=shade(base, 0.62))
        cut = rnd.randint(2, 13)
        d.point((cut, y - 1), fill=shade(base, 0.7))
        d.point((cut, y - 2), fill=shade(base, 0.7))
    return img


def frame(img, color):
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 15, 15], outline=color)
    return img


# ------------------------------------------------------------------ blocos

WOOD = rgb("#8a5a32")
DARK_WOOD = rgb("#5a3a20")
BRICK = rgb("#a4523a")
GOLD = rgb("#e8c040")


def mayor_desk():
    top = planks(10, DARK_WOOD)
    d = ImageDraw.Draw(top)
    d.rectangle([3, 4, 9, 11], fill=rgb("#efe6cf"))          # papel
    for y in (6, 8, 10):
        d.line([(4, y), (8, y)], fill=rgb("#8c8478"))           # linhas escritas
    d.rectangle([11, 5, 12, 6], fill=rgb("#30303a"))         # tinteiro
    d.line([(12, 4), (14, 2)], fill=rgb("#f4f4f4"))           # pena
    d.point((10, 11), fill=rgb("#c03030"))                     # selo de cera
    frame(top, shade(DARK_WOOD, 0.55))
    save(top, "block/mayor_desk_top.png")
    side = planks(11, WOOD)
    d = ImageDraw.Draw(side)
    d.rectangle([0, 0, 15, 3], fill=shade(DARK_WOOD, 1.0))
    d.rectangle([1, 4, 2, 15], fill=shade(DARK_WOOD, 0.9))
    d.rectangle([13, 4, 14, 15], fill=shade(DARK_WOOD, 0.9))
    save(side, "block/mayor_desk_side.png")
    front = side.copy()
    d = ImageDraw.Draw(front)
    for y0 in (5, 10):                                         # duas gavetas com puxador dourado
        d.rectangle([4, y0, 11, y0 + 3], outline=shade(DARK_WOOD, 0.6), fill=shade(WOOD, 1.08))
        d.point((7, y0 + 1), fill=GOLD)
        d.point((8, y0 + 1), fill=GOLD)
    save(front, "block/mayor_desk_front.png")


def baker_oven():
    def bricks(seed):
        img = noisy((16, 16), BRICK, 0.08, seed)
        d = ImageDraw.Draw(img)
        mortar = rgb("#c9b8a0")
        for row in range(4):
            y = row * 4 + 3
            d.line([(0, y), (15, y)], fill=mortar)
            off = 0 if row % 2 == 0 else 4
            for x in range(off, 16, 8):
                d.line([(x, y - 3), (x, y - 1)], fill=mortar)
        return img
    side = bricks(20)
    save(side, "block/baker_oven_side.png")
    top = bricks(21)
    d = ImageDraw.Draw(top)
    d.rectangle([6, 6, 9, 9], fill=rgb("#2a2420"))             # chaminé
    save(top, "block/baker_oven_top.png")
    front = bricks(22)
    d = ImageDraw.Draw(front)
    d.rectangle([3, 7, 12, 14], fill=rgb("#1e1612"))           # boca do forno
    d.rectangle([4, 6, 11, 6], fill=rgb("#1e1612"))
    for x, c in ((5, "#ff9a2a"), (7, "#ffd050"), (9, "#ff9a2a"), (10, "#ff6a1a")):
        d.line([(x, 14), (x, 12)], fill=rgb(c))                 # brasas
    d.rectangle([6, 10, 9, 11], fill=rgb("#d8a860"))           # pão
    save(front, "block/baker_oven_front.png")


def flower_stall():
    top = planks(30, WOOD)
    d = ImageDraw.Draw(top)
    colors = ["#e04050", "#f0d040", "#8050e0", "#f080c0", "#40a0f0", "#f07020"]
    rnd = random.Random(31)
    for i, (x, y) in enumerate([(3, 3), (8, 3), (12, 4), (4, 8), (9, 9), (12, 12), (3, 12), (7, 13)]):
        c = rgb(colors[i % len(colors)])
        d.point((x, y), fill=c)
        d.point((x + 1, y), fill=c)
        d.point((x, y + 1), fill=c)
        d.point((x + 1, y + 1), fill=shade(c, 0.8))
        d.point((x + rnd.choice((-1, 2)), y + 1), fill=rgb("#3c8a30"))
    frame(top, shade(WOOD, 0.6))
    save(top, "block/flower_stall_top.png")
    side = planks(32, WOOD)
    d = ImageDraw.Draw(side)
    for x in range(0, 16, 4):                                   # toldo às riscas no topo
        d.rectangle([x, 0, x + 1, 3], fill=rgb("#e04050"))
        d.rectangle([x + 2, 0, x + 3, 3], fill=rgb("#f4f0e8"))
    save(side, "block/flower_stall_side.png")


def tavern_counter():
    top = planks(40, DARK_WOOD)
    d = ImageDraw.Draw(top)
    d.ellipse([3, 3, 7, 7], outline=rgb("#c8c8c8"), fill=rgb("#d8a030"))   # caneca vista de cima
    d.ellipse([9, 8, 13, 12], outline=shade(DARK_WOOD, 0.5))              # marca de copo
    save(top, "block/tavern_counter_top.png")
    side = planks(41, WOOD)
    d = ImageDraw.Draw(side)
    d.rectangle([0, 0, 15, 2], fill=DARK_WOOD)
    save(side, "block/tavern_counter_side.png")
    front = side.copy()
    d = ImageDraw.Draw(front)
    d.ellipse([3, 5, 12, 14], fill=shade(WOOD, 0.85), outline=rgb("#6a6a6a"))  # tampo do barril
    d.line([(3, 9), (12, 9)], fill=rgb("#6a6a6a"))
    d.rectangle([7, 8, 8, 11], fill=rgb("#b08030"))                           # torneira
    d.point((7, 12), fill=rgb("#e0b040"))
    save(front, "block/tavern_counter_front.png")


def apiary_bench():
    top = noisy((16, 16), rgb("#e8a820"), 0.05, 50)
    d = ImageDraw.Draw(top)
    hexc = rgb("#b07010")
    for y in range(0, 16, 4):                                   # favo hexagonal simplificado
        off = 0 if (y // 4) % 2 == 0 else 2
        for x in range(off, 16, 4):
            d.line([(x, y), (x + 1, y)], fill=hexc)
            d.point((x + 2, y + 1), fill=hexc)
            d.point((x - 1, y + 1), fill=hexc)
    frame(top, rgb("#8a5a10"))
    save(top, "block/apiary_bench_top.png")
    side = planks(51, rgb("#b08850"))
    d = ImageDraw.Draw(side)
    d.rectangle([0, 0, 15, 3], fill=rgb("#e8a820"))
    d.line([(0, 3), (15, 3)], fill=rgb("#8a5a10"))
    d.rectangle([5, 8, 10, 12], fill=rgb("#f0c040"), outline=rgb("#8a5a10"))   # frasco de mel
    d.point((7, 7), fill=rgb("#d8d8d8"))
    d.point((8, 7), fill=rgb("#d8d8d8"))
    save(side, "block/apiary_bench_side.png")


# ------------------------------------------------------------------ aldeões (64×64, UV do VillagerModel)

def rect(d, x0, y0, x1, y1, c):
    """Retângulo de píxeis [x0, x1) × [y0, y1)."""
    d.rectangle([x0, y0, x1 - 1, y1 - 1], fill=c)


def hat_cap(d, color, band=None, band_rows=(14, 15)):
    """Chapéu no «hat layer» (32,0): topo e as 4 faces na parte de cima da cabeça; faixa opcional."""
    rect(d, 40, 0, 48, 8, color)                                # topo
    for x0 in (32, 40, 48, 56):                                 # 4 faces (8 px cada), linhas 8..15
        rect(d, x0, 8, x0 + 8, 8 + (band_rows[1] - 8 + 1), color)
        if band:
            rect(d, x0, band_rows[0], x0 + 8, band_rows[1] + 1, band)


def brim(d, color, inset):
    """Aba do chapéu (plano 16×16 em (30,47), face de cima em (31,48)): quadrado com {inset} píxeis de margem."""
    rect(d, 31 + inset, 48 + inset, 47 - inset, 64 - inset, color)


def jacket_front(d, fn):
    """Pinta a frente do casaco (6,44)-(14,64) e a frente do corpo (22,26)-(30,38) com fn(x, y, w, h) → cor ou None."""
    for (x0, y0, w, h) in ((6, 44, 8, 20), (22, 26, 8, 12)):
        for y in range(h):
            for x in range(w):
                c = fn(x, y, w, h)
                if c:
                    d.point((x0 + x, y0 + y), fill=c)


def villager(name, draw, hat):
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    draw(d)
    for kind in ("villager", "zombie_villager"):
        save(img, f"entity/{kind}/profession/{name}.png")
        meta = os.path.join(TEX, f"entity/{kind}/profession/{name}.png.mcmeta")
        with open(meta, "w") as f:
            json.dump({"villager": {"hat": hat}}, f, indent=2)
            f.write("\n")


def mayor(d):
    black, band, gold, red = rgb("#1c1c22"), rgb("#7a1a24"), GOLD, rgb("#b8202c")
    hat_cap(d, black, band, band_rows=(14, 15))                 # cartola preta com fita bordô
    brim(d, black, 2)                                           # aba
    # faixa de prefeito (diagonal, vermelha com orla dourada) e colar com medalha
    def sash(x, y, w, h):
        k = (x - y * w / h * 0.9)
        if -1.2 <= k <= 1.2:
            return red
        if -2.0 <= k <= 2.0:
            return gold
        return None
    jacket_front(d, sash)
    for (x, y) in ((6, 44), (7, 45), (8, 46), (9, 47), (10, 47), (11, 46), (12, 45), (13, 44)):
        d.point((x, y), fill=gold)
    rect(d, 9, 48, 11, 50, gold)
    d.point((9, 48), fill=shade(gold, 0.7))
    # mangas com punho dourado
    for x0 in (44, 40):
        rect(d, x0, 28 if x0 == 44 else 42, x0 + 16 if x0 == 44 else x0 + 24, 30 if x0 == 44 else 44, gold)


def baker(d):
    white, cream = rgb("#f6f6f0"), rgb("#e0dccf")
    hat_cap(d, white, cream, band_rows=(15, 15))                # chapéu de cozinheiro
    rect(d, 40, 0, 48, 8, white)
    jacket_front(d, lambda x, y, w, h: white if h == 20 and y >= 4 else None)   # avental branco
    for x in range(6, 14):
        d.point((x, 47), fill=cream)


def florist(d):
    green, dark = rgb("#4a9a3a"), rgb("#2e6a26")
    flowers = [rgb("#e04050"), rgb("#f0d040"), rgb("#8050e0"), rgb("#f080c0")]
    for i, x0 in enumerate((32, 40, 48, 56)):                   # coroa de flores na testa
        for x in range(x0, x0 + 8, 2):
            d.point((x, 9), fill=flowers[(x // 2 + i) % 4])
            d.point((x + 1, 9), fill=dark)
    jacket_front(d, lambda x, y, w, h: (green if (x + y) % 5 else dark) if h == 20 and y >= 5 else None)  # avental verde


def innkeeper(d):
    vest, white, brown = rgb("#6a2a1e"), rgb("#efe8da"), rgb("#7a5230")
    def apron(x, y, w, h):
        if h == 12:
            return vest if x in (0, 1, 6, 7) else white       # colete por cima da camisa
        if y >= 8:
            return brown if (y - 8) % 6 else shade(brown, 0.8)  # avental de couro
        return vest if x in (0, 1, 6, 7) else None
    jacket_front(d, apron)
    rect(d, 0, 44, 6, 52, vest)                                # lados do colete
    rect(d, 14, 44, 20, 52, vest)


def beekeeper(d):
    veil, mesh, yellow, black = rgb("#efe6c8"), rgb("#bdb49a"), rgb("#f0c020"), rgb("#2a2a2a")
    hat_cap(d, veil, None)
    for x0 in (32, 40, 48, 56):                                 # véu em rede à frente da cara
        for y in range(10, 18):
            for x in range(x0, x0 + 8):
                if (x + y) % 2 == 0:
                    d.point((x, y), fill=mesh)
    brim(d, veil, 1)
    jacket_front(d, lambda x, y, w, h: (yellow if (y // 2) % 2 == 0 else black) if h == 20 and 2 <= y <= 9 else None)


# ------------------------------------------------------------------ ícone

def icon():
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([4, 4, 123, 123], 18, fill=rgb("#6fb1e0"))
    d.rectangle([4, 92, 123, 123], fill=rgb("#5a9a3a"))
    d.rounded_rectangle([4, 4, 123, 123], 18, outline=rgb("#2c4a60"), width=4)
    d.rectangle([28, 54, 100, 98], fill=rgb("#d8c8a0"), outline=rgb("#5a3a20"), width=3)   # prefeitura
    d.polygon([(20, 56), (64, 22), (108, 56)], fill=rgb("#a4523a"), outline=rgb("#5a2a1a"))  # telhado
    d.rectangle([56, 72, 72, 98], fill=rgb("#6a4020"))                                     # porta
    for x in (36, 82):
        d.rectangle([x, 64, x + 10, 76], fill=rgb("#9fd0f0"), outline=rgb("#5a3a20"))
    d.ellipse([56, 36, 72, 52], fill=GOLD, outline=rgb("#8a6a10"), width=2)                 # sino
    d.line([(64, 22), (64, 8)], fill=rgb("#3a3a3a"), width=2)                               # mastro
    d.polygon([(64, 8), (82, 12), (64, 16)], fill=rgb("#c03030"))                           # bandeira
    img.save(os.path.join(ROOT, "src", "main", "resources", "assets", "tvilas", "icon.png"))


def main():
    mayor_desk()
    baker_oven()
    flower_stall()
    tavern_counter()
    apiary_bench()
    villager("mayor", mayor, "full")
    villager("baker", baker, "full")
    villager("florist", florist, "partial")
    villager("innkeeper", innkeeper, "none")
    villager("beekeeper", beekeeper, "full")
    icon()
    print("texturas geradas em", TEX)


if __name__ == "__main__":
    main()
