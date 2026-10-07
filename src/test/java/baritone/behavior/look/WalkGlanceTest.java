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
 * The glance to the side on a long walk: it happens, it is bounded, it is never faster than a look, it
 * comes back to exactly nothing, and it is gone the moment the head is wanted.
 */
public class WalkGlanceTest {

    private static final int SEEDS = 40;

    @Test
    public void aPlainWalkGetsAGlanceAndGetsItsHeadBack() {
        for (int seed = 0; seed < SEEDS; seed++) {
            final WalkGlance glance = new WalkGlance(new Random(seed));
            int glances = 0;
            boolean out = false;
            float furthest = 0f;
            for (int tick = 0; tick < 6000; tick++) {
                glance.advance(true, () -> null);
                furthest = Math.max(furthest, Math.abs(glance.yaw()));
                if (glance.yaw() != 0f && !out) {
                    glances++;
                }
                out = glance.yaw() != 0f;
            }
            assertTrue("seed " + seed + ": " + glances + " glances in five minutes of walking", glances >= 10);
            assertTrue("seed " + seed + ": only " + furthest + " degrees in five minutes", furthest >= 20f);
        }
    }

    @Test
    public void theFirstGlanceIsNeitherAtOnceNorNever() {
        for (int seed = 0; seed < SEEDS; seed++) {
            final WalkGlance glance = new WalkGlance(new Random(seed));
            int tick = 0;
            while (!glance.out()) {
                glance.advance(true, () -> null);
                tick++;
                assertTrue("seed " + seed + ": no glance after " + tick + " ticks", tick <= WalkGlance.COOLDOWN_MAX);
            }
            assertTrue("seed " + seed + ": a glance " + tick + " ticks into the walk", tick >= WalkGlance.COOLDOWN_MIN);
        }
    }

    @Test
    public void neverFurtherThanTheBoundsAndNeverFasterThanALook() {
        for (int seed = 0; seed < SEEDS; seed++) {
            final Random rand = new Random(seed);
            final WalkGlance glance = new WalkGlance(new Random(seed * 31 + 7));
            final Rotation[] interest = {null};
            float lastYaw = 0f;
            float lastPitch = 0f;
            for (int tick = 0; tick < 20000; tick++) {
                // a hostile walk: mostly plain, with the head wanted at random, sometimes mid-glance
                final boolean allowed = rand.nextFloat() < 0.97f;
                interest[0] = rand.nextFloat() < 0.5f ? null
                        : new Rotation(rand.nextFloat() * 360f - 180f, rand.nextFloat() * 120f - 60f);
                glance.advance(allowed, () -> interest[0]);
                assertFalse(Float.isNaN(glance.yaw()) || Float.isNaN(glance.pitch()));
                assertTrue("seed " + seed + " tick " + tick + ": yaw " + glance.yaw(),
                        Math.abs(glance.yaw()) <= WalkGlance.MAX_INTEREST_YAW + 2f);
                assertTrue("seed " + seed + " tick " + tick + ": pitch " + glance.pitch(),
                        Math.abs(glance.pitch()) <= WalkGlance.MAX_INTEREST_PITCH + 1f);
                assertTrue("seed " + seed + " tick " + tick + ": yaw stepped " + (glance.yaw() - lastYaw),
                        Math.abs(glance.yaw() - lastYaw) <= WalkGlance.MAX_STEP + 1e-3f);
                assertTrue("seed " + seed + " tick " + tick + ": pitch stepped " + (glance.pitch() - lastPitch),
                        Math.abs(glance.pitch() - lastPitch) <= WalkGlance.MAX_PITCH_STEP + 1e-3f);
                lastYaw = glance.yaw();
                lastPitch = glance.pitch();
            }
        }
    }

    @Test
    public void aGlanceAtNothingStaysLevelAndToTheSide() {
        for (int seed = 0; seed < SEEDS; seed++) {
            final WalkGlance glance = new WalkGlance(new Random(seed));
            for (int tick = 0; tick < 6000; tick++) {
                glance.advance(true, () -> null);
                assertTrue(Math.abs(glance.yaw()) <= WalkGlance.MAX_YAW + 1e-3f);
                assertTrue(Math.abs(glance.pitch()) <= WalkGlance.MAX_PITCH + 1e-3f);
            }
        }
    }

    @Test
    public void itComesBackToExactlyNothing() {
        for (int seed = 0; seed < SEEDS; seed++) {
            final WalkGlance glance = new WalkGlance(new Random(seed));
            int returns = 0;
            boolean wasOut = false;
            for (int tick = 0; tick < 6000; tick++) {
                glance.advance(true, () -> null);
                if (wasOut && !glance.out()) {
                    returns++;
                    assertEquals(0f, glance.yaw(), 0f);
                    assertEquals(0f, glance.pitch(), 0f);
                }
                wasOut = glance.out();
            }
            assertTrue(returns >= 10);
        }
    }

    @Test
    public void whenTheHeadIsWantedItComesBackAtOnceAndWaitsAWholeInterval() {
        for (int seed = 0; seed < SEEDS; seed++) {
            final WalkGlance glance = new WalkGlance(new Random(seed));
            // walk until a glance is well out
            while (Math.abs(glance.yaw()) < 10f) {
                glance.advance(true, () -> null);
            }
            final float from = Math.abs(glance.yaw());
            int back = 0;
            while (glance.out()) {
                glance.advance(false, () -> null);
                back++;
                assertTrue("seed " + seed + ": still out after " + back + " ticks", back <= 1 + from / WalkGlance.RECOVER_STEP + 1);
            }
            assertEquals(0f, glance.yaw(), 0f);
            // and once the walk is plain again, the count starts over
            for (int tick = 0; tick < WalkGlance.COOLDOWN_MIN - 1; tick++) {
                glance.advance(true, () -> null);
                assertFalse("seed " + seed + ": a glance " + tick + " ticks after being interrupted", glance.out());
            }
        }
    }

    @Test
    public void nothingHappensOnAWalkThatIsNeverPlain() {
        for (int seed = 0; seed < SEEDS; seed++) {
            final WalkGlance glance = new WalkGlance(new Random(seed));
            for (int tick = 0; tick < 6000; tick++) {
                glance.advance(false, () -> null);
                assertEquals(0f, glance.yaw(), 0f);
                assertEquals(0f, glance.pitch(), 0f);
                assertFalse(glance.out());
            }
        }
    }

    @Test
    public void theIntervalIsCountedInPlainTicksOnly() {
        // a walk that is plain one tick in three takes three times as long to get to its first glance:
        // broken ground is not where a person looks about
        for (int seed = 0; seed < SEEDS; seed++) {
            final WalkGlance glance = new WalkGlance(new Random(seed));
            int plain = 0;
            int tick = 0;
            while (!glance.out()) {
                final boolean allowed = tick % 3 == 0;
                glance.advance(allowed, () -> null);
                if (allowed) {
                    plain++;
                }
                tick++;
                assertTrue(tick < 3 * WalkGlance.COOLDOWN_MAX + 3);
            }
            assertTrue("seed " + seed + ": glanced after " + plain + " plain ticks", plain >= WalkGlance.COOLDOWN_MIN);
        }
    }

    @Test
    public void somethingNearbyIsLookedAtSometimesAndOnlyAskedForAtTheStart() {
        int looked = 0;
        for (int seed = 0; seed < SEEDS; seed++) {
            final WalkGlance glance = new WalkGlance(new Random(seed));
            final int[] asked = {0};
            final Rotation toTheLeft = new Rotation(-55f, -10f);
            boolean wasOut = false;
            int starts = 0;
            for (int tick = 0; tick < 6000; tick++) {
                glance.advance(true, () -> {
                    asked[0]++;
                    return toTheLeft;
                });
                if (glance.out() && !wasOut) {
                    starts++;
                }
                if (Math.abs(glance.yaw() + 55f) < 3f && Math.abs(glance.pitch() + 10f) < 2f) {
                    looked++;
                }
                wasOut = glance.out();
            }
            assertEquals("asked on ticks other than a glance's first", starts, asked[0]);
        }
        assertTrue("never once looked at the thing to the left", looked > 0);
    }

    @Test
    public void theFeetStopForAFewLooksAndOnlyWhileTheHeadIsOutThere() {
        int glances = 0;
        int stops = 0;
        for (int seed = 0; seed < SEEDS; seed++) {
            final WalkGlance glance = new WalkGlance(new Random(seed));
            boolean wasOut = false;
            boolean wasStopped = false;
            boolean wentAgainThisGlance = false;
            float yawWhenStopped = 0f;
            for (int tick = 0; tick < 12000; tick++) {
                glance.advance(true, () -> null);
                final boolean out = glance.out();
                final boolean stopped = glance.stopped();
                if (out && !wasOut) {
                    glances++;
                    wentAgainThisGlance = false;
                    assertFalse("seed " + seed + ": the feet stopped on the first tick of a look", stopped);
                }
                if (stopped) {
                    assertTrue("seed " + seed + ": the feet stopped with the head on the bearing", out);
                    assertFalse("seed " + seed + ": the feet stopped twice for one look", wentAgainThisGlance);
                    if (!wasStopped) {
                        stops++;
                        yawWhenStopped = Math.abs(glance.yaw());
                        assertTrue("seed " + seed + ": stopped before the head was anywhere", yawWhenStopped > 0f);
                    } else {
                        // standing to look: the head is still going out or holding, never on its way back
                        assertTrue("seed " + seed + ": the feet stood while the head was already coming back",
                                Math.abs(glance.yaw()) >= yawWhenStopped - 0.01f);
                        yawWhenStopped = Math.abs(glance.yaw());
                    }
                } else if (wasStopped) {
                    wentAgainThisGlance = true;
                    assertTrue("seed " + seed + ": the feet went again with the look already over", out);
                }
                if (!out && wasOut) {
                    assertFalse(stopped);
                }
                wasOut = out;
                wasStopped = stopped;
            }
        }
        assertTrue("no walk ever stopped for a look", stops > 0);
        assertTrue("the feet stopped for " + stops + " of " + glances + " looks at nothing, which is most walks on hold",
                stops * 100 <= glances * 20);
        assertTrue("the feet stopped for " + stops + " of " + glances + " looks at nothing, which is as good as never",
                stops * 100 >= glances * 4);
    }

    @Test
    public void aLookAtSomethingIsStoppedForMoreOften() {
        int glances = 0;
        int stops = 0;
        for (int seed = 0; seed < SEEDS; seed++) {
            final WalkGlance glance = new WalkGlance(new Random(seed));
            final Rotation toTheLeft = new Rotation(-55f, -10f);
            boolean wasOut = false;
            boolean wasStopped = false;
            for (int tick = 0; tick < 12000; tick++) {
                glance.advance(true, () -> toTheLeft);
                if (glance.out() && !wasOut) {
                    glances++;
                }
                if (glance.stopped() && !wasStopped) {
                    stops++;
                }
                wasOut = glance.out();
                wasStopped = glance.stopped();
            }
        }
        assertTrue("stopped for " + stops + " of " + glances + " looks with something there to look at",
                stops * 100 >= glances * 12 && stops * 100 <= glances * 40);
    }

    @Test
    public void theFeetGoAgainTheMomentTheHeadIsWanted() {
        for (int seed = 0; seed < SEEDS; seed++) {
            final WalkGlance glance = new WalkGlance(new Random(seed));
            int tick = 0;
            while (!glance.stopped() && tick < 60000) {
                glance.advance(true, () -> null);
                tick++;
            }
            if (!glance.stopped()) {
                continue; // a walk that never stopped is a fine walk; the count is asserted elsewhere
            }
            glance.advance(false, () -> null);
            assertFalse("seed " + seed + ": the feet stayed stopped with the head wanted", glance.stopped());
            for (int i = 0; i < 200; i++) {
                glance.advance(false, () -> null);
                assertFalse(glance.stopped());
            }
        }
    }

    @Test
    public void aPromptIsLookedAtAheadOfTheClockWhenTheWalkAllows() {
        for (int seed = 0; seed < SEEDS; seed++) {
            final WalkGlance glance = new WalkGlance(new Random(seed));
            for (int t = 0; t < 20; t++) {
                glance.advance(true, () -> null);
                assertFalse("seed " + seed + ": a glance before the first interval was up", glance.out());
            }
            glance.prompt(() -> new Rotation(-50f, -4f));
            float nearest = 180f;
            for (int t = 0; t < 60; t++) {
                glance.advance(true, () -> null);
                nearest = Math.min(nearest, Math.abs(glance.yaw() + 50f));
            }
            assertTrue("seed " + seed + ": the prompted glance got no nearer than " + nearest + " degrees of them", nearest <= 3f);
        }
    }

    @Test
    public void aPromptIsNotAPermission() {
        final WalkGlance glance = new WalkGlance(new Random(8));
        glance.prompt(() -> new Rotation(-50f, -4f));
        for (int t = 0; t < 20; t++) {
            glance.advance(false, () -> null);
            assertFalse("a glance on a walk it was not allowed on", glance.out());
        }
        // allowed again within the two seconds: taken then
        glance.advance(true, () -> null);
        assertTrue(glance.out());
    }

    @Test
    public void aPromptAtSomebodyGoneOrTooFarRoundIsNoGlance() {
        for (final Rotation where : new Rotation[]{null, new Rotation(150f, 0f), new Rotation(2f, 0f)}) {
            final WalkGlance glance = new WalkGlance(new Random(9));
            for (int t = 0; t < 20; t++) {
                glance.advance(true, () -> null);
            }
            glance.prompt(() -> where);
            for (int t = 0; t < 30; t++) {
                glance.advance(true, () -> null);
                assertFalse("a glance at " + where, glance.out());
            }
        }
    }

    @Test
    public void aPromptNotTakenInTimeIsDropped() {
        final WalkGlance glance = new WalkGlance(new Random(10));
        glance.prompt(() -> new Rotation(-50f, -4f));
        for (int t = 0; t <= WalkGlance.PROMPT_LASTS; t++) {
            glance.advance(false, () -> null);
        }
        for (int t = 0; t < 15; t++) {
            glance.advance(true, () -> null);
            assertFalse("a glance taken on a two second old prompt", glance.out());
        }
    }

    @Test
    public void theSameSeedIsTheSameWalk() {
        final WalkGlance a = new WalkGlance(new Random(99));
        final WalkGlance b = new WalkGlance(new Random(99));
        for (int tick = 0; tick < 3000; tick++) {
            a.advance(true, () -> null);
            b.advance(true, () -> null);
            assertEquals(a.yaw(), b.yaw(), 0f);
            assertEquals(a.pitch(), b.pitch(), 0f);
        }
    }
}
