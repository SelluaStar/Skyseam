"""First-pass textures for the Seam (spec section 20, "Suggested art order" 2). [FIRST PASS]

Writes, under src/main/resources/assets/skyseam/:
  textures/entity/seam/interior.png         512x256 pre-baked Halcyon sky seen through the Seam, tiles left-right
  textures/entity/seam/interior_glints.png  256x256 sparse glints on black (drawn additively, a nearer parallax layer)
  textures/particle/<id>_<n>.png            16x16 white sprites for seam_mote, seam_spark (sparks and voxel bits),
                                            thread_snap and scar
  particles/<id>.json                       the sprite list of each particle
and a scaled-up contact sheet of everything in build/previews/seam_textures.png to look at.

Deterministic: the same script always writes the same files. Run with tools/.venv/Scripts/python.exe.
Replace any file by name (docs/ASSET-PATHS.md); particle sprites are tinted by the game, so draw them white.
"""
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import palette as P  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "skyseam"
PREVIEW = ROOT / "build" / "previews"


def rgb(c):
    return np.array(c, dtype=np.float64) / 255.0


def gradient(t, stops):
    """Colour at t (array) along [(position, colour)] stops."""
    out = np.zeros(t.shape + (3,))
    positions = [s[0] for s in stops]
    colours = [rgb(s[1]) for s in stops]
    for k in range(len(stops) - 1):
        a, b = positions[k], positions[k + 1]
        mask = (t >= a) & (t <= b)
        f = ((t - a) / (b - a))[..., None]
        f = f * f * (3 - 2 * f)
        out = np.where(mask[..., None], colours[k] * (1 - f) + colours[k + 1] * f, out)
    out = np.where((t < positions[0])[..., None], colours[0], out)
    out = np.where((t > positions[-1])[..., None], colours[-1], out)
    return out


def wrap_dx(x, x0, width):
    d = np.abs(x - x0)
    return np.minimum(d, width - d)


def periodic_noise(rng, w, h, x_freqs, y_scale, octaves=5):
    """Smooth noise that tiles left-right: sums of waves with whole-number frequencies across the width."""
    x = np.arange(w)[None, :] / w
    y = np.arange(h)[:, None] / h
    total = np.zeros((h, w))
    amp = 1.0
    for o in range(octaves):
        for _ in range(4):
            fx = rng.integers(x_freqs[0], x_freqs[1] + 1) * (2 ** o)
            fy = rng.uniform(0.5, 1.5) * y_scale * (2 ** o)
            px, py = rng.uniform(0, 2 * math.pi, 2)
            total += amp * np.sin(2 * math.pi * fx * x + px + np.sin(2 * math.pi * fy * y + py) * 1.3)
        amp *= 0.5
    total -= total.min()
    return total / total.max()


def interior(rng):
    w, h = 512, 256
    horizon = 0.53
    y = (np.arange(h)[:, None] + 0.5) / h * np.ones((1, w))
    x = np.ones((h, 1)) * np.arange(w)[None, :]

    sky = gradient(y, [(0.0, P.SKY_ZENITH), (0.2, P.SKY_HIGH), (0.36, P.SKY_LILAC), (0.47, P.SKY_GLOW), (horizon, P.HORIZON)])
    sea = gradient(y, [(horizon, P.SEA_SHALLOW), (0.66, P.SEA_TEAL), (1.0, P.SEA_DEEP)])
    img = np.where((y < horizon)[..., None], sky, sea)

    # Prismatic bands: soft diagonal washes of pink, orange and teal that tile across the width.
    xs = x / w
    for colour, fx, fy, phase in ((P.PRISM_PINK, 2, 1.7, 0.0), (P.PRISM_ORANGE, 3, -1.2, 2.1), (P.PRISM_TEAL, 1, 2.3, 4.0)):
        band = 0.5 + 0.5 * np.sin(2 * math.pi * (fx * xs + fy * y) + phase)
        strength = np.where(y < horizon, 0.16, 0.08) * band ** 3
        img = img * (1 - strength[..., None]) + rgb(colour) * strength[..., None]

    # Distant floating islands just above the horizon: a dome on top, a tapering root below.
    for k in range(6):
        cx = rng.uniform(0, w)
        cy = rng.uniform(horizon - 0.12, horizon - 0.05) * h
        rx = rng.uniform(10, 26)
        top = rng.uniform(5, 11)
        root = rng.uniform(8, 18)
        dx = wrap_dx(x, cx, w) / rx
        dy = (y * h - cy)
        dome = (dx ** 2 + (dy / top) ** 2 <= 1) & (dy <= 0)
        taper = (dy > 0) & (dy < root) & (dx <= np.clip(1 - dy / root, 0, 1) ** 1.5)
        mask = (dome | taper).astype(np.float64)
        colour = P.ISLAND_FAR if k % 2 else P.ISLAND_NEAR
        alpha = 0.32 * mask
        img = img * (1 - alpha[..., None]) + rgb(colour) * alpha[..., None]

    # Clouds: pink-white streaks in the upper sky.
    noise = periodic_noise(rng, w, h, (1, 3), 3.0)
    band = np.clip(1 - np.abs(y - 0.25) / 0.17, 0, 1)
    cloud = np.clip((noise - 0.55) / 0.3, 0, 1) * band * 0.5
    img = img * (1 - cloud[..., None]) + rgb((255, 236, 244)) * cloud[..., None]

    # The Lantern-Sun and its long reflection on the Mirror Sea.
    sun_x, sun_y = 0.27 * w, 0.4 * h
    d = np.sqrt(wrap_dx(x, sun_x, w) ** 2 + (y * h - sun_y) ** 2)
    glow = np.exp(-(d / 18) ** 2) * 0.5 + np.exp(-(d / 42) ** 2) * 0.35 + np.exp(-(d / 110) ** 2) * 0.2
    img = img + rgb(P.LANTERN_GLOW) * glow[..., None] * 0.6
    # The orb itself: a soft-edged disc, brightest in the middle, painted over the glow.
    core = np.clip((12 - d) / 4, 0, 1)
    orb = rgb(P.LANTERN_CORE) * (1 - 0.04 * (d / 12) ** 2)[..., None] + 0.04
    img = img * (1 - core[..., None]) + orb * core[..., None]
    rx, ry = wrap_dx(x, sun_x, w), (y * h - (2 * horizon * h - sun_y))
    reflection = np.exp(-(rx / 16) ** 2 - (ry / 34) ** 2) * (y > horizon) * 0.45
    ripples = 0.65 + 0.35 * np.sin(y * h * 1.9 + np.sin(xs * 2 * math.pi * 6) * 2)
    img = img + rgb(P.LANTERN_GLOW) * (reflection * ripples)[..., None]

    # Mirror Sea ripples and a bright horizon line.
    sea_mask = (y > horizon).astype(np.float64)
    shimmer = 0.035 * np.sin(2 * math.pi * (y * 46 + 0.25 * np.sin(2 * math.pi * xs * 4))) * sea_mask
    img = img + shimmer[..., None]
    line = np.exp(-((y - horizon) * h / 1.2) ** 2) * 0.35
    img = img + rgb(P.PEARL) * line[..., None]

    # A scatter of faint stars high up.
    for _ in range(90):
        sx, sy = rng.uniform(0, w), rng.uniform(0, 0.32) * h
        r = np.sqrt(wrap_dx(x, sx, w) ** 2 + (y * h - sy) ** 2)
        img = img + rgb(P.PEARL) * (np.exp(-(r / 0.8) ** 2) * rng.uniform(0.25, 0.6))[..., None]

    return to_image(np.clip(img, 0, 1))


def glints(rng):
    size = 256
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float64)
    img = np.zeros((size, size, 3))
    colours = [P.PEARL, P.PRISM_PINK, P.PRISM_TEAL, P.GOLD_LIGHT, P.PASTEL_LAVENDER]
    for k in range(70):
        cx, cy = rng.uniform(0, size, 2)
        dx, dy = wrap_dx(xx, cx, size), wrap_dx(yy, cy, size)
        r = np.sqrt(dx ** 2 + dy ** 2)
        brightness = rng.uniform(0.4, 1.0)
        spot = np.exp(-(r / rng.uniform(0.7, 1.8)) ** 2)
        if k % 5 == 0:
            # A four-point star: thin cross arms.
            arm = rng.uniform(4, 9)
            spot = spot + 0.6 * (np.exp(-(dx / 0.6) ** 2) * np.exp(-(dy / arm) ** 2) + np.exp(-(dy / 0.6) ** 2) * np.exp(-(dx / arm) ** 2))
        img = img + rgb(colours[k % len(colours)]) * (spot * brightness)[..., None]
    return to_image(np.clip(img, 0, 1))


def sprite(alpha):
    """A white 16x16 sprite with the given alpha mask."""
    a = np.clip(alpha, 0, 1)
    rgba = np.zeros((16, 16, 4), dtype=np.uint8)
    rgba[..., :3] = 255
    rgba[..., 3] = np.round(a * 255).astype(np.uint8)
    return Image.fromarray(rgba, "RGBA")


def particle_sprites(rng):
    yy, xx = np.mgrid[0:16, 0:16].astype(np.float64) + 0.5
    c = 8.0
    r = np.sqrt((xx - c) ** 2 + (yy - c) ** 2)
    sprites = {}
    # Soft round motes, from crisp to very soft.
    # The window takes every sprite to nothing before the edge of its square.
    window = np.clip(1 - r / 7.8, 0, 1) ** 0.6
    sprites["seam_mote"] = [sprite(np.exp(-(r / s) ** p) * window) for s, p in ((3.0, 2), (4.2, 2), (5.5, 1.6), (6.5, 1.3))]
    # Sparks: a bright centre with four arms, the arms turned a little each time.
    sparks = []
    for k, turn in enumerate((0.0, 0.4, 0.78)):
        u = (xx - c) * math.cos(turn) + (yy - c) * math.sin(turn)
        v = -(xx - c) * math.sin(turn) + (yy - c) * math.cos(turn)
        arms = np.exp(-(u / 0.7) ** 2) * np.exp(-(np.abs(v) / 5.5)) + np.exp(-(v / 0.7) ** 2) * np.exp(-(np.abs(u) / 5.5))
        sparks.append(sprite((np.exp(-(r / 1.6) ** 2) + 0.9 * arms) * window))
    # Voxel bits: small solid squares with a soft glow, shed from the border like broken-off pixels.
    for half, glow in ((2.0, 0.35), (3.0, 0.25)):
        square = ((np.abs(xx - c) <= half) & (np.abs(yy - c) <= half)).astype(np.float64)
        sparks.append(sprite(np.maximum(square, glow * np.exp(-(r / (half + 2.5)) ** 2)) * window))
    sprites["seam_spark"] = sparks
    # Thread fragments: short curved strands, tinted gold by the game.
    threads = []
    for k in range(3):
        bend = (k - 1) * 0.08
        t = np.linspace(-1, 1, 64)
        px = c + t * 6
        py = c + bend * (t * 6) ** 2 - bend * 12
        mask = np.zeros((16, 16))
        for x0, y0 in zip(px, py):
            mask = np.maximum(mask, np.exp(-(((xx - x0) ** 2 + (yy - y0) ** 2) / 0.55)))
        threads.append(sprite(mask * (0.6 + 0.4 * np.exp(-(r / 6) ** 2))))
    sprites["thread_snap"] = threads
    # Scar dust: small faint diamonds.
    sprites["scar"] = [sprite(np.clip(1 - (np.abs(xx - c) + np.abs(yy - c)) / s, 0, 1) ** 1.5 * 0.85) for s in (3.5, 4.5, 5.5)]
    return sprites


def to_image(arr):
    return Image.fromarray(np.round(arr * 255).astype(np.uint8), "RGB")


def main():
    rng = np.random.default_rng(20261008)
    entity = ASSETS / "textures" / "entity" / "seam"
    particle_tex = ASSETS / "textures" / "particle"
    particle_defs = ASSETS / "particles"
    for folder in (entity, particle_tex, particle_defs, PREVIEW):
        folder.mkdir(parents=True, exist_ok=True)

    sky = interior(rng)
    sky.save(entity / "interior.png")
    glint = glints(rng)
    glint.save(entity / "interior_glints.png")

    written = [entity / "interior.png", entity / "interior_glints.png"]
    sheet_items = []
    for pid, images in particle_sprites(rng).items():
        names = []
        for n, image in enumerate(images):
            name = f"{pid}_{n}"
            image.save(particle_tex / f"{name}.png")
            written.append(particle_tex / f"{name}.png")
            names.append(f"skyseam:{name}")
            sheet_items.append(image)
        (particle_defs / f"{pid}.json").write_text(json.dumps({"textures": names}, indent=2) + "\n", encoding="utf-8")

    # Contact sheet: the sky, the glints, then every sprite at 8x on a dark background.
    sheet = Image.new("RGB", (1024, 512 + 256), (24, 22, 34))
    sheet.paste(sky.resize((1024, 512), Image.NEAREST), (0, 0))
    sheet.paste(glint.resize((256, 256), Image.NEAREST), (0, 512))
    for k, image in enumerate(sheet_items):
        big = image.resize((96, 96), Image.NEAREST)
        sheet.paste(big, (272 + (k % 7) * 104, 524 + (k // 7) * 80), big.resize((96, 96)))
    sheet.save(PREVIEW / "seam_textures.png")
    for path in written:
        print(path.relative_to(ROOT))
    print(PREVIEW.relative_to(ROOT) / "seam_textures.png")


if __name__ == "__main__":
    main()
