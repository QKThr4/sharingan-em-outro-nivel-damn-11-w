"""Gera texturas e modelos do Laboratorio de Analise, da Amostra e da Celula de Hashirama."""
import json
import os
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources")
TEX = os.path.join(ROOT, "assets", "sharingan", "textures")
MOD = os.path.join(ROOT, "assets", "sharingan", "models")
DATA = os.path.join(ROOT, "data")
T = (0, 0, 0, 0)


def save(im, *path):
    p = os.path.join(TEX, *path)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    im.save(p)


def solid(color, noise=None):
    im = Image.new("RGBA", (16, 16), color)
    px = im.load()
    if noise:
        for y in range(16):
            for x in range(16):
                if (x * 7 + y * 13) % 11 == 0:
                    px[x, y] = noise
    return im


# ---------- blocos ----------
base = Image.new("RGBA", (16, 16), (236, 240, 243, 255))
px = base.load()
for y in range(16):
    for x in range(16):
        if x in (0, 8) or y in (0, 8):
            px[x, y] = (200, 207, 214, 255)
for y in range(11, 14):
    for x in range(16):
        px[x, y] = (63, 182, 217, 255)
save(base, "block", "lab_base.png")

top = Image.new("RGBA", (16, 16), (244, 247, 249, 255))
px = top.load()
for y in range(16):
    for x in range(16):
        if x in (0, 15) or y in (0, 15):
            px[x, y] = (190, 198, 206, 255)
        elif (x + y) % 8 == 0:
            px[x, y] = (226, 232, 237, 255)
save(top, "block", "lab_top.png")
save(solid((150, 158, 166, 255), (128, 136, 144, 255)), "block", "lab_metal.png")
save(solid((150, 225, 245, 255), (190, 240, 252, 255)), "block", "lab_glass.png")
save(solid((70, 220, 110, 255), (140, 255, 160, 255)), "block", "lab_green.png")
save(solid((160, 80, 230, 255), (200, 140, 255, 255)), "block", "lab_purple.png")
save(solid((50, 54, 60, 255), (70, 74, 80, 255)), "block", "lab_dark.png")

# ---------- itens ----------
amostra = Image.new("RGBA", (16, 16), T)
px = amostra.load()
K = (30, 40, 40, 255)
G = (190, 235, 240, 255)
L = (120, 200, 90, 255)
L2 = (80, 150, 60, 255)
for y in range(3, 14):
    px[6, y] = K
    px[9, y] = K
for y in range(3, 13):
    for x in (7, 8):
        px[x, y] = G
for y in range(7, 13):
    for x in (7, 8):
        px[x, y] = L if y < 11 else L2
for x in range(6, 10):
    px[x, 2] = K
    px[x, 14] = K
px[7, 3] = (255, 255, 255, 255)
for x in range(5, 11):
    px[x, 1] = (150, 100, 60, 255)
save(amostra, "item", "amostra_hashirama.png")

celula = Image.new("RGBA", (16, 16), T)
px = celula.load()
OUT = (20, 70, 40, 255)
MID = (60, 200, 100, 255)
LIGHT = (170, 255, 190, 255)
NUC = (255, 230, 120, 255)
for (cx, cy, r) in ((7, 8, 5), (11, 5, 3), (4, 4, 2)):
    for y in range(16):
        for x in range(16):
            d2 = (x - cx) ** 2 + (y - cy) ** 2
            if d2 <= r * r and px[x, y][3] == 0:
                px[x, y] = OUT if d2 > (r - 1) ** 2 else MID
for (cx, cy, r) in ((7, 8, 5), (11, 5, 3), (4, 4, 2)):
    for y in range(16):
        for x in range(16):
            d2 = (x - cx) ** 2 + (y - cy) ** 2
            if d2 <= (r - 1) ** 2 and (x + y) % 3 == 0:
                px[x, y] = LIGHT
px[7, 8] = NUC
px[8, 8] = NUC
px[7, 9] = NUC
px[11, 5] = NUC
px[4, 4] = NUC
save(celula, "item", "celula_hashirama.png")

# ---------- modelos ----------
def w(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


for name in ("amostra_hashirama", "celula_hashirama"):
    w(os.path.join(MOD, "item", name + ".json"),
      {"parent": "minecraft:item/generated", "textures": {"layer0": "sharingan:item/" + name}})
w(os.path.join(MOD, "item", "laboratorio_analise.json"), {"parent": "sharingan:block/laboratorio_analise"})
w(os.path.join(ROOT, "assets", "sharingan", "blockstates", "laboratorio_analise.json"),
  {"variants": {"": {"model": "sharingan:block/laboratorio_analise"}}})


def box(x0, y0, z0, x1, y1, z1, tex, up=None):
    faces = {}
    for f in ("north", "south", "east", "west", "down"):
        faces[f] = {"texture": "#" + tex}
    faces["up"] = {"texture": "#" + (up or tex)}
    return {"from": [x0, y0, z0], "to": [x1, y1, z1], "faces": faces}


els = [
    box(0, 0, 0, 16, 12, 16, "base", "top"),
    # em cima: bequeres
    box(2, 12, 2, 6, 16, 6, "green"),
    box(1.5, 16, 1.5, 6.5, 16.5, 6.5, "glass"),
    box(2, 12, 9, 5, 15, 12, "purple"),
    box(1.5, 15, 8.5, 5.5, 15.5, 12.5, "glass"),
    # suporte de tubos de ensaio
    box(9, 12, 2, 15, 13, 6, "metal"),
    box(9.5, 13, 3, 10.5, 17, 4.2, "glass"),
    box(11.5, 13, 3, 12.5, 17, 4.2, "green"),
    box(13.5, 13, 3, 14.5, 17, 4.2, "purple"),
    # microscopio
    box(9, 12, 9, 14, 13, 14, "metal"),
    box(12.5, 13, 12.5, 13.5, 19, 13.5, "dark"),
    box(9.5, 17.5, 10, 13.5, 18.5, 11.5, "dark"),
    box(9.5, 15, 9.5, 11, 18, 11, "dark"),
    box(9.8, 14, 9.8, 10.7, 15, 10.7, "glass"),
    box(9.8, 18, 9.8, 10.7, 19.5, 10.7, "metal"),
    # em volta: frascos nas laterais
    box(-1.5, 5, 3, 0.5, 9, 5, "green"), box(-1.5, 9, 3, 0.5, 10, 5, "metal"),
    box(-1.5, 5, 9, 0.5, 8, 11, "purple"),
    box(15.5, 6, 5, 17.5, 10, 7, "glass"), box(15.5, 3, 10, 17.5, 7, 12, "green"),
    box(4, 6, -1.5, 7, 10, 0.5, "purple"), box(10, 5, -1.5, 12, 8, 0.5, "green"),
    box(3, 4, 15.5, 5, 8, 17.5, "green"), box(9, 6, 15.5, 12, 9, 17.5, "glass"),
]
w(os.path.join(MOD, "block", "laboratorio_analise.json"), {
    "parent": "minecraft:block/block",
    "textures": {
        "particle": "sharingan:block/lab_base",
        "base": "sharingan:block/lab_base",
        "top": "sharingan:block/lab_top",
        "metal": "sharingan:block/lab_metal",
        "glass": "sharingan:block/lab_glass",
        "green": "sharingan:block/lab_green",
        "purple": "sharingan:block/lab_purple",
        "dark": "sharingan:block/lab_dark",
    },
    "elements": els,
})

# ---------- dados: receitas, loot, tag ----------
w(os.path.join(DATA, "sharingan", "recipe", "amostra_hashirama.json"), {
    "type": "minecraft:crafting_shaped",
    "category": "misc",
    "pattern": ["RNF", "NLN", "ANT"],
    "key": {
        "R": {"item": "minecraft:rotten_flesh"},
        "N": {"item": "minecraft:nether_star"},
        "F": {"item": "minecraft:blaze_powder"},
        "L": {"tag": "minecraft:logs"},
        "A": {"item": "minecraft:amethyst_shard"},
        "T": {"item": "minecraft:ghast_tear"},
    },
    "result": {"id": "sharingan:amostra_hashirama", "count": 1},
})
w(os.path.join(DATA, "sharingan", "recipe", "laboratorio_analise.json"), {
    "type": "minecraft:crafting_shaped",
    "category": "misc",
    "pattern": ["PSP", "PGP", "PPP"],
    "key": {
        "P": {"tag": "minecraft:planks"},
        "S": {"item": "minecraft:spyglass"},
        "G": [{"item": "minecraft:glass_pane"}, {"item": "minecraft:glass"}],
    },
    "result": {"id": "sharingan:laboratorio_analise", "count": 1},
})
w(os.path.join(DATA, "sharingan", "loot_table", "blocks", "laboratorio_analise.json"), {
    "type": "minecraft:block",
    "pools": [{
        "rolls": 1.0,
        "bonus_rolls": 0.0,
        "entries": [{"type": "minecraft:item", "name": "sharingan:laboratorio_analise"}],
        "conditions": [{"condition": "minecraft:survives_explosion"}],
    }],
})
w(os.path.join(DATA, "minecraft", "tags", "block", "mineable", "pickaxe.json"),
  {"values": ["sharingan:laboratorio_analise"]})
