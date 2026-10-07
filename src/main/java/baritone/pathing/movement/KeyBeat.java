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
 * The beat between letting go of one walking direction and pressing its opposite.
 * <p>
 * The four keys are decided from scratch every tick - see {@link MovementOption} - which means a change of
 * direction arrives as a change of key states, and the old pair of them went from held to gone in exactly
 * one tick. Forward to back is the worst of it: one tick the finger is on W, the next tick it is on S,
 * through the game's own input packet to anybody watching. No hand moves like that. A person lets go,
 * repositions, and presses the other key, and there is a beat of nothing in between.
 * <p>
 * That beat is all this is. It never presses a key nobody asked for, it never holds a key past the moment
 * something stops asking for it - a careful walk that backs away from an edge gets its keys the tick it
 * asks for them - and it is spent only where standing still for a tick is safe, which the caller decides
 * ({@code InputOverrideHandler#beatTheKeys()}). Everything that is not a walking key is this class's none
 * of business: the sneak that holds a bridge, the jump timed to a ledge, and both mouse buttons go around
 * it, exactly as they go around {@link Cadence#sprintHangover}.
 * <p>
 * Free of Minecraft and of the behaviour that drives it, so the rules can be tested. {@code KeyBeatTest}
 * covers them, over fixed seeds, the way the look machines are covered.
 */
public final class KeyBeat {

    /**
     * What the four walking keys are doing: which of them a finger is actually on this tick.
     * <p>
     * A direction rather than a set of booleans, because the question this class answers is about
     * directions: forward is one way, back is the other, and a reversal is when one turns into the other.
     * Both W and S held at once is a direction of nothing - the player is being braked by their own
     * keyboard, which is a thing people do on purpose and this class does not interfere with.
     */
    public record Keys(boolean forward, boolean back, boolean left, boolean right) {

        public static final Keys NONE = new Keys(false, false, false, false);

        /** forward and back as one axis: 1 forward, -1 back, 0 for neither or both */
        public int forwardAxis() {
            return (forward ? 1 : 0) - (back ? 1 : 0);
        }

        /** left and right as one axis: 1 left, -1 right, 0 for neither or both */
        public int leftAxis() {
            return (left ? 1 : 0) - (right ? 1 : 0);
        }

        public boolean isEmpty() {
            return !forward && !back && !left && !right;
        }

        /**
         * The way these keys walk, as an angle off the yaw the player is holding: 0 for forward, 180 for
         * back, -90 for left and 90 for right, and the diagonals between - the same arithmetic the game
         * does when it turns the pressed combination into motion. Zero for no keys, and for keys that
         * cancel out.
         */
        public float yawOffset() {
            final int forward = this.forwardAxis();
            final int left = this.leftAxis();
            if (forward == 0 && left == 0) {
                return 0f;
            }
            return (float) Math.toDegrees(Math.atan2(-left, forward));
        }

        /**
         * Whether going from this to {@code next} reverses a direction a finger is on: one axis is held
         * one way here and the other way there. Appearing out of nothing is not a reversal (the hand was
         * already moving to the key), and a key let go of is not one either (letting go is instant, always).
         */
        public boolean reverses(Keys next) {
            final int forward = this.forwardAxis();
            final int left = this.leftAxis();
            final int nextForward = next.forwardAxis();
            final int nextLeft = next.leftAxis();
            return (forward != 0 && nextForward != 0 && Integer.signum(forward) != Integer.signum(nextForward))
                    || (left != 0 && nextLeft != 0 && Integer.signum(left) != Integer.signum(nextLeft));
        }
    }

    private final Random rand;

    /**
     * What was actually under the fingers last tick, and how much longer the hands are off the keys.
     */
    private Keys pressed;
    private int beat;

    public KeyBeat() {
        this(new Random());
    }

    /**
     * Takes the source of the beat lengths, for the tests.
     * <p>
     * The same arrangement as {@code ReactionDelay} and every other machine in here: in game this is
     * seeded from the clock and nobody cares which beat they get, and a test that draws from the clock is
     * one that passes on the machine that wrote it and fails on somebody else's once a week.
     */
    public KeyBeat(Random rand) {
        this.rand = rand;
        this.pressed = Keys.NONE;
    }

    /**
     * One tick of the hands on the keys.
     *
     * @param desired what the walking keys are being asked for this tick
     * @param atPace  whether the player is running along the ground somewhere a beat of standing is safe.
     *                At a careful pace - edging along a bridge, backing away from a drop - the keys answer
     *                the tick they are asked, because the hand is not what is being careful there; the
     *                movement is.
     * @return what the fingers are actually on this tick: either {@code desired} or no keys at all
     */
    public Keys tick(Keys desired, boolean atPace) {
        if (desired.isEmpty()) {
            // nobody is asking for anything: the hands come off this tick, beat or no beat
            this.pressed = Keys.NONE;
            this.beat = 0;
            return desired;
        }
        if (!atPace) {
            // careful walking, swimming, anything mid-air: the keys answer immediately
            this.pressed = desired;
            this.beat = 0;
            return desired;
        }
        if (this.beat > 0) {
            this.beat--;
            this.pressed = Keys.NONE;
            return Keys.NONE;
        }
        if (this.pressed.reverses(desired)) {
            this.beat = Cadence.keyBeat(this.rand) - 1; // the first tick of the beat is this one
            this.pressed = Keys.NONE;
            return Keys.NONE;
        }
        this.pressed = desired;
        return desired;
    }

    /**
     * Forgets what the fingers were doing, for when the bot is handed the keys again after somebody else
     * had them. A direction that was pressed five minutes ago is not one being reversed out of.
     */
    public void reset() {
        this.pressed = Keys.NONE;
        this.beat = 0;
    }
}
