package com.selluastar.skyseam.command;

import java.util.Arrays;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.selluastar.skyseam.halcyon.tide.Tide;
import com.selluastar.skyseam.halcyon.tide.TideSchedule;
import com.selluastar.skyseam.halcyon.tide.Tides;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * {@code /skyseam halcyon tide} (op 2; spec section 6, "force a Tide"; docs/DECISIONS.md K111): on its own, the Tide
 * running and the next; {@code start <tide> [minutes]} starts one now (8 minutes unless told), in place of any that
 * would run meanwhile; {@code stop} ends the one running.
 */
final class TideCommands {
    private static final int DEFAULT_MINUTES = 8;

    private TideCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("tide")
                .executes(TideCommands::info)
                .then(Commands.literal("start")
                        .then(Commands.argument("tide", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(Tide.values()).filter(tide -> tide != Tide.CALM).map(Tide::id), builder))
                                .executes(context -> start(context, DEFAULT_MINUTES))
                                .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 120))
                                        .executes(context -> start(context, IntegerArgumentType.getInteger(context, "minutes"))))))
                .then(Commands.literal("stop").executes(TideCommands::stop));
    }

    private static int info(CommandContext<CommandSourceStack> context) {
        MinecraftServer server = context.getSource().getServer();
        long now = server.overworld().getGameTime();
        TideSchedule.Forecast forecast = Tides.forecast(server);
        TideSchedule.Window current = forecast.current();
        Component running = current == null ? Component.translatable("commands.skyseam.halcyon.tide.calm")
                : Component.translatable("commands.skyseam.halcyon.tide.running", current.tide().title(), minutes(current.end() - now));
        Component next = forecast.next() == null ? Component.translatable("commands.skyseam.halcyon.tide.none_next")
                : Component.translatable("commands.skyseam.halcyon.tide.next", forecast.next().tide().title(), minutes(forecast.next().start() - now),
                        minutes(forecast.next().end() - forecast.next().start()));
        context.getSource().sendSuccess(() -> running, false);
        context.getSource().sendSuccess(() -> next, false);
        return current == null ? 0 : current.tide().ordinal();
    }

    private static int start(CommandContext<CommandSourceStack> context, int minutes) {
        String id = StringArgumentType.getString(context, "tide");
        Tide tide = Arrays.stream(Tide.values()).filter(t -> t != Tide.CALM && t.id().equals(id)).findFirst().orElse(null);
        if (tide == null) {
            context.getSource().sendFailure(Component.translatable("commands.skyseam.halcyon.tide.unknown", id));
            return 0;
        }
        Tides.force(context.getSource().getServer(), tide, minutes * 1200L);
        context.getSource().sendSuccess(() -> Component.translatable("commands.skyseam.halcyon.tide.started", tide.title(), minutes), true);
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> context) {
        if (!Tides.stop(context.getSource().getServer())) {
            context.getSource().sendFailure(Component.translatable("commands.skyseam.halcyon.tide.calm"));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.translatable("commands.skyseam.halcyon.tide.stopped"), true);
        return 1;
    }

    /** Ticks as whole minutes, at least one. */
    private static String minutes(long ticks) {
        return String.valueOf(Math.max(1, Math.round(ticks / 1200.0)));
    }
}
