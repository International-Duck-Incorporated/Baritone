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

package baritone.pathing.movement;

import baritone.utils.Cadence;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SprintWindupTest {

    @Test
    public void aNewRunWalksFirst() {
        final SprintWindup windup = new SprintWindup(new Random(1));
        assertFalse("control on the first tick of a run", windup.tick(true, true));
        int walked = 1;
        while (!windup.tick(true, true)) {
            walked++;
            assertTrue("windup outlived its maximum", walked <= Cadence.SPRINT_WINDUP_MAX);
        }
        assertTrue("windup shorter than its minimum", walked >= Cadence.SPRINT_WINDUP_MIN);
        for (int i = 0; i < 20; i++) {
            assertTrue("a run already going is being wound up again", windup.tick(true, true));
        }
    }

    @Test
    public void parkourDoesNotWait() {
        final SprintWindup windup = new SprintWindup(new Random(2));
        assertTrue("a gap that needs speed on tick one was delayed", windup.tick(true, false));
    }

    @Test
    public void hangoverIsTheSameRun() {
        final SprintWindup windup = new SprintWindup(new Random(3));
        while (!windup.tick(true, true)) {
            // spend the windup
        }
        windup.keepHeld();
        assertTrue("the movement after a hangover wound up again", windup.tick(true, true));
    }

    @Test
    public void lettingGoStartsTheNextWindup() {
        final SprintWindup windup = new SprintWindup(new Random(4));
        while (!windup.tick(true, true)) {
        }
        assertFalse(windup.tick(false, true));
        assertFalse("the second run started sprinting on the tick it was asked", windup.tick(true, true));
    }
}
