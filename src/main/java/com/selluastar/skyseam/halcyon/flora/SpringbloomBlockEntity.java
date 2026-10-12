package com.selluastar.skyseam.halcyon.flora;

import com.selluastar.skyseam.registry.SkyseamBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Springbloom's block entity (docs/DECISIONS.md K98): only there to carry its GeckoLib animations. It sways
 * ({@code idle}) until something lands on it ({@code squish}) or jumps off it ({@code launch}); both are played on
 * each client when the block's event reaches it ({@link SpringbloomBlock#triggerEvent}).
 */
public class SpringbloomBlockEntity extends BlockEntity implements GeoBlockEntity {
    public static final String SQUISH = "squish";
    public static final String LAUNCH = "launch";
    private static final RawAnimation IDLE_ANIMATION = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation SQUISH_ANIMATION = RawAnimation.begin().thenPlay(SQUISH);
    private static final RawAnimation LAUNCH_ANIMATION = RawAnimation.begin().thenPlay(LAUNCH);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public SpringbloomBlockEntity(BlockPos pos, BlockState state) {
        super(SkyseamBlockEntities.SPRINGBLOOM.get(), pos, state);
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
