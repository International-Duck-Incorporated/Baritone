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

import baritone.api.utils.Rotation;

/**
 * The maths behind the human-like aim.
 * <p>
 * A rotation used to be applied in full in the tick its target changed, so a path reversal or a freshly
 * chosen block face teleported the crosshair. These are the rules that replaced that: move a fraction of
 * the way each tick, never faster than a hand can, quantized to whole mouse pixels so the aim settles
 * exactly rather than hunting around the target, and spin up before peaking.
 * <p>
 * Deliberately free of every Minecraft type beyond {@link Rotation} and of any mutable state: this is the
 * part worth being able to reason about and test, so it lives apart from the behaviour that feeds it.
 * {@code AimDynamicsTest} covers it.
 */
public final class AimDynamics {

    private AimDynamics() {
    }

    /**
     * Largest yaw change per tick, in degrees. 18 deg/tick is 360 deg/s, the peak speed of a deliberate
     * human flick; ordinary corrections never get near it because of {@link #AIM_EASE}.
     */
    public static final float MAX_YAW_STEP = 18.0f;

    /**
     * Pitch is a slower joint than yaw and drags behind it, so it gets a lower ceiling.
     */
    public static final float MAX_PITCH_STEP = 9.0f;

    /**
     * Largest yaw change per tick for the idle glance, in degrees: half the flick ceiling, 180 deg/s.
     * <p>
     * The flick ceiling is a speed for <em>aiming</em> - the hand is being told where to go and it goes there
     * - and the idle glance is not aiming at anything. Driving the glance at the full budget made an idle bot
     * twitch its head aside at up to 11.7 degrees in one tick, measured over twelve hundred ticks of a paused
     * bot, which is 585 deg/s: a speed for someone flicking a mouse across a desk, and far quicker than a
     * person turns their head to look at something while they wait. At this ceiling a full 28 degree glance
     * takes eight ticks, four tenths of a second, and peaks at 180 deg/s, which is what an unhurried look
     * around actually costs.
     */
    public static final float IDLE_YAW_STEP = 9.0f;

    /**
     * The same for the glance's pitch: a glance is mostly a yaw movement.
     */
    public static final float IDLE_PITCH_STEP = 4.5f;

    /**
     * Fraction of the remaining angle covered per tick. A real movement covers most of the distance early
     * and then settles, rather than travelling at a constant speed and stopping dead.
     */
    public static final float AIM_EASE = 0.5f;

    /**
     * The same fraction for a turn that ends in a click, which is the whole way.
     * <p>
     * Aiming at something to click on is not the same job as walking. Everything that breaks or places a
     * block waits for the crosshair to be on it first, because the trace the game actually acts along is
     * taken from wherever the player is really looking - so a slow hand there is not a safer hand, it is a
     * bot standing in front of a block it has already decided about, waiting for its own crosshair. Somebody
     * about to click does not drift toward the block either: they put the crosshair on it, quickly and
     * deliberately. The ceiling is untouched, so this is a hurry and not a snap - a right angle still takes
     * five ticks of a hand at its fastest.
     */
    public static final float CLICK_EASE = 1.0f;

    /**
     * Fraction of full speed available on the first, second, third... tick of a turn.
     * <p>
     * A hand does not go from still to its top speed in a single tick; the muscles spin up. Without this a
     * 180 degree turn opened at the full 18 deg/tick immediately and only ever slowed down from there,
     * which is a velocity profile nothing with a wrist produces. Holding the first tick to half speed and
     * the second to most of it is enough to read as a real movement: accelerate, peak, ease off.
     */
    public static final float[] TURN_RAMP = {0.5f, 0.8f, 1.0f};

    /**
     * How long a hand carries past what it is walking toward, in ticks.
     * <p>
     * The carry is not a step that is taken and then undone: what the hand aims at starts a little past
     * the target and walks back onto it over this many ticks, which is what correcting looks like.
     */
    public static final int OVERSHOOT_TICKS = 16;

    /**
     * How much of the turn the hand carries past the target, before the cap: eight percent, which is the
     * size of the correction a person makes after reaching for something - the hand does not stop where it
     * meant to, it goes a little past and comes back.
     */
    public static final float OVERSHOOT_FRACTION = 0.08f;

    /**
     * The most the hand carries past the target, in degrees. The fraction alone would put a half turn of
     * the same few percent out at fourteen degrees, which is not a correction, it is a miss.
     */
    public static final float OVERSHOOT_MAX = 6.0f;

    /**
     * Turns smaller than this are not carried past at all. Nobody overshoots a movement they were almost
     * already making, and the aim makes plenty of those: a degree of tremor, a step of the wander, the
     * drift of a target as the player walks toward it.
     */
    public static final float OVERSHOOT_MIN_TURN = 15.0f;

    /**
     * Low pass applied to the random-looking offsets, so they wander like an unsteady wrist instead of
     * being resampled from scratch twenty times a second.
     */
    public static final float TREMOR_SMOOTHING = 0.3f;

    /**
     * Puts back the amplitude that {@link #TREMOR_SMOOTHING} costs, so {@code randomLooking} and
     * {@code randomLooking113} keep meaning what they always meant.
     */
    public static final float TREMOR_GAIN = 2.4f;

    /**
     * Converts whole mouse pixels to degrees, using the same sensitivity maths the game does.
     *
     * @param sensitivity the player's mouse sensitivity setting, 0 to 1
     * @param mouseDelta  mouse movement in pixels
     * @return the angle that movement turns the view, in degrees
     */
    public static float mouseToAngle(double sensitivity, double mouseDelta) {
        // casting float literals to double gets us the precise values used by mc
        final double f = sensitivity * (double) 0.6f + (double) 0.2f;
        return (float) (mouseDelta * f * f * f * 8.0d) * 0.15f; // yes, one double and one float scaling factor
    }

    /**
     * The fraction of full speed available this many ticks into a turn.
     */
    public static float ramp(int aimAge) {
        return TURN_RAMP[Math.min(Math.max(aimAge, 0), TURN_RAMP.length - 1)];
    }

    /**
     * One tick of movement from {@code from} toward {@code to} at the given speed, quantized to whole
     * mouse pixels.
     * <p>
     * The quantization is what makes the aim settle: a fractional step would approach the target forever
     * without ever reaching it, so the last sub-pixel remainder is taken in full.
     */
    public static Rotation step(Rotation from, Rotation to, float maxYaw, float maxPitch, double sensitivity) {
        return step(from, to, maxYaw, maxPitch, sensitivity, AIM_EASE);
    }

    /**
     * The same, with the fraction of the remaining angle as a parameter.
     *
     * @param ease how much of what is left to cover this tick, before the ceiling is applied
     */
    public static Rotation step(Rotation from, Rotation to, float maxYaw, float maxPitch, double sensitivity, float ease) {
        return new Rotation(
                steppedAngle(from.getYaw(), to.getYaw(), maxYaw, sensitivity, ease),
                steppedAngle(from.getPitch(), to.getPitch(), maxPitch, sensitivity, ease)
        ).clamp();
    }

    /**
     * One tick of movement for a turn that is {@code aimAge} ticks old, so it spins up first.
     */
    public static Rotation humanStep(Rotation from, Rotation to, int aimAge, double sensitivity) {
        return humanStep(from, to, aimAge, sensitivity, AIM_EASE);
    }

    /**
     * The same, at a given rate. See {@link #AIM_EASE} and {@link #CLICK_EASE} for the two that are used.
     */
    public static Rotation humanStep(Rotation from, Rotation to, int aimAge, double sensitivity, float ease) {
        final float ramp = ramp(aimAge);
        return step(from, to, MAX_YAW_STEP * ramp, MAX_PITCH_STEP * ramp, sensitivity, ease);
    }

    /**
     * What the hand is aimed at while a turn is young: the target, with the little extra a real hand carries
     * past it.
     * <p>
     * Easing alone is a hand that gets slower the closer it gets and stops dead on arrival, and that is not
     * what arriving looks like. A person's arm is still moving when it reaches what it was going for, so it
     * goes a little past, notices, and comes back. This returns the angle to aim at so the stepper draws
     * that: past the target at the start of a turn, walking back onto it as the turn ages, and exactly on
     * the target once the carry has run out. What is aimed at, not what is held - the hand still never
     * passes the thing it is aimed at, which is what {@link #humanStep} promises and its own test asserts.
     * <p>
     * Simulated against the stepper at the in-game default sensitivity and measured on the hand, not on the
     * aim: a quarter turn peaks 2.4 degrees past its target nine ticks in and is sitting exactly on it ten
     * ticks after that; a twenty degree turn carries one degree past at tick six and lands ten ticks later;
     * a turn under {@link #OVERSHOOT_MIN_TURN} is not carried at all; and half a turn never gets the chance,
     * because the flick ceiling gets the hand there after the carry has run out - which is the cap earning
     * its keep, since a hand cannot be carried past a place it has not reached yet.
     *
     * @param turnFrom where the hand was when the turn began, which is what the carry is measured from;
     *                 {@code null} when there is no turn to measure
     * @param to       the rotation that was asked for
     * @param aimAge   how many ticks the turn has been going
     * @return the rotation to aim at this tick
     */
    public static Rotation overshoot(Rotation turnFrom, Rotation to, int aimAge) {
        if (turnFrom == null) {
            return to;
        }
        return new Rotation(
                to.getYaw() + carriedPast(Rotation.normalizeYaw(to.getYaw() - turnFrom.getYaw()), aimAge),
                to.getPitch() + carriedPast(to.getPitch() - turnFrom.getPitch(), aimAge)
        ).clamp();
    }

    /**
     * One axis of the carry: how far past the target to aim, for a turn that started {@code delta} degrees
     * away and is {@code aimAge} ticks old.
     */
    private static float carriedPast(float delta, int aimAge) {
        final float decay = 1.0f - aimAge / (float) OVERSHOOT_TICKS;
        if (decay <= 0.0f || Math.abs(delta) < OVERSHOOT_MIN_TURN) {
            return 0.0f;
        }
        return Math.copySign(Math.min(Math.abs(delta) * OVERSHOOT_FRACTION, OVERSHOOT_MAX), delta) * decay;
    }

    /**
     * A single angle's worth of {@link #step}: eases toward the target, never further than {@code maxStep},
     * and takes the last sub-pixel remainder in full so it can actually arrive.
     * <p>
     * The pixel snap can land up to half a pixel past the ceiling - a cap of 18 degrees against a pixel of
     * 0.45 rounds up to 18.07 - so the snapped step is held to the ceiling afterwards. The ceiling is what
     * says a hand cannot be quicker than this, and it is the number the tests assert, so it is the number
     * that has to hold; half a pixel either way is otherwise invisible and the ordinary, uncapped step is
     * still exactly on the pixel grid.
     */
    public static float steppedAngle(float current, float target, float maxStep, double sensitivity) {
        return steppedAngle(current, target, maxStep, sensitivity, AIM_EASE);
    }

    /**
     * The same, with the fraction of what is left to cover as a parameter.
     */
    public static float steppedAngle(float current, float target, float maxStep, double sensitivity, float ease) {
        // the delta has to be wrapped, or a turn across the 180 degree boundary goes the long way round
        final float delta = Rotation.normalizeYaw(target - current);
        final float minStep = Math.abs(mouseToAngle(sensitivity, 1));
        if (minStep <= 0.0f || Math.abs(delta) <= minStep) {
            // less than one pixel of mouse movement left: land it, don't creep toward it forever
            return Rotation.normalizeYaw(target);
        }
        final float step = Math.max(-maxStep, Math.min(maxStep, delta * ease));
        final float snapped = Math.round(step / minStep) * minStep;
        return Rotation.normalizeYaw(current + Math.max(-maxStep, Math.min(maxStep, snapped)));
    }

    /**
     * How far apart two rotations are, in degrees: the short way around on yaw, and the larger of the two
     * axes, so a movement counts as much as a turn.
     * <p>
     * This is how "is this still the same thing I was aiming at, or has it genuinely changed?" is answered,
     * both for the spin-up and for the reaction pause.
     */
    /**
     * How far the hand may be from what it is aiming at and still count as having arrived, in degrees.
     * <p>
     * Not a precision, a scale. The hand never lands exactly on its target: it eases toward it and it is
     * shaken by the tremor while it does, so once a turn is over the aim holds a fraction of a degree off
     * the angle it was asked for - measured over twenty thousand settled ticks, 0.20 degrees on average and
     * 0.75 at worst with the aim easing, 0.03 and 0.08 when a click is being aimed and the hand is
     * hurrying. The tremor is the whole of it: with {@code randomLooking113} at its default the shaken aim
     * sits 0.42 degrees from its target on average, and inside a hundredth of a degree on 1.4 percent of
     * ticks - which is the point. Every gate that asked the angles to coincide to a hundredth of a degree
     * (see {@link Rotation#isReallyCloseTo}) used to be answered by luck, one tick in seventy, because that
     * test was written when the aim was set straight to its target. This is the question those gates meant:
     * is the hand done travelling, or is it still on its way.
     */
    public static final float ARRIVED_ANGLE = 2.0f;

    /**
     * Whether a hand that has arrived at {@code aim} has finished travelling to {@code wanted}.
     * <p>
     * The two arguments are one rotation in two parts, the same way the steering pair is: {@code wanted} is
     * what the hand is being asked for this tick, tremor, nudge and all, and {@code aim} is what it is
     * holding. A settled hand is within a fraction of a degree of the second; a hand in the middle of a turn
     * is a whole step or more away from it.
     */
    public static boolean arrived(Rotation aim, Rotation wanted) {
        return separation(aim, wanted) <= ARRIVED_ANGLE;
    }

    public static float separation(Rotation a, Rotation b) {
        return Math.max(
                Math.abs(Rotation.normalizeYaw(a.getYaw() - b.getYaw())),
                Math.abs(a.getPitch() - b.getPitch())
        );
    }

    /**
     * One axis of the wander the aim adds so it is never perfectly still: a uniform draw in {@code [0, 1)}
     * turned into an offset in degrees.
     * <p>
     * Centred and flat, with one exception, which is the whole character of it: a draw inside a tenth of the
     * centre is thrown four times as far. Left out, a hand asked for two degrees of wander spends a fifth of
     * its time inside a couple of tenths of a degree of dead centre, and resting at the centre is what a hand
     * on a table does, not a hand on a mouse. The rule is not new - it is the shape the setting's own draw
     * has always had - but it lived inline at one call site, so the yaw was drawn with it and the pitch could
     * not be.
     * <p>
     * Both axes are drawn with this now, which is the only way they can be the same size. The two are
     * independent draws: a hand that wandered along a line would be a hand that is being moved, and the
     * diagonal is a shape nobody's wrist produces.
     *
     * @param uniform   a fresh draw, uniformly distributed over {@code [0, 1)}
     * @param amplitude the setting that sizes it: the wander spans this many degrees either side of centre
     * @return the offset to add to this axis, in degrees
     */
    public static double tremor(double uniform, double amplitude) {
        double offset = uniform - 0.5;
        if (Math.abs(offset) < 0.1) {
            offset *= 4.0;
        }
        return offset * amplitude;
    }

    /**
     * One tick of the wandering offsets the aim adds so it is never perfectly still: a low pass toward the
     * freshly drawn noise, so the offset drifts instead of shivering.
     * <p>
     * The gain is deliberately not applied here. It belongs where the offset is used, so that what is
     * smoothed is the plain noise and the amplitude stays a property of the setting that asked for it.
     * <p>
     * Takes and returns a double to match the offsets the aim processor accumulates, so the low pass runs
     * at the same precision it always has rather than being rounded through a float every tick.
     */
    public static double smoothOffset(double current, double freshlyDrawn) {
        return current + (freshlyDrawn - current) * TREMOR_SMOOTHING;
    }
}
