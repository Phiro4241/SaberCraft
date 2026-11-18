#!/usr/bin/env python3
"""Phase 24: renders SaberCraft's own title screen background (no game or film files used).

A space scene drawn from scratch with numpy and Pillow: a nebula, a dense star field with a
galaxy band, a desert planet with its atmosphere lit by a far sun, a wedge-shaped capital ship
(our own design, its hull lights and engines glowing), a pair of small fighters and a few blaster
bolts between them. Written to
src/client/resources/assets/sabercraft/textures/gui/title/background.png (drawn at 1920x1080, stored at 1600x900).

Usage: python3 tools/make_title_background.py [out.png]
"""

import math
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

W, H = 1920, 1080
SS = 2  # supersampling for the shapes
rng = np.random.default_rng(1977)


def fbm(w, h, octaves=6, base=4, seed=0, persistence=0.55):
    """Fractal value noise in [0, 1] (smooth, by upsampling random grids)."""
    r = np.random.default_rng(seed)
    total = np.zeros((h, w), np.float32)
    amp = 1.0
    norm = 0.0
    cells = base
    for _ in range(octaves):
        grid = r.random((cells + 1, int(cells * w / h) + 2)).astype(np.float32)
        img = Image.fromarray((grid * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)
        total += amp * (np.asarray(img, np.float32) / 255.0)
        norm += amp
        amp *= persistence
        cells *= 2
    return total / norm


def blur(arr, radius):
    """Gaussian blur of a float image (h, w, 3) or (h, w), through the FFT (wrapping at the edges is far too faint to see)."""
    two = arr.ndim == 2
    if two:
        arr = arr[..., None]
    h, w = arr.shape[:2]
    fy = np.fft.fftfreq(h)[:, None]
    fx = np.fft.rfftfreq(w)[None, :]
    kernel = np.exp(-2.0 * (math.pi * radius) ** 2 * (fx * fx + fy * fy)).astype(np.float32)
    out = np.empty_like(arr)
    for c in range(arr.shape[2]):
        out[..., c] = np.fft.irfft2(np.fft.rfft2(arr[..., c]) * kernel, s=(h, w)).astype(np.float32)
    return out[..., 0] if two else out


def radial(cx, cy, r, w=W, h=H):
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    return np.sqrt((x - cx) ** 2 + (y - cy) ** 2) / r


canvas = np.zeros((H, W, 3), np.float32)

# --- Deep space and the nebula ------------------------------------------------------------------
y, x = np.mgrid[0:H, 0:W].astype(np.float32)
canvas += np.stack([0.010 + 0.012 * (x / W), 0.012 + 0.006 * (1 - y / H), 0.030 + 0.020 * (1 - x / W)], -1)

n1 = fbm(W, H, octaves=7, base=3, seed=11)
n2 = fbm(W, H, octaves=6, base=5, seed=23)
n3 = fbm(W, H, octaves=5, base=2, seed=37)
band = np.exp(-(((y - (0.15 * H + 0.55 * x * H / W)) / (0.30 * H)) ** 2))  # a diagonal band
cloud = np.clip((n1 - 0.42) * 2.6, 0, 1) ** 1.6 * (0.35 + 0.65 * band)
wisps = np.clip((n2 - 0.5) * 3.0, 0, 1) ** 2.0 * band
warm = np.clip((n3 - 0.55) * 3.0, 0, 1) ** 1.5 * np.clip((x / W - 0.45) * 2.0, 0, 1)
canvas += cloud[..., None] * np.array([0.10, 0.16, 0.34], np.float32)
canvas += wisps[..., None] * np.array([0.22, 0.10, 0.30], np.float32)
canvas += warm[..., None] * np.array([0.26, 0.08, 0.06], np.float32)
# Dark dust lanes through the band.
dust = np.clip((fbm(W, H, octaves=6, base=6, seed=51) - 0.5) * 3.0, 0, 1) * band
canvas *= (1.0 - 0.55 * dust)[..., None]

# --- Stars ------------------------------------------------------------------------------------------
stars = np.zeros((H, W, 3), np.float32)
count = 9000
sx = rng.random(count) * W
sy = rng.random(count) * H
# Denser in the band.
keep = rng.random(count) < (0.35 + 0.65 * np.exp(-(((sy - (0.15 * H + 0.55 * sx * H / W)) / (0.28 * H)) ** 2)))
sx, sy = sx[keep], sy[keep]
bright = rng.random(sx.size) ** 3.2
tint = rng.random(sx.size)
for px, py, b, t in zip(sx, sy, bright, tint):
    col = np.array([1.0, 0.92, 0.82]) if t < 0.2 else np.array([0.80, 0.88, 1.0]) if t > 0.75 else np.array([0.95, 0.95, 0.97])
    ix, iy = int(px), int(py)
    stars[iy, ix] += col * (0.25 + 1.6 * b)
glow = blur(stars, 1.2) * 1.6 + blur(stars, 4.0) * 0.6
canvas += stars * 0.7 + glow
# A few big bright stars with spikes.
for _ in range(14):
    px, py = rng.random() * W, rng.random() * H * 0.8
    # Only within a window around the star: the spikes fade out within it.
    x0, x1 = max(0, int(px) - 120), min(W, int(px) + 121)
    y0, y1 = max(0, int(py) - 120), min(H, int(py) + 121)
    wy, wx = np.mgrid[y0:y1, x0:x1].astype(np.float32)
    r = np.sqrt((wx - px) ** 2 + (wy - py) ** 2)
    core = np.exp(-(r / 2.2) ** 2) * 2.0 + np.exp(-(r / 9.0) ** 2) * 0.35
    fade = np.clip(1.0 - r / 120.0, 0.0, 1.0)
    spike = (np.exp(-((wy - py) / 0.9) ** 2) * np.exp(-np.abs(wx - px) / 45.0) + np.exp(-((wx - px) / 0.9) ** 2) * np.exp(-np.abs(wy - py) / 45.0)) * 0.5 * fade
    canvas[y0:y1, x0:x1] += (core + spike)[..., None] * np.array([0.85, 0.92, 1.0], np.float32)

# --- The far sun (top right, just off the picture) -------------------------------------------------
SUN = (2050.0, -140.0)
r = radial(*SUN, 1.0)
canvas += (np.exp(-(r / 260.0) ** 2) * 1.4 + np.exp(-(r / 700.0) ** 2) * 0.35)[..., None] * np.array([1.0, 0.86, 0.62], np.float32)
light = np.array([0.62, -0.42, 0.66])
light /= np.linalg.norm(light)

# --- The planet (lower left) ---------------------------------------------------------------------------
PC = (260.0, 1520.0)
PR = 980.0
dx = (x - PC[0]) / PR
dy = (y - PC[1]) / PR
d2 = dx * dx + dy * dy
inside = d2 < 1.0
nz = np.sqrt(np.clip(1.0 - d2, 0.0, 1.0))
# Normal: x right, y up (image y down), z toward the viewer.
nxp, nyp = dx, -dy
diffuse = np.clip(nxp * light[0] + nyp * (-light[1]) + nz * light[2] * 0.35, 0.0, 1.0)
lat = np.arcsin(np.clip(-dy, -1, 1))
surface_noise = fbm(W, H, octaves=7, base=6, seed=77)
bands = 0.5 + 0.5 * np.sin(lat * 14.0 + surface_noise * 7.0)
dunes = fbm(W, H, octaves=6, base=18, seed=91)
sand_a = np.array([0.78, 0.52, 0.30], np.float32)
sand_b = np.array([0.55, 0.32, 0.18], np.float32)
rock = np.array([0.36, 0.24, 0.17], np.float32)
col = sand_a[None, None] * bands[..., None] + sand_b[None, None] * (1 - bands[..., None])
col = col * (0.75 + 0.5 * dunes[..., None])
col = np.where((surface_noise > 0.62)[..., None], col * 0.6 + rock * 0.4, col)
shade = diffuse ** 0.9 * (0.25 + 0.75 * nz ** 0.4)
planet = col * shade[..., None] * 1.25
# Night side: faint blue from the space around it.
planet += col * 0.025
canvas = np.where(inside[..., None], planet, canvas)
# The atmosphere: a lit rim, scattering into space.
edge = np.sqrt(d2)
rim = np.exp(-((edge - 1.0) / 0.012) ** 2)
lit_side = np.clip((nxp * light[0] + nyp * 0.42) * 1.4 + 0.25, 0.0, 1.0)
halo = np.exp(-np.clip(edge - 1.0, 0, None) / 0.035) * (edge >= 1.0)
atmo = (rim * 1.4 + halo * 0.55) * lit_side
canvas += atmo[..., None] * np.array([1.0, 0.72, 0.42], np.float32)
canvas += (np.exp(-np.clip(edge - 1.0, 0, None) / 0.09) * (edge >= 1.0) * lit_side * 0.16)[..., None] * np.array([0.45, 0.6, 1.0], np.float32)

# --- Shapes drawn supersampled: the capital ship, the fighters, the bolts -----------------------
BIG = (W * SS, H * SS)


def layer():
    return Image.new("RGBA", BIG, (0, 0, 0, 0))


def pts(seq):
    return [(px * SS, py * SS) for px, py in seq]


def composite(img, base):
    a = np.asarray(img.resize((W, H), Image.LANCZOS), np.float32) / 255.0
    alpha = a[..., 3:4]
    return base * (1.0 - alpha) + a[..., :3] * alpha, alpha[..., 0]


# The ship: a long wedge from its nose (left, lower) to its stern (right, upper), seen a little from below.
NOSE = np.array([1095.0, 640.0])
STERN_TOP = np.array([1830.0, 300.0])
STERN_LOW = np.array([1905.0, 455.0])
KEEL = np.array([1790.0, 520.0])

hull = layer()
d = ImageDraw.Draw(hull)
# The underside (in shadow), then the flank lit by the sun.
d.polygon(pts([NOSE, STERN_LOW, KEEL]), fill=(34, 38, 46, 255))
d.polygon(pts([NOSE, STERN_TOP, STERN_LOW]), fill=(92, 98, 110, 255))
# The superstructure: stepped blocks toward the stern, the command tower and its bridge.
for i, (a, b, hgt) in enumerate([(0.62, 0.80, 26), (0.70, 0.86, 44), (0.78, 0.90, 62)]):
    p0 = NOSE + (STERN_TOP - NOSE) * a
    p1 = NOSE + (STERN_TOP - NOSE) * b
    q0 = NOSE + (STERN_LOW - NOSE) * (a + 0.02)
    q1 = NOSE + (STERN_LOW - NOSE) * (b + 0.02)
    top = np.array([0.0, -hgt])
    shade = 108 + i * 10
    d.polygon(pts([p0, p1, p1 + top, p0 + top]), fill=(shade, shade + 4, shade + 14, 255))
    d.polygon(pts([p0 + top, p1 + top, q1 + top * 0.6, q0 + top * 0.6]), fill=(shade - 30, shade - 26, shade - 18, 255))
tower = NOSE + (STERN_TOP - NOSE) * 0.86 + np.array([0.0, -62.0])
d.polygon(pts([tower + (-14, 0), tower + (14, -6), tower + (12, -48), tower + (-12, -44)]), fill=(120, 126, 138, 255))
bridge = tower + np.array([0.0, -50.0])
d.polygon(pts([bridge + (-46, 4), bridge + (46, -4), bridge + (46, -14), bridge + (-46, -6)]), fill=(132, 138, 150, 255))
for s in (-1, 1):
    c = bridge + np.array([s * 36.0, -18.0 - s * 3.0])
    d.ellipse([(c[0] - 7) * SS, (c[1] - 7) * SS, (c[0] + 7) * SS, (c[1] + 7) * SS], fill=(150, 156, 168, 255))

ship_rgb, ship_alpha = composite(hull, np.zeros_like(canvas))
# Plating: noise and lines along the hull, light falling off toward the nose.
plate = fbm(W, H, octaves=5, base=40, seed=303)
along = (x - NOSE[0]) / (STERN_TOP[0] - NOSE[0])
ship_rgb *= (0.78 + 0.35 * plate)[..., None] * (0.7 + 0.45 * np.clip(along, 0, 1))[..., None]
lines = (np.sin((x * 0.42 - y * 0.88) * 0.9) > 0.97) | (np.sin((x * 0.9 + y * 0.42) * 0.55) > 0.985)
ship_rgb *= np.where(lines, 0.72, 1.0)[..., None]
canvas = canvas * (1.0 - ship_alpha[..., None]) + ship_rgb * ship_alpha[..., None]

# Hull lights: rows along the flank and the superstructure, warm and a few blue.
lights = np.zeros((H, W, 3), np.float32)
for row in range(7):
    t0 = 0.12 + row * 0.015
    for k in range(170):
        u = 0.08 + rng.random() * 0.9
        a = NOSE + (STERN_TOP - NOSE) * u
        b = NOSE + (STERN_LOW - NOSE) * u
        p = a + (b - a) * (t0 + rng.random() * 0.6)
        ix, iy = int(p[0]), int(p[1])
        if 0 <= ix < W and 0 <= iy < H and ship_alpha[iy, ix] > 0.5 and rng.random() < 0.55:
            lights[iy, ix] += (np.array([0.6, 0.8, 1.0]) if rng.random() < 0.12 else np.array([1.0, 0.86, 0.6])) * (0.6 + rng.random())
canvas += lights * 0.9 + blur(lights, 1.5) * 1.4
# The bridge's window band.
win = np.zeros((H, W), np.float32)
for k in range(60):
    p = bridge + np.array([-40.0 + k * 1.35, -8.0 - k * 0.12])
    win[int(p[1]), int(p[0])] = 1.0
canvas += (win * 1.2 + blur(win, 2.0) * 2.0)[..., None] * np.array([1.0, 0.9, 0.7], np.float32)
# The engines' blue glow out of the stern.
for (ex, ey, er) in [(1912.0, 360.0, 30.0), (1922.0, 420.0, 22.0), (1898.0, 312.0, 18.0)]:
    r = radial(ex, ey, 1.0)
    canvas += (np.exp(-(r / er) ** 2) * 1.6 + np.exp(-(r / (er * 3.2)) ** 2) * 0.45)[..., None] * np.array([0.5, 0.72, 1.0], np.float32)
# Sunlight catching the upper edge.
edge_mask = np.zeros((H, W), np.float32)
for k in range(1200):
    u = k / 1200.0
    p = NOSE + (STERN_TOP - NOSE) * u
    edge_mask[int(p[1]), int(p[0])] = 0.8 * u
canvas += blur(edge_mask, 1.2)[..., None] * np.array([1.0, 0.9, 0.75], np.float32) * 2.0

# Two small fighters (a ball and two upright wings each) turning in toward the ship, bolts.
fighters = layer()
d = ImageDraw.Draw(fighters)
for (fx, fy, s) in [(1290.0, 455.0, 1.0), (1355.0, 505.0, 0.8)]:
    def hexagon(cx, cy, rr):
        return [((cx + rr * 0.32 * math.cos(math.radians(90 + 60 * i))) * SS, (cy + rr * math.sin(math.radians(90 + 60 * i))) * SS) for i in range(6)]
    d.polygon(hexagon(fx - 11 * s, fy, 13 * s), fill=(48, 52, 60, 255))
    d.polygon(hexagon(fx + 11 * s, fy, 13 * s), fill=(48, 52, 60, 255))
    d.line([((fx - 10 * s) * SS, fy * SS), ((fx + 10 * s) * SS, fy * SS)], fill=(70, 74, 82, 255), width=int(3 * s * SS))
    d.ellipse([(fx - 4.5 * s) * SS, (fy - 4.5 * s) * SS, (fx + 4.5 * s) * SS, (fy + 4.5 * s) * SS], fill=(86, 90, 100, 255))
f_rgb, f_alpha = composite(fighters, np.zeros_like(canvas))
canvas = canvas * (1.0 - f_alpha[..., None]) + f_rgb * f_alpha[..., None]

bolts = np.zeros((H, W, 3), np.float32)
for (bx, by, ang, ln, colour) in [(1215.0, 425.0, -24.0, 46.0, (0.35, 1.0, 0.45)), (1180.0, 470.0, -22.0, 38.0, (0.35, 1.0, 0.45)),
                                  (1420.0, 560.0, 158.0, 40.0, (1.0, 0.3, 0.25)), (1470.0, 590.0, 160.0, 34.0, (1.0, 0.3, 0.25))]:
    for k in range(int(ln * 2)):
        t = k / (ln * 2)
        px = bx + math.cos(math.radians(ang)) * ln * t
        py = by + math.sin(math.radians(ang)) * ln * t
        bolts[int(py), int(px)] += np.array(colour, np.float32)
canvas += bolts * 1.4 + blur(bolts, 2.0) * 3.0 + blur(bolts, 6.0) * 1.2

# --- Grading: a little bloom, the vignette, fine grain --------------------------------------------
bright_part = np.clip(canvas - 0.75, 0, None)
canvas += blur(bright_part, 12.0) * 0.6
vig = radial(W * 0.5, H * 0.45, math.hypot(W, H) * 0.55)
canvas *= np.clip(1.08 - 0.55 * vig ** 2.2, 0.25, 1.0)[..., None]
canvas += (rng.random((H, W, 1)).astype(np.float32) - 0.5) * 0.012
# A filmic curve.
canvas = 1.0 - np.exp(-canvas * 1.35)
canvas = np.clip(canvas, 0.0, 1.0) ** (1 / 1.05)

out = sys.argv[1] if len(sys.argv) > 1 else "src/client/resources/assets/sabercraft/textures/gui/title/background.png"
# Drawn at 1920x1080, stored at 1600x900 to keep the mod small (the game scales it to the window, filtered).
Image.fromarray((canvas * 255.0 + 0.5).astype(np.uint8), "RGB").resize((1600, 900), Image.LANCZOS).save(out, optimize=True)
print("wrote", out)
