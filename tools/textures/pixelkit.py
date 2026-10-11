"""Skyseam's pixel-art kit: tileable 16 x 16 block textures built the way a pixel artist paints them.

Every texture is painted in *tones*: indices into a short hue-shifted ramp (cool, slightly more saturated shadows; warm,
softer highlights), never in free colours, so each block keeps a small, coherent palette. Shapes are clusters, not
per-pixel noise: stones are Voronoi cells, bricks are rows, strata are wavy bands, and each shape is lit from the top
left (a highlight on its upper and left edges, a shadow on its lower and right edges). Everything is painted on a
torus, so each texture tiles without seams.

The generators return a 16 x 16 array of tones (floats, rounded when painted) or an RGBA image. Scripts in this folder
(make_halcyon_terrain_blocks.py, make_halcyon_flora_blocks.py) call them with a ramp and a seed per variant.
"""
import math

import numpy as np
from PIL import Image

N = 16


# ---- Colour ----------------------------------------------------------------------------------------------------------

def hex_rgb(value):
    value = value.lstrip("#")
    return tuple(int(value[k:k + 2], 16) for k in (0, 2, 4))


def _to_linear(c):
    c = c / 255.0
    return np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)


def _to_srgb(c):
    c = np.where(c <= 0.0031308, 12.92 * c, 1.055 * np.power(np.clip(c, 0, None), 1 / 2.4) - 0.055)
    return c * 255.0


def oklab(rgb):
    r, g, b = _to_linear(np.array(rgb, dtype=float))
    l = 0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b
    m = 0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b
    s = 0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b
    l, m, s = np.cbrt(l), np.cbrt(m), np.cbrt(s)
    return (0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s)


def from_oklab(L, a, b):
    l = (L + 0.3963377774 * a + 0.2158037573 * b) ** 3
    m = (L - 0.1055613458 * a - 0.0638541728 * b) ** 3
    s = (L - 0.0894841775 * a - 1.2914855480 * b) ** 3
    r = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s
    g = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
    bb = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s
    return _to_srgb(np.array([r, g, bb]))


def oklch(rgb):
    L, a, b = oklab(rgb)
    return L, math.hypot(a, b), math.degrees(math.atan2(b, a)) % 360


def from_oklch(L, C, h):
    """An sRGB colour from OKLCH, with chroma pulled in until it fits the gamut."""
    for _ in range(40):
        rgb = from_oklab(L, C * math.cos(math.radians(h)), C * math.sin(math.radians(h)))
        if rgb.min() >= -0.5 and rgb.max() <= 255.5:
            break
        C *= 0.92
    return tuple(int(round(v)) for v in np.clip(rgb, 0, 255))


def _toward(h, target, amount):
    """Turns hue h towards target by at most amount degrees, the short way round."""
    d = (target - h + 540) % 360 - 180
    return (h + max(-amount, min(amount, d))) % 360


COOL = 285  # shadows lean towards blue-violet
WARM = 85   # highlights lean towards yellow


def ramp(base, n=7, dark=0.30, light=0.22, cool=22, warm=16, shadow_chroma=1.05, light_chroma=0.7, pivot=None):
    """n tones from darkest to lightest round a base colour (hex or rgb), the base at index pivot (default the middle).

    Lightness spreads {dark} below and {light} above the base (OKLab L). Each step darker turns the hue a little
    towards blue-violet and each step lighter towards yellow, up to {cool} and {warm} degrees at the ends; chroma is
    scaled by {shadow_chroma} at the dark end and {light_chroma} at the light end.
    """
    if isinstance(base, str):
        base = hex_rgb(base)
    L0, C0, h0 = oklch(base)
    pivot = n // 2 if pivot is None else pivot
    tones = []
    for i in range(n):
        if i < pivot:
            t = (pivot - i) / max(1, pivot)
            L = L0 - dark * t
            h = _toward(h0, COOL, cool * t)
            C = C0 * (1 + (shadow_chroma - 1) * t)
        elif i > pivot:
            t = (i - pivot) / max(1, n - 1 - pivot)
            L = L0 + light * t
            h = _toward(h0, WARM, warm * t)
            C = C0 * (1 + (light_chroma - 1) * t)
        else:
            L, C, h = L0, C0, h0
        tones.append(from_oklch(max(0.02, min(0.99, L)), C, h))
    return tones


def shift(rgb, dL=0.0, dC=1.0, dh=0.0):
    """A colour moved in OKLCH: lightness by dL, chroma scaled by dC, hue turned dh degrees."""
    L, C, h = oklch(rgb)
    return from_oklch(L + dL, C * dC, (h + dh) % 360)


# ---- Tileable noise --------------------------------------------------------------------------------------------------

def value_noise(rng, period=4, size=N):
    """Smooth value noise in [-1, 1] that tiles every {size} pixels; period lattice points across (divides size)."""
    lattice = rng.uniform(-1, 1, (period, period))
    out = np.zeros((size, size))
    for y in range(size):
        for x in range(size):
            fx = x * period / size
            fy = y * period / size
            x0, y0 = int(fx), int(fy)
            tx, ty = fx - x0, fy - y0
            tx = tx * tx * (3 - 2 * tx)
            ty = ty * ty * (3 - 2 * ty)
            a = lattice[y0 % period, x0 % period]
            b = lattice[y0 % period, (x0 + 1) % period]
            c = lattice[(y0 + 1) % period, x0 % period]
            d = lattice[(y0 + 1) % period, (x0 + 1) % period]
            out[y, x] = (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty
    return out


def fbm(rng, periods=(2, 4, 8), weights=(0.55, 0.3, 0.15), size=N):
    total = np.zeros((size, size))
    for p, w in zip(periods, weights):
        total += w * value_noise(rng, p, size)
    return total / sum(weights)


def voronoi(rng, count, sx=1.0, sy=1.0, size=N):
    """Cells round {count} random points on a torus. Returns (labels, d1, d2): the nearest point's index, and the
    distances to the nearest and second-nearest points (d2 - d1 is small along cell borders). sx and sy stretch the
    metric, so sx < 1 makes cells wider than tall."""
    pts = rng.uniform(0, size, (count, 2))
    labels = np.zeros((size, size), dtype=int)
    d1 = np.zeros((size, size))
    d2 = np.zeros((size, size))
    for y in range(size):
        for x in range(size):
            dx = np.abs(pts[:, 0] - (x + 0.5))
            dy = np.abs(pts[:, 1] - (y + 0.5))
            dx = np.minimum(dx, size - dx) * sx
            dy = np.minimum(dy, size - dy) * sy
            d = np.hypot(dx, dy)
            order = np.argsort(d)
            labels[y, x] = order[0]
            d1[y, x] = d[order[0]]
            d2[y, x] = d[order[1]]
    return labels, d1, d2, pts


def roll(a, dy, dx):
    return np.roll(np.roll(a, dy, axis=0), dx, axis=1)


# ---- Shading helpers -------------------------------------------------------------------------------------------------

def bevel(labels, hi=1.0, lo=1.0, deep=0.6):
    """Light each cluster from the top left: +hi where the pixel above or to the left is another cluster, -lo where
    the pixel below or to the right is, and a further -deep in the corner where both are."""
    up = roll(labels, 1, 0) != labels
    left = roll(labels, 0, 1) != labels
    down = roll(labels, -1, 0) != labels
    right = roll(labels, 0, -1) != labels
    out = np.zeros(labels.shape)
    out += hi * (up | left) * ~(down | right)
    out -= lo * (down | right)
    out -= deep * (down & right)
    return out


def mask_bevel(mask, hi=1.0, lo=1.0):
    """bevel() for a raised shape given as a boolean mask (its outside is one cluster)."""
    labels = np.where(mask, 1, 0)
    return bevel(labels, hi, lo, 0) * mask


def carve(mask, depth=2.0, rim=1.0):
    """Shading for a recess cut into a surface: the floor sits {depth} tones down, its top-left inner edge darker
    still (in shadow) and its bottom-right inner edge lighter (catching light)."""
    out = np.where(mask, -depth, 0.0)
    above = roll(mask, 1, 0)
    leftn = roll(mask, 0, 1)
    below = roll(mask, -1, 0)
    rightn = roll(mask, 0, -1)
    out -= rim * (mask & (~above | ~leftn))
    out += rim * 1.2 * (mask & (~below | ~rightn) & above & leftn)
    return out


def speckle(rng, tone, amount, delta=1.0, cluster=0.0, avoid=None):
    """Shifts about {amount} of the pixels by +-delta in small clusters (cluster > 0 grows each speck sideways)."""
    out = tone.copy()
    count = int(amount * tone.size)
    for _ in range(count):
        y, x = rng.integers(0, N, 2)
        if avoid is not None and avoid[y, x]:
            continue
        d = delta if rng.random() < 0.5 else -delta
        out[y, x] += d
        if cluster and rng.random() < cluster:
            out[y, (x + 1) % N] += d
    return out


def crack(rng, tone, start=None, length=6, dark=-2.5, light=0.8):
    """A thin crack wandering down and sideways, its lower-right lip catching light."""
    out = tone.copy()
    y, x = start if start is not None else rng.integers(0, N, 2)
    dx = rng.choice([-1, 1])
    for _ in range(length):
        out[y % N, x % N] = dark
        lip = ((y + 1) % N, (x + 1) % N)
        if out[lip] > dark:
            out[lip] = max(out[lip], tone[lip] + light)
        if rng.random() < 0.55:
            y += 1
        if rng.random() < 0.6:
            x += dx
        if rng.random() < 0.2:
            dx = -dx
    return out


# ---- Painting --------------------------------------------------------------------------------------------------------

def paint(tones, colours, alpha=None):
    """An RGBA image from a tone array and a ramp; alpha (0-255 array or None) cuts holes."""
    idx = np.clip(np.round(tones), 0, len(colours) - 1).astype(int)
    pal = np.array(colours, dtype=np.uint8)
    rgb = pal[idx]
    a = np.full(tones.shape, 255, dtype=np.uint8) if alpha is None else alpha.astype(np.uint8)
    return Image.fromarray(np.dstack([rgb, a]), "RGBA")


def overlay(base, colours, tones, mask):
    """Paints tones in another ramp over an image where mask is true."""
    top = paint(tones, colours)
    arr = np.array(base)
    t = np.array(top)
    arr[mask] = t[mask]
    return Image.fromarray(arr, "RGBA")


def dots(image, rng, colours, count, avoid=None, size=1):
    """Single bright or accent pixels (mineral flecks, shell bits), each from colours at random."""
    arr = np.array(image)
    placed = 0
    tries = 0
    while placed < count and tries < count * 40:
        tries += 1
        y, x = rng.integers(0, N, 2)
        if avoid is not None and avoid[y, x]:
            continue
        c = colours[rng.integers(0, len(colours))]
        arr[y, x, :3] = c
        if size > 1:
            arr[y, (x + 1) % N, :3] = shift(c, -0.08)
        placed += 1
    return Image.fromarray(arr, "RGBA")


# ---- Stone -----------------------------------------------------------------------------------------------------------

def stone(rng, n=7, cells=10, base=(2.6, 4.2), sx=1.0, sy=1.0, grain=0.35, cracks=1, crevice=True, edge=1.0):
    """Natural rock: Voronoi slabs at mixed tones, each bevel-lit ({edge} scales the bevel), with clustered grain and
    a crack or two."""
    labels, d1, d2, _ = voronoi(rng, cells, sx, sy)
    cell_tone = rng.uniform(base[0], base[1], cells)
    tone = cell_tone[labels]
    tone += edge * bevel(labels, 1.0, 1.0, 0.6)
    if crevice:
        border = (d2 - d1) < 0.55
        tone = np.where(border & (roll(labels, -1, 0) != labels), np.minimum(tone, base[0] - 1.6), tone)
    tone += grain * fbm(rng, (4, 8, 16), (0.4, 0.4, 0.2)) * 2
    tone = speckle(rng, tone, 0.04, 1.0, 0.4)
    for _ in range(cracks):
        tone = crack(rng, tone, length=int(rng.integers(3, 6)), dark=base[0] - 1.6)
    return np.clip(tone, 0, n - 1), labels


def cobble(rng, n=7, cells=8, base=(2.5, 4.8), gap=0.9):
    """Rounded stones with dark gaps: each stone domed (lit top left) and set in shadowed mortar."""
    labels, d1, d2, pts = voronoi(rng, cells)
    cell_tone = rng.uniform(base[0], base[1], cells)
    tone = cell_tone[labels].astype(float)
    # Dome each stone: lighter towards its top-left, darker towards its bottom-right.
    for y in range(N):
        for x in range(N):
            px, py = pts[labels[y, x]]
            dx = (x + 0.5 - px + N / 2) % N - N / 2
            dy = (y + 0.5 - py + N / 2) % N - N / 2
            tone[y, x] += -0.32 * (dx + dy)
    gap = (d2 - d1) < gap
    tone = np.where(gap, 0.6 + 0.6 * (roll(gap, 1, 0) & ~gap), tone)
    tone = np.where(gap & roll(~gap, 1, 0), 0.0, tone)
    tone += 0.25 * fbm(rng, (8, 16), (0.6, 0.4)) * 2
    return np.clip(tone, 0, n - 1), labels


def bricks(rng, n=7, height=4, length=8, base=(2.8, 4.4), mortar=1.0, chips=4, grain=0.25, offset=None):
    """Running-bond bricks: a tone per brick, a lit top and left edge, a shadowed bottom and right, chipped corners,
    and mortar darkest just under each brick."""
    tone = np.zeros((N, N))
    labels = np.zeros((N, N), dtype=int)
    is_mortar = np.zeros((N, N), dtype=bool)
    rows = N // height
    offset = length // 2 if offset is None else offset
    k = 0
    tones = {}
    for r in range(rows):
        shift_x = (r % 2) * offset
        for c in range(N // length):
            k += 1
            tones[k] = rng.uniform(base[0], base[1])
            for y in range(r * height, (r + 1) * height):
                for x in range(c * length, (c + 1) * length):
                    xx = (x + shift_x) % N
                    labels[y, xx] = k
                    if y == (r + 1) * height - 1 or x == (c + 1) * length - 1:
                        is_mortar[y, xx] = True
    for y in range(N):
        for x in range(N):
            tone[y, x] = tones[labels[y, x]]
    brick = ~is_mortar
    up = roll(is_mortar, 1, 0)
    left = roll(is_mortar, 0, 1)
    down = roll(is_mortar, -1, 0)
    right = roll(is_mortar, 0, -1)
    tone += 1.0 * (brick & (up | left) & ~down & ~right)
    tone -= 0.9 * (brick & (down | right))
    tone += grain * fbm(rng, (8, 16), (0.5, 0.5)) * 2
    tone = speckle(rng, tone, 0.04, 1.0, 0.3, avoid=is_mortar)
    m = np.where(roll(brick, 1, 0), 0.0, mortar)  # mortar right under a brick is in shadow
    tone = np.where(is_mortar, m, tone)
    # Chipped corners: a brick corner pixel knocked back to mortar.
    for _ in range(chips):
        ys, xs = np.nonzero(brick & (down | right))
        if len(ys):
            j = rng.integers(0, len(ys))
            tone[ys[j], xs[j]] = mortar
    return np.clip(tone, 0, n - 1), labels, is_mortar


def tiles(rng, n=7, size=8, base=(3.0, 4.3), grout=0.5, mottle=0.35):
    """Square tiles, each its own tone and slightly domed, with dark grout lines."""
    labels = np.zeros((N, N), dtype=int)
    for y in range(N):
        for x in range(N):
            labels[y, x] = (y // size) * (N // size) + x // size
    count = labels.max() + 1
    tone = rng.uniform(base[0], base[1], count)[labels]
    grout_mask = ((np.arange(N)[:, None] % size) == size - 1) | ((np.arange(N)[None, :] % size) == size - 1)
    tone += bevel(np.where(grout_mask, -1, labels), 1.0, 0.9, 0.4)
    tone += mottle * fbm(rng, (4, 8), (0.6, 0.4)) * 2
    return np.clip(np.where(grout_mask, grout, tone), 0, n - 1), grout_mask


def slab(rng, n=7, base=3.6, border=True, mottle=0.45, sheen=True):
    """A polished slab: one smooth face with soft clouding, a bevelled rim and a faint diagonal sheen."""
    tone = np.full((N, N), base) + mottle * fbm(rng, (2, 4, 8), (0.5, 0.35, 0.15)) * 2
    if sheen:
        for y in range(N):
            for x in range(N):
                d = (x + y) - 9
                if -1 <= d <= 0:
                    tone[y, x] += 0.6
    if border:
        tone[0, :] += 1.0
        tone[:, 0] += 1.0
        tone[N - 1, :] -= 1.2
        tone[:, N - 1] -= 1.2
        tone[0, N - 1] = base
        tone[N - 1, 0] = base - 0.4
    return np.clip(tone, 0, n - 1)


def strata(rng, n=7, bands=(3.0, 4.2, 2.4, 3.6, 4.6, 2.8), wobble=1.2, pebbles=3, fissures=1, grain=0.3):
    """Layered sedimentary rock: wavy horizontal bands of different tones, each with a lit upper lip and a shadowed
    underside (so a cliff of it reads in ledges), pebbles and a vertical fissure."""
    count = len(bands)
    edges = np.sort(rng.uniform(0, N, count))
    phase = rng.uniform(0, 2 * math.pi, count)
    band = np.zeros((N, N), dtype=int)
    # Each pixel belongs to the band whose (wobbled) start is closest above it.
    for x in range(N):
        starts = [(edges[k] + wobble * math.sin(2 * math.pi * x / N * (1 + k % 2) + phase[k])) % N for k in range(count)]
        for y in range(N):
            best, best_d = 0, 99
            for k, s in enumerate(starts):
                d = (y + 0.5 - s) % N
                if d < best_d:
                    best, best_d = k, d
            band[y, x] = best
    tone = np.array(bands)[band].astype(float)
    above = roll(band, 1, 0) != band
    below = roll(band, -1, 0) != band
    tone += 1.0 * above - 1.2 * below
    tone += grain * fbm(rng, (4, 8, 16), (0.3, 0.4, 0.3)) * 2
    tone = speckle(rng, tone, 0.05, 1.0, 0.5)
    for _ in range(fissures):
        x = int(rng.integers(0, N))
        y0 = int(rng.integers(0, N))
        for k in range(int(rng.integers(4, 8))):
            y = (y0 + k) % N
            tone[y, x] = 0.4
            tone[y, (x + 1) % N] += 0.8
            if rng.random() < 0.3:
                x = (x + rng.choice([-1, 1])) % N
    for _ in range(pebbles):
        y, x = rng.integers(0, N, 2)
        t = rng.uniform(3.5, 5.0)
        tone[y, x] = t + 0.8
        tone[y, (x + 1) % N] = t
        tone[(y + 1) % N, x] = t - 0.4
        tone[(y + 1) % N, (x + 1) % N] = t - 1.2
    return np.clip(tone, 0, n - 1), band


def grain_lines(rng, n=7, base=3.6, waves=3, amp=2.2, strength=0.9):
    """Flowing grain like bone or horn: soft curved growth lines over a smooth face."""
    tone = np.full((N, N), base) + 0.35 * fbm(rng, (2, 4), (0.6, 0.4)) * 2
    phase = rng.uniform(0, 2 * math.pi)
    for y in range(N):
        for x in range(N):
            v = math.sin(2 * math.pi * (y + amp * math.sin(2 * math.pi * x / N + phase)) / N * waves)
            if v > 0.82:
                tone[y, x] += strength
            elif v < -0.9:
                tone[y, x] -= strength * 1.1
    return np.clip(speckle(rng, tone, 0.03, 1.0), 0, n - 1)


def flutes(rng, n=7, base=3.4, width=4, ring_rows=()):
    """A pillar's side: vertical flutes, each lit on its left and shadowed in its groove, with optional ring bands."""
    tone = np.full((N, N), base) + 0.3 * fbm(rng, (4, 8), (0.6, 0.4)) * 2
    for x in range(N):
        p = x % width
        if p == 0:
            tone[:, x] -= 1.6
        elif p == 1:
            tone[:, x] += 1.0
        elif p == width - 1:
            tone[:, x] -= 0.7
    for r in ring_rows:
        tone[r, :] = base + 1.2
        tone[(r + 1) % N, :] = base - 1.4
    return np.clip(speckle(rng, tone, 0.03, 1.0), 0, n - 1)


def rings(rng, n=7, base=3.4, rim=1, step=2.5, rim_tone=1.0, wobble=0.5):
    """A pillar or log end: concentric rings round the middle, with a rim."""
    tone = np.zeros((N, N))
    c = (N - 1) / 2
    ph = rng.uniform(0, 6.28)
    for y in range(N):
        for x in range(N):
            d = max(abs(x - c), abs(y - c)) * 0.55 + math.hypot(x - c, y - c) * 0.45
            d += wobble * math.sin(math.atan2(y - c, x - c) * 3 + ph)
            v = math.sin(d / step * math.pi)
            tone[y, x] = base + (0.8 if v > 0.55 else -0.8 if v < -0.6 else 0)
            if max(abs(x - c), abs(y - c)) > c - rim:
                tone[y, x] = rim_tone + (0.8 if (x == 0 or y == 0) else 0)
    return np.clip(speckle(rng, tone, 0.03, 1.0), 0, n - 1)


def bark(rng, n=7, base=3.2, fissure=0.42, knots=1, horizontal=2):
    """Bark: vertical ridges lit on their left, dark fissures between them that wander, a few cross-cracks, a knot."""
    tone = np.zeros((N, N))
    cols = fbm(rng, (4, 8), (0.6, 0.4))  # ridge noise varies along y slowly via value noise
    ridge = np.zeros((N, N))
    phase = rng.uniform(0, 2 * math.pi, N)
    for y in range(N):
        for x in range(N):
            w = x + 1.2 * math.sin(2 * math.pi * y / N + phase[x // 4 * 4])
            ridge[y, x] = math.sin(2 * math.pi * w / 4.0) + 0.6 * cols[y, x]
    tone = base + 0.9 * ridge
    tone = np.where(ridge < -fissure * 2, 0.6, tone)
    left_dark = roll(ridge < -fissure * 2, 0, 1)
    tone = np.where(left_dark & ~(ridge < -fissure * 2), tone + 0.9, tone)
    for _ in range(horizontal):
        y, x = rng.integers(0, N, 2)
        for k in range(int(rng.integers(2, 4))):
            tone[y, (x + k) % N] = 0.8
            tone[(y + 1) % N, (x + k) % N] += 0.7
    for _ in range(knots):
        cy, cx = rng.integers(0, N, 2)
        for y in range(-2, 3):
            for x in range(-2, 3):
                d = math.hypot(x, y * 1.3)
                yy, xx = (cy + y) % N, (cx + x) % N
                if d < 1.0:
                    tone[yy, xx] = 0.8
                elif d < 2.2:
                    tone[yy, xx] = base + (0.9 if x + y < 0 else -0.6)
    return np.clip(tone, 0, n - 1)


# ---- Foliage, caps and sprites ---------------------------------------------------------------------------------------

def clumps(rng, n=7, count=26, radius=(1.6, 2.6), base=(2.6, 4.2), coverage_holes=0.12):
    """Leaves: overlapping round clumps painted back to front, each lit top-left; small holes between them.
    Returns (tone, alpha)."""
    tone = np.zeros((N, N))
    alpha = np.zeros((N, N))
    for _ in range(count):
        cy, cx = rng.uniform(0, N, 2)
        r = rng.uniform(*radius)
        t0 = rng.uniform(*base)
        for y in range(int(cy - r) - 1, int(cy + r) + 2):
            for x in range(int(cx - r) - 1, int(cx + r) + 2):
                dx = x + 0.5 - cx
                dy = y + 0.5 - cy
                d = math.hypot(dx, dy)
                if d <= r:
                    yy, xx = y % N, x % N
                    light = -(dx + dy) / (r * 1.4)
                    tone[yy, xx] = t0 + 1.4 * light + (0.0 if d < r - 0.8 else -0.8)
                    alpha[yy, xx] = 255
    holes = (alpha > 0) & (rng.random((N, N)) < coverage_holes) & (tone < base[0])
    alpha[holes] = 0
    return np.clip(tone, 0, n - 1), alpha


def spots(rng, count=5, radius=(0.9, 2.1), avoid=None):
    """Round spots (a mushroom cap's), as a float mask: 1 inside, 0.5 on the soft rim."""
    mask = np.zeros((N, N))
    for _ in range(count):
        cy, cx = rng.uniform(0, N, 2)
        r = rng.uniform(*radius)
        for y in range(int(cy - r) - 1, int(cy + r) + 2):
            for x in range(int(cx - r) - 1, int(cx + r) + 2):
                d = math.hypot(x + 0.5 - cx, (y + 0.5 - cy) * 1.1)
                yy, xx = y % N, x % N
                if d <= r:
                    mask[yy, xx] = 1.0
                elif d <= r + 0.7:
                    mask[yy, xx] = max(mask[yy, xx], 0.5)
    return mask


def blank():
    return np.zeros((N, N)), np.zeros((N, N))


def sheet(images, labels, scale=4, tile=3, cols=6, background=(40, 38, 48)):
    """A contact sheet: each texture tiled {tile} x {tile} (to show seams), scaled up, with its name."""
    from PIL import ImageDraw
    cell = N * tile * scale
    rows = (len(images) + cols - 1) // cols
    out = Image.new("RGB", (cols * (cell + 10), rows * (cell + 24)), background)
    draw = ImageDraw.Draw(out)
    for k, (img, label) in enumerate(zip(images, labels)):
        tiled = Image.new("RGBA", (N * tile, N * tile), background + (255,))
        src = img.crop((0, 0, N, N))
        for a in range(tile):
            for b in range(tile):
                tiled.alpha_composite(src, (a * N, b * N))
        tiled = tiled.resize((cell, cell), Image.NEAREST)
        x = (k % cols) * (cell + 10)
        y = (k // cols) * (cell + 24)
        out.paste(tiled.convert("RGB"), (x, y))
        draw.text((x + 2, y + cell + 6), label, fill=(225, 225, 230))
    return out
