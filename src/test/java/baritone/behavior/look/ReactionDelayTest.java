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

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The reaction pause has one job: cost a real turn a small, human amount of time, and cost tracking
 * nothing at all. Over-reaching here would slow the bot down visibly for no benefit, so the boundaries are
 * worth pinning down.
 */
public class ReactionDelayTest {

    private static Rotation yaw(float yaw) {
        return new Rotation(yaw, 0f);
    }

    /** @return how many ticks in a row the hand was told to stay still, starting from a fresh state */
    private static int holdLength(ReactionDelay delay, Rotation target, int max) {
        int held = 0;
        while (held < max && delay.holding(target)) {
            held++;
        }
        return held;
    }

    @Test
    public void aFreshTargetCostsAReaction() {
        for (int i = 0; i < 200; i++) {
            final ReactionDelay delay = new ReactionDelay();
            final int held = holdLength(delay, yaw(90f), 50);
            assertTrue("a fresh target started moving immediately", held >= ReactionDelay.HOLD_MIN);
            assertTrue("a fresh target stalled for " + held + " ticks", held <= ReactionDelay.HOLD_MAX);
        }
    }

    @Test
    public void trackingTheSameTargetCostsNothing() {
        final ReactionDelay delay = new ReactionDelay();
        delay.holding(yaw(90f));
        holdLength(delay, yaw(90f), 50);
        for (int t = 0; t < 200; t++) {
            assertFalse("the aim stalled while already tracking its target", delay.holding(yaw(90f)));
        }
    }

    @Test
    public void theTargetCreepingForwardDoesNotReArm() {
        // this is what tracking a moving target or walking a path looks like: tiny changes every tick
        final ReactionDelay delay = new ReactionDelay();
        holdLength(delay, yaw(0f), 50);
        for (int t = 1; t <= 200; t++) {
            assertFalse("a " + t + " deg drift re-armed the reaction", delay.holding(yaw(t * 0.5f)));
        }
    }

    @Test
    public void aRealChangeOfDirectionRearms() {
        final ReactionDelay delay = new ReactionDelay();
        holdLength(delay, yaw(0f), 50);
        final int held = holdLength(delay, yaw(-90f), 50);
        assertTrue("a reversal was started without a reaction", held >= ReactionDelay.HOLD_MIN);
        assertTrue(held <= ReactionDelay.HOLD_MAX);
    }

    @Test
    public void pitchCountsToo() {
        final ReactionDelay delay = new ReactionDelay();
        holdLength(delay, new Rotation(0f, 0f), 50);
        assertTrue("looking up was started without a reaction",
                holdLength(delay, new Rotation(0f, 60f), 50) >= ReactionDelay.HOLD_MIN);
        delay.clear();
        holdLength(delay, new Rotation(0f, 0f), 50);
        assertFalse("a small pitch adjustment re-armed the reaction",
                delay.holding(new Rotation(0f, 5f)));
    }

    @Test
    public void theWrapPointIsMeasuredTheShortWay() {
        // 170 and -170 are twenty degrees apart, not three hundred and forty
        final ReactionDelay delay = new ReactionDelay();
        holdLength(delay, yaw(170f), 50);
        assertFalse("crossing the wrap point was mistaken for a new direction",
                delay.holding(yaw(-170f)));
        assertEquals(20.0f, ReactionDelay.separation(yaw(170f), yaw(-170f)), 0.01f);
        assertEquals(180.0f, ReactionDelay.separation(yaw(90f), yaw(-90f)), 0.01f);
        assertEquals(0.0f, ReactionDelay.separation(yaw(-45f), yaw(315f)), 0.01f);
    }

    @Test
    public void clearingForgetsEverything() {
        final ReactionDelay delay = new ReactionDelay();
        holdLength(delay, yaw(0f), 50);
        delay.clear();
        assertTrue("a target after a clear was not treated as new", delay.holding(yaw(0f)));
    }

    @Test
    public void theDelayStaysSmallWhateverItIsFed() {
        // Each change of direction gets its own reaction, charged exactly once, and tracking never pays.
        // Two new directions in consecutive ticks legitimately chain, so the cap is per direction, not per
        // run of ticks.
        final Random rand = new Random(7);
        final ReactionDelay delay = new ReactionDelay();
        Rotation previous = null;
        for (int i = 0; i < 2000; i++) {
            final Rotation target = yaw(rand.nextFloat() * 360f - 180f);
            final boolean newDirection = previous == null
                    || ReactionDelay.separation(previous, target) > ReactionDelay.NEW_DIRECTION_ANGLE;
            int held = 0;
            while (held < 100 && delay.holding(target)) {
                held++;
            }
            assertTrue("a change of direction stalled for " + held + " ticks",
                    held <= ReactionDelay.HOLD_MAX);
            if (newDirection) {
                assertTrue("a new direction started moving immediately", held >= ReactionDelay.HOLD_MIN);
            } else {
                assertEquals("a target that barely moved still paid a reaction", 0, held);
            }
            previous = target;
        }
    }
}
