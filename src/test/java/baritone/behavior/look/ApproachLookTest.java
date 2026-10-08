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

import baritone.api.pathing.goals.Goal;
import baritone.api.pathing.goals.GoalBlock;
import baritone.api.pathing.goals.GoalComposite;
import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.pathing.goals.GoalInverted;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.pathing.goals.GoalRunAway;
import baritone.api.pathing.goals.GoalTwoBlocks;
import baritone.api.pathing.goals.GoalXZ;
import baritone.api.pathing.goals.GoalYLevel;
import baritone.api.utils.Rotation;
import net.minecraft.core.BlockPos;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The look onto the block a job is about has two halves and both are here: which block a goal is about,
 * and how much of the way to it the eyes may come. What is deliberately not here is the wiring - whether
 * the walk asks - because that needs a player and a world; it is read from the source, the way the other
 * counted edges are.
 * <p>
 * The properties that matter, and the reason each test exists:
 * <ul>
 *     <li>a goal that names a block is asked for it, one that does not names nothing, and a composite
 *     answers with the part of itself the walk is actually for - a head that swung between two equally
 *     good blocks as a walk wandered would be worse than no look at all;</li>
 *     <li>the weight is continuous at every boundary it has, because a look that appears at a distance is
 *     a snap however small it is when it appears;</li>
 *     <li>it never exceeds {@link ApproachLook#LEAN}, never points further off the walk than
 *     {@link ApproachLook#MAX_OFF_THE_WALK}, and is nothing at all for a block the player is standing
 *     in or one behind them - the three shapes of "this would be a stare, not an arrival";</li>
 *     <li>a walk that is not a walk <i>to</i> anything - the end of a plain {@code goto}, which is the
 *     block under the player's own last step - produces no lean, which is what keeps this from being a
 *     rule that changes every walk on the branch;</li>
 *     <li>a target about to be clicked is still returned exactly.</li>
 * </ul>
 */
public class ApproachLookTest {

    /** The walk is due east in the test's own plane, and every block in it is placed at an angle off it. */
    private static final double WALK_X = 1.0;
    private static final double WALK_Z = 0.0;

    /** A block {@code degrees} round the walk, {@code distance} blocks away, as the caller would see it. */
    private static float leanOf(double degrees, double distance, boolean blockInteract) {
        final float off = ApproachLook.angleOffTheWalk(
                WALK_X, WALK_Z,
                Math.cos(Math.toRadians(degrees)), Math.sin(Math.toRadians(degrees)));
        assertEquals("the angle to the block is not the angle it was put at",
                (float) Math.abs(degrees), off, 0.01f);
        final double weight = ApproachLook.weight(distance, off);
        final GazeAhead gaze = new GazeAhead();
        gaze.offer(new Rotation((float) degrees, 0f), weight);
        return gaze.lean(new Rotation(0f, 0f), blockInteract).getYaw();
    }

    // --- which block a goal is about ---------------------------------------------------------------

    @Test
    public void aGoalThatNamesABlockIsAsked() {
        final BlockPos at = new BlockPos(11, 63, -4);
        assertEquals(at, ApproachLook.theBlock(new GoalBlock(at), null));
        assertEquals(at, ApproachLook.theBlock(new GoalTwoBlocks(at), null));
        assertEquals(at, ApproachLook.theBlock(new GoalGetToBlock(at), null));
        assertEquals(at, ApproachLook.theBlock(new GoalNear(at, 3), null));
    }

    @Test
    public void aGoalThatNamesNoBlockHasNone() {
        assertNull(ApproachLook.theBlock(null, null));
        assertNull(ApproachLook.theBlock(new GoalXZ(11, -4), null));
        assertNull(ApproachLook.theBlock(new GoalYLevel(63), null));
        assertNull(ApproachLook.theBlock(new GoalRunAway(4, new BlockPos(0, 64, 0)), null));
        assertNull(ApproachLook.theBlock(new GoalInverted(new GoalBlock(1, 64, 1)), null));
        // and a composite of things that name nothing is nothing, not an empty answer that reads as one
        assertNull(ApproachLook.theBlock(new GoalComposite(new GoalXZ(1, 1), new GoalYLevel(64)), null));
    }

    @Test
    public void aCompositeIsTheBlockNearestTheEndOfTheWalk() {
        final BlockPos near = new BlockPos(0, 64, 0);
        final BlockPos far = new BlockPos(40, 64, 40);
        final BlockPos middling = new BlockPos(10, 64, 10);
        final Goal composite = new GoalComposite(new GoalXZ(0, 0) /* names nothing */,
                new GoalBlock(far), new GoalBlock(near), new GoalBlock(middling));
        // the walk ends here, so the block the walk is for is the one by it - not the first listed, and
        // not anything to do with where the player is standing
        assertEquals(near, ApproachLook.theBlock(composite, new BlockPos(1, 64, 1)));
        assertEquals(middling, ApproachLook.theBlock(composite, new BlockPos(11, 64, 11)));
        // and a nested composite answers the same way
        final Goal nested = new GoalComposite(new GoalComposite(new GoalYLevel(64), new GoalBlock(far)),
                new GoalTwoBlocks(near));
        assertEquals(near, ApproachLook.theBlock(nested, new BlockPos(0, 64, 1)));
        // with nowhere named for the walk, the first block that can answer does
        assertEquals(far, ApproachLook.theBlock(composite, null));
    }

    // --- how much of the way the eyes may come -----------------------------------------------------

    @Test
    public void theWeightIsNothingWhenThereIsNothingToLookAt() {
        // too far: at the range and past it
        assertEquals(0.0, ApproachLook.weight(ApproachLook.RANGE, 0f), 0.0);
        assertEquals(0.0, ApproachLook.weight(ApproachLook.RANGE + 4, 0f), 0.0);
        // beside or behind rather than on the way
        assertEquals(0.0, ApproachLook.weight(2.0, ApproachLook.MAX_OFF_THE_WALK), 0.0);
        assertEquals(0.0, ApproachLook.weight(2.0, 180f), 0.0);
        // the block the player is standing in, which has no direction to it
        assertEquals(0.0, ApproachLook.weight(ApproachLook.UNDERFOOT, 0f), 0.0);
        assertEquals(0.0, ApproachLook.weight(0.0, 0f), 0.0);
    }

    @Test
    public void theWeightComesOnOverTheRampAsTheWalkClosesIn() {
        double last = 0.0;
        for (double distance = ApproachLook.RANGE; distance >= ApproachLook.UNDERFOOT + 0.01; distance -= 0.01) {
            final double weight = ApproachLook.weight(distance, 0f);
            assertTrue("the weight went down as the walk closed in, at " + distance, weight >= last - 1e-12);
            assertTrue("the weight passed its share, at " + distance, weight <= ApproachLook.LEAN + 1e-12);
            last = weight;
        }
        // full share by the time there are RAMP blocks to go, and no earlier
        assertEquals(ApproachLook.LEAN, ApproachLook.weight(ApproachLook.RANGE - ApproachLook.RAMP, 0f), 1e-9);
        assertEquals(ApproachLook.LEAN, ApproachLook.weight(2.0, 0f), 1e-9);
    }

    @Test
    public void theWeightFadesOutBeforeTheBlockIsBesideTheWalk() {
        // full strength up to the fade band, nothing at the bound, and no step in between
        assertEquals(ApproachLook.LEAN, ApproachLook.weight(2.0, ApproachLook.MAX_OFF_THE_WALK - ApproachLook.OFF_THE_WALK_FADE), 1e-9);
        assertEquals(0.0, ApproachLook.weight(2.0, ApproachLook.MAX_OFF_THE_WALK), 1e-9);
        double last = ApproachLook.LEAN;
        for (float angle = ApproachLook.MAX_OFF_THE_WALK - ApproachLook.OFF_THE_WALK_FADE;
                angle <= ApproachLook.MAX_OFF_THE_WALK; angle += 0.05f) {
            final double weight = ApproachLook.weight(2.0, angle);
            assertTrue("the weight went up as the block moved off the walk, at " + angle, weight <= last + 1e-12);
            last = weight;
        }
    }

    @Test
    public void theWeightIsContinuousAtBothOfItsEdges() {
        // a look that appears at a distance is a snap however small the angle it appears at, so neither
        // edge may step: the weight has to go to nothing as the distance and the angle approach theirs
        assertTrue("the weight appeared at the range", ApproachLook.weight(ApproachLook.RANGE - 1e-6, 0f) < 1e-6);
        assertTrue("the weight appeared at the bound",
                ApproachLook.weight(2.0, ApproachLook.MAX_OFF_THE_WALK - 0.01f) < 0.001);
    }

    @Test
    public void theAngleIsTheAngleBetweenTheTwoDirections() {
        assertEquals(0f, ApproachLook.angleOffTheWalk(1, 0, 1, 0), 1e-4f);
        assertEquals(90f, ApproachLook.angleOffTheWalk(1, 0, 0, 1), 1e-4f);
        assertEquals(180f, ApproachLook.angleOffTheWalk(1, 0, -1, 0), 1e-4f);
        assertEquals(45f, ApproachLook.angleOffTheWalk(1, 0, 1, 1), 1e-3f);
        // no walk, or a block on the player: the answer that asks for no lean at all
        assertEquals(180f, ApproachLook.angleOffTheWalk(0, 0, 1, 0), 0f);
        assertEquals(180f, ApproachLook.angleOffTheWalk(1, 0, 0, 0), 0f);
    }

    // --- the whole offer, through the lean the path already uses -----------------------------------

    @Test
    public void theEyesComeMostOfTheWayOntoABlockOnTheApproach() {
        // three blocks out and forty degrees off the walk: a person is already looking at it
        final float lean = leanOf(40, 3.0, false);
        assertTrue("the walk was not looked at at all: " + lean, lean > 8f);
        assertTrue("the lean took the head off the job: " + lean, lean < 20f);
        // and by the last block it is most of the way there
        final float arriving = leanOf(40, 1.5, false);
        assertTrue("the eyes were still on the path at the last block: " + arriving, arriving > lean);
        assertTrue("the lean came all the way onto the job: " + arriving, arriving < 40f);
    }

    @Test
    public void theLeanNeverLeavesTheWalk() {
        for (double distance = ApproachLook.RANGE; distance > ApproachLook.UNDERFOOT; distance -= 0.25) {
            for (double degrees = -180; degrees <= 180; degrees += 5) {
                final float lean = leanOf(degrees, distance, false);
                assertTrue("the lean pointed past its own bound: " + lean,
                        Math.abs(lean) <= GazeAhead.MAX_LEAN_DEGREES + 1e-3f);
                assertTrue("the lean went further off the walk than the block is: " + lean
                                + " for a block at " + degrees,
                        Math.abs(lean) <= Math.abs(degrees) + 1e-3f);
                assertTrue("the lean was stronger than the share: " + lean,
                        Math.abs(lean) <= Math.abs(degrees) * ApproachLook.LEAN + 1e-3f);
            }
        }
    }

    @Test
    public void aBlockAboutToBeClickedIsAimedAtExactly() {
        // whatever the offer says, the lean stands aside for a target that is about to be clicked - the
        // rule every click in the mod is aimed under, and the one this must not be able to bend
        for (double degrees = -180; degrees <= 180; degrees += 5) {
            assertEquals("a click target was leaned off", 0f, leanOf(degrees, 1.0, true), 0f);
        }
    }

    @Test
    public void aStraightApproachToTheEndOfTheWalkIsNoLean() {
        // the end of a plain goto is the block the walk ends in, dead ahead: nothing to add to a walk that
        // is already looking at it, which is what keeps this from changing every walk on the branch
        assertEquals(0f, leanOf(0, 1.0, false), 1e-4f);
        assertEquals(0f, leanOf(0, 5.0, false), 1e-4f);
    }

    // --- the wiring, which needs a player and is read rather than run ------------------------------

    @Test
    public void theWalkAsksBeforeItOffersTheLineAhead() throws IOException {
        final Path root = repoRoot();
        org.junit.Assume.assumeTrue("the sources are not next to this test run", root != null);
        final List<String> executor = Files.readAllLines(
                root.resolve("src/main/java/baritone/pathing/path/PathExecutor.java"));
        final String source = String.join("\n", executor);
        assertTrue("the block the walk is for is no longer resolved once per path",
                source.contains("ApproachLook.theBlock(behavior.getGoal(),"));
        assertTrue("the eyes are no longer offered to the block the walk is for",
                source.contains("if (lookAtTheJob(movement)) {"));
        assertTrue("the offer is no longer made into the lean the path already uses",
                source.contains("getLookBehavior().gazeAhead().offer("));
        assertTrue("the resolved block has to be kept for the path, not re-asked every tick",
                source.contains("private final BlockPos theJob;"));
    }

    /** The checkout, found the way the other counted tests find it. */
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
