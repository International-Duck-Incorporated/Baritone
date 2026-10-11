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
 * What a hungry bot is allowed to eat, which of the carried candidates it eats first, and how many
 * times it may try before it stops asking.
 * <p>
 * The three answers are here, Minecraft-free, because they are the part of eating that can be wrong
 * in a way a person would notice - a bot that bites a golden apple, or that chews through a stack of
 * them one stall at a time, or that picks a different slot on every tick and so never settles on one.
 * Everything that needs a world (the hand, the slot, the button) stays in {@code EatBehavior}; this
 * is the judgement, and it is testable without a client.
 * <p>
 * The acceptance rule is deliberately narrow. An item is eaten only if it is ordinary carrying food:
 * it feeds ({@code nutrition > 0}), there is some of it, and it is neither of the two things that
 * make a food special. {@code alwaysEdible} is vanilla's own word for the foods a person can eat on a
 * full stomach, and those are the ones that are worth something else - the golden apple and its
 * enchanted sibling, the chorus fruit, the honey bottle. {@code hasEffects} covers the other half:
 * the plain-looking foods that do something to you when they go down, which is the rotten flesh, the
 * spider eye, the pufferfish, the poisonous potato. A bot has no way to want an effect, and a person
 * who wanted one can eat it themselves, so the bot leaves all of them alone. What is left is the
 * fixture: bread, cooked meat, carrots, and everything else whose whole job is the bar.
 */
public final class EatPolicy {

    private EatPolicy() {}

    /**
     * One thing the bot is carrying and could eat, described without reference to a world.
     *
     * @param slot         the hotbar slot (0 to 8) or the inventory slot it would come from
     * @param nutrition    {@code FoodProperties#nutrition()}, the half-bar hunger points it restores
     * @param count        how many of them are in the stack
     * @param alwaysEdible vanilla's {@code FoodProperties#canAlwaysEat()}: edible on a full stomach,
     *                     which is what the special foods have in common
     * @param hasEffects   whether consuming it applies anything beyond the food itself, including
     *                     the harmful ones a plain nutrition figure does not reveal
     */
    public record Food(int slot, int nutrition, int count, boolean alwaysEdible, boolean hasEffects) {
    }

    /**
     * Whether this is ordinary carrying food. See the class comment for why the specials are excluded.
     */
    public static boolean edible(Food food) {
        return food != null
                && food.nutrition() > 0
                && food.count() > 0
                && !food.alwaysEdible()
                && !food.hasEffects();
    }

    /**
     * The one to eat: of everything edible, the most filling, because a meal that does not fill the
     * bar is a meal somebody has to repeat.
     * <p>
     * Ties go to the lowest slot, which is not a preference between two equal foods - it is what
     * makes the answer the same on every tick. A choice that moved between equal candidates would
     * have the hand switching back and forth and never arriving, which is the one way this code
     * could spend a whole hunger bar's worth of ticks doing nothing.
     *
     * @return the chosen candidate, or {@code null} if nothing here is edible
     */
    public static Food chooseFood(List<Food> candidates) {
        Food best = null;
        for (Food candidate : candidates) {
            if (!edible(candidate)) {
                continue;
            }
            if (best == null
                    || candidate.nutrition() > best.nutrition()
                    || (candidate.nutrition() == best.nutrition() && candidate.slot() < best.slot())) {
                best = candidate;
            }
        }
        return best;
    }

    /**
     * How many bites of one hunger episode may be attempted. A stall is a thing that happens - the
     * hand was busy, the server refused, the mouth was interrupted - and a second attempt is the
     * reasonable answer to it. Three is where that stops being an answer and starts being a bot
     * clicking at nothing, so the budget runs out and the bot is quiet until it is fed by something
     * else or gets hungry again later.
     */
    public static final int ATTEMPTS_PER_EPISODE = 3;

    /**
     * Attempts spent on the current hunger episode. Handed out, never taken back - except by
     * {@link #reset()}, which is called when a meal actually lands or when the bot is no longer
     * hungry, and those are the two events that mean the episode is over.
     */
    public static final class Budget {

        private final int max;
        private int spent;

        public Budget(int max) {
            this.max = max;
        }

        public boolean mayTry() {
            return spent < max;
        }

        public void spend() {
            spent++;
        }

        public void reset() {
            spent = 0;
        }

        public int spent() {
            return spent;
        }
    }

    /**
     * How long to keep watching a bite that has been started, in ticks, on top of what vanilla says
     * the item takes. The extra is patience rather than tolerance: a server that is a little behind,
     * or a tick where the client had not yet been told the mouth was full.
     */
    public static int completionWindow(int consumeTicks) {
        return consumeTicks + 10;
    }
}
