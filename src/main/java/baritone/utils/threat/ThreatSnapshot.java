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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * An immutable, deterministic picture of what the bot can see right now, built from raw observations.
 * <p>
 * This replaces the separate entity scans the look, avoidance and follow code each make. It does not
 * decide anything. It keeps the classification, the confidence, the provenance and the age of every
 * entity, and {@link #asSightings(int)} hands the decision layer only what {@link DefensePolicy} needs.
 * <p>
 * What it removes, with the numbers: {@code utils/pathing/Avoidance#create} walks the whole
 * {@code entitiesStream()} on every path calculation, keeps every mob that survives three filters, and
 * has no rate limit and no ownership check. A snapshot is built at most every {@link ScanSchedule}
 * interval while a task owns the body, and every consumer reads the same one.
 * <p>
 * Determinism: the same observations, in any order, give the same snapshot, with entries sorted by
 * distance then id then uuid. Nothing here reads a clock, a random source or the game.
 */
public final class ThreatSnapshot {

    /**
     * One entity in the snapshot: its observation, what its type is, and the tick it was sampled on.
     *
     * @param observation the sample, unchanged
     * @param kind        the classification from its type id
     */
    public record Entry(Observation observation, ThreatKind kind) {
        public int entityId() {
            return observation.entityId();
        }

        public UUID uuid() {
            return observation.uuid();
        }

        public int ageTicks(int nowTick) {
            return nowTick - observation.sampledTick();
        }
    }

    private final int tick;
    private final String world;
    private final double health;
    private final boolean armed;
    private final List<Entry> entries;

    private ThreatSnapshot(int tick, String world, double health, boolean armed, List<Entry> entries) {
        this.tick = tick;
        this.world = world;
        this.health = health;
        this.armed = armed;
        this.entries = List.copyOf(entries);
    }

    /**
     * Builds the snapshot for {@code nowTick}, keeping only what is still true about the world now.
     * <p>
     * Dropped, and why: a sample from another world (a different entity, whatever its id); a sample
     * that says the entity was dead or unloaded; a sample older than {@code maxAgeTicks}, or from the
     * future (a clock that went backwards is not evidence). Of several samples of the same entity, the
     * newest is kept. The same id with a different uuid is a different entity, and both are kept.
     *
     * @param nowTick     the current client tick
     * @param world       the world the player is in now
     * @param health      the player's health, as the caller reads it
     * @param armed       whether the hand holds something worth hitting with
     * @param maxAgeTicks the oldest sample that may still be used
     * @param observations raw samples in any order
     * @return the snapshot, never {@code null}
     */
    public static ThreatSnapshot of(int nowTick, String world, double health, boolean armed,
                                    int maxAgeTicks, List<Observation> observations) {
        Map<String, Observation> newest = new HashMap<>();
        for (Observation o : observations) {
            if (o == null || !world.equals(o.world()) || !o.alive() || !o.loaded()) {
                continue;
            }
            int age = nowTick - o.sampledTick();
            if (age < 0 || age > maxAgeTicks) {
                continue;
            }
            String key = o.entityId() + "/" + o.uuid();
            Observation kept = newest.get(key);
            if (kept == null || o.sampledTick() > kept.sampledTick()) {
                newest.put(key, o);
            }
        }
        List<Entry> entries = new ArrayList<>(newest.size());
        for (Observation o : newest.values()) {
            entries.add(new Entry(o, ThreatKind.of(o.typeId())));
        }
        entries.sort(Comparator.comparingDouble((Entry e) -> e.observation().distance())
                .thenComparingInt(Entry::entityId)
                .thenComparing(e -> e.uuid().toString()));
        return new ThreatSnapshot(nowTick, world, health, armed, entries);
    }

    public int tick() {
        return tick;
    }

    public String world() {
        return world;
    }

    public double health() {
        return health;
    }

    public boolean armed() {
        return armed;
    }

    /** Every surviving entity, nearest first. Unmodifiable. */
    public List<Entry> entries() {
        return entries;
    }

    /**
     * The decision layer's view of this snapshot: one {@link DefensePolicy.Sighting} per entry.
     * <p>
     * The mapping is the conservative one, and the field that could be flattened too early is handled
     * explicitly:
     * <ul>
     *   <li>{@code HOSTILE_BY_NATURE} is hostile; {@code DETONATING} is hostile and detonates.</li>
     *   <li>{@code NEUTRAL} and {@code PASSIVE} are neutral-or-passive, so the policy declines them
     *   with {@code NOT_HOSTILE}.</li>
     *   <li>{@code UNKNOWN} is neither, so the policy declines it with {@code UNKNOWN_MOB}, which is the
     *   gap worth closing rather than a silent pass.</li>
     *   <li>{@code comingForUs} is true only when the client <em>knows</em> the entity is targeting this
     *   player. {@link Knowledge#UNKNOWN} maps to false, which the policy reads as a reason not to fight.
     *   The unknown itself stays in the {@link Entry}, so a future consumer can see it.</li>
     * </ul>
     *
     * @param nowTick the tick the caller is deciding on; entries are measured against it
     * @return the sightings in the snapshot's order
     */
    public List<DefensePolicy.Sighting> asSightings(int nowTick) {
        List<DefensePolicy.Sighting> out = new ArrayList<>(entries.size());
        for (Entry e : entries) {
            Observation o = e.observation();
            boolean hostile = e.kind() == ThreatKind.HOSTILE_BY_NATURE || e.kind() == ThreatKind.DETONATING;
            boolean neutralOrPassive = e.kind() == ThreatKind.NEUTRAL || e.kind() == ThreatKind.PASSIVE;
            boolean detonates = e.kind() == ThreatKind.DETONATING;
            boolean comingForUs = o.targetingUs() == Knowledge.YES;
            out.add(new DefensePolicy.Sighting(
                    o.entityId(), o.distance(), hostile, neutralOrPassive, detonates, o.visible(), comingForUs));
        }
        return out;
    }
}
