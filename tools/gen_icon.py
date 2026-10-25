"""Draws the SaberCraft mod icon (Phase 11): two crossed sabers, red and blue, glowing in front of a
planet and a starfield inside a rounded emblem. Everything is computed here, no outside artwork.

    python3 tools/gen_icon.py            -> src/main/resources/assets/sabercraft/icon.png (512 px)
"""
import math
import sys

import numpy as np
from PIL import Image, ImageFilter

SIZE = 512
SS = 2  # supersampling
N = SIZE * SS
OUT = sys.argv[1] if len(sys.argv) > 1 else "src/main/resources/assets/sabercraft/icon.png"

yy, xx = np.mgrid[0:N, 0:N].astype(np.float32) / N  # 0..1


def rounded_mask(margin, radius):
    """1 inside a rounded square, soft edge."""
    dx = np.maximum(np.abs(xx - 0.5) - (0.5 - margin - radius), 0)
    dy = np.maximum(np.abs(yy - 0.5) - (0.5 - margin - radius), 0)
    d = np.sqrt(dx * dx + dy * dy) - radius
    return np.clip(0.5 - d * N, 0, 1)


def segment_distance(ax, ay, bx, by):
    px, py = xx - ax, yy - ay
    vx, vy = bx - ax, by - ay
    t = np.clip((px * vx + py * vy) / (vx * vx + vy * vy), 0, 1)
    return np.sqrt((px - vx * t) ** 2 + (py - vy * t) ** 2), t


img = np.zeros((N, N, 3), np.float32)

# Space: deep blue to black, a nebula haze.
r = np.sqrt((xx - 0.35) ** 2 + (yy - 0.3) ** 2)
img[..., 0] = 0.02 + 0.10 * np.exp(-r * 4.0)
img[..., 1] = 0.02 + 0.04 * np.exp(-r * 5.0)
img[..., 2] = 0.06 + 0.20 * np.exp(-r * 3.0)
neb = np.exp(-((xx - 0.75) ** 2 * 18 + (yy - 0.25) ** 2 * 40))
img += neb[..., None] * np.array([0.18, 0.05, 0.22])

# Stars.
rng = np.random.default_rng(1977)
for _ in range(140):
    sx, sy = rng.random(), rng.random()
    b = rng.random() ** 3
    size = (0.0015 + 0.004 * b)
    d2 = (xx - sx) ** 2 + (yy - sy) ** 2
    star = np.exp(-d2 / (size * size)) * (0.4 + 0.6 * b)
    tint = np.array([0.9, 0.95, 1.0]) if rng.random() < 0.7 else np.array([1.0, 0.85, 0.7])
    img += star[..., None] * tint

# A planet low on the right, lit from the upper left, with a thin atmosphere rim.
pcx, pcy, pr = 0.78, 1.02, 0.42
pd = np.sqrt((xx - pcx) ** 2 + (yy - pcy) ** 2)
inside = np.clip((pr - pd) * N * 0.5, 0, 1)
nx, ny = (xx - pcx) / pr, (yy - pcy) / pr
nz = np.sqrt(np.clip(1 - nx * nx - ny * ny, 0, 1))
light = np.clip(-0.6 * nx - 0.6 * ny + 0.5 * nz, 0, 1)
bands = 0.5 + 0.5 * np.sin(ny * 22 + np.sin(nx * 6) * 1.5)
planet = np.stack([0.75 + 0.15 * bands, 0.55 + 0.1 * bands, 0.35 + 0.05 * bands], -1) * (0.08 + 0.9 * light[..., None])
img = img * (1 - inside[..., None]) + planet * inside[..., None]
rim = np.exp(-((pd - pr) * N / (8 * SS)) ** 2) * np.clip(-nx - ny + 0.3, 0, 1)
img += rim[..., None] * np.array([0.4, 0.6, 1.0]) * 0.8


def saber(ax, ay, bx, by, core, glow, hilt_len):
    """A blade from a (hilt end) to b (tip), with a metal hilt at the start."""
    global img
    length = math.hypot(bx - ax, by - ay)
    ux, uy = (bx - ax) / length, (by - ay) / length
    hx, hy = ax + ux * hilt_len, ay + uy * hilt_len
    d, t = segment_distance(hx, hy, bx, by)
    # Rounded tip: the blade gets thinner over the last few percent.
    taper = np.clip((1 - t) / 0.04, 0.4, 1)
    w = 0.011 * taper
    halo = np.exp(-(d / (w * 4.5)) ** 2) * 0.9 + np.exp(-(d / (w * 12)) ** 2) * 0.35
    img += halo[..., None] * np.array(glow)
    body = np.clip((w * 1.25 - d) * N * 0.6, 0, 1)
    img = img * (1 - body[..., None]) + body[..., None] * (np.array(glow) * 2.2 + 0.4)
    core_m = np.clip((w * 0.75 - d) * N * 0.6, 0, 1)
    img = img * (1 - core_m[..., None]) + core_m[..., None] * np.array(core) * 3.0
    # Hilt: dark grip with ridges, a bright emitter ring and a pommel.
    d, t = segment_distance(ax, ay, hx, hy)
    hw = 0.022
    hilt = np.clip((hw - d) * N * 0.6, 0, 1)
    across = ((xx - ax) * -uy + (yy - ay) * ux) / hw  # -1..1 across the hilt
    shade = 0.55 + 0.45 * np.cos(across * 1.4)
    ridges = (np.sin(t * 60) > 0.2) & (t > 0.35) & (t < 0.8)
    metal = np.where(ridges[..., None], np.array([0.12, 0.12, 0.14]), np.array([0.72, 0.74, 0.78]))
    metal = np.where((t > 0.9)[..., None], np.array([0.88, 0.88, 0.9]), metal)
    metal = np.where((t < 0.1)[..., None], np.array([0.3, 0.3, 0.34]), metal)
    switch = (t > 0.18) & (t < 0.28) & (across < -0.2)
    metal = np.where(switch[..., None], np.array(glow) * 1.2, metal)
    metal = metal * shade[..., None]
    img = img * (1 - hilt[..., None]) + metal * hilt[..., None]


# Crossed sabers: red from the lower left up to the right, blue from the lower right up to the left.
saber(0.25, 0.83, 0.82, 0.17, [1.0, 0.97, 0.95], [1.0, 0.12, 0.08], 0.17)
saber(0.75, 0.83, 0.18, 0.17, [0.95, 0.98, 1.0], [0.15, 0.45, 1.0], 0.17)

# Spark where the blades cross.
cx, cy = 0.5, 0.535
d = np.sqrt((xx - cx) ** 2 + (yy - cy) ** 2)
img += (np.exp(-(d / 0.03) ** 2) * 1.2 + np.exp(-(d / 0.09) ** 2) * 0.35)[..., None] * np.array([1.0, 0.9, 0.85])
for k in range(6):
    a = k * math.pi / 3 + 0.3
    ex, ey = cx + math.cos(a) * 0.12, cy + math.sin(a) * 0.12
    sd, st = segment_distance(cx, cy, ex, ey)
    img += (np.exp(-(sd / 0.004) ** 2) * (1 - st) ** 2)[..., None] * np.array([1.0, 0.85, 0.6])

# Tone map, emblem frame.
img = 1 - np.exp(-img * 1.6)
mask = rounded_mask(0.02, 0.16)
frame_outer = rounded_mask(0.02, 0.16)
frame_inner = rounded_mask(0.045, 0.135)
frame = np.clip(frame_outer - frame_inner, 0, 1)
gy = 1 - yy
frame_col = np.stack([0.55 + 0.3 * gy, 0.58 + 0.3 * gy, 0.65 + 0.3 * gy], -1)
img = img * (1 - frame[..., None]) + frame_col * frame[..., None]
edge = np.clip(frame_inner - rounded_mask(0.05, 0.13), 0, 1)
img = img * (1 - edge[..., None] * 0.7)

rgba = np.dstack([np.clip(img, 0, 1), mask])
out = Image.fromarray((rgba * 255).astype(np.uint8), "RGBA").resize((SIZE, SIZE), Image.LANCZOS)
out.save(OUT, optimize=True)
print("wrote", OUT, out.size)
