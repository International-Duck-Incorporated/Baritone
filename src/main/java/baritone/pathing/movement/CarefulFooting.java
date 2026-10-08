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

/**
 * The crouch a person keeps while their footing is not certain.
 * <p>
 * A bridge used to be walked crouch-stand-crouch-stand, once per block. The crouch was asked for by the
 * traverse only on the ticks where the block being stepped onto was missing and its placement was ready
 * ({@code MovementTraverse#updateState}), and every key is cleared and re-decided from scratch every tick
 * ({@code Movement.update()}), so the tick the block went in and the feet stepped onto it - four or five
 * ticks of walking, at walk speed - the bot stood up, and then crouched again for the next one. Nobody
 * crosses a gap that way. A person crouches at the edge and stays crouched until they are across.
 * <p>
 * That is the whole of what this holds: the ask is a movement that has to place the block it is stepping
 * onto, and once the crouch is down it stays down until there is nothing ahead to fall into. It does not
 * time anything, it has no drawn interval and it takes no random source - the crossing ends when the
 * ground does, which is what makes a person stand up too. It never shortens an ask (a movement that needs
 * the crouch gets it on the tick it asks), and it never invents one: a walk that is not over a gap gets no
 * crouch from this class at all, whatever is beside it.
 * <p>
 * Free of Minecraft and of the behaviour that drives it, so the rules can be tested - the same terms as
 * {@link KeyBeat}, {@link SprintWindup} and {@link HeadFirst}. {@code CarefulFootingTest} covers them.
 */
public final class CarefulFooting {

    /**
     * Whether the crouch is down right now.
     */
    private boolean crouched;

    /**
     * One tick of the crouch.
     *
     * @param asked             the movement has to place the block it is stepping onto, so there is
     *                          nothing under the next step yet: the footing is not certain and the
     *                          crouch goes down - or stays down, if it already was
     * @param anotherGapIsAhead the block under the step after this one has no floor either, which is
     *                          what a crossing looks like from the middle: consulted only while the
     *                          crouch is already down, so a single hole at the side of an ordinary walk
     *                          is not a bridge and gets no crouch
     * @return whether the crouch should be down this tick
     */
    public boolean tick(boolean asked, boolean anotherGapIsAhead) {
        this.crouched = asked || (this.crouched && anotherGapIsAhead);
        return this.crouched;
    }

    /**
     * Whether the crouch is down, without ticking it: for a caller that has to know before the tick it
     * is asking about happens ({@code InputOverrideHandler#atAPace()}, which decides whether a beat of no
     * keys is safe, and a bridge is the one place it is not).
     */
    public boolean crouched() {
        return this.crouched;
    }

    /**
     * Forgets the crouch, for when the bot is handed the keys again after somebody else had them, or when
     * there is no path any more. A crossing that ended while nobody was looking is not one being walked.
     */
    public void reset() {
        this.crouched = false;
    }
}
