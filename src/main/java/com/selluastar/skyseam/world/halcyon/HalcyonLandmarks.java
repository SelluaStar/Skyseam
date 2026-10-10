package com.selluastar.skyseam.world.halcyon;

import java.util.Optional;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.world.HalcyonLayout;
import com.selluastar.skyseam.world.structure.StructureMarkers;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * The Halcyon's fixed landmarks (spec section 14: "Halcyon landmarks sit at fixed positions chosen by the dimension
 * generator"). Each is a structure file placed at its spot a chunk at a time, as the chunks around it generate, so a
 * large build never stalls the server. Its data markers are swapped for their real blocks as they are placed
 * ({@link StructureMarkers}).
 *
 * <p>M3 places the Sundered Obelisk on the Anchorage, its gate turned to face the Arrival Lane.
 */
public final class HalcyonLandmarks {
    /** A structure file placed at a fixed spot: its template's centre column sits on {@code centre}. */
    public record Landmark(ResourceLocation template, BlockPos centre, Rotation rotation) {
        /** Where the template goes and how, for a template of the given size. */
        public StructurePlaceSettings settings(StructureTemplate structure) {
            return new StructurePlaceSettings()
                    .setRotation(rotation)
                    .setRotationPivot(new BlockPos(structure.getSize().getX() / 2, 0, structure.getSize().getZ() / 2))
                    .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
        }

        /** The corner the template is placed from, so that its centre lands on {@link #centre}. */
        public BlockPos origin(StructureTemplate structure) {
            return centre.offset(-structure.getSize().getX() / 2, 0, -structure.getSize().getZ() / 2);
        }

        public BoundingBox bounds(StructureTemplate structure) {
            return structure.getBoundingBox(settings(structure), origin(structure));
        }
    }

    /** The Sundered Obelisk: its gate faces south in the file, and the lane is to the south-west. */
    public static final Landmark SUNDERED_OBELISK = new Landmark(Skyseam.id("sundered_obelisk"), HalcyonLayout.OBELISK, Rotation.NONE);

    private HalcyonLandmarks() {}

    static void place(WorldGenLevel level, ChunkAccess chunk) {
        place(level, chunk.getPos(), SUNDERED_OBELISK);
    }

    private static void place(WorldGenLevel level, ChunkPos chunk, Landmark landmark) {
        MinecraftServer server = level.getServer();
        Optional<StructureTemplate> found = server == null ? Optional.empty() : server.getStructureManager().get(landmark.template());
        if (found.isEmpty()) {
            Skyseam.LOGGER.error("Landmark structure {} is missing", landmark.template());
            return;
        }
        StructureTemplate structure = found.get();
        BoundingBox chunkBox = new BoundingBox(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ(), chunk.getMaxBlockX(),
                level.getMaxBuildHeight() - 1, chunk.getMaxBlockZ());
        if (!landmark.bounds(structure).intersects(chunkBox)) {
            return;
        }
        BlockPos origin = landmark.origin(structure);
        StructurePlaceSettings settings = landmark.settings(structure).setBoundingBox(chunkBox);
        RandomSource random = RandomSource.create(chunk.toLong());
        structure.placeInWorld(level, origin, origin, settings, random, Block.UPDATE_CLIENTS);
        StructureMarkers.apply(level, structure, origin, landmark.settings(structure), chunkBox);
    }
}
