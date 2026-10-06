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
}
