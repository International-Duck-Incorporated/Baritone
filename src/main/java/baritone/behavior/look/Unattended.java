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

/**
 * Who the standing body belongs to once the job is over.
 * <p>
 * The idle repertoire used to run only while a process held control, because the idle glance must
 * never wander onto a camera somebody is looking through. The gate had the wrong shape, though: it
 * switched the repertoire off at the exact moment Baritone ran out of work - the goal reached, the
 * mine finished, the follow lost - and left the body standing at the last walking heading, perfectly
 * still, for as long as the wait lasted. A person who has just walked somewhere and stopped sways and
 * glances about; a body that has stopped dead and stays dead is the thing people point at.
 * <p>
 * So the repertoire stays on after the job ends, and this is the rule for who owns the body in the
 * gap between one job and the next. It starts with the body: the job only just ended and nobody has
 * touched anything since. The person at the keyboard takes it back the moment they actually arrive -
 * the view moves, a key or a mouse button goes down, a screen of any kind opens - and the take-back
 * is a latch, not a look: once they have shown they are there, the body stays theirs even if they
 * then hold still for an hour, because that is a person aiming at something, not an absence. The
 * next job takes the body from them, and the gap starts over.
 * <p>
 * Free of every Minecraft type and of the camera itself: the caller reads the input and measures the
 * view, this only decides whose it is. {@code UnattendedTest} covers it.
 */
public final class Unattended {

    /**
     * Whether the person has shown up since the last job ended. The latch: the body does not come
     * back to Baritone on its own, only with the next job.
     */
    private boolean personIsHere;

    /**
     * @param inControl      whether a process holds control this tick - a job owns the body outright
     * @param personShowedUp whether the person at the keyboard has produced any input this tick
     * @param theViewMoved   whether the view is somewhere other than where the idle look left it,
     *                       which is the person's mouse and nothing else
     * @return whether the standing body may keep its idle repertoire this tick
     */
    public boolean glanceIsOurs(boolean inControl, boolean personShowedUp, boolean theViewMoved) {
        if (inControl) {
            // a job taking control takes the body from the person too: when it ends, the gap starts over
            this.personIsHere = false;
            return true;
        }
        if (this.personIsHere) {
            return false;
        }
        if (personShowedUp || theViewMoved) {
            this.personIsHere = true;
            return false;
        }
        return true;
    }

    /**
     * Whether the person has taken the body back since the last job ended. Asked when the caller
     * wants to stop paying attention to the view because it no longer matters.
     */
    public boolean personIsHere() {
        return this.personIsHere;
    }

    /**
     * Forgets the person. The world they were standing in has gone - dimension change, respawn - and
     * with it the view they were thought to be looking through; whoever is there now has not shown up.
     */
    public void forget() {
        this.personIsHere = false;
    }
}
