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
