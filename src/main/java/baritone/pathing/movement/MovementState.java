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

import baritone.api.pathing.movement.MovementStatus;
import baritone.api.utils.Rotation;
import baritone.api.utils.input.Input;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class MovementState {

    private MovementStatus status;
    private MovementTarget target = new MovementTarget();
    private final Map<Input, Boolean> inputState = new HashMap<>();

    public MovementState setStatus(MovementStatus status) {
        this.status = status;
        return this;
    }

    public MovementStatus getStatus() {
        return status;
    }

    public MovementTarget getTarget() {
        return this.target;
    }

    public MovementState setTarget(MovementTarget target) {
        this.target = target;
        return this;
    }

    public MovementState setInput(Input input, boolean forced) {
        this.inputState.put(input, forced);
        return this;
    }

    public Map<Input, Boolean> getInputStates() {
        return this.inputState;
    }

    public static class MovementTarget {

        /**
         * Yaw and pitch angles that must be matched
         */
        public Rotation rotation;

        /**
         * Whether this target is about acting on a block rather than about where the movement is going.
         * <p>
         * {@code true} if we're trying to place or break blocks, {@code false} if we're trying to look at the
         * movement location. It is named after the question rather than the answer because the answer is not
         * a yes or no: whether a rotation is applied visibly or quietly is {@link
         * baritone.behavior.LookBehavior}'s business, and this is what it asks. It used to be called
         * {@code forceRotations}, which reads as a demand that the rotations be applied, and the walking
         * target of every horizontal movement was flagged with it as though it were one - so under free look
         * the player's camera was driven for a walk and left alone for a jump, which is not a distinction
         * anybody asked for.
         */
        private boolean blockInteract;

        public MovementTarget() {
            this(null, false);
        }

        public MovementTarget(Rotation rotation, boolean blockInteract) {
            this.rotation = rotation;
            this.blockInteract = blockInteract;
        }

        public final Optional<Rotation> getRotation() {
            return Optional.ofNullable(this.rotation);
        }

        public boolean isBlockInteract() {
            return this.blockInteract;
        }
    }
}
