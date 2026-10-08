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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The gap between one job and the next: whose the standing body is, and how it changes hands.
 * <p>
 * The rule under test - see {@link Unattended} - is that the idle repertoire survives the end of a
 * job, and that the person at the keyboard takes the body back the moment they actually arrive, by
 * any input at all, and keep it until the next job takes it from them. Every sequence a person could
 * walk into is here: the body kept for a jobless hour, the hand on the mouse, the key held down, the
 * screen opened, the latch that does not quietly un-latch, and the next job resetting the whole thing.
 */
public class UnattendedTest {

    @Test
    public void theBodyStaysAliveWhenTheJobEnds() {
        final Unattended unattended = new Unattended();
        assertTrue(unattended.glanceIsOurs(true, false, false)); // the job itself
        // job over, nobody has touched anything: the body sways and glances on
        for (int tick = 0; tick < 3600; tick++) { // three minutes of nobody coming back
            assertTrue("an unattended body went statue-still after " + tick + " idle ticks",
                    unattended.glanceIsOurs(false, false, false));
        }
    }

    @Test
    public void theMouseTakesTheBodyBack() {
        final Unattended unattended = new Unattended();
        assertTrue(unattended.glanceIsOurs(false, false, false));
        assertFalse("the view moved and the body did not change hands",
                unattended.glanceIsOurs(false, false, true));
        // the person holds still after moving the view: the body stays theirs - that is a person
        // aiming at something, not an absence
        assertFalse(unattended.glanceIsOurs(false, false, false));
        assertFalse(unattended.glanceIsOurs(false, false, false));
    }

    @Test
    public void soDoesAKeyOrAButtonOrAScreen() {
        // the three inputs arrive the same way from the caller - as "the person showed up" - and any
        // one of them is enough, on a tick the body was otherwise ours
        for (boolean personShowedUp : new boolean[]{true}) {
            final Unattended unattended = new Unattended();
            assertTrue(unattended.glanceIsOurs(false, false, false));
            assertFalse(unattended.glanceIsOurs(false, personShowedUp, false));
            assertFalse(unattended.glanceIsOurs(false, false, false));
        }
    }

    @Test
    public void thePersonArrivingMidWalkIsStillThePersonArriving() {
        // a person who walks off after the job ends is not an absence either; the latch takes them
        // while the body is not allowed to glance anyway (they are moving), so that the moment they
        // stop is not the moment the head starts moving on its own again
        final Unattended unattended = new Unattended();
        assertTrue(unattended.glanceIsOurs(false, false, false));
        assertFalse("the person's keys did not take the body back",
                unattended.glanceIsOurs(false, true, false));
        assertFalse(unattended.glanceIsOurs(false, false, false));
    }

    @Test
    public void theNextJobTakesTheBodyBackAndTheGapStartsOver() {
        final Unattended unattended = new Unattended();
        assertTrue(unattended.glanceIsOurs(false, false, false));
        assertFalse(unattended.glanceIsOurs(false, false, true)); // the person arrives
        assertFalse(unattended.glanceIsOurs(false, false, false)); // and holds the body
        // a new job: theirs goes back to ours mid-hold
        assertTrue(unattended.glanceIsOurs(true, false, false));
        assertTrue(unattended.glanceIsOurs(true, true, true)); // a job owns the body outright
        // and when that job ends, the gap is a fresh one: nobody has touched anything yet
        assertTrue(unattended.glanceIsOurs(false, false, false));
    }

    @Test
    public void aWorldChangeForgetsWhoeverWasThoughtToBeThere() {
        final Unattended unattended = new Unattended();
        assertFalse(unattended.glanceIsOurs(false, true, false));
        unattended.forget();
        assertTrue(unattended.glanceIsOurs(false, false, false));
    }
}
