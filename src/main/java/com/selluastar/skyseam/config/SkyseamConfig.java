package com.selluastar.skyseam.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server config ({@code config/skyseam-server.toml}). Every number the spec marks as config (spec section 1, rule 5),
 * with the spec's value as the default. Values are added by the milestone that uses them.
 *
 * <p>Laid out for the in-game screen (Mods, Skyseam, Config; docs/DECISIONS.md K103): the Halcyon first (its shape,
 * its flux vortices in four pages, the wind lanes, the Springbloom, the Murmurberry, the Veil and Rebound), then the Seam, its sites and the Harmonic
 * Aperture. Every entry and section has a name in the language file ({@code skyseam.configuration.<path>}); its
 * comment is its tooltip.
 */
public final class SkyseamConfig {
    public static final ModConfigSpec SPEC;

    // Spec section 6, "Trigger rules" and "The reveal, beat by beat".
    public static final ModConfigSpec.IntValue ENTRY_RADIUS;
    public static final ModConfigSpec.DoubleValue SIZE_FACTOR;
    public static final ModConfigSpec.IntValue MIN_SIZE;
    public static final ModConfigSpec.IntValue MAX_SIZE;
    public static final ModConfigSpec.IntValue HOLD_RADIUS;
    public static final ModConfigSpec.IntValue MEND_DELAY_SECONDS;
    public static final ModConfigSpec.IntValue MAX_OPEN_SECONDS;
    public static final ModConfigSpec.IntValue SCAR_SECONDS;
    public static final ModConfigSpec.IntValue PULL_RADIUS;
    public static final ModConfigSpec.DoubleValue PULL_STRENGTH;

    // Spec section 6 "Trigger rules", with the author's change to many sites (docs/DECISIONS.md K47).
    public static final ModConfigSpec.ConfigValue<String> SITE_DIMENSION;
    public static final ModConfigSpec.IntValue SITE_SPACING;
    public static final ModConfigSpec.IntValue SITE_MARGIN;
    public static final ModConfigSpec.IntValue DORMANT_HEIGHT;
    public static final ModConfigSpec.IntValue MIN_ALTITUDE;
    public static final ModConfigSpec.DoubleValue MIN_SPEED;
    public static final ModConfigSpec.IntValue CHARGE_SECONDS;
    // The author's changes after the first in-game test (docs/DECISIONS.md K54).
    public static final ModConfigSpec.DoubleValue MAX_SPEED;
    public static final ModConfigSpec.DoubleValue ENTRY_RADIUS_PER_BLOCK;
    public static final ModConfigSpec.IntValue MAX_ENTRY_RADIUS;

    // Spec section 7, the Halcyon (docs/DECISIONS.md K59 to K66).
    public static final ModConfigSpec.DoubleValue MAIN_ISLAND_DENSITY;
    public static final ModConfigSpec.DoubleValue ISLE_DENSITY;
    public static final ModConfigSpec.DoubleValue SEA_ROCK_DENSITY;
    public static final ModConfigSpec.DoubleValue SPIRE_DENSITY;
    public static final ModConfigSpec.DoubleValue FLOATING_ISLAND_DENSITY;
    public static final ModConfigSpec.DoubleValue TREE_DENSITY;
    public static final ModConfigSpec.BooleanValue VEIL_ENABLED;
    public static final ModConfigSpec.BooleanValue VORTEX_ENABLED;
    public static final ModConfigSpec.IntValue VORTEX_CELL_SIZE;
    public static final ModConfigSpec.DoubleValue VORTEX_CHANCE;
    public static final ModConfigSpec.IntValue VORTEX_MIN_SECONDS;
    public static final ModConfigSpec.IntValue VORTEX_MAX_SECONDS;
    public static final ModConfigSpec.IntValue VORTEX_MIN_COOLDOWN_SECONDS;
    public static final ModConfigSpec.IntValue VORTEX_MAX_COOLDOWN_SECONDS;
    public static final ModConfigSpec.IntValue VORTEX_TOP_Y;
    public static final ModConfigSpec.DoubleValue VORTEX_NEARBY_CHANCE;
    public static final ModConfigSpec.IntValue VORTEX_NEARBY_AREA;
    public static final ModConfigSpec.IntValue VORTEX_NEARBY_EVERY_SECONDS;
    public static final ModConfigSpec.IntValue VORTEX_SEND_RADIUS;
    public static final ModConfigSpec.DoubleValue VORTEX_LOW_GRAVITY;
    public static final ModConfigSpec.IntValue VORTEX_GRAVITY_REACH;
    public static final ModConfigSpec.DoubleValue VORTEX_LIFT_SPEED;
    public static final ModConfigSpec.DoubleValue VORTEX_SHIP_PULL;
    public static final ModConfigSpec.DoubleValue VORTEX_SHIP_ORBIT;
    public static final ModConfigSpec.IntValue VORTEX_SHIP_REACH;
    public static final ModConfigSpec.DoubleValue VORTEX_SHIP_SPIN;
    public static final ModConfigSpec.BooleanValue VORTEX_SCRAMBLE;
    public static final ModConfigSpec.IntValue SPRINGBLOOM_BASE_LAUNCH_HEIGHT;
    public static final ModConfigSpec.DoubleValue SPRINGBLOOM_CHANCE;
    public static final ModConfigSpec.BooleanValue WIND_LANES_ENABLED;
    public static final ModConfigSpec.DoubleValue WIND_LANES_SPEED;
    public static final ModConfigSpec.DoubleValue WIND_LANES_SHIP_PUSH;
    public static final ModConfigSpec.IntValue WIND_LANES_SPACING;
    public static final ModConfigSpec.DoubleValue WIND_LANES_CHANCE;
    public static final ModConfigSpec.IntValue MURMURBERRY_RANGE;
    public static final ModConfigSpec.DoubleValue MURMURBERRY_MAX_DELAY_SECONDS;
    public static final ModConfigSpec.DoubleValue MURMURBERRY_DENSITY;
    public static final ModConfigSpec.DoubleValue VEIL_PLAYER_PUSH;
    public static final ModConfigSpec.DoubleValue VEIL_SHIP_PUSH;
    public static final ModConfigSpec.IntValue REBOUND_HEIGHT;
    public static final ModConfigSpec.IntValue REBOUND_COOLDOWN_SECONDS;
    public static final ModConfigSpec.IntValue REBOUND_SLOW_FALLING_SECONDS;
    public static final ModConfigSpec.IntValue REBOUND_WEAKNESS_SECONDS;

    /** The language key every entry and section is named by: {@code skyseam.configuration.<path>}. */
    private static final String KEY = "skyseam.configuration.";

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        section(b, "halcyon_world");
        MAIN_ISLAND_DENSITY = named(b, "halcyon_world.main_island_density").comment(
                        "How many main islands (big landmasses rising from the sea) the Halcyon makes: 1 is the usual, 0 none.",
                        "These shape settings apply to a Halcyon generated after a change; restart after changing them.")
                .worldRestart().defineInRange("main_island_density", 1.0, 0.0, 1.15);
        ISLE_DENSITY = named(b, "halcyon_world.isle_density").comment("How many small islands are scattered between the main islands.")
                .worldRestart().defineInRange("isle_density", 1.0, 0.0, 2.5);
        FLOATING_ISLAND_DENSITY = named(b, "halcyon_world.floating_island_density").comment("How many floating islands hang above the land and sea.")
                .worldRestart().defineInRange("floating_island_density", 1.0, 0.0, 2.0);
        SEA_ROCK_DENSITY = named(b, "halcyon_world.sea_rock_density").comment("How many sea stacks, leaning slabs, boulders and arches stand in the sea.")
                .worldRestart().defineInRange("sea_rock_density", 1.0, 0.0, 2.0);
        SPIRE_DENSITY = named(b, "halcyon_world.spire_density").comment("How many needle-spire clusters rise from the sea, mostly in the Hush.")
                .worldRestart().defineInRange("spire_density", 1.0, 0.0, 2.0);
        TREE_DENSITY = named(b, "halcyon_world.tree_density").comment("How thickly trees grow on the islands.")
                .worldRestart().defineInRange("tree_density", 1.0, 0.0, 3.0);
        b.pop();

        section(b, "flux_vortices");
        section(b, "flux_vortices.spawning");
        VORTEX_ENABLED = named(b, "flux_vortices.spawning.enabled").comment(
                        "Flux vortices: columns of turning wind and light over the Halcyon's low ground that throw creatures up and",
                        "drive ships down.")
                .define("enabled", true);
        VORTEX_CELL_SIZE = named(b, "flux_vortices.spawning.cell_size").comment(
                        "The Halcyon is cut into square cells this many blocks wide. Each holds one vortex at a time, one after",
                        "another, at a different spot each time. Smaller cells: more vortices, closer together.")
                .defineInRange("cell_size", 460, 192, 4096);
        VORTEX_CHANCE = named(b, "flux_vortices.spawning.chance").comment(
                        "The chance a cell's next vortex forms: if it doesn't, the cell stays empty for as long as that one would",
                        "have lasted and its cooldown.")
                .defineInRange("chance", 0.65, 0.0, 1.0);
        VORTEX_MIN_SECONDS = named(b, "flux_vortices.spawning.min_lifetime_seconds").comment(
                        "The shortest a vortex lasts, in seconds, forming and unravelling included. Each lasts a random time",
                        "between this and the longest.")
                .defineInRange("min_lifetime_seconds", 180, 20, 1800);
        VORTEX_MAX_SECONDS = named(b, "flux_vortices.spawning.max_lifetime_seconds").comment("The longest a vortex lasts, in seconds.")
                .defineInRange("max_lifetime_seconds", 420, 20, 1800);
        VORTEX_MIN_COOLDOWN_SECONDS = named(b, "flux_vortices.spawning.min_cooldown_seconds").comment(
                        "After a vortex unravels, its cell waits a random time between this and the longest cooldown, in seconds,",
                        "before the next forms. Every cell keeps its own times, so they never all change at once.")
                .defineInRange("min_cooldown_seconds", 15, 0, 1800);
        VORTEX_MAX_COOLDOWN_SECONDS = named(b, "flux_vortices.spawning.max_cooldown_seconds").comment("The longest cooldown, in seconds.")
                .defineInRange("max_cooldown_seconds", 120, 0, 1800);
        VORTEX_TOP_Y = named(b, "flux_vortices.spawning.top_y").comment("The height a vortex reaches, a little above the highest floating islands.")
                .defineInRange("top_y", 400, 200, 600);
        b.pop();
        section(b, "flux_vortices.near_you");
        VORTEX_NEARBY_CHANCE = named(b, "flux_vortices.near_you.chance").comment(
                        "Every so often a player in the Halcyon with no vortex in the area round them has this chance of one",
                        "forming 32 to 50 blocks from them, where one may stand. 0 turns it off.")
                .defineInRange("chance", 0.35, 0.0, 1.0);
        VORTEX_NEARBY_AREA = named(b, "flux_vortices.near_you.area").comment("The area, in blocks round the player, that must have no vortex.")
                .defineInRange("area", 640, 64, 4096);
        VORTEX_NEARBY_EVERY_SECONDS = named(b, "flux_vortices.near_you.every_seconds").comment(
                        "How often each player is checked, in seconds, each at a moment of their own.")
                .defineInRange("every_seconds", 300, 30, 3600);
        b.pop();
        section(b, "flux_vortices.distance");
        VORTEX_SEND_RADIUS = named(b, "flux_vortices.distance.send_radius").comment(
                        "Players are sent the vortices this many blocks round them, so they can be seen from far off with Distant",
                        "Horizons. A little past its usual distance (256 chunks); raise it with Distant Horizons' own.")
                .defineInRange("send_radius", 4608, 1024, 16384);
        b.pop();
        section(b, "flux_vortices.effects");
        VORTEX_LOW_GRAVITY = named(b, "flux_vortices.effects.low_gravity").comment(
                        "How strong gravity is at a vortex's edge and inside it, against the usual: 0.2 is a fifth. It weakens",
                        "gradually as anything (a player, a creature, a ship) comes closer. 1 leaves gravity alone.")
                .defineInRange("low_gravity", 0.2, 0.0, 1.0);
        VORTEX_GRAVITY_REACH = named(b, "flux_vortices.effects.gravity_reach").comment(
                        "How far out from a vortex gravity starts to weaken, in blocks from its edge.")
                .defineInRange("gravity_reach", 40, 0, 200);
        VORTEX_LIFT_SPEED = named(b, "flux_vortices.effects.lift_speed").comment(
                        "A player or creature that touches a vortex is drawn in and carried up it, round and round, at this many",
                        "blocks per second, until it is flung out over the top. No Slow Falling: the fall hurts as usual.")
                .defineInRange("lift_speed", 16.0, 1.0, 60.0);
        VORTEX_SHIP_PULL = named(b, "flux_vortices.effects.ship_pull_speed").comment(
                        "How fast a vortex draws a ship (a Create Aeronautics contraption) in towards it, in blocks per second, as",
                        "it sweeps it round in a spiral. A ship's engines can fight it. 0 draws none in.")
                .defineInRange("ship_pull_speed", 1.5, 0.0, 20.0);
        VORTEX_SHIP_ORBIT = named(b, "flux_vortices.effects.ship_orbit_speed").comment(
                        "How fast a vortex sweeps a ship round it, in blocks per second at its edge: with the pull, a spiral in,",
                        "like something orbiting into a black hole.")
                .defineInRange("ship_orbit_speed", 12.0, 0.0, 60.0);
        VORTEX_SHIP_REACH = named(b, "flux_vortices.effects.ship_pull_reach").comment(
                        "How far out from a vortex's edge it starts to draw ships in, in blocks.")
                .defineInRange("ship_pull_reach", 48, 0, 256);
        VORTEX_SHIP_SPIN = named(b, "flux_vortices.effects.ship_spin").comment(
                        "How fast a ship inside a vortex is spun round, in degrees per second, until it leaves.")
                .defineInRange("ship_spin", 45.0, 0.0, 360.0);
        VORTEX_SCRAMBLE = named(b, "flux_vortices.effects.scramble_spinning_parts").comment(
                        "Inside a vortex, every spinning part of a ship (propellers, shafts, gears) turns a random way at a random",
                        "speed until the ship leaves. Off: ships are only spun and drawn round.")
                .define("scramble_spinning_parts", true);
        b.pop();
        b.pop();

        section(b, "springbloom");
        SPRINGBLOOM_CHANCE = named(b, "springbloom.chance").comment(
                        "Springblooms grow on the grass of the Halcyon's islands, grounded and floating: one spot in each 11-block",
                        "square may hold one, with this chance. 0 grows none. Only new chunks.")
                .defineInRange("chance", 0.45, 0.0, 1.0);
        SPRINGBLOOM_BASE_LAUNCH_HEIGHT = named(b, "springbloom.base_launch_height").comment(
                        "How high a Springbloom of the usual size throws whatever jumps off it, in blocks. Bigger ones throw higher,",
                        "up to twice as high from one three times the size; each launch goes up to an eighth higher or lower. The",
                        "first landing after a launch does no harm.")
                .defineInRange("base_launch_height", 24, 2, 64);
        b.pop();

        section(b, "wind_lanes");
        WIND_LANES_ENABLED = named(b, "wind_lanes.enabled").comment(
                        "Whether the Halcyon has its wind lanes: streams of moving air all over it, drawn as ribbons of light with",
                        "petals and motes, that carry ships and anyone in the air along them.")
                .define("enabled", true);
        WIND_LANES_SPEED = named(b, "wind_lanes.speed").comment(
                        "How fast the wind in a lane blows, in blocks per second (each lane a little faster or slower). It turns",
                        "round through the Hush, the last quarter of the Lantern-Sun's day.")
                .defineInRange("speed", 16.0, 0.0, 60.0);
        WIND_LANES_SHIP_PUSH = named(b, "wind_lanes.ship_push").comment(
                        "How firmly a lane carries a ship along it: how much of the difference between its speed along the lane",
                        "and the wind's it makes up each second, in the lane's middle (less towards its edge). 0 carries none; a ship",
                        "already outrunning the wind is left alone.")
                .defineInRange("ship_push", 1.5, 0.0, 10.0);
        WIND_LANES_SPACING = named(b, "wind_lanes.spacing").comment(
                        "The Halcyon is cut into square cells this many blocks wide, each with one lane or none (600 to 1,300 blocks",
                        "long, its middle in the cell). Smaller cells: more lanes, closer together.")
                .defineInRange("spacing", 720, 256, 4096);
        WIND_LANES_CHANCE = named(b, "wind_lanes.chance").comment(
                        "The chance a cell has a lane.")
                .defineInRange("chance", 0.85, 0.0, 1.0);
        b.pop();

        section(b, "murmurberry");
        MURMURBERRY_RANGE = named(b, "murmurberry.range").comment(
                        "How far Murmurberries hear, in blocks. Anything a sculk sensor would take as a vibration (a step, a fall, a",
                        "splash, a block placed or broken, a door; not a crouched step, nor anything on wool) lights every Murmurberry",
                        "this close to it for a split second.")
                .defineInRange("range", 32, 4, 96);
        MURMURBERRY_MAX_DELAY_SECONDS = named(b, "murmurberry.max_delay_seconds").comment(
                        "How long the light takes to reach a Murmurberry at the edge of that range, in seconds. Nearer ones light",
                        "sooner and the nearest at once, so the light runs out from the sound in a ring. 0 lights them all together.")
                .defineInRange("max_delay_seconds", 10.0, 0.0, 30.0);
        MURMURBERRY_DENSITY = named(b, "murmurberry.density").comment(
                        "How thickly Murmurberries grow on the floors, walls and roofs of the Halcyon's caves and under its overhangs:",
                        "1 is the usual, 2 twice as many, 0 none. Only new chunks.")
                .defineInRange("density", 1.0, 0.0, 4.0);
        b.pop();

        section(b, "veil");
        VEIL_ENABLED = named(b, "veil.enabled").comment(
                        "Whether the Veil holds the Halcyon in: its pearl fog, its muffling, and its push back from the 1,500-block",
                        "rim and the Y 420 lid. Off for now, so the Halcyon has no edge; it will return later.")
                .define("enabled", false);
        VEIL_PLAYER_PUSH = named(b, "veil.player_push").comment(
                        "How hard the Veil pushes a player back towards the centre at the very rim, in blocks per tick of speed",
                        "added each second. It grows from nothing where the Veil begins.")
                .defineInRange("player_push", 0.6, 0.0, 4.0);
        VEIL_SHIP_PUSH = named(b, "veil.ship_push").comment(
                        "How hard the Veil pushes a ship back towards the centre at the very rim, in blocks per second squared.",
                        "Past the rim a ship flying outward is turned back as well.")
                .defineInRange("ship_push", 4.0, 0.0, 40.0);
        b.pop();

        section(b, "rebound");
        REBOUND_HEIGHT = named(b, "rebound.height").comment(
                        "How far above the Mirror Sea Rebound throws whoever falls into it, in blocks. At most 60: the game cannot",
                        "send a player a faster launch than that takes.")
                .defineInRange("height", 24, 4, 60);
        REBOUND_COOLDOWN_SECONDS = named(b, "rebound.cooldown_seconds").comment("Rebound throws the same creature at most once in this many seconds.")
                .defineInRange("cooldown_seconds", 30, 0, 600);
        REBOUND_SLOW_FALLING_SECONDS = named(b, "rebound.slow_falling_seconds").comment("Seconds of Slow Falling after a Rebound.")
                .defineInRange("slow_falling_seconds", 8, 0, 120);
        REBOUND_WEAKNESS_SECONDS = named(b, "rebound.weakness_seconds").comment("Seconds of Weakness once the Slow Falling ends.")
                .defineInRange("weakness_seconds", 10, 0, 120);
        b.pop();

        section(b, "seam");
        ENTRY_RADIUS = named(b, "seam.entry_radius").comment("Horizontal radius around the Seam site, in blocks. While a scar is mending, no Seam opens",
                        "within this radius of it, and only one Seam can be open within it. A ship starts charging further out",
                        "than this: see aperture.entry_radius_per_block.")
                .defineInRange("entry_radius", 48, 8, 512);
        SIZE_FACTOR = named(b, "seam.size_factor").comment("Seam width and height = the ship's bounds times this.")
                .defineInRange("size_factor", 1.5, 1.0, 4.0);
        MIN_SIZE = named(b, "seam.min_size").comment("Smallest Seam width or height, in blocks.")
                .defineInRange("min_size", 16, 4, 128);
        MAX_SIZE = named(b, "seam.max_size").comment("Largest Seam width or height, in blocks.")
                .defineInRange("max_size", 64, 4, 128);
        HOLD_RADIUS = named(b, "seam.hold_radius").comment("An open Seam stays open while someone is within this many blocks of it.")
                .defineInRange("hold_radius", 128, 16, 512);
        MEND_DELAY_SECONDS = named(b, "seam.mend_delay_seconds").comment("Seconds after the last one leaves the hold radius before the Seam mends.")
                .defineInRange("mend_delay_seconds", 5, 0, 600);
        MAX_OPEN_SECONDS = named(b, "seam.max_open_seconds").comment("A Seam mends after this many seconds open, whoever is near.")
                .defineInRange("max_open_seconds", 120, 10, 3600);
        SCAR_SECONDS = named(b, "seam.scar_seconds").comment("Seconds the scar of a mended Seam lingers, during which no Seam opens near it.")
                .defineInRange("scar_seconds", 60, 0, 3600);
        PULL_RADIUS = named(b, "seam.pull_radius").comment("An open Seam gently pulls ships within this many blocks towards it.")
                .defineInRange("pull_radius", 60, 0, 512);
        PULL_STRENGTH = named(b, "seam.pull_strength").comment("How hard the pull is at the Seam, in blocks per second squared. It fades to nothing at",
                        "the edge of the pull radius. 0 turns the pull off.")
                .defineInRange("pull_strength", 0.5, 0.0, 10.0);
        b.pop();

        section(b, "sites");
        SITE_DIMENSION = named(b, "sites.site_dimension").comment("The dimension that has Seam sites. A ship with a Harmonic Aperture can only open a Seam here.")
                .define("site_dimension", "minecraft:overworld");
        SITE_SPACING = named(b, "sites.site_spacing").comment("The world is cut into square regions this many blocks wide, each with one Seam site at a",
                        "spot picked from the world seed.")
                .defineInRange("site_spacing", 2048, 256, 65536);
        SITE_MARGIN = named(b, "sites.site_margin").comment("A site stays at least this many blocks inside its region's edges, so two sites are never",
                        "closer than twice this. Kept below half the spacing.")
                .defineInRange("site_margin", 256, 0, 32768);
        DORMANT_HEIGHT = named(b, "sites.dormant_height").comment("A closed Seam hangs this many blocks above the ground at its site, as a faint scar in the sky.")
                .defineInRange("dormant_height", 40, 0, 256);
        b.pop();

        section(b, "aperture");
        MIN_ALTITUDE = named(b, "aperture.min_altitude").comment("The ship's lowest block must be at least this many blocks above the ground at the site",
                        "(the highest block that is not leaves).")
                .defineInRange("min_altitude", 30, 0, 256);
        MIN_SPEED = named(b, "aperture.min_speed").comment("The ship counts as flying above this speed, in blocks per second, with no ground contact.")
                .defineInRange("min_speed", 2.0, 0.0, 100.0);
        CHARGE_SECONDS = named(b, "aperture.charge_seconds").comment("Seconds of charge, with every rule met, before the Seam opens. Dropping a rule pauses the",
                        "charge and drains it at the same rate.")
                .defineInRange("charge_seconds", 5, 1, 120);
        MAX_SPEED = named(b, "aperture.max_speed").comment("A ship faster than this, in blocks per second, cannot open a Seam: its charge drains until it",
                        "slows down. It keeps a ship from flying past the site before the Seam has opened.")
                .defineInRange("max_speed", 10.0, 1.0, 100.0);
        ENTRY_RADIUS_PER_BLOCK = named(b, "aperture.entry_radius_per_block").comment("A ship starts charging within a radius that grows with its size and speed: seam.entry_radius,",
                        "plus this many blocks for every block of the ship's length, plus as far as the ship flies at its",
                        "speed while the Seam charges and cracks open. Measured from the nearest part of the ship.")
                .defineInRange("entry_radius_per_block", 3.0, 0.0, 16.0);
        MAX_ENTRY_RADIUS = named(b, "aperture.max_entry_radius").comment("The entry radius never grows past this many blocks, however big or fast the ship. A Seam",
                        "further away than the server's view distance can't be seen until the ship comes closer.")
                .defineInRange("max_entry_radius", 256, 16, 512);
        b.pop();

        SPEC = b.build();
    }

    /** Opens a section named by the language file, and its page's title. */
    private static void section(ModConfigSpec.Builder b, String path) {
        b.translation(KEY + path).push(path.substring(path.lastIndexOf('.') + 1));
    }

    /** The next entry, named by the language file. */
    private static ModConfigSpec.Builder named(ModConfigSpec.Builder b, String path) {
        return b.translation(KEY + path);
    }

    private SkyseamConfig() {}
}
