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
import baritone.behavior.look.AimDynamics;
import baritone.behavior.look.ForkableRandom;
import baritone.behavior.look.IdleGlance;
import baritone.behavior.look.ReactionDelay;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

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

    private final IdleGlance idleGlance = new IdleGlance();

    /**
     * The beat before the hand starts moving toward a newly chosen target. Only ever allowed to hold while
     * the player is standing still; see {@link #standingStill()}.
     */
    private final ReactionDelay reaction = new ReactionDelay();

    /**
     * Where the camera was before an idle glance took the angles for this tick, so it can be handed
     * back before the frame is drawn. See {@link #updateIdleLook}.
     */
    private Rotation glanceCamera;

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
    public void onTick(TickEvent event) {
        if (event.getType() == TickEvent.Type.IN) {
            this.processor.tick();
        }
    }

    @Override
    public void onPlayerUpdate(PlayerUpdateEvent event) {

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
                    this.smoothYawBuffer.addLast(this.target.rotation.getYaw());
                    while (this.smoothYawBuffer.size() > Baritone.settings().smoothLookTicks.value) {
                        this.smoothYawBuffer.removeFirst();
                    }
                    this.smoothPitchBuffer.addLast(this.target.rotation.getPitch());
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
     */
    private void updateIdleLook(EventState state) {
        switch (state) {
            case PRE: {
                // the raw player rotation, not ctx.playerRotations(): under freeLook that returns the
                // server-side view, and what this needs to know about is the camera
                final Rotation camera = new Rotation(ctx.player().getYRot(), ctx.player().getXRot());
                final Rotation wanted = this.idleGlance.next(camera, this.canGlanceSomewhere(), this::idleStep);
                if (wanted == null) {
                    return;
                }
                this.glanceCamera = camera;
                ctx.player().setYRot(wanted.getYaw());
                ctx.player().setXRot(wanted.getPitch());
                break;
            }
            case POST: {
                if (this.glanceCamera != null) {
                    ctx.player().setYRot(this.glanceCamera.getYaw());
                    ctx.player().setXRot(this.glanceCamera.getPitch());
                    this.glanceCamera = null;
                }
                break;
            }
            default:
                break;
        }
    }

    /**
     * Whether an idle glance is appropriate right now.
     * <p>
     * Every one of these is a reason to keep still instead. In particular this requires Baritone to be
     * executing a path: if it is not driving, then the person is, and it has no business moving their
     * head about.
     */
    private boolean canGlanceSomewhere() {
        return baritone.getPathingBehavior().isPathing() && standingStill();
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
     */
    private Rotation humanStep(Rotation from, Rotation to) {
        return AimDynamics.humanStep(from, to, this.aimAge, sensitivity(ctx));
    }

    /**
     * One tick of movement with the full speed budget. The idle glance uses this: it is a small
     * absent-minded movement already and does not need a spin-up of its own.
     */
    private Rotation idleStep(Rotation from, Rotation to) {
        // the full budget, no ramp: a glance is a small absent-minded movement, not a turn
        return AimDynamics.step(from, to, AimDynamics.MAX_YAW_STEP, AimDynamics.MAX_PITCH_STEP, sensitivity(ctx));
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
        private double randomYawOffset;
        private double randomPitchOffset;

        public AbstractAimProcessor(IPlayerContext ctx) {
            this.ctx = ctx;
            this.rand = new ForkableRandom();
        }

        private AbstractAimProcessor(final AbstractAimProcessor source) {
            this.ctx = source.ctx;
            this.rand = source.rand.fork();
            this.randomYawOffset = source.randomYawOffset;
            this.randomPitchOffset = source.randomPitchOffset;
        }

        @Override
        public final Rotation peekRotation(final Rotation rotation) {
            final Rotation prev = this.getPrevRotation();

            float desiredYaw = rotation.getYaw();
            float desiredPitch = rotation.getPitch();

            // In other words, the target doesn't care about the pitch, so it used playerRotations().getPitch()
            // and it's safe to adjust it to a normal level
            if (desiredPitch == prev.getPitch()) {
                desiredPitch = nudgeToLevel(desiredPitch);
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

        /**
         * Nudges the player's pitch to a regular level. (Between {@code -20} and {@code 10}, increments are by {@code 1})
         */
        private float nudgeToLevel(float pitch) {
            if (pitch < -20) {
                return pitch + 1;
            } else if (pitch > 10) {
                return pitch - 1;
            }
            return pitch;
        }

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
