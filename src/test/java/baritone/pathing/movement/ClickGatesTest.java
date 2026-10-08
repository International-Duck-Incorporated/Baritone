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

package baritone.pathing.movement;

import org.junit.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Which block a click is for.
 * <p>
 * {@code CameraWritesTest} counts the files that may write the player's rotation, {@code HandWritesTest}
 * counts the files that may write the selected slot and {@code ButtonWritesTest} counts the files that may
 * press a button at all. None of them asks the last question about a click: not who may send it, but which
 * block it is for. Every click in the mod acts on a block - a block to break with a reason, a face to place
 * against, a door to open, a chest to open, a crop to harvest - and the aim is somewhere else on the tick
 * the click is first asked for, because the aim is being moved to the block at that moment. The click goes
 * out one or two ticks later, on the settle every button already pays, by which time the crosshair is on the
 * block it was sent to - unless it is not, and then it lands on whatever the crosshair crosses on the way
 * there. That is not a hypothetical: the door branch of {@code MovementTraverse} put its button down the
 * tick the door was noticed and clicked the wall beside the doorway; the pillar put its button down as it
 * turned towards the block in its feet.
 * <p>
 * So the rule this test counts is: a click is asked for with the crosshair already on the block it is for,
 * or with the hand arrived at the point it was sent to, or it says in the code why neither question was
 * asked. The gates are the ones the mod actually uses - {@code ctx.isLookingAt(block)},
 * {@code LookBehavior#hasArrivedAt(rotation)}, {@code PlaceResult.READY_TO_PLACE} (which is the ray trace
 * landing on the right block or its attachment), {@code ctx.getSelectedBlock()}, and, for an item that only
 * acts on one face of the block, the trace's face ({@code ctx.isLookingAt(block, face)},
 * {@code RayTraceUtils#hitsFaceTowards}) - plus the two clicks that are not acts on a block at all: breaking
 * out of a wall the body is stuck inside, and opening a block the process has already picked out by ray.
 * <p>
 * The count is taken from the source because that is where it lives: the property being asserted is about
 * the code as written, and none of it can be run without a game. A statement that has no gate carries the
 * sentence {@code ungated click:} with its reason, which is what the two places that need it do - the blind
 * break {@code Movement#prepared} falls back to when no face of the block can be seen, and the bucket taken
 * out of the water the player is standing in. Comments do not count as gates, and a new click anywhere in
 * these two layers fails this test until it is either gated or has said why not.
 */
public class ClickGatesTest {

    /**
     * Where clicks are asked for. The movements (and the two helpers the processes press through are out of
     * scope: they are the buttons themselves, not the decisions about what to press them for) and the
     * processes that drive them.
     */
    private static final String[] ROOTS = {
            "pathing/movement",
            "process",
    };

    /**
     * A click, however it is asked for: the movement's own state or a process's forced state.
     */
    private static final Pattern CLICK = Pattern.compile("(setInput|setInputForceState)\\(\\s*Input\\.CLICK_(LEFT|RIGHT)\\s*,\\s*true\\s*\\)");

    /**
     * The questions that count as a gate, each one a way of saying the click is for the block the hand is
     * already on: the crosshair is on that block, or the hand has arrived at the point it was sent to, or
     * the ray trace has confirmed the face to place against, or the process is clicking the block under the
     * crosshair it picked out, or the body is inside a wall and mining its way out. A gate has to be asked
     * in the same statement as the click - the preceding few lines - so a click cannot be gated by a
     * question asked about something else.
     */
    private static final String[] GATES = {
            "ctx.isLookingAt(",
            "hasArrivedAt(",
            "READY_TO_PLACE",
            "ctx.getSelectedBlock(",
            "knownLocations.contains(",
            "isInWall(",
            "hitsFace(",
            "hitsFaceTowards(",
    };

    /**
     * A click that has none of the above says why, in the code, where it is. The sentence is deliberately
     * long enough not to appear by accident and short enough to be written in a line.
     */
    private static final String MARKER = "ungated click:";

    /**
     * How far above a click its gate may be. The question and the click are one statement in every one of
     * the gated sites today, so this is generous; its point is that the gate cannot be in another statement.
     */
    private static final int WINDOW = 6;

    /**
     * Where the clicks are and how many of them there are, as the assertion as much as the documentation: a
     * click added, moved or removed anywhere in these layers has to be added here, next to the site it came
     * to or left. Paths are relative to {@code src/main/java/baritone/}.
     */
    private static final String[] CLICK_COUNTS = {
            "pathing/movement/Movement.java 3",
            "pathing/movement/movements/MovementAscend.java 1",
            "pathing/movement/movements/MovementFall.java 2",
            "pathing/movement/movements/MovementParkour.java 1",
            "pathing/movement/movements/MovementPillar.java 2",
            "pathing/movement/movements/MovementTraverse.java 6",
            "process/BackfillProcess.java 1",
            "process/BuilderProcess.java 2",
            "process/FarmProcess.java 4",
            "process/GetToBlockProcess.java 1",
            "process/MineProcess.java 1",
    };

    @Test
    public void everyClickIsForTheBlockTheHandIsOn() {
        final Path root = repoRoot();
        assumeTrue("the sources are not next to this test run", root != null);
        final List<String> violations = new ArrayList<>();
        for (Site site : sites(root)) {
            if (gateIn(site) || markerIn(site)) {
                continue;
            }
            violations.add(site.where() + ": " + site.text().trim());
        }
        assertEquals("clicks asked for with neither the crosshair nor the hand on the block they are for, "
                + "and no sentence saying why not", List.of(), violations);
    }

    @Test
    public void theClicksAreWhereTheyWereLastCounted() {
        final Path root = repoRoot();
        assumeTrue("the sources are not next to this test run", root != null);
        final TreeMap<String, Integer> found = new TreeMap<>();
        for (Site site : sites(root)) {
            found.merge(site.file(), 1, Integer::sum);
        }
        final TreeMap<String, Integer> expected = new TreeMap<>();
        for (String row : CLICK_COUNTS) {
            final int space = row.lastIndexOf(' ');
            expected.put(row.substring(0, space), Integer.parseInt(row.substring(space + 1)));
        }
        assertEquals("the clicks have moved: every click in the movement and process layers is a decision "
                + "about what a button is for, so it has a count beside it and a gate in front of it", expected, found);
    }

    /**
     * Where a block is asked for *and* a face of it, per file and per shape of the question: the calls that
     * name a face, asked either of the trace under the crosshair ({@code isLookingAt-face}, an
     * {@code isLookingAt} with a second argument) or of a trace the caller took itself ({@code hitsFace},
     * {@code hitsFaceTowards}). Where an item only acts on one face of a block, the block is not the whole
     * question: a seed goes into the top of a farmland and a cocoa bean onto the side of a log, a block
     * goes against the side the trace is on, and a click asked for on the wrong face of the right block is
     * a packet that lands on a face that does not take the item - an arm swinging at nothing, and the tick
     * spent standing there for it. That is what these sites are, and why they are pinned: the builder's
     * candidate and its placement click, the farmer's two plantings and the two traces it takes before
     * aiming at them, the movement layer's own placement search, and the bridge's step-back. The breaking
     * clicks and the bone meal are deliberately not here - neither has a face to need, and both say so -
     * and a site that goes back to the block question alone fails with the file and the call it lost.
     * Paths are relative to {@code src/main/java/baritone/}.
     */
    private static final String[] FACE_QUESTIONS = {
            "pathing/movement/MovementHelper.java hitsFaceTowards 1",
            "pathing/movement/movements/MovementTraverse.java hitsFaceTowards 1",
            "process/BuilderProcess.java hitsFace 1",
            "process/BuilderProcess.java isLookingAt-face 1",
            "process/FarmProcess.java hitsFace 2",
            "process/FarmProcess.java isLookingAt-face 2",
    };

    @Test
    public void theFaceQuestionIsAskedWhereItMatters() {
        final Path root = repoRoot();
        assumeTrue("the sources are not next to this test run", root != null);
        final TreeMap<String, Integer> expected = new TreeMap<>();
        final List<String> files = new ArrayList<>();
        for (String row : FACE_QUESTIONS) {
            final String[] parts = row.split(" ");
            expected.put(parts[0] + " " + parts[1], Integer.parseInt(parts[2]));
            if (!files.contains(parts[0])) {
                files.add(parts[0]);
            }
        }
        final TreeMap<String, Integer> found = new TreeMap<>();
        for (String file : files) {
            final Path source = root.resolve("src/main/java/baritone").resolve(file);
            final String text;
            try {
                text = Files.readString(source);
            } catch (IOException e) {
                throw new UncheckedIOException("could not read " + source, e);
            }
            for (String question : List.of("isLookingAt-face", "hitsFace", "hitsFaceTowards")) {
                final int count = questionsIn(text, question);
                if (count > 0) {
                    found.put(file + " " + question, count);
                }
            }
        }
        assertEquals("the face questions have moved: asking a trace for the block of a click that acts on "
                + "one face of it is not asking the whole question, and the sites that ask it are pinned here",
                expected, found);
    }

    /**
     * How many times a file asks one shape of the question. The two-argument form is told from the
     * one-argument one by parens rather than by a regular expression: the first argument nests
     * ({@code toPlace.get().placeAgainst}), and the comma that matters is the one at the call's own depth.
     */
    private static int questionsIn(String source, String question) {
        if (!"isLookingAt-face".equals(question)) {
            return occurrences(source, question + "(");
        }
        int found = 0;
        final String call = "isLookingAt(";
        int at = source.indexOf(call);
        while (at >= 0) {
            int depth = 1;
            boolean comma = false;
            for (int i = at + call.length(); i < source.length() && depth > 0; i++) {
                final char c = source.charAt(i);
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                } else if (c == ',' && depth == 1) {
                    comma = true;
                }
            }
            if (comma) {
                found++;
            }
            at = source.indexOf(call, at + call.length());
        }
        return found;
    }

    private static int occurrences(String source, String needle) {
        int found = 0;
        int at = source.indexOf(needle);
        while (at >= 0) {
            found++;
            at = source.indexOf(needle, at + needle.length());
        }
        return found;
    }

    /**
     * One click, with enough of what is around it to be judged: the file it is in and the lines above it.
     */
    private record Site(String file, int line, String text, List<String> above) {

        String where() {
            return file + ":" + line;
        }
    }

    private static List<Site> sites(Path root) {
        final List<Site> sites = new ArrayList<>();
        final Path baritone = root.resolve("src/main/java/baritone");
        for (String relative : ROOTS) {
            final Path dir = baritone.resolve(relative);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> stream = Files.walk(dir)) {
                for (Path file : stream.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                    final List<String> lines = Files.readAllLines(file);
                    for (int i = 0; i < lines.size(); i++) {
                        if (!CLICK.matcher(lines.get(i)).find()) {
                            continue;
                        }
                        final int from = Math.max(0, i - WINDOW);
                        sites.add(new Site(baritone.relativize(file).toString(), i + 1, lines.get(i),
                                lines.subList(from, i + 1)));
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException("could not read the sources under " + dir, e);
            }
        }
        assertTrue("no clicks found at all - the scan is looking in the wrong place", !sites.isEmpty());
        return sites;
    }

    private static boolean gateIn(Site site) {
        for (String line : site.above()) {
            for (String gate : GATES) {
                if (line.contains(gate)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean markerIn(Site site) {
        for (String line : site.above()) {
            if (line.contains(MARKER)) {
                return true;
            }
        }
        return false;
    }

    private static Path repoRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 4 && dir != null; i++) {
            if (Files.isDirectory(dir.resolve("src/main/java/baritone"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        return null;
    }
}
