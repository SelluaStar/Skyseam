"""The wind lanes' textures (docs/DECISIONS.md K109), in Minecraft's pixel style.

Writes, under src/main/resources/assets/skyseam/:
  textures/environment/wind_ribbon.png   16x64, one length of a lane's ribbon, white (tinted by code): soft across its
                                         width, brightest along three thin streaks, the streaks broken into dashes of
                                         different lengths so they are seen to run when the texture scrolls with the
                                         wind. Runs along its height and tiles top to bottom.
  textures/particle/petal_0..2.png       8x8, three petal shapes (a teardrop, a petal cleft at its tip, a small round one), white
                                         with a pale vein and a darker rim (tinted by code to pink, rose, peach, white)
  textures/particle/wind_mote_0..1.png   8x8, a mote of light in two twinkle frames, white (tinted by code)
and a contact sheet in build/previews/wind_lane_textures.png. Deterministic.
"""
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "skyseam"
PREVIEW = ROOT / "build" / "previews"
CLEAR = (0, 0, 0, 0)


def ribbon():
    rng = random.Random(4109)
    w, h = 16, 64
    img = Image.new("RGBA", (w, h), CLEAR)
    # Soft across the width: faint at the edges, fuller in the middle, in steps.
    body = [0.0, 0.18, 0.3, 0.38, 0.44, 0.5, 0.54, 0.56, 0.56, 0.54, 0.5, 0.44, 0.38, 0.3, 0.18, 0.0]
    streaks = {4: 0.95, 8: 1.0, 11: 0.85}
    dashes = {}
    for column in streaks:
        on = []
        y = rng.randrange(h)
        while len(on) < h:
            length = rng.choice((6, 9, 12, 16))
            gap = rng.choice((3, 5, 8))
            on += [True] * length + [False] * gap
        offset = rng.randrange(h)
        dashes[column] = [on[(i + offset) % len(on)] for i in range(h)]
    for y in range(h):
        # A gentle swell along its length, so the ribbon is not one flat band.
        swell = 0.85 + 0.15 * (1 if (y // 8) % 2 == 0 else 0.6)
        for x in range(w):
            a = body[x] * swell
            if x in streaks and dashes[x][y]:
                a = max(a, streaks[x])
            # The dashes' ends soften by a step.
            elif x in streaks and (dashes[x][(y - 1) % h] or dashes[x][(y + 1) % h]):
                a = max(a, streaks[x] * 0.6)
            if a > 0:
                img.putpixel((x, y), (255, 255, 255, int(round(255 * min(1, a)))))
    return img


def petal(shape):
    img = Image.new("RGBA", (8, 8), CLEAR)
    masks = [
        # A teardrop, its point down-left.
        ["........",
         "....##..",
         "...####.",
         "..#####.",
         "..#####.",
         ".#####..",
         ".###....",
         "........"],
        # A petal cleft at its tip.
        ["........",
         "..##.##.",
         ".#######",
         ".#######",
         "..#####.",
         "...###..",
         "....#...",
         "........"],
        # A small round petal, tilted.
        ["........",
         "........",
         "...###..",
         "..#####.",
         "..#####.",
         "...###..",
         "........",
         "........"],
    ]
    rows = masks[shape]
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c != "#":
                continue
            edge = any(not (0 <= x + dx < 8 and 0 <= y + dy < 8 and rows[y + dy][x + dx] == "#") for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            vein = shape == 0 and x == 4 and y >= 2 or shape == 1 and x == 4 and y >= 3
            shade = 205 if edge else 255
            if vein:
                shade = 232
            img.putpixel((x, y), (shade, shade, shade, 255))
    return img


def mote(frame):
    img = Image.new("RGBA", (8, 8), CLEAR)
    core = 1.0 if frame == 0 else 0.8
    for y in range(8):
        for x in range(8):
            d = ((x - 3.5) ** 2 + (y - 3.5) ** 2) ** 0.5
            if frame == 1 and (x in (3, 4) or y in (3, 4)) and d < 3.6:
                # The twinkle: a little cross of light.
                a = max(0.0, 1 - d / 3.8)
            else:
                a = max(0.0, core - d / 2.4)
            a = round(a * 4) / 4
            if a > 0:
                img.putpixel((x, y), (255, 255, 255, int(255 * a)))
    return img


def main():
    out = []
    path = ASSETS / "textures" / "environment" / "wind_ribbon.png"
    path.parent.mkdir(parents=True, exist_ok=True)
    rib = ribbon()
    rib.save(path)
    out.append(rib)
    for k in range(3):
        p = petal(k)
        p.save(ASSETS / "textures" / "particle" / f"petal_{k}.png")
        out.append(p)
    for k in range(2):
        m = mote(k)
        m.save(ASSETS / "textures" / "particle" / f"wind_mote_{k}.png")
        out.append(m)
    PREVIEW.mkdir(parents=True, exist_ok=True)
    scale = 8
    sheet = Image.new("RGBA", (16 * scale + 10 + 5 * (8 * scale + 10), 64 * scale), (40, 52, 90, 255))
    sheet.alpha_composite(out[0].resize((16 * scale, 64 * scale), Image.NEAREST), (0, 0))
    for k, img in enumerate(out[1:]):
        tinted = Image.new("RGBA", img.size)
        tint = [(255, 200, 225), (255, 170, 205), (255, 220, 200), (255, 240, 200), (210, 230, 255)][k]
        for y in range(img.height):
            for x in range(img.width):
                r, g, b, a = img.getpixel((x, y))
                tinted.putpixel((x, y), (r * tint[0] // 255, g * tint[1] // 255, b * tint[2] // 255, a))
        sheet.alpha_composite(tinted.resize((8 * scale, 8 * scale), Image.NEAREST), (16 * scale + 10 + k * (8 * scale + 10), 0))
    sheet.save(PREVIEW / "wind_lane_textures.png")
    print("wrote the wind lane textures and", PREVIEW / "wind_lane_textures.png")


if __name__ == "__main__":
    main()
