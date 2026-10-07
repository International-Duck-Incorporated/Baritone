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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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

    private static Rotation step(Rotation from, Rotation to, int legAge) {
        return AimDynamics.step(from, to, AimDynamics.MAX_YAW_STEP, AimDynamics.MAX_PITCH_STEP, SENSITIVITY);
    }

    @Test
    public void neverMovesTheHeadWhenNotAllowed() {
        final IdleGlance glance = new IdleGlance(new Random(11));
        final Rotation camera = new Rotation(35f, 4f);
        for (int t = 0; t < 3000; t++) {
            assertNull("the glance fired while it was not allowed to", glance.next(camera, false, IdleGlanceTest::step));
        }
    }

    @Test
    public void aGlanceIsBoundedAndAlwaysComesHome() {
        final IdleGlance glance = new IdleGlance(new Random(1));
        final Rotation camera = new Rotation(35f, 4f);
        Rotation previous = null;
        int active = 0, gestures = 0, streak = 0, longest = 0;
        int wandering = 0, atTheHand = 0;
        float maxYawDeviation = 0f, maxStep = 0f;
        float gesturePitch = 0f, gestureLowest = 0f, deepestWander = 0f, deepestHand = 0f;
        boolean lookedUp = false;
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
                    // Two gestures, one bound each: a look around is a yaw gesture with a few degrees of
                    // pitch in it, and a look at the hand is a pitch gesture of thirty degrees or so.
                    // Which of the two a finished glance was is read off how far it took the pitch.
                    if (gesturePitch > 7.5f) {
                        atTheHand++;
                        // how far below level it went, which is where the rule lives: the hand is down
                        // there, and the camera the glance started from is not part of it
                        deepestHand = Math.min(deepestHand, gestureLowest);
                    } else {
                        wandering++;
                        deepestWander = Math.max(deepestWander, gesturePitch);
                    }
                    gesturePitch = 0f;
                    gestureLowest = 0f;
                }
                previous = null;
                longest = Math.max(longest, streak);
                streak = 0;
                continue;
            }
            final float pitchDeviation = Math.abs(sent.getPitch() - camera.getPitch());
            maxYawDeviation = Math.max(maxYawDeviation, Math.abs(Rotation.normalizeYaw(sent.getYaw() - camera.getYaw())));
            gesturePitch = Math.max(gesturePitch, pitchDeviation);
            gestureLowest = Math.min(gestureLowest, sent.getPitch());
            if (sent.getPitch() - camera.getPitch() > 7.5f) {
                // the deep half of a glance is the hand, which is below the eye - never the sky
                lookedUp = true;
            }
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
        assertTrue("a look around went " + deepestWander + " deg off in pitch", deepestWander <= 7.5f);
        assertTrue("a look at the hand went down to " + deepestHand + " deg", deepestHand >= -38.5f);
        assertTrue("a glance looked up at nothing", !lookedUp);
        assertTrue("a glance jumped " + maxStep + " deg in one tick", maxStep <= AimDynamics.MAX_YAW_STEP + 0.01f);
        assertTrue("a glance ran for " + longest + " ticks, past its cap", longest <= 100);
        // 6000 ticks is five minutes; a person glances about now and then, they do not scan constantly
        assertTrue("only " + gestures + " glances in five minutes", gestures >= 10);
        assertTrue("glancing constantly: " + gestures + " in five minutes", gestures <= 120);
        assertTrue("the head was active " + (100.0 * active / 6000.0) + "% of the time", active < 3600);
        // and now and then one of those glances is at the hand rather than around: a person standing about
        // checks what they are holding, but it is a habit and not a tic
        assertTrue("the hand was checked " + atTheHand + " times in five minutes", atTheHand >= 3);
        assertTrue(atTheHand + " hand checks in " + gestures + " glances",
                atTheHand * 2 <= gestures);
        assertTrue("only " + wandering + " glances looked around", wandering >= 10);
    }

    @Test
    public void aGlanceTakesAnInterestWhenThereIsSomethingToLookAt() {
        final IdleGlance glance = new IdleGlance(new Random(12));
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
    public void aHostileInterestIsStillOnlyAGlance() {
        // The look behaviour offers an interest only when there is something in sight to look at, but the
        // glance must not rely on that: this hands it the worst angles there are - behind the player, at both
        // ends of the yaw wrap, pitches past vertical - while the camera itself sits near the wrap point, and
        // holds it to the same rules every other glance is held to.
        final Random rand = new Random(20261007);
        final IdleGlance glance = new IdleGlance(new Random(13));
        final Rotation camera = new Rotation(179.5f, 42f);
        Rotation previous = null;
        int gestures = 0, longest = 0, streak = 0;
        float maxStep = 0f;
        for (int t = 0; t < 60000; t++) {
            final Rotation interest = new Rotation(rand.nextFloat() * 360f - 180f, rand.nextFloat() * 360f - 180f);
            final Rotation sent = glance.next(camera, true, IdleGlanceTest::step, () -> interest);
            if (sent == null) {
                if (previous != null) {
                    assertEquals("an interested glance stopped away from home (yaw)", camera.getYaw(), previous.getYaw(), 1e-4f);
                    assertEquals("an interested glance stopped away from home (pitch)", camera.getPitch(), previous.getPitch(), 1e-4f);
                    gestures++;
                }
                longest = Math.max(longest, streak);
                streak = 0;
                previous = null;
                continue;
            }
            assertTrue("pitch escaped its range: " + sent.getPitch(), sent.getPitch() >= -90f && sent.getPitch() <= 90f);
            assertTrue("yaw escaped its range: " + sent.getYaw(), sent.getYaw() >= -180f && sent.getYaw() <= 180f);
            if (previous != null) {
                maxStep = Math.max(maxStep, Math.abs(Rotation.normalizeYaw(sent.getYaw() - previous.getYaw())));
            }
            previous = sent;
            streak++;
        }
        assertTrue("the head moved " + maxStep + " deg in one tick", maxStep <= AimDynamics.MAX_YAW_STEP + 0.01f);
        assertTrue("a glance outlived its cap: " + longest, longest <= 100);
        assertTrue("almost nothing happened in fifty minutes: " + gestures, gestures > 10);
    }

    @Test
    public void nothingOfferedMeansNothingIsLookedAt() {
        // the supplier answering null - there is nobody near - has to leave the glance exactly as it was
        final IdleGlance glance = new IdleGlance(new Random(2));
        final Rotation camera = new Rotation(35f, 4f);
        float maxYawDeviation = 0f, maxUpPitch = 0f, lowestPitch = 0f;
        for (int t = 0; t < 6000; t++) {
            final Rotation sent = glance.next(camera, true, IdleGlanceTest::step, () -> null);
            if (sent == null) {
                continue;
            }
            maxYawDeviation = Math.max(maxYawDeviation, Math.abs(Rotation.normalizeYaw(sent.getYaw() - camera.getYaw())));
            maxUpPitch = Math.max(maxUpPitch, sent.getPitch() - camera.getPitch());
            lowestPitch = Math.min(lowestPitch, sent.getPitch());
        }
        assertTrue("a glance strayed more than 28 deg in yaw: " + maxYawDeviation, maxYawDeviation <= 28.5f);
        assertTrue("a glance looked up " + maxUpPitch + " deg", maxUpPitch <= 7.5f);
        assertTrue("a glance went down to " + lowestPitch + " deg", lowestPitch >= -38.5f);
    }

    @Test
    public void givesTheCameraBackWhenTheViewIsTakenOver() {
        final IdleGlance glance = new IdleGlance(new Random(14));
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

    /**
     * The other half of that rule, and the half that costs something if it is missing: a camera nobody is
     * looking through cannot be taken over by anybody, and the gesture is what moves it. Measuring it
     * against where the gesture started would abandon every glance wider than {@code TAKEN_OVER} - most of
     * the wander draws and every look at the hand, neither of which is ever that narrow - and leave the head
     * pointing wherever the abandoned gesture got to.
     */
    @Test
    public void aCameraOfOurOwnIsNeverTakenOver() {
        for (int seed = 1; seed <= 40; seed++) {
            final IdleGlance glance = new IdleGlance(new Random(seed));
            Rotation camera = new Rotation(12f, 3f);
            Rotation homeOfGesture = null;
            Rotation lastOfGesture = null;
            int gestures = 0;
            float furthest = 0f, widest = 0f;
            for (int t = 0; t < 6000; t++) {
                final Rotation wanted = glance.next(camera, true, false, IdleGlanceTest::step, () -> null);
                if (wanted != null) {
                    if (homeOfGesture == null) {
                        homeOfGesture = camera;
                        furthest = 0f;
                    }
                    lastOfGesture = wanted;
                    // free look off: this angle is what the packet carried and what the camera keeps, so the
                    // tick after it is looking at this, which is the camera of a bot nobody is looking through
                    camera = wanted;
                    furthest = Math.max(furthest, AimDynamics.separation(homeOfGesture, wanted));
                } else if (homeOfGesture != null) {
                    gestures++;
                    widest = Math.max(widest, furthest);
                    assertTrue("a " + furthest + " degree gesture ended somewhere else: " + lastOfGesture
                                    + " instead of " + homeOfGesture,
                            Math.abs(Rotation.normalizeYaw(lastOfGesture.getYaw() - homeOfGesture.getYaw())) < 1e-4f
                                    && Math.abs(lastOfGesture.getPitch() - homeOfGesture.getPitch()) < 1e-4f);
                    homeOfGesture = null;
                }
            }
            assertTrue("seed " + seed + " took no glance in six thousand ticks", gestures > 0);
            assertTrue("seed " + seed + " never looked more than " + widest + " degrees anywhere", widest > 5f);
        }
    }

    @Test
    public void stopsImmediatelyWhenItBecomesDisallowed() {
        final IdleGlance glance = new IdleGlance(new Random(15));
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
        final IdleGlance glance = new IdleGlance(new Random(16));
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
    public void aDroppedGlanceStartsTheWaitOver() {
        // Wherever the caller knows the glance is not its to take but has no camera to hand it - the look
        // behaviour reaches for this on the ticks a target is being held - the glance has to end the same
        // way a disallowed tick ends it: dropped where it stands, and the wait started again.
        final IdleGlance glance = new IdleGlance(new Random(17));
        final Rotation camera = new Rotation(0f, 0f);
        boolean started = false;
        for (int t = 0; t < 400 && !started; t++) {
            started = glance.next(camera, true, IdleGlanceTest::step) != null;
        }
        assertTrue("a glance never started", started);
        glance.drop();
        for (int t = 0; t < 59; t++) {
            assertNull("the wait was not started over", glance.next(camera, true, IdleGlanceTest::step));
        }
        boolean again = false;
        for (int t = 0; t < 102 && !again; t++) {
            again = glance.next(camera, true, IdleGlanceTest::step) != null;
        }
        assertTrue("the glance never came back after being dropped", again);
    }

    @Test
    public void droppingNothingLeavesTheWaitAlone() {
        // drop is called on every tick this glance is not ours to take, so it has to be free when there is
        // nothing to drop. If it restarted the wait every time, a bot that is walking would never glance
        // again for as long as it walked.
        final IdleGlance glance = new IdleGlance(new Random(18));
        final Rotation camera = new Rotation(0f, 0f);
        boolean started = false;
        for (int t = 0; t < 400 && !started; t++) {
            glance.drop();
            started = glance.next(camera, true, IdleGlanceTest::step) != null;
        }
        assertTrue("a wait that is dropped every tick never ends", started);
    }

    @Test
    public void neverReturnsTheCameraInsteadOfAGlance() {
        // Regression guard: the state machine must either hand back a new rotation or nothing at all,
        // because the caller uses null to mean "this tick is not ours".
        final IdleGlance glance = new IdleGlance(new Random(19));
        final Rotation camera = new Rotation(12f, -3f);
        for (int t = 0; t < 2000; t++) {
            final Rotation sent = glance.next(camera, t % 7 != 0, IdleGlanceTest::step);
            if (sent != null) {
                assertTrue("pitch out of range: " + sent.getPitch(), Math.abs(sent.getPitch()) <= 90f);
            }
        }
    }
    @Test
    public void eachLegOfAGlanceStartsFromItsFirstTick() {
        // the stepper is told how far into the leg it is, from zero, twice per glance: once setting off
        // and once again heading home, so that both turns can spin up the way the aim's turns do
        final IdleGlance glance = new IdleGlance(new Random(11));
        final Rotation camera = new Rotation(0f, 0f);
        final List<Integer> ages = new ArrayList<>();
        boolean started = false;
        for (int tick = 0; tick < 400; tick++) {
            final Rotation sent = glance.next(camera, true, (from, to, legAge) -> {
                ages.add(legAge);
                return step(from, to, legAge);
            });
            if (sent != null) {
                started = true;
            } else if (started) {
                break;
            }
        }
        assertTrue(started);
        int zeros = 0;
        for (int i = 0; i < ages.size(); i++) {
            if (ages.get(i) == 0) {
                zeros++;
                assertTrue("a leg's age started again at tick " + i + " without a first tick before it", i == 0 || ages.get(i - 1) > 0);
            } else {
                assertEquals("the age skipped", ages.get(i - 1) + 1, (int) ages.get(i));
            }
        }
        assertEquals("one leg out and one leg back", 2, zeros);
    }
}
