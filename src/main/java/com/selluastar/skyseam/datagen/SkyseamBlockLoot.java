package com.selluastar.skyseam.datagen;

import java.util.Set;

import com.selluastar.skyseam.registry.SkyseamBlocks;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredBlock;

/**
 * Loot for the Halcyon's blocks. Stone, clouds and carpets drop themselves; glass and the two smaller Prismite buds
 * need Silk Touch, and glow moss and lantern vines need shears, as their vanilla cousins do. The full Prismite
 * cluster drops itself until Prism Dust exists (M5).
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
            } else if (block == SkyseamBlocks.LANTERN_VINE) {
                add(b, createShearsOnlyDrop(b));
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
