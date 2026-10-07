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

package baritone.pathing.movement;

import net.minecraft.core.BlockPos;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Whether a walk has gone its way long enough for the head to straighten onto it is read off the path,
 * and these are the shapes a path takes: a straight, a staircase, a slope, and the corners between.
 */
public class StraightRunTest {

    private static final BlockPos N = new BlockPos(0, 0, -1);
    private static final BlockPos E = new BlockPos(1, 0, 0);
    private static final BlockPos UP_N = new BlockPos(0, 1, -1);
    private static final BlockPos DOWN_N = new BlockPos(0, -1, -1);
    private static final BlockPos PILLAR = new BlockPos(0, 1, 0);

    @Test
    public void aStraightSettlesAtItsThirdBlock() {
        final List<BlockPos> straight = Arrays.asList(N, N, N, N, N);
        assertEquals(0, StraightRun.before(straight, 0));
        assertEquals(1, StraightRun.before(straight, 1));
        assertEquals(2, StraightRun.before(straight, 2));
        assertEquals(4, StraightRun.before(straight, 4));
        assertFalse("the first block of a straight faced the way", StraightRun.settled(straight, 0));
        assertFalse("the second block of a straight faced the way", StraightRun.settled(straight, 1));
        assertTrue("the third block of a straight did not face the way", StraightRun.settled(straight, 2));
        assertTrue(StraightRun.settled(straight, 4));
    }

    @Test
    public void aStaircaseNeverSettles() {
        final List<BlockPos> staircase = Arrays.asList(N, E, N, E, N, E, N, E);
        for (int i = 0; i < staircase.size(); i++) {
            assertEquals("a leg of a staircase counted as a straight", 0, StraightRun.before(staircase, i));
            assertFalse(StraightRun.settled(staircase, i));
        }
    }

    @Test
    public void aTwoToOneSlopeNeverSettlesEither() {
        // the slope a path takes toward a goal just off its axis: walked on a steady diagonal, the way a
        // person walks it, and not with the head turning at every second block
        final List<BlockPos> slope = Arrays.asList(N, N, E, N, N, E, N, N, E);
        for (int i = 0; i < slope.size(); i++) {
            assertFalse("block " + i + " of a two-to-one slope faced the way", StraightRun.settled(slope, i));
        }
    }

    @Test
    public void aCornerStartsTheCountAgain() {
        final List<BlockPos> corner = Arrays.asList(N, N, N, E, E, E);
        assertTrue(StraightRun.settled(corner, 2));
        assertEquals("the corner inherited the straight before it", 0, StraightRun.before(corner, 3));
        assertFalse(StraightRun.settled(corner, 3));
        assertFalse(StraightRun.settled(corner, 4));
        assertTrue(StraightRun.settled(corner, 5));
    }

    @Test
    public void climbsAndDropsTheSameWayAreTheSameWay() {
        // the head only cares which way the feet are going across the ground: a step up or down a straight
        // is still the straight
        final List<BlockPos> hilly = Arrays.asList(UP_N, DOWN_N, N);
        assertEquals(2, StraightRun.before(hilly, 2));
        assertTrue(StraightRun.settled(hilly, 2));
    }

    @Test
    public void aPillarGoesNoWayAcrossTheGround() {
        final List<BlockPos> pillared = Arrays.asList(N, N, PILLAR, N);
        assertEquals("a pillar counted as part of a straight", 0, StraightRun.before(pillared, 3));
    }

    @Test
    public void outOfRangeIsNoRunAtAll() {
        final List<BlockPos> straight = Arrays.asList(N, N, N);
        assertEquals(0, StraightRun.before(straight, -1));
        assertEquals(0, StraightRun.before(straight, 3));
        assertEquals(0, StraightRun.before(Collections.emptyList(), 0));
        assertFalse(StraightRun.settled(null, null));
    }
}
