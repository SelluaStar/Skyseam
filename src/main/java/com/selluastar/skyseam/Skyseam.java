package com.selluastar.skyseam;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.selluastar.skyseam.command.SkyseamCommands;
import com.selluastar.skyseam.compat.fealty.FealtyCompat;
import com.selluastar.skyseam.config.SkyseamClientConfig;
import com.selluastar.skyseam.config.SkyseamConfig;
import com.selluastar.skyseam.dev.DevBootCheck;
import com.selluastar.skyseam.external.ExternalIds;
import com.selluastar.skyseam.network.SkyseamNetwork;
import com.selluastar.skyseam.registry.SkyseamEntities;
import com.selluastar.skyseam.registry.SkyseamParticles;
import com.selluastar.skyseam.registry.SkyseamSounds;
import com.selluastar.skyseam.seam.Seams;
import com.selluastar.skyseam.transfer.CrossingHolds;
import com.selluastar.skyseam.transfer.ShipTransfer;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(Skyseam.MOD_ID)
public final class Skyseam {
    public static final String MOD_ID = "skyseam";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Skyseam(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, SkyseamConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, SkyseamClientConfig.SPEC);
        SkyseamEntities.register(modBus);
        SkyseamSounds.register(modBus);
        SkyseamParticles.register(modBus);
        modBus.addListener(SkyseamNetwork::register);
        modBus.addListener(Skyseam::commonSetup);

        NeoForge.EVENT_BUS.addListener(DevBootCheck::onServerStarted);
        NeoForge.EVENT_BUS.addListener(Seams::onServerStarted);
        NeoForge.EVENT_BUS.addListener(SkyseamCommands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(ShipTransfer::onServerTick);
        NeoForge.EVENT_BUS.addListener(CrossingHolds::onLevelTick);
        NeoForge.EVENT_BUS.addListener(CrossingHolds::onServerStopping);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        ExternalIds.logLoadedVersions();
        FealtyCompat.logApiVersion();
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
