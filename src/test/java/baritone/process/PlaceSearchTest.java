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

import org.junit.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * The order the builder looks for somewhere to put the next block, and for a block to take out.
 * <p>
 * Two things have to hold at once and this is the only place they can be checked without a world: the search
 * has to consider exactly the same blocks it always did, and it has to consider the near ones first - a
 * person puts down the block they are standing next to, not the one at the far corner of what they can
 * reach. The order also has to be the same from one tick to the next, or the builder changes its mind about
 * which block it is about to place while it is walking up to it.
 * <p>
 * The break search walks the same columns and is pinned to them here, because it was left on the old nested
 * loops when the placement half moved to this order in {@code 32308640} - a wall came down from the west
 * edge of the box and went back up from where the person was standing, in the same tick.
 */
public class PlaceSearchTest {

    @Test
    public void everyColumnIsSearchedExactlyOnce() {
        final Set<String> seen = new HashSet<>();
        for (int[] column : PlaceSearch.columns()) {
            assertTrue("a column of the box is searched twice: " + column[0] + " " + column[1], seen.add(column[0] + "," + column[1]));
        }
        final int width = PlaceSearch.RADIUS * 2 + 1;
        assertEquals("the box searched is not the one the builder has always covered", width * width, seen.size());
    }

    @Test
    public void theSearchStartsUnderThePlayerAndWorksOutwards() {
        final int[][] columns = PlaceSearch.columns();
        // the block under the player's feet is looked at first, and from there the search only ever gets
        // further away: nothing in the middle of the box is favoured over something nearer
        assertEquals("the search does not start at the player", 0, columns[0][0]);
        assertEquals("the search does not start at the player", 0, columns[0][1]);
        int last = -1;
        for (int[] column : columns) {
            final int distance = column[0] * column[0] + column[1] * column[1];
            assertTrue("the search came back inwards: " + column[0] + " " + column[1], distance >= last);
            last = distance;
        }
        final int[] corner = columns[columns.length - 1];
        final int radius = PlaceSearch.RADIUS;
        assertEquals("the far corner of the box is not searched last", radius, Math.abs(corner[0]));
        assertEquals("the far corner of the box is not searched last", radius, Math.abs(corner[1]));
    }

    @Test
    public void columnsEquallyFarAwayKeepTheOrderTheyUsedToComeIn() {
        // the four corners are all the same distance out, and the order among things equally far away is all
        // that is left of the old nested loops - kept, because it is fixed
        final int[][] columns = PlaceSearch.columns();
        final int[][] corners = {{-5, -5}, {-5, 5}, {5, -5}, {5, 5}};
        for (int i = 0; i < corners.length; i++) {
            assertEquals(Arrays.toString(corners[i]), Arrays.toString(columns[columns.length - corners.length + i]));
        }
    }

    @Test
    public void theOrderIsTheSameEveryTimeItIsAskedFor() {
        // nothing about it is drawn or measured, so two calls in a row cannot disagree
        final String first = Arrays.deepToString(PlaceSearch.columns());
        for (int i = 0; i < 10; i++) {
            assertEquals(first, Arrays.deepToString(PlaceSearch.columns()));
        }
        assertEquals("the shared list is not the one the builder searches", first, Arrays.deepToString(PlaceSearch.COLUMNS));
    }

    /**
     * The break search walks the shared column order too.
     * <p>
     * This is the assertion that was missing when the placement half moved to this order and the break half
     * did not: nothing failed, because both orders consider the same blocks, and the only thing that changed
     * was which of them the builder reached for first. Counted from the source, the way
     * {@code CameraWritesTest} counts camera writes, because the property is about the code as written and
     * there is no world to break a block in here.
     */
    @Test
    public void theBreakSearchWalksTheSameColumnsAsThePlaceSearch() {
        final Path root = repoRoot();
        assumeTrue("the sources are not next to this test run", root != null);
        final Path builder = root.resolve("src/main/java/baritone/process/BuilderProcess.java");
        assumeTrue("the builder is not next to this test run", Files.isRegularFile(builder));
        final List<String> lines = read(builder);

        final List<String> breakSearch = new ArrayList<>();
        final List<String> placeSearch = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            final String trimmed = lines.get(i).trim();
            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                continue;
            }
            if (trimmed.contains("toBreakNearPlayer(")) {
                breakSearch.add(trimmed);
            }
            if (trimmed.contains("searchForPlacables(")) {
                placeSearch.add(trimmed);
            }
        }
        // each is declared once and called once: both halves of the builder's tick are still there to check
        assertEquals("the break search is not declared once and called once: " + breakSearch, 2, breakSearch.size());
        assertEquals("the place search is not declared once and called once: " + placeSearch, 2, placeSearch.size());

        // the box the break search walks is the shared one, and the loops it replaced are gone
        assertTrue("the break search has stopped walking the shared column order: a builder that reaches for"
                        + " the far west edge of what it can reach is the thing this order exists to stop",
                lines.stream().anyMatch(line -> line.contains("for (int[] column : PlaceSearch.COLUMNS)")));
        assertEquals("a raw west-edge-first loop is back in the builder: the box has to be anchored to the"
                        + " player's feet, not to the world's axes",
                0, lines.stream().filter(line -> !line.trim().startsWith("//")).filter(line -> line.contains("for (int dx = -5")).count());
    }

    /**
     * The break search considers exactly the blocks it always did.
     * <p>
     * The order moved; the box did not. Five blocks each way horizontally, as {@link PlaceSearch} covers, and
     * the vertical range the break search has always had - which is not the placement one, so it is checked
     * here rather than shared.
     */
    @Test
    public void theBreakSearchCoversTheSameBoxItAlwaysDid() {
        final Path root = repoRoot();
        assumeTrue("the sources are not next to this test run", root != null);
        final List<String> lines = read(root.resolve("src/main/java/baritone/process/BuilderProcess.java"));
        final String source = String.join("\n", lines);
        assertTrue("the break search no longer reaches one block below the feet, which is what"
                        + " breakFromAbove is for",
                source.contains("BREAK_BELOW = -1"));
        assertTrue("the break search no longer reaches five blocks above the feet",
                source.contains("BREAK_ABOVE = 5"));
        assertTrue("the block the player is supported by is no longer left alone",
                source.contains("dont mine what we're supported by"));
        // eleven by eleven columns, the box PlaceSearch covers and the one the break loops covered
        final int width = PlaceSearch.RADIUS * 2 + 1;
        assertEquals(width * width, PlaceSearch.COLUMNS.length);
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
