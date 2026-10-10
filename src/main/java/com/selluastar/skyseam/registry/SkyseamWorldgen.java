package com.selluastar.skyseam.registry;

import com.mojang.serialization.MapCodec;
import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.world.halcyon.HalcyonBiomeSource;
import com.selluastar.skyseam.world.halcyon.HalcyonChunkGenerator;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The Halcyon's chunk generator and biome source types, named in {@code data/skyseam/dimension/halcyon.json}. */
public final class SkyseamWorldgen {
    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(Registries.CHUNK_GENERATOR, Skyseam.MOD_ID);
    public static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, Skyseam.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends ChunkGenerator>, MapCodec<HalcyonChunkGenerator>> HALCYON_GENERATOR =
            CHUNK_GENERATORS.register("halcyon", () -> HalcyonChunkGenerator.CODEC);
    public static final DeferredHolder<MapCodec<? extends BiomeSource>, MapCodec<HalcyonBiomeSource>> HALCYON_BIOMES =
            BIOME_SOURCES.register("halcyon", () -> HalcyonBiomeSource.CODEC);

    private SkyseamWorldgen() {}

    public static void register(IEventBus modBus) {
        CHUNK_GENERATORS.register(modBus);
        BIOME_SOURCES.register(modBus);
    }
}
