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

import baritone.Baritone;
import baritone.api.BaritoneAPI;
import baritone.api.event.events.TickEvent;
import baritone.api.utils.IInputOverrideHandler;
import baritone.api.utils.input.Input;
import baritone.behavior.Behavior;
import baritone.pathing.movement.CarefulFooting;
import baritone.pathing.movement.KeyBeat;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * An interface with the game's control system allowing the ability to
 * force down certain controls, having the same effect as if we were actually
 * physically forcing down the assigned key.
 *
 * @author Brady
 * @since 7/31/2018
 */
public final class InputOverrideHandler extends Behavior implements IInputOverrideHandler {

    /**
     * Maps inputs to whether or not we are forcing their state down.
     */
    private final Map<Input, Boolean> inputForceStateMap = new HashMap<>();

    private final BlockBreakHelper blockBreakHelper;
    private final BlockPlaceHelper blockPlaceHelper;

    /**
     * The beat between two opposite walking keys, held here so that it survives across movements, paths
     * and process handovers - a reversal is usually the last keys of one movement and the first of the
     * next, which is exactly the boundary every other piece of state dies at.
     */
    private final KeyBeat keyBeat = new KeyBeat();

    /**
     * Whether something is asking for the use button this tick, and whether the button is down because
     * this file put it there. See {@link #holdTheUseButton()}.
     */
    private boolean useButtonAskedFor;
    private boolean holdingTheUseButton;

    /**
     * The crouch a person keeps while their footing is not certain, held here for the same reason the key
     * beat is held here: the crossing it covers outlives the movement that asked for it. A movement that
     * has to place the block it is stepping onto is the ask, and the walk onto the block it just placed is
     * the same crossing - which is exactly the tick the movement seam is in the middle of. See
     * {@link CarefulFooting}.
     */
    private final CarefulFooting footing = new CarefulFooting();

    /**
     * Squared horizontal speed that counts as a run: a walk tops out at 0.216 blocks per tick and every
     * sprint is at least 0.28, so 0.23 squared is the gap between them.
     */
    private static final double PACE_SQUARED = 0.23 * 0.23;

    public InputOverrideHandler(Baritone baritone) {
        super(baritone);
        this.blockBreakHelper = new BlockBreakHelper(baritone.getPlayerContext());
        this.blockPlaceHelper = new BlockPlaceHelper(baritone.getPlayerContext());
    }

    /**
     * Returns whether or not we are forcing down the specified {@link Input}.
     *
     * @param input The input
     * @return Whether or not it is being forced down
     */
    @Override
    public final boolean isInputForcedDown(Input input) {
        return input == null ? false : this.inputForceStateMap.getOrDefault(input, false);
    }

    /**
     * Sets whether or not the specified {@link Input} is being forced down.
     *
     * @param input  The {@link Input}
     * @param forced Whether or not the state is being forced
     */
    @Override
    public final void setInputForceState(Input input, boolean forced) {
        this.inputForceStateMap.put(input, forced);
    }

    /**
     * Clears the override state for all keys
     * <p>
     * Deliberately not the held use button: see {@link #holdTheUseButton()} for why it is a lease and
     * why every movement rebuilding the task's own keys every tick must not lift somebody's bite.
     */
    @Override
    public final void clearAllKeys() {
        this.inputForceStateMap.clear();
    }

    /**
     * Hold the game's use button down for this tick, the way a thumb does - ask again next tick to keep
     * holding.
     * <p>
     * The click helpers in this tick are the wrong shape for a use that lasts rather than lands: they
     * re-click on a cadence, and re-clicking a block is another block while re-clicking a mouthful is a
     * mouthful started over and re-clicking a bow is an arrow that is never drawn. A sustained use wants
     * the button down and held, because vanilla completes a consumable over its own ticking time and
     * abandons it the instant that key comes up - a bite that is not held is not a bite at all.
     * <p>
     * It is a lease rather than a switch: the ask is spent by the tick that reads it, so the button can
     * only stay down while somebody is asking for it every tick, and a holder that goes away - a
     * behaviour that stops ticking, a world that closes - cannot leave it down by forgetting. It is also
     * why {@link #clearAllKeys()} does not touch it: that is called by every movement, every tick of a
     * walk, to rebuild the task's own keys, and a meal eaten while walking would come off the player's
     * mouth twenty times a second if it were.
     */
    public void holdTheUseButton() {
        this.useButtonAskedFor = true;
    }

    @Override
    public final void onTick(TickEvent event) {
        if (event.getType() == TickEvent.Type.OUT) {
            return;
        }
        if (isInputForcedDown(Input.CLICK_LEFT)) {
            setInputForceState(Input.CLICK_RIGHT, false);
        }
        // Neither button while the hand is between slots: the item that would be used is not the one that
        // was decided on. The beat before the click still runs underneath - the two overlap, the way a
        // person's key press and their crosshair settling do - so a switch costs its own beat and no more.
        final boolean handReady = !baritone.getInventoryBehavior().switchingHands();
        blockBreakHelper.tick(isInputForcedDown(Input.CLICK_LEFT), handReady);
        blockPlaceHelper.tick(isInputForcedDown(Input.CLICK_RIGHT), handReady);
        // The use that lasts, as opposed to the two clicks above: the ask is spent by this tick, and the
        // button follows it. Nothing here is a click - the click was made once, by whoever started the
        // use - so this only keeps the game from taking the mouthful away while it is still being chewed
        final boolean holdTheUse = this.useButtonAskedFor;
        this.useButtonAskedFor = false;
        if (holdTheUse && !ctx.minecraft().options.keyUse.isDown()) {
            ctx.minecraft().options.keyUse.setDown(true);
            this.holdingTheUseButton = true;
        } else if (!holdTheUse && this.holdingTheUseButton) {
            ctx.minecraft().options.keyUse.setDown(false);
            this.holdingTheUseButton = false;
        }

        if (!baritone.getPathingBehavior().isPathing()) {
            footing.reset(); // a crossing that ended while nobody was asking is not one being walked
        }
        if (inControl()) {
            if (ctx.player().input.getClass() != PlayerMovementInput.class) {
                keyBeat.reset(); // somebody else had the keys; the hands come to them fresh
                footing.reset();
                ctx.player().input = new PlayerMovementInput(this);
            }
        } else {
            if (ctx.player().input.getClass() == PlayerMovementInput.class) { // allow other movement inputs that aren't this one, e.g. for a freecam
                ctx.player().input = new KeyboardInput(ctx.minecraft().options);
            }
        }
    }

    /**
     * The four walking keys as the fingers actually hold them this tick: asked for here, shaped by the
     * beat between two opposite directions ({@link KeyBeat}), and pressed by {@link PlayerMovementInput}.
     * <p>
     * The beat is only spent where a tick of standing is what a person would do anyway - see
     * {@link #atAPace()}. Everywhere else the keys answer the tick they are asked.
     */
    public final KeyBeat.Keys beatTheKeys(KeyBeat.Keys desired) {
        return keyBeat.tick(desired, atAPace());
    }

    /**
     * The crouch a person keeps while their footing is not certain: asked for once a tick by the movement
     * that is crossing, held across the movement boundary that would otherwise end it (see
     * {@link CarefulFooting}).
     *
     * @param asked             the movement has to place the block it is stepping onto, so the block
     *                          under the next step is not there yet
     * @param anotherGapIsAhead the block under the step after this one is missing too, which is what the
     *                          middle of a crossing looks like
     * @return whether the crouch is down this tick
     */
    public final boolean carefulFooting(boolean asked, boolean anotherGapIsAhead) {
        return footing.tick(asked, anotherGapIsAhead);
    }

    /**
     * Whether that crouch is down: {@link PlayerMovementInput} presses it like the sneak key it is, and
     * {@link #atAPace()} has to know, because a crossing is the one place a beat of no keys is not a beat
     * a person would take.
     */
    public final boolean isCrouchingForTheFooting() {
        return footing.crouched();
    }

    /**
     * Whether the player is running along the ground somewhere a beat between two directions is a beat a
     * person would take.
     * <p>
     * A running reversal is the visible one: full speed one way, and on the next tick a packet saying the
     * other, with the head not even turning. It is also the safe one to pause - momentum carries on
     * through a beat of no keys, and a path's tolerance eats that distance for breakfast. The cases where
     * a pause would not be safe are all the cases where the player is not at a run: edging along a bridge
     * or back from a drop (sneaking, or barely walking, or both), anything in water, anything in the air,
     * and anything the elytra solver is flying - those keys answer the tick they are asked, because there
     * the keys are not fingers, they are a control loop.
     */
    private boolean atAPace() {
        if (ctx.player() == null) {
            return false;
        }
        if (!ctx.player().onGround()
                || ctx.player().isShiftKeyDown()
                || footing.crouched() // the crouch a crossing is walked with: the keys are a control loop here
                || isInputForcedDown(Input.SNEAK)
                || ctx.player().isInWater()
                || ctx.player().isInLava()
                || ctx.player().getAbilities().flying) {
            return false;
        }
        if (isInputForcedDown(Input.SPRINT) || ctx.player().isSprinting()) {
            return true;
        }
        // still coasting: the sprint key came up a beat ago, but the player is moving at a run
        final Vec3 velocity = ctx.player().getDeltaMovement();
        return velocity.x * velocity.x + velocity.z * velocity.z >= PACE_SQUARED;
    }

    private boolean inControl() {
        for (Input input : new Input[]{Input.MOVE_FORWARD, Input.MOVE_BACK, Input.MOVE_LEFT, Input.MOVE_RIGHT, Input.SNEAK, Input.JUMP}) {
            if (isInputForcedDown(input)) {
                return true;
            }
        }
        // if we are not primary (a bot) we should set the movementinput even when idle (not pathing)
        return baritone.getPathingBehavior().isPathing() || baritone != BaritoneAPI.getProvider().getPrimaryBaritone();
    }

    public BlockBreakHelper getBlockBreakHelper() {
        return blockBreakHelper;
    }
}
