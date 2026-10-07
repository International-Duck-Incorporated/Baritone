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
import static org.junit.Assert.assertTrue;

/**
 * The pause between repeated actions has to be a real pause. If this ever goes back to zero the bot
 * chains the next block in the same tick the last one broke, which is the single most obvious tell there
 * is: a person cannot do that, and no amount of camera smoothing hides it.
 */
public class CadenceTest {

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
}
