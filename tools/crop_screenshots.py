"""Turns the PNGs of MediaGameTest into the README screenshots in docs/media/screenshots/.

Usage: python3 tools/crop_screenshots.py <media_dir>   (the folder SABERCRAFT_MEDIA pointed to)

Each entry: README file -> (MediaGameTest picture, crop box (left, top, right, bottom) in the 1920x1080
picture, or None for the whole picture). The crops mostly cut away the flat ground of the test world.
Pictures are saved as JPG, at most 1600 pixels wide. Needs Pillow.

Scenes move a little from run to run (droids idle, ships drift), so after a new run look at the results
and adjust a box where needed.
"""

import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "docs", "media", "screenshots")

WIDE = (240, 150, 1680, 960)
SKY = (240, 120, 1680, 930)
MENU = (120, 60, 1800, 1005)
HERO = (240, 170, 1680, 980)

PICKS = {
    "hero-saber": ("hero_vader.png", HERO),
    "ships-fleet": ("fleet_xwing_close.png", None),
    "story-duel": ("story_duel_half.png", None),
    "interior-falcon": ("interior_falcon_cockpit.png", None),
    "sabers-colors": ("blades_naboo.png", (220, 60, 1580, 825)),
    "combat-swing": ("combat_swing_1.png", (320, 240, 1600, 960)),
    "story-briefing": ("story_briefing.png", None),
    "story-disguise": ("story_disguise_hangar.png", None),
    "story-reactor": ("story_reactor.png", None),
    "story-shadowblade": ("story_duel_film.png", None),
    "story-finale": ("story_finale_0.png", None),
    "story-escape": ("story_escape_150.png", None),
    "ship-xwing": ("flight_xwing_turn.png", SKY),
    "ship-tie-interceptor": ("flight_tie_interceptor_fire.png", SKY),
    "ship-tie-fighter": ("flight_tie_fighter.png", SKY),
    "ship-falcon": ("flight_falcon.png", SKY),
    "ship-star-destroyer": ("destroyer_quarter.png", (140, 100, 1580, 910)),
    "interior-destroyer-bridge": ("interior_destroyer_bridge.png", None),
    "droids": ("droids_high.png", (380, 430, 1500, 1060)),
    "blaster": ("menu_blaster.png", MENU),
    "character-kenobi": ("hero_kenobi.png", WIDE),
    "menu-saber": ("menu_saber.png", MENU),
    "planet-tatooine": ("planet_tatooine_evening.png", WIDE),
    "gallery-falcon": ("fleet_falcon_close.png", None),
    "gallery-falcon-cockpit": ("interior_falcon_cockpit_seats.png", None),
    "gallery-reactor": ("interior_destroyer_reactor.png", None),
    "gallery-hangar-menu": ("menu_ships.png", MENU),
    "gallery-story-menu": ("menu_story.png", MENU),
    "gallery-vader": ("hero_vader_guard.png", HERO),
}
# The collage of the other skies: three across, two down.
PLANETS = ["planet_%s_evening.png" % p for p in ("hoth", "endor", "mustafar", "naboo", "coruscant")] + ["space_sky_night.png"]


def save(img, path, max_w=1600):
    if img.width > max_w:
        img = img.resize((max_w, round(img.height * max_w / img.width)), Image.LANCZOS)
    img.convert("RGB").save(path, "JPEG", quality=86, optimize=True, progressive=True)


def collage(paths, box, cols, out_w=1920, gap=6):
    tiles = [Image.open(p).convert("RGB").crop(box) for p in paths]
    rows = (len(tiles) + cols - 1) // cols
    tw = (out_w - gap * (cols - 1)) // cols
    th = round(tw * tiles[0].height / tiles[0].width)
    sheet = Image.new("RGB", (tw * cols + gap * (cols - 1), th * rows + gap * (rows - 1)), (8, 10, 20))
    for i, t in enumerate(tiles):
        sheet.paste(t.resize((tw, th), Image.LANCZOS), ((i % cols) * (tw + gap), (i // cols) * (th + gap)))
    return sheet


def main(media):
    os.makedirs(OUT, exist_ok=True)
    for target, (source, box) in PICKS.items():
        path = os.path.join(media, source)
        if not os.path.exists(path):
            print("missing", source, "for", target)
            continue
        img = Image.open(path).convert("RGB")
        if source.startswith("story_duel"):
            # The skip hint sits on the black bar of a cutscene: black it out, nothing else.
            img.paste((0, 0, 0), (1540, 30, 1910, 100))
        if box:
            img = img.crop(box)
        save(img, os.path.join(OUT, target + ".jpg"))
        print("wrote", target)
    planets = [os.path.join(media, p) for p in PLANETS]
    if all(os.path.exists(p) for p in planets):
        save(collage(planets, WIDE, 3), os.path.join(OUT, "planets.jpg"), 1920)
        print("wrote planets")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__)
    main(sys.argv[1])
