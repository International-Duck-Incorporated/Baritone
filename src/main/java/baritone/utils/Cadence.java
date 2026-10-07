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

import java.util.Random;

/**
 * How long a person waits between repeated actions.
 * <p>
 * Anything that repeats every tick, forever, is a giveaway, and the fix is always the same: draw a short
 * delay and sit still for it. The arithmetic is kept here, away from the code that talks to Minecraft, so
 * it can be tested without a game running.
 */
public final class Cadence {

    private Cadence() {}

    /**
     * Ticks to wait before starting the next block after one breaks.
     * <p>
     * A person does not begin the next block in the same tick the last one vanished: there is a beat while
     * they register it and move on. Multi-tick breaks are already paced by {@code blockBreakSpeed}, but an
     * instant break never set a timer, so a field of crops or a patch of grass went down at exactly one
     * block per tick, forever.
     */
    public static final int RETARGET_DELAY_MIN = 1;
    public static final int RETARGET_DELAY_MAX = 3;

    /**
     * @return the pause, in ticks, before attacking the next block; inclusive of both ends of the range
     */
    public static int retargetDelay(Random rand) {
        return RETARGET_DELAY_MIN + rand.nextInt(RETARGET_DELAY_MAX - RETARGET_DELAY_MIN + 1);
    }

    /**
     * Ticks between the crosshair arriving on a block and the click that follows it: one or two.
     * <p>
     * Everything that breaks or places a block waits for the aim before it starts, because that is what
     * the callers gate the click on - the trace has to land on the block, and the trace follows the aim.
     * The click itself, though, used to be in the same tick the crosshair arrived, which is a thing no
     * hand does: aim, then press, with a reaction in between. One or two ticks is fifty to a hundred
     * milliseconds, which is about as short as that reaction gets.
     * <p>
     * Both buttons draw it, and both spend it per arrival rather than per block, so a run of blocks mined
     * or a run of blocks placed without the crosshair ever leaving a face only pays it once. The one place
     * it is deliberately not spent is a block placed in mid air: that one is timed against the jump arc
     * the player is already on.
     */
    public static final int SIGHT_SETTLE_MIN = 1;
    public static final int SIGHT_SETTLE_MAX = 2;

    /**
     * @return the pause, in ticks, before the first click on a block the crosshair has just arrived on
     */
    public static int sightSettle(Random rand) {
        return SIGHT_SETTLE_MIN + rand.nextInt(SIGHT_SETTLE_MAX - SIGHT_SETTLE_MIN + 1);
    }

    /**
     * The beat between the crosshair arriving on a block and the click that follows it, as a small piece of
     * state: drawn on the tick a request starts, spent over the ticks that follow it, and gone the moment
     * the request does.
     * <p>
     * Both of Baritone's mouse buttons need exactly this - see {@link #sightSettle} - and both had their own
     * copy of it, which is the shape of thing that drifts: one of them growing a guard the other lacks. The
     * rule is here instead, free of any Minecraft type, so that "one settle per arrival, none for a place
     * that has to be made in mid air" can be tested without a world to click in.
     */
    public static final class Settle {

        private final Random rand;
        private boolean wasAsked;
        private int timer;

        public Settle(Random rand) {
            this.rand = rand;
        }

        /**
         * @param asked    whether something is being clicked at this moment
         * @param onGround whether the player is standing on something. A place made in mid air is timed
         *                 against the jump arc the player is already on, so it is not held back by a
         *                 reaction it cannot afford: such a request is never charged, and one that is
         *                 already in flight when the player lands is not charged on landing either.
         * @return whether this tick is still the beat before the click
         */
        public boolean waiting(boolean asked, boolean onGround) {
            if (!asked) {
                this.wasAsked = false;
                this.timer = 0;
            } else if (!this.wasAsked && onGround) {
                this.timer = sightSettle(this.rand);
            }
            this.wasAsked = asked;
            if (this.timer > 0) {
                this.timer--;
                return true;
            }
            return false;
        }
    }

    /**
     * Ticks a sprint key stays held after nothing is asking for it any more: one to three.
     * <p>
     * Sprint is decided from scratch every tick from what the current movement wants, which means the run
     * blinks off on exactly the tick a movement stops asking for it and back on the tick the next one asks
     * again. Nobody releases the key on that tick: the hand comes off a beat later, and the run peters out
     * over a few ticks of walking instead of switching between two speeds between one tick and the next.
     * Only ever spent where letting go can be delayed safely - see the gate in
     * {@code PathExecutor#shouldSprintNextTick()}.
     */
    public static final int SPRINT_HANGOVER_MIN = 1;
    public static final int SPRINT_HANGOVER_MAX = 3;

    /**
     * @return the hangover, in ticks, for a sprint nothing is asking for any more; inclusive of both ends
     */
    public static int sprintHangover(Random rand) {
        return SPRINT_HANGOVER_MIN + rand.nextInt(SPRINT_HANGOVER_MAX - SPRINT_HANGOVER_MIN + 1);
    }

    /**
     * How far a repeated action's interval is allowed to wander, in ticks, either side of the setting.
     */
    public static final int JITTER = 1;

    /**
     * A fixed interval with a tick of slack on either side.
     * <p>
     * Everything that repeats on a setting - placing a block every {@code rightClickSpeed} ticks, the
     * pause after a block breaks, an inventory move - repeats on <em>exactly</em> that interval, forever,
     * which is a rhythm no person produces: hold a stopwatch to someone building a wall and the gaps are
     * three ticks, four, three, three, five. The eye does not read "5.0 blocks per second" as fast, it
     * reads the metronome, and it is one of the few tells that survives a good camera and a plausible
     * reaction time.
     * <p>
     * A tick either way is 50 milliseconds: enough to break the periodicity, far too little to change how
     * the activity looks or how long it takes. Intervals the setting has already set to one tick cannot be
     * shortened and are left alone.
     *
     * @param ticks the interval the setting asks for
     * @return the interval to actually wait, in ticks
     */
    public static int jitter(Random rand, int ticks) {
        if (ticks <= 1) {
            return ticks;
        }
        final int drawn = ticks - JITTER + rand.nextInt(JITTER * 2 + 1);
        return Math.max(1, drawn);
    }

    /**
     * An interval that comes round again, with {@link #jitter}'s tick of slack drawn afresh each time round
     * rather than the same grid repeating.
     * <p>
     * The three menus that rescan for something to do - mining, getting to a block, and farming - all kept
     * time as {@code tickCount++ % interval == 0}, which is a scan on exactly every fifth tick of the
     * process's life (the setting defaults to five) for as long as it holds control, forever. That is a
     * decision on a metronome: the bot can only ever change its mind on one tick in five, and the moment it
     * notices something better should be whenever it notices it. Drawing the slack each time round leaves
     * the average where the setting put it and lets the phase wander, so the scans land on whatever ticks
     * attention would.
     * <p>
     * Free of Minecraft and of the setting that feeds it, so the drift can be tested. {@code CadenceTest}
     * covers it: the first scan is immediate - a process that has just taken control should look at the
     * world, not wait a fifth of a second first - and {@code 0} asks for nothing at all, which is the
     * spelling these settings use for disabled.
     */
    public static final class Repeat {

        private final Random rand;
        private int countdown;

        public Repeat(Random rand) {
            this.rand = rand;
        }

        /**
         * One tick of the clock.
         *
         * @param ticks the interval the calling setting asks for, in ticks, or {@code 0} for never
         * @return whether the interval has come round this tick
         */
        public boolean due(int ticks) {
            if (ticks <= 0) {
                return false;
            }
            if (this.countdown <= 1) {
                this.countdown = jitter(this.rand, ticks);
                return true;
            }
            this.countdown--;
            return false;
        }
    }
}
