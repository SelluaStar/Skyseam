package com.selluastar.skyseam.client.flora;

import com.selluastar.skyseam.halcyon.flora.MurmurberryBlockEntity;
import com.selluastar.skyseam.halcyon.flora.MurmurberryIndex;
import com.selluastar.skyseam.halcyon.flora.Murmurberries;
import com.selluastar.skyseam.network.MurmurPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

/**
 * The client's half of the Murmurberries (docs/DECISIONS.md K106): when the server sends a sound they heard
 * ({@link MurmurPayload}), each loaded Murmurberry within its range is told when the light reaches it, later the
 * further it is from the sound ({@link Murmurberries#delayTicks}); it lights up then ({@link MurmurberryBlockEntity}).
 */
public final class ClientMurmurberries {
    private ClientMurmurberries() {}

    public static void ripple(MurmurPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        MurmurberryIndex index = MurmurberryIndex.of(level, false);
        if (index == null || index.isEmpty()) {
            return;
        }
        Vec3 at = new Vec3(payload.x(), payload.y(), payload.z());
        long now = level.getGameTime();
        index.forEachWithin(at, payload.range(), pos -> {
            if (level.getBlockEntity(pos) instanceof MurmurberryBlockEntity berry) {
                double distance = Math.sqrt(pos.getCenter().distanceToSqr(at));
                berry.hear(now + Murmurberries.delayTicks(distance, payload.range(), payload.maxDelayTicks()));
            }
        });
    }
}
