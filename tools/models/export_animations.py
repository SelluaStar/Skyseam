"""Writes a GeckoLib .animation.json from the clips in a .bbmodel (docs/DECISIONS.md K9).

The headless Blockbench server has no animation exporter, so this reads the clips' keyframes and writes the Bedrock
animation format GeckoLib 4 reads: one entry per clip, keyed by the clip's name, with each bone's rotation, position
and scale keyframes. Rotation values are written as Blockbench stores them for GeckoLib models (the same numbers its
own exporter writes); catmullrom keys keep their lerp mode.

    tools/.venv/Scripts/python.exe tools/models/export_animations.py art/models/<name>.bbmodel <out .animation.json>
"""
import json
import sys
from pathlib import Path


def number(value):
    """Keyframe values are stored as strings; plain numbers become numbers, anything else stays Molang."""
    try:
        f = float(value)
    except (TypeError, ValueError):
        return value
    return int(f) if f == int(f) else f


def time_key(t):
    return f"{float(t):.4f}".rstrip("0").rstrip(".") if float(t) != int(float(t)) else f"{float(t):.1f}"


def export(model_path, out_path):
    model = json.loads(Path(model_path).read_text(encoding="utf-8"))
    animations = {}
    for clip in model.get("animations", []):
        bones = {}
        for animator in clip.get("animators", {}).values():
            if animator.get("type", "bone") != "bone":
                continue
            channels = {}
            for key in sorted(animator.get("keyframes", []), key=lambda k: float(k["time"])):
                point = key["data_points"][0]
                vector = [number(point.get("x", 0)), number(point.get("y", 0)), number(point.get("z", 0))]
                entry = {"vector": vector}
                if key.get("interpolation") == "catmullrom":
                    entry = {"post": vector, "lerp_mode": "catmullrom"}
                elif key.get("interpolation") == "step":
                    entry = {"pre": vector, "post": vector}
                channels.setdefault(key["channel"], {})[time_key(key["time"])] = entry
            if channels:
                bones[animator["name"]] = channels
        entry = {"animation_length": float(clip["length"]), "bones": bones}
        if clip.get("loop") == "loop":
            entry["loop"] = True
        elif clip.get("loop") == "hold":
            entry["loop"] = "hold_on_last_frame"
        animations[clip["name"]] = entry
    out = {"format_version": "1.8.0", "animations": animations, "geckolib_format_version": 2}
    Path(out_path).parent.mkdir(parents=True, exist_ok=True)
    Path(out_path).write_text(json.dumps(out, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {out_path}: {', '.join(animations)}")


if __name__ == "__main__":
    export(sys.argv[1], sys.argv[2])
