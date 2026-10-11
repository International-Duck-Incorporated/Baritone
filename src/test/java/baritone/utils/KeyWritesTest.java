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
 * The other write-counting tests in this fork are about the eyes and the hands. {@code CameraWritesTest}
 * counts the files that may write the player's rotation, because a snap is the first thing somebody
 * watching a recording notices. {@code HandWritesTest} counts the files that may change the selected slot,
 * because an item appearing in the hand out of nowhere is that snap's twin. {@code ButtonWritesTest} counts
 * the files that may press a mouse button, because a click on the tick it was decided on skips the settle,
 * the reaction and the wait for the item in the hand. {@code ClickGatesTest} asks every click which block
 * it is for. This is the fifth: the keys.
 * <p>
 * Everything this fork has written about walking is a rule about the key stream, and every one of them
 * lives behind this surface: {@code KeyBeat} spends a beat between a direction and its opposite,
 * {@code HeadFirst} keeps the feet off until the head has come round, {@code CarefulFooting} holds the
 * crouch across a movement seam, {@code SprintWindup} walks before it runs, {@code atAPace} decides which
 * of those a tick is allowed to spend. All of it arrives at the game through one class: the four keys, the
 * jump, the sneak and the sprint become the game's own {@code Input} record and move vector in
 * {@code PlayerMovementInput#tick}, out of {@code InputOverrideHandler}. That is worth exactly as much as
 * the number of places that go around it - one line of {@code ctx.player().input.keyPresses = ...} in a
 * process solving a different problem is a bot whose walk has no beat in it, and nothing in this suite
 * would notice.
 * <p>
 * So the writes are counted, from the source, and every one of them has to be on the list below with the
 * reason written next to it. The list is documentation as much as it is the assertion: a new entry is a
 * new decision about how the keys behave, and it should not be possible to make one without writing it
 * down here. The shapes are what keep the entries from going stale - a file on the list because of the
 * rule it applies is no longer that file once the rule is gone.
 */
public class KeyWritesTest {

    /**
     * The files that may write the player's own movement input, and the reason each is on the list.
     */
    private static final String[] ALLOWED = {
            // the file that takes the keys and gives them back: the input object itself is written here,
            // twice, and both writes are the edges of Baritone's control rather than a key press - the
            // override goes in when a path is running and the keyboard comes back when it is not
            "src/main/java/baritone/utils/InputOverrideHandler.java",
            // the keys themselves: the one place the four walking keys, the jump, the sneak and the sprint
            // become the game's Input record and move vector, built out of the handler - the beat between
            // two opposite directions, the stance held across a movement seam and the forced inputs are all
            // applied inside this write and nowhere else
            "src/main/java/baritone/utils/PlayerMovementInput.java",
            // the sprint flag, not the sprint key: letting go of control does not make the player stop
            // sprinting, so the flag is cleared when nothing wants the run - which is the tick after the
            // key's own trailing is spent (Cadence.sprintHangover, through shouldSprintNextTick below)
            "src/main/java/baritone/pathing/path/PathExecutor.java",
    };

    /**
     * The lines those files have to keep, so that the list above cannot go on being true of a file that has
     * been changed into something else: a keys class that no longer asks the handler for the beat is a
     * direct write with a nicer name, and it would still be on the list while every key in it went down on
     * the tick it was asked for.
     */
    private static final String[][] SHAPES = {
            // the override goes in, and the beat and the stance start fresh because somebody else had the
            // keys until this tick
            {"src/main/java/baritone/utils/InputOverrideHandler.java", "ctx.player().input = new PlayerMovementInput(this);"},
            // and comes back out: the person's own keyboard, handed the game's own way
            {"src/main/java/baritone/utils/InputOverrideHandler.java", "ctx.player().input = new KeyboardInput(ctx.minecraft().options);"},
            // the four walking keys through the beat, not straight from the movement
            {"src/main/java/baritone/utils/PlayerMovementInput.java", "handler.beatTheKeys(new KeyBeat.Keys("},
            // the jump, the sneak and the sprint through the handler rather than read from the keyboard
            {"src/main/java/baritone/utils/PlayerMovementInput.java", "handler.isInputForcedDown(Input.JUMP)"},
            {"src/main/java/baritone/utils/PlayerMovementInput.java", "handler.isInputForcedDown(Input.SPRINT)"},
            // the movement's own sneak ask, without which every crouch a movement requests - the magma
            // walk, the ladder, the movement's bridge - is silently gone while the count still passes
            {"src/main/java/baritone/utils/PlayerMovementInput.java", "handler.isInputForcedDown(Input.SNEAK)"},
            // the crouch a person keeps while the footing is not certain, which is why the sneak the game
            // sees is this OR that and not either one of them
            {"src/main/java/baritone/utils/PlayerMovementInput.java", "handler.isCrouchingForTheFooting()"},
            // the write itself: one record, built from what was decided above it
            {"src/main/java/baritone/utils/PlayerMovementInput.java", "this.keyPresses = new net.minecraft.world.entity.player.Input("},
            // the flag write is the answer to the hangover-aware question, and the question is the hangover
            // the held use button: a lease on the game's own use key, inside the file that owns the keys,
            // asked for by the one sustained use in the fork (EatBehavior, eating) and spent by the tick
            // that reads it, so a holder that stops asking cannot leave the button down. It is asked for
            // rather than switched because clearAllKeys above does not lift it - that runs on every
            // movement of a walk, and a meal eaten on the way somewhere must not come off the mouth
            {"src/main/java/baritone/utils/InputOverrideHandler.java", "this.useButtonAskedFor = false;"},
            {"src/main/java/baritone/utils/InputOverrideHandler.java", "ctx.minecraft().options.keyUse.setDown(true)"},
            {"src/main/java/baritone/pathing/path/PathExecutor.java", "sprintNextTick = shouldSprintNextTick();"},
            {"src/main/java/baritone/pathing/path/PathExecutor.java", "if (this.sprintHangover > 0 && canTrailOffSprint()) {"},
    };

    /**
     * Where the player's own key state is <i>read</i>, pinned as reads: if either of these turns into a
     * write, the count above finds it, and these assertions fail beside it. The mixin is not a third write
     * site - it answers vanilla's own sprint question without touching the record - but it is the second
     * interception of the sprint key, and a reader looking for where the sprint doctrine can be lost
     * without a test seeing it should be sent here rather than left to grep for it.
     */
    private static final String[][] READERS = {
            {"src/main/java/baritone/behavior/LookBehavior.java", "final net.minecraft.world.entity.player.Input keys = ctx.player().input.keyPresses;"},
            {"src/launch/java/baritone/launch/mixins/MixinClientPlayerEntity.java", "private boolean redirectSprintInput(final Input instance) {"},
            {"src/launch/java/baritone/launch/mixins/MixinClientPlayerEntity.java", "baritone.getGameEventHandler().onPlayerSprintState(event);"},
    };

    /**
     * Writes to the player's own movement input, in the spellings they are made in: the game's record and
     * move vector, the input object itself, the keyboard it is handed back to, and the ways to reach
     * around all of it (the key-mapping class's own static entry points, a mapping's down-state, and the
     * player's own movement flags). {@code KeyMapping.set} and {@code KeyMapping.click} are public statics
     * with no caller in the fork today - they are watched for the same reason the count exists: a key held
     * or clicked through vanilla's own plumbing has gone around every rule about the key stream, and the
     * next reader should find it watched rather than discover it. {@code setAll} and {@code releaseAll}
     * are the same entry points in bulk, and so are the two that act on the toggle mappings (the sprint and
     * sneak toggles): {@code resetToggleKeys} is a bulk {@code setDown(false)} and
     * {@code restoreToggleStatesOnScreenClosed} a bulk {@code setDown(true)}. {@code makeJump} writes the
     * jump bit of the record from a method call, with no assignment at the call site to find, and
     * {@code consumeClick} takes a click the person made before the game has seen it. These four are
     * spelled by their bare names: the names are unique, and a static import cannot dodge them. Only the
     * binding-table rewrites ({@code resetMapping}, {@code setKey}) are configuration rather than a key,
     * and are left out.
     */
    private static final String[] WRITES = {
            "keyPresses =",
            "moveVector =",
            ".input = ",
            "new KeyboardInput(",
            "new PlayerMovementInput(",
            "setSprinting(",
            "setShiftKeyDown(",
            ".setDown(",
            "KeyMapping.set(",
            "KeyMapping.click(",
            "KeyMapping.setAll(",
            "KeyMapping.releaseAll(",
            "resetToggleKeys(",
            "restoreToggleStatesOnScreenClosed(",
            "makeJump(",
            "consumeClick(",
    };

    /**
     * What the player's own input object offers to read, in the spellings it is read with: the record, and
     * the two questions the game asks of it - the move vector and whether it has a forward impulse. The
     * census in {@link #theKeysAreReadWhereTheyAreReadAndNowhereElse} is for a file naming any of them.
     */
    private static final String[] READ_SPELLINGS = {
            "input.keyPresses",
            ".getMoveVector(",
            ".hasForwardImpulse(",
    };

    private static final String[] SOURCE_ROOTS = {"src/main/java", "src/api/java", "src/launch/java"};

    @Test
    public void theOnlyHandsOnTheKeysAreTheOnesOnTheList() {
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
                    if (writesTheKeys(lines.get(i))) {
                        found.computeIfAbsent(root.relativize(source).toString().replace('\\', '/'), k -> new TreeSet<>())
                                .add(i + 1);
                    }
                }
            }
        }
        assertEquals("the player's own keys are written somewhere new: if this is a key going down or up"
                        + " outside the handler, it skips the beat, the stance and the wind-up - every rule"
                        + " about how a walk looks is a rule about this stream - and it belongs behind"
                        + " InputOverrideHandler instead; if it is not a key, it belongs on the list in this"
                        + " test with the reason written next to it. Found: " + found,
                new TreeSet<>(List.of(ALLOWED)), found.keySet());
    }

    @Test
    public void theFilesThatWriteTheKeysStillHoldTheRulesInThem() {
        final Path root = repoRoot();
        assumeTrue("the sources are not next to this test run", root != null);
        for (String[] shape : SHAPES) {
            assertTrue("the file " + shape[0] + " no longer contains \"" + shape[1] + "\": it is on the list of"
                            + " places allowed to write the player's keys because of that line, and without it"
                            + " the allowance is for a key with nothing in front of it",
                    read(root.resolve(shape[0])).stream().anyMatch(line -> line.contains(shape[1])));
        }
    }

    @Test
    public void theKeysAreReadWhereTheyAreReadAndNowhereElse() {
        final Path root = repoRoot();
        assumeTrue("the sources are not next to this test run", root != null);
        for (String[] reader : READERS) {
            assertTrue("the file " + reader[0] + " no longer contains \"" + reader[1] + "\": that line is what"
                            + " it is on this list for - the two places the player's own key state is read in"
                            + " the fork, both of which the count above would find if either became a write",
                    read(root.resolve(reader[0])).stream().anyMatch(line -> line.contains(reader[1])));
        }
        // and the other half of the name above: a file that names the player's own key state is a reader
        // whatever it does with it, so a reader the list does not name fails here. Pinned as the same text
        // the count reads, and generous about where a known file puts it - the mixin names the record as an
        // argument rather than through the player, so only the player-side shape is asked for. The record is
        // one spelling of that; the move vector and the forward impulse are the other two (READ_SPELLINGS).
        final TreeSet<String> unknown = new TreeSet<>();
        for (String sourceRoot : SOURCE_ROOTS) {
            final Path dir = root.resolve(sourceRoot);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            for (Path source : javaFiles(dir)) {
                final String relative = root.relativize(source).toString().replace('\\', '/');
                boolean known = false;
                for (String[] reader : READERS) {
                    if (reader[0].equals(relative)) {
                        known = true;
                        break;
                    }
                }
                if (known) {
                    continue;
                }
                for (String line : read(source)) {
                    if (namesTheInput(line)) {
                        unknown.add(relative);
                        break;
                    }
                }
            }
        }
        assertEquals("the player's own key state is read in a file the readers list does not name: a reader"
                + " is a decision about the key stream like any other, and it belongs on READERS with its"
                + " reason", new TreeSet<>(), unknown);
    }

    @Test
    public void theKeysFileDoesNotAskTheKeyboard() {
        final Path root = repoRoot();
        assumeTrue("the sources are not next to this test run", root != null);
        final List<String> keysFile = read(root.resolve("src/main/java/baritone/utils/PlayerMovementInput.java"));
        assertTrue("PlayerMovementInput reads a key mapping directly: the keys it presses are the ones"
                        + " InputOverrideHandler was asked for this tick, and a key read from the keyboard"
                        + " here is a key no rule of this fork has seen",
                keysFile.stream().noneMatch(line -> line.contains("options.key")));
    }

    private static boolean namesTheInput(String line) {
        for (String spelling : READ_SPELLINGS) {
            if (line.contains(spelling)) {
                return true;
            }
        }
        return false;
    }

    private static boolean writesTheKeys(String line) {
        for (String write : WRITES) {
            if (line.contains(write)) {
                return true;
            }
        }
        return false;
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
