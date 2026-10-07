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

package baritone.api.behavior;

import baritone.api.Settings;
import baritone.api.behavior.look.IAimProcessor;
import baritone.api.utils.Rotation;

/**
 * @author Brady
 * @since 9/23/2018
 */
public interface ILookBehavior extends IBehavior {

    /**
     * Updates the current {@link ILookBehavior} target to target the specified rotations on the next tick. If any sort
     * of block interaction is required, {@code blockInteract} should be {@code true}. It is not guaranteed that the
     * rotations set by the caller will be the exact rotations expressed by the client (This is due to settings like
     * {@link Settings#randomLooking}). If the rotations produced by this behavior are required, then the
     * {@link #getAimProcessor() aim processor} should be used.
     *
     * @param rotation      The target rotations
     * @param blockInteract Whether the target rotations are needed for a block interaction
     */
    void updateTarget(Rotation rotation, boolean blockInteract);

    /**
     * Whether the hand has finished travelling to the specified rotation and is holding it.
     * <p>
     * This is the question the code that clicks actually has - has the aim arrived, or is it still on its
     * way - and it cannot be answered by comparing the player's rotation with the target, because the two
     * are never equal: the hand eases toward the target and the tremor shakes it on the way, so a settled
     * aim sits a fraction of a degree off the angle it was asked for. Callers used to write that comparison
     * with a hundredth of a degree of tolerance and get an answer once every seventy ticks.
     *
     * @param rotation The rotation the caller is asking about, which is what its target was set to
     * @return Whether the aim is holding that rotation
     */
    boolean hasArrivedAt(Rotation rotation);

    /**
     * Where the head is, as far as the hand knows: the rotation the aim has got to so far, before the sway
     * and the glances that ride on top of it for everyone else to see; or the player's own rotation when
     * nothing is being aimed at.
     * <p>
     * This is the angle for anything that chooses what to do next from where the head is - which way to
     * turn, which keys to press - as opposed to {@link #hasArrivedAt}, which is about where the head
     * actually is. The things that ride on top are not a direction, they are a habit: a choice made from
     * them is a choice made from a look to the side, and feeds that look back into the steering.
     *
     * @return the rotation the hand is holding, or the player's own when it holds nothing
     */
    Rotation headRotation();

    /**
     * The aim processor instance for this {@link ILookBehavior}, which is responsible for applying additional,
     * deterministic transformations to the target rotation set by {@link #updateTarget}.
     *
     * @return The aim processor
     * @see IAimProcessor#fork
     */
    IAimProcessor getAimProcessor();
}
