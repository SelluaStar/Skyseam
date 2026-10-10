package com.selluastar.skyseam.world.structure;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import com.selluastar.skyseam.Skyseam;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Data markers (spec section 14): every functional spot in a structure file is a structure block in data mode whose
 * text names what goes there, {@code name} or {@code name:facing}. After a structure is placed, each marker is swapped
 * for its real block here, so a hand-built replacement only needs the same markers. The list of markers each
 * structure must have is in docs/STRUCTURE-MARKERS.md.
 *
 * <p>Markers whose feature does not exist yet are cleared to air and logged; their milestone adds the block.
 */
public final class StructureMarkers {
    /** What each marker becomes. The facing is already turned with the structure. */
    private static final Map<String, BiConsumer<Placement, ServerLevelAccessor>> HANDLERS = Map.of(
            "motto", StructureMarkers::motto);

    /** One marker in the world: where it is, which way it faces, and its full text. */
    public record Placement(BlockPos pos, Direction facing, String name) {}

    private StructureMarkers() {}

    /** Swaps every marker of {@code structure}, placed at {@code origin} with {@code settings}, that lies inside {@code box}. */
    public static void apply(ServerLevelAccessor level, StructureTemplate structure, BlockPos origin, StructurePlaceSettings settings, BoundingBox box) {
        for (StructureTemplate.StructureBlockInfo info : markers(structure, origin, settings)) {
            if (!box.isInside(info.pos())) {
                continue;
            }
            Placement placement = parse(info, settings);
            level.setBlock(info.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            BiConsumer<Placement, ServerLevelAccessor> handler = HANDLERS.get(placement.name());
            if (handler != null) {
                handler.accept(placement, level);
            } else {
                Skyseam.LOGGER.debug("Structure marker '{}' at {} has no block yet; left as air", placement.name(), info.pos().toShortString());
            }
        }
    }

    /** Every data marker of a structure, at its world position for this placement. */
    public static List<StructureTemplate.StructureBlockInfo> markers(StructureTemplate structure, BlockPos origin, StructurePlaceSettings settings) {
        return structure.filterBlocks(origin, settings, Blocks.STRUCTURE_BLOCK).stream()
                .filter(info -> info.nbt() != null && "DATA".equals(info.nbt().getString("mode")))
                .toList();
    }

    /** The marker's name, without its facing. */
    public static String name(StructureTemplate.StructureBlockInfo info) {
        String text = info.nbt() == null ? "" : info.nbt().getString("metadata");
        int colon = text.indexOf(':');
        return colon < 0 ? text : text.substring(0, colon);
    }

    private static Placement parse(StructureTemplate.StructureBlockInfo info, StructurePlaceSettings settings) {
        String text = info.nbt().getString("metadata");
        int colon = text.indexOf(':');
        Direction facing = Direction.NORTH;
        if (colon >= 0) {
            Direction named = Direction.byName(text.substring(colon + 1));
            facing = named != null && named.getAxis().isHorizontal() ? named : Direction.NORTH;
        }
        return new Placement(info.pos(), settings.getRotation().rotate(facing), name(info));
    }

    /** The Wrights' motto (spec section 4), carved at the Anchorage: a cherry-wood sign facing out from the gate. */
    private static void motto(Placement placement, ServerLevelAccessor level) {
        level.setBlock(placement.pos(), Blocks.CHERRY_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, placement.facing()),
                Block.UPDATE_CLIENTS);
        if (level.getBlockEntity(placement.pos()) instanceof SignBlockEntity sign) {
            SignText text = new SignText().setColor(DyeColor.BROWN)
                    .setMessage(0, Component.translatable("skyseam.motto.line1"))
                    .setMessage(1, Component.translatable("skyseam.motto.line2"))
                    .setMessage(2, Component.translatable("skyseam.motto.line3"))
                    .setMessage(3, Component.translatable("skyseam.motto.line4"));
            // Loaded as saved data rather than set: a sign in a chunk that is still generating has no level to update.
            CompoundTag tag = new CompoundTag();
            SignText.DIRECT_CODEC.encodeStart(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), text).result()
                    .ifPresent(front -> tag.put("front_text", front));
            tag.putBoolean("is_waxed", true);
            sign.loadWithComponents(tag, level.registryAccess());
            sign.setChanged();
        }
    }
}
