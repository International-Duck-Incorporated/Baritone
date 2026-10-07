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

package baritone.behavior.look;

import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The arm's idle habit is only acceptable because it is rare and irregular, and because a fidget that has
 * been waiting is not swallowed. Both are pinned here.
 * <p>
 * Measured, twenty seeds over five and a half hours of standing around (200000 ticks each): a fidget comes
 * every 694 ticks on average - thirty-five seconds - never closer than the ten second floor and never
 * further than the minute ceiling. A fidget that could not be taken waits for the arm, one of them and no
 * more, and the next starts its own interval from the tick it is taken.
 */
public class IdleFidgetTest {

    private static final int SEEDS = 20;

    /** Five and a half hours of standing around. */
    private static final int LONG_STAND = 200000;

    private static IdleFidget fidget(final long seed) {
        return new IdleFidget(new Random(seed));
    }

    @Test
    public void theArmWaitsBetweenTenSecondsAndAMinute() {
        long fires = 0;
        long ticks = 0;
        long closest = Long.MAX_VALUE;
        long furthest = 0;
        for (long seed = 0; seed < SEEDS; seed++) {
            final IdleFidget fidget = fidget(seed);
            long since = 0;
            for (int t = 0; t < LONG_STAND; t++) {
                fidget.advance();
                since++;
                if (fidget.take()) {
                    fires++;
                    closest = Math.min(closest, since);
                    furthest = Math.max(furthest, since);
                    since = 0;
                }
                ticks++;
            }
        }
        assertEquals("the wait was shorter than the floor: " + closest, (long) IdleFidget.WAIT_MIN, closest);
        assertEquals("the wait was longer than the ceiling: " + furthest, (long) IdleFidget.WAIT_MAX, furthest);
        // and it is a fidget, not a tic: an arm that moves more often than every seven seconds is not
        // standing around, it is twitching, whatever the settings say
        assertTrue("a fidget every " + (ticks / (double) fires) + " ticks", ticks / (double) fires > 150.0);
    }

    @Test
    public void aFidgetWaitsToBeTaken() {
        final IdleFidget fidget = fidget(5);
        int first = -1;
        for (int t = 0; t < 5000 && first < 0; t++) {
            fidget.advance();
            if (fidget.take()) {
                first = t;
            }
        }
        assertTrue("no fidget in five thousand ticks", first >= 0);
        // taken once and no more: nothing is owed, and nothing accumulates while the arm is busy
        assertFalse("the same fidget was taken twice", fidget.take());
    }

    @Test
    public void anOwedFidgetSurvivesTheWaitAndOnlyOneIsOwed() {
        final IdleFidget fidget = fidget(1);
        // five and a half hours go by with nobody asking: the player was walking, or mining
        for (int t = 0; t < LONG_STAND; t++) {
            fidget.advance();
        }
        // the arm has something to do the first tick it is asked, whatever it was doing in the meantime
        assertTrue("a fidget never arrived", fidget.take());
        // and only one: the wait for the next starts here, so nothing can come a tick behind it
        for (int t = 0; t < IdleFidget.WAIT_MIN - 1; t++) {
            fidget.advance();
            assertFalse("a second fidget came before the floor", fidget.take());
        }
        int ticks = IdleFidget.WAIT_MIN - 1;
        while (!fidget.take() && ticks < IdleFidget.WAIT_MAX + IdleFidget.WAIT_MIN) {
            fidget.advance();
            ticks++;
        }
        assertTrue("the next fidget never came", ticks < IdleFidget.WAIT_MAX + IdleFidget.WAIT_MIN);
    }

    @Test
    public void theSameSeedFiresAtTheSameTicks() {
        final IdleFidget first = fidget(9);
        final IdleFidget second = fidget(9);
        for (int t = 0; t < 20000; t++) {
            first.advance();
            second.advance();
            assertEquals(first.take(), second.take());
        }
    }

    @Test
    public void takingEveryFidgetDoesNotBankThem() {
        for (long seed = 0; seed < SEEDS; seed++) {
            final IdleFidget fidget = fidget(seed);
            int fired = 0;
            for (int t = 0; t < LONG_STAND; t++) {
                fidget.advance();
                if (fidget.take()) {
                    fired++;
                }
            }
            // One swing per interval and no more: what the clock allows over this long is bounded above by
            // the floor and below by the ceiling, and taking every fidget as it comes cannot beat either.
            assertTrue("fired " + fired + " times, which is past the floor", fired <= LONG_STAND / IdleFidget.WAIT_MIN + 1);
            assertTrue("fired " + fired + " times, which is short of the ceiling", fired >= LONG_STAND / IdleFidget.WAIT_MAX - 1);
        }
    }
}
