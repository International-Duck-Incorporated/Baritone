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

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The three ways eating can be wrong that a person would notice, and the rule that stops each of
 * them: biting something that is not dinner, changing its mind about which slot on every tick, and
 * clicking at a refused bite until the end of the game.
 */
public class EatPolicyTest {

    // bread, cooked porkchop, a golden apple, rotten flesh, a stick (not food at all)
    private static EatPolicy.Food plain(int slot, int nutrition) {
        return new EatPolicy.Food(slot, nutrition, 1, false, false);
    }

    @Test
    public void eatsTheOrdinaryFood() {
        assertTrue(EatPolicy.edible(plain(0, 5)));
        assertTrue(EatPolicy.edible(new EatPolicy.Food(3, 8, 64, false, false)));
    }

    @Test
    public void refusesTheSpecialsHoweverFillingTheyAre() {
        // A golden apple feeds and can be eaten on a full stomach. It is dinner for an emergency,
        // and a bot has no way to know that an emergency is what it is in.
        assertFalse(EatPolicy.edible(new EatPolicy.Food(0, 4, 1, true, false)));
        // Rotten flesh, spider eyes, pufferfish: ordinary nutrition figures with a sting attached.
        assertFalse(EatPolicy.edible(new EatPolicy.Food(0, 4, 1, false, true)));
        // A stack of nothing, and a stick.
        assertFalse(EatPolicy.edible(new EatPolicy.Food(0, 4, 0, false, false)));
        assertFalse(EatPolicy.edible(new EatPolicy.Food(0, 0, 1, false, false)));
        assertFalse(EatPolicy.edible(null));
    }

    @Test
    public void eatsTheMostFillingOneAvailable() {
        List<EatPolicy.Food> hotbar = Arrays.asList(plain(0, 2), plain(4, 8), plain(7, 5));
        assertEquals(4, EatPolicy.chooseFood(hotbar).slot());
    }

    @Test
    public void aSpecialIsNotChosenEvenWhenItIsTheMostFilling() {
        List<EatPolicy.Food> hotbar = Arrays.asList(
                plain(1, 5),
                new EatPolicy.Food(2, 9, 3, true, false),
                new EatPolicy.Food(3, 9, 3, false, true));
        assertEquals(1, EatPolicy.chooseFood(hotbar).slot());
    }

    @Test
    public void equalFoodsAlwaysComeBackToTheSameSlot() {
        // Two loaves. Either is right; changing your mind about which one every tick is not, because
        // the hand would be asked to move before it ever arrived.
        List<EatPolicy.Food> hotbar = Arrays.asList(new EatPolicy.Food(6, 5, 1, false, false), new EatPolicy.Food(2, 5, 1, false, false));
        assertEquals(2, EatPolicy.chooseFood(hotbar).slot());
        assertEquals(2, EatPolicy.chooseFood(new ArrayList<>(hotbar)).slot());
    }

    @Test
    public void nothingEdibleIsNoChoice() {
        assertNull(EatPolicy.chooseFood(new ArrayList<>()));
        assertNull(EatPolicy.chooseFood(Arrays.asList(plain(0, 0), new EatPolicy.Food(1, 6, 1, true, false))));
    }

    @Test
    public void aRefusedBiteIsTriedAgainAndThenDropped() {
        EatPolicy.Budget budget = new EatPolicy.Budget(EatPolicy.ATTEMPTS_PER_EPISODE);
        for (int attempt = 0; attempt < EatPolicy.ATTEMPTS_PER_EPISODE; attempt++) {
            assertTrue("attempt " + attempt + " should be allowed", budget.mayTry());
            budget.spend();
        }
        assertFalse("the budget is spent, so the clicking stops", budget.mayTry());
        // A meal landing, or the bot no longer being hungry, is what starts a new episode
        budget.reset();
        assertTrue(budget.mayTry());
        assertEquals(0, budget.spent());
    }

    @Test
    public void theWaitOutlastsTheBite() {
        assertTrue(EatPolicy.completionWindow(32) > 32);
        assertTrue(EatPolicy.completionWindow(0) > 0);
    }
}
