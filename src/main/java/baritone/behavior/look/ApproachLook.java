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

import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalComposite;
import baritone.api.utils.interfaces.IGoalRenderPos;
import net.minecraft.core.BlockPos;

/**
 * The look onto the block a job is about, for the walk that ends at it.
 * <p>
 * A person who has decided to open a chest, mine an ore, use a table or plant a field is looking at it
 * before they get there: the eyes arrive with the feet and the last thing left to do is reach. The walk
 * has eyes of its own - the movement's own target says where the feet are going this tick, and
 * {@link GazeAhead} leads them onto the line the path takes after it - but the walk's target is the
 * <i>walk</i>. On the last stretch into something, the head is still on the line the path takes and only
 * comes onto the job once the feet have stopped, when the process that wanted it asks for it: the bot
 * walks up to a chest facing the wall beside it and turns at the last moment. That turn is the tell.
 * <p>
 * So the gaze is offered the block the job is about, on the approach, and this class is the rule for
 * which block that is and how much of the way to it the eyes may come. The offer goes into the same lean
 * the path already uses ({@link GazeAhead#offer}), which is where the rest of the rules live: it is still
 * only ever the gaze and never the movement's own rotation - the keys are turned by the movement's target
 * and chosen against the aim, see {@code MovementHelper#moveTowardsWithSlightRotation} - it is still
 * nothing at all on a target that is about to be clicked, and a lean not taken is still gone on the next
 * tick.
 * <p>
 * Nothing here knows where the player is: the caller measures the distance and the angle off the walk and
 * asks {@link #weight} what to do about them, so the rules can be tested without a world.
 * {@code ApproachLookTest} covers them.
 */
public final class ApproachLook {

    /**
     * How close the block has to be, in blocks, before the eyes come onto it: the last few steps of the
     * walk. Further off than this and they belong on the way - a head fixed on something six blocks away
     * while the feet are walking a path is a head that has stopped reading the path.
     */
    public static final double RANGE = 6.0;

    /**
     * The stretch of the approach the lean comes on over: nothing at {@link #RANGE}, its full share with
     * two blocks to go. A weight that stepped from nothing to everything at a distance would flicker at
     * that distance every time a walk crossed it, and a walk on a path does cross it.
     */
    public static final double RAMP = 4.0;

    /**
     * The share of the way to the block the eyes come at their strongest: half, as
     * {@link GazeAhead#LEAN}. What is deliberately left undone is the last half. The aim that has to land
     * on a block is the thing a click's own arrival question is asked about, and the lean is not what puts
     * the crosshair on anything; stopping short also keeps the walk visibly the subject of the look, which
     * is what the lean onto the line ahead holds itself to for the same reason.
     */
    public static final double LEAN = GazeAhead.LEAN;

    /**
     * How far off the walk the block may be, in degrees, before it stops being something on the way and
     * becomes something to the side: sixty, the over-the-shoulder glance the lean onto the line ahead
     * already holds itself to. Past that, a head turned onto it is a head that is not reading where the
     * feet are going.
     */
    public static final float MAX_OFF_THE_WALK = 60f;

    /**
     * The band before that bound over which the lean fades out, in degrees: full strength up to forty off
     * the walk, nothing at sixty. Same reason as the ramp - a look that ends at one angle and begins again
     * at the next is a flicker at the angle between them.
     */
    public static final float OFF_THE_WALK_FADE = 20f;

    /**
     * Below this many blocks the block is the one the player is standing in - the goal of a plain
     * {@code goto} is the block the walk ends in - and there is no direction to it to look along.
     */
    public static final double UNDERFOOT = 0.75;

    private ApproachLook() {
    }

    /**
     * The one block a goal is about, or null when it is not about one.
     * <p>
     * The goals that name a block are exactly the ones that implement {@link IGoalRenderPos} - a position
     * to stand in ({@code GoalBlock}), a position to stand next to ({@code GoalGetToBlock}), a block two
     * perches tall ({@code GoalTwoBlocks}), the point a range is measured from ({@code GoalNear}) - and it
     * is answered by asking for it rather than by guessing: a goal that is <i>away</i> from positions
     * ({@code GoalXZ}, {@code GoalYLevel}, {@code GoalAxis}, {@code GoalInverted}, {@code GoalRunAway})
     * has no block to look at, and no heuristic can be turned back into one without inventing it.
     * <p>
     * A composite - what mining and exploring path on - is answered with the block of whichever part of it
     * sits nearest to {@code theWalkEndsAt}, which is the one the path is actually walking to. Choosing by
     * the player's own position instead would move the answer between two equally good blocks as the walk
     * wandered, and the head would swing between them; the end of the walk does not move under the walk.
     *
     * @param goal          the goal the walk was calculated towards, or null
     * @param theWalkEndsAt where the walk ends, for choosing between a composite's blocks, or null
     * @return the block the goal is about, or null when it is not about one
     */
    public static BlockPos theBlock(Goal goal, BlockPos theWalkEndsAt) {
        if (goal == null) {
            return null;
        }
        if (goal instanceof IGoalRenderPos named) {
            return named.getGoalPos();
        }
        if (goal instanceof GoalComposite composite) {
            BlockPos nearest = null;
            double closest = Double.MAX_VALUE;
            for (Goal part : composite.goals()) {
                final BlockPos block = theBlock(part, theWalkEndsAt);
                if (block == null) {
                    continue;
                }
                final double distance = theWalkEndsAt == null ? 0.0 : block.distSqr(theWalkEndsAt);
                if (distance < closest) {
                    closest = distance;
                    nearest = block;
                }
            }
            return nearest;
        }
        return null;
    }

    /**
     * How much of the way to the block the gaze may come this tick: {@link #LEAN} at the strongest, 0 for
     * not at all.
     * <p>
     * Three things take it away, all of them over a distance rather than at a cutoff, because a look that
     * appears is a snap however small the angle it appears at: the block being far ({@link #RANGE},
     * {@link #RAMP}), the block being off the walk rather than on it ({@link #MAX_OFF_THE_WALK},
     * {@link #OFF_THE_WALK_FADE}), and the block being the one the player is standing in
     * ({@link #UNDERFOOT}).
     *
     * @param distance         how far the block is from the player's feet, in blocks
     * @param angleOffTheWalk  how far off the direction the feet are going it is, in degrees, from
     *                         {@link #angleOffTheWalk}
     * @return the weight to offer, between 0 and {@link #LEAN}
     */
    public static double weight(double distance, float angleOffTheWalk) {
        if (distance >= RANGE || distance <= UNDERFOOT || angleOffTheWalk >= MAX_OFF_THE_WALK) {
            return 0.0;
        }
        final double closedIn = Math.min(1.0, (RANGE - distance) / RAMP);
        final double onTheWalk = Math.min(1.0, (MAX_OFF_THE_WALK - angleOffTheWalk) / OFF_THE_WALK_FADE);
        return LEAN * closedIn * onTheWalk;
    }

    /**
     * The angle between where the feet are going and where the block is, both as horizontal directions from
     * the player, in degrees: 0 straight ahead, 90 to the side, 180 behind.
     * <p>
     * A direction of no length - no walk at all, or a block on top of the player - is answered as 180,
     * which is the answer that asks for no lean.
     */
    public static float angleOffTheWalk(double walkX, double walkZ, double toItX, double toItZ) {
        final double walkLength = Math.sqrt(walkX * walkX + walkZ * walkZ);
        final double toItLength = Math.sqrt(toItX * toItX + toItZ * toItZ);
        if (walkLength <= 0.0 || toItLength <= 0.0) {
            return 180f;
        }
        final double cosine = (walkX * toItX + walkZ * toItZ) / (walkLength * toItLength);
        return (float) Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cosine))));
    }
}
