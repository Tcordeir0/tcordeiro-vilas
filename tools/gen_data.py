#!/usr/bin/env python3
"""Gera os dados das Vilas do Tcordeiro (tvilas):

  - modelos, blockstates, definições de itens, loot tables, receitas (+ conquistas que as desbloqueiam), tags;
  - trocas dos vendedores novos (data/tvilas/villager_trade/... e data/tvilas/trade_set/<profissão>/level_N.json, o formato
    data-driven do 26.3);
  - a tag minecraft:acquirable_job_site com os postos de trabalho novos;
  - traduções (pt_br, pt_pt, en_us) — verifica que nenhum nome repete um nome do jogo;
  - o guia: traduções guide.tvilas.*, o livro escrito (receita tvilas:guide) e VilasGuide.java (separador «Guias» do Receitário).

Os itens usados nas trocas e missões são verificados contra o jar 26.3 (não se inventa nenhum id).
Uso: python3 tools/gen_data.py   (depois: tools/textures.py e tools/gen_structures.py)
"""
import json
import os
import re
import sys
import zipfile

HOME = os.path.expanduser("~")
MC = os.path.join(HOME, "Library", "Application Support", "horizon-launcher", "minecraft")
JAR = os.path.join(MC, "versions", "26.3", "26.3.jar")
ASSET_INDEX = os.path.join(MC, "assets", "indexes", "34.json")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
JAVA = os.path.join(ROOT, "src", "main", "java", "com", "tcordeiro", "vilas")
MOD = "tvilas"

written = set()


def cond(data, mod):
    """Só carrega com o mod instalado (condições de recurso da Fabric API: receitas, conquistas, trocas, loot)."""
    if mod:
        data = {"fabric:load_conditions": [{"condition": "fabric:all_mods_loaded", "values": [mod]}]} | data
    return data


def write(rel, data, mod=None):
    data = cond(data, mod)
    path = os.path.join(RES, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")
    written.add(rel)


zjar = zipfile.ZipFile(JAR)


def jar_json(path):
    return json.load(zjar.open(path))


def item_exists(i):
    ns, path = i.split(":")
    if ns == MOD:
        return True
    if ns != "minecraft":
        return mod_item_exists(i)
    try:
        zjar.getinfo(f"assets/minecraft/items/{path}.json")
        return True
    except KeyError:
        return False


SIBLINGS = {"tindustrial": "tcordeiro-industrial", "tforja": "tcordeiro-forja", "tmoveis": "tcordeiro-moveis", "cubcoin": "cubcoin"}
_MOD_LANG = {}


def mod_item_exists(i):
    """Um item de um mod irmão existe? (pela definição de item em assets/<mod>/items do código dele)"""
    ns, path = i.split(":")
    repo = SIBLINGS.get(ns)
    assert repo, f"mod desconhecido: {i}"
    return os.path.exists(os.path.join(HOME, "Cods", repo, "src", "main", "resources", "assets", ns, "items", path + ".json"))


def asset_lang(code):
    idx = json.load(open(ASSET_INDEX))["objects"]
    h = idx[f"minecraft/lang/{code}.json"]["hash"]
    return json.load(open(os.path.join(MC, "assets", "objects", h[:2], h)))


LANG = {"en": jar_json("assets/minecraft/lang/en_us.json"), "br": asset_lang("pt_br"), "pt": asset_lang("pt_pt")}
TR = {"br": {}, "pt": {}, "en": {}}


def tr(key, br, pt, en):
    TR["br"][key], TR["pt"][key], TR["en"][key] = br, pt, en


# ------------------------------------------------------------------ blocos

# id: (pt_br, pt_pt, en, modelo, ferramenta)
BLOCKS = {
    "mayor_desk": ("Mesa do Prefeito", "Mesa da Câmara", "Mayor's Desk", "desk", "axe"),
    "baker_oven": ("Forno do Padeiro", "Forno do Padeiro", "Baker's Oven", "orientable", "pickaxe"),
    "flower_stall": ("Banca de Flores", "Banca de Flores", "Flower Stall", "stall", "axe"),
    "tavern_counter": ("Balcão da Taverna", "Balcão da Taberna", "Tavern Counter", "orientable", "axe"),
    "apiary_bench": ("Bancada do Apicultor", "Bancada do Apicultor", "Beekeeper's Bench", "bench", "axe"),
    # 0.2.0 — postos dos vendedores dos mods irmãos (só existem com o mod: MOD_OF)
    "mechanic_bench": ("Bancada do Mecânico", "Bancada do Mecânico", "Mechanic's Bench", "orientable", "pickaxe"),
    "alloy_anvil": ("Bigorna de Ligas", "Bigorna de Ligas", "Alloy Anvil", "bench", "pickaxe"),
    "carpenter_bench": ("Bancada do Marceneiro", "Bancada do Marceneiro", "Carpenter's Bench", "bench", "axe"),
    "changer_counter": ("Balcão do Cambista", "Balcão do Cambista", "Changer's Counter", "orientable", "axe"),
    # 0.5.0 — o Carteiro (só com o Móveis); modelo feito no Blender (tools/blender_050.py)
    "post_counter": ("Balcão dos Correios", "Balcão dos Correios", "Post Office Counter", "blender", "axe"),
}

# 0.5.0 — itens de cena com modelo do Blender (tools/blender_050.py): sem receita e fora do separador criativo
STAGE_ITEMS = ("mail_bag", "bobber", "market_stall")

# bloco/profissão → mod de que depende
MOD_OF = {"mechanic_bench": "tindustrial", "alloy_anvil": "tforja", "carpenter_bench": "tmoveis", "changer_counter": "cubcoin",
          "mechanic": "tindustrial", "alloy_smith": "tforja", "carpenter": "tmoveis", "money_changer": "cubcoin",
          "post_counter": "tmoveis", "postman": "tmoveis"}

# profissão: (pt_br, pt_pt, en, posto)
PROFESSIONS = {
    "mayor": ("Prefeito", "Presidente da Câmara", "Mayor", "mayor_desk"),
    "baker": ("Padeiro", "Padeiro", "Baker", "baker_oven"),
    "florist": ("Florista", "Florista", "Florist", "flower_stall"),
    "innkeeper": ("Taberneiro", "Taberneiro", "Innkeeper", "tavern_counter"),
    "beekeeper": ("Apicultor", "Apicultor", "Beekeeper", "apiary_bench"),
    "mechanic": ("Mecânico", "Mecânico", "Mechanic", "mechanic_bench"),
    "alloy_smith": ("Ferreiro de Ligas", "Ferreiro de Ligas", "Alloy Smith", "alloy_anvil"),
    "carpenter": ("Marceneiro", "Marceneiro", "Carpenter", "carpenter_bench"),
    "money_changer": ("Cambista", "Cambista", "Money Changer", "changer_counter"),
    "postman": ("Carteiro", "Carteiro", "Postman", "post_counter"),
}


def tex(name):
    return f"{MOD}:block/{name}"


def face(t, uv=None, cull=None):
    f = {"texture": t}
    if uv:
        f["uv"] = uv
    if cull:
        f["cullface"] = cull
    return f


def gen_models():
    for bid, (_, _, _, kind, _) in BLOCKS.items():
        if kind == "orientable":
            model = {"parent": "minecraft:block/orientable",
                     "textures": {"top": tex(bid + "_top"), "front": tex(bid + "_front"), "side": tex(bid + "_side")}}
        elif kind == "desk":
            model = {"parent": "minecraft:block/block", "textures": {"top": tex(bid + "_top"), "side": tex(bid + "_side"),
                                                                       "front": tex(bid + "_front"), "particle": tex(bid + "_side")},
                     "elements": [
                         {"from": [0, 12, 0], "to": [16, 16, 16], "faces": {
                             "up": face("#top", cull="up"), "down": face("#side"),
                             "north": face("#side", [0, 0, 16, 4]), "south": face("#side", [0, 0, 16, 4]),
                             "east": face("#side", [0, 0, 16, 4]), "west": face("#side", [0, 0, 16, 4])}},
                         {"from": [1, 0, 1], "to": [15, 12, 15], "faces": {
                             "down": face("#side", cull="down"), "north": face("#front", [1, 4, 15, 16]),
                             "south": face("#side", [1, 4, 15, 16]), "east": face("#side", [1, 4, 15, 16]),
                             "west": face("#side", [1, 4, 15, 16])}}]}
        elif kind == "stall":
            model = {"parent": "minecraft:block/block", "textures": {"top": tex(bid + "_top"), "side": tex(bid + "_side"),
                                                                       "particle": tex(bid + "_side")},
                     "elements": [
                         {"from": [0, 10, 0], "to": [16, 14, 16], "faces": {
                             "up": face("#top"), "down": face("#side"),
                             "north": face("#side", [0, 0, 16, 4]), "south": face("#side", [0, 0, 16, 4]),
                             "east": face("#side", [0, 0, 16, 4]), "west": face("#side", [0, 0, 16, 4])}},
                         {"from": [2, 0, 2], "to": [14, 10, 14], "faces": {
                             "down": face("#side", cull="down"), "north": face("#side", [2, 6, 14, 16]),
                             "south": face("#side", [2, 6, 14, 16]), "east": face("#side", [2, 6, 14, 16]),
                             "west": face("#side", [2, 6, 14, 16])}}]}
        elif kind == "blender":  # 0.5.0: o modelo vem do Blender (tools/blender_050.py); aqui só se regista
            with open(os.path.join(RES, "assets", MOD, "models", "block", bid + ".json")) as fh:
                model = json.load(fh)
        else:  # bench
            model = {"parent": "minecraft:block/block", "textures": {"top": tex(bid + "_top"), "side": tex(bid + "_side"),
                                                                       "particle": tex(bid + "_side")},
                     "elements": [{"from": [0, 0, 0], "to": [16, 12, 16], "faces": {
                         "up": face("#top"), "down": face("#side", cull="down"),
                         "north": face("#side", [0, 4, 16, 16], "north"), "south": face("#side", [0, 4, 16, 16], "south"),
                         "east": face("#side", [0, 4, 16, 16], "east"), "west": face("#side", [0, 4, 16, 16], "west")}}]}
        write(f"assets/{MOD}/models/block/{bid}.json", model)
        write(f"assets/{MOD}/blockstates/{bid}.json", {"variants": {
            f"facing={f}": ({"model": f"{MOD}:block/{bid}"} | ({"y": y} if y else {}))
            for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})
        write(f"assets/{MOD}/items/{bid}.json", {"model": {"type": "minecraft:model", "model": f"{MOD}:block/{bid}"}})
        write(f"data/{MOD}/loot_table/blocks/{bid}.json", {
            "type": "minecraft:block", "pools": [{"rolls": 1, "condition": {"type": "minecraft:survives_explosion"},
                                                  "entries": [{"type": "minecraft:item", "name": f"{MOD}:{bid}"}]}],
            "random_sequence": f"{MOD}:blocks/{bid}"}, MOD_OF.get(bid))
    for it in STAGE_ITEMS:
        write(f"assets/{MOD}/items/{it}.json", {"model": {"type": "minecraft:model", "model": f"{MOD}:item/{it}"}})
    for tool in ("axe", "pickaxe"):
        write(f"data/minecraft/tags/block/mineable/{tool}.json",
              {"replace": False, "values": [tag_entry(b) for b, v in BLOCKS.items() if v[4] == tool]})
    write("data/minecraft/tags/point_of_interest_type/acquirable_job_site.json",
          {"replace": False, "values": [tag_entry(p) for p in PROFESSIONS]})


def tag_entry(name):
    """Entrada de tag: os blocos/profissões dos mods irmãos são opcionais (não existem sem o mod)."""
    return {"id": f"{MOD}:{name}", "required": False} if name in MOD_OF else f"{MOD}:{name}"


# ------------------------------------------------------------------ receitas

RECIPES = {}


def advancement(recipe, items, mod=None):
    crit = {"has_the_recipe": {"trigger": "minecraft:recipe_unlocked", "conditions": {"recipes": f"{MOD}:{recipe}"}}}
    for i, it in enumerate(items):
        crit[f"has_item_{i}"] = {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": it}]}}
    write(f"data/{MOD}/advancement/recipes/{recipe}.json", {"parent": "minecraft:recipes/root", "criteria": crit,
                                                            "requirements": [list(crit)], "rewards": {"recipes": [f"{MOD}:{recipe}"]}}, mod)


def shaped(name, pattern, key, count=1, unlock=None):
    r = {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern, "key": key, "result": {"id": f"{MOD}:{name}"}}
    if count > 1:
        r["result"]["count"] = count
    for v in key.values():
        assert v.startswith("#") or item_exists(v), v
    mod = MOD_OF.get(name)
    write(f"data/{MOD}/recipe/{name}.json", r, mod)
    RECIPES[name] = r
    advancement(name, unlock or sorted(set(key.values())), mod)


def gen_recipes():
    shaped("mayor_desk", ["PPP", "BEB", "P P"], {"P": "#minecraft:planks", "B": "minecraft:book", "E": "minecraft:emerald"},
           unlock=["minecraft:emerald"])
    shaped("baker_oven", ["BBB", "BFB", "BBB"], {"B": "minecraft:bricks", "F": "minecraft:furnace"}, unlock=["minecraft:bricks"])
    shaped("flower_stall", ["WWW", "SFS"], {"W": "#minecraft:wool", "S": "#minecraft:wooden_slabs", "F": "minecraft:flower_pot"},
           unlock=["minecraft:flower_pot"])
    shaped("tavern_counter", ["SSS", "PBP"], {"S": "#minecraft:wooden_slabs", "P": "#minecraft:planks", "B": "minecraft:barrel"},
           unlock=["minecraft:barrel"])
    shaped("apiary_bench", ["HHH", "PCP"], {"H": "minecraft:honeycomb", "P": "#minecraft:planks", "C": "minecraft:glass_bottle"},
           unlock=["minecraft:honeycomb"])
    # 0.2.0 — só com o mod (receita e conquista com condição)
    shaped("mechanic_bench", ["WWW", "IPI"], {"W": "tindustrial:copper_wire", "I": "minecraft:iron_ingot", "P": "#minecraft:planks"},
           unlock=["tindustrial:copper_wire"])
    shaped("alloy_anvil", ["BBB", " I ", "III"], {"B": "tforja:bronze_ingot", "I": "minecraft:iron_ingot"}, unlock=["tforja:bronze_ingot"])
    shaped("carpenter_bench", ["SSS", "PCP"], {"S": "#minecraft:wooden_slabs", "P": "#minecraft:planks", "C": "tmoveis:oak_chair"},
           unlock=["tmoveis:oak_chair"])
    shaped("changer_counter", ["NNN", "PEP"], {"N": "cubcoin:note_1", "P": "#minecraft:planks", "E": "minecraft:emerald"},
           unlock=["cubcoin:note_1"])
    # 0.5.0 — Balcão dos Correios: papel, tábuas e uma caixa de correio do Móveis
    shaped("post_counter", ["AAA", "PMP"], {"A": "minecraft:paper", "P": "#minecraft:planks", "M": "tmoveis:mailbox"},
           unlock=["tmoveis:mailbox"])


# ------------------------------------------------------------------ trocas (data-driven, 26.3)

XP_BUY = {1: 2, 2: 10, 3: 20, 4: 30, 5: 30}
XP_SELL = {1: 1, 2: 5, 3: 10, 4: 15, 5: 30}


def buy(item, count, uses=16):
    """O aldeão COMPRA: o jogador dá {count} {item}, recebe 1 esmeralda."""
    return ("buy", item, count, 1, uses)


def sell(emeralds, item, count, uses=12):
    """O aldeão VENDE: o jogador dá {emeralds} esmeraldas, recebe {count} {item}."""
    return ("sell", item, count, emeralds, uses)


TRADES = {
    "baker": {
        1: [buy("minecraft:wheat", 20), sell(1, "minecraft:bread", 6, 16)],
        2: [buy("minecraft:sugar", 12), sell(1, "minecraft:cookie", 8)],
        3: [buy("minecraft:egg", 12), sell(2, "minecraft:pumpkin_pie", 3)],
        4: [buy("minecraft:cocoa_beans", 10), sell(3, "minecraft:cake", 1)],
        5: [sell(4, "minecraft:golden_carrot", 4), sell(1, "minecraft:cookie", 16)],
    },
    "florist": {
        1: [buy("minecraft:bone_meal", 16), sell(1, "minecraft:poppy", 4, 16), sell(1, "minecraft:dandelion", 4, 16)],
        2: [buy("minecraft:flower_pot", 4), sell(1, "minecraft:cornflower", 3), sell(1, "minecraft:allium", 3)],
        3: [sell(1, "minecraft:blue_orchid", 2), sell(1, "minecraft:azure_bluet", 3), sell(2, "minecraft:lily_of_the_valley", 3)],
        4: [sell(3, "minecraft:pink_petals", 6), sell(2, "minecraft:sunflower", 2), sell(2, "minecraft:peony", 2)],
        5: [sell(6, "minecraft:torchflower_seeds", 2, 6), sell(6, "minecraft:pitcher_pod", 1, 6), sell(8, "minecraft:spore_blossom", 1, 4)],
    },
    "innkeeper": {
        1: [buy("minecraft:glass_bottle", 9), sell(1, "minecraft:mushroom_stew", 1), sell(1, "minecraft:beetroot_soup", 1)],
        2: [buy("minecraft:sweet_berries", 18), sell(1, "minecraft:baked_potato", 4, 16)],
        3: [sell(2, "minecraft:milk_bucket", 1, 8), sell(3, "minecraft:white_bed", 1, 6)],
        4: [sell(3, "minecraft:glow_berries", 6), sell(4, "minecraft:lantern", 2)],
        5: [sell(4, "minecraft:golden_apple", 1, 3), sell(12, "minecraft:music_disc_cat", 1, 1)],
    },
    "beekeeper": {
        1: [buy("minecraft:poppy", 16), sell(1, "minecraft:honey_bottle", 2)],
        2: [sell(1, "minecraft:honeycomb", 3), sell(2, "minecraft:candle", 4)],
        3: [buy("minecraft:glass_bottle", 6), sell(3, "minecraft:honey_block", 1)],
        4: [sell(5, "minecraft:beehive", 1, 6), sell(4, "minecraft:campfire", 2)],
        5: [sell(12, "minecraft:bee_nest", 1, 3), sell(3, "minecraft:honeycomb_block", 2)],
    },
}


def sellc(emeralds, item, count, components, uses=12, name=None):
    """Como sell, com componentes no item (verniz dos móveis, tipo e material das peças da Forja)."""
    return ("sellc", item, count, emeralds, uses, components, name)


# Preço de uma esmeralda no Balcão de Câmbio do CubCoin (o Cambista troca ao mesmo preço, sem margem).
def balcao_emerald():
    f = os.path.join(HOME, "Cods", "cubcoin", "src", "main", "resources", "data", "cubcoin", "cubcoin_exchange", "default.json")
    try:
        return json.load(open(f))["minecraft:emerald"]
    except (OSError, KeyError):
        return 4000


EMERALD_CB = balcao_emerald()
NOTE = {"cubcoin:note_1": 100, "cubcoin:note_2": 200, "cubcoin:note_5": 500, "cubcoin:note_10": 1000, "cubcoin:note_20": 2000,
        "cubcoin:note_50": 5000, "cubcoin:note_100": 10000}


def change(gives_count, gives, wants, wants_count, uses=32, additional=None, name=None):
    """Troca do Cambista: SEM desconto (reputation_discount 0) e com «wants» de 1 só item, para nenhum desconto (gossip, procura,
    herói da aldeia) mexer no câmbio."""
    return ("change", gives, gives_count, wants, wants_count, uses, additional, name)


def cambista():
    e = EMERALD_CB
    assert e % 2000 == 0 and e % 500 == 0, e
    block = e * 9
    return {
        # esmeralda → notas (1 esmeralda em «wants»)
        1: [change(e // 2000, "cubcoin:note_20", "minecraft:emerald", 1, name="emerald_note_20"),
            # notas → esmeralda: 1 nota de 20 em «wants» + o resto em «additional_wants» (este não tem desconto)
            change(1, "minecraft:emerald", "cubcoin:note_20", 1, additional=("cubcoin:note_20", e // 2000 - 1), name="note_20_emerald")],
        2: [change(e // 1000, "cubcoin:note_10", "minecraft:emerald", 1, name="emerald_note_10"),
            change(1, "minecraft:emerald", "cubcoin:note_10", 1, additional=("cubcoin:note_10", e // 1000 - 1), name="note_10_emerald")],
        3: [change(block // 2000, "cubcoin:note_20", "minecraft:emerald_block", 1, 16, name="emerald_block_note_20"),
            change(1, "minecraft:emerald_block", "cubcoin:note_100", 1, 16, additional=("cubcoin:note_20", (block - 10000) // 2000),
                   name="note_100_emerald_block")],
        4: [change(e // 500, "cubcoin:note_5", "minecraft:emerald", 1, name="emerald_note_5"),
            change(1, "minecraft:emerald", "cubcoin:note_5", 1, additional=("cubcoin:note_5", e // 500 - 1), name="note_5_emerald")],
        5: [change(block // 10000 if block % 10000 == 0 else block // 2000, "cubcoin:note_100" if block % 10000 == 0 else "cubcoin:note_20",
                   "minecraft:emerald_block", 1, 16, name="emerald_block_notes"),
            change(e // 2000, "cubcoin:note_20", "minecraft:emerald", 1, 64, name="emerald_note_20_bulk")],
    }


VARNISHED = {"minecraft:block_state": {"finish": "natural"}}


def part(kind, material):
    return {"tforja:part_kind": kind, "tforja:parts": [material], "minecraft:custom_model_data": {"strings": [kind, material]}}


MOD_TRADES = {
    # Mecânico (Industrial): fio de cobre, cabos, canos, conectores, chave, melhorias e placa de expansão — barato mas nunca
    # abaixo do custo dos materiais
    "mechanic": {
        1: [buy("minecraft:copper_ingot", 10), sell(1, "tindustrial:copper_wire", 16, 16), sell(2, "tindustrial:copper_cable", 6, 16)],
        2: [buy("minecraft:redstone", 16), sell(3, "tindustrial:item_pipe", 8), sell(3, "tindustrial:fluid_pipe", 8)],
        3: [sell(3, "tindustrial:pipe_wrench", 1, 4), sell(4, "tindustrial:extraction_connector", 2), sell(6, "tindustrial:gold_cable", 8)],
        4: [sell(8, "tindustrial:speed_upgrade", 1, 6), sell(8, "tindustrial:capacity_upgrade", 1, 6), sell(10, "tindustrial:efficiency_upgrade", 1, 6)],
        5: [sell(16, "tindustrial:expansion_plate", 1, 3), sell(10, "tindustrial:range_upgrade", 1, 4)],
    },
    # Ferreiro de Ligas (Forja): lingotes de liga, minérios da 1.9 e peças de aço/bronze (nada de adamantite, ferro-noite,
    # diapasão nem materiais lendários)
    "alloy_smith": {
        1: [buy("minecraft:coal", 15), sell(3, "tforja:bronze_ingot", 4)],
        2: [sell(4, "tforja:steel_ingot", 3), sell(3, "tforja:chromium_ingot", 2), buy("tforja:raw_chromite", 6)],
        3: [sell(5, "tforja:titanium_ingot", 2), sell(4, "tforja:steel_cable", 2), buy("tforja:raw_titanium", 6)],
        4: [sell(9, "tforja:stainless_steel_ingot", 2, 6), sell(7, "tforja:red_steel_ingot", 2, 6)],
        5: [sellc(6, "tforja:part", 1, part("pickaxe_head", "alloy_steel"), 3, "steel_pickaxe_head"),
            sellc(3, "tforja:part", 1, part("binding", "alloy_bronze"), 6, "bronze_binding"),
            sellc(3, "tforja:part", 1, part("handle", "alloy_steel"), 6, "steel_handle")],
    },
    # Marceneiro (Móveis): móveis envernizados (verniz natural: não envelhecem à chuva) e pincéis de verniz
    "carpenter": {
        1: [buy("minecraft:oak_planks", 24), sellc(1, "tmoveis:oak_chair", 2, VARNISHED, 12), sellc(1, "tmoveis:oak_stool", 2, VARNISHED, 12)],
        2: [sellc(2, "tmoveis:oak_table", 1, VARNISHED, 12), sellc(2, "tmoveis:oak_nightstand", 1, VARNISHED, 8)],
        3: [sell(3, "tmoveis:varnish_brush_natural", 1, 6), sell(3, "tmoveis:varnish_brush_honey", 1, 6), sellc(3, "tmoveis:oak_coffee_table", 1, VARNISHED, 8)],
        4: [sell(4, "tmoveis:chandelier", 1, 6), sell(4, "tmoveis:red_sofa", 2, 6)],
        5: [sell(7, "tmoveis:red_double_bed", 1, 4), sellc(6, "tmoveis:oak_wardrobe", 1, VARNISHED, 4)],
    },
    # Cambista (CubCoin): esmeraldas ↔ notas ao preço do Balcão de Câmbio
    "money_changer": cambista(),
    # 0.5.0 — Carteiro (Móveis): papel, livros para escrever, pacotes, bússolas e as caixas de correio do Móveis
    "postman": {
        1: [buy("minecraft:paper", 24), sell(1, "minecraft:writable_book", 1)],
        2: [buy("minecraft:leather", 6), sell(2, "minecraft:bundle", 1, 8)],
        3: [buy("minecraft:ink_sac", 6), sell(3, "minecraft:compass", 1, 8)],
        4: [sell(4, "tmoveis:mailbox", 1, 8), buy("minecraft:feather", 16)],
        5: [sell(5, "minecraft:name_tag", 1, 4), sell(2, "tmoveis:mailbox", 1, 8)],
    },
}


def gen_trades():
    all_trades = dict(TRADES) | MOD_TRADES
    for prof, levels in all_trades.items():
        mod = MOD_OF.get(prof)
        for lvl, trades in levels.items():
            ids = []
            for tr_ in trades:
                kind = tr_[0]
                if kind == "change":
                    _, gives, gcount, wants, wcount, uses, additional, name = tr_
                    for it in (gives, wants) + ((additional[0],) if additional else ()):
                        assert item_exists(it), f"item inexistente: {it}"
                    t = {"wants": {"id": wants} | ({"count": wcount} if wcount > 1 else {}),
                         "gives": {"id": gives} | ({"count": gcount} if gcount > 1 else {}),
                         "max_uses": uses, "xp": XP_SELL[lvl], "reputation_discount": 0.0}
                    if additional and additional[1] > 0:
                        t["additional_wants"] = {"id": additional[0]} | ({"count": additional[1]} if additional[1] > 1 else {})
                    rel = f"{prof}/{lvl}/{name}"
                    write(f"data/{MOD}/villager_trade/{rel}.json", t, mod)
                    ids.append(f"{MOD}:{rel}")
                    continue
                if kind == "sellc":
                    _, item, count, emeralds, uses, comps, name = tr_
                    assert item_exists(item), f"item inexistente: {item}"
                    t = {"wants": {"id": "minecraft:emerald", "count": emeralds} if emeralds > 1 else {"id": "minecraft:emerald"},
                         "gives": {"id": item, "components": comps} | ({"count": count} if count > 1 else {}), "max_uses": uses,
                         "xp": XP_SELL[lvl], "reputation_discount": 0.05}
                    rel = f"{prof}/{lvl}/emerald_{name or item.split(':')[1]}"
                    write(f"data/{MOD}/villager_trade/{rel}.json", t, mod)
                    ids.append(f"{MOD}:{rel}")
                    continue
                kind, item, count, emeralds, uses = tr_
                assert item_exists(item), f"item inexistente: {item}"
                name = f"{item.split(':')[1]}_emerald" if kind == "buy" else f"emerald_{item.split(':')[1]}"
                rel = f"{prof}/{lvl}/{name}"
                if kind == "buy":
                    t = {"wants": {"id": item, "count": count}, "gives": {"id": "minecraft:emerald"}, "max_uses": uses,
                         "xp": XP_BUY[lvl], "reputation_discount": 0.05}
                else:
                    t = {"wants": {"id": "minecraft:emerald", "count": emeralds} if emeralds > 1 else {"id": "minecraft:emerald"},
                         "gives": {"id": item, "count": count} if count > 1 else {"id": item}, "max_uses": uses,
                         "xp": XP_SELL[lvl], "reputation_discount": 0.05}
                write(f"data/{MOD}/villager_trade/{rel}.json", t, mod)
                ids.append(f"{MOD}:{rel}")
            write(f"data/{MOD}/trade_set/{prof}/level_{lvl}.json",
                  {"amount": 2, "random_sequence": f"{MOD}:trade_set/{prof}/level_{lvl}", "trades": ids}, mod)


# ------------------------------------------------------------------ traduções

def gen_lang_names():
    for bid, (br, pt, en, _, _) in BLOCKS.items():
        tr(f"block.{MOD}.{bid}", br, pt, en)
    for p, (br, pt, en, _) in PROFESSIONS.items():
        tr(f"entity.{MOD}.villager.{p}", br, pt, en)
    tr(f"itemGroup.{MOD}.villages", "Vilas do Tcordeiro", "Vilas do Tcordeiro", "Tcordeiro's Villages")
    T = [
        ("tvilas.level.aldeia", "Aldeia", "Aldeia", "Hamlet"),
        ("tvilas.level.vila", "Vila", "Vila", "Village"),
        ("tvilas.level.cidade", "Cidade", "Cidade", "Town"),
        ("tvilas.level.up", "★ A vila subiu de nível: agora é %s! Mais terrenos à venda e missões maiores.",
         "★ A povoação subiu de nível: agora é %s! Mais terrenos à venda e missões maiores.",
         "★ The village leveled up: it is now a %s! More lots for sale and bigger missions."),
        ("tvilas.desk.duplicate", "Esta vila já tem Prefeitura: só pode haver uma Mesa do Prefeito por vila.",
         "Esta povoação já tem Câmara: só pode haver uma Mesa da Câmara por povoação.",
         "This village already has a Town Hall: only one Mayor's Desk per village."),
        ("tvilas.mayor.no_desk", "O Prefeito perdeu a Prefeitura: coloque uma Mesa do Prefeito por perto.",
         "O Presidente da Câmara perdeu a Câmara: põe uma Mesa da Câmara perto.",
         "The Mayor lost the Town Hall: place a Mayor's Desk nearby."),
        ("tvilas.alarm.on", "⚠ ALARME! O Prefeito toca o sino e chama os golens.",
         "⚠ ALARME! O Presidente da Câmara toca o sino e chama os golens.",
         "⚠ ALARM! The Mayor rings the bell and calls the golems."),
        ("tvilas.alarm.off", "A vila está calma de novo.", "A povoação está calma outra vez.", "The village is calm again."),
        ("tvilas.lot.no_village", "Você não está numa vila com Prefeitura.", "Não estás numa povoação com Câmara.",
         "You are not in a village with a Town Hall."),
        ("tvilas.lot.outside", "Esse terreno sairia da vila: chegue mais perto do centro.",
         "Esse terreno sairia da povoação: chega-te mais ao centro.", "That lot would go outside the village: move closer to the center."),
        ("tvilas.lot.civic", "A praça da Prefeitura não se vende: afaste-se um pouco da Mesa do Prefeito.",
         "A praça da Câmara não se vende: afasta-te um pouco da Mesa da Câmara.",
         "The Town Hall square is not for sale: move a bit away from the Mayor's Desk."),
        ("tvilas.lot.overlap", "Esse terreno toca num terreno que já tem dono.", "Esse terreno toca num terreno que já tem dono.",
         "That lot touches a lot that already has an owner."),
        ("tvilas.lot.sold_out", "A vila já vendeu todos os %s terrenos do seu nível. Suba o nível da vila para haver mais.",
         "A povoação já vendeu os %s terrenos do seu nível. Sobe o nível da povoação para haver mais.",
         "The village sold all %s lots of its level. Level the village up for more."),
        ("tvilas.lot.no_money", "Falta dinheiro: o terreno custa %s.", "Falta dinheiro: o terreno custa %s.",
         "Not enough money: the lot costs %s."),
        ("tvilas.lot.bought", "Terreno comprado por %s: de x=%s z=%s até x=%s z=%s. Só você e os seus amigos constroem aqui.",
         "Terreno comprado por %s: de x=%s z=%s até x=%s z=%s. Só tu e os teus amigos constroem aqui.",
         "Lot bought for %s: from x=%s z=%s to x=%s z=%s. Only you and your friends build here."),
        ("tvilas.lot.not_yours", "Fique dentro do SEU terreno para mudar os amigos.", "Fica dentro do TEU terreno para mudar os amigos.",
         "Stand inside YOUR lot to change friends."),
        ("tvilas.lot.protected", "Este terreno tem dono: só ele e os amigos dele podem mexer aqui.",
         "Este terreno tem dono: só ele e os amigos dele podem mexer aqui.", "This lot has an owner: only they and their friends can build here."),
        ("tvilas.lot.none_here", "Aqui não há terreno comprado.", "Aqui não há terreno comprado.", "There is no bought lot here."),
        ("tvilas.lot.info", "Terreno de %s: de x=%s z=%s até x=%s z=%s, %s amigo(s).",
         "Terreno de %s: de x=%s z=%s até x=%s z=%s, %s amigo(s).", "%s's lot: from x=%s z=%s to x=%s z=%s, %s friend(s)."),
        ("tvilas.friend.self", "Você já é o dono.", "Já és o dono.", "You are already the owner."),
        ("tvilas.friend.added", "%s agora pode construir no seu terreno.", "%s agora pode construir no teu terreno.",
         "%s can now build on your lot."),
        ("tvilas.friend.removed", "%s já não pode construir no seu terreno.", "%s já não pode construir no teu terreno.",
         "%s can no longer build on your lot."),
        ("tvilas.friend.not_friend", "Esse jogador não era seu amigo neste terreno.", "Esse jogador não era teu amigo neste terreno.",
         "That player was not your friend on this lot."),
        ("tvilas.mission.none", "Essa missão não existe.", "Essa missão não existe.", "That mission does not exist."),
        ("tvilas.mission.already", "Você já aceitou essa missão.", "Já aceitaste essa missão.", "You already accepted that mission."),
        ("tvilas.mission.accepted", "Missão aceita: %s.", "Missão aceite: %s.", "Mission accepted: %s."),
        ("tvilas.mission.not_accepted", "Aceite a missão primeiro: só conta o que fizer depois de aceitar.",
         "Aceita a missão primeiro: só conta o que fizeres depois de aceitar.", "Accept the mission first: only what you do afterwards counts."),
        ("tvilas.mission.not_done", "Ainda não: %s de %s.", "Ainda não: %s de %s.", "Not yet: %s of %s."),
        ("tvilas.mission.paid", "Missão cumprida! +%s, +%s de reputação.", "Missão cumprida! +%s, +%s de reputação.",
         "Mission complete! +%s, +%s reputation."),
        ("tvilas.mission.deliver", "Trazer %s %s", "Trazer %s %s", "Bring %s %s"),
        ("tvilas.mission.decorate", "Colocar %s %s na vila", "Pôr %s %s na povoação", "Place %s %s in the village"),
        ("tvilas.mission.defend", "Defender: derrotar %s monstros/invasores na vila", "Defender: derrotar %s monstros/invasores na povoação",
         "Defend: defeat %s monsters/raiders in the village"),
        ("tvilas.dialog.title", "Prefeitura — %s", "Câmara Municipal — %s", "Town Hall — %s"),
        ("tvilas.dialog.status", "População: %s · Sua reputação: %s · Reputação da vila: %s · Terrenos vendidos: %s de %s",
         "População: %s · A tua reputação: %s · Reputação da povoação: %s · Terrenos vendidos: %s de %s",
         "Population: %s · Your reputation: %s · Village reputation: %s · Lots sold: %s of %s"),
        ("tvilas.dialog.mission", "%s. %s — %s, +%s reputação", "%s. %s — %s, +%s reputação",
         "%s. %s — %s, +%s reputation"),
        ("tvilas.dialog.progress", " (%s/%s)", " (%s/%s)", " (%s/%s)"),
        ("tvilas.dialog.defend_hint", " · só conta numa invasão", " · só conta numa invasão", " · counts only during a raid"),
        ("tvilas.dialog.accept", "Aceitar %s", "Aceitar %s", "Accept %s"),
        ("tvilas.dialog.deliver", "Entregar %s", "Entregar %s", "Hand in %s"),
        ("tvilas.dialog.buy", "Comprar terreno aqui (%s)", "Comprar terreno aqui (%s)", "Buy lot here (%s)"),
        ("tvilas.dialog.lot_here", "Você está no terreno de %s.", "Estás no terreno de %s.", "You are on %s's lot."),
        ("tvilas.dialog.lot_info", "Ver este terreno", "Ver este terreno", "View this lot"),
        ("tvilas.dialog.friend_hint", "Amigos no seu terreno: /vila terreno amigo adicionar <jogador>",
         "Amigos no teu terreno: /vila terreno amigo adicionar <jogador>", "Friends on your lot: /vila terreno amigo adicionar <player>"),
        ("tvilas.mayor.arrived", "Um Prefeito chegou na vila e se instalou na Prefeitura.",
         "Um Presidente da Câmara chegou à povoação e instalou-se na Câmara.", "A Mayor arrived in the village and moved into the Town Hall."),
        ("tvilas.upgrade.immigrant", "um morador novo chegou (grátis)", "chegou um morador novo (grátis)", "a new resident arrived (free)"),
        ("tvilas.upgrade.resident", "novo morador", "novo morador", "new resident"),
        ("tvilas.upgrade.golem", "golem da vila", "golem da povoação", "village golem"),
        ("tvilas.upgrade.alarm", "reforço do alarme (+1 golem)", "reforço do alarme (+1 golem)", "alarm upgrade (+1 golem)"),
        ("tvilas.upgrade.bought", "A Prefeitura pagou: %s (%s).", "A Câmara pagou: %s (%s).", "The Town Hall paid for: %s (%s)."),
        ("tvilas.dialog.treasury", "Tesouraria: %s (já cobrado: %s) · Você deve: %s · Taxa das trocas: %s %% · Território: %s blocos",
         "Tesouraria: %s (já cobrado: %s) · Deves: %s · Taxa das trocas: %s %% · Território: %s blocos",
         "Treasury: %s (collected: %s) · You owe: %s · Trade tax: %s %% · Territory: %s blocks"),
        ("tvilas.dialog.expense", "Dia %s: %s — %s", "Dia %s: %s — %s", "Day %s: %s — %s"),
        ("tvilas.dialog.no_expenses", "A Prefeitura ainda não gastou nada.", "A Câmara ainda não gastou nada.", "The Town Hall has not spent anything yet."),
        ("tvilas.cmd.no_village", "Você não está numa vila com Prefeitura.", "Não estás numa povoação com Câmara.",
         "You are not in a village with a Town Hall."),
        ("tvilas.cmd.info", "%s · população %s · reputação da vila %s (sua: %s) · terrenos %s/%s · Mesa em %s %s %s",
         "%s · população %s · reputação da povoação %s (tua: %s) · terrenos %s/%s · Mesa em %s %s %s",
         "%s · population %s · village reputation %s (yours: %s) · lots %s/%s · Desk at %s %s %s"),
    ]
    from life_040 import T_040
    from life_050 import T_050
    T += T_020 + T_030 + T_040 + T_050
    for k, br, pt, en in T:
        tr(k, br, pt, en)


T_020 = [
    ("entity.tvilas.builder", "Construtor", "Construtor", "Builder"),
    ("entity.tvilas.village_merchant", "Mercador da Vila", "Mercador da Povoação", "Village Peddler"),
    ("tvilas.project.golem", "o golem da vila", "o golem da povoação", "the village golem"),
    ("tvilas.project.wall", "a muralha", "a muralha", "the town wall"),
    ("tvilas.project.lighting", "a iluminação pública", "a iluminação pública", "the street lights"),
    ("tvilas.upgrade.lighting", "iluminação pública", "iluminação pública", "street lights"),
    ("tvilas.upgrade.wall", "muralha encomendada", "muralha encomendada", "town wall ordered"),
    ("tvilas.news.building", "A vila está construindo %s", "A povoação está a construir %s", "The village is building %s"),
    ("tvilas.news.done", "A vila terminou %s", "A povoação terminou %s", "The village finished %s"),
    ("tvilas.news.overnight", "Durante a noite os construtores colocaram %s blocos", "Durante a noite os construtores puseram %s blocos",
     "Overnight the builders placed %s blocks"),
    ("tvilas.news.merchant_arrived", "O mercador da vila chegou à praça (fica %s dias)", "O mercador chegou à praça (fica %s dias)",
     "The village peddler reached the square (stays %s days)"),
    ("tvilas.news.merchant_left", "O mercador foi embora (volta no dia %s)", "O mercador partiu (volta no dia %s)",
     "The peddler left (back on day %s)"),
    ("tvilas.news.tax_up", "O Prefeito subiu a taxa das trocas para %s %% (%s)", "O Presidente subiu a taxa das trocas para %s %% (%s)",
     "The Mayor raised the trade tax to %s %% (%s)"),
    ("tvilas.news.tax_down", "O Prefeito baixou a taxa das trocas para %s %% (%s)", "O Presidente baixou a taxa das trocas para %s %% (%s)",
     "The Mayor lowered the trade tax to %s %% (%s)"),
    ("tvilas.news.debt_warning", "Você deve %s à vila: pague até amanhã ou seu terreno fica congelado",
     "Deves %s à povoação: paga até amanhã ou o teu terreno fica congelado", "You owe the village %s: pay by tomorrow or your lot freezes"),
    ("tvilas.news.debt_frozen", "Seu terreno está congelado até pagar %s (nada foi apagado)",
     "O teu terreno está congelado até pagares %s (nada foi apagado)", "Your lot is frozen until you pay %s (nothing was removed)"),
    ("tvilas.news.debt_unfrozen", "Dívida paga: seu terreno voltou ao normal", "Dívida paga: o teu terreno voltou ao normal",
     "Debt paid: your lot is back to normal"),
    ("tvilas.news.wall_cancelled", "%s cancelou a muralha (o que já foi feito fica)", "%s cancelou a muralha (o que já está feito fica)",
     "%s cancelled the town wall (what was built stays)"),
    ("tvilas.tax.reason.base", "taxa base", "taxa base", "base rate"),
    ("tvilas.tax.reason.raised", "mais obras para manter", "mais obras para manter", "more works to keep up"),
    ("tvilas.tax.reason.capped", "chegou ao teto", "chegou ao teto", "at the ceiling"),
    ("tvilas.tax.reason.full", "a tesouraria está cheia", "a tesouraria está cheia", "the treasury is full"),
    ("tvilas.lot.frozen", "Terreno congelado por dívida: pague no quadro da Prefeitura.", "Terreno congelado por dívida: paga no quadro da Câmara.",
     "Lot frozen for debt: pay at the Town Hall board."),
    ("tvilas.debt.none", "Você não deve nada a esta vila.", "Não deves nada a esta povoação.", "You owe this village nothing."),
    ("tvilas.debt.no_money", "Falta dinheiro: a dívida é %s.", "Falta dinheiro: a dívida é %s.", "Not enough money: the debt is %s."),
    ("tvilas.debt.no_emeralds", "Faltam esmeraldas: são precisas %s.", "Faltam esmeraldas: são precisas %s.", "Not enough emeralds: %s needed."),
    ("tvilas.debt.paid", "Dívida de %s paga. Obrigado!", "Dívida de %s paga. Obrigado!", "Debt of %s paid. Thank you!"),
    ("tvilas.wall.not_citizen", "Só quem tem terreno ou reputação nesta vila decide sobre a muralha.",
     "Só quem tem terreno ou reputação nesta povoação decide sobre a muralha.", "Only those with a lot or reputation here decide about the wall."),
    ("tvilas.wall.not_building", "Não há muralha em obras.", "Não há muralha em obras.", "No wall is being built."),
    ("tvilas.wall.not_cancelled", "A muralha não foi cancelada.", "A muralha não foi cancelada.", "The wall was not cancelled."),
    ("tvilas.wall.cancelled", "Muralha cancelada.", "Muralha cancelada.", "Wall cancelled."),
    ("tvilas.wall.resumed", "Muralha retomada.", "Muralha retomada.", "Wall resumed."),
    ("tvilas.dialog.tax", "Taxa das trocas: %s %% (%s) · base %s %% + %s %% por obra paga (%s obras) · teto %s %%",
     "Taxa das trocas: %s %% (%s) · base %s %% + %s %% por obra paga (%s obras) · teto %s %%",
     "Trade tax: %s %% (%s) · base %s %% + %s %% per paid work (%s works) · ceiling %s %%"),
    ("tvilas.dialog.project", "🏗 %s: %s blocos colocados (%s)", "🏗 %s: %s blocos postos (%s)", "🏗 %s: %s blocks placed (%s)"),
    ("tvilas.dialog.wall_done", "A vila já tem muralha.", "A povoação já tem muralha.", "The village has its wall."),
    ("tvilas.dialog.lighting", "💡 Iluminação pública: %s postes", "💡 Iluminação pública: %s postes", "💡 Street lights: %s poles"),
    ("tvilas.dialog.merchant_here", "🐴 O mercador está na praça até o dia %s", "🐴 O mercador está na praça até ao dia %s",
     "🐴 The peddler is in the square until day %s"),
    ("tvilas.dialog.merchant_next", "Próximo mercador: dia %s", "Próximo mercador: dia %s", "Next peddler: day %s"),
    ("tvilas.dialog.frozen", "❄ Seu terreno está congelado até pagar a dívida.", "❄ O teu terreno está congelado até pagares a dívida.",
     "❄ Your lot is frozen until you pay the debt."),
    ("tvilas.dialog.pay_debt", "Pagar dívida (%s)", "Pagar dívida (%s)", "Pay debt (%s)"),
    ("tvilas.dialog.wall_cancel", "Cancelar muralha", "Cancelar muralha", "Cancel wall"),
    ("tvilas.dialog.wall_resume", "Retomar muralha", "Retomar muralha", "Resume wall"),
]


T_030 = [
    ("tvilas.coin.price", "Preço: %s", "Preço: %s", "Price: %s"),
    ("tvilas.money.emeralds", "%s esmeraldas", "%s esmeraldas", "%s emeralds"),
    ("tvilas.mission.already_done", "Você já fez esta missão neste lote. Missões novas em %s.",
     "Já fizeste esta missão neste lote. Missões novas em %s.", "You already did this mission in this batch. New missions in %s."),
    ("tvilas.batch.left", "%s d %s h", "%s d %s h", "%s d %s h"),
    ("tvilas.mission.not_real", "Esta Mesa não está numa vila de verdade: as missões só pagam numa vila do jogo ou com 5 aldeões ou mais.",
     "Esta Mesa não está numa povoação a sério: as missões só pagam numa povoação do jogo ou com 5 aldeões ou mais.",
     "This Desk is not in a real village: missions only pay in a generated village or one with 5+ villagers."),
    ("tvilas.dialog.batch", "Missões novas em %s · a vila pagou hoje %s de %s", "Missões novas em %s · a povoação pagou hoje %s de %s",
     "New missions in %s · the village paid %s of %s today"),
    ("tvilas.dialog.prestige", "Prestígio: %s de %s para o próximo morador · população %s de %s",
     "Prestígio: %s de %s para o próximo morador · população %s de %s", "Prestige: %s of %s for the next resident · population %s of %s"),
    ("tvilas.dialog.requests", "%s aldeão(s) com «!» têm pedidos para você", "%s aldeão(s) com «!» têm pedidos para ti",
     "%s villager(s) with «!» have requests for you"),
    ("tvilas.dialog.mission_done", " · feita ✔", " · feita ✔", " · done ✔"),
    ("tvilas.request.title", "Pedido", "Pedido", "Request"),
    ("tvilas.request.asks", "%s pede: %s", "%s pede: %s", "%s asks: %s"),
    ("tvilas.request.pays", "Paga %s e +%s de reputação (da vila)", "Paga %s e +%s de reputação (da povoação)", "Pays %s and +%s reputation (from the village)"),
    ("tvilas.request.have", "Você tem %s de %s", "Tens %s de %s", "You have %s of %s"),
    ("tvilas.request.once", "Uma vez por jogador; pedidos novos em %s. Shift+clique abre as trocas.",
     "Uma vez por jogador; pedidos novos em %s. Shift+clique abre as trocas.", "Once per player; new requests in %s. Shift+click opens the trades."),
    ("tvilas.request.deliver", "Entregar", "Entregar", "Hand in"),
    ("tvilas.request.trade", "Trocar", "Trocar", "Trade"),
    ("tvilas.request.too_far", "Chegue mais perto do aldeão.", "Chega-te mais perto do aldeão.", "Get closer to the villager."),
    ("tvilas.request.none", "Esse aldeão não tem pedido agora.", "Esse aldeão não tem pedido agora.", "That villager has no request now."),
    ("tvilas.news.newcomer", "Um morador novo está chegando à vila", "Um morador novo está a chegar à povoação", "A new resident is coming to the village"),
    ("tvilas.upgrade.house", "casa simples para um morador novo", "casa simples para um morador novo", "simple house for a new resident"),
    ("tvilas.project.house", "uma casa nova", "uma casa nova", "a new house"),
    ("tvilas.mission.no_funds", "A tesouraria não tem como pagar: a missão vale %s e há %s.",
     "A tesouraria não tem como pagar: a missão vale %s e há %s.", "The treasury cannot pay: the mission is worth %s and it holds %s."),
    ("tvilas.mission.daily_cap", "A tesouraria já pagou hoje o máximo em missões (%s). Volte amanhã.",
     "A tesouraria já pagou hoje o máximo em missões (%s). Volta amanhã.", "The treasury already paid today's maximum for missions (%s). Come back tomorrow."),
    ("tvilas.lot.entity_protected", "Isto está num terreno com dono ou na área da Prefeitura: não pode mexer.",
     "Isto está num terreno com dono ou na área da Câmara: não podes mexer.", "This is on an owned lot or in the Town Hall area: hands off."),
    ("tvilas.mayor.recalled", "O Prefeito voltou para a Prefeitura.", "O Presidente da Câmara voltou à Câmara.", "The Mayor is back at the Town Hall."),
    ("tvilas.mayor.taken", "Levaram o Prefeito: leve-o para um lugar carregado e clique na Mesa. Sumido há 3 dias, chega outro.",
     "Levaram o Presidente: leva-o para um sítio carregado e clica na Mesa. Sumido há 3 dias, chega outro.",
     "The Mayor was taken: bring him somewhere loaded and click the Desk. Gone for 3 days, another one comes."),
    ("tvilas.dialog.mayor_taken", "⚠ O Prefeito foi levado: clique na Mesa para ele voltar.",
     "⚠ O Presidente foi levado: clica na Mesa para ele voltar.", "⚠ The Mayor was taken: click the Desk to bring him back."),
    ("tvilas.dialog.civic_missing", "Faltam: %s (o Prefeito manda construir com a tesouraria)",
     "Faltam: %s (o Presidente manda construir com a tesouraria)", "Missing: %s (the Mayor orders them with the treasury)"),
    ("tvilas.civic.not_owner", "Só quem colocou a Mesa do Prefeito escolhe os amigos da Prefeitura.",
     "Só quem pôs a Mesa da Câmara escolhe os amigos da Câmara.", "Only whoever placed the Mayor's Desk picks the Town Hall friends."),
    ("tvilas.civic.friend_added", "%s agora pode mexer na Prefeitura e na praça.", "%s agora pode mexer na Câmara e na praça.",
     "%s can now build at the Town Hall and square."),
    ("tvilas.civic.friend_removed", "%s já não pode mexer na Prefeitura.", "%s já não pode mexer na Câmara.", "%s can no longer build at the Town Hall."),
    ("tvilas.dialog.mission_fund", "Fundo das missões: %s (até %s; +50 CB por dia)", "Fundo das missões: %s (até %s; +50 CB por dia)",
     "Mission fund: %s (up to %s; +50 CB a day)"),
    ("tvilas.upgrade.town_hall", "Prefeitura", "Câmara", "Town Hall"),
    ("tvilas.upgrade.market", "Mercado", "Mercado", "Market"),
    ("tvilas.upgrade.tavern", "Taverna", "Taverna", "Tavern"),
    ("tvilas.upgrade.plaza", "Praça", "Praça", "Plaza"),
    ("tvilas.project.town_hall", "a Prefeitura", "a Câmara", "the Town Hall"),
    ("tvilas.project.market", "o Mercado", "o Mercado", "the Market"),
    ("tvilas.project.tavern", "a Taverna", "a Taverna", "the Tavern"),
    ("tvilas.project.plaza", "a Praça", "a Praça", "the Plaza"),
]


# ------------------------------------------------------------------ guia

def P(key, recipe, br, pt, en, requires=None):
    return {"key": key, "recipe": recipe, "br": br, "pt": pt, "en": en, "requires": requires}


GUIDE = [
    P("cover", None,
      ("Guia das Vilas", "Prefeito, missões, terrenos com dono, vilas que crescem, alarme contra invasões, vendedores novos, obras à vista e um mercador.\n\nTudo começa na Prefeitura, no centro das vilas novas."),
      ("Guia das Vilas", "Presidente da Câmara, missões, terrenos com dono, povoações que crescem, alarme contra invasões, vendedores novos, obras à vista e um mercador.\n\nTudo começa na Câmara, no centro das povoações novas."),
      ("Villages Guide", "Mayor, missions, owned lots, growing villages, a raid alarm, new traders, works you can watch and a peddler.\n\nIt all starts at the Town Hall, in the center of new villages.")),
    P("mayor", "mayor_desk",
      ("O Prefeito", "Um aldeão sem emprego que ache a Mesa do Prefeito vira Prefeito. Só uma Mesa por vila."),
      ("Presidente", "Um aldeão sem emprego que encontre a Mesa da Câmara fica Presidente. Só uma por povoação."),
      ("The Mayor", "A jobless villager who finds the Mayor's Desk becomes the Mayor. One desk per village.")),
    P("town_hall", None,
      ("A Prefeitura", "Metade das vilas novas nasce com uma Prefeitura no centro: um salão com a Mesa do Prefeito e um sino na praça.\n\nNas outras vilas, faça a Mesa e coloque-a perto do centro."),
      ("A Câmara", "Metade das povoações novas nasce com uma Câmara no centro: um salão com a Mesa da Câmara e um sino na praça.\n\nNas outras, faz a Mesa e põe-na perto do centro."),
      ("The Town Hall", "Half of new villages start with a Town Hall in the center: a hall with the Mayor's Desk and a bell in the square.\n\nIn other villages, craft the Desk and place it near the center.")),
    P("territory", None,
      ("Território", "A vila vai até 400 blocos ao redor da Mesa do Prefeito: casas longe do centro também contam.\n\nQuem administra o servidor muda o raio em config/tvilas.json."),
      ("Território", "A povoação vai até 400 blocos à volta da Mesa da Câmara: casas longe do centro também contam.\n\nQuem gere o servidor muda o raio em config/tvilas.json."),
      ("Territory", "The village reaches 400 blocks around the Mayor's Desk: houses far from the center count too.\n\nServer admins change the radius in config/tvilas.json.")),
    P("reborn", None,
      ("Vila vazia", "Uma vila sem aldeões renasce: coloque a Mesa do Prefeito e um Prefeito chega.\n\nSe houver camas livres, chega um morador por dia, até três. Depois a tesouraria paga mais."),
      ("Povoação vazia", "Uma povoação sem aldeões renasce: põe a Mesa da Câmara e chega um Presidente.\n\nSe houver camas livres, chega um morador por dia, até três. Depois a tesouraria paga mais."),
      ("Empty village", "A village with no villagers comes back: place the Mayor's Desk and a Mayor arrives.\n\nWith free beds, one resident arrives per day, up to three. Then the treasury pays for more.")),
    P("talk", None,
      ("Falar com ele", "Clique com o botão direito no Prefeito ou na Mesa: abre o quadro da Prefeitura, com o estado da vila, as missões e os terrenos.\n\nO Prefeito não faz trocas."),
      ("Falar com ele", "Clique direito no Presidente ou na Mesa: abre o quadro da Câmara, com o estado da povoação, as missões e os terrenos.\n\nO Presidente não faz trocas."),
      ("Talking", "Right-click the Mayor or the Desk: it opens the Town Hall board with the village status, missions and lots.\n\nThe Mayor does not trade.")),
    P("missions", None,
      ("Missões", "Há sempre três no quadro: trazer itens, colocar blocos na vila e defender a vila.\n\nAceite primeiro: só conta o que fizer depois. Os blocos precisam estar lá quando você entregar."),
      ("Missões", "Há sempre três: trazer itens, pôr blocos na povoação e defendê-la.\n\nAceita primeiro: só conta o que fizeres depois. Os blocos têm de lá estar quando entregares."),
      ("Missions", "There are always three: bring items, place blocks in the village and defend it.\n\nAccept first: only what you do afterwards counts. Blocks must still be there when you hand in.")),
    P("rewards", None,
      ("Recompensas", "Cada missão dá reputação nesta vila e é paga pela tesouraria: em CubCoins com o mod CubCoin, em esmeraldas sem ele.\n\nA reputação deixa os aldeões daqui um pouco mais baratos."),
      ("Recompensas", "Cada missão dá reputação nesta povoação e é paga pela tesouraria: em CubCoins com o mod CubCoin, em esmeraldas sem ele.\n\nA reputação deixa os aldeões daqui um pouco mais baratos."),
      ("Rewards", "Each mission gives reputation in this village and is paid by the treasury: in CubCoins with the CubCoin mod, in emeralds without it.\n\nReputation makes the villagers here a bit cheaper.")),
    P("defend", None,
      ("Defender", "A missão de defesa só conta numa invasão de verdade: os invasores dessa invasão derrotados dentro da vila. Monstros comuns não contam.\n\nDepois de entregar, o Prefeito põe outra missão no lugar."),
      ("Defender", "A missão de defesa só conta numa invasão a sério: os invasores dessa invasão derrotados dentro da povoação. Monstros comuns não contam.\n\nDepois de entregares, o Presidente põe outra missão no lugar."),
      ("Defending", "The defense mission only counts in a real raid: that raid's raiders defeated inside the village. Ordinary monsters do not count.\n\nAfter you hand in, the Mayor posts a new mission.")),
    P("lots", None,
      ("Terrenos", "No quadro, compre o terreno onde você está: 16 por 16 blocos com você no meio, do fundo do mundo até o céu.\n\nPaga em esmeraldas, ou em CubCoins com o CubCoin. Mais reputação, mais barato."),
      ("Terrenos", "No quadro, compra o terreno onde estás: 16 por 16 blocos contigo no meio, do fundo do mundo até ao céu.\n\nPaga-se em esmeraldas (ou CubCoins). Mais reputação, mais barato."),
      ("Lots", "On the board, buy the lot you stand on: 16 by 16 blocks with you in the middle, from the bottom of the world to the sky.\n\nPaid in emeralds, or CubCoins with CubCoin. More reputation, cheaper.")),
    P("lot_rules", None,
      ("Proteção", "No seu terreno, só você e os amigos quebram ou colocam blocos. Máquinas do mod Industrial também obedecem. Lava, fogo, explosões e pistões de fora não entram.\n\nA praça da Prefeitura não se vende."),
      ("Proteção", "No teu terreno, só tu e os amigos partem ou põem blocos. Máquinas do mod Industrial também obedecem. Lava, fogo, explosões e pistões de fora não entram.\n\nA praça da Câmara não se vende."),
      ("Protection", "On your lot, only you and your friends break or place blocks. Machines from the Industrial mod obey too. Lava, fire, explosions and pistons from outside stay out.\n\nThe Town Hall square is not for sale.")),
    P("friends", None,
      ("Amigos", "Dentro do seu terreno digite:\n/vila terreno amigo adicionar Nome\n\nPara tirar, troque adicionar por remover. Funciona mesmo com o amigo desligado."),
      ("Amigos", "Dentro do teu terreno escreve:\n/vila terreno amigo adicionar Nome\n\nPara tirar, troca adicionar por remover. Funciona mesmo com o amigo desligado."),
      ("Friends", "Inside your lot type:\n/vila terreno amigo adicionar Name\n\nTo take a friend out, use remover instead. It works even with the friend offline.")),
    P("levels", None,
      ("Nível da vila", "Aldeia, depois Vila, depois Cidade.\n\nVila: 8 aldeões e 100 de reputação somada.\nCidade: 16 aldeões e 400.\n\nO nível nunca desce."),
      ("Nível", "Aldeia, depois Vila, depois Cidade.\n\nVila: 8 aldeões e 100 de reputação somada.\nCidade: 16 aldeões e 400.\n\nO nível nunca desce."),
      ("Village level", "Hamlet, then Village, then Town.\n\nVillage: 8 villagers and 100 total reputation.\nTown: 16 villagers and 400.\n\nThe level never goes down.")),
    P("unlocks", None,
      ("O que desbloqueia", "Aldeia: 4 terrenos à venda.\nVila: 8 terrenos, missões maiores e 1 reforço do alarme.\nCidade: 16 terrenos, as missões mais bem pagas e 2 reforços.\n\nO terreno fica mais caro em vilas maiores."),
      ("O que desbloqueia", "Aldeia: 4 terrenos à venda.\nVila: 8 terrenos, missões maiores e 1 reforço do alarme.\nCidade: 16 terrenos, as missões mais bem pagas e 2 reforços.\n\nO terreno fica mais caro em povoações maiores."),
      ("Unlocks", "Hamlet: 4 lots for sale.\nVillage: 8 lots, bigger missions and 1 alarm upgrade.\nTown: 16 lots, the best paid missions and 2 upgrades.\n\nLots cost more in bigger villages.")),
    P("alarm", None,
      ("Alarme", "Durante uma invasão, ou com 3 ou mais monstros por perto, o Prefeito toca o sino e chama golens de ferro: 2 no máximo (mais com o reforço), um de cada vez.\n\nOs golens da vila ficam mais fortes."),
      ("Alarme", "Numa invasão, ou com 3 ou mais monstros perto, o Presidente toca o sino e chama golens de ferro: 2 no máximo (mais com o reforço), um de cada vez.\n\nOs golens ficam mais fortes."),
      ("Alarm", "In a raid, or with 3 or more monsters nearby, the Mayor rings the bell and calls iron golems: 2 at most (more with upgrades), one at a time.\n\nThe village golems get stronger.")),
    P("alarm_warning", None,
      ("⚠ Cuidado", "Os golens atacam quem ferir aldeões, como no jogo normal.\n\nOs golens chamados vão embora um minuto depois do alarme e não deixam ferro."),
      ("⚠ Cuidado", "Os golens atacam quem ferir aldeões, como no jogo normal.\n\nOs golens chamados vão-se embora um minuto depois do alarme e não largam ferro."),
      ("⚠ Careful", "Golems attack anyone who hurts villagers, as in the normal game.\n\nCalled golems leave a minute after the alarm and drop no iron.")),
    P("taxes", None,
      ("Impostos", "O Prefeito cobra 1 centavo por cama e por dia nas casas dos terrenos, e 2 por cento das trocas no território, com aldeões ou entre jogadores.\n\nSem CubCoin, cobra em esmeraldas quando a conta chega a uma."),
      ("Impostos", "O Presidente cobra 1 cêntimo por cama e por dia nas casas dos terrenos, e 2 por cento das trocas no território, com aldeões ou entre jogadores.\n\nSem CubCoin, cobra em esmeraldas quando a conta chega a uma."),
      ("Taxes", "The Mayor charges 1 cent per bed per day in lot houses, and 2 percent of trades in the territory, with villagers or between players.\n\nWithout CubCoin, it charges emeralds once the bill reaches one.")),
    P("treasury", None,
      ("Tesouraria", "Os impostos vão para a tesouraria. Uma vez por dia o Prefeito compra uma melhoria: moradores novos, um golem da vila ou o reforço do alarme.\n\nVeja a tesouraria e os gastos no quadro."),
      ("Tesouraria", "Os impostos vão para a tesouraria. Uma vez por dia o Presidente compra uma melhoria: moradores novos, um golem ou o reforço do alarme.\n\nVê a tesouraria e os gastos no quadro."),
      ("Treasury", "Taxes go to the treasury. Once a day the Mayor buys an upgrade: new residents, a village golem or an alarm upgrade.\n\nSee the treasury and spending on the board.")),
    P("debts", None,
      ("Dívidas", "Quem não tiver como pagar fica devendo. A dívida aparece no quadro e é cobrada quando houver dinheiro."),
      ("Dívidas", "Quem não tiver com que pagar fica a dever. A dívida aparece no quadro e cobra-se quando houver dinheiro."),
      ("Debts", "Anyone who cannot pay owes it. The debt shows on the board and is charged when there is money.")),
    P("market", None,
      ("Mercado", "Algumas vilas novas têm um Mercado: quatro bancas com os postos de trabalho dos vendedores novos.\n\nAldeões sem emprego ficam vendendo lá."),
      ("Mercado", "Algumas povoações novas têm um Mercado: quatro bancas com os postos de trabalho dos vendedores novos.\n\nAldeões sem emprego ficam lá a vender."),
      ("Market", "Some new villages have a Market: four stalls with the job sites of the new traders.\n\nJobless villagers take the stalls.")),
    P("baker", "baker_oven",
      ("Padeiro", "Compra trigo, açúcar, ovos e cacau. Vende pão, biscoitos, tortas e bolo."),
      ("Padeiro", "Compra trigo, açúcar, ovos e cacau. Vende pão, bolachas, tartes e bolo."),
      ("Baker", "Buys wheat, sugar, eggs and cocoa. Sells bread, cookies, pies and cake.")),
    P("florist", "flower_stall",
      ("Florista", "Vende flores de todo tipo. No último nível, sementes raras e flor de esporos."),
      ("Florista", "Vende flores de todo o tipo. No último nível, sementes raras e flor de esporos."),
      ("Florist", "Sells all kinds of flowers. At the top level, rare seeds and spore blossom.")),
    P("innkeeper", "tavern_counter",
      ("Taberneiro", "Sopas, batatas, leite, camas para a pousada e, no fim, maçã dourada e um disco."),
      ("Taberneiro", "Sopas, batatas, leite, camas para a pousada e, no fim, maçã dourada e um disco."),
      ("Innkeeper", "Soups, potatoes, milk, beds for the inn and, at the end, a golden apple and a disc.")),
    P("beekeeper", "apiary_bench",
      ("Apicultor", "Mel, favos, velas e colmeias. No último nível vende um ninho de abelhas."),
      ("Apicultor", "Mel, favos, velas e colmeias. No último nível vende um ninho de abelhas."),
      ("Beekeeper", "Honey, honeycomb, candles and hives. At the top level it sells a bee nest.")),
    # 0.2.0
    P("guide_book", "guide",
      ("Este livro", "Livro e trigo, juntos na mesa de trabalho, dão outro Guia das Vilas."),
      ("Este livro", "Livro e trigo, juntos na mesa de trabalho, dão outro Guia das Vilas."),
      ("This book", "A book and wheat, together on the crafting table, make another Villages Guide.")),
    P("news", None,
      ("Novidades", "O Prefeito avisa no chat, numa linha, quem está no território: obras, mercador, impostos.\n\nEx.: 🏗 A vila está construindo a muralha."),
      ("Novidades", "O Presidente avisa no chat, numa linha, quem está no território: obras, mercador, impostos.\n\nEx.: 🏗 A povoação está a construir a muralha."),
      ("News", "The Mayor tells everyone in the territory, in one chat line: works, peddler, taxes.\n\nE.g.: 🏗 The village is building the town wall.")),
    P("builders", None,
      ("Construtores", "As obras pagas pela tesouraria são feitas por um Construtor: ele vai ao local e coloca os blocos um a um, com pó e som.\n\nObras: golem, muralha e iluminação."),
      ("Construtores", "As obras pagas pela tesouraria são feitas por um Construtor: ele vai ao sítio e põe os blocos um a um, com pó e som.\n\nObras: golem, muralha e iluminação."),
      ("Builders", "Works paid by the treasury are done by a Builder: he walks to the spot and places the blocks one by one, with dust and sound.\n\nWorks: golem, wall and lights.")),
    P("builders_night", None,
      ("De noite", "Se ninguém estiver por perto para ver, a obra espera.\n\nO que falta aparece depois da noite, quando alguém dorme ou o dia passa."),
      ("De noite", "Se ninguém estiver perto a ver, a obra espera.\n\nO que falta aparece depois da noite, quando alguém dorme ou o dia passa."),
      ("At night", "If nobody is near to watch, the work waits.\n\nWhat is left appears after the night, when someone sleeps or the day passes.")),
    P("golem_build", None,
      ("Golem montado", "Quando a vila compra um golem, o Construtor monta quatro blocos de ferro e uma abóbora. O golem acorda sozinho."),
      ("Golem montado", "Quando a povoação compra um golem, o Construtor monta quatro blocos de ferro e uma abóbora. O golem acorda sozinho."),
      ("Built golem", "When the village buys a golem, the Builder stacks four iron blocks and a pumpkin. The golem wakes up by itself.")),
    P("wall", None,
      ("Muralha", "Quando a vila vira Cidade, o Prefeito encomenda uma muralha com torres em volta do centro.\n\nÉ construída aos poucos e paga pela tesouraria, bloco a bloco."),
      ("Muralha", "Quando a povoação chega a Cidade, o Presidente encomenda uma muralha com torres à volta do centro.\n\nÉ feita aos poucos e paga pela tesouraria, bloco a bloco."),
      ("Town wall", "When the village becomes a Town, the Mayor orders a wall with towers around the center.\n\nIt is built little by little, paid by the treasury block by block.")),
    P("wall_safety", None,
      ("Muralha segura", "A muralha nunca quebra nem troca blocos de jogadores nem nada dentro de terrenos. Só ocupa ar, grama, flores e neve.\n\nQuando encontra algo, desvia ou deixa um vão. As ruas ficam como portões."),
      ("Muralha segura", "A muralha nunca parte nem troca blocos de jogadores nem nada dentro de terrenos. Só ocupa ar, erva, flores e neve.\n\nQuando encontra algo, desvia ou deixa um vão. As ruas ficam como portões."),
      ("Safe wall", "The wall never breaks or replaces player blocks, nor anything inside lots. It only takes air, grass, flowers and snow.\n\nIt goes around things or leaves a gap. Streets become gates.")),
    P("wall_cancel", None,
      ("Cancelar", "Quem tem terreno ou reputação na vila pode cancelar a muralha no quadro. O que já foi feito fica.\n\nNo quadro também se retoma."),
      ("Cancelar", "Quem tem terreno ou reputação na povoação pode cancelar a muralha no quadro. O que já está feito fica.\n\nNo quadro também se retoma."),
      ("Cancel", "Anyone with a lot or reputation in the village can cancel the wall on the board. What was built stays.\n\nIt can be resumed there too.")),
    P("lighting", None,
      ("Iluminação", "Numa Vila, a tesouraria também compra iluminação pública: o Construtor coloca postes ao lado das ruas.\n\nSem o mod Industrial são postes com lanterna."),
      ("Iluminação", "Numa Vila, a tesouraria também compra iluminação pública: o Construtor põe postes ao lado das ruas.\n\nSem o mod Industrial são postes com lanterna."),
      ("Street lights", "In a Village, the treasury also buys street lights: the Builder sets poles beside the streets.\n\nWithout the Industrial mod they are lantern posts.")),
    P("lighting_industrial", None,
      ("Postes elétricos", "Com o Industrial são postes de verdade (madeira do bioma, ferro no deserto), ligados por fio aéreo.\n\nNa Prefeitura ficam 2 painéis solares e uma bateria: acendem à noite."),
      ("Postes elétricos", "Com o Industrial são postes a sério (madeira do bioma, ferro no deserto), ligados por fio aéreo.\n\nNa Câmara ficam 2 painéis solares e uma bateria: acendem à noite."),
      ("Industrial poles", "With Industrial they are real lamp posts (biome wood, iron in the desert), joined by aerial wire.\n\nThe Town Hall gets 2 solar panels and a battery: they light at night.")),
    P("tax_changes", None,
      ("Imposto variável", "A taxa das trocas sobe um pouco a cada obra paga, até um teto (5 por cento).\n\nQuando a tesouraria está cheia, o Prefeito baixa de novo. O quadro mostra o motivo."),
      ("Imposto variável", "A taxa das trocas sobe um pouco a cada obra paga, até um teto (5 por cento).\n\nQuando a tesouraria está cheia, o Presidente baixa outra vez. O quadro mostra porquê."),
      ("Changing tax", "The trade tax rises a little with each paid work, up to a ceiling (5 percent).\n\nWhen the treasury is full, the Mayor lowers it again. The board shows why.")),
    P("freeze", None,
      ("Terreno congelado", "Quem deve há alguns dias fica com o terreno congelado: não pode construir nem quebrar lá até pagar.\n\nPistões e ejetores param lá. Nada é apagado. Um dia antes, o Prefeito avisa."),
      ("Terreno congelado", "Quem deve há alguns dias fica com o terreno congelado: não pode construir nem partir lá até pagar.\n\nPistões e ejetores param lá. Nada é apagado. Um dia antes, o Presidente avisa."),
      ("Frozen lot", "Owing for a few days freezes your lot: no building or breaking there until you pay.\n\nPistons and dispensers stop there. Nothing is removed. The Mayor warns you a day before.")),
    P("pay_debt", None,
      ("Pagar a dívida", "No quadro, o botão Pagar dívida paga tudo de uma vez e o terreno volta ao normal.\n\nSem CubCoin, paga em esmeraldas."),
      ("Pagar a dívida", "No quadro, o botão Pagar dívida paga tudo de uma vez e o terreno volta ao normal.\n\nSem CubCoin, paga-se em esmeraldas."),
      ("Paying debt", "On the board, Pay debt pays it all at once and the lot is back to normal.\n\nWithout CubCoin, it is paid in emeralds.")),
    P("merchant", None,
      ("Mercador da Vila", "De tempos em tempos chega à praça um mercador com um burro de carga. Fica uns dias e o Prefeito avisa quando chega e quando vai embora."),
      ("Mercador", "De tempos a tempos chega à praça um mercador com um burro de carga. Fica uns dias e o Presidente avisa quando chega e quando parte."),
      ("Village Peddler", "Every few days a peddler with a pack donkey comes to the square. He stays a few days and the Mayor tells you when he comes and goes.")),
    P("merchant_diff", None,
      ("Não é o ambulante", "O Mercador Ambulante aparece por acaso e some depressa.\n\nO da vila vem por calendário, fica na praça e vende coisas raras: etiquetas, selas, discos, chaves e itens dos mods."),
      ("Não é o ambulante", "O Vendedor Ambulante aparece ao acaso e desaparece depressa.\n\nO da povoação vem por calendário, fica na praça e vende coisas raras: etiquetas, selas, discos, chaves e itens dos mods."),
      ("Not the wanderer", "The Wandering Trader shows up by chance and leaves fast.\n\nThe village peddler comes on schedule, stays in the square and sells rare things: name tags, saddles, discs, keys and mod items.")),
    P("tavern", None,
      ("Taverna", "Vilas novas podem ter uma Taverna e pousada: balcão do Taberneiro, mesas, cadeiras e camas para dormir."),
      ("Taberna", "Povoações novas podem ter uma Taberna e estalagem: balcão do Taberneiro, mesas, cadeiras e camas para dormir."),
      ("Tavern", "New villages may have a Tavern and inn: the Innkeeper's counter, tables, chairs and beds to sleep in.")),
    P("plaza", None,
      ("Praça", "Algumas vilas novas ganham uma praça com fonte, poço novo, bancos e uma mesa de café."),
      ("Praça", "Algumas povoações novas ganham uma praça com fonte, poço novo, bancos e uma mesa de esplanada."),
      ("Square", "Some new villages get a square with a fountain, a new well, benches and a café table.")),
    P("furniture", None,
      ("Com o Móveis", "Com o mod Móveis instalado, a Taverna e a Praça já nascem mobiliadas: cadeiras, mesas, lustres e cama de casal, envernizados.\n\nSem ele, ficam com blocos do jogo."),
      ("Com o Móveis", "Com o mod Móveis instalado, a Taberna e a Praça já nascem mobiladas: cadeiras, mesas, lustres e cama de casal, envernizados.\n\nSem ele, ficam com blocos do jogo."),
      ("With Móveis", "With the Móveis mod installed, the Tavern and the Square come furnished: chairs, tables, chandeliers and a double bed, varnished.\n\nWithout it, they use game blocks.")),
    P("mod_vendors", None,
      ("Mais vendedores", "Mais quatro vendedores, cada um só com o seu mod: Mecânico (Industrial), Ferreiro de Ligas (Forja), Marceneiro (Móveis) e Cambista (CubCoin)."),
      ("Mais vendedores", "Mais quatro vendedores, cada um só com o seu mod: Mecânico (Industrial), Ferreiro de Ligas (Forja), Marceneiro (Móveis) e Cambista (CubCoin)."),
      ("Mod traders", "Four more traders, each only with its mod: Mechanic (Industrial), Alloy Smith (Forja), Carpenter (Móveis) and Money Changer (CubCoin).")),
    P("mechanic", "mechanic_bench",
      ("Mecânico", "Só com o Industrial. Fio de cobre, cabos, canos, conectores e melhorias para as máquinas."),
      ("Mecânico", "Só com o Industrial. Fio de cobre, cabos, canos, conectores e melhorias para as máquinas."),
      ("Mechanic", "Industrial only. Copper wire, cables, pipes, connectors and machine upgrades."), "tindustrial"),
    P("alloy_smith", "alloy_anvil",
      ("Ferreiro de Ligas", "Só com a Forja. Bronze, aço, cromo, titânio, aço inox e peças de ferramenta."),
      ("Ferreiro de Ligas", "Só com a Forja. Bronze, aço, crómio, titânio, aço inox e peças de ferramenta."),
      ("Alloy Smith", "Forja only. Bronze, steel, chromium, titanium, stainless steel and tool parts."), "tforja"),
    P("carpenter", "carpenter_bench",
      ("Marceneiro", "Só com o Móveis. Cadeiras, mesas, sofás, lustres e cama de casal, já envernizados, e pincéis de verniz."),
      ("Marceneiro", "Só com o Móveis. Cadeiras, mesas, sofás, lustres e cama de casal, já envernizados, e pincéis de verniz."),
      ("Carpenter", "Móveis only. Chairs, tables, sofas, chandeliers and a double bed, varnished, and varnish brushes."), "tmoveis"),
    P("money_changer", "changer_counter",
      ("Cambista", "Só com o CubCoin. Troca esmeraldas por notas e notas por esmeraldas, ao preço do Balcão de Câmbio, sem desconto."),
      ("Cambista", "Só com o CubCoin. Troca esmeraldas por notas e notas por esmeraldas, ao preço do Balcão de Câmbio, sem desconto."),
      ("Money Changer", "CubCoin only. Swaps emeralds for notes and back at the Exchange Counter price, no discounts."), "cubcoin"),
    # 0.3.0
    P("coin_trades", None,
      ("Trocas em CubCoin", "Com o mod CubCoin, todos os aldeões compram e vendem em notas e moedas em vez de esmeraldas: os do jogo, os deste mod, o ambulante e o mercador.\n\nO preço é o do Balcão de Câmbio: uma esmeralda vale 40 CB."),
      ("Trocas em CubCoin", "Com o mod CubCoin, todos os aldeões compram e vendem em notas e moedas em vez de esmeraldas: os do jogo, os deste mod, o ambulante e o mercador.\n\nO preço é o do Balcão de Câmbio: uma esmeralda vale 40 CB."),
      ("CubCoin trades", "With the CubCoin mod, every villager buys and sells in notes and coins instead of emeralds: the game's, this mod's and the traders.\n\nThe price is the Exchange Counter's: one emerald is worth 40 CB."), "cubcoin"),
    P("coin_change", None,
      ("Troco", "Coloque nas casas da troca qualquer nota ou moeda que chegue ao preço. O aldeão fica com o preço e o troco vai para o seu inventário.\n\nEscolher a troca na lista já coloca o dinheiro."),
      ("Troco", "Põe nas casas da troca qualquer nota ou moeda que chegue ao preço. O aldeão fica com o preço e o troco vai para o teu inventário.\n\nEscolher a troca na lista já põe o dinheiro."),
      ("Change", "Put any notes or coins that reach the price in the trade slots. The villager keeps the price and the change goes to your inventory.\n\nPicking the trade in the list already puts the money in."), "cubcoin"),
    P("coin_more", None,
      ("Tudo em CubCoin", "Com o CubCoin, os terrenos, os impostos e as missões também são em CubCoins. O Cambista continua trocando esmeraldas por notas.\n\nSem o CubCoin, tudo volta para as esmeraldas."),
      ("Tudo em CubCoin", "Com o CubCoin, os terrenos, os impostos e as missões também são em CubCoins. O Cambista continua a trocar esmeraldas por notas.\n\nSem o CubCoin, tudo volta às esmeraldas."),
      ("All in CubCoin", "With CubCoin, lots, taxes and missions are in CubCoins too. The Money Changer still swaps emeralds for notes.\n\nWithout CubCoin, it all goes back to emeralds."), "cubcoin"),
    P("mission_limits", None,
      ("Limites", "A cada 4 dias o quadro troca todas as missões. Cada missão vale uma vez por jogador nesse tempo; o quadro mostra quanto falta.\n\nA vila paga até 400 CB por dia: primeiro do fundo das missões, depois da tesouraria."),
      ("Limites", "A cada 4 dias o quadro troca todas as missões. Cada missão vale uma vez por jogador nesse tempo.\n\nA povoação paga até 400 CB por dia: primeiro do fundo das missões, depois da tesouraria."),
      ("Limits", "Every 4 days the board swaps all missions. Each mission counts once per player in that time; the board shows how long is left.\n\nThe village pays up to 400 CB a day: first from the mission fund, then the treasury.")),
    P("mission_fund", None,
      ("Fundo das missões", "Uma vila de verdade (do jogo, ou com 5 aldeões ou mais) recebe uma vez 200 CB no fundo das missões e ganha 50 CB por dia, até 400.\n\nUma Mesa no mato não paga missões."),
      ("Fundo das missões", "Uma povoação a sério (do jogo, ou com 5 aldeões ou mais) recebe uma vez 200 CB no fundo das missões e ganha 50 CB por dia, até 400.\n\nUma Mesa no mato não paga missões."),
      ("Mission fund", "A real village (generated, or with 5+ villagers) gets 200 CB once in the mission fund and gains 50 CB a day, up to 400.\n\nA Desk out in the wild pays no missions.")),
    P("villager_requests", None,
      ("Pedidos", "Em cada lote, um em cada quatro aldeões com profissão faz um pedido e mostra um «!» na cabeça: o ferreiro quer ferro, o agricultor trigo ou batatas, o bibliotecário papel ou livros."),
      ("Pedidos", "Em cada lote, um em cada quatro aldeões com profissão faz um pedido e mostra um «!» na cabeça: o ferreiro quer ferro, o agricultor trigo ou batatas, o bibliotecário papel ou livros."),
      ("Villager requests", "In each batch, one in four villagers with a job makes a request and shows a «!» over the head: the smith wants iron, the farmer wheat or potatoes, the librarian paper or books.")),
    P("request_screen", None,
      ("O pedido", "Clique no aldeão com «!»: abre o pedido, com o que pede e quanto paga. Entregar paga da vila, uma vez por jogador.\n\nShift+clique, ou o botão Trocar, abre as trocas de sempre."),
      ("O pedido", "Clica no aldeão com «!»: abre o pedido, com o que pede e quanto paga. Entregar paga da povoação, uma vez por jogador.\n\nShift+clique, ou o botão Trocar, abre as trocas de sempre."),
      ("The request", "Click the villager with «!»: the request opens, with what it wants and what it pays. Hand in pays from the village, once per player.\n\nShift+click, or the Trade button, opens the usual trades.")),
    P("request_money", None,
      ("Só com dinheiro", "O «!» só aparece se a vila tiver dinheiro para pagar o pedido. Os pedidos mudam junto com o quadro, a cada 4 dias, e pagam no máximo um pouco acima do mercado."),
      ("Só com dinheiro", "O «!» só aparece se a povoação tiver dinheiro para pagar o pedido. Os pedidos mudam junto com o quadro, a cada 4 dias, e pagam no máximo um pouco acima do mercado."),
      ("Only with money", "The «!» only shows if the village can pay for the request. Requests change with the board, every 4 days, and pay at most a little above the market.")),
    P("growth", None,
      ("A vila cresce", "Cada missão ou pedido feito dá prestígio à vila. A cada 3, chega um morador novo, a pé, pela borda da vila.\n\nO quadro mostra quanto falta. Há um teto de 40 moradores."),
      ("Crescer", "Cada missão ou pedido feito dá prestígio à povoação. A cada 3, chega um morador novo, a pé, pela borda da povoação.\n\nO quadro mostra quanto falta. Há um teto de 40 moradores."),
      ("The village grows", "Each mission or request done gives the village prestige. Every 3, a new resident walks in from the edge of the village.\n\nThe board shows how far it is. There is a cap of 40 residents.")),
    P("growth_house", None,
      ("Casa nova", "O morador novo só vem se houver cama livre. Sem cama, o Prefeito encomenda uma casa simples, paga pela tesouraria, num lugar livre, e o Construtor a faz."),
      ("Casa nova", "O morador novo só vem se houver cama livre. Sem cama, o Presidente encomenda uma casa simples, paga pela tesouraria, num sítio livre, e o Construtor fá-la."),
      ("New house", "The new resident only comes if there is a free bed. Without one, the Mayor orders a simple house, paid by the treasury, on a free spot, and the Builder makes it.")),
    P("mission_fair", None,
      ("Preço justo", "Nenhuma missão paga muito mais do que os itens valem no mercado, nem mais do que custa comprá-los dos aldeões.\n\nPedregulho já não vale missão."),
      ("Preço justo", "Nenhuma missão paga muito mais do que os itens valem no mercado, nem mais do que custa comprá-los aos aldeões.\n\nPedregulho já não vale missão."),
      ("Fair price", "No mission pays much more than the items are worth on the market, nor more than buying them from villagers.\n\nCobblestone no longer makes a mission.")),
    P("decor_village", None,
      ("Decoração da vila", "Os blocos que valeram uma missão de decorar passam a ser da vila: se você quebrar, não sobra nada para levar."),
      ("Decoração", "Os blocos que valeram uma missão de decorar passam a ser da povoação: se os partires, não sobra nada para levar."),
      ("Village decor", "Blocks that paid for a decorating mission now belong to the village: break one and nothing is left to take.")),
    P("old_villages", None,
      ("Vilas antigas", "Ponha a Mesa numa vila sem Prefeitura, Mercado, Taverna ou Praça: o Prefeito manda construir o que falta, um de cada vez, pago pela tesouraria.\n\nO quadro mostra o que ainda falta."),
      ("Povoações antigas", "Põe a Mesa numa povoação sem Câmara, Mercado, Taverna ou Praça: o Presidente manda construir o que falta, um de cada vez, pago pela tesouraria.\n\nO quadro mostra o que ainda falta."),
      ("Old villages", "Place the Desk in a village with no Town Hall, Market, Tavern or Plaza: the Mayor orders what is missing, one at a time, paid by the treasury.\n\nThe board shows what is still missing.")),
    P("civic_site", None,
      ("Onde constrói", "O Construtor escolhe um lugar livre perto do centro, em chão natural. Nunca em cima de casas, ruas ou terrenos de jogadores.\n\nÀ vista ele coloca bloco a bloco; de noite a obra avança sozinha."),
      ("Onde constrói", "O Construtor escolhe um sítio livre perto do centro, em chão natural. Nunca em cima de casas, ruas ou terrenos de jogadores.\n\nÀ vista põe bloco a bloco; de noite a obra avança sozinha."),
      ("Where it builds", "The Builder picks a free spot near the center, on natural ground. Never on houses, streets or players' lots.\n\nWhile watched it places block by block; at night the work moves on by itself.")),
    P("civic_area", None,
      ("Área cívica", "A praça da Mesa, a Prefeitura, a Praça, o Mercado e a Taverna são da vila. Só quem colocou a Mesa e os amigos dele mexem lá:\n/vila civica amigo adicionar Nome\n\nExplosões, lava e pistões de fora não entram."),
      ("Área cívica", "A praça da Mesa, a Câmara, a Praça, o Mercado e a Taverna são da povoação. Só quem pôs a Mesa e os amigos dele mexem lá:\n/vila civica amigo adicionar Nome\n\nExplosões, lava e pistões de fora não entram."),
      ("Civic area", "The Desk square, Town Hall, Plaza, Market and Tavern belong to the village. Only the Desk's owner and friends build there:\n/vila civica amigo adicionar Name\n\nOutside explosions and lava stay out.")),
    P("lot_animals", None,
      ("Bichos no terreno", "Os animais, aldeões e decorações dentro do seu terreno ficam protegidos: quem não é amigo não bate, não prende com laço, não leva no balde nem no barco.\n\nMonstros não são protegidos."),
      ("Bichos no terreno", "Os animais, aldeões e decorações dentro do teu terreno ficam protegidos: quem não é amigo não lhes bate, não os prende com trela, não os leva no balde nem no barco.\n\nMonstros não são protegidos."),
      ("Animals on lots", "Animals, villagers and decorations inside your lot are protected: non-friends cannot hit them, leash them, or carry them off in a bucket or a boat.\n\nMonsters are not protected.")),
    P("mayor_taken", None,
      ("Prefeito levado", "Se alguém levar o Prefeito embora, clique na Mesa: se ele estiver num lugar carregado, volta para a Prefeitura.\n\nSumido há 3 dias, chega um novo e o antigo deixa de ser Prefeito."),
      ("Presidente levado", "Se alguém levar o Presidente embora, clica na Mesa: se ele estiver num sítio carregado, volta à Câmara.\n\nSumido há 3 dias, chega um novo e o antigo deixa de ser Presidente."),
      ("Mayor taken", "If someone carries the Mayor away, click the Desk: if he is somewhere loaded, he comes back to the Town Hall.\n\nGone for 3 days, a new one comes and the old one stops being Mayor.")),
    P("commands", None,
      ("Comandos", "/vila abre o quadro\n/vila missao aceitar 1\n/vila missao entregar 1\n/vila terreno comprar\n/vila terreno info\n/vila divida pagar\n/vila muralha cancelar\n/vila reuniao\n/vila eleicao\n/vila votar 1"),
      ("Comandos", "/vila abre o quadro\n/vila missao aceitar 1\n/vila missao entregar 1\n/vila terreno comprar\n/vila terreno info\n/vila divida pagar\n/vila muralha cancelar\n/vila reuniao\n/vila eleicao\n/vila votar 1"),
      ("Commands", "/vila opens the board\n/vila missao aceitar 1\n/vila missao entregar 1\n/vila terreno comprar\n/vila terreno info\n/vila divida pagar\n/vila muralha cancelar\n/vila reuniao\n/vila eleicao\n/vila votar 1")),
]


import life_040 as _life  # noqa: E402  (0.4.0: aldeões vivos)

GUIDE[-1:-1] = _life.pages(P)
import life_050 as _life5  # noqa: E402  (0.5.0: Carteiro, mercado, eleições, pescador, crianças, festa, ajudante)

GUIDE[-1:-1] = _life5.pages(P)


def _grid(r):
    if "ingredients" in r:  # sem forma: em fila na primeira linha
        ing = r["ingredients"] + [None] * (3 - len(r["ingredients"]))
        return [ing[:3], [None] * 3, [None] * 3]
    rows = [row.ljust(3) for row in r["pattern"]] + ["   "] * (3 - len(r["pattern"]))
    return [[None if ch == " " else r["key"][ch] for ch in row[:3]] for row in rows[:3]]


def gen_guide():
    keys = [g["key"] for g in GUIDE]
    assert len(keys) == len(set(keys))
    for g in GUIDE:
        for part, i in (("title", 0), ("text", 1)):
            tr(f"guide.{MOD}.{g['key']}.{part}", g["br"][i], g["pt"][i], g["en"][i])
    book = [f"§l{g['br'][0]}§r\n\n{g['br'][1]}" for g in GUIDE]
    # 0.5.0: o livro escrito vai até 100 páginas — as primeiras 98, os comandos e «E o resto?»; tudo o resto fica no separador Guias
    if len(book) > 99:
        book = book[:98] + [book[-1]]
    # 0.3.0: como os livros dos outros mods, o último manda para o separador Guias (lá estão as receitas)
    book.append("§lE o resto?§r\n\nTudo o resto está no separador Guias do Receitário (botão no inventário): a receita de cada "
                "bloco, uma por página, e as páginas dos mods que estão instalados.")
    assert len(book) <= 100
    guide = {"type": "minecraft:crafting_shapeless", "category": "misc",
             "ingredients": ["minecraft:book", "minecraft:wheat"],
             "result": {"id": "minecraft:written_book", "components": {"minecraft:written_book_content": {
                 "title": {"raw": "Guia das Vilas"}, "author": "Tcordeiro",
                 "pages": [{"raw": p} for p in book], "resolved": True}}}}
    write(f"data/{MOD}/recipe/guide.json", guide)
    RECIPES["guide"] = guide
    advancement("guide", ["minecraft:wheat"])

    def js(v):
        return "null" if v is None else json.dumps(v, ensure_ascii=False)

    consts, lines = [], []
    for g in GUIDE:
        req = "null" if not g["requires"] else f'"{g["requires"]}"'
        if g["recipe"] is None:
            lines.append(f'new Page("{g["key"]}", null, List.of(), {req})')
            continue
        r = RECIPES[g["recipe"]]
        const = g["recipe"].upper()
        rows = ", ".join("{" + ", ".join(js(c) for c in row) + "}" for row in _grid(r))
        consts.append(f'\tstatic final Recipe {const} = new Recipe("{MOD}:{g["recipe"]}", "{r["result"]["id"]}", {r["result"].get("count", 1)},\n'
                      f'\t\t\tnew String[][] {{{rows}}});')
        lines.append(f'new Page("{g["key"]}", null, List.of({const}), {req})')
    java = f"""package com.tcordeiro.vilas.guide;

import java.util.List;

/**
 * Páginas do guia das Vilas, lidas pelo separador «Guias» do Receitário por reflexão ({{@code PAGES}}; cada página com
 * {{@code key()}}, {{@code image()}} e {{@code recipes()}}; cada receita com {{@code result()}}, {{@code count()}} e {{@code cell(linha, coluna)}}).
 * Os textos estão nas traduções: guide.{MOD}.&lt;página&gt;.title / .text.
 * <p>
 * GERADO por tools/gen_data.py: não editar à mão.
 */
public final class VilasGuide {{
	private VilasGuide() {{}}

	/** Receita numa página: id, resultado, quantidade e 3 linhas de 3 casas (id de item, «#tag» ou null). */
	public record Recipe(String id, String result, int count, String[][] grid) {{
		public String cell(int row, int col) {{
			return row >= 0 && row < grid.length && col >= 0 && col < grid[row].length ? grid[row][col] : null;
		}}
	}}

	/**
	 * Página: chave de tradução, imagem opcional (textures/gui/guide/&lt;img&gt;.png), receitas e o mod de que depende (null =
	 * sempre). As páginas de um mod que não está instalado não entram em {{@link #PAGES}}.
	 */
	public record Page(String key, String image, List<Recipe> recipes, String requires) {{
		public boolean available() {{
			return requires == null || net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded(requires);
		}}
	}}

{chr(10).join(consts)}

	/** Todas as páginas (o livro escrito leva-as todas; as dos mods dizem «só com o mod»). */
	public static final List<Page> ALL = List.of(
			{(","+chr(10)+chr(9)+chr(9)+chr(9)).join(lines)});

	/** As páginas do separador «Guias» do Receitário: sem as dos mods que não estão instalados. */
	public static final List<Page> PAGES = ALL.stream().filter(Page::available).toList();
}}
"""
    path = os.path.join(JAVA, "guide", "VilasGuide.java")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(java)


# ------------------------------------------------------------------ verificações e escrita

def check_names():
    """Nenhum nome de bloco/profissão repete um nome do jogo (nem entre si) — ex.: «Açougueiro» já é o Butcher vanilla."""
    for lg in TR:
        vanilla = {v for k, v in LANG[lg].items() if k.startswith(("block.", "item.", "entity."))}
        names = [v for k, v in TR[lg].items() if k.startswith(("block.", "entity."))]
        assert len(names) == len(set(names)), f"{lg}: nomes repetidos"
        clash = [n for n in names if n in vanilla]
        assert not clash, f"{lg}: nomes iguais aos do jogo: {clash}"
        assert set(TR[lg]) == set(TR["br"]), f"{lg}: chaves diferentes das de pt_br"


def check_missions():
    """Os itens/blocos das missões (logic/MissionRules.java) existem no 26.3."""
    src = open(os.path.join(JAVA, "logic", "MissionRules.java")).read()
    for i in re.findall(r'"(minecraft:[a-z_]+)"', src):
        assert item_exists(i), f"missão com item inexistente: {i}"


def check_merchant_and_news():
    """O stock do mercador (village/TravelingMerchant.java) existe; os anúncios do Prefeito são de UMA linha."""
    src = open(os.path.join(JAVA, "village", "TravelingMerchant.java")).read()
    ids = re.findall(r'new Stock\("([a-z_]+:[a-z0-9_]+)"', src)
    assert len(ids) >= 10, ids
    for i in ids:
        assert item_exists(i), f"mercador com item inexistente: {i}"
    for lg in TR:
        for k, v in TR[lg].items():
            if k.startswith("tvilas.news."):
                assert "\n" not in v and len(v) <= 90, f"{lg}: anúncio de mais de uma linha: {k}"


def gen_lang():
    for lg, code in (("br", "pt_br"), ("pt", "pt_pt"), ("en", "en_us")):
        write(f"assets/{MOD}/lang/{code}.json", dict(sorted(TR[lg].items())))


def clean_stale():
    managed = [f"assets/{MOD}/blockstates", f"assets/{MOD}/items", f"assets/{MOD}/models/block", f"data/{MOD}/loot_table",
               f"data/{MOD}/recipe", f"data/{MOD}/advancement", f"data/{MOD}/villager_trade", f"data/{MOD}/trade_set"]
    for d in managed:
        for dp, _, fs in os.walk(os.path.join(RES, d)):
            for fn in fs:
                rel = os.path.relpath(os.path.join(dp, fn), RES)
                if fn.endswith(".json") and rel not in written:
                    os.remove(os.path.join(dp, fn))
                    print("  apagado:", rel)


def main():
    gen_models()
    gen_recipes()
    gen_trades()
    gen_lang_names()
    gen_guide()
    check_names()
    check_missions()
    check_merchant_and_news()
    gen_lang()
    clean_stale()
    n_trades = sum(len(t) for lv in (dict(TRADES) | MOD_TRADES).values() for t in lv.values())
    print(f"{len(BLOCKS)} blocos, {len(PROFESSIONS)} profissões, {n_trades} trocas, {len(RECIPES)} receitas, "
          f"{len(GUIDE)} páginas no guia, {len(written)} ficheiros")


if __name__ == "__main__":
    main()
