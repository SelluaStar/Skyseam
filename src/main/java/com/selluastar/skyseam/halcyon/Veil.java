package com.selluastar.skyseam.halcyon;

import com.selluastar.skyseam.config.SkyseamConfig;
import com.selluastar.skyseam.external.SableBridge;
import com.selluastar.skyseam.external.Ship;
import com.selluastar.skyseam.registry.SkyseamParticles;
import com.selluastar.skyseam.transfer.CrossingHolds;
import com.selluastar.skyseam.world.HalcyonLayout;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * The Veil (spec section 7): the Halcyon's soft edge. There is no wall and no death. Over the outer 200 blocks of the
 * 1,500-block radius, and above the sky lid at Y 420, the fog thickens to pearl white and sound muffles (drawn by the
 * client), players feel a growing prismatic push back towards the middle, and ships are turned gently inward. Past
 * the rim, anyone heading out is carried back, and a ship flying outward is deflected like light off glass: the
 * outward part of its velocity is reflected.
 */
public final class Veil {
    /** Players are pushed every few ticks, so their clients are not flooded with motion updates. */
    private static final int PLAYER_PERIOD = 4;
    /** How fast someone past the rim is carried back, in blocks per tick. */
    private static final double CARRY_BACK = 0.3;
    /** How much of a ship's outward speed is kept when it is turned back at the rim. */
    private static final double DEFLECT_KEEP = 0.7;
    private static final int LID_WIDTH = 40;

    private Veil() {}

    /** How deep into the Veil a point is: 0 inside the clear world, 1 at the rim or the lid, more beyond. */
    public static double depth(double x, double y, double z) {
        double radial = (HalcyonLayout.radiusOf(x, z) - (HalcyonLayout.RADIUS - HalcyonLayout.VEIL_WIDTH)) / HalcyonLayout.VEIL_WIDTH;
        double lid = (y - (HalcyonLayout.LID - LID_WIDTH)) / LID_WIDTH;
        return Math.max(0, Math.max(radial, lid));
    }

    /** The unit direction a point is pushed: in towards the Spindle, and down when above the lid. */
    public static Vec3 inward(double x, double y, double z) {
        double r = HalcyonLayout.radiusOf(x, z);
        Vec3 in = r < 1 ? Vec3.ZERO : new Vec3(-x / r, 0, -z / r);
        double radialDepth = (r - (HalcyonLayout.RADIUS - HalcyonLayout.VEIL_WIDTH)) / HalcyonLayout.VEIL_WIDTH;
        double lidDepth = (y - (HalcyonLayout.LID - LID_WIDTH)) / LID_WIDTH;
        Vec3 push = in.scale(Math.max(0, radialDepth)).add(0, -Math.max(0, lidDepth), 0);
        return push.lengthSqr() < 1.0e-9 ? Vec3.ZERO : push.normalize();
    }

    /**
     * A player's new velocity (blocks per tick) after one push, {@link #PLAYER_PERIOD} ticks' worth: a push that
     * grows with the square of how deep into the Veil they are; past the rim, at least {@link #CARRY_BACK} inward.
     */
    public static Vec3 pushPlayer(Vec3 position, Vec3 velocity) {
        double depth = depth(position.x, position.y, position.z);
        if (depth <= 0) {
            return velocity;
        }
        Vec3 in = inward(position.x, position.y, position.z);
        double strength = SkyseamConfig.VEIL_PLAYER_PUSH.get() * PLAYER_PERIOD / 20.0 * Math.min(1, depth) * Math.min(1, depth);
        Vec3 pushed = velocity.add(in.scale(strength));
        if (depth >= 1) {
            double along = pushed.dot(in);
            if (along < CARRY_BACK) {
                pushed = pushed.add(in.scale(CARRY_BACK - along));
            }
        }
        return pushed;
    }

    /**
     * A ship's new velocity (blocks per second) after one tick: a gentle inward push in the Veil, and past the rim
     * the outward part of its velocity reflected back in, a little damped.
     */
    public static Vec3 deflectShip(Vec3 position, Vec3 velocity) {
        double depth = depth(position.x, position.y, position.z);
        if (depth <= 0) {
            return velocity;
        }
        Vec3 in = inward(position.x, position.y, position.z);
        Vec3 result = velocity.add(in.scale(SkyseamConfig.VEIL_SHIP_PUSH.get() * Math.min(1, depth) / 20.0));
        if (depth >= 1) {
            double outward = -result.dot(in);
            if (outward > 0) {
                result = result.add(in.scale(outward * (1 + DEFLECT_KEEP)));
            }
        }
        return result;
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != HalcyonLayout.LEVEL) {
            return;
        }
        if (level.getGameTime() % PLAYER_PERIOD == 0) {
            for (ServerPlayer player : level.players()) {
                if (player.isSpectator() || player.isPassenger()) {
                    continue;
                }
                // The client moves a player, so push from the movement it last reported, not the server's stale velocity.
                Vec3 velocity = player.getKnownMovement();
                Vec3 pushed = pushPlayer(player.position(), velocity);
                if (pushed != velocity) {
                    player.setDeltaMovement(pushed);
                    player.hurtMarked = true;
                    // The prismatic push: motes streaming past, so it is felt rather than unexplained.
                    level.sendParticles(SkyseamParticles.SEAM_MOTE.get(), player.getX(), player.getY() + 1, player.getZ(), 4, 0.6, 0.8, 0.6, 0.02);
                }
            }
        }
        for (Ship ship : SableBridge.allShips(level)) {
            if (CrossingHolds.isHeld(ship.id())) {
                continue;
            }
            Vec3 position = SableBridge.position(ship);
            if (depth(position.x, position.y, position.z) <= 0) {
                continue;
            }
            Vec3 velocity = SableBridge.linearVelocity(ship);
            SableBridge.addVelocity(ship, deflectShip(position, velocity).subtract(velocity));
        }
    }

    /** True if a point is anywhere in the Veil, for sounds and fog. */
    public static boolean isIn(double x, double y, double z) {
        return depth(x, y, z) > 0;
    }

    /** {@link #depth} clamped to 0 to 1, for how thick the fog is. */
    public static float thickness(double x, double y, double z) {
        return (float) Mth.clamp(depth(x, y, z), 0, 1);
    }
}
