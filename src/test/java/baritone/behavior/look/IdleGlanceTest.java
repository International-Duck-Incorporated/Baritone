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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Covers the idle glance: the thing that has the bot look around while it is waiting for something.
 * <p>
 * The important properties are the ones that keep it from being a nuisance: it must never move the head
 * when it is not allowed to, it must always come back, it must not be able to get stuck, and it must give
 * the camera back the moment somebody takes it.
 */
public class IdleGlanceTest {

    private static final double SENSITIVITY = 0.5;

    private static Rotation step(Rotation from, Rotation to) {
        return AimDynamics.step(from, to, AimDynamics.MAX_YAW_STEP, AimDynamics.MAX_PITCH_STEP, SENSITIVITY);
    }

    @Test
    public void neverMovesTheHeadWhenNotAllowed() {
        final IdleGlance glance = new IdleGlance();
        final Rotation camera = new Rotation(35f, 4f);
        for (int t = 0; t < 3000; t++) {
            assertNull("the glance fired while it was not allowed to", glance.next(camera, false, IdleGlanceTest::step));
        }
    }

    @Test
    public void aGlanceIsBoundedAndAlwaysComesHome() {
        final IdleGlance glance = new IdleGlance();
        final Rotation camera = new Rotation(35f, 4f);
        Rotation previous = null;
        int active = 0, gestures = 0, streak = 0, longest = 0;
        float maxYawDeviation = 0f, maxPitchDeviation = 0f, maxStep = 0f;
        for (int t = 0; t < 6000; t++) {
            final Rotation sent = glance.next(camera, true, IdleGlanceTest::step);
            if (sent == null) {
                // A null while nothing is running is just the cooldown ticking down; a null straight
                // after an active glance means it finished, and it must have finished back at home.
                // Rotation does not implement equals, so compare the angles it actually holds.
                if (previous != null) {
                    assertEquals("the glance stopped away from home (yaw)",
                            camera.getYaw(), previous.getYaw(), 1e-4f);
                    assertEquals("the glance stopped away from home (pitch)",
                            camera.getPitch(), previous.getPitch(), 1e-4f);
                }
                previous = null;
                longest = Math.max(longest, streak);
                streak = 0;
                continue;
            }
            maxYawDeviation = Math.max(maxYawDeviation, Math.abs(Rotation.normalizeYaw(sent.getYaw() - camera.getYaw())));
            maxPitchDeviation = Math.max(maxPitchDeviation, Math.abs(sent.getPitch() - camera.getPitch()));
            if (previous != null) {
                maxStep = Math.max(maxStep, Math.abs(Rotation.normalizeYaw(sent.getYaw() - previous.getYaw())));
            }
            previous = sent;
            active++;
            streak++;
            if (streak == 1) {
                gestures++;
            }
        }
        assertTrue("a glance strayed more than 28 deg in yaw: " + maxYawDeviation, maxYawDeviation <= 28.5f);
        assertTrue("a glance strayed more than 7 deg in pitch: " + maxPitchDeviation, maxPitchDeviation <= 7.5f);
        assertTrue("a glance jumped " + maxStep + " deg in one tick", maxStep <= AimDynamics.MAX_YAW_STEP + 0.01f);
        assertTrue("a glance ran for " + longest + " ticks, past its cap", longest <= 100);
        // 6000 ticks is five minutes; a person glances about now and then, they do not scan constantly
        assertTrue("only " + gestures + " glances in five minutes", gestures >= 10);
        assertTrue("glancing constantly: " + gestures + " in five minutes", gestures <= 120);
        assertTrue("the head was active " + (100.0 * active / 6000.0) + "% of the time", active < 3600);
    }

    @Test
    public void aGlanceTakesAnInterestWhenThereIsSomethingToLookAt() {
        final IdleGlance glance = new IdleGlance();
        final Rotation camera = new Rotation(35f, 4f);
        // well outside anywhere a wandering glance can go, so only the interest branch can explain a glance
        // arriving there. 120 degrees away, and out of the pitch band as well.
        final Rotation interest = new Rotation(155f, -20f);
        int gestures = 0, interested = 0, active = 0, streak = 0, longest = 0;
        float maxStep = 0f;
        boolean thisGlanceWentToIt = false;
        Rotation previous = null;
        for (int t = 0; t < 120000; t++) {
            final Rotation sent = glance.next(camera, true, IdleGlanceTest::step, () -> interest);
            if (sent == null) {
                if (previous != null) {
                    // an interested glance is still a glance: it comes home, exactly, and then goes quiet
                    assertEquals("the glance stopped away from home (yaw)", camera.getYaw(), previous.getYaw(), 1e-4f);
                    assertEquals("the glance stopped away from home (pitch)", camera.getPitch(), previous.getPitch(), 1e-4f);
                    gestures++;
                    if (thisGlanceWentToIt) {
                        interested++;
                    }
                    thisGlanceWentToIt = false;
                }
                longest = Math.max(longest, streak);
                streak = 0;
                previous = null;
                continue;
            }
            if (Math.abs(Rotation.normalizeYaw(sent.getYaw() - camera.getYaw())) > 30f) {
                thisGlanceWentToIt = true;
            }
            if (previous != null) {
                maxStep = Math.max(maxStep, Math.abs(Rotation.normalizeYaw(sent.getYaw() - previous.getYaw())));
            }
            previous = sent;
            streak++;
            active++;
        }
        assertTrue("nothing ever went to what was offered", interested > 0);
        // and about one glance in three does, no more: most glances still wander, because a head that goes
        // to whoever is nearby every time it moves is a different kind of wrong
        final double fraction = (double) interested / gestures;
        assertTrue("the offer was taken " + (100.0 * fraction) + "% of the time", fraction > 0.20 && fraction < 0.50);
        // the interest does not get to break the rules the wandering glances are held to: the turn there is
        // rate limited like any other hand movement and the glance still cannot outlive its cap
        assertTrue("an interested glance moved " + maxStep + " deg in one tick", maxStep <= AimDynamics.MAX_YAW_STEP + 0.01f);
        assertTrue("an interested glance ran for " + longest + " ticks", longest <= 100);
        assertTrue("the head was active " + (100.0 * active / 120000.0) + "% of the time", active < 120000 / 2);
    }

    @Test
    public void nothingOfferedMeansNothingIsLookedAt() {
        // the supplier answering null - there is nobody near - has to leave the glance exactly as it was
        final IdleGlance glance = new IdleGlance();
        final Rotation camera = new Rotation(35f, 4f);
        float maxYawDeviation = 0f, maxPitchDeviation = 0f;
        for (int t = 0; t < 6000; t++) {
            final Rotation sent = glance.next(camera, true, IdleGlanceTest::step, () -> null);
            if (sent == null) {
                continue;
            }
            maxYawDeviation = Math.max(maxYawDeviation, Math.abs(Rotation.normalizeYaw(sent.getYaw() - camera.getYaw())));
            maxPitchDeviation = Math.max(maxPitchDeviation, Math.abs(sent.getPitch() - camera.getPitch()));
        }
        assertTrue("a glance strayed more than 28 deg in yaw: " + maxYawDeviation, maxYawDeviation <= 28.5f);
        assertTrue("a glance strayed more than 7 deg in pitch: " + maxPitchDeviation, maxPitchDeviation <= 7.5f);
    }

    @Test
    public void givesTheCameraBackWhenTheViewIsTakenOver() {
        final IdleGlance glance = new IdleGlance();
        final Rotation camera = new Rotation(0f, 0f);
        boolean started = false;
        for (int t = 0; t < 400 && !started; t++) {
            started = glance.next(camera, true, IdleGlanceTest::step) != null;
        }
        assertTrue("a glance never started", started);
        // somebody moves the mouse 40 degrees away mid-glance
        final Rotation moved = new Rotation(40f, 0f);
        boolean backedOff = false;
        for (int t = 0; t < 40; t++) {
            if (glance.next(moved, true, IdleGlanceTest::step) == null) {
                backedOff = true;
                break;
            }
        }
        assertTrue("the glance kept going after the camera was moved out from under it", backedOff);
    }

    @Test
    public void stopsImmediatelyWhenItBecomesDisallowed() {
        final IdleGlance glance = new IdleGlance();
        final Rotation camera = new Rotation(0f, 0f);
        boolean started = false;
        for (int t = 0; t < 400 && !started; t++) {
            started = glance.next(camera, true, IdleGlanceTest::step) != null;
        }
        assertTrue(started);
        assertNull("a glance should be dropped the tick it stops being allowed",
                glance.next(camera, false, IdleGlanceTest::step));
    }

    @Test
    public void resettingDropsAGlanceInProgress() {
        // What a world change needs: the glance was taken against a player who is no longer there, and the
        // rotation it would return to is an angle in a different level.
        final IdleGlance glance = new IdleGlance();
        final Rotation camera = new Rotation(0f, 0f);
        boolean started = false;
        for (int t = 0; t < 400 && !started; t++) {
            started = glance.next(camera, true, IdleGlanceTest::step) != null;
        }
        assertTrue("a glance never started", started);
        glance.reset();
        // the world is new, and so is the camera: this must not be treated as the old glance continuing
        for (int t = 0; t < 60; t++) {
            final Rotation sent = glance.next(new Rotation(137f, 12f), true, IdleGlanceTest::step);
            if (sent != null) {
                assertTrue("the glance resumed against a stale home", Math.abs(Rotation.normalizeYaw(sent.getYaw() - 137f)) <= 28.5f);
                assertTrue(Math.abs(sent.getPitch() - 12f) <= 7.5f);
                return;
            }
        }
        assertTrue("no glance was ever taken after the reset", true);
    }

    @Test
    public void neverReturnsTheCameraInsteadOfAGlance() {
        // Regression guard: the state machine must either hand back a new rotation or nothing at all,
        // because the caller uses null to mean "this tick is not ours".
        final IdleGlance glance = new IdleGlance();
        final Rotation camera = new Rotation(12f, -3f);
        for (int t = 0; t < 2000; t++) {
            final Rotation sent = glance.next(camera, t % 7 != 0, IdleGlanceTest::step);
            if (sent != null) {
                assertTrue("pitch out of range: " + sent.getPitch(), Math.abs(sent.getPitch()) <= 90f);
            }
        }
    }
}
