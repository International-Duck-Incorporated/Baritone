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

import java.util.Set;

/**
 * What kind of thing an entity is, decided from its registry id and nothing else.
 * <p>
 * This is an explicit whitelist on purpose. Vanilla's own category ("monster", "creature") is not a
 * reliable answer to "is this hostile by nature": a neutral mob can sit in the monster category, and a
 * mod can add anything under any category. So a type is only ever {@link #HOSTILE_BY_NATURE} or
 * {@link #DETONATING} if it is named here, in the {@code minecraft} namespace, and everything else is
 * {@link #UNKNOWN}. An unknown is a reason to decline, never a reason to attack.
 * <p>
 * The tables are conservative rather than complete. A mob added to the game later reads as
 * {@link #UNKNOWN} until someone reviews it and names it here - that is the intended failure mode.
 */
public enum ThreatKind {
    /** Monsters that are hostile in every state: the only class {@code DefensePolicy} may engage. */
    HOSTILE_BY_NATURE,
    /** Hostile, but whose death or approach is itself the attack. Never engaged. */
    DETONATING,
    /** Angerable neutrals. Their anger may be at a person beside the bot, so they are never engaged. */
    NEUTRAL,
    /** Mobs that do not attack. */
    PASSIVE,
    /** Anything not named above, including every non-vanilla entity. */
    UNKNOWN;

    private static final String VANILLA = "minecraft:";

    private static final Set<String> HOSTILE = Set.of(
            "zombie", "husk", "drowned", "skeleton", "stray", "spider", "cave_spider", "witch", "phantom");

    private static final Set<String> DETONATE = Set.of("creeper");

    private static final Set<String> ANGERABLE = Set.of(
            "enderman", "zombified_piglin", "wolf", "bee", "iron_golem", "polar_bear", "llama", "goat");

    private static final Set<String> PASSIVE_TYPES = Set.of(
            "cow", "pig", "sheep", "chicken", "rabbit", "horse", "donkey", "mooshroom", "villager", "cat");

    /**
     * The kind of a registry id such as {@code minecraft:zombie}.
     *
     * @param typeId the entity type's registry id, or {@code null} if it could not be read
     * @return the kind, never {@code null}; anything not recognised is {@link #UNKNOWN}
     */
    public static ThreatKind of(String typeId) {
        if (typeId == null || !typeId.startsWith(VANILLA)) {
            return UNKNOWN;
        }
        String name = typeId.substring(VANILLA.length());
        if (HOSTILE.contains(name)) {
            return HOSTILE_BY_NATURE;
        }
        if (DETONATE.contains(name)) {
            return DETONATING;
        }
        if (ANGERABLE.contains(name)) {
            return NEUTRAL;
        }
        if (PASSIVE_TYPES.contains(name)) {
            return PASSIVE;
        }
        return UNKNOWN;
    }
}
