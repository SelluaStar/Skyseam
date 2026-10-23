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

## M3 rebuild: the Halcyon from the author's images (2026-10-10 to 2026-10-11)

After testing M3 the author rejected the world ("the entire world is so bad") and the Obelisk. They asked for:
- a generation system that is different every time, built from kits of pieces with variations;
- ground, not air, with only the floating islands in the sky;
- detailed biomes;
- the sky of their images;
- an Obelisk about 100 blocks tall and detailed like reference 3.

Round one was built to an approved plan, with a halfway check-in of captures beside the images. The author's
answers to round one changed the brief again, with seven more reference images:
- **The Obelisk:** blended materials, bigger, and in mid-air.
- **Textures:** better ones with more colour and variants.
- **The world:** unique caves, no pull-back and no map limit for now, varied sea floor, and taller, denser land with
  Palworld-style cliffs further out.
- **Floating islands:** big ones further out.
- **Nature:** giant mushrooms and giant trees.
- **The sky:** rebuilt to work with shaders, with time cycles and lighting: a sun, a smaller star and a planet by day,
  and four moons by night.

Their answers:
- keep the 40-minute Lantern-Sun day through a mixin;
- `/time` is turned away in the Halcyon, and beds don't work there until a unique way to rest comes later;
- test with Complementary Reimagined;
- no check-in, finish everything.

This replaces most of M3's world, sky and Obelisk (DECISIONS K75–K90, DEVIATIONS D23–D29).

**Built**
- **A grounded archipelago from a seeded plan** (`world/halcyon/plan/`, K75):
  - land rising from a sea at Y 96, about 45 % of the archipelago;
  - main islands in six archetypes (Terraced, Spire Massif, Caldera, Twin Ridge, Cove, and Highland in the outer
    lands), each with 5–10 satellite isles;
  - scattered isles (mound, knoll, sandbar) and needle-spire clusters;
  - sea rocks (stacks, leaning slabs, boulders, arches);
  - floating islands in eight variants: Tuft, Teardrop, Shelf, Shard, Cluster, Ruin-top, Thorn, and the colossal
    Horncrown, a pale isle crowned by giant curved horns (image 9).

  Every world is laid out anew from its seed, the Arrival Lane included (K76).
- **No edge for now** (K81):
  - **The Veil:** off behind `veil_enabled`, so nothing pulls you back.
  - **The outer lands:** past 1,300 blocks the islands grow bigger, taller and closer together. Past 2,600 the land is
    mostly mountainous Highlands (about 78 % land, ground on average 37 blocks higher), with sheer stepped sea cliffs,
    fjords, and sea straits and sounds winding through.
  - **The plan's caches:** bounded, so the world can go on without end.
- **The sea floor** (`HalcyonPlan.seaFloor`): a deep basin crossed by undersea ridges, with seamounts rising to reefs,
  canyons down to Y 30, shallow lagoons in the Mirror Shoals, and stepped reef shelves round the land.
- **Palworld cliffs** (image 11):
  - **Layered rock:** the Sunreach Cliffs' rock lies in horizontal beds of sandy, ochre and umber Cliffstone.
  - **Ledges:** on every tall cliff, ledges jut from the bed lines, grassed on top with ivy under them.
- **Ten biomes** in seeded regions (K77): Petalwash, the Cirrus Reefs, the Hush, the Wreckfields and the Mirror Shoals
  in the archipelago; the Sunreach Cliffs, the Capwood and the Elderwood in the outer lands; the Spindle at the centre;
  the Underbloom in caves and under floating islands. Each has its own rock, land shapes, colours, trees and flora.
- **Caves** (K84), all four kinds plus tunnels:
  - **Grotto** (image 11): a sea-level lagoon under a pierced rock roof, with sand, an islet with a tree, and an arch to
    the sea.
  - **Waterfall Hall** (image 12): a cliff chamber looking out through a waterfall, with lupines, ferns and a timber
    railing.
  - **Sinkhole** (image 13): a domed cavern with a shaft of light, a pool, ivy and a plank walkway.
  - **Geode:** lined with Prismite.
  - **Tunnels:** twisting through the high land.
- **Giant flora** (K85), written chunk by chunk so it can be any size:
  - **Trees:** the Elder Lumen (55 tall, glowing veins; image 8), the Great Violet, the Grand Blossom, the Weeping Dusk
    and the Spirepine.
  - **Mushrooms:** the Ember Cap (image 10), the Parasol, the glowing Glowcap, the Bellcap, and clusters.
- **Decoration** (K79):
  - **Trees and ground plants:** small trees by biome; grass, petals, ferns, lupines and small mushrooms.
  - **Hanging and climbing plants:** Bloomcoral; vines down cliffs; wisteria, ivy and lantern vines under overhangs.
  - **Crystals and the sea floor:** crystals on rock; kelp forests and Bloomcoral reefs underwater.
  - **Waterfalls:** real water, two blocks wide, lit as they fall.
- **The Sundered Obelisk** (K78, K83):
  - **Where:** floating over open sea 170 blocks in from the lane, nothing under it.
  - **The parts:** about 238 tall, its gate on a hanging terrace, two shafts with an orb and a ring of rubble between,
    a floating capstone, and drifting masonry.
  - **The stone:** pale brick blended through mauve Duskstone by noise and gradients, with moss, ivy, glowing runes and
    amethyst veins.
  - **The files:** five slices of at most 48.
- **Time** (K82):
  - **The day:** the Halcyon's day is the Lantern-Sun's 40 minutes, so night really falls and shader packs follow it.
  - **Commands:** `/time` answers "Time works differently here"; `/skyseam halcyon time set|add|query` replaces it.
  - **Beds:** they bring no sleep there.
- **The sky, rebuilt** (K86), all moving with the time:
  - **By day:** the Lantern-Sun (corona and halo), its Ember companion star, and the ringed planet Veyra.
  - **By night:** four moons, the Pale Moon (with phases), the Ringed Moon, the Shard Moon with its debris, and the
    Ember Moon.
  - **Stars and effects:** 2,600 twinkling stars, two nebulae, shooting stars, sky lanterns, a swirl of high cloud
    (image 14) and aurora.
  - **Clouds and light:** thinner, shaded 3D clouds; moonlight and twilight in the light; mist in the Elderwood
    valleys; fireflies and floating wisps at night.
- **Blocks and textures** (K87):
  - **The texture kit:** a new pixel-art kit with colour ramps, bevels, cracks, moss and variants.
  - **Repainted:** Duskstone, Shoalrock, Stillstone, the leaves and the plants.
  - **New blocks (26):**
    - cracked, mossy, tiled, chiseled and runed Duskstone;
    - four Cliffstones and Hornstone;
    - Elder and Duskwood logs, wood and leaves, and Spirepine needles;
    - four giant mushroom caps and their stem;
    - two small mushrooms, the Skylupine and hanging ivy.
  - **Logs** craft into vanilla planks.
- **Commands** (K89): `tp` to the terrace, the Obelisk, the Highlands and each biome; `find` for every kit, cave and
  giant.

**Verified (actually run)**
- **The milestone check** (`tools/verify_milestone.py`): build ✓, runData ✓, **46/46 GameTests** ✓ (three runs in a row; see the M2 flake below), server boot ✓. Sounds (`tools/audio/check.py`): all 18 pass.
- **The Halcyon's GameTests** (`HalcyonGameTests`):
  - `planFollowsTheRules`:
    - the same seed makes the same world and another seed a different one;
    - the lane's air is clear, and the view to the Obelisk is clear;
    - nothing stands within 40 blocks of the Obelisk's axis;
    - the archipelago is 46 % land and the Highlands 78 % (mean ground Y 123 and 160);
    - nothing reaches the build limit, and floating islands keep clear of the land;
    - every kit appears;
    - the plan works 20,000 blocks out.
  - `outerLandsHaveTheirPieces` (new): a Horncrown, an Elderwood blade, all four kinds of cave (each inside land), and
    the giants of each biome standing on their ground.
  - `regionsCoverTheWorld`:
    - every inner and outer biome gets at least two regions, and the Obelisk is in Petalwash;
    - two seeds differ;
    - it is the Underbloom under a floating island and inside a cave.
  - `veilPushesBack`: the Veil is off by default; its maths still hold.
  - `lanternSunKeepsTime`: the day mapping (sunrise, noon, sunset and midnight in the right phases, the day count, the
    offset) and the time commands.
  - `structuresFitAndHaveTheirMarkers`: the Obelisk is over 200 tall in slices of at most 48, with its markers and
    palette, and its motto is carved.
- **The server boot check**, on a real dedicated server with the Halcyon generated fresh:
  - the lane's air is clear, with sea under it;
  - all five Obelisk slices stand as their files say, its motto is carved, and it floats, with only air and sea under
    its foot;
  - the Obelisk is in Petalwash;
  - the Halcyon's day time is the Lantern-Sun's (the mixin works);
  - `/time` run inside the Halcyon left the Overworld's day alone;
  - Sable reads gravity −9 and pressure 1.2;
  - generation: 144 fresh chunks each took 16.0 ms a chunk by the Spindle, 16.3 in the archipelago and 22.9 in the Highlands (the first M3 world, all floating islands, took about 11).
- **The real client** (`tools/capture/m3r2_world.json`, `m3r2_sky.json`, `m3r2_shaders.json`):
  - every cave kind, the giant mushrooms and trees by day and night, the Sunreach canyons, a Horncrown and a Thorn,
    the Highlands, the Capwood, the Elderwood and the Hush;
  - the sky at dawn, noon, sunset and night, and a wall of every new block.

  Contact sheets: [`docs/previews/m3r2-arrival.png`](previews/m3r2-arrival.png),
  [`m3r2-world.png`](previews/m3r2-world.png), [`m3r2-sky.png`](previews/m3r2-sky.png), every new texture
  [`m3r2-textures.png`](previews/m3r2-textures.png), and the Obelisk beside reference 3:
  [`m3r2-obelisk.png`](previews/m3r2-obelisk.png).
- **Shaders** (Iris 1.8.14 beta, Sodium 0.8.13, Complementary Reimagined r5.9.3, `-Pshaders`):
  - **Compatibility:** they load beside Sable, Create and Aeronautics, and the Halcyon renders.
  - **What shows:** the Lantern-Sun, the companion star, the planet, all four moons, the stars, nebulae, aurora, swirl
    and lanterns.
  - **What the pack replaces:** its own sky colour and cloud shading.
  - **The time:** night falls with the Lantern-Sun.

  Contact sheet: [`docs/previews/m3r2-shaders.png`](previews/m3r2-shaders.png).
- **Fixed from the capture runs, before this commit:**
  - the outer lands were solid land (now cut by straits);
  - Horncrowns couldn't fit over the Highlands;
  - the Obelisk's stone looked grainy (now blended in patches);
  - waterfalls off floating islands looked like strings (now fewer and wider);
  - the Elderwood mist was too thick;
  - the high clouds hid the sky, and the moons were too small;
  - `find` could frame a giant behind a cliff;
  - an M2 GameTest crashed when its ship was already gone.
- **Seen but not mine:**
  - **Create's registry startup race:** "Mod loading has failed" during registry baking stopped one capture launch at
    startup. The relaunch worked.
  - **NeoForge's early loading window:** it failed to open once while the previous game was still closing.
  - **M2's `seamMendsAfterTheShipLeaves`:** it failed twice in about nine GameTest runs, once by crashing in its
    clean-up when its ship was already gone (removing a ship that is already gone now does nothing). It runs alone in
    its own batch, before the new world tests, so the new world isn't the cause. It needs a closer look in M2's
    timing with Sable. The last three runs in a row all passed.

**Needs the author's eyes** (`[FIRST PASS]`)
- **The new look:** every texture, the biomes' colours, the cliffs, caves, giants and the Obelisk.
- **The sky:** whether day and night, the moons and the light feel right.
- **The world's balance:** how much land, how tall, how many floating islands and caves. Densities are config.
- **Shaders:** other shader packs. Under Sodium, the Elder logs' and Runed Duskstone's glowing layers draw at
  ordinary light (DEVIATIONS D28).
- **Performance on real hardware,** especially flying over the Highlands.

**Stubs and limits**
- **No edge:** the Veil is off for now (it comes back later). A world is now unbounded.
- **Beds:** no sleep in the Halcyon; a unique way to rest is planned for a later part.
- **Markers without a block yet become air:** `mooring_post` and `almanac_pages` until M4, the two
  `wrightling_stall` until the Wrightling (M5).
- **Not yet:** the Wright ruins on Ruin-top islands and in the Wreckfields, stone bridges, and Wreckfields hulls.
- **Old worlds:** a Halcyon generated before this commit keeps its old chunks. Use a new world.

**Author's in-game test (M3 rebuild)**
1. Use a **new** world.
2. Cross into the Halcyon with an Aeronautics ship.
   - You should arrive over the sea at the Arrival Lane and face the Sundered Obelisk floating ahead.
   - Fly up to its terrace and read the motto over the gate.
3. Try `/time set day` in the Halcyon: it should say time works differently here.
   - Then try `/skyseam halcyon time set sunrise`, `noon`, `sunset` and `midnight`, and look round the sky each time:
     - by day: the Lantern-Sun, its small companion star and the ringed planet;
     - by night: four moons, stars, aurora and shooting stars.
   - A full day takes 40 minutes.
4. Visit the biomes with `/skyseam halcyon tp petalwash`, `hush`, `shoals`, `cirrus` and `wreckfields`, and in the
   outer lands `sunreach`, `capwood` and `elderwood`. `/skyseam halcyon tp highlands` puts you over the mountains
   3,000 blocks out.
5. Find the caves and giants with `/skyseam halcyon find cave_grotto`, `cave_waterfall_hall`, `cave_sinkhole`,
   `giant_elder_lumen`, `giant_ember_cap`, `giant_parasol` and `floating_island_horncrown`.
6. Try sleeping in a bed in the Halcyon: you shouldn't be able to.
7. Fly far out. Nothing should pull you back.
8. If you play with shaders, try them in the Halcyon at day and night.
9. Send `logs/latest.log` and screenshots, and say what to change.

## M3 follow-up: shaders, Prismite, glow moss and Elder veins (2026-10-11)

The author tested the M3 rebuild with Complementary Reimagined and Distant Horizons. They sent two screenshots: a
night aerial view with glowing specks on every far island, and the night sky with hard-edged shapes. They said:
- "there's currently a few problems with the shaders… fix what you can and tell me what I need to fix";
- "too many crystals everywhere, it should be rare kind of";
- the glow moss "should only be on full blocks and it should have variations rather than only one";
- "completely redo the texture of the crystal";
- "randomize the glow texture on the tree and make them blend into each other; most should be one bigger line rather
  than two lines, but when it's two lines it should merge".

**What was wrong**
- **The sky under shaders.** Complementary's sky programs (read in its source) do four things vanilla doesn't:
  - they ignore the alpha a sky element is faded with;
  - they use one colour per triangle;
  - they square a textured colour;
  - they paint anything untextured with the pack's own sky colour.

  So the faded swirl over the zenith became a solid teal and black shape. The aurora's curtains (untextured) became
  slabs of sky colour with hard edges, and the faint stars small squares of it.
- **The blocky glowing squares** near the camera at night (in the author's sky screenshot too) were the wisp
  particles. Their texture was 8×8, blown up close and spread by the pack's bloom.
- **The specks on far islands.** Under shaders, Distant Horizons draws every far block that gives off light as glowing
  (Complementary gives its "illuminated" material an emission of 2.5). It leaves out blocks without collision by
  default, but Prismite had collision and grew on about 3 % of all bare rock, about 10 % in the Hush. Every far
  crystal became a glowing block.
- **Glow moss** grew under anything "solid" (leaves, slabs, stairs, fences), in one texture.
- **The Elder veins** were two thin lines placed at random, so they broke at every block.

**Built** (DECISIONS K91–K94)
- **The sky survives a shader pack** (`client/halcyon/sky/SkyDraw`, `ShaderPack`):
  - **Textured, apart from the dome:** everything but the dome itself is textured. Complementary repaints the dome
    with its own sky colour.
  - **Light:** glows, stars, nebulae, swirl and aurora are added with their strength folded into their colour. In
    vanilla that's exactly the same as fading by alpha; under shaders a square root makes up for the squaring.
  - **Solid bodies:** moons, planet and debris are drawn in two passes, a white cut-out darkening the sky behind them
    by their opacity, then their colour. That's exactly vanilla's alpha blend, and it fades the same under shaders.
  - **New textures:** the faint stars use a dot texture, and the aurora a gradient strip (teal through violet to rose)
    with rays.
  - **Smoothing:** the soft glows are smoothed (`.png.mcmeta` blur), so they don't show their pixels stretched across
    the sky.
  - **Iris check:** found by reflection, so Iris stays optional.
- **The wisp particle:** now 32×32, a small bright core in a faint glow.
- **Prismite is rare:**
  - **Where it grows:** in scattered patches on bare rock (about 5 % of rock, 10 % in the Hush), about one spot in
    seven there. Elsewhere a stray small bud, one spot in a thousand.
  - **Lamps:** fewer on cave floors and the sea floor, and fewer set in the rock (Thorn isles 0.8 %, was 3.5 %).
  - **No collision**, like a flower, so Distant Horizons leaves it out of its far terrain.
  - **New textures:** faceted, with a lit left facet and a bright ridge, deep violet roots, rose and cyan tips, and two
    layouts per size picked at random. The Prismite lamp is repainted to match.
- **Glow moss:**
  - only on whole blocks, never leaves, slabs, stairs, fences or walls;
  - four growths picked at random per face: a lacy mat, branching strands, round colonies, scattered tufts;
  - spores on a full-bright layer, so they sparkle at night.
- **Elder veins:**
  - six vein overlays, mostly one thick vein winding up the bark;
  - in one, a thinner vein parts from it and merges back; in two, a thinner branch joins or leaves it and fades out;
  - every vein crosses the block's top and bottom edges through the same two middle pixels, so a trunk's veins never
    break;
  - each Elder log and wood picks one of six models at random, with a different vein and one of two barks on each
    side.
- **The boot check counts the glow:** in 100 fresh chunks in three places, it counts Prismite crystals, lamps (and how
  many are open to the air) and glow moss. It fails if any moss clings to less than a whole block.
- **DEVIATIONS D28 corrected.** The full-bright layers *do* glow under Sodium and Iris; measured below.

**Verified (actually run)**
- **The milestone check** (`tools/verify_milestone.py`): build ✓, runData ✓, **46/46 GameTests** ✓, server boot ✓.
- **The boot check's census** (a fresh Halcyon):

  | Where (100 chunks) | Crystals | Per chunk | Lamps | Lamps open to the air | Glow moss | Moss on less than a whole block |
  |---|---|---|---|---|---|---|
  | By the Spindle | 72 | 0.72 | 1,130 | 8 | 202 | 0 |
  | In the archipelago | 59 | 0.59 | 3 | 2 | 193 | 0 |
  | In the Highlands | 185 | 1.85 | 6 | 5 | 2,668 | 0 |

  - **Lamps:** almost all of the Spindle's lamps are buried in the rock, where nobody sees them.
  - **Generation speed:** 14.4, 14.6 and 21.1 ms a chunk, as before.
- **The real client, without and with shaders** (Iris 1.8.14, Sodium 0.8.13, Complementary Reimagined r5.9.3;
  `tools/capture/m3f_glow.json` and `m3f_glow_shaders.json`):
  - the sky at noon, sunset and midnight;
  - the Hush's aurora;
  - a showcase of crystals, glow moss, Elder trunks and a lying Elder log at noon and midnight;
  - an Elder Lumen, a sinkhole and the Highlands at night.

  Under Complementary:
  - the aurora is soft curtains with rays;
  - the swirl and aurora slabs are gone;
  - the wisps are smooth round glows;
  - the Shard Moon and its debris draw correctly;
  - the Elder veins and moss spores glow. The veins measure as bright at midnight as at noon (mean 196 against 175)
    while the bark dims (72 against 102).

  Contact sheets:
  - before and after under shaders: [`docs/previews/m3f-shaders-before-after.png`](previews/m3f-shaders-before-after.png);
  - the showcase, aurora and Elder: [`m3f-glow.png`](previews/m3f-glow.png);
  - every new texture, a trunk face and a moss wall from random variants: [`m3f-textures.png`](previews/m3f-textures.png).

**Not tested here**
- **Distant Horizons** was never run (not installed for development). The fix follows its code: it draws a far block
  as glowing when the block gives off light, and leaves out blocks without collision by default.
- **What the pack still owns:** under Complementary the sky's colour and the 3D clouds' shading are the pack's. At
  night the clouds are dark, opaque shapes that can hide the moons.
- **Prismite at night under Complementary:** it gives off a little light but has no full-bright layer, so it reads
  dark.

**For the author to do (Distant Horizons and shaders)**
1. Use a **new** world. Old chunks keep their crowded crystals, and Distant Horizons keeps its saved far terrain.
2. In Distant Horizons' settings (Advanced → Graphics → Culling, or `config/DistantHorizons.toml`), keep "blocks to
   ignore" (`blocksToIgnore`) at its default, non-colliding blocks.
3. If the few far Prismite lamps still glow too much, add `skyseam:prismite_lamp` to its ignored blocks
   (`ignoredRenderBlockCsv`).

**Author's in-game test (M3 follow-up)**
1. New world, Complementary on, Distant Horizons on. Fly high over the islands at night: the far islands should no
   longer be freckled with glowing specks.
2. Look round the night sky (`/skyseam halcyon time set midnight`):
   - no hard-edged shapes;
   - the aurora (strongest in the Hush, `/skyseam halcyon tp hush`) should be soft curtains with rays;
   - wisps near you should be round glows, not squares.
3. Find crystals on bare rock: they should be rare, in small patches, faceted with coloured tips, and you walk
   through them.
4. Look under overhangs and in caves (`/skyseam halcyon find cave_sinkhole`): glow moss only on whole blocks, in
   different growths, sparkling at night.
5. `/skyseam halcyon find giant_elder_lumen` at night: the veins should run unbroken up the trunk, mostly one thick
   line, sometimes splitting and merging.
6. Send screenshots and `logs/latest.log`.

### M3 follow-up, part 2: Distant Horizons' ignored blocks, set by the mod (2026-10-11)

The author: "the way to fix the glowing blocks is to remove them from LODs" (Distant Horizons' ignored-blocks list),
"just make the mod do it automatically… only leave on things like the glowing mushrooms".

**Built** (DECISIONS K95)
- **`client/compat/DistantHorizonsBlocks`:** once Distant Horizons has loaded, Skyseam adds its small glowing
  decorations to Distant Horizons' `ignoredRenderBlockCsv`.
  - **What's kept:** the existing entries.
  - **How it's set:** through Distant Horizons' own config entry, as its settings screen does, so the change is saved
    to `config/DistantHorizons.toml` and applied at once. Distant Horizons has no public API for this list, and
    editing its file directly could be overwritten by its own copy.
  - **Distant Horizons stays optional:** it's reached by reflection.
- **The default list** (`distant_horizons_hidden_blocks` in `config/skyseam-client.toml`):
  - **On it:** Prismite buds and clusters, Prismite lamps, glow moss, lantern vines and the small glow shrooms.
  - **Left off:** the giant Glowcaps, to stand out. The Elder logs and Runed Duskstone, which Distant Horizons draws
    as wood and stone, not as glowing.
  - **To switch it off:** `distant_horizons_hide_glow_blocks = false`.
- **Why the collision change alone wasn't enough:** Distant Horizons passes a skipped plant's colour and glow to the
  block under it ("tint with avoided blocks"), so the glow moss and glow shrooms still freckled the rock.
- **Development only:** `gradlew runCaptureClient -Pdh` adds Distant Horizons 3.3.3 to the screenshot client (K88).

**Verified (actually run)**
- **The milestone check:** build ✓, runData ✓, 46/46 GameTests ✓, server boot ✓.
- **With Distant Horizons 3.3.3 and Complementary Reimagined** (`tools/capture/m3f_dh.json`: 140 blocks up at night
  over the Hush and 120 up over the Highlands, two minutes for Distant Horizons to build far terrain each). Same
  world and spot each time:

  | Highlands at night | Bright specks far away | Bright specks near |
  |---|---|---|
  | Setting off | 761 | 2,707 |
  | Setting on, without the glow shrooms | 163 | 889 |
  | Setting on, with them (the default) | 154 | 174 |

  - **The last run's framing:** slightly shifted, so its near count is approximate.
  - **What's left:** mostly the wisps and sky lanterns drifting in the air.
  - **The config:** `config/DistantHorizons.toml` holds Distant Horizons' defaults followed by Skyseam's blocks, and a
    second launch adds nothing twice.
  - **Contact sheet:** [`docs/previews/m3f-distant-horizons.png`](previews/m3f-distant-horizons.png).
- **Correction:** the previous section's "Distant Horizons was never run" no longer holds.

**Author's in-game test**
1. Update Skyseam and launch once with Distant Horizons. Its ignored blocks (Advanced → Graphics → Culling) should
   now end with the `skyseam:` blocks.
2. In a **new** world, fly high at night with Complementary on: the far islands should be free of glowing specks,
   with the giant Glowcaps still glowing.
3. If you'd like the small glow shrooms (or anything else) back in the far terrain, remove them from
   `distant_horizons_hidden_blocks` in `config/skyseam-client.toml` *and* from Distant Horizons' list.

## Flux vortices (2026-10-11)

The author: "let's add a flux vortex type of thing (kinda like in the Avatar movie on Pandora)". Their wishes:
- **Walking in:** you are launched up.
- **A contraption flying in:** it is launched down, and all its propellers and spinning things spin in a random
  direction until it leaves.
- **How many and where:** several in a world, spawning like structures, in areas of low ground rather than on top of
  mountains.
- **How high:** up to about Y 400, a little above the highest floating islands.
- **Coming and going:** they vanish and reappear at random times and places.
- **The look:** nice particles, textures and animations, glowing, with forming and unravelling animations and effects
  in the surroundings.

**Built** (DECISIONS K97)
- **Where and when** (`halcyon/vortex/VortexField`), from the seed and the clock, with nothing saved:
  - **Cells and slots:** 640-block cells and ten-minute slots; in each slot a cell may hold one vortex (55 %).
  - **Life:** it forms at a random moment and lasts 2.5 to 6 minutes, at a new spot each time.
  - **The spot:** the lowest ground among a dozen random spots, lowland first, then open water, never a mountain.
  - **Kept clear of:** floating islands, rock stacks, the Obelisk, the Arrival Lane and the Spindle.
  - **Settings:** all of it (`[flux_vortex]` in the server config).
- **What it does** (`halcyon/vortex/FluxVortices`, `external/CreateBridge`):
  - **Creatures:** one walking in is thrown about 40 blocks up and out, with Slow Falling.
  - **Ships:** one flying in is dragged down (up to 16 blocks per second) and swept round. Every spinning part on it
    turns a random way at a random speed until it leaves, then gets its own speed back.
- **What you see and hear** (`client/halcyon/vortex/`), sent to players within 2,048 blocks. Redrawn after the
  author's reference stills (see "Looks like Pandora's" below):
  - **The column:** two thick vortices of light winding round each other like DNA, each with strands spinning round
    its middle, arcs jumping between them, wisps peeling off; pixel-art textures in Minecraft's style.
  - **The crown:** at the top they flare apart; arcs burst out all round and fall away, a glowing cap hangs over it,
    halos pulse out and sparkles fall like spray.
  - **The foot:** a pool of light with ripples running out over the water.
  - **Rocks:** eight great flat-bottomed mesas hanging round it.
  - **Particles:** twinkling motes up the vortices, sparkles falling from the crown, light drawn in over the water to
    the foot, mist (or spray) off its foot, sparks.
  - **Sound:** a roar, and sounds as it forms and unravels.
  - **Forming:** the pool lights, a ripple runs out, the pair shoots up winding round each other, the crown bursts
    open and the rocks lift.
  - **Unravelling:** the strands let go of the ground and stream away upward; the pool dims and the rocks sink.
- **New files:** textures (`tools/textures/make_flux_vortex_textures.py`), three particles, four synthesized sounds.
- **Commands:** `/skyseam halcyon vortex here [seconds]`, `at <x> <z> [seconds]`, `find`, `list`, `clear`.

**Verified (actually run)**
- **The milestone check:** build ✓, runData ✓, **49/49 GameTests** ✓, server boot ✓, sounds ✓.
- **`vortexFieldFollowsTheRules`:**
  - deterministic;
  - 239 of 726 cell slots hold a vortex (286 before the column widened and the clearance from islands grew to 20);
  - each lives inside its slot and its cell;
  - feet between 0 and 61 blocks above the sea, median 10;
  - clear of floating islands and rock, and of the Obelisk and the lane;
  - every cell with a vortex in two or more slots had them in different places;
  - the shape and strength curve are right.
- **`vortexThrowsCreaturesUp`:** a pig at a vortex's foot (in open air near the End's origin) was thrown from Y 230 past
  Y 243 and had Slow Falling.
- **`vortexDrivesShipsDownAndScramblesSpinningParts`:** two rafts released side by side near the End's origin.
  - **The fall:** after 1.5 s the one in the vortex fell at 26 blocks per second and the free one at 16.
  - **The scramble:** the shaft its creative motor turns went from 16 rpm to a scrambled speed (16 to 27 or to -22.7 in
    different runs).
  - **The restore:** once the vortex was gone the shaft was back at exactly 16.
- **The boot check** (a real Halcyon): at the middle of the next ten slots, 184 vortices within 3,000 blocks of the
  Spindle, 131 of them within 20 blocks of the sea, the highest foot 61 above it (206 and 147 before the clearance
  grew).
- **The real client** (`tools/capture/m3v_vortex.json`, and `m3v_vortex_shaders.json` with Complementary Reimagined):
  - **Shots:** forming, raging by day and night, among its rocks, unravelling, and a natural one found with `find`.
  - **Under Complementary:** the column stays see-through with its ribbons showing.
  - **Contact sheets:** [`docs/previews/m3v-vortex.png`](previews/m3v-vortex.png) and
    [`m3v-vortex-shaders.png`](previews/m3v-vortex-shaders.png).

### Two vortices like DNA, in Minecraft's style (2026-10-12)

The author, on the thin double helix, sending the same three stills again: "add extra effects to it and make the top
look better and make it like two helixes or something like a thick vortex like shown in the reference. MAKE IT LOOK
EXACTLY LIKE THE REFERENCE AND ALSO MATCH MINECRAFTS STYLE (two vortex's in a dna shape)".

**What changed** (DECISIONS K97)
- **Two thick vortices:** each about ten blocks thick, with a body of pixel streaks running up it, a bright core, a
  haze, and four strands spinning round its middle. They wind round each other like DNA, cyan and violet by night,
  milky white by day, spreading and throwing up spray at the water.
- **The top:** they flare apart into a crown. Eighteen arcs burst out and fall away in turn, a glowing cap with rays
  hangs over it, halos pulse out, and sparkles fall from it.
- **Extra effects:** a new `flux_glint` particle for the crown's sparkles and for light drawn in over the water to the
  foot; motes that twinkle through four pixel frames; arcs between the two vortices; wisps.
- **Minecraft's style:** every vortex texture redone as pixel art in a few steps of brightness, without smoothing,
  like a beacon beam.
- **Fixes found on the way:**
  - vanilla drops particles more than 32 blocks from the camera, so motes high up the column never showed; they are
    now spawned as always-visible;
  - from inside a vortex the strips filled the view with light; anything within a few blocks of the camera now fades;
  - by day the pair stacked up to solid white; it is now drawn at about half strength by day.
- **Far, with Distant Horizons:** the same two vortices as boxes, a soft column round a bright core each.

### Looks like Pandora's (2026-10-11)

The author, on the first look: "it looks more like a tornado than a unique thing", "the animations just look like a
tornado", "it renders in unrendered LODs and chunks (Distant Horizons compat)" and "it renders in front of islands and
stuff". A second look (a thread with rings of field) was stopped: "IT LOOKS BAD". The author sent three stills of the
flux vortices in Avatar (kept in the gitignored `reference/local/`, not in the repository) to work from.

**What the stills show, and what was built** (DECISIONS K97)
- **Strands, not wind:** strands of light flowing up, crossing. A first try with 44 fine braided strands was rejected
  ("LOOKS BAD maybe like a double helix with thick lines and less lines aaand it looks cool aand random"). Built as a
  double helix: two thick strands winding round each other, bent and swollen unevenly by slow waves, the twists
  climbing slowly; four thinner wanderers that come and go at random; wisps peeling off; arcs jumping between the
  two strands.
- **Even width:** no funnel. The column is about 26 blocks across nearly all the way up.
- **The foot:** the brightest part, glowing on the water. Built as a pool of light, ripples and rising mist.
- **The top:** strands curling out and falling away. Built as a fountain of arcs.
- **Colour:** white and see-through by day; cyan, blue and violet by night.
- **Rocks:** great flat-bottomed rocks hanging round it, barely moving. Built as eight mesas, 10 to 19 blocks across
  and 5 to 8 thick, grassed on top.
- **Animations:** it forms with the helix shooting up out of the pool, and unravels with its strands streaming away
  upward.

**Verified (actually run)**
- **The milestone check:** build ✓, runData ✓, **49/49 GameTests** ✓, server boot ✓.
- **The real client, plain** (`tools/capture/m3v_vortex.json`, now with wide shots by day and night): the helix forms
  out of the pool, rages among its rocks, is seen from inside while being thrown, unravels, and a natural one is
  found with `find`. [`docs/previews/m3v-vortex.png`](previews/m3v-vortex.png).
- **Under Complementary Reimagined** (`m3v_vortex_shaders.json`): the helix stays see-through, white by day, cyan and
  violet by night. [`docs/previews/m3v-vortex-shaders.png`](previews/m3v-vortex-shaders.png).
- **With Distant Horizons** (`m3v_vortex_dh.json`): vortices 500 blocks and more away are drawn among its far
  terrain and hidden behind nearer islands; the near one is hidden behind islands in front of it.
  [`docs/previews/m3v-vortex-dh.png`](previews/m3v-vortex-dh.png).
- **A freeze found and fixed:** the first Distant Horizons capture froze the client right after arriving. Asking
  Distant Horizons for its terrain on the game's thread was the likely cause (no thread dump was taken). Each
  question is now asked on a short-lived thread of its own, with a ten-second limit in real time (the game's own
  server falls far behind while Distant Horizons generates, so game ticks were no measure), only while a vortex
  stands beyond the render distance, and never again once the answer is yes. The capture has run through five times
  since. Each far vortex's answer is logged once (`Skyseam: far flux vortex at x, z: Distant Horizons has terrain
  there: drawn`); in the last run the vortex 517 blocks out was answered within ten seconds of forming.
- **`/skyseam halcyon vortex here`** where the chunk isn't loaded now stands the vortex on the Halcyon's planned
  ground or sea, not at the caller's height.

**Where it is drawn** (the author's points 2 and 4)
- **Near:** only within the render distance and once the chunk under it has loaded, fading out over the last 64
  blocks. Terrain and islands in front of it hide it.
- **Far, with Distant Horizons:** handed to Distant Horizons as glowing see-through boxes, drawn among its far terrain
  with its own depth, only where it has terrain, fading in where the near one fades out
  (`client/compat/DistantHorizonsVortices`). Distant Horizons stays optional: compiled against its API, loaded only
  when it is installed, and switched off for the session if anything fails.

**Not seen yet**
- **A real Aeronautics ship's propellers** in a vortex. The GameTest scrambles a Create shaft on a Sable raft.
  Propellers take their spin and thrust from the same speed, but nobody has watched one go wild.
- **The throw on camera:** the capture's view from inside the column doesn't show the climb clearly.

**Author's in-game test**
1. In the Halcyon, `/skyseam halcyon vortex find` takes you to the nearest one (there are usually two or three within
   1,000 blocks). Or `/skyseam halcyon vortex here 120` makes one where you stand.
2. Watch it form and unravel (`/skyseam halcyon vortex here 20` makes a short one), and look at it by night.
3. Walk into one: you should be thrown up and float down.
4. Fly an Aeronautics ship into one: it should be dragged down, its propellers spinning wildly, until it gets out.
5. With shaders, check it by day and by night.
6. With Distant Horizons, look at one from beyond your render distance: it should stand among the far terrain, the
   double helix in boxes, hidden behind any island in front of it, and turn into the full one as you fly closer.
   Islands in front of a near one should hide it too.
7. Send screenshots and `logs/latest.log`.

## The Springbloom (2026-10-12)

The author, with three reference images of glowing launch plants: "make something like this. it spawns on both
floating islands and normal islands and when hitting it its kind of like a boost pad. first when you land on it it
kind of like squishes down and makes kind of like a farting sound, and then when jumping while on it it launches you
up. make sure its a 3d model with animations (you can use block bench.) ... also make sure it looks good and has
particles and animations". The images are kept in the gitignored `reference/local/` (18 to 20).

**Built** (DECISIONS K98)
- **The plant** (`halcyon/flora/SpringbloomBlock`, a GeckoLib block entity): five broad teal leaves with glowing
  veins and five violet-pink inner leaves round a squat glowing violet bulb, four stalks tipped with glowing pink
  cubes. Model `art/models/springbloom.bbmodel`, built with the headless Blockbench tools; pixel-art textures with a
  glowmask (`tools/textures/make_springbloom_textures.py`). Each plant is turned and sized a little differently.
- **Animations:** `idle` (sways, the bulb breathes, the tips pulse), `squish` (the bulb flattens, the stalks drop and
  splay, all spring back) and `launch` (compresses, then stretches up with leaves and stalks flung up, and wobbles).
- **Landing on it:** no fall damage; it squishes, puffs spores and makes a rude raspberry noise (three takes).
- **Jumping on it:** a launch about 16 blocks up (`springbloom.launch_height`), with a boing and whoosh, a burst of
  spores and a spore trail behind you. Mobs are launched too. Crouch to step on and off without a launch. The first
  landing within twenty seconds of a launch does no harm.
- **Particles:** a new glowing spore cube (`springbloom_spore`, two twinkle frames), drifting off its tips all the
  time, puffing out as it squishes, bursting up as it launches, and trailing behind what it launches.
- **Where it grows:** the grass of the Halcyon's islands, grounded and floating: one spot per 11-block cell may hold
  one (`springbloom.chance`, 0.45), where the grass is flat and open. It drops itself when broken and can be planted
  on any solid top. In the Skyseam creative tab.
- **Also:** `/skyseam halcyon springbloom find`; hidden from Distant Horizons' far terrain like the other small
  glowing blocks (the default list only; an existing client config keeps its own list); the capture tool gained
  `jump` and `camera` steps.

**Verified (actually run)**
- **The milestone check:** build ✓, runData ✓, **52/52 GameTests** ✓, server boot ✓.
- **`springbloomLaunchesWhatJumpsOffIt`:** a pig on a Springbloom (in open air near the End's origin) jumped and
  was thrown from Y 201.5 past Y 210 at 1.76 blocks a tick.
- **`springbloomTakesTheFall`:** a pig dropped twelve blocks onto one landed unhurt.
- **`springbloomSparesTheLandingAfterALaunch`:** a launched pig whose Springbloom was swapped for stone while it was
  in the air came down on the stone unhurt.
- **The boot check** (a real Halcyon) counts them in 300 chunks: 8 with the first chance of 0.2, so the default went
  up to 0.45, which grows 22, 3 of them on floating islands.
- **The real client** (`tools/capture/m4_springbloom.json`): three planted on a patch of grass, by day and by night; a
  survival player dropped onto one (it squishes, spores puff out, no damage), then jumping (launched high with a spore
  trail, and back down onto it); and a natural one found with `find`. Contact sheet
  [`docs/previews/m4-springbloom.png`](previews/m4-springbloom.png).

**Not seen yet**
- **The sounds** have only been checked for level, not listened to: the "rude noise" is a synthesized stand-in.
- **A natural one close up:** the capture's `find` shot shows one at the edge of the frame.
- **Multiplayer:** a launched player's own client moves them; other players see the launch through the server.
  Not tried with two players.

**Author's in-game test**
1. In the Halcyon, `/skyseam halcyon springbloom find` (or take one from the creative tab and plant it).
2. Drop onto one from a few blocks up: it should squish with a rude noise and spores, and not hurt.
3. Jump while standing on it: you should be launched high with a trail of spores, and land unhurt.
4. Crouch-walk onto and off one: no launch.
5. Look at them at night: the bulb, veins and tips glow.
6. Send screenshots and `logs/latest.log`.

## Springbloom sizes and walking on; playtest mods (2026-10-12)

The author, after trying the Springbloom: "higher base boost height, i should be able to walk onto it rather than get
stuck on it when holding w as if it were a block. i shouldnt need to jump, add variable boost height. add variable
sizing so sometimes there will be a bigger one ... up to 3 times the size or so and have it random each time you place
it or it spawns", and for the test client: Xaero's Minimap and World Map, Sodium, Lithium, C2ME, FerriteCore, Jade,
Yeetus Experimentus and ImmediatelyFast.

**Built** (DECISIONS K99, K100)
- **Walking on:** its collision box is 9 pixels high (was 10), under the 0.6 blocks you step up without a jump.
- **Sizes:** a block state `size` 0 to 4, drawn at 1, 1.35, 1.75, 2.3 and 3 times the usual size; rolled each time
  one is planted (45, 25, 15, 10 and 5 in 100) or grows (made smaller until it has room). It still fills one block.
  The bulb is flatter and wider on a big one, and sinks under whatever stands on it and bulges out, at every size.
  Bigger ones puff more spores and sound lower. `/setblock ~ ~ ~ skyseam:springbloom[size=4]` places a given size.
- **Launches:** 24 blocks from one of the usual size (was 16), up to twice that from the biggest, each launch up to
  an eighth higher or lower. The setting is now `springbloom.base_launch_height` (renamed so old configs take the new
  default). Jumping while on it still launches you; walking on and off doesn't.
- **Three test clients** (after the author made clear what they wanted), all in `run/` so they share worlds:
  `gradlew runClient` (Distant Horizons 3.3.3, Sodium 0.8.13, Lithium 0.15.4, FerriteCore 7.0.3, ImmediatelyFast
  1.6.14), `gradlew runShaderClient` (the same and Iris 1.8.14 beta, with Complementary Reimagined switched on) and
  `gradlew runModdedClient` (the same with shaders, and Xaero's Minimap 26.6.0, Xaero's World Map 1.47.0, Jade
  15.10.6, Yeetus Experimentus 87.0.0 and C2ME 0.4.0 alpha 123). `runServer`, the GameTests and `runData` clean
  them back out of `run/mods`.
- **Also:** the capture tool's `walk` step (holds forward), `runCaptureClient -Pplaytest` and `-PcaptureDir=`.
- **Fixed on the way:** Gradle had called the shared `run/mods` sync up to date with the playtest jars still in it,
  so the first data run loaded them; every sync now runs each time.

**Verified (actually run)**
- **The milestone check:** build ✓, runData ✓, **54/54 GameTests** ✓, server boot ✓.
- **`springbloomCanBeWalkedOnto`:** a pig moved straight at one along the ground stepped up onto the bulb.
- **`bigSpringbloomLaunchesHigher`:** a huge one threw a pig from Y 151.7 past Y 183.1 (more than the usual 24 plus
  its spread, plus 4); the usual launch test now reads 2.22 blocks a tick (was 1.76).
- **The boot check** (300 chunks of a real Halcyon): 22 Springblooms, 3 on floating islands; by size 16, 4, 1, 1, 0.
  (The first room check, which wanted flat ground all round, let none bigger than size 1 grow: 19, 3, 0, 0, 0.)
- **The real client, with all nine playtest mods loaded** (`runCaptureClient -Pplaytest`,
  `tools/capture/m4_springbloom_sizes.json`): a fresh Halcyon generated with C2ME and Lithium running; the five sizes
  side by side by day and night; a survival player holding forward walked onto one of the usual size without jumping
  and stood on its bulb, then jumped and was launched; walked into the huge one, sank into its bulb, and was thrown
  far higher. The first try showed the big bulbs squashed flat under their petals; they now stand higher at rest
  (top 10 pixels up on the usual size, 21 on the biggest) and still sink under you. Contact sheet
  [`docs/previews/m4-springbloom-sizes.png`](previews/m4-springbloom-sizes.png). The log has only the mods' usual notes
  about optional add-ons that aren't installed, and Xaero's version check failing offline.

**Not seen yet**
- **The three clients in a world:** each was started to the title screen (`runClient` with its five mods, no errors;
  `runShaderClient` loading Complementary; `runModdedClient` with C2ME, the maps, Jade and Yeetus, its only error
  Xaero's online version check) and closed; only the screenshot client went into a world with these mods.
- **A big one grown wild** up close: the boot check counts them, the capture's `find` shot landed in a hillside.
- **Multiplayer**, as before.

**Author's in-game test**
1. Start a client: `.\gradlew runClient`, `.\gradlew runShaderClient` or `.\gradlew runModdedClient`.
2. `/setblock ~ ~ ~2 skyseam:springbloom[size=4]` (or place some from the creative tab: each rolls a size).
3. Walk onto one holding W: you should step up onto it without jumping, and the bulb should sink under you.
4. Jump on one of the usual size, then on a huge one: about 24 blocks, then about 48.
5. Send screenshots and `logs/latest.log`.

## Flux vortices: drawing fixed, smooth handover, far distances, spawning (2026-10-12)

The author: clipping with clouds under shaders and with islands without, a rough handover to the far version, a hint
that it shows close up and at an angle, that it goes faint where loaded chunks are behind it (not Distant Horizons'),
that it hides water behind it; then that far vortices didn't seem to spawn or show with Distant Horizons, to make them
a little more common, and to have one form near you now and then. DECISIONS K101, K102.

**Built**
- **Drawn last:** the vortex's light is drawn after the whole level (shader pack and all), against the finished depth,
  so water, clouds and a shader pack's passes are no longer laid over it.
- **Handover past the render distance:** the near vortex at full strength inside it, crossfading with the far one
  over 64 blocks past it; both fade in over a second instead of popping in.
- **Close up:** continuous strips that fade out end-on, and spinning slices (`flux_slice`) that fade in, so it never
  shows as flat sheets.
- **Far version:** a thick solid, slightly greyer column per vortex with two thin bright lines winding round it;
  with a shader pack the column is plain and only the lines glow; thicker in proportion past 600 blocks.
- **Far distances:** vortices sent out to `flux_vortex.send_radius` (4,608 blocks), worked out a few cells a second;
  only those near players are ticked.
- **Spawning:** `slot_chance` 0.65 (was 0.55); `nearby_chance` 0.35 of one forming 32 to 50 blocks from a player
  with none within 640, once each ten-minute slot.

**Verified (actually run)**
- **The screenshot client** (Sodium, Iris, Distant Horizons; with and without Complementary Reimagined): close up from
  above, the side and below (`tools/capture/m3v_vortex_close.json`); from 67 to 500 blocks, looking down over water
  (`m3v_vortex_handover.json`): the column reaches its pool on the water at every distance, with and without shaders
  (before, it vanished over loaded water with shaders and was cut off without); with shaders it stands in front of
  the pack's clouds and behind the islands in front of it; the handover past the render distance shows no seam.
- **Far distances** (`m3v_vortex_dh_far.json`, three vortices placed 800, 1,300 and 2,000 blocks off, five minutes
  for Distant Horizons): 50 far vortices drawn, out to about 5,000 blocks (16 more still waiting on terrain); the one
  2,000 blocks off reads clearly by day and night, with and without shaders, hidden by the islands in front of it.
  Contact sheet [`docs/previews/m3v-vortex-fixes.png`](previews/m3v-vortex-fixes.png).
- **The milestone check:** build ✓, runData ✓, **55/55 GameTests** ✓ (new: `vortexFormsNearAPlayerWhereOneMayStand`,
  a vortex could form 32 to 50 blocks from 93 of 100 players on low ground, always where one may stand), server boot ✓.

**Not seen yet**
- **A vortex forming near you** in a real game: it is rolled once per ten-minute slot, so only the spot-finding was
  tested. The log says "a flux vortex forms by <name>" when it happens.
- **Your own world:** your `run/config/skyseam-server.toml` keeps your `cell_size` of 350 (the default is 640): with it
  and the new chance there are about four times as many vortices as the defaults give.
- **A depth problem left:** under a shader pack its clouds write no depth, so a cloud in front of a vortex doesn't
  hide it.

**Author's in-game test**
1. `.\gradlew runShaderClient` (or `runClient`), in the Halcyon, with Distant Horizons' terrain generated round you.
2. Look round from up high: vortices should stand on the horizon out to Distant Horizons' distance.
3. Fly close to one, look at it from above, the side and below, over water and against clouds: no faint patches, no
   flat sheets, and it should reach its pool on the water.
4. Fly away from one past your render distance: it should hand over to the far version without a jump.
5. Send screenshots and `logs/latest.log`.

## Flux vortices: timing, settings screen, full detail further out, floating clumps (2026-10-12)

DECISIONS K103, K104. The author asked for vortices lasting 3 to 7 minutes with a cooldown of up to 2 between them,
varying so they don't all change at once; defaults of 460-block cells and a 0.65 chance; settings for all of it and a
better settings screen; a working far version without shaders; full detail further out; and the old floating blocks
back in place of the hanging mesas, in the far version too.

**Built**
- **Timing:** each cell runs its own chain of vortices, 3 to 7 minutes each with 15 s to 2 min between; cells keep
  their own times. 32 spots tried per turn, so far fewer turns go empty.
- **Settings screen:** named sections and entries with units, flux vortices in four pages. New settings: lifetimes,
  cooldowns, near-you chance, area and interval; client `flux_vortices.detail_distance`.
- **Full detail to 640 blocks** with Distant Horizons (no shader pack), Distant Horizons' far terrain put into the
  depth buffer first so islands in front still hide it.
- **Far version:** solid, shaded, the same detail at every distance.
- **Floating clumps** of 1 to 4 blocks round each vortex, near and far.

**Verified (actually run)**
- **The milestone check:** build ✓, runData ✓, **55/55 GameTests** ✓ (the field: 1,832 vortices in three hours of 121
  cells, none shorter than 3 minutes or longer than 7, at least 15 s between; cells hold one 41% of the time near the
  Spindle). The server boot check's other counts pass (45% of cells hold one at a time, about 60 within 3,000 blocks),
  but its chunk-speed check failed twice at 39 to 51 ms a chunk (12 ms this morning): a Modrinth-launched game was
  running on the machine at the time; world generation itself didn't change.
- **The screenshot client:** the settings screen's pages (`m3v_config_screen.json`); close up, the floating clumps
  orbiting with their lamps; with Distant Horizons and no shaders, vortices drawn in full out to 640 blocks behind the
  islands in front of them and the solid far version beyond; with shaders, the far versions all over the horizon.

**Not seen yet**
- **The phantom far version** the author saw: not reproduced. If it shows again, `logs/debug.log` says when each far
  vortex was handed to Distant Horizons and removed.
- **Your own client and world.**
- **Map markers (K105):** a white, black-outlined vortex icon (after the author's storm-symbol reference) at each
  vortex on Xaero's Minimap and World Map (drawn by their own element renderers, not waypoints) and on JourneyMap, with
  what it is doing and the time left on hover. Seen in the screenshot client on all three (`m3v_vortex_maps.json`,
  sheet `build/previews/vortex-map-icons.png`); the hover text wasn't seen.

## The Murmurberry (2026-10-13)

DECISIONS K106. The author: "create a glow berry type thing that has variations so its like whitish birthday cake ish
color blended and it glows and it either hangs or attaches to the wall or roof or floor (variated) and when a mob or a
player moves or makes a sound or a block is placed or broken or pretty much anything that would trigger a sculk sensor
happens, it should glow for a split second ... closer it would be instant and farther it would be like 10 seconds ...
a 3d model with animations and nice textures. for the off (no glow) it should be like a translucent shell of a balloon
grey ... when it glows it should be the same but glowing and animated".

**Built**
- **The plant** (`halcyon/flora/MurmurberryBlock`, a GeckoLib block entity): up to five berries like little balloons on
  thin stalks over a mossy pad with four leaves (`art/models/murmurberry.bbmodel`). Off, each berry is a see-through
  pale grey shell with a sheen and a dim core inside. Lit, the same shell glows in one of four colourways of
  birthday-cake pastels blended into each other (pink and sky blue, pink and peach, mint and sky blue, lemon and
  lavender), whitest in the middle and flecked with sprinkles, with a soft halo of its colour, and it swells
  (`pulse`); otherwise it sways (`idle`). Textures by `tools/textures/make_murmurberry_textures.py`.
- **Variety:** on floors (berries up), walls (out and tipped up) and roofs (hanging, on stalks up to half again as
  long); its colourway, size, turn and number of berries (two to five) differ from one to the next.
- **Hearing:** anything a sculk sensor would take as a vibration (steps, falls, splashes, swimming, flapping, blocks
  placed or broken, doors, chests, eating, hits, projectiles, explosions), but not a crouched step, a spectator, or
  anything on wool. It hears 32 blocks (`murmurberry.range`): the nearest light at once and the furthest 10 seconds
  later (`murmurberry.max_delay_seconds`), so the light runs out from the sound in a ring, about 3 blocks a second. A
  flash lasts about half a second; after one, a berry rests a second, so something walking past sends rings out about
  once a second rather than a flicker each step.
- **Light is drawn, not cast:** it lights nothing round it, off or lit, so a ripple across a cave costs no lighting
  work. No sound of its own.
- **Where it grows:** in patches, on the floors, walls and roofs of the Halcyon's caves and under overhangs (giant
  trees' canopies count), roofs only with ground below (`murmurberry.density`). It breaks at a touch, drops itself,
  needs the face it grows from, and can be placed on any solid face. In the Skyseam creative tab.
- **Also:** `/skyseam halcyon murmurberry find` (stands you where you can see the nearest) and `ripple` (a sound where
  you stand); hidden from Distant Horizons' far terrain (the default list only).

**Verified (actually run)**
- **The milestone check:** build ✓, runData ✓, **60/60 GameTests** ✓, server boot ✓ (chunks at 12 to 19 ms again).
- **GameTests** (`gametest/MurmurberryGameTests`): a block placed three blocks away is heard and sent from where it was
  placed; a sound just inside the range is sent and one just outside isn't; a step is heard but a crouched step and
  anything on wool aren't, nor a sculk sensor's own clicking; the delay is none within a block, the full 10 s at the
  edge and grows steadily between; taking away the block it grows on (floor, wall or roof) breaks it.
- **The boot check** (a real Halcyon): 819 in 300 chunks, 204 on floors, 352 on walls, 263 hanging from roofs, every
  one with something to hold on to.
- **The real client** (`tools/capture/m4_murmurberry.json`, `m4_murmurberry_natural.json`): a sealed hall lined with
  them on floors, walls and roof; off (see-through grey); a sound filmed at 1, 3, 6 and 9 seconds, the light running
  out across the hall; close up lit, in all four colourways with halos; walking through it (steps are heard); two that
  grew, one under a giant tree and one on a cave wall, lit by a sound. Sheet
  [`docs/previews/m4-murmurberry.png`](previews/m4-murmurberry.png).
- **Found and fixed while filming:** the first run drew only the pads. GeckoLib shares one set of bones between every
  Murmurberry, and the per-plant changes (longer hanging stalks) piled up frame after frame until the stalks were
  endlessly long; each change is now made afresh from the animation every frame.

**Not seen yet**
- **Under a shader pack:** the lit pass uses the same render type as the vortices' glow (drawn as emissive by
  Complementary), but it hasn't been filmed under shaders.
- **Many at once in a big natural cave:** the hall held 167; a dense patch in a large cave could hold more, each a
  GeckoLib model.
- **Multiplayer:** each player's client lights its own berries from the sounds the server sends; not tried with two.

**Author's in-game test**
1. In the Halcyon, `/skyseam halcyon murmurberry find` (or place some from the creative tab on floors, walls and
   ceilings in a dark place).
2. Stand still, then break or place a block: the nearest should flash at once, the rest in a ring outward over about
   ten seconds, each going out after half a second.
3. Walk past them, then crouch-walk past them: walking sets off rings, crouching shouldn't.
4. Look at them unlit: grey see-through balloons. Lit: pastel blends with sprinkles and a halo.
5. Settings (Mods, Skyseam, Config, Murmurberry): hearing range, delay at the edge, how thickly they grow.
6. Send screenshots and `logs/latest.log`.

## Murmurberry sounds, heights, clumps and fields (2026-10-13)

DECISIONS K107. The author: "add sfx for them, add variable height for both the bulbs and the plant stem itself have
them usually spawn in clumps and also have it so some areas spawn with fields of them with the variable height".

**Built**
- **Sounds** (`tools/audio/synth.py murmurberry`): as each berry lights, a soft glassy chime on one of five notes of a
  pentatonic scale (C5, D5, E5, G5, A5), so a ripple plays like wind chimes and never clashes; a little lower from a
  taller plant. Only berries within 20 blocks of a player chime, and at most 3 start in a tick. Broken, a balloon pop;
  placed, a rubbery squeak.
- **Heights:** a `height` (0 to 4) draws the stalks 0.7 to 1.8 times as long; each berry's own stalk varies by a
  quarter either way and its size from 0.85 to 1.2 times. Placed ones get a random height.
- **Clumps:** they now grow in clumps 2 to 4.5 blocks across, thickest and tallest in the middle, on the floors, walls
  and roofs of caves and overhangs (replacing the even patches), with a stray one now and then.
- **Fields:** in a few places a slow swell of noise makes a field, most spots in its heart holding one, tall and short
  in rolling waves: under roofs anywhere, and in the Hush (whose standing dusk shows the light) on open grass too.
- **Also:** `/skyseam halcyon murmurberry find field`; the capture tool can log the frame rate (`fps`) and turn
  subtitles on (`subtitles`).

**Verified (actually run)**
- **The milestone check:** build ✓, runData ✓, **61/61 GameTests** ✓ (new: heights draw longer stalks, 0.7 to 1.8, and
  a placed one can be any height; the sound definitions, subtitles and files are checked by the asset test), server
  boot ✓. Sound files: `tools/audio/check.py` passes.
- **The boot check:** in 300 chunks, 2,084 Murmurberries (1,083 on floors, 293 on walls, 708 hanging), 92% of them in
  clumps (another within 3 blocks); by height, lowest first: 63, 474, 816, 572, 159. Deep in the Hush, 516 in 100
  chunks, 330 of them in the open in its fields.
- **The real client** (`tools/capture/m4_murmurberry_k107.json`): a hall of clumps and a field of every height; a sound
  with subtitles on shows "Murmurberry chimes"; the light running across the field; a field grown in the Hush, lit at
  its dusk; a clump grown under an overhang. Frame rate in that client: 117 looking at a bare wall, 85 with the hall's
  field of about 150 in view, 64 while it is lit, 93 standing in the Hush's field. Sheet
  [`docs/previews/m4-murmurberry-k107.png`](previews/m4-murmurberry-k107.png).

**Not seen yet**
- **The sounds heard by ear:** the chime was seen to play (its subtitle), but the sounds have only been checked as
  files and spectrograms, not listened to.
- **Weaker machines with big fields:** each Murmurberry is a GeckoLib model; the numbers above are from this machine.
  `murmurberry.density` thins clumps, strays and fields.

**Author's in-game test**
1. `/skyseam halcyon tp hush`, then `/skyseam halcyon murmurberry find field`: a field of mixed heights.
2. Break a block among them: the ring of light, with chimes as it passes.
3. In a cave elsewhere, `/skyseam halcyon murmurberry find`: they should come in clumps.
4. Break one (a pop) and place one (a squeak); placed ones come in different heights.
5. Send screenshots and `logs/latest.log`.

## Flux vortex effects reworked: low gravity, a ride up the column, a spiral for ships (2026-10-13)

DECISIONS K108. The author: "the systems we defined for the vortex's before dont actually work. remember it should mess
with gravity so as you get closer to it it should be low gravity", then five points: low gravity next to it; a slow,
configurable pull towards the middle for ships; players that touch it pulled in and pushed up until they leave, with no
Slow Falling and fall damage on; ships inside spun until they leave, their parts still turning at random; and the pull a
spiral, like something orbiting into a black hole.

**Built** (`halcyon/vortex/FluxVortices`)
- **Low gravity:** gravity weakens from 40 blocks out from the column (or above its top) to a fifth of the usual at its
  edge and inside. Players and creatures jump higher and fall slower (their gravity attribute, given back when they leave,
  change level or the vortex goes); a falling ship falls slower. A ship holding its height (levitite, envelopes,
  propellers) is left to it: lifting it too would fling it upward.
- **Creatures and players** touching the column are drawn in towards a ring round its middle and carried up it, round
  and round, at 16 blocks a second; near the top they are flung out over the crown. No Slow Falling, and the fall is
  counted from where they leave, so it hurts as usual. Only those a ship carries stay with it.
- **Ships** within 48 blocks of the edge are swept round it (12 blocks a second at the edge) and slowly drawn in (1.5),
  with the pull towards the middle an orbit needs, so they spiral in; their engines can fly out against the slow drift.
  Inside, a ship is spun round at 45 degrees a second and its spinning parts turn a random way until it leaves; then the
  parts get their own speeds back and the spin slows to a stop.
- **Settings** (Config, Flux vortices, What they do): gravity at the edge, low gravity reach, lift speed, ship pull, ship
  orbit, ship pull reach, ship spin, and the scramble switch. The old throw height and ship pull-down are gone.

**Verified (actually run)**
- **The milestone check:** build ✓, runData ✓, **62/62 GameTests** ✓ (in one run four Seam and Aperture tests failed
  together and passed on the next two; a separate task is suggested to find what they share), server boot ✓.
- **GameTests** (`VortexGameTests`): six blocks from the edge a pig's gravity is a quarter of the usual, and all of it
  again once the vortex goes; a pig at the edge rose from Y 210 to 235.6 in 2 s and was drawn from 12 to 6.7 blocks off
  the axis, with no Slow Falling and no fall counted; at the top it is flung out of the column and its fall is counted; a
  raft 17 blocks from the edge went from 30 to 28 blocks off the axis in 3 s, swept round at 9.8 blocks a second and
  falling at 14 against 30 for a raft far off; a raft inside spun at 43 degrees a second with its shaft scrambled, got
  the shaft's own speed back when the vortex went, and stopped spinning.
- **The real client** (`tools/capture/m3v_vortex_k108.json`): a survival player's gravity read 0.08 far off and 0.016
  beside and inside the vortex; they were caught at its foot, carried up past the floating islands, flung out at the
  top, and killed by the fall onto a stone floor laid round its foot ("Dev fell from a high place"). A raft half of
  levitite, hovering 32.6 blocks off the axis, was swept round and in: 32.6, 31.7, 30.6, 29.0, 27.2, 25.2, 23.2, 21.4,
  19.9, 18.4, 16.4, 13.7 and 11.8 blocks off every 2 seconds, about one and three quarter turns at 8 to 11 blocks a
  second, into the column. Sheet [`docs/previews/m3v-vortex-k108.png`](previews/m3v-vortex-k108.png).
- **Found while filming:** a raft spinning in the pool beside a player counted them as aboard (they were inside its box),
  so the vortex left them be until it drifted off; now only those Sable carries count. And a hovering levitite raft
  barely moved (half a block a second) until the swirl's hold on ships was made firmer.

**Not seen yet**
- **A real Aeronautics ship flown by a player** into a vortex (with propellers, envelopes or levitite): the rafts here
  had no engines.
- **The spin seen from aboard**, and how it feels to steer out against the spiral.
- **Multiplayer:** each player's own client is sent their motion every tick while they are carried.

**Author's in-game test**
1. `/skyseam halcyon vortex here 300` a little way off, or `/skyseam halcyon vortex find`.
2. Walk towards it: jumps get floatier the closer you are.
3. Step into it: you should be drawn in and carried up, round and round, flung out at the top, and the fall should hurt
   (water still saves you).
4. Fly a ship near it: it should be swept round and slowly drawn in; inside, it spins and its propellers go wild until
   you get out. Settings under Config, Flux vortices, What they do.
5. Send screenshots and `logs/latest.log`.

## Wind lanes (2026-10-17)

DECISIONS K109. The author: "Okay lets do the wind lanes now." The spec's Wind Lanes: "Visible ribbons of petals and
light motes between landmarks. A ship inside a lane is nudged along it (a force if Sable allows, else a velocity nudge
each tick). Direction flips with Tides." The Tides aren't built yet, so the Hush stands in for them.

**Built** (`halcyon/wind/`, `client/halcyon/wind/`)
- **Where:** a ring of lanes round the archipelago through the Arrival and the heart of one region of each kind,
  sweeping round each landmark, and a spoke branching off it at each and running in to the Spindle, alternately blowing
  in and out: 10 to 12 lanes, worked out from the seed alone. Each runs at about the height ships fly there in long,
  gentle swings; it swings round the Obelisk to one side; it passes over, under or between the floating islands and rock
  in its way, whichever strays least from its way, climbing and dropping no steeper than one block in three.
- **Ships** inside a lane are carried along it: a velocity nudge each tick towards the wind's speed (16 blocks a second,
  each lane 0.85 to 1.15 of that), firmest in its middle; a ship going faster is left alone, one flying against it is
  held back, and its motion across the lane is its own. **Players and creatures in the air** (falling, gliding, jumping
  in) are carried more gently; standing, swimming, riding or flying in creative they are left alone.
- **Direction:** from each lane's first landmark to its last, and back through the Hush (the last quarter of the
  Lantern-Sun's day), turning round over half a minute.
- **Seen and heard:** three soft streaked ribbons in each lane (pale pink, gold and sky blue), the streaks running with
  the wind, swaying a little; petals and motes of light riding the wind near you; the rush of the wind (subtitle "Wind
  rushes").
- **Commands:** `/skyseam halcyon lanes list|find|ride`. **Settings** (Config, Wind lanes): on or off, the wind's speed,
  and how firmly ships are carried.

**Verified (actually run)**
- **The milestone check:** build ✓, runData ✓, **65/65 GameTests** ✓, server boot ✓. (In one GameTest run during the
  work `seamMendsAfterTheShipLeaves` failed, one of the intermittent Seam tests the separate task is looking into; the
  full check passed.)
- **GameTests** (`WindLaneGameTests`): a test world has 12 lanes, 8,503 blocks in all, none through rock, a floating
  island or the Obelisk, their middles at least 24 blocks over the ground and at most 23 degrees steep, none turning
  more than 9 degrees in 4 blocks (5.7 and 7.5 at their sharpest in two test worlds), the same lanes every time; the
  wind turns round in the Hush; a hovering levitite raft in a lane goes 12.2 blocks a second along it after 3 seconds
  (the wind 16), the one beside it 0.0; a pig falling through one is blown along at 3.8 blocks a second.
- **The real client** (`tools/capture/m4_wind_lanes.json`, `m4_wind_lanes_look.json`): the filming world has 10 lanes
  (no Cirrus Reefs region far enough out); the ring passes 47 blocks inside the Arrival at Y 237 to 241, with the
  spoke branching off there. A 3 by 3 raft with levitite assembled in the spoke went 1.0, then 12.6, then 13.1 blocks a
  second, 44 blocks along in 4 seconds. A survival player falling through drifted 4 blocks a second along it. `lanes
  list` read "turning round" as the Hush began and "blowing back (the Hush)" 35 seconds later. The subtitle showed
  inside a lane. Sheet [`docs/previews/m4-wind-lanes.png`](previews/m4-wind-lanes.png).
- **Found while filming:**
  - Ribbons winding right round a lane's middle read as coils; wound more slowly, they drew loops wherever a lane was
    seen end-on. Now each keeps its own side and sways.
  - Lanes looked knotted from afar: they wiggled every 80 blocks or so, between points 48 apart (bends of up to 12
    degrees in 4 blocks), and the ring turned about 95 degrees on the spot at the Arrival. Now they swing over about 300
    blocks and the ring's corners are rounded.
  - Lanes climbed to 392 to 412 round the Arrival: going over everything in the way carried them over stacks of tall
    floating islands, and the slope limit held them up there for hundreds of blocks. Also, the Obelisk was taken to be
    only as tall as its orb (it is 238 blocks tall). Now lanes take the clear gaps, under or between islands too, and
    swing round the Obelisk.
  - The spoke from the Arrival crossed the ring where the ring rounds the Arrival. Spokes now branch off the ring.
  - The subtitle never showed in the first captures: the wind's loop had started before the capture turned subtitles
    on (the capture's order, not the game's).

**Not seen yet**
- **A real Aeronautics ship flown by a player** along a lane, or against one: the rafts here had no engines.
- **Shader packs (Iris) and Distant Horizons** with the ribbons: not looked at yet.
- **Multiplayer:** each player's own client is sent their motion every tick while they are carried.
- **The Tides:** when they are built they turn the lanes instead of the Hush.

**Author's in-game test**
1. `/skyseam halcyon tp arrival` and look round: the lanes are the ribbons; `/skyseam halcyon lanes find` puts you
   beside the nearest one and `lanes list` names them all.
2. Fly a ship into one: it should be carried along at about the wind's speed. In survival, jump or fall into one and
   you are pushed along as you pass through.
3. `/skyseam halcyon time set hush`: within about half a minute the lanes blow the other way.
4. Settings under Config, Wind lanes.
5. Send screenshots and `logs/latest.log`.

## Wind lanes all over the Halcyon (2026-10-20)

DECISIONS K110. The author: "THE WIND lanes shouldnt be from specific plaaces. it should be around the dimension rather
than one spot". The ring round the archipelago and the spokes to the Spindle are gone; what a lane does, how it looks and
which way it blows are as before (K109).

**Built** (`halcyon/wind/WindLaneNetwork`, `WindLanes`)
- **Everywhere:** the Halcyon is cut into 720-block cells, and most (85 %) hold a lane 600 to 1,300 blocks long. Lanes
  head the way a slow swirl of the sky's air runs there, so neighbouring lanes run much the same way, like weather.
  Each is named for its heading and the region under it ("the west lane over the Cirrus Reefs").
- **Heights:** lanes pass under, between or over the floating islands and rock in their way, by the route that strays
  least from their way while keeping to the slope, now chosen over the whole lane at once.
- **Worked out as you go,** on a background thread, and sent to each player within 900 blocks of them as they travel.
- **Settings** (Config, Wind lanes): lane spacing (cell size) and the chance a cell has a lane, beside the old three.
  `lanes list` names those within 1,500 blocks; `find` and `ride` look 3,000 out.

**Verified (actually run)**
- **GameTests:** 65/65 in the full check (see the commit); during the work `seamMendsAfterTheShipLeaves` failed in some
  runs, one of the intermittent Seam tests the separate task is looking into.
- **`windLanesCoverTheHalcyon`:** 122 lanes in 144 cells (a square 8,640 blocks across), lanes far out in every direction,
  none through rock, a floating island or the Obelisk, at least 23 blocks over the ground, at most 25 degrees steep, the
  sharpest bend 7.6 degrees in 4 blocks (in two test worlds), the same lanes every time. One goes to 465 over a stack far
  out. Working out 144 cells took 2.4 seconds (off the server thread in play).
- **The real client** (`tools/capture/m4_wind_lanes.json`): 16 lanes within 1,500 blocks of the Arrival, every way round;
  a levitite raft in "the west lane over the Cirrus Reefs" was carried west at 10.5 then 9.3 blocks a second; a falling
  survival player drifted along it; the lanes turned round in the Hush; no server stall (the first try, working them out on
  the server thread, stalled it for 2 seconds on arriving). Sheet
  [`docs/previews/m4-wind-lanes-k110.png`](previews/m4-wind-lanes-k110.png).

**Not seen yet**
- As in K109: a real Aeronautics ship with engines, shader packs and Distant Horizons, multiplayer.
- **Flying far out** for a long while (lanes are worked out as you go; their ribbons may pop in at the edge of a new area
  for a moment).

**Author's in-game test**
1. Fly anywhere in the Halcyon: lanes should be in sight wherever you are. `/skyseam halcyon lanes list` names those near.
2. `/skyseam halcyon lanes find`, then fly a ship into it.
3. Try Config, Wind lanes, Lane spacing and Chance for more or fewer.
4. Send screenshots and `logs/latest.log`.

## The Tides and the noise bus (2026-10-22)

DECISIONS K111, K112. The author: "now lets complete. and that will be it for m4 we will come back to the almanac later
The noise bus and Tides."

**Built**
- **The Tides** (`halcyon/tide/`, `client/halcyon/tide/`): Calm most of the time; about every 25 minutes one Tide runs
  for 6 to 10, from the world's seed and the time (nothing saved but one started by command):
  - **Prismfall**: glass falls from the open sky, glinting; Prism Dust (a new item) falls onto the ground near you to
    gather; the sky leans pale glass-blue.
  - **Gale**: a crosswind carries ships and anyone in the air across it; streaks race past; the sky greys; the wind
    lanes blow back hard.
  - **Bloom**: pollen drifts on the air; the sky warms; helpers will make twice as much (when they exist, M5).
  - **Stillness** (rare): the wind lanes stop, every sound deepens, noise carries twice as far, the sky dims.
  - The wind lanes blow back while any Tide runs (the Hush no longer turns them).
  - As a Tide begins and ends you are told and hear it turn; live through one as it runs out and you earn its
    advancement (a new Halcyon tab: The Halcyon, then Glass Rain, Weather the Gale, In Full Bloom, The Quiet Hour).
  - The Halcyon has ambience now: a loop each for Calm, the four Tides and the Hush, crossfading.
  - **The Tide Vane** (brass ingots under a pinch of Prism Dust): its arrow and gem show the Tide running, or the next
    when it is under ten minutes off; use it for the forecast; a comparator reads which.
  - `/skyseam halcyon tide` (forecast), `tide start <tide> [minutes]`, `tide stop`. Settings under Config, Tides.
  - Other mods and pack scripts can listen for a Tide changing (`TideEvent.Changed`).
- **The noise bus** (`echo/NoiseBus`): sprinting on the ground, explosions and struck bells make noises with a radius,
  which the Tides stretch or shrink; creatures that hunt by sound will register to hear them (Hollow Listeners, the
  Cantor). `NoiseBus.ping` is ready for the Echo Sounder. `/skyseam noise emit <radius> [kind]` and `/skyseam noise
  watch` (each noise near you in chat, with a ring of light at its reach).
- **The capture tool** has a `sprint` step.

**Verified (actually run)**
- **The milestone check:** build ✓, runData ✓, **71/71 GameTests** ✓, server boot ✓.
- **GameTests** (`TideGameTests`, `NoiseGameTests`, `WindLaneGameTests`): one Tide in each of 400 slots, 6 to 10
  minutes long, never overlapping, the same every time (Prismfall 114, Gale 109, Bloom 137, Stillness 40, a Tide
  running 32 % of the time); a forced Tide takes over and a forced Calm stops one; the Tide Vane shows a forced
  Prismfall and gives a comparator 3; a noise reaches listeners within its radius, nearest first, and a keener one
  further off; a Stillness carries noise twice as far and a Prismfall or Gale 0.7 as far, easing in, and nowhere but
  the Halcyon; a struck bell carries 64 blocks and an explosion of power 2 carries 48; the wind lanes blow back in each
  Tide, 1.6 times as hard in a Gale, not at all in a Stillness, turning smoothly.
- **The real client** (`tools/capture/m4_tides.json`, `m4_tides_land.json`): each Tide forced in turn at the Arrival:
  the vane's gem and arrow changed with each; `lanes list` read "blowing back with the Tide", "blowing back hard (Gale)"
  and "still (Stillness)"; a ping of 30 carried 21 in a Prismfall and a Gale and 60 in a Stillness; a Bloom left to run
  out earned "In Full Bloom"; "The Halcyon" was earned on arriving; the message "Glass begins to fall: a Prismfall" and
  the subtitle "The Tide turns" showed as a Prismfall began. On the highlands, sprinting made a noise every half second
  (16 blocks), and with the dust set to every 2 seconds, 11 and then 19 pinches of Prism Dust fell within reach in a
  Prismfall (none fell at the Arrival, over the sea, as meant). All the new sounds pass `tools/audio/check.py`, whose
  loop check now measures a join against the sound's own movement from sample to sample (bright hiss moved more than
  its old fixed limit). Sheet [`docs/previews/m4-tides.png`](previews/m4-tides.png).
- **Found while filming:** the shards, pollen and Gale streaks were too faint to see at first (now larger and many
  more); Prism Dust falling round a player over the sea was lost far below (now it only falls onto ground near them).

**Not seen yet**
- **The ambience by ear**: the loops play (their subtitles show) but only the author can judge how they sound.
- **The Gale's crosswind** on a real ship or a gliding player, and Stillness's deeper sound by ear.
- **Shader packs and Distant Horizons** with the Tides' sky tints and particles; **multiplayer**.

**M4 against the spec's list** (Tides, wind lanes, Moorings, Almanac skeleton, the noise bus, the music manager, the
audio synthesis tool): the Tides, wind lanes, noise bus and the audio tool (since M1) are built. The Almanac is left for
later, as the author asked. **Moorings** (the Mooring Post that docks a ship, holds cargo and sets your respawn) and
**the music manager** (its slots, all empty) are not built yet.

**Author's in-game test**
1. In the Halcyon, `/skyseam halcyon tide` shows the Tide running and the next; `/skyseam halcyon tide start gale`
   (or `prismfall`, `bloom`, `stillness`) runs one now. Watch the sky, listen, and look at the wind lanes.
2. In a Prismfall, stand on land and gather the Prism Dust that falls; craft a Tide Vane (four brass ingots under a
   pinch of dust) and use it.
3. `/skyseam noise watch`, then sprint, ring a bell or set off TNT, in Calm and in a Stillness.
4. Let a Tide run out while you're in the Halcyon for its advancement. Settings under Config, Tides.
5. Send screenshots and `logs/latest.log`.

## The Tides do something, and are seen (2026-10-22)

DECISIONS K113. The author: the Tides didn't actually do anything yet and weren't very visible.

**Built**
- **Prismfall**: out under the open sky without a helmet, the glass cuts you, a heart about every other second; take
  cover or wear a helmet. Prism Dust falls every 4 seconds.
- **Gale**: the wind shoves you on your feet (it carries you standing and slows you walking into it), harder in the
  air, and bends arrows. Crosswind 9 blocks a second.
- **Bloom**: crops and saplings near you grow many times as fast, and you heal slowly out under the sky.
- **Stillness**: gravity eases to a little over half; falls and jumps float.
- **Seen**: a big title as each Tide begins; a badge at the top of the screen with its name, minutes left and a bar;
  real weather round you (falling glass, drifting pollen, streaks of wind) wherever the sky is open; thicker fog and
  stronger sky colours.

**Verified (actually run)**
- **The milestone check:** build ✓, runData ✓, **71/71 GameTests** ✓, server boot ✓.
- **The real client** (`tools/capture/m4_tide_effects.json`, survival, on a platform at Y 420 above every island):
  the Prismfall cut the player from 20 to 18 (the capture world is on Peaceful, whose healing made it back); the Gale
  carried a standing player 3 to 6 blocks, to the platform's wall; a Bloom grew all nine wheat seedlings on the
  platform to full in about a minute; a Stillness read gravity 0.044 (0.55 of 0.08). Title, badge, weather and fog
  for each in the sheet [`docs/previews/m4-tide-effects.png`](previews/m4-tide-effects.png).
- **Found while testing:** the Arrival lies under a floating island, which counts as cover from the glass (as it
  would from rain); the weather's texture was at first stretched far too long (pillars of light, squares of pollen);
  the Gale's push on a walking player, done from the server, would have stuttered their walking and was too weak
  against ground friction, so it runs on the player's own side now.

**Not seen yet**
- The glass on Normal or Hard difficulty (it should win against healing there), and a helmet keeping it off.
- The Gale with an elytra or on a real ship; shader packs with the weather sheets; multiplayer.
- **Existing settings files** keep the old Gale (6) and Prism Dust (12) values. Those in `run/` and `run/capture/`
  were updated; any other world's `skyseam-server.toml` needs `gale_crosswind = 9.0` and `prism_dust_every = 4`.

**Author's in-game test**
1. In survival, under the open sky, `/skyseam halcyon tide start prismfall`: the title, badge and falling glass;
   stand out without a helmet, then put one on or step under a roof.
2. `tide start gale`: stand still, then walk into the wind; shoot an arrow across it.
3. Plant wheat or a sapling, `tide start bloom`, and watch it grow.
4. `tide start stillness`, then jump off something.
5. Send screenshots and `logs/latest.log`.
