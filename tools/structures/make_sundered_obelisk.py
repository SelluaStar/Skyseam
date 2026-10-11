"""Generates the Sundered Obelisk (spec section 14, reference image 3), the landmark an arriving crew sees first.

Rebuilt for round two (docs/DECISIONS.md K83): bigger, longer, and floating in mid-air over open sea, not standing on
an island. About 238 blocks tall, from the bottom up:

  y   0-26   the foot: an inverted, stepped keel of masonry hanging under the terrace, broken at its tip
  y  26-33   the terrace: a round plaza 42 across with a balustrade, the gate (two pillars, a round arch over a purple
             portal, the Wrights' motto), low wings, lamp columns, the mooring post and the stalls
  y  36-97   the lower shaft, floating: 30 wide and 14 deep, a framed slab with a tall arched recess on each face,
             pilasters, cornices every 12 blocks, an arched door at its foot and a round notch in its top
  y  98-119  the gap: a dark orb 13 across with a glowing seam, cradled by the notches, ringed by floating rubble
  y 120-219  the upper shaft: framed, arched recesses, a notch in its foot, its top corners rounded
  y 226-237  the capstone, floating free above it
  and pieces of masonry drifting round the whole height.

Every face blends its stone (the author: "material blending and variation rather than one block one texture"): pale
Stillstone brick mixed with plain and chiseled Stillstone and calcite, stained by a slow 3D noise and by gradients
(heavier at each shaft's foot and top and round the orb) into polished, tiled, bricked and cracked Duskstone, the mauve
of reference image 3; moss and ivy creep over it, heavier on the ledges and the weather side; glowing runes run in
bands across the recesses, and Prismite veins fade in and out. No face is one block.

The gate faces south (+z) in the file; the game turns it to face the lane. The model is cut into slices 48 blocks tall
(data/skyseam/structure/sundered_obelisk/slice_0.nbt .. slice_4), each small enough to save by hand with one structure
block (docs/STRUCTURE-MARKERS.md); the game stacks them. Markers (in slice 0): motto, mooring_post,
wrightling_stall (2), almanac_pages.

Also writes build/previews/sundered_obelisk.png (isometric) and build/previews/sundered_obelisk_vs_reference.png
(front elevation beside reference image 3). Deterministic.
"""
import math
import random
import sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from structure_lib import Structure  # noqa: E402
from preview import colour, render  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "src" / "main" / "resources" / "data" / "skyseam" / "structure" / "sundered_obelisk"
PREVIEWS = ROOT / "build" / "previews"
REFERENCE = ROOT / "reference" / "3-sundered-obelisk.webp"

W, D = 48, 48
H = 238
SLICE = 48
CX, CZ = 24, 24
TERRACE = 26
#: The orb's height above the terrace; the plan's lookAt() must match (Landmarks.ORB_ABOVE_TERRACE).
ORB_Y = 108

S_BRICKS = "skyseam:stillstone_bricks"
S_MOSSY = "skyseam:mossy_stillstone_bricks"
S_CHISELED = "skyseam:chiseled_stillstone_bricks"
S_STONE = "skyseam:stillstone"
S_PILLAR = "skyseam:stillstone_pillar"
S_SLAB = "skyseam:stillstone_brick_slab"
S_STAIRS = "skyseam:stillstone_brick_stairs"
S_WALL = "skyseam:stillstone_brick_wall"
CALCITE = "minecraft:calcite"
D_BRICKS = "skyseam:duskstone_bricks"
D_CRACKED = "skyseam:cracked_duskstone_bricks"
D_MOSSY = "skyseam:mossy_duskstone_bricks"
D_TILES = "skyseam:duskstone_tiles"
D_POLISHED = "skyseam:polished_duskstone"
D_CHISELED = "skyseam:chiseled_duskstone"
D_RUNED = "skyseam:runed_duskstone"
D_PILLAR = "skyseam:duskstone_pillar"
D_COBBLED = "skyseam:cobbled_duskstone"
LAMP = "skyseam:prismite_lamp"
AMETHYST = "minecraft:amethyst_block"
PORTAL = "minecraft:purple_stained_glass"
MOSS = "minecraft:moss_block"
MOSS_CARPET = "minecraft:moss_carpet"
VINE = "minecraft:vine"
IVY = "skyseam:hanging_ivy"
CHAIN = "minecraft:chain"
PETALS = "skyseam:petal_carpet"

rng = random.Random(1508)
model = {}


def put(x, y, z, name, **props):
    if 0 <= x < W and 0 <= y < H and 0 <= z < D:
        model[(x, y, z)] = (name, props)


def clear(x, y, z):
    model.pop((x, y, z), None)


def solid(x, y, z):
    entry = model.get((x, y, z))
    return entry is not None and entry[0] not in (VINE, IVY, MOSS_CARPET, PETALS, CHAIN)


# ---- Noise -----------------------------------------------------------------------------------------------------------

def _hash(ix, iy, iz, salt):
    h = (ix * 374761393 + iy * 668265263 + iz * 2147483647 + salt * 144665) & 0xFFFFFFFF
    h = (h ^ (h >> 13)) * 1274126177 & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFFFF) / float(0xFFFFFF)


def value3(x, y, z, scale, salt=0):
    """Smooth 3D value noise in 0 to 1, features about {scale} blocks across."""
    fx, fy, fz = x / scale, y / scale, z / scale
    ix, iy, iz = math.floor(fx), math.floor(fy), math.floor(fz)
    tx, ty, tz = fx - ix, fy - iy, fz - iz
    tx, ty, tz = (t * t * (3 - 2 * t) for t in (tx, ty, tz))
    total = 0.0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (tx if dx else 1 - tx) * (ty if dy else 1 - ty) * (tz if dz else 1 - tz)
                total += w * _hash(ix + dx, iy + dy, iz + dz, salt)
    return total


def fbm3(x, y, z, scale, salt=0):
    return 0.6 * value3(x, y, z, scale, salt) + 0.3 * value3(x, y, z, scale / 2.3, salt + 7) + 0.1 * value3(x, y, z, scale / 5.1, salt + 13)


def smoothstep(a, b, v):
    t = max(0.0, min(1.0, (v - a) / (b - a)))
    return t * t * (3 - 2 * t)


# ---- The blended stone -----------------------------------------------------------------------------------------------

LOW0, LOW1 = 36, 97
UP0, UP1 = 120, 219
CAP0, CAP1 = 226, 237
X0, X1 = 9, 38         # 30 wide
Z0, Z1 = 17, 30        # 14 deep
NOTCH = 6.5


def stain(x, y, z):
    """How far the pale stone has weathered to mauve Duskstone here, about 0 to 1."""
    s = fbm3(x, y, z, 11.0, 1) - 0.45
    s += 0.45 * math.exp(-((y - ORB_Y) / 13.0) ** 2)           # round the orb
    for base in (LOW0, UP0, TERRACE - 26):
        s += 0.5 * (1 - smoothstep(base, base + 18, y)) * (1 if y >= base - 30 else 0)
    s += 0.35 * smoothstep(UP1 - 26, UP1, y)                    # the upper shaft's crown
    s += 0.3 * smoothstep(LOW1 - 14, LOW1, y) * (1 if y <= LOW1 else 0)
    return s


def mossy(x, y, z, bias=0.0):
    """How mossy a block is: patches, heavier on the west and south (the weather side)."""
    m = fbm3(x, y, z, 5.0, 2) + bias
    if x < CX - 10:
        m += 0.08
    if z > CZ + 4:
        m += 0.06
    return m


def stone(x, y, z, bias=0.0, moss_bias=0.0, pale=False):
    """The blended wall stone at a block: pale brick by default, stained and mossed by noise. Variants are picked in
    patches (a hash per 2-by-2-by-2 cluster, with a little noise between), not block by block, so from afar a face shades
    smoothly from pale to mauve, as in reference image 3, instead of looking grainy."""
    s = stain(x, y, z) + bias + 0.06 * (fbm3(x, y, z, 2.0, 9) - 0.5)
    m = mossy(x, y, z, moss_bias)
    r = _hash(x // 2, y // 2, z // 2, 11) * 0.8 + _hash(x, y, z, 12) * 0.2
    if pale:
        s -= 0.3
    if m > 0.74:
        return D_MOSSY if s > 0.5 else S_MOSSY
    if s > 0.78:
        return D_CRACKED if r < 0.35 else D_BRICKS if r < 0.85 else D_COBBLED
    if s > 0.6:
        return D_BRICKS if r < 0.45 else D_POLISHED if r < 0.75 else D_TILES if r < 0.92 else D_CRACKED
    if s > 0.5:
        # The edge of a stain: a band of the mid-tone, mauve-grey polished Duskstone, with pale patches.
        return D_POLISHED if r < 0.6 else S_STONE
    if r < 0.72:
        return S_BRICKS
    if r < 0.95:
        return S_STONE
    return CALCITE


# ---- Shafts ----------------------------------------------------------------------------------------------------------

def upper_outline(x, y):
    """The upper shaft's silhouette: its top corners rounded off."""
    corner = 8
    if y <= UP1 - corner or X0 + corner <= x <= X1 - corner:
        return True
    cx = X0 + corner if x < CX else X1 - corner
    return (x - cx) ** 2 + (y - (UP1 - corner)) ** 2 <= corner ** 2 + 2


def shaft(y0, y1, notch_top, notch_bottom, outline=lambda x, y: True, moss_bias=0.0):
    """A framed slab: a shell three blocks thick (hollow inside), arched recesses on both broad faces, pilasters,
    cornices with rune bands, and moss and ivy on its ledges."""
    for y in range(y0, y1 + 1):
        for x in range(X0, X1 + 1):
            if not outline(x, y):
                continue
            for z in range(Z0, Z1 + 1):
                if notch_top and (x - CX + 0.5) ** 2 + (y - (y1 + 1)) ** 2 < NOTCH ** 2:
                    continue
                if notch_bottom and (x - CX + 0.5) ** 2 + (y - (y0 - 1)) ** 2 < NOTCH ** 2:
                    continue
                inner = X0 + 3 <= x <= X1 - 3 and Z0 + 3 <= z <= Z1 - 3 and y0 + 3 <= y <= y1 - 3 and outline(x, y + 3)
                if inner:
                    continue
                put(x, y, z, stone(x, y, z, 0, moss_bias))
    # The frame: the slab's outer edges, chiseled pale stone and polished Duskstone trim in turn.
    for y in range(y0, y1 + 1):
        for x in range(X0, X1 + 1):
            if not outline(x, y):
                continue
            edge = x in (X0, X1) or y in (y0, y1) or not outline(x, y + 1)
            if not edge:
                continue
            for z in range(Z0, Z1 + 1):
                if (x, y, z) in model and (z in (Z0, Z1) or x in (X0, X1)):
                    band = (y // 4) % 3
                    put(x, y, z, S_CHISELED if band == 0 else D_POLISHED if band == 1 and stain(x, y, z) > 0.3 else stone(x, y, z, -0.2))
    # The recesses: one tall arch per broad face, three deep at the back, ribbed by two pilasters.
    top = y1 - 5
    rx0, rx1 = X0 + 4, X1 - 4
    mid = (rx0 + rx1) / 2
    half = (rx1 - rx0) / 2 + 0.5
    head = top - half

    def in_recess(x, y):
        return y0 + 4 <= y <= top and rx0 <= x <= rx1 and (y <= head or (x - mid) ** 2 + (y - head) ** 2 <= half ** 2) and outline(x, y + 4)

    rib_left, rib_right = int(mid - half / 2), int(mid + half / 2) + 1
    for face, inward in ((Z1, -1), (Z0, 1)):
        for x in range(rx0, rx1 + 1):
            for y in range(y0 + 4, top + 1):
                if not in_recess(x, y):
                    continue
                if x in (rx0, rx1) or not in_recess(x, y + 1) or y == y0 + 4:
                    put(x, y, face, S_CHISELED if (x + y) % 2 else D_CHISELED if stain(x, y, face) > 0.4 else S_CHISELED)
                    continue
                clear(x, y, face)
                back = face + inward
                if x in (rib_left, rib_right):
                    put(x, y, back, S_PILLAR if stain(x, y, back) < 0.55 else D_PILLAR, axis="y")
                    continue
                vein = fbm3(x * 1.6, y, back, 3.0, 4) > 0.66 and math.sin(y / 9.0 + x) > -0.2
                put(x, y, back, AMETHYST if vein else stone(x, y, back, 0.25, -0.15))
        # Rune bands across the recess, glowing, every 12 blocks.
        for y in range(y0 + 10, top - 4, 12):
            for x in range(rx0 + 1, rx1):
                if in_recess(x, y) and x not in (rib_left, rib_right):
                    put(x, y, face + inward, D_RUNED)
    # Cornices: a ledge of slabs every 12 blocks round all four sides; moss on them, ivy and vines under them.
    for y in range(y0 + 12, y1 - 6, 12):
        for x in range(X0 - 1, X1 + 2):
            if not outline(min(max(x, X0), X1), y):
                continue
            for z, facing in ((Z1 + 1, "north"), (Z0 - 1, "south")):
                put(x, y, z, S_SLAB, type="top")
                drape(x, y, z, facing)
        for z in range(Z0, Z1 + 1):
            for x, facing in ((X0 - 1, "east"), (X1 + 1, "west")):
                if outline(min(max(x, X0), X1), y):
                    put(x, y, z, S_SLAB, type="top")
                    drape(x, y, z, facing)
    for (x, y, z), (name, _) in list(model.items()):
        if name == S_SLAB and y0 <= y <= y1 and mossy(x, y, z) > 0.5 and (x, y + 1, z) not in model:
            put(x, y + 1, z, MOSS_CARPET)


def drape(x, y, z, facing):
    """Ivy or vines hanging from a ledge, in places."""
    if mossy(x, y, z) < 0.6 or rng.random() > 0.35:
        return
    length = 2 + rng.randrange(5)
    if rng.random() < 0.5:
        for dy in range(1, length + 1):
            if (x, y - dy, z) in model:
                break
            put(x, y - dy, z, IVY, tip="true" if dy == length else "false")
    else:
        for dy in range(1, length + 1):
            if (x, y - dy, z) in model:
                break
            put(x, y - dy, z, VINE, **{facing: "true"})


def door(y0, height, face=Z1):
    """An arched door, 7 wide, set two blocks into the face, framed in pilasters, a lamp over it."""
    for x in range(CX - 3, CX + 3):
        for y in range(y0, y0 + height):
            arch = y < y0 + height - 3 or (x - CX + 0.5) ** 2 + (y - (y0 + height - 3)) ** 2 <= 3.3 ** 2
            if arch:
                clear(x, y, face)
                clear(x, y, face - 1)
                put(x, y, face - 2, D_TILES)
    for x in (CX - 4, CX + 3):
        for y in range(y0, y0 + height - 2):
            put(x, y, face, D_PILLAR, axis="y")
    put(CX, y0 + height, face, D_CHISELED)
    put(CX - 1, y0 + height, face, D_CHISELED)
    put(CX, y0 + height - 1, face - 2, LAMP)


def orb():
    """The dark orb in the gap, a glowing seam round its middle, runes about its poles."""
    r = 6.4
    for x in range(CX - 8, CX + 8):
        for y in range(ORB_Y - 8, ORB_Y + 8):
            for z in range(CZ - 8, CZ + 8):
                d = math.sqrt((x - CX + 0.5) ** 2 + (y - ORB_Y) ** 2 + (z - CZ + 0.5) ** 2)
                if d <= r:
                    if abs(y - ORB_Y) <= 0 and d > r - 1.4:
                        name = LAMP
                    elif abs(y - ORB_Y) in (2, 3) and d > r - 1.2 and (x + z) % 3 == 0:
                        name = D_RUNED
                    else:
                        name = D_POLISHED if rng.random() < 0.6 else D_TILES
                    put(x, y, z, name)


def rubble(count, y_range, radius_range, salt):
    """Broken masonry floating round the Obelisk: lumps two to five blocks across, of the same blended stone."""
    local = random.Random(salt)
    for _ in range(count):
        a = local.random() * math.pi * 2
        r = local.uniform(*radius_range)
        cx = CX + math.cos(a) * r
        cz = CZ + math.sin(a) * r
        cy = local.uniform(*y_range)
        size = local.uniform(1.0, 2.6)
        for x in range(int(cx - size) - 1, int(cx + size) + 2):
            for y in range(int(cy - size) - 1, int(cy + size) + 2):
                for z in range(int(cz - size) - 1, int(cz + size) + 2):
                    d = math.sqrt((x + 0.5 - cx) ** 2 + ((y + 0.5 - cy) * 1.3) ** 2 + (z + 0.5 - cz) ** 2)
                    if d <= size and local.random() < 0.88:
                        put(x, y, z, stone(x, y, z, 0.1, 0.05))


def capstone():
    """The capstone floating over the upper shaft: a smaller slab with a rounded top, a lamp at its heart."""
    cx0, cx1 = X0 + 4, X1 - 4
    cz0, cz1 = Z0 + 2, Z1 - 2
    for y in range(CAP0, CAP1 + 1):
        for x in range(cx0, cx1 + 1):
            for z in range(cz0, cz1 + 1):
                ry = (y - CAP0) / (CAP1 - CAP0)
                inset = int(round(6 * ry * ry))
                if cx0 + inset <= x <= cx1 - inset:
                    put(x, y, z, stone(x, y, z, 0.15, 0.1))
    put(CX, CAP0 + 3, cz1, LAMP)
    put(CX - 1, CAP0 + 3, cz1, LAMP)


# ---- The foot and the terrace ----------------------------------------------------------------------------------------

def foot():
    """An inverted stepped keel hanging under the terrace, broken off at its tip, chains and ivy hanging from it."""
    for y in range(0, TERRACE + 1):
        t = (TERRACE - y) / TERRACE
        r = 21 * (1 - t) ** 0.8 + 3
        r = math.floor(r / 2.5) * 2.5 + 1.5                         # stepped
        for x in range(CX - 23, CX + 24):
            for z in range(CZ - 23, CZ + 24):
                d = math.hypot(x - CX + 0.5, z - CZ + 0.5)
                ragged = r - 2.2 * fbm3(x, y * 2, z, 4.0, 6)
                if d <= ragged and not (y < 6 and rng.random() < 0.25):
                    inner = d < ragged - 3 and y > 2 and y < TERRACE - 1
                    if not inner:
                        put(x, y, z, stone(x, y, z, 0.35, 0.1))
        # Rune bands round the keel.
        if y % 7 == 3:
            for a in range(0, 360, 6):
                x = int(round(CX - 0.5 + math.cos(math.radians(a)) * (r - 0.5)))
                z = int(round(CZ - 0.5 + math.sin(math.radians(a)) * (r - 0.5)))
                if (x, y, z) in model:
                    put(x, y, z, D_RUNED)
    # Chains hanging from the terrace's rim, ivy from the keel's steps.
    for a in range(0, 360, 30):
        x = int(round(CX - 0.5 + math.cos(math.radians(a + 15)) * 22))
        z = int(round(CZ - 0.5 + math.sin(math.radians(a + 15)) * 22))
        for dy in range(1, 6 + rng.randrange(10)):
            if (x, TERRACE - dy, z) in model:
                break
            put(x, TERRACE - dy, z, CHAIN, axis="y")
    for (x, y, z), (name, _) in list(model.items()):
        if y < TERRACE and (x, y - 1, z) not in model and rng.random() < 0.05:
            for dy in range(1, 3 + rng.randrange(7)):
                if (x, y - dy, z) in model:
                    break
                put(x, y - dy, z, IVY, tip="false")


def terrace():
    """The plaza: blended paving with a carved ring and inlaid paths, a balustrade round its rim with gaps."""
    for x in range(CX - 23, CX + 24):
        for z in range(CZ - 23, CZ + 24):
            r = math.hypot(x - CX + 0.5, z - CZ + 0.5)
            if r > 21.5:
                continue
            if 16.5 < r < 17.6:
                name = S_CHISELED if (x + z) % 2 else D_CHISELED
            elif r < 6.5 or abs(x - CX + 0.5) <= 1.5 and z > CZ:
                name = D_TILES if rng.random() < 0.8 else D_POLISHED
            else:
                name = stone(x, TERRACE, z, -0.25, -0.1)
            put(x, TERRACE, z, name)
            if 20.5 < r <= 21.5:
                gap = (z > CZ and abs(x - CX + 0.5) < 3) or rng.random() < 0.08
                if not gap:
                    put(x, TERRACE + 1, z, S_WALL)
            elif r > 18 and rng.random() < 0.3:
                put(x, TERRACE + 1, z, PETALS)


def gate():
    """Two pillars, a round arch over a purple portal, a lintel with the motto, low wings and lamp columns."""
    y0 = TERRACE + 1
    zf0, zf1 = CZ + 10, CZ + 14
    for px0 in (CX - 11, CX + 6):
        for x in range(px0, px0 + 6):
            for z in range(zf0, zf1 + 1):
                for y in range(y0, y0 + 16):
                    corner = x in (px0, px0 + 5) and z in (zf0, zf1)
                    put(x, y, z, S_PILLAR if corner else stone(x, y, z, -0.1), **({"axis": "y"} if corner else {}))
        for x in range(px0 - 1, px0 + 7):
            for z in range(zf0 - 1, zf1 + 2):
                put(x, y0 + 13, z, S_CHISELED if (x + z) % 2 else D_CHISELED)
        put(px0 + 2, y0 + 7, zf1, LAMP)
        put(px0 + 3, y0 + 7, zf1, LAMP)
        put(px0 + 2, y0 + 3, zf1, D_RUNED)
    # The lintel and the arch between the pillars.
    for x in range(CX - 5, CX + 6):
        for z in range(zf0, zf1 + 1):
            for y in range(y0, y0 + 17):
                inside_arch = y <= y0 + 6 or (x - CX + 0.5) ** 2 + (y - (y0 + 6)) ** 2 <= 5.2 ** 2
                if not inside_arch:
                    put(x, y, z, stone(x, y, z, -0.1))
    for x in range(CX - 12, CX + 13):
        for z in range(zf0 - 1, zf1 + 2):
            put(x, y0 + 17, z, S_SLAB, type="bottom")
    put(CX, y0 + 18, zf1, D_CHISELED)
    put(CX, y0 + 19, zf1, LAMP)
    # The portal: purple glass framed in amethyst, set back in the arch.
    for x in range(CX - 5, CX + 6):
        for y in range(y0, y0 + 13):
            if (x - CX + 0.5) ** 2 + (y - (y0 + 6)) ** 2 <= 5.2 ** 2 or y <= y0 + 6:
                framed = (x - CX + 0.5) ** 2 + (y - (y0 + 6)) ** 2 > 4.2 ** 2 and y > y0 + 6 or x in (CX - 5, CX + 5)
                put(x, y, zf0 + 2, AMETHYST if framed else PORTAL)
    # Wings: low stepped walls with lamps at their ends.
    for wx0, wx1 in ((CX - 19, CX - 12), (CX + 12, CX + 19)):
        for x in range(wx0, wx1 + 1):
            for z in range(zf0 - 1, zf1 + 1):
                end = wx0 if wx0 < CX else wx1
                height = 7 - abs(x - end) // 3
                for y in range(y0, y0 + height):
                    put(x, y, z, stone(x, y, z, 0.05, 0.1))
                put(x, y0 + height, z, S_WALL)
        end = wx0 if wx0 < CX else wx1
        put(end, y0 + 7, zf1, LAMP)
    # Lamp columns at the back of the plaza.
    for x, z in ((CX - 14, CZ - 12), (CX + 13, CZ - 12)):
        for y in range(y0, y0 + 9):
            put(x, y, z, S_PILLAR if y % 4 else D_PILLAR, axis="y")
        put(x, y0 + 9, z, LAMP)


def markers(s):
    """Markers in slice 0 (docs/STRUCTURE-MARKERS.md)."""
    s.marker(CX, TERRACE + 14, CZ + 15, "motto", "south")
    s.marker(CX + 3, TERRACE + 1, CZ + 20, "mooring_post", "south")
    s.marker(CX - 15, TERRACE + 1, CZ - 2, "wrightling_stall", "east")
    s.marker(CX + 14, TERRACE + 1, CZ - 2, "wrightling_stall", "west")
    s.marker(CX - 8, TERRACE + 1, CZ + 18, "almanac_pages", "south")


def build():
    foot()
    terrace()
    gate()
    shaft(LOW0, LOW1, notch_top=True, notch_bottom=False, moss_bias=-0.04)
    door(LOW0, 11)
    door(LOW0, 11, face=Z0 + 0)
    orb()
    shaft(UP0, UP1, notch_top=False, notch_bottom=True, outline=upper_outline, moss_bias=0.04)
    door(UP0 + 2, 12)
    capstone()
    rubble(16, (ORB_Y - 9, ORB_Y + 9), (11, 18), 41)
    rubble(26, (TERRACE + 20, CAP1 - 4), (17, 21.5), 43)


def write_slices():
    OUT.mkdir(parents=True, exist_ok=True)
    count = (H + SLICE - 1) // SLICE
    for k in range(count):
        height = min(SLICE, H - k * SLICE)
        s = Structure(W, height, D)
        for (x, y, z), (name, props) in model.items():
            if k * SLICE <= y < k * SLICE + height:
                s.set(x, y - k * SLICE, z, name, **props)
        if k == 0:
            markers(s)
        n = s.write(OUT / f"slice_{k}.nbt")
        print(f"wrote {(OUT / f'slice_{k}.nbt').relative_to(ROOT)} ({n} blocks, size {s.size})")
    for stale in OUT.glob("slice_*.nbt"):
        if int(stale.stem.split("_")[1]) >= count:
            stale.unlink()


def previews():
    PREVIEWS.mkdir(parents=True, exist_ok=True)
    blocks = {pos: name for pos, (name, _) in model.items()}
    iso = render(blocks, (W, H, D), 2, False)
    iso.save(PREVIEWS / "sundered_obelisk.png")
    # Front elevation: looking north at the south face, nearest block wins, darker further back.
    scale = 3
    front = Image.new("RGB", (W * scale, H * scale), (186, 214, 240))
    px = front.load()
    for x in range(W):
        for y in range(H):
            for z in range(D - 1, -1, -1):
                name = blocks.get((x, y, z))
                if name:
                    c = colour(name)
                    f = 1.0 - (D - 1 - z) * 0.008
                    for dx in range(scale):
                        for dy in range(scale):
                            px[x * scale + dx, (H - 1 - y) * scale + dy] = tuple(int(v * f) for v in c)
                    break
    sheet = front
    if REFERENCE.exists():
        ref = Image.open(REFERENCE).convert("RGB")
        ref = ref.resize((int(ref.width * front.height / ref.height), front.height))
        sheet = Image.new("RGB", (ref.width + front.width + 20, front.height), (24, 22, 30))
        sheet.paste(ref, (0, 0))
        sheet.paste(front, (ref.width + 20, 0))
    sheet.save(PREVIEWS / "sundered_obelisk_vs_reference.png")
    print("wrote build/previews/sundered_obelisk.png and sundered_obelisk_vs_reference.png")


if __name__ == "__main__":
    build()
    write_slices()
    previews()
