"""Draws the README banner docs/media/banner.png.

Everything is computed here: the star field, the nebula, the two glowing blades and the text. The
only input is the mod icon (made by tools/gen_icon.py). The text uses DejaVu Sans, which ships with
most Linux systems. Run it with ``python3 tools/gen_banner.py`` (needs numpy and Pillow).
"""

import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ICON = os.path.join(ROOT, "src", "main", "resources", "assets", "sabercraft", "icon.png")
OUT = os.path.join(ROOT, "docs", "media", "banner.png")
FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"

W, H = 1280, 360


def background(rng):
    y, x = np.mgrid[0:H, 0:W].astype(np.float32)
    # Deep blue to violet, like the icon.
    t = x / W
    base = np.stack([
        8 + 30 * t,
        10 + 6 * t,
        28 + 30 * t,
    ], axis=-1)

    # Two soft nebula clouds.
    for cx, cy, r, color in ((980, 120, 260, (120, 40, 110)), (300, 300, 300, (30, 60, 140))):
        d = np.sqrt((x - cx) ** 2 + (y - cy) ** 2) / r
        glow = np.clip(1 - d, 0, 1) ** 2
        base += glow[..., None] * np.array(color, dtype=np.float32) * 0.55

    img = np.clip(base, 0, 255)

    # Stars of different brightness.
    for _ in range(420):
        sx, sy = rng.integers(0, W), rng.integers(0, H)
        b = rng.uniform(80, 255)
        img[sy, sx] = np.maximum(img[sy, sx], b)
        if b > 220 and 0 < sx < W - 1 and 0 < sy < H - 1:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                img[sy + dy, sx + dx] = np.maximum(img[sy + dy, sx + dx], b * 0.5)

    return Image.fromarray(img.astype(np.uint8), "RGB")


def blade(size, start, end, color, width):
    """A glowing blade: wide colored halo, narrower bloom, white core."""
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    for blur, w, alpha, c in ((26, width * 7, 120, color), (8, width * 3, 200, color), (2, width, 255, (255, 255, 255))):
        part = Image.new("RGBA", size, (0, 0, 0, 0))
        ImageDraw.Draw(part).line([start, end], fill=c + (alpha,), width=w)
        part = part.filter(ImageFilter.GaussianBlur(blur))
        layer = Image.alpha_composite(layer, part)
    # Round tip and emitter end of the white core.
    d = ImageDraw.Draw(layer)
    r = width // 2
    for px, py in (start, end):
        d.ellipse([px - r, py - r, px + r, py + r], fill=(255, 255, 255, 255))
    return layer


def main():
    rng = np.random.default_rng(1977)
    img = background(rng).convert("RGBA")

    # Blades crossing behind the right part of the banner.
    img = Image.alpha_composite(img, blade(img.size, (1030, 290), (1250, 50), (60, 140, 255), 8))
    img = Image.alpha_composite(img, blade(img.size, (1250, 290), (1030, 50), (255, 50, 50), 8))
    d = ImageDraw.Draw(img)
    for x0, x1 in ((1030, 1008), (1250, 1272)):
        # Simple hilts below the emitters.
        d.line([(x0, 290), (x1, 314)], fill=(70, 74, 84, 255), width=16)
        d.line([(x0, 290), (x1, 314)], fill=(150, 156, 168, 255), width=6)

    icon = Image.open(ICON).convert("RGBA").resize((220, 220), Image.LANCZOS)
    shadow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle([66, 76, 66 + 220, 76 + 220], 40, fill=(0, 0, 0, 160))
    img = Image.alpha_composite(img, shadow.filter(ImageFilter.GaussianBlur(12)))
    img.alpha_composite(icon, (60, 70))

    d = ImageDraw.Draw(img)
    title = ImageFont.truetype(FONT_BOLD, 96)
    sub = ImageFont.truetype(FONT, 30)
    small = ImageFont.truetype(FONT, 22)

    # Title with a faint glow.
    glow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(glow).text((320, 78), "SaberCraft", font=title, fill=(140, 190, 255, 200))
    img = Image.alpha_composite(img, glow.filter(ImageFilter.GaussianBlur(10)))
    d = ImageDraw.Draw(img)
    d.text((320, 78), "SaberCraft", font=title, fill=(245, 248, 255, 255))
    d.text((324, 200), "Lightsabers, blasters, droids and an X-Wing", font=sub, fill=(215, 225, 245, 255))
    d.text((324, 242), "for Minecraft Java 26.2 · Fabric", font=sub, fill=(215, 225, 245, 255))
    d.text((324, 296), "Unofficial fan project · not affiliated with Lucasfilm or Disney", font=small,
           fill=(160, 170, 200, 255))

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.convert("RGB").save(OUT, optimize=True)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
