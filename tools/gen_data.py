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


def write(rel, data):
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
    if ns != "minecraft":
        return True
    try:
        zjar.getinfo(f"assets/minecraft/items/{path}.json")
        return True
    except KeyError:
        return False


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
}

# profissão: (pt_br, pt_pt, en, posto)
PROFESSIONS = {
    "mayor": ("Prefeito", "Presidente da Câmara", "Mayor", "mayor_desk"),
    "baker": ("Padeiro", "Padeiro", "Baker", "baker_oven"),
    "florist": ("Florista", "Florista", "Florist", "flower_stall"),
    "innkeeper": ("Taberneiro", "Taberneiro", "Innkeeper", "tavern_counter"),
    "beekeeper": ("Apicultor", "Apicultor", "Beekeeper", "apiary_bench"),
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
            "random_sequence": f"{MOD}:blocks/{bid}"})
    for tool in ("axe", "pickaxe"):
        write(f"data/minecraft/tags/block/mineable/{tool}.json",
              {"replace": False, "values": [f"{MOD}:{b}" for b, v in BLOCKS.items() if v[4] == tool]})
    write("data/minecraft/tags/point_of_interest_type/acquirable_job_site.json",
          {"replace": False, "values": [f"{MOD}:{p}" for p in PROFESSIONS]})


# ------------------------------------------------------------------ receitas

RECIPES = {}


def advancement(recipe, items):
    crit = {"has_the_recipe": {"trigger": "minecraft:recipe_unlocked", "conditions": {"recipes": f"{MOD}:{recipe}"}}}
    for i, it in enumerate(items):
        crit[f"has_item_{i}"] = {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": it}]}}
    write(f"data/{MOD}/advancement/recipes/{recipe}.json", {"parent": "minecraft:recipes/root", "criteria": crit,
                                                            "requirements": [list(crit)], "rewards": {"recipes": [f"{MOD}:{recipe}"]}})


def shaped(name, pattern, key, count=1, unlock=None):
    r = {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern, "key": key, "result": {"id": f"{MOD}:{name}"}}
    if count > 1:
        r["result"]["count"] = count
    for v in key.values():
        assert v.startswith("#") or item_exists(v), v
    write(f"data/{MOD}/recipe/{name}.json", r)
    RECIPES[name] = r
    advancement(name, unlock or sorted(set(key.values())))


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


def gen_trades():
    for prof, levels in TRADES.items():
        for lvl, trades in levels.items():
            ids = []
            for kind, item, count, emeralds, uses in trades:
                assert item_exists(item), f"item inexistente no 26.3: {item}"
                name = f"{item.split(':')[1]}_emerald" if kind == "buy" else f"emerald_{item.split(':')[1]}"
                rel = f"{prof}/{lvl}/{name}"
                if kind == "buy":
                    t = {"wants": {"id": item, "count": count}, "gives": {"id": "minecraft:emerald"}, "max_uses": uses,
                         "xp": XP_BUY[lvl], "reputation_discount": 0.05}
                else:
                    t = {"wants": {"id": "minecraft:emerald", "count": emeralds} if emeralds > 1 else {"id": "minecraft:emerald"},
                         "gives": {"id": item, "count": count} if count > 1 else {"id": item}, "max_uses": uses,
                         "xp": XP_SELL[lvl], "reputation_discount": 0.05}
                write(f"data/{MOD}/villager_trade/{rel}.json", t)
                ids.append(f"{MOD}:{rel}")
            write(f"data/{MOD}/trade_set/{prof}/level_{lvl}.json",
                  {"amount": 2, "random_sequence": f"{MOD}:trade_set/{prof}/level_{lvl}", "trades": ids})


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
        ("tvilas.mayor.no_desk", "O Prefeito perdeu a Prefeitura: ponha uma Mesa do Prefeito perto.",
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
        ("tvilas.lot.no_money", "Faltam esmeraldas: o terreno custa %s.", "Faltam esmeraldas: o terreno custa %s.",
         "Not enough emeralds: the lot costs %s."),
        ("tvilas.lot.bought", "Terreno comprado por %s esmeraldas: de x=%s z=%s até x=%s z=%s. Só você e os seus amigos constroem aqui.",
         "Terreno comprado por %s esmeraldas: de x=%s z=%s até x=%s z=%s. Só tu e os teus amigos constroem aqui.",
         "Lot bought for %s emeralds: from x=%s z=%s to x=%s z=%s. Only you and your friends build here."),
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
        ("tvilas.mission.paid", "Missão cumprida! +%s esmeraldas, +%s de reputação.", "Missão cumprida! +%s esmeraldas, +%s de reputação.",
         "Mission complete! +%s emeralds, +%s reputation."),
        ("tvilas.mission.paid_cubcoin", "Missão cumprida! +%s esmeraldas, +%s, +%s de reputação.",
         "Missão cumprida! +%s esmeraldas, +%s, +%s de reputação.", "Mission complete! +%s emeralds, +%s, +%s reputation."),
        ("tvilas.mission.deliver", "Trazer %s %s", "Trazer %s %s", "Bring %s %s"),
        ("tvilas.mission.decorate", "Pôr %s %s na vila", "Pôr %s %s na povoação", "Place %s %s in the village"),
        ("tvilas.mission.defend", "Defender: derrotar %s monstros/invasores na vila", "Defender: derrotar %s monstros/invasores na povoação",
         "Defend: defeat %s monsters/raiders in the village"),
        ("tvilas.dialog.title", "Prefeitura — %s", "Câmara Municipal — %s", "Town Hall — %s"),
        ("tvilas.dialog.status", "População: %s · Sua reputação: %s · Reputação da vila: %s · Terrenos vendidos: %s de %s",
         "População: %s · A tua reputação: %s · Reputação da povoação: %s · Terrenos vendidos: %s de %s",
         "Population: %s · Your reputation: %s · Village reputation: %s · Lots sold: %s of %s"),
        ("tvilas.dialog.mission", "%s. %s — %s esmeraldas, +%s reputação", "%s. %s — %s esmeraldas, +%s reputação",
         "%s. %s — %s emeralds, +%s reputation"),
        ("tvilas.dialog.progress", " (%s/%s)", " (%s/%s)", " (%s/%s)"),
        ("tvilas.dialog.defend_hint", " · só conta durante um alarme", " · só conta durante um alarme", " · counts only during an alarm"),
        ("tvilas.dialog.accept", "Aceitar %s", "Aceitar %s", "Accept %s"),
        ("tvilas.dialog.deliver", "Entregar %s", "Entregar %s", "Hand in %s"),
        ("tvilas.dialog.buy", "Comprar terreno aqui (%s esm.)", "Comprar terreno aqui (%s esm.)", "Buy lot here (%s em.)"),
        ("tvilas.dialog.lot_here", "Você está no terreno de %s.", "Estás no terreno de %s.", "You are on %s's lot."),
        ("tvilas.dialog.lot_info", "Ver este terreno", "Ver este terreno", "View this lot"),
        ("tvilas.dialog.friend_hint", "Amigos no seu terreno: /vila terreno amigo adicionar <jogador>",
         "Amigos no teu terreno: /vila terreno amigo adicionar <jogador>", "Friends on your lot: /vila terreno amigo adicionar <player>"),
        ("tvilas.mayor.arrived", "Um Prefeito chegou à vila e instalou-se na Prefeitura.",
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
    for k, br, pt, en in T:
        tr(k, br, pt, en)


# ------------------------------------------------------------------ guia

def P(key, recipe, br, pt, en):
    return {"key": key, "recipe": recipe, "br": br, "pt": pt, "en": en}


GUIDE = [
    P("cover", None,
      ("Guia das Vilas", "Prefeito, missões, terrenos com dono, vilas que crescem, alarme contra invasões e quatro vendedores novos.\n\nTudo começa na Prefeitura, no centro das vilas novas."),
      ("Guia das Vilas", "Presidente da Câmara, missões, terrenos com dono, povoações que crescem, alarme contra invasões e quatro vendedores novos.\n\nTudo começa na Câmara, no centro das povoações novas."),
      ("Villages Guide", "Mayor, missions, owned lots, growing villages, a raid alarm and four new traders.\n\nIt all starts at the Town Hall, in the center of new villages.")),
    P("mayor", "mayor_desk",
      ("O Prefeito", "Um aldeão sem emprego que ache a Mesa do Prefeito vira Prefeito. Só uma Mesa por vila."),
      ("Presidente", "Um aldeão sem emprego que encontre a Mesa da Câmara fica Presidente. Só uma por povoação."),
      ("The Mayor", "A jobless villager who finds the Mayor's Desk becomes the Mayor. One desk per village.")),
    P("town_hall", None,
      ("A Prefeitura", "Metade das vilas novas nasce com uma Prefeitura no centro: um salão com a Mesa do Prefeito e um sino na praça.\n\nNas outras vilas, faça a Mesa e ponha-a perto do centro."),
      ("A Câmara", "Metade das povoações novas nasce com uma Câmara no centro: um salão com a Mesa da Câmara e um sino na praça.\n\nNas outras, faz a Mesa e põe-na perto do centro."),
      ("The Town Hall", "Half of new villages start with a Town Hall in the center: a hall with the Mayor's Desk and a bell in the square.\n\nIn other villages, craft the Desk and place it near the center.")),
    P("territory", None,
      ("Território", "A vila vai até 400 blocos à volta da Mesa do Prefeito: casas longe do centro também contam.\n\nQuem gere o servidor muda o raio em config/tvilas.json."),
      ("Território", "A povoação vai até 400 blocos à volta da Mesa da Câmara: casas longe do centro também contam.\n\nQuem gere o servidor muda o raio em config/tvilas.json."),
      ("Territory", "The village reaches 400 blocks around the Mayor's Desk: houses far from the center count too.\n\nServer admins change the radius in config/tvilas.json.")),
    P("reborn", None,
      ("Vila vazia", "Uma vila sem aldeões renasce: ponha a Mesa do Prefeito e um Prefeito chega.\n\nSe houver camas livres, chega um morador por dia, até três. Depois a tesouraria paga mais."),
      ("Povoação vazia", "Uma povoação sem aldeões renasce: põe a Mesa da Câmara e chega um Presidente.\n\nSe houver camas livres, chega um morador por dia, até três. Depois a tesouraria paga mais."),
      ("Empty village", "A village with no villagers comes back: place the Mayor's Desk and a Mayor arrives.\n\nWith free beds, one resident arrives per day, up to three. Then the treasury pays for more.")),
    P("talk", None,
      ("Falar com ele", "Clique direito no Prefeito ou na Mesa: abre o quadro da Prefeitura, com o estado da vila, as missões e os terrenos.\n\nO Prefeito não faz trocas."),
      ("Falar com ele", "Clique direito no Presidente ou na Mesa: abre o quadro da Câmara, com o estado da povoação, as missões e os terrenos.\n\nO Presidente não faz trocas."),
      ("Talking", "Right-click the Mayor or the Desk: it opens the Town Hall board with the village status, missions and lots.\n\nThe Mayor does not trade.")),
    P("missions", None,
      ("Missões", "Há sempre três: trazer itens, pôr blocos na vila e defender a vila.\n\nAceite primeiro: só conta o que fizer depois. Os blocos têm de lá estar quando entregar."),
      ("Missões", "Há sempre três: trazer itens, pôr blocos na povoação e defendê-la.\n\nAceita primeiro: só conta o que fizeres depois. Os blocos têm de lá estar quando entregares."),
      ("Missions", "There are always three: bring items, place blocks in the village and defend it.\n\nAccept first: only what you do afterwards counts. Blocks must still be there when you hand in.")),
    P("rewards", None,
      ("Recompensas", "Cada missão paga esmeraldas e dá reputação nesta vila. Com o mod CubCoin, paga também em CubCoins.\n\nA reputação deixa os aldeões daqui um pouco mais baratos."),
      ("Recompensas", "Cada missão paga esmeraldas e dá reputação nesta povoação. Com o mod CubCoin, paga também em CubCoins.\n\nA reputação deixa os aldeões daqui um pouco mais baratos."),
      ("Rewards", "Each mission pays emeralds and gives reputation in this village. With the CubCoin mod it also pays CubCoins.\n\nReputation makes the villagers here a bit cheaper.")),
    P("defend", None,
      ("Defender", "A missão de defesa só conta durante um alarme: monstros e invasores derrotados dentro da vila.\n\nDepois de entregar, o Prefeito põe outra missão no lugar."),
      ("Defender", "A missão de defesa só conta durante um alarme: monstros e invasores derrotados dentro da povoação.\n\nDepois de entregares, o Presidente põe outra missão no lugar."),
      ("Defending", "The defense mission only counts during an alarm: monsters and raiders defeated inside the village.\n\nAfter you hand in, the Mayor posts a new mission.")),
    P("lots", None,
      ("Terrenos", "No quadro, compre o terreno onde está: 16 por 16 blocos com você no meio, do fundo do mundo até ao céu.\n\nPaga em esmeraldas. Mais reputação, mais barato."),
      ("Terrenos", "No quadro, compra o terreno onde estás: 16 por 16 blocos contigo no meio, do fundo do mundo até ao céu.\n\nPaga-se em esmeraldas. Mais reputação, mais barato."),
      ("Lots", "On the board, buy the lot you stand on: 16 by 16 blocks with you in the middle, from the bottom of the world to the sky.\n\nPaid in emeralds. More reputation, cheaper.")),
    P("lot_rules", None,
      ("Proteção", "No seu terreno, só você e os amigos partem ou põem blocos. Máquinas do mod Industrial também obedecem. Lava, fogo, explosões e pistões de fora não entram.\n\nA praça da Prefeitura não se vende."),
      ("Proteção", "No teu terreno, só tu e os amigos partem ou põem blocos. Máquinas do mod Industrial também obedecem. Lava, fogo, explosões e pistões de fora não entram.\n\nA praça da Câmara não se vende."),
      ("Protection", "On your lot, only you and your friends break or place blocks. Machines from the Industrial mod obey too. Lava, fire, explosions and pistons from outside stay out.\n\nThe Town Hall square is not for sale.")),
    P("friends", None,
      ("Amigos", "Dentro do seu terreno escreva:\n/vila terreno amigo adicionar Nome\n\nPara tirar, troque adicionar por remover."),
      ("Amigos", "Dentro do teu terreno escreve:\n/vila terreno amigo adicionar Nome\n\nPara tirar, troca adicionar por remover."),
      ("Friends", "Inside your lot type:\n/vila terreno amigo adicionar Name\n\nTo take a friend out, use remover instead.")),
    P("levels", None,
      ("Nível da vila", "Aldeia, depois Vila, depois Cidade.\n\nVila: 8 aldeões e 100 de reputação somada.\nCidade: 16 aldeões e 400.\n\nO nível nunca desce."),
      ("Nível", "Aldeia, depois Vila, depois Cidade.\n\nVila: 8 aldeões e 100 de reputação somada.\nCidade: 16 aldeões e 400.\n\nO nível nunca desce."),
      ("Village level", "Hamlet, then Village, then Town.\n\nVillage: 8 villagers and 100 total reputation.\nTown: 16 villagers and 400.\n\nThe level never goes down.")),
    P("unlocks", None,
      ("O que desbloqueia", "Aldeia: 4 terrenos à venda.\nVila: 8 terrenos, missões maiores e 1 reforço do alarme.\nCidade: 16 terrenos, as missões mais bem pagas e 2 reforços.\n\nO terreno fica mais caro em vilas maiores."),
      ("O que desbloqueia", "Aldeia: 4 terrenos à venda.\nVila: 8 terrenos, missões maiores e 1 reforço do alarme.\nCidade: 16 terrenos, as missões mais bem pagas e 2 reforços.\n\nO terreno fica mais caro em povoações maiores."),
      ("Unlocks", "Hamlet: 4 lots for sale.\nVillage: 8 lots, bigger missions and 1 alarm upgrade.\nTown: 16 lots, the best paid missions and 2 upgrades.\n\nLots cost more in bigger villages.")),
    P("alarm", None,
      ("Alarme", "Numa invasão, ou com 3 ou mais monstros perto, o Prefeito toca o sino e chama golens de ferro: 2 no máximo (mais com o reforço), um de cada vez.\n\nOs golens da vila ficam mais fortes."),
      ("Alarme", "Numa invasão, ou com 3 ou mais monstros perto, o Presidente toca o sino e chama golens de ferro: 2 no máximo (mais com o reforço), um de cada vez.\n\nOs golens ficam mais fortes."),
      ("Alarm", "In a raid, or with 3 or more monsters nearby, the Mayor rings the bell and calls iron golems: 2 at most (more with upgrades), one at a time.\n\nThe village golems get stronger.")),
    P("alarm_warning", None,
      ("⚠ Cuidado", "Os golens atacam quem ferir aldeões, como no jogo normal.\n\nOs golens chamados vão-se embora um minuto depois do alarme e não largam ferro."),
      ("⚠ Cuidado", "Os golens atacam quem ferir aldeões, como no jogo normal.\n\nOs golens chamados vão-se embora um minuto depois do alarme e não largam ferro."),
      ("⚠ Careful", "Golems attack anyone who hurts villagers, as in the normal game.\n\nCalled golems leave a minute after the alarm and drop no iron.")),
    P("taxes", None,
      ("Impostos", "O Prefeito cobra 1 cêntimo por cama e por dia nas casas dos terrenos, e 2 por cento das trocas no território, com aldeões ou entre jogadores.\n\nSem CubCoin, cobra em esmeraldas quando a conta chega a uma."),
      ("Impostos", "O Presidente cobra 1 cêntimo por cama e por dia nas casas dos terrenos, e 2 por cento das trocas no território, com aldeões ou entre jogadores.\n\nSem CubCoin, cobra em esmeraldas quando a conta chega a uma."),
      ("Taxes", "The Mayor charges 1 cent per bed per day in lot houses, and 2 percent of trades in the territory, with villagers or between players.\n\nWithout CubCoin, it charges emeralds once the bill reaches one.")),
    P("treasury", None,
      ("Tesouraria", "Os impostos vão para a tesouraria. Uma vez por dia o Prefeito compra uma melhoria: moradores novos, um golem da vila ou o reforço do alarme.\n\nVeja a tesouraria e os gastos no quadro."),
      ("Tesouraria", "Os impostos vão para a tesouraria. Uma vez por dia o Presidente compra uma melhoria: moradores novos, um golem ou o reforço do alarme.\n\nVê a tesouraria e os gastos no quadro."),
      ("Treasury", "Taxes go to the treasury. Once a day the Mayor buys an upgrade: new residents, a village golem or an alarm upgrade.\n\nSee the treasury and spending on the board.")),
    P("debts", None,
      ("Dívidas", "Quem não tiver com que pagar fica a dever. A dívida aparece no quadro e cobra-se quando houver dinheiro."),
      ("Dívidas", "Quem não tiver com que pagar fica a dever. A dívida aparece no quadro e cobra-se quando houver dinheiro."),
      ("Debts", "Anyone who cannot pay owes it. The debt shows on the board and is charged when there is money.")),
    P("market", None,
      ("Mercado", "Algumas vilas novas têm um Mercado: quatro bancas com os postos de trabalho dos vendedores novos.\n\nAldeões sem emprego ficam lá a vender."),
      ("Mercado", "Algumas povoações novas têm um Mercado: quatro bancas com os postos de trabalho dos vendedores novos.\n\nAldeões sem emprego ficam lá a vender."),
      ("Market", "Some new villages have a Market: four stalls with the job sites of the new traders.\n\nJobless villagers take the stalls.")),
    P("baker", "baker_oven",
      ("Padeiro", "Compra trigo, açúcar, ovos e cacau. Vende pão, biscoitos, tortas e bolo."),
      ("Padeiro", "Compra trigo, açúcar, ovos e cacau. Vende pão, bolachas, tartes e bolo."),
      ("Baker", "Buys wheat, sugar, eggs and cocoa. Sells bread, cookies, pies and cake.")),
    P("florist", "flower_stall",
      ("Florista", "Vende flores de todo o tipo. No último nível, sementes raras e flor de esporos."),
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
    P("commands", None,
      ("Comandos", "/vila abre o quadro\n/vila info\n/vila missao aceitar 1\n/vila missao entregar 1\n/vila terreno comprar\n/vila terreno info"),
      ("Comandos", "/vila abre o quadro\n/vila info\n/vila missao aceitar 1\n/vila missao entregar 1\n/vila terreno comprar\n/vila terreno info"),
      ("Commands", "/vila opens the board\n/vila info\n/vila missao aceitar 1\n/vila missao entregar 1\n/vila terreno comprar\n/vila terreno info")),
]


def _grid(r):
    rows = [row.ljust(3) for row in r["pattern"]] + ["   "] * (3 - len(r["pattern"]))
    return [[None if ch == " " else r["key"][ch] for ch in row[:3]] for row in rows[:3]]


def gen_guide():
    keys = [g["key"] for g in GUIDE]
    assert len(keys) == len(set(keys))
    for g in GUIDE:
        for part, i in (("title", 0), ("text", 1)):
            tr(f"guide.{MOD}.{g['key']}.{part}", g["br"][i], g["pt"][i], g["en"][i])
    book = [f"§l{g['br'][0]}§r\n\n{g['br'][1]}" for g in GUIDE]
    assert len(book) <= 100
    write(f"data/{MOD}/recipe/guide.json", {"type": "minecraft:crafting_shapeless", "category": "misc",
                                             "ingredients": ["minecraft:book", "minecraft:wheat"],
                                             "result": {"id": "minecraft:written_book", "components": {"minecraft:written_book_content": {
                                                 "title": {"raw": "Guia das Vilas"}, "author": "Tcordeiro",
                                                 "pages": [{"raw": p} for p in book], "resolved": True}}}})
    advancement("guide", ["minecraft:wheat"])

    def js(v):
        return "null" if v is None else json.dumps(v, ensure_ascii=False)

    consts, lines = [], []
    for g in GUIDE:
        if g["recipe"] is None:
            lines.append(f'new Page("{g["key"]}", null, List.of())')
            continue
        r = RECIPES[g["recipe"]]
        const = g["recipe"].upper()
        rows = ", ".join("{" + ", ".join(js(c) for c in row) + "}" for row in _grid(r))
        consts.append(f'\tstatic final Recipe {const} = new Recipe("{MOD}:{g["recipe"]}", "{r["result"]["id"]}", {r["result"].get("count", 1)},\n'
                      f'\t\t\tnew String[][] {{{rows}}});')
        lines.append(f'new Page("{g["key"]}", null, List.of({const}))')
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

	/** Página: chave de tradução, imagem opcional (textures/gui/guide/&lt;img&gt;.png) e receitas. */
	public record Page(String key, String image, List<Recipe> recipes) {{}}

{chr(10).join(consts)}

	public static final List<Page> PAGES = List.of(
			{(","+chr(10)+chr(9)+chr(9)+chr(9)).join(lines)});
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
    gen_lang()
    clean_stale()
    n_trades = sum(len(t) for lv in TRADES.values() for t in lv.values())
    print(f"{len(BLOCKS)} blocos, {len(PROFESSIONS)} profissões, {n_trades} trocas, {len(RECIPES)} receitas, "
          f"{len(GUIDE)} páginas no guia, {len(written)} ficheiros")


if __name__ == "__main__":
    main()
