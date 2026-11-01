"""Builds the starship models of SaberCraft: the X-Wing (Phase 11), the TIE Fighter and the TIE
Interceptor (Phase 12).

Same tools as the droids (gen_droids.py, gen_blasters.py): boxes and round parts built in code,
painted with generated textures; nothing is taken from films or games. A ship is far bigger than
a droid, so it is built at half size: one model unit is 1/8 block, and the game draws the parts
twice as large. The wings are parts of their own that turn around the fuselage's long axis, so
the S-foils open into the X when flying and close when landed.

Ship space: 1/8 block, the ground under the middle at (0, 0, 0), y up, the nose looking to -z.

    python3 tools/gen_ships.py            -> models, textures, item definitions, ShipParts.java
    python3 tools/gen_ships.py --preview  -> also the whole ship for tools/preview_model.py in /tmp
"""
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import gen_blasters as gb  # noqa: E402
import gen_droids as gd  # noqa: E402

JAVA = os.path.join("src", "client", "java", "com", "phiro", "sabercraft", "client", "ship", "ShipParts.java")

gb.MATERIALS.update({
    "hull": ((214, 214, 208), "polymer", {}),
    "hull_grey": ((150, 152, 156), "metal", {"brush": 0.05}),
    "red_mark": ((178, 44, 38), "polymer", {}),
    "canopy": ((26, 32, 40), "lens", {}),
    "engine_glow": ((255, 120, 70), "glow", {}),
    "cannon_tip": ((255, 90, 80), "glow", {}),
})
gb.EMISSIVE.update({"engine_glow": 14, "cannon_tip": 9})

# Phase 12: the Imperial ships. Blue-grey hull, black solar panels with a fine grid, green guns.
gb.MATERIALS.update({
    "tie_hull": ((118, 124, 134), "metal", {"brush": 0.04}),
    "tie_dark": ((70, 74, 82), "metal", {"brush": 0.04}),
    "solar": ((24, 27, 34), "flat", {}),
    "tie_window": ((20, 26, 30), "lens", {}),
    "tie_engine": ((150, 200, 255), "glow", {}),
    "tie_gun_tip": ((90, 255, 120), "glow", {}),
})
gb.EMISSIVE.update({"tie_engine": 12, "tie_gun_tip": 9})
_decorate = gb.decorate


def decorate(img, deco, w, h, along_u, rng, base):
    """Adds the solar panel grid of the TIE wings to the details of gen_blasters.py."""
    name = deco if isinstance(deco, str) else deco[0]
    if name != "solar_grid":
        return _decorate(img, deco, w, h, along_u, rng, base)
    for y in range(h):
        for x in range(w):
            if x % 6 == 0 or y % 6 == 0:
                img[y, x] = (58, 62, 72)
            elif (x + y) % 11 == 0:
                img[y, x] = img[y, x] * 1.25
    return img


gb.decorate = decorate

# The middle of the fuselage above the ground, in units.
MID = 9.0


def xwing():
    d = gd.Droid("xwing", 1977, 18, 44, folder="ship")
    d.part("body", (0, MID, 0))
    # Nose: tapering towards the tip, a red band behind it.
    segments = [(-24.0, -20.0, 1.4, 1.2), (-20.0, -15.0, 2.0, 1.7), (-15.0, -10.0, 2.6, 2.1), (-10.0, -6.0, 3.0, 2.4)]
    for z0, z1, hw, hh in segments:
        d.box((-hw, MID - hh, z0), (hw, MID + hh, z1), "hull", "panel")
    d.box((-2.05, MID - 1.75, -17.2), (2.05, MID + 1.75, -16.2), "red_mark")
    # Body with the cockpit, the astromech behind it, and the engine block at the back.
    d.box((-3.2, MID - 2.6, -6.0), (3.2, MID + 2.4, 8.0), "hull", "panel")
    d.box((-2.4, MID + 2.4, -6.5), (2.4, MID + 3.6, 0.5), "canopy")
    d.box((-2.5, MID + 2.4, -6.7), (2.5, MID + 2.7, -6.2), "hull_grey")
    d.box((-2.5, MID + 3.5, -3.0), (2.5, MID + 3.8, -2.6), "hull_grey")
    d.cyl("y", (0.0, 2.5), MID + 2.4, MID + 3.4, 1.4, "white", sides=8)
    d.cyl("y", (0.0, 2.5), MID + 3.4, MID + 4.0, 1.0, "blue_paint", sides=8)
    d.box((-4.2, MID - 3.0, 8.0), (4.2, MID + 3.0, 14.0), "hull_grey", "ribs")
    d.box((-4.4, MID - 3.2, 13.6), (4.4, MID + 3.2, 14.4), "hull")
    d.box((-0.6, MID - 3.6, -8.0), (0.6, MID - 2.4, 6.0), "hull_grey")

    # The four wings with their engines and the cannons at the tips. Built flat; the game opens
    # them into the X around the long axis.
    for name, side, up in (("wing_ur", 1, 1), ("wing_ul", -1, 1), ("wing_lr", 1, -1), ("wing_ll", -1, -1)):
        d.part(name, (0, MID, 0))
        y = MID + up * 1.0
        x0, x1 = (3.2, 22.0) if side > 0 else (-22.0, -3.2)
        d.box((x0, y - 0.35, 2.0), (x1, y + 0.35, 11.0), "hull", "panel")
        # Red squadron stripes near the root and the tip.
        for a, b in ((6.0, 9.5), (17.0, 18.5)):
            lo, hi = (a, b) if side > 0 else (-b, -a)
            d.box((lo, y - 0.4, 3.0), (hi, y + 0.4, 10.0), "red_mark")
        # The engine on the wing root.
        ex = side * 6.4
        ey = MID + up * 2.6
        d.cyl("z", (ex, ey), -4.0, 13.0, 1.9, "hull", sides=8)
        d.cyl("z", (ex, ey), 13.0, 14.6, 1.6, "hull_grey", sides=8)
        d.cyl("z", (ex, ey), 14.6, 15.0, 1.2, "engine_glow", sides=8)
        d.cyl("z", (ex, ey), -4.6, -4.0, 1.4, "hull_grey", sides=8)
        # The laser cannon at the tip.
        cx = side * 21.6
        d.cyl("z", (cx, y), -12.0, 10.0, 0.55, "hull_grey", sides=8)
        d.cyl("z", (cx, y), -6.0, 4.0, 0.85, "hull", sides=8)
        d.cyl("z", (cx, y), -14.0, -12.0, 0.35, "cannon_tip", sides=8)
    return d


def hexagon(d, x0, x1, cy, cz, r, material, deco=None):
    """A regular hexagon in the y-z plane from x0 to x1 with apothem r: flat top and bottom edges,
    points to the front and back, like the TIE wings. Three planks, two turned by 60 degrees."""
    half = r * math.tan(math.pi / 6)
    for k, angle in enumerate((0.0, 60.0, -60.0)):
        inset = 0.01 * k
        frm = (x0 + inset, cy - half, cz - r)
        to = (x1 - inset, cy + half, cz + r)
        rot = {"origin": [(x0 + x1) / 2, cy, cz], "axis": "x", "angle": angle} if angle else None
        d.box(frm, to, material, deco if k == 0 else None, rotation=rot)


def rod(d, x0, x1, cy, cz, length, thickness, angle, material):
    """A thin strut in the y-z plane through (cy, cz), turned by angle degrees from upright."""
    rot = {"origin": [(x0 + x1) / 2, cy, cz], "axis": "x", "angle": angle} if angle else None
    d.box((x0, cy - length / 2, cz - thickness / 2), (x1, cy + length / 2, cz + thickness / 2), material, rotation=rot)


# The TIE Fighter: a ball cockpit between two upright hexagonal solar wings. No moving parts; it
# banks into turns like every ship. Middle of the ball above the ground, in units.
TIE_MID = 11.0


def tie_fighter():
    d = gd.Droid("tie_fighter", 1977_2, 22, 22, folder="ship")
    m = TIE_MID
    d.part("body", (0, m, 0))
    # The ball cockpit, the round front window with its frame, the hatch on top.
    d.ball((0, m, 0), 4.2, "tie_hull", layers=7, sides=12)
    d.cyl("z", (0.0, m), -4.6, -3.7, 2.5, "tie_dark", sides=8)
    d.cyl("z", (0.0, m), -4.8, -4.4, 1.9, "tie_window", sides=8)
    d.box((-0.18, m - 1.9, -4.9), (0.18, m + 1.9, -4.5), "tie_dark")
    d.box((-1.9, m - 0.18, -4.9), (1.9, m + 0.18, -4.5), "tie_dark")
    d.cyl("y", (0.0, 0.0), m + 3.8, m + 4.5, 1.6, "tie_dark", sides=8)
    # Twin ion engine vents at the back.
    for x in (-1.2, 1.2):
        d.cyl("z", (x, m), 3.6, 4.5, 0.75, "tie_dark", sides=8)
        d.cyl("z", (x, m), 4.5, 4.7, 0.55, "tie_engine", sides=8)
    # The two laser cannons under the window.
    for x in (-1.3, 1.3):
        d.cyl("z", (x, m - 2.7), -6.2, -2.8, 0.38, "tie_dark", sides=8)
        d.cyl("z", (x, m - 2.7), -6.6, -6.2, 0.26, "tie_gun_tip", sides=8)
    # The pylons out to the wings, with the hub on the inside of each wing.
    for side in (-1, 1):
        x0, x1 = sorted((side * 3.6, side * 10.0))
        d.box((x0, m - 1.0, -1.3), (x1, m + 1.0, 1.3), "tie_hull", "panel")
        d.box((min(x0, x1) + 1.0, m - 1.3, -0.6), (max(x0, x1) - 1.0, m + 1.3, 0.6), "tie_dark")
        # The wing: a grey frame plate with black solar panels on both faces, spokes and a hub.
        wx = side * 10.6
        hexagon(d, wx - 0.3, wx + 0.3, m, 0.0, 9.6, "tie_dark")
        for face in (-1, 1):
            fx = wx + face * 0.3
            a, b = sorted((fx, fx + face * 0.12))
            hexagon(d, a, b, m, 0.0, 8.9, "solar", "solar_grid")
            a, b = sorted((fx, fx + face * 0.35))
            for angle in (0.0, 60.0, -60.0):
                rod(d, a, b, m, 0.0, 20.2, 0.55, angle, "tie_hull")
        d.cyl("x", (m, 0.0), wx - 0.9, wx + 0.9, 1.8, "tie_hull", sides=8)
    return d


# The TIE Interceptor: the same ball cockpit with four dagger-like wing blades bent towards it and a
# laser cannon at the tip of each blade.
def tie_interceptor():
    d = gd.Droid("tie_interceptor", 1983_2, 22, 22, folder="ship")
    m = TIE_MID
    d.part("body", (0, m, 0))
    d.ball((0, m, 0), 4.0, "tie_hull", layers=7, sides=12)
    d.cyl("z", (0.0, m), -4.4, -3.5, 2.4, "tie_dark", sides=8)
    d.cyl("z", (0.0, m), -4.6, -4.2, 1.8, "tie_window", sides=8)
    d.box((-0.18, m - 1.8, -4.7), (0.18, m + 1.8, -4.3), "tie_dark")
    d.box((-1.8, m - 0.18, -4.7), (1.8, m + 0.18, -4.3), "tie_dark")
    d.cyl("y", (0.0, 0.0), m + 3.6, m + 4.3, 1.5, "tie_dark", sides=8)
    for x in (-1.1, 1.1):
        d.cyl("z", (x, m), 3.4, 4.3, 0.7, "tie_dark", sides=8)
        d.cyl("z", (x, m), 4.3, 4.5, 0.5, "tie_engine", sides=8)
    for side in (-1, 1):
        x0, x1 = sorted((side * 3.4, side * 9.4))
        d.box((x0, m - 0.9, -1.1), (x1, m + 0.9, 1.1), "tie_hull", "panel")
        wx = side * 9.8
        d.cyl("x", (m, 0.0), wx - 0.7, wx + 0.7, 1.7, "tie_hull", sides=8)
        # The wing root behind the hub.
        d.box((wx - 0.35, m - 2.6, 0.3), (wx + 0.35, m + 2.6, 3.4), "tie_dark")
        for up in (-1, 1):
            # Each blade is bent 10 degrees in towards the cockpit (turned around z), and its long
            # forward point slants out from the root (turned around x). The outer strip runs to the
            # point, the inner part stays short, so the front between the two points is cut deep.
            bend = {"origin": [wx, m, 0.0], "x": up * -26.0, "y": 0.0, "z": side * up * 10.0}
            edge = m + up * 9.0
            y0, y1 = sorted((m + up * 5.4, edge))
            d.box((wx - 0.25, y0, -9.0), (wx + 0.25, y1, 2.5), "solar", "solar_grid", rotation=dict(bend))
            y0, y1 = sorted((m + up * 1.0, m + up * 5.4))
            d.box((wx - 0.24, y0, -3.0), (wx + 0.24, y1, 2.5), "solar", "solar_grid", rotation=dict(bend))
            # Frame: the back edge, the outer edge to the point, and the inner edge of the point.
            y0, y1 = sorted((m + up * 1.0, edge))
            d.box((wx - 0.4, y0, 2.5), (wx + 0.4, y1, 3.3), "tie_hull", rotation=dict(bend))
            d.box((wx - 0.4, edge - 0.4, -9.6), (wx + 0.4, edge + 0.4, 3.3), "tie_hull", rotation=dict(bend))
            inner = m + up * 5.4
            d.box((wx - 0.32, inner - 0.3, -9.0), (wx + 0.32, inner + 0.3, -3.0), "tie_hull", rotation=dict(bend))
            # The laser cannon at the point.
            ty = edge - up * 0.4
            d.box((wx - 0.35, ty - 0.35, -11.0), (wx + 0.35, ty + 0.35, -8.0), "tie_dark", rotation=dict(bend))
            d.box((wx - 0.22, ty - 0.22, -11.6), (wx + 0.22, ty + 0.22, -11.0), "tie_gun_tip", rotation=dict(bend))
    return d


SHIPS = [xwing, tie_fighter, tie_interceptor]


def write_java(rigs):
    lines = [
        "package com.phiro.sabercraft.client.ship;",
        "",
        "import java.util.List;",
        "import java.util.Map;",
        "",
        "/**",
        " * The parts of each ship model and the points they turn around, in blocks from the ground",
        " * under the ship's middle (x right, y up, the nose to -z), at the size the game draws them.",
        " * Generated by tools/gen_ships.py, do not edit.",
        " */",
        "public final class ShipParts {",
        "\t/** How much larger the game draws the models than they are built. */",
        "\tpublic static final float SCALE = 2.0F;",
        "",
        "\t/** One part: its name, its item model, and its pivot. */",
        "\tpublic record Part(String name, String model, float x, float y, float z) {",
        "\t}",
        "",
        "\tpublic static final Map<String, List<Part>> PARTS = Map.of(",
    ]
    entries = []
    for name, parts in rigs:
        body = ",\n".join(
            f"\t\t\t\tnew Part(\"{g}\", \"sabercraft:ship/{name}_{g}\", {p[0] / 8:.4f}F, {p[1] / 8:.4f}F, {p[2] / 8:.4f}F)" for g, p, _ in parts)
        entries.append(f"\t\t\t\"{name}\", List.of(\n{body}\n\t\t\t)")
    lines.append(",\n".join(entries))
    lines += ["\t);", "", "\tprivate ShipParts() {", "\t}", "}", ""]
    os.makedirs(os.path.dirname(JAVA), exist_ok=True)
    with open(JAVA, "w") as f:
        f.write("\n".join(lines))


def main():
    preview = "--preview" in sys.argv
    rigs = []
    for make in SHIPS:
        d = make()
        rigs.append((d.name, d.write(preview)))
    write_java(rigs)


if __name__ == "__main__":
    main()
