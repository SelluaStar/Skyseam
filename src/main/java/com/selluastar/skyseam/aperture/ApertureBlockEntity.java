package com.selluastar.skyseam.aperture;

import java.util.List;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.config.SkyseamConfig;
import com.selluastar.skyseam.external.SableBridge;
import com.selluastar.skyseam.external.Ship;
import com.selluastar.skyseam.network.ApertureGaugePayload;
import com.selluastar.skyseam.registry.SkyseamBlockEntities;
import com.selluastar.skyseam.registry.SkyseamDataComponents;
import com.selluastar.skyseam.registry.SkyseamItems;
import com.selluastar.skyseam.registry.SkyseamSounds;
import com.selluastar.skyseam.seam.SeamEntity;
import com.selluastar.skyseam.seam.Seams;
import com.selluastar.skyseam.seam.site.SeamSite;
import com.selluastar.skyseam.seam.site.SeamSites;
import com.selluastar.skyseam.transfer.SeamCrossing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Harmonic Aperture's block entity (spec sections 6 and 11). On a ship it checks the trigger rules
 * ({@link TriggerRules}) every few ticks and charges while they all hold. A full charge opens the Seam at the site,
 * at the ship's height, facing the way the ship is flying, sized for the ship. The open Seam stays open while a ship
 * carrying an Aperture is near it, and carries such a ship through ({@link SeamCrossing}).
 *
 * <p>The gauge always points the way to the nearest site and says how far it is. A Skychart in the chart slot adds the
 * site's position, the ground there and the altitude needed. Players aboard the ship, and anyone with its screen
 * open, get the gauge ({@link ApertureGaugePayload}). The site is also synced with the block, so its needle can point
 * to it.
 */
public class ApertureBlockEntity extends BlockEntity implements GeoBlockEntity, MenuProvider {
    /** What the Aperture is doing, for its animation: spec section 20, {@code idle, spin_up, charged, cooldown}. */
    public enum Mode { IDLE, SPIN_UP, CHARGED, COOLDOWN }

    /** How often the rules are checked and the gauge sent. */
    public static final int CHECK_PERIOD = 5;
    /** How long the Aperture winds down after its Seam mends. */
    private static final int COOLDOWN_TICKS = 60;
    /** An opened Seam stays open this much further out than the ship's entry radius. */
    private static final double HOLD_MARGIN = 32;
    /** A ship this close to its site, horizontally, is over it: its Seam faces the way it is flying. */
    private static final double OVER_SITE = 8;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation SPIN_UP = RawAnimation.begin().thenLoop("spin_up");
    private static final RawAnimation CHARGED = RawAnimation.begin().thenLoop("charged");
    private static final RawAnimation COOLDOWN = RawAnimation.begin().thenPlay("cooldown").thenLoop("idle");

    /** Set by the client at setup: plays the charge loop. Never set on a dedicated server. */
    public static Consumer<ApertureBlockEntity> clientTicker = aperture -> {};

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final SimpleContainer chart = new SimpleContainer(1) {
        @Override
        public void setChanged() {
            super.setChanged();
            ApertureBlockEntity.this.setChanged();
        }

        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return stack.is(SkyseamItems.SKYCHART.get());
        }
    };
    @Nullable
    private ApertureOwner owner;

    // Synced to clients for the animation, the gems, the needle and the charge loop.
    private Mode mode = Mode.IDLE;
    private float charge;
    private int flags;
    private boolean hasSite;
    private int siteX;
    private int siteZ;

    // Server only.
    private int chargeTicks;
    private int cooldownLeft;
    @Nullable
    private SeamEntity openedSeam;
    @Nullable
    private SeamSite chargingSite;
    @Nullable
    private TriggerRules.Status status;

    public ApertureBlockEntity(BlockPos pos, BlockState state) {
        super(SkyseamBlockEntities.APERTURE.get(), pos, state);
    }

    // ---- Server ---------------------------------------------------------------------------------------------------

    public static void serverTick(Level level, BlockPos pos, BlockState state, ApertureBlockEntity aperture) {
        if (level instanceof ServerLevel server) {
            aperture.serverTick(server);
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, ApertureBlockEntity aperture) {
        clientTicker.accept(aperture);
    }

    private void serverTick(ServerLevel level) {
        if (cooldownLeft > 0) {
            cooldownLeft--;
        }
        if ((level.getGameTime() + worldPosition.hashCode()) % CHECK_PERIOD != 0) {
            return;
        }
        // While charging, the radius the charge started with holds (K58).
        double heldRadius = chargeTicks > 0 && status != null ? status.entryRadius() : 0;
        status = TriggerRules.evaluate(level, worldPosition, owner, heldRadius);
        int full = SkyseamConfig.CHARGE_SECONDS.get() * 20;
        if (!status.crewed()) {
            // Losing the pilot, or the Aperture, mid-charge cancels the charge (spec section 6, "Edge cases").
            chargeTicks = 0;
        } else if (status.allMet()) {
            chargeTicks = Math.min(full, chargeTicks + CHECK_PERIOD);
        } else {
            // Dropping a rule pauses the charge and drains the gauge.
            chargeTicks = Math.max(0, chargeTicks - CHECK_PERIOD);
        }
        showChargeAtSite(level, full);
        // A ship can charge from further out than chunks stay loaded: wait at full charge until the site is live.
        if (chargeTicks >= full && status.allMet() && Seams.isSiteLive(level, status.site())) {
            openSeam(level);
        }
        if (openedSeam != null && (openedSeam.isRemoved() || !openedSeam.state().isOpening())) {
            openedSeam = null;
            cooldownLeft = COOLDOWN_TICKS;
        }
        Mode now = openedSeam != null ? Mode.CHARGED : cooldownLeft > 0 ? Mode.COOLDOWN : chargeTicks > 0 ? Mode.SPIN_UP : Mode.IDLE;
        float shown = (float) chargeTicks / full;
        int newFlags = status.flags();
        SeamSite needle = needleSite(level);
        boolean siteMoved = (needle != null) != hasSite || needle != null && (needle.x() != siteX || needle.z() != siteZ);
        if (siteMoved) {
            hasSite = needle != null;
            siteX = needle != null ? needle.x() : 0;
            siteZ = needle != null ? needle.z() : 0;
        }
        if (newFlags != flags) {
            Skyseam.LOGGER.debug("Aperture at {}: rules {} (ship {}, speed {}, site {}, distance {})", worldPosition.toShortString(),
                    Integer.toBinaryString(newFlags), status.ship() != null ? status.ship().id() : null,
                    status.reading() != null ? status.reading().speed() : 0, status.site(), status.distance());
        }
        if (now != mode || Math.abs(shown - charge) > 1.0e-3 || newFlags != flags || siteMoved) {
            mode = now;
            charge = shown;
            flags = newFlags;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        sendGauge(level);
    }

    /**
     * The site the needle points to: the one the rules are checking, or on the ground (where the rules check none)
     * the nearest one to the Aperture, so it works as a compass while a ship is being built.
     */
    @Nullable
    private SeamSite needleSite(ServerLevel level) {
        if (status != null && status.site() != null) {
            return status.site();
        }
        if (!SeamSites.hasSites(level)) {
            return null;
        }
        Vec3 here = worldPosition(level);
        return SeamSites.nearest(level, here.x, here.z).orElse(null);
    }

    /** Beat 1: the heat shimmer at the site, at the ship's height, as full as the charge. */
    private void showChargeAtSite(ServerLevel level, int full) {
        SeamSite site = status != null && status.radius() && status.dimension() && chargeTicks > 0 ? status.site() : null;
        if (chargingSite != null && !chargingSite.equals(site)) {
            Seams.closedAt(level, chargingSite).ifPresent(seam -> seam.showCharge(0, 0, 0, 0));
        }
        chargingSite = site;
        if (site != null && status.reading() != null) {
            // Load the site's area for as long as the charge lasts, then show the shimmer once it is live.
            Seams.keepSiteLoaded(level, site);
            if (Seams.isSiteLive(level, site)) {
                float[] size = Seams.sizeFor(status.reading().groupBounds());
                double y = status.reading().position().y;
                Seams.placeClosed(level, site).ifPresent(seam -> seam.showCharge((float) chargeTicks / full, y, size[0], size[1]));
            }
        }
    }

    /** The charge is full: open the Seam at the site for this ship (spec section 6, "Spawn point" and "Size"). */
    private void openSeam(ServerLevel level) {
        TriggerRules.ShipReading reading = status.reading();
        SeamSite site = status.site();
        chargeTicks = 0;
        chargingSite = null;
        // Sized for the ship and everything tied to it, so a towed body fits through too.
        float[] size = Seams.sizeFor(reading.groupBounds());
        Vec3 centre = site.at(reading.position().y);
        float yaw = approachYaw(reading.velocity(), centre.subtract(reading.position()));
        Seams.OpenResult result = Seams.openAtSite(level, site, centre, yaw, size[0], size[1]);
        if (!result.opened()) {
            tellPilots(level, result.refusal());
            return;
        }
        SeamEntity seam = result.seam();
        // Spec section 6, "Hold radius": open while a ship carrying an Aperture is near. A big ship starts charging
        // further out than the hold radius, so the hold reaches past where it charged (docs/DECISIONS.md K54).
        double hold = Math.max(SkyseamConfig.HOLD_RADIUS.get(), status.entryRadius() + HOLD_MARGIN);
        seam.keepOpenWhile(() -> ApertureIndex.anyShipWithin(level, seam.position(), hold));
        openedSeam = seam;
        Vec3 here = worldPosition(level);
        level.playSound(null, here.x, here.y, here.z, SkyseamSounds.APERTURE_READY.get(), SoundSource.BLOCKS, 1, 1);
        SeamCrossing.prepareArrival(level, status.ship());
        Skyseam.LOGGER.info("A Harmonic Aperture opened a Seam at site ({}, {}) for ship {}", site.x(), site.z(), status.ship().id());
    }

    /**
     * The yaw a Seam faces for a ship arriving with {@code velocity} from {@code toSite} away: along the line from the
     * ship to the site, which is the way it will come in from the edge of the entry radius whichever way it happens to
     * be heading at that moment, or along its motion if it is already over the site.
     */
    public static float approachYaw(Vec3 velocity, Vec3 toSite) {
        boolean over = toSite.horizontalDistanceSqr() < OVER_SITE * OVER_SITE;
        Vec3 along = !over ? toSite : velocity.horizontalDistanceSqr() > 0.25 ? velocity : toSite;
        if (along.horizontalDistanceSqr() < 1.0e-6) {
            return 0;
        }
        return (float) (Mth.atan2(-along.x, along.z) * Mth.RAD_TO_DEG);
    }

    private void tellPilots(ServerLevel level, @Nullable Component message) {
        if (message == null || status == null || status.reading() == null) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (status.reading().group().stream().anyMatch(body -> SableBridge.isAboard(player, body))) {
                player.displayClientMessage(message, true);
            }
        }
    }

    /** Sends the gauge to everyone aboard the ship and everyone with this Aperture's screen open. */
    private void sendGauge(ServerLevel level) {
        if (status == null) {
            return;
        }
        ApertureGaugePayload payload = ApertureGaugePayload.of(worldPosition, mode, charge, status, hasChart(), owner);
        List<Ship> group = status.reading() != null ? status.reading().group() : List.of();
        for (ServerPlayer player : level.players()) {
            boolean aboard = group.stream().anyMatch(body -> SableBridge.isAboard(player, body));
            boolean viewing = player.containerMenu instanceof ApertureMenu menu && menu.aperturePos().equals(worldPosition);
            if (aboard || viewing) {
                PacketDistributor.sendToPlayer(player, payload);
            }
        }
    }

    /** Where the Aperture is in the world now: on a ship, its plot position moved to where the ship is. */
    public Vec3 worldPosition(Level level) {
        return SableBridge.projectToWorld(level, Vec3.atCenterOf(worldPosition));
    }

    // ---- State ----------------------------------------------------------------------------------------------------

    @Nullable
    public ApertureOwner owner() {
        return owner;
    }

    public void setOwner(@Nullable ApertureOwner owner) {
        this.owner = owner;
        setChanged();
    }

    public boolean mayUse(Player player) {
        return ApertureOwnership.mayUse(owner, player);
    }

    public SimpleContainer chart() {
        return chart;
    }

    public boolean hasChart() {
        return !chart.getItem(0).isEmpty();
    }

    public Mode mode() {
        return mode;
    }

    /** How full the charge is, 0 to 1 (synced). */
    public float charge() {
        return charge;
    }

    /** Which rules hold ({@link TriggerRules.Status} flags, synced): the three status gems. */
    public int flags() {
        return flags;
    }

    /** True if the needle has a site to point to (synced). */
    public boolean hasSite() {
        return hasSite;
    }

    /** The site the needle points to, as block x and z (synced). Only meaningful with {@link #hasSite}. */
    public int siteX() {
        return siteX;
    }

    public int siteZ() {
        return siteZ;
    }

    /** The last rule check, or null before the first. Server only. */
    @Nullable
    public TriggerRules.Status status() {
        return status;
    }

    /** Ticks of charge built up. Server only. */
    public int chargeTicks() {
        return chargeTicks;
    }

    /** The Seam this Aperture opened, while it is open. Server only. */
    @Nullable
    public SeamEntity openedSeam() {
        return openedSeam;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        ApertureIndex.add(this);
    }

    @Override
    public void setRemoved() {
        ApertureIndex.remove(this);
        if (level instanceof ServerLevel server && chargingSite != null) {
            Seams.closedAt(server, chargingSite).ifPresent(seam -> seam.showCharge(0, 0, 0, 0));
        }
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        ApertureIndex.remove(this);
        super.onChunkUnloaded();
    }

    // ---- Saving and syncing ---------------------------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) {
            ApertureOwner.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, owner).result().ifPresent(owned -> tag.put("owner", owned));
        }
        if (hasChart()) {
            tag.put("chart", chart.getItem(0).save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.contains("owner") ? ApertureOwner.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, tag.get("owner")).result().orElse(null) : null;
        chart.setItem(0, tag.contains("chart") ? ItemStack.parseOptional(registries, tag.getCompound("chart")) : ItemStack.EMPTY);
        if (tag.contains("mode")) {
            mode = Mode.values()[Mth.clamp(tag.getInt("mode"), 0, Mode.values().length - 1)];
            charge = tag.getFloat("charge");
            flags = tag.getInt("flags");
            hasSite = tag.getBoolean("has_site");
            siteX = tag.getInt("site_x");
            siteZ = tag.getInt("site_z");
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (owner != null) {
            ApertureOwner.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, owner).result().ifPresent(owned -> tag.put("owner", owned));
        }
        tag.putInt("mode", mode.ordinal());
        tag.putFloat("charge", charge);
        tag.putInt("flags", flags);
        tag.putBoolean("has_site", hasSite);
        tag.putInt("site_x", siteX);
        tag.putInt("site_z", siteZ);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // The owner travels as an item component: kept when the block is broken and set when it is placed.

    @Override
    protected void applyImplicitComponents(BlockEntity.DataComponentInput input) {
        super.applyImplicitComponents(input);
        ApertureOwner fromItem = input.get(SkyseamDataComponents.OWNER.get());
        if (fromItem != null) {
            owner = fromItem;
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        if (owner != null) {
            builder.set(SkyseamDataComponents.OWNER.get(), owner);
        }
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove("owner");
    }

    // ---- Screen ---------------------------------------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ApertureMenu(id, inventory, this);
    }

    // ---- GeckoLib -------------------------------------------------------------------------------------------------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 6, state -> state.setAndContinue(switch (mode) {
            case IDLE -> IDLE;
            case SPIN_UP -> SPIN_UP;
            case CHARGED -> CHARGED;
            case COOLDOWN -> COOLDOWN;
        })));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
