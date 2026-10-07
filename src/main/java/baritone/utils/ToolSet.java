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
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.effects.EnchantmentAttributeEffect;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A cached list of the best tools on the hotbar for any block
 *
 * @author Avery, Brady, leijurv
 */
public class ToolSet {

    /**
     * A cache mapping a {@link Block} to how long it will take to break
     * with this toolset, given the optimum tool is used.
     */
    private final Map<Block, Double> breakStrengthCache;

    /**
     * My buddy leijurv owned me so we have this to not create a new lambda instance.
     */
    private final Function<Block, Double> backendCalculation;

    private final LocalPlayer player;

    /**
     * Used for evaluating the material cost of a tool.
     * see {@link #getMaterialCost(ItemStack)}
     * Prefer tools with lower material cost (lower index in this list).
     */
    private static final List<TagKey<Item>> materialTagsPriorityList = List.of(
        ItemTags.WOODEN_TOOL_MATERIALS,
        ItemTags.STONE_TOOL_MATERIALS,
        ItemTags.IRON_TOOL_MATERIALS,
        ItemTags.GOLD_TOOL_MATERIALS,
        ItemTags.DIAMOND_TOOL_MATERIALS,
        ItemTags.NETHERITE_TOOL_MATERIALS
    );

    public ToolSet(LocalPlayer player) {
        breakStrengthCache = new HashMap<>();
        this.player = player;

        if (Baritone.settings().considerPotionEffects.value) {
            double amplifier = potionAmplifier();
            Function<Double, Double> amplify = x -> amplifier * x;
            backendCalculation = amplify.compose(this::getBestDestructionTime);
        } else {
            backendCalculation = this::getBestDestructionTime;
        }
    }

    /**
     * Using the best tool on the hotbar, how fast we can mine this block
     *
     * @param state the blockstate to be mined
     * @return the speed of how fast we'll mine it. 1/(time in ticks)
     */
    public double getStrVsBlock(BlockState state) {
        return breakStrengthCache.computeIfAbsent(state.getBlock(), backendCalculation);
    }

    /**
     * Evaluate the material cost of a possible tool.
     * If all else is equal, we want to prefer the tool with the lowest material cost.
     * i.e. we want to prefer a wooden pickaxe over a stone pickaxe, if all else is equal.
     * @param itemStack a possibly empty ItemStack
     * @return values from 0 up
     */
    private int getMaterialCost(ItemStack itemStack) {
        for (int i = 0; i < materialTagsPriorityList.size(); i++) {
            final TagKey<Item> tag = materialTagsPriorityList.get(i);
            if (itemStack.is(tag)) return i;
        }
        return -1;
    }

    public boolean hasSilkTouch(ItemStack stack) {
        ItemEnchantments enchantments = stack.getEnchantments();
        for (Holder<Enchantment> enchant : enchantments.keySet()) {
            // silk touch enchantment is still special cased as affecting block drops
            // not possible to add custom attribute via datapack
            if (enchant.is(Enchantments.SILK_TOUCH) && enchantments.getLevel(enchant) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Calculate which tool on the hotbar is best for mining, depending on an override setting,
     * related to auto tool movement cost, it will either return current selected slot, or the best slot.
     *
     * @param b the blockstate to be mined
     * @return An int containing the index in the tools array that worked best
     */

    public int getBestSlot(Block b, boolean preferSilkTouch) {
        return getBestSlot(b, preferSilkTouch, false);
    }

    /**
     * How much faster another tool has to be before it is worth putting down what is already in hand and
     * taking it out: a quarter again as fast.
     * <p>
     * The best tool for each block is the best tool for each block, and a bot that reaches for it every
     * time swaps the item in its hand on the boundary between two blocks of a different kind - a mixed
     * column, the dirt and the stone under a tree stump, the breakable block in front of a traverse - and
     * that is not what a hand does. Somebody mining a run of mixed blocks holds what they have unless it is
     * properly worse, because a scroll or a number key and the second take is a movement they would rather
     * not make. A quarter is the difference between one tool and a materially quicker one; two pickaxes of
     * different metals both do the job, and a shovel over a pickaxe in dirt is not a preference, it is
     * four times the speed and worth the swap.
     */
    public static final double SWITCH_GAIN = 1.25;

    /**
     * Whether the better slot is worth taking over the one already in hand.
     * <p>
     * Pure, so that what counts as worth switching for can be tested without a world to mine in. See
     * {@link #SWITCH_GAIN}.
     */
    public static boolean worthSwitching(double heldSpeed, double bestSpeed) {
        return bestSpeed > heldSpeed * SWITCH_GAIN;
    }

    public int getBestSlot(Block b, boolean preferSilkTouch, boolean pathingCalculation) {

        /*
        If we actually want know what efficiency our held item has instead of the best one
        possible, this lets us make pathing depend on the actual tool to be used (if auto tool is disabled)
        */
        if (!Baritone.settings().autoTool.value && pathingCalculation) {
            return player.getInventory().getSelectedSlot();
        }

        final int held = player.getInventory().getSelectedSlot();
        // Whether the item already in hand was one of the tools considered, and how fast it is against
        // this block. A sword the user has told us not to mine with, or a tool that itemSaver is keeping
        // back, was never a candidate and must not be held on to: those are the user's rules, not taste.
        double heldSpeed = Double.NEGATIVE_INFINITY;

        int best = 0;
        double highestSpeed = Double.NEGATIVE_INFINITY;
        int lowestCost = Integer.MIN_VALUE;
        boolean bestSilkTouch = false;
        BlockState blockState = b.defaultBlockState();
        for (int i = 0; i < 9; i++) {
            ItemStack itemStack = player.getInventory().getItem(i);
            if (!Baritone.settings().useSwordToMine.value && itemStack.is(ItemTags.SWORDS)) {
                continue;
            }

            if (Baritone.settings().itemSaver.value && (itemStack.getDamageValue() + Baritone.settings().itemSaverThreshold.value) >= itemStack.getMaxDamage() && itemStack.getMaxDamage() > 1) {
                continue;
            }
            double speed = calculateSpeedVsBlock(itemStack, blockState);
            if (i == held) {
                heldSpeed = speed;
            }
            boolean silkTouch = hasSilkTouch(itemStack);
            if (speed > highestSpeed) {
                highestSpeed = speed;
                best = i;
                lowestCost = getMaterialCost(itemStack);
                bestSilkTouch = silkTouch;
            } else if (speed == highestSpeed) {
                int cost = getMaterialCost(itemStack);
                if ((cost < lowestCost && (silkTouch || !bestSilkTouch)) ||
                        (preferSilkTouch && !bestSilkTouch && silkTouch)) {
                    highestSpeed = speed;
                    best = i;
                    lowestCost = cost;
                    bestSilkTouch = silkTouch;
                }
            }
        }

        // Pathing cost estimates are about how long a block would take, not about the hand doing it, and
        // nothing here should make a path cheaper or dearer than it was: the tools the user actually has
        // are priced the way they always were.
        if (pathingCalculation || best == held || heldSpeed == Double.NEGATIVE_INFINITY) {
            return best;
        }
        // Silk touch is a drop, not a speed: a user who asked for it gets it, even when the plain tool in
        // hand would mine just as quickly.
        if (preferSilkTouch && hasSilkTouch(player.getInventory().getItem(best)) && !hasSilkTouch(player.getInventory().getItem(held))) {
            return best;
        }
        return worthSwitching(heldSpeed, calculateSpeedVsBlock(player.getInventory().getItem(best), blockState)) ? best : held;
    }

    /**
     * Calculate how effectively a block can be destroyed
     *
     * @param b the blockstate to be mined
     * @return A double containing the destruction ticks with the best tool
     */
    private double getBestDestructionTime(Block b) {
        ItemStack stack = player.getInventory().getItem(getBestSlot(b, false, true));
        return calculateSpeedVsBlock(stack, b.defaultBlockState()) * avoidanceMultiplier(b);
    }

    private double avoidanceMultiplier(Block b) {
        return Baritone.settings().blocksToAvoidBreaking.value.contains(b) ? Baritone.settings().avoidBreakingMultiplier.value : 1;
    }

    /**
     * Calculates how long would it take to mine the specified block given the best tool
     * in this toolset is used. A negative value is returned if the specified block is unbreakable.
     *
     * @param item  the item to mine it with
     * @param state the blockstate to be mined
     * @return how long it would take in ticks
     */
    public static double calculateSpeedVsBlock(ItemStack item, BlockState state) {
        float hardness;
        try {
            hardness = state.getDestroySpeed(null, null);
        } catch (NullPointerException npe) {
            // can't easily determine the hardness so treat it as unbreakable
            return -1;
        }
        if (hardness < 0) {
            return -1;
        }

        float speed = item.getDestroySpeed(state);
        if (speed > 1) {
            final ItemEnchantments itemEnchantments = item.getEnchantments();
            OUTER: for (Holder<Enchantment> enchant : itemEnchantments.keySet()) {
                List<EnchantmentAttributeEffect> effects = enchant.value().getEffects(EnchantmentEffectComponents.ATTRIBUTES);
                for (EnchantmentAttributeEffect e : effects) {
                    if (e.attribute().is(Attributes.MINING_EFFICIENCY.unwrapKey().get())) {
                        speed += e.amount().calculate(itemEnchantments.getLevel(enchant));
                        break OUTER;
                    }
                }
            }
        }

        speed /= hardness;
        if (!state.requiresCorrectToolForDrops() || (!item.isEmpty() && item.isCorrectToolForDrops(state))) {
            return speed / 30;
        } else {
            return speed / 100;
        }
    }

    /**
     * Calculates any modifier to breaking time based on status effects.
     *
     * @return a double to scale block breaking speed.
     */
    private double potionAmplifier() {
        double speed = 1;
        if (player.hasEffect(MobEffects.HASTE)) {
            speed *= 1 + (player.getEffect(MobEffects.HASTE).getAmplifier() + 1) * 0.2;
        }
        if (player.hasEffect(MobEffects.MINING_FATIGUE)) {
            switch (player.getEffect(MobEffects.MINING_FATIGUE).getAmplifier()) {
                case 0:
                    speed *= 0.3;
                    break;
                case 1:
                    speed *= 0.09;
                    break;
                case 2:
                    speed *= 0.0027; // you might think that 0.09*0.3 = 0.027 so that should be next, that would make too much sense. it's 0.0027.
                    break;
                default:
                    speed *= 0.00081;
                    break;
            }
        }
        return speed;
    }
}
