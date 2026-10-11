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

package baritone.utils.threat;

import java.util.UUID;

/**
 * One entity as it was seen on one client tick, with everything the client could actually establish
 * about it - and no more.
 * <p>
 * The sampler that builds these is the only place that touches the game. Nothing here is a claim the
 * client cannot back: the three answers that could be guessed are kept explicit rather than flattened.
 * <ul>
 *   <li>{@link #targetingUs} is {@link Knowledge#UNKNOWN} when the client has no replicated answer.
 *   A client's view of a mob's target can be missing, and "missing" is not "not coming for us".</li>
 *   <li>{@code hurtTime} and "a player stood nearby" are deliberately not fields at all. They are
 *   timing, not attribution, and they once named the wrong attacker.</li>
 *   <li>{@link #armed} is the caller's answer to "is the hand worth hitting something with", because
 *   only the caller knows what the task is holding.</li>
 * </ul>
 *
 * @param world        the dimension or world key the sample was taken in; a sample from another
 *                     world is never the same entity
 * @param entityId     the entity's network id, reused by the game after a despawn
 * @param uuid         the entity's own id, which is not reused: id plus uuid is the entity's identity
 * @param typeId       the registry id, e.g. {@code minecraft:zombie}, or {@code null} if unreadable
 * @param distance     blocks from the player at the sample
 * @param visible      whether the sampler found a line of sight
 * @param targetingUs  whether the entity is targeting this player, as far as the client knows
 * @param alive        the entity was alive at the sample
 * @param loaded       the entity was in a loaded chunk at the sample
 * @param sampledTick  the client tick the sample was taken on
 */
public record Observation(
        String world,
        int entityId,
        UUID uuid,
        String typeId,
        double distance,
        boolean visible,
        Knowledge targetingUs,
        boolean alive,
        boolean loaded,
        int sampledTick) {

    /** A three-valued answer the client may not be able to give. */
    public enum Knowledge {
        YES,
        NO,
        UNKNOWN
    }
}
