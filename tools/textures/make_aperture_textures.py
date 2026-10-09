"""First-pass textures for the Harmonic Aperture and the Skychart (spec section 20). [FIRST PASS]

Writes, under src/main/resources/assets/skyseam/:
  textures/block/harmonic_aperture.png            64x64 model texture, painted onto the box-UV layout read from
                                                   art/models/harmonic_aperture.bbmodel
  textures/block/harmonic_aperture_glowmask.png   what glows: the core, a line round the ring, the lit gems, the needle tip
  textures/block/harmonic_aperture_particle.png   16x16 brass, for break particles
  textures/item/harmonic_aperture.png             16x16 inventory icon
  textures/item/skychart.png                      16x16 inventory icon
  textures/gui/aperture.png                       256x256: the Aperture screen panel, the 9x9 tick icons (unlit row,
                                                   lit row), the 9x9 arrow and the 84x22 HUD frame
  textures/gui/skychart.png                       256x256: the 200x200 round chart
and a scaled-up contact sheet in build/previews/aperture_textures.png.

Deterministic: the same script always writes the same files. Run with tools/.venv/Scripts/python.exe.
Every colour comes from palette.py plus the brass tones below. Replace any file by name (docs/ASSET-PATHS.md).
"""
import json
import math
import random
import sys
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
import palette as P  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "skyseam"
MODEL = ROOT / "art" / "models" / "harmonic_aperture.bbmodel"
PREVIEW = ROOT / "build" / "previews"

# Brass for the Aperture's body: warm, a little pink, so it sits with the pastel palette.
BRASS_LIGHT = (246, 214, 150)
BRASS = (214, 168, 102)
BRASS_DARK = (158, 112, 72)
BRASS_DEEP = (112, 76, 58)
GLASS_DIM = (92, 80, 134)
GLASS_DIM_LIGHT = (132, 118, 178)
# The three gems, in tick order: flying (teal), altitude (gold), site (pink).
GEM_COLOURS = [P.PRISM_TEAL, P.GOLD, P.PRISM_PINK]

# Screen colours: pastel glass in the style of Fealty's screens (docs/DECISIONS.md K13).
PANEL_TOP = (232, 224, 255)
PANEL_BOTTOM = (210, 198, 246)
PANEL_EDGE = (120, 104, 176)
PANEL_LIGHT = (250, 246, 255)
SLOT_DARK = (96, 84, 140)
SLOT_FILL = (170, 158, 214)
SLOT_LIGHT = (246, 242, 255)


def mix(a, b, t):
    return tuple(int(round(a[k] * (1 - t) + b[k] * t)) for k in range(3))


def shade(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c)


# ---- The model texture ---------------------------------------------------------------------------------------


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


def bevel(img, rect, base, light_f=1.18, dark_f=0.78, noise=0.05, rng=None):
    """Fills a face with a colour, faint noise, a light top-left edge and a dark bottom-right edge."""
    x0, y0, w, h = rect
    px = img.load()
    for y in range(h):
        for x in range(w):
            c = base
            if rng is not None:
                c = shade(c, 1 + (rng.random() - 0.5) * 2 * noise)
            if w > 2 and h > 2:
                if x == 0 or y == 0:
                    c = shade(base, light_f)
                elif x == w - 1 or y == h - 1:
                    c = shade(base, dark_f)
            px[x0 + x, y0 + y] = c + (255,)


def paint_model():
    model = json.loads(MODEL.read_text(encoding="utf-8"))
    tex = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    glow = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    rng = random.Random(4207)
    tpx = tex.load()
    gpx = glow.load()
    for cube in model["elements"]:
        name = cube["name"]
        faces = box_faces(cube)
        for face, rect in faces.items():
            x0, y0, w, h = rect
            if w <= 0 or h <= 0:
                continue
            top = face == "up"
            bottom = face == "down"
            if name.startswith("plinth"):
                base = BRASS_LIGHT if top else BRASS_DARK if bottom else BRASS
                bevel(tex, rect, base, rng=rng)
                if not top and not bottom and h >= 3 and name == "plinth":
                    # A lavender inlay band with brass rivets along each side.
                    for x in range(1, w - 1):
                        tpx[x0 + x, y0 + h // 2] = P.PASTEL_LAVENDER + (255,)
                    for x in range(2, w - 2, 4):
                        tpx[x0 + x, y0 + 1] = BRASS_LIGHT + (255,)
                if top and name == "plinth":
                    # A ring of lavender around the top, framing the upper plate.
                    for k in range(1, w - 1):
                        tpx[x0 + k, y0 + 1] = P.PASTEL_LAVENDER + (255,)
                        tpx[x0 + k, y0 + h - 2] = P.PASTEL_LAVENDER + (255,)
                        tpx[x0 + 1, y0 + k] = P.PASTEL_LAVENDER + (255,)
                        tpx[x0 + w - 2, y0 + k] = P.PASTEL_LAVENDER + (255,)
                if top and name == "plinth_top":
                    # A star rose engraved in the plate.
                    cx, cy = x0 + w // 2, y0 + h // 2
                    for k in range(-4, 5):
                        tpx[cx + k, cy] = BRASS_DARK + (255,)
                        tpx[cx, cy + k] = BRASS_DARK + (255,)
                    for k in range(-2, 3):
                        tpx[cx + k, cy + k] = BRASS_DARK + (255,)
                        tpx[cx + k, cy - k] = BRASS_DARK + (255,)
            elif name.startswith(("post", "cap", "axle")):
                base = BRASS_LIGHT if top else BRASS_DEEP if bottom else BRASS
                bevel(tex, rect, base, rng=rng)
                if name.startswith("post") and not top and not bottom and w >= 2:
                    for y in range(2, h - 1, 3):
                        tpx[x0 + w // 2, y0 + y] = BRASS_DARK + (255,)
            elif name.startswith("ring"):
                base = mix(P.PEARL, P.GOLD_LIGHT, 0.45)
                bevel(tex, rect, base, light_f=1.05, dark_f=0.82, noise=0.02, rng=rng)
                # A gold thread round the ring, which glows.
                if face in ("north", "south"):
                    for x in range(w):
                        for y in range(h):
                            if (h >= 3 and y == h // 2) or (w >= 3 and x == w // 2) or (w < 3 and h < 3):
                                tpx[x0 + x, y0 + y] = P.GOLD + (255,)
                                gpx[x0 + x, y0 + y] = P.GOLD_LIGHT + (255,)
            elif name.startswith("core"):
                bevel(tex, rect, P.LANTERN_CORE, light_f=1.0, dark_f=0.92, noise=0.0)
                for y in range(h):
                    for x in range(w):
                        c = P.LANTERN_CORE if (x + y) % 2 == 0 else P.LANTERN_GLOW
                        gpx[x0 + x, y0 + y] = c + (255,)
            elif name.endswith("_dim"):
                bevel(tex, rect, GLASS_DIM, light_f=1.35, dark_f=0.8, noise=0.0)
            elif name.endswith("_glow"):
                index = ["gem_flying_glow", "gem_altitude_glow", "gem_site_glow"].index(name)
                colour = GEM_COLOURS[index]
                bevel(tex, rect, colour, light_f=1.1, dark_f=0.9, noise=0.0)
                for y in range(h):
                    for x in range(w):
                        gpx[x0 + x, y0 + y] = mix(colour, (255, 255, 255), 0.25) + (255,)
            elif name.startswith("needle"):
                # The compass needle on the plate: a glowing pink tip (the site gem's colour) points to the site.
                if name == "needle_tip":
                    bevel(tex, rect, P.PRISM_PINK, light_f=1.1, dark_f=0.85, noise=0.0)
                    for y in range(h):
                        for x in range(w):
                            gpx[x0 + x, y0 + y] = mix(P.PRISM_PINK, (255, 255, 255), 0.3) + (255,)
                elif name == "needle_hub":
                    bevel(tex, rect, BRASS_LIGHT, light_f=1.05, dark_f=0.8, noise=0.0)
                else:
                    bevel(tex, rect, BRASS_DARK, light_f=1.15, dark_f=0.8, noise=0.0)
            else:
                bevel(tex, rect, BRASS, rng=rng)
    return tex, glow


# ---- Small pixel art -------------------------------------------------------------------------------------------


def from_rows(rows, colours):
    """An image from strings of single-character colour keys ('.' is transparent)."""
    h = len(rows)
    w = len(rows[0])
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".":
                px[x, y] = colours[ch] + (255,)
    return img


ICON_ROWS = {
    # A wing: flying.
    "flying": [
        ".........",
        "......##.",
        "....###o.",
        "..####o..",
        ".####o...",
        "#####o#..",
        ".oo#####.",
        "...ooo...",
        ".........",
    ],
    # An arrow rising over a line: altitude.
    "altitude": [
        "....#....",
        "...###...",
        "..#####..",
        ".#.###.#.",
        "...###...",
        "...###...",
        ".........",
        "#########",
        ".........",
    ],
    # A target: the site.
    "site": [
        "..#####..",
        ".#.....#.",
        "#..###..#",
        "#.#...#.#",
        "#.#.#.#.#",
        "#.#...#.#",
        "#..###..#",
        ".#.....#.",
        "..#####..",
    ],
}

ARROW_ROWS = [
    "....#....",
    "...#w#...",
    "..#www#..",
    ".#wwwww#.",
    "#www#www#",
    "...#w#...",
    "...#w#...",
    "...#w#...",
    "...###...",
]


def icon(name, lit):
    if lit:
        colour = GEM_COLOURS[["flying", "altitude", "site"].index(name)]
        return from_rows(ICON_ROWS[name], {"#": colour, "o": shade(colour, 0.7)})
    return from_rows(ICON_ROWS[name], {"#": (150, 138, 190), "o": (120, 108, 160)})


def item_aperture():
    rows = [
        "................",
        "...cc......cc...",
        "...pp..rr..pp...",
        "...pp.rggr.pp...",
        "...pp.r..r.pp...",
        "...ppar.wrapp...",
        "...pp.r..r.pp...",
        "...pp.rggr.pp...",
        "...pp..rr..pp...",
        "..llllllllllll..",
        ".lbbbbbbbbbbbbd.",
        ".bvvvvvvvvvvvvd.",
        ".btbbtbbtbbbbbd.",
        ".bbbbbbbbbbbbbd.",
        "..dddddddddddd..",
        "................",
    ]
    return from_rows(rows, {
        "c": BRASS_LIGHT, "p": BRASS, "a": BRASS_DARK, "r": mix(P.PEARL, P.GOLD_LIGHT, 0.45), "g": P.GOLD,
        "w": P.LANTERN_CORE, "l": BRASS_LIGHT, "b": BRASS, "d": BRASS_DARK, "v": P.PASTEL_LAVENDER, "t": P.PRISM_TEAL,
    })


def item_skychart():
    rows = [
        "................",
        "..ee........ee..",
        ".ennkkkkkkkkkne.",
        ".enkkkkkkskkkne.",
        ".enkskkkkkkkkne.",
        ".enkkkkkkkkpkne.",
        ".enkkkkkkkpppne.",
        ".enkkskkkkkpkne.",
        ".enkkkkkkkkkkne.",
        ".enkkkkksskkkne.",
        ".enkskkkkkkkkne.",
        ".enkkkkkkkkksne.",
        ".ennkkkkkkkkkne.",
        "..ee........ee..",
        "................",
        "................",
    ]
    return from_rows(rows, {
        "e": BRASS_DARK, "n": BRASS_LIGHT, "k": P.SKY_HIGH, "s": P.GOLD_LIGHT, "p": P.PRISM_PINK,
    })


def particle():
    rng = random.Random(77)
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = shade(BRASS, 1 + (rng.random() - 0.5) * 0.25) + (255,)
    return img


# ---- Screens ---------------------------------------------------------------------------------------------------


def slot(draw, x, y):
    """An 18x18 slot frame with its top-left corner at (x, y)."""
    draw.rectangle([x, y, x + 17, y + 17], fill=SLOT_FILL + (255,))
    draw.line([x, y, x + 16, y], fill=SLOT_DARK + (255,))
    draw.line([x, y, x, y + 16], fill=SLOT_DARK + (255,))
    draw.line([x + 1, y + 17, x + 17, y + 17], fill=SLOT_LIGHT + (255,))
    draw.line([x + 17, y + 1, x + 17, y + 17], fill=SLOT_LIGHT + (255,))


def panel(img, x, y, w, h):
    px = img.load()
    for yy in range(h):
        c = mix(PANEL_TOP, PANEL_BOTTOM, yy / max(1, h - 1))
        for xx in range(w):
            px[x + xx, y + yy] = c + (255,)
    draw = ImageDraw.Draw(img)
    draw.rectangle([x, y, x + w - 1, y + h - 1], outline=PANEL_EDGE + (255,))
    draw.line([x + 1, y + 1, x + w - 2, y + 1], fill=PANEL_LIGHT + (255,))
    draw.line([x + 1, y + 1, x + 1, y + h - 2], fill=PANEL_LIGHT + (255,))
    # Rounded corners.
    for cx, cy in ((x, y), (x + w - 1, y), (x, y + h - 1), (x + w - 1, y + h - 1)):
        px[cx, cy] = (0, 0, 0, 0)


def gui_aperture():
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    panel(img, 0, 0, 176, 206)
    draw = ImageDraw.Draw(img)
    # A faint divider under the gauge, and one above the inventory.
    draw.line([8, 69, 167, 69], fill=shade(PANEL_BOTTOM, 0.88) + (255,))
    draw.line([8, 111, 167, 111], fill=shade(PANEL_BOTTOM, 0.88) + (255,))
    # The chart slot, with a ghost of a chart in it.
    slot(draw, 151, 91)
    ghost = item_skychart().convert("RGBA")
    ghost_px = ghost.load()
    for y in range(16):
        for x in range(16):
            r, g, b, a = ghost_px[x, y]
            if a:
                img.putpixel((152 + x, 92 + y), mix(SLOT_FILL, (r, g, b), 0.25) + (255,))
    for row in range(3):
        for col in range(9):
            slot(draw, 7 + col * 18, 123 + row * 18)
    for col in range(9):
        slot(draw, 7 + col * 18, 181)
    # Tick icons: unlit row at y 0, lit row at y 9; the arrow below them.
    for k, name in enumerate(("flying", "altitude", "site")):
        img.paste(icon(name, False), (176 + k * 9, 0))
        img.paste(icon(name, True), (176 + k * 9, 9))
    img.paste(from_rows(ARROW_ROWS, {"#": P.GOLD, "w": P.PEARL}), (176, 18))
    # The HUD frame: dark glass, so white text reads over any sky.
    hud = Image.new("RGBA", (84, 22), (58, 46, 92, 190))
    hd = ImageDraw.Draw(hud)
    hd.rectangle([0, 0, 83, 21], outline=(200, 186, 246, 230))
    for cx, cy in ((0, 0), (83, 0), (0, 21), (83, 21)):
        hud.putpixel((cx, cy), (0, 0, 0, 0))
    img.paste(hud, (0, 208))
    return img


def gui_skychart():
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    px = img.load()
    rng = random.Random(1308)
    c = 99.5
    for y in range(200):
        for x in range(200):
            d = math.hypot(x - c, y - c)
            if d > 99:
                continue
            t = d / 99
            col = mix(P.SKY_ZENITH, P.SKY_HIGH, t * t)
            if d > 95:
                col = BRASS if d < 97.5 else BRASS_DARK
            elif d > 93.5:
                col = BRASS_LIGHT
            px[x, y] = col + (255,)
    draw = ImageDraw.Draw(img)
    # Range rings at a quarter, a half and three quarters of 4,096 blocks, and the cross of the compass.
    for frac in (0.25, 0.5, 0.75):
        r = 88 * frac
        draw.ellipse([c - r, c - r, c + r, c + r], outline=mix(P.SKY_HIGH, P.SKY_LILAC, 0.5) + (255,))
    for k in range(-92, 93):
        if k % 2 == 0:
            px[int(c + k), int(c)] = mix(P.SKY_HIGH, P.SKY_LILAC, 0.3) + (255,)
            px[int(c), int(c + k)] = mix(P.SKY_HIGH, P.SKY_LILAC, 0.3) + (255,)
    # Stars.
    for _ in range(140):
        x = rng.randrange(8, 192)
        y = rng.randrange(8, 192)
        if math.hypot(x - c, y - c) < 90:
            px[x, y] = mix(P.SKY_HIGH, P.PEARL, 0.4 + 0.6 * rng.random()) + (255,)
    # The north mark: a gold point at the top of the rim.
    for k in range(5):
        draw.line([c - k, 4 + k, c + k, 4 + k], fill=P.GOLD + (255,))
    return img


def save(img, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    print("wrote", path.relative_to(ROOT))


def main():
    tex, glow = paint_model()
    save(tex, ASSETS / "textures/block/harmonic_aperture.png")
    save(glow, ASSETS / "textures/block/harmonic_aperture_glowmask.png")
    save(particle(), ASSETS / "textures/block/harmonic_aperture_particle.png")
    save(item_aperture(), ASSETS / "textures/item/harmonic_aperture.png")
    save(item_skychart(), ASSETS / "textures/item/skychart.png")
    aperture = gui_aperture()
    chart = gui_skychart()
    save(aperture, ASSETS / "textures/gui/aperture.png")
    save(chart, ASSETS / "textures/gui/skychart.png")

    sheet = Image.new("RGBA", (64 * 4 * 2 + 256 * 2 + 40, 512), (40, 36, 56, 255))
    sheet.paste(tex.resize((256, 256), Image.NEAREST), (0, 0))
    sheet.paste(glow.resize((256, 256), Image.NEAREST), (264, 0))
    sheet.paste(item_aperture().resize((128, 128), Image.NEAREST), (0, 300))
    sheet.paste(item_skychart().resize((128, 128), Image.NEAREST), (136, 300))
    sheet.paste(aperture, (536, 0))
    sheet.paste(chart, (536, 256))
    save(sheet, PREVIEW / "aperture_textures.png")


if __name__ == "__main__":
    main()
