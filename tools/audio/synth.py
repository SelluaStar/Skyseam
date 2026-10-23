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
    backwards (a reversed reverb tail) and peaks the instant the crack lands, over the opening's low A bass building
    up and a breath of rising air. No static: it should feel like the sky drawing in a breath."""
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
    # The opening's low A bass, building from nothing into the crack's hit.
    low = bass(t, np.clip(t / peak, 0, 1) ** 1.2 * after)
    air = svf(rng.normal(0, 1, len(t)), 600 + 2400 * rise, 0.9) * rise * after
    mix = reverse * 1.0 + low * 0.9 + air * 0.25
    return finish_level(reverb(mix, seconds=1.8, wet=0.25), -24, fade_in=0.05, fade_out=0.3)


def bass(t, shape, root=55.0):
    """The opening's low hum: a clean A1 (55 Hz, the open hum's note) with a soft octave and a slow sway, shaped by
    `shape` (one value per sample). Sines only, so it is felt as warmth rather than heard as buzz."""
    sway = 1 + 0.08 * np.sin(2 * np.pi * 0.7 * t)
    tone = np.sin(2 * np.pi * root * t) + 0.35 * np.sin(2 * np.pi * 2 * root * t + 0.5) + 0.12 * np.sin(2 * np.pi * 3 * root * t)
    return tone * shape * sway


def seam_crack():
    """Beat 3, the sky splits open (1.5 s on): one clean, heavy hit, then it blooms. A hard snap with the falling
    'pew' of ice splitting and a deep sub boom, then a warm A-major chord (with an added ninth) swelling open in a wide
    hall as the crack spreads, over the opening's low A bass, which carries on into the open Seam's hum. No run of
    small cracks: just the one moment."""
    seconds = 3.6
    t = t_axis(seconds)
    hit = np.zeros(len(t))
    big = ice_crack(0.8, 1.0, 4200)
    hit[: len(big)] += big * 2.2
    boom = sub_boom(t, start=52, end=33, decay=0.8)
    # The bloom: soft detuned sines with a little glass on top, swelling in after the hit and fading at the end.
    bloom_shape = np.clip(t / 0.9, 0, 1) ** 1.5 * (1 - np.clip((t - 2.4) / 1.2, 0, 1))
    chord = np.zeros(len(t))
    for freq, amp in ((220.0, 1.0), (329.63, 0.8), (440.0, 0.7), (493.88, 0.45), (554.37, 0.55)):
        chord += amp * 0.5 * (np.sin(2 * np.pi * freq * t) + np.sin(2 * np.pi * freq * 1.004 * t + 1.0))
    glass = bell(np.maximum(0, t - 0.08), 1760, ratios=(1, 2.76), amps=(1, 0.25), decay=1.2) * (t >= 0.08) * 0.25
    bloom = one_pole_lowpass(chord * bloom_shape, 2500) * 0.35 + glass
    # Held right to the end, so it hands over to the open Seam's hum (same note) as that fades in.
    low = bass(t, np.minimum(1, 0.6 + t / 0.15) * (1 - np.clip((t - 3.3) / 0.3, 0, 1)))
    mix = reverb(hit, seconds=1.6, wet=0.2) + boom * 0.9 + reverb(bloom, seconds=2.8, wet=0.45) + low * 0.5
    return finish_level(one_pole_lowpass(mix, 9000), -16, fade_out=0.4)


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


# ---- The Harmonic Aperture and the Skychart (spec section 21: "Aperture (charge loop, ready chime, mount click)",
# "Skychart unfold") -----------------------------------------------------------------------------------------------


def aperture_charge():
    """Beat 1: the Aperture's core spinning up. A 2 second seamless loop: a warm A3 chord with a fast rotating
    tremolo and a soft whirr of air, as if something is turning fast inside brass. The client raises its pitch and
    volume as the charge fills, so the loop itself stays level."""
    seconds = 2.0
    t = t_axis(seconds)
    # Whole numbers of cycles in 2 s for every layer, so the loop joins cleanly.
    spin = 0.5 + 0.5 * np.sin(2 * np.pi * 8 * t)
    x = np.zeros(len(t))
    for f, amp in ((220.0, 1.0), (330.0, 0.5), (440.0, 0.35), (660.0, 0.12)):
        x += amp * np.sin(2 * np.pi * f * t)
    x *= 0.75 + 0.25 * spin
    fade = int(0.3 * RATE)
    whirr = svf(rng.normal(0, 1, len(t) + fade), 1400, q=2.0) * 0.6
    head = whirr[:fade].copy()
    whirr = whirr[fade:]
    ramp = np.linspace(0, 1, fade)
    whirr[-fade:] = whirr[-fade:] * (1 - ramp) + head * ramp
    x = x + whirr * spin
    low = np.sin(2 * np.pi * 110 * t) * 0.4
    x = one_pole_lowpass(x + low, 5000)
    x = x - np.mean(x)
    return x / np.max(np.abs(x)) * PEAK * 0.7


def aperture_ready():
    """The charge is full: a bright rising A-major arpeggio of glass bells (A5, C#6, E6) over a soft low thump,
    in the opening's hall."""
    seconds = 2.2
    t = t_axis(seconds)
    x = np.zeros(len(t))
    for start, f0, amp in ((0.0, 880.0, 0.8), (0.08, 1108.73, 0.7), (0.16, 1318.5, 0.9)):
        n = int(start * RATE)
        x[n:] += (bell(t, f0, decay=1.1) * amp)[: len(t) - n]
    thump = np.sin(2 * np.pi * 110 * t) * np.minimum(1, t / 0.004) * np.exp(-t / 0.18)
    return finish_level(reverb(x * 0.6 + thump, seconds=1.8, wet=0.3), -18, fade_out=0.3)


def aperture_mount():
    """The Aperture clicks into place on a ship: two quick metal clicks and a small low knock."""
    seconds = 0.6
    t = t_axis(seconds)
    x = np.zeros(len(t))
    for start, centre, amp in ((0.0, 3200, 1.0), (0.07, 2100, 0.8)):
        n = int(start * RATE)
        burst = bandpass_noise(seconds, centre, 4.0) * env_exp(t, 0.012, attack=0.0005)
        ring = np.sin(2 * np.pi * centre * 0.6 * t) * env_exp(t, 0.05, attack=0.0005) * 0.3
        x[n:] += ((burst + ring) * amp)[: len(t) - n]
    knock = np.sin(2 * np.pi * (140 - 40 * np.clip(t / 0.1, 0, 1)) * t) * env_exp(t, 0.06, attack=0.002) * 0.8
    n = int(0.07 * RATE)
    x[n:] += knock[: len(t) - n]
    return finish_level(x, -18, fade_out=0.05)


def skychart_unfold():
    """The Skychart unfolds: three soft swishes of stiff paper and a faint sparkle as the chart opens."""
    seconds = 0.9
    t = t_axis(seconds)
    x = np.zeros(len(t))
    for start, length, centre in ((0.0, 0.18, 3200), (0.16, 0.22, 2600), (0.36, 0.3, 3800)):
        n = int(start * RATE)
        local = t_axis(length)
        shape = np.sin(np.pi * local / length) ** 2
        swish = bandpass_noise(length, centre, 1.2) * shape
        swish += crackle(length, 120, centre=4000, q=1.5) * shape * 0.5
        x[n:n + len(swish)] += swish[: len(t) - n]
    sparkle = bell(t, 2093.0, decay=0.4) * np.clip((t - 0.45) / 0.02, 0, 1) * 0.15
    return finish_level(x + sparkle, -24, fade_out=0.1)


def mirror_sea_rebound():
    """Rebound (spec section 8): the Mirror Sea throws you back up. A watery whoosh that rises, a few bubbles, and a
    quick glass glissando climbing C5, E5, G5, C6, so it sounds like being sent back up rather than hurt."""
    seconds = 1.6
    t = t_axis(seconds)
    sweep_centre = 350 + 2200 * np.clip(t / 0.7, 0, 1) ** 1.5
    whoosh = np.zeros(len(t))
    noise = np.random.default_rng(7).standard_normal(len(t))
    # A band of noise whose centre slides up: filter in short blocks.
    block = 512
    for start in range(0, len(t), block):
        stop = min(len(t), start + block)
        whoosh[start:stop] = svf(noise[max(0, start - 2048):stop], sweep_centre[start], 2.0)[-(stop - start):]
    whoosh *= np.sin(np.pi * np.clip(t / 0.9, 0, 1)) ** 2 * 0.8
    bubbles = np.zeros(len(t))
    rng = np.random.default_rng(11)
    for _ in range(9):
        start = rng.uniform(0.0, 0.5)
        f = rng.uniform(500, 1200)
        n = int(start * RATE)
        local = t[: len(t) - n]
        pop = np.sin(2 * np.pi * (f + 900 * local) * local) * np.exp(-local / 0.03) * 0.25
        bubbles[n:] += pop
    chime = np.zeros(len(t))
    for k, f0 in enumerate((523.25, 659.26, 783.99, 1046.5)):
        n = int((0.18 + 0.07 * k) * RATE)
        chime[n:] += (bell(t, f0, decay=0.9) * (0.45 + 0.1 * k))[: len(t) - n]
    return finish_level(reverb(whoosh + bubbles + chime * 0.5, seconds=1.4, wet=0.28), -20, fade_out=0.25)


# ---- The flux vortex (the author's request after M3: a Pandora-like vortex of wind and light) ---------------------

def swept_noise(t, centre, q=1.6, block=512, seed=5):
    """Noise through a band-pass whose centre follows `centre` (an array, Hz), filtered in short blocks."""
    noise = np.random.default_rng(seed).standard_normal(len(t))
    out = np.zeros(len(t))
    for start in range(0, len(t), block):
        stop = min(len(t), start + block)
        out[start:stop] = svf(noise[max(0, start - 2048):stop], centre[start], q)[-(stop - start):]
    return out


def flux_vortex_loop():
    """The vortex's roar, a 6 second seamless loop: deep turning wind, a whistle of air circling high above it, and
    a low magnetic hum (A1 and E2 with a slow beat), every tonal layer a whole number of cycles long and the noise
    crossfaded over the loop point."""
    seconds = 6.0
    t = t_axis(seconds)
    fade = int(0.6 * RATE)
    long_t = t_axis(seconds + 0.6)
    # The wind's band turns slowly round (two cycles a loop), so the roar seems to circle.
    turn = np.sin(2 * np.pi * 2 * long_t / seconds)
    wind = swept_noise(long_t, 260 + 120 * turn, q=0.9, seed=31) * 0.9
    whistle = swept_noise(long_t, 1500 + 500 * np.sin(2 * np.pi * 3 * long_t / seconds + 1), q=4.0, seed=32) * 0.22
    air = wind + whistle
    head = air[:fade].copy()
    air = air[fade:]
    ramp = np.linspace(0, 1, fade)
    air[-fade:] = air[-fade:] * (1 - ramp) + head * ramp
    hum = np.zeros(len(t))
    for cycles, amp in ((330, 1.0), (336, 0.7), (495, 0.45), (660, 0.25), (990, 0.1)):
        hum += amp * np.sin(2 * np.pi * (cycles / seconds) * t)
    hum *= 0.85 + 0.15 * np.sin(2 * np.pi * 3 * t / seconds)
    x = air + hum * 0.32
    x = x - np.mean(x)
    return x / np.max(np.abs(x)) * PEAK * 0.85


def flux_vortex_appear():
    """A vortex forms: a deep boom, then a band of wind sweeping up from a rumble to a whistle as the column climbs,
    the hum rising under it, and a bright shimmer as it reaches the sky."""
    seconds = 4.0
    t = t_axis(seconds)
    rise = np.clip(t / 3.0, 0, 1)
    wind = swept_noise(t, 160 + 2200 * rise ** 1.8, q=1.4, seed=41)
    wind *= np.minimum(1, t / 0.4) * (1 - np.clip((t - 3.0) / 1.0, 0, 1)) ** 2
    hum = sum(a * np.sin(2 * np.pi * np.cumsum(f * (0.6 + 0.4 * rise)) / RATE) for f, a in ((55, 1.0), (82.4, 0.6), (110, 0.4)))
    hum *= np.minimum(1, t / 0.8) * np.exp(-np.clip(t - 3.0, 0, None) / 0.4)
    shimmer = np.zeros(len(t))
    for k, f0 in enumerate((880, 1318.5, 1760)):
        n = int((2.6 + 0.12 * k) * RATE)
        shimmer[n:] += bell(t, f0, decay=1.0)[: len(t) - n] * 0.25
    boom = sub_boom(t, start=60, end=34, decay=0.9)
    return finish_level(reverb(wind * 0.8 + hum * 0.35 + shimmer + boom * 0.9, seconds=2.2, wet=0.3), -16, fade_out=0.3)


def flux_vortex_dissipate():
    """A vortex unravels: the wind falls from a whistle to a sigh, the hum sinks away, and a last soft gust."""
    seconds = 4.0
    t = t_axis(seconds)
    fall = np.clip(t / 3.2, 0, 1)
    wind = swept_noise(t, 1900 - 1700 * fall ** 0.7, q=1.3, seed=51)
    wind *= np.minimum(1, t / 0.15) * (1 - fall) ** 1.2
    hum = sum(a * np.sin(2 * np.pi * np.cumsum(f * (1 - 0.45 * fall)) / RATE) for f, a in ((110, 1.0), (165, 0.5), (220, 0.3)))
    hum *= (1 - fall) ** 2
    gust = swept_noise(t, np.full(len(t), 500.0), q=0.8, seed=52) * np.exp(-((t - 2.9) / 0.35) ** 2) * 0.5
    return finish_level(reverb(wind * 0.8 + hum * 0.3 + gust, seconds=2.0, wet=0.3), -17, fade_out=0.4)


def flux_vortex_launch():
    """Thrown up by the vortex: a deep whump and a fast rising whoosh of air."""
    seconds = 1.6
    t = t_axis(seconds)
    whoosh = swept_noise(t, 300 + 2600 * np.clip(t / 0.8, 0, 1) ** 1.4, q=1.8, seed=61)
    whoosh *= np.sin(np.pi * np.clip(t / 1.1, 0, 1)) ** 2
    boom = sub_boom(t, start=70, end=38, decay=0.35)
    thump = svf(np.random.default_rng(62).standard_normal(len(t)), 180, q=0.8, mode="low") * np.exp(-t / 0.08)
    return finish_level(reverb(whoosh * 0.7 + boom + thump * 0.6, seconds=1.2, wet=0.22), -17, fade_out=0.25)


FLUX_VORTEX = [("loop", flux_vortex_loop), ("appear", flux_vortex_appear), ("dissipate", flux_vortex_dissipate),
               ("launch", flux_vortex_launch)]


# ---- The Springbloom (docs/DECISIONS.md K98) ------------------------------------------------------------------------


def springbloom_squish(k):
    """Landing on a Springbloom: a rude, rubbery raspberry. A buzzy, fluttering low tone (a soft square wave, its pitch
    jittering and sagging, its loudness flapping like lips), through a wet squelch at the start, a breathy hiss under
    it and a little "pft" as it runs out. Three takes: medium, long and low, short and high."""
    seconds = (0.46, 0.62, 0.34)[k]
    f0 = (112.0, 88.0, 138.0)[k]
    flutter_rate = (27.0, 22.0, 33.0)[k]
    gen = np.random.default_rng(700 + k)
    t = t_axis(seconds)
    jitter = one_pole_lowpass(gen.standard_normal(len(t)), 40) * 4.0
    freq = f0 * (1 - 0.32 * (t / seconds) ** 1.3) * (1 + 0.05 * np.sin(2 * np.pi * 6.5 * t)) * (1 + 0.04 * jitter)
    phase = 2 * np.pi * np.cumsum(freq) / RATE
    buzz = np.tanh(3.5 * np.sin(phase)) + 0.35 * np.tanh(2 * np.sin(2 * phase + 0.6))
    flap = 0.55 + 0.45 * np.clip(np.sin(2 * np.pi * flutter_rate * t + gen.uniform(0, 6)), -0.2, 1)
    buzz = svf(buzz * flap, 850, q=0.8, mode="low") + 0.5 * svf(buzz * flap, 320, q=1.6)
    breath = svf(gen.standard_normal(len(t)), 1100, q=0.9) * 0.18
    squelch = svf(gen.standard_normal(len(t)), 240 + 500 * np.exp(-t / 0.03), q=3) * np.exp(-t / 0.035) * 1.4
    pft = svf(gen.standard_normal(len(t)), 1800, q=1.2) * np.exp(-np.maximum(0, seconds - 0.05 - t) / 0.012) * (t > seconds - 0.09) * 0.6
    body = (buzz + breath) * np.minimum(1, t / 0.012) * np.clip((seconds - t) / 0.08, 0, 1) ** 0.6
    return finish_level(body + squelch + pft, -16, fade_out=0.02)


def springbloom_launch(k):
    """Jumping off a Springbloom: a springy boing that rises and wobbles away, over a deep bump and a whoosh of air
    climbing after you. Two takes, the second a little higher and quicker."""
    seconds = 1.1
    t = t_axis(seconds)
    lift = (1.0, 1.15)[k]
    freq = lift * (170 + 260 * (1 - np.exp(-t / 0.05))) * (1 + 0.09 * np.exp(-t / 0.22) * np.sin(2 * np.pi * 15 * t))
    phase = 2 * np.pi * np.cumsum(freq) / RATE
    boing = (np.sin(phase) + 0.35 * np.sin(2 * phase) + 0.12 * np.sin(3 * phase)) * env_exp(t, 0.28, attack=0.004)
    whoosh = swept_noise(t, 350 + 2800 * np.clip(t / 0.6, 0, 1) ** 1.3, q=1.8, seed=81 + k)
    whoosh *= np.sin(np.pi * np.clip(t / 0.9, 0, 1)) ** 2 * 0.8
    bump = sub_boom(t, start=95, end=55, decay=0.09)
    return finish_level(reverb(boing * 0.8 + whoosh + bump * 0.9, seconds=0.9, wet=0.18), -16, fade_out=0.2)


SPRINGBLOOM = ([(f"squish_{k + 1}", (lambda k=k: springbloom_squish(k))) for k in range(3)]
               + [(f"launch_{k + 1}", (lambda k=k: springbloom_launch(k))) for k in range(2)])


# ---- The wind lanes (docs/DECISIONS.md K109) ----------------------------------------------------------------------


def wind_lane_loop():
    """A wind lane's rush of air, an 8 second seamless loop: a broad, soft wind that gusts and eases (three gusts a
    loop), a thinner airy band sweeping up and down above it, and the faint whistle of air past something, the noise
    crossfaded over the loop point. No tones, so it sits under the Halcyon's music and chimes."""
    seconds = 8.0
    fade = int(0.8 * RATE)
    long_t = t_axis(seconds + 0.8)
    gusts = 0.65 + 0.35 * np.sin(2 * np.pi * 3 * long_t / seconds) * np.sin(2 * np.pi * 1 * long_t / seconds + 0.7)
    broad = swept_noise(long_t, 420 + 160 * np.sin(2 * np.pi * 2 * long_t / seconds), q=0.7, seed=41) * gusts
    airy = swept_noise(long_t, 1700 + 700 * np.sin(2 * np.pi * 3 * long_t / seconds + 2), q=1.4, seed=42) * 0.35 * gusts
    whistle = swept_noise(long_t, 2600 + 300 * np.sin(2 * np.pi * 4 * long_t / seconds), q=6.0, seed=43) * 0.08 * gusts ** 2
    air = broad + airy + whistle
    head = air[:fade].copy()
    air = air[fade:]
    ramp = np.linspace(0, 1, fade)
    air[-fade:] = air[-fade:] * (1 - ramp) + head * ramp
    air = air - np.mean(air)
    return air / np.max(np.abs(air)) * PEAK * 0.7


WIND_LANE = [("loop", wind_lane_loop)]


# ---- The Murmurberry (docs/DECISIONS.md K106, K107) -------------------------------------------------------------------

# C5, D5, E5, G5, A5: a pentatonic scale, so any of them heard together sound sweet, and a ripple running across a
# cave of them plays like wind chimes.
MURMUR_NOTES = (523.25, 587.33, 659.25, 783.99, 880.0)


def murmurberry_chime(k):
    """A Murmurberry lighting up: a soft glassy bloop, like a bubble of light. A round tone that slides up into its note
    as it swells (the balloon filling), a faint glass shimmer an octave above, and a breath of air, in the space of a
    cave, gone in about a second. One take on each note of MURMUR_NOTES."""
    seconds = 1.2
    t = t_axis(seconds)
    f0 = MURMUR_NOTES[k]
    freq = f0 * (1 - 0.1 * np.exp(-t / 0.03))
    phase = 2 * np.pi * np.cumsum(freq) / RATE
    tone = np.sin(phase) + 0.22 * np.sin(2 * phase) * np.exp(-t / 0.25) + 0.07 * np.sin(3.01 * phase) * np.exp(-t / 0.12)
    tone *= np.minimum(1, t / 0.014) * np.exp(-t / 0.3)
    glass = bell(t, f0 * 2, ratios=(1, 2.76), amps=(0.16, 0.05), decay=0.45) * np.minimum(1, t / 0.01)
    air = bandpass_noise(seconds, 2600 + 1400 * np.exp(-t / 0.2), q=0.8) * np.minimum(1, t / 0.02) * np.exp(-t / 0.16) * 0.04
    return finish_level(reverb(tone + glass + air, seconds=1.0, wet=0.24), -21, fade_out=0.3)


def murmurberry_pop(k):
    """A Murmurberry broken: its berries pop like small balloons. A sharp crack, a burst of bright air, a little low
    thump and the rubbery flap of the skin, then a rustle of its leaves. Two takes, the second smaller and higher."""
    seconds = 0.5
    t = t_axis(seconds)
    size = (1.0, 0.75)[k]
    burst = svf(rng.standard_normal(len(t)), 2800 / size, q=0.9) * np.exp(-t / (0.014 * size)) * 1.4
    crack = rng.standard_normal(len(t)) * np.exp(-t / 0.003) * 0.7
    thump = sub_boom(t, start=190 / size, end=95 / size, decay=0.05) * 0.8
    flap = svf(rng.standard_normal(len(t)), 900 / size, q=2.5) * np.exp(-t / 0.06) * 0.35 * (0.6 + 0.4 * np.sin(2 * np.pi * 38 * t))
    rustle = svf(rng.standard_normal(len(t)), 1900, q=0.7) * np.clip((t - 0.04) / 0.03, 0, 1) * np.exp(-np.maximum(0, t - 0.04) / 0.09) * 0.3
    return finish_level(reverb(burst + crack + thump + flap + rustle, seconds=0.6, wet=0.15), -18, fade_out=0.1)


def murmurberry_place(k):
    """A Murmurberry placed: a soft rubbery squeak as its berries settle, and a rustle. Two takes."""
    seconds = 0.38
    t = t_axis(seconds)
    freq = (700.0, 840.0)[k] * (1 + 0.25 * t / seconds)
    phase = 2 * np.pi * np.cumsum(freq) / RATE
    squeak = (np.sin(phase) + 0.3 * np.sin(2 * phase)) * np.minimum(1, t / 0.02) * np.exp(-t / 0.08) * 0.5
    rustle = svf(rng.standard_normal(len(t)), 2200, q=0.7) * np.minimum(1, t / 0.005) * np.exp(-t / 0.1) * 0.6
    return finish_level(squeak + rustle, -20, fade_out=0.08)


MURMURBERRY = ([(f"chime_{k + 1}", (lambda k=k: murmurberry_chime(k))) for k in range(5)]
               + [(f"pop_{k + 1}", (lambda k=k: murmurberry_pop(k))) for k in range(2)]
               + [(f"place_{k + 1}", (lambda k=k: murmurberry_place(k))) for k in range(2)])


# ---- The Tides (docs/DECISIONS.md K111): the Halcyon's ambience, a loop for Calm, each Tide and the Hush (spec section
# 21: "one synthesized loop per Tide and one for the Hush"), and the swell as a Tide turns -------------------------------

AMBIENCE_SECONDS = 16.0


def looped_air(build, seconds=AMBIENCE_SECONDS, fade=1.0):
    """A noise layer `seconds` long that joins its own start: `build(t)` makes it over a little longer, and its tail is
    crossfaded into its head."""
    long_t = t_axis(seconds + fade)
    air = build(long_t)
    n = int(fade * RATE)
    head = air[:n].copy()
    air = air[n:]
    ramp = np.linspace(0, 1, n)
    air[-n:] = air[-n:] * (1 - ramp) + head * ramp
    return air


def folded(x, seconds=AMBIENCE_SECONDS):
    """Folds whatever rings on past `seconds` back onto the start, so a chime near the end carries over the loop point."""
    n = int(seconds * RATE)
    out = x[:n].copy()
    tail = x[n:]
    out[:len(tail)] += tail[:n]
    return out


def hum(t, cycles_and_amps, seconds=AMBIENCE_SECONDS):
    """Sine tones each a whole number of cycles in `seconds`, so they loop cleanly, each swelling slowly on its own."""
    out = np.zeros(len(t))
    for k, (cycles, amp) in enumerate(cycles_and_amps):
        swell = 0.6 + 0.4 * np.sin(2 * np.pi * (1 + k % 2) * t / seconds + k * 1.7)
        out += amp * swell * np.sin(2 * np.pi * (cycles / seconds) * t)
    return out


def chimes(times_freqs, decay, amp, seconds=AMBIENCE_SECONDS, wet=0.35):
    """Soft glass chimes struck at the given times, in a little space, folded over the loop point."""
    t = t_axis(seconds + decay * 6)
    x = np.zeros(len(t))
    for when, f0 in times_freqs:
        start = int(when * RATE)
        tt = t[: len(t) - start]
        x[start:] += bell(tt, f0, ratios=(1, 2.76, 5.4), amps=(1, 0.3, 0.1), decay=decay)
    return folded(reverb(x, seconds=2.5, wet=wet), seconds) * amp


def ambience_calm():
    """Calm: a light breeze rising and falling, air high above it, a soft pad of A, C sharp and E swelling slowly under
    it, and now and then a far-off glass chime."""
    gust = lambda t: 0.6 + 0.4 * np.sin(2 * np.pi * t / AMBIENCE_SECONDS) * np.sin(2 * np.pi * 2 * t / AMBIENCE_SECONDS + 0.5)
    breeze = looped_air(lambda t: swept_noise(t, 360 + 120 * np.sin(2 * np.pi * 2 * t / AMBIENCE_SECONDS), q=0.7, seed=111) * gust(t))
    high = looped_air(lambda t: swept_noise(t, 2400 + 600 * np.sin(2 * np.pi * 3 * t / AMBIENCE_SECONDS + 1), q=1.2, seed=112) * gust(t)) * 0.12
    t = t_axis(AMBIENCE_SECONDS)
    pad = hum(t, ((3520, 0.08), (4440, 0.05), (5280, 0.06)))
    glass = chimes(((2.3, 1318.5), (9.1, 880.0), (13.7, 1108.7)), decay=1.4, amp=0.05)
    x = breeze * 0.5 + high + pad + glass
    x -= np.mean(x)
    return x / np.max(np.abs(x)) * PEAK * 0.6


def ambience_prismfall():
    """Prismfall: the hiss of glass falling like fine rain, and a thousand tiny tinkles as the shards land, over a low
    breeze."""
    hiss = looped_air(lambda t: swept_noise(t, 5200 + 500 * np.sin(2 * np.pi * 2 * t / AMBIENCE_SECONDS), q=0.8, seed=121)) * 0.35
    hiss += looped_air(lambda t: swept_noise(t, 2900 + 300 * np.sin(2 * np.pi * 3 * t / AMBIENCE_SECONDS), q=1.0, seed=122)) * 0.18
    breeze = looped_air(lambda t: swept_noise(t, 340 + 90 * np.sin(2 * np.pi * 2 * t / AMBIENCE_SECONDS), q=0.7, seed=123)) * 0.3
    t = t_axis(AMBIENCE_SECONDS + 3)
    tinkles = np.zeros(len(t))
    for _ in range(90):
        start = int(rng.uniform(0, AMBIENCE_SECONDS) * RATE)
        tt = t[: len(t) - start]
        tinkles[start:] += bell(tt, rng.uniform(2200, 5200), ratios=(1, 2.76), amps=(1, 0.25), decay=rng.uniform(0.12, 0.35)) * rng.uniform(0.02, 0.09)
    tinkles = folded(reverb(tinkles, seconds=1.6, wet=0.3))
    x = hiss + breeze + tinkles
    x -= np.mean(x)
    return x / np.max(np.abs(x)) * PEAK * 0.7


def ambience_gale():
    """Gale: a strong wind roaring in gusts, a howl rising and falling through it, a thin whistle high above and the
    flutter of air past everything."""
    def gusts(t):
        return 0.55 + 0.45 * np.sin(2 * np.pi * 3 * t / AMBIENCE_SECONDS) * np.sin(2 * np.pi * t / AMBIENCE_SECONDS + 0.4)
    roar = looped_air(lambda t: swept_noise(t, 230 + 110 * gusts(t), q=0.6, seed=131) * gusts(t) * (1 + 0.15 * np.sin(2 * np.pi * 11 * t)))
    howl = looped_air(lambda t: swept_noise(t, 700 + 260 * np.sin(2 * np.pi * 2 * t / AMBIENCE_SECONDS + 1), q=9.0, seed=132) * gusts(t) ** 2) * 0.3
    whistle = looped_air(lambda t: swept_noise(t, 2300 + 500 * np.sin(2 * np.pi * 5 * t / AMBIENCE_SECONDS), q=7.0, seed=133) * gusts(t)) * 0.09
    x = roar + howl + whistle
    x -= np.mean(x)
    return x / np.max(np.abs(x)) * PEAK * 0.8


def ambience_bloom():
    """Bloom: a soft, warm breeze, a pad of C, E, G and C swelling gently, and bright little chimes now and then, like
    flowers opening."""
    breeze = looped_air(lambda t: swept_noise(t, 520 + 140 * np.sin(2 * np.pi * 2 * t / AMBIENCE_SECONDS), q=0.7, seed=141)) * 0.35
    t = t_axis(AMBIENCE_SECONDS)
    pad = hum(t, ((4186, 0.08), (5274, 0.06), (6272, 0.06), (8372, 0.025)))
    pentatonic = (1046.5, 1174.66, 1318.5, 1568.0, 1760.0)
    shimmer = chimes([(0.8 + 1.9 * k + rng.uniform(-0.3, 0.3), pentatonic[int(rng.integers(5))]) for k in range(8)], decay=0.9, amp=0.06)
    x = breeze + pad + shimmer
    x -= np.mean(x)
    return x / np.max(np.abs(x)) * PEAK * 0.6


def ambience_stillness():
    """Stillness: no wind at all. A deep, slow drone, low A beating against itself, felt more than heard, and the faint
    pressure of still air."""
    t = t_axis(AMBIENCE_SECONDS)
    drone = hum(t, ((880, 0.5), (884, 0.4), (1320, 0.18), (1760, 0.1)))
    air = looped_air(lambda tt: swept_noise(tt, 180 + 40 * np.sin(2 * np.pi * tt / AMBIENCE_SECONDS), q=0.5, seed=151)) * 0.06
    x = drone + air
    x -= np.mean(x)
    return x / np.max(np.abs(x)) * PEAK * 0.5


def ambience_hush():
    """The Hush: nearly silent on purpose (spec section 7, sound is a channel there). The faintest low air, and twice a
    loop a deep, distant tone, like a bell very far away."""
    air = looped_air(lambda t: swept_noise(t, 260 + 60 * np.sin(2 * np.pi * t / AMBIENCE_SECONDS), q=0.6, seed=161))
    far = chimes(((3.0, 110.0), (11.0, 164.8)), decay=3.0, amp=1.0, wet=0.5)
    x = air * 0.6 + far * 0.4
    x -= np.mean(x)
    return x / np.max(np.abs(x)) * PEAK * 0.35


def tide_turn():
    """A Tide turning: a breath of air swelling up and away, and a soft glass chord (C, E, G) ringing out through it."""
    seconds = 3.5
    t = t_axis(seconds)
    swell = np.minimum(1, t / 1.2) * np.exp(-np.maximum(0, t - 1.2) / 0.6)
    whoosh = svf(rng.standard_normal(len(t)), 300 + 2100 * np.minimum(1, t / 1.4), q=1.1) * swell * 0.5
    chord = np.zeros(len(t))
    start = int(0.9 * RATE)
    for f0 in (523.25, 659.25, 783.99):
        chord[start:] += bell(t[: len(t) - start], f0, decay=1.4) * 0.3
    return finish_level(reverb(whoosh + chord, seconds=2.2, wet=0.35), -18, fade_out=0.4)


TIDE = [("turn", tide_turn), ("ambience_calm", ambience_calm), ("ambience_prismfall", ambience_prismfall), ("ambience_gale", ambience_gale),
        ("ambience_bloom", ambience_bloom), ("ambience_stillness", ambience_stillness), ("ambience_hush", ambience_hush)]


APERTURE = [("charge", aperture_charge), ("ready", aperture_ready), ("mount", aperture_mount)]
MIRROR_SEA = [("rebound", mirror_sea_rebound)]
SKYCHART = [("unfold", skychart_unfold)]


SEAM = (
    [("hairline", seam_hairline), ("crack", seam_crack)]
    + [(f"thread_snap_{k + 1}", (lambda k=k: seam_thread_snap(k))) for k in range(5)]
    + [("hum", seam_hum), ("ring_pulse", seam_ring_pulse), ("crossing", seam_crossing), ("mend", seam_mend)]
)
LOOPS = {"seam/hum", "aperture/charge", "flux_vortex/loop", "wind_lane/loop"} | {f"tide/{name}" for name, _ in TIDE if name.startswith("ambience")}
# The Seam's closing crackle has its own folder (sounds/closing/), see docs/CLOSING-AUDIO.md.
CLOSING = [("close", seam_close)]
GROUPS = {"seam": SEAM, "closing": CLOSING, "aperture": APERTURE, "skychart": SKYCHART, "mirror_sea": MIRROR_SEA,
          "flux_vortex": FLUX_VORTEX, "springbloom": SPRINGBLOOM, "murmurberry": MURMURBERRY, "wind_lane": WIND_LANE,
          "tide": TIDE}


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
