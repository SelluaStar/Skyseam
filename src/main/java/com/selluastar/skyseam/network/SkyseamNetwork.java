package com.selluastar.skyseam.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Payload registration. The server decides, the client only shows (spec section 1, rule 10). */
public final class SkyseamNetwork {
    private SkyseamNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        // Client-bound handlers are lambdas so the client classes they call are only loaded on the client.
        registrar.playToClient(ScreenFlashPayload.TYPE, ScreenFlashPayload.STREAM_CODEC,
                (payload, context) -> com.selluastar.skyseam.client.ScreenFlash.start(payload.holdTicks()));
        registrar.playToClient(ApertureGaugePayload.TYPE, ApertureGaugePayload.STREAM_CODEC,
                (payload, context) -> com.selluastar.skyseam.client.aperture.ClientGauge.accept(payload));
        registrar.playToClient(SkychartPayload.TYPE, SkychartPayload.STREAM_CODEC,
                (payload, context) -> com.selluastar.skyseam.client.skychart.ClientSkychart.accept(payload));
    }

    public static void flash(ServerPlayer player, int holdTicks) {
        PacketDistributor.sendToPlayer(player, new ScreenFlashPayload(holdTicks));
    }
}
