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

import java.util.Random;

/**
 * The walk before the sprint key goes down on a new run.
 * <p>
 * A traverse that wants to sprint used to press W and control on the same tick. The hangover already
 * covers letting go; this covers picking it up. Only spent where a couple of ticks of walking is safe
 * - the caller decides that. A hangover that is still holding the key is not a new run: {@link #keepHeld()}
 * keeps this machine in the "already sprinting" state so the next movement does not wind up in the
 * middle of a jog.
 */
public final class SprintWindup {

    private final Random rand;
    private int waiting;
    private boolean wasWanted;

    public SprintWindup() {
        this(new Random());
    }

    public SprintWindup(Random rand) {
        this.rand = rand;
    }

    /**
     * @param wanted  whether something is asking for the sprint key this tick
     * @param mayWait whether a beat of walking is safe (ground, flat, not parkour)
     * @return whether the sprint key should actually be down this tick
     */
    public boolean tick(boolean wanted, boolean mayWait) {
        if (!wanted) {
            this.waiting = 0;
            this.wasWanted = false;
            return false;
        }
        if (!mayWait) {
            this.waiting = 0;
            this.wasWanted = true;
            return true;
        }
        if (!this.wasWanted) {
            this.waiting = Cadence.sprintWindup(this.rand);
            this.wasWanted = true;
        }
        if (this.waiting > 0) {
            this.waiting--;
            return false;
        }
        return true;
    }

    /**
     * The hangover is still holding the key: this is the same run, not a new one.
     */
    public void keepHeld() {
        this.waiting = 0;
        this.wasWanted = true;
    }

    /** Carry the wind-up across a splice or trim of the same run. */
    public void copyFrom(SprintWindup other) {
        this.waiting = other.waiting;
        this.wasWanted = other.wasWanted;
    }

    public int waiting() {
        return this.waiting;
    }
}
