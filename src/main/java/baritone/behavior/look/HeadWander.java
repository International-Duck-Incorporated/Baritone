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
 * How much the head wanders off the direction it is supposed to be facing.
 * <p>
 * A player walking holds their head on a slow, small sway - a degree or so, swinging from side to side
 * over a couple of seconds, from nothing more than the body moving under it. A bot holds it nailed exactly
 * on the bearing, forever, and that is one of the few things that stays visible in a recording long after
 * everything else has been smoothed over. This is that sway, and nothing else.
 * <p>
 * <b>Why it sways rather than drifts.</b> The obvious way to write this is a bounded random walk: nudge
 * the offset by a random amount each tick and reflect it at the limits. Its offset is bounded, but its
 * <i>integral</i> is not - the offset stays correlated over tens of ticks, so the accumulated deviation
 * wanders like a random walk and reached four blocks sideways over a thousand seconds of walking when this
 * was measured. A slow oscillation is much better behaved, because the head keeps coming back through the
 * bearing: over the same thousand seconds the sway reaches 0.38 blocks, nine thousandths of one percent of
 * the ground covered, and no twenty second stretch leans by more than a tenth of a degree. Both designs
 * stay inside a degree at every instant, which is the property that is actually visible.
 * <p>
 * It is also the more human shape of the two: a person's head sways with their stride, and the amplitude
 * and period of that sway wander slowly rather than being resampled every tick.
 */
public final class HeadWander {

    /**
     * The furthest the head may stray from the bearing it is meant to hold, in degrees.
     */
    public static final float MAX_YAW = 1.0f;

    /**
     * Pitch sways less than yaw: a head bobs rather than nods, and pitch is the axis a player would notice.
     */
    public static final float MAX_PITCH = 0.6f;

    /**
     * The sway period, in ticks: two to five seconds. A sway per step would be a wobble; this is one slow
     * swing every few paces.
     */
    private static final double PERIOD_MIN = 40.0;
    private static final double PERIOD_MAX = 100.0;

    /**
     * How often the amplitude and period are drawn again, in ticks.
     */
    private static final int REDRAW_TICKS = 120;

    /**
     * The smallest the amplitude ever gets, as a fraction of the maximum: the sway quietens, it never
     * vanishes.
     */
    private static final float MIN_AMPLITUDE_FRACTION = 0.25f;

    /**
     * How fast a freshly drawn amplitude is approached, and how fast the sway fades in and out when the
     * head changes hands. Slow enough that neither can ever produce a step.
     */
    private static final float AMPLITUDE_RATE = 0.01f;
    private static final float GAIN_RATE = 0.05f;

    private final Random rand = new Random();

    private double phase;
    private double yawOmega = omega(60.0);
    private double pitchOmega = omega(90.0);
    private float yawAmplitude = MAX_YAW;
    private float pitchAmplitude = MAX_PITCH;
    private float yawAmplitudeTarget = MAX_YAW;
    private float pitchAmplitudeTarget = MAX_PITCH;
    private float gain;
    private int sinceRedraw;

    /**
     * Advances the sway one tick.
     */
    public void advance() {
        if (++this.sinceRedraw >= REDRAW_TICKS) {
            this.sinceRedraw = 0;
            this.yawAmplitudeTarget = drawAmplitude(MAX_YAW);
            this.pitchAmplitudeTarget = drawAmplitude(MAX_PITCH);
            this.yawOmega = omega(drawPeriod());
            this.pitchOmega = omega(drawPeriod() * 1.5);
        }
        this.phase += this.yawOmega;
        if (this.phase > Math.PI * 2.0) {
            this.phase -= Math.PI * 2.0;
        }
        this.yawAmplitude = approach(this.yawAmplitude, this.yawAmplitudeTarget, AMPLITUDE_RATE);
        this.pitchAmplitude = approach(this.pitchAmplitude, this.pitchAmplitudeTarget, AMPLITUDE_RATE);
        this.gain = Math.min(1.0f, this.gain + GAIN_RATE);
    }

    /**
     * Fades the sway out, for when the head is not ours to move. Without this the head would be left
     * wherever the sway happened to be when it stopped.
     */
    public void recentre() {
        this.gain = Math.max(0.0f, this.gain - GAIN_RATE);
    }

    /**
     * @return the yaw offset to add to the bearing, in degrees
     */
    public float yaw() {
        return this.gain * this.yawAmplitude * (float) Math.sin(this.phase);
    }

    /**
     * @return the pitch offset to add to the bearing, in degrees
     */
    public float pitch() {
        // a lower frequency and its own phase, so the two axes do not move in lockstep
        return this.gain * this.pitchAmplitude * (float) Math.sin(this.phase * 0.6 + 1.7);
    }

    private float drawAmplitude(float max) {
        return max * (MIN_AMPLITUDE_FRACTION + this.rand.nextFloat() * (1.0f - MIN_AMPLITUDE_FRACTION));
    }

    private double drawPeriod() {
        return PERIOD_MIN + this.rand.nextDouble() * (PERIOD_MAX - PERIOD_MIN);
    }

    private static double omega(double period) {
        return Math.PI * 2.0 / period;
    }

    private static float approach(float current, float target, float rate) {
        if (Math.abs(target - current) <= rate) {
            return target;
        }
        return current + Math.signum(target - current) * rate;
    }
}
