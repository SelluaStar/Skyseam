package com.selluastar.skyseam.external;

import java.util.UUID;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;

import net.minecraft.server.level.ServerLevel;

/**
 * A handle to one Sable ship (a server sub-level). The rest of Skyseam holds ships through this class and acts on
 * them through {@link SableBridge}, so no other package imports a Sable type (spec section 1, rule 9).
 */
public final class Ship {
    final ServerSubLevel subLevel;

    Ship(ServerSubLevel subLevel) {
        this.subLevel = subLevel;
    }

    public UUID id() {
        return subLevel.getUniqueId();
    }

    public ServerLevel level() {
        return subLevel.getLevel();
    }

    /** True once Sable has removed or unloaded the ship. A removed handle must not be used again. */
    public boolean isRemoved() {
        return subLevel.isRemoved();
    }

    @Override
    public String toString() {
        return "Ship[" + id() + " in " + level().dimension().location() + "]";
    }
}
