"""Write the placeholder mod icon src/main/resources/logo.png (spec section 22, item 7).

A plain 128 x 128 placeholder: a pastel disc (pink to teal, the Seam's interior colours) with a white,
slightly jagged vertical line for the Seam. The author replaces it with real art under the same name.

Run: tools/.venv/Scripts/python.exe tools/textures/make_placeholder_logo.py
"""
from pathlib import Path

import numpy as np
from PIL import Image

SIZE = 128
OUT = Path(__file__).resolve().parents[2] / "src/main/resources/logo.png"

PINK = np.array([244, 184, 214], dtype=float)
TEAL = np.array([140, 214, 206], dtype=float)
NIGHT = np.array([46, 42, 74], dtype=float)
WHITE = np.array([255, 255, 255], dtype=float)


def build():
    y, x = np.mgrid[0:SIZE, 0:SIZE].astype(float)
    cx = cy = (SIZE - 1) / 2
    r = np.hypot(x - cx, y - cy)
    radius = SIZE * 0.46

    # Diagonal pastel gradient inside the disc, a dark rim just inside its edge.
    t = np.clip((x + y) / (2 * (SIZE - 1)), 0, 1)[..., None]
    rgb = PINK * (1 - t) + TEAL * t
    rim = (r > radius - 5)[..., None]
    rgb = np.where(rim, NIGHT, rgb)

    # The Seam: a white vertical line stepping sideways every few pixels, like the voxel-edged rift.
    steps = np.array([0, 1, 1, 0, -1, -1, 0, 1, 2, 1, 0, -1, -2, -1, 0, 0])
    offset = steps[(y // 8).astype(int) % len(steps)]
    seam_x = cx + offset * 3
    seam = (np.abs(x - seam_x) <= 2.5) & (np.abs(y - cy) < radius * 0.72)
    glow = (np.abs(x - seam_x) <= 6) & (np.abs(y - cy) < radius * 0.76)
    rgb = np.where(glow[..., None] & ~seam[..., None], rgb * 0.45 + WHITE * 0.55, rgb)
    rgb = np.where(seam[..., None], WHITE, rgb)

    alpha = np.clip((radius - r) * 2 + 0.5, 0, 1) * 255
    image = np.dstack([rgb, alpha]).round().astype(np.uint8)
    return Image.fromarray(image, "RGBA")


if __name__ == "__main__":
    OUT.parent.mkdir(parents=True, exist_ok=True)
    build().save(OUT)
    print(f"wrote {OUT} ({SIZE}x{SIZE})")
