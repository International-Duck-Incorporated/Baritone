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

import baritone.api.utils.Rotation;
import baritone.api.utils.input.Input;
import net.minecraft.util.Mth;

import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * One of the eight ways a person can walk with four keys: which keys, and which way they take the player.
 * <p>
 * This is how the bot changes walking direction without turning - it presses a different combination, which
 * is the only way a keyboard has ever changed a person's mind about which way to walk - and the combination
 * picked is the one that points closest to where the movement wants to go. The angle to hold the camera at
 * while a combination is pressed is here too, next to the combinations it is paired with.
 */
public record MovementOption(Input input1, Input input2, float motionX, float motionZ) {

    /** how far apart two neighbouring combinations are */
    private static final float STEP = 45f;
    /** half of that: the most a person can be out by with four keys, and the most a camera needs to turn */
    private static final float HALF_STEP = STEP / 2f;

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
     * The most steps the camera is ever left behind the way the feet are going: one, which is forty-five
     * degrees, which is the diagonal a person walks with two keys while looking where they are going.
     * <p>
     * Without this the camera was anchored to whatever step it happened to be nearest and the keys covered
     * the whole of the rest of the turn, which is a bot that walks a route backwards on S because it was
     * facing away when it was told to go, and crab-walks every corner on A or D with its head still on the
     * old heading. Four keys can press those, and nobody uses them for more than a step or two: the body
     * goes where the eyes go, within a diagonal.
     */
    private static final int MAX_STEPS = 1;

    /**
     * The yaw to look along while the keys for {@code idealYaw} are pressed, so that they come out pointing
     * at it.
     * <p>
     * The camera turns in whole steps of {@code STEP} degrees - the same grid the combinations sit on -
     * and the keys cover the rest of the turn, which is why the snap is anchored at the direction the
     * movement wants rather than at the direction the player happens to be facing: the remainder is then a
     * whole number of steps, something a set of keys can actually press. Of the steps on that grid, the one
     * nearest the player's facing is held, within {@link #MAX_STEPS} of the direction asked for: inside half
     * a step of it the camera faces it outright and the keys are W, and anywhere further round the camera
     * is brought to the diagonal on the near side and the keys are W with A or D. A camera already on the
     * grid is never asked to turn more than half a step, and the direction of travel is never more than a
     * diagonal off wherever the camera is told to be.
     * <p>
     * This has to be computed together with the keys and not separately from them, because the game applies
     * them together: what the pressed combination means is measured from the yaw the player is holding at
     * the moment the input is turned into motion, which is the angle this returns. Picking the camera angle
     * with one rounding and the keys with another lets the two disagree by a whole step, and the bot then
     * walks a whole step away from the line its path follows.
     *
     * @param facingYaw the yaw the player is currently looking along
     * @param idealYaw  the yaw of the direction the movement wants to go in
     * @return the yaw to hold while the combination for {@code idealYaw} is pressed
     */
    public static float lookYaw(float facingYaw, float idealYaw) {
        final float offset = Rotation.normalizeYaw(facingYaw - idealYaw);
        final int steps = Math.max(-MAX_STEPS, Math.min(MAX_STEPS, Mth.floor((offset + HALF_STEP) / STEP)));
        return Rotation.normalizeYaw(idealYaw + STEP * steps);
    }

    /**
     * The same, for a walk that has been going the same way long enough for the head to have straightened
     * onto it.
     * <p>
     * The diagonal is how a person walks the first step or two of a new heading, and a staircase of them -
     * the zig-zag a path makes toward a goal off the axes - is walked facing the diagonal the whole way, the
     * keys alternating under a steady head. A long straight is not: a few blocks in, the head comes round to
     * face the way the feet are going and the keys become W alone. Which of the two a walk is at is the
     * caller's to say ({@link StraightRun}); when it is settled the camera is told the direction itself and
     * the keys are the forward key, which is the same walk at a different angle of the head.
     *
     * @param settled whether the walk has gone this way for long enough to face it outright
     */
    public static float lookYaw(float facingYaw, float idealYaw, boolean settled) {
        return settled ? Rotation.normalizeYaw(idealYaw) : lookYaw(facingYaw, idealYaw);
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
