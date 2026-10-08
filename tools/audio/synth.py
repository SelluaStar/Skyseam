"""Synthesized first-pass sounds (spec section 21, "How sound effects are covered"). [FIRST PASS]

Builds the hero sounds from simple recipes and writes mono Ogg Vorbis files (44.1 kHz) with the soundfile package
into src/main/resources/assets/skyseam/sounds/<group>/:
  plucked strings for thread snaps, inharmonic bell tones for chimes, filtered noise sweeps for cracks and whooshes,
  layered sine hums for loops (loops are built from whole cycles and crossfaded, so they join cleanly).

Deterministic. Run with tools/.venv/Scripts/python.exe tools/audio/synth.py [group ...]; then check the result with
tools/audio/check.py. Every file here is a stand-in for the author's final sound design (TODO-MANUAL.md).
"""
import sys
from pathlib import Path

import numpy as np
import soundfile as sf

RATE = 44100
ROOT = Path(__file__).resolve().parents[2]
SOUNDS = ROOT / "src" / "main" / "resources" / "assets" / "skyseam" / "sounds"
PEAK = 0.8  # about -2 dBFS, with headroom for the Vorbis encoder

rng = np.random.default_rng(20261008)


def t_axis(seconds):
    return np.arange(int(seconds * RATE)) / RATE


def env_exp(t, decay, attack=0.002):
    """Fast attack, exponential decay (decay = time to fall to 1/e)."""
    return np.minimum(1, t / attack) * np.exp(-t / decay)


def bell(t, f0, ratios=(1, 2.76, 5.4, 8.93), amps=(1, 0.5, 0.25, 0.12), decay=1.2, detune=1.002):
    """An inharmonic bell or glass tone: a few partials, higher ones dying faster, each with a slow beat."""
    out = np.zeros_like(t)
    for ratio, amp in zip(ratios, amps):
        f = f0 * ratio
        d = decay / (1 + 0.6 * (ratio - 1))
        beat = 0.5 * (np.sin(2 * np.pi * f * t) + np.sin(2 * np.pi * f * detune * t))
        out += amp * beat * env_exp(t, d)
    return out


def pluck(f0, seconds, brightness=0.5):
    """Karplus-Strong plucked string: a burst of noise in a delay line with a softening loop filter."""
    n = int(seconds * RATE)
    period = max(2, int(round(RATE / f0)))
    buf = rng.uniform(-1, 1, period)
    out = np.zeros(n)
    blend = 0.5 + 0.5 * brightness
    for i in range(n):
        j = i % period
        out[i] = buf[j]
        buf[j] = 0.996 * (blend * buf[j] + (1 - blend) * buf[(j + 1) % period])
    return out


def one_pole_lowpass(x, cutoff):
    """Lowpass with a cutoff that may change per sample (array or scalar, Hz)."""
    cutoff = np.broadcast_to(np.asarray(cutoff, dtype=np.float64), x.shape)
    a = np.exp(-2 * np.pi * cutoff / RATE)
    y = np.zeros_like(x)
    prev = 0.0
    for i in range(len(x)):
        prev = (1 - a[i]) * x[i] + a[i] * prev
        y[i] = prev
    return y


def svf(x, centre, q=1.5, mode="band"):
    """Chamberlin state-variable filter with a centre that may change per sample (array or scalar, Hz)."""
    centre = np.broadcast_to(np.asarray(centre, dtype=np.float64), x.shape)
    f = 2 * np.sin(np.pi * np.minimum(centre, RATE / 7) / RATE)
    y = np.zeros_like(x)
    low = band = 0.0
    for i in range(len(x)):
        low += f[i] * band
        high = x[i] - low - band / q
        band += f[i] * high
        y[i] = band if mode == "band" else low
    return y


def bandpass_noise(seconds, centre, q=1.5):
    """Noise through a resonant band-pass around `centre` (array or scalar, Hz)."""
    return svf(rng.normal(0, 1, int(seconds * RATE)), centre, q)


def finish(x, fade_in=0.002, fade_out=0.03):
    x = x - np.mean(x)
    n_in, n_out = int(fade_in * RATE), int(fade_out * RATE)
    if n_in:
        x[:n_in] *= np.linspace(0, 1, n_in)
    if n_out:
        x[-n_out:] *= np.linspace(1, 0, n_out)
    return x / np.max(np.abs(x)) * PEAK


def write(group, name, x, loop=False):
    folder = SOUNDS / group
    folder.mkdir(parents=True, exist_ok=True)
    path = folder / f"{name}.ogg"
    sf.write(path, x.astype(np.float32), RATE, format="OGG", subtype="VORBIS")
    print(f"{path.relative_to(ROOT)}  {len(x) / RATE:.2f} s{'  loop' if loop else ''}")


# ---- The Seam (spec section 20: 11 sounds) ------------------------------------------------------------------------

def seam_hairline():
    """Beat 2: a thin glass 'tink', with a second softer tink just after."""
    t = t_axis(1.4)
    x = bell(t, 2650, decay=0.5) + 0.35 * np.roll(bell(t, 3170, decay=0.35), int(0.07 * RATE))
    click = rng.normal(0, 1, len(t)) * env_exp(t, 0.0015) * 0.6
    return finish(x + one_pole_lowpass(click, 9000))


def seam_crack():
    """Beat 3: cloth tearing (dense noise crackle that speeds up and brightens) over a low thump, with a glass chime."""
    seconds = 2.2
    t = t_axis(seconds)
    # Tearing: grains of noise, sparse then dense, through a lowpass that opens up.
    grains = np.zeros(len(t))
    time = 0.0
    while time < 1.7:
        start = int(time * RATE)
        length = int(rng.uniform(0.004, 0.018) * RATE)
        end = min(len(t), start + length)
        grains[start:end] += rng.normal(0, 1, end - start) * np.hanning(end - start) * rng.uniform(0.4, 1.0)
        time += rng.uniform(0.004, 0.03) * (1.2 - time / 1.7)
    tear = one_pole_lowpass(grains, 1500 + 4500 * np.clip(t / 1.7, 0, 1)) * (1 - np.clip((t - 1.5) / 0.6, 0, 1))
    thump = np.sin(2 * np.pi * (58 * t - 10 * t * t)) * env_exp(t, 0.18) * 0.9
    chime = np.roll(bell(t, 1760, decay=0.9), int(0.35 * RATE)) * 0.45
    chime[: int(0.35 * RATE)] = 0
    return finish(tear * 1.4 + thump + chime, fade_out=0.15)


# Thread snaps: a harp pluck in a rising pentatonic run (C5 D5 E5 G5 A5), with a tiny snap transient.
THREAD_PITCHES = (523.25, 587.33, 659.26, 783.99, 880.0)


def seam_thread_snap(k):
    seconds = 1.8
    t = t_axis(seconds)
    string = pluck(THREAD_PITCHES[k], seconds, brightness=0.35)
    octave = pluck(THREAD_PITCHES[k] * 2, seconds, brightness=0.2) * 0.25
    snap = rng.normal(0, 1, len(t)) * env_exp(t, 0.003)
    snap = snap - one_pole_lowpass(snap, 2500)
    return finish(string + octave + 0.5 * snap, fade_out=0.2)


def seam_hum():
    """Beat 6: the open Seam's low hum, an 8 second seamless loop of layered sines and soft air."""
    seconds = 8.0
    t = t_axis(seconds)
    x = np.zeros(len(t))
    # Whole numbers of cycles in the loop, so every layer ends exactly where it began.
    for cycles, amp in ((440, 1.0), (660, 0.55), (880, 0.4), (1320, 0.22), (1760, 0.12), (2640, 0.06)):
        f = cycles / seconds
        swell = 1 + 0.25 * np.sin(2 * np.pi * (2 + cycles % 3) * t / seconds + cycles)
        x += amp * np.sin(2 * np.pi * f * t) * swell
    # Air: filtered noise, made a little longer and crossfaded over the loop point.
    fade = int(0.5 * RATE)
    air = svf(rng.normal(0, 1, len(t) + fade), 300, q=0.7, mode="low") * 0.25
    head = air[:fade].copy()
    air = air[fade:]
    ramp = np.linspace(0, 1, fade)
    air[-fade:] = air[-fade:] * (1 - ramp) + head * ramp
    x = x + air
    x = x - np.mean(x)
    return x / np.max(np.abs(x)) * PEAK * 0.9


def seam_ring_pulse():
    """Beat 6, every 6 s: a soft low 'whum' that falls in pitch, under a glass-harmonica shimmer."""
    seconds = 3.2
    t = t_axis(seconds)
    whum = np.sin(2 * np.pi * (200 * t - 30 * t * t)) * np.minimum(1, t / 0.05) * np.exp(-t / 0.9)
    shimmer_env = np.minimum(1, t / 0.4) * np.exp(-np.maximum(0, t - 0.4) / 1.1)
    shimmer = (np.sin(2 * np.pi * 880 * t) + 0.5 * np.sin(2 * np.pi * 1318.5 * t) + 0.3 * np.sin(2 * np.pi * 1760 * t * 1.003)) * shimmer_env
    return finish(whum + 0.35 * shimmer, fade_out=0.3)


def seam_crossing():
    """Beat 7: the crossing whoosh, a band of noise sweeping up and down with a rising shimmer under the flash."""
    seconds = 2.4
    t = t_axis(seconds)
    shape = np.sin(np.pi * np.clip(t / seconds, 0, 1)) ** 1.5
    centre = 300 + 2700 * np.sin(np.pi * np.clip(t / 1.6, 0, 1)) ** 2
    rush = bandpass_noise(seconds, centre, 2.5) * shape
    rise = np.sin(2 * np.pi * (440 * t + 300 * t * t)) * np.sin(np.pi * np.clip(t / 1.2, 0, 1)) ** 2 * 0.2
    return finish(rush * 3 + rise, fade_out=0.2)


def seam_mend():
    """Beat 8: the closing chime, three bell strikes falling (A5, E5, A4) over a soft zip as the crack shuts."""
    seconds = 3.6
    t = t_axis(seconds)
    x = np.zeros(len(t))
    for start, f0, amp in ((0.0, 880.0, 0.7), (0.14, 659.26, 0.8), (0.3, 440.0, 1.0)):
        shifted = bell(t, f0, decay=1.6) * amp
        n = int(start * RATE)
        x[n:] += shifted[: len(t) - n]
    zip_env = np.clip(t / 0.05, 0, 1) * np.exp(-t / 0.12)
    zipper = bandpass_noise(seconds, 4000 - 3000 * np.clip(t / 0.3, 0, 1), 3.0) * zip_env
    return finish(x + zipper * 1.5, fade_out=0.4)


GROUPS = {
    "seam": lambda: (
        [("hairline", seam_hairline(), False), ("crack", seam_crack(), False)]
        + [(f"thread_snap_{k + 1}", seam_thread_snap(k), False) for k in range(5)]
        + [("hum", seam_hum(), True), ("ring_pulse", seam_ring_pulse(), False),
           ("crossing", seam_crossing(), False), ("mend", seam_mend(), False)]
    ),
}


def main(argv):
    groups = argv or list(GROUPS)
    for group in groups:
        for name, data, loop in GROUPS[group]():
            write(group, name, data, loop)


if __name__ == "__main__":
    main(sys.argv[1:])
