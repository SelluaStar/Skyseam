package com.selluastar.skyseam.network;

import org.jetbrains.annotations.Nullable;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.halcyon.tide.Tide;
import com.selluastar.skyseam.halcyon.tide.TideSchedule;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: the Tides' forecast (docs/DECISIONS.md K111), the Tide running now and the next, each with the game
 * times it starts and ends, so the client draws and plays them from the same windows the server works from.
 */
public record TidePayload(@Nullable TideSchedule.Window current, @Nullable TideSchedule.Window next) implements CustomPacketPayload {
    public static final Type<TidePayload> TYPE = new Type<>(Skyseam.id("tides"));
    public static final StreamCodec<FriendlyByteBuf, TidePayload> STREAM_CODEC = StreamCodec.of(TidePayload::write, TidePayload::read);

    public static TidePayload of(TideSchedule.Forecast forecast) {
        return new TidePayload(forecast.current(), forecast.next());
    }

    public TideSchedule.Forecast forecast() {
        return new TideSchedule.Forecast(current, next);
    }

    private static void write(FriendlyByteBuf buf, TidePayload payload) {
        writeWindow(buf, payload.current);
        writeWindow(buf, payload.next);
    }

    private static TidePayload read(FriendlyByteBuf buf) {
        return new TidePayload(readWindow(buf), readWindow(buf));
    }

    private static void writeWindow(FriendlyByteBuf buf, @Nullable TideSchedule.Window window) {
        buf.writeBoolean(window != null);
        if (window != null) {
            buf.writeVarInt(window.tide().ordinal());
            buf.writeLong(window.start());
            buf.writeLong(window.end());
        }
    }

    @Nullable
    private static TideSchedule.Window readWindow(FriendlyByteBuf buf) {
        return buf.readBoolean() ? new TideSchedule.Window(Tide.byId(buf.readVarInt()), buf.readLong(), buf.readLong()) : null;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
