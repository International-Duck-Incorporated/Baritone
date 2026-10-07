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

package baritone.process;

import java.util.Arrays;
import java.util.Comparator;

/**
 * The columns the builder searches for somewhere to put the next block, in the order it searches them.
 * <p>
 * The block a person puts down is the one they are already standing next to and can see, not the one at the
 * far back corner of what they can reach. The search used to walk its box the other way round - x from the
 * west edge inwards, then y upwards, then z - so the block the builder reached for was whichever hittable
 * one sat furthest west: it built from a corner, one reach at a time, and the order it worked in was
 * anchored to the world's axes rather than to where the person was standing. Building from where you are
 * also means fewer steps between one block and the next.
 * <p>
 * Nearest first, then, with one constraint kept from the loops this replaces: the order has to be a fixed
 * list rather than something that changes as the player shifts their feet, because the builder is walking
 * towards the block it picked and a choice that depends on nothing will not be reversed half way there. Two
 * columns equally far away therefore keep the order the old nested loops produced them in, which is what
 * {@code java.util.Arrays.sort} being stable leaves them in.
 * <p>
 * Free of Minecraft and of the builder, so the order can be tested on its own; {@code PlaceSearchTest} does.
 */
final class PlaceSearch {

    /**
     * How far the builder looks, in blocks: five each way, the box it has always covered. The search covers
     * {@code (2 * RADIUS + 1)} squared columns.
     */
    static final int RADIUS = 5;

    /**
     * The search order, as {@code {dx, dz}} pairs relative to the block the player is standing on. Shared
     * rather than rebuilt: it is the same list every tick of every build, and it is not to be modified.
     */
    static final int[][] COLUMNS = columns();

    private PlaceSearch() {
    }

    /**
     * Builds the order {@link #COLUMNS} holds.
     */
    static int[][] columns() {
        final int width = RADIUS * 2 + 1;
        final int[][] columns = new int[width * width][];
        int i = 0;
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                columns[i++] = new int[]{dx, dz};
            }
        }
        // java.util.Arrays.sort is stable, so two columns equally far away keep the order above
        Arrays.sort(columns, Comparator.comparingInt(column -> column[0] * column[0] + column[1] * column[1]));
        return columns;
    }
}
