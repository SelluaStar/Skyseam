package com.selluastar.skyseam.gametest;

import java.util.EnumMap;
import java.util.Map;

import com.selluastar.skyseam.Skyseam;
import com.selluastar.skyseam.block.TideVaneBlock;
import com.selluastar.skyseam.halcyon.tide.Tide;
import com.selluastar.skyseam.halcyon.tide.TideSchedule;
import com.selluastar.skyseam.halcyon.tide.Tides;
import com.selluastar.skyseam.registry.SkyseamBlocks;
import com.selluastar.skyseam.world.HalcyonLayout;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The Tides (docs/DECISIONS.md K111): when they run, forcing and stopping them, and the Tide Vane. */
@GameTestHolder(Skyseam.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TideGameTests {
    private static final String EMPTY = "gametest/empty";

    private TideGameTests() {}

    /**
     * One Tide in every 25-minute slot, 6 to 10 minutes long, inside its slot and never overlapping the next; Stillness
     * rare, the other three about alike; the forecast at any moment names the Tide running (if any) and the next, the
     * same every time.
     */
    @GameTest(template = EMPTY)
    public static void tidesComeEveryTwentyFiveMinutesOrSo(GameTestHelper helper) {
        TideSchedule.Settings settings = TideSchedule.Settings.DEFAULT;
        long seed = 12345L;
        int slots = 400;
        Map<Tide, Integer> counts = new EnumMap<>(Tide.class);
        TideSchedule.Window previous = null;
        long running = 0;
        for (long slot = 0; slot < slots; slot++) {
            TideSchedule.Window window = TideSchedule.natural(seed, settings, slot);
            long length = window.end() - window.start();
            helper.assertTrue(length >= settings.shortest() && length <= settings.longest(), "Slot " + slot + "'s Tide lasts " + length + " ticks");
            helper.assertTrue(window.start() >= slot * settings.slot() && window.end() <= (slot + 1) * settings.slot(),
                    "Slot " + slot + "'s Tide runs outside it: " + window);
            helper.assertTrue(previous == null || !previous.overlaps(window), "Tides overlap: " + previous + " and " + window);
            helper.assertTrue(window.equals(TideSchedule.natural(seed, settings, slot)), "Slot " + slot + " changed between asks");
            counts.merge(window.tide(), 1, Integer::sum);
            running += length;
            previous = window;
        }
        double stillness = counts.getOrDefault(Tide.STILLNESS, 0) / (double) slots;
        helper.assertTrue(stillness > 0.04 && stillness < 0.18, "Stillness is " + stillness + " of the Tides");
        for (Tide tide : new Tide[] {Tide.PRISMFALL, Tide.GALE, Tide.BLOOM}) {
            double share = counts.getOrDefault(tide, 0) / (double) slots;
            helper.assertTrue(share > 0.22 && share < 0.38, tide + " is " + share + " of the Tides");
        }
        for (long time = 0; time < 200_000; time += 777) {
            TideSchedule.Forecast forecast = TideSchedule.forecast(seed, settings, null, time);
            helper.assertTrue(forecast.current() == null || forecast.current().covers(time), "At " + time + " the current Tide isn't running: " + forecast);
            helper.assertTrue(forecast.next() != null && forecast.next().start() > time, "At " + time + " there's no next Tide: " + forecast);
            helper.assertTrue(forecast.next().start() - time <= 2 * settings.slot(), "At " + time + " the next Tide is too far off: " + forecast);
        }
        Skyseam.LOGGER.info("Skyseam Tides: in {} slots {}, running {} of the time", slots, counts,
                String.format("%.2f", running / (double) (slots * settings.slot())));
        helper.succeed();
    }

    /** A forced Tide takes the place of any natural one it overlaps, a forced Calm stops one, and both lapse. */
    @GameTest(template = EMPTY)
    public static void aForcedTideTakesOver(GameTestHelper helper) {
        TideSchedule.Settings settings = TideSchedule.Settings.DEFAULT;
        long seed = 777L;
        TideSchedule.Window natural = TideSchedule.natural(seed, settings, 10);
        long during = (natural.start() + natural.end()) / 2;
        TideSchedule.Window gale = new TideSchedule.Window(natural.tide() == Tide.GALE ? Tide.BLOOM : Tide.GALE, during - 100, during + 5000);
        TideSchedule.Forecast forced = TideSchedule.forecast(seed, settings, gale, during);
        helper.assertTrue(forced.current() != null && forced.current().equals(gale), "The forced Tide isn't running: " + forced);
        TideSchedule.Window calm = new TideSchedule.Window(Tide.CALM, during - 100, natural.end());
        TideSchedule.Forecast stopped = TideSchedule.forecast(seed, settings, calm, during);
        helper.assertTrue(stopped.current() == null, "A forced Calm didn't stop the Tide: " + stopped);
        TideSchedule.Forecast after = TideSchedule.forecast(seed, settings, calm, natural.end() + settings.slot());
        helper.assertTrue(after.current() == null || !after.current().equals(calm), "The forced Calm didn't lapse");
        helper.succeed();
    }

    /**
     * The Tide Vane shows the Tide running (forced here, already under way) and gives a comparator its number; and the
     * forced Tide is the server's: running in the Halcyon, Calm anywhere else.
     */
    @GameTest(template = EMPTY, batch = "m4_tide_vane", timeoutTicks = 200)
    public static void tideVaneShowsTheTide(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel halcyon = server.getLevel(HalcyonLayout.LEVEL);
        BlockPos at = new BlockPos(1, 2, 1);
        helper.setBlock(at, SkyseamBlocks.TIDE_VANE.get());
        long now = server.overworld().getGameTime();
        Tides.force(server, new TideSchedule.Window(Tide.PRISMFALL, now - 2000, now + 4000));
        helper.runAfterDelay(30, () -> {
            helper.assertBlockProperty(at, TideVaneBlock.SHOWING, Tide.PRISMFALL);
            int signal = helper.getBlockState(at).getAnalogOutputSignal(helper.getLevel(), helper.absolutePos(at));
            helper.assertTrue(signal == 3, "The vane's comparator signal is " + signal);
            helper.assertTrue(halcyon == null || Tides.tide(halcyon) == Tide.PRISMFALL, "The Halcyon's Tide isn't the Prismfall");
            helper.assertTrue(Tides.tide(helper.getLevel()) == Tide.CALM, "The Tide runs outside the Halcyon");
            Tides.unforce(server);
            helper.succeed();
        });
    }
}
