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
 * four keys, chosen by which combination points closest to the direction the movement asked for.
 * <p>
 * Four keys give eight directions, so the best a person can do is be half a step out, 22.5 degrees. A bot
 * that is further out than that is not walking the path it planned, and at the option boundaries - where a
 * step out is also a step sideways every tick - it is also the thing that reads as a machine: the old
 * choice routine compared component differences with the forward options scaled by a sprinter's stride,
 * and could point 51 degrees off the direction it was given. These tests are the bound that came out of
 * that, and it is the keyboard's own.
 */
public class MovementOptionTest {

    private static final float DEG = (float) (Math.PI / 180);

    /** cosine of the furthest a choice of four keys can ever be from the direction asked for */
    private static final float HALF_A_STEP = (float) Math.cos(22.5 * Math.PI / 180);

    /**
     * The chosen combination for a player facing {@code facing} and a movement that wants {@code ideal},
     * both in degrees, exactly as the movement code asks the question.
     */
    private static MovementOption choose(float facing, float ideal) {
        final Optional<MovementOption> choice = MovementOption.closest(
                (float) Math.sin(facing * DEG), (float) Math.cos(facing * DEG),
                (float) Math.sin(ideal * DEG), (float) Math.cos(ideal * DEG));
        assertTrue("no option was chosen at all", choice.isPresent());
        return choice.get();
    }

    @Test
    public void straightAheadIsForwardAndNothingElse() {
        for (float facing = -180f; facing < 180f; facing += 15f) {
            final MovementOption choice = choose(facing, facing);
            assertEquals("a movement straight ahead of the player for a " + facing + " degree facing",
                    Input.MOVE_FORWARD, choice.input1());
            assertNull("a movement straight ahead also strafed", choice.input2());
        }
    }

    @Test
    public void straightBehindIsBackAndNothingElse() {
        // Backing up is the one reversal a person does press keys for, and getting it wrong here means
        // walking further away from the block every tick, which is how a bot ends up in a wall.
        for (float facing = -180f; facing < 180f; facing += 15f) {
            final MovementOption choice = choose(facing, facing + 180f);
            assertEquals("a movement directly behind a " + facing + " degree facing",
                    Input.MOVE_BACK, choice.input1());
            assertNull("a movement directly behind also strafed", choice.input2());
        }
    }

    @Test
    public void theChoiceIsNeverMoreThanHalfAStepOff() {
        // The property that makes keys safe to steer with at all. Every combination is a multiple of 45
        // degrees from the facing, so one of the eight is never more than 22.5 degrees from the direction
        // asked for; picking the closest one means the bot is never further out than that, whatever the
        // facing and whatever the movement wants.
        float worst = -1f;
        float worstFacing = 0f;
        float worstIdeal = 0f;
        for (float facing = -180f; facing < 180f; facing += 0.25f) {
            for (float ideal = -180f; ideal < 180f; ideal += 0.25f) {
                final float error = choose(facing, ideal).directionError(
                        (float) Math.sin(ideal * DEG), (float) Math.cos(ideal * DEG));
                if (error > worst) {
                    worst = error;
                    worstFacing = facing;
                    worstIdeal = ideal;
                }
            }
        }
        final double degreesOff = Math.toDegrees(Math.acos(1.0 - worst));
        assertTrue("the worst choice was " + degreesOff + " degrees off, at facing " + worstFacing
                        + " toward " + worstIdeal + "; four keys cannot be further out than 22.5",
                degreesOff <= 22.5 + 1e-3);
        // and the boundary is actually reached, so this is the real bound and not an artefact of the sweep
        assertTrue("the sweep never even got near the bound: " + degreesOff, degreesOff > 22.4);
    }

    @Test
    public void theDirectionIsRespectedEvenInAbsoluteTerms() {
        // A weaker but very readable version of the bound above, kept because it is the one a person checks
        // by eye: the combination chosen always moves the player toward what the movement asked for, by a
        // long way, and never sideways past a right angle.
        float worst = 1f;
        for (float facing = -180f; facing < 180f; facing += 5f) {
            for (float ideal = -180f; ideal < 180f; ideal += 5f) {
                final MovementOption choice = choose(facing, ideal);
                final float interesting = 1f - choice.directionError(
                        (float) Math.sin(ideal * DEG), (float) Math.cos(ideal * DEG));
                worst = Math.min(worst, interesting);
            }
        }
        assertTrue("a combination pointing only " + worst + " of the way there was chosen", worst > 0.9f);
    }

    @Test
    public void theStraightestCombinationWins() {
        // Between two options the closer one is taken, which is what makes the direction change gradual as
        // the player turns: as the facing sweeps, the combination changes at the halfway marks rather than
        // jumping about.
        for (float offset = -180f; offset < 180f; offset += 3f) {
            final MovementOption choice = choose(37f, 37f + offset);
            for (MovementOption other : MovementOption.getOptions(
                    (float) Math.sin(37f * DEG), (float) Math.cos(37f * DEG)).toList()) {
                assertTrue("a combination further from the direction asked for was chosen",
                        choice.directionError((float) Math.sin((37f + offset) * DEG),
                                (float) Math.cos((37f + offset) * DEG))
                                <= other.directionError((float) Math.sin((37f + offset) * DEG),
                                (float) Math.cos((37f + offset) * DEG)) + 1e-6f);
            }
        }
    }
}
