#!/usr/bin/env python3
"""
Paints the SaberCraft outfits (Phase 7): original robe and armor designs as 64x64 textures in the
Minecraft skin layout. They are drawn as an extra layer over the player's own skin, so the face
and anything the outfit leaves free stay the player's. No film costumes are copied: these are
generic robes and armor in the mod's own colors. Run from the repository root:

    python3 tools/paint_outfits.py

Needs Pillow and numpy. Writes src/client/resources/assets/sabercraft/textures/outfit/<name>.png.
"""
import os

import numpy as np
from PIL import Image

OUT = os.path.join("src", "client", "resources", "assets", "sabercraft", "textures", "outfit")

# (u, v, width, height, depth) of each box in the skin layout, base layer and outer layer.
PARTS = {
    "head": ((0, 0, 8, 8, 8), (32, 0, 8, 8, 8)),
    "body": ((16, 16, 8, 12, 4), (16, 32, 8, 12, 4)),
    "right_arm": ((40, 16, 4, 12, 4), (40, 32, 4, 12, 4)),
    "left_arm": ((32, 48, 4, 12, 4), (48, 48, 4, 12, 4)),
    "right_leg": ((0, 16, 4, 12, 4), (0, 32, 4, 12, 4)),
    "left_leg": ((16, 48, 4, 12, 4), (0, 48, 4, 12, 4)),
}
ARMS = ("right_arm", "left_arm")
LEGS = ("right_leg", "left_leg")


def rgb(hex_color):
    return tuple(int(hex_color[i:i + 2], 16) for i in (1, 3, 5))


class Outfit:
    def __init__(self, seed):
        self.img = np.zeros((64, 64, 4), dtype=np.float32)
        self.rng = np.random.default_rng(seed)

    def faces(self, part, outer):
        u, v, w, h, d = PARTS[part][1 if outer else 0]
        # name, x0, y0, width, height
        return {
            "top": (u + d, v, w, d),
            "bottom": (u + d + w, v, w, d),
            "right": (u, v + d, d, h),
            "front": (u + d, v + d, w, h),
            "left": (u + d + w, v + d, d, h),
            "back": (u + 2 * d + w, v + d, w, h),
        }

    def paint(self, parts, outer, fn, faces=None):
        """fn(face, x, y, w, h) -> '#rrggbb', (r, g, b) or None for each pixel of the faces."""
        for part in parts if isinstance(parts, (list, tuple)) else [parts]:
            for face, (x0, y0, w, h) in self.faces(part, outer).items():
                if faces and face not in faces:
                    continue
                for y in range(h):
                    for x in range(w):
                        color = fn(face, x, y, w, h)
                        if color is None:
                            continue
                        if isinstance(color, str):
                            color = rgb(color)
                        # A little grain, so large areas look like cloth or metal.
                        grain = 1.0 + self.rng.normal(0, 0.045)
                        self.img[y0 + y, x0 + x] = [min(255, c * grain) for c in color] + [255]

    def fill(self, parts, outer, color, rows=None, faces=None):
        """Fills faces; with rows only that band of the side faces (and the bottom if it reaches it)."""
        def fn(face, x, y, w, h):
            if rows is None:
                return color
            if face == "top":
                return color if rows[0] == 0 else None
            if face == "bottom":
                return color if rows[1] >= 12 else None
            return color if rows[0] <= y < rows[1] else None
        self.paint(parts, outer, fn, faces)

    def save(self, name):
        os.makedirs(OUT, exist_ok=True)
        path = os.path.join(OUT, name + ".png")
        Image.fromarray(self.img.astype(np.uint8), "RGBA").save(path)
        print(path)


def shade(color, factor):
    r, g, b = rgb(color) if isinstance(color, str) else color
    return (r * factor, g * factor, b * factor)


def wanderer():
    """Keeper's robe: a cream tunic, a brown open robe, a wide belt and the hood down."""
    o = Outfit(11)
    tunic, robe, belt, trousers, boots = "#d6c6a2", "#76502f", "#4e331b", "#55402c", "#33241a"
    o.fill("body", False, tunic)
    o.fill(ARMS, False, tunic)
    o.fill(LEGS, False, trousers)
    o.fill(LEGS, False, boots, rows=(8, 12))
    # Belt with a buckle.
    o.paint("body", False, lambda f, x, y, w, h: ("#b08a40" if f == "front" and x in (3, 4) else belt) if y in (7, 8) and f not in ("top", "bottom") else None)
    # Crossed tabards over the tunic.
    o.paint("body", False, lambda f, x, y, w, h: "#c2ae86" if f in ("front", "back") and x in (2, 3, 4, 5) and y < 7 else None)
    # The open robe: sides and back, the front only at the edges.
    o.paint("body", True, lambda f, x, y, w, h: None if f == "front" and 1 < x < w - 2 or f == "bottom" else shade(robe, 0.9 if f == "back" else 1.0))
    o.paint(ARMS, True, lambda f, x, y, w, h: None if f in ("top",) or y > 9 else shade(robe, 0.85 if y > 7 else 1.0))
    o.paint(ARMS, True, lambda f, x, y, w, h: "#5e3e22" if f == "top" else None)
    # The robe's hem over the legs, open at the front.
    o.paint(LEGS, True, lambda f, x, y, w, h: None if f in ("top", "bottom", "front") or y > 6 else shade(robe, 0.9))
    # The hood lies down on the back of the neck.
    o.paint("head", True, lambda f, x, y, w, h: shade(robe, 0.8) if (f == "back" and y >= 5) or (f in ("left", "right") and y >= 6 and x <= 2 if f == "right" else f == "left" and y >= 6 and x >= 5) else None)
    o.save("wanderer")


def shadow():
    """Shadow robe: black layered robe with a crimson sash and trim, the hood up."""
    o = Outfit(22)
    base, robe, trim, sash, metal = "#141114", "#0b090b", "#6e0f18", "#8a141e", "#5a5a62"
    o.fill("body", False, "#1d1a1e")
    o.fill(ARMS, False, base)
    o.fill(LEGS, False, base)
    o.paint(LEGS, False, lambda f, x, y, w, h: metal if y == 9 and f == "front" and x in (1, 2) else None)
    o.paint("body", False, lambda f, x, y, w, h: (sash if y in (6, 7, 8) else None) if f not in ("top", "bottom") else None)
    o.paint("body", False, lambda f, x, y, w, h: metal if f == "front" and y == 7 and x in (3, 4) else None)
    # Layered over-robe with a crimson edge down the front.
    o.paint("body", True, lambda f, x, y, w, h: None if f == "bottom" or f == "front" and 2 < x < w - 3 and y < 6 else (trim if f == "front" and x in (2, w - 3) else robe))
    o.paint("body", True, lambda f, x, y, w, h: None if f == "front" and 2 < x < w - 3 and y >= 6 else None)
    # Wide sleeves with a red cuff.
    o.paint(ARMS, True, lambda f, x, y, w, h: None if f == "bottom" else (trim if y == 10 else robe if y < 11 else None))
    # Robe down to the boots.
    o.paint(LEGS, True, lambda f, x, y, w, h: None if f in ("top", "bottom") else (trim if y == 9 and f != "back" else robe if y < 10 else None))
    # The hood: everything but an opening for the face, which stays in shadow at the top.
    def hood(f, x, y, w, h):
        if f == "front":
            if 1 <= x <= 6 and y >= 3:
                return None
            return robe if y > 0 else trim
        if f == "bottom":
            return None
        return shade(robe, 1.1)
    o.paint("head", True, hood)
    o.save("shadow")


def sentinel():
    """Sentinel armor: silver plates over a dark green suit, green marks."""
    o = Outfit(33)
    suit, plate, edge, mark = "#26332a", "#9aa3a9", "#6c757b", "#3fa45c"
    o.fill(["body"], False, suit)
    o.fill(ARMS, False, suit)
    o.fill(LEGS, False, suit)
    # Chest plate with a center ridge and a green mark.
    def chest(f, x, y, w, h):
        if f in ("bottom",):
            return None
        if f in ("front", "back") and y < 7:
            if f == "front" and x in (3, 4) and y in (2, 3):
                return mark
            return edge if y == 6 or x in (0, w - 1) else plate
        if f in ("left", "right") and y < 6:
            return edge
        if y in (7, 8) and f != "top":
            return "#2e2a24"
        if f == "top":
            return plate
        return None
    o.paint("body", True, chest)
    # Shoulder pads and bracers.
    o.paint(ARMS, True, lambda f, x, y, w, h: None if f == "bottom" else (plate if y < 3 or f == "top" else edge if y == 3 else plate if 7 <= y <= 10 else None))
    o.paint(ARMS, True, lambda f, x, y, w, h: mark if f == "left" and y == 1 and x in (1, 2) else None)
    # Greaves and knee guards.
    o.paint(LEGS, True, lambda f, x, y, w, h: None if f in ("top", "bottom") else (plate if 6 <= y <= 11 and f != "back" else edge if y == 5 and f == "front" else None))
    o.fill(LEGS, False, "#202622", rows=(10, 12))
    o.save("sentinel")


def scout():
    """Scout gear: a light shirt, a leather vest, a yellow scarf, gloves and high boots."""
    o = Outfit(44)
    shirt, vest, scarf, trousers, boots, glove = "#ded5bc", "#a3743f", "#e0b02c", "#68683e", "#5b3b22", "#3e2a1a"
    o.fill("body", False, shirt)
    o.fill(ARMS, False, shirt)
    o.fill(ARMS, False, glove, rows=(9, 12))
    o.fill(LEGS, False, trousers)
    o.fill(LEGS, False, boots, rows=(6, 12))
    o.paint(LEGS, False, lambda f, x, y, w, h: "#8a6a3a" if y == 6 else None)
    # The vest, open at the front; the scarf around the neck and down the back.
    o.paint("body", True, lambda f, x, y, w, h: None if f == "bottom" or f == "front" and 2 < x < w - 3 or y > 8 else (scarf if y <= 1 or f == "back" and x in (5, 6) and y < 6 else vest))
    o.paint("body", True, lambda f, x, y, w, h: "#7c552c" if f != "top" and y == 8 and not (f == "front" and 2 < x < w - 3) else None)
    o.paint("head", True, lambda f, x, y, w, h: scarf if f != "top" and f != "bottom" and y == 7 else None)
    # Pouches on the belt line.
    o.paint(LEGS, True, lambda f, x, y, w, h: "#7c552c" if f in ("left", "right") and y in (0, 1, 2) and 1 <= x <= 2 else None)
    o.save("scout")


def twilight():
    """Twilight armor: dark gunmetal plates with violet light lines and a closed helmet."""
    o = Outfit(55)
    plate, dark, glow = "#2b2d35", "#17181d", "#9a5ce0"
    o.fill("body", False, dark)
    o.fill(ARMS, False, dark)
    o.fill(LEGS, False, dark)
    def armor(f, x, y, w, h):
        if f == "bottom":
            return None
        if f == "front" and (x in (3, 4) and 1 <= y <= 5):
            return glow if y % 2 else plate
        if y in (7, 8):
            return dark if f != "front" or x not in (3, 4) else glow
        return plate if y < 11 else None
    o.paint("body", True, armor)
    o.paint(ARMS, True, lambda f, x, y, w, h: None if f == "bottom" else (glow if y == 4 and f in ("left", "right", "front") and x in (1, 2) else plate if y < 4 or 7 <= y < 11 or f == "top" else None))
    o.paint(LEGS, True, lambda f, x, y, w, h: None if f in ("top", "bottom") else (glow if y == 7 and f == "front" and x in (1, 2) else plate if y >= 4 else None))
    # Closed helmet with a narrow glowing visor.
    def helmet(f, x, y, w, h):
        if f == "bottom":
            return None
        if f == "front" and y == 4 and 1 <= x <= 6:
            return glow
        if f == "front" and y == 6 and x in (3, 4):
            return dark
        return plate if f != "top" or (x + y) % 5 else shade(plate, 1.2)
    o.paint("head", True, helmet)
    o.paint("head", False, lambda f, x, y, w, h: dark if f != "bottom" else None)
    o.save("twilight")


# ---------------- Phase 8: film characters ----------------
# Own pixel art after the look of the characters, drawn from scratch in the 64 x 64 skin layout.
# Unlike the outfits above they paint the head as well (face, hair, helmet), so the character is
# recognisable; a player can still replace each of them with a skin file of their own in
# config/sabercraft/characters/<name>.png.

def head_from(o, outer, layout):
    """layout: dict face -> list of 8 strings of 8 chars, each char a key of the palette in layout['_']."""
    pal = layout["_"]
    def fn(f, x, y, w, h):
        rows = layout.get(f)
        if rows is None:
            return None
        c = rows[y][x]
        return None if c == "." else pal[c]
    o.paint("head", outer, fn)


def vader():
    """Dark lord in black armor: a domed helmet with a grilled mask, a chest panel, a cape."""
    o = Outfit(66)
    black, dark, gray, metal, lens = "#0c0c0f", "#1b1b20", "#4a4c52", "#8d9097", "#2a1e22"
    o.fill("body", False, dark)
    o.fill(ARMS, False, black)
    o.fill(LEGS, False, black)
    # Chest panel with coloured buttons, shoulder plates, belt with silver boxes.
    def chest(f, x, y, w, h):
        if f == "front" and 2 <= x <= 5 and 2 <= y <= 4:
            return {(2, 3): "#c02020", (3, 3): "#2f6fd0", (4, 3): "#30a040", (5, 3): "#c02020"}.get((x, y), gray)
        if f == "front" and y == 0 and x in (1, 2, 5, 6):
            return gray
        if f not in ("top", "bottom") and y in (7, 8):
            return metal if f == "front" and x in (2, 3, 4, 5) and y == 7 else black
        return None
    o.paint("body", False, chest)
    o.paint(ARMS, False, lambda f, x, y, w, h: gray if y < 2 and f != "bottom" else (dark if 8 <= y <= 10 and f != "bottom" else None))
    # The cape: the back and the sides of the body and the legs, open at the front.
    o.paint("body", True, lambda f, x, y, w, h: None if f in ("front", "bottom") or f in ("left", "right") and x == 0 and f == "right" else shade(black, 0.85 if f == "back" else 1.0))
    o.paint(LEGS, True, lambda f, x, y, w, h: None if f in ("front", "top", "bottom") else shade(black, 0.85))
    o.paint(ARMS, True, lambda f, x, y, w, h: shade(black, 1.0) if f in ("top",) or y < 3 and f != "bottom" else None)
    # Helmet: the base layer is the mask, the outer layer the dome and its flared rim.
    head_from(o, False, {
        "_": {"k": black, "d": dark, "g": gray, "m": metal, "l": lens},
        "front": ["kkkkkkkk", "kddddddk", "kddkkddk", "kllkkllk", "kdkggkdk", "kdgmmgdk", "kgmgmgmk", "kkgmmgkk"],
        "top": ["kkkkkkkk"] * 8, "bottom": ["kkkkkkkk"] * 8,
        "left": ["kkkkkkkk"] * 8, "right": ["kkkkkkkk"] * 8, "back": ["kkkkkkkk"] * 8,
    })
    head_from(o, True, {
        "_": {"k": black, "d": dark},
        "front": ["kkkkkkkk", "kdddddk.".replace(".", "k"), "........", "........", "........", "........", "........", "........"],
        "top": ["kdkkkkdk", "kkkkkkkk", "kkkdkkkk", "kkkkkkkk", "kkkkkkkk", "kkkkdkkk", "kkkkkkkk", "kdkkkkdk"],
        "left": ["kkkkkkkk"] * 7 + ["kkkkkk.."], "right": ["kkkkkkkk"] * 7 + ["..kkkkkk"],
        "back": ["kkkkkkkk"] * 8,
    })
    o.save("vader")


def obi_wan():
    """Calm master: auburn hair and beard, cream tunic and tabards, a brown belt and robe."""
    o = Outfit(77)
    skin, hair, beard, eye = "#d8a67e", "#8b5a30", "#7a4d28", "#5b7896"
    tunic, tabard, belt, robe, trousers, boots = "#d9c9a6", "#c7b38c", "#5a3a1e", "#6b4628", "#c8b690", "#3a2a1c"
    o.fill("body", False, tunic)
    o.fill(ARMS, False, tunic)
    o.fill(LEGS, False, trousers)
    o.fill(LEGS, False, boots, rows=(7, 12))
    o.paint("body", False, lambda f, x, y, w, h: tabard if f in ("front", "back") and x in (1, 2, 5, 6) and y < 7 else None)
    o.paint("body", False, lambda f, x, y, w, h: ("#9a7a44" if f == "front" and x in (3, 4) else belt) if y in (7, 8) and f not in ("top", "bottom") else None)
    # Open brown robe over the shoulders and down the back.
    o.paint("body", True, lambda f, x, y, w, h: None if f == "bottom" or f == "front" and 1 < x < w - 2 else shade(robe, 0.9 if f == "back" else 1.0))
    o.paint(ARMS, True, lambda f, x, y, w, h: None if f == "top" or y > 10 else shade(robe, 0.85 if y > 8 else 1.0))
    o.paint(ARMS, True, lambda f, x, y, w, h: shade(robe, 0.8) if f == "top" else None)
    o.paint(LEGS, True, lambda f, x, y, w, h: None if f in ("top", "bottom", "front") or y > 8 else shade(robe, 0.9))
    head_from(o, False, {
        "_": {"s": skin, "h": hair, "b": beard, "e": eye, "w": "#f0ece4", "m": "#a8644a"},
        "front": ["hhhhhhhh", "hhhhhhhs", "hsssssss", "ssssssss", "swessews".replace("e", "e"), "sssbbsss", "sbbmmbbs", "hbbbbbbh"],
        "top": ["hhhhhhhh"] * 8, "bottom": ["ssbbbbss", "sbbbbbbs", "bbbbbbbb", "bbbbbbbb", "ssssssss", "ssssssss", "ssssssss", "ssssssss"],
        "right": ["hhhhhhhh", "hhhhhhhh", "hhhhhhss", "hhhhhsss", "hhhhssss", "hhhssssb", "hhhhsbbb", "hhhhhbbb"],
        "left": ["hhhhhhhh", "hhhhhhhh", "sshhhhhh", "ssshhhhh", "sssshhhh", "bsssshhh", "bbbshhhh", "bbbhhhhh"],
        "back": ["hhhhhhhh"] * 8,
    })
    o.save("obi_wan")


def maul():
    """Sith warrior: a red face with black markings, yellow eyes, small horns, black robes."""
    o = Outfit(88)
    red, ink, eye, horn = "#a8201c", "#141012", "#e8c020", "#d8ccb0"
    black, dark, belt = "#121013", "#1e1a1e", "#2c2428"
    o.fill("body", False, dark)
    o.fill(ARMS, False, black)
    o.fill(LEGS, False, black)
    o.paint("body", False, lambda f, x, y, w, h: belt if y in (7, 8) and f not in ("top", "bottom") else None)
    o.paint("body", False, lambda f, x, y, w, h: black if f == "front" and (x == y // 2 + 1 or x == 6 - y // 2) and y < 7 else None)
    # A short open robe over the tunic and long skirts over the legs.
    o.paint("body", True, lambda f, x, y, w, h: None if f == "bottom" or f == "front" and 2 < x < w - 3 else shade(black, 0.9 if f == "back" else 1.0))
    o.paint(ARMS, True, lambda f, x, y, w, h: None if f == "bottom" or y > 9 else black)
    o.paint(LEGS, True, lambda f, x, y, w, h: None if f in ("top", "bottom") or y > 8 else shade(dark, 0.9))
    head_from(o, False, {
        "_": {"r": red, "k": ink, "e": eye},
        "front": ["kkrkkrkk", "rkkrrkkr", "kkrkkrkk", "rkkkkkkr", "reekkeer", "krkrrkrk", "kkrkkrkk", "rkkkkkkr"],
        "top": ["kkrkkrkk", "krkkkkrk", "rkkrrkkr", "kkrkkrkk", "kkrkkrkk", "rkkrrkkr", "krkkkkrk", "kkrkkrkk"],
        "bottom": ["kkkkkkkk"] * 8,
        "right": ["kkrkkkrk", "rkkkrkkk", "kkrkkrrk", "rkkrkkkr", "kkrkkrkk", "rkkkrkkr", "kkrkkkrk", "rkkkrkkk"],
        "left": ["krkkkrkk", "kkkrkkkr", "krrkkrkk", "rkkkrkkr", "kkrkkrkk", "rkkrkkkr", "krkkkrkk", "kkkrkkkr"],
        "back": ["kkrkkrkk", "rkkrrkkr", "kkrkkrkk", "rkkkkkkr", "kkrkkrkk", "rkkrrkkr", "kkrkkrkk", "rkkkkkkr"],
    })
    # Horns: a crown of small points on the outer layer of the head.
    head_from(o, True, {
        "_": {"h": horn},
        "top": ["........", ".h..h.h.", "...h....", ".h....h.", "....h...", ".h.h..h.", "........", "..h..h.."],
        "front": ["h..h.h.h", "........", "........", "........", "........", "........", "........", "........"],
    })
    o.save("maul")


def luke():
    """Young knight in black: sandy hair, a black tunic and trousers, one black glove."""
    o = Outfit(99)
    skin, hair, eye = "#e2b48e", "#c9a25e", "#4f7cae"
    black, dark, belt = "#141416", "#1f1f23", "#2a2a2e"
    o.fill("body", False, black)
    o.fill(ARMS, False, black)
    o.fill(LEGS, False, dark)
    o.fill(LEGS, False, black, rows=(7, 12))
    # The wrapped tunic: a lighter fold from the left shoulder down to the belt.
    o.paint("body", False, lambda f, x, y, w, h: dark if f == "front" and x == 5 - y // 2 and y < 7 else None)
    o.paint("body", False, lambda f, x, y, w, h: ("#6a6a72" if f == "front" and x in (3, 4) else belt) if y in (7, 8) and f not in ("top", "bottom") else None)
    # The left hand stays bare, the right one has a black glove.
    o.fill("left_arm", False, skin, rows=(11, 12))
    o.paint("body", True, lambda f, x, y, w, h: dark if f == "front" and y == 0 and x in (2, 3, 4, 5) else None)
    head_from(o, False, {
        "_": {"s": skin, "h": hair, "e": eye, "w": "#f2eee8", "m": "#b97a64"},
        "front": ["hhhhhhhh", "hhhhhhhh", "shhhhhhs", "ssssssss", "swessews", "ssssssss", "sssmmsss", "ssssssss"],
        "top": ["hhhhhhhh"] * 8, "bottom": ["ssssssss"] * 8,
        "right": ["hhhhhhhh", "hhhhhhhh", "hhhhhhhs", "hhhhhhss", "hhhhssss", "hhhsssss", "hhssssss", "hhssssss"],
        "left": ["hhhhhhhh", "hhhhhhhh", "shhhhhhh", "sshhhhhh", "sssshhhh", "ssssshhh", "sssssshh", "sssssshh"],
        "back": ["hhhhhhhh"] * 7 + ["hhhhhhhh"],
    })
    head_from(o, True, {
        "_": {"h": hair},
        "front": ["hhhhhhhh", "h..hh..h", "........", "........", "........", "........", "........", "........"],
        "top": ["h.h..h.h", ".h.hh.h.", "........", "........", "........", "........", "........", "........"],
    })
    o.save("luke")


if __name__ == "__main__":
    wanderer()
    shadow()
    sentinel()
    scout()
    twilight()
    vader()
    obi_wan()
    maul()
    luke()
