package com.selluastar.skyseam.external;

import java.util.ArrayList;
import java.util.List;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The only class that turns Create's rotating parts (spec section 1, rule 9): a flux vortex scrambles a ship's
 * spinning parts (docs/DECISIONS.md K97).
 *
 * <p>Every spinning part (propellers, shafts, gears, engines) is given a random direction and speed by setting its
 * speed directly, as Create's own network does when it propagates. Aeronautics propellers take their thrust and their
 * spin from that speed, so each pushes a random way. Create only re-propagates when the network changes, so the
 * scrambled speeds hold; any that are reset are set again each tick. When the ship leaves, every part gets back the
 * speed it had, all at once, so the network is just as it was: re-propagating instead, part by part, would meet
 * neighbours still turning the wrong way, which Create treats as a conflict and breaks the block.
 */
public final class CreateBridge {
    private CreateBridge() {}

    /** One scrambled part: where it is, the speed it had and the speed it was given. */
    private record Part(BlockPos pos, float original, float scrambled) {}

    /** A ship's scrambled parts, to hold and later restore. */
    public static final class Scramble {
        private final Level level;
        private final List<Part> parts;

        private Scramble(Level level, List<Part> parts) {
            this.level = level;
            this.parts = parts;
        }

        public int size() {
            return parts.size();
        }
    }

    /** Gives every spinning part of the ship a random direction and a speed of half to twice what it had. */
    public static Scramble scramble(Ship ship, RandomSource random) {
        List<Part> parts = new ArrayList<>();
        Level level = null;
        for (BlockEntity be : SableBridge.blockEntities(ship)) {
            if (be instanceof KineticBlockEntity kinetic && kinetic.getTheoreticalSpeed() != 0 && be.getLevel() != null) {
                level = be.getLevel();
                float original = kinetic.getTheoreticalSpeed();
                float scrambled = Math.abs(original) * (0.5f + 1.5f * random.nextFloat()) * (random.nextBoolean() ? 1 : -1);
                set(kinetic, scrambled);
                parts.add(new Part(be.getBlockPos().immutable(), original, scrambled));
            }
        }
        return new Scramble(level, parts);
    }

    /**
     * Keeps the parts scrambled: sets again any part the network has reset to its own speed. A part whose network has
     * stopped (speed 0, its engine off or gone) is left alone, and later restored to stopped.
     */
    public static void hold(Scramble scramble) {
        if (scramble.level == null) {
            return;
        }
        for (Part part : scramble.parts) {
            if (scramble.level.isLoaded(part.pos) && scramble.level.getBlockEntity(part.pos) instanceof KineticBlockEntity kinetic) {
                float now = kinetic.getTheoreticalSpeed();
                if (now != 0 && now != part.scrambled) {
                    set(kinetic, part.scrambled);
                }
            }
        }
    }

    /** Puts every part that is still scrambled back to the speed it had. */
    public static void restore(Scramble scramble) {
        if (scramble.level == null) {
            return;
        }
        for (Part part : scramble.parts) {
            if (scramble.level.isLoaded(part.pos) && scramble.level.getBlockEntity(part.pos) instanceof KineticBlockEntity kinetic
                    && kinetic.getTheoreticalSpeed() == part.scrambled) {
                set(kinetic, part.original);
            }
        }
    }

    /** The speed of the rotating part at {@code pos}, or 0 if there is none. For GameTests. */
    public static float speedAt(Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof KineticBlockEntity kinetic ? kinetic.getTheoreticalSpeed() : 0;
    }

    private static void set(KineticBlockEntity kinetic, float speed) {
        float previous = kinetic.getSpeed();
        kinetic.setSpeed(speed);
        kinetic.onSpeedChanged(previous);
        kinetic.sendData();
    }
}
