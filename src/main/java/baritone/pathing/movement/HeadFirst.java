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

/**
 * The feet wait for the head.
 * <p>
 * The body is steered by the rotation a movement asks for, on the tick it asks for it: the keys are turned
 * into motion by that angle ({@code LookBehavior#onPlayerRotationMove}) while the head eases toward it over
 * the ticks that follow. That is what lets the aim move like a hand without the walk going astray, and it
 * has one visible consequence: at a sharp turn the body sets off in the new direction while the head is
 * still pointing in the old one. A path that starts with a half turn walked backwards, as far as anybody
 * watching could tell, for the half second it took the head to come round - and nobody walks like that. A
 * person turns to face where they are going, and then goes.
 * <p>
 * So the walking keys are kept off while the head is too far from where the feet are being sent, and only
 * then. How far is too far depends on whether the player is already moving: from a standstill the head
 * leads by {@link #STANDING_LEAD}, because somebody who has decided to go somewhere looks there first; at a
 * walk only a turn sharper than {@link #WALKING_LEAD} - a reversal, in practice - stops the feet, because
 * the rest are walked round the way a person walks round a corner, with the body a little behind the eyes.
 * The lead is decided when the hold starts and kept for the length of it, so a reversal that decelerates
 * the player into standing does not have the standing rule tighten on it half way through.
 * <p>
 * The hold is a beat, never a wall. It is spent only on dry ground, where a tick of standing is free - never
 * in the air, where a key let go is a jump fallen short, and never in water, where the keys are a control
 * loop - and it gives up after {@link #MAX_HOLD} ticks whatever the head is doing, and does not take hold
 * again until that turn has actually been made, so a head that cannot turn can only ever cost one beat.
 * It never presses anything, and it never touches the sneak or either mouse button.
 * <p>
 * Free of Minecraft and of the executor that drives it, so the rules can be tested. {@code HeadFirstTest}
 * covers them.
 */
public final class HeadFirst {

    /**
     * How far the head may be from the way the feet are going before they set off from a standstill, in
     * degrees of yaw. Sixty: the feet begin while the head is finishing the turn, which is how a person
     * sets off - the turn and the first step overlap, but the turn starts first.
     */
    public static final float STANDING_LEAD = 60f;

    /**
     * The same at a walk. A right angle is walked round; anything sharper than this is a turn-around,
     * and a person turning around lets go of the keys, turns, and presses them again.
     */
    public static final float WALKING_LEAD = 100f;

    /**
     * The most ticks the feet are ever kept waiting for one turn. At the aim's own ceiling a half turn
     * comes inside {@link #STANDING_LEAD} in nine ticks; this is a safety net above that, not a target.
     */
    public static final int MAX_HOLD = 10;

    /** ticks the current hold has lasted, 0 when the feet are free */
    private int held;
    /** the lead the current hold was started under, so it does not change under a hold in progress */
    private float lead;
    /** whether the last hold ran out of patience, in which case this turn gets no second hold */
    private boolean gaveUp;

    /**
     * One tick of the feet.
     *
     * @param headBehind how far the head is from the direction the feet are being asked to go, in degrees
     *                   of yaw, as a magnitude; zero when nobody is asking the feet for anything or the
     *                   head is not Baritone's to turn
     * @param standing   whether the player is standing still
     * @param grounded   whether the player is on dry ground, where a tick of standing is safe
     * @return whether to keep the walking keys off this tick
     */
    public boolean hold(float headBehind, boolean standing, boolean grounded) {
        if (!grounded) {
            // not a place to stand: the keys answer the tick they are asked, and the turn is forgotten
            this.release();
            return false;
        }
        if (this.held == 0) {
            this.lead = standing ? STANDING_LEAD : WALKING_LEAD;
        }
        if (headBehind <= this.lead) {
            // the head is there, or near enough: the feet go, and the next turn is a fresh one
            this.release();
            this.gaveUp = false;
            return false;
        }
        if (this.gaveUp) {
            return false;
        }
        if (this.held >= MAX_HOLD) {
            // out of patience: the feet go whatever the head is doing, and are not stopped again for
            // this turn
            this.release();
            this.gaveUp = true;
            return false;
        }
        this.held++;
        return true;
    }

    /**
     * Ticks the current hold has lasted, for anything that wants to know whether the feet are waiting.
     */
    public int held() {
        return this.held;
    }

    private void release() {
        this.held = 0;
    }

    /**
     * Forgets the turn in progress, for when the keys change hands.
     */
    public void reset() {
        this.held = 0;
        this.gaveUp = false;
    }
}
