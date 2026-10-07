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
import static org.junit.Assert.assertTrue;

/**
 * The pitch of a gaze that nobody is aiming is only acceptable because it is small, slow, and does not
 * wander off. All three are pinned here, because all three are invisible in a short playtest and obvious in
 * a long one: a band that leaks shows up as a bot that ends up staring at the sky, and a step that grows
 * shows up as a twitch.
 * <p>
 * Measured, worst of fifty seeds over a thousand seconds of walking (20000 ticks, 1000 seconds): the pitch
 * moves on 5.3 percent of ticks, never by more than a degree, never leaves ten degrees either side of
 * level, and the gaze turns over 0.22 times a second - between the one-in-seven and one-in-two the hold
 * length asks for. A pitch left at the sky comes back inside the band in at most 78 ticks. The thresholds
 * below are those numbers with a little room.
 */
public class PitchWanderTest {

    /** A thousand seconds of walking: as long as the sway test, and for the same reason. */
    private static final int LONG_WALK = 20000;

    private static final int SEEDS = 50;

    private static PitchWander wander(final long seed) {
        return new PitchWander(new Random(seed));
    }

    @Test
    public void theGazeNeverLeavesItsBand() {
        float worstPitch = 0.0f;
        float worstPreferred = 0.0f;
        for (long seed = 0; seed < SEEDS; seed++) {
            final PitchWander wander = wander(seed);
            float pitch = 0.0f;
            for (int t = 0; t < LONG_WALK; t++) {
                wander.advance();
                pitch = wander.step(pitch);
                worstPitch = Math.max(worstPitch, Math.abs(pitch));
                worstPreferred = Math.max(worstPreferred, Math.abs(wander.preferredPitch()));
            }
        }
        assertTrue("pitch left the band: " + worstPitch, worstPitch <= PitchWander.RANGE);
        assertTrue("gaze settled outside the band: " + worstPreferred, worstPreferred <= PitchWander.RANGE);
    }

    @Test
    public void aStepIsADegreeAtMostAndOnlyTowardTheGaze() {
        for (long seed = 0; seed < SEEDS; seed++) {
            final PitchWander wander = wander(seed);
            float pitch = 90.0f;
            for (int t = 0; t < LONG_WALK; t++) {
                wander.advance();
                final float ahead = wander.step(pitch);
                final float distance = pitch - wander.preferredPitch();
                if (ahead != pitch) {
                    assertEquals("a step was not a degree", PitchWander.STEP, Math.abs(pitch - ahead), 1e-6f);
                    assertTrue("stepped away from the gaze", distance > 0.0f == ahead < pitch);
                }
                if (Math.abs(distance) <= PitchWander.DEAD_BAND) {
                    assertEquals("moved without leaving the dead band", pitch, ahead, 0.0f);
                }
                pitch = ahead;
            }
        }
    }

    @Test
    public void thePitchRestsFarMoreThanItMoves() {
        long moving = 0;
        long ticks = 0;
        for (long seed = 0; seed < SEEDS; seed++) {
            final PitchWander wander = wander(seed);
            float pitch = 0.0f;
            for (int t = 0; t < LONG_WALK; t++) {
                wander.advance();
                final float ahead = wander.step(pitch);
                if (ahead != pitch) {
                    moving++;
                }
                ticks++;
                pitch = ahead;
            }
        }
        final double fraction = moving / (double) ticks;
        // 5.3 percent measured. A tenth is the line: past that the head is not resting between drifts, it is
        // drifting all the time, which is the shiver the smoothing elsewhere exists to avoid.
        assertTrue("the pitch moved on " + fraction + " of ticks", fraction < 0.10);
    }

    @Test
    public void theGazeTurnsOverInTheSeconds() {
        for (long seed = 0; seed < SEEDS; seed++) {
            final PitchWander wander = wander(seed);
            float last = wander.preferredPitch();
            int changes = 0;
            for (int t = 0; t < LONG_WALK; t++) {
                wander.advance();
                if (wander.preferredPitch() != last) {
                    changes++;
                    last = wander.preferredPitch();
                }
            }
            // The hold is 40 to 140 ticks, so a thousand seconds of walking holds a heart rate between the
            // two: fourteen changes at the longest, a hundred at the shortest, and it is drawn afresh each
            // time rather than being either.
            final double perSecond = changes / (LONG_WALK / 20.0);
            assertTrue("the gaze turned over " + perSecond + " times a second", perSecond > 0.05 && perSecond < 0.60);
        }
    }

    @Test
    public void anExtremePitchIsBroughtBackToTheGaze() {
        for (long seed = 0; seed < SEEDS; seed++) {
            for (float start : new float[]{90.0f, -90.0f}) {
                final PitchWander wander = wander(seed);
                float pitch = start;
                int ticks = 0;
                while (Math.abs(pitch) > PitchWander.RANGE + PitchWander.DEAD_BAND + 1.0f && ticks < 200) {
                    wander.advance();
                    pitch = wander.step(pitch);
                    ticks++;
                }
                assertTrue("started at " + start + ", still at " + pitch + " after " + ticks + " ticks", ticks < 200);
            }
        }
    }

    @Test
    public void theSameSeedGivesTheSameRun() {
        final PitchWander first = wander(7);
        final PitchWander second = wander(7);
        float a = 0.0f;
        float b = 0.0f;
        for (int t = 0; t < 5000; t++) {
            first.advance();
            second.advance();
            a = first.step(a);
            b = second.step(b);
            assertEquals(a, b, 0.0f);
        }
    }

    @Test
    public void theDeadBandHolds() {
        final PitchWander wander = wander(3);
        final float preferred = wander.preferredPitch();
        assertEquals(preferred, wander.step(preferred), 0.0f);
        assertEquals(preferred + 0.4f, wander.step(preferred + 0.4f), 0.0f);
        assertEquals(preferred - 0.4f, wander.step(preferred - 0.4f), 0.0f);
        assertEquals(preferred - 0.4f, wander.step(preferred + 0.6f), 1e-6f);
        assertEquals(preferred + 0.4f, wander.step(preferred - 0.6f), 1e-6f);
    }
}
