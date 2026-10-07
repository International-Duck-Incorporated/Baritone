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

/**
 * Whether finishing the walk under way is still a step toward the thing being followed.
 * <p>
 * A goal that moves - a person being followed, an item being chased - used to take the walking apart:
 * every time the goal stepped clear of where the path ended, the whole path was cancelled and a new one
 * asked for, and while the new one was being calculated the bot stood still. Follow somebody walking at
 * a steady pace and that is a stop every second or two, forever, which is the walk of a machine and not
 * of a person. A person following somebody keeps walking: their plan bends a little at a time, and only
 * somebody who has actually gone somewhere else makes them stop and work out a new way.
 * <p>
 * So the walk keeps going while its end is still closer to what is being followed than the player is -
 * arriving there is progress, whatever the goal has been doing since the path was made - and the
 * plan-ahead machinery chains the next stretch to wherever the goal has wandered by then. A target that
 * has doubled back, or run off sideways, is not progress from the end of the path, and that is the only
 * thing that stops the walk.
 * <p>
 * Free of Minecraft and of the behaviour that drives it, so the rule can be tested. {@code
 * FollowProgressTest} covers it.
 */
public final class FollowProgress {

    private FollowProgress() {}

    /**
     * Whether walking to the end of the path is still walking toward what is being followed.
     *
     * @param meX the x coordinate of the player, who has not finished walking
     * @param meY the y coordinate of the player
     * @param meZ the z coordinate of the player
     * @param endX the x coordinate of where the current path ends
     * @param endY the y coordinate of where the current path ends
     * @param endZ the z coordinate of where the current path ends
     * @param targetX the x coordinate of the thing being followed (or where its goal wants it)
     * @param targetY the y coordinate of the thing being followed
     * @param targetZ the z coordinate of the thing being followed
     * @return true to let the walk finish and chain the next one on; false to stop and turn around
     */
    public static boolean worthFinishing(int meX, int meY, int meZ, int endX, int endY, int endZ, int targetX, int targetY, int targetZ) {
        return distSq(endX, endY, endZ, targetX, targetY, targetZ) <= distSq(meX, meY, meZ, targetX, targetY, targetZ);
    }

    private static long distSq(int x1, int y1, int z1, int x2, int y2, int z2) {
        final long dx = x1 - x2;
        final long dy = y1 - y2;
        final long dz = z1 - z2;
        return dx * dx + dy * dy + dz * dz;
    }
}
