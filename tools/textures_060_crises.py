#!/usr/bin/env python3
"""Vilas 0.6 crises — ícones dos balões: fogo (incêndio), pão (fome) e vela (luto). Usa o balão vazio da 0.5 (textures_050.bubble)."""
from textures_050 import glyph, rgb


def balloons():
    red, orange, yellow = rgb("#c82818"), rgb("#f07818"), rgb("#f8d038")
    crust, crumb = rgb("#9a5a1e"), rgb("#e0a050")
    wax, wick, flame = rgb("#f0ecdc"), rgb("#3a2a1a"), rgb("#f8b828")
    # 🔥 chama: vermelho por fora, laranja e amarelo no meio
    glyph("fire", [
        (red, [(7, 3), (6, 4), (8, 4), (5, 5), (9, 5), (5, 6), (10, 6), (5, 7), (10, 7), (5, 8), (10, 8), (6, 9), (9, 9), (7, 9), (8, 9), (9, 4)]),
        (orange, [(7, 4), (6, 5), (7, 5), (8, 5), (6, 6), (9, 6), (6, 7), (9, 7), (6, 8), (9, 8)]),
        (yellow, [(7, 6), (8, 6), (7, 7), (8, 7), (7, 8), (8, 8)]),
    ])
    # 🍞 pão: côdea castanha com miolo claro e cortes
    glyph("hunger", [
        (crust, [(5, 5), (6, 4), (7, 4), (8, 4), (9, 4), (10, 5), (4, 6), (11, 6), (4, 7), (11, 7), (5, 8), (6, 8), (7, 8), (8, 8), (9, 8), (10, 8)]),
        (crumb, [(6, 5), (8, 5), (9, 5), (5, 6), (6, 6), (7, 6), (9, 6), (10, 6), (5, 7), (6, 7), (7, 7), (8, 7), (9, 7), (10, 7)]),
        (rgb("#7a4410"), [(7, 5), (8, 6)]),
    ])
    # 🕯 vela: cera branca, pavio escuro e a chama
    glyph("candle", [
        (flame, [(7, 2), (7, 3), (8, 3)]),
        (wick, [(7, 4)]),
        (wax, [(6, 5), (7, 5), (8, 5), (6, 6), (7, 6), (8, 6), (6, 7), (7, 7), (8, 7), (6, 8), (7, 8), (8, 8)]),
        (rgb("#c8c0a8"), [(9, 6), (9, 7), (9, 8), (5, 9), (6, 9), (7, 9), (8, 9), (9, 9), (10, 9)]),
    ])


if __name__ == "__main__":
    balloons()
    print("3 balões (fogo, pão, vela)")
