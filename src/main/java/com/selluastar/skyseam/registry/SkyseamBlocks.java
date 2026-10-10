package com.selluastar.skyseam.registry;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.aperture.HarmonicApertureBlock;

import java.util.List;

import com.selluastar.skyseam.block.CloudBlock;
import com.selluastar.skyseam.block.LanternVineBlock;

import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.GlowLichenBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
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

    /** Every Halcyon block, in creative tab order. */
    public static List<DeferredBlock<? extends Block>> halcyonBlocks() {
        return List.of(STILLSTONE, STILLSTONE_BRICKS, MOSSY_STILLSTONE_BRICKS, CHISELED_STILLSTONE_BRICKS, STILLSTONE_PILLAR,
                STILLSTONE_BRICK_SLAB, STILLSTONE_BRICK_STAIRS, STILLSTONE_BRICK_WALL, STILLSTONE_GLASS, CLOUD, ROSE_CLOUD, DUSK_CLOUD,
                PETAL_CARPET, CLOUDMOSS, GLOW_MOSS, LANTERN_VINE, SMALL_PRISMITE_BUD, LARGE_PRISMITE_BUD, PRISMITE_CLUSTER);
    }

    private static BlockBehaviour.Properties stone() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.TERRACOTTA_WHITE).instrument(NoteBlockInstrument.BASEDRUM)
                .requiresCorrectToolForDrops().strength(1.5f, 6f).sound(SoundType.STONE);
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
