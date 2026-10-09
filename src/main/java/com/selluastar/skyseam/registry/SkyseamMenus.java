package com.selluastar.skyseam.registry;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.aperture.ApertureMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SkyseamMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Skyseam.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<ApertureMenu>> APERTURE = MENUS.register("harmonic_aperture",
            () -> IMenuTypeExtension.create(ApertureMenu::fromNetwork));

    private SkyseamMenus() {}

    public static void register(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
