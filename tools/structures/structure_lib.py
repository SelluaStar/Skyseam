"""Shared helpers for the structure generators (spec section 14, "How the structures get built").

A Structure is a box of blocks addressed by (x, y, z), written as a vanilla structure file (.nbt) that the game and a
structure block both read. Data markers are structure blocks in data mode whose text names what goes there,
"name" or "name:facing" (see docs/STRUCTURE-MARKERS.md); the game swaps them for the real block after placing.

Only the blocks that are set are written. Unset spots keep whatever the world has there, so a structure sits on its
island instead of carving a box out of it.

Deterministic: generators seed their randomness, so the same parameters always write the same file.
"""
import gzip
import io
from pathlib import Path

import nbtlib
from nbtlib import Byte, Compound, Int, List, String

DATA_VERSION = 3955  # Minecraft 1.21.1
STRUCTURE_BLOCK = "minecraft:structure_block"
MAX_SIDE = 48  # the largest box one structure block can save, so the author can hand-build a replacement


class Structure:
    def __init__(self, size_x, size_y, size_z):
        if max(size_x, size_y, size_z) > MAX_SIDE:
            raise ValueError(f"{size_x}x{size_y}x{size_z} is over {MAX_SIDE} on a side")
        self.size = (size_x, size_y, size_z)
        self.blocks = {}

    def inside(self, x, y, z):
        return 0 <= x < self.size[0] and 0 <= y < self.size[1] and 0 <= z < self.size[2]

    def set(self, x, y, z, name, **properties):
        """Sets one block. Properties are block state properties, as strings or numbers or booleans."""
        if self.inside(x, y, z):
            props = {key: str(value).lower() if isinstance(value, bool) else str(value) for key, value in properties.items()}
            self.blocks[(x, y, z)] = (name, tuple(sorted(props.items())), None)

    def get(self, x, y, z):
        entry = self.blocks.get((x, y, z))
        return entry[0] if entry else None

    def clear(self, x, y, z):
        self.blocks.pop((x, y, z), None)

    def fill(self, x0, y0, z0, x1, y1, z1, name, **properties):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, name, **properties)

    def marker(self, x, y, z, name, facing=None):
        """A data marker: a structure block in data mode, swapped for the real block by the game."""
        text = name if facing is None else f"{name}:{facing}"
        nbt = Compound({
            "id": String(STRUCTURE_BLOCK),
            "mode": String("DATA"),
            "metadata": String(text),
            "name": String(""),
            "author": String("skyseam"),
            "posX": Int(0), "posY": Int(1), "posZ": Int(0),
            "sizeX": Int(0), "sizeY": Int(0), "sizeZ": Int(0),
            "rotation": String("NONE"), "mirror": String("NONE"),
            "ignoreEntities": Byte(1), "powered": Byte(0), "showair": Byte(0), "showboundingbox": Byte(1),
            "integrity": nbtlib.Float(1.0), "seed": nbtlib.Long(0),
        })
        if self.inside(x, y, z):
            self.blocks[(x, y, z)] = (STRUCTURE_BLOCK, (("mode", "data"),), nbt)

    def markers(self):
        """Every marker as (name, x, y, z)."""
        found = []
        for (x, y, z), (name, _props, nbt) in sorted(self.blocks.items()):
            if name == STRUCTURE_BLOCK and nbt is not None:
                found.append((str(nbt["metadata"]).split(":")[0], x, y, z))
        return found

    def palette_names(self):
        return sorted({name for name, _props, _nbt in self.blocks.values()})

    def write(self, path):
        palette = []
        index = {}
        blocks = []
        for (x, y, z), (name, props, nbt) in sorted(self.blocks.items(), key=lambda item: (item[0][1], item[0][2], item[0][0])):
            key = (name, props)
            if key not in index:
                index[key] = len(palette)
                entry = {"Name": String(name)}
                if props:
                    entry["Properties"] = Compound({k: String(v) for k, v in props})
                palette.append(Compound(entry))
            block = {"pos": List[Int]([Int(x), Int(y), Int(z)]), "state": Int(index[key])}
            if nbt is not None:
                block["nbt"] = nbt
            blocks.append(Compound(block))
        file = nbtlib.File({
            "DataVersion": Int(DATA_VERSION),
            "size": List[Int]([Int(s) for s in self.size]),
            "palette": List[Compound](palette),
            "blocks": List[Compound](blocks),
            "entities": List[Compound]([]),
        })
        # Gzipped by hand with a fixed timestamp, so the same structure always writes the same bytes.
        raw = io.BytesIO()
        file.write(raw)
        path = Path(path)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(gzip.compress(raw.getvalue(), mtime=0))
        return len(blocks)


def load(path):
    """Reads a structure file back as {(x, y, z): name} plus its size, for previews and checks."""
    data = nbtlib.load(path)
    palette = [str(entry["Name"]) for entry in data["palette"]]
    blocks = {}
    for block in data["blocks"]:
        x, y, z = (int(v) for v in block["pos"])
        blocks[(x, y, z)] = palette[int(block["state"])]
    size = tuple(int(v) for v in data["size"])
    return size, blocks
