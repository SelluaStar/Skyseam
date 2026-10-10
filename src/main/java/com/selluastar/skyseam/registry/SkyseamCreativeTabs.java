package com.selluastar.skyseam.registry;

import com.selluastar.skyseam.Skyseam;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SkyseamCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Skyseam.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.skyseam"))
            .icon(() -> SkyseamItems.HARMONIC_APERTURE.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(SkyseamItems.HARMONIC_APERTURE.get());
                output.accept(SkyseamItems.SKYCHART.get());
                SkyseamBlocks.halcyonBlocks().forEach(block -> output.accept(block.get()));
            })
            .build());

    private SkyseamCreativeTabs() {}

    public static void register(IEventBus modBus) {
        TABS.register(modBus);
    }
}
