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
import java.util.List;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * The camera's writes are counted, because a snap is the first thing somebody watching a recording notices
 * (see {@code CameraWritesTest}). The hand has the same kind of rule and it is counted here: an item that
 * appears in the hand out of nowhere is that snap's twin, and the one place it is allowed is the beat in
 * {@link HotbarSwitch} - decide, press the key, click once the item is there.
 * <p>
 * Two bypasses of it have been found and fixed by hand, which is why this exists: Elytra's firework use
 * clicked on the tick it asked for a rocket, so it could fire whatever was in the old hand (fixed in
 * {@code 5e905d8e}), and the water-bucket fall selected its bucket on the tick it needed it. A rule that
 * has been forgotten twice is a rule that needs a test rather than another review.
 * <p>
 * The count is taken from the source, because there is nowhere else to take it from without a running
 * game, and because what is being asserted is a property of the code as written: that the selected slot is
 * written in the files below and nowhere else, that the one outside the beat is a fall's landing guarantee
 * rather than a way of getting an item into the hand, and that the beat's own write is still the press of a
 * key that was asked for. Comments are not writes; the rule is about code.
 */
public class HandWritesTest {

    /**
     * The files that may write the selected slot, and the reason each of them is on the list. The map is
     * the assertion as much as it is documentation: a new entry is a new decision about an item appearing
     * in the hand, and it should not be possible to make one without saying so here.
     */
    private static final String[] ALLOWED = {
            // the beat itself, pressing the key that was asked for (see InventoryBehavior#onTick)
            "src/main/java/baritone/behavior/InventoryBehavior.java",
            // a fall's landing guarantee: a bucket asked for on the way down (through the beat), and put
            // into the hand directly if the crosshair reaches the spot before the hand gets there. It is
            // the one write that is not a beat, and it exists because a beat spent here is spent on the
            // last ticks between the fall and the ground
            "src/main/java/baritone/pathing/movement/movements/MovementFall.java",
    };

    /**
     * The shapes those allow-listed writes have to keep, so that the list cannot go on being true of a file
     * that has been changed into something else: the fall has to keep asking for the bucket through the
     * beat, and writing the same bucket it asked for, and the beat has to keep writing the slot it decided
     * to press rather than any slot at all.
     */
    private static final String[][] SHAPES = {
            {"src/main/java/baritone/pathing/movement/movements/MovementFall.java", "getInventoryBehavior().hold(bucket)"},
            {"src/main/java/baritone/pathing/movement/movements/MovementFall.java", "setSelectedSlot(bucket)"},
            {"src/main/java/baritone/behavior/InventoryBehavior.java", "setSelectedSlot(press)"},
    };

    private static final String[] SOURCE_ROOTS = {"src/main/java", "src/api/java", "src/launch/java"};

    @Test
    public void theOnlyHandsOnTheItemInHandAreTheOnesOnTheList() {
        final Path root = repoRoot();
        assumeTrue("the sources are not next to this test run", root != null);
        final TreeMap<String, TreeSet<Integer>> found = new TreeMap<>();
        for (String sourceRoot : SOURCE_ROOTS) {
            final Path dir = root.resolve(sourceRoot);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            for (Path source : javaFiles(dir)) {
                final List<String> lines = read(source);
                for (int i = 0; i < lines.size(); i++) {
                    if (writesHand(lines.get(i))) {
                        found.computeIfAbsent(root.relativize(source).toString().replace('\\', '/'), k -> new TreeSet<>())
                                .add(i + 1);
                    }
                }
            }
        }
        assertEquals("the item in the hand is written somewhere new: if it appears there without the hand"
                        + " having been asked for it, that is the one thing the hand beat exists to stop, and"
                        + " if it does not, it belongs on the list in this test with the reason written next to"
                        + " it. Found: " + found,
                new TreeSet<>(List.of(ALLOWED)), found.keySet());
    }

    @Test
    public void theFallThatPutsItsOwnBucketInHandIsStillACatch() {
        final Path root = repoRoot();
        assumeTrue("the sources are not next to this test run", root != null);
        for (String[] shape : SHAPES) {
            assertTrue("the hold on " + shape[0] + " no longer has the shape it is allowed for: it should be"
                            + " \"" + shape[1] + "\", and a write that has become something else has stopped"
                            + " being the thing this test made an exception for",
                    read(root.resolve(shape[0])).stream().anyMatch(line -> line.contains(shape[1])));
        }
    }

    /** The keywords a write to the item in the hand can be spelled with. */
    private static boolean writesHand(String line) {
        final String trimmed = line.trim();
        if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
            return false;
        }
        return trimmed.contains("setSelectedSlot(")
                || trimmed.contains("ServerboundSetCarriedItemPacket")
                || trimmed.contains("selectedSlot =");
    }

    private static List<Path> javaFiles(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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
