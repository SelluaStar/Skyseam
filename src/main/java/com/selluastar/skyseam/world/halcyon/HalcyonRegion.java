package com.selluastar.skyseam.world.halcyon;

/**
 * The Halcyon's biomes. Where each lies is different in every world ({@code plan.RegionMap}); each has its own land
 * shapes, rock, colours and flora (docs/DECISIONS.md K77). The biomes themselves are data
 * ({@code data/skyseam/worldgen/biome/}), so their colours can be tuned without code.
 */
public enum HalcyonRegion {
    SPINDLE("the_spindle"),
    HUSH("the_hush"),
    CIRRUS_REEFS("cirrus_reefs"),
    PETALWASH("petalwash_meadows"),
    MIRROR_SHOALS("mirror_shoals"),
    WRECKFIELDS("wreckfields"),
    /** The outer lands (docs/DECISIONS.md K81): layered tan cliffs over turquoise lagoons (reference image 11). */
    SUNREACH_CLIFFS("sunreach_cliffs"),
    /** Rolling green hills grown with giant mushrooms (image 10). */
    CAPWOOD("capwood"),
    /** Misty valleys of giant glowing-vein trees and leaning stone blades (image 8). */
    ELDERWOOD("elderwood"),
    UNDERBLOOM("underbloom");

    private final String biome;

    HalcyonRegion(String biome) {
        this.biome = biome;
    }

    /** The biome's id path, under {@code skyseam:}. */
    public String biome() {
        return biome;
    }

    /** The region whose biome has this id path, or null. */
    public static HalcyonRegion byBiome(String path) {
        for (HalcyonRegion region : values()) {
            if (region.biome.equals(path)) {
                return region;
            }
        }
        return null;
    }
}
