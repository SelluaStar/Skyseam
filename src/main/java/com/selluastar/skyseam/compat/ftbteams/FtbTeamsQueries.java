package com.selluastar.skyseam.compat.ftbteams;

import java.util.UUID;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;

/** The only class that touches FTB Teams. Loaded only when FTB Teams is installed ({@link FtbTeamsCompat}). */
final class FtbTeamsQueries {
    private FtbTeamsQueries() {}

    static boolean sameTeam(UUID a, UUID b) {
        FTBTeamsAPI.API api = FTBTeamsAPI.api();
        return api != null && api.isManagerLoaded() && api.getManager().arePlayersInSameTeam(a, b);
    }
}
