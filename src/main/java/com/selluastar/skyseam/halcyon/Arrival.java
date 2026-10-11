package com.selluastar.skyseam.halcyon;

import com.selluastar.skyseam.world.halcyon.HalcyonChunkGenerator;

import net.minecraft.commands.arguments.EntityAnchorArgument;
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
        HalcyonChunkGenerator.plan(player.serverLevel()).ifPresent(plan -> face(player, plan.landmarks.lookAt()));
    }

    /** Turns a player to look at {@code target}, without moving them. */
    public static void face(ServerPlayer player, Vec3 target) {
        face(player, target, 30);
    }

    /**
     * Turns a player to look at {@code target}, tilting up or down at most {@code limit} degrees. It sends vanilla's
     * look-at packet rather than a teleport: a teleport makes the server ignore the player's movement and turning
     * until their client confirms it, which aboard a Sable ship could leave the server holding a stale facing (blocks
     * then placed all facing one way).
     */
    public static void face(ServerPlayer player, Vec3 target, float limit) {
        Vec3 eye = player.getEyePosition();
        double dx = target.x - eye.x;
        double dz = target.z - eye.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        if (flat < 1.0e-3) {
            return;
        }
        // Clamp the tilt by moving the point looked at up or down, keeping its bearing.
        double rise = Mth.clamp(target.y - eye.y, -flat * Math.tan(limit * Mth.DEG_TO_RAD), flat * Math.tan(limit * Mth.DEG_TO_RAD));
        player.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(target.x, eye.y + rise, target.z));
    }
}
