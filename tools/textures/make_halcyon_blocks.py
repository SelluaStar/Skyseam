"""First-pass textures for the Halcyon's blocks and sky (spec sections 7 and 20). [FIRST PASS]

Writes, under src/main/resources/assets/skyseam/textures/:
  block/stillstone_glass.png                         the Wrights' pale glass
  block/cloud.png, rose_cloud.png, dusk_cloud.png    soft cloud blocks, slightly see-through
  block/petal_carpet.png, cloudmoss.png              carpets, with gaps (drawn cut out)
  block/glow_moss.png                                glowing teal strands, like glow lichen
  block/lantern_vine.png, lantern_vine_tip.png       a hanging vine and its glowing lantern-bud
  block/small_prismite_bud.png, large_prismite_bud.png, prismite_cluster.png   violet crystal, three sizes
and a scaled-up contact sheet in build/previews/halcyon_blocks.png. The rest of the Stillstone set is painted by
make_halcyon_terrain_blocks.py, and the sky's textures by make_sky_textures.py.

Deterministic: the same script always writes the same files. Run with tools/.venv/Scripts/python.exe.
Every colour comes from palette.py or the few material tones below. Replace any file by name (docs/ASSET-PATHS.md).
"""
import math
import random
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import palette as P  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
TEXTURES = ROOT / "src" / "main" / "resources" / "assets" / "skyseam" / "textures"
PREVIEW = ROOT / "build" / "previews"

# Stillstone: pale limestone with a lavender cast, so it sits in the pastel sky.
STONE = (210, 203, 218)
STONE_LIGHT = (232, 226, 238)
STONE_DARK = (176, 166, 190)
STONE_DEEP = (150, 138, 166)
MORTAR = (164, 152, 176)
MOSS = (126, 168, 104)
MOSS_LIGHT = (164, 200, 126)
MOSS_DARK = (92, 130, 86)
VINE = (88, 148, 118)
VINE_DARK = (62, 112, 94)
PETALS = [(214, 150, 226), (184, 120, 214), (238, 182, 236), (166, 112, 200)]
CLOUDMOSS = [(206, 238, 216), (184, 226, 202), (226, 248, 232)]
GLOW_MOSS = [(120, 232, 212), (164, 246, 228), (96, 206, 196)]
PRISMITE = [(150, 100, 214), (186, 136, 240), (220, 186, 255), (120, 80, 186)]


def shade(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c)


def mix(a, b, t):
    return tuple(int(round(a[k] * (1 - t) + b[k] * t)) for k in range(3))


def noise_fill(img, base, amount, rng, alpha=255):
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            px[x, y] = shade(base, 1 + (rng.random() - 0.5) * 2 * amount) + (alpha,)


# ---- Stillstone ---------------------------------------------------------------------------------------------------


def glass():
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for k in range(16):
        for edge in (0, 15):
            px[k, edge] = STONE + (255,)
            px[edge, k] = STONE + (255,)
    # Faint lavender streaks in a clear pane.
    for y in range(1, 15):
        for x in range(1, 15):
            px[x, y] = P.PASTEL_LAVENDER + (40,)
    for k in range(3, 9):
        px[k, 12 - k] = P.PEARL + (150,)
        px[k + 4, 14 - k] = P.PEARL + (110,)
    return img


# ---- Clouds -------------------------------------------------------------------------------------------------------


def value_noise(size, cells, seed):
    """Smooth noise that tiles: random values on a cells x cells lattice, blended with a smoothstep."""
    rng = random.Random(seed)
    lattice = [[rng.random() for _ in range(cells)] for _ in range(cells)]

    def at(x, y):
        fx, fy = x / size * cells, y / size * cells
        x0, y0 = int(fx) % cells, int(fy) % cells
        x1, y1 = (x0 + 1) % cells, (y0 + 1) % cells
        tx, ty = fx - int(fx), fy - int(fy)
        tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
        top = lattice[y0][x0] * (1 - tx) + lattice[y0][x1] * tx
        bottom = lattice[y1][x0] * (1 - tx) + lattice[y1][x1] * tx
        return top * (1 - ty) + bottom * ty

    return at


def cloud(base, seed):
    broad = value_noise(16, 4, seed)
    fine = value_noise(16, 8, seed + 100)
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            puff = 0.7 * broad(x, y) + 0.3 * fine(x, y)
            c = mix(shade(base, 0.9), shade(base, 1.05), puff)
            px[x, y] = c + (int(206 + 40 * puff),)
    return img


# ---- Flora --------------------------------------------------------------------------------------------------------


def scattered(colours, seed, count, size=(1, 2), alpha_gaps=True):
    rng = random.Random(seed)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for _ in range(count):
        cx, cy = rng.randrange(16), rng.randrange(16)
        colour = colours[rng.randrange(len(colours))]
        r = rng.randint(*size)
        for dy in range(-r, r + 1):
            for dx in range(-r, r + 1):
                if dx * dx + dy * dy <= r * r and rng.random() < 0.9:
                    px[(cx + dx) % 16, (cy + dy) % 16] = shade(colour, 1 + (rng.random() - 0.5) * 0.12) + (255,)
    return img


def petal_carpet():
    img = scattered(PETALS, 41, 26, (1, 2))
    px = img.load()
    # A few bright petal centres.
    rng = random.Random(42)
    for _ in range(6):
        px[rng.randrange(16), rng.randrange(16)] = P.PRISM_PINK + (255,)
    return img


def cloudmoss():
    return scattered(CLOUDMOSS, 51, 22, (1, 2))


def glow_moss():
    rng = random.Random(61)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for _ in range(9):
        x, y = rng.randrange(16), rng.randrange(16)
        for _ in range(rng.randint(4, 9)):
            px[x % 16, y % 16] = GLOW_MOSS[rng.randrange(3)] + (255,)
            x += rng.choice((-1, 0, 1))
            y += rng.choice((0, 1))
    return img


def lantern_vine(tip):
    rng = random.Random(71)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(16 if not tip else 9):
        x = 7 + int(round(math.sin(y * 0.8) * 1.2))
        px[x, y] = VINE + (255,)
        px[x + 1, y] = VINE_DARK + (255,)
        if y % 3 == 1:
            side = -1 if (y // 3) % 2 else 2
            px[x + side, y] = VINE + (255,)
            px[x + side + (1 if side > 0 else -1), y + 1 if y < 15 else y] = shade(VINE, 1.15) + (255,)
    if tip:
        # A small lantern-bud: gold shell, bright core.
        for y in range(9, 15):
            for x in range(5, 11):
                d = math.hypot(x - 7.5, (y - 11.7) * 1.15)
                if d < 2.9:
                    c = P.LANTERN_CORE if d < 1.3 else P.GOLD_LIGHT if d < 2.1 else P.GOLD
                    px[x, y] = c + (255,)
        px[7, 15] = P.GOLD + (255,)
        px[8, 15] = P.GOLD + (255,)
    return img


def prismite(stage):
    """Crystals for a cross model: taller and more of them as the cluster grows."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    shards = {0: [(7, 5, 2)], 1: [(5, 8, 2), (9, 10, 2)], 2: [(3, 9, 2), (7, 13, 2), (11, 10, 2)]}[stage]
    for cx, height, half in shards:
        for y in range(16 - height, 16):
            t = (16 - y) / height
            w = max(0, int(round(half * min(1.0, (1 - t) * 3))))
            for x in range(cx - w, cx + w + 1):
                if 0 <= x < 16:
                    edge = x == cx - w
                    c = PRISMITE[2] if edge else PRISMITE[1] if x <= cx else PRISMITE[0]
                    if y == 16 - height:
                        c = P.PEARL
                    px[x, y] = c + (255,)
    return img


# ---- Sky ----------------------------------------------------------------------------------------------------------


def save(img, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path)
    print("wrote", path.relative_to(ROOT))


def main():
    # The Stillstone set is painted by make_halcyon_terrain_blocks.py now, and the sky textures by make_sky_textures.py.
    blocks = {
        "stillstone_glass": glass(),
        "cloud": cloud(P.PEARL, 1),
        "rose_cloud": cloud((255, 220, 230), 2),
        "dusk_cloud": cloud((204, 202, 238), 3),
        "petal_carpet": petal_carpet(),
        "cloudmoss": cloudmoss(),
        "glow_moss": glow_moss(),
        "lantern_vine": lantern_vine(False),
        "lantern_vine_tip": lantern_vine(True),
        "small_prismite_bud": prismite(0),
        "large_prismite_bud": prismite(1),
        "prismite_cluster": prismite(2),
    }
    for name, img in blocks.items():
        save(img, TEXTURES / "block" / f"{name}.png")

    cols = 6
    sheet = Image.new("RGBA", (cols * 104 + 8, ((len(blocks) + cols - 1) // cols) * 104 + 8), (40, 36, 56, 255))
    for k, img in enumerate(blocks.values()):
        sheet.alpha_composite(img.resize((96, 96), Image.NEAREST), (8 + (k % cols) * 104, 8 + (k // cols) * 104))
    save(sheet, PREVIEW / "halcyon_blocks.png")


if __name__ == "__main__":
    main()
