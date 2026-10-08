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
 * The turn to whoever just hit us: the one look a person takes without choosing to.
 * <p>
 * Everything else this head does is something its owner meant to do - the aim at what is being worked
 * on, the sway of a walk, a glance along a road - and the two glances cover both the looks people take
 * while standing about and the ones they take while walking. A blow is neither. It is not a look at
 * the side of the road, it does not wait for the walk to be a plain one, and it is not what somebody
 * deciding to look does: it is <i>being looked at</i> arriving over a shoulder. The fork had one
 * behaviour for both - a blow was delivered as a prompt to whichever glance was running - and the
 * limits of a glance are exactly the limits a blow does not have: the walking glance refuses an
 * interest further off than {@link WalkGlance#MAX_INTEREST_YAW} and is only advanced at all on a walk
 * the executor has welcomed, and the standing one steps at the speed of a look. So a bot punched from
 * the side or from behind while walking did not turn its head at all, and a standing one took the
 * better part of a second. This is the look that is not a glance.
 * <p>
 * <b>How far it goes.</b> A person whose feet are still can turn their whole body, and that is what
 * they do, so with the feet still this is unbounded: the head goes to whoever hit it, however far
 * round that is. A person whose feet are moving cannot - their body is their walk, and a head that
 * swivels the whole way round on a walking body is a body walking backwards, the one picture this
 * fork's walking work exists not to draw - so while the feet are moving the turn stops at
 * {@link #NECK_YAW}: the same quarter turn {@link WalkGlance} holds itself to, and for the same
 * reason, a neck's limit without the body going with it.
 * <p>
 * <b>How it moves.</b> Nothing for a drawn reaction of {@link #ONSET_MIN} to {@link #ONSET_MAX} ticks
 * first, because a head that moves in the same tick the blow lands has no reaction time in it at all,
 * and then out at {@link #STEP}, the hand's own ceiling - a flick, because that is what a flinch is,
 * and deliberately not the walking glance's step: a startle is the one time a person's head moves as
 * fast as it can. Held for {@link #HOLD_MIN} to {@link #HOLD_MAX} ticks, drawn when the blow lands
 * and counted from the moment the head arrives, with the attacker tracked while it holds, because a
 * person keeps their eyes on whoever hit them and keeps them there while that person moves about.
 * Then back at {@link #HOME_STEP}, the speed of a look: coming home is not a flinch, and a head
 * snapped back onto its aim is the other half of the same tell. A second blow while the head is
 * already on somebody is the same flinch held again, at whoever it names last, and it draws itself no
 * second reaction: a person punched twice in a second does not flinch twice. The sway of the body
 * under the head keeps running throughout, so the head is never quite still.
 * <p>
 * <b>What it never does.</b> It is not added while anything is being clicked - the trace Baritone
 * aims its own pickaxe and blocks along follows the rotation it last told the server, so a head
 * turned to a fight during a break aims the pickaxe at the fight - and every click in this mod waits
 * for the head instead. {@link #advance}'s {@code wanted} is that gate: not wanted, the flinch stands
 * down and comes home rather than vanishing, and if the head becomes free again while the stare is
 * still owed it resumes, so a bot that was hit mid-block looks round at whoever did it once the block
 * is done, which is what a person does. Free of Minecraft, so the rules can be tested;
 * {@code FlinchTest} covers them.
 */
public final class Flinch {

    /**
     * How far the head may turn while the feet are moving, in degrees: a neck's limit, the same
     * quarter turn {@link WalkGlance} allows itself and for the same reason. With the feet still
     * there is no limit at all - see the class note.
     */
    public static final float NECK_YAW = 70f;

    /**
     * How long after the blow the head starts coming round, in ticks, drawn when the blow lands: two to
     * six, which is a flinch and not a decision. Quicker than noticing somebody arrive - a blow is not in
     * the corner of the eye - and it is here rather than left out because a head that starts moving in the
     * same tick the blow lands has no reaction time in it at all, which is exactly as machine as never
     * looking round, only in the other direction. A second blow while a flinch is already owed does not
     * draw itself a second reaction: a person punched twice in a second does not flinch twice.
     */
    public static final int ONSET_MIN = 2;
    public static final int ONSET_MAX = 6;

    /**
     * How long the head stays on whoever hit it, in ticks, drawn when the blow lands: half a second to
     * a second and a half. A stare, not a flick of the eyes, and counted from the moment the head
     * arrives rather than from the blow.
     */
    public static final int HOLD_MIN = 10;
    public static final int HOLD_MAX = 30;

    /**
     * How fast the flinch turns and tilts, in degrees a tick: the hand's own ceiling, the fastest this
     * mod's head is ever allowed to move, which is what a startle is.
     */
    public static final float STEP = AimDynamics.MAX_YAW_STEP;
    public static final float PITCH_STEP = AimDynamics.MAX_PITCH_STEP;

    /**
     * How fast the head comes home, in degrees a tick: the speed of a look rather than of a flinch.
     */
    public static final float HOME_STEP = AimDynamics.IDLE_YAW_STEP;
    public static final float HOME_PITCH_STEP = AimDynamics.IDLE_PITCH_STEP;

    /**
     * How close to the attacker the head counts as being on them, in degrees. A degree: the tremor is
     * still shaking the hand, so it never sits exactly, and this is a stare rather than an aim.
     */
    public static final float ARRIVED = 1.0f;

    /**
     * The longest one blow's flinch is ever on the head, in ticks: a safety net, not a target. The
     * hold ends it long before this unless the attacker keeps moving out from under the stare.
     */
    public static final int MAX_AGE = 200;

    private final Random rand;

    private Supplier<Rotation> them; // whoever hit us, asked again every tick
    private boolean answering;
    private float yaw;   // the offset this is holding on the head, in degrees
    private float pitch;
    private int onset;   // ticks still to go before the head starts coming round
    private int hold;
    private int held;
    private int age;

    public Flinch() {
        this(new Random());
    }

    /**
     * @param rand the source of the held lengths, injectable so a run can be repeated; see
     *             {@code FlinchTest}
     */
    public Flinch(Random rand) {
        this.rand = rand;
    }

    /**
     * We have just been hit, and this is where whoever did it is: the bearing to them, asked again
     * every tick so that a target who walks about is tracked rather than remembered. A second blow
     * before the head has come home is the same flinch held again - the stare restarts, at whoever
     * this one names, without the head jumping anywhere to do it - and it does not start itself a
     * second reaction, because the flinch it is arriving on is still being flinched.
     */
    public void blow(Supplier<Rotation> them) {
        if (!this.answering) {
            this.onset = ONSET_MIN + this.rand.nextInt(ONSET_MAX - ONSET_MIN + 1);
        }
        this.them = them;
        this.answering = true;
        this.hold = HOLD_MIN + this.rand.nextInt(HOLD_MAX - HOLD_MIN + 1);
        this.held = 0;
        this.age = 0;
    }

    /**
     * One tick of the flinch.
     *
     * @param base   the rotation the head is holding without this offset, and it has to be the rotation
     *               this offset is added to - the aim, or the aim plus whatever a glance is putting on
     *               the head - or the stare lands beside the attacker rather than on them. The sway is
     *               deliberately not in it: the sway keeps riding on the stare. Only read while
     *               {@code wanted}, so a caller with nothing to measure may pass {@code null}
     * @param moving whether the feet are moving. True, the turn stops at {@link #NECK_YAW}; false,
     *               the whole body may come round
     * @param wanted whether the head is free for this at all. False while anything is being clicked
     *               or while the head is somebody else's: the flinch stands down and comes home, and
     *               a stare that is still owed is taken up again if the head comes free in time
     */
    public void advance(Rotation base, boolean moving, boolean wanted) {
        if (!this.answering || !wanted) {
            this.comeHome();
            return;
        }
        if (++this.age > MAX_AGE) {
            // the safety net: a stare that can never be taken has to end somewhere
            this.answering = false;
            this.them = null;
            this.comeHome();
            return;
        }
        if (this.onset > 0) {
            // the reaction: nothing moves yet. A head still on its way home from the last blow goes on
            // coming home until this one's turn starts.
            this.onset--;
            this.comeHome();
            return;
        }
        final Rotation at = this.them == null ? null : this.them.get();
        if (at == null || this.held >= this.hold) {
            // whoever hit us is gone, or the stare has been held long enough: home at the speed of a look
            this.answering = false;
            this.them = null;
            this.comeHome();
            return;
        }
        float wantedYaw = Rotation.normalizeYaw(at.getYaw() - base.getYaw());
        if (moving) {
            // as far as a neck goes, and no further: the body is the walk
            wantedYaw = Math.max(-NECK_YAW, Math.min(NECK_YAW, wantedYaw));
        }
        final float wantedPitch = at.getPitch() - base.getPitch();
        this.yaw = step(this.yaw, wantedYaw, STEP);
        this.pitch = step(this.pitch, wantedPitch, PITCH_STEP);
        if (Math.abs(wantedYaw - this.yaw) <= ARRIVED && Math.abs(wantedPitch - this.pitch) <= ARRIVED) {
            this.held++;
        }
    }

    /**
     * Whether the flinch is holding an angle on the head at all, including while it is on its way
     * home. False means nothing of this is on the head and none of it has to be applied.
     */
    public boolean out() {
        return this.yaw != 0f || this.pitch != 0f;
    }

    /**
     * Whether a blow is still owed a look: true from the blow until the stare has been taken or
     * whoever did it has gone. The caller reads it to stand the glances down for the episode - one thing
     * on the head at a time while somebody is being stared at - and the run in {@code FlinchTest} and
     * {@code HumanlikeLookFuzzTest} reads it to know when a blow is still owed.
     */
    public boolean answering() {
        return this.answering;
    }

    /**
     * @return the yaw to add to the base, in degrees
     */
    public float yaw() {
        return this.yaw;
    }

    /**
     * @return the pitch to add to the base, in degrees
     */
    public float pitch() {
        return this.pitch;
    }

    /**
     * The world this was owed in has gone, so nobody has hit us in this one. The rest of the head's
     * state is dropped on the same event and for the same reason; see {@code LookBehavior#onWorldEvent}.
     */
    public void reset() {
        this.them = null;
        this.answering = false;
        this.yaw = 0f;
        this.pitch = 0f;
        this.onset = 0;
        this.hold = 0;
        this.held = 0;
        this.age = 0;
    }

    private void comeHome() {
        if (!this.out()) {
            return;
        }
        this.yaw = step(this.yaw, 0f, HOME_STEP);
        this.pitch = step(this.pitch, 0f, HOME_PITCH_STEP);
    }

    /**
     * A step of at most {@code most} toward {@code target}, and exactly it when it is nearer than
     * that: the same shape every hand movement in this fork has, and it lands rather than buzzing
     * around the target a step at a time.
     */
    private static float step(float current, float target, float most) {
        final float delta = target - current;
        if (delta > most) {
            return current + most;
        }
        if (delta < -most) {
            return current - most;
        }
        return target;
    }
}
