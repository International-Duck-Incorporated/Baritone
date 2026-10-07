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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The tool in hand is a thing a person holds on to. The best tool for each block is the best tool for each
 * block, and reaching for it on every block means the item in the hand changes on the boundary between two
 * blocks of a different kind - a mixed column, dirt over stone, the breakable block in front of a traverse.
 * This is the rule that stops that, and it is the whole reason the rule exists, so it is worth failing a
 * build over.
 */
public class ToolSetTest {

    @Test
    public void theToolInHandIsKeptForASmallGain() {
        assertFalse("a tool the same speed as the one in hand is not worth a swap",
                ToolSet.worthSwitching(4.0, 4.0));
        assertFalse("a tool a shade faster is still not worth a swap",
                ToolSet.worthSwitching(4.0, 4.4));
        assertFalse("this is a preference, not a switch: the held tool has to be properly beaten",
                ToolSet.worthSwitching(1.0, 1.2));
    }

    @Test
    public void aProperlyFasterToolIsWorthTheSwap() {
        assertTrue("a quarter again as fast is a different tool, not the same one",
                ToolSet.worthSwitching(4.0, 5.1));
        assertTrue("a shovel over a pickaxe in dirt is four times the speed",
                ToolSet.worthSwitching(1.0, 4.0));
        assertTrue("an empty hand is not a tool to be held on to",
                ToolSet.worthSwitching(1.0, 8.0));
    }

    @Test
    public void theMarginIsAMarginAndNotADisableSwitch() {
        // Both failure modes matter. A margin at or below one is not a margin - the held tool would be kept
        // over an equally fast one, and the silk touch tie-break and the material cost tie-break in
        // getBestSlot would never be reached. A margin above two would mean a hand that mines with the
        // wrong tool for most of a session, which is not human either, it is lazy.
        assertTrue("a margin of " + ToolSet.SWITCH_GAIN + " is not a margin", ToolSet.SWITCH_GAIN > 1.0);
        assertTrue("a margin of " + ToolSet.SWITCH_GAIN + " keeps the wrong tool for too long",
                ToolSet.SWITCH_GAIN <= 2.0);
    }
}
