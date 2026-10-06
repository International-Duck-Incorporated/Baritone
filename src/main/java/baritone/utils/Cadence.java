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
}
