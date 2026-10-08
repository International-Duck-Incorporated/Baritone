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

package baritone.utils;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The elytra auto-swap's three window clicks, and the rule about the window they land in: they wait for the
 * player's own inventory to be the menu in front, a sequence that has not begun when its window is gone is
 * given up whole, and one that has begun is finished. The gaps are the auto-swap's own timing and are pinned
 * here too, so that the rule written around them does not quietly re-time them: they are the configured
 * interval, drawn afresh, which is the shape every other hand rule in this fork has.
 */
public class InventoryTransactionTest {

    /**
     * One swap: three clicks that record the tick each of them went out on, and the transaction they were
     * queued in. The clock is the test's own, so a click's tick is a number this class can assert about.
     */
    private static final class Swap {
        private final InventoryTransaction transaction;
        private final List<Integer> ticks = new ArrayList<>();
        private int tick;

        Swap(int seed) {
            this.transaction = new InventoryTransaction(new Random(seed));
            for (int click = 0; click < 3; click++) {
                this.transaction.click(() -> this.ticks.add(this.tick));
            }
        }

        /** Advances the clock one tick and reports whether a click went out on it. */
        boolean tick(boolean windowIsOurs, int setting) {
            this.tick++;
            return this.transaction.tick(windowIsOurs, setting);
        }

        /** Queues one click of a later swap, the way the auto-swap writes one once the last has gone out. */
        void andAnother() {
            this.transaction.click(() -> this.ticks.add(this.tick));
        }

        /** Advances the clock until the swap has run every click, and returns the ticks they went out on. */
        List<Integer> runOut(int setting) {
            for (int guard = 0; guard < 500 && this.ticks.size() < 3; guard++) {
                tick(true, setting);
            }
            return this.ticks;
        }

        boolean waiting() {
            return this.transaction.waiting();
        }
    }

    @Test
    public void theFirstClickIsAvailableAtOnce() {
        final Swap swap = new Swap(1);
        assertTrue("a transaction with a click queued did nothing on its first tick", swap.tick(true, 5));
        assertEquals("more than one click went out in one tick", 1, swap.ticks.size());
        assertTrue("a transaction with clicks left is not waiting", swap.waiting());
    }

    @Test
    public void theClicksAreSpacedByTheSetting() {
        for (int setting : new int[]{1, 2, 5, 20}) {
            final Swap swap = new Swap(setting);
            final List<Integer> ticks = swap.runOut(setting);
            assertEquals("the transaction did not finish on setting " + setting + ": " + ticks,
                    3, ticks.size());
            assertEquals("the first click is not the first tick on setting " + setting + ": " + ticks,
                    Integer.valueOf(1), ticks.get(0));
            for (int i = 1; i < ticks.size(); i++) {
                final int gap = ticks.get(i) - ticks.get(i - 1);
                assertTrue("a gap of " + gap + " ticks is off the setting's band on setting " + setting
                                + ": " + ticks,
                        gap >= Math.max(1, setting - Cadence.JITTER)
                                && gap <= setting + Cadence.JITTER);
            }
            assertFalse("a finished transaction is still waiting", swap.waiting());
        }
    }

    @Test
    public void oneTickBetweenClicksIsTheNextTick() {
        // the floor of the setting: the three clicks of a swap go out on three consecutive ticks, which is
        // what the auto-swap did before the gaps were drawn and what a swap with a deadline needs
        final Swap swap = new Swap(6);
        assertEquals("a one-tick transaction is not three consecutive ticks",
                List.of(1, 2, 3), swap.runOut(1));
        assertFalse("a finished transaction is still waiting", swap.waiting());
    }

    @Test
    public void aTransactionThatHasNotBegunIsGivenUpWithItsWindow() {
        final Swap swap = new Swap(2);
        for (int tick = 0; tick < 50; tick++) {
            // a chest or a crafting table in front of the player: not the window these clicks were for
            assertFalse("a click went out into somebody else's window", swap.tick(false, 1));
        }
        assertFalse("a transaction that never began is still waiting", swap.waiting());
        assertEquals("a click went out into a window that was not the one it was written for",
                0, swap.ticks.size());
        // and it is gone for good: a window that comes back does not fire a swap that was given up
        for (int tick = 0; tick < 10; tick++) {
            swap.tick(true, 1);
        }
        assertEquals("a given-up transaction fired when its window came back", 0, swap.ticks.size());
    }

    @Test
    public void aTransactionThatHasBegunIsFinished() {
        final Swap swap = new Swap(3);
        assertTrue("the transaction did not begin", swap.tick(true, 1));
        // the window goes away with the first click of three already out: the spare elytra is on the cursor
        // and the worn one is still on the back, so this is the one state that must not be left standing
        for (int tick = 0; tick < 50; tick++) {
            swap.tick(false, 1);
        }
        assertEquals("a begun transaction was given up halfway", 1, swap.ticks.size());
        assertTrue("a begun transaction stopped waiting for its window", swap.waiting());
        for (int tick = 0; tick < 10 && swap.ticks.size() < 3; tick++) {
            swap.tick(true, 1);
        }
        assertEquals("a begun transaction did not finish when its window came back", 3, swap.ticks.size());
        assertFalse("a finished transaction is still waiting", swap.waiting());
    }

    @Test
    public void aSecondSequenceStartsAtOnce() {
        // the auto-swap asks for a swap only when none is in flight, so a second sequence begins with the
        // state the first one ended in rather than carrying its countdown
        final Swap swap = new Swap(7);
        swap.runOut(5);
        assertEquals("the first transaction did not finish", 3, swap.ticks.size());
        swap.andAnother();
        assertTrue("a new sequence waited on the old one's clock", swap.tick(true, 5));
        assertEquals("a new sequence did not begin at once", 4, swap.ticks.size());
    }

    @Test
    public void anEmptyTransactionDoesNothing() {
        final InventoryTransaction transaction = new InventoryTransaction(new Random(5));
        for (int tick = 0; tick < 20; tick++) {
            assertFalse("an empty transaction sent a click", transaction.tick(tick % 2 == 0, 5));
        }
        assertFalse("an empty transaction is waiting", transaction.waiting());
    }
}
