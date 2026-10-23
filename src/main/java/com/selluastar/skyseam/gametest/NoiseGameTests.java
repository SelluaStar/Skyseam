package com.selluastar.skyseam.gametest;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.echo.NoiseBus;
import com.selluastar.skyseam.halcyon.tide.Tide;
import com.selluastar.skyseam.halcyon.tide.TideSchedule;
import com.selluastar.skyseam.halcyon.tide.Tides;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The noise bus (docs/DECISIONS.md K112): who hears a noise, how far the Tides carry it, and what makes noise. */
@GameTestHolder(Skyseam.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NoiseGameTests {
    private static final String EMPTY = "gametest/empty";

    private NoiseGameTests() {}

    /** A listener at a place, noting what it hears, and itself in a log shared with others, in the order they heard. */
    private record Ear(ServerLevel where, Vec3 ear, double hearing, List<Double> heard, List<Ear> log) implements NoiseBus.Listener {
        Ear(ServerLevel where, Vec3 ear, double hearing, List<Ear> log) {
            this(where, ear, hearing, new ArrayList<>(), log);
        }

        @Nullable
        @Override
        public ServerLevel level() {
            return where;
        }

        @Override
        public void hear(NoiseBus.Noise noise, double distance) {
            heard.add(distance);
            log.add(this);
        }
    }

    /**
     * A noise reaches the listeners within its radius and no others, nearest first, and one with keener hearing further
     * off; a muffled-out noise reaches none.
     */
    @GameTest(template = EMPTY)
    public static void noiseReachesListenersWithinItsRadius(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 origin = helper.absoluteVec(new Vec3(1.5, 2, 1.5));
        List<Ear> log = new ArrayList<>();
        Ear near = new Ear(level, origin.add(10, 0, 0), 1, log);
        Ear far = new Ear(level, origin.add(30, 0, 0), 1, log);
        Ear keen = new Ear(level, origin.add(0, 0, 30), 2, log);
        Ear nearest = new Ear(level, origin.add(5, 0, 0), 1, log);
        List<NoiseBus.Listener> ears = List.of(near, far, keen, nearest);
        ears.forEach(NoiseBus::listen);
        try {
            NoiseBus.Noise noise = NoiseBus.emit(level, origin, 20, NoiseBus.Kind.OTHER, null);
            helper.assertTrue(noise != null && Math.abs(noise.radius() - 20) < 1.0e-6, "The noise carries " + (noise == null ? 0 : noise.radius()));
            helper.assertTrue(near.heard().size() == 1 && Math.abs(near.heard().get(0) - 10) < 1.0e-6, "The near listener heard " + near.heard());
            helper.assertTrue(far.heard().isEmpty(), "The far listener heard " + far.heard());
            helper.assertTrue(keen.heard().size() == 1, "The keen listener didn't hear it");
            helper.assertTrue(log.size() == 3 && log.get(0) == nearest && log.get(2) == keen, "They didn't hear it nearest first: " + log.size());
        } finally {
            ears.forEach(NoiseBus::stopListening);
        }
        helper.succeed();
    }

    /**
     * A Stillness carries noise twice as far and a Prismfall or Gale 0.7 as far (easing in with the Tide), a Bloom as
     * ever; and outside the Halcyon the Tides change nothing.
     */
    @GameTest(template = EMPTY, batch = "m4_noise_tides")
    public static void theTidesCarryNoise(GameTestHelper helper) {
        long start = 10_000;
        for (Tide tide : new Tide[] {Tide.STILLNESS, Tide.PRISMFALL, Tide.GALE, Tide.BLOOM}) {
            TideSchedule.Forecast forecast = new TideSchedule.Forecast(new TideSchedule.Window(tide, start, start + 8000), null);
            double expected = switch (tide) {
                case STILLNESS -> 2;
                case PRISMFALL, GALE -> 0.7;
                default -> 1;
            };
            double during = Tides.noiseFactor(forecast, start + 4000);
            helper.assertTrue(Math.abs(during - expected) < 1.0e-6, "In a " + tide + " noise carries " + during + " as far, not " + expected);
            helper.assertTrue(Tides.noiseFactor(forecast, start - 10) == 1, "Before a " + tide + " noise carries " + Tides.noiseFactor(forecast, start - 10));
            double coming = Tides.noiseFactor(forecast, start + TideSchedule.RAMP / 2.0);
            helper.assertTrue(Math.abs(coming - (1 + expected) / 2) < 1.0e-6, "Half way in, a " + tide + " carries noise " + coming + " as far");
        }
        MinecraftServer server = helper.getLevel().getServer();
        long now = server.overworld().getGameTime();
        try {
            Tides.force(server, new TideSchedule.Window(Tide.STILLNESS, now - 2000, now + 4000));
            double outside = NoiseBus.carry(helper.getLevel(), helper.absoluteVec(new Vec3(1, 2, 1)), 20, null);
            helper.assertTrue(Math.abs(outside - 20) < 1.0e-6, "Outside the Halcyon in a Stillness a noise of 20 carries " + outside);
        } finally {
            Tides.unforce(server);
        }
        helper.succeed();
    }

    /** A struck bell and an explosion make noise, of their kinds, where they are. */
    @GameTest(template = EMPTY, timeoutTicks = 60)
    public static void bellsAndExplosionsMakeNoise(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<NoiseBus.Noise> heard = new ArrayList<>();
        NoiseBus.Overhearer overhearer = heard::add;
        NoiseBus.overhear(overhearer);
        BlockPos bell = new BlockPos(1, 2, 1);
        helper.setBlock(bell, Blocks.BELL);
        Vec3 bellAt = Vec3.atCenterOf(helper.absolutePos(bell));
        Vec3 boomAt = helper.absoluteVec(new Vec3(4.5, 2, 4.5));
        helper.runAfterDelay(2, () -> {
            try {
                ((BellBlock) Blocks.BELL).attemptToRing(level, helper.absolutePos(bell), Direction.NORTH);
                level.explode(null, boomAt.x, boomAt.y, boomAt.z, 2, Level.ExplosionInteraction.NONE);
                NoiseBus.Noise rung = heard.stream().filter(n -> n.kind() == NoiseBus.Kind.BELL).findFirst().orElse(null);
                NoiseBus.Noise boom = heard.stream().filter(n -> n.kind() == NoiseBus.Kind.EXPLOSION).findFirst().orElse(null);
                helper.assertTrue(rung != null && rung.at().distanceTo(bellAt) < 1.5, "No bell noise at the bell: " + heard);
                helper.assertTrue(boom != null && boom.at().distanceTo(boomAt) < 1.5 && boom.radius() >= 48, "No explosion noise: " + heard);
                Skyseam.LOGGER.info("Skyseam noise: a bell carries {} blocks, an explosion of power 2 carries {}", String.format("%.0f", rung.radius()),
                        String.format("%.0f", boom.radius()));
            } finally {
                NoiseBus.stopOverhearing(overhearer);
            }
            helper.succeed();
        });
    }
}
