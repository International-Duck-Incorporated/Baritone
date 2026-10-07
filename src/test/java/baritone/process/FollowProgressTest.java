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

package baritone.process;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Following has one rule: a target that keeps moving is not a reason to stop walking, and a target that
 * has actually gone somewhere else is. These tests are where the line between the two is drawn.
 */
public class FollowProgressTest {

    private static final int[] ME = {0, 64, 0};
    private static final int[] END = {50, 64, 0};

    private static boolean finishing(int targetX, int targetY, int targetZ) {
        return FollowProgress.worthFinishing(ME[0], ME[1], ME[2], END[0], END[1], END[2], targetX, targetY, targetZ);
    }

    @Test
    public void aTargetStillAheadIsStillProgress() {
        // the everyday case: the target walked on past where the path was made for, and getting to the
        // end of it is still getting closer to them
        assertTrue(finishing(100, 64, 0));
        assertTrue(finishing(END[0], 64, 0)); // standing right on the end
        assertTrue(finishing(51, 64, 3)); // a little way off the end
    }

    @Test
    public void aTargetThatDoubledBackIsNot() {
        // the target is now nearer the player than the end of the walk is: walking on is walking away
        assertFalse(finishing(5, 64, 0));
        assertFalse(finishing(ME[0], ME[1], ME[2])); // right at the player's feet
    }

    @Test
    public void aTargetThatWentSidewaysTurnsAroundToo() {
        // perpendicular from the player: the end of the walk is further from them than standing still
        assertFalse(finishing(ME[0], 64, 60));
        // and the same target from the end's point of view is behind everybody
        assertFalse(finishing(ME[0] - 10, 64, ME[2] - 10));
    }

    @Test
    public void heightIsDistanceToo() {
        // a target that went up the hill above the end is still nearest the end
        assertTrue(finishing(END[0], END[1] + 12, END[2]));
        // but one that went up the hill above the player is nearest the player
        assertFalse(finishing(ME[0], ME[1] + 12, ME[2]));
    }

    @Test
    public void anEvenSplitKeepsWalking() {
        // exactly between the player and the end: arriving is no worse than standing, and a person
        // already on their way does not stop for a tie
        assertTrue(finishing((ME[0] + END[0]) / 2, 64, 0));
    }

    @Test
    public void aTargetWalkingAwayStaysProgressForever() {
        // the case this exists for: a target that keeps walking away from the path's end, on and on,
        // must never take the walk apart - each end is still the closer place to get to
        for (int away = 0; away < 500; away += 10) {
            assertTrue("a target " + away + " blocks beyond the end stopped the walk",
                    finishing(END[0] + away, END[1], END[2]));
        }
    }
}
