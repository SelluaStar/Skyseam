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
                SkyseamBlocks.PRISMITE_CLUSTER.get());
        tag(BlockTags.MINEABLE_WITH_SHOVEL).add(SkyseamBlocks.CLOUD.get(), SkyseamBlocks.ROSE_CLOUD.get(), SkyseamBlocks.DUSK_CLOUD.get());
        tag(BlockTags.SLABS).add(SkyseamBlocks.STILLSTONE_BRICK_SLAB.get());
        tag(BlockTags.STAIRS).add(SkyseamBlocks.STILLSTONE_BRICK_STAIRS.get());
        tag(BlockTags.WALLS).add(SkyseamBlocks.STILLSTONE_BRICK_WALL.get());
        tag(BlockTags.IMPERMEABLE).add(SkyseamBlocks.STILLSTONE_GLASS.get());
        tag(BlockTags.CRYSTAL_SOUND_BLOCKS).add(SkyseamBlocks.PRISMITE_CLUSTER.get());
    }
}
