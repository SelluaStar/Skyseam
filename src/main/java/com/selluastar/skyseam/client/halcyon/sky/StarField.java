package com.selluastar.skyseam.client.halcyon.sky;

import java.util.Random;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.world.halcyon.plan.Noise;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The Halcyon's night sky behind its moons (docs/DECISIONS.md K86), turning with the sky like vanilla's stars:
 *
 * <ul>
 * <li>Two nebula bands, violet and teal, crossing at an angle and drifting slowly against each other.</li>
 * <li>About 2,600 stars: faint dust, thicker along the nebulae, and some 400 bright ones that sparkle (four-pointed
 * glints), each twinkling at its own pace, in white, blue, gold and rose.</li>
 * <li>Shooting stars, now and then.</li>
 * <li>Drifting sky lanterns: small warm lights floating slowly round the low sky.</li>
 * </ul>
 *
 * It is all rebuilt every frame (a few thousand quads), so each star can twinkle and fade as it nears the horizon.
 */
public final class StarField {
    private static final ResourceLocation GLINT = Skyseam.id("textures/environment/star_glint.png");
    private static final ResourceLocation NEBULA = Skyseam.id("textures/environment/nebula.png");
    private static final ResourceLocation NEBULA_TEAL = Skyseam.id("textures/environment/nebula_teal.png");
    private static final ResourceLocation METEOR = Skyseam.id("textures/environment/meteor.png");
    private static final ResourceLocation HALO = Skyseam.id("textures/environment/halo.png");

    private static final int DUST = 2200;
    private static final int BRIGHT = 420;
    private static final int LANTERNS = 44;
    /** Each star: x, y, z, size (degrees), r, g, b, brightness, twinkle speed, twinkle phase. */
    private static final float[][] DUST_STARS = new float[DUST][];
    private static final float[][] BRIGHT_STARS = new float[BRIGHT][];
    private static final float[][] LANTERN = new float[LANTERNS][];
    private static final float[][] STAR_COLOURS = {{1, 1, 1}, {0.78f, 0.86f, 1}, {1, 0.9f, 0.7f}, {1, 0.78f, 0.9f}, {0.8f, 1, 0.95f}};
    /** The two nebulae's planes: each band runs round a great circle tilted this way. */
    private static final Vector3f BAND_A_U = new Vector3f(Mth.cos(0.35f), 0, Mth.sin(0.35f));
    private static final Vector3f BAND_A_W = new Vector3f(Mth.cos(1.92f) * Mth.cos(1.08f), Mth.sin(1.08f), Mth.sin(1.92f) * Mth.cos(1.08f)).normalize();
    private static final Vector3f BAND_B_U = new Vector3f(Mth.cos(2.2f), 0.2f, Mth.sin(2.2f)).normalize();
    private static final Vector3f BAND_B_W = new Vector3f(0.3f, 0.9f, -0.3f).normalize();

    static {
        Random random = new Random(1508);
        for (int k = 0; k < DUST; k++) {
            Vector3f at = k % 3 == 0 ? nearBand(random, k % 2 == 0 ? BAND_A_U : BAND_B_U, k % 2 == 0 ? BAND_A_W : BAND_B_W) : uniform(random);
            float[] colour = STAR_COLOURS[random.nextInt(STAR_COLOURS.length)];
            DUST_STARS[k] = new float[] {at.x, at.y, at.z, 0.09f + random.nextFloat() * 0.12f, colour[0], colour[1], colour[2],
                    0.25f + random.nextFloat() * 0.45f, 0.02f + random.nextFloat() * 0.05f, random.nextFloat() * 6.3f};
        }
        for (int k = 0; k < BRIGHT; k++) {
            Vector3f at = uniform(random);
            float[] colour = STAR_COLOURS[random.nextInt(STAR_COLOURS.length)];
            float size = 0.35f + random.nextFloat() * random.nextFloat() * 1.1f;
            BRIGHT_STARS[k] = new float[] {at.x, at.y, at.z, size, colour[0], colour[1], colour[2], 0.55f + random.nextFloat() * 0.45f,
                    0.03f + random.nextFloat() * 0.09f, random.nextFloat() * 6.3f};
        }
        for (int k = 0; k < LANTERNS; k++) {
            float[] colour = k % 4 == 0 ? new float[] {0.6f, 0.95f, 1} : k % 3 == 0 ? new float[] {1, 0.66f, 0.8f} : new float[] {1, 0.8f, 0.48f};
            LANTERN[k] = new float[] {random.nextFloat() * Mth.TWO_PI, 4 + random.nextFloat() * 26, (random.nextFloat() - 0.5f) * 0.0006f,
                    0.18f + random.nextFloat() * 0.25f, colour[0], colour[1], colour[2], random.nextFloat() * 6.3f};
        }
    }

    private StarField() {}

    private static Vector3f uniform(Random random) {
        double z = random.nextDouble() * 2 - 1;
        double angle = random.nextDouble() * Math.PI * 2;
        double ring = Math.sqrt(1 - z * z);
        return new Vector3f((float) (Math.cos(angle) * ring), (float) z, (float) (Math.sin(angle) * ring));
    }

    private static Vector3f nearBand(Random random, Vector3f u, Vector3f w) {
        float t = random.nextFloat() * Mth.TWO_PI;
        Vector3f normal = new Vector3f(u).cross(w).normalize();
        float across = (float) random.nextGaussian() * 0.12f;
        return new Vector3f(u).mul(Mth.cos(t)).add(new Vector3f(w).mul(Mth.sin(t))).mul(Mth.cos(across)).add(normal.mul(Mth.sin(across))).normalize();
    }

    /** The turn of the star sphere for a sun angle: vanilla's, so the stars wheel with the sun. */
    public static Matrix4f turn(float sunAngle) {
        return new Matrix4f().rotateY(-Mth.HALF_PI).rotateX(sunAngle);
    }

    /**
     * Draws the nebulae, stars and shooting stars for {@code night} (0 to 1) and the lanterns for {@code lanterns},
     * with the sky turned by {@code sunAngle}, at game time {@code time} (ticks).
     */
    public static void draw(Matrix4f matrix, float sunAngle, float night, float lanterns, float time) {
        Matrix4f turn = turn(sunAngle);
        SkyDraw.additive();
        if (night > 0.01f) {
            nebula(matrix, turn, NEBULA, BAND_A_U, BAND_A_W, 13, time * 0.00002f, night * 0.85f);
            nebula(matrix, turn, NEBULA_TEAL, BAND_B_U, BAND_B_W, 10, -time * 0.000035f + 0.5f, night * 0.6f);
            stars(matrix, turn, night, time);
            meteors(matrix, night, time);
        }
        if (lanterns > 0.01f) {
            lanterns(matrix, lanterns, time);
        }
    }

    private static void stars(Matrix4f matrix, Matrix4f turn, float night, float time) {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder dust = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Vector3f at = new Vector3f();
        for (float[] star : DUST_STARS) {
            turn.transformDirection(star[0], star[1], star[2], at);
            float alpha = night * star[7] * (0.7f + 0.3f * Mth.sin(time * star[8] + star[9])) * SkyDraw.aboveHorizon(at);
            if (alpha > 0.01f) {
                quad(dust, matrix, at, star[3], null, star[4], star[5], star[6], alpha);
            }
        }
        SkyDraw.draw(dust);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, GLINT);
        BufferBuilder bright = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (float[] star : BRIGHT_STARS) {
            turn.transformDirection(star[0], star[1], star[2], at);
            float twinkle = 0.6f + 0.4f * Mth.sin(time * star[8] + star[9]);
            float alpha = night * star[7] * twinkle * SkyDraw.aboveHorizon(at);
            if (alpha > 0.01f) {
                quad(bright, matrix, at, star[3] * (0.8f + 0.25f * twinkle), new float[] {0, 0, 1, 1}, star[4], star[5], star[6], alpha);
            }
        }
        SkyDraw.draw(bright);
    }

    /** A square of {@code degrees} half-width facing the camera from {@code direction}; textured if {@code uv} is given. */
    private static void quad(BufferBuilder buffer, Matrix4f matrix, Vector3f direction, float degrees, float[] uv, float r, float g, float b,
            float alpha) {
        float half = SkyDraw.DOME * degrees * Mth.DEG_TO_RAD;
        Vector3f at = new Vector3f(direction).mul(SkyDraw.DOME);
        Vector3f side = new Vector3f(direction).cross(0, 1, 0);
        if (side.lengthSquared() < 1.0e-6f) {
            side.set(1, 0, 0);
        }
        side.normalize().mul(half);
        Vector3f up = new Vector3f(side).cross(direction).normalize().mul(half);
        if (uv == null) {
            buffer.addVertex(matrix, at.x - side.x - up.x, at.y - side.y - up.y, at.z - side.z - up.z).setColor(r, g, b, alpha);
            buffer.addVertex(matrix, at.x + side.x - up.x, at.y + side.y - up.y, at.z + side.z - up.z).setColor(r, g, b, alpha);
            buffer.addVertex(matrix, at.x + side.x + up.x, at.y + side.y + up.y, at.z + side.z + up.z).setColor(r, g, b, alpha);
            buffer.addVertex(matrix, at.x - side.x + up.x, at.y - side.y + up.y, at.z - side.z + up.z).setColor(r, g, b, alpha);
        } else {
            buffer.addVertex(matrix, at.x - side.x - up.x, at.y - side.y - up.y, at.z - side.z - up.z).setUv(uv[0], uv[3]).setColor(r, g, b, alpha);
            buffer.addVertex(matrix, at.x + side.x - up.x, at.y + side.y - up.y, at.z + side.z - up.z).setUv(uv[2], uv[3]).setColor(r, g, b, alpha);
            buffer.addVertex(matrix, at.x + side.x + up.x, at.y + side.y + up.y, at.z + side.z + up.z).setUv(uv[2], uv[1]).setColor(r, g, b, alpha);
            buffer.addVertex(matrix, at.x - side.x + up.x, at.y - side.y + up.y, at.z - side.z + up.z).setUv(uv[0], uv[1]).setColor(r, g, b, alpha);
        }
    }

    /** A nebula band round a great circle (u, w) of the turning sky, {@code width} degrees wide, its texture slid by {@code slide}. */
    private static void nebula(Matrix4f matrix, Matrix4f turn, ResourceLocation texture, Vector3f u, Vector3f w, float width, float slide,
            float strength) {
        Vector3f normal = new Vector3f(u).cross(w).normalize();
        float across = width * Mth.DEG_TO_RAD;
        int steps = 96;
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int s = 0; s < steps; s++) {
            float t0 = (float) (s * Math.PI * 2 / steps);
            float t1 = (float) ((s + 1) * Math.PI * 2 / steps);
            float u0 = s * 4f / steps + slide;
            float u1 = (s + 1) * 4f / steps + slide;
            bandVertex(buffer, matrix, turn, u, w, normal, t0, -across, u0, 1, strength);
            bandVertex(buffer, matrix, turn, u, w, normal, t1, -across, u1, 1, strength);
            bandVertex(buffer, matrix, turn, u, w, normal, t1, across, u1, 0, strength);
            bandVertex(buffer, matrix, turn, u, w, normal, t0, across, u0, 0, strength);
        }
        SkyDraw.draw(buffer);
    }

    private static void bandVertex(BufferBuilder buffer, Matrix4f matrix, Matrix4f turn, Vector3f u, Vector3f w, Vector3f normal, float t, float across,
            float tu, float tv, float strength) {
        Vector3f local = new Vector3f(u).mul(Mth.cos(t)).add(new Vector3f(w).mul(Mth.sin(t))).mul(Mth.cos(across)).add(new Vector3f(normal).mul(Mth.sin(across)));
        Vector3f at = turn.transformDirection(local, new Vector3f());
        float alpha = strength * SkyDraw.aboveHorizon(at);
        at.mul(SkyDraw.DOME * 0.99f);
        buffer.addVertex(matrix, at.x, at.y, at.z).setUv(tu, tv).setColor(1, 1, 1, alpha);
    }

    /**
     * Shooting stars: every 70 ticks one may fall (more than one in three do), from a seeded spot in the upper sky,
     * streaking 20 to 35 degrees in under two seconds.
     */
    private static void meteors(Matrix4f matrix, float night, float time) {
        long slot = (long) Math.floor(time / 70);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, METEOR);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (long s = slot - 1; s <= slot; s++) {
            long h = Noise.mix(77, s, 0, 0, 3);
            if ((h & 0xFF) > 96) {
                continue;
            }
            float start = s * 70 + ((h >>> 8) & 31);
            float duration = 18 + ((h >>> 13) & 15);
            float progress = (time - start) / duration;
            if (progress < 0 || progress > 1) {
                continue;
            }
            float azimuth = ((h >>> 17) & 1023) / 1023f * Mth.TWO_PI;
            float elevation = (25 + ((h >>> 27) & 63) * 0.7f) * Mth.DEG_TO_RAD;
            float heading = ((h >>> 33) & 1023) / 1023f * Mth.TWO_PI;
            float length = (20 + ((h >>> 43) & 15)) * Mth.DEG_TO_RAD;
            Vector3f origin = new Vector3f(Mth.cos(elevation) * Mth.cos(azimuth), Mth.sin(elevation), Mth.cos(elevation) * Mth.sin(azimuth));
            Vector3f east = new Vector3f(origin).cross(0, 1, 0).normalize();
            Vector3f north = new Vector3f(east).cross(origin).normalize();
            Vector3f travel = east.mul(Mth.cos(heading)).add(north.mul(Mth.sin(heading))).normalize();
            Vector3f head = new Vector3f(origin).mul(Mth.cos(length * progress)).add(new Vector3f(travel).mul(Mth.sin(length * progress))).normalize();
            Vector3f tail = new Vector3f(origin).mul(Mth.cos(length * Math.max(0, progress - 0.35f)))
                    .add(new Vector3f(travel).mul(Mth.sin(length * Math.max(0, progress - 0.35f)))).normalize();
            float alpha = night * Mth.sin(progress * Mth.PI) * SkyDraw.aboveHorizon(head);
            Vector3f across = new Vector3f(head).cross(travel).normalize().mul(SkyDraw.DOME * 0.004f);
            head.mul(SkyDraw.DOME * 0.98f);
            tail.mul(SkyDraw.DOME * 0.98f);
            buffer.addVertex(matrix, tail.x - across.x, tail.y - across.y, tail.z - across.z).setUv(0, 1).setColor(1, 1, 1, alpha);
            buffer.addVertex(matrix, head.x - across.x, head.y - across.y, head.z - across.z).setUv(1, 1).setColor(1, 1, 1, alpha);
            buffer.addVertex(matrix, head.x + across.x, head.y + across.y, head.z + across.z).setUv(1, 0).setColor(1, 1, 1, alpha);
            buffer.addVertex(matrix, tail.x + across.x, tail.y + across.y, tail.z + across.z).setUv(0, 0).setColor(1, 1, 1, alpha);
        }
        SkyDraw.draw(buffer);
    }

    /** Small warm lights drifting slowly round the low sky, bobbing and flickering. */
    private static void lanterns(Matrix4f matrix, float strength, float time) {
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, HALO);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (float[] lantern : LANTERN) {
            float azimuth = lantern[0] + time * lantern[2];
            float elevation = (lantern[1] + 1.5f * Mth.sin(time * 0.006f + lantern[7])) * Mth.DEG_TO_RAD;
            Vector3f at = new Vector3f(Mth.cos(elevation) * Mth.cos(azimuth), Mth.sin(elevation), Mth.cos(elevation) * Mth.sin(azimuth));
            float flicker = 0.75f + 0.25f * Mth.sin(time * 0.21f + lantern[7] * 3);
            quad(buffer, matrix, at, lantern[3], new float[] {0, 0, 1, 1}, lantern[4], lantern[5], lantern[6], strength * flicker);
        }
        SkyDraw.draw(buffer);
    }
}
