package com.selluastar.skyseam.client.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.config.SkyseamClientConfig;

import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Keeps the Halcyon's small glowing decorations out of Distant Horizons' far terrain (docs/DECISIONS.md K95).
 *
 * <p>Under a shader pack, Distant Horizons draws every far block that gives off light as glowing, a whole block wide,
 * and passes a skipped plant's colour and glow to the block under it; so crystals, lamps, glow moss, lantern vines
 * and small glow shrooms freckled every far island with bright specks. Once Distant Horizons has loaded its config,
 * this adds the client config's {@code distant_horizons_hidden_blocks} (by default those decorations) to its "ignored
 * render blocks" list ({@code ignoredRenderBlockCsv} in {@code config/DistantHorizons.toml}), keeping whatever is
 * already there. It goes through Distant Horizons' own config entry, as its settings screen does, so the change is
 * saved and takes effect at once. The giant Glowcap mushrooms are not on the list: they are meant to stand out. Nor
 * are the Elder logs and Runed Duskstone: Distant Horizons draws them as wood and stone, not as glowing, and hiding
 * them would leave holes.
 *
 * <p>Distant Horizons stays optional: it is reached by reflection, and if it is missing or its config is laid out
 * differently, nothing happens beyond a warning. {@code distant_horizons_hide_glow_blocks} turns it off.
 */
public final class DistantHorizonsBlocks {
    /** Where the list lives: in Distant Horizons 2.3 to 3.3 (checked in its source), and before that in 2.2. */
    private static final String[] HOLDERS = {"com.seibel.distanthorizons.core.config.Config$Client$Advanced$Graphics$Culling",
            "com.seibel.distanthorizons.core.config.Config$Client$Advanced$LodBuilding"};
    private static final String ENTRY = "ignoredRenderBlockCsv";
    /** How many client ticks to keep trying while Distant Horizons is still starting up. */
    private static final int TRIES = 600;

    private static boolean done;
    private static int tries;

    private DistantHorizonsBlocks() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        if (done) {
            return;
        }
        if (!ModList.get().isLoaded("distanthorizons") || !enabled()) {
            done = true;
            return;
        }
        try {
            Object entry = entry();
            Method get = method(entry, "getTrueValue", "get");
            String current = String.valueOf(get.invoke(entry));
            String merged = merge(current);
            if (!merged.equals(current)) {
                entry.getClass().getMethod("set", Object.class).invoke(entry, merged);
                Skyseam.LOGGER.info("Skyseam: added the Halcyon's glowing decorations to Distant Horizons' ignored blocks ({})", merged);
            }
            done = true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException notReady) {
            if (++tries >= TRIES) {
                done = true;
                Skyseam.LOGGER.warn("Skyseam: could not add the Halcyon's glowing decorations to Distant Horizons' ignored blocks: {}. "
                        + "Add them by hand to ignoredRenderBlockCsv in config/DistantHorizons.toml: {}", notReady, String.join(",", ids()));
            }
        }
    }

    /** {@code csv} with every hidden block added once, keeping its own entries first and in order. */
    static String merge(String csv) {
        Set<String> ids = new LinkedHashSet<>();
        for (String id : csv.split(",")) {
            if (!id.isBlank()) {
                ids.add(id.trim());
            }
        }
        ids.addAll(ids());
        return String.join(",", ids);
    }

    /** The blocks to leave out: the client config's list, or the default if the config isn't loaded. */
    private static List<String> ids() {
        try {
            return List.copyOf(SkyseamClientConfig.DISTANT_HORIZONS_HIDDEN_BLOCKS.get());
        } catch (IllegalStateException notLoaded) {
            return SkyseamClientConfig.GLOW_DECORATIONS;
        }
    }

    private static Object entry() throws ReflectiveOperationException {
        ReflectiveOperationException missing = null;
        for (String holder : HOLDERS) {
            try {
                Field field = Class.forName(holder).getField(ENTRY);
                Object entry = field.get(null);
                if (entry == null) {
                    throw new IllegalStateException("Distant Horizons' config is not loaded yet");
                }
                return entry;
            } catch (ClassNotFoundException | NoSuchFieldException absent) {
                missing = absent;
            }
        }
        throw missing;
    }

    /** The first of {@code names} the entry has, with no parameters. */
    private static Method method(Object target, String... names) throws NoSuchMethodException {
        for (String name : names) {
            try {
                return target.getClass().getMethod(name);
            } catch (NoSuchMethodException next) {
                // try the next name
            }
        }
        throw new NoSuchMethodException(String.join(" or ", names));
    }

    private static boolean enabled() {
        try {
            return SkyseamClientConfig.DISTANT_HORIZONS_HIDE_GLOW_BLOCKS.get();
        } catch (IllegalStateException notLoaded) {
            return SkyseamClientConfig.DISTANT_HORIZONS_HIDE_GLOW_BLOCKS.getDefault();
        }
    }
}
