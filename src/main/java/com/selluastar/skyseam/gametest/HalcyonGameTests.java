package com.selluastar.skyseam.gametest;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.halcyon.LanternSun;
import com.selluastar.skyseam.halcyon.Rebound;
import com.selluastar.skyseam.halcyon.Veil;
import com.selluastar.skyseam.world.HalcyonLayout;
import com.selluastar.skyseam.world.halcyon.HalcyonChunkGenerator;
import com.selluastar.skyseam.world.halcyon.HalcyonLandmarks;
import com.selluastar.skyseam.world.halcyon.HalcyonRegion;
import com.selluastar.skyseam.world.halcyon.plan.Flora;
import com.selluastar.skyseam.world.halcyon.plan.HalcyonPlan;
import com.selluastar.skyseam.world.halcyon.plan.Landmarks;
import com.selluastar.skyseam.world.halcyon.shape.CaveFeature;
import com.selluastar.skyseam.world.halcyon.shape.FloatingIsland;
import com.selluastar.skyseam.world.halcyon.shape.LandFeature;
import com.selluastar.skyseam.world.halcyon.shape.SolidFeature;
import com.selluastar.skyseam.world.structure.StructureMarkers;

import io.netty.channel.embedded.EmbeddedChannel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * M3, the Halcyon (spec sections 7, 8 and 14, rebuilt from the author's images). The GameTest server loads no
 * data-pack dimensions (DEVIATIONS D20), so the world's rules are tested on its plan directly: the seeded layout, the
 * biome regions, the Veil, Rebound and the Lantern-Sun, plus every structure file. The server boot check generates the
 * real Halcyon (DevBootCheck).
 */
@GameTestHolder(Skyseam.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HalcyonGameTests {
    private static final String EMPTY = "gametest/empty";
    /** The markers each structure file must have (docs/STRUCTURE-MARKERS.md). */
    private static final Map<String, List<String>> MARKERS = Map.of(
            "sundered_obelisk/slice_0", List.of("motto", "mooring_post", "wrightling_stall", "wrightling_stall", "almanac_pages"));
    /** The structure palette (spec section 14): pale stone, warm brick, moss, teal and gold, violet crystal, soft light. */
    private static final Set<String> PALETTE = Set.of("skyseam:stillstone", "skyseam:stillstone_bricks", "skyseam:mossy_stillstone_bricks",
            "skyseam:chiseled_stillstone_bricks", "skyseam:stillstone_pillar", "skyseam:stillstone_brick_slab", "skyseam:stillstone_brick_stairs",
            "skyseam:stillstone_brick_wall", "skyseam:stillstone_glass", "skyseam:petal_carpet", "skyseam:glow_moss", "skyseam:lantern_vine",
            "skyseam:small_prismite_bud", "skyseam:large_prismite_bud", "skyseam:prismite_cluster", "skyseam:cloud", "skyseam:rose_cloud",
            "skyseam:dusk_cloud", "skyseam:cloudmoss", "minecraft:moss_block", "minecraft:moss_carpet", "minecraft:vine", "minecraft:mud_bricks",
            "minecraft:amethyst_block", "minecraft:pearlescent_froglight", "minecraft:waxed_oxidized_cut_copper", "minecraft:gold_block",
            "minecraft:calcite", "minecraft:grass_block", "minecraft:dirt", "minecraft:structure_block", "minecraft:air",
            "skyseam:duskstone_bricks", "skyseam:polished_duskstone", "skyseam:prismite_lamp", "minecraft:purple_stained_glass",
            "skyseam:cracked_duskstone_bricks", "skyseam:mossy_duskstone_bricks", "skyseam:duskstone_tiles", "skyseam:chiseled_duskstone",
            "skyseam:runed_duskstone", "skyseam:duskstone_pillar", "skyseam:cobbled_duskstone", "skyseam:hanging_ivy", "minecraft:chain");

    private HalcyonGameTests() {}

    /**
     * The world plan (docs/DECISIONS.md K75 to K85): the same seed always makes the same world and another seed a
     * different one; the air round the Arrival Lane and the view from it to the Obelisk stay clear; the Obelisk floats
     * over open sea; the archipelago is roughly 45 % land and the outer lands are denser and taller but still cut by
     * sea; no land climbs past Y 400 and nothing past the build limit; floating islands keep clear of the land below;
     * every kit and variant turns up
     * somewhere; and the plan works far out, at 5,000 and 20,000 blocks, as the world has no edge.
     */
    @GameTest(template = EMPTY, batch = "m3_plan", timeoutTicks = 1200)
    public static void planFollowsTheRules(GameTestHelper helper) {
        HalcyonPlan plan = HalcyonPlan.fresh(12345L);
        HalcyonPlan again = HalcyonPlan.fresh(12345L);
        HalcyonPlan other = HalcyonPlan.fresh(-777L);
        int same = 0;
        int differ = 0;
        for (int k = 0; k < 200; k++) {
            int r = k % 2 == 0 ? 1300 : 3200;
            int x = (int) (Math.sin(k * 1.7) * r);
            int z = (int) (Math.cos(k * 2.3) * r);
            same += plan.landHeight(x, z) == again.landHeight(x, z) ? 1 : 0;
            differ += Math.abs(plan.landHeight(x, z) - other.landHeight(x, z)) > 2 ? 1 : 0;
        }
        helper.assertTrue(same == 200, "The same seed made a different world at " + (200 - same) + " of 200 points");
        helper.assertTrue(differ > 60, "Two seeds made nearly the same world: they differ at only " + differ + " of 200 points");
        helper.assertFalse(plan.landmarks.arrival.equals(other.landmarks.arrival), "Two seeds put the Arrival Lane in the same place");

        Landmarks marks = plan.landmarks;
        // The air round the lane: no land and no floating island or rock.
        int blocked = 0;
        for (int x = (int) marks.arrival.x - 48; x <= marks.arrival.x + 48; x += 6) {
            for (int z = (int) marks.arrival.z - 48; z <= marks.arrival.z + 48; z += 6) {
                if (plan.landHeight(x, z) >= Landmarks.LANE_Y - 50) {
                    blocked++;
                }
                for (SolidFeature solid : plan.solidsNear(x, z, x, z)) {
                    int[] hit = {0};
                    solid.spans(x, z, (from, to) -> hit[0] += to >= Landmarks.LANE_Y - 50 && from <= Landmarks.LANE_Y + 50 ? 1 : 0);
                    blocked += hit[0];
                }
            }
        }
        helper.assertTrue(blocked == 0, blocked + " columns stand in the air round the Arrival Lane");
        // The view: from the lane to the Obelisk's orb, nothing in the way.
        Vec3 eye = marks.arrival;
        Vec3 target = marks.lookAt();
        for (double t = 0.05; t < 0.85; t += 0.01) {
            Vec3 at = eye.lerp(target, t);
            int x = (int) Math.floor(at.x);
            int z = (int) Math.floor(at.z);
            helper.assertTrue(plan.landHeight(x, z) < at.y, "Land at " + x + ", " + z + " blocks the view to the Obelisk");
            for (SolidFeature solid : plan.solidsNear(x, z, x, z)) {
                solid.spans(x, z, (from, to) -> helper.assertFalse(from <= at.y && to >= at.y,
                        solid.describe() + " blocks the view to the Obelisk"));
            }
        }
        // The Obelisk floats over open sea: nothing rises within 40 blocks of its axis, from the sea to its top.
        BlockPos base = marks.obeliskBase();
        for (int dx = -40; dx <= 40; dx += 8) {
            for (int dz = -40; dz <= 40; dz += 8) {
                int x = base.getX() + dx;
                int z = base.getZ() + dz;
                helper.assertTrue(plan.landHeight(x, z) < HalcyonLayout.SEA_LEVEL, "Land at " + dx + ", " + dz + " from the Obelisk's axis");
                for (SolidFeature solid : plan.solidsNear(x, z, x, z)) {
                    solid.spans(x, z, (from, to) -> helper.assertFalse(to > HalcyonLayout.SEA_LEVEL - 2,
                            solid.describe() + " stands in the Obelisk's clear space"));
                }
            }
        }

        // Land, height, clearance and kits, a 64-block tile at a time: the archipelago (inside 1,250 blocks) and a
        // stretch of the Highlands (2,700 to 3,700 blocks out along one bearing).
        int[] land = new int[2];
        int[] samples = new int[2];
        double[] heights = new double[2];
        Set<String> kinds = new TreeSet<>();
        LandFeature.LandSample sample = new LandFeature.LandSample();
        List<int[]> tiles = new ArrayList<>();
        for (int tx = -1280; tx < 1280; tx += 64) {
            for (int tz = -1280; tz < 1280; tz += 64) {
                tiles.add(new int[] {tx, tz, 0});
            }
        }
        for (int tx = 2700; tx < 3700; tx += 64) {
            for (int tz = -640; tz < 640; tz += 64) {
                tiles.add(new int[] {tx, tz, 1});
            }
        }
        for (int[] tile : tiles) {
            int tx = tile[0];
            int tz = tile[1];
            int zone = tile[2];
            List<LandFeature> lands = plan.landsNear(tx, tz, tx + 63, tz + 63);
            for (int x = tx; x < tx + 64; x += 16) {
                for (int z = tz; z < tz + 64; z += 16) {
                    if (zone == 1 || Math.hypot(x, z) < 1250) {
                        plan.land(x, z, lands, sample);
                        samples[zone]++;
                        if (sample.height >= HalcyonLayout.SEA_LEVEL) {
                            land[zone]++;
                            heights[zone] += sample.height;
                        }
                    }
                }
            }
            for (LandFeature feature : lands) {
                kinds.add(kind(feature.describe()));
                helper.assertTrue(feature.peak() < 400, feature.describe() + " climbs to " + feature.peak());
            }
            for (SolidFeature solid : plan.solidsNear(tx, tz, tx + 63, tz + 63)) {
                kinds.add(kind(solid.describe()));
                helper.assertTrue(solid.top() < 500, solid.describe() + " reaches " + solid.top());
                if (solid instanceof FloatingIsland island) {
                    double ground = plan.landHeight((int) island.cx, (int) island.cz);
                    helper.assertTrue(island.bottom() >= ground + HalcyonPlan.FLOAT_CLEARANCE - 8,
                            island.describe() + " hangs " + (island.bottom() - ground) + " blocks over the land");
                }
            }
        }
        double inner = land[0] / (double) samples[0];
        double outer = land[1] / (double) samples[1];
        double innerHeight = heights[0] / Math.max(1, land[0]);
        double outerHeight = heights[1] / Math.max(1, land[1]);
        Skyseam.LOGGER.info("Skyseam Halcyon plan (seed 12345): archipelago {} % land, mean height {}; Highlands {} % land, mean height {}; kits {}",
                Math.round(inner * 100), Math.round(innerHeight), Math.round(outer * 100), Math.round(outerHeight), kinds);
        helper.assertTrue(inner > 0.35 && inner < 0.55, "The archipelago is " + Math.round(inner * 100) + " % land, not about 45 %");
        helper.assertTrue(outer > 0.6 && outer < 0.93, "The Highlands are " + Math.round(outer * 100) + " % land, not dense land cut by sea");
        helper.assertTrue(outerHeight > innerHeight + 20, "The Highlands' land (" + Math.round(outerHeight) + ") is not much taller than the "
                + "archipelago's (" + Math.round(innerHeight) + ")");
        for (String wanted : List.of("main island terraced", "main island spire_massif", "main island caldera", "main island twin_ridge",
                "main island cove", "main island highland", "main island spindle", "isle mound", "isle knoll", "isle sandbar", "spire cluster",
                "sea rock stack", "sea rock slab", "sea rock boulders", "sea rock arch", "floating island tuft", "floating island teardrop",
                "floating island shelf", "floating island shard", "floating island cluster", "floating island thorn")) {
            helper.assertTrue(kinds.contains(wanted), "No " + wanted + " in the sampled Halcyon; found " + kinds);
        }
        // The plan works anywhere: far out, the land and sea are still sensible.
        for (int far : new int[] {5000, 20000, -20000}) {
            double h = plan.landHeight(far, far / 3);
            helper.assertTrue(h > 10 && h < 400, "At " + far + " blocks the land is at Y " + h);
        }
        helper.succeed();
    }

    /** "main island cove r130 at 10, 20" becomes "main island cove". */
    private static String kind(String described) {
        String head = described.split(" r[0-9]| at | of ")[0];
        return head.trim();
    }

    /**
     * The outer lands' pieces (docs/DECISIONS.md K81 to K85): colossal Horncrown isles, Elderwood stone blades, all four
     * kinds of cave carved inside land, and the giant trees and mushrooms of each biome, each on its own ground.
     */
    @GameTest(template = EMPTY, batch = "m3_plan", timeoutTicks = 1200)
    public static void outerLandsHaveTheirPieces(GameTestHelper helper) {
        HalcyonPlan plan = HalcyonPlan.fresh(12345L);
        Set<String> kinds = new TreeSet<>();
        Set<CaveFeature.Kind> caves = new java.util.HashSet<>();
        for (int tx = -4000; tx < 4000; tx += 512) {
            for (int tz = -4000; tz < 4000; tz += 512) {
                for (SolidFeature solid : plan.solidsNear(tx, tz, tx + 511, tz + 511)) {
                    kinds.add(kind(solid.describe()));
                }
                for (CaveFeature cave : plan.cavesNear(tx, tz, tx + 511, tz + 511)) {
                    caves.add(cave.kind);
                    // Every cave lies inside land: the ground over its middle stands above its roof (or its shaft).
                    double over = plan.landHeight((int) cave.cx, (int) cave.cz);
                    helper.assertTrue(over > cave.bottom() + 6, cave.describe() + " is not inside land (the ground is at " + over + ")");
                }
            }
        }
        helper.assertTrue(kinds.contains("floating island horncrown"), "No Horncrown isle; found " + kinds);
        helper.assertTrue(kinds.contains("sea rock blade"), "No Elderwood stone blade; found " + kinds);
        helper.assertTrue(caves.containsAll(List.of(CaveFeature.Kind.values())), "Only these kinds of cave: " + caves);
        // Giants: in each biome that grows them, find a stretch of it and the giants there.
        Map<HalcyonRegion, Flora.Kind> wanted = Map.of(HalcyonRegion.ELDERWOOD, Flora.Kind.ELDER_LUMEN, HalcyonRegion.SUNREACH_CLIFFS,
                Flora.Kind.SPIREPINE, HalcyonRegion.PETALWASH, Flora.Kind.GREAT_VIOLET);
        Set<Flora.Kind> grown = new java.util.HashSet<>();
        for (Map.Entry<HalcyonRegion, Flora.Kind> entry : wanted.entrySet()) {
            int[] spot = TestShips.require(spotIn(plan, entry.getKey()), "No stretch of " + entry.getKey());
            for (Flora.Big big : plan.floraNear(spot[0] - 400, spot[1] - 400, spot[0] + 400, spot[1] + 400)) {
                grown.add(big.kind());
                double ground = plan.landHeight(big.x(), big.z());
                helper.assertTrue(Math.abs(ground - big.groundY()) <= 1, big.kind() + " at " + big.x() + ", " + big.z() + " floats or is buried");
            }
        }
        int[] capwood = TestShips.require(spotIn(plan, HalcyonRegion.CAPWOOD), "No stretch of the Capwood");
        long mushrooms = plan.floraNear(capwood[0] - 300, capwood[1] - 300, capwood[0] + 300, capwood[1] + 300).stream()
                .filter(big -> big.kind().isMushroom()).count();
        helper.assertTrue(grown.containsAll(wanted.values()), "Giants grown: " + grown);
        helper.assertTrue(mushrooms >= 5, "Only " + mushrooms + " giant mushrooms in 600 blocks of the Capwood");
        helper.succeed();
    }

    /** A column well inside a biome region, searched in rings out from the middle, or null. */
    private static int[] spotIn(HalcyonPlan plan, HalcyonRegion region) {
        for (int r = 300; r < 6000; r += 150) {
            for (int k = 0; k < 48; k++) {
                double a = k * Math.PI / 24;
                int x = (int) (Math.cos(a) * r);
                int z = (int) (Math.sin(a) * r);
                if (plan.region(x, z) == region && plan.region(x + 80, z) == region && plan.region(x - 80, z) == region
                        && plan.region(x, z + 80) == region && plan.region(x, z - 80) == region) {
                    return new int[] {x, z};
                }
            }
        }
        return null;
    }

    /**
     * The biome regions (docs/DECISIONS.md K77, K81): the Spindle in the middle, at least two regions of each inner
     * biome in the archipelago and of each outer biome in the first ring of the outer lands, the Obelisk in Petalwash,
     * a different layout for another seed, the Underbloom under a floating island and inside a cave.
     */
    @GameTest(template = EMPTY, batch = "m3_plan", timeoutTicks = 400)
    public static void regionsCoverTheWorld(GameTestHelper helper) {
        HalcyonPlan plan = HalcyonPlan.fresh(12345L);
        HalcyonPlan other = HalcyonPlan.fresh(-777L);
        helper.assertTrue(plan.region(0, 0) == HalcyonRegion.SPINDLE, "The centre is not the Spindle");
        for (HalcyonRegion inner : List.of(HalcyonRegion.PETALWASH, HalcyonRegion.CIRRUS_REEFS, HalcyonRegion.HUSH, HalcyonRegion.WRECKFIELDS,
                HalcyonRegion.MIRROR_SHOALS)) {
            helper.assertTrue(plan.regions.counts().getOrDefault(inner, 0) >= 2, inner + " has fewer than two regions: " + plan.regions.counts());
        }
        for (HalcyonRegion outer : List.of(HalcyonRegion.SUNREACH_CLIFFS, HalcyonRegion.CAPWOOD, HalcyonRegion.ELDERWOOD)) {
            helper.assertTrue(plan.regions.outerCounts().getOrDefault(outer, 0) >= 2, outer + " has fewer than two regions: "
                    + plan.regions.outerCounts());
        }
        BlockPos gate = plan.landmarks.gate;
        helper.assertTrue(plan.region(gate.getX(), gate.getZ()) == HalcyonRegion.PETALWASH, "The Obelisk is in "
                + plan.region(gate.getX(), gate.getZ()));
        int differ = 0;
        for (int k = 0; k < 100; k++) {
            int x = (int) (Math.sin(k * 1.3) * 900);
            int z = (int) (Math.cos(k * 0.7) * 900);
            differ += plan.region(x, z) != other.region(x, z) ? 1 : 0;
        }
        helper.assertTrue(differ > 25, "Two seeds put the biomes in nearly the same places (" + differ + " of 100 differ)");
        // The Underbloom: under a floating island, above the land.
        FloatingIsland island = null;
        for (SolidFeature solid : plan.solidsNear(-1200, -1200, 1200, 1200)) {
            if (solid instanceof FloatingIsland floating && floating.radius > 12) {
                island = floating;
                break;
            }
        }
        FloatingIsland found = TestShips.require(island, "No floating island to stand under");
        int x = (int) found.cx;
        int z = (int) found.cz;
        HalcyonRegion under = plan.biomeAt(x, found.bottom() - 10, z, plan.landHeight(x, z), plan.solidsNear(x, z, x, z), List.of(), 0);
        helper.assertTrue(under == HalcyonRegion.UNDERBLOOM, "Under a floating island is " + under + ", not the Underbloom");
        // And inside a cave (a sinkhole: its middle, a third of the way up).
        CaveFeature sinkhole = null;
        for (CaveFeature cave : plan.cavesNear(-3000, -3000, 3000, 3000)) {
            if (cave.kind == CaveFeature.Kind.SINKHOLE) {
                sinkhole = cave;
                break;
            }
        }
        CaveFeature cave = TestShips.require(sinkhole, "No sinkhole");
        int cx = (int) Math.floor(cave.cx);
        int cz = (int) Math.floor(cave.cz);
        int cy = cave.floorY + (int) (cave.height / 3);
        HalcyonRegion inside = plan.biomeAt(cx, cy, cz, plan.landHeight(cx, cz), List.of(), List.of(cave), 0);
        helper.assertTrue(inside == HalcyonRegion.UNDERBLOOM, "Inside a sinkhole is " + inside + ", not the Underbloom");
        // The dimension file parses with the Halcyon generator, if the server loaded it.
        LevelStem stem = helper.getLevel().registryAccess().registryOrThrow(Registries.LEVEL_STEM).get(HalcyonLayout.LEVEL.location());
        if (stem != null) {
            helper.assertTrue(stem.generator() instanceof HalcyonChunkGenerator, "The Halcyon's generator is " + stem.generator());
        }
        helper.succeed();
    }

    /**
     * The Veil is off for now (docs/DECISIONS.md K81): nothing is pushed and no fog thickens. Its maths still hold for
     * when it returns: a push that grows towards the rim, a carry back past it, and ships deflected like light off glass.
     */
    @GameTest(template = EMPTY)
    public static void veilPushesBack(GameTestHelper helper) {
        helper.assertFalse(Veil.enabled(), "The Veil is on; the author asked for no edge for now");
        helper.assertTrue(Veil.thickness(1600, 200, 0) == 0 && !Veil.isIn(1600, 200, 0), "The Veil thickens though it is off");
        helper.assertTrue(Veil.depth(1000, 200, 0) == 0, "The Veil reaches 1,000 blocks out");
        helper.assertTrue(Math.abs(Veil.depth(1400, 200, 0) - 0.5) < 1.0e-6, "Half way through the Veil is not half deep");
        helper.assertTrue(Veil.depth(0, HalcyonLayout.LID, 0) >= 1, "The lid is not part of the Veil");
        Vec3 calm = Veil.pushPlayer(new Vec3(1000, 200, 0), Vec3.ZERO);
        helper.assertTrue(calm.equals(Vec3.ZERO), "A player inside the clear world was pushed");
        Vec3 pushed = Veil.pushPlayer(new Vec3(1450, 200, 0), Vec3.ZERO);
        helper.assertTrue(pushed.x < 0 && Math.abs(pushed.z) < 1.0e-9, "A player in the Veil was pushed " + pushed + ", not in");
        Vec3 carried = Veil.pushPlayer(new Vec3(1600, 200, 0), new Vec3(0.5, 0, 0));
        helper.assertTrue(carried.x <= -0.3 + 1.0e-9, "A player past the rim heading out is not carried back: " + carried);
        Vec3 ship = Veil.deflectShip(new Vec3(1600, 200, 0), new Vec3(10, 0, 3));
        helper.assertTrue(ship.x < -5 && Math.abs(ship.z - 3) < 1.0e-9, "A ship flying out past the rim was not turned back: " + ship);
        Vec3 lid = Veil.deflectShip(new Vec3(0, 440, 0), new Vec3(0, 5, 0));
        helper.assertTrue(lid.y < 0, "A ship climbing past the lid was not turned down: " + lid);
        helper.succeed();
    }

    /**
     * Rebound: thrown 24 blocks up with Slow Falling, and not again within 30 seconds. For a player, the fall is the
     * movement their client reported (the server's velocity for a player goes stale), and they are lifted out of the
     * water before the launch.
     */
    @GameTest(template = EMPTY)
    public static void reboundThrowsBackUp(GameTestHelper helper) {
        double speed = Rebound.launchSpeed(24);
        double y = 0;
        double v = speed;
        while (v > 0) {
            y += v;
            v = (v - 0.08) * 0.98;
        }
        helper.assertTrue(y >= 24 && y < 24.6, "The launch speed " + speed + " rises " + y + " blocks, not 24");
        Pig pig = TestShips.require(EntityType.PIG.create(helper.getLevel()), "No pig");
        pig.moveTo(helper.absoluteVec(new Vec3(3.5, 2, 3.5)));
        helper.getLevel().addFreshEntity(pig);
        long now = helper.getLevel().getGameTime();
        Rebound.forget(pig.getUUID());
        boolean first = Rebound.tryThrow(pig, now);
        boolean slowFalling = pig.hasEffect(MobEffects.SLOW_FALLING);
        double launched = pig.getDeltaMovement().y;
        boolean second = Rebound.tryThrow(pig, now + 20 * 10);
        boolean later = Rebound.tryThrow(pig, now + 20 * 31);
        Rebound.forget(pig.getUUID());
        pig.discard();
        helper.assertTrue(first, "A pig falling into the sea was not thrown");
        helper.assertTrue(slowFalling, "Rebound gave no Slow Falling");
        helper.assertTrue(Math.abs(launched - speed) < 1.0e-9, "The pig was launched at " + launched + ", not " + speed);
        helper.assertFalse(second, "Rebound threw the same pig twice in 10 seconds");
        helper.assertTrue(later, "Rebound refused the pig after 31 seconds");

        ServerPlayer player = connectedPlayer(helper);
        player.setDeltaMovement(Vec3.ZERO);
        player.setKnownMovement(new Vec3(0, -1.2, 0));
        double reported = Rebound.fallingSpeed(player);
        Rebound.forget(player.getUUID());
        boolean thrown = Rebound.tryThrow(player, now);
        double lifted = player.getY();
        double playerLaunch = player.getDeltaMovement().y;
        Rebound.forget(player.getUUID());
        helper.assertTrue(reported < -1, "A player's reported fall reads as " + reported);
        helper.assertTrue(thrown, "A player falling into the sea was not thrown");
        helper.assertTrue(Math.abs(lifted - (HalcyonLayout.SEA_LEVEL + 0.2)) < 1.0e-6, "The player was left at Y " + lifted + ", not lifted out of the water");
        helper.assertTrue(Math.abs(playerLaunch - speed) < 1.0e-9, "The player was launched at " + playerLaunch + ", not " + speed);
        helper.succeed();
    }

    /**
     * A server player with its own connection, which is all Rebound needs. It is not logged in: a full join (the
     * GameTest mock player) also syncs every mod's data, and Simulated's sync sends a payload a fake connection
     * cannot carry.
     */
    private static ServerPlayer connectedPlayer(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "rebound-test");
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(), profile, ClientInformation.createDefault());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        new ServerGamePacketListenerImpl(server, connection, player, CommonListenerCookie.createInitial(profile, false));
        player.moveTo(helper.absoluteVec(new Vec3(5.5, 2, 5.5)));
        return player;
    }

    /**
     * The Lantern-Sun (docs/DECISIONS.md K82): four phases of ten minutes, brightest at noon, darkest in the Hush; the
     * day the game sees in the Halcyon is the Lantern-Sun's, sunrise in Dawn-Glass, noon in Noon-Bloom, sunset in
     * Dusk-Prism, midnight in the Hush, the day count climbing every 40 minutes; and the time commands move it forward.
     */
    @GameTest(template = EMPTY)
    public static void lanternSunKeepsTime(GameTestHelper helper) {
        helper.assertTrue(LanternSun.phase(0) == LanternSun.Phase.DAWN_GLASS && LanternSun.phase(12000) == LanternSun.Phase.NOON_BLOOM
                && LanternSun.phase(24000) == LanternSun.Phase.DUSK_PRISM && LanternSun.phase(36000) == LanternSun.Phase.HUSH
                && LanternSun.phase(48000) == LanternSun.Phase.DAWN_GLASS, "The phases are not ten minutes each");
        helper.assertTrue(LanternSun.brightness(18000) > 0.99f && LanternSun.brightness(42000) < 0.01f, "Noon is not bright or the Hush is not dark");
        helper.assertTrue(Math.floorMod(LanternSun.dayTime(6000, 0), 24000) == 0, "Dawn-Glass's middle is not sunrise");
        helper.assertTrue(Math.floorMod(LanternSun.dayTime(18000, 0), 24000) == 6000, "Noon-Bloom's middle is not noon");
        helper.assertTrue(Math.floorMod(LanternSun.dayTime(30000, 0), 24000) == 12000, "Dusk-Prism's middle is not sunset");
        helper.assertTrue(Math.floorMod(LanternSun.dayTime(42000, 0), 24000) == 18000, "The Hush's middle is not midnight");
        helper.assertTrue(LanternSun.dayTime(48000 * 3 + 18000, 0) / 24000 == LanternSun.dayTime(18000, 0) / 24000 + 3,
                "The day count does not climb once a circuit");
        helper.assertTrue(LanternSun.dayTime(100, 48000) == LanternSun.dayTime(48100, 0), "The offset does not move the day");
        var server = helper.getLevel().getServer();
        LanternSun.setPhase(server, LanternSun.Phase.HUSH);
        LanternSun.Phase hush = LanternSun.phase(server);
        long before = LanternSun.offset(false);
        LanternSun.addTicks(server, 1000);
        long moved = LanternSun.offset(false) - before;
        LanternSun.setPhase(server, LanternSun.Phase.DAWN_GLASS);
        helper.assertTrue(hush == LanternSun.Phase.HUSH, "The phase command set " + hush);
        helper.assertTrue(moved == 1000, "time add 1000 moved the clock " + moved);
        helper.assertTrue(LanternSun.offset(false) >= before, "Setting the phase turned the clock back");
        helper.succeed();
    }

    /**
     * Every structure file (spec section 14, "Check"): it loads, fits a structure block (48 a side), has every marker
     * docs/STRUCTURE-MARKERS.md lists, and uses only palette blocks. The Obelisk is over 200 blocks tall, and placed in
     * the world its motto marker becomes the carved sign.
     */
    @GameTest(template = EMPTY, batch = "m3_structures", timeoutTicks = 200)
    public static void structuresFitAndHaveTheirMarkers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var resources = level.getServer().getResourceManager().listResources("structure", path -> path.getPath().endsWith(".nbt"));
        int checked = 0;
        for (ResourceLocation file : resources.keySet()) {
            if (!file.getNamespace().equals(Skyseam.MOD_ID) || file.getPath().startsWith("structure/gametest/")) {
                continue;
            }
            String name = file.getPath().substring("structure/".length(), file.getPath().length() - ".nbt".length());
            StructureTemplate template = TestShips.require(level.getStructureManager().get(Skyseam.id(name)).orElse(null), "Cannot load " + name);
            helper.assertTrue(template.getSize().getX() <= 48 && template.getSize().getY() <= 48 && template.getSize().getZ() <= 48,
                    name + " is " + template.getSize() + ", more than a structure block can save");
            List<String> found = StructureMarkers.markers(template, BlockPos.ZERO, new StructurePlaceSettings()).stream()
                    .map(StructureMarkers::name).sorted().toList();
            List<String> wanted = MARKERS.getOrDefault(name, List.of()).stream().sorted().toList();
            helper.assertTrue(found.equals(wanted), name + " has markers " + found + ", docs/STRUCTURE-MARKERS.md lists " + wanted);
            for (String block : palette(helper, file)) {
                helper.assertTrue(PALETTE.contains(block), name + " uses " + block + ", which is outside the structure palette");
            }
            checked++;
        }
        helper.assertTrue(checked >= 1, "No structure files were checked");

        // The Obelisk: over 200 blocks tall, in slices of at most 48. Place its first slice well away from the other
        // tests and swap its markers.
        int slices = 0;
        int height = 0;
        while (level.getStructureManager().get(Skyseam.id(HalcyonLandmarks.OBELISK + slices)).isPresent()) {
            StructureTemplate slice = level.getStructureManager().get(Skyseam.id(HalcyonLandmarks.OBELISK + slices)).orElseThrow();
            height = slices * HalcyonLandmarks.SLICE + slice.getSize().getY();
            slices++;
        }
        helper.assertTrue(height >= 200, "The Sundered Obelisk is " + height + " blocks tall, in " + slices + " slices, not over 200");
        HalcyonLandmarks.Piece piece = new HalcyonLandmarks.Piece(Skyseam.id(HalcyonLandmarks.OBELISK + 0), helper.absolutePos(new BlockPos(400, 10, 0)),
                Rotation.NONE);
        StructureTemplate obelisk = level.getStructureManager().get(piece.template()).orElseThrow();
        BlockPos origin = piece.origin(obelisk);
        StructurePlaceSettings settings = piece.settings(obelisk);
        BoundingBox box = obelisk.getBoundingBox(settings, origin);
        obelisk.placeInWorld(level, origin, origin, settings, level.getRandom(), 2);
        StructureMarkers.apply(level, obelisk, origin, settings, box);
        BlockPos motto = StructureMarkers.markers(obelisk, origin, settings).stream().filter(info -> StructureMarkers.name(info).equals("motto"))
                .findFirst().orElseThrow().pos();
        boolean sign = level.getBlockEntity(motto) instanceof SignBlockEntity carved
                && carved.getFrontText().getMessage(0, false).getString().contains("leaves");
        BlockPos.betweenClosedStream(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())
                .forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16));
        helper.assertTrue(sign, "The Obelisk's motto marker did not become the carved sign");
        helper.succeed();
    }

    /** The block names in a structure file's palette, read straight from the file. */
    private static List<String> palette(GameTestHelper helper, ResourceLocation file) {
        try (InputStream in = helper.getLevel().getServer().getResourceManager().open(file)) {
            CompoundTag tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
            ListTag palette = tag.getList("palette", Tag.TAG_COMPOUND);
            List<String> names = new ArrayList<>();
            for (int k = 0; k < palette.size(); k++) {
                names.add(palette.getCompound(k).getString("Name"));
            }
            return names;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read " + file, e);
        }
    }
}
