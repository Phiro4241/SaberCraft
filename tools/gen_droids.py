"""Builds the droid models of SaberCraft (Phase 11): R2-D2, C-3PO, Chopper, BB-8, K-2SO, a mouse
droid, a probe droid and a B1 battle droid. Every droid is made of parts (body, head, arms, legs)
that the game moves on their own: the head turns, BB-8's ball rolls under its head, arms and legs
swing when walking.

All shapes are boxes and round parts built in code with the tools of gen_blasters.py, painted with
generated textures; nothing is taken from films or games.

Droid space: 1/16 block, the feet in the middle at (0, 0, 0), y up, the front looks towards -z.
Each part is written as its own item model, moved so its pivot sits at (8, 8, 8); the game turns
the part around that point. All parts of a droid share one texture.

    python3 tools/gen_droids.py            -> models, textures, item definitions, DroidParts.java
    python3 tools/gen_droids.py --preview  -> also whole droids for tools/preview_model.py in /tmp
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
import gen_blasters as gb  # noqa: E402

ASSETS = gb.ASSETS
FOLDER = "droid"
MODELS = os.path.join(ASSETS, "sabercraft", "models", FOLDER)
TEXTURES = os.path.join(ASSETS, "sabercraft", "textures", "item", FOLDER)
ITEMS = os.path.join(ASSETS, "sabercraft", "items", FOLDER)


def folders(folder):
    """Where a model kind goes (droid, ship): models, textures and item definitions."""
    return (os.path.join(ASSETS, "sabercraft", "models", folder), os.path.join(ASSETS, "sabercraft", "textures", "item", folder),
            os.path.join(ASSETS, "sabercraft", "items", folder))
JAVA = os.path.join("src", "client", "java", "com", "phiro", "sabercraft", "client", "droid", "DroidParts.java")
PREVIEW = "/tmp/claude-0/droids"

# Droid paints, added to the blaster materials.
gb.MATERIALS.update({
    "gold": ((214, 168, 72), "metal", {"brush": 0.06}),
    "bb_white": ((226, 226, 222), "polymer", {}),
    "dark_gold": ((150, 112, 50), "metal", {"brush": 0.06}),
    "r2_blue": ((38, 78, 168), "polymer", {}),
    "silver": ((176, 182, 192), "metal", {"brush": 0.05}),
    "bb_orange": ((228, 124, 42), "polymer", {}),
    "chop_orange": ((204, 116, 44), "polymer", {}),
    "chop_white": ((206, 202, 190), "polymer", {}),
    "b1_tan": ((198, 172, 120), "polymer", {}),
    "b1_dark": ((150, 126, 84), "polymer", {}),
    "k2_black": ((44, 46, 52), "polymer", {}),
    "k2_grey": ((78, 82, 90), "metal", {"brush": 0.05}),
    "lens_black": ((14, 14, 18), "lens", {}),
    "eye_white": ((235, 240, 250), "glow", {}),
    "eye_yellow": ((255, 214, 90), "glow", {}),
})
gb.EMISSIVE.update({"eye_white": 13, "eye_yellow": 12})

# Polished metal (C-3PO's gold, chrome trim): smooth with a broad sheen and soft edges, no
# brushing or plank-like borders, so round parts read as one shiny surface.
POLISHED = {"gold", "silver", "bb_white"}
_paint_face = gb.paint_face


def paint_face(material, deco, w, h, along_u, rng):
    if material not in POLISHED:
        return _paint_face(material, deco, w, h, along_u, rng)
    base = np.array(gb.MATERIALS[material][0], dtype=float)
    across = (np.arange(h if along_u else w) + 0.5) / (h if along_u else w)
    sheen = 0.97 + 0.08 * np.exp(-((across - 0.5) / 0.35) ** 2)
    img = np.ones((h, w, 3)) * base
    img *= sheen[:, None, None] if along_u else sheen[None, :, None]
    img *= 1.0 + 0.025 * (rng.random((h, w)) - 0.5)[:, :, None]
    if deco:
        img = gb.decorate(img, deco, w, h, along_u, rng, base)
    return np.clip(img, 0, 255)


gb.paint_face = paint_face


class Droid:
    """One droid: parts in groups, each group a part the game moves around its pivot."""

    def __init__(self, name, seed, height, width, folder=FOLDER):
        self.name = name
        self.folder = folder
        self.b = gb.Blaster(name, seed, False)
        self.groups = []  # names in order
        self.pivots = {}
        self.group_of = []  # group of each Part, in order
        self.current = None
        self.height = height
        self.width = width

    def part(self, name, pivot):
        self.groups.append(name)
        self.pivots[name] = pivot
        self.current = name
        return self

    def _added(self, before):
        self.group_of += [self.current] * (len(self.b.parts) - before)

    # Shapes, in droid space; x and z around the middle, so +-.
    def box(self, frm, to, material, deco=None, rotation=None, faces=None):
        n = len(self.b.parts)
        self.b.box(frm, to, material, deco, rotation, faces)
        self._added(n)

    def boxc(self, center, size, material, deco=None, rotation=None):
        n = len(self.b.parts)
        self.b.boxc(center, size, material, deco, rotation)
        self._added(n)

    def cyl(self, axis, center, a0, a1, r, material, deco=None, sides=8, tilt=None):
        n = len(self.b.parts)
        self.b.cyl(axis, center, a0, a1, r, material, deco, sides, tilt)
        self._added(n)

    def sphere(self, center, r, material):
        n = len(self.b.parts)
        self.b.sphere(center, r, material)
        self._added(n)

    def ball(self, center, r, material, layers=7, sides=12, bands=None):
        """A round ball from stacked discs along y; bands: {layer index: material}."""
        cx, cy, cz = center
        for i in range(layers):
            y0 = -r + 2 * r * i / layers
            y1 = -r + 2 * r * (i + 1) / layers
            ym = (y0 + y1) / 2
            rr = math.sqrt(max(r * r - ym * ym, 0.0)) * (1.0 / math.cos(math.pi / sides)) ** 0.0
            rr = max(rr, r * 0.25)
            m = (bands or {}).get(i, material)
            self.cyl("y", (cx, cz), cy + y0, cy + y1, rr, m, sides=sides)

    def dome(self, base_y, r, height, material, steps=5, sides=12, bands=None, cx=0.0, cz=0.0):
        """A dome from stacked discs, from base_y up."""
        for i in range(steps):
            y0 = base_y + height * i / steps
            y1 = base_y + height * (i + 1) / steps
            t = (i + 0.5) / steps
            rr = r * math.sqrt(max(1.0 - t * t, 0.0)) if i else r
            rr = max(rr, r * 0.3)
            m = (bands or {}).get(i, material)
            self.cyl("y", (cx, cz), y0, y1, rr, m, sides=sides)

    # Output.
    def write(self, preview=False):
        elements, img = self.b.build()
        models, textures, items = folders(self.folder)
        os.makedirs(models, exist_ok=True)
        os.makedirs(textures, exist_ok=True)
        os.makedirs(items, exist_ok=True)
        Image.fromarray(img.astype(np.uint8), "RGBA").save(os.path.join(textures, self.name + ".png"))
        texture = f"sabercraft:item/{self.folder}/" + self.name
        out = []
        for group in self.groups:
            px, py, pz = self.pivots[group]
            shift = (8.0 - px, 8.0 - py, 8.0 - pz)
            mine = []
            for element, g in zip(elements, self.group_of):
                if g != group:
                    continue
                e = json.loads(json.dumps(element))
                e["from"] = [gb.r4(e["from"][i] + shift[i]) for i in range(3)]
                e["to"] = [gb.r4(e["to"][i] + shift[i]) for i in range(3)]
                if "rotation" in e:
                    e["rotation"]["origin"] = [gb.r4(e["rotation"]["origin"][i] + shift[i]) for i in range(3)]
                for v in e["from"] + e["to"]:
                    assert -16 <= v <= 32, f"{self.name}/{group}: {v} out of the model range"
                mine.append(e)
            model = {"textures": {"blaster": texture, "particle": texture}, "elements": mine}
            gb.write_json(os.path.join(models, f"{self.name}_{group}.json"), model)
            gb.write_json(os.path.join(items, f"{self.name}_{group}.json"),
                          {"model": {"type": "minecraft:model", "model": f"sabercraft:{self.folder}/{self.name}_{group}"}})
            out.append((group, (px, py, pz), len(mine)))

        # The whole droid in one model, for icons (the droid menu, advancements): centred on
        # (8, 8, 8) and scaled to fit a slot, turned a little so the face shows.
        lo = [min(min(e["from"][i], e["to"][i]) for e in elements) for i in range(3)]
        hi = [max(max(e["from"][i], e["to"][i]) for e in elements) for i in range(3)]
        centre = [(lo[i] + hi[i]) / 2 for i in range(3)]
        icon = []
        for element in elements:
            e = json.loads(json.dumps(element))
            e["from"] = [gb.r4(e["from"][i] + 8 - centre[i]) for i in range(3)]
            e["to"] = [gb.r4(e["to"][i] + 8 - centre[i]) for i in range(3)]
            if "rotation" in e:
                e["rotation"]["origin"] = [gb.r4(e["rotation"]["origin"][i] + 8 - centre[i]) for i in range(3)]
            icon.append(e)
        fit = round(min(1.0, 15.0 / max(hi[i] - lo[i] for i in range(3))), 4)
        display = {
            "gui": {"rotation": [12, 155, 0], "translation": [0, 0, 0], "scale": [fit, fit, fit]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [fit, fit, fit]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [fit * 0.5] * 3},
        }
        gb.write_json(os.path.join(models, f"{self.name}_icon.json"),
                      {"textures": {"blaster": texture, "particle": texture}, "elements": icon, "display": display})
        gb.write_json(os.path.join(items, f"{self.name}_icon.json"),
                      {"model": {"type": "minecraft:model", "model": f"sabercraft:{self.folder}/{self.name}_icon"}})

        if preview:
            whole = []
            for element, g in zip(elements, self.group_of):
                e = json.loads(json.dumps(element))
                e["from"] = [gb.r4(e["from"][i] + (8 if i != 1 else 0)) for i in range(3)]
                e["to"] = [gb.r4(e["to"][i] + (8 if i != 1 else 0)) for i in range(3)]
                if "rotation" in e:
                    e["rotation"]["origin"] = [gb.r4(e["rotation"]["origin"][i] + (8 if i != 1 else 0)) for i in range(3)]
                whole.append(e)
            os.makedirs(PREVIEW, exist_ok=True)
            gb.write_json(os.path.join(PREVIEW, self.name + ".json"), {"textures": {"blaster": texture}, "elements": whole})
        print(f"{self.name}: {len(elements)} elements, texture {img.shape[0]} px, parts {[g for g, _, _ in out]}")
        return out


# ------------------------------------------------------------------------------------------------
# R2-D2: a blue and white astromech, 17 units tall. Barrel body on two legs, a turning dome.

def r2d2():
    d = Droid("r2d2", 2201, 17.5, 11)
    d.part("body", (0, 0, 0))
    # The barrel: white with a silver band at the top.
    d.cyl("y", (0, 0), 3.0, 12.6, 4.6, "white", sides=12)
    d.cyl("y", (0, 0), 12.6, 13.4, 4.7, "silver", sides=12)
    d.cyl("y", (0, 0), 2.4, 3.0, 4.2, "grey", sides=12)
    # Blue panels on the front and the sides.
    for x0, x1, y0, y1 in ((-2.6, -0.4, 8.6, 12.0), (0.4, 2.6, 8.6, 12.0), (-1.2, 1.2, 4.0, 7.6)):
        d.box((x0, y0, -4.75), (x1, y1, -4.45), "r2_blue", "panel")
    d.box((-3.2, 4.2, -4.35), (-1.6, 7.6, -4.05), "r2_blue")
    d.box((1.6, 4.2, -4.35), (3.2, 7.6, -4.05), "r2_blue")
    d.box((-0.8, 7.9, -4.85), (0.8, 8.4, -4.55), "silver", "slots")
    d.box((-2.0, 11.0, 4.3), (2.0, 12.2, 4.75), "r2_blue")
    d.box((-1.5, 5.0, 4.3), (1.5, 9.5, 4.7), "silver", "ribs")
    # Shoulders and legs.
    for side in (-1, 1):
        x = side * 5.3
        d.cyl("x", (11.0, 0.0), x - 0.9 if side > 0 else x - 0.7, x + 0.7 if side > 0 else x + 0.9, 1.8, "white", sides=8)
        d.cyl("x", (11.0, 0.0), x + (0.7 if side > 0 else -1.2), x + (1.2 if side > 0 else -0.7), 1.2, "r2_blue", sides=8)
        d.box((x - 0.8, 2.0, -1.5), (x + 0.8, 11.0, 1.5), "white", "panel")
        d.box((x - 0.85, 4.5, -1.2), (x + 0.85, 9.5, -0.2), "r2_blue")
        d.box((x - 1.1, 0.0, -2.6), (x + 1.1, 2.4, 2.6), "grey", "ribs")
        d.box((x - 0.6, 2.4, -1.8), (x + 0.6, 3.0, 1.8), "dark_steel")
    # Third leg in the middle.
    d.box((-0.9, 0.0, -1.6), (0.9, 2.4, 1.6), "grey")
    d.box((-0.6, 2.4, -0.8), (0.6, 3.0, 0.8), "dark_steel")

    d.part("head", (0, 13.4, 0))
    d.dome(13.4, 4.6, 4.2, "silver", steps=6, sides=12, bands={1: "r2_blue"})
    d.boxc((0, 15.8, -3.85), (1.6, 1.6, 0.8), "lens_black")
    d.boxc((0, 15.8, -4.3), (0.7, 0.7, 0.2), "led_red")
    d.boxc((-2.3, 14.4, -3.6), (1.4, 1.0, 0.5), "r2_blue")
    d.boxc((2.3, 14.4, -3.6), (1.4, 1.0, 0.5), "r2_blue")
    d.boxc((1.9, 16.2, -3.0), (1.0, 0.8, 0.5), "led_blue")
    d.boxc((-1.6, 17.0, -2.4), (0.7, 0.6, 0.4), "led_red")
    d.boxc((0, 14.3, 4.2), (2.4, 0.9, 0.5), "r2_blue")
    return d


# ------------------------------------------------------------------------------------------------
# C-3PO: a golden protocol droid, 28 units tall. Head, torso, two arms and two legs.

def humanoid_limbs(d, mat, joint, hip_y, shoulder_y, shoulder_x, arm_len, leg_len, r_arm, r_leg, hand, foot, right_shin=None):
    for side, name in ((-1, "arm_r"), (1, "arm_l")):
        # Right is -x seen from behind with the face at -z? The droid looks to -z, its right hand is at -x.
        x = side * shoulder_x
        d.part(name, (x, shoulder_y, 0))
        d.cyl("x", (shoulder_y, 0), x - 1.0, x + 1.0, r_arm * 1.25, joint)
        d.cyl("y", (x, 0), shoulder_y - arm_len * 0.5, shoulder_y - 0.6, r_arm, mat, sides=8)
        d.cyl("x", (shoulder_y - arm_len * 0.5, 0), x - r_arm * 0.9, x + r_arm * 0.9, r_arm * 0.9, joint)
        d.cyl("y", (x, 0), shoulder_y - arm_len + 1.2, shoulder_y - arm_len * 0.5, r_arm * 0.9, mat, sides=8)
        d.boxc((x, shoulder_y - arm_len + 0.4, 0), (r_arm * 1.6, 1.8, r_arm * 2.2), hand)
    for side, name in ((-1, "leg_r"), (1, "leg_l")):
        x = side * 1.9
        d.part(name, (x, hip_y, 0))
        d.cyl("y", (x, 0), hip_y - leg_len * 0.5, hip_y, r_leg, mat, sides=8)
        d.cyl("x", (hip_y - leg_len * 0.5, 0), x - r_leg * 0.95, x + r_leg * 0.95, r_leg * 0.95, joint)
        shin = right_shin if (right_shin and side < 0) else mat
        d.cyl("y", (x, 0), 1.4, hip_y - leg_len * 0.5, r_leg * 0.9, shin, sides=8)
        d.box((x - r_leg * 1.05, 0.0, -r_leg * 1.9), (x + r_leg * 1.05, 1.4, r_leg * 1.1), foot)


def c3po():
    d = Droid("c3po", 3301, 28.0, 10)
    d.part("torso", (0, 13.0, 0))
    # Pelvis, the open middle with its wiring, the chest.
    d.boxc((0, 13.6, 0), (6.4, 2.2, 3.6), "gold")
    d.cyl("y", (0, 0), 14.6, 17.6, 1.4, "dark_steel")
    for x, m in ((-1.6, "copper"), (1.5, "red_paint"), (0.2, "brass")):
        d.cyl("y", (x, -0.6), 14.7, 17.5, 0.45, m, sides=8)
    d.boxc((0, 20.6, 0), (8.0, 6.4, 4.4), "gold", "panel")
    d.boxc((0, 20.4, -2.25), (5.4, 3.6, 0.3), "dark_gold", "ribs")
    d.boxc((0, 21.3, -2.45), (1.4, 1.4, 0.2), "steel", "dial")
    d.boxc((0, 17.8, 0), (5.8, 1.2, 3.4), "gold", "ribs")
    d.boxc((0, 24.0, 0), (5.0, 0.8, 3.2), "dark_gold")
    d.cyl("y", (0, 0), 24.0, 25.0, 0.8, "dark_steel")
    # Shoulder caps.
    for side in (-1, 1):
        d.boxc((side * 4.3, 23.0, 0), (1.6, 1.6, 3.2), "gold")
    humanoid_limbs(d, "gold", "dark_steel", 12.6, 22.6, 4.9, 9.6, 12.4, 1.05, 1.35, "gold", "gold", right_shin="silver")

    d.part("head", (0, 25.0, 0))
    d.cyl("y", (0, 0), 25.6, 29.4, 2.5, "gold", sides=12)
    d.dome(29.4, 2.5, 1.3, "gold", steps=3, sides=12)
    for x in (-1.0, 1.0):
        d.cyl("z", (x, 28.0), -2.75, -2.35, 1.05, "dark_gold", sides=8)
        d.cyl("z", (x, 28.0), -2.95, -2.65, 0.75, "eye_yellow", sides=8)
    d.boxc((0, 26.5, -2.55), (1.4, 0.5, 0.2), "bore")
    d.boxc((0, 27.3, -2.6), (0.5, 1.2, 0.3), "gold")
    for x in (-2.6, 2.6):
        d.cyl("x", (27.8, 0.0), x - 0.35, x + 0.35, 1.0, "dark_gold", sides=8)
    d.boxc((0, 25.3, 0), (1.8, 0.8, 1.8), "dark_steel")
    return d


# ------------------------------------------------------------------------------------------------
# BB-8: an orange and white ball droid, 11 units tall. The ball rolls, the head rides on top.

def bb8():
    d = Droid("bb8", 8801, 11.0, 7)
    r = 4.3
    d.part("body", (0, r, 0))
    d.ball((0, r, 0), r, "bb_white", layers=7, sides=12)
    # Orange circles with silver centers on the sides and the front and back (they roll along).
    for axis, sign in (("x", -1), ("x", 1), ("z", -1), ("z", 1)):
        a0, a1 = (sign * r * 0.9, sign * (r + 0.08)) if sign > 0 else (sign * (r + 0.08), sign * r * 0.9)
        d.cyl(axis, (r, 0) if axis == "x" else (0, r), a0, a1, 2.5, "bb_orange", sides=12)
        b0, b1 = (sign * (r + 0.08), sign * (r + 0.16)) if sign > 0 else (sign * (r + 0.16), sign * (r + 0.08))
        d.cyl(axis, (r, 0) if axis == "x" else (0, r), b0, b1, 1.4, "silver", "dial", sides=12)
    d.cyl("y", (0, 0), r * 2 - 0.15, r * 2 + 0.1, 1.9, "bb_orange", sides=12)
    d.cyl("y", (0, 0), -0.1, 0.15, 1.9, "bb_orange", sides=12)

    d.part("head", (0, r * 2, 0))
    base = r * 2 + 0.2
    d.cyl("y", (0, 0), base, base + 0.5, 2.6, "silver", sides=12)
    d.dome(base + 0.5, 2.8, 2.2, "bb_white", steps=4, sides=12, bands={0: "bb_orange", 2: "silver"})
    d.cyl("z", (0, base + 1.6), -2.95, -2.5, 0.9, "lens_black", sides=8)
    d.cyl("z", (1.4, base + 0.9), -2.7, -2.3, 0.35, "lens_black", sides=8)
    d.cyl("y", (0.9, 0.4), base + 2.5, base + 5.0, 0.12, "steel", sides=8)
    d.cyl("y", (0.4, 0.9), base + 2.5, base + 4.0, 0.12, "steel", sides=8)
    return d


# ------------------------------------------------------------------------------------------------
# Chopper (C1-10P): a worn, grumpy astromech, 16 units tall, orange and white with patched panels.

def chopper():
    d = Droid("chopper", 1001, 16.0, 10)
    d.part("body", (0, 0, 0))
    d.cyl("y", (0, 0), 3.2, 11.0, 4.0, "chop_white", sides=12)
    d.cyl("y", (0, 0), 11.0, 11.6, 4.1, "chop_orange", sides=12)
    d.cyl("y", (0, 0), 2.6, 3.2, 3.6, "grey", sides=12)
    d.box((-2.2, 5.2, -4.15), (2.2, 9.6, -3.85), "chop_orange", "panel")
    d.box((-1.0, 3.8, -4.1), (1.6, 4.8, -3.8), "rust")
    d.box((2.6, 6.0, -3.5), (3.6, 9.0, -2.9), "grey", "slots")
    d.box((-3.2, 6.4, 3.3), (0.8, 9.8, 3.9), "chop_orange")
    # Two mismatched arms folding out of the body, and the stubby legs.
    for side, m in ((-1, "chop_orange"), (1, "grey")):
        x = side * 4.6
        d.cyl("x", (9.6, 0), x - 0.6, x + 0.6, 1.0, "dark_steel")
        d.box((x - 0.4, 6.0, -0.4), (x + 0.4, 9.6, 0.4), m)
        d.box((x - 0.5, 5.0, -1.6), (x + 0.5, 6.0, 0.4), "dark_steel")
        d.box((x - 0.3, 5.0, -2.2), (x + 0.3, 5.6, -1.6), "steel")
    for side in (-1, 1):
        x = side * 4.6
        d.box((x - 0.8, 1.6, -1.2), (x + 0.8, 5.6, 1.2), "chop_white", "panel")
        d.box((x - 1.0, 0.0, -2.2), (x + 1.0, 1.8, 2.2), "grey", "ribs")
    d.box((-0.8, 0.0, -1.4), (0.8, 2.6, 1.4), "grey")

    d.part("head", (0, 11.6, 0))
    d.dome(11.6, 4.0, 3.6, "chop_white", steps=5, sides=12, bands={1: "chop_orange", 3: "grey"})
    d.boxc((0, 13.6, -3.4), (1.6, 1.6, 0.8), "lens_black")
    d.boxc((0, 13.6, -3.85), (0.7, 0.7, 0.2), "led_amber")
    d.boxc((-2.0, 12.6, -3.2), (1.0, 0.8, 0.6), "rust")
    # Antennas, one bent.
    d.cyl("y", (1.2, 1.0), 14.8, 17.6, 0.15, "steel")
    d.cyl("y", (-1.0, 1.4), 14.8, 16.6, 0.15, "steel")
    d.boxc((-1.0, 16.7, 1.4), (0.5, 0.5, 0.5), "led_red")
    return d


# ------------------------------------------------------------------------------------------------
# Mouse droid (MSE-6): a small black box on wheels, 5 units tall.

def mouse():
    d = Droid("mouse", 6001, 5.0, 6)
    d.part("body", (0, 0, 0))
    d.box((-2.6, 1.0, -4.0), (2.6, 4.2, 4.0), "black", "panel")
    d.box((-2.2, 4.2, -3.4), (2.2, 4.8, 3.2), "matte", "slots")
    d.box((-2.0, 1.4, -4.3), (2.0, 3.6, -4.0), "dark_steel", "ribs")
    d.boxc((-1.2, 3.0, -4.4), (0.6, 0.6, 0.2), "led_red")
    d.boxc((1.2, 3.0, -4.4), (0.6, 0.6, 0.2), "led_blue")
    d.box((-2.8, 0.6, 3.2), (2.8, 3.6, 4.2), "dark_steel")
    for x in (-2.9, 2.9):
        for z in (-2.6, 2.6):
            d.cyl("x", (1.0, z), x - 0.5 if x > 0 else x - 0.1, x + 0.1 if x > 0 else x + 0.5, 1.0, "rubber", sides=8)
    d.cyl("y", (1.4, 3.6), 4.8, 6.4, 0.12, "steel")
    return d


# ------------------------------------------------------------------------------------------------
# Probe droid: a floating black head with red eyes and hanging arms; the whole droid bobs.

def probe():
    d = Droid("probe", 7001, 30.0, 10)
    d.part("body", (0, 24.0, 0))
    d.ball((0, 26.0, 0), 4.2, "black", layers=7, sides=12, bands={3: "matte"})
    for i in range(6):
        a = i * math.tau / 6 + 0.3
        x, z = math.cos(a) * 3.9, math.sin(a) * 3.9
        d.boxc((x, 26.6, z), (1.0, 1.0, 1.0), "lens_red")
        d.boxc((x * 1.05, 26.6, z * 1.05), (0.4, 0.4, 0.4), "led_red")
    d.boxc((0, 26.4, -4.2), (1.6, 1.6, 0.6), "lens_red")
    # Antennas up.
    for x, z, h in ((-1.5, 0.5, 7.0), (1.2, -0.8, 9.0), (0.4, 1.6, 5.5)):
        d.cyl("y", (x, z), 29.6, 29.6 + h, 0.15, "steel")
    d.boxc((1.2, 38.8, -0.8), (0.4, 0.4, 0.4), "led_red")
    # Body below and the hanging arms.
    d.cyl("y", (0, 0), 19.6, 22.4, 2.4, "matte", "ribs")
    d.cyl("y", (0, 0), 18.8, 19.6, 1.6, "dark_steel")
    for i in range(5):
        a = i * math.tau / 5
        x, z = math.cos(a) * 1.6, math.sin(a) * 1.6
        length = 9.0 + (i % 2) * 3.0
        d.cyl("y", (x, z), 19.6 - length, 21.0, 0.25, "dark_steel")
        d.boxc((x, 19.4 - length, z), (0.8, 0.8, 0.8), "steel")
    return d


# ------------------------------------------------------------------------------------------------
# B1 battle droid: thin tan limbs, a long head, 30 units tall.

def b1():
    d = Droid("b1", 1101, 30.0, 8)
    d.part("torso", (0, 15.4, 0))
    d.boxc((0, 15.6, 0), (3.2, 1.4, 2.2), "b1_tan")
    d.cyl("y", (0, 0), 16.2, 19.0, 0.5, "b1_dark")
    d.boxc((0, 21.8, 0), (5.6, 4.6, 2.6), "b1_tan", "panel")
    d.boxc((0, 21.8, 1.9), (3.0, 4.0, 1.4), "b1_dark", "slots")
    d.boxc((0, 23.7, 0), (6.8, 0.8, 2.0), "b1_tan")
    d.cyl("y", (0, 0), 24.1, 25.8, 0.35, "b1_dark")
    humanoid_limbs(d, "b1_tan", "b1_dark", 15.2, 23.2, 3.8, 11.0, 14.6, 0.55, 0.7, "b1_dark", "b1_tan")

    d.part("head", (0, 25.8, 0))
    # The long snout pointing forward and down, the bulb at the back.
    d.boxc((0, 27.4, 0.4), (2.0, 2.8, 2.6), "b1_tan")
    d.box((-0.7, 26.8, -5.0), (0.7, 28.0, -0.8), "b1_tan", rotation={"origin": [0, 27.4, -0.8], "axis": "x", "angle": -22.5})
    d.box((-0.5, 26.9, -5.4), (0.5, 27.7, -4.8), "b1_dark", rotation={"origin": [0, 27.4, -0.8], "axis": "x", "angle": -22.5})
    d.boxc((-1.05, 27.9, -0.6), (0.2, 0.7, 0.7), "bore")
    d.boxc((1.05, 27.9, -0.6), (0.2, 0.7, 0.7), "bore")
    d.cyl("y", (0, 1.5), 26.0, 26.8, 0.4, "b1_dark")
    return d


# ------------------------------------------------------------------------------------------------
# K-2SO: a tall, dark security droid, 34 units tall, with white eyes.

def k2so():
    d = Droid("k2so", 2001, 34.0, 9)
    d.part("torso", (0, 17.0, 0))
    d.boxc((0, 17.6, 0), (4.6, 1.8, 2.8), "k2_black")
    d.cyl("y", (0, 0), 18.4, 21.4, 0.9, "k2_grey", "ribs")
    d.boxc((0, 24.6, 0), (7.6, 6.4, 3.6), "k2_black", "panel")
    d.boxc((0, 25.6, -1.95), (5.2, 3.0, 0.3), "k2_grey", "slots")
    d.boxc((0, 22.0, 0), (5.0, 1.0, 3.0), "k2_grey")
    for side in (-1, 1):
        d.boxc((side * 4.2, 27.2, 0), (1.8, 1.4, 3.2), "k2_grey")
    d.cyl("y", (0, 0), 27.8, 29.6, 0.6, "k2_grey")
    humanoid_limbs(d, "k2_black", "k2_grey", 16.8, 26.8, 4.7, 15.4, 16.6, 0.85, 1.0, "k2_grey", "k2_black")

    d.part("head", (0, 29.6, 0))
    d.boxc((0, 31.2, -0.2), (3.6, 3.4, 4.4), "k2_black", "panel")
    d.boxc((0, 32.9, 0.2), (3.0, 0.6, 3.6), "k2_grey")
    for x in (-0.95, 0.95):
        d.boxc((x, 31.9, -2.45), (0.9, 0.7, 0.2), "eye_white")
    d.boxc((0, 30.4, -2.45), (2.4, 0.6, 0.2), "k2_grey")
    return d


DROIDS = [r2d2, c3po, bb8, chopper, mouse, probe, b1, k2so]


def write_java(rigs):
    lines = [
        "package com.phiro.sabercraft.client.droid;",
        "",
        "import java.util.List;",
        "import java.util.Map;",
        "",
        "/**",
        " * The parts of each droid model and the points they turn around, in blocks from the droid's",
        " * feet (x right, y up, the face looking to -z). Generated by tools/gen_droids.py, do not edit.",
        " */",
        "public final class DroidParts {",
        "\t/** One part: its name, its item model, and its pivot. */",
        "\tpublic record Part(String name, String model, float x, float y, float z) {",
        "\t}",
        "",
        "\tpublic static final Map<String, List<Part>> PARTS = Map.of(",
    ]
    entries = []
    for name, parts in rigs:
        inner = ",\n".join(
            f"\t\t\t\tnew Part(\"{g}\", \"sabercraft:droid/{name}_{g}\", {p[0] / 16:.4f}F, {p[1] / 16:.4f}F, {p[2] / 16:.4f}F)" for g, p, _ in parts)
        entries.append(f"\t\t\t\"{name}\", List.of(\n{inner}\n\t\t\t)")
    lines.append(",\n".join(entries))
    lines += ["\t);", "", "\tprivate DroidParts() {", "\t}", "}", ""]
    os.makedirs(os.path.dirname(JAVA), exist_ok=True)
    with open(JAVA, "w") as f:
        f.write("\n".join(lines))


def main():
    os.makedirs(MODELS, exist_ok=True)
    os.makedirs(ITEMS, exist_ok=True)
    preview = "--preview" in sys.argv
    rigs = []
    for make in DROIDS:
        d = make()
        rigs.append((d.name, d.write(preview)))
    write_java(rigs)


if __name__ == "__main__":
    main()
