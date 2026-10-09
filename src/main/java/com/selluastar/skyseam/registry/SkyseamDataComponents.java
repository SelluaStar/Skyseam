package com.selluastar.skyseam.registry;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.aperture.ApertureOwner;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SkyseamDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Skyseam.MOD_ID);

    /** The player a Harmonic Aperture is bound to (spec section 5). Kept on the item when the block is broken. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ApertureOwner>> OWNER = COMPONENTS.registerComponentType("owner",
            builder -> builder.persistent(ApertureOwner.CODEC).networkSynchronized(ApertureOwner.STREAM_CODEC));

    private SkyseamDataComponents() {}

    public static void register(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }
}
