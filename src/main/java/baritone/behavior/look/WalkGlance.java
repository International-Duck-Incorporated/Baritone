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
import java.util.function.Supplier;

/**
 * The look to the side a person takes every so often on a long walk.
 * <p>
 * Nobody walks a road with their eyes nailed to the end of it. Every several seconds the head turns a
 * little way - at the scenery, at a mob in a field, at nothing - holds there for half a second or a
 * second, and comes back, while the feet carry on exactly as they were. A bot that walks for a minute
 * without once doing that is reading as a bot the whole minute, however well it turned the last corner.
 * {@link IdleGlance} is the same habit for a body that is standing still; this is the walking one, and the
 * two differ in the one way that matters.
 * <p>
 * <b>Why it is an offset and not an angle.</b> The idle glance picks a place to look and goes there,
 * which is right for a head whose base is not moving. A walking head's base is: the aim turns at corners
 * and settles its pitch while the glance is out, and a glance that held an absolute angle through all of
 * that would freeze the head while the body turned and then jump to wherever the aim had got to when it
 * ended. So this is a yaw and a pitch to add to whatever the aim is doing, eased out along a smooth curve,
 * held, and eased back to exactly nothing; on a bend the glance rides round the bend with the head under
 * it. The head wander's degree of sway is built the same way, for the same reason.
 * <p>
 * <b>What it is never allowed to do</b> is be on the head while the head is needed. The rotation this
 * rides on is the one Baritone traces its own pickaxe and blocks along, and a click aimed with the eyes
 * forty degrees to the left lands on the wrong block. The caller says each tick whether a glance is
 * welcome - a plain walk along the flat, nothing to break or place, nothing being clicked - and the
 * moment it is not, the offset is brought back to nothing at {@link #RECOVER_STEP} a tick, which is the
 * speed of a glance and not of a flinch, and no new one starts until the walk has been plain again for a
 * whole interval. A walk that is all jumps and corners therefore never glances at all, which is what a
 * person on broken ground does too: they watch their feet.
 * <p>
 * The whole of it is free of Minecraft, and {@code WalkGlanceTest} drives it: never further than the
 * bounds, never faster than the step, back to exactly nothing at the end, and nothing at all when it is
 * never allowed.
 */
public final class WalkGlance {

    /**
     * The furthest a glance to nothing in particular turns the head, in degrees. Forty is a look at the
     * side of the road; it is not a look over the shoulder, which a person does not do while walking
     * forward at speed.
     */
    public static final float MAX_YAW = 40.0f;

    /**
     * The smallest glance worth taking: anything under this reads as the sway, not as a look.
     */
    private static final float MIN_YAW = 12.0f;

    /**
     * How far a glance to nothing in particular tilts the head, at most. Side glances are level; the eyes
     * go to the horizon, not the ground.
     */
    public static final float MAX_PITCH = 6.0f;

    /**
     * How far a glance at something will turn and tilt the head. Further than a glance at nothing, because
     * it has somewhere to go, and still within the quarter turn that keeps the road in the corner of the
     * eye.
     */
    public static final float MAX_INTEREST_YAW = 70.0f;
    public static final float MAX_INTEREST_PITCH = 30.0f;

    /**
     * The most the offset is allowed to change in one tick, in degrees of yaw and of pitch. These are
     * {@link AimDynamics#IDLE_YAW_STEP} and {@link AimDynamics#IDLE_PITCH_STEP}, the speed of a head that
     * is looking rather than aiming, and the eased curve stays under them by construction: the curve's
     * steepest point is one and a half times its average, so a glance is given at least as many ticks as
     * that takes on the slower axis.
     */
    public static final float MAX_STEP = AimDynamics.IDLE_YAW_STEP;
    public static final float MAX_PITCH_STEP = AimDynamics.IDLE_PITCH_STEP;

    /**
     * How fast the head comes back when the walk stops being plain while a glance is out: the full looking
     * speed rather than the eased curve, because something has come up; still a turn of the head and not a
     * snap. The pitch comes back at its own step, and the two land together.
     */
    public static final float RECOVER_STEP = MAX_STEP;

    /**
     * The time between glances, in ticks of plain walking: five to fifteen seconds, drawn afresh after
     * each one so there is no rhythm to it.
     */
    public static final int COOLDOWN_MIN = 100;
    public static final int COOLDOWN_MAX = 300;

    /**
     * How long the head takes to get out to the glance, to hold there, and to come back, in ticks. Out and
     * back are a little under half a second; the hold is anything from a flick to over a second.
     */
    private static final int OUT_MIN = 8;
    private static final int OUT_MAX = 12;
    private static final int HOLD_MIN = 4;
    private static final int HOLD_MAX = 24;
    private static final int BACK_MIN = 8;
    private static final int BACK_MAX = 14;

    /**
     * How often a glance goes to something that is actually there, when there is something, rather than to
     * nothing in particular. Most glances are at nothing.
     */
    private static final float INTEREST_CHANCE = 0.6f;

    private enum Phase {NONE, OUT, HOLD, BACK}

    private final Random rand;

    private int cooldown;
    private Phase phase = Phase.NONE;
    private int length;      // how many ticks the current phase takes
    private int elapsed;     // how many of them have gone
    private float yawAmplitude;
    private float pitchAmplitude;
    private float yaw;
    private float pitch;
    private boolean welcome; // offered this tick

    public WalkGlance() {
        this(new Random());
    }

    /**
     * Takes the source of the intervals and the directions, for the tests: see {@link HeadWander#HeadWander(Random)}
     * for why a clock-seeded one cannot be tested.
     */
    public WalkGlance(Random rand) {
        this.rand = rand;
        this.cooldown = drawCooldown();
    }

    /**
     * Says a glance would be welcome this tick: the walk under way is a plain one along the flat, with
     * nothing to break, place or click, and nothing that needs the eyes coming next. Not saying so is the
     * default, and a tick nobody says so on is a tick the head is wanted.
     */
    public void welcome() {
        this.welcome = true;
    }

    /**
     * One tick of the clock, to be called before anything can {@link #welcome}: a welcome not repeated is
     * gone.
     */
    public void tick() {
        this.welcome = false;
    }

    /**
     * Whether a glance would be welcome this tick, as offered.
     */
    public boolean welcomed() {
        return this.welcome;
    }

    /**
     * Advances the glance one tick.
     *
     * @param allowed  whether the head is free to glance this tick: welcomed by the walk, and nothing
     *                 being clicked. When it is not, any glance out comes back and nothing starts.
     * @param interest something worth a look, as an offset from the bearing the head is holding - the
     *                 way to the nearest animal or player, say - or null for nothing in particular. Asked
     *                 only as a glance starts, so it may cost something.
     */
    public void advance(boolean allowed, Supplier<Rotation> interest) {
        if (!allowed) {
            if (this.phase != Phase.NONE) {
                // interrupted: come back, and count the whole interval again before the next one
                this.phase = Phase.NONE;
                this.cooldown = drawCooldown();
            }
            recover();
            return;
        }
        switch (this.phase) {
            case NONE:
                if (this.yaw != 0f || this.pitch != 0f) {
                    // the tail of an interrupted glance, which finishes coming back before anything else
                    recover();
                    return;
                }
                if (--this.cooldown > 0) {
                    return;
                }
                start(interest == null ? null : interest.get());
                return;
            case OUT:
                this.elapsed++;
                set(ease((float) this.elapsed / this.length));
                if (this.elapsed >= this.length) {
                    enter(Phase.HOLD, draw(HOLD_MIN, HOLD_MAX));
                }
                return;
            case HOLD:
                this.elapsed++;
                set(1f);
                if (this.elapsed >= this.length) {
                    enter(Phase.BACK, ticksFor(BACK_MIN, BACK_MAX));
                }
                return;
            case BACK:
                this.elapsed++;
                set(1f - ease((float) this.elapsed / this.length));
                if (this.elapsed >= this.length) {
                    this.yaw = 0f;
                    this.pitch = 0f;
                    this.phase = Phase.NONE;
                    this.cooldown = drawCooldown();
                }
                return;
            default:
                throw new IllegalStateException(this.phase.name());
        }
    }

    /**
     * @return the yaw to add to the bearing this tick, in degrees; zero whenever no glance is out
     */
    public float yaw() {
        return this.yaw;
    }

    /**
     * @return the pitch to add to the bearing this tick, in degrees
     */
    public float pitch() {
        return this.pitch;
    }

    /**
     * Whether the head is off the bearing at all, out on a glance or on its way back from one.
     */
    public boolean out() {
        return this.phase != Phase.NONE || this.yaw != 0f || this.pitch != 0f;
    }

    private void start(Rotation interest) {
        if (interest != null && this.rand.nextFloat() < INTEREST_CHANCE
                && Math.abs(interest.getYaw()) <= MAX_INTEREST_YAW && Math.abs(interest.getPitch()) <= MAX_INTEREST_PITCH
                && Math.abs(interest.getYaw()) >= MIN_YAW * 0.5f) {
            // at the thing, near enough: a glance is not an aim, and an exact bearing would look like one
            this.yawAmplitude = interest.getYaw() + (this.rand.nextFloat() - 0.5f) * 4f;
            this.pitchAmplitude = interest.getPitch() + (this.rand.nextFloat() - 0.5f) * 2f;
        } else {
            final float side = this.rand.nextBoolean() ? 1f : -1f;
            this.yawAmplitude = side * (MIN_YAW + this.rand.nextFloat() * (MAX_YAW - MIN_YAW));
            this.pitchAmplitude = (this.rand.nextFloat() * 2f - 1f) * MAX_PITCH;
        }
        enter(Phase.OUT, ticksFor(OUT_MIN, OUT_MAX));
    }

    private void enter(Phase phase, int length) {
        this.phase = phase;
        this.length = Math.max(1, length);
        this.elapsed = 0;
    }

    /**
     * How many ticks to give a leg of the glance, so that the eased curve never moves the head faster than
     * {@link #MAX_STEP}: the curve's steepest slope is one and a half times the average, on either axis.
     */
    private int ticksFor(int min, int max) {
        final float slowest = Math.max(Math.abs(this.yawAmplitude) / MAX_STEP, Math.abs(this.pitchAmplitude) / MAX_PITCH_STEP);
        final int needed = (int) Math.ceil(1.5f * slowest);
        return Math.max(draw(min, max), needed);
    }

    private void set(float fraction) {
        this.yaw = this.yawAmplitude * fraction;
        this.pitch = this.pitchAmplitude * fraction;
    }

    /**
     * Brings both axes back toward nothing together, the slower of them at its full step, so that they
     * land on it on the same tick.
     */
    private void recover() {
        final float ticks = Math.max(Math.abs(this.yaw) / RECOVER_STEP, Math.abs(this.pitch) / MAX_PITCH_STEP);
        if (ticks <= 1f) {
            this.yaw = 0f;
            this.pitch = 0f;
            return;
        }
        final float keep = 1f - 1f / ticks;
        this.yaw *= keep;
        this.pitch *= keep;
    }

    private int drawCooldown() {
        return draw(COOLDOWN_MIN, COOLDOWN_MAX);
    }

    private int draw(int min, int max) {
        return min + this.rand.nextInt(max - min + 1);
    }

    /**
     * The smooth step from nothing to everything: still at both ends, quickest in the middle, which is the
     * shape of a head turning to look and not of a servo.
     */
    private static float ease(float t) {
        final float x = Math.max(0f, Math.min(1f, t));
        return x * x * (3f - 2f * x);
    }
}
