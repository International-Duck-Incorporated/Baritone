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

import java.util.Random;

/**
 * The beat before an inventory swap is executed.
 * <p>
 * A person does not swap items the instant they decide to: there is a reaction, a beat while the hand
 * finds the key and the eyes find the slot. One or two ticks - fifty to a hundred milliseconds - is about
 * as short as that reaction gets. The swap itself is still the bare window click, but the moment it
 * happens is no longer the same tick the need for it was noticed.
 */
public final class InventorySwap {

    private final Random rand;

    private int countdown;
    private boolean wanted;

    public InventorySwap() {
        this(new Random());
    }

    public InventorySwap(Random rand) {
        this.rand = rand;
    }

    /**
     * Asks for an inventory swap to be executed.
     *
     * @param wanted whether a swap is being requested this tick
     */
    public void ask(boolean wanted) {
        if (wanted && !this.wanted) {
            this.countdown = Cadence.inventorySwap(this.rand);
        }
        this.wanted = wanted;
    }

    /**
     * One tick of the clock.
     *
     * @return whether the swap should actually be executed this tick
     */
    public boolean tick() {
        if (!this.wanted) {
            return false;
        }
        if (--this.countdown > 0) {
            return false;
        }
        this.wanted = false;
        return true;
    }

    /**
     * Whether a swap has been requested and is not yet executed.
     */
    public boolean waiting() {
        return this.wanted && this.countdown > 0;
    }
}
