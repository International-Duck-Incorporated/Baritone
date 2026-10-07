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

package baritone.process;

import baritone.Baritone;
import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalComposite;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.process.IFollowProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.BetterBlockPos;
import baritone.pathing.path.PathExecutor;
import baritone.utils.BaritoneProcessHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Follow an entity
 *
 * @author leijurv
 */
public final class FollowProcess extends BaritoneProcessHelper implements IFollowProcess {

    private Predicate<Entity> filter;
    private List<Entity> cache;
    private boolean into; // walk straight into the target, regardless of settings

    public FollowProcess(Baritone baritone) {
        super(baritone);
    }

    @Override
    public PathingCommand onTick(boolean calcFailed, boolean isSafeToCancel) {
        scanWorld();
        List<BlockPos> places = cache.stream().map(this::placeOf).collect(Collectors.toList());
        Goal goal = new GoalComposite(places.stream().map(this::towards).toArray(Goal[]::new));
        return new PathingCommand(goal, walkOrTurnAround(places));
    }

    /**
     * The command for this tick: let the walk under way finish, or stop it and turn around.
     * <p>
     * Following something that moves used to be revalidated on every tick, and revalidation cancels a
     * path the moment its end is out of the goal - which a moving goal does to every end, sooner or
     * later. So the walk was taken apart and rebuilt on a metronome of the target's own walking speed,
     * and each rebuild stood still while it calculated. A person following somebody keeps walking: the
     * plan in their head bends a little at a time, and only somebody who has actually gone somewhere
     * else makes them stop and work out a new way.
     * <p>
     * So while the end of the walk under way is still a step toward the thing being followed - closer to
     * it than the player is, see {@link FollowProgress} - the goal is merely set afresh and the walk is
     * left alone; the plan-ahead already chains the next stretch to wherever the goal has got to by the
     * time the walk is nearly done. Only when the target has genuinely gone elsewhere does the command
     * become the revalidate that stops, turns around, and paths from where the player is standing.
     */
    private PathingCommandType walkOrTurnAround(List<BlockPos> places) {
        PathExecutor current = baritone.getPathingBehavior().getCurrent();
        if (current != null && !current.failed() && !current.finished()) {
            final BetterBlockPos me = ctx.playerFeet();
            final BlockPos end = current.getPath().getDest();
            for (BlockPos target : places) {
                if (FollowProgress.worthFinishing(me.x, me.y, me.z, end.getX(), end.getY(), end.getZ(), target.getX(), target.getY(), target.getZ())) {
                    return PathingCommandType.SET_GOAL_AND_PATH;
                }
            }
        }
        return PathingCommandType.REVALIDATE_GOAL_AND_PATH;
    }

    private BlockPos placeOf(Entity following) {
        if (Baritone.settings().followOffsetDistance.value == 0 || into) {
            return following.blockPosition();
        }
        GoalXZ g = GoalXZ.fromDirection(following.position(), Baritone.settings().followOffsetDirection.value, Baritone.settings().followOffsetDistance.value);
        return new BetterBlockPos(g.getX(), following.position().y, g.getZ());
    }

    private Goal towards(BlockPos pos) {
        if (into) {
            return new GoalBlock(pos);
        }
        return new GoalNear(pos, Baritone.settings().followRadius.value);
    }


    private boolean followable(Entity entity) {
        if (entity == null) {
            return false;
        }
        if (!entity.isAlive()) {
            return false;
        }
        if (entity.equals(ctx.player())) {
            return false;
        }
        int maxDist = Baritone.settings().followTargetMaxDistance.value;
        if (maxDist != 0 && entity.distanceToSqr(ctx.player()) > maxDist * maxDist) {
            return false;
        }
        return ctx.entitiesStream().anyMatch(entity::equals);
    }

    private void scanWorld() {
        cache = ctx.entitiesStream()
                .filter(this::followable)
                .filter(this.filter)
                .distinct()
                .collect(Collectors.toList());
    }

    @Override
    public boolean isActive() {
        if (filter == null) {
            return false;
        }
        scanWorld();
        return !cache.isEmpty();
    }

    @Override
    public void onLostControl() {
        filter = null;
        cache = null;
    }

    @Override
    public String displayName0() {
        return "Following " + cache;
    }

    @Override
    public void follow(Predicate<Entity> filter) {
        this.filter = filter;
        this.into = false;
    }

    @Override
    public void pickup(Predicate<ItemStack> filter) {
        this.filter = e -> e instanceof ItemEntity && filter.test(((ItemEntity) e).getItem());
        this.into = true;
    }

    @Override
    public List<Entity> following() {
        return cache;
    }

    @Override
    public Predicate<Entity> currentFilter() {
        return filter;
    }
}
