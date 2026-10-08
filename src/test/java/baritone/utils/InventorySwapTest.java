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

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InventorySwapTest {

    @Test
    public void aSwapWaitsFirst() {
        final InventorySwap swap = new InventorySwap(new Random(1));
        swap.ask(true);
        assertFalse("swap on the first tick of a request", swap.tick());
        int waited = 1;
        while (!swap.tick()) {
            waited++;
            assertTrue("swap windup outlived its maximum", waited <= Cadence.INVENTORY_SWAP_MAX);
        }
        assertTrue("swap windup shorter than its minimum", waited >= Cadence.INVENTORY_SWAP_MIN);
    }

    @Test
    public void noSwapWhenNotAsked() {
        final InventorySwap swap = new InventorySwap(new Random(2));
        swap.ask(false);
        assertFalse("swap executed when not asked", swap.tick());
    }

    @Test
    public void waitingReportsCorrectly() {
        final InventorySwap swap = new InventorySwap(new Random(3));
        assertFalse("waiting before asked", swap.waiting());
        swap.ask(true);
        assertTrue("waiting after asked", swap.waiting());
        while (swap.waiting()) {
            swap.tick();
        }
        assertFalse("waiting after execution", swap.waiting());
    }

    @Test
    public void consecutiveSwapsEachWait() {
        final InventorySwap swap = new InventorySwap(new Random(4));
        swap.ask(true);
        int firstWait = 0;
        while (!swap.tick()) {
            firstWait++;
        }
        swap.ask(true);
        int secondWait = 0;
        while (!swap.tick()) {
            secondWait++;
        }
        // Each swap should have its own windup
        assertTrue("second swap did not wait", secondWait >= Cadence.INVENTORY_SWAP_MIN);
    }

    @Test
    public void everyDrawIsWithinTheRange() {
        final Random rand = new Random(5);
        final Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            final int delay = Cadence.inventorySwap(rand);
            assertTrue("delay " + delay + " below the minimum", delay >= Cadence.INVENTORY_SWAP_MIN);
            assertTrue("delay " + delay + " above the maximum", delay <= Cadence.INVENTORY_SWAP_MAX);
            seen.add(delay);
        }
        assertEquals("the delay should cover every value in its range",
                Cadence.INVENTORY_SWAP_MAX - Cadence.INVENTORY_SWAP_MIN + 1, seen.size());
    }
}
