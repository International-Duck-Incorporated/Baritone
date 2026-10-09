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
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * The other counting tests in this fork watch what the bot does to the game. This one watches where its
 * randomness comes from, because a rule that cannot be seeded cannot be reproduced, and a rule that cannot
 * be reproduced cannot be fixed. HUMANLIKE.md states the rule: "Everything with a random source takes it
 * through its constructor ... and the constructor with no argument is the only one that seeds itself from
 * the clock. Every machine in a test run draws from that run's seed, because a failure reported for a seed
 * has to be reproducible by running that seed."
 * <p>
 * Nothing enforced that. The rule was prose, so a new class could take the clock in a field initialiser and
 * the build would stay green while the timing it draws - a rescan interval, a place hold-off, a retarget
 * delay - quietly became a thing that could be seen misbehaving and never re-run. Every one of those
 * intervals is a humanlike interval, so this is the one count that is about the tests rather than about a
 * spectator: it protects the ability to answer "do that again" about any of the others.
 * <p>
 * The shape being pinned is narrow. {@code this(new Random());} as the body of a constructor with no
 * argument is the correct pattern: the clock is read there and nowhere else, and the constructor that takes
 * a {@code Random} beside it is the seam a test drives. Anything else that constructs a random source is
 * either seeded already - in which case it belongs on the list with the reason written next to it - or it
 * is a source with no seam, and it belongs on the list with that said plainly.
 */
public class RandomSourceTest {

    /**
     * The random sources that are not reached through an injectable constructor, and the reason each is on
     * the list. This map is a record of a gap, not a defence of one: eight of the nine entries say "no seam
     * yet", and the failure message below says the same, so the list shrinks as the gap is closed rather
     * than growing to cover for it.
     */
    private static final String[][] UNSEEDED = {
            {
                    "src/api/java/baritone/api/utils/AimScatter.java",
                    "no seam, and it does not need one: AimScatter is a static utility with no constructor to"
                            + " inject through, and its SALT is per-run by design (\"Drawn from the clock once,"
                            + " so two runs do not share a scatter\"). The seam is the other overload,"
                            + " point(BlockPos, long, Vec3, Vec3), whose javadoc says \"with the salt given, for"
                            + " the tests\", and AimScatterTest drives it with a fixed 42L. See"
                            + " theAimScatterSaltStillHasItsTestSeam, which fails if that overload goes."
            },
            {
                    "src/main/java/baritone/behavior/InventoryBehavior.java",
                    "no seam yet: cadenceRand is a field initialiser, so the inventory cadence cannot be driven"
                            + " from a seed. Needs a constructor parameter and a call-site change."
            },
            {
                    "src/main/java/baritone/pathing/path/PathExecutor.java",
                    "no seam yet: cadenceRand is a field initialiser, so the movement cadence - the sprint"
                            + " wind-up and hangover beats a path spends - cannot be driven from a seed."
            },
            {
                    "src/main/java/baritone/process/BuilderProcess.java",
                    "no seam yet: the Cadence.Run place hold-off is built with the clock in the field"
                            + " initialiser, so the builder's rhythm cannot be driven from a seed."
            },
            {
                    "src/main/java/baritone/process/FarmProcess.java",
                    "no seam yet: the Cadence.Repeat rescan is built with the clock in the field initialiser,"
                            + " so the farm's rescan interval cannot be driven from a seed."
            },
            {
                    "src/main/java/baritone/process/GetToBlockProcess.java",
                    "no seam yet: the Cadence.Repeat rescan is built with the clock in the field initialiser,"
                            + " so this process's rescan interval cannot be driven from a seed."
            },
            {
                    "src/main/java/baritone/process/MineProcess.java",
                    "no seam yet: the Cadence.Repeat rescan is built with the clock in the field initialiser,"
                            + " so the miner's rescan interval cannot be driven from a seed."
            },
            {
                    "src/main/java/baritone/utils/BlockBreakHelper.java",
                    "no seam yet: retargetRand is a field initialiser, so the retarget delay after an instant"
                            + " block cannot be driven from a seed. One construction site, in"
                            + " InputOverrideHandler, so this is the cheapest of the eight to close."
            },
            {
                    "src/main/java/baritone/utils/BlockPlaceHelper.java",
                    "no seam yet: placeRand is a field initialiser, so placement timing cannot be driven from a"
                            + " seed. One construction site, in InputOverrideHandler."
            },
    };

    /**
     * {@code this(new Random());}, the one place the clock may be read: a constructor with no argument
     * handing off to the constructor that takes a {@code Random}.
     */
    private static final Pattern DELEGATION = Pattern.compile("^this\\(new Random\\(\\)\\);$");

    /**
     * Any construction of a random source, seeded or not.
     */
    private static final Pattern CONSTRUCTION = Pattern.compile("new (Random|SecureRandom|SplittableRandom)\\(");

    private static final String AIM_SCATTER = "src/api/java/baritone/api/utils/AimScatter.java";

    private static final String[] SOURCE_ROOTS = {"src/main/java", "src/api/java", "src/launch/java"};

    @Test
    public void theOnlyRandomSourcesWithNoSeamAreTheOnesOnTheList() {
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
                    final String line = lines.get(i);
                    if (!CONSTRUCTION.matcher(line).find() || isComment(line)) {
                        continue;
                    }
                    if (DELEGATION.matcher(line.trim()).matches()) {
                        continue; // the clock read inside a constructor with no argument: the correct shape
                    }
                    found.computeIfAbsent(root.relativize(source).toString().replace('\\', '/'), k -> new TreeSet<>())
                            .add(i + 1);
                }
            }
        }
        final TreeSet<String> expected = new TreeSet<>();
        for (String[] site : UNSEEDED) {
            expected.add(site[0]);
        }
        assertEquals("a random source is being built somewhere that is neither the clock read inside a"
                        + " constructor with no argument nor on the list in this test. If it is seeded from"
                        + " something the caller gives, add it to the list with that said next to it. If it is"
                        + " not, it is a source with no seam: its intervals cannot be driven from a seed, so a"
                        + " failure in whatever it paces cannot be re-run, and that is what HUMANLIKE.md means"
                        + " by \"every machine in a test run draws from that run's seed\". Found: " + found,
                expected, found.keySet());
    }

    /**
     * A canary, and only a canary. It is tempting to also assert here that every file with the delegation
     * declares a constructor taking a {@code Random}, but that assertion cannot fail on code that compiles:
     * {@code this(new Random());} is a call, so javac rejects it with no such constructor, which is what
     * happened when the {@code Random} constructor was deleted from {@code TurnPace} and the build reported
     * "constructor TurnPace in class TurnPace cannot be applied to given types". Asserting it anyway would
     * buy nothing and cost a false positive on every constructor whose signature is wrapped across lines.
     * <p>
     * What this does assert is the thing a source-scanning test can genuinely lose: that there is still
     * something to scan. If the pattern is refactored away wholesale - the classes move package, the
     * delegation is replaced by a shared seeded source, the sources leave the tree - the first test goes
     * quiet rather than red, and a count that reports nothing is indistinguishable from a count that found
     * nothing wrong. That is the failure the sibling write-counting tests were built around, so it gets a
     * guard here too.
     */
    @Test
    public void theClockReadingPatternIsStillSomewhereForThisTestToBeCounting() {
        final Path root = repoRoot();
        assumeTrue("the sources are not next to this test run", root != null);
        int delegating = 0;
        for (String sourceRoot : SOURCE_ROOTS) {
            final Path dir = root.resolve(sourceRoot);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            for (Path source : javaFiles(dir)) {
                final boolean delegates = read(source).stream()
                        .map(String::trim)
                        .anyMatch(line -> DELEGATION.matcher(line).matches());
                if (delegates) {
                    delegating++;
                }
            }
        }
        assertTrue("nothing under " + String.join(", ", SOURCE_ROOTS) + " reads the clock through a constructor"
                        + " with no argument any more, so the count in the test above is scanning for a shape"
                        + " that no longer exists and would now pass against anything. Either the pattern was"
                        + " replaced - in which case this test needs rewriting around whatever replaced it, not"
                        + " deleting - or the sources are not where this test thinks they are.",
                delegating > 0);
    }

    @Test
    public void theAimScatterSaltStillHasItsTestSeam() {
        final Path root = repoRoot();
        assumeTrue("the sources are not next to this test run", root != null);
        final List<String> lines = read(root.resolve(AIM_SCATTER));
        assertTrue("AimScatter is on the list of random sources with no injectable constructor on the strength"
                        + " of the overload that takes the salt for the tests. That overload is gone, so the"
                        + " reason on the list is gone with it, and AimScatter now has a per-run salt nothing"
                        + " outside the class can reproduce - which would make every scatter result a one-off."
                        + " Put point(BlockPos, long, Vec3, Vec3) back, or take AimScatter off the list and give"
                        + " it a real seam.",
                lines.stream().anyMatch(line -> line.contains("point(BlockPos pos, long salt")));
    }

    private static boolean isComment(String line) {
        final String trimmed = line.trim();
        return trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*");
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
