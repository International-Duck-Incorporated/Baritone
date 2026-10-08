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
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * The two places the crouch of a crossing has to be wired, counted from the source, because neither can be
 * reached without a running game.
 * <p>
 * A crossing is walked crouched, so it has to cost what a crouched walk costs: the side-place branch of
 * {@code MovementTraverse#cost} was returning a plain walk price for a block the bot walks crouched, while
 * its own {@code updateState} sets the crouch for both the side-place and the backplace path - and the
 * backplace branch has charged the crouch since it was written. A price that does not describe the path is
 * a pathfinder that picks routes it cannot walk in the time it thinks, which is the one thing the
 * behaviour work here is not allowed to do. If the crouch is removed from the walking side one day, the
 * price has to come off with it, and this fails until the two are decided together.
 * <p>
 * The ask is the same kind of pin: the crouch has to be asked for on the tick the block goes in *and* on
 * the ticks the feet walk onto it, because every key is cleared and re-decided from scratch every tick
 * ({@code Movement.update()}) and the movement boundary is exactly where the walk onto a just-placed block
 * happens. Asked on one side of that seam only, the bot stands up in the middle of its own crossing, which
 * is the thing this whole change is for.
 */
public class BridgeCrossingTest {

    private static final String SNEAK_MULTIPLIER = "SNEAK_ONE_BLOCK_COST / WALK_ONE_BLOCK_COST";

    @Test
    public void bothBridgePricesChargeTheCrouchTheBotWalksWith() {
        final List<String> lines = code("src/main/java/baritone/pathing/movement/movements/MovementTraverse.java");
        final List<String> charging = lines.stream().filter(line -> line.contains(SNEAK_MULTIPLIER)).collect(Collectors.toList());
        assertEquals("exactly the two bridge paths charge the crouch - the backplace path and the"
                        + " side-place path - and this is what found the second one missing: " + charging,
                2, charging.size());
    }

    @Test
    public void theCrossingIsAskedForEveryTickByWhicheverMovementIsRunning() {
        final List<String> movement = code("src/main/java/baritone/pathing/movement/Movement.java");
        final List<String> asks = movement.stream().filter(line -> line.contains("carefulFooting(")).collect(Collectors.toList());
        assertEquals("the crossing is asked for once a tick, from the one seam every movement passes"
                        + " through - asked anywhere else, a movement that is not a traverse never ticks the"
                        + " crouch and a crossing followed by one leaves the bot walking away crouched: " + asks,
                1, asks.size());
        assertTrue("the tick no longer asks whether the movement has to place under itself, which is what"
                        + " starts a crouch: " + asks,
                asks.get(0).contains("isPlacingUnderItself()"));
        assertTrue("the tick no longer asks whether the step after this one has a floor either, which is"
                        + " what holds the crouch through the walk onto the block just placed: " + asks,
                asks.get(0).contains("isAnotherGapAhead()"));

        final List<String> traverse = code("src/main/java/baritone/pathing/movement/movements/MovementTraverse.java");
        assertTrue("the traverse stopped answering the ask: nothing would crouch on a bridge",
                traverse.stream().anyMatch(line -> line.contains("protected boolean isPlacingUnderItself()")));
        assertTrue("the traverse stopped answering whether another gap is ahead: the crouch would come up"
                        + " in the middle of its own crossing",
                traverse.stream().anyMatch(line -> line.contains("protected boolean isAnotherGapAhead()")));
        assertTrue("the ask no longer honours assumeSafeWalk: the user who turned the crouch off would"
                        + " get it back on every bridge",
                methodBody(traverse, "protected boolean isPlacingUnderItself()")
                        .stream().anyMatch(line -> line.contains("assumeSafeWalk")));
        assertTrue("the gap ahead no longer honours assumeSafeWalk, so the crouch would be held for a user"
                        + " who turned it off",
                methodBody(traverse, "protected boolean isAnotherGapAhead()")
                        .stream().anyMatch(line -> line.contains("assumeSafeWalk")));
    }

    @Test
    public void theInputLayerIsWhatPressesTheCrouch() {
        final List<String> handler = code("src/main/java/baritone/utils/InputOverrideHandler.java");
        assertTrue("the input layer no longer holds the crouch across the movement seam",
                handler.stream().anyMatch(line -> line.contains("footing.tick(")));
        assertTrue("the crouch is no longer forgotten when there is no path, so a crossing that ended while"
                        + " nobody was asking would leave the bot crouched for good",
                handler.stream().filter(line -> line.contains("footing.reset()")).count() == 2);
        final List<String> input = code("src/main/java/baritone/utils/PlayerMovementInput.java");
        assertTrue("the crouch is no longer what the game sees as the sneak key",
                input.stream().anyMatch(line -> line.contains("isCrouchingForTheFooting()")));
    }

    /**
     * The lines of one method, from its signature to the closing brace at the same indent. Bare enough to
     * read a guard out of a body without a parser, which is all this file needs.
     */
    private static List<String> methodBody(List<String> lines, String signature) {
        int start = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).startsWith(signature)) {
                start = i;
                break;
            }
        }
        // not an assumption: this is the thing being pinned, so its absence has to fail rather than skip
        assertTrue("the method this file pins is gone: " + signature, start >= 0);
        final List<String> body = new java.util.ArrayList<>();
        for (int i = start + 1; i < lines.size(); i++) {
            if (lines.get(i).equals("}")) {
                return body;
            }
            body.add(lines.get(i));
        }
        return body;
    }

    private static List<String> code(String path) {
        final Path root = repoRoot();
        assumeTrue("the sources are not next to this test run", root != null);
        final Path file = root.resolve(path);
        assumeTrue(path + " is not next to this test run", Files.isRegularFile(file));
        try {
            return Files.readAllLines(file).stream()
                    .map(String::trim)
                    .filter(line -> !line.startsWith("//") && !line.startsWith("*") && !line.startsWith("/*"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

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
