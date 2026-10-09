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

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Random;

/**
 * The clicks of one move of items that takes more than one window click. The elytra auto-swap is the one
 * that exists: it picks the spare elytra up, swaps it with the worn one, and puts the worn one back, and
 * each of those three is a click of its own.
 * <p>
 * The gaps between the clicks are ordinary inventory timing - the configured average, with
 * {@link Cadence#jitter}'s tick of slack drawn afresh each time ({@code ticksBetweenInventoryMoves}), the
 * first click available as soon as there is one - which is what the auto-swap has done since Tempo-2's
 * {@code fe0a9d55} spaced it. What this class adds is the rule about <em>where</em> they land.
 * <p>
 * A window click carries the id of the window it was written for, and the game drops a click whose id is
 * not the menu that is open in front of the player. So a sequence fired while a chest or a crafting table
 * is open does not fail loudly, it stalls half applied: the spare elytra on the cursor and the worn one
 * still on the back, which is worse than either end of the move. A person swapping an elytra with a chest
 * open is holding the elytra out over the chest.
 * <p>
 * So the clicks wait for the player's own inventory to be the menu in front, and the sequence is treated
 * as a sequence rather than as three clicks that happen to be queued together:
 * <ul>
 * <li>a sequence that has <em>not begun</em> when its window is gone is given up whole, silently. Nothing
 * has happened, the items are where they were, and the job that asked for the swap asks again if it still
 * wants it - which costs nothing, because the reason for the swap is a worn elytra that is still worn;</li>
 * <li>a sequence that <em>has begun</em> waits for the window to come back and then finishes, however long
 * that takes, because the half-applied state is the one worth avoiding.</li>
 * </ul>
 * The rest of the hand rules are the helpers' ({@code BlockBreakHelper}, {@code BlockPlaceHelper}) and the
 * inventory's own wind-up ({@code InventorySwap}, {@code InventoryBehavior#onTick}, which asks its three
 * questions - the request still alive, the setting still on, the player's own menu still open - about the
 * swap it does itself). This is the same class of rule for a move that is several clicks long.
 * <p>
 * Free of Minecraft and of the settings that feed it, so that it can be tested: {@code InventoryTransactionTest}
 * covers the wait, the give-up and the finish, and pins the gaps this class must keep.
 */
public final class InventoryTransaction {

    private final Queue<Runnable> clicks = new ArrayDeque<>();
    private final Random rand;

    /** Ticks still to wait before the next click of a sequence that has begun. */
    private int countdown;
    /** Whether a click of the sequence being drained has already gone out. */
    private boolean begun;

    public InventoryTransaction() {
        this(new Random());
    }

    public InventoryTransaction(Random rand) {
        this.rand = rand;
    }

    /**
     * Adds one click to the sequence being assembled.
     */
    public void click(Runnable click) {
        this.clicks.add(click);
    }

    /**
     * Whether a click is still waiting to go out.
     */
    public boolean waiting() {
        return !this.clicks.isEmpty();
    }

    /**
     * The end of the thing that owns this sequence: finish what has started rather than drop it.
     * <p>
     * The elytra auto-swap's clicks are drained from the behaviour that queued them, and that behaviour is
     * let go when the flight is - so a cancellation inside the gap between two of the three clicks used to
     * leave the queue simply never asked again, with the spare elytra still on the cursor and the worn one
     * still on the back. That is the exact state the window rule above exists to prevent, arrived at by a
     * different road.
     * <p>
     * This applies the same rule the class already has for a window that goes away mid-swap, with no "come
     * back" to wait for:
     * <ul>
     * <li>a sequence that <em>has not begun</em> is given up whole and silently - nothing happened, the
     * items are where they were, and the job that wanted the swap asks again if it still wants it;</li>
     * <li>a sequence that <em>has begun</em> and whose window is still the one in front is finished now,
     * remainder in one go. The gaps {@link #tick} keeps are a hand's timing and teardown is not a moment
     * anybody is watching; the inventory being right is worth more than the rhythm here;</li>
     * <li>a sequence that <em>has begun</em> whose window is <em>not</em> in front is left exactly as it
     * is. Finishing it would send clicks with a window id the game drops and then clear a queue that never
     * ran, which turns "stalled and still retryable" into "stalled and silently abandoned" - strictly
     * worse, so it is reported unfinished instead.</li>
     * </ul>
     *
     * @param windowIsOurs whether the menu the player has open is their own inventory
     * @return whether the sequence is now empty - false only for a begun sequence that could not be
     * finished, which is left untouched so a caller that wants to retry still has it
     */
    public boolean finishNow(boolean windowIsOurs) {
        if (this.clicks.isEmpty()) {
            this.begun = false;
            this.countdown = 0;
            return true;
        }
        if (!this.begun) {
            // nothing went out, so there is nothing half applied to finish - the give-up-whole rule
            this.clicks.clear();
            this.countdown = 0;
            return true;
        }
        if (!windowIsOurs) {
            // the clicks would be dropped by the game; leave the queue alone and say it did not finish
            return false;
        }
        while (!this.clicks.isEmpty()) {
            this.clicks.poll().run();
        }
        this.countdown = 0;
        this.begun = false;
        return true;
    }

    /**
     * One tick of the sequence: at most one click goes out, and only while the window it was written for is
     * the one in front.
     *
     * @param windowIsOurs       whether the menu the player has open is their own inventory
     * @param ticksBetweenClicks the configured gap, read every tick rather than when the sequence was
     *                           written, so a setting changed mid-swap is obeyed like every other interval
     * @return whether a click went out on this tick
     */
    public boolean tick(boolean windowIsOurs, int ticksBetweenClicks) {
        if (this.clicks.isEmpty()) {
            this.begun = false;
            return false;
        }
        if (!windowIsOurs) {
            // a click with a window id that is not the open menu's is dropped by the game, which is how a
            // swap ends up half applied - see the class comment for what is done about it
            if (!this.begun) {
                this.clicks.clear();
                this.countdown = 0;
            }
            return false;
        }
        if (this.countdown > 0) {
            this.countdown--;
            return false;
        }
        this.clicks.poll().run();
        this.begun = !this.clicks.isEmpty();
        // the gap is counted from the tick the click went out on: the configured interval, drawn afresh,
        // so a setting of one tick puts the next click on the next tick, which is what the auto-swap did
        // before this class existed and is what "the setting is the average interval" means here
        this.countdown = this.begun ? Math.max(0, Cadence.jitter(this.rand, ticksBetweenClicks) - 1) : 0;
        return true;
    }
}
