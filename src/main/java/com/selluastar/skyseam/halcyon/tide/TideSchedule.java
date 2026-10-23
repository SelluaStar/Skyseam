package com.selluastar.skyseam.halcyon.tide;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.selluastar.skyseam.world.halcyon.plan.Noise;

import net.minecraft.util.Mth;

/**
 * When the Tides run (docs/DECISIONS.md K111), from the world's seed and the game time alone, so nothing is saved but a
 * Tide forced by command, and every restart agrees.
 *
 * <p>Time is cut into slots ({@link Settings#slot}, 25 minutes); each slot holds one Tide, 6 to 10 minutes long, starting
 * somewhere in it, so one comes every 25 minutes or so, with Calm between. Which Tide is up to the slot's hash: Stillness
 * is rare ({@link Settings#stillness}), the other three alike. A Tide set by command takes the place of any that would
 * have run while it does; a Calm set by command stops one.
 *
 * <p>Pure: the server works out its forecast from this, sends it to the clients, and both use it alike.
 */
public final class TideSchedule {
    /** A Tide takes this long to come in at its start and to die away at its end, in ticks. */
    public static final int RAMP = 600;

    private TideSchedule() {}

    /** How often and how long: the slot each Tide falls in, its shortest and longest, and how often it's Stillness. */
    public record Settings(long slot, long shortest, long longest, double stillness) {
        public static final Settings DEFAULT = new Settings(25 * 1200, 6 * 1200, 10 * 1200, 0.1);
    }

    /** One Tide, from game time {@code start} to {@code end}. */
    public record Window(Tide tide, long start, long end) {
        public boolean covers(double time) {
            return time >= start && time < end;
        }

        public boolean overlaps(Window other) {
            return start < other.end && other.start < end;
        }

        /** How strongly it is running at {@code time}: coming in over its first {@link #RAMP} ticks, dying away over its last. */
        public double strength(double time) {
            if (!covers(time)) {
                return 0;
            }
            return smooth((time - start) / RAMP) * smooth((end - time) / RAMP);
        }
    }

    /** The Tide running now (or null, in Calm), and the next to come. */
    public record Forecast(@Nullable Window current, @Nullable Window next) {
        public static final Forecast CALM = new Forecast(null, null);

        /** Which Tide is running at {@code time} (Calm if none). */
        public Tide tide(double time) {
            return current != null && current.covers(time) ? current.tide() : next != null && next.covers(time) ? next.tide() : Tide.CALM;
        }

        /** How strongly {@code tide} is running at {@code time}, 0 to 1. */
        public double strength(Tide tide, double time) {
            double s = 0;
            for (Window window : new Window[] {current, next}) {
                if (window != null && window.tide() == tide) {
                    s = Math.max(s, window.strength(time));
                }
            }
            return s;
        }
    }

    /** The Tide of time slot {@code slot}: its kind, and when it starts and ends. */
    public static Window natural(long seed, Settings settings, long slot) {
        double length = Mth.lerp(unit(seed, slot, 1), settings.shortest(), settings.longest());
        long start = slot * settings.slot() + (long) (unit(seed, slot, 2) * Math.max(0, settings.slot() - length));
        Tide tide;
        if (unit(seed, slot, 3) < settings.stillness()) {
            tide = Tide.STILLNESS;
        } else {
            tide = new Tide[] {Tide.PRISMFALL, Tide.GALE, Tide.BLOOM}[(int) Math.min(2, unit(seed, slot, 4) * 3)];
        }
        return new Window(tide, start, start + (long) length);
    }

    /**
     * The forecast at game time {@code time}: the natural Tides round it, less any that a forced one (from a command, or
     * null) overlaps, plus the forced one unless it is a forced Calm.
     */
    public static Forecast forecast(long seed, Settings settings, @Nullable Window forced, long time) {
        List<Window> windows = new ArrayList<>();
        long slot = Math.floorDiv(time, settings.slot());
        for (long s = slot - 1; s <= slot + 3; s++) {
            Window natural = natural(seed, settings, s);
            if (forced == null || !natural.overlaps(forced)) {
                windows.add(natural);
            }
        }
        if (forced != null && forced.tide() != Tide.CALM) {
            windows.add(forced);
        }
        windows.sort(Comparator.comparingLong(Window::start));
        Window current = null;
        Window next = null;
        for (Window window : windows) {
            if (window.covers(time)) {
                current = window;
            } else if (window.start() > time && next == null) {
                next = window;
            }
        }
        return new Forecast(current, next);
    }

    private static double unit(long seed, long slot, int salt) {
        return (Noise.mix(seed, slot, salt, 0, 0x71DE) >>> 11) * 0x1.0p-53;
    }

    private static double smooth(double t) {
        double x = Mth.clamp(t, 0, 1);
        return x * x * (3 - 2 * x);
    }
}
