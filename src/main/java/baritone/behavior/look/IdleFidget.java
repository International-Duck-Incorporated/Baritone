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

import java.util.Random;

/**
 * The arm, when there is nothing for it to do.
 * <p>
 * A standing player who is not doing anything still swings at the air now and then - at nothing, at a
 * thought, at the wait. A bot that stands perfectly still for a minute is not resting, it is switched off,
 * and the arm is the part of it that reads that way most clearly because nothing else has moved either.
 * <p>
 * The swing is free: it is an animation packet, not a click, so it cannot mine or place anything no matter
 * where the crosshair happens to be pointing. That is why this is allowed the run of the idle gate at all.
 * <p>
 * Free of Minecraft so the timing can be tested; {@code IdleFidgetTest} covers it, and the behaviour that
 * drives it holds the gate - the player is standing still, Baritone has a process in control, and no click
 * is being driven.
 */
public final class IdleFidget {

    /**
     * How long the arm waits between fidgets, in ticks: ten seconds to a minute, drawn fresh each time.
     */
    public static final int WAIT_MIN = 200;
    public static final int WAIT_MAX = 1200;

    private final Random rand;

    private int wait;
    private boolean pending;

    public IdleFidget() {
        this(new Random());
    }

    /**
     * @param rand the source of variation, injectable so a run can be repeated; see {@code IdleFidgetTest}
     */
    public IdleFidget(Random rand) {
        this.rand = rand;
        this.wait = this.next();
    }

    /**
     * One tick of standing around.
     * <p>
     * The clock waits whenever the arm already has something to do, rather than running on and lining up
     * the next fidget behind it: a fidget that could not be taken on time - because the player was walking,
     * or mining - is owed the moment the arm is free, and only one of them is ever owed. A clock that kept
     * running would let a second fidget fall due during that wait and be taken a tick after the first,
     * which is two swings in a row and reads as a twitch.
     */
    public void advance() {
        if (this.pending) {
            return;
        }
        if (--this.wait > 0) {
            return;
        }
        this.pending = true;
    }

    /**
     * Takes the pending fidget, if there is one, and starts the wait for the next.
     *
     * @return whether the arm should swing this tick
     */
    public boolean take() {
        if (!this.pending) {
            return false;
        }
        this.pending = false;
        this.wait = this.next();
        return true;
    }

    private int next() {
        return WAIT_MIN + this.rand.nextInt(WAIT_MAX - WAIT_MIN + 1);
    }
}
