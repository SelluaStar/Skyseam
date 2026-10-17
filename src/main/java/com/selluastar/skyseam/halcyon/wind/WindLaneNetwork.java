package com.selluastar.skyseam.halcyon.wind;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import com.selluastar.skyseam.world.halcyon.HalcyonRegion;
import com.selluastar.skyseam.world.halcyon.plan.HalcyonPlan;
import com.selluastar.skyseam.world.halcyon.plan.Landmarks;
import com.selluastar.skyseam.world.halcyon.shape.SolidFeature;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Where the Halcyon's wind lanes run (docs/DECISIONS.md K109), from the world's seed alone, so the server works them
 * out once and every restart agrees.
 *
 * <p>The landmarks they join: the Arrival, where ships come in (the Obelisk just inside it), the heart of one region of
 * each of the archipelago's five kinds (the Petalwash, the Cirrus Reefs, the Hush, the Wreckfields and the Mirror
 * Shoals), and the Spindle, whose lanes end at its ring of floating islands rather than at the massif. A ring of lanes
 * goes round the archipelago from landmark to landmark, sweeping round each without a break (passing a little inside it
 * where the ring turns sharply), and a spoke branches off it at each, running in to the Spindle: twelve in all.
 *
 * <p>Each lane leaves its landmark at the height ships fly there (the Arrival's 240, about 60 above a region's land,
 * the Spindle's ring at 280) and wanders a little on its way, sideways and up and down in long, gentle swings. Then its
 * heights are settled: through the gaps clear of the ground and of the floating islands and rock stacks in its way
 * (under them, between them or over them) that keep it nearest that way all along, climbing and dropping no steeper than
 * one block in three, and under 392 unless that is the only way by. The Obelisk, 238 blocks tall, they swing round to one
 * side.
 */
public final class WindLaneNetwork {
    /** The archipelago's region kinds, each given a landmark at the heart of one of its regions. */
    private static final HalcyonRegion[] REGIONS = {HalcyonRegion.PETALWASH, HalcyonRegion.CIRRUS_REEFS, HalcyonRegion.HUSH,
            HalcyonRegion.WRECKFIELDS, HalcyonRegion.MIRROR_SHOALS};
    /** How far out from the middle a region's landmark is looked for. */
    private static final double REGION_RADIUS = 760;
    /** Where the Spindle's lanes end: just past its ring of floating islands. */
    private static final double SPINDLE_RADIUS = 360;
    private static final double SPINDLE_Y = 280;
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
    /**
     * The ring's corner at each landmark is rounded off over this many of its points either side (each about
     * {@link #SPACING} apart), smoothed this many times: at its sharpest the ring turns a right angle or more there.
     */
    private static final int ROUND_POINTS = 4;
    private static final int ROUND_PASSES = 6;
    private static final Map<HalcyonPlan, List<WindLane>> CACHE = new WeakHashMap<>();

    private WindLaneNetwork() {}

    /** A landmark a lane joins. */
    record Landmark(String name, Vec3 at, double bearing) {}

    /** The world's lanes, worked out once per plan. */
    public static synchronized List<WindLane> of(HalcyonPlan plan) {
        return CACHE.computeIfAbsent(plan, WindLaneNetwork::build);
    }

    private static List<WindLane> build(HalcyonPlan plan) {
        List<Landmark> ring = new ArrayList<>();
        Landmarks landmarks = plan.landmarks;
        ring.add(new Landmark("the Arrival", landmarks.arrival, Math.atan2(landmarks.arrival.z, landmarks.arrival.x)));
        double arrivalBearing = Math.atan2(landmarks.outZ, landmarks.outX);
        for (int k = 0; k < REGIONS.length; k++) {
            double bearing = arrivalBearing + (k + 1) * Math.PI * 2 / (REGIONS.length + 1);
            Vec3 from = new Vec3(Math.cos(bearing) * REGION_RADIUS, 0, Math.sin(bearing) * REGION_RADIUS);
            Vec3 heart = plan.deepIn(REGIONS[k], from);
            if (heart == null || Math.hypot(heart.x, heart.z) < SPINDLE_RADIUS + 200) {
                continue;
            }
            double y = Mth.clamp(Math.max(plan.landHeight((int) heart.x, (int) heart.z), 64) + 60, 200, 300);
            ring.add(new Landmark("the " + name(REGIONS[k]), new Vec3(heart.x, y, heart.z), Math.atan2(heart.z, heart.x)));
        }
        ring.sort(Comparator.comparingDouble(Landmark::bearing));
        List<WindLane> lanes = new ArrayList<>();
        long seed = plan.noise.cellHash(0x57, 0x1D, 0x1A4E);
        int n = ring.size();
        // Where the ring passes each landmark, for its spoke to start from.
        List<Vec3> passes = new ArrayList<>();
        ring.forEach(landmark -> passes.add(landmark.at()));
        if (n > 1) {
            // The ring as one closed course, each lane's points but its last (the next lane's first). It sweeps round each
            // landmark rather than turning on the spot there, passing a little inside it where it turns sharply, and its
            // heights are settled all round it together, so it runs on unbroken from each lane into the next.
            List<Vec3> loop = new ArrayList<>();
            int[] starts = new int[n + 1];
            for (int k = 0; k < n; k++) {
                List<Vec3> path = path(plan, k, ring.get(k).at(), ring.get((k + 1) % n).at(), seed);
                starts[k] = loop.size();
                loop.addAll(path.subList(0, path.size() - 1));
            }
            starts[n] = loop.size();
            if (n > 2) {
                for (int k = 0; k < n; k++) {
                    round(loop, starts[k]);
                }
            }
            settle(plan, loop, true);
            int size = loop.size();
            for (int k = 0; k < n; k++) {
                passes.set(k, loop.get(starts[k]));
            }
            for (int k = 0; k < n; k++) {
                List<Vec3> points = new ArrayList<>(loop.subList(starts[k], starts[k + 1]));
                points.add(loop.get(starts[k + 1] % size));
                Vec3 before = loop.get(Math.floorMod(starts[k] - 1, size));
                Vec3 after = loop.get((starts[k + 1] + 1) % size);
                lanes.add(new WindLane(k, ring.get(k).name() + " to " + ring.get((k + 1) % n).name(), points, pace(plan, k), before, after));
            }
        }
        // Each landmark's spoke branches off the ring where it passes the landmark, rather than crossing it.
        for (int k = 0; k < n; k++) {
            Landmark landmark = ring.get(k);
            Vec3 from = passes.get(k);
            Vec3 spindle = new Vec3(Math.cos(landmark.bearing()) * SPINDLE_RADIUS, SPINDLE_Y, Math.sin(landmark.bearing()) * SPINDLE_RADIUS);
            // Spokes alternate, some blowing in to the Spindle and some out from it.
            boolean in = lanes.size() % 2 == 0;
            int id = lanes.size();
            List<Vec3> points = path(plan, id, in ? from : spindle, in ? spindle : from, seed);
            settle(plan, points, false);
            lanes.add(new WindLane(id, in ? landmark.name() + " to the Spindle" : "the Spindle to " + landmark.name(), points, pace(plan, id)));
        }
        return List.copyOf(lanes);
    }

    /** Rounds off the ring's corner at point {@code corner} of its closed course, smoothing the points either side of it. */
    private static void round(List<Vec3> loop, int corner) {
        int size = loop.size();
        int reach = Math.min(ROUND_POINTS, (size - 1) / 2);
        // From the last unmoved point before it to the first after, the corner in the middle.
        Vec3[] window = new Vec3[2 * reach + 1];
        for (int i = 0; i < window.length; i++) {
            window[i] = loop.get(Math.floorMod(corner - reach + i, size));
        }
        for (int pass = 0; pass < ROUND_PASSES; pass++) {
            Vec3[] next = window.clone();
            for (int i = 1; i + 1 < window.length; i++) {
                next[i] = window[i - 1].add(window[i].scale(2)).add(window[i + 1]).scale(0.25);
            }
            window = next;
        }
        for (int i = 0; i < window.length; i++) {
            loop.set(Math.floorMod(corner - reach + i, size), window[i]);
        }
    }

    private static double pace(HalcyonPlan plan, int id) {
        return 0.85 + 0.3 * plan.noise.hash(id, 0, 0, 0x1A4E);
    }

    private static String name(HalcyonRegion region) {
        return switch (region) {
            case PETALWASH -> "Petalwash";
            case CIRRUS_REEFS -> "Cirrus Reefs";
            case HUSH -> "Hush";
            case WRECKFIELDS -> "Wreckfields";
            case MIRROR_SHOALS -> "Mirror Shoals";
            default -> region.biome();
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
            // Wandering fades out towards each end, so the lane meets its landmarks, and while it swings round the Obelisk.
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
     * as near where it would be as it may be. A closed course (the ring) runs on from its last point to its first.
     */
    private static void settle(HalcyonPlan plan, List<Vec3> points, boolean closed) {
        int count = points.size();
        double[] want = new double[count];
        double[] along = new double[count];
        for (int k = 0; k < count; k++) {
            want[k] = points.get(k).y;
            along[k] = k == 0 ? 0 : along[k - 1] + flat(points.get(k - 1), points.get(k));
        }
        double around = closed ? along[count - 1] + flat(points.get(count - 1), points.get(0)) : 0;
        // What stands at each point, and a quarter and half of the way to each neighbour, so nothing slips between them.
        List<double[][]> gaps = new ArrayList<>(count);
        for (int k = 0; k < count; k++) {
            Vec3 point = points.get(k);
            double floor = floor(plan, point.x, point.z);
            List<SolidFeature> solids = new ArrayList<>(solidsNear(plan, point.x, point.z));
            for (int side = -1; side <= 1; side += 2) {
                int j = closed ? Math.floorMod(k + side, count) : k + side;
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
        int[] chosen = choose(gaps, want, along, around, closed);
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
                if (closed) {
                    apart = Math.min(apart, around - apart);
                }
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
     * over the ceiling), from gap to gap no steeper than the slope allows where it can be. A closed course is tried from
     * each gap at its first point, coming back round into it.
     */
    private static int[] choose(List<double[][]> gaps, double[] want, double[] along, double around, boolean closed) {
        int count = gaps.size();
        int[] best = null;
        double bestCost = Double.POSITIVE_INFINITY;
        for (int first = 0; first < (closed ? gaps.get(0).length : 1); first++) {
            double[][] cost = new double[count][];
            int[][] from = new int[count][];
            for (int k = 0; k < count; k++) {
                cost[k] = new double[gaps.get(k).length];
                from[k] = new int[gaps.get(k).length];
                Arrays.fill(cost[k], Double.POSITIVE_INFINITY);
            }
            for (int g = 0; g < gaps.get(0).length; g++) {
                if (!closed || g == first) {
                    cost[0][g] = stray(gaps.get(0)[g], want[0]);
                }
            }
            for (int k = 1; k < count; k++) {
                double reach = SLOPE * (along[k] - along[k - 1]);
                for (int g = 0; g < gaps.get(k).length; g++) {
                    for (int h = 0; h < gaps.get(k - 1).length; h++) {
                        double c = cost[k - 1][h] + jump(gaps.get(k - 1)[h], gaps.get(k)[g], reach) + stray(gaps.get(k)[g], want[k]);
                        if (c < cost[k][g]) {
                            cost[k][g] = c;
                            from[k][g] = h;
                        }
                    }
                }
            }
            for (int g = 0; g < gaps.get(count - 1).length; g++) {
                double c = cost[count - 1][g] + (closed ? jump(gaps.get(count - 1)[g], gaps.get(0)[first], SLOPE * (around - along[count - 1])) : 0);
                if (c < bestCost) {
                    bestCost = c;
                    best = new int[count];
                    best[count - 1] = g;
                    for (int k = count - 1; k > 0; k--) {
                        best[k - 1] = from[k][best[k]];
                    }
                }
            }
        }
        return best;
    }

    /** How far a lane in this gap strays from where it would be, counting what is over the ceiling three times. */
    private static double stray(double[] gap, double want) {
        double y = Mth.clamp(want, gap[0], gap[1]);
        return Math.abs(y - want) + 2 * Math.max(0, y - CEILING);
    }

    /** Nothing if a lane can go from one gap to the next within {@code reach} up or down, else a great deal. */
    private static double jump(double[] a, double[] b, double reach) {
        double apart = Math.max(b[0] - (a[1] + reach), (a[0] - reach) - b[1]);
        return apart <= 0 ? 0 : 1000 + 100 * apart;
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
