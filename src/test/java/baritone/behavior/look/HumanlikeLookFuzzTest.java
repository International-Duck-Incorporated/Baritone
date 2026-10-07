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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Drives all four look state machines together through a long, hostile run and asserts the properties the
 * game depends on, whatever the input.
 * <p>
 * The individual tests cover each rule on its own. This covers the things nobody thinks to write a rule
 * for: that nothing ever produces a NaN, that no state can be entered from which the aim stops moving, that
 * a target which never settles still gets arrived at, that the two behaviours that move the head can never
 * both be driving, and that no sequence of player states can leave one of them stuck on. Those are the
 * failures that would not look like a bug in a short playtest; they would look like Baritone being strange
 * a few minutes in.
 */
public class HumanlikeLookFuzzTest {

    private static final double[] SENSITIVITIES = {0.0, 0.05, 0.5, 0.87, 1.0};
    private static final int TICKS = 20000;

    /** A walking pace: four point three blocks a second, twenty ticks to the second. */
    private static final double BLOCKS_PER_TICK = 4.317 / 20.0;

    /** What one tick of the caller's logic did. */
    private static class Outcome {
        Rotation applied;
        boolean swayAdvanced;
        boolean swayDecayed;
        /** Whether the sway that this tick advanced, if any, was the standing-still one. */
        boolean idleSway;
    }

    private static Rotation target(Random rand) {
        return new Rotation(rand.nextFloat() * 360f - 180f, rand.nextFloat() * 160f - 80f);
    }

    private static boolean finite(Rotation rotation) {
        return !Float.isNaN(rotation.getYaw()) && !Float.isInfinite(rotation.getYaw())
                && !Float.isNaN(rotation.getPitch()) && !Float.isInfinite(rotation.getPitch());
    }

    /**
     * One tick of the caller's PRE logic: the aim, the glance, the reaction and the sway, wired exactly as
     * LookBehavior wires them and behind the same gates.
     * <p>
     * The two head-movers take different first gates, and they are not interchangeable. The glance asks
     * whether Baritone is in control at all - executing, calculating, or deliberately waiting - because in
     * all three the body is Baritone's and the bot used to stand frozen in the last two. The sway asks
     * whether a path is actually executing, because a sway is a walking thing and walking is what
     * executing means.
     *
     * @return the rotation the server would be told about, or null when nothing moved the head
     */
    private static Rotation tick(Random rand, double sensitivity, double speedSquared, Rotation wanted,
                                 Rotation camera, boolean pathing, boolean inControl, boolean interacting,
                                 Rotation[] aim, int[] aimAge, Rotation[] lastWanted,
                                 IdleGlance[] glance, ReactionDelay[] reaction, HeadWander[] wander,
                                 int[] glanceCamera, float[] sway) {
        final Outcome outcome = new Outcome();
        // the glance is only ever consulted when there is no target at all, so a tick with a target must not
        // inherit the previous tick's answer
        glanceCamera[0] = 0;
        final boolean standingStill = speedSquared < 0.02 * 0.02;
        final boolean walking = speedSquared > 0.02 * 0.02;

        Rotation applied = null;

        // the aim, exactly as handlePlayerUpdate does it
        if (wanted != null) {
            final Rotation from = aim[0] != null ? aim[0] : camera;
            if (aim[0] == null || AimDynamics.separation(lastWanted[0], wanted) > ReactionDelay.NEW_DIRECTION_ANGLE) {
                aimAge[0] = 0;
            }
            if (standingStill && reaction[0].holding(wanted)) {
                aim[0] = from;
                aimAge[0] = 0;
            } else {
                final Rotation next = AimDynamics.humanStep(from, wanted, aimAge[0], sensitivity);
                assertTrue("the aim moved further than a flick in one tick",
                        AimDynamics.separation(aim[0] != null ? aim[0] : from, next) <= AimDynamics.MAX_YAW_STEP + 0.01f);
                aim[0] = next;
                aimAge[0]++;
            }
            lastWanted[0] = wanted;
            applied = aim[0];
        } else {
            aim[0] = null;
            aimAge[0] = 0;
            lastWanted[0] = null;
            reaction[0].clear();
            // the glance, which needs Baritone in control, the player still, and nothing being clicked - the
            // last of those because the trace Baritone breaks and places along follows the rotation this code
            // last sent the server, and the first because a person playing manually is not ours to animate
            final Rotation sent = glance[0].next(camera, inControl && standingStill && !interacting, (from, to) -> {
                final Rotation next = AimDynamics.step(from, to, AimDynamics.IDLE_YAW_STEP, AimDynamics.IDLE_PITCH_STEP,
                        sensitivity);
                // the glance is never allowed the aiming speed, at any sensitivity
                assertTrue("a glance moved " + AimDynamics.separation(from, next) + " deg in one tick",
                        AimDynamics.separation(from, next) <= AimDynamics.IDLE_YAW_STEP + 1e-3f);
                return next;
            });
            if (sent != null) {
                applied = sent;
                glanceCamera[0] = 1;
            } else {
                glanceCamera[0] = 0;
            }
        }

        // The sway, last, and behind either of two gates: a walk being executed, in which case it is at
        // full amplitude, or a bot standing about under Baritone's control with nothing being clicked, in
        // which case the idle path runs the same sway at HeadWander.IDLE_FRACTION and the glance rides on
        // top of it. Both want the head to be ours and nothing to be being clicked; they cannot both be
        // true, because one of them wants a walk and the other wants no walk.
        final boolean walkingSway = wanted != null && walking && pathing && !interacting;
        final boolean idleSway = wanted == null && inControl && standingStill && !interacting;
        outcome.idleSway = idleSway;
        final float before = wander[0].yaw();
        if (walkingSway || idleSway) {
            wander[0].advance();
            outcome.swayAdvanced = true;
        } else {
            wander[0].recentre();
            outcome.swayDecayed = Math.abs(wander[0].yaw()) <= Math.abs(before) + 1e-5f;
        }
        final boolean idle = wanted == null;
        sway[0] = idle ? wander[0].idleYaw() : wander[0].yaw();
        final float swayPitch = idle ? wander[0].idlePitch() : wander[0].pitch();
        outcome.applied = null;

        if (idle) {
            // the idle path writes nothing at all unless the head was ours this tick, which is what keeps
            // a leftover half degree off the rotation Baritone traces its pickaxe along; when it is ours,
            // the sway is added to whatever the glance asked for, or to the camera if it asked for nothing
            if (!idleSway) {
                applied = null;
            } else {
                final Rotation base = applied != null ? applied : camera;
                applied = new Rotation(base.getYaw() + sway[0], Rotation.clampPitch(base.getPitch() + swayPitch));
            }
        } else if (walkingSway) {
            final Rotation base = applied != null ? applied : camera;
            applied = new Rotation(base.getYaw() + sway[0], base.getPitch() + swayPitch);
        } else if (applied != null && sway[0] != 0f) {
            applied = new Rotation(applied.getYaw() + sway[0], applied.getPitch() + swayPitch);
        }
        outcome.applied = applied;
        lastOutcome = outcome;
        return applied;
    }

    /** The last tick's outcome, for the assertions that need to know what drove the head. */
    private static Outcome lastOutcome;

    @Test
    public void nothingSurvivesTwentyThousandHostileTicks() {
        for (double sensitivity : SENSITIVITIES) {
            final Random rand = new Random(20261006);
            Rotation aim = null;
            int aimAge = 0;
            Rotation lastWanted = null;
            final IdleGlance glance = new IdleGlance();
            final ReactionDelay reaction = new ReactionDelay();
            // a fixed seed: the run is twenty thousand ticks of a fixed pseudo-random sequence, and it
            // should be reproducible on the machine that reads the failure as well as the one that wrote it
            final HeadWander wander = new HeadWander(new Random(1));
            int glanceActive = 0;
            float sway = 0f;

            Rotation camera = new Rotation(0f, 0f);
            int reactionRun = 0;
            double sideways = 0;

            for (int t = 0; t < TICKS; t++) {
                final boolean pathing = rand.nextInt(10) > 2;
                // in control is the glance's gate and has to be visited on its own: executing implies it,
                // and the states this widened gate exists for are the ones where nothing is executing
                final boolean inControl = pathing || rand.nextInt(10) > 4;
                final boolean interacting = rand.nextInt(10) > 8;
                // walking, stopping, and standing around, with the speed crossing the idle threshold
                final double speedSquared = rand.nextInt(100) < 45 ? 0.0 : 0.05 + rand.nextDouble() * 0.1;
                final Rotation wanted = rand.nextInt(100) < 70 ? target(rand) : null;

                final Rotation[] aimRef = {aim};
                final int[] ageRef = {aimAge};
                final Rotation[] lastRef = {lastWanted};
                final IdleGlance[] glanceRef = {glance};
                final ReactionDelay[] reactionRef = {reaction};
                final HeadWander[] wanderRef = {wander};
                final int[] glanceRef2 = {glanceActive};
                final float[] swayRef = {sway};

                final Rotation applied = tick(rand, sensitivity, speedSquared, wanted, camera, pathing, inControl,
                        interacting, aimRef, ageRef, lastRef, glanceRef, reactionRef, wanderRef, glanceRef2, swayRef);

                aim = aimRef[0];
                aimAge = ageRef[0];
                lastWanted = lastRef[0];
                glanceActive = glanceRef2[0];
                sway = swayRef[0];

                if (applied != null) {
                    assertTrue("a rotation with a NaN or infinity came out of the look code: " + applied,
                            finite(applied));
                    assertTrue("pitch escaped its range: " + applied.getPitch(),
                            applied.getPitch() >= -90f && applied.getPitch() <= 90f);
                    assertTrue("yaw escaped its range: " + applied.getYaw(),
                            applied.getYaw() >= -180f && applied.getYaw() <= 180f);
                }
                if (aim != null) {
                    assertTrue("the aim became non-finite", finite(aim));
                }
                assertTrue("the sway escaped its limit: " + sway, Math.abs(sway) <= HeadWander.MAX_YAW + 1e-4f);

                // A glance and the *walking* sway must never drive the head together: the glance wants the
                // player standing still and the walking sway wants them moving, so a harness that let both
                // through would have lost one of those gates. The standing-still sway is the deliberate
                // exception - one look every few seconds on a head that is otherwise still is exactly what
                // the idle path is for - so what must hold there is that it is the quiet one.
                if (glanceActive == 1 && lastOutcome.swayAdvanced) {
                    assertTrue("a walking sway drove the head while a glance was driving it",
                            lastOutcome.idleSway);
                    assertTrue("the standing sway was as loud as the walking one: " + sway,
                            Math.abs(sway) <= HeadWander.MAX_YAW * HeadWander.IDLE_FRACTION + 1e-4f);
                }

                // A sway that is not being advanced is a sway on its way out. Without this the fade is
                // unobservable and nothing would notice it being replaced by a switch.
                if (!lastOutcome.swayAdvanced && sway != 0f) {
                    assertTrue("the sway grew while it was not being advanced", lastOutcome.swayDecayed);
                }

                // Nobody's head but ours, and only while Baritone is in charge of the body: the glance's
                // first gate is control, not execution, so this is where a harness that quietly went back to
                // asking about paths would show up.
                if (!inControl) {
                    assertFalse("a glance drove the head while no Baritone process was in control",
                            glanceActive == 1);
                }

                // Nothing moves the head while a click is being forced. This is the harness asserting its own
                // wiring against the product's rule rather than the product itself - the two are written out
                // separately on purpose, so that they can disagree here if either one drifts. The focused case
                // for the rule is aGlanceStandsDownWhileSomethingIsBeingClicked.
                if (interacting) {
                    assertFalse("a glance drove the head while a click was forced", glanceActive == 1);
                    assertFalse("the sway was advanced while a click was forced", lastOutcome.swayAdvanced);
                }

                // walking or not, the sway must not be able to push the walk off its line
                sideways += BLOCKS_PER_TICK * Math.sin(Math.toRadians(sway));
                assertTrue("the walk strayed " + Math.abs(sideways) + " blocks sideways",
                        Math.abs(sideways) < 1.0);

                // the reaction may never hold for longer than its cap, however the target behaves
                if (wanted != null && aim != null) {
                    reactionRun = speedSquared < 0.02 * 0.02 && reaction.holding(wanted) ? reactionRun + 1 : 0;
                    assertTrue("the reaction held the aim still for " + reactionRun + " ticks",
                            reactionRun <= ReactionDelay.HOLD_MAX);
                } else {
                    reactionRun = 0;
                }
            }
        }
    }

    @Test
    public void aHeldTargetIsAlwaysArrivedAt() {
        // Whatever the aim has to travel and wherever it starts, and at every sensitivity, it has to land -
        // exactly, not approximately, because placing and breaking gate on the aim being on target.
        for (double sensitivity : SENSITIVITIES) {
            for (float fromYaw : new float[]{0f, 179f, -179f, 90f, -90f}) {
                for (float toYaw : new float[]{0f, 179f, -179f, 90f, -90f}) {
                    Rotation aim = new Rotation(fromYaw, 0f);
                    final Rotation to = new Rotation(toYaw, 0f);
                    int ticks = 0;
                    while (ticks < 60 && (Rotation.normalizeYaw(toYaw - aim.getYaw()) != 0f)) {
                        aim = AimDynamics.humanStep(aim, to, ticks, sensitivity);
                        ticks++;
                    }
                    assertEquals("never arrived from " + fromYaw + " to " + toYaw + " at sensitivity " + sensitivity,
                            toYaw, aim.getYaw(), 0f);
                    assertTrue("arrival took " + ticks + " ticks", ticks <= 60);
                }
            }
        }
    }

    @Test
    public void aTargetThatNeverSettlesStillGetsMovedToward() {
        // The pathological case: the target flips to the opposite side every tick, which re-arms the spin-up
        // and the reaction every tick. The hand must still make progress rather than sitting at the start.
        final Rotation a = new Rotation(170f, 0f);
        final Rotation b = new Rotation(-170f, 0f);
        Rotation aim = new Rotation(0f, 0f);
        final ReactionDelay reaction = new ReactionDelay();
        int held = 0;
        float moved = 0f;
        for (int t = 0; t < 200; t++) {
            final Rotation wanted = t % 2 == 0 ? a : b;
            final int age = 0; // every tick looks like a new direction
            if (reaction.holding(wanted)) {
                held++;
                assertTrue("the hand was held still " + held + " ticks in a row", held <= ReactionDelay.HOLD_MAX);
                continue;
            }
            held = 0;
            final Rotation next = AimDynamics.humanStep(aim, wanted, age, 0.5);
            moved += Math.abs(Rotation.normalizeYaw(next.getYaw() - aim.getYaw()));
            aim = next;
        }
        assertTrue("the aim never moved toward a target that would not settle", moved > 200f);
    }

    @Test
    public void aGlanceStandsDownWhileSomethingIsBeingClicked() {
        // Baritone aims the pickaxe and the block out along the rotation it last sent the server, so a glance
        // taken while something is being clicked is a glance Baritone itself is mining along - twenty-eight
        // degrees of it, most of a block. This is the rule at the level it is decided, and the two shapes it
        // has to hold in: a glance that has not started must not start, and one already running must be
        // dropped where it stands rather than carried into the click.
        final Rotation camera = new Rotation(40.0f, -10.0f);
        final IdleGlance.Stepper step = (from, to) -> AimDynamics.step(from, to,
                AimDynamics.IDLE_YAW_STEP, AimDynamics.IDLE_PITCH_STEP, 0.5);

        final IdleGlance idle = new IdleGlance();
        int waited = 0;
        while (idle.next(camera, true, step) == null && waited < 400) {
            waited++;
        }
        assertTrue("the idle glance never fired in " + waited + " ticks of standing still", waited < 400);

        // the click starts mid-glance: it is dropped, not driven on
        assertNull("the glance was carried into a click", idle.next(camera, false, step));

        // and however long the click lasts - long enough to cover the longest cooldown twice over - the
        // glance does not start up again behind it
        for (int t = 0; t < 5000; t++) {
            assertNull("the glance started while something was being clicked", idle.next(camera, false, step));
        }
    }

    @Test
    public void aStandingHeadKeepsMovingAndTheSwaySurvivesTheHandover() {
        // What the standing sway is for, and what it must not cost. For: a bot with nothing to do used to
        // hold its angle to the last bit between glances, which under a held pause is minutes of a head
        // that is not moving at all. Cost: the sway is one state machine shared with the walk, so the tick
        // the feet start or stop the head may only change by the difference between the two amplitudes -
        // never from whatever one phase had got to, to whatever another one was at.
        final HeadWander wander = new HeadWander(new Random(2));
        final Random rand = new Random(11);
        boolean walking = true;
        float previous = 0f;
        int idleTicks = 0, stillness = 0, longestStillness = 0;
        float worstStep = 0f;
        for (int t = 0; t < 40000; t++) {
            if (rand.nextInt(60) == 0) {
                walking = !walking;
            }
            wander.advance();
            assertTrue("the sway escaped its limit", Math.abs(wander.yaw()) <= HeadWander.MAX_YAW + 1e-4f);
            assertTrue("the standing sway was louder than the walking one",
                    Math.abs(wander.idleYaw()) <= Math.abs(wander.yaw()) + 1e-4f);
            final float offset = walking ? wander.yaw() : wander.idleYaw();
            if (t > 0) {
                worstStep = Math.max(worstStep, Math.abs(offset - previous));
            }
            if (!walking) {
                idleTicks++;
                stillness = Math.abs(offset) < 1e-3f ? stillness + 1 : 0;
                longestStillness = Math.max(longestStillness, stillness);
            }
            previous = offset;
        }
        assertTrue("no ticks were spent standing", idleTicks > 1000);
        // a swing crosses the bearing a couple of times a second at most, and where it does it is briefly
        // within a thousandth of a degree of zero; anything longer than a second or two of that is a head
        // that has stopped moving, which is the thing this exists to prevent
        assertTrue("the head stood perfectly still for " + longestStillness + " ticks of " + idleTicks,
                longestStillness < 40);
        // one tick of the slowest swing the walk allows, plus the half degree the two amplitudes differ by
        assertTrue("the sway stepped " + worstStep + " deg when the feet changed", worstStep < 0.7f);
    }
}
