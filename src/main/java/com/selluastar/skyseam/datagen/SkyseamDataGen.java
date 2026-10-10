package com.selluastar.skyseam.datagen;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * Data generation ({@code gradlew runData}, docs/DECISIONS.md K61): writes the blockstates, block and item models, loot
 * tables and block tags of the Halcyon's blocks into {@code src/generated/resources}. Files that predate it (the
 * Harmonic Aperture's) stay hand-written in {@code src/main/resources}; the lang file stays hand-written too.
 */
public final class SkyseamDataGen {
    private SkyseamDataGen() {}

    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput output = generator.getPackOutput();
        ExistingFileHelper files = event.getExistingFileHelper();
        CompletableFuture<HolderLookup.Provider> registries = event.getLookupProvider();

        generator.addProvider(event.includeClient(), new SkyseamBlockStates(output, files));
        generator.addProvider(event.includeServer(), new LootTableProvider(output, Set.of(),
                List.of(new LootTableProvider.SubProviderEntry(SkyseamBlockLoot::new, LootContextParamSets.BLOCK)), registries));
        generator.addProvider(event.includeServer(), new SkyseamBlockTags(output, registries, files));
    }
}
