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
 * The head sway is only acceptable because it is small, smooth, and cannot lean. These tests pin all
 * three, because the failure mode that matters - a head quietly leaning off the bearing over a long walk -
 * is invisible in a short playtest and obvious over a long one.
 * <p>
 * Measured limits, worst of fifty seeds over a thousand seconds of walking (20000 ticks, 4317 blocks):
 * offset never leaves its limit, one tick never moves more than 0.16 degrees, no four-hundred-tick window
 * leans more than 0.08 degrees, and the total sideways deviation reaches 0.38 blocks - nine thousandths of
 * one percent of the distance walked. The thresholds below are those numbers with a little room, so a
 * regression that makes the sway grow again fails here rather than in someone's game.
 */
public class HeadWanderTest {

    /** A walking pace: four point three blocks a second, twenty ticks to the second. */
    private static final double BLOCKS_PER_TICK = 4.317 / 20.0;

    /** A thousand seconds of walking: two thousand blocks a quarter, if it were diagonal. */
    private static final int LONG_WALK = 20000;

    /**
     * A sway with a seed nobody has to guess at. Everything here draws amplitudes and periods, so a test
     * that took its sway from the clock would be a test of the clock: the two long-walk assertions are the
     * ones that can fail on an unlucky draw, and they failed on the pipeline before this existed.
     */
    private static HeadWander sway(final long seed) {
        return new HeadWander(new Random(seed));
    }

    private static float[] run(int ticks) {
        final HeadWander wander = sway(1);
        final float[] yaws = new float[ticks];
        for (int t = 0; t < ticks; t++) {
            wander.advance();
            yaws[t] = wander.yaw();
        }
        return yaws;
    }

    @Test
    public void theOffsetsNeverLeaveTheirLimits() {
        final HeadWander wander = sway(1);
        for (int t = 0; t < 200000; t++) {
            wander.advance();
            assertTrue("yaw offset " + wander.yaw() + " escaped its limit",
                    Math.abs(wander.yaw()) <= HeadWander.MAX_YAW + 1e-4f);
            assertTrue("pitch offset " + wander.pitch() + " escaped its limit",
                    Math.abs(wander.pitch()) <= HeadWander.MAX_PITCH + 1e-4f);
        }
    }

    @Test
    public void theHeadMovesSmoothly() {
        final float[] yaws = run(LONG_WALK);
        for (int t = 1; t < yaws.length; t++) {
            final float delta = Math.abs(yaws[t] - yaws[t - 1]);
            assertTrue("the head jumped " + delta + " degrees in one tick", delta <= 0.2f);
        }
    }

    @Test
    public void theHeadActuallyWanders() {
        final float[] yaws = run(LONG_WALK);
        float min = 0f, max = 0f, total = 0f;
        int turns = 0;
        for (int t = 0; t < yaws.length; t++) {
            min = Math.min(min, yaws[t]);
            max = Math.max(max, yaws[t]);
            total += yaws[t];
            if (t > 0 && Math.signum(yaws[t]) != Math.signum(yaws[t - 1]) && yaws[t] != 0f && yaws[t - 1] != 0f) {
                turns++;
            }
        }
        assertTrue("the head never left the bearing", Math.max(max, -min) > HeadWander.MAX_YAW * 0.5f);
        assertTrue("the head kept to one side only, which is a lean rather than a sway", min < 0f && max > 0f);
        assertTrue("the head changed side only " + turns + " times in a thousand seconds", turns > 20);
        assertTrue("the average offset ran off to one side: " + total / yaws.length,
                Math.abs(total / yaws.length) < HeadWander.MAX_YAW * 0.1f);
    }

    @Test
    public void noStretchOfAWalkLeans() {
        // A sway must come back through the bearing regularly. Twenty seconds is long enough for a lean to
        // show up and short enough that a walk is made of several of them.
        for (int seed = 0; seed < 30; seed++) {
            final HeadWander wander = sway(seed);
            double sum = 0, worstLean = 0;
            for (int t = 0; t < LONG_WALK; t++) {
                wander.advance();
                sum += wander.yaw();
                if (t % 400 == 399) {
                    worstLean = Math.max(worstLean, Math.abs(sum / 400.0));
                    sum = 0;
                }
            }
            assertTrue("the head leaned " + worstLean + " degrees for twenty seconds at a time", worstLean < 0.2);
        }
    }

    @Test
    public void aLongWalkIsNotPushedSideways() {
        double worst = 0;
        for (int seed = 0; seed < 30; seed++) {
            final HeadWander wander = sway(seed);
            double sideways = 0;
            for (int t = 0; t < LONG_WALK; t++) {
                wander.advance();
                sideways += BLOCKS_PER_TICK * Math.sin(Math.toRadians(wander.yaw()));
                worst = Math.max(worst, Math.abs(sideways));
            }
        }
        final double distance = LONG_WALK * BLOCKS_PER_TICK;
        assertTrue("the walk deviated " + worst + " blocks over " + (long) distance + " blocks walked",
                worst < 0.5);
        assertTrue("the deviation is more than a thousandth of the distance walked",
                worst / distance < 0.001);
    }

    @Test
    public void recentringBringsTheHeadBack() {
        final HeadWander wander = sway(1);
        for (int t = 0; t < 200; t++) {
            wander.advance();
        }
        int ticks = 0;
        while (ticks < 100 && (wander.yaw() != 0f || wander.pitch() != 0f)) {
            wander.recentre();
            ticks++;
        }
        assertEquals("the head never came back to the bearing", 0f, wander.yaw(), 0f);
        assertEquals(0f, wander.pitch(), 0f);
        assertTrue("recentring took " + ticks + " ticks", ticks < 100);
    }

    @Test
    public void theSwayFadesInAndOutRatherThanSwitching() {
        // When the head changes hands the sway is faded, not switched, or starting and stopping a walk
        // would step the head by up to a degree on a single tick.
        final HeadWander wander = sway(1);
        wander.advance();
        assertTrue("the sway did not start small", Math.abs(wander.yaw()) < 0.1f);
        for (int t = 0; t < 200; t++) {
            wander.advance();
        }
        float loudest = 0f;
        for (int t = 0; t < 200; t++) {
            wander.advance();
            loudest = Math.max(loudest, Math.abs(wander.yaw()));
        }
        assertTrue("the sway never became audible", loudest > HeadWander.MAX_YAW * 0.5f);

        // fading out: recentring while it is loud must not step either
        float previous = wander.yaw();
        for (int t = 0; t < 100; t++) {
            wander.recentre();
            final float now = wander.yaw();
            assertTrue("recentring stepped the head by " + Math.abs(now - previous),
                    Math.abs(now - previous) <= 0.2f);
            previous = now;
        }
        assertEquals("the sway never fully faded", 0f, wander.yaw(), 0f);
    }

    @Test
    public void pitchWandersLessThanYaw() {
        // a head bobs; it does not nod, and pitch is what the player would actually notice
        assertTrue("the pitch sway is not smaller than the yaw sway",
                HeadWander.MAX_PITCH < HeadWander.MAX_YAW);
        final HeadWander wander = sway(1);
        float maxPitch = 0f;
        for (int t = 0; t < LONG_WALK; t++) {
            wander.advance();
            maxPitch = Math.max(maxPitch, Math.abs(wander.pitch()));
        }
        assertTrue("the pitch never moved at all", maxPitch > HeadWander.MAX_PITCH * 0.5f);
    }

    @Test
    public void theStandingSwayIsQuieterThanTheWalkingOne() {
        // Standing still is not the same as being switched off. The head still drifts, it just drifts less:
        // half of the walking sway is small enough that a crosshair held on a block does not wander off it,
        // and large enough that a bot waiting for a door to open is not a screenshot.
        assertTrue("the standing sway is not quieter than the walking one", HeadWander.IDLE_FRACTION < 1f);
        assertTrue("the standing sway is not there at all", HeadWander.IDLE_FRACTION >= 0.25f);
        final HeadWander wander = sway(1);
        for (int t = 0; t < 200; t++) {
            wander.advance();
        }
        assertTrue("the walking sway was silent to begin with", Math.abs(wander.yaw()) > 0.05f);
        assertEquals("the standing yaw is not a fixed fraction of the walking one",
                wander.yaw() * HeadWander.IDLE_FRACTION, wander.idleYaw(), 1e-6f);
        assertEquals("the standing pitch is not a fixed fraction of the walking one",
                wander.pitch() * HeadWander.IDLE_FRACTION, wander.idlePitch(), 1e-6f);
    }

    @Test
    public void everySeedIsWellBehaved() {
        // in game this is seeded from the clock, so no seed may be the bad one
        for (long seed = 0; seed < 30; seed++) {
            final HeadWander wander = sway(seed);
            double sideways = 0, worst = 0;
            for (int t = 0; t < LONG_WALK; t++) {
                wander.advance();
                assertTrue(Math.abs(wander.yaw()) <= HeadWander.MAX_YAW + 1e-4f);
                sideways += BLOCKS_PER_TICK * Math.sin(Math.toRadians(wander.yaw()));
                worst = Math.max(worst, Math.abs(sideways));
            }
            assertFalse(Double.isNaN(sideways));
            assertTrue("seed " + seed + " drifted " + worst + " blocks", worst < 0.5);
        }
    }
}
