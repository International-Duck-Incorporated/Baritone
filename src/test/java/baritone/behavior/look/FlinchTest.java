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
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A blow turns the head to whoever struck, and the things that make that a person rather than a machine
 * are all pinned here: it goes however far round they are (a neck's worth when the feet are moving, the
 * whole way when they are not), it goes at the hand's own ceiling rather than a look's, it stays there
 * for a stare, and it comes back at the speed of a look and exactly to nothing. The one thing it never
 * does is touch the head while something is being clicked, and the debt it owes is taken up again
 * afterwards - a bot hit mid-block looks round at whoever did it once the block is done.
 */
public class FlinchTest {

    private static final int SEEDS = 40;

    /** Far enough round that a look would refuse it and the answer has to be the flinch's own. */
    private static final float BEHIND = 179f;

    private static final Rotation STILL = new Rotation(0f, 0f);

    /** A whole body's turn back onto a walk takes at most this long at the hand's ceiling. */
    private static final int CARRY_BACK = 25;

    private static Flinch flinch(final long seed) {
        return new Flinch(new Random(seed));
    }

    /** Somewhere the attacker is, moveable mid-flinch the way a real one is. */
    private static final class Them implements Supplier<Rotation> {
        private Rotation where;

        private Them(float yaw, float pitch) {
            this.where = new Rotation(yaw, pitch);
        }

        @Override
        public Rotation get() {
            return this.where;
        }
    }

    /**
     * Whether the flinch has the head on {@code yaw} at the moment, to {@link Flinch#ARRIVED}.
     */
    private static boolean on(Flinch flinch, float yaw) {
        return Math.abs(flinch.yaw() - yaw) <= Flinch.ARRIVED;
    }

    @Test
    public void theHeadTurnsToWhoeverHitItHoweverFarRoundTheyAre() {
        for (long seed = 0; seed < SEEDS; seed++) {
            final Flinch flinch = flinch(seed);
            flinch.blow(new Them(BEHIND, 0f));
            int startedOn = -1;
            int arrivedOn = -1;
            float worstStep = 0f;
            for (int tick = 1; tick <= 30; tick++) {
                final float before = flinch.yaw();
                flinch.advance(STILL, false, true);
                worstStep = Math.max(worstStep, Math.abs(flinch.yaw() - before));
                if (startedOn < 0 && flinch.yaw() != 0f) {
                    startedOn = tick;
                }
                if (arrivedOn < 0 && on(flinch, BEHIND)) {
                    arrivedOn = tick;
                }
            }
            assertTrue("seed " + seed + ": a blow from behind was never turned to", arrivedOn > 0);
            // the reaction first, drawn: two to six ticks with nothing moving, then the flick. A head that
            // moved in the tick the blow landed would be as much a machine as one that never moved
            assertTrue("seed " + seed + ": the head started moving after " + startedOn + " ticks, with no reaction in it",
                    startedOn > Flinch.ONSET_MIN);
            assertTrue("seed " + seed + ": the head started moving after " + startedOn + " ticks, which is a decision",
                    startedOn <= Flinch.ONSET_MAX + 1);
            // and ten ticks of turning is the ceiling's arithmetic for a half turn: slower is a look
            assertTrue("seed " + seed + ": took " + (arrivedOn - startedOn + 1) + " ticks of turning to come round",
                    arrivedOn - startedOn + 1 <= 10);
            assertTrue("seed " + seed + ": stepped " + worstStep + " degrees in one tick",
                    worstStep <= Flinch.STEP + 1e-3f);
        }
    }

    @Test
    public void aWalkingHeadTurnsAsFarAsANeckGoesAndNoFurther() {
        for (long seed = 0; seed < SEEDS; seed++) {
            final Flinch flinch = flinch(seed);
            flinch.blow(new Them(BEHIND, 0f));
            float furthest = 0f;
            for (int tick = 0; tick < 60; tick++) {
                flinch.advance(STILL, true, true);
                furthest = Math.max(furthest, Math.abs(flinch.yaw()));
                // and it turns toward them rather than away: the offset keeps the bearing's sign
                assertTrue("seed " + seed + ": turned the wrong way round", flinch.yaw() >= 0f);
            }
            assertTrue("seed " + seed + ": a walking head reached " + furthest + " degrees round, past a neck",
                    furthest <= Flinch.NECK_YAW + Flinch.ARRIVED + 1e-3f);
            assertTrue("seed " + seed + ": a walking head only reached " + furthest + " degrees of its neck",
                    furthest >= Flinch.NECK_YAW - Flinch.ARRIVED);
        }
    }

    @Test
    public void theHeadHoldsAStareThenComesBackToExactlyNothing() {
        for (long seed = 0; seed < SEEDS; seed++) {
            final Flinch flinch = flinch(seed);
            flinch.blow(new Them(90f, 25f));
            int onThem = 0;
            int homeOn = -1;
            float worstReturnStep = 0f;
            boolean stareDone = false;
            for (int tick = 1; tick <= 600; tick++) {
                final float beforeYaw = flinch.yaw();
                flinch.advance(STILL, false, true);
                if (on(flinch, 90f)) {
                    onThem++;
                } else if (onThem > 0) {
                    // the stare is over and the head is on its way home
                    stareDone = true;
                    worstReturnStep = Math.max(worstReturnStep, Math.abs(flinch.yaw() - beforeYaw));
                }
                if (!flinch.out() && onThem > 0) {
                    // home, and it had been there: nothing moved during the reaction, so a head that is not
                    // out yet is not a head that has come home
                    homeOn = tick;
                    break;
                }
            }
            assertTrue("seed " + seed + ": the head never came home", homeOn > 0);
            assertTrue("seed " + seed + ": came home after " + homeOn + " ticks, before any stare",
                    stareDone);
            assertEquals("seed " + seed + ": the head was not put down exactly", 0f, flinch.yaw(), 0f);
            assertEquals("seed " + seed + ": the head was not put down exactly", 0f, flinch.pitch(), 0f);
            assertTrue("seed " + seed + ": the stare lasted " + onThem + " ticks",
                    onThem >= Flinch.HOLD_MIN && onThem <= Flinch.HOLD_MAX + 2);
            assertTrue("seed " + seed + ": came home at " + worstReturnStep + " degrees a tick, which is a flinch",
                    worstReturnStep <= Flinch.HOME_STEP + 1e-3f);
            assertFalse("seed " + seed + ": still owed a look after coming home", flinch.answering());
        }
    }

    @Test
    public void aSecondBlowStartsTheStareAgainWhereTheyAreNow() {
        for (long seed = 0; seed < SEEDS; seed++) {
            final Flinch flinch = flinch(seed);
            final Them them = new Them(90f, 0f);
            flinch.blow(them);
            int arrived = -1;
            for (int tick = 1; tick <= 20 && arrived < 0; tick++) {
                flinch.advance(STILL, false, true);
                if (on(flinch, 90f)) {
                    arrived = tick;
                }
            }
            assertTrue("seed " + seed + ": the first blow was never answered", arrived > 0);
            // they have moved - or it is somebody else - and the blow lands again
            them.where = new Rotation(160f, 0f);
            flinch.blow(them);
            float worstStep = 0f;
            int arrivedOn = -1;
            int onThem = 0;
            for (int tick = 1; tick <= 80; tick++) {
                final float before = flinch.yaw();
                flinch.advance(STILL, false, true);
                worstStep = Math.max(worstStep, Math.abs(flinch.yaw() - before));
                if (on(flinch, 160f)) {
                    onThem++;
                    if (arrivedOn < 0) {
                        arrivedOn = tick;
                    }
                }
            }
            assertTrue("seed " + seed + ": a second blow did not turn to where they are now", arrivedOn > 0);
            assertTrue("seed " + seed + ": a second blow snapped the head " + worstStep + " degrees",
                    worstStep <= Flinch.STEP + 1e-3f);
            // the stare was taken again rather than run out under it, and it had the length of a stare
            assertTrue("seed " + seed + ": the second blow was stared at for only " + onThem + " ticks",
                    onThem >= Flinch.HOLD_MIN);
        }
    }

    @Test
    public void aSecondBlowOnTheHeelsOfTheFirstDoesNotStartTheReactionOver() {
        for (long seed = 0; seed < SEEDS; seed++) {
            final Flinch once = flinch(seed);
            final Flinch twice = flinch(seed);
            once.blow(new Them(90f, 0f));
            twice.blow(new Them(90f, 0f));
            int startedOnce = -1;
            int startedTwice = -1;
            for (int tick = 1; tick <= 20; tick++) {
                once.advance(STILL, false, true);
                twice.advance(STILL, false, true);
                if (tick == 2) {
                    // the same hand again, while the first flinch is still being decided on
                    twice.blow(new Them(90f, 0f));
                }
                if (startedOnce < 0 && once.yaw() != 0f) {
                    startedOnce = tick;
                }
                if (startedTwice < 0 && twice.yaw() != 0f) {
                    startedTwice = tick;
                }
            }
            assertTrue("seed " + seed + ": the head never moved", startedOnce > 0);
            assertEquals("seed " + seed + ": a second blow restarted the reaction, which is a decision twice",
                    startedOnce, startedTwice);
        }
    }

    @Test
    public void aNewWorldIsNobodyHavingHitUs() {
        final Flinch flinch = flinch(9);
        flinch.blow(new Them(179f, 40f));
        for (int tick = 0; tick < 12; tick++) {
            flinch.advance(STILL, false, true);
        }
        assertTrue("the stare never started", flinch.out());
        flinch.reset();
        assertFalse("a flinch survived the world it happened in", flinch.out());
        assertFalse("a blow was still owed across worlds", flinch.answering());
        assertEquals("the head was left holding an angle", 0f, flinch.yaw(), 0f);
        assertEquals("the head was left holding an angle", 0f, flinch.pitch(), 0f);
    }

    @Test
    public void somebodyGoneIsNotStaredAt() {
        final Flinch flinch = flinch(7);
        flinch.blow(() -> null);
        for (int tick = 0; tick < Flinch.ONSET_MAX + 2; tick++) {
            flinch.advance(STILL, false, true);
        }
        assertFalse("nothing to look at, and a head was turned anyway", flinch.answering());
        assertFalse("nothing to look at", flinch.out());

        // and one who goes away while the head is on them
        final Them them = new Them(120f, 0f);
        final Flinch second = flinch(11);
        second.blow(them);
        for (int tick = 0; tick < 10; tick++) {
            second.advance(STILL, false, true);
        }
        assertTrue("the stare never started", second.out());
        them.where = null;
        second.advance(STILL, false, true);
        assertFalse("the head was still owed a look at somebody who had gone", second.answering());
        assertTrue("the head vanished rather than coming home", second.out());
        for (int tick = 0; tick < 40; tick++) {
            second.advance(STILL, false, true);
        }
        assertFalse("the head never got home", second.out());
    }

    @Test
    public void aClickStandsTheHeadDownAndTheStareIsTakenUpAgain() {
        final Flinch flinch = flinch(3);
        flinch.blow(new Them(90f, 0f));
        int arrived = -1;
        for (int tick = 1; tick <= 20 && arrived < 0; tick++) {
            flinch.advance(STILL, false, true);
            if (on(flinch, 90f)) {
                arrived = tick;
            }
        }
        assertTrue("the stare never started", arrived > 0);
        // the pickaxe comes out: the head is needed, and it is handed back rather than switched off
        flinch.advance(STILL, false, false);
        assertTrue("the head snapped off the attacker", flinch.out());
        assertTrue("the head did not move off the attacker at all", Math.abs(flinch.yaw()) < 90f);
        // a moment of digging later it is free again, and the look it still owes is taken
        int backOnThem = -1;
        for (int tick = 1; tick <= 40; tick++) {
            flinch.advance(STILL, false, true);
            if (backOnThem < 0 && on(flinch, 90f)) {
                backOnThem = tick;
                break;
            }
        }
        assertTrue("the head never came back round to whoever hit it", backOnThem > 0);
    }

    @Test
    public void theHeadNeverMovesFasterThanTheHandCanFlick() {
        for (long seed = 0; seed < SEEDS; seed++) {
            final Random rand = new Random(seed);
            final Flinch flinch = new Flinch(rand);
            final Them them = new Them(0f, 0f);
            float baseYaw = 0f;
            int movingFor = 0;
            boolean walking = false;
            int walkLeft = 0;
            for (int tick = 0; tick < 3000; tick++) {
                // a base that is itself turning, sometimes as fast as the aim ever does
                baseYaw = Rotation.normalizeYaw(baseYaw + (rand.nextFloat() * 2f - 1f) * Flinch.STEP);
                them.where = rand.nextFloat() < 0.1f
                        ? null
                        : new Rotation(Rotation.normalizeYaw(them.where == null
                        ? baseYaw
                        : them.where.getYaw() + (rand.nextFloat() * 2f - 1f) * 90f),
                        Math.max(-90f, Math.min(90f, rand.nextFloat() * 120f - 60f)));
                if (rand.nextFloat() < 0.05f) {
                    flinch.blow(them);
                }
                // the walk comes in stretches, as a walk does, so the carry-back is actually reached
                if (walkLeft <= 0) {
                    walking = rand.nextBoolean();
                    walkLeft = walking ? 30 + rand.nextInt(111) : 5 + rand.nextInt(56);
                }
                walkLeft--;
                final boolean moving = walking;
                final boolean wanted = rand.nextFloat() > 0.1f;
                movingFor = moving ? movingFor + 1 : 0;
                final float beforeYaw = flinch.yaw();
                final float beforePitch = flinch.pitch();
                flinch.advance(new Rotation(baseYaw, 0f), moving, wanted);
                final float stepYaw = Math.abs(flinch.yaw() - beforeYaw);
                final float stepPitch = Math.abs(flinch.pitch() - beforePitch);
                assertTrue("seed " + seed + " tick " + tick + ": yaw moved " + stepYaw + " degrees in a tick",
                        stepYaw <= Flinch.STEP + 1e-3f);
                assertTrue("seed " + seed + " tick " + tick + ": pitch moved " + stepPitch + " degrees in a tick",
                        stepPitch <= Flinch.PITCH_STEP + 1e-3f);
                if (movingFor >= CARRY_BACK) {
                    // the whole body's turn has been carried back onto the walk by now
                    assertTrue("seed " + seed + " tick " + tick + ": walking with the head " + flinch.yaw()
                                    + " degrees off the walk, past a neck",
                            Math.abs(flinch.yaw()) <= Flinch.NECK_YAW + Flinch.ARRIVED + 1e-3f);
                }
                assertTrue("seed " + seed + " tick " + tick + ": the pitch wandered past the sky",
                        Math.abs(flinch.pitch()) <= 90f);
            }
        }
    }

    @Test
    public void theStareIsBoundedEvenIfEveryBlowCannotBeAnswered() {
        // the safety net proper: one blow, and a target that is always just out of reach
        final Flinch flinch = flinch(13);
        // somebody who is never where the head is: they cross from one side to the other every tick, and a
        // head that steps eighteen degrees toward sixty is then asked for minus sixty, so it never settles
        final int[] n = {0};
        flinch.blow(() -> new Rotation(n[0]++ % 2 == 0 ? 60f : -60f, 0f));
        int ended = -1;
        for (int tick = 1; tick <= Flinch.MAX_AGE + 60; tick++) {
            flinch.advance(STILL, false, true);
            if (!flinch.answering()) {
                ended = tick;
                break;
            }
        }
        assertTrue("a flinch that never arrives and is never renewed went on for ever", ended > 0);
        assertTrue("it gave up before the safety net", ended >= Flinch.MAX_AGE);
        for (int tick = 0; tick < 200; tick++) {
            flinch.advance(STILL, false, true);
        }
        assertEquals("the head was left holding an angle", 0f, flinch.yaw(), 0f);
        assertEquals("the head was left holding an angle", 0f, flinch.pitch(), 0f);
    }
}
