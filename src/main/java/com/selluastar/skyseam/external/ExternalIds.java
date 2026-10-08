package com.selluastar.skyseam.external;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.compat.fealty.FealtyCompat;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;

/**
 * Every mod id, item id, class name and pinned version that Skyseam takes from Create, Create Aeronautics
 * (Simulated) and Sable, kept in one place (spec section 1, rule 9). Each name was confirmed against the
 * pinned jars, see docs/ENVIRONMENT.md. When a dependency is updated, change the pins here and in
 * gradle.properties, then run the GameTests.
 */
public final class ExternalIds {
    public static final String CREATE = "create";
    public static final String SIMULATED = "simulated";
    public static final String AERONAUTICS = "aeronautics";
    public static final String SABLE = "sable";
    public static final String SABLE_COMPANION = "sablecompanion";
    public static final String GECKOLIB = "geckolib";

    /** Required mods and the version Skyseam is built and tested against. */
    public static final Map<String, String> PINNED_VERSIONS = pins();

    /** Create Aeronautics' creative physics staff. The Pneumatic Coupler recipe renames it (spec section 11, Option A). */
    public static final ResourceLocation PHYSICS_STAFF = ResourceLocation.fromNamespaceAndPath(SIMULATED, "creative_physics_staff");
    /** The staff's class, named rather than imported so that Skyseam does not compile against Simulated. */
    public static final String PHYSICS_STAFF_CLASS = "dev.simulated_team.simulated.content.physics_staff.PhysicsStaffItem";

    /** The two Create parts in the Harmonic Aperture and Pneumatic Coupler recipes. */
    public static final ResourceLocation PRECISION_MECHANISM = ResourceLocation.fromNamespaceAndPath(CREATE, "precision_mechanism");
    public static final ResourceLocation BRASS_CASING = ResourceLocation.fromNamespaceAndPath(CREATE, "brass_casing");

    private ExternalIds() {}

    private static Map<String, String> pins() {
        Map<String, String> pins = new LinkedHashMap<>();
        pins.put(CREATE, "6.0.10");
        pins.put(SIMULATED, "1.3.2");
        pins.put(AERONAUTICS, "1.3.2");
        pins.put(SABLE, "2.0.6");
        pins.put(SABLE_COMPANION, "1.6.0");
        pins.put(GECKOLIB, "4.9.3");
        pins.put(FealtyCompat.MOD_ID, "0.1.0");
        return Collections.unmodifiableMap(pins);
    }

    public static Optional<String> loadedVersion(String modId) {
        return ModList.get().getModContainerById(modId).map(c -> c.getModInfo().getVersion().toString());
    }

    /** One line per pinned mod whose loaded version differs from its pin. Empty when everything matches. */
    public static List<String> versionMismatches() {
        List<String> mismatches = new ArrayList<>();
        PINNED_VERSIONS.forEach((modId, pinned) -> {
            String loaded = loadedVersion(modId).orElse("not loaded");
            if (!loaded.equals(pinned)) {
                mismatches.add(modId + ": pinned " + pinned + ", loaded " + loaded);
            }
        });
        return mismatches;
    }

    public static void logLoadedVersions() {
        PINNED_VERSIONS.keySet().forEach(modId ->
                Skyseam.LOGGER.info("Skyseam dependency {} {}", modId, loadedVersion(modId).orElse("not loaded")));
        versionMismatches().forEach(line ->
                Skyseam.LOGGER.warn("Skyseam was tested against a different version of {}. Its ids and APIs may have changed", line));
    }
}
