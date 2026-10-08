# Asset paths and the swap rule

**Swap rule (spec §1 rule 4, §20):** every texture, model, animation, sound, music track, structure and lang file is
found **by name** at the paths below. Replacing a file with another file of the same name must work with **no code
change**. If it doesn't, that is a bug: report it.

All paths are under `src/main/resources/`. The namespace is `skyseam`.

| Kind | Folder | Naming | How to replace |
|---|---|---|---|
| Entity geometry | `assets/skyseam/geo/` | `<name>.geo.json` (Bedrock geometry format 1.12.0, which GeckoLib 4.9.3 reads) | Export from the `.bbmodel` source over the same file name. Keep bone names, because animations and code refer to them |
| Animations | `assets/skyseam/animations/` | `<name>.animation.json`. Clip names are `animation.<name>.<action>`. Shared actions: `idle`, `walk`, `fly`, `attack`, `hurt`, `death` | Overwrite the file. Keep the clip names the code plays |
| Entity textures | `assets/skyseam/textures/entity/` | `<name>.png`, plus `<name>_glowmask.png` for the glowing parts | Overwrite. A glowmask **must be the same size** as its base texture (GeckoLib rule) |
| Block textures | `assets/skyseam/textures/block/` | `<name>.png` (16×16), animated ones add `<name>.png.mcmeta` | Overwrite. Keep or update the `.mcmeta` for animated textures |
| Item textures | `assets/skyseam/textures/item/` | `<name>.png` (16×16), optional `.mcmeta` | Overwrite |
| GUI textures | `assets/skyseam/textures/gui/` | `<screen>/<part>.png` | Overwrite. Keep the pixel size, because screen layout reads it |
| Particles | `assets/skyseam/particles/` and `assets/skyseam/textures/particle/` | `<id>.json` (lists its sprites), sprites `<id>_<n>.png` | Overwrite the sprites, or edit the JSON's texture list |
| Sounds | `assets/skyseam/sounds/<group>/` | `<name>.ogg` (mono for positional sounds), declared in `assets/skyseam/sounds.json` | Overwrite the `.ogg`. If the `sounds.json` entry points at a vanilla event (`"type": "event"`), change that entry to name your file |
| Music | `assets/skyseam/sounds/music/` | `<slot>.ogg` (stereo is fine) | See `docs/ADDING-MUSIC.md` |
| Structures | `data/skyseam/structure/` | `<name>.nbt` | Build in creative, save with a structure block under the same name and overwrite. Keep the data markers listed in `docs/STRUCTURE-MARKERS.md` |
| Lang | `assets/skyseam/lang/` | `en_us.json`, other languages `<locale>.json` | Edit or add files. All player-visible text goes through lang keys |
| Mod icon | `src/main/resources/` | `logo.png` (placeholder until the author's art) | Overwrite |

### What exists so far (M1)

| Asset | Path under `assets/skyseam/` | Notes for a replacement |
|---|---|---|
| Seam interior sky | `textures/entity/seam/interior.png` | Any 2:1 size. It tiles left to right; the top and bottom rows are stretched when seen steeply. The game shows it behind the Seam with parallax, horizon at eye level |
| Seam interior glints | `textures/entity/seam/interior_glints.png` | Drawn **additively**: black is invisible, bright pixels glow. A nearer parallax layer |
| Seam particles | `particles/{seam_mote,seam_spark,thread_snap,scar}.json`, sprites `textures/particle/<id>_<n>.png` | Sprites are drawn **white**: the game tints them (pastels for motes and dust, gold for thread fragments). A coloured sprite gets tinted too. Add or remove sprites by editing the JSON list |
| Seam sounds | `sounds/seam/<name>.ogg`, entries in `sounds.json` | 11 files (see `TODO-MANUAL.md`). How far each carries is its `attenuation_distance` in `sounds.json` |
| Seam outline, threads, god-rays, rings | none | Drawn by code (`client/seam/SeamRenderer`), no texture to swap. Colours and sizes are constants at the top of that class |

Note that 1.21 data folders are **singular**: `recipe/`, `advancement/`, `loot_table/`, `structure/`.

`data/skyseam/structure/gametest/` holds GameTest templates (`empty.nbt` from `tools/structures/make_gametest_empty.py`).
They are test scaffolding, not world structures. Structure checks skip them, and there is nothing in that folder to hand-build.

## Source files (outside the resources folder)

| Kind | Folder | Notes |
|---|---|---|
| Blockbench sources | `art/models/<name>.bbmodel` | `geckolib_model` format. Edit here, then export the geo and animation JSON over the resources files |
| Texture generators | `tools/textures/` | Pillow + numpy scripts that share one palette file. Run with `tools/.venv/Scripts/python.exe` |
| Sound generators | `tools/audio/` | `synth.py` (writes mono OGG) and `check.py` (validates files and `sounds.json`) |
| Structure generators | `tools/structures/` | Write `.nbt` files plus isometric previews (`preview.py`) |
| Reference images | `reference/` | Style references only (spec §3) |

Folders are created by the milestone that first needs them. This file is the contract they follow.
