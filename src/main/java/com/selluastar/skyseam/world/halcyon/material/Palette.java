package com.selluastar.skyseam.world.halcyon.material;

import java.util.EnumMap;
import java.util.Map;

import com.selluastar.skyseam.registry.SkyseamBlocks;
import com.selluastar.skyseam.world.halcyon.HalcyonRegion;
import com.selluastar.skyseam.world.halcyon.plan.Noise;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The blocks each biome is made of (docs/DECISIONS.md K77). Cliffs never show one block: the rock is a weighted mix
 * picked by 3D noise squashed flat, so it lies in strata like the layered cliffs in the reference images, with a
 * little speckle and the odd accent vein. The Sunreach Cliffs go further (image 11): their rock is laid down in
 * horizontal beds 5 to 8 blocks thick, each one stone of sandy, ochre or umber Cliffstone, gently wavering.
 */
public final class Palette {
    private static final Map<HalcyonRegion, Palette> PALETTES = new EnumMap<>(HalcyonRegion.class);
    private static Palette shoalrock;
    private static Palette hornstone;
    private static Palette thorn;
    /** How tall each bed of layered rock is. */
    private static final int BED = 6;

    public final BlockState turf;
    public final BlockState turfAlt;
    public final BlockState soil;
    public final BlockState beach;
    public final BlockState beachUnder;
    public final BlockState floorShallow;
    public final BlockState floorDeep;
    private final BlockState[] rocks;
    private final double[] cumulative;
    private final BlockState accent;
    private final double accentChance;
    /** For layered rock, the stone of each bed in turn (picked per bed by hash); else null. */
    private BlockState[] beds;

    private Palette(BlockState turf, BlockState turfAlt, BlockState soil, BlockState beach, BlockState beachUnder, BlockState floorShallow,
            BlockState floorDeep, BlockState accent, double accentChance, Object... rocksAndWeights) {
        this.turf = turf;
        this.turfAlt = turfAlt;
        this.soil = soil;
        this.beach = beach;
        this.beachUnder = beachUnder;
        this.floorShallow = floorShallow;
        this.floorDeep = floorDeep;
        this.accent = accent;
        this.accentChance = accentChance;
        int n = rocksAndWeights.length / 2;
        rocks = new BlockState[n];
        cumulative = new double[n];
        double total = 0;
        for (int k = 0; k < n; k++) {
            rocks[k] = (BlockState) rocksAndWeights[2 * k];
            total += (Double) rocksAndWeights[2 * k + 1];
            cumulative[k] = total;
        }
        for (int k = 0; k < n; k++) {
            cumulative[k] /= total;
        }
    }

    /** The palette for a biome. */
    public static Palette of(HalcyonRegion region) {
        Palette palette = PALETTES.get(region);
        if (palette == null) {
            synchronized (PALETTES) {
                palette = PALETTES.computeIfAbsent(region, Palette::make);
            }
        }
        return palette;
    }

    /** Smooth cream Shoalrock, for the leaning slabs and boulders wherever they stand. */
    public static Palette shoalrock() {
        if (shoalrock == null) {
            BlockState smooth = SkyseamBlocks.SMOOTH_SHOALROCK.get().defaultBlockState();
            BlockState rough = SkyseamBlocks.SHOALROCK.get().defaultBlockState();
            shoalrock = new Palette(rough, rough, rough, Blocks.SAND.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(),
                    Blocks.SAND.defaultBlockState(), Blocks.SAND.defaultBlockState(), Blocks.CALCITE.defaultBlockState(), 0.03,
                    smooth, 0.55, rough, 0.45);
        }
        return shoalrock;
    }

    /** Pale Hornstone, for the Horncrown isles and their horns. */
    public static Palette hornstone() {
        if (hornstone == null) {
            BlockState horn = SkyseamBlocks.HORNSTONE.get().defaultBlockState();
            hornstone = new Palette(Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.DIRT.defaultBlockState(),
                    Blocks.SAND.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), Blocks.SAND.defaultBlockState(),
                    Blocks.SAND.defaultBlockState(), Blocks.CALCITE.defaultBlockState(), 0.02, horn, 0.75,
                    SkyseamBlocks.SMOOTH_SHOALROCK.get().defaultBlockState(), 0.15, Blocks.CALCITE.defaultBlockState(), 0.1);
        }
        return hornstone;
    }

    /** Dark spiked rock veined with glowing crystal, for the Thorn isles. */
    public static Palette thorn() {
        if (thorn == null) {
            BlockState duskstone = SkyseamBlocks.DUSKSTONE.get().defaultBlockState();
            thorn = new Palette(Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.MOSS_BLOCK.defaultBlockState(), Blocks.DIRT.defaultBlockState(),
                    Blocks.GRAVEL.defaultBlockState(), duskstone, Blocks.GRAVEL.defaultBlockState(), Blocks.GRAVEL.defaultBlockState(),
                    SkyseamBlocks.PRISMITE_LAMP.get().defaultBlockState(), 0.008, duskstone, 0.4, Blocks.DEEPSLATE.defaultBlockState(), 0.3,
                    SkyseamBlocks.COBBLED_DUSKSTONE.get().defaultBlockState(), 0.3);
        }
        return thorn;
    }

    private static Palette make(HalcyonRegion region) {
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState sand = Blocks.SAND.defaultBlockState();
        BlockState sandstone = Blocks.SANDSTONE.defaultBlockState();
        BlockState gravel = Blocks.GRAVEL.defaultBlockState();
        BlockState clay = Blocks.CLAY.defaultBlockState();
        BlockState stillstone = SkyseamBlocks.STILLSTONE.get().defaultBlockState();
        BlockState duskstone = SkyseamBlocks.DUSKSTONE.get().defaultBlockState();
        BlockState cobbledDusk = SkyseamBlocks.COBBLED_DUSKSTONE.get().defaultBlockState();
        BlockState shoal = SkyseamBlocks.SHOALROCK.get().defaultBlockState();
        BlockState smoothShoal = SkyseamBlocks.SMOOTH_SHOALROCK.get().defaultBlockState();
        BlockState lamp = SkyseamBlocks.PRISMITE_LAMP.get().defaultBlockState();
        return switch (region) {
            // Each biome's cliffs keep to one range of tone, so its strata read as bands, not stripes of black and white.
            case MIRROR_SHOALS -> new Palette(grass, grass, dirt, sand, sandstone, sand, sand, Blocks.CALCITE.defaultBlockState(), 0.02,
                    shoal, 0.5, smoothShoal, 0.25, sandstone, 0.15, Blocks.CALCITE.defaultBlockState(), 0.1);
            case CIRRUS_REEFS -> new Palette(grass, grass, dirt, sand, sandstone, sand, clay, SkyseamBlocks.CLOUD.get().defaultBlockState(), 0.0,
                    Blocks.CALCITE.defaultBlockState(), 0.4, stillstone, 0.4, Blocks.DIORITE.defaultBlockState(), 0.2);
            case HUSH -> new Palette(grass, grass, dirt, gravel, duskstone, gravel, cobbledDusk, lamp, 0.0006,
                    duskstone, 0.45, cobbledDusk, 0.3, Blocks.DEEPSLATE.defaultBlockState(), 0.25);
            case SPINDLE -> new Palette(grass, grass, dirt, sand, sandstone, sand, gravel, lamp, 0.0003,
                    duskstone, 0.45, cobbledDusk, 0.3, Blocks.DEEPSLATE.defaultBlockState(), 0.25);
            case WRECKFIELDS -> new Palette(grass, Blocks.COARSE_DIRT.defaultBlockState(), Blocks.COARSE_DIRT.defaultBlockState(), gravel, Blocks.TUFF
                    .defaultBlockState(), gravel, gravel, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 0.02,
                    Blocks.TUFF.defaultBlockState(), 0.4, Blocks.ANDESITE.defaultBlockState(), 0.3, cobbledDusk, 0.3);
            case SUNREACH_CLIFFS -> {
                BlockState cliff = SkyseamBlocks.CLIFFSTONE.get().defaultBlockState();
                BlockState ochre = SkyseamBlocks.OCHRE_CLIFFSTONE.get().defaultBlockState();
                BlockState umber = SkyseamBlocks.UMBER_CLIFFSTONE.get().defaultBlockState();
                Palette layered = new Palette(grass, grass, dirt, sand, sandstone, sand, sand, SkyseamBlocks.MOSSY_CLIFFSTONE.get().defaultBlockState(),
                        0.02, cliff, 0.45, ochre, 0.35, umber, 0.2);
                layered.beds = new BlockState[] {cliff, ochre, cliff, umber, ochre, cliff, umber, sandstone};
                yield layered;
            }
            case CAPWOOD -> new Palette(grass, Blocks.MOSS_BLOCK.defaultBlockState(), dirt, sand, sandstone, sand, gravel,
                    Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 0.03, Blocks.STONE.defaultBlockState(), 0.35, Blocks.ANDESITE.defaultBlockState(), 0.3,
                    Blocks.TUFF.defaultBlockState(), 0.2, Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 0.15);
            case ELDERWOOD -> new Palette(grass, Blocks.PODZOL.defaultBlockState(), dirt, gravel, Blocks.TUFF.defaultBlockState(), gravel, gravel,
                    Blocks.MOSS_BLOCK.defaultBlockState(), 0.02, Blocks.DEEPSLATE.defaultBlockState(), 0.3, duskstone, 0.3,
                    Blocks.TUFF.defaultBlockState(), 0.2, Blocks.COBBLED_DEEPSLATE.defaultBlockState(), 0.2);
            default -> new Palette(grass, grass, dirt, sand, sandstone, sand, clay, Blocks.CALCITE.defaultBlockState(), 0.01,
                    duskstone, 0.4, cobbledDusk, 0.3, Blocks.ANDESITE.defaultBlockState(), 0.15, Blocks.TUFF.defaultBlockState(), 0.15);
        };
    }

    /**
     * The rock at a block: broad patches and strata from two scales of squashed 3D noise, so a cliff shows bands and
     * patches of each stone (as in the reference images) rather than static; a touch of speckle, and the odd accent.
     */
    public BlockState rock(Noise noise, int x, int y, int z) {
        double h = noise.hash(x, y, z, 77);
        if (h < accentChance) {
            return accent;
        }
        if (beds != null) {
            // Beds waver up and down a few blocks across the land, and their edges fray a little into each other.
            double wave = 3.5 * noise.at(x / 70.0, 0.5, z / 70.0) + 0.8 * (h - 0.5);
            int bed = Math.floorDiv((int) Math.floor(y + wave), BED);
            return beds[(int) Math.floorMod(Noise.mix(0, bed, 0, 0, 3), beds.length)];
        }
        double v = 0.5 + 0.5 * noise.at(x / 46.0, y / 11.0, z / 46.0) + 0.18 * noise.at(x / 13.0 + 50, y / 4.5, z / 13.0) + 0.05 * (h - 0.5);
        for (int k = 0; k < rocks.length; k++) {
            if (v < cumulative[k]) {
                return rocks[k];
            }
        }
        return rocks[rocks.length - 1];
    }

    /**
     * The rock of one column, block by block, for the chunk generator: the same choice as {@link #rock}, but with its
     * slow noise looked up once every 4 blocks of height (and its fine noise every 2) and blended between, and the fine
     * noise left out deep inside the land where no one sees it. One instance serves a whole chunk, column by column.
     */
    public static final class Column {
        private static final int COARSE = 4;
        private static final int FINE = 2;
        private final double[] coarse = new double[512 / COARSE + 2];
        private final double[] fine = new double[512 / FINE + 2];
        private Noise noise;
        private Palette palette;
        private int x;
        private int z;
        private double wave;

        /** Starts a new column. */
        public Column at(Noise noise, Palette palette, int x, int z) {
            this.noise = noise;
            this.palette = palette;
            this.x = x;
            this.z = z;
            java.util.Arrays.fill(coarse, Double.NaN);
            java.util.Arrays.fill(fine, Double.NaN);
            this.wave = palette.beds != null ? 3.5 * noise.at(x / 70.0, 0.5, z / 70.0) : 0;
            return this;
        }

        /** The rock at a height, {@code depth} blocks under the surface. */
        public BlockState rock(int y, int depth) {
            double h = noise.hash(x, y, z, 77);
            if (h < palette.accentChance) {
                return palette.accent;
            }
            if (palette.beds != null) {
                int bed = Math.floorDiv((int) Math.floor(y + wave + 0.8 * (h - 0.5)), BED);
                return palette.beds[(int) Math.floorMod(Noise.mix(0, bed, 0, 0, 3), palette.beds.length)];
            }
            double v = 0.5 + 0.5 * coarse(y) + (depth < 14 ? 0.18 * fine(y) : 0) + 0.05 * (h - 0.5);
            for (int k = 0; k < palette.rocks.length; k++) {
                if (v < palette.cumulative[k]) {
                    return palette.rocks[k];
                }
            }
            return palette.rocks[palette.rocks.length - 1];
        }

        private double coarse(int y) {
            int k = Math.floorDiv(Math.max(0, y), COARSE);
            double t = (Math.max(0, y) - k * COARSE) / (double) COARSE;
            return net.minecraft.util.Mth.lerp(t, coarseAt(k), coarseAt(k + 1));
        }

        private double coarseAt(int k) {
            if (k >= coarse.length) {
                return noise.at(x / 46.0, k * COARSE / 11.0, z / 46.0);
            }
            if (Double.isNaN(coarse[k])) {
                coarse[k] = noise.at(x / 46.0, k * COARSE / 11.0, z / 46.0);
            }
            return coarse[k];
        }

        private double fine(int y) {
            int k = Math.floorDiv(Math.max(0, y), FINE);
            double t = (Math.max(0, y) - k * FINE) / (double) FINE;
            return net.minecraft.util.Mth.lerp(t, fineAt(k), fineAt(k + 1));
        }

        private double fineAt(int k) {
            if (k >= fine.length) {
                return noise.at(x / 13.0 + 50, k * FINE / 4.5, z / 13.0);
            }
            if (Double.isNaN(fine[k])) {
                fine[k] = noise.at(x / 13.0 + 50, k * FINE / 4.5, z / 13.0);
            }
            return fine[k];
        }
    }

    /** The turf at a column: mostly the main turf, in patches the alternative. */
    public BlockState turf(Noise noise, int x, int z) {
        return turfAlt == turf || noise.fbm(x, z, 14, 2) < 0.35 ? turf : turfAlt;
    }
}
