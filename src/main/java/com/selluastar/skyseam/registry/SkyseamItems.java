package com.selluastar.skyseam.registry;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.skychart.SkychartItem;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SkyseamItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Skyseam.MOD_ID);

    public static final DeferredItem<BlockItem> HARMONIC_APERTURE = ITEMS.register("harmonic_aperture",
            () -> new BlockItem(SkyseamBlocks.HARMONIC_APERTURE.get(), new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));
    /** The Skychart (spec section 5): marks the nearest Seam site and, in an Aperture's slot, gives the gauge its site. */
    public static final DeferredItem<SkychartItem> SKYCHART = ITEMS.register("skychart",
            () -> new SkychartItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    private SkyseamItems() {}

    static {
        // A plain block item for every Halcyon block.
        SkyseamBlocks.halcyonBlocks().forEach(ITEMS::registerSimpleBlockItem);
    }

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
    }
}
