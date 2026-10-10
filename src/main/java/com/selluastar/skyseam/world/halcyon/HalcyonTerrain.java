package com.selluastar.skyseam.world.halcyon;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.selluastar.skyseam.registry.SkyseamBlocks;
import com.selluastar.skyseam.world.HalcyonLayout;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * The Halcyon's land (spec section 7, "Terrain generation", docs/DECISIONS.md K59): floating islands in three size
 * classes over the Mirror Sea, with the fixed Anchorage island and the ring of islands around the Spindle.
 *
 * <p>Each size class has its own jittered grid; a cell holds at most one island, at a spot and size picked from the
 * world seed. An island is a plateau over a tapering underside. Its outline is warped by 2D noise and its body hollowed
 * a little by 3D noise. Its shape and blocks follow the ring it stands in ({@link HalcyonRegion}): low pale reefs rising
 * from the sea in the Mirror Shoals, wide meadows in Petalwash, stacked cloud reefs in the Cirrus Reefs, tall spires
 * in the Hush. No island enters the air kept around the Arrival Lane or the line of sight from it to the Anchorage.
 *
 * <p>Pure and thread-safe: build it from a seed and ask it for columns. The chunk generator and GameTests both do.
 */
public final class HalcyonTerrain {
    /** Island size classes (spec: small 20 to 40 blocks across, medium 60 to 120, large 150 to 300). */
    public enum SizeClass {
        SMALL(72, 10, 20, 0.55),
        MEDIUM(180, 30, 60, 0.6),
        LARGE(420, 75, 150, 0.5);

        final int cell;
        final double minRadius;
        final double maxRadius;
        final double chance;

        SizeClass(int cell, double minRadius, double maxRadius, double chance) {
            this.cell = cell;
            this.minRadius = minRadius;
            this.maxRadius = maxRadius;
            this.chance = chance;
        }
    }

    /** The kind of island, which sets its shape and blocks. */
    public enum Kind { MEADOW, REEF, CLOUD, SPIRE, WRECK, RING, ANCHORAGE }

    /**
     * One island: its centre column, radius, the height of its top, how deep its underside reaches, its kind and size
     * class, and a seed for its details.
     */
    public record Island(double x, double z, double radius, int top, double depth, Kind kind, SizeClass size, long seed) {
        /** The furthest any part of it reaches from its centre, after its outline is warped. */
        public double reach() {
            return radius * (1 + EDGE_WARP) + 2;
        }

        public int lowest() {
            return kind == Kind.REEF ? HalcyonLayout.SEA_FLOOR - 4 : Mth.floor(top - depth * 1.3) - 4;
        }

        public int highest() {
            return top + 10;
        }
    }

    /** Something a column's blocks are written to: a chunk, or an array for a height query. */
    @FunctionalInterface
    public interface ColumnWriter {
        void set(int y, BlockState state);
    }

    private static final double EDGE_WARP = 0.25;
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState GRASS = Blocks.GRASS_BLOCK.defaultBlockState();
    private static final BlockState DIRT = Blocks.DIRT.defaultBlockState();
    private static final BlockState COARSE_DIRT = Blocks.COARSE_DIRT.defaultBlockState();
    private static final BlockState CALCITE = Blocks.CALCITE.defaultBlockState();

    private final long seed;
    private final double[] density;
    private final SimplexNoise edge;
    private final SimplexNoise surface;
    private final SimplexNoise underside;
    private final SimplexNoise hollows;
    private final List<Island> fixed;
    private final ConcurrentHashMap<Long, Optional<Island>> cells = new ConcurrentHashMap<>();

    /**
     * @param seed    the world's seed for the Halcyon's terrain
     * @param density how many islands each size class makes, small, medium and large: 1 is the default
     */
    public HalcyonTerrain(long seed, double... density) {
        this.seed = seed;
        this.density = density.length == 3 ? density.clone() : new double[] {1, 1, 1};
        RandomSource random = new XoroshiroRandomSource(seed);
        this.edge = new SimplexNoise(random);
        this.surface = new SimplexNoise(random);
        this.underside = new SimplexNoise(random);
        this.hollows = new SimplexNoise(random);
        this.fixed = fixedIslands();
    }

    public long seed() {
        return seed;
    }

    // ---- Where the islands are ---------------------------------------------------------------------------------

    /** The Anchorage, and the ring of islands that orbit the Spindle. */
    private List<Island> fixedIslands() {
        List<Island> islands = new ArrayList<>();
        islands.add(new Island(HalcyonLayout.ANCHORAGE.getX() + 0.5, HalcyonLayout.ANCHORAGE.getZ() + 0.5, HalcyonLayout.ANCHORAGE_RADIUS,
                HalcyonLayout.ANCHORAGE.getY() - 1, 88, Kind.ANCHORAGE, SizeClass.MEDIUM, seed ^ 0xA9C40EL));
        int ring = 10;
        for (int k = 0; k < ring; k++) {
            RandomSource random = new XoroshiroRandomSource(seed ^ (0x5B1DL * (k + 1)));
            double angle = Math.PI * 2 * k / ring + 0.15 * (random.nextDouble() - 0.5);
            double distance = 150 + 20 * random.nextDouble();
            double radius = 20 + 10 * random.nextDouble();
            int top = 228 + random.nextInt(44);
            islands.add(new Island(Math.cos(angle) * distance, Math.sin(angle) * distance, radius, top, radius * 0.8, Kind.RING,
                    SizeClass.MEDIUM, random.nextLong()));
        }
        return List.copyOf(islands);
    }

    /** Every island whose outline may reach into the rectangle from (minX, minZ) to (maxX, maxZ), inclusive. */
    public List<Island> islandsNear(int minX, int minZ, int maxX, int maxZ) {
        List<Island> found = new ArrayList<>();
        for (Island island : fixed) {
            if (touches(island, minX, minZ, maxX, maxZ)) {
                found.add(island);
            }
        }
        for (SizeClass size : SizeClass.values()) {
            double reach = size.maxRadius * (1 + EDGE_WARP) + 2;
            int cx0 = Mth.floor((minX - reach) / size.cell);
            int cx1 = Mth.floor((maxX + reach) / size.cell);
            int cz0 = Mth.floor((minZ - reach) / size.cell);
            int cz1 = Mth.floor((maxZ + reach) / size.cell);
            for (int cx = cx0; cx <= cx1; cx++) {
                for (int cz = cz0; cz <= cz1; cz++) {
                    islandIn(size, cx, cz).filter(island -> touches(island, minX, minZ, maxX, maxZ)).ifPresent(found::add);
                }
            }
        }
        return found;
    }

    private static boolean touches(Island island, int minX, int minZ, int maxX, int maxZ) {
        double nx = Mth.clamp(island.x, minX, maxX + 1);
        double nz = Mth.clamp(island.z, minZ, maxZ + 1);
        return Mth.square(nx - island.x) + Mth.square(nz - island.z) <= Mth.square(island.reach());
    }

    /** The island in one grid cell of a size class, if it has one. */
    public Optional<Island> islandIn(SizeClass size, int cx, int cz) {
        long key = ((long) cx & 0x3FFFFFFFL) | (((long) cz & 0x3FFFFFFFL) << 30) | ((long) size.ordinal() << 60);
        return cells.computeIfAbsent(key, k -> Optional.ofNullable(makeIsland(size, cx, cz)));
    }

    private Island makeIsland(SizeClass size, int cx, int cz) {
        RandomSource random = new XoroshiroRandomSource(seed ^ mix(cx, cz, size.ordinal()));
        if (random.nextDouble() > size.chance * density[size.ordinal()]) {
            return null;
        }
        double x = (cx + 0.15 + 0.7 * random.nextDouble()) * size.cell;
        double z = (cz + 0.15 + 0.7 * random.nextDouble()) * size.cell;
        double fromCentre = HalcyonLayout.radiusOf(x, z);
        HalcyonRegion ring = HalcyonRegion.ringAt(x, z);
        if (ring == HalcyonRegion.SPINDLE || fromCentre > HalcyonLayout.RADIUS - 20) {
            return null;
        }
        double radius = Mth.lerp(random.nextDouble(), size.minRadius, size.maxRadius);
        Kind kind;
        int top;
        double depth;
        switch (ring) {
            case MIRROR_SHOALS -> {
                // Low pale reefs standing in the sea, like the rocks in reference image 2.
                kind = Kind.REEF;
                radius *= size == SizeClass.LARGE ? 0.35 : 0.6;
                top = HalcyonLayout.SEA_LEVEL + 2 + random.nextInt(size == SizeClass.SMALL ? 8 : 16);
                depth = top - HalcyonLayout.SEA_FLOOR;
            }
            case CIRRUS_REEFS -> {
                kind = Kind.CLOUD;
                top = 210 + random.nextInt(106);
                depth = radius * (0.3 + 0.15 * random.nextDouble());
            }
            case HUSH -> {
                kind = Kind.SPIRE;
                radius *= 0.55;
                top = 190 + random.nextInt(126);
                depth = radius * (2.2 + random.nextDouble());
            }
            case WRECKFIELDS -> {
                kind = Kind.WRECK;
                radius *= 0.7;
                top = 145 + random.nextInt(60);
                depth = radius * 0.6;
            }
            default -> {
                kind = Kind.MEADOW;
                // Small islands also drift low, in the Underbloom band, to be flown under.
                top = size == SizeClass.SMALL && random.nextInt(3) == 0 ? 112 + random.nextInt(28) : 150 + random.nextInt(80);
                depth = radius * (0.6 + 0.25 * random.nextDouble());
            }
        }
        if (kind != Kind.REEF) {
            depth = Math.min(depth, top - HalcyonLayout.SEA_LEVEL - 8);
        }
        Island island = new Island(x, z, radius, top, depth, kind, size, random.nextLong());
        return isKeptClear(island) ? null : island;
    }

    /** True if the island would block the Arrival Lane, the view from it to the Anchorage, or the Anchorage itself. */
    private static boolean isKeptClear(Island island) {
        double lx = HalcyonLayout.ARRIVAL_LANE.x;
        double lz = HalcyonLayout.ARRIVAL_LANE.z;
        double ax = HalcyonLayout.ANCHORAGE.getX() + 0.5;
        double az = HalcyonLayout.ANCHORAGE.getZ() + 0.5;
        double reach = island.reach();
        boolean vertically = island.highest() > HalcyonLayout.ARRIVAL_CLEARANCE.minY && island.lowest() < HalcyonLayout.ARRIVAL_CLEARANCE.maxY;
        double laneHalf = (HalcyonLayout.ARRIVAL_CLEARANCE.maxX - HalcyonLayout.ARRIVAL_CLEARANCE.minX) / 2;
        if (vertically && Math.hypot(island.x - lx, island.z - lz) < reach + laneHalf * Math.sqrt(2)) {
            return true;
        }
        if (Math.hypot(island.x - ax, island.z - az) < reach + HalcyonLayout.ANCHORAGE_RADIUS * (1 + EDGE_WARP) + 24) {
            return true;
        }
        // The view from the lane to the Obelisk: nothing tall between them.
        if (island.highest() > HalcyonLayout.ANCHORAGE.getY() - 15) {
            double t = Mth.clamp(((island.x - lx) * (ax - lx) + (island.z - lz) * (az - lz)) / (Mth.square(ax - lx) + Mth.square(az - lz)), 0, 1);
            double px = lx + t * (ax - lx);
            double pz = lz + t * (az - lz);
            return Math.hypot(island.x - px, island.z - pz) < reach + 24;
        }
        return false;
    }

    private static long mix(long x, long z, long salt) {
        long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return h;
    }

    // ---- Columns ---------------------------------------------------------------------------------------------------

    /**
     * Writes one column: the Mirror Sea and its floor, then every island in {@code near} that reaches it. Only solid
     * blocks and water are written; everything else is left as it is (air).
     */
    public void column(int x, int z, List<Island> near, ColumnWriter out) {
        if (HalcyonLayout.radiusOf(x, z) < HalcyonLayout.SEA_RADIUS) {
            for (int y = HalcyonLayout.SEA_FLOOR - 4; y < HalcyonLayout.SEA_FLOOR - 1; y++) {
                out.set(y, SkyseamBlocks.STILLSTONE.get().defaultBlockState());
            }
            out.set(HalcyonLayout.SEA_FLOOR - 1, CALCITE);
            for (int y = HalcyonLayout.SEA_FLOOR; y < HalcyonLayout.SEA_LEVEL; y++) {
                out.set(y, WATER);
            }
        }
        for (Island island : near) {
            islandColumn(island, x, z, out);
        }
    }

    /** The top of the highest island at a column, or -1 if no island is there. */
    public int islandTop(int x, int z, List<Island> near) {
        int[] top = {-1};
        for (Island island : near) {
            islandColumn(island, x, z, (y, state) -> {
                if (!state.isAir() && !state.is(Blocks.WATER) && y > top[0]) {
                    top[0] = y;
                }
            });
        }
        return top[0];
    }

    private void islandColumn(Island island, int x, int z, ColumnWriter out) {
        double dx = x + 0.5 - island.x;
        double dz = z + 0.5 - island.z;
        // The Anchorage keeps a rounder rim, so the Obelisk's plaza always stands on ground.
        double warp = 1 + (island.kind == Kind.ANCHORAGE ? 0.08 : EDGE_WARP) * edge.getValue(x / 28.0 + island.seed % 97, z / 28.0);
        double t = Math.sqrt(dx * dx + dz * dz) / (island.radius * warp);
        if (t >= 1) {
            return;
        }
        double bumps = surface.getValue(x / 36.0, z / 36.0 + island.seed % 89);
        double topY;
        double bottomY;
        switch (island.kind) {
            case ANCHORAGE -> {
                // Flat where the Obelisk stands, falling away in a cliff at the rim.
                topY = island.top - (t > 0.82 ? (t - 0.82) / 0.18 * 7 : 0);
                bottomY = island.top - island.depth * Math.pow(1 - t, 0.45) * (0.85 + 0.15 * underside.getValue(x / 20.0, z / 20.0));
            }
            case REEF -> {
                topY = island.top - 9 * t * t + 2 * bumps;
                bottomY = HalcyonLayout.SEA_FLOOR - 1;
            }
            case CLOUD -> {
                topY = island.top + 5 * (1 - t * t) * (0.6 + 0.4 * bumps);
                bottomY = topY - 3 - island.depth * Math.pow(1 - t, 0.8) * (0.7 + 0.3 * underside.getValue(x / 14.0, z / 14.0));
            }
            case SPIRE -> {
                // A needle: concave flanks rising to a point, over a short root hanging under its base.
                double rise = island.depth * 0.65;
                topY = island.top - rise * Math.pow(t, 0.6) + 2 * bumps;
                bottomY = island.top - rise - island.depth * 0.35 * Math.pow(1 - t, 0.7);
            }
            default -> {
                topY = island.top + 3 * bumps - (t > 0.78 ? (t - 0.78) / 0.22 * 6 : 0);
                bottomY = topY - 2 - island.depth * Math.pow(1 - t, 0.65) * (0.75 + 0.5 * (0.5 + 0.5 * underside.getValue(x / 18.0, z / 18.0)));
            }
        }
        int top = Mth.floor(topY);
        int bottom = Mth.ceil(bottomY);
        for (int y = bottom; y <= top; y++) {
            int fromTop = top - y;
            // Hollows and hanging pockets in the underside, never through the surface; the Hush's spires are deeply hollowed.
            double hollowAbove = island.kind == Kind.SPIRE ? 0.5 : 0.62;
            if (fromTop > 4 && island.kind != Kind.REEF && hollows.getValue(x / 16.0, y / 12.0, z / 16.0) > hollowAbove - 0.25 * t) {
                continue;
            }
            out.set(y, block(island, fromTop, y));
        }
    }

    /** The block at {@code fromTop} blocks below an island's surface. */
    private static BlockState block(Island island, int fromTop, int y) {
        BlockState stone = SkyseamBlocks.STILLSTONE.get().defaultBlockState();
        return switch (island.kind) {
            case CLOUD -> switch ((int) Math.floorMod(island.seed, 3)) {
                case 0 -> SkyseamBlocks.CLOUD.get().defaultBlockState();
                case 1 -> fromTop < 2 ? SkyseamBlocks.ROSE_CLOUD.get().defaultBlockState() : SkyseamBlocks.CLOUD.get().defaultBlockState();
                default -> fromTop < 2 ? SkyseamBlocks.DUSK_CLOUD.get().defaultBlockState() : SkyseamBlocks.CLOUD.get().defaultBlockState();
            };
            case REEF -> fromTop == 0 && y >= HalcyonLayout.SEA_LEVEL - 1 ? CALCITE : stone;
            case SPIRE -> fromTop == 0 ? CALCITE : stone;
            case WRECK -> fromTop == 0 ? COARSE_DIRT : fromTop < 3 ? DIRT : stone;
            default -> fromTop == 0 ? GRASS : fromTop < 3 ? DIRT : stone;
        };
    }
}
