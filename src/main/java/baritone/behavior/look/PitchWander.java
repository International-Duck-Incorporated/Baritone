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

import java.util.Random;

/**
 * Where a gaze that has nothing to look at settles, and how it moves on.
 * <p>
 * A target that has no opinion about the pitch - which is the idiom for one that only cares about the yaw,
 * and it is how the walking targets are built, using the player's own pitch so as to leave it, tall - used
 * to leave the pitch exactly where it was, held flat for as long as the walk lasted. Nobody holds their
 * head at one angle for a minute. The eyes settle somewhere slightly different every few seconds: a little
 * down at the ground ahead of the feet, a little up at the horizon, near level most of the time, and they
 * walk there over a few ticks rather than arriving.
 * <p>
 * So the pitch is stepped at most a degree a tick toward a preference that is drawn again every few
 * seconds, from a triangular distribution around level, and left alone in between. A degree a tick is the
 * old rule for dragging an extreme pitch back to level and it is slow enough to read as a head moving
 * rather than a number changing; the dead band of half a degree is there so a pitch that has arrived stays
 * arrived instead of buzzing against its own preference.
 * <p>
 * The ground ahead pulls on where the gaze settles - see {@link #lean} - because the eyes go where the
 * feet are about to, and the pull lasts one tick so that only a walk that keeps saying where it is going
 * is looked along.
 * <p>
 * Free of Minecraft so the rules can be tested; {@code PitchWanderTest} covers it.
 */
public final class PitchWander {

    /**
     * The furthest a settled gaze sits from level, in degrees. Reached about never: the draw is triangular,
     * so most preferences are within a few degrees.
     */
    public static final float RANGE = 10.0f;

    /**
     * How long a preference is held, in ticks: two to seven seconds.
     */
    public static final int HOLD_MIN = 40;
    public static final int HOLD_MAX = 140;

    /**
     * How far from the preference a pitch is left where it is, in degrees.
     */
    public static final float DEAD_BAND = 0.5f;

    /**
     * The largest step taken in one tick, in degrees. Positive pitch looks down.
     */
    public static final float STEP = 1.0f;

    /**
     * The furthest the ground ahead may pull the settled gaze off its own preference, in degrees, either
     * way. See {@link #lean}: a stair down is looked down, a drop is looked down more, and this is where
     * "more" stops being a glance at the footing and starts being a stare at the floor.
     */
    public static final float MAX_LEAN = 35.0f;

    private final Random rand;

    private float preferred;
    private float lean;
    private int hold;

    public PitchWander() {
        this(new Random());
    }

    /**
     * @param rand the source of variation, injectable so a run can be repeated; see {@code PitchWanderTest}
     */
    public PitchWander(Random rand) {
        this.rand = rand;
    }

    /**
     * One tick of the habit: every few seconds, the gaze settles somewhere else.
     */
    public void advance() {
        this.lean = 0f; // the ground ahead is offered every tick, and a lean not repeated is gone
        if (--this.hold > 0) {
            return;
        }
        // Two draws rather than one: summing two flat draws and taking off their mean gives a triangular
        // distribution, which puts the gaze near level most of the time and out at the edges rarely - the
        // shape of the thing rather than a uniform spread across the whole band.
        this.preferred = (this.rand.nextFloat() + this.rand.nextFloat() - 1.0f) * RANGE;
        this.hold = HOLD_MIN + this.rand.nextInt(HOLD_MAX - HOLD_MIN + 1);
    }

    /**
     * Steps a pitch toward where the gaze has settled, and not at all when it is already there.
     *
     * @param pitch the pitch the target asked for, which for these targets is the player's own
     * @return the pitch to ask for instead
     */
    public float step(float pitch) {
        final float settling = this.preferred + this.lean;
        if (pitch > settling + DEAD_BAND) {
            return pitch - STEP;
        }
        if (pitch < settling - DEAD_BAND) {
            return pitch + STEP;
        }
        return pitch;
    }

    /**
     * Where the ground ahead is going, as degrees of pitch to add to wherever the gaze has settled, for
     * this tick only: positive looks down. The eyes go where the feet are about to: a person walking down
     * a hill watches the footing and one walking up it looks up the slope, and a gaze that stays level
     * through both is a gaze fixed on nothing. A lean is a lean and not a new preference - the gaze still
     * settles where it settled, the ground just pulls it - and it lasts one tick, so that whoever knows the
     * ground has to keep saying so; the moment they stop, the gaze comes back to its own.
     *
     * @param degrees the pull, bounded to {@link #MAX_LEAN} either way
     */
    public void lean(float degrees) {
        this.lean = Math.max(-MAX_LEAN, Math.min(MAX_LEAN, degrees));
    }

    /**
     * Where the gaze is settling at the moment, in degrees, as its own preference: without the pull of
     * the ground ahead.
     */
    public float preferredPitch() {
        return this.preferred;
    }

    /**
     * Where the gaze is settling this tick, in degrees, ground and all: what {@link #step} steps toward.
     */
    public float settlingPitch() {
        return this.preferred + this.lean;
    }
}
