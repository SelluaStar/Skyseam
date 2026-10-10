package com.selluastar.skyseam.client.halcyon;

import com.mojang.blaze3d.shaders.FogShape;
import com.selluastar.skyseam.halcyon.Veil;
import com.selluastar.skyseam.world.HalcyonLayout;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * The Halcyon's fog (spec section 7): mostly the sky's own horizon colour for the phase, so fogged islands and sea fade
 * into the sky without a seam, with a share of the biome's fog colour (which vanilla blends) by day; high above the
 * Mirror Sea it leans to the sea's teal, so the far sea, all haze from the air, still reads as sea. The haze is round
 * and starts a little nearer than vanilla's, so the sea seen from high above fades away softly instead of ending in a
 * hard-edged disc. In the Veil it thickens and washes out to pearl white, so the world's edge reads as a soft glow
 * rather than a wall. Underwater and blindness fog are left as vanilla makes them.
 */
public final class HalcyonFog {
    private static final float[] PEARL = {0.97f, 0.95f, 1f};
    /** How much of the daytime fog colour is the sky's horizon; the rest is the biome's fog colour. */
    private static final float HORIZON_SHARE = 0.7f;
    /** How close the fog closes in at the rim, in blocks. */
    private static final float VEIL_FAR = 28;
    /** Where the haze starts, as a share of the render distance (vanilla: 0.9). */
    private static final float HAZE_START = 0.8f;

    private HalcyonFog() {}

    private static boolean inHalcyon() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level != null && minecraft.level.dimension() == HalcyonLayout.LEVEL;
    }

    public static void onFogColour(ViewportEvent.ComputeFogColor event) {
        if (!inHalcyon() || event.getCamera().getFluidInCamera() != FogType.NONE || isBlinded(event.getCamera())) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        double clock = ClientLanternSun.clock(minecraft.level, (float) event.getPartialTick());
        Vec3 at = event.getCamera().getPosition();
        float[] horizon = HalcyonSky.skyColour(clock, 0, HalcyonSky.hush(at));
        // By night the fog is all sky: the biome colours are daylight colours.
        float share = Mth.lerp(HalcyonSky.night(clock, at), HORIZON_SHARE, 1);
        float r = Mth.lerp(share, event.getRed(), horizon[0]);
        float g = Mth.lerp(share, event.getGreen(), horizon[1]);
        float b = Mth.lerp(share, event.getBlue(), horizon[2]);
        float overSea = HalcyonSky.seaHaze(at);
        if (overSea > 0) {
            float[] sea = HalcyonSky.farSea(clock, HalcyonSky.hush(at));
            r = Mth.lerp(overSea, r, sea[0]);
            g = Mth.lerp(overSea, g, sea[1]);
            b = Mth.lerp(overSea, b, sea[2]);
        }
        float veil = Veil.thickness(at.x, at.y, at.z);
        event.setRed(Mth.lerp(veil, r, PEARL[0]));
        event.setGreen(Mth.lerp(veil, g, PEARL[1]));
        event.setBlue(Mth.lerp(veil, b, PEARL[2]));
    }

    private static boolean isBlinded(Camera camera) {
        return camera.getEntity() instanceof LivingEntity living && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS));
    }

    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (!inHalcyon() || event.getType() != FogType.NONE || event.getMode() != FogRenderer.FogMode.FOG_TERRAIN) {
            return;
        }
        Vec3 at = event.getCamera().getPosition();
        float veil = Veil.thickness(at.x, at.y, at.z);
        float far = event.getFarPlaneDistance();
        event.setFogShape(FogShape.SPHERE);
        event.setFarPlaneDistance(Mth.lerp(veil * veil, far, VEIL_FAR));
        event.setNearPlaneDistance(Mth.lerp(veil, far * HAZE_START, 0));
        // NeoForge only uses the new distances when the event is cancelled.
        event.setCanceled(true);
    }
}
