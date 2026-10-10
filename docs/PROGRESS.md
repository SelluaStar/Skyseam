# Progress

The per-milestone report (spec §1 rule 12; `docs/STATUS.md` points here). For each milestone: what was built, how it
was verified, what only the author can check, what is still a stub, and known problems.

---

## Step 0: environment check (2026-10-08)

**Built**
- Inputs:
  - `docs/Skyseam-Build-Spec.pdf` + generated `docs/Skyseam-Build-Spec.md`;
  - `libs/` Fealty jars (0.1.0, API 1.3.0);
  - read-only `vendor/fealty-source/` snapshot;
  - `reference/` (the 4 images, renamed to the spec's order).
- A Gradle probe build with **no mod code**: wrapper 8.14.3, ModDevGradle 2.0.148, pinned dependencies, four runs (client, server, gameTestServer, data) and a `syncRunMods` task that installs Create, Aeronautics, Sable and Fealty into `run/mods/`.
- A Python venv in `tools/.venv` with numpy, Pillow, soundfile and nbtlib.
- Docs: `ENVIRONMENT.md`, `DEVIATIONS.md`, `DECISIONS.md`, `ASSET-PATHS.md`, `ADDING-MUSIC.md`, `STATUS.md`, this file, and `TODO-MANUAL.md`.

**Verified (actually run)**
- `gradlew build` ✓.
- `runGameTestServer` loads all 14 mods ✓. It stops on "no test functions", which is expected with no tests.
- Sable's own GameTests headless: Rapier native loads, 5/5 required pass ✓.
- `runServer` reaches `Done` ✓.
- `runData` ✓.
- `runClient` reaches title-screen loading ✓.
- Every external id and API the spec relies on, checked in the pinned jars (table in ENVIRONMENT.md).
- Blockbench headless: create, edit, validate, validate_animations, export and render ✓.
- OGG Vorbis write, WebP read and NBT round trip ✓.

**Untested / needs the author**
- Nothing in the game window was seen. `runClient` was only checked through its log.
- Whether the renamed physics staff works in survival (static scan only, planned GameTest in M5).

**Known problems (handled in M-1)**
- Gradle reports success even when the game-test server fails to start. Milestone checks must read the log.
- Gradle doesn't forward stdin, so `runServer` can't be told to `stop`. M-1 adds a dev-only auto-stop.
- The Fealty 1.3.0 API is uncommitted in the Fealty repo (DEVIATIONS D3). Recommended: commit it there.

**Stubs:** none yet. No mod code exists. M-1 builds the empty mod, `SableBridge`, `ExternalIds` and the placeholder `logo.png`.

**Git:** Step 0 committed to GitHub `SelluaStar/Skyseam`. `main` has no Fealty files. `with-fealty` = `main` plus `libs/` and `vendor/` (DECISIONS K7).

---

## M-1: environment and skeleton (2026-10-08)

**Built**
- The empty Skyseam mod (`com.selluastar.skyseam.Skyseam`, mod id `skyseam`, version 0.0.1).
  - `neoforge.mods.toml` (generated from `src/main/templates`) declares as **required**: Create ≥ 6.0.10, Simulated and Aeronautics ≥ 1.3.2, Sable [2.0.6, 3.0.0), GeckoLib ≥ 4.9.3 and Fealty ≥ 0.1.0. KubeJS and FTB Quests are not listed, since there is no code link to them.
- **ExternalIds** (`external/ExternalIds.java`): every Create, Simulated and Sable id and class name, plus the pinned versions. At startup it logs the loaded versions and warns on any difference.
- **SableBridge** (`external/SableBridge.java`), the only class that calls Sable:
  - `isActive`;
  - `subLevelCount`;
  - `containing` (via Sable Companion);
  - `assemble` (via `SubLevelAssemblyHelper.assembleBlocks`).
- **FealtyCompat** (`compat/fealty/`) reads and logs the loaded Fealty API version (1.3.0). No other Fealty use before M10.
- **DevBootCheck**: `gradlew runServer -PbootCheck` makes the headless server log `Skyseam boot check passed` and stop cleanly.
- **5 GameTests** (`gametest/SkyseamGameTests.java`):
  - required mods present and Fealty API 1.3.0;
  - loaded versions equal the pins;
  - the staff and both Create items resolve, and the staff is a `PhysicsStaffItem`;
  - Sable physics active and a plain block is in no sub-level;
  - a 3×1×3 plank platform assembles into a sub-level (world spots become air, sub-level count +1, `containing` finds it by UUID).
- Generated assets:
  - GameTest template `data/skyseam/structure/gametest/empty.nbt` (`tools/structures/make_gametest_empty.py`);
  - placeholder `logo.png` (`tools/textures/make_placeholder_logo.py`).
- **`tools/verify_milestone.py`**: the reusable "done when" check that judges logs, not exit codes.

**Verified (actually run)**
`tools/verify_milestone.py`, all PASS:

| Step | Result |
|---|---|
| build | BUILD SUCCESSFUL |
| data | data gatherer ran for skyseam |
| gametest | All 5 required tests passed (783 ms) |
| server | `Done (0.605s)!`, then `Skyseam boot check passed … 15 mods loaded`, clean exit 0 |

The startup log lists every pinned dependency at its pinned version with no mismatch warning, plus `Skyseam found Fealty API 1.3.0`.
Placeholder logo viewed as a PNG.

**Untested / needs the author**
- Nothing was seen in a game window. Author's in-game check:
  1. Start the client (`gradlew runClient`, or your pack with `skyseam-0.0.1.jar` plus the pinned mods).
  2. Mods screen: Skyseam 0.0.1 is listed with the placeholder icon and description.
  3. Create a world and load in. There is no Skyseam content yet, so just confirm nothing breaks.
  4. Send `run/logs/latest.log`, or your pack's `logs/latest.log`, if anything looks wrong. Search it for `Skyseam`: there should be seven `Skyseam dependency …` lines and `Skyseam found Fealty API 1.3.0`, with no WARN or ERROR.
- The logo is a placeholder by design (TODO-MANUAL).

**Known problems:** none found.

**Stubs:** no gameplay yet, by design. M0 is the ship-crossing spike.

**Git:** commit on `main`, then `with-fealty` brought up to date (main + `libs/` and `vendor/`). Both pushed.

---

## M0: ship-crossing spike (2026-10-08)

**Question (spec §19):** can a Sable ship be moved between two dimensions, by route A (Sable save/load) or route B
(copy the blocks)? **Answer: yes, by both. Route A is chosen** (DECISIONS K19, K20). The §23 fallback is not needed.

**Built**
- `SableBridge.moveBySaveAndLoad` (route A).
  - Snapshots the ship with `toData`.
  - Rewrites the saved tag: arrival pose, velocity × 0.5, a free plot slot if the saved one is taken, block entities, ticks and rotation point shifted to the new plot, and sections re-indexed for the target's floor.
  - Loads it with `fullyLoad`, and only then removes the original.
- `SableBridge.moveByCopyingBlocks` (route B, the backup): copies blocks and block entities, re-assembles, and restores velocity × 0.5.
- `Ship`, a handle class, so no package outside `external/` touches a Sable type. The M-1 test now uses it too.
- 3 GameTests in `gametest/ShipCrossingSpikeTests.java`:
  - route A round trip (with a ship already in the End taking a plot slot);
  - route B round trip;
  - route A timing with a 1,152-block ship.
- Temporary operator command `/skyseam spike ship` and `/skyseam spike cross <dimension> [route_a|route_b]` (`dev/SpikeCommands.java`), for the in-game test below.

**Verified (actually run)**
`tools/verify_milestone.py`: build ✓, data ✓, **All 8 required tests passed** ✓ (run twice), server boot ✓.
Each spike test checks, after every leg, that the ship has all 11 blocks, the chest still holds its 3 diamonds, and the Create shaft keeps its block entity:

| Route | Leg | Result |
|---|---|---|
| A | overworld → End | ✓ same ship id; velocity (1.07, 0, 0) → (0.48, 0, 0); plot slot (0,3) was taken, so slot (0,9) was used |
| A | 30 ticks in the End | ✓ still there, falling under gravity as expected |
| A | End → overworld | ✓ same ship id |
| B | overworld → End → overworld | ✓ new id each leg; velocity 1.07 → 0.54; arrives level (rotation not kept) |
| A, 1,152 blocks | overworld → End | assembled in 45–55 ms, **crossed in 32–34 ms** |

How route A was made to work (all found from failing runs, see DEVIATIONS D12):
1. block entities first loaded into air because their absolute plot coordinates pointed at the old plot;
2. the ship vanished after a slot change because the pose's rotation point still pointed at the old plot;
3. blocks landed 64 blocks too high because the End's floor is at y 0, not −64.

**Untested / needs the author** (GameTests can't build a real Aeronautics ship):
- Real Aeronautics ships: propellers, envelopes, seats, ropes, Create kinetics running across the crossing.
- Ships made of several linked bodies (Sable "loading dependencies"; Skyseam passes none yet).
- The client side: whether the ship renders right away after crossing, and how the player is placed.
- Players riding seats (only the command's own player is moved, by teleport).

**Author's in-game test (M0)**
1. Open a creative world with cheats on, built with this jar plus the pinned mods.
2. Build a small Aeronautics ship (a few blocks, a propeller or two, a seat, a chest with items) and get it into the air.
3. Stand on or near it (within 48 blocks) and run `/skyseam spike ship`. It should name the ship and its block count.
4. Run `/skyseam spike cross minecraft:the_end`. You and the ship should appear in the End at the same x and z, with a chat line giving the block count before and after and the time in ms.
5. Check: every block is there, the chest items are kept, the propellers and controls still work, you can fly it, and it looks right without relogging.
6. Run `/skyseam spike cross minecraft:overworld` to come back. Then try `… cross minecraft:the_end route_b` once to compare.
7. Send `logs/latest.log`, the chat lines, and screenshots of anything broken: missing blocks, parts that stopped working, an invisible ship, a ship falling apart, or the player landing in the wrong place.

**Known problems**
- Route B loses the ship's rotation and id by design. It is only a backup.
- Linked multi-body ships are not handled yet (M2).
- The spike command is temporary, and its feedback is not in the lang file.

**Stubs:** none. This milestone is a spike.

**Git:** commit on `main`, `with-fealty` updated, both pushed.

---

## M0 follow-up: fixes from the author's in-game test (2026-10-08)

The author crossed real Aeronautics ships with `/skyseam spike cross` and reported three problems. All three are
fixed, together with related problems found while fixing them. The crossing is now one Skyseam call,
`transfer/ShipTransfer.begin`, which the spike command uses. It will also be the Seam's ship transfer in M2.

| Reported | Cause | Fix |
|---|---|---|
| 1. The player sometimes lands on the ground, not on the ship | The player arrived before their client knew about the ship, so the client saw no deck. The arrival chunks were also often not live yet | Riders keep their exact spot in the ship's own coordinates, and seated players are re-seated. The crossing first waits (up to 5 s) until every chunk under the arrival is live. Then a **3 s hold** keeps the ship still and puts any rider who drifts off or falls back on their spot (`CrossingHolds`), before giving the ship back 50% of its velocity |
| 2. Ship state is not kept (hot-air balloon fill resets to 0) | Aeronautics keeps balloon gas in a per-dimension `BalloonMap`, not in the ship's blocks | `AeronauticsBridge` saves each balloon in the ship (Aeronautics' own `saveBalloon`), moves it to the new plot and files it in the target dimension's map. The burners pick it up again as if their chunk had reloaded. **Also found:** entities in the ship's plot (item frames, paintings, seats, armor stands) were being lost. `PlotEntityMover` now carries them |
| 3. The ship sometimes arrives inside a wall | The ship was placed at the same x/z with no check | `ArrivalFinder` picks the **nearest spot where the ship's box plus 1 block of clearance touches no solid block, no fluid (never in water or lava) and no fire**. It searches up to 24 blocks sideways and 48 up or down; moving down counts double, so the ship would rather rise out of a hill than sink into a cave. If there is no safe spot, the crossing is refused and nothing moves |

Other related fixes:
- Route B (copy blocks) now carries balloons, plot entities and riders too.
- A failed crossing leaves the ship and riders where they were.
- The spike command reports when the ship had to be moved to a clear spot.

**Verified (actually run)**
`tools/verify_milestone.py`: all PASS, **11/11 required GameTests**, and the GameTest step passed 3 runs in a row. New tests in `gametest/ShipTransferTests.java`:
- **Safe spot.** A stone block fills the destination with water beside it. The finder picks a dry, clear spot 6 blocks away, and the test checks every block of the cleared box.
- **Full crossing, route A and route B.** The test ship carries a hung item frame (with an emerald), an armor stand on deck, a saved balloon in its plot, a chest with diamonds and a Create shaft. The End's arrival area is deliberately not pre-loaded. The test checks:
  - all 11 blocks, the chest contents and the shaft's block entity;
  - the balloon filed at the new plot position, and gone from the overworld;
  - the frame in the new plot;
  - the armor stand in the End on its deck spot;
  - the ship not moving during the hold;
  - the armor stand still aboard after release.
  Route A took 165–233 ms including waiting for fresh End chunks; route B took 4–6 ms.

**Untested / needs the author**
- Real players (GameTests can't move a real player between dimensions), seated players, and how it looks on the client.
- A *live* balloon refilling from the carried state. The test uses a saved balloon; the real burners → `loadFrom` path only runs in game.

**Known gaps (state still not carried, for the author to report if it matters)**
- Simulated ropes whose data stores absolute positions.
- Physics-staff locks.
- Sable force-load tickets and tracking points.
- Levitite crystallization progress.
- Ships made of several linked bodies.

Each gap can be added as another `PlotMoveListener` when needed.

**Author's in-game test (M0 follow-up)**
1. Same setup as before: a creative world with cheats, this jar and the pinned mods.
2. Build a ship with a hot-air balloon and fill it. Put a chest with items on it, hang an item frame on it, and put a seat on it.
3. Stand on the deck and run `/skyseam spike cross minecraft:the_end`. You should see "Preparing the crossing…" and then the "Moved ship …" line. Check:
   - you land **on the deck**, not below;
   - the ship hangs still for about 3 seconds, then carries on;
   - the **balloon is still full**;
   - the frame and chest contents are there.
4. Sit in the seat and cross back (`… cross minecraft:overworld`). You should still be seated when you arrive.
5. Stand next to a hill or a lake in the End or the overworld and cross so the ship would land inside it. It should arrive at the nearest clear, dry spot, with the chat line saying how far it moved.
6. Send `logs/latest.log` and screenshots of anything wrong.

---

## M1: the Seam (2026-10-08)

Spec §19: "Project content skeleton, the `/skyseam` debug commands, the Seam entity, renderer and particles, the full
reveal, synthesized reveal sounds. Look and sound are `[FIRST PASS]`. Done when the command opens and mends a Seam, and
the author can film it."

**Built**
- **Content skeleton.** Registries for entities, sounds and particles (`registry/`). Server config `skyseam-server.toml`
  (every Seam number the spec marks as config, at the spec's value) and client config `skyseam-client.toml` (camera
  shake and flash strength), both editable in game under Mods → Skyseam → Config. Networking (`network/`) and the English
  lang file. The M0 spike command is gone.
- **The Seam** (`seam/`): an entity that is never saved with its chunk. The server runs its whole life:
  1. **Opens** on the spec's beat timeline (DECISIONS K28): the hairline tink, the crack, five thread snaps, then the
     interior fades in.
  2. **Stays open** while someone is within 128 blocks. Until M2 that means a player; `keepOpenWhile` is the hook the
     Aperture will use.
  3. **Mends** 5 s after the last one leaves, or after 120 s regardless, by playing the reveal backwards with the
     closing chime.
  4. **Leaves a scar** for 60 s, during which no Seam opens within 48 blocks.

  While open it keeps its chunks loaded and **gently pulls ships** within 60 blocks. Open Seams are kept in world data
  and mended when the server next starts.
- **Renderer** (`client/seam/SeamRenderer`), no model and vanilla shaders only:
  - a ragged, voxel-edged fissure built from the Seam's id;
  - a white outline with depth, so it reads as blocky from an angle;
  - a pre-baked Halcyon sky behind the opening with parallax, plus a nearer layer of glints;
  - the hairline, golden threads that snap and spring back, soft god-rays;
  - the ring pulse every 6 s, and a ring of parting clouds as it cracks.
- **Particles** `seam_mote`, `seam_spark`, `thread_snap` and `scar`, the hum loop, the light camera shake, and the
  pearl-white **crossing flash**. The flash plays on `/skyseam ship cross` now and on the real crossing in M2.
- **Debug commands** (DECISIONS K37): `/skyseam seam open [<pos> [<width> <height>]]`, `seam mend [all]`, `seam list`,
  `seam clearscars`, `/skyseam ship info`, `ship cross <dimension> [route_a|route_b]`.
- **First-pass assets, all generated by script:**
  - `tools/textures/make_seam_textures.py`, using the shared palette `tools/textures/palette.py`: the interior sky
    (lavender sky, the Lantern-Sun, floating islands, teal Mirror Sea), the glints, and 13 particle sprites.
  - `tools/audio/synth.py`: the Seam's **11 sounds**.
  - `tools/audio/check.py`: checks every sound file and `sounds.json`, and draws spectrograms.
- **A capture client** (`gradlew runCaptureClient -Pscene=…`, DECISIONS K38) that plays a scripted scene in a throwaway
  world and saves screenshots, so the look can be checked without the author.

**Verified (actually run)**
- `tools/verify_milestone.py`: build ✓, runData ✓, **21/21 required GameTests** ✓, headless server boot ✓. The
  GameTests passed 3 runs in a row. The 10 new tests:
  - **The full lifecycle** (`SeamGameTests`): reveal on time → open → stays open while someone is near → mends 5 s after
    they leave → scar → no new Seam beside the fresh scar → the scar fades and the entity is removed.
  - **Mending rules:** it mends 5 s after opening when nobody is near, playing the reveal backwards from where it got
    to; and it mends at the 120 s cap even with someone near.
  - **The commands:** `/skyseam seam open … 20 18` makes one 20×18 Seam, and `/skyseam seam mend` mends it.
  - **Restart:** an open Seam survives a world-data save and load, and is turned into a scar on load.
  - **The pull:** a real Sable raft 20 blocks away moved 0.58 blocks towards an open Seam in 2 s (0.67 expected).
  - **Shape and timeline:** the same id gives the same shape, the opening only widens, the threads run top to bottom,
    and mending ends fully closed.
  - **Files** (`AssetDefinitionTests`): every registered sound has a `sounds.json` entry, a subtitle and its file. Every
    particle has its sprites. The entity has a name and the renderer's textures exist.
- `tools/audio/check.py`: all 11 files are mono 44.1 kHz with no clipping and no DC offset, and the hum loop joins
  smoothly (sample jump 3% of peak).
- **The look, as still frames from the running game** (`tools/capture/seam_reveal.json`, 31 screenshots). Contact sheet:
  [`docs/previews/m1-seam-reveal.png`](previews/m1-seam-reveal.png). The screenshots caught three bugs, now fixed:
  - the opening was blown out to plain white in daylight;
  - the overworld's clouds showed through the interior;
  - the parting-cloud puffs flew past the camera as giant blocky squares.

**Needs the author's eyes or ears** (`[FIRST PASS]`: built, not judged)
- **Motion:** how the reveal plays in motion, the shake, the flash, and the dust stream.
- **All 11 sounds.** I can measure them but not hear them.
- **Size and distance:** whether the Seam feels the right size and timing from a ship.
- **Shader packs.** Only vanilla shaders are used, but this is untested.

**Stubs and limits**
- "Someone near" is a player until the Aperture exists (M2).
- The Halcyon arrival title (beat 7) waits for the Halcyon (M3).
- The Aperture's charge, gauge and heat shimmer (beat 1) are M2.
- Tide, Almanac and landmark debug commands arrive with those features.
- The Seam is drawn up to 256 blocks away only if the server's view distance allows (DEVIATIONS D17).
- A replacement interior texture must stay 2:1 (`docs/ASSET-PATHS.md`).

**Author's in-game test (M1)**
1. Creative world with cheats, this jar and the pinned mods. Fly up at least 30 blocks and face open sky.
2. Run `/skyseam seam open`. A Seam opens in front of you, sized for your ship if one is within 48 blocks. Watch and
   listen for about 6 s:
   - the hairline and its tink;
   - the crack and its tearing sound;
   - five threads snapping with rising plucks;
   - the sky opening into the Halcyon, with a light shake.
   Then the hum, and a ring pulse every 6 s.
3. Look at it from the side and from behind: the view through it should shift as you move.
4. Run `/skyseam seam mend`. You should see the threads re-stitch and the crack close, and hear the closing chime. A
   faint scar stays for 60 s.
5. Run `/skyseam seam open` again, then fly more than 128 blocks away. It should mend 5 s later.
6. On a ship, run `/skyseam ship cross minecraft:the_end` for the white flash and the whoosh.
7. Film it if you like. Then send `logs/latest.log`, and say what to change in the look, the sounds and the timing.

### M1 follow-up: more depth and spread (2026-10-08)

The author said: "the portal looks a little 2d and also the glitch/voxel event should have more spread and have more
effects around the border". Changed (DECISIONS K42):
- **Depth:** the opening is now a recessed voxel hole. Its back is stepped, and its walls glow pink-white where they
  meet the rim and fade going in. From an angle you see into it, and the sky shows through every surface of it as one
  deep space.
- **Rim:** a ragged frame of glowing 3D voxel blocks stands out of the plane towards you and back. Some blocks sit loose
  and bob. The outline now has pink and teal colour-split ghosts.
- **Spread:**
  - stepped cracks branch out from the edge into the surrounding sky as it cracks, then hang faintly;
  - up to 72 voxel fragments break loose around the border with a spark and drift out, and some show a piece of the
    Halcyon sky;
  - more and longer spurs, plus stray voxel holes that glitch open a few blocks outside the edge.
- **Glitch:** for a few ticks at a time a band of the Seam jumps sideways and its colour split widens. Often while it
  opens and mends, rarely while it is open.
- **Particles:** sparks, voxel bits (two new square sprites in `seam_spark`) and motes shed from the whole border, not
  only the dust edge.

**Verified:** `tools/verify_milestone.py` all PASS (21/21 GameTests). New screenshots from the capture client are in
the updated contact sheet [`docs/previews/m1-seam-reveal.png`](previews/m1-seam-reveal.png); the angled and close-up
frames show the hole's depth. The glitch bursts last 3 ticks, so the still frames do not show them clearly: they need
your eyes in motion.

**Author's in-game test:** same as M1. Look at it from the side and up close to judge the depth, and watch the edge
while it cracks for the glitch bursts and the fragments breaking loose.

### M1 follow-up: gentler opening sounds (2026-10-08)

The author said: "BETTER sfx for the beginning when it first spawns rather than that weird loud sound".

**Cause:** the hairline tink was two slightly detuned bell tones at 2.6–3.2 kHz, the band the ear is most sensitive
to, with overtones up past 20 kHz. Like every synthesized sound it was normalised to its peak, which made this short,
bright sound the loudest of the set. The crack 1.5 s later had a heavy 58 Hz thump.

**Changed** (`tools/audio/synth.py`, DECISIONS K43):
- **`seam/hairline`:** a soft breath of air rising into a gentle, round glass chime (E6 over an E5 body, every
  partial under 4 kHz), with a short echo and a few faint sparkles. It is 13 dB quieter: its loudest moment fell from
  −10.9 to −24 dBFS, and its brightness (spectral centre) from 3.4 to 1.8 kHz.
- **`seam/crack`:** eight small clusters of crisp glass crackles a quarter second apart, matching the eight crack steps
  on screen, over a soft cloth-tearing swish, with a gentle C6 chime and a light low swell instead of the thump. It is
  6 dB quieter (−16.1 to −22.4 dBFS), and its share of energy above 6 kHz fell from 28% to 9%.
- The other nine Seam sounds are unchanged, byte for byte.

**Verified:** `tools/audio/check.py` passes, and build and GameTests pass. **Needs your ears:** whether the new opening
sounds right, and whether its level now sits well with the thread plucks and the hum.

### M1 follow-up: darker, crackling opening and a closing sound (2026-10-09)

The author said: "make the opening sfx seem more dark or like a crackling sound then also add a sfx for it closing".
Changed (DECISIONS K44, `tools/audio/synth.py`):
- **`seam/hairline`**, as the Seam appears: a low, uneasy buzzing drone (two voices at 55 and 58 Hz, beating slowly)
  swells out of nothing under static that thickens from a few clicks to a dense crackle, with a few deep pops.
  Subtitle: "The sky strains and crackles".
- **`seam/crack`**: eight sharp, dark cracks a quarter second apart, matching the eight crack steps on screen. Each is
  a burst of crackle over a short low thud, standing 10–19 dB above the static between them. Under them, a low groan
  sinks as the crack widens, and a muted low bell sounds as it gives.
- **New `closing/close`** (`seam.close`, in its own folder `sounds/closing/`, see `docs/CLOSING-AUDIO.md`), played the moment mending starts: air drawn back in, crackle thickening and
  tightening, and a sinking drone, all cut off at 3 s by a deep, soft thump as the crack seals. On a full mend the
  existing closing chime plays at that same moment. Subtitle: "Seam crackles shut".
- Measured: about 20–30% of each new sound's energy is in the 500–4000 Hz band, so the crackle still carries on small
  speakers that can't play the low drone. The other nine Seam sounds are unchanged.

**Verified:** `tools/audio/check.py` passes (12 files). Build and GameTests pass, and the asset test now covers 12
sounds. **Needs your ears:** the new character. The bright harp plucks of the five thread snaps and the closing chime
are unchanged and may now feel out of place next to the darker cues.

### M1 follow-up: a new crack and a better opening (2026-10-09)

The author said: "replace the cracking sound in the opening with something completely better because it sounds so
bad, and also make the opening sound better in general, but don't remove sounds already in there". All 12 sound events
stay. Rebuilt (DECISIONS K45): `seam/crack` (completely new), `seam/hairline`, `seam/thread_snap_1`…`_5` and
`seam/ring_pulse`. The hum, crossing, closing chime and closing sound are byte-for-byte unchanged.

**Measured** (I can't hear them):

| Sound | Loudest 50 ms | Brightness (spectral centre) | Energy above 6 kHz |
|---|---|---|---|
| crack | −20.4 → −17.0 dBFS | 1064 Hz → spread evenly: 15% below 150 Hz, 35% 150–600, 29% 600–2k, 19% 2–5k | 3.6% → 1% |
| hairline | −21.0 → −24.0 dBFS | 992 → 1477 Hz (glass swell instead of low static) | 3.8% → 0.3% |
| thread snap 1 | −15.8 → −22.0 dBFS | 4393 → 1764 Hz | 22.7% → 2.4% |
| ring pulse | −7.2 → −21.9 dBFS | about the same | 0% |

- **The crack's shape:** the first, big crack is its loudest moment, and each of the eight step cracks stands
  5–7 dB above the gaps between them, so they line up with the eight steps on screen.
- **The hairline:** it swells from −37 to −24 dBFS, peaks just before 1.5 s and falls away as the crack lands.
- **Checks:** `tools/audio/check.py` passes all 12 files. Build and all 21 GameTests pass.

**Needs your ears:** all of it, above all whether the new crack sounds good, and whether the hum (unchanged and now
louder than the opening around it) should come down.

### M1 follow-up: one satisfying hit and a bass line (2026-10-09)

The author said: "remove the sequential burst sounds at the beginning. just make the beginning sound more satisfying.
also add a low hum/bass to the opening." Changed (DECISIONS K46): `seam/crack` and `seam/hairline`. Every other sound,
the five thread plucks included, is unchanged.
- **`seam/crack`:** the eight small cracks are gone. It is now one heavy hit (a hard snap, the falling 'pew' of ice
  splitting and a deep sub boom) that blooms into a warm A-major chord in a wide hall.
- **The bass line:** a clean A1 at 55 Hz, the open hum's note. It swells under the glass shimmer of `seam/hairline`,
  lands with the hit and holds until the open hum takes over.

**Measured:**
- The crack has a single peak (−17 dBFS at the hit), then a smooth −22 to −24 bloom with no repeated spikes. 41% of
  its energy is below 80 Hz.
- Mixed as heard in game, the low end runs −48 → −34 dB through the appearance, −25 at the hit, about −30 through the
  bloom, and −22 once the hum is in.
- `tools/audio/check.py`, build and all 21 GameTests pass.

**Needs your ears:** whether the hit and bloom feel satisfying, and the bass on your speakers. The open hum is still
about 7 dB louder than the opening before it.

---

## M2: the entry (2026-10-09)

Spec §19: "Harmonic Aperture block and block entity, trigger rules, gauge HUD, Skychart, ship transfer. Done when: a
GameTest per trigger rule, and a test ship crosses."

The author changed the spec in two rounds:
- **Before building:** many Seam sites instead of one (DECISIONS K47). Each site has a closed Seam that only an Aperture
  opens (K48).
- **After the first in-game test, before this commit:**
  1. Bodies tied to the ship by ropes cross with it (K53).
  2. The Aperture shows the way to the site itself (K55).
  3. A ship starts charging further out the bigger it is, and too fast won't open (K54).
  4. A ship only crosses if part of it really goes through the opening (K51). The first build counted 60 % of the
     ship's box overlapping the opening, and carried the author's ship across without it going through.

**Built**
- **Seam sites** (`seam/site/`):
  - Sites are on a grid of one per 2048 blocks, picked from the world seed, in the site dimension only.
  - The nearest-site lookup and the ground height at a site (heightmap if the chunk is loaded, else the generator's
    estimate).
  - Temporary sites for tests and the `site here` command.
- **Closed Seams:** a faint, flickering hairline at every site near a player (K48). While an Aperture charges, it shows
  the heat shimmer at the ship's height.
- **The Harmonic Aperture** (`aperture/`):
  - **The block:** a GeckoLib block with the `idle`, `spin_up`, `charged` and `cooldown` animations, three status gems
    that light per rule, and a needle that points to the nearest site.
  - **Ownership:** soulbound to its owner and their teammates (K49).
  - **Mounting:** it mounts on a Sable ship with a click, as it is placed or assembled.
  - **The screen:** a charge ring, the three ticks, a compass with the distance, the ship's speed against the limit,
    its entry radius, what it is waiting for, and a Skychart slot that adds the height to fly at.
- **Trigger rules** (`TriggerRules`), each a check of its own:
  - **Ship:** the Aperture is on a ship.
  - **Pilot:** a player aboard who may use it (K50).
  - **Dimension:** the ship is in the site dimension.
  - **Radius:** the entry radius, which grows with the ship's size (K54).
  - **Flying:** faster than 2 b/s and touching no ground or water.
  - **Speed:** no faster than 10 b/s (K54).
  - **Altitude:** at least 30 blocks above the ground at the site.
  - **Clear site:** no scar there, and no Seam already open.

  With every rule met, the charge fills in 5 s. Dropping a rule drains it; losing the pilot or the Aperture resets it.
- **Opening:** a full charge opens the Seam at the site, at the ship's height.
  - It faces along the line from the ship to the site.
  - It is sized for the ship and everything tied to it.
  - It stays open while an Aperture ship is within the entry radius plus 32 blocks.
- **Crossing** (`transfer/SeamCrossing`, K51): a ship crosses only when one of its blocks passes through an open cell of
  the Seam between two ticks. It counts from the moment the crack opens.
  - Everyone aboard, and every body tied to the ship, crosses with it into the placeholder Halcyon (K52, K53).
  - Ropes between the bodies come along. A rope to anything staying behind is cut.
  - The Seam it left through mends.
  - A rider who logged out aboard is put back on the ship's deck when they return.
- **Skychart** (`skychart/`): held, a HUD box points the way to the nearest site. Used, it unfolds into a chart of the
  sites within 4096 blocks.
- **Gauge HUD:** the three ticks, the charge, and an arrow with the distance to the site, while you're aboard an
  Aperture ship.
- **Debug commands** (K56): `site nearest|list|tp|here|charge`, `ship assemble|drive`.
- **First-pass assets:**
  - **Textures:** the Aperture model texture and glowmask, both item icons, the screen and HUD sheet, and the chart
    (`tools/textures/make_aperture_textures.py`).
  - **Model:** the Aperture model (`art/models/harmonic_aperture.bbmodel`, headless Blockbench), its geometry, and the
    animations (`tools/models/export_animations.py`).
  - **Sounds:** four new ones: `aperture/charge` (a loop that rises with the charge), `aperture/ready`,
    `aperture/mount` and `skychart/unfold`.

**Verified (actually run)**
- **The milestone check** (`tools/verify_milestone.py`): build ✓, runData ✓, **39/39 required GameTests** ✓, headless
  server boot ✓ (the boot check also confirms the Halcyon dimension loads). The GameTests passed 3 runs in a row.
- **The 18 new M2 GameTests:**
  - **One test per trigger rule:**
    - `rulesShipAndRadius`: the ship and radius rules. The radius is never below 48, grows with the ship, leaves room
      at the speed limit, and is capped. A site just inside the radius counts and one just outside does not.
    - `rulePilotIsAboard` and `rulePilotMayUseTheAperture`: the pilot rule. Someone on deck is aboard and someone
      beside the ship isn't. Owner, teammate and stranger.
    - `ruleFlying`: 1 b/s doesn't fly, 3 b/s does, a ship resting on stone doesn't, and over the speed limit the
      charge stops with "too fast".
    - `ruleAltitude`, `ruleDimension` and `ruleScar`: altitude ignores leaves; the End has no sites; a scar blocks
      the site.
  - **Crossing** (`CrossingGameTests`):
    - `apertureOpensTheSeamAndTheShipCrosses`: a raft charges for 5 s and the closed Seam shimmers at its height. The
      Seam opens over the site, facing the ship's course and sized for it. The raft sits in its plane for 1.5 s and is
      **not** carried. Then it slides through and crosses with every block and the Aperture's owner, and the Seam
      mends.
    - `shipPassingBesideOrOverDoesNotCross`: a raft passes beside, over and under the open Seam and is **not**
      carried. Its blocks crossed the Seam's plane outside the opening on 47 ticks.
    - `ropedBodiesCrossTogether`: two rafts tied by a Simulated rope cross together, still 7 blocks apart. The rope is
      back up in the new dimension with its points moved along. A second rope to a post in the ground is cut.
    - `chargeDrainsAndCancels` and `seamMendsAfterTheShipLeaves`.
  - **Sites and the rest** (`SiteGameTests`):
    - `sitesAreFixedSpacedAndFound` and `closedSeamWaitsAtItsSite`.
    - `crossingThroughAndFacing`: the "through" geometry, both ways; short of the plane, beside, over and before the
      crack all don't count. Also the Seam's facing.
    - `gaugePayloadRoundTrips` and `absentRidersFollowTheirShip`.
  - **Files** (`AssetDefinitionTests`): every block and item has its blockstate, models and textures. Every sound has
    its file and subtitle.
- `tools/audio/check.py`: all 16 sounds pass. The charge loop joins smoothly.
- **The real client** (`tools/capture/m2_entry.json`). A raft on a scripted drive at 3 b/s, with the player on deck:
  1. The closed Seam waits at the site, then shimmers as the raft charges.
  2. The Seam reveals, and the raft crosses 6 to 7 s after it opened, when it reached it, into the placeholder Halcyon
     with the player on deck.
  3. The Aperture's needle points at a site 200 blocks north-east, and on the raft at the site ahead.
  4. The HUD arrow and the screen compass point at the site. In the Halcyon the needle drifts and the HUD shows "–".

  Contact sheet: [`docs/previews/m2-entry.png`](previews/m2-entry.png). The scene played fully in 7 of 9 runs. In the
  other 2 the Aperture was broken just after the raft was assembled, while the camera looked straight at it. I couldn't
  catch it with a trace in the next 3 runs. The likeliest cause is a click on the capture window: the scene runs in
  creative mode, where one click breaks a block. The capture client now ignores attack clicks. Logging every Aperture
  removal showed none in the passing runs except the expected one during assembly.

**Needs the author's eyes or ears** (`[FIRST PASS]`)
- **A real Aeronautics ship:**
  - Charging at speed from far out, the new radius and speed limit in practice, and flying through the opening.
  - Towing a roped contraption across.
  - None of this has been flown in game by me: the GameTests pin or drive simple rafts.
- **The look:** the Aperture model and its animations, the needle, the closed Seam and the shimmer.
- **The sounds:** the four new ones, which I can measure but not hear.
- **Multiplayer:** the pilot and teammate rules with real players. FTB Teams is compiled against but never run.

**Stubs and limits**
- **The Halcyon** is an empty placeholder sky (K52). Its arrival title and real world are M3. Come back with
  `/skyseam ship cross minecraft:overworld`.
- **The Aperture's recipe** is M10 (K49). Get it from the creative tab or `/give`.
- **Joints when a body changes plot slot:** bearings, springs and docking connectors may let go if a tied body has to
  change plot slot in the new dimension (DEVIATIONS D21). It is untested. Ropes are handled.
- **A boat parked on a deck** without a rope stays behind (K53).
- **The Stargazer** pointing to a site is M10 (K47).

**Author's in-game test (M2)**
1. In a creative world with cheats, run `/skyseam site nearest`, then `/skyseam site tp` to stand near a site. You
   should see a faint flickering line in the sky about 40 blocks up. `/skyseam site here` makes a site where you stand.
2. Build an Aeronautics ship and place a Harmonic Aperture on it (creative tab). It clicks into place.
   - Open it and check the compass.
   - Check the needle on its plate: it should point at the site, also after you turn the ship.
3. Fly away (more than 150 blocks), then fly back toward the site. Stay below 10 blocks a second and at least 30 above
   the ground. The gauge's arrow should lead you.
   - The three gems should light, the charge should fill over 5 s and the shimmer should appear ahead at your height.
   - Then the Seam opens.
   - Fly through the opening: you should cross with everyone aboard.
4. **The fixes:**
   - Fly past the open Seam beside it, or over it: nothing should happen.
   - Fly at it above 10 b/s: the gauge should say "Too fast" and the charge should drain.
   - Tie a second contraption to your ship with a rope (rope connectors and rope), fly through: both should arrive
     still tied.
5. Come back with `/skyseam ship cross minecraft:overworld`.
6. Send `logs/latest.log`, and say what to change in:
   - the radius and speed limit;
   - the look and sounds of the Aperture and the shimmer;
   - the needle and HUD.

### M2 follow-up: range from the nearest part, growing with speed (2026-10-09)

The author's change after testing M2 (DECISIONS K58): "make the portal open up from a farther range when the ship is
bigger and when it's going faster … use the closest part of the ship, not the center".

**Changed**
- **Distance:** the distance to a site is now from the nearest part of the ship. That is the nearest point of its
  blocks' box, turned with the ship, or of any body tied to it. It used to be from the ship's centre.
- **Radius:** the entry radius is 48 + 3 per block of ship length + the ship's speed × 8.5 s, up to 256. 8.5 s is how
  long the charge and the crack take, so a ship flying straight in reaches the site as the Seam opens. Examples:
  - a 5-block raft at 3 b/s charges from 89 blocks;
  - a 20-block ship at 8 b/s from 176;
  - a 40-block ship at 10 b/s from 253.
- **During a charge:** the radius it started with holds, so easing off the throttle on the way in doesn't cancel it.
- **Far sites:** a charging Aperture keeps the site's chunks loaded, so the shimmer and the Seam work from far out. The
  Seam opens once the site is loaded.
- **Hold:** an open Seam stays open while the nearest part of an Aperture ship is within its hold radius.

**Verified (actually run)**
- `tools/verify_milestone.py`: build ✓, runData ✓, **39/39 GameTests** ✓ (3 runs in a row), server boot ✓.
- `rulesShipAndRadius` now checks the new rules:
  - **Nearest part:** a point 10 blocks east of the 3-block raft's centre is 8.5 blocks from its nearest part, and a
    point over the raft is at 0.
  - **Size and speed:** the radius grows with length and by exactly speed × 8.5 s, and stops at its cap.
  - **Measured from the edge:** a site just inside the radius from the raft's edge counts, and one just outside
    doesn't.
- The capture client played the full entry again, with the raft charging from 88.5 blocks.

**Needs the author:** whether the new ranges feel right on a real ship. They're config: `entry_radius_per_block`,
`max_entry_radius` and `max_speed`. Beyond the server's view distance (160 blocks by default) the shimmer can't be
seen until you come closer, but the gauge and the charge work.

## M3: the shell (2026-10-09)

Spec §19: "Dimension, sky, physics pack, Arrival Lane, Veil, Mirror Sea Rebound, biomes, the structure generator tools
and the Sundered Obelisk. Done when: a ship arrives and the Obelisk is in view."

The author's answers before building:
- **Blocks:** a core custom block set now; Glassreed and Lumen Lily come in M4 with the wind (DECISIONS K62).
- **The Obelisk:** it fits in 48 blocks, so one structure block can save a hand-built replacement (K64).

**Built**
- **The world** (`world/halcyon/`, K59–K61): a custom chunk generator.
  - **Islands:** three size classes of floating islands between Y 140 and 320, nothing above the sky lid at Y 420.
  - **The Mirror Sea:** water at Y 96 over a shallow pale floor, out to radius 2,000.
  - **Shapes follow the ring:**
    - low pale reefs standing in the sea in the Mirror Shoals;
    - wide meadows in Petalwash;
    - stacked cloud reefs in the Cirrus Reefs;
    - needle spires with deep hollows in the Hush;
    - small wrecks in the Wreckfields;
    - a ring of islands round the Spindle.
  - **The Arrival Lane:** at (−848.5, 240, 848.5) on the south-west rim, in 96×110×96 blocks of clear air, with nothing
    tall between it and the Anchorage.
  - **The Anchorage:** a fixed island 160 blocks ahead of the lane, flat at Y 199, where the Sundered Obelisk stands.
  - **Flora:** petals, grass, cloudmoss and Prismite on top; glow moss, lantern vines and hanging roots under every
    island.
- **Biomes** (K60): seven, in rings with wavy borders, plus Wreckfield patches and the Underbloom band below Y 140.
  Each has its own sky, fog and water colours. No vanilla features or spawns.
- **The dimension:** Y 0 to 512, always noon to the game (`fixed_time`, K66), sky effects `skyseam:halcyon`.
- **The physics pack** (`dimension_physics/halcyon.json`, DEVIATIONS D22): gravity −9; pressure 1.2, flat to Y 320,
  thinning to 0.3 at the lid; drag 0.07.
- **The Lantern-Sun** (`halcyon/LanternSun`, K66–K67): a 40-minute cycle of four phases (Dawn-Glass, Noon-Bloom,
  Dusk-Prism, the Hush), kept in world data and synced to players. The orb circles the Spindle at Y 360.
- **The sky** (`client/halcyon/HalcyonSky`, `[FIRST PASS]`):
  - a pastel dome for each phase;
  - a glow round the Lantern-Sun, and the orb drawn where it really is;
  - a faint ring on the horizon;
  - stars and aurora in the Hush;
  - a slow cloud layer at Y 384;
  - dimmer light in the Hush.

  The sky and the fog share one colour, so distant islands and the far sea fade into the sky without a seam (K70).
- **The Hush ring** keeps a standing dusk whatever the phase: violet sky, stars, dimmer light and quieter sound (K69).
- **The Veil** (`halcyon/Veil`, K71), over the outer 200 blocks and under the lid:
  - the fog closes in and the whole sky washes to pearl white, and sound muffles;
  - players are pushed back in, and carried back past the rim;
  - ships are turned in, and past the rim their outward speed is reflected;
  - nothing is ever damaged.
- **Rebound** (`halcyon/Rebound`, K72):
  - Falling into the sea throws you 24 blocks up with 8 s of Slow Falling, then 10 s of Weakness, at most once every
    30 s.
  - A ship that dips into the sea is sprung back up.
  - Two new sounds: `mirror_sea/rebound` (synthesized) and `mirror_sea.splash` (a pitched vanilla stand-in).
- **Arrival** (K68): riders crossing into the Halcyon face the Obelisk and see the title "The Halcyon".
- **Blocks** (K62, `[FIRST PASS]` textures from `tools/textures/make_halcyon_blocks.py`):
  - the Stillstone set: stone, bricks, mossy and chiseled bricks, pillar, glass, slab, stairs and wall;
  - three cloud blocks, which soften falls;
  - Petal Carpet, Cloudmoss and glow moss;
  - the lantern vine;
  - three Prismite stages.

  Their block files come from datagen (K63).
- **Structure tools** (`tools/structures/`): a byte-for-byte repeatable structure writer, an isometric preview, and
  the Sundered Obelisk's generator (K64). The Obelisk is a split mossy monolith with a glowing orb in the gap, over a
  gateway plaza, 31×48×31. The motto is carved over the gate.
- **Markers** (K65): data markers are swapped for their blocks after placement. The list is in
  `docs/STRUCTURE-MARKERS.md`.
- **Commands** (K73): `/skyseam halcyon tp <arrival|anchorage|spindle|hush|cirrus|petalwash|shoals|veil>` and
  `/skyseam halcyon phase <dawn|noon|dusk|hush>`.
- **Config** section `halcyon`: island density per size class, the Veil's pushes, and Rebound's height (4 to 60),
  cooldown and effect lengths.

**Verified (actually run)**
- **The milestone check** (`tools/verify_milestone.py`): build ✓, runData ✓, **45/45 GameTests** ✓ (3 runs in a row),
  server boot ✓.
- **The 6 new GameTests** (`HalcyonGameTests`):
  - `islandsFollowTheLayout`:
    - 810 islands for the test seed, in all three sizes and every kind; the same seed gives the same islands;
    - no block in the lane's clearance, and nothing tall in the view to the Obelisk;
    - the Anchorage flat under the whole plaza;
    - nothing above the lid, and no floating island dipping into the sea;
    - the sea's surface at Y 96, and no sea past radius 2,000.
  - `biomesFormRings`:
    - each ring at its sample point: the Anchorage in Petalwash, the lane over the Shoals;
    - the Underbloom below Y 140, and Wreckfield patches;
    - the Hush's dusk full inside the ring and absent outside it.
  - `veilPushesBack`: how deep each point is; push direction and carry-back for players; a ship's reflection past
    the rim and at the lid.
  - `reboundThrowsBackUp`:
    - the launch rises exactly 24 blocks under the game's own gravity and drag;
    - Slow Falling, and the 30 s cooldown;
    - a real server player with a connection is caught by the fall their client reported, and is lifted out of the
      water before the launch.
  - `lanternSunKeepsTime`: the phases, the brightness, and the phase command.
  - `structuresFitAndHaveTheirMarkers`: every structure file is at most 48 per side, with its markers and only
    palette blocks. Placed, its motto marker becomes the carved sign.
- **The server boot check:** a real dedicated server with the real Halcyon, generated fresh on every run. It checks:
  - the lane's clearance is empty, with sea under it;
  - the Obelisk's blocks match its file, and its motto is carved;
  - the Anchorage is in Petalwash;
  - Sable reads gravity −9 and pressure 1.2 for the Halcyon.

  Generating the lane and the Anchorage took about 1 s.
- **Sounds:** `tools/audio/check.py`: all 18 pass.
- **The real client** (`tools/capture/m3_shell.json`):
  - **Arrival:** the raft crosses as in M2 and arrives at the lane with the title, the Obelisk on its island ahead.
  - **Shots:** the four phases, the Hush sky, the Obelisk's gate, plaza and motto, each ring, under the Anchorage,
    and both directions inside the Veil.
  - **Rebound:** a survival player dropped from 40 blocks into the sea was thrown back up. A traced run measured the
    top of the throw at 24.2 blocks above the sea.

  Contact sheet: [`docs/previews/m3-shell.png`](previews/m3-shell.png). The Obelisk:
  [`docs/previews/m3-obelisk.png`](previews/m3-obelisk.png). The blocks:
  [`docs/previews/m3-blocks.png`](previews/m3-blocks.png).
- **Fixed from the capture runs, before this commit:**
  - **Rebound never threw a player.** The server moved the player without telling their client, and the launch
    assumed Slow Falling on the way up. The game only applies it on the way down. Now the client is moved to the
    surface first, and the climb uses full gravity.
  - **The Veil barely showed.** The sky ignored the fog; now it washes out with it.
  - **The far sea had edges.** Flying low it ended in a pale rim; from high up the sea was a hard-edged disc. Fixed by
    K70.
  - **The Hush's spires were egg-shaped.** Now they are needles.
  - **The Halcyon's fog replaced underwater and blindness fog.** Now those are left as vanilla makes them.
- **Seen but not mine:** Sable sometimes logs "Received a sub-level movement packet for a non-existent sub-level" on
  the client when the capture assembles the raft. It came up in 5 of today's 20 capture runs with a raft, and the
  raft crossed in every one of them.

**Needs the author's eyes or ears** (`[FIRST PASS]`)
- **The look:** the phase palettes, the Lantern-Sun, the clouds, the fog and haze, the Hush's dusk, the Veil's white
  and the biome colours.
- **The blocks and the Obelisk:** generated first passes. You can rebuild the Obelisk by hand
  (`docs/STRUCTURE-MARKERS.md`).
- **The terrain:** how many islands, their shapes in each ring, and the shallow sea. Density is config.
- **A real Aeronautics ship in the Halcyon:** the lighter gravity, the thicker air, the Veil turning it back, and
  Rebound springing it out of the sea. I only flew the scripted raft.
- **The two new sounds.**
- **Performance on real hardware** while flying across the islands.

**Stubs and limits**
- **Markers without a block yet become air:** `mooring_post` and `almanac_pages` until M4, the two
  `wrightling_stall` until the Wrightling (M5).
- **Not yet:** ambience, Tides and wind lanes (M4), creatures (M5 on), and the arenas and other structures (M6–M9).
- **Glassreed and Lumen Lily** come in M4 (K62). Prismite drops itself until Prism Dust (M5).
- **The way home** is M8's Stitches. Until then: `/skyseam ship cross minecraft:overworld`.
- **Beds** set your spawn but you can't sleep: the game always sees noon (K66). Respawning at a Mooring Post is M4.
- **Old worlds:** Halcyon chunks made by M2's empty placeholder stay empty (K74). Use a new world, or delete
  `<world>/dimensions/skyseam/halcyon`.

**Author's in-game test (M3)**
1. Use a **new** world, or delete `<world>/dimensions/skyseam/halcyon` in your M2 test world.
2. Cross into the Halcyon with an Aeronautics ship, as in M2.
   - You should arrive over the sea at the Arrival Lane, see the title "The Halcyon", and face the Sundered Obelisk on
     its island ahead.
   - Spend half a minute just looking round.
3. Fly to the Anchorage and land. Walk through the gate and read the motto over it.
4. Try `/skyseam halcyon phase dawn`, then `noon`, `dusk` and `hush`, and look at the sky in each. A full cycle takes
   40 minutes.
5. Visit the rings with `/skyseam halcyon tp hush`, then `cirrus`, `petalwash`, `shoals` and `spindle`.
   - In the Hush the sky should stay dusky with stars, and sounds should be quieter.
   - Fly under an island to see the Underbloom.
6. Fly your ship out towards the rim (`/skyseam halcyon tp veil` puts you inside the Veil).
   - The fog and sky should whiten and sound should muffle.
   - Past radius 1,500 your ship should be turned back. On foot, or flying in creative, you should be pushed back.
7. In survival, jump off an island into the sea.
   - Rebound should throw you back up about 24 blocks, with Slow Falling, then Weakness.
   - Fall in again within 30 s: it's just water.
8. Fly a ship down until it touches the sea: it should be sprung back up.
9. Come back with `/skyseam ship cross minecraft:overworld`.
10. Send `logs/latest.log` (it has a line for each crossing and each Rebound throw), and say what to change in the
    look, the terrain, the Veil and Rebound.
