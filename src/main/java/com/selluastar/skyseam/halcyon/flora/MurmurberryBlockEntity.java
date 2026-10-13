package com.selluastar.skyseam.halcyon.flora;

import com.selluastar.skyseam.registry.SkyseamBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Murmurberry's block entity (docs/DECISIONS.md K106). On both sides it keeps the berry in its level's
 * {@link MurmurberryIndex} while it is loaded. On the client it carries the GeckoLib animations (a slow sway,
 * {@code idle}, and a swell, {@code pulse}) and the flashes: the ripples it was sent ({@link #hear}) wait here until
 * their light reaches it, then it lights up ({@link #glow}) and swells.
 *
 * <p>A flash rises in a tick and a half, holds, and fades out by the {@link #FLASH_TICKS}th tick. Once lit it hears
 * nothing more for {@link #REST_TICKS} ticks, as a sculk sensor rests: so a creature walking by sends rings of light out
 * about once a second rather than a flicker on every step.
 */
public class MurmurberryBlockEntity extends BlockEntity implements GeoBlockEntity {
    public static final String PULSE = "pulse";
    /** How long a flash lasts, in ticks: a split second. */
    public static final int FLASH_TICKS = 12;
    /** Ticks from one flash to the earliest the next can start. */
    public static final int REST_TICKS = 20;
    private static final float RISE = 1.5f;
    private static final float HOLD = 4f;
    /** How many ripples it can wait on at once; any more are dropped. */
    private static final int QUEUE = 16;
    private static final RawAnimation IDLE_ANIMATION = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation PULSE_ANIMATION = RawAnimation.begin().thenPlay(PULSE);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Client only: when the ripples it was sent reach it, in game ticks, soonest first. */
    private final long[] heard = new long[QUEUE];
    private int waiting;
    /** Client only: the game tick its last flash began. */
    private long litAt = Long.MIN_VALUE / 2;

    public MurmurberryBlockEntity(BlockPos pos, BlockState state) {
        super(SkyseamBlockEntities.MURMURBERRY.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) {
            MurmurberryIndex index = MurmurberryIndex.of(level, true);
            if (index != null) {
                index.add(worldPosition);
            }
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null) {
            MurmurberryIndex index = MurmurberryIndex.of(level, false);
            if (index != null) {
                index.remove(worldPosition);
            }
        }
    }

    /** Client only: a ripple's light reaches this berry at game tick {@code at}. */
    public void hear(long at) {
        if (waiting == QUEUE) {
            return;
        }
        int k = waiting++;
        while (k > 0 && heard[k - 1] > at) {
            heard[k] = heard[k - 1];
            k--;
        }
        heard[k] = at;
    }

    /** Client only: how brightly it is lit, 0 (off) to 1, {@code lag} ticks behind the cluster's first berry. */
    public float glow(float partialTick, float lag) {
        if (level == null) {
            return 0;
        }
        float t = (float) (level.getGameTime() - litAt) + partialTick - lag;
        if (t <= 0 || t >= FLASH_TICKS) {
            return 0;
        }
        if (t < RISE) {
            return t / RISE;
        }
        if (t < HOLD) {
            return 1;
        }
        float out = (t - HOLD) / (FLASH_TICKS - HOLD);
        // Eased out, so it lingers a little before it dies.
        return 1 - out * out * (3 - 2 * out);
    }

    /** Client only: true while any of it may still be lit (its last berry trails the first by under three ticks). */
    public boolean lit(float partialTick) {
        return level != null && level.getGameTime() - litAt + partialTick < FLASH_TICKS + 3;
    }

    static void clientTick(Level level, BlockPos pos, BlockState state, MurmurberryBlockEntity berry) {
        long now = level.getGameTime();
        int done = 0;
        while (done < berry.waiting && berry.heard[done] <= now) {
            if (now - berry.litAt >= REST_TICKS) {
                berry.litAt = now;
                berry.triggerAnim("main", PULSE);
            }
            done++;
        }
        if (done > 0) {
            System.arraycopy(berry.heard, done, berry.heard, 0, berry.waiting - done);
            berry.waiting -= done;
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 2, state -> state.setAndContinue(IDLE_ANIMATION))
                .triggerableAnim(PULSE, PULSE_ANIMATION));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
