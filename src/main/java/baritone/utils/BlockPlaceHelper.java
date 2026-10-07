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
import baritone.api.utils.IPlayerContext;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Random;

public class BlockPlaceHelper {
    // base ticks between places caused by tick logic
    private static final int BASE_PLACE_DELAY = 1;

    private final IPlayerContext ctx;
    private final Random placeRand = new Random();
    private final Cadence.Settle sightSettle = new Cadence.Settle(this.placeRand);
    private int rightClickTimer;

    BlockPlaceHelper(IPlayerContext playerContext) {
        this.ctx = playerContext;
    }

    public void tick(boolean rightClickRequested) {
        // The same beat the pickaxe gets, between arriving on a face and clicking it, and not spent while
        // the player is off the ground. See Cadence.Settle: MovementParkour places a block to land on.
        if (this.sightSettle.waiting(rightClickRequested, ctx.player().onGround())) {
            return;
        }
        if (rightClickTimer > 0) {
            rightClickTimer--;
            return;
        }
        HitResult mouseOver = ctx.objectMouseOver();
        if (!rightClickRequested || ctx.player().isHandsBusy() || mouseOver == null || mouseOver.getType() != HitResult.Type.BLOCK) {
            return;
        }
        // not exactly rightClickSpeed ticks apart, every time: see Cadence.jitter
        rightClickTimer = Cadence.jitter(this.placeRand,
                Baritone.settings().rightClickSpeed.value - BASE_PLACE_DELAY);
        for (InteractionHand hand : InteractionHand.values()) {
            if (ctx.playerController().processRightClickBlock(ctx.player(), ctx.world(), hand, (BlockHitResult) mouseOver) == InteractionResult.SUCCESS) {
                ctx.player().swing(hand, SwingAnimation.DEFAULT, false);
                return;
            }
            if (!ctx.player().getItemInHand(hand).isEmpty() && ctx.playerController().processRightClick(ctx.player(), ctx.world(), hand) == InteractionResult.SUCCESS) {
                // And vanilla swings on this path too. A bucket, a firework, a flint and steel - an item being
                // used is an arm, the same as a block being placed is, and it is the arm that carries the
                // gesture. The swing only animates the hand the player is watching; the server broadcasts one
                // for a successful use by itself, so nothing else is needed here.
                ctx.player().swing(hand, SwingAnimation.DEFAULT, false);
                return;
            }
        }
    }
}
