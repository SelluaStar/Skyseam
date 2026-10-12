package com.selluastar.skyseam.halcyon.vortex;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.selluastar.skyseam.config.SkyseamConfig;
import com.selluastar.skyseam.world.HalcyonLayout;
import com.selluastar.skyseam.world.halcyon.plan.HalcyonPlan;
import com.selluastar.skyseam.world.halcyon.plan.Noise;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Where and when the Halcyon's flux vortices form (docs/DECISIONS.md K97), worked out from the world's seed and the
 * game time alone, so nothing is saved and every server and restart agrees.
 *
 * <p>The Halcyon is cut into square cells ({@code flux_vortex.cell_size}) and time into ten-minute slots. In each slot
 * a cell may hold one vortex ({@code chance}), forming at a random moment and lasting a random while within the slot,
 * so vortices come and go at random times. Where it forms is chosen afresh each slot: the lowest ground among a
 * dozen random spots in the cell, preferring lowland just above the sea, then open water, never a mountain (more than
 * 60 blocks above the sea). Spots whose column would pass through a floating island, a sea stack or a blade of rock
 * are passed over, as are the Obelisk's waters, the Arrival Lane and the Spindle.
 */
public final class VortexField {
    /** A slot: each cell holds at most one vortex in it. */
    public static final int SLOT_TICKS = 12000;
    /** A vortex never stands this close to its cell's edge, so two never touch. */
    private static final int MARGIN = 64;
    private static final int CANDIDATES = 12;
    /** Half the side of the square round a spot that must be clear of floating islands and rock stacks: the column's width and a little. */
    private static final int CLEAR = 20;
    /** Ground higher than this above the sea is a mountain: no vortex. */
    private static final int MOUNTAIN = 60;
    private static final int SEA = HalcyonLayout.SEA_LEVEL;

    /** Chosen sites, by seed, cell, slot and settings: each is looked for once. */
    private static final Map<String, Optional<FluxVortex>> CACHE = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Optional<FluxVortex>> eldest) {
            return size() > 1024;
        }
    };

    private VortexField() {}

    public static boolean enabled() {
        return setting(SkyseamConfig.VORTEX_ENABLED);
    }

    public static int cellSize() {
        return setting(SkyseamConfig.VORTEX_CELL_SIZE);
    }

    /** Every vortex alive at {@code time} (or forming within the next {@code ahead} ticks) whose axis is within {@code radius}. */
    public static List<FluxVortex> around(HalcyonPlan plan, double x, double z, double radius, long time, long ahead) {
        List<FluxVortex> out = new ArrayList<>();
        int cell = cellSize();
        long slot = Math.floorDiv(time, SLOT_TICKS);
        int cx0 = Math.floorDiv((int) Math.floor(x - radius), cell);
        int cx1 = Math.floorDiv((int) Math.floor(x + radius), cell);
        int cz0 = Math.floorDiv((int) Math.floor(z - radius), cell);
        int cz1 = Math.floorDiv((int) Math.floor(z + radius), cell);
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                for (long s = slot; s <= Math.floorDiv(time + ahead, SLOT_TICKS); s++) {
                    at(plan, cx, cz, s).ifPresent(vortex -> {
                        if (vortex.end() > time && vortex.start() <= time + ahead && Math.hypot(vortex.x() - x, vortex.z() - z) <= radius) {
                            out.add(vortex);
                        }
                    });
                }
            }
        }
        return out;
    }

    /** The vortex cell {@code (cx, cz)} holds in {@code slot}, if any. */
    public static Optional<FluxVortex> at(HalcyonPlan plan, int cx, int cz, long slot) {
        int cell = cellSize();
        double chance = setting(SkyseamConfig.VORTEX_CHANCE);
        int top = setting(SkyseamConfig.VORTEX_TOP_Y);
        int minLife = setting(SkyseamConfig.VORTEX_MIN_SECONDS) * 20;
        int maxLife = Math.max(minLife, setting(SkyseamConfig.VORTEX_MAX_SECONDS) * 20);
        String key = plan.seed + ":" + cx + ":" + cz + ":" + slot + ":" + cell + ":" + chance + ":" + top + ":" + minLife + ":" + maxLife;
        synchronized (CACHE) {
            Optional<FluxVortex> known = CACHE.get(key);
            if (known != null) {
                return known;
            }
        }
        Optional<FluxVortex> found = choose(plan, cx, cz, slot, cell, chance, top, minLife, maxLife);
        synchronized (CACHE) {
            CACHE.put(key, found);
        }
        return found;
    }

    private static Optional<FluxVortex> choose(HalcyonPlan plan, int cx, int cz, long slot, int cell, double chance, int top, int minLife,
            int maxLife) {
        long h = Noise.mix(plan.seed, cx, cz, slot, 0x7A5E);
        if ((h & 0xFFFF) / 65536.0 >= chance) {
            return Optional.empty();
        }
        int life = Math.min(SLOT_TICKS - 200, minLife + (int) Math.floorMod(h >>> 16, (long) (maxLife - minLife + 1)));
        long start = slot * SLOT_TICKS + Math.floorMod(h >>> 32, (long) (SLOT_TICKS - life));
        int best = Integer.MAX_VALUE;
        int bestX = 0;
        int bestZ = 0;
        int bestBase = 0;
        int span = cell - 2 * MARGIN;
        for (int k = 0; k < CANDIDATES; k++) {
            long hk = Noise.mix(plan.seed, cx, cz, slot, 1000 + k);
            int x = cx * cell + MARGIN + (int) Math.floorMod(hk, (long) span);
            int z = cz * cell + MARGIN + (int) Math.floorMod(hk >>> 24, (long) span);
            if (!open(plan, x, z, top)) {
                continue;
            }
            int ground = (int) Math.round(plan.landHeight(x, z));
            if (ground > SEA + MOUNTAIN) {
                continue;
            }
            // Lowland just above the sea first, then open water, then higher ground.
            int score = ground < SEA ? 30 : ground - SEA;
            if (score < best) {
                best = score;
                bestX = x;
                bestZ = z;
                bestBase = Math.max(ground + 1, SEA);
            }
        }
        if (best == Integer.MAX_VALUE) {
            return Optional.empty();
        }
        long id = Noise.mix(plan.seed, cx, cz, slot, 0x51DE);
        return Optional.of(new FluxVortex(id, bestX + 0.5, bestZ + 0.5, bestBase, top, start, start + life));
    }

    /** True if a vortex may stand at the column: clear of floating islands and rock, and of the landmarks. */
    private static boolean open(HalcyonPlan plan, int x, int z, int top) {
        if (plan.landmarks.nearObelisk(x, z, 280) || Math.hypot(x, z) < 200
                || Math.hypot(x - plan.landmarks.arrival.x, z - plan.landmarks.arrival.z) < 240) {
            return false;
        }
        return plan.solidsNear(x - CLEAR, z - CLEAR, x + CLEAR, z + CLEAR).stream().noneMatch(solid -> solid.bottom() < top);
    }

    private static <T> T setting(ModConfigSpec.ConfigValue<T> value) {
        try {
            return value.get();
        } catch (IllegalStateException notLoaded) {
            return value.getDefault();
        }
    }
}
