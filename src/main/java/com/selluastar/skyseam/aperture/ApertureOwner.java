package com.selluastar.skyseam.aperture;

import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;

/**
 * Who a Harmonic Aperture is bound to (spec section 5: "soulbound to the crafter"). The name is kept for display and
 * for vanilla scoreboard teams, which list players by name.
 */
public record ApertureOwner(UUID id, String name) {
    public static final Codec<ApertureOwner> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(ApertureOwner::id),
            Codec.STRING.fieldOf("name").forGetter(ApertureOwner::name)).apply(instance, ApertureOwner::new));
    public static final StreamCodec<ByteBuf, ApertureOwner> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, ApertureOwner::id,
            ByteBufCodecs.STRING_UTF8, ApertureOwner::name,
            ApertureOwner::new);

    public static ApertureOwner of(Player player) {
        return new ApertureOwner(player.getUUID(), player.getGameProfile().getName());
    }
}
