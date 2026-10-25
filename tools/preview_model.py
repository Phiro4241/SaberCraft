#!/usr/bin/env python3
"""
Offline preview of SaberCraft block/item models (cuboid JSON with textures and rotations), so new
models can be checked without starting Minecraft. A small software rasterizer with a depth buffer,
simple directional shading like Minecraft's, and glowing faces for light_emission.

    python3 tools/preview_model.py <model.json> <out.png> [yaw pitch] [--assets src/client/resources/assets]

Several views side by side: python3 tools/preview_model.py model.json out.png --views
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image

ASSETS = os.path.join("src", "client", "resources", "assets")


def load_texture(ref):
    ns, path = ref.split(":", 1) if ":" in ref else ("minecraft", ref)
    img = Image.open(os.path.join(ASSETS, ns, "textures", path + ".png")).convert("RGBA")
    return np.asarray(img).astype(float)


def rot_matrix(axis, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if axis == "x":
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == "y":
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def element_matrix(rot):
    if not rot:
        return np.eye(3), np.zeros(3)
    origin = np.array(rot["origin"], dtype=float)
    if "axis" in rot:
        m = rot_matrix(rot["axis"], rot["angle"])
    else:
        # Euler as in CuboidRotation.EulerXYZRotation: rotationZYX, so x is applied first.
        m = rot_matrix("z", rot.get("z", 0)) @ rot_matrix("y", rot.get("y", 0)) @ rot_matrix("x", rot.get("x", 0))
    return m, origin


# Corners of each face, in the order matching uv (u0,v0) top-left, (u1,v0) top-right, (u1,v1), (u0,v1).
def face_corners(f, a, b):
    x0, y0, z0 = a
    x1, y1, z1 = b
    return {
        "north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }[f]


NORMALS = {"north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}


def render(model, yaw, pitch, size=(640, 400), bg=(150, 170, 200), dark=False, quads=None):
    textures = {k: load_texture(v) for k, v in model.get("textures", {}).items() if not v.startswith("#")}
    W, H = size
    view = rot_matrix("x", pitch) @ rot_matrix("y", yaw)
    tris = []
    for el in model["elements"] if quads is None else []:
        m, origin = element_matrix(el.get("rotation"))
        a, b = el["from"], el["to"]
        glow = el.get("light_emission", 0)
        for f, face in el["faces"].items():
            tex = textures[face["texture"].lstrip("#")]
            corners = np.array(face_corners(f, a, b), dtype=float)
            corners = (corners - origin) @ m.T + origin
            normal = np.array(NORMALS[f], dtype=float) @ m.T
            u0, v0, u1, v1 = face.get("uv", [0, 0, 16, 16])
            uvs = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
            tris.append((corners, uvs, tex, normal, glow))
    if quads:
        tris = quads
    pts = np.concatenate([t[0] for t in tris])
    center = (pts.min(0) + pts.max(0)) / 2
    vp = (pts - center) @ view.T
    span = max(np.ptp(vp[:, 0]) / W, np.ptp(vp[:, 1]) / H) * 1.1
    scale = 1 / span
    img = np.zeros((H, W, 3)) + np.array(bg, dtype=float)
    zbuf = np.full((H, W), -1e9)
    light = np.array([0.3, 1.0, -0.6])
    light /= np.linalg.norm(light)
    for corners, uvs, tex, normal, glow in tris:
        n = normal @ view.T
        if n[2] <= 1e-6:
            # facing away (camera looks along -z of view space? we keep faces with n.z > 0)
            continue
        p = (corners - center) @ view.T
        sx = p[:, 0] * scale + W / 2
        sy = -p[:, 1] * scale + H / 2
        sz = p[:, 2]
        # Minecraft-like shading: up 1.0, down 0.5, north/south 0.8, east/west 0.6.
        ax = np.abs(normal)
        shade = 0.6 * ax[0] ** 2 + (1.0 if normal[1] > 0 else 0.5) * ax[1] ** 2 + 0.8 * ax[2] ** 2
        if dark:
            shade *= 0.25
        if glow:
            shade = max(shade, glow / 15.0 * (1.2 if dark else 1.0))
        th, tw = tex.shape[:2]
        for tri in ((0, 1, 2), (0, 2, 3)):
            xs, ys, zs = sx[list(tri)], sy[list(tri)], sz[list(tri)]
            us = np.array([uvs[i][0] for i in tri]) / 16 * tw
            vs = np.array([uvs[i][1] for i in tri]) / 16 * th
            minx, maxx = int(max(0, math.floor(xs.min()))), int(min(W - 1, math.ceil(xs.max())))
            miny, maxy = int(max(0, math.floor(ys.min()))), int(min(H - 1, math.ceil(ys.max())))
            if minx > maxx or miny > maxy:
                continue
            gx, gy = np.meshgrid(np.arange(minx, maxx + 1) + 0.5, np.arange(miny, maxy + 1) + 0.5)
            d = (ys[1] - ys[2]) * (xs[0] - xs[2]) + (xs[2] - xs[1]) * (ys[0] - ys[2])
            if abs(d) < 1e-9:
                continue
            w0 = ((ys[1] - ys[2]) * (gx - xs[2]) + (xs[2] - xs[1]) * (gy - ys[2])) / d
            w1 = ((ys[2] - ys[0]) * (gx - xs[2]) + (xs[0] - xs[2]) * (gy - ys[2])) / d
            w2 = 1 - w0 - w1
            inside = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
            z = w0 * zs[0] + w1 * zs[1] + w2 * zs[2]
            sub = zbuf[miny:maxy + 1, minx:maxx + 1]
            mask = inside & (z > sub)
            if not mask.any():
                continue
            u = np.clip((w0 * us[0] + w1 * us[1] + w2 * us[2]).astype(int), 0, tw - 1)
            v = np.clip((w0 * vs[0] + w1 * vs[1] + w2 * vs[2]).astype(int), 0, th - 1)
            texel = tex[v, u]
            mask &= texel[:, :, 3] > 10
            sub[mask] = z[mask]
            block = img[miny:maxy + 1, minx:maxx + 1]
            block[mask] = texel[mask][:, :3] * shade
    return np.clip(img, 0, 255).astype(np.uint8)


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    model = json.load(open(args[0]))
    out = args[1]
    if "--views" in sys.argv:
        dark = "--dark" in sys.argv
        views = [(-90, 0), (90, 0), (-130, 25), (-45, 20), (180, 10), (0, -60)]
        tiles = [render(model, y, p, (480, 300), dark=dark) for y, p in views]
        top = np.concatenate(tiles[:3], axis=1)
        bottom = np.concatenate(tiles[3:], axis=1)
        Image.fromarray(np.concatenate([top, bottom], axis=0)).save(out)
    else:
        yaw = float(args[2]) if len(args) > 2 else -120
        pitch = float(args[3]) if len(args) > 3 else 20
        Image.fromarray(render(model, yaw, pitch)).save(out)


if __name__ == "__main__":
    main()
