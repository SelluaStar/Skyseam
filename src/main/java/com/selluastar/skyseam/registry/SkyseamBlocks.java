package com.selluastar.skyseam.registry;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.aperture.HarmonicApertureBlock;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class SkyseamBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Skyseam.MOD_ID);

    /** The Harmonic Aperture (spec section 11): opens the Seam when mounted on a flying ship. */
    public static final DeferredBlock<HarmonicApertureBlock> HARMONIC_APERTURE = BLOCKS.register("harmonic_aperture",
            () -> new HarmonicApertureBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_YELLOW)
                    .strength(3.5f, 1200f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.COPPER)
                    .lightLevel(state -> 7)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)));

    private SkyseamBlocks() {}

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
