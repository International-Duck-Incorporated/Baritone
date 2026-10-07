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

import baritone.api.utils.Rotation;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The lean onto the line ahead has one job: lead the head around corners without ever pulling it off
 * the walk it is looking at. The boundaries are the whole test.
 */
public class GazeAheadTest {

    private static final Rotation WALKING = new Rotation(0f, 0f);

    private static float yawOf(GazeAhead gaze, Rotation ahead, double weight, boolean blockInteract) {
        gaze.offer(ahead, weight);
        return gaze.lean(WALKING, blockInteract).getYaw();
    }

    @Test
    public void aStraightLineIsNoLeanAtAll() {
        // the point of the whole thing: where the path does not bend, the gaze does not move
        final GazeAhead gaze = new GazeAhead();
        assertEquals(0f, yawOf(gaze, new Rotation(0f, 10f), GazeAhead.LEAN, false), 1e-6f);
    }

    @Test
    public void aCornerIsLedByHalfwayAtTheStrongest() {
        final GazeAhead gaze = new GazeAhead();
        // a right angle round the corner, at full strength: halfway there
        assertEquals(45f, yawOf(gaze, new Rotation(90f, 0f), GazeAhead.LEAN, false), 1e-3f);
    }

    @Test
    public void theLeanFadesInWithTheDistance() {
        final GazeAhead gaze = new GazeAhead();
        final float near = yawOf(gaze, new Rotation(90f, 0f), 0.5, false);
        final float far = yawOf(gaze, new Rotation(90f, 0f), 0.1, false);
        assertTrue("a weaker weight leaned further", Math.abs(far) < Math.abs(near));
    }

    @Test
    public void somethingAboutToBeClickedIsAimedAtExactly() {
        // the one rule every smoothing in this fork stands aside for
        final GazeAhead gaze = new GazeAhead();
        assertEquals(0f, yawOf(gaze, new Rotation(90f, 0f), GazeAhead.LEAN, true), 1e-6f);
    }

    @Test
    public void theLeanNeverPointsBehindTheWalk() {
        // a path that doubles back: the head goes over the shoulder, not all the way around
        final GazeAhead gaze = new GazeAhead();
        final float yaw = yawOf(gaze, new Rotation(180f, 0f), GazeAhead.LEAN, false);
        assertTrue("a doubling-back path leaned " + yaw + " degrees off the walk",
                Math.abs(yaw) <= GazeAhead.MAX_LEAN_DEGREES + 1e-3f);
    }

    @Test
    public void theShortWayRoundIsTheWayTaken() {
        // across the seam: a bend from 170 to -170 leans 20 degrees through 180, not 340 through 0
        final GazeAhead gaze = new GazeAhead();
        gaze.offer(new Rotation(-170f, 0f), GazeAhead.LEAN);
        final float yaw = gaze.lean(new Rotation(170f, 0f), false).getYaw();
        assertEquals(180f, yaw, 1e-3f);
    }

    @Test
    public void aLeanNotTakenIsGone() {
        final GazeAhead gaze = new GazeAhead();
        gaze.offer(new Rotation(90f, 0f), GazeAhead.LEAN);
        gaze.tick();
        assertEquals(0f, yawOf(gaze, null, 0, false), 1e-6f); // the offer was reset; a new one of nothing
    }

    @Test
    public void oneOfferLeansOnce() {
        // the walk offers every tick; one offer is one lean, and the next target is on its own
        final GazeAhead gaze = new GazeAhead();
        gaze.offer(new Rotation(90f, 0f), GazeAhead.LEAN);
        assertEquals(45f, gaze.lean(WALKING, false).getYaw(), 1e-3f);
        assertEquals(0f, gaze.lean(WALKING, false).getYaw(), 1e-6f);
    }

    @Test
    public void thePitchLeansToo() {
        // the path climbs round the corner and the gaze takes the stairs with it
        final GazeAhead gaze = new GazeAhead();
        gaze.offer(new Rotation(0f, -30f), GazeAhead.LEAN);
        assertEquals(-15f, gaze.lean(WALKING, false).getPitch(), 1e-3f);
    }

    @Test
    public void aZeroWeightIsNoLean() {
        final GazeAhead gaze = new GazeAhead();
        assertEquals(0f, yawOf(gaze, new Rotation(90f, 0f), 0, false), 1e-6f);
    }
}
