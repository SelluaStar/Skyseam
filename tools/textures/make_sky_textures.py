"""The Halcyon's sky (round two, docs/DECISIONS.md K86): everything that moves across it, each drawn by the sky code
as a billboard, its colour and brightness set per frame.

Writes, under src/main/resources/assets/skyseam/textures/:
  environment/lantern_sun.png      64x64   the Lantern-Sun: a rounded lantern-square of white-gold light in a glow; its
                                           bright core spans the middle 24 of 64 pixels (the sky code sizes it from that)
  environment/sun_corona.png       128x128 tapered rays round an empty middle, turned slowly by the sky code
  environment/halo.png             128x128 a soft round glow, tinted and added behind suns, moons and the planet
  environment/companion_star.png   64x64   the Ember companion: a small blue-white star with four long flares
  environment/planet.png           128x128 Veyra, a banded teal, cream and violet giant with a storm and a tilted ring
  environment/planet_shadow.png    128x128 the planet's night side, turned by the sky code to face away from the sun
  environment/pale_moon.png        512x64  the Pale Moon, cratered silver-lilac, in its eight phases side by side
  environment/ringed_moon.png      128x128 the Ringed Moon, violet, crossed by a tilted ring
  environment/shard_moon.png       128x128 the Shard Moon, a cracked teal crystal glowing along its fractures
  environment/moon_debris.png      64x64   four lumps of rock, 2 by 2, that circle the Shard Moon (image 14)
  environment/ember_moon.png       64x64   the Ember Moon, small and rose-red with molten seams
  environment/star_glint.png       32x32   a four-pointed sparkle for the brightest stars
  environment/meteor.png           128x16  a shooting star's streak, bright head to fading tail
  environment/nebula.png           512x128 a violet and magenta star-cloud band, tiling left to right
  environment/nebula_teal.png      512x128 a second, teal and rose band that drifts against the first
  environment/swirl.png            256x256 swirling cloud streaks round a centre (image 14), turned over the zenith
  particle/wisp.png                8x8     a soft glowing mote, the night's fireflies and drifting lanterns
and build/previews/sky_textures.png. Colours from the author's reference images (palette.json and images 8 to 14).
Deterministic.
"""
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
TEX = ROOT / "src" / "main" / "resources" / "assets" / "skyseam" / "textures"
OUT = TEX / "environment"
PREVIEW = ROOT / "build" / "previews" / "sky_textures.png"
PALETTE = json.loads((ROOT / "tools" / "reference" / "palette.json").read_text(encoding="utf-8"))


def hexc(value):
    value = value.lstrip("#")
    return np.array([int(value[k:k + 2], 16) for k in (0, 2, 4)], dtype=float)


def grid(size, w=None):
    w = w or size
    ys, xs = np.mgrid[0:size, 0:w].astype(float)
    return xs + 0.5, ys + 0.5


def smoothstep(a, b, v):
    t = np.clip((v - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def value_noise(h, w, cells_x, seed, wrap=True):
    rng = np.random.default_rng(seed)
    cells_y = max(2, int(round(cells_x * h / w)))
    lattice = rng.random((cells_y + 1, cells_x + 1))
    if wrap:
        lattice[:, -1] = lattice[:, 0]
    ys = np.linspace(0, cells_y, h, endpoint=False)
    xs = np.linspace(0, cells_x, w, endpoint=False)
    y0 = ys.astype(int)
    x0 = xs.astype(int)
    fy = ys - y0
    fx = xs - x0
    fy = fy * fy * (3 - 2 * fy)
    fx = fx * fx * (3 - 2 * fx)
    a = lattice[y0][:, x0]
    b = lattice[y0][:, x0 + 1]
    c = lattice[np.minimum(y0 + 1, cells_y)][:, x0]
    d = lattice[np.minimum(y0 + 1, cells_y)][:, x0 + 1]
    top = a * (1 - fx) + b * fx
    bottom = c * (1 - fx) + d * fx
    return top * (1 - fy[:, None]) + bottom * fy[:, None]


def fbm(h, w, seed, octaves=4, base=6, wrap=True):
    total = np.zeros((h, w))
    weight = 0
    for k in range(octaves):
        amp = 0.5 ** k
        total += amp * value_noise(h, w, base * 2 ** k, seed + k * 31, wrap)
        weight += amp
    return total / weight


def rgba(rgb, alpha):
    return Image.fromarray(np.dstack([np.clip(rgb, 0, 255), np.clip(alpha, 0, 255)]).astype(np.uint8), "RGBA")


def disc_normal(xs, ys, cx, cy, r):
    """For each pixel: (inside, nx, ny, nz) of a sphere of radius r seen face on."""
    nx = (xs - cx) / r
    ny = (ys - cy) / r
    q = 1 - nx * nx - ny * ny
    inside = q > 0
    nz = np.sqrt(np.clip(q, 0, 1))
    return inside, nx, ny, nz


# ---- Suns ------------------------------------------------------------------------------------------------------------

def lantern_sun():
    """A rounded lantern-square of white-gold light with a faint lattice, in a soft glow."""
    size = 64
    xs, ys = grid(size)
    c = size / 2
    dx = np.abs(xs - c)
    dy = np.abs(ys - c)
    square = (dx ** 4 + dy ** 4) ** 0.25          # a superellipse: a square with rounded corners
    core = square <= 12
    t = np.clip(square / 12, 0, 1)
    rgb = hexc("#FFFFF6") * (1 - t[..., None] * 0.35) + hexc("#FFD99A") * (t[..., None] * 0.35)
    lattice = core & ((np.abs(dx - 5) < 0.6) | (np.abs(dy - 5) < 0.6)) & (square > 4)
    rgb = np.where(lattice[..., None], rgb * 0.86, rgb)
    alpha = np.where(core, 255, 255 * np.clip(1 - (square - 12) / 20, 0, 1) ** 2.4 * 0.7)
    glow = hexc("#FFE6C0")
    rgb = np.where(core[..., None], rgb, glow)
    return rgba(rgb, alpha)


def corona():
    """Tapered rays round an empty middle: twelve, long and short in turn, each soft-edged."""
    size = 128
    xs, ys = grid(size)
    c = size / 2
    dx, dy = xs - c, ys - c
    r = np.hypot(dx, dy) / c
    a = np.arctan2(dy, dx)
    rays = np.zeros_like(r)
    for k in range(12):
        centre = k * math.pi / 6 + 0.13 * math.sin(k * 2.3)
        length = 0.95 if k % 2 == 0 else 0.65
        width = 0.11 if k % 2 == 0 else 0.07
        d = np.abs(np.angle(np.exp(1j * (a - centre))))
        taper = width * (1 - r / length)
        rays = np.maximum(rays, smoothstep(0, 1, 1 - d / np.maximum(taper, 1e-3)) * (r < length) * smoothstep(0.12, 0.25, r))
    alpha = 255 * rays * np.clip(1 - r, 0, 1) ** 0.6
    return rgba(np.dstack([np.full_like(r, 255)] * 3), alpha)


def halo():
    size = 128
    xs, ys = grid(size)
    r = np.hypot(xs - size / 2, ys - size / 2) / (size / 2)
    alpha = 255 * np.clip(1 - r, 0, 1) ** 2.2
    return rgba(np.dstack([np.full_like(r, 255)] * 3), alpha)


def companion_star():
    """A small blue-white star: a bright core, four long flares and four short ones."""
    size = 64
    xs, ys = grid(size)
    c = size / 2
    dx, dy = np.abs(xs - c), np.abs(ys - c)
    r = np.hypot(dx, dy)
    core = np.clip(1 - r / 5, 0, 1) ** 0.8
    long_flare = np.maximum(np.clip(1 - dx / 30, 0, 1) ** 2 * np.clip(1 - dy / 1.6, 0, 1), np.clip(1 - dy / 30, 0, 1) ** 2 * np.clip(1 - dx / 1.6, 0, 1))
    u, v = (dx + dy) / math.sqrt(2), np.abs(dx - dy) / math.sqrt(2)
    short_flare = np.clip(1 - u / 14, 0, 1) ** 2 * np.clip(1 - v / 1.2, 0, 1)
    glow = np.clip(1 - r / 20, 0, 1) ** 3 * 0.5
    a = np.clip(np.maximum.reduce([core, long_flare, short_flare * 0.7, glow]), 0, 1)
    tint = hexc("#E6F2FF") * (1 - core[..., None]) + hexc("#FFFFFF") * core[..., None]
    return rgba(tint, 255 * a)


# ---- The planet ------------------------------------------------------------------------------------------------------

PLANET_R = 34


def planet():
    """Veyra: a banded giant (teal, cream, dusty violet, rust) with a storm, limb-darkened, and a tilted banded ring
    passing behind it at the top and in front at the bottom."""
    size = 128
    xs, ys = grid(size)
    c = size / 2
    inside, nx, ny, nz = disc_normal(xs, ys, c, c, PLANET_R)
    turbulence = fbm(size, size, 71, octaves=4, base=4, wrap=False)
    lat = ny + 0.08 * (turbulence - 0.5) + 0.05 * np.sin(nx * 6 + ny * 9)
    bands = [hexc("#E8D8B4"), hexc("#4FA6A8"), hexc("#D8C098"), hexc("#8E78B0"), hexc("#C07A52"), hexc("#E2D2AE"), hexc("#3E8A90"),
             hexc("#A08CC0")]
    index = np.clip(((lat + 1) * 3.6).astype(int), 0, len(bands) - 1)
    rgb = np.zeros((size, size, 3))
    for k, colour in enumerate(bands):
        rgb[index == k] = colour
    # Soft edges between bands.
    blur = np.array(Image.fromarray(rgb.astype(np.uint8)).resize((64, 64), Image.BILINEAR).resize((128, 128), Image.BILINEAR), dtype=float)
    rgb = rgb * 0.55 + blur * 0.45
    # The storm: an oval swirl in the southern bands.
    storm = np.hypot((nx - 0.28) / 0.22, (ny - 0.32) / 0.11)
    rgb = np.where((storm < 1)[..., None], rgb * 0.6 + hexc("#F0A070") * 0.4 * (1 - storm[..., None] * 0.5), rgb)
    limb = 0.45 + 0.55 * nz
    rgb = rgb * limb[..., None]
    alpha = np.where(inside, 255, 0).astype(float)
    # The ring: an ellipse tilted -18 degrees, banded, hidden behind the planet at the top.
    tilt = math.radians(-18)
    dx, dy = xs - c, ys - c
    rx = dx * math.cos(tilt) + dy * math.sin(tilt)
    ry = -dx * math.sin(tilt) + dy * math.cos(tilt)
    e = np.hypot(rx / 60, ry / 13)
    ring = (e > 0.66) & (e < 1.0) & ~((e > 0.8) & (e < 0.84))
    ring_band = 0.75 + 0.25 * np.sin(e * 60)
    ring_rgb = hexc("#D8D0E8") * ring_band[..., None]
    behind = ring & (ry < 0) & inside
    front = ring & ~behind
    rgb = np.where(front[..., None], ring_rgb, rgb)
    alpha = np.where(front, 230 * (0.6 + 0.4 * ring_band), alpha)
    return rgba(rgb, alpha)


def planet_shadow():
    """The planet's night side: dark over its left half, a soft terminator, nothing beyond its disc."""
    size = 128
    xs, ys = grid(size)
    c = size / 2
    inside, nx, ny, nz = disc_normal(xs, ys, c, c, PLANET_R + 0.6)
    alpha = 235 * smoothstep(-0.05, 0.55, -nx) * inside
    return rgba(np.dstack([np.full_like(nx, 10), np.full_like(nx, 8), np.full_like(nx, 24)]), alpha)


# ---- The moons -------------------------------------------------------------------------------------------------------

def craters(xs, ys, cx, cy, r, seed, count):
    """A height-like shading field for craters on a disc: each a dip with a lit lower-right rim."""
    rng = np.random.default_rng(seed)
    shade = np.zeros_like(xs)
    for _ in range(count):
        a = rng.random() * math.tau
        d = math.sqrt(rng.random()) * r * 0.85
        px, py = cx + math.cos(a) * d, cy + math.sin(a) * d
        size = r * (0.06 + 0.16 * rng.random() ** 2)
        q = np.hypot(xs - px, ys - py) / size
        floor = (q < 0.8) * -0.18
        rim_lit = ((q > 0.75) & (q < 1.1)) * 0.16 * np.clip(((xs - px) + (ys - py)) / size, -1, 1)
        shade += floor + rim_lit
    return shade


def pale_moon():
    """The Pale Moon in vanilla's eight phases (full, waning gibbous ... new ... waxing gibbous), side by side."""
    size = 64
    frames = []
    xs, ys = grid(size)
    c = size / 2
    r = 26
    inside, nx, ny, nz = disc_normal(xs, ys, c, c, r)
    maria = fbm(size, size, 13, octaves=3, base=3, wrap=False)
    base = hexc("#DCD6EC") * (0.86 + 0.14 * maria[..., None])
    base = np.where((maria > 0.6)[..., None], base * 0.8 + hexc("#9C94B8") * 0.2, base)
    base = base * (1 + craters(xs, ys, c, c, r, 17, 22)[..., None])
    base = base * (0.62 + 0.38 * nz)[..., None]
    for k in range(8):
        phase = k * math.pi / 4
        light = np.array([-math.sin(phase), 0.0, math.cos(phase)])
        lit = nx * light[0] + nz * light[2]
        day = smoothstep(-0.08, 0.12, lit)
        rgb = base * (0.12 + 0.88 * day[..., None]) + hexc("#2A2848") * (1 - day[..., None]) * 0.5
        alpha = np.where(inside, 120 + 135 * day, 0)
        frames.append(rgba(rgb, alpha))
    sheet = Image.new("RGBA", (size * 8, size), (0, 0, 0, 0))
    for k, frame in enumerate(frames):
        sheet.paste(frame, (k * size, 0))
    return sheet


def ringed_moon():
    """A violet moon, lit from the upper left, banded, with a tilted ring in front of its lower half."""
    size = 128
    xs, ys = grid(size)
    c = size / 2
    r = 28
    inside, nx, ny, nz = disc_normal(xs, ys, c, c, r)
    light = np.clip(0.25 + 0.85 * (-nx * 0.5 - ny * 0.5 + nz * 0.7), 0.15, 1.1)
    band = 0.9 + 0.1 * np.sin(ny * 22 + nx * 3)
    rgb = hexc("#8A64C8") * (light * band)[..., None]
    alpha = np.where(inside, 255, 0).astype(float)
    tilt = math.radians(-24)
    dx, dy = xs - c, ys - c
    rx = dx * math.cos(tilt) + dy * math.sin(tilt)
    ry = -dx * math.sin(tilt) + dy * math.cos(tilt)
    e = np.hypot(rx / 58, ry / 14)
    ring = (e > 0.7) & (e < 1.0) & ~((e > 0.82) & (e < 0.86))
    front = ring & ~((ry < 0) & inside)
    ring_rgb = hexc("#E2C8F8") * (0.7 + 0.3 * np.sin(e * 50))[..., None]
    rgb = np.where(front[..., None], ring_rgb, rgb)
    alpha = np.where(front, 200, alpha)
    return rgba(rgb, alpha)


def shard_moon():
    """A moon of teal crystal split along glowing fractures, a wedge broken out of one side."""
    size = 128
    xs, ys = grid(size)
    c = size / 2
    r = 30
    inside, nx, ny, nz = disc_normal(xs, ys, c, c, r)
    rng = np.random.default_rng(29)
    seeds = rng.uniform(c - r, c + r, (14, 2))
    d = np.stack([np.hypot(xs - sx, ys - sy) for sx, sy in seeds])
    order = np.sort(d, axis=0)
    label = np.argmin(d, axis=0)
    edge = (order[1] - order[0]) < 1.3
    tone = rng.uniform(0.7, 1.15, 14)[label]
    facet_light = tone * (0.55 + 0.45 * (nz * 0.7 - nx * 0.3 - ny * 0.3))
    rgb = hexc("#4FB8B8") * facet_light[..., None]
    rgb = np.where(edge[..., None], hexc("#C8FFFF"), rgb)
    # The broken wedge: gone between two angles on the right.
    a = np.arctan2(ys - c, xs - c)
    wedge = (a > -0.5) & (a < 0.25) & (np.hypot(xs - c, ys - c) > r * 0.45)
    alpha = np.where(inside & ~wedge, 255, 0).astype(float)
    glow = np.clip(1 - np.hypot(xs - c, ys - c) / (r * 1.9), 0, 1) ** 2
    halo = (~inside | wedge) & (glow > 0)
    rgb = np.where(halo[..., None], hexc("#7FF0F0"), rgb)
    alpha = np.where(halo, 90 * glow, alpha)
    return rgba(rgb, alpha)


def moon_debris():
    """Four lumps of rock, each lit on its upper left, for the Shard Moon's ring of debris."""
    out = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    rng = np.random.default_rng(37)
    for k in range(4):
        size = 32
        xs, ys = grid(size)
        c = size / 2
        a = np.arctan2(ys - c, xs - c)
        bumps = sum(rng.uniform(0.05, 0.15) * np.cos(n * a + rng.uniform(0, 6)) for n in (2, 3, 5))
        r = (9 + 4 * rng.random()) * (1 + bumps)
        inside = np.hypot(xs - c, ys - c) < r
        light = np.clip(0.55 - ((xs - c) + (ys - c)) / 30, 0.25, 1.1)
        rgb = hexc("#7A9C9C") * light[..., None]
        out.paste(rgba(rgb, np.where(inside, 255, 0)), ((k % 2) * 32, (k // 2) * 32))
    return out


def ember_moon():
    """A small rose-red moon with molten seams and a warm glow."""
    size = 64
    xs, ys = grid(size)
    c = size / 2
    r = 14
    inside, nx, ny, nz = disc_normal(xs, ys, c, c, r)
    seams = fbm(size, size, 43, octaves=3, base=5, wrap=False)
    molten = np.abs(seams - 0.5) < 0.04
    rgb = hexc("#C24A5A") * (0.5 + 0.5 * nz)[..., None]
    rgb = np.where(molten[..., None], hexc("#FFB070"), rgb)
    glow = np.clip(1 - np.hypot(xs - c, ys - c) / 30, 0, 1) ** 2
    alpha = np.where(inside, 255, 140 * glow)
    rgb = np.where(inside[..., None], rgb, hexc("#FF8A80"))
    return rgba(rgb, alpha)


# ---- Stars, streaks, clouds ------------------------------------------------------------------------------------------

def star_glint():
    size = 32
    xs, ys = grid(size)
    c = size / 2
    dx, dy = np.abs(xs - c), np.abs(ys - c)
    core = np.clip(1 - np.hypot(dx, dy) / 4, 0, 1)
    flare = np.maximum(np.clip(1 - dx / 15, 0, 1) ** 3 * np.clip(1 - dy / 1.2, 0, 1), np.clip(1 - dy / 15, 0, 1) ** 3 * np.clip(1 - dx / 1.2, 0, 1))
    return rgba(np.dstack([np.full_like(dx, 255)] * 3), 255 * np.clip(np.maximum(core, flare), 0, 1))


def meteor():
    w, h = 128, 16
    xs, ys = grid(h, w)
    t = xs / w
    across = np.clip(1 - np.abs(ys - h / 2) / (1.2 + 5 * t), 0, 1)
    alpha = 255 * across * t ** 2.2
    rgb = hexc("#B0D8FF") * (1 - t[..., None]) + hexc("#FFFFFF") * t[..., None]
    return rgba(rgb, alpha)


def nebula(seed, colours, width=512, height=128):
    """A star-cloud band tiling left to right: dense where two noises agree, crossed by dark lanes, dusted with stars."""
    clouds = fbm(height, width, seed, octaves=5, base=5)
    lanes = fbm(height, width, seed + 12, octaves=4, base=7)
    ys = (np.arange(height) + 0.5) / height
    band = (1 - np.abs(ys * 2 - 1)) ** 1.6
    density = np.clip((clouds - 0.3) / 0.45, 0, 1) * band[:, None]
    density *= 1 - 0.75 * np.clip((lanes - 0.55) / 0.45, 0, 1)
    t = np.clip((clouds - 0.45) * 2.2, 0, 1)
    rgb = colours[0] * (1 - t[..., None]) + colours[1] * t[..., None]
    rgb = np.where((density > 0.55)[..., None], rgb * 0.6 + colours[2] * 0.4, rgb)
    alpha = 255 * np.clip(density * 1.7, 0, 1)
    rng = np.random.default_rng(seed + 5)
    for _ in range(1600):
        x = rng.integers(0, width)
        y = int(height / 2 + rng.normal(0, height / 6))
        if 0 <= y < height:
            rgb[y, x] = (255, 245, 255)
            alpha[y, x] = max(alpha[y, x], 110 + rng.random() * 145)
    return rgba(rgb, alpha)


def swirl():
    """Swirling streaks of cloud wound round a centre (image 14): three arms, frayed by noise, fading out at the rim."""
    size = 256
    xs, ys = grid(size)
    c = size / 2
    dx, dy = xs - c, ys - c
    r = np.hypot(dx, dy) / c
    a = np.arctan2(dy, dx)
    detail = fbm(size, size, 81, octaves=5, base=6, wrap=False)
    arms = 0.5 + 0.5 * np.sin(a * 3 + np.log(r + 0.05) * 7 + detail * 4)
    streaks = np.clip((arms - 0.35) / 0.65, 0, 1) ** 1.5
    fade = smoothstep(0.08, 0.3, r) * smoothstep(1.0, 0.6, r)
    alpha = 255 * streaks * fade * (0.55 + 0.45 * detail)
    rgb = np.dstack([np.full_like(r, 255)] * 3) * (0.85 + 0.15 * detail[..., None])
    return rgba(rgb, alpha)


def wisp():
    size = 8
    xs, ys = grid(size)
    r = np.hypot(xs - 4, ys - 4) / 4
    alpha = 255 * np.clip(1 - r, 0, 1) ** 1.6
    rgb = hexc("#F4FFD8") * (1 - r[..., None] * 0.3)
    return rgba(rgb, alpha)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    made = {
        "lantern_sun": lantern_sun(),
        "sun_corona": corona(),
        "halo": halo(),
        "companion_star": companion_star(),
        "planet": planet(),
        "planet_shadow": planet_shadow(),
        "pale_moon": pale_moon(),
        "ringed_moon": ringed_moon(),
        "shard_moon": shard_moon(),
        "moon_debris": moon_debris(),
        "ember_moon": ember_moon(),
        "star_glint": star_glint(),
        "meteor": meteor(),
        "nebula": nebula(7, [hexc("#9A5AE0"), hexc("#E260D6"), hexc("#E6C0FF")]),
        "nebula_teal": nebula(53, [hexc("#3AB0C0"), hexc("#E890B0"), hexc("#D8FFF8")]),
        "swirl": swirl(),
    }
    for name, img in made.items():
        img.save(OUT / f"{name}.png")
        print("wrote", (OUT / f"{name}.png").relative_to(ROOT))
    (TEX / "particle").mkdir(parents=True, exist_ok=True)
    wisp().save(TEX / "particle" / "wisp.png")
    print("wrote", (TEX / "particle" / "wisp.png").relative_to(ROOT))
    old = OUT / "rift_eye.png"
    if old.exists():
        old.unlink()
        print("removed", old.relative_to(ROOT), "(the swirl layer replaces the Rift Eye)")

    sheet = Image.new("RGBA", (1100, 760), (12, 18, 36, 255))
    draw = ImageDraw.Draw(sheet)
    sheet.alpha_composite(made["nebula"].resize((1024, 128)), (20, 10))
    sheet.alpha_composite(made["nebula_teal"].resize((1024, 128)), (20, 150))
    sheet.alpha_composite(made["pale_moon"].resize((1024, 128), Image.NEAREST), (20, 290))
    x, y = 20, 440
    for name in ("lantern_sun", "sun_corona", "halo", "companion_star", "planet", "planet_shadow", "ringed_moon", "shard_moon"):
        sheet.alpha_composite(made[name].resize((128, 128), Image.NEAREST), (x, y))
        draw.text((x, y + 130), name, fill=(220, 220, 230))
        x += 134
    x, y = 20, 600
    for name in ("moon_debris", "ember_moon", "star_glint", "swirl"):
        sheet.alpha_composite(made[name].resize((128, 128), Image.NEAREST), (x, y))
        draw.text((x, y + 130), name, fill=(220, 220, 230))
        x += 134
    sheet.alpha_composite(made["meteor"].resize((256, 32), Image.NEAREST), (x + 10, y + 40))
    PREVIEW.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(PREVIEW)
    print("wrote", PREVIEW.relative_to(ROOT))


if __name__ == "__main__":
    main()
