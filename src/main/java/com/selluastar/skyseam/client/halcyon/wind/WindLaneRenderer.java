package com.selluastar.skyseam.client.halcyon.wind;

import java.util.List;

import org.joml.Matrix4fStack;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.client.SkyseamRenderTypes;
import com.selluastar.skyseam.halcyon.wind.WindLane;
import com.selluastar.skyseam.halcyon.wind.WindLanes;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Draws the wind lanes' ribbons (docs/DECISIONS.md K109): in each lane three soft ribbons of light, pale pink, gold and
 * sky blue, streaming along it a few blocks out from its middle like streamers in the wind. Each keeps its own side of
 * the middle, swaying a little to and fro and in and out, the sway running down the lane with the wind (winding right
 * round the middle read as coils from the side and as loops seen end-on); the three turn slowly round together. Each is
 * turned partly towards the camera so it is never seen edge-on, and streaked so the streaks run the way the wind blows
 * ({@code textures/environment/wind_ribbon.png}). They dim as the wind turns round and come back running the other
 * way; they fade towards the lane's ends and with distance, out to {@link #RANGE} blocks.
 *
 * <p>Drawn after the whole level, as the vortices' light is, so water, clouds and particles drawn late don't cover
 * them; tested against the world's depth but not writing it, so they show through each other.
 */
public final class WindLaneRenderer {
    private static final ResourceLocation TEXTURE = Skyseam.id("textures/environment/wind_ribbon.png");
    /** How far away ribbons are drawn, and where they start to fade. */
    private static final double RANGE = 640;
    private static final double FADE_FROM = 380;
    /** Each ribbon: its distance from the lane's middle, its width, its tint (RGB), and where on its circle it starts. */
    private static final double[] REACH = {3.5, 5.8, 7.6};
    private static final double[] WIDTH = {3.4, 2.4, 1.7};
    private static final int[] TINT = {0xFFD6E8, 0xFFEEC4, 0xD6ECFF};
    private static final double[] START = {0, 2.1, 4.2};
    /** How far a ribbon sways to and fro round the lane's middle, either way (radians), and the length of its sway in blocks over 2 pi. */
    private static final double SWAY = 0.3;
    private static final double SWAY_LENGTH = 60;
    /** How fast the three turn round the middle together, radians a tick. */
    private static final double TURN = 0.002;
    /** How far each ribbon is turned towards the camera, against winding flat round the lane's middle. */
    private static final double FACING = 0.8;
    /** Blocks along the lane for one length of the ribbon's texture. */
    private static final double TEXTURE_LENGTH = 32;
    /**
     * Fading in from a lane's loose end over this many blocks, so it doesn't stop short; and over {@link #JOIN_FADE} where
     * a lane runs on into another (one with points before or after it), whose ribbons sway out of step.
     */
    private static final double END_FADE = 100;
    private static final double JOIN_FADE = 30;

    /**
     * How far the wind has carried the streaks, in texture lengths, and the sways and gusts, in blocks; and the game time
     * they were last moved on at.
     */
    private static double scroll;
    private static double carried;
    private static double scrolledTo = Double.NaN;

    private WindLaneRenderer() {}

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        List<WindLane> lanes = ClientWindLanes.all();
        if (level == null || lanes.isEmpty()) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double time = level.getGameTime() + partial;
        double flow = ClientWindLanes.flow(time);
        // The streaks run with the wind: moved on by however far it blew since the last frame, so they slow and turn back with it.
        if (!Double.isNaN(scrolledTo) && time > scrolledTo && time - scrolledTo < 200) {
            double blown = flow * WindLanes.speed() / 20 * (time - scrolledTo);
            scroll += blown / TEXTURE_LENGTH;
            scroll -= Math.floor(scroll);
            carried += blown;
        }
        scrolledTo = time;
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        RenderType type = SkyseamRenderTypes.ribbon(TEXTURE);
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        try {
            VertexConsumer consumer = buffers.getBuffer(type);
            float visible = (float) (0.35 + 0.65 * Math.abs(flow));
            for (WindLane lane : lanes) {
                if (!lane.bounds().inflate(RANGE).contains(camera)) {
                    continue;
                }
                for (int ribbon = 0; ribbon < REACH.length; ribbon++) {
                    ribbon(consumer, lane, ribbon, camera, time, carried, visible);
                }
            }
            buffers.endBatch(type);
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
        }
    }

    /** One ribbon down a lane: a strip of quads, one for each step of the traced middle within range. */
    private static void ribbon(VertexConsumer consumer, WindLane lane, int ribbon, Vec3 camera, double time, double carried, float visible) {
        int tint = TINT[ribbon];
        double[] previous = null;
        float previousAlpha = 0;
        double previousV = 0;
        Vec3 lastSide = null;
        for (int k = 0; k < lane.size(); k++) {
            Vec3 middle = lane.sample(k);
            double distance = middle.distanceTo(camera);
            if (distance > RANGE + WindLane.RADIUS) {
                previous = null;
                continue;
            }
            Vec3 tangent = lane.tangent(k);
            Vec3 side = tangent.cross(new Vec3(0, 1, 0));
            // Where the lane climbs steeply, keep the last side, so the ribbons don't flip over.
            if (side.lengthSqr() < 0.04 && lastSide != null) {
                side = lastSide.subtract(tangent.scale(lastSide.dot(tangent)));
            }
            side = side.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : side.normalize();
            lastSide = side;
            Vec3 up = side.cross(tangent).normalize();
            double along = lane.along(k);
            // Sways and gusts run down the lane as the wind has carried them.
            double blown = along - carried;
            double angle = START[ribbon] + SWAY * Math.sin(blown / SWAY_LENGTH + ribbon * 1.9) + time * TURN;
            double reach = REACH[ribbon] * (1 + 0.22 * Math.sin(blown / 37 + ribbon * 1.7));
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            // Its middle on a circle round the lane's, and its width along that circle.
            double cx = middle.x + (side.x * cos + up.x * sin) * reach - camera.x;
            double cy = middle.y + (side.y * cos + up.y * sin) * reach - camera.y;
            double cz = middle.z + (side.z * cos + up.z * sin) * reach - camera.z;
            // Its width along that circle, turned partly to face the camera (across both the lane and the line to the eye).
            double rx = -side.x * sin + up.x * cos;
            double ry = -side.y * sin + up.y * cos;
            double rz = -side.z * sin + up.z * cos;
            double fx = tangent.y * cz - tangent.z * cy;
            double fy = tangent.z * cx - tangent.x * cz;
            double fz = tangent.x * cy - tangent.y * cx;
            double facing = Math.sqrt(fx * fx + fy * fy + fz * fz);
            // Close to the camera the line to it swings fast from one point to the next, which would twist the strip: there
            // it winds flat round the middle only, turning to the camera from 4 to 28 blocks off.
            double near = Mth.clamp((Math.sqrt(cx * cx + cy * cy + cz * cz) - 4) / 24, 0, 1);
            double turn = FACING * near * near * (3 - 2 * near);
            if (facing > 1.0e-6 && turn > 0) {
                double sign = rx * fx + ry * fy + rz * fz < 0 ? -turn : turn;
                rx += fx / facing * sign;
                ry += fy / facing * sign;
                rz += fz / facing * sign;
            }
            double width = Math.sqrt(rx * rx + ry * ry + rz * rz);
            double half = WIDTH[ribbon] / 2 / Math.max(1.0e-6, width);
            double wx = rx * half;
            double wy = ry * half;
            double wz = rz * half;
            double ends = Math.min(1, Math.min(along / (lane.before() != null ? JOIN_FADE : END_FADE),
                    (lane.length() - along) / (lane.after() != null ? JOIN_FADE : END_FADE)));
            double far = 1 - Mth.clamp((distance - FADE_FROM) / (RANGE - FADE_FROM), 0, 1);
            // Gusts: the ribbons brighten and fade a little along their length.
            double gust = 0.75 + 0.25 * Math.sin(blown / 23 + ribbon);
            float alpha = (float) (0.7 * visible * ends * far * gust);
            double v = along / TEXTURE_LENGTH - scroll;
            double[] here = {cx - wx, cy - wy, cz - wz, cx + wx, cy + wy, cz + wz};
            if (previous != null && (alpha > 0.004f || previousAlpha > 0.004f)) {
                vertex(consumer, previous[0], previous[1], previous[2], tint, previousAlpha, 0, previousV);
                vertex(consumer, previous[3], previous[4], previous[5], tint, previousAlpha, 1, previousV);
                vertex(consumer, here[3], here[4], here[5], tint, alpha, 1, v);
                vertex(consumer, here[0], here[1], here[2], tint, alpha, 0, v);
            }
            previous = here;
            previousAlpha = alpha;
            previousV = v;
        }
    }

    private static void vertex(VertexConsumer consumer, double x, double y, double z, int tint, float alpha, float u, double v) {
        consumer.addVertex((float) x, (float) y, (float) z).setColor(FastColor.ARGB32.color(Mth.clamp((int) (alpha * 255), 0, 255), tint))
                .setUv(u, (float) v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
    }
}
