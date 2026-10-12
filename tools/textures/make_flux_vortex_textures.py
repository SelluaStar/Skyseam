"""The flux vortex's textures (the author's request after M3: a Pandora-like vortex of wind, light and floating rock).

Writes, under src/main/resources/assets/skyseam/textures/:
  environment/flux_ribbon.png   64x256  one strand of the vortex's spiral: bright filaments running up it, twisting
                                        and braiding, fading at its edges; tiles top to bottom
  environment/flux_core.png     32x128  the column's glowing heart: a soft bright middle streaked with light; tiles top
                                        to bottom
  environment/flux_haze.png     128x128 turbulent veils of air for the haze round the column; tiles both ways
  environment/flux_mist.png     128x128 the vortex's body: a soft veil of mist, streaked and torn, mostly covering;
                                        tiles both ways
  environment/flux_ground.png   128x128 the ring of swirling light on the ground at the vortex's foot
  particle/flux_mote.png        16x16   a soft mote with a bright heart, drawn circling up the column
  particle/flux_spark.png       16x16   a four-pointed spark, for the flashes as it forms and the sparks off its rocks
  particle/flux_dust.png        8x8     a soft puff of dust, swept round the vortex's foot
Everything is white, its brightness in its alpha, so the code tints it (teal, cyan and violet). The four environment
textures get a `.png.mcmeta` with "blur", as they are stretched over a column hundreds of blocks tall. Deterministic.
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


def white(alpha):
    h, w = alpha.shape
    return rgba(np.full((h, w, 3), 255.0), np.clip(alpha, 0, 1) * 255)


def ribbon():
    """Filaments along the strand: each a thin bright line wandering across it, braiding with the others, with
    brighter knots travelling up; the whole strand fades to its edges."""
    w, h = 64, 256
    xs, ys = grid(h, w)
    u = xs / w
    v = ys / h
    out = np.zeros((h, w))
    rng = np.random.default_rng(71)
    for k in range(7):
        centre = rng.uniform(0.2, 0.8)
        amp = rng.uniform(0.05, 0.18)
        waves = rng.integers(1, 4)
        phase = rng.uniform(0, np.pi * 2)
        width = rng.uniform(0.012, 0.035)
        line = centre + amp * np.sin(2 * np.pi * waves * v + phase) + 0.05 * np.sin(2 * np.pi * (waves + 2) * v + phase * 2)
        glow = np.exp(-((u - line) / width) ** 2) * rng.uniform(0.5, 1.0)
        knots = 0.6 + 0.4 * np.sin(2 * np.pi * rng.integers(2, 6) * v + phase) ** 8
        out = np.maximum(out, glow * knots)
    wisps = fbm(h, w, 72, octaves=4, base=4) ** 2 * 0.35
    edge = smoothstep(0.0, 0.2, u) * smoothstep(1.0, 0.8, u)
    return white((out + wisps) * edge)


def core():
    """A soft bright middle, streaked along its length."""
    w, h = 32, 128
    xs, ys = grid(h, w)
    u = xs / w
    streaks = 0.65 + 0.35 * fbm(h, w, 73, octaves=3, base=8)
    body = np.exp(-((u - 0.5) / 0.22) ** 2)
    return white(body * streaks)


def haze():
    """Veils of air: turbulent noise, mostly clear with soft brighter folds."""
    size = 128
    n = fbm(size, size, 74, octaves=5, base=4)
    folds = np.clip((n - 0.42) / 0.4, 0, 1) ** 1.5
    return white(folds * 0.8)


def mist():
    """A veil of mist: soft, mostly covering, streaked along the turn of the vortex and torn in places."""
    size = 128
    streaks = fbm(size, size, 76, octaves=4, base=3)
    torn = fbm(size, size, 77, octaves=5, base=6)
    veil = np.clip(0.25 + 0.9 * streaks - 0.5 * np.clip((torn - 0.62) / 0.2, 0, 1), 0, 1)
    return white(veil ** 1.3)


def ground():
    """A ring of light on the ground: spiral arms wound round the foot, brightest at the ring, faint in the middle
    and fading to nothing at the edge."""
    size = 128
    xs, ys = grid(size)
    c = size / 2
    dx, dy = xs - c, ys - c
    r = np.hypot(dx, dy) / c
    a = np.arctan2(dy, dx)
    detail = fbm(size, size, 75, octaves=4, base=6, wrap=False)
    arms = 0.5 + 0.5 * np.sin(a * 5 - np.log(r + 0.05) * 6 + detail * 3)
    ring = np.exp(-((r - 0.62) / 0.16) ** 2)
    inner = 0.25 * smoothstep(0.1, 0.45, r)
    fade = smoothstep(1.0, 0.82, r)
    return white((arms ** 2 * (ring + inner) + 0.35 * ring) * fade)


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
    environment = {"flux_ribbon": ribbon(), "flux_core": core(), "flux_haze": haze(), "flux_mist": mist(), "flux_ground": ground()}
    particles = {"flux_mote": mote(), "flux_spark": spark(), "flux_dust": dust()}
    for name, img in environment.items():
        path = TEX / "environment" / f"{name}.png"
        img.save(path)
        meta = path.with_name(path.name + ".mcmeta")
        meta.write_text(json.dumps({"texture": {"blur": True, "clamp": name == "flux_ground"}}, indent=1) + "\n", encoding="utf-8")
        print("wrote", path.relative_to(ROOT), "and its .mcmeta")
    for name, img in particles.items():
        path = TEX / "particle" / f"{name}.png"
        img.save(path)
        print("wrote", path.relative_to(ROOT))
    from PIL import Image
    sheet = Image.new("RGBA", (64 * 3 + 32 + 138 * 3 + 16 * 4 * 3 + 70, 260), (12, 18, 36, 255))
    x = 10
    for name in ("flux_ribbon", "flux_core"):
        img = environment[name]
        sheet.alpha_composite(img, (x, 2))
        x += img.width + 10
    for name in ("flux_haze", "flux_mist", "flux_ground"):
        sheet.alpha_composite(environment[name], (x, 2))
        x += 138
    for name in particles:
        sheet.alpha_composite(particles[name].resize((64, 64), Image.NEAREST), (x, 2))
        x += 70
    PREVIEW.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(PREVIEW)
    print("wrote", PREVIEW.relative_to(ROOT))


if __name__ == "__main__":
    main()
