package com.selluastar.skyseam.datagen;

import java.util.Set;

import com.selluastar.skyseam.registry.SkyseamBlocks;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * Loot for the Halcyon's blocks. Stone, wood, clouds and carpets drop themselves; glass and the two smaller Prismite
 * buds need Silk Touch, and glow moss, vines and leaves need shears, as their vanilla cousins do. Giant mushroom caps
 * drop small mushrooms (themselves with Silk Touch), as vanilla's do. The full Prismite cluster drops itself until
 * Prism Dust exists (M5).
 */
final class SkyseamBlockLoot extends BlockLootSubProvider {
    SkyseamBlockLoot(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    protected void generate() {
        for (DeferredBlock<? extends Block> block : SkyseamBlocks.halcyonBlocks()) {
            Block b = block.get();
            if (block == SkyseamBlocks.STILLSTONE_BRICK_SLAB) {
                add(b, createSlabItemTable(b));
            } else if (block == SkyseamBlocks.STILLSTONE_GLASS || block == SkyseamBlocks.SMALL_PRISMITE_BUD
                    || block == SkyseamBlocks.LARGE_PRISMITE_BUD) {
                dropWhenSilkTouch(b);
            } else if (block == SkyseamBlocks.GLOW_MOSS) {
                add(b, createMultifaceBlockDrops(b, HAS_SHEARS));
            } else if (block == SkyseamBlocks.LANTERN_VINE || block == SkyseamBlocks.WISTERIA || block == SkyseamBlocks.PALE_BLOSSOM_LEAVES
                    || block == SkyseamBlocks.HANGING_IVY || b instanceof LeavesBlock) {
                add(b, createShearsOnlyDrop(b));
            } else if (block == SkyseamBlocks.GIANT_MUSHROOM_STEM) {
                dropWhenSilkTouch(b);
            } else if (b instanceof HugeMushroomBlock) {
                // As vanilla's caps: up to two small mushrooms, or the block itself with Silk Touch.
                add(b, createMushroomBlockDrop(b, block == SkyseamBlocks.GLOWCAP ? SkyseamBlocks.GLOW_SHROOM.get() : SkyseamBlocks.EMBER_SHROOM.get()));
            } else if (block == SkyseamBlocks.SKYLUPINE) {
                add(b, createSinglePropConditionTable(b, DoublePlantBlock.HALF, DoubleBlockHalf.LOWER));
            } else {
                dropSelf(b);
            }
        }
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return SkyseamBlocks.halcyonBlocks().stream().<Block>map(DeferredBlock::get).toList();
    }
}
