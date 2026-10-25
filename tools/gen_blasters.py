#!/usr/bin/env python3
"""
Generates the blaster models of Phase 10: block-model JSON made of boxes, one shared palette
texture, and the bow item model that picks a blaster by the bow's model data. Own designs after
the look of the film blasters, built from scratch here. Run from the repository root:

    python3 tools/gen_blasters.py

Needs Pillow. Writes into src/client/resources/assets/:

    sabercraft/models/item/blaster/<type>.json
    sabercraft/textures/item/blaster/palette.png
    minecraft/items/bow.json

The barrel points along -Z (the muzzle is at small z), the grip hangs down. The blaster type of a
bow comes from its minecraft:custom_model_data component: strings[0] is the type id (see
BlasterType.java). A bow without one keeps the vanilla bow model, including drawing.
"""
import json
import os

from PIL import Image

ASSETS = os.path.join("src", "client", "resources", "assets")
MODELS = os.path.join(ASSETS, "sabercraft", "models", "item", "blaster")
TEXTURES = os.path.join(ASSETS, "sabercraft", "textures", "item", "blaster")
ITEMS = os.path.join(ASSETS, "minecraft", "items")

# 16 colors in a 4 x 4 grid of 4 x 4 pixel cells.
PALETTE = [
    ("black", "#1b1b1f"), ("dark", "#33353b"), ("grey", "#62656c"), ("silver", "#a7abb3"),
    ("wood", "#6d4527"), ("darkwood", "#3f2a1a"), ("orange", "#c66a22"), ("red", "#e0301c"),
    ("blue", "#2f63c4"), ("olive", "#5b5c3a"), ("white", "#dedede"), ("tan", "#b59866"),
    ("green", "#3f8f3a"), ("brass", "#b08a3c"), ("lens", "#2b3e56"), ("rust", "#8a4a2a"),
]
INDEX = {name: i for i, (name, _) in enumerate(PALETTE)}


def uv(color):
    i = INDEX[color]
    u, v = (i % 4) * 4, (i // 4) * 4
    # Model UVs are in 0..16 for the 16 x 16 texture; keep off the cell edges.
    return [u + 0.5, v + 0.5, u + 3.5, v + 3.5]


def box(frm, to, color):
    faces = {face: {"uv": uv(color), "texture": "#palette"} for face in ("north", "south", "east", "west", "up", "down")}
    return {"from": frm, "to": to, "faces": faces}


# Each blaster: list of (from, to, color). Units: 16 = one block, muzzle towards z = 0.
BLASTERS = {
    # E-11: short black carbine with a scope, a side magazine and a folded stock.
    "e11": [
        ([6.6, 7.0, 3.5], [9.4, 10.0, 11.0], "black"),
        ([7.3, 8.6, 0.5], [8.7, 10.0, 3.5], "dark"),
        ([7.0, 8.4, 0.2], [9.0, 10.2, 0.8], "grey"),
        ([7.6, 10.0, 4.5], [8.4, 11.4, 9.5], "dark"),
        ([7.5, 10.1, 4.2], [8.5, 11.5, 4.7], "lens"),
        ([5.2, 7.8, 5.0], [6.6, 8.8, 7.2], "silver"),
        ([7.2, 3.8, 8.6], [8.8, 7.0, 10.6], "black"),
        ([7.6, 6.2, 6.8], [8.4, 7.0, 8.6], "grey"),
        ([7.6, 8.0, 11.0], [8.4, 8.8, 15.0], "grey"),
        ([7.4, 7.2, 14.4], [8.6, 9.2, 15.2], "grey"),
    ],
    # DL-44: heavy pistol with a flash hider, a long scope and a wooden grip.
    "dl44": [
        ([6.8, 8.0, 4.0], [9.2, 10.4, 11.0], "black"),
        ([7.2, 8.4, 1.6], [8.8, 10.0, 4.0], "grey"),
        ([6.9, 8.1, 1.0], [9.1, 10.3, 1.8], "dark"),
        ([7.0, 8.2, 2.6], [9.0, 10.2, 3.0], "dark"),
        ([7.55, 10.4, 4.6], [8.45, 11.6, 10.4], "black"),
        ([7.5, 10.5, 4.3], [8.5, 11.7, 4.7], "lens"),
        ([7.65, 10.4, 10.0], [8.35, 11.0, 10.4], "silver"),
        ([7.1, 4.0, 9.0], [8.9, 8.0, 11.4], "wood"),
        ([7.4, 6.6, 7.4], [8.6, 7.2, 9.0], "silver"),
        ([6.6, 8.6, 6.0], [6.8, 9.6, 9.0], "silver"),
    ],
    # A280: long rifle with a brown stock and a slim barrel.
    "a280": [
        ([7.35, 8.6, 0.0], [8.65, 9.8, 6.0], "grey"),
        ([7.1, 8.4, 0.0], [8.9, 10.0, 0.6], "dark"),
        ([6.8, 7.6, 5.0], [9.2, 10.4, 11.0], "dark"),
        ([7.5, 10.4, 6.0], [8.5, 11.4, 9.6], "black"),
        ([7.45, 10.5, 5.7], [8.55, 11.5, 6.1], "lens"),
        ([7.2, 7.0, 11.0], [8.8, 10.0, 16.0], "wood"),
        ([7.3, 3.8, 9.2], [8.7, 7.6, 10.8], "black"),
        ([7.2, 6.0, 6.0], [8.8, 7.6, 7.8], "silver"),
        ([6.7, 9.0, 3.0], [9.3, 9.4, 5.0], "olive"),
    ],
    # Heavy repeater: a big black body, a vented barrel shroud, a drum and a carry handle.
    "repeater": [
        ([6.0, 7.0, 4.0], [10.0, 11.0, 12.5], "black"),
        ([6.7, 7.8, 0.0], [9.3, 10.4, 4.0], "grey"),
        ([6.5, 7.6, 0.6], [9.5, 10.6, 1.2], "dark"),
        ([6.5, 7.6, 2.0], [9.5, 10.6, 2.6], "dark"),
        ([4.8, 7.2, 6.0], [6.0, 10.6, 9.5], "silver"),
        ([7.5, 11.0, 6.0], [8.5, 12.2, 6.8], "grey"),
        ([7.5, 11.0, 9.6], [8.5, 12.2, 10.4], "grey"),
        ([7.5, 11.8, 6.0], [8.5, 12.4, 10.4], "grey"),
        ([7.1, 3.6, 9.4], [8.9, 7.0, 11.6], "black"),
        ([7.2, 7.4, 12.5], [8.8, 10.2, 15.5], "dark"),
        ([9.8, 8.6, 5.0], [10.4, 9.6, 8.0], "red"),
    ],
    # Rebel blaster (DH-17 style): slim black pistol, a magazine under the front, a grey band.
    "rebel": [
        ([7.0, 8.0, 3.0], [9.0, 10.4, 11.0], "black"),
        ([7.4, 8.6, 0.8], [8.6, 9.8, 3.0], "grey"),
        ([7.2, 8.4, 0.4], [8.8, 10.0, 1.0], "dark"),
        ([7.3, 6.4, 4.4], [8.7, 8.0, 6.8], "silver"),
        ([7.1, 3.8, 8.6], [8.9, 8.0, 10.6], "black"),
        ([6.9, 8.6, 6.4], [9.1, 9.2, 7.2], "grey"),
        ([7.6, 10.4, 5.0], [8.4, 11.0, 9.0], "dark"),
        ([8.9, 9.0, 8.0], [9.2, 9.6, 9.6], "blue"),
    ],
    # Ezra Bridger's blaster: the stun setting of his first lightsaber, a grey hilt with orange and
    # brown parts, the folding grip down and the blaster emitter at the front.
    "ezra": [
        ([7.1, 8.0, 3.0], [8.9, 9.8, 11.2], "grey"),
        ([7.0, 7.9, 3.0], [9.0, 9.9, 4.2], "silver"),
        ([7.3, 8.25, 1.2], [8.7, 9.55, 3.0], "dark"),
        ([7.0, 7.9, 6.0], [9.0, 9.9, 6.8], "orange"),
        ([7.0, 7.9, 8.6], [9.0, 9.9, 9.4], "orange"),
        ([7.2, 4.4, 8.0], [8.8, 8.0, 10.0], "rust"),
        ([7.4, 6.0, 6.2], [8.6, 6.6, 8.0], "darkwood"),
        ([7.4, 9.8, 4.4], [8.6, 10.4, 5.6], "red"),
        ([7.2, 8.1, 11.2], [8.8, 9.7, 12.0], "dark"),
    ],
}

RIFLES = {"e11", "a280", "repeater"}


def display(name, boxes):
    """Hand and inventory placement, made for the barrel along -Z.

    Players hold every blaster aimed like a loaded crossbow (see AvatarRendererMixin). With the
    arm pointing forward, the model's own axes already fit: barrel forward, top up. The grip (the
    lowest box) is moved into the hand. A mob or armor stand with a hanging arm points it down. In
    first person the frame is the camera's: the barrel points into the screen, turned a little
    towards the crosshair.
    """
    grip = min(boxes, key=lambda b: b[0][1])
    gy = (grip[0][1] + grip[1][1]) / 2 - 8
    gz = (grip[0][2] + grip[1][2]) / 2 - 8
    rifle = name in RIFLES
    s3 = 0.85
    third = {"rotation": [0, 0, 0], "translation": r([0, -gy * s3, -gz * s3]), "scale": [s3] * 3}
    s1 = 0.6 if rifle else 0.65
    first = {"rotation": [0, 4, 0], "translation": [0, 1.2, -1.5 if rifle else -2.5], "scale": [s1] * 3}
    return {
        "thirdperson_righthand": third,
        "thirdperson_lefthand": third,
        "firstperson_righthand": first,
        "firstperson_lefthand": first,
        "gui": {"rotation": [15, -90, 0], "translation": [0, 0, 0], "scale": [1.05] * 3},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.6] * 3},
        "fixed": {"rotation": [0, 90, 0], "translation": [0, 0, 0], "scale": [1.0] * 3},
        "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    }


def r(values):
    return [round(v, 2) + 0.0 for v in values]


def main():
    os.makedirs(MODELS, exist_ok=True)
    os.makedirs(TEXTURES, exist_ok=True)
    os.makedirs(ITEMS, exist_ok=True)

    image = Image.new("RGBA", (16, 16))
    for i, (_, color) in enumerate(PALETTE):
        rgb = tuple(int(color[k:k + 2], 16) for k in (1, 3, 5))
        for y in range(4):
            for x in range(4):
                image.putpixel(((i % 4) * 4 + x, (i // 4) * 4 + y), rgb + (255,))
    image.save(os.path.join(TEXTURES, "palette.png"))
    print("wrote palette")

    cases = []
    for name, boxes in BLASTERS.items():
        model = {
            "textures": {"palette": "sabercraft:item/blaster/palette", "particle": "sabercraft:item/blaster/palette"},
            "elements": [box(f, t, c) for f, t, c in boxes],
            "display": display(name, boxes),
        }
        write_json(os.path.join(MODELS, name + ".json"), model)
        cases.append({"when": "sabercraft:" + name, "model": {"type": "minecraft:model", "model": "sabercraft:item/blaster/" + name}})

    vanilla_bow = {
        "type": "minecraft:condition",
        "property": "minecraft:using_item",
        "on_false": {"type": "minecraft:model", "model": "minecraft:item/bow"},
        "on_true": {
            "type": "minecraft:range_dispatch",
            "property": "minecraft:use_duration",
            "scale": 0.05,
            "entries": [
                {"threshold": 0.65, "model": {"type": "minecraft:model", "model": "minecraft:item/bow_pulling_1"}},
                {"threshold": 0.9, "model": {"type": "minecraft:model", "model": "minecraft:item/bow_pulling_2"}},
            ],
            "fallback": {"type": "minecraft:model", "model": "minecraft:item/bow_pulling_0"},
        },
    }
    item = {
        "model": {
            "type": "minecraft:select",
            "property": "minecraft:custom_model_data",
            "index": 0,
            "cases": cases,
            # Not converted: the vanilla bow, drawing included.
            "fallback": vanilla_bow,
        }
    }
    write_json(os.path.join(ITEMS, "bow.json"), item)


def write_json(path, data):
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")
    print("wrote", path)


if __name__ == "__main__":
    main()
