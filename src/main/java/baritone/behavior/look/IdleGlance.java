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

import java.util.Random;
import java.util.function.Supplier;

/**
 * Decides when to look around, where at, and when to come back.
 * <p>
 * Deliberately free of any Minecraft type beyond {@link Rotation}: the rules are the interesting part
 * and they are worth being able to test on their own. Everything it does is bounded, and every path
 * out of it ends with the aim back where it started, so a glance can never leave the head stuck
 * pointing at a wall.
 */
public final class IdleGlance {

    /**
     * One tick of hand movement, from where the aim is to where it is going.
     */
    @FunctionalInterface
    public interface Stepper {

        /**
         * @param legAge how many ticks this leg of the glance - out to the thing, or back home - has been
         *               under way, from zero on its first tick. A turn spins up ({@code AimDynamics#ramp}),
         *               and the glance is two turns: a head that set off at full speed toward something
         *               that caught its eye, and again on the way back, was the one thing about it that
         *               still moved like a servo.
         */
        Rotation step(Rotation from, Rotation to, int legAge);
    }


    /**
     * Ticks to wait between glances: three to eight seconds. Long enough that a bot standing at a
     * closed door does not look like it is scanning for something.
     */
    private static final int COOLDOWN_MIN = 60;
    private static final int COOLDOWN_MAX = 160;

    /**
     * How long a glance is held once it has arrived: 0.4 to 1.5 seconds.
     */
    private static final int HOLD_MIN = 8;
    private static final int HOLD_MAX = 30;

    /**
     * How far a glance may wander from where the player was already looking: a glance, not a survey.
     */
    private static final float MAX_YAW = 28.0f;
    private static final float MAX_PITCH = 7.0f;

    /**
     * How often a glance looks down at what the hand is holding rather than around: about one wander in
     * four.
     * <p>
     * The two are different gestures and neither can stand in for the other. Looking around is a yaw
     * gesture - the head sweeps the horizon and comes back - and it is what a person does at a door, a
     * junction, a wait. Checking the hand is a pitch gesture: the eyes drop to the item for a second without
     * the head turning at all, and every player does it while standing about, because the thing being held
     * is the thing with the durability on it. With both, an idle head moves in two dimensions instead of
     * one, which is most of what separates a person waiting from a turret.
     */
    private static final float HAND_CHANCE = 0.25f;

    /**
     * Where the hand is, as a pitch to look at it by: down by thirty degrees or so, and a little to one
     * side of straight ahead, because that is where an arm hangs.
     */
    private static final float HAND_PITCH_MIN = 24.0f;
    private static final float HAND_PITCH_MAX = 38.0f;
    private static final float HAND_YAW = 10.0f;

    /**
     * How far the view may move away from where a glance started before the glance gives up. Movement
     * here means somebody moved the mouse themselves, and the polite thing is to get out of the way.
     * <p>
     * Only ever asked about a view somebody is looking through: a camera of the bot's own is moved by the
     * glance itself, and measuring it would give up on every wide gesture. See the five argument
     * {@code next}.
     */
    private static final float TAKEN_OVER = 20.0f;

    /**
     * Safety net: a glance may never outlive this many ticks, however confused the state gets.
     */
    private static final int MAX_AGE = 100;

    /**
     * How often a glance takes an interest in something nearby rather than wandering: about one in three.
     * <p>
     * A glance is taken every three to eight seconds, so with somebody in the neighbourhood this is a person
     * looked at about every fifteen. It is the one thing that says a head belongs to somebody: a player
     * standing next to a bot that never once looks at them reads as a machine, however well the bot's aim
     * wanders otherwise. The rest of the glances wander as they always did, because a head that turns to
     * face somebody every single time it moves is a different kind of wrong.
     */
    private static final float INTEREST_CHANCE = 0.35f;

    /**
     * What to fall back on when the caller has nothing nearby worth looking at.
     */
    private static final Supplier<Rotation> NO_INTEREST = () -> null;

    /**
     * How long a prompt - see {@link #prompt} - is kept for before it is dropped untaken: two seconds.
     * Somebody who arrived two seconds ago has not arrived, they are there, and the ordinary glances will
     * get round to them the ordinary way.
     */
    static final int PROMPT_LASTS = 40;

    private final Random rand;

    private int cooldown = COOLDOWN_MIN;
    private int hold;
    private int age;
    private int legAge;      // ticks into the current leg, out or back: see Stepper
    private Rotation aim;    // where the glance is pointed, null when none is running
    private Rotation home;   // where it started, and where it goes back to
    private Rotation sent;   // what the server was last told
    private Supplier<Rotation> prompted; // somewhere to look as soon as a glance may start, or null
    private int promptAge;

    public IdleGlance() {
        this(new Random());
    }

    /**
     * @param rand the source of variation, injectable so a run can be repeated; see {@code IdleGlanceTest}
     */
    public IdleGlance(Random rand) {
        this.rand = rand;
    }

    /**
     * @param camera where the player is actually looking right now
     * @param allowed whether an idle glance is appropriate this tick
     * @param step one tick of hand movement
     * @return the rotation to send to the server, or null when this tick is not ours to take
     */
    public Rotation next(Rotation camera, boolean allowed, Stepper step) {
        return this.next(camera, allowed, step, NO_INTEREST);
    }

    /**
     * The same, with something nearby that a person might look at.
     * <p>
     * One glance in three goes to whatever is offered instead of wandering. Whether a glance is taken, how
     * long it is held and where the head goes back to are all unchanged: an interest is where a glance is
     * aimed, not a reason for the head to stay there.
     *
     * @param interest what is worth looking at, or null when nothing is. Only asked when a glance is
     *                 starting, which is where the cost of working it out belongs
     */
    public Rotation next(Rotation camera, boolean allowed, Stepper step, Supplier<Rotation> interest) {
        return this.next(camera, allowed, true, step, interest);
    }

    /**
     * The same, with the caller saying whether the camera it hands over belongs to the player.
     * <p>
     * It is the difference between a view and a vantage point. A camera somebody is looking through can be
     * taken away from the glance - they move the mouse and the glance is pointing somewhere they are not -
     * and that is what {@link #TAKEN_OVER} is for. A camera of Baritone's own cannot be taken over by
     * anybody, and the gesture is what is moving it, so measuring it against where the gesture started
     * would abandon every glance wider than {@code TAKEN_OVER}: most of the wander draws, and every look at
     * the hand, none of which is ever that narrow. Those are held to the promise the class is built on
     * instead - out, held, and home again.
     *
     * @param someoneElsesCamera whether {@code camera} is the player's own view rather than the bot's
     */
    public Rotation next(Rotation camera, boolean allowed, boolean someoneElsesCamera, Stepper step,
                         Supplier<Rotation> interest) {
        if (this.prompted != null && ++this.promptAge > PROMPT_LASTS) {
            this.prompted = null;
        }
        if (!allowed) {
            // Baritone has started doing something, or the player is moving again. Drop the glance where
            // it stands; whatever happens next owns the aim from here.
            this.drop();
            return null;
        }
        if (this.aim == null) {
            if (this.prompted == null && --this.cooldown > 0) {
                return null;
            }
            // A glance is only taken while the player is standing still and nothing is being clicked. Both
            // matter, and not for the same reason: the movement code chooses its keys from the rotation the
            // server was last told, so a glance is visible to it, and Baritone's own ray trace follows that
            // same rotation, so a glance taken while a block was being broken would aim its pickaxe most of
            // a block off. Standing still with nothing clicked, nothing is reading the angle at all.
            Rotation lookAt = null;
            if (this.prompted != null) {
                // somebody just arrived: this glance is at them, now, whatever the clock says - unless they
                // are gone again, in which case there is nothing to look at and no glance is started
                lookAt = this.prompted.get();
                this.prompted = null;
                if (lookAt == null) {
                    return null;
                }
            } else if (this.rand.nextFloat() < INTEREST_CHANCE) {
                lookAt = interest.get();
            }
            this.home = camera;
            if (lookAt == null) {
                lookAt = this.rand.nextFloat() < HAND_CHANCE
                        ? this.hand(camera)
                        : new Rotation(
                                Rotation.normalizeYaw(camera.getYaw() + (this.rand.nextFloat() * 2.0f - 1.0f) * MAX_YAW),
                                Rotation.clampPitch(camera.getPitch() + (this.rand.nextFloat() * 2.0f - 1.0f) * MAX_PITCH)
                        );
            }
            this.aim = new Rotation(Rotation.normalizeYaw(lookAt.getYaw()), Rotation.clampPitch(lookAt.getPitch()));
            this.hold = HOLD_MIN + this.rand.nextInt(HOLD_MAX - HOLD_MIN + 1);
            this.age = 0;
            this.legAge = 0;
            this.sent = camera;
        } else if (someoneElsesCamera
                && (Math.abs(Rotation.normalizeYaw(camera.getYaw() - this.home.getYaw())) > TAKEN_OVER
                || Math.abs(camera.getPitch() - this.home.getPitch()) > TAKEN_OVER)) {
            this.reset();
            return null;
        }

        this.age++;
        if (this.age > MAX_AGE) {
            this.reset();
            return null;
        }

        final Rotation wanted = step.step(this.sent, this.aim, this.legAge++);
        if (this.reached(wanted)) {
            if (this.hold > 0) {
                this.hold--;
            } else if (this.aim != this.home) {
                this.aim = this.home;   // nothing else to see here, head back
                this.legAge = 0;        // and that is a turn of its own, which spins up like one
            } else {
                // Home again. Hand back the exact rotation we started from one last time, so the
                // server is not left holding a fraction of a degree of leftover offset, and only
                // then go quiet for a while: this should read as somebody glancing up from what
                // they were doing, not as a surveillance camera sweeping.
                final Rotation home = this.home;
                this.reset();
                return home;
            }
        }
        this.sent = wanted;
        return wanted;
    }

    /**
     * Somewhere to look as soon as a glance may start, ahead of the clock: somebody who has just arrived,
     * say - see {@link Newcomer}. Asked for where exactly when the glance starts, so that it is where they
     * are and not where they were, and dropped if that is nowhere, or after {@link #PROMPT_LASTS} ticks
     * untaken. A glance already out finishes first; everything else about a glance - that it is only taken
     * when one is allowed, its hold, its coming home - is unchanged, which is what makes this safe to ask.
     */
    public void prompt(Supplier<Rotation> where) {
        this.prompted = where;
        this.promptAge = 0;
    }

    /**
     * Down at the hand, and never up.
     * <p>
     * A person already looking at the floor sees the item by doing nothing at all, so where the eye is below
     * the hand the glance is the angle they are already holding - it arrives at once, is held for its
     * moment, and comes back, which is the gesture of somebody checking rather than a head that dutifully
     * lifts to the item and drops again.
     */
    private Rotation hand(Rotation camera) {
        final float drop = HAND_PITCH_MIN + this.rand.nextFloat() * (HAND_PITCH_MAX - HAND_PITCH_MIN);
        return new Rotation(
                Rotation.normalizeYaw(camera.getYaw() + (this.rand.nextFloat() * 2.0f - 1.0f) * HAND_YAW),
                Rotation.clampPitch(Math.min(camera.getPitch(), -drop))
        );
    }

    private boolean reached(Rotation rotation) {
        return Rotation.normalizeYaw(this.aim.getYaw() - rotation.getYaw()) == 0.0f
                && Math.abs(this.aim.getPitch() - rotation.getPitch()) < 1e-4f;
    }

    /**
     * Drops any glance in progress, where it stands, and starts the wait over.
     * <p>
     * This is what a tick that is not allowed to glance does, and it is also what a caller does on the
     * ticks it knows a glance is not its to take - it needs no camera to be handed one for that.
     */
    public void drop() {
        if (this.aim != null) {
            this.reset();
        }
    }

    /**
     * Drops any glance in progress and starts the cooldown again.
     * <p>
     * Public because a glance does not survive its world: the player it was taken against is gone, and the
     * rotation it would come back to is an angle in a level that no longer exists.
     */
    public void reset() {
        this.aim = null;
        this.home = null;
        this.sent = null;
        this.hold = 0;
        this.age = 0;
        this.legAge = 0;
        this.cooldown = COOLDOWN_MIN + this.rand.nextInt(COOLDOWN_MAX - COOLDOWN_MIN + 1);
    }
}
