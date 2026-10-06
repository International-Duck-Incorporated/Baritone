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
     * Fraction of the remaining angle covered per tick. A real movement covers most of the distance early
     * and then settles, rather than travelling at a constant speed and stopping dead.
     */
    public static final float AIM_EASE = 0.5f;

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
        return new Rotation(
                steppedAngle(from.getYaw(), to.getYaw(), maxYaw, sensitivity),
                steppedAngle(from.getPitch(), to.getPitch(), maxPitch, sensitivity)
        ).clamp();
    }

    /**
     * One tick of movement for a turn that is {@code aimAge} ticks old, so it spins up first.
     */
    public static Rotation humanStep(Rotation from, Rotation to, int aimAge, double sensitivity) {
        final float ramp = ramp(aimAge);
        return step(from, to, MAX_YAW_STEP * ramp, MAX_PITCH_STEP * ramp, sensitivity);
    }

    /**
     * A single angle's worth of {@link #step}: eases toward the target, never further than {@code maxStep},
     * and takes the last sub-pixel remainder in full so it can actually arrive.
     */
    public static float steppedAngle(float current, float target, float maxStep, double sensitivity) {
        // the delta has to be wrapped, or a turn across the 180 degree boundary goes the long way round
        final float delta = Rotation.normalizeYaw(target - current);
        final float minStep = Math.abs(mouseToAngle(sensitivity, 1));
        if (minStep <= 0.0f || Math.abs(delta) <= minStep) {
            // less than one pixel of mouse movement left: land it, don't creep toward it forever
            return Rotation.normalizeYaw(target);
        }
        final float step = Math.max(-maxStep, Math.min(maxStep, delta * AIM_EASE));
        final double pixels = Math.round(step / minStep);
        return Rotation.normalizeYaw(current + (float) (pixels * minStep));
    }

    /**
     * How far apart two rotations are, in degrees: the short way around on yaw, and the larger of the two
     * axes, so a movement counts as much as a turn.
     * <p>
     * This is how "is this still the same thing I was aiming at, or has it genuinely changed?" is answered,
     * both for the spin-up and for the reaction pause.
     */
    public static float separation(Rotation a, Rotation b) {
        return Math.max(
                Math.abs(Rotation.normalizeYaw(a.getYaw() - b.getYaw())),
                Math.abs(a.getPitch() - b.getPitch())
        );
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
