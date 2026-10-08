# TODO-MANUAL

Everything the author adds by hand, seeded from spec §22. One line per item:
`[TAG] what | where the file goes | now: what the game does until then | owner`.
Claude Code keeps this current at every milestone. **No item blocks a build or a test.**

"Planned (Mx)" means the stub or first pass is not built yet; it arrives in that milestone.

## [MANUAL]: guaranteed, "I'll add this later manually"

- [MANUAL] FTB Quests chapters, quests and rewards | the pack's FTB Quests folder, made in the FTB editor from the ids in spec §17 | now: no chapter file (decision 9); advancement, stat and tag ids planned (M11) | owner: author
- [MANUAL] Final Seam reveal sounds (11: `hairline`, `crack`, `thread_snap_1`…`_5`, `hum` (a loop), `ring_pulse`, `crossing`, `mend`) | `assets/skyseam/sounds/seam/<name>.ogg`, mono for positional sound. Keep `hum.ogg` loopable | now: synthesized by `tools/audio/synth.py` (M1), checked by `tools/audio/check.py` | owner: author
- [MANUAL] Final sound design for the other hero moments: Echo Sounder (pulse, beam, echoes), whale song (4), Cantor's six bell notes, boss cries, Aperture charge | `assets/skyseam/sounds/<group>/<name>.ogg`. If the `sounds.json` entry names a vanilla event, change it to name your file | now: planned synthesized files and vanilla stand-ins under the final names (M2, M5–M8) | owner: author
- [MANUAL] Final art for the Seam interior sky | overwrite `assets/skyseam/textures/entity/seam/interior.png` (2:1, tiles left to right, horizon at 53% height) and `interior_glints.png` (glints on black, drawn additively) | now: first pass by `tools/textures/make_seam_textures.py` (M1) | owner: author
- [MANUAL] Final art for the other hero pieces: Harmonic Aperture, Skywright's Keystone, Almanac, Echo Sounder held model, the three boss textures | edit `art/models/<name>.bbmodel` and export over the same names, or overwrite the texture (see `docs/ASSET-PATHS.md`) | now: planned first-pass models and textures (M2, M4, M6–M8) | owner: author
- [MANUAL] (optional) Polish on the hero structures: Conservatory Spire, Sundered Obelisk, Standing Arch | build in creative, save with a structure block, overwrite `data/skyseam/structure/<name>.nbt` and keep the markers in `docs/STRUCTURE-MARKERS.md` | now: planned generated `.nbt` files (M3, M6, M9) | owner: author
- [MANUAL] Playtest tuning: difficulty, Tide timers, spawn rates, boss health, journey length | the config file or data pack JSON | now: the spec's numbers as defaults (from each milestone) | owner: author
- [MANUAL] Real-hardware checks: shader packs, performance, multiplayer, dedicated server | send logs and screenshots back | now: headless server boots and GameTests only (Step 0: dependencies boot headless ✓) | owner: author
- [MANUAL] Mod identity: icon, page art, screenshots, trailer, changelog | `src/main/resources/logo.png` and the mod page | now: plain placeholder `logo.png` (pastel disc with a white seam line, made by `tools/textures/make_placeholder_logo.py`) | owner: author
- [MANUAL] License check and publishing (Sable is PolyForm Shield; Aeronautics art is all rights reserved; Skyseam's own license field is a placeholder, DECISIONS K1) | the author | now: license notes in spec §23 | owner: author

## [SUPPORT ONLY]: optional, the hooks are built and the content is added if needed

The music slots, manager and how-to are built in M4. Until then nothing plays. See `docs/ADDING-MUSIC.md`.

- [SUPPORT ONLY] Music slot tide_calm | `assets/skyseam/sounds/music/tide_calm.ogg` | now: silent (manager planned M4) | see docs/ADDING-MUSIC.md
- [SUPPORT ONLY] Music slot tide_prismfall | `assets/skyseam/sounds/music/tide_prismfall.ogg` | now: silent (M4) | see docs/ADDING-MUSIC.md
- [SUPPORT ONLY] Music slot tide_gale | `assets/skyseam/sounds/music/tide_gale.ogg` | now: silent (M4) | see docs/ADDING-MUSIC.md
- [SUPPORT ONLY] Music slot tide_bloom | `assets/skyseam/sounds/music/tide_bloom.ogg` | now: silent (M4) | see docs/ADDING-MUSIC.md
- [SUPPORT ONLY] Music slot tide_stillness | `assets/skyseam/sounds/music/tide_stillness.ogg` | now: silent (M4) | see docs/ADDING-MUSIC.md
- [SUPPORT ONLY] Music slot boss_regent | `assets/skyseam/sounds/music/boss_regent.ogg` | now: silent (M4, fight in M6) | see docs/ADDING-MUSIC.md
- [SUPPORT ONLY] Music slot boss_skyray | `assets/skyseam/sounds/music/boss_skyray.ogg` | now: silent (M4, fight in M7) | see docs/ADDING-MUSIC.md
- [SUPPORT ONLY] Music slot boss_cantor | `assets/skyseam/sounds/music/boss_cantor.ogg` | now: silent (M4, fight in M8) | see docs/ADDING-MUSIC.md
- [SUPPORT ONLY] Per-phase boss stems boss_<boss>_p1/_p2/_p3 (9, optional) | `assets/skyseam/sounds/music/boss_<boss>_p<n>.ogg` | now: silent; the main theme plays through if a stem is missing (M4) | see docs/ADDING-MUSIC.md
- [SUPPORT ONLY] Optional music slot anchorage | `assets/skyseam/sounds/music/anchorage.ogg` | now: silent (M4) | see docs/ADDING-MUSIC.md
- [SUPPORT ONLY] Optional music slot spire | `assets/skyseam/sounds/music/spire.ogg` | now: silent (M4) | see docs/ADDING-MUSIC.md
- [SUPPORT ONLY] Optional music slot seam_open | `assets/skyseam/sounds/music/seam_open.ogg` | now: silent (M4) | see docs/ADDING-MUSIC.md
- [SUPPORT ONLY] Optional music slot hush_stinger | `assets/skyseam/sounds/music/hush_stinger.ogg` | now: silent (M4) | see docs/ADDING-MUSIC.md
- [SUPPORT ONLY] Other languages | `assets/skyseam/lang/<locale>.json` | now: all text goes through lang keys in `en_us.json` | owner: author or translators

No voice acting is planned.
