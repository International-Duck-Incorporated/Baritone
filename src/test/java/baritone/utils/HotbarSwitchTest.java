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

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The beat between deciding on a hotbar slot and pressing its key: it is a beat and not nothing, it is
 * never longer than the range, a changed mind does not stretch it, and nothing is clicked across it.
 */
public class HotbarSwitchTest {

    /**
     * Asks for {@code slot} every tick from {@code held} until the key is pressed, the way the callers do.
     *
     * @return how many ticks it took
     */
    private static int ticksToSwitch(HotbarSwitch hand, int slot, int held) {
        for (int tick = 1; tick <= 100; tick++) {
            hand.ask(slot, held);
            final int pressed = hand.tick(held);
            if (pressed >= 0) {
                assertEquals(slot, pressed);
                return tick;
            }
            assertTrue("not switching while a switch is on its way", hand.switching());
        }
        throw new AssertionError("the key was never pressed");
    }

    @Test
    public void theKeyIsPressedABeatLaterAndWithinTheRange() {
        // one hand, many switches: the first draw of a freshly seeded java.util.Random is not to be trusted
        // to cover a range, and no hand in the game is fresh for more than one switch anyway
        final HotbarSwitch hand = new HotbarSwitch(new Random(17));
        boolean sawMin = false;
        boolean sawMax = false;
        int held = 0;
        for (int i = 0; i < 400; i++) {
            final int slot = (held + 3) % 9;
            final int ticks = ticksToSwitch(hand, slot, held);
            held = slot;
            assertTrue("switched in " + ticks + " ticks", ticks >= Cadence.HOTBAR_SWITCH_MIN && ticks <= Cadence.HOTBAR_SWITCH_MAX);
            sawMin |= ticks == Cadence.HOTBAR_SWITCH_MIN;
            sawMax |= ticks == Cadence.HOTBAR_SWITCH_MAX;
            assertFalse("still switching after the key was pressed", hand.switching());
        }
        assertTrue("never the quickest beat", sawMin);
        assertTrue("never the slowest beat", sawMax);
    }

    @Test
    public void theSlotAlreadyInHandIsNothingToDo() {
        final HotbarSwitch hand = new HotbarSwitch(new Random(1));
        for (int tick = 0; tick < 50; tick++) {
            hand.ask(4, 4);
            assertEquals(-1, hand.tick(4));
            assertFalse(hand.switching());
        }
    }

    @Test
    public void nothingAskedIsNothingPressed() {
        final HotbarSwitch hand = new HotbarSwitch(new Random(1));
        for (int tick = 0; tick < 50; tick++) {
            assertEquals(-1, hand.tick(2));
            assertFalse(hand.switching());
        }
        hand.ask(-1, 2);
        hand.ask(9, 2);
        assertFalse("a slot off the hotbar was taken as an ask", hand.switching());
    }

    @Test
    public void aChangedMindPressesTheLastKeyDecidedOnWithoutStartingOver() {
        // two callers taking turns every tick: the key still gets pressed, within the one beat, and it is
        // the one decided on last
        for (int seed = 0; seed < 100; seed++) {
            final HotbarSwitch hand = new HotbarSwitch(new Random(seed));
            int pressed = -1;
            int ticks = 0;
            while (pressed < 0) {
                ticks++;
                hand.ask(1, 0);
                hand.ask(2, 0);
                pressed = hand.tick(0);
                assertTrue("never pressed", ticks <= Cadence.HOTBAR_SWITCH_MAX);
            }
            assertEquals(2, pressed);
        }
    }

    @Test
    public void somebodyElseGettingThereFirstEndsTheSwitch() {
        final HotbarSwitch hand = new HotbarSwitch(new Random(5));
        hand.ask(6, 0);
        assertTrue(hand.switching());
        // the slot turns up in the hand before the beat is up - a reflex that sets it directly
        assertEquals(-1, hand.tick(6));
        assertFalse(hand.switching());
    }

    @Test
    public void anAskForTheHeldSlotDoesNotCancelOneOnItsWay() {
        // the key already being pressed is pressed
        final HotbarSwitch hand = new HotbarSwitch(new Random(9));
        hand.ask(5, 0);
        hand.ask(0, 0);
        assertTrue(hand.switching());
        int pressed = -1;
        for (int tick = 0; tick < Cadence.HOTBAR_SWITCH_MAX && pressed < 0; tick++) {
            pressed = hand.tick(0);
        }
        assertEquals(5, pressed);
    }

    @Test
    public void everySwitchDrawsItsOwnBeat() {
        final HotbarSwitch hand = new HotbarSwitch(new Random(3));
        boolean differed = false;
        int previous = -1;
        int held = 0;
        for (int i = 0; i < 40; i++) {
            final int slot = (held + 1) % 9;
            final int ticks = ticksToSwitch(hand, slot, held);
            held = slot;
            differed |= previous >= 0 && previous != ticks;
            previous = ticks;
        }
        assertTrue("forty switches all took the same number of ticks", differed);
    }
}
