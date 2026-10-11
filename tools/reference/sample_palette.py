"""Samples the Halcyon's colours from the author's reference images (reference/5-, 6-, 7-) into palette.json.

Each named colour is the median of one or more cells of a 16 x 16 grid over an image (the middle 60 % of each cell,
so edges between objects don't bleed in). The texture, data and sky generators read palette.json, so the world's
colours come from the images rather than from guesses.

Run with tools/.venv/Scripts/python.exe tools/reference/sample_palette.py. Writes tools/reference/palette.json and a
swatch sheet build/previews/reference_palette.png.
"""
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
REFERENCE = ROOT / "reference"
OUT = Path(__file__).resolve().parent / "palette.json"
PREVIEW = ROOT / "build" / "previews" / "reference_palette.png"
GRID = 16

# name -> (image, [(column, row), ...] on the 16 x 16 grid, mode). Modes: "median" for skies, water and rock;
# "bright" (the brightest fifth) for lit or glowing things; "saturated" (the most saturated quarter) for foliage and
# crystals; "dark" (the darkest fifth) for the Rift Eye's disc.
A = "5-lagoon-dusk.webp"
B = "6-spires-dusk.webp"
C = "7-archipelago-day.webp"
SAMPLES = {
    # The night sky (image A): the Hush.
    "hush_zenith": (A, [(6, 0), (7, 0)], "median"),
    "hush_high": (A, [(7, 1), (6, 1)], "median"),
    "hush_mid": (A, [(5, 3), (6, 3)], "median"),
    "hush_low": (A, [(12, 5), (13, 5)], "median"),
    "hush_horizon": (A, [(14, 6), (14, 7)], "median"),
    # The dusk sky (image B): Dusk-Prism.
    "dusk_zenith": (B, [(7, 0), (8, 0)], "median"),
    "dusk_high": (B, [(6, 1), (7, 2)], "median"),
    "dusk_mid": (B, [(8, 5), (7, 5)], "median"),
    "dusk_low": (B, [(8, 6), (12, 6)], "median"),
    "dusk_horizon": (B, [(8, 7), (14, 7)], "median"),
    "nebula": (B, [(9, 2), (9, 3), (10, 3)], "saturated"),
    "ring_moon": (B, [(13, 3), (14, 4)], "saturated"),
    "moon": (B, [(3, 1)], "bright"),
    # The day sky (image C): Noon-Bloom. The middle of the gradient is interpolated by the sky code.
    "noon_zenith": (C, [(8, 0), (9, 0)], "median"),
    "noon_high": (C, [(7, 2), (8, 2)], "median"),
    "noon_horizon": (C, [(15, 3), (15, 2)], "median"),
    "sun_glare": (C, [(15, 1)], "bright"),
    "cloud_lit": (C, [(13, 9), (14, 9)], "bright"),
    "cloud_shade": (C, [(12, 9), (13, 10)], "median"),
    "rift_eye": (C, [(3, 1), (4, 1)], "dark"),
    "rift_swirl": (C, [(5, 2), (6, 1)], "bright"),
    # Water.
    "water_dusk": (A, [(6, 12), (8, 12), (7, 11)], "median"),
    "water_night": (B, [(7, 12), (8, 13)], "median"),
    "water_day": (C, [(6, 9), (7, 10), (8, 12)], "median"),
    "waterfall": (A, [(9, 5), (9, 6)], "bright"),
    # Land.
    "rock_dark": (A, [(1, 10), (0, 12), (2, 12)], "median"),
    "rock_spire": (B, [(11, 4), (10, 6)], "median"),
    "rock_day": (C, [(9, 7), (10, 8)], "median"),
    "rock_day_lit": (C, [(9, 7), (10, 8)], "bright"),
    "sand": (C, [(10, 10), (11, 10)], "bright"),
    "foliage_violet": (A, [(2, 6), (3, 6), (13, 2)], "saturated"),
    "foliage_dark": (C, [(13, 12)], "saturated"),
    "blossom_white": (A, [(10, 6)], "bright"),
    "blossom_lavender": (C, [(0, 8), (1, 8)], "bright"),
    "blossom_pink": (C, [(7, 4), (8, 4)], "bright"),
    "coral_pink": (A, [(14, 12)], "saturated"),
    "crystal_violet": (B, [(3, 12), (15, 12)], "saturated"),
    "crystal_pale": (C, [(4, 11)], "bright"),
    "glow_cube": (A, [(7, 13)], "bright"),
}


def cell(image, column, row):
    w, h = image.size
    x0, x1 = (column + 0.2) * w / GRID, (column + 0.8) * w / GRID
    y0, y1 = (row + 0.2) * h / GRID, (row + 0.8) * h / GRID
    return np.asarray(image.crop((int(x0), int(y0), int(x1), int(y1)))).reshape(-1, 3)


def pick(pixels, mode):
    """The pixels a mode keeps."""
    if mode == "median":
        return pixels
    values = pixels.astype(float)
    if mode == "saturated":
        key = values.max(axis=1) - values.min(axis=1)
        return pixels[key >= np.percentile(key, 75)]
    key = values.sum(axis=1)
    if mode == "bright":
        return pixels[key >= np.percentile(key, 80)]
    return pixels[key <= np.percentile(key, 20)]


def main():
    images = {}
    palette = {}
    for name, (file, cells, mode) in SAMPLES.items():
        if file not in images:
            images[file] = Image.open(REFERENCE / file).convert("RGB")
        pixels = np.concatenate([cell(images[file], c, r) for c, r in cells])
        rgb = [int(v) for v in np.median(pick(pixels, mode), axis=0)]
        palette[name] = "#{:02X}{:02X}{:02X}".format(*rgb)
    OUT.write_text(json.dumps(palette, indent=2) + "\n", encoding="utf-8")

    font = ImageFont.truetype("arial.ttf", 13)
    cols = 4
    rows = (len(palette) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * 220, rows * 34), (24, 22, 30))
    draw = ImageDraw.Draw(sheet)
    for k, (name, hex_colour) in enumerate(palette.items()):
        x, y = (k % cols) * 220, (k // cols) * 34
        draw.rectangle([x + 4, y + 4, x + 54, y + 30], fill=hex_colour)
        draw.text((x + 60, y + 9), f"{name} {hex_colour}", fill=(230, 226, 240), font=font)
    PREVIEW.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(PREVIEW)
    print(f"wrote {OUT.relative_to(ROOT)} ({len(palette)} colours) and {PREVIEW.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
