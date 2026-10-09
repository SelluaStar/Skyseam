package com.selluastar.skyseam.skychart;

import java.util.List;

import com.selluastar.skyseam.config.SkyseamConfig;
import com.selluastar.skyseam.network.SkychartPayload;
import com.selluastar.skyseam.registry.SkyseamSounds;
import com.selluastar.skyseam.seam.site.SeamSite;
import com.selluastar.skyseam.seam.site.SeamSites;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Skychart (spec section 5): a chart of the sky that marks the nearest Seam site (the author's choice with many
 * sites, docs/DECISIONS.md K47). Held in either hand it shows the way on the HUD; used, it unfolds into a chart of the
 * sites around you; in a Harmonic Aperture's chart slot it gives the gauge the site's details.
 */
public class SkychartItem extends Item {
    /** How far around the holder the chart shows sites. */
    public static final double CHART_RADIUS = 4096;
    /** How often a held chart is brought up to date. */
    private static final int HELD_PERIOD = 10;

    public SkychartItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SkyseamSounds.SKYCHART_UNFOLD.get(), SoundSource.PLAYERS, 0.8f, 1);
            send(serverPlayer, true);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (entity instanceof ServerPlayer player && level.getGameTime() % HELD_PERIOD == 0
                && (selected || player.getOffhandItem() == stack)) {
            send(player, false);
        }
    }

    /** Sends the sites around the player, nearest first. */
    public static void send(ServerPlayer player, boolean open) {
        PacketDistributor.sendToPlayer(player, new SkychartPayload(open, sitesAround(player.serverLevel(), player.getX(), player.getZ())));
    }

    public static List<SkychartPayload.Site> sitesAround(ServerLevel level, double x, double z) {
        List<SeamSite> sites = SeamSites.within(level, x, z, CHART_RADIUS);
        int altitude = SkyseamConfig.MIN_ALTITUDE.get();
        return sites.stream().limit(32).map(site -> {
            int ground = SeamSites.groundY(level, site);
            return new SkychartPayload.Site(site.x(), site.z(), ground, ground + altitude);
        }).toList();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.skyseam.skychart.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.skyseam.skychart.tooltip.slot").withStyle(ChatFormatting.DARK_GRAY));
    }
}
