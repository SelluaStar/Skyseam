package com.selluastar.skyseam.halcyon;

import com.selluastar.skyseam.world.HalcyonLayout;

import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Beat 7's last moment (spec section 6): the Halcyon's arrival title, and every rider turned to look at the Sundered
 * Obelisk, so the first thing they see is the landmark (spec section 4: "the first thirty seconds after arrival should
 * make the player stop flying and look").
 */
public final class Arrival {
    /** Fade in, hold, fade out, in ticks: the title rises out of the crossing flash. */
    private static final int FADE_IN = 30;
    private static final int STAY = 90;
    private static final int FADE_OUT = 40;

    private Arrival() {}

    public static void welcome(ServerPlayer player) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(FADE_IN, STAY, FADE_OUT));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("skyseam.arrival.subtitle")));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("skyseam.arrival.title")));
        face(player, Vec3.atCenterOf(HalcyonLayout.OBELISK).add(0, 24, 0));
    }

    /** Turns a player to look at {@code target}, without moving them. */
    public static void face(ServerPlayer player, Vec3 target) {
        Vec3 eye = player.getEyePosition();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90;
        float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
        player.connection.teleport(player.getX(), player.getY(), player.getZ(), yaw, Mth.clamp(pitch, -30, 30));
    }
}
