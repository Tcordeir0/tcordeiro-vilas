#!/usr/bin/env python3
"""Vilas 0.6 eleições — textura do broche «Eu votei» (item/i_voted.png, 16×16, arte original e determinística, estilo vanilla):
uma roseta azul com o centro dourado e um visto, e duas fitas vermelhas por baixo. Contorno escuro como nos itens do jogo.

Uso: python3 tools/textures_060.py
"""
from PIL import Image

from textures import TEX, rgb, save, shade

OUT = rgb("#1b1b2a")
BLUE, BLUE_L = rgb("#2f5fb8"), rgb("#5b8be0")
RED, RED_D = rgb("#c23a2e"), rgb("#8a241c")
GOLD, GOLD_L, GOLD_D = rgb("#e8b828"), rgb("#f6dc6a"), rgb("#a87a10")
WHITE = rgb("#f4f1e8")


# o desenho, pixel a pixel (16×16): . vazio, O contorno, L/B/D azul claro/médio/escuro, Y/G/g dourado, W visto, R/r fita
ART = [
    "................",
    ".....OOOOOO.....",
    "...OOLLLLLLOO...",
    "..OLLBBBBBBBDO..",
    ".OLLBBOOOOBBBDO.",
    ".OLBBOYYYYWOBDO.",
    ".OLBOYYYYWGGOBDO",
    ".OLBOYWYWGGgOBDO",
    ".OLBOYGWGGGgOBDO",
    ".OLBBOGGGggOBBDO",
    "..OBBBOOOOOBBDO.",
    "..ORrODDDDDORrO.",
    "..ORrO.OOO.ORrO.",
    "..ORrO.....ORrO.",
    "..ORrO.....ORrO.",
    "..O.OO.....OO.O.",
]
COLORS = {"O": OUT, "L": BLUE_L, "B": BLUE, "D": shade(BLUE, 0.7), "Y": GOLD_L, "G": GOLD, "g": GOLD_D, "W": WHITE, "R": RED, "r": RED_D}


def i_voted():
    assert len(ART) == 16 and all(len(r) == 16 for r in ART), [len(r) for r in ART]
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(ART):
        for x, ch in enumerate(row):
            if ch != ".":
                px[x, y] = COLORS[ch][:3] + (255,)
    save(img, "item/i_voted.png")


def main():
    i_voted()
    print("textura 0.6 (broche «Eu votei») gerada em", TEX)


if __name__ == "__main__":
    main()
