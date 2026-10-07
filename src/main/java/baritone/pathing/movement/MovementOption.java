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

import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * One of the eight ways a person can walk with four keys: which keys, and which way they take the player.
 * <p>
 * This is how the bot changes walking direction without turning - it presses a different combination, which
 * is the only way a keyboard has ever changed a person's mind about which way to walk - and the combination
 * picked is the one that points closest to where the movement wants to go.
 */
public record MovementOption(Input input1, Input input2, float motionX, float motionZ) {

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

    /**
     * How far off the direction asked for this combination would take the player: 0 for exactly on it, 1
     * for a right angle, 2 for exactly opposite.
     * <p>
     * An angle, and deliberately not a distance. This used to be the sum of the component differences, which
     * is not the same thing twice over: it depends on which way the comparison happens to be oriented (two
     * pairs of directions equally far apart can score differently), and it silently made the length of the
     * vector part of the choice, so the forward options - which were scaled by a sprinter's longer stride -
     * compared as if they moved further than the movement had asked for. Between them, the combination
     * chosen could point up to 51 degrees off the direction the movement wanted, when four keys can only
     * ever be 22.5 degrees wrong, and a bot walking half a step sideways at every option boundary is exactly
     * the kind of thing this fork is here to not do.
     */
    public float directionError(float idealX, float idealZ) {
        final float length = (float) Math.sqrt(this.motionX * this.motionX + this.motionZ * this.motionZ);
        final float idealLength = (float) Math.sqrt(idealX * idealX + idealZ * idealZ);
        if (length <= 0f || idealLength <= 0f) {
            // no direction at all: nothing is closer to it than anything else, so nothing wins
            return 2f;
        }
        return 1f - (this.motionX * idealX + this.motionZ * idealZ) / (length * idealLength);
    }

    /**
     * The key combination whose movement comes closest to the direction that was asked for.
     * <p>
     * The options are in the player's own frame - forward, back, and the six strafes - so each of them moves
     * in the direction the player is facing turned by a multiple of 45 degrees, and the one picked is the
     * one that lands nearest to where the movement wants to go. Strafing costs nothing for this: the game
     * normalizes the input vector, so a diagonal combination walks at the same speed as a straight one.
     *
     * @param motionX the world direction the player is currently moving in, x component
     * @param motionZ the same, z component
     * @param idealX  the world direction the movement wants to go in, x component
     * @param idealZ  the same, z component
     * @return the combination to press, or empty if there are somehow no options at all
     */
    public static Optional<MovementOption> closest(float motionX, float motionZ, float idealX, float idealZ) {
        return getOptions(motionX, motionZ)
                .min(Comparator.comparing(option -> option.directionError(idealX, idealZ)));
    }

    public static Stream<MovementOption> getOptions(float motionX, float motionZ) {
        return Stream.of(
                new MovementOption(Input.MOVE_FORWARD, motionX, motionZ),
                new MovementOption(Input.MOVE_BACK, -motionX, -motionZ),
                new MovementOption(Input.MOVE_LEFT, -motionZ, motionX),
                new MovementOption(Input.MOVE_RIGHT, motionZ, -motionX),
                new MovementOption(Input.MOVE_FORWARD, Input.MOVE_LEFT, motionX - motionZ, motionZ + motionX),
                new MovementOption(Input.MOVE_FORWARD, Input.MOVE_RIGHT, motionX + motionZ, motionZ - motionX),
                new MovementOption(Input.MOVE_BACK, Input.MOVE_LEFT, -motionX - motionZ, -motionZ + motionX),
                new MovementOption(Input.MOVE_BACK, Input.MOVE_RIGHT, -motionX + motionZ, -motionZ - motionX)
        );
    }
}
