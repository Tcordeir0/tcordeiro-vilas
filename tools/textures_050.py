#!/usr/bin/env python3
"""Vilas 0.5.0 — texturas novas (PIL, arte original, determinística), usadas pelos modelos do Blender (tools/blender_050.py):

  - Balcão dos Correios (frente com a ranhura e gavetas, lado azul dos correios, prateleira de escaninhos com cartas);
  - Bolsa do Carteiro (couro com costuras e a aba com o envelope amarelo);
  - farda do Carteiro (boné azul com pala, casaco azul, a correia da bolsa a tiracolo) — também para o aldeão zumbi;
  - 4 balões novos no estilo dos da 0.4 (carta, peixe, voto, esconde-esconde).

Uso: python3 tools/textures_050.py
"""
import os

from PIL import Image, ImageDraw

from textures import TEX, frame, noisy, planks, rect, rgb, save, shade, hat_cap, brim, jacket_front, villager

BLUE, YELLOW, LEATHER = rgb("#2c4f8f"), rgb("#e8b828"), rgb("#7a4a26")


def post_counter():
    side = noisy((16, 16), BLUE, 0.05, 501)
    d = ImageDraw.Draw(side)
    rect(d, 0, 6, 16, 8, YELLOW)                       # a faixa amarela dos correios
    for x in (0, 15):
        d.line([(x, 0), (x, 15)], fill=shade(BLUE, 0.7))
    d.line([(0, 15), (15, 15)], fill=shade(BLUE, 0.6))
    save(side, "block/post_counter_side.png")
    front = side.copy()
    d = ImageDraw.Draw(front)
    rect(d, 3, 2, 13, 4, rgb("#1a1a1a"))               # ranhura das cartas
    d.line([(3, 4), (12, 4)], fill=shade(YELLOW, 0.8))
    for y0 in (9, 12):                                 # duas gavetas com puxador
        rect(d, 2, y0, 14, y0 + 3, shade(BLUE, 0.85))
        d.rectangle([2, y0, 13, y0 + 2], outline=shade(BLUE, 0.6))
        d.point((7, y0 + 1), fill=YELLOW)
        d.point((8, y0 + 1), fill=YELLOW)
    save(front, "block/post_counter_front.png")
    shelf = planks(502, rgb("#6b4a2e"))
    d = ImageDraw.Draw(shelf)
    paper = [rgb("#f2efe6"), rgb("#e6dcc4"), rgb("#f6e9b0"), rgb("#dfe8f2")]
    for i, (x, y) in enumerate([(x, y) for y in (1, 6, 11) for x in (1, 6, 11)]):
        rect(d, x, y, x + 4, y + 4, rgb("#2a1c10"))     # escaninho
        if (i * 7) % 3:                                # algumas com carta
            rect(d, x, y + 1, x + 4, y + 4, paper[i % 4])
            d.point((x + 1, y + 2), fill=rgb("#b02020"))  # selo
    save(shelf, "block/post_counter_shelf.png")


def mail_bag():
    img = noisy((16, 16), LEATHER, 0.07, 503)
    d = ImageDraw.Draw(img)
    for i in range(0, 16, 2):                          # costuras
        d.point((i, 1), fill=shade(LEATHER, 1.35))
        d.point((i, 14), fill=shade(LEATHER, 1.35))
    save(img, "item/mail_bag.png")
    flap = noisy((16, 16), shade(LEATHER, 0.82), 0.05, 504)
    d = ImageDraw.Draw(flap)
    rect(d, 4, 4, 12, 10, rgb("#f0e6c8"))              # o envelope
    d.line([(4, 4), (8, 7)], fill=rgb("#b8a882"))
    d.line([(11, 4), (8, 7)], fill=rgb("#b8a882"))
    rect(d, 7, 8, 9, 10, YELLOW)                       # selo amarelo dos correios
    frame(flap, shade(LEATHER, 0.6))
    save(flap, "item/mail_bag_flap.png")


def postman(d):
    blue, dark, strap = BLUE, shade(BLUE, 0.7), rgb("#5a3418")
    hat_cap(d, blue, YELLOW, band_rows=(14, 15))       # boné dos correios com fita amarela
    brim(d, dark, 3)                                   # pala
    def coat(x, y, w, h):
        k = x - y * w / h                              # correia da bolsa a tiracolo
        if -0.9 <= k <= 0.9:
            return strap
        if h == 20 and y >= 1:
            return blue
        if h == 12 and y >= 0:
            return blue
        return None
    jacket_front(d, coat)
    for x in range(6, 14):
        d.point((x, 45), fill=YELLOW)                  # gola com orla amarela


# ------------------------------------------------------------------ balões (como os da 0.4: balão branco, contorno escuro)

def bubble():
    base = Image.open(os.path.join(TEX, "balloon", "talk.png")).convert("RGBA")
    px = base.load()
    # o miolo do balão vazio (só o fundo): tudo o que não for contorno/transparente fica da cor do fundo
    bg = px[7, 3]
    for y in range(2, 11):
        for x in range(2, 14):
            if px[x, y][3] > 128 and sum(px[x, y][:3]) > 200:
                px[x, y] = bg
    return base


def glyph(name, pixels):
    img = bubble()
    d = ImageDraw.Draw(img)
    for c, pts in pixels:
        for (x, y) in pts:
            d.point((x, y), fill=c)
    save(img, f"balloon/{name}.png")


def balloons():
    ink, red, blue, gold, green = rgb("#3a2a1a"), rgb("#c02828"), rgb("#2c66c8"), rgb("#e0a818"), rgb("#2f9a3a")
    paper = rgb("#f4ead0")
    # ✉ envelope: papel com contorno fino, a dobra em V até ao meio e o selo vermelho
    env = [(x, y) for y in range(4, 11) for x in range(3, 13)]
    edge = [(x, 4) for x in range(3, 13)] + [(x, 10) for x in range(3, 13)] + [(3, y) for y in range(4, 11)] + [(12, y) for y in range(4, 11)]
    fold = [(4, 5), (5, 6), (6, 7), (7, 8), (8, 8), (9, 7), (10, 6), (11, 5)]
    glyph("mail", [(paper, env), (rgb("#8a7a5a"), edge), (rgb("#8a7a5a"), fold), (red, [(10, 8), (11, 8), (10, 9), (11, 9)])])
    # 🐟 peixe
    body = [(x, y) for x in range(4, 11) for y in range(5, 9) if not ((x in (4, 10)) and y in (5, 8))]
    tail = [(11, 5), (11, 8), (12, 4), (12, 5), (12, 8), (12, 9), (11, 6), (11, 7)]
    glyph("fish", [(blue, body + tail), (rgb("#9cc4f0"), [(5, 7), (6, 7), (7, 7), (8, 7)]), (ink, [(6, 6)])])
    # 🗳 urna com o boletim
    box = [(x, y) for x in range(4, 12) for y in range(6, 10)]
    glyph("vote", [(rgb("#8a5a2a"), box), (ink, [(x, 6) for x in range(5, 11)]), (paper, [(x, y) for x in range(6, 10) for y in range(3, 6)]),
                   (green, [(7, 4), (8, 3)])])
    # 🙈 esconde-esconde: olhos espreitando por cima de um muro
    wall = [(x, y) for x in range(3, 13) for y in range(7, 10)]
    glyph("hide", [(rgb("#7a7a7a"), wall), (ink, [(x, 7) for x in range(3, 13)]), (rgb("#c89070"), [(x, y) for x in range(5, 11) for y in range(4, 7)]),
                   (ink, [(6, 5), (9, 5)]), (gold, [(5, 3), (10, 3)])])


def main():
    post_counter()
    mail_bag()
    villager("postman", postman, "full")
    balloons()
    print("texturas 0.5.0 geradas em", TEX)


if __name__ == "__main__":
    main()
