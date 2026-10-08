package com.selluastar.skyseam.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client config ({@code config/skyseam-client.toml}): how strong the screen effects are for this player. */
public final class SkyseamClientConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue CAMERA_SHAKE;
    public static final ModConfigSpec.DoubleValue SCREEN_FLASH;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("effects");
        CAMERA_SHAKE = b.comment("Strength of the camera shake when a Seam opens near you. 0 turns it off.")
                .defineInRange("camera_shake", 1.0, 0.0, 1.0);
        SCREEN_FLASH = b.comment("Strength of the white flash when your ship crosses a Seam. 0 turns it off.")
                .defineInRange("screen_flash", 1.0, 0.0, 1.0);
        b.pop();
        SPEC = b.build();
    }

    private SkyseamClientConfig() {}
}
