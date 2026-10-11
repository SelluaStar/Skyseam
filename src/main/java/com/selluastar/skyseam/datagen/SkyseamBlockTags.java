package com.selluastar.skyseam.datagen;

import java.util.concurrent.CompletableFuture;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.registry.SkyseamBlocks;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/** Block tags: which tool mines what, and the vanilla shape tags for the Stillstone slab, stairs and wall. */
final class SkyseamBlockTags extends BlockTagsProvider {
    SkyseamBlockTags(PackOutput output, CompletableFuture<HolderLookup.Provider> registries, ExistingFileHelper files) {
        super(output, registries, Skyseam.MOD_ID, files);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(SkyseamBlocks.HARMONIC_APERTURE.get(), SkyseamBlocks.STILLSTONE.get(),
                SkyseamBlocks.STILLSTONE_BRICKS.get(), SkyseamBlocks.MOSSY_STILLSTONE_BRICKS.get(), SkyseamBlocks.CHISELED_STILLSTONE_BRICKS.get(),
                SkyseamBlocks.STILLSTONE_PILLAR.get(), SkyseamBlocks.STILLSTONE_BRICK_SLAB.get(), SkyseamBlocks.STILLSTONE_BRICK_STAIRS.get(),
                SkyseamBlocks.STILLSTONE_BRICK_WALL.get(), SkyseamBlocks.SMALL_PRISMITE_BUD.get(), SkyseamBlocks.LARGE_PRISMITE_BUD.get(),
                SkyseamBlocks.PRISMITE_CLUSTER.get(), SkyseamBlocks.DUSKSTONE.get(), SkyseamBlocks.COBBLED_DUSKSTONE.get(),
                SkyseamBlocks.DUSKSTONE_BRICKS.get(), SkyseamBlocks.POLISHED_DUSKSTONE.get(), SkyseamBlocks.DUSKSTONE_PILLAR.get(),
                SkyseamBlocks.SHOALROCK.get(), SkyseamBlocks.SMOOTH_SHOALROCK.get(), SkyseamBlocks.PRISMITE_LAMP.get());
        tag(BlockTags.MINEABLE_WITH_PICKAXE).add(SkyseamBlocks.CRACKED_DUSKSTONE_BRICKS.get(), SkyseamBlocks.MOSSY_DUSKSTONE_BRICKS.get(),
                SkyseamBlocks.DUSKSTONE_TILES.get(), SkyseamBlocks.CHISELED_DUSKSTONE.get(), SkyseamBlocks.RUNED_DUSKSTONE.get(),
                SkyseamBlocks.CLIFFSTONE.get(), SkyseamBlocks.OCHRE_CLIFFSTONE.get(), SkyseamBlocks.UMBER_CLIFFSTONE.get(),
                SkyseamBlocks.MOSSY_CLIFFSTONE.get(), SkyseamBlocks.HORNSTONE.get());
        tag(BlockTags.LEAVES).add(SkyseamBlocks.PALE_BLOSSOM_LEAVES.get(), SkyseamBlocks.ELDER_LEAVES.get(), SkyseamBlocks.DUSKWOOD_LEAVES.get(),
                SkyseamBlocks.SPIREPINE_NEEDLES.get());
        tag(BlockTags.MINEABLE_WITH_HOE).add(SkyseamBlocks.PALE_BLOSSOM_LEAVES.get(), SkyseamBlocks.ELDER_LEAVES.get(),
                SkyseamBlocks.DUSKWOOD_LEAVES.get(), SkyseamBlocks.SPIREPINE_NEEDLES.get());
        tag(BlockTags.LOGS).add(SkyseamBlocks.ELDER_LOG.get(), SkyseamBlocks.ELDER_WOOD.get(), SkyseamBlocks.DUSKWOOD_LOG.get(),
                SkyseamBlocks.DUSKWOOD_WOOD.get());
        tag(BlockTags.MINEABLE_WITH_AXE).add(SkyseamBlocks.ELDER_LOG.get(), SkyseamBlocks.ELDER_WOOD.get(), SkyseamBlocks.DUSKWOOD_LOG.get(),
                SkyseamBlocks.DUSKWOOD_WOOD.get(), SkyseamBlocks.EMBER_CAP.get(), SkyseamBlocks.PARASOL_CAP.get(), SkyseamBlocks.GLOWCAP.get(),
                SkyseamBlocks.BELL_CAP.get(), SkyseamBlocks.GIANT_MUSHROOM_STEM.get());
        tag(BlockTags.TALL_FLOWERS).add(SkyseamBlocks.SKYLUPINE.get());
        tag(BlockTags.MINEABLE_WITH_SHOVEL).add(SkyseamBlocks.CLOUD.get(), SkyseamBlocks.ROSE_CLOUD.get(), SkyseamBlocks.DUSK_CLOUD.get());
        tag(BlockTags.SLABS).add(SkyseamBlocks.STILLSTONE_BRICK_SLAB.get());
        tag(BlockTags.STAIRS).add(SkyseamBlocks.STILLSTONE_BRICK_STAIRS.get());
        tag(BlockTags.WALLS).add(SkyseamBlocks.STILLSTONE_BRICK_WALL.get());
        tag(BlockTags.IMPERMEABLE).add(SkyseamBlocks.STILLSTONE_GLASS.get());
        tag(BlockTags.CRYSTAL_SOUND_BLOCKS).add(SkyseamBlocks.PRISMITE_CLUSTER.get());
    }
}
