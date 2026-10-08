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

package baritone.pathing.path;

import baritone.Baritone;
import baritone.api.pathing.calc.IPath;
import baritone.api.pathing.movement.ActionCosts;
import baritone.api.pathing.movement.IMovement;
import baritone.api.pathing.movement.MovementStatus;
import baritone.api.pathing.path.IPathExecutor;
import baritone.api.utils.*;
import baritone.api.utils.input.Input;
import baritone.behavior.PathingBehavior;
import baritone.behavior.look.ApproachLook;
import baritone.behavior.look.GazeAhead;
import baritone.pathing.calc.AbstractNodeCostSearch;
import baritone.pathing.movement.CalculationContext;
import baritone.pathing.movement.HeadFirst;
import baritone.pathing.movement.KeyBeat;
import baritone.pathing.movement.Movement;
import baritone.pathing.movement.SprintWindup;
import baritone.pathing.movement.MovementHelper;
import baritone.pathing.movement.movements.*;
import baritone.utils.BlockStateInterface;
import baritone.utils.Cadence;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.phys.Vec3;
import java.util.*;

import static baritone.api.pathing.movement.MovementStatus.*;

/**
 * Behavior to execute a precomputed path
 *
 * @author leijurv
 */
public class PathExecutor implements IPathExecutor, Helper {

    private static final double MAX_MAX_DIST_FROM_PATH = 3;
    private static final double MAX_DIST_FROM_PATH = 2;

    /**
     * Default value is equal to 10 seconds. It's find to decrease it, but it must be at least 5.5s (110 ticks).
     * For more information, see issue #102.
     *
     * @see <a href="https://github.com/cabaletta/baritone/issues/102">Issue #102</a>
     * @see <a href="https://i.imgur.com/5s5GLnI.png">Anime</a>
     */
    private static final double MAX_TICKS_AWAY = 200;

    private final IPath path;
    private int pathPosition;
    private int ticksAway;
    private int ticksOnCurrent;
    private Double currentMovementOriginalCostEstimate;
    private Integer costEstimateIndex;
    private boolean failed;
    private boolean recalcBP = true;
    private HashSet<BlockPos> toBreak = new HashSet<>();
    private HashSet<BlockPos> toPlace = new HashSet<>();
    private HashSet<BlockPos> toWalkInto = new HashSet<>();

    private final PathingBehavior behavior;
    private final IPlayerContext ctx;

    private boolean sprintNextTick;

    /**
     * Ticks the sprint key is still being held for after the last movement that wanted it, so that a run
     * does not blink on and off between one movement and the next. See {@link Cadence#sprintHangover}.
     */
    private int sprintHangover;
    private final Random cadenceRand = new Random();
    private final SprintWindup sprintWindup = new SprintWindup(this.cadenceRand);

    /**
     * Ticks left of the beat after a fall landed before the keys move again. See
     * {@link Cadence#landingRest}.
     */
    private int landingRest;

    /**
     * The rule that the feet wait for the head at a sharp turn. See {@link HeadFirst}.
     */
    private final HeadFirst headFirst = new HeadFirst();

    /**
     * Squared horizontal speed below which the player counts as standing still, for {@link #letTheHeadLead}:
     * the same figure the look behaviour uses to decide whether the hand may hesitate.
     */
    private static final double STANDING_SPEED_SQ = 0.02 * 0.02;

    /**
     * The block this walk is for - the chest, the door, the crop, the block to be mined - or null when the
     * goal is not about one. Resolved once per path, because the answer cannot change while this walk is
     * the walk under way, and because asking every tick would walk a composite of every matching block in
     * the loaded chunks on every tick of every path. See {@link ApproachLook}.
     * <p>
     * From the executing goal rather than the path's own: when {@code simplifyUnloadedYCoord} is on and the
     * goal's chunk is not loaded, the pathfinder is handed - and the path keeps - a {@code GoalXZ} in place
     * of the goal that named a block, and a walk to a chest calculated from far away would arrive having
     * forgotten what it was for.
     */
    private final BlockPos theJob;

    public PathExecutor(PathingBehavior behavior, IPath path) {
        this.behavior = behavior;
        this.ctx = behavior.ctx;
        this.path = path;
        this.pathPosition = 0;
        final List<BetterBlockPos> positions = path.positions();
        this.theJob = ApproachLook.theBlock(behavior.getGoal(), positions.isEmpty() ? null : positions.get(positions.size() - 1));
    }

    /**
     * Tick this executor
     *
     * @return True if a movement just finished (and the player is therefore in a "stable" state, like,
     * not sneaking out over lava), false otherwise
     */
    public boolean onTick() {
        if (pathPosition == path.length() - 1) {
            pathPosition++;
        }
        if (pathPosition >= path.length()) {
            return true; // stop bugging me, I'm done
        }
        if (this.landingRest > 0) {
            if (ctx.player().onGround() && ctx.world().getFluidState(ctx.playerFeet()).isEmpty()) {
                // a fall just landed, and finding your footing takes a beat; see Cadence#landingRest
                this.landingRest--;
                clearKeys();
                return true; // standing on solid ground is as safe to cancel as it gets
            }
            this.landingRest = 0; // into water, or back in the air: the rest was for the landing
        }
        Movement movement = (Movement) path.movements().get(pathPosition);
        BetterBlockPos whereAmI = ctx.playerFeet();
        if (!movement.getValidPositions().contains(whereAmI)) {
            for (int i = 0; i < pathPosition && i < path.length(); i++) {//this happens for example when you lag out and get teleported back a couple blocks
                if (((Movement) path.movements().get(i)).getValidPositions().contains(whereAmI)) {
                    int previousPos = pathPosition;
                    pathPosition = i;
                    for (int j = pathPosition; j <= previousPos; j++) {
                        path.movements().get(j).reset();
                    }
                    onChangeInPathPosition();
                    onTick();
                    return false;
                }
            }
            for (int i = pathPosition + 3; i < path.length() - 1; i++) { //dont check pathPosition+1. the movement tells us when it's done (e.g. sneak placing)
                // also don't check pathPosition+2 because reasons
                if (((Movement) path.movements().get(i)).getValidPositions().contains(whereAmI)) {
                    if (i - pathPosition > 2) {
                        logDebug("Skipping forward " + (i - pathPosition) + " steps, to " + i);
                    }
                    pathPosition = i - 1;
                    onChangeInPathPosition();
                    onTick();
                    return false;
                }
            }
        }
        Pair<Double, BlockPos> status = closestPathPos(path);
        if (possiblyOffPath(status, MAX_DIST_FROM_PATH)) {
            ticksAway++;
            logDebug("Far away from the path for " + ticksAway + " ticks, at distance " + status.first() + " over a threshold of " + MAX_DIST_FROM_PATH);
            if (ticksAway > MAX_TICKS_AWAY) {
                logDebug("Too far away from path for too long, cancelling path");
                cancel();
                return false;
            }
        } else {
            ticksAway = 0;
        }
        if (possiblyOffPath(status, MAX_MAX_DIST_FROM_PATH)) { // ok, stop right away, we're way too far.
            logDebug("too far from path");
            cancel();
            return false;
        }
        //long start = System.nanoTime() / 1000000L;
        BlockStateInterface bsi = new BlockStateInterface(ctx);
        for (int i = pathPosition - 10; i < pathPosition + 10; i++) {
            if (i < 0 || i >= path.movements().size()) {
                continue;
            }
            Movement m = (Movement) path.movements().get(i);
            List<BlockPos> prevBreak = m.toBreak(bsi);
            List<BlockPos> prevPlace = m.toPlace(bsi);
            List<BlockPos> prevWalkInto = m.toWalkInto(bsi);
            m.resetBlockCache();
            if (!prevBreak.equals(m.toBreak(bsi))) {
                recalcBP = true;
            }
            if (!prevPlace.equals(m.toPlace(bsi))) {
                recalcBP = true;
            }
            if (!prevWalkInto.equals(m.toWalkInto(bsi))) {
                recalcBP = true;
            }
        }
        if (recalcBP) {
            HashSet<BlockPos> newBreak = new HashSet<>();
            HashSet<BlockPos> newPlace = new HashSet<>();
            HashSet<BlockPos> newWalkInto = new HashSet<>();
            for (int i = pathPosition; i < path.movements().size(); i++) {
                Movement m = (Movement) path.movements().get(i);
                newBreak.addAll(m.toBreak(bsi));
                newPlace.addAll(m.toPlace(bsi));
                newWalkInto.addAll(m.toWalkInto(bsi));
            }
            toBreak = newBreak;
            toPlace = newPlace;
            toWalkInto = newWalkInto;
            recalcBP = false;
        }
        if (pathPosition < path.movements().size() - 1) {
            IMovement next = path.movements().get(pathPosition + 1);
            if (!behavior.baritone.bsi.worldContainsLoadedChunk(next.getDest().x, next.getDest().z)) {
                logDebug("Pausing since destination is at edge of loaded chunks");
                clearKeys();
                return true;
            }
        }
        boolean canCancel = movement.safeToCancel();
        if (costEstimateIndex == null || costEstimateIndex != pathPosition) {
            costEstimateIndex = pathPosition;
            // do this only once, when the movement starts, and deliberately get the cost as cached when this path was calculated, not the cost as it is right now
            currentMovementOriginalCostEstimate = movement.getCost();
            for (int i = 1; i < Baritone.settings().costVerificationLookahead.value && pathPosition + i < path.length() - 1; i++) {
                if (((Movement) path.movements().get(pathPosition + i)).calculateCost(behavior.secretInternalGetCalculationContext()) >= ActionCosts.COST_INF && canCancel) {
                    logDebug("Something has changed in the world and a future movement has become impossible. Cancelling.");
                    cancel();
                    return true;
                }
            }
        }
        double currentCost = movement.recalculateCost(behavior.secretInternalGetCalculationContext());
        if (currentCost >= ActionCosts.COST_INF && canCancel) {
            logDebug("Something has changed in the world and this movement has become impossible. Cancelling.");
            cancel();
            return true;
        }
        if (!movement.calculatedWhileLoaded() && currentCost - currentMovementOriginalCostEstimate > Baritone.settings().maxCostIncrease.value && canCancel) {
            // don't do this if the movement was calculated while loaded
            // that means that this isn't a cache error, it's just part of the path interfering with a later part
            logDebug("Original cost " + currentMovementOriginalCostEstimate + " current cost " + currentCost + ". Cancelling.");
            cancel();
            return true;
        }
        if (shouldPause()) {
            logDebug("Pausing since current best path is a backtrack");
            clearKeys();
            return true;
        }
        leanGazeAhead(movement);
        leanOntoTheSlope(movement);
        welcomeGlance(movement);
        MovementStatus movementStatus = movement.update();
        if (movementStatus == UNREACHABLE || movementStatus == FAILED) {
            logDebug("Movement returns status " + movementStatus);
            cancel();
            return true;
        }
        if (movementStatus == SUCCESS) {
            if (movement.getDirection().getY() < 0) {
                // the beat a person takes after a fall lands, before the feet move again
                this.landingRest = Cadence.landingRest(this.cadenceRand, -movement.getDirection().getY());
            }
            pathPosition++;
            onChangeInPathPosition();
            onTick();
            return true;
        } else {
            letTheHeadLead();
            standForTheLook();
            sprintNextTick = shouldSprintNextTick();
            if (!sprintNextTick) {
                ctx.player().setSprinting(false); // letting go of control doesn't make you stop sprinting actually
            }
            ticksOnCurrent++;
            if (ticksOnCurrent > currentMovementOriginalCostEstimate + Baritone.settings().movementTimeoutTicks.value) {
                // only cancel if the total time has exceeded the initial estimate
                // as you break the blocks required, the remaining cost goes down, to the point where
                // ticksOnCurrent is greater than recalculateCost + 100
                // this is why we cache cost at the beginning, and don't recalculate for this comparison every tick
                logDebug("This movement has taken too long (" + ticksOnCurrent + " ticks, expected " + currentMovementOriginalCostEstimate + "). Cancelling.");
                cancel();
                return true;
            }
        }
        return canCancel; // movement is in progress, but if it reports cancellable, PathingBehavior is good to cut onto the next path
    }

    /**
     * How close the walk has to be to its own end before the gaze may lean onto what comes next, in
     * blocks: the last step or two of a movement, which is where a person's head is already around the
     * corner. See {@link baritone.behavior.look.GazeAhead}.
     */
    private static final double GAZE_LEAD_DISTANCE = 2.0;

    /**
     * How far ahead along the path the slope the gaze leans along is measured, in blocks of run, and the
     * share of its angle the gaze takes: more than half of a way down, a third of a way up. See
     * {@link #leanOntoTheSlope}.
     */
    private static final double SLOPE_LOOKAHEAD = 5.0;
    private static final double SLOPE_DOWN_SHARE = 0.6;
    private static final double SLOPE_UP_SHARE = 0.33;

    /**
     * Offers the gaze the block the walk is for, when this is a tick the eyes can be on it: the chest, the
     * door, the crop or the table comes onto the head on the approach rather than after the feet have
     * stopped. See {@link ApproachLook} for the rule and {@code HUMANLIKE.md} for the behaviour.
     * <p>
     * Asked before {@link #leanGazeAhead} and takes its place when it has an answer: there is one lean on
     * a walking target, and on the stretch of walk that ends at a block the thing the walk is for is the
     * more useful thing to have the eyes on than the corner of it. When it has no answer - the goal is not
     * about a block, the block is still too far, or it sits beside the walk rather than on it - the line
     * ahead is offered as before.
     * <p>
     * The same states that take the line ahead away take this away, and for the same reasons: a fall, a
     * climb and a jump steer on the angles they are given, water and a glide solve for their own, and a
     * target that is about to be clicked is aimed at exactly, which
     * {@link baritone.behavior.look.GazeAhead#lean} does regardless of what is offered here.
     *
     * @return whether the eyes have somewhere better to be than the line ahead, so that the caller can
     *         leave the line alone
     */
    private boolean lookAtTheJob(Movement movement) {
        if (this.theJob == null
                || !ctx.player().onGround()
                || ctx.player().isInWater()
                || movement instanceof MovementParkour
                || movement.getDirection().getY() != 0) {
            return false;
        }
        final BlockPos step = movement.getDirection();
        if (step.getX() == 0 && step.getZ() == 0) {
            return false;
        }
        if (this.theJob.equals(ctx.playerFeet())) {
            // the block the walk is to be standing in, which the player is already standing in: the walk is
            // there, and there is no direction to it to look along
            return false;
        }
        final Vec3 centre = VecUtils.getBlockPosCenter(this.theJob);
        final Vec3 feet = ctx.player().position();
        final double distance = centre.distanceTo(feet);
        final float offTheWalk = ApproachLook.angleOffTheWalk(
                step.getX(), step.getZ(),
                centre.x - feet.x, centre.z - feet.z);
        final double weight = ApproachLook.weight(distance, offTheWalk);
        if (weight <= 0) {
            return false;
        }
        behavior.baritone.getLookBehavior().gazeAhead().offer(
                RotationUtils.calcRotationFromVec3d(ctx.playerHead(), centre, ctx.playerRotations()),
                weight);
        return true;
    }

    /**
     * Offers the gaze the line the path takes next, so the head leads the body around corners instead
     * of whipping around at every movement boundary.
     * <p>
     * Only for a walk on the ground, and only near the end of one - a fall, a climb and a jump steer on
     * the angles they are given, and they get those exactly. Anything the movement is about to click is
     * aimed at exactly too: the lean is on walking targets alone, by the rule in
     * {@link baritone.behavior.look.GazeAhead#lean}.
     */
    private void leanGazeAhead(Movement movement) {
        final GazeAhead gaze = behavior.baritone.getLookBehavior().gazeAhead();
        if (lookAtTheJob(movement)) {
            return;
        }
        if (movement.getDirection().getY() != 0
                || movement instanceof MovementParkour
                || pathPosition + 1 >= path.length()
                || !ctx.player().onGround()) {
            // A jump along the flat is a jump all the same: the run-up to a gap is the one walk where a
            // person has their eyes on the landing and nowhere else, so parkour gets no lean either.
            gaze.offer(null, 0);
            return;
        }
        final BetterBlockPos dest = movement.getDest();
        final double dx = dest.x + 0.5 - ctx.player().position().x;
        final double dz = dest.z + 0.5 - ctx.player().position().z;
        final double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist >= GAZE_LEAD_DISTANCE) {
            gaze.offer(null, 0);
            return;
        }
        final IMovement next = path.movements().get(pathPosition + 1);
        final Rotation toNext = RotationUtils.calcRotationFromVec3d(ctx.playerHead(), VecUtils.getBlockPosCenter(next.getDest()), ctx.playerRotations());
        gaze.offer(toNext, GazeAhead.LEAN * (1 - dist / GAZE_LEAD_DISTANCE));
    }

    /**
     * Tells the gaze where the ground is going, so that it can settle along it: see
     * {@link baritone.behavior.look.PitchWander#lean}. The slope is the rise over the run of the next few
     * movements - the climb of a stair, the step down off a ledge, the drop at the end of a corridor - as
     * the angle a person would look along it, and then only part of that, because the eyes check the
     * footing on a way down and glance up a way up without ever fixing on either. A drop is looked down
     * more than a climb is looked up: on a way down the footing is the thing that can go wrong. Nothing on
     * a flat, in water or in the air, and not on the run-up to a jump, whose eyes are on the landing.
     */
    private void leanOntoTheSlope(Movement movement) {
        if (!ctx.player().onGround() || ctx.player().isInWater() || movement instanceof MovementParkour) {
            return;
        }
        double rise = 0;
        double run = 0;
        for (int i = pathPosition; i < path.length() && run < SLOPE_LOOKAHEAD; i++) {
            final BlockPos direction = path.movements().get(i).getDirection();
            rise += direction.getY();
            run += Math.sqrt(direction.getX() * direction.getX() + direction.getZ() * direction.getZ());
        }
        if (rise == 0) {
            return;
        }
        // positive pitch looks down, and a run of less than a block is a drop or a climb looked straight along
        final double angle = Math.toDegrees(Math.atan2(-rise, Math.max(1.0, run)));
        behavior.baritone.getLookBehavior().leanOntoTheSlope((float) (angle * (rise < 0 ? SLOPE_DOWN_SHARE : SLOPE_UP_SHARE)));
    }

    /**
     * Tells the walking glance whether this is a tick it is welcome on. See
     * {@link baritone.behavior.look.WalkGlance}: a plain walk along the flat, with two more of them to
     * come, so that the head has a couple of blocks to come back in before anything needs it - a glance
     * takes under half a second to come back from, and a block at a run is a fifth of one. Water and the
     * air are not walks, and a walk is plain when nothing on it is broken, placed or opened.
     */
    private void welcomeGlance(Movement movement) {
        if (!ctx.player().onGround() || ctx.player().isInWater() || pathPosition + 2 >= path.length()) {
            return;
        }
        if (plainWalk(movement) && plainWalk(path.movements().get(pathPosition + 1)) && plainWalk(path.movements().get(pathPosition + 2))) {
            behavior.baritone.getLookBehavior().walkGlance().welcome();
        }
    }

    private boolean plainWalk(IMovement movement) {
        if (!(movement instanceof MovementTraverse) && !(movement instanceof MovementDiagonal)) {
            return false;
        }
        if (movement.getDirection().getY() != 0) {
            return false;
        }
        final Movement m = (Movement) movement;
        // the caches are filled a few lines up in onTick for every movement within ten of this one
        if (m.toBreakCached == null || m.toPlaceCached == null || !m.toBreakCached.isEmpty() || !m.toPlaceCached.isEmpty()) {
            return false;
        }
        // a door or a gate is opened rather than broken, so it is in neither list, and it is the one thing
        // a traverse with nothing to break still clicks
        final BetterBlockPos dest = movement.getDest();
        for (BlockPos pos : new BlockPos[]{dest, dest.above(), movement.getSrc(), movement.getSrc().above()}) {
            final Block block = ctx.world().getBlockState(pos).getBlock();
            if (block instanceof DoorBlock || block instanceof FenceGateBlock) {
                return false;
            }
        }
        return true;
    }

    private Pair<Double, BlockPos> closestPathPos(IPath path) {
        double best = -1;
        BlockPos bestPos = null;
        for (IMovement movement : path.movements()) {
            for (BlockPos pos : ((Movement) movement).getValidPositions()) {
                double dist = VecUtils.entityDistanceToCenter(ctx.player(), pos);
                if (dist < best || best == -1) {
                    best = dist;
                    bestPos = pos;
                }
            }
        }
        return new Pair<>(best, bestPos);
    }

    private boolean shouldPause() {
        Optional<AbstractNodeCostSearch> current = behavior.getInProgress();
        if (!current.isPresent()) {
            return false;
        }
        if (!ctx.player().onGround()) {
            return false;
        }
        if (!MovementHelper.canWalkOn(ctx, ctx.playerFeet().below())) {
            // we're in some kind of sketchy situation, maybe parkouring
            return false;
        }
        if (!MovementHelper.canWalkThrough(ctx, ctx.playerFeet()) || !MovementHelper.canWalkThrough(ctx, ctx.playerFeet().above())) {
            // suffocating?
            return false;
        }
        if (!path.movements().get(pathPosition).safeToCancel()) {
            return false;
        }
        Optional<IPath> currentBest = current.get().bestPathSoFar();
        if (!currentBest.isPresent()) {
            return false;
        }
        List<BetterBlockPos> positions = currentBest.get().positions();
        if (positions.size() < 3) {
            return false; // not long enough yet to justify pausing, its far from certain we'll actually take this route
        }
        // the first block of the next path will always overlap
        // no need to pause our very last movement when it would have otherwise cleanly exited with MovementStatus SUCCESS
        positions = positions.subList(1, positions.size());
        return positions.contains(ctx.playerFeet());
    }

    private boolean possiblyOffPath(Pair<Double, BlockPos> status, double leniency) {
        double distanceFromPath = status.first();
        if (distanceFromPath > leniency) {
            // when we're midair in the middle of a fall, we're very far from both the beginning and the end, but we aren't actually off path
            if (path.movements().get(pathPosition) instanceof MovementFall) {
                BlockPos fallDest = path.positions().get(pathPosition + 1); // .get(pathPosition) is the block we fell off of
                return VecUtils.entityFlatDistanceToCenter(ctx.player(), fallDest) >= leniency; // ignore Y by using flat distance
            } else {
                return true;
            }
        } else {
            return false;
        }
    }

    /**
     * Regardless of current path position, snap to the current player feet if possible
     *
     * @return Whether or not it was possible to snap to the current player feet
     */
    public boolean snipsnapifpossible() {
        if (!ctx.player().onGround() && ctx.world().getFluidState(ctx.playerFeet()).isEmpty()) {
            // if we're falling in the air, and not in water, don't splice
            return false;
        } else {
            // we are either onGround or in liquid
            if (ctx.player().getDeltaMovement().y < -0.1) {
                // if we are strictly moving downwards (not stationary)
                // we could be falling through water, which could be unsafe to splice
                return false; // so don't
            }
        }
        int index = path.positions().indexOf(ctx.playerFeet());
        if (index == -1) {
            return false;
        }
        pathPosition = index; // jump directly to current position
        clearKeys();
        return true;
    }

    private boolean shouldSprintNextTick() {
        if (sprintWanted()) {
            // Something wants us running: the key is held, and the hand comes off within this many ticks
            // of nothing wanting it any more.
            this.sprintHangover = Cadence.sprintHangover(this.cadenceRand);
            return this.sprintWindup.tick(true, canWindUpSprint());
        }
        if (this.sprintHangover > 0 && canTrailOffSprint()) {
            // Nothing wants it any more, but the key does not come off on this tick. Baritone only forces
            // the sprint key down - vanilla still decides whether the player is sprinting - so this holds
            // the key, it does not make the player do anything they could not have done anyway.
            this.sprintHangover--;
            this.sprintWindup.keepHeld();
            return true;
        }
        this.sprintHangover = 0;
        this.sprintWindup.tick(false, false);
        return false;
    }

    /**
     * Whether a new sprint may start with a beat of walking first.
     * <p>
     * Only a flat run on the ground. Climbs, drops, parkour, water and air all need the speed they asked
     * for on the tick they asked: delaying control there is a miss, not a personality trait.
     */
    private boolean canWindUpSprint() {
        if (pathPosition >= path.length()) {
            return false;
        }
        final IMovement movement = path.movements().get(pathPosition);
        if (movement instanceof MovementParkour
                || movement instanceof MovementAscend
                || movement instanceof MovementDescend
                || movement instanceof MovementFall
                || movement instanceof MovementPillar
                || movement.getDirection().getY() != 0) {
            return false;
        }
        if (!ctx.player().onGround() || ctx.player().isInWater() || ctx.player().isInLava()) {
            return false;
        }
        return true;
    }

    /**
     * Whether a sprint that nothing is asking for any more may be held for another tick.
     * <p>
     * Only while running along the ground. Everywhere else the speed at the instant of the transition is
     * load bearing - the jump that clears a gap, the step off a ledge, the fall after it - and those are
     * planned assuming the player is slowing down, so the key has to come off the tick the movement stops
     * asking for it.
     */
    private boolean canTrailOffSprint() {
        if (pathPosition >= path.length() || path.movements().get(pathPosition).getDirection().getY() != 0) {
            return false;
        }
        return ctx.player().onGround();
    }

    /**
     * Whether the current movement wants the sprint key held, ignoring any hangover from the last one.
     */
    private boolean sprintWanted() {
        boolean requested = behavior.baritone.getInputOverrideHandler().isInputForcedDown(Input.SPRINT);

        // we'll take it from here, no need for minecraft to see we're holding down control and sprint for us
        behavior.baritone.getInputOverrideHandler().setInputForceState(Input.SPRINT, false);

        // first and foremost, if allowSprint is off, or if we don't have enough hunger, don't try and sprint
        if (!new CalculationContext(behavior.baritone, false).canSprint) {
            return false;
        }
        IMovement current = path.movements().get(pathPosition);

        // traverse requests sprinting, so we need to do this check first
        if (current instanceof MovementTraverse && pathPosition < path.length() - 3) {
            IMovement next = path.movements().get(pathPosition + 1);
            if (next instanceof MovementAscend && sprintableAscend(ctx, (MovementTraverse) current, (MovementAscend) next, path.movements().get(pathPosition + 2))) {
                if (skipNow(ctx, current)) {
                    logDebug("Skipping traverse to straight ascend");
                    pathPosition++;
                    onChangeInPathPosition();
                    onTick();
                    behavior.baritone.getInputOverrideHandler().setInputForceState(Input.JUMP, true);
                    return true;
                } else {
                    logDebug("Too far to the side to safely sprint ascend");
                }
            }
        }

        // if the movement requested sprinting, then we're done
        if (requested) {
            return true;
        }

        // however, descend and ascend don't request sprinting, because they don't know the context of what movement comes after it
        if (current instanceof MovementDescend) {

            if (pathPosition < path.length() - 2) {
                // keep this out of onTick, even if that means a tick of delay before it has an effect
                IMovement next = path.movements().get(pathPosition + 1);
                if (MovementHelper.canUseFrostWalker(ctx, next.getDest().below())) {
                    // frostwalker only works if you cross the edge of the block on ground so in some cases we may not overshoot
                    // Since MovementDescend can't know the next movement we have to tell it
                    if (next instanceof MovementTraverse || next instanceof MovementParkour) {
                        boolean couldPlaceInstead = Baritone.settings().allowPlace.value && behavior.baritone.getInventoryBehavior().hasGenericThrowaway() && next instanceof MovementParkour; // traverse doesn't react fast enough
                        // this is true if the next movement does not ascend or descends and goes into the same cardinal direction (N-NE-E-SE-S-SW-W-NW) as the descend
                        // in that case current.getDirection() is e.g. (0, -1, 1) and next.getDirection() is e.g. (0, 0, 3) so the cross product of (0, 0, 1) and (0, 0, 3) is taken, which is (0, 0, 0) because the vectors are colinear (don't form a plane)
                        // since movements in exactly the opposite direction (e.g. descend (0, -1, 1) and traverse (0, 0, -1)) would also pass this check we also have to rule out that case
                        // we can do that by adding the directions because traverse is always 1 long like descend and parkour can't jump through current.getSrc().down()
                        boolean sameFlatDirection = !current.getDirection().above().offset(next.getDirection()).equals(BlockPos.ZERO)
                                && current.getDirection().above().cross(next.getDirection()).equals(BlockPos.ZERO); // here's why you learn maths in school
                        if (sameFlatDirection && !couldPlaceInstead) {
                            ((MovementDescend) current).forceSafeMode();
                        }
                    }
                }
            }
            if (((MovementDescend) current).safeMode() && !((MovementDescend) current).skipToAscend()) {
                logDebug("Sprinting would be unsafe");
                return false;
            }

            if (pathPosition < path.length() - 2) {
                IMovement next = path.movements().get(pathPosition + 1);
                if (next instanceof MovementAscend && current.getDirection().above().equals(next.getDirection().below())) {
                    // a descend then an ascend in the same direction
                    pathPosition++;
                    onChangeInPathPosition();
                    onTick();
                    // okay to skip clearKeys and / or onChangeInPathPosition here since this isn't possible to repeat, since it's asymmetric
                    logDebug("Skipping descend to straight ascend");
                    return true;
                }
                if (canSprintFromDescendInto(ctx, current, next)) {

                    if (next instanceof MovementDescend && pathPosition < path.length() - 3) {
                        IMovement next_next = path.movements().get(pathPosition + 2);
                        if (next_next instanceof MovementDescend && !canSprintFromDescendInto(ctx, next, next_next)) {
                            return false;
                        }

                    }
                    if (ctx.playerFeet().equals(current.getDest())) {
                        pathPosition++;
                        onChangeInPathPosition();
                        onTick();
                    }

                    return true;
                }
                //logDebug("Turning off sprinting " + movement + " " + next + " " + movement.getDirection() + " " + next.getDirection().down() + " " + next.getDirection().down().equals(movement.getDirection()));
            }
        }
        if (current instanceof MovementAscend && pathPosition != 0) {
            IMovement prev = path.movements().get(pathPosition - 1);
            if (prev instanceof MovementDescend && prev.getDirection().above().equals(current.getDirection().below())) {
                BlockPos center = current.getSrc().above();
                // playerFeet adds 0.1251 to account for soul sand
                // farmland is 0.9375
                // 0.07 is to account for farmland
                if (ctx.player().position().y >= center.getY() - 0.07) {
                    behavior.baritone.getInputOverrideHandler().setInputForceState(Input.JUMP, false);
                    return true;
                }
            }
            if (pathPosition < path.length() - 2 && prev instanceof MovementTraverse && sprintableAscend(ctx, (MovementTraverse) prev, (MovementAscend) current, path.movements().get(pathPosition + 1))) {
                return true;
            }
        }
        if (current instanceof MovementFall) {
            Pair<Vec3, BlockPos> data = overrideFall((MovementFall) current);
            if (data != null) {
                BetterBlockPos fallDest = new BetterBlockPos(data.second());
                if (!path.positions().contains(fallDest)) {
                    throw new IllegalStateException(String.format(
                            "Fall override at %s %s %s returned illegal destination %s %s %s",
                            current.getSrc(), fallDest));
                }
                if (ctx.playerFeet().equals(fallDest)) {
                    pathPosition = path.positions().indexOf(fallDest);
                    onChangeInPathPosition();
                    onTick();
                    return true;
                }
                clearKeys();
                behavior.baritone.getLookBehavior().updateTarget(RotationUtils.calcRotationFromVec3d(ctx.playerHead(), data.first(), ctx.playerRotations()), false);
                behavior.baritone.getInputOverrideHandler().setInputForceState(Input.MOVE_FORWARD, true);
                return true;
            }
        }
        return false;
    }

    private Pair<Vec3, BlockPos> overrideFall(MovementFall movement) {
        Vec3i dir = movement.getDirection();
        if (dir.getY() < -3) {
            return null;
        }
        if (!movement.toBreakCached.isEmpty()) {
            return null; // it's breaking
        }
        Vec3i flatDir = new Vec3i(dir.getX(), 0, dir.getZ());
        int i;
        outer:
        for (i = pathPosition + 1; i < path.length() - 1 && i < pathPosition + 3; i++) {
            IMovement next = path.movements().get(i);
            if (!(next instanceof MovementTraverse)) {
                break;
            }
            if (!flatDir.equals(next.getDirection())) {
                break;
            }
            for (int y = next.getDest().y; y <= movement.getSrc().y + 1; y++) {
                BlockPos chk = new BlockPos(next.getDest().x, y, next.getDest().z);
                if (!MovementHelper.fullyPassable(ctx, chk)) {
                    break outer;
                }
            }
            if (!MovementHelper.canWalkOn(ctx, next.getDest().below())) {
                break;
            }
        }
        i--;
        if (i == pathPosition) {
            return null; // no valid extension exists
        }
        double len = i - pathPosition - 0.4;
        return new Pair<>(
                new Vec3(flatDir.getX() * len + movement.getDest().x + 0.5, movement.getDest().y, flatDir.getZ() * len + movement.getDest().z + 0.5),
                movement.getDest().offset(flatDir.getX() * (i - pathPosition), 0, flatDir.getZ() * (i - pathPosition)));
    }

    private static boolean skipNow(IPlayerContext ctx, IMovement current) {
        double offTarget = Math.abs(current.getDirection().getX() * (current.getSrc().z + 0.5D - ctx.player().position().z)) + Math.abs(current.getDirection().getZ() * (current.getSrc().x + 0.5D - ctx.player().position().x));
        if (offTarget > 0.1) {
            return false;
        }
        // we are centered
        BlockPos headBonk = current.getSrc().subtract(current.getDirection()).above(2);
        if (MovementHelper.fullyPassable(ctx, headBonk)) {
            return true;
        }
        // wait 0.3
        double flatDist = Math.abs(current.getDirection().getX() * (headBonk.getX() + 0.5D - ctx.player().position().x)) + Math.abs(current.getDirection().getZ() * (headBonk.getZ() + 0.5 - ctx.player().position().z));
        return flatDist > 0.8;
    }

    private static boolean sprintableAscend(IPlayerContext ctx, MovementTraverse current, MovementAscend next, IMovement nextnext) {
        if (!Baritone.settings().sprintAscends.value) {
            return false;
        }
        if (!current.getDirection().equals(next.getDirection().below())) {
            return false;
        }
        if (nextnext.getDirection().getX() != next.getDirection().getX() || nextnext.getDirection().getZ() != next.getDirection().getZ()) {
            return false;
        }
        if (!MovementHelper.canWalkOn(ctx, current.getDest().below())) {
            return false;
        }
        if (!MovementHelper.canWalkOn(ctx, next.getDest().below())) {
            return false;
        }
        if (!next.toBreakCached.isEmpty()) {
            return false; // it's breaking
        }
        for (int x = 0; x < 2; x++) {
            for (int y = 0; y < 3; y++) {
                BlockPos chk = current.getSrc().above(y);
                if (x == 1) {
                    chk = chk.offset(current.getDirection());
                }
                if (!MovementHelper.fullyPassable(ctx, chk)) {
                    return false;
                }
            }
        }
        if (MovementHelper.avoidWalkingInto(ctx.world().getBlockState(current.getSrc().above(3)))) {
            return false;
        }
        return !MovementHelper.avoidWalkingInto(ctx.world().getBlockState(next.getDest().above(2))); // codacy smh my head
    }

    private static boolean canSprintFromDescendInto(IPlayerContext ctx, IMovement current, IMovement next) {
        if (next instanceof MovementDescend && next.getDirection().equals(current.getDirection())) {
            return true;
        }
        if (!MovementHelper.canWalkOn(ctx, current.getDest().offset(current.getDirection()))) {
            return false;
        }
        if (next instanceof MovementTraverse && next.getDirection().equals(current.getDirection())) {
            return true;
        }
        return next instanceof MovementDiagonal && Baritone.settings().allowOvershootDiagonalDescend.value;
    }

    private void onChangeInPathPosition() {
        clearKeys();
        ticksOnCurrent = 0;
    }

    private void clearKeys() {
        // i'm just sick and tired of this snippet being everywhere lol
        behavior.baritone.getInputOverrideHandler().clearAllKeys();
    }

    /**
     * Keeps the feet still while the head is still coming round to where they are being sent. See
     * {@link HeadFirst} for the rule; this is the part of it that has to know about the game.
     * <p>
     * Run after the movement has set its keys and its target for the tick, so the head is being turned
     * toward the new direction all the while: the hold is on the walking keys and the jump alone. The
     * sneak that holds a bridge and both mouse buttons go around it, the way they go around every other
     * beat in here. Nothing is held in the air or in water, and a walk under way is only stopped for a
     * turn-around - the machine decides both, from what it is told here.
     */
    private void letTheHeadLead() {
        final IInputOverrideHandler keys = behavior.baritone.getInputOverrideHandler();
        final KeyBeat.Keys walking = new KeyBeat.Keys(
                keys.isInputForcedDown(Input.MOVE_FORWARD), keys.isInputForcedDown(Input.MOVE_BACK),
                keys.isInputForcedDown(Input.MOVE_LEFT), keys.isInputForcedDown(Input.MOVE_RIGHT));
        final boolean grounded = ctx.player().onGround() && !ctx.player().isInWater() && !ctx.player().isInLava();
        final Vec3 velocity = ctx.player().getDeltaMovement();
        final boolean standing = velocity.x * velocity.x + velocity.z * velocity.z < STANDING_SPEED_SQ;
        // nobody asking the feet for anything is a head that is not behind them; otherwise the gap is to
        // where the keys actually take the body, which is off the steering yaw by the keys' own angle
        final float headBehind = walking.isEmpty() ? 0f : behavior.baritone.getLookBehavior().headBehind(walking.yawOffset());
        if (!headFirst.hold(headBehind, standing, grounded)) {
            return;
        }
        for (Input input : new Input[]{Input.MOVE_FORWARD, Input.MOVE_BACK, Input.MOVE_LEFT, Input.MOVE_RIGHT, Input.JUMP}) {
            keys.setInputForceState(input, false);
        }
    }

    /**
     * Takes the feet off the keys while the walking glance says to - see
     * {@link baritone.behavior.look.WalkGlance#stopped()}. Only ever true on a walk this executor has
     * welcomed the glance on, which is a plain one along the flat with more of the same ahead, so standing
     * on it for a moment is free; the movement has already set its target for the tick, so the head stays
     * on the walk and the walk picks up where it left off.
     */
    private void standForTheLook() {
        if (!behavior.baritone.getLookBehavior().walkGlance().stopped()) {
            return;
        }
        final IInputOverrideHandler keys = behavior.baritone.getInputOverrideHandler();
        for (Input input : new Input[]{Input.MOVE_FORWARD, Input.MOVE_BACK, Input.MOVE_LEFT, Input.MOVE_RIGHT, Input.SPRINT}) {
            keys.setInputForceState(input, false);
        }
    }

    private void cancel() {
        clearKeys();
        behavior.baritone.getInputOverrideHandler().getBlockBreakHelper().stopBreakingBlock();
        pathPosition = path.length() + 3;
        failed = true;
    }

    @Override
    public int getPosition() {
        return pathPosition;
    }

    public PathExecutor trySplice(PathExecutor next) {
        if (next == null) {
            return cutIfTooLong();
        }
        return SplicedPath.trySplice(path, next.path, false).map(path -> {
            if (!path.getDest().equals(next.getPath().getDest())) {
                throw new IllegalStateException(String.format(
                        "Path has end %s instead of %s after splicing",
                        path.getDest(), next.getPath().getDest()));
            }
            PathExecutor ret = new PathExecutor(behavior, path);
            ret.pathPosition = pathPosition;
            ret.currentMovementOriginalCostEstimate = currentMovementOriginalCostEstimate;
            ret.costEstimateIndex = costEstimateIndex;
            ret.ticksOnCurrent = ticksOnCurrent;
            ret.sprintHangover = sprintHangover; // continuing the same run, so not a boundary the player can feel
            ret.landingRest = landingRest; // and the landing beat goes on across the splice too
            ret.sprintWindup.copyFrom(this.sprintWindup);
            return ret;
        }).orElseGet(this::cutIfTooLong); // dont actually call cutIfTooLong every tick if we won't actually use it, use a method reference
    }

    private PathExecutor cutIfTooLong() {
        if (pathPosition > Baritone.settings().maxPathHistoryLength.value) {
            int cutoffAmt = Baritone.settings().pathHistoryCutoffAmount.value;
            CutoffPath newPath = new CutoffPath(path, cutoffAmt, path.length() - 1);
            if (!newPath.getDest().equals(path.getDest())) {
                throw new IllegalStateException(String.format(
                        "Path has end %s instead of %s after trimming its start",
                        newPath.getDest(), path.getDest()));
            }
            logDebug("Discarding earliest segment movements, length cut from " + path.length() + " to " + newPath.length());
            PathExecutor ret = new PathExecutor(behavior, newPath);
            ret.pathPosition = pathPosition - cutoffAmt;
            ret.currentMovementOriginalCostEstimate = currentMovementOriginalCostEstimate;
            if (costEstimateIndex != null) {
                ret.costEstimateIndex = costEstimateIndex - cutoffAmt;
            }
            ret.ticksOnCurrent = ticksOnCurrent;
            ret.sprintHangover = sprintHangover; // continuing the same run, so not a boundary the player can feel
            ret.landingRest = landingRest; // and the landing beat goes on across the trim too
            ret.sprintWindup.copyFrom(this.sprintWindup);
            return ret;
        }
        return this;
    }

    @Override
    public IPath getPath() {
        return path;
    }

    public boolean failed() {
        return failed;
    }

    public boolean finished() {
        return pathPosition >= path.length();
    }

    public Set<BlockPos> toBreak() {
        return Collections.unmodifiableSet(toBreak);
    }

    public Set<BlockPos> toPlace() {
        return Collections.unmodifiableSet(toPlace);
    }

    public Set<BlockPos> toWalkInto() {
        return Collections.unmodifiableSet(toWalkInto);
    }

    public boolean isSprinting() {
        return sprintNextTick;
    }
}
