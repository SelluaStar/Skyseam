"""Synthesized first-pass sounds (spec section 21, "How sound effects are covered"). [FIRST PASS]

Builds the hero sounds from simple recipes and writes mono Ogg Vorbis files (44.1 kHz) with the soundfile package
into src/main/resources/assets/skyseam/sounds/<group>/:
  plucked strings for thread snaps, inharmonic bell tones for chimes, filtered noise sweeps for cracks and whooshes,
  layered sine hums for loops (loops are built from whole cycles and crossfaded, so they join cleanly).

Deterministic, and each sound has its own random seed, so changing one recipe never changes another file. Run with
tools/.venv/Scripts/python.exe tools/audio/synth.py [group | group/name ...]; then check the result with
tools/audio/check.py. Every file here is a stand-in for the author's final sound design (TODO-MANUAL.md).

Regenerate one sound by name after changing its recipe (e.g. `synth.py seam/crack`). The seam sounds other than
`hairline` and `crack` (and `closing/close`) were written by an earlier version that shared one random seed across all sounds; their recipes
are unchanged, but a fresh run gives them different noise detail (the thread plucks are the most sensitive to it).
"""
import sys
import zlib
from pathlib import Path

import numpy as np
import soundfile as sf

RATE = 44100
ROOT = Path(__file__).resolve().parents[2]
SOUNDS = ROOT / "src" / "main" / "resources" / "assets" / "skyseam" / "sounds"
PEAK = 0.8  # about -2 dBFS, with headroom for the Vorbis encoder

# Each sound gets its own generator, seeded from its name, so changing one recipe never changes another's file.
rng = np.random.default_rng(20261008)


def seed_for(name):
    global rng
    rng = np.random.default_rng(20261008 + zlib.crc32(name.encode("utf-8")))


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


def finish_level(x, loudest_db, fade_in=0.002, fade_out=0.03):
    """Like finish, but sets how loud the sound is: its loudest 50 ms comes out at `loudest_db` dBFS (RMS), with peaks
    kept below PEAK. Short bright sounds normalised by peak alone come out much louder than they feel they should."""
    x = x - np.mean(x)
    n_in, n_out = int(fade_in * RATE), int(fade_out * RATE)
    if n_in:
        x[:n_in] *= np.linspace(0, 1, n_in)
    if n_out:
        x[-n_out:] *= np.linspace(1, 0, n_out)
    window = int(0.05 * RATE)
    loudest = max(np.sqrt(np.mean(x[i:i + window] ** 2)) for i in range(0, max(1, len(x) - window), window // 2))
    x = x * (10 ** (loudest_db / 20) / loudest)
    peak = np.max(np.abs(x))
    return x * (PEAK / peak) if peak > PEAK else x


def echo(x, taps=((0.13, 0.3), (0.29, 0.14)), tone=3000):
    """A short, softened echo for a sense of space."""
    soft = one_pole_lowpass(x, tone)
    out = x.copy()
    for delay, gain in taps:
        n = int(delay * RATE)
        out[n:] += soft[:-n] * gain
    return out


def crackle(seconds, density, centre=1500, q=1.2):
    """Static crackle: sharp clicks at random times, most small and a few big, through a band around `centre` Hz.
    `density` is clicks per second, a scalar or one value per sample, so the crackle can thicken and thin out."""
    n = int(seconds * RATE)
    density = np.broadcast_to(np.asarray(density, dtype=np.float64), (n,))
    out = np.zeros(n)
    time = rng.exponential(1 / max(density[0], 0.5))
    while time < seconds:
        i = int(time * RATE)
        m = int(rng.uniform(0.0008, 0.005) * RATE) + 2
        click = rng.normal(0, 1, m) * np.exp(-np.arange(m) / (m / 3)) * rng.uniform(0.2, 1.0) ** 2.5
        out[i:i + m] += click[: n - i]
        time += rng.exponential(1 / max(density[min(i, n - 1)], 0.5))
    return svf(out, centre, q)


def saw(t, freq, top=6000):
    """A soft, band-limited sawtooth whose pitch may glide (freq: scalar or per-sample array, Hz)."""
    freq = np.broadcast_to(np.asarray(freq, dtype=np.float64), t.shape)
    phase = 2 * np.pi * np.cumsum(freq) / RATE
    out = np.zeros_like(t)
    for k in range(1, 40):
        if k * float(np.max(freq)) > top:
            break
        out += np.sin(k * phase) / k
    return out


def reverb(x, seconds=2.2, wet=0.3, predelay=0.025, bright=7000, dark=900):
    """Space: x convolved with a made-up hall (decaying noise that darkens as it fades, -60 dB at `seconds`), mixed
    with the dry sound. Keeps x's length, so recipes leave room for the tail."""
    n = int(seconds * RATE)
    t = np.arange(n) / RATE
    ir = one_pole_lowpass(rng.normal(0, 1, n), bright * (dark / bright) ** (t / seconds)) * np.exp(-6.9 * t / seconds)
    ir[: int(predelay * RATE)] = 0
    ir /= np.sqrt(np.sum(ir ** 2))
    size = 1 << int(np.ceil(np.log2(len(x) + n)))
    tail = np.fft.irfft(np.fft.rfft(x, size) * np.fft.rfft(ir, size), size)[: len(x)]
    return x * (1 - wet) + tail * wet * 2.2


def glide(t, start, end, settle):
    """A tone that falls fast from `start` to `end` Hz (time constant `settle` s): the 'pew' of ice or glass
    splitting, as a crack runs through it faster than the sound."""
    freq = end + (start - end) * np.exp(-t / settle)
    return np.sin(2 * np.pi * np.cumsum(freq) / RATE)


def ice_crack(seconds, size, rng_pitch):
    """One crack in ice or glass: a hard click, a few dispersive glides falling from high to low, and a short knock
    of body. `size` from 0 (a tick) to 1 (the big one) scales length and depth."""
    t = t_axis(seconds)
    click = rng.normal(0, 1, len(t)) * np.exp(-t / 0.0012)
    click = click - one_pole_lowpass(click, 1500)
    pews = np.zeros(len(t))
    for k in range(3):
        top = rng_pitch * (1 + 0.35 * k) * rng.uniform(0.9, 1.1)
        bottom = 180 + 220 * (1 - size) + 40 * k
        pews += glide(t, top, bottom, 0.012 + 0.02 * size) * np.exp(-t / (0.05 + 0.12 * size)) / (1 + k)
    knock = svf(rng.normal(0, 1, len(t)), 320 - 120 * size, 2.0) * np.exp(-t / (0.03 + 0.08 * size))
    return click * 0.6 + pews * 0.7 + knock * 0.5


def sub_boom(t, start=48, end=31, decay=0.6):
    """A deep, soft boom felt more than heard: a falling sine with a gentle 5 ms attack."""
    freq = end + (start - end) * np.exp(-t / 0.15)
    return np.sin(2 * np.pi * np.cumsum(freq) / RATE) * np.minimum(1, t / 0.005) * np.exp(-t / decay)


def write(group, name, x, loop=False):
    folder = SOUNDS / group
    folder.mkdir(parents=True, exist_ok=True)
    path = folder / f"{name}.ogg"
    sf.write(path, x.astype(np.float32), RATE, format="OGG", subtype="VORBIS")
    print(f"{path.relative_to(ROOT)}  {len(x) / RATE:.2f} s{'  loop' if loop else ''}")


# ---- The Seam (spec section 20: 11 sounds) ------------------------------------------------------------------------

def seam_hairline():
    """Beat 2, the Seam appears (0 to 1.5 s, then the crack): the air goes tense. A shimmer of glass swells in
    backwards (a reversed reverb tail) and peaks the instant the crack lands, over a low drone and a breath of rising
    air. No static: it should feel like the sky drawing in a breath."""
    seconds = 2.0
    t = t_axis(seconds)
    peak = 1.5
    # A soft glass chord, put through a long hall and reversed, so it swells up to `peak` and stops.
    chord_t = t_axis(2.4)
    chord = (bell(chord_t, 1046.5, decay=1.6) + 0.7 * bell(chord_t, 1568.0, decay=1.4) + 0.5 * bell(chord_t, 1318.5, decay=1.5))
    swell = reverb(chord * 0.5, seconds=2.4, wet=0.85)[::-1]
    swell = one_pole_lowpass(swell, 4500)
    reverse = np.zeros(len(t))
    n = int(peak * RATE)
    reverse[max(0, n - len(swell)):n] = swell[-min(n, len(swell)):]
    # A low drone (A1 with its fifth) that rises with the swell and lets go with it.
    rise = np.clip(t / peak, 0, 1) ** 2
    after = 1 - np.clip((t - peak) / 0.4, 0, 1)
    drone = one_pole_lowpass(saw(t, 55) + 0.6 * saw(t, 82.4), 120 + 380 * rise) * rise * after
    air = svf(rng.normal(0, 1, len(t)), 600 + 2400 * rise, 0.9) * rise * after
    mix = reverse * 1.0 + drone * 0.22 + air * 0.25
    return finish_level(reverb(mix, seconds=1.8, wet=0.25), -24, fade_in=0.05, fade_out=0.3)


def seam_crack():
    """Beat 3, the sky splits (1.5 to 3.5 s, eight steps a quarter second apart, as on screen). One big crack lands
    first: a hard snap, the falling 'pew' of ice splitting and a deep boom. Then eight smaller ice cracks, one per step,
    rising a little in pitch, each with a faint glass tone, over a dark tone that opens up as the sky tears. The cracks
    stay fairly dry so each one is heard; the tones and the tear sit in a wide hall. Kept below 9 kHz so it never
    hisses."""
    seconds = 3.6
    t = t_axis(seconds)
    hits = np.zeros(len(t))
    big = ice_crack(0.8, 1.0, 4200)
    hits[: len(big)] += big * 2.2
    tones = sub_boom(t, decay=0.45) * 0.5
    glass_t = t_axis(1.2)
    for step in range(8):
        n = int((0.25 + step * 0.25) * RATE)
        crack = ice_crack(0.3, 0.2 + 0.05 * step, 3200 + 250 * step) * (0.9 + 0.06 * step)
        end = min(len(t), n + len(crack))
        hits[n:end] += crack[: end - n]
        tone = bell(glass_t, 880 * 2 ** ((step % 5) * 2 / 12), ratios=(1, 2.76), amps=(1, 0.3), decay=0.5) * 0.08
        end = min(len(t), n + len(tone))
        tones[n:end] += tone[: end - n]
    # The tear: a dark two-voice tone whose filter opens as the crack widens, then lets go.
    open_up = np.clip(t / 2.2, 0, 1)
    tear_shape = np.clip(t / 0.4, 0, 1) * (1 - np.clip((t - 2.2) / 1.0, 0, 1))
    tones += one_pole_lowpass(saw(t, 73.4) + 0.7 * saw(t, 110.0 * 1.003), 200 + 1100 * open_up ** 1.5) * tear_shape * 0.07
    mix = reverb(hits, seconds=1.6, wet=0.15) + reverb(tones, seconds=2.6, wet=0.4)
    return finish_level(one_pole_lowpass(mix, 9000), -17, fade_out=0.4)


def seam_close():
    """Beat 8, as the Seam starts to mend: it crackles shut. Air is drawn back in, low and falling, while the static
    thickens and tightens and a low drone sinks; it all cuts off in a deep, soft thump as the crack seals at 3 s,
    which is when the closing chime plays on a full mend."""
    seconds = 3.6
    t = t_axis(seconds)
    seal = 3.0
    build = np.clip(t / seal, 0, 1)
    # A smooth cut at the seal rather than a click.
    before = np.clip((seal - t) / 0.012, 0, 1)
    suck = svf(rng.normal(0, 1, len(t)), 1700 - 1450 * build, 1.1) * build ** 1.3 * before * 0.5
    static = crackle(seconds, 15 + 220 * build ** 2, centre=1300 - 500 * build, q=1.1) * before * (0.4 + 0.6 * build)
    drone = one_pole_lowpass(saw(t, 72 - 34 * build), 180 + 220 * build) * build ** 1.5 * before * 0.5
    after = np.maximum(0, t - seal)
    thump = np.sin(2 * np.pi * (58 * after - 14 * after * after)) * np.minimum(1, after / 0.004) * np.exp(-after / 0.22) * (t >= seal)
    tail = svf(rng.normal(0, 1, len(t)), 90, 0.8, mode="low") * np.exp(-after / 0.35) * (t >= seal) * 0.4
    mix = suck * 1.2 + static * 3.5 + drone * 0.45 + thump * 1.1 + tail
    return finish_level(echo(mix, taps=((0.1, 0.22),), tone=1200), -19, fade_out=0.25)


# Thread snaps: a harp pluck in a rising pentatonic run (C5 D5 E5 G5 A5), with a tiny snap transient.
THREAD_PITCHES = (523.25, 587.33, 659.26, 783.99, 880.0)


def seam_thread_snap(k):
    """Beat 4: a golden thread snaps. A harp pluck with a soft snap transient and a small glass sparkle an octave and
    a fifth up, in the same hall as the crack, so the five rising plucks ring out over it rather than poke out of it."""
    seconds = 2.2
    t = t_axis(seconds)
    string = pluck(THREAD_PITCHES[k], seconds, brightness=0.35)
    octave = pluck(THREAD_PITCHES[k] * 2, seconds, brightness=0.2) * 0.25
    snap = rng.normal(0, 1, len(t)) * env_exp(t, 0.003)
    snap = snap - one_pole_lowpass(snap, 2500)
    sparkle = bell(t, THREAD_PITCHES[k] * 3, ratios=(1, 2.76), amps=(1, 0.25), decay=0.6) * 0.15
    mix = one_pole_lowpass(string + octave + 0.35 * snap + sparkle, 7000)
    return finish_level(reverb(mix, seconds=2.0, wet=0.3), -22, fade_out=0.3)


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
    """Beat 6, every 6 s: a soft low 'whum' that falls in pitch, under a glass-harmonica shimmer, in the same hall
    as the rest of the opening."""
    seconds = 3.2
    t = t_axis(seconds)
    whum = np.sin(2 * np.pi * (200 * t - 30 * t * t)) * np.minimum(1, t / 0.05) * np.exp(-t / 0.9)
    shimmer_env = np.minimum(1, t / 0.4) * np.exp(-np.maximum(0, t - 0.4) / 1.1)
    shimmer = (np.sin(2 * np.pi * 880 * t) + 0.5 * np.sin(2 * np.pi * 1318.5 * t) + 0.3 * np.sin(2 * np.pi * 1760 * t * 1.003)) * shimmer_env
    return finish_level(reverb(whum + 0.35 * shimmer, seconds=2.4, wet=0.3), -22, fade_out=0.3)


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


SEAM = (
    [("hairline", seam_hairline), ("crack", seam_crack)]
    + [(f"thread_snap_{k + 1}", (lambda k=k: seam_thread_snap(k))) for k in range(5)]
    + [("hum", seam_hum), ("ring_pulse", seam_ring_pulse), ("crossing", seam_crossing), ("mend", seam_mend)]
)
LOOPS = {"seam/hum"}
# The Seam's closing crackle has its own folder (sounds/closing/), see docs/CLOSING-AUDIO.md.
CLOSING = [("close", seam_close)]
GROUPS = {"seam": SEAM, "closing": CLOSING}


def main(argv):
    """With no arguments, writes every sound. Otherwise each argument is a group ("seam") or one sound ("seam/hum")."""
    wanted = argv or list(GROUPS)
    for group, recipes in GROUPS.items():
        for name, recipe in recipes:
            key = f"{group}/{name}"
            if group in wanted or key in wanted:
                seed_for(key)
                write(group, name, recipe(), key in LOOPS)


if __name__ == "__main__":
    main(sys.argv[1:])
