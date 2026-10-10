package com.selluastar.skyseam.gametest;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import com.selluastar.skyseam.world.halcyon.HalcyonTerrain;
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
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * M3, the Halcyon's shell (spec sections 7, 8 and 14). The GameTest server loads no data-pack dimensions (DEVIATIONS
 * D20), so the world's rules are tested directly: the island model, the biome rings, the Veil, Rebound and the
 * Lantern-Sun, plus every structure file. The server boot check generates the real Halcyon (DevBootCheck).
 */
@GameTestHolder(Skyseam.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HalcyonGameTests {
    private static final String EMPTY = "gametest/empty";
    /** The markers each structure file must have (docs/STRUCTURE-MARKERS.md). */
    private static final Map<String, List<String>> MARKERS = Map.of(
            "sundered_obelisk", List.of("motto", "mooring_post", "wrightling_stall", "wrightling_stall", "almanac_pages"));
    /** The structure palette (spec section 14): pale stone, warm brick, moss, teal and gold, violet crystal, soft light. */
    private static final Set<String> PALETTE = Set.of("skyseam:stillstone", "skyseam:stillstone_bricks", "skyseam:mossy_stillstone_bricks",
            "skyseam:chiseled_stillstone_bricks", "skyseam:stillstone_pillar", "skyseam:stillstone_brick_slab", "skyseam:stillstone_brick_stairs",
            "skyseam:stillstone_brick_wall", "skyseam:stillstone_glass", "skyseam:petal_carpet", "skyseam:glow_moss", "skyseam:lantern_vine",
            "skyseam:small_prismite_bud", "skyseam:large_prismite_bud", "skyseam:prismite_cluster", "skyseam:cloud", "skyseam:rose_cloud",
            "skyseam:dusk_cloud", "skyseam:cloudmoss", "minecraft:moss_block", "minecraft:moss_carpet", "minecraft:vine", "minecraft:mud_bricks",
            "minecraft:amethyst_block", "minecraft:pearlescent_froglight", "minecraft:waxed_oxidized_cut_copper", "minecraft:gold_block",
            "minecraft:calcite", "minecraft:grass_block", "minecraft:dirt", "minecraft:structure_block", "minecraft:air");

    private HalcyonGameTests() {}

    /**
     * The island model: islands in all three size classes and every kind, the same for the same seed; the Arrival
     * Lane's air and the view to the Obelisk kept clear; the Anchorage flat under the whole plaza; nothing above the
     * lid, and floating islands clear of the sea.
     */
    @GameTest(template = EMPTY)
    public static void islandsFollowTheLayout(GameTestHelper helper) {
        HalcyonTerrain terrain = new HalcyonTerrain(12345L, 1, 1, 1);
        List<HalcyonTerrain.Island> all = terrain.islandsNear(-HalcyonLayout.RADIUS, -HalcyonLayout.RADIUS, HalcyonLayout.RADIUS, HalcyonLayout.RADIUS);
        EnumSet<HalcyonTerrain.SizeClass> sizes = EnumSet.noneOf(HalcyonTerrain.SizeClass.class);
        EnumSet<HalcyonTerrain.Kind> kinds = EnumSet.noneOf(HalcyonTerrain.Kind.class);
        for (HalcyonTerrain.Island island : all) {
            sizes.add(island.size());
            kinds.add(island.kind());
            helper.assertTrue(island.highest() < HalcyonLayout.LID, "An island reaches above the lid: " + island);
            if (island.kind() != HalcyonTerrain.Kind.REEF) {
                helper.assertTrue(island.top() - island.depth() > HalcyonLayout.SEA_LEVEL, "A floating island dips into the sea: " + island);
            }
        }
        helper.assertTrue(sizes.size() == 3, "Only these island sizes appear: " + sizes);
        helper.assertTrue(kinds.containsAll(EnumSet.allOf(HalcyonTerrain.Kind.class)), "Only these island kinds appear: " + kinds);
        helper.assertTrue(all.size() > 150, "Only " + all.size() + " islands in the whole Halcyon");
        List<HalcyonTerrain.Island> again = new HalcyonTerrain(12345L, 1, 1, 1).islandsNear(-600, -600, 600, 600);
        helper.assertTrue(again.equals(terrain.islandsNear(-600, -600, 600, 600)), "The same seed made different islands");
        helper.assertFalse(again.equals(new HalcyonTerrain(999L, 1, 1, 1).islandsNear(-600, -600, 600, 600)), "Two seeds made the same islands");

        // The Arrival Lane: no block in its clearance.
        int[] blocked = {0};
        double minY = HalcyonLayout.ARRIVAL_CLEARANCE.minY;
        double maxY = HalcyonLayout.ARRIVAL_CLEARANCE.maxY;
        for (int x = (int) HalcyonLayout.ARRIVAL_CLEARANCE.minX; x <= HalcyonLayout.ARRIVAL_CLEARANCE.maxX; x += 3) {
            for (int z = (int) HalcyonLayout.ARRIVAL_CLEARANCE.minZ; z <= HalcyonLayout.ARRIVAL_CLEARANCE.maxZ; z += 3) {
                terrain.column(x, z, terrain.islandsNear(x, z, x, z), (y, state) -> {
                    if (y >= minY && y <= maxY) {
                        blocked[0]++;
                    }
                });
            }
        }
        helper.assertTrue(blocked[0] == 0, blocked[0] + " blocks stand in the Arrival Lane's clearance");
        // The view to the Obelisk: nothing above the plaza between the lane and the Anchorage's rim.
        Vec3 lane = HalcyonLayout.ARRIVAL_LANE;
        Vec3 obelisk = Vec3.atCenterOf(HalcyonLayout.OBELISK);
        for (double t = 0; t < 0.55; t += 0.02) {
            int x = (int) Math.floor(lane.x + (obelisk.x - lane.x) * t);
            int z = (int) Math.floor(lane.z + (obelisk.z - lane.z) * t);
            int top = terrain.islandTop(x, z, terrain.islandsNear(x, z, x, z));
            helper.assertTrue(top < HalcyonLayout.OBELISK.getY() - 10, "An island at " + x + ", " + z + " (top " + top + ") blocks the view to the Obelisk");
        }
        // The Anchorage: flat at the plaza's level under the whole 31-block plaza.
        for (int dx = -15; dx <= 15; dx += 3) {
            for (int dz = -15; dz <= 15; dz += 3) {
                if (dx * dx + dz * dz > 15 * 15) {
                    continue;
                }
                int x = HalcyonLayout.OBELISK.getX() + dx;
                int z = HalcyonLayout.OBELISK.getZ() + dz;
                int top = terrain.islandTop(x, z, terrain.islandsNear(x, z, x, z));
                helper.assertTrue(top == HalcyonLayout.OBELISK.getY(), "The Anchorage's top at " + x + ", " + z + " is " + top
                        + ", not flat at " + HalcyonLayout.OBELISK.getY());
            }
        }
        // The sea is everywhere inside its radius, and nowhere beyond.
        List<Integer> sea = new ArrayList<>();
        terrain.column(1200, -300, List.of(), (y, state) -> {
            if (state.is(Blocks.WATER)) {
                sea.add(y);
            }
        });
        helper.assertTrue(sea.contains(HalcyonLayout.SEA_LEVEL - 1) && !sea.contains(HalcyonLayout.SEA_LEVEL), "The Mirror Sea's surface is not at Y "
                + HalcyonLayout.SEA_LEVEL);
        boolean[] beyond = {false};
        terrain.column(HalcyonLayout.SEA_RADIUS + 10, 0, List.of(), (y, state) -> beyond[0] = true);
        helper.assertFalse(beyond[0], "The sea goes on past its radius");
        Skyseam.LOGGER.info("Skyseam M3 islands: {} islands, sizes {}, kinds {}", all.size(), sizes, kinds);
        helper.succeed();
    }

    /** The biome rings, from the Spindle out to the Mirror Shoals, the Underbloom below, and the dimension's generator. */
    @GameTest(template = EMPTY)
    public static void biomesFormRings(GameTestHelper helper) {
        helper.assertTrue(HalcyonRegion.at(0, 250, 0) == HalcyonRegion.SPINDLE, "The centre is not the Spindle");
        helper.assertTrue(HalcyonRegion.at(-370, 250, 0) == HalcyonRegion.HUSH, "370 blocks out is not the Hush");
        helper.assertTrue(HalcyonRegion.ringAt(0, -670) == HalcyonRegion.CIRRUS_REEFS || HalcyonRegion.ringAt(0, -670) == HalcyonRegion.WRECKFIELDS,
                "670 blocks out is not the Cirrus Reefs");
        helper.assertTrue(HalcyonRegion.at(HalcyonLayout.OBELISK.getX(), 220, HalcyonLayout.OBELISK.getZ()) == HalcyonRegion.PETALWASH,
                "The Anchorage is not in Petalwash Meadows");
        helper.assertTrue(HalcyonRegion.at(0, 120, 1350) == HalcyonRegion.MIRROR_SHOALS, "The rim is not the Mirror Shoals");
        helper.assertTrue(HalcyonRegion.at(HalcyonLayout.ARRIVAL_LANE.x, 240, HalcyonLayout.ARRIVAL_LANE.z) == HalcyonRegion.MIRROR_SHOALS,
                "The Arrival Lane is not over the Mirror Shoals");
        helper.assertTrue(HalcyonRegion.at(-370, 120, 0) == HalcyonRegion.UNDERBLOOM, "Under the islands is not the Underbloom");
        // The Hush's standing dusk: full in the ring, none at the Spindle or out in Petalwash.
        helper.assertTrue(HalcyonRegion.hushness(-370, 0) > 0.99, "The Hush's middle has no dusk: " + HalcyonRegion.hushness(-370, 0));
        helper.assertTrue(HalcyonRegion.hushness(0, 0) == 0 && HalcyonRegion.hushness(0, 1000) == 0, "The Hush's dusk spills out of the ring");
        int wreckfields = 0;
        for (int x = -1150; x <= 1150; x += 50) {
            for (int z = -1150; z <= 1150; z += 50) {
                wreckfields += HalcyonRegion.ringAt(x, z) == HalcyonRegion.WRECKFIELDS ? 1 : 0;
            }
        }
        helper.assertTrue(wreckfields > 10, "Only " + wreckfields + " samples fell in Wreckfields");
        // The dimension file parses with the Halcyon generator, if the server loaded it.
        LevelStem stem = helper.getLevel().registryAccess().registryOrThrow(Registries.LEVEL_STEM).get(HalcyonLayout.LEVEL.location());
        if (stem != null) {
            helper.assertTrue(stem.generator() instanceof HalcyonChunkGenerator, "The Halcyon's generator is " + stem.generator());
        } else {
            Skyseam.LOGGER.info("Skyseam M3 biomes: the GameTest server has no Halcyon level stem (D20); the boot check covers it");
        }
        helper.succeed();
    }

    /** The Veil: a push that grows towards the rim, a carry back past it, and ships deflected like light off glass. */
    @GameTest(template = EMPTY)
    public static void veilPushesBack(GameTestHelper helper) {
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

    /** The Lantern-Sun: four phases of ten minutes, brightest at noon, an ember in the Hush, and the phase command. */
    @GameTest(template = EMPTY)
    public static void lanternSunKeepsTime(GameTestHelper helper) {
        helper.assertTrue(LanternSun.phase(0) == LanternSun.Phase.DAWN_GLASS && LanternSun.phase(12000) == LanternSun.Phase.NOON_BLOOM
                && LanternSun.phase(24000) == LanternSun.Phase.DUSK_PRISM && LanternSun.phase(36000) == LanternSun.Phase.HUSH
                && LanternSun.phase(48000) == LanternSun.Phase.DAWN_GLASS, "The phases are not ten minutes each");
        helper.assertTrue(LanternSun.brightness(18000) > 0.99f && LanternSun.brightness(42000) < 0.01f, "Noon is not bright or the Hush is not dark");
        Vec3 orb = LanternSun.position(0);
        helper.assertTrue(orb.y == HalcyonLayout.LANTERN_SUN_Y && Math.abs(orb.horizontalDistance() - HalcyonLayout.LANTERN_SUN_ORBIT) < 1.0e-6,
                "The Lantern-Sun is at " + orb);
        var server = helper.getLevel().getServer();
        LanternSun.setPhase(server, LanternSun.Phase.HUSH);
        LanternSun.Phase hush = LanternSun.phase(server);
        LanternSun.setPhase(server, LanternSun.Phase.DAWN_GLASS);
        helper.assertTrue(hush == LanternSun.Phase.HUSH, "The phase command set " + hush);
        helper.succeed();
    }

    /**
     * Every structure file (spec section 14, "Check"): it loads, fits a structure block (48 a side), has every marker
     * docs/STRUCTURE-MARKERS.md lists, and uses only palette blocks. The Obelisk is placed in the world, its motto
     * marker becomes the carved sign, and the Anchorage's flat top is wide enough for its plaza.
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

        // Place the Obelisk well away from other tests and swap its markers.
        StructureTemplate obelisk = level.getStructureManager().get(HalcyonLandmarks.SUNDERED_OBELISK.template()).orElseThrow();
        BlockPos origin = helper.absolutePos(new BlockPos(400, 10, 0));
        StructurePlaceSettings settings = HalcyonLandmarks.SUNDERED_OBELISK.settings(obelisk);
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
