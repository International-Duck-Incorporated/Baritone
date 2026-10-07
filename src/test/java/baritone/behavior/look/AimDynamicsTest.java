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
 * Covers the rules that make the aim move like a hand rather than a cursor: speed limits, easing,
 * settling exactly, the short way around the wrap point, the spin-up, and the wandering offset.
 * <p>
 * These are the properties that make the difference between a bot and a person visible from outside, so
 * they are worth failing a build over.
 */
public class AimDynamicsTest {

    private static final double SENSITIVITY = 0.5; // default in game

    private static float onePixel() {
        return Math.abs(AimDynamics.mouseToAngle(SENSITIVITY, 1));
    }

    /**
     * Runs a turn and reports how many ticks until the aim is within a degree (what anyone can see) and
     * how many until it is exactly on target (the sub-pixel settle). Fails the test if it never lands.
     */
    private static int[] profile(float startYaw, float targetYaw, int rampStart) {
        Rotation aim = new Rotation(startYaw, 0f);
        final Rotation to = new Rotation(targetYaw, 0f);
        int age = rampStart, visible = -1, exact = -1;
        for (int t = 1; t <= 200; t++) {
            aim = AimDynamics.humanStep(aim, to, age, SENSITIVITY);
            age++;
            final float err = Math.abs(Rotation.normalizeYaw(targetYaw - aim.getYaw()));
            if (visible < 0 && err <= 1.0f) {
                visible = t;
            }
            if (exact < 0 && err <= 1e-4f) {
                exact = t;
                break;
            }
        }
        assertTrue("turn from " + startYaw + " to " + targetYaw + " never landed", exact > 0);
        return new int[]{visible, exact};
    }

    private static Rotation trace(Rotation start, Rotation target, int ticks) {
        Rotation aim = start;
        for (int t = 0; t < ticks; t++) {
            aim = AimDynamics.humanStep(aim, target, t, SENSITIVITY);
        }
        return aim;
    }

    @Test
    public void noSingleTickMovesFasterThanAFlick() {
        // Every sensitivity, because the whole-mouse-pixel snap is what decides whether the ceiling is
        // respected: a pixel of 0.45 degrees against a cap of 18 rounds up to 18.07, and this used to be
        // assertion-passing only at the sensitivities whose pixel happened to divide the cap.
        for (double sensitivity : new double[]{0.0, 0.05, 0.5, 0.87, 1.0}) {
            Rotation aim = new Rotation(0f, 0f);
            final Rotation to = new Rotation(170f, 80f);
            for (int t = 0; t < 60; t++) {
                final Rotation next = AimDynamics.humanStep(aim, to, t, sensitivity);
                assertTrue("yaw jumped " + Math.abs(Rotation.normalizeYaw(next.getYaw() - aim.getYaw()))
                                + " deg in one tick at sensitivity " + sensitivity,
                        Math.abs(Rotation.normalizeYaw(next.getYaw() - aim.getYaw())) <= AimDynamics.MAX_YAW_STEP + 1e-3f);
                assertTrue("pitch jumped " + Math.abs(next.getPitch() - aim.getPitch())
                                + " deg in one tick at sensitivity " + sensitivity,
                        Math.abs(next.getPitch() - aim.getPitch()) <= AimDynamics.MAX_PITCH_STEP + 1e-3f);
                aim = next;
            }
        }
    }

    @Test
    public void aTurnThatEndsInAClickHurriesWithoutSnapping() {
        // Everything that breaks or places a block holds the click back until the crosshair is on it, so a
        // slow hand there is paid for in ticks standing in front of a block already decided on. A hand on
        // its way to something it is about to click goes, rather than drifting there - but the ceiling still
        // holds, because a hurry is not a teleport, and a hand that crosses ninety degrees in one tick is
        // the thing this whole file exists to prevent.
        final Rotation to = new Rotation(90f, 40f);
        Rotation aim = new Rotation(0f, 0f);
        int hurried = 0;
        while (hurried < 60 && (Rotation.normalizeYaw(to.getYaw() - aim.getYaw()) != 0f || aim.getPitch() != to.getPitch())) {
            final Rotation next = AimDynamics.humanStep(aim, to, hurried, SENSITIVITY, AimDynamics.CLICK_EASE);
            assertTrue("a clicking hand moved " + Math.abs(Rotation.normalizeYaw(next.getYaw() - aim.getYaw()))
                            + " degrees of yaw in one tick",
                    Math.abs(Rotation.normalizeYaw(next.getYaw() - aim.getYaw())) <= AimDynamics.MAX_YAW_STEP + 1e-3f);
            assertTrue("a clicking hand moved " + Math.abs(next.getPitch() - aim.getPitch())
                            + " degrees of pitch in one tick",
                    Math.abs(next.getPitch() - aim.getPitch()) <= AimDynamics.MAX_PITCH_STEP + 1e-3f);
            aim = next;
            hurried++;
        }
        assertEquals("the clicking hand never arrived on yaw", to.getYaw(), aim.getYaw(), 1e-4f);
        assertEquals("the clicking hand never arrived on pitch", to.getPitch(), aim.getPitch(), 1e-4f);
        assertTrue("a right angle took " + hurried + " ticks with a click waiting at the end of it",
                hurried <= 8);
        assertTrue("a turn of " + hurried + " ticks is a snap, not a hurry", hurried >= 3);

        aim = new Rotation(0f, 0f);
        int leisurely = 0;
        while (leisurely < 60 && (Rotation.normalizeYaw(to.getYaw() - aim.getYaw()) != 0f || aim.getPitch() != to.getPitch())) {
            aim = AimDynamics.humanStep(aim, to, leisurely, SENSITIVITY);
            leisurely++;
        }
        assertTrue("the hurry has to actually be quicker than the ordinary aim: " + hurried + " vs " + leisurely,
                hurried < leisurely);
    }

    @Test
    public void turnsTakeAHumanAmountOfTime() {
        // 20 ticks is one second. A correction should land inside a fraction of one, and a reversal
        // should not take longer than a person needs to physically turn around.
        assertVisibleTime(20f, 100, 400);
        assertVisibleTime(45f, 150, 500);
        assertVisibleTime(90f, 300, 700);
        assertVisibleTime(180f, 500, 1000);
    }

    private static void assertVisibleTime(float degrees, int minMs, int maxMs) {
        final int[] p = profile(degrees, 0f, 0);
        final int ms = p[0] * 50;
        assertTrue(degrees + " deg turn finished in " + ms + "ms, expected at least " + minMs,
                ms >= minMs);
        assertTrue(degrees + " deg turn finished in " + ms + "ms, expected at most " + maxMs,
                ms <= maxMs);
    }

    @Test
    public void biggerTurnsAreNeverQuicker() {
        int previous = 0;
        for (float degrees : new float[]{20f, 45f, 90f, 180f}) {
            final int ticks = profile(degrees, 0f, 0)[0];
            assertTrue(degrees + " deg turn was quicker than the previous one", ticks >= previous);
            previous = ticks;
        }
    }

    @Test
    public void theAimSettlesExactlyAndDoesNotOvershoot() {
        final Rotation start = new Rotation(20f, 6f);
        final Rotation target = new Rotation(0f, 0f);
        final int settled = profile(20f, 0f, 0)[1];
        Rotation aim = start;
        float previousError = Math.abs(Rotation.normalizeYaw(target.getYaw() - aim.getYaw()));
        for (int t = 0; t < settled; t++) {
            aim = AimDynamics.humanStep(aim, target, t, SENSITIVITY);
            final float error = Math.abs(Rotation.normalizeYaw(target.getYaw() - aim.getYaw()));
            assertTrue("the aim moved away from the target at tick " + t, error <= previousError + 1e-4f);
            previousError = error;
        }
        assertEquals("yaw should land exactly on target", target.getYaw(), aim.getYaw(), 1e-4f);
        assertEquals("pitch should land exactly on target", target.getPitch(), aim.getPitch(), 1e-4f);
    }

    @Test
    public void aReachedTargetProducesNoMovementAtAll() {
        final Rotation aim = new Rotation(10f, 5f);
        for (int t = 0; t < 20; t++) {
            final Rotation next = AimDynamics.humanStep(aim, new Rotation(10f, 5f), t, SENSITIVITY);
            assertEquals(10f, next.getYaw(), 0f);
            assertEquals(5f, next.getPitch(), 0f);
        }
    }

    @Test
    public void aResidualSmallerThanOnePixelIsTakenInFull() {
        final float min = onePixel();
        final Rotation next = AimDynamics.humanStep(new Rotation(10f, 5f), new Rotation(10f + min * 0.4f, 5f), 0, SENSITIVITY);
        assertEquals("a sub-pixel remainder should be taken, not crept toward",
                10f + min * 0.4f, next.getYaw(), 1e-5f);
    }

    @Test
    public void turningAcrossTheWrapPointGoesTheShortWay() {
        // 170 to -170 is a 20 degree movement. It used to travel 340 degrees the long way round, because
        // the delta was never wrapped.
        final Rotation start = new Rotation(170f, 0f);
        final Rotation target = new Rotation(-170f, 0f);
        final int settled = profile(170f, -170f, 0)[1];
        Rotation aim = start;
        float travelled = 0f;
        for (int t = 0; t < settled; t++) {
            final Rotation next = AimDynamics.humanStep(aim, target, t, SENSITIVITY);
            travelled += Math.abs(Rotation.normalizeYaw(next.getYaw() - aim.getYaw()));
            aim = next;
        }
        assertTrue("crosshair travelled " + travelled + " deg for a 20 deg movement", travelled < 30f);
        assertEquals("the wrapped turn should cost the same as a plain 20 deg turn",
                profile(20f, 0f, 0)[0], profile(170f, -170f, 0)[0], 2);
    }

    @Test
    public void aTurnSpinsUpPeaksThenEasesOff() {
        final Rotation to = new Rotation(180f, 0f);
        final int settled = profile(0f, 180f, 0)[1];
        final float[] steps = new float[settled + 1];
        Rotation aim = new Rotation(0f, 0f);
        steps[0] = 0f;
        for (int t = 0; t < settled; t++) {
            final Rotation next = AimDynamics.humanStep(aim, to, t, SENSITIVITY);
            steps[t + 1] = Math.abs(Rotation.normalizeYaw(next.getYaw() - aim.getYaw()));
            aim = next;
        }
        float peak = 0f;
        int peakTick = 0;
        for (int i = 1; i <= settled; i++) {
            if (steps[i] > peak) {
                peak = steps[i];
                peakTick = i;
            }
        }
        assertTrue("the first tick should be slower than the peak", steps[1] < peak);
        assertTrue("the peak should arrive after the start, not on tick one", peakTick > 1);
        assertTrue("the peak should respect the ceiling", peak <= AimDynamics.MAX_YAW_STEP + 0.01f);
        for (int i = peakTick + 2; i <= settled; i++) {
            assertTrue("the movement sped up again at tick " + i + " after peaking",
                    steps[i] <= steps[i - 1] + onePixel() + 1e-4f);
        }
    }

    @Test
    public void theRampIsCappedRatherThanIndexingOutOfBounds() {
        assertEquals(AimDynamics.TURN_RAMP[0], AimDynamics.ramp(0), 0f);
        assertEquals(AimDynamics.TURN_RAMP[AimDynamics.TURN_RAMP.length - 1],
                AimDynamics.ramp(AimDynamics.TURN_RAMP.length), 0f);
        assertEquals("a very long turn should hold at full speed",
                AimDynamics.TURN_RAMP[AimDynamics.TURN_RAMP.length - 1], AimDynamics.ramp(10000), 0f);
        assertEquals("a negative age should behave like a fresh turn", AimDynamics.TURN_RAMP[0], AimDynamics.ramp(-5), 0f);
    }

    @Test
    public void distanceBetweenRotationsIsMeasuredTheShortWay() {
        assertEquals(20.0f, AimDynamics.separation(new Rotation(170f, 0f), new Rotation(-170f, 0f)), 0.01f);
        assertEquals(180.0f, AimDynamics.separation(new Rotation(90f, 0f), new Rotation(-90f, 0f)), 0.01f);
        assertEquals(0.0f, AimDynamics.separation(new Rotation(-45f, 0f), new Rotation(315f, 0f)), 0.01f);
        // a movement counts as much as a turn: the larger of the two axes
        assertEquals(60.0f, AimDynamics.separation(new Rotation(0f, 0f), new Rotation(10f, 60f)), 0.01f);
        // pitch is not cyclic: only yaw wraps, so a pitch difference is just a difference
        assertEquals(145.0f, AimDynamics.separation(new Rotation(0f, -100f), new Rotation(0f, 45f)), 0.01f);
    }

    @Test
    public void theWanderingOffsetDriftsInsteadOfShivering() {
        // Fed a constant direction of noise the offset should approach it smoothly, and the gain belongs
        // to the caller rather than being baked into the smoothing.
        double offset = 0;
        final double drawn = 1.0;
        int ticksToSettle = 0;
        for (int t = 0; t < 200; t++) {
            offset = AimDynamics.smoothOffset(offset, drawn);
            ticksToSettle++;
            if (offset > 0.95f) {
                break;
            }
        }
        assertTrue("a constant offset should be approached, not jumped to", ticksToSettle > 5);
        assertTrue("reaching a constant offset should not take forever", ticksToSettle < 40);
        assertEquals("smoothing a constant should converge to it", 1.0f, offset, 0.05f);
        // alternating noise must not be passed through at full amplitude, which is the whole point
        double alternating = 0, maxExcursion = 0;
        for (int t = 0; t < 200; t++) {
            alternating = AimDynamics.smoothOffset(alternating, t % 2 == 0 ? 1.0 : -1.0);
            maxExcursion = Math.max(maxExcursion, Math.abs(alternating));
        }
        assertTrue("per-tick noise was passed through at full amplitude (" + maxExcursion + ")",
                maxExcursion < 0.75f);
    }

    @Test
    public void sensitivityScalesTheStepButNotTheProfile() {
        // A low-sensitivity player moves in smaller mouse pixels; the turn should still complete in a
        // comparable number of ticks rather than crawling.
        final double low = 0.1, high = 1.0;
        assertTrue("a pixel at low sensitivity should be smaller than at high",
                Math.abs(AimDynamics.mouseToAngle(low, 1)) <= Math.abs(AimDynamics.mouseToAngle(high, 1)));
        for (double sensitivity : new double[]{0.1, 0.5, 1.0}) {
            Rotation aim = new Rotation(45f, 0f);
            final Rotation to = new Rotation(0f, 0f);
            int ticks = 0;
            while (Math.abs(Rotation.normalizeYaw(to.getYaw() - aim.getYaw())) > 1e-4f && ticks < 100) {
                aim = AimDynamics.humanStep(aim, to, ticks, sensitivity);
                ticks++;
            }
            assertTrue("45 deg at sensitivity " + sensitivity + " took " + ticks + " ticks", ticks > 2 && ticks < 40);
        }
    }

    /** The default of {@code randomLooking113}, which is what shakes the aim and what these tests model. */
    private static final double TREMOR_SETTING = 2.0;

    /** A thousand seconds of a settled aim. */
    private static final int SETTLED_RUN = 20000;

    /**
     * One tick's draw of the aim shake, as {@code LookBehavior.tick} makes it: a flat draw scaled by the
     * setting, with small draws spread out rather than left bunched at nothing, plus the tiny second draw
     * from {@code randomLooking}. Modelled rather than called because the behaviour that makes it needs a
     * Minecraft client; the shape is the part that matters here.
     */
    private static double tremorDraw(Random rand) {
        double raw = rand.nextDouble() - 0.5;
        if (Math.abs(raw) < 0.1) {
            raw *= 4;
        }
        return raw * TREMOR_SETTING + (rand.nextDouble() - 0.5) * 0.01;
    }

    /**
     * The gate the clicking code asks, run against a hand that is eased and shaken exactly the way the
     * behaviour eases and shakes it.
     * <p>
     * This is the test that would have caught the gates going dead. Every one of them asked whether the
     * player's rotation was within a hundredth of a degree of the target - true while the aim was set
     * straight to the target, as it was when they were written, and answered by luck once a tick in seventy
     * after the aim learned to ease and to shake. A blocked movement would then wait for the ray to come
     * right, which it cannot when the thing it wants to look at is behind another block, and the movement
     * would time out instead of breaking through.
     */
    @Test
    public void theArrivalGateSurvivesTheTremor() {
        final Rotation target = new Rotation(-90f, 20f);
        final Random rand = new Random(11);
        Rotation aim = target;
        double offset = 0.0;
        int settled = 0;
        int arrived = 0;
        int exact = 0;
        float worst = 0.0f;
        for (int t = 0; t < SETTLED_RUN; t++) {
            offset = AimDynamics.smoothOffset((float) offset, (float) tremorDraw(rand));
            final Rotation wanted = new Rotation(
                    target.getYaw() + (float) offset * AimDynamics.TREMOR_GAIN,
                    target.getPitch());
            aim = AimDynamics.humanStep(aim, wanted, 40, SENSITIVITY);
            // what the aim processor would answer if the gate asked it what to do with this target: a step
            // toward it, which for a settled hand is where the hand already is and one mouse step at most
            final Rotation probed = AimDynamics.humanStep(aim, wanted, 40, SENSITIVITY, AimDynamics.CLICK_EASE);
            if (t > 40) {
                settled++;
                if (AimDynamics.arrived(aim, probed)) {
                    arrived++;
                }
                if (AimDynamics.separation(aim, target) < 0.01f) {
                    exact++;
                }
                worst = Math.max(worst, AimDynamics.separation(aim, probed));
            }
        }
        assertEquals("the gate missed a settled hand on " + (settled - arrived) + " ticks", settled, arrived);
        assertTrue("a settled hand was " + worst + " degrees from its aim", worst <= AimDynamics.ARRIVED_ANGLE);
        // for the record, the question the gates used to ask: the angles coincide on almost no tick at all
        assertTrue("the hundredth of a degree test was answered " + exact + " times", exact < settled / 20);
    }

    @Test
    public void aHandOnItsWayHasNotArrivedAndASettledOneHas() {
        final Rotation wanted = new Rotation(45f, 0f);
        // a hand that has arrived sits at the spread the tremor leaves it at
        assertTrue(AimDynamics.arrived(new Rotation(45f, 0f), wanted));
        assertTrue(AimDynamics.arrived(new Rotation(44.58f, 0f), wanted));
        assertTrue(AimDynamics.arrived(new Rotation(45f, 0.5f), wanted));
        // a hand that is still travelling is a step away, and the step is eighteen degrees
        assertFalse(AimDynamics.arrived(new Rotation(27f, 0f), wanted));
        assertFalse(AimDynamics.arrived(new Rotation(45f, -18f), wanted));
        // and the tolerance is a scale for the tremor, not a licence to click from anywhere
        assertFalse(AimDynamics.arrived(new Rotation(42f, 0f), wanted));
    }

    @Test
    public void theAimIsAlwaysWithinUsableBounds() {
        Rotation aim = new Rotation(0f, 89f);
        for (int t = 0; t < 40; t++) {
            aim = AimDynamics.humanStep(aim, new Rotation(0f, -200f), t, SENSITIVITY);
            assertTrue("pitch escaped its range: " + aim.getPitch(),
                    aim.getPitch() >= -90f && aim.getPitch() <= 90f);
            assertTrue("yaw escaped its range: " + aim.getYaw(),
                    aim.getYaw() >= -180f && aim.getYaw() <= 180f);
        }
    }
}
