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

package baritone.behavior;

import baritone.Baritone;
import baritone.api.Settings;
import baritone.api.behavior.ILookBehavior;
import baritone.api.behavior.look.IAimProcessor;
import baritone.api.behavior.look.ITickableAimProcessor;
import baritone.api.event.events.*;
import baritone.api.event.events.type.EventState;
import baritone.api.utils.IPlayerContext;
import baritone.api.utils.RayTraceUtils;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.input.Input;
import baritone.behavior.look.AimDynamics;
import baritone.behavior.look.Flinch;
import baritone.behavior.look.ForkableRandom;
import baritone.behavior.look.GazeAhead;
import baritone.behavior.look.HeadWander;
import baritone.behavior.look.IdleFidget;
import baritone.behavior.look.IdleGlance;
import baritone.behavior.look.Newcomer;
import baritone.behavior.look.PitchWander;
import baritone.behavior.look.ReactionDelay;
import baritone.behavior.look.TurnPace;
import baritone.behavior.look.Unattended;
import baritone.behavior.look.WalkGlance;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Supplier;

public final class LookBehavior extends Behavior implements ILookBehavior {

    /**
     * The current look target, may be {@code null}.
     */
    private Target target;

    /**
     * The rotation known to the server. Returned by {@link #getEffectiveRotation()} for use in {@link IPlayerContext}.
     */
    private Rotation serverRotation;

    /**
     * The last player rotation. Used to restore the player's angle when using free look.
     *
     * @see Settings#freeLook
     */
    private Rotation prevRotation;

    private final AimProcessor processor;

    private final Deque<Float> smoothYawBuffer;
    private final Deque<Float> smoothPitchBuffer;

    /**
     * The rotation Baritone is currently driving the player with.
     * <p>
     * This is not the same thing as the target it is aiming for: a hand takes time to get anywhere, so
     * this is where the hand has got to so far. {@code null} when nothing is currently being aimed at.
     */
    private Rotation aimRotation;

    /**
     * Ticks since the current turn started, indexing {@link AimDynamics#TURN_RAMP}. Reset whenever a new
     * target appears so that every turn gets its own spin-up.
     */
    private int aimAge;

    /**
     * Where the hand was when the current turn started. A hand carries a little way past what it is walking
     * toward, and how far is a fraction of how far it had to come - so the turn's own size has to be
     * remembered, and this is it. Reset with {@link #aimAge}; {@code null} when nothing is being aimed at.
     */
    private Rotation turnFrom;

    // The aim's speed, easing, spin-up and tremor numbers all live in AimDynamics, together with the
    // maths that uses them, so they can be reasoned about and tested without a game running.


    /**
     * Squared horizontal speed below which the player counts as standing still. Baritone standing at a
     * gate waiting for it to open, or holding position at the end of a path, is exactly the moment a
     * person would start looking around.
     */
    private static final double IDLE_SPEED_SQ = 0.02 * 0.02;

    /**
     * How close somebody else has to be for an idle glance to be allowed to land on them, in blocks. A
     * person notices other people in the room they are standing in, not on the horizon; a head that turns
     * to look at whoever is twelve blocks away is still someone noticing, and one that tracks the far side
     * of the render distance is a camera.
     */
    private static final double INTEREST_RANGE = 12.0;

    /**
     * How far round from where the head is pointing somebody can be and still be seen arriving, in
     * degrees of yaw: a little past the shoulder, which is about what a screen's field of view and the
     * corner of an eye come to between them. Somebody arriving behind the head is not seen at all - a
     * head that turns to them has seen through the back of its own skull - and the idle glances will come
     * across them in their own time if the head happens to turn that way.
     */
    private static final float ARRIVAL_FIELD = 100.0f;

    /**
     * How far away whoever just hit us can be and still be the one looked round at, in blocks: an arm's
     * reach and a bit for a hand or a sword, a bow's worth for an arrow, and no further - at a greater
     * distance a person hit does not know who did it, they know they were hit.
     */
    private static final double STRUCK_RANGE = 10.0;

    /**
     * How far the view may sit from where the idle look last left it, in degrees, before it counts as
     * taken back by the person. The idle look knows to the float what it wrote last tick and nothing
     * else writes the player's rotation on a tick nothing is aimed at, so anything past a hundredth of
     * a degree is a hand: a single pixel of mouse movement at the lowest sensitivity there is turns
     * the view about twice this much, and a camera nobody is holding does not drift at all.
     */
    private static final float VIEW_TAKEN_BACK = 0.01f;

    private final IdleGlance idleGlance = new IdleGlance();

    /**
     * Who has just walked up, for the glances to turn to. See {@link #noticeArrivals}.
     */
    private final Newcomer newcomer = new Newcomer();

    /**
     * The turn to whoever just hit us. Not a glance, because a glance has limits a blow does not - it
     * refuses an interest further off than its own, and it only advances on a walk the executor has
     * welcomed - and not an arrival, because a blow says where it came from whether or not it was in view.
     * It rides the same head the sway rides, so it is taken exactly where the sway is taken and stands down
     * exactly where the sway does, and it is applied with the sway's offsets and handed back with them. See
     * {@link Flinch}.
     */
    private final Flinch flinch = new Flinch();
    private int lastHurtTime; // the player's hurtTime last tick, to tell a fresh blow from the tail of one

    /**
     * The small amount the head drifts off the bearing while walking. See {@link #updateHeadWander}.
     */
    private final HeadWander headWander = new HeadWander();

    /**
     * The beat before the hand starts moving toward a newly chosen target. Only ever allowed to hold while
     * the player is standing still; see {@link #standingStill()}.
     */
    private final ReactionDelay reaction = new ReactionDelay();

    /**
     * How quickly the turn under way is being made. Drawn by this behaviour where a turn starts - the same
     * two places the age of the turn is reset - and read on every tick in between, so that the same turn is
     * not the same turn twice. See {@link TurnPace}; the ceiling in {@link AimDynamics} is untouched by it.
     */
    private final TurnPace turnPace = new TurnPace();

    /**
     * The arm's own idle habit: a swing at the air every ten seconds to a minute. It lives here because the
     * question it has to answer before doing anything - is the player standing still with nothing to do -
     * is the one this behaviour already asks for the glance, and the answer is the same gate.
     */
    private final IdleFidget idleFidget = new IdleFidget();

    /**
     * Where the camera was before the idle behaviour took the angles for this tick, so it can be handed
     * back before the frame is drawn. See {@link #updateIdleLook}. Covers the glance and the sway that
     * runs under it, because they are applied to the same angles and have to be handed back together.
     */
    private Rotation idleCamera;
    /**
     * The angle the idle head's offsets ride on while the camera is ours, which is not the camera then: see
     * the PRE branch of {@link #updateIdleLook}. Null whenever the idle head is not the one driving.
     */
    private Rotation idleBase;

    /**
     * What the camera should still be reading this tick if nobody has touched it, from the last tick the
     * idle look wrote: under free look the person's own angles, which POST hands back; under the aim's own
     * camera, the angles the idle look itself left there. Anywhere else is the person's hand - the view is
     * how their arrival is noticed once the job is over. See {@link #updateIdleLook}.
     */
    private Rotation idleExpected;

    /**
     * Who the standing body belongs to in the gap between one job and the next. See {@link Unattended}.
     */
    private final Unattended unattended = new Unattended();

    /**
     * The player's own rotation before the walking wander was applied, or null when it is not applied.
     */
    private Rotation wanderCamera;

    /**
     * The rotation the aim was asked for last tick, used to notice a genuine change of direction. While
     * Baritone is pathing the target is re-set every tick, so "has the target changed?" is not the same
     * question as "is the hand aiming somewhere else now?".
     */
    private Rotation lastWanted;

    /**
     * The lean of the walking gaze onto the line the path takes next. Offered fresh every tick by the
     * path executor and taken by {@link #updateTarget}; one tick of memory at most.
     */
    private final GazeAhead gazeAhead = new GazeAhead();

    /**
     * The look to the side a person takes now and then on a long walk. The path executor says on which
     * ticks the walk is plain enough for one; see {@link #updateHeadWander}, which is where it rides on
     * top of the aim, and {@link WalkGlance} for why it is an offset and not an angle.
     */
    private final WalkGlance walkGlance = new WalkGlance();

    public LookBehavior(Baritone baritone) {
        super(baritone);
        this.processor = new AimProcessor(baritone.getPlayerContext());
        this.smoothYawBuffer = new ArrayDeque<>();
        this.smoothPitchBuffer = new ArrayDeque<>();
    }

    /**
     * The lean onto the line ahead, for the path to offer into. The only thing that ever writes it is
     * {@code PathExecutor}, and the only thing that reads it is {@link #updateTarget} - it is a lean on
     * a walking target and nothing else, so the count of hands on the camera does not change.
     */
    public GazeAhead gazeAhead() {
        return this.gazeAhead;
    }

    /**
     * The walking glance, for the path to welcome on the ticks it is welcome. {@code PathExecutor} is the
     * only thing that says so, and it is only ever read here; the camera is still written in one place.
     */
    public WalkGlance walkGlance() {
        return this.walkGlance;
    }

    /**
     * Where the ground ahead is going, in degrees of pitch - positive down - for the settled gaze to lean
     * along this tick. {@code PathExecutor} is the only thing that knows and the only thing that says, every
     * tick it is on the ground with somewhere to go; the lean lasts the tick, and it only ever moves the pitch
     * of a target that had no opinion about its pitch, by the degree a tick the gaze moves anyway. See
     * {@link PitchWander#lean}, which is where the rule lives, and {@link #settledGaze}, which is where it is
     * applied.
     */
    public void leanOntoTheSlope(float degrees) {
        this.processor.leanOntoTheSlope(degrees);
    }

    @Override
    public void updateTarget(Rotation rotation, boolean blockInteract) {
        // The rotation the movement asked for is kept as it was asked for, because it is two things at
        // once: the angle the game turns the pressed keys by (see onPlayerRotationMove, which hands it to
        // Entity.moveRelative) and the angle the head is to look along. The lean onto the line ahead is
        // only ever the second. Applied to the first, it steered: the keys were chosen against the walk's
        // own yaw and the body was then turned by the leaned one, which walked it up to thirty degrees
        // toward the inside of every corner and off the axis of a parkour run-up. A target about to be
        // clicked is aimed at exactly - the lean stands aside, and takes its tick of memory with it
        // either way.
        this.target = new Target(rotation, this.gazeAhead.lean(rotation, blockInteract), blockInteract,
                Target.Mode.resolve(ctx, blockInteract));
    }

    @Override
    public IAimProcessor getAimProcessor() {
        return this.processor;
    }

    /**
     * How far the head is from the direction the body is being sent, in degrees of yaw.
     * <p>
     * The two are different angles on purpose. The body is steered by the rotation the movement asked for,
     * on the tick it asked ({@link #onPlayerRotationMove}), turned by the keys; the head is where the aim has got to so far,
     * which is a tick behind at best and most of a turn behind at the start of one. This is the gap between
     * them, for the feet to wait on - see {@code HeadFirst}. Asked after the movement has set its target for
     * the tick and before the aim has moved for it, which is the order the path executor runs in, so the
     * head is last tick's aim; on the first tick of a target there is no aim yet and the head is wherever
     * the player is actually looking, which under free look is the angle the server was last told.
     *
     * @param keysOffset the way the pressed keys walk, as an angle off the steering yaw
     *                   ({@code KeyBeat.Keys#yawOffset}): the body goes where the steering yaw and the keys
     *                   together say, which is a diagonal or more off the yaw itself when the keys are not
     *                   W alone
     * @return the gap, as a magnitude; zero when nothing is being aimed at, when the aim is not being
     * driven at all ({@link Target.Mode#NONE}, where the head is the person's own and will never come
     * round), and in a glide, where the keys are a control loop
     */
    public float headBehind(float keysOffset) {
        if (this.target == null || this.target.mode == Target.Mode.NONE || ctx.player().isFallFlying()) {
            return 0f;
        }
        return Math.abs(Rotation.normalizeYaw(this.headRotation().getYaw() - (this.target.rotation.getYaw() + keysOffset)));
    }

    @Override
    public Rotation headRotation() {
        // The aim is where the hand has got to; the sway and the walking glance are added on top of it
        // every tick and taken off again, and are in the player's rotation for anything that reads that.
        // On the first tick of a target there is no aim yet, and the head is wherever the player is
        // looking - under free look, the angle the server was last told.
        return this.aimRotation != null ? this.aimRotation : ctx.playerRotations();
    }

    @Override
    public boolean hasArrivedAt(Rotation rotation) {
        // One question with two sides to it, and the aim processor answers the second: what would the hand
        // do this tick if it were aiming here? When the hand has arrived, that is the rotation it is already
        // holding, a fraction of a degree away - and while it is still travelling it is a step away, because
        // the step toward the target is what the processor returns.
        return AimDynamics.arrived(ctx.playerRotations(), this.processor.peekRotation(rotation));
    }

    @Override
    public void onTick(TickEvent event) {
        if (event.getType() == TickEvent.Type.IN) {
            this.processor.tick();
            // The fidget's clock runs every tick, walking or not; only taking the fidget is gated.
            this.idleFidget.advance();
            // a lean not taken this tick is gone, and so is a welcome not repeated
            this.gazeAhead.tick();
            this.walkGlance.tick();
            this.noticeArrivals();
        }
    }

    /**
     * Turns the head to somebody who has just arrived, the way a person's turns: within half a second,
     * once, and only to somebody in front of it with nothing in between - or to whoever has just hit us,
     * a flinch later and whichever way they are; see {@link #noticeBlows}. See {@link Newcomer} for the
     * noticing. The turning is a glance - the idle one if the body is standing, the walking one if it is
     * on a plain walk - prompted ahead of its clock and otherwise unchanged, so it is never taken while
     * anything is being clicked, never outlives its hold, and always comes home; what it costs is one
     * pass over the players in the room every tick, which is a short list and a ray each.
     */
    private void noticeArrivals() {
        if (ctx.player() == null || ctx.world() == null) {
            return;
        }
        this.noticeBlows();
        final List<Integer> inView = new ArrayList<>();
        final List<Double> distances = new ArrayList<>();
        for (Player other : ctx.world().players()) {
            if (!this.inView(other)) {
                continue;
            }
            // nearest first, which is who a person's eyes go to when several arrive at once
            final double distance = other.distanceToSqr(ctx.player());
            int at = 0;
            while (at < distances.size() && distances.get(at) <= distance) {
                at++;
            }
            inView.add(at, other.getId());
            distances.add(at, distance);
        }
        final int arrived = this.newcomer.notice(inView);
        if (arrived == -1) {
            return;
        }
        final Supplier<Rotation> where = () -> {
            final Entity them = ctx.world().getEntity(arrived);
            if (!(them instanceof LivingEntity) || !this.canBeSeen((LivingEntity) them, INTEREST_RANGE)) {
                return null; // gone again, or round a corner: nothing to look at
            }
            return RotationUtils.calcRotationFromVec3d(ctx.playerHead(), them.getEyePosition(), ctx.playerRotations());
        };
        this.idleGlance.prompt(where);
        this.walkGlance.prompt(() -> {
            final Rotation bearing = where.get();
            if (bearing == null || this.wanderCamera == null) {
                return null;
            }
            // the walking glance is an offset from the aim, not an angle; see WalkGlance
            return new Rotation(Rotation.normalizeYaw(bearing.getYaw() - this.wanderCamera.getYaw()),
                    bearing.getPitch() - this.wanderCamera.getPitch());
        });
    }

    /**
     * Tells {@link Newcomer} when we have just been hit, and by whom as far as can be told from here: the
     * nearest player within {@link #STRUCK_RANGE} that can be seen, or failing one the nearest hostile. The
     * client is not told who dealt a blow, only that one landed - {@code hurtTime} is set to ten and counts
     * down - so this is a guess, and the guess a person makes: they look at whoever is closest and could
     * have done it. A fall or a fire with nobody near is not looked round at, because there is nobody to
     * look at.
     */
    private void noticeBlows() {
        final int hurtTime = ctx.player().hurtTime;
        final boolean freshBlow = hurtTime > this.lastHurtTime;
        this.lastHurtTime = hurtTime;
        if (!freshBlow) {
            return;
        }
        LivingEntity culprit = null;
        double closest = Double.MAX_VALUE;
        for (LivingEntity entity : ctx.world().getEntitiesOfClass(LivingEntity.class, ctx.player().getBoundingBox().inflate(STRUCK_RANGE))) {
            if (!(entity instanceof Player) && !(entity instanceof Enemy)) {
                continue;
            }
            if (!this.canBeSeen(entity, STRUCK_RANGE)) {
                continue;
            }
            // a player over a mob at any distance, the nearest otherwise
            final double distance = entity.distanceToSqr(ctx.player()) - (entity instanceof Player ? STRUCK_RANGE * STRUCK_RANGE : 0);
            if (distance < closest) {
                closest = distance;
                culprit = entity;
            }
        }
        if (culprit != null) {
            // A flinch rather than a glance, because a glance has limits a blow does not: see Flinch. The
            // bearing is asked for again on every tick of the flinch, so a target who walks about is
            // tracked rather than remembered, and comes back null once they are gone - logged out, dead,
            // unloaded - which is a stare with nobody left in it. Whether they can still be seen does not
            // matter: a person keeps looking at the wall somebody stepped behind.
            final int struck = culprit.getId();
            this.flinch.blow(() -> {
                final Entity them = ctx.world().getEntity(struck);
                if (!(them instanceof LivingEntity) || !them.isAlive()) {
                    return null;
                }
                return RotationUtils.calcRotationFromVec3d(ctx.playerHead(), them.getEyePosition(), ctx.playerRotations());
            });
        }
    }

    /**
     * Whether {@code other} is somebody the head could see arrive: another player, alive and visible,
     * within {@link #INTEREST_RANGE}, with a line of sight, and within {@link #ARRIVAL_FIELD} of where the
     * head is pointing.
     */
    private boolean inView(Player other) {
        if (!this.canBeSeen(other, INTEREST_RANGE)) {
            return false;
        }
        final Rotation bearing = RotationUtils.calcRotationFromVec3d(ctx.playerHead(), other.getEyePosition(), ctx.playerRotations());
        return Math.abs(Rotation.normalizeYaw(bearing.getYaw() - ctx.player().getYRot())) <= ARRIVAL_FIELD;
    }

    /**
     * Whether {@code other} is there to be looked at from here: not us, alive, not invisible or
     * spectating, within {@code range} blocks and with nothing in between. Which way the head is pointing
     * is a separate question, and only asked of arrivals.
     */
    private boolean canBeSeen(LivingEntity other, double range) {
        if (other == ctx.player() || !other.isAlive() || other.isInvisible() || other.isSpectator()) {
            return false;
        }
        return other.distanceToSqr(ctx.player()) <= range * range && ctx.player().hasLineOfSight(other);
    }

    @Override
    public void onPlayerUpdate(PlayerUpdateEvent event) {
        // The order of these two is not a matter of taste.
        //
        // The wander is applied after everything else that runs in PRE, because two things downstream depend
        // on reading the player's own angle: the glance works out where to come back to from the camera, and
        // the aim path records prevRotation, which Target.Mode.SERVER restores the view to in POST. Applied
        // first, the wander was baked into both, and the SERVER restore then put the swayed angle back on
        // the camera every tick - each tick starting from the previous tick's leftover, which does not
        // settle, it accumulates. Measured at 32 degrees of drift over two seconds of walking with a
        // server-side target; with the order below, 0.00.
        //
        // In POST it is the other way round: the wander comes off before the aim is put back, so that
        // whichever of the two writes the rotation last, the camera ends up on the player's own angle.
        if (event.getState() != EventState.PRE) {
            this.unwindHeadWander();
            this.handlePlayerUpdate(event);
            return;
        }
        this.handlePlayerUpdate(event);
        this.updateHeadWander();
    }

    /**
     * The body of a player update, with the walking wander left out so that it can be ordered around it.
     */
    private void handlePlayerUpdate(PlayerUpdateEvent event) {
        if (this.target == null) {
            // Nothing has been aimed at since the last tick: forget where the hand was, so that the next
            // target starts from wherever the player is actually looking instead of from a stale angle.
            this.aimRotation = null;
            this.aimAge = 0;
            this.turnFrom = null;
            this.lastWanted = null;
            this.reaction.clear();
            // Nothing is being aimed at, so this is where a person would get bored and have a look about.
            this.updateIdleLook(event.getState());
            return;
        }

        switch (event.getState()) {
            case PRE: {
                if (this.target.mode == Target.Mode.NONE) {
                    // Just return for PRE, we still want to set target to null on POST. The hand is not
                    // driving, so there is no aim: anything that asks where the head is gets the player's
                    // own rotation, and the next target that is driven starts from the camera and not
                    // from wherever the hand last was.
                    this.aimRotation = null;
                    return;
                }

                this.prevRotation = new Rotation(ctx.player().getYRot(), ctx.player().getXRot());
                // the aim is writing the camera again, so the idle head's base is a stale angle from now on
                this.idleBase = null;
                // and so is the idle look's memory of what the camera should read: whatever the aim leaves
                // there is where the idle look picks the body up from when the job is done, not a hand.
                this.idleExpected = null;
                if (ctx.player().isFallFlying()) {
                    // Elytra is a control loop: the flight path is solved assuming the angles it asked for
                    // last tick are the angles it has this tick. A lagging aim there is a crash, not a
                    // personality trait, so flying keeps the old straight-to-target behaviour - and it is
                    // the steering rotation it keeps, not the gaze, for the same reason.
                    this.aimRotation = this.processor.peekRotation(this.target.rotation);
                } else {
                    // What the head is to look along, which is the walk's own direction with the lean
                    // onto the line ahead on it. The body is steered by target.rotation, never by this.
                    final Rotation wanted = this.processor.peekRotation(this.target.gaze);
                    // On a fresh target the hand is wherever the player is currently looking.
                    final Rotation from = this.aimRotation != null ? this.aimRotation : this.prevRotation;
                    if (this.aimRotation == null || this.isNewDirection(wanted)) {
                        // Either there is nothing to continue from, or the thing being aimed at has genuinely
                        // changed direction rather than drifted a little. Both are the start of a turn, and a
                        // turn spins up: without this, a reversal half way along a path - the turn other
                        // players are most likely to be watching - opened at the full speed ceiling, because
                        // the target is re-set every tick while pathing and the hand never looked fresh.
                        this.aimAge = 0;
                        this.turnFrom = from;
                        // ... and it is drawn a tempo of its own, so that the same corner is not turned the
                        // same way every time it is walked. See TurnPace.
                        this.turnPace.begin();
                    }
                    if (standingStill() && this.reaction.holding(wanted)) {
                        // Notice, then move. Deliberately only while the player is not going anywhere:
                        // the movement code reads the facing to decide which keys to press, so letting the
                        // aim lag mid-walk would steer. Standing still, the only thing it costs is a beat
                        // before the turn, and the turn still gets its full spin-up afterwards.
                        this.aimRotation = from;
                        this.aimAge = 0;
                        this.turnFrom = from;
                        // the turn that starts after the beat is a turn of its own, with a tempo of its own
                        this.turnPace.begin();
                    } else {
                        this.aimRotation = this.humanStep(from, wanted);
                        this.aimAge++;
                    }
                    this.lastWanted = wanted;
                }
                ctx.player().setYRot(this.aimRotation.getYaw());
                ctx.player().setXRot(this.aimRotation.getPitch());
                break;
            }
            case POST: {
                // Reset the player's rotations back to their original values
                if (this.prevRotation != null) {
                    // What goes in the buffer is the rotation the server was just told about, which is where
                    // the hand has got to - not the angle it is aiming for. The setting says as much - an
                    // average of the last few server-side rotations - and it used to be the target, which
                    // was the same thing back when the aim arrived in the tick it was asked for. Now that
                    // the hand takes time, averaging the destination draws a straight line from where the
                    // view was to the block being mined, on top of a hand that is moving the way a hand
                    // moves, and the two do not agree: the camera arrives before the arm does.
                    final Rotation smoothed = this.aimRotation != null ? this.aimRotation : this.target.rotation;
                    this.smoothYawBuffer.addLast(smoothed.getYaw());
                    while (this.smoothYawBuffer.size() > Baritone.settings().smoothLookTicks.value) {
                        this.smoothYawBuffer.removeFirst();
                    }
                    this.smoothPitchBuffer.addLast(smoothed.getPitch());
                    while (this.smoothPitchBuffer.size() > Baritone.settings().smoothLookTicks.value) {
                        this.smoothPitchBuffer.removeFirst();
                    }
                    if (this.target.mode == Target.Mode.SERVER) {
                        ctx.player().setYRot(this.prevRotation.getYaw());
                        ctx.player().setXRot(this.prevRotation.getPitch());
                    } else if (ctx.player().isFallFlying() ? Baritone.settings().elytraSmoothLook.value : Baritone.settings().smoothLook.value) {
                        ctx.player().setYRot((float) this.smoothYawBuffer.stream().mapToDouble(d -> d).average().orElse(this.prevRotation.getYaw()));
                        if (ctx.player().isFallFlying()) {
                            ctx.player().setXRot((float) this.smoothPitchBuffer.stream().mapToDouble(d -> d).average().orElse(this.prevRotation.getPitch()));
                        }
                    }
                    //ctx.player().xRotO = prevRotation.getPitch();
                    //ctx.player().yRotO = prevRotation.getYaw();
                    this.prevRotation = null;
                }
                // The target is done being used for this game tick, so it can be invalidated
                this.target = null;
                break;
            }
            default:
                break;
        }
    }

    @Override
    public void onSendPacket(PacketEvent event) {
        if (!(event.getPacket() instanceof ServerboundMovePlayerPacket)) {
            return;
        }

        final ServerboundMovePlayerPacket packet = (ServerboundMovePlayerPacket) event.getPacket();
        if (packet instanceof ServerboundMovePlayerPacket.Rot || packet instanceof ServerboundMovePlayerPacket.PosRot) {
            this.serverRotation = new Rotation(packet.getYRot(0.0f), packet.getXRot(0.0f));
        }
    }

    @Override
    public void onWorldEvent(WorldEvent event) {
        this.serverRotation = null;
        this.target = null;
        // Everything below belongs to the world that has just gone. The Baritone instance outlives it - it
        // is one per client, not one per level - so without this the next target would be eased toward from
        // wherever the aim happened to be in the previous dimension, and the sweep would be visible. The
        // glance's own state would come back to an angle in a level that no longer exists.
        this.aimRotation = null;
        this.aimAge = 0;
        this.turnFrom = null;
        this.lastWanted = null;
        this.wanderCamera = null;
        this.idleCamera = null;
        this.idleBase = null;
        this.idleGlance.reset();
        this.flinch.reset();
        this.reaction.clear();
        this.smoothYawBuffer.clear();
        this.smoothPitchBuffer.clear();
        // The world the person was thought to be at the keyboard of has gone: whoever is here now has
        // not shown up yet, and the first thing the new world's camera reads is not measured against
        // anything the old one left on it.
        this.unattended.forget();
        this.idleExpected = null;
    }

    public void pig() {
        if (this.target != null) {
            final Rotation actual = this.processor.peekRotation(this.target.rotation);
            ctx.player().setYRot(actual.getYaw());
        }
    }

    /**
     * Idle look-around.
     * <p>
     * The angles are applied during PRE, so the client's own rotation packet carries them out to the
     * server, and handed back during POST. PRE runs after the player has moved for this tick and POST
     * runs after {@code sendChanges()}, so what the server is told and what the camera shows are two
     * different things: everyone else sees somebody glancing about, and the person at the keyboard never
     * has the view taken off them. That is the same trick {@link Target.Mode#SERVER} plays for aiming,
     * and it is what makes this safe to do while someone is playing.
     * <p>
     * The sway under the glance is the same arrangement, and it is here rather than in a state machine of
     * its own because it has to share the camera handback: the glance and the sway are two offsets added
     * to the same two numbers, and one of them taking the angles back to where they started while the
     * other still holds them would be a head that drifts a fraction of a degree per tick, which is the
     * failure the walking sway's ordering comment describes.
     */
    private void updateIdleLook(EventState state) {
        switch (state) {
            case PRE: {
                // Who the body belongs to this tick. A process in control owns it outright. In the gap
                // between one job and the next - the goal reached, the mine finished, nothing waiting -
                // it stays ours, and keeps its whole idle repertoire, until the person at the keyboard
                // actually arrives: the view moved, a key or a button down, a screen open. Their arrival
                // is measured on the view, so the camera is read first, against what the idle look left
                // on it last tick; anywhere else is their hand. See {@link Unattended}.
                final boolean inControl = this.baritoneIsInControl();
                final Rotation camera = new Rotation(ctx.player().getYRot(), ctx.player().getXRot());
                final boolean theViewMoved = !inControl && this.idleExpected != null
                        && AimDynamics.separation(camera, this.idleExpected) > VIEW_TAKEN_BACK;
                final boolean ours = this.unattended.glanceIsOurs(inControl,
                        !inControl && this.thePersonIsAtTheKeyboard(), theViewMoved);
                this.idleExpected = null;
                final boolean allowed = ours && standingStill() && !this.interacting();
                // A person standing still does not stop moving. The glance is a coarse thing - one look
                // every few seconds, held and given back - and between two of them a bot used to hold its
                // angle to the last bit, exactly, for as long as the wait lasted. Minutes, under a held
                // pause. The sway keeps running instead, quieter, because there is no stride underneath it,
                // and it fades on the same terms as the walking one so that the two can hand it over.
                if (allowed) {
                    this.headWander.advance();
                } else {
                    this.headWander.recentre();
                }
                // the player's own rotation, read straight from the player above: ctx.playerRotations()
                // returns exactly this (LocalPlayer.getYRot/getXRot) and is what the movement code reads,
                // so the glance goes into the same variable the movement code will read. That is fine for
                // a glance - it only runs while the player is standing still, where nothing downstream
                // reads the facing - and it is why the walking wander, which does run while walking, does
                // not go here.
                // The base the glance and the sway ride on, which is the camera only while the camera is the
                // player's: their view has the head's own offsets taken back off it at the end of every tick,
                // so there is nothing of ours left on it to read. When the camera is ours they stay on it, and
                // reading it again makes the offsets ride on themselves - each tick's sway added to the last
                // tick's, which is a slow oscillation of the sum rather than of the sway. Measured over a
                // hundred minutes of standing about, the view the person is looking through drifts 41 degrees
                // from the angle the head is actually holding; remembering the base instead holds it to half a
                // degree, which is the sway's own amplitude. The old handback hid all of this by putting the
                // camera back every tick, which is exactly why it cannot simply be switched off.
                final boolean theirs = thePersonKeepsTheirView();
                if (!theirs && this.idleBase == null) {
                    this.idleBase = camera;
                }
                final Rotation base = theirs ? camera : this.idleBase;
                // A blow that is still owed a look is not a tick for the idle glance either, and for the
                // same reason: the flinch's angle is added beside this one's, so a glance riding out under a
                // stare is the stare pointing somewhere else. The glance comes home; the sway does not, and
                // neither does the flinch. See Flinch.
                final Rotation wanted = this.idleGlance.next(base, allowed && !this.flinch.answering(), theirs,
                        this::idleStep, this::somethingNearby);
                // What the sway and the flinch below ride on: the glance's angle when one is out, the base
                // otherwise. The sway is read against the base rather than the glance's own angle so that it
                // cannot be mistaken for the glance's bookkeeping, which stays exactly where it was and so
                // still arrives and still comes home.
                final Rotation riding = wanted != null ? wanted : base;
                // The flinch is wanted on the same gate as everything else the standing body does - which
                // has nothing being clicked in it - and with the feet still it may turn the whole body
                // round. Measured against what it is added to, so the stare lands on the attacker rather
                // than on the attacker plus whatever the glance was doing; stood down, it comes home
                // rather than vanishing. See Flinch.
                this.flinch.advance(riding, false, allowed);
                if (!allowed) {
                    this.idleBase = null;
                    // Not ours this tick, and that includes the sway: a leftover half degree rides on the
                    // rotation Baritone traces its own pickaxe and blocks along, and an idle drift is not
                    // worth pointing the crosshair at the wrong block for. Standing still with nothing
                    // being clicked, nothing reads the angle at all, which is the whole reason this is safe.
                    // The view is watched all the same - the person's arrival is noticed on the tick it
                    // happens, whether or not the head is ours that tick - and what the camera reads now
                    // is what it will be measured against.
                    this.idleExpected = camera;
                    return;
                }
                // The sway rides on top of whatever the glance is doing, including nothing, and the flinch
                // rides on top of both.
                final Rotation applied = new Rotation(
                        riding.getYaw() + this.headWander.idleYaw() + this.flinch.yaw(),
                        Rotation.clampPitch(riding.getPitch() + this.headWander.idlePitch() + this.flinch.pitch()));
                this.idleCamera = camera;
                ctx.player().setYRot(applied.getYaw());
                ctx.player().setXRot(applied.getPitch());
                // What the camera should read next tick if nobody has touched it: under free look the
                // person's own angles, which POST hands back; under the aim's own camera, the angles just
                // written, which POST leaves where they are. Armed on the job's own standing ticks too,
                // so the view the first jobless tick is measured against the view the job left, and an
                // idle body nobody comes back for is the same body that was waiting for the job.
                this.idleExpected = theirs ? camera : applied;
                if (this.idleFidget.take()) {
                    // A swing at nothing. Only the swing goes out with it - no click, so it cannot mine,
                    // place or break anything however the crosshair happens to be pointing, which is what
                    // makes it safe to do while Baritone is holding still on someone's behalf. The same
                    // line the block break helper swings for the arm that never moves while it mines.
                    ctx.player().swing(InteractionHand.MAIN_HAND);
                }
                break;
            }
            case POST: {
                if (this.idleCamera != null) {
                    // Handed back only to somebody whose view it is; see thePersonKeepsTheirView.
                    if (thePersonKeepsTheirView()) {
                        ctx.player().setYRot(this.idleCamera.getYaw());
                        ctx.player().setXRot(this.idleCamera.getPitch());
                    }
                    this.idleCamera = null;
                }
                break;
            }
            default:
                break;
        }
    }

    /**
     * Walks the head slightly off the bearing. See {@link #canWander}.
     * <p>
     * Applied on the same terms as the idle glance: during PRE, so the client's rotation packet carries it
     * to the server, and taken back off during POST before the frame is drawn. Everyone else sees a head
     * that moves a little as its owner walks - or, at {@link HeadWander#IDLE_FRACTION}, while it stands
     * there waiting for something to let it carry on; the person at the keyboard sees nothing.
     * <p>
     * The lagging player rotation is not invisible to Baritone itself, though the two channels are worth
     * keeping apart. What runs early - the movement state machines, the key selection - reads
     * {@code ctx.playerRotations()}, which under free look is the last rotation sent to the server, so that
     * one does carry the sway, at its full degree, and that is intended. What reads the player's own angle
     * is this method's own bookkeeping: {@code prevRotation}, and the glance's idea of where it started.
     * Those two have to see the real camera, which is why this runs last in PRE and first in POST.
     */
    private void updateHeadWander() {
        if (this.target == null) {
            // Nothing is being aimed at, so the idle path owns this sway - the same state, at half the
            // amplitude - and it has already advanced or faded it for this tick, in the one place where
            // the glance's angles are written. Doing it here as well would fade out what that just faded
            // in, every tick, and leave the head nailed to the bearing after all. The walking glance has
            // no aim to ride on either, and is only told so; a walk that ends with one still out is a
            // walk whose last two movements were not plain, and that is not one it is welcome on.
            this.walkGlance.advance(false, null);
            return;
        }
        // Two ways for the head to be swaying while something is still being aimed at. Walking along the
        // thing being aimed at is the stride, and it is the full amplitude. Standing still waiting for it -
        // a path that is executing but has nowhere to go this tick, a door somebody else has to open, a
        // block that has not updated yet - is the same sway at half. Without the second one there was a
        // gap in the middle of the behaviour: with a target the idle path is not consulted at all, and
        // standing still while pathing is not walking, so both gates were shut and the head was held
        // exactly on the last angle it was given for as long as the wait lasted.
        final boolean walking = this.canWander();
        // The waiting half of that is also where the idle glance belongs, and for a while it was not
        // there: the glance was only ever consulted by the idle path, which runs on the ticks with no
        // target at all, so a bot standing still *with* one - the door, the wait, the block - swayed at
        // half amplitude and never once looked up from what it was waiting for. That is the state this mod
        // spends the most time in on a long job, and it was the last statue left. The glance is taken
        // against the aimed camera and rides on top of it exactly as the idle path takes it, and its
        // angles are handed back with the sway's, in POST.
        final boolean waiting = !walking && this.canGlanceSomewhere();
        if (!walking && !waiting) {
            // Mining, placing, or not ours to drive: fade the sway out rather than switching it off, so
            // that starting or stopping a walk does not step the head by a degree on one tick. The offsets
            // are taken back off every tick anyway, so this only ever smooths the two ends. Any glance in
            // flight is dropped here, for the same reason one is dropped when Baritone starts doing
            // something: the aim is about to be used again.
            this.idleGlance.drop();
            this.headWander.recentre();
            // The walking glance is the one offset that cannot simply not be added: it is tens of degrees,
            // and a head that is forty degrees to the left on one tick and on the block the next has
            // snapped. It comes back at the speed of a look instead, and until it is back it is still on
            // the camera - which keeps the trace honest, and so keeps the click waiting on the head. The
            // flinch is here for the same reason and on the same terms: this is the branch where something
            // is being clicked, or the head is not ours to turn at all, and a head that was mid-stare when
            // the pickaxe came out still visibly comes off whoever hit it rather than vanishing.
            this.flinch.advance(null, false, false);
            this.walkGlance.advance(false, null);
            if (this.walkGlance.out() || this.flinch.out()) {
                this.wanderCamera = new Rotation(ctx.player().getYRot(), ctx.player().getXRot());
                ctx.player().setYRot(this.wanderCamera.getYaw() + this.walkGlance.yaw() + this.flinch.yaw());
                ctx.player().setXRot(Rotation.clampPitch(this.wanderCamera.getPitch() + this.walkGlance.pitch() + this.flinch.pitch()));
            }
            return;
        }
        this.headWander.advance();
        final float fraction = walking ? 1.0f : HeadWander.IDLE_FRACTION;
        // Both angles are taken from the player and put back, exactly, for the same reason the glance does
        // it: an offset that is only taken off later accumulates. Applied to the yaw alone, the pitch would
        // be added again on top of itself every tick and walk the view up into the sky at six degrees a
        // second.
        this.wanderCamera = new Rotation(ctx.player().getYRot(), ctx.player().getXRot());
        // The glance's angles are absolute and are taken against the aimed camera rather than the swayed
        // one, which is what the idle path does too: the two offsets ride on the same base and are handed
        // back together in POST, so neither can take the head home while the other still holds it.
        // The idle glance has the waiting ticks, unless the walking glance is still out on them: the feet
        // stop for some of its looks, and the two must not be on the head at once.
        final Rotation glance = this.idleGlance.next(this.wanderCamera, waiting && !this.walkGlance.out(), this::idleStep, this::somethingNearby);
        final Rotation base = glance != null ? glance : this.wanderCamera;
        // The walking glance rides on the same base, as an offset. It is welcome when the walk is plain
        // and the target is not something about to be clicked - a block to break, a face to place against
        // - and not otherwise; standing still waiting is the idle glance's turn, not this one's, except
        // while the feet have stopped for a look that is already out, and a head the aim is not driving at
        // all (Mode.NONE: the person's own view, walked along) is not ours to turn by forty degrees.
        // Something nearby worth looking at is offered as a way off the aim, which is what the glance is
        // made of.
        final boolean onTheWalk = walking || (waiting && this.walkGlance.out());
        // A blow that is still owed a look is not a tick for the road: a person staring at whoever just hit
        // them is not also looking at the cow. The flinch's angle is added on top of this glance's (below),
        // and this glance is the one that can be tens of degrees wide, so a glance that started before the
        // blow is handed back rather than ridden out under the stare - and one that comes home leaves the
        // head where the stare put it. See Flinch.
        this.walkGlance.advance(onTheWalk && this.walkGlance.welcomed() && !this.target.blockInteract
                && this.target.mode != Target.Mode.NONE && !this.flinch.answering(), () -> {
            final Rotation nearby = this.somethingNearby();
            if (nearby == null) {
                return null;
            }
            return new Rotation(Rotation.normalizeYaw(nearby.getYaw() - this.wanderCamera.getYaw()),
                    nearby.getPitch() - this.wanderCamera.getPitch());
        });
        // The flinch rides on this same head, and this is the same head the sway rides: wanted wherever the
        // sway is wanted, which is what both flags above already mean - every gate they carry has nothing
        // being clicked in it - and stood down wherever it is not. The feet half of it is the walk: a body
        // that is walking cannot come round without walking backwards, so the turn is held to a neck's
        // worth while it is one, and a body that is standing still turns whole. It is measured against the
        // angle it is added to - which is the walking glance's as well, since that is on the head and is
        // what this is added on top of - so the stare lands on the attacker rather than on the attacker
        // plus whatever the road was showing at the time. The sway is deliberately not in there: it is
        // meant to keep riding on the stare. See Flinch.
        final Rotation stareFrom = new Rotation(base.getYaw() + this.walkGlance.yaw(),
                base.getPitch() + this.walkGlance.pitch());
        this.flinch.advance(stareFrom, walking, true);
        ctx.player().setYRot(base.getYaw() + this.walkGlance.yaw() + this.headWander.yaw() * fraction + this.flinch.yaw());
        ctx.player().setXRot(Rotation.clampPitch(base.getPitch() + this.walkGlance.pitch() + this.headWander.pitch() * fraction + this.flinch.pitch()));
    }

    /**
     * Somebody nearby worth a glance, as an angle to look along, or null when there is nobody to notice.
     * <p>
     * Only ever asked as a glance is starting, so the search costs nothing on the ticks in between. The
     * nearest one wins, because that is the one a person's eyes go to. Invisible things are skipped: a bot
     * that turns to look at somebody it cannot see is answering a question nobody asked, and answering it
     * wrongly. Anything with a wall between it and the player is skipped for the same reason, which is the
     * difference between noticing a passer-by and turning to face a wall.
     */
    private Rotation somethingNearby() {
        LivingEntity nearest = null;
        double closest = INTEREST_RANGE * INTEREST_RANGE;
        for (LivingEntity entity : ctx.world().getEntitiesOfClass(LivingEntity.class, ctx.player().getBoundingBox().inflate(INTEREST_RANGE))) {
            if (entity == ctx.player() || !entity.isAlive() || entity.isInvisible()) {
                continue;
            }
            final double distance = entity.distanceToSqr(ctx.player());
            if (distance >= closest || !ctx.player().hasLineOfSight(entity)) {
                continue;
            }
            closest = distance;
            nearest = entity;
        }
        if (nearest == null) {
            return null;
        }
        final Vec3 eyes = ctx.playerHead();
        return RotationUtils.calcRotationFromVec3d(eyes, nearest.getEyePosition(), ctx.playerRotations());
    }

    private void unwindHeadWander() {
        if (this.wanderCamera != null) {
            // The same rule as the idle head's, with one extra clause. Nobody who is not looking through
            // this camera needs the angles taken back off it, but leaving them on only costs nothing while
            // something rewrites the camera from scratch every tick - which the aim does, and only when it
            // is being applied at all (a target under {@code Mode.NONE} writes nothing and would let the
            // sway build on itself). What the server was told does not change either way: the packet went
            // out with the sway on it, between this and the aim's own handback.
            if (thePersonKeepsTheirView() || this.prevRotation == null) {
                ctx.player().setYRot(this.wanderCamera.getYaw());
                ctx.player().setXRot(this.wanderCamera.getPitch());
            }
            this.wanderCamera = null;
        }
    }

    /**
     * Whether the view on the person's screen is theirs or the bot's.
     * <p>
     * Free look is the setting that decides it, and the aim already follows it: with free look on, the
     * angles Baritone drives are carried out to the server by the client's own rotation packet and the
     * person's camera is handed back untouched, so the aim is something only everybody else sees. With it
     * off, the person has said they want to watch the bot do the work, and the aim is written onto their
     * camera and stays there.
     * <p>
     * The head has to follow the same rule as the aim, or the mod takes the camera for one thing and not for
     * the other: a free-look-off player would be looking through a camera pinned exactly to the block centre
     * their bot is walking at, with none of the sway and none of the glances that everyone else can see,
     * which is the turret this fork exists to not be. The angles the server is told are the same either way.
     */
    private static boolean thePersonKeepsTheirView() {
        return Baritone.settings().freeLook.value;
    }

    /**
     * Whether the head is ours to move, for the walk.
     * <p>
     * Three conditions and all three are load bearing. The player has to be moving along - on foot, in
     * water or in the air, which are the same deal as far as the head is concerned (see {@link
     * #isSwimming} and {@link #isInTheAir}) - because this is the stride, and standing still is a
     * different gate and a quieter sway. Baritone has to be the one driving,
     * because a head that moves on its own while a person is walking somewhere is not human, it is broken.
     * And nothing may be being clicked this tick, for the reason given on {@link #interacting()}: the trace
     * Baritone aims its own pickaxe and blocks along follows the rotation it last told the server, and a
     * block being broken is not the place for a degree of drift.
     * <p>
     * What this does *not* promise is that the walk itself is untouched. Under free look, the movement code
     * chooses which keys to press from {@code playerRotations()}, which is the server rotation, so the sway
     * is in there at its full one degree. That is the intended shape of it - a walk that wobbles slightly -
     * and HeadWanderTest bounds what it can add up to: 0.38 blocks of sideways deviation over a walk of four
     * thousand three hundred.
     */
    private boolean canWander() {
        return this.target != null
                && (this.isWalking() || this.isSwimming() || this.isInTheAir())
                && baritone.getPathingBehavior().isPathing()
                && !this.interacting();
    }

    /**
     * Whether the player is moving along the ground at a walking pace.
     */
    private boolean isWalking() {
        if (!ctx.player().onGround() || ctx.player().isInWater() || ctx.player().isFallFlying()) {
            return false;
        }
        return movingAlong();
    }

    /**
     * Whether the player is swimming along.
     * <p>
     * The same deal as walking, as far as the head is concerned, which is why the stride sway is allowed in
     * water as well: the game turns a swim by the yaw exactly as it turns a walk, so a degree of drift in
     * the yaw is a degree of drift in the heading - which is what this sway is, and what
     * {@code HeadWanderTest} bounds. The pitch is the part that is not the same: it steers the depth, so
     * the wander leaves it alone and the sway only ever moves it by the six tenths of a degree that is the
     * head moving rather than the body (see the note on the wander in {@code AimProcessor}).
     * <p>
     * Deliberately not a gate for anything else: the carry past a target
     * ({@link #carriedPast}) is asked for on dry land only, because a few degrees of heading is worth more
     * than the six tenths this takes and there is more than one reason to be careful in water.
     */
    private boolean isSwimming() {
        if (!ctx.player().isInWater() || ctx.player().isFallFlying()) {
            return false;
        }
        return movingAlong();
    }

    /**
     * The speed half of both of those: is the player getting anywhere this tick, on the ground or in water.
     */
    private boolean movingAlong() {
        final double dx = ctx.player().getDeltaMovement().x;
        final double dz = ctx.player().getDeltaMovement().z;
        return dx * dx + dz * dz > IDLE_SPEED_SQ;
    }

    /**
     * Whether the player is off the ground and not being steered by the look: a fall, a jump, a climb,
     * anything between the ground and the ground.
     * <p>
     * The feet leaving the ground used to switch the stride off - {@link #isWalking()} is dry land with the
     * feet down, and {@link #isSwimming()} is water - so every drop of a block or two took the sway out of
     * the head for the length of the fall and then faded the last of it out over the twenty ticks after
     * that ({@link HeadWander#recentre} is five percent a tick). On rolling ground, where a path of any
     * length is a run of little descents, that is most of a walk spent with a head that is not moving at
     * all: the statue this sway exists to avoid, switched by the shape of the terrain rather than by
     * anything the head was doing.
     * <p>
     * Nothing about the air argues for it. The game turns a fall by the yaw exactly as it turns a walk, so a
     * degree of drift in the yaw is a degree of drift in the heading - the same sentence the swim gate makes
     * - and the pitch steers nothing at all up there, which is the one place this gate is simpler than that
     * one: there is no axis to leave alone. The two states that do steer the body by the look are water,
     * where the pitch is the depth, and an elytra, which is a control loop; both are excluded here, and the
     * elytra is excused from everything in this file for that reason.
     * <p>
     * Deliberately without the speed test the other two carry. A walk stops when the player does, and the
     * quieter sway takes over; there is no standing still in the air, and a straight drop with no speed to
     * measure on either horizontal axis is still the body moving under the head.
     */
    private boolean isInTheAir() {
        return !ctx.player().onGround() && !ctx.player().isInWater() && !ctx.player().isFallFlying();
    }


    /**
     * Whether an idle glance is appropriate right now.
     * <p>
     * Every one of these is a reason to keep still instead, and the first is the one that took longest to
     * get right. Requiring a path to be *executing* sound like the honest version of "Baritone is the one
     * driving", but it left out every state where Baritone is in charge and deliberately not moving: a
     * player who pauses it, a build waiting on blocks, a search still thinking. In those the bot used to
     * stand exactly as it was put, head and all, for as long as the state lasted - minutes of it, under a
     * held pause - which is the most statue-like thing in the whole mod, and the states a person fidgets in
     * most are the ones where they have been told to wait.
     * <p>
     * What the question actually is: is anyone else driving the body? A process in control this tick means
     * Baritone is, whether it is executing, calculating or waiting. If none is, the body is in the gap
     * between one job and the next, and that gap has a rule of its own - see {@link Unattended}: the body
     * stays Baritone's until the person actually shows up, so this method answers only for the ticks that
     * are still a job's, where a person's own head is not ours to animate.
     * <p>
     * It is asked of the ticks that have a target: the bot is standing still waiting on the thing being
     * aimed at - see {@link #updateHeadWander}. The ticks with no target at all ask the wider question
     * on the spot, because there the body may be ours even with no process in control - the gap between
     * one job and the next, see {@link Unattended} - so the gate lives inline in {@link #updateIdleLook}.
     */
    private boolean canGlanceSomewhere() {
        return this.baritoneIsInControl() && standingStill() && !this.interacting();
    }

    /**
     * Whether the person at the keyboard has shown any sign of being there.
     * <p>
     * Asked only once the job is over and the body is waiting for its next one, because that is the
     * state whose ownership the answer decides: the body is Baritone's until the person arrives, and
     * this is how they arrive. Any input at all counts - the view moving is measured by the caller
     * against what the idle look last wrote, and this covers the rest: a key held, a mouse button down,
     * a screen of any kind open, chat, a chest, the pause menu. Keys are read from the game's own input,
     * which is the person's again the moment nothing is being driven; while a process holds control the
     * game's input is Baritone's, which is exactly why this is not asked there.
     */
    private boolean thePersonIsAtTheKeyboard() {
        if (ctx.minecraft().screen != null) {
            return true;
        }
        // The use button is the one key a behaviour of ours sets: eating holds it for the length of a
        // bite. A button we are holding is not somebody arriving, and counting it as though it were cost
        // the idle look its whole repertoire for as long as nothing was running, because the latch this
        // feeds (Unattended#glanceIsOurs) is only released by the next job. Found by Sandpiper's second
        // read, 2026-10-11 17:17.
        if (ctx.minecraft().options.keyAttack.isDown()
                || (ctx.minecraft().options.keyUse.isDown()
                    && !baritone.getInputOverrideHandler().isUseButtonHeldForUs())) {
            return true;
        }
        final net.minecraft.world.entity.player.Input keys = ctx.player().input.keyPresses;
        return keys.forward() || keys.backward() || keys.left() || keys.right()
                || keys.jump() || keys.shift() || keys.sprint();
    }

    /**
     * Whether Baritone has some process in control of the body this tick, executing a path or not.
     */
    private boolean baritoneIsInControl() {
        return baritone.getPathingControlManager().mostRecentInControl().isPresent();
    }

    /**
     * Whether the rotation being aimed at is a different direction from the one aimed at last tick, rather
     * than the same thing drifting a little as the player walks toward it.
     */
    private boolean isNewDirection(Rotation wanted) {
        return this.lastWanted == null
                || AimDynamics.separation(this.lastWanted, wanted) > ReactionDelay.NEW_DIRECTION_ANGLE;
    }

    /**
     * Whether Baritone is driving a click this tick.
     * <p>
     * Both behaviours that move the head are gated on this, and the reason is the ray trace rather than the
     * aim. {@code BaritonePlayerContext.objectMouseOver()} traces along {@code playerRotations()}, which
     * under free look is {@code serverRotation} - the last rotation sent to the server - so a rotation this
     * code has applied and sent is what Baritone's own pickaxe and blocks are aimed along until the next
     * packet. A degree of drift there moves the trace a few centimetres; the glance's twenty-eight would
     * move it most of a block. Standing still with nothing being clicked, none of that applies and the head
     * is free.
     */
    private boolean interacting() {
        return baritone.getInputOverrideHandler().isInputForcedDown(Input.CLICK_LEFT)
                || baritone.getInputOverrideHandler().isInputForcedDown(Input.CLICK_RIGHT);
    }

    /**
     * Whether the player is stationary on dry ground.
     * <p>
     * This is what separates the two halves of the look behaviour. Standing still, nothing downstream cares
     * where the aim is, so the hand may hesitate and wander. Walking, swimming or falling, the movement code
     * picks which keys to press from the direction the player is facing, so an aim that lags the direction
     * it was asked for steers the player, and both of those behaviours have to stay out of the way.
     */
    private boolean standingStill() {
        if (!ctx.player().onGround() || ctx.player().isInWater() || ctx.player().isFallFlying()) {
            return false;
        }
        final double dx = ctx.player().getDeltaMovement().x;
        final double dz = ctx.player().getDeltaMovement().z;
        return dx * dx + dz * dz < IDLE_SPEED_SQ;
    }

    /**
     * Moves {@code from} one tick's worth of hand movement toward {@code to}.
     * <p>
     * Two properties matter here and both are copied from the way the game itself handles a mouse. The
     * step eases out, so the aim decelerates into the target instead of arriving at full speed and
     * stopping dead, and it is quantized to whole mouse pixels, which is what lets the aim settle
     * exactly on target instead of hunting around it forever.
     * <p>
     * The third is a rate: a turn that is being made in order to click something runs at
     * {@link AimDynamics#CLICK_EASE}. Mining and placing both wait for the trace to be on the block before
     * they click - the game acts along the trace from wherever the crosshair really is - so the slower rate
     * is not safety there, it is ticks spent standing in front of a block the bot has already chosen.
     * Everything else - walking, looking about, the idle sway - keeps {@link AimDynamics#AIM_EASE}, and so
     * does the last of a click's turn once the crosshair is on the block and face being clicked
     * ({@link #crosshairIsOn}): from there nothing is waiting on the hand - the pickaxe is already
     * swinging at the right block - and the rest of the way to its point on the block is a hand settling,
     * not a hand snapping the last few degrees because a click began.
     */
    private Rotation humanStep(Rotation from, Rotation to) {
        final float ease = this.interacting() && !this.crosshairIsOn(to) ? AimDynamics.CLICK_EASE : AimDynamics.AIM_EASE;
        final Rotation wanted = this.carriedPast(this.settledGaze(from, to));
        return AimDynamics.humanStep(from, wanted, this.aimAge, sensitivity(ctx), ease, this.turnPace.pace());
    }

    /**
     * Whether the crosshair, where it really is, is already on the block and the face that aiming at
     * {@code to} would put it on.
     */
    private boolean crosshairIsOn(Rotation to) {
        final HitResult now = ctx.objectMouseOver();
        if (now == null || now.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        final HitResult there = RayTraceUtils.rayTraceTowards(ctx.player(), to, ctx.playerController().getBlockReachDistance(), false);
        if (there == null || there.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        final BlockHitResult here = (BlockHitResult) now;
        final BlockHitResult wanted = (BlockHitResult) there;
        return here.getBlockPos().equals(wanted.getBlockPos()) && here.getDirection() == wanted.getDirection();
    }

    /**
     * Where the hand is actually aimed this tick: the target, or the target with the carry of a young turn
     * on it. See {@link AimDynamics#overshoot}.
     * <p>
     * The carry is a flourish, and it is not for everything. A target that is about to be acted on - a block
     * to break, a face to place against - is aimed at exactly: there the angles are the point of asking for
     * them, the trace follows them to the centimetre, and the movement waits for them to be there before it
     * clicks. The targets that get the carry are the ones that say where the bot is walking, and only while
     * it is walking: the flourish is a person turning while they walk, and a bot standing still has nothing
     * to carry.
     */
    private Rotation carriedPast(Rotation to) {
        if (this.target.blockInteract || this.interacting() || !this.isWalking()) {
            return to;
        }
        return AimDynamics.overshoot(this.turnFrom, to, this.aimAge);
    }

    /**
     * The rotation to aim at, for a target that has no opinion about the pitch.
     * <p>
     * Every walking target is built by echoing the player's own pitch straight back - {@code new
     * Rotation(yaw, ctx.playerRotations().getPitch())} is the idiom for a target with something to say
     * about the yaw and nothing to say about the pitch - so aiming at one exactly is holding whichever
     * angle the journey happened to start on until it ends. Nobody's gaze does that. It settles a few
     * degrees up or down while they walk, holds that for a few seconds, and moves on, and that habit is
     * {@link PitchWander}, drawn and tested and described long before this: a preference two to seven
     * seconds long, out to ten degrees but usually a few, approached at a degree a tick and left alone
     * once it is reached.
     * <p>
     * It used to be applied inside {@code peekRotation}, which is the copy of the rotation the game's own
     * movement reads. On dry ground - the only place the settle is allowed - that copy's pitch is read by
     * nobody: {@code Entity.moveRelative} turns its input by the yaw alone, and the trade packet carries the
     * aim, not the peek. So the habit only ever came out on the tracing side of the aiming, where it could
     * delay an arrival gate by a degree and nothing else. It is applied here instead, to the angle the
     * server is told, which is the one anybody watching can see.
     * <p>
     * Not for every target. The same two rules the carry uses, argued once at {@link #carriedPast}: a target
     * that is about to be acted on asked for an angle in particular and gets exactly it, and nothing may be
     * settling while a click is in flight, because the rotation this returns is the one Baritone traces its
     * own pickaxe along. The third is about the body rather than the target: in water the pitch is the
     * swimmer's depth and in a glide it is the attitude the elytra is holding, which is the whole subject of
     * {@code AbstractAimProcessor#pitchIsAFreeAngle}.
     * <p>
     * The aim's own pitch is what is stepped, not the one the target echoed: the settle has to be continuous
     * with the angle the aim is actually holding, or the destination would be a degree from the echo and the
     * aim would walk at it forever without ever asking for the same angle twice - which is also what makes
     * the settle a settle. Once the aim is inside the wander's dead band, {@link PitchWander#step} hands back
     * the pitch it was given, and the destination is the aim, and the gaze holds.
     */
    private Rotation settledGaze(Rotation from, Rotation to) {
        if (this.target.blockInteract || this.interacting() || !this.processor.pitchIsAFreeAngle()) {
            return to;
        }
        if (to.getPitch() != ctx.playerRotations().getPitch()) {
            // The target asked for a pitch of its own - down a hole, up at a block face - and gets it. This
            // is the same question peekRotation asks of the same rotation, and it is asked of the same value:
            // ctx.playerRotations() is what the movement echoed, under either free look setting.
            return to;
        }
        return new Rotation(to.getYaw(), this.processor.settledPitch(from.getPitch()));
    }

    /**
     * One tick of movement for the idle glance, with no spin-up of its own: it is a small absent-minded
     * movement, not a turn.
     * <p>
     * The speed budget is the glance's own and not the aim's. The flick ceiling is what a hand does when it
     * is told to point at something; a bot standing about with nothing to do should not be moving its head at
     * a deliberate aiming speed, and measured over a paused minute it was - 11.7 degrees in a single tick,
     * 585 deg/s, at the peak of every glance. {@link AimDynamics#IDLE_YAW_STEP} is where that came from and
     * what replaced it.
     */
    private Rotation idleStep(Rotation from, Rotation to, int legAge) {
        // the looking speed, spun up over the first ticks of each leg the way the aim's own turns are
        final float ramp = AimDynamics.ramp(legAge);
        return AimDynamics.step(from, to, AimDynamics.IDLE_YAW_STEP * ramp, AimDynamics.IDLE_PITCH_STEP * ramp,
                sensitivity(ctx));
    }

    /**
     * The player's mouse sensitivity, which is what turns mouse pixels into degrees.
     * <p>
     * Static, and taking the context as an argument, because the aim processor below is a static nested
     * class: it holds its own context and cannot reach anything that belongs to this instance.
     */
    private static double sensitivity(IPlayerContext ctx) {
        return ctx.minecraft().options.sensitivity().get();
    }

    public Optional<Rotation> getEffectiveRotation() {
        if (Baritone.settings().freeLook.value) {
            return Optional.ofNullable(this.serverRotation);
        }
        // If freeLook isn't on, just defer to the player's actual rotations
        return Optional.empty();
    }

    @Override
    public void onPlayerRotationMove(RotationMoveEvent event) {
        if (this.target != null) {
            final Rotation actual = this.processor.peekRotation(this.target.rotation);
            event.setYaw(actual.getYaw());
            event.setPitch(actual.getPitch());
        }
    }

    private static final class AimProcessor extends AbstractAimProcessor {

        public AimProcessor(final IPlayerContext ctx) {
            super(ctx);
        }

        @Override
        protected Rotation getPrevRotation() {
            // Implementation will use LookBehavior.serverRotation
            return ctx.playerRotations();
        }
    }

    private static abstract class AbstractAimProcessor implements ITickableAimProcessor {

        protected final IPlayerContext ctx;
        private final ForkableRandom rand;
        private final PitchWander pitchWander;
        private double randomYawOffset;
        private double randomPitchOffset;

        public AbstractAimProcessor(IPlayerContext ctx) {
            this.ctx = ctx;
            this.rand = new ForkableRandom();
            this.pitchWander = new PitchWander();
        }

        private AbstractAimProcessor(final AbstractAimProcessor source) {
            this.ctx = source.ctx;
            this.rand = source.rand.fork();
            this.randomYawOffset = source.randomYawOffset;
            this.randomPitchOffset = source.randomPitchOffset;
            // A fork of the noise is a fork of the same hand, so the offsets are carried over; the pitch
            // wander is not, because the things that fork - the elytra flight solver, which calls this
            // processor hundreds of times a tick to try out a route - advance it every call, and a habit
            // that lives in seconds has no meaning at that rate. Starting the fork at level is the
            // harmless answer: at worst the simulated gaze is a degree or two from the real one.
            this.pitchWander = new PitchWander();
        }

        /**
         * Whether this body is free to let its gaze settle where it likes.
         * <p>
         * Two states where it is not, and both of them are places the movement follows the look: water,
         * where sprint-swimming drives the player's depth off the look vector's pitch, and a glide, where
         * the flight solver solves for the attitude it asked for. Everywhere else the pitch is decoration -
         * on the ground the keys are turned by the yaw alone, whatever the crosshair is pointed at - which
         * is what makes it the one angle a walking bot is allowed to be careless with. The habit that uses
         * the freedom is {@link LookBehavior#settledGaze}, and this is the gate it asks.
         */
        boolean pitchIsAFreeAngle() {
            return !ctx.player().isInWater() && !ctx.player().isFallFlying();
        }

        /**
         * The pitch a gaze that has settled somewhere would ask for next, from where the aim is holding it.
         * <p>
         * One tick of the habit {@link PitchWander} describes: a degree toward wherever the gaze has settled
         * for these few seconds, and exactly the pitch it was handed once it is already there, so that a gaze
         * that has arrived stays arrived instead of buzzing against its own preference. A person's gaze moves
         * in steps about this size and then rests; the rules are the wander's own and only the aiming side of
         * them is here.
         */
        final float settledPitch(float pitch) {
            return this.pitchWander.step(pitch);
        }

        /**
         * Where the ground ahead is going, for the gaze to settle along this tick; see
         * {@link PitchWander#lean} and {@link LookBehavior#leanOntoTheSlope}.
         */
        final void leanOntoTheSlope(float degrees) {
            this.pitchWander.lean(degrees);
        }

        @Override
        public final Rotation peekRotation(final Rotation rotation) {
            final Rotation prev = this.getPrevRotation();

            float desiredYaw = rotation.getYaw();
            float desiredPitch = rotation.getPitch();

            // The pitch of a target that has no opinion about it is not settled here any more, and the
            // reason is that here is the wrong side of the looking glass. What this returns is the rotation
            // the game's own movement reads, and on dry ground - the only place the settle is free to happen
            // at all - the movement reads the yaw out of it and nothing else, and the packet the client sends
            // carries the aim rather than this. So the habit lived in a copy nobody looked at and moved an
            // arrival gate by a degree besides. It is applied to the aim now, in
            // {@link LookBehavior#settledGaze}, under the same three gates and by the same rules.

            desiredYaw += this.randomYawOffset * AimDynamics.TREMOR_GAIN;
            desiredPitch += this.randomPitchOffset * AimDynamics.TREMOR_GAIN;

            return new Rotation(
                    this.calculateMouseMove(prev.getYaw(), desiredYaw),
                    this.calculateMouseMove(prev.getPitch(), desiredPitch)
            ).clamp();
        }

        @Override
        public final void tick() {
            // randomLooking
            final double rawYawOffset = (this.rand.nextDouble() - 0.5) * Baritone.settings().randomLooking.value;
            final double rawPitchOffset = (this.rand.nextDouble() - 0.5) * Baritone.settings().randomLooking.value;

            // randomLooking113: the wander proper, and on both axes. It was drawn into the yaw alone, and
            // the pitch took only the hundredth of a degree randomLooking adds - small enough that the
            // mouse grid swallows it whole, so a bot breaking a block moved its head sideways by a degree
            // or two and up and down by nothing at all. Each axis draws for itself, because a hand that
            // wandered along a line would be a hand that is being moved.
            final double rawYaw113 = rawYawOffset + AimDynamics.tremor(this.rand.nextDouble(),
                    Baritone.settings().randomLooking113.value);
            final double rawPitch113 = rawPitchOffset + AimDynamics.tremor(this.rand.nextDouble(),
                    Baritone.settings().randomLooking113.value);

            // All of those draw fresh noise every single tick, and at 20Hz that is not a hand, it is a
            // shiver: uncorrelated jitter is something no person produces and the eye reads it as a
            // machine. Low pass them so the offset wanders the way an unsteady wrist does, and scale the
            // result back up so the settings keep the amplitude they always had.
            this.randomYawOffset = AimDynamics.smoothOffset(this.randomYawOffset, (float) rawYaw113);
            // The pitch is only the hand's to wander where the body is not travelling by it: in water it is
            // the swimmer's depth and in a glide it is the attitude the flight solver is holding, which is
            // the same rule pitchIsAFreeAngle answers before a gaze is settled. A bot that holds its depth
            // only because the camera is looking down is not a person, however well the camera moves.
            this.randomPitchOffset = this.pitchIsAFreeAngle()
                    ? AimDynamics.smoothOffset(this.randomPitchOffset, (float) rawPitch113)
                    : 0.0;

            // and the gaze the pitch settles on, which turns over in the seconds rather than the ticks
            this.pitchWander.advance();
        }

        @Override
        public final void advance(int ticks) {
            for (int i = 0; i < ticks; i++) {
                this.tick();
            }
        }

        @Override
        public Rotation nextRotation(final Rotation rotation) {
            final Rotation actual = this.peekRotation(rotation);
            this.tick();
            return actual;
        }

        @Override
        public final ITickableAimProcessor fork() {
            return new AbstractAimProcessor(this) {

                private Rotation prev = AbstractAimProcessor.this.getPrevRotation();

                @Override
                public Rotation nextRotation(final Rotation rotation) {
                    return (this.prev = super.nextRotation(rotation));
                }

                @Override
                protected Rotation getPrevRotation() {
                    return this.prev;
                }
            };
        }

        protected abstract Rotation getPrevRotation();

        private float calculateMouseMove(float current, float target) {
            final float delta = target - current;
            final double deltaPx = angleToMouse(delta); // yes, even the mouse movements use double
            return current + mouseToAngle(deltaPx);
        }

        private double angleToMouse(float angleDelta) {
            final float minAngleChange = mouseToAngle(1);
            return Math.round(angleDelta / minAngleChange);
        }

        private float mouseToAngle(double mouseDelta) {
            return AimDynamics.mouseToAngle(sensitivity(ctx), mouseDelta);
        }
    }

    private static class Target {

        /**
         * The rotation the movement asked for, exactly as it asked for it. This is the one the body is
         * steered by: {@link #onPlayerRotationMove} hands it to the game in place of the player's own
         * angle when the pressed keys are turned into motion, so the keys a movement chose against it
         * come out pointing where the movement meant. Nothing that is only about how the head looks may
         * be applied to it.
         */
        public final Rotation rotation;
        /**
         * Where the head is to look, which is {@link #rotation} with the lean onto the line ahead on it
         * ({@link GazeAhead}), and is identical to it on a straight line and for anything about to be
         * clicked. The aim eases toward this; the body never reads it.
         */
        public final Rotation gaze;
        /**
         * Whether acting on this rotation is the point of it - a block to break, a face to place against -
         * rather than it saying where the movement is going. Decides whether the aim may carry past it.
         */
        public final boolean blockInteract;
        public final Mode mode;

        public Target(Rotation rotation, Rotation gaze, boolean blockInteract, Mode mode) {
            this.rotation = rotation;
            this.gaze = gaze;
            this.blockInteract = blockInteract;
            this.mode = mode;
        }

        enum Mode {
            /**
             * Rotation will be set client-side and is visual to the player
             */
            CLIENT,

            /**
             * Rotation will be set server-side and is silent to the player
             */
            SERVER,

            /**
             * Rotation will remain unaffected on both the client and server
             */
            NONE;

            static Mode resolve(IPlayerContext ctx, boolean blockInteract) {
                final Settings settings = Baritone.settings();
                final boolean antiCheat = settings.antiCheatCompatibility.value;
                final boolean blockFreeLook = settings.blockFreeLook.value;

                if (ctx.player().isFallFlying()) {
                    // always need to set angles while flying
                    return settings.elytraFreeLook.value ? SERVER : CLIENT;
                } else if (settings.freeLook.value) {
                    // Regardless of if antiCheatCompatibility is enabled, if a blockInteract is requested then the player
                    // rotation needs to be set somehow, otherwise Baritone will halt since objectMouseOver() will just be
                    // whatever the player is mousing over visually. Let's just settle for setting it silently.
                    if (blockInteract) {
                        return blockFreeLook ? SERVER : CLIENT;
                    }
                    return antiCheat ? SERVER : NONE;
                }

                // all freeLook settings are disabled so set the angles
                return CLIENT;
            }
        }
    }
}
