# The Seam's closing audio

The sound the Seam makes as it closes: air drawn back in, crackle thickening and tightening, a sinking drone, then a
deep, soft thump as the crack seals. It was added in the M1 follow-up at the author's request ("add a sfx for it
closing"). It is not one of the spec's 11 Seam sounds, so it has its own folder (DECISIONS K44).

## Where it is

| What | Where |
|---|---|
| The audio file | `src/main/resources/assets/skyseam/sounds/closing/close.ogg` (mono Ogg Vorbis, 44.1 kHz, 3.6 s) |
| Its sound event | `skyseam:seam.close`, registered in `registry/SkyseamSounds.java` as `SEAM_CLOSE` |
| Its definition | the `seam.close` entry in `src/main/resources/assets/skyseam/sounds.json`: names `skyseam:closing/close`, fades out over 128 blocks |
| Its subtitle | `subtitles.skyseam.seam.close` in `assets/skyseam/lang/en_us.json`: "Seam crackles shut" |
| Its recipe | `seam_close()` in `tools/audio/synth.py`, in the `closing` group |

## How it was added

1. **Made the sound.** `seam_close()` in `tools/audio/synth.py` builds it from four parts:
   - band-passed noise whose band falls (the air drawn back in);
   - static crackle that gets denser and lower over 3 s;
   - a low sawtooth drone that sinks in pitch;
   - a deep falling thump at exactly 3.0 s, with a short low rumble after it.

   Everything before the thump is cut off with a 12 ms fade, so the seal sounds sudden but doesn't click. The level is
   set by loudness (loudest 50 ms at −19 dBFS), not by peak.
2. **Registered it.** `SkyseamSounds.SEAM_CLOSE = seam("close")`. Like every Seam sound it is a fixed-range event, so
   the server sends it to players up to 256 blocks away.
3. **Defined it.** The `seam.close` entry in `sounds.json`, plus its subtitle in `en_us.json`.
4. **Played it.** In `seam/SeamEntity.java`, the moment a Seam starts mending (tick 0 of the mending state) the
   server plays `SEAM_CLOSE` at the Seam's centre. A full mend from open takes 4 s, and the closing chime
   (`seam.mend`) plays at 75% of it, 3 s in, which is exactly when this sound's thump lands. A Seam mended part-way
   through opening mends faster, so the chime comes earlier than the thump.
5. **Checked it.** `tools/audio/check.py` passes it (mono, 44.1 kHz, no clipping, no DC offset). The asset GameTest
   (`AssetDefinitionTests`) checks it has a `sounds.json` entry, a subtitle and its file.

## Replacing or remaking it

- **With your own sound:** overwrite `sounds/closing/close.ogg` with a mono `.ogg` of the same name. No code change.
  Keep the seal about 3 s in if you want it to land with the chime.
- **Remake the synthesized one** after changing its recipe:

  ```bash
  tools/.venv/Scripts/python.exe tools/audio/synth.py closing
  ```

  This rewrites only this file: each sound has its own random seed. Then run `tools/audio/check.py`.
- **Louder or quieter, or heard further:** change `volume` or `attenuation_distance` in the `seam.close` entry of
  `sounds.json`.
