package com.selluastar.skyseam.registry;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.seam.SeamEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SkyseamEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, Skyseam.MOD_ID);

    /**
     * The Seam: an entity, not a block, because it hangs in open air at any height (spec section 6). It is never
     * saved with its chunk: open Seams are kept in world data and mended when the world loads (spec section 19).
     * Tracked up to 16 chunks (256 blocks) away, so everyone within 256 blocks sees the reveal; a server whose view
     * distance is smaller sends it only that far.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<SeamEntity>> SEAM = ENTITY_TYPES.register("seam",
            () -> EntityType.Builder.<SeamEntity>of(SeamEntity::new, MobCategory.MISC)
                    .sized(1, 1)
                    .noSave()
                    .noSummon()
                    .fireImmune()
                    .clientTrackingRange(16)
                    .updateInterval(20)
                    .build("seam"));

    private SkyseamEntities() {}

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
    }
}
