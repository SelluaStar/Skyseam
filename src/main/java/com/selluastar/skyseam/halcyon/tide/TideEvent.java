package com.selluastar.skyseam.halcyon.tide;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.Event;

/**
 * Posted on NeoForge's event bus as the Halcyon's Tides change (docs/DECISIONS.md K111), for other mods and pack scripts
 * (spec "Modpack extras": "KubeJS events for ... Tide change"; KubeJS can listen to it with {@code NativeEvents.onEvent}).
 */
public abstract class TideEvent extends Event {
    private final MinecraftServer server;

    protected TideEvent(MinecraftServer server) {
        this.server = server;
    }

    public MinecraftServer getServer() {
        return server;
    }

    /** A Tide began, ended, or one gave way to another. Either side is null for Calm. */
    public static final class Changed extends TideEvent {
        @Nullable
        private final TideSchedule.Window from;
        @Nullable
        private final TideSchedule.Window to;

        public Changed(MinecraftServer server, @Nullable TideSchedule.Window from, @Nullable TideSchedule.Window to) {
            super(server);
            this.from = from;
            this.to = to;
        }

        /** The Tide that was running, or null if it was Calm. */
        @Nullable
        public TideSchedule.Window getFrom() {
            return from;
        }

        /** The Tide running now, or null for Calm. */
        @Nullable
        public TideSchedule.Window getTo() {
            return to;
        }
    }
}
