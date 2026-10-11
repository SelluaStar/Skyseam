# Structure markers

Every functional spot in a Skyseam structure is a **data marker** (spec section 14), so a hand-built replacement only
needs the same markers in the same places. After a structure is placed, the game swaps each marker for its real block
(`world/structure/StructureMarkers`). A marker whose feature does not exist yet becomes air and is logged at debug level.

## What a marker is

A vanilla structure block in **Data** mode. Its "Custom Data Tag Name" is the marker's text:

- `name`, for example `almanac_pages`;
- or `name:facing`, for example `motto:south`. The facing is `north`, `south`, `east` or `west`, as the structure
  is saved. It turns with the structure when it is placed rotated.

## The markers each structure must have

The GameTest `structuresFitAndHaveTheirMarkers` checks that every structure file has at least these, is at most 48
blocks on every side, and uses only the structure palette (the `PALETTE` list in `gametest/HalcyonGameTests`; add
any new block there if a replacement uses one).

### `sundered_obelisk/slice_0` (M3, rebuilt)

The Obelisk is five files, `sundered_obelisk/slice_0` (the hanging foot and the terrace) to `slice_4` (the upper
shaft's top and the capstone), each 48×48 across and at most 48 tall. Only slice 0 has markers.

| Marker | How many | Becomes | Since |
|---|---|---|---|
| `motto:<facing>` | 1 | A waxed cherry wall sign with the Wrights' motto (lang keys `skyseam.motto.line1`…`line4`), facing the way named. In the generated Obelisk it hangs on the gate's lintel on the terrace, facing south, out towards the Arrival Lane | M3 |
| `mooring_post` | 1 | The Mooring Post | air until M4 |
| `almanac_pages` | 1 | The first Almanac pages | air until M4 |
| `wrightling_stall` | 2 | A Wrightling's stall | air until the Wrightling (M5) |

**Where it goes:** somewhere new in each world, floating over open sea 170 blocks in from the Arrival Lane
(`plan/Landmarks`). Each slice's middle column (its size ÷ 2 on x and z) stands on the Obelisk's axis. Slice 0's
bottom layer is the tip of the foot, 26 blocks under the terrace floor; the terrace is at Y 146 to 152. Slice `k`
starts 48 × `k` blocks above slice 0. The files are turned so the gate faces the lane (they are saved facing south).
Nothing else stands within 120 blocks of the axis, so a replacement may use the whole 48×48 footprint.

To replace it by hand, build all five pieces stacked 48 apart and save each with its own structure block as
`skyseam:sundered_obelisk/slice_<k>`.

## Replacing a structure by hand

1. Build it in creative, with the markers above as Data structure blocks.
2. Save it with a structure block (Save mode) as `skyseam:<name>`. A structure block saves at most 48×48×48.
3. Copy `<world>/generated/skyseam/structures/<name>.nbt` over
   `src/main/resources/data/skyseam/structure/<name>.nbt`.
4. Run the GameTests (`tools/verify_milestone.py gametest`) and the server boot check
   (`tools/verify_milestone.py server`). The boot check places the Obelisk in a freshly generated Halcyon and checks
   it against its files, that the motto was carved, and that nothing but air and sea lies under its foot.

The generated first pass comes from `tools/structures/make_sundered_obelisk.py`. Running it again overwrites the
`.nbt` file, so don't run it after replacing the file by hand.
