"""The Tide Vane's block models and blockstate (spec section 20, "Tide Vane: one pose per Tide"; docs/DECISIONS.md K111).

A brass weathervane: a round foot plate, a post with compass arms, a faceted gem on top in the colour of the Tide it
shows, and an arrow over the gem swung to a different angle for each Tide (Calm straight, Prismfall and Gale to one
side, Bloom and Stillness to the other). It shows the Tide running, or the next once it is less than ten minutes off.

Writes, under src/main/resources/assets/skyseam/:
  models/block/tide_vane_<tide>.json   one model for each pose
  models/item/tide_vane.json           the Calm pose
  blockstates/tide_vane.json           facing x showing
Deterministic.
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "skyseam"
TIDES = {"calm": 0, "prismfall": 22.5, "gale": 45, "bloom": -22.5, "stillness": -45}


def box(frm, to, texture="#brass", rotation=None, uv=None):
    faces = {}
    for face in ("north", "south", "east", "west", "up", "down"):
        f = {"texture": texture}
        if uv:
            f["uv"] = uv
        faces[face] = f
    element = {"from": frm, "to": to, "faces": faces}
    if rotation is not None:
        element["rotation"] = {"angle": rotation, "axis": "y", "origin": [8, 16, 8]}
    return element


def model(tide, angle):
    elements = [
        # The foot: a plate, and a step on it.
        box([3, 0, 3], [13, 1, 13]),
        box([5, 1, 5], [11, 2, 11]),
        # The post.
        box([7.25, 2, 7.25], [8.75, 15, 8.75]),
        # The compass arms, and their tips.
        box([3, 10, 7.5], [13, 11, 8.5]),
        box([7.5, 10, 3], [8.5, 11, 13]),
        box([2.5, 9.5, 7.25], [3.5, 11.5, 8.75]),
        box([12.5, 9.5, 7.25], [13.5, 11.5, 8.75]),
        box([7.25, 9.5, 2.5], [8.75, 11.5, 3.5]),
        box([7.25, 9.5, 12.5], [8.75, 11.5, 13.5]),
        # The gem.
        box([6.5, 13, 6.5], [9.5, 16, 9.5], texture="#gem"),
    ]
    rot = angle if angle != 0 else None
    # The arrow, swung on the post's top: a shaft, a head and a tail fin.
    arrow = [
        box([2, 16.25, 7.6], [14, 17, 8.4], rotation=rot),
        box([12.5, 15.75, 7.4], [15, 17.5, 8.6], rotation=rot),
        box([1, 15.5, 7.75], [4.5, 19, 8.25], rotation=rot),
    ]
    return {
        "parent": "block/block",
        "ambientocclusion": False,
        "textures": {"particle": "skyseam:block/tide_vane_brass", "brass": "skyseam:block/tide_vane_brass",
                     "gem": f"skyseam:block/tide_vane_{tide}"},
        "elements": elements + arrow,
    }


def main():
    for tide, angle in TIDES.items():
        path = ASSETS / "models" / "block" / f"tide_vane_{tide}.json"
        path.write_text(json.dumps(model(tide, angle), indent=2) + "\n", encoding="utf-8")
        print(path.relative_to(ROOT))
    item = ASSETS / "models" / "item" / "tide_vane.json"
    item.parent.mkdir(parents=True, exist_ok=True)
    item.write_text(json.dumps({"parent": "skyseam:block/tide_vane_calm"}, indent=2) + "\n", encoding="utf-8")
    variants = {}
    for facing, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for tide in TIDES:
            variant = {"model": f"skyseam:block/tide_vane_{tide}"}
            if y:
                variant["y"] = y
            variants[f"facing={facing},showing={tide}"] = variant
    blockstate = ASSETS / "blockstates" / "tide_vane.json"
    blockstate.write_text(json.dumps({"variants": variants}, indent=2) + "\n", encoding="utf-8")
    print(item.relative_to(ROOT), blockstate.relative_to(ROOT))


if __name__ == "__main__":
    main()
