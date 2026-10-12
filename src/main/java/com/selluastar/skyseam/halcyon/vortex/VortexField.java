package com.selluastar.skyseam.halcyon.vortex;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import com.selluastar.skyseam.config.SkyseamConfig;
import com.selluastar.skyseam.world.HalcyonLayout;
import com.selluastar.skyseam.world.halcyon.plan.HalcyonPlan;
import com.selluastar.skyseam.world.halcyon.plan.Noise;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Where and when the Halcyon's flux vortices form (docs/DECISIONS.md K97, K103), worked out from the world's seed and
 * the game time alone, so nothing is saved and every server and restart agrees.
 *
 * <p>The Halcyon is cut into square cells ({@code flux_vortices.spawning.cell_size}). Each cell holds one vortex at a
 * time, one after another: each lasts a random while (3 to 7 minutes), then the cell waits a random cooldown (up to 2
 * minutes) and the next forms, if it does ({@code chance}; if not, the cell stays empty that long). Every cell keeps
 * its own times, worked out an hour of its own at a time (its hours start at different moments), so vortices come and
 * go all over at random and never all at once.
 *
 * <p>Where each forms is chosen afresh: the lowest ground among some random spots in the cell, preferring lowland just
 * above the sea, then open water, never a mountain (more than 60 blocks above the sea). Spots whose column would pass
 * through a floating island, a sea stack or a blade of rock are passed over, as are the Obelisk's waters, the Arrival
 * Lane and the Spindle. This holds all over the Halcyon, however far out: only what is worked out round each player
 * depends on where they are, and a spot is only looked for when its vortex is wanted.
 */
public final class VortexField {
    /** A cell's times are worked out this many ticks at a time: an hour of the cell's own. */
    static final int HOUR_TICKS = 72000;
    /** A vortex never stands this close to its cell's edge, so two never touch. */
    private static final int MARGIN = 48;
    private static final int CANDIDATES = 32;
    /** Half the side of the square round a spot that must be clear of floating islands and rock stacks: the column's width and a little. */
    private static final int CLEAR = 20;
    /** Ground higher than this above the sea is a mountain: no vortex. */
    private static final int MOUNTAIN = 60;
    private static final int SEA = HalcyonLayout.SEA_LEVEL;

    /** The settings that shape the field, read once per look. */
    record Settings(int cell, double chance, int top, int minLife, int maxLife, int minCooldown, int maxCooldown) {
        static Settings now() {
            int minLife = setting(SkyseamConfig.VORTEX_MIN_SECONDS) * 20;
            int maxLife = Math.max(minLife, setting(SkyseamConfig.VORTEX_MAX_SECONDS) * 20);
            int minCooldown = setting(SkyseamConfig.VORTEX_MIN_COOLDOWN_SECONDS) * 20;
            int maxCooldown = Math.max(minCooldown, setting(SkyseamConfig.VORTEX_MAX_COOLDOWN_SECONDS) * 20);
            // A vortex and its cooldown must fit in a cell's hour.
            maxLife = Math.min(maxLife, HOUR_TICKS / 2);
            minLife = Math.min(minLife, maxLife);
            maxCooldown = Math.min(maxCooldown, HOUR_TICKS / 2);
            minCooldown = Math.min(minCooldown, maxCooldown);
            return new Settings(setting(SkyseamConfig.VORTEX_CELL_SIZE), setting(SkyseamConfig.VORTEX_CHANCE), setting(SkyseamConfig.VORTEX_TOP_Y), minLife,
                    maxLife, minCooldown, maxCooldown);
        }
    }

    /** One turn of a cell: when it starts and ends, and whether a vortex forms in it. */
    record Turn(int index, long start, long end, boolean forms) {}

    /** Chosen spots, by seed, cell, hour, turn and settings: each is looked for once. Empty: no spot would do. */
    private static final Map<String, Optional<int[]>> SPOTS = new LinkedHashMap<>(4096, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Optional<int[]>> eldest) {
            return size() > 16384;
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
        return around(plan, x, z, radius, time, ahead, new int[] {Integer.MAX_VALUE});
    }

    /**
     * As above, but looking for at most {@code budget[0]} spots not looked for yet (counting it down), nearest cells
     * first; vortices whose spot is past the budget are left out this time. So a wide radius is worked out over a few
     * calls rather than all at once (each spot takes a dozen and more looks at the plan).
     */
    public static List<FluxVortex> around(HalcyonPlan plan, double x, double z, double radius, long time, long ahead, int[] budget) {
        Settings settings = Settings.now();
        int cell = settings.cell();
        List<FluxVortex> out = new ArrayList<>();
        int cx0 = Math.floorDiv((int) Math.floor(x - radius), cell);
        int cx1 = Math.floorDiv((int) Math.floor(x + radius), cell);
        int cz0 = Math.floorDiv((int) Math.floor(z - radius), cell);
        int cz1 = Math.floorDiv((int) Math.floor(z + radius), cell);
        List<int[]> cells = new ArrayList<>();
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                double dx = Math.max(0, Math.max(cx * (double) cell - x, x - (cx + 1) * (double) cell));
                double dz = Math.max(0, Math.max(cz * (double) cell - z, z - (cz + 1) * (double) cell));
                if (Math.hypot(dx, dz) <= radius) {
                    cells.add(new int[] {cx, cz});
                }
            }
        }
        cells.sort(Comparator.comparingDouble(c -> Math.hypot((c[0] + 0.5) * cell - x, (c[1] + 0.5) * cell - z)));
        for (int[] c : cells) {
            for (FluxVortex vortex : inCell(plan, c[0], c[1], time, time + ahead + 1, settings, budget)) {
                if (Math.hypot(vortex.x() - x, vortex.z() - z) <= radius) {
                    out.add(vortex);
                }
            }
        }
        return out;
    }

    /** The vortices of cell {@code (cx, cz)} alive at any moment from {@code from} to {@code to}. */
    public static List<FluxVortex> inCell(HalcyonPlan plan, int cx, int cz, long from, long to) {
        return inCell(plan, cx, cz, from, to, Settings.now(), new int[] {Integer.MAX_VALUE});
    }

    private static List<FluxVortex> inCell(HalcyonPlan plan, int cx, int cz, long from, long to, Settings settings, int[] budget) {
        List<FluxVortex> out = new ArrayList<>();
        long offset = offset(plan, cx, cz);
        long firstHour = Math.floorDiv(from + offset, (long) HOUR_TICKS);
        long lastHour = Math.floorDiv(to - 1 + offset, (long) HOUR_TICKS);
        for (long hour = firstHour; hour <= lastHour; hour++) {
            for (Turn turn : turns(plan, cx, cz, hour, settings)) {
                if (!turn.forms() || turn.end() <= from || turn.start() >= to) {
                    continue;
                }
                Optional<int[]> spot = spot(plan, cx, cz, hour, turn.index(), settings, budget);
                if (spot == null || spot.isEmpty()) {
                    continue;
                }
                int[] at = spot.get();
                long id = Noise.mix(plan.seed, cx, cz, hour * 128 + turn.index(), 0x51DE);
                out.add(new FluxVortex(id, at[0] + 0.5, at[1] + 0.5, at[2], settings.top(), turn.start(), turn.end()));
            }
        }
        return out;
    }

    /** Where the cell's hours start: a moment of its own, so cells don't change all at once. */
    private static long offset(HalcyonPlan plan, int cx, int cz) {
        return Math.floorMod(Noise.mix(plan.seed, cx, cz, 0, 0x0FF5), (long) HOUR_TICKS);
    }

    /**
     * The cell's turns in one of its hours, one after another: each lasts a random while, then a random cooldown; a
     * turn that wouldn't end inside the hour isn't taken (the cell rests until the next hour's first turn).
     */
    static List<Turn> turns(HalcyonPlan plan, int cx, int cz, long hour, Settings settings) {
        List<Turn> out = new ArrayList<>();
        long start = hour * HOUR_TICKS - offset(plan, cx, cz);
        long end = start + HOUR_TICKS;
        long cursor = start + between(Noise.mix(plan.seed, cx, cz, hour, 0x6A9), 0, settings.maxCooldown());
        for (int n = 0; n < 128; n++) {
            long h = Noise.mix(plan.seed, cx, cz, hour * 128 + n, 0x7A5E);
            int life = (int) between(h, settings.minLife(), settings.maxLife());
            if (cursor + life > end) {
                break;
            }
            boolean forms = ((h >>> 40) & 0xFFFF) / 65536.0 < settings.chance();
            out.add(new Turn(n, cursor, cursor + life, forms));
            cursor += life + between(h >>> 20, settings.minCooldown(), settings.maxCooldown());
        }
        return out;
    }

    /** A number from {@code min} to {@code max} (both included), from a hash. */
    private static long between(long hash, long min, long max) {
        return min + Math.floorMod(hash, max - min + 1);
    }

    /** The spot of the cell's turn, looked for once; null if not looked for yet and the budget has run out. */
    private static Optional<int[]> spot(HalcyonPlan plan, int cx, int cz, long hour, int turn, Settings settings, int[] budget) {
        String key = plan.seed + ":" + cx + ":" + cz + ":" + hour + ":" + turn + ":" + settings.cell() + ":" + settings.top();
        synchronized (SPOTS) {
            Optional<int[]> known = SPOTS.get(key);
            if (known != null) {
                return known;
            }
        }
        if (budget[0] <= 0) {
            return null;
        }
        budget[0]--;
        Optional<int[]> found = choose(plan, cx, cz, hour * 128 + turn, settings);
        synchronized (SPOTS) {
            SPOTS.put(key, found);
        }
        return found;
    }

    private static Optional<int[]> choose(HalcyonPlan plan, int cx, int cz, long turn, Settings settings) {
        int cell = settings.cell();
        int best = Integer.MAX_VALUE;
        int[] chosen = null;
        int span = Math.max(1, cell - 2 * MARGIN);
        for (int k = 0; k < CANDIDATES; k++) {
            long hk = Noise.mix(plan.seed, cx, cz, turn, 1000 + k);
            int x = cx * cell + MARGIN + (int) Math.floorMod(hk, (long) span);
            int z = cz * cell + MARGIN + (int) Math.floorMod(hk >>> 24, (long) span);
            if (!open(plan, x, z, settings.top())) {
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
                chosen = new int[] {x, z, Math.max(ground + 1, SEA)};
            }
        }
        return Optional.ofNullable(chosen);
    }

    /**
     * Where a vortex could stand at the column, if anywhere: its foot's height, by the same rules as the cells' own
     * (clear of floating islands, rock and the landmarks, not on a mountain).
     */
    public static OptionalInt standAt(HalcyonPlan plan, int x, int z) {
        int top = setting(SkyseamConfig.VORTEX_TOP_Y);
        if (!open(plan, x, z, top)) {
            return OptionalInt.empty();
        }
        int ground = (int) Math.round(plan.landHeight(x, z));
        if (ground > SEA + MOUNTAIN) {
            return OptionalInt.empty();
        }
        return OptionalInt.of(Math.max(ground + 1, SEA));
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
