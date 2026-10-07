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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Somebody walking up is looked at once, after a reaction and not before; the people already there are
 * not; somebody who leaves before the look is not looked at; a crowd is one look, not a sweep.
 */
public class NewcomerTest {

    private static final List<Integer> NOBODY = Collections.emptyList();
    private static final int SEEDS = 40;

    @Test
    public void somebodyWalkingUpIsLookedAtAfterAReactionAndOnlyOnce() {
        for (int seed = 0; seed < SEEDS; seed++) {
            final Newcomer newcomer = new Newcomer(new Random(seed));
            for (int tick = 0; tick < 100; tick++) {
                assertEquals(-1, newcomer.notice(NOBODY));
            }
            final List<Integer> them = Collections.singletonList(7);
            int lookedOn = -1;
            int looks = 0;
            for (int tick = 1; tick <= 600; tick++) {
                if (newcomer.notice(them) == 7) {
                    looks++;
                    lookedOn = tick;
                }
            }
            assertEquals("seed " + seed + ": looked " + looks + " times at one arrival", 1, looks);
            assertTrue("seed " + seed + ": looked on tick " + lookedOn + " of their arriving, which is a reflex",
                    lookedOn >= Newcomer.REACT_MIN);
            assertTrue("seed " + seed + ": looked on tick " + lookedOn + " of their arriving, which is not noticing",
                    lookedOn <= Newcomer.REACT_MAX);
        }
    }

    @Test
    public void thePeopleAlreadyThereAreNotArrivals() {
        final Newcomer newcomer = new Newcomer(new Random(1));
        final List<Integer> crowd = Arrays.asList(1, 2, 3);
        for (int tick = 0; tick < 400; tick++) {
            assertEquals(-1, newcomer.notice(crowd));
        }
    }

    @Test
    public void somebodyWhoLeavesBeforeTheLookIsNotLookedAt() {
        for (int seed = 0; seed < SEEDS; seed++) {
            final Newcomer newcomer = new Newcomer(new Random(seed));
            newcomer.notice(NOBODY);
            final List<Integer> them = Collections.singletonList(4);
            for (int tick = 0; tick < Newcomer.REACT_MIN - 1; tick++) {
                assertEquals("seed " + seed, -1, newcomer.notice(them));
            }
            for (int tick = 0; tick < 200; tick++) {
                assertEquals("seed " + seed, -1, newcomer.notice(NOBODY));
            }
        }
    }

    @Test
    public void steppingBehindATreeAndOutAgainIsNotArrivingTwice() {
        for (int seed = 0; seed < SEEDS; seed++) {
            final Newcomer newcomer = new Newcomer(new Random(seed));
            newcomer.notice(NOBODY);
            final List<Integer> them = Collections.singletonList(9);
            int looks = 0;
            for (int tick = 0; tick < 100; tick++) {
                if (newcomer.notice(them) == 9) {
                    looks++;
                }
            }
            for (int tick = 0; tick < Newcomer.FORGOTTEN_AFTER / 2; tick++) {
                assertEquals(-1, newcomer.notice(NOBODY));
            }
            for (int tick = 0; tick < 100; tick++) {
                if (newcomer.notice(them) == 9) {
                    looks++;
                }
            }
            assertEquals("seed " + seed + ": looked " + looks + " times at somebody who stepped out of view once", 1, looks);
            // gone long enough to be forgotten, and they have arrived again
            for (int tick = 0; tick < Newcomer.FORGOTTEN_AFTER + 1; tick++) {
                assertEquals(-1, newcomer.notice(NOBODY));
            }
            for (int tick = 0; tick < 100; tick++) {
                if (newcomer.notice(them) == 9) {
                    looks++;
                }
            }
            assertEquals("seed " + seed + ": not looked at when they came back after a long while", 2, looks);
        }
    }

    @Test
    public void aCrowdArrivingTogetherIsOneLookAtTheNearest() {
        for (int seed = 0; seed < SEEDS; seed++) {
            final Newcomer newcomer = new Newcomer(new Random(seed));
            newcomer.notice(NOBODY);
            final List<Integer> crowd = Arrays.asList(30, 31, 32);
            int looks = 0;
            int at = -1;
            for (int tick = 0; tick < Newcomer.LOOKS_APART - 1; tick++) {
                final int look = newcomer.notice(crowd);
                if (look != -1) {
                    looks++;
                    at = look;
                }
            }
            assertEquals("seed " + seed + ": " + looks + " looks at a crowd of three", 1, looks);
            assertEquals("seed " + seed + ": looked at somebody other than the nearest", 30, at);
        }
    }

    @Test
    public void twoArrivalsInQuickSuccessionAreOneLookUntilTheFirstHasWornOff() {
        for (int seed = 0; seed < SEEDS; seed++) {
            final Newcomer newcomer = new Newcomer(new Random(seed));
            newcomer.notice(NOBODY);
            int tick = 0;
            int firstLook = -1;
            final List<Integer> one = Collections.singletonList(1);
            while (firstLook == -1) {
                tick++;
                if (newcomer.notice(one) == 1) {
                    firstLook = tick;
                }
            }
            final List<Integer> both = Arrays.asList(1, 2);
            int secondLook = -1;
            for (int i = 0; i < 400 && secondLook == -1; i++) {
                tick++;
                if (newcomer.notice(both) == 2) {
                    secondLook = tick;
                }
            }
            assertTrue("seed " + seed + ": the second arrival was never looked at", secondLook != -1);
            assertTrue("seed " + seed + ": the second look came " + (secondLook - firstLook) + " ticks after the first",
                    secondLook - firstLook >= Newcomer.LOOKS_APART);
        }
    }

    @Test
    public void theSameSeedIsTheSameReaction() {
        final Newcomer a = new Newcomer(new Random(5));
        final Newcomer b = new Newcomer(new Random(5));
        final List<Integer> them = Collections.singletonList(2);
        a.notice(NOBODY);
        b.notice(NOBODY);
        for (int tick = 0; tick < 50; tick++) {
            assertEquals(a.notice(them), b.notice(them));
        }
    }
}
