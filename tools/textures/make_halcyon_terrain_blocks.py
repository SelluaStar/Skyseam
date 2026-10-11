"""The Halcyon's rock and masonry textures, painted with pixelkit (hue-shifted ramps, lit clusters, tileable).

Writes, under src/main/resources/assets/skyseam/textures/block/ (a name ending _1, _2, _3 is a random variant of the
block, picked per position by its blockstate):
  duskstone[_1.._3], cobbled_duskstone[_1]       dark violet-slate rock with blue and plum slabs, teal and rose flecks
  duskstone_bricks, cracked_duskstone_bricks, mossy_duskstone_bricks, duskstone_tiles, polished_duskstone,
  chiseled_duskstone, runed_duskstone (+ _glow overlay), duskstone_pillar(_top)   the Sundered Obelisk's masonry
  shoalrock[_1, _2], smooth_shoalrock            cream water-worn rock with shell flecks (reference 2)
  cliffstone[_1.._3], ochre_cliffstone[_1, _2], umber_cliffstone[_1, _2], mossy_cliffstone[_1]
                                                 layered tan, ochre and umber strata for the outer cliffs (image 11)
  hornstone[_1, _2]                              pale horn-grained rock of the Horncrown isles (image 9)
  stillstone[_1, _2], stillstone_bricks, mossy_stillstone_bricks, chiseled_stillstone_bricks, stillstone_pillar(_top)
                                                 the pale lavender limestone of Wright ruins
and build/previews/halcyon_terrain_blocks.png, every texture tiled 3 x 3 so seams show.

Colours come from the author's reference images: tools/reference/palette.json (images 5-7) and the hand-sampled
outer-world colours below (images 8-14, kept locally in reference/local).
"""
import math
from pathlib import Path

import numpy as np

import pixelkit as px

ROOT = Path(__file__).resolve().parents[2]
BLOCKS = ROOT / "src" / "main" / "resources" / "assets" / "skyseam" / "textures" / "block"
PREVIEW = ROOT / "build" / "previews"

N = px.N
R = px.ramp

# ---- Ramps (7 tones, dark to light) ------------------------------------------------------------------------------
DUSK = R("#4E4670", dark=0.27, light=0.24)
DUSK_BLUE = R("#3F4A74", dark=0.25, light=0.24)
DUSK_PLUM = R("#5B4464", dark=0.27, light=0.22)
DUSK_BRICK = R("#5A4D78", dark=0.29, light=0.24)
DUSK_POLISH = R("#625A8C", dark=0.30, light=0.22)
DUSK_FLECKS = [(88, 168, 170), (168, 112, 150), (150, 144, 190)]
MOSS = R("#4D8A5A", dark=0.24, light=0.26, cool=8, warm=22)
LICHEN = R("#6E9440", dark=0.22, light=0.24, cool=10, warm=18)
RUNE_GLOW = [(120, 236, 255), (176, 150, 255), (236, 252, 255)]

SHOAL = R("#D9C9AA", dark=0.30, light=0.10, cool=26, warm=8)
SHOAL_ROSE = R("#DCBFB0", dark=0.30, light=0.10, cool=26, warm=8)
SHOAL_GREY = R("#CAC4B6", dark=0.30, light=0.10, cool=26, warm=8)
SHELLS = [(240, 186, 202), (160, 206, 222), (252, 244, 228)]

CLIFF_SAND = R("#B98F4C", dark=0.30, light=0.17, cool=30, warm=10)
CLIFF_OCHRE = R("#94692F", dark=0.28, light=0.20, cool=30, warm=10)
CLIFF_UMBER = R("#654C2A", dark=0.24, light=0.24, cool=30, warm=12)
CLIFF_PALE = R("#D2B57A", dark=0.30, light=0.12, cool=30, warm=8)

HORN = R("#C8C7D4", dark=0.38, light=0.10, cool=20, warm=6, shadow_chroma=1.6)
HORN_WARM = R("#CFC6C4", dark=0.38, light=0.10, cool=20, warm=6, shadow_chroma=1.6)

STILL = R("#C6BDDB", dark=0.32, light=0.12, cool=18, warm=10)
STILL_ROSE = R("#CFC0D4", dark=0.32, light=0.12, cool=18, warm=10)



def by_label(tone, labels, ramps, weights, rng, alpha=None):
    """Paints each cluster in one of several ramps, picked per cluster, so a block mixes related hues."""
    choice = rng.choice(len(ramps), labels.max() + 1, p=weights)
    images = [np.array(px.paint(tone, r, alpha)) for r in ramps]
    out = images[0].copy()
    for k in range(1, len(ramps)):
        mask = choice[labels] == k
        out[mask] = images[k][mask]
    return px.Image.fromarray(out, "RGBA")


def no_edge(labels):
    """Pixels on a cluster edge, so flecks land inside stones, not on their rims."""
    return (px.roll(labels, 1, 0) != labels) | (px.roll(labels, -1, 0) != labels) | (px.roll(labels, 0, 1) != labels) \
        | (px.roll(labels, 0, -1) != labels)


# ---- Duskstone -------------------------------------------------------------------------------------------------------

def duskstone(seed):
    rng = np.random.default_rng(seed)
    tone, labels = px.stone(rng, cells=8, base=(2.7, 3.8), sx=0.62, sy=1.25, grain=0.22, cracks=1, edge=0.75)
    img = by_label(tone, labels, [DUSK, DUSK_BLUE, DUSK_PLUM], [0.5, 0.32, 0.18], rng)
    return px.dots(img, rng, DUSK_FLECKS, 1 + seed % 2, avoid=no_edge(labels))


def cobbled_duskstone(seed):
    rng = np.random.default_rng(seed)
    tone, labels = px.cobble(rng, cells=8, base=(3.0, 4.6), gap=0.7)
    img = by_label(tone, labels, [DUSK, DUSK_BLUE, DUSK_PLUM, DUSK_BRICK], [0.4, 0.25, 0.2, 0.15], rng)
    return px.dots(img, rng, DUSK_FLECKS[:2], 1, avoid=no_edge(labels))


def moss_mask(rng, cover=0.35, mortar=None, ledges=None):
    """Moss: tileable noise patches covering about {cover} of the block (no bias to its top, so a wall of it doesn't
    stripe), creeping further along mortar lines and resting on the tops of ledges."""
    n = px.fbm(rng, (2, 4, 8), (0.5, 0.3, 0.2))
    mask = n > np.quantile(n, 1 - cover)
    if mortar is not None:
        mask |= mortar & (n > np.quantile(n, 1 - cover * 2))
    if ledges is not None:
        mask |= ledges & (n > np.quantile(n, 0.25))
    return mask


def paint_moss(img, rng, mask, ramp=MOSS):
    t = np.full((N, N), 3.2) + 0.4 * px.fbm(rng, (4, 8), (0.5, 0.5)) * 2
    t += px.mask_bevel(mask, 1.1, 1.2)
    t = px.speckle(rng, t, 0.08, 1.0, 0.5)
    return px.overlay(img, ramp, np.clip(t, 0, 6), mask)


def duskstone_bricks(seed, cracked=False, mossy=False):
    rng = np.random.default_rng(seed)
    tone, labels, mortar = px.bricks(rng, height=4, length=8, base=(2.7, 4.3), mortar=0.9, chips=3)
    if cracked:
        for _ in range(3):
            ys, xs = np.nonzero(~mortar)
            j = rng.integers(0, len(ys))
            tone = px.crack(rng, tone, (ys[j], xs[j]), int(rng.integers(4, 8)), dark=0.3, light=0.9)
        tone = px.speckle(rng, tone, 0.05, 1.0, 0.4, avoid=mortar)
    img = by_label(tone, labels, [DUSK_BRICK, DUSK, DUSK_BLUE, DUSK_PLUM], [0.45, 0.25, 0.18, 0.12], rng)
    if mossy:
        img = paint_moss(img, rng, moss_mask(rng, 0.3, mortar=mortar))
    return img


def duskstone_tiles(seed):
    rng = np.random.default_rng(seed)
    tone, grout = px.tiles(rng, size=8, base=(3.2, 4.0), grout=0.7)
    labels = (np.arange(N)[:, None] // 8) * 2 + np.arange(N)[None, :] // 8
    return by_label(tone, labels, [DUSK_POLISH, DUSK_BRICK, DUSK_BLUE], [0.45, 0.35, 0.2], rng)


def polished_duskstone(seed):
    rng = np.random.default_rng(seed)
    return px.dots(px.paint(px.slab(rng, base=3.4, mottle=0.5, sheen=False), DUSK_POLISH), rng, DUSK_FLECKS[2:3], 1)


# The Wright seal: a ring round a four-pointed star, cut into the Obelisk's trim ('#' is cut).
SEAL = [
    "................",
    "................",
    ".....######.....",
    "....#......#....",
    "...#...##...#...",
    "..#....##....#..",
    "..#...#..#...#..",
    "..#.##....##.#..",
    "..#.##....##.#..",
    "..#...#..#...#..",
    "..#....##....#..",
    "...#...##...#...",
    "....#......#....",
    ".....######.....",
    "................",
    "................",
]


def chiseled_duskstone(seed):
    """The Wright seal cut into a polished block: the motif on the Obelisk's trim."""
    rng = np.random.default_rng(seed)
    tone = px.slab(rng, base=3.6, mottle=0.35, sheen=False)
    mask = np.array([[c == "#" for c in row] for row in SEAL])
    tone += px.carve(mask, 1.8, 0.9)
    tone[1, 1:N - 1] += 0.6
    tone[1:N - 1, 1] += 0.6
    return px.paint(np.clip(tone, 0, 6), DUSK_POLISH)


GLYPHS = [
    [(3, 2), (3, 3), (3, 4), (3, 5), (4, 5), (5, 5), (5, 4), (5, 3)],
    [(9, 2), (10, 2), (11, 2), (10, 3), (10, 4), (9, 5), (11, 5)],
    [(3, 9), (4, 10), (5, 11), (4, 12), (3, 13), (5, 9), (5, 13)],
    [(9, 9), (9, 10), (10, 11), (11, 11), (12, 10), (12, 9), (10, 13), (11, 13)],
    [(7, 7), (8, 7), (7, 8), (8, 8)],
]


def runed_duskstone(seed):
    """Dark masonry cut with Wright glyphs; the matching _glow overlay lights the cuts (drawn full-bright)."""
    rng = np.random.default_rng(seed)
    tone, labels, mortar = px.bricks(rng, height=8, length=16, base=(2.4, 3.2), mortar=0.6, chips=1, offset=0)
    mask = np.zeros((N, N), dtype=bool)
    for glyph in GLYPHS:
        for x, y in glyph:
            mask[y, x] = True
    tone += px.carve(mask, 1.6, 0.6)
    base = px.paint(np.clip(tone, 0, 6), DUSK)
    glow = np.zeros((N, N, 4), dtype=np.uint8)
    for glyph in GLYPHS:
        for k, (x, y) in enumerate(glyph):
            c = RUNE_GLOW[2] if k % 3 == 0 else RUNE_GLOW[0] if (x + y) % 2 else RUNE_GLOW[1]
            glow[y, x] = (*c, 255)
    return base, px.Image.fromarray(glow, "RGBA")


def duskstone_pillar(seed):
    rng = np.random.default_rng(seed)
    return px.paint(px.flutes(rng, base=3.3, width=4, ring_rows=(0, 14)), DUSK_BRICK)


def duskstone_pillar_top(seed):
    rng = np.random.default_rng(seed)
    return px.paint(px.rings(rng, base=3.5, rim=1, step=2.4, rim_tone=2.2, wobble=0), DUSK_POLISH)


# ---- Shoalrock, Cliffstone, Hornstone --------------------------------------------------------------------------------

def shoalrock(seed):
    rng = np.random.default_rng(seed)
    tone, labels = px.stone(rng, cells=6, base=(3.2, 4.6), sx=0.7, sy=1.1, grain=0.3, cracks=0, edge=0.7)
    img = by_label(tone, labels, [SHOAL, SHOAL_ROSE, SHOAL_GREY], [0.55, 0.3, 0.15], rng)
    return px.dots(img, rng, SHELLS, 1, avoid=no_edge(labels))


def smooth_shoalrock(seed):
    rng = np.random.default_rng(seed)
    return px.dots(px.paint(px.slab(rng, base=4.4, mottle=0.4, sheen=True), SHOAL), rng, SHELLS[:2], 2)


def cliffstone(seed, ramps, weights, bands=(3.2, 4.4, 2.6, 3.8, 4.8, 3.0, 2.2)):
    """Strata: each band drawn from one of the ramps, so a cliff shows sandy, ochre and umber layers like image 11."""
    rng = np.random.default_rng(seed)
    order = rng.permutation(len(bands))
    tone, band = px.strata(rng, bands=tuple(np.array(bands)[order]), wobble=1.1, pebbles=3, fissures=int(rng.random() < 0.5))
    return by_label(tone, band, ramps, weights, rng), band


def mossy_cliffstone(seed):
    """Cliffstone with moss resting on its ledges and spreading in patches, for cliff tops and overhangs."""
    rng = np.random.default_rng(seed + 50)
    img, band = cliffstone(seed, [CLIFF_SAND, CLIFF_OCHRE, CLIFF_PALE], [0.45, 0.35, 0.2])
    ledges = (px.roll(band, 1, 0) != band) | (px.roll(band, 2, 0) != band)
    return paint_moss(img, rng, moss_mask(rng, 0.25, ledges=ledges), LICHEN)


def hornstone(seed):
    rng = np.random.default_rng(seed)
    tone = px.grain_lines(rng, base=4.0, waves=3, amp=2.4, strength=1.2)
    labels, d1, d2, _ = px.voronoi(rng, 5, 0.6, 1.2)
    tone += px.bevel(labels, 0.9, 1.3, 0.5)
    tone = px.crack(rng, tone, length=5, dark=1.2, light=0.6)
    return by_label(np.clip(tone, 0, 6), labels, [HORN, HORN_WARM], [0.7, 0.3], rng)


# ---- Stillstone ------------------------------------------------------------------------------------------------------

def stillstone(seed):
    rng = np.random.default_rng(seed)
    tone, labels = px.stone(rng, cells=7, base=(3.6, 4.7), sx=0.8, sy=1.0, grain=0.22, cracks=0, edge=0.6)
    return by_label(tone, labels, [STILL, STILL_ROSE], [0.65, 0.35], rng)


def stillstone_bricks(seed, mossy=False):
    rng = np.random.default_rng(seed)
    tone, labels, mortar = px.bricks(rng, height=4, length=8, base=(3.4, 4.8), mortar=1.4, chips=3)
    img = by_label(tone, labels, [STILL, STILL_ROSE], [0.65, 0.35], rng)
    if mossy:
        img = paint_moss(img, rng, moss_mask(rng, 0.3, mortar=mortar))
    return img


def chiseled_stillstone(seed):
    """Square bricks cut with a stepped Wright frame."""
    rng = np.random.default_rng(seed)
    tone, labels, mortar = px.bricks(rng, height=8, length=8, base=(3.8, 4.6), mortar=1.6, chips=0, offset=0)
    mask = np.zeros((N, N), dtype=bool)
    for q in range(4):
        oy, ox = (q // 2) * 8, (q % 2) * 8
        for k in range(1, 6):
            mask[oy + 1, ox + k] = mask[oy + 5, ox + k] = True
            mask[oy + k, ox + 1] = mask[oy + k, ox + 5] = True
        mask[oy + 3, ox + 3] = True
    tone += px.carve(mask, 1.6, 0.7)
    return px.paint(np.clip(tone, 0, 6), STILL)


def stillstone_pillar(seed):
    rng = np.random.default_rng(seed)
    return px.paint(px.flutes(rng, base=4.2, width=4, ring_rows=(0, 14)), STILL)


def stillstone_pillar_top(seed):
    rng = np.random.default_rng(seed)
    return px.paint(px.rings(rng, base=4.4, rim=1, step=2.4, rim_tone=3.0, wobble=0), STILL)


# ---- Main ------------------------------------------------------------------------------------------------------------

def main():
    made = {}

    def add(name, img):
        made[name] = img

    for k in range(4):
        add("duskstone" + (f"_{k}" if k else ""), duskstone(11 + k * 7))
    for k in range(2):
        add("cobbled_duskstone" + (f"_{k}" if k else ""), cobbled_duskstone(31 + k * 5))
    add("duskstone_bricks", duskstone_bricks(41))
    add("cracked_duskstone_bricks", duskstone_bricks(41, cracked=True))
    add("mossy_duskstone_bricks", duskstone_bricks(41, mossy=True))
    add("duskstone_tiles", duskstone_tiles(43))
    add("polished_duskstone", polished_duskstone(45))
    add("chiseled_duskstone", chiseled_duskstone(47))
    runed, glow = runed_duskstone(49)
    add("runed_duskstone", runed)
    add("runed_duskstone_glow", glow)
    add("duskstone_pillar", duskstone_pillar(51))
    add("duskstone_pillar_top", duskstone_pillar_top(53))

    for k in range(3):
        add("shoalrock" + (f"_{k}" if k else ""), shoalrock(61 + k * 3))
    add("smooth_shoalrock", smooth_shoalrock(67))

    for k in range(4):
        add("cliffstone" + (f"_{k}" if k else ""), cliffstone(71 + k * 3, [CLIFF_SAND, CLIFF_PALE, CLIFF_OCHRE], [0.5, 0.3, 0.2])[0])
    for k in range(3):
        add("ochre_cliffstone" + (f"_{k}" if k else ""), cliffstone(83 + k * 3, [CLIFF_OCHRE, CLIFF_SAND, CLIFF_UMBER], [0.55, 0.25, 0.2])[0])
    for k in range(3):
        add("umber_cliffstone" + (f"_{k}" if k else ""), cliffstone(93 + k * 3, [CLIFF_UMBER, CLIFF_OCHRE], [0.7, 0.3],
                                                                    bands=(2.4, 3.6, 2.0, 3.0, 4.2, 2.6, 1.8))[0])
    for k in range(2):
        add("mossy_cliffstone" + (f"_{k}" if k else ""), mossy_cliffstone(103 + k * 3))
    for k in range(3):
        add("hornstone" + (f"_{k}" if k else ""), hornstone(111 + k * 3))

    for k in range(3):
        add("stillstone" + (f"_{k}" if k else ""), stillstone(121 + k * 3))
    add("stillstone_bricks", stillstone_bricks(131))
    add("mossy_stillstone_bricks", stillstone_bricks(131, mossy=True))
    add("chiseled_stillstone_bricks", chiseled_stillstone(133))
    add("stillstone_pillar", stillstone_pillar(135))
    add("stillstone_pillar_top", stillstone_pillar_top(137))

    BLOCKS.mkdir(parents=True, exist_ok=True)
    for name, img in made.items():
        img.save(BLOCKS / f"{name}.png")
    PREVIEW.mkdir(parents=True, exist_ok=True)
    px.sheet(list(made.values()), list(made.keys()), scale=4, tile=3, cols=7).save(PREVIEW / "halcyon_terrain_blocks.png")
    print(f"wrote {len(made)} textures and build/previews/halcyon_terrain_blocks.png")


if __name__ == "__main__":
    main()
