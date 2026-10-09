package com.selluastar.skyseam.aperture;

import org.jetbrains.annotations.Nullable;

import com.selluastar.skyseam.compat.ftbteams.FtbTeamsCompat;
import com.selluastar.skyseam.registry.SkyseamDataComponents;
import com.selluastar.skyseam.registry.SkyseamItems;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Who may use a Harmonic Aperture (spec section 5): it is soulbound to the player who crafted it, and their
 * teammates can use it too. Teammates are players on the owner's vanilla scoreboard team, or in the owner's FTB Teams
 * party when the pack runs FTB Teams. An Aperture with no owner (given by a command or placed with
 * {@code /setblock}) can be used by anyone.
 */
public final class ApertureOwnership {
    private ApertureOwnership() {}

    public static boolean mayUse(@Nullable ApertureOwner owner, Player player) {
        return owner == null || owner.id().equals(player.getUUID()) || areTeammates(owner, player);
    }

    /** Spec section 5: a crafted Aperture is bound to the player who crafted it. */
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        ItemStack result = event.getCrafting();
        if (result.is(SkyseamItems.HARMONIC_APERTURE.get()) && result.get(SkyseamDataComponents.OWNER.get()) == null) {
            result.set(SkyseamDataComponents.OWNER.get(), ApertureOwner.of(event.getEntity()));
        }
    }

    /** True if the player is on the owner's team: a vanilla scoreboard team, or an FTB Teams party. */
    public static boolean areTeammates(ApertureOwner owner, Player player) {
        PlayerTeam team = player.getTeam();
        if (team != null && team.getPlayers().contains(owner.name())) {
            return true;
        }
        return FtbTeamsCompat.sameTeam(owner.id(), player.getUUID());
    }
}
