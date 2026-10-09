package com.selluastar.skyseam.client;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.aperture.ApertureBlockEntity;
import com.selluastar.skyseam.client.aperture.ApertureChargeSound;
import com.selluastar.skyseam.client.aperture.ApertureRenderer;
import com.selluastar.skyseam.client.aperture.ApertureScreen;
import com.selluastar.skyseam.client.aperture.ClientGauge;
import com.selluastar.skyseam.client.aperture.SeamGaugeHud;
import com.selluastar.skyseam.client.skychart.ClientSkychart;
import com.selluastar.skyseam.client.skychart.SkychartHud;
import com.selluastar.skyseam.client.dev.DevSceneCapture;
import com.selluastar.skyseam.client.particle.GlowParticle;
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
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
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
        NeoForge.EVENT_BUS.addListener(SeamClientEffects::onComputeCameraAngles);
        // The in-game config screen (Mods > Skyseam > Config), for the shake and flash strength among others.
        ModList.get().getModContainerById(Skyseam.MOD_ID)
                .ifPresent(container -> container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new));
        DevSceneCapture.install();
        ApertureBlockEntity.clientTicker = ApertureChargeSound::tick;
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut loggingOut) -> {
            ClientGauge.clear();
            ClientSkychart.clear();
        });
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(SkyseamEntities.SEAM.get(), SeamRenderer::new);
        event.registerBlockEntityRenderer(SkyseamBlockEntities.APERTURE.get(), ApertureRenderer::new);
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
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, Skyseam.id("seam_gauge"), SeamGaugeHud::render);
        event.registerAbove(VanillaGuiLayers.HOTBAR, Skyseam.id("skychart"), SkychartHud::render);
        event.registerAboveAll(Skyseam.id("screen_flash"), ScreenFlash::render);
    }
}
