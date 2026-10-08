# Environment (Step 0)

Checked on **2026-10-08** on the author's machine: Windows 11 Home 10.0.26200, 31.8 GB RAM.
Every result below comes from a command that was actually run. Logs are in `docs/env-logs/`.

## Summary

| # | Check (spec §2 "Step zero") | Result |
|---|---|---|
| 1 | Java 21 + Gradle wrapper | ✓ Java 21.0.12 LTS (Oracle). Wrapper Gradle 8.14.3; its checksum matches Gradle's official one |
| 2 | Maven repos reachable | ✓ NeoForge, Create (createmod), Sable (ryanhcode), GeckoLib (Cloudsmith) and Modrinth all resolve. `gradlew build` passes |
| 3 | `runServer`, `runData`, `runGameTestServer` headless | ✓ All three boot with every dependency loaded. See notes A–C for three things M-1 must handle |
| 4 | Sable native physics headless | ✓ The Rapier native loads, a physics pipeline is created for every dimension, and Sable's own GameTests pass (5/5 required) |
| 5 | Blockbench MCP | ✓ Headless server (`blockbench-headless` 1.10.0): create, edit, validate (with GeckoLib rules), validate_animations, Bedrock geometry export and GPU render all work. ✓ Desktop plugin 1.10.0 on Blockbench 5.2.1 is connected, but it has **no GeckoLib codec** (GeckoLib Blockbench plugin not installed) |
| 6 | GPU + Node ≥ 23.6 | ✓ NVIDIA RTX 4060 (driver 591.86) + StarDesk virtual display. Node v24.20.0, npx 11.19.0 |
| 7 | Python 3 + Pillow + numpy + OGG writer + NBT writer | ✓ Python 3.14.7 with a project venv `tools/.venv`: numpy 2.5.3, Pillow 12.3.0, soundfile 0.14.0 (libsndfile 1.2.2, OGG Vorbis ✓), nbtlib 2.0.4. ffmpeg is **not** installed and not needed |
| 8 | Display for `runClient` | ✓ The client starts and reaches the title-screen load (sound engine, atlases, shaders) in about 27 s, then was closed |
| 9 | Can Claude Code see or hear the game? | ✗ No. It can read logs and view PNGs (textures, model renders, structure previews). It cannot view the game window or hear audio |

## Pinned versions (confirmed working together by the boots above)

| Thing | Version | Source / coordinates |
|---|---|---|
| Minecraft | 1.21.1 | |
| NeoForge | **21.1.256** (FML 4.0.45) | `net.neoforged:neoforge`. Minimum required by Sable/Simulated/Aeronautics: 21.1.228 |
| ModDevGradle | 2.0.148 | `net.neoforged.moddev` |
| Gradle | 8.14.3 | wrapper, copied from the Fealty project |
| Parchment | 2024.11.17 (1.21.1) | |
| GeckoLib | **4.9.3** | `software.bernie.geckolib:geckolib-neoforge-1.21.1:4.9.3` (Cloudsmith), published 2026-09-16 |
| Create | **6.0.10** | `maven.modrinth:create:6.0.10+mc1.21.1` (runtime). For compiling against Create later: `com.simibubi.create:create-1.21.1:6.0.10-280` (createmod), the build Sable's POM names |
| ↳ bundled in Create | Flywheel 1.0.6, Ponder 1.0.82+mc1.21.1, Registrate MC1.21-1.3.0+67 | jar-in-jar |
| Create Aeronautics | **1.3.2+mc1.21.1** (bundled jar) | `maven.modrinth:create-aeronautics:1.3.2+mc1.21.1`, file `create-aeronautics-bundled-1.21.1-1.3.2.jar` |
| ↳ bundled in it | Simulated 1.3.2, Aeronautics 1.3.2, Offroad 1.3.2 | jar-in-jar |
| Sable | **2.0.6** | `dev.ryanhcode.sable:sable-neoforge-1.21.1:2.0.6` (maven.ryanhcode.dev/releases) |
| ↳ bundled in Sable | Veil 4.3.2, sable_rapier 2.0.6 (natives), Sable Companion 1.6.0 | jar-in-jar |
| Fealty | **0.1.0**, API **1.3.0** | `libs/fealty-0.1.0.jar` (runtime), `libs/fealty-0.1.0+api.1.3.0-api.jar` (compile) |

The full mod list from the boot log has 14 mods: Create 6.0.10, Create Aeronautics 1.3.2 (`aeronautics` and `aeronautics_bundled`),
Create Offroad 1.3.2, Create Simulated 1.3.2, Fealty 0.1.0, Flywheel 1.0.6, GeckoLib 4 4.9.3, Minecraft 1.21.1,
NeoForge 21.1.256, Ponder 1.0.82+mc1.21.1, Sable 2.0.6, Sable Companion 1.6.0 and Veil 4.3.2.

### Fealty files on a fresh clone
The GitHub repo is public, so `main` does **not** contain the Fealty jars (`libs/`) or the Fealty source snapshot
(`vendor/`); both are git-ignored there. To build from a fresh clone, either:
- check out the `with-fealty` branch, which is `main` plus those two folders, or
- copy `fealty-0.1.0.jar` and `fealty-0.1.0+api.1.3.0-api.jar` from the Fealty project's `build/libs/` into `libs/`.
  The `vendor/fealty-source/` snapshot is optional; it is only read as an API reference.

### How the runtime mods are loaded
Create, Aeronautics and Sable keep their sub-mods inside nested jars, and those only load from a mods folder.
`build.gradle` therefore has a `runMods` configuration and a `syncRunMods` task. They copy
Create, Aeronautics-bundled, Sable and Fealty into `run/mods/` before every run, the same way a player installs
them. The log line `JarInJarDependencyLocator: Found 13 dependencies` confirms the nested jars load. GeckoLib
is a normal `implementation` dependency. Sable (non-transitive) and the Fealty API jar are `compileOnly`.

## External ids and APIs confirmed in the pinned jars (spec §1 rule 9)

Checked with `javap` and by reading the jars. These are the names `SableBridge` and `ExternalIds` will use.

| Spec name | Real name in the pinned jar | Status |
|---|---|---|
| `simulated:creative_physics_staff` | Item id ✓. Class `dev.simulated_team.simulated.content.physics_staff.PhysicsStaffItem extends Item`. Registered in `dev.simulated_team.simulated.index.SimItems` via Registrate with `rarity(EPIC).stacksTo(1)`. `public static float RANGE = 128.0f` | ✓ matches the spec |
| "no creative-mode check" | No `isCreative`/`instabuild`/creative reference in any `physics_staff` class or packet (constant-pool scan) | ✓ as far as a static scan can tell. Needs an in-game survival check |
| `SubLevelSerializer.toData` | `dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelSerializer.toData(ServerSubLevel, List<UUID>) → SubLevelData` | ✓ (the list is UUIDs) |
| `fullyLoad` | `SubLevelSerializer.fullyLoad(ServerLevel, SubLevelData) → ServerSubLevel`. Also `fromData(CompoundTag) → SubLevelData` | ✓ |
| `SubLevelAssemblyHelper.assembleBlocks` | `dev.ryanhcode.sable.api.SubLevelAssemblyHelper.assembleBlocks(ServerLevel, BlockPos, Iterable<BlockPos>, BoundingBox3ic) → ServerSubLevel`. Also `gatherConnectedBlocks`, `moveBlocks`, `moveTrackingPoints` | ✓ |
| "find the sub-level that holds a block" | `dev.ryanhcode.sable.companion.SableCompanion.INSTANCE.getContaining(Level, Vec3i \| Position \| Entity \| BlockEntity) → SubLevelAccess` (Sable Companion 1.6.0) | ✓ (spec named no class) |
| Per-dimension physics pack `data/<ns>/dimension_physics/<file>.json` | Reload listener folder `dimension_physics` ✓. Codec fields: `dimension`, `priority`, `base_gravity`, `base_pressure`, `pressure_function`, `universal_drag`, `magnetic_north`, `ignore_chunks` | ⚠ field names differ from the spec, see DEVIATIONS |
| Physics per dimension | Sable creates a Rapier pipeline for each loaded dimension (log: "Creating physics pipeline for ResourceKey[minecraft:dimension / …]") | ✓ promising for the Halcyon. Proven in M0 |
| `create:precision_mechanism`, `create:brass_casing` | `assets/create/models/item/precision_mechanism.json` + lang `item.create.precision_mechanism`. `assets/create/blockstates/brass_casing.json` + lang `block.create.brass_casing` | ✓ |
| `RegisterDimensionSpecialEffectsEvent` | `net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent` (mod bus, `register(ResourceLocation, DimensionSpecialEffects)`) | ✓ |
| `SelectMusicEvent` | `net.neoforged.neoforge.client.event.SelectMusicEvent` (cancellable; `getMusic`, `setMusic`, `overrideMusic`, `getPlayingMusic`) | ✓ |
| GeckoLib `AutoGlowingGeoLayer` + `_glowmask` | `software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer`. The texture suffix `_glowmask` is confirmed, and the glowmask must match the base texture size | ✓ |
| GeckoLib asset folders | `DefaultedGeoModel` builds `geo/<subtype>/<name>.geo.json` and `animations/<subtype>/<name>.animation.json`. Geometry format **1.12.0** is accepted (1.14.0 and 1.21.0 are rejected) | ✓ matches §20 (`geo/`, `animations/`) |
| Fealty API 1.3.0 | `FealtyApi.API_VERSION = "1.3.0"`. API jar contains `api.quest.*` (QuestType, QuestObjective, QuestContext, QuestGiver), `api.chain.*` (ChainHandler, ChainContext), `api.dialogue.*` (conditions, effects, DialogueOption), `api.event.{RumourEvent, DialogueEvent, DialogueBuildEvent, ChainStageEvent}`, `VillageInfo`, `RepApi` flags/roles/villages | ✓ M10 can go ahead (details checked again at M10 against `vendor/fealty-source/docs/API.md`) |

## Boot details

### `gradlew build` ✓
Log: `env-logs/gradle-build.log`. All dependencies resolved, BUILD SUCCESSFUL. There is no mod code yet.

### `runGameTestServer` ✓ (with one expected stop)
Log: `env-logs/runGameTestServer.log`. All 14 mods load. Then `IllegalArgumentException: No test functions were given!`,
because no Skyseam GameTests exist yet.
**Note A:** Gradle still reports BUILD SUCCESSFUL when the game-test server fails to start, so the exit code alone
cannot be trusted. M-1 adds a smoke GameTest, and every milestone checks the log for
`All N required tests passed`.

### Sable native physics on a headless server ✓
Log: `env-logs/runGameTestServer-sable.log` (`gradlew runGameTestServer -PgametestNamespaces=sable`).
- `Using game directory relative Sable Rapier native directory …\run\.sable\natives`. The native unpacked as `sable_rapier_x86_64_windows.dll`.
- `(sable_rapier) Rapier scene initialized` for overworld, nether and end.
- `5 GAME TESTS COMPLETE … All 5 required tests passed :)`. 3 *optional* Sable tests failed (`physicstest.testsnag`, `physicstest.testgravity`, `assemblytest.testallblocks`). These are Sable's own tests and not ours. Recorded in case they matter for M0.
- **Consequence:** the M0 crossing spike can run as a GameTest. No dev command for the author is needed for M0.

### `runServer` ✓ (with one gap)
Log: `env-logs/runServer.log`. A dev EULA file `run/eula.txt` was written with the author's approval. The server reached
`Done (0.693s)! For help, type "help"`, with Rapier initialised in all three dimensions.
**Note B:** Gradle does not forward stdin, so `stop` could not be sent and the process was killed. That is why the log
ends in BUILD FAILED. M-1 adds a dev-only switch (system property) that stops the server cleanly after boot, so
the headless boot check ends on its own.

### `runData` ✓
Log: `env-logs/runData.log`. All mods load, the data gatherer runs for `skyseam` (no providers yet), and the run exits cleanly.

### `runClient` ✓ (starts; the game window was not seen)
Log: `env-logs/runClient.log`. `Backend library: LWJGL version 3.3.3+5`, `Sound engine started`, block atlas
`2048x1024x4`, Veil post pipelines and Flywheel shaders compiled, all about 27 s after launch. The process was then
stopped (hence BUILD FAILED at the end of the log).
Warnings seen, none from Skyseam:
- 2× `Missing sound for event: minecraft:item.goat_horn…`, from the dev asset index.
- `Shader rendertype_entity_translucent_emissive could not find sampler Sampler2`.
- Sable's notice "Sable is loaded with Flywheel … expect compatibility issues". This is expected, because Create bundles Flywheel.
- Mixin refmap warnings, which are normal in a dev environment.

**Note C:** the `ApiStatus$ScheduledForRemoval` and Iris `PipelineManager` "Error loading class" mixin warnings are
optional-compat probes inside dependency mods and are harmless.

## Model pipeline (spec §2 and §20)
- **Headless server** (`mcp__blockbench-headless__*`, plugin 1.10.0). It accepts absolute paths inside the project. The probe model `art/models/_probe/probe.bbmodel` (format `geckolib_model`, 2 bones, 1 clip) went through:
  - `bbmodel_validate` with `self_test`: passed. Every gate discriminates, and GeckoLib rules ran with 0 errors and 2 warnings (no modid or identifier set).
  - `bbmodel_validate_animations`: passed.
  - `bbmodel_export_bedrock_geometry`: wrote format `1.12.0`, which is the version GeckoLib accepts.
  - `bbmodel_render`: rendered the posed clip at t=1 s in 2.3 s on the GPU. Evidence: `env-logs/blockbench-probe-render.png`. The first render downloaded the render engine (three, three-blockbench, Dawn) into the per-user npm cache.
  - The probe was deleted afterwards.
- **Animation export:** the headless server has no `.animation.json` exporter, as the spec expected. Claude Code writes the Bedrock/GeckoLib animation JSON from the `.bbmodel` keyframes with a script, and checks clips with `bbmodel_validate_animations` (spec §2).
- **Desktop plugin** (`mcp__blockbench__*`, plugin 1.10.0, Blockbench 5.2.1): connected and used only for viewing.
  - The **GeckoLib Blockbench plugin is not installed**, so the desktop app has no GeckoLib format or codec, and `geckolib_*` desktop tools stay disabled. Not needed, because the headless server handles `geckolib_model`. Optional for the author: File → Plugins → "GeckoLib Models & Animations".
  - **`risky_eval` is enabled.** Spec §2 recommends switching it off in Blockbench settings unless needed. Claude Code does not use it.
- **Fallback** if either server fails: generate geo and animation JSON by script, and note it here.

## Audio, texture and structure tooling
- Run tools with `tools/.venv/Scripts/python.exe` (the venv is git-ignored; recreate with `python -m venv tools/.venv` then `tools/.venv/Scripts/python.exe -m pip install numpy pillow soundfile nbtlib`).
- Verified in Step 0 (scratchpad files, since deleted):
  - wrote a 1 s mono OGG Vorbis at 44.1 kHz and read it back (peak 0.303, 5.6 KB);
  - opened all four `reference/*.webp` with Pillow;
  - round-tripped a gzipped structure-style NBT with nbtlib.
- `tools/docs/pdf_to_md.py` regenerates `docs/Skyseam-Build-Spec.md` from the PDF (standard library only).

## What Claude Code cannot do here (unchanged from spec §2)
It cannot see the running game window or hear any sound. It can read logs, run headless servers and GameTests, and view
PNGs. Anything that needs eyes or ears is reported as "built but unverified".
