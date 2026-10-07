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

package baritone.utils;

import java.util.Random;

/**
 * How long a person waits between repeated actions.
 * <p>
 * Anything that repeats every tick, forever, is a giveaway, and the fix is always the same: draw a short
 * delay and sit still for it. The arithmetic is kept here, away from the code that talks to Minecraft, so
 * it can be tested without a game running.
 */
public final class Cadence {

    private Cadence() {}

    /**
     * Ticks to wait before starting the next block after one breaks.
     * <p>
     * A person does not begin the next block in the same tick the last one vanished: there is a beat while
     * they register it and move on. Multi-tick breaks are already paced by {@code blockBreakSpeed}, but an
     * instant break never set a timer, so a field of crops or a patch of grass went down at exactly one
     * block per tick, forever.
     */
    public static final int RETARGET_DELAY_MIN = 1;
    public static final int RETARGET_DELAY_MAX = 3;

    /**
     * @return the pause, in ticks, before attacking the next block; inclusive of both ends of the range
     */
    public static int retargetDelay(Random rand) {
        return RETARGET_DELAY_MIN + rand.nextInt(RETARGET_DELAY_MAX - RETARGET_DELAY_MIN + 1);
    }

    /**
     * How far a repeated action's interval is allowed to wander, in ticks, either side of the setting.
     */
    public static final int JITTER = 1;

    /**
     * A fixed interval with a tick of slack on either side.
     * <p>
     * Everything that repeats on a setting - placing a block every {@code rightClickSpeed} ticks, the
     * pause after a block breaks, an inventory move - repeats on <em>exactly</em> that interval, forever,
     * which is a rhythm no person produces: hold a stopwatch to someone building a wall and the gaps are
     * three ticks, four, three, three, five. The eye does not read "5.0 blocks per second" as fast, it
     * reads the metronome, and it is one of the few tells that survives a good camera and a plausible
     * reaction time.
     * <p>
     * A tick either way is 50 milliseconds: enough to break the periodicity, far too little to change how
     * the activity looks or how long it takes. Intervals the setting has already set to one tick cannot be
     * shortened and are left alone.
     *
     * @param ticks the interval the setting asks for
     * @return the interval to actually wait, in ticks
     */
    public static int jitter(Random rand, int ticks) {
        if (ticks <= 1) {
            return ticks;
        }
        final int drawn = ticks - JITTER + rand.nextInt(JITTER * 2 + 1);
        return Math.max(1, drawn);
    }
}
