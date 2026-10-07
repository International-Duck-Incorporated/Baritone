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

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The order the builder looks for somewhere to put the next block.
 * <p>
 * Two things have to hold at once and this is the only place they can be checked without a world: the search
 * has to consider exactly the same blocks it always did, and it has to consider the near ones first - a
 * person puts down the block they are standing next to, not the one at the far corner of what they can
 * reach. The order also has to be the same from one tick to the next, or the builder changes its mind about
 * which block it is about to place while it is walking up to it.
 */
public class PlaceSearchTest {

    @Test
    public void everyColumnIsSearchedExactlyOnce() {
        final Set<String> seen = new HashSet<>();
        for (int[] column : PlaceSearch.columns()) {
            assertTrue("a column of the box is searched twice: " + column[0] + " " + column[1], seen.add(column[0] + "," + column[1]));
        }
        final int width = PlaceSearch.RADIUS * 2 + 1;
        assertEquals("the box searched is not the one the builder has always covered", width * width, seen.size());
    }

    @Test
    public void theSearchStartsUnderThePlayerAndWorksOutwards() {
        final int[][] columns = PlaceSearch.columns();
        // the block under the player's feet is looked at first, and from there the search only ever gets
        // further away: nothing in the middle of the box is favoured over something nearer
        assertEquals("the search does not start at the player", 0, columns[0][0]);
        assertEquals("the search does not start at the player", 0, columns[0][1]);
        int last = -1;
        for (int[] column : columns) {
            final int distance = column[0] * column[0] + column[1] * column[1];
            assertTrue("the search came back inwards: " + column[0] + " " + column[1], distance >= last);
            last = distance;
        }
        final int[] corner = columns[columns.length - 1];
        final int radius = PlaceSearch.RADIUS;
        assertEquals("the far corner of the box is not searched last", radius, Math.abs(corner[0]));
        assertEquals("the far corner of the box is not searched last", radius, Math.abs(corner[1]));
    }

    @Test
    public void columnsEquallyFarAwayKeepTheOrderTheyUsedToComeIn() {
        // the four corners are all the same distance out, and the order among things equally far away is all
        // that is left of the old nested loops - kept, because it is fixed
        final int[][] columns = PlaceSearch.columns();
        final int[][] corners = {{-5, -5}, {-5, 5}, {5, -5}, {5, 5}};
        for (int i = 0; i < corners.length; i++) {
            assertEquals(Arrays.toString(corners[i]), Arrays.toString(columns[columns.length - corners.length + i]));
        }
    }

    @Test
    public void theOrderIsTheSameEveryTimeItIsAskedFor() {
        // nothing about it is drawn or measured, so two calls in a row cannot disagree
        final String first = Arrays.deepToString(PlaceSearch.columns());
        for (int i = 0; i < 10; i++) {
            assertEquals(first, Arrays.deepToString(PlaceSearch.columns()));
        }
        assertEquals("the shared list is not the one the builder searches", first, Arrays.deepToString(PlaceSearch.COLUMNS));
    }
}
