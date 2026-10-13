package com.selluastar.skyseam.client.compat;

import java.util.List;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.halcyon.vortex.FluxVortex;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/**
 * Flux vortices marked on the map mods players use (docs/DECISIONS.md K105): Xaero's Minimap and World Map
 * ({@link XaeroVortexMarkers}: a waypoint whose symbol is a vortex icon in the game's font) and JourneyMap
 * ({@link JourneyMapVortices}: a vortex icon), each named with what the vortex is doing and how long it has left.
 * Refreshed once a second from the vortices the server sends; neither mod is ever required, and each is left alone for
 * the session if anything goes wrong with it.
 */
public final class VortexMapMarkers {
    private static boolean xaero = ModList.get().isLoaded("xaerominimap");
    private static boolean journeyMap = ModList.get().isLoaded("journeymap");

    private VortexMapMarkers() {}

    /** Puts a marker at each vortex alive now in {@code dimension} and takes the others away. */
    public static void update(List<FluxVortex> vortices, long time, ResourceKey<Level> dimension, Vec3 camera) {
        if (xaero) {
            try {
                XaeroVortexMarkers.update(vortices, time, camera);
            } catch (RuntimeException | LinkageError failed) {
                xaero = false;
                Skyseam.LOGGER.warn("Skyseam: could not mark flux vortices on Xaero's maps; left out for this session", failed);
            }
        }
        if (journeyMap) {
            try {
                JourneyMapVortices.update(vortices, time, dimension);
            } catch (RuntimeException | LinkageError failed) {
                journeyMap = false;
                Skyseam.LOGGER.warn("Skyseam: could not mark flux vortices on JourneyMap; left out for this session", failed);
            }
        }
    }

    /** Takes every marker away (leaving the world). */
    public static void clear() {
        try {
            if (xaero) {
                XaeroVortexMarkers.clear();
            }
            if (journeyMap) {
                JourneyMapVortices.clear();
            }
        } catch (RuntimeException | LinkageError ignored) {
            // The map mod has already let go of the world.
        }
    }

    /** What a vortex's marker says: "Flux vortex: raging, 4:12 left". */
    static String title(FluxVortex vortex, long time) {
        String doing = switch (vortex.phase(time)) {
            case FORMING, WAITING -> "forming";
            case UNRAVELLING, GONE -> "unravelling";
            default -> "raging";
        };
        long seconds = Math.max(0, (vortex.end() - time) / 20);
        return String.format("Flux vortex: %s, %d:%02d left", doing, seconds / 60, seconds % 60);
    }
}
