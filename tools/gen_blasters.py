#!/usr/bin/env python3
"""
Generates the 3D blaster models (Phase 11, replacing the simple box models of Phase 10).

Every blaster is an own design after the look of a known blaster from the films and series, built
here from scratch out of boxes: round parts (barrels, scopes, coils) are 8- or 12-sided prisms made
of two or three boxes turned around their axis, so they read as round from every side. Each face
gets its own place in a texture painted here (no external resources): brushed metal, matte
polymer, wood grain, rubber grips with ribs, vent holes, panel lines, glass lenses and small lights
that glow in the dark (light_emission). Run from the repository root:

    python3 tools/gen_blasters.py

Needs numpy and Pillow. Writes into src/client/resources/assets/:

    sabercraft/models/item/blaster/<type>.json
    sabercraft/textures/item/blaster/<type>.png
    minecraft/items/bow.json

Model space: the barrel points along -Z (the muzzle is at small z), up is +Y, the barrel axis is at
x = 8. The models are built at twice the size of a normal item (1 unit = 1/32 block when held) so
that small parts stay possible; the display transforms scale them back. The blaster type of a bow
comes from its minecraft:custom_model_data component: strings[0] is the type id (see
BlasterType.java). A bow without one keeps the vanilla bow model, drawing included. Held blasters
also get the muzzle flash (item model sabercraft:blaster_flash, see BlasterFlashItemModel.java).
"""
import json
import math
import os

import numpy as np
from PIL import Image

ASSETS = os.path.join("src", "client", "resources", "assets")
MODELS = os.path.join(ASSETS, "sabercraft", "models", "item", "blaster")
TEXTURES = os.path.join(ASSETS, "sabercraft", "textures", "item", "blaster")
ITEMS = os.path.join(ASSETS, "minecraft", "items")

# Texture pixels per model unit.
PX = 4

# ------------------------------------------------------------------------------------------------
# Materials: base color and how the surface is painted.

MATERIALS = {
    # name: (rgb, kind, options)
    "gunmetal": ((74, 78, 87), "metal", {"brush": 0.07}),
    "black": ((40, 41, 46), "polymer", {}),
    "matte": ((56, 57, 63), "polymer", {}),
    "steel": ((150, 155, 164), "metal", {"brush": 0.10}),
    "chrome": ((196, 200, 208), "metal", {"brush": 0.06}),
    "dark_steel": ((92, 96, 104), "metal", {"brush": 0.08}),
    "wood": ((112, 70, 40), "wood", {}),
    "darkwood": ((70, 44, 28), "wood", {}),
    "leather": ((86, 58, 38), "leather", {}),
    "rubber": ((34, 34, 38), "rubber", {}),
    "white": ((222, 224, 226), "polymer", {}),
    "grey": ((126, 128, 134), "polymer", {}),
    "olive": ((88, 92, 60), "polymer", {}),
    "tan": ((176, 150, 104), "polymer", {}),
    "orange": ((206, 112, 38), "polymer", {}),
    "brass": ((176, 138, 62), "metal", {"brush": 0.08}),
    "copper": ((176, 98, 58), "metal", {"brush": 0.08}),
    "rust": ((128, 72, 44), "polymer", {}),
    "blue_paint": ((52, 92, 170), "polymer", {}),
    "red_paint": ((168, 40, 32), "polymer", {}),
    "bore": ((10, 10, 12), "flat", {}),
    "lens": ((40, 70, 110), "lens", {}),
    "lens_red": ((140, 30, 30), "lens", {}),
    "led_red": ((255, 60, 40), "glow", {}),
    "led_blue": ((70, 150, 255), "glow", {}),
    "led_green": ((80, 255, 110), "glow", {}),
    "led_amber": ((255, 180, 50), "glow", {}),
    "orb": ((150, 210, 160), "orb", {}),
}

# Lights glow in the dark.
EMISSIVE = {"led_red": 15, "led_blue": 15, "led_green": 15, "led_amber": 15, "orb": 12, "lens": 4, "lens_red": 6}


def rng_for(seed):
    return np.random.default_rng(seed)


def paint_face(material, deco, w, h, along_u, rng):
    """Paints one face of w x h pixels. along_u: the long axis of the part runs along u (else v)."""
    rgb, kind, opts = MATERIALS[material]
    base = np.array(rgb, dtype=float)
    img = np.ones((h, w, 3)) * base

    if kind == "metal":
        # Fine brushing along the part, a soft sheen across it.
        n = rng.random(h if along_u else w)
        lines = 1.0 + opts.get("brush", 0.08) * (n - 0.5)
        img *= lines[:, None, None] if along_u else lines[None, :, None]
        across = (np.arange(h if along_u else w) + 0.5) / (h if along_u else w)
        sheen = 0.9 + 0.22 * np.exp(-((across - 0.35) / 0.25) ** 2)
        img *= sheen[:, None, None] if along_u else sheen[None, :, None]
        # A few scratches.
        for _ in range(max(1, (w * h) // 220)):
            y, x = rng.integers(0, h), rng.integers(0, w)
            length = rng.integers(2, 6)
            for k in range(length):
                xx, yy = (x + k, y) if along_u else (x, y + k)
                if 0 <= xx < w and 0 <= yy < h:
                    img[yy, xx] = np.minimum(img[yy, xx] * 1.25 + 8, 255)
    elif kind == "polymer":
        img *= 1.0 + 0.05 * (rng.random((h, w)) - 0.5)[:, :, None]
        # Edge wear: lighter specks near the border.
        for _ in range(max(1, (w + h) // 10)):
            if rng.random() < 0.5:
                y, x = rng.integers(0, h), rng.choice([0, w - 1])
            else:
                y, x = rng.choice([0, h - 1]), rng.integers(0, w)
            img[y, x] = np.minimum(img[y, x] * 1.35 + 14, 255)
    elif kind == "wood":
        # Grain along the part with darker rings.
        for i in range(h if along_u else w):
            stripe = 0.86 + 0.18 * math.sin(i * 1.7 + rng.random() * 0.8) + 0.06 * rng.random()
            if along_u:
                img[i, :] *= stripe
            else:
                img[:, i] *= stripe
        img *= 1.0 + 0.06 * (rng.random((h, w)) - 0.5)[:, :, None]
    elif kind == "leather":
        img *= 1.0 + 0.12 * (rng.random((h, w)) - 0.5)[:, :, None]
        for i in range(0, w + h, 4):
            for y in range(h):
                x = i - y
                if 0 <= x < w:
                    img[y, x] *= 0.72
    elif kind == "rubber":
        img *= 1.0 + 0.08 * (rng.random((h, w)) - 0.5)[:, :, None]
    elif kind == "lens":
        yy, xx = np.mgrid[0:h, 0:w]
        t = (xx + yy) / max(1, w + h - 2)
        img = base[None, None, :] * (0.55 + 0.9 * (1 - t))[:, :, None]
        # A white glint.
        gx, gy = max(0, w // 3 - 1), max(0, h // 3 - 1)
        img[gy:gy + max(1, h // 4), gx:gx + max(1, w // 4)] = (235, 240, 250)
    elif kind == "glow":
        yy, xx = np.mgrid[0:h, 0:w]
        cx, cy = (w - 1) / 2, (h - 1) / 2
        r = np.hypot((xx - cx) / max(1, w / 2), (yy - cy) / max(1, h / 2))
        img = base[None, None, :] * (1.15 - 0.35 * np.clip(r, 0, 1))[:, :, None] + 30 * (r < 0.5)[:, :, None]
    elif kind == "orb":
        yy, xx = np.mgrid[0:h, 0:w]
        cx, cy = (w - 1) * 0.4, (h - 1) * 0.4
        r = np.hypot(xx - cx, yy - cy) / max(1, max(w, h))
        img = base[None, None, :] * (1.25 - 0.9 * r)[:, :, None]
    elif kind == "flat":
        pass

    if deco:
        img = decorate(img, deco, w, h, along_u, rng, base)

    # Bevel: a light top-left edge and a dark bottom-right edge, so every part has crisp borders.
    if w >= 3 and h >= 3 and kind not in ("glow", "lens", "orb", "flat"):
        img[0, :] = np.minimum(img[0, :] * 1.18 + 6, 255)
        img[:, 0] = np.minimum(img[:, 0] * 1.12 + 4, 255)
        img[-1, :] *= 0.66
        img[:, -1] *= 0.74
    return np.clip(img, 0, 255)


def decorate(img, deco, w, h, along_u, rng, base):
    """Details painted onto a face. deco is a string or a tuple (name, option)."""
    name, opt = (deco, None) if isinstance(deco, str) else deco
    L, A = (w, h) if along_u else (h, w)  # length and width in pixels

    def put(l, a, value, mul=False):
        x, y = (l, a) if along_u else (a, l)
        if 0 <= x < w and 0 <= y < h:
            if mul:
                img[y, x] *= value
            else:
                img[y, x] = value

    if name == "holes":
        # Rows of round vent holes, like a perforated barrel jacket.
        period = opt or 5
        rows = range(2, A - 2, period) if A >= 6 else [max(0, A // 2 - 1)]
        for l in range(2, L - 2, period):
            for a in rows:
                for dl in (0, 1):
                    for da in (0, 1):
                        put(l + dl, a + da, (8, 8, 10))
                put(l - 1, a, 1.3, True)
                put(l, a - 1, 1.3, True)
    elif name == "slots":
        # Long cooling slots across the middle.
        period = opt or 4
        a0, a1 = A // 4, A - A // 4
        for l in range(2, L - 2, period):
            for a in range(a0, a1):
                put(l, a, (10, 10, 12))
                put(l + 1, a, 0.6, True)
    elif name == "ribs":
        # Raised ribs across the part (grips, fins).
        period = opt or 3
        for l in range(0, L):
            phase = l % period
            for a in range(A):
                if phase == 0:
                    put(l, a, 1.35, True)
                elif phase == period - 1:
                    put(l, a, 0.55, True)
    elif name == "panel":
        # An inset panel line around the face and a couple of screws.
        for l in range(1, L - 1):
            put(l, 1, 0.55, True)
            put(l, A - 2, 0.55, True)
        for a in range(1, A - 1):
            put(1, a, 0.55, True)
            put(L - 2, a, 0.55, True)
        for l, a in ((3, 3), (L - 4, A - 4)):
            if L > 8 and A > 8:
                put(l, a, (200, 204, 210))
    elif name == "knurl":
        for l in range(L):
            for a in range(A):
                if (l + a) % 3 == 0 or (l - a) % 3 == 0:
                    put(l, a, 0.7, True)
    elif name == "stripe":
        # A colored stripe along the part (clone and rebel markings).
        color = MATERIALS[opt][0]
        for l in range(L):
            for a in range(A // 3, A - A // 3):
                put(l, a, color)
    elif name == "dial":
        # Marks around a dial face.
        cx, cy = (w - 1) / 2, (h - 1) / 2
        for k in range(12):
            ang = k * math.pi / 6
            x = int(round(cx + math.cos(ang) * (w / 2 - 1.5)))
            y = int(round(cy + math.sin(ang) * (h / 2 - 1.5)))
            if 0 <= x < w and 0 <= y < h:
                img[y, x] = (220, 220, 220)
    elif name == "grain_bands":
        # Brass bands on wood (bowcaster).
        for l in range(0, L, opt or 8):
            for a in range(A):
                put(l, a, MATERIALS["brass"][0])
                put(l + 1, a, MATERIALS["brass"][0])
    return img


# ------------------------------------------------------------------------------------------------
# Geometry.

class Part:
    def __init__(self, frm, to, material, deco=None, rotation=None, faces=None, long_axis=None):
        self.frm = [float(v) for v in frm]
        self.to = [float(v) for v in to]
        self.material = material
        self.deco = deco
        self.rotation = rotation
        self.faces = faces or ("north", "south", "east", "west", "up", "down")
        size = [self.to[i] - self.frm[i] for i in range(3)]
        self.long_axis = long_axis if long_axis is not None else int(np.argmax(size))


# Which world axes u and v of each face follow (vanilla's default face UVs).
FACE_AXES = {
    "north": (0, 1), "south": (0, 1), "east": (2, 1), "west": (2, 1), "up": (0, 2), "down": (0, 2),
}


class Blaster:
    def __init__(self, name, seed, rifle):
        self.name = name
        self.rng = rng_for(seed)
        self.rifle = rifle
        self.parts = []
        self.muzzle = None
        self.grip = None

    # Basic shapes ------------------------------------------------------------------------------

    def box(self, frm, to, material, deco=None, rotation=None, faces=None, long_axis=None):
        self.parts.append(Part(frm, to, material, deco, rotation, faces, long_axis))

    def boxc(self, center, size, material, deco=None, rotation=None, long_axis=None):
        frm = [center[i] - size[i] / 2 for i in range(3)]
        to = [center[i] + size[i] / 2 for i in range(3)]
        self.box(frm, to, material, deco, rotation, long_axis=long_axis)

    def cyl(self, axis, center, a0, a1, r, material, deco=None, sides=8, tilt=None):
        """A round part along an axis ('x', 'y' or 'z'): center = the two other coordinates, from a0
        to a1 along the axis, apothem r, 8 or 12 sides. Built from planks as wide as one side and
        as long as the diameter, turned in steps around the axis: their ends are exactly the sides
        of a regular polygon, so the part is round without corners sticking out. Only the outer
        ends and the caps are kept. The turned planks are a hair shorter, so their caps do not
        flicker against each other."""
        ai = "xyz".index(axis)
        others = [i for i in range(3) if i != ai]
        n = sides // 2
        half_side = r * math.tan(math.pi / sides)
        names = {0: ("west", "east"), 1: ("down", "up"), 2: ("north", "south")}
        name = deco if isinstance(deco, str) else (deco or (None,))[0]
        repeat = name in ("holes", "slots", "ribs", "knurl", "stripe")
        for k in range(n):
            angle = k * 180.0 / n
            swapped = angle == 90.0
            if angle > 90.0:
                angle -= 180.0
            inset = 0.012 * k
            frm, to = [0.0] * 3, [0.0] * 3
            frm[ai], to[ai] = a0 + inset, a1 - inset
            long_dir, short_dir = (others[1], others[0]) if swapped else (others[0], others[1])
            c = {others[0]: center[0], others[1]: center[1]}
            frm[long_dir], to[long_dir] = c[long_dir] - r, c[long_dir] + r
            frm[short_dir], to[short_dir] = c[short_dir] - half_side, c[short_dir] + half_side
            origin = [0.0] * 3
            origin[ai] = (a0 + a1) / 2
            origin[others[0]], origin[others[1]] = center[0], center[1]
            rotation = None
            if angle and not swapped:
                rotation = {"origin": origin, "axis": axis, "angle": angle}
            if tilt:
                rotation = combine(rotation, tilt)
            faces = names[ai] + names[long_dir]
            self.box(frm, to, material, deco if k == 0 or repeat else None, rotation, faces=faces, long_axis=ai)

    def ring(self, z, thickness, r, material, cx=8.0, cy=10.0, sides=8):
        self.cyl("z", (cx, cy), z, z + thickness, r, material, sides=sides)

    def sphere(self, center, r, material):
        """A round knob: three crossed slabs, each a little thinner than the one before, so no two
        faces lie in the same plane. From every axis it shows a rounded, notched outline."""
        for k, axis in enumerate((0, 1, 2)):
            a = r * (0.68 - 0.025 * k)
            frm = [center[i] - (r if i == axis else a) for i in range(3)]
            to = [center[i] + (r if i == axis else a) for i in range(3)]
            self.box(frm, to, material)

    # Output -------------------------------------------------------------------------------------

    def bounds(self):
        lo = np.min([p.frm for p in self.parts], axis=0)
        hi = np.max([p.to for p in self.parts], axis=0)
        return lo, hi

    def build(self):
        faces = []  # (part index, face name, w px, h px)
        for i, p in enumerate(self.parts):
            for f in p.faces:
                ua, va = FACE_AXES[f]
                w = max(1, int(round((p.to[ua] - p.frm[ua]) * PX)))
                h = max(1, int(round((p.to[va] - p.frm[va]) * PX)))
                faces.append((i, f, w, h))

        size, places = pack([(w + 2, h + 2) for _, _, w, h in faces])
        img = np.zeros((size, size, 4))
        elements = []
        uvs = {}
        for (i, f, w, h), (x, y) in zip(faces, places):
            p = self.parts[i]
            ua, va = FACE_AXES[f]
            along_u = p.long_axis == ua or (p.long_axis != va and w >= h)
            tile = paint_face(p.material, p.deco if f not in ("up", "down") or p.long_axis != 1 else None, w, h, along_u, self.rng)
            # One pixel of the face's own border around it, so mipmaps do not bleed in other colors.
            padded = np.pad(tile, ((1, 1), (1, 1), (0, 0)), mode="edge")
            img[y:y + h + 2, x:x + w + 2, :3] = padded
            img[y:y + h + 2, x:x + w + 2, 3] = 255
            k = 16.0 / size
            uvs[(i, f)] = [r4((x + 1) * k), r4((y + 1) * k), r4((x + 1 + w) * k), r4((y + 1 + h) * k)]

        for i, p in enumerate(self.parts):
            element = {
                "from": [r4(v) for v in p.frm],
                "to": [r4(v) for v in p.to],
                "faces": {f: {"uv": uvs[(i, f)], "texture": "#blaster"} for f in p.faces},
            }
            if p.rotation:
                rot = dict(p.rotation)
                rot["origin"] = [r4(v) for v in rot["origin"]]
                element["rotation"] = rot
            if p.material in EMISSIVE:
                element["light_emission"] = EMISSIVE[p.material]
            elements.append(element)
        return elements, img

    def display(self):
        lo, hi = self.bounds()
        gy = (self.grip[1] - 8.0)
        gz = (self.grip[2] - 8.0)
        gx = (self.grip[0] - 8.0)
        s3 = 0.42
        # Players hold blasters aimed like a loaded crossbow (AvatarRendererMixin): the hand
        # closes around the grip, the barrel points forward.
        third = {"rotation": [0, 0, 0], "translation": r2([-gx * s3, -gy * s3 + 1.0, -gz * s3 - 0.5]), "scale": [s3] * 3}
        s1 = 0.3
        first = {"rotation": [0, 4, 0], "translation": r2([0.6, 2.0 - gy * s1 * 0.5, -gz * s1 * 0.6 - (1.0 if self.rifle else 2.0)]), "scale": [s1] * 3}
        length = hi[2] - lo[2]
        height = hi[1] - lo[1]
        width = hi[0] - lo[0]
        g = min(15.0 / length, 15.0 / max(height, width * 0.4))
        cz, cy = (lo[2] + hi[2]) / 2 - 8, (lo[1] + hi[1]) / 2 - 8
        # GUI: seen from the side, muzzle to the left; centered in the slot.
        gui = {"rotation": [12, 90, 0], "translation": r2([cz * g, -cy * g, 0]), "scale": [r4(g)] * 3}
        f = min(14.0 / length, 0.6)
        return {
            "thirdperson_righthand": third,
            "thirdperson_lefthand": third,
            "firstperson_righthand": first,
            "firstperson_lefthand": first,
            "gui": gui,
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [r4(f * 0.7)] * 3},
            "fixed": {"rotation": [0, 90, 0], "translation": r2([cz * f, -cy * f, 0]), "scale": [r4(f)] * 3},
            "on_shelf": {"rotation": [0, 90, 0], "translation": r2([cz * f, -cy * f, 0]), "scale": [r4(f)] * 3},
            "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [0.5, 0.5, 0.5]},
        }


def combine(rotation, tilt):
    """Puts a turn around the part's own axis and a tilt of the whole part into one euler rotation.
    Only used with a tilt around x (grips and stocks): the result is euler x, then the own turn."""
    axis, degrees, origin = tilt
    if rotation is None:
        return {"origin": list(origin), "axis": axis, "angle": degrees}
    # Turn around z (own axis of a barrel-like part) inside a tilt around x: euler XYZ applies x
    # first in model space, which equals the tilt when the origins agree.
    own = rotation["angle"]
    return {"origin": list(origin), "x": degrees if axis == "x" else 0.0, "y": degrees if axis == "y" else 0.0,
            "z": own if rotation["axis"] == "z" else 0.0}


def pack(rects):
    """Shelf packing into the smallest square power-of-two texture."""
    order = sorted(range(len(rects)), key=lambda i: -rects[i][1])
    for size in (64, 128, 256, 512, 1024):
        x = y = shelf = 0
        places = [None] * len(rects)
        ok = True
        for i in order:
            w, h = rects[i]
            if x + w > size:
                x, y, shelf = 0, y + shelf, 0
            if y + h > size or w > size:
                ok = False
                break
            places[i] = (x, y)
            x += w
            shelf = max(shelf, h)
        if ok:
            return size, places
    raise ValueError("texture too large")


def r4(v):
    return round(float(v), 4)


def r2(values):
    return [round(float(v), 2) + 0.0 for v in values]


# ------------------------------------------------------------------------------------------------
# The blasters. Barrel axis at x = 8, y = 10 unless noted; z grows from the muzzle to the back.

def dl44():
    """DL-44 heavy blaster pistol: a long round barrel with a slotted flash hider and cooling rings,
    a long scope over the boxy receiver, a box magazine in front of the trigger, a ribbed grip that
    leans back, and a gas cartridge with a red light on the left."""
    b = Blaster("dl44", 4401, rifle=False)
    # Flash hider with slots, a steel muzzle ring and the dark bore.
    b.cyl("z", (8, 10), -1.0, 3.0, 1.55, "gunmetal", "slots", sides=12)
    b.cyl("z", (8, 10), -1.6, -1.0, 1.75, "steel", sides=12)
    b.cyl("z", (8, 10), -1.8, -1.6, 0.8, "bore")
    # Barrel and its cooling rings.
    b.cyl("z", (8, 10), 3.0, 10.0, 1.0, "gunmetal", sides=12)
    for z in (3.6, 5.0, 6.4):
        b.ring(z, 0.6, 1.45, "dark_steel", sides=12)
    # Motivator tube under the barrel.
    b.cyl("z", (8, 8.25), 3.6, 10.4, 0.5, "steel")
    b.boxc((8, 8.7, 4.2), (0.6, 0.9, 0.6), "steel")
    # Receiver.
    b.box((6.7, 8.2, 9.6), (9.3, 11.6, 17.4), "gunmetal", "panel")
    b.box((6.5, 8.6, 10.0), (6.7, 11.0, 16.6), "dark_steel")
    b.box((9.3, 8.6, 10.0), (9.5, 11.0, 16.6), "dark_steel")
    # Rear sight block and hammer.
    b.box((7.3, 11.6, 16.4), (8.7, 12.4, 17.6), "black")
    b.box((7.6, 10.8, 17.2), (8.4, 12.2, 18.4), "steel", rotation={"origin": [8, 11, 17.4], "axis": "x", "angle": -25})
    # Scope: tube, front bell with the lens, rear eyepiece, two rings on a rail.
    b.box((7.4, 11.6, 10.4), (8.6, 12.3, 16.0), "black")
    b.cyl("z", (8, 13.5), 9.4, 17.0, 0.95, "black", sides=12)
    b.cyl("z", (8, 13.5), 8.4, 9.4, 1.25, "black", sides=12)
    b.cyl("z", (8, 13.5), 8.25, 8.45, 1.0, "lens")
    b.cyl("z", (8, 13.5), 17.0, 17.9, 1.15, "rubber", sides=12)
    b.cyl("z", (8, 13.5), 17.9, 18.0, 0.8, "lens")
    for z in (10.8, 15.0):
        b.box((7.15, 12.3, z), (8.85, 14.6, z + 0.8), "steel")
    b.cyl("x", (13.5, 13.2), 8.9, 9.5, 0.45, "steel", "dial")
    # Box magazine in front of the trigger guard.
    b.box((7.0, 5.4, 10.4), (9.0, 8.2, 13.0), "gunmetal", "ribs")
    b.box((7.2, 5.0, 10.6), (8.8, 5.4, 12.8), "steel")
    # Trigger guard and trigger.
    b.box((7.65, 5.6, 13.0), (8.35, 6.1, 16.4), "gunmetal")
    b.box((7.65, 5.6, 16.0), (8.35, 8.3, 16.5), "gunmetal")
    b.box((7.8, 6.6, 14.3), (8.2, 8.3, 14.8), "steel", rotation={"origin": [8, 8.2, 14.5], "axis": "x", "angle": 15})
    # Grip, leaning back, with ribs and a steel cap.
    tilt = {"origin": [8, 8.2, 17.0], "axis": "x", "angle": -18}
    b.box((6.9, 1.2, 16.3), (9.1, 8.4, 19.9), "darkwood", "ribs", rotation=tilt, long_axis=1)
    b.box((6.8, 0.5, 16.2), (9.2, 1.2, 20.0), "steel", rotation=tilt)
    # Gas cartridge with a red light on the left side.
    b.cyl("z", (6.2, 10.2), 11.0, 15.6, 0.6, "copper", "knurl")
    b.box((5.9, 10.0, 15.6), (6.5, 10.4, 16.2), "steel")
    b.boxc((5.55, 10.2, 13.2), (0.2, 0.5, 0.5), "led_red")
    # Selector on the right.
    b.box((9.5, 10.2, 12.2), (9.9, 10.8, 13.6), "steel")
    b.muzzle = (8.0, 10.0, -1.8)
    b.grip = (8.0, 4.4, 18.6)
    return b


def e11():
    """E-11 blaster rifle: a black body tube full of vent holes, a ribbed muzzle with a T-fin, a
    power magazine sticking out to the left, a short scope with a dial, a ribbed pistol grip and
    the metal stock folded over the top."""
    b = Blaster("e11", 1101, rifle=True)
    # Body tube with vent holes.
    b.cyl("z", (8, 10), -2.0, 17.0, 1.9, "black", ("holes", 5), sides=12)
    # Muzzle: ribbed collar, front cap, a short emitter and the bore, T-shaped fin.
    b.cyl("z", (8, 10), -3.6, -2.0, 2.15, "matte", "ribs", sides=12)
    b.cyl("z", (8, 10), -5.2, -3.6, 1.0, "gunmetal", sides=12)
    b.cyl("z", (8, 10), -5.4, -5.2, 0.6, "bore")
    b.box((4.8, 9.5, -3.3), (11.2, 10.5, -2.5), "matte")
    b.box((7.5, 7.0, -3.3), (8.5, 10.0, -2.5), "matte")
    # Rear cap and the charging end.
    b.cyl("z", (8, 10), 17.0, 18.6, 2.1, "gunmetal", sides=12)
    b.cyl("z", (8, 10), 18.6, 19.2, 1.2, "dark_steel")
    # Power magazine to the left, with a steel end and a ribbed grip area.
    b.box((1.2, 9.0, 7.6), (6.1, 11.0, 10.2), "black", "ribs")
    b.box((0.6, 8.8, 7.4), (1.2, 11.2, 10.4), "steel")
    b.box((6.0, 8.6, 7.2), (6.4, 11.4, 10.6), "gunmetal")
    # Ejection port and a counter on the right.
    b.box((9.8, 9.4, 9.0), (10.2, 10.8, 12.0), "dark_steel")
    b.box((9.6, 11.0, 13.2), (10.3, 12.0, 15.6), "gunmetal")
    b.boxc((10.32, 11.5, 14.4), (0.1, 0.5, 1.4), "led_red")
    # Scope on two mounts, front lens, rear eyepiece and a dial.
    b.box((7.3, 11.8, 5.4), (8.7, 12.6, 6.4), "matte")
    b.box((7.3, 11.8, 11.4), (8.7, 12.6, 12.4), "matte")
    b.cyl("z", (8, 13.6), 4.0, 13.4, 1.0, "black", sides=12)
    b.cyl("z", (8, 13.6), 3.8, 4.0, 0.85, "lens_red")
    b.cyl("z", (8, 13.6), 13.4, 14.4, 1.1, "rubber", sides=12)
    b.cyl("x", (13.6, 8.4), 8.9, 9.6, 0.55, "gunmetal", "dial")
    # Pistol grip with ribs, trigger guard and trigger.
    tilt = {"origin": [8, 8.0, 13.4], "axis": "x", "angle": -14}
    b.box((6.9, 2.4, 12.8), (9.1, 8.2, 15.8), "rubber", "ribs", rotation=tilt, long_axis=1)
    b.box((6.8, 1.8, 12.7), (9.2, 2.4, 15.9), "gunmetal", rotation=tilt)
    b.box((7.65, 6.2, 9.6), (8.35, 6.7, 12.9), "gunmetal")
    b.box((7.65, 6.2, 9.6), (8.35, 8.2, 10.1), "gunmetal")
    b.box((7.8, 6.9, 11.2), (8.2, 8.2, 11.7), "steel", rotation={"origin": [8, 8.1, 11.4], "axis": "x", "angle": 15})
    # Folded stock: two struts from the hinge at the back over the top, and the butt plate in front.
    b.box((5.4, 12.2, 3.0), (5.9, 12.7, 19.4), "gunmetal")
    b.box((10.1, 12.2, 3.0), (10.6, 12.7, 19.4), "gunmetal")
    b.box((5.2, 11.6, 2.4), (10.8, 13.2, 3.2), "dark_steel")
    b.box((5.2, 11.2, 18.8), (10.8, 12.2, 19.8), "gunmetal")
    b.muzzle = (8.0, 10.0, -5.4)
    b.grip = (8.0, 5.0, 14.6)
    return b


def dc15a():
    """DC-15A blaster rifle of the clone troopers: a long barrel with a slotted muzzle brake, an
    angular shroud with cooling fins, a long boxy scope, a power pack under the receiver, a blue
    power light and a fixed skeleton stock."""
    b = Blaster("dc15a", 1501, rifle=True)
    # Muzzle brake and barrel.
    b.box((6.6, 8.7, -12.0), (9.4, 11.3, -9.0), "black", "slots")
    b.cyl("z", (8, 10), -12.2, -12.0, 0.7, "bore")
    b.cyl("z", (8, 10), -9.0, 2.0, 1.05, "gunmetal", "ribs", sides=12)
    b.box((7.7, 11.1, -10.6), (8.3, 12.2, -9.8), "black")
    # Angular shroud with fins on top.
    b.box((6.2, 8.2, 1.0), (9.8, 11.6, 11.0), "gunmetal", "panel")
    for z in (2.0, 3.6, 5.2, 6.8):
        b.box((6.6, 11.6, z), (9.4, 12.4, z + 0.8), "dark_steel")
    b.box((6.0, 9.2, 1.0), (6.2, 10.8, 11.0), ("dark_steel"), ("stripe", "white"))
    b.box((9.8, 9.2, 1.0), (10.0, 10.8, 11.0), ("dark_steel"), ("stripe", "white"))
    # Fore grip under the shroud.
    b.box((7.0, 5.6, 4.0), (9.0, 8.2, 8.6), "matte", "ribs", long_axis=1)
    # Receiver.
    b.box((6.0, 7.4, 11.0), (10.0, 12.0, 21.0), "gunmetal", "panel")
    b.box((10.0, 9.2, 13.0), (10.15, 10.0, 19.0), "led_blue")
    b.box((5.8, 9.6, 14.0), (6.0, 11.2, 16.6), "steel")
    # Scope: a long box with lenses at both ends, on a rail.
    b.box((7.4, 12.0, 9.0), (8.6, 12.6, 19.0), "black")
    b.box((7.0, 12.6, 8.0), (9.0, 14.6, 18.0), "black", "panel")
    b.box((7.2, 12.8, 7.8), (8.8, 14.4, 8.0), "lens")
    b.box((7.2, 12.8, 18.0), (8.8, 14.4, 18.8), "rubber")
    # Power pack, leaning forward.
    b.box((6.6, 3.0, 12.4), (9.4, 7.4, 14.8), "matte", "ribs", rotation={"origin": [8, 7.4, 13.6], "axis": "x", "angle": 12}, long_axis=1)
    # Trigger guard, trigger and grip.
    b.box((7.65, 6.4, 15.2), (8.35, 6.9, 18.0), "gunmetal")
    b.box((7.65, 6.4, 15.2), (8.35, 7.6, 15.7), "gunmetal")
    b.box((7.8, 7.1, 16.2), (8.2, 7.6, 16.7), "steel")
    tilt = {"origin": [8, 7.4, 18.6], "axis": "x", "angle": -14}
    b.box((6.9, 1.8, 17.8), (9.1, 7.4, 20.6), "rubber", "ribs", rotation=tilt, long_axis=1)
    # Skeleton stock: upper bar, lower bar and the butt plate.
    b.box((6.8, 9.6, 21.0), (9.2, 11.4, 29.4), "gunmetal")
    b.box((6.8, 5.6, 21.0), (9.2, 7.0, 29.4), "gunmetal")
    b.box((6.8, 7.0, 21.0), (9.2, 7.6, 22.0), "gunmetal")
    b.box((6.6, 5.0, 29.4), (9.4, 11.8, 30.8), "rubber")
    b.box((6.9, 7.0, 21.0), (9.1, 9.6, 22.4), "dark_steel")
    b.muzzle = (8.0, 10.0, -12.2)
    b.grip = (8.0, 4.4, 19.4)
    return b


def se14c():
    """SE-14C blaster pistol: slim and black, a long slide with vent slots and a steel top rail, a
    knurled selector ring around the barrel, a steep grip with a power cell at the bottom."""
    b = Blaster("se14c", 1401, rifle=False)
    b.cyl("z", (8, 10.4), -1.6, 6.0, 0.85, "black", sides=12)
    b.cyl("z", (8, 10.4), -2.0, -1.6, 1.05, "steel", sides=12)
    b.cyl("z", (8, 10.4), -2.15, -2.0, 0.55, "bore")
    b.cyl("z", (8, 10.4), 4.4, 5.6, 1.3, "steel", "knurl", sides=12)
    # Slide and rail.
    b.box((6.9, 9.4, 1.0), (9.1, 12.0, 16.0), "black", "slots")
    b.box((7.45, 12.0, 1.4), (8.55, 12.7, 15.4), "chrome")
    b.box((7.6, 12.7, 1.6), (8.4, 13.3, 2.4), "black")
    b.box((7.2, 12.7, 14.2), (8.8, 13.4, 15.2), "black")
    # Frame under the slide.
    b.box((7.05, 8.0, 5.4), (8.95, 9.4, 16.2), "gunmetal", "panel")
    b.box((8.95, 8.6, 9.0), (9.15, 9.2, 12.0), "steel")
    b.boxc((8.0, 12.25, 15.8), (0.8, 0.5, 0.25), "led_red")
    # Trigger guard, trigger.
    b.box((7.65, 6.0, 9.6), (8.35, 6.5, 13.4), "gunmetal")
    b.box((7.65, 6.0, 9.6), (8.35, 8.0, 10.1), "gunmetal")
    b.box((7.8, 6.6, 11.4), (8.2, 8.0, 11.9), "steel", rotation={"origin": [8, 7.9, 11.6], "axis": "x", "angle": 15})
    # Steep grip with the power cell.
    tilt = {"origin": [8, 8.0, 14.4], "axis": "x", "angle": -22}
    b.box((6.95, 1.4, 12.9), (9.05, 8.2, 16.1), "rubber", "ribs", rotation=tilt, long_axis=1)
    b.box((7.2, 0.4, 13.2), (8.8, 1.4, 15.8), "steel", rotation=tilt)
    b.box((7.5, 0.0, 13.6), (8.5, 0.4, 15.4), "led_blue", rotation=tilt)
    b.muzzle = (8.0, 10.4, -2.15)
    b.grip = (8.0, 4.6, 15.4)
    return b


def bowcaster():
    """Bowcaster of the Wookiees: a heavy stock of wood with brass bands, a steel barrel with a
    brass muzzle, the bow arms across the front with a glowing polarizing orb at each end, cables
    to the body, a scope, a quarrel magazine on the left and a leather grip."""
    b = Blaster("bowcaster", 9901, rifle=True)
    # Barrel and muzzle.
    b.cyl("z", (8, 10), -7.0, 6.0, 1.15, "steel", sides=12)
    b.cyl("z", (8, 10), -8.0, -7.0, 1.45, "brass", "ribs", sides=12)
    b.cyl("z", (8, 10), -8.15, -8.0, 0.7, "bore")
    for z in (-4.0, 0.0, 3.4):
        b.ring(z, 0.6, 1.5, "brass", sides=12)
    # Bow arms, swept back, with the orbs.
    b.box((8.0, 9.4, 2.6), (22.0, 10.6, 4.0), "gunmetal", rotation={"origin": [8, 10, 3.3], "axis": "y", "angle": -14})
    b.box((-6.0, 9.4, 2.6), (8.0, 10.6, 4.0), "gunmetal", rotation={"origin": [8, 10, 3.3], "axis": "y", "angle": 14})
    b.box((8.0, 9.7, 2.3), (21.0, 10.3, 2.6), "brass", rotation={"origin": [8, 10, 3.3], "axis": "y", "angle": -14})
    b.box((-5.0, 9.7, 2.3), (8.0, 10.3, 2.6), "brass", rotation={"origin": [8, 10, 3.3], "axis": "y", "angle": 14})
    # Arm ends after the sweep: 14 units out at 14 degrees.
    ex = 14.0 * math.cos(math.radians(14))
    ez = 14.0 * math.sin(math.radians(14))
    b.sphere((8 + ex, 10.0, 3.3 + ez), 1.5, "orb")
    b.sphere((8 - ex, 10.0, 3.3 + ez), 1.5, "orb")
    # Front block that carries the arms.
    b.box((6.4, 8.2, 1.6), (9.6, 11.8, 5.2), "dark_steel", "panel")
    # Stock body of wood with brass bands.
    b.box((6.3, 7.4, 5.2), (9.7, 11.6, 22.0), "wood", ("grain_bands", 12))
    b.box((6.5, 11.6, 9.0), (9.5, 12.2, 19.0), "brass")
    # Scope.
    b.cyl("z", (8, 13.4), 10.0, 18.0, 1.0, "black", sides=12)
    b.cyl("z", (8, 13.4), 9.8, 10.0, 0.85, "lens")
    b.cyl("z", (8, 13.4), 18.0, 18.8, 1.1, "leather", sides=12)
    b.box((7.4, 12.2, 11.0), (8.6, 12.8, 11.8), "brass")
    b.box((7.4, 12.2, 16.2), (8.6, 12.8, 17.0), "brass")
    # Quarrel magazine on the left with a green light.
    b.box((3.6, 8.8, 13.0), (6.3, 11.2, 17.6), "brass", "ribs")
    b.boxc((3.5, 10.0, 15.3), (0.2, 0.7, 1.4), "led_green")
    # Trigger guard, trigger, grip.
    b.box((7.65, 5.4, 17.6), (8.35, 5.9, 21.2), "brass")
    b.box((7.65, 5.4, 17.6), (8.35, 7.6, 18.1), "brass")
    b.box((7.8, 5.9, 19.0), (8.2, 7.4, 19.5), "steel")
    tilt = {"origin": [8, 7.4, 21.6], "axis": "x", "angle": -12}
    b.box((6.9, 1.2, 20.6), (9.1, 7.4, 23.6), "leather", rotation=tilt, long_axis=1)
    # Shoulder stock.
    b.box((6.5, 5.0, 22.0), (9.5, 11.4, 29.0), "wood", ("grain_bands", 9))
    b.box((6.3, 4.6, 29.0), (9.7, 11.8, 30.2), "leather")
    b.muzzle = (8.0, 10.0, -8.15)
    b.grip = (8.0, 4.2, 22.2)
    return b


def a280():
    """A280 rifle of the rebels on Hoth: a slim finned barrel, a grey receiver, an offset scope,
    a straight magazine and a brown stock."""
    b = Blaster("a280", 2801, rifle=True)
    b.cyl("z", (8, 10), -10.0, 2.0, 0.9, "gunmetal", sides=12)
    b.cyl("z", (8, 10), -11.0, -10.0, 1.2, "matte", "slots", sides=12)
    b.cyl("z", (8, 10), -11.15, -11.0, 0.55, "bore")
    for z in (-7.0, -5.6, -4.2, -2.8):
        b.ring(z, 0.5, 1.4, "dark_steel", sides=12)
    b.box((6.6, 8.0, 2.0), (9.4, 11.8, 14.0), "grey", "panel")
    b.box((6.4, 9.0, 3.0), (6.6, 10.6, 9.0), "olive")
    b.box((7.2, 11.8, 4.0), (8.8, 12.5, 12.0), "matte")
    b.cyl("z", (8.6, 13.4), 3.6, 11.6, 0.9, "black", sides=12)
    b.cyl("z", (8.6, 13.4), 3.4, 3.6, 0.75, "lens")
    b.cyl("z", (8.6, 13.4), 11.6, 12.4, 1.0, "rubber", sides=12)
    b.box((7.0, 3.6, 6.0), (9.0, 8.0, 8.0), "matte", "ribs", long_axis=1)
    b.box((7.65, 6.4, 9.2), (8.35, 6.9, 12.4), "gunmetal")
    b.box((7.65, 6.4, 9.2), (8.35, 8.2, 9.7), "gunmetal")
    b.box((7.8, 6.9, 10.4), (8.2, 8.0, 10.9), "steel")
    tilt = {"origin": [8, 8.0, 12.6], "axis": "x", "angle": -14}
    b.box((6.9, 2.4, 11.8), (9.1, 8.0, 14.6), "rubber", "ribs", rotation=tilt, long_axis=1)
    b.box((6.8, 6.4, 14.0), (9.2, 11.4, 24.0), "wood")
    b.box((6.6, 5.8, 24.0), (9.4, 11.8, 25.2), "rubber")
    b.box((9.4, 9.6, 3.0), (9.55, 10.2, 5.0), "led_amber")
    b.muzzle = (8.0, 10.0, -11.15)
    b.grip = (8.0, 5.2, 13.4)
    return b


def repeater():
    """A heavy repeating blaster: a thick vented shroud around two barrels, a drum magazine, a
    carry handle, a red power light and a short stock."""
    b = Blaster("repeater", 3101, rifle=True)
    b.cyl("z", (8, 10), -8.0, 8.0, 2.4, "black", ("holes", 4), sides=12)
    b.cyl("z", (7.0, 10), -10.0, -8.0, 0.75, "gunmetal")
    b.cyl("z", (9.0, 10), -10.0, -8.0, 0.75, "gunmetal")
    b.cyl("z", (7.0, 10), -10.15, -10.0, 0.45, "bore")
    b.cyl("z", (9.0, 10), -10.15, -10.0, 0.45, "bore")
    b.cyl("z", (8, 10), -8.6, -8.0, 2.6, "dark_steel", sides=12)
    b.box((5.4, 7.2, 8.0), (10.6, 12.8, 20.0), "gunmetal", "panel")
    b.cyl("x", (9.0, 13.0), 2.0, 5.4, 2.6, "matte", "ribs", sides=12)
    b.box((7.4, 12.8, 9.0), (8.6, 15.0, 9.8), "dark_steel")
    b.box((7.4, 12.8, 17.0), (8.6, 15.0, 17.8), "dark_steel")
    b.box((7.4, 14.4, 9.0), (8.6, 15.4, 17.8), "rubber")
    b.box((10.6, 10.0, 11.0), (10.8, 11.0, 14.0), "led_red")
    b.box((7.65, 5.8, 15.0), (8.35, 6.3, 18.4), "gunmetal")
    b.box((7.65, 5.8, 15.0), (8.35, 7.4, 15.5), "gunmetal")
    b.box((7.8, 6.3, 16.2), (8.2, 7.2, 16.7), "steel")
    tilt = {"origin": [8, 7.2, 18.8], "axis": "x", "angle": -12}
    b.box((6.9, 1.6, 18.0), (9.1, 7.2, 21.0), "rubber", "ribs", rotation=tilt, long_axis=1)
    b.box((6.8, 6.6, 20.0), (9.2, 11.6, 26.0), "gunmetal")
    b.box((6.6, 6.0, 26.0), (9.4, 12.0, 27.2), "rubber")
    b.muzzle = (8.0, 10.0, -10.15)
    b.grip = (8.0, 4.4, 19.6)
    return b


def rebel():
    """A DH-17 style rebel pistol: a short tube full of vent holes, a horizontal magazine on the
    left, a steel muzzle, blue markings and a ribbed grip."""
    b = Blaster("rebel", 1701, rifle=False)
    b.cyl("z", (8, 10), 0.0, 14.0, 1.5, "black", ("holes", 4), sides=12)
    b.cyl("z", (8, 10), -1.6, 0.0, 1.0, "steel", "ribs", sides=12)
    b.cyl("z", (8, 10), -1.75, -1.6, 0.55, "bore")
    b.cyl("z", (8, 10), 14.0, 15.2, 1.65, "gunmetal", sides=12)
    b.box((2.6, 9.1, 4.4), (6.6, 10.9, 6.6), "black", "ribs")
    b.box((2.2, 8.9, 4.2), (2.6, 11.1, 6.8), "steel")
    b.box((7.3, 11.4, 5.0), (8.7, 12.2, 11.0), "matte")
    b.box((9.4, 9.4, 6.0), (9.6, 10.6, 12.0), "blue_paint")
    b.boxc((9.62, 10.0, 12.8), (0.1, 0.5, 0.6), "led_blue")
    b.box((7.65, 6.4, 8.0), (8.35, 6.9, 11.6), "gunmetal")
    b.box((7.65, 6.4, 8.0), (8.35, 8.6, 8.5), "gunmetal")
    b.box((7.8, 7.0, 9.6), (8.2, 8.6, 10.1), "steel")
    tilt = {"origin": [8, 8.4, 12.0], "axis": "x", "angle": -16}
    b.box((6.95, 2.0, 11.0), (9.05, 8.6, 14.0), "rubber", "ribs", rotation=tilt, long_axis=1)
    b.box((6.85, 1.4, 10.9), (9.15, 2.0, 14.1), "steel", rotation=tilt)
    b.muzzle = (8.0, 10.0, -1.75)
    b.grip = (8.0, 5.0, 13.2)
    return b


def ezra():
    """Ezra Bridger's first lightsaber in blaster mode: a grey body with orange bands and a brown
    folding grip pointing down, the blaster emitter at the front and a red activator."""
    b = Blaster("ezra", 2101, rifle=False)
    b.cyl("z", (8, 10), 2.0, 18.0, 1.6, "grey", sides=12)
    for z in (6.0, 11.6):
        b.ring(z, 1.2, 1.75, "orange", sides=12)
    b.cyl("z", (8, 10), 0.0, 2.0, 1.85, "dark_steel", "slots", sides=12)
    b.cyl("z", (8, 10), -1.4, 0.0, 0.9, "steel", sides=12)
    b.cyl("z", (8, 10), -1.55, -1.4, 0.55, "bore")
    b.cyl("z", (8, 10), 18.0, 19.4, 1.8, "dark_steel", sides=12)
    b.box((7.2, 11.6, 7.6), (8.8, 12.4, 9.6), "red_paint")
    b.box((7.0, 11.4, 13.4), (9.0, 12.0, 16.4), "chrome", "panel")
    tilt = {"origin": [8, 8.6, 14.6], "axis": "x", "angle": -10}
    b.box((7.0, 2.4, 13.4), (9.0, 8.6, 15.8), "rust", "ribs", rotation=tilt, long_axis=1)
    b.box((7.3, 6.6, 15.8), (8.7, 8.6, 16.6), "darkwood", rotation=tilt)
    b.box((7.65, 6.4, 10.0), (8.35, 6.9, 13.4), "dark_steel")
    b.box((7.65, 6.4, 10.0), (8.35, 8.6, 10.5), "dark_steel")
    b.box((7.8, 6.9, 11.0), (8.2, 8.4, 11.5), "steel")
    b.boxc((9.62, 10.0, 4.0), (0.12, 0.5, 0.8), "led_amber")
    b.muzzle = (8.0, 10.0, -1.55)
    b.grip = (8.0, 5.4, 14.6)
    return b


BLASTERS = [dl44, e11, dc15a, se14c, bowcaster, a280, repeater, rebel, ezra]
# Bolt color of each type (BlasterType.java has the same).
BOLT = {"bowcaster": "sabercraft:green", "rebel": "sabercraft:blue", "ezra": "sabercraft:yellow", "dc15a": "sabercraft:blue"}


def main():
    os.makedirs(MODELS, exist_ok=True)
    os.makedirs(TEXTURES, exist_ok=True)
    os.makedirs(ITEMS, exist_ok=True)
    old = os.path.join(TEXTURES, "palette.png")
    if os.path.exists(old):
        os.remove(old)

    cases = []
    for make in BLASTERS:
        b = make()
        elements, img = b.build()
        Image.fromarray(img.astype(np.uint8), "RGBA").save(os.path.join(TEXTURES, b.name + ".png"))
        model = {
            "gui_light": "side",
            "textures": {"blaster": "sabercraft:item/blaster/" + b.name, "particle": "sabercraft:item/blaster/" + b.name},
            "elements": elements,
            "display": b.display(),
        }
        write_json(os.path.join(MODELS, b.name + ".json"), model)
        lo, hi = b.bounds()
        print(f"{b.name}: {len(elements)} elements, texture {img.shape[0]} px, size {hi - lo}")

        plain = {"type": "minecraft:model", "model": "sabercraft:item/blaster/" + b.name}
        held = {
            "type": "minecraft:composite",
            "models": [
                plain,
                {
                    "type": "sabercraft:blaster_flash",
                    "base": "sabercraft:item/blaster/" + b.name,
                    "color": BOLT.get(b.name, "sabercraft:red"),
                    "muzzle": [r4(b.muzzle[0] / 16), r4(b.muzzle[1] / 16), r4(b.muzzle[2] / 16)],
                },
            ],
        }
        cases.append({
            "when": "sabercraft:" + b.name,
            "model": {
                "type": "minecraft:select",
                "property": "minecraft:display_context",
                "cases": [{"when": ["gui", "ground", "fixed", "on_shelf", "head"], "model": plain}],
                "fallback": held,
            },
        })

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
        json.dump(data, f, indent=1)
        f.write("\n")


if __name__ == "__main__":
    main()
