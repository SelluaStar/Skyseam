package com.selluastar.skyseam;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.selluastar.skyseam.aperture.ApertureIndex;
import com.selluastar.skyseam.aperture.ApertureOwnership;
import com.selluastar.skyseam.command.ShipDrives;
import com.selluastar.skyseam.command.SkyseamCommands;
import com.selluastar.skyseam.compat.fealty.FealtyCompat;
import com.selluastar.skyseam.config.SkyseamClientConfig;
import com.selluastar.skyseam.config.SkyseamConfig;
import com.selluastar.skyseam.datagen.SkyseamDataGen;
import com.selluastar.skyseam.dev.DevBootCheck;
import com.selluastar.skyseam.external.ExternalIds;
import com.selluastar.skyseam.halcyon.LanternSun;
import com.selluastar.skyseam.halcyon.Rebound;
import com.selluastar.skyseam.halcyon.Veil;
import com.selluastar.skyseam.network.SkyseamNetwork;
import com.selluastar.skyseam.registry.SkyseamBlockEntities;
import com.selluastar.skyseam.registry.SkyseamBlocks;
import com.selluastar.skyseam.registry.SkyseamCreativeTabs;
import com.selluastar.skyseam.registry.SkyseamDataComponents;
import com.selluastar.skyseam.registry.SkyseamEntities;
import com.selluastar.skyseam.registry.SkyseamItems;
import com.selluastar.skyseam.registry.SkyseamMenus;
import com.selluastar.skyseam.registry.SkyseamParticles;
import com.selluastar.skyseam.registry.SkyseamSounds;
import com.selluastar.skyseam.registry.SkyseamWorldgen;
import com.selluastar.skyseam.seam.Seams;
import com.selluastar.skyseam.seam.site.SeamSites;
import com.selluastar.skyseam.seam.site.SiteKeeper;
import com.selluastar.skyseam.transfer.AbsentRiders;
import com.selluastar.skyseam.transfer.CrossingHolds;
import com.selluastar.skyseam.transfer.SeamCrossing;
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
        SkyseamDataComponents.register(modBus);
        SkyseamBlocks.register(modBus);
        SkyseamItems.register(modBus);
        SkyseamBlockEntities.register(modBus);
        SkyseamMenus.register(modBus);
        SkyseamCreativeTabs.register(modBus);
        SkyseamWorldgen.register(modBus);
        modBus.addListener(SkyseamNetwork::register);
        modBus.addListener(Skyseam::commonSetup);
        modBus.addListener(SkyseamDataGen::gatherData);

        NeoForge.EVENT_BUS.addListener(DevBootCheck::onServerStarted);
        NeoForge.EVENT_BUS.addListener(Seams::onServerStarted);
        NeoForge.EVENT_BUS.addListener(SkyseamCommands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(ShipTransfer::onServerTick);
        NeoForge.EVENT_BUS.addListener(CrossingHolds::onLevelTick);
        NeoForge.EVENT_BUS.addListener(CrossingHolds::onServerStopping);
        NeoForge.EVENT_BUS.addListener(SiteKeeper::onLevelTick);
        NeoForge.EVENT_BUS.addListener(ShipDrives::onLevelTick);
        NeoForge.EVENT_BUS.addListener(ShipDrives::onServerStopped);
        NeoForge.EVENT_BUS.addListener(SeamSites::onServerStopped);
        NeoForge.EVENT_BUS.addListener(ApertureIndex::onServerStopped);
        NeoForge.EVENT_BUS.addListener(SeamCrossing::onServerStopped);
        NeoForge.EVENT_BUS.addListener(AbsentRiders::onLoggedOut);
        NeoForge.EVENT_BUS.addListener(AbsentRiders::onLoggedIn);
        NeoForge.EVENT_BUS.addListener(ApertureOwnership::onItemCrafted);
        NeoForge.EVENT_BUS.addListener(LanternSun::onLoggedIn);
        NeoForge.EVENT_BUS.addListener(Veil::onLevelTick);
        NeoForge.EVENT_BUS.addListener(Rebound::onEntityTick);
        NeoForge.EVENT_BUS.addListener(Rebound::onLevelTick);
        NeoForge.EVENT_BUS.addListener(Rebound::onServerStopped);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        ExternalIds.logLoadedVersions();
        FealtyCompat.logApiVersion();
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
