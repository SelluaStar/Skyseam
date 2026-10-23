package com.selluastar.skyseam.client.halcyon.tide;

import com.selluastar.skyseam.config.SkyseamConfig;
import com.selluastar.skyseam.halcyon.tide.Tide;
import com.selluastar.skyseam.halcyon.tide.TideEffects;
import com.selluastar.skyseam.halcyon.tide.TideSchedule;
import com.selluastar.skyseam.halcyon.tide.Tides;
import com.selluastar.skyseam.network.TidePayload;
import com.selluastar.skyseam.registry.SkyseamParticles;
import com.selluastar.skyseam.world.HalcyonLayout;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * The Tides on the client (docs/DECISIONS.md K111): the forecast the server sends ({@link TidePayload}), and what each
 * Tide looks like round the camera in the Halcyon, coming in and dying away with it.
 *
 * <ul>
 * <li>The sky leans towards each Tide's colour ({@link #tint}): pale glass-blue in a Prismfall, grey in a Gale, warm in
 * a Bloom, a dim violet in a Stillness.</li>
 * <li>Prismfall: slivers of glass fall from the open sky round the camera, glinting ({@code shard_glint}).</li>
 * <li>Bloom: pollen drifts on the air ({@code pollen}).</li>
 * <li>Gale: streaks of wind race past on the crosswind.</li>
 * <li>Stillness: sound deepens (the Veil's muffle lowers its pitch; client/halcyon/VeilMuffle).</li>
 * </ul>
 */
public final class ClientTides {
    /** How far round the camera the shards fall, the pollen drifts and the wind streaks, in blocks. */
    private static final int SHARD_SPREAD = 20;
    private static final int POLLEN_SPREAD = 14;
    private static final int GALE_SPREAD = 22;
    /** How much each Tide tints the sky at its height, and how much it dims it. */
    private static final float[] TINT = {0, 0.55f, 0.6f, 0.5f, 0.7f};
    private static final float[] DIM = {0, 0.04f, 0.24f, 0, 0.4f};
    /** How much each Tide closes in the fog at its height (share of the view lost). */
    private static final float[] FOG = {0, 0.25f, 0.5f, 0.35f, 0.7f};

    private static TideSchedule.Forecast forecast = TideSchedule.Forecast.CALM;

    private ClientTides() {}

    public static void accept(TidePayload payload) {
        forecast = payload.forecast();
    }

    public static TideSchedule.Forecast forecast() {
        return forecast;
    }

    public static void clear() {
        forecast = TideSchedule.Forecast.CALM;
    }

    /** How strongly a Tide runs here now (0 outside the Halcyon), between ticks. */
    public static float strength(Tide tide, float partialTick) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || level.dimension() != HalcyonLayout.LEVEL) {
            return 0;
        }
        return (float) forecast.strength(tide, level.getGameTime() + partialTick);
    }

    /** How far the view reaches now, as a share of the usual: the Tide running closes the fog in. */
    public static float fogFactor(float partialTick) {
        float factor = 1;
        for (Tide tide : Tide.values()) {
            factor -= FOG[tide.ordinal()] * strength(tide, partialTick);
        }
        return Math.max(0.25f, factor);
    }

    /** A sky colour leaning towards the Tide running, and dimmed as it asks. */
    public static float[] tint(float[] colour) {
        float[] out = colour;
        for (Tide tide : Tide.values()) {
            if (tide == Tide.CALM) {
                continue;
            }
            float s = strength(tide, Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false));
            if (s <= 0) {
                continue;
            }
            int rgb = tide.colour();
            float r = ((rgb >> 16) & 255) / 255f;
            float g = ((rgb >> 8) & 255) / 255f;
            float b = (rgb & 255) / 255f;
            // Towards the Tide's colour at the sky's own brightness, so night stays night.
            float bright = Math.max(out[0], Math.max(out[1], out[2]));
            float top = Math.max(r, Math.max(g, b));
            float k = TINT[tide.ordinal()] * s;
            float dim = 1 - DIM[tide.ordinal()] * s;
            out = new float[] {Mth.lerp(k, out[0], r / top * bright) * dim, Mth.lerp(k, out[1], g / top * bright) * dim,
                    Mth.lerp(k, out[2], b / top * bright) * dim};
        }
        return out;
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.isPaused() || level.dimension() != HalcyonLayout.LEVEL) {
            return;
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        RandomSource random = level.random;
        float prismfall = strength(Tide.PRISMFALL, 0);
        for (int k = count(26 * prismfall, random); k > 0; k--) {
            double x = camera.x + (random.nextDouble() * 2 - 1) * SHARD_SPREAD;
            double z = camera.z + (random.nextDouble() * 2 - 1) * SHARD_SPREAD;
            double y = camera.y + 4 + random.nextDouble() * 18;
            // Only from the open sky.
            if (y > level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z))) {
                level.addParticle(SkyseamParticles.SHARD_GLINT.get(), x, y, z, 0.04 + random.nextDouble() * 0.04, -0.75 - random.nextDouble() * 0.3,
                        0.02 + random.nextDouble() * 0.03);
            }
        }
        float bloom = strength(Tide.BLOOM, 0);
        for (int k = count(8 * bloom, random); k > 0; k--) {
            level.addParticle(SkyseamParticles.POLLEN.get(), camera.x + (random.nextDouble() * 2 - 1) * POLLEN_SPREAD,
                    camera.y + (random.nextDouble() * 2 - 1) * 7, camera.z + (random.nextDouble() * 2 - 1) * POLLEN_SPREAD,
                    (random.nextDouble() - 0.5) * 0.03, 0.004 + random.nextDouble() * 0.01, (random.nextDouble() - 0.5) * 0.03);
        }
        float gale = strength(Tide.GALE, 0);
        LocalPlayer player = minecraft.player;
        if (gale > 0 && player != null && player.onGround() && !player.getAbilities().flying && !player.isSpectator()
                && level.getHeight(Heightmap.Types.MOTION_BLOCKING, player.getBlockX(), player.getBlockZ()) <= Mth.floor(player.getEyeY()) + 1) {
            // The Gale leans on you on your feet, out under the sky (others it leans on are the server's: TideEffects).
            Vec3 wind = Tides.crosswind(level.getGameTime(), gale, crosswindSetting());
            player.addDeltaMovement(wind.normalize().scale(TideEffects.GROUND_PUSH * wind.length() / 9));
        }
        if (gale > 0) {
            Vec3 wind = Tides.crosswind(level.getGameTime(), gale, Math.max(4, crosswindSetting()));
            Vec3 streak = wind.scale(2.2 / 20);
            for (int k = count(16 * gale, random); k > 0; k--) {
                level.addParticle(SkyseamParticles.WIND_MOTE.get(), camera.x + (random.nextDouble() * 2 - 1) * GALE_SPREAD - streak.x * 30,
                        camera.y + (random.nextDouble() * 2 - 1) * 9, camera.z + (random.nextDouble() * 2 - 1) * GALE_SPREAD - streak.z * 30,
                        streak.x, (random.nextDouble() - 0.5) * 0.04, streak.z);
            }
        }
    }

    /** The Gale's crosswind in the settings (sent by the server), blocks a second. */
    private static double crosswindSetting() {
        try {
            return SkyseamConfig.TIDES_GALE_CROSSWIND.get();
        } catch (IllegalStateException notLoaded) {
            return SkyseamConfig.TIDES_GALE_CROSSWIND.getDefault();
        }
    }

    /** A whole number of particles averaging {@code mean} a tick. */
    private static int count(float mean, RandomSource random) {
        int whole = (int) mean;
        return whole + (random.nextFloat() < mean - whole ? 1 : 0);
    }
}
