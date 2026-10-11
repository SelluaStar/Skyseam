package com.selluastar.skyseam.world.halcyon.plan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongFunction;

import com.selluastar.skyseam.config.SkyseamConfig;
import com.selluastar.skyseam.world.HalcyonLayout;
import com.selluastar.skyseam.world.halcyon.HalcyonRegion;
import com.selluastar.skyseam.world.halcyon.shape.CaveFeature;
import com.selluastar.skyseam.world.halcyon.shape.Feature;
import com.selluastar.skyseam.world.halcyon.shape.FloatingIsland;
import com.selluastar.skyseam.world.halcyon.shape.Isle;
import com.selluastar.skyseam.world.halcyon.shape.LandFeature;
import com.selluastar.skyseam.world.halcyon.shape.MainIsland;
import com.selluastar.skyseam.world.halcyon.shape.SeaRock;
import com.selluastar.skyseam.world.halcyon.shape.SolidFeature;
import com.selluastar.skyseam.world.halcyon.shape.SpireCluster;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;

/**
 * The Halcyon's plan for one world seed (docs/DECISIONS.md K75 to K78, K81 to K85): where its landmarks are, which
 * biome lies where, and every placed piece, so the chunk generator only has to ask "what reaches this chunk?".
 *
 * <p>The world has no edge for now (K81). It changes as you go out from the Spindle: the archipelago, about 45 % land,
 * gives way past 1,300 blocks to the outer lands, where islands grow bigger, taller and closer together until, past
 * 2,600, the land is mostly mountainous Highlands cut by sea channels ({@link #outwardness}).
 *
 * <p>Pieces are placed in layers, each on its own jittered grid, made lazily one grid cell at a time and cached (a
 * bounded cache, {@link CellCache}), the same every time for the same seed. Each layer checks only the layers before
 * it:
 *
 * <ol>
 * <li>The landmarks: the Arrival Lane, the Sundered Obelisk and the Spindle ({@link Landmarks}).</li>
 * <li>Main islands, one per 380-block cell or none, each with 5 to 10 satellite isles round it.</li>
 * <li>Scattered isles, between the main islands.</li>
 * <li>Needle-spire clusters, mostly in the Hush.</li>
 * <li>Sea rocks (stacks, leaning slabs, boulders and arches), only where there is sea; stone blades on Elderwood land.</li>
 * <li>Floating islands in four size classes, at least 25 blocks clear of the land under them; the largest, the
 * Horncrowns, only in the outer lands.</li>
 * <li>Caves in the land (grottoes, waterfall halls, sinkholes, geodes), and twisting tunnels from noise.</li>
 * <li>Giant trees and mushrooms ({@link Flora}).</li>
 * </ol>
 *
 * Nothing tall stands round the Arrival Lane, between it and the Obelisk, or round the Obelisk ({@link Landmarks}).
 */
public final class HalcyonPlan {
    public static final int SEA = HalcyonLayout.SEA_LEVEL;
    private static final int MAIN_CELL = 380;
    /** As far as the biggest main island (a Highland of radius 650) can reach from its cell. */
    private static final int MAIN_REACH = 1150;
    private static final int ISLE_CELL = 130;
    private static final int ISLE_REACH = 200;
    private static final int SPIRE_CELL = 170;
    private static final int SPIRE_REACH = 110;
    private static final int ROCK_CELL = 52;
    private static final int ROCK_REACH = 40;
    private static final int BLADE_CELL = 150;
    private static final int BLADE_REACH = 80;
    /** Floating islands: small, medium, large and colossal. */
    private static final int[] FLOAT_CELL = {64, 150, 520, 900};
    private static final int[] FLOAT_REACH = {40, 160, 300, 520};
    private static final int CAVE_CELL = 150;
    private static final int CAVE_REACH = 75;
    private static final int FLORA_CELL = 36;
    /** The lowest a floating island's underside may hang, and the highest its top may reach. */
    public static final int FLOAT_FLOOR = 165;
    public static final int FLOAT_CEILING = 400;
    public static final int FLOAT_CLEARANCE = 25;
    /** How many cells each layer keeps cached. */
    private static final int CACHE = 4096;

    private static final Map<Long, HalcyonPlan> PLANS = new LinkedHashMap<>(8, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, HalcyonPlan> eldest) {
            return size() > 4;
        }
    };

    public final long seed;
    public final Noise noise;
    public final Landmarks landmarks;
    public final RegionMap regions;
    private final double mainDensity;
    private final double isleDensity;
    private final double rockDensity;
    private final double spireDensity;
    private final double floatDensity;
    private final CellCache<List<LandFeature>> mains = new CellCache<>(CACHE, this::makeMain);
    private final CellCache<Optional<LandFeature>> isles = new CellCache<>(CACHE, this::makeIsle);
    private final CellCache<Optional<LandFeature>> spires = new CellCache<>(CACHE, this::makeSpire);
    private final CellCache<Optional<SolidFeature>> rocks = new CellCache<>(CACHE * 4, this::makeRock);
    private final CellCache<Optional<SolidFeature>> blades = new CellCache<>(CACHE, this::makeBlade);
    private final List<CellCache<Optional<SolidFeature>>> floats = List.of(new CellCache<>(CACHE * 2, k -> makeFloating(0, k)),
            new CellCache<>(CACHE, k -> makeFloating(1, k)), new CellCache<>(CACHE, k -> makeFloating(2, k)),
            new CellCache<>(CACHE, k -> makeFloating(3, k)));
    private final CellCache<Optional<CaveFeature>> caves = new CellCache<>(CACHE, this::makeCave);
    private final CellCache<Optional<Flora.Big>> flora = new CellCache<>(CACHE * 4, this::makeFlora);

    private HalcyonPlan(long seed) {
        this.seed = seed;
        this.noise = new Noise(seed);
        this.landmarks = new Landmarks(seed, noise);
        this.regions = new RegionMap(seed, noise, landmarks.gate.getX(), landmarks.gate.getZ());
        this.mainDensity = setting(SkyseamConfig.MAIN_ISLAND_DENSITY);
        this.isleDensity = setting(SkyseamConfig.ISLE_DENSITY);
        this.rockDensity = setting(SkyseamConfig.SEA_ROCK_DENSITY);
        this.spireDensity = setting(SkyseamConfig.SPIRE_DENSITY);
        this.floatDensity = setting(SkyseamConfig.FLOATING_ISLAND_DENSITY);
    }

    /** The plan for a seed, shared by everything that asks for the same seed. */
    public static HalcyonPlan of(long seed) {
        synchronized (PLANS) {
            return PLANS.computeIfAbsent(seed, HalcyonPlan::new);
        }
    }

    /** A new plan for a seed, not shared: for tests that compare two plans of the same seed. */
    public static HalcyonPlan fresh(long seed) {
        return new HalcyonPlan(seed);
    }

    private static double setting(net.neoforged.neoforge.common.ModConfigSpec.DoubleValue value) {
        try {
            return value.get();
        } catch (IllegalStateException notLoaded) {
            return value.getDefault();
        }
    }

    // ---- Asking the plan ---------------------------------------------------------------------------------------------

    /** The biome region at a column. */
    public HalcyonRegion region(double x, double z) {
        return regions.at(x, z);
    }

    /**
     * How far into the outer lands a column is: 0 in the archipelago (inside about 1,300 blocks), rising to 1 in the
     * Highlands (past about 2,600), along a wavy line. Land grows bigger, taller and denser with it.
     */
    public double outwardness(double x, double z) {
        return Noise.smoothstep(1300, 2600, Math.hypot(x, z) + 150 * noise.fbm(x - 11000, z + 4000, 700, 2));
    }

    /** Every piece of land that may reach the box. */
    public List<LandFeature> landsNear(int x0, int z0, int x1, int z1) {
        List<LandFeature> out = new ArrayList<>();
        if (landmarks.spindle.touches(x0, z0, x1, z1)) {
            out.add(landmarks.spindle);
        }
        forCells(x0, z0, x1, z1, MAIN_CELL, MAIN_REACH, mains::get, out);
        forOptionalCells(x0, z0, x1, z1, ISLE_CELL, ISLE_REACH, isles::get, out);
        forOptionalCells(x0, z0, x1, z1, SPIRE_CELL, SPIRE_REACH, spires::get, out);
        return out;
    }

    /** Every solid piece (sea rocks, blades and floating islands) that may reach the box. */
    public List<SolidFeature> solidsNear(int x0, int z0, int x1, int z1) {
        List<SolidFeature> out = new ArrayList<>();
        for (FloatingIsland island : landmarks.spindleRing) {
            if (island.touches(x0, z0, x1, z1)) {
                out.add(island);
            }
        }
        forOptionalCells(x0, z0, x1, z1, ROCK_CELL, ROCK_REACH, rocks::get, out);
        forOptionalCells(x0, z0, x1, z1, BLADE_CELL, BLADE_REACH, blades::get, out);
        for (int size = 0; size < 4; size++) {
            forOptionalCells(x0, z0, x1, z1, FLOAT_CELL[size], FLOAT_REACH[size], floats.get(size)::get, out);
        }
        return out;
    }

    /** Every cave that may reach the box. */
    public List<CaveFeature> cavesNear(int x0, int z0, int x1, int z1) {
        List<CaveFeature> out = new ArrayList<>();
        forOptionalCells(x0, z0, x1, z1, CAVE_CELL, CAVE_REACH, caves::get, out);
        return out;
    }

    /** Every giant tree or mushroom that may reach the box. */
    public List<Flora.Big> floraNear(int x0, int z0, int x1, int z1) {
        List<Flora.Big> out = new ArrayList<>();
        for (int gx = Math.floorDiv(x0 - Flora.MAX_REACH, FLORA_CELL); gx <= Math.floorDiv(x1 + Flora.MAX_REACH, FLORA_CELL); gx++) {
            for (int gz = Math.floorDiv(z0 - Flora.MAX_REACH, FLORA_CELL); gz <= Math.floorDiv(z1 + Flora.MAX_REACH, FLORA_CELL); gz++) {
                flora.get(key(gx, gz)).filter(big -> big.touches(x0, z0, x1, z1)).ifPresent(out::add);
            }
        }
        return out;
    }

    /** One column's land: the highest of the given pieces, or the sea floor. */
    public void land(int x, int z, List<LandFeature> near, LandFeature.LandSample out) {
        out.reset();
        for (LandFeature feature : near) {
            if (x >= feature.minX && x <= feature.maxX && z >= feature.minZ && z <= feature.maxZ) {
                feature.sample(x, z, out);
            }
        }
        // Out in the outer lands, straits and sea channels wind between the land masses, so dense land never closes up.
        if (out.height > SEA - 30) {
            double cut = channel(x, z);
            if (cut > 0) {
                out.height = Mth.lerp(cut, out.height, Math.min(out.height, SEA - 12));
                out.shelf *= 1 - cut;
                if (cut > 0.5) {
                    out.surface = LandFeature.Surface.SAND;
                }
            }
        }
        if (out.height >= SEA) {
            // The sea floor never rises above the water, so on land it can't matter.
            return;
        }
        double floor = seaFloor(x, z, out.shelf);
        if (out.height < floor) {
            out.height = floor;
            out.surface = LandFeature.Surface.SAND;
        }
    }

    /**
     * The sea floor: a deep basin (Y 58 to 72) crossed by undersea ridges, with seamounts rising to reefs just under the
     * surface and long canyons cut down to Y 30; shallow lagoons in the Mirror Shoals, and stepped reef shelves rising
     * to the shallows round the land.
     */
    public double seaFloor(int x, int z, double shelf) {
        double floor = SEA - 24 - 14 * (0.5 + 0.5 * noise.fbm(x, z, 300, 3));
        double ridge = noise.ridged(x + 4000, z - 4000, 190, 3);
        floor += 26 * ridge * ridge * ridge;
        double mount = Math.max(0, noise.fbm(x - 7000, z + 7000, 150, 2) - 0.35) / 0.65;
        floor += 30 * mount * mount;
        floor -= 28 * Noise.smoothstep(0.07, 0, Math.abs(noise.fbm(x + 9000, z - 1000, 280, 2)));
        double shallow = shelf > 0.98 ? 0 : regions.blend(x, z, region -> region == HalcyonRegion.MIRROR_SHOALS ? 1
                : region == HalcyonRegion.SUNREACH_CLIFFS ? 0.4 : 0, 90);
        if (shallow > 0) {
            floor = Mth.lerp(shallow * 0.8, floor, SEA - 6 - 4 * noise.fbm(x, z + 3000, 60, 2));
        }
        floor = Mth.lerp(shelf * shelf, floor, SEA - 3);
        if (shelf > 0.2) {
            // Near land the slope steps down in reef ledges.
            double depth = SEA - 3 - floor;
            floor = Mth.lerp(Noise.smoothstep(0.2, 0.5, shelf), floor, SEA - 3 - Noise.terrace(depth, 4, 0.3));
        }
        floor = Math.min(floor, SEA - 3 - 3 * (0.5 + 0.5 * noise.fbm(x - 500, z, 40, 2)));
        return Math.max(18, floor + 1.2 * noise.fbm(x, z, 12, 2));
    }

    /**
     * How much a strait cuts a column down to the sea, 0 to 1: along the crests of two slow ridged noises, a network of
     * sea channels 60 to 150 blocks wide every few hundred blocks, and inland sounds where they cross, only in the outer
     * lands.
     */
    public double channel(int x, int z) {
        double out = outwardness(x, z);
        if (out <= 0) {
            return 0;
        }
        double straits = Noise.smoothstep(0.56, 0.74, noise.ridged(x + 13000, z - 9000, 900, 2));
        double sounds = Noise.smoothstep(0.62, 0.8, noise.ridged(x - 21000, z + 17000, 520, 2));
        return Math.max(straits, sounds) * Noise.smoothstep(0, 0.3, out);
    }

    /** The top of the land (or sea floor) at a column, without floating islands. */
    public double landHeight(int x, int z) {
        LandFeature.LandSample sample = new LandFeature.LandSample();
        land(x, z, landsNear(x, z, x, z), sample);
        return sample.height;
    }

    /** One column's land as a sample (height, surface, shelf). */
    public LandFeature.LandSample landSample(int x, int z) {
        LandFeature.LandSample sample = new LandFeature.LandSample();
        land(x, z, landsNear(x, z, x, z), sample);
        return sample;
    }

    /**
     * How much of a column twisting tunnels may take, 0 to 1: tunnels wind through some parts of the land and not
     * others, more of them in the outer lands. Only land more than 30 blocks above the sea is tunnelled.
     */
    public double tunnelAmount(int x, int z) {
        double patches = Noise.smoothstep(-0.1, 0.35, noise.fbm(x - 2000, z + 2000, 400, 2));
        return patches * (0.6 + 0.4 * outwardness(x, z));
    }

    /**
     * The tunnel field at a block: negative inside a tunnel. Two 3D noises cross zero along sheets; where both are near
     * zero at once runs a tube (spaghetti caves), its width set by {@code amount}.
     */
    public double tunnel(double x, double y, double z, double amount) {
        double a = noise.at(x / 46.0, y / 24.0, z / 46.0 + 300);
        double b = noise.at(x / 46.0 + 500, y / 24.0, z / 46.0);
        double width = 0.11 * amount;
        return a * a + b * b - width * width;
    }

    /** True if tunnels may run at a height in a column whose land tops out at {@code landTop}. */
    public static boolean tunnelRange(int y, double landTop) {
        return landTop > SEA + 30 && y > SEA + 1 && y < landTop - 3;
    }

    /** The biome at a block: its region, the Underbloom in a cave or under a floating island. */
    public HalcyonRegion biomeAt(int x, int y, int z, double landTop, List<SolidFeature> solids, List<CaveFeature> nearCaves, double tunnels) {
        return biomeAt(x, y, z, landTop, solids, nearCaves, tunnels, region(x, z));
    }

    /** {@link #biomeAt}, with the column's region already worked out (it is the same all the way up a column). */
    public HalcyonRegion biomeAt(int x, int y, int z, double landTop, List<SolidFeature> solids, List<CaveFeature> nearCaves, double tunnels,
            HalcyonRegion columnRegion) {
        if (y < landTop - 2) {
            for (CaveFeature cave : nearCaves) {
                if (x >= cave.minX && x <= cave.maxX && z >= cave.minZ && z <= cave.maxZ && cave.contains(x, y, z)) {
                    return cave.kind == CaveFeature.Kind.GROTTO ? columnRegion : HalcyonRegion.UNDERBLOOM;
                }
            }
            if (tunnels > 0 && tunnelRange(y, landTop) && tunnel(x, y, z, tunnels) < 0) {
                return HalcyonRegion.UNDERBLOOM;
            }
        }
        if (y > landTop + 6 && y < FLOAT_CEILING) {
            for (SolidFeature solid : solids) {
                if (solid instanceof FloatingIsland island && y < island.bottom() + 6 && y > island.bottom() - 30
                        && Math.hypot(x - island.cx, z - island.cz) < island.radius) {
                    return HalcyonRegion.UNDERBLOOM;
                }
            }
        }
        return columnRegion;
    }

    // ---- Layers ------------------------------------------------------------------------------------------------------

    private static long key(int gx, int gz) {
        return ((long) gx << 32) ^ (gz & 0xFFFFFFFFL);
    }

    private static int keyX(long key) {
        return (int) (key >> 32);
    }

    private static int keyZ(long key) {
        return (int) key;
    }

    private static <T> void forCells(int x0, int z0, int x1, int z1, int cell, int reach, LongFunction<List<? extends T>> get,
            List<? super T> out) {
        for (int gx = Math.floorDiv(x0 - reach, cell); gx <= Math.floorDiv(x1 + reach, cell); gx++) {
            for (int gz = Math.floorDiv(z0 - reach, cell); gz <= Math.floorDiv(z1 + reach, cell); gz++) {
                for (T feature : get.apply(key(gx, gz))) {
                    if (((Feature) feature).touches(x0, z0, x1, z1)) {
                        out.add(feature);
                    }
                }
            }
        }
    }

    private static <T extends Feature> void forOptionalCells(int x0, int z0, int x1, int z1, int cell, int reach,
            LongFunction<Optional<? extends T>> get, List<? super T> out) {
        for (int gx = Math.floorDiv(x0 - reach, cell); gx <= Math.floorDiv(x1 + reach, cell); gx++) {
            for (int gz = Math.floorDiv(z0 - reach, cell); gz <= Math.floorDiv(z1 + reach, cell); gz++) {
                get.apply(key(gx, gz)).filter(feature -> feature.touches(x0, z0, x1, z1)).ifPresent(out::add);
            }
        }
    }

    private RandomSource random(long key, int layer) {
        return new XoroshiroRandomSource(noise.cellHash(keyX(key), keyZ(key), layer));
    }

    /** A main island and its satellite isles, or nothing. */
    private List<LandFeature> makeMain(long key) {
        RandomSource random = random(key, 1);
        double cx = (keyX(key) + 0.15 + 0.7 * random.nextDouble()) * MAIN_CELL;
        double cz = (keyZ(key) + 0.15 + 0.7 * random.nextDouble()) * MAIN_CELL;
        double out = outwardness(cx, cz);
        if (random.nextDouble() > (0.92 + 0.08 * out) * mainDensity) {
            return List.of();
        }
        double fromCentre = Math.hypot(cx, cz);
        if (fromCentre < 380) {
            return List.of();
        }
        HalcyonRegion region = region(cx, cz);
        double radius = switch (region) {
            case MIRROR_SHOALS -> 100 + random.nextDouble() * 55;
            case WRECKFIELDS -> 135 + random.nextDouble() * 70;
            default -> 180 + random.nextDouble() * 115;
        };
        double relief = switch (region) {
            case HUSH -> 80 + random.nextDouble() * 50;
            case CIRRUS_REEFS -> 70 + random.nextDouble() * 40;
            case WRECKFIELDS, MIRROR_SHOALS -> 25 + random.nextDouble() * 30;
            case SUNREACH_CLIFFS, ELDERWOOD -> 70 + random.nextDouble() * 40;
            case CAPWOOD -> 40 + random.nextDouble() * 30;
            default -> 50 + random.nextDouble() * 35;
        };
        MainIsland.Archetype archetype = archetype(region, random.nextDouble());
        if (random.nextDouble() < out * 0.85 && region != HalcyonRegion.CAPWOOD && region != HalcyonRegion.MIRROR_SHOALS) {
            archetype = MainIsland.Archetype.HIGHLAND;
        }
        // Out in the outer lands everything grows: wider, and much taller, but the Capwood stays rolling.
        radius *= 1 + 0.3 * out;
        relief *= 1 + (region == HalcyonRegion.CAPWOOD ? 0.4 : 1.5) * out;
        if (archetype == MainIsland.Archetype.HIGHLAND) {
            radius = Math.max(radius, 210 + random.nextDouble() * 90);
            relief = Math.min(Math.max(relief, 110 + random.nextDouble() * 40), 175);
        }
        if (landmarks.nearObelisk(cx, cz, radius * 1.3) || landmarks.blocks(cx, cz, radius * 1.3, SEA, SEA + (int) relief + 60)) {
            return List.of();
        }
        MainIsland island = MainIsland.of(archetype, cx, cz, radius, relief, region, random.nextLong(), noise);
        List<LandFeature> group = new ArrayList<>();
        group.add(island);
        int satellites = 5 + random.nextInt(6);
        for (int k = 0; k < satellites; k++) {
            double a = random.nextDouble() * Math.PI * 2;
            double d = radius * (0.75 + random.nextDouble() * 0.5);
            double sx = cx + Math.cos(a) * d;
            double sz = cz + Math.sin(a) * d;
            Isle isle = isle(sx, sz, (28 + random.nextDouble() * 47) * (1 + 0.5 * out), region(sx, sz), out, random);
            if (!landmarks.blocks(sx, sz, isle.radius, SEA, isle.peak()) && !landmarks.nearObelisk(sx, sz, isle.radius)) {
                group.add(isle);
            }
        }
        return group;
    }

    private static MainIsland.Archetype archetype(HalcyonRegion region, double roll) {
        MainIsland.Archetype[] kinds;
        double[] weights;
        switch (region) {
            case HUSH -> {
                kinds = new MainIsland.Archetype[] {MainIsland.Archetype.SPIRE_MASSIF, MainIsland.Archetype.TWIN_RIDGE, MainIsland.Archetype.CALDERA};
                weights = new double[] {0.6, 0.2, 0.2};
            }
            case CIRRUS_REEFS -> {
                kinds = new MainIsland.Archetype[] {MainIsland.Archetype.TERRACED, MainIsland.Archetype.TWIN_RIDGE, MainIsland.Archetype.CALDERA,
                        MainIsland.Archetype.SPIRE_MASSIF};
                weights = new double[] {0.4, 0.25, 0.2, 0.15};
            }
            case WRECKFIELDS -> {
                kinds = new MainIsland.Archetype[] {MainIsland.Archetype.COVE, MainIsland.Archetype.CALDERA, MainIsland.Archetype.TERRACED};
                weights = new double[] {0.4, 0.3, 0.3};
            }
            case MIRROR_SHOALS -> {
                kinds = new MainIsland.Archetype[] {MainIsland.Archetype.COVE, MainIsland.Archetype.TERRACED};
                weights = new double[] {0.6, 0.4};
            }
            case SUNREACH_CLIFFS -> {
                kinds = new MainIsland.Archetype[] {MainIsland.Archetype.HIGHLAND, MainIsland.Archetype.TERRACED, MainIsland.Archetype.COVE,
                        MainIsland.Archetype.CALDERA};
                weights = new double[] {0.45, 0.25, 0.15, 0.15};
            }
            case CAPWOOD -> {
                kinds = new MainIsland.Archetype[] {MainIsland.Archetype.TERRACED, MainIsland.Archetype.TWIN_RIDGE, MainIsland.Archetype.COVE};
                weights = new double[] {0.5, 0.3, 0.2};
            }
            case ELDERWOOD -> {
                kinds = new MainIsland.Archetype[] {MainIsland.Archetype.HIGHLAND, MainIsland.Archetype.TWIN_RIDGE, MainIsland.Archetype.TERRACED};
                weights = new double[] {0.5, 0.3, 0.2};
            }
            default -> {
                kinds = new MainIsland.Archetype[] {MainIsland.Archetype.TERRACED, MainIsland.Archetype.COVE, MainIsland.Archetype.TWIN_RIDGE,
                        MainIsland.Archetype.CALDERA, MainIsland.Archetype.SPIRE_MASSIF};
                weights = new double[] {0.3, 0.3, 0.15, 0.15, 0.1};
            }
        }
        for (int k = 0; k < kinds.length; k++) {
            if (roll < weights[k]) {
                return kinds[k];
            }
            roll -= weights[k];
        }
        return kinds[0];
    }

    private Isle isle(double x, double z, double radius, HalcyonRegion region, double out, RandomSource random) {
        double roll = random.nextDouble();
        double sandbars = region == HalcyonRegion.MIRROR_SHOALS ? 0.4 : 0.2;
        Isle.Kind kind = roll < sandbars ? Isle.Kind.SANDBAR : roll < sandbars + 0.3 ? Isle.Kind.KNOLL : Isle.Kind.MOUND;
        double height = (kind == Isle.Kind.KNOLL ? 15 + random.nextDouble() * 30 : 8 + random.nextDouble() * 22) * (1 + out);
        return new Isle(kind, x, z, radius, height, region, random.nextLong(), noise);
    }

    private Optional<LandFeature> makeIsle(long key) {
        RandomSource random = random(key, 2);
        if (random.nextDouble() > 0.62 * isleDensity) {
            return Optional.empty();
        }
        double x = (keyX(key) + 0.15 + 0.7 * random.nextDouble()) * ISLE_CELL;
        double z = (keyZ(key) + 0.15 + 0.7 * random.nextDouble()) * ISLE_CELL;
        if (Math.hypot(x, z) < RegionMap.SPINDLE_EDGE + 20) {
            return Optional.empty();
        }
        double out = outwardness(x, z);
        double radius = (16 + random.nextDouble() * 34) * (1 + 0.5 * out);
        if (landmarks.nearObelisk(x, z, radius) || insideMainIsland(x, z)) {
            return Optional.empty();
        }
        Isle isle = isle(x, z, radius, region(x, z), out, random);
        return landmarks.blocks(x, z, radius, SEA, isle.peak()) ? Optional.empty() : Optional.of(isle);
    }

    private boolean insideMainIsland(double x, double z) {
        List<LandFeature> near = new ArrayList<>();
        forCells((int) x, (int) z, (int) x, (int) z, MAIN_CELL, MAIN_REACH, mains::get, near);
        if (landmarks.spindle.touches((int) x, (int) z, (int) x, (int) z)) {
            near.add(landmarks.spindle);
        }
        LandFeature.LandSample sample = new LandFeature.LandSample();
        sample.reset();
        for (LandFeature feature : near) {
            feature.sample((int) x, (int) z, sample);
        }
        return sample.height > SEA + 2;
    }

    private Optional<LandFeature> makeSpire(long key) {
        RandomSource random = random(key, 3);
        double x = (keyX(key) + 0.2 + 0.6 * random.nextDouble()) * SPIRE_CELL;
        double z = (keyZ(key) + 0.2 + 0.6 * random.nextDouble()) * SPIRE_CELL;
        HalcyonRegion region = region(x, z);
        double chance = switch (region) {
            case HUSH -> 0.6;
            case SUNREACH_CLIFFS -> 0.12;
            case ELDERWOOD -> 0.1;
            case WRECKFIELDS -> 0.08;
            case CIRRUS_REEFS -> 0.06;
            case PETALWASH -> 0.04;
            case MIRROR_SHOALS, CAPWOOD -> 0.03;
            default -> 0;
        };
        if (random.nextDouble() > chance * spireDensity) {
            return Optional.empty();
        }
        boolean hush = region == HalcyonRegion.HUSH;
        int count = hush ? 3 + random.nextInt(10) : 2 + random.nextInt(4);
        double spread = hush ? 14 + random.nextDouble() * 28 : 8 + random.nextDouble() * 12;
        double tallest = (hush ? 170 + random.nextDouble() * 130 : 140 + random.nextDouble() * 60) + 60 * outwardness(x, z);
        if (landmarks.nearObelisk(x, z, spread + 10) || landmarks.blocks(x, z, spread + 16, SEA, (int) tallest)) {
            return Optional.empty();
        }
        return Optional.of(new SpireCluster(x, z, spread, count, Math.min(380, tallest), region, random.nextLong(), noise));
    }

    private Optional<SolidFeature> makeRock(long key) {
        RandomSource random = random(key, 4);
        double x = (keyX(key) + 0.15 + 0.7 * random.nextDouble()) * ROCK_CELL;
        double z = (keyZ(key) + 0.15 + 0.7 * random.nextDouble()) * ROCK_CELL;
        HalcyonRegion region = region(x, z);
        double chance = switch (region) {
            case MIRROR_SHOALS -> 0.6;
            case SUNREACH_CLIFFS -> 0.35;
            case WRECKFIELDS -> 0.3;
            case PETALWASH -> 0.2;
            case SPINDLE, CAPWOOD, ELDERWOOD -> 0.12;
            default -> 0.15;
        };
        if (random.nextDouble() > chance * rockDensity || landmarks.nearObelisk(x, z, 20) || landHeight((int) x, (int) z) > SEA - 2) {
            return Optional.empty();
        }
        double roll = random.nextDouble();
        SeaRock.Kind kind = region == HalcyonRegion.MIRROR_SHOALS
                ? roll < 0.35 ? SeaRock.Kind.SLAB : roll < 0.7 ? SeaRock.Kind.BOULDERS : roll < 0.9 ? SeaRock.Kind.STACK : SeaRock.Kind.ARCH
                : region == HalcyonRegion.SUNREACH_CLIFFS
                        ? roll < 0.55 ? SeaRock.Kind.STACK : roll < 0.8 ? SeaRock.Kind.ARCH : SeaRock.Kind.SLAB
                        : roll < 0.5 ? SeaRock.Kind.STACK : roll < 0.75 ? SeaRock.Kind.SLAB : roll < 0.9 ? SeaRock.Kind.BOULDERS : SeaRock.Kind.ARCH;
        SeaRock rock = SeaRock.of(kind, x, z, region, random.nextLong());
        return landmarks.blocks(x, z, 20, rock.bottom(), rock.top()) ? Optional.empty() : Optional.of(rock);
    }

    /** An Elderwood stone blade, thrust up out of the land at a slant (image 8). */
    private Optional<SolidFeature> makeBlade(long key) {
        RandomSource random = random(key, 5);
        double x = (keyX(key) + 0.2 + 0.6 * random.nextDouble()) * BLADE_CELL;
        double z = (keyZ(key) + 0.2 + 0.6 * random.nextDouble()) * BLADE_CELL;
        if (region(x, z) != HalcyonRegion.ELDERWOOD || random.nextDouble() > 0.45 * rockDensity) {
            return Optional.empty();
        }
        double ground = landHeight((int) x, (int) z);
        if (ground < SEA + 4 || landmarks.nearObelisk(x, z, 80)) {
            return Optional.empty();
        }
        SeaRock blade = SeaRock.blade(x, z, ground, HalcyonRegion.ELDERWOOD, random.nextLong());
        return landmarks.blocks(x, z, 80, blade.bottom(), blade.top()) ? Optional.empty() : Optional.of(blade);
    }

    private Optional<SolidFeature> makeFloating(int size, long key) {
        RandomSource random = random(key, 10 + size);
        double x = (keyX(key) + 0.15 + 0.7 * random.nextDouble()) * FLOAT_CELL[size];
        double z = (keyZ(key) + 0.15 + 0.7 * random.nextDouble()) * FLOAT_CELL[size];
        HalcyonRegion region = region(x, z);
        double out = outwardness(x, z);
        if (size == 3 && out < 0.4) {
            return Optional.empty();
        }
        double factor = switch (region) {
            case CIRRUS_REEFS -> 1.3;
            case SPINDLE, ELDERWOOD -> 1.1;
            case SUNREACH_CLIFFS -> 1.0;
            case PETALWASH -> 0.9;
            case HUSH, CAPWOOD -> 0.8;
            case WRECKFIELDS -> 0.7;
            default -> 0.35;
        };
        double chance = new double[] {0.5, 0.42, 0.35, 0.55}[size] * factor * (size == 3 ? 1 : 1 + 0.2 * out);
        if (random.nextDouble() > chance * floatDensity) {
            return Optional.empty();
        }
        double radius = switch (size) {
            case 0 -> 8 + random.nextDouble() * 10;
            case 1 -> (22 + random.nextDouble() * 22) * (1 + 0.6 * out);
            case 2 -> (60 + random.nextDouble() * 40) * (1 + 0.6 * out);
            default -> 60 + random.nextDouble() * 50;
        };
        FloatingIsland.Variant variant = floatingVariant(size, region, random.nextDouble());
        if (variant == FloatingIsland.Variant.SHARD) {
            radius *= 0.55;
        } else if (variant == FloatingIsland.Variant.THORN) {
            radius = Math.min(radius, 34);
        }
        double depth = FloatingIsland.depthFor(variant, radius);
        // Keep clear of the land below: look at the middle and round the edge.
        double ground = landHeight((int) x, (int) z);
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            ground = Math.max(ground, landHeight((int) (x + Math.cos(a) * radius), (int) (z + Math.sin(a) * radius)));
        }
        double lowest = Math.max(size == 3 ? 210 : FLOAT_FLOOR, ground + FLOAT_CLEARANCE) + depth + 3;
        double highest = size == 3 ? 375 : FLOAT_CEILING - 6;
        if (lowest > highest) {
            return Optional.empty();
        }
        int top = (int) Mth.lerp(Math.pow(random.nextDouble(), 1.3), lowest, highest);
        double reach = variant == FloatingIsland.Variant.HORNCROWN ? radius * 1.9 : radius * 1.3;
        if (landmarks.blocks(x, z, reach, (int) (top - depth - 4), top + 90) || landmarks.nearObelisk(x, z, reach)) {
            return Optional.empty();
        }
        return Optional.of(FloatingIsland.of(variant, x, z, radius, top, region, random.nextLong(), noise));
    }

    private static FloatingIsland.Variant floatingVariant(int size, HalcyonRegion region, double roll) {
        FloatingIsland.Variant[] kinds;
        double[] weights;
        double thorns = region == HalcyonRegion.HUSH || region == HalcyonRegion.ELDERWOOD ? 0.25 : 0.1;
        switch (size) {
            case 0 -> {
                kinds = new FloatingIsland.Variant[] {FloatingIsland.Variant.TUFT, FloatingIsland.Variant.TEARDROP, FloatingIsland.Variant.SHARD,
                        FloatingIsland.Variant.CLUSTER};
                weights = new double[] {0.35, 0.35, 0.2, 0.1};
            }
            case 1 -> {
                kinds = new FloatingIsland.Variant[] {FloatingIsland.Variant.THORN, FloatingIsland.Variant.TEARDROP, FloatingIsland.Variant.SHELF,
                        FloatingIsland.Variant.CLUSTER, FloatingIsland.Variant.SHARD, FloatingIsland.Variant.RUIN_TOP};
                weights = new double[] {thorns, 0.28, 0.27, 0.18, 0.09, 0.09};
            }
            case 2 -> {
                kinds = new FloatingIsland.Variant[] {FloatingIsland.Variant.THORN, FloatingIsland.Variant.SHELF, FloatingIsland.Variant.CLUSTER};
                weights = new double[] {thorns * 0.6, 0.55, 0.4};
            }
            default -> {
                kinds = new FloatingIsland.Variant[] {FloatingIsland.Variant.HORNCROWN, FloatingIsland.Variant.SHELF};
                weights = new double[] {0.72, 0.28};
            }
        }
        double total = 0;
        for (double w : weights) {
            total += w;
        }
        roll *= total;
        for (int k = 0; k < kinds.length; k++) {
            if (roll < weights[k]) {
                return kinds[k];
            }
            roll -= weights[k];
        }
        return kinds[0];
    }

    /** A cave, or nothing: its kind by what the land round the spot allows (docs/DECISIONS.md K84). */
    private Optional<CaveFeature> makeCave(long key) {
        RandomSource random = random(key, 20);
        double x = (keyX(key) + 0.2 + 0.6 * random.nextDouble()) * CAVE_CELL;
        double z = (keyZ(key) + 0.2 + 0.6 * random.nextDouble()) * CAVE_CELL;
        if (Math.hypot(x, z) < RegionMap.SPINDLE_EDGE + 40 || random.nextDouble() > 0.3 + 0.35 * outwardness(x, z)
                || landmarks.nearObelisk(x, z, 70)) {
            return Optional.empty();
        }
        double here = landHeight((int) x, (int) z);
        if (here < SEA + 25) {
            return Optional.empty();
        }
        HalcyonRegion region = region(x, z);
        double roll = random.nextDouble();
        long seed = random.nextLong();
        CaveFeature cave = null;
        if (roll < 0.35) {
            cave = grotto(x, z, here, region, seed, random);
        } else if (roll < 0.62) {
            cave = waterfallHall(x, z, here, region, seed, random);
        } else if (roll < 0.88) {
            double radius = 16 + random.nextDouble() * 12;
            int floor = SEA + 4 + random.nextInt(8);
            double height = radius * 1.1;
            if (here > floor + height + 12 && ringAbove(x, z, radius * 0.8, floor + height * 0.6 + 6)) {
                cave = CaveFeature.of(CaveFeature.Kind.SINKHOLE, x, z, radius, floor, height, 0, region, seed, noise);
            }
        } else {
            double radius = 6 + random.nextDouble() * 4;
            int centre = SEA + 8 + random.nextInt(Math.max(1, (int) (here - SEA - 30)));
            if (here > centre + radius + 6) {
                cave = CaveFeature.of(CaveFeature.Kind.GEODE, x, z, radius, centre, radius, 0, region, seed, noise);
            }
        }
        return Optional.ofNullable(cave);
    }

    /** A grotto where the land stands high enough for its roof and the sea lies close enough for its arch. */
    private CaveFeature grotto(double x, double z, double here, HalcyonRegion region, long seed, RandomSource random) {
        double radius = 24 + random.nextDouble() * 16;
        double height = 18 + random.nextDouble() * 12;
        if (here < SEA + height + 8 || !ringAbove(x, z, radius * 0.8, SEA + height * 0.6 + 6)) {
            return null;
        }
        double[] low = lowestAround(x, z, radius * 1.6);
        if (low[1] > SEA - 1) {
            return null;
        }
        return CaveFeature.of(CaveFeature.Kind.GROTTO, x, z, radius, SEA, height, low[0], region, seed, noise);
    }

    /** A waterfall hall where a cliff drops away on one side, its mouth facing the drop. */
    private CaveFeature waterfallHall(double x, double z, double here, HalcyonRegion region, long seed, RandomSource random) {
        double radius = 9 + random.nextDouble() * 5;
        double height = radius * 0.9;
        double[] low = lowestAround(x, z, radius * 2.4);
        int floor = (int) Math.max(SEA + 2, low[1] + 3);
        if (here < floor + height + 6 || here - low[1] < height + 10) {
            return null;
        }
        return CaveFeature.of(CaveFeature.Kind.WATERFALL_HALL, x, z, radius, floor, height, low[0], region, seed, noise);
    }

    /** True if the land at eight points round a circle all stands above {@code y}. */
    private boolean ringAbove(double x, double z, double radius, double y) {
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            if (landHeight((int) (x + Math.cos(a) * radius), (int) (z + Math.sin(a) * radius)) < y) {
                return false;
            }
        }
        return true;
    }

    /** The bearing and height of the lowest land among twelve points round a circle. */
    private double[] lowestAround(double x, double z, double radius) {
        double best = Double.MAX_VALUE;
        double bearing = 0;
        for (int k = 0; k < 12; k++) {
            double a = k * Math.PI / 6;
            double h = landHeight((int) (x + Math.cos(a) * radius), (int) (z + Math.sin(a) * radius));
            if (h < best) {
                best = h;
                bearing = a;
            }
        }
        return new double[] {bearing, best};
    }

    /** A giant tree or mushroom, or nothing: what grows depends on the biome, and it needs gentle grassy ground. */
    private Optional<Flora.Big> makeFlora(long key) {
        RandomSource random = random(key, 30);
        int x = (int) Math.floor((keyX(key) + 0.2 + 0.6 * random.nextDouble()) * FLORA_CELL);
        int z = (int) Math.floor((keyZ(key) + 0.2 + 0.6 * random.nextDouble()) * FLORA_CELL);
        HalcyonRegion region = region(x, z);
        double roll = random.nextDouble();
        double pick = random.nextDouble();
        Flora.Kind kind = switch (region) {
            case ELDERWOOD -> roll < 0.5 ? Flora.Kind.ELDER_LUMEN : null;
            case CAPWOOD -> roll < 0.85 ? pick < 0.32 ? Flora.Kind.EMBER_CAP : pick < 0.5 ? Flora.Kind.PARASOL : pick < 0.65 ? Flora.Kind.BELLCAP
                    : pick < 0.72 ? Flora.Kind.GLOWCAP : Flora.Kind.MUSHROOM_CLUSTER : null;
            case PETALWASH -> roll < 0.14 ? Flora.Kind.GREAT_VIOLET : null;
            case CIRRUS_REEFS -> roll < 0.14 ? Flora.Kind.GRAND_BLOSSOM : null;
            case HUSH -> roll < 0.1 ? Flora.Kind.WEEPING_DUSK : roll < 0.2 ? Flora.Kind.GLOWCAP : null;
            case SUNREACH_CLIFFS -> roll < 0.55 ? Flora.Kind.SPIREPINE : null;
            case MIRROR_SHOALS -> roll < 0.04 ? Flora.Kind.GRAND_BLOSSOM : null;
            case SPINDLE -> roll < 0.07 ? Flora.Kind.GREAT_VIOLET : null;
            default -> null;
        };
        if (kind == null || landmarks.nearObelisk(x, z, kind.reach) || landmarks.blocks(x, z, kind.reach, SEA, SEA + 200)) {
            return Optional.empty();
        }
        LandFeature.LandSample base = landSample(x, z);
        if (base.surface != LandFeature.Surface.TURF || base.rock != null || base.height < SEA + 2) {
            return Optional.empty();
        }
        int ground = (int) Math.floor(base.height);
        for (int k = 0; k < 4; k++) {
            double a = k * Math.PI / 2;
            if (Math.abs(landHeight((int) (x + Math.cos(a) * 3), (int) (z + Math.sin(a) * 3)) - base.height) > 3) {
                return Optional.empty();
            }
        }
        // Nothing overhead within reach of its crown.
        for (SolidFeature solid : solidsNear(x - kind.reach, z - kind.reach, x + kind.reach, z + kind.reach)) {
            if (solid.bottom() < ground + 70 && solid.top() > ground) {
                return Optional.empty();
            }
        }
        return Optional.of(new Flora.Big(kind, x, ground, z, random.nextLong()));
    }
}
