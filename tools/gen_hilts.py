#!/usr/bin/env python3
"""
Generates the 3D saber hilts: block-model JSON, textures and the netherite sword item model.

Every hilt is built from boxes along a vertical axis and then tilted by 45 degrees onto the item
diagonal, where vanilla handheld items point. So the hilts use the vanilla handheld transforms for
first person, third person, item frames and the inventory, and the blade starts exactly at the
emitter. The textures are painted from scratch here (no external resources). Run from the
repository root:

    python3 tools/gen_hilts.py

Needs numpy and Pillow. Writes into src/client/resources/assets/:

    sabercraft/models/item/hilt/<name>.json
    sabercraft/textures/item/hilt/<name>.png
    minecraft/items/netherite_sword.json

The hilt and the blade color of each sword come from its minecraft:custom_model_data component:
strings[0] is the hilt id, strings[1] the color id (see SaberVariant.java).
"""
import json
import math
import os

import numpy as np
from PIL import Image

ASSETS = os.path.join("src", "client", "resources", "assets")
MODELS = os.path.join(ASSETS, "sabercraft", "models", "item", "hilt")
TEXTURES = os.path.join(ASSETS, "sabercraft", "textures", "item", "hilt")
ITEMS = os.path.join(ASSETS, "minecraft", "items")

# Texture: 64x64 pixels for the 16x16 model UV space, so 4 pixels per model unit. It is split into
# columns of 3 units, one per material. Side faces stretch the column across their width (it only
# holds the round shading) and map 1:1 along the hilt, so ridges and vents keep their spacing.
TEX = 64
PX = TEX // 16
COLUMN = 3.0

# Position along the hilt axis is "d", in model units, measured from the model center (8, 8, 8).
# The axis is tilted onto the item diagonal, so d maps to the model point (8 + d * S, 8 + d * S, 8).
S = math.sqrt(0.5)
# Where the blade starts on all one-handed hilts. Same place as the flat standard hilt, so held
# swords keep their old proportions.
EMITTER_D = 1.8

DIAGONAL = {"origin": [8, 8, 8], "axis": "z", "angle": -45}



def shade_profile(width):
    """Brightness across a face: soft highlight left of center, darker edges, like a round tube."""
    u = (np.arange(width) + 0.5) / width
    return 0.66 + 0.46 * np.exp(-((u - 0.36) / 0.2) ** 2) - 0.14 * (np.abs(u - 0.5) * 2) ** 4


def brushed(height, rng, amount):
    return 1.0 + amount * (rng.random(height) - 0.5)


class Painter:
    """Paints one material per column."""

    def __init__(self, seed):
        self.img = np.zeros((TEX, TEX, 4), dtype=np.float64)
        self.rng = np.random.default_rng(seed)
        self.columns = {}

    def column(self, name, paint):
        index = len(self.columns)
        x0 = int(index * COLUMN * PX)
        width = int(COLUMN * PX)
        rgb = paint(width, TEX, self.rng)
        self.img[:, x0:x0 + width, :3] = rgb
        self.img[:, x0:x0 + width, 3] = 255
        self.columns[name] = index * COLUMN
        return index * COLUMN

    def save(self, path):
        Image.fromarray(np.clip(self.img, 0, 255).astype(np.uint8), "RGBA").save(path)


def metal(base, brush=0.08, shine=1.0):
    def paint(width, height, rng):
        profile = shade_profile(width) ** shine
        lines = brushed(height, rng, brush)
        out = np.array(base, dtype=float)[None, None, :] * profile[None, :, None] * lines[:, None, None]
        return out
    return paint


def ridged(base, ridge, period=4, ridge_px=2, offset=0):
    """Grip with raised ridges every `period` pixels."""
    def paint(width, height, rng):
        out = metal(base, 0.05)(width, height, rng)
        top = metal(ridge, 0.05)(width, height, rng)
        for y in range(height):
            if (y + offset) % period < ridge_px:
                out[y] = top[y]
            elif (y + offset) % period == ridge_px:
                out[y] *= 0.55
        return out
    return paint


def vented(base, slot, period=6, slot_px=3):
    """Metal with dark vent slots in the middle of the face."""
    def paint(width, height, rng):
        out = metal(base)(width, height, rng)
        x0, x1 = width // 3, width - width // 3
        for y in range(height):
            phase = y % period
            if 1 <= phase <= slot_px:
                out[y, x0:x1] = np.array(slot, dtype=float) * (0.8 + 0.2 * (phase == 1))
            elif phase == slot_px + 1:
                out[y, x0:x1] *= 1.25
        return out
    return paint


def wrapped(base, line, period=5):
    """Leather wrap with diagonal seams."""
    def paint(width, height, rng):
        out = metal(base, 0.12, 0.6)(width, height, rng)
        for y in range(height):
            for x in range(width):
                if (x + y) % period == 0:
                    out[y, x] = np.array(line, dtype=float) * shade_profile(width)[x]
        return out
    return paint


def emitter_top(metal_rgb):
    """Top of the emitter, seen from the blade: metal rim, dark ring, glowing center."""
    def paint(width, height, rng):
        out = metal(metal_rgb)(width, height, rng)
        c = (width - 1) / 2.0
        for y in range(width):
            for x in range(width):
                r = math.hypot(x - c, y - c)
                if r < 1.6:
                    out[y, x] = (255, 244, 236)
                elif r < 3.6:
                    out[y, x] = (22, 22, 26)
                elif r < 4.4:
                    out[y, x] *= 0.7
        return out
    return paint


def flat(rgb, glow=False):
    def paint(width, height, rng):
        profile = np.ones(width) if glow else shade_profile(width)
        return np.array(rgb, dtype=float)[None, None, :] * profile[None, :, None] * np.ones((height, 1, 1))
    return paint


class Hilt:
    """One hilt: a list of boxes along the axis plus where its blades start."""

    def __init__(self, name, seed):
        self.name = name
        self.painter = Painter(seed)
        self.elements = []
        self.top = None
        self.bottom = None

    def material(self, name, paint):
        self.painter.column(name, paint)

    def box(self, d0, d1, hw, material, hz=None, ox=0.0, oz=0.0, cap=None, spin=0.0):
        """A box from d0 to d1 along the axis with half width hw (x) and hz (z), shifted by ox, oz."""
        hz = hw if hz is None else hz
        self.top = d1 if self.top is None else max(self.top, d1)
        self.bottom = d0 if self.bottom is None else min(self.bottom, d0)
        self.elements.append((d0, d1, hw, hz, ox, oz, material, cap, spin))

    def model(self, display=None):
        elements = []
        span_top = self.top

        for d0, d1, hw, hz, ox, oz, material, cap, spin in self.elements:
            u0 = self.painter.columns[material]
            # Continuous along the hilt: v counts from the top of the whole hilt.
            v0 = (span_top - d1) % 16.0
            v1 = v0 + (d1 - d0)
            if v1 > 16.0:
                v0, v1 = 0.0, d1 - d0
            side = {"uv": rnd([u0, v0, u0 + COLUMN, v1]), "texture": "#hilt"}
            cap_material = cap or material
            cu = self.painter.columns[cap_material]
            cap_size = min(COLUMN, 2 * max(hw, hz))
            cap_face = {"uv": rnd([cu, 0, cu + cap_size, cap_size]), "texture": "#hilt"}
            if cap_material.startswith("emitter"):
                cap_face = {"uv": rnd([cu, 0, cu + COLUMN, COLUMN]), "texture": "#hilt"}
            rotation = dict(DIAGONAL)
            if spin:
                rotation = {"origin": [8, 8, 8], "x": 0, "y": spin, "z": -45}
            elements.append({
                "from": rnd([8 + ox - hw, 8 + d0, 8 + oz - hz]),
                "to": rnd([8 + ox + hw, 8 + d1, 8 + oz + hz]),
                "rotation": rotation,
                "faces": {
                    "north": side, "south": side, "east": side, "west": side,
                    "up": cap_face, "down": {"uv": rnd([u0, 0, u0 + cap_size, cap_size]), "texture": "#hilt"},
                },
            })

        model = {
            "parent": "minecraft:item/handheld",
            "gui_light": "front",
            "textures": {
                "hilt": "sabercraft:item/hilt/" + self.name,
                "particle": "sabercraft:item/hilt/" + self.name,
            },
            "elements": elements,
        }
        if display:
            model["display"] = display
        return model


def rnd(values):
    return [round(v, 4) for v in values]


def emitter_point(d):
    p = (8 + d * S) / 16.0
    return rnd([p, p, 0.5])


# ---------------------------------------------------------------------------------------------
# The hilts. All one-handed hilts run from d = -11.2 (pommel) to EMITTER_D (emitter rim), about
# 13 units like the flat standard hilt.

def guardian():
    """Classic silver hilt with black grip ridges and a clamp, the look of a knight's first saber."""
    h = Hilt("guardian", 11)
    h.material("silver", metal((200, 204, 212)))
    h.material("grip", ridged((34, 34, 38), (70, 70, 76), period=4, ridge_px=2))
    h.material("steel", metal((92, 96, 104)))
    h.material("emitter", emitter_top((200, 204, 212)))
    h.material("button", flat((196, 32, 28)))

    h.box(-11.2, -10.5, 1.2, "steel")
    h.box(-10.5, -9.4, 1.05, "silver")
    h.box(-9.4, -4.0, 1.0, "silver")
    # Raised grip ridges in front and behind, like rubber T-tracks.
    for side in (-1, 1):
        h.box(-9.1, -4.3, 0.5, "grip", hz=0.2, oz=side * 1.05)
    h.box(-4.0, -3.4, 1.15, "steel")
    h.box(-3.4, -0.6, 1.1, "silver")
    # Clamp bar with the activation button on top.
    h.box(-3.0, -1.0, 0.45, "steel", hz=0.35, oz=1.35)
    h.box(-2.4, -1.6, 0.25, "button", hz=0.2, oz=1.85)
    h.box(-0.6, 0.0, 0.95, "steel")
    h.box(0.0, EMITTER_D, 1.35, "silver", cap="emitter")
    return h


def dark_lord():
    """Black hilt with silver ribs, a control box with red buttons and a toothed emitter shroud."""
    h = Hilt("dark_lord", 23)
    h.material("black", metal((40, 40, 46), 0.05))
    h.material("ribs", ridged((30, 30, 34), (178, 182, 190), period=5, ridge_px=2, offset=1))
    h.material("silver", metal((188, 192, 200)))
    h.material("emitter", emitter_top((52, 52, 58)))
    h.material("red", flat((210, 24, 18)))

    h.box(-11.2, -10.3, 1.3, "black")
    h.box(-10.3, -9.8, 1.15, "silver")
    h.box(-9.8, -3.8, 1.05, "ribs")
    h.box(-3.8, -3.3, 1.2, "silver")
    h.box(-3.3, -0.7, 1.15, "black")
    # Control box: a black plate with two red buttons and a silver switch.
    h.box(-3.0, -1.0, 0.7, "black", hz=0.3, oz=1.3)
    h.box(-2.7, -2.2, 0.22, "red", hz=0.18, oz=1.72)
    h.box(-1.9, -1.4, 0.22, "red", hz=0.18, oz=1.72)
    h.box(-2.6, -1.3, 0.18, "silver", hz=0.15, ox=-0.5, oz=1.65)
    h.box(-0.7, -0.2, 1.0, "silver")
    h.box(-0.2, 1.1, 1.25, "silver")
    # Shroud: wider black ring on top, with teeth turned by 45 degrees.
    h.box(1.1, EMITTER_D, 1.45, "black", cap="emitter")
    h.box(1.0, EMITTER_D - 0.15, 1.15, "black", spin=45)
    return h


def sentinel():
    """Black and silver hilt with a long black grip and a vented emitter."""
    h = Hilt("sentinel", 37)
    h.material("silver", metal((206, 208, 214)))
    h.material("black", wrapped((30, 30, 34), (58, 58, 64), period=5))
    h.material("vent", vented((196, 198, 206), (18, 18, 20), period=6, slot_px=3))
    h.material("emitter", emitter_top((196, 198, 206)))
    h.material("button", flat((40, 150, 60)))

    h.box(-11.2, -10.2, 1.2, "silver")
    h.box(-10.2, -9.8, 1.05, "black")
    h.box(-9.8, -4.2, 1.0, "black")
    h.box(-4.2, -3.6, 1.15, "silver")
    h.box(-3.6, -0.5, 1.05, "black")
    h.box(-2.6, -1.6, 0.3, "silver", hz=0.25, oz=1.2)
    h.box(-2.35, -1.85, 0.16, "button", hz=0.12, oz=1.5)
    h.box(-0.5, EMITTER_D - 0.4, 1.3, "vent")
    h.box(EMITTER_D - 0.4, EMITTER_D, 1.4, "silver", cap="emitter")
    return h


def kenobi():
    """Phase 8, Obi-Wan Kenobi: silver hilt with black grip rings, a dark neck and a flared, vented
    emitter shroud. An own design in the spirit of a calm master's saber."""
    h = Hilt("kenobi", 61)
    h.material("silver", metal((206, 208, 214)))
    h.material("rings", ridged((26, 26, 30), (196, 198, 204), period=3, ridge_px=2))
    h.material("dark", metal((58, 54, 52), 0.06))
    h.material("vent", vented((200, 202, 208), (24, 22, 22), period=5, slot_px=2))
    h.material("emitter", emitter_top((200, 202, 208)))

    h.box(-11.2, -10.6, 1.25, "silver")
    h.box(-10.6, -10.0, 1.0, "dark")
    h.box(-10.0, -5.0, 1.05, "rings")
    h.box(-5.0, -4.4, 1.2, "silver")
    h.box(-4.4, -2.2, 0.95, "dark")
    h.box(-3.8, -2.8, 0.3, "silver", hz=0.25, oz=1.0)
    h.box(-2.2, -1.6, 1.15, "silver")
    h.box(-1.6, 0.2, 1.05, "silver")
    # Flared shroud: two steps out, vented.
    h.box(0.2, 1.0, 1.3, "vent")
    h.box(1.0, EMITTER_D, 1.48, "silver", cap="emitter")
    return h


def skywalker():
    """Phase 8, Luke Skywalker: black grip with fine ribs, a silver body and a long silver emitter
    cone with a small control box. An own design after a young knight's self-built saber."""
    h = Hilt("skywalker", 67)
    h.material("silver", metal((212, 214, 220)))
    h.material("black", ridged((22, 22, 26), (52, 52, 58), period=3, ridge_px=1))
    h.material("steel", metal((110, 114, 122)))
    h.material("emitter", emitter_top((212, 214, 220)))
    h.material("green", flat((46, 160, 70)))

    h.box(-11.2, -10.4, 1.15, "silver")
    h.box(-10.4, -4.6, 1.05, "black")
    h.box(-4.6, -4.0, 1.2, "steel")
    h.box(-4.0, -1.4, 1.0, "silver")
    # Control box with a green activator.
    h.box(-3.6, -2.0, 0.55, "black", hz=0.3, oz=1.2)
    h.box(-3.1, -2.5, 0.2, "green", hz=0.16, oz=1.55)
    h.box(-1.4, -0.8, 1.15, "steel")
    # Emitter cone, narrowing in two steps towards the blade.
    h.box(-0.8, 0.5, 1.1, "silver")
    h.box(0.5, EMITTER_D, 1.3, "silver", cap="emitter")
    return h


# The double-bladed hilt is centered on the hand. In the vanilla handheld transforms the hand
# holds the item about 4.7 units below the model center on the diagonal.
STAFF_CENTER = -4.7
STAFF_HALF = 8.0


def double():
    """Long two-ended hilt, symmetric around the grip, with an emitter at each end."""
    h = Hilt("double", 53)
    h.material("silver", metal((190, 194, 202)))
    h.material("black", ridged((30, 30, 34), (64, 64, 70), period=4, ridge_px=2))
    h.material("red", flat((200, 30, 24)))
    h.material("emitter", emitter_top((190, 194, 202)))
    h.material("dark", metal((70, 72, 80)))

    c = STAFF_CENTER
    # Center grip.
    h.box(c - 2.8, c + 2.8, 1.0, "black")
    h.box(c - 0.3, c + 0.3, 1.2, "dark")
    for sign in (-1, 1):
        def at(a, b):
            lo, hi = sorted((c + sign * a, c + sign * b))
            return lo, hi
        lo, hi = at(2.8, 3.4)
        h.box(lo, hi, 1.15, "silver")
        lo, hi = at(3.4, 6.6)
        h.box(lo, hi, 1.05, "dark")
        lo, hi = at(4.2, 5.4)
        h.box(lo, hi, 0.2, "red", hz=0.18, oz=1.12)
        lo, hi = at(6.6, STAFF_HALF)
        h.box(lo, hi, 1.3, "silver", cap="emitter")
    return h


HILTS = [
    # name, builder, default color, blades as (emitter d, angle)
    ("guardian", guardian, "blue", [(EMITTER_D, 0)]),
    ("dark_lord", dark_lord, "red", [(EMITTER_D, 0)]),
    ("sentinel", sentinel, "green", [(EMITTER_D, 0)]),
    ("double", double, "red", [(STAFF_CENTER + STAFF_HALF, 0), (STAFF_CENTER - STAFF_HALF, 180)]),
    ("kenobi", kenobi, "blue", [(EMITTER_D, 0)]),
    ("skywalker", skywalker, "green", [(EMITTER_D, 0)]),
]

# Inventory icon size. The 3D hilts are slimmer than a sword sprite, so the one-handed hilts are
# drawn larger; the long double hilt smaller so it fits into a slot.
ICON_SCALE = {"double": 0.95}
DEFAULT_ICON_SCALE = 1.3


def icon_display(hilt, scale):
    """Centers the hilt in the inventory slot and item frame and scales it."""
    center = (hilt.top + hilt.bottom) / 2.0 * S
    shift = round(-center * scale, 3)
    return {
        "gui": {"rotation": [0, 0, 0], "translation": [shift, shift, 0], "scale": [scale] * 3},
        # Item frames show the back of the item, so X is mirrored.
        "fixed": {"rotation": [0, 180, 0], "translation": [-shift, shift, 0], "scale": [scale] * 3},
    }

ICON_CONTEXTS = ["gui", "ground", "fixed", "on_shelf"]


def held_model(hilt_model, color, blades):
    """Hilt only in the inventory, on the ground and in frames; hilt plus blade(s) in the hand."""
    layers = [{"type": "minecraft:model", "model": hilt_model}]
    for d, angle in blades:
        blade = {"type": "sabercraft:saber_blade", "base": hilt_model, "style": "sabercraft:" + color}
        if hilt_model != "sabercraft:item/saber_hilt":
            blade["emitter"] = emitter_point(d)
        if angle:
            blade["angle"] = angle
        layers.append(blade)
    return {
        "type": "minecraft:select",
        "property": "minecraft:display_context",
        "cases": [{"when": ICON_CONTEXTS, "model": {"type": "minecraft:model", "model": hilt_model}}],
        "fallback": {"type": "minecraft:composite", "models": layers},
    }


def main():
    os.makedirs(MODELS, exist_ok=True)
    os.makedirs(TEXTURES, exist_ok=True)
    os.makedirs(ITEMS, exist_ok=True)

    cases = []
    for name, build, color, blades in HILTS:
        hilt = build()
        hilt.painter.save(os.path.join(TEXTURES, name + ".png"))
        display = icon_display(hilt, ICON_SCALE.get(name, DEFAULT_ICON_SCALE))
        write_json(os.path.join(MODELS, name + ".json"), hilt.model(display))
        cases.append({"when": "sabercraft:" + name, "model": held_model("sabercraft:item/hilt/" + name, color, blades)})

    item = {
        "model": {
            "type": "minecraft:select",
            "property": "minecraft:custom_model_data",
            "index": 0,
            "cases": cases,
            # No hilt chosen, or "sabercraft:standard": the flat hilt with the red blade of Phase 3.
            "fallback": held_model("sabercraft:item/saber_hilt", "red", [(EMITTER_D, 0)]),
        }
    }
    write_json(os.path.join(ITEMS, "netherite_sword.json"), item)


def write_json(path, data):
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")
    print("wrote", path)


if __name__ == "__main__":
    main()
