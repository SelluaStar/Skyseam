package com.selluastar.skyseam.halcyon.wind;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * One wind lane (docs/DECISIONS.md K109): a stream of moving air between two landmarks of the Halcyon (spec "Wind
 * Lanes": "visible ribbons of petals and light motes between landmarks"). Its middle is a smooth curve through
 * {@link #points()}, traced every {@link #STEP} blocks; it reaches {@link #RADIUS} blocks out from that middle. The air
 * runs from its first point to its last, or back while the lanes are turned ({@link WindLanes#flow}).
 *
 * <p>Shared by the server (which carries ships and riders along it) and the client (which draws it), so both trace the
 * same curve from the same points.
 */
public final class WindLane {
    /** How far the moving air reaches from the lane's middle, in blocks. */
    public static final double RADIUS = 10;
    /** The curve is traced every this many blocks. */
    public static final double STEP = 4;

    private final int id;
    private final String name;
    private final List<Vec3> points;
    /** Where the lane would come from before its first point and go after its last, if it runs on (a ring), else null. */
    @Nullable
    private final Vec3 before;
    @Nullable
    private final Vec3 after;
    /** Each lane's own share of the wind's speed, so they don't all blow alike. */
    private final double pace;
    private final Vec3[] samples;
    private final Vec3[] tangents;
    private final double[] along;
    private final AABB bounds;

    public WindLane(int id, String name, List<Vec3> points, double pace) {
        this(id, name, points, pace, null, null);
    }

    /**
     * A lane that runs on from another before its first point and into another after its last: its curve leaves and
     * meets them in the same direction, so where they join it flows straight through.
     */
    public WindLane(int id, String name, List<Vec3> points, double pace, @Nullable Vec3 before, @Nullable Vec3 after) {
        this.id = id;
        this.name = name;
        this.points = List.copyOf(points);
        this.pace = pace;
        this.before = before;
        this.after = after;
        int count = 1;
        for (int k = 0; k + 1 < points.size(); k++) {
            count += Math.max(1, (int) Math.ceil(points.get(k).distanceTo(points.get(k + 1)) / STEP));
        }
        samples = new Vec3[count];
        int n = 0;
        for (int k = 0; k + 1 < points.size(); k++) {
            Vec3 p0 = k > 0 ? points.get(k - 1) : before != null ? before : points.get(0);
            Vec3 p1 = points.get(k);
            Vec3 p2 = points.get(k + 1);
            Vec3 p3 = k + 2 < points.size() ? points.get(k + 2) : after != null ? after : points.get(points.size() - 1);
            int steps = Math.max(1, (int) Math.ceil(p1.distanceTo(p2) / STEP));
            for (int s = 0; s < steps; s++) {
                samples[n++] = catmullRom(p0, p1, p2, p3, s / (double) steps);
            }
        }
        samples[n] = points.get(points.size() - 1);
        tangents = new Vec3[count];
        along = new double[count];
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        for (int k = 0; k < count; k++) {
            Vec3 ahead = samples[Math.min(count - 1, k + 1)].subtract(samples[Math.max(0, k - 1)]);
            tangents[k] = ahead.lengthSqr() < 1.0e-9 ? new Vec3(1, 0, 0) : ahead.normalize();
            along[k] = k == 0 ? 0 : along[k - 1] + samples[k].distanceTo(samples[k - 1]);
            minX = Math.min(minX, samples[k].x);
            minY = Math.min(minY, samples[k].y);
            minZ = Math.min(minZ, samples[k].z);
            maxX = Math.max(maxX, samples[k].x);
            maxY = Math.max(maxY, samples[k].y);
            maxZ = Math.max(maxZ, samples[k].z);
        }
        bounds = new AABB(minX, minY, minZ, maxX, maxY, maxZ).inflate(RADIUS);
    }

    private static Vec3 catmullRom(Vec3 p0, Vec3 p1, Vec3 p2, Vec3 p3, double t) {
        double t2 = t * t;
        double t3 = t2 * t;
        return new Vec3(spline(p0.x, p1.x, p2.x, p3.x, t, t2, t3), spline(p0.y, p1.y, p2.y, p3.y, t, t2, t3),
                spline(p0.z, p1.z, p2.z, p3.z, t, t2, t3));
    }

    private static double spline(double a, double b, double c, double d, double t, double t2, double t3) {
        return 0.5 * (2 * b + (c - a) * t + (2 * a - 5 * b + 4 * c - d) * t2 + (3 * b - a - 3 * c + d) * t3);
    }

    public int id() {
        return id;
    }

    /** What it joins, for commands and the log: "the Arrival to the Spindle". */
    public String name() {
        return name;
    }

    /** The points its middle passes through, first to last. */
    public List<Vec3> points() {
        return points;
    }

    /** Where it would come from before its first point, if it runs on from another lane; else null. */
    @Nullable
    public Vec3 before() {
        return before;
    }

    /** Where it would go after its last point, if it runs on into another lane; else null. */
    @Nullable
    public Vec3 after() {
        return after;
    }

    /** Its own share of the wind's speed (about 0.85 to 1.15). */
    public double pace() {
        return pace;
    }

    /** How many points its traced middle has. */
    public int size() {
        return samples.length;
    }

    public Vec3 sample(int index) {
        return samples[index];
    }

    /** The way the lane runs at a traced point (first to last), as a unit vector. */
    public Vec3 tangent(int index) {
        return tangents[index];
    }

    /** How far along the lane a traced point is, in blocks from its first. */
    public double along(int index) {
        return along[index];
    }

    public double length() {
        return along[along.length - 1];
    }

    /** Everything within {@link #RADIUS} of its middle. */
    public AABB bounds() {
        return bounds;
    }

    /** The nearest point of the lane's middle to a position: which traced point, how far, and the way the lane runs there. */
    public record Near(WindLane lane, int index, double distance) {
        public Vec3 point() {
            return lane.sample(index);
        }

        public Vec3 tangent() {
            return lane.tangent(index);
        }

        /** How deep in the lane, 1 in its middle easing to 0 at its edge. */
        public double depth() {
            double t = Math.max(0, 1 - distance / RADIUS);
            return t * t * (3 - 2 * t);
        }
    }

    /** The nearest point of the middle to {@code at}, if {@code at} is within {@code reach} of it; else null. */
    @Nullable
    public Near nearest(Vec3 at, double reach) {
        if (!bounds.inflate(reach - RADIUS).contains(at)) {
            return null;
        }
        // Every fourth point first, then the points round the best.
        int best = -1;
        double bestSq = Double.MAX_VALUE;
        for (int k = 0; k < samples.length; k += 4) {
            double d = samples[k].distanceToSqr(at);
            if (d < bestSq) {
                bestSq = d;
                best = k;
            }
        }
        int from = Math.max(0, best - 4);
        int to = Math.min(samples.length - 1, best + 4);
        for (int k = from; k <= to; k++) {
            double d = samples[k].distanceToSqr(at);
            if (d < bestSq) {
                bestSq = d;
                best = k;
            }
        }
        double distance = Math.sqrt(bestSq);
        return distance <= reach ? new Near(this, best, distance) : null;
    }
}
