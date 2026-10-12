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
| The Lantern-Sun | `textures/environment/lantern_sun.png` | Replaced in the M3 rebuild (below) |
| Sky, fog, the Veil, the Hush's dusk | none | Drawn by code (`client/halcyon/HalcyonSky`, `HalcyonFog`). The phase palettes are at the top of `HalcyonSky`. The flat cloud texture is gone: the clouds are 3D boxes drawn by `HalcyonClouds` |
| Biome colours | `data/skyseam/worldgen/biome/<biome>.json` (`the_spindle`, `the_hush`, `cirrus_reefs`, `petalwash_meadows`, `mirror_shoals`, `wreckfields`, `underbloom`) | Sky, fog, water, water fog, grass and foliage colours. Written by `tools/worldgen/make_halcyon_data.py` |
| The dimension | `data/skyseam/dimension_type/halcyon.json`, `data/skyseam/dimension/halcyon.json` | Written by `tools/worldgen/make_halcyon_data.py`. The generator and biome source are code (`skyseam:halcyon`) |
| Physics pack | `data/skyseam/dimension_physics/halcyon.json` | Sable's format (DEVIATIONS D22). Gravity, pressure curve and drag are the numbers to tune |
| Sundered Obelisk | `data/skyseam/structure/sundered_obelisk.nbt` | Replaced in the M3 rebuild by five slices (below) |
| Mirror Sea sounds | `sounds/mirror_sea/rebound.ogg`; `mirror_sea.splash` names a vanilla event in `sounds.json` | Mono. To give the splash its own file, change its `sounds.json` entry to name `skyseam:mirror_sea/splash` and add the `.ogg` |

Note that 1.21 data folders are **singular**: `recipe/`, `advancement/`, `loot_table/`, `structure/`.

`data/skyseam/structure/gametest/` holds GameTest templates (`empty.nbt` from `tools/structures/make_gametest_empty.py`).
They are test scaffolding, not world structures. Structure checks skip them, and there is nothing in that folder to hand-build.

### Added or replaced in the M3 rebuild

| Asset | Path under `assets/skyseam/` (or `data/skyseam/`) | Notes for a replacement |
|---|---|---|
| Rock and masonry textures | `textures/block/{duskstone,cobbled_duskstone,shoalrock,cliffstone,ochre_cliffstone,umber_cliffstone,mossy_cliffstone,hornstone,stillstone}[_1…_3].png`, `textures/block/{duskstone_bricks,cracked_duskstone_bricks,mossy_duskstone_bricks,duskstone_tiles,polished_duskstone,chiseled_duskstone,runed_duskstone,runed_duskstone_glow,duskstone_pillar,duskstone_pillar_top,smooth_shoalrock,stillstone_bricks,mossy_stillstone_bricks,chiseled_stillstone_bricks,stillstone_pillar,stillstone_pillar_top}.png` | 16×16. A name ending `_1`, `_2`, `_3` is a variant the blockstate picks at random by position: keep the same count or edit `SkyseamBlockStates`. `runed_duskstone_glow` is drawn full-bright over the block: transparent except the glyphs. Painted by `tools/textures/make_halcyon_terrain_blocks.py` (the Prismite lamp moved to the M3 follow-up row below) |
| Wood, leaves, mushrooms and plants | `textures/block/{elder_log,elder_log_top,elder_log_glow,duskwood_log,duskwood_log_top}.png`, `textures/block/{elder_leaves,duskwood_leaves,spirepine_needles,pale_blossom_leaves}[_1].png`, `textures/block/{ember_cap,parasol_cap,glowcap,bell_cap,giant_mushroom_stem}[_inside].png`, `textures/block/{ember_shroom,glow_shroom,skylupine_top,skylupine_bottom,hanging_ivy,hanging_ivy_tip,wisteria,wisteria_tip,bloomcoral_pink,bloomcoral_violet,bloomcoral_white}.png` | 16×16. `elder_log_glow` is drawn full-bright over the bark's sides. A mushroom block shows its `_inside` texture on faces touching another mushroom block and on a cap's underside (its gills). Painted by `tools/textures/make_halcyon_flora_blocks.py` |
| Sky bodies and effects | `textures/environment/{lantern_sun,sun_corona,halo,companion_star,planet,planet_shadow,pale_moon,ringed_moon,shard_moon,moon_debris,ember_moon,star_glint,meteor,nebula,nebula_teal,swirl}.png` | Drawn as billboards and tinted by code (`client/halcyon/sky/`). `lantern_sun`'s bright core spans the middle 24 of its 64 pixels. `pale_moon` is eight 64×64 phases side by side, in vanilla's order (full first). `planet_shadow` is the planet's night side, dark on its left; the code turns it away from the sun. `moon_debris` is four lumps, 2 by 2. `nebula*` tile left to right. Painted by `tools/textures/make_sky_textures.py`. The old `halcyon_clouds.png` and `rift_eye.png` are gone |
| Wisp particle | `textures/particle/wisp.png`, `particles/wisp.json` | Drawn full-bright and tinted by code. Now 32×32 (M3 follow-up, below) |
| Biome colours and wisps | `data/skyseam/worldgen/biome/<biome>.json`, now also `sunreach_cliffs`, `capwood`, `elderwood` | Written by `tools/worldgen/make_halcyon_data.py`. The `particle` entry is how often a biome gives off wisps at night |
| The dimension type | `data/skyseam/dimension_type/halcyon.json` | No `fixed_time`: the Lantern-Sun's clock sets the day (DECISIONS K82) |
| Sundered Obelisk | `data/skyseam/structure/sundered_obelisk/slice_0.nbt` … `slice_4.nbt` | Five slices, each at most 48 on every side, stacked 48 apart. Keep the markers in slice 0 (`docs/STRUCTURE-MARKERS.md`). Made by `tools/structures/make_sundered_obelisk.py`. The old single `sundered_obelisk.nbt` is gone |
| Mixin config | `src/main/resources/skyseam.mixins.json` | One mixin, `LevelTimeMixin` (K82) |

### Added or replaced in the M3 follow-up

| Asset | Path under `assets/skyseam/` | Notes for a replacement |
|---|---|---|
| Prismite | `textures/block/{small_prismite_bud,large_prismite_bud,prismite_cluster}[_1].png`, `textures/block/prismite_lamp.png` | 16×16. The buds and clusters are cross models standing on the bottom row; `_1` is a second layout the blockstate picks at random. Painted by `tools/textures/make_halcyon_glow_blocks.py`, which now owns the lamp too |
| Glow moss | `textures/block/glow_moss[_1…_3].png` and `textures/block/glow_moss_spores[_1…_3].png` | Four growths for one face, picked at random per block. Each `_spores` texture is the same growth's spore pixels alone, transparent elsewhere, drawn full-bright over it: keep each pair aligned |
| Elder bark and veins | `textures/block/elder_log[_1].png`, `textures/block/elder_log_glow[_1…_5].png` | Two barks and six full-bright vein overlays; each of the six Elder models shows a different pair on each side. **Every vein must cross the top and bottom edges through pixels 7 and 8 (two wide)**, so the veins of a trunk join whichever variants stand together. Keep veins three pixels or more from the side edges |
| Sky effects | `textures/environment/aurora.png`, `textures/environment/star_dust.png` | The aurora strip runs teal at its foot (bottom) to rose at the top and tiles left to right; its alpha is its brightness. `star_dust` is the faint stars' dot |
| Sky body cut-outs | `textures/environment/{planet,planet_shadow,pale_moon,ringed_moon,shard_moon,moon_debris,ember_moon}_mask.png` | White where the body is opaque, its colour equal to its alpha. If you repaint a body, remake its mask the same way (`make_sky_textures.py` does it) or it will show the old shape's shadow |
| Smoothing for the soft glows | `textures/environment/{halo,sun_corona,swirl,nebula,nebula_teal,aurora,meteor}.png.mcmeta` | `"blur": true` so they stay smooth stretched over the sky. The bodies keep their crisp pixels |
| Wisp particle | `textures/particle/wisp.png` | 32×32: a small bright core in a faint glow, so it no longer shows its pixels up close |

### Added with the flux vortex

| Asset | Path under `assets/skyseam/` | Notes for a replacement |
|---|---|---|
| Vortex textures | `textures/environment/{flux_tube,flux_strand,flux_glow,flux_pool,flux_crown,flux_band}.png`, and `.png.mcmeta` for the pool and crown | Pixel art in Minecraft's style: white, the brightness in the alpha in a few steps, tinted by code (`client/halcyon/vortex/VortexRenderer`), shown without smoothing. `flux_tube` (16×64) is one of the pair of vortices, a body of pixel streaks brighter towards its middle; `flux_strand` (8×64) a thin strand (cores, the strands spinning round each vortex, the crown's arcs, wisps); both run up and tile top to bottom. `flux_glow` (16×16) is a soft glow across; `flux_pool` (32×32) the pool of light at the foot with pixel rings and `flux_crown` (32×32) the crown's glow with rays (both clamped by their `.mcmeta`); `flux_band` (16×4) a band for the ripples and the crown's halos. Painted by `tools/textures/make_flux_vortex_textures.py` |
| Vortex particles | `textures/particle/{flux_mote_0..3,flux_spark,flux_dust}.png` (8×8 pixel art), `particles/{flux_mote,flux_glint,flux_spark,flux_dust}.json` | White, tinted by code. The mote twinkles through its four frames as it climbs the column; `flux_glint` uses the same frames for sparkles falling from the crown and light drawn in to the foot; the dust is mist rising off its foot; the spark flashes as it forms |
| Vortex sounds | `sounds/flux_vortex/{loop,appear,dissipate,launch}.ogg`, entries in `sounds.json` | `loop` must loop seamlessly (6 s): the client plays it from the column's nearest point. `appear` (4 s) and `dissipate` (4 s) are played as it forms and unravels, `launch` when it throws something. Synthesized by `tools/audio/synth.py flux_vortex` |
| Springbloom model | `geo/block/springbloom.geo.json`, source `art/models/springbloom.bbmodel` | 128×64 box UV. **Keep the bone names**: `root` (turned and scaled by code), `bulb` (squishes; scaled about its foot), `stalks` (moved down as it squishes), `stalk_0..3` and `stalk_0..3_upper`, `tip_0..3`, `leaf_0..4`, `leaf_inner_0..4`, `bulge_0..3`. The bulb's top is 8 pixels up; the renderer draws the model at 1.25 times, to meet the block's 10-pixel collision box |
| Springbloom animations | `animations/block/springbloom.animation.json` | Clips `idle` (looped), `squish` and `launch` (triggered by code). Written by `tools/models/export_animations.py` from the `.bbmodel` |
| Springbloom textures | `textures/block/springbloom.png`, `springbloom_glowmask.png` (same size), `springbloom_particle.png` (break particles), `textures/item/springbloom.png` (16×16 icon), `textures/particle/springbloom_spore_{0,1}.png` (8×8, two twinkle frames), `particles/springbloom_spore.json` | Painted by `tools/textures/make_springbloom_textures.py`, which reads the box-UV layout from the `.bbmodel`; after repainting, embed the texture in the `.bbmodel` again so Blockbench shows it. The glowmask lights the leaf veins, the bulb, the upper stalks and the tips |
| Springbloom sounds | `sounds/springbloom/{squish_1,squish_2,squish_3,launch_1,launch_2}.ogg`, entries in `sounds.json` | `squish_*` (0.3 to 0.6 s, a rude rubbery raspberry) when something lands on it; `launch_*` (1.1 s, a boing and a whoosh) when something jumps off it. Synthesized by `tools/audio/synth.py springbloom` |
| Floating rocks | none | Real blocks drawn by code: Duskstone, cobbled Duskstone, Shoalrock, Hornstone, Cliffstone and now and then a Prismite lamp, some topped with moss or grass |

## Source files (outside the resources folder)

| Kind | Folder | Notes |
|---|---|---|
| Blockbench sources | `art/models/<name>.bbmodel` | `geckolib_model` format. Edit here, then export the geo and animation JSON over the resources files |
| Texture generators | `tools/textures/` | Pillow + numpy scripts that share one palette file. Run with `tools/.venv/Scripts/python.exe` |
| Sound generators | `tools/audio/` | `synth.py` (writes mono OGG) and `check.py` (validates files and `sounds.json`) |
| Structure generators | `tools/structures/` | Write `.nbt` files plus isometric previews (`preview.py`). `structure_lib.py` writes the same bytes for the same structure |
| World data generator | `tools/worldgen/make_halcyon_data.py` | Writes the Halcyon's biome, dimension and physics JSON |
| Block datagen | `src/main/java/com/selluastar/skyseam/datagen/` | Run by `gradlew runData`; writes `src/generated/resources/` |
| Reference images | `reference/` | Style references only (spec §3). The author's round-two images (third-party screenshots) stay in the git-ignored `reference/local/` (DECISIONS K90) |
| Texture kit | `tools/textures/pixelkit.py` | The shared pixel-art helpers: hue-shifted ramps, lit stones, bricks, strata, bark, leaves, caps |

Folders are created by the milestone that first needs them. This file is the contract they follow.
