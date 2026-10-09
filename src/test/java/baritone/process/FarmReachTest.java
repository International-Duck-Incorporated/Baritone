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
import org.junit.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * The order the farm works through what is within reach.
 * <p>
 * Three things have to hold at once, and this is the only place they can be checked without a world: the
 * loops have to consider exactly the blocks they always did, they have to consider the near ones first, and
 * the answer has to be the same from one tick to the next while the bot stands in the same block - because
 * the click each loop is waiting on is gated on the aim arriving, and an aim that is re-sent at a different
 * block every tick never arrives at either.
 */
public class FarmReachTest {

    private static final BlockPos FEET = new BlockPos(0, 64, 0);

    @Test
    public void theNearestBlockIsWorkedFirst() {
        // handed to the farm in the order a west-to-east section sweep would give them
        final List<BlockPos> candidates = new ArrayList<>(Arrays.asList(
                new BlockPos(-4, 64, 0),
                new BlockPos(-1, 64, 0),
                new BlockPos(3, 64, 0),
                new BlockPos(0, 64, 2)
        ));
        FarmReach.nearestFirst(candidates, FEET);
        assertEquals("the farm did not reach for the block it is standing next to",
                new BlockPos(-1, 64, 0), candidates.get(0));
        assertEquals("the farm did not leave the far end of its reach for last",
                new BlockPos(-4, 64, 0), candidates.get(candidates.size() - 1));
        long last = -1;
        for (BlockPos pos : candidates) {
            final long distance = FarmReach.squaredDistance(FEET, pos);
            assertTrue("the order came back inwards at " + pos, distance >= last);
            last = distance;
        }
    }

    @Test
    public void itConsidersExactlyTheBlocksItWasGiven() {
        // order only: nothing is dropped, nothing is invented, nothing is duplicated
        final List<BlockPos> given = Arrays.asList(
                new BlockPos(3, 64, 3),
                new BlockPos(-2, 65, 1),
                new BlockPos(0, 64, -1),
                new BlockPos(3, 64, 3)
        );
        final List<BlockPos> ordered = new ArrayList<>(given);
        FarmReach.nearestFirst(ordered, FEET);
        assertEquals(given.size(), ordered.size());
        assertEquals("a block was lost or invented", new HashSet<>(given), new HashSet<>(ordered));
        assertEquals("a block was counted twice", Collections.frequency(given, new BlockPos(3, 64, 3)),
                Collections.frequency(ordered, new BlockPos(3, 64, 3)));
    }

    /**
     * The stall this order has to avoid.
     * <p>
     * {@code LookBehavior#updateTarget} replaces the target outright every tick and the click is gated on
     * {@code hasArrivedAt} for that tick's target, so a farm that picked a different block each tick would
     * chase two aims and press neither button. What keeps the pick still is that the key is the
     * <em>integer feet block</em> and the sort is stable: ask twice from the same block and the answer is
     * the same answer, including between two blocks the same distance away.
     */
    @Test
    public void theSameFeetGiveTheSameAnswerTwice() {
        // two crops equally far away, and a third further out: the tie has to break the same way every time
        final List<BlockPos> scanOrder = Arrays.asList(
                new BlockPos(2, 64, 0),
                new BlockPos(-2, 64, 0),
                new BlockPos(5, 64, 0)
        );
        final List<BlockPos> first = new ArrayList<>(scanOrder);
        FarmReach.nearestFirst(first, FEET);
        for (int i = 0; i < 10; i++) {
            final List<BlockPos> again = new ArrayList<>(scanOrder);
            FarmReach.nearestFirst(again, FEET);
            assertEquals("the pick changed with the bot standing still, which sends the aim chasing two"
                    + " blocks and presses neither button", first, again);
        }
        assertEquals("the tie did not keep the order the scan gave, so the pick depends on nothing",
                new BlockPos(2, 64, 0), first.get(0));
    }

    @Test
    public void theOrderIsMeasuredFromTheBlockThePlayerIsStandingIn() {
        // a body settling onto farmland moves by fractions of a block; the feet block does not, so the
        // order cannot be reordered by it. Asked from a neighbouring block, it can and should be.
        final List<BlockPos> candidates = Arrays.asList(
                new BlockPos(1, 64, 0),
                new BlockPos(2, 64, 0)
        );
        final List<BlockPos> fromHere = new ArrayList<>(candidates);
        FarmReach.nearestFirst(fromHere, FEET);
        assertEquals(new BlockPos(1, 64, 0), fromHere.get(0));

        final List<BlockPos> fromOneOver = new ArrayList<>(candidates);
        FarmReach.nearestFirst(fromOneOver, new BlockPos(2, 64, 0));
        assertEquals("the order is not measured from where the player is standing",
                new BlockPos(2, 64, 0), fromOneOver.get(0));
    }

    @Test
    public void anEmptyOrSingleCandidateListIsLeftAlone() {
        final List<BlockPos> empty = new ArrayList<>();
        FarmReach.nearestFirst(empty, FEET);
        assertTrue(empty.isEmpty());

        final BlockPos only = new BlockPos(7, 64, 7);
        final List<BlockPos> single = new ArrayList<>(Collections.singletonList(only));
        FarmReach.nearestFirst(single, FEET);
        assertEquals(Collections.singletonList(only), single);
    }

    @Test
    public void heightCountsAsDistance() {
        // a crop on a terrace is further away than one at the feet, and is worked later
        final List<BlockPos> candidates = new ArrayList<>(Arrays.asList(
                new BlockPos(0, 68, 0),
                new BlockPos(1, 64, 0)
        ));
        FarmReach.nearestFirst(candidates, FEET);
        assertEquals(new BlockPos(1, 64, 0), candidates.get(0));
    }

    /**
     * Every in-reach loop in the farm is ordered before it runs.
     * <p>
     * This is the assertion that keeps the fix from quietly coming apart: the loops are four copies of the
     * same shape, and a fifth written the same way would have the same defect with nothing failing. Counted
     * from the source, the way {@code PlaceSearchTest} counts the builder's searches, because the property
     * is about the code as written and there is no field to stand in here.
     * <p>
     * The shape it looks for is the one the farm itself uses - a {@code for (BlockPos pos : ...)} whose
     * first act is the reach test - and it deliberately does not match the loops that build the goal list,
     * which hand their blocks to a {@code GoalComposite} that the pathfinder prices, so their order was
     * never the thing being decided.
     */
    @Test
    public void everyInReachLoopIsOrderedNearestFirst() {
        final Path root = repoRoot();
        assumeTrue("the sources are not next to this test run", root != null);
        final Path farm = root.resolve("src/main/java/baritone/process/FarmProcess.java");
        assumeTrue("the farm is not next to this test run", Files.isRegularFile(farm));
        final List<String> lines = read(farm);

        final Pattern loop = Pattern.compile("^\\s*for \\(BlockPos pos : (\\w+)\\) \\{$");
        final Pattern ordered = Pattern.compile("^\\s*FarmReach\\.nearestFirst\\((\\w+), playerPos\\);$");

        final List<String> reachGated = new ArrayList<>();
        final List<String> ordering = new ArrayList<>();
        final List<Integer> orderedAt = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            final String trimmed = lines.get(i).trim();
            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                continue;
            }
            final Matcher order = ordered.matcher(lines.get(i));
            if (order.matches()) {
                ordering.add(order.group(1));
                orderedAt.add(i);
                continue;
            }
            final Matcher candidate = loop.matcher(lines.get(i));
            if (!candidate.matches() || i + 1 >= lines.size()) {
                continue;
            }
            // the farm's own reach test, which is what makes a loop an in-reach one
            if (lines.get(i + 1).contains("playerPos.distSqr(pos) > blockReachDistance")) {
                final String name = candidate.group(1);
                reachGated.add(name);
                assertTrue("the in-reach loop over `" + name + "` is not ordered nearest-first, so the farm"
                                + " reaches for the far end of what it can reach - the west-to-east sweep"
                                + " this order exists to stop",
                        orderedBefore(ordering, orderedAt, name, i));
            }
        }

        assertEquals("the farm's in-reach loops are not the four this was written against - if one was added"
                        + " or renamed, decide whether it needs the order too and say so here: " + reachGated,
                Arrays.asList("toBreak", "both", "openLog", "bonemealable"), reachGated);
        assertEquals("a list is ordered that no in-reach loop reads, or one is ordered twice: " + ordering,
                new HashSet<>(reachGated), new HashSet<>(ordering));
        assertEquals(reachGated.size(), ordering.size());
    }

    /**
     * The far half is still the pathfinder's choice.
     * <p>
     * The order only ever belonged to the in-reach half; the goals are handed to a composite and priced. If
     * the goal half is ever given a preference of its own, that is a decision about where the farm walks
     * and not about which block it reaches for, and it should not arrive by way of this class.
     */
    @Test
    public void theGoalHalfIsStillPricedByThePathfinder() {
        final Path root = repoRoot();
        assumeTrue("the sources are not next to this test run", root != null);
        final String source = String.join("\n", read(root.resolve("src/main/java/baritone/process/FarmProcess.java")));
        assertTrue("the farm's goals no longer go into a composite the pathfinder can price",
                source.contains("new GoalComposite(goalz"));
        assertTrue("the farm's in-reach order has been applied to the goal list",
                !source.contains("FarmReach.nearestFirst(goalz"));
    }

    private static boolean orderedBefore(List<String> ordering, List<Integer> orderedAt, String name, int loopLine) {
        for (int i = 0; i < ordering.size(); i++) {
            if (ordering.get(i).equals(name) && orderedAt.get(i) < loopLine) {
                return true;
            }
        }
        return false;
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
