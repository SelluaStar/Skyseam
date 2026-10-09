package com.selluastar.skyseam.seam;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.jetbrains.annotations.Nullable;

import com.selluastar.skyseam.config.SkyseamConfig;
import com.selluastar.skyseam.external.SableBridge;
import com.selluastar.skyseam.external.Ship;
import com.selluastar.skyseam.registry.SkyseamSounds;
import com.selluastar.skyseam.transfer.CrossingHolds;

import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Seam: a crack in the sky that hangs in open air (spec section 6). The server decides everything (spec section
 * 1, rule 10): when it opens, stays open, mends and fades, which sounds play when, and the pull on nearby ships. The
 * client only draws it, from the synced state, start time and size ({@code client/seam/SeamRenderer}).
 *
 * <p>Open one with {@link Seams#open}. It keeps itself open while someone is within the hold radius, mends
 * {@code mend_delay_seconds} after the last one leaves or after {@code max_open_seconds} regardless, then leaves a scar
 * for {@code scar_seconds} during which no Seam opens near it. The chunks around it stay loaded until it has mended.
 *
 * <p>Until the Harmonic Aperture exists (M2), "someone" means a player. M2 narrows it to a piloted ship carrying an
 * Aperture, as the spec's trigger rules say.
 */
public class SeamEntity extends Entity {
    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(SeamEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Long> DATA_STATE_START = SynchedEntityData.defineId(SeamEntity.class, EntityDataSerializers.LONG);
    private static final EntityDataAccessor<Float> DATA_WIDTH = SynchedEntityData.defineId(SeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_HEIGHT = SynchedEntityData.defineId(SeamEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_MEND_FROM = SynchedEntityData.defineId(SeamEntity.class, EntityDataSerializers.FLOAT);

    /** Keeps the chunks around an open Seam loaded (spec section 6, "Edge cases"). Keyed by the entity id. */
    private static final TicketType<Integer> TICKET = TicketType.create("skyseam_seam", Integer::compare);
    /** How often the keep-open check looks for people nearby. */
    private static final int KEEP_CHECK_TICKS = 10;

    // Server only.
    private final List<BooleanSupplier> keepers = new ArrayList<>();
    private long openedAt;
    private long lastKept;
    private int cuesPlayedTo = -1;
    @Nullable
    private ChunkPos ticketCentre;
    private int ticketDistance;

    // Both sides: built from the id and size the first time it is needed.
    @Nullable
    private SeamShape shape;

    public SeamEntity(EntityType<? extends SeamEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /** Server: a new Seam, before it is added to the level. */
    void setUp(Vec3 centre, float yaw, float width, float height, long now) {
        setPos(centre);
        setYRot(yaw);
        this.yRotO = yaw;
        entityData.set(DATA_WIDTH, width);
        entityData.set(DATA_HEIGHT, height);
        entityData.set(DATA_STATE, SeamState.OPENING.ordinal());
        entityData.set(DATA_STATE_START, now);
        openedAt = now;
        lastKept = now;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_STATE, SeamState.OPENING.ordinal());
        builder.define(DATA_STATE_START, 0L);
        builder.define(DATA_WIDTH, 16f);
        builder.define(DATA_HEIGHT, 16f);
        builder.define(DATA_MEND_FROM, (float) SeamTimeline.STABLE_AT);
    }

    // ---- State, shared with the client ---------------------------------------------------------------------------

    public SeamState state() {
        return SeamState.byId(entityData.get(DATA_STATE));
    }

    /** The game time the current state began. */
    public long stateStart() {
        return entityData.get(DATA_STATE_START);
    }

    public float seamWidth() {
        return entityData.get(DATA_WIDTH);
    }

    public float seamHeight() {
        return entityData.get(DATA_HEIGHT);
    }

    /** The reveal age a mend started from. */
    public float mendFrom() {
        return entityData.get(DATA_MEND_FROM);
    }

    public SeamShape shape() {
        if (shape == null || shape.cols != Mth.clamp(Math.round(seamWidth()), 4, 128)
                || shape.rows != Mth.clamp(Math.round(seamHeight()), 4, 128)) {
            shape = SeamShape.create(SeamShape.seed(getUUID()), seamWidth(), seamHeight());
        }
        return shape;
    }

    /** Ticks since the current state began, with the partial tick for smooth drawing. */
    public float ticksInState(float partialTick) {
        return Math.max(0, level().getGameTime() - stateStart() + partialTick);
    }

    /** The reveal age to draw: how far the opening has got, going backwards while mending. See {@link SeamTimeline}. */
    public float revealAge(float partialTick) {
        float t = ticksInState(partialTick);
        return switch (state()) {
            case OPENING -> Math.min(t, SeamTimeline.STABLE_AT);
            case OPEN -> SeamTimeline.STABLE_AT;
            case MENDING -> SeamTimeline.mendingAge(mendFrom(), t);
            case SCAR -> 0;
        };
    }

    private void setState(SeamState state, long now) {
        entityData.set(DATA_STATE, state.ordinal());
        entityData.set(DATA_STATE_START, now);
        cuesPlayedTo = -1;
    }

    // ---- Server behaviour ----------------------------------------------------------------------------------------

    @Override
    public void tick() {
        if (level() instanceof ServerLevel level) {
            serverTick(level);
        }
    }

    private void serverTick(ServerLevel level) {
        long now = level.getGameTime();
        int age = (int) (now - stateStart());
        switch (state()) {
            case OPENING -> {
                holdChunks(level);
                playRevealCues(level, age);
                keepOpenCheck(level, now);
                if (state() == SeamState.OPENING && age >= SeamTimeline.STABLE_AT) {
                    setState(SeamState.OPEN, now);
                }
            }
            case OPEN -> {
                holdChunks(level);
                if (age > 0 && age % SeamTimeline.RING_PERIOD == 0) {
                    play(level, SkyseamSounds.SEAM_RING_PULSE, position());
                }
                pullShips(level);
                keepOpenCheck(level, now);
            }
            case MENDING -> {
                int total = SeamTimeline.mendTicks(mendFrom());
                if (passed(0, age)) {
                    play(level, SkyseamSounds.SEAM_CLOSE, position());
                }
                if (passed(Math.round(total * SeamTimeline.MEND_CHIME_AT), age)) {
                    play(level, SkyseamSounds.SEAM_MEND, position());
                }
                cuesPlayedTo = age;
                if (age >= total) {
                    setState(SeamState.SCAR, now);
                    releaseChunks(level);
                    Seams.recordMended(level, this);
                }
            }
            case SCAR -> {
                if (age >= SkyseamConfig.SCAR_SECONDS.get() * 20) {
                    discard();
                }
            }
        }
    }

    /** Beats 2 to 4: the hairline tink, the crack, and a pluck as each thread snaps. */
    private void playRevealCues(ServerLevel level, int age) {
        if (passed(0, age)) {
            play(level, SkyseamSounds.SEAM_HAIRLINE, position());
        }
        if (passed(SeamTimeline.CRACK_AT, age)) {
            play(level, SkyseamSounds.SEAM_CRACK, position());
        }
        for (int k = 0; k < SeamShape.THREADS; k++) {
            if (passed(SeamTimeline.THREAD_SNAPS[k], age)) {
                play(level, SkyseamSounds.SEAM_THREAD_SNAPS.get(k), threadPosition(k, age));
            }
        }
        cuesPlayedTo = age;
    }

    /** True if {@code cueTick} is reached between the last tick handled and {@code age}. */
    private boolean passed(int cueTick, int age) {
        return cueTick > cuesPlayedTo && cueTick <= age;
    }

    /** The world position of thread {@code k}'s middle at this reveal age. */
    public Vec3 threadPosition(int k, float age) {
        SeamShape shape = shape();
        int row = shape.threadRow(k);
        float[] span = shape.openSpan(row, SeamTimeline.openness(age));
        return SeamShape.toWorld(position(), getYRot(), (span[0] + span[1]) / 2, shape.rowCentreV(row));
    }

    private static void play(ServerLevel level, Holder<SoundEvent> sound, Vec3 at) {
        level.playSound(null, at.x, at.y, at.z, sound.value(), SoundSource.AMBIENT, 1, 1);
    }

    /** Mends the Seam if nobody has been near it for the mend delay, or it has been open too long. */
    private void keepOpenCheck(ServerLevel level, long now) {
        if (now % KEEP_CHECK_TICKS == 0 && isSomeoneNear(level)) {
            lastKept = now;
        }
        if (now - lastKept >= SkyseamConfig.MEND_DELAY_SECONDS.get() * 20L || now - openedAt >= SkyseamConfig.MAX_OPEN_SECONDS.get() * 20L) {
            mend();
        }
    }

    private boolean isSomeoneNear(ServerLevel level) {
        double radius = SkyseamConfig.HOLD_RADIUS.get();
        return level.players().stream().anyMatch(player -> !player.isSpectator() && player.distanceToSqr(this) <= radius * radius)
                || keepers.stream().anyMatch(BooleanSupplier::getAsBoolean);
    }

    /**
     * Keeps the Seam open while {@code keeper} says so, as a player within the hold radius does: it mends
     * {@code mend_delay_seconds} after every keeper and player is gone, and after {@code max_open_seconds} regardless.
     * M2's Aperture uses this for the ship that opened the Seam. GameTests use it in place of a player.
     */
    public void keepOpenWhile(BooleanSupplier keeper) {
        keepers.add(keeper);
    }

    /** Beat 6: a gentle pull on ships within the pull radius, fading to nothing at its edge. */
    private void pullShips(ServerLevel level) {
        double radius = SkyseamConfig.PULL_RADIUS.get();
        double strength = SkyseamConfig.PULL_STRENGTH.get();
        if (radius <= 0 || strength <= 0) {
            return;
        }
        for (Ship ship : SableBridge.shipsWithin(level, position(), radius)) {
            if (CrossingHolds.isHeld(ship.id())) {
                continue;
            }
            Vec3 toSeam = position().subtract(SableBridge.position(ship));
            double distance = toSeam.length();
            if (distance < 1) {
                continue;
            }
            // Velocity is in blocks per second, so one tick of the pull adds a twentieth of the acceleration.
            double acceleration = strength * (1 - distance / radius);
            SableBridge.addVelocity(ship, toSeam.scale(acceleration / distance / 20));
        }
    }

    /** Starts mending (beat 8). Does nothing if it is already mending or mended. */
    public void mend() {
        if (level().isClientSide || !state().isOpening()) {
            return;
        }
        long now = level().getGameTime();
        float from = state() == SeamState.OPEN ? SeamTimeline.STABLE_AT : Math.min(now - stateStart(), SeamTimeline.STABLE_AT);
        entityData.set(DATA_MEND_FROM, from);
        setState(SeamState.MENDING, now);
    }

    /**
     * Test and debug use: moves the Seam's clock on by {@code ticks}, as if that much time had passed in its current
     * state. Cues in between are skipped.
     */
    public void skipAhead(int ticks) {
        entityData.set(DATA_STATE_START, stateStart() - ticks);
        openedAt -= ticks;
        cuesPlayedTo = (int) (level().getGameTime() - stateStart());
    }

    private void holdChunks(ServerLevel level) {
        if (ticketCentre != null) {
            return;
        }
        ticketCentre = chunkPosition();
        float half = Math.max(seamWidth(), seamHeight()) / 2;
        // A region ticket of distance d keeps chunks within d - 2 of the centre entity-ticking.
        ticketDistance = Mth.ceil(half / 16) + 2;
        level.getChunkSource().addRegionTicket(TICKET, ticketCentre, ticketDistance, getId());
    }

    private void releaseChunks(ServerLevel level) {
        if (ticketCentre != null) {
            level.getChunkSource().removeRegionTicket(TICKET, ticketCentre, ticketDistance, getId());
            ticketCentre = null;
        }
    }

    /** True while the chunks around the Seam are held loaded. */
    public boolean holdsChunks() {
        return ticketCentre != null;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel level) {
            releaseChunks(level);
            // Removed by a command or a mod while still open: treat it as mended on the spot.
            if (reason.shouldDestroy() && state() != SeamState.SCAR) {
                Seams.recordMended(level, this);
            }
        }
        super.remove(reason);
    }

    // ---- A Seam is not a thing you can touch ---------------------------------------------------------------------

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return true;
    }

    @Override
    public boolean canUsePortal(boolean allowPassengers) {
        return false;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    // ---- Drawing range ---------------------------------------------------------------------------------------------

    /** Everyone within 256 blocks sees the animation (spec section 6). */
    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 256 * 256;
    }

    /** The box the renderer culls against: the opening, the god-rays and the ring of parting clouds. */
    @Override
    public AABB getBoundingBoxForCulling() {
        double reach = Math.max(seamWidth(), seamHeight()) * 2 + 4;
        double up = seamHeight() / 2 + 4;
        return new AABB(getX() - reach, getY() - up, getZ() - reach, getX() + reach, getY() + up, getZ() + reach);
    }

    // ---- Not saved: open Seams live in SeamSavedData and are mended when the world loads --------------------------

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    /** Orders Seams nearest first. */
    public static Comparator<SeamEntity> nearestTo(Vec3 pos) {
        return Comparator.comparingDouble(seam -> seam.distanceToSqr(pos));
    }
}
