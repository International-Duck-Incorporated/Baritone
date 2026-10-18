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
 * The other write-counting tests in this fork are about the hand's edges. {@code CameraWritesTest} counts
 * the files that may write the player's rotation, because a snap is the first thing somebody watching a
 * recording notices. {@code HandWritesTest} counts the files that may write the selected slot, because an
 * item appearing in the hand out of nowhere is that snap's twin. This is the third: the buttons.
 * <p>
 * Everything the mod can click - a block to break, a face to place against, a door, a farm, the hotbar and
 * the inventory window - is supposed to arrive at the game through one of two helpers, each of which spends
 * a reaction after the crosshair arrives and holds the click off while the item in the hand is not the one
 * that was decided on. That is the whole of "the click comes after the item is in the hand, never before,
 * and it comes a beat after the crosshair gets there" - see {@code Cadence.Settle}, {@code HotbarSwitch},
 * {@code InventorySwap} and HUMANLIKE.md - and it is worth exactly as much as the number of places that go
 * around it. A process that reaches for the game's own controller and clicks on the tick it decides to is a
 * click with no beat in it, and that is the kind of thing that gets added by somebody solving a different
 * problem: {@code HandWritesTest} exists because two bypasses of the slot rule were found by hand, and the
 * click rule has had no such count.
 * <p>
 * So the calls are counted, from the source, and every one of them has to be on the list below with the
 * reason written next to it.
 */
public class ButtonWritesTest {

    /**
     * The files that may press a mouse button or swing an arm, and the reason each is on the list. The map
     * is documentation as much as it is the assertion: a new entry is a new decision about how the hands
     * behave, and it should not be possible to make one without writing it down here.
     */
    private static final String[] ALLOWED = {
            // the interface the clicks are declared on: names, not presses
            "src/api/java/baritone/api/utils/IPlayerController.java",
            // the wrapper that forwards to the game's own controller: the only file that calls it to do
            // anything, so that everything above it can be answered without a game (BlockBreakHelper, below,
            // is the one other file that names it, to clear the controller's own destroy delay)
            "src/main/java/baritone/utils/player/BaritonePlayerController.java",
            // the left button: the beat, then the click, then the arm
            "src/main/java/baritone/utils/BlockBreakHelper.java",
            // the right button: the beat, then the click, then the arm
            "src/main/java/baritone/utils/BlockPlaceHelper.java",
            // eating: the one held-item use in the fork that is not aimed at a block, so it cannot go
            // through BlockPlaceHelper - that helper's whole condition is a block under the crosshair.
            // It reaches for the mod's own controller, like everything above it, and it waits for the
            // item in the hand the same way the helpers do: the slot is asked for through
            // InventoryBehavior and the bite is not started until the switch has landed, and food that
            // is already in the hand is the case that needs no switch at all. What it does not do is
            // reach into the hand of a task that is using it, which is the reason it is a behaviour and
            // not a process: a meal is never worth the slot a mine is in the middle of
            "src/main/java/baritone/behavior/EatBehavior.java",
            // the inventory swap: the beat behind InventorySwap, then the window click
            "src/main/java/baritone/behavior/InventoryBehavior.java",
            // the idle fidget and the arm that swings while the aim holds still: animation packets with no
            // click behind them, which is what makes them safe to send on somebody else's behalf
            "src/main/java/baritone/behavior/LookBehavior.java",
            // elytra flight, and neither of its two clicks is a click on a block. The firework is an item
            // use on a control loop with a deadline, gated on the hand being out of its own wind-up; the
            // auto-swap's window clicks are the queued sequence in InventoryTransaction, which waits for
            // the player's own inventory to be the menu in front, gives up a sequence that has not begun
            // with the window it was written for, and finishes one that has. The rest of the file is flight
            // control, which HUMANLIKE.md excuses from the other rules
            "src/main/java/baritone/process/elytra/ElytraBehavior.java",
    };

    /**
     * The lines those files have to keep, so that the list above cannot go on being true of a file that has
     * been changed into something else: a helper that has lost its beat is no longer a helper, and the file
     * that presses the buttons for a living would still be on the list while every click in it went out on
     * the tick it was asked for.
     */
    private static final String[][] SHAPES = {
            {"src/main/java/baritone/utils/BlockBreakHelper.java", "this.sightSettle.waiting("},
            {"src/main/java/baritone/utils/BlockPlaceHelper.java", "this.sightSettle.waiting("},
            // defence clicks on an entity, and neither helper can express that click (no block under the
            // crosshair, none to break) - the same case eating is on the list for. It is not on ALLOWED
            // because the click goes through the mod's own controller like everything above the wrapper,
            // but the beat is pinned here anyway: the swing is sent only once the head has already arrived
            // on the mob, and only once the weapon has come back up. Without these two lines the click is
            // a machine's click and this file is a licence for it.
            {"src/main/java/baritone/behavior/DefendBehavior.java", "baritone.getLookBehavior().hasArrivedAt(rotation)"},
            {"src/main/java/baritone/behavior/DefendBehavior.java", "player.getAttackStrengthScale(0.0F) < SWING_WHEN_RECOVERED"},
            // eating waits for the item that was asked for, and drops the attempt instead of pressing
            // at a hand that never arrived (the window bounds the wait, the budget bounds the retries)
            {"src/main/java/baritone/behavior/EatBehavior.java", "baritone.getInventoryBehavior().switchingHands()"},
            {"src/main/java/baritone/behavior/EatBehavior.java", "player.getInventory().getSelectedSlot() == targetSlot"},
            {"src/main/java/baritone/behavior/InventoryBehavior.java", "this.swap.tick()"},
            // the arm that swings at nothing: the swing itself, and no click in the file at all. On 26.2 the
            // swing is LocalPlayer#swing(InteractionHand), which sends ServerboundSwingPacket for us - there
            // is no ServerboundPunchPacket in this version, so the line pinned here is the call site.
            {"src/main/java/baritone/behavior/LookBehavior.java", "ctx.player().swing("},
            // the firework is not clicked until the hand has finished changing
            {"src/main/java/baritone/process/elytra/ElytraBehavior.java", "getInventoryBehavior().switchingHands()"},
            // the auto-swap's clicks wait for the player's own inventory: the window they were written for
            // is the question, and the transaction that does the waiting is asked whether it is still there
            {"src/main/java/baritone/process/elytra/ElytraBehavior.java", "ctx.player().containerMenu == ctx.player().inventoryMenu"},
            {"src/main/java/baritone/process/elytra/ElytraBehavior.java", "invTransaction.tick("},
            // the one place that talks to the game's controller rather than to the mod's own wrapper
            {"src/main/java/baritone/utils/player/BaritonePlayerController.java", "mc.gameMode."},
    };

    /**
     * The wiring of the wait itself, which is what the helpers' beats are worth: a helper that is never told
     * the hand is busy presses the button anyway, and a helper that is told and does not look is the same
     * thing with more code. The rule is one line in {@code InputOverrideHandler} and one condition in each
     * helper, and it is pinned here because it has already been bypassed twice by callers that clicked on
     * the tick they asked for an item (the elytra firework and the bucket fall - see {@code HandWritesTest}).
     */
    private static final String[][] HAND_READY_SHAPES = {
            // the gate, computed once a tick from the inventory's own wind-up
            {"src/main/java/baritone/utils/InputOverrideHandler.java", "final boolean handReady = !baritone.getInventoryBehavior().switchingHands();"},
            {"src/main/java/baritone/utils/InputOverrideHandler.java", "blockBreakHelper.tick(isInputForcedDown(Input.CLICK_LEFT), handReady)"},
            {"src/main/java/baritone/utils/InputOverrideHandler.java", "blockPlaceHelper.tick(isInputForcedDown(Input.CLICK_RIGHT), handReady)"},
            // and looked at by both helpers before anything is pressed
            {"src/main/java/baritone/utils/BlockBreakHelper.java", "public void tick(boolean isLeftClick, boolean handReady)"},
            {"src/main/java/baritone/utils/BlockBreakHelper.java", "isLeftClick && isBlockTrace && handReady"},
            {"src/main/java/baritone/utils/BlockPlaceHelper.java", "public void tick(boolean rightClickRequested, boolean handReady)"},
            {"src/main/java/baritone/utils/BlockPlaceHelper.java", "!handReady"},
    };

    /** Calls that press a button, in the spelling they are made in: the game's, vanilla's and the mod's. */
    private static final String[] BUTTONS = {
            "clickBlock(",
            "onPlayerDamageBlock(",
            "processRightClickBlock(",
            "processRightClick(",
            "windowClick(",
            // the manual animation packet. 26.2 sends ServerboundSwingPacket (from LocalPlayer#swing);
            // 26.3 replaced it with ServerboundPunchPacket. Watching the current version's name keeps a
            // future manual send from slipping in ungated. Matches nothing on 26.2 by construction.
            "ServerboundSwingPacket",
            ".swing(",
            "startDestroyBlock(",
            "continueDestroyBlock(",
            "stopDestroyBlock(",
            "destroyBlock(",
            "useItemOn(",
            "useItem(",
            "attack(",
            // the game's own controller, whichever method is called on it. The spellings above are the ones the
            // fork uses; the rest of its public hand methods (handleContainerInput, interact, releaseUsingItem,
            // dropItem, handlePickItemFromBlock, handleInventoryButtonClick, handlePlaceRecipe, piercingAttack)
            // are each one name away from a click with no beat in front of it, so the controller itself is
            // what is watched, and the two files that name it are the two on the list that do
            ".gameMode",
    };

    /** The one file on the list that is allowed to swing without pressing anything. */
    private static final String THE_FIDGET = "src/main/java/baritone/behavior/LookBehavior.java";

    private static final String[] SOURCE_ROOTS = {"src/main/java", "src/api/java", "src/launch/java"};

    @Test
    public void theOnlyHandsOnTheButtonsAreTheOnesOnTheList() {
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
                    if (pressesAButton(lines.get(i))) {
                        found.computeIfAbsent(root.relativize(source).toString().replace('\\', '/'), k -> new TreeSet<>())
                                .add(i + 1);
                    }
                }
            }
        }
        assertEquals("the mouse is pressed somewhere new: if this is a click with no beat in it, it skips the"
                        + " reaction, the settle and the wait for the item in the hand, and it belongs behind"
                        + " BlockBreakHelper or BlockPlaceHelper instead; if it is not a click, it belongs on"
                        + " the list in this test with the reason written next to it. Found: " + found,
                new TreeSet<>(List.of(ALLOWED)), found.keySet());
    }

    @Test
    public void theFilesThatPressTheButtonsStillHaveTheirBeatsInThem() {
        final Path root = repoRoot();
        assumeTrue("the sources are not next to this test run", root != null);
        for (String[] shape : SHAPES) {
            assertTrue("the file " + shape[0] + " no longer contains \"" + shape[1] + "\": it is on the list of"
                            + " places allowed to press the buttons because of that line, and without it the"
                            + " allowance is for a click with nothing in front of it",
                    read(root.resolve(shape[0])).stream().anyMatch(line -> line.contains(shape[1])));
        }
    }

    @Test
    public void theButtonsWaitForTheHandThatWasDecidedOn() {
        final Path root = repoRoot();
        assumeTrue("the sources are not next to this test run", root != null);
        for (String[] shape : HAND_READY_SHAPES) {
            assertTrue("the file " + shape[0] + " no longer contains \"" + shape[1] + "\": that line is the wait"
                            + " for the item in the hand - the gate computed from the inventory's wind-up and"
                            + " the question each helper asks before it presses - and without it a right click"
                            + " with the wrong item in hand is not a missed place, it is a bucket emptied",
                    read(root.resolve(shape[0])).stream().anyMatch(line -> line.contains(shape[1])));
        }
    }

    @Test
    public void theOnlySwingWithNoClickBehindItIsTheFidget() {
        final Path root = repoRoot();
        assumeTrue("the sources are not next to this test run", root != null);
        final List<String> fidget = read(root.resolve(THE_FIDGET));
        for (String button : BUTTONS) {
            if (button.equals(".swing(") || button.equals("ServerboundSwingPacket")) {
                continue; // a swing is what this file is allowed to do
            }
            assertTrue("the fidget's file " + THE_FIDGET + " has grown a \"" + button + "\" call: the swing"
                            + " sent from here goes out with no click behind it on purpose, and a real one in"
                            + " this file would be a click on the tick it was decided on",
                    fidget.stream().noneMatch(line -> line.contains(button)));
        }
    }

    private static boolean pressesAButton(String line) {
        final String trimmed = line.trim();
        if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
            return false;
        }
        for (String button : BUTTONS) {
            if (trimmed.contains(button)) {
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
