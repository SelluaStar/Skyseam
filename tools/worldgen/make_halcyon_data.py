"""Writes the Halcyon's data files (spec section 7). [CC] data, [FIRST PASS] colours.

  data/skyseam/worldgen/biome/<biome>.json        the seven biomes: sky, fog, water and grass colours, no
                                                  vanilla features (the generator decorates) and no spawns (M5)
  data/skyseam/dimension_type/halcyon.json        Y 0 to 512, its own sky effects; no fixed time, since the
                                                  Lantern-Sun's clock drives the day there (LevelTimeMixin)
  data/skyseam/dimension/halcyon.json             the Halcyon generator and biome source
  data/skyseam/dimension_physics/halcyon.json     Sable's physics for the Halcyon: lighter gravity, thicker air

Colours follow the author's reference images (tools/reference/palette.json): lilac and violet grass and leaves, deep
blue water, set for daylight. Tune them here and run again:
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
    "the_spindle": ("#4065A3", "#C8C4E8", "#2F5CC4", "#1B2F6E", "#9474D8", "#9A62E6"),
    "the_hush": ("#1E1C4A", "#4A4290", "#2A3C96", "#141E50", "#6E4FB0", "#7A44D0"),
    "cirrus_reefs": ("#5A80C0", "#E2DCF2", "#3A70D0", "#22447F", "#C0A6EA", "#D49CE8"),
    "petalwash_meadows": ("#4C70B4", "#D4CCEE", "#3064C8", "#1E3A78", "#A584E0", "#A066E4"),
    "mirror_shoals": ("#5A84C4", "#DCE0F0", "#3C8ADA", "#2A5A9A", "#B4A2DC", "#A88CD8"),
    "wreckfields": ("#56709E", "#C8C0D4", "#2E5596", "#1C3060", "#9A90A8", "#8C7A9C"),
    # The outer lands (round two), from the author's images 8 to 11: sunlit tan cliffs over turquoise lagoons, green
    # mushroom hills, and the misty blue Elderwood.
    "sunreach_cliffs": ("#5C9CE0", "#D8E4EE", "#2FC4C6", "#1A6E78", "#86B65C", "#5E9E4A"),
    "capwood": ("#7EB0E2", "#DCE6EE", "#3A9AD0", "#1E5470", "#8EAA5C", "#6E9A48"),
    "elderwood": ("#5A6A96", "#9CA6C0", "#2E5E8E", "#18304C", "#5E7E42", "#3E7050"),
    "underbloom": ("#2A2A5A", "#4E4890", "#2F5CC4", "#1B2F6E", "#8466CC", "#8A58DC"),
}


# How often each biome gives off wisps (fireflies and drifting lanterns) at night; the particle hides itself by day.
WISPS = {"petalwash_meadows": 0.004, "the_hush": 0.006, "the_spindle": 0.003, "cirrus_reefs": 0.002, "capwood": 0.005,
         "elderwood": 0.007, "sunreach_cliffs": 0.002, "underbloom": 0.008}


def biome(colours, name=None):
    sky, fog, water, water_fog, grass, foliage = (rgb(c) for c in colours)
    effects = {
        "sky_color": sky,
        "fog_color": fog,
        "water_color": water,
        "water_fog_color": water_fog,
        "grass_color": grass,
        "foliage_color": foliage,
    }
    if name in WISPS:
        effects["particle"] = {"options": {"type": "skyseam:wisp"}, "probability": WISPS[name]}
    return {
        "has_precipitation": False,
        "temperature": 0.7,
        "downfall": 0.4,
        "effects": effects,
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
        write(DATA / "worldgen" / "biome" / f"{name}.json", biome(colours, name))
    write(DATA / "dimension_type" / "halcyon.json", DIMENSION_TYPE)
    write(DATA / "dimension" / "halcyon.json", DIMENSION)
    write(DATA / "dimension_physics" / "halcyon.json", PHYSICS)


if __name__ == "__main__":
    main()
