package com.selluastar.skyseam.halcyon.tide;

import java.util.Locale;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * The Halcyon's Tides (spec section 8, "Tides"; docs/DECISIONS.md K111): weather on top of the Lantern-Sun's day. Calm
 * is the usual; every 25 minutes or so one of the others runs for 6 to 10.
 *
 * <ul>
 * <li>{@link #PRISMFALL}: glass-shard rain, Prism Dust falling to gather (Shardlings, when they exist).</li>
 * <li>{@link #GALE}: crosswinds, and the wind lanes blow back harder (Gale Harriers, when they exist).</li>
 * <li>{@link #BLOOM}: pollen on the air, and helpers make twice as much (Pollen Bloats, when they exist).</li>
 * <li>{@link #STILLNESS}: rare. The wind stops, sound deepens, and noise carries twice as far.</li>
 * </ul>
 */
public enum Tide implements StringRepresentable {
    CALM(0xFFFFFF),
    PRISMFALL(0xC8D6FF),
    GALE(0xB4C2D2),
    BLOOM(0xFFD8CC),
    STILLNESS(0x8C84A8);

    /** The colour the sky leans towards while it runs. */
    private final int colour;

    Tide(int colour) {
        this.colour = colour;
    }

    public int colour() {
        return colour;
    }

    /** Its name in commands and the lang file: "prismfall". */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** "Prismfall", "the Gale". */
    public Component title() {
        return Component.translatable("tide.skyseam." + id());
    }

    @Override
    public String getSerializedName() {
        return id();
    }

    public static Tide byId(int ordinal) {
        Tide[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : CALM;
    }
}
