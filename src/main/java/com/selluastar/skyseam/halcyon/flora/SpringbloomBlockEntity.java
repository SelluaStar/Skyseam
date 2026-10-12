package com.selluastar.skyseam.halcyon.flora;

import com.selluastar.skyseam.registry.SkyseamBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Springbloom's block entity (docs/DECISIONS.md K98): only there to carry its GeckoLib animations. It sways
 * ({@code idle}) until something lands on it ({@code squish}) or jumps off it ({@code launch}); both are played on
 * each client when the block's event reaches it ({@link SpringbloomBlock#triggerEvent}). On the client it also keeps
 * track of whether something stands on it ({@link #weight}), so the renderer can sink the bulb under it.
 */
public class SpringbloomBlockEntity extends BlockEntity implements GeoBlockEntity {
    public static final String SQUISH = "squish";
    public static final String LAUNCH = "launch";
    private static final RawAnimation IDLE_ANIMATION = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation SQUISH_ANIMATION = RawAnimation.begin().thenPlay(SQUISH);
    private static final RawAnimation LAUNCH_ANIMATION = RawAnimation.begin().thenPlay(LAUNCH);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    /** Client only: 1 while something stands on the bulb, easing back to 0 when it steps off. */
    private float weight;
    private float lastWeight;

    public SpringbloomBlockEntity(BlockPos pos, BlockState state) {
        super(SkyseamBlockEntities.SPRINGBLOOM.get(), pos, state);
    }

    /** Client only: how far the bulb is pressed down by something standing on it, 0 to 1, between ticks. */
    public float weight(float partialTick) {
        return Mth.lerp(partialTick, lastWeight, weight);
    }

    static void clientTick(Level level, BlockPos pos, BlockState state, SpringbloomBlockEntity bloom) {
        // Anything whose feet are on the bulb's top (nine pixels up) and over it.
        AABB top = new AABB(pos.getX() + 0.05, pos.getY() + 0.5, pos.getZ() + 0.05, pos.getX() + 0.95, pos.getY() + 0.7, pos.getZ() + 0.95);
        boolean pressed = !level.getEntitiesOfClass(LivingEntity.class, top, entity -> entity.getY() >= pos.getY() + 0.5 && entity.getY() < pos.getY() + 0.7)
                .isEmpty();
        bloom.lastWeight = bloom.weight;
        // It sinks fast and swells back a little slower.
        bloom.weight = pressed ? Math.min(1.0f, bloom.weight + 0.45f) : Math.max(0.0f, bloom.weight - 0.2f);
    }

    /** Plays {@link #SQUISH} or {@link #LAUNCH}, then goes back to swaying. */
    public void play(String animation) {
        triggerAnim("main", animation);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 2, state -> state.setAndContinue(IDLE_ANIMATION))
                .triggerableAnim(SQUISH, SQUISH_ANIMATION)
                .triggerableAnim(LAUNCH, LAUNCH_ANIMATION));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
