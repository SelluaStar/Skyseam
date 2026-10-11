package com.selluastar.skyseam.client.halcyon;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * The Halcyon's clouds (the author's image 7): big blocky clouds in three decks: sparse puffy banks drifting between
 * the islands at Y 175, a fuller deck at Y 300, and a thin one under the lid at Y 385. A cloud is a clump of cells; its
 * middle rises a second step, so the banks read as cumulus rather than flat tiles. Each face is shaded (lit tops,
 * darker bottoms) and the whole tinted by the Lantern-Sun's phase: white by day, violet at night. Each deck's mesh is
 * built round the camera into a vertex buffer and rebuilt only when the camera crosses a cell or the light changes; it
 * is drawn in two passes (depth, then colour) like vanilla's, so the translucent clouds don't show their insides.
 */
public final class HalcyonClouds {
    /** How many blocks out from the camera each deck reaches. */
    private static final int REACH = 380;

    /**
     * One deck: its height, its cell size and step height, how much of the sky it covers, the size of its banks (in
     * cells), and its drift speed (blocks a tick).
     */
    private record Deck(int y, int cell, int step, double threshold, double scale, double speed, SimplexNoise noise) {
        double value(int cx, int cz) {
            return noise.getValue(cx / scale, cz / scale) * 0.75 + noise.getValue(cx / scale * 2.7 + 17, cz / scale * 2.7) * 0.25;
        }

        /** 0 for open sky, 1 for a cloud's edge, 2 for its puffed-up middle. */
        int level(int cx, int cz) {
            double v = value(cx, cz);
            return v < threshold ? 0 : v < threshold + 0.16 ? 1 : 2;
        }
    }

    private static final Deck[] DECKS = {
            new Deck(175, 16, 6, 0.68, 6.0, 0.025, new SimplexNoise(RandomSource.create(71))),
            new Deck(300, 12, 4, 0.38, 8.0, 0.035, new SimplexNoise(RandomSource.create(73))),
            new Deck(385, 12, 4, 0.5, 11.0, 0.045, new SimplexNoise(RandomSource.create(79))),
    };
    private static final Built[] BUILT = new Built[DECKS.length];

    /** A deck's mesh as last built: where round, and with what light. */
    private static final class Built {
        VertexBuffer buffer;
        int cellX = Integer.MIN_VALUE;
        int cellZ = Integer.MIN_VALUE;
        int lightKey = -1;
        boolean empty;
    }

    private HalcyonClouds() {}

    /**
     * Draws every deck. {@code top}, {@code side} and {@code bottom} are the face colours for this moment, and
     * {@code opacity} fades the clouds out (in the Veil).
     */
    public static void render(Matrix4f modelView, Matrix4f projection, double camX, double camY, double camZ, double time, float[] top,
            float[] side, float[] bottom, float opacity) {
        int lightKey = (int) (top[0] * 31) * 1_000_000 + (int) (top[1] * 31) * 10_000 + (int) (top[2] * 31) * 100 + (int) (opacity * 31);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        for (int k = 0; k < DECKS.length; k++) {
            Deck deck = DECKS[k];
            double drift = time * deck.speed;
            double fx = camX + drift;
            int cellX = Mth.floor(fx / deck.cell);
            int cellZ = Mth.floor(camZ / deck.cell);
            Built built = BUILT[k];
            if (built == null) {
                built = BUILT[k] = new Built();
            }
            if (built.buffer == null || built.cellX != cellX || built.cellZ != cellZ || built.lightKey != lightKey) {
                rebuild(built, deck, cellX, cellZ, top, side, bottom, opacity);
                built.cellX = cellX;
                built.cellZ = cellZ;
                built.lightKey = lightKey;
            }
            if (built.empty) {
                continue;
            }
            Matrix4f matrix = new Matrix4f(modelView).translate((float) (cellX * deck.cell - fx), (float) (deck.y - camY),
                    (float) (cellZ * deck.cell - camZ));
            built.buffer.bind();
            // Depth first, then colour where the depth matches: only the outermost faces show.
            RenderSystem.colorMask(false, false, false, false);
            built.buffer.drawWithShader(matrix, projection, RenderSystem.getShader());
            RenderSystem.colorMask(true, true, true, true);
            built.buffer.drawWithShader(matrix, projection, RenderSystem.getShader());
            VertexBuffer.unbind();
        }
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static void rebuild(Built built, Deck deck, int cellX, int cellZ, float[] top, float[] side, float[] bottom, float opacity) {
        int radius = REACH / deck.cell;
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = -radius; i <= radius; i++) {
            for (int j = -radius; j <= radius; j++) {
                double d = Math.sqrt(i * i + j * j) / radius;
                int level = d > 1 ? 0 : deck.level(cellX + i, cellZ + j);
                if (level == 0) {
                    continue;
                }
                // The thin edge of a cloud is a little see-through; its puffed-up middle is dense.
                float alpha = opacity * (level == 1 ? 0.78f : 0.94f) * (float) Mth.clamp(1 - d * d, 0, 1);
                float x0 = i * deck.cell;
                float z0 = j * deck.cell;
                float x1 = x0 + deck.cell;
                float z1 = z0 + deck.cell;
                float h = level * deck.step;
                quad(buffer, x0, h, z0, x0, h, z1, x1, h, z1, x1, h, z0, top, alpha);
                quad(buffer, x0, 0, z0, x1, 0, z0, x1, 0, z1, x0, 0, z1, bottom, alpha);
                // Each side, from the neighbour's height up to this cell's.
                float east = deck.level(cellX + i + 1, cellZ + j) * deck.step;
                float west = deck.level(cellX + i - 1, cellZ + j) * deck.step;
                float south = deck.level(cellX + i, cellZ + j + 1) * deck.step;
                float north = deck.level(cellX + i, cellZ + j - 1) * deck.step;
                // Each side darkens from its lit top towards the cloud's shadowed base.
                float[] low = mixColour(side, bottom, 0.65f);
                if (east < h) {
                    sideQuad(buffer, x1, east, z0, x1, h, z0, x1, h, z1, x1, east, z1, low, side, alpha);
                }
                if (west < h) {
                    sideQuad(buffer, x0, west, z0, x0, west, z1, x0, h, z1, x0, h, z0, low, side, alpha * 0.95f);
                }
                if (south < h) {
                    sideQuad(buffer, x0, south, z1, x1, south, z1, x1, h, z1, x0, h, z1, low, side, alpha * 0.9f);
                }
                if (north < h) {
                    sideQuad(buffer, x0, north, z0, x0, h, z0, x1, h, z0, x1, north, z0, low, side, alpha);
                }
            }
        }
        MeshData mesh = buffer.build();
        if (built.buffer == null) {
            built.buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        }
        built.empty = mesh == null;
        if (mesh != null) {
            built.buffer.bind();
            built.buffer.upload(mesh);
            VertexBuffer.unbind();
        }
    }

    /** A side face: vertices at its foot take {@code low}, at its top {@code high}. */
    private static void sideQuad(BufferBuilder buffer, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz,
            float dx, float dy, float dz, float[] low, float[] high, float alpha) {
        float top = Math.max(Math.max(ay, by), Math.max(cy, dy));
        float[][] colours = {ay >= top ? high : low, by >= top ? high : low, cy >= top ? high : low, dy >= top ? high : low};
        buffer.addVertex(ax, ay, az).setColor(colours[0][0], colours[0][1], colours[0][2], alpha);
        buffer.addVertex(bx, by, bz).setColor(colours[1][0], colours[1][1], colours[1][2], alpha);
        buffer.addVertex(cx, cy, cz).setColor(colours[2][0], colours[2][1], colours[2][2], alpha);
        buffer.addVertex(dx, dy, dz).setColor(colours[3][0], colours[3][1], colours[3][2], alpha);
    }

    private static float[] mixColour(float[] a, float[] b, float t) {
        return new float[] {Mth.lerp(t, a[0], b[0]), Mth.lerp(t, a[1], b[1]), Mth.lerp(t, a[2], b[2])};
    }

    private static void quad(BufferBuilder buffer, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx,
            float dy, float dz, float[] c, float alpha) {
        buffer.addVertex(ax, ay, az).setColor(c[0], c[1], c[2], alpha);
        buffer.addVertex(bx, by, bz).setColor(c[0], c[1], c[2], alpha);
        buffer.addVertex(cx, cy, cz).setColor(c[0], c[1], c[2], alpha);
        buffer.addVertex(dx, dy, dz).setColor(c[0], c[1], c[2], alpha);
    }
}
