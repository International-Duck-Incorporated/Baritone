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

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The tempo a turn is made at has one job: make two turns to the same place into two different turns, and
 * stay inside the band the rest of the aim was measured against while doing it.
 */
public class TurnPaceTest {

    @Test
    public void aTempoIsDrawnInsideItsBand() {
        final TurnPace pace = new TurnPace(new Random(1));
        for (int i = 0; i < 2000; i++) {
            final float drawn = pace.begin();
            assertTrue("a tempo of " + drawn + " is quicker than the band allows", drawn <= TurnPace.MAX);
            assertTrue("a tempo of " + drawn + " is slower than the band allows", drawn >= TurnPace.MIN);
        }
    }

    @Test
    public void theBandIsWideEnoughToBeWorthDrawingFrom() {
        // A band that draws the same number every time is a constant with extra steps, and the whole point
        // of this class is that the same turn is not the same turn twice.
        final TurnPace pace = new TurnPace(new Random(7));
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        final Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            final float drawn = pace.begin();
            min = Math.min(min, drawn);
            max = Math.max(max, drawn);
            seen.add(Math.round(drawn * 1000f));
        }
        assertTrue("200 draws only produced " + seen.size() + " tempos", seen.size() > 100);
        assertTrue("the band was only ever drawn from at one end: " + min + " to " + max,
                max - min > (TurnPace.MAX - TurnPace.MIN) * 0.9f);
    }

    @Test
    public void aTurnKeepsTheTempoItStartedWith() {
        // Per turn, not per tick: a hand that drew again every tick would have no tempo at all, it would be
        // the per-tick shiver the tremor's smoothing exists to remove.
        final TurnPace pace = new TurnPace(new Random(11));
        final float drawn = pace.begin();
        for (int t = 0; t < 100; t++) {
            assertEquals("the tempo changed under a turn that was already under way", drawn, pace.pace(), 0f);
        }
        final float next = pace.begin();
        assertEquals("a new turn did not get the tempo it drew", next, pace.pace(), 0f);
        assertTrue("two consecutive turns drew exactly the same tempo", Math.abs(next - drawn) > 1e-6f);
    }

    @Test
    public void anUntouchedTempoIsTheOrdinarySpeed() {
        // A caller that never starts a turn must get the turn it always got, to the last degree: this is the
        // value that reproduces the trajectories in AimDynamicsTest.
        assertEquals(1.0f, new TurnPace(new Random(3)).pace(), 0f);
    }

    @Test
    public void theClockSeededConstructorDrawsToo() {
        // The one constructor with no argument is the only one that seeds itself, the way it is everywhere
        // else in this package; what matters is that it also produces a tempo rather than a constant.
        final TurnPace pace = new TurnPace();
        final float first = pace.begin();
        boolean differs = false;
        for (int i = 0; i < 100 && !differs; i++) {
            differs = Math.abs(pace.begin() - first) > 1e-6f;
        }
        assertTrue("the clock-seeded tempo never moved off its first draw", differs);
    }
}
