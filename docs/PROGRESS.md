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
