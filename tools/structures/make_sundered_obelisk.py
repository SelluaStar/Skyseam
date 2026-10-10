"""The Sundered Obelisk (spec section 14, reference image 3): the landmark you see first on arrival. [FIRST PASS]

A monolith of mossy Stillstone split in two, floating over the Anchorage's plaza, with a glowing orb in the gap
(it rhymes with the Seam), and a gateway at the plaza's edge facing the Arrival Lane. Within 48 blocks on every side,
so the author can save a hand-built replacement with one structure block (docs/DECISIONS.md K63).

Layout (template coordinates; the gate is on the south, +z, side):
  y 0          the plaza: a round floor of Stillstone bricks with warm mud-brick rings and a carved rose
  y 1-10       the gateway on the south edge, two Wright stalls (west and east), the Mooring Post spot, a pedestal
               for the first Almanac pages, low walls and lamp posts
  y 9-21       the monolith's lower piece, 11 wide and 7 deep, its top cut in a cradle for the orb
  y 22-27      the gap, open sky, with the orb in it: a ball of pearlescent light
  y 28-47      the upper piece, its foot cut in an arch over the orb, its top rounded
The monolith is about four times as tall as it is wide, as in the reference.

Markers (docs/STRUCTURE-MARKERS.md): motto, mooring_post, wrightling_stall (2), almanac_pages.

    tools/.venv/Scripts/python.exe tools/structures/make_sundered_obelisk.py [--seed 7]
writes data/skyseam/structure/sundered_obelisk.nbt and the preview build/previews/sundered_obelisk.png.
"""
import argparse
import math
import random
import subprocess
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from structure_lib import Structure  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src/main/resources/data/skyseam/structure/sundered_obelisk.nbt"
PREVIEW = ROOT / "build/previews/sundered_obelisk.png"

SIZE = 31  # footprint, odd so there is a centre column
HEIGHT = 48
C = SIZE // 2  # centre x and z

STONE = "skyseam:stillstone"
BRICKS = "skyseam:stillstone_bricks"
MOSSY = "skyseam:mossy_stillstone_bricks"
CHISELED = "skyseam:chiseled_stillstone_bricks"
PILLAR = "skyseam:stillstone_pillar"
SLAB = "skyseam:stillstone_brick_slab"
STAIRS = "skyseam:stillstone_brick_stairs"
WALL = "skyseam:stillstone_brick_wall"
WARM = "minecraft:mud_bricks"
MOSS = "minecraft:moss_block"
MOSS_CARPET = "minecraft:moss_carpet"
VINE = "minecraft:vine"
LIGHT = "minecraft:pearlescent_froglight"
TEAL = "minecraft:waxed_oxidized_cut_copper"
GOLD = "minecraft:gold_block"
VIOLET = "minecraft:amethyst_block"
PRISMITE = "skyseam:prismite_cluster"
PETALS = "skyseam:petal_carpet"

# The monolith: 11 wide (x) by 7 deep (z), centred on the plaza.
MX0, MX1 = C - 5, C + 5
MZ0, MZ1 = C - 3, C + 3
LOWER = (9, 21)
ORB_Y = 24
UPPER = (28, 47)


def plaza(s, rng):
    for x in range(SIZE):
        for z in range(SIZE):
            d = math.hypot(x - C, z - C)
            if d > C + 0.4:
                continue
            ring = int(d)
            name = WARM if ring in (5, 11) else CHISELED if d < 1.5 else BRICKS
            if name == BRICKS and rng.random() < 0.08:
                name = MOSSY
            s.set(x, 0, z, name)
            # A low wall round the rim, open at the gate (south) and at the Mooring Post (south-east).
            if C - 1.2 < d <= C + 0.4 and not (z > C + 8 and abs(x - C) < 6) and not (x > C + 7 and z > C + 7):
                s.set(x, 1, z, WALL, up=True)
    # Petals drifted against the wall.
    for _ in range(40):
        a = rng.uniform(0, math.tau)
        r = rng.uniform(C - 4, C - 2)
        x, z = int(round(C + r * math.cos(a))), int(round(C + r * math.sin(a)))
        if s.get(x, 1, z) is None:
            s.set(x, 1, z, PETALS)


def gate(s):
    """The cliff gateway on the south edge: two banded pillars, an arch, and the motto on the lintel."""
    z = SIZE - 2
    for side in (C - 5, C + 5):
        for y in range(1, 10):
            s.set(side, y, z, PILLAR, axis="y")
            s.set(side, y, z - 1, PILLAR, axis="y")
        s.set(side, 5, z, TEAL)
        s.set(side, 10, z, GOLD)
        s.set(side, 10, z - 1, LIGHT)
    for x in range(C - 4, C + 5):
        span = abs(x - C)
        # The arch's underside steps down towards the pillars.
        bottom = 9 if span <= 1 else 8 if span <= 3 else 7
        for y in range(bottom, 11):
            s.set(x, y, z, BRICKS)
            s.set(x, y, z - 1, BRICKS)
    s.set(C, 10, z, CHISELED)
    s.set(C, 11, z, LIGHT)
    s.set(C - 1, 11, z, SLAB, type="bottom")
    s.set(C + 1, 11, z, SLAB, type="bottom")
    # The motto hangs on the lintel's outer face (DECISIONS K63; the line itself is in the lang file).
    s.marker(C, 9, z + 1, "motto", "south")
    # Steps down to the gate.
    for x in range(C - 4, C + 5):
        s.set(x, 0, SIZE - 1, STAIRS, facing="north", half="bottom", shape="straight")


def stalls(s):
    """Two Wright stalls, west and east: a counter under a slab awning on four posts. The Wrightling comes in M10."""
    for cx, facing in ((4, "east"), (SIZE - 5, "west")):
        for dx in (-1, 1):
            for dz in (-1, 1):
                for y in range(1, 4):
                    s.set(cx + dx, y, C + dz * 2, WALL, up=True)
        for dx in range(-1, 2):
            for dz in range(-2, 3):
                s.set(cx + dx, 4, C + dz, SLAB, type="bottom")
        for dz in range(-1, 2):
            s.set(cx + (1 if facing == "east" else -1), 1, C + dz, BRICKS)
        s.set(cx, 4, C, LIGHT)
        s.marker(cx, 1, C, "wrightling_stall", facing)


def mooring_and_pages(s):
    # The Mooring Post stands at the south-east gap in the wall, where a ship can come alongside (M4).
    s.marker(C + 10, 1, C + 10, "mooring_post", "south")
    # The first Almanac pages rest on a carved pedestal by the west stall (M4).
    s.set(C - 9, 1, C + 7, CHISELED)
    s.marker(C - 9, 2, C + 7, "almanac_pages", "south")
    # Lamp posts round the plaza: soft white light.
    for angle in (0.5, 2.0, 3.9, 5.4):
        x, z = int(round(C + 11 * math.cos(angle))), int(round(C + 11 * math.sin(angle)))
        for y in range(1, 4):
            s.set(x, y, z, WALL, up=True)
        s.set(x, 4, z, LIGHT)


def monolith_block(rng, x, y, z, rows_from_top, moss_patches):
    """Clean bricks with pilasters at the corners; moss gathers on the top rows and in a few patches that drip."""
    if x in (MX0, MX1) and z in (MZ0, MZ1):
        return PILLAR
    if rows_from_top < 2 and rng.random() < 0.7:
        return MOSS if rows_from_top == 0 and rng.random() < 0.5 else MOSSY
    for px, py, pz, r in moss_patches:
        if abs(x - px) + abs(z - pz) * 0.6 + max(0, y - py) * 0.6 + max(0, py - y) * 0.25 < r and rng.random() < 0.75:
            return MOSSY
    return BRICKS


def monolith(s, rng):
    for y0, y1 in (LOWER, UPPER):
        patches = [(rng.randint(MX0, MX1), rng.randint(y0 + 3, y1 - 2), rng.randint(MZ0, MZ1), rng.uniform(2.0, 3.5)) for _ in range(3)]
        for y in range(y0, y1 + 1):
            for x in range(MX0, MX1 + 1):
                for z in range(MZ0, MZ1 + 1):
                    # Hollow inside, one block of wall, so the file stays small.
                    if MX0 < x < MX1 and MZ0 < z < MZ1 and y0 < y < y1:
                        continue
                    # The upper piece's top is rounded at the sides.
                    if (y0, y1) == UPPER and y > y1 - 3:
                        inset = (y - (y1 - 3)) * 2 - 1
                        if x < MX0 + inset or x > MX1 - inset:
                            continue
                    s.set(x, y, z, monolith_block(rng, x, y, z, y1 - y, patches))
        # Teal and gold: a band round the foot of each piece, gold studs on the corners.
        for x in range(MX0, MX1 + 1):
            for z in (MZ0, MZ1):
                s.set(x, y0, z, TEAL)
        for z in range(MZ0, MZ1 + 1):
            for x in (MX0, MX1):
                s.set(x, y0, z, TEAL)
        for x in (MX0, MX1):
            for z in (MZ0, MZ1):
                s.set(x, y0, z, GOLD)
    # The cradle cut into the lower piece's top and the arch cut into the upper piece's foot, round the orb.
    for x in range(MX0, MX1 + 1):
        for y in range(LOWER[1] - 3, UPPER[0] + 4):
            if math.hypot(x - C, y - ORB_Y) < 5.2:
                for z in range(MZ0, MZ1 + 1):
                    s.clear(x, y, z)
    # An arched doorway at the foot of each piece, as on the reference's faces.
    for y0 in (LOWER[0], UPPER[0] + 6):
        for z in (MZ0, MZ1):
            for y in range(y0 + 1, y0 + 6):
                for x in range(C - 1, C + 2):
                    if not (y == y0 + 5 and x != C):
                        s.set(x, y, z, "skyseam:stillstone_glass" if y > y0 + 1 else CHISELED)
    # The rounded top's cap, and the windows: tall arched niches on the north and south faces of the upper piece.
    for x in range(MX0 + 5, MX1 - 4):
        s.set(x, UPPER[1], C, CHISELED)
    # Violet veins running up the east face, and crystal growing from them.
    for y0, y1 in (LOWER, UPPER):
        z = MZ0 + 2
        for y in range(y0 + 2, y1 - 1):
            if rng.random() < 0.7:
                s.set(MX1, y, z + (y // 4) % 3, VIOLET)
        s.set(MX1 + 1, y0 + 4, z, PRISMITE, facing="east", waterlogged=False)
    # Vines hanging from the ledges on the north and south faces.
    for z, side in ((MZ0 - 1, "south"), (MZ1 + 1, "north")):
        for x in range(MX0 + 1, MX1):
            if rng.random() < 0.35:
                top = UPPER[1] - 5 if rng.random() < 0.5 else LOWER[1] - 5
                for y in range(top, top - rng.randint(3, 9), -1):
                    s.set(x, y, z, VINE, **{side: True})
    # Moss on the ledges.
    for x in range(MX0, MX1 + 1):
        for z in range(MZ0, MZ1 + 1):
            if s.get(x, LOWER[1], z) in (BRICKS, MOSSY) and s.get(x, LOWER[1] + 1, z) is None and rng.random() < 0.4:
                s.set(x, LOWER[1] + 1, z, MOSS_CARPET)


def orb(s):
    for x in range(C - 3, C + 4):
        for y in range(ORB_Y - 3, ORB_Y + 4):
            for z in range(C - 3, C + 4):
                if math.hypot(x - C, y - ORB_Y, z - C) < 2.6:
                    s.set(x, y, z, LIGHT)


def build(seed):
    rng = random.Random(seed)
    s = Structure(SIZE, HEIGHT, SIZE)
    plaza(s, rng)
    gate(s)
    stalls(s)
    mooring_and_pages(s)
    monolith(s, rng)
    orb(s)
    return s


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--seed", type=int, default=7)
    args = parser.parse_args()
    s = build(args.seed)
    count = s.write(OUT)
    print("wrote", OUT.relative_to(ROOT), f"({count} blocks, size {s.size})")
    print("markers:", ", ".join(f"{name} at {x},{y},{z}" for name, x, y, z in s.markers()))
    print("palette:", ", ".join(s.palette_names()))
    subprocess.run([sys.executable, str(Path(__file__).with_name("preview.py")), str(OUT), str(PREVIEW), "--scale", "5"], check=True)


if __name__ == "__main__":
    main()
