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
 * The beat between deciding on a hotbar slot and having it in hand.
 * <p>
 * Everything that wanted a different item in the hand - the best tool for a block, a block to throw down,
 * the builder's next material - set the selected slot itself, on the tick it decided, and the click
 * followed on the same tick or the next. That is a reaction time of zero on the one thing about a hand
 * that everybody can see: the held item changing. A person notices, finds the key, presses it, and clicks
 * once the item is there, and this is that: the asks are collected here, the key is pressed
 * {@link Cadence#hotbarSwitch} ticks after the first of them, and {@link #switching()} tells the two
 * mouse buttons to wait until it has been.
 * <p>
 * Two things it is careful about. A mind that changes while the beat is running does not start it over -
 * the key pressed is the last one decided on, when the beat is up - because two callers taking turns
 * every tick would otherwise never get a key pressed at all, and nothing would ever be clicked again. And
 * an ask for the slot already in hand is nothing to do, not a cancellation: a pending ask for some other
 * slot lands anyway, the way a key already being pressed is pressed.
 * <p>
 * Free of Minecraft, so {@code HotbarSwitchTest} can cover it; the held slot is handed in.
 */
public final class HotbarSwitch {

    private final Random rand;

    /**
     * The slot asked for and not yet in hand, or -1 for none.
     */
    private int wanted = -1;
    private int countdown;

    public HotbarSwitch() {
        this(new Random());
    }

    public HotbarSwitch(Random rand) {
        this.rand = rand;
    }

    /**
     * Asks for {@code slot} to be in hand. Asked every tick by whoever needs it, which is fine: only the
     * first ask of a switch starts the beat.
     *
     * @param slot the hotbar slot wanted, 0 to 8; anything else is ignored
     * @param held the slot in hand right now
     */
    public void ask(int slot, int held) {
        if (slot < 0 || slot >= 9 || slot == held) {
            return;
        }
        if (this.wanted < 0) {
            this.countdown = Cadence.hotbarSwitch(this.rand);
        }
        this.wanted = slot;
    }

    /**
     * One tick of the clock, after everything that asks this tick has asked.
     *
     * @param held the slot in hand right now
     * @return the slot to put in hand this tick, or -1 for none
     */
    public int tick(int held) {
        if (this.wanted < 0) {
            return -1;
        }
        if (this.wanted == held) {
            // somebody else put it there, or it was there all along
            this.wanted = -1;
            return -1;
        }
        if (--this.countdown > 0) {
            return -1;
        }
        final int slot = this.wanted;
        this.wanted = -1;
        return slot;
    }

    /**
     * Whether a slot has been asked for and is not in hand yet, which is when neither mouse button should
     * be pressed: the item that would be used is not the one that was decided on.
     */
    public boolean switching() {
        return this.wanted >= 0;
    }
}
