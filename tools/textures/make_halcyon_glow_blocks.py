"""The Halcyon's glowing things, painted with pixelkit: Prismite crystal and glow moss (the M3 follow-up's redo).

Writes, under src/main/resources/assets/skyseam/textures/block/:
  small_prismite_bud[_1], large_prismite_bud[_1], prismite_cluster[_1]   faceted violet crystal for cross models, each
                                           shard lit on its left facet with a bright ridge, deep violet at its root and
                                           shimmering rose and cyan towards its tip; two layouts of each size
  prismite_lamp                            a block of cut Prismite: lit facets round a glowing heart
  glow_moss, glow_moss_1, glow_moss_2, glow_moss_3   four growths of glowing moss for one face: a mat, branching
                                           strands, round colonies and scattered tufts, each freckled with bright spores
  glow_moss_spores[_1.._3]                 each growth's spores alone, drawn full-bright over it so they glow at night
and build/previews/halcyon_glow_blocks.png.

Deterministic: the same script always writes the same files. Run with tools/.venv/Scripts/python.exe.
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

# Prismite: violet, deepening to indigo in shadow and paling to lilac and white on its lit facets.
PRISM = R("#9460DA", n=9, dark=0.36, light=0.36, cool=16, warm=-10, shadow_chroma=1.15, light_chroma=0.45)
# Towards the tips the crystal shimmers rose, and here and there cyan.
TIP_ROSE = R("#D27ADC", n=9, dark=0.36, light=0.30, cool=18, warm=-6, shadow_chroma=1.1, light_chroma=0.4)
TIP_CYAN = R("#7FB8EE", n=9, dark=0.36, light=0.30, cool=10, warm=0, shadow_chroma=1.1, light_chroma=0.4)
SPARK = (250, 244, 255)

# Glow moss: deep teal mats, brighter where they grow thick, freckled with glowing cyan spores.
MOSS = R("#33A08F", n=7, dark=0.28, light=0.26, cool=10, warm=6, light_chroma=0.85)
SPORE = [(150, 255, 236), (196, 255, 244), (236, 255, 250)]


def blank():
    return np.zeros((N, N, 4), dtype=np.uint8)


def image(arr):
    return px.Image.fromarray(arr, "RGBA")


# ---- Prismite ------------------------------------------------------------------------------------------------------

def shard(tone, kind, cx, height, half, lean, depth, glow_row, tip):
    """Paints one crystal into tone (float, -1 for empty) and kind (0 body, 1 rose tip, 2 cyan tip), standing on the
    bottom row: a column {half} wide each side of cx, leaning {lean} pixels a row, with a pointed tip.

    Its left facet is lit, a bright ridge runs up between the left and front facets, the right facet and its edge are
    in shadow; it deepens towards its root, glows a little in a band across its middle, and is {depth} tones darker if
    it stands behind others."""
    tip_rows = max(2, int(round(half * 2.2)))
    for i in range(height):
        y = N - 1 - i
        above = height - 1 - i
        c = cx + lean * i
        hw = half if above >= tip_rows else max(0.35, half * (above + 0.6) / (tip_rows + 0.6))
        left = c - hw
        right = c + hw
        x0 = int(math.floor(left + 0.5))
        x1 = int(math.ceil(right - 0.5))
        x1 = max(x0, x1)
        height_shade = -1.6 * max(0.0, 1 - i / max(1.0, height * 0.35))
        for x in range(x0, x1 + 1):
            if not 0 <= x < N or not 0 <= y < N:
                continue
            u = 0.5 if x1 == x0 else (x - x0) / (x1 - x0)
            if above < tip_rows:
                t = 6.6 if u < 0.5 else 3.6
                if above == 0:
                    t = 7.6
            elif x == x1 and x1 > x0:
                t = 1.4
            elif x == x0:
                t = 4.4
            elif u < 0.42:
                t = 5.6
            elif u < 0.6:
                t = 6.6 if x == x0 + 1 or u < 0.5 else 4.6
            else:
                t = 3.2
            if abs(y - glow_row) <= 0 and 0.2 < u < 0.8 and above >= tip_rows:
                t += 1.2
            t += height_shade - depth
            tone[y, x] = max(0.0, t)
            kind[y, x] = tip if above < max(tip_rows, int(height * 0.3)) else 0


def prismite(seed, shards):
    """A crystal growth from (cx, height, half, lean, depth, tip) shards, painted back to front."""
    rng = np.random.default_rng(seed)
    tone = np.full((N, N), -1.0)
    kind = np.zeros((N, N), dtype=int)
    for cx, height, half, lean, depth, tip in sorted(shards, key=lambda s: -s[4]):
        glow_row = N - 1 - int(height * rng.uniform(0.35, 0.6))
        shard(tone, kind, cx, height, half, lean, depth, glow_row, tip)
    arr = blank()
    ramps = (PRISM, TIP_ROSE, TIP_CYAN)
    for y in range(N):
        for x in range(N):
            if tone[y, x] < 0:
                continue
            ramp = ramps[kind[y, x]]
            arr[y, x] = (*ramp[int(np.clip(round(tone[y, x]), 0, len(ramp) - 1))], 255)
    # Glints: the brightest pixel of each lit ridge and a speck or two at the tips.
    lit = np.argwhere(tone >= 7.0)
    for k in range(min(len(lit), 3)):
        y, x = lit[rng.integers(0, len(lit))]
        arr[y, x] = (*SPARK, 255)
    # A little crystal grit at the root, so it grows out of the rock rather than standing on it.
    for x in range(N):
        if tone[N - 1, x] >= 0 and rng.random() < 0.35:
            arr[N - 1, x] = (*PRISM[1], 255)
    return image(arr)


# Each size has two layouts: (cx, height, half-width, lean per row, depth, tip ramp 1 rose / 2 cyan).
PRISMITE = {
    "small_prismite_bud": [
        (3, [(7.5, 6, 1.4, 0.0, 0, 1), (10.5, 3, 0.9, 0.15, 0.6, 2)]),
        (4, [(8.0, 5, 1.3, -0.08, 0, 2), (5.2, 3, 0.9, -0.2, 0.6, 1), (10.6, 2, 0.7, 0.2, 0.9, 1)]),
    ],
    "large_prismite_bud": [
        (5, [(6.6, 10, 1.7, -0.05, 0, 1), (10.4, 7, 1.4, 0.15, 0.7, 2), (3.6, 5, 1.0, -0.25, 1.1, 1)]),
        (6, [(8.4, 9, 1.6, 0.06, 0, 2), (4.8, 7, 1.3, -0.2, 0.7, 1), (11.8, 5, 1.0, 0.28, 1.0, 1)]),
    ],
    "prismite_cluster": [
        (7, [(7.6, 14, 2.0, 0.0, 0, 1), (4.2, 10, 1.6, -0.22, 0.8, 2), (11.2, 11, 1.7, 0.2, 0.6, 1),
             (2.0, 6, 1.1, -0.3, 1.4, 1), (13.6, 6, 1.1, 0.3, 1.4, 2)]),
        (8, [(8.6, 13, 1.9, 0.08, 0, 2), (5.2, 11, 1.7, -0.16, 0.6, 1), (12.0, 8, 1.4, 0.26, 1.0, 1),
             (2.4, 7, 1.2, -0.3, 1.2, 2), (9.8, 5, 1.1, 0.1, 1.6, 1)]),
    ],
}


def prismite_lamp(seed):
    """Cut Prismite: faceted cells, each lit from the top left, brightening towards a glowing heart, with a dark
    seam between facets and a few sparks."""
    rng = np.random.default_rng(seed)
    labels, d1, d2, _ = px.voronoi(rng, 9)
    tone = rng.uniform(3.4, 5.6, 9)[labels]
    tone += 1.2 * px.bevel(labels, 1.0, 1.0, 0.6)
    c = (N - 1) / 2
    yy, xx = np.mgrid[0:N, 0:N]
    heart = np.clip(1 - np.hypot(xx - c, yy - c) / 7.5, 0, 1)
    tone += 2.0 * heart ** 1.5
    seam = (d2 - d1) < 0.5
    tone = np.where(seam, np.minimum(tone, 1.6 + 1.5 * heart), tone)
    img = px.paint(np.clip(tone, 0, len(PRISM) - 1), PRISM)
    rose = (labels % 3 == 0) & ~seam
    img = px.overlay(img, TIP_ROSE, np.clip(tone - 0.4, 0, 8), rose)
    return px.dots(img, rng, [SPARK, PRISM[8]], 4, avoid=seam)


# ---- Glow moss -----------------------------------------------------------------------------------------------------

def moss_from(cover, rng, spores):
    """Paints a moss cover (0 bare, else thickness) in the moss ramp, edges darker, and freckles it with spores."""
    arr = blank()
    on = cover > 0
    thick = np.where(on, cover, 0)
    edge = on & ~(px.roll(on, 1, 0) & px.roll(on, -1, 0) & px.roll(on, 0, 1) & px.roll(on, 0, -1))
    tone = 1.6 + 2.6 * thick + 0.8 * px.fbm(rng, (4, 8), (0.6, 0.4))
    tone = np.where(edge, tone - 1.2, tone)
    tone = tone + 1.0 * px.bevel(np.where(on, 1, 0), 0.6, 0.6, 0.3)
    for y in range(N):
        for x in range(N):
            if on[y, x]:
                arr[y, x] = (*MOSS[int(np.clip(round(tone[y, x]), 0, len(MOSS) - 1))], 255)
    spots = np.argwhere(on & (thick > 0.35))
    for k in range(min(spores, len(spots))):
        y, x = spots[rng.integers(0, len(spots))]
        arr[y, x] = (*SPORE[int(rng.integers(0, 3))], 255)
    return image(arr)


def moss_mat(seed):
    """A lacy mat over about half the face, in ragged patches with holes, thickest at their hearts."""
    rng = np.random.default_rng(seed)
    n = px.fbm(rng, (4, 8, 16), (0.5, 0.35, 0.15))
    cover = np.clip((n + 0.04) * 2.0, 0, 0.8)
    cover = np.where(cover > 0.1, cover, 0)
    return moss_from(cover, rng, 12)


def moss_strands(seed):
    """Strands branching out from two or three holdfasts, each ending in a bright spore."""
    rng = np.random.default_rng(seed)
    cover = np.zeros((N, N))
    tips = []
    for _ in range(3):
        sx, sy = rng.uniform(2, 14, 2)
        for _ in range(int(rng.integers(3, 6))):
            a = rng.uniform(0, math.tau)
            x, y = sx, sy
            for step in range(int(rng.integers(4, 8))):
                cover[int(y) % N, int(x) % N] = max(cover[int(y) % N, int(x) % N], 1 - step / 9)
                a += rng.uniform(-0.6, 0.6)
                x += math.cos(a)
                y += math.sin(a)
            tips.append((int(y) % N, int(x) % N))
        for dy in (-1, 0, 1):
            for dx in (-1, 0, 1):
                cover[int(sy + dy) % N, int(sx + dx) % N] = 1
    img = np.array(moss_from(cover, rng, 4))
    for y, x in tips:
        img[y, x] = (*SPORE[2], 255)
    return image(img)


def moss_colonies(seed):
    """Round colonies, each a darker ring round a glowing heart."""
    rng = np.random.default_rng(seed)
    cover = np.zeros((N, N))
    hearts = []
    yy, xx = np.mgrid[0:N, 0:N]
    for _ in range(4):
        cx, cy = rng.uniform(0, N, 2)
        r = rng.uniform(2.0, 3.6)
        dx = np.minimum(abs(xx - cx), N - abs(xx - cx))
        dy = np.minimum(abs(yy - cy), N - abs(yy - cy))
        d = np.hypot(dx, dy) + 0.5 * px.value_noise(rng, 8)
        cover = np.maximum(cover, np.where(d < r, 0.35 + 0.65 * (1 - d / r), 0))
        hearts.append((int(cy) % N, int(cx) % N))
    img = np.array(moss_from(cover, rng, 6))
    for y, x in hearts:
        img[y, x] = (*SPORE[2], 255)
        for dy, dx in ((0, 1), (1, 0)):
            if img[(y + dy) % N, (x + dx) % N, 3]:
                img[(y + dy) % N, (x + dx) % N] = (*SPORE[0], 255)
    return image(img)


def moss_tufts(seed):
    """Scattered small tufts, a spore on most."""
    rng = np.random.default_rng(seed)
    cover = np.zeros((N, N))
    for _ in range(11):
        x, y = rng.integers(0, N, 2)
        for _ in range(int(rng.integers(3, 7))):
            cover[y % N, x % N] = max(cover[y % N, x % N], rng.uniform(0.4, 1.0))
            x += int(rng.choice((-1, 0, 1)))
            y += int(rng.choice((-1, 0, 1)))
    return moss_from(cover, rng, 9)


def spores_of(moss):
    """The spore pixels of a moss growth alone (its brightest colours), for the full-bright layer."""
    arr = np.array(moss)
    keep = np.zeros(arr.shape[:2], dtype=bool)
    for colour in SPORE:
        keep |= np.all(arr[..., :3] == colour, axis=-1) & (arr[..., 3] > 0)
    out = blank()
    out[keep] = arr[keep]
    return image(out)


def main():
    made = {}
    for name, layouts in PRISMITE.items():
        for k, (seed, shards) in enumerate(layouts):
            made[name if k == 0 else f"{name}_1"] = prismite(seed, shards)
    made["prismite_lamp"] = prismite_lamp(11)
    made["glow_moss"] = moss_mat(21)
    made["glow_moss_1"] = moss_strands(22)
    made["glow_moss_2"] = moss_colonies(23)
    made["glow_moss_3"] = moss_tufts(24)
    for k in range(4):
        name = "glow_moss" if k == 0 else f"glow_moss_{k}"
        made[f"glow_moss_spores{'' if k == 0 else f'_{k}'}"] = spores_of(made[name])
    for name, img in made.items():
        img.save(BLOCKS / f"{name}.png")
        print("wrote", (BLOCKS / f"{name}.png").relative_to(ROOT))
    PREVIEW.mkdir(parents=True, exist_ok=True)
    px.sheet(list(made.values()), list(made.keys()), scale=4, tile=1, cols=6).save(PREVIEW / "halcyon_glow_blocks.png")
    print("wrote", (PREVIEW / "halcyon_glow_blocks.png").relative_to(ROOT))


if __name__ == "__main__":
    main()
