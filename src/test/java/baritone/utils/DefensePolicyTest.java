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

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The ways a bot with a sword can be wrong, in the order they get a person killed: swinging at the wrong
 * thing, swinging at something that is not a threat, swinging when the answer is to leave, and swinging
 * at a creeper.
 */
public class DefensePolicyTest {

    private static final DefensePolicy.Self HEALTHY = new DefensePolicy.Self(20, true);
    private static final double REACH = 3.0;

    private static DefensePolicy.Sighting zombie(int id, double distance, boolean comingForUs) {
        return new DefensePolicy.Sighting(id, distance, true, false, false, true, comingForUs);
    }

    private static DefensePolicy.Sighting mob(int id, double distance, boolean hostile, boolean neutral,
                                              boolean detonates, boolean visible, boolean comingForUs) {
        return new DefensePolicy.Sighting(id, distance, hostile, neutral, detonates, visible, comingForUs);
    }

    @Test
    public void aZombieComingForUsIsAnswered() {
        DefensePolicy.Decision d = DefensePolicy.decide(
                List.of(zombie(7, 2.5, true)), HEALTHY, REACH);
        assertTrue(d.engage());
        assertEquals(7, d.entityId());
        assertEquals(DefensePolicy.Reason.ENGAGE, d.reason());
    }

    @Test
    public void nothingInSightIsNothingAtAll() {
        assertEquals(DefensePolicy.Reason.NOTHING_IN_SIGHT,
                DefensePolicy.decide(Collections.emptyList(), HEALTHY, REACH).reason());
        assertEquals(DefensePolicy.Reason.NOTHING_IN_SIGHT,
                DefensePolicy.decide(null, HEALTHY, REACH).reason());
    }

    @Test
    public void aPassiveIsNeverHitHoweverCloseItStands() {
        // A cow in the way, a chicken under the feet: not a threat, and not dinner either.
        DefensePolicy.Decision d = DefensePolicy.decide(
                List.of(mob(1, 0.5, false, true, false, true, false)), HEALTHY, REACH);
        assertFalse(d.engage());
        assertEquals(DefensePolicy.Reason.NOT_HOSTILE, d.reason());
    }

    @Test
    public void anAngeredNeutralIsDeclinedEvenWhenItIsComingForUs() {
        // The zombified piglin a stray arrow hit, the wolf whose owner was struck. A bot cannot know
        // whose fault it was, and the plan names this case as one to decline rather than guess at.
        DefensePolicy.Decision d = DefensePolicy.decide(
                List.of(mob(2, 1.5, false, true, false, true, true)), HEALTHY, REACH);
        assertFalse(d.engage());
        assertEquals(DefensePolicy.Reason.NOT_HOSTILE, d.reason());
    }

    @Test
    public void anUnclassifiableMobIsDeclinedAndSaysSo() {
        // Neither hostile nor known-neutral: a form the caller did not fill in. It declines, and the
        // reason is different from the passive case because this one is a gap in the caller, not a
        // fact about the mob.
        DefensePolicy.Decision d = DefensePolicy.decide(
                List.of(mob(3, 1.0, false, false, false, true, true)), HEALTHY, REACH);
        assertFalse(d.engage());
        assertEquals(DefensePolicy.Reason.UNKNOWN_MOB, d.reason());
    }

    @Test
    public void aCreeperIsNeverMeleedHoweverTempting() {
        DefensePolicy.Decision d = DefensePolicy.decide(
                List.of(mob(4, 1.0, true, false, true, true, true)), HEALTHY, REACH);
        assertFalse(d.engage());
        assertEquals(DefensePolicy.Reason.DETONATES, d.reason());
    }

    @Test
    public void aHostileThatHasNotNoticedUsIsNotStarted() {
        // Self-defence, not hunting: the plan's card says defence, and a bot that opens fights is a
        // different product from the one being built.
        DefensePolicy.Decision d = DefensePolicy.decide(
                List.of(zombie(5, 1.0, false)), HEALTHY, REACH);
        assertFalse(d.engage());
        assertEquals(DefensePolicy.Reason.NOT_COMING_FOR_US, d.reason());
    }

    @Test
    public void outOfReachIsNotHit() {
        assertFalse(DefensePolicy.decide(List.of(zombie(6, 3.5, true)), HEALTHY, REACH).engage());
        assertEquals(DefensePolicy.Reason.TOO_FAR,
                DefensePolicy.decide(List.of(zombie(6, 3.5, true)), HEALTHY, REACH).reason());
    }

    @Test
    public void theReachIsTheCallersAndNotALiteralInHere() {
        // The same sighting, two reaches. A number written into this file would be wrong on some
        // version; a parameter cannot be.
        DefensePolicy.Sighting far = zombie(8, 4.0, true);
        assertTrue(DefensePolicy.decide(List.of(far), HEALTHY, 4.5).engage());
        assertFalse(DefensePolicy.decide(List.of(far), HEALTHY, 3.0).engage());
    }

    @Test
    public void oneArrivingFromBehindAWallIsNotSwungAt() {
        DefensePolicy.Decision d = DefensePolicy.decide(
                List.of(mob(9, 1.0, true, false, false, false, true)), HEALTHY, REACH);
        assertFalse(d.engage());
        assertEquals(DefensePolicy.Reason.NOT_VISIBLE, d.reason());
    }

    @Test
    public void aCrowdIsLeftRatherThanFought() {
        List<DefensePolicy.Sighting> crowd = new ArrayList<>();
        for (int i = 0; i < DefensePolicy.CROWD_LIMIT + 1; i++) {
            crowd.add(zombie(10 + i, 1.0 + i * 0.1, true));
        }
        DefensePolicy.Decision d = DefensePolicy.decide(crowd, HEALTHY, REACH);
        assertFalse(d.engage());
        assertEquals(DefensePolicy.Reason.CROWDED, d.reason());
    }

    @Test
    public void exactlyTheCrowdLimitIsStillTaken() {
        List<DefensePolicy.Sighting> crowd = new ArrayList<>();
        for (int i = 0; i < DefensePolicy.CROWD_LIMIT; i++) {
            crowd.add(zombie(20 + i, 1.0 + i * 0.1, true));
        }
        assertTrue(DefensePolicy.decide(crowd, HEALTHY, REACH).engage());
    }

    @Test
    public void hurtEnoughAndTheAnswerIsNotToFight() {
        DefensePolicy.Self hurt = new DefensePolicy.Self(DefensePolicy.MIN_HEALTH, true);
        assertEquals(DefensePolicy.Reason.TOO_HURT,
                DefensePolicy.decide(List.of(zombie(30, 1.0, true)), hurt, REACH).reason());
        assertTrue(DefensePolicy.decide(List.of(zombie(30, 1.0, true)),
                new DefensePolicy.Self(DefensePolicy.MIN_HEALTH + 1, true), REACH).engage());
    }

    @Test
    public void emptyHandedIsDeclined() {
        DefensePolicy.Self bare = new DefensePolicy.Self(20, false);
        assertEquals(DefensePolicy.Reason.UNARMED,
                DefensePolicy.decide(List.of(zombie(31, 1.0, true)), bare, REACH).reason());
    }

    @Test
    public void theNearestIsTheOneAnswered() {
        DefensePolicy.Decision d = DefensePolicy.decide(Arrays.asList(
                zombie(40, 2.8, true),
                zombie(41, 1.2, true),
                zombie(42, 2.0, true)), HEALTHY, REACH);
        assertTrue(d.engage());
        assertEquals("the closest threat is the one that gets there first", 41, d.entityId());
    }

    @Test
    public void aRefusedMobDoesNotSuppressAnAllowedOne() {
        // The creeper beside the zombie must not be why the zombie is left alone: each sighting is
        // judged on its own, and only the crowd count is shared.
        DefensePolicy.Decision d = DefensePolicy.decide(Arrays.asList(
                mob(50, 1.0, true, false, true, true, true),
                zombie(51, 2.0, true)), HEALTHY, REACH);
        assertTrue(d.engage());
        assertEquals(51, d.entityId());
    }
}
