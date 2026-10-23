package com.selluastar.skyseam.command;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.selluastar.skyseam.echo.NoiseBus;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /skyseam noise} (op 2), for testing and filming the noise bus (docs/DECISIONS.md K112): {@code emit <radius>
 * [kind]} makes a noise where you are; {@code watch} toggles seeing every noise within {@link #WATCHED} blocks of you, as
 * a line in chat and a ring of light at its reach.
 */
final class NoiseCommands {
    /** How far off a watched noise is shown. */
    private static final double WATCHED = 160;
    /** Points round a watched noise's ring, at most. */
    private static final int RING = 96;
    private static final Set<UUID> WATCHING = new HashSet<>();

    static {
        NoiseBus.overhear(NoiseCommands::show);
    }

    private NoiseCommands() {}

    static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("noise")
                .then(Commands.literal("emit")
                        .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1, 512))
                                .executes(context -> emit(context, NoiseBus.Kind.OTHER))
                                .then(Commands.argument("kind", StringArgumentType.word())
                                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                                Arrays.stream(NoiseBus.Kind.values()).map(kind -> kind.name().toLowerCase(Locale.ROOT)), builder))
                                        .executes(context -> emit(context, kind(StringArgumentType.getString(context, "kind")))))))
                .then(Commands.literal("watch").executes(NoiseCommands::watch));
    }

    private static NoiseBus.Kind kind(String name) {
        return Arrays.stream(NoiseBus.Kind.values()).filter(kind -> kind.name().equalsIgnoreCase(name)).findFirst().orElse(NoiseBus.Kind.OTHER);
    }

    private static int emit(CommandContext<CommandSourceStack> context, NoiseBus.Kind kind) {
        CommandSourceStack source = context.getSource();
        double radius = DoubleArgumentType.getDouble(context, "radius");
        NoiseBus.Noise noise = NoiseBus.emit(source.getLevel(), source.getPosition(), radius, kind, source.getEntity());
        double carried = noise == null ? 0 : noise.radius();
        source.sendSuccess(() -> Component.translatable("commands.skyseam.noise.emitted", kind.name().toLowerCase(Locale.ROOT),
                String.format(Locale.ROOT, "%.0f", radius), String.format(Locale.ROOT, "%.0f", carried)), false);
        return (int) Math.round(carried);
    }

    private static int watch(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        boolean now = WATCHING.add(player.getUUID()) || !WATCHING.remove(player.getUUID());
        context.getSource().sendSuccess(() -> Component.translatable(now ? "commands.skyseam.noise.watching" : "commands.skyseam.noise.not_watching"), false);
        return now ? 1 : 0;
    }

    /** Shows a noise to those watching near it: a line in chat, and a ring of light where it reaches. */
    private static void show(NoiseBus.Noise noise) {
        if (WATCHING.isEmpty()) {
            return;
        }
        for (ServerPlayer player : noise.level().players()) {
            if (!WATCHING.contains(player.getUUID()) || player.position().distanceTo(noise.at()) > WATCHED) {
                continue;
            }
            String by = noise.source() == null ? "-" : noise.source().getName().getString();
            player.sendSystemMessage(Component.translatable("commands.skyseam.noise.heard", noise.kind().name().toLowerCase(Locale.ROOT), by,
                    String.format(Locale.ROOT, "%.0f", noise.radius()), (int) noise.at().x, (int) noise.at().y, (int) noise.at().z,
                    String.format(Locale.ROOT, "%.0f", player.position().distanceTo(noise.at()))));
            int points = (int) Math.min(RING, Math.max(16, noise.radius() * 1.5));
            for (int k = 0; k < points; k++) {
                double a = Math.PI * 2 * k / points;
                noise.level().sendParticles(player, ParticleTypes.END_ROD, true, noise.at().x + Math.cos(a) * noise.radius(), noise.at().y + 1,
                        noise.at().z + Math.sin(a) * noise.radius(), 1, 0, 0.2, 0, 0);
            }
        }
    }
}
