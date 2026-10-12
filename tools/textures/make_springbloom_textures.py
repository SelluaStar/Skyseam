"""The Springbloom's textures (docs/DECISIONS.md K98): a bouncy, glowing Halcyon plant, after the author's three
reference images of glowing launch plants (kept locally, not in the repository), in Minecraft's pixel style.

Writes, under src/main/resources/assets/skyseam/:
  textures/block/springbloom.png            128x64 model texture, painted onto the box-UV layout read from
                                             art/models/springbloom.bbmodel
  textures/block/springbloom_glowmask.png   what glows: the leaves' veins, the inner leaves' veins and rims, the whole
                                             bulb (its ribs and sparkles brightest), the stalks' upper halves, the tips
  textures/block/springbloom_particle.png   16x16, for break particles
  textures/item/springbloom.png             16x16 inventory icon
  textures/particle/springbloom_spore_{0,1}.png  8x8, a glowing spore cube, two frames
and a scaled-up contact sheet in build/previews/springbloom_textures.png.

Teal leaves round a violet bulb, violet-pink inner leaves, lavender stalks with pink-white tips: the Halcyon's palette
(palette.py). Deterministic. Replace any file by name (docs/ASSET-PATHS.md).
"""
import json
import random
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import palette as P  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "skyseam"
MODEL = ROOT / "art" / "models" / "springbloom.bbmodel"
PREVIEW = ROOT / "build" / "previews"

LEAF = [(22, 58, 72), (30, 84, 92), (40, 116, 116), (62, 152, 140), (104, 196, 172)]
VEIN = P.PRISM_TEAL
PETAL = [(70, 44, 140), (108, 64, 186), (150, 92, 222), (198, 132, 238), (240, 176, 240)]
PETAL_VEIN = (255, 220, 250)
BULB = [(54, 40, 128), (82, 58, 170), (118, 84, 214), (156, 124, 244), (196, 170, 255)]
RIB = (176, 156, 250)
SPARKLE = P.PEARL
STALK_LOW = (52, 112, 120)
STALK_HIGH = (200, 176, 252)
TIP_CORE = (255, 244, 255)
TIP_EDGE = (255, 150, 214)
BASE = (34, 66, 58)
CLEAR = (0, 0, 0, 0)


def mix(a, b, t):
    return tuple(int(round(a[k] * (1 - t) + b[k] * t)) for k in range(3))


def box_faces(cube):
    """Face rectangles (x, y, w, h) of a box-UV cube, by Blockbench face name."""
    fx, fy, fz = cube["from"]
    tx, ty, tz = cube["to"]
    w, h, d = int(round(tx - fx)), int(round(ty - fy)), int(round(tz - fz))
    u, v = cube.get("uv_offset", [0, 0])
    return {
        "up": (u + d, v, w, d),
        "down": (u + d + w, v, w, d),
        "east": (u, v + d, d, h),
        "north": (u + d, v + d, w, h),
        "west": (u + d + w, v + d, d, h),
        "south": (u + 2 * d + w, v + d, w, h),
    }


def leaf_shape(w, d, x, z):
    """How far inside a leaf's outline a texel is (negative outside): narrow at its base (z = 0), widest past halfway,
    pointed at its tip (z = d - 1)."""
    t = (z + 0.5) / d
    half = (w / 2) * (0.5 + 0.7 * (1 - abs(t - 0.5) / 0.5) ** 0.6)
    half = min(half, w / 2) * (1 - max(0, t - 0.78) / 0.22 * 0.9)
    return half - abs(x + 0.5 - w / 2)


def paint_leaf(tex, glow, rect, colours, vein, flip, rng, petal):
    x0, y0, w, d = rect
    for z in range(d):
        for x in range(w):
            zz = d - 1 - z if flip else z
            inside = leaf_shape(w, d, x, zz)
            if inside < 0:
                continue
            t = zz / max(1, d - 1)
            edge = inside < 1.0
            mid = abs(x + 0.5 - w / 2) < 0.6
            tone = 2 + (1 if t > 0.5 else 0) - (1 if rng.random() < 0.15 else 0)
            if edge:
                tone = 3 if not petal else 4
            colour = colours[max(0, min(len(colours) - 1, tone))]
            if mid and zz < d - 1:
                colour = vein
                glow.putpixel((x0 + x, y0 + z), vein + (255,))
            elif petal and edge:
                glow.putpixel((x0 + x, y0 + z), colour + (255,))
            elif not petal and (zz + x) % 4 == 0 and abs(x + 0.5 - w / 2) < w / 2 - 1.5 and rng.random() < 0.5:
                # Side veins running off the midrib.
                colour = mix(colour, vein, 0.55)
                glow.putpixel((x0 + x, y0 + z), colour + (160,))
            tex.putpixel((x0 + x, y0 + z), colour + (255,))


def paint_bulb_side(tex, glow, rect, rng, row_offset):
    x0, y0, w, h = rect
    for y in range(h):
        for x in range(w):
            depth = (y + row_offset) / 7
            tone = 3 if depth < 0.3 else 2 if depth < 0.7 else 1
            colour = BULB[tone]
            if (x + 2) % 4 == 0:
                colour = RIB
            if rng.random() < 0.025:
                colour = SPARKLE
            tex.putpixel((x0 + x, y0 + y), colour + (255,))
            glow.putpixel((x0 + x, y0 + y), colour + (255 if colour in (RIB, SPARKLE) else 150,))


def paint_bulb_top(tex, glow, rect, rng, bright):
    x0, y0, w, h = rect
    cx, cy = w / 2, h / 2
    for y in range(h):
        for x in range(w):
            r = max(abs(x + 0.5 - cx) / cx, abs(y + 0.5 - cy) / cy)
            tone = 4 if r < 0.3 and bright else 3 if r < 0.6 else 2
            colour = BULB[tone]
            if abs(x + 0.5 - cx) < 0.6 or abs(y + 0.5 - cy) < 0.6:
                colour = mix(colour, RIB, 0.4)
            if rng.random() < 0.025:
                colour = SPARKLE
            tex.putpixel((x0 + x, y0 + y), colour + (255,))
            glow.putpixel((x0 + x, y0 + y), colour + (255 if tone == 4 or colour == SPARKLE else 170,))


def fill(tex, glow, rect, colour, glow_alpha=0):
    x0, y0, w, h = rect
    for y in range(h):
        for x in range(w):
            tex.putpixel((x0 + x, y0 + y), colour + (255,))
            if glow_alpha:
                glow.putpixel((x0 + x, y0 + y), colour + (glow_alpha,))


def paint_model():
    model = json.loads(MODEL.read_text(encoding="utf-8"))
    tex = Image.new("RGBA", (128, 64), CLEAR)
    glow = Image.new("RGBA", (128, 64), CLEAR)
    rng = random.Random(9811)
    done = set()
    for cube in model["elements"]:
        name = cube["name"]
        key = tuple(cube.get("uv_offset", [0, 0]))
        if key in done:
            continue
        done.add(key)
        faces = box_faces(cube)
        if name.endswith("_blade"):
            petal = name.startswith("leaf_inner")
            colours = PETAL if petal else LEAF
            vein = PETAL_VEIN if petal else VEIN
            paint_leaf(tex, glow, faces["up"], colours, vein, False, rng, petal)
            paint_leaf(tex, glow, faces["down"], [tuple(int(c * 0.75) for c in col) for col in colours], vein, False, rng, petal)
            # The sides stay clear, so each leaf reads as a thin blade.
        elif name.startswith("bulb_base"):
            for rect in faces.values():
                fill(tex, glow, rect, BASE)
        elif name.startswith(("bulb_", "bulge")):
            # The bulb's layers, darker towards its foot: low, belly (with the bulges), upper, top.
            row = {"bulb_low": 5, "bulb_belly": 2, "bulb_upper": 1, "bulb_top": 0}.get(name, 2)
            for face in ("north", "south", "east", "west"):
                paint_bulb_side(tex, glow, faces[face], rng, row)
            paint_bulb_top(tex, glow, faces["up"], rng, name == "bulb_top")
            fill(tex, glow, faces["down"], BULB[0])
        elif name.endswith("_lower"):
            for face, (x0, y0, w, h) in faces.items():
                for y in range(h):
                    for x in range(w):
                        t = 1 - y / max(1, h - 1) if face not in ("up", "down") else 0.5
                        colour = mix(STALK_LOW, STALK_HIGH, 0.15 + 0.6 * t)
                        tex.putpixel((x0 + x, y0 + y), colour + (255,))
        elif name.endswith("_rod"):
            for face, (x0, y0, w, h) in faces.items():
                for y in range(h):
                    for x in range(w):
                        t = 1 - y / max(1, h - 1) if face not in ("up", "down") else 0.8
                        colour = mix(STALK_HIGH, TIP_EDGE, 0.5 * t)
                        tex.putpixel((x0 + x, y0 + y), colour + (255,))
                        glow.putpixel((x0 + x, y0 + y), colour + (int(120 + 135 * t),))
        elif name.endswith("_gem"):
            for face, (x0, y0, w, h) in faces.items():
                for y in range(h):
                    for x in range(w):
                        colour = TIP_CORE if (x + y) % 2 == 0 else TIP_EDGE
                        tex.putpixel((x0 + x, y0 + y), colour + (255,))
                        glow.putpixel((x0 + x, y0 + y), colour + (255,))
    return tex, glow


def particle():
    img = Image.new("RGBA", (16, 16), CLEAR)
    rng = random.Random(9812)
    for y in range(16):
        for x in range(16):
            colour = BULB[rng.choice((1, 2, 2, 3))]
            if (x + 1) % 4 == 0:
                colour = RIB
            img.putpixel((x, y), colour + (255,))
    return img


def icon():
    """The plant from the side: teal leaves fanning out, the violet bulb on them, three stalks with glowing tips."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    px = img.putpixel
    # Leaves.
    for x, y, tone in ((1, 12, 2), (2, 12, 3), (2, 11, 3), (3, 11, 2), (3, 12, 1), (4, 12, 2), (13, 11, 2), (12, 11, 3), (12, 12, 2),
                       (13, 12, 3), (14, 12, 2), (11, 12, 1), (0, 13, 1), (15, 13, 1), (1, 13, 2), (14, 13, 2), (2, 13, 3), (13, 13, 3)):
        px((x, y), LEAF[tone] + (255,))
    for x in range(3, 13):
        px((x, 13), LEAF[2 if x % 3 else 3] + (255,))
        px((x, 14), LEAF[1] + (255,))
    for x in (4, 7, 8, 11):
        px((x, 13), VEIN + (255,))
    # The bulb.
    rows = [(5, 10, 9), (4, 11, 10), (4, 11, 11), (5, 10, 12)]
    for x0, x1, y in rows:
        for x in range(x0, x1 + 1):
            colour = BULB[3] if y <= 10 else BULB[2]
            if (x - x0) % 3 == 2:
                colour = RIB
            px((x, y), colour + (255,))
    px((7, 9), BULB[4] + (255,))
    px((8, 9), BULB[4] + (255,))
    # Stalks and tips.
    for (x, y) in ((5, 8), (5, 7), (4, 6), (4, 5)):
        px((x, y), STALK_HIGH + (255,))
    for (x, y) in ((8, 8), (8, 7), (8, 6), (8, 5), (8, 4), (8, 3)):
        px((x, y), STALK_HIGH + (255,))
    for (x, y) in ((10, 8), (11, 7), (11, 6), (12, 5)):
        px((x, y), STALK_HIGH + (255,))
    for (x, y) in ((3, 3), (4, 3), (3, 4), (4, 4), (7, 1), (8, 1), (7, 2), (8, 2), (12, 3), (13, 3), (12, 4), (13, 4)):
        px((x, y), (TIP_CORE if (x + y) % 2 == 0 else TIP_EDGE) + (255,))
    return img


def spore(frame):
    img = Image.new("RGBA", (8, 8), CLEAR)
    if frame == 0:
        cells = {(3, 3): TIP_CORE, (4, 3): TIP_EDGE, (3, 4): TIP_EDGE, (4, 4): TIP_CORE}
        ring = [(2, 3), (2, 4), (5, 3), (5, 4), (3, 2), (4, 2), (3, 5), (4, 5)]
    else:
        cells = {(x, y): TIP_CORE if (x + y) % 2 == 0 else TIP_EDGE for x in range(2, 6) for y in range(2, 6)}
        ring = [(1, 3), (1, 4), (6, 3), (6, 4), (3, 1), (4, 1), (3, 6), (4, 6)]
    for (x, y), colour in cells.items():
        img.putpixel((x, y), colour + (255,))
    for (x, y) in ring:
        img.putpixel((x, y), TIP_EDGE + (120,))
    return img


def main():
    tex, glow = paint_model()
    out = {
        ASSETS / "textures" / "block" / "springbloom.png": tex,
        ASSETS / "textures" / "block" / "springbloom_glowmask.png": glow,
        ASSETS / "textures" / "block" / "springbloom_particle.png": particle(),
        ASSETS / "textures" / "item" / "springbloom.png": icon(),
        ASSETS / "textures" / "particle" / "springbloom_spore_0.png": spore(0),
        ASSETS / "textures" / "particle" / "springbloom_spore_1.png": spore(1),
    }
    for path, img in out.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        img.save(path)
        print("wrote", path.relative_to(ROOT))
    scale = 6
    scale = 4
    sheet = Image.new("RGBA", (128 * scale * 2 + 40 + 16 * 6, 64 * scale + 20), (24, 22, 40, 255))
    sheet.alpha_composite(tex.resize((128 * scale, 64 * scale), Image.NEAREST), (10, 10))
    sheet.alpha_composite(glow.resize((128 * scale, 64 * scale), Image.NEAREST), (20 + 128 * scale, 10))
    sheet.alpha_composite(icon().resize((16 * 6, 16 * 6), Image.NEAREST), (30 + 256 * scale, 10))
    sheet.alpha_composite(spore(1).resize((8 * 6, 8 * 6), Image.NEAREST), (30 + 256 * scale, 30 + 16 * 6))
    PREVIEW.mkdir(parents=True, exist_ok=True)
    sheet.save(PREVIEW / "springbloom_textures.png")
    print("wrote", (PREVIEW / "springbloom_textures.png").relative_to(ROOT))


if __name__ == "__main__":
    main()
