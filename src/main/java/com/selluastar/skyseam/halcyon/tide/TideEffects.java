package com.selluastar.skyseam.halcyon.tide;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.config.SkyseamConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * What each Tide does to those in it (docs/DECISIONS.md K113), round each player in the Halcyon, growing with the Tide:
 *
 * <ul>
 * <li><b>Prismfall</b>: the falling glass cuts anyone out under the open sky without a helmet, a heart about every other second
 * ({@code skyseam:prismfall}); a helmet or a roof keeps it off.</li>
 * <li><b>Gale</b>: the crosswind leans on everyone under the open sky, hard enough to slow you walking into it and carry
 * you walking with it, and bends arrows and other things thrown in flight (the air's own push is {@link Tides}').</li>
 * <li><b>Bloom</b>: crops, saplings and other growing things near players grow several times as fast, and those out
 * under the sky slowly heal.</li>
 * <li><b>Stillness</b>: the air hangs still and holds you up: gravity eases to a little over half, so falls and jumps
 * float.</li>
 * </ul>
 */
public final class TideEffects {
    public static final ResourceKey<DamageType> PRISMFALL_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Skyseam.id("prismfall"));
    private static final ResourceLocation STILL = Skyseam.id("stillness_gravity");
    /** Creatures this far from a player feel the Tide (beyond, nothing is there to see it). */
    private static final double RANGE = 64;
    /** In a Stillness, gravity at its height. */
    private static final double STILL_GRAVITY = 0.55;
    /**
     * How hard the Gale leans on someone on their feet: blocks a tick added each tick at the crosswind's usual speed (9),
     * nearly as much as a walk's push, so it slows you hard walking into it and carries you standing. Players feel it on their own side (client/
     * halcyon/tide/ClientTides), where it doesn't stutter their walking.
     */
    public static final double GROUND_PUSH = 0.08;
    /** How many growing things round each player a Bloom hurries on, each second, and how far round. */
    private static final int BLOOM_TRIES = 40;
    private static final int BLOOM_REACH = 24;
    private static final Set<UUID> LIGHTENED = new HashSet<>();

    private TideEffects() {}

    static void tick(ServerLevel level) {
        long time = level.getGameTime();
        double prismfall = Tides.strength(level, Tide.PRISMFALL);
        double gale = Tides.strength(level, Tide.GALE);
        double bloom = Tides.strength(level, Tide.BLOOM);
        double stillness = Tides.strength(level, Tide.STILLNESS);
        Set<LivingEntity> near = new HashSet<>();
        if (prismfall + gale + bloom + stillness > 0 || !LIGHTENED.isEmpty()) {
            for (ServerPlayer player : level.players()) {
                near.addAll(level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RANGE), entity -> !entity.isSpectator()));
            }
        }
        if (prismfall > 0.5 && time % 20 == 0) {
            DamageSource cut = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(PRISMFALL_DAMAGE));
            for (LivingEntity living : near) {
                if (open(level, living) && living.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && living.getRandom().nextFloat() < 0.5f * prismfall) {
                    living.hurt(cut, 2);
                }
            }
        }
        if (gale > 0) {
            Vec3 wind = Tides.crosswind(time, gale, crosswindSpeed());
            Vec3 way = wind.normalize();
            for (LivingEntity living : near) {
                if (!(living instanceof ServerPlayer) && living.onGround() && open(level, living)) {
                    living.setDeltaMovement(living.getDeltaMovement().add(way.scale(GROUND_PUSH * wind.length() / 9)));
                }
            }
            for (ServerPlayer player : level.players()) {
                for (Projectile thrown : level.getEntitiesOfClass(Projectile.class, player.getBoundingBox().inflate(RANGE), p -> !p.onGround())) {
                    thrown.setDeltaMovement(thrown.getDeltaMovement().add(wind.scale(0.0012)));
                    thrown.hurtMarked = true;
                }
            }
        }
        if (bloom > 0.3 && time % 20 == 0) {
            for (ServerPlayer player : level.players()) {
                hurryGrowth(level, player, (int) Math.round(BLOOM_TRIES * bloom));
                if (time % 300 == 0 && open(level, player)) {
                    player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, true, true, true));
                }
            }
        }
        lighten(level, near, stillness);
    }

    /** Under the open sky (nothing over their head). */
    private static boolean open(ServerLevel level, LivingEntity living) {
        BlockPos head = BlockPos.containing(living.getEyePosition());
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, head.getX(), head.getZ()) <= head.getY() + 1;
    }

    /** Gives growing things round a player extra growth: as many random ticks as a Bloom adds, on the ground round them. */
    private static void hurryGrowth(ServerLevel level, ServerPlayer player, int tries) {
        RandomSource random = level.getRandom();
        for (int k = 0; k < tries; k++) {
            int x = player.getBlockX() + random.nextInt(BLOOM_REACH * 2 + 1) - BLOOM_REACH;
            int z = player.getBlockZ() + random.nextInt(BLOOM_REACH * 2 + 1) - BLOOM_REACH;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            if (Math.abs(top - player.getBlockY()) > BLOOM_REACH) {
                continue;
            }
            // The plant on the ground, or the leaves' top block; check the two just under the top.
            for (int dy = 0; dy <= 1; dy++) {
                BlockPos pos = new BlockPos(x, top - 1 - dy, z);
                BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof BonemealableBlock && state.isRandomlyTicking()) {
                    for (int tick = 0; tick < 3; tick++) {
                        state = level.getBlockState(pos);
                        if (state.isRandomlyTicking()) {
                            state.randomTick(level, pos, random);
                        }
                    }
                    break;
                }
            }
        }
    }

    /** In a Stillness, eases gravity for those near players; gives the usual back to the rest. */
    private static void lighten(ServerLevel level, Set<LivingEntity> near, double stillness) {
        double amount = Math.round((STILL_GRAVITY - 1) * stillness * 20) / 20.0;
        for (LivingEntity living : near) {
            AttributeInstance gravity = living.getAttribute(Attributes.GRAVITY);
            if (gravity == null) {
                continue;
            }
            if (amount == 0) {
                if (gravity.getModifier(STILL) != null) {
                    gravity.removeModifier(STILL);
                }
                LIGHTENED.remove(living.getUUID());
                continue;
            }
            AttributeModifier current = gravity.getModifier(STILL);
            if (current == null || current.amount() != amount) {
                gravity.addOrUpdateTransientModifier(new AttributeModifier(STILL, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
            LIGHTENED.add(living.getUUID());
        }
        for (Iterator<UUID> ids = LIGHTENED.iterator(); ids.hasNext();) {
            UUID id = ids.next();
            Entity entity = level.getEntity(id);
            if (entity == null) {
                ids.remove();
            } else if (!(entity instanceof LivingEntity living) || !near.contains(living)) {
                release(entity);
                ids.remove();
            }
        }
    }

    /** Gives an entity back its usual gravity (on leaving the Halcyon or a Stillness's reach). */
    public static void release(Entity entity) {
        if (entity instanceof LivingEntity living && living.getAttribute(Attributes.GRAVITY) != null) {
            living.getAttribute(Attributes.GRAVITY).removeModifier(STILL);
        }
    }

    public static void clear() {
        LIGHTENED.clear();
    }

    static double crosswindSpeed() {
        return setting(SkyseamConfig.TIDES_GALE_CROSSWIND);
    }

    private static <T> T setting(ModConfigSpec.ConfigValue<T> value) {
        try {
            return value.get();
        } catch (IllegalStateException notLoaded) {
            return value.getDefault();
        }
    }
}
