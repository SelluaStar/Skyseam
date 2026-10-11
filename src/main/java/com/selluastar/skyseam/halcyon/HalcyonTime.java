package com.selluastar.skyseam.halcyon;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.selluastar.skyseam.world.HalcyonLayout;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;

/**
 * Time works differently in the Halcyon (docs/DECISIONS.md K82): its day is the Lantern-Sun's, so vanilla's
 * {@code /time} is turned away there with a pointer to {@code /skyseam halcyon time}, and beds don't bring sleep. A way
 * to rest in the Halcyon is for a later part.
 */
public final class HalcyonTime {
    private HalcyonTime() {}

    /** Turns away {@code /time} run from inside the Halcyon. Run elsewhere (the console, the Overworld) it works as ever. */
    public static void onCommand(CommandEvent event) {
        ParseResults<CommandSourceStack> parse = event.getParseResults();
        CommandContextBuilder<CommandSourceStack> context = parse.getContext();
        if (context.getNodes().isEmpty() || !"time".equals(context.getNodes().get(0).getNode().getName())) {
            return;
        }
        CommandSourceStack source = context.getSource();
        if (source.getLevel().dimension() != HalcyonLayout.LEVEL) {
            return;
        }
        event.setCanceled(true);
        source.sendFailure(Component.translatable("commands.skyseam.halcyon.time.differs"));
    }

    /** Beds bring no sleep in the Halcyon. */
    public static void onSleep(CanPlayerSleepEvent event) {
        if (event.getLevel().dimension() != HalcyonLayout.LEVEL) {
            return;
        }
        event.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);
        event.getEntity().displayClientMessage(Component.translatable("block.skyseam.bed.halcyon"), true);
    }
}
