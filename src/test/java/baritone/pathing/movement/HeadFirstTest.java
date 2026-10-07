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

import baritone.api.utils.Rotation;
import baritone.behavior.look.AimDynamics;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The feet waiting for the head has one job: keep the body from setting off before the head has turned to
 * face the way it is going, and cost nothing anywhere else. The boundaries are the test, because getting
 * them wrong is either a bot that still walks backwards out of every turn or a bot that stands at the top
 * of a jump with the keys off.
 */
public class HeadFirstTest {

    private static final double DEFAULT_SENSITIVITY = 0.5;

    /**
     * Turns a head from {@code behind} degrees off the way the feet are going, at the aim's own pace,
     * and reports how many ticks the feet were held before they were let go.
     */
    private static int ticksHeld(HeadFirst feet, float behind, boolean standing) {
        final Rotation to = new Rotation(0f, 0f);
        Rotation head = new Rotation(behind, 0f);
        int held = 0;
        for (int tick = 0; tick < 100; tick++) {
            final float gap = Math.abs(Rotation.normalizeYaw(head.getYaw() - to.getYaw()));
            if (!feet.hold(gap, standing, true)) {
                return held;
            }
            held++;
            head = AimDynamics.humanStep(head, to, tick, DEFAULT_SENSITIVITY);
        }
        throw new AssertionError("the feet were never let go");
    }

    @Test
    public void aHeadAlreadyFacingTheWayCostsNothing() {
        final HeadFirst feet = new HeadFirst();
        for (int i = 0; i < 50; i++) {
            assertFalse("held with the head on the line", feet.hold(0f, true, true));
            assertFalse("held with the head nearly on the line", feet.hold(HeadFirst.STANDING_LEAD, true, true));
        }
    }

    @Test
    public void fromAStandstillTheHeadLeads() {
        // a half turn: the feet wait for the head to come inside the lead, and not a tick longer
        final int held = ticksHeld(new HeadFirst(), 180f, true);
        assertTrue("the feet set off while the head was facing the other way", held > 0);
        assertTrue("the feet waited " + held + " ticks, past the safety net", held < HeadFirst.MAX_HOLD);
    }

    @Test
    public void theHoldEndsTheTickTheHeadIsNearEnough() {
        final HeadFirst feet = new HeadFirst();
        assertTrue(feet.hold(HeadFirst.STANDING_LEAD + 1f, true, true));
        assertFalse("still held with the head inside the lead", feet.hold(HeadFirst.STANDING_LEAD, true, true));
        assertEquals(0, feet.held());
    }

    @Test
    public void aWalkIsOnlyStoppedForATurnAround() {
        final HeadFirst feet = new HeadFirst();
        // a right angle at a walk is walked round, body a little behind the eyes
        for (int i = 0; i < 20; i++) {
            assertFalse("a right angle stopped the feet at a walk", feet.hold(90f, false, true));
        }
        // a reversal is not
        assertTrue("a reversal at a walk did not stop the feet", feet.hold(180f, false, true));
    }

    @Test
    public void theLeadIsDecidedWhenTheHoldStarts() {
        // A reversal at a walk: the hold starts under the walking lead, and the player decelerates into
        // standing while the head turns. The standing lead must not take over half way through, or a
        // reversal would cost the long standing wait instead of the short walking one.
        final HeadFirst feet = new HeadFirst();
        assertTrue(feet.hold(180f, false, true));
        assertTrue(feet.hold(150f, false, true));
        assertTrue(feet.hold(120f, true, true));
        assertFalse("the standing lead tightened on a hold in progress",
                feet.hold(HeadFirst.WALKING_LEAD - 1f, true, true));
        // ... and a fresh hold from standing is held to the standing lead again
        assertTrue("a fresh hold from standing was not held to the standing lead",
                feet.hold(HeadFirst.STANDING_LEAD + 10f, true, true));
    }

    @Test
    public void nothingIsHeldOffTheGround() {
        final HeadFirst feet = new HeadFirst();
        for (int i = 0; i < 20; i++) {
            assertFalse("held in the air", feet.hold(180f, false, false));
            assertFalse("held standing in the air", feet.hold(180f, true, false));
        }
        assertEquals(0, feet.held());
        // and leaving the ground drops a hold in progress on the spot
        assertTrue(feet.hold(180f, true, true));
        assertFalse(feet.hold(180f, true, false));
        assertEquals("a hold survived leaving the ground", 0, feet.held());
    }

    @Test
    public void theFeetNeverWaitPastTheSafetyNet() {
        // a head that never comes round: the feet go after MAX_HOLD and are not stopped again
        final HeadFirst feet = new HeadFirst();
        int held = 0;
        for (int i = 0; i < 200; i++) {
            if (feet.hold(180f, true, true)) {
                held++;
            }
        }
        assertEquals("a stuck head held the feet " + held + " ticks over two hundred", HeadFirst.MAX_HOLD, held);
        // once the turn is made, the next one gets its own hold
        assertFalse(feet.hold(0f, true, true));
        assertTrue("the hold did not come back for the next turn", feet.hold(180f, true, true));
    }

    @Test
    public void everyTurnFromStandingComesInsideTheSafetyNet() {
        // the safety net is above anything the aim's own ceiling can produce, not a thing the ordinary
        // turn ever reaches: at every angle a head turned at hand speed is let go before it
        for (float behind = 0f; behind <= 180f; behind += 5f) {
            final int held = ticksHeld(new HeadFirst(), behind, true);
            assertTrue("a " + behind + " degree turn held the feet " + held + " ticks", held < HeadFirst.MAX_HOLD);
            if (behind > HeadFirst.STANDING_LEAD) {
                assertTrue("a " + behind + " degree turn let the feet go at once", held > 0);
            }
        }
    }

    @Test
    public void aWiderTurnHoldsLonger() {
        final int quarter = ticksHeld(new HeadFirst(), 90f, true);
        final int half = ticksHeld(new HeadFirst(), 180f, true);
        assertTrue("a half turn (" + half + ") was not held longer than a quarter (" + quarter + ")", half > quarter);
    }
}
