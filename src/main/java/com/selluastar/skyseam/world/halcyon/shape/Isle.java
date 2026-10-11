package com.selluastar.skyseam.world.halcyon.shape;

import com.selluastar.skyseam.world.HalcyonLayout;
import com.selluastar.skyseam.world.halcyon.HalcyonRegion;
import com.selluastar.skyseam.world.halcyon.plan.Noise;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

/**
 * A small island, 30 to 120 blocks across: around each main island, and scattered between them. Three kinds: a
 * grassy mound, a steep rocky knoll, and a low sandbar.
 */
public final class Isle extends LandFeature {
    public enum Kind { MOUND, KNOLL, SANDBAR }

    private static final int SEA = HalcyonLayout.SEA_LEVEL;

    public final Kind kind;
    public final double cx;
    public final double cz;
    public final double radius;
    private final double height;
    private final Noise noise;
    private final double ox;
    private final double oz;

    public Isle(Kind kind, double cx, double cz, double radius, double height, HalcyonRegion region, long seed, Noise noise) {
        super(Mth.floor(cx - radius * 1.8 - 14), Mth.floor(cz - radius * 1.8 - 14), Mth.ceil(cx + radius * 1.8 + 14),
                Mth.ceil(cz + radius * 1.8 + 14), region, seed);
        RandomSource random = new XoroshiroRandomSource(seed);
        this.kind = kind;
        this.cx = cx;
        this.cz = cz;
        this.radius = radius;
        this.height = height;
        this.noise = noise;
        this.ox = random.nextInt(20000) - 10000;
        this.oz = random.nextInt(20000) - 10000;
    }

    @Override
    public void sample(int x, int z, LandSample out) {
        double wx = x + 10 * noise.warpX(x + ox, z + oz, 50);
        double wz = z + 10 * noise.warpZ(x + ox, z + oz, 50);
        double dx = wx - cx;
        double dz = wz - cz;
        double angle = Math.atan2(dz, dx);
        double rim = radius * (0.78 + 0.22 * noise.fbm(ox + Math.cos(angle) * 60, oz + Math.sin(angle) * 60, 40, 2));
        double d = Math.sqrt(dx * dx + dz * dz) / rim;
        double shelf = 1 - Noise.smoothstep(1.0, 1.7, d);
        if (shelf > out.shelf) {
            out.shelf = shelf;
        }
        if (d >= 1) {
            return;
        }
        double t = 1 - d;
        double h;
        Surface surface = Surface.TURF;
        switch (kind) {
            case MOUND -> h = SEA - 2 + height * Math.pow(t, 0.8) * (0.85 + 0.15 * noise.fbm(x + ox, z + oz, 30, 2));
            case KNOLL -> h = SEA - 2 + height * Math.pow(Noise.smoothstep(0, 0.4, t), 0.7) * (0.8 + 0.25 * noise.ridged(x + ox, z + oz, 30, 3));
            default -> {
                h = SEA - 2 + (2.6 + 1.5 * noise.fbm(x + ox, z + oz, 20, 2)) * Noise.smoothstep(0, 0.5, t);
                surface = Surface.SAND;
            }
        }
        if (h > SEA) {
            h = SEA + Noise.terrace(h - SEA, kind == Kind.KNOLL ? 5 : 3, 0.3);
        }
        if (h > out.height) {
            out.height = h;
            out.surface = surface;
        }
    }

    @Override
    public int peak() {
        return (int) Math.ceil(SEA + height * 1.1);
    }

    @Override
    public String describe() {
        return "isle " + kind.name().toLowerCase() + " r" + Math.round(radius) + " at " + Math.round(cx) + ", " + Math.round(cz);
    }
}
