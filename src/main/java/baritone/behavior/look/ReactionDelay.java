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

import java.util.Random;

/**
 * The beat between noticing where the aim should go and starting to move it there.
 * <p>
 * A hand does not begin travelling the same tick the tile changes: there is a reaction, and it costs
 * roughly a fifth of a second. Starting the turn on the tick the target appears is a small thing but it
 * is the kind of small thing that reads as a machine, because a person's own movements never line up that
 * precisely with the moment something changed.
 * <p>
 * Only a real change of direction is worth a reaction, so the delay is armed when the target appears or
 * moves by more than {@link #NEW_DIRECTION_ANGLE}, and not while the aim is already tracking something
 * (mining, or walking a straight line) where the target nudges forward a fraction of a degree every tick.
 * <p>
 * Free of Minecraft and of the behaviour that drives it, so the rules can be tested. {@code
 * ReactionDelayTest} covers it. Whether it is *allowed* to hold at all is the caller's business: the
 * movement code reads the player's facing to decide which keys to press, so the aim may only lag while the
 * player is standing still.
 */
public final class ReactionDelay {

    /**
     * How far the target has to move, in degrees, to count as a new direction rather than tracking.
     */
    public static final float NEW_DIRECTION_ANGLE = 25.0f;

    /**
     * How long the hand stays still first: two to four ticks, 100 to 200 milliseconds.
     */
    public static final int HOLD_MIN = 2;
    public static final int HOLD_MAX = 4;

    private final Random rand;

    private Rotation lastTarget;
    private int hold;
    private int heldFor;

    public ReactionDelay() {
        this(new Random());
    }

    /**
     * Takes the source of the react times, for the tests.
     * <p>
     * The same arrangement as {@link HeadWander}: in game this is seeded from the clock and nobody cares
     * which beat they get, and a test that draws from the clock is one that passes on the machine that wrote
     * it and fails on somebody else's once a week.
     */
    public ReactionDelay(Random rand) {
        this.rand = rand;
    }

    /**
     * Advances one tick.
     *
     * @param target where the aim is wanted this tick
     * @return whether the hand should stay still for this tick
     */
    public boolean holding(Rotation target) {
        if (this.lastTarget == null
                || AimDynamics.separation(this.lastTarget, target) > NEW_DIRECTION_ANGLE) {
            this.hold = HOLD_MIN + this.rand.nextInt(HOLD_MAX - HOLD_MIN + 1);
        }
        this.lastTarget = target;
        if (this.hold > 0 && this.heldFor < HOLD_MAX) {
            this.hold--;
            this.heldFor++;
            return true;
        }
        // Out of hold, or out of patience: the hand moves this tick, whatever the target is doing. A stream
        // of new directions - two targets a tick apart, alternating - would otherwise re-arm the pause every
        // single tick and leave the aim frozen where it started, and anything waiting for the aim to arrive
        // (a block being placed, a face being broken) would wait forever. Holding is never allowed to stop
        // progress; the worst it can cost is a couple of ticks per movement.
        this.hold = 0;
        this.heldFor = 0;
        return false;
    }

    /**
     * Forgets where the aim was last wanted, so that the next target gets its own reaction.
     */
    public void clear() {
        this.lastTarget = null;
        this.hold = 0;
        this.heldFor = 0;
    }
}
