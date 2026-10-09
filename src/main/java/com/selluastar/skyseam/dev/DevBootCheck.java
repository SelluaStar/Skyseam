package com.selluastar.skyseam.dev;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.world.HalcyonLayout;

import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Development only. With {@code -Dskyseam.dev.stopAfterBoot=true} (set by {@code gradlew runServer -PbootCheck}) a
 * dedicated server logs a marker line once it has fully started and then stops itself. Gradle cannot pass "stop"
 * to the server console, so this is how the headless boot check ends cleanly. Without the property nothing happens.
 */
public final class DevBootCheck {
    public static final String PROPERTY = "skyseam.dev.stopAfterBoot";
    public static final String PASSED_MARKER = "Skyseam boot check passed";

    private DevBootCheck() {}

    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        if (!Boolean.getBoolean(PROPERTY) || !server.isDedicatedServer()) {
            return;
        }
        // The placeholder Halcyon is a data-pack dimension: check it really loaded (docs/DECISIONS.md K52).
        if (server.getLevel(HalcyonLayout.LEVEL) == null) {
            Skyseam.LOGGER.error("Skyseam boot check failed: the dimension {} did not load", HalcyonLayout.LEVEL.location());
        } else {
            Skyseam.LOGGER.info("{}: dedicated server started with {} mods loaded and the dimension {}. Stopping because -D{}=true",
                    PASSED_MARKER, ModList.get().size(), HalcyonLayout.LEVEL.location(), PROPERTY);
        }
        server.halt(false);
    }
}
