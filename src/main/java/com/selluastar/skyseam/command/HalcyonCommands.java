package com.selluastar.skyseam.command;

import java.util.Locale;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.selluastar.skyseam.halcyon.LanternSun;
import com.selluastar.skyseam.world.HalcyonLayout;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Debug and filming (spec section 6, "teleport to landmarks"): {@code /skyseam halcyon tp <place>} and
 * {@code /skyseam halcyon phase <phase>}. Operators only, like the rest of {@code /skyseam}.
 */
final class HalcyonCommands {
    /** Places to go: a name, a column, and the height to stand at (or above the ground there, if higher). */
    private enum Place {
        ARRIVAL(HalcyonLayout.ARRIVAL_LANE.x, HalcyonLayout.ARRIVAL_LANE.z, HalcyonLayout.ARRIVAL_LANE.y),
        ANCHORAGE(HalcyonLayout.OBELISK.getX() + 0.5, HalcyonLayout.OBELISK.getZ() + 12.5, 0),
        SPINDLE(0.5, 0.5, 300),
        HUSH(-370.5, 0.5, 330),
        CIRRUS(0.5, -670.5, 330),
        PETALWASH(985.5, 0.5, 260),
        SHOALS(0.5, 1320.5, 150),
        VEIL(1440.5, 0.5, 250);

        final double x;
        final double z;
        final double y;

        Place(double x, double z, double y) {
            this.x = x;
            this.z = z;
            this.y = y;
        }
    }

    private HalcyonCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> tp = Commands.literal("tp");
        for (Place place : Place.values()) {
            tp.then(Commands.literal(place.name().toLowerCase(Locale.ROOT)).executes(context -> teleport(context, place)));
        }
        LiteralArgumentBuilder<CommandSourceStack> phase = Commands.literal("phase");
        for (LanternSun.Phase value : LanternSun.Phase.values()) {
            phase.then(Commands.literal(phaseName(value)).executes(context -> setPhase(context, value)));
        }
        return Commands.literal("halcyon").then(tp).then(phase);
    }

    private static String phaseName(LanternSun.Phase phase) {
        return switch (phase) {
            case DAWN_GLASS -> "dawn";
            case NOON_BLOOM -> "noon";
            case DUSK_PRISM -> "dusk";
            case HUSH -> "hush";
        };
    }

    private static int teleport(CommandContext<CommandSourceStack> context, Place place) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel halcyon = source.getServer().getLevel(HalcyonLayout.LEVEL);
        if (halcyon == null) {
            source.sendFailure(Component.translatable("commands.skyseam.halcyon.missing"));
            return 0;
        }
        int ground = halcyon.getChunk((int) Math.floor(place.x) >> 4, (int) Math.floor(place.z) >> 4)
                .getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(place.x) & 15, (int) Math.floor(place.z) & 15) + 1;
        double y = Math.max(place.y, ground);
        Vec3 to = new Vec3(place.x, y, place.z);
        player.teleportTo(halcyon, to.x, to.y, to.z, player.getYRot(), player.getXRot());
        String name = place.name().toLowerCase(Locale.ROOT);
        source.sendSuccess(() -> Component.translatable("commands.skyseam.halcyon.tp", name, (int) to.x, (int) to.y, (int) to.z), false);
        return 1;
    }

    private static int setPhase(CommandContext<CommandSourceStack> context, LanternSun.Phase phase) {
        LanternSun.setPhase(context.getSource().getServer(), phase);
        context.getSource().sendSuccess(() -> Component.translatable("commands.skyseam.halcyon.phase",
                Component.translatable("skyseam.phase." + phaseName(phase))), true);
        return 1;
    }
}
