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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * The elytra auto-swap's three window clicks, and the rule about the window they land in: they wait for the
 * player's own inventory to be the menu in front, a sequence that has not begun when its window is gone is
 * given up whole, and one that has begun is finished. The gaps are the auto-swap's own timing and are pinned
 * here too, so that the rule written around them does not quietly re-time them: they are the configured
 * interval, drawn afresh, which is the shape every other hand rule in this fork has.
 */
public class InventoryTransactionTest {

    /**
     * One swap: three clicks that record the tick each of them went out on, and the transaction they were
     * queued in. The clock is the test's own, so a click's tick is a number this class can assert about.
     */
    private static final class Swap {
        private final InventoryTransaction transaction;
        private final List<Integer> ticks = new ArrayList<>();
        private int tick;

        Swap(int seed) {
            this.transaction = new InventoryTransaction(new Random(seed));
            for (int click = 0; click < 3; click++) {
                this.transaction.click(() -> this.ticks.add(this.tick));
            }
        }

        /** Advances the clock one tick and reports whether a click went out on it. */
        boolean tick(boolean windowIsOurs, int setting) {
            this.tick++;
            return this.transaction.tick(windowIsOurs, setting);
        }

        /** Queues one click of a later swap, the way the auto-swap writes one once the last has gone out. */
        void andAnother() {
            this.transaction.click(() -> this.ticks.add(this.tick));
        }

        /** Advances the clock until the swap has run every click, and returns the ticks they went out on. */
        List<Integer> runOut(int setting) {
            for (int guard = 0; guard < 500 && this.ticks.size() < 3; guard++) {
                tick(true, setting);
            }
            return this.ticks;
        }

        boolean waiting() {
            return this.transaction.waiting();
        }

        /** The behaviour being let go: finish what has started rather than drop it. */
        boolean finishNow(boolean windowIsOurs) {
            return this.transaction.finishNow(windowIsOurs);
        }
    }

    @Test
    public void theFirstClickIsAvailableAtOnce() {
        final Swap swap = new Swap(1);
        assertTrue("a transaction with a click queued did nothing on its first tick", swap.tick(true, 5));
        assertEquals("more than one click went out in one tick", 1, swap.ticks.size());
        assertTrue("a transaction with clicks left is not waiting", swap.waiting());
    }

    @Test
    public void theClicksAreSpacedByTheSetting() {
        for (int setting : new int[]{1, 2, 5, 20}) {
            final Swap swap = new Swap(setting);
            final List<Integer> ticks = swap.runOut(setting);
            assertEquals("the transaction did not finish on setting " + setting + ": " + ticks,
                    3, ticks.size());
            assertEquals("the first click is not the first tick on setting " + setting + ": " + ticks,
                    Integer.valueOf(1), ticks.get(0));
            for (int i = 1; i < ticks.size(); i++) {
                final int gap = ticks.get(i) - ticks.get(i - 1);
                assertTrue("a gap of " + gap + " ticks is off the setting's band on setting " + setting
                                + ": " + ticks,
                        gap >= Math.max(1, setting - Cadence.JITTER)
                                && gap <= setting + Cadence.JITTER);
            }
            assertFalse("a finished transaction is still waiting", swap.waiting());
        }
    }

    @Test
    public void oneTickBetweenClicksIsTheNextTick() {
        // the floor of the setting: the three clicks of a swap go out on three consecutive ticks, which is
        // what the auto-swap did before the gaps were drawn and what a swap with a deadline needs
        final Swap swap = new Swap(6);
        assertEquals("a one-tick transaction is not three consecutive ticks",
                List.of(1, 2, 3), swap.runOut(1));
        assertFalse("a finished transaction is still waiting", swap.waiting());
    }

    @Test
    public void aTransactionThatHasNotBegunIsGivenUpWithItsWindow() {
        final Swap swap = new Swap(2);
        for (int tick = 0; tick < 50; tick++) {
            // a chest or a crafting table in front of the player: not the window these clicks were for
            assertFalse("a click went out into somebody else's window", swap.tick(false, 1));
        }
        assertFalse("a transaction that never began is still waiting", swap.waiting());
        assertEquals("a click went out into a window that was not the one it was written for",
                0, swap.ticks.size());
        // and it is gone for good: a window that comes back does not fire a swap that was given up
        for (int tick = 0; tick < 10; tick++) {
            swap.tick(true, 1);
        }
        assertEquals("a given-up transaction fired when its window came back", 0, swap.ticks.size());
    }

    @Test
    public void aTransactionThatHasBegunIsFinished() {
        final Swap swap = new Swap(3);
        assertTrue("the transaction did not begin", swap.tick(true, 1));
        // the window goes away with the first click of three already out: the spare elytra is on the cursor
        // and the worn one is still on the back, so this is the one state that must not be left standing
        for (int tick = 0; tick < 50; tick++) {
            swap.tick(false, 1);
        }
        assertEquals("a begun transaction was given up halfway", 1, swap.ticks.size());
        assertTrue("a begun transaction stopped waiting for its window", swap.waiting());
        for (int tick = 0; tick < 10 && swap.ticks.size() < 3; tick++) {
            swap.tick(true, 1);
        }
        assertEquals("a begun transaction did not finish when its window came back", 3, swap.ticks.size());
        assertFalse("a finished transaction is still waiting", swap.waiting());
    }

    @Test
    public void aSecondSequenceStartsAtOnce() {
        // the auto-swap asks for a swap only when none is in flight, so a second sequence begins with the
        // state the first one ended in rather than carrying its countdown
        final Swap swap = new Swap(7);
        swap.runOut(5);
        assertEquals("the first transaction did not finish", 3, swap.ticks.size());
        swap.andAnother();
        assertTrue("a new sequence waited on the old one's clock", swap.tick(true, 5));
        assertEquals("a new sequence did not begin at once", 4, swap.ticks.size());
    }

    @Test
    public void anEmptyTransactionDoesNothing() {
        final InventoryTransaction transaction = new InventoryTransaction(new Random(5));
        for (int tick = 0; tick < 20; tick++) {
            assertFalse("an empty transaction sent a click", transaction.tick(tick % 2 == 0, 5));
        }
        assertFalse("an empty transaction is waiting", transaction.waiting());
    }

    /**
     * The residual this exists for: a flight cancelled inside the gap between two of the three clicks used
     * to leave the queue never asked again, with the spare elytra on the cursor and the worn one still on
     * the back. The drain is not run any more, so the sequence has to be finished on the way out.
     */
    @Test
    public void teardownFinishesASwapThatHasBegun() {
        final Swap swap = new Swap(11);
        assertTrue("the transaction did not begin", swap.tick(true, 5));
        assertEquals("the swap did not begin with exactly one click out", 1, swap.ticks.size());
        assertTrue("teardown of a begun swap with its window open did not finish it", swap.finishNow(true));
        assertEquals("teardown did not run the two clicks that were left", 3, swap.ticks.size());
        assertEquals("the remainder was spaced rather than sent now",
                List.of(1, 1, 1), swap.ticks);
        assertFalse("a finished transaction is still waiting", swap.waiting());
    }

    @Test
    public void teardownGivesUpASwapThatHasNotBegun() {
        for (boolean windowIsOurs : new boolean[]{true, false}) {
            final Swap swap = new Swap(12);
            assertTrue("teardown of a swap that never began reported unfinished", swap.finishNow(windowIsOurs));
            assertEquals("a click went out for a swap that was given up whole", 0, swap.ticks.size());
            assertFalse("a given-up transaction is still waiting", swap.waiting());
            // and it is gone for good, the way the window rule's give-up is: nothing happened, so the job
            // asks again rather than this queue firing late
            for (int tick = 0; tick < 10; tick++) {
                swap.tick(true, 1);
            }
            assertEquals("a given-up transaction fired afterwards", 0, swap.ticks.size());
        }
    }

    /**
     * The case that is honestly left open. Finishing into somebody else's window would send clicks the game
     * drops and then clear a queue that never ran - turning "stalled and still retryable" into "stalled and
     * silently abandoned" - so the queue is left exactly as it was and the caller is told.
     */
    @Test
    public void teardownLeavesABegunSwapItCannotFinish() {
        final Swap swap = new Swap(13);
        assertTrue("the transaction did not begin", swap.tick(true, 5));
        assertFalse("teardown claimed to finish a swap it could not send", swap.finishNow(false));
        assertEquals("a click went out into a window that was not the one it was written for",
                1, swap.ticks.size());
        assertTrue("an unfinished transaction stopped waiting", swap.waiting());
        // the queue is untouched, so a caller that wants to retry still can, and the window rule still holds
        for (int tick = 0; tick < 10 && swap.ticks.size() < 3; tick++) {
            swap.tick(true, 5);
        }
        assertEquals("the left-alone transaction did not finish when its window came back",
                3, swap.ticks.size());
        assertFalse("a finished transaction is still waiting", swap.waiting());
    }

    @Test
    public void teardownSpendsNoCountdownOnTheNextSequence() {
        // a swap finished on the way out leaves no clock behind: the next sequence the auto-swap queues
        // begins at once, the way a sequence after a finished one always has
        final Swap swap = new Swap(14);
        swap.tick(true, 20);
        swap.finishNow(true);
        swap.andAnother();
        assertTrue("the next sequence waited on a teardown's clock", swap.tick(true, 20));
    }

    @Test
    public void teardownOfAnEmptyTransactionDoesNothing() {
        final InventoryTransaction transaction = new InventoryTransaction(new Random(15));
        assertTrue("teardown of an empty transaction reported unfinished", transaction.finishNow(true));
        assertTrue("teardown of an empty transaction reported unfinished", transaction.finishNow(false));
        assertFalse("an empty transaction is waiting", transaction.waiting());
    }

    /**
     * The wiring, pinned from the source because there is no world to cancel a flight in here.
     * <p>
     * Two things have to hold and both are about <em>where</em> the ask sits. It has to be in
     * {@code onLostControl}, which every caller reaches on the client thread, and not in
     * {@code destroyBehaviorAsync}, which runs on the executor and is also reached by
     * {@code onWorldEvent} on the way out of a world - a window click sent into a closing connection is
     * worse than the residual this closes. And it has to reach the rule, not a copy of it.
     */
    @Test
    public void theSwapIsFinishedOnTheWayOutAndNotOnTheExecutor() {
        final Path root = repoRoot();
        assumeTrue("the sources are not next to this test run", root != null);
        final Path process = root.resolve("src/main/java/baritone/process/ElytraProcess.java");
        final Path behavior = root.resolve("src/main/java/baritone/process/elytra/ElytraBehavior.java");
        assumeTrue("the elytra process is not next to this test run", Files.isRegularFile(process));
        assumeTrue("the elytra behaviour is not next to this test run", Files.isRegularFile(behavior));

        final List<String> processLines = read(process);
        final int ask = lineContaining(processLines, "this.behavior.finishTheSwapBeforeGoing();");
        assertTrue("the swap is no longer finished on the way out: a flight cancelled inside the gap between"
                        + " two of the three clicks leaves the spare elytra on the cursor",
                ask >= 0);
        assertEquals("the swap is finished in more than one place",
                1, countContaining(processLines, "finishTheSwapBeforeGoing("));
        final int destroyAsync = lineContaining(processLines, "private void destroyBehaviorAsync() {");
        assertTrue("destroyBehaviorAsync moved above onLostControl; re-check that the ask is still in"
                        + " onLostControl and not on the executor",
                destroyAsync > 0);
        assertTrue("the swap is finished inside destroyBehaviorAsync rather than in onLostControl: that runs"
                        + " on the executor, and onWorldEvent reaches it on the way out of a world",
                ask < destroyAsync);

        // the behaviour's own teardown does not do it: destroy() runs on the executor and sees no window
        final List<String> behaviorLines = read(behavior);
        final int destroy = lineContaining(behaviorLines, "public void destroy() {");
        assertTrue("ElytraBehavior#destroy moved; re-check the ask is not inside it", destroy >= 0);
        int end = destroy + 1;
        while (end < behaviorLines.size() && !behaviorLines.get(end).equals("    }")) {
            end++;
        }
        for (int i = destroy; i < end; i++) {
            assertFalse("ElytraBehavior#destroy reaches the transaction: it is on the executor, where a"
                            + " window click has no thread to be sent on",
                    behaviorLines.get(i).contains("finishNow(") || behaviorLines.get(i).contains("invTransaction"));
        }
        // and the ask reaches the rule rather than a copy of it
        final int helper = lineContaining(behaviorLines, "void finishTheSwapBeforeGoing() {");
        assertTrue("finishTheSwapBeforeGoing is gone from the behaviour", helper >= 0);
        assertTrue("finishTheSwapBeforeGoing no longer asks InventoryTransaction#finishNow",
                behaviorLines.subList(helper, Math.min(behaviorLines.size(), helper + 6)).stream()
                        .anyMatch(line -> line.contains("invTransaction.finishNow(")));
    }

    private static int lineContaining(List<String> lines, String needle) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).contains(needle)) {
                return i;
            }
        }
        return -1;
    }

    private static int countContaining(List<String> lines, String needle) {
        int count = 0;
        for (String line : lines) {
            if (line.contains(needle)) {
                count++;
            }
        }
        return count;
    }

    private static List<String> read(Path path) {
        try {
            return Files.readAllLines(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The checkout, found by walking up from wherever this test was started: Gradle runs from the project
     * directory and so does the test runner beside the build scripts, but nothing guarantees either.
     */
    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 4 && dir != null; i++) {
            if (Files.isDirectory(dir.resolve("src"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        return null;
    }
}
