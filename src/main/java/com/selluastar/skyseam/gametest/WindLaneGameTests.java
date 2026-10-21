package com.selluastar.skyseam.gametest;

import java.util.ArrayList;
import java.util.List;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.config.SkyseamConfig;
import com.selluastar.skyseam.external.SableBridge;
import com.selluastar.skyseam.external.Ship;
import com.selluastar.skyseam.halcyon.LanternSun;
import com.selluastar.skyseam.halcyon.wind.WindLane;
import com.selluastar.skyseam.halcyon.wind.WindLaneNetwork;
import com.selluastar.skyseam.halcyon.wind.WindLanes;
import com.selluastar.skyseam.world.halcyon.plan.HalcyonPlan;
import com.selluastar.skyseam.world.halcyon.plan.Landmarks;
import com.selluastar.skyseam.world.halcyon.shape.SolidFeature;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The wind lanes (docs/DECISIONS.md K109): where they run, which way they blow, and what they carry. */
@GameTestHolder(Skyseam.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WindLaneGameTests {
    private static final String EMPTY = "gametest/empty";

    private WindLaneGameTests() {}

    /** How far out from the middle the lanes are looked at, each way, in cells of the default spacing. */
    private static final int CELLS = 6;

    /**
     * The lanes of a seed's world lie all over it, not round one place: most cells of a square 8,640 blocks across have
     * one, out to its far corners in every direction; every one at least ten blocks clear of the ground all along, never
     * through a floating island, rock or the Obelisk, never steep, and curving smoothly; the same lanes every time.
     */
    @GameTest(template = EMPTY, timeoutTicks = 400)
    public static void windLanesCoverTheHalcyon(GameTestHelper helper) {
        HalcyonPlan plan = HalcyonPlan.fresh(12345L);
        List<WindLane> lanes = field(plan);
        int cells = (2 * CELLS) * (2 * CELLS);
        helper.assertTrue(lanes.size() >= cells / 2, "Only " + lanes.size() + " wind lanes in " + cells + " cells");
        // Far out, every way round.
        for (int quarter = 0; quarter < 4; quarter++) {
            int q = quarter;
            helper.assertTrue(lanes.stream().anyMatch(lane -> {
                Vec3 middle = lane.sample(lane.size() / 2);
                return Math.hypot(middle.x, middle.z) > 2500 && (middle.x > 0) == (q % 2 == 0) && (middle.z > 0) == (q < 2);
            }), "No lane far out in quarter " + q);
        }
        BlockPos gate = plan.landmarks.gate;
        double total = 0;
        double lowest = Double.MAX_VALUE;
        double highest = 0;
        String highestAt = "";
        double steepest = 0;
        String steepestAt = "";
        for (WindLane lane : lanes) {
            helper.assertTrue(lane.length() > 500 && lane.length() < 2000, lane.name() + " is " + lane.length() + " blocks long");
            total += lane.length();
            for (int k = 0; k < lane.size(); k++) {
                Vec3 at = lane.sample(k);
                if (at.y > highest) {
                    highest = at.y;
                    highestAt = lane.name() + " at " + BlockPos.containing(at).toShortString();
                }
                if (Math.abs(lane.tangent(k).y) > steepest) {
                    steepest = Math.abs(lane.tangent(k).y);
                    steepestAt = lane.name() + " at " + BlockPos.containing(at).toShortString();
                }
                double clear = at.y - Math.max(plan.landHeight((int) Math.floor(at.x), (int) Math.floor(at.z)), 64);
                lowest = Math.min(lowest, clear);
                helper.assertTrue(clear >= WindLane.RADIUS, lane.name() + " runs " + String.format("%.1f", clear) + " blocks over the ground at "
                        + BlockPos.containing(at).toShortString());
                helper.assertFalse(insideSolid(plan, at), lane.name() + " runs through rock at " + BlockPos.containing(at).toShortString());
                helper.assertFalse(Math.hypot(at.x - gate.getX() - 0.5, at.z - gate.getZ() - 0.5) < Landmarks.OBELISK_REACH + WindLane.RADIUS
                        && at.y < gate.getY() + Landmarks.TOP_ABOVE_TERRACE + WindLane.RADIUS, lane.name() + " runs through the Obelisk at "
                        + BlockPos.containing(at).toShortString());
            }
        }
        double degrees = Math.toDegrees(Math.asin(Math.min(1, steepest)));
        helper.assertTrue(degrees < 35, "A lane climbs or drops at " + String.format("%.0f", degrees) + " degrees: " + steepestAt);
        // Smooth all along, in either seed's world: never turning more than 9 degrees in 4 blocks (no tighter than about 25
        // blocks round), so their ribbons don't fold over or knot.
        for (long seed : new long[] {12345L, 20261008L}) {
            double sharpest = 0;
            String where = "";
            long started = System.nanoTime();
            List<WindLane> lanesHere = seed == 12345L ? lanes : field(HalcyonPlan.fresh(seed));
            if (seed != 12345L) {
                Skyseam.LOGGER.info("Skyseam wind lanes, seed {}: {} cells worked out in {} ms", seed, 4 * CELLS * CELLS,
                        (System.nanoTime() - started) / 1_000_000);
            }
            for (WindLane lane : lanesHere) {
                for (int k = 1; k < lane.size(); k++) {
                    double turn = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, lane.tangent(k).dot(lane.tangent(k - 1))))));
                    if (turn > sharpest) {
                        sharpest = turn;
                        where = lane.name() + " at " + BlockPos.containing(lane.sample(k)).toShortString();
                    }
                }
            }
            helper.assertTrue(sharpest < 9, "Seed " + seed + ": " + where + " turns " + String.format("%.1f", sharpest) + " degrees in 4 blocks");
            Skyseam.LOGGER.info("Skyseam wind lanes, seed {}: at their sharpest they turn {} degrees in 4 blocks, {}", seed, String.format("%.1f", sharpest),
                    where);
        }
        List<WindLane> again = field(HalcyonPlan.fresh(12345L));
        helper.assertTrue(again.size() == lanes.size() && again.get(0).points().equals(lanes.get(0).points()), "The same seed made different lanes");
        Skyseam.LOGGER.info("Skyseam wind lanes: {} lanes in {} cells, {} blocks in all, their middles at least {} blocks over the ground and at "
                + "most {} high ({}), at most {} degrees steep", lanes.size(), cells, Math.round(total), String.format("%.0f", lowest),
                String.format("%.0f", highest), highestAt, String.format("%.0f", degrees));
        helper.succeed();
    }

    /** The lanes of the cells round the middle, at the default spacing and chance. */
    private static List<WindLane> field(HalcyonPlan plan) {
        List<WindLane> lanes = new ArrayList<>();
        int spacing = SkyseamConfig.WIND_LANES_SPACING.getDefault();
        double chance = SkyseamConfig.WIND_LANES_CHANCE.getDefault();
        for (int cx = -CELLS; cx < CELLS; cx++) {
            for (int cz = -CELLS; cz < CELLS; cz++) {
                lanes.addAll(WindLaneNetwork.cell(plan, cx, cz, spacing, chance));
            }
        }
        return lanes;
    }

    private static boolean insideSolid(HalcyonPlan plan, Vec3 at) {
        int x = (int) Math.floor(at.x);
        int z = (int) Math.floor(at.z);
        int y = (int) Math.floor(at.y);
        for (SolidFeature solid : plan.solidsNear(x, z, x, z)) {
            boolean[] inside = {false};
            solid.spans(x, z, (from, to) -> inside[0] |= y >= from - 1 && y <= to + 1);
            if (inside[0]) {
                return true;
            }
        }
        return false;
    }

    /** The wind blows its usual way through the day and back through the Hush, turning smoothly round each change. */
    @GameTest(template = EMPTY)
    public static void windTurnsRoundInTheHush(GameTestHelper helper) {
        int phase = LanternSun.PHASE_TICKS;
        helper.assertTrue(WindLanes.flow(phase / 2.0) == 1, "Dawn's wind is " + WindLanes.flow(phase / 2.0));
        helper.assertTrue(WindLanes.flow(2.5 * phase) == 1, "Dusk's wind is " + WindLanes.flow(2.5 * phase));
        helper.assertTrue(WindLanes.flow(3.5 * phase) == -1, "The Hush's wind is " + WindLanes.flow(3.5 * phase));
        helper.assertTrue(Math.abs(WindLanes.flow(3.0 * phase)) < 0.01, "The wind isn't half turned as the Hush begins: " + WindLanes.flow(3.0 * phase));
        helper.assertTrue(Math.abs(WindLanes.flow(0)) < 0.01, "The wind isn't half turned as the day begins: " + WindLanes.flow(0));
        double previous = WindLanes.flow(0);
        for (int clock = 20; clock <= LanternSun.CYCLE_TICKS; clock += 20) {
            double now = WindLanes.flow(clock);
            helper.assertTrue(Math.abs(now - previous) < 0.12, "The wind jumps from " + previous + " to " + now + " at " + clock);
            previous = now;
        }
        helper.succeed();
    }

    /**
     * Near the End's origin (Sable's physics rounds positions at GameTest coordinates): two rafts hovering on levitite,
     * one in a lane and one thirty blocks beside it. The one in the lane is carried along it the way the wind blows;
     * the other stays put. A pig dropped through the lane is blown along it as it falls.
     */
    @GameTest(template = EMPTY, batch = "m4_wind_lane", timeoutTicks = 400)
    public static void windLaneCarriesShipsAndFallers(GameTestHelper helper) {
        ServerLevel level = TestShips.require(helper.getLevel().getServer().getLevel(Level.END), "The End is not loaded");
        Vec3 start = new Vec3(-2800.5, 230.5, -2800.5);
        WindLane lane = WindLanes.summon(level, List.of(start, start.add(100, 0, 0), start.add(200, 0, 0), start.add(300, 0, 0)));
        Vec3 inAt = start.add(60, 0, 0);
        Vec3 besideAt = start.add(60, 0, 30);
        Vec3 dropAt = start.add(120, 9, 0);
        for (Vec3 at : List.of(inAt, besideAt, dropAt, start.add(100, 0, 0), start.add(200, 0, 0))) {
            TestShips.forceLoad(level, at, true);
        }
        List<BlockPos> inBlocks = hoveringRaft(level, BlockPos.containing(inAt));
        List<BlockPos> besideBlocks = hoveringRaft(level, BlockPos.containing(besideAt));
        Ship[] ships = new Ship[2];
        Pig[] pig = new Pig[1];
        double[] flow = new double[1];
        helper.startSequence()
                .thenExecuteAfter(10, () -> {
                    ships[0] = TestShips.require(SableBridge.assemble(level, BlockPos.containing(inAt), inBlocks), "Could not assemble the raft in the lane");
                    ships[1] = TestShips.require(SableBridge.assemble(level, BlockPos.containing(besideAt), besideBlocks), "Could not assemble the raft beside it");
                    pig[0] = TestShips.require(EntityType.PIG.create(level), "No pig");
                    pig[0].moveTo(dropAt.x, dropAt.y, dropAt.z);
                    level.addFreshEntity(pig[0]);
                    flow[0] = WindLanes.flow(LanternSun.clock(level.getGameTime(), LanternSun.offset(level.getServer())));
                })
                .thenExecuteAfter(16, () -> {
                    double drift = pig[0].getDeltaMovement().x * 20;
                    Skyseam.LOGGER.info("Skyseam wind lane: a pig falling through it is blown along at {} blocks/s (the wind's way {})",
                            String.format("%.1f", drift), String.format("%.2f", flow[0]));
                    if (Math.abs(flow[0]) > 0.5) {
                        helper.assertTrue(drift * Math.signum(flow[0]) > 1.5, "The falling pig is blown along at only " + drift + " blocks/s");
                    }
                })
                .thenExecuteAfter(50, () -> {
                    double along = SableBridge.linearVelocity(ships[0]).x;
                    double beside = SableBridge.linearVelocity(ships[1]).length();
                    Skyseam.LOGGER.info("Skyseam wind lane: after 3 s the raft in it goes {} blocks/s along it, the one beside it {} (the wind {} blocks/s)",
                            String.format("%.1f", along), String.format("%.1f", beside), String.format("%.1f", WindLanes.windSpeed(lane,
                                    LanternSun.clock(level.getGameTime(), LanternSun.offset(level.getServer())))));
                    if (Math.abs(flow[0]) > 0.5) {
                        helper.assertTrue(along * Math.signum(flow[0]) > 4, "The raft in the lane goes only " + along + " blocks/s along it");
                    }
                    helper.assertTrue(beside < 1, "The raft beside the lane moves at " + beside + " blocks/s");
                    WindLanes.clearSummoned(level);
                    for (Ship ship : ships) {
                        SableBridge.remove(ship);
                    }
                    pig[0].discard();
                    for (Vec3 at : List.of(inAt, besideAt, dropAt, start.add(100, 0, 0), start.add(200, 0, 0))) {
                        TestShips.forceLoad(level, at, false);
                    }
                })
                .thenSucceed();
    }

    /** A 3 by 3 raft of planks and levitite, enough levitite that it hovers where it is assembled. */
    private static List<BlockPos> hoveringRaft(ServerLevel level, BlockPos centre) {
        Block levitite = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("aeronautics", "levitite"));
        List<BlockPos> blocks = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-1, 0, -1), centre.offset(1, 0, 1))) {
            boolean lift = (pos.getX() + pos.getZ()) % 2 == 0;
            level.setBlockAndUpdate(pos, lift ? levitite.defaultBlockState() : Blocks.OAK_PLANKS.defaultBlockState());
            blocks.add(pos.immutable());
        }
        return blocks;
    }
}
