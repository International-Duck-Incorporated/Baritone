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

import baritone.api.utils.input.Input;
import net.minecraft.util.Mth;

import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

public record MovementOption(Input input1, Input input2, float motionX, float motionZ) {
    private static final float SPRINT_MULTIPLIER = 1.3f;

    public MovementOption(Input input1, float motionX, float motionZ) {
        this(input1, null, motionX, motionZ);
    }

    public void setInputs(MovementState movementState) {
        if (input1 != null) {
            movementState.setInput(input1, true);
        }
        if (input2 != null) {
            movementState.setInput(input2, true);
        }
    }

    public float distanceToSq(float otherX, float otherZ) {
        return Mth.abs(motionX() - otherX) + Mth.abs(motionZ() - otherZ);
    }
    
    /**
     * The key combination whose movement comes closest to the direction that was asked for.
     * <p>
     * The options are the player's own frame - forward, back, and the six strafes - so the direction each
     * of them moves in has to be turned from the direction the player is currently facing, and the one
     * picked is the one that lands nearest to where the movement wants to go. This is how the bot changes
     * walking direction without turning: it presses a different combination of the same four keys, which
     * is the only way a keyboard has ever changed a person's mind about which way to walk. Extracted from
     * the caller so that "the keys always go the way the movement asked" is a thing that can be tested.
     *
     * @param motionX  the world direction the player is currently moving in, x component
     * @param motionZ  the same, z component
     * @param idealX   the world direction the movement wants to go in, x component
     * @param idealZ   the same, z component
     * @param canSprint whether the sprinter's longer stride is available, which shifts the choice slightly
     * @return the combination to press, or empty if there are somehow no options at all
     */
    public static Optional<MovementOption> closest(float motionX, float motionZ, float idealX, float idealZ, boolean canSprint) {
        return getOptions(motionX, motionZ, canSprint)
                .min(Comparator.comparing(option -> option.distanceToSq(idealX, idealZ)));
    }

    public static Stream<MovementOption> getOptions(float motionX, float motionZ, boolean canSprint) {
        return Stream.of(
                new MovementOption(Input.MOVE_FORWARD, canSprint ? motionX * SPRINT_MULTIPLIER : motionX, canSprint ? motionZ * SPRINT_MULTIPLIER : motionZ),
                new MovementOption(Input.MOVE_BACK, -motionX, -motionZ),
                new MovementOption(Input.MOVE_LEFT, -motionZ, motionX),
                new MovementOption(Input.MOVE_RIGHT, motionZ, -motionX),
                new MovementOption(Input.MOVE_FORWARD, Input.MOVE_LEFT, (canSprint ? motionX * SPRINT_MULTIPLIER : motionX) - motionZ, (canSprint ? motionZ * SPRINT_MULTIPLIER : motionZ) + motionX),
                new MovementOption(Input.MOVE_FORWARD, Input.MOVE_RIGHT, (canSprint ? motionX * SPRINT_MULTIPLIER : motionX) + motionZ, (canSprint ? motionZ * SPRINT_MULTIPLIER : motionZ) - motionX),
                new MovementOption(Input.MOVE_BACK, Input.MOVE_LEFT, -motionX - motionZ, -motionZ + motionX),
                new MovementOption(Input.MOVE_BACK, Input.MOVE_RIGHT, -motionX + motionZ, -motionZ - motionX)
        );
    }
}
