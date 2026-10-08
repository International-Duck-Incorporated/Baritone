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

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The crouch a person keeps while their footing is not certain, over the two things that decide it.
 * <p>
 * The boundaries are the whole test, the way they are for {@code KeyBeatTest}: a crouch that ends a tick
 * early is the flicker this class exists to remove, a crouch that lingers past the last gap is a bot
 * walking away from its bridge still crouched, and a crouch that appears on a walk that crosses nothing is
 * a bot crouching at nothing. None of it is timed and none of it is drawn, so every case here is exact.
 */
public class CarefulFootingTest {

    /** a tick of a movement that has to place the block it is stepping onto */
    private static final boolean ASKED = true;
    /** a tick of a step whose own block is there and whose next one is not: the middle of a crossing */
    private static final boolean GAP_AHEAD = true;
    /** the ground under the step after this one is solid */
    private static final boolean GROUND_AHEAD = false;

    @Test
    public void theCrouchGoesDownTheTickAMovementHasToPlaceUnderItself() {
        final CarefulFooting footing = new CarefulFooting();
        assertFalse("the crouch is down before anything asked for it", footing.crouched());
        assertTrue("a movement that has to place the block it is stepping onto got no crouch",
                footing.tick(ASKED, GROUND_AHEAD));
        assertTrue("the crouch is not down the tick after it was asked for", footing.crouched());
    }

    @Test
    public void anAskIsNeverShortened() {
        final CarefulFooting footing = new CarefulFooting();
        for (boolean gapAhead : new boolean[]{GROUND_AHEAD, GAP_AHEAD}) {
            footing.reset();
            assertTrue("an ask was answered with an upright walk because of what was ahead of it",
                    footing.tick(ASKED, gapAhead));
            assertTrue("an ask was answered with an upright walk on the tick after it", footing.tick(ASKED, gapAhead));
        }
    }

    @Test
    public void theCrouchStaysDownForTheWalkOntoTheBlockJustPlaced() {
        final CarefulFooting footing = new CarefulFooting();
        footing.tick(ASKED, GAP_AHEAD);
        // the block goes in: the movement stops asking, the next gap is still there, and the feet walk onto
        // the block that was just placed. This is the window the old behaviour stood up in, four or five
        // ticks at walk speed, once per block.
        for (int tick = 0; tick < 6; tick++) {
            assertTrue("the bot stood up in the middle of its own crossing, on tick " + tick,
                    footing.tick(!ASKED, GAP_AHEAD));
        }
        assertFalse("the crouch outlived the crossing: the ground ahead is solid and nothing is asking",
                footing.tick(!ASKED, GROUND_AHEAD));
    }

    @Test
    public void aWalkThatIsNotCrossingAnythingGetsNoCrouch() {
        final CarefulFooting footing = new CarefulFooting();
        assertFalse("a plain walk was crouched", footing.tick(!ASKED, GROUND_AHEAD));
        // a hole beside an ordinary walk is not a crossing: the machine only reads the gap ahead while the
        // crouch is already down, so a single missing block ahead of a step cannot start one
        assertFalse("a walk past a hole was crouched", footing.tick(!ASKED, GAP_AHEAD));
    }

    @Test
    public void aCrossingOfManyBlocksIsOneCrouch() {
        final CarefulFooting footing = new CarefulFooting();
        final boolean[] crossing = {
                // step one: nothing under it, place it
                ASKED, ASKED, ASKED,
                // the walk onto it, with the next gap already there
                !ASKED, !ASKED, !ASKED, !ASKED,
                // step two
                ASKED, ASKED,
                !ASKED, !ASKED, !ASKED, !ASKED,
                // step three
                ASKED, ASKED,
                !ASKED, !ASKED, !ASKED, !ASKED,
                // step four, and the far side: solid ground under and beyond
                ASKED, !ASKED, !ASKED,
        };
        for (int tick = 0; tick < crossing.length; tick++) {
            assertTrue("the crouch came up in the middle of the crossing, on tick " + tick,
                    footing.tick(crossing[tick], GAP_AHEAD));
        }
        assertFalse("the crossing is over and the bot is still crouched", footing.tick(!ASKED, GROUND_AHEAD));
    }

    @Test
    public void theGroundComingBackStandsTheBotUpOnTheTick() {
        final CarefulFooting footing = new CarefulFooting();
        footing.tick(ASKED, GAP_AHEAD);
        assertFalse("the crouch took a tick longer than the ground did", footing.tick(!ASKED, GROUND_AHEAD));
        assertFalse("the bot crouched again with nothing asking and nothing ahead", footing.tick(!ASKED, GROUND_AHEAD));
    }

    @Test
    public void resetForgetsACrossing() {
        final CarefulFooting footing = new CarefulFooting();
        footing.tick(ASKED, GAP_AHEAD);
        footing.reset();
        assertFalse("a crossing that was forgotten is still being walked", footing.crouched());
        assertFalse("a crossing that was forgotten started up again by itself", footing.tick(!ASKED, GAP_AHEAD));
    }

    @Test
    public void whetherTheCrouchIsDownCanBeAskedWithoutTicking() {
        final CarefulFooting footing = new CarefulFooting();
        assertFalse(footing.crouched());
        footing.tick(ASKED, GAP_AHEAD);
        assertTrue("asking whether the crouch is down ticked it or lost it", footing.crouched());
        footing.tick(!ASKED, GROUND_AHEAD);
        assertFalse("asking whether the crouch is down kept a crouch that had ended", footing.crouched());
    }
}
