package com.selluastar.skyseam.registry;

import java.util.List;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.aperture.HarmonicApertureBlock;
import com.selluastar.skyseam.block.BloomcoralBlock;
import com.selluastar.skyseam.block.CloudBlock;
import com.selluastar.skyseam.block.LanternVineBlock;
import com.selluastar.skyseam.block.SmallShroomBlock;

import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.GlowLichenBlock;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TallFlowerBlock;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SkyseamBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Skyseam.MOD_ID);

    /** The Harmonic Aperture (spec section 11): opens the Seam when mounted on a flying ship. */
    public static final DeferredBlock<HarmonicApertureBlock> HARMONIC_APERTURE = BLOCKS.register("harmonic_aperture",
            () -> new HarmonicApertureBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_YELLOW)
                    .strength(3.5f, 1200f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.COPPER)
                    .lightLevel(state -> 7)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)));

    // ---- The Halcyon's own blocks (spec section 20; the core set the author chose for M3, docs/DECISIONS.md K60) ----

    /** Stillstone: the pale stone the Halcyon's islands are made of, and the Wrights' building stone. */
    public static final DeferredBlock<Block> STILLSTONE = BLOCKS.register("stillstone", () -> new Block(stone()));
    public static final DeferredBlock<Block> STILLSTONE_BRICKS = BLOCKS.register("stillstone_bricks", () -> new Block(stone()));
    public static final DeferredBlock<Block> MOSSY_STILLSTONE_BRICKS = BLOCKS.register("mossy_stillstone_bricks", () -> new Block(stone()));
    public static final DeferredBlock<Block> CHISELED_STILLSTONE_BRICKS = BLOCKS.register("chiseled_stillstone_bricks", () -> new Block(stone()));
    public static final DeferredBlock<RotatedPillarBlock> STILLSTONE_PILLAR = BLOCKS.register("stillstone_pillar",
            () -> new RotatedPillarBlock(stone()));
    public static final DeferredBlock<SlabBlock> STILLSTONE_BRICK_SLAB = BLOCKS.register("stillstone_brick_slab", () -> new SlabBlock(stone()));
    public static final DeferredBlock<StairBlock> STILLSTONE_BRICK_STAIRS = BLOCKS.register("stillstone_brick_stairs",
            () -> new StairBlock(STILLSTONE_BRICKS.get().defaultBlockState(), stone()));
    public static final DeferredBlock<WallBlock> STILLSTONE_BRICK_WALL = BLOCKS.register("stillstone_brick_wall",
            () -> new WallBlock(stone().forceSolidOn()));
    public static final DeferredBlock<TransparentBlock> STILLSTONE_GLASS = BLOCKS.register("stillstone_glass",
            () -> new TransparentBlock(BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).strength(0.3f)
                    .sound(SoundType.GLASS).noOcclusion().isValidSpawn((state, level, pos, type) -> false)
                    .isRedstoneConductor((state, level, pos) -> false).isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false)));

    /** Cloud blocks in three shades: the soft, slightly see-through body of the Cirrus Reefs. */
    public static final DeferredBlock<CloudBlock> CLOUD = BLOCKS.register("cloud", () -> new CloudBlock(cloud(MapColor.SNOW)));
    public static final DeferredBlock<CloudBlock> ROSE_CLOUD = BLOCKS.register("rose_cloud", () -> new CloudBlock(cloud(MapColor.COLOR_PINK)));
    public static final DeferredBlock<CloudBlock> DUSK_CLOUD = BLOCKS.register("dusk_cloud", () -> new CloudBlock(cloud(MapColor.COLOR_LIGHT_BLUE)));

    /** Island flora: petal carpets on the Petalwash meadows and Cloudmoss on cloud blocks. */
    public static final DeferredBlock<CarpetBlock> PETAL_CARPET = BLOCKS.register("petal_carpet",
            () -> new CarpetBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(0.1f).sound(SoundType.PINK_PETALS)
                    .noOcclusion().pushReaction(PushReaction.DESTROY)));
    public static final DeferredBlock<CarpetBlock> CLOUDMOSS = BLOCKS.register("cloudmoss",
            () -> new CarpetBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GREEN).strength(0.1f).sound(SoundType.MOSS_CARPET)
                    .noOcclusion().pushReaction(PushReaction.DESTROY)));
    /** Glow moss: soft teal light on the undersides of islands (the Underbloom). */
    public static final DeferredBlock<GlowLichenBlock> GLOW_MOSS = BLOCKS.register("glow_moss",
            () -> new GlowLichenBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).replaceable().noCollission().strength(0.2f)
                    .sound(SoundType.GLOW_LICHEN).lightLevel(GlowLichenBlock.emission(9)).ignitedByLava().pushReaction(PushReaction.DESTROY)));
    public static final DeferredBlock<LanternVineBlock> LANTERN_VINE = BLOCKS.register("lantern_vine",
            () -> new LanternVineBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).noCollission().instabreak()
                    .sound(SoundType.CAVE_VINES).lightLevel(LanternVineBlock::lightLevel).pushReaction(PushReaction.DESTROY)));

    /** Prismite: violet crystal growing from Stillstone, in three sizes, mined for Prism Dust (from M5). */
    public static final DeferredBlock<AmethystClusterBlock> SMALL_PRISMITE_BUD = BLOCKS.register("small_prismite_bud",
            () -> new AmethystClusterBlock(3, 4, prismite(1)));
    public static final DeferredBlock<AmethystClusterBlock> LARGE_PRISMITE_BUD = BLOCKS.register("large_prismite_bud",
            () -> new AmethystClusterBlock(5, 3, prismite(4)));
    public static final DeferredBlock<AmethystClusterBlock> PRISMITE_CLUSTER = BLOCKS.register("prismite_cluster",
            () -> new AmethystClusterBlock(7, 3, prismite(6)));

    // ---- The rebuilt Halcyon's blocks (the author's reference images 5 to 7; docs/DECISIONS.md K75) ----------------

    /** Duskstone: the dark violet-grey rock of the Halcyon's cliffs and spires (images 5 and 6). */
    public static final DeferredBlock<Block> DUSKSTONE = BLOCKS.register("duskstone", () -> new Block(dusk()));
    public static final DeferredBlock<Block> COBBLED_DUSKSTONE = BLOCKS.register("cobbled_duskstone", () -> new Block(dusk()));
    public static final DeferredBlock<Block> DUSKSTONE_BRICKS = BLOCKS.register("duskstone_bricks", () -> new Block(dusk()));
    public static final DeferredBlock<Block> POLISHED_DUSKSTONE = BLOCKS.register("polished_duskstone", () -> new Block(dusk()));
    public static final DeferredBlock<RotatedPillarBlock> DUSKSTONE_PILLAR = BLOCKS.register("duskstone_pillar",
            () -> new RotatedPillarBlock(dusk()));
    /** Shoalrock: the cream, water-worn rock of the Mirror Shoals' boulders and leaning slabs (reference image 2). */
    public static final DeferredBlock<Block> SHOALROCK = BLOCKS.register("shoalrock", () -> new Block(stone().mapColor(MapColor.SAND)));
    public static final DeferredBlock<Block> SMOOTH_SHOALROCK = BLOCKS.register("smooth_shoalrock", () -> new Block(stone().mapColor(MapColor.SAND)));
    /** Pale Blossom leaves: the white umbrella crowns of image 6's trees. Untinted. */
    public static final DeferredBlock<LeavesBlock> PALE_BLOSSOM_LEAVES = BLOCKS.register("pale_blossom_leaves",
            () -> new LeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CHERRY_LEAVES).mapColor(MapColor.SNOW)));
    /** Wisteria: purple drapes hanging from island edges and crowns (image 5). */
    public static final DeferredBlock<LanternVineBlock> WISTERIA = BLOCKS.register("wisteria",
            () -> new LanternVineBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).noCollission().instabreak()
                    .sound(SoundType.CAVE_VINES).pushReaction(PushReaction.DESTROY)));
    /** The Prismite lamp: the glowing violet cubes on shores and under the shallows (images 5 and 6). */
    public static final DeferredBlock<Block> PRISMITE_LAMP = BLOCKS.register("prismite_lamp",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(0.6f).sound(SoundType.AMETHYST)
                    .lightLevel(state -> 14)));
    /** Bloomcoral: coral-like bushes on land and under the sea (images 5 and 7), in three colours. */
    public static final DeferredBlock<BloomcoralBlock> BLOOMCORAL_PINK = BLOCKS.register("bloomcoral_pink",
            () -> new BloomcoralBlock(bloomcoral(MapColor.COLOR_PINK)));
    public static final DeferredBlock<BloomcoralBlock> BLOOMCORAL_VIOLET = BLOCKS.register("bloomcoral_violet",
            () -> new BloomcoralBlock(bloomcoral(MapColor.COLOR_PURPLE)));
    public static final DeferredBlock<BloomcoralBlock> BLOOMCORAL_WHITE = BLOCKS.register("bloomcoral_white",
            () -> new BloomcoralBlock(bloomcoral(MapColor.SNOW)));

    // ---- Round two (the author's halfway feedback, reference images 8 to 14; docs/DECISIONS.md K81) --------------

    /** The Sundered Obelisk's weathered and carved Duskstone. Runed Duskstone's glyphs glow (a full-bright overlay). */
    public static final DeferredBlock<Block> CRACKED_DUSKSTONE_BRICKS = BLOCKS.register("cracked_duskstone_bricks", () -> new Block(dusk()));
    public static final DeferredBlock<Block> MOSSY_DUSKSTONE_BRICKS = BLOCKS.register("mossy_duskstone_bricks", () -> new Block(dusk()));
    public static final DeferredBlock<Block> DUSKSTONE_TILES = BLOCKS.register("duskstone_tiles", () -> new Block(dusk()));
    public static final DeferredBlock<Block> CHISELED_DUSKSTONE = BLOCKS.register("chiseled_duskstone", () -> new Block(dusk()));
    public static final DeferredBlock<Block> RUNED_DUSKSTONE = BLOCKS.register("runed_duskstone", () -> new Block(dusk().lightLevel(state -> 6)));
    /** Cliffstone: the layered tan, ochre and umber rock of the outer cliffs (image 11), and its mossy ledges. */
    public static final DeferredBlock<Block> CLIFFSTONE = BLOCKS.register("cliffstone", () -> new Block(stone().mapColor(MapColor.SAND)));
    public static final DeferredBlock<Block> OCHRE_CLIFFSTONE = BLOCKS.register("ochre_cliffstone",
            () -> new Block(stone().mapColor(MapColor.COLOR_ORANGE)));
    public static final DeferredBlock<Block> UMBER_CLIFFSTONE = BLOCKS.register("umber_cliffstone",
            () -> new Block(stone().mapColor(MapColor.COLOR_BROWN)));
    public static final DeferredBlock<Block> MOSSY_CLIFFSTONE = BLOCKS.register("mossy_cliffstone",
            () -> new Block(stone().mapColor(MapColor.COLOR_GREEN)));
    /** Hornstone: the pale, horn-grained rock of the Horncrown isles (image 9). */
    public static final DeferredBlock<Block> HORNSTONE = BLOCKS.register("hornstone", () -> new Block(stone().mapColor(MapColor.QUARTZ)));
    /** Elder wood: the giant trees of the Elderwood, their bark threaded with glowing veins (image 8). */
    public static final DeferredBlock<RotatedPillarBlock> ELDER_LOG = BLOCKS.register("elder_log",
            () -> new RotatedPillarBlock(wood(MapColor.COLOR_GRAY).lightLevel(state -> 5)));
    public static final DeferredBlock<RotatedPillarBlock> ELDER_WOOD = BLOCKS.register("elder_wood",
            () -> new RotatedPillarBlock(wood(MapColor.COLOR_GRAY).lightLevel(state -> 5)));
    public static final DeferredBlock<LeavesBlock> ELDER_LEAVES = BLOCKS.register("elder_leaves",
            () -> new LeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LEAVES).mapColor(MapColor.COLOR_GREEN)));
    /** Duskwood: violet-barked trees with violet crowns, the great trees of Petalwash (images 5 and 6). */
    public static final DeferredBlock<RotatedPillarBlock> DUSKWOOD_LOG = BLOCKS.register("duskwood_log",
            () -> new RotatedPillarBlock(wood(MapColor.COLOR_PURPLE)));
    public static final DeferredBlock<RotatedPillarBlock> DUSKWOOD_WOOD = BLOCKS.register("duskwood_wood",
            () -> new RotatedPillarBlock(wood(MapColor.COLOR_PURPLE)));
    public static final DeferredBlock<LeavesBlock> DUSKWOOD_LEAVES = BLOCKS.register("duskwood_leaves",
            () -> new LeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_LEAVES).mapColor(MapColor.COLOR_PURPLE)));
    /** Spirepine needles: the crowns of the tall dark conifers on the Sunreach Cliffs (image 11). */
    public static final DeferredBlock<LeavesBlock> SPIREPINE_NEEDLES = BLOCKS.register("spirepine_needles",
            () -> new LeavesBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.SPRUCE_LEAVES).mapColor(MapColor.COLOR_GREEN)));
    /** The giant mushrooms of the Capwood (image 10): each cap's underside shows its gills, the Glowcap glows. */
    public static final DeferredBlock<HugeMushroomBlock> EMBER_CAP = BLOCKS.register("ember_cap",
            () -> new HugeMushroomBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.RED_MUSHROOM_BLOCK)));
    public static final DeferredBlock<HugeMushroomBlock> PARASOL_CAP = BLOCKS.register("parasol_cap",
            () -> new HugeMushroomBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.BROWN_MUSHROOM_BLOCK).mapColor(MapColor.SAND)));
    public static final DeferredBlock<HugeMushroomBlock> GLOWCAP = BLOCKS.register("glowcap",
            () -> new HugeMushroomBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.RED_MUSHROOM_BLOCK).mapColor(MapColor.COLOR_PURPLE)
                    .lightLevel(state -> 13)));
    public static final DeferredBlock<HugeMushroomBlock> BELL_CAP = BLOCKS.register("bell_cap",
            () -> new HugeMushroomBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.RED_MUSHROOM_BLOCK).mapColor(MapColor.COLOR_MAGENTA)));
    public static final DeferredBlock<HugeMushroomBlock> GIANT_MUSHROOM_STEM = BLOCKS.register("giant_mushroom_stem",
            () -> new HugeMushroomBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.MUSHROOM_STEM)));
    public static final DeferredBlock<SmallShroomBlock> EMBER_SHROOM = BLOCKS.register("ember_shroom",
            () -> new SmallShroomBlock(plant(MapColor.COLOR_RED)));
    public static final DeferredBlock<SmallShroomBlock> GLOW_SHROOM = BLOCKS.register("glow_shroom",
            () -> new SmallShroomBlock(plant(MapColor.COLOR_PURPLE).lightLevel(state -> 8)));
    /** Skylupine: a tall purple lupine of cave mouths and cliff meadows (image 12). */
    public static final DeferredBlock<TallFlowerBlock> SKYLUPINE = BLOCKS.register("skylupine",
            () -> new TallFlowerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.LILAC)));
    /** Hanging ivy: green trailing ivy over cavern rims and cliff lips (image 13). */
    public static final DeferredBlock<LanternVineBlock> HANGING_IVY = BLOCKS.register("hanging_ivy",
            () -> new LanternVineBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).noCollission().instabreak()
                    .sound(SoundType.CAVE_VINES).pushReaction(PushReaction.DESTROY)));

    /** Every Halcyon block, in creative tab order. */
    public static List<DeferredBlock<? extends Block>> halcyonBlocks() {
        return List.of(STILLSTONE, STILLSTONE_BRICKS, MOSSY_STILLSTONE_BRICKS, CHISELED_STILLSTONE_BRICKS, STILLSTONE_PILLAR,
                STILLSTONE_BRICK_SLAB, STILLSTONE_BRICK_STAIRS, STILLSTONE_BRICK_WALL, STILLSTONE_GLASS, DUSKSTONE, COBBLED_DUSKSTONE,
                DUSKSTONE_BRICKS, POLISHED_DUSKSTONE, DUSKSTONE_PILLAR, SHOALROCK, SMOOTH_SHOALROCK, CLOUD, ROSE_CLOUD, DUSK_CLOUD,
                PALE_BLOSSOM_LEAVES, PETAL_CARPET, CLOUDMOSS, GLOW_MOSS, LANTERN_VINE, WISTERIA, BLOOMCORAL_PINK, BLOOMCORAL_VIOLET,
                BLOOMCORAL_WHITE, SMALL_PRISMITE_BUD, LARGE_PRISMITE_BUD, PRISMITE_CLUSTER, PRISMITE_LAMP, CRACKED_DUSKSTONE_BRICKS,
                MOSSY_DUSKSTONE_BRICKS, DUSKSTONE_TILES, CHISELED_DUSKSTONE, RUNED_DUSKSTONE, CLIFFSTONE, OCHRE_CLIFFSTONE, UMBER_CLIFFSTONE,
                MOSSY_CLIFFSTONE, HORNSTONE, ELDER_LOG, ELDER_WOOD, ELDER_LEAVES, DUSKWOOD_LOG, DUSKWOOD_WOOD, DUSKWOOD_LEAVES,
                SPIREPINE_NEEDLES, EMBER_CAP, PARASOL_CAP, GLOWCAP, BELL_CAP, GIANT_MUSHROOM_STEM, EMBER_SHROOM, GLOW_SHROOM, SKYLUPINE,
                HANGING_IVY);
    }

    /** The giant mushroom blocks, which share one model layout (a cap or stem face outside, gills or flesh inside). */
    public static List<DeferredBlock<HugeMushroomBlock>> mushroomBlocks() {
        return List.of(EMBER_CAP, PARASOL_CAP, GLOWCAP, BELL_CAP, GIANT_MUSHROOM_STEM);
    }

    private static BlockBehaviour.Properties wood(MapColor colour) {
        return BlockBehaviour.Properties.of().mapColor(colour).instrument(NoteBlockInstrument.BASS).strength(2.0f).sound(SoundType.WOOD)
                .ignitedByLava();
    }

    private static BlockBehaviour.Properties plant(MapColor colour) {
        return BlockBehaviour.Properties.of().mapColor(colour).noCollission().instabreak().sound(SoundType.GRASS)
                .offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.DESTROY);
    }

    private static BlockBehaviour.Properties stone() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops().strength(1.5f, 6f).sound(SoundType.STONE);
    }

    private static BlockBehaviour.Properties dusk() {
        return stone().mapColor(MapColor.COLOR_GRAY).strength(1.8f, 6f).sound(SoundType.DEEPSLATE);
    }

    private static BlockBehaviour.Properties bloomcoral(MapColor colour) {
        return BlockBehaviour.Properties.of().mapColor(colour).noCollission().instabreak().sound(SoundType.WET_GRASS)
                .offsetType(BlockBehaviour.OffsetType.XZ).pushReaction(PushReaction.DESTROY);
    }

    private static BlockBehaviour.Properties cloud(MapColor colour) {
        return BlockBehaviour.Properties.of().mapColor(colour).strength(0.4f).sound(SoundType.WOOL).noOcclusion()
                .isValidSpawn((state, level, pos, type) -> false).isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos) -> false);
    }

    private static BlockBehaviour.Properties prismite(int light) {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).forceSolidOn().noOcclusion().sound(SoundType.AMETHYST_CLUSTER)
                .strength(1.5f).lightLevel(state -> light).pushReaction(PushReaction.DESTROY);
    }

    private SkyseamBlocks() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
