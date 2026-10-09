package com.selluastar.skyseam.seam.site;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.selluastar.skyseam.seam.SeamEntity;
import com.selluastar.skyseam.seam.Seams;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Gives every site near a player its closed Seam (the author's choice: each site shows a faint dormant scar, docs/
 * DECISIONS.md K48). Closed Seams are never saved: they vanish when their chunk unloads and come back when a player
 * is near again.
 */
public final class SiteKeeper {
    /** How far from a player a site gets its closed Seam. Further than the scar can be seen (about 128 blocks). */
    public static final double RANGE = 192;
    private static final int PERIOD = 20;
    private static final List<TestCharge> TEST_CHARGES = new ArrayList<>();

    /** A charge shown by the debug command: the shimmer only, no Aperture and no Seam opening at the end. */
    private static final class TestCharge {
        final SeamEntity seam;
        final double y;
        final int ticks;
        int age;

        TestCharge(SeamEntity seam, double y, int ticks) {
            this.seam = seam;
            this.y = y;
            this.ticks = ticks;
        }
    }

    private SiteKeeper() {}

    /** Filming ({@code /skyseam site charge}): plays a charge's heat shimmer on a closed Seam, at height {@code y}. */
    public static void showTestCharge(SeamEntity seam, double y, int ticks) {
        TEST_CHARGES.add(new TestCharge(seam, y, ticks));
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Iterator<TestCharge> charges = TEST_CHARGES.iterator();
        while (charges.hasNext()) {
            TestCharge charge = charges.next();
            if (charge.seam.level() != level) {
                continue;
            }
            charge.age++;
            boolean done = charge.age > charge.ticks || charge.seam.isRemoved();
            charge.seam.showCharge(done ? 0 : (float) charge.age / charge.ticks, charge.y, 24, 20);
            if (done) {
                charges.remove();
            }
        }
        if (level.getGameTime() % PERIOD != 0 || level.players().isEmpty()) {
            return;
        }
        if (!SeamSites.hasSites(level) && SeamSites.temporary(level).isEmpty()) {
            return;
        }
        Set<SeamSite> done = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator()) {
                continue;
            }
            for (SeamSite site : SeamSites.within(level, player.getX(), player.getZ(), RANGE)) {
                // Only once the chunk is entity-ticking, so the check for a Seam already there sees it (DEVIATIONS D16).
                if (done.add(site) && level.isPositionEntityTicking(new BlockPos(site.x(), 0, site.z()))) {
                    Seams.placeClosed(level, site);
                }
            }
        }
    }
}
