package com.selluastar.skyseam.client.halcyon;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.client.halcyon.tide.ClientTides;
import com.selluastar.skyseam.client.halcyon.tide.TideWeather;
import com.selluastar.skyseam.client.halcyon.sky.Atmosphere;
import com.selluastar.skyseam.client.halcyon.sky.SkyBodies;
import com.selluastar.skyseam.client.halcyon.sky.SkyDraw;
import com.selluastar.skyseam.client.halcyon.sky.StarField;
import com.selluastar.skyseam.halcyon.LanternSun;
import com.selluastar.skyseam.halcyon.Veil;
import com.selluastar.skyseam.world.HalcyonLayout;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The Halcyon's sky (spec section 7, "Sky and light"), rebuilt in round two (docs/DECISIONS.md K86), drawn in place
 * of vanilla's:
 *
 * <ul>
 * <li>A gradient for each phase of the Lantern-Sun's day: Dawn-Glass's lavender over peach, Noon-Bloom's clear blue
 * (images 9 and 10), Dusk-Prism's indigo over a rose and orange horizon (image 6), and the Hush's deep teal-navy over a
 * violet glow (images 5 and 14), with a warm glow round the sun.</li>
 * <li>Behind everything, by night: two drifting nebulae, sparkling stars that wheel with the sky, shooting stars, and
 * sky lanterns drifting low ({@link StarField}).</li>
 * <li>High cloud swirled over the zenith (image 14), and the Hush's aurora ({@link Atmosphere}).</li>
 * <li>The sky's bodies, all moving with the time ({@link SkyBodies}): by day the Lantern-Sun, its Ember companion
 * and the ringed planet Veyra; by night four moons.</li>
 * <li>Below the horizon, the fog colour down to where the rendered sea ends, then the sky mirrored in the deep blue sea.</li>
 * <li>Real blocky clouds in three decks ({@link HalcyonClouds}).</li>
 * <li>Over the Hush, a standing dusk whatever the phase.</li>
 * </ul>
 *
 * The world's day follows the Lantern-Sun (mixin/LevelTimeMixin), so the land darkens at night by itself and shader
 * packs light it from the Lantern-Sun; this adds moonlight and twilight tints to the light. Everything is drawn with
 * vanilla's shaders inside the sky and cloud hooks, so a shader pack routes it through its own sky programs, and drawn
 * so it survives them ({@link SkyDraw}): under a shader pack the pack paints the sky's colour, and everything in it
 * keeps its look.
 */
public final class HalcyonSky extends DimensionSpecialEffects {
    public static final ResourceLocation ID = Skyseam.id("halcyon");

    /** How deep the Hush ring's standing dusk is, 0 to 1. */
    private static final float HUSH_DUSK = 0.75f;
    /** Below the sea's rendered edge, the fog gives way to the mirrored sea over this many degrees. */
    private static final float MIRROR_FADE = 25;
    /** How much of the haze is the sea's colour when high above it. */
    private static final float SEA_HAZE = 0.6f;
    private static final int SEGMENTS = 32;
    private static final float CLOUD_HIGH = 384;

    /**
     * Sky colours per phase (Dawn-Glass, Noon-Bloom, Dusk-Prism, the Hush), from zenith down to the horizon: zenith,
     * high, middle, low, horizon. From the author's reference images: noon from images 9 and 10, dusk from image 6,
     * the Hush from images 5 and 14.
     */
    private static final float[][][] PALETTES = {
            {rgb(0x2E3A78), rgb(0x5A5A9C), rgb(0xA07AB0), rgb(0xE8A0A0), rgb(0xFFD0A0)},
            {rgb(0x3E78C8), rgb(0x5E92D6), rgb(0x8DB2E2), rgb(0xB8CDEA), rgb(0xDCE4F0)},
            {rgb(0x1A1C48), rgb(0x2C2E6A), rgb(0x4A3A86), rgb(0x9A5A96), rgb(0xF09A7A)},
            {rgb(0x0E2236), rgb(0x172C4E), rgb(0x2A2F66), rgb(0x563C80), rgb(0x9A5A96)},
    };

    /** The sea's deep blue, blended into the mirrored sky below the horizon. */
    private static final float[] SEA = {0.16f, 0.30f, 0.62f};

    public HalcyonSky() {
        super(CLOUD_HIGH, true, SkyType.NORMAL, false, false);
    }

    private static float[] rgb(int colour) {
        return new float[] {((colour >> 16) & 255) / 255f, ((colour >> 8) & 255) / 255f, (colour & 255) / 255f};
    }

    // ---- What the phase looks like ---------------------------------------------------------------------------------

    /**
     * The sky colour above the horizon at an elevation (degrees) for the clock, blended across phase changes and
     * towards the Hush's palette by {@code hush} (the Hush ring's standing dusk), and tinted by the Tide running.
     */
    static float[] skyColour(double clock, float elevation, float hush) {
        int phase = (int) (Math.floorMod((long) clock, LanternSun.CYCLE_TICKS) / LanternSun.PHASE_TICKS);
        float progress = LanternSun.phaseProgress((long) clock);
        float blend = SkyDraw.smooth(Mth.clamp((progress - 0.75f) / 0.25f, 0, 1));
        float[] colour = mix(paletteAt(PALETTES[phase], elevation), paletteAt(PALETTES[(phase + 1) % 4], elevation), blend);
        // And towards the Tide running (docs/DECISIONS.md K111).
        return ClientTides.tint(hush > 0 ? mix(colour, paletteAt(PALETTES[3], elevation), hush) : colour);
    }

    /** The mirrored sea far below the horizon: the sky above mirrored in deep blue water, a little darker as it nears. */
    private static float[] mirror(double clock, float elevation, float hush) {
        float[] mirrored = skyColour(clock, Math.min(90, -elevation), hush);
        float depth = Mth.clamp(-elevation / 40, 0, 1);
        float[] water = mix(mirrored, SEA, 0.35f + 0.25f * depth);
        return new float[] {water[0] * (1 - 0.15f * depth), water[1] * (1 - 0.1f * depth), water[2] * (1 - 0.08f * depth)};
    }

    /**
     * How much the haze takes the sea's colour where the camera is: none near the water, {@link #SEA_HAZE} high above
     * it, as haze over open water does.
     */
    static float seaHaze(Vec3 camera) {
        double above = camera.y - HalcyonLayout.SEA_LEVEL;
        return SEA_HAZE * SkyDraw.smooth((float) Mth.clamp((above - 30) / 120, 0, 1));
    }

    /** The far sea's colour, as seen 20 degrees down. */
    static float[] farSea(double clock, float hush) {
        return mirror(clock, -20, hush);
    }

    /** How dark the phase is, 0 in daylight and 1 in the deep of the Hush. */
    static float night(double clock) {
        return Mth.clamp(1 - LanternSun.brightness(clock) * 1.6f, 0, 1);
    }

    /** The Hush's standing dusk where the camera is: 0 outside it, up to {@link #HUSH_DUSK} inside it. */
    static float hush(Vec3 camera) {
        return HUSH_DUSK * ClientHush.at(camera.x, camera.y, camera.z);
    }

    /** How dark it is for someone here: the phase's darkness or the Hush ring's dusk, whichever is deeper. */
    static float night(double clock, Vec3 camera) {
        return Math.max(night(clock), hush(camera));
    }

    /** How golden the hour is, 0 to 1: strongest with the sun just over the horizon, at dawn and dusk. */
    static float golden(Vector3f sun) {
        return Mth.clamp(1 - Math.abs(sun.y + 0.04f) / 0.32f, 0, 1);
    }

    private static float[] paletteAt(float[][] palette, float elevation) {
        float[] stops = {60, 30, 12, 4, 0};
        if (elevation >= stops[0]) {
            return palette[0];
        }
        for (int k = 1; k < stops.length; k++) {
            if (elevation >= stops[k]) {
                float t = (elevation - stops[k]) / (stops[k - 1] - stops[k]);
                return mix(palette[k], palette[k - 1], t);
            }
        }
        return palette[4];
    }

    private static float[] mix(float[] a, float[] b, float t) {
        return new float[] {Mth.lerp(t, a[0], b[0]), Mth.lerp(t, a[1], b[1]), Mth.lerp(t, a[2], b[2])};
    }

    // ---- Drawing ---------------------------------------------------------------------------------------------------

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelView, Camera camera, Matrix4f projection,
            boolean foggy, Runnable setupFog) {
        SkyDraw.begin();
        double clock = ClientLanternSun.clock(level, partialTick);
        float sunAngle = level.getTimeOfDay(partialTick) * Mth.TWO_PI;
        double day = (level.getGameTime() + LanternSun.offset(true) + partialTick) / (double) LanternSun.CYCLE_TICKS;
        SkyBodies.Positions bodies = SkyBodies.positions(sunAngle, day);
        Vec3 at = camera.getPosition();
        float hush = hush(at);
        float light = LanternSun.brightness(clock) * (1 - 0.6f * hush);
        float veil = Veil.thickness(at.x, at.y, at.z);
        float clear = 1 - veil;
        float night = night(clock, at) * clear;
        float golden = golden(bodies.sun());
        float time = ticks + partialTick;
        PoseStack pose = new PoseStack();
        pose.mulPose(modelView);
        Matrix4f matrix = pose.last().pose();

        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        drawDome(matrix, clock, bodies.sun(), light, hush, veil, seaEdge(at));
        if (clear > 0.02f) {
            StarField.draw(matrix, sunAngle, night, night * 0.8f * clear, time);
            // The swirl: white streaks by day, teal by night, rose at dusk.
            float[] tint = mix(mix(new float[] {1, 1, 1}, new float[] {0.45f, 0.78f, 0.82f}, night), new float[] {1, 0.72f, 0.75f}, golden * 0.6f);
            Atmosphere.swirl(matrix, time, tint[0], tint[1], tint[2], (0.14f + 0.16f * night) * clear);
            SkyBodies.drawNight(matrix, bodies, night, level.getMoonPhase(), time, clear);
            SkyBodies.drawDay(matrix, bodies, light, golden, time, clear);
            Atmosphere.aurora(matrix, night * (0.35f + 0.65f * hush / HUSH_DUSK) * clear, time);
        }
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        return true;
    }

    /**
     * How far below the horizon (degrees) the rendered sea ends (the terrain fog closes at the render distance, measured
     * flat): the dome is the fog colour from the horizon down to there, so the fogged water fades into it.
     */
    private static float seaEdge(Vec3 camera) {
        float reach = Math.max(16, Minecraft.getInstance().gameRenderer.getRenderDistance());
        double above = camera.y - HalcyonLayout.SEA_LEVEL;
        return (float) Mth.clamp(Math.toDegrees(Math.atan2(above, reach)), 1, 70);
    }

    private void drawDome(Matrix4f matrix, double clock, Vector3f sun, float brightness, float hush, float veil, float seaEdge) {
        float[] shaderFog = RenderSystem.getShaderFogColor();
        float[] fog = {shaderFog[0], shaderFog[1], shaderFog[2]};
        float[] elevations = {-90, -Math.min(85, seaEdge + MIRROR_FADE + 5), -Math.min(80, seaEdge + MIRROR_FADE / 2), -seaEdge, 0, 4, 12, 30, 60, 90};
        float wash = (float) Math.pow(veil, 0.7);
        float golden = golden(sun);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int band = 0; band < elevations.length - 1; band++) {
            float e0 = elevations[band];
            float e1 = elevations[band + 1];
            float[] c0 = mix(domeColour(clock, e0, hush, fog, seaEdge), fog, wash);
            float[] c1 = mix(domeColour(clock, e1, hush, fog, seaEdge), fog, wash);
            for (int s = 0; s < SEGMENTS; s++) {
                float a0 = (float) (s * Math.PI * 2 / SEGMENTS);
                float a1 = (float) ((s + 1) * Math.PI * 2 / SEGMENTS);
                domeVertex(buffer, matrix, c0, sun, brightness * (1 - wash), golden, e0, a0);
                domeVertex(buffer, matrix, c0, sun, brightness * (1 - wash), golden, e0, a1);
                domeVertex(buffer, matrix, c1, sun, brightness * (1 - wash), golden, e1, a1);
                domeVertex(buffer, matrix, c1, sun, brightness * (1 - wash), golden, e1, a0);
            }
        }
        SkyDraw.draw(buffer);
    }

    /**
     * The dome's colour at an elevation: the sky above, the fog colour at the horizon and down to where the rendered sea
     * ends (so the fogged water's edge vanishes into it), then the mirrored sea below that.
     */
    private static float[] domeColour(double clock, float elevation, float hush, float[] fog, float seaEdge) {
        if (elevation > 0) {
            return skyColour(clock, elevation, hush);
        }
        float below = -elevation - seaEdge;
        if (below <= 0) {
            return fog;
        }
        return mix(fog, mirror(clock, elevation, hush), SkyDraw.smooth(Mth.clamp(below / MIRROR_FADE, 0, 1)));
    }

    private static void domeVertex(BufferBuilder buffer, Matrix4f matrix, float[] c, Vector3f sun, float brightness, float golden, float elevation,
            float azimuth) {
        float e = elevation * Mth.DEG_TO_RAD;
        float x = Mth.cos(e) * Mth.cos(azimuth);
        float y = Mth.sin(e);
        float z = Mth.cos(e) * Mth.sin(azimuth);
        // A warm glow round the Lantern-Sun, wider and rosier when it is low.
        float toward = Math.max(0, x * sun.x + y * sun.y + z * sun.z);
        float glow = (float) Math.pow(toward, 6 - 3 * golden) * (0.35f + 0.25f * golden) * (0.3f + 0.7f * Math.max(brightness, golden));
        float sunUp = Mth.clamp(sun.y * 6 + 0.6f, 0, 1);
        glow *= sunUp;
        buffer.addVertex(matrix, x * SkyDraw.DOME, y * SkyDraw.DOME, z * SkyDraw.DOME).setColor(Math.min(1, c[0] + glow),
                Math.min(1, c[1] + glow * (0.82f - 0.25f * golden)), Math.min(1, c[2] + glow * (0.6f - 0.3f * golden)), 1);
    }

    /** The Tides' weather: glass, pollen or wind round the camera (docs/DECISIONS.md K113). */
    @Override
    public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick, LightTexture lightTexture, double camX, double camY,
            double camZ) {
        return TideWeather.render(level, ticks, partialTick, lightTexture, camX, camY, camZ);
    }

    @Override
    public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, double camX, double camY, double camZ,
            Matrix4f modelView, Matrix4f projection) {
        double clock = ClientLanternSun.clock(level, partialTick);
        Vec3 at = new Vec3(camX, camY, camZ);
        float opacity = 1 - Veil.thickness(camX, camY, camZ);
        if (opacity <= 0.01f) {
            return true;
        }
        // White by day (images 9 and 10), deep teal-violet with lighter tops at night (image 14), tinted by the low sky.
        float night = night(clock, at);
        float[] tint = skyColour(clock, 10, hush(at));
        float[] top = mix(mix(new float[] {1f, 1f, 1f}, new float[] {0.40f, 0.42f, 0.66f}, night), tint, 0.15f);
        float[] side = mix(mix(new float[] {0.88f, 0.9f, 0.97f}, new float[] {0.28f, 0.32f, 0.52f}, night), tint, 0.22f);
        float[] bottom = mix(mix(new float[] {0.66f, 0.7f, 0.84f}, new float[] {0.18f, 0.22f, 0.40f}, night), tint, 0.3f);
        RenderSystem.depthMask(true);
        // By night the clouds thin out, so the moons and stars show through.
        HalcyonClouds.render(modelView, projection, camX, camY, camZ, ticks + partialTick, top, side, bottom, opacity * (1 - 0.45f * night));
        return true;
    }

    /**
     * The light, on top of the Lantern-Sun's day (which darkens the night by itself): moonlight from four moons keeps
     * the Hush from going black and leans it blue-violet; dawn and dusk warm the daylight towards rose-gold; and the
     * Hush ring's standing dusk dims the sky's share of the light whatever the phase.
     */
    @Override
    public void adjustLightmapColors(ClientLevel level, float partialTick, float skyDarken, float blockLightFlicker, float skyLight, int pixelX,
            int pixelY, Vector3f colours) {
        double clock = ClientLanternSun.clock(level, partialTick);
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        float night = night(clock);
        float hush = hush(camera);
        float golden = golden(SkyDraw.path(level.getTimeOfDay(partialTick) * Mth.TWO_PI, 0));
        float sky = pixelY / 15f * (1 - pixelX / 15f * 0.6f);
        float moon = night * sky;
        colours.add(0.05f * moon, 0.055f * moon, 0.12f * moon);
        if (golden > 0) {
            float g = golden * sky * (1 - night);
            colours.mul(1 + 0.08f * g, 1 - 0.01f * g, 1 - 0.07f * g);
        }
        if (hush > 0) {
            float dim = 1 - 0.55f * hush * sky;
            colours.mul(dim * (1 - 0.06f * hush), dim * (1 - 0.03f * hush), Math.min(1, dim * (1 + 0.05f * hush)));
        }
        colours.set(Mth.clamp(colours.x, 0, 1), Mth.clamp(colours.y, 0, 1), Mth.clamp(colours.z, 0, 1));
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 fog, float brightness) {
        return fog;
    }

    @Override
    public boolean isFoggyAt(int x, int z) {
        return false;
    }

    @Override
    public float[] getSunriseColor(float timeOfDay, float partialTick) {
        return null;
    }
}
