/*
 * This file is part of Baritone.
 *
 * Baritone is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Baritone is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Baritone.  If not, see <https://www.gnu.org/licenses/>.
 */

package baritone.utils;

import org.junit.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The pause between repeated actions has to be a real pause. If this ever goes back to zero the bot
 * chains the next block in the same tick the last one broke, which is the single most obvious tell there
 * is: a person cannot do that, and no amount of camera smoothing hides it.
 */
public class CadenceTest {

    private static final int REST = 5;

    @Test
    public void thePauseIsNeverZero() {
        assertTrue("a zero pause means breaking blocks every tick", Cadence.RETARGET_DELAY_MIN >= 1);
    }

    @Test
    public void everyDrawIsInsideTheRange() {
        final Random rand = new Random(1234);
        for (int i = 0; i < 10000; i++) {
            final int delay = Cadence.retargetDelay(rand);
            assertTrue("delay " + delay + " below the minimum", delay >= Cadence.RETARGET_DELAY_MIN);
            assertTrue("delay " + delay + " above the maximum", delay <= Cadence.RETARGET_DELAY_MAX);
        }
    }

    @Test
    public void theDelayVaries() {
        final Random rand = new Random(4321);
        final Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            seen.add(Cadence.retargetDelay(rand));
        }
        assertEquals("the pause should be able to take every value in its range",
                Cadence.RETARGET_DELAY_MAX - Cadence.RETARGET_DELAY_MIN + 1, seen.size());
    }

    @Test
    public void thePauseIsABeatRatherThanADelay() {
        // 1 to 3 ticks is roughly 50 to 150 milliseconds: long enough to read as reaction time, short
        // enough that breaking a field of crops does not turn into a coffee break.
        final Random rand = new Random(99);
        double total = 0;
        final int draws = 100000;
        for (int i = 0; i < draws; i++) {
            total += Cadence.retargetDelay(rand);
        }
        final double mean = total / draws;
        assertTrue("mean pause " + mean + " ticks is longer than a reaction", mean < 5.0);
        assertTrue("mean pause " + mean + " ticks is barely a pause", mean > 1.0);
    }

    @Test
    public void theFirstClickWaitsForTheCrosshair() {
        // A hand that clicks in the same tick its crosshair arrives is a hand that has already decided to
        // click. One or two ticks of reaction is as short as that gets, and it must never be zero. Both
        // the pickaxe and the block in hand draw this, so it is the reaction to arriving on a face, not a
        // property of either button.
        assertTrue("a zero settle means clicking the tick the crosshair arrives",
                Cadence.SIGHT_SETTLE_MIN >= 1);
        assertTrue("the settle is a reaction, not a nap", Cadence.SIGHT_SETTLE_MAX <= 4);
        final Random rand = new Random(31415);
        final Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            final int settle = Cadence.sightSettle(rand);
            seen.add(settle);
            assertTrue("settle " + settle + " below the minimum", settle >= Cadence.SIGHT_SETTLE_MIN);
            assertTrue("settle " + settle + " above the maximum", settle <= Cadence.SIGHT_SETTLE_MAX);
        }
        assertEquals("the settle should be able to take every value in its range",
                Cadence.SIGHT_SETTLE_MAX - Cadence.SIGHT_SETTLE_MIN + 1, seen.size());
    }

    @Test
    public void aSprintOutlivesTheMovementThatAskedForIt() {
        // Letting go of a run the same tick nothing asks for it means the speed, the particles and the
        // field of view all switch between one tick and the next, which is not something a hand does. It
        // has to be a beat, and it must never be a long enough one to turn into the bot running when it
        // was told to walk.
        assertTrue("a zero hangover releases the key on the very tick the movement stopped asking",
                Cadence.SPRINT_HANGOVER_MIN >= 1);
        assertTrue("a hangover this long is not a hand letting go", Cadence.SPRINT_HANGOVER_MAX <= 4);
        final Random rand = new Random(2718);
        final Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            final int hangover = Cadence.sprintHangover(rand);
            seen.add(hangover);
            assertTrue("hangover " + hangover + " below the minimum", hangover >= Cadence.SPRINT_HANGOVER_MIN);
            assertTrue("hangover " + hangover + " above the maximum", hangover <= Cadence.SPRINT_HANGOVER_MAX);
        }
        assertEquals("a hangover that is always the same length is a timer, not a hand",
                Cadence.SPRINT_HANGOVER_MAX - Cadence.SPRINT_HANGOVER_MIN + 1, seen.size());
    }

    @Test
    public void aSprintStartsAfterAWalk() {
        assertTrue("a zero windup presses control on the first tick of a run",
                Cadence.SPRINT_WINDUP_MIN >= 1);
        assertTrue("a windup this long is standing around, not starting a run",
                Cadence.SPRINT_WINDUP_MAX <= 5);
        final Random rand = new Random(1618);
        final Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            final int windup = Cadence.sprintWindup(rand);
            seen.add(windup);
            assertTrue("windup " + windup + " below the minimum", windup >= Cadence.SPRINT_WINDUP_MIN);
            assertTrue("windup " + windup + " above the maximum", windup <= Cadence.SPRINT_WINDUP_MAX);
        }
        assertEquals("a windup that is always the same length is a timer, not a hand",
                Cadence.SPRINT_WINDUP_MAX - Cadence.SPRINT_WINDUP_MIN + 1, seen.size());
    }

    @Test
    public void theSettleIsPaidOncePerArrival() {
        // The whole point of charging it on the rising edge: a run of blocks clicked with the crosshair
        // never leaving a face is one arrival and one reaction, not a reaction before every block. A settle
        // drawn per tick would make the bot slower the longer it mined, which is the opposite of the point.
        final Cadence.Settle settle = new Cadence.Settle(new Random(99));
        assertTrue("the first tick of a request is not held back at all", settle.waiting(true, true));
        int waited = 1;
        while (settle.waiting(true, true)) {
            waited++;
            assertTrue("the settle outlived its own maximum", waited <= Cadence.SIGHT_SETTLE_MAX);
        }
        assertTrue("the settle was shorter than its own minimum", waited >= Cadence.SIGHT_SETTLE_MIN);
        for (int i = 0; i < 500; i++) {
            assertFalse("a request that is still going is being charged again", settle.waiting(true, true));
        }
    }

    @Test
    public void lettingGoOfTheMouseStartsTheNextSettleAfresh() {
        // Click, let go, click again somewhere else: the second click is a new arrival and gets its own
        // reaction. Without the reset the timer would be long spent and every click after the first would
        // land in the tick the crosshair arrived, which is the thing being fixed.
        final Cadence.Settle settle = new Cadence.Settle(new Random(1234));
        boolean sawThemAllSettle = true;
        for (int round = 0; round < 50; round++) {
            sawThemAllSettle &= settle.waiting(true, true);
            assertFalse("nothing to wait for", settle.waiting(false, true));
            assertFalse("nothing to wait for", settle.waiting(false, true));
        }
        assertTrue("a click after letting go did not wait for the crosshair", sawThemAllSettle);
    }

    @Test
    public void aPlaceTimedAgainstAJumpIsNotHeldBack() {
        // MovementParkour places a block under itself in mid air to land on. That click is timed against
        // the arc and cannot afford a reaction, so a request made while airborne never waits - and the same
        // request is not charged when the player lands on it either, because by then it is not an arrival.
        final Cadence.Settle settle = new Cadence.Settle(new Random(4242));
        for (int i = 0; i < 20; i++) {
            assertFalse("a block placed under a jump was held back", settle.waiting(true, false));
        }
        for (int i = 0; i < 20; i++) {
            assertFalse("a request already in flight was charged when the player landed",
                    settle.waiting(true, true));
        }
        // and once it is let go of, the next click on the ground is a normal arrival again
        assertFalse(settle.waiting(false, true));
        assertTrue(settle.waiting(true, true));
    }

    @Test
    public void everyJitteredIntervalIsWithinATickOfTheSetting() {
        final Random rand = new Random(2026);
        for (int setting = 0; setting <= 40; setting++) {
            for (int i = 0; i < 500; i++) {
                final int drawn = Cadence.jitter(rand, setting);
                assertTrue("an interval of " + setting + " ticks became " + drawn, drawn >= 1 || setting <= 0);
                assertTrue("an interval of " + setting + " ticks became " + drawn,
                        Math.abs(drawn - setting) <= Cadence.JITTER);
            }
        }
    }

    @Test
    public void jitterActuallyVariesTheInterval() {
        // The whole point is that a setting stops being a metronome. A drawing function that always
        // returned its argument would pass every bound above and change nothing at all.
        final Random rand = new Random(7);
        final Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            seen.add(Cadence.jitter(rand, 4));
        }
        assertEquals("a four tick interval can only ever be four ticks", 3, seen.size());
        assertTrue(seen.contains(3) && seen.contains(4) && seen.contains(5));
    }

    /** A thousand seconds of a process holding control: the mining rescan's own default interval is five. */
    private static final int LONG_RUN = 20000;

    private static final int RESCAN = 5;

    @Test
    public void theScanHappensAtOnceAndThenOnItsOwnClock() {
        final Cadence.Repeat repeat = new Cadence.Repeat(new Random(1));
        assertTrue("a process that has just taken control waited before looking at the world", repeat.due(RESCAN));
        int waited = 0;
        while (!repeat.due(RESCAN) && waited < 100) {
            waited++;
        }
        // the gap between one scan and the next is the setting with a tick of slack: the ticks counted here
        // are the ones the scan did not happen on, so one fewer than the gap at each end
        assertTrue("the next scan was " + (waited + 1) + " ticks after the last", waited >= RESCAN - 2 && waited <= RESCAN);
    }

    @Test
    public void theIntervalWandersInsteadOfRepeatingOnAGrid() {
        final Cadence.Repeat repeat = new Cadence.Repeat(new Random(2));
        final Set<Integer> residues = new HashSet<>();
        final Set<Integer> gaps = new HashSet<>();
        int fires = 0;
        int since = 0;
        for (int t = 0; t < LONG_RUN; t++) {
            since++;
            if (repeat.due(RESCAN)) {
                if (fires > 0) {
                    gaps.add(since);
                }
                residues.add(t % RESCAN);
                fires++;
                since = 0;
            }
        }
        // the setting is still the average, tick either way, and the count over a thousand seconds is the
        // one the setting asks for rather than something the slack has quietly changed
        assertEquals("gaps were " + gaps, 3, gaps.size());
        assertTrue(gaps.contains(RESCAN - 1) && gaps.contains(RESCAN) && gaps.contains(RESCAN + 1));
        assertTrue("fired " + fires + " times over " + LONG_RUN + " ticks", fires > LONG_RUN / RESCAN * 9 / 10);
        assertTrue("fired " + fires + " times over " + LONG_RUN + " ticks", fires < LONG_RUN / RESCAN * 11 / 10);
        // and the point of all of it: the scans are not all on the same tick of the interval, so the bot is
        // not only ever able to change its mind on one tick in five. Measured over 20000 ticks: every one of
        // the five residues, from a grid that used to hold exactly one.
        assertEquals("the scans landed on " + residues.size() + " ticks of the interval", RESCAN, residues.size());
    }

    @Test
    public void zeroAsksForNothingAtAll() {
        final Cadence.Repeat repeat = new Cadence.Repeat(new Random(3));
        for (int t = 0; t < 200; t++) {
            assertFalse("something happened on a disabled interval", repeat.due(0));
            assertFalse("something happened on a negative interval", repeat.due(-1));
        }
        // and having been asked for nothing, the clock still knows how to start
        assertTrue(repeat.due(RESCAN));
    }

    @Test
    public void anIntervalOfOneTickStillFiresEveryTick() {
        final Cadence.Repeat repeat = new Cadence.Repeat(new Random(4));
        for (int t = 0; t < 100; t++) {
            assertTrue("a one tick interval skipped a tick", repeat.due(1));
        }
    }

    @Test
    public void theSameSeedScansAtTheSameTicks() {
        final Cadence.Repeat first = new Cadence.Repeat(new Random(6));
        final Cadence.Repeat second = new Cadence.Repeat(new Random(6));
        for (int t = 0; t < 5000; t++) {
            assertEquals(first.due(RESCAN), second.due(RESCAN));
        }
    }

    @Test
    public void aRestLastsAboutAsLongAsItWasAskedFor() {
        final Cadence.Run run = new Cadence.Run(new Random(1));
        run.run(0);
        assertFalse("a rest of nothing was still running", run.running());
        run.run(-1);
        assertFalse("a rest of less than nothing was still running", run.running());
        run.run(REST);
        assertTrue("the rest never started", run.running());
        int lasted = 0;
        while (run.running() && lasted < 100) {
            lasted++;
            run.advance();
        }
        // the rest covers the tick it was asked for, so it spans the setting either side of exact
        assertTrue("the rest lasted " + lasted + " ticks", lasted >= REST - 1 && lasted <= REST + 1);
        // and once it is over, it stays over until it is asked for again
        for (int t = 0; t < 20; t++) {
            run.advance();
            assertFalse("a finished rest ran on by itself", run.running());
        }
    }

    @Test
    public void backToBackRestsAreNotAllTheSameLength() {
        final Cadence.Run run = new Cadence.Run(new Random(2));
        final Set<Integer> lengths = new HashSet<>();
        int total = 0;
        final int rests = 400;
        for (int i = 0; i < rests; i++) {
            run.run(REST);
            int lasted = 0;
            while (run.running()) {
                lasted++;
                run.advance();
            }
            lengths.add(lasted);
            total += lasted;
            run.advance(); // whatever the caller does between rests
        }
        // the builder asks for the same five ticks every time, and never gets it twice running: what a
        // person watching a wall go up reads is the unevenness, not the average, and the average is exactly
        // where the old counter left it
        assertTrue("every rest was " + lengths, lengths.size() > 1);
        for (int length : lengths) {
            assertTrue("a rest lasted " + length + " ticks", length >= REST - 1 && length <= REST + 1);
        }
        final double mean = (double) total / rests;
        assertTrue("the rests averaged " + mean, Math.abs(mean - REST) < 0.3);
    }

    @Test
    public void aRestAsksToBeMeasuredFromTheLastThingThatHappened() {
        // the builder re-asks on every tick a block goes down, which it does while a click is held: the
        // rest runs from the latest block, so a held click does not build up a rest it then has to serve
        final Cadence.Run run = new Cadence.Run(new Random(7));
        run.run(REST);
        run.advance();
        run.advance();
        run.run(REST);
        int lasted = 0;
        while (run.running()) {
            lasted++;
            run.advance();
        }
        assertTrue("the rest was " + lasted + " ticks after being renewed", lasted >= REST - 1 && lasted <= REST + 1);
    }

    @Test
    public void anIntervalOfOneTickIsLeftAlone() {
        // rightClickSpeed and friends can be set to one tick, meaning "as fast as the game will let me".
        // There is no jitter below one, and a zero must stay zero rather than being rounded up to a pause
        // somebody did not ask for.
        final Random rand = new Random(5);
        for (int i = 0; i < 100; i++) {
            assertEquals(1, Cadence.jitter(rand, 1));
            assertEquals(0, Cadence.jitter(rand, 0));
        }
    }

    @Test
    public void aStepOffACurbIsNotAFall() {
        // descends and hops are one and two blocks, and people walk straight out of those
        final Random rand = new Random(42);
        assertEquals("a one block drop got a rest", 0, Cadence.landingRest(rand, 1));
        assertEquals("a two block drop got a rest", 0, Cadence.landingRest(rand, 2));
    }

    @Test
    public void aLandingBeatIsARealBeat() {
        // the moment the feet touch the ground after a fall is the one moment stopping is free, and it
        // must actually stop: a zero would be the tick-the-feet-land walk-on that this exists to kill
        final Random rand = new Random(2024);
        final Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            final int rest = Cadence.landingRest(rand, 4);
            assertTrue("a landing rest of " + rest + " ticks is not a beat", rest >= 1);
            assertTrue("a landing rest of " + rest + " ticks is a nap", rest <= 5);
            seen.add(rest);
        }
        assertTrue("every landing rest is the same length: " + seen, seen.size() > 1);
    }

    @Test
    public void aLongerFallRestsLonger() {
        // drawn from the same sequence, so the jitter is the same and the difference is the fall
        for (int seed = 0; seed < 100; seed++) {
            final Random same1 = new Random(seed);
            final Random same2 = new Random(seed);
            final int hop = Cadence.landingRest(same1, 3);
            final int drop = Cadence.landingRest(same2, 40);
            assertTrue("a 40 block drop rested " + drop + " against " + hop + " for a 3 block one", drop > hop);
        }
    }
}
