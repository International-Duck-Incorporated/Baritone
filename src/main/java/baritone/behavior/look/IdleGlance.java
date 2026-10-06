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

import java.util.Random;

/**
 * Decides when to look around, where at, and when to come back.
 * <p>
 * Deliberately free of any Minecraft type beyond {@link Rotation}: the rules are the interesting part
 * and they are worth being able to test on their own. Everything it does is bounded, and every path
 * out of it ends with the aim back where it started, so a glance can never leave the head stuck
 * pointing at a wall.
 */
public final class IdleGlance {

    /**
     * One tick of hand movement, from where the aim is to where it is going.
     */
    @FunctionalInterface
    public interface Stepper {

        Rotation step(Rotation from, Rotation to);
    }


    /**
     * Ticks to wait between glances: three to eight seconds. Long enough that a bot standing at a
     * closed door does not look like it is scanning for something.
     */
    private static final int COOLDOWN_MIN = 60;
    private static final int COOLDOWN_MAX = 160;

    /**
     * How long a glance is held once it has arrived: 0.4 to 1.5 seconds.
     */
    private static final int HOLD_MIN = 8;
    private static final int HOLD_MAX = 30;

    /**
     * How far a glance may wander from where the player was already looking: a glance, not a survey.
     */
    private static final float MAX_YAW = 28.0f;
    private static final float MAX_PITCH = 7.0f;

    /**
     * How far the view may move away from where a glance started before the glance gives up. Movement
     * here means somebody moved the mouse themselves, and the polite thing is to get out of the way.
     */
    private static final float TAKEN_OVER = 20.0f;

    /**
     * Safety net: a glance may never outlive this many ticks, however confused the state gets.
     */
    private static final int MAX_AGE = 100;

    private final Random rand = new Random();

    private int cooldown = COOLDOWN_MIN;
    private int hold;
    private int age;
    private Rotation aim;    // where the glance is pointed, null when none is running
    private Rotation home;   // where it started, and where it goes back to
    private Rotation sent;   // what the server was last told

    /**
     * @param camera where the player is actually looking right now
     * @param allowed whether an idle glance is appropriate this tick
     * @param step one tick of hand movement
     * @return the rotation to send to the server, or null when this tick is not ours to take
     */
    public Rotation next(Rotation camera, boolean allowed, Stepper step) {
        if (!allowed) {
            if (this.aim != null) {
                // Baritone has started doing something, or the player is moving again. Drop the
                // glance where it stands; whatever happens next owns the aim from here.
                this.reset();
            }
            return null;
        }
        if (this.aim == null) {
            if (--this.cooldown > 0) {
                return null;
            }
            // Whatever the glance has just been asked to look at, it is off the direction the player is
            // travelling. Anything waiting for the aim to arrive at a movement target gates on the aim
            // being there, so standing idle is the only place this belongs: at the one speed where the
            // traversal happens to be idle and a nudge is under way, a glance must not delay it.
            this.home = camera;
            this.aim = new Rotation(
                    Rotation.normalizeYaw(camera.getYaw() + (this.rand.nextFloat() * 2.0f - 1.0f) * MAX_YAW),
                    Rotation.clampPitch(camera.getPitch() + (this.rand.nextFloat() * 2.0f - 1.0f) * MAX_PITCH)
            );
            this.hold = HOLD_MIN + this.rand.nextInt(HOLD_MAX - HOLD_MIN + 1);
            this.age = 0;
            this.sent = camera;
        } else if (Math.abs(Rotation.normalizeYaw(camera.getYaw() - this.home.getYaw())) > TAKEN_OVER
                || Math.abs(camera.getPitch() - this.home.getPitch()) > TAKEN_OVER) {
            this.reset();
            return null;
        }

        this.age++;
        if (this.age > MAX_AGE) {
            this.reset();
            return null;
        }

        final Rotation wanted = step.step(this.sent, this.aim);
        if (this.reached(wanted)) {
            if (this.hold > 0) {
                this.hold--;
            } else if (this.aim != this.home) {
                this.aim = this.home;   // nothing else to see here, head back
            } else {
                // Home again. Hand back the exact rotation we started from one last time, so the
                // server is not left holding a fraction of a degree of leftover offset, and only
                // then go quiet for a while: this should read as somebody glancing up from what
                // they were doing, not as a surveillance camera sweeping.
                final Rotation home = this.home;
                this.reset();
                return home;
            }
        }
        this.sent = wanted;
        return wanted;
    }

    private boolean reached(Rotation rotation) {
        return Rotation.normalizeYaw(this.aim.getYaw() - rotation.getYaw()) == 0.0f
                && Math.abs(this.aim.getPitch() - rotation.getPitch()) < 1e-4f;
    }

    private void reset() {
        this.aim = null;
        this.home = null;
        this.sent = null;
        this.hold = 0;
        this.age = 0;
        this.cooldown = COOLDOWN_MIN + this.rand.nextInt(COOLDOWN_MAX - COOLDOWN_MIN + 1);
    }
}
