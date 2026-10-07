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

package baritone.api.utils;

/**
 * @author Brady
 * @since 9/25/2018
 */
public class Rotation {

    /**
     * The yaw angle of this Rotation
     */
    private final float yaw;

    /**
     * The pitch angle of this Rotation
     */
    private final float pitch;

    public Rotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
        if (Float.isInfinite(yaw) || Float.isNaN(yaw) || Float.isInfinite(pitch) || Float.isNaN(pitch)) {
            throw new IllegalStateException(yaw + " " + pitch);
        }
    }

    /**
     * @return The yaw of this rotation
     */
    public float getYaw() {
        return this.yaw;
    }

    /**
     * @return The pitch of this rotation
     */
    public float getPitch() {
        return this.pitch;
    }

    /**
     * Adds the yaw/pitch of the specified rotations to this
     * rotation's yaw/pitch, and returns the result.
     *
     * @param other Another rotation
     * @return The result from adding the other rotation to this rotation
     */
    public Rotation add(Rotation other) {
        return new Rotation(
                this.yaw + other.yaw,
                this.pitch + other.pitch
        );
    }

    /**
     * Subtracts the yaw/pitch of the specified rotations from this
     * rotation's yaw/pitch, and returns the result.
     *
     * @param other Another rotation
     * @return The result from subtracting the other rotation from this rotation
     */
    public Rotation subtract(Rotation other) {
        return new Rotation(
                this.yaw - other.yaw,
                this.pitch - other.pitch
        );
    }

    /**
     * @return A copy of this rotation with the pitch clamped
     */
    public Rotation clamp() {
        return new Rotation(
                this.yaw,
                clampPitch(this.pitch)
        );
    }

    /**
     * @return A copy of this rotation with the yaw normalized
     */
    public Rotation normalize() {
        return new Rotation(
                normalizeYaw(this.yaw),
                this.pitch
        );
    }

    /**
     * @return A copy of this rotation with the pitch clamped and the yaw normalized
     */
    public Rotation normalizeAndClamp() {
        return new Rotation(
                normalizeYaw(this.yaw),
                clampPitch(this.pitch)
        );
    }

    public Rotation withPitch(float pitch) {
        return new Rotation(this.yaw, pitch);
    }

    /**
     * Whether the two rotations agree to a hundredth of a degree.
     *
     * @param other another rotation
     * @return are they really close
     * @deprecated A hand that eases toward where it is going, and is shaken by the tremor while it does,
     * sits a fraction of a degree off the angle it was asked for - two fifths of a degree on average, and
     * inside a hundredth of a degree on about one tick in seventy. A question this exact is therefore
     * answered by luck, which is what every gate that asked it "is the aim here yet" was waiting for. Ask
     * the aim instead: {@link baritone.api.behavior.ILookBehavior#hasArrivedAt(Rotation)} where there is a
     * behaviour to hand, or {@code AimDynamics.arrived} for a pair of rotations in hand. Kept, and working
     * exactly as it did, for the API's sake.
     */
    @Deprecated
    public boolean isReallyCloseTo(Rotation other) {
        return yawIsReallyClose(other) && Math.abs(this.pitch - other.pitch) < 0.01;
    }

    /**
     * Whether the two yaws agree to a hundredth of a degree.
     * <p>
     * This is a wraparound guard rather than a question about a hand: {@link
     * RotationUtils#wrapAnglesToRelative(Rotation, Rotation)} asks it so that a yaw which is already right
     * is kept rather than re-derived from the same geometry, which is what stops floating point noise
     * deciding the sign of the one-eighty boundary afresh every tick. "Has the aim arrived" is a different
     * question, with a different answer: {@link baritone.api.behavior.ILookBehavior#hasArrivedAt(Rotation)}.
     */
    public boolean yawIsReallyClose(Rotation other) {
        float yawDiff = Math.abs(normalizeYaw(yaw) - normalizeYaw(other.yaw)); // you cant fool me
        return (yawDiff < 0.01 || yawDiff > 359.99);
    }

    /**
     * Clamps the specified pitch value between -90 and 90.
     *
     * @param pitch The input pitch
     * @return The clamped pitch
     */
    public static float clampPitch(float pitch) {
        return Math.max(-90, Math.min(90, pitch));
    }

    /**
     * Normalizes the specified yaw value between -180 and 180.
     *
     * @param yaw The input yaw
     * @return The normalized yaw
     */
    public static float normalizeYaw(float yaw) {
        float newYaw = yaw % 360F;
        if (newYaw < -180F) {
            newYaw += 360F;
        }
        if (newYaw > 180F) {
            newYaw -= 360F;
        }
        return newYaw;
    }

    /**
     * The signed distance between two yaw angles, taken the short way round: positive when {@code yaw} is
     * clockwise of {@code offsetYaw}, negative the other way, and never further from zero than half a turn.
     *
     * @param yaw The initial yaw
     * @param offsetYaw The offset yaw
     * @return The distance between the yaws
     */
    public static float yawDistanceFromOffset(float yaw, float offsetYaw) {
        return normalizeYaw(yaw - offsetYaw);
    }

    @Override
    public String toString() {
        return "Yaw: " + yaw + ", Pitch: " + pitch;
    }
}
