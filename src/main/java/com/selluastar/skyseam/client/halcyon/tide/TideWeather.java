package com.selluastar.skyseam.client.halcyon.tide;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.halcyon.tide.Tide;
import com.selluastar.skyseam.halcyon.tide.Tides;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * The Tides' weather (docs/DECISIONS.md K113), drawn as vanilla draws rain: sheets in a ring of columns round the
 * camera, wherever the sky is open above, scrolling.
 *
 * <ul>
 * <li>Prismfall: slivers of glass falling fast, pale cyan, lilac and white, glinting (lit as by day, always).</li>
 * <li>Bloom: pollen drifting slowly down, golden.</li>
 * <li>Gale: streaks of air raking across on the crosswind, the sheets leaning hard its way.</li>
 * </ul>
 * Each comes in and dies away with its Tide, and thins towards the edge of the ring.
 */
public final class TideWeather {
    private static final ResourceLocation GLASS = Skyseam.id("textures/environment/glass_rain.png");
    private static final ResourceLocation POLLEN = Skyseam.id("textures/environment/pollen_fall.png");
    private static final ResourceLocation GALE = Skyseam.id("textures/environment/gale_streaks.png");
    /** How far round the camera the sheets stand, in blocks, and how tall they are above and below it. */
    private static final int RADIUS = 12;
    private static final int TALL = 14;

    private TideWeather() {}

    /** Draws the weather of the Tide running; false if there is none to draw (and vanilla's own weather is left to it). */
    public static boolean render(ClientLevel level, int ticks, float partialTick, LightTexture lightTexture, double camX, double camY, double camZ) {
        float prismfall = ClientTides.strength(Tide.PRISMFALL, partialTick);
        float bloom = ClientTides.strength(Tide.BLOOM, partialTick);
        float gale = ClientTides.strength(Tide.GALE, partialTick);
        if (prismfall <= 0 && bloom <= 0 && gale <= 0) {
            return false;
        }
        lightTexture.turnOnLightLayer();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(Minecraft.useShaderTransparency());
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getParticleShader);
        double time = ticks + partialTick;
        if (prismfall > 0) {
            sheets(level, GLASS, camX, camY, camZ, time, prismfall * 0.6f, 3.5, Vec3.ZERO, new float[] {0.86f, 0.93f, 1f}, true);
        }
        if (bloom > 0) {
            sheets(level, POLLEN, camX, camY, camZ, time, bloom * 0.9f, 0.3, Vec3.ZERO, new float[] {1f, 0.9f, 0.55f}, false);
        }
        if (gale > 0) {
            Vec3 way = Tides.crosswind((long) time, 1, 1).normalize();
            sheets(level, GALE, camX, camY, camZ, time, gale * 0.45f, 5, way, new float[] {0.92f, 0.95f, 1f}, false);
        }
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        lightTexture.turnOffLightLayer();
        return true;
    }

    /**
     * One kind of weather: a sheet in each open column round the camera, facing it, its texture scrolling down at
     * {@code speed} (sheets a tick); with {@code lean}, the sheets lean that way, so the streaks race across.
     */
    private static void sheets(ClientLevel level, ResourceLocation texture, double camX, double camY, double camZ, double time, float strength,
            double speed, Vec3 lean, float[] tint, boolean bright) {
        RenderSystem.setShaderTexture(0, texture);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        int cx = Mth.floor(camX);
        int cz = Mth.floor(camZ);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dz = -RADIUS; dz <= RADIUS; dz++) {
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > RADIUS) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                int bottom = Math.max(Mth.floor(camY) - TALL, ground);
                int top = Math.max(Mth.floor(camY) + TALL, ground);
                if (bottom >= top) {
                    continue;
                }
                int hash = (x * 3121 + x * x * 45238971 ^ z * 418711 + z * z * 13761) & 0xFF;
                float u0 = (hash & 3) * 0.25f;
                // As vanilla's rain: a quarter of the texture a block, scrolling down at the weather's speed.
                double scroll = (time * speed + hash * 3.17) / 32.0;
                double v0 = bottom * 0.25 - scroll;
                double v1 = top * 0.25 - scroll;
                // Facing the camera: across the line to it.
                double ox = x + 0.5 - camX;
                double oz = z + 0.5 - camZ;
                double len = Math.max(0.01, Math.sqrt(ox * ox + oz * oz));
                double sx = -oz / len * 0.5;
                double sz = ox / len * 0.5;
                double leanX = lean.x * (top - bottom) * 1.4;
                double leanZ = lean.z * (top - bottom) * 1.4;
                float fade = (float) (1 - distance / RADIUS);
                float alpha = Mth.clamp(strength * fade * 1.4f, 0, 1) * (0.55f + 0.45f * ((hash >> 2) & 3) / 3f);
                pos.set(x, Math.max(Mth.floor(camY), ground), z);
                int light = bright ? LightTexture.FULL_BRIGHT : LevelRenderer.getLightColor(level, pos);
                float yb = (float) (bottom - camY);
                float yt = (float) (top - camY);
                float xc = (float) (x + 0.5 - camX);
                float zc = (float) (z + 0.5 - camZ);
                buffer.addVertex((float) (xc - sx - leanX / 2), yb, (float) (zc - sz - leanZ / 2)).setUv(u0, (float) v1)
                        .setColor(tint[0], tint[1], tint[2], alpha).setLight(light);
                buffer.addVertex((float) (xc + sx - leanX / 2), yb, (float) (zc + sz - leanZ / 2)).setUv(u0 + 0.25f, (float) v1)
                        .setColor(tint[0], tint[1], tint[2], alpha).setLight(light);
                buffer.addVertex((float) (xc + sx + leanX / 2), yt, (float) (zc + sz + leanZ / 2)).setUv(u0 + 0.25f, (float) v0)
                        .setColor(tint[0], tint[1], tint[2], alpha).setLight(light);
                buffer.addVertex((float) (xc - sx + leanX / 2), yt, (float) (zc - sz + leanZ / 2)).setUv(u0, (float) v0)
                        .setColor(tint[0], tint[1], tint[2], alpha).setLight(light);
            }
        }
        MeshData mesh = buffer.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }
    }
}
