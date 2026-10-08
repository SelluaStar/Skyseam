package com.selluastar.skyseam.seam;

import net.minecraft.util.Mth;

/**
 * The Seam's reveal, beat by beat (spec section 6), in ticks. The server plays the sounds and the client draws the
 * look from the same numbers, so they cannot drift apart.
 *
 * <p>Everything visual is a function of one "reveal age": ticks since the Seam started opening, stopping at
 * {@link #STABLE_AT}. Mending plays the reveal backwards from wherever it had got to ({@link #mendingAge}), so the
 * threads re-stitch bottom to top and the crack narrows, exactly undoing the opening.
 *
 * <pre>
 * beat           ticks      seconds
 * 2 hairline      0 - 30    0 - 1.5
 * 3 crack        30 - 70    1.5 - 3.5   (opens to {@link #CRACK_OPENNESS} in {@link #CRACK_STEPS} steps)
 * 4 threads snap  62 - 94   3 - 5       (each of 5 snaps widens the opening by a notch)
 * 5 open        100 - 120   5 - 6       (interior fades in, camera shake)
 * 6 stable      120 +       loop        (hum, shimmer, ring pulse every 6 s, pull)
 * 8 mending     reverse playback at 1.5x speed, then a scar
 * </pre>
 */
public final class SeamTimeline {
    public static final int CRACK_AT = 30;
    public static final int CRACK_END = 70;
    public static final int CRACK_STEPS = 8;
    public static final float CRACK_OPENNESS = 0.55f;
    /** The tick each thread snaps, top thread first. */
    public static final int[] THREAD_SNAPS = {62, 70, 78, 86, 94};
    public static final float NOTCH = (1 - CRACK_OPENNESS) / THREAD_SNAPS.length;
    public static final int OPEN_AT = 100;
    public static final int STABLE_AT = 120;
    /** Stable beat: a ring pulse every 6 seconds. */
    public static final int RING_PERIOD = 120;
    /** Mending replays the reveal backwards this much faster than it opened. */
    public static final float MEND_SPEED = 1.5f;
    /** Where in the mend the closing chime plays, as a fraction of the mend. */
    public static final float MEND_CHIME_AT = 0.75f;

    private SeamTimeline() {}

    /** How many of the five threads have snapped at this reveal age. */
    public static int threadsSnapped(float age) {
        int snapped = 0;
        for (int tick : THREAD_SNAPS) {
            if (age >= tick) {
                snapped++;
            }
        }
        return snapped;
    }

    /**
     * How far the crack has opened, 0 (only the hairline) to 1 (fully open). The crack opens in steps, and each snapped
     * thread widens it by one notch. A cell of the {@link SeamShape} is open when its opening threshold is at most this.
     */
    public static float openness(float age) {
        if (age < CRACK_AT) {
            return 0;
        }
        float crack = Math.min(1, (age - CRACK_AT) / (CRACK_END - CRACK_AT));
        float stepped = Math.min(1, Mth.ceil(crack * CRACK_STEPS + 0.001f) / (float) CRACK_STEPS);
        return Math.min(1, CRACK_OPENNESS * stepped + NOTCH * threadsSnapped(age));
    }

    /** How long the hairline is, 0 to 1 of its full length. It grows from the middle during the first 1.2 seconds. */
    public static float hairline(float age) {
        return Mth.clamp(age / 24f, 0, 1);
    }

    /** How strongly the prismatic interior shows, 0 to 1: half in as soon as it cracks, fully in during the open beat. */
    public static float interior(float age) {
        if (age < CRACK_AT) {
            return 0;
        }
        float hint = 0.55f * Mth.clamp((age - CRACK_AT) / 20f, 0, 1);
        float open = Mth.clamp((age - OPEN_AT) / (float) (STABLE_AT - OPEN_AT), 0, 1);
        return hint + (1 - hint) * smooth(open);
    }

    /** Ticks the mend takes when it starts from {@code fromAge}. A barely opened Seam mends quickly. */
    public static int mendTicks(float fromAge) {
        return Math.max(10, Mth.ceil(Math.min(fromAge, STABLE_AT) / MEND_SPEED));
    }

    /** The reveal age shown {@code ticksIntoMend} ticks into a mend that started at reveal age {@code fromAge}. */
    public static float mendingAge(float fromAge, float ticksIntoMend) {
        float from = Math.min(fromAge, STABLE_AT);
        return Math.max(0, from * (1 - ticksIntoMend / mendTicks(from)));
    }

    private static float smooth(float t) {
        return t * t * (3 - 2 * t);
    }
}
