package com.selluastar.skyseam.client.seam;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

import com.selluastar.skyseam.seam.SeamEntity;
import com.selluastar.skyseam.seam.SeamShape;
import com.selluastar.skyseam.seam.SeamTimeline;

import net.minecraft.util.Mth;

/**
 * The look-only layout of one Seam, made from its id like its {@link SeamShape}, so every client draws the same Seam:
 * how deep each open cell is recessed, the stepped cracks that branch out into the sky around it, and the voxel
 * fragments that float around its border. Nothing here affects the game.
 */
final class SeamDecor {
    private static final Map<SeamShape, SeamDecor> CACHE = new WeakHashMap<>();

    /** A stepped crack running out from the opening's edge: a polyline in plane coordinates. */
    record Fracture(float[] us, float[] vs, float length, float start) {}

    /**
     * A voxel fragment floating near the border. {@code (u, v, z)} is where it sits once spread out, {@code (ou, ov)}
     * the direction it drifts, {@code appear} the reveal age it breaks loose at. Shards show the Halcyon sky.
     */
    record Fragment(float u, float v, float z, float size, boolean plate, float appear, boolean shard, float phase, float ou, float ov, int tint) {}

    final SeamShape shape;
    final long seed;
    /** The deepest an open cell is recessed, in blocks. */
    final float tunnel;
    private final float[] depth;
    final List<Fracture> fractures;
    final List<Fragment> fragments;

    private SeamDecor(SeamShape shape, long seed, float tunnel, float[] depth, List<Fracture> fractures, List<Fragment> fragments) {
        this.shape = shape;
        this.seed = seed;
        this.tunnel = tunnel;
        this.depth = depth;
        this.fractures = fractures;
        this.fragments = fragments;
    }

    static SeamDecor of(SeamEntity seam) {
        SeamShape shape = seam.shape();
        return CACHE.computeIfAbsent(shape, s -> create(s, SeamShape.seed(seam.getUUID())));
    }

    /** How deep cell (i, j) is recessed when open, in blocks: stepped in half blocks, in clusters of 4 by 4 cells. */
    float depth(int i, int j) {
        return depth[i + j * shape.cols];
    }

    /** A stable pseudo-random number from 0 to 1 for a cell, so per-cell details stay put from frame to frame. */
    float hash(int i, int j, int salt) {
        long h = seed ^ (i * 0x9E3779B97F4A7C15L) ^ (j * 0xC2B2AE3D27D4EB4FL) ^ (salt * 0x165667B19E3779F9L);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return (h & 0xFFFFFF) / (float) 0xFFFFFF;
    }

    private static SeamDecor create(SeamShape shape, long seed) {
        Random random = new Random(seed * 31 + 7);
        float size = Math.max(shape.cols, shape.rows);
        float tunnel = Mth.clamp(size * 0.08f, 1.5f, 4f);

        float[] depth = new float[shape.cols * shape.rows];
        SeamDecor probe = new SeamDecor(shape, seed, tunnel, depth, List.of(), List.of());
        for (int j = 0; j < shape.rows; j++) {
            for (int i = 0; i < shape.cols; i++) {
                float cluster = probe.hash(Math.floorDiv(i, 4), Math.floorDiv(j, 4), 11);
                depth[i + j * shape.cols] = Math.round(tunnel * (0.5f + 0.5f * cluster) * 2) / 2f;
            }
        }

        // Edges of the finished opening, as (u, v, outward normal) at each side's middle.
        List<float[]> edges = new ArrayList<>();
        for (int j = 0; j < shape.rows; j++) {
            for (int i = 0; i < shape.cols; i++) {
                if (!shape.isPartOfOpening(i, j)) {
                    continue;
                }
                float u = shape.cellU(i);
                float v = shape.cellV(j);
                if (!shape.isPartOfOpening(i - 1, j)) {
                    edges.add(new float[] {u, v + 0.5f, -1, 0});
                }
                if (!shape.isPartOfOpening(i + 1, j)) {
                    edges.add(new float[] {u + 1, v + 0.5f, 1, 0});
                }
                if (!shape.isPartOfOpening(i, j - 1)) {
                    edges.add(new float[] {u + 0.5f, v, 0, -1});
                }
                if (!shape.isPartOfOpening(i, j + 1)) {
                    edges.add(new float[] {u + 0.5f, v + 1, 0, 1});
                }
            }
        }

        List<Fracture> fractures = new ArrayList<>();
        int count = edges.isEmpty() ? 0 : Mth.clamp(Math.round(size / 4) + 4, 6, 18);
        for (int k = 0; k < count; k++) {
            float[] edge = edges.get(random.nextInt(edges.size()));
            float start = SeamTimeline.CRACK_AT + random.nextFloat() * 35;
            Fracture main = fracture(random, edge[0], edge[1], edge[2], edge[3], 3 + random.nextFloat() * size * 0.3f, start);
            fractures.add(main);
            if (random.nextFloat() < 0.6f && main.us().length > 2) {
                int at = 1 + random.nextInt(main.us().length - 2);
                float side = random.nextBoolean() ? 1 : -1;
                fractures.add(fracture(random, main.us()[at], main.vs()[at], -edge[3] * side, edge[2] * side,
                        2 + random.nextFloat() * size * 0.12f, start + 8 + random.nextFloat() * 10));
            }
        }

        List<Fragment> fragments = new ArrayList<>();
        int pieces = Mth.clamp(Math.round(size * 1.3f), 16, 72);
        for (int k = 0; k < pieces; k++) {
            float angle = random.nextFloat() * Mth.TWO_PI;
            float ou = Mth.cos(angle);
            float ov = Mth.sin(angle);
            // Start on the opening's edge along this direction, then sit a little outside it.
            float reach = 0;
            while (reach < size && shape.isPartOfOpening(Mth.floor(ou * reach + shape.cols / 2f), Mth.floor(ov * reach + shape.rows / 2f))) {
                reach += 0.5f;
            }
            reach = Math.max(reach, Math.min(shape.cols, shape.rows) * 0.3f) + 0.5f + random.nextFloat() * size * 0.18f;
            float s = 0.3f + 1.4f * random.nextFloat() * random.nextFloat();
            fragments.add(new Fragment(ou * reach, ov * reach, (random.nextFloat() - 0.5f) * 5, s, random.nextFloat() < 0.3f,
                    36 + random.nextFloat() * 70, random.nextFloat() < 0.4f, random.nextFloat() * Mth.TWO_PI, ou, ov, random.nextInt(3)));
        }
        return new SeamDecor(shape, seed, tunnel, depth, List.copyOf(fractures), List.copyOf(fragments));
    }

    /** A crack that runs outward along (nu, nv) in whole-block steps, jogging sideways now and then. */
    private static Fracture fracture(Random random, float u, float v, float nu, float nv, float length, float start) {
        List<float[]> points = new ArrayList<>();
        points.add(new float[] {u, v});
        float total = 0;
        boolean outward = true;
        while (total < length) {
            float step = outward ? 1 + random.nextInt(3) : (random.nextBoolean() ? 1 : -1) * (1 + random.nextInt(2));
            float du = outward ? nu * step : -nv * step;
            float dv = outward ? nv * step : nu * step;
            u += du;
            v += dv;
            total += Math.abs(step);
            points.add(new float[] {u, v});
            outward = !outward || random.nextFloat() < 0.35f;
        }
        float[] us = new float[points.size()];
        float[] vs = new float[points.size()];
        for (int n = 0; n < points.size(); n++) {
            us[n] = points.get(n)[0];
            vs[n] = points.get(n)[1];
        }
        return new Fracture(us, vs, total, start);
    }
}
