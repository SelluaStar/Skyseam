package com.selluastar.skyseam.dev;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import javax.imageio.ImageIO;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.world.HalcyonLayout;
import com.selluastar.skyseam.world.halcyon.HalcyonRegion;
import com.selluastar.skyseam.world.halcyon.plan.HalcyonPlan;
import com.selluastar.skyseam.world.halcyon.shape.FloatingIsland;
import com.selluastar.skyseam.world.halcyon.shape.LandFeature;
import com.selluastar.skyseam.world.halcyon.shape.SolidFeature;

/**
 * Development only: a top-down map of one world's Halcyon plan, to review the layout of a seed before flying it. Land
 * is shaded by height with light from the north-west and tinted by biome, the sea darkens with depth, floating
 * islands show as pale overlays, and the Arrival Lane (red), the Obelisk (gold) and the 1,300 and 2,600-block rings
 * where the outer lands begin and the Highlands take over are marked.
 * Written by the server boot check when {@code -Dskyseam.dev.map=<folder>} is set.
 */
public final class HalcyonMapExport {
    private static final int SEA = HalcyonLayout.SEA_LEVEL;
    private static final int TILE = 64;

    private HalcyonMapExport() {}

    /** Writes the map of {@code plan}, {@code blocksPerPixel} blocks to a pixel, covering {@code half} blocks each way. */
    public static void write(HalcyonPlan plan, Path file, int blocksPerPixel, int half) throws IOException {
        int size = 2 * half / blocksPerPixel;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        double[][] heights = new double[size][size];
        int[][] floating = new int[size][size];
        LandFeature.LandSample sample = new LandFeature.LandSample();
        for (int tx = -half; tx < half; tx += TILE) {
            for (int tz = -half; tz < half; tz += TILE) {
                List<LandFeature> lands = plan.landsNear(tx, tz, tx + TILE - 1, tz + TILE - 1);
                List<SolidFeature> solids = plan.solidsNear(tx, tz, tx + TILE - 1, tz + TILE - 1);
                for (int x = tx; x < tx + TILE; x += blocksPerPixel) {
                    for (int z = tz; z < tz + TILE; z += blocksPerPixel) {
                        int px = (x + half) / blocksPerPixel;
                        int pz = (z + half) / blocksPerPixel;
                        plan.land(x, z, lands, sample);
                        heights[px][pz] = sample.height;
                        for (SolidFeature solid : solids) {
                            if (solid instanceof FloatingIsland && x >= solid.minX && x <= solid.maxX && z >= solid.minZ && z <= solid.maxZ) {
                                int[] top = {0};
                                solid.spans(x, z, (from, to) -> top[0] = Math.max(top[0], to));
                                floating[px][pz] = Math.max(floating[px][pz], top[0]);
                            } else if (!(solid instanceof FloatingIsland) && x >= solid.minX && x <= solid.maxX && z >= solid.minZ && z <= solid.maxZ) {
                                int[] top = {0};
                                solid.spans(x, z, (from, to) -> top[0] = Math.max(top[0], to));
                                heights[px][pz] = Math.max(heights[px][pz], top[0]);
                            }
                        }
                    }
                }
            }
        }
        for (int px = 0; px < size; px++) {
            for (int pz = 0; pz < size; pz++) {
                int x = px * blocksPerPixel - half;
                int z = pz * blocksPerPixel - half;
                double h = heights[px][pz];
                int rgb;
                if (h < SEA) {
                    double depth = Math.min(1, (SEA - h) / 45);
                    rgb = mix(0x6FB4D8, 0x1B3A78, depth);
                } else {
                    double west = px > 0 ? heights[px - 1][pz] : h;
                    double north = pz > 0 ? heights[px][pz - 1] : h;
                    double light = Math.max(0.45, Math.min(1.35, 1 + ((h - west) + (h - north)) * 0.08 / blocksPerPixel));
                    int base = tint(plan.region(x, z));
                    double rise = Math.min(1, (h - SEA) / 240);
                    rgb = shade(mix(base, 0xE8E2F0, rise * 0.6), light);
                }
                if (floating[px][pz] > 0) {
                    rgb = mix(rgb, 0xFFFFFF, 0.55);
                }
                image.setRGB(px, pz, rgb);
            }
        }
        for (int ring : new int[] {1300, 2600}) {
            double r = ring / (double) blocksPerPixel;
            for (int k = 0; k < 3000; k += 2) {
                double a = k * Math.PI * 2 / 3000;
                plot(image, (int) (size / 2.0 + Math.cos(a) * r), (int) (size / 2.0 + Math.sin(a) * r), 0xF0F0FF, 0);
            }
        }
        plot(image, (int) ((plan.landmarks.arrival.x + half) / blocksPerPixel), (int) ((plan.landmarks.arrival.z + half) / blocksPerPixel), 0xE02020, 4);
        plot(image, (plan.landmarks.gate.getX() + half) / blocksPerPixel, (plan.landmarks.gate.getZ() + half) / blocksPerPixel, 0xFFC020, 3);
        Files.createDirectories(file.getParent());
        ImageIO.write(image, "png", file.toFile());
        Skyseam.LOGGER.info("Skyseam dev: wrote the Halcyon map for seed {} to {} (regions {})", plan.seed, file, plan.regions.counts());
    }

    private static int tint(HalcyonRegion region) {
        return switch (region) {
            case PETALWASH -> 0x8E6CC0;
            case CIRRUS_REEFS -> 0xC9C0D8;
            case HUSH -> 0x4A3A6E;
            case SPINDLE -> 0x6A5A80;
            case WRECKFIELDS -> 0x8A7A60;
            case MIRROR_SHOALS -> 0xD8C8A8;
            case SUNREACH_CLIFFS -> 0xC89A50;
            case CAPWOOD -> 0x7CA858;
            case ELDERWOOD -> 0x3E6A54;
            default -> 0x888888;
        };
    }

    private static void plot(BufferedImage image, int x, int y, int rgb, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                int px = x + dx;
                int py = y + dy;
                if (px >= 0 && py >= 0 && px < image.getWidth() && py < image.getHeight()) {
                    image.setRGB(px, py, rgb);
                }
            }
        }
    }

    private static int mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    private static int shade(int rgb, double light) {
        int r = (int) Math.min(255, ((rgb >> 16) & 255) * light);
        int g = (int) Math.min(255, ((rgb >> 8) & 255) * light);
        int b = (int) Math.min(255, (rgb & 255) * light);
        return (r << 16) | (g << 8) | b;
    }
}
