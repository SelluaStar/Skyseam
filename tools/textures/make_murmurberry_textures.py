"""The Murmurberry's textures (docs/DECISIONS.md K106): a cluster of balloon-like berries that light up for a moment
when something nearby makes a vibration (as a sculk sensor would hear it), in Minecraft's pixel style.

Writes, under src/main/resources/assets/skyseam/:
  textures/block/murmurberry.png              64x64 model texture, unlit: each berry a translucent grey shell, like a
                                              balloon's skin with nothing lit inside (a sheen on its upper side, its
                                              rim a little darker, a dim core showing through), on grey-green stalks
                                              from a mossy pad with four little leaves. Painted onto the box-UV layout
                                              read from art/models/murmurberry.bbmodel.
  textures/block/murmurberry_lit_{0..3}.png   the same layout lit, drawn added over the unlit one while it glows, in
                                              four colourways of birthday-cake pastels blended into each other and
                                              flecked with sprinkles, whitest in the middle of each face and most
                                              coloured at its rim, like a balloon lit from inside: 0 vanilla frosting
                                              (pink and sky), 1 strawberry (pink and peach), 2 mint (mint and sky),
                                              3 lemon-lavender. The stalks glow faintly; the pad and leaves not at all
                                              (black adds nothing).
  textures/block/murmurberry_halo.png         16x16, a soft round glow in steps, white, drawn round each lit berry
  textures/block/murmurberry_particle.png     16x16, for break particles
  textures/item/murmurberry.png               16x16 inventory icon
and a contact sheet in build/previews/murmurberry_textures.png. Deterministic.
"""
import json
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "skyseam"
MODEL = ROOT / "art" / "models" / "murmurberry.bbmodel"
PREVIEW = ROOT / "build" / "previews"
SIZE = 64
CLEAR = (0, 0, 0, 0)

# Unlit: grey glass and grey-green growth.
SHELL = (172, 178, 192)
SHELL_RIM = (128, 134, 150)
SHEEN = (242, 246, 252)
CORE_DIM = (110, 116, 132)
KNOT = (146, 152, 166)
STALK_LOW = (64, 82, 78)
STALK_HIGH = (128, 146, 140)
MOSS = [(42, 58, 52), (54, 74, 64), (70, 94, 80)]
LEAF = [(66, 96, 90), (84, 120, 112), (112, 150, 138)]

# Lit: birthday-cake cream, pastels and sprinkles.
CREAM = (255, 246, 234)
CORE_LIT = (255, 253, 246)
PINK = (255, 168, 206)
SKY = (166, 206, 255)
MINT = (160, 240, 204)
PEACH = (255, 192, 160)
LEMON = (255, 228, 134)
LAVENDER = (204, 178, 255)
SPRINKLES = [(255, 120, 178), (110, 182, 255), (255, 214, 80), (110, 226, 170), (186, 140, 255)]
COLOURWAYS = [(PINK, SKY), (PINK, PEACH), (MINT, SKY), (LEMON, LAVENDER)]


def mix(a, b, t):
    return tuple(int(round(a[k] * (1 - t) + b[k] * t)) for k in range(3))


def scale(c, s):
    return tuple(max(0, min(255, int(round(v * s)))) for v in c)


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


def blobs(rng, w, h):
    """A soft blend field 0..1 over a w by h face: a few round blobs of the second pastel in the first."""
    centres = [(rng.uniform(-0.5, w - 0.5), rng.uniform(-0.5, h - 0.5), rng.uniform(1.2, 2.6)) for _ in range(2)]
    field = {}
    for y in range(h):
        for x in range(w):
            value = 0.0
            for cx, cy, r in centres:
                d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
                value = max(value, 1 - d / r)
            field[(x, y)] = max(0.0, min(1.0, value))
    return field


def paint_shell(off, lit, rect, face, rng, colours):
    x0, y0, w, h = rect
    first, second = colours
    field = blobs(rng, w, h)
    for y in range(h):
        for x in range(w):
            edge = x == 0 or y == 0 or x == w - 1 or y == h - 1
            # Unlit: thin see-through grey skin (three of them cross in each berry), darker at its rim and underneath,
            # a sheen on its upper side.
            colour, alpha = SHELL, 62
            if edge:
                colour, alpha = SHELL_RIM, 118
            if face == "down":
                colour, alpha = SHELL_RIM, 110
            sheen = face in ("north", "east", "up") and (x + y) in (1, 2) and not (face == "up" and edge)
            if sheen:
                colour, alpha = SHEEN, 200
            off.putpixel((x0 + x, y0 + y), colour + (alpha,))
            # Lit: the colourway's two pastels blended into each other, whitened towards the middle of the face (lit from
            # inside), flecked with sprinkles.
            t = field[(x, y)]
            out = max(abs(x - (w - 1) / 2) / max(0.5, (w - 1) / 2), abs(y - (h - 1) / 2) / max(0.5, (h - 1) / 2))
            glow = mix(mix(first, second, t * 0.9), CREAM, 0.5 * (1 - out) ** 1.5)
            if rng.random() < 0.08:
                glow = rng.choice(SPRINKLES)
            if sheen:
                glow = CORE_LIT
            if face == "down":
                glow = scale(glow, 0.8)
            lit.putpixel((x0 + x, y0 + y), scale(glow, 0.92) + (255,))


def fill(img, rect, colour):
    x0, y0, w, h = rect
    for y in range(h):
        for x in range(w):
            img.putpixel((x0 + x, y0 + y), colour)


def paint(colourway):
    model = json.loads(MODEL.read_text(encoding="utf-8"))
    off = Image.new("RGBA", (SIZE, SIZE), CLEAR)
    lit = Image.new("RGBA", (SIZE, SIZE), CLEAR)
    first, second = COLOURWAYS[colourway]
    for cube in model["elements"]:
        name = cube["name"]
        faces = box_faces(cube)
        berry = int(name.split("_")[1]) if name.startswith("berry_") else 0
        # Each berry its own blend: the colourway's pastels, the lead swapped on every other berry.
        rng = random.Random(4100 + 37 * berry + 991 * colourway + sum(map(ord, name.rsplit("_", 1)[-1])) % 97)
        colours = (first, second) if berry % 2 == 0 else (second, first)
        if "_shell_" in name:
            for face, rect in faces.items():
                paint_shell(off, lit, rect, face, rng, colours)
        elif name.endswith("_core"):
            for rect in faces.values():
                fill(off, rect, CORE_DIM + (150,))
                fill(lit, rect, CORE_LIT + (255,))
        elif name.endswith("_knot"):
            for rect in faces.values():
                fill(off, rect, KNOT + (230,))
                fill(lit, rect, scale(mix(CREAM, first, 0.5), 0.6) + (255,))
        elif name.endswith("_stalk"):
            for face, (x0, y0, w, h) in faces.items():
                for y in range(h):
                    for x in range(w):
                        t = 1 - y / max(1, h - 1) if face not in ("up", "down") else 1.0
                        off.putpixel((x0 + x, y0 + y), mix(STALK_LOW, STALK_HIGH, t) + (255,))
                        lit.putpixel((x0 + x, y0 + y), scale(mix(CREAM, first, 0.5), 0.15 + 0.3 * t) + (255,))
        elif name == "base_pad":
            prng = random.Random(4099)
            for rect in faces.values():
                x0, y0, w, h = rect
                for y in range(h):
                    for x in range(w):
                        off.putpixel((x0 + x, y0 + y), prng.choice(MOSS) + (255,))
                        lit.putpixel((x0 + x, y0 + y), (0, 0, 0, 255))
        elif name.endswith("_blade"):
            for face, (x0, y0, w, h) in faces.items():
                for y in range(h):
                    for x in range(w):
                        long_axis = max(w, h)
                        along = (x if w >= h else y) / max(1, long_axis - 1)
                        colour = LEAF[2] if face == "up" and (w > 1 and h > 1) and (y == h // 2 if w >= h else x == w // 2) else \
                            LEAF[1] if along > 0.4 else LEAF[0]
                        if face == "down":
                            colour = scale(colour, 0.75)
                        off.putpixel((x0 + x, y0 + y), colour + (255,))
                        lit.putpixel((x0 + x, y0 + y), (0, 0, 0, 255))
    return off, lit


def halo():
    img = Image.new("RGBA", (16, 16), CLEAR)
    for y in range(16):
        for x in range(16):
            r = (((x + 0.5 - 8) ** 2 + (y + 0.5 - 8) ** 2) ** 0.5) / 8
            if r >= 1:
                continue
            level = round((1 - r) ** 1.6 * 5) / 5
            if level > 0:
                img.putpixel((x, y), (255, 255, 255, int(255 * level)))
    return img


def particle():
    img = Image.new("RGBA", (16, 16), CLEAR)
    rng = random.Random(4120)
    for y in range(16):
        for x in range(16):
            colour, alpha = SHELL, 150
            if (x + y) % 7 == 0:
                colour, alpha = SHEEN, 220
            elif rng.random() < 0.2:
                colour, alpha = SHELL_RIM, 190
            img.putpixel((x, y), colour + (alpha,))
    return img


def icon():
    """Three lit berries on grey stalks over a little mossy pad: what you would hope to see it do."""
    img = Image.new("RGBA", (16, 16), CLEAR)
    outline = (70, 72, 92, 255)
    berries = [(4, 5, PINK), (11, 4, SKY), (8, 9, LEMON)]
    stalks = [((4, 8), (6, 13)), ((11, 7), (9, 13)), ((8, 12), (8, 13))]
    for (x1, y1), (x2, y2) in stalks:
        steps = max(abs(x2 - x1), abs(y2 - y1))
        for s in range(steps + 1):
            x = round(x1 + (x2 - x1) * s / max(1, steps))
            y = round(y1 + (y2 - y1) * s / max(1, steps))
            img.putpixel((x, y), STALK_HIGH + (255,))
    for x in range(5, 12):
        img.putpixel((x, 14), MOSS[1] + (255,))
    img.putpixel((4, 14), LEAF[1] + (255,))
    img.putpixel((12, 14), LEAF[1] + (255,))
    for cx, cy, colour in berries:
        cells = [(cx + dx, cy + dy) for dx in range(-2, 2) for dy in range(-2, 3) if not (abs(dx + 0.5) > 1 and abs(dy) == 2)]
        for x, y in cells:
            for ox, oy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                p = (x + ox, y + oy)
                if p not in cells and 0 <= p[0] < 16 and 0 <= p[1] < 16 and img.getpixel(p)[3] == 0:
                    img.putpixel(p, outline)
        for x, y in cells:
            light = mix(CREAM, colour, 0.6)
            if (x - cx, y - cy) in ((-1, -1), (-2, -1), (-1, -2)):
                light = CORE_LIT
            img.putpixel((x, y), light + (255,))
    return img


def main():
    out = {}
    off = None
    for colourway in range(len(COLOURWAYS)):
        off, lit = paint(colourway)
        out[ASSETS / "textures" / "block" / f"murmurberry_lit_{colourway}.png"] = lit
    out[ASSETS / "textures" / "block" / "murmurberry.png"] = off
    out[ASSETS / "textures" / "block" / "murmurberry_halo.png"] = halo()
    out[ASSETS / "textures" / "block" / "murmurberry_particle.png"] = particle()
    out[ASSETS / "textures" / "item" / "murmurberry.png"] = icon()
    for path, img in out.items():
        path.parent.mkdir(parents=True, exist_ok=True)
        img.save(path)
        print("wrote", path.relative_to(ROOT))
    scale_up = 5
    sheet = Image.new("RGBA", (SIZE * scale_up * 5 + 60, SIZE * scale_up + 20), (24, 22, 40, 255))
    sheet.alpha_composite(off.resize((SIZE * scale_up, SIZE * scale_up), Image.NEAREST), (10, 10))
    for k in range(len(COLOURWAYS)):
        lit = Image.open(ASSETS / "textures" / "block" / f"murmurberry_lit_{k}.png")
        sheet.alpha_composite(lit.resize((SIZE * scale_up, SIZE * scale_up), Image.NEAREST), (20 + (k + 1) * (SIZE * scale_up + 10), 10))
    PREVIEW.mkdir(parents=True, exist_ok=True)
    sheet.save(PREVIEW / "murmurberry_textures.png")
    print("wrote", (PREVIEW / "murmurberry_textures.png").relative_to(ROOT))


if __name__ == "__main__":
    main()
