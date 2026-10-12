package com.selluastar.skyseam.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server config ({@code serverconfig/skyseam-server.toml} in each world). Every number the spec marks as config
 * (spec section 1, rule 5), with the spec's value as the default. Values are added by the milestone that uses them.
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
    public static final ModConfigSpec.IntValue VORTEX_TOP_Y;
    public static final ModConfigSpec.IntValue VORTEX_MIN_SECONDS;
    public static final ModConfigSpec.IntValue VORTEX_MAX_SECONDS;
    public static final ModConfigSpec.IntValue VORTEX_LAUNCH_HEIGHT;
    public static final ModConfigSpec.DoubleValue VORTEX_SHIP_SPEED;
    public static final ModConfigSpec.BooleanValue VORTEX_SCRAMBLE;
    public static final ModConfigSpec.DoubleValue VEIL_PLAYER_PUSH;
    public static final ModConfigSpec.DoubleValue VEIL_SHIP_PUSH;
    public static final ModConfigSpec.IntValue REBOUND_HEIGHT;
    public static final ModConfigSpec.IntValue REBOUND_COOLDOWN_SECONDS;
    public static final ModConfigSpec.IntValue REBOUND_SLOW_FALLING_SECONDS;
    public static final ModConfigSpec.IntValue REBOUND_WEAKNESS_SECONDS;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("seam");
        ENTRY_RADIUS = b.comment("Horizontal radius around the Seam site, in blocks. While a scar is mending, no Seam opens",
                        "within this radius of it, and only one Seam can be open within it. A ship starts charging further out",
                        "than this: see aperture.entry_radius_per_block.")
                .defineInRange("entry_radius", 48, 8, 512);
        SIZE_FACTOR = b.comment("Seam width and height = the ship's bounds times this.")
                .defineInRange("size_factor", 1.5, 1.0, 4.0);
        MIN_SIZE = b.comment("Smallest Seam width or height, in blocks.")
                .defineInRange("min_size", 16, 4, 128);
        MAX_SIZE = b.comment("Largest Seam width or height, in blocks.")
                .defineInRange("max_size", 64, 4, 128);
        HOLD_RADIUS = b.comment("An open Seam stays open while someone is within this many blocks of it.")
                .defineInRange("hold_radius", 128, 16, 512);
        MEND_DELAY_SECONDS = b.comment("Seconds after the last one leaves the hold radius before the Seam mends.")
                .defineInRange("mend_delay_seconds", 5, 0, 600);
        MAX_OPEN_SECONDS = b.comment("A Seam mends after this many seconds open, whoever is near.")
                .defineInRange("max_open_seconds", 120, 10, 3600);
        SCAR_SECONDS = b.comment("Seconds the scar of a mended Seam lingers, during which no Seam opens near it.")
                .defineInRange("scar_seconds", 60, 0, 3600);
        PULL_RADIUS = b.comment("An open Seam gently pulls ships within this many blocks towards it.")
                .defineInRange("pull_radius", 60, 0, 512);
        PULL_STRENGTH = b.comment("How hard the pull is at the Seam, in blocks per second squared. It fades to nothing at",
                        "the edge of the pull radius. 0 turns the pull off.")
                .defineInRange("pull_strength", 0.5, 0.0, 10.0);
        b.pop();

        b.push("sites");
        SITE_DIMENSION = b.comment("The dimension that has Seam sites. A ship with a Harmonic Aperture can only open a Seam here.")
                .define("site_dimension", "minecraft:overworld");
        SITE_SPACING = b.comment("The world is cut into square regions this many blocks wide, each with one Seam site at a",
                        "spot picked from the world seed.")
                .defineInRange("site_spacing", 2048, 256, 65536);
        SITE_MARGIN = b.comment("A site stays at least this many blocks inside its region's edges, so two sites are never",
                        "closer than twice this. Kept below half the spacing.")
                .defineInRange("site_margin", 256, 0, 32768);
        DORMANT_HEIGHT = b.comment("A closed Seam hangs this many blocks above the ground at its site, as a faint scar in the sky.")
                .defineInRange("dormant_height", 40, 0, 256);
        b.pop();

        b.push("aperture");
        MIN_ALTITUDE = b.comment("The ship's lowest block must be at least this many blocks above the ground at the site",
                        "(the highest block that is not leaves).")
                .defineInRange("min_altitude", 30, 0, 256);
        MIN_SPEED = b.comment("The ship counts as flying above this speed, in blocks per second, with no ground contact.")
                .defineInRange("min_speed", 2.0, 0.0, 100.0);
        CHARGE_SECONDS = b.comment("Seconds of charge, with every rule met, before the Seam opens. Dropping a rule pauses the",
                        "charge and drains it at the same rate.")
                .defineInRange("charge_seconds", 5, 1, 120);
        MAX_SPEED = b.comment("A ship faster than this, in blocks per second, cannot open a Seam: its charge drains until it",
                        "slows down. It keeps a ship from flying past the site before the Seam has opened.")
                .defineInRange("max_speed", 10.0, 1.0, 100.0);
        ENTRY_RADIUS_PER_BLOCK = b.comment("A ship starts charging within a radius that grows with its size and speed: seam.entry_radius,",
                        "plus this many blocks for every block of the ship's length, plus as far as the ship flies at its",
                        "speed while the Seam charges and cracks open. Measured from the nearest part of the ship.")
                .defineInRange("entry_radius_per_block", 3.0, 0.0, 16.0);
        MAX_ENTRY_RADIUS = b.comment("The entry radius never grows past this many blocks, however big or fast the ship. A Seam",
                        "further away than the server's view distance can't be seen until the ship comes closer.")
                .defineInRange("max_entry_radius", 256, 16, 512);
        b.pop();

        b.push("halcyon");
        MAIN_ISLAND_DENSITY = b.comment("How many main islands (big landmasses rising from the sea) the Halcyon makes: 1 is the default,",
                        "0 none. These settings apply to worlds whose Halcyon is generated after a change; restart after changing them.")
                .defineInRange("main_island_density", 1.0, 0.0, 1.15);
        ISLE_DENSITY = b.comment("How many small islands are scattered between the main islands.")
                .defineInRange("isle_density", 1.0, 0.0, 2.5);
        SEA_ROCK_DENSITY = b.comment("How many sea stacks, leaning slabs, boulders and arches stand in the sea.")
                .defineInRange("sea_rock_density", 1.0, 0.0, 2.0);
        SPIRE_DENSITY = b.comment("How many needle-spire clusters rise from the sea, mostly in the Hush.")
                .defineInRange("spire_density", 1.0, 0.0, 2.0);
        FLOATING_ISLAND_DENSITY = b.comment("How many floating islands hang above the land and sea.")
                .defineInRange("floating_island_density", 1.0, 0.0, 2.0);
        TREE_DENSITY = b.comment("How thickly trees grow on the islands.")
                .defineInRange("tree_density", 1.0, 0.0, 3.0);
        VEIL_ENABLED = b.comment("Whether the Veil holds the Halcyon in: its pearl fog, its muffling, and its push back from the",
                        "1,500-block rim and the Y 420 lid. Off for now, so the Halcyon has no edge; it will return later.")
                .define("veil_enabled", false);
        VEIL_PLAYER_PUSH = b.comment("How hard the Veil pushes a player back towards the centre at the very rim, in blocks per tick",
                        "of speed added each second. It grows from nothing where the Veil begins.")
                .defineInRange("veil_player_push", 0.6, 0.0, 4.0);
        VEIL_SHIP_PUSH = b.comment("How hard the Veil pushes a ship back towards the centre at the very rim, in blocks per second",
                        "squared. Past the rim a ship flying outward is turned back as well.")
                .defineInRange("veil_ship_push", 4.0, 0.0, 40.0);
        REBOUND_HEIGHT = b.comment("How far above the Mirror Sea Rebound throws whoever falls into it, in blocks. At most 60: the game",
                        "cannot send a player a faster launch than that takes.")
                .defineInRange("rebound_height", 24, 4, 60);
        REBOUND_COOLDOWN_SECONDS = b.comment("Rebound throws the same creature at most once in this many seconds.")
                .defineInRange("rebound_cooldown_seconds", 30, 0, 600);
        REBOUND_SLOW_FALLING_SECONDS = b.comment("Seconds of Slow Falling after a Rebound.")
                .defineInRange("rebound_slow_falling_seconds", 8, 0, 120);
        REBOUND_WEAKNESS_SECONDS = b.comment("Seconds of Weakness once the Slow Falling ends.")
                .defineInRange("rebound_weakness_seconds", 10, 0, 120);
        b.pop();

        b.push("flux_vortex");
        VORTEX_ENABLED = b.comment("Flux vortices form over the Halcyon's low ground: columns of turning wind and light that throw "
                + "creatures up and drive ships down.")
                .define("enabled", true);
        VORTEX_CELL_SIZE = b.comment("The Halcyon is cut into square cells this many blocks wide. Each may hold one vortex at a time,",
                "at a different place each time it forms.")
                .defineInRange("cell_size", 640, 256, 4096);
        VORTEX_CHANCE = b.comment("The chance a cell has a vortex in each ten-minute slot. It forms at a random moment in the slot.")
                .defineInRange("chance", 0.55, 0.0, 1.0);
        VORTEX_TOP_Y = b.comment("The height a vortex reaches, a little above the highest floating islands.")
                .defineInRange("top_y", 400, 200, 600);
        VORTEX_MIN_SECONDS = b.comment("The shortest a vortex lasts, in seconds, forming and unravelling included.")
                .defineInRange("min_seconds", 150, 20, 3600);
        VORTEX_MAX_SECONDS = b.comment("The longest a vortex lasts, in seconds. At most nine and a half minutes, as it lives inside its slot.")
                .defineInRange("max_seconds", 360, 20, 570);
        VORTEX_LAUNCH_HEIGHT = b.comment("How high a vortex throws a creature that walks into it, in blocks. It lands with Slow Falling.")
                .defineInRange("launch_height", 40, 4, 120);
        VORTEX_SHIP_SPEED = b.comment("How fast a vortex drives a ship down, in blocks per second, at full strength.")
                .defineInRange("ship_down_speed", 16.0, 0.0, 60.0);
        VORTEX_SCRAMBLE = b.comment("Inside a vortex, every spinning part of a ship (propellers, shafts, gears) turns a random way at a",
                "random speed until the ship leaves. Off: ships are only driven down.")
                .define("scramble_spinning_parts", true);
        b.pop();
        SPEC = b.build();
    }

    private SkyseamConfig() {}
}
