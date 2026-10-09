package com.selluastar.skyseam.registry;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.aperture.ApertureBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SkyseamBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Skyseam.MOD_ID);

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ApertureBlockEntity>> APERTURE = BLOCK_ENTITIES.register("harmonic_aperture",
            () -> BlockEntityType.Builder.of(ApertureBlockEntity::new, SkyseamBlocks.HARMONIC_APERTURE.get()).build(null));

    private SkyseamBlockEntities() {}

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}
