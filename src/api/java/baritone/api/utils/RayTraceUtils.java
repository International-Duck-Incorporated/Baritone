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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * @author Brady
 * @since 8/25/2018
 */
public final class RayTraceUtils {

    private RayTraceUtils() {}

    /**
     * Performs a block raytrace with the specified rotations. This should only be used when
     * any entity collisions can be ignored, because this method will not recognize if an
     * entity is in the way or not. The local player's block reach distance will be used.
     *
     * @param entity             The entity representing the raytrace source
     * @param rotation           The rotation to raytrace towards
     * @param blockReachDistance The block reach distance of the entity
     * @return The calculated raytrace result
     */
    public static HitResult rayTraceTowards(Entity entity, Rotation rotation, double blockReachDistance) {
        return rayTraceTowards(entity, rotation, blockReachDistance, false);
    }

    public static HitResult rayTraceTowards(Entity entity, Rotation rotation, double blockReachDistance, boolean wouldSneak) {
        Vec3 start;
        if (wouldSneak) {
            start = inferSneakingEyePosition(entity);
        } else {
            start = entity.getEyePosition(1.0F); // do whatever is correct
        }
        
        Vec3 direction = RotationUtils.calcLookDirectionFromRotation(rotation);
        Vec3 end = start.add(
                direction.x * blockReachDistance,
                direction.y * blockReachDistance,
                direction.z * blockReachDistance
        );
        return entity.level().clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, entity));
    }

    public static Vec3 inferSneakingEyePosition(Entity entity) {
        return new Vec3(entity.getX(), entity.getY() + entity.getEyeHeight(Pose.CROUCHING), entity.getZ());
    }

    /**
     * Whether a trace landed on the given block and on the given face of it.
     * <p>
     * The question a click that acts on one face of a block has to ask. A block being broken has no face -
     * the game breaks the block the crosshair is on, from whichever side it is on - so breaking and
     * anything else that acts on the block itself ask {@link IPlayerContext#isLookingAt(BlockPos)} and are
     * right to. But an item that is used *on* a face: a seed goes into the top of a farmland, a block goes
     * against the side the trace is on, and the trace the click is sent along is the one the game uses.
     * A click asked for while the crosshair is on the right block but the wrong face of it is a packet
     * that lands on a face that does not take the item, an arm that swings for nothing, and the tick spent
     * standing there for it.
     */
    public static boolean hitsFace(HitResult result, BlockPos pos, Direction face) {
        return result != null
                && result.getType() == HitResult.Type.BLOCK
                && ((BlockHitResult) result).getBlockPos().equals(pos)
                && ((BlockHitResult) result).getDirection() == face;
    }

    /**
     * Whether a trace landed on the given block and on the face of it that points at {@code placeAt} - the
     * same question as {@link #hitsFace}, for a caller that knows where the item is going rather than which
     * way the face is called. A placement is this exactly: the block is put into the position the traced
     * face points at, so a click sent with the crosshair on another side of the right block puts it
     * somewhere else - behind the player, on the bridge they just left.
     */
    public static boolean hitsFaceTowards(HitResult result, BlockPos against, BlockPos placeAt) {
        return result != null
                && result.getType() == HitResult.Type.BLOCK
                && ((BlockHitResult) result).getBlockPos().equals(against)
                && against.relative(((BlockHitResult) result).getDirection()).equals(placeAt);
    }
}
