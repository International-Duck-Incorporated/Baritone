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

import net.minecraft.core.BlockPos;

import java.util.Comparator;
import java.util.List;

/**
 * The order the farm works through the blocks within reach, in.
 * <p>
 * The block a person harvests is the one they are already standing next to, not the one at the far end of
 * what they can reach. The farm's four in-reach loops - harvesting, planting, cocoa and bonemeal - each
 * took the <em>first</em> entry of their list that was within reach and traceable, and those lists came out
 * of the world scan in the order the scan found them: {@code FasterWorldScanner#visitSection} walks a chunk
 * section's raw {@code BitStorage} in index order, and its own decode makes {@code x} run fastest, then
 * {@code z}, then {@code y}. That is a west-to-east, north-to-south sweep of each sixteen-by-sixteen layer,
 * anchored to the world's axes and to nothing about the player - so a bot standing in a mature field reached
 * for the westernmost ripe crop in reach, across the body at full arm, worked eastward, and started the
 * sweep over whenever the reachable set changed. The same tell {@link PlaceSearch} exists to stop in the
 * builder.
 * <p>
 * Every other candidate list in the tree is already ordered from the player: {@code MineProcess#prune} and
 * its shaft pick, {@code BackfillProcess#toFillIn}, the builder's {@link PlaceSearch#COLUMNS}, and
 * {@code GetToBlockProcess} through {@code MineProcess#searchWorld}. The farm was the one left over.
 * <p>
 * Order only, and only the in-reach half: the same blocks are considered and the same ones turn out to be
 * reachable, and the far half is a {@code GoalComposite} that the pathfinder prices, so list order there
 * was already irrelevant.
 * <p>
 * Two things about the key are load bearing rather than tidy, and {@code FarmReachTest} pins both:
 * <ul>
 * <li><b>The distance is measured from the integer feet block.</b> The click every one of those loops is
 * waiting on is gated on {@code LookBehavior#hasArrivedAt}, which asks the aim processor what the hand
 * would do toward <em>this</em> rotation, and {@code updateTarget} replaces the target outright every tick.
 * A choice that flips between two blocks tick to tick therefore sends the aim chasing one and then the
 * other, neither is ever arrived at, and the click never goes out - not a cosmetic stutter but a stall. The
 * feet block, rather than the exact position, cannot be reordered by the sub-block jitter of a body
 * settling onto farmland.</li>
 * <li><b>The sort is stable.</b> Two blocks equally far away keep the order the scan gave them, so the
 * answer is the same from one tick to the next for as long as the bot stands in the same block - which is
 * the whole of a harvest, since every one of the loops pauses the path to do its work where it stands.</li>
 * </ul>
 * <p>
 * Free of the world - it is given positions and asks for nothing else - so the order can be tested on its
 * own.
 */
final class FarmReach {

    private FarmReach() {
    }

    /**
     * Reorders {@code candidates} so the ones nearest the player's feet come first.
     * <p>
     * Nothing is added and nothing is dropped: this is a reordering of the list it is given. Ties keep the
     * order they arrived in.
     *
     * @param candidates the blocks to work through, in whatever order the scan gave them
     * @param feet       the block the player is standing in, as {@code ctx.playerFeet()}
     */
    static void nearestFirst(List<BlockPos> candidates, BlockPos feet) {
        candidates.sort(Comparator.comparingLong(pos -> squaredDistance(feet, pos)));
    }

    /**
     * The squared distance between two block positions: the ordering key. Squared because only the order is
     * wanted, and between <em>blocks</em> because a key that moved with the player's exact position would
     * let the choice flip while the hand was still on its way to the last one.
     *
     * @param from the block the player is standing in
     * @param to   the candidate
     * @return the squared distance, in blocks
     */
    static long squaredDistance(BlockPos from, BlockPos to) {
        final long dx = to.getX() - from.getX();
        final long dy = to.getY() - from.getY();
        final long dz = to.getZ() - from.getZ();
        return dx * dx + dy * dy + dz * dz;
    }
}
