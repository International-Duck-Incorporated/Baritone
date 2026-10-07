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
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * How the bot changes walking direction without turning: it presses a different combination of the same
 * four keys, chosen by which combination lands closest to the direction the movement asked for. That
 * choice is the difference between walking toward a block and walking away from it, and it is made from
 * float comparisons of units vectors, so it is worth pinning down rather than trusting.
 */
public class MovementOptionTest {

    private static final float DEG = (float) (Math.PI / 180);

    /**
     * The chosen combination for a player facing {@code facing} and a movement that wants {@code ideal},
     * both in degrees, exactly as the movement code asks the question.
     */
    private static MovementOption choose(float facing, float ideal, boolean canSprint) {
        final Optional<MovementOption> choice = MovementOption.closest(
                (float) Math.sin(facing * DEG), (float) Math.cos(facing * DEG),
                (float) Math.sin(ideal * DEG), (float) Math.cos(ideal * DEG),
                canSprint);
        assertTrue("no option was chosen at all", choice.isPresent());
        return choice.get();
    }

    @Test
    public void straightAheadIsForwardAndNothingElse() {
        for (boolean canSprint : new boolean[]{false, true}) {
            for (float facing = -180f; facing < 180f; facing += 15f) {
                final MovementOption choice = choose(facing, facing, canSprint);
                assertEquals("a movement straight ahead of the player for a " + facing + " degree facing",
                        Input.MOVE_FORWARD, choice.input1());
                assertNull("a movement straight ahead also strafed", choice.input2());
            }
        }
    }

    @Test
    public void straightBehindIsBackAndNothingElse() {
        // Backing up is the one reversal a person does press keys for, and getting it wrong here means
        // walking further away from the block every tick, which is how a bot ends up in a wall.
        for (boolean canSprint : new boolean[]{false, true}) {
            for (float facing = -180f; facing < 180f; facing += 15f) {
                final MovementOption choice = choose(facing, facing + 180f, canSprint);
                assertEquals("a movement directly behind a " + facing + " degree facing",
                        Input.MOVE_BACK, choice.input1());
                assertNull("a movement directly behind also strafed", choice.input2());
            }
        }
    }

    @Test
    public void theKeysNeverWalkAwayFromWhereTheMovementAsked() {
        // Every combination is within 45 degrees of some direction and the options cover the circle at 45
        // degree spacing, so whatever is chosen has to have a positive dot product with what the movement
        // asked for. This is the property that makes keys safe to steer with at all: a wrong choice costs
        // a little sideways drift, never a walk in the opposite direction.
        float worst = 1f;
        for (boolean canSprint : new boolean[]{false, true}) {
            for (float facing = -180f; facing < 180f; facing += 5f) {
                for (float ideal = -180f; ideal < 180f; ideal += 5f) {
                    final MovementOption choice = choose(facing, ideal, canSprint);
                    // the option's motion was built in the player's frame, so it is already a world vector
                    final float choiceX = choice.motionX();
                    final float choiceZ = choice.motionZ();
                    final float idealX = (float) Math.sin(ideal * DEG);
                    final float idealZ = (float) Math.cos(ideal * DEG);
                    final float length = (float) Math.sqrt(choiceX * choiceX + choiceZ * choiceZ);
                    final float dot = (choiceX * idealX + choiceZ * idealZ) / length;
                    assertTrue("facing " + facing + " toward " + ideal + " chose a combination pointing "
                            + dot + " of the way back", dot > 0f);
                    worst = Math.min(worst, dot);
                }
            }
        }
        // and not merely "forward-ish": the caller's direction is respected to within a wide but real bound,
        // which is what keeps the bot on the path it planned instead of wandering beside it
        assertTrue("the worst combination pointed only " + worst + " of the way there", worst > 0.6f);
    }

    @Test
    public void theStraightestCombinationWins() {
        // Between two options the closer one is taken, which is what makes the direction change gradual as
        // the player turns: as the facing sweeps, the combination changes function by function rather than
        // jumping about.
        for (float offset = -180f; offset < 180f; offset += 3f) {
            final MovementOption choice = choose(37f, 37f + offset, true);
            for (MovementOption other : MovementOption.getOptions(
                    (float) Math.sin(37f * DEG), (float) Math.cos(37f * DEG), true).toList()) {
                assertTrue("a combination further from the direction asked for was chosen",
                        choice.distanceToSq((float) Math.sin((37f + offset) * DEG),
                                (float) Math.cos((37f + offset) * DEG))
                                <= other.distanceToSq((float) Math.sin((37f + offset) * DEG),
                                (float) Math.cos((37f + offset) * DEG)) + 1e-4f);
            }
        }
    }
}
