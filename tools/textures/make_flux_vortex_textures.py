"""The flux vortex's textures (docs/DECISIONS.md K97), after the author's reference stills of the flux vortices of
Pandora (kept locally, not in the repository) and their direction: two thick vortices of light winding round each
other like DNA, in Minecraft's own style. So everything is pixel art: small, drawn on whole pixels, in a few steps of
brightness, and shown without smoothing (no "blur"), like a beacon beam or an end gateway.

Writes, under src/main/resources/assets/skyseam/textures/:
  environment/flux_tube.png     16x64  one vortex of the pair seen from the side: a body of light brighter towards its
                                       middle, made of pixel streaks of different lengths running up it, tiles top to
                                       bottom
  environment/flux_strand.png   8x64   a thin strand of light: a two-pixel core in a one-pixel halo, broken into runs;
                                       tiles top to bottom
  environment/flux_glow.png     16x16  a soft glow across, in steps, for haze, spray and the column's glow
  environment/flux_pool.png     32x32  the pool of light at the foot: a bright middle and pixel rings (clamped)
  environment/flux_crown.png    32x32  the crown at the top: a glow with rays bursting out of it (clamped)
  environment/flux_band.png     16x4   a band, for ripples
  environment/flux_slice.png    32x32  a slice across one vortex of the pair, seen from above or below: a glow brighter
                                       towards its middle with three pixel arms winding out of it (clamped)
  particle/flux_mote_{0..3}.png 8x8    a twinkling mote, four frames from a dot to a cross
  particle/flux_spark.png       8x8    a four-pointed spark
  particle/flux_dust.png        8x8    a puff of mist
Everything is white, its brightness in its alpha, so the code tints it (white and pale blue by day, cyan and violet
by night). Textures of the earlier vortex designs are deleted. Deterministic.
"""
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
from make_sky_textures import grid, rgba  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
TEX = ROOT / "src" / "main" / "resources" / "assets" / "skyseam" / "textures"
PREVIEW = ROOT / "build" / "previews" / "flux_vortex_textures.png"
GONE = ("flux_ribbon", "flux_core", "flux_haze", "flux_mist", "flux_thread", "flux_ring", "flux_ground", "flux_veil", "flux_helix")
GONE_PARTICLES = ("flux_mote",)
STEPS = 5


def steps(alpha, levels=STEPS):
    """Brightness in a few whole steps, as Minecraft's own textures are painted."""
    a = np.clip(alpha, 0, 1)
    return np.round(a * (levels - 1)) / (levels - 1)


def white(alpha):
    h, w = alpha.shape
    return rgba(np.full((h, w, 3), 255.0), np.clip(alpha, 0, 1) * 255)


def runs(h, rng, count, length):
    """Brightness along a column of pixels: runs of light with gaps, wrapping round."""
    out = np.zeros(h)
    for _ in range(count):
        start = rng.integers(0, h)
        size = rng.integers(length[0], length[1] + 1)
        level = rng.uniform(0.5, 1.0)
        for k in range(size):
            out[(start + k) % h] = max(out[(start + k) % h], level)
    return out


def tube():
    w, h = 16, 64
    rng = np.random.default_rng(101)
    u = (np.arange(w) + 0.5) / w * 2 - 1
    body = 0.22 + 0.3 * np.cos(u * np.pi / 2) ** 2
    out = np.zeros((h, w))
    for x in range(w):
        streak = runs(h, rng, 5, (6, 22))
        out[:, x] = body[x] + 0.55 * streak * (0.5 + 0.5 * np.cos(u[x] * np.pi / 2))
    out[:, 0] *= 0.4
    out[:, -1] *= 0.4
    return white(steps(out))


def strand():
    h = 64
    rng = np.random.default_rng(102)
    along = runs(h, rng, 6, (5, 16))
    across = np.array([0, 0.15, 0.4, 1, 1, 0.4, 0.15, 0])
    out = across[None, :] * (0.3 + 0.7 * along[:, None])
    return white(steps(out))


def glow():
    w, h = 16, 16
    u = (np.arange(w) + 0.5) / w * 2 - 1
    row = np.exp(-(u / 0.5) ** 2)
    return white(steps(np.tile(row, (h, 1)), 6))


def pool():
    size = 32
    xs, ys = grid(size)
    r = np.hypot(xs + 0.5 - size / 2, ys + 0.5 - size / 2) / (size / 2)
    centre = np.clip(1 - r / 0.45, 0, 1) ** 1.2
    rings = ((np.floor(r * 8) % 2) == 0) * 0.3 * np.clip(1 - r, 0, 1)
    return white(steps(centre + rings))


def crown():
    size = 32
    xs, ys = grid(size)
    dx = xs + 0.5 - size / 2
    dy = ys + 0.5 - size / 2
    r = np.hypot(dx, dy) / (size / 2)
    angle = np.arctan2(dy, dx)
    rays = np.clip(np.cos(angle * 6) * 0.5 + 0.5, 0, 1) ** 3 * np.clip(1 - r, 0, 1) * 0.6
    core = np.clip(1 - r / 0.55, 0, 1) ** 1.5
    return white(steps(core + rays))


def slice_():
    size = 32
    xs, ys = grid(size)
    dx = xs + 0.5 - size / 2
    dy = ys + 0.5 - size / 2
    r = np.hypot(dx, dy) / (size / 2)
    angle = np.arctan2(dy, dx)
    body = np.clip(1 - r / 0.9, 0, 1) ** 1.1 * 0.5
    arms = np.clip(np.cos(3 * angle - 6 * r) * 0.5 + 0.5, 0, 1) ** 4 * np.clip(1 - r / 0.8, 0, 1) * 0.8
    return white(steps(body + arms))


def band():
    return white(np.array([[0.35] * 16, [1.0] * 16, [1.0] * 16, [0.35] * 16]))


def mote(frame):
    out = np.zeros((8, 8))
    if frame == 0:
        out[3:5, 3:5] = 0.75
    elif frame == 1:
        out[3:5, 3:5] = 1
        for y, x in ((2, 3), (2, 4), (5, 3), (5, 4), (3, 2), (4, 2), (3, 5), (4, 5)):
            out[y, x] = 0.5
    elif frame == 2:
        out[3:5, :] = 0.5
        out[:, 3:5] = 0.5
        out[3:5, 3:5] = 1
        out[3:5, 0] = out[3:5, 7] = out[0, 3:5] = out[7, 3:5] = 0.25
    else:
        out[3:5, 3:5] = 1
        for k in range(1, 3):
            for y, x in ((3 - k, 3 - k), (4 + k, 4 + k), (3 - k, 4 + k), (4 + k, 3 - k)):
                out[y, x] = 0.75 / k
    return white(out)


def spark():
    out = np.zeros((8, 8))
    out[3:5, 1:7] = 0.5
    out[1:7, 3:5] = 0.5
    out[3:5, 3:5] = 1
    out[3:5, 0] = out[3:5, 7] = out[0, 3:5] = out[7, 3:5] = 0.25
    return white(out)


def dust():
    out = np.array([
        [0, 0, 0.25, 0.25, 0.25, 0.25, 0, 0],
        [0, 0.25, 0.5, 0.5, 0.5, 0.5, 0.25, 0],
        [0.25, 0.5, 0.5, 0.75, 0.75, 0.5, 0.5, 0.25],
        [0.25, 0.5, 0.75, 0.75, 0.75, 0.75, 0.5, 0.25],
        [0.25, 0.5, 0.75, 0.75, 0.75, 0.75, 0.5, 0.25],
        [0.25, 0.5, 0.5, 0.75, 0.75, 0.5, 0.5, 0.25],
        [0, 0.25, 0.5, 0.5, 0.5, 0.5, 0.25, 0],
        [0, 0, 0.25, 0.25, 0.25, 0.25, 0, 0],
    ])
    return white(out * 0.8)


def main():
    environment = {"flux_tube": tube(), "flux_strand": strand(), "flux_glow": glow(), "flux_pool": pool(), "flux_crown": crown(),
                   "flux_band": band(), "flux_slice": slice_()}
    clamped = {"flux_pool", "flux_crown", "flux_slice"}
    particles = {f"flux_mote_{k}": mote(k) for k in range(4)}
    particles.update({"flux_spark": spark(), "flux_dust": dust()})
    for name, img in environment.items():
        path = TEX / "environment" / f"{name}.png"
        img.save(path)
        meta = path.with_name(path.name + ".mcmeta")
        if name in clamped:
            meta.write_text(json.dumps({"texture": {"blur": False, "clamp": True}}, indent=1) + "\n", encoding="utf-8")
        elif meta.exists():
            meta.unlink()
        print("wrote", path.relative_to(ROOT))
    for name, img in particles.items():
        path = TEX / "particle" / f"{name}.png"
        img.save(path)
        print("wrote", path.relative_to(ROOT))
    for name in GONE:
        for path in (TEX / "environment" / f"{name}.png", TEX / "environment" / f"{name}.png.mcmeta"):
            if path.exists():
                path.unlink()
                print("removed", path.relative_to(ROOT))
    for name in GONE_PARTICLES:
        path = TEX / "particle" / f"{name}.png"
        if path.exists():
            path.unlink()
            print("removed", path.relative_to(ROOT))
    from PIL import Image
    scale = 4
    width = 10 + sum(img.width * scale + 10 for img in environment.values()) + len(particles) * 74
    sheet = Image.new("RGBA", (width, 64 * scale + 10), (12, 18, 36, 255))
    x = 10
    for img in environment.values():
        sheet.alpha_composite(img.resize((img.width * scale, img.height * scale), Image.NEAREST), (x, 4))
        x += img.width * scale + 10
    for img in particles.values():
        sheet.alpha_composite(img.resize((64, 64), Image.NEAREST), (x, 4))
        x += 74
    PREVIEW.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(PREVIEW)
    print("wrote", PREVIEW.relative_to(ROOT))


if __name__ == "__main__":
    main()
