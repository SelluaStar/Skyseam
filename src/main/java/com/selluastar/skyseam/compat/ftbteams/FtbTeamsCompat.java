package com.selluastar.skyseam.compat.ftbteams;

import java.util.UUID;

import net.neoforged.fml.ModList;

/**
 * Optional FTB Teams support (spec section 5: "teammates can use it if the pack runs FTB Teams"). FTB Teams is never
 * required: without it this always answers no, and no FTB class is ever loaded.
 */
public final class FtbTeamsCompat {
    private static final String MOD_ID = "ftbteams";
    private static Boolean loaded;

    private FtbTeamsCompat() {}

    /** True if FTB Teams is installed and puts both players in the same team. */
    public static boolean sameTeam(UUID a, UUID b) {
        if (loaded == null) {
            loaded = ModList.get() != null && ModList.get().isLoaded(MOD_ID);
        }
        return loaded && FtbTeamsQueries.sameTeam(a, b);
    }
}
