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

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Who has just walked up, so that the head can turn to them the way a person's does.
 * <p>
 * The glances already look at whoever is nearby now and then, on their own clock, and that is the right
 * habit for company that is simply there. Somebody arriving is a different thing: a person who sees
 * another player come round the corner looks at them within half a second, once, and a bot that lets
 * somebody walk up to within arm's length and only gets round to them when its next glance happens to
 * fall is noticed for it, because the one thing every player checks a suspected bot with is walking up to
 * it. So this remembers who was in view on the ticks before, calls anyone who was not an arrival, waits a
 * reaction's worth of ticks, and names them - once - for the glance that is running to look at.
 * <p>
 * What it is not: it is not awareness. The caller decides who is in view, and the caller only offers the
 * people in front of the head with nothing between, because a head that turns to somebody behind it has
 * seen through the back of its own skull. The names given here are only ever a prompt to a glance, and
 * the glance keeps all of its own rules - it is not taken while anything is being clicked, it does not
 * outlive its hold, it comes home - so this cannot point the crosshair anywhere the glance could not.
 * <p>
 * The one thing that is not an arrival and still goes through here is a blow - see {@link #struck} -
 * because the head's answer to it is the same kind of look, only quicker and whichever way it came from.
 * <p>
 * Free of Minecraft; {@code NewcomerTest} drives it.
 */
public final class Newcomer {

    /**
     * How long somebody has to be out of view before they are an arrival again when they come back:
     * fifteen seconds. A player who steps behind a tree and out again has not arrived twice.
     */
    static final int FORGOTTEN_AFTER = 300;

    /**
     * Ticks between somebody arriving and the head turning to them: a quarter of a second to seven
     * tenths, which is where a person's reaction to something in the corner of their eye falls.
     */
    static final int REACT_MIN = 5;
    static final int REACT_MAX = 14;

    /**
     * The least ticks between two such looks: four seconds. A crowd arriving together gets one look, at
     * the nearest of them, and not a head that sweeps across each in turn; somebody arriving on the heels
     * of somebody else is looked at when that much has passed, if they are still there.
     */
    static final int LOOKS_APART = 80;

    /**
     * Ticks between being hit and the head turning to whoever did it: two to six, which is a flinch and
     * not a decision. Quicker than noticing somebody arrive, because a blow is not in the corner of the
     * eye, and allowed to come on the heels of another look, because it is not one.
     */
    static final int STRUCK_REACT_MIN = 2;
    static final int STRUCK_REACT_MAX = 6;

    private static final int NOBODY = -1;

    private final Random rand;
    private final Map<Integer, Integer> lastSeen = new HashMap<>();
    private boolean primed;
    private int tick;
    private int who = NOBODY;  // the arrival the pending look is for
    private int reactIn;
    private int sinceLook = LOOKS_APART;
    private int struckBy = NOBODY; // whoever just hit us, until the head has turned to them
    private int flinchIn;

    public Newcomer() {
        this(new Random());
    }

    /**
     * @param rand the source of variation, injectable so a run can be repeated; see {@code NewcomerTest}
     */
    public Newcomer(Random rand) {
        this.rand = rand;
    }

    /**
     * One tick. The company on the first tick is never an arrival - the world comes with people already in
     * it - and after that anyone not seen for {@link #FORGOTTEN_AFTER} ticks is one.
     *
     * @param inView who can be seen this tick, nearest first, by any id that stays the same from tick to
     *               tick; the first arrival in the order given is the one looked at
     * @return the id to look at this tick, or -1 on every tick there is nobody to turn to
     */
    public int notice(List<Integer> inView) {
        this.tick++;
        this.sinceLook++;
        if (this.struckBy != NOBODY) {
            // whoever hit us comes before anyone who merely arrived, and before the clock
            if (--this.flinchIn <= 0) {
                final int look = this.struckBy;
                this.struckBy = NOBODY;
                this.sinceLook = 0;
                return look;
            }
        }
        if (!this.primed) {
            for (int id : inView) {
                this.lastSeen.put(id, this.tick);
            }
            this.primed = true;
            return NOBODY;
        }
        int arrived = NOBODY;
        for (int id : inView) {
            final Integer last = this.lastSeen.put(id, this.tick);
            if (arrived == NOBODY && (last == null || this.tick - last > FORGOTTEN_AFTER)) {
                arrived = id;
            }
        }
        if (this.tick % FORGOTTEN_AFTER == 0) {
            forget();
        }
        if (this.who == NOBODY && arrived != NOBODY) {
            this.who = arrived;
            this.reactIn = Math.max(REACT_MIN + this.rand.nextInt(REACT_MAX - REACT_MIN + 1), LOOKS_APART - this.sinceLook + 1);
        }
        if (this.who == NOBODY) {
            return NOBODY;
        }
        if (!inView.contains(this.who)) {
            // gone again before the head got round to them, so the look is not taken: there is nothing
            // there to look at, and a head that turns to where somebody was is a head that heard them
            this.who = NOBODY;
            return NOBODY;
        }
        if (--this.reactIn > 0) {
            return NOBODY;
        }
        final int look = this.who;
        this.who = NOBODY;
        this.sinceLook = 0;
        return look;
    }

    /**
     * We have just been hit, and {@code id} is the likeliest to have done it: the nearest player, or
     * failing one the nearest hostile. The head turns to them a flinch later - see
     * {@link #STRUCK_REACT_MIN} - whether or not they were in view, because a blow says where it came
     * from, and instead of anything else that was waiting. A second blow before the head has turned is the
     * same flinch, at whoever it names last. A person being punched looks round at who is punching them; that
     * is the test every suspected bot gets, and a head that goes on staring at the horizon fails it.
     */
    public void struck(int id) {
        if (id == NOBODY) {
            return;
        }
        if (this.struckBy == NOBODY) {
            this.flinchIn = STRUCK_REACT_MIN + this.rand.nextInt(STRUCK_REACT_MAX - STRUCK_REACT_MIN + 1);
        }
        this.struckBy = id;
        this.who = NOBODY; // an arrival the head was about to turn to can wait; it will not now be looked at
    }

    /**
     * Drops everyone who has been gone long enough to be an arrival when they come back, so that the map
     * does not grow with everybody ever passed on a long enough day.
     */
    private void forget() {
        final Iterator<Map.Entry<Integer, Integer>> it = this.lastSeen.entrySet().iterator();
        while (it.hasNext()) {
            if (this.tick - it.next().getValue() > FORGOTTEN_AFTER) {
                it.remove();
            }
        }
    }
}
