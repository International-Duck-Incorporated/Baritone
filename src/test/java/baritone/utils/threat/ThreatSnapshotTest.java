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

package baritone.utils.threat;

import baritone.utils.DefensePolicy;
import baritone.utils.threat.Observation.Knowledge;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ThreatSnapshotTest {

    private static final String OVERWORLD = "minecraft:overworld";
    private static final UUID A = new UUID(1, 1);
    private static final UUID B = new UUID(2, 2);

    private static Observation obs(int id, UUID uuid, String type, double distance, boolean visible,
                                   Knowledge targeting, int sampledTick) {
        return new Observation(OVERWORLD, id, uuid, type, distance, visible, targeting, true, true, sampledTick);
    }

    private static Observation zombie(int id, double distance, Knowledge targeting) {
        return obs(id, A, "minecraft:zombie", distance, true, targeting, 100);
    }

    private static ThreatSnapshot snap(List<Observation> observations) {
        return ThreatSnapshot.of(100, OVERWORLD, 20, true, 4, observations);
    }

    // ---- classification: explicit table, unknowns decline ----

    @Test
    public void hostileNamedTypesAreHostileByNature() {
        assertEquals(ThreatKind.HOSTILE_BY_NATURE, ThreatKind.of("minecraft:zombie"));
        assertEquals(ThreatKind.HOSTILE_BY_NATURE, ThreatKind.of("minecraft:skeleton"));
        assertEquals(ThreatKind.HOSTILE_BY_NATURE, ThreatKind.of("minecraft:spider"));
    }

    @Test
    public void creeperDetonates() {
        assertEquals(ThreatKind.DETONATING, ThreatKind.of("minecraft:creeper"));
    }

    @Test
    public void angerableNeutralsAreNeutralEvenWhenAngry() {
        // A zombified piglin or an enderman can be angry and still never be a thing to swing at.
        assertEquals(ThreatKind.NEUTRAL, ThreatKind.of("minecraft:zombified_piglin"));
        assertEquals(ThreatKind.NEUTRAL, ThreatKind.of("minecraft:enderman"));
        assertEquals(ThreatKind.NEUTRAL, ThreatKind.of("minecraft:wolf"));
    }

    @Test
    public void passiveMobsArePassive() {
        assertEquals(ThreatKind.PASSIVE, ThreatKind.of("minecraft:cow"));
        assertEquals(ThreatKind.PASSIVE, ThreatKind.of("minecraft:sheep"));
    }

    @Test
    public void unknownModdedOrUnreadableTypesAreUnknownEvenIfTheNameLooksHostile() {
        // A mod's "zombie" is not the vanilla zombie: the namespace is part of the identity.
        assertEquals(ThreatKind.UNKNOWN, ThreatKind.of("somemod:zombie"));
        assertEquals(ThreatKind.UNKNOWN, ThreatKind.of("minecraft:some_future_mob"));
        assertEquals(ThreatKind.UNKNOWN, ThreatKind.of(null));
        assertEquals(ThreatKind.UNKNOWN, ThreatKind.of(""));
    }

    // ---- lifetime and identity: what is still true now ----

    @Test
    public void deadUnloadedOtherWorldAndStaleSamplesAreDropped() {
        List<Observation> raw = new ArrayList<>();
        raw.add(new Observation(OVERWORLD, 1, A, "minecraft:zombie", 3, true, Knowledge.NO, false, true, 100));
        raw.add(new Observation(OVERWORLD, 2, B, "minecraft:zombie", 3, true, Knowledge.NO, true, false, 100));
        raw.add(new Observation("minecraft:the_nether", 3, new UUID(3, 3), "minecraft:zombie", 3, true, Knowledge.NO, true, true, 100));
        raw.add(obs(4, new UUID(4, 4), "minecraft:zombie", 3, true, Knowledge.NO, 90)); // age 10 > 4
        raw.add(obs(5, new UUID(5, 5), "minecraft:zombie", 3, true, Knowledge.NO, 101)); // from the future
        raw.add(obs(6, new UUID(6, 6), "minecraft:zombie", 3, true, Knowledge.NO, 99)); // age 1, kept
        ThreatSnapshot s = snap(raw);
        assertEquals(1, s.entries().size());
        assertEquals(6, s.entries().get(0).entityId());
    }

    @Test
    public void newestSampleOfTheSameEntityIsKept() {
        ThreatSnapshot s = snap(List.of(
                obs(7, A, "minecraft:zombie", 6.0, true, Knowledge.NO, 97),
                obs(7, A, "minecraft:zombie", 2.5, true, Knowledge.NO, 99)));
        assertEquals(1, s.entries().size());
        assertEquals(2.5, s.entries().get(0).observation().distance(), 0.0);
    }

    @Test
    public void sameIdWithAnotherUuidIsAnotherEntity() {
        // The game reuses network ids after a despawn: id alone is not identity.
        ThreatSnapshot s = snap(List.of(
                obs(7, A, "minecraft:zombie", 2.0, true, Knowledge.NO, 100),
                obs(7, B, "minecraft:skeleton", 4.0, true, Knowledge.NO, 100)));
        assertEquals(2, s.entries().size());
    }

    // ---- determinism ----

    @Test
    public void sameObservationsInAnyOrderGiveTheSameSnapshot() {
        List<Observation> raw = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            raw.add(obs(i, new UUID(0, i), i % 2 == 0 ? "minecraft:zombie" : "minecraft:cow",
                    1 + (i % 4), true, Knowledge.UNKNOWN, 100));
        }
        List<Observation> shuffled = new ArrayList<>(raw);
        Collections.reverse(shuffled);
        assertEquals(snap(raw).entries(), snap(shuffled).entries());
    }

    @Test
    public void entriesAreNearestFirstWithTiesBrokenByIdThenUuid() {
        ThreatSnapshot s = snap(List.of(
                obs(9, A, "minecraft:zombie", 3, true, Knowledge.NO, 100),
                obs(2, B, "minecraft:zombie", 3, true, Knowledge.NO, 100),
                obs(5, A, "minecraft:zombie", 1, true, Knowledge.NO, 100)));
        List<Integer> ids = new ArrayList<>();
        s.entries().forEach(e -> ids.add(e.entityId()));
        assertEquals(List.of(5, 2, 9), ids);
    }

    // ---- the decision layer's view: conservative, and the unknown is not flattened in the snapshot ----

    @Test
    public void unknownTargetingIsKeptInTheSnapshotButDeclinedByThePolicyView() {
        ThreatSnapshot s = snap(List.of(zombie(1, 2, Knowledge.UNKNOWN)));
        assertEquals(Knowledge.UNKNOWN, s.entries().get(0).observation().targetingUs());
        DefensePolicy.Sighting sighting = s.asSightings(100).get(0);
        assertFalse(sighting.comingForUs());
    }

    @Test
    public void onlyAKnownTargetOfUsIsComingForUs() {
        assertTrue(snap(List.of(zombie(1, 2, Knowledge.YES))).asSightings(100).get(0).comingForUs());
        assertFalse(snap(List.of(zombie(1, 2, Knowledge.NO))).asSightings(100).get(0).comingForUs());
    }

    @Test
    public void sightingMappingFollowsTheKindTable() {
        ThreatSnapshot s = snap(List.of(
                obs(1, A, "minecraft:zombie", 2, true, Knowledge.YES, 100),
                obs(2, B, "minecraft:creeper", 2, true, Knowledge.YES, 100),
                obs(3, new UUID(3, 3), "minecraft:enderman", 2, true, Knowledge.YES, 100),
                obs(4, new UUID(4, 4), "minecraft:cow", 2, true, Knowledge.NO, 100),
                obs(5, new UUID(5, 5), "somemod:thing", 2, true, Knowledge.NO, 100)));
        List<DefensePolicy.Sighting> out = s.asSightings(100);
        // sorted by id here because distances tie
        assertSighting(out.get(0), true, false, false);   // zombie
        assertSighting(out.get(1), true, false, true);    // creeper
        assertSighting(out.get(2), false, true, false);   // enderman
        assertSighting(out.get(3), false, true, false);   // cow
        assertSighting(out.get(4), false, false, false);  // unknown
    }

    private static void assertSighting(DefensePolicy.Sighting s, boolean hostile, boolean neutralOrPassive,
                                       boolean detonates) {
        assertEquals(hostile, s.hostileByNature());
        assertEquals(neutralOrPassive, s.neutralOrPassive());
        assertEquals(detonates, s.detonatesOnContact());
    }

    @Test
    public void thePolicyEngagesAHostileThatIsComingForUsAndNothingElse() {
        DefensePolicy.Self self = new DefensePolicy.Self(20, true);
        // a zombie coming for us: the one case the policy may engage
        ThreatSnapshot zombieComing = snap(List.of(zombie(1, 2, Knowledge.YES)));
        assertTrue(DefensePolicy.decide(zombieComing.asSightings(100), self, 3.0).engage());
        // an angry enderman, a creeper and an unknown mob all decline
        ThreatSnapshot others = snap(List.of(
                obs(2, A, "minecraft:enderman", 2, true, Knowledge.YES, 100),
                obs(3, B, "minecraft:creeper", 2, true, Knowledge.YES, 100),
                obs(4, new UUID(4, 4), "somemod:thing", 2, true, Knowledge.YES, 100)));
        assertFalse(DefensePolicy.decide(others.asSightings(100), self, 3.0).engage());
    }

    // ---- scan budget ----

    @Test
    public void nothingIsScannedWithoutAnOwnedTask() {
        ScanSchedule schedule = new ScanSchedule();
        for (int tick = 0; tick < 50; tick++) {
            assertFalse(schedule.shouldScan(tick, false));
        }
    }

    @Test
    public void ownedTaskScansAtMostEveryFourTicks() {
        ScanSchedule schedule = new ScanSchedule();
        int scans = 0;
        for (int tick = 0; tick < 40; tick++) {
            if (schedule.shouldScan(tick, true)) {
                scans++;
            }
        }
        assertEquals(10, scans); // ticks 0, 4, 8, ... 36
    }

    @Test
    public void losWindowCoversEveryCandidateWithinCeilingOfNOverFourTicks() {
        for (int n = 1; n <= 40; n++) {
            Set<Integer> seen = new HashSet<>();
            int ticks = (n + ScanSchedule.LOS_PER_TICK - 1) / ScanSchedule.LOS_PER_TICK;
            for (int tick = 0; tick < ticks; tick++) {
                List<Integer> window = ScanSchedule.losWindow(n, tick);
                assertEquals(Math.min(ScanSchedule.LOS_PER_TICK, n), window.size());
                assertEquals(window.size(), new HashSet<>(window).size()); // no duplicates in one tick
                seen.addAll(window);
            }
            assertEquals("n=" + n, n, seen.size());
        }
    }

    @Test
    public void losWindowIsDeterministicAndEmptyForNoCandidates() {
        assertEquals(ScanSchedule.losWindow(9, 7), ScanSchedule.losWindow(9, 7));
        assertTrue(ScanSchedule.losWindow(0, 3).isEmpty());
    }
}
