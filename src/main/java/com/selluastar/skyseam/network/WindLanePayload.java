package com.selluastar.skyseam.network;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.halcyon.wind.WindLane;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Server to client: the wind lanes round the player (docs/DECISIONS.md K109, K110), as the points each one's
 * middle passes through, so the client traces the same curves to draw them. Which way the wind runs the client works
 * out itself, from the Lantern-Sun's clock it shares with the server.
 */
public record WindLanePayload(List<Lane> lanes) implements CustomPacketPayload {
    public static final Type<WindLanePayload> TYPE = new Type<>(Skyseam.id("wind_lanes"));
    public static final StreamCodec<FriendlyByteBuf, WindLanePayload> STREAM_CODEC = StreamCodec.of(WindLanePayload::write, WindLanePayload::read);

    /** One lane as sent; {@code before} and {@code after} are null unless it runs on from and into others. */
    public record Lane(int id, String name, List<Vec3> points, double pace, @Nullable Vec3 before, @Nullable Vec3 after) {
        public WindLane toLane() {
            return new WindLane(id, name, points, pace, before, after);
        }
    }

    public static WindLanePayload of(List<WindLane> lanes) {
        return new WindLanePayload(lanes.stream().map(lane -> new Lane(lane.id(), lane.name(), lane.points(), lane.pace(), lane.before(), lane.after()))
                .toList());
    }

    private static void write(FriendlyByteBuf buf, WindLanePayload payload) {
        buf.writeVarInt(payload.lanes.size());
        for (Lane lane : payload.lanes) {
            buf.writeVarInt(lane.id());
            buf.writeUtf(lane.name(), 128);
            buf.writeDouble(lane.pace());
            buf.writeVarInt(lane.points().size());
            for (Vec3 point : lane.points()) {
                writeVec(buf, point);
            }
            buf.writeBoolean(lane.before() != null);
            if (lane.before() != null) {
                writeVec(buf, lane.before());
            }
            buf.writeBoolean(lane.after() != null);
            if (lane.after() != null) {
                writeVec(buf, lane.after());
            }
        }
    }

    private static void writeVec(FriendlyByteBuf buf, Vec3 point) {
        buf.writeDouble(point.x);
        buf.writeDouble(point.y);
        buf.writeDouble(point.z);
    }

    private static Vec3 readVec(FriendlyByteBuf buf) {
        return new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
    }

    private static WindLanePayload read(FriendlyByteBuf buf) {
        int count = Math.min(buf.readVarInt(), 256);
        List<Lane> lanes = new ArrayList<>(count);
        for (int k = 0; k < count; k++) {
            int id = buf.readVarInt();
            String name = buf.readUtf(128);
            double pace = buf.readDouble();
            int size = Math.min(buf.readVarInt(), 4096);
            List<Vec3> points = new ArrayList<>(size);
            for (int p = 0; p < size; p++) {
                points.add(readVec(buf));
            }
            Vec3 before = buf.readBoolean() ? readVec(buf) : null;
            Vec3 after = buf.readBoolean() ? readVec(buf) : null;
            if (points.size() >= 2) {
                lanes.add(new Lane(id, name, points, pace, before, after));
            }
        }
        return new WindLanePayload(lanes);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
