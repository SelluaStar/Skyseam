package com.selluastar.skyseam.compat.fealty;

import java.util.Optional;

import com.selluastar.skyseam.Skyseam;

import net.neoforged.fml.ModList;

/**
 * The single entry point for Fealty (spec section 15). Everything Skyseam does with Fealty lives in this package
 * and uses only Fealty's public API, documented in vendor/fealty-source/docs/API.md. The conversations, rumours,
 * favor objectives and the Stargazer are added in M10.
 */
public final class FealtyCompat {
    public static final String MOD_ID = "fealty";
    /** The Fealty public API version Skyseam is written against (spec decision 4). */
    public static final String REQUIRED_API_VERSION = "1.3.0";

    private FealtyCompat() {}

    /**
     * Fealty's public API version, read from the {@code apiVersion} mod property in Fealty's neoforge.mods.toml.
     * Reading it from the mod metadata, not from a compiled constant, shows the version of the jar that is
     * actually loaded.
     */
    public static Optional<String> apiVersion() {
        return ModList.get().getModContainerById(MOD_ID)
                .map(container -> container.getModInfo().getModProperties().get("apiVersion"))
                .map(Object::toString);
    }

    public static void logApiVersion() {
        String version = apiVersion().orElse("unknown");
        if (REQUIRED_API_VERSION.equals(version)) {
            Skyseam.LOGGER.info("Skyseam found Fealty API {}", version);
        } else {
            Skyseam.LOGGER.warn("Skyseam needs Fealty API {}, but the loaded Fealty reports {}. Its story chain may not work",
                    REQUIRED_API_VERSION, version);
        }
    }
}
