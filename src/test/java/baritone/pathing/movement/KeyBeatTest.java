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

package baritone.pathing.movement;

import baritone.utils.Cadence;
import org.junit.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The beat between two directions has one job: cost a reversal a small, human amount of time, and cost
 * every other key state nothing at all. The boundaries are the whole test, because getting them wrong
 * means either a bot that still flips on a dime or a bot that stands still at the edge of a bridge.
 */
public class KeyBeatTest {

    private static final KeyBeat.Keys W = new KeyBeat.Keys(true, false, false, false);
    private static final KeyBeat.Keys S = new KeyBeat.Keys(false, true, false, false);
    private static final KeyBeat.Keys A = new KeyBeat.Keys(false, false, true, false);
    private static final KeyBeat.Keys D = new KeyBeat.Keys(false, false, false, true);
    private static final KeyBeat.Keys W_A = new KeyBeat.Keys(true, false, true, false);
    private static final KeyBeat.Keys W_D = new KeyBeat.Keys(true, false, false, true);
    private static final KeyBeat.Keys W_S = new KeyBeat.Keys(true, true, false, false);
    private static final KeyBeat.Keys NONE = KeyBeat.Keys.NONE;

    /**
     * Runs a direction in for five ticks, then reverses onto {@code opposite}, and reports how many ticks
     * the hands were held off the keys before the new direction landed.
     */
    private static int beatLength(KeyBeat beat, KeyBeat.Keys start, KeyBeat.Keys opposite) {
        for (int i = 0; i < 5; i++) {
            assertEquals("the run did not establish itself", start, beat.tick(start, true));
        }
        int held = 0;
        while (held < 50 && !beat.tick(opposite, true).equals(opposite)) {
            held++;
        }
        return held;
    }

    @Test
    public void aReversalCostsARealBeat() {
        for (int i = 0; i < 200; i++) {
            final int held = beatLength(new KeyBeat(new Random(i)), W, S);
            assertTrue("a reversal was answered immediately", held >= Cadence.KEY_BEAT_MIN);
            assertTrue("a reversal stalled for " + held + " ticks", held <= Cadence.KEY_BEAT_MAX);
        }
    }

    @Test
    public void theBeatVaries() {
        final Random rand = new Random(1234);
        final Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            seen.add(beatLength(new KeyBeat(rand), W, S));
        }
        assertTrue("every beat is the same length: " + seen, seen.size() > 1);
    }

    @Test
    public void aStrafeFlipCountsAsAReversal() {
        for (int i = 0; i < 200; i++) {
            final int held = beatLength(new KeyBeat(new Random(i)), A, D);
            assertTrue("a strafe flip was answered immediately", held >= Cadence.KEY_BEAT_MIN);
            assertTrue("a strafe flip stalled for " + held + " ticks", held <= Cadence.KEY_BEAT_MAX);
        }
    }

    @Test
    public void aStrafeFlipWhileWalkingCountsToo() {
        for (int i = 0; i < 200; i++) {
            // the sideways dance: W held, the strafe finger crosses from one key to the other
            final int held = beatLength(new KeyBeat(new Random(i)), W_D, W_A);
            assertTrue("a strafe flip under W was answered immediately", held >= Cadence.KEY_BEAT_MIN);
            assertTrue("a strafe flip under W stalled for " + held + " ticks", held <= Cadence.KEY_BEAT_MAX);
        }
    }

    @Test
    public void lettingGoAndPressingAnewIsNotAReversal() {
        final KeyBeat beat = new KeyBeat(new Random(7));
        assertEquals(W_A, beat.tick(W_A, true));
        // W+A to W is a finger lifting; W to W+D is a finger landing. Neither crosses an opposite key
        assertEquals(W, beat.tick(W, true));
        assertEquals(W_D, beat.tick(W_D, true));
    }

    @Test
    public void aQuarterTurnDoesNotCostAReversalBeat() {
        final KeyBeat beat = new KeyBeat(new Random(8));
        for (int i = 0; i < 5; i++) {
            beat.tick(W, true);
        }
        // ninety degrees, a change of direction but not a turn-around: the keys answer the same tick
        assertEquals(A, beat.tick(A, true));
    }

    @Test
    public void addingTheBrakeKeysIsNotAReversal() {
        final KeyBeat beat = new KeyBeat(new Random(9));
        for (int i = 0; i < 5; i++) {
            beat.tick(W, true);
        }
        // W to W+S is somebody braking with their keyboard, on purpose
        assertEquals(W_S, beat.tick(W_S, true));
    }

    @Test
    public void carefulWalkingAnswersTheTickItIsAsked() {
        final KeyBeat beat = new KeyBeat(new Random(10));
        for (int i = 0; i < 5; i++) {
            beat.tick(W, true);
        }
        // the slab and soul-sand backstep: the movement backs away from a drop on the tick it decides to,
        // and a beat of standing would be a beat of walking off the edge
        assertEquals(S, beat.tick(S, false));
    }

    @Test
    public void nothingIsHeldPastItsTime() {
        final KeyBeat beat = new KeyBeat(new Random(11));
        for (int i = 0; i < 5; i++) {
            beat.tick(W, true);
        }
        beat.tick(S, true); // a beat has begun
        for (int i = 0; i < 5; i++) {
            assertEquals("a key was pressed that nothing asked for", NONE, beat.tick(NONE, true));
        }
    }

    @Test
    public void aCarefulReversalCutsABeatShort() {
        final KeyBeat beat = new KeyBeat(new Random(12));
        for (int i = 0; i < 5; i++) {
            beat.tick(W, true);
        }
        beat.tick(S, true); // a beat has begun at a run
        // the player comes to their senses and starts edging back: the keys answer now
        assertEquals(S, beat.tick(S, false));
    }

    @Test
    public void theKeysAlwaysLandWithinOneBeat() {
        final Random rand = new Random(13);
        for (int seed = 0; seed < 100; seed++) {
            final KeyBeat beat = new KeyBeat(new Random(seed));
            KeyBeat.Keys desired = W;
            beat.tick(desired, true);
            for (int run = 0; run < 50; run++) {
                // pick the next direction and hold it: it has to land within the beat, whatever came before
                desired = switch (rand.nextInt(4)) {
                    case 0 -> W;
                    case 1 -> S;
                    case 2 -> A;
                    default -> D;
                };
                int waited = 0;
                while (waited <= Cadence.KEY_BEAT_MAX) {
                    final KeyBeat.Keys applied = beat.tick(desired, true);
                    if (applied.equals(desired)) {
                        break;
                    }
                    waited++;
                }
                assertTrue("the keys never landed on " + desired + " after " + Cadence.KEY_BEAT_MAX + " ticks", waited <= Cadence.KEY_BEAT_MAX);
            }
        }
    }

    @Test
    public void theKeysNeverSaySomethingTheMovementDidNot() {
        final Random rand = new Random(14);
        for (int seed = 0; seed < 100; seed++) {
            final KeyBeat beat = new KeyBeat(new Random(seed));
            for (int i = 0; i < 2000; i++) {
                final KeyBeat.Keys desired = new KeyBeat.Keys(rand.nextBoolean(), rand.nextBoolean(), rand.nextBoolean(), rand.nextBoolean());
                final KeyBeat.Keys applied = beat.tick(desired, rand.nextBoolean());
                if (!applied.equals(NONE)) {
                    assertEquals("the hands pressed keys nothing asked for", desired, applied);
                }
                if (desired.isEmpty()) {
                    assertEquals("a key outlived its use", NONE, applied);
                }
            }
        }
    }

    @Test
    public void aResetForgetsWhatTheFingersWereDoing() {
        final KeyBeat beat = new KeyBeat(new Random(15));
        for (int i = 0; i < 5; i++) {
            beat.tick(W, true);
        }
        beat.reset();
        // handed the keys again after somebody else had them: no beat, the hands come down on S
        assertEquals(S, beat.tick(S, true));
    }

    @Test
    public void bothDirectionsInOneHandIsABrakeAndNotAReversal() {
        final KeyBeat beat = new KeyBeat(new Random(16));
        for (int i = 0; i < 5; i++) {
            beat.tick(W, true);
        }
        assertEquals(W_S, beat.tick(W_S, true));
        // and letting go of the brake key is instant, too
        assertEquals(S, beat.tick(S, true));
    }

    @Test
    public void everyDrawIsInsideTheRange() {
        final Random rand = new Random(17);
        for (int i = 0; i < 10000; i++) {
            final int drawn = Cadence.keyBeat(rand);
            assertTrue("drawn " + drawn + " below the minimum", drawn >= Cadence.KEY_BEAT_MIN);
            assertTrue("drawn " + drawn + " above the maximum", drawn <= Cadence.KEY_BEAT_MAX);
        }
    }
}
