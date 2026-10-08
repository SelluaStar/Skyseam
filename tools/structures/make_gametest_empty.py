"""Write the GameTest template data/skyseam/structure/gametest/empty.nbt.

A 7 x 6 x 7 box: one floor layer of calcite, air above. GameTests that need open space use it
(template "gametest/empty"). It is test scaffolding, not a world structure, so it lives in the gametest/
subfolder that the structure checks skip.

Run: tools/.venv/Scripts/python.exe tools/structures/make_gametest_empty.py
"""
from pathlib import Path

import nbtlib
from nbtlib import Compound, Int, List, String

DATA_VERSION = 3955  # Minecraft 1.21.1
SIZE_X, SIZE_Y, SIZE_Z = 7, 6, 7
FLOOR_BLOCK = "minecraft:calcite"

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/data/skyseam/structure/gametest/empty.nbt"


def build():
    blocks = [
        Compound({"pos": List[Int]([Int(x), Int(0), Int(z)]), "state": Int(0)})
        for x in range(SIZE_X)
        for z in range(SIZE_Z)
    ]
    return nbtlib.File(
        {
            "DataVersion": Int(DATA_VERSION),
            "size": List[Int]([Int(SIZE_X), Int(SIZE_Y), Int(SIZE_Z)]),
            "palette": List[Compound]([Compound({"Name": String(FLOOR_BLOCK)})]),
            "blocks": List[Compound](blocks),
            "entities": List[Compound]([]),
        },
        gzipped=True,
    )


if __name__ == "__main__":
    OUT.parent.mkdir(parents=True, exist_ok=True)
    build().save(OUT)
    print(f"wrote {OUT} ({SIZE_X}x{SIZE_Y}x{SIZE_Z}, {SIZE_X * SIZE_Z} floor blocks)")
