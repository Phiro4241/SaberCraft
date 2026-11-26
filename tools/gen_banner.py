"""Draws the README banner docs/media/banner.png (1280x400) and the GitHub social preview
docs/media/social-preview.png (1280x640, the size GitHub asks for).

Both are put together from real in-game screenshots in docs/media/screenshots/ (taken by
MediaGameTest, see docs/media/README.md), the mod icon (tools/gen_icon.py) and the title. The
screenshots sit in slanted panels, separated by thin glowing blade lines; nothing in the pictures is
painted or generated, only cropped, darkened and arranged. The text uses DejaVu Sans, which ships with
most Linux systems. Run it with ``python3 tools/gen_banner.py`` (needs numpy and Pillow).
"""

import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SHOTS = os.path.join(ROOT, "docs", "media", "screenshots")
ICON = os.path.join(ROOT, "src", "main", "resources", "assets", "sabercraft", "icon.png")
OUT = os.path.join(ROOT, "docs", "media", "banner.png")
SOCIAL = os.path.join(ROOT, "docs", "media", "social-preview.png")
FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"

W = 1280
BLUE, RED, GREEN, PURPLE = (60, 140, 255), (255, 50, 50), (60, 230, 90), (190, 90, 255)

# Each panel: the screenshot and the point of it to keep in view (0..1 across, 0..1 down).
BANNER_PANELS = (
    ("hero-saber.jpg", 0.50, 0.45),
    ("ships-fleet.jpg", 0.48, 0.58),
    ("story-duel.jpg", 0.42, 0.50),
)
SOCIAL_PANELS = BANNER_PANELS
# Parts of a screenshot that never go into a panel, as (left, top, right, bottom) in 0..1: the black
# bars and the subtitle of a cutscene.
SOURCE_BOX = {"story-duel.jpg": (0.0, 0.12, 1.0, 0.90)}


def cover(path, size, fx, fy):
    """The screenshot scaled to fill size, cropped around the point (fx, fy)."""
    img = Image.open(path).convert("RGB")
    box = SOURCE_BOX.get(os.path.basename(path))
    if box:
        img = img.crop((round(box[0] * img.width), round(box[1] * img.height),
                        round(box[2] * img.width), round(box[3] * img.height)))
    w, h = size
    scale = max(w / img.width, h / img.height)
    img = img.resize((round(img.width * scale), round(img.height * scale)), Image.LANCZOS)
    x = min(max(round(fx * img.width - w / 2), 0), img.width - w)
    y = min(max(round(fy * img.height - h / 2), 0), img.height - h)
    return img.crop((x, y, x + w, y + h))


def blade(size, start, end, color, width):
    """A glowing blade line: wide colored halo, narrower bloom, white core."""
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    for blur, w, alpha, c in ((14, width * 6, 110, color), (5, width * 3, 190, color), (1, width, 255, (255, 255, 255))):
        part = Image.new("RGBA", size, (0, 0, 0, 0))
        ImageDraw.Draw(part).line([start, end], fill=c + (alpha,), width=w)
        layer = Image.alpha_composite(layer, part.filter(ImageFilter.GaussianBlur(blur)))
    return layer


def panels(size, shots, x0, slant, colors):
    """Slanted panels from x0 to the right edge, each filled with one screenshot."""
    w, h = size
    canvas = Image.new("RGBA", size, (6, 8, 20, 255))
    n = len(shots)
    step = (w - x0 + slant) / n
    edges = [x0 + i * step for i in range(n + 1)]
    for i, (name, fx, fy) in enumerate(shots):
        left, right = edges[i], edges[i + 1]
        # The panel's box: from the bottom of its left edge to the top of its right edge.
        bx0 = int(left - slant)
        box_w = int(right + 1) - bx0
        shot = cover(os.path.join(SHOTS, name), (box_w, h), fx, fy).convert("RGBA")
        mask = Image.new("L", (box_w, h), 0)
        # Both edges lean the same way: (x, 0) at the top, (x - slant, h) at the bottom.
        ImageDraw.Draw(mask).polygon(
            [(left - bx0, 0), (right - bx0, 0), (right - slant - bx0, h), (left - slant - bx0, h)], fill=255)
        canvas.paste(shot, (bx0, 0), mask)
    for i in range(1, n):
        x = edges[i]
        canvas = Image.alpha_composite(canvas, blade(size, (x, -4), (x - slant, h + 4), colors[(i - 1) % len(colors)], 3))
    return canvas, edges


def shade_left(img, x_solid, x_clear, strength=1.0):
    """Darkens the left part of img so text stays readable: solid until x_solid, clear from x_clear."""
    w, h = img.size
    x = np.arange(w, dtype=np.float32)
    t = np.clip((x_clear - x) / max(x_clear - x_solid, 1), 0, 1)
    alpha = (t ** 1.4 * 235 * strength).astype(np.uint8)
    mask = Image.fromarray(np.tile(alpha, (h, 1)), "L")
    dark = Image.new("RGBA", img.size, (6, 8, 20, 255))
    dark.putalpha(mask)
    return Image.alpha_composite(img, dark)


def shade_band(img, y0, y1, fade, strength=0.82):
    """A dark horizontal band from y0 to y1, fading out over fade pixels above and below."""
    w, h = img.size
    y = np.arange(h, dtype=np.float32)
    t = np.clip(np.minimum((y - (y0 - fade)) / fade, ((y1 + fade) - y) / fade), 0, 1)
    alpha = (t * 255 * strength).astype(np.uint8)
    mask = Image.fromarray(np.tile(alpha[:, None], (1, w)), "L")
    dark = Image.new("RGBA", img.size, (6, 8, 20, 255))
    dark.putalpha(mask)
    return Image.alpha_composite(img, dark)


def vignette(img, strength=0.45):
    w, h = img.size
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    d = np.sqrt(((x - w / 2) / (w / 2)) ** 2 + ((y - h / 2) / (h / 2)) ** 2)
    alpha = (np.clip(d - 0.6, 0, 1) ** 1.5 * 255 * strength).astype(np.uint8)
    dark = Image.new("RGBA", img.size, (0, 0, 0, 255))
    dark.putalpha(Image.fromarray(alpha, "L"))
    return Image.alpha_composite(img, dark)


def icon_at(img, size, pos):
    icon = Image.open(ICON).convert("RGBA").resize((size, size), Image.LANCZOS)
    shadow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    r = size * 2 // 11
    ImageDraw.Draw(shadow).rounded_rectangle([pos[0] + 6, pos[1] + 8, pos[0] + size + 6, pos[1] + size + 8], r, fill=(0, 0, 0, 170))
    img = Image.alpha_composite(img, shadow.filter(ImageFilter.GaussianBlur(10)))
    img.alpha_composite(icon, pos)
    return img


def title(img, pos, size):
    font = ImageFont.truetype(FONT_BOLD, size)
    glow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(glow).text(pos, "SaberCraft", font=font, fill=(140, 190, 255, 190))
    img = Image.alpha_composite(img, glow.filter(ImageFilter.GaussianBlur(10)))
    ImageDraw.Draw(img).text(pos, "SaberCraft", font=font, fill=(245, 248, 255, 255))
    return img


def text(img, pos, line, size, fill=(220, 228, 245, 255), anchor="la"):
    ImageDraw.Draw(img).text(pos, line, font=ImageFont.truetype(FONT, size), fill=fill, anchor=anchor)
    return img


def banner():
    h = 400
    img, _ = panels((W, h), BANNER_PANELS, 700, 70, (BLUE, RED))
    img = shade_left(img, 640, 840)
    img = vignette(img, 0.35)
    img = icon_at(img, 120, (50, 56))
    img = title(img, (192, 62), 80)
    img = text(img, (54, 206), "Lightsabers, starships and a story campaign", 26)
    img = text(img, (54, 244), "for Minecraft Java 26.2 · Fabric", 26)
    img = text(img, (54, 334), "Unofficial fan project · in-game screenshots", 18, (165, 175, 205, 255))
    return img.convert("RGB")


def social():
    h = 640
    img, _ = panels((W, h), SOCIAL_PANELS, 0, 110, (BLUE, RED))
    img = vignette(img, 0.5)
    img = shade_band(img, 236, 446, 90)
    icon = 150
    font = ImageFont.truetype(FONT_BOLD, 104)
    tw = ImageDraw.Draw(img).textlength("SaberCraft", font=font)
    x = round((W - (icon + 34 + tw)) / 2)
    img = icon_at(img, icon, (x, 252))
    img = title(img, (x + icon + 34, 250), 104)
    img = text(img, (W // 2, 410), "Lightsabers · starships · a story campaign · Minecraft 26.2 (Fabric)", 30,
               anchor="mm")
    img = text(img, (W // 2, 604), "Unofficial fan project · in-game screenshots", 20, (190, 198, 222, 255), anchor="mm")
    return img.convert("RGB")


def main():
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    banner().save(OUT, optimize=True)
    print("wrote", OUT)
    social().save(SOCIAL, optimize=True)
    print("wrote", SOCIAL)


if __name__ == "__main__":
    main()
