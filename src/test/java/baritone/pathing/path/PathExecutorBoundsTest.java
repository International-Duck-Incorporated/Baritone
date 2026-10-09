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

package baritone.pathing.path;

import baritone.api.pathing.calc.IPath;
import baritone.api.pathing.movement.IMovement;
import net.minecraft.core.BlockPos;
import org.junit.Test;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A path of n movements runs over n + 1 positions, and {@link IPath#length()} counts the positions. The
 * three look-aheads that were written against it - the gaze's lean around the next corner, the lean onto
 * the slope, and the welcome for a walking glance - each read one movement past the end of the path during
 * the last movement of it, and the first walk in a real client ended in an IndexOutOfBoundsException from
 * the tick at the end of the path. None of the unit tests could see it: nothing ran a path to its end.
 * <p>
 * The path here is a stand-in with the one property that matters - one more position than movements, and
 * the real {@code length()} on top of them - so what is pinned is the arithmetic, and the wiring test
 * pins that the three sites go through it.
 */
public class PathExecutorBoundsTest {

    private static BlockPos east() {
        return new BlockPos(1, 0, 0);
    }

    private static IMovement movement(BlockPos direction) {
        return (IMovement) Proxy.newProxyInstance(IMovement.class.getClassLoader(), new Class<?>[]{IMovement.class}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "getDirection":
                    return direction;
                case "toString":
                    return "movement " + direction;
                default:
                    throw new UnsupportedOperationException(method.getName());
            }
        });
    }

    private static IPath pathOf(BlockPos... directions) {
        final List<IMovement> movements = new ArrayList<>();
        for (BlockPos direction : directions) {
            movements.add(movement(direction));
        }
        return (IPath) Proxy.newProxyInstance(IPath.class.getClassLoader(), new Class<?>[]{IPath.class}, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                switch (method.getName()) {
                    case "movements":
                        return movements;
                    case "positions":
                        // one more position than there are movements: the start, and then where each one ends
                        return Collections.nCopies(movements.size() + 1, null);
                    default:
                        if (method.isDefault()) {
                            return InvocationHandler.invokeDefault(proxy, method, args);
                        }
                        throw new UnsupportedOperationException(method.getName());
                }
            }
        });
    }

    @Test
    public void thePathCountsOnePositionMoreThanItHasMovements() {
        final IPath path = pathOf(east(), east(), east());
        assertEquals(3, path.movements().size());
        assertEquals("length() counts the positions: one more than there are movements", 4, path.length());
    }

    @Test
    public void theLastMovementHasNothingAheadOfIt() {
        final IPath path = pathOf(east(), east(), east(), east(), east());
        assertTrue(PathExecutor.hasMovementAhead(path, 4, 0));
        assertTrue(PathExecutor.hasMovementAhead(path, 3, 1));
        assertFalse("on the last movement there is no next one", PathExecutor.hasMovementAhead(path, 4, 1));
        assertTrue(PathExecutor.hasMovementAhead(path, 2, 2));
        assertFalse("one before the last has one ahead and not two", PathExecutor.hasMovementAhead(path, 3, 2));
        assertFalse(PathExecutor.hasMovementAhead(path, 0, 5));
        assertTrue(PathExecutor.hasMovementAhead(path, 0, 4));
    }

    @Test
    public void theSlopeStopsWhereThePathStops() {
        // five flat blocks and then nothing: a look-ahead longer than what is left ends with the path
        final IPath path = pathOf(east(), east(), east(), east(), east());
        for (int from = 0; from < 5; from++) {
            final double[] slope = PathExecutor.slopeAhead(path, from, 8.0);
            assertEquals("rise from " + from, 0.0, slope[0], 0.0);
            assertEquals("run from " + from, 5 - from, slope[1], 1e-9);
        }
        final double[] past = PathExecutor.slopeAhead(path, 5, 8.0);
        assertEquals(0.0, past[0], 0.0);
        assertEquals(0.0, past[1], 0.0);
    }

    @Test
    public void theSlopeAddsUpTheRiseAndTheRunUntilTheLookAheadIsSpent() {
        // two steps down the stairs, a flat block, a diagonal flat step, and the drop at the end
        final IPath path = pathOf(new BlockPos(1, -1, 0), new BlockPos(1, -1, 0), east(), new BlockPos(1, 0, 1), new BlockPos(0, -1, 0));
        final double[] all = PathExecutor.slopeAhead(path, 0, 100.0);
        assertEquals(-3.0, all[0], 0.0);
        assertEquals(3.0 + Math.sqrt(2.0), all[1], 1e-9);
        // the run is spent after two movements, and the third is not read
        final double[] two = PathExecutor.slopeAhead(path, 0, 2.0);
        assertEquals(-2.0, two[0], 0.0);
        assertEquals(2.0, two[1], 1e-9);
        // from the diagonal on: it and the drop that follows it
        final double[] tail = PathExecutor.slopeAhead(path, 3, 100.0);
        assertEquals(-1.0, tail[0], 0.0);
        assertEquals(Math.sqrt(2.0), tail[1], 1e-9);
    }

    @Test
    public void theThreeLookAheadsGoThroughTheHelpersAndNotThroughLength() throws IOException {
        final List<String> source = Files.readAllLines(Paths.get("src/main/java/baritone/pathing/path/PathExecutor.java"));
        final String[][] sites = {
                {"private void leanGazeAhead(", "hasMovementAhead("},
                {"private void leanOntoTheSlope(", "slopeAhead("},
                {"private void welcomeGlance(", "hasMovementAhead("},
        };
        final List<String> problems = new ArrayList<>();
        for (String[] site : sites) {
            final String body = bodyOf(source, site[0]);
            if (body.contains("path.length()")) {
                problems.add(site[0] + " bounds itself with path.length(), which counts the positions: one more than there are movements");
            }
            if (!body.contains(site[1])) {
                problems.add(site[0] + " does not go through " + site[1]);
            }
        }
        assertTrue(String.join("; ", problems), problems.isEmpty());
    }

    /**
     * The lines of the method that opens on the line with {@code signature}, up to the line that closes it at
     * the indent of a class member, with the {@code //} comments taken off.
     */
    private static String bodyOf(List<String> source, String signature) {
        int start = -1;
        for (int i = 0; i < source.size(); i++) {
            if (source.get(i).contains(signature)) {
                start = i;
                break;
            }
        }
        assertTrue("no " + signature + " in PathExecutor", start >= 0);
        final StringBuilder body = new StringBuilder();
        for (int i = start + 1; i < source.size() && !source.get(i).equals("    }"); i++) {
            body.append(source.get(i).replaceAll("//.*", "")).append('\n');
        }
        return body.toString();
    }
}
