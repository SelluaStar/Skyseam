"""Checks Skyseam's sound files and sound definitions (spec section 21, "Checks Claude Code can run").

For every .ogg under assets/skyseam/sounds/ except music: mono, 44.1 kHz, a sane length (0.05 to 30 s), peak below
full scale, no DC offset, and for loops a small jump where the end joins the start. Writes one spectrogram PNG per file
to build/audio-check/ to look at.

For assets/skyseam/sounds.json: every entry has a subtitle that exists in en_us.json, and every file an entry names
exists. (That every registered sound event has an entry is checked by a GameTest, which can see the registry.)

These checks catch broken files, not bad taste. Exit code 1 if anything fails.
Run with tools/.venv/Scripts/python.exe tools/audio/check.py
"""
import json
import sys
from pathlib import Path

import numpy as np
import soundfile as sf
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "skyseam"
SOUNDS = ASSETS / "sounds"
OUT = ROOT / "build" / "audio-check"
RATE = 44100
# Files that loop. Their join must be smooth.
LOOPS = {"seam/hum", "aperture/charge"}


def spectrogram(data, path):
    window = 1024
    hop = 256
    frames = max(1, (len(data) - window) // hop)
    hann = np.hanning(window)
    columns = []
    for f in range(frames):
        chunk = data[f * hop: f * hop + window]
        if len(chunk) < window:
            chunk = np.pad(chunk, (0, window - len(chunk)))
        columns.append(np.abs(np.fft.rfft(chunk * hann)))
    spec = np.array(columns).T  # frequency x time
    db = 20 * np.log10(spec + 1e-9)
    db = np.clip((db - db.max() + 80) / 80, 0, 1)
    img = (np.flipud(db) * 255).astype(np.uint8)
    # Log-ish frequency axis: keep the bottom 12 kHz, stretch the low end.
    keep = int(len(img) * (1 - 12000 / (RATE / 2)))
    img = img[keep:]
    rows = np.linspace(0, 1, 256) ** 2
    pick = (len(img) - 1 - (rows * (len(img) - 1))).astype(int)[::-1]
    img = img[pick]
    picture = Image.fromarray(img, "L").resize((min(1200, max(300, frames)), 256))
    colour = Image.merge("RGB", (picture, picture.point(lambda v: int(v * 0.8)), picture.point(lambda v: min(255, int(v * 1.2)))))
    colour.save(path)


def check_file(path, problems):
    rel = path.relative_to(SOUNDS).with_suffix("").as_posix()
    data, rate = sf.read(path, always_2d=True)
    channels = data.shape[1]
    mono = data.mean(axis=1)
    seconds = len(mono) / rate
    peak = float(np.max(np.abs(mono))) if len(mono) else 0.0
    dc = float(np.mean(mono)) if len(mono) else 0.0
    notes = []
    if channels != 1:
        problems.append(f"{rel}: {channels} channels, positional sounds must be mono")
    if rate != RATE:
        problems.append(f"{rel}: sample rate {rate}, expected {RATE}")
    if not 0.05 <= seconds <= 30:
        problems.append(f"{rel}: length {seconds:.2f} s is outside 0.05 to 30 s")
    if peak >= 0.99:
        problems.append(f"{rel}: peak {peak:.3f} is at full scale (clipping)")
    if peak < 0.05:
        problems.append(f"{rel}: peak {peak:.3f}, nearly silent")
    if abs(dc) > 0.01:
        problems.append(f"{rel}: DC offset {dc:.4f}")
    if rel in LOOPS:
        n = int(0.01 * rate)
        jump = abs(mono[-1] - mono[0]) / max(peak, 1e-9)
        rms_end = float(np.sqrt(np.mean(mono[-n:] ** 2)))
        rms_start = float(np.sqrt(np.mean(mono[:n] ** 2)))
        level_step = abs(rms_end - rms_start) / max(peak, 1e-9)
        notes.append(f"loop join: sample jump {jump:.3f}, level step {level_step:.3f}")
        if jump > 0.1 or level_step > 0.1:
            problems.append(f"{rel}: the loop join jumps (sample {jump:.3f}, level {level_step:.3f} of peak)")
    OUT.mkdir(parents=True, exist_ok=True)
    spectrogram(mono, OUT / (rel.replace("/", "__") + ".png"))
    print(f"  {rel:28s} {channels} ch {rate} Hz {seconds:5.2f} s  peak {peak:.3f}  dc {dc:+.4f}  {'; '.join(notes)}")


def check_definitions(problems):
    definitions = json.loads((ASSETS / "sounds.json").read_text(encoding="utf-8"))
    lang = json.loads((ASSETS / "lang" / "en_us.json").read_text(encoding="utf-8"))
    for event, entry in definitions.items():
        subtitle = entry.get("subtitle")
        if not subtitle:
            problems.append(f"sounds.json {event}: no subtitle")
        elif subtitle not in lang:
            problems.append(f"sounds.json {event}: subtitle {subtitle} is missing from en_us.json")
        for sound in entry.get("sounds", []):
            if isinstance(sound, str):
                sound = {"name": sound}
            if sound.get("type") == "event":
                continue
            namespace, _, name = sound["name"].partition(":")
            if namespace != "skyseam":
                continue
            if not (SOUNDS / f"{name}.ogg").exists():
                problems.append(f"sounds.json {event}: names {sound['name']}, but sounds/{name}.ogg does not exist")
    print(f"  sounds.json: {len(definitions)} events checked against files and en_us.json")


def main():
    problems = []
    files = sorted(p for p in SOUNDS.rglob("*.ogg") if p.relative_to(SOUNDS).parts[0] != "music")
    print(f"Checking {len(files)} sound files")
    for path in files:
        check_file(path, problems)
    check_definitions(problems)
    print(f"Spectrograms: {OUT.relative_to(ROOT)}")
    if problems:
        print("PROBLEMS:")
        for p in problems:
            print("  " + p)
        return 1
    print("All sound checks passed")
    return 0


if __name__ == "__main__":
    sys.exit(main())
