package com.selluastar.skyseam.client;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.client.dev.DevSceneCapture;
import com.selluastar.skyseam.client.particle.GlowParticle;
import com.selluastar.skyseam.client.seam.SeamClientEffects;
import com.selluastar.skyseam.client.seam.SeamRenderer;
import com.selluastar.skyseam.registry.SkyseamEntities;
import com.selluastar.skyseam.registry.SkyseamParticles;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only registration: renderers, particle providers, the flash layer and the per-tick effects. */
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
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(SkyseamEntities.SEAM.get(), SeamRenderer::new);
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
        event.registerAboveAll(Skyseam.id("screen_flash"), ScreenFlash::render);
    }
}
