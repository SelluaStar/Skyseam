package com.selluastar.skyseam.client.halcyon;

import java.util.Random;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.halcyon.LanternSun;
import com.selluastar.skyseam.halcyon.Veil;
import com.selluastar.skyseam.world.HalcyonLayout;
import com.selluastar.skyseam.world.halcyon.HalcyonRegion;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The Halcyon's sky (spec section 7, "Sky and light"), drawn in place of the vanilla sky:
 *
 * <ul>
 * <li>A pastel gradient that changes with the Lantern-Sun's phase: Dawn-Glass peach and lavender, Noon-Bloom bright
 * lilac, Dusk-Prism bands of pink, orange and teal, and the deep blue-violet Hush.</li>
 * <li>A faint ring on the horizon, the inner wall of the pocket.</li>
 * <li>In the Hush, stars and slow aurora ribbons.</li>
 * <li>The Lantern-Sun itself, drawn where it really is, circling the Spindle at Y 360, dimming to an ember in the
 * Hush.</li>
 * <li>Below the horizon, the fog colour down to where the rendered sea ends, so fogged water meets the sky without a
 * seam; further down, the Mirror Sea, the sky mirrored in teal water.</li>
 * <li>Slow cloud banks from one cloud texture, high under the lid.</li>
 * <li>Over the Hush ring, a standing dusk whatever the phase (spec: "dim blue-violet, stars and aurora").</li>
 * <li>In the Veil, everything washes out towards the pearl fog.</li>
 * </ul>
 *
 * The light engine always sees noon ({@code fixed_time} 6000); the darkness of the Hush is the lightmap dimmed here.
 * Everything is vanilla shaders and blending, so it stays shader-pack friendly. [FIRST PASS] look.
 */
public final class HalcyonSky extends DimensionSpecialEffects {
    public static final ResourceLocation ID = Skyseam.id("halcyon");
    private static final ResourceLocation SUN = Skyseam.id("textures/environment/lantern_sun.png");
    private static final ResourceLocation CLOUDS = Skyseam.id("textures/environment/halcyon_clouds.png");

    private static final float DOME = 100;
    /** How deep the Hush ring's standing dusk is, 0 to 1. */
    private static final float HUSH_DUSK = 0.75f;
    /** Below the sea's rendered edge, the fog gives way to the mirrored sea over this many degrees. */
    private static final float MIRROR_FADE = 25;
    /** How much of the haze is the Mirror Sea's colour when high above it. */
    private static final float SEA_HAZE = 0.6f;
    /** The orb's real radius, in blocks; it is drawn at least {@link #MIN_SUN_DEGREES} across so it reads from anywhere. */
    private static final float SUN_RADIUS = 16;
    private static final float MIN_SUN_DEGREES = 2.5f;
    private static final int SEGMENTS = 32;
    private static final int STARS = 700;
    private static final float CLOUD_HIGH = 384;

    /** Sky colours per phase, from zenith down to the horizon: zenith, high, middle, low, horizon. */
    private static final float[][][] PALETTES = {
            {{0.42f, 0.40f, 0.72f}, {0.62f, 0.55f, 0.82f}, {0.86f, 0.70f, 0.86f}, {1.00f, 0.80f, 0.78f}, {1.00f, 0.89f, 0.81f}},
            {{0.50f, 0.52f, 0.88f}, {0.64f, 0.62f, 0.92f}, {0.82f, 0.75f, 0.94f}, {0.98f, 0.87f, 0.91f}, {1.00f, 0.94f, 0.89f}},
            {{0.30f, 0.27f, 0.62f}, {0.56f, 0.40f, 0.72f}, {1.00f, 0.66f, 0.80f}, {1.00f, 0.76f, 0.60f}, {0.64f, 0.90f, 0.86f}},
            {{0.05f, 0.05f, 0.15f}, {0.09f, 0.08f, 0.23f}, {0.15f, 0.13f, 0.33f}, {0.23f, 0.19f, 0.43f}, {0.31f, 0.27f, 0.52f}},
    };

    /** The Mirror Sea's teal, blended into the mirrored sky below the horizon. */
    private static final float[] SEA = {0.44f, 0.75f, 0.77f};

    private final float[][] stars = new float[STARS][4];

    public HalcyonSky() {
        super(CLOUD_HIGH, true, SkyType.NORMAL, false, false);
        Random random = new Random(1508);
        for (float[] star : stars) {
            // Uniform on the upper part of the sphere, a few slightly below the horizon for the haze.
            double z = random.nextDouble() * 1.1 - 0.1;
            double angle = random.nextDouble() * Math.PI * 2;
            double ring = Math.sqrt(Math.max(0, 1 - z * z));
            star[0] = (float) (Math.cos(angle) * ring);
            star[1] = (float) z;
            star[2] = (float) (Math.sin(angle) * ring);
            star[3] = 0.22f + random.nextFloat() * random.nextFloat() * 0.6f;
        }
    }

    // ---- What the phase looks like ---------------------------------------------------------------------------------

    /**
     * The sky colour above the horizon at an elevation (degrees) for the clock, blended across phase changes and
     * towards the Hush's palette by {@code hush} (the Hush ring's standing dusk).
     */
    static float[] skyColour(double clock, float elevation, float hush) {
        int phase = (int) (Math.floorMod((long) clock, LanternSun.CYCLE_TICKS) / LanternSun.PHASE_TICKS);
        float progress = LanternSun.phaseProgress((long) clock);
        float blend = smooth(Mth.clamp((progress - 0.75f) / 0.25f, 0, 1));
        float[] colour = mix(paletteAt(PALETTES[phase], elevation), paletteAt(PALETTES[(phase + 1) % 4], elevation), blend);
        return hush > 0 ? mix(colour, paletteAt(PALETTES[3], elevation), hush) : colour;
    }

    /** The Mirror Sea far below the horizon: the sky above mirrored in teal water, a little darker as it nears. */
    private static float[] mirror(double clock, float elevation, float hush) {
        float[] mirrored = skyColour(clock, Math.min(90, -elevation), hush);
        float depth = Mth.clamp(-elevation / 40, 0, 1);
        float[] water = mix(mirrored, SEA, 0.35f + 0.25f * depth);
        return new float[] {water[0] * (1 - 0.15f * depth), water[1] * (1 - 0.1f * depth), water[2] * (1 - 0.08f * depth)};
    }

    /**
     * How much the haze takes the Mirror Sea's colour where the camera is: none near the water, {@link #SEA_HAZE} high
     * above it, as haze over open water does. From the air the far sea, all haze, then still reads as sea.
     */
    static float seaHaze(Vec3 camera) {
        double above = camera.y - HalcyonLayout.SEA_LEVEL;
        return SEA_HAZE * smooth((float) Mth.clamp((above - 30) / 120, 0, 1));
    }

    /** The far Mirror Sea's colour, as seen 20 degrees down. */
    static float[] farSea(double clock, float hush) {
        return mirror(clock, -20, hush);
    }

    /** How dark the phase is, 0 in daylight and 1 in the deep of the Hush. */
    static float night(double clock) {
        return Mth.clamp(1 - LanternSun.brightness(clock) * 1.6f, 0, 1);
    }

    /** The Hush ring's standing dusk where the camera is: 0 outside the ring, up to {@link #HUSH_DUSK} inside it. */
    static float hush(Vec3 camera) {
        return HUSH_DUSK * (float) HalcyonRegion.hushness(camera.x, camera.z);
    }

    /** How dark it is for someone here: the phase's darkness or the Hush ring's dusk, whichever is deeper. */
    static float night(double clock, Vec3 camera) {
        return Math.max(night(clock), hush(camera));
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

    private static float smooth(float t) {
        return t * t * (3 - 2 * t);
    }

    // ---- Drawing ---------------------------------------------------------------------------------------------------

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, Matrix4f modelView, Camera camera, Matrix4f projection,
            boolean foggy, Runnable setupFog) {
        double clock = ClientLanternSun.clock(level, partialTick);
        Vec3 at = camera.getPosition();
        Vec3 sunDirection = LanternSun.position(clock).subtract(at).normalize();
        float hush = hush(at);
        // The Lantern-Sun's light as seen from here: dimmer under the Hush's dusk.
        float brightness = LanternSun.brightness(clock) * (1 - 0.6f * hush);
        float veil = Veil.thickness(at.x, at.y, at.z);
        float clear = 1 - veil;
        float night = night(clock, at) * clear;
        PoseStack pose = new PoseStack();
        pose.mulPose(modelView);
        Matrix4f matrix = pose.last().pose();

        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        drawDome(matrix, clock, sunDirection, brightness, hush, veil, seaEdge(at));

        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        drawHorizonRing(matrix, night, clear);
        if (night > 0.01f) {
            drawStars(matrix, night, ticks + partialTick);
            drawAurora(matrix, night, ticks + partialTick);
        }
        if (clear > 0.02f) {
            drawSun(matrix, sunDirection, at.distanceTo(LanternSun.position(clock)), brightness, clear);
        }

        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        return true;
    }

    /**
     * How far below the horizon (degrees) the sea is fully fogged (the round haze of {@link HalcyonFog} closes at the
     * render distance): the dome is the fog colour from the horizon down to there, so the water fades into it.
     */
    private static float seaEdge(Vec3 camera) {
        float reach = Math.max(16, Minecraft.getInstance().gameRenderer.getRenderDistance());
        double above = camera.y - HalcyonLayout.SEA_LEVEL;
        return (float) Mth.clamp(Math.toDegrees(Math.asin(Mth.clamp(above / reach, -1, 1))), 1, 70);
    }

    private void drawDome(Matrix4f matrix, double clock, Vec3 sun, float brightness, float hush, float veil, float seaEdge) {
        float[] shaderFog = RenderSystem.getShaderFogColor();
        float[] fog = {shaderFog[0], shaderFog[1], shaderFog[2]};
        float[] elevations = {-90, -Math.min(85, seaEdge + MIRROR_FADE + 5), -Math.min(80, seaEdge + MIRROR_FADE / 2), -seaEdge, 0, 4, 12, 30, 60, 90};
        float wash = (float) Math.pow(veil, 0.7);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int band = 0; band < elevations.length - 1; band++) {
            float e0 = elevations[band];
            float e1 = elevations[band + 1];
            float[] c0 = mix(domeColour(clock, e0, hush, fog, seaEdge), fog, wash);
            float[] c1 = mix(domeColour(clock, e1, hush, fog, seaEdge), fog, wash);
            for (int s = 0; s < SEGMENTS; s++) {
                float a0 = (float) (s * Math.PI * 2 / SEGMENTS);
                float a1 = (float) ((s + 1) * Math.PI * 2 / SEGMENTS);
                domeVertex(buffer, matrix, c0, sun, brightness * (1 - wash), e0, a0);
                domeVertex(buffer, matrix, c0, sun, brightness * (1 - wash), e0, a1);
                domeVertex(buffer, matrix, c1, sun, brightness * (1 - wash), e1, a1);
                domeVertex(buffer, matrix, c1, sun, brightness * (1 - wash), e1, a0);
            }
        }
        draw(buffer);
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
        return mix(fog, mirror(clock, elevation, hush), smooth(Mth.clamp(below / MIRROR_FADE, 0, 1)));
    }

    private static void domeVertex(BufferBuilder buffer, Matrix4f matrix, float[] c, Vec3 sun, float brightness, float elevation, float azimuth) {
        float e = elevation * Mth.DEG_TO_RAD;
        float x = Mth.cos(e) * Mth.cos(azimuth);
        float y = Mth.sin(e);
        float z = Mth.cos(e) * Mth.sin(azimuth);
        // A warm glow round the Lantern-Sun, wider and softer than the orb.
        float toward = (float) Math.max(0, x * sun.x + y * sun.y + z * sun.z);
        float glow = (float) Math.pow(toward, 6) * 0.35f * (0.3f + 0.7f * brightness);
        buffer.addVertex(matrix, x * DOME, y * DOME, z * DOME).setColor(Math.min(1, c[0] + glow), Math.min(1, c[1] + glow * 0.82f),
                Math.min(1, c[2] + glow * 0.6f), 1);
    }

    /** The pocket's inner wall: a thin pale band right on the horizon, brighter in the Hush, gone in the Veil's fog. */
    private static void drawHorizonRing(Matrix4f matrix, float night, float clear) {
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float alpha = (0.08f + 0.06f * night) * clear;
        for (int s = 0; s < SEGMENTS * 2; s++) {
            float a0 = (float) (s * Math.PI / SEGMENTS);
            float a1 = (float) ((s + 1) * Math.PI / SEGMENTS);
            for (int half = 0; half < 2; half++) {
                float low = half == 0 ? -2.5f : 0.6f;
                float high = half == 0 ? 0.6f : 5.0f;
                float aLow = half == 0 ? 0 : alpha;
                float aHigh = half == 0 ? alpha : 0;
                ringVertex(buffer, matrix, low, a0, aLow);
                ringVertex(buffer, matrix, low, a1, aLow);
                ringVertex(buffer, matrix, high, a1, aHigh);
                ringVertex(buffer, matrix, high, a0, aHigh);
            }
        }
        draw(buffer);
    }

    private static void ringVertex(BufferBuilder buffer, Matrix4f matrix, float elevation, float azimuth, float alpha) {
        float e = elevation * Mth.DEG_TO_RAD;
        buffer.addVertex(matrix, Mth.cos(e) * Mth.cos(azimuth) * DOME * 0.98f, Mth.sin(e) * DOME * 0.98f, Mth.cos(e) * Mth.sin(azimuth) * DOME * 0.98f)
                .setColor(0.97f, 0.95f, 1f, alpha);
    }

    private void drawStars(Matrix4f matrix, float night, float time) {
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Vector3f up = new Vector3f();
        Vector3f side = new Vector3f();
        for (int k = 0; k < stars.length; k++) {
            float[] star = stars[k];
            Vector3f at = new Vector3f(star[0], star[1], star[2]).mul(DOME * 0.97f);
            new Vector3f(star[0], star[1], star[2]).cross(0, 1, 0.01f, side).normalize();
            new Vector3f(star[0], star[1], star[2]).cross(side, up).normalize();
            float twinkle = 0.75f + 0.25f * Mth.sin(time * 0.05f + k * 1.7f);
            float size = star[3];
            float alpha = night * twinkle * Mth.clamp(star[1] * 6 + 0.4f, 0, 1);
            float r = k % 7 == 0 ? 1f : 0.9f;
            float b = k % 5 == 0 ? 0.85f : 1f;
            buffer.addVertex(matrix, at.x - side.x * size - up.x * size, at.y - side.y * size - up.y * size, at.z - side.z * size - up.z * size)
                    .setColor(r, 0.95f, b, alpha);
            buffer.addVertex(matrix, at.x + side.x * size - up.x * size, at.y + side.y * size - up.y * size, at.z + side.z * size - up.z * size)
                    .setColor(r, 0.95f, b, alpha);
            buffer.addVertex(matrix, at.x + side.x * size + up.x * size, at.y + side.y * size + up.y * size, at.z + side.z * size + up.z * size)
                    .setColor(r, 0.95f, b, alpha);
            buffer.addVertex(matrix, at.x - side.x * size + up.x * size, at.y - side.y * size + up.y * size, at.z - side.z * size + up.z * size)
                    .setColor(r, 0.95f, b, alpha);
        }
        draw(buffer);
    }

    /** Aurora ribbons for the Hush: three slow curtains of teal fading to pink, swaying with time. */
    private static void drawAurora(Matrix4f matrix, float night, float time) {
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int steps = 48;
        for (int ribbon = 0; ribbon < 3; ribbon++) {
            float start = ribbon * 2.1f + 0.4f;
            float span = 1.9f;
            for (int s = 0; s < steps; s++) {
                float t0 = (float) s / steps;
                float t1 = (float) (s + 1) / steps;
                auroraQuad(buffer, matrix, ribbon, start, span, t0, t1, night, time);
            }
        }
        draw(buffer);
    }

    private static void auroraQuad(BufferBuilder buffer, Matrix4f matrix, int ribbon, float start, float span, float t0, float t1, float night,
            float time) {
        float[][] points = new float[4][];
        float[] ts = {t0, t1, t1, t0};
        boolean[] top = {false, false, true, true};
        for (int k = 0; k < 4; k++) {
            float t = ts[k];
            float azimuth = start + span * t;
            float base = 34 + ribbon * 7 + 6 * Mth.sin(azimuth * 3 + time * 0.004f + ribbon);
            float elevation = (base + (top[k] ? 14 : 0)) * Mth.DEG_TO_RAD;
            float sway = 0.06f * Mth.sin(t * 9 + time * 0.01f + ribbon * 2);
            points[k] = new float[] {Mth.cos(elevation) * Mth.cos(azimuth + sway) * DOME * 0.96f, Mth.sin(elevation) * DOME * 0.96f,
                    Mth.cos(elevation) * Mth.sin(azimuth + sway) * DOME * 0.96f};
            float ends = Mth.sin(t * Mth.PI);
            float alpha = night * 0.32f * ends * (top[k] ? 0 : 1);
            float r = top[k] ? 1f : 0.45f;
            float g = top[k] ? 0.6f : 0.95f;
            float b = top[k] ? 0.85f : 0.85f;
            buffer.addVertex(matrix, points[k][0], points[k][1], points[k][2]).setColor(r, g, b, alpha);
        }
    }

    /** The Lantern-Sun: a textured glow facing the camera, in the orb's real direction. */
    private static void drawSun(Matrix4f matrix, Vec3 direction, double distance, float brightness, float clear) {
        float degrees = Math.max(MIN_SUN_DEGREES, (float) Math.toDegrees(Math.atan(SUN_RADIUS / Math.max(1, distance))));
        // The texture's bright core is 9 of its 32 half-width pixels.
        float half = DOME * Mth.sin(degrees * Mth.DEG_TO_RAD) * 32 / 9f * (0.75f + 0.25f * brightness);
        Vector3f at = new Vector3f((float) direction.x, (float) direction.y, (float) direction.z).mul(DOME * 0.9f);
        Vector3f side = new Vector3f((float) direction.x, (float) direction.y, (float) direction.z).cross(0, 1, 0.001f).normalize().mul(half);
        Vector3f up = new Vector3f((float) direction.x, (float) direction.y, (float) direction.z).cross(side).normalize().mul(half);
        // Noon: white-gold; the Hush: a small orange ember.
        float r = 1f;
        float g = Mth.lerp(brightness, 0.55f, 0.97f);
        float b = Mth.lerp(brightness, 0.35f, 0.9f);
        float alpha = (0.55f + 0.45f * brightness) * clear;
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, SUN);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.addVertex(matrix, at.x - side.x - up.x, at.y - side.y - up.y, at.z - side.z - up.z).setUv(0, 0).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, at.x + side.x - up.x, at.y + side.y - up.y, at.z + side.z - up.z).setUv(1, 0).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, at.x + side.x + up.x, at.y + side.y + up.y, at.z + side.z + up.z).setUv(1, 1).setColor(r, g, b, alpha);
        buffer.addVertex(matrix, at.x - side.x + up.x, at.y - side.y + up.y, at.z - side.z + up.z).setUv(0, 1).setColor(r, g, b, alpha);
        draw(buffer);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
    }

    @Override
    public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, double camX, double camY, double camZ,
            Matrix4f modelView, Matrix4f projection) {
        double clock = ClientLanternSun.clock(level, partialTick);
        Vec3 at = new Vec3(camX, camY, camZ);
        float light = 0.22f + 0.78f * LanternSun.brightness(clock) * (1 - hush(at));
        float opacity = 0.8f * (1 - Veil.thickness(camX, camY, camZ));
        if (opacity <= 0.01f) {
            return true;
        }
        float time = ticks + partialTick;
        PoseStack pose = new PoseStack();
        pose.mulPose(modelView);
        Matrix4f matrix = pose.last().pose();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, CLOUDS);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        cloudLayer(buffer, matrix, camX, camY, camZ, CLOUD_HIGH, 900, opacity, light, time * 0.0006f);
        draw(buffer);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        return true;
    }

    /** One layer of cloud: a grid round the camera, the texture fixed to the world and drifting, fading out with distance. */
    private static void cloudLayer(BufferBuilder buffer, Matrix4f matrix, double camX, double camY, double camZ, float height, float reach,
            float opacity, float light, float drift) {
        int cells = 12;
        float cell = reach * 2 / cells;
        double baseX = Math.floor(camX / cell) * cell - reach;
        double baseZ = Math.floor(camZ / cell) * cell - reach;
        float y = (float) (height - camY);
        for (int i = 0; i < cells; i++) {
            for (int j = 0; j < cells; j++) {
                double x0 = baseX + i * cell;
                double z0 = baseZ + j * cell;
                cloudVertex(buffer, matrix, x0, y, z0, camX, camZ, reach, opacity, light, drift);
                cloudVertex(buffer, matrix, x0 + cell, y, z0, camX, camZ, reach, opacity, light, drift);
                cloudVertex(buffer, matrix, x0 + cell, y, z0 + cell, camX, camZ, reach, opacity, light, drift);
                cloudVertex(buffer, matrix, x0, y, z0 + cell, camX, camZ, reach, opacity, light, drift);
            }
        }
    }

    private static void cloudVertex(BufferBuilder buffer, Matrix4f matrix, double x, float y, double z, double camX, double camZ, float reach,
            float opacity, float light, float drift) {
        double d = Math.hypot(x - camX, z - camZ) / reach;
        float alpha = opacity * (float) Mth.clamp(1 - d * d, 0, 1);
        buffer.addVertex(matrix, (float) (x - camX), y, (float) (z - camZ)).setUv((float) (x / 700.0 + drift), (float) (z / 700.0))
                .setColor(light, light * 0.97f, Math.min(1, light * 1.08f), alpha);
    }

    private static void draw(BufferBuilder buffer) {
        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }

    /** The Hush: the sky's share of every light level dims, and the dark leans blue. */
    @Override
    public void adjustLightmapColors(ClientLevel level, float partialTick, float skyDarken, float blockLightFlicker, float skyLight, int pixelX,
            int pixelY, Vector3f colours) {
        float night = night(ClientLanternSun.clock(level, partialTick), Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
        if (night <= 0) {
            return;
        }
        float skyShare = pixelY / 15f * (1 - pixelX / 15f * 0.7f);
        float dim = 1 - 0.6f * night * skyShare;
        colours.set(colours.x * dim * (1 - 0.08f * night), colours.y * dim * (1 - 0.04f * night), colours.z * Math.min(1, dim * (1 + 0.06f * night)));
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
