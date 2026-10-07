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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The two things every other rotation in this mod is built on: an angle is wrapped to the short way round,
 * and two angles that are a hundredth of a degree apart are the same angle wherever the wrap happens to put
 * them. Both are easy to get subtly wrong - the caveats are all at the 180 degree boundary, which is exactly
 * where a steering bug of the whole-step kind grew from, see
 * {@link Rotation#yawDistanceFromOffset(float, float)} - so both are pinned here.
 */
public class RotationTest {

    @Test
    public void theDistanceIsTheShortWayRound() {
        // The property the function is named for, and the one a bit of quadrant-arithmetic got wrong: a
        // difference of 112 degrees is a difference of 112 degrees whether you call it 112 or -112, and is
        // never anything like 472.
        for (float yaw = -180f; yaw < 180f; yaw += 5f) {
            for (float offsetYaw = -180f; offsetYaw < 180f; offsetYaw += 5f) {
                final float distance = Rotation.yawDistanceFromOffset(yaw, offsetYaw);
                assertTrue("a distance of " + distance + " degrees between " + yaw + " and " + offsetYaw,
                        Math.abs(distance) <= 180f);
                assertEquals("the distance is not the difference wrapped",
                        Rotation.normalizeYaw(yaw - offsetYaw), distance, 1e-3f);
            }
        }
        assertEquals("a hundred degrees the short way, not the long way",
                -112f, Rotation.yawDistanceFromOffset(0f, 112f), 1e-4f);
        assertEquals("a distance of 472 degrees, which is what this used to say",
                -2f, Rotation.yawDistanceFromOffset(179f, -179f), 1e-3f);
        assertEquals("and the same the other way round the world",
                2f, Rotation.yawDistanceFromOffset(-179f, 179f), 1e-3f);
    }

    @Test
    public void theDistanceSaysWhichSideTheOffsetIsOn() {
        // Positive when the offset is counter-clockwise of the yaw, negative when it is clockwise, which is
        // what makes it useful for steering: the sign is the side the keys have to turn toward.
        assertTrue("an offset twenty degrees to the left came back negative",
                Rotation.yawDistanceFromOffset(0f, 20f) < 0f);
        assertTrue("an offset twenty degrees to the right came back positive",
                Rotation.yawDistanceFromOffset(20f, 0f) > 0f);
        assertEquals("a yaw is no distance at all from itself",
                0f, Rotation.yawDistanceFromOffset(17.5f, 17.5f), 1e-6f);
        // and the angles that are the same angle written differently are also no distance apart
        assertEquals("170 and -190 are the same direction",
                0f, Rotation.yawDistanceFromOffset(170f, -190f), 1e-4f);
        // the distance from a to b is the distance from b to a, backwards
        for (float a = -180f; a < 180f; a += 11.25f) {
            for (float b = -180f; b < 180f; b += 11.25f) {
                assertEquals("the distance one way is not the other way negated",
                        0f,
                        Rotation.yawDistanceFromOffset(a, b) + Rotation.yawDistanceFromOffset(b, a),
                        1e-3f);
            }
        }
    }

    @Test
    public void theToleranceReachesAcrossTheBoundary() {
        // "Really close" is a hundredth of a degree, and two angles either side of the 180 degree boundary
        // can be a thousandth of a degree apart, which is why the check is written the awkward way it is.
        // Pinned here so that a future simplification has to argue with a test rather than with a comment.
        assertTrue("two angles a thousandth of a degree apart across the boundary are not close",
                new Rotation(179.999f, 0f).isReallyCloseTo(new Rotation(-179.999f, 0f)));
        assertFalse("two angles a fiftieth of a degree apart somewhere else are close",
                new Rotation(179.99f, 0f).isReallyCloseTo(new Rotation(-179.99f, 0f)));
        assertTrue("the same angle written either side of the boundary is close",
                new Rotation(-180f, 0f).isReallyCloseTo(new Rotation(180f, 0f)));
        // and a pitch is held inside the joint's range wherever it is asked to clamp, because every angle
        // the aim produces goes through clamp() on the way out
        assertEquals("pitch below the floor was not clamped", -90f, new Rotation(0f, -100f).clamp().getPitch(), 1e-4f);
        assertEquals("pitch above the ceiling was not clamped", 90f, new Rotation(0f, 100f).clamp().getPitch(), 1e-4f);
        assertEquals("a pitch inside the range was moved", 42f, new Rotation(0f, 42f).clamp().getPitch(), 1e-4f);
    }
}
