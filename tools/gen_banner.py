"""Draws the README banner docs/media/sabercraft-banner.png (1600x500) and the GitHub social preview
docs/media/sabercraft-social-preview.png (1280x640, the size GitHub asks for).

Both show the mod icon, the title and a grid of real in-game screenshots from docs/media/screenshots/
(taken by MediaGameTest, see docs/media/README.md): the lightsabers, four of the starships and the story.
The screenshots are only cropped, scaled and labelled; nothing in them is painted or generated. The text
uses DejaVu Sans, which ships with most Linux systems. Run it with ``python3 tools/gen_banner.py``
(needs numpy and Pillow).
"""

import os
import random

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SHOTS = os.path.join(ROOT, "docs", "media", "screenshots")
ICON = os.path.join(ROOT, "src", "main", "resources", "assets", "sabercraft", "icon.png")
BANNER = os.path.join(ROOT, "docs", "media", "sabercraft-banner.png")
SOCIAL = os.path.join(ROOT, "docs", "media", "sabercraft-social-preview.png")
FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"

BG_TOP, BG_BOTTOM = (8, 10, 26), (16, 10, 34)
TEXT, MUTED, FAINT = (240, 244, 255), (196, 206, 232), (150, 160, 192)
BLUE, RED, GREEN, PURPLE, YELLOW = (70, 150, 255), (255, 70, 70), (70, 225, 110), (185, 100, 255), (255, 205, 70)

# Each tile: screenshot, label, accent color, the point of the picture to keep in view (0..1) and how
# far to zoom in on it (1 = the whole height of the picture).
TILES = (
    ("hero-saber.jpg", "Lightsabers", RED, 0.47, 0.52, 1.15),
    ("ship-xwing.jpg", "X-Wing", BLUE, 0.47, 0.60, 1.2),
    ("ship-tie-interceptor.jpg", "TIE Interceptor", RED, 0.47, 0.56, 1.8),
    ("story-duel.jpg", "Story: Nightshade", PURPLE, 0.46, 0.46, 1.0),
    ("ship-falcon.jpg", "Millennium Falcon", YELLOW, 0.47, 0.52, 1.35),
    ("ship-star-destroyer.jpg", "Star Destroyer", GREEN, 0.45, 0.52, 1.0),
)
# Parts of a screenshot that never go into a tile, as (left, top, right, bottom) in 0..1: the black
# bars and the subtitle of a cutscene.
SOURCE_BOX = {"story-duel.jpg": (0.0, 0.12, 1.0, 0.90)}
CHIPS = ("15 fighting styles", "5 starships", "6 story missions")


def font(size, bold=False):
    return ImageFont.truetype(FONT_BOLD if bold else FONT, size)


def background(size, stars, keep_clear):
    """Dark space gradient with a few faint stars, none inside keep_clear (the text)."""
    w, h = size
    t = np.linspace(0, 1, h, dtype=np.float32)[:, None, None]
    grad = np.array(BG_TOP, np.float32) * (1 - t) + np.array(BG_BOTTOM, np.float32) * t
    img = Image.fromarray(np.repeat(grad, w, axis=1).astype(np.uint8), "RGB").convert("RGBA")
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    rng = random.Random(7)
    for _ in range(stars):
        x, y = rng.uniform(0, w), rng.uniform(0, h)
        r = rng.choice((0.6, 0.8, 1.0, 1.4))
        a = rng.randint(50, 150)
        if keep_clear[0] <= x <= keep_clear[2] and keep_clear[1] <= y <= keep_clear[3]:
            continue
        draw.ellipse([x - r, y - r, x + r, y + r], fill=(220, 230, 255, a))
    # A soft blue glow behind the title.
    glow = Image.new("RGBA", size, (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse([-w * 0.15, -h * 0.3, w * 0.45, h * 1.1], fill=(40, 70, 160, 60))
    img = Image.alpha_composite(img, glow.filter(ImageFilter.GaussianBlur(min(w, h) // 4)))
    return Image.alpha_composite(img, layer)


def cover(name, size, fx, fy, zoom=1.0):
    """The screenshot scaled to fill size, cropped around the point (fx, fy), zoomed in by zoom."""
    img = Image.open(os.path.join(SHOTS, name)).convert("RGB")
    box = SOURCE_BOX.get(name)
    if box:
        img = img.crop((round(box[0] * img.width), round(box[1] * img.height),
                        round(box[2] * img.width), round(box[3] * img.height)))
    w, h = size
    scale = max(w / img.width, h / img.height) * zoom
    img = img.resize((round(img.width * scale), round(img.height * scale)), Image.LANCZOS)
    x = min(max(round(fx * img.width - w / 2), 0), img.width - w)
    y = min(max(round(fy * img.height - h / 2), 0), img.height - h)
    return img.crop((x, y, x + w, y + h))


def tile(name, label, color, fx, fy, zoom, size, label_size, radius):
    """One screenshot with rounded corners, a dark foot and its label behind an accent bar."""
    w, h = size
    img = cover(name, size, fx, fy, zoom).convert("RGBA")
    foot = int(h * 0.42)
    t = np.clip((np.arange(h, dtype=np.float32) - (h - foot)) / foot, 0, 1)
    alpha = (t ** 1.6 * 215).astype(np.uint8)
    shade = Image.new("RGBA", size, (4, 6, 16, 255))
    shade.putalpha(Image.fromarray(np.tile(alpha[:, None], (1, w)), "L"))
    img = Image.alpha_composite(img, shade)
    draw = ImageDraw.Draw(img)
    pad = max(10, label_size * 2 // 3)
    bar_h = label_size + 4
    y = h - pad - bar_h
    draw.rounded_rectangle([pad, y, pad + 4, y + bar_h], 2, fill=color + (255,))
    draw.text((pad + 12, y + bar_h / 2), label, font=font(label_size, True), fill=TEXT + (255,), anchor="lm")
    mask = Image.new("L", size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, w - 1, h - 1], radius, fill=255)
    out = Image.new("RGBA", size, (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    ImageDraw.Draw(out).rounded_rectangle([0, 0, w - 1, h - 1], radius, outline=(255, 255, 255, 46), width=1)
    return out


def grid(canvas, box, cols, rows, gap, label_size, radius):
    x0, y0, x1, y1 = box
    tw = (x1 - x0 - gap * (cols - 1)) // cols
    th = (y1 - y0 - gap * (rows - 1)) // rows
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    sd = ImageDraw.Draw(shadow)
    for i in range(cols * rows):
        x, y = x0 + (i % cols) * (tw + gap), y0 + (i // cols) * (th + gap)
        sd.rounded_rectangle([x + 4, y + 6, x + tw + 4, y + th + 6], radius, fill=(0, 0, 0, 150))
    canvas = Image.alpha_composite(canvas, shadow.filter(ImageFilter.GaussianBlur(8)))
    for i, (name, label, color, fx, fy, zoom) in enumerate(TILES[:cols * rows]):
        x, y = x0 + (i % cols) * (tw + gap), y0 + (i // cols) * (th + gap)
        canvas.alpha_composite(tile(name, label, color, fx, fy, zoom, (tw, th), label_size, radius), (x, y))
    return canvas


def icon_at(img, size, pos):
    icon = Image.open(ICON).convert("RGBA").resize((size, size), Image.LANCZOS)
    shadow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    r = size * 2 // 11
    ImageDraw.Draw(shadow).rounded_rectangle([pos[0] + 5, pos[1] + 8, pos[0] + size + 5, pos[1] + size + 8], r,
                                             fill=(0, 0, 0, 170))
    img = Image.alpha_composite(img, shadow.filter(ImageFilter.GaussianBlur(10)))
    img.alpha_composite(icon, pos)
    return img


def title(img, pos, size, anchor="lm"):
    f = font(size, True)
    glow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    ImageDraw.Draw(glow).text(pos, "SaberCraft", font=f, fill=(120, 175, 255, 170), anchor=anchor)
    img = Image.alpha_composite(img, glow.filter(ImageFilter.GaussianBlur(size // 8)))
    ImageDraw.Draw(img).text(pos, "SaberCraft", font=f, fill=TEXT + (255,), anchor=anchor)
    return img


def text(img, pos, line, size, fill, bold=False, anchor="la"):
    ImageDraw.Draw(img).text(pos, line, font=font(size, bold), fill=fill + (255,), anchor=anchor)
    return img


def chips(img, pos, size, gap):
    """Small rounded labels with the key numbers."""
    layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    f = font(size)
    x, y = pos
    h = size + size * 9 // 10
    for i, label in enumerate(CHIPS):
        w = draw.textlength(label, font=f) + size * 1.4
        color = (BLUE, RED, PURPLE)[i % 3]
        draw.rounded_rectangle([x, y, x + w, y + h], h // 2, fill=color + (40,), outline=color + (190,), width=2)
        draw.text((x + w / 2, y + h / 2), label, font=f, fill=TEXT + (255,), anchor="mm")
        x += w + gap
    return Image.alpha_composite(img, layer)


def banner():
    w, h = 1600, 500
    img = background((w, h), 140, (40, 60, 800, 480))
    img = grid(img, (812, 28, w - 28, h - 28), 3, 2, 10, 17, 12)
    img = icon_at(img, 124, (64, 82))
    img = title(img, (210, 146), 86)
    img = text(img, (68, 248), "Lightsabers, starships and a story campaign", 30, TEXT)
    img = text(img, (68, 292), "for Minecraft Java 26.2 · Fabric", 26, MUTED)
    img = chips(img, (68, 356), 18, 12)
    img = text(img, (68, 444), "Unofficial fan project · in-game screenshots", 17, FAINT)
    return img.convert("RGB")


def social():
    w, h = 1280, 640
    img = background((w, h), 120, (30, 70, 650, 625))
    img = grid(img, (668, 32, w - 32, h - 32), 2, 3, 10, 16, 12)
    img = icon_at(img, 120, (56, 90))
    img = title(img, (52, 296), 86)
    img = text(img, (58, 372), "Lightsabers, starships and", 30, TEXT)
    img = text(img, (58, 412), "a story campaign", 30, TEXT)
    img = text(img, (58, 462), "Minecraft Java 26.2 · Fabric", 24, MUTED)
    img = chips(img, (58, 516), 16, 10)
    img = text(img, (58, 596), "Unofficial fan project · in-game screenshots", 16, FAINT)
    return img.convert("RGB")


def main():
    os.makedirs(os.path.dirname(BANNER), exist_ok=True)
    banner().save(BANNER, optimize=True)
    print("wrote", BANNER, os.path.getsize(BANNER) // 1024, "KB")
    social().save(SOCIAL, optimize=True)
    print("wrote", SOCIAL, os.path.getsize(SOCIAL) // 1024, "KB")


if __name__ == "__main__":
    main()
