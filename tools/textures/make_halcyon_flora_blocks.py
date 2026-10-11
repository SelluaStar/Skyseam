"""The Halcyon's wood, leaves, mushrooms and plants, painted with pixelkit.

Writes, under src/main/resources/assets/skyseam/textures/block/:
  elder_log, elder_log_top, elder_log_glow (full-bright vein overlay), elder_leaves[_1]   the giant glowing-vein
                                           trees of the Elderwood (image 8)
  duskwood_log, duskwood_log_top, duskwood_leaves[_1]   violet-barked trees with violet crowns (images 5, 6)
  spirepine_needles[_1]                    the dark conifers of the Sunreach Cliffs (image 11)
  pale_blossom_leaves[_1], wisteria, wisteria_tip       white umbrella crowns and purple drapes (images 5, 6)
  ember_cap, parasol_cap, glowcap, bell_cap (each with _inside: the gills seen from under the cap),
  giant_mushroom_stem(_inside)             the giant mushrooms of the Capwood (image 10)
  ember_shroom, glow_shroom                small mushrooms (cross models)
  skylupine_top, skylupine_bottom          a tall purple lupine (image 12)
  hanging_ivy, hanging_ivy_tip             green trailing ivy (image 13)
  bloomcoral_pink, bloomcoral_violet, bloomcoral_white  coral bushes, on land and under water
and build/previews/halcyon_flora_blocks.png.
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

ELDER_BARK = R("#434C49", dark=0.24, light=0.26, cool=18, warm=14)
ELDER_HEART = R("#B9C49A", dark=0.30, light=0.12)
VEIN = [(70, 214, 236), (130, 244, 255), (214, 255, 255)]
ELDER_LEAF = R("#356F55", dark=0.24, light=0.30, cool=14, warm=26, light_chroma=0.9)
DUSK_BARK = R("#4D3D5C", dark=0.24, light=0.26, cool=12, warm=12)
DUSK_HEART = R("#B6A0BE", dark=0.30, light=0.12)
DUSK_LEAF = R("#7A4DC2", dark=0.26, light=0.26, cool=10, warm=30, light_chroma=0.8)
DUSK_LEAF_PINK = R("#A04DB4", dark=0.26, light=0.26, cool=10, warm=30, light_chroma=0.8)
PINE = R("#2F624A", dark=0.22, light=0.28, cool=12, warm=24)
BLOSSOM = R("#E6D4E8", dark=0.30, light=0.08, cool=20, warm=4, shadow_chroma=2.2)
BLOSSOM_PINK = R("#F0C8DC", dark=0.30, light=0.06, cool=20, warm=4, shadow_chroma=1.6)
WISTERIA = R("#8C62D8", dark=0.28, light=0.26, cool=8, warm=26, light_chroma=0.6)
STEM_GREEN = R("#3E7046", dark=0.20, light=0.24)

EMBER = R("#D2452A", dark=0.26, light=0.20, cool=30, warm=22, light_chroma=0.85)
EMBER_SPOT = R("#F7EADC", dark=0.16, light=0.04, cool=0, warm=4)
PARASOL = R("#E2CFAA", dark=0.28, light=0.08, cool=24, warm=6)
SCALE = R("#8B5B3C", dark=0.16, light=0.16)
GLOW = R("#6B3FC6", dark=0.22, light=0.32, cool=6, warm=0, light_chroma=0.6)
GLOW_SPOT = [(150, 246, 255), (210, 255, 255)]
BELL = R("#C285CF", dark=0.26, light=0.16, cool=10, warm=10)
GILLS = R("#E3C6A6", dark=0.30, light=0.08, cool=20, warm=6)
GILLS_GLOW = R("#D7B8FF", dark=0.28, light=0.08)
STEM = R("#E4DDCF", dark=0.30, light=0.06, cool=22, warm=4)
LUPINE = R("#7C4ED6", dark=0.22, light=0.32, cool=6, warm=0, light_chroma=0.5)
IVY = R("#4E8A3E", dark=0.22, light=0.26, cool=12, warm=28)
CORAL = {"pink": R("#E0709E", dark=0.24, light=0.18), "violet": R("#9157D8", dark=0.24, light=0.22),
         "white": R("#EDE3EC", dark=0.30, light=0.04, shadow_chroma=2.0)}


def blank():
    return np.zeros((N, N, 4), dtype=np.uint8)


def put(arr, x, y, colour):
    if 0 <= x < N and 0 <= y < N:
        arr[y, x] = (*colour, 255)


def image(arr):
    return px.Image.fromarray(arr, "RGBA")


# ---- Wood ------------------------------------------------------------------------------------------------------------

def log_side(seed, ramp):
    rng = np.random.default_rng(seed)
    return px.paint(px.bark(rng, base=3.2, fissure=0.4, knots=1, horizontal=2), ramp)


def log_top(seed, heart, bark):
    rng = np.random.default_rng(seed)
    t = px.rings(rng, base=3.6, rim=2, step=2.2, rim_tone=0, wobble=0.6)
    img = np.array(px.paint(t, heart))
    rim = np.array(px.paint(px.bark(rng, base=3.0), bark))
    c = (N - 1) / 2
    for y in range(N):
        for x in range(N):
            if max(abs(x - c), abs(y - c)) > c - 2:
                img[y, x] = rim[y, x]
    return image(img)


def elder_glow(seed):
    """Glowing veins that wind up the bark in its fissures, branching once or twice: drawn full-bright."""
    rng = np.random.default_rng(seed)
    arr = blank()
    for start in (3, 11):
        x = start + int(rng.integers(-1, 2))
        for y in range(N):
            c = VEIN[2] if rng.random() < 0.18 else VEIN[1] if rng.random() < 0.6 else VEIN[0]
            put(arr, x % N, y, c)
            if rng.random() < 0.35:
                x += int(rng.choice([-1, 1]))
            if rng.random() < 0.12:
                bx = x
                for k in range(int(rng.integers(2, 4))):
                    bx += 1 if start < 8 else -1
                    put(arr, bx % N, (y - k - 1) % N, VEIN[0])
    return image(arr)


# ---- Leaves ----------------------------------------------------------------------------------------------------------

def leaves(seed, ramps, weights, count=28, radius=(1.5, 2.6), holes=0.14, glints=None):
    rng = np.random.default_rng(seed)
    tone, alpha = px.clumps(rng, count=count, radius=radius, base=(2.4, 4.4), coverage_holes=holes)
    labels = (px.fbm(rng, (2, 4), (0.6, 0.4)) * 3).astype(int) - (px.fbm(rng, (2, 4), (0.6, 0.4)) * 3).astype(int)
    labels = labels - labels.min()
    out = np.array(px.paint(tone, ramps[0], alpha))
    if len(ramps) > 1:
        pick = rng.choice(len(ramps), labels.max() + 1, p=weights)
        for k in range(1, len(ramps)):
            other = np.array(px.paint(tone, ramps[k], alpha))
            mask = pick[labels] == k
            out[mask] = other[mask]
    img = image(out)
    if glints:
        img = px.dots(img, rng, glints, 2, avoid=alpha == 0)
    return img


def needles(seed):
    """Conifer needles: short dark strokes fanning down and out from twigs, lit on top."""
    rng = np.random.default_rng(seed)
    tone = np.zeros((N, N))
    alpha = np.zeros((N, N))
    for _ in range(22):
        cx, cy = rng.integers(0, N, 2)
        length = int(rng.integers(3, 6))
        for side in (-1, 1):
            for k in range(length):
                x = (cx + side * k) % N
                y = (cy + k // 2) % N
                tone[y, x] = 4.2 - k * 0.5 + rng.uniform(-0.3, 0.3)
                alpha[y, x] = 255
                y2 = (y + 1) % N
                if alpha[y2, x] == 0:
                    tone[y2, x] = 1.6
                    alpha[y2, x] = 255
    for y in range(N):
        for x in range(N):
            if alpha[y, x] == 0 and rng.random() < 0.55:
                tone[y, x] = 2.6 + rng.uniform(-0.5, 0.5)
                alpha[y, x] = 255
    return px.paint(np.clip(tone, 0, 6), PINE, alpha)


def wisteria(tip):
    """Hanging racemes: grape-like clusters of florets, dark at the top, pale towards the tips."""
    arr = blank()
    rng = np.random.default_rng(7 if tip else 5)
    columns = [2, 6, 10, 13]
    for cx in columns:
        top = int(rng.integers(0, 3))
        length = int(rng.integers(8, 12)) if tip else N
        for y in range(top, min(N, top + length)):
            t = (y - top) / max(1, length)
            width = 2 if (not tip or t < 0.6) else 1
            for dx in range(-width + 1, width):
                if rng.random() < 0.85:
                    tone = 2 + 3.5 * t + (0.8 if dx < 0 else -0.4) + rng.uniform(-0.4, 0.4)
                    put(arr, cx + dx, y, WISTERIA[int(np.clip(round(tone), 0, 6))])
            if y % 3 == 0:
                put(arr, cx, y, STEM_GREEN[2])
    return image(arr)


# ---- Mushrooms -------------------------------------------------------------------------------------------------------

def cap(seed, ramp, spot_ramp=None, spot_count=5, scales=False, ridges=False, glow_spots=None):
    rng = np.random.default_rng(seed)
    tone = np.full((N, N), 3.4) + 0.55 * px.fbm(rng, (2, 4, 8), (0.5, 0.3, 0.2)) * 2
    if ridges:
        for x in range(N):
            if x % 4 == 0:
                tone[:, x] -= 1.0
            elif x % 4 == 1:
                tone[:, x] += 0.8
    tone = px.speckle(rng, tone, 0.05, 1.0, 0.5)
    img = px.paint(np.clip(tone, 0, 6), ramp)
    if scales:
        arr = np.array(img)
        for _ in range(9):
            y, x = rng.integers(0, N, 2)
            c = SCALE[int(rng.integers(2, 5))]
            arr[y, x, :3] = c
            arr[y, (x + 1) % N, :3] = SCALE[1]
            if rng.random() < 0.5:
                arr[(y + 1) % N, x, :3] = SCALE[1]
        img = image(arr)
    if spot_ramp is not None or glow_spots is not None:
        mask = px.spots(rng, spot_count, (0.9, 2.0))
        arr = np.array(img)
        for y in range(N):
            for x in range(N):
                if mask[y, x] >= 1:
                    if glow_spots:
                        arr[y, x, :3] = glow_spots[1] if (x + y) % 3 else glow_spots[0]
                    else:
                        lit = mask[(y - 1) % N, (x - 1) % N] < 1
                        arr[y, x, :3] = spot_ramp[6 if lit else 4]
                elif mask[y, x] > 0:
                    arr[y, x, :3] = px.shift(tuple(arr[y, x, :3]), 0.08, 0.6)
        img = image(arr)
    return img


def gills(seed, ramp):
    """A cap's underside: fine radial gills, read on a block face as close parallel lines that wander slightly."""
    rng = np.random.default_rng(seed)
    tone = np.full((N, N), 3.8) + 0.4 * px.fbm(rng, (2, 4), (0.6, 0.4)) * 2
    phase = rng.uniform(0, 6.28, N)
    for x in range(N):
        if x % 2 == 1:
            continue
        for y in range(N):
            # Every other column is a gill's shadowed fold; it breaks now and then where two gills merge.
            if math.sin(y / 2.6 + phase[x]) > -0.85:
                tone[y, x] -= 1.3
    tone[:, 1::4] += 0.5
    return px.paint(np.clip(tone, 0, 6), ramp)


def stem(seed):
    """Ribbed giant mushroom stem: pale vertical ribs, each lit on its left, with a few darker flecks."""
    rng = np.random.default_rng(seed)
    tone = np.full((N, N), 4.0)
    for x in range(N):
        p = x % 3
        tone[:, x] += 0.9 if p == 0 else -1.1 if p == 2 else 0
    tone += 0.35 * px.fbm(rng, (2, 4, 8), (0.4, 0.4, 0.2)) * 2
    tone = px.speckle(rng, tone, 0.04, 1.0, 0.0)
    return px.paint(np.clip(tone, 0, 6), STEM)


def stem_inside(seed):
    rng = np.random.default_rng(seed)
    return px.paint(px.rings(rng, base=4.4, rim=1, step=2.6, rim_tone=2.4, wobble=0.4), STEM)


def small_shroom(glow):
    """A little mushroom (a cross model): a domed cap on a pale stem, or a cluster of three glowing caps."""
    arr = blank()
    if glow:
        caps = [(4, 9, 2), (10, 6, 3), (12, 11, 2)]
        for cx, top, r in caps:
            for y in range(top + r, 16):
                put(arr, cx, y, STEM[4])
                put(arr, cx + 1, y, STEM[2])
            for dy in range(r + 1):
                w = r + 1 - dy
                for dx in range(-w, w + 1):
                    tone = 3 + dy * 0.8 + (0.8 if dx < 0 else 0)
                    put(arr, cx + dx, top + r - dy, GLOW[int(min(6, tone))])
            put(arr, cx - 1, top + 1, GLOW_SPOT[1])
            put(arr, cx + 1, top + r - 1, GLOW_SPOT[0])
    else:
        for y in range(8, 16):
            put(arr, 7, y, STEM[5] if y % 3 else STEM[4])
            put(arr, 8, y, STEM[3])
        for dy in range(5):
            w = [6, 6, 5, 4, 2][dy]
            for dx in range(-w + 1, w + 1):
                tone = 2.4 + dy * 0.7 + (0.9 if dx < 0 else -0.2)
                put(arr, 7 + dx, 8 - dy, EMBER[int(min(6, tone))])
        for x in range(2, 14):
            put(arr, x, 9, GILLS[2])
        for sx, sy in ((5, 6), (9, 5), (7, 4), (11, 7), (3, 7)):
            put(arr, sx, sy, EMBER_SPOT[6])
    return image(arr)


# ---- Plants ----------------------------------------------------------------------------------------------------------

def lupine(top):
    """A tall lupine: the bottom half a stem with fanned leaves, the top half a spike of florets paling upward."""
    arr = blank()
    rng = np.random.default_rng(13 if top else 11)
    if top:
        for y in range(2, 16):
            put(arr, 8, y, STEM_GREEN[2])
        for y in range(1, 16):
            t = y / 15
            width = 1 + int(t * 3)
            for dx in range(-width, width + 1):
                if abs(dx) == width and rng.random() < 0.4:
                    continue
                if (y + dx) % 2 == 0 or abs(dx) < 2:
                    tone = 6 - t * 4.5 + (0.6 if dx < 0 else -0.3)
                    put(arr, 8 + dx, y, LUPINE[int(np.clip(round(tone), 0, 6))])
        put(arr, 8, 0, LUPINE[6])
    else:
        for y in range(0, 16):
            put(arr, 8, y, STEM_GREEN[2 if y % 4 else 3])
        for base_y, side in ((5, -1), (9, 1), (12, -1), (3, 1)):
            for k in range(5):
                for spread in (-1, 0, 1):
                    x = 8 + side * (k + 1)
                    y = base_y - k // 2 + spread * (k // 2)
                    put(arr, x, y, STEM_GREEN[4 if spread < 0 else 3 if spread == 0 else 2])
    return image(arr)


def ivy(tip):
    """Trailing ivy: a wandering stem with pairs of small lit leaves, tapering at the tip."""
    arr = blank()
    rng = np.random.default_rng(23 if tip else 21)
    for cx in (4, 11):
        x = cx
        bottom = 11 if tip else N
        for y in range(0, bottom):
            put(arr, x, y, IVY[1])
            if y % 3 == 1:
                side = 1 if (y // 3) % 2 else -1
                put(arr, x + side, y, IVY[4])
                put(arr, x + 2 * side, y, IVY[3])
                put(arr, x + side, y + 1, IVY[2])
                put(arr, x + 2 * side, y - 1, IVY[5])
            if rng.random() < 0.25:
                x += int(rng.choice([-1, 1]))
                x = max(cx - 2, min(cx + 2, x))
    return image(arr)


def bloomcoral(colour):
    """A branching coral bush: forked branches lit on their left, with round tips."""
    arr = blank()
    ramp = CORAL[colour]
    rng = np.random.default_rng({"pink": 3, "violet": 4, "white": 5}[colour])

    def branch(x, y, dx, length, depth):
        for k in range(length):
            put(arr, x, y, ramp[3])
            put(arr, x - 1, y, ramp[5]) if k % 2 == 0 else None
            y -= 1
            if rng.random() < 0.45:
                x += dx
        put(arr, x, y, ramp[6])
        put(arr, x + 1, y, ramp[4])
        if depth > 0:
            branch(x, y, -1, int(rng.integers(2, 4)), depth - 1)
            branch(x, y, 1, int(rng.integers(2, 4)), depth - 1)

    branch(8, 15, 0, 5, 2)
    branch(5, 15, -1, 4, 1)
    branch(11, 15, 1, 4, 1)
    return image(arr)


def main():
    made = {}
    made["elder_log"] = log_side(3, ELDER_BARK)
    made["elder_log_top"] = log_top(5, ELDER_HEART, ELDER_BARK)
    made["elder_log_glow"] = elder_glow(7)
    made["elder_leaves"] = leaves(9, [ELDER_LEAF], [1.0], glints=[(150, 240, 220)])
    made["elder_leaves_1"] = leaves(10, [ELDER_LEAF], [1.0], glints=[(150, 240, 220)])
    made["duskwood_log"] = log_side(13, DUSK_BARK)
    made["duskwood_log_top"] = log_top(15, DUSK_HEART, DUSK_BARK)
    made["duskwood_leaves"] = leaves(17, [DUSK_LEAF, DUSK_LEAF_PINK], [0.7, 0.3])
    made["duskwood_leaves_1"] = leaves(18, [DUSK_LEAF, DUSK_LEAF_PINK], [0.7, 0.3])
    made["spirepine_needles"] = needles(19)
    made["spirepine_needles_1"] = needles(20)
    made["pale_blossom_leaves"] = leaves(21, [BLOSSOM, BLOSSOM_PINK], [0.7, 0.3], count=30, radius=(1.4, 2.4))
    made["pale_blossom_leaves_1"] = leaves(22, [BLOSSOM, BLOSSOM_PINK], [0.7, 0.3], count=30, radius=(1.4, 2.4))
    made["wisteria"] = wisteria(False)
    made["wisteria_tip"] = wisteria(True)
    made["ember_cap"] = cap(31, EMBER, EMBER_SPOT, spot_count=5)
    made["ember_cap_inside"] = gills(32, GILLS)
    made["parasol_cap"] = cap(33, PARASOL, scales=True)
    made["parasol_cap_inside"] = gills(34, GILLS)
    made["glowcap"] = cap(35, GLOW, glow_spots=GLOW_SPOT, spot_count=4)
    made["glowcap_inside"] = gills(36, GILLS_GLOW)
    made["bell_cap"] = cap(37, BELL, ridges=True)
    made["bell_cap_inside"] = gills(38, GILLS)
    made["giant_mushroom_stem"] = stem(39)
    made["giant_mushroom_stem_inside"] = stem_inside(40)
    made["ember_shroom"] = small_shroom(False)
    made["glow_shroom"] = small_shroom(True)
    made["skylupine_top"] = lupine(True)
    made["skylupine_bottom"] = lupine(False)
    made["hanging_ivy"] = ivy(False)
    made["hanging_ivy_tip"] = ivy(True)
    for colour in ("pink", "violet", "white"):
        made[f"bloomcoral_{colour}"] = bloomcoral(colour)

    BLOCKS.mkdir(parents=True, exist_ok=True)
    for name, img in made.items():
        img.save(BLOCKS / f"{name}.png")
    PREVIEW.mkdir(parents=True, exist_ok=True)
    px.sheet(list(made.values()), list(made.keys()), scale=4, tile=3, cols=7).save(PREVIEW / "halcyon_flora_blocks.png")
    print(f"wrote {len(made)} textures and build/previews/halcyon_flora_blocks.png")


if __name__ == "__main__":
    main()
