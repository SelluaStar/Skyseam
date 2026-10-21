package com.selluastar.skyseam.halcyon.wind;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.world.halcyon.HalcyonRegion;
import com.selluastar.skyseam.world.halcyon.plan.HalcyonPlan;
import com.selluastar.skyseam.world.halcyon.plan.Landmarks;
import com.selluastar.skyseam.world.halcyon.shape.SolidFeature;

import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Where the Halcyon's wind lanes run (docs/DECISIONS.md K110, replacing K109's network between the landmarks): all over
 * the dimension, from the world's seed alone, so every server and restart agree and nothing is saved.
 *
 * <p>The Halcyon is cut into square cells ({@code wind_lanes.spacing} blocks wide); each holds one lane or none
 * ({@code wind_lanes.chance}), worked out the first time something asks for a lane near it (on a background thread,
 * for the server's ticks). A lane is 600 to 1,300
 * blocks long, its middle somewhere in its cell. Its heading follows a slow swirl of the sky's air across the whole
 * Halcyon, a little to either side, so neighbouring lanes run much the same way, like weather, rather than every which
 * way; the wind in it blows one way along it by day and back through the Hush.
 *
 * <p>Each lane wanders in long, gentle swings, sideways and up and down, at about the height ships fly (190 to 310), and
 * swings round the Obelisk to one side. Then its heights are settled: through the gaps clear of the ground and of the
 * floating islands and rock stacks in its way (under them, between them or over them) that keep it nearest that way all
 * along, climbing and dropping no steeper than one block in three, and under 392 unless that is the only way by.
 */
public final class WindLaneNetwork {
    /** How long a lane is, at least and at most. */
    private static final double SHORTEST = 600;
    private static final double LONGEST = 1300;
    /** How high a lane would be at its ends, at least and at most. */
    private static final double LOWEST = 190;
    private static final double HIGHEST = 310;
    /** Across how many blocks the sky's air turns right round, and how far a lane may head off from it either way. */
    private static final double SWIRL = 2600;
    private static final double VEER = Math.toRadians(30);
    /** Points along a lane are this far apart before it is traced. */
    private static final double SPACING = 48;
    /**
     * How far a lane wanders sideways and up and down, and over how many blocks it swings each way: long, gentle swings,
     * as shorter ones read as kinks and, seen end-on from afar, as tangles.
     */
    private static final double WANDER = 48;
    private static final double WANDER_LENGTH = 300;
    private static final double BOB = 12;
    private static final double BOB_LENGTH = 260;
    /**
     * How far from the Obelisk's axis a lane passes it, swinging out round it (it stands 238 blocks tall) over this many
     * blocks before and after, at most: clear of it by its clearance, and ten blocks more.
     */
    private static final double SKIRT = Landmarks.OBELISK_REACH + WindLane.RADIUS + 8 + 10;
    private static final double SKIRT_SPAN = 160;
    /** How steeply a lane may climb or drop, blocks up per block along. */
    private static final double SLOPE = 0.33;
    /**
     * How far a lane keeps from the ground, floating islands and rock (from its middle); and the height it keeps under,
     * unless that is the only way by.
     */
    private static final double CLEARANCE = WindLane.RADIUS + 8;
    private static final double CEILING = 392;
    /** The very highest a lane's middle may be (the sky ends at 512). */
    private static final double TOP = 470;
    /** Heights are weighed this many blocks apart when a lane's way through the gaps is chosen. */
    private static final double STEP_Y = 4;
    /** Cells' lanes kept worked out, most recently asked for kept longest. */
    private static final int CACHE = 1024;
    private static final Map<String, List<WindLane>> CELLS = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<WindLane>> eldest) {
            return size() > CACHE;
        }
    };
    /** Cells being worked out on a background thread. */
    private static final Set<String> PENDING = new HashSet<>();

    private WindLaneNetwork() {}

    /** Lanes near somewhere: those worked out, and whether every cell that could hold one has been. */
    public record Near(List<WindLane> lanes, boolean complete) {}

    /**
     * The lanes that come within {@code reach} of {@code (x, z)} (across, not up), in cells {@code spacing} wide. With
     * {@code now}, every cell's lane is worked out first, on this thread; without, cells not yet worked out are set to be
     * on a background thread and left out until they are (so a server tick never waits on them).
     */
    public static Near near(HalcyonPlan plan, double x, double z, double reach, int spacing, double chance, boolean now) {
        // A lane reaches half its length (and its wandering) out of its cell.
        double out = reach + LONGEST / 2 + WANDER + SKIRT;
        int x0 = Mth.floor((x - out) / spacing);
        int x1 = Mth.floor((x + out) / spacing);
        int z0 = Mth.floor((z - out) / spacing);
        int z1 = Mth.floor((z + out) / spacing);
        List<WindLane> lanes = new ArrayList<>();
        boolean complete = true;
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                List<WindLane> here = now ? cell(plan, cx, cz, spacing, chance) : ready(plan, cx, cz, spacing, chance);
                if (here == null) {
                    complete = false;
                    continue;
                }
                for (WindLane lane : here) {
                    AABB box = lane.bounds();
                    if (x >= box.minX - reach && x <= box.maxX + reach && z >= box.minZ - reach && z <= box.maxZ + reach) {
                        lanes.add(lane);
                    }
                }
            }
        }
        return new Near(lanes, complete);
    }

    /** The lane of one cell, or none, worked out now if it hasn't been. */
    public static List<WindLane> cell(HalcyonPlan plan, int cx, int cz, int spacing, double chance) {
        String key = key(plan, cx, cz, spacing, chance);
        synchronized (CELLS) {
            List<WindLane> lanes = CELLS.get(key);
            if (lanes != null) {
                return lanes;
            }
        }
        List<WindLane> lanes = build(plan, cx, cz, spacing, chance);
        synchronized (CELLS) {
            CELLS.put(key, lanes);
        }
        return lanes;
    }

    /** The lane of one cell, or none, if it has been worked out; else null, and it is set to be on a background thread. */
    private static List<WindLane> ready(HalcyonPlan plan, int cx, int cz, int spacing, double chance) {
        String key = key(plan, cx, cz, spacing, chance);
        synchronized (CELLS) {
            List<WindLane> lanes = CELLS.get(key);
            if (lanes != null || !PENDING.add(key)) {
                return lanes;
            }
        }
        CompletableFuture.supplyAsync(() -> build(plan, cx, cz, spacing, chance), Util.backgroundExecutor()).whenComplete((lanes, failed) -> {
            synchronized (CELLS) {
                PENDING.remove(key);
                if (lanes != null) {
                    CELLS.put(key, lanes);
                }
            }
            if (failed != null) {
                Skyseam.LOGGER.error("Skyseam: couldn't work out the wind lane of cell {}, {}", cx, cz, failed);
            }
        });
        return null;
    }

    private static String key(HalcyonPlan plan, int cx, int cz, int spacing, double chance) {
        return System.identityHashCode(plan) + ":" + spacing + ":" + chance + ":" + cx + ":" + cz;
    }

    private static List<WindLane> build(HalcyonPlan plan, int cx, int cz, int spacing, double chance) {
        if (plan.noise.hash(cx, 0, cz, 0x1A40) >= chance) {
            return List.of();
        }
        // An id of its own, from its cell: positive, so it never meets a summoned lane's (negative).
        int id = (int) (Math.floorMod(plan.noise.cellHash(cx, cz, 0x1A41), Integer.MAX_VALUE - 1) + 1);
        double mx = (cx + 0.15 + 0.7 * plan.noise.hash(cx, 1, cz, 0x1A42)) * spacing;
        double mz = (cz + 0.15 + 0.7 * plan.noise.hash(cx, 2, cz, 0x1A42)) * spacing;
        // The sky's air swirls slowly across the Halcyon; each lane runs with it, give or take.
        double heading = Math.PI * 2 * plan.noise.fbm(mx + 7000, mz - 3000, SWIRL, 2)
                + (plan.noise.hash(cx, 3, cz, 0x1A42) - 0.5) * 2 * VEER;
        double length = Mth.lerp(plan.noise.hash(cx, 4, cz, 0x1A42), SHORTEST, LONGEST);
        double dx = Math.cos(heading) * length / 2;
        double dz = Math.sin(heading) * length / 2;
        Vec3 a = new Vec3(mx - dx, Mth.lerp(plan.noise.hash(cx, 5, cz, 0x1A42), LOWEST, HIGHEST), mz - dz);
        Vec3 b = new Vec3(mx + dx, Mth.lerp(plan.noise.hash(cx, 6, cz, 0x1A42), LOWEST, HIGHEST), mz + dz);
        List<Vec3> points = path(plan, id, a, b, plan.noise.cellHash(cx, cz, 0x1A43));
        settle(plan, points);
        double pace = 0.85 + 0.3 * plan.noise.hash(cx, 7, cz, 0x1A42);
        return List.of(new WindLane(id, name(plan, mx, mz, heading), points, pace));
    }

    /** What a lane is called, for commands and the log: "the north-east lane over the Petalwash". */
    private static String name(HalcyonPlan plan, double x, double z, double heading) {
        String[] ways = {"east", "south-east", "south", "south-west", "west", "north-west", "north", "north-east"};
        String way = ways[Math.floorMod(Math.round(heading / (Math.PI / 4)), 8)];
        return "the " + way + " lane over " + place(plan.region(x, z));
    }

    private static String place(HalcyonRegion region) {
        return switch (region) {
            case SPINDLE -> "the Spindle";
            case HUSH -> "the Hush";
            case CIRRUS_REEFS -> "the Cirrus Reefs";
            case PETALWASH -> "the Petalwash";
            case MIRROR_SHOALS -> "the Mirror Shoals";
            case WRECKFIELDS -> "the Wreckfields";
            case SUNREACH_CLIFFS -> "the Sunreach Cliffs";
            case CAPWOOD -> "the Capwood";
            case ELDERWOOD -> "the Elderwood";
            case UNDERBLOOM -> "the Underbloom";
        };
    }

    /**
     * The points of a lane from {@code a} to {@code b}, wandering a little sideways and up and down on its way, at the
     * heights it would be at if nothing stood in its way ({@link #settle} finds the heights it can be at). Where its way
     * runs near the Obelisk it swings round it instead, to one side.
     */
    static List<Vec3> path(HalcyonPlan plan, int id, Vec3 a, Vec3 b, long seed) {
        double length = Math.hypot(b.x - a.x, b.z - a.z);
        int count = Math.max(2, (int) Math.ceil(length / SPACING) + 1);
        double aheadX = (b.x - a.x) / Math.max(1, length);
        double aheadZ = (b.z - a.z) / Math.max(1, length);
        double sideX = -aheadZ;
        double sideZ = aheadX;
        // Where along its way it passes the Obelisk nearest, how far to the side the Obelisk stands from its way there, and
        // so how far to the side the lane swings to pass it SKIRT off (away from it; a spoke from the Arrival runs straight
        // at it, and goes round whichever side the seed picks).
        double gx = plan.landmarks.gate.getX() + 0.5;
        double gz = plan.landmarks.gate.getZ() + 0.5;
        double passing = (gx - a.x) * aheadX + (gz - a.z) * aheadZ;
        double beside = (gx - a.x) * sideX + (gz - a.z) * sideZ;
        // It swings out and back in no further than its nearer end, which stays where it is.
        double span = Math.min(SKIRT_SPAN, Math.min(passing, length - passing));
        double swing = 0;
        if (span > SKIRT && Math.abs(beside) < SKIRT) {
            double away = Math.abs(beside) > 1 ? -Math.signum(beside) : plan.noise.hash(id, 1, 0, 0x0B1E) < 0.5 ? -1 : 1;
            swing = beside + away * SKIRT;
        }
        List<Vec3> points = new ArrayList<>(count);
        for (int k = 0; k < count; k++) {
            double t = k / (double) (count - 1);
            // Wandering fades out towards each end, and while it swings round the Obelisk.
            double ends = Math.sin(Math.PI * t);
            double round = swing == 0 ? 0 : bell((t * length - passing) / span);
            double wander = WANDER * ends * plan.noise.fbm(t * length / WANDER_LENGTH + id * 17.3, seed % 1000 + id * 31.1, 1, 1);
            double sideways = wander * (1 - round) + swing * round;
            double bob = BOB * ends * plan.noise.fbm(t * length / BOB_LENGTH - id * 9.7, seed % 977 - id * 13.9, 1, 1);
            points.add(new Vec3(Mth.lerp(t, a.x, b.x) + sideX * sideways, Mth.lerp(t, a.y, b.y) + bob, Mth.lerp(t, a.z, b.z) + sideZ * sideways));
        }
        return points;
    }

    /** 1 at 0, easing to 0 at -1 and 1 and beyond. */
    private static double bell(double u) {
        if (Math.abs(u) >= 1) {
            return 0;
        }
        double c = Math.cos(Math.PI / 2 * u);
        return c * c;
    }

    /**
     * Settles a lane's heights, each point's height as given being where it would be. At each point it may pass through
     * any gap clear of the ground (and the Obelisk) and of the floating islands and rock there: under them, between them
     * or over them. It takes the gaps that keep it nearest where it would be all along, going from one to the next no
     * steeper than the slope allows, and keeps under the ceiling unless that is the only way by; then, within them, it is
     * as near where it would be as it may be.
     */
    private static void settle(HalcyonPlan plan, List<Vec3> points) {
        int count = points.size();
        double[] want = new double[count];
        double[] along = new double[count];
        for (int k = 0; k < count; k++) {
            want[k] = points.get(k).y;
            along[k] = k == 0 ? 0 : along[k - 1] + flat(points.get(k - 1), points.get(k));
        }
        // What stands at each point, and a quarter and half of the way to each neighbour, so nothing slips between them.
        List<double[][]> gaps = new ArrayList<>(count);
        for (int k = 0; k < count; k++) {
            Vec3 point = points.get(k);
            double floor = floor(plan, point.x, point.z);
            List<SolidFeature> solids = new ArrayList<>(solidsNear(plan, point.x, point.z));
            for (int side = -1; side <= 1; side += 2) {
                int j = k + side;
                if (j < 0 || j >= count) {
                    continue;
                }
                Vec3 other = points.get(j);
                for (double part = 0.25; part <= 0.5; part += 0.25) {
                    double x = Mth.lerp(part, point.x, other.x);
                    double z = Mth.lerp(part, point.z, other.z);
                    floor = Math.max(floor, floor(plan, x, z));
                    solids.addAll(solidsNear(plan, x, z));
                }
            }
            gaps.add(gaps(floor, solids));
        }
        int[] chosen = choose(gaps, want, along);
        double[] low = new double[count];
        double[] high = new double[count];
        for (int k = 0; k < count; k++) {
            low[k] = gaps.get(k)[chosen[k]][0];
            high[k] = gaps.get(k)[chosen[k]][1];
        }
        // As near where it would be as it may be, climbing to and dropping from where it must be no steeper than the
        // slope allows (where it can't be both, the higher).
        for (int k = 0; k < count; k++) {
            double least = Double.NEGATIVE_INFINITY;
            double most = Double.POSITIVE_INFINITY;
            for (int j = 0; j < count; j++) {
                double apart = Math.abs(along[j] - along[k]);
                least = Math.max(least, low[j] - SLOPE * apart);
                most = Math.min(most, high[j] + SLOPE * apart);
            }
            Vec3 point = points.get(k);
            points.set(k, new Vec3(point.x, Math.max(least, Math.min(want[k], most)), point.z));
        }
    }

    /** The gaps from {@code floor} up to the top of the sky that keep a lane's middle clear of every solid, lowest first. */
    private static double[][] gaps(double floor, List<SolidFeature> solids) {
        List<double[]> blocked = new ArrayList<>();
        for (SolidFeature solid : solids) {
            blocked.add(new double[] {solid.bottom() - CLEARANCE, solid.top() + CLEARANCE});
        }
        blocked.sort(Comparator.comparingDouble(span -> span[0]));
        List<double[]> free = new ArrayList<>();
        double from = floor;
        for (double[] span : blocked) {
            if (from >= TOP) {
                break;
            }
            if (span[0] > from) {
                free.add(new double[] {from, Math.min(span[0], TOP)});
            }
            from = Math.max(from, span[1]);
        }
        if (from < TOP) {
            free.add(new double[] {from, TOP});
        }
        if (free.isEmpty()) {
            // Nowhere clear: as low as it may be over all of it.
            free.add(new double[] {from, from});
        }
        return free.toArray(new double[0][]);
    }

    /**
     * Which gap a lane passes through at each point: the way along that strays least from where it would be (and least
     * over the ceiling), climbing and dropping no steeper than the slope allows. Worked out over its heights every
     * {@link #STEP_Y} blocks, every way along at once, so a lane coming off a stack is never led into a low gap it can't
     * drop to in time, nor kept low where it must start climbing to clear the next. Only if there is no way at all does it
     * take a step steeper than the slope, the smallest it can.
     */
    private static int[] choose(List<double[][]> gaps, double[] want, double[] along) {
        int count = gaps.size();
        int levels = (int) (TOP / STEP_Y) + 1;
        double[][] cost = new double[count][levels];
        int[][] from = new int[count][levels];
        for (int k = 0; k < count; k++) {
            double reach = k == 0 ? 0 : SLOPE * (along[k] - along[k - 1]);
            for (int i = 0; i < levels; i++) {
                double y = i * STEP_Y;
                if (gapAt(gaps.get(k), y) < 0) {
                    cost[k][i] = Double.POSITIVE_INFINITY;
                    continue;
                }
                double here = Math.abs(y - want[k]) + 2 * Math.max(0, y - CEILING);
                if (k == 0) {
                    cost[k][i] = here;
                    continue;
                }
                double best = Double.POSITIVE_INFINITY;
                int steps = (int) (reach / STEP_Y);
                for (int j = Math.max(0, i - steps); j <= Math.min(levels - 1, i + steps); j++) {
                    if (cost[k - 1][j] < best) {
                        best = cost[k - 1][j];
                        from[k][i] = j;
                    }
                }
                if (best == Double.POSITIVE_INFINITY) {
                    // No way here within the slope: the smallest step steeper than it, at a cost no other way could reach.
                    for (int j = 0; j < levels; j++) {
                        double c = cost[k - 1][j] + 1.0e7 + 1.0e5 * (Math.abs(j - i) * STEP_Y - reach);
                        if (c < best) {
                            best = c;
                            from[k][i] = j;
                        }
                    }
                }
                cost[k][i] = best + here;
            }
        }
        int end = 0;
        for (int i = 1; i < levels; i++) {
            if (cost[count - 1][i] < cost[count - 1][end]) {
                end = i;
            }
        }
        int[] chosen = new int[count];
        for (int k = count - 1, i = end; k >= 0; k--) {
            chosen[k] = Math.max(0, gapAt(gaps.get(k), i * STEP_Y));
            i = from[k][i];
        }
        return chosen;
    }

    /** Which of a point's gaps holds height {@code y} (to within half a step, for a gap narrower than one), or -1. */
    private static int gapAt(double[][] gaps, double y) {
        for (int g = 0; g < gaps.length; g++) {
            if (y >= gaps[g][0] - STEP_Y / 2 && y <= gaps[g][1] + STEP_Y / 2) {
                return g;
            }
        }
        return -1;
    }

    private static List<SolidFeature> solidsNear(HalcyonPlan plan, double x, double z) {
        int reach = (int) WindLane.RADIUS + 8;
        return plan.solidsNear((int) x - reach, (int) z - reach, (int) x + reach, (int) z + reach);
    }

    /**
     * How high a lane's middle must be over the column at {@code (x, z)}: clear of the ground round it, and (should it ever
     * come this near, which swinging round it keeps it from) over the Obelisk.
     */
    private static double floor(HalcyonPlan plan, double x, double z) {
        double need = Double.NEGATIVE_INFINITY;
        double r = WindLane.RADIUS;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                need = Math.max(need, Math.max(plan.landHeight((int) (x + dx * r), (int) (z + dz * r)), 64) + CLEARANCE);
            }
        }
        BlockPos gate = plan.landmarks.gate;
        if (Math.hypot(x - gate.getX() - 0.5, z - gate.getZ() - 0.5) < Landmarks.OBELISK_REACH + CLEARANCE) {
            need = Math.max(need, gate.getY() + Landmarks.TOP_ABOVE_TERRACE + CLEARANCE);
        }
        return need;
    }

    private static double flat(Vec3 a, Vec3 b) {
        return Math.hypot(a.x - b.x, a.z - b.z);
    }
}
