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

package baritone.api.utils;

import baritone.api.BaritoneAPI;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/**
 * @author Brady
 * @since 12/14/2018
 */
public interface IPlayerController {

    void syncHeldItem();

    boolean hasBrokenBlock();

    boolean onPlayerDamageBlock(BlockPos pos, Direction side);

    void resetBlockRemoving();

    void windowClick(int windowId, int slotId, int mouseButton, ContainerInput type, Player player);

    GameType getGameType();

    InteractionResult processRightClickBlock(LocalPlayer player, Level world, InteractionHand hand, BlockHitResult result);

    InteractionResult processRightClick(LocalPlayer player, Level world, InteractionHand hand);

    /**
     * Swing at an entity, the way a left click aims at one rather than at a block.
     * <p>
     * It is here rather than reached for directly for the same reason the block clicks are: the game's own
     * controller is called from one wrapper and nowhere else, so that everything above it can be answered
     * without a game. Defence needs the click and neither helper can express it - a click on an entity has
     * no block under the crosshair, which is {@link #processRightClick}'s whole condition and
     * {@code BlockPlaceHelper}'s, and no block to break, which is {@code BlockBreakHelper}'s. Eating is the
     * other click in the fork that is not aimed at a block and it is here for the same reason.
     */
    void attackEntity(Player player, Entity target);

    boolean clickBlock(BlockPos loc, Direction face);

    void setHittingBlock(boolean hittingBlock);

    default double getBlockReachDistance() {
        return this.getGameType().isCreative() ? 5.0F : BaritoneAPI.getSettings().blockReachDistance.value;
    }
}
