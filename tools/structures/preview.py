"""Isometric preview of a structure file (spec section 14: "view an isometric preview before moving on").

    tools/.venv/Scripts/python.exe tools/structures/preview.py <file.nbt> <out.png> [--scale 6]

Draws every block as a small shaded cube, back to front, from the south-east and from the north-west side by side,
in a flat colour per block (COLOURS below, a guess from the block's name otherwise). Data markers show as bright
magenta so they are easy to check.
"""
import argparse
import sys
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
from structure_lib import load  # noqa: E402

COLOURS = {
    "skyseam:stillstone": (210, 203, 218),
    "skyseam:stillstone_bricks": (214, 206, 222),
    "skyseam:mossy_stillstone_bricks": (170, 190, 150),
    "skyseam:chiseled_stillstone_bricks": (226, 220, 232),
    "skyseam:stillstone_pillar": (220, 214, 228),
    "skyseam:stillstone_brick_slab": (214, 206, 222),
    "skyseam:stillstone_brick_stairs": (214, 206, 222),
    "skyseam:stillstone_brick_wall": (200, 192, 210),
    "skyseam:stillstone_glass": (226, 218, 255),
    "skyseam:petal_carpet": (200, 140, 220),
    "skyseam:glow_moss": (120, 232, 212),
    "skyseam:prismite_cluster": (180, 130, 236),
    "skyseam:large_prismite_bud": (180, 130, 236),
    "minecraft:moss_block": (110, 150, 80),
    "minecraft:moss_carpet": (120, 160, 86),
    "minecraft:vine": (90, 140, 70),
    "minecraft:mud_bricks": (150, 112, 88),
    "minecraft:amethyst_block": (150, 100, 210),
    "minecraft:pearlescent_froglight": (250, 236, 246),
    "minecraft:waxed_oxidized_cut_copper": (90, 180, 160),
    "minecraft:gold_block": (250, 210, 80),
    "minecraft:structure_block": (255, 0, 255),
}


def colour(name):
    if name in COLOURS:
        return COLOURS[name]
    return (180, 180, 180)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c)


def render(blocks, size, scale, flip):
    sx, sy, sz = size
    w = (sx + sz) * scale * 2 + scale * 4
    h = (sx + sz) * scale + sy * scale * 2 + scale * 4
    img = Image.new("RGBA", (w, h), (36, 32, 52, 255))
    draw = ImageDraw.Draw(img)
    ox = sz * scale * 2 + scale * 2
    oy = sy * scale * 2 + scale * 2

    def project(x, y, z):
        if flip:
            x, z = sx - 1 - x, sz - 1 - z
        return ox + (x - z) * scale * 2, oy + (x + z) * scale - y * scale * 2

    order = sorted(blocks.items(), key=lambda item: ((sx - 1 - item[0][0] if flip else item[0][0])
                                                     + (sz - 1 - item[0][2] if flip else item[0][2]), item[0][1]))
    for (x, y, z), name in order:
        if name == "minecraft:air":
            continue
        c = colour(name)
        px, py = project(x, y, z)
        top = [(px, py - scale * 2), (px + scale * 2, py - scale), (px, py), (px - scale * 2, py - scale)]
        left = [(px - scale * 2, py - scale), (px, py), (px, py + scale * 2), (px - scale * 2, py + scale)]
        right = [(px, py), (px + scale * 2, py - scale), (px + scale * 2, py + scale), (px, py + scale * 2)]
        draw.polygon(top, fill=shade(c, 1.08))
        draw.polygon(left, fill=shade(c, 0.82))
        draw.polygon(right, fill=shade(c, 0.66))
    return img


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("structure")
    parser.add_argument("out")
    parser.add_argument("--scale", type=int, default=6)
    args = parser.parse_args()
    size, blocks = load(args.structure)
    a = render(blocks, size, args.scale, flip=False)
    b = render(blocks, size, args.scale, flip=True)
    sheet = Image.new("RGBA", (a.width + b.width, max(a.height, b.height)), (36, 32, 52, 255))
    sheet.paste(a, (0, 0))
    sheet.paste(b, (a.width, 0))
    Path(args.out).parent.mkdir(parents=True, exist_ok=True)
    sheet.save(args.out)
    print("wrote", args.out, f"({len(blocks)} blocks, size {size})")


if __name__ == "__main__":
    main()
