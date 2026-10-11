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
import baritone.utils.EatPolicy;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;

import java.util.ArrayList;
import java.util.List;

/**
 * A hungry bot eats.
 * <p>
 * This is a behaviour and not a process on purpose. Eating does not want the path, the goal or the
 * task: it wants the hand, for a second and a half, and it must be able to happen while the bot is
 * walking somewhere, because that is when a person eats. A process would have to take control, stop
 * the walk, and give it back, and every one of those handovers is a way to lose a path - which is a
 * large price for a sandwich. So this sits beside the pathing behaviour and asks the same hand the
 * other behaviours ask, one beat after the inventory behaviour has had its turn and before the input
 * handler presses anything.
 * <p>
 * What it will not do, and these are the bounds rather than the policy:
 * <ul>
 *   <li><b>It never changes what is in the hand while a process is in control.</b> If the bot is
 *   mining, building or crossing, the hotbar belongs to that task; a snack is not worth the
 *   pickaxe's slot. The one exception is food already in the hand, which costs the task nothing.</li>
 *   <li><b>It never eats a special food.</b> See {@link EatPolicy}: no golden apples, no chorus
 *   fruit, nothing that applies an effect, nothing harmful.</li>
 *   <li><b>It never takes the hand from the player.</b> It asks the same way the other behaviours
 *   ask, and it waits for the hand to actually arrive before pressing - a right click with the
 *   wrong item in hand is not a missed meal, it is a thrown pearl.</li>
 *   <li><b>It gives up.</b> Three attempts per hunger episode and then quiet, so a bot whose food
 *   is refused cannot spend the whole game clicking at a wall.</li>
 * </ul>
 */
public final class EatBehavior extends Behavior implements Helper {

    /**
     * How long a hotbar switch is given to land before the attempt is written off. The switch itself
     * is a beat; a second and a half is far past that and still short enough that the bot is not
     * standing there with its hand halfway to a slot.
     */
    private static final int SWITCH_WINDOW = 30;

    private enum Phase {
        IDLE,
        REACHING,
        EATING
    }

    private final EatPolicy.Budget budget = new EatPolicy.Budget(EatPolicy.ATTEMPTS_PER_EPISODE);

    private Phase phase = Phase.IDLE;
    private int targetSlot = -1;
    private int phaseTicks;
    private int startFoodLevel;
    private int startCount;
    private int window;
    private boolean notedEmpty;

    public EatBehavior(Baritone baritone) {
        super(baritone);
    }

    @Override
    public void onTick(TickEvent event) {
        if (event.getType() == TickEvent.Type.OUT) {
            return;
        }
        if (!Baritone.settings().autoEat.value) {
            abandon();
            return;
        }
        LocalPlayer player = ctx.player();
        if (player == null) {
            abandon();
            return;
        }
        if (ctx.world() == null || !ctx.playerController().getGameType().isSurvival()) {
            // Creative and spectator have a full bar that never moves, and no world has no bar at all
            abandon();
            return;
        }
        if (phase == Phase.EATING) {
            eating(player);
            return;
        }
        // A hand on a rocket is not a hand for a sandwich, and the elytra's own owner is minding it
        if (player.isFallFlying()) {
            abandon();
            return;
        }
        if (phase == Phase.IDLE) {
            idle(player);
            return;
        }
        reaching(player);
    }

    /**
     * Decide whether to eat at all, and what.
     */
    private void idle(LocalPlayer player) {
        if (player.getFoodData().getFoodLevel() >= Baritone.settings().autoEatThreshold.value) {
            // Not hungry: the episode is over, and the next one starts with a fresh budget
            budget.reset();
            notedEmpty = false;
            return;
        }
        if (!handsFree(player)) {
            return;
        }
        if (!budget.mayTry()) {
            return;
        }
        EatPolicy.Food chosen = choose(player);
        if (chosen == null) {
            if (!notedEmpty) {
                notedEmpty = true;
                logDebug("Hungry and nothing in the hand or the hotbar to eat; leaving it to you.");
            }
            return;
        }
        if (chosen.slot() == player.getInventory().getSelectedSlot()) {
            // Already in the hand, which is the case that costs a task nothing
            press(player);
            return;
        }
        targetSlot = chosen.slot();
        phase = Phase.REACHING;
        phaseTicks = 0;
        baritone.getInventoryBehavior().hold(targetSlot);
    }

    /**
     * Wait for the hand to arrive in the slot that was asked for, then eat.
     */
    private void reaching(LocalPlayer player) {
        if (player.containerMenu != player.inventoryMenu) {
            // A menu opened over the top of this: whatever is in the hand is now the player's choice
            abandon();
            return;
        }
        if (player.getInventory().getSelectedSlot() == targetSlot
                && !baritone.getInventoryBehavior().switchingHands()) {
            press(player);
            return;
        }
        if (phaseTicks == 0) {
            baritone.getInventoryBehavior().hold(targetSlot);
        }
        if (phaseTicks++ > SWITCH_WINDOW) {
            abandon();
        }
    }

    /**
     * Watch the mouth while the button is held. Vanilla finishes the bite by itself, so this is a wait
     * as much as a hold; and the wait is bounded, because a bite that never lands must not become a
     * button that is held forever.
     */
    private void eating(LocalPlayer player) {
        holdUseButton(); // the bite lasts as long as we keep asking for the button
        boolean swallowed = player.getFoodData().getFoodLevel() > startFoodLevel
                || player.getItemInHand(InteractionHand.MAIN_HAND).getCount() < startCount;
        if (swallowed) {
            phase = Phase.IDLE;
            window = 0;
            // It worked. A second helping for a bar that is still short is a new ask, not a retry
            budget.reset();
            return;
        }
        if (!player.isUsingItem()) {
            // The bite was dropped before it landed - interrupted, or never really started
            abandon();
            return;
        }
        if (phaseTicks++ > window) {
            // Our own bite, so it is ours to take back; the budget has already paid for it
            player.stopUsingItem();
            abandon();
        }
    }

    /**
     * Drop whatever this behaviour was in the middle of. Not a failure and not a success: the next
     * tick looks again from the start, carrying the budget the episode has already spent. Nothing
     * here reaches for the hand - whatever a task was doing with it is still its own.
     */
    private void abandon() {
        phase = Phase.IDLE;
        targetSlot = -1;
        phaseTicks = 0;
        window = 0;
    }

    /**
     * Press use on whatever is in the hand. Only ever called with an edible item in the main hand.
     */
    private void press(LocalPlayer player) {
        ItemStack inHand = player.getItemInHand(InteractionHand.MAIN_HAND);
        startFoodLevel = player.getFoodData().getFoodLevel();
        startCount = inHand.getCount();
        window = EatPolicy.completionWindow(consumeTicks(inHand));
        budget.spend();
        InteractionResult result = ctx.playerController().processRightClick(player, ctx.world(), InteractionHand.MAIN_HAND);
        if (result != null && result.consumesAction()) {
            holdUseButton();
            phase = Phase.EATING;
            phaseTicks = 0;
        } else {
            abandon();
        }
    }

    /**
     * One press is not a bite. Vanilla completes a consumable over its own ticking time and abandons it
     * the moment the use button comes up - so a single call to start one is a mouthful the game releases
     * again on the next tick, which is what a bot that "eats" and never chews looks like. The mod's click
     * helpers re-click on a cadence, which suits a placement and not a mouthful, so what a sustained use
     * needs is the button held down.
     * <p>
     * That belongs to the input layer, which owns the player's keys: this asks it, every tick of the bite,
     * through {@code InputOverrideHandler.holdTheUseButton}, and stops asking when the bite is over. The
     * ask is a lease of one tick, so the button cannot outlive the meal even if this behaviour stops
     * ticking, and asking rather than switching is why a walk can still eat - see that method for the
     * argument in full.
     */
    private void holdUseButton() {
        baritone.getInputOverrideHandler().holdTheUseButton();
    }

    /**
     * The item to eat, or {@code null} if there is nothing the bot is allowed to reach for.
     */
    private EatPolicy.Food choose(LocalPlayer player) {
        int selected = player.getInventory().getSelectedSlot();
        EatPolicy.Food held = describe(player.getItemInHand(InteractionHand.MAIN_HAND), selected);
        if (EatPolicy.edible(held)) {
            return held;
        }
        // Changing what is in the hand is a different proposition: the hand belongs to whichever
        // process is in control, and nothing here is worth taking it from one.
        if (!baritone.getPathingControlManager().mostRecentInControl().isEmpty()) {
            return null;
        }
        List<EatPolicy.Food> hotbar = new ArrayList<>(9);
        for (int slot = 0; slot < 9; slot++) {
            hotbar.add(describe(player.getInventory().getItem(slot), slot));
        }
        EatPolicy.Food best = EatPolicy.chooseFood(hotbar);
        if (best != null) {
            return best;
        }
        // And the main inventory only if the user has told Baritone it may move items there. The
        // move lands in a hotbar slot a beat later, so this tick asks for it and the next one finds
        // the food where it was put.
        if (!Baritone.settings().allowInventory.value) {
            return null;
        }
        for (int slot = 9; slot < 36; slot++) {
            if (EatPolicy.edible(describe(player.getInventory().getItem(slot), slot))) {
                baritone.getInventoryBehavior().attemptToPutOnHotbar(slot, ignored -> false);
                return null;
            }
        }
        return null;
    }

    /**
     * Is the hand free to be asked for? This is the idle-side test, so it includes a switch that is
     * still on its way - a bot that pressed during one would be clicking with the previous item.
     */
    private boolean handsFree(LocalPlayer player) {
        return !player.isUsingItem()
                && !player.isHandsBusy()
                && !baritone.getInventoryBehavior().switchingHands()
                && player.containerMenu == player.inventoryMenu;
    }

    private static int consumeTicks(ItemStack stack) {
        Consumable consumable = stack.get(DataComponents.CONSUMABLE);
        // A food with no consumable component still takes vanilla's default eating time
        return consumable == null ? 32 : consumable.consumeTicks();
    }

    /**
     * Describe a stack the way {@link EatPolicy} wants to see it. An empty slot, or anything that is
     * not food, comes back as something the policy will refuse rather than as a special case here.
     */
    private static EatPolicy.Food describe(ItemStack stack, int slot) {
        if (stack == null || stack.isEmpty()) {
            return new EatPolicy.Food(slot, 0, 0, false, false);
        }
        FoodProperties food = stack.get(DataComponents.FOOD);
        if (food == null) {
            return new EatPolicy.Food(slot, 0, stack.getCount(), false, false);
        }
        Consumable consumable = stack.get(DataComponents.CONSUMABLE);
        boolean hasEffects = consumable != null && !consumable.onConsumeEffects().isEmpty();
        return new EatPolicy.Food(slot, food.nutrition(), stack.getCount(), food.canAlwaysEat(), hasEffects);
    }
}
