#!/usr/bin/env python3
"""Gera os ficheiros NBT das estruturas (Prefeitura e Mercado) para os 5 biomas de vila, com a paleta de cada bioma.

Formato igual ao das estruturas vanilla do 26.3 (lidas do jar para conferir: DataVersion, paleta com «id»/«properties», blocos
de quebra-cabeça/jigsaw com name/target/pool/joint/final_state):

  - Prefeitura (town_centers): 13×8×13. Salão com a Mesa do Prefeito, estantes, tapete e lanternas; praça com sino;
    3 ligações de rua (oeste, leste, sul) como os centros vanilla; 2 pontos de aldeões e 1 de golem (pools vanilla da vila).
  - Mercado (houses): 11×6×9. Quatro bancas com toldo e os postos de trabalho dos vendedores novos; entrada a oeste
    (building_entrance, como as casas vanilla) e 2 pontos de aldeões desempregados, que apanham as bancas.

Só blocos vanilla (os móveis do mod Móveis NÃO entram: as estruturas são ficheiros fixos e teriam de existir em duas versões).
Uso: python3 tools/gen_structures.py
"""
import json
import os
import sys
import zipfile

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbt  # noqa: E402

HOME = os.path.expanduser("~")
JAR = os.path.join(HOME, "Library", "Application Support", "horizon-launcher", "minecraft", "versions", "26.3", "26.3.jar")
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "data", "tvilas", "structure", "village")

with zipfile.ZipFile(JAR) as z:
    DATA_VERSION = json.load(z.open("version.json"))["world_version"]
    VANILLA_BLOCKS = {n.split("/")[-1][:-5] for n in z.namelist() if n.startswith("assets/minecraft/blockstates/")}

PALETTES = {
    "plains": dict(planks="oak_planks", log="oak_log", stone="cobblestone", path="dirt_path", stairs="oak_stairs", slab="oak_slab",
                   fence="oak_fence", door="oak_door", wool="red_wool", carpet="red_carpet"),
    "savanna": dict(planks="acacia_planks", log="acacia_log", stone="orange_terracotta", path="dirt_path", stairs="acacia_stairs",
                    slab="acacia_slab", fence="acacia_fence", door="acacia_door", wool="orange_wool", carpet="orange_carpet"),
    "desert": dict(planks="smooth_sandstone", log="cut_sandstone", stone="sandstone", path="smooth_sandstone", stairs="sandstone_stairs",
                   slab="sandstone_slab", fence="jungle_fence", door="jungle_door", wool="cyan_wool", carpet="cyan_carpet"),
    "taiga": dict(planks="spruce_planks", log="spruce_log", stone="cobblestone", path="dirt_path", stairs="spruce_stairs",
                  slab="spruce_slab", fence="spruce_fence", door="spruce_door", wool="green_wool", carpet="green_carpet"),
    "snowy": dict(planks="spruce_planks", log="stripped_spruce_log", stone="polished_diorite", path="dirt_path", stairs="spruce_stairs",
                  slab="spruce_slab", fence="spruce_fence", door="spruce_door", wool="blue_wool", carpet="blue_carpet"),
}


class Template:
    def __init__(self, sx, sy, sz):
        self.size = (sx, sy, sz)
        self.cells = {}

    def set(self, x, y, z, block, props=None, be=None):
        sx, sy, sz = self.size
        assert 0 <= x < sx and 0 <= y < sy and 0 <= z < sz, (x, y, z, block)
        name = block if ":" in block else "minecraft:" + block
        if name.startswith("minecraft:"):
            assert name[10:] in VANILLA_BLOCKS, f"bloco inexistente no 26.3: {name}"
        self.cells[(x, y, z)] = (name, tuple(sorted((props or {}).items())), be)

    def fill(self, x0, y0, z0, x1, y1, z1, block, props=None):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    self.set(x, y, z, block, props)

    def jigsaw(self, x, y, z, orientation, name, target, pool, joint, final_state):
        self.set(x, y, z, "jigsaw", {"orientation": orientation},
                 {"id": "minecraft:jigsaw", "name": name, "target": target, "pool": pool, "joint": joint, "final_state": final_state})

    def to_nbt(self):
        sx, sy, sz = self.size
        palette, index, blocks = [], {}, []
        for y in range(sy):
            for z in range(sz):
                for x in range(sx):
                    name, props, be = self.cells.get((x, y, z), ("minecraft:air", (), None))
                    key = (name, props)
                    if key not in index:
                        index[key] = len(palette)
                        entry = {"id": name}
                        if props:
                            entry["properties"] = {k: str(v) for k, v in props}
                        palette.append(entry)
                    b = {"pos": [nbt.Int(x), nbt.Int(y), nbt.Int(z)], "state": nbt.Int(index[key])}
                    if be:
                        b["nbt"] = be
                    blocks.append(b)
        return {"size": [nbt.Int(sx), nbt.Int(sy), nbt.Int(sz)], "entities": [], "blocks": blocks, "palette": palette,
                "DataVersion": nbt.Int(DATA_VERSION)}


def stairs(p, facing, half="bottom"):
    return (p["stairs"], {"facing": facing, "half": half, "shape": "straight", "waterlogged": "false"})


def town_hall(biome, p):
    t = Template(13, 8, 13)
    path = p["path"]
    # chão: praça (caminho com bordas de pedra) e o salão
    t.fill(0, 0, 0, 12, 0, 12, path)
    for x in range(13):
        for z in range(7, 13):
            if (x + z) % 4 == 0:
                t.set(x, 0, z, p["stone"])
    t.fill(2, 0, 0, 10, 0, 6, p["planks"])
    # paredes do salão (x 2..10, z 0..6), cantos em tronco
    t.fill(2, 1, 0, 10, 4, 0, p["planks"])
    t.fill(2, 1, 6, 10, 4, 6, p["planks"])
    t.fill(2, 1, 0, 2, 4, 6, p["planks"])
    t.fill(10, 1, 0, 10, 4, 6, p["planks"])
    for x, z in ((2, 0), (10, 0), (2, 6), (10, 6)):
        t.fill(x, 1, z, x, 4, z, p["log"], {"axis": "y"})
    t.fill(3, 1, 1, 9, 4, 5, "air")
    # janelas
    for x in (4, 8):
        t.fill(x, 2, 6, x, 3, 6, "glass_pane", {"east": "true", "west": "true", "north": "false", "south": "false", "waterlogged": "false"})
    for x in (4, 6, 8):
        t.fill(x, 2, 0, x, 3, 0, "glass_pane", {"east": "true", "west": "true", "north": "false", "south": "false", "waterlogged": "false"})
    for x in (2, 10):
        t.fill(x, 2, 3, x, 3, 3, "glass_pane", {"north": "true", "south": "true", "east": "false", "west": "false", "waterlogged": "false"})
    # porta (parede sul, ao meio)
    door = {"facing": "south", "hinge": "left", "open": "false", "powered": "false"}
    t.set(6, 1, 6, p["door"], door | {"half": "lower"})
    t.set(6, 2, 6, p["door"], door | {"half": "upper"})
    # telhado em degraus de lajes, com escadas a fazer a aba da frente
    t.fill(1, 5, 0, 11, 5, 7, p["slab"], {"type": "bottom", "waterlogged": "false"})
    t.fill(2, 5, 0, 10, 5, 6, p["planks"])
    t.fill(3, 6, 1, 9, 6, 5, p["slab"], {"type": "bottom", "waterlogged": "false"})
    t.fill(4, 6, 2, 8, 6, 4, p["planks"])
    t.fill(5, 7, 2, 7, 7, 4, p["slab"], {"type": "bottom", "waterlogged": "false"})
    # dentro: Mesa do Prefeito virada para a porta, estantes, tapete até à porta, lanternas
    t.set(6, 1, 2, "tvilas:mayor_desk", {"facing": "south"})
    for x in (3, 4, 8, 9):
        t.set(x, 1, 1, "bookshelf")
    t.fill(6, 1, 3, 6, 1, 5, p["carpet"])
    for x in (3, 9):
        t.set(x, 1, 5, "lantern", {"hanging": "false", "waterlogged": "false"})
    # praça: sino ao centro, postes com lanterna
    t.set(6, 1, 9, "bell", {"attachment": "floor", "facing": "north", "powered": "false"}, {"id": "minecraft:bell"})
    for x in (2, 10):
        t.set(x, 1, 8, p["fence"])
        t.set(x, 2, 8, "lantern", {"hanging": "false", "waterlogged": "false"})
    # ligações de rua (como os centros vanilla): oeste, leste, sul
    streets = f"minecraft:village/{biome}/streets"
    for x, z, o in ((0, 10, "west_up"), (12, 10, "east_up"), (6, 12, "south_up")):
        t.jigsaw(x, 1, z, o, "minecraft:street", "minecraft:street", streets, "aligned", "minecraft:structure_void")
    villagers = f"minecraft:village/{biome}/villagers"
    for x, z in ((4, 9), (8, 11)):
        t.jigsaw(x, 0, z, "up_north", "minecraft:bottom", "minecraft:bottom", villagers, "rollable", "minecraft:" + path)
    t.jigsaw(8, 0, 9, "up_north", "minecraft:bottom", "minecraft:bottom", "minecraft:village/common/iron_golem", "rollable", "minecraft:" + path)
    return t


STALLS = [((2, 1), "tvilas:baker_oven", "south"), ((6, 1), "tvilas:flower_stall", "south"),
          ((2, 5), "tvilas:tavern_counter", "north"), ((6, 5), "tvilas:apiary_bench", "north")]


def market(biome, p):
    t = Template(11, 6, 9)
    path = p["path"]
    t.fill(0, 0, 0, 10, 0, 8, path)
    for x in range(1, 10):
        for z in range(9):
            if (x * 3 + z) % 5 == 0:
                t.set(x, 0, z, p["stone"])
    for (x0, z0), block, facing in STALLS:
        # 4 postes, toldo de lã de 3×3 e o posto de trabalho ao fundo da banca
        for dx, dz in ((0, 0), (2, 0), (0, 2), (2, 2)):
            t.fill(x0 + dx, 1, z0 + dz, x0 + dx, 2, z0 + dz, p["fence"])
        t.fill(x0, 3, z0, x0 + 2, 3, z0 + 2, p["wool"])
        back_z = z0 if facing == "south" else z0 + 2
        t.set(x0 + 1, 1, back_z, block, {"facing": facing})
        front_z = z0 + 2 if facing == "south" else z0
        t.set(x0 + 1, 0, front_z, p["planks"])
    t.set(9, 1, 2, "barrel", {"facing": "up", "open": "false"}, {"id": "minecraft:barrel"})
    t.set(9, 1, 6, "hay_block", {"axis": "y"})
    t.set(9, 1, 4, p["fence"])
    t.set(9, 2, 4, "lantern", {"hanging": "false", "waterlogged": "false"})
    # entrada (oeste, no chão, como as casas vanilla) e aldeões
    t.jigsaw(0, 0, 4, "west_up", "minecraft:building_entrance", "minecraft:building_entrance", "minecraft:empty", "aligned", "minecraft:" + path)
    villagers = f"minecraft:village/{biome}/villagers"
    for x in (5, 8):
        t.jigsaw(x, 0, 4, "up_north", "minecraft:bottom", "minecraft:bottom", villagers, "rollable", "minecraft:" + path)
    return t


def main():
    n = 0
    for biome, p in PALETTES.items():
        for kind, fn in (("town_hall", town_hall), ("market", market)):
            data = fn(biome, p).to_nbt()
            path = os.path.join(OUT, biome, kind + ".nbt")
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with open(path, "wb") as f:
                f.write(nbt.dumps(data))
            back = nbt.loads(open(path, "rb").read())
            assert back["size"] == data["size"] and len(back["blocks"]) == len(data["blocks"])
            n += 1
    print(f"{n} estruturas (DataVersion {DATA_VERSION}) em {OUT}")


if __name__ == "__main__":
    main()
