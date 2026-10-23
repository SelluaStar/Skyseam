"""The Tides' textures (docs/DECISIONS.md K111), in Minecraft's pixel style.

Writes, under src/main/resources/assets/skyseam/:
  textures/item/prism_dust.png            16x16, a small heap of glittering glass dust, pale cyan, lilac and pink with
                                          white glints, the colours of the Prismfall
  textures/particle/shard_glint_0..1.png  8x8, a falling sliver of glass and its glint, white (tinted by code)
  textures/particle/pollen_0..1.png       8x8, a soft fleck of pollen, two sizes, white (tinted by code)
  textures/block/tide_vane_brass.png      16x16, brushed brass for the Tide Vane's post, arms and arrow
  textures/block/tide_vane_<tide>.png     16x16, the faceted gem atop the vane, one colour for each Tide it shows (Calm,
                                          Prismfall, Gale, Bloom, Stillness)
  textures/environment/glass_rain.png     64x256, the Prismfall's falling glass as weather sheets, white (tinted by code):
                                          four strips of thin slivers and glints, like vanilla's rain.png
  textures/environment/pollen_fall.png    64x256, the Bloom's drifting pollen, soft flecks in four strips, like snow.png
  textures/environment/gale_streaks.png   64x256, the Gale's racing air, long thin streaks in four strips
and a contact sheet in build/previews/tide_textures.png. Deterministic.
"""
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "skyseam"
PREVIEW = ROOT / "build" / "previews"
CLEAR = (0, 0, 0, 0)


def prism_dust():
    rng = random.Random(4111)
    img = Image.new("RGBA", (16, 16), CLEAR)
    # The heap: wide and low, rounded on top.
    heights = [0, 0, 2, 3, 4, 5, 6, 6, 6, 6, 5, 4, 3, 2, 0, 0]
    colours = [(170, 214, 240), (196, 178, 236), (236, 182, 214), (206, 228, 246), (180, 196, 238)]
    for x in range(16):
        h = heights[x]
        for k in range(h):
            y = 13 - k
            c = rng.choice(colours)
            # Darker towards the bottom and the edges.
            shade = 0.72 + 0.28 * (k / max(1, h - 1)) if h > 1 else 0.8
            img.putpixel((x, y), (int(c[0] * shade), int(c[1] * shade), int(c[2] * shade), 255))
        if h:
            img.putpixel((x, 14), (92, 96, 140, 255))
    # A rim of darker blue-violet on the heap's top edge and sides.
    for x in range(16):
        h = heights[x]
        if h:
            r, g, b, a = img.getpixel((x, 14 - h))
            img.putpixel((x, 14 - h), (int(r * 1.05) if r < 240 else r, g, b, a))
    # Glints: white on the heap, and a few loose grains sparkling above it.
    for x, y in ((6, 9), (9, 8), (4, 11), (11, 10), (8, 12)):
        img.putpixel((x, y), (255, 255, 255, 255))
    for x, y, a in ((3, 5, 255), (12, 4, 220), (7, 3, 255), (10, 6, 180)):
        img.putpixel((x, y), (240, 246, 255, a))
    for x, y in ((7, 2), (7, 4), (6, 3), (8, 3)):
        img.putpixel((x, y), (214, 228, 255, 150))
    return img


def shard(frame):
    img = Image.new("RGBA", (8, 8), CLEAR)
    if frame == 0:
        # A thin sliver of glass, slanting as it falls, brightest at its leading tip.
        for k, a in enumerate((90, 140, 190, 230, 255)):
            x, y = 2 + k // 2 + (1 if k > 3 else 0), 1 + k
            img.putpixel((x, y), (255, 255, 255, a))
            if k < 4:
                img.putpixel((x + 1, y), (255, 255, 255, a // 2))
    else:
        # Its glint: a small cross of light.
        for x, y, a in ((3, 3, 255), (4, 3, 230), (3, 4, 230), (4, 4, 255), (3, 1, 120), (3, 2, 200), (4, 5, 200), (4, 6, 120),
                        (1, 4, 120), (2, 4, 200), (5, 3, 200), (6, 3, 120)):
            img.putpixel((x, y), (255, 255, 255, a))
    return img


def pollen(size):
    img = Image.new("RGBA", (8, 8), CLEAR)
    if size == 0:
        core = [(3, 3), (4, 3), (3, 4), (4, 4)]
        halo = [(2, 3), (2, 4), (5, 3), (5, 4), (3, 2), (4, 2), (3, 5), (4, 5)]
    else:
        core = [(3, 3)]
        halo = [(2, 3), (4, 3), (3, 2), (3, 4)]
    for p in halo:
        img.putpixel(p, (255, 255, 255, 110))
    for p in core:
        img.putpixel(p, (255, 255, 255, 255))
    return img


def brass():
    rng = random.Random(4112)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        # Brushed: bands across, with a bright line and a dark line.
        band = 1.0 + (0.12 if y in (3, 4) else 0) - (0.14 if y in (11, 12) else 0) + (0.06 if y % 5 == 0 else 0)
        for x in range(16):
            n = rng.uniform(-0.05, 0.05)
            k = max(0.5, band + n)
            img.putpixel((x, y), (min(255, int(200 * k)), min(255, int(152 * k)), min(255, int(70 * k)), 255))
    for x in range(16):
        img.putpixel((x, 0), (232, 190, 104, 255))
        img.putpixel((x, 15), (126, 88, 38, 255))
    return img


# Each Tide's gem: its light, mid and dark facets.
GEMS = {
    "calm": ((246, 244, 236), (214, 212, 204), (164, 160, 156)),
    "prismfall": ((214, 240, 255), (168, 196, 246), (150, 112, 210)),
    "gale": ((236, 244, 250), (176, 196, 214), (110, 128, 150)),
    "bloom": ((255, 226, 214), (246, 170, 186), (214, 150, 76)),
    "stillness": ((176, 160, 214), (112, 94, 160), (52, 42, 86)),
}


def gem(name):
    light, mid, dark = GEMS[name]
    img = Image.new("RGBA", (16, 16), dark + (255,))
    for y in range(16):
        for x in range(16):
            # Facets: a lit upper-left half, a shaded lower-right, a bright table in the middle.
            if x + y < 14:
                c = light if (x + y) % 7 < 4 else mid
            elif x + y < 18:
                c = mid
            else:
                c = dark
            if 5 <= x <= 10 and 5 <= y <= 10:
                c = light if (x, y) not in ((10, 10), (9, 10), (10, 9)) else mid
            img.putpixel((x, y), c + (255,))
    for x, y in ((6, 6), (7, 6), (6, 7)):
        img.putpixel((x, y), (255, 255, 255, 255))
    for k in range(16):
        img.putpixel((k, 15), tuple(int(v * 0.7) for v in dark) + (255,))
        img.putpixel((15, k), tuple(int(v * 0.7) for v in dark) + (255,))
    return img


def weather_sheet(seed, kind):
    """A weather sheet: four 16-wide strips, tiling top to bottom."""
    rng = random.Random(seed)
    img = Image.new("RGBA", (64, 256), CLEAR)
    def put(x, y, a):
        x %= 64
        y %= 256
        r, g, b, old = img.getpixel((x, y))
        img.putpixel((x, y), (255, 255, 255, max(old, int(a))))
    for strip in range(4):
        for _ in range({"glass": 34, "pollen": 46, "gale": 26}[kind]):
            x = strip * 16 + rng.randrange(1, 15)
            y = rng.randrange(256)
            if kind == "glass":
                length = rng.choice((3, 4, 5, 6))
                for k in range(length):
                    put(x, y + k, 120 + 135 * k / (length - 1))
                if rng.random() < 0.25:
                    for dx, dy, a in ((-1, length - 1, 160), (1, length - 1, 160), (0, length, 200)):
                        put(x + dx, y + dy, a)
            elif kind == "pollen":
                put(x, y, 255)
                if rng.random() < 0.5:
                    for dx, dy in ((1, 0), (0, 1), (1, 1)):
                        put(x + dx, y + dy, 150)
            else:
                length = rng.choice((10, 16, 24, 32))
                for k in range(length):
                    put(x, y + k, 200 * min(1, k / 3, (length - k) / 6))
    return img


def main():
    out = {
        "item/prism_dust": prism_dust(),
        "particle/shard_glint_0": shard(0),
        "particle/shard_glint_1": shard(1),
        "particle/pollen_0": pollen(0),
        "particle/pollen_1": pollen(1),
        "block/tide_vane_brass": brass(),
    }
    for name in GEMS:
        out["block/tide_vane_" + name] = gem(name)
    out["environment/glass_rain"] = weather_sheet(4113, "glass")
    out["environment/pollen_fall"] = weather_sheet(4114, "pollen")
    out["environment/gale_streaks"] = weather_sheet(4115, "gale")
    for name, img in out.items():
        path = ASSETS / "textures" / f"{name}.png"
        path.parent.mkdir(parents=True, exist_ok=True)
        img.save(path)
        print(path.relative_to(ROOT))
    PREVIEW.mkdir(parents=True, exist_ok=True)
    scale = 12
    sheet = Image.new("RGBA", (len(out) * (16 * scale + 8) + 8, 16 * scale + 16), (40, 42, 54, 255))
    for k, img in enumerate(out.values()):
        big = img.resize((img.width * scale * 16 // img.width, img.height * scale * 16 // img.width), Image.NEAREST)
        sheet.alpha_composite(big, (8 + k * (16 * scale + 8), 8))
    sheet.save(PREVIEW / "tide_textures.png")


if __name__ == "__main__":
    main()
