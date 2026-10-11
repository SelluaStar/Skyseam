package com.selluastar.skyseam.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.selluastar.skyseam.halcyon.LanternSun;
import com.selluastar.skyseam.world.HalcyonLayout;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;

/**
 * The Halcyon keeps the Lantern-Sun's day, not the Overworld's (docs/DECISIONS.md K82). Every level but the Halcyon
 * shares the Overworld's day time, so in the Halcyon this answers {@code getDayTime} from the Lantern-Sun's clock
 * instead ({@link LanternSun#dayTime}). Everything that reads the day follows: the sun's angle, how dark the sky is,
 * {@code isDay}, and shader packs' sun and shadows. {@code dayTime()} normally reads the level data directly, so it is
 * sent through {@code getDayTime} here too.
 */
@Mixin(Level.class)
public abstract class LevelTimeMixin implements LevelAccessor {
    @Shadow
    public abstract ResourceKey<Level> dimension();

    @Shadow
    public abstract long getGameTime();

    @Shadow
    public abstract boolean isClientSide();

    @Shadow
    public abstract long getDayTime();

    @Inject(method = "getDayTime", at = @At("HEAD"), cancellable = true)
    private void skyseam$lanternSunDayTime(CallbackInfoReturnable<Long> callback) {
        if (dimension() == HalcyonLayout.LEVEL) {
            callback.setReturnValue(LanternSun.dayTime(getGameTime(), LanternSun.offset(isClientSide())));
        }
    }

    @Override
    public long dayTime() {
        return getDayTime();
    }
}
