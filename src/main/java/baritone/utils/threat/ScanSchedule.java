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

package baritone.utils.threat;

import java.util.ArrayList;
import java.util.List;

/**
 * How often the perception layer may look, and how many expensive line-of-sight checks one tick may spend.
 * <p>
 * The budget is for the bot's owned tasks only. With no task in control there is no scan at all: an
 * idle bot does not start watching the world on its own, because nothing has asked it to.
 * <p>
 * Rotation for line of sight: the candidates are checked in a window that moves by {@link #LOS_PER_TICK}
 * every tick and wraps. Every candidate is looked at within {@code ceil(n / LOS_PER_TICK)} ticks, and the
 * window for a given tick depends only on the tick and the count, so the same inputs always check the
 * same things.
 */
public final class ScanSchedule {

    /** At 20 client ticks per second, a rescan every four ticks is 5 Hz. */
    public static final int RESCAN_INTERVAL_TICKS = 4;

    /** Expensive line-of-sight checks allowed on one tick. */
    public static final int LOS_PER_TICK = 4;

    private boolean scannedBefore;
    private int lastScanTick;

    /**
     * Whether a scan is due this tick.
     *
     * @param nowTick   the current client tick
     * @param ownedTask whether a process holds control of the body
     * @return true at most once per {@link #RESCAN_INTERVAL_TICKS}, and only while a task owns the body
     */
    public boolean shouldScan(int nowTick, boolean ownedTask) {
        if (!ownedTask) {
            return false;
        }
        if (scannedBefore && nowTick - lastScanTick < RESCAN_INTERVAL_TICKS) {
            return false;
        }
        scannedBefore = true;
        lastScanTick = nowTick;
        return true;
    }

    /**
     * The candidate indices to run line of sight on this tick.
     *
     * @param candidateCount how many candidates there are
     * @param nowTick        the current client tick
     * @return at most {@link #LOS_PER_TICK} distinct indices in {@code [0, candidateCount)}, in window order
     */
    public static List<Integer> losWindow(int candidateCount, int nowTick) {
        if (candidateCount <= 0) {
            return List.of();
        }
        int size = Math.min(LOS_PER_TICK, candidateCount);
        // Windows of LOS_PER_TICK, each starting where the last one ended, so they tile the list.
        int start = Math.floorMod(nowTick * LOS_PER_TICK, candidateCount);
        List<Integer> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            out.add((start + i) % candidateCount);
        }
        return out;
    }
}
