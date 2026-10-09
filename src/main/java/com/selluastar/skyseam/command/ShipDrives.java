package com.selluastar.skyseam.command;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.selluastar.skyseam.external.SableBridge;
import com.selluastar.skyseam.external.Ship;
import com.selluastar.skyseam.transfer.CrossingHolds;
import com.selluastar.skyseam.transfer.SeamCrossing;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Debug and filming ({@code /skyseam ship drive}): flies a ship along a straight line at a set velocity for a while,
 * level and steady, without propellers or balloons. Each tick it sets the ship's velocity (holding its height and
 * stopping any spin), so the physics moves it and carries whoever stands on its deck. Stops when the ship starts
 * crossing a Seam.
 */
public final class ShipDrives {
    private static final List<Drive> DRIVES = new ArrayList<>();

    private static final class Drive {
        final ResourceKey<Level> dimension;
        final UUID ship;
        final Vec3 velocity;
        final double startY;
        int ticksLeft;
        int elapsed;

        Drive(ResourceKey<Level> dimension, UUID ship, Vec3 velocity, double startY, int ticksLeft) {
            this.dimension = dimension;
            this.ship = ship;
            this.velocity = velocity;
            this.startY = startY;
            this.ticksLeft = ticksLeft;
        }
    }

    private ShipDrives() {}

    /** Drives {@code ship} at {@code velocity} blocks per second for {@code ticks} ticks. Replaces an earlier drive. */
    public static void start(Ship ship, Vec3 velocity, int ticks) {
        DRIVES.removeIf(drive -> drive.ship.equals(ship.id()));
        DRIVES.add(new Drive(ship.level().dimension(), ship.id(), velocity, SableBridge.position(ship).y, ticks));
    }

    public static void onLevelTick(LevelTickEvent.Pre event) {
        if (DRIVES.isEmpty() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Iterator<Drive> drives = DRIVES.iterator();
        while (drives.hasNext()) {
            Drive drive = drives.next();
            if (drive.dimension != level.dimension()) {
                continue;
            }
            Optional<Ship> found = SableBridge.find(level, drive.ship);
            if (found.isEmpty() || drive.ticksLeft-- <= 0 || SeamCrossing.isUnderWay(drive.ship) || CrossingHolds.isHeld(drive.ship)) {
                drives.remove();
                continue;
            }
            Ship ship = found.get();
            // The wanted velocity, plus a pull back to the starting height (or along the climb, for a vertical drive).
            double heightError = drive.startY + drive.velocity.y * (drive.elapsed++ / 20.0) - SableBridge.position(ship).y;
            Vec3 wanted = drive.velocity.add(0, 2 * heightError, 0);
            SableBridge.addVelocity(ship, wanted.subtract(SableBridge.linearVelocity(ship)), SableBridge.angularVelocity(ship).scale(-1));
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        DRIVES.clear();
    }
}
