package com.selluastar.skyseam.config;

import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client config ({@code config/skyseam-client.toml}): how strong the screen effects are for this player, and how
 * Skyseam sets up other client mods.
 */
public final class SkyseamClientConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue CAMERA_SHAKE;
    public static final ModConfigSpec.DoubleValue SCREEN_FLASH;
    public static final ModConfigSpec.BooleanValue DISTANT_HORIZONS_HIDE_GLOW_BLOCKS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DISTANT_HORIZONS_HIDDEN_BLOCKS;

    /**
     * The Halcyon's small glowing decorations: Prismite, Prismite lamps, glow moss, lantern vines and the small glow
     * shrooms. The giant Glowcap mushrooms are left out, so they still glow far away.
     */
    public static final List<String> GLOW_DECORATIONS = List.of("skyseam:small_prismite_bud", "skyseam:large_prismite_bud",
            "skyseam:prismite_cluster", "skyseam:prismite_lamp", "skyseam:glow_moss", "skyseam:lantern_vine", "skyseam:glow_shroom",
            "skyseam:springbloom");

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("effects");
        CAMERA_SHAKE = b.comment("Strength of the camera shake when a Seam opens near you. 0 turns it off.")
                .defineInRange("camera_shake", 1.0, 0.0, 1.0);
        SCREEN_FLASH = b.comment("Strength of the white flash when your ship crosses a Seam. 0 turns it off.")
                .defineInRange("screen_flash", 1.0, 0.0, 1.0);
        b.pop();
        b.push("compat");
        DISTANT_HORIZONS_HIDE_GLOW_BLOCKS = b.comment("With Distant Horizons installed, add the blocks below to its ignored blocks "
                + "(ignoredRenderBlockCsv), so far islands aren't freckled with glowing specks under shaders. Entries already in its list are kept. "
                + "Turn off to manage Distant Horizons' list yourself (blocks already added stay there until you remove them).")
                .define("distant_horizons_hide_glow_blocks", true);
        DISTANT_HORIZONS_HIDDEN_BLOCKS = b.comment("The blocks Distant Horizons is asked to leave out of its far terrain. By default the Halcyon's "
                + "small glowing decorations; the giant Glowcap mushrooms are not here, so they still glow far away. Remove an entry here (and from "
                + "Distant Horizons' own list) to bring it back.")
                .defineListAllowEmpty("distant_horizons_hidden_blocks", GLOW_DECORATIONS, () -> "skyseam:",
                        id -> id instanceof String text && ResourceLocation.tryParse(text) != null);
        b.pop();
        SPEC = b.build();
    }

    private SkyseamClientConfig() {}
}
