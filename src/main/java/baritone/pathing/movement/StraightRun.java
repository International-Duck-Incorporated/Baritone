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

import baritone.api.pathing.movement.IMovement;
import baritone.api.pathing.path.IPathExecutor;
import net.minecraft.core.BlockPos;

import java.util.AbstractList;
import java.util.List;

/**
 * How long a walk has been going the same way, which is what decides whether the head has straightened
 * onto it.
 * <p>
 * A walk is held on the diagonal for its first steps - see {@link MovementOption#lookYaw(float, float,
 * boolean)} - and comes round to face the way it is going once it has gone that way for a while. "A while"
 * is counted in blocks rather than ticks because the path already says it: a straight is a run of
 * movements in the same direction, and a zig-zag is a run of none. Two blocks is the number. One would
 * turn the head at every second block of the two-to-one slope a path takes toward a goal just off its
 * axis; two walks that slope on a steady diagonal, as a person walks it, and turns the head onto a real
 * straight at its third block, which is about a second after the corner at a run.
 * <p>
 * Free of the executor that drives it beyond the two things it needs to read, so the rule can be tested.
 * {@code StraightRunTest} covers it.
 */
public final class StraightRun {

    private StraightRun() {
    }

    /**
     * How many movements in a row have to have gone the same way before this one for the head to face it.
     */
    public static final int SETTLED_AFTER = 2;

    /**
     * How many movements in a row before {@code position} go the same horizontal way as the one at it.
     *
     * @param directions the direction of each movement of the path, in order
     * @param position   which movement is being walked
     * @return the length of the straight run ending at {@code position}, not counting it
     */
    public static int before(List<BlockPos> directions, int position) {
        if (position < 0 || position >= directions.size()) {
            return 0;
        }
        final BlockPos direction = directions.get(position);
        int run = 0;
        for (int i = position - 1; i >= 0; i--) {
            final BlockPos previous = directions.get(i);
            if (previous.getX() != direction.getX() || previous.getZ() != direction.getZ()) {
                break;
            }
            run++;
        }
        return run;
    }

    /**
     * Whether the walk at {@code position} has gone its way for long enough to face it outright.
     */
    public static boolean settled(List<BlockPos> directions, int position) {
        return before(directions, position) >= SETTLED_AFTER;
    }

    /**
     * The same, asked of the path being executed, for the movement asking.
     *
     * @param executor the path under way, or null when there is none
     * @param movement the movement asking, which has to be the one the executor is at: a movement being
     *                 ticked by anything else - a process stepping it by hand - is nowhere on a straight
     * @return whether to face the way, or false when there is no path to read the answer from
     */
    public static boolean settled(IPathExecutor executor, IMovement movement) {
        if (executor == null) {
            return false;
        }
        final List<IMovement> movements = executor.getPath().movements();
        final int position = executor.getPosition();
        if (position < 0 || position >= movements.size() || movements.get(position) != movement) {
            return false;
        }
        return settled(new AbstractList<BlockPos>() {
            @Override
            public BlockPos get(int index) {
                return movements.get(index).getDirection();
            }

            @Override
            public int size() {
                return movements.size();
            }
        }, position);
    }
}
