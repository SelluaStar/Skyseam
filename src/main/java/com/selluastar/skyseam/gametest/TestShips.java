package com.selluastar.skyseam.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.aperture.ApertureBlockEntity;
import com.selluastar.skyseam.external.ExternalIds;
import com.selluastar.skyseam.external.SableBridge;
import com.selluastar.skyseam.external.Ship;
import com.selluastar.skyseam.external.ShipPose;
import com.selluastar.skyseam.registry.SkyseamBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

/** The small test ship the crossing GameTests use, and checks on it. */
final class TestShips {
    /** 9 planks, a chest and a Create shaft. */
    static final int SHIP_BLOCKS = 11;
    static final int DIAMONDS = 3;

    private TestShips() {}

    /**
     * Planks at y 2 (x and z 1 to 3), a chest with diamonds at (2, 3, 2) and a Create shaft at (1, 3, 1), assembled
     * into one ship. {@code beforeAssembly} runs once the blocks are placed, for adding things that should be taken
     * into the ship with them.
     */
    static Ship build(GameTestHelper helper, Consumer<ServerLevel> beforeAssembly) {
        ServerLevel level = helper.getLevel();
        List<BlockPos> blocks = new ArrayList<>();
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) {
                BlockPos pos = helper.absolutePos(new BlockPos(x, 2, z));
                level.setBlockAndUpdate(pos, Blocks.OAK_PLANKS.defaultBlockState());
                blocks.add(pos);
            }
        }
        BlockPos chest = helper.absolutePos(new BlockPos(2, 3, 2));
        level.setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        ((Container) level.getBlockEntity(chest)).setItem(0, new ItemStack(Items.DIAMOND, DIAMONDS));
        blocks.add(chest);
        BlockPos shaft = helper.absolutePos(new BlockPos(1, 3, 1));
        level.setBlockAndUpdate(shaft, BuiltInRegistries.BLOCK.get(ExternalIds.SHAFT).defaultBlockState());
        blocks.add(shaft);
        beforeAssembly.accept(level);

        return require(SableBridge.assemble(level, helper.absolutePos(new BlockPos(2, 2, 2)), blocks), "Could not assemble the test ship");
    }

    static Ship build(GameTestHelper helper) {
        return build(helper, level -> {});
    }

    /** The ship has all its blocks, the chest still holds its diamonds and the Create shaft kept its block entity. */
    static void assertCargo(GameTestHelper helper, Ship ship, String where) {
        List<BlockPos> blocks = SableBridge.blocks(ship);
        helper.assertTrue(blocks.size() == SHIP_BLOCKS, where + ": the ship has " + blocks.size() + " blocks, expected " + SHIP_BLOCKS);
        int diamonds = 0;
        boolean shaftEntity = false;
        for (BlockPos pos : blocks) {
            BlockEntity entity = ship.level().getBlockEntity(pos);
            if (entity instanceof Container container) {
                diamonds += container.countItem(Items.DIAMOND);
            }
            if (entity != null && BuiltInRegistries.BLOCK.getKey(entity.getBlockState().getBlock()).equals(ExternalIds.SHAFT)) {
                shaftEntity = true;
            }
        }
        helper.assertTrue(diamonds == DIAMONDS, where + ": the chest holds " + diamonds + " diamonds, expected " + DIAMONDS);
        helper.assertTrue(shaftEntity, where + ": the Create shaft lost its block entity");
        Skyseam.LOGGER.info("Skyseam M0 spike, {}: {} blocks, {} diamonds, shaft block entity kept", where, blocks.size(), diamonds);
    }

    /** The chest's position in the ship's plot. */
    static BlockPos chestInPlot(Ship ship) {
        return SableBridge.blocks(ship).stream()
                .filter(pos -> ship.level().getBlockState(pos).is(Blocks.CHEST))
                .findFirst()
                .orElseThrow(() -> new GameTestAssertException("The test ship has no chest"));
    }

    static void forceLoad(ServerLevel level, Vec3 around, boolean load) {
        ChunkPos center = new ChunkPos(BlockPos.containing(around));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                level.setChunkForced(center.x + dx, center.z + dz, load);
            }
        }
    }

    /** A ship with a Harmonic Aperture on it, and where the Aperture is in the ship's plot. */
    record ApertureShip(Ship ship, BlockPos aperture) {
        ApertureBlockEntity apertureEntity() {
            return require(ship.level().getBlockEntity(aperture) instanceof ApertureBlockEntity found ? found : null,
                    "The Aperture's block entity is missing from the ship");
        }
    }

    /** A 3 by 3 plank raft centred on {@code centre} with a Harmonic Aperture on its middle, assembled into a ship. */
    static ApertureShip raftWithAperture(ServerLevel level, BlockPos centre) {
        List<BlockPos> blocks = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-1, 0, -1), centre.offset(1, 0, 1))) {
            level.setBlockAndUpdate(pos, Blocks.OAK_PLANKS.defaultBlockState());
            blocks.add(pos.immutable());
        }
        level.setBlockAndUpdate(centre.above(), SkyseamBlocks.HARMONIC_APERTURE.get().defaultBlockState());
        blocks.add(centre.above());
        Ship ship = require(SableBridge.assemble(level, centre, blocks), "Could not assemble the Aperture raft");
        BlockPos aperture = SableBridge.blocks(ship).stream()
                .filter(pos -> ship.level().getBlockState(pos).is(SkyseamBlocks.HARMONIC_APERTURE.get()))
                .findFirst()
                .orElseThrow(() -> new GameTestAssertException("The Aperture did not go into the ship"));
        return new ApertureShip(ship, aperture);
    }

    /** Holds the ship where it is, moving at {@code velocity} blocks per second: "flying" without going anywhere. */
    static void fly(Ship ship, ShipPose pose, Vec3 velocity) {
        if (!ship.isRemoved()) {
            SableBridge.pin(ship, pose);
            SableBridge.addVelocity(ship, velocity);
        }
    }

    static <T> T require(T value, String message) {
        if (value == null) {
            throw new GameTestAssertException(message);
        }
        return value;
    }
}
