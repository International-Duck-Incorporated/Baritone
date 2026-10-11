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

package baritone.utils;

import java.util.List;

/**
 * Whether to hit something, and the answer is no.
 * <p>
 * Every other decision in this mod is about doing a job; this one is the only place where the bot can
 * take an action that another player will read as aggression, and it is the only one where being wrong
 * has a cost beyond a wasted tick. So the shape of the answer is inverted: the default is declining,
 * every allowance is named, and anything the policy does not recognise is a reason not to swing rather
 * than a reason to swing. A rule that said "attack everything that is not one of these five harmless
 * mobs" would fail open on every mob added to the game after the rule was written; this file cannot
 * fail that way, because it has to be told that a thing is allowed before it will act.
 * <p>
 * The constraints it is written to, all of them from the plan's DEFEND card and the operator's
 * instruction of 2026-10-11 ("attack hostile mobs (hostile only)"):
 * <ul>
 *   <li><b>Hostile only.</b> A mob must be hostile <em>by nature</em>. An angered neutral - a struck
 *   zombified piglin, a wolf whose owner was hit, a bee whose nest was broken - is not hostile by
 *   nature, and it is exactly the case where a bot cannot know whether the anger is at it or at the
 *   person standing beside it. Those are declined.</li>
 *   <li><b>Never a detonating mob.</b> Meleeing a creeper is not a fight, it is a way to lose the
 *   items in the bot's inventory; the card forbids it and so does this.</li>
 *   <li><b>Self-defence, not hunting.</b> A hostile that has not noticed the bot is not a threat, and
 *   starting one is aggression a bot has no business initiating. The bot answers what is coming for
 *   it.</li>
 *   <li><b>In reach and in sight.</b> The policy is handed the reach it should use, so that the
 *   version-specific figure stays where the version is known, and there is no literal range buried
 *   here to be wrong on some other version.</li>
 *   <li><b>One at a time, and not into a crowd.</b> A single target, and if there are more hostiles
 *   nearby than the bound allows, the answer is that this is not a fight to take.</li>
 *   <li><b>Not while nearly dead, and not empty-handed</b> unless the caller has decided otherwise
 *   about what counts as a weapon.</li>
 * </ul>
 * <p>
 * Everything here is Minecraft-free on purpose, like {@link EatPolicy}: the judgement is the part that
 * can be wrong in a way that matters, and it is testable without a client.
 */
public final class DefensePolicy {

    private DefensePolicy() {}

    /**
     * What the bot can see of one nearby mob, described without a world.
     *
     * @param entityId          the entity's network id, which is what a decision names so that the
     *                          thing decided about and the thing swung at are the same thing
     * @param distance          blocks away
     * @param hostileByNature   vanilla's own category says this is a monster, as opposed to a passive
     *                          or a neutral mob - including a neutral that is currently angry
     * @param neutralOrPassive  the complement of the above, named separately so that "not hostile",
     *                          "unknown" and "forgot to fill this in" all land on the declining side
     * @param detonatesOnContact a mob whose death or approach is itself the attack
     * @param visible           line of sight to it
     * @param comingForUs       it is currently targeting this player - the self-defence test
     */
    public record Sighting(
            int entityId,
            double distance,
            boolean hostileByNature,
            boolean neutralOrPassive,
            boolean detonatesOnContact,
            boolean visible,
            boolean comingForUs) {
    }

    /**
     * The bot's own state, as far as this decision needs it.
     *
     * @param health what is left of it, in half-hearts out of the twenty a player has
     * @param armed  whether what is in the hand is worth hitting something with. The caller decides
     *               what that means - a sword, an axe, a pickaxe - because it is the caller that
     *               knows what the task is holding and whether it may be put aside
     */
    public record Self(double health, boolean armed) {
    }

    /** Why the answer was what it was, so that a log line can say it and a test can assert it. */
    public enum Reason {
        NOTHING_IN_SIGHT,
        ENGAGE,
        TOO_FAR,
        NOT_VISIBLE,
        NOT_HOSTILE,
        UNKNOWN_MOB,
        DETONATES,
        NOT_COMING_FOR_US,
        CROWDED,
        TOO_HURT,
        UNARMED
    }

    /** The decision: one target, or none, and the reason in either case. */
    public record Decision(int entityId, Reason reason) {
        public boolean engage() {
            return reason == Reason.ENGAGE;
        }
    }

    /**
     * More hostile mobs than this within reach and the bot does not take the fight. Three is where a
     * person stops counting and starts leaving; it is a bound rather than a measurement, and the plan's
     * own caution about "reviewable defaults, not measured balance" applies to it.
     */
    public static final int CROWD_LIMIT = 3;

    /**
     * Below this many half-hearts, the bot declines the fight. Six is three hearts: hurt enough that
     * the next few hits decide it, and above the point where a person would already have eaten.
     */
    public static final int MIN_HEALTH = 6;

    /**
     * The one to hit, or none.
     *
     * @param sightings everything the bot can currently see that might be a threat
     * @param self      the bot's own state
     * @param reach     how far the bot can hit from, decided by the caller because it is version- and
     *                  item-dependent
     * @return the decision, never {@code null}
     */
    public static Decision decide(List<Sighting> sightings, Self self, double reach) {
        if (sightings == null || sightings.isEmpty()) {
            return new Decision(-1, Reason.NOTHING_IN_SIGHT);
        }
        if (self.health() <= MIN_HEALTH) {
            return new Decision(-1, Reason.TOO_HURT);
        }
        if (!self.armed()) {
            return new Decision(-1, Reason.UNARMED);
        }
        Sighting best = null;
        Reason whyNot = Reason.NOTHING_IN_SIGHT;
        int crowd = 0;
        for (Sighting s : sightings) {
            if (!s.hostileByNature()) {
                // passive, neutral, angered-neutral, or a form the caller did not classify: all of
                // them decline, and the reason distinguishes "we know it is not a monster" from
                // "we do not know what it is", because only the second is a gap worth closing
                whyNot = whyNot == Reason.NOTHING_IN_SIGHT ? (s.neutralOrPassive() ? Reason.NOT_HOSTILE : Reason.UNKNOWN_MOB) : whyNot;
                continue;
            }
            if (s.detonatesOnContact()) {
                whyNot = whyNot == Reason.NOTHING_IN_SIGHT ? Reason.DETONATES : whyNot;
                continue;
            }
            if (!s.visible()) {
                whyNot = whyNot == Reason.NOTHING_IN_SIGHT ? Reason.NOT_VISIBLE : whyNot;
                continue;
            }
            if (s.distance() > reach) {
                whyNot = whyNot == Reason.NOTHING_IN_SIGHT ? Reason.TOO_FAR : whyNot;
                continue;
            }
            // It is a hostile, up close and in sight. Everything below is about whether this is a
            // fight to take, or the one to take.
            crowd++;
            if (!s.comingForUs()) {
                whyNot = whyNot == Reason.NOTHING_IN_SIGHT ? Reason.NOT_COMING_FOR_US : whyNot;
                continue;
            }
            if (best == null || s.distance() < best.distance()) {
                best = s;
            }
        }
        if (best == null) {
            return new Decision(-1, whyNot);
        }
        if (crowd > CROWD_LIMIT) {
            return new Decision(-1, Reason.CROWDED);
        }
        return new Decision(best.entityId(), Reason.ENGAGE);
    }
}
