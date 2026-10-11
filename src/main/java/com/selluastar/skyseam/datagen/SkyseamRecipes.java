package com.selluastar.skyseam.datagen;

import java.util.concurrent.CompletableFuture;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.registry.SkyseamBlocks;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

/**
 * Recipes for the Halcyon's blocks. For now only the logs: each splits into four vanilla planks of a near colour
 * (Elder into spruce, Duskwood into dark oak) until the Halcyon has planks of its own.
 */
final class SkyseamRecipes extends RecipeProvider {
    SkyseamRecipes(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(RecipeOutput output) {
        planks(output, SkyseamBlocks.ELDER_LOG.get(), Items.SPRUCE_PLANKS, "elder_log");
        planks(output, SkyseamBlocks.ELDER_WOOD.get(), Items.SPRUCE_PLANKS, "elder_wood");
        planks(output, SkyseamBlocks.DUSKWOOD_LOG.get(), Items.DARK_OAK_PLANKS, "duskwood_log");
        planks(output, SkyseamBlocks.DUSKWOOD_WOOD.get(), Items.DARK_OAK_PLANKS, "duskwood_wood");
    }

    private static void planks(RecipeOutput output, ItemLike log, ItemLike planks, String name) {
        ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, planks, 4).requires(log).group("planks")
                .unlockedBy("has_" + name, has(log)).save(output, Skyseam.id("planks_from_" + name));
    }
}
