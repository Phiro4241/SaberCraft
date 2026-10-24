#!/usr/bin/env python3
"""
Draws the combat moves of SaberCraft as stick-figure filmstrips, so moves can be designed and
checked without starting the game. It reads the same file the mod loads:

    src/client/resources/assets/sabercraft/combat/moves.json

and evaluates the poses with the same math as the Java code (Catmull-Rom between keyframes, the
same transforms as Minecraft 26.2 uses for held items). Run from the repository root:

    python3 tools/preview_moves.py [move id ...] [--out build/move-previews]

For every move it writes one PNG with a row of frames: the first-person view (what the player
sees, 16:9, vertical FOV 70) and a third-person view from the front, from the side and from the
top. The blade is drawn from the emitter to the tip; older frames of the same move are drawn
fainter, so the arc of the swing is visible. Needs numpy and Pillow.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

MOVES = os.path.join("src", "client", "resources", "assets", "sabercraft", "combat", "moves.json")

# Model space points of the hilts (see tools/gen_hilts.py): grip (where the hand holds every hilt),
# emitter of the one-handed hilts and the two emitters of the double hilt.
GRIP = (0.2923, 0.2923, 0.5)
EMITTER = (0.5795, 0.5795, 0.5)
DOUBLE_EMITTERS = ((0.6458, 0.6458, 0.5, 1), (-0.0613, -0.0613, 0.5, -1))
BLADE = 1.2
DIAG = 1.0 / math.sqrt(2.0)


def T(x, y, z):
    m = np.eye(4)
    m[:3, 3] = [x, y, z]
    return m


def Rx(a):
    c, s = math.cos(a), math.sin(a)
    m = np.eye(4)
    m[1, 1], m[1, 2], m[2, 1], m[2, 2] = c, -s, s, c
    return m


def Ry(a):
    c, s = math.cos(a), math.sin(a)
    m = np.eye(4)
    m[0, 0], m[0, 2], m[2, 0], m[2, 2] = c, s, -s, c
    return m


def Rz(a):
    c, s = math.cos(a), math.sin(a)
    m = np.eye(4)
    m[0, 0], m[0, 1], m[1, 0], m[1, 1] = c, -s, s, c
    return m


def S(x, y=None, z=None):
    m = np.eye(4)
    m[0, 0], m[1, 1], m[2, 2] = x, x if y is None else y, x if z is None else z
    return m


def rad(d):
    return math.radians(d)


def display(translation, rotation, scale):
    """Vanilla ItemTransform.apply for the right hand."""
    t = [v / 16.0 for v in translation]
    return T(*t) @ Rx(rad(rotation[0])) @ Ry(rad(rotation[1])) @ Rz(rad(rotation[2])) @ S(scale) @ T(-0.5, -0.5, -0.5)


FP_DISPLAY = display([1.13, 3.2, 1.13], [0, -90, 25], 0.68)
TP_DISPLAY = display([0, 4.0, 0.5], [0, -90, 55], 0.85)


def apply(m, p):
    return (m @ np.array([p[0], p[1], p[2], 1.0]))[:3]


# --- keyframe evaluation, mirrors CombatPose/Keyframes.java ------------------------------------

def catmull(p0, p1, p2, p3, t):
    t2, t3 = t * t, t * t * t
    return 0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3)


def sample(frames, key, time, size):
    """Catmull-Rom through the frames that have the key; holds the ends."""
    keyed = [(f["t"], np.array(f[key], dtype=float)) for f in frames if key in f]
    if not keyed:
        return None
    if time <= keyed[0][0]:
        return keyed[0][1]
    if time >= keyed[-1][0]:
        return keyed[-1][1]
    for i in range(len(keyed) - 1):
        t0, v1 = keyed[i]
        t1, v2 = keyed[i + 1]
        if t0 <= time <= t1:
            v0 = keyed[i - 1][1] if i > 0 else v1
            v3 = keyed[i + 2][1] if i + 2 < len(keyed) else v2
            return catmull(v0, v1, v2, v3, (time - t0) / (t1 - t0))
    return keyed[-1][1]


# --- first person -------------------------------------------------------------------------------

FP_PIVOT = apply(FP_DISPLAY, GRIP)
FP_ARM = np.array([0.56, -0.52, -0.72])
TAN = math.tan(math.radians(35))
# The handheld transform leans the blade 20 degrees back from vertical; aim 0/0 means "straight
# ahead". Mirrors FirstPersonPose.java.
REST_LEAN = 110.0


def fp_matrix(grip, aim):
    """grip: screen x/y in half screen heights plus extra depth; aim: elevation, azimuth, roll."""
    depth = 0.72 + grip[2]
    target = np.array([grip[0] * TAN * depth, grip[1] * TAN * depth, -depth])
    pos = target - FP_ARM - FP_PIVOT
    pivot = FP_PIVOT
    pose = T(*FP_ARM) @ T(*pos) @ T(*pivot) @ Ry(rad(aim[1])) @ Rx(rad(aim[0] - REST_LEAN)) @ Rz(rad(aim[2])) @ T(*(-pivot))
    return pose @ FP_DISPLAY


def blades(double):
    if double:
        out = []
        for x, y, z, sign in DOUBLE_EMITTERS:
            out.append(((x, y, z), (x + sign * BLADE * DIAG, y + sign * BLADE * DIAG, z)))
        return out
    return [(EMITTER, (EMITTER[0] + BLADE * DIAG, EMITTER[1] + BLADE * DIAG, EMITTER[2]))]


def fp_project(p, w, h):
    tan = math.tan(math.radians(35))
    if p[2] > -0.05:
        return None
    x = p[0] / -p[2] / (tan * w / h)
    y = p[1] / -p[2] / tan
    return (w / 2 + x * w / 2, h / 2 - y * h / 2)


# --- third person -------------------------------------------------------------------------------

def arm_matrix(pivot, rot):
    return T(pivot[0] / 16, pivot[1] / 16, pivot[2] / 16) @ Rz(rot[2]) @ Ry(rot[1]) @ Rx(rot[0])


def tp_points(pose, double):
    """World-space points (player at origin facing +Z) of the body, arms and blade."""
    main = [rad(v) for v in pose.get("main_arm", [-18, 0, 0])]
    off = pose.get("off_arm")
    off = [rad(v) for v in off] if off is not None else [rad(-10), 0, 0]
    body = pose.get("body", [0, 0])
    body_x, body_y = rad(body[0]), rad(body[1])
    spin = rad(pose.get("spin", 0.0))
    item = [rad(v) for v in pose.get("item", [0, 0, 0])]

    # Whole-body movement (Phase 7), the same order as SaberCombat.applyRoot.
    main_leg = [rad(v) for v in pose.get("main_leg", [0, 0, 0])]
    off_leg = [rad(v) for v in pose.get("off_leg", [0, 0, 0])]
    legs = 1.0 if ("main_leg" in pose or "off_leg" in pose) else 0.0
    lean = rad(pose.get("lean", 0.0))
    rp = pose.get("root", [0, 0, 0])
    crouch = pose.get("crouch", 0.0)

    def upright(leg):
        return math.cos(leg[0] + lean) * math.cos(leg[2])

    drop = 0.75 * (1 - max(upright(main_leg), upright(off_leg))) * legs + crouch
    root = (Ry(rad(180) - spin) @ T(0, rp[2] - drop, 0) @ T(0, 0.9, 0) @ Rx(-rad(rp[0])) @ Rz(rad(rp[1])) @ T(0, -0.9, 0)
            @ T(0, 0.75, 0) @ Rx(-lean) @ T(0, -0.75, 0) @ S(-1, -1, 1) @ T(0, -1.501, 0))
    right_leg = arm_matrix((-1.9, 12, 0), (main_leg[0] - lean, main_leg[1], main_leg[2]))
    left_leg = arm_matrix((1.9, 12, 0), (off_leg[0] - lean, off_leg[1], off_leg[2]))
    right_pivot = (-math.cos(body_y) * 5, 2, math.sin(body_y) * 5)
    left_pivot = (math.cos(body_y) * 5, 2, -math.sin(body_y) * 5)
    right = arm_matrix(right_pivot, (main[0], main[1] + body_y, main[2]))
    left = arm_matrix(left_pivot, (off[0], off[1] + body_y, off[2]))

    hand = root @ right @ Rx(rad(-90)) @ Ry(rad(180)) @ T(1 / 16, 2 / 16, -10 / 16)
    grip = apply(TP_DISPLAY, GRIP)
    hand = hand @ T(*grip) @ Ry(item[1]) @ Rx(item[0]) @ Rz(item[2]) @ T(*(-grip)) @ TP_DISPLAY

    body_m = root @ T(0, 0, 0) @ Ry(body_y) @ Rx(body_x)
    pts = {
        "neck": apply(body_m, (0, 0, 0)), "hip": apply(body_m, (0, 12 / 16, 0)),
        "rs": apply(root @ right, (0, 0, 0)), "rh": apply(root @ right, (-1 / 16, 10 / 16, 0)),
        "ls": apply(root @ left, (0, 0, 0)), "lh": apply(root @ left, (1 / 16, 10 / 16, 0)),
        "head": apply(root, (0, -4 / 16, 0)),
        "rhip": apply(root @ right_leg, (0, 0, 0)), "rfoot": apply(root @ right_leg, (0, 12 / 16, 0)),
        "lhip": apply(root @ left_leg, (0, 0, 0)), "lfoot": apply(root @ left_leg, (0, 12 / 16, 0)),
    }
    lines = [(apply(hand, a), apply(hand, b)) for a, b in blades(double)]
    hilt = (apply(hand, (0.2923 - 0.25 if double else 0.0, 0.2923 - 0.25 if double else 0.0, 0.5)), apply(hand, EMITTER if not double else (0.6458, 0.6458, 0.5)))
    return pts, lines, hilt


VIEWS = {
    # name: (right axis, up axis) in world coordinates; player faces +Z.
    "front": (np.array([1.0, 0, 0]), np.array([0, 1.0, 0])),
    "side": (np.array([0, 0, 1.0]), np.array([0, 1.0, 0])),
    "top": (np.array([-1.0, 0, 0]), np.array([0, 0, 1.0])),
}


def ortho(p, view, w, h, scale=60):
    r, u = VIEWS[view]
    base = h * 0.5 if view == "top" else h * 0.92
    return (w / 2 + float(np.dot(p, r)) * scale, base - float(np.dot(p, u)) * scale)


def draw_move(name, move, style_double, out, columns=3):
    duration = move["duration"]
    steps = int(duration) + 1
    fw, fh = 320, 180
    tw = 200
    cell_w, cell_h = 3 * tw, fh + tw + 24
    grid_cols = max(1, min(columns, steps))
    grid_rows = (steps + grid_cols - 1) // grid_cols
    img = Image.new("RGB", (grid_cols * cell_w, grid_rows * cell_h + 14), (24, 24, 30))
    d = ImageDraw.Draw(img)
    d.text((4, 2), name + "  strike t=" + str(move.get("strike")), fill=(255, 255, 255))
    fp = move.get("first_person", [])
    tp = move.get("third_person", [])
    for i in range(steps):
        t = i * duration / max(1, steps - 1)
        x0 = (i % grid_cols) * cell_w
        y0 = 14 + (i // grid_cols) * cell_h
        d.rectangle([x0, y0, x0 + fw - 2, y0 + fh - 2], outline=(70, 70, 80))
        d.text((x0 + 3, y0 + 2), "t=%.1f" % t, fill=(220, 220, 220))
        cx, cy = x0 + fw / 2, y0 + fh / 2
        d.line([(cx - 4, cy), (cx + 4, cy)], fill=(120, 120, 120))
        d.line([(cx, cy - 4), (cx, cy + 4)], fill=(120, 120, 120))
        for k, back in enumerate((1.5, 1.0, 0.5, 0.0)):
            tt = max(0.0, t - back)
            grip = sample(fp, "grip", tt, 3)
            aim = sample(fp, "aim", tt, 3)
            grip = np.array([1.255, -1.01, 0.0]) if grip is None else grip
            aim = np.array([110.0, 0.0, 0.0]) if aim is None else aim
            m = fp_matrix(grip, aim)
            shade = int(60 + 195 * (k + 1) / 4)
            for a, b in blades(style_double):
                pa, pb = fp_project(apply(m, a), fw, fh), fp_project(apply(m, b), fw, fh)
                if pa and pb:
                    d.line([(x0 + pa[0], y0 + pa[1]), (x0 + pb[0], y0 + pb[1])], fill=(shade, shade // 3, shade // 3), width=4 if k == 3 else 1)
            pg = fp_project(apply(m, GRIP), fw, fh)
            if pg and k == 3:
                d.ellipse([x0 + pg[0] - 4, y0 + pg[1] - 4, x0 + pg[0] + 4, y0 + pg[1] + 4], fill=(200, 200, 200))
        pose = {}
        for key in ("main_arm", "off_arm", "body", "item", "main_leg", "off_leg", "root"):
            v = sample(tp, key, t, 3)
            if v is not None:
                pose[key] = list(v)
        for key in ("spin", "lean", "crouch"):
            v = sample(tp, key, t, 1)
            if v is not None:
                pose[key] = float(np.ravel(v)[0])
        pts, lines, hilt = tp_points(pose, style_double)
        for j, view in enumerate(("front", "side", "top")):
            vx, vy = x0 + j * tw, y0 + fh + 4
            d.rectangle([vx, vy, vx + tw - 2, vy + tw - 2], outline=(70, 70, 80))
            d.text((vx + 3, vy + 2), view, fill=(150, 150, 150))
            P = lambda p, vx=vx, vy=vy, view=view: (vx + ortho(p, view, tw, tw, 55)[0], vy + ortho(p, view, tw, tw, 55)[1])
            if view != "top":
                d.line([P(np.array([-3.0, 0, -3.0])), P(np.array([3.0, 0, 3.0]))], fill=(60, 90, 60), width=1)
            d.line([P(pts["rhip"]), P(pts["rfoot"])], fill=(70, 120, 200), width=4)
            d.line([P(pts["lhip"]), P(pts["lfoot"])], fill=(70, 170, 100), width=4)
            d.line([P(pts["neck"]), P(pts["hip"])], fill=(140, 140, 160), width=3)
            d.line([P(pts["rs"]), P(pts["ls"])], fill=(140, 140, 160), width=2)
            d.line([P(pts["rs"]), P(pts["rh"])], fill=(90, 160, 255), width=4)
            d.line([P(pts["ls"]), P(pts["lh"])], fill=(90, 220, 120), width=4)
            hp = P(pts["head"])
            d.ellipse([hp[0] - 7, hp[1] - 7, hp[0] + 7, hp[1] + 7], outline=(200, 200, 200))
            for a, b in lines:
                d.line([P(a), P(b)], fill=(255, 70, 60), width=3)
            d.line([P(hilt[0]), P(hilt[1])], fill=(230, 230, 230), width=4)
    img.save(os.path.join(out, name.replace(":", "_") + ".png"))


def main():
    args = sys.argv[1:]
    out = os.path.join("build", "move-previews")
    if "--out" in args:
        i = args.index("--out")
        out = args[i + 1]
        del args[i:i + 2]
    os.makedirs(out, exist_ok=True)
    data = json.load(open(MOVES))
    doubles = set()
    for style in data["styles"].values():
        if style.get("double"):
            for key in ("combo", "sprint", "jump", "special"):
                v = style.get(key)
                for m in (v if isinstance(v, list) else [v] if v else []):
                    doubles.add(m)
    for name, move in data["moves"].items():
        if args and name not in args and name.split(":")[-1] not in args:
            continue
        draw_move(name, move, name in doubles, out)
        print("wrote", name)
    # Idle poses as a one-frame "move".
    for name, style in data["styles"].items():
        idle = {"duration": 0.0, "strike": None,
                "first_person": [dict(style.get("idle_first_person", {}), t=0)],
                "third_person": [dict(style.get("idle_third_person", {}), t=0)]}
        idle["duration"] = 1
        draw_move(name.replace(":", "_") + "_idle", idle, bool(style.get("double")), out)


if __name__ == "__main__":
    main()
