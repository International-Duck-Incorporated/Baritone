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
 * How quickly this particular turn is being made: the hand does not take a turn at the same speed twice.
 * <p>
 * Nearly everything else about a turn already varies - where the target is, how far off it is, whether a
 * click is waiting at the end of it - but the trajectory itself did not. {@code AimDynamics} is a function
 * of the angle, the tick and the player's sensitivity, so the same ninety degrees at the default sensitivity
 * was the same eight ticks of the same eight steps: the same numbers on every corner of the same route, for
 * as long as somebody watched. Nothing about a person works that way. How quickly a hand crosses the desk
 * depends on how much of a hurry its owner is in, how well they know what they are reaching for, and a dozen
 * things that never appear in the angle, and the result is that a person's turn to the same place is never
 * quite the same turn twice.
 * <p>
 * So a turn draws its own tempo when it begins: {@link #MIN} to {@link #MAX} of the ordinary speed, handed to
 * {@code AimDynamics.humanStep}. It is not a speed limit and it is not allowed to become one - a pace beyond
 * the flick ceiling is held at the ceiling by the stepper, so this changes how long a turn takes and never
 * how fast a hand can move. {@link #begin()} is called once per turn rather than once per tick: a hand that
 * drew afresh every tick would not have a tempo at all, it would be the per-tick shiver the tremor's own
 * smoothing was added to remove. {@link #pace()} is read on every tick of the turn in between.
 * <p>
 * Free of Minecraft and of any mutable state beyond the tempo itself, so {@code TurnPaceTest} can hold it to
 * its range. The {@link Random} comes in through the constructor, the way every other drawn rule in this
 * package takes its randomness.
 */
public final class TurnPace {

    /**
     * The slowest a hand's tempo runs, as a fraction of the ordinary turn speed.
     * <p>
     * Fifteen percent either way is a turn that is unhurried rather than a different turn: measured against
     * the flick ceiling on the biggest turns this is one tick of difference, and on the small corrections
     * that make up most of a walk it is a hair. What it is not is a number small enough to see in the
     * trajectory - a pace of half would be a bot that has stopped turning - or large enough to change what
     * anybody thinks they are watching.
     */
    public static final float MIN = 0.85f;

    /**
     * The quickest, before the ceiling has its say. Applied to the spin-up as well as to the step, so a
     * quicker turn is one that gets going quicker rather than one that moves further per tick.
     */
    public static final float MAX = 1.15f;

    private final Random rand;

    /**
     * The tempo of the turn under way. Exactly one - the ordinary speed, the way the aim moved before there
     * was a tempo at all - until a turn has begun, so a caller that never starts one behaves as it always did.
     */
    private float pace = 1.0f;

    public TurnPace() {
        this(new Random());
    }

    public TurnPace(Random rand) {
        this.rand = rand;
    }

    /**
     * A turn is starting: draw the tempo it will be made at.
     * <p>
     * Called where the hand's bookkeeping starts a turn - the angle it was at when the turn began, and the
     * age of the turn - so that the draw lands on the same tick those do and not on some tick of its own.
     *
     * @return the tempo drawn, for a caller that wants to hand it straight on
     */
    public float begin() {
        this.pace = MIN + this.rand.nextFloat() * (MAX - MIN);
        return this.pace;
    }

    /**
     * How quickly the turn under way is being made, as a fraction of the ordinary speed.
     */
    public float pace() {
        return this.pace;
    }
}
