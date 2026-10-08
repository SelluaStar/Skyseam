package com.selluastar.skyseam.seam;

import java.util.Arrays;
import java.util.Random;
import java.util.UUID;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The outline of one Seam: a ragged, voxel-edged fissure (spec section 6, reference image 1), made of one-block
 * cells in the Seam's own plane. It is generated from the Seam's id, so the server and every client build exactly the
 * same shape without sending it.
 *
 * <p>Plane coordinates: {@code u} runs across the Seam (to its right when looking along its facing) and {@code v} runs
 * up, both in blocks, with (0, 0) at the Seam's position. Cell {@code (i, j)} covers {@code u} from
 * {@code i - cols / 2} to one more, and {@code v} likewise from {@code j - rows / 2}. Row 0 is the bottom.
 *
 * <p>Each cell has an opening threshold from 0 to 1. A cell is open once the reveal's openness
 * ({@link SeamTimeline#openness}) reaches its threshold: cells near the crack's spine open first, the outer ones as the
 * threads snap, and a few spurs branch outward in steps.
 */
public final class SeamShape {
    public static final int THREADS = SeamTimeline.THREAD_SNAPS.length;
    private static final float NEVER = Float.POSITIVE_INFINITY;

    public final int cols;
    public final int rows;
    private final float[] openAt;
    private final float[] spine;
    private final int[] threadRows;
    private final boolean dustOnRight;

    private SeamShape(int cols, int rows, float[] openAt, float[] spine, int[] threadRows, boolean dustOnRight) {
        this.cols = cols;
        this.rows = rows;
        this.openAt = openAt;
        this.spine = spine;
        this.threadRows = threadRows;
        this.dustOnRight = dustOnRight;
    }

    public static long seed(UUID id) {
        return id.getMostSignificantBits() ^ id.getLeastSignificantBits();
    }

    public static SeamShape create(long seed, float width, float height) {
        Random random = new Random(seed);
        int cols = Mth.clamp(Math.round(width), 4, 128);
        int rows = Mth.clamp(Math.round(height), 4, 128);
        float halfCols = cols / 2f;

        // The spine: a stepped random walk either side of the middle column.
        float[] spine = new float[rows];
        int maxShift = Math.max(1, Math.round(cols * 0.08f));
        walkSpine(random, spine, rows / 2, 1, maxShift);
        walkSpine(random, spine, rows / 2, -1, maxShift);

        // Half-widths per row: a lens, cut into bands of 1 to 3 rows with their own width, so the sides step.
        float[] left = new float[rows];
        float[] right = new float[rows];
        for (int j = 0; j < rows; ) {
            int band = 1 + random.nextInt(3);
            float leftFactor = 0.7f + 0.3f * random.nextFloat();
            float rightFactor = 0.7f + 0.3f * random.nextFloat();
            for (int k = j; k < Math.min(rows, j + band); k++) {
                float t = (k + 0.5f) / rows;
                float lens = t < 0.06f || t > 0.94f ? 0 : (float) Math.pow(Math.sin(Math.PI * t), 0.75);
                left[k] = halfCols * lens * leftFactor;
                right[k] = halfCols * lens * rightFactor;
            }
            j += band;
        }

        float[] openAt = new float[cols * rows];
        Arrays.fill(openAt, NEVER);
        for (int j = 0; j < rows; j++) {
            for (int i = 0; i < cols; i++) {
                float d = i + 0.5f - halfCols - spine[j];
                float w = d < 0 ? left[j] : right[j];
                if (w < 0.5f) {
                    continue;
                }
                float n = Math.abs(d) / w;
                if (n <= 1) {
                    openAt[i + j * cols] = n * (0.82f + 0.18f * random.nextFloat());
                }
            }
        }

        // Spurs: short branches that crack outward from the sides in steps, while the crack opens.
        int spurs = 2 + random.nextInt(3);
        for (int s = 0; s < spurs; s++) {
            int row = Mth.floor(rows * (0.2f + 0.6f * random.nextFloat()));
            boolean toRight = random.nextBoolean();
            float w = toRight ? right[row] : left[row];
            if (w < 1) {
                continue;
            }
            int startCol = Mth.floor(halfCols + spine[row] + (toRight ? w - 0.5f : -w + 0.5f));
            int length = 2 + random.nextInt(Math.max(2, Math.round(cols * 0.12f)));
            int thickness = 1 + random.nextInt(2);
            int r = row;
            for (int k = 0; k < length; k++) {
                if (k == length / 2 && random.nextBoolean()) {
                    r += random.nextBoolean() ? 1 : -1;
                }
                int c = startCol + (toRight ? k : -k);
                for (int t = 0; t < thickness; t++) {
                    open(openAt, cols, rows, c, r + t, 0.3f + 0.45f * k / length);
                }
            }
        }

        // Five threads stitched across the crack, spread from high to low. They snap top first.
        int[] threadRows = new int[THREADS];
        for (int k = 0; k < THREADS; k++) {
            float t = 0.74f - k * (0.46f / (THREADS - 1));
            threadRows[k] = Mth.clamp(Math.round(t * rows - 0.5f), 0, rows - 1);
        }
        return new SeamShape(cols, rows, openAt, spine, threadRows, random.nextBoolean());
    }

    private static void walkSpine(Random random, float[] spine, int from, int direction, int maxShift) {
        int shift = 0;
        for (int j = from + direction; j >= 0 && j < spine.length; j += direction) {
            if (random.nextFloat() < 0.3f) {
                shift = Mth.clamp(shift + (random.nextBoolean() ? 1 : -1), -maxShift, maxShift);
            }
            spine[j] = shift;
        }
    }

    private static void open(float[] openAt, int cols, int rows, int i, int j, float threshold) {
        if (i >= 0 && i < cols && j >= 0 && j < rows) {
            openAt[i + j * cols] = Math.min(openAt[i + j * cols], threshold);
        }
    }

    /** True if cell (i, j) is open at this openness. Cells outside the grid are never open. */
    public boolean isOpen(int i, int j, float openness) {
        return i >= 0 && i < cols && j >= 0 && j < rows && openAt[i + j * cols] <= openness;
    }

    /** True if the cell is open at full openness, i.e. part of the finished opening. */
    public boolean isPartOfOpening(int i, int j) {
        return isOpen(i, j, 1);
    }

    /** The cell's opening threshold, or infinity if it never opens. */
    public float openAt(int i, int j) {
        return openAt[i + j * cols];
    }

    public float cellU(int i) {
        return i - cols / 2f;
    }

    public float cellV(int j) {
        return j - rows / 2f;
    }

    /** Where the spine (and the hairline) crosses row {@code j}, as a {@code u}. */
    public float spineU(int j) {
        return spine[Mth.clamp(j, 0, rows - 1)];
    }

    /** The row thread {@code k} crosses. Thread 0 is the top one and snaps first. */
    public int threadRow(int k) {
        return threadRows[k];
    }

    /** The middle of the row, as a {@code v}. */
    public float rowCentreV(int j) {
        return cellV(j) + 0.5f;
    }

    /** True if the dust streams off the right edge, false for the left. */
    public boolean dustOnRight() {
        return dustOnRight;
    }

    /**
     * The left and right {@code u} of the open run through the spine in row {@code j}: where a thread stitched
     * across that row is anchored. With nothing open yet it is the hairline's width.
     */
    public float[] openSpan(int j, float openness) {
        int spineCol = Mth.floor(spine[j] + cols / 2f);
        int start = isOpen(spineCol, j, openness) ? spineCol : isOpen(spineCol - 1, j, openness) ? spineCol - 1 : -1;
        if (start < 0) {
            float u = spine[j];
            return new float[] {u - 0.25f, u + 0.25f};
        }
        int lo = start;
        int hi = start;
        while (isOpen(lo - 1, j, openness)) {
            lo--;
        }
        while (isOpen(hi + 1, j, openness)) {
            hi++;
        }
        return new float[] {cellU(lo), cellU(hi) + 1};
    }

    /** How many cells are open at this openness. */
    public int openCount(float openness) {
        int count = 0;
        for (float threshold : openAt) {
            if (threshold <= openness) {
                count++;
            }
        }
        return count;
    }

    /** A point in the Seam's plane as a world position, for a Seam at {@code centre} facing {@code yawDegrees}. */
    public static Vec3 toWorld(Vec3 centre, float yawDegrees, double u, double v) {
        float yaw = yawDegrees * Mth.DEG_TO_RAD;
        return centre.add(u * Mth.cos(yaw), v, u * Mth.sin(yaw));
    }

    /** The unit vector the Seam faces (its plane's normal), for {@code yawDegrees}. */
    public static Vec3 normal(float yawDegrees) {
        float yaw = yawDegrees * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw));
    }
}
