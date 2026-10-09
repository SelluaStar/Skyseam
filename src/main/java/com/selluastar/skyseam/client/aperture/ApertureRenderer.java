package com.selluastar.skyseam.client.aperture;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.aperture.ApertureBlockEntity;
import com.selluastar.skyseam.external.SableBridge;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Draws the Harmonic Aperture (spec section 20): a GeckoLib model with the {@code idle}, {@code spin_up},
 * {@code charged} and {@code cooldown} animations, whose core ring and three status gems glow ({@code _glowmask}
 * texture). Each gem is lit while its rule holds: flying, altitude, site. The needle on its plate points to the
 * nearest Seam site, however the block and its ship are turned, and drifts slowly round where there is none.
 *
 * <p>Files: {@code geo/block/harmonic_aperture.geo.json}, {@code animations/block/harmonic_aperture.animation.json},
 * {@code textures/block/harmonic_aperture.png} and {@code harmonic_aperture_glowmask.png} (docs/ASSET-PATHS.md).
 */
public class ApertureRenderer extends GeoBlockRenderer<ApertureBlockEntity> {
    /** The lit gem bones, in the order of the ticks: flying, altitude, site. */
    private static final String[] LIT_GEMS = {"gem_flying_lit", "gem_altitude_lit", "gem_site_lit"};
    /** How fast the needle drifts round with no site to point to, in radians per second. */
    private static final float LOST_DRIFT = 0.6f;

    public ApertureRenderer(BlockEntityRendererProvider.Context context) {
        super(new Model());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public AABB getRenderBoundingBox(ApertureBlockEntity aperture) {
        return new AABB(aperture.getBlockPos()).inflate(0.5);
    }

    private static final class Model extends DefaultedBlockGeoModel<ApertureBlockEntity> {
        Model() {
            super(Skyseam.id("harmonic_aperture"));
        }

        @Override
        public void setCustomAnimations(ApertureBlockEntity aperture, long instanceId, AnimationState<ApertureBlockEntity> state) {
            super.setCustomAnimations(aperture, instanceId, state);
            for (int k = 0; k < LIT_GEMS.length; k++) {
                boolean lit = (aperture.flags() & ApertureScreen.TICK_FLAGS[k]) != 0;
                getBone(LIT_GEMS[k]).ifPresent(bone -> bone.setHidden(!lit));
            }
            float needle = needleAngle(aperture, (float) state.animationTick / 20);
            getBone("needle").ifPresent(bone -> bone.setRotY(needle));
        }
    }

    /**
     * The needle's turn about the block's up axis, in radians, so its tip (the model's -z) points at the site. The
     * site's direction is taken into the block's own frame: through the ship's turn if it is on one (by placing the
     * block's own axes in the world), then through the block's facing, which GeckoLib applies as a turn about y.
     */
    static float needleAngle(ApertureBlockEntity aperture, float seconds) {
        Level level = aperture.getLevel();
        if (level == null || !aperture.hasSite()) {
            return seconds * LOST_DRIFT;
        }
        Vec3 centre = Vec3.atCenterOf(aperture.getBlockPos());
        Vec3 here = SableBridge.projectToWorld(level, centre);
        Vec3 axisX = SableBridge.projectToWorld(level, centre.add(1, 0, 0)).subtract(here);
        Vec3 axisZ = SableBridge.projectToWorld(level, centre.add(0, 0, 1)).subtract(here);
        double dx = aperture.siteX() + 0.5 - here.x;
        double dz = aperture.siteZ() + 0.5 - here.z;
        // The site's direction along the block's own x and z.
        double px = dx * axisX.x + dz * axisX.z;
        double pz = dx * axisZ.x + dz * axisZ.z;
        BlockState block = aperture.getBlockState();
        Direction facing = block.hasProperty(HorizontalDirectionalBlock.FACING) ? block.getValue(HorizontalDirectionalBlock.FACING) : Direction.NORTH;
        float turn = switch (facing) {
            case SOUTH -> Mth.PI;
            case WEST -> Mth.HALF_PI;
            case EAST -> -Mth.HALF_PI;
            default -> 0;
        };
        // Undo the facing turn, then turn the needle's -z onto that direction.
        double mx = px * Mth.cos(turn) - pz * Mth.sin(turn);
        double mz = px * Mth.sin(turn) + pz * Mth.cos(turn);
        return (float) Mth.atan2(-mx, -mz);
    }
}
