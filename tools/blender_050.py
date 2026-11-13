# Vilas 0.5.0 — modelos novos feitos no Blender em estilo Minecraft (cubos com texturas de 16 px):
#   Balcão dos Correios (bloco), Bolsa do Carteiro, Boia do pescador e Banca do mercado (itens de cena).
# Os cubos são a fonte única: o Blender monta-os (com as texturas, para as prévias) e exporta cada modelo como JSON do Minecraft.
#
# Uso:  /Applications/Blender.app/Contents/MacOS/Blender --background --python tools/blender_050.py -- <pasta_texturas_vanilla> <pasta_previas>
#       (a pasta das texturas vanilla tem block/*.png tirados do 26.3.jar; as do Vilas vêm do próprio repositório)
import json
import math
import os
import sys

import bpy
import bmesh

ARGS = sys.argv[sys.argv.index("--") + 1:]
VAN, PREV = ARGS[0], ARGS[1]
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources", "assets", "tvilas")
os.makedirs(PREV, exist_ok=True)

# (x1, y1, z1, x2, y2, z2, textura[, {face: textura}])  — coordenadas do Minecraft (0..16), y para cima, frente = norte
POST_COUNTER = {
    "textures": {"front": "tvilas:block/post_counter_front", "side": "tvilas:block/post_counter_side", "shelf": "tvilas:block/post_counter_shelf",
                 "top": "minecraft:block/spruce_planks", "brass": "minecraft:block/gold_block", "paper": "minecraft:block/white_concrete",
                 "particle": "tvilas:block/post_counter_side"},
    "boxes": [
        (0, 0, 2, 16, 10, 16, "side", {"north": "front"}),           # o móvel (a frente com a ranhura e as gavetas)
        (0, 10, 0, 16, 12, 16, "top"),                                # o tampo
        (0, 12, 12, 16, 16, 16, "shelf", {"south": "side", "east": "side", "west": "side", "up": "top", "down": "top"}),  # escaninhos
        (2, 12, 3, 5, 13, 6, "brass"),                                # a balança dos selos
        (3, 13, 4, 4, 14, 5, "brass"),
        (8, 12, 4, 13, 12.5, 8, "paper"),                             # um maço de cartas
        (9, 12.5, 5, 12, 13, 7, "paper"),
    ],
}

MAIL_BAG = {
    "textures": {"leather": "tvilas:block/mail_bag", "flap": "tvilas:block/mail_bag_flap", "brass": "minecraft:block/gold_block",
                 "strap": "minecraft:block/brown_wool", "paper": "minecraft:block/white_concrete", "particle": "tvilas:block/mail_bag"},
    "boxes": [
        (3, 2, 6, 13, 11, 10, "leather"),                             # o saco
        (3, 5, 5, 13, 11.5, 6, "flap"),                               # a aba com o envelope
        (3, 11, 5, 13, 11.5, 10, "leather"),                          # a aba por cima
        (7, 5.5, 4.5, 9, 7.5, 5, "brass"),                            # a fivela
        (2, 9, 7.5, 3, 16, 8.5, "strap"),                             # a correia
        (13, 9, 7.5, 14, 16, 8.5, "strap"),
        (3, 15, 7.5, 13, 16, 8.5, "strap"),
        (5, 11, 7, 10, 13, 7.5, "paper"),                             # uma carta a espreitar
    ],
    "display": {"ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.6, 0.6, 0.6]},
                "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]},
                "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]},
                "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 1, 1], "scale": [0.5, 0.5, 0.5]},
                "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]}},
}

BOBBER = {
    "textures": {"red": "minecraft:block/red_concrete", "white": "minecraft:block/white_concrete", "particle": "minecraft:block/red_concrete"},
    "boxes": [
        (6, 0, 6, 10, 3, 10, "red"),                                  # a metade de baixo (vermelha, dentro de água)
        (6, 3, 6, 10, 6, 10, "white"),                                # a de cima (branca)
        (6.5, 6, 6.5, 9.5, 6.5, 9.5, "red"),
        (7.5, 6.5, 7.5, 8.5, 10, 8.5, "red"),                         # a antena
    ],
    "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, 2, 0], "scale": [1, 1, 1]},
                "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]}},
}

# A banca: a frente (balcão) virada para sul (+z), o vendedor fica atrás (norte); toldo às riscas vermelhas e brancas.
_stall = [
    (1, 0, 1, 2, 16, 2, "post"), (14, 0, 1, 15, 16, 2, "post"),     # postes de trás (mais altos)
    (1, 0, 13, 2, 13, 14, "post"), (14, 0, 13, 15, 13, 14, "post"),  # postes da frente
    (1, 0, 9, 15, 6, 13, "planks"),                                   # o balcão
    (0, 6, 8, 16, 7, 14, "top"),                                      # o tampo
    (2, 7, 9, 5, 9, 12, "hay"),                                       # mercadoria: fardo, melancia, abóbora, maçãs (caixote)
    (6, 7, 10, 9, 9.5, 12.5, "melon"),
    (10, 7, 9.5, 13, 10, 12.5, "pumpkin", {"up": "pumpkin_top", "down": "pumpkin_top"}),
    (13.5, 7, 9, 15, 8, 10.5, "apple"),
]
for i in range(8):                                                    # toldo: riscas em degraus (de trás para a frente desce)
    tex = "red" if i % 2 == 0 else "white"
    x1, x2 = i * 2, i * 2 + 2
    _stall += [(x1, 16, 0, x2, 17, 6, tex), (x1, 15, 6, x2, 16, 11, tex), (x1, 14, 11, x2, 15, 15, tex), (x1, 12.5, 15, x2, 14, 16, tex)]
MARKET_STALL = {
    "textures": {"post": "minecraft:block/dark_oak_log", "planks": "minecraft:block/oak_planks", "top": "minecraft:block/spruce_planks",
                 "hay": "minecraft:block/hay_block_side", "melon": "minecraft:block/melon_side", "pumpkin": "minecraft:block/pumpkin_side",
                 "pumpkin_top": "minecraft:block/pumpkin_top", "apple": "minecraft:block/red_concrete", "red": "minecraft:block/red_wool",
                 "white": "minecraft:block/white_wool", "particle": "minecraft:block/oak_planks"},
    "boxes": _stall,
    "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.55, 0.55, 0.55]},
                "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.25, 0.25, 0.25]}},
}

MODELS = {
    "block/post_counter": POST_COUNTER,
    "item/mail_bag": MAIL_BAG,
    "item/bobber": BOBBER,
    "item/market_stall": MARKET_STALL,
}

FACES = ("north", "south", "east", "west", "up", "down")


def r(v):
    return round(v, 3) if v != int(v) else int(v)


def uv(face, b):
    x1, y1, z1, x2, y2, z2 = b[:6]
    if face in ("north", "south"):
        return [r(x1), r(16 - y2), r(x2), r(16 - y1)]
    if face in ("east", "west"):
        return [r(z1), r(16 - y2), r(z2), r(16 - y1)]
    return [r(x1), r(z1), r(x2), r(z2)]


# ---------- 1) exportar JSON (fonte única: os cubos) ----------
for name, m in MODELS.items():
    elements = []
    for b in m["boxes"]:
        x1, y1, z1, x2, y2, z2, t = b[:7]
        over = b[7] if len(b) > 7 else {}
        assert -16 <= min(x1, y1, z1) and max(x2, y2, z2) <= 32 and x1 < x2 and y1 < y2 and z1 < z2, (name, b)
        faces = {f: {"texture": "#" + over.get(f, t)} for f in FACES}  # UV automático do Minecraft (pela posição)
        elements.append({"from": [r(x1), r(y1), r(z1)], "to": [r(x2), r(y2), r(z2)], "faces": faces})
    model = {"parent": "minecraft:block/block" if name.startswith("block/") else "minecraft:item/generated",
             "textures": m["textures"], "elements": elements}
    if name.startswith("item/"):
        model["parent"] = "minecraft:block/block"
        model["gui_light"] = "side"
    if "display" in m:
        model["display"] = m["display"]
    path = os.path.join(RES, "models", name + ".json")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as fh:
        json.dump(model, fh, indent=1)
        fh.write("\n")
print("JSON:", len(MODELS))

# ---------- 2) cena (prévias para conferir) ----------
bpy.ops.wm.read_factory_settings(use_empty=True)
scn = bpy.context.scene
mats = {}


def tex_path(ref):
    ns, path = ref.split(":")
    return os.path.join(RES, "textures", path + ".png") if ns == "tvilas" else os.path.join(VAN, path + ".png")


def material(ref):
    if ref in mats:
        return mats[ref]
    m = bpy.data.materials.new(ref)
    m.use_nodes = True
    nt = m.node_tree
    bsdf = next(n for n in nt.nodes if n.type == "BSDF_PRINCIPLED")
    t = nt.nodes.new("ShaderNodeTexImage")
    t.image = bpy.data.images.load(tex_path(ref))
    t.interpolation = "Closest"
    nt.links.new(t.outputs["Color"], bsdf.inputs["Base Color"])
    bsdf.inputs["Roughness"].default_value = 0.85
    mats[ref] = m
    return m


def build(name, m, ox):
    me = bpy.data.meshes.new(name)
    bm = bmesh.new()
    uvl = bm.loops.layers.uv.new()
    refs = []
    for b in m["boxes"]:
        x1, y1, z1, x2, y2, z2, t = b[:7]
        over = b[7] if len(b) > 7 else {}
        P = lambda x, y, z: bm.verts.new((x / 16, -z / 16, y / 16))  # Minecraft (x, y, z) → Blender (x, −z, y)
        c = {(i, j, k): P((x1, x2)[i], (y1, y2)[j], (z1, z2)[k]) for i in (0, 1) for j in (0, 1) for k in (0, 1)}
        corners = {"north": [(1, 0, 0), (0, 0, 0), (0, 1, 0), (1, 1, 0)], "south": [(0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1)],
                   "west": [(0, 0, 0), (0, 0, 1), (0, 1, 1), (0, 1, 0)], "east": [(1, 0, 1), (1, 0, 0), (1, 1, 0), (1, 1, 1)],
                   "up": [(0, 1, 1), (1, 1, 1), (1, 1, 0), (0, 1, 0)], "down": [(0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)]}
        X, Y, Z = (x1, x2), (y1, y2), (z1, z2)
        for fname, cs in corners.items():
            ref = m["textures"][over.get(fname, t)]
            if ref not in refs:
                refs.append(ref)
            f = bm.faces.new([c[k] for k in cs])
            f.material_index = refs.index(ref)
            for loop, (i, j, k) in zip(f.loops, cs):
                if fname in ("north", "south"):
                    u, v = X[i], Y[j]
                elif fname in ("east", "west"):
                    u, v = Z[k], Y[j]
                else:
                    u, v = X[i], 16 - Z[k]
                loop[uvl].uv = (u / 16, v / 16)
    bm.to_mesh(me)
    bm.free()
    ob = bpy.data.objects.new(name, me)
    scn.collection.objects.link(ob)
    for ref in refs:
        me.materials.append(material(ref))
    ob.location = (ox, 0, 0)
    return ob


obs = []
for i, (name, m) in enumerate(MODELS.items()):
    obs.append(build(name.split("/")[1], m, i * 1.6))

world = bpy.data.worlds.new("w")
scn.world = world
world.use_nodes = True
world.node_tree.nodes["Background"].inputs[0].default_value = (0.55, 0.7, 0.9, 1)
world.node_tree.nodes["Background"].inputs[1].default_value = 0.9
sun = bpy.data.objects.new("sol", bpy.data.lights.new("sol", "SUN"))
sun.data.energy = 3.5
sun.rotation_euler = (math.radians(50), math.radians(10), math.radians(-35))
scn.collection.objects.link(sun)
try:
    scn.render.engine = "BLENDER_EEVEE_NEXT"
except TypeError:
    try:
        scn.render.engine = "BLENDER_EEVEE"
    except TypeError:
        pass
scn.render.resolution_x, scn.render.resolution_y = 1280, 720
cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam"))
scn.collection.objects.link(cam)
scn.camera = cam
cam.data.type = "ORTHO"

target = bpy.data.objects.new("alvo", None)
scn.collection.objects.link(target)
tc = cam.constraints.new("TRACK_TO")
tc.target = target
tc.track_axis = "TRACK_NEGATIVE_Z"
tc.up_axis = "UP_Y"
for i, ob in enumerate(obs):
    for o in obs:
        o.hide_render = o is not ob
    target.location = (ob.location.x + 0.5, -0.5, 0.5)
    cam.data.ortho_scale = 1.9
    # de frente-esquerda e de cima (a frente dos modelos é o norte = +y no Blender; a da banca é o sul = −y)
    front = -1 if ob.name == "market_stall" else 1
    cam.location = (ob.location.x + 0.5 - 2.0, -0.5 + front * 2.6, 2.4)
    scn.render.filepath = os.path.join(PREV, f"{ob.name}.png")
    bpy.ops.render.render(write_still=True)
    print("prévia:", scn.render.filepath)
