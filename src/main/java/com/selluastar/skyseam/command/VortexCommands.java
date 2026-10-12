package com.selluastar.skyseam.command;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.selluastar.skyseam.halcyon.Arrival;
import com.selluastar.skyseam.halcyon.vortex.FluxVortex;
import com.selluastar.skyseam.halcyon.vortex.FluxVortices;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /skyseam halcyon vortex} (op 2), for testing and filming flux vortices (docs/DECISIONS.md K97):
 * {@code here [seconds]} and {@code at <x> <z> [seconds]} summon one, {@code find} puts you where you can watch the
 * nearest, {@code list} names those near you, {@code clear} removes the summoned ones.
 */
final class VortexCommands {
    private static final double SEARCH = 3000;
    private static final int DEFAULT_SECONDS = 120;

    private VortexCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("vortex")
                .then(Commands.literal("here")
                        .executes(context -> here(context, DEFAULT_SECONDS))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(10, 3600))
                                .executes(context -> here(context, IntegerArgumentType.getInteger(context, "seconds")))))
                .then(Commands.literal("at")
                        .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                                .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                        .executes(context -> at(context, DEFAULT_SECONDS))
                                        .then(Commands.argument("seconds", IntegerArgumentType.integer(10, 3600))
                                                .executes(context -> at(context, IntegerArgumentType.getInteger(context, "seconds")))))))
                .then(Commands.literal("find").executes(VortexCommands::find))
                .then(Commands.literal("list").executes(VortexCommands::list))
                .then(Commands.literal("clear").executes(VortexCommands::clear));
    }

    private static int here(CommandContext<CommandSourceStack> context, int seconds) {
        Vec3 at = context.getSource().getPosition();
        return summon(context.getSource(), at.x, at.z, seconds);
    }

    private static int at(CommandContext<CommandSourceStack> context, int seconds) {
        return summon(context.getSource(), DoubleArgumentType.getDouble(context, "x"), DoubleArgumentType.getDouble(context, "z"), seconds);
    }

    private static int summon(CommandSourceStack source, double x, double z, int seconds) {
        ServerLevel level = source.getLevel();
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
        if (ground <= level.getMinBuildHeight()) {
            ground = Mth.floor(source.getPosition().y);
        }
        FluxVortex vortex = FluxVortices.summon(level, x, z, ground, seconds);
        source.sendSuccess(() -> Component.translatable("commands.skyseam.halcyon.vortex.here",
                String.format("%.0f, %d, %.0f", vortex.x(), vortex.baseY(), vortex.z()), seconds), true);
        return 1;
    }

    private static int find(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = player.serverLevel();
        long time = level.getGameTime();
        Optional<FluxVortex> nearest = FluxVortices.around(level, player.getX(), player.getZ(), SEARCH, time).stream()
                .min(Comparator.comparingDouble(vortex -> Math.hypot(vortex.x() - player.getX(), vortex.z() - player.getZ())));
        if (nearest.isEmpty()) {
            source.sendFailure(Component.translatable("commands.skyseam.halcyon.vortex.none", (int) SEARCH));
            return 0;
        }
        FluxVortex vortex = nearest.get();
        // Watch from 70 blocks off, a little above its foot, on the side the player came from.
        double dx = player.getX() - vortex.x();
        double dz = player.getZ() - vortex.z();
        double flat = Math.max(1, Math.hypot(dx, dz));
        double x = vortex.x() + dx / flat * 70;
        double z = vortex.z() + dz / flat * 70;
        double y = vortex.baseY() + 45;
        player.teleportTo(level, x, y, z, player.getYRot(), player.getXRot());
        Arrival.face(player, new Vec3(vortex.x(), vortex.baseY() + 80, vortex.z()), 60);
        source.sendSuccess(() -> Component.translatable("commands.skyseam.halcyon.vortex.found",
                String.format("%.0f, %d, %.0f", vortex.x(), vortex.baseY(), vortex.z()), state(vortex, time), vortex.secondsLeft(time)), false);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        long time = level.getGameTime();
        Vec3 at = source.getPosition();
        List<FluxVortex> near = FluxVortices.around(level, at.x, at.z, SEARCH, time);
        String names = near.stream().map(vortex -> String.format("(%.0f, %d, %.0f) %d blocks, %ds left", vortex.x(), vortex.baseY(), vortex.z(),
                (int) Math.hypot(vortex.x() - at.x, vortex.z() - at.z), vortex.secondsLeft(time))).collect(Collectors.joining("; "));
        source.sendSuccess(() -> Component.translatable("commands.skyseam.halcyon.vortex.list", near.size(), (int) SEARCH, names), false);
        return near.size();
    }

    private static int clear(CommandContext<CommandSourceStack> context) {
        int cleared = FluxVortices.clearSummoned(context.getSource().getLevel());
        context.getSource().sendSuccess(() -> Component.translatable("commands.skyseam.halcyon.vortex.cleared", cleared), true);
        return cleared;
    }

    private static Component state(FluxVortex vortex, long time) {
        return Component.translatable("commands.skyseam.halcyon.vortex.state." + switch (vortex.phase(time)) {
            case FORMING, WAITING -> "forming";
            case UNRAVELLING, GONE -> "unravelling";
            default -> "raging";
        });
    }
}
