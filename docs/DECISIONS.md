# Decisions

One line per choice made where the spec is silent (spec §1 rule 1). Facts that turned out different from the
spec are in `DEVIATIONS.md`. The locked answers in spec §23 are not repeated here.

| # | When | Decision | Why |
|---|---|---|---|
| K1 | Step 0 | Package and group `com.selluastar.skyseam`, author `SelluaStar`, version `0.0.1`, license field "All Rights Reserved" | Matches Fealty's conventions. The real license is the author's call (TODO-MANUAL item 8) |
| K2 | Step 0 | Create, Aeronautics-bundled, Sable and Fealty run from `run/mods/` (copied by `syncRunMods`). GeckoLib is a normal dependency | Their nested jars (Flywheel, Simulated, Veil, rapier natives…) only load from a mods folder. Matches a real install |
| K3 | Step 0 | The reference images are stored as `reference/1-seam-rift.webp`, `2-arch-orb-mirror-lake.webp`, `3-sundered-obelisk.webp`, `4-conservatory-spire.webp`, numbered as in spec §3 | The author pasted them in a different order. Mapped by content to the §3 descriptions |
| K4 | Step 0 | `docs/Skyseam-Build-Spec.md` is generated from the PDF by `tools/docs/pdf_to_md.py`, and the PDF is kept beside it as the source of truth | Only the PDF was supplied. A text copy can be searched and quoted |
| K5 | Step 0 | `vendor/fealty-source/` is a snapshot of the Fealty *working tree* (not a commit), with every file marked read-only | The API 1.3.0 work is uncommitted there (DEVIATIONS D3) |
| K6 | Step 0 | `docs/PROGRESS.md` is the per-milestone report. `docs/STATUS.md` is a pointer to it | The prompt asked for PROGRESS.md and the spec for STATUS.md. Keeping one real file avoids two drifting copies |
| K7 | Step 0 | Repo on GitHub, `SelluaStar/Skyseam` (public). `main` leaves out `libs/` and `vendor/` (the Fealty jars and source). Branch `with-fealty` is `main` plus those two folders, and is brought up to date with `main` after each milestone. One commit per milestone on `main` | Spec §1 rule 12. Requested by the author: keep Fealty out of the public `main` but have a branch that builds as-is |
| K8 | Step 0 | Python tools run from a project venv `tools/.venv` (git-ignored). OGG files are written with `soundfile` (libsndfile Vorbis), not ffmpeg | ffmpeg isn't installed. The soundfile wheel bundles Vorbis support |
| K9 | Step 0 | Models are authored with the headless Blockbench server in `geckolib_model` format. `.animation.json` files are written by script from the `.bbmodel` keyframes. The desktop plugin is for viewing only | The desktop app has no GeckoLib codec. The headless server validates GeckoLib rules and renders on the GPU |
| K10 | Step 0 | NeoForge 21.1.256 (latest 21.1.x on 2026-10-08) | Needs ≥ 21.1.228. The newest build had every dependency boot cleanly |
| K11 | Step 0 | `run/eula.txt` has `eula=true` for local dev only | Approved by the author in the Step 0 plan. Needed for the headless `runServer` check |
| K12 | Step 0 | Gradle property `-PgametestNamespaces=<ns>` overrides which mod's GameTests run (default `skyseam`) | Used to run Sable's own physics tests headless, and useful for spikes |
| K13 | Step 0 | `reference/fealty-gui/` was not supplied. Skyseam's UI kit will use Fealty's GUI textures in `vendor/fealty-source/src/main/resources/assets/fealty/textures/gui/` as a **style reference only** (nothing copied), in a pastel-glass palette | Spec §13 asks to match Fealty's screens, or use a neutral style and say so here |
