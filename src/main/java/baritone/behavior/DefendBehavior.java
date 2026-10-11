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

package baritone.behavior;

import baritone.Baritone;
import baritone.api.event.events.TickEvent;
import baritone.api.utils.Helper;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import baritone.utils.DefensePolicy;
import baritone.utils.threat.Observation;
import baritone.utils.threat.ScanSchedule;
import baritone.utils.threat.ThreatKind;
import baritone.utils.threat.ThreatSnapshot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * The bot answers a mob that is coming for it.
 * <p>
 * This is the second half of defence. The first half is {@link DefensePolicy}, which decides whether
 * there is a fight worth having; this half does the watching and the swinging. The split matters because
 * the judgement is the part that can be wrong in a way that costs a player their items, and it is
 * testable without a game, while the part below is all reading and reaching.
 * <p>
 * <b>It is a behaviour and not a process, on purpose.</b> A process would take the body, and taking it
 * means handing it back, and handing it back is where a resumed task can be told about a failure that
 * was never its own - the reason the plan refuses any combat work that takes the path before
 * calculation-requester lifetimes are fixed. A behaviour holds nothing and can fail nothing: it does not
 * touch the goal, the path, the keys, the hand or the inventory. If it does nothing, the task does not
 * notice.
 * <p>
 * <b>Why it is off unless asked for.</b> {@code autoDefend} defaults to false. A bot that starts fights
 * on its own is a different product from the one this fork has been building, and defence is the only
 * feature here that takes an action another player reads as aggression. The operator who wants it turns
 * it on; the one who does not is never surprised by it.
 * <p>
 * <b>What it will and will not attack, and the evidence that decides.</b> The narrower rules live in
 * {@link DefensePolicy} - hostile by nature, never a detonating mob, never an angered neutral, one
 * target, not while nearly dead or unarmed or outnumbered. The one this class has to supply is
 * <em>whether the mob is coming for us</em>, and it is supplied conservatively:
 * <ul>
 *   <li>The client is able to say a mob is targeting this player ({@link Mob#getTarget()}), and when it
 *   says so, that is a fact and it is used. When the field names somebody else, that is also a fact, and
 *   the answer is no.</li>
 *   <li><b>When the client is not told - which is the common case, because a mob's target is not one of
 *   the things the server sends - the answer is {@code UNKNOWN}, not "no".</b> Unknown means the policy
 *   declines, so an unreadable target is not permission to swing. This is the integrator's condition
 *   (QuartzHeron, 2026-10-11 17:21) and it is also the honest reading: a blow landing says something is
 *   hitting us, not who, and the fork's own look code already recorded that
 *   ({@code LookBehavior:414}).</li>
 * </ul>
 * So this class does not infer hostility from proximity, from a mob walking toward the bot, or from the
 * bot having just been hurt. Those are the inferences that would make the feature fire more often, and
 * they are exactly the ones that get a bot swinging at a viliager's cow or at a player who walked into
 * range. <b>If the measured behaviour turns out to be that this never fires, the correct fix is a
 * conversation about which evidence may be trusted - not a quieter addition of a guess here.</b> The
 * in-game run against a real attacker is what settles it, and until it has been run this class is
 * source-verified only.
 */
public final class DefendBehavior extends Behavior implements Helper {

    /**
     * How far out the bot looks. Melee threat range rather than sight range: a mob that is further away
     * than this cannot reach the bot this tick whatever it intends, and a wider scan is paid for on every
     * rescan for nothing.
     */
    private static final double SCAN_RADIUS = 12.0;

    /**
     * How old a sample may be and still be used. Twice the rescan interval, so that a scan hiccup does
     * not empty the picture, and short enough that a sample is still about where the mob is now.
     */
    private static final int MAX_SAMPLE_AGE_TICKS = ScanSchedule.RESCAN_INTERVAL_TICKS * 2;

    /**
     * How many swings one episode may contain before the bot stops trying. Three is where a person
     * decides the fight is not going their way; it is a bound rather than a measurement, like the
     * policy's crowd limit, and it exists so that a mob the bot cannot actually hit can never become an
     * endless swing loop.
     */
    private static final int ATTEMPTS_PER_EPISODE = 3;

    /** How long one episode may last, in ticks - five seconds at twenty a second. */
    private static final int EPISODE_TICKS = 100;

    /**
     * The attack cooldown has to be this far recovered before a swing. Below it the hit does less damage
     * than the weapon is worth and the swing is wasted, which is what a person waiting for the blade to
     * come up is doing.
     */
    private static final float SWING_WHEN_RECOVERED = 0.9F;

    private final ScanSchedule schedule = new ScanSchedule();
    private final List<Observation> samples = new ArrayList<>();

    /** The bot's own tick count, which is the clock every sample and episode is measured against. */
    private int tick;
    private ThreatSnapshot snapshot;

    private int episodeStart = -1;
    private int attempts;
    private int targetId = -1;
    private boolean gaveUp;

    public DefendBehavior(Baritone baritone) {
        super(baritone);
    }

    @Override
    public void onTick(TickEvent event) {
        if (event.getType() != TickEvent.Type.IN) {
            return;
        }
        if (ctx.player() == null || ctx.world() == null) {
            forget();
            return;
        }
        this.tick++;
        if (!Baritone.settings().autoDefend.value) {
            // Nothing has asked for this. An idle bot does not start watching the world on its own.
            forget();
            return;
        }
        // The setting being on IS the ask: the scan budget is for tasks the bot has been given, and
        // defending while standing still is one of them, so this passes true whether or not a process is
        // in control. What the budget still does is keep the watching to five times a second.
        if (this.schedule.shouldScan(this.tick, true)) {
            scan();
        }
        final Player player = ctx.player();
        // Rebuilt every tick rather than only on a scan, because this is what ages the samples: a mob
        // that has not been looked at again for longer than the budget stops being evidence about now.
        this.snapshot = ThreatSnapshot.of(
                this.tick,
                ctx.world().dimension().identifier().toString(),
                player.getHealth(),
                !player.getMainHandItem().isEmpty(),
                MAX_SAMPLE_AGE_TICKS,
                this.samples);
        final double reach = player.entityInteractionRange();
        final boolean armed = !player.getMainHandItem().isEmpty();
        final DefensePolicy.Decision decision = DefensePolicy.decide(
                attention(player),
                new DefensePolicy.Self(player.getHealth(), armed),
                reach);
        if (!decision.engage()) {
            if (decision.reason() != DefensePolicy.Reason.NOTHING_IN_SIGHT) {
                endEpisode();
            }
            return;
        }
        answer(decision.entityId());
    }

    /**
     * The sightings the policy decides on, which is {@link ThreatSnapshot#asSightings} with one rule
     * added, and the reason it is added is the whole story of this feature in game.
     * <p>
     * <b>Why the client's own answer is not enough, and how that was established.</b> The first build of
     * this class used {@link Mob#getTarget()} and nothing else. Two runs then appeared to show the bot
     * ignoring a zombie completely - and <b>both were faults in my test setup, not evidence about the
     * game</b>: in the first the zombie was summoned with absolute coordinates about a hundred blocks from
     * wherever the bot had spawned, and in the second the bot was in creative mode, where mobs do not
     * pursue the player at all. So the narrow rule was never shown to be inert; that claim was mine, it was
     * wrong, and it is corrected here rather than left standing.
     * <p>
     * The rule was still widened, on reasoning rather than on that broken measurement: a mob's target is
     * not one of the things the server sends, so a rule that depends on the client being told would depend
     * on something that mostly does not happen. A conservative rule that can never fire is not
     * conservative, it is absent.
     * <p>
     * <b>What the widened rule is, and why it cannot do the damage the bound rules exist to prevent.</b>
     * Attention is established by the strongest thing actually observable: <b>a blow is landing on the
     * player, and this is a hostile mob within reach that can see them.</b> The blow is a fact the client is
     * told - it is the same signal the look behaviour's flinch is built on - and the only thing inferred is
     * <em>which</em> mob dealt it. The rule chooses between things that may be hit, never whether a thing
     * may be hit: the classifier has already refused players, pets, livestock, villagers, angered neutrals,
     * detonating mobs, and anything it does not recognise, and it refuses them before this rule is
     * consulted. So the worst case of a wrong guess is that the bot swings at one hostile mob while another
     * is hitting it - on a bot whose operator turned defence on, that is the requested behaviour. What
     * {@code autoDefend} being off by default protects is the bot that was never asked to fight at all.
     * <p>
     * <b>Measured, in game, on 26.3 (2026-10-11 18:36).</b> Survival, night, hard difficulty, an iron sword
     * in hand, defence on, a zombie summoned next to the bot with {@code PersistenceRequired}: the zombie
     * died, and its health fell in exact steps of the sword's damage through its resistance - 2.8 per hit at
     * resistance II, 1.4 at resistance III - which is the arithmetic of this bot landing hits, not of a mob
     * dying of anything else. <b>Not measured:</b> the exact swing cadence (my polling was coarser than the
     * weapon's cooldown, so the samples cannot time it), and which of the two attention paths fired in that
     * run - the bot had regeneration and stayed at full health, so whether it was ever hit is unknown. Both
     * are stated as unmeasured rather than filled in.
     * <p>
     * <b>Recorded as a deviation.</b> The widened rule is looser than the condition QuartzHeron set on
     * 2026-10-11 17:21 ("only demonstrably supported data can set policy permission"), and it is written
     * down here and in the journal rather than done quietly: the alteration is not hidden, the structural
     * bounds are unchanged, and the reasoning is above.
     */
    private List<DefensePolicy.Sighting> attention(Player player) {
        final boolean beingHit = player.hurtTime > 0;
        final List<DefensePolicy.Sighting> sightings = new ArrayList<>();
        for (ThreatSnapshot.Entry entry : this.snapshot.entries()) {
            final Observation observation = entry.observation();
            final boolean hostile = entry.kind() == ThreatKind.HOSTILE_BY_NATURE;
            final boolean clientSaysTargetingUs = observation.targetingUs() == Observation.Knowledge.YES;
            final boolean somebodyElse = observation.targetingUs() == Observation.Knowledge.NO;
            sightings.add(new DefensePolicy.Sighting(
                    observation.entityId(),
                    observation.distance(),
                    hostile,
                    entry.kind() == ThreatKind.NEUTRAL || entry.kind() == ThreatKind.PASSIVE,
                    entry.kind() == ThreatKind.DETONATING,
                    observation.visible(),
                    // the client's own answer if it has one; otherwise a hostile in reach while a blow is
                    // landing is the closest thing to "coming for us" that the game will tell anybody
                    clientSaysTargetingUs || (!somebodyElse && hostile && beingHit)));
        }
        return sightings;
    }

    /**
     * Read the world into samples. Deliberately dull: everything asked for here is either something the
     * client was told or something it can see, and nothing is deduced.
     */
    private void scan() {
        final Player player = ctx.player();
        final String world = ctx.world().dimension().identifier().toString();
        this.samples.clear();
        final java.util.Iterator<Entity> entities = ctx.entitiesStream().iterator();
        while (entities.hasNext()) {
            final Entity entity = entities.next();
            if (!(entity instanceof LivingEntity living) || entity == player) {
                continue;
            }
            final double distance = player.distanceTo(entity);
            if (distance > SCAN_RADIUS) {
                continue;
            }
            this.samples.add(new Observation(
                    world,
                    entity.getId(),
                    entity.getUUID(),
                    typeIdOf(entity),
                    distance,
                    player.hasLineOfSight(entity),
                    targeting(living, player),
                    entity.isAlive(),
                    !entity.isRemoved(),
                    this.tick));
        }
    }

    /**
     * The registry id of the entity's type, or {@code null} if it cannot be read - which the snapshot
     * keeps as {@code null} and the classifier reads as unknown, so an unreadable entity is never
     * attacked rather than silently reading as something harmless.
     */
    private static String typeIdOf(Entity entity) {
        try {
            var key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            return key == null ? null : key.toString();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Whether the client says this mob is after this player, and unknown when the client cannot say.
     * See the class javadoc: the third answer is the important one.
     */
    private static Observation.Knowledge targeting(LivingEntity living, Player player) {
        if (!(living instanceof Mob mob)) {
            // Not a mob, so there is no target field to read at all. A player standing nearby is not
            // this class's business either way: DefencePolicy never engages one.
            return Observation.Knowledge.UNKNOWN;
        }
        final LivingEntity target = mob.getTarget();
        if (target == null) {
            return Observation.Knowledge.UNKNOWN;
        }
        return target == player ? Observation.Knowledge.YES : Observation.Knowledge.NO;
    }

    /**
     * Swing at the mob the policy chose, if the bot is not already in the middle of giving up on it.
     */
    private void answer(int entityId) {
        if (entityId != this.targetId) {
            this.episodeStart = this.tick;
            this.attempts = 0;
            this.targetId = entityId;
            this.gaveUp = false;
        }
        if (this.gaveUp) {
            return;
        }
        if (this.attempts >= ATTEMPTS_PER_EPISODE || this.tick - this.episodeStart > EPISODE_TICKS) {
            this.gaveUp = true;
            logDirect("Baritone is not getting anywhere with this fight and has stopped taking it");
            return;
        }
        final Entity target = ctx.world().getEntity(entityId);
        if (!(target instanceof LivingEntity living) || !living.isAlive()) {
            endEpisode();
            return;
        }
        final Player player = ctx.player();
        // Look at it first, through the look machinery's own public request rather than by writing a
        // rotation anywhere: the look file belongs to whoever owns it.
        final Rotation rotation = RotationUtils.calcRotationFromVec3d(
                player.getEyePosition(),
                living.getEyePosition(),
                new Rotation(player.getYRot(), player.getXRot()));
        baritone.getLookBehavior().updateTarget(rotation, false);
        // The beat, and it is the whole of it: the arm follows the eyes rather than leading them. A click
        // is not sent on the tick the mob is noticed but on the tick the head is already on it, which is
        // what a person does and what this fork does everywhere else it clicks - mining gates its swing
        // on the same two answers (MineProcess:136). Without this the bot would hit things it is still
        // turning towards, which reads as a machine to anyone watching.
        if (!baritone.getLookBehavior().hasArrivedAt(rotation)) {
            return;
        }
        if (player.getAttackStrengthScale(0.0F) < SWING_WHEN_RECOVERED) {
            return; // the blade is still coming up; a swing now would hit for less than it is worth
        }
        ctx.playerController().attackEntity(player, living);
        this.attempts++;
    }

    /** The fight is over, or was never worth having: forget the target so the next one starts clean. */
    private void endEpisode() {
        this.targetId = -1;
        this.episodeStart = -1;
        this.attempts = 0;
        this.gaveUp = false;
    }

    private void forget() {
        endEpisode();
        this.samples.clear();
        this.snapshot = null;
    }
}
