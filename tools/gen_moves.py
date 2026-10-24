#!/usr/bin/env python3
"""
Writes src/client/resources/assets/sabercraft/combat/moves.json, the attack moves and fighting
styles of SaberCraft. Run from the repository root: python3 tools/gen_moves.py

Keyframes are kept short here: first person (t, grip [x, y, z], aim [elevation, azimuth, roll]),
third person (t, main_arm, off_arm, body [lean, twist], item, optional spin). The format of the
JSON itself is described in CombatLibrary.java; the JSON can also be edited by hand.
Preview the result with: python3 tools/preview_moves.py
"""
import json
# FP keyframe: (t, grip[x,y,z], aim[e,a,r]); TP keyframe: (t, main, off, body, item, spin)
def move(duration, strike, sound, fp, tp, **kw):
    m = {"duration": duration, "strike": strike, "sound": sound}
    m.update(kw)
    m["first_person"] = [{"t": t, "grip": g, "aim": a} for t, g, a in fp]
    frames = []
    for f in tp:
        t, main, off, body, item = f[:5]
        fr = {"t": t, "main_arm": main}
        if off is not None: fr["off_arm"] = off
        if body is not None: fr["body"] = body
        if item is not None: fr["item"] = item
        if len(f) > 5: fr["spin"] = f[5]
        frames.append(fr)
    m["third_person"] = frames
    return m

M = {}
# ---------------- flowing ----------------
M["flow_diagonal"] = move(10, 3, "swing",
    [(0, [0.95, -0.35, 0.1], [72, -100, 0]), (1.5, [0.65, -0.05, 0.15], [58, -62, 0]), (3, [0.15, -0.2, 0.2], [22, 62, 0]),
     (4.5, [-0.4, -0.55, 0.15], [-28, 88, 0]), (6.5, [-0.65, -0.8, 0.1], [-42, 100, 0])],
    [(0, [-155, 25, 15], [-145, 50, 0], [0, 25], [-40, 0, 0]), (1.5, [-130, 10, 8], [-125, 40, 0], [0, 15], [-50, 0, 0]),
     (3, [-90, -15, 0], [-85, 25, 0], [0, -8], [-65, 0, 0]), (4.5, [-50, -40, 0], [-50, 10, 0], [0, -25], [-70, 0, 0]),
     (6.5, [-35, -48, 0], [-38, 5, 0], [0, -28], [-70, 0, 0])])
M["flow_backhand"] = move(9, 2.5, "swing",
    [(0, [-0.75, -0.35, 0.15], [8, 100, 0]), (1.5, [-0.35, -0.25, 0.2], [10, 62, 0]), (2.5, [0.15, -0.25, 0.22], [10, -48, 0]),
     (4, [0.75, -0.35, 0.15], [6, -100, 0]), (6, [1.0, -0.5, 0.1], [10, -115, 0])],
    [(0, [-90, -60, 0], [-85, 5, 0], [0, -25], [-80, 0, 0]), (1.5, [-92, -30, 0], [-88, 20, 0], [0, -12], [-82, 0, 0]),
     (2.5, [-92, 10, 0], [-88, 40, 0], [0, 6], [-82, 0, 0]), (4, [-85, 50, 5], [-75, 60, 0], [0, 22], [-80, 0, 0]),
     (6, [-75, 62, 10], [-65, 65, 0], [0, 26], [-80, 0, 0])])
M["flow_rising"] = move(9, 2.5, "swing",
    [(0, [0.8, -0.85, 0.1], [-35, -95, 0]), (1.5, [0.4, -0.5, 0.15], [-5, -62, 0]), (2.5, [0.0, -0.15, 0.2], [35, 22, 0]),
     (4, [-0.45, 0.2, 0.15], [70, 80, 0]), (6, [-0.6, 0.25, 0.1], [80, 100, 0])],
    [(0, [-30, 45, 0], [-30, 65, 0], [0, 25], [-70, 0, 0]), (1.5, [-55, 30, 0], [-55, 55, 0], [0, 15], [-62, 0, 0]),
     (2.5, [-100, 0, 0], [-95, 30, 0], [0, 0], [-50, 0, 0]), (4, [-145, -30, -10], [-140, 5, 0], [0, -20], [-30, 0, 0]),
     (6, [-155, -35, -10], [-150, 0, 0], [0, -22], [-25, 0, 0])])
M["flow_overhead"] = move(11, 3.5, "swing",
    [(0, [0.3, 0.3, 0.05], [125, 5, 0]), (2, [0.15, 0.15, 0.15], [100, 0, 0]), (3.5, [0.05, -0.3, 0.25], [20, 5, 0]),
     (5, [0.0, -0.6, 0.25], [-25, 5, 0]), (7, [0.05, -0.75, 0.2], [-35, 5, 0])],
    [(0, [-170, -5, 0], [-165, 30, 0], [-10, 0], [10, 0, 0]), (2, [-160, -5, 0], [-158, 30, 0], [-8, 0], [-20, 0, 0]),
     (3.5, [-95, -10, 0], [-95, 30, 0], [15, 0], [-70, 0, 0]), (5, [-55, -10, 0], [-55, 25, 0], [20, 0], [-85, 0, 0]),
     (7, [-45, -10, 0], [-45, 25, 0], [15, 0], [-85, 0, 0])])
M["flow_stab"] = move(10, 3, "swing",
    [(0, [0.85, -0.5, -0.12], [12, -20, 0]), (1.5, [0.8, -0.45, -0.15], [10, -18, 0]), (3, [0.15, -0.22, 0.45], [3, 8, 0]),
     (5.5, [0.12, -0.22, 0.48], [3, 8, 0])],
    [(0, [-60, 20, 10], [-55, 45, 0], [0, 30], [-80, 0, 0]), (1.5, [-62, 22, 10], [-58, 45, 0], [0, 32], [-82, 0, 0]),
     (3, [-92, -5, 0], [-88, 25, 0], [10, -20], [-88, 0, 0]), (5.5, [-92, -5, 0], [-88, 25, 0], [10, -20], [-88, 0, 0])],
    interrupt=6)
M["flow_spin"] = move(14, 5, "spin",
    [(0, [0.9, -0.45, 0.1], [10, -110, 0]), (2, [1.2, -0.4, 0.2], [8, -150, 0]), (3.5, [-0.2, -0.3, 0.3], [8, 40, 0]),
     (5, [-1.0, -0.35, 0.2], [8, 110, 0]), (6.5, [-1.6, -0.4, 0.1], [8, 150, 0]), (8, [1.6, -0.4, 0.1], [8, -150, 0]),
     (9.5, [0.6, -0.45, 0.15], [10, -90, 0])],
    [(0, [-90, 50, 0], [-85, 65, 0], [0, 25], [-80, 0, 0], 0), (2, [-90, 30, 0], [-88, 55, 0], [0, 10], [-82, 0, 0], -40),
     (5, [-90, -20, 0], [-88, 20, 0], [0, -15], [-82, 0, 0], -180), (8, [-90, -30, 0], [-88, 10, 0], [0, -20], [-82, 0, 0], -320),
     (10, [-70, -20, 0], [-65, 20, 0], [0, -10], [-60, 0, 0], -360)],
    interrupt=12)
# ---------------- heavy ----------------
M["heavy_overhead"] = move(13, 4.5, "heavy",
    [(0, [0.45, 0.35, 0.0], [135, -10, 0]), (2.5, [0.3, 0.3, 0.05], [130, -5, 0]), (4.5, [0.1, -0.35, 0.25], [15, 0, 0]),
     (6, [0.05, -0.75, 0.25], [-40, 0, 0]), (8.5, [0.1, -0.85, 0.2], [-45, 0, 0])],
    [(0, [-175, 5, 5], [-172, 30, 0], [-15, 8], [20, 0, 0]), (2.5, [-178, 0, 5], [-175, 28, 0], [-18, 5], [25, 0, 0]),
     (4.5, [-90, -5, 0], [-90, 28, 0], [20, 0], [-75, 0, 0]), (6, [-45, -5, 0], [-45, 25, 0], [30, 0], [-90, 0, 0]),
     (8.5, [-40, -5, 0], [-40, 25, 0], [28, 0], [-90, 0, 0])])
M["heavy_wide"] = move(13, 4.5, "heavy",
    [(0, [1.3, -0.3, 0.1], [12, -125, 0]), (2.5, [1.1, -0.25, 0.15], [12, -110, 0]), (4.5, [0.0, -0.3, 0.25], [8, 40, 0]),
     (6, [-0.9, -0.4, 0.2], [5, 105, 0]), (8.5, [-1.1, -0.55, 0.15], [0, 120, 0])],
    [(0, [-85, 75, 10], [-80, 90, 0], [0, 38], [-80, 0, 0]), (2.5, [-88, 70, 10], [-82, 88, 0], [0, 40], [-82, 0, 0]),
     (4.5, [-92, 0, 0], [-90, 30, 0], [0, 0], [-85, 0, 0]), (6, [-88, -55, 0], [-85, -15, 0], [0, -35], [-82, 0, 0]),
     (8.5, [-80, -65, 0], [-78, -20, 0], [0, -40], [-80, 0, 0])])
M["heavy_rising"] = move(12, 4, "heavy",
    [(0, [-0.7, -0.9, 0.1], [-40, 100, 0]), (2, [-0.6, -0.8, 0.1], [-35, 95, 0]), (4, [0.0, -0.2, 0.2], [30, -20, 0]),
     (5.5, [0.6, 0.15, 0.15], [70, -85, 0]), (8, [0.8, 0.25, 0.1], [85, -100, 0])],
    [(0, [-30, -50, 0], [-30, -10, 0], [0, -30], [-70, 0, 0]), (2, [-32, -52, 0], [-32, -12, 0], [0, -32], [-70, 0, 0]),
     (4, [-100, 0, 0], [-98, 30, 0], [0, 0], [-50, 0, 0]), (5.5, [-150, 30, 10], [-145, 50, 0], [-5, 22], [-30, 0, 0]),
     (8, [-160, 35, 10], [-155, 55, 0], [-8, 25], [-20, 0, 0])])
M["heavy_spin"] = move(16, 6, "spin",
    [(0, [1.0, -0.4, 0.1], [10, -115, 0]), (2.5, [1.3, -0.35, 0.2], [8, -150, 0]), (4.5, [-0.2, -0.3, 0.3], [8, 40, 0]),
     (6, [-1.0, -0.35, 0.2], [8, 110, 0]), (7.5, [-1.6, -0.4, 0.1], [8, 150, 0]), (9, [1.6, -0.4, 0.1], [8, -150, 0]),
     (11, [0.6, -0.45, 0.15], [10, -90, 0])],
    [(0, [-90, 60, 5], [-85, 75, 0], [0, 30], [-80, 0, 0], 0), (2.5, [-90, 40, 0], [-88, 60, 0], [0, 15], [-82, 0, 0], -40),
     (6, [-92, -20, 0], [-90, 20, 0], [0, -15], [-85, 0, 0], -190), (9, [-90, -30, 0], [-88, 10, 0], [0, -20], [-82, 0, 0], -330),
     (11.5, [-65, -20, 0], [-60, 20, 0], [0, -10], [-60, 0, 0], -360)],
    interrupt=14)
M["heavy_thrust"] = move(12, 4, "heavy",
    [(0, [0.9, -0.45, -0.15], [15, -25, 0]), (2.5, [0.85, -0.42, -0.18], [12, -20, 0]), (4, [0.1, -0.2, 0.5], [3, 6, 0]),
     (7, [0.1, -0.2, 0.52], [3, 6, 0])],
    [(0, [-55, 25, 12], [-50, 50, 0], [0, 38], [-80, 0, 0]), (2.5, [-58, 28, 12], [-52, 52, 0], [0, 40], [-82, 0, 0]),
     (4, [-92, -5, 0], [-90, 25, 0], [15, -25], [-88, 0, 0]), (7, [-92, -5, 0], [-90, 25, 0], [15, -25], [-88, 0, 0])],
    interrupt=8)
# ---------------- staff ----------------
M["staff_sweep"] = move(11, 3.5, "swing",
    [(0, [0.75, -0.45, 0.3], [12, 130, 0]), (2, [0.5, -0.4, 0.3], [12, 110, 0]), (3.5, [0.0, -0.35, 0.35], [12, 80, 0]),
     (5, [-0.5, -0.4, 0.3], [12, 50, 0]), (7.5, [-0.55, -0.5, 0.25], [12, 40, 0])],
    [(0, [-75, 45, 0], [-72, 70, 0], [0, 28], [0, 0, 85]), (2, [-80, 25, 0], [-78, 55, 0], [0, 18], [0, 0, 85]),
     (3.5, [-82, -5, 0], [-80, 30, 0], [0, 0], [0, 0, 85]), (5, [-80, -40, 0], [-78, 0, 0], [0, -22], [0, 0, 85]),
     (7.5, [-75, -50, 0], [-72, -8, 0], [0, -26], [0, 0, 85])])
M["staff_twirl"] = move(12, 4, "spin",
    [(0, [0.15, -0.35, 0.35], [80, 90, 0]), (2, [0.1, -0.3, 0.4], [10, 90, 0]), (4, [0.05, -0.3, 0.42], [-90, 90, 0]),
     (6, [0.05, -0.3, 0.42], [-190, 90, 0]), (8, [0.1, -0.32, 0.38], [-280, 90, 0])],
    [(0, [-88, -10, 0], [-85, 40, 0], [0, 0], [0, 0, 0]), (2, [-90, -10, 0], [-88, 40, 0], [0, 0], [0, 0, -80]),
     (4, [-92, -10, 0], [-90, 40, 0], [0, 0], [0, 0, -180]), (6, [-92, -10, 0], [-90, 40, 0], [0, 0], [0, 0, -280]),
     (8, [-90, -10, 0], [-88, 40, 0], [0, 0], [0, 0, -360])],
    interrupt=8)
M["staff_reverse"] = move(11, 3.5, "swing",
    [(0, [-0.6, -0.45, 0.3], [12, 40, 0]), (2, [-0.4, -0.4, 0.3], [12, 55, 0]), (3.5, [0.05, -0.35, 0.35], [12, 85, 0]),
     (5, [0.55, -0.4, 0.3], [12, 115, 0]), (7.5, [0.7, -0.5, 0.25], [12, 128, 0])],
    [(0, [-75, -50, 0], [-72, -8, 0], [0, -26], [0, 0, -85]), (2, [-80, -30, 0], [-78, 10, 0], [0, -15], [0, 0, -85]),
     (3.5, [-82, 0, 0], [-80, 30, 0], [0, 0], [0, 0, -85]), (5, [-80, 35, 0], [-78, 60, 0], [0, 20], [0, 0, -85]),
     (7.5, [-75, 45, 0], [-72, 70, 0], [0, 26], [0, 0, -85])])
M["staff_spin"] = move(16, 6, "spin",
    [(0, [0.6, -0.45, 0.3], [12, 120, 0]), (3, [0.0, -0.35, 0.35], [12, 85, 0]), (6, [-1.2, -0.4, 0.3], [12, 30, 0]),
     (8, [1.4, -0.4, 0.3], [12, 150, 0]), (11, [0.3, -0.45, 0.3], [15, 95, 0])],
    [(0, [-80, 30, 0], [-78, 60, 0], [0, 20], [0, 0, 85], 0), (3, [-82, 0, 0], [-80, 30, 0], [0, 0], [0, 0, 85], -90),
     (6, [-82, -15, 0], [-80, 20, 0], [0, -8], [0, 0, 85], -200), (9, [-82, -15, 0], [-80, 20, 0], [0, -8], [0, 0, 85], -320),
     (11.5, [-60, -15, 0], [-58, 30, 0], [0, 0], [0, 0, 80], -360)],
    interrupt=14)
M["staff_whirl"] = move(26, 4, "spin",
    [(0, [0.15, -0.35, 0.35], [80, 90, 0]), (2, [0.1, -0.25, 0.45], [20, 90, 0]), (6, [0.05, -0.2, 0.5], [-160, 90, 0]),
     (10, [0.05, -0.2, 0.5], [-360, 90, 0]), (14, [0.05, -0.2, 0.5], [-560, 90, 0]), (18, [0.05, -0.2, 0.5], [-760, 90, 0]),
     (21, [0.1, -0.3, 0.4], [-890, 90, 0])],
    [(0, [-88, -10, 0], [-85, 40, 0], [0, 0], [0, 0, 0]), (2, [-92, -10, 0], [-90, 40, 0], [0, 0], [0, 0, -60]),
     (6, [-95, -10, 0], [-92, 40, 0], [0, 0], [0, 0, -240]), (10, [-95, -10, 0], [-92, 40, 0], [0, 0], [0, 0, -440]),
     (14, [-95, -10, 0], [-92, 40, 0], [0, 0], [0, 0, -640]), (18, [-95, -10, 0], [-92, 40, 0], [0, 0], [0, 0, -840]),
     (21, [-90, -10, 0], [-88, 40, 0], [0, 0], [0, 0, -1000])],
    interrupt=24, blend_out=5)

# ---------------- abilities (any style) ----------------
M["force_push"] = move(14, 4, "none",
    [(0, [1.2551, -1.0103, 0.0], [110, 0, 0]), (2, [1.35, -0.95, -0.1], [100, -25, 0]), (4, [1.45, -1.0, -0.15], [95, -35, 0]),
     (9, [1.4, -1.0, -0.1], [100, -30, 0])],
    [(0, [-40, -15, 0], [-40, 30, 0], [0, 0], [0, 0, 0]), (2, [-30, 25, 10], [-60, 20, 0], [0, 20], [10, 0, 0]),
     (4, [-25, 30, 10], [-95, 5, 0], [5, 30], [15, 0, 0]), (9, [-28, 28, 10], [-92, 5, 0], [5, 28], [15, 0, 0])],
    interrupt=10, trail=False)
M["dash"] = move(10, 3, "none",
    [(0, [0.85, -0.5, -0.12], [12, -20, 0]), (2, [0.3, -0.3, 0.2], [6, 4, 0]), (6, [0.28, -0.3, 0.22], [6, 4, 0])],
    [(0, [-60, 20, 10], [-55, 45, 0], [10, 30], [-80, 0, 0]), (2, [-92, -5, 0], [-40, 40, 0], [20, -20], [-88, 0, 0]),
     (6, [-90, -5, 0], [-40, 40, 0], [18, -20], [-88, 0, 0])],
    interrupt=7)


# ---------------- Phase 7: whole body ----------------
# Legs, hips and the whole figure. Leg angles are like the arm angles (x negative = forward,
# z = outwards: positive for the saber-side leg, negative for the other). "lean" bends the upper
# body forward at the hips, "root" turns the whole figure [pitch forward, roll to its right, lift
# in blocks], "crouch" lowers it. First person "camera" is [dip in blocks, pitch, roll].
def put(frames, t, **values):
    for f in frames:
        if f["t"] == t:
            f.update(values)
            return
    frames.append(dict(t=t, **values))
    frames.sort(key=lambda f: f["t"])


def body(name, legs=(), root=(), cam=()):
    """legs: (t, main_leg, off_leg, lean, crouch); root: (t, [pitch, roll, lift]); cam: (t, [dip, pitch, roll])."""
    m = M[name]
    for t, main, off, lean, crouch in legs:
        put(m["third_person"], t, main_leg=main, off_leg=off, lean=lean, crouch=crouch)
    for t, r in root:
        put(m["third_person"], t, root=r)
    for t, c in cam:
        put(m["first_person"], t, camera=c)


def step(name, strike, end, lead="main", front=-24, back=20, lean=10, prep=-3, side=4, crouch=0.0, prep_crouch=0.03):
    """A step into the strike: weight back during the wind-up, the lead foot forward at the strike."""
    f, b = (front, back)
    if lead == "main":
        strike_pose = ([f, 0, side], [b, 0, -side])
        prep_pose = ([f * 0.3, 0, side], [b * 0.5, 0, -side])
    else:
        strike_pose = ([b, 0, side], [f, 0, -side])
        prep_pose = ([b * 0.5, 0, side], [f * 0.3, 0, -side])
    body(name, legs=[
        (0, prep_pose[0], prep_pose[1], prep, prep_crouch),
        (strike * 0.7, [v * 0.75 for v in strike_pose[0]], [v * 0.75 for v in strike_pose[1]], lean * 0.6, crouch * 0.6),
        (strike, strike_pose[0], strike_pose[1], lean, crouch),
        (end, [v * 0.85 for v in strike_pose[0]], [v * 0.85 for v in strike_pose[1]], lean * 0.7, crouch * 0.7),
    ])


step("flow_diagonal", 3, 6.5, lead="off", front=-22, back=18, lean=10)
step("flow_backhand", 2.5, 6, lead="main", front=-20, back=16, lean=8)
step("flow_rising", 2.5, 6, lead="off", front=-20, back=18, lean=-4, prep=12, prep_crouch=0.1)
step("flow_overhead", 3.5, 7, lead="main", front=-28, back=22, lean=16, prep=-6, crouch=0.04)
step("flow_stab", 3, 5.5, lead="main", front=-36, back=28, lean=16, crouch=0.05)
body("flow_spin", legs=[
    (0, [-8, 0, 6], [10, 0, -6], 4, 0.04), (2, [6, 0, 8], [-6, 0, -8], 8, 0.08), (5, [-10, 0, 8], [12, 0, -8], 10, 0.1),
    (8, [8, 0, 8], [-8, 0, -8], 8, 0.08), (10, [-20, 0, 6], [16, 0, -6], 8, 0.04)])
step("heavy_overhead", 4.5, 8.5, lead="main", front=-30, back=26, lean=22, prep=-10, side=6, crouch=0.06)
step("heavy_wide", 4.5, 8.5, lead="off", front=-22, back=20, lean=10, side=8, crouch=0.04)
step("heavy_rising", 4, 8, lead="off", front=-22, back=20, lean=-6, prep=18, side=6, prep_crouch=0.12)
body("heavy_spin", legs=[
    (0, [-8, 0, 8], [10, 0, -8], 6, 0.05), (2.5, [6, 0, 10], [-6, 0, -10], 10, 0.1), (6, [-12, 0, 10], [14, 0, -10], 14, 0.12),
    (9, [8, 0, 10], [-8, 0, -10], 10, 0.08), (11.5, [-22, 0, 8], [18, 0, -8], 10, 0.05)])
step("heavy_thrust", 4, 7, lead="main", front=-40, back=32, lean=20, side=6, crouch=0.08)
step("staff_sweep", 3.5, 7.5, lead="off", front=-16, back=14, lean=8, side=11, crouch=0.04)
body("staff_twirl", legs=[
    (0, [-6, 0, 10], [8, 0, -10], 4, 0.02), (4, [-8, 0, 11], [10, 0, -11], 6, 0.06), (8, [-6, 0, 10], [8, 0, -10], 4, 0.02)])
step("staff_reverse", 3.5, 7.5, lead="main", front=-16, back=14, lean=8, side=11, crouch=0.04)
body("staff_spin", legs=[
    (0, [-6, 0, 10], [8, 0, -10], 4, 0.04), (3, [8, 0, 12], [-8, 0, -12], 10, 0.1), (6, [-10, 0, 12], [10, 0, -12], 12, 0.12),
    (9, [8, 0, 12], [-8, 0, -12], 10, 0.08), (11.5, [-8, 0, 10], [10, 0, -10], 6, 0.04)])
body("staff_whirl", legs=[
    (0, [-6, 0, 10], [8, 0, -10], 4, 0.02), (6, [-10, 0, 13], [12, 0, -13], 8, 0.08), (18, [-10, 0, 13], [12, 0, -13], 8, 0.08),
    (21, [-6, 0, 10], [8, 0, -10], 4, 0.02)])
step("force_push", 4, 9, lead="main", front=-24, back=20, lean=12, prep=-4)
step("dash", 3, 6, lead="main", front=-40, back=32, lean=20, crouch=0.08)

# ---------------- Phase 7: dodges, rolls, landings ----------------
TUCK_ARM = [-75, -25, 0]
TUCK_OFF = [-80, 30, 0]


def roll_move(name, axis, sign, duration=13, lift=0.35, fp_side=0.0, tuck=(TUCK_ARM, [80, 0, 0]),
              ends=(([-50, -10, 5], [-60, 30, 0], [40, 0, 0]), ([-55, -10, 5], [-55, 30, 0], [40, 0, 0]))):
    """A roll over the head (axis 0, forward/back) or over the shoulder (axis 1, sideways)."""
    def r(angle, l):
        v = [0.0, 0.0, l]
        v[axis] = angle * sign
        return v
    M[name] = move(duration, 4, "none",
        [(0, [0.85, -0.75, 0.0], [60, -30, 0]), (3, [0.7, -1.0, -0.1], [10, -60, 0]), (7, [0.75, -0.95, -0.05], [20, -50, 0]),
         (10, [0.9, -0.85, 0.05], [70, 0, 0])],
        [(0, ends[0][0], ends[0][1], [0, 0], ends[0][2]), (2, tuck[0], TUCK_OFF, [0, 0], tuck[1]),
         (7, tuck[0], TUCK_OFF, [0, 0], tuck[1]), (10, ends[1][0], ends[1][1], [0, 0], ends[1][2])],
        interrupt=10, trail=False, blend_in=1.0, blend_out=3.0)
    body(name,
        legs=[(0, [-30, 0, 4], [24, 0, -4], 18, 0.12), (2, [-70, 0, 6], [-40, 0, -6], 50, 0.2),
              (4, [-110, 0, 6], [-105, 0, -6], 70, 0.15), (6.5, [-110, 0, 6], [-105, 0, -6], 70, 0.15),
              (8.5, [-60, 0, 6], [10, 0, -6], 35, 0.2), (10, [-25, 0, 5], [20, 0, -5], 14, 0.12)],
        root=[(0, r(0, 0.0)), (2, r(70, 0.0)), (4, r(165, lift)), (6.5, r(270, lift)), (8.5, r(345, 0.0)), (9.5, r(360, 0.0))],
        cam=[(0, [0.0, 0, 0]), (2, [0.45, 18, fp_side * 8]), (4, [0.75, 28, fp_side * 18]), (6.5, [0.75, 18, fp_side * 18]),
             (8.5, [0.4, 6, fp_side * 6]), (11, [0.0, 0, 0])])


roll_move("dodge_roll", 0, 1, lift=0.0, tuck=([-30, -20, 0], [120, 0, -45]))
roll_move("dodge_roll_right", 1, 1, duration=12, lift=0.0, fp_side=1.0, tuck=(TUCK_ARM, [0, 0, 0]))
roll_move("dodge_roll_left", 1, -1, duration=12, lift=0.0, fp_side=-1.0, tuck=(TUCK_ARM, [0, 0, 0]))

M["dodge_backflip"] = move(13, 4, "none",
    [(0, [0.9, -0.7, 0.0], [70, -20, 0]), (3, [0.8, -0.4, -0.1], [100, -10, 0]), (8, [0.9, -0.8, 0.0], [70, -10, 0])],
    [(0, [-60, -10, 5], [-60, 30, 0], [0, 0], [30, 0, 0]), (2, [-160, -5, 15], [-160, 10, -10], [0, 0], [110, 0, 0]),
     (6, [-170, 0, 20], [-170, 0, -20], [0, 0], [120, 0, 0]), (10, [-50, -10, 5], [-55, 30, 0], [0, 0], [40, 0, 0])],
    interrupt=10, trail=False, blend_in=1.0, blend_out=3.0)
body("dodge_backflip",
    legs=[(0, [-10, 0, 4], [10, 0, -4], -8, 0.12), (2, [10, 0, 4], [15, 0, -4], -15, 0.0), (4, [-100, 0, 6], [-95, 0, -6], 40, 0.0),
          (7, [-100, 0, 6], [-95, 0, -6], 40, 0.0), (9, [-25, 0, 5], [20, 0, -5], 12, 0.15)],
    root=[(0, [0, 0, 0]), (2, [-40, 0, 0]), (4, [-150, 0, 0]), (6, [-250, 0, 0]), (8, [-340, 0, 0]), (9, [-360, 0, 0])],
    cam=[(0, [0.0, 0, 0]), (2, [-0.1, -25, 0]), (5, [0.0, -40, 0]), (8, [0.25, -8, 0]), (11, [0.0, 0, 0])])

for name, sign in (("dodge_sidestep_right", 1), ("dodge_sidestep_left", -1)):
    M[name] = move(8, 3, "none",
        [(0, [0.9, -0.8, 0.05], [82, 18, 0]), (3, [0.95 + 0.1 * sign, -0.85, 0.05], [75, 25, 0]), (6, [1.0, -0.9, 0.05], [82, 18, 0])],
        [(0, [-50, -10, 0], [-50, 35, 0], [0, -6 * sign], [30, 0, -10]), (3, [-55, -12, 0], [-55, 35, 0], [0, -10 * sign], [30, 0, -10]),
         (6, [-45, -15, 0], [-45, 35, 0], [0, -4 * sign], [35, 0, -10])],
        interrupt=5, trail=False, blend_in=1.0, blend_out=2.5)
    lead, trail_leg = ([-4, 0, 22], [6, 0, -4]) if sign > 0 else ([6, 0, 4], [-4, 0, -22])
    body(name,
        legs=[(0, [-4, 0, 6], [6, 0, -6], 6, 0.05), (1.5, lead, trail_leg, 8, 0.1), (4, [-6, 0, 10], [8, 0, -10], 6, 0.06),
              (6, [-10, 0, 5], [12, 0, -5], 4, 0.02)],
        root=[(0, [0, 0, 0]), (1.5, [0, 8 * sign, 0]), (4, [0, 3 * sign, 0]), (6, [0, 0, 0])],
        cam=[(0, [0, 0, 0]), (1.5, [0.1, 0, 5 * sign]), (5, [0, 0, 0])])

M["dodge_backstep"] = move(9, 3, "none",
    [(0, [0.85, -0.75, 0.05], [80, 20, 0]), (2, [0.6, -0.55, 0.12], [45, 70, 0]), (6, [0.7, -0.6, 0.1], [50, 65, 0])],
    [(0, [-50, -10, 0], [-50, 35, 0], [0, 0], [30, 0, -10]), (2, [-90, -20, 0], [-88, 40, 0], [0, -8], [0, 0, 50]),
     (6, [-85, -18, 0], [-85, 38, 0], [0, -6], [0, 0, 45])],
    interrupt=6, trail=False, blend_in=1.0, blend_out=3.0)
body("dodge_backstep",
    legs=[(0, [-6, 0, 5], [8, 0, -5], 4, 0.05), (1.5, [12, 0, 5], [-14, 0, -5], -6, 0.0), (4, [16, 0, 6], [-12, 0, -6], 10, 0.12),
          (7, [-8, 0, 5], [10, 0, -5], 6, 0.04)],
    cam=[(0, [0, 0, 0]), (1.5, [-0.05, -4, 0]), (4, [0.15, 5, 0]), (7, [0, 0, 0])])

M["dodge_lunge"] = move(11, 3, "heavy",
    [(0, [0.9, -0.45, -0.15], [15, -25, 0]), (1.5, [0.7, -0.4, 0.0], [10, -10, 0]), (3, [0.1, -0.22, 0.55], [3, 6, 0]),
     (7, [0.12, -0.25, 0.5], [3, 6, 0])],
    [(0, [-55, 25, 12], [-50, 50, 0], [0, 38], [-80, 0, 0]), (1.5, [-75, 10, 5], [-70, 40, 0], [5, 10], [-85, 0, 0]),
     (3, [-92, -5, 0], [-90, 25, 0], [15, -25], [-88, 0, 0]), (7, [-90, -5, 0], [-88, 25, 0], [12, -22], [-88, 0, 0])],
    interrupt=8, volume=1.0)
body("dodge_lunge",
    legs=[(0, [-10, 0, 6], [14, 0, -6], 4, 0.06), (1.5, [-30, 0, 6], [26, 0, -6], 14, 0.08), (3, [-48, 0, 6], [40, 0, -6], 22, 0.1),
          (7, [-44, 0, 6], [36, 0, -6], 18, 0.08)],
    cam=[(0, [0, 0, 0]), (3, [0.25, 6, 0]), (7, [0.2, 4, 0]), (10, [0, 0, 0])])

M["dodge_spin_step"] = move(12, 5, "spin",
    [(0, [0.9, -0.45, 0.1], [10, -110, 0]), (2, [1.2, -0.4, 0.2], [8, -150, 0]), (3.5, [-0.2, -0.3, 0.3], [8, 40, 0]),
     (5, [-1.0, -0.35, 0.2], [8, 110, 0]), (7, [-0.4, -0.5, 0.15], [-10, 80, 0])],
    [(0, [-90, 50, 0], [-60, 50, 0], [0, 25], [-80, 0, 0], 0), (2, [-90, 30, 0], [-60, 50, 0], [0, 10], [-82, 0, 0], -90),
     (4, [-90, -10, 0], [-60, 40, 0], [0, -10], [-82, 0, 0], -250), (6, [-80, -35, 0], [-55, 30, 0], [5, -25], [-75, 0, 0], -360)],
    interrupt=9, blend_out=3.5)
body("dodge_spin_step",
    legs=[(0, [-14, 0, 6], [12, 0, -6], 6, 0.04), (2, [10, 0, 8], [-16, 0, -8], 10, 0.08), (4, [-18, 0, 8], [14, 0, -8], 12, 0.1),
          (6, [-28, 0, 6], [22, 0, -6], 14, 0.06)],
    cam=[(0, [0, 0, 0]), (4, [0.12, 0, 0]), (8, [0, 0, 0])])

roll_move("staff_roll", 0, 1, lift=0.0, tuck=([-30, -20, 0], [90, 0, 90]),
          ends=(([-45, -20, 0], [-45, 40, 0], [0, 0, 80]), ([-45, -20, 0], [-45, 40, 0], [0, 0, 80])))

M["land"] = move(9, 1, "none",
    [(0, [1.0, -0.95, 0.05], [80, 18, 0]), (1.5, [1.0, -1.1, 0.05], [70, 20, 0]), (5, [1.0, -0.9, 0.05], [82, 18, 0])],
    [(0, [-50, -10, 5], [-50, 35, 10], [0, 0], [35, 0, -10]), (1.5, [-35, -10, 15], [-35, 30, 25], [0, 0], [45, 0, -10]),
     (5, [-40, -15, 0], [-45, 35, 0], [0, 0], [35, 0, -10])],
    interrupt=2, trail=False, blend_in=0.5, blend_out=3.5)
body("land",
    legs=[(0, [-14, 0, 6], [12, 0, -6], 6, 0.0), (1.5, [-32, 0, 7], [28, 0, -7], 16, 0.05), (5, [-12, 0, 5], [12, 0, -5], 6, 0.02)],
    cam=[(0, [0, 0, 0]), (1.5, [0.22, 4, 0]), (5, [0.03, 0, 0]), (8, [0, 0, 0])])
M["land_heavy"] = move(16, 1, "none",
    [(0, [1.0, -0.95, 0.05], [80, 18, 0]), (2, [1.1, -1.2, 0.05], [40, 10, 0]), (8, [1.05, -1.1, 0.05], [50, 15, 0]),
     (12, [1.0, -0.9, 0.05], [82, 18, 0])],
    [(0, [-50, -10, 5], [-50, 35, 10], [0, 0], [35, 0, -10]), (2, [-20, -5, 25], [-60, 20, 0], [0, 0], [70, 0, -10]),
     (8, [-25, -5, 25], [-55, 20, 0], [0, 0], [65, 0, -10]), (12, [-40, -15, 0], [-45, 35, 0], [0, 0], [35, 0, -10])],
    interrupt=4, trail=False, blend_in=0.5, blend_out=4.0)
body("land_heavy",
    legs=[(0, [-14, 0, 6], [12, 0, -6], 6, 0.0), (2, [-80, 0, 8], [62, 0, -6], 26, 0.06), (8, [-78, 0, 8], [60, 0, -6], 24, 0.05),
          (12, [-14, 0, 6], [12, 0, -6], 6, 0.03)],
    cam=[(0, [0, 0, 0]), (2, [0.55, 10, 0]), (8, [0.5, 8, 0]), (13, [0, 0, 0])])


# ---------------- Phase 7: block, parry, guard break ----------------
# Reactions to the server's verdict on a hit, played on top of the stance. One set for single
# blades, one for the staff.
GUARD_FP = ([0.45, -0.55, 0.15], [38, 78, 0])
GUARD_TP = ([-95, -20, 0], [-90, 40, 0], [0, -10], [0, 0, 55])
GUARD_LEGS = ([-14, 0, 5], [16, 0, -6], 6, 0.04)
STAFF_GUARD_FP = ([0.1, -0.45, 0.35], [65, 90, 0])
STAFF_GUARD_TP = ([-95, -15, 0], [-92, 40, 0], [0, 0], [0, 0, 30])


def reactions(prefix, guard_fp, guard_tp, idle_fp, idle_tp, hit_item, parry_items, break_item):
    """Block recoil, parry beat and guard break around the given stance."""
    g, a = guard_fp
    m, o, b, it = guard_tp
    M[prefix + "guard_hit"] = move(7, 0.5, "none",
        [(0, g, a), (1, [g[0] + 0.05, g[1] + 0.05, g[2] - 0.15], [a[0] + 7, a[1] + 7, 0]), (3, [g[0] + 0.02, g[1] + 0.02, g[2] - 0.05], [a[0] + 2, a[1] + 2, 0]),
         (5, g, a)],
        [(0, m, o, b, it), (1, [m[0] + 15, m[1] - 5, 0], [o[0] + 12, o[1] - 5, 0], [b[0], b[1] - 4], hit_item), (3.5, m, o, b, it)],
        interrupt=3, trail=False, blend_in=0.5, blend_out=2.5)
    body(prefix + "guard_hit",
        legs=[(0,) + GUARD_LEGS, (1, [-6, 0, 6], [24, 0, -6], -4, 0.07), (4,) + GUARD_LEGS],
        cam=[(0, [0, 0, 0]), (1, [0.04, -3, 2]), (4, [0, 0, 0])])
    p1, p2, p3 = parry_items
    M[prefix + "parry"] = move(9, 1.5, "none",
        [(0, g, a), (1.5, [0.9, -0.4, 0.25], [30, -40, 0]), (4, [0.85, -0.6, 0.2], [60, -10, 0]), (7, idle_fp[0], idle_fp[1])],
        [(0, m, o, b, it), (1.5, [-110, 35, 0], [-80, 40, 0], [0, 18], p1), (4, [-80, 20, 0], [-60, 40, 0], [0, 10], p2),
         (7, idle_tp[0], idle_tp[1], [0, 0], p3)],
        interrupt=4, blend_in=0.5, blend_out=2.5)
    body(prefix + "parry",
        legs=[(0,) + GUARD_LEGS, (1.5, [-24, 0, 5], [18, 0, -5], 10, 0.06), (4, [-20, 0, 5], [16, 0, -5], 8, 0.05), (7, [-12, 0, 5], [12, 0, -5], 4, 0.03)],
        cam=[(0, [0, 0, 0]), (1.5, [0.03, 2, -2]), (5, [0, 0, 0])])
    M[prefix + "guard_break"] = move(16, 1, "none",
        [(0, g, a), (1.5, [1.2, -0.1, -0.1], [110, -40, 0]), (6, [1.15, -0.3, -0.05], [100, -30, 0]), (12, idle_fp[0], idle_fp[1])],
        [(0, m, o, b, it), (1.5, [-150, 40, 15], [-30, 60, 30], [-12, 15], break_item), (6, [-120, 35, 15], [-25, 55, 25], [-10, 12], break_item),
         (12, idle_tp[0], idle_tp[1], [0, 0], idle_tp[2])],
        interrupt=12, trail=False, blend_in=0.5, blend_out=4.0)
    body(prefix + "guard_break",
        legs=[(0,) + GUARD_LEGS, (1.5, [4, 0, 6], [30, 0, -8], -14, 0.04), (4, [20, 0, 6], [-10, 0, -6], -10, 0.1),
              (9, [-8, 0, 5], [14, 0, -5], 4, 0.06), (12, [-10, 0, 4], [12, 0, -5], 3, 0.0)],
        root=[(0, [0, 0, 0]), (2, [-8, 4, 0]), (6, [-5, 2, 0]), (11, [0, 0, 0])],
        cam=[(0, [0, 0, 0]), (1.5, [0.05, -8, 4]), (6, [0.1, -4, 2]), (12, [0, 0, 0])])


reactions("", GUARD_FP, GUARD_TP, ([1.0, -0.9, 0.05], [82, 18, 0]), ([-40, -15, 0], [-45, 35, 0], [35, 0, -10]),
          [0, 0, 62], ([0, 0, 10], [-20, 0, 0], [20, 0, -10]), [30, 0, 0])
reactions("staff_", STAFF_GUARD_FP, STAFF_GUARD_TP, ([0.35, -0.75, 0.3], [22, 88, 0]), ([-45, -20, 0], [-45, 40, 0], [0, 0, 80]),
          [0, 0, 36], ([0, 0, -40], [0, 0, -5], [0, 0, 80]), [0, 0, 120])

# ---------------- Phase 7: new styles ----------------
# Standhaft (defensive): compact, economical cuts close to the body, low wide stance.
# Wendig (agile): fast, springy cuts with turns and hops, the blade carried high.
import copy


def derive(src, name, time=1.0, amp=1.0, fp_rest=None, tp_rest=None, **kw):
    """A copy of a move, faster or slower (time) and smaller or bigger (amp) around a rest pose."""
    m = copy.deepcopy(M[src])
    # The body of the source move is left out, each new move gets its own footwork.
    for frames, keys in ((m["first_person"], ("camera",)), (m["third_person"], ("main_leg", "off_leg", "lean", "crouch", "root"))):
        for f in frames:
            for k in keys:
                f.pop(k, None)
        frames[:] = [f for f in frames if len(f) > 1]
    m["duration"] = round(m["duration"] * time, 2)
    m["strike"] = round(m["strike"] * time, 2)
    for k in ("interrupt", "blend_out"):
        if k in m:
            m[k] = round(m[k] * time, 2)
    for f in m["first_person"]:
        f["t"] = round(f["t"] * time, 2)
        if fp_rest:
            for key, rest in zip(("grip", "aim"), fp_rest):
                f[key] = [round(r + (v - r) * amp, 3) for v, r in zip(f[key], rest)]
    for f in m["third_person"]:
        f["t"] = round(f["t"] * time, 2)
        if tp_rest:
            for key, rest in zip(("main_arm", "off_arm", "item"), tp_rest):
                if key in f:
                    f[key] = [round(r + (v - r) * amp, 1) for v, r in zip(f[key], rest)]
            if "body" in f:
                f["body"] = [round(v * amp, 1) for v in f["body"]]
    m.update(kw)
    M[name] = m


DEF_FP = ([0.6, -0.75, 0.15], [88, 6, 0])
DEF_TP = ([-62, -22, 0], [-58, 32, 0], [25, 0, 0])
for src, name, time, amp in (("flow_diagonal", "def_cut", 0.85, 0.7), ("flow_backhand", "def_backhand", 0.85, 0.7),
                             ("flow_stab", "def_riposte", 0.8, 0.85), ("heavy_rising", "def_rising", 0.75, 0.7),
                             ("flow_spin", "def_spin", 0.95, 0.85)):
    derive(src, name, time, amp, DEF_FP, DEF_TP)
step("def_cut", 2.55, 5.5, lead="off", front=-16, back=14, lean=6, side=8, crouch=0.06, prep_crouch=0.06)
step("def_backhand", 2.1, 5.1, lead="main", front=-14, back=12, lean=5, side=8, crouch=0.06, prep_crouch=0.06)
step("def_riposte", 2.4, 4.4, lead="main", front=-34, back=26, lean=14, side=8, crouch=0.07, prep_crouch=0.06)
step("def_rising", 3.0, 6.0, lead="off", front=-16, back=14, lean=0, prep=8, side=8, crouch=0.04, prep_crouch=0.08)
body("def_spin", legs=[
    (0, [-8, 0, 9], [10, 0, -9], 6, 0.06), (2, [6, 0, 10], [-6, 0, -10], 8, 0.08), (4.75, [-10, 0, 10], [12, 0, -10], 10, 0.1),
    (7.6, [8, 0, 9], [-8, 0, -9], 8, 0.08), (9.5, [-10, 0, 9], [12, 0, -9], 6, 0.06)])

AGI_FP = ([1.05, -0.65, 0.0], [100, -10, 0])
AGI_TP = ([-115, -10, 10], [-35, 30, 10], [40, 0, -10])
for src, name, time, amp in (("flow_diagonal", "agile_slash", 0.75, 1.1), ("flow_backhand", "agile_backhand", 0.75, 1.1),
                             ("flow_rising", "agile_rising", 0.75, 1.15), ("flow_spin", "agile_twirl", 0.7, 1.0),
                             ("flow_stab", "agile_leap", 0.8, 1.0)):
    derive(src, name, time, amp, AGI_FP, AGI_TP)
step("agile_slash", 2.25, 4.9, lead="off", front=-26, back=20, lean=12, prep=-4, side=3, crouch=0.04, prep_crouch=0.08)
step("agile_backhand", 1.9, 4.5, lead="main", front=-24, back=18, lean=10, side=3, crouch=0.04)
body("agile_rising",
    legs=[(0, [-10, 0, 3], [10, 0, -3], 8, 0.12), (1.1, [-30, 0, 4], [-10, 0, -4], 4, 0.0), (1.9, [-50, 0, 4], [-30, 0, -4], 0, 0.0),
          (3.0, [-40, 0, 4], [-20, 0, -4], 4, 0.0), (4.5, [-16, 0, 3], [14, 0, -3], 6, 0.03)],
    root=[(0, [0, 0, 0]), (1.1, [0, 0, 0.1]), (1.9, [-6, 0, 0.3]), (3.0, [-3, 0, 0.18]), (4.5, [0, 0, 0])])
body("agile_twirl", legs=[
    (0, [-14, 0, 4], [12, 0, -4], 6, 0.03), (1.4, [8, 0, 6], [-8, 0, -6], 8, 0.05), (3.5, [-12, 0, 6], [12, 0, -6], 10, 0.06),
    (5.6, [8, 0, 6], [-8, 0, -6], 8, 0.05), (7, [-18, 0, 4], [14, 0, -4], 6, 0.03)],
    root=[(0, [0, 0, 0]), (1.4, [0, 0, 0.12]), (3.5, [0, 0, 0.18]), (5.6, [0, 0, 0.08]), (7, [0, 0, 0])])
body("agile_leap",
    legs=[(0, [-6, 0, 3], [8, 0, -3], 2, 0.1), (1.2, [-40, 0, 4], [30, 0, -4], 10, 0.0), (2.4, [-50, 0, 4], [36, 0, -4], 18, 0.0),
          (4.4, [-34, 0, 4], [26, 0, -4], 14, 0.06)],
    root=[(0, [0, 0, 0]), (1.2, [6, 0, 0.25]), (2.4, [10, 0, 0.3]), (3.6, [6, 0, 0.1]), (4.4, [0, 0, 0])])

# Front flip with a downward cut, the agile jump attack.
M["agile_flip"] = move(12, 6, "swing",
    [(0, [1.0, -0.4, 0.0], [110, -10, 0]), (2, [0.6, -0.1, 0.1], [130, 0, 0]), (5, [0.2, -0.2, 0.25], [60, 5, 0]),
     (6, [0.05, -0.3, 0.25], [20, 5, 0]), (7.5, [0.0, -0.6, 0.25], [-25, 5, 0]), (9.5, [0.05, -0.75, 0.2], [-35, 5, 0])],
    [(0, [-120, -10, 10], [-40, 30, 10], [0, 0], [40, 0, -10]), (2, [-170, -5, 0], [-165, 30, 0], [0, 0], [20, 0, 0]),
     (5, [-165, -5, 0], [-160, 30, 0], [0, 0], [0, 0, 0]), (6, [-95, -10, 0], [-95, 30, 0], [10, 0], [-70, 0, 0]),
     (7.5, [-55, -10, 0], [-55, 25, 0], [15, 0], [-85, 0, 0]), (9.5, [-45, -10, 0], [-45, 25, 0], [12, 0], [-85, 0, 0])],
    interrupt=9, blend_in=1.0, blend_out=3.0)
body("agile_flip",
    legs=[(0, [-10, 0, 3], [10, 0, -3], 6, 0.1), (2, [-60, 0, 4], [-50, 0, -4], 30, 0.0), (4, [-110, 0, 5], [-105, 0, -5], 60, 0.0),
          (6, [-60, 0, 5], [-40, 0, -5], 30, 0.0), (8, [-30, 0, 4], [24, 0, -4], 16, 0.08), (9.5, [-24, 0, 4], [20, 0, -4], 14, 0.06)],
    root=[(0, [0, 0, 0]), (2, [70, 0, 0.4]), (4, [200, 0, 0.6]), (6, [320, 0, 0.3]), (7, [360, 0, 0])],
    cam=[(0, [0, 0, 0]), (2, [0.1, 12, 0]), (4, [0.15, 18, 0]), (6, [0.2, 6, 0]), (8, [0.1, 2, 0]), (10, [0, 0, 0])])


# Rolls and flips turn the figure around its middle. The lift of every root keyframe is set here so
# that the lowest point of the body (head, hands, feet) just touches the ground, plus the authored
# lift, which is the height above the ground (for flips in the air). Uses the same math as the
# preview tool, which matches SaberCombat.applyRoot.
import importlib.util, os, sys
_spec = importlib.util.spec_from_file_location("preview_moves", os.path.join(os.path.dirname(os.path.abspath(__file__)), "preview_moves.py"))
preview = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(preview)


def pose_at(frames, t):
    pose = {}
    for key in ("main_arm", "off_arm", "body", "item", "main_leg", "off_leg", "root"):
        v = preview.sample(frames, key, t, 3)
        if v is not None:
            pose[key] = list(v)
    for key in ("spin", "lean", "crouch"):
        v = preview.sample(frames, key, t, 1)
        if v is not None:
            pose[key] = float(preview.np.ravel(v)[0])
    return pose


def ground(name):
    """Replaces the root keyframes by one per tick, each lifted so the body rests on the ground."""
    frames = M[name]["third_person"]
    keyed = [f for f in frames if "root" in f]
    last = keyed[-1]["t"]
    dense = []
    t = 0.0
    while t <= last + 1e-6:
        pose = pose_at(frames, t)
        air = pose["root"][2]
        pose["root"] = [pose["root"][0], pose["root"][1], 0.0]
        pts, blades_, _ = preview.tp_points(pose, False)
        low = min(p[1] for k, p in pts.items() if k != "head")
        low = min(low, pts["head"][1] - 0.25)
        dense.append((t, [round(pose["root"][0], 2), round(pose["root"][1], 2), round(air - low + 0.02, 3)]))
        t += 1.0 if t + 1.0 <= last or t == last else last - t
    for f in keyed:
        del f["root"]
    frames[:] = [f for f in frames if len(f) > 1]
    for t, r in dense:
        put(frames, t, root=r)


for name in ("dodge_roll", "dodge_roll_right", "dodge_roll_left", "staff_roll", "dodge_backflip", "agile_flip"):
    ground(name)

styles = {
    "sabercraft:flowing": {
        "combo": ["flow_diagonal", "flow_backhand", "flow_rising", "flow_overhead"],
        "sprint": "flow_stab", "jump": "flow_overhead", "special": "flow_spin", "combo_window": 16,
        "idle_first_person": {"grip": [1.0, -0.9, 0.05], "aim": [82, 18, 0]},
        "idle_third_person": {"main_arm": [-40, -15, 0], "off_arm": [-45, 35, 0], "item": [35, 0, -10]},
        "guard_first_person": {"grip": [0.45, -0.55, 0.15], "aim": [38, 78, 0]},
        "guard_third_person": {"main_arm": [-95, -20, 0], "off_arm": [-90, 40, 0], "body": [0, -10], "item": [0, 0, 55]}},
    "sabercraft:heavy": {
        "combo": ["heavy_overhead", "heavy_wide", "heavy_rising", "heavy_spin"],
        "sprint": "heavy_thrust", "jump": "heavy_overhead", "special": "heavy_spin", "combo_window": 20,
        "idle_first_person": {"grip": [0.95, -0.75, 0.1], "aim": [100, -20, 0]},
        "idle_third_person": {"main_arm": [-55, 15, 10], "off_arm": [-55, 55, 0], "body": [0, 10], "item": [10, 0, -25]},
        "guard_first_person": {"grip": [0.45, -0.5, 0.15], "aim": [42, 80, 0]},
        "guard_third_person": {"main_arm": [-100, -20, 0], "off_arm": [-95, 40, 0], "body": [5, -10], "item": [0, 0, 60]}},
    "sabercraft:staff": {
        "combo": ["staff_sweep", "staff_twirl", "staff_reverse", "staff_spin"],
        "sprint": "staff_sweep", "jump": "staff_twirl", "special": "staff_whirl", "combo_window": 16,
        "idle_first_person": {"grip": [0.35, -0.75, 0.3], "aim": [22, 88, 0]},
        "idle_third_person": {"main_arm": [-45, -20, 0], "off_arm": [-45, 40, 0], "item": [0, 0, 80]},
        "guard_first_person": {"grip": [0.1, -0.45, 0.35], "aim": [65, 90, 0]},
        "guard_third_person": {"main_arm": [-95, -15, 0], "off_arm": [-92, 40, 0], "item": [0, 0, 30]}},
    "sabercraft:defensive": {
        "combo": ["def_cut", "def_backhand", "def_rising", "def_riposte"],
        "sprint": "def_riposte", "jump": "def_cut", "special": "def_spin", "combo_window": 18,
        "idle_first_person": {"grip": DEF_FP[0], "aim": DEF_FP[1]},
        "idle_third_person": {"main_arm": DEF_TP[0], "off_arm": DEF_TP[1], "item": DEF_TP[2]},
        "guard_first_person": {"grip": [0.38, -0.55, 0.18], "aim": [42, 80, 0]},
        "guard_third_person": {"main_arm": [-92, -22, 0], "off_arm": [-88, 38, 0], "body": [0, -6], "item": [0, 0, 60]}},
    "sabercraft:agile": {
        "combo": ["agile_slash", "agile_backhand", "agile_rising", "agile_twirl"],
        "sprint": "agile_leap", "jump": "agile_flip", "special": "agile_twirl", "combo_window": 14,
        "idle_first_person": {"grip": AGI_FP[0], "aim": AGI_FP[1]},
        "idle_third_person": {"main_arm": AGI_TP[0], "off_arm": AGI_TP[1], "body": [0, 12], "item": AGI_TP[2]},
        "guard_first_person": {"grip": [0.5, -0.55, 0.12], "aim": [36, 76, 0]},
        "guard_third_person": {"main_arm": [-96, -20, 0], "off_arm": [-60, 40, 0], "body": [0, -12], "item": [0, 0, 52]}},
}

# Phase 7: legs of the resting poses, running and jumping poses, dodges and landings.
LEGS = {
    "sabercraft:flowing": ({"main_leg": [-10, 0, 4], "off_leg": [12, 0, -5], "lean": 3},
                           {"main_leg": [-14, 0, 5], "off_leg": [16, 0, -6], "lean": 6, "crouch": 0.04}),
    "sabercraft:heavy": ({"main_leg": [14, 0, 6], "off_leg": [-12, 0, -6], "lean": 2},
                         {"main_leg": [16, 0, 7], "off_leg": [-16, 0, -7], "lean": 6, "crouch": 0.05}),
    "sabercraft:staff": ({"main_leg": [-6, 0, 10], "off_leg": [8, 0, -10], "lean": 4},
                         {"main_leg": [-8, 0, 12], "off_leg": [10, 0, -12], "lean": 8, "crouch": 0.05}),
    "sabercraft:defensive": ({"main_leg": [-8, 0, 9], "off_leg": [10, 0, -9], "lean": 5, "crouch": 0.05},
                             {"main_leg": [-10, 0, 10], "off_leg": [12, 0, -10], "lean": 7, "crouch": 0.07}),
    "sabercraft:agile": ({"main_leg": [-16, 0, 3], "off_leg": [14, 0, -3], "lean": 8, "crouch": 0.02},
                         {"main_leg": [-16, 0, 4], "off_leg": [16, 0, -4], "lean": 8, "crouch": 0.04}),
}
for sid, (idle, guard) in LEGS.items():
    styles[sid]["idle_third_person"].update(idle)
    styles[sid]["guard_third_person"].update(guard)
for sid in ("sabercraft:flowing", "sabercraft:heavy", "sabercraft:defensive", "sabercraft:agile"):
    styles[sid]["guard_hit"] = "guard_hit"
    styles[sid]["parry"] = "parry"
    styles[sid]["guard_break"] = "guard_break"
    styles[sid]["run_third_person"] = {"main_arm": [20, 5, 12], "item": [75, 0, 0], "lean": 14}
    styles[sid]["air_third_person"] = {"main_arm": [-70, -10, 5], "off_arm": [-35, 30, 25], "item": [30, 0, -10],
                                       "main_leg": [-30, 0, 4], "off_leg": [18, 0, -4], "lean": 4}
    styles[sid]["land"] = "land"
    styles[sid]["land_heavy"] = "land_heavy"
styles["sabercraft:staff"]["run_third_person"] = {"main_arm": [-25, -15, 0], "item": [0, 0, 80], "lean": 12}
styles["sabercraft:staff"]["air_third_person"] = {"main_arm": [-60, -15, 0], "off_arm": [-60, 35, 0], "item": [0, 0, 80],
                                                  "main_leg": [-30, 0, 6], "off_leg": [18, 0, -6], "lean": 4}
styles["sabercraft:staff"]["guard_hit"] = "staff_guard_hit"
styles["sabercraft:staff"]["parry"] = "staff_parry"
styles["sabercraft:staff"]["guard_break"] = "staff_guard_break"
styles["sabercraft:agile"]["run_third_person"] = {"main_arm": [30, 10, 15], "item": [80, 0, 0], "lean": 18}
styles["sabercraft:defensive"]["run_third_person"] = {"main_arm": [-30, -15, 0], "off_arm": [-20, 20, 0], "item": [60, 0, 0], "lean": 12}
styles["sabercraft:staff"]["land"] = "land"
styles["sabercraft:staff"]["land_heavy"] = "land_heavy"
styles["sabercraft:flowing"]["dodge"] = {"forward": "dodge_roll", "left": "dodge_roll_left", "right": "dodge_roll_right", "back": "dodge_backstep"}
styles["sabercraft:heavy"]["dodge"] = {"forward": "dodge_lunge", "left": "dodge_sidestep_left", "right": "dodge_sidestep_right", "back": "dodge_backstep"}
styles["sabercraft:defensive"]["dodge"] = {"forward": "dodge_lunge", "left": "dodge_sidestep_left", "right": "dodge_sidestep_right", "back": "dodge_backstep"}
styles["sabercraft:agile"]["dodge"] = {"forward": "dodge_spin_step", "left": "dodge_roll_left", "right": "dodge_roll_right", "back": "dodge_backflip"}
styles["sabercraft:staff"]["dodge"] = {"forward": "staff_roll", "left": "dodge_sidestep_left", "right": "dodge_sidestep_right", "back": "dodge_backstep"}
for s in styles.values():
    for k in ("combo",):
        s[k] = ["sabercraft:" + v for v in s[k]]
    for k in ("sprint", "jump", "special", "land", "land_heavy", "guard_hit", "parry", "guard_break"):
        if k in s:
            s[k] = "sabercraft:" + s[k]
    if "dodge" in s:
        s["dodge"] = {d: "sabercraft:" + v for d, v in s["dodge"].items()}
out = {"styles": styles, "moves": {"sabercraft:" + k: v for k, v in M.items()}}

text = json.dumps(out, indent=2)
# collapse number arrays onto one line
import re
text = re.sub(r'\[\s+([-\d.,\s]+?)\s+\]', lambda m: '[' + ', '.join(x.strip() for x in m.group(1).split(',')) + ']', text)
open('src/client/resources/assets/sabercraft/combat/moves.json', 'w').write(text + "\n")
print("ok", len(M))
