package com.selluastar.skyseam.aperture;

import org.jetbrains.annotations.Nullable;

import com.selluastar.skyseam.external.SableBridge;
import com.selluastar.skyseam.registry.SkyseamItems;
import com.selluastar.skyseam.registry.SkyseamMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * The Harmonic Aperture's screen (spec section 13): one Skychart slot over the player's inventory. Everything else on
 * the screen (charge ring, status ticks, site) comes from the gauge payload, not the menu.
 */
public class ApertureMenu extends AbstractContainerMenu {
    public static final int CHART_X = 152;
    public static final int CHART_Y = 92;
    public static final int INVENTORY_Y = 124;
    /** How close a player must stay to the Aperture, measured where it is in the world (it may be on a moving ship). */
    private static final double REACH = 8;

    private final BlockPos pos;
    @Nullable
    private final ApertureBlockEntity aperture;
    private final Container chart;

    /** Server: the menu for a real Aperture. */
    public ApertureMenu(int id, Inventory inventory, ApertureBlockEntity aperture) {
        this(id, inventory, aperture.getBlockPos(), aperture, aperture.chart());
    }

    /** Client: opened from the server, which sends the Aperture's position. */
    public static ApertureMenu fromNetwork(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        ApertureBlockEntity aperture = inventory.player.level().getBlockEntity(pos) instanceof ApertureBlockEntity found ? found : null;
        return new ApertureMenu(id, inventory, pos, aperture, aperture != null ? aperture.chart() : new SimpleContainer(1));
    }

    private ApertureMenu(int id, Inventory inventory, BlockPos pos, @Nullable ApertureBlockEntity aperture, Container chart) {
        super(SkyseamMenus.APERTURE.get(), id);
        this.pos = pos;
        this.aperture = aperture;
        this.chart = chart;
        addSlot(new Slot(chart, 0, CHART_X, CHART_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(SkyseamItems.SKYCHART.get());
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, INVENTORY_Y + 58));
        }
    }

    public BlockPos aperturePos() {
        return pos;
    }

    @Nullable
    public ApertureBlockEntity aperture() {
        return aperture;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.is(SkyseamItems.SKYCHART.get()) && !slots.get(0).hasItem()) {
            slots.get(0).setByPlayer(stack.split(1));
        } else {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        if (aperture == null || aperture.isRemoved() || !aperture.mayUse(player)) {
            return false;
        }
        Vec3 where = SableBridge.projectToWorld(player.level(), Vec3.atCenterOf(pos));
        return player.distanceToSqr(where) <= REACH * REACH;
    }
}
