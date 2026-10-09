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

import baritone.api.utils.Rotation;

/**
 * The lean of the gaze onto the line the path takes next.
 * <p>
 * A person walking a route looks where they are about to go: the head starts around the corner before
 * the feet get there, and only a bot walks with its eyes locked on the block it is currently stepping
 * onto and whips them around at every boundary. The movement's own target says where the walk is going
 * this tick; this leans the gaze toward where the path goes after it, more the closer the walk comes to
 * its end. On a straight line the two directions are the same and the lean is nothing at all, which is
 * the point: the head only leads where the path actually bends.
 * <p>
 * The lean is of the yaw alone. The direction the path takes next has a pitch too, but the pitch from the eyes
 * down to the centre of the next block is about thirty degrees on level ground - not the pitch of a level walk,
 * so the two directions are not "the same" on a straight line in that axis, and leaning onto it held the head
 * on the floor two blocks ahead for the whole of every flat walk (measured in the client: a median of 29
 * degrees down, 93% of the walk past fifteen, with the target level). The pitch belongs to
 * {@link PitchWander}, which keeps it near level, and to {@code PathExecutor#leanOntoTheSlope}, which carries it
 * where the path climbs or falls.
 * <p>
 * Only ever a lean on a walking target. A target that is about to be clicked is aimed at exactly, under
 * every rule this fork has, and this stands aside for those like the sway and the carry do - see
 * {@code LookBehavior#updateTarget}. And it is one tick of memory at most: the walk offers where the
 * path goes next every tick, and a lean not taken this tick is gone ({@link #tick}).
 * <p>
 * Free of Minecraft and of the behaviour that drives it, so the rules can be tested. {@code
 * GazeAheadTest} covers them.
 */
public final class GazeAhead {

    /**
     * How far the lean pulls the gaze off the movement's own target at its strongest: halfway, so the
     * walk is still plainly the subject of the look and the corner is only being anticipated.
     */
    public static final double LEAN = 0.5;

    /**
     * The furthest off the walking direction the lean ever points, in degrees. A path that doubles back
     * would otherwise lean half of a half turn - ninety degrees off the walk - and a person who looks
     * behind themselves while still walking forward is a person about to walk into something. Sixty is
     * the over-the-shoulder glance of somebody who knows the route is about to turn around.
     */
    public static final float MAX_LEAN_DEGREES = 60f;

    private Rotation ahead;
    private double weight;

    /**
     * Where the path goes next, and how far the gaze may lean onto it this tick.
     *
     * @param ahead  the direction the path takes after the walk under way, or null for none; only its yaw is used
     * @param weight how far to lean, from 0 for not at all to {@link #LEAN} for as far as it ever goes
     */
    public void offer(Rotation ahead, double weight) {
        this.ahead = ahead;
        this.weight = weight;
    }

    /**
     * One tick of the clock: a lean not taken is gone. Call it before anything can offer.
     */
    public void tick() {
        this.ahead = null;
        this.weight = 0;
    }

    /**
     * The target the hand should actually be aiming at this tick: the movement's own, leaned onto the
     * line ahead.
     *
     * @param target       where the movement wants the gaze
     * @param blockInteract whether this target is about clicking something - if so, it is used exactly
     * @return the rotation to aim at
     */
    public Rotation lean(Rotation target, boolean blockInteract) {
        final Rotation ahead = this.ahead;
        final double weight = this.weight;
        tick();
        if (ahead == null || blockInteract || weight <= 0) {
            return target;
        }
        final double w = Math.min(weight, LEAN);
        // the shortest way round: a path that bends across the ±180 seam leans through the seam, not
        // all the way around the compass
        final float deltaYaw = Rotation.normalizeYaw(ahead.getYaw() - target.getYaw());
        final float leanYaw = (float) Math.max(-MAX_LEAN_DEGREES, Math.min(MAX_LEAN_DEGREES, deltaYaw * w));
        // the pitch is the target's own, untouched: see the class comment
        return new Rotation(Rotation.normalizeYaw((float) (target.getYaw() + leanYaw)), target.getPitch());
    }
}
