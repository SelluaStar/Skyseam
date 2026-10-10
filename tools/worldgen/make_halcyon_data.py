"""Writes the Halcyon's data files (spec section 7). [CC] data, [FIRST PASS] colours.

  data/skyseam/worldgen/biome/<biome>.json        the seven biomes: sky, fog, water and grass colours, no
                                                  vanilla features (the generator decorates) and no spawns (M5)
  data/skyseam/dimension_type/halcyon.json        Y 0 to 512, always day for the light engine (the Hush's dark is
                                                  drawn by the client), its own sky effects
  data/skyseam/dimension/halcyon.json             the Halcyon generator and biome source
  data/skyseam/dimension_physics/halcyon.json     Sable's physics for the Halcyon: lighter gravity, thicker air

Colours are pastel, from tools/textures/palette.py's family. Tune them here and run again:
    tools/.venv/Scripts/python.exe tools/worldgen/make_halcyon_data.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / "src" / "main" / "resources" / "data" / "skyseam"


def rgb(hex_string):
    return int(hex_string.lstrip("#"), 16)


# biome: (sky, fog, water, water fog, grass, foliage)
BIOMES = {
    "the_spindle": ("#C9B8F0", "#FFF0E0", "#A8E4E2", "#9CCED6", "#B4E0A8", "#A8D8A0"),
    "the_hush": ("#5A5294", "#5E5896", "#7C8CC8", "#5A6AA8", "#8C94BC", "#8890B8"),
    "cirrus_reefs": ("#D8C8F4", "#F4ECF6", "#B4E8E6", "#A6D4DA", "#C4E4C4", "#B8DCB8"),
    "petalwash_meadows": ("#BCA8EC", "#EAD6F2", "#9EDCD8", "#8CC6CC", "#9FD49A", "#A8D8A0"),
    "mirror_shoals": ("#C4C0F0", "#DCE6F2", "#B8ECEC", "#A4D8DC", "#B0D8B0", "#A8D0A8"),
    "wreckfields": ("#C8B4D8", "#DCCCC4", "#A4D4D0", "#90BEC0", "#A8B890", "#A0B088"),
    "underbloom": ("#9A8CCC", "#9C8EC4", "#8CC4CC", "#7AB0BC", "#7FB894", "#78B090"),
}


def biome(colours):
    sky, fog, water, water_fog, grass, foliage = (rgb(c) for c in colours)
    return {
        "has_precipitation": False,
        "temperature": 0.7,
        "downfall": 0.4,
        "effects": {
            "sky_color": sky,
            "fog_color": fog,
            "water_color": water,
            "water_fog_color": water_fog,
            "grass_color": grass,
            "foliage_color": foliage,
        },
        "spawners": {},
        "spawn_costs": {},
        "carvers": {},
        "features": [],
    }


DIMENSION_TYPE = {
    "ultrawarm": False,
    "natural": False,
    "piglin_safe": False,
    "respawn_anchor_works": False,
    "bed_works": True,
    "has_raids": False,
    "has_skylight": True,
    "has_ceiling": False,
    "coordinate_scale": 1.0,
    "ambient_light": 0.1,
    "fixed_time": 6000,
    "logical_height": 512,
    "effects": "skyseam:halcyon",
    "infiniburn": "#minecraft:infiniburn_overworld",
    "min_y": 0,
    "height": 512,
    "monster_spawn_light_level": 0,
    "monster_spawn_block_light_limit": 0,
}

DIMENSION = {
    "type": "skyseam:halcyon",
    "generator": {
        "type": "skyseam:halcyon",
        "biome_source": {"type": "skyseam:halcyon", **{name: f"skyseam:{name}" for name in BIOMES}},
    },
}

# Sable 2.0.6 (docs/DEVIATIONS.md D22): gravity is a vector, pressure is base_pressure times a Bezier curve of height.
# Spec section 7: gravity -9 (the Overworld is -11), pressure 1.2 staying flat to Y 320, drag 0.07.
PHYSICS = {
    "dimension": "skyseam:halcyon",
    "priority": 0,
    "base_gravity": [0.0, -9.0, 0.0],
    "base_pressure": 1.2,
    "pressure_function": [
        {"altitude": 0.0, "value": 1.0, "slope": 0.0},
        {"altitude": 320.0, "value": 1.0, "slope": 0.0},
        {"altitude": 420.0, "value": 0.3, "slope": -0.004},
        {"altitude": 520.0, "value": 0.0, "slope": 0.0},
    ],
    "universal_drag": 0.07,
}


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
    print("wrote", path.relative_to(ROOT))


def main():
    for name, colours in BIOMES.items():
        write(DATA / "worldgen" / "biome" / f"{name}.json", biome(colours))
    write(DATA / "dimension_type" / "halcyon.json", DIMENSION_TYPE)
    write(DATA / "dimension" / "halcyon.json", DIMENSION)
    write(DATA / "dimension_physics" / "halcyon.json", PHYSICS)


if __name__ == "__main__":
    main()
