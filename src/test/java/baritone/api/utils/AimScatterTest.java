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

package baritone.api.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * The point on a block the hand goes to: inside the block, off its centre, the same for the same block,
 * and different for the next one.
 */
public class AimScatterTest {

    private static final Vec3 FULL = new Vec3(1, 1, 1);

    private static Vec3 centre(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    @Test
    public void thePointIsInsideTheMiddleOfTheBox() {
        final Random rand = new Random(1);
        for (int i = 0; i < 20000; i++) {
            final BlockPos pos = new BlockPos(rand.nextInt(60000000) - 30000000, rand.nextInt(384) - 64, rand.nextInt(60000000) - 30000000);
            final Vec3 extent = new Vec3(0.125 + rand.nextDouble() * 0.875, 0.125 + rand.nextDouble() * 0.875, 0.125 + rand.nextDouble() * 0.875);
            final Vec3 centre = centre(pos);
            final Vec3 point = AimScatter.point(pos, rand.nextLong(), centre, extent);
            assertTrue(Math.abs(point.x - centre.x) <= AimScatter.SPREAD * extent.x + 1e-9);
            assertTrue(Math.abs(point.y - centre.y) <= AimScatter.SPREAD * extent.y + 1e-9);
            assertTrue(Math.abs(point.z - centre.z) <= AimScatter.SPREAD * extent.z + 1e-9);
        }
    }

    @Test
    public void theSameBlockGetsTheSamePointForAsLongAsItIsAimedAt() {
        final BlockPos pos = new BlockPos(12, 64, -7);
        final Vec3 first = AimScatter.point(pos, 42L, centre(pos), FULL);
        for (int tick = 0; tick < 100; tick++) {
            assertEquals(first, AimScatter.point(pos, 42L, centre(pos), FULL));
        }
        // and a copy of the position is the same block
        assertEquals(first, AimScatter.point(new BlockPos(12, 64, -7), 42L, centre(pos), FULL));
    }

    @Test
    public void neighbouringBlocksDoNotGetNeighbouringPoints() {
        // the obvious failure for a hash of a position: a row of blocks getting a row of points that drift
        // across their faces in step, which would be a pattern of its own
        final Random rand = new Random(2);
        for (int trial = 0; trial < 200; trial++) {
            final long salt = rand.nextLong();
            final BlockPos origin = new BlockPos(rand.nextInt(1000) - 500, rand.nextInt(200), rand.nextInt(1000) - 500);
            Vec3 previousOffset = null;
            Vec3 previousStep = null;
            int sameDirection = 0;
            for (int i = 0; i < 12; i++) {
                final BlockPos pos = origin.offset(i, 0, 0);
                final Vec3 point = AimScatter.point(pos, salt, centre(pos), FULL);
                final Vec3 offset = point.subtract(centre(pos));
                if (previousOffset != null) {
                    final Vec3 step = offset.subtract(previousOffset);
                    assertTrue("two blocks in a row got the same point", step.lengthSqr() > 1e-6);
                    if (previousStep != null && step.dot(previousStep) > 0) {
                        sameDirection++;
                    }
                    previousStep = step;
                }
                previousOffset = offset;
            }
            assertTrue("the points along a row of twelve blocks drifted the same way " + sameDirection + " times", sameDirection < 9);
        }
    }

    @Test
    public void thePointsCoverTheMiddleOfTheFaceEvenly() {
        // no axis is favoured, no corner is favoured, and the centre is nothing special: a quarter of the
        // points in each quarter of the spread, give or take
        final Random rand = new Random(3);
        final int[] quadrant = new int[4];
        final int n = 40000;
        double sumX = 0;
        double sumY = 0;
        for (int i = 0; i < n; i++) {
            final BlockPos pos = new BlockPos(rand.nextInt(100000), rand.nextInt(256), rand.nextInt(100000));
            final Vec3 offset = AimScatter.point(pos, 7L, centre(pos), FULL).subtract(centre(pos));
            quadrant[(offset.x >= 0 ? 1 : 0) + (offset.y >= 0 ? 2 : 0)]++;
            sumX += offset.x;
            sumY += offset.y;
        }
        for (int q = 0; q < 4; q++) {
            assertTrue("quadrant " + q + " got " + quadrant[q] + " of " + n, Math.abs(quadrant[q] - n / 4.0) < n * 0.02);
        }
        assertTrue(Math.abs(sumX / n) < 0.01);
        assertTrue(Math.abs(sumY / n) < 0.01);
    }

    @Test
    public void anotherRunPutsThePointSomewhereElse() {
        final BlockPos pos = new BlockPos(0, 0, 0);
        assertNotEquals(AimScatter.point(pos, 1L, centre(pos), FULL), AimScatter.point(pos, 2L, centre(pos), FULL));
    }

    @Test
    public void aFlatBoxGetsAFlatScatter() {
        // a slab, a carpet: the point stays inside the thin box
        final BlockPos pos = new BlockPos(3, 3, 3);
        final Vec3 centre = new Vec3(3.5, 3.25, 3.5);
        final Vec3 extent = new Vec3(1, 0.5, 1);
        for (long salt = 0; salt < 1000; salt++) {
            final Vec3 point = AimScatter.point(pos, salt, centre, extent);
            assertTrue(point.y > 3.0 && point.y < 3.5);
        }
    }
    @Test
    public void aFacePointIsOnTheFaceAndInTheMiddleOfIt() {
        final Random rand = new Random(7);
        for (int i = 0; i < 2000; i++) {
            final BlockPos placeAt = new BlockPos(rand.nextInt(2000) - 1000, rand.nextInt(256), rand.nextInt(2000) - 1000);
            final Direction.Axis normal = Direction.Axis.values()[i % 3];
            final Vec3 centre = new Vec3(placeAt.getX() + 0.5, placeAt.getY() + 0.5, placeAt.getZ() + 0.5);
            final Vec3 point = AimScatter.facePoint(placeAt, centre, normal);
            final double dx = Math.abs(point.x - centre.x);
            final double dy = Math.abs(point.y - centre.y);
            final double dz = Math.abs(point.z - centre.z);
            assertEquals("moved along the normal " + normal, 0.0, normal == Direction.Axis.X ? dx : normal == Direction.Axis.Y ? dy : dz, 0.0);
            assertTrue("off the middle of the face: " + dx + ", " + dy + ", " + dz,
                    dx <= AimScatter.SPREAD && dy <= AimScatter.SPREAD && dz <= AimScatter.SPREAD);
        }
    }

    @Test
    public void aFacePointIsTheSameForTheSamePlaceWhateverTheRunOfTicks() {
        final BlockPos placeAt = new BlockPos(3, 64, -9);
        final Vec3 centre = new Vec3(3.0, 64.5, -8.5);
        final Vec3 first = AimScatter.facePoint(placeAt, centre, Direction.Axis.X);
        for (int i = 0; i < 50; i++) {
            assertEquals(first, AimScatter.facePoint(placeAt, centre, Direction.Axis.X));
        }
    }
}
