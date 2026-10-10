# Asset paths and the swap rule

**Swap rule (spec §1 rule 4, §20):** every texture, model, animation, sound, music track, structure and lang file is
found **by name** at the paths below. Replacing a file with another file of the same name must work with **no code
change**. If it doesn't, that is a bug: report it.

All paths are under `src/main/resources/`, except the block files `runData` writes into `src/generated/resources/`
(see "Added in M3"). The namespace is `skyseam`.

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
| Seam particles | `particles/{seam_mote,seam_spark,thread_snap,scar}.json`, sprites `textures/particle/<id>_<n>.png` | Sprites are drawn **white**: the game tints them (pastels for motes and dust, gold for thread fragments). `seam_spark_3` and `_4` are the square voxel bits shed from the border. A coloured sprite gets tinted too. Add or remove sprites by editing the JSON list |
| Seam sounds | `sounds/seam/<name>.ogg`, entries in `sounds.json` | 11 files, plus the closing crackle in `sounds/closing/close.ogg` (see `docs/CLOSING-AUDIO.md`). How far each carries is its `attenuation_distance` in `sounds.json` |
| Seam outline, rim blocks, fragments, cracks, threads, god-rays, rings | none | Drawn by code (`client/seam/SeamRenderer`, layout in `SeamDecor`), no texture to swap. Colours and sizes are constants at the top of those classes. Sky shards and the hole's walls reuse `interior.png` |

### Added in M2

| Asset | Path under `assets/skyseam/` (or `data/skyseam/`) | Notes for a replacement |
|---|---|---|
| Harmonic Aperture model | `geo/block/harmonic_aperture.geo.json`, source `art/models/harmonic_aperture.bbmodel` | 64×64 box UV. **Keep the bone names**: `ring`, `core` and `gems` (animated); `gem_flying_lit`, `gem_altitude_lit`, `gem_site_lit` (shown while that rule holds); `needle` (turned by code to point at the site: its tip must lie along the model's −z, pivot on the needle's centre) |
| Harmonic Aperture animations | `animations/block/harmonic_aperture.animation.json` | Clips `idle`, `spin_up`, `charged`, `cooldown` (looped by code). Written by `tools/models/export_animations.py` from the `.bbmodel` |
| Harmonic Aperture textures | `textures/block/harmonic_aperture.png`, `harmonic_aperture_glowmask.png` (same size), `harmonic_aperture_particle.png` (break particles) | The glowmask lights the core, the ring's thread, the lit gems and the needle's tip |
| Item icons | `textures/item/harmonic_aperture.png`, `textures/item/skychart.png` | 16×16. The Aperture's item model is 2D for now (`models/item/harmonic_aperture.json`) |
| Aperture screen and HUD sheet | `textures/gui/aperture.png` (256×256) | Fixed layout read by code: the 176×206 panel at (0, 0), the chart slot at (151, 91); the three 9×9 tick icons unlit at (176, 0) and lit at (176, 9); the 9×9 arrow at (176, 18), pointing up; the 84×22 HUD frame at (0, 208) |
| Skychart screen | `textures/gui/skychart.png` (256×256) | The round 200×200 chart at (0, 0), north up |
| Aperture and Skychart sounds | `sounds/aperture/{charge,ready,mount}.ogg`, `sounds/skychart/unfold.ogg` | Mono. `charge` is a loop; its pitch rises with the charge |
| Blockstate and models | `blockstates/harmonic_aperture.json`, `models/block/harmonic_aperture.json`, `models/item/{harmonic_aperture,skychart}.json` | The block model only gives the particle texture; GeckoLib draws the block |
| Loot table | `data/skyseam/loot_table/blocks/harmonic_aperture.json` | Keeps the owner (`skyseam:owner`) on the dropped item |
| Placeholder Halcyon | `data/skyseam/dimension_type/halcyon.json`, `data/skyseam/dimension/halcyon.json` | Replaced in M3 (below) |

### Added in M3

| Asset | Path under `assets/skyseam/` (or `data/skyseam/`) | Notes for a replacement |
|---|---|---|
| Halcyon block textures | `textures/block/{stillstone,stillstone_bricks,mossy_stillstone_bricks,chiseled_stillstone_bricks,stillstone_pillar,stillstone_pillar_top,stillstone_glass,cloud,rose_cloud,dusk_cloud,petal_carpet,cloudmoss,glow_moss,lantern_vine,lantern_vine_tip,small_prismite_bud,large_prismite_bud,prismite_cluster}.png` | 16×16. `stillstone_glass` and the cloud blocks are drawn translucent, so their alpha counts. The slab, stairs and wall use `stillstone_bricks` |
| Halcyon block files | `src/generated/resources/assets/skyseam/{blockstates,models/block,models/item}/<block>.json`, `src/generated/resources/data/skyseam/loot_table/blocks/<block>.json`, block tags under `src/generated/resources/data/{minecraft,skyseam}/tags/block/` | Written by `gradlew runData` from `src/main/java/com/selluastar/skyseam/datagen/`. Replace textures freely. To change a model, a loot table or a tag, edit the datagen class and run `runData`: a hand-written file at the same path would be a second copy of it |
| The Lantern-Sun | `textures/environment/lantern_sun.png` | A soft orb on transparent, drawn facing the camera. Its bright core must span the middle 9 of every 32 pixels of half-width: the code sizes the orb from that. Tinted white-gold to ember by code |
| Cloud layer | `textures/environment/halcyon_clouds.png` | Tiles in both directions. Drawn white and tinted by the light, at Y 384, fading with distance |
| Sky, fog, the Veil, the Hush's dusk | none | Drawn by code (`client/halcyon/HalcyonSky`, `HalcyonFog`). The phase palettes are at the top of `HalcyonSky` |
| Biome colours | `data/skyseam/worldgen/biome/<biome>.json` (`the_spindle`, `the_hush`, `cirrus_reefs`, `petalwash_meadows`, `mirror_shoals`, `wreckfields`, `underbloom`) | Sky, fog, water, water fog, grass and foliage colours. Written by `tools/worldgen/make_halcyon_data.py` |
| The dimension | `data/skyseam/dimension_type/halcyon.json`, `data/skyseam/dimension/halcyon.json` | Written by `tools/worldgen/make_halcyon_data.py`. The generator and biome source are code (`skyseam:halcyon`) |
| Physics pack | `data/skyseam/dimension_physics/halcyon.json` | Sable's format (DEVIATIONS D22). Gravity, pressure curve and drag are the numbers to tune |
| Sundered Obelisk | `data/skyseam/structure/sundered_obelisk.nbt` | At most 48 on every side. Keep the markers and see where it is placed in `docs/STRUCTURE-MARKERS.md`. Made by `tools/structures/make_sundered_obelisk.py` |
| Mirror Sea sounds | `sounds/mirror_sea/rebound.ogg`; `mirror_sea.splash` names a vanilla event in `sounds.json` | Mono. To give the splash its own file, change its `sounds.json` entry to name `skyseam:mirror_sea/splash` and add the `.ogg` |

Note that 1.21 data folders are **singular**: `recipe/`, `advancement/`, `loot_table/`, `structure/`.

`data/skyseam/structure/gametest/` holds GameTest templates (`empty.nbt` from `tools/structures/make_gametest_empty.py`).
They are test scaffolding, not world structures. Structure checks skip them, and there is nothing in that folder to hand-build.

## Source files (outside the resources folder)

| Kind | Folder | Notes |
|---|---|---|
| Blockbench sources | `art/models/<name>.bbmodel` | `geckolib_model` format. Edit here, then export the geo and animation JSON over the resources files |
| Texture generators | `tools/textures/` | Pillow + numpy scripts that share one palette file. Run with `tools/.venv/Scripts/python.exe` |
| Sound generators | `tools/audio/` | `synth.py` (writes mono OGG) and `check.py` (validates files and `sounds.json`) |
| Structure generators | `tools/structures/` | Write `.nbt` files plus isometric previews (`preview.py`). `structure_lib.py` writes the same bytes for the same structure |
| World data generator | `tools/worldgen/make_halcyon_data.py` | Writes the Halcyon's biome, dimension and physics JSON |
| Block datagen | `src/main/java/com/selluastar/skyseam/datagen/` | Run by `gradlew runData`; writes `src/generated/resources/` |
| Reference images | `reference/` | Style references only (spec §3) |

Folders are created by the milestone that first needs them. This file is the contract they follow.
