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

package baritone.behavior.look;

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
 * Everything about the aim can be smoothed and still leave one rotation in the mod that is written straight
 * onto the player: the snap is what somebody watching a recording notices before anything else, and it is
 * the one thing no amount of easing anywhere else can excuse. So the writes are counted.
 * <p>
 * The count is taken from the source, because there is no other place to take it from without a running
 * game, and because what is being asserted is a property of the code as written: that the player's own
 * rotation is written in the four files below and nowhere else, that two of them write it as an event the
 * aim asked for and put the player's own angles back afterwards, and that the builder's transient - the one
 * place that writes an angle the aim did not ask for - puts back exactly what it found.
 * <p>
 * The writes in {@code LookBehavior} are the aim's own, and the aim cannot snap its way past
 * {@code AimDynamics}; that half is {@code AimDynamicsTest} and {@code HumanlikeLookFuzzTest}. This is the
 * other half: that nothing else has a way to move the camera at all.
 */
public class CameraWritesTest {

    /**
     * The files that may write the player's rotation, and the reason each of them is on the list. The map is
     * the assertion as much as it is documentation: a new entry is a new decision about a snap, and it
     * should not be possible to make one without saying so here.
     */
    private static final String[] ALLOWED = {
            // the aim, the glance and the sway, all of them eased or a fraction of a degree: AimDynamics
            "src/main/java/baritone/behavior/LookBehavior.java",
            // the builder has to know which way round a block would be placed, and the game reads that off
            // the player, so one tick's worth of angle is borrowed and given back inside the same call
            "src/main/java/baritone/process/BuilderProcess.java",
            // the bridges the aim is applied through: the event's angles, and back to the original in POST
            "src/launch/java/baritone/launch/mixins/MixinEntity.java",
            // elytra flight, which is a control loop and is excused in HUMANLIKE.md for that reason
            "src/launch/java/baritone/launch/mixins/MixinLivingEntity.java",
    };

    /**
     * The shapes those allow-listed writes have to keep, so that the list above cannot go on being true of a
     * file that has been changed into something else: the builder's restore, and the two events' originals.
     */
    private static final String[][] SHAPES = {
            {"src/main/java/baritone/process/BuilderProcess.java", "ctx.player().setYRot(originalYaw);"},
            {"src/main/java/baritone/process/BuilderProcess.java", "ctx.player().setXRot(originalPitch);"},
            {"src/launch/java/baritone/launch/mixins/MixinEntity.java", "this.yRot = this.motionUpdateRotationEvent.getOriginal().getYaw();"},
            {"src/launch/java/baritone/launch/mixins/MixinLivingEntity.java", "this.setYRot(this.elytraRotationEvent.getOriginal().getYaw());"},
    };

    private static final String[] SOURCE_ROOTS = {"src/main/java", "src/api/java", "src/launch/java"};

    @Test
    public void theOnlyHandsOnTheCameraAreTheOnesOnTheList() {
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
                    if (writesRotation(lines.get(i))) {
                        found.computeIfAbsent(root.relativize(source).toString().replace('\\', '/'), k -> new TreeSet<>())
                                .add(i + 1);
                    }
                }
            }
        }
        assertEquals("the player's rotation is written somewhere new: if this is a snap, it is the one thing"
                        + " this fork exists to not do, and if it is not, it belongs on the list in this test"
                        + " with the reason written next to it. Found: " + found,
                new TreeSet<>(List.of(ALLOWED)), found.keySet());
    }

    @Test
    public void theTwoFilesThatBorrowAnAngleGiveItBack() {
        final Path root = repoRoot();
        assumeTrue("the sources are not next to this test run", root != null);
        for (String[] shape : SHAPES) {
            assertTrue("the borrowed-angle file " + shape[0] + " no longer puts back what it found: the write"
                            + " is allowed because it is invisible, and it is only invisible while this line is there",
                    read(root.resolve(shape[0])).stream().anyMatch(line -> line.contains(shape[1])));
        }
    }

    /** The keywords a write to the player's own rotation can be spelled with. */
    private static boolean writesRotation(String line) {
        return line.contains("setYRot(")
                || line.contains("setXRot(")
                || line.contains("yRot =")
                || line.contains("xRot =");
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
