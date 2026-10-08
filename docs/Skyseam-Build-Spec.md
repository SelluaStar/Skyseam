<!-- Machine conversion of docs/Skyseam-Build-Spec.pdf by tools/docs/pdf_to_md.py. -->
<!-- The PDF is the source of truth: if this file and the PDF disagree, the PDF wins. -->
<!-- Tables are flattened: cells on one line are joined with ' | ', and wrapped cells continue on the next lines. -->

<!-- page 1 of 78 -->
Oct 8, 2026 ·  @sb

## 1. Read this first: how Claude Code should use this document

This is the build specification for Skyseam, a Minecraft 1.21.1 NeoForge mod. It is the
Design Bible with one change: every item now says who builds it. Sections 1 and 2 tell
Claude Code how to read the rest, and they win over anything below them. Section 23
holds the locked answers to every open question, so there is nothing to ask the author
before starting.

### Reading order

1. Sections 1 and 2, then section 23.
2. Sections 3 to 15 and 17 for the design. Skim 3 and 4 for the story and read 5 to 15 for the
systems.
3. Section 19 for the milestone order. Build in that order.
4. Sections 20 to 22 alongside each milestone: the asset list, the audio plan and the
manual list.
5. Section 16 is a prompt for a different project (Fealty), so do not run it. Section 18 is a
backlog, so build nothing from it unless the author names the item.

### The five tags

Every item that has to be built carries one of these tags, always written in code style.
Tag | What it means | Claude Code should | Claude Code must
not
`[CC]` | Claude Code builds it
and can check it itself
Build it fully. It is done when
the build, the data
generator and its GameTest
or headless server check
pass
Leave a TODO
where the feature
should be

<!-- page 2 of 78 -->
An item with no tag: treat art, sound and structures as `[FIRST PASS]` and everything else
as `[CC]`. Text with no tag is design background.

### Standing rules

1. Do not wait on the author. The open questions are answered in section 23. Where this
spec is silent, pick the simplest reading, record it in `docs/DECISIONS.md` and continue.
Stop and ask only for a real blocker: a dependency that cannot be downloaded, a
license problem, or a choice that cannot be undone.
2. Never block on `[SUPPORT ONLY]` or `[MANUAL]`. Build the plumbing or the stub, record
it, move on.
Tag | What it means | Claude Code should | Claude Code must
not
`[FIRST`
`PASS]`
Claude Code builds a
complete, working
first version. Only the
author can judge how
it looks, sounds or
feels
Build the real thing, wired in
and easy to swap. Look at
what it can (generated
PNGs, renders). Report it
as "built, needs your eyes
or ears"
Call it final, or say
that it looks or
sounds good
`[SUPPORT`
`ONLY]`
Claude Code cannot
make the content,
and the content is
optional ("add it later
if we need it")
Build the hooks, config,
registrations and empty
slots, plus a how-to-add
guide, so the author can
drop the content in with no
code change. The game
must run cleanly with the
slot empty
Invent substitute
content or ship fake
files
`[MANUAL]` | Claude Code cannot
make it, and the final
mod will contain it.
The author's words:
"I'll add this later
manually"
Build a working stub, add
one line to `TODO-`
`MANUAL.md`, and keep going
Block on it, guess at
it, or skip it silently
`[OTHER`
`REPO]`
Built in a different
project by a different
session (the Fealty
features)
Write only the Skyseam
side, against the
documented API, inside one
package (`compat/fealty`)
Edit the other
project

<!-- page 3 of 78 -->
3. Never fake what you cannot make. No placeholder "music", no empty files with real-
looking names, no text that pretends to be final. A stub is labeled in `TODO-MANUAL.md`
and in `docs/STATUS.md`.
4. Everything is swappable without code. Textures, models, animations, sounds, music,
structures and lang files sit at the paths in section 20 and are found by name.
Replacing a file with a file of the same name must work with no code change.
5. Everything tunable is data or config. Each number in this document that is marked
config or tune goes into a config file or a data pack JSON, with the document's value as
the default.
6. Placeholders first. Every entity, block and item works as a plain placeholder before any
art exists, so quests and fights can be tested early.
7. Fealty is required, the rest stay optional. Fealty is a required dependency, declared as
required in `neoforge.mods.toml`, and Skyseam compiles against its API version 1.3.0.
FTB Quests and KubeJS are soft dependencies: Skyseam starts and plays without
them, so guard every use with a loaded-mod check and keep their classes out of the
main code path.
8. Nothing copied. Use Create, Create Aeronautics (the Simulated project) and Sable
through their public APIs only. Never copy their code, models, textures or sounds.
9. One bridge for outside ids. Every Sable, Simulated and Create class, id and method is
touched in one place (`SableBridge` plus an `ExternalIds` class), with pinned versions.
This document read those names from source at an earlier version, so confirm each
one against the pinned jar before relying on it.
10. Server decides, client shows. Gameplay logic runs on the server. The client plays
visuals and sounds from packets.
11. Look at what you make. Generated textures, model renders and structure previews
are PNG files you can open and view. Do that before reporting a `[FIRST PASS]` item.
You still cannot hear audio or see the running game.
12. Report honestly. At each milestone write `docs/STATUS.md`: what was built, how it was
verified, what only the author can check, and what is still a stub. Commit once per
milestone with the milestone name in the message.

<!-- page 4 of 78 -->

### Files to create


## 2. Who builds what

This section is the source of truth for responsibility. If a later section disagrees with the
table below, the table wins. The tags are explained in section 1.

### Step zero: check the environment

Before writing mod code, create `docs/ENVIRONMENT.md` with the answers to these
checks. Several tags depend on them, and the right fallback differs between a cloud
sandbox and the author's own machine.
File | Purpose
`docs/ENVIRONMENT.md` | Written first. The answers to the environment
checks in section 2
`docs/DECISIONS.md` | One line for each choice made where this spec was
silent
`docs/STATUS.md` | Per milestone: built, how it was verified, what
needs the author's eyes or ears, what is stubbed
`docs/ASSET-PATHS.md` | Every asset kind, its folder, its naming rule and how
to replace it (section 20)
`docs/ADDING-MUSIC.md` | The exact steps to fill each music slot (section 21)
`TODO-MANUAL.md` | One line per `[MANUAL]` item and per `[SUPPORT`
`ONLY]` slot: what it is, which file goes where, and
what the game does until then. Seed it from
section 22
`reference/` | The four reference images from section 3, put
there by the author. If they are missing, build from
the written descriptions in section 14 and say so in
`docs/DECISIONS.md`

<!-- page 5 of 78 -->

### Responsibility table

Check | Why it matters
Java 21 and the Gradle wrapper run, and
Gradle can reach the NeoForge, Create,
Sable, GeckoLib and Modrinth Maven
repositories
Without this nothing builds. Report it and
stop
`runServer`, `runData` and
`runGameTestServer` run headless
They are the main automatic checks
Sable's native physics library loads on a
headless server
Decides whether the M0 spike runs as a
GameTest or needs the author
Which Blockbench MCP is connected: the
desktop plugin, the headless `.bbmodel`
server, or neither
Decides the model pipeline
A GPU and Node 23.6 or newer, for the
headless renderer
Without them Claude Code cannot preview
a model
Python 3 with Pillow and numpy, an OGG
writer (ffmpeg with libvorbis, or the soundfile
package) and an NBT writer (the nbtlib
package)
Textures, synthesized sounds and
structures
A display, real or virtual, for `runClient` | Lets Claude Code launch the client and
read its log for model or sound errors
Area | Tag | How | Checked by
Mod code: registries,
entities, AI, blocks,
items, menus,
packets, systems,
config
`[CC]` | NeoForge Java, with
GeckoLib 4 for animated
entities and block entities
`./gradlew build`,
GameTests,
headless server boot

<!-- page 6 of 78 -->
Area | Tag | How | Checked by
Data: recipes, loot
tables,
advancements, tags,
dimension, biomes,
noise settings,
physics pack, lang
keys, the
`sounds.json`
structure
`[CC]` | `runData` or hand-written
JSON. Version 1.21 folders
are singular (`recipe`,
`advancement`,
`loot_table`, `structure`)
`runData`, server
boot, a GameTest
per recipe and
advancement
Ship crossing
between dimensions
`[CC]`, gated
by the M0
spike
Route A or B in section 6,
with the fallback in
section 23
A GameTest if Sable
loads headless,
otherwise a dev
command the author
runs
Seam, Echo Sounder,
sky and beam
rendering code,
particle types
`[CC]` code,
`[FIRST`
`PASS]` look
Custom renderers,
additive quads, particle
providers
It compiles and runs.
The look is the
author's call
Screens and menus | `[CC]` logic,
`[FIRST`
`PASS]` art
Screens, menus and
payloads, with panels and
sprites drawn by script
Menu GameTests.
The author checks
layout in game
Entity and block-
entity models,
animations, glow
masks
`[FIRST`
`PASS]`
Blockbench MCP if
connected, otherwise
generated geo JSON.
Sources live in
`art/models/`
MCP validators
when present, the
client log, renders if
there is a GPU, and
the author's eyes
Textures for blocks,
items, entities,
particles, sky and GUI
`[FIRST`
`PASS]`
Pillow and numpy
generators in
`tools/textures/`, or the
MCP paint tools
Claude Code views
the PNGs. The
author's eyes

<!-- page 7 of 78 -->
Area | Tag | How | Checked by
Structures (all 15) | `[FIRST`
`PASS]`
Generator scripts in
`tools/structures/`
write `.nbt` files. Hero
builds are parametric
(towers, domes, arches,
rings) and use the
pictures in `reference/`
A GameTest places
each file and checks
size and block
counts. Claude Code
views isometric
previews
Sound effects | `[FIRST`
`PASS]`
Vanilla stand-ins through
`"type": "event"`
entries, plus synthesized
mono `.ogg` files for the
hero sounds
(`tools/audio/`)
A script validates
files, subtitles and
`sounds.json`, and
measures length,
peak and loop
seams. Claude Code
cannot hear them
Music | `[SUPPORT`
`ONLY]`
Music manager, empty
slots and a how-to guide
(section 21)
The game runs
cleanly with every
slot empty
Tuning, balance,
difficulty
`[FIRST`
`PASS]`
This document's numbers
as defaults
The author's
playtests
The eight Fealty
features
`[OTHER`
`REPO]`
The prompt in section 16,
run in the Fealty project
Fealty's own
GameTests
Skyseam side of
Fealty: rumour data,
conversations, favor
objectives, the
Stargazer
`[CC]` | Built against Fealty 1.3.0 in
M10, after the Fealty
project finishes the
section 16 work
GameTests with
Fealty on the
classpath
Advancements,
custom stats, tags
`[CC]` | Data generation | `runData`, and one
GameTest per
advancement chain
FTB Quests chapters,
quests and rewards
`[MANUAL]` | The author builds them
from the ids in section 17
Not applicable
Final sound design for
the hero moments
`[MANUAL]` | Replace the synthesized
files by name (section 22)
The author's ears

<!-- page 8 of 78 -->

### What Claude Code cannot do

Hear audio. It can measure a file (length, peak level, loop seam) and view a
spectrogram, but it cannot judge taste.
See the running game. It can view any PNG, so generated textures, model renders and
structure previews are checkable, but the game window is not.
Compose music or record sound.
Judge feel, difficulty, framerate or shader-pack behavior.
Run a real multiplayer session, or publish the mod.

### Tools that help (all optional)

Blockbench MCP (jasonjgardner/blockbench-mcp-plugin). It comes as two servers, and
either or both can be connected.
Area | Tag | How | Checked by
Final art for the hero
pieces and the hero
structures
`[MANUAL]` | Replace the first-pass
files by name (section 22)
The author's eyes
Real-hardware
testing: performance,
shader packs,
multiplayer,
dedicated server
`[MANUAL]` | Claude Code boots a
dedicated server and
writes the tests it can
The author
Mod icon, page art,
trailer, license check,
publishing
`[MANUAL]` | A plain placeholder icon
only
The author

<!-- page 9 of 78 -->
```text
# Desktop plugin over HTTP. Blockbench desktop must be running with the
plugin loaded
# (File > Plugins > Load Plugin from URL:
https://jasonjgardner.github.io/blockbench-mcp-plugin/mcp.js)
claude mcp add blockbench --transport http http://localhost:3000/bb-mcp
# Headless server over stdio. No Blockbench needed. --root limits file access
to that folder
claude mcp add bbmodel --transport stdio -- npx -y
github:jasonjgardner/blockbench-mcp-plugin --root /path/to/art/models
```
The headless server edits `.bbmodel` files (`bbmodel_create`, `bbmodel_edit`,
`bbmodel_add_texture`), checks them (`bbmodel_validate`, which includes GeckoLib
rules, and `bbmodel_validate_animations`), exports Bedrock geometry
(`bbmodel_export_bedrock_geometry`) and renders PNG previews (`bbmodel_render`,
`bbmodel_contact_sheet`, GPU needed). Its documentation lists no animation export,
so Claude Code writes each `.animation.json` itself from the model's keyframes (it is
plain JSON) and checks the clips with `bbmodel_validate_animations`.
The desktop plugin adds painting and animation tools and screenshots of the 3D view,
which is the only way to see a model without a GPU render. Its `risky_eval` tool runs
any JavaScript it is sent, so leave it switched off in Blockbench settings unless it is
needed.
Treat both as experimental. If a call fails, fall back to generated geo JSON and note it in
`docs/ENVIRONMENT.md`.
An audio-generation MCP or API, only if the author has one. Nothing in this document
depends on it. Section 21 explains how the audio is covered without it.

## 3. The pitch

Build tags. Background only. Nothing in this section is built directly. The things it names
are built in the sections that follow.
Skyseam sends an airship through a crack in the sky into a small, bounded pocket world of
floating islands, mirror seas and living light, and makes you earn the way back out.
A villager you have won over whispers about a scar in the sky. Five strange favors later you
craft the Harmonic Aperture, mount it on a Create Aeronautics ship and fly to the marked
spot. The sky unpicks itself like a seam and your whole ship slips into the Halcyon, an
archipelago about 3 km across, lit by a single hovering lantern-orb instead of a sun. Inside

<!-- page 10 of 78 -->
are helper creatures that produce things you truly need, three Stay Guardian bosses, a
central puzzle spire, and the parts for two tools: the Pneumatic Coupler (a craftable copy
of the creative physics staff) and the Echo Sounder (a sonar tool, and the only way to find
the hidden exit seams).

### Design pillars

Flight is the verb. Arrival, travel, boss arenas, docking and loot are all built around
airships.
Calm, beautiful, slightly wrong. The world looks serene. Danger arrives as events and
set pieces, not constant mob spam.
Every helper is a lever. No cosmetic-only creatures: each passive mob produces
something the player needs.
Earn the exit. You cannot leave until you have built the Echo Sounder and found a
hidden seam.
Quest-native. Every milestone is a discrete advancement, stat or item tag, so FTB
Quests needs no custom code.
Small enough to know by heart. A bounded world you can learn, with replay value from
Tides and events.
No armor. Rewards are tools, utilities, consumables and blocks.

### Working names

All names are working names. Keep them unless the author changes them in section 23.
Put every player-visible name in the lang file so renaming is one pass.
Thing | Working name | Alternates
Mod and namespace | Skyseam (`skyseam`) | Halcyon Rift, Opaline
The portal | The Seam | The Rent, the Scar
Pocket dimension | The Halcyon | The Lull
Entry block (the final craft)Harmonic Aperture | Seam Ripper, Seam Key
Staff copy | Pneumatic Coupler | the author's call
Sonar tool | Echo Sounder | Resonance Sounder
Map from the villager | Skychart | Seam Chart

<!-- page 11 of 78 -->

### How the four reference images are used

The four pictures are style references. Claude Code should open them from `reference/`
before building the Seam look, the sky and the structures (sections 6, 7 and 14). If the files
are named differently, match them by the descriptions below.
The locked decisions are in section 23.
Thing | Working name | Alternates
Quest villager | Wystan Starling, the Stargazerany name
Ancient builders | The Wrights (Skyfolk) | none
Image | Becomes
1. Rift from Minecraft Dungeons | The Seam's look: ragged voxel-edged fissure,
white glowing outline, pastel prismatic interior,
dust streaming off one edge
2. Arch, glowing orb, petal field, mirror
lake
The Halcyon's mood board: Lantern-Sun,
Petalwash Meadows, Mirror Sea, and the
Standing Arch gate to the first boss
3. Split floating monolith over a cliff gateThe Sundered Obelisk, the landmark you see
first on arrival (its gap rhymes with the Seam)
4. Domed overgrown tower, teal and
gold
The Conservatory Spire, the central puzzle tower
with one themed floor per relic

<!-- page 12 of 78 -->

### The journey at a glance

The Seam is the door in and a Stitch is the door out. The dashed Spire is a capstone you
can reach any time after the three Stays, and it is not needed to leave.
1. Rumour. A Trusted villager mentions the scar in the sky and hands you the Starlit
Letter.
2. The Stargazer. Wystan Starling lives in a different village, marked by a Brass Telescope
on a rooftop, and opens up to the letter.
3. Five favors. Look, talk, listen, fly and endure. Each favor rewards one component.
4. Closing talk. He asks why you want to go, then hands over the Keystone and the
Skychart.
5. Craft. The Harmonic Aperture, from the five components, the Keystone and two
Create parts.
journey at a glance · 2 worlds, 2 doors, 10 steps

<!-- page 13 of 78 -->
6. Fly. Bolt the Aperture to an Aeronautics ship and follow the Skychart to the marked
site, at least 30 blocks above the ground.
7. The Seam opens. About six seconds of charge, hairline, crack and snapping golden
threads, then the ship crosses once it overlaps the opening.
8. Arrive. The Anchorage on the Sundered Obelisk island: a Mooring Post, Wrightling
stalls and the first Almanac pages.
9. Explore. Helpers give Moonsilk, Antler Glass and Whale Song Shells, and the wrecks
hold logbook pages.
10. Three Stays. The Prism Regent, Tempest Skyray and Hollow Cantor in any order, each
dropping a relic.
11. Craft two tools. The Skyray's Pneumatic Heart builds the Pneumatic Coupler, and the
Cantor's Resonant Tine builds the Echo Sounder.
12. Find a Stitch. A ping draws a dotted outline of one of the four hidden exits for 20
seconds.
13. Leave. Charge the Aperture beside the Stitch and you return to the Seam site in the
Overworld.
14. Capstone. Put the three relics into the Glyph Cipher at the Spire's summit for the
Stillpoint Heart.

## 4. Lore

Build tags. Background for Claude Code. Every piece of in-game text that comes from this
lore (the twelve logbook pages, Almanac entries, Wrightling lines, the carved motto) is
`[FIRST PASS]`. Write it in `assets/skyseam/lang/en_us.json` in the tone below, and the
author will reword whatever they do not like.
The Halcyon is a shelter the Wrights stitched into the sky, and the Seam is the one stitch
they left loose.
The Wrights were airship builders who watched a storm they called the Unmooring tear
the sky open. They wove a pocket of permanent calm out of the torn fabric, sailed their
fleet inside, and sewed the opening shut. Villagers still tell it as a nursery story: the sky
has a scar, and the old stargazers keep the needle.
Three machines called the Stays hold the pocket's calm: the Mirror Stay (light), the Wind
Stay (air) and the Echo Stay (sound). Over the centuries their guardians stopped
recognising visitors and began treating every ship as the storm.

<!-- page 14 of 78 -->
Why you cannot just leave. The Wrights hid the exits so the storm could never trace them.
A hidden stitch can only be heard with an Echo Sounder, and its parts are guarded by the
Stays. The Wright motto is carved at the Anchorage: no one leaves who cannot listen.
The wrecks. A few Wright ships never reached the Anchorage. Their twelve logbook
pages teach the glyph script that the Spire's final puzzle is written in, so exploring the
wrecks is how knowledge, not just loot, becomes progress.

### Tone

Wistful and lonely, never grim. The Halcyon is abandoned, not evil.
Story arrives through logbooks, Wrightling dialogue and the Almanac, never cutscenes.
Beauty first: the first thirty seconds after arrival should make the player stop flying
and look.

## 5. Part 1: Before the Rift

Build tags. `[CC]` the chain logic, the five favor objectives, the clue generator, the rumour
roll, the items below, and the Star Dial and Bell Sequence puzzles. `[FIRST PASS]` every
model, texture, GUI sprite and sound, and the wording of the dialogue and rumour lines.
`[OTHER REPO]` the Fealty features this chain stands on (sections 15 and 16). Fealty is a
required dependency, so this chain is built in M10, after the Fealty project has finished the
section 16 work.
The Overworld half is a six-step chain built on Fealty's existing rumour, quest-giver and
dialogue systems: hear a rumour, meet the Stargazer, finish five favors that each yield one
component, receive the Keystone and the Skychart, then craft the Harmonic Aperture.
1. Rumour. Once a player is Trusted in a village, ordinary villagers have a rare chance to
mention the scar in the sky.
2. The Stargazer. Wystan Starling lives in a different village, marked by a Brass Telescope
on a rooftop. He only opens up to a player carrying the Starlit Letter from the rumour.
3. Five favors. Each uses a different verb (look, talk, listen, fly, endure) and rewards one
component.
4. Closing talk. He asks why you want to go. Your answer picks a small cosmetic title and
unlocks the Keystone and the Skychart.
5. Craft the Harmonic Aperture from the five components, the Keystone and two Create
parts.
6. Mount and fly. Bolt it to an Aeronautics ship and follow the Skychart.

<!-- page 15 of 78 -->

### The rumour roll

Eligible: Trusted tier or higher (reuses the start tier of Fealty's rare villager chain).
Chance: 3 percent per conversation, once per villager per day. Each miss adds 1
percent (cap 12). Guaranteed after 30 eligible conversations. All values are config.
Three stages, each delivered by a different villager so players meet several: Whisper
(the scar exists), Lead (a Survey Map to the Stargazer's village), Introduction (the
Starlit Letter, a sealed letter item).
Sample Whisper lines: "My flock won't graze under that patch of sky." / "The bells
stopped ringing the day the light split." / "Ask a stargazer, not a farmer."
Write at least 30 Whisper lines, grouped by villager profession, in the same voice as the
samples.

### The five favors

Favor | Verb | What the player
does
ComponentObjective id
The Lost
Survey
Look | Follow riddle clues
from the Survey
Notes to the
Hollow Cairn and
align the three-ring
Star Dial on a clear
night
Star-Glass
Lens
`skyseam:survey` (extends
`explore`)
Three
Hands, One
Seal
Talk | Win a sealed
accord from the
elders of three
Trusted villages,
each with its own
dialogue challenge
(settle a feud by
checking facts,
deliver a crafted
item, return a
runaway animal)
Triple-Wax
Seal
`skyseam:three_seals`
(builds on `courier` and
Fealty letters)

<!-- page 16 of 78 -->
Register each objective id as a Fealty quest type.
Clues for the Cairn and the Chapel are generated per world by locating the nearest
structure, the same way Fealty's chain maps are made, so no world is unwinnable. A lost
component can be re-earned from the Stargazer for a fee, with the puzzle skipped.

### Keystone and Skychart

Skywright's Keystone is the one ingredient that cannot be crafted. It is the Stargazer's
own heirloom.
Skychart is a map item marking the Seam site. With an Aperture mounted it also shows
the live gauge from the next section.
Favor | Verb | What the player
does
ComponentObjective id
The Silent
Chapel
Listen | Find the Silent
Chapel, recover
three bell clappers,
then play the
sequence carved
on the wall
Tuned
Clapper
`skyseam:chapel_bells`
The Ring
Run
Fly | Fly a ship carrying
the Flight Logbook
through 12 Wind
Rings over the
village inside four
minutes
Windwheel
Gear
`skyseam:ring_run`
The
Stormglass
Night
EndureBuild a Skyspike
lightning tower,
hold the empty vial
on it through a
thunderstorm and
beat the
Stormlings it draws
Stormglass
Vial
`skyseam:stormglass`

<!-- page 17 of 78 -->

### Harmonic Aperture recipe

```text
Star-Glass Lens   | Precision Mechanism  | Triple-Wax Seal
Brass Casing      | Skywright's Keystone | Brass Casing
Tuned Clapper     | Windwheel Gear       | Stormglass Vial
```
The two Create parts are `create:precision_mechanism` and `create:brass_casing`
(confirm the ids against the pinned Create version). The block is soulbound to the crafter,
and teammates can use it if the pack runs FTB Teams.

## 6. The Seam (portal)

Build tags. `[CC]` the Seam entity, trigger rules, charge logic, gauge, force-loading, ship
transfer, mending and the debug commands. `[FIRST PASS]` the look of the reveal
(renderer, particles, interior sky, camera shake) and every sound in the beat table. The
ship transfer is gated by the M0 spike in section 19.
The Seam opens only while a qualifying ship is flying near the marked site. It appears at
the ship's altitude, takes the whole ship through, and mends when nobody is left near it.

### Trigger rules

Rule | Value (all config)
Site | One (x, z) per world, 600 to 900 blocks from the
Stargazer's village, saved in world data and drawn
on the Skychart
Entry radius48 blocks horizontal from the site
Ship | A Sable sub-level containing a Harmonic Aperture,
with a player piloting it
Flying | Ship speed above 2 blocks per second and no
ground contact
Altitude | Lowest ship block at least 30 blocks above the
ground at the site (motion-blocking height ignoring
leaves at that x, z)
Dimension | Overworld by default

<!-- page 18 of 78 -->

### The reveal, beat by beat

Rule | Value (all config)
Charge | 5 seconds with every rule met. Dropping a rule
pauses and drains the gauge
Spawn point(site x, ship y, site z), facing the ship's approach
bearing
Size | Seam width and height = ship bounds x 1.5,
clamped to 16 to 64 blocks, so large ships fit
Hold radius | 128 blocks. The Seam mends 5 seconds after the
last qualifying ship leaves, or after 120 seconds
regardless
Beat | Time | Player sees and hears
1. Listening | during chargeAperture core spins up with a rising hum. A HUD
gauge shows distance and three ticks (flying,
altitude, radius). Heat shimmer at the site
2. Hairline | 0 to 1.5 s | One thin white vertical line, a glass "tink", drifting
motes
3. Crack | 1.5 to 3.5 s | Voxel-edged cracks branch outward in steps, like
screenshot 1. Cloth-tearing sound with a glass
chime. Clouds part in a ring
4. Threads snap3 to 5 s | Golden threads along the edges snap one at a time,
each with a harp pluck and a spark, and each snap
widens the opening a notch
5. Open | 5 to 6 s | Pastel prismatic interior fades in (pink, orange,
teal), dust streams off one edge, the white outline
pulses, soft god-rays, light camera shake
6. Stable | loop | Low hum, edge shimmer, a slow ring pulse every 6 s,
and a gentle pull on ships within 60 blocks
7. Crossing | on overlap | When the ship's bounds overlap the opening by 60
percent: pearl-white flash, then the Halcyon arrival
title

<!-- page 19 of 78 -->
Everyone within 256 blocks sees the animation, not just the pilot.

### How it should be built

The Seam is an entity with a custom renderer, not a block, because it hangs in open air
at any height.
The interior is a pre-baked Halcyon sky texture with parallax, not a true portal render. It
is cheap and stays compatible with shader packs.
Edges are additive line strips and translucent quads. Everything else is particles and
sound.

### Moving the ship

1. Freeze the ship and riders, then snapshot blocks, block entities, entities inside the
bounds, and velocity.
2. Choose a clear Arrival Lane in the Halcyon (64 by 64 blocks of open air at Y 240).
3. Move it. Route A: Sable's own save and load path (`SubLevelSerializer.toData` then
`fullyLoad` into the Halcyon level). Route B: copy blocks across and rebuild with
`SubLevelAssemblyHelper.assembleBlocks`.
4. Restore velocity at 50 percent, place riders at the same relative positions, and mend
the Overworld Seam.
Both routes exist in Sable's public code, but neither is proven for cross-dimension moves.
The first prototype task is a spike that moves a test ship between two dimensions. Default
size cap: 64 by 64 by 64 blocks. If the spike shows that neither route works, use the
fallback in section 23 (decision 2) and record the result in `docs/DECISIONS.md`.

### Edge cases

Only ships with an Aperture cross. Anyone standing on the ship rides along. People on
foot elsewhere are left behind.
Riders who log out are saved with the snapshot and reappear at the Arrival Lane.
Chunks around the site stay force-loaded while the Seam is open.
Beat | Time | Player sees and hears
8. Mending | on leave | Threads re-stitch bottom to top, the crack narrows,
a closing chime, faint scar particles for 60 s during
which it cannot reopen

<!-- page 20 of 78 -->
Losing the pilot, or the Aperture, mid-charge cancels the charge.
A creative-only command opens a Seam anywhere for testing.
Operator-only debug commands live under `/skyseam`: open and mend a Seam, force a
Tide, give the Almanac, and teleport to landmarks. Claude Code needs them for
GameTests and the author needs them for filming.

## 7. The Halcyon (pocket dimension)

Build tags. `[CC]` the dimension and dimension type, terrain generation, biomes, fixed
landmark positions, the Veil, Rebound, the Lantern-Sun cycle logic, the physics data pack
and the flora blocks. `[FIRST PASS]` the look: sky and fog colors, the Lantern-Sun art, the
cloud texture, biome palettes and the ambience loops. `[SUPPORT ONLY]` music (the music
line under Flora and sound).
The Halcyon is a bounded archipelago about 3 km across, with a mirror sea below and a
lantern-orb above, built so that flying is the best way to see it.

<!-- page 21 of 78 -->

### Shape and size limit


### Sky and light

There is no sun or moon. The Lantern-Sun, a hovering orb like the one in screenshot 2,
circles the Spindle at Y 360 on a 40 minute cycle.
Its position sets four phases: Dawn-Glass, Noon-Bloom, Dusk-Prism and the Hush (the
orb dims to an ember, stars and aurora appear). Mobs and several puzzles read the
phase.
The skybox is a pastel gradient with slow cloud banks and a faint ring on the horizon,
the inner wall of the pocket. Fog is tinted per biome.

### Physics pack

Sable lets a data pack set per-dimension physics in
`data/skyseam/dimension_physics/halcyon.json`. Proposed start: gravity -9 (Overworld
is -11), base pressure 1.2 with the curve staying flat to Y 320, universal drag 0.07. The air
PropertyValue (starting point, tune in playtests)
Radius | 1,500 blocks around the Spindle at the center, so
the world is 3,000 by 3,000 blocks
Heights | Mirror Sea at Y 96, islands from Y 140 to 320, sky lid
at Y 420
The VeilA soft boundary at the radius with a fade. Over the
outer 200 blocks the fog thickens, colors wash out
to pearl white and sound muffles. Ships are
deflected like light off glass and players feel a
prismatic push. No invisible wall and no death:
anyone who crosses the radius is carried gently
back, and anyone who jumps or falls off the edge
drops into the Mirror Sea and triggers Rebound
The floorThe Mirror Sea never kills: falling into it triggers
Rebound (see Core systems)
Arrival | Arrival Lane on the south-west rim at Y 240, with
the Sundered Obelisk visible ahead

<!-- page 22 of 78 -->
feels thicker and floatier, so ships behave a little differently here and players notice
without being told. Confirm the folder name and the fields against the pinned Sable
version.

### Biomes


### Terrain generation

Floating islands come from 3D density noise with three size classes (small 20 to 40
blocks, medium 60 to 120, large 150 to 300). Biomes form rough concentric rings with
noise at the borders.
Biome | Look | Terrain | Purpose
Petalwash
Meadows
Purple petal carpets,
glass-stem flowers, still
pools (screenshot 2)
Wide, gentle
floating plateaus
Safest home of the helper
creatures
Mirror Shoals | Shallow reflective water,
pale rock outcrops
Low reefs over
the Mirror Sea
The Standing Arch and
the Prism Regent
Cirrus Reefs | Cloud-coral islands, soft
cloud blocks, drifting
petals along wind lanes
Airy, stacked
reefs
Whale roosts and the
Gale Ring
Underbloom | Inverted gardens:
hanging roots, lantern
vines, glow moss
The undersides
of islands
Hidden entrances,
Cogwren nests. You must
fly under islands
The Hush | Dim blue-violet, stars
and aurora, muted sound
Deep hollows
and tall spires
Hollow Listeners, the
Belfry and the Hollow
Cantor
The Spindle | A column of rising light
ringed by orbiting
islands
Concentric ring
islands
The Conservatory Spire
(screenshot 4)
Wreckfields | Graveyards of Wright
ships snagged on
drifting reefs
Scattered wreck
islands
Salvage, logbook pages,
brass

<!-- page 23 of 78 -->
Key structures sit at fixed positions (Obelisk, Spire, three arenas) so quests and maps
can name them. Everything between is generated. The four exit seams pick from nine
candidate sites using the world seed.
Use the simplest implementation that gives a bounded size, fixed landmark positions
and the three island size classes. At 3,000 by 3,000 blocks, generate lazily per chunk
and keep island density in config so the larger area stays playable. Data-driven noise
settings with density functions, or a custom `ChunkGenerator`, are both fine.
Dimension type: tall enough for the sky lid (for example `min_y` 0 and `height` 512). The
sky is drawn by a custom `DimensionSpecialEffects` (NeoForge's
`RegisterDimensionSpecialEffectsEvent`; confirm the name in the pinned version).

### Flora and sound

Glassreed chimes when brushed, and pitch follows wind speed. Lumen Lily glows and
bounces players. Cloudmoss grows on cloud blocks. Prismite crystal clusters are
mined for Prism Dust.
Music is `[SUPPORT ONLY]`. The intended sound is sparse piano and glass harmonica,
one layer per Tide. Claude Code cannot make music, so it builds the music slots
described in section 21 and leaves them empty. The Hush stays nearly silent on
purpose even when music exists, because sound is a gameplay channel there.
Ambience is `[FIRST PASS]`: one synthesized loop per Tide and one for the Hush
(section 21).

## 8. Core systems

Build tags. `[CC]` all nine systems. `[FIRST PASS]` the visual and audio side: beam and ring
rendering, Tide weather visuals, Almanac art and every sound.
Nine reusable rules carry the whole dimension, so every creature, puzzle and boss is built
from the same few ideas and players learn the Halcyon once.

<!-- page 24 of 78 -->
System | The rule | Used by
Tides | Weather events on top of the day
phase. Calm is default. Every 25
minutes or so one Tide runs for 6 to 10
minutes: Prismfall (glass-shard rain,
Prism Dust to gather, Shardlings
spawn), Gale (crosswinds, tailwinds
on lanes, Gale Harriers), Bloom
(flowers open, helpers produce
double, Pollen Bloats drift in),
Stillness (rare: wind stops, sound
deepens). A Tide Vane block and the
Almanac show the next one
Creatures, bosses, gathering
Wind Lanes | Visible ribbons of petals and light
motes between landmarks. A ship
inside a lane is nudged along it (a
force if Sable allows, else a velocity
nudge each tick). Direction flips with
Tides
Travel, the Skyray fight, Create
windmill puzzle
Refraction | A light beam leaves the Lantern-Sun,
hits Prism Pylons you rotate in 15
degree steps, splits into red, green
and blue at a Splitter and recombines.
Max 12 bounces, 64 blocks per leg
Prism Regent, Spire dome
Echo | Sound is a channel. Pings, sprinting,
explosions and bell strikes create
noise events with a radius. Listeners
and the Hollow Cantor react. A ping
reveals hidden things as outlines for
20 seconds
Echo Sounder, Hush, Belfry

<!-- page 25 of 78 -->
Fealty's API says data packs can add static factions, so the Wrightling faction needs no
code change in Fealty.
System | The rule | Used by
Rebound | Falling into the Mirror Sea bounces
you 24 blocks up with 8 seconds of
Slow Falling, then Weakness for 10
seconds, at most once per 30
seconds. A ship hitting the sea is
sprung back up without damage. No
void deaths, and none at the border
either
The floor of the world
Moorings | A Mooring Post docks a ship (it holds
still within 6 blocks), stores a chest-
sized cargo hold, and sets your
respawn inside the Halcyon until you
first leave through an exit
Anchorage, wrecks, camps
Almanac | A codex item with five tabs: Chart
(discoveries on a map), Bestiary (fills
by observing), Glyphs, Tides, Favors.
Every entry is also an advancement
FTB Quests, hints
Salvage and
Glyphs
Twelve logbook pages are hidden in
the Wreckfields. Each teaches one
glyph pair. The Spire's last puzzle is a
six-glyph phrase. A Wren House near
a wreck lets Cogwrens deliver one
page per Tide cycle
Spire, helpers
Wrightling rep | Wrightlings are tiny artisans at the
Anchorage and Moorings with their
own Fealty faction `skyseam:wrights`.
Gifts of Moonsilk, brass and flowers
raise rep. Tiers unlock Lost and Found
(replaces a lost key item), Tide
forecast, repair-kit trades and hints
Services, Fealty tie-in

<!-- page 26 of 78 -->

## 9. Creatures

Build tags. `[CC]` entity classes, AI goals, spawn rules, loot, products, rep effects and
dialogue hooks. `[FIRST PASS]` every model, animation, texture and sound. The rig list is in
section 20, and creature sounds start as vanilla stand-ins (section 21).
The Halcyon has four helper species whose products are required for progress, a few
ambient animals, and six hostile types that arrive mostly through Tides and the Hush. No
creature wears or drops armor.

### Helpers that matter

Creature | Look | Behavior | What it contributes
Lanternmoth | Palm-sized glowing
moth, four soft
wings
Gathers around lit
lanterns, dims and rests
in the Hush
Shear its cocoon for
Moonsilk (3 per cocoon,
regrows each Tide).
Moonsilk becomes the
Sounder's membrane
and Resonance Cells.
Also a living light source
Wayfarer
Whale
14-block sky whale,
slow and gentle
Follows wind lanes and
sings. At each Tide
change it drifts toward
the nearest exit seam
you have not found
Right-click a singing
whale with a Hollow
Shell to record a Whale
Song Shell (10 minute
cooldown per whale), a
Sounder part. Its drift is
a soft hint when you are
lost. Harming it costs
Wrightling rep
Cogwren | Chicken-sized bird
with brass feathers
Nests in wrecks. A Wren
House block nearby
makes it fetch one
Logbook Page per Tide
cycle until all 12 are
found
Unlocks the glyphs for
the Spire without a fixed
route. Also picks up
dropped items near a
Mooring

<!-- page 27 of 78 -->

### Ambient and neutral


### Hostile

Creature | Look | Behavior | What it contributes
Bloomstag | Slender deer with
crystal antlers
Grazes petal carpet,
sheds antlers during
Bloom
Antler Glass (2 per shed,
never harmed), the rod
of the Pneumatic
Coupler
Wrightling | Knee-high artisan
in a brass apron
Lives at the Anchorage
and Moorings, speaks
through the dialogue
system
Trades, Lost and Found,
hints, Tide forecast (see
Wrightling rep)
Creature | Notes
Glasskipper | School of ray-like fish skimming the Mirror Sea.
They scatter when a Hollow Listener is near, a free
warning for watchful players
Updraft Gull | Flock that follows ships. Feed Glass Berries and
they circle wind lanes, making them easy to see
Brasshorn Ram | Grazes ledges and headbutts anyone who walks up
from the front. Knockback only
Creature | Where and when | Behavior | Drop
Shardling | Prismfall, Mirror
Shoals
Knee-high glass imps in
swarms of 6 to 10. Throw
shards, shatter when hit
hard
Prism Dust
Hollow
Listener
The Hush | Tall, blind, bell-eared.
Hunts by noise only:
sprinting, pings,
explosions. Sneaking past
is safe
Muted Bell (consumable,
silences you for 30 s)

<!-- page 28 of 78 -->
Overworld only: the Stormling, a small static-charged wisp that appears in thunderstorms
near a Skyspike during the Stormglass Night favor.

## 10. Bosses

Build tags. `[CC]` the three fights: phases, arena logic, multipart hit boxes, drops, per-
player relics and the Mooring Post at each entrance. `[FIRST PASS]` models, animations,
textures and fight sounds. `[SUPPORT ONLY]` the boss themes: three music slots, each
with an optional per-phase stem hook (section 21). Boarding the Skyray is the hardest
part, and its fallback is in section 19.
Three Stay Guardians, one per Stay, each teaching a core system (light, wind, sound). The
Tempest Skyray is the fight that yields the Pneumatic Coupler parts. Recommended order
is Regent, Skyray, Cantor, but any order works, and the Spire needs all three relics. Tune
each fight to about 7 minutes with iron-tier gear and expect packs to retune it.
Creature | Where and when | Behavior | Drop
Thread
Spinner
Underbloom,
Wreckfields
Spider-like, strings golden
thread tripwires between
islands. Threads slow
ships and players. Shears
cut them
Golden Thread
Gale HarrierGale Tide, Cirrus
Reefs
Raptors that dive at ship
decks and knock
passengers about. Reward
deck design
Gale Plume
Stray
Reflection
Mirror Shoals | A humanoid of mirror that
walks on the sea and
copies your movements a
second late. Hits you if you
stand still
Mirror Shard
Pollen Bloat | Bloom Tide | A drifting jelly that bursts
into Levitation for 6
seconds. A hazard near
ships, a tool near ledges
none

<!-- page 29 of 78 -->

### 1. The Prism Regent (Mirror Stay)

Arena: the Hall of Reflections, entered through the Standing Arch from screenshot 2,
whose mirror door previews the hall. A disc of shallow water over a mirror floor, ringed
by six Prism Pylons, with the Lantern-Sun overhead.
Look: a tall slender crystal figure with a glass crown and eight orbiting shard ribbons.
Phase 1, Reflections: it splits into four copies. Only the real one casts a reflection in
the floor. Decoys pop when hit.
Phase 2, Refraction: players rotate the Pylons to route the sun beam onto the Regent's
core while it casts Overexposure. A hit stuns it for 8 seconds at triple damage.
Phase 3, Shatter: the floor breaks into nine hovering platforms and Stray Reflections
join. Falling into the sea only costs time because of Rebound.
Drops: Prism Core (Spire relic), 3 to 5 Mirror Shards.

### 2. The Tempest Skyray (Wind Stay), Coupler boss

Arena: the Gale Ring, eight small islands around a central cyclone column, open sky on
every side.
Look: a 48-block manta made of cloud-stone plates, with six glowing air sacs along its
flanks.
Phase 1, Circling: it glides in loops and throws charged cloud cells that arc to metal on
ships. Players ground the arcs with lightning rods or pop the cells, so deck design
matters.
Phase 2, Cyclone: the central storm grows. Crosswinds push ships and the Skyray's
wake pulls them. Ride the wind lanes or moor to the ring islands.
Phase 3, Low pass: it skims a Perch island for 15 seconds so players can jump onto its
back and pop all six air sacs while they are inflated, exposing the heart.
Drops: Pneumatic Heart (Coupler core), 2 to 4 Gale Plumes, Gale Core (Spire relic).
Highlights: boarding a flying colossus, and thunder rods bolted to your own deck.

### 3. The Hollow Cantor (Echo Stay)

Arena: the Belfry, a vast hollow island in the Hush with a ring of giant Bell Pillars and
almost no light.
Look: a hooded choir-master of dark glass with twelve small bells orbiting it. It is
invisible until pinged.

<!-- page 30 of 78 -->
Phase 1, Call and response: it plays a four-note phrase on the pillars. Replaying it
correctly stuns and reveals it for 10 seconds.
Phase 2, Choir: it calls Hollow Listeners. Noise draws them, so fight quietly, sneak-
attack, or use Muted Bells.
Phase 3, Crescendo: phrases grow to six notes and the arena rings with false echoes,
so listen for pitch, not just position.
Drops: Echo Core (Spire relic), Resonant Tine (Sounder part).
Gear for the fight: a Tuning Fork (short-range ping) given by the Wrightlings at the
Belfry door.

### Shared rules

Each arena has a Mooring Post at its entrance, a respawn point, and a safe return route,
so a loss never traps the player.
Kills are tracked per player. Co-op fights share the drop roll but every participant gets
their own relic.
Each fight has a music slot with an optional per-phase stem hook (section 21). With no
music file the fight is silent apart from its sound effects.

## 11. Items, blocks, recipes

Build tags. `[CC]` every item, block, recipe, loot entry and tag. `[FIRST PASS]` all item and
block art (section 20). The Pneumatic Coupler needs no art under Option A.
The mod adds about thirty items, a set of working blocks and a building set, and no armor.
Every craft uses something a creature or boss gives, so the Halcyon's life matters to the
player.

### Key tools and blocks

Name | Kind | Obtained | What it does
Harmonic
Aperture
Block | Crafted from the five favor
components, the Keystone
and two Create parts
Opens the Seam when
mounted on a flying ship. Has
a gauge GUI
Skychart | Item | Stargazer, after the fifth favorMarks the Seam site, shows
the entry gauge

<!-- page 31 of 78 -->

### Pneumatic Coupler

```text
Mirror Shard         | Gale Plume      | Mirror Shard
Antler Glass         | Pneumatic Heart | Antler Glass
Precision Mechanism  | Brass Casing    | Precision Mechanism
```
The Heart and Plume come from the Tempest Skyray, Antler Glass from Bloomstags, and
the shards from the Regent or Stray Reflections.
Name | Kind | Obtained | What it does
Almanac | Item | Stargazer, at the closing talk | Five-tab codex (Chart,
Bestiary, Glyphs, Tides,
Favors)
Pneumatic
Coupler
Item | Crafted in the Halcyon | An exact copy of Create
Aeronautics' creative physics
staff
Echo Sounder | Item | Crafted in the Halcyon | Sonar pulse and beam that
reveals hidden exit seams
Tuning Fork | Item | Wrightlings at the Belfry doorShort-range ping for the
Cantor fight
Prism Spyglass | Item | Spyglass plus Mirror Shard | Shows wind lane direction and
names creatures at range
Mooring Post | Block | 2 brass ingots, 3 sticks, 1
Moonsilk
Docks ships, stores cargo,
sets respawn
Prism Pylon and
Splitter
BlocksMirror Shard plus brass | The light-routing pieces
Tide Vane | Block | Brass plus Prism Dust | Shows the next Tide
Wren House | Block | 4 planks, 1 brass nugget, 1
Golden Thread
Lets Cogwrens deliver
logbook pages
Halcyon
Lantern
Item | Lumen Lily, brass, glass | Portable light that draws
Lanternmoths

<!-- page 32 of 78 -->
Making it an exact copy. The Create Aeronautics source, read when this was written,
shows that the staff is `simulated:creative_physics_staff`, class `PhysicsStaffItem`,
epic rarity, stack size 1, range 128 blocks. Its handlers only check `instanceof`
`PhysicsStaffItem`, and no creative-mode check was found, so it works in survival once
held. Confirm the id, the class and the range against the pinned version before relying on
any of it. Two ways to ship the copy:
A. Recipe only (recommended, locked in section 23). The recipe's result is the original
item with its name changed to "Pneumatic Coupler". No item code, identical behavior
and look. In 1.21.1 a recipe result can carry data components. Use
`minecraft:item_name` so the name shows as plain text, or `minecraft:custom_name`
with italics off if the staff's tooltip depends on its default name. Add a GameTest that
crafts it and asserts that the result is the Simulated staff item with the new name.
B. Subclass (not chosen). `PneumaticCouplerItem extends PhysicsStaffItem` under
`skyseam:pneumatic_coupler`. Still recognized by every handler, but needs its own
model, because Aeronautics' art assets are all rights reserved and cannot be copied.

### Echo Sounder and Resonance Cell

```text
Mirror Shard       | Moonsilk            | Mirror Shard
Brass Ingot        | Resonant Tine       | Brass Ingot
Whale Song Shell   | Precision Mechanism | Moonsilk
```
Resonance Cell (shapeless): 1 Moonsilk, 1 Prism Dust, 1 copper ingot. One cell powers a
Pulse, three power a Beam.

### Materials

Moonsilk, Prism Dust, Mirror Shard, Gale Plume, Antler Glass, Golden Thread, Hollow Shell,
Whale Song Shell, Pneumatic Heart, Resonant Tine, Muted Bell, Logbook Page, and Glass
Berries (food: restores hunger and gives 5 seconds of Glowing).

### Building set

Stillstone stone, bricks, slabs, stairs, walls, pillars and glass for the Wright architecture,
plus Petal Carpet, Cloud blocks, Glassreed and Lumen Lily for dressing islands.

<!-- page 33 of 78 -->

### Armor rule

No armor, no trinket slots, no Curios dependency. Survivability comes from systems
(Rebound, Mooring respawns, Muted Bells), not gear.

## 12. Leaving the Halcyon

Build tags. `[CC]` the Stitches, the exit charge, the Echo Sounder logic, noise events, the
radar data and the soft-lock nets. `[FIRST PASS]` the look of the ping ring and beam, the
radar art and every sound.
Four hidden exit seams lead home, and the Echo Sounder is the only reliable way to find
them, so the player is stuck until they have built it.

### The exits (Stitches)

Four Stitches, chosen at world creation from nine candidate sites in different biomes
and heights. At least one sits on an island summit reachable on foot, one in the
Underbloom and one deep in the Hush.
Dormant means invisible and intangible. The only tell is a faint shimmer and thread
hum within 12 blocks, which attentive players can learn to notice.
A ping from the Sounder draws a dotted outline for 20 seconds and logs the site on the
Almanac Chart.
Opening one: a ship with a Harmonic Aperture within 24 blocks runs the same charge
as the entry, with the reveal played in reverse (the threads unpick). A player on foot
opens a walkable Stitch by carrying the Aperture item within 6 blocks.
Every Stitch returns you to the Seam site in the Overworld, at your departure altitude
or 30 blocks above ground, whichever is higher.

### The Echo Sounder

ModeHow it works | Range | Cost
PulseHold 1 second. A ring of pale-blue light expands
from you, sweeping terrain and open air
96 blocks | 1 Resonance Cell
Beam | Hold 3 seconds while aiming. A narrow cone with
ripples along the line
400
blocks
3 Resonance
Cells

<!-- page 34 of 78 -->
Look and sound: the ring clips through islands, things it hits glow and chime, and pitch
drops with distance. A low thrum plays on release and the echoes return a moment
later.
Screen: held in hand it shows a round radar plan view with a compass ring, blips that
stay unlabeled until identified, and the last ten pings.
Reveals: dormant seams, wreck islands, large creatures as shapes, Prismite deposits
and Mooring Posts. Boss arenas are always visible.
The risk: a ping is a noise event. Hollow Listeners within 128 blocks come to the spot. In
the Hush the Cantor's choir stirs. Prismfall and Gale cut range by 30 percent. Stillness
doubles range and noise.

### The gate: building the Sounder

1. Collect Moonsilk from Lanternmoths.
2. Record a Whale Song Shell from a singing Wayfarer Whale.
3. Gather Mirror Shards from Stray Reflections or the Prism Regent.
4. Defeat the Hollow Cantor for the Resonant Tine, using the Tuning Fork, which also
teaches the risk of pinging.
5. Craft it, then refill with Resonance Cells.
That forces at least one boss, two helper species and a trip through three biomes before
leaving.

### Soft-lock safety nets

Dying in the Halcyon respawns you at a Mooring Post, never in the Overworld, until you
first leave through a Stitch.
Dropped items stay at the death spot. Wrightling Lost and Found replaces lost key
items for a fee once Wrightling rep is high enough.
A lost ship: once per Tide cycle a Wrightling will assemble a basic Wright Skiff at the
Anchorage for a fee.
Whales drift toward unfound exits at each Tide change, a free hint for the stuck.
A config switch turns all four nets off for hardcore packs, and an admin command
ejects a stuck player.

<!-- page 35 of 78 -->

## 13. Puzzles and GUIs

Build tags. `[CC]` the logic of all twelve puzzles, the per-world shuffling, menus, payloads
and server-side checks. `[FIRST PASS]` the art and layout of every screen, the puzzle
block models and the puzzle sounds. The author checks layout and readability in game.
Twelve puzzles, no two sharing a mechanic, spread across the Overworld chain, the three
arenas and the Spire. Answers that depend on the world (angles, orders, glyph phrases)
are shuffled per world, so a video guide cannot hand out one solution.

### Puzzle catalog

\# | Puzzle | Where | Mechanic | Reward
1 | Star Dial | Hollow Cairn
(Overworld)
Three-ring dial GUI.
Rotate rings to match
constellation clues from
the Survey Notes
Star-Glass Lens
2 | Bell
Sequence
Silent Chapel
(Overworld)
Place three clappers,
then strike the bells in
the order carved on the
wall
Tuned Clapper
3 | Ring Run | Sky above the
village
Fly through 12 Wind
Rings in order within 4
minutes
Windwheel Gear
4 | Skyspike | Overworld
thunderstorm
Build a lightning tower,
keep the vial on it, and
repel Stormlings
Stormglass Vial
5 | Prism
Routing
Hall of Reflections,
Spire dome
Rotate Pylons so a
colored beam reaches
matching receivers
Regent stun, dome
opens
6 | Flower
Clock
Petalwash vault | Flowers open at certain
Lantern-Sun phases.
Arrange colors to match
three phase markers,
then wait
Vault cache and
Glass Berry seeds

<!-- page 36 of 78 -->

### Screens

Skyseam ships a small UI kit styled to match Fealty's screens (tab bar, scroll list, buttons)
so the two mods feel like one set. If the author supplies Fealty's GUI textures in
`reference/fealty-gui/`, match them. Otherwise use a neutral pastel-glass style and say
so in `docs/DECISIONS.md`.
\# | Puzzle | Where | Mechanic | Reward
7 | Thread
Loom
Spire gardens floorGUI grid. Route golden
threads between node
pairs without crossing
Lift unlocks
8 | Echo Map | The Hush caves | Pulse pings in the dark
and follow rising pitch to
three hidden bells,
struck low to high
Belfry door opens
9 | Gravity
Switch
Wreck interiors | Float and Sink plates
toggle levitation fields so
you walk walls and
ceilings
Logbook Page
10 | Wind Lane
Turbine
Spire arcade | Build a Create windmill
or propeller engine in a
wind lane that meets the
lift's gauge
Powers the Spire
lift
11 | Mirror Maze | Mirror Shoals ruin | Walls are mirrors that lie.
The water floor shows
the true layout
Mirror Shard
cache
12 | Glyph
Cipher
Spire summit | Three relic sockets and a
six-glyph wheel.
Translate the phrase
using logbook pages
Stillpoint Heart
Screen | Purpose
Dialogue | Reuse Fealty's dialogue box: portrait, subtitle, up to
six replies with icons, and grayed replies that
explain what they need

<!-- page 37 of 78 -->
```text
+--------------------------------------+
|  HARMONIC APERTURE        owner: you
|
|         ( charge ring  62% )
|   [x] Flying  [x] Altitude  [ ] Site
|
|  Site: 1204 / -388    Distance: 212
|  Ground at site: Y 71   Need: Y 101+
|  [ Skychart slot ]
+--------------------------------------+
          N
       .-----.
     .'   *   '.      * hidden seam (dotted)
    |  .  @   . |     @ you
     '.   o   .'      o unidentified echo
       '-----'
  Cells 2/3   Mode: Beam   Range 400
```
Screen | Purpose
Aperture | Status ticks, charge ring, Skychart slot (mockup
below)
Seam Gauge HUD | Compact overlay near the hotbar while an
Aperture is mounted
Almanac | Chart, Bestiary, Glyphs, Tides, Favors tabs
Star Dial | Three concentric rotating rings over a star field
Thread Loom | Grid of nodes with drag-to-connect thread
Glyph Cipher | Three relic sockets and a rotating glyph wheel
Echo Sounder | Radar plan view (mockup below)
Wrightling servicesTrade, Lost and Found, Tide forecast, hints

<!-- page 38 of 78 -->

## 14. Structures

Build tags. `[FIRST PASS]` all fifteen structures: Claude Code writes generators that
produce the `.nbt` files from the reference images and the palette rule. `[CC]` the world-
gen registration, placement rules, loot tables, structure sets, data markers and the
GameTests that place every file. The author may later overwrite any file with a hand-built
version of the same name (`[MANUAL]`, optional, section 22).
Fifteen structures, three of them tied directly to the reference images. Palette rule: pale
limestone and warm brown brick, moss green, teal and gold accents, violet crystal veins,
soft white light. Nothing black, nothing harsh.

### Halcyon

Structure | Where | Reference | Holds
Sundered
Obelisk
Anchorage,
arrival island
Screenshot 3: a floating
monolith split in two
with a small orb in the
gap, mossy, with a cliff
gateway at the base
Mooring Post, Wrightling
stalls, the carved motto,
the first Almanac pages
Conservatory
Spire
The Spindle | Screenshot 4: overgrown
domed tower, teal and
gold dome, balconies,
hanging gardens. About
120 blocks tall
Four floors: flooded
arcade (Wind Lane
Turbine), gardens (Thread
Loom), observatory dome
(Prism Routing), summit
(Glyph Cipher)
Standing Arch | Mirror Shoals | Screenshot 2: a 40-block
arched mirror gate
standing in water beside
pale rocks, a glowing orb
nearby
Entrance to the Regent's
hall
Hall of
Reflections
Beyond the
Arch
Disc of shallow water
over a mirror floor
Regent arena, six Pylon
plinths
Gale Ring | Cirrus Reefs | Eight islands around a
cyclone column
Skyray arena, a Mooring
Post and a lightning mast
on each island

<!-- page 39 of 78 -->

### Overworld


### How the structures get built

Generated by script. Generators in `tools/structures/` (Python with an NBT writer,
or a small Java tool that uses Minecraft's own NBT classes) write
`data/skyseam/structure/<name>.nbt`. Generators are deterministic for a given seed
and size.
Structure | Where | Reference | Holds
The Belfry | The Hush | A hollow island with a
ring of Bell Pillars
Cantor arena, Echo Map
puzzle
Wright Wrecks | Wreckfields | Three sizes: 30, 60 and
120 blocks
Logbook pages, brass, the
Gravity Switch rooms
Moth Grove | Petalwash
Meadows
Lantern trees around a
pool
Lanternmoth cocoons
Whale Roost | Cirrus Reefs | A hollow cloud arch with
bone-white rings
Where whales rest and
sing
Wright MooringScattered | A small dock with one
Wrightling
Respawn and storage
Stitch rings | Nine candidate
sites
A ring of frayed-stitch
stones, easy to miss
The hidden exits
Structure | Where | Holds
Hollow Cairn | Mountain biomes | Standing stones, a toppled brass
telescope, the Star Dial pedestal
Silent Chapel | Plains and forests | Ruined chapel with three empty bell
frames and wall carvings
Brass
Telescope
On a rooftop in the
Stargazer's village
The marker that tells players they found
him
Wind Ring
course
Sky above the village | Entities, no blocks, so nothing to
generate

<!-- page 40 of 78 -->
Hero builds are parametric. The Conservatory Spire is a tall tower with a teal and gold
dome, balconies and hanging gardens. The Sundered Obelisk is a split monolith with a
gap and an orb socket above a cliff gate. The Standing Arch is a 40-block arch standing
in shallow water. For each one, open the matching picture in `reference/`, match its
silhouette, palette and proportions, and view an isometric preview
(`tools/structures/preview.py`) before moving on.
Repeatable pieces are the natural fit for generators: floating islands in three size
classes, ring courses, hollow cave islands, wreck hulls and Stitch rings.
Props and creatures are not structures. They are modeled (section 20).
Data markers. Mark every functional spot (puzzle blocks, chests, spawn points, each
Stitch center) with a data marker, list the markers in `docs/STRUCTURE-MARKERS.md`, and
let code swap each marker for the real block or block entity after placement. A hand-
built replacement then only needs the same markers.
Placement. Overworld structures use normal structure generation (a structure set
with rare spacing and biome tags). Halcyon landmarks sit at fixed positions chosen by
the dimension generator (section 7).
Check. A GameTest places every `.nbt` file in a flat test world, asserts the bounding
box and that every marker listed for it exists, and flags blocks outside the palette list.
Swap rule. The author can hand-build any structure in creative, save it with a structure
block under the same name and overwrite the file. That is the optional polish step in
section 22. No code change should be needed.

## 15. Fealty integration

Build tags. `[OTHER REPO]` the eight Fealty features listed below, written out as a prompt
in section 16. `[CC]` everything on the Skyseam side: the Fealty compat package, the
rumour and conversation data files, the five favor objectives and the Stargazer entity.
`[FIRST PASS]` all dialogue wording and the models of the Stargazer and the Wrightling.
Fealty already has most of what this needs: a rumour chain, a quest-giver system, server-
driven dialogue and a quest-type registry. The plan is to build on those and add eight
small, general features to Fealty, not a Skyseam-specific hack. The Fealty source
(NeoForge 1.21.1, API 1.2.0) was read from the author's zip.

<!-- page 41 of 78 -->

### What Fealty already gives us

One naming clash: Fealty already has a Keeper (the hidden hamlet NPC). This design calls
ours the Stargazer to avoid confusing the two.

### What Fealty needs added

1. Single-giver chains. Today each step is set by a different villager. Add a chain mode
where one named NPC gives every step in order.
2. A stable API for quest types, chain kinds and quest givers. The quest-type registry
currently lives in the main jar. Promote it to the API jar.
Fealty piece | Where it lives | How Skyseam uses it
Rep tiers and
API
`FealtyApi`, `RepApi`, data-driven tiers | Gate the rumour roll
(Trusted or higher), read rep
for dialogue conditions
Rare villager
chain
`QuestChainDefinition`, `ChainManager`,
`data/fealty/fealty/quest_chains/`
The model for the Stargazer
chain: rumours, steps, a map,
a trial, a final reward
Dialogue | `DialogueService`, `DialogueNode`,
`VillagerDialogue`, client
`DialogueScreen`
The conversation UI for
villagers, the Stargazer and
Wrightlings
Quest
objectives
`QuestObjective`, `QuestType`, registry
`fealty:quest_type`
Skyseam registers its five
favor objectives here
Quest givers | `QuestGiver`, `QuestGivers`,
`KeeperEntity`
The Stargazer is a new giver
Letters | `SealedLetterItem`, mailboxes | The Starlit Letter and the
three accords in Three
Hands
Factions | Data-driven factions | `skyseam:wrights` for
Wrightling rep
FTB Quests
and KubeJS
`compat/ftbquests`, `compat/kubejs` | Fealty rep and quest events
already reach the author's
questbook

<!-- page 42 of 78 -->
3. A rumour hook. An event and data format for rumours with conditions and weights,
plus a rare roll on ordinary villager conversations (today only an elder's "rumours"
action starts the chain).
4. Branching dialogue from data. Nodes, replies with conditions (rep tier, item, flag,
advancement, dimension, time) and effects (take or give item, set flag, change rep,
start quest, grant advancement).
5. Per-player flags. A small `getFlag` and `setFlag` API so a conversation can remember a
choice.
6. Dialogue contributors. Let another mod add a reply or line to an existing villager's
node, for example "Ask about the sky".
7. Special-role villagers. A public way to mark one villager as a named role with a title
(Fealty already stores roles on villagers).
8. Events. `RumourEvent`, `DialogueEvent` and `ChainStageEvent`, so FTB Quests and
KubeJS can react.
Section 16 is a ready prompt for the Fealty project. It is not part of this build.

### What Claude Code builds on the Skyseam side

1. Fealty is required. Declare it as a required dependency in `neoforge.mods.toml` and
compile against its API version 1.3.0. Do not write a fallback dialogue runner.
2. Order of work. The Fealty project finishes the section 16 prompt first. Until then
Claude Code builds everything that does not touch dialogue, rumours or Fealty quest
types. Dialogue-dependent features (the Stargazer, Wrightling talk, the rumour roll, the
favor objectives) are built in M10.
3. One package for Fealty. All Fealty calls live in one package (`compat/fealty`). The
Fealty session may name things slightly differently from the prompt, so read the
finished `docs/API.md` in the Fealty project first and adapt inside that package only.
4. Data where Fealty reads it. Put every conversation, rumour and chain file at
`data/skyseam/fealty/conversations/`, `data/skyseam/fealty/rumours/` and
`data/skyseam/fealty/quest_chains/`. The formats are in this section and in section
16.
5. What it registers. The five favor objectives as Fealty quest types, the static faction file
for `skyseam:wrights`, the Stargazer role on a villager in a found village (a Brass
Telescope is placed on a rooftop there), and listeners that forward Fealty events to
advancements.

<!-- page 43 of 78 -->

### Dialogue data format

```text
{
  "id": "skyseam:stargazer/first_meeting",
  "speaker": "skyseam:stargazer",
  "start": "greet",
  "nodes": {
    "greet": {
      "text": "skyseam.dialogue.stargazer.greet",
      "replies": [
        { "label": "skyseam.reply.show_letter", "if": { "has_item":
"skyseam:starlit_letter" }, "goto": "letter" },
        { "label": "skyseam.reply.ask_sky", "goto": "sky" },
        { "label": "skyseam.reply.bye", "end": true }
      ]
    },
    "letter": {
      "text": "skyseam.dialogue.stargazer.letter",
      "effects": [ { "take_item": "skyseam:starlit_letter" }, { "set_flag":
"skyseam:met_stargazer" } ],
      "replies": [ { "label": "skyseam.reply.ready", "goto": "favors" } ]
    }
  }
}
```

### Example: first meeting with the Stargazer

Wystan: "Mind the stairs. The telescope has right of way."
Reply A (needs the Starlit Letter): "A villager sent me. I have this." Reply B: "What are
you looking at?" Reply C: "Goodbye."
A, then Wystan: "Mara's hand, and Mara's bad spelling. Then she told you. Good. Sit,
and listen, because the sky does not stay open long." He keeps the letter and sets the
`met_stargazer` flag.
B, then Wystan: "A stitch that has come loose. Come back with a letter and I will show
you where." (Reply A stays hidden until the letter exists.)
Further replies unlock the five favors one at a time, each with a short briefing and a
clearly grayed reply that explains what is missing when the player is not ready.
Fealty is a required dependency (decision 4 in section 23).

<!-- page 44 of 78 -->

## 16. Prompt for the Fealty AI

Build tags. `[OTHER REPO]` Claude Code does not run this prompt. The author pastes it
into the session that has the Fealty project open. Claude Code only needs to know what it
asks for, so that the Skyseam compat package (M10) can use the result. The text inside
the box was edited so that Fealty stays standalone and names no other mod.
The author pastes this whole block into the session that has the Fealty project open. It
names the real files found in the Fealty zip, asks for eight general features and nothing
Skyseam-specific, and ends with tests it must pass. A copy was also sent to the author as
a file.
```text
TASK
Extend the Fealty mod with general extension points so data packs and other
mods can add story chains, rumours and branching conversations. Fealty must
keep working completely on its own: it must not depend on, require or mention
any other mod. Every feature below must be general, data-driven where
possible, and backwards compatible.
CONTEXT
- Project: Fealty (NeoForge 1.21.1, mod id fealty, package
com.selluastar.fealty, API 1.2.0, see docs/API.md).
- Read before coding: chain/QuestChainDefinition.java,
chain/ChainManager.java, quest/QuestObjective.java, quest/QuestGiver.java,
quest/QuestGivers.java, quest/ElderGiver.java, registry/ModQuestTypes.java,
registry/FealtyRegistries.java, registry/ModAttachments.java,
dialogue/DialogueService.java, dialogue/DialogueNode.java,
dialogue/VillagerDialogue.java, dialogue/GiverDialogue.java,
data/FealtyDataManager.java, data/CodecDataLoader.java,
village/VillageResolver.java, village/VillageTracker.java,
src/api/java/com/selluastar/fealty/api/*, docs/API.md,
gametest/FealtyGameTests.java, and
data/fealty/fealty/quest_chains/rare_villager.json.
- Today: the rare_villager chain starts when a Trusted player asks an elder
about rumours. Each step is given by a different villager (CHAIN_ROLE
attachment), then comes a map to a hidden hamlet, a Keeper trial and a final
reward.
BUILD THESE EIGHT FEATURES
1. Single-giver chains. Add a field "giver" to QuestChainDefinition
("per_step" by default, or "single"). With "single", one villager holds one
role (field "giver_role", optional "giver_title" component) and offers every
step in order through ChainManager, using the existing step and quest fields.
```

<!-- page 45 of 78 -->
```text
A step may carry an optional "unlock" condition (see feature 4).
rare_villager.json must behave exactly as before.
2. Stable extension API. Move or mirror into the API jar (package
com.selluastar.fealty.api.quest) the types other mods need: QuestType,
QuestObjective, QuestContext, QuestGiver and the quest-type registry key. Add
a new registry "fealty:chain_kind" with a ChainHandler interface (start,
onStepComplete, onChainComplete) so other mods can define chain kinds beyond
RARE_VILLAGER and GUILD. Document it in docs/API.md.
3. Rumour hook. New data folder data/<ns>/fealty/rumours/<id>.json:
   { "chain": "ns:chain_id", "stage": 1, "min_tier": "fealty:trusted",
"weight": 5, "professions": [], "lines": ["ns.rumour.key"], "requires": {
condition }, "grants": [ effect ] }
   Add a RumourService. It rolls once per villager per in-game day on each
conversation (hook into the node build in VillagerDialogue). Config:
rumour_chance (default 0.03), pity_step (0.01), pity_cap (0.12),
guarantee_after (30 eligible conversations). A successful roll picks a
weighted eligible rumour, adds its line to the villager's dialogue, applies
"grants" and records progress per player. The elder's existing "rumours"
action should draw from the same pool. Fire a RumourEvent (cancelable, lines
editable).
4. Branching conversations from data.
data/<ns>/fealty/conversations/<id>.json (format below), compiled into the
existing DialogueNode and Option records and routed through
DialogueService.choose. Add two registries, "fealty:dialogue_condition" and
"fealty:dialogue_effect", with built-ins. Conditions: has_item,
tier_at_least, flag, advancement, dimension, quest_stage, time_of_day, and
all/any/not. Effects: take_item, give_item, set_flag, clear_flag, add_rep,
start_quest, grant_advancement, run_function. A reply whose condition fails
is shown disabled with its "hint" text. DialogueScreen must support up to 6
replies.
5. Player flags. Add a PlayerFlags attachment (ModAttachments) and RepApi
methods getFlag, setFlag and clearFlag (ResourceLocation keys). Persist
across death and relog. Add /fealty flag <player> get|set|clear for
debugging.
6. Dialogue contributors. Add a DialogueBuildEvent fired in
VillagerDialogue.node and GiverDialogue.node with getPlayer, getNpc,
addOption and addLine. Options whose id starts with "<modid>:" are routed
back to the listener that added them when the player picks them.
7. Special-role villagers and villages. API methods: assignRole(villager,
chainId, role, title) built on the existing CHAIN_ROLE data,
findVillager(server, village, predicate), and findVillage(server, origin,
minDistance, maxDistance, predicate) built on VillageResolver and
VillageTracker. The assigned villager shows its title in its name display.
```

<!-- page 46 of 78 -->
```text
8. Events in api.event: RumourEvent, DialogueEvent (Open, Choose) and
ChainStageEvent (Start, Advance, Complete), as plain NeoForge events that
anything can subscribe to. Do not add or change any integration with another
mod.
CONVERSATION FORMAT (feature 4)
{
  "id": "ns:npc/first_meeting",
  "speaker": "ns:role",
  "start": "greet",
  "nodes": {
    "greet": {
      "text": "ns.dialogue.greet",
      "replies": [
        { "label": "ns.reply.show_letter", "if": { "has_item": "ns:letter" },
"hint": "ns.hint.need_letter", "goto": "letter" },
        { "label": "ns.reply.bye", "end": true }
      ]
    },
    "letter": {
      "text": "ns.dialogue.letter",
      "effects": [ { "take_item": "ns:letter" }, { "set_flag": "ns:met" } ],
      "replies": [ { "label": "ns.reply.ready", "goto": "greet" } ]
    }
  }
}
RULES
- Backwards compatible: existing data, saves and the rare_villager chain
behave exactly as before. Bump api_version to 1.3.0 (gradle.properties and
FealtyApi.API_VERSION).
- Match the code style: records with codecs, DeferredRegister registries,
NeoForge events, no dependency of any kind on another mod. Leave the existing
compat modules exactly as they are.
- Fealty must start and play fully with no other mod installed. Nothing you
add may name another mod, including in code, data, lang keys, docs, examples
and tests. Use the neutral namespace "example" in all examples and tests.
- The server decides and the client displays. Anything the client needs goes
through a payload like OpenDialoguePayload.
- Add lang keys to assets/fealty/lang/en_us.json for any text Fealty itself
shows.
DELIVERABLES AND ACCEPTANCE
```

<!-- page 47 of 78 -->
```text
- Update docs/API.md with every new type, registry, event and data format.
- Add an example data pack in docs/examples/single_giver_chain/ with a
rumour, a conversation and a single-giver chain.
- Add GameTests in FealtyGameTests: rumour roll and pity, a single-giver
chain end to end (rumour, letter, three steps, reward), a contributor reply,
and flags surviving relog.
- Run the build and the tests, then list what changed and anything you chose
not to do.
- If something is ambiguous, pick the simplest option that keeps the API
general, and note it.
```

## 17. Advancements and FTB Quests

Build tags. `[CC]` every advancement, custom stat, tag and `skyseam:` id below, each fired
by a custom or vanilla trigger from the system it belongs to, with a GameTest that fires it.
`[FIRST PASS]` advancement titles, descriptions and icons (icons reuse item sprites).
`[MANUAL]` the FTB Quests chapters, quests and rewards: the author builds them from
these ids, and no starter chapter is generated (decision 9 in section 23).
Every milestone is an advancement under the `skyseam:` namespace, plus custom stats
and tags, so the author's questbook needs no custom code. The FTB Quests source was
checked: its task types are item, custom, xp, dimension, stat, kill, location, checkmark,
advancement, observation, biome, structure, gamestage, fluid and interaction. The table
names the best fit for each milestone. Fealty already ships its own FTB Quests rep task,
which can sit beside these.

### Advancements

Ids are under `skyseam:`. A star means a group of several, such as one per biome.
Advancement | Fires when | Best FTB task
`rumour/whisper` | A villager mentions the
scar
Advancement
`rumour/lead` | Survey Map received | Item
`rumour/introduction` | Starlit Letter received | Item
`stargazer/met` | First talk with Wystan | Advancement

<!-- page 48 of 78 -->
Advancement | Fires when | Best FTB task
`favor/survey`, `favor/three_seals`,
`favor/silent_chapel`,
`favor/ring_run`, `favor/stormglass`
Each favor finished | Advancement
`favor/all_five` | All five favors done | Advancement
`cairn/found`, `chapel/found` | Enter the structure | Structure
`storm/stormlings` | 10 Stormlings killed | Kill
`aperture/crafted` | Harmonic Aperture craftedItem
`aperture/mounted` | Aperture placed on a ship | Advancement
`seam/opened` | First Seam opened | Stat
`seams_opened`
`seam/crossed` | First crossing | Dimension
`halcyon/anchorage` | Reach the Sundered
Obelisk
Structure
`halcyon/biome_*` (7) | Enter each biome | Biome
`halcyon/tide_*` (4) | Live through each Tide | Advancement
`halcyon/rebound` | Survive a Rebound | Advancement
`halcyon/underbelly` | Reach an Underbloom
hollow from below
Advancement
`halcyon/wreck_*` (3) | Salvage each wreck size | Structure
`halcyon/logbook_6`, `logbook_12` | Pages found | Stat
`logbook_pages`
`helpers/moonsilk`, `whale_song`,
`antler_glass`
First of each helper
product
Item
`helpers/cogwren_page` | First page from a Cogwren | Advancement
`helpers/wrightling_friend` | Friendly tier with
Wrightlings
Fealty rep task
`boss/regent`, `boss/skyray`,
`boss/cantor`
Each Guardian defeated | Kill

<!-- page 49 of 78 -->

### Custom stats

`seams_opened`, `crossings`, `exits_used`, `moonsilk_harvested`, `whale_songs`,
`logbook_pages`, `sounder_pings`, `listeners_alerted`, `guardians_defeated`.
Advancement | Fires when | Best FTB task
`boss/skyray_boarded` | Stand on the Skyray's back | Advancement
`boss/skyray_airborne` | Beat it without touching
ground
Advancement
`boss/cantor_silent` | Beat it without a Listener
noticing you
Advancement
`boss/all_three` | All three Guardians | Advancement
`coupler/crafted` | Pneumatic Coupler craftedItem
`coupler/first_lift` | Move a ship with it | Advancement
`sounder/crafted` | Echo Sounder crafted | Item
`sounder/first_ping` | First Pulse | Stat
`sounder_pings`
`sounder/first_echo` | Reveal a Stitch | Advancement
`exit/found_all` | All four Stitches logged | Advancement
`exit/first_use` | First exit taken | Advancement
`exit/round_trip` | Three crossings each way | Stat `crossings`
`spire/floor_*` (4) | Each Spire floor solved | Advancement
`spire/stillpoint` | Glyph Cipher solved | Advancement
`challenge/pacifist` | Cross the Halcyon killing
no hostile
Advancement
`challenge/hush_runner` | Cross the Hush without
alerting a Listener
Advancement

<!-- page 50 of 78 -->

### Tags

Item tags `#skyseam:favor_components`, `#skyseam:stay_relics`,
`#skyseam:halcyon_materials`. Entity tags `#skyseam:guardians`, `#skyseam:hostile`,
`#skyseam:helpers`. Structure tag `#skyseam:halcyon_landmarks`. FTB Quests item and
kill tasks accept tags, so one quest can cover a whole group.

### Suggested chapters

These are suggestions for the author's questbook (`[MANUAL]`). Claude Code does not
create chapter files.
1. Whispers: rumour, rep with villages, meeting the Stargazer.
2. Five Favors: one quest per favor, then the Keystone.
3. Unpicking the Sky: craft, mount and open the Seam.
4. Arrival: biomes, Tides, helpers, Rebound.
5. Stay Guardians: three bosses in any order, plus the challenge variants.
6. Craftsmanship: the Coupler and the Sounder.
7. Listen: find the four Stitches and go home.
8. The Spire: four floors and the Cipher.
9. Challenges: optional, hidden until the main line is done.

## 18. Idea pool

Build tags. Backlog. None of this is required and none of it is part of the first build. Build
an item only when the author names it. An item built later is `[CC]` for its logic and `[FIRST`
`PASS]` for its art and sound, except where it is tagged inline.
More than 80 extra ideas, none required, grouped so the author can pull a few per update.
Each is built from the core systems above.

### Creatures

Prism Lamprey: an eel that swims along wind lanes and dive-bombs ships at dusk.
Drops Prism Dust.
Cloud Tortoise: a huge, slow neutral animal with a garden on its back. Moor to it and
Glass Berries make it carry you along a lane.

<!-- page 51 of 78 -->
Gilded Kite: an ally bird called with a whistle that scouts 200 blocks and marks
creatures for 10 seconds (5 minute cooldown).
Hush Moth: eats noise. Carried in a Quiet Jar it mutes footsteps in a 6-block radius.
Tide Heron: wades the Mirror Sea, and its posture predicts the next Tide.
Echo Finch: repeats the last bell note it heard, a hint source in the Belfry.
Dewfox: a shy fox that leads you to Moth Groves if you leave Glass Berries.
Wrightling Elder: a rare, seasonal quest-giver with a longer story.
Brass Beetle: rolls Prism Dust into balls you can collect.
Stray Child: a Stray Reflection variant that only copies, never attacks, used in a puzzle.

### Items and tools

Petal Kite: a one-handed glider that rides wind lanes.
Quiet Jar, Lantern Whistle, Tide Compass: a noise muffler, a moth caller, a pointer
toward the next Tide.
Prism Lens: a Sounder upgrade with more range for more cells.
Cell Charger: a block that recharges Resonance Cells from Create rotation speed.
Skyfarer's Journal: records your flight path as a line on the Almanac Chart.
Glass Berry Jam: a food that briefly boosts ping range.
Rope Ladder Kit: quick deck access for large ships.

### Blocks and building

Cloud Glass in colors, Wright Brass trim set, Petal Thatch roofs, Lumen Lamps.
Prism Windows that throw colored light patches on the floor.
Chime Chains that play with the wind, Glassreed Thatch.
Gravity Plates as functional decor for wreck-style rooms.

### Puzzles

Tide Clock: an orrery GUI where you set the Lantern-Sun position to open a vault.
Whisper Walls: hidden doors that only answer to pings.
Coupler Docks: drag a floating platform into a socket with the Pneumatic Coupler. A
puzzle designed around the reward.
Moth Maze: lead a Lanternmoth swarm with a lantern to a switch.
Shadow Sundial: time the Lantern-Sun so a shadow lands on marks.

<!-- page 52 of 78 -->
Windbell Chimes: tune blade pitches so wind plays a melody that opens a door.
Weighted Scales: balance pans using item weights.
Petal Lock: plant flowers in a coded order.
Glyph Mirror: glyphs read reversed in a mirror, so the cipher needs a second look.

### Events and Tides

Aurora Night: ribbons act as extra wind lanes and whales sing in chorus (double Whale
Song).
Lantern Eclipse: the orb dims for 3 minutes and Listeners walk in daylight. High risk
and reward.
Glass Meteors: Prismite showers leave temporary mining nodes.
Great Migration: every whale crosses in one pod, and riding with them reveals all
Stitches.
Hollow Tide: all sound muted, pings are the only navigation. Hard mode.
Wreck Surge: buried wrecks rise into reach for 10 minutes.
Pollen Storm: low visibility, triple helper yields.
Seam Tremor: a mini-seam flickers open with a loot cache beyond it.

### Boss variants

Tide-touched fights: one Tide's rules active during the fight.
Cantor's Encore: longer phrases and visual-only clues.
Spire boss rush with shared health, unlocked after the Stillpoint.
Co-op scaling: +50 percent health and extra pylons per extra player.
Time-trial stat and a no-damage advancement per boss.
Phase-reactive layered boss music `[SUPPORT ONLY]`. The hook is built in section 21,
and the tracks are the author's.

### Structures and landmarks

The Orrery: a working model of the pocket inside the Spire that doubles as the Tide
Clock.
Choir Cathedral: a ruined Hush hall with giant organ pipes that play when pinged.
Unfinished Flagship: a half-built Wright ship at the Anchorage. Gather parts across the
Halcyon to finish it and unlock a sky parade.

<!-- page 53 of 78 -->
Lighthouse of Seams: a tower whose sweeping beam highlights the nearest unfound
Stitch.
Pearl Gardens: glass biospheres of tropical plants in the Underbloom.
Whale Graveyard: sacred bones and lore, with a sing site.
Floating Market: a Wrightling barge that drifts between Moorings once a day.
Quiet Library: a room of Wrightling books and a librarian.

### Create and Aeronautics tie-ins

Opal envelope paint crafted from Prism Dust, and Antler Glass propeller skins
(cosmetic).
Wrightling cargo contracts: deliver Create-made goods by ship to a Mooring for rep
and rare materials.
Wind lane turbines that power Create machines in a dimension with no rivers.
Wright Skiff and other ships as Create schematics, found in logbook pages and
printable with the Schematicannon.
Sky salvage: tow wreck chunks home with ropes.
Spire lift and Cell Charger both run on Create rotation.

### Fealty and villagers

30 or more rumour lines, written per profession.
After the chain, a Wright stall opens in the Stargazer's village and permanently sells
Halcyon goods.
Villages you helped hang lanterns on their roofs.
The Stargazer's apprentice remembers your closing answer in later dialogue.
Wrightling rep tiers appear in Fealty's own standings screen.
Fealty's mailbox delivers letters from Wrightlings after you return.

### Multiplayer and teams

Progress scope switch: per player or per FTB team.
Crew charge: each extra seated player shortens the Seam charge by 0.5 seconds, down
to 2.
Shared Mooring respawn for a team, PvP off in the Halcyon by default.
Independent boss loot per player.

<!-- page 54 of 78 -->

### Accessibility and quality of life

Colorblind-safe Prism Routing with shapes as well as colors.
Visual ring and text subtitles for echo pings.
Reduce-motion toggle for Seam shake and flash.
Server option to skip a puzzle already solved once.
Almanac hints in three levels: nudge, hint, answer.

### Modpack extras

KubeJS events for Seam open, cross and exit, Tide change and boss kill.
Config presets: Casual, Standard, Hardcore.
Data-driven Tide table, spawn tables and wreck loot, so pack makers add their own
items.
A generated FTB Quests chapter file for the quests folder, built from the advancement
table. `[CC]`, but only if the author asks, because decision 9 says no starter chapter.

### Polish

Footstep chimes on Glassreed, per-biome ambient loops, a whale chorus.
Seam colors shift per Tide (a renderer tint, not a custom shader).
Photo mode hotkey: hides the HUD and freezes the Lantern-Sun.
Arrival cinematic toggle: a slow pan as the Seam opens.
Particle quality slider and per-phase lighting.

## 19. Technical notes and build order

Build tags. `[CC]` all of it. This section is the plan Claude Code follows, and where it differs
from the Design Bible this version wins.
Everything here is buildable, but the ship crossing between dimensions is the one piece
nobody has proven yet, so it is milestone zero.

### Stack

Minecraft 1.21.1 on NeoForge with Java 21, matching Fealty (NeoForge 21.1.209).
Sable's add-ons list NeoForge 21.1.228 or newer, so confirm the highest requirement in
the pack.

<!-- page 55 of 78 -->
Required dependencies: Create, Create Aeronautics (which brings Simulated, the
mod that owns the physics staff), Sable (which lists Create and Veil as its own
requirements), Fealty (API 1.3.0, built by the Fealty session in section 16) and GeckoLib
4.
Where to get them: Sable publishes its API at
`https://maven.ryanhcode.dev/releases` as `dev.ryanhcode.sable:sable-common-`
`1.21.1`. GeckoLib is `software.bernie.geckolib:geckolib-`
`neoforge-${minecraft_version}:${geckolib_version}` from
`https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/`. Use the 4.x line for
1.21.1 and confirm the exact version there, because a 5.x alpha also exists. For anything
without a Maven of its own, use Modrinth's Maven (`maven.modrinth`) or CurseMaven,
and take the coordinates from the project's own page rather than guessing. Fealty
comes from the author's local build of the Fealty project.
Soft dependencies: KubeJS and FTB Quests (no code link, advancements only).
Animated mobs: GeckoLib 4 for NeoForge 1.21.1, with models and animations authored
in Blockbench.
Licenses: Sable is under the Polyform Shield license. The Simulated project's code is
MIT but its art assets are all rights reserved. Link to public APIs only and never copy
their models or textures. The author checks both before redistributing a modpack.

### What is easy and what is risky

Feature | Effort | Risk | Note
Seam visuals (entity, renderer,
particles)
Medium | Low | Stays shader-pack safe by
avoiding a true portal render
Entry trigger (Aperture, ship
detection, ground check)
Medium | Medium | Needs Sable calls to find the
sub-level that holds the block
and its pilot
Ship crossing between
dimensions
High | High | Spike first. Routes A and B in
section 6
Bounded floating-island
dimension
High | Medium | Custom chunk generator, Veil
deflection
Halcyon physics data pack | Low | Low | One JSON file

<!-- page 56 of 78 -->

### Build order

Each milestone ends with something the author can launch and film, and with
`docs/STATUS.md` updated. A milestone is done when `./gradlew build`, `runData` and
`runGameTestServer` pass and a headless `runServer` boots. Every milestone also includes
the first-pass art and sounds for its own features, in the order given in section 20.
Feature | Effort | Risk | Note
Three animated bosses | High | Medium | Boarding the Skyray is the
hardest part. Fallback: weak
points hit from ships or the air
Echo Sounder visuals and noise
events
Medium | Low | Ring and beam rendering plus a
server noise bus
Six custom screens | Medium | Low | Match Fealty's look
Eight Fealty features (`[OTHER`
`REPO]`)
Medium | Low | Separate task, prompt in
section 16
Advancements, stats, tags | Low | Low | Data generation
Pneumatic Coupler | Low | Low | Recipe only
Models, textures, structures,
sounds (`[FIRST PASS]`)
High | Medium | Buildable by script and MCP, but
only the author can judge the
result
ID | Milestone | What is built | Done when
M-1Environment
and skeleton
`docs/ENVIRONMENT.md`, the
project with every dependency,
the four run configs,
`SableBridge` and
`ExternalIds`, the Blockbench
MCP servers registered, the
`docs/` files created
The build passes and an
empty mod boots on a
headless server
M0 | Spike | Move a test Sable ship between
two dimensions with Route A,
then Route B. If neither works,
adopt the fallback in section 23
The result and chosen route
are in `docs/DECISIONS.md`

<!-- page 57 of 78 -->
ID | Milestone | What is built | Done when
M1 | The Seam | Project content skeleton, the
`/skyseam` debug commands,
the Seam entity, renderer and
particles, the full reveal,
synthesized reveal sounds.
Look and sound are `[FIRST`
`PASS]`
The command opens and
mends a Seam, and the author
can film it
M2 | Entry | Harmonic Aperture block and
block entity, trigger rules, gauge
HUD, Skychart, ship transfer
A GameTest per trigger rule,
and a test ship crosses
M3 | The shell | Dimension, sky, physics pack,
Arrival Lane, Veil, Mirror Sea
Rebound, biomes, the structure
generator tools and the
Sundered Obelisk
A ship arrives and the Obelisk
is in view
M4 | Systems | Tides, wind lanes, Moorings,
Almanac skeleton, the noise
bus, the music manager with
every slot empty (`[SUPPORT`
`ONLY]`), the audio synthesis tool
Tides cycle, ships dock, the
game runs silent of music
without errors
M5 | Helpers | The four helper species and the
Wrightling as placeholder-then-
first-pass creatures, materials,
the Pneumatic Coupler recipe
The Coupler can be crafted
early from creative-spawned
parts
M6 | Regent | Prism Regent, Refraction, Hall
of Reflections, Standing Arch
The fight is winnable and
drops its relic
M7 | Skyray | Tempest Skyray, Gale Ring,
boarding, Pneumatic Heart
The fight is winnable and
completes the Coupler
M8 | Cantor and exitsHollow Cantor, Echo, the Echo
Sounder, Stitches, soft-lock
nets
The first full loop: enter, fight,
leave
M9 | The Spire | Four floors, puzzles 5 to 12 as
needed, the Glyph Cipher
The Stillpoint Heart can be
earned

<!-- page 58 of 78 -->
The Fealty session (section 16) should start on day one in parallel. It only blocks M10.

### Risks and answers

API drift: wrap every Sable and Aeronautics call in one `SableBridge` class and pin
versions, so an update is a one-file fix.
Intrusive mixins: test against the author's real pack early, not at the end.
Big-ship hitch on crossing: cap size and let the pearl-white flash hide the load.
Multiplayer sync: the server decides everything and the client only plays visuals from
packets.
Server stops with a Seam open: store Seam state in world data and mend on load.
Blockbench MCP is experimental: if it fails, generate the geo and animation JSON by
script and record that in `docs/ENVIRONMENT.md`.
Testing: Claude Code can write code, data, models, textures, structures and
synthesized sounds, and can run the build, data generation and headless GameTests. It
cannot launch Minecraft and look at it or listen to it, so each milestone ends with the
author running it and sending logs or screenshots. `docs/STATUS.md` says what to look
at.
ID | Milestone | What is built | Done when
M10The chain | Needs Fealty 1.3.0. Rumour
data, the Stargazer, the five
favors and their puzzles, the
closing talk, Wrightling dialogue
and services
Part 1 plays start to finish
M11Questbook | Advancements, stats and tags
(ids only, no chapter file,
decision 9)
A GameTest fires every
advancement
M12Polish and
handoff
Remaining sounds, structure
and texture review pass,
accessibility options, config
presets, `TODO-MANUAL.md`
finalized from section 22
The author has a complete list
of what is left for them

<!-- page 59 of 78 -->

## 20. Asset manifest

Build tags. `[FIRST PASS]` for every model, texture, animation, sprite and particle below,
and for the sounds as marked in the sound table. `[CC]` for spawn eggs, `.mcmeta` files,
`sounds.json`, lang entries and the renderer-only pieces that have no art. `[SUPPORT`
`ONLY]` for music. The author's final polish pass on the hero pieces is `[MANUAL]` and is
listed in section 22.
Everything that has to be modeled, drawn, animated, built or recorded, with who makes it.
Rig and animation names are working names under the `skyseam` namespace. Sizes
marked "suggested" are the designer's guesses; everything else comes from the sections
above.
At a glance: 18 creature rigs (3 bosses, 15 mobs), 3 small entity models, 22 block entries,
33 items (32 need new art), 9 screens, 14 particles and about 150 sounds. Structures are
generated by script (section 14) and are not Blockbench work.

### Where assets live (the swap rule)

Claude Code writes this table into `docs/ASSET-PATHS.md`. Replacing any file with a file of
the same name must work with no code change.
Kind | Folder under `src/main/resources/` | Naming
Entity
geometry
assets/skyseam/geo/` | `<name>.geo.json
Animations | `assets/skyseam/animations/` | `<name>.animation.json`
Entity
textures
`assets/skyseam/textures/entity/` | `<name>.png` and
`<name>_glowmask.png`
Block, item
and GUI
textures
`assets/skyseam/textures/block/`,
`item/`, `gui/`
Moving ones add
`<name>.png.mcmeta`
Particles | `assets/skyseam/particles/` and
`textures/particle/`
`<id>.json`
Sounds | `assets/skyseam/sounds/<group>/` | `<name>.ogg`, declared in
`assets/skyseam/sounds.json`
Music | `assets/skyseam/sounds/music/` | `<slot>.ogg` (section 21)

<!-- page 60 of 78 -->
Source models stay in `art/models/<name>.bbmodel` outside the resources folder, and
generator scripts stay in `tools/`.

### Production rules

Mobs and bosses are built with the Blockbench MCP (section 2) in GeckoLib mode,
kept as `.bbmodel` sources, and exported to `.geo.json` plus `.animation.json`. If no
MCP works, generate the same files by script. Suggested texture sizes: 64 by 64 for
small mobs, 128 by 128 for mid-size mobs, the Regent and the Cantor, 256 by 256 for the
Whale and the Skyray. Box UV is the simplest layout for generated textures.
Glow comes from a separate `_glowmask` texture per model (GeckoLib's
`AutoGlowingGeoLayer` convention), so lamps, crowns and bells shine in the dark. Make
it by script: copy the base texture, then clear every pixel that should not glow.
Shared animation names on every mob that has the pose: `idle`, `walk`, `fly`, `attack`,
`hurt`, `death`. The tables below list only the extra ones.
Blocks and items use 16 by 16 textures in the pastel-glass palette of screenshot 2,
generated by scripts in `tools/textures/` that share one palette file so the whole set
stays coherent. Moving textures (Mirror Sea, Prismite, Lumen Lily) use `.mcmeta` frame
files.
Animated blocks (Aperture, Star Dial, Glyph Cipher) are block entities with GeckoLib
renderers. The Seam has a custom renderer and no model.
Sounds are mono `.ogg` files listed in `sounds.json` with subtitles, because only mono
sounds are positional.
Placeholders first. Every entity gets a single-box model and a flat color before any art,
so gameplay and quests can be built and tested while the art catches up.
Look at the result. Open every generated texture and every model render as an image
before reporting it.

### Boss rigs

All three are `[FIRST PASS]`.
Kind | Folder under `src/main/resources/` | Naming
Structures | `data/skyseam/structure/` | `<name>.nbt`
Lang | `assets/skyseam/lang/` | `en_us.json`

<!-- page 61 of 78 -->
Rig | Size | Main bones | Animations | Textures and
notes
Prism
Regent
about 4
blocks
(suggested)
`root`,
`torso`,
`head`, `crown`
(five spikes),
`arm_l` and
`arm_r`
(upper and
lower), `core`,
`ribbon_1` to
`ribbon_8`
(three links
each)
`float`, `split`,
`decoy_pop`,
`overexposure_charge`,
`overexposure_release`,
`stunned`,
`shatter_phase`, `death`
`regent` plus
glowmask, and a
translucent
`regent_decoy`
skin on the same
rig. The floor
reflection is a
renderer effect
drawn for the real
Regent only
Tempest
Skyray
48 blocks
across
`root`,
`plate_1` to
`plate_8`,
`wing_l1` to
`wing_l4`,
`wing_r1` to
`wing_r4`,
`head`,
`tail_1` to
`tail_6`,
`sac_1` to
`sac_6`,
`heart`,
`emitter_l`,
`emitter_r`
`glide`, `bank_l`, `bank_r`,
`throw_cell`,
`cyclone_surge`,
`low_pass`, `sac_inflate`,
`sac_pop` (once per sac
bone), `heart_exposed`,
`stagger`,
`death_breakup`
`skyray` plus
glowmask. Needs
multipart hit
boxes (head, six
sacs, heart, tail)
like the Ender
Dragon, and solid
back plates so
players can stand
on it during the
low pass

<!-- page 62 of 78 -->

### Mob rigs

All fifteen are `[FIRST PASS]`.
Rig | Size | Main bones | Animations | Textures and
notes
Hollow
Cantor
about 3.5
blocks
(suggested)
`root`,
`robe_1` to
`robe_3`,
`torso`, `head`
with hood,
`arm_l` and
`arm_r`,
`baton`,
`bell_ring`
pivot with
`bell_1` to
`bell_12`
`sway`, `shimmer` (the
near-invisible state),
`ping_reveal`,
`conduct_4`, `conduct_6`,
`stunned`, `summon_choir`,
`crescendo`, `death_toll`
`cantor` plus
glowmask for the
bells, and a
`cantor_outline`
skin shown only
while pinged
Mob | Main bones | Extra animations | Notes
Lanternmoth
(palm-sized)
`body`, `head`,
`antenna_l` and
`antenna_r`,
`wing_fore_l` and
`_r`, `wing_hind_l`
and `_r`, `lamp`
`hover`, `flap`, `rest` | Glowmask on
`lamp`, dimmer in
the Hush. The
cocoon is a block,
listed below
Wayfarer Whale
(14 blocks)
`head`, `jaw`, `body_1`
to `body_3`, `tail_1`,
`tail_2`, `fluke`,
`fin_l` and `fin_r`,
`ring_1` to `ring_3`,
`throat`
`glide`, `sing`, `turn`,
`roost`
256 texture.
Glowmask pulses
during `sing`. Hit
box smaller than
the model
Cogwren
(chicken-sized)
`body`, `head`, `beak`,
`wing_l` and `wing_r`,
`tail`, `leg_l` and
`leg_r`, `gear`
`peck`, `flap_glide`,
`carry`, `nest`
`gear` spins faster
while a Logbook
Page is carried

<!-- page 63 of 78 -->
Mob | Main bones | Extra animations | Notes
Bloomstag | `body`, `neck`, `head`,
`antler_l` and
`antler_r` (three
tines each), four legs,
`tail`
`graze`, `shed`, `regrow`,
`bloom_glow`
Antler bones scale
to zero during
`shed`. Glowmask
on the antlers
Wrightling
(knee-high)
`head` with goggles,
`body`, `apron`, `arm_l`
and `arm_r`, `leg_l`
and `leg_r`, `pack`,
`tool`
`work`, `trade_nod`,
`wave`, `sit`
Apron color
variants for stall,
dock and camp
crews
Glasskipper | `body`, `tail`, `fin_l`
and `fin_r`, `jaw`
`skim`, `scatter` | Translucent
texture. Drawn in
schools, so keep
the cube count low
Updraft Gull | `body`, `head`, `beak`,
`wing_l1`, `wing_l2`,
`wing_r1`, `wing_r2`,
`tail`, `foot_l` and
`foot_r`
`glide`, `flap`, `circle`,
`perch`
Circling is driven by
code around wind
lanes, so `circle`
only needs a
banked glide
Brasshorn Ram | `body`, `head`, `horn_l`
and `horn_r`, four
legs, `tail`
`graze`,
`headbutt_windup`,
`headbutt`
Brass horns get a
painted gloss, no
glowmask
Shardling | `body`, `head`,
`shard_crown`, `arm_l`
and `arm_r`,
`hand_shard`, `leg_l`
and `leg_r`
`hop`, `throw_shard`,
`shatter`
Death plays
`shatter` and
bursts Prism Dust
particles
Hollow Listener
(tall)
`hips`, `torso`, `leg_l`
and `leg_r` (long),
`arm_l` and `arm_r`,
`head`, `ear_l` and
`ear_r`
`stalk`, `sneak`,
`perk_ears`, `charge`,
`swipe`
No eyes.
`perk_ears` swivels
the bell ears toward
a noise. Glowmask
on the ear rims

<!-- page 64 of 78 -->
Mob | Main bones | Extra animations | Notes
Thread Spinner | `abdomen`, `thorax`,
`head`, `leg_l1` to
`leg_l4`, `leg_r1` to
`leg_r4`, `spinneret`
`climb`, `cast_thread`,
`bite`, `curl`
The golden thread
is a line the
renderer draws
between two
anchors, not a
model. Glowmask
on the abdomen
Gale Harrier | `body`, `head`, `beak`,
`wing_l1` to `wing_l3`,
`wing_r1` to `wing_r3`,
`tail`, `talon_l` and
`talon_r`
`glide`, `flap`, `dive`,
`strike`, `retreat`
Feather bones on
the wing tips flutter
in the wind
Stray
Reflection
`head`, `body`, `arm_l`
and `arm_r`, `leg_l`
and `leg_r`, `shard_1`
to `shard_6` floating
around it
`walk_water`, `strike`,
`shatter`
Translucent mirror
texture. The copy
of your movement a
second late is code,
so the model only
needs biped poses
Pollen Bloat | `bell`, `tentacle_1` to
`tentacle_6` (three
links each), `sac_1` to
`sac_3`, `core`
`drift`, `inflate`, `burst` | Glowmask on the
core. Pollen
particles on `burst`
Stormling
(small)
`core`, `arc_1` to
`arc_4`, `crown`
`float`, `charge`,
`arc_strike`, `grounded`,
`fizzle`
Glowmask. Arcs are
renderer lines
between the
Stormling and its
target. `grounded`
plays when a
lightning rod
catches the arc

<!-- page 65 of 78 -->

### Other entity models and renderers


### Blocks

All block art is `[FIRST PASS]`; the block entities and behavior are `[CC]`.
Piece | What it is | Notes
Wind Ring | An eight-segment ring, about 6
blocks across (suggested)
States `dormant`, `next`, `passed`,
`missed`. Pulses while it is the
next ring. The Ring Run uses 12.
`[FIRST PASS]`
Charged Cloud CellSkyray projectile | Small sphere with arcing lines
and a `spin` loop. `[FIRST PASS]`
Glass Shard | Shardling projectile | Tiny spinning shard in the Mirror
Shard material. `[FIRST PASS]`
The Seam | Custom renderer, no Blockbench
model
Additive line-strip edges,
translucent quads, the pre-
baked interior sky with parallax,
golden thread strips and scar
particles (see section 6). `[CC]`
code, `[FIRST PASS]` textures
Prism beams and
sonar rings
Renderer effects | Additive quads and ring meshes,
no model. `[CC]`
Block | Build | States and animation
Harmonic Aperture | GeckoLib block entity | `idle`, `spin_up`, `charged`, `cooldown`.
The core ring and three status gems
glow
Prism Pylon | Block entity renderer | Head turns in 15 degree steps. Beam
socket lights when hit
Prism Splitter | Static model plus beam
renderer
Crystal with three output arms, lit when
fed
Prism Receiver | Static | Red, green and blue lenses, lit and unlit

<!-- page 66 of 78 -->
Block | Build | States and animation
Tide Vane | Block entity renderer | One pose per Tide: Calm, Prismfall,
Gale, Bloom, Stillness
Mooring Post | Static plus rope | Docked and free rope states
Wren House | Static | Empty and occupied
Star Dial | GeckoLib block entity | Three rings turn independently, plus a
solved pose
Bell Frame | Static with a clapper slot | Empty, clapper placed, ringing (three in
the Silent Chapel)
Bell Pillar | Static, large | Ringing animation per pitch (the ring of
them in the Belfry)
Echo Bell | Static | Hidden outline and revealed (three in
the Hush caves)
Thread Loom | Static plus GUI | Idle and threaded
Glyph Cipher | GeckoLib block entity | Wheel turn, three relic sockets (empty
or filled), open pose
Float Plate and Sink
Plate
Static | Pressed and unpressed. Glow color tells
them apart
Mirror wall | Static, translucent | Reflective wall tile for the Mirror Maze
Spire Lift car | Moving platform | `idle`, `rise`, `locked`
Moth Cocoon | Static | Four growth stages, from empty to
ready to shear
Glass Berry bush | Crop-style | Three growth stages
Stillstone set | Static set | Stone, bricks, slabs, stairs, walls, pillars,
glass
Island flora | Cross and plant models | Petal Carpet, Cloudmoss, Glassreed
(two high), Lumen Lily, Prismite clusters
in three sizes, lantern vines, glow moss,
hanging roots
Cloud blocks | Static | Soft, slightly translucent, in a few
shades

<!-- page 67 of 78 -->

### Items

All icons are `[FIRST PASS]`.
Block | Build | States and animation
Mirror Sea | Animated surface | Reflective and nearly flat, scrolling
`.mcmeta` frames
Group | Items | Art
Tools (8) | Pneumatic Coupler, Echo Sounder,
Skychart, Almanac, Tuning Fork,
Prism Spyglass, Halcyon Lantern,
Resonance Cell
16 by 16 icons. The Echo Sounder
and the Halcyon Lantern also get
held 3D models, and the Sounder
gets `pulse` and `beam` use poses.
The Coupler needs no art under
Option A (the original model,
renamed)
Quest papers
(2)
Survey Notes, Flight Logbook | Icons
Materials (13)Moonsilk, Prism Dust, Mirror Shard,
Gale Plume, Antler Glass, Golden
Thread, Hollow Shell, Whale Song
Shell, Pneumatic Heart, Resonant
Tine, Muted Bell, Logbook Page,
Glass Berries
Icons. The Pneumatic Heart and the
Whale Song Shell get a faint idle
glint
Favor parts
(6)
Star-Glass Lens, Triple-Wax Seal,
Tuned Clapper, Windwheel Gear,
Stormglass Vial, Skywright's
Keystone
Icons. The Keystone is the hero
asset, with a slow glint animation
Relics (4) | Prism Core, Gale Core, Echo Core,
Stillpoint Heart
Icons with an idle glow. The three
cores are also drawn inside the
Glyph Cipher sockets
Spawn eggs
(18)
One per mob | Vanilla template with two tint
colors per mob, no new art. `[CC]`

<!-- page 68 of 78 -->

### Screens

All screen art is `[FIRST PASS]`. Glyphs are drawn once and reused as the carved wall tile,
the GUI sprite and the Almanac entry. Advancements need one tab background, and their
icons reuse item sprites.

### Sky, world and particles

Lantern-Sun: a disc with rays, plus the phase art the Flower Clock reads. Sky: day
colors per Tide as dimension-effects data, a star layer, aurora ribbons for the Hush, and
one cloud texture. Fog is data per biome. `[FIRST PASS]`
Particles (14): `seam_mote`, `seam_spark`, `thread_snap`, `scar`, `prism_dust`,
`shard_glint`, `petal`, `wind_mote`, `pollen`, `sonar_ring`, `ping_ripple`,
`lantern_glow`, `bell_wave`, `arc_spark`. `[CC]` types and providers, `[FIRST PASS]`
textures.
Screen | Textures needed
Dialogue | None new, since Fealty draws the box. Portraits for
the Stargazer and the Wrightling if the box does
not draw the entity
Aperture | Panel, status ticks, charge ring, Skychart slot
Seam Gauge HUD | Compact bar with three ticks and a distance
readout
Almanac | Page background, five tab icons, entry frame, map
overlay for the Chart tab
Star Dial | Star field background and three ring layers
Thread Loom | Grid, node sprites and thread sprites
Glyph Cipher | Wheel, three sockets and the glyph set
Echo Sounder radarPlan-view disc, sweep ring, blips and compass ticks
Wrightling servicesPanel plus four icons: Trade, Lost and Found, Tide
forecast, hints

<!-- page 69 of 78 -->

### Sounds

Every sound below exists in `sounds.json` from the milestone that needs it, with a subtitle
key, pointing at a stand-in until a better file replaces it. Section 21 explains the methods.
Group | Sounds | AboutMethod and tag
The Seam | Hairline tink, tearing crack,
thread snap in five pitches, open
hum loop, ring pulse, crossing
whoosh, closing chime
11 | Synthesized mono OGG. `[FIRST`
`PASS]`, and the first candidates
for the author's replacement
AmbienceOne loop each for Calm, Prismfall,
Gale, Bloom, Stillness and the
Hush
6 | Synthesized seamless loops
(filtered noise and slow tones).
`[FIRST PASS]`
Music | Five layers of sparse piano and
glass harmonica, one per Tide,
plus one theme per boss
8 | `[SUPPORT ONLY]`. Slots only, no
files
World | Glassreed chime in five pitches,
Lumen Lily bounce, petal rustle,
Prismite chime, Mirror Sea splash
and rebound
10 | Vanilla stand-ins with pitch
changes, chimes synthesized.
`[FIRST PASS]`
CreaturesAmbient, hurt and death for each
of the 15 mobs, plus specials:
Whale song in four variants,
Cogwren gear tick, antler shed
chime, Listener ear ring, Spinner
thread twang, Pollen burst,
Stormling arc
55 | Vanilla stand-ins per mob with
pitch and volume changes.
Specials synthesized. `[FIRST`
`PASS]`
Bosses | Regent (split chime,
overexposure whine, stun
shatter, phase break, death),
Skyray (cry, cell throw, cyclone
loop, three pitched sac pops,
heart exposed, death), Cantor
(six bell notes, phrase fail buzz,
summon, crescendo loop, death
toll)
23 | Synthesized bells and stingers,
vanilla stand-ins for cries.
`[FIRST PASS]`

<!-- page 70 of 78 -->

### Suggested art order

1. Placeholder boxes for everything, so quests and fights can be tested early.
2. The Seam: renderer textures, interior sky and the reveal sounds. It is the first moment
players see.
3. The three hero props: Aperture, Echo Sounder and Almanac.
4. The Anchorage set: Stillstone, Mooring Post, Wrightling, Lanternmoth and Whale.
5. The bosses in the order players meet them: Regent, Skyray, Cantor.
6. Hostile mobs, flora and the remaining icons, then the sound pass.

## 21. Audio: built now, added later

Build tags. `[FIRST PASS]` sound effects. `[SUPPORT ONLY]` music. `[MANUAL]` the final
sound design for the hero moments (section 22).

### The straight answers

Sound effects: yes, as a first pass. Claude Code registers every sound, writes every
subtitle and `sounds.json` entry, points them at vanilla stand-ins, and synthesizes the
hero sounds by script. It cannot hear any of it, so the author judges the result.
Group | Sounds | AboutMethod and tag
Tools | Echo Sounder (pulse, beam loop,
three return echoes, empty
click), Aperture (charge loop,
ready chime, mount click), Tuning
Fork, Muted Bell, Almanac page
turn, Skychart unfold, Mooring
dock and undock
15 | Synthesized plus vanilla stand-
ins. `[FIRST PASS]`
Puzzles | Star Dial click, Bell Sequence
strikes in four pitches, Ring Run
pass and miss, Pylon turn and
beam hum, Flower Clock bloom,
Loom connect and fail, Gravity
plates, Glyph Cipher socket and
open, Lift start and stop, Turbine
spin
19 | Vanilla stand-ins (bell, piston,
note-style tones) with pitch, plus
synthesized chimes. `[FIRST`
`PASS]`

<!-- page 71 of 78 -->
Music: no. Claude Code cannot compose music. It builds the slots and the music
manager so tracks can be added later with no code change, and the game runs silent of
music until then.
An audio-generation MCP or API: optional. Nothing here depends on one.

### How sound effects are covered

1. Registry and `sounds.json`. Every sound event is registered in code, has an entry in
`assets/skyseam/sounds.json` and a subtitle key in the lang file. All 147 sounds in
section 20 exist from the milestone that needs them.
2. Vanilla stand-ins. An entry can point at a vanilla sound event with `"type": "event"`
plus its own `volume` and `pitch`. That gives every creature, block and tool a sound on
day one. Replacing a stand-in later means changing that one entry to name a file. No
code changes.
3. Synthesized files. `tools/audio/synth.py` (Python with numpy) builds the hero
sounds from simple recipes: plucked strings for thread snaps, inharmonic bell tones for
chimes and bell notes, filtered noise sweeps for cracks and whooshes, layered sine
hums for loops. It writes mono Ogg Vorbis files (ffmpeg with libvorbis, or the soundfile
package) into `assets/skyseam/sounds/<group>/`. Loops are crossfaded so they join
cleanly.

### Checks Claude Code can run

A script (`tools/audio/check.py`) verifies, for every synthesized file: mono, a common
sample rate, a sane length, peak level below full scale, no DC offset, and for loops a small
jump at the join. It also writes a spectrogram PNG that Claude Code can view. A second
script verifies that every registered event has a `sounds.json` entry and a subtitle, and
that every file an entry names exists.
These checks catch broken files, not bad taste. Creature voices and the whale song are
the sounds a synthesizer will do worst, so expect them to be replaced. Everything labeled
`[FIRST PASS]` goes in `docs/STATUS.md` as "needs your ears".

### Music support `[SUPPORT ONLY]`

The intended sound is sparse piano and glass harmonica. The slots are:

<!-- page 72 of 78 -->
What Claude Code builds:
1. Registered events. One sound event per slot (for example
`skyseam:music.tide_calm`), registered in code so biome and dimension data can
name them.
2. No music files. None are shipped and none are faked. The manager checks whether a
slot has a sound definition and plays nothing if it does not. No crash, and no repeated
log spam (at most one info line per slot per session).
3. A client music manager. It chooses the slot from the context: an active boss fight first,
then the current Tide, otherwise nothing. It hooks NeoForge's music selection event
(`SelectMusicEvent`; confirm the name in the pinned version), crossfades between
slots over about 3 seconds, follows the player's music volume, and has a
`music.enabled` config switch. The choice itself is a pure function (context in, slot out)
with unit tests.
4. The Hush stays quiet. In the Hush the manager plays no Tide music, only the optional
`hush_stinger`, at most once every ten minutes.
5. Boss stems. The manager exposes the boss and its phase. If the phase stem exists it
fades in for that phase. If not, the main theme plays through.
6. Streaming. Every music entry uses `"stream": true`.
7. The how-to. `docs/ADDING-MUSIC.md` explains: put the Ogg Vorbis file (stereo is fine for
music) in `assets/skyseam/sounds/music/`, then either run `python`
`tools/sync_music.py`, which scans that folder and regenerates the music block of
Slot id | Used when | File name
`tide_calm`, `tide_prismfall`,
`tide_gale`, `tide_bloom`,
`tide_stillness`
The matching Tide is running (five
layers, one per Tide)
`<slot>.ogg`
`boss_regent`, `boss_skyray`,
`boss_cantor`
That fight is active | `<slot>.ogg`
`boss_<boss>_p1`, `_p2`, `_p3`
(optional)
Per-phase stems for a boss, if
present
`<slot>.ogg`
`anchorage`, `spire`, `seam_open`,
`hush_stinger` (optional)
The Anchorage, the Spire, the Seam
reveal, a rare quiet stinger in the
Hush
`<slot>.ogg`

<!-- page 73 of 78 -->
`sounds.json`, or paste the ready-made block from the document. A resource pack
with its own `sounds.json` also works, so pack makers can add music without touching
the jar. `TODO-MANUAL.md` lists each slot as `[SUPPORT ONLY]`.

### Optional audio generation

If the author later connects an audio-generation MCP or API, Claude Code may use it only
when asked, and only for stand-in sound effects. It never produces music unless the
author supplies the files. The author checks the license of anything generated, and
Claude Code notes the source of each file in `docs/AUDIO-SOURCES.md`.

## 22. Manual to-do list ("I'll add this later manually")

Build tags. `[MANUAL]` and `[SUPPORT ONLY]` items only. Claude Code seeds `TODO-`
`MANUAL.md` from this section and keeps it current. Every item has a working stub, so none
of them blocks a build or a test.

### Guaranteed: "I'll add this later manually" `[MANUAL]`

These will be in the final mod, and Claude Code cannot make them.
\# | Item | Why Claude
Code cannot
What Claude Code
leaves
Where the author's file goes
1 | FTB Quests
chapters, quests
and rewards
The author
designs the
progression
Every id from
section 17
The pack's FTB Quests folder, made
in the FTB editor
2 | Final sound
design for the
hero moments:
the Seam reveal
(11 sounds), the
Echo Sounder
(pulse, beam,
echoes), whale
song (4), the
Cantor's six bell
notes, boss cries,
the Aperture
charge
It cannot hear
or record
Synthesized files or
vanilla stand-ins
under the final
names
Overwrite the file in
`assets/skyseam/sounds/<group>/`.
If the entry names a vanilla event,
change the entry to name the file

<!-- page 74 of 78 -->
\# | Item | Why Claude
Code cannot
What Claude Code
leaves
Where the author's file goes
3 | Final art for the
hero pieces:
Harmonic
Aperture,
Skywright's
Keystone, the
Seam interior
sky, the
Almanac, the
Echo Sounder
held model, the
three boss
textures
It cannot
judge beauty
First-pass models
and textures with
sources in
`art/models/`
Edit the `.bbmodel`, export over the
same names, or overwrite the texture
4 | Polish on the
hero structures
(optional):
Conservatory
Spire, Sundered
Obelisk,
Standing Arch
It cannot
judge quality
Generated `.nbt`
files and
`docs/STRUCTURE-`
`MARKERS.md`
Build in creative, save with a
structure block, overwrite the `.nbt`
and keep the markers
5 | Playtest tuning:
difficulty, Tide
timers, spawn
rates, boss
health, journey
length
It cannot play
the game
This document's
defaults in config
and data
The config file or data pack JSON
6 | Real-hardware
checks: shader
packs,
performance,
multiplayer,
dedicated server
It has no real
session or
GPU
A headless server
boot and the
GameTests it can
write
The author sends logs and
screenshots back
7 | Mod identity:
icon, page art,
screenshots,
trailer, changelog
Art and
marketing
A plain placeholder
`logo.png`
`src/main/resources/` and the mod
page
8 | License check
and publishing
Legal and
accounts
The license notes in
section 23
The author

<!-- page 75 of 78 -->

### Optional later: support is built, content added if needed `[SUPPORT ONLY]`

No voice acting is planned.

### What `TODO-MANUAL.md` looks like

```text
[MANUAL] Final Seam reveal sounds | assets/skyseam/sounds/seam/*.ogg | now:
synthesized | owner: author
[MANUAL] FTB Quests chapters | pack config | now: ids only (section 17)
owner: author
[SUPPORT ONLY] Music slot tide_calm
assets/skyseam/sounds/music/tide_calm.ogg | now: silent | see docs/ADDING-
MUSIC.md
```

## 23. Locked decisions and assumptions

Build tags. Reference. Claude Code follows these rows without asking. The author
changes a row here before handing the document over if they disagree.
Nothing here blocks the build. Every question from the Design Bible has a locked answer
that the rest of this document already uses.
Item | Hooks Claude Code builds | Added later by
Tide music (5 slots) and boss
themes (3 slots)
Registered events, the music
manager, the how-to (section
21)
Dropping `.ogg`
files in
Optional music slots: `anchorage`,
`spire`, `seam_open`,
`hush_stinger`
The same manager | Dropping `.ogg`
files in
Per-phase boss stems | The boss and phase exposed to
the manager
Dropping `.ogg`
files in
Other languages | All text through lang keys in
`en_us.json`
Adding
`<lang>.json` files

<!-- page 76 of 78 -->

### Locked decisions

\# | Decision | Locked answer | The alternative
1 | What happens when you
die in the Halcyon
Respawn at a Mooring Post.
Items stay at the death
spot, and Lost and Found
replaces key items. A config
switch turns the safety nets
off
Respawn in the
Overworld, which makes
the trip a bigger risk
2 | What if a ship cannot be
carried between
dimensions
Used only if the M0 spike
shows neither route works.
Players cross alone, and a
Wrightling assembles a
basic Wright Skiff for a fee,
once per Tide cycle
Copy the ship block by
block and accept that
some contraptions will
not survive (Route B)
3 | Progress per player or
per FTB team
Per player, because Fealty
reputation is per player
Per team, which the Idea
pool lists as a switch
4 | Fealty | A required dependency.
Skyseam compiles against
Fealty API 1.3.0 and has no
fallback dialogue runner
none
5 | What the Stillpoint Heart
unlocks
A trophy and the key to the
optional Spire boss rush.
Real rewards come from the
author's FTB Quests
A Home Seam: a personal
return Seam placed at a
chosen Overworld spot
6 | Pneumatic Coupler look | Same model as the original
staff, renamed by the recipe
(Option A)
Its own model and texture
(Option B), which needs
the author's art
7 | Journey length | First crossing after roughly
5 to 8 hours of normal play,
full completion after
roughly 15 to 20
Shorter for a fast pack,
longer with more favors
8 | Names | The working names in
section 3
The alternates in the
same table

<!-- page 77 of 78 -->

### Assumptions

Stack: Minecraft 1.21.1 on NeoForge. Create, Create Aeronautics (the Simulated
project), Sable, GeckoLib and Fealty are required. FTB Quests and KubeJS are optional.
The staff: the Pneumatic Coupler is the original creative physics staff, renamed by its
recipe. Its code was read and no creative-mode check was found, so it works in
survival. Confirm against the pinned version.
Licenses: Skyseam depends on Aeronautics and Sable but ships none of their art or
code. Sable uses the Polyform Shield license and Aeronautics' art is all rights reserved,
so the author reads both licenses before publishing. This document is not legal advice.
References: the four screenshots are style references. The Seam is inspired by the
Dungeons rift but uses original art and sound, and no build or asset is copied.
\# | Decision | Locked answer | The alternative
9 | Starter FTB Quests
chapter
None. Only the ids
(advancements, stats, tags)
A drafted chapter per act,
once the mod exists
10 | World size | 3,000 by 3,000 blocks
(radius 1,500), a fading
border, and no deaths at the
edge
Smaller or larger by config
11 | Music | Support only. No music is
shipped (section 21)
The author adds tracks
later
12 | Structures | Generated by script, with
the optional hand polish in
section 22
The author hand-builds
them
13 | Sound effects | Vanilla stand-ins plus
synthesized files, replaced
by the author later
The author supplies final
files
14 | Order of the Fealty work | The Fealty session starts on
day one and blocks only
M10
none
15 | Source of truth for who
builds what
The table in section 2 | none

<!-- page 78 of 78 -->
Fealty: it gains eight general features and an API bump to 1.3.0, written as a prompt in
section 16, so Skyseam stays a plug-in rather than a patch.
No armor: there are no armor items or trims. Rewards are tools, materials and utility.
Size: the Halcyon is deliberately limited in size, as the author asked, with the Mirror Sea
as its floor and a fading border.
Scope: this document is the build specification. Nothing has been built yet.

### Not checked yet

Whether Sable can save a ship in one dimension and load it in another. The M0 spike
answers it.
Exact mod versions. Create Aeronautics and Sable change quickly, so pin versions
when the build starts, and confirm every external id (section 1, rule 9).
Whether the Blockbench MCP can create GeckoLib-format projects and what it can
export. Claude Code records the answer in `docs/ENVIRONMENT.md` and falls back to
generated JSON if needed.
Whether Sable's native physics library loads on a headless server.
Multiplayer and shader-pack behavior. The design is server-authoritative and avoids
custom shaders, but none of it has been tested.
The quality of generated textures, structures and sounds. Only the author can judge it.
