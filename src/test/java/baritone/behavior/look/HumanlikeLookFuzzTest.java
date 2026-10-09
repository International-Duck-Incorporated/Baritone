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
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Drives all six look state machines together through a long, hostile run and asserts the properties the
 * game depends on, whatever the input.
 * <p>
 * The individual tests cover each rule on its own. This covers the things nobody thinks to write a rule
 * for: that nothing ever produces a NaN, that no state can be entered from which the aim stops moving, that
 * a target which never settles still gets arrived at, that the standing glance and the walking sway never
 * drive the head together, and that no sequence of player states can leave the head - or one of the state
 * machines that moves it - stuck. Those are the
 * failures that would not look like a bug in a short playtest; they would look like Baritone being strange
 * a few minutes in.
 * <p>
 * It is also where the offsets are added up the caller's way, which no single machine's own test can check:
 * the aim, the two glances, the sway and the stare land on one head in the caller's order and against the
 * caller's bases, so an offset added twice, or measured against a head it is not added to, shows up as a
 * jump or as a stare that settles beside the attacker instead of on them.
 * <p>
 * It also drives the lean the executor offers between the movement's own rotation and the aim: where the
 * product goes movement rotation -> GazeAhead#lean (the line ahead, or the block the job is about) ->
 * the aim machinery, the run now keeps a walk direction that persists across ticks, offers onto it the
 * way the executor's running ticks do, and asserts the stand-aside (a click's target is aimed at exactly)
 * and the lean's caps over every hostile draw. Unit tests can only ever test one offer; what this run owns
 * is that no sequence of offers - ramped like the approach's, jumped like a repath's, flapped like a walk
 * straddling the ramp's edge - can starve the turn, freeze the hand, or carry it past the lean.
 * <p>
 * What it does not model: the pitch, where the settle of a gaze nobody has an opinion about is covered by
 * PitchWanderTest while the aim here is asked for the target's own pitch, which is the case that has an
 * opinion - a block to break, a face to place against; the person's own camera, since everything here is
 * driven on one head; the executor's conditions for welcoming a glance, which are its business while the
 * glance's own cooldown sets the rate; the feet stopping for a look worth it (WalkGlance#stopped, which the
 * caller's PathExecutor acts on) - the speed here is drawn, never held by a glance; the ground under the
 * walk, so the lean's offer conditions (feet in the air, water, parkour, a vertical or zero step, the walk
 * already standing in the job's block) are drawn as offer/no-offer rather than modelled - they are pinned
 * by reading in ApproachLookTest; and which block the goal says the job is about, which is
 * ApproachLookTest's first three tests. The halves of the caller's gates that need the ground under it are
 * pinned by reading instead. See the notes where each of those is dealt with below.
 */
public class HumanlikeLookFuzzTest {

    private static final double[] SENSITIVITIES = {0.0, 0.05, 0.5, 0.87, 1.0};
    private static final int TICKS = 20000;

    /** A walking pace: four point three blocks a second, twenty ticks to the second. */
    private static final double BLOCKS_PER_TICK = 4.317 / 20.0;

    /** What one tick of the caller's logic did. */
    private static class Outcome {
        Rotation applied;
        boolean swayAdvanced;
        boolean swayDecayed;
        /** Whether the sway that this tick advanced, if any, was the standing-still one. */
        boolean idleSway;
        /** Whether the gates that advance the sway - and so the flinch - were open this tick. */
        boolean flinchGate;
        /** Whether a flinch was holding an angle after this tick's advance, and what it was holding. */
        boolean flinchOut;
        /** Whether the flinch was still owed a look this tick: from the blow until it has been taken. */
        boolean flinchAnswering;
        float flinchYaw;
        /** Whether the walk's glance was allowed this tick - what LookBehavior asks before it advances. */
        boolean walkGlanceGate;
        /** Whether the walk's glance was off the bearing at all after this tick. */
        boolean walkGlanceOut;
        float walkGlanceYaw;
    }

    private static Rotation target(Random rand) {
        return new Rotation(rand.nextFloat() * 360f - 180f, rand.nextFloat() * 160f - 80f);
    }

    private static boolean finite(Rotation rotation) {
        return !Float.isNaN(rotation.getYaw()) && !Float.isInfinite(rotation.getYaw())
                && !Float.isNaN(rotation.getPitch()) && !Float.isInfinite(rotation.getPitch());
    }

    /**
     * One tick of the caller's PRE logic: the aim, the glance, the reaction and the sway, wired exactly as
     * LookBehavior wires them and behind the same gates.
     * <p>
     * The two head-movers take different first gates, and they are not interchangeable. The glance asks
     * whether Baritone is in control at all - executing, calculating, or deliberately waiting - because in
     * all three the body is Baritone's and the bot used to stand frozen in the last two. The sway asks
     * whether a path is actually executing, because a sway is a walking thing and walking is what
     * executing means.
     *
     * @return the rotation the server would be told about, or null when nothing moved the head
     */
    private static Rotation tick(Random rand, double sensitivity, double speedSquared, Rotation wanted,
                                 Rotation camera, boolean pathing, boolean inControl, boolean interacting,
                                 boolean blockInteract,
                                 Rotation[] aim, int[] aimAge, Rotation[] lastWanted, Rotation[] turnFrom,
                                 TurnPace[] turnPace,
                                 IdleGlance[] glance, ReactionDelay[] reaction, HeadWander[] wander,
                                 int[] glanceCamera, float[] sway, Flinch flinch, WalkGlance walkGlance,
                                 Rotation attacker, Rotation[] them) {
        final Outcome outcome = new Outcome();
        // A blow lands, if one does this tick: the caller hands the flinch the bearing, and it is asked for
        // again on every tick of the stare - here for an attacker who stands still, because what one who
        // walks about does to it is FlinchTest's business. It is delivered before the rest of the tick
        // because that is when the caller's own IN event sees it, and the onset counts from there.
        if (attacker != null) {
            flinch.blow(() -> them[0]);
        }
        // the glance is only ever consulted when there is no target at all, so a tick with a target must not
        // inherit the previous tick's answer
        glanceCamera[0] = 0;
        final boolean standingStill = speedSquared < 0.02 * 0.02;
        final boolean walking = speedSquared > 0.02 * 0.02;

        Rotation applied = null;

        // the aim, exactly as handlePlayerUpdate does it
        if (wanted != null) {
            final Rotation from = aim[0] != null ? aim[0] : camera;
            if (aim[0] == null || AimDynamics.separation(lastWanted[0], wanted) > ReactionDelay.NEW_DIRECTION_ANGLE) {
                aimAge[0] = 0;
                turnFrom[0] = from;
                // and the tempo the turn will be made at, drawn once for the turn rather than once a tick:
                // the same ninety degrees is not the same eight ticks of the same eight steps every time
                turnPace[0].begin();
            }
            if (standingStill && reaction[0].holding(wanted)) {
                aim[0] = from;
                aimAge[0] = 0;
                turnFrom[0] = from;
                turnPace[0].begin();
            } else {
                // What the hand is asked for is not always exactly what is being aimed at: while the body
                // is walking and nothing is being clicked, a turn that is only a direction to travel is
                // carried a little past where it is going. Anything about to be acted on - a face to place
                // against, a block to break - is aimed at exactly, because the rotation this returns is the
                // one Baritone traces its own pickaxe and blocks along. The pitch side of the same rule,
                // which settles a gaze that nobody has an opinion about, is modelled in PitchWanderTest;
                // here the pitch echoes the target, which is the blockInteract case.
                final boolean carried = !blockInteract && !interacting && walking;
                final Rotation to = carried ? AimDynamics.overshoot(turnFrom[0], wanted, aimAge[0]) : wanted;
                assertTrue("a carried turn went " + AimDynamics.separation(to, wanted)
                                + " deg past what it was walking at",
                        AimDynamics.separation(to, wanted) <= AimDynamics.OVERSHOOT_MAX + 0.01f);
                final Rotation next = AimDynamics.humanStep(from, to, aimAge[0], sensitivity,
                        interacting ? AimDynamics.CLICK_EASE : AimDynamics.AIM_EASE, turnPace[0].pace());
                assertTrue("the aim moved further than a flick in one tick",
                        AimDynamics.separation(aim[0] != null ? aim[0] : from, next) <= AimDynamics.MAX_YAW_STEP + 0.01f);
                aim[0] = next;
                aimAge[0]++;
            }
            lastWanted[0] = wanted;
            applied = aim[0];
        } else {
            aim[0] = null;
            aimAge[0] = 0;
            turnFrom[0] = null;
            lastWanted[0] = null;
            reaction[0].clear();
            // the glance, which needs Baritone in control, the player still, and nothing being clicked - the
            // last of those because the trace Baritone breaks and places along follows the rotation this code
            // last sent the server, and the first because a person playing manually is not ours to animate
            final Rotation sent = glance[0].next(camera, inControl && standingStill && !interacting, (from, to, legAge) -> {
                final Rotation next = AimDynamics.step(from, to, AimDynamics.IDLE_YAW_STEP, AimDynamics.IDLE_PITCH_STEP,
                        sensitivity);
                // the glance is never allowed the aiming speed, at any sensitivity
                assertTrue("a glance moved " + AimDynamics.separation(from, next) + " deg in one tick",
                        AimDynamics.separation(from, next) <= AimDynamics.IDLE_YAW_STEP + 1e-3f);
                return next;
            });
            if (sent != null) {
                applied = sent;
                glanceCamera[0] = 1;
            } else {
                glanceCamera[0] = 0;
            }
        }

        // The sway, last, and behind one of three gates. Walking along what is being aimed at is the
        // stride, at full amplitude. Standing still waiting for it - a path with nowhere to go this tick -
        // is the same sway at HeadWander.IDLE_FRACTION. And a bot with nothing aimed at at all, standing
        // about under Baritone's control with nothing clicked, runs it at half under the glance. Nothing
        // being clicked is the gate they all share, because the rotation they are adding to is the one
        // Baritone traces its own pickaxe and blocks along.
        //
        // The body's half of the stride gate is modelled here as speed alone, which is what the two flags
        // below can express: the caller (LookBehavior#canWander) counts a player who is off the ground as
        // moving whatever their speed - a fall, a jump, a climb, where the yaw steers a heading just as it
        // does on foot - and this harness has no ground to stand on. So its walking ticks model the caller's
        // ground and water halves, and the airborne half is pinned by reading rather than by these runs.
        // The flinch is not one of the deliberate head-movers - it is a blow - but it rides these same
        // three gates, because the caller advances it with the sway's own flags, so the gates above are its
        // gates and this is where they are exercised. Its own rules - the drawn reaction, the flick at the
        // hand's ceiling, the neck's bound while the feet move, the hold, the stand-down, the handback - are
        // measured hard in FlinchTest; what this run has that that one cannot is the caller's half of it:
        // the flags it is advanced with, that a stare that is out while they are shut only shrinks and
        // comes off rather than sitting there, and that the head all of these add up to cannot jump further
        // in one tick than the hand's flick plus a flinch. Its attacker is fixed, because what one who
        // walks about does to a stare is FlinchTest's business.
        final boolean walkingSway = wanted != null && walking && pathing && !interacting;
        final boolean waitingSway = wanted != null && standingStill && inControl && !interacting;
        final boolean idleSway = wanted == null && inControl && standingStill && !interacting;
        outcome.idleSway = idleSway || waitingSway;
        final float fraction = wanted != null && walking ? 1f : HeadWander.IDLE_FRACTION;
        final float before = wander[0].yaw();
        if (walkingSway || waitingSway || idleSway) {
            wander[0].advance();
            outcome.swayAdvanced = true;
        } else {
            wander[0].recentre();
            outcome.swayDecayed = Math.abs(wander[0].yaw()) <= Math.abs(before) + 1e-5f;
        }
        sway[0] = fraction * wander[0].yaw();
        final float swayPitch = fraction * wander[0].pitch();
        outcome.applied = null;

        // The walk's glance, advanced before the stare and on the caller's own gates: welcome by the walk,
        // nothing about to be clicked aimed at, the head not the person's own (Mode.NONE), and - since the
        // stare is measured against the angle this is putting on the head - no blow still owed a look. It
        // keeps its own out-clause once the feet have stopped for a look that is already out, and with
        // nothing aimed at the caller only ever tells it that it is not welcome.
        // The walk's glance: clocked every tick, and welcomed on every tick the walk under way is a plain
        // one - which is how the caller's executor offers it, on the ground and three plain movements ahead
        // rather than now and then, and about the movements rather than about what is being aimed at. A
        // welcome not repeated is gone, so this is every tick of a plain walk; and whether it is welcome is
        // asked again below, on the caller's own gate, with the target and the click and the stare in it.
        walkGlance.tick();
        if (pathing && walking && !interacting) {
            walkGlance.welcome();
        }
        final boolean onTheWalk = walkingSway || (waitingSway && walkGlance.out());
        final boolean walkGlanceAllowed = wanted != null && onTheWalk && walkGlance.welcomed()
                && !blockInteract && !flinch.answering();
        walkGlance.advance(walkGlanceAllowed, () -> {
            if (rand.nextInt(10) < 3) {
                return null; // nothing in particular, which is most of a walk
            }
            return new Rotation(rand.nextFloat() * 2f * WalkGlance.MAX_INTEREST_YAW - WalkGlance.MAX_INTEREST_YAW,
                    rand.nextFloat() * 2f * WalkGlance.MAX_INTEREST_PITCH - WalkGlance.MAX_INTEREST_PITCH);
        });
        outcome.walkGlanceGate = walkGlanceAllowed;
        outcome.walkGlanceOut = walkGlance.out();
        outcome.walkGlanceYaw = walkGlance.out() ? walkGlance.yaw() : 0f;

        // The flinch, advanced on the same three gates the sway is advanced on and measured against the head
        // it is added to - the walk's glance included, because that is on the head too - exactly as the
        // caller measures it: the bearing is asked for again every tick, so a stare rides on whatever the
        // head is doing rather than on where it was when the blow landed. `moving` is the stride gate - the
        // feet, which this harness has only as speed. It is advanced whenever the head is ours at all, which
        // is what lets a stand-down happen where it is owed. With the gates shut it comes home instead of
        // turning, and what is left of it is still added, because the offset is physically on the rotation
        // the caller last wrote: a head that was mid-stare when the pickaxe came out comes off whoever hit it
        // at the speed of a look rather than vanishing, and a tick that writes no head at all carries none
        // of it. The sway is deliberately not in the base the stare is measured from: it is meant to keep
        // riding on the stare.
        final boolean flinchGate = walkingSway || waitingSway || idleSway;
        final Rotation head = applied != null ? applied : camera;
        // The base is the head the offset is added to, and which head that is depends on the caller's path:
        // with something aimed at, the walk's glance is on this head, so the stare is measured against the
        // head plus that glance; with nothing aimed at, the caller only tells the glance it is not welcome,
        // and the stare rides on what the idle path is holding.
        final Rotation stareFrom = wanted == null ? head
                : new Rotation(head.getYaw() + walkGlance.yaw(), head.getPitch() + walkGlance.pitch());
        flinch.advance(stareFrom, wanted != null && walking, flinchGate);
        outcome.flinchGate = flinchGate;
        outcome.flinchOut = flinch.out();
        outcome.flinchAnswering = flinch.answering();
        outcome.flinchYaw = flinch.out() ? flinch.yaw() : 0f;

        // What the head is holding after all of it, which is the caller's own sum: the aim (or whatever the
        // idle path is holding), the walk's glance, the sway, and the stare - and on a stand-down tick, when
        // something is being clicked or the head is not ours, no sway at all, but whatever is still
        // physically on the rotation the caller last wrote comes off rather than vanishing.
        if (wanted == null) {
            // the idle path writes nothing at all unless the head was ours this tick; the walk's glance is
            // not on this head - with nothing aimed at, the caller only tells it that it is not welcome
            applied = idleSway ? new Rotation(head.getYaw() + sway[0] + flinch.yaw(),
                    Rotation.clampPitch(head.getPitch() + swayPitch + flinch.pitch())) : null;
        } else if (walkingSway || waitingSway) {
            applied = new Rotation(head.getYaw() + walkGlance.yaw() + sway[0] + flinch.yaw(),
                    Rotation.clampPitch(head.getPitch() + walkGlance.pitch() + swayPitch + flinch.pitch()));
        } else if (walkGlance.out() || flinch.out()) {
            applied = new Rotation(head.getYaw() + walkGlance.yaw() + flinch.yaw(),
                    Rotation.clampPitch(head.getPitch() + walkGlance.pitch() + flinch.pitch()));
        }
        outcome.applied = applied;
        lastOutcome = outcome;
        return applied;
    }

    /** The last tick's outcome, for the assertions that need to know what drove the head. */
    private static Outcome lastOutcome;

    /**
     * The seeds the hostile run is repeated from. The first is the long reference run; the rest are shorter,
     * and are there for the rules that hold for one sequence of states and not for another - which, in a
     * fork whose numbers are all drawn, is most of them.
     */
    private static final long[] SEEDS = {20261006L, 3L, 11L, 101L, 4242L, 7919L};

    /** How long the runs after the first one are, in ticks: five minutes each, at five sensitivities. */
    private static final int SWEEP_TICKS = 6000;

    /** How often the run writes down where the head was, for the fingerprint its callers compare. */
    private static final int FINGERPRINT_EVERY = 137;

    @Test
    public void nothingSurvivesTwentyThousandHostileTicks() {
        final String[] heads = new String[SEEDS.length];
        for (int i = 0; i < SEEDS.length; i++) {
            heads[i] = run(SEEDS[i], i == 0 ? TICKS : SWEEP_TICKS);
        }
        // And no two seeds are the same head. Everything in the run draws from the seed it was given, so a
        // seed that changed nothing means one of the five machines has gone back to the clock or to a fixed
        // sequence - and a head that is the same head every time is the one thing nobody has to watch twice
        // to know it is a machine.
        for (int a = 0; a < heads.length; a++) {
            for (int b = a + 1; b < heads.length; b++) {
                assertFalse("seeds " + SEEDS[a] + " and " + SEEDS[b] + " produced the same head",
                        heads[a].equals(heads[b]));
            }
        }
    }

    /**
     * One hostile run: {@code ticks} ticks at each sensitivity, every machine in it drawing from {@code
     * seed}, and the invariants asserted as it goes. The assertions are the point of the run; the string it
     * returns is a coarse fingerprint of what the head did, which is how a caller tells one run from another
     * without caring about a fraction of a degree.
     */
    private static String run(long seed, int ticks) {
        final StringBuilder head = new StringBuilder();
        // What the flinch's half of the run has to have actually seen, so that the leg of the run that
        // carries it cannot quietly stop carrying anything: a blow that was never taken is a run that
        // proves nothing about a stare, and a neck's bound that was never reached is a bound nobody tested.
        int blown = 0;
        int leaned = 0;
        int jobs = 0;
        int stared = 0;
        int neckChecked = 0;
        int glanced = 0;
        int interrupted = 0;
        // The stare, per blow: how long it has been owed for, how much of that the head was free for, the
        // closest the head came to the attacker, and whether the neck was what stopped it. Since the guards
        // below ask after the last of those, they live out here with the other counters and are reset when a
        // blow lands.
        // (the stare's own landing is a focused test below rather than a line in here: in a run whose aim
        // is drawn again every few ticks, a stare is judged on whether it arrives, which is a chase, and
        // the chase is FlinchTest's; what this run holds about the stare is where its angle may be added
        // and what the head does while something is being clicked)
        for (double sensitivity : SENSITIVITIES) {
            final Random rand = new Random(seed);
            Rotation aim = null;
            int aimAge = 0;
            Rotation lastWanted = null;
            Rotation turnFrom = null;
            // Every machine draws from the run's own seed, each from a number of its own cut from it: a
            // failure reported for a seed has to be reproducible by running that seed, and a machine that
            // draws from the clock instead makes the run a different run every time somebody reads it.
            final IdleGlance glance = new IdleGlance(new Random(seed + 1));
            final ReactionDelay reaction = new ReactionDelay(new Random(seed + 2));
            final HeadWander wander = new HeadWander(new Random(seed + 3));
            final TurnPace turnPace = new TurnPace(new Random(seed + 4));
            final Flinch flinch = new Flinch(new Random(seed + 5));
            final WalkGlance walkGlance = new WalkGlance(new Random(seed + 6));
            final GazeAhead gazeAhead = new GazeAhead();
            // the lean's coherent state: a walk goes somewhere and keeps going there, and the offer made
            // against it persists, ramps and drifts the way the executor's offers on a running tick do
            Rotation walkDirection = null;
            Rotation aheadBearing = null;
            double leanWeight = 0;
            boolean jobLook = false;
            int glanceActive = 0;
            float sway = 0f;

            Rotation camera = new Rotation(0f, 0f);
            int reactionRun = 0;
            double sideways = 0;
            Rotation previousApplied = null;
            boolean previousWanted = false;
            float previousFlinchYaw = 0f;
            int shutRun = 0;
            int glanceShutRun = 0;
            boolean previousGlanceRunning = false;
            boolean stareWalk = false;        // whether the blow the stare is owed for landed on a walk
            boolean walkingDuringStare = false;
            final Rotation[] them = {null};  // who the stare is on now, re-asked by the flinch every tick

            for (int t = 0; t < ticks; t++) {
                final boolean pathing = rand.nextInt(10) > 2;
                // in control is the glance's gate and has to be visited on its own: executing implies it,
                // and the states this widened gate exists for are the ones where nothing is executing
                final boolean inControl = pathing || rand.nextInt(10) > 4;
                final boolean interacting = rand.nextInt(10) > 8;
                // a target that is about to be acted on is aimed at exactly, which is most of them
                final boolean blockInteract = rand.nextInt(10) > 2;
                // walking, stopping, and standing around, with the speed crossing the idle threshold
                final double speedSquared = rand.nextInt(100) < 45 ? 0.0 : 0.05 + rand.nextDouble() * 0.1;
                // A walk goes somewhere and keeps going there - the direction persists on most ticks,
                // drawn fresh on the rest (a corner, a repath). Fresh random targets every tick would fuzz
                // the lean's coherence away, and coherence is the thing about a walking target that only
                // this run can draw: what the lean does to the aim tick after tick on one approach.
                Rotation rawWanted = null;
                if (rand.nextInt(100) < 70) {
                    rawWanted = walkDirection != null && rand.nextInt(100) < 60 ? walkDirection : target(rand);
                }
                walkDirection = rawWanted;
                // The lean, offered exactly where the executor offers it - pathing, walking, nothing being
                // clicked - then taken by the one thing the product lets take it: LookBehavior#updateTarget
                // leaning the movement's own rotation before the aim ever sees it. The offer's ground-side
                // conditions are drawn as offer/no-offer (see the class javadoc); what the fuzz owns is
                // what the stream of offers does to the aim downstream of them.
                final boolean leanEligible = rawWanted != null && pathing
                        && speedSquared > 0.02 * 0.02 && !interacting;
                boolean leanOffered = false;
                if (leanEligible) {
                    if (aheadBearing == null || rand.nextInt(100) < 6) {
                        // a fresh look: the line ahead two starts in three, the block the job is about the
                        // third. The weight starts at nothing, the way the approach's ramp does - except a
                        // hostile few, which start heavier than any real ramp: if the aim survives those,
                        // the ramped starts are safe
                        jobLook = rand.nextInt(100) < 34;
                        aheadBearing = jobLook ? target(rand)
                                : new Rotation(rawWanted.getYaw() + rand.nextFloat() * 24f - 12f,
                                        rawWanted.getPitch() + rand.nextFloat() * 8f - 4f);
                        leanWeight = rand.nextInt(100) < 8
                                ? 0.05 + rand.nextDouble() * (GazeAhead.LEAN - 0.05) : 0;
                        if (jobLook) {
                            jobs++;
                        }
                    }
                    leanWeight = Math.min(GazeAhead.LEAN, leanWeight + 0.06 + rand.nextDouble() * 0.07);
                    // a job's block sits still (barring a repath); the line ahead moves with the walk
                    if (jobLook) {
                        if (rand.nextInt(100) < 2) {
                            aheadBearing = target(rand);
                        }
                    } else {
                        aheadBearing = new Rotation(aheadBearing.getYaw() + rand.nextFloat() * 10f - 5f,
                                Math.max(-80f, Math.min(80f,
                                        aheadBearing.getPitch() + rand.nextFloat() * 4f - 2f)));
                        if (rand.nextInt(100) < 2) {
                            aheadBearing = new Rotation(rawWanted.getYaw() + rand.nextFloat() * 50f - 25f,
                                    rawWanted.getPitch());
                        }
                    }
                    leanOffered = true;
                    leaned++;
                } else {
                    aheadBearing = null;
                    leanWeight = 0;
                }
                gazeAhead.offer(leanOffered ? aheadBearing : null, leanOffered ? leanWeight : 0);
                final Rotation wanted = gazeAhead.lean(rawWanted, blockInteract);
                // What the lean may do to what the aim sees. About to be clicked: exactly nothing, over
                // ten thousand draws, not one unit case - the stand-aside is the click path's guarantee.
                // Off the click path: no further off the walk than the cap, and the pitch no more than the
                // weight's share of the way to the bearing.
                if (rawWanted != null) {
                    assertTrue("a leaned target is not finite: " + wanted, wanted != null && finite(wanted));
                    if (blockInteract) {
                        assertTrue("the lean rode into a click: " + wanted + " against " + rawWanted,
                                wanted.getYaw() == rawWanted.getYaw()
                                        && wanted.getPitch() == rawWanted.getPitch());
                    }
                    if (leanOffered && !blockInteract) {
                        assertTrue("the lean went "
                                        + Math.abs(Rotation.normalizeYaw(wanted.getYaw() - rawWanted.getYaw()))
                                        + " degrees off the walk",
                                Math.abs(Rotation.normalizeYaw(wanted.getYaw() - rawWanted.getYaw()))
                                        <= GazeAhead.MAX_LEAN_DEGREES + 1e-3f);
                        assertTrue("the pitch leaned past what the weight allows: " + wanted,
                                Math.abs(wanted.getPitch() - rawWanted.getPitch())
                                        <= GazeAhead.LEAN * Math.abs(aheadBearing.getPitch() - rawWanted.getPitch())
                                                + 1e-3f);
                    }
                }
                // A blow lands, sometimes, at a bearing of its own: the caller hands the flinch the bearing
                // and is asked for it again on every tick of the stare, and a fixed attacker is what makes
                // the stare's own tracking FlinchTest's business rather than this run's. Three ticks in a
                // thousand, which is a punch every sixteen seconds of running: rarer than that and the
                // flinch goes untested, and much more often and the bot is simply being beaten up - a stare
                // outlasts the gap between blows, so an answering flinch becomes the standing state and the
                // glances, which stand down while one is owed, never get to run at all.
                final Rotation attacker = rand.nextInt(1000) < 3
                        ? new Rotation(rand.nextFloat() * 360f - 180f, rand.nextFloat() * 120f - 60f) : null;
                if (attacker != null) {
                    blown++;
                    them[0] = attacker; // who the stare is on, asked for again every tick until it is done
                    // whether this blow landed on a walking body, which is the whole of the neck's bound
                    stareWalk = speedSquared > 0.02 * 0.02;
                }
                walkingDuringStare = wanted != null && speedSquared > 0.02 * 0.02;

                final Rotation[] aimRef = {aim};
                final int[] ageRef = {aimAge};
                final Rotation[] lastRef = {lastWanted};
                final Rotation[] turnRef = {turnFrom};
                final TurnPace[] paceRef = {turnPace};
                final IdleGlance[] glanceRef = {glance};
                final ReactionDelay[] reactionRef = {reaction};
                final HeadWander[] wanderRef = {wander};
                final int[] glanceRef2 = {glanceActive};
                final float[] swayRef = {sway};

                final Rotation applied = tick(rand, sensitivity, speedSquared, wanted, camera, pathing, inControl,
                        interacting, blockInteract, aimRef, ageRef, lastRef, turnRef, paceRef, glanceRef, reactionRef,
                        wanderRef, glanceRef2, swayRef, flinch, walkGlance, attacker, them);

                aim = aimRef[0];
                aimAge = ageRef[0];
                lastWanted = lastRef[0];
                turnFrom = turnRef[0];
                glanceActive = glanceRef2[0];
                sway = swayRef[0];

                // where the head was, roughly, for the fingerprint: one look every few seconds is enough to
                // tell one run from another, and rounding to whole degrees keeps it from being a measure of
                // anything but that
                if (t % FINGERPRINT_EVERY == 0) {
                    head.append(applied == null ? "."
                            : Math.round(applied.getYaw()) + "/" + Math.round(applied.getPitch())).append(' ');
                }

                if (applied != null) {
                    assertTrue("a rotation with a NaN or infinity came out of the look code: " + applied,
                            finite(applied));
                    assertTrue("pitch escaped its range: " + applied.getPitch(),
                            applied.getPitch() >= -90f && applied.getPitch() <= 90f);
                    // The head is a sum of raw angles, so it may read outside a half turn by the size of the
                    // offsets on it - which is what the caller does and what the server normalises on use.
                    // What has to hold is that the sum cannot be further round than the pieces could push it:
                    // the aim's own half turn, a glance, the sway, and a stare, which is unbounded while the
                    // feet are still. Each of those is bounded where it is produced, above and in its own
                    // test; this is the sum's.
                    assertTrue("the head is further round than the offsets on it can carry: " + applied.getYaw(),
                            Math.abs(applied.getYaw()) <= 180f + WalkGlance.MAX_INTEREST_YAW + 2f
                                    + HeadWander.MAX_YAW + 180f + 1f);
                }
                if (aim != null) {
                    assertTrue("the aim became non-finite", finite(aim));
                }
                assertTrue("the sway escaped its limit: " + sway, Math.abs(sway) <= HeadWander.MAX_YAW + 1e-4f);

                // A glance and the *walking* sway must never drive the head together: the glance wants the
                // player standing still and the walking sway wants them moving, so a harness that let both
                // through would have lost one of those gates. The standing-still sway is the deliberate
                // exception - one look every few seconds on a head that is otherwise still is exactly what
                // the idle path is for - so what must hold there is that it is the quiet one.
                if (glanceActive == 1 && lastOutcome.swayAdvanced) {
                    assertTrue("a walking sway drove the head while a glance was driving it",
                            lastOutcome.idleSway);
                    assertTrue("the standing sway was as loud as the walking one: " + sway,
                            Math.abs(sway) <= HeadWander.MAX_YAW * HeadWander.IDLE_FRACTION + 1e-4f);
                }

                // A sway that is not being advanced is a sway on its way out. Without this the fade is
                // unobservable and nothing would notice it being replaced by a switch.
                if (!lastOutcome.swayAdvanced && sway != 0f) {
                    assertTrue("the sway grew while it was not being advanced", lastOutcome.swayDecayed);
                }

                // Nobody's head but ours, and only while Baritone is in charge of the body: the glance's
                // first gate is control, not execution, so this is where a harness that quietly went back to
                // asking about paths would show up.
                if (!inControl) {
                    assertFalse("a glance drove the head while no Baritone process was in control",
                            glanceActive == 1);
                }

                // Nothing moves the head while a click is being forced. This is the harness asserting its own
                // wiring against the product's rule rather than the product itself - the two are written out
                // separately on purpose, so that they can disagree here if either one drifts. The focused case
                // for the rule is aGlanceStandsDownWhileSomethingIsBeingClicked.
                if (interacting) {
                    assertFalse("a glance drove the head while a click was forced", glanceActive == 1);
                    assertFalse("the sway was advanced while a click was forced", lastOutcome.swayAdvanced);
                }

                // A path with nowhere to go this tick - a door somebody else has to open, a block that has
                // not updated - is exactly where a bot used to stand absolutely still: with something aimed
                // at, the idle path is never consulted, and standing still is not walking, so every gate was
                // shut and the head was held exactly where it was for as long as the wait lasted.
                if (wanted != null && speedSquared < 0.02 * 0.02 && inControl && !interacting) {
                    assertTrue("nothing was moving the head while waiting with a target on",
                            lastOutcome.swayAdvanced);
                }

                // The flinch's half of the caller's side of the wiring - the flags it is advanced with and
                // where its angle may be added - which is what this run has that FlinchTest does not.
                //
                // The tick that matters here is a click being forced: the trace Baritone breaks and places
                // along follows the rotation it last told the server, so the gates that carry the sway are
                // all shut on it and what a stare does is come off, never turn.
                if (interacting) {
                    assertFalse("a flinch was still being turned while a click was forced", lastOutcome.flinchGate);
                }
                // A stare that is out while the gates are shut only shrinks - nothing is added to the head
                // for it beyond what is already on there - and it reaches exactly nothing rather than sitting
                // at an angle until they open: the stand-down is a come-home, not a hold. Twenty-one ticks is
                // the arithmetic of it: a half turn at the speed of a look.
                if (!lastOutcome.flinchGate && lastOutcome.flinchOut) {
                    assertTrue("a flinch grew while the gates were shut: " + lastOutcome.flinchYaw,
                            Math.abs(lastOutcome.flinchYaw) <= Math.abs(previousFlinchYaw) + 1e-4f);
                    shutRun++;
                    assertTrue("a flinch sat on a shut head for " + shutRun + " ticks", shutRun <= 21);
                } else {
                    shutRun = 0;
                }
                previousFlinchYaw = lastOutcome.flinchYaw;
                // The feet's half of the neck's bound, measured once a walk has had its own worth of ticks
                // to pull in whatever a standing body had turned the whole way round: a blow that lands on
                // a standing head is allowed the whole turn, and the walk it then starts is not.
                // The feet's half of the neck's bound, stated so that it holds from one tick to the next: on
                // a walking tick with the head free, a stare that was inside a neck's worth stays inside it,
                // because every step it takes is measured against a target no further round than the neck.
                // What this deliberately does not judge is a stare that is already past the neck when the
                // feet start moving - pulled in from a standing turn - since that is a turn that has to come
                // back, and FlinchTest's walking bracket is where it is held.
                if (stareWalk && lastOutcome.flinchAnswering && lastOutcome.flinchGate && walkingDuringStare
                        && Math.abs(previousFlinchYaw) <= Flinch.NECK_YAW + Flinch.ARRIVED) {
                    neckChecked++;
                    assertTrue("a flinch on a walking head reached " + lastOutcome.flinchYaw + " degrees round",
                            Math.abs(lastOutcome.flinchYaw) <= Flinch.NECK_YAW + Flinch.ARRIVED + 1e-3f);
                }
                if (lastOutcome.flinchGate && lastOutcome.flinchOut) {
                    stared++;
                }
                // The head that the aim, the two glances, the sway and the flinch add up to cannot jump
                // further in one tick than the hand's flick plus a glance plus a flinch: an offset added
                // twice, or added against a base it was not measured against, is a jump like that, and
                // nothing else in this run would see it. Measured only while the same kind of path drives
                // both ticks - the harness's aims start from the camera, which by construction is not where
                // the last aim finished, so a tick that hands over from one kind to the other is a seam in
                // the model, not a movement.
                if (applied != null && previousApplied != null && (wanted != null) == previousWanted) {
                    assertTrue("the head jumped " + AimDynamics.separation(previousApplied, applied)
                                    + " degrees in one tick",
                            AimDynamics.separation(previousApplied, applied) <= AimDynamics.MAX_YAW_STEP
                                    + Flinch.STEP + WalkGlance.MAX_STEP + HeadWander.MAX_YAW + 1f);
                }
                previousApplied = applied;
                previousWanted = wanted != null;

                // The walk's glance. Its own rules - the cooldown, the phases, the amplitudes, the step -
                // are WalkGlanceTest's; what this run holds is its place in the caller's wiring: that what
                // it puts on the head is within what a glance may turn to (an interest within its own
                // bounds, plus the couple of degrees of jitter the amplitude carries), that it comes home
                // once it is not welcome rather than sitting out there, and that the stare is measured
                // against the head it is on.
                if (lastOutcome.walkGlanceOut) {
                    assertTrue("the walk's glance reached " + lastOutcome.walkGlanceYaw + " degrees round",
                            Math.abs(lastOutcome.walkGlanceYaw)
                                    <= WalkGlance.MAX_INTEREST_YAW + 2f + 1e-3f);
                }
                // A glance that was running when the gate shut: the run's dice shut it often, which is the
                // hostile half of this - in the game the welcome is a plain walk's and outlasts a glance.
                if (previousGlanceRunning && !lastOutcome.walkGlanceGate) {
                    interrupted++;
                }
                previousGlanceRunning = lastOutcome.walkGlanceGate && lastOutcome.walkGlanceOut;
                if (lastOutcome.walkGlanceOut && !lastOutcome.walkGlanceGate) {
                    // from the widest a glance may be, at the speed it recovers at: ten ticks is the
                    // arithmetic, and one more is the tick the recovery starts on
                    assertTrue("the walk's glance was still out " + glanceShutRun
                                    + " ticks after it stopped being welcome",
                            ++glanceShutRun <= 1 + (int) Math.ceil(
                                    (WalkGlance.MAX_INTEREST_YAW + 2f) / WalkGlance.RECOVER_STEP) + 1);
                } else {
                    glanceShutRun = 0;
                }
                if (lastOutcome.walkGlanceGate && lastOutcome.walkGlanceOut) {
                    glanced++;
                }

                // walking or not, the sway must not be able to push the walk off its line
                sideways += BLOCKS_PER_TICK * Math.sin(Math.toRadians(sway));
                assertTrue("the walk strayed " + Math.abs(sideways) + " blocks sideways",
                        Math.abs(sideways) < 1.0);

                // the reaction may never hold for longer than its cap, however the target behaves
                if (wanted != null && aim != null) {
                    reactionRun = speedSquared < 0.02 * 0.02 && reaction.holding(wanted) ? reactionRun + 1 : 0;
                    assertTrue("the reaction held the aim still for " + reactionRun + " ticks",
                            reactionRun <= ReactionDelay.HOLD_MAX);
                } else {
                    reactionRun = 0;
                }
            }
        }
        // And the run above really did carry a flinch and really did reach the neck's bound with one: the
        // reference run is the one long enough to promise it, and a run that stopped seeing blows - a gate
        // edited, a blow moved behind one - fails here instead of passing by never exercising the leg.
        assertTrue("no blow landed in the whole run", blown > 0);
        assertTrue("no blow was ever stared at", stared > 0);
        assertTrue("the walk's glance never once went out", glanced > 0);
        assertTrue("the lean was never offered in the whole run", leaned > 0);
        if (ticks == TICKS) {
            assertTrue("the job look was never offered in the long run", jobs > 0);
        }
        if (ticks == TICKS) {
            assertTrue("the neck's bound was never reached with a stare on a walking head", neckChecked > 0);
            assertTrue("no glance was ever interrupted", interrupted > 0);
        }
        return head.toString();
    }

    /**
     * The stare lands on whoever hit us, even with a glance out. The caller sums four things onto one head -
     * the aim, the walk's glance, the sway and the stare - and the stare is worth nothing unless it is
     * measured against the head it is added to, the glance included. Measured against the bare aim instead,
     * it settles at the attacker <i>plus</i> whatever the road was showing at the time, which is up to a
     * glance's seventy degrees and is the defect this test was written for: a bot punched mid-glance does
     * not look at whoever did it, it looks past them.
     * <p>
     * Driven without the run's noise, because it is a rule about one sum and not about a chase: the aim held
     * still, the glance out at forty degrees, the attacker at ninety - so the stare needs fifty of its
     * seventy degrees of neck - and blows every few ticks, since somebody being stared at is somebody who is
     * still hitting us, and each blow re-arms the stare rather than starting a new reaction.
     */
    @Test
    public void aStareLandsOnWhoeverHitUsWithAGlanceOut() {
        final WalkGlance walk = new WalkGlance(new Random(7));
        final Flinch flinch = new Flinch(new Random(7));
        final Rotation aim = new Rotation(0f, 0f);
        final Rotation interest = new Rotation(40f, 0f);
        final Rotation attacker = new Rotation(90f, 0f);
        int judged = 0;
        for (int t = 0; t < 900; t++) {
            // the walk keeps being plain, and the glance's first look is drawn a hundred welcome ticks away
            walk.tick();
            walk.welcome();
            walk.advance(true, () -> interest);
            if (t % 6 == 0) {
                flinch.blow(() -> attacker);
            }
            // the caller's own sum, and the caller's own base: the head this offset is added to
            final Rotation stareFrom = new Rotation(aim.getYaw() + walk.yaw(), aim.getPitch() + walk.pitch());
            flinch.advance(stareFrom, true, true);
            final float head = aim.getYaw() + walk.yaw() + flinch.yaw();
            // judged only where the neck is not the thing that stops it: with the glance swinging to the
            // other side now and then, the stare for an attacker at ninety would need more than the seventy
            // degrees of neck a walking body has, and a clamped stare is FlinchTest's business
            final float needed = Rotation.normalizeYaw(attacker.getYaw() - (aim.getYaw() + walk.yaw()));
            if (t >= 12 && flinch.answering() && Math.abs(walk.yaw()) >= 20f
                    && Math.abs(needed) <= Flinch.NECK_YAW - 5f) {
                // three degrees of slack: the glance carries a couple of degrees of jitter on its amplitude,
                // and the stare is done when it is within a degree of what it is chasing
                assertEquals("the stare settled beside whoever hit us, not on them, with a glance "
                        + walk.yaw() + " degrees out", attacker.getYaw(), head, 3f);
                judged++;
            }
        }
        assertTrue("the glance was never out by enough for the two to be told apart", judged >= 5);
    }

    /**
     * The aim loop the focused lean tests drive, written out next to {@link #tick}'s own rather than
     * shared with it, so the harness and the scenario can disagree: the spin-up on a genuine direction
     * change, the carry past a walking target, and one paced step per tick, as precisely
     * {@code LookBehavior#humanStep} composes them in {@link #tick}. Walking, nothing clicked - the only
     * state the offer is ever made in.
     */
    private static Rotation aimTick(Rotation aim, Rotation wanted, Rotation turnFrom, int aimAge,
                                    TurnPace pace) {
        final Rotation to = AimDynamics.overshoot(turnFrom, wanted, aimAge);
        return AimDynamics.humanStep(aim, to, aimAge, 0.5, AimDynamics.AIM_EASE, pace.pace());
    }

    @Test
    public void aCoherentLookSettlesOntoTheLeanedLineAndTurnsWithIt() {
        // One approach: the walk goes its way, the look is offered from the ramp up to its full share,
        // and the hand has to land on the leaned line - the lean is one more direction to arrive at, not
        // a state the hand cannot leave. Then the offer turns the corner without the walk moving (the
        // repath of a job that changed its block) and the hand has to come round to the new one.
        final TurnPace pace = new TurnPace(new Random(17));
        final GazeAhead gaze = new GazeAhead();
        final Rotation walk = new Rotation(0f, 5f);
        Rotation aim = null;
        Rotation turnFrom = null;
        Rotation last = null;
        int aimAge = 0;
        Rotation midTurn = null;
        for (int t = 0; t < 140; t++) {
            final Rotation line = t < 70 ? new Rotation(50f, 12f) : new Rotation(-50f, 12f);
            final double weight = Math.min(GazeAhead.LEAN, 0.08 * (t + 1));
            gaze.offer(line, weight);
            final Rotation wanted = gaze.lean(walk, false);
            final Rotation from = aim != null ? aim : new Rotation(-20f, 0f);
            if (aim == null || AimDynamics.separation(last, wanted) > ReactionDelay.NEW_DIRECTION_ANGLE) {
                aimAge = 0;
                turnFrom = from;
                pace.begin();
            }
            aim = aimTick(from, wanted, turnFrom, aimAge, pace);
            aimAge++;
            last = wanted;
            // the carry is the only thing allowed past the leaned line: the walk, half of a fifty-degree
            // offer, and the flourish - never more than that from the walk's own direction
            assertTrue("the hand left the lean by "
                    + Math.abs(Rotation.normalizeYaw(aim.getYaw() - walk.getYaw())) + " degrees",
                    Math.abs(Rotation.normalizeYaw(aim.getYaw() - walk.getYaw()))
                            <= 25f + AimDynamics.OVERSHOOT_MAX + AimDynamics.MAX_YAW_STEP + 1f);
            if (t == 65) {
                midTurn = new Rotation(aim.getYaw(), aim.getPitch());
            }
        }
        // seven ticks of ramp at 0.08, the full offer from tick 8: 25 degrees over, and the pitch the walk's own -
        // the lean is of the yaw alone (it was 8.5, half of the way to the offer's twelve, and on a real flat walk
        // that pull put the head thirty degrees down for the whole walk: see GazeAhead)
        assertEquals("the hand did not settle onto the leaned line", 25f, midTurn.getYaw(), 2f);
        assertEquals("the pitch left the walk's own: the lean is of the yaw alone", 5f, midTurn.getPitch(), 2f);
        assertEquals("the offer turned and the hand did not follow", -25f, aim.getYaw(), 2f);
    }

    @Test
    public void theLookStandsAsideForAClickMidLean() {
        // Mid-lean, with the eyes twenty-five degrees off the walk, the walk's target becomes a click: the
        // same lean that moved the aim all that way must contribute nothing, on that tick - the product
        // hands the rotation back untouched, and the harness asserts it as an identity, not a tolerance.
        final GazeAhead gaze = new GazeAhead();
        final Rotation walk = new Rotation(0f, 5f);
        gaze.offer(new Rotation(50f, 12f), GazeAhead.LEAN);
        final Rotation leaned = gaze.lean(walk, false);
        assertTrue("the lean did nothing here, so the stand-aside is not being tested",
                leaned.getYaw() == 25f);
        gaze.offer(new Rotation(50f, 12f), GazeAhead.LEAN);
        final Rotation clicked = gaze.lean(walk, true);
        assertTrue("the lean rode into a click", clicked == walk);
        // and the aim behind it re-turns to the walk's own direction rather than holding the lean
        final TurnPace pace = new TurnPace(new Random(19));
        Rotation aim = leaned;
        Rotation last = leaned;
        Rotation turnFrom = leaned;
        int aimAge = 0;
        pace.begin();
        for (int t = 0; t < 60; t++) {
            gaze.offer(null, 0); // the click is being acted on every tick now: no look is offered
            final Rotation wanted = gaze.lean(walk, true);
            assertTrue("an offer reached a click", wanted == walk);
            if (AimDynamics.separation(last, wanted) > ReactionDelay.NEW_DIRECTION_ANGLE) {
                turnFrom = aim;
                aimAge = 0;
                pace.begin();
            }
            aim = AimDynamics.humanStep(aim, wanted, aimAge, 0.5, AimDynamics.CLICK_EASE, pace.pace());
            aimAge++;
            last = wanted;
        }
        assertTrue("the aim held the lean through sixty ticks of clicking",
                AimDynamics.separation(aim, walk) <= AimDynamics.ARRIVED_ANGLE);
    }

    @Test
    public void aFlappingOfferKeepsTheHeadBesideTheWalk() {
        // The worst walk the ramp can draw: an offer at full weight that exists on every second tick only -
        // a walk straddling the ramp's edge, a job weight flickering through zero. The wanted swings
        // between the walk's own direction and the lean's cap, sixty degrees a tick, past the new-turn
        // angle every time: if a starvation exists this is where it lives. What has to hold instead: the
        // head never leaves the lean's side of the walk, and it never stops moving while the flap goes on.
        final TurnPace pace = new TurnPace(new Random(23));
        final GazeAhead gaze = new GazeAhead();
        final Rotation walk = new Rotation(0f, 0f);
        final Rotation line = new Rotation(170f, 0f); // 85 degrees at full weight - the cap's job to hold at 60
        gaze.offer(line, GazeAhead.LEAN);
        assertTrue("the cap is not being exercised", Math.abs(gaze.lean(walk, false).getYaw()) == 60f);
        Rotation aim = new Rotation(0f, 0f);
        Rotation last = walk;
        Rotation turnFrom = aim;
        int aimAge = 0;
        int moved = 0;
        for (int t = 0; t < 600; t++) {
            final boolean on = (t & 1) == 0;
            gaze.offer(on ? line : null, on ? GazeAhead.LEAN : 0);
            final Rotation wanted = gaze.lean(walk, false);
            final Rotation from = aim;
            if (AimDynamics.separation(last, wanted) > ReactionDelay.NEW_DIRECTION_ANGLE) {
                turnFrom = from;
                aimAge = 0;
                pace.begin();
            }
            aim = aimTick(aim, wanted, turnFrom, aimAge, pace);
            aimAge++;
            last = wanted;
            assertTrue("the flap carried the head " + Math.abs(aim.getYaw()) + " degrees off the walk",
                    Math.abs(aim.getYaw()) <= GazeAhead.MAX_LEAN_DEGREES + AimDynamics.OVERSHOOT_MAX + 1f);
            if (AimDynamics.separation(from, aim) > 0f) {
                moved++;
            }
        }
        assertTrue("the hand froze under a flapping offer: moved on only " + moved + " ticks of 600",
                moved > 400);
    }

    @Test
    public void aHeldTargetIsAlwaysArrivedAt() {
        // Whatever the aim has to travel and wherever it starts, and at every sensitivity, it has to land -
        // exactly, not approximately, because placing and breaking gate on the aim being on target. At every
        // tempo of the hand's too: the pace a turn draws for itself moves how quickly the turn is made and
        // never whether it gets there, and the arrival gates every click in the mod waits on cannot depend
        // on the mood of the hand that is doing the clicking.
        for (float pace : new float[]{TurnPace.MIN, 1.0f, TurnPace.MAX}) {
        for (double sensitivity : SENSITIVITIES) {
            for (float fromYaw : new float[]{0f, 179f, -179f, 90f, -90f}) {
                for (float toYaw : new float[]{0f, 179f, -179f, 90f, -90f}) {
                    final Rotation turnFrom = new Rotation(fromYaw, 0f);
                    Rotation aim = turnFrom;
                    final Rotation to = new Rotation(toYaw, 0f);
                    int ticks = 0;
                    while (ticks < 60 && (Rotation.normalizeYaw(toYaw - aim.getYaw()) != 0f)) {
                        // Walking with nothing being clicked, which is the worst case for this: the hand is
                        // aiming a little past the target for the first sixteen ticks of the turn rather
                        // than at it. Arrival is exact all the same, because the carry decays to nothing
                        // long before the hand gets there - and if it ever did not, this is the test that
                        // would say so, since every click in the mod waits on it.
                        aim = AimDynamics.humanStep(aim, AimDynamics.overshoot(turnFrom, to, ticks), ticks,
                                sensitivity, AimDynamics.AIM_EASE, pace);
                        ticks++;
                    }
                    assertEquals("never arrived from " + fromYaw + " to " + toYaw + " at sensitivity " + sensitivity
                                    + " and tempo " + pace,
                            toYaw, aim.getYaw(), 0f);
                    assertTrue("arrival took " + ticks + " ticks at tempo " + pace, ticks <= 60);
                }
            }
        }
        }
    }

    /**
     * The carry, from both sides.
     * <p>
     * Walking, a turn that is only a direction of travel is allowed to go past where it is going and come
     * back - that is what a hand does, and the arrival gates were measured against it. The moment the same
     * turn ends in a click it is not, because the rotation the aim holds is the one Baritone traces its own
     * pickaxe and blocks along: an aim that overshoots a block by eight percent of the turn is an aim that
     * mines the block next to it on a long turn, and hits the wrong face on a short one.
     */
    @Test
    public void aWalkingTurnIsCarriedAndAClickingOneIsNot() {
        for (double sensitivity : SENSITIVITIES) {
            for (float turn : new float[]{0f, 10f, 14f, 15f, 20f, 45f, 90f, 179f, -10f, -30f, -90f, -179f}) {
                final Rotation to = new Rotation(turn, 0f);
                final float direction = Math.signum(turn);

                // carried: walking, nothing clicked
                final Rotation turnFrom = new Rotation(0f, 0f);
                Rotation aim = turnFrom;
                float past = 0f;
                int ticks = 0;
                while (ticks < 60 && (Rotation.normalizeYaw(turn - aim.getYaw()) != 0f)) {
                    aim = AimDynamics.humanStep(aim, AimDynamics.overshoot(turnFrom, to, ticks), ticks, sensitivity);
                    past = Math.max(past, (aim.getYaw() - turn) * direction);
                    ticks++;
                }
                assertEquals("a carried turn never landed exactly on it: " + turn + " deg at sensitivity " + sensitivity,
                        turn, aim.getYaw(), 0f);
                assertTrue("a carried turn took " + ticks + " ticks", ticks <= 60);
                final float allowed = Math.min(Math.abs(turn) * AimDynamics.OVERSHOOT_FRACTION, AimDynamics.OVERSHOOT_MAX);
                assertTrue("a " + turn + " deg turn was carried " + past + " deg past the target, over " + allowed,
                        past <= allowed + 0.01f);
                if (Math.abs(turn) < AimDynamics.OVERSHOOT_MIN_TURN) {
                    assertEquals("a short turn was carried: " + turn + " deg", 0f, past, 0f);
                }

                // not carried: the same turn, ended in a click. The hand may still tap up to one mouse
                // step over the target on the tick it zooms in - a fraction of a degree, taken back on the
                // next tick - but never the degrees past that the carry is made of, which is at least a
                // whole step at the coarsest sensitivity and two at the finest.
                aim = turnFrom;
                ticks = 0;
                float tapped = 0f;
                while (ticks < 60 && (Rotation.normalizeYaw(turn - aim.getYaw()) != 0f)) {
                    aim = AimDynamics.humanStep(aim, to, ticks, sensitivity, AimDynamics.CLICK_EASE);
                    tapped = Math.max(tapped, (aim.getYaw() - turn) * direction);
                    ticks++;
                }
                assertTrue("an aim that was about to be clicked went " + tapped + " deg past the target: "
                                + turn + " deg at sensitivity " + sensitivity,
                        tapped <= Math.abs(AimDynamics.mouseToAngle(sensitivity, 1)) + 0.005f);
                assertEquals("a clicked turn never landed exactly on it", turn, aim.getYaw(), 0f);
            }
        }
    }

    @Test
    public void aTargetThatNeverSettlesStillGetsMovedToward() {
        // The pathological case: the target flips to the opposite side every tick, which re-arms the spin-up
        // and the reaction every tick. The hand must still make progress rather than sitting at the start.
        final Rotation a = new Rotation(170f, 0f);
        final Rotation b = new Rotation(-170f, 0f);
        Rotation aim = new Rotation(0f, 0f);
        // seeded, like the hostile run above, so that a failure is the same failure twice
        final ReactionDelay reaction = new ReactionDelay(new Random(4));
        int held = 0;
        float moved = 0f;
        for (int t = 0; t < 200; t++) {
            final Rotation wanted = t % 2 == 0 ? a : b;
            final int age = 0; // every tick looks like a new direction
            if (reaction.holding(wanted)) {
                held++;
                assertTrue("the hand was held still " + held + " ticks in a row", held <= ReactionDelay.HOLD_MAX);
                continue;
            }
            held = 0;
            final Rotation next = AimDynamics.humanStep(aim, wanted, age, 0.5);
            moved += Math.abs(Rotation.normalizeYaw(next.getYaw() - aim.getYaw()));
            aim = next;
        }
        assertTrue("the aim never moved toward a target that would not settle", moved > 200f);
    }

    @Test
    public void aGlanceStandsDownWhileSomethingIsBeingClicked() {
        // Baritone aims the pickaxe and the block out along the rotation it last sent the server, so a glance
        // taken while something is being clicked is a glance Baritone itself is mining along - twenty-eight
        // degrees of it, most of a block. This is the rule at the level it is decided, and the two shapes it
        // has to hold in: a glance that has not started must not start, and one already running must be
        // dropped where it stands rather than carried into the click.
        final Rotation camera = new Rotation(40.0f, -10.0f);
        final IdleGlance.Stepper step = (from, to, legAge) -> AimDynamics.step(from, to,
                AimDynamics.IDLE_YAW_STEP, AimDynamics.IDLE_PITCH_STEP, 0.5);

        final IdleGlance idle = new IdleGlance(new Random(5));
        int waited = 0;
        while (idle.next(camera, true, step) == null && waited < 400) {
            waited++;
        }
        assertTrue("the idle glance never fired in " + waited + " ticks of standing still", waited < 400);

        // the click starts mid-glance: it is dropped, not driven on
        assertNull("the glance was carried into a click", idle.next(camera, false, step));

        // and however long the click lasts - long enough to cover the longest cooldown twice over - the
        // glance does not start up again behind it
        for (int t = 0; t < 5000; t++) {
            assertNull("the glance started while something was being clicked", idle.next(camera, false, step));
        }
    }

    @Test
    public void aStandingHeadKeepsMovingAndTheSwaySurvivesTheHandover() {
        // What the standing sway is for, and what it must not cost. For: a bot with nothing to do used to
        // hold its angle to the last bit between glances, which under a held pause is minutes of a head
        // that is not moving at all. Cost: the sway is one state machine shared with the walk, so the tick
        // the feet start or stop the head may only change by the difference between the two amplitudes -
        // never from whatever one phase had got to, to whatever another one was at.
        final HeadWander wander = new HeadWander(new Random(2));
        final Random rand = new Random(11);
        boolean walking = true;
        float previous = 0f;
        int idleTicks = 0, stillness = 0, longestStillness = 0;
        float worstStep = 0f;
        for (int t = 0; t < 40000; t++) {
            if (rand.nextInt(60) == 0) {
                walking = !walking;
            }
            wander.advance();
            assertTrue("the sway escaped its limit", Math.abs(wander.yaw()) <= HeadWander.MAX_YAW + 1e-4f);
            assertTrue("the standing sway was louder than the walking one",
                    Math.abs(wander.idleYaw()) <= Math.abs(wander.yaw()) + 1e-4f);
            final float offset = walking ? wander.yaw() : wander.idleYaw();
            if (t > 0) {
                worstStep = Math.max(worstStep, Math.abs(offset - previous));
            }
            if (!walking) {
                idleTicks++;
                stillness = Math.abs(offset) < 1e-3f ? stillness + 1 : 0;
                longestStillness = Math.max(longestStillness, stillness);
            }
            previous = offset;
        }
        assertTrue("no ticks were spent standing", idleTicks > 1000);
        // a swing crosses the bearing a couple of times a second at most, and where it does it is briefly
        // within a thousandth of a degree of zero; anything longer than a second or two of that is a head
        // that has stopped moving, which is the thing this exists to prevent
        assertTrue("the head stood perfectly still for " + longestStillness + " ticks of " + idleTicks,
                longestStillness < 40);
        // one tick of the slowest swing the walk allows, plus the half degree the two amplitudes differ by
        assertTrue("the sway stepped " + worstStep + " deg when the feet changed", worstStep < 0.7f);
    }
}
