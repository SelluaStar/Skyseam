package com.selluastar.skyseam.client;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.aperture.ApertureBlockEntity;
import com.selluastar.skyseam.client.aperture.ApertureChargeSound;
import com.selluastar.skyseam.client.aperture.ApertureRenderer;
import com.selluastar.skyseam.client.flora.MurmurberryRenderer;
import com.selluastar.skyseam.client.flora.SpringbloomRenderer;
import com.selluastar.skyseam.client.particle.SporeParticle;
import com.selluastar.skyseam.client.aperture.ApertureScreen;
import com.selluastar.skyseam.client.aperture.ClientGauge;
import com.selluastar.skyseam.client.aperture.SeamGaugeHud;
import com.selluastar.skyseam.client.compat.DistantHorizonsBlocks;
import com.selluastar.skyseam.client.skychart.ClientSkychart;
import com.selluastar.skyseam.client.skychart.SkychartHud;
import com.selluastar.skyseam.client.dev.DevSceneCapture;
import com.selluastar.skyseam.client.halcyon.HalcyonFog;
import com.selluastar.skyseam.client.halcyon.HalcyonSky;
import com.selluastar.skyseam.client.halcyon.VeilMuffle;
import com.selluastar.skyseam.client.halcyon.vortex.ClientVortices;
import com.selluastar.skyseam.client.halcyon.vortex.FarDepth;
import com.selluastar.skyseam.client.halcyon.vortex.VortexRenderer;
import com.selluastar.skyseam.client.halcyon.HalcyonAmbience;
import com.selluastar.skyseam.client.halcyon.tide.ClientTides;
import com.selluastar.skyseam.client.halcyon.tide.TideBadge;
import com.selluastar.skyseam.client.halcyon.wind.ClientWindLanes;
import com.selluastar.skyseam.client.halcyon.wind.WindLaneRenderer;
import com.selluastar.skyseam.client.particle.GlowParticle;
import com.selluastar.skyseam.client.particle.PetalParticle;
import com.selluastar.skyseam.client.particle.VortexParticle;
import com.selluastar.skyseam.client.particle.PollenParticle;
import com.selluastar.skyseam.client.particle.ShardGlintParticle;
import com.selluastar.skyseam.client.particle.WindMoteParticle;
import com.selluastar.skyseam.client.particle.WispParticle;
import com.selluastar.skyseam.client.seam.SeamClientEffects;
import com.selluastar.skyseam.client.seam.SeamRenderer;
import com.selluastar.skyseam.registry.SkyseamBlockEntities;
import com.selluastar.skyseam.registry.SkyseamEntities;
import com.selluastar.skyseam.registry.SkyseamMenus;
import com.selluastar.skyseam.registry.SkyseamParticles;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only registration: renderers, screens, particle providers, the HUD layers and the per-tick effects. */
@EventBusSubscriber(modid = Skyseam.MOD_ID, value = Dist.CLIENT)
public final class SkyseamClient {
    private SkyseamClient() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.addListener(SeamClientEffects::onClientTick);
        NeoForge.EVENT_BUS.addListener(DistantHorizonsBlocks::onClientTick);
        NeoForge.EVENT_BUS.addListener(SeamClientEffects::onComputeCameraAngles);
        NeoForge.EVENT_BUS.addListener(HalcyonFog::onFogColour);
        NeoForge.EVENT_BUS.addListener(HalcyonFog::onRenderFog);
        NeoForge.EVENT_BUS.addListener(VeilMuffle::onPlaySound);
        NeoForge.EVENT_BUS.addListener(ClientVortices::onClientTick);
        NeoForge.EVENT_BUS.addListener(VortexRenderer::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(ClientWindLanes::onClientTick);
        NeoForge.EVENT_BUS.addListener(WindLaneRenderer::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(ClientTides::onClientTick);
        NeoForge.EVENT_BUS.addListener(HalcyonAmbience::onClientTick);
        // The in-game config screen (Mods > Skyseam > Config), for the shake and flash strength among others.
        ModList.get().getModContainerById(Skyseam.MOD_ID)
                .ifPresent(container -> container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new));
        DevSceneCapture.install();
        ApertureBlockEntity.clientTicker = ApertureChargeSound::tick;
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut loggingOut) -> {
            ClientGauge.clear();
            ClientSkychart.clear();
            ClientVortices.clear();
            ClientWindLanes.clear();
            ClientTides.clear();
            HalcyonAmbience.clear();
        });
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(SkyseamEntities.SEAM.get(), SeamRenderer::new);
        event.registerBlockEntityRenderer(SkyseamBlockEntities.APERTURE.get(), ApertureRenderer::new);
        event.registerBlockEntityRenderer(SkyseamBlockEntities.SPRINGBLOOM.get(), SpringbloomRenderer::new);
        event.registerBlockEntityRenderer(SkyseamBlockEntities.MURMURBERRY.get(), MurmurberryRenderer::new);
    }

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws java.io.IOException {
        FarDepth.register(event);
    }

    @SubscribeEvent
    public static void registerSkies(RegisterDimensionSpecialEffectsEvent event) {
        event.register(HalcyonSky.ID, new HalcyonSky());
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(SkyseamMenus.APERTURE.get(), ApertureScreen::new);
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(SkyseamParticles.SEAM_MOTE.get(), sprites -> new GlowParticle.Provider(sprites, GlowParticle.Kind.MOTE));
        event.registerSpriteSet(SkyseamParticles.SEAM_SPARK.get(), sprites -> new GlowParticle.Provider(sprites, GlowParticle.Kind.SPARK));
        event.registerSpriteSet(SkyseamParticles.THREAD_SNAP.get(), sprites -> new GlowParticle.Provider(sprites, GlowParticle.Kind.THREAD));
        event.registerSpriteSet(SkyseamParticles.SCAR.get(), sprites -> new GlowParticle.Provider(sprites, GlowParticle.Kind.SCAR));
        event.registerSpriteSet(SkyseamParticles.WISP.get(), WispParticle.Provider::new);
        event.registerSpriteSet(SkyseamParticles.FLUX_MOTE.get(), sprites -> new VortexParticle.Provider(sprites, VortexParticle.Kind.MOTE));
        event.registerSpriteSet(SkyseamParticles.FLUX_DUST.get(), sprites -> new VortexParticle.Provider(sprites, VortexParticle.Kind.DUST));
        event.registerSpriteSet(SkyseamParticles.FLUX_GLINT.get(), sprites -> new VortexParticle.Provider(sprites, VortexParticle.Kind.GLINT));
        event.registerSpriteSet(SkyseamParticles.FLUX_SPARK.get(), sprites -> new GlowParticle.Provider(sprites, GlowParticle.Kind.SPARK));
        event.registerSpriteSet(SkyseamParticles.SPRINGBLOOM_SPORE.get(), SporeParticle.Provider::new);
        event.registerSpriteSet(SkyseamParticles.PETAL.get(), PetalParticle.Provider::new);
        event.registerSpriteSet(SkyseamParticles.WIND_MOTE.get(), WindMoteParticle.Provider::new);
        event.registerSpriteSet(SkyseamParticles.SHARD_GLINT.get(), ShardGlintParticle.Provider::new);
        event.registerSpriteSet(SkyseamParticles.POLLEN.get(), PollenParticle.Provider::new);
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, Skyseam.id("seam_gauge"), SeamGaugeHud::render);
        event.registerAbove(VanillaGuiLayers.HOTBAR, Skyseam.id("skychart"), SkychartHud::render);
        event.registerAbove(VanillaGuiLayers.BOSS_OVERLAY, Skyseam.id("tide_badge"), TideBadge::render);
        event.registerAboveAll(Skyseam.id("screen_flash"), ScreenFlash::render);
    }
}
