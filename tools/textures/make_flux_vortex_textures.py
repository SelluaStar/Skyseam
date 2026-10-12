"""The flux vortex's textures (docs/DECISIONS.md K97), after the author's reference stills of the flux vortices of
Pandora (kept locally, not in the repository) and their wish for a double helix: thick strands of light twisting up
out of the water.

Writes, under src/main/resources/assets/skyseam/textures/:
  environment/flux_strand.png   32x256  one strand: a fine bright core in a faint halo, broken along its length into long
                                        streaks and wisps of light with gaps between; tiles top to bottom
  environment/flux_helix.png    32x256  a thick strand of the helix: a broad bright band with a brighter line down its
                                        middle and soft edges, light rippling along it; tiles top to bottom
  environment/flux_glow.png     32x32   a soft glow across, for the column's glow
  environment/flux_pool.png     128x128 the pool of light where the column meets the water or the ground: brightest in
                                        the middle, a few soft ripples, fading out
  environment/flux_band.png     64x16   a soft band, for ripples running out over the water
  particle/flux_mote.png        16x16   a soft mote with a bright heart
  particle/flux_spark.png       16x16   a four-pointed spark
  particle/flux_dust.png        8x8     a soft puff of mist
Everything is white, its brightness in its alpha, so the code tints it (white and pale blue by day, cyan, blue and
violet by night). The environment textures get a `.png.mcmeta` with "blur". Textures of the earlier vortex designs
are deleted. Deterministic.
"""
import json
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
from make_sky_textures import fbm, grid, rgba, smoothstep  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
TEX = ROOT / "src" / "main" / "resources" / "assets" / "skyseam" / "textures"
PREVIEW = ROOT / "build" / "previews" / "flux_vortex_textures.png"
GONE = ("flux_ribbon", "flux_core", "flux_haze", "flux_mist", "flux_thread", "flux_ring", "flux_ground", "flux_veil")


def white(alpha):
    h, w = alpha.shape
    return rgba(np.full((h, w, 3), 255.0), np.clip(alpha, 0, 1) * 255)


def streaks(h, seed, count, length=(0.08, 0.35)):
    """Brightness along a strand: long streaks of light with soft ends and gaps between, wrapping round."""
    rng = np.random.default_rng(seed)
    v = np.arange(h) / h
    out = np.zeros(h)
    for _ in range(count):
        start = rng.random()
        size = rng.uniform(*length)
        d = (v - start) % 1
        inside = d < size
        t = np.where(inside, d / size, 0)
        out = np.maximum(out, inside * np.sin(np.pi * t) ** 0.6 * rng.uniform(0.5, 1.0))
    return out


def strand():
    w, h = 32, 256
    xs, ys = grid(h, w)
    u = (xs / w - 0.5) * 2
    along = streaks(h, 91, 9)[:, None]
    core = np.exp(-(u / 0.16) ** 2)
    halo = 0.3 * np.exp(-(u / 0.6) ** 2)
    flicker = 0.8 + 0.2 * fbm(h, w, 92, octaves=3, base=4)
    return white((core + halo) * (0.15 + 0.85 * along) * flicker)


def helix():
    w, h = 32, 256
    xs, ys = grid(h, w)
    u = (xs / w - 0.5) * 2
    along = streaks(h, 95, 12, length=(0.15, 0.45))[:, None]
    band = np.clip(1 - np.abs(u) / 0.62, 0, 1) ** 0.7
    line = np.exp(-(u / 0.14) ** 2)
    halo = 0.35 * np.exp(-(u / 0.8) ** 2)
    flicker = 0.85 + 0.15 * fbm(h, w, 96, octaves=3, base=4)
    return white((0.55 * band + 0.6 * line + halo) * (0.55 + 0.45 * along) * flicker)


def glow():
    w, h = 32, 32
    xs, ys = grid(h, w)
    u = (xs / w - 0.5) * 2
    return white(np.exp(-(u / 0.45) ** 2))


def pool():
    size = 128
    xs, ys = grid(size)
    r = np.hypot(xs - size / 2, ys - size / 2) / (size / 2)
    centre = np.exp(-(r / 0.35) ** 2)
    ripples = 0.25 * (0.5 + 0.5 * np.cos(r * 28)) * np.exp(-(r / 0.75) ** 2)
    return white((centre + ripples) * smoothstep(1.0, 0.8, r))


def band():
    w, h = 64, 16
    xs, ys = grid(h, w)
    v = (ys / h - 0.5) * 2
    return white(np.exp(-(v / 0.5) ** 2))


def mote():
    size = 16
    xs, ys = grid(size)
    r = np.hypot(xs - 8, ys - 8) / 8
    return white(np.clip(1 - r, 0, 1) ** 2.2 + 0.6 * np.clip(1 - r / 0.3, 0, 1))


def spark():
    size = 16
    xs, ys = grid(size)
    dx, dy = np.abs(xs - 8) / 8, np.abs(ys - 8) / 8
    star = np.clip(1 - dx * 6, 0, 1) * np.clip(1 - dy, 0, 1) + np.clip(1 - dy * 6, 0, 1) * np.clip(1 - dx, 0, 1)
    heart = np.clip(1 - np.hypot(dx, dy) / 0.35, 0, 1)
    return white(star ** 1.5 + heart)


def dust():
    size = 8
    xs, ys = grid(size)
    r = np.hypot(xs - 4, ys - 4) / 4
    return white(np.clip(1 - r, 0, 1) ** 1.4 * 0.8)


def main():
    environment = {"flux_strand": strand(), "flux_helix": helix(), "flux_glow": glow(), "flux_pool": pool(), "flux_band": band()}
    particles = {"flux_mote": mote(), "flux_spark": spark(), "flux_dust": dust()}
    for name, img in environment.items():
        path = TEX / "environment" / f"{name}.png"
        img.save(path)
        meta = path.with_name(path.name + ".mcmeta")
        meta.write_text(json.dumps({"texture": {"blur": True, "clamp": name == "flux_pool"}}, indent=1) + "\n", encoding="utf-8")
        print("wrote", path.relative_to(ROOT), "and its .mcmeta")
    for name, img in particles.items():
        path = TEX / "particle" / f"{name}.png"
        img.save(path)
        print("wrote", path.relative_to(ROOT))
    for name in GONE:
        for path in (TEX / "environment" / f"{name}.png", TEX / "environment" / f"{name}.png.mcmeta"):
            if path.exists():
                path.unlink()
                print("removed", path.relative_to(ROOT))
    from PIL import Image
    sheet = Image.new("RGBA", (32 + 32 + 32 + 128 + 64 + 3 * 70 + 80, 270), (12, 18, 36, 255))
    x = 10
    for name in ("flux_strand", "flux_helix", "flux_glow", "flux_pool", "flux_band"):
        img = environment[name]
        sheet.alpha_composite(img, (x, 4))
        x += img.width + 10
    for img in particles.values():
        sheet.alpha_composite(img.resize((64, 64), Image.NEAREST), (x, 4))
        x += 70
    PREVIEW.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(PREVIEW)
    print("wrote", PREVIEW.relative_to(ROOT))


if __name__ == "__main__":
    main()
