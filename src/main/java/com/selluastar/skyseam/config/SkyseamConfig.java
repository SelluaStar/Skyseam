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

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("seam");
        ENTRY_RADIUS = b.comment("Horizontal radius around the Seam site, in blocks. While a scar is mending, no Seam opens",
                        "within this radius of it, and only one Seam can be open within it.")
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
        SPEC = b.build();
    }

    private SkyseamConfig() {}
}
