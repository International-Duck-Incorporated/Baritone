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
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.api.utils.input.Input;
import baritone.behavior.look.AimDynamics;
import baritone.behavior.look.ForkableRandom;
import baritone.behavior.look.HeadWander;
import baritone.behavior.look.IdleFidget;
import baritone.behavior.look.IdleGlance;
import baritone.behavior.look.PitchWander;
import baritone.behavior.look.ReactionDelay;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.Random;

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

    private final IdleGlance idleGlance = new IdleGlance();

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
     * The player's own rotation before the walking wander was applied, or null when it is not applied.
     */
    private Rotation wanderCamera;

    /**
     * The rotation the aim was asked for last tick, used to notice a genuine change of direction. While
     * Baritone is pathing the target is re-set every tick, so "has the target changed?" is not the same
     * question as "is the hand aiming somewhere else now?".
     */
    private Rotation lastWanted;

    public LookBehavior(Baritone baritone) {
        super(baritone);
        this.processor = new AimProcessor(baritone.getPlayerContext());
        this.smoothYawBuffer = new ArrayDeque<>();
        this.smoothPitchBuffer = new ArrayDeque<>();
    }

    @Override
    public void updateTarget(Rotation rotation, boolean blockInteract) {
        this.target = new Target(rotation, Target.Mode.resolve(ctx, blockInteract));
    }

    @Override
    public IAimProcessor getAimProcessor() {
        return this.processor;
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
        }
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
            this.lastWanted = null;
            this.reaction.clear();
            // Nothing is being aimed at, so this is where a person would get bored and have a look about.
            this.updateIdleLook(event.getState());
            return;
        }

        switch (event.getState()) {
            case PRE: {
                if (this.target.mode == Target.Mode.NONE) {
                    // Just return for PRE, we still want to set target to null on POST
                    return;
                }

                this.prevRotation = new Rotation(ctx.player().getYRot(), ctx.player().getXRot());
                final Rotation wanted = this.processor.peekRotation(this.target.rotation);
                if (ctx.player().isFallFlying()) {
                    // Elytra is a control loop: the flight path is solved assuming the angles it asked for
                    // last tick are the angles it has this tick. A lagging aim there is a crash, not a
                    // personality trait, so flying keeps the old straight-to-target behaviour.
                    this.aimRotation = wanted;
                } else {
                    // On a fresh target the hand is wherever the player is currently looking.
                    final Rotation from = this.aimRotation != null ? this.aimRotation : this.prevRotation;
                    if (this.aimRotation == null || this.isNewDirection(wanted)) {
                        // Either there is nothing to continue from, or the thing being aimed at has genuinely
                        // changed direction rather than drifted a little. Both are the start of a turn, and a
                        // turn spins up: without this, a reversal half way along a path - the turn other
                        // players are most likely to be watching - opened at the full speed ceiling, because
                        // the target is re-set every tick while pathing and the hand never looked fresh.
                        this.aimAge = 0;
                    }
                    if (standingStill() && this.reaction.holding(wanted)) {
                        // Notice, then move. Deliberately only while the player is not going anywhere:
                        // the movement code reads the facing to decide which keys to press, so letting the
                        // aim lag mid-walk would steer. Standing still, the only thing it costs is a beat
                        // before the turn, and the turn still gets its full spin-up afterwards.
                        this.aimRotation = from;
                        this.aimAge = 0;
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
        this.lastWanted = null;
        this.wanderCamera = null;
        this.idleCamera = null;
        this.idleGlance.reset();
        this.reaction.clear();
        this.smoothYawBuffer.clear();
        this.smoothPitchBuffer.clear();
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
                final boolean allowed = this.canGlanceSomewhere();
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
                // the player's own rotation, read straight from the player: ctx.playerRotations() returns
                // exactly this (LocalPlayer.getYRot/getXRot) and is what the movement code reads, so the
                // glance goes into the same variable the movement code will read. That is fine for a glance
                // - it only runs while the player is standing still, where nothing downstream reads the
                // facing - and it is why the walking wander, which does run while walking, does not go here.
                final Rotation camera = new Rotation(ctx.player().getYRot(), ctx.player().getXRot());
                final Rotation wanted = this.idleGlance.next(camera, allowed, this::idleStep, this::somethingNearby);
                if (!allowed) {
                    // Not ours this tick, and that includes the sway: a leftover half degree rides on the
                    // rotation Baritone traces its own pickaxe and blocks along, and an idle drift is not
                    // worth pointing the crosshair at the wrong block for. Standing still with nothing
                    // being clicked, nothing reads the angle at all, which is the whole reason this is safe.
                    return;
                }
                // The sway rides on top of whatever the glance is doing, including nothing: read against
                // the camera rather than the glance's own angle, it cannot be mistaken for the glance's
                // bookkeeping, which stays exactly where it was and so still arrives and still comes home.
                final Rotation base = wanted != null ? wanted : camera;
                final Rotation applied = new Rotation(
                        base.getYaw() + this.headWander.idleYaw(),
                        Rotation.clampPitch(base.getPitch() + this.headWander.idlePitch()));
                this.idleCamera = camera;
                ctx.player().setYRot(applied.getYaw());
                ctx.player().setXRot(applied.getPitch());
                if (this.idleFidget.take()) {
                    // A swing at nothing. It is sent as the animation packet and nothing else - no click goes
                    // out with it, so it cannot mine, place or break anything however the crosshair happens
                    // to be pointing, which is what makes it safe to do while Baritone is holding still on
                    // someone's behalf. The same two lines the block break helper sends for the arm that
                    // never moves while it mines.
                    ctx.player().swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
                    ctx.player().connection.send(ServerboundPunchPacket.INSTANCE);
                }
                break;
            }
            case POST: {
                if (this.idleCamera != null) {
                    ctx.player().setYRot(this.idleCamera.getYaw());
                    ctx.player().setXRot(this.idleCamera.getPitch());
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
            // in, every tick, and leave the head nailed to the bearing after all.
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
        if (!walking && !this.canGlanceSomewhere()) {
            // Mining, placing, or not ours to drive: fade the sway out rather than switching it off, so
            // that starting or stopping a walk does not step the head by a degree on one tick. The offsets
            // are taken back off every tick anyway, so this only ever smooths the two ends.
            this.headWander.recentre();
            return;
        }
        this.headWander.advance();
        final float fraction = walking ? 1.0f : HeadWander.IDLE_FRACTION;
        // Both angles are taken from the player and put back, exactly, for the same reason the glance does
        // it: an offset that is only taken off later accumulates. Applied to the yaw alone, the pitch would
        // be added again on top of itself every tick and walk the view up into the sky at six degrees a
        // second.
        this.wanderCamera = new Rotation(ctx.player().getYRot(), ctx.player().getXRot());
        ctx.player().setYRot(this.wanderCamera.getYaw() + this.headWander.yaw() * fraction);
        ctx.player().setXRot(Rotation.clampPitch(
                this.wanderCamera.getPitch() + this.headWander.pitch() * fraction));
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
            ctx.player().setYRot(this.wanderCamera.getYaw());
            ctx.player().setXRot(this.wanderCamera.getPitch());
            this.wanderCamera = null;
        }
    }

    /**
     * Whether the head is ours to move, for the walk.
     * <p>
     * Three conditions and all three are load bearing. The player has to be walking, because this is the
     * stride, and standing still is a different gate and a quieter sway. Baritone has to be the one driving,
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
                && this.isWalking()
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
        final double dx = ctx.player().getDeltaMovement().x;
        final double dz = ctx.player().getDeltaMovement().z;
        return dx * dx + dz * dz > IDLE_SPEED_SQ;
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
     * Baritone is, whether it is executing, calculating or waiting. If none is, the person is playing, and
     * there is no business animating their head.
     */
    private boolean canGlanceSomewhere() {
        return this.baritoneIsInControl() && standingStill() && !this.interacting();
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
     * Everything else - walking, looking about, the idle sway - keeps {@link AimDynamics#AIM_EASE}.
     */
    private Rotation humanStep(Rotation from, Rotation to) {
        final float ease = this.interacting() ? AimDynamics.CLICK_EASE : AimDynamics.AIM_EASE;
        return AimDynamics.humanStep(from, to, this.aimAge, sensitivity(ctx), ease);
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
    private Rotation idleStep(Rotation from, Rotation to) {
        return AimDynamics.step(from, to, AimDynamics.IDLE_YAW_STEP, AimDynamics.IDLE_PITCH_STEP,
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

        @Override
        public final Rotation peekRotation(final Rotation rotation) {
            final Rotation prev = this.getPrevRotation();

            float desiredYaw = rotation.getYaw();
            float desiredPitch = rotation.getPitch();

            // In other words, the target doesn't care about the pitch: it asked for the player's own, which
            // is the idiom for a target with an opinion about the yaw only, and that is how every walking
            // target is built. So the pitch is free, and it does what a person's does while they walk - it
            // settles a few degrees this way and that over the seconds rather than holding one angle for the
            // whole journey. See PitchWander.
            if (desiredPitch == prev.getPitch()) {
                desiredPitch = this.pitchWander.step(desiredPitch);
            }

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

            // randomLooking113
            double random = this.rand.nextDouble() - 0.5;
            if (Math.abs(random) < 0.1) {
                random *= 4;
            }
            final double rawYaw113 = rawYawOffset + random * Baritone.settings().randomLooking113.value;

            // Both of those draw fresh noise every single tick, and at 20Hz that is not a hand, it is a
            // shiver: uncorrelated jitter is something no person produces and the eye reads it as a
            // machine. Low pass them so the offset wanders the way an unsteady wrist does, and scale the
            // result back up so the settings keep the amplitude they always had.
            this.randomYawOffset = AimDynamics.smoothOffset(this.randomYawOffset, (float) rawYaw113);
            this.randomPitchOffset = AimDynamics.smoothOffset(this.randomPitchOffset, (float) rawPitchOffset);

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

        public final Rotation rotation;
        public final Mode mode;

        public Target(Rotation rotation, Mode mode) {
            this.rotation = rotation;
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
