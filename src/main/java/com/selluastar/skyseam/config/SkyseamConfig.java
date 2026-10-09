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
        SPEC = b.build();
    }

    private SkyseamConfig() {}
}
