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
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * Where on a block the hand is put when the block is the thing being aimed at.
 * <p>
 * Upstream aims at the exact centre of the block's box, every block, every time, and that is a tell that
 * survives everything else: a person's crosshair lands somewhere on the block, a little high and to the
 * left on this one and low on the next, and whatever it is that reads a player as a person reads that
 * scatter as part of it. This picks the somewhere. It is a point inside the middle {@code 2 * }
 * {@link #SPREAD} of the block's box on each axis - never near an edge, where a neighbour could get in the
 * way of the trace - and it is the same point for the same block for as long as the game is running,
 * because an aim that is being held must not be re-drawn under the hand: the point is a hash of the block's
 * position and a salt drawn once per run, not a random draw per tick. The next run puts it somewhere
 * else.
 * <p>
 * Only the point is chosen here. Whether the hand can actually see it from where the player is standing is
 * {@code RotationUtils#reachable}'s to check, and when it cannot the centre and the face centres are tried
 * as they always were. Nothing that is placed takes it: a face to place against is aimed at exactly,
 * because where the crosshair lands on it is where the block goes.
 */
public final class AimScatter {

    /**
     * How far off the centre the point may be on each axis, as a fraction of the box's extent that way: a
     * third of the way to the edge at most, so the point is always in the middle two thirds of the face.
     */
    public static final double SPREAD = 1.0 / 3.0;

    /**
     * This run's salt. Drawn from the clock once, so two runs do not share a scatter, and no setting.
     */
    private static final long SALT = new Random().nextLong();

    private AimScatter() {
    }

    /**
     * The point to aim at on the block at {@code pos}, given the box the block occupies.
     *
     * @param pos    the block
     * @param centre the centre of its box
     * @param extent the size of its box on each axis
     * @return a point inside the box, within {@link #SPREAD} of the centre on each axis
     */
    public static Vec3 point(BlockPos pos, Vec3 centre, Vec3 extent) {
        return point(pos, SALT, centre, extent);
    }

    /**
     * The same, with the salt given, for the tests.
     */
    public static Vec3 point(BlockPos pos, long salt, Vec3 centre, Vec3 extent) {
        final long seed = mix(pos.asLong() ^ salt);
        return new Vec3(
                centre.x + (unit(seed) - 0.5) * 2.0 * SPREAD * extent.x,
                centre.y + (unit(mix(seed)) - 0.5) * 2.0 * SPREAD * extent.y,
                centre.z + (unit(mix(mix(seed))) - 0.5) * 2.0 * SPREAD * extent.z
        );
    }

    /**
     * SplitMix64's finaliser: every bit of the position ends up in every bit of the result, so neighbouring
     * blocks do not get neighbouring points.
     */
    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /**
     * The top 53 bits of {@code bits} as a number in [0, 1).
     */
    static double unit(long bits) {
        return (bits >>> 11) * 0x1.0p-53;
    }
}
