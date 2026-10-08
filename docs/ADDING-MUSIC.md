# Adding music

> **Status: Step 0.** The music manager and the empty slots are built in **M4** (spec §19). Until M4 lands,
> dropping a file in does nothing, and `tools/sync_music.py` does not exist yet. This guide describes the
> finished system so the slot names are fixed now. It is updated when M4 is done.

Skyseam ships **no music** (spec §21, decision 11). Every slot below is empty, and the game runs silent of music
until you add a file. You add tracks without touching code.

## Slots

| Slot id | Sound event | Plays when | File |
|---|---|---|---|
| `tide_calm` | `skyseam:music.tide_calm` | Calm (no Tide) in the Halcyon | `assets/skyseam/sounds/music/tide_calm.ogg` |
| `tide_prismfall` | `skyseam:music.tide_prismfall` | The Prismfall Tide is running | `…/music/tide_prismfall.ogg` |
| `tide_gale` | `skyseam:music.tide_gale` | The Gale Tide is running | `…/music/tide_gale.ogg` |
| `tide_bloom` | `skyseam:music.tide_bloom` | The Bloom Tide is running | `…/music/tide_bloom.ogg` |
| `tide_stillness` | `skyseam:music.tide_stillness` | The Stillness Tide is running | `…/music/tide_stillness.ogg` |
| `boss_regent` | `skyseam:music.boss_regent` | The Prism Regent fight is active | `…/music/boss_regent.ogg` |
| `boss_skyray` | `skyseam:music.boss_skyray` | The Tempest Skyray fight is active | `…/music/boss_skyray.ogg` |
| `boss_cantor` | `skyseam:music.boss_cantor` | The Hollow Cantor fight is active | `…/music/boss_cantor.ogg` |
| `boss_<boss>_p1`, `_p2`, `_p3` (optional) | `skyseam:music.boss_<boss>_p<n>` | That phase of that fight. If a phase stem is missing, the main boss theme keeps playing | `…/music/boss_regent_p1.ogg` etc. |
| `anchorage` (optional) | `skyseam:music.anchorage` | At the Anchorage | `…/music/anchorage.ogg` |
| `spire` (optional) | `skyseam:music.spire` | In the Conservatory Spire | `…/music/spire.ogg` |
| `seam_open` (optional) | `skyseam:music.seam_open` | During the Seam reveal | `…/music/seam_open.ogg` |
| `hush_stinger` (optional) | `skyseam:music.hush_stinger` | Rarely in the Hush (at most once every 10 minutes). No Tide music plays in the Hush | `…/music/hush_stinger.ogg` |

Priority: an active boss fight first, then the current Tide, otherwise nothing. Slots crossfade over about 3 seconds
and follow your music volume. The config switch `music.enabled` turns all of it off.

## Steps

1. Export the track as **Ogg Vorbis** (`.ogg`). Stereo is fine for music.
2. Name it exactly `<slot>.ogg` and put it in `src/main/resources/assets/skyseam/sounds/music/`.
3. Make `sounds.json` name it, in one of two ways:
   - Run `tools/.venv/Scripts/python.exe tools/sync_music.py` (arrives in M4). It scans the music folder and rewrites the music block of `assets/skyseam/sounds.json`.
   - Or paste the entry by hand into `assets/skyseam/sounds.json`, one per file you added:

     ```json
     "music.tide_calm": {
       "sounds": [ { "name": "skyseam:music/tide_calm", "stream": true } ]
     }
     ```

     Every music entry uses `"stream": true`.
4. Rebuild, or reload resources with F3+T, and play.

## Without touching the jar

A resource pack with its own `assets/skyseam/sounds.json` and `assets/skyseam/sounds/music/<slot>.ogg` also works. That lets
pack makers add music without rebuilding Skyseam.

## What happens with an empty slot

The manager checks whether a slot has a sound definition. If it doesn't, it plays nothing. That causes no crash, and at most one
info line per slot per session in the log.
