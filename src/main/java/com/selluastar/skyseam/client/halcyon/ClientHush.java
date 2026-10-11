package com.selluastar.skyseam.client.halcyon;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.world.halcyon.HalcyonRegion;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

/**
 * How far into the Hush the camera is (0 to 1), for its standing dusk and muted sound (docs/DECISIONS.md K69), and how
 * far into the Elderwood, for its valley mist (K86). Biomes lie somewhere new in each world and the client doesn't know
 * the seed, so this reads the biomes the client already has: a 5 by 5 grid 16 blocks apart round the camera, weighted
 * towards the middle. Worked out once a tick.
 */
public final class ClientHush {
    private static final ResourceKey<Biome> HUSH = ResourceKey.create(Registries.BIOME, Skyseam.id(HalcyonRegion.HUSH.biome()));
    private static final ResourceKey<Biome> ELDERWOOD = ResourceKey.create(Registries.BIOME, Skyseam.id(HalcyonRegion.ELDERWOOD.biome()));
    private static long lastTick = Long.MIN_VALUE;
    private static BlockPos lastPos = BlockPos.ZERO;
    private static float lastHush;
    private static float lastMist;

    private ClientHush() {}

    /** How far into the Hush a point is, 0 to 1. */
    public static float at(double x, double y, double z) {
        sample(x, y, z);
        return lastHush;
    }

    /** How far into the Elderwood a point is, 0 to 1. */
    public static float mist(double x, double y, double z) {
        sample(x, y, z);
        return lastMist;
    }

    private static void sample(double x, double y, double z) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            lastHush = 0;
            lastMist = 0;
            return;
        }
        BlockPos centre = BlockPos.containing(x, y, z);
        if (level.getGameTime() == lastTick && centre.distManhattan(lastPos) < 4) {
            return;
        }
        float total = 0;
        float hush = 0;
        float mist = 0;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int i = -2; i <= 2; i++) {
            for (int j = -2; j <= 2; j++) {
                float weight = 3 - (Math.abs(i) + Math.abs(j)) * 0.5f;
                total += weight;
                Holder<Biome> biome = level.getBiome(at.set(centre.getX() + i * 16, Math.max(centre.getY(), 160), centre.getZ() + j * 16));
                if (biome.is(HUSH)) {
                    hush += weight;
                } else if (biome.is(ELDERWOOD)) {
                    mist += weight;
                }
            }
        }
        lastTick = level.getGameTime();
        lastPos = centre;
        lastHush = hush / total;
        lastMist = mist / total;
    }
}
