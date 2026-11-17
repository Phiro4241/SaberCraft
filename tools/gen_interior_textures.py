"""Paints the texture atlas of the starship interiors and hulls (Phase 15).

Every surface of the walkable Star Destroyer and Millennium Falcon that the client draws as a model
(wall panels, floors, ceilings, consoles, screens, pipes, the outer hull plating, engine glow) takes
its picture from one atlas: 16 x 16 tiles of 64 x 64 pixels. Everything is painted here in code
from noise, panel lines, rivets and light masks; nothing is taken from films or games. The look is
inspired by the two ships (cold grey Imperial panels with light strips, the worn off-white padding
and the cluttered machinery of a freighter), not copied.

A tile is either a surface ("base") or a glow mask ("glow": black where nothing shines). The game
draws glow masks a second time with an additive, unlit pass over their base, so lights and screens
shine in the dark and can blink.

    python3 tools/gen_interior_textures.py            -> atlas.png + InteriorAtlas.java
    python3 tools/gen_interior_textures.py --sheet    -> also /tmp/interior_atlas_sheet.png (labelled)
"""
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

TILE = 64
GRID = 16
OUT_PNG = os.path.join("src", "client", "resources", "assets", "sabercraft", "textures", "interior", "atlas.png")
OUT_JAVA = os.path.join("src", "client", "java", "com", "phiro", "sabercraft", "client", "interior", "InteriorAtlas.java")

TILES = []  # (name, rgba float array 64x64x4, comment)


def tile(name, comment=""):
    def wrap(fn):
        TILES.append((name, fn, comment))
        return fn
    return wrap


# --- Helpers -----------------------------------------------------------------------------------


def rng(seed):
    return np.random.default_rng(seed)


def value_noise(seed, cells, size=TILE):
    """Tileable value noise with `cells` cells across, smoothly interpolated, 0..1."""
    r = rng(seed)
    grid = r.random((cells, cells))
    xs = np.arange(size) * cells / size
    x0 = np.floor(xs).astype(int)
    t = xs - x0
    t = t * t * (3 - 2 * t)
    x1 = (x0 + 1) % cells
    x0 = x0 % cells
    a = grid[np.ix_(x0, x0)]
    b = grid[np.ix_(x0, x1)]
    c = grid[np.ix_(x1, x0)]
    d = grid[np.ix_(x1, x1)]
    ty = t[:, None]
    tx = t[None, :]
    return (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty


def fbm(seed, octaves=4, base=4):
    out = np.zeros((TILE, TILE))
    amp = 1.0
    total = 0.0
    for i in range(octaves):
        out += value_noise(seed + i * 17, base * (2 ** i)) * amp
        total += amp
        amp *= 0.5
    return out / total


def solid(rgb):
    img = np.zeros((TILE, TILE, 4))
    img[..., 0] = rgb[0] / 255.0
    img[..., 1] = rgb[1] / 255.0
    img[..., 2] = rgb[2] / 255.0
    img[..., 3] = 1.0
    return img


def shade(img, factor, mask=None):
    f = factor if mask is None else np.where(mask, factor, 1.0)
    if np.ndim(f) == 2:
        f = f[..., None]
    img[..., :3] = np.clip(img[..., :3] * f, 0, 1)
    return img


def grain(img, seed, amount=0.06, cells=16):
    n = fbm(seed, 3, cells // 4 or 1) - 0.5
    fine = rng(seed + 99).random((TILE, TILE)) - 0.5
    return shade(img, 1.0 + n * amount * 2.0 + fine * amount * 0.6)


def rect(img, x0, y0, x1, y1, rgb=None, factor=None):
    """Fills (x1, y1 exclusive) with a colour or darkens/lightens it by a factor."""
    x0, y0, x1, y1 = int(x0), int(y0), int(x1), int(y1)
    if rgb is not None:
        img[y0:y1, x0:x1, 0] = rgb[0] / 255.0
        img[y0:y1, x0:x1, 1] = rgb[1] / 255.0
        img[y0:y1, x0:x1, 2] = rgb[2] / 255.0
        img[y0:y1, x0:x1, 3] = 1.0
    if factor is not None:
        img[y0:y1, x0:x1, :3] = np.clip(img[y0:y1, x0:x1, :3] * factor, 0, 1)


def bevel(img, x0, y0, x1, y1, light=1.18, dark=0.72, width=1):
    """A raised panel: light top/left edge, dark bottom/right edge."""
    for w in range(width):
        rect(img, x0 + w, y0 + w, x1 - w, y0 + w + 1, factor=light)
        rect(img, x0 + w, y0 + w, x0 + w + 1, y1 - w, factor=light)
        rect(img, x0 + w, y1 - w - 1, x1 - w, y1 - w, factor=dark)
        rect(img, x1 - w - 1, y0 + w, x1 - w, y1 - w, factor=dark)


def inset(img, x0, y0, x1, y1, light=1.15, dark=0.7):
    """A recessed panel: dark top/left, light bottom/right."""
    bevel(img, x0, y0, x1, y1, light=dark, dark=light)


def line_h(img, y, factor, x0=0, x1=TILE):
    rect(img, x0, y, x1, y + 1, factor=factor)


def line_v(img, x, factor, y0=0, y1=TILE):
    rect(img, x, y0, x + 1, y1, factor=factor)


def rivet(img, x, y, light=1.3, dark=0.6):
    rect(img, x, y, x + 1, y + 1, factor=light)
    rect(img, x + 1, y + 1, x + 2, y + 2, factor=dark)


def glow_blob(img, cx, cy, r, rgb, strength=1.0):
    yy, xx = np.mgrid[0:TILE, 0:TILE]
    d = np.sqrt((xx + 0.5 - cx) ** 2 + (yy + 0.5 - cy) ** 2)
    a = np.clip(1.0 - d / r, 0, 1) ** 2 * strength
    for c in range(3):
        img[..., c] = np.clip(img[..., c] + a * rgb[c] / 255.0, 0, 1)
    img[..., 3] = 1.0
    return img


def dark():
    return solid((0, 0, 0))


def stain(img, seed, amount=0.25, cells=4, colour=(0.75, 0.7, 0.62)):
    """Grime: patches that darken and tint the surface."""
    n = np.clip((fbm(seed, 4, cells) - 0.52) * 3.0, 0, 1) * amount
    for c in range(3):
        img[..., c] = np.clip(img[..., c] * (1 - n) + img[..., c] * colour[c] * n * 0.85, 0, 1)
    return img


def scratches(img, seed, count=12, factor=1.25, length=10):
    r = rng(seed)
    for _ in range(count):
        x = r.integers(0, TILE)
        y = r.integers(0, TILE)
        dx, dy = r.choice([(1, 0), (0, 1), (1, 1), (1, -1)])
        for i in range(r.integers(3, length)):
            xx, yy = (x + dx * i) % TILE, (y + dy * i) % TILE
            img[yy, xx, :3] = np.clip(img[yy, xx, :3] * factor, 0, 1)
    return img


# --- Imperial: hull plating seen from outside -------------------------------------------------


IMP_HULL = (168, 172, 178)


def imp_hull_base(seed, panels):
    img = solid(IMP_HULL)
    img = grain(img, seed, 0.035)
    # Large plates with slightly different shades.
    r = rng(seed)
    for (x0, y0, x1, y1) in panels:
        rect(img, x0, y0, x1, y1, factor=0.94 + r.random() * 0.12)
        line_h(img, y0, 0.78, x0, x1)
        line_v(img, x0, 0.78, y0, y1)
    return img


@tile("imp_hull_a", "Star Destroyer plating: big plates")
def _():
    panels = [(0, 0, 32, 20), (32, 0, 64, 20), (0, 20, 20, 44), (20, 20, 64, 44), (0, 44, 40, 64), (40, 44, 64, 64)]
    img = imp_hull_base(11, panels)
    rect(img, 44, 26, 58, 38, factor=0.85)
    bevel(img, 44, 26, 58, 38, 1.1, 0.8)
    rect(img, 4, 50, 18, 54, factor=0.8)
    return img


@tile("imp_hull_b", "Star Destroyer plating: narrow strips and hatches")
def _():
    panels = [(0, y, 64, y + 8) for y in range(0, 64, 8)]
    img = imp_hull_base(12, panels)
    r = rng(5)
    for _ in range(7):
        x = r.integers(0, 54)
        y = r.integers(0, 7) * 8 + 2
        rect(img, x, y, x + r.integers(4, 10), y + 4, factor=0.82)
    return img


@tile("imp_hull_c", "Star Destroyer plating: greebles (pipes, boxes, vents)")
def _():
    img = imp_hull_base(13, [(0, 0, 64, 64)])
    r = rng(77)
    for _ in range(26):
        w, h = r.integers(3, 14), r.integers(2, 9)
        x, y = r.integers(0, TILE - w), r.integers(0, TILE - h)
        rect(img, x, y, x + w, y + h, factor=0.8 + r.random() * 0.35)
        bevel(img, x, y, x + w, y + h, 1.2, 0.65)
    for y in (10, 37, 52):
        line_h(img, y, 0.7)
        line_h(img, y + 1, 1.15)
    return img


@tile("imp_hull_trench", "Star Destroyer trench: dense dark machinery")
def _():
    img = solid((96, 100, 108))
    img = grain(img, 14, 0.06)
    r = rng(78)
    for _ in range(60):
        w, h = r.integers(2, 10), r.integers(2, 12)
        x, y = r.integers(0, TILE - w), r.integers(0, TILE - h)
        rect(img, x, y, x + w, y + h, factor=0.6 + r.random() * 0.7)
        bevel(img, x, y, x + w, y + h, 1.25, 0.55)
    return img


@tile("imp_hull_dark", "Star Destroyer: shadowed recesses")
def _():
    img = solid((70, 74, 82))
    img = grain(img, 15, 0.05)
    for y in range(4, 64, 12):
        line_h(img, y, 0.6)
    for x in range(6, 64, 16):
        line_v(img, x, 0.75)
    return img


@tile("imp_hull_windows", "Star Destroyer: hull with rows of small lit windows (base)")
def _():
    img = imp_hull_base(16, [(0, 0, 64, 32), (0, 32, 64, 64)])
    for row in (10, 26, 42, 58):
        for x in range(2, 64, 4):
            rect(img, x, row, x + 2, row + 2, (40, 44, 52))
    return img


@tile("imp_hull_windows_glow", "glow: the lit windows")
def _():
    img = dark()
    r = rng(17)
    for row in (10, 26, 42, 58):
        for x in range(2, 64, 4):
            if r.random() < 0.55:
                rect(img, x, row, x + 2, row + 2, (255, 236, 200) if r.random() < 0.8 else (190, 220, 255))
    return img


@tile("imp_engine_glow", "glow: the engine nozzles (bright core, blue rim)")
def _():
    img = dark()
    yy, xx = np.mgrid[0:TILE, 0:TILE]
    d = np.sqrt((xx + 0.5 - 32) ** 2 + (yy + 0.5 - 32) ** 2) / 32.0
    core = np.clip(1.0 - d, 0, 1)
    img[..., 0] = np.clip(core ** 0.6 * 0.75 + core ** 3 * 0.4, 0, 1)
    img[..., 1] = np.clip(core ** 0.5 * 0.88 + core ** 3 * 0.3, 0, 1)
    img[..., 2] = np.clip(core ** 0.35, 0, 1)
    return img


@tile("imp_engine_bell", "Star Destroyer: inside of an engine nozzle")
def _():
    img = solid((60, 64, 74))
    for y in range(0, 64, 4):
        line_h(img, y, 0.65)
        line_h(img, y + 1, 1.2)
    return grain(img, 18, 0.05)


@tile("imp_dome", "Star Destroyer: shield generator dome")
def _():
    img = solid((182, 186, 192))
    img = grain(img, 19, 0.04)
    for x in range(0, 64, 8):
        line_v(img, x, 0.85)
    for y in range(0, 64, 16):
        line_h(img, y, 0.85)
    return img


# --- Imperial: inside -----------------------------------------------------------------------


IMP_WALL = (58, 62, 70)
IMP_WALL_LIGHT = (82, 87, 96)


@tile("imp_wall", "Imperial wall: framed dark panels, one block a tile")
def _():
    img = solid(IMP_WALL)
    img = grain(img, 21, 0.03)
    inset(img, 3, 3, 61, 61, 1.25, 0.6)
    rect(img, 4, 4, 60, 60, factor=1.08)
    bevel(img, 8, 8, 56, 30, 1.18, 0.7)
    bevel(img, 8, 34, 56, 56, 1.18, 0.7)
    for (x, y) in ((5, 5), (57, 5), (5, 57), (57, 57)):
        rivet(img, x, y)
    return img


@tile("imp_wall_vent", "Imperial wall: a vent grille")
def _():
    img = solid(IMP_WALL)
    img = grain(img, 22, 0.03)
    inset(img, 3, 3, 61, 61, 1.25, 0.6)
    rect(img, 10, 10, 54, 54, (26, 28, 32))
    for y in range(12, 53, 4):
        rect(img, 11, y, 53, y + 2, (70, 74, 82))
        line_h(img, y, 1.25, 11, 53)
    inset(img, 10, 10, 54, 54, 1.3, 0.5)
    return img


@tile("imp_wall_strip", "Imperial wall with a light strip (base)")
def _():
    img = solid(IMP_WALL)
    img = grain(img, 23, 0.03)
    inset(img, 3, 3, 61, 61, 1.25, 0.6)
    rect(img, 0, 26, 64, 38, (34, 36, 42))
    rect(img, 0, 29, 64, 35, (190, 200, 210))
    line_h(img, 26, 1.3)
    line_h(img, 37, 0.6)
    return img


@tile("imp_wall_strip_glow", "glow: the light strip")
def _():
    img = dark()
    rect(img, 0, 29, 64, 35, (225, 235, 255))
    rect(img, 0, 28, 64, 29, (70, 76, 90))
    rect(img, 0, 35, 64, 36, (70, 76, 90))
    return img


@tile("imp_wall_tall", "Imperial wall: tall ribbed panel (upper wall)")
def _():
    img = solid(IMP_WALL_LIGHT)
    img = grain(img, 24, 0.03)
    for x in (0, 32):
        inset(img, x + 2, 2, x + 30, 62, 1.2, 0.65)
        for y in range(8, 60, 6):
            line_h(img, y, 0.82, x + 6, x + 26)
    return img


@tile("imp_floor", "Imperial floor: polished black tiles")
def _():
    img = solid((40, 43, 48))
    n = fbm(25, 4, 4)
    shade(img, 0.92 + n * 0.16)
    for v in (0, 32):
        line_h(img, v, 0.55)
        line_v(img, v, 0.55)
        line_h(img, v + 1, 1.3)
        line_v(img, v + 1, 1.3)
    return img


@tile("imp_floor_grate", "Imperial floor: grating")
def _():
    img = solid((44, 47, 54))
    for y in range(0, 64, 6):
        rect(img, 0, y, 64, y + 2, (18, 19, 22))
    for x in range(0, 64, 16):
        rect(img, x, 0, x + 2, 64, (70, 74, 82))
    return grain(img, 26, 0.04)


@tile("imp_floor_walkway", "Imperial floor: the walkway with guide lines")
def _():
    img = solid((52, 55, 62))
    img = grain(img, 27, 0.03)
    for x in (6, 57):
        rect(img, x, 0, x + 2, 64, (120, 126, 136))
    for y in range(0, 64, 16):
        line_h(img, y, 0.6)
        line_h(img, y + 1, 1.25)
    return img


@tile("imp_ceiling", "Imperial ceiling: coffers")
def _():
    img = solid((48, 51, 58))
    img = grain(img, 28, 0.03)
    inset(img, 2, 2, 62, 62, 1.2, 0.55)
    rect(img, 8, 8, 56, 56, factor=0.8)
    inset(img, 8, 8, 56, 56, 1.15, 0.6)
    return img


@tile("imp_ceiling_light", "Imperial ceiling: a light panel (base)")
def _():
    img = solid((48, 51, 58))
    inset(img, 2, 2, 62, 62, 1.2, 0.55)
    rect(img, 10, 10, 54, 54, (200, 210, 220))
    for x in range(10, 54, 6):
        line_v(img, x, 0.8, 10, 54)
    return img


@tile("imp_ceiling_light_glow", "glow: the ceiling light")
def _():
    img = dark()
    rect(img, 10, 10, 54, 54, (230, 238, 255))
    for x in range(10, 54, 6):
        line_v(img, x, 0.75, 10, 54)
    return img


@tile("imp_metal", "Imperial dark metal for frames, rails and pillars")
def _():
    img = solid((40, 43, 50))
    img = grain(img, 29, 0.04)
    for y in range(0, 64, 16):
        line_h(img, y, 0.7)
        line_h(img, y + 1, 1.2)
    return img


@tile("imp_metal_light", "Imperial light grey metal")
def _():
    img = solid((122, 127, 136))
    img = grain(img, 30, 0.035)
    scratches(img, 31, 10, 1.12)
    return img


@tile("imp_console", "Imperial console face: buttons, switches, small screens (base)")
def _():
    img = solid((34, 36, 42))
    img = grain(img, 32, 0.03)
    inset(img, 1, 1, 63, 63, 1.2, 0.6)
    r = rng(33)
    rect(img, 4, 4, 28, 20, (12, 16, 18))
    rect(img, 36, 4, 60, 20, (12, 16, 18))
    for y in range(26, 60, 6):
        for x in range(4, 60, 5):
            c = r.choice([0, 1, 2, 3])
            col = [(60, 62, 70), (90, 30, 30), (30, 70, 40), (70, 70, 80)][c]
            rect(img, x, y, x + 3, y + 3, col)
            bevel(img, x, y, x + 3, y + 3, 1.3, 0.6)
    return img


@tile("imp_console_glow", "glow: console lights and screens")
def _():
    img = dark()
    r = rng(34)
    # Screen 1: a sweep of green lines; screen 2: bars in blue.
    for y in range(6, 19, 3):
        w = r.integers(6, 22)
        rect(img, 5, y, 5 + w, y + 1, (90, 230, 140))
    for i in range(6):
        hgt = r.integers(3, 13)
        rect(img, 38 + i * 4, 19 - hgt, 41 + i * 4, 19, (100, 170, 255))
    for y in range(26, 60, 6):
        for x in range(4, 60, 5):
            if r.random() < 0.3:
                col = [(255, 70, 60), (90, 255, 120), (110, 170, 255), (255, 200, 80)][r.integers(0, 4)]
                rect(img, x + 1, y + 1, x + 2, y + 2, col)
    return img


@tile("imp_console_top", "Imperial console top: a slanted desk with a keypad")
def _():
    img = solid((44, 47, 54))
    img = grain(img, 35, 0.03)
    inset(img, 2, 2, 62, 62, 1.2, 0.6)
    rect(img, 6, 30, 58, 58, (26, 28, 32))
    for y in range(32, 56, 4):
        for x in range(8, 56, 4):
            rect(img, x, y, x + 3, y + 3, (70, 74, 82))
    rect(img, 6, 6, 58, 24, (10, 14, 16))
    return img


@tile("imp_console_top_glow", "glow: the desk screen")
def _():
    img = dark()
    r = rng(36)
    for y in range(8, 23, 2):
        w = r.integers(10, 48)
        rect(img, 8, y, 8 + w, y + 1, (80, 200, 255) if y % 4 else (110, 230, 160))
    for _ in range(5):
        x, y = r.integers(8, 56), r.integers(32, 56)
        rect(img, x, y, x + 3, y + 3, (255, 90, 70))
    return img


@tile("imp_screen", "Imperial wall screen frame (base)")
def _():
    img = solid((24, 26, 30))
    inset(img, 2, 2, 62, 62, 1.25, 0.55)
    rect(img, 5, 5, 59, 59, (6, 10, 12))
    return img


@tile("imp_screen_radar_glow", "glow: a radar screen")
def _():
    img = dark()
    yy, xx = np.mgrid[0:TILE, 0:TILE]
    d = np.sqrt((xx + 0.5 - 32) ** 2 + (yy + 0.5 - 32) ** 2)
    for rr in (8, 16, 24):
        ring = np.abs(d - rr) < 0.6
        img[ring] = [0.1, 0.55, 0.3, 1]
    cross = (np.abs(xx - 32) < 0.6) | (np.abs(yy - 32) < 0.6)
    img[cross & (d < 26)] = [0.1, 0.45, 0.25, 1]
    ang = np.arctan2(yy - 32, xx - 32)
    sweep = np.clip(1 - ((ang + math.pi) % (2 * math.pi)) / 1.2, 0, 1) * (d < 26)
    img[..., 1] = np.clip(img[..., 1] + sweep * 0.7, 0, 1)
    img[..., 0] = np.clip(img[..., 0] + sweep * 0.12, 0, 1)
    img[..., 2] = np.clip(img[..., 2] + sweep * 0.3, 0, 1)
    r = rng(37)
    for _ in range(7):
        a, rr = r.random() * 6.28, r.random() * 22 + 2
        x, y = int(32 + math.cos(a) * rr), int(32 + math.sin(a) * rr)
        rect(img, x, y, x + 2, y + 2, (255, 120, 90) if r.random() < 0.3 else (160, 255, 190))
    return img


@tile("imp_screen_tactical_glow", "glow: a tactical display (ship outlines, grid)")
def _():
    img = dark()
    for v in range(8, 60, 8):
        line_h(img, v, 1.0, 6, 58)
        rect(img, 6, v, 58, v + 1, (20, 50, 80))
        rect(img, v, 6, v + 1, 58, (20, 50, 80))
    pts = [(14, 40), (50, 34), (14, 28)]
    im = Image.new("RGB", (TILE, TILE))
    dr = ImageDraw.Draw(im)
    dr.polygon(pts, outline=(120, 200, 255))
    dr.line([(20, 46), (44, 20)], fill=(255, 110, 80))
    dr.ellipse([40, 14, 48, 22], outline=(255, 110, 80))
    arr = np.asarray(im).astype(float) / 255.0
    img[..., :3] = np.clip(img[..., :3] + arr, 0, 1)
    return img


@tile("imp_screen_text_glow", "glow: lines of readouts")
def _():
    img = dark()
    r = rng(38)
    for y in range(7, 58, 4):
        x = 6
        while x < 56:
            w = r.integers(2, 7)
            col = (255, 160, 70) if r.random() < 0.15 else (120, 210, 255)
            rect(img, x, y, min(58, x + w), y + 2, col)
            x += w + r.integers(1, 4)
    return img


@tile("imp_holo", "a hologram: blue scan lines (translucent)")
def _():
    img = np.zeros((TILE, TILE, 4))
    for y in range(TILE):
        a = 0.45 + 0.35 * (y % 3 == 0)
        img[y, :, :] = [0.45, 0.75, 1.0, a]
    n = fbm(39, 3, 4)
    img[..., 3] *= 0.75 + n * 0.5
    return img


@tile("imp_glass", "a window pane: faint reflections (translucent)")
def _():
    img = np.zeros((TILE, TILE, 4))
    yy, xx = np.mgrid[0:TILE, 0:TILE]
    streak = np.clip(1 - np.abs(((xx - yy * 0.6) % 48) - 12) / 5.0, 0, 1) * 0.10
    img[..., 0] = 0.55
    img[..., 1] = 0.65
    img[..., 2] = 0.75
    img[..., 3] = 0.06 + streak
    return img


@tile("imp_hazard", "hazard stripes")
def _():
    img = solid((40, 40, 40))
    yy, xx = np.mgrid[0:TILE, 0:TILE]
    stripe = ((xx + yy) // 8) % 2 == 0
    img[stripe] = [0.85, 0.7, 0.12, 1]
    return grain(img, 40, 0.06)


@tile("imp_door", "Imperial door: two halves with a seam")
def _():
    img = solid((70, 74, 82))
    img = grain(img, 41, 0.03)
    rect(img, 31, 0, 33, 64, (24, 26, 30))
    for y in (10, 54):
        rect(img, 4, y, 60, y + 2, (40, 43, 50))
    bevel(img, 2, 2, 31, 62, 1.15, 0.7)
    bevel(img, 33, 2, 62, 62, 1.15, 0.7)
    return img


@tile("imp_pipe", "Imperial pipe: banded metal, along u")
def _():
    img = solid((92, 97, 106))
    for y in range(TILE):
        shade_row = 0.6 + 0.6 * math.sin(math.pi * (y + 0.5) / TILE)
        img[y, :, :3] *= shade_row
    for x in range(0, 64, 16):
        rect(img, x, 0, x + 3, 64, factor=0.7)
    return grain(img, 42, 0.03)


@tile("imp_reactor_glow", "glow: the reactor core, swirling white-blue")
def _():
    img = dark()
    n = fbm(43, 4, 2)
    yy, xx = np.mgrid[0:TILE, 0:TILE]
    bands = 0.5 + 0.5 * np.sin(yy / 64 * math.pi * 6 + n * 6)
    img[..., 0] = 0.45 + 0.4 * bands
    img[..., 1] = 0.65 + 0.3 * bands
    img[..., 2] = 1.0
    return img


@tile("imp_force_field_glow", "glow: a hangar force field")
def _():
    img = dark()
    n = fbm(44, 4, 4)
    yy, xx = np.mgrid[0:TILE, 0:TILE]
    hexes = (np.abs(((xx + (yy // 8 % 2) * 4) % 8) - 4) < 0.8) | (yy % 8 == 0)
    a = 0.22 + 0.2 * n + 0.35 * hexes
    img[..., 0] = a * 0.4
    img[..., 1] = a * 0.7
    img[..., 2] = a * 1.0
    return img


@tile("imp_black", "near black (shadow gaps, backs)")
def _():
    return grain(solid((14, 15, 18)), 45, 0.04)


@tile("imp_chair", "Imperial seat: black padding")
def _():
    img = solid((26, 27, 30))
    for y in range(0, 64, 10):
        line_h(img, y, 0.6)
        line_h(img, y + 1, 1.25)
    return grain(img, 46, 0.05)


@tile("imp_crate", "Imperial crate: grey with markings")
def _():
    img = solid((96, 100, 108))
    img = grain(img, 47, 0.04)
    bevel(img, 1, 1, 63, 63, 1.2, 0.6, 2)
    rect(img, 8, 8, 56, 56, factor=0.92)
    inset(img, 8, 8, 56, 56)
    rect(img, 12, 44, 40, 48, (190, 160, 40))
    rect(img, 12, 14, 30, 18, (220, 220, 220))
    return img


@tile("imp_tie_panel", "TIE fighter wing panel: black with a fine grid")
def _():
    img = solid((28, 30, 36))
    for v in range(0, 64, 8):
        line_h(img, v, 1.7)
        line_v(img, v, 1.7)
    return grain(img, 48, 0.05)


# --- Millennium Falcon: outside -------------------------------------------------------------


FAL_HULL = (204, 200, 190)


def fal_base(seed):
    img = solid(FAL_HULL)
    img = grain(img, seed, 0.05)
    stain(img, seed + 1, 0.35, 3)
    return img


@tile("fal_hull_a", "freighter hull plating: worn off-white plates")
def _():
    img = fal_base(51)
    for (x0, y0, x1, y1) in [(0, 0, 40, 24), (40, 0, 64, 24), (0, 24, 24, 64), (24, 24, 64, 46), (24, 46, 64, 64)]:
        rect(img, x0, y0, x1, y1, factor=0.94 + (x0 * 7 + y0 * 3) % 10 / 80.0)
        line_h(img, y0, 0.72, x0, x1)
        line_v(img, x0, 0.72, y0, y1)
    scratches(img, 52, 16, 0.82, 12)
    return img


@tile("fal_hull_b", "freighter hull: plates with a red-brown repair patch")
def _():
    img = fal_base(53)
    for y in range(0, 64, 16):
        line_h(img, y, 0.72)
    for x in range(0, 64, 21):
        line_v(img, x, 0.72)
    rect(img, 30, 34, 56, 52, (150, 92, 70))
    stain(img, 54, 0.4, 5)
    bevel(img, 30, 34, 56, 52, 1.1, 0.75)
    for x in range(31, 56, 4):
        rivet(img, x, 35)
        rivet(img, x, 50)
    return img


@tile("fal_hull_greeble", "freighter hull: exposed machinery and pipes")
def _():
    img = solid((150, 146, 138))
    img = grain(img, 55, 0.06)
    r = rng(56)
    for _ in range(36):
        w, h = r.integers(3, 14), r.integers(2, 10)
        x, y = r.integers(0, TILE - w), r.integers(0, TILE - h)
        rect(img, x, y, x + w, y + h, factor=0.65 + r.random() * 0.6)
        bevel(img, x, y, x + w, y + h, 1.2, 0.6)
    for y in (6, 30, 50):
        rect(img, 0, y, 64, y + 3, (110, 106, 100))
        line_h(img, y, 1.25)
    stain(img, 57, 0.5, 4)
    return img


@tile("fal_hull_vents", "freighter hull: radial vent slots")
def _():
    img = fal_base(58)
    for x in range(4, 64, 6):
        rect(img, x, 8, x + 3, 56, (60, 58, 56))
        line_v(img, x, 0.6, 8, 56)
        line_v(img, x + 3, 1.2, 8, 56)
    return img


@tile("fal_engine_glow", "glow: the sublight engine band (blue-white)")
def _():
    img = dark()
    yy, xx = np.mgrid[0:TILE, 0:TILE]
    d = np.abs(yy + 0.5 - 32) / 32.0
    core = np.clip(1 - d, 0, 1)
    img[..., 0] = core ** 1.5 * 0.85
    img[..., 1] = core ** 1.1 * 0.95
    img[..., 2] = core ** 0.6
    for x in range(0, 64, 8):
        img[:, x, :3] *= 0.6
    return img


@tile("fal_cockpit_glass", "freighter cockpit glass: dark with a sheen (translucent)")
def _():
    img = np.zeros((TILE, TILE, 4))
    img[..., 0] = 0.18
    img[..., 1] = 0.22
    img[..., 2] = 0.28
    yy, xx = np.mgrid[0:TILE, 0:TILE]
    img[..., 3] = 0.35 + np.clip(1 - np.abs(((xx + yy) % 40) - 10) / 4.0, 0, 1) * 0.25
    return img


@tile("fal_dish", "radar dish: light grey with ribs")
def _():
    img = solid((190, 188, 182))
    yy, xx = np.mgrid[0:TILE, 0:TILE]
    d = np.sqrt((xx - 32) ** 2 + (yy - 32) ** 2)
    for rr in range(6, 32, 6):
        img[np.abs(d - rr) < 0.7] *= [0.7, 0.7, 0.7, 1]
    return grain(img, 59, 0.04)


# --- Millennium Falcon: inside --------------------------------------------------------------


FAL_PAD = (196, 188, 170)


@tile("fal_wall_pad", "freighter wall padding: quilted off-white cushions")
def _():
    img = solid(FAL_PAD)
    img = grain(img, 61, 0.04)
    yy, xx = np.mgrid[0:TILE, 0:TILE]
    for (cx, cy) in [(16, 16), (48, 16), (16, 48), (48, 48)]:
        d = np.maximum(np.abs(xx + 0.5 - cx), np.abs(yy + 0.5 - cy)) / 16.0
        puff = 1.12 - 0.3 * d ** 2
        img[..., :3] *= np.where(d < 1.0, puff, 1.0)[..., None]
    for v in (0, 32):
        line_h(img, v, 0.62)
        line_v(img, v, 0.62)
    for (x, y) in [(31, 31), (63, 31), (31, 63), (63, 63), (0, 0)]:
        rect(img, x - 1, y - 1, x + 1, y + 1, (120, 112, 100))
    stain(img, 62, 0.3, 3)
    return np.clip(img, 0, 1)


@tile("fal_wall_rib", "freighter wall: grey structural rib with bolts")
def _():
    img = solid((122, 120, 116))
    img = grain(img, 63, 0.05)
    bevel(img, 0, 0, 64, 64, 1.2, 0.6, 2)
    for y in range(6, 64, 10):
        rivet(img, 8, y)
        rivet(img, 54, y)
    stain(img, 64, 0.4, 4)
    return img


@tile("fal_wall_tech", "freighter wall: panels, conduits and a junction box")
def _():
    img = solid((140, 136, 128))
    img = grain(img, 65, 0.05)
    inset(img, 2, 2, 62, 30)
    inset(img, 2, 34, 30, 62)
    rect(img, 36, 36, 58, 58, (90, 88, 84))
    bevel(img, 36, 36, 58, 58, 1.25, 0.6)
    for x in (40, 46, 52):
        rect(img, x, 40, x + 3, 44, (180, 60, 40))
    for y in (8, 14, 20):
        rect(img, 6, y, 58, y + 3, (100, 98, 92))
        line_h(img, y, 1.3, 6, 58)
    stain(img, 66, 0.45, 4)
    return img


@tile("fal_floor", "freighter floor: metal grating over dark gaps")
def _():
    img = solid((96, 94, 90))
    for y in range(0, 64, 8):
        rect(img, 0, y + 5, 64, y + 8, (34, 33, 32))
        line_h(img, y, 1.25)
    for x in range(0, 64, 32):
        rect(img, x, 0, x + 2, 64, (70, 68, 66))
    stain(img, 67, 0.4, 4)
    return grain(img, 68, 0.05)


@tile("fal_floor_plate", "freighter floor: worn deck plate")
def _():
    img = solid((110, 108, 104))
    img = grain(img, 69, 0.05)
    for v in (0, 32):
        line_h(img, v, 0.65)
        line_v(img, v, 0.65)
    yy, xx = np.mgrid[0:TILE, 0:TILE]
    tread = ((xx // 4 + yy // 4) % 2 == 0) & ((xx % 4) == 1)
    img[tread] *= [1.15, 1.15, 1.15, 1]
    stain(img, 70, 0.5, 3)
    scratches(img, 71, 20, 1.2, 8)
    return img


@tile("fal_ceiling", "freighter ceiling: ribs and conduits")
def _():
    img = solid((128, 124, 118))
    img = grain(img, 72, 0.05)
    for x in (0, 32):
        rect(img, x, 0, x + 6, 64, (96, 94, 90))
        line_v(img, x + 6, 0.6)
    for y in (16, 44):
        rect(img, 0, y, 64, y + 4, (80, 78, 74))
        line_h(img, y, 1.25)
    stain(img, 73, 0.45, 4)
    return img


@tile("fal_ceiling_light", "freighter ceiling light panel (base)")
def _():
    img = solid((128, 124, 118))
    inset(img, 4, 4, 60, 60)
    rect(img, 10, 10, 54, 54, (220, 214, 196))
    return img


@tile("fal_ceiling_light_glow", "glow: the warm ceiling light")
def _():
    img = dark()
    rect(img, 10, 10, 54, 54, (255, 238, 205))
    return img


@tile("fal_metal", "freighter metal: grey, scuffed")
def _():
    img = solid((116, 114, 110))
    img = grain(img, 74, 0.06)
    scratches(img, 75, 18, 1.2)
    stain(img, 76, 0.4, 3)
    return img


@tile("fal_metal_dark", "freighter dark metal")
def _():
    img = solid((62, 60, 58))
    img = grain(img, 77, 0.06)
    scratches(img, 78, 14, 1.3)
    return img


@tile("fal_console", "cockpit panel: rows of switches and gauges (base)")
def _():
    img = solid((70, 68, 66))
    img = grain(img, 79, 0.05)
    r = rng(80)
    for row in range(5):
        y = 4 + row * 12
        rect(img, 2, y - 1, 62, y + 9, (52, 50, 48))
        for x in range(4, 60, 4):
            kind = r.integers(0, 4)
            if kind == 0:
                rect(img, x, y + 2, x + 2, y + 6, (200, 196, 186))
            elif kind == 1:
                rect(img, x, y + 1, x + 3, y + 4, (30, 30, 30))
            elif kind == 2:
                rect(img, x, y + 3, x + 3, y + 6, (150, 40, 30))
            else:
                rect(img, x, y + 2, x + 3, y + 5, (120, 118, 110))
    rect(img, 22, 40, 42, 60, (16, 20, 18))
    return img


@tile("fal_console_glow", "glow: indicator lamps and a green scope")
def _():
    img = dark()
    r = rng(81)
    for row in range(5):
        y = 4 + row * 12
        for x in range(4, 60, 4):
            if r.random() < 0.35:
                col = [(255, 80, 50), (255, 190, 60), (90, 255, 110), (120, 190, 255)][r.integers(0, 4)]
                rect(img, x + 1, y + 7, x + 2, y + 8, col)
    for x in range(23, 41):
        y = int(50 + 6 * math.sin(x / 2.5))
        rect(img, x, y, x + 1, y + 1, (100, 255, 140))
    return img


@tile("fal_screen_glow", "glow: a targeting/readout screen (amber)")
def _():
    img = dark()
    r = rng(82)
    for y in range(8, 56, 5):
        w = r.integers(10, 44)
        rect(img, 8, y, 8 + w, y + 2, (255, 170, 60))
    rect(img, 6, 6, 58, 7, (255, 170, 60))
    rect(img, 6, 57, 58, 58, (255, 170, 60))
    return img


@tile("fal_couch", "lounge couch: worn leather-like cushions")
def _():
    img = solid((176, 150, 116))
    img = grain(img, 83, 0.06)
    for x in range(0, 64, 21):
        line_v(img, x, 0.6)
    yy, xx = np.mgrid[0:TILE, 0:TILE]
    img[..., :3] *= (1.05 - 0.15 * ((xx % 21) / 21.0 - 0.5) ** 2)[..., None]
    stain(img, 84, 0.5, 3, (0.7, 0.6, 0.5))
    return np.clip(img, 0, 1)


@tile("fal_table", "the round game table top: dark with a ring pattern")
def _():
    img = solid((48, 50, 48))
    yy, xx = np.mgrid[0:TILE, 0:TILE]
    d = np.sqrt((xx + 0.5 - 32) ** 2 + (yy + 0.5 - 32) ** 2)
    ang = np.arctan2(yy - 32, xx - 32)
    sector = ((ang + math.pi) / (2 * math.pi) * 12).astype(int) % 2 == 0
    ring = (d > 10) & (d < 30)
    img[ring & sector] = [0.24, 0.30, 0.24, 1]
    img[ring & ~sector] = [0.18, 0.2, 0.19, 1]
    img[(np.abs(d - 10) < 0.8) | (np.abs(d - 20) < 0.6) | (np.abs(d - 30) < 0.8)] = [0.5, 0.52, 0.48, 1]
    img[d < 10] = [0.12, 0.14, 0.13, 1]
    return grain(img, 85, 0.04)


@tile("fal_holo_glow", "glow: green-blue hologram light")
def _():
    img = dark()
    for y in range(TILE):
        v = 0.55 + 0.45 * (y % 3 == 0)
        img[y, :, :3] = [0.35 * v, 0.95 * v, 0.75 * v]
    return img


@tile("fal_pipe", "freighter pipe: copper-brown banded")
def _():
    img = solid((150, 112, 84))
    for y in range(TILE):
        img[y, :, :3] *= 0.6 + 0.6 * math.sin(math.pi * (y + 0.5) / TILE)
    for x in range(0, 64, 20):
        rect(img, x, 0, x + 4, 64, (110, 108, 104))
    return grain(img, 86, 0.05)


@tile("fal_pipe_grey", "freighter pipe: grey banded")
def _():
    img = solid((150, 148, 142))
    for y in range(TILE):
        img[y, :, :3] *= 0.6 + 0.6 * math.sin(math.pi * (y + 0.5) / TILE)
    for x in range(0, 64, 12):
        rect(img, x, 0, x + 2, 64, factor=0.7)
    return grain(img, 87, 0.05)


@tile("fal_machine", "freighter machinery block: panels, gauges, warning marks")
def _():
    img = solid((104, 102, 98))
    img = grain(img, 88, 0.06)
    inset(img, 2, 2, 62, 62)
    for (x0, y0) in [(6, 6), (34, 6)]:
        rect(img, x0, y0, x0 + 24, y0 + 22, (80, 78, 74))
        bevel(img, x0, y0, x0 + 24, y0 + 22, 1.25, 0.6)
    yy, xx = np.mgrid[0:TILE, 0:TILE]
    d = np.sqrt((xx + 0.5 - 18) ** 2 + (yy + 0.5 - 46) ** 2)
    img[d < 9] = [0.85, 0.83, 0.78, 1]
    img[np.abs(d - 9) < 0.8] = [0.2, 0.2, 0.2, 1]
    stripe = ((xx + yy) // 4 % 2 == 0) & (xx > 34) & (xx < 60) & (yy > 50) & (yy < 58)
    img[stripe] = [0.8, 0.62, 0.12, 1]
    stain(img, 89, 0.5, 4)
    return img


@tile("fal_hyperdrive_glow", "glow: the hyperdrive core, pulsing blue")
def _():
    img = dark()
    n = fbm(90, 4, 3)
    yy, xx = np.mgrid[0:TILE, 0:TILE]
    v = 0.5 + 0.5 * np.sin(xx / 64 * math.pi * 4 + n * 5)
    img[..., 0] = 0.25 + 0.35 * v
    img[..., 1] = 0.55 + 0.35 * v
    img[..., 2] = 1.0
    return img


@tile("fal_crate", "freighter cargo crate: olive, stencilled")
def _():
    img = solid((112, 112, 92))
    img = grain(img, 91, 0.05)
    bevel(img, 1, 1, 63, 63, 1.2, 0.6, 2)
    rect(img, 6, 30, 58, 34, (70, 70, 58))
    rect(img, 10, 10, 26, 14, (220, 214, 196))
    stain(img, 92, 0.45, 3)
    return img


@tile("fal_hatch", "floor hatch: round-cornered plate with handles")
def _():
    img = solid((100, 98, 94))
    img = grain(img, 93, 0.05)
    inset(img, 3, 3, 61, 61, 1.3, 0.55)
    rect(img, 26, 28, 38, 32, (50, 48, 46))
    for (x, y) in [(6, 6), (56, 6), (6, 56), (56, 56)]:
        rivet(img, x, y)
    return img


@tile("fal_bunk", "crew bunk mattress: grey-blue fabric")
def _():
    img = solid((120, 128, 140))
    img = grain(img, 94, 0.06)
    for y in range(0, 64, 16):
        line_h(img, y, 0.7)
    stain(img, 95, 0.3, 3)
    return img


@tile("white", "plain white (tinted by vertex colour)")
def _():
    return solid((255, 255, 255))


# --- Phase 17: the crew of the Star Destroyer (drawn figures) ----------------------------------
# Faces of blocky figures (a tile per face of a box, stretched to it): troopers in white armour,
# officers in grey uniforms, technicians in black. Inspired by the look, not copied.


def armour_white(seed):
    img = solid((226, 228, 232))
    grain(img, seed, 0.035)
    return img


@tile("crew_trooper_helmet", "crew: trooper helmet, front (eyes, breathing grille)")
def _():
    img = armour_white(1701)
    # The brow and the cheeks are bevelled plates.
    bevel(img, 4, 6, 60, 30, 1.06, 0.86, 2)
    # Eye lenses: dark, slanting down to the outside.
    for side in (0, 1):
        for i in range(10):
            x0 = 12 + i if side == 0 else 52 - i - 12
            y0 = 22 + (i // 3 if side == 0 else (9 - i) // 3)
            rect(img, (14 + i) if side == 0 else (40 + i), 22 + (i // 4 if side == 0 else (9 - i) // 4), (15 + i) if side == 0 else (41 + i), 31 - (i // 3 if side == 0 else (9 - i) // 3), (24, 26, 30))
    # The tube-like breathing grille and the "frown".
    rect(img, 24, 40, 40, 48, (70, 74, 82))
    for x in range(25, 40, 3):
        line_v(img, x, 0.5, 41, 47)
    rect(img, 18, 50, 46, 52, factor=0.55)
    rect(img, 16, 46, 22, 52, factor=0.6)
    rect(img, 42, 46, 48, 52, factor=0.6)
    # Side vents (dark), the chin.
    rect(img, 6, 38, 12, 54, factor=0.55)
    rect(img, 52, 38, 58, 54, factor=0.55)
    rect(img, 0, 58, 64, 64, factor=0.75)
    return img


@tile("crew_trooper_helmet_side", "crew: trooper helmet, sides and back")
def _():
    img = armour_white(1702)
    bevel(img, 3, 4, 61, 60, 1.06, 0.86, 2)
    # The ear piece and a dark band low down.
    for y in range(64):
        for x in range(64):
            if (x - 32) ** 2 + (y - 34) ** 2 < 90:
                img[y, x, :3] *= 0.82
    rect(img, 26, 28, 38, 40, factor=0.75)
    rect(img, 0, 50, 64, 54, (60, 64, 70))
    return img


@tile("crew_trooper_torso", "crew: trooper chest and back (plates, black under-suit, belt)")
def _():
    img = solid((32, 34, 38))
    grain(img, 1703, 0.05)
    # Chest plate, shoulders, the plates of the belly.
    rect(img, 6, 2, 58, 26, (228, 230, 234))
    bevel(img, 6, 2, 58, 26, 1.05, 0.82, 2)
    rect(img, 14, 30, 50, 36, (220, 222, 226))
    rect(img, 16, 38, 48, 44, (220, 222, 226))
    # The belt with its pouches.
    rect(img, 0, 48, 64, 56, (214, 216, 220))
    for x in (6, 18, 40, 52):
        rect(img, x, 47, x + 7, 57, (236, 238, 240))
        bevel(img, x, 47, x + 7, 57, 1.05, 0.8, 1)
    rect(img, 28, 49, 36, 55, (120, 124, 130))
    # Little controls on the chest.
    rect(img, 24, 12, 30, 16, (60, 64, 70))
    rect(img, 34, 12, 40, 16, (150, 60, 50))
    return img


@tile("crew_trooper_limb", "crew: trooper arm or leg (white plates, black joints)")
def _():
    img = solid((30, 32, 36))
    grain(img, 1704, 0.05)
    rect(img, 4, 2, 60, 26, (228, 230, 234))
    bevel(img, 4, 2, 60, 26, 1.05, 0.82, 2)
    rect(img, 4, 34, 60, 60, (228, 230, 234))
    bevel(img, 4, 34, 60, 60, 1.05, 0.82, 2)
    rect(img, 0, 60, 64, 64, (210, 212, 216))
    return img


@tile("crew_officer_face", "crew: officer's face under the cap")
def _():
    img = solid((214, 172, 140))
    grain(img, 1705, 0.04)
    # The cap: grey with a black visor.
    rect(img, 0, 0, 64, 16, (96, 102, 92))
    rect(img, 0, 14, 64, 19, (22, 22, 24))
    # Eyes, brows, nose and mouth.
    for x in (18, 40):
        rect(img, x, 28, x + 7, 32, (250, 250, 248))
        rect(img, x + 2, 28, x + 5, 32, (60, 44, 34))
        rect(img, x - 1, 24, x + 8, 26, (90, 66, 50))
    rect(img, 30, 32, 34, 42, factor=0.9)
    rect(img, 24, 47, 40, 50, (150, 90, 80))
    rect(img, 0, 19, 6, 40, (70, 52, 40))
    rect(img, 58, 19, 64, 40, (70, 52, 40))
    return img


@tile("crew_officer_head", "crew: officer's head, sides and back (cap, short hair)")
def _():
    img = solid((70, 52, 40))
    grain(img, 1706, 0.06)
    rect(img, 0, 0, 64, 18, (96, 102, 92))
    rect(img, 0, 16, 64, 19, (60, 64, 58))
    rect(img, 0, 52, 64, 64, (214, 172, 140))
    return img


@tile("crew_officer_tunic", "crew: officer's tunic (grey, belt, rank plaque)")
def _():
    img = solid((104, 110, 100))
    grain(img, 1707, 0.05)
    line_v(img, 32, 0.8, 0, 46)
    rect(img, 0, 46, 64, 52, (24, 24, 26))
    rect(img, 28, 46, 36, 52, (170, 170, 176))
    # The rank plaque: rows of small red and blue squares.
    rect(img, 8, 10, 26, 18, (40, 40, 44))
    for i, col in enumerate(((200, 50, 40), (200, 50, 40), (60, 90, 200), (220, 200, 60))):
        rect(img, 9 + i * 4, 11, 12 + i * 4, 14, col)
        rect(img, 9 + i * 4, 15, 12 + i * 4, 17, (200, 50, 40) if i % 2 else (60, 90, 200))
    rect(img, 0, 52, 64, 64, (92, 98, 88))
    return img


@tile("crew_officer_limb", "crew: officer's sleeve or trouser leg (black boot below)")
def _():
    img = solid((96, 102, 92))
    grain(img, 1708, 0.05)
    line_v(img, 32, 0.85)
    rect(img, 0, 40, 64, 64, (20, 20, 22))
    rect(img, 0, 40, 64, 42, (60, 60, 64))
    return img


@tile("crew_tech_helmet", "crew: technician's black helmet, front (visor)")
def _():
    img = solid((26, 27, 30))
    grain(img, 1709, 0.06)
    bevel(img, 2, 2, 62, 62, 1.25, 0.7, 2)
    rect(img, 8, 22, 56, 36, (10, 10, 12))
    rect(img, 8, 22, 56, 24, (90, 96, 110))
    rect(img, 22, 44, 42, 50, (60, 62, 68))
    return img


@tile("crew_tech_body", "crew: technician's black suit (chest box, belt)")
def _():
    img = solid((30, 31, 34))
    grain(img, 1710, 0.06)
    rect(img, 14, 10, 34, 24, (54, 56, 62))
    bevel(img, 14, 10, 34, 24, 1.3, 0.7, 1)
    for i, col in enumerate(((200, 60, 50), (60, 180, 90), (60, 110, 220))):
        rect(img, 17 + i * 5, 14, 20 + i * 5, 17, col)
    rect(img, 0, 46, 64, 52, (16, 16, 18))
    rect(img, 28, 46, 36, 52, (120, 122, 128))
    return img


# --- Phase 18: more people (faces, uniforms, pilots, guards, the Falcon's crew) ---------------

SKINS = ((222, 180, 148), (196, 150, 112), (150, 104, 74), (234, 196, 170))
HAIRS = ((70, 50, 36), (40, 34, 30), (150, 146, 140), (170, 120, 70))


def disc(img, cx, cy, r, rgb=None, factor=None):
    for y in range(max(0, int(cy - r)), min(TILE, int(cy + r) + 1)):
        for x in range(max(0, int(cx - r)), min(TILE, int(cx + r) + 1)):
            if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r:
                rect(img, x, y, x + 1, y + 1, rgb, factor)


def face_tile(seed, skin, hair, top=10, moustache=False, beard=False, brows=None, eyes=(70, 90, 110), long_hair=False):
    """A face filling the whole front of a head: hair line at {top}, eyes, nose, mouth."""
    img = solid(skin)
    grain(img, seed, 0.035)
    rect(img, 0, 0, 64, top, hair)
    grain_mask = rng(seed + 3).random((TILE, TILE)) - 0.5
    img[:top, :, :3] = np.clip(img[:top, :, :3] * (1.0 + grain_mask[:top, :, None] * 0.12), 0, 1)
    if long_hair:
        rect(img, 0, top, 7, 52, hair)
        rect(img, 57, top, 64, 52, hair)
    brows = brows or tuple(int(c * 0.8) for c in hair)
    for x in (17, 40):
        rect(img, x - 1, 24, x + 8, 26, brows)
        rect(img, x, 28, x + 7, 32, (246, 246, 242))
        rect(img, x + 2, 28, x + 5, 32, eyes)
        rect(img, x + 3, 29, x + 4, 31, (20, 20, 22))
    rect(img, 30, 31, 34, 42, factor=0.9)
    rect(img, 29, 41, 35, 43, factor=0.8)
    if moustache:
        rect(img, 24, 44, 40, 47, hair)
    rect(img, 25, 48, 39, 50, (150, 90, 80))
    if beard:
        rect(img, 8, 46, 56, 64, hair)
        rect(img, 24, 48, 40, 51, (120, 70, 64))
    rect(img, 0, top, 4, 40, factor=0.92)
    rect(img, 60, top, 64, 40, factor=0.92)
    return img


def head_side_tile(seed, skin, hair, low=40):
    """The sides and back of a bare head: hair down to {low}, skin (the neck, an ear) below."""
    img = solid(hair)
    grain(img, seed, 0.1)
    rect(img, 0, low, 64, 64, skin)
    rect(img, 26, low - 14, 36, low + 4, skin)
    rect(img, 28, low - 12, 34, low + 2, factor=0.85)
    return img


for _i, (_skin, _hair, _mo, _beard, _eyes) in enumerate((
        (SKINS[0], HAIRS[0], False, False, (70, 90, 110)),
        (SKINS[1], HAIRS[1], True, False, (60, 44, 34)),
        (SKINS[2], HAIRS[1], False, False, (50, 36, 28)),
        (SKINS[3], HAIRS[2], True, False, (80, 110, 130)))):
    def _make(skin=_skin, hair=_hair, mo=_mo, beard=_beard, eyes=_eyes, seed=1800 + _i):
        return face_tile(seed, skin, hair, 12, mo, beard, eyes=eyes)
    tile("crew_face_%d" % _i, "crew: an officer's face (variant %d), the cap is drawn over it" % _i)(_make)

for _i, (_skin, _hair) in enumerate(((SKINS[0], HAIRS[0]), (SKINS[1], HAIRS[1]), (SKINS[2], HAIRS[1]), (SKINS[3], HAIRS[2]))):
    def _make(skin=_skin, hair=_hair, seed=1810 + _i):
        return head_side_tile(seed, skin, hair)
    tile("crew_head_%d" % _i, "crew: sides and back of an officer's head (variant %d)" % _i)(_make)


@tile("crew_hair_top", "crew: the top of a head, light (tinted to the hair colour)")
def _():
    img = solid((236, 236, 236))
    grain(img, 1820, 0.14)
    for x in range(4, 64, 7):
        line_v(img, x, 0.92)
    return img


@tile("crew_uniform_tunic", "crew: an officer's tunic, light (tinted to the uniform): belt, rank plaque, code cylinders")
def _():
    img = solid((232, 232, 228))
    grain(img, 1821, 0.045)
    line_v(img, 32, 0.82, 0, 46)
    rect(img, 30, 0, 34, 6, factor=0.75)
    rect(img, 0, 46, 64, 52, (26, 26, 28))
    rect(img, 28, 46, 36, 52, (190, 190, 196))
    rect(img, 8, 10, 26, 18, (46, 46, 50))
    for i, col in enumerate(((210, 60, 50), (210, 60, 50), (70, 100, 210), (230, 210, 70))):
        rect(img, 9 + i * 4, 11, 12 + i * 4, 14, col)
        rect(img, 9 + i * 4, 15, 12 + i * 4, 17, (210, 60, 50) if i % 2 else (70, 100, 210))
    for x in (44, 48):
        rect(img, x, 8, x + 2, 18, (200, 200, 206))
    rect(img, 0, 52, 64, 64, factor=0.9)
    return img


@tile("crew_uniform_limb", "crew: an officer's sleeve or trouser leg, light (tinted), black boot below")
def _():
    img = solid((232, 232, 228))
    grain(img, 1822, 0.045)
    line_v(img, 32, 0.86)
    rect(img, 0, 40, 64, 64, (24, 24, 26))
    rect(img, 0, 40, 64, 42, (64, 64, 68))
    return img


@tile("crew_cap", "crew: an officer's cap, light (tinted), with the rank disc")
def _():
    img = solid((232, 232, 228))
    grain(img, 1823, 0.05)
    rect(img, 0, 52, 64, 64, factor=0.8)
    disc(img, 32, 30, 6, (200, 200, 206))
    disc(img, 32, 30, 3, (120, 120, 128))
    return img


@tile("crew_trooper_helmet_top", "crew: the top of a trooper's helmet (white dome, a ridge)")
def _():
    img = armour_white(1824)
    bevel(img, 3, 3, 61, 61, 1.05, 0.88, 2)
    rect(img, 30, 0, 34, 64, factor=0.9)
    return img


@tile("crew_tech_helmet_side", "crew: technician's black helmet, sides, back and top")
def _():
    img = solid((28, 29, 32))
    grain(img, 1825, 0.06)
    bevel(img, 2, 2, 62, 62, 1.25, 0.7, 2)
    rect(img, 0, 48, 64, 52, (60, 62, 68))
    return img


@tile("crew_pilot_helmet", "crew: starfighter pilot's helmet, front (big lenses, breathing tubes)")
def _():
    img = solid((22, 23, 26))
    grain(img, 1826, 0.06)
    bevel(img, 2, 2, 62, 62, 1.3, 0.7, 2)
    for x in (14, 38):
        disc(img, x + 6, 26, 9, (8, 8, 10))
        disc(img, x + 4, 23, 3, (70, 74, 90))
    rect(img, 26, 38, 38, 56, (40, 42, 46))
    for y in range(40, 56, 3):
        line_h(img, y, 0.6, 26, 38)
    for side in (0, 1):
        for i in range(10):
            x = 22 - i * 2 if side == 0 else 42 + i * 2
            rect(img, x, 52 + i // 2, x + 3, 55 + i // 2, (70, 72, 78))
    rect(img, 6, 6, 12, 12, (150, 150, 156))
    rect(img, 52, 6, 58, 12, (150, 150, 156))
    return img


@tile("crew_pilot_helmet_side", "crew: pilot's helmet, sides, back and top (black, a grey emblem)")
def _():
    img = solid((22, 23, 26))
    grain(img, 1827, 0.06)
    bevel(img, 2, 2, 62, 62, 1.25, 0.72, 2)
    disc(img, 32, 30, 8, (120, 122, 128))
    disc(img, 32, 30, 5, (22, 23, 26))
    return img


@tile("crew_pilot_body", "crew: pilot's black flight suit (chest box with switches, hoses, harness)")
def _():
    img = solid((26, 27, 30))
    grain(img, 1828, 0.06)
    rect(img, 16, 6, 48, 26, (44, 46, 52))
    bevel(img, 16, 6, 48, 26, 1.3, 0.7, 1)
    for i, col in enumerate(((210, 60, 50), (220, 200, 60), (60, 120, 220), (200, 200, 206), (60, 180, 90))):
        rect(img, 19 + i * 6, 10, 23 + i * 6, 14, col)
    rect(img, 19, 17, 45, 22, (90, 92, 100))
    for x in (8, 56):
        rect(img, x - 3, 0, x + 3, 46, (60, 62, 68))
    rect(img, 0, 46, 64, 52, (14, 14, 16))
    rect(img, 26, 46, 38, 52, (150, 152, 160))
    return img


@tile("crew_pilot_limb", "crew: pilot's sleeve or leg (black, a harness strap, boots)")
def _():
    img = solid((26, 27, 30))
    grain(img, 1829, 0.06)
    rect(img, 0, 18, 64, 22, (60, 62, 68))
    rect(img, 0, 44, 64, 64, (12, 12, 14))
    return img


@tile("crew_guard_helmet", "crew: honour guard's red helmet, front (a narrow black visor)")
def _():
    img = solid((170, 24, 22))
    grain(img, 1830, 0.08)
    shade(img, 0.9 + 0.2 * (np.linspace(1, 0, TILE)[None, :] * 0 + np.linspace(1.0, 0.7, TILE)[:, None]))
    rect(img, 18, 22, 46, 27, (12, 10, 10))
    rect(img, 30, 27, 34, 46, (12, 10, 10))
    bevel(img, 2, 2, 62, 62, 1.2, 0.7, 2)
    return img


@tile("crew_guard_robe", "crew: honour guard's red robe (folds), also its hood and sleeves")
def _():
    img = solid((160, 22, 20))
    grain(img, 1831, 0.08)
    for x in range(3, 64, 9):
        line_v(img, x, 0.78)
        line_v(img, x + 1, 1.12)
    return img


@tile("crew_smuggler_face", "crew: the Falcon's smuggler: a face with a crooked smile, brown hair")
def _():
    img = face_tile(1832, (220, 176, 140), (92, 64, 40), 12, eyes=(70, 90, 70))
    rect(img, 34, 47, 39, 49, (150, 90, 80))
    rect(img, 38, 46, 40, 48, (150, 90, 80))
    rect(img, 24, 50, 30, 51, factor=0.92)
    return img


@tile("crew_smuggler_head", "crew: the smuggler's head, sides and back (brown hair)")
def _():
    return head_side_tile(1833, (220, 176, 140), (92, 64, 40), 44)


@tile("crew_smuggler_torso", "crew: the smuggler: white shirt, black vest, holster belt")
def _():
    img = solid((228, 224, 214))
    grain(img, 1834, 0.05)
    rect(img, 0, 0, 20, 46, (28, 28, 30))
    rect(img, 44, 0, 64, 46, (28, 28, 30))
    rect(img, 22, 0, 42, 6, factor=0.85)
    for y in (12, 22, 32):
        rect(img, 8, y, 12, y + 4, (60, 60, 64))
    rect(img, 0, 46, 64, 53, (90, 60, 36))
    rect(img, 6, 46, 14, 53, (180, 170, 140))
    rect(img, 50, 46, 60, 60, (70, 46, 28))
    rect(img, 0, 53, 64, 64, (40, 44, 60))
    return img


@tile("crew_smuggler_arm", "crew: the smuggler's sleeve (white shirt, the hand below)")
def _():
    img = solid((228, 224, 214))
    grain(img, 1835, 0.05)
    line_v(img, 32, 0.9)
    rect(img, 0, 52, 64, 64, (220, 176, 140))
    return img


@tile("crew_smuggler_legs", "crew: the smuggler's trousers (dark blue, a red stripe, boots)")
def _():
    img = solid((40, 44, 62))
    grain(img, 1836, 0.06)
    rect(img, 4, 0, 8, 40, (150, 40, 34))
    rect(img, 56, 0, 60, 40, (150, 40, 34))
    rect(img, 0, 40, 64, 64, (36, 26, 20))
    return img


@tile("crew_wookiee_face", "crew: the tall furry mechanic's face (fur, deep-set eyes, a dark nose)")
def _():
    img = solid((118, 84, 52))
    grain(img, 1837, 0.2, 32)
    for x in (14, 38):
        rect(img, x, 22, x + 12, 30, (70, 48, 30))
        rect(img, x + 3, 24, x + 8, 28, (30, 22, 16))
        rect(img, x + 4, 25, x + 6, 27, (120, 160, 190))
    rect(img, 26, 34, 38, 42, (40, 28, 22))
    rect(img, 22, 48, 42, 52, (60, 30, 26))
    return img


@tile("crew_wookiee_fur", "crew: brown fur (shaggy streaks)")
def _():
    img = solid((122, 88, 56))
    grain(img, 1838, 0.22, 32)
    r = rng(1839)
    for _ in range(90):
        x = int(r.integers(0, 64))
        y = int(r.integers(0, 56))
        rect(img, x, y, x + 1, y + int(r.integers(4, 10)), factor=float(r.choice([0.75, 1.2])))
    return img


@tile("crew_wookiee_torso", "crew: brown fur with a bandolier across it")
def _():
    img = solid((122, 88, 56))
    grain(img, 1840, 0.22, 32)
    for i in range(64):
        y = int(i * 0.9)
        rect(img, i, y, i + 1, min(64, y + 9), (78, 66, 52))
        if i % 9 < 5:
            rect(img, i, y + 2, i + 1, min(64, y + 7), (170, 172, 176))
    return img


@tile("crew_rebel_face", "crew: a rebel passenger's face (short dark hair)")
def _():
    return face_tile(1841, (196, 150, 112), (36, 30, 26), 13, eyes=(60, 44, 34))


@tile("crew_rebel_head", "crew: the rebel's head, sides and back")
def _():
    return head_side_tile(1842, (196, 150, 112), (36, 30, 26), 42)


@tile("crew_rebel_suit", "crew: an orange flight suit with a white vest and harness")
def _():
    img = solid((214, 112, 40))
    grain(img, 1843, 0.06)
    rect(img, 12, 0, 52, 30, (226, 224, 216))
    rect(img, 30, 0, 34, 30, factor=0.8)
    for x in (18, 44):
        rect(img, x - 2, 0, x + 2, 46, (50, 50, 54))
    rect(img, 22, 8, 28, 14, (180, 50, 40))
    rect(img, 0, 46, 64, 52, (50, 50, 54))
    return img


@tile("crew_rebel_limb", "crew: the rebel's orange sleeve or leg, a black boot or glove below")
def _():
    img = solid((214, 112, 40))
    grain(img, 1844, 0.06)
    line_v(img, 32, 0.88)
    rect(img, 0, 46, 64, 64, (30, 30, 32))
    return img


# --- Writing ---------------------------------------------------------------------------------


def main():
    if len(TILES) > GRID * GRID:
        raise SystemExit("too many tiles")
    atlas = np.zeros((GRID * TILE, GRID * TILE, 4))
    names = []
    for i, (name, fn, comment) in enumerate(TILES):
        img = fn()
        img = np.clip(img, 0, 1)
        tx, ty = i % GRID, i // GRID
        atlas[ty * TILE:(ty + 1) * TILE, tx * TILE:(tx + 1) * TILE] = img
        names.append((name, i, comment))
    out = Image.fromarray((atlas * 255 + 0.5).astype(np.uint8), "RGBA")
    os.makedirs(os.path.dirname(OUT_PNG), exist_ok=True)
    out.save(OUT_PNG, optimize=True)

    lines = [
        "package com.phiro.sabercraft.client.interior;",
        "",
        "/**",
        " * The tiles of the interior atlas ({@code textures/interior/atlas.png}), written by",
        " * {@code tools/gen_interior_textures.py}: do not edit by hand. A tile is {@value #TILE} pixels in a",
        " * grid of {@value #GRID} x {@value #GRID}.",
        " */",
        "public final class InteriorAtlas {",
        "\tpublic static final int TILE = %d;" % TILE,
        "\tpublic static final int GRID = %d;" % GRID,
        "",
    ]
    for name, i, comment in names:
        if comment:
            lines.append("\t/** %s */" % comment)
        lines.append("\tpublic static final int %s = %d;" % (name.upper(), i))
    lines += ["", "\tprivate InteriorAtlas() {", "\t}", "}", ""]
    os.makedirs(os.path.dirname(OUT_JAVA), exist_ok=True)
    with open(OUT_JAVA, "w") as f:
        f.write("\n".join(lines))
    print("tiles:", len(TILES), "->", OUT_PNG, os.path.getsize(OUT_PNG), "bytes")

    if "--sheet" in sys.argv:
        scale = 2
        cols = 8
        rows = (len(TILES) + cols - 1) // cols
        sheet = Image.new("RGB", (cols * (TILE * scale + 8), rows * (TILE * scale + 20)), (30, 30, 30))
        dr = ImageDraw.Draw(sheet)
        for i, (name, idx, _) in enumerate(names):
            tx, ty = idx % GRID, idx // GRID
            crop = out.crop((tx * TILE, ty * TILE, (tx + 1) * TILE, (ty + 1) * TILE)).resize((TILE * scale, TILE * scale), Image.NEAREST)
            bg = Image.new("RGB", crop.size, (90, 90, 120))
            bg.paste(crop, (0, 0), crop)
            x, y = (i % cols) * (TILE * scale + 8), (i // cols) * (TILE * scale + 20)
            sheet.paste(bg, (x + 4, y + 16))
            dr.text((x + 4, y + 2), name[:22], fill=(230, 230, 230))
        sheet.save("/tmp/claude-0/interior_atlas_sheet.png")


if __name__ == "__main__":
    main()
