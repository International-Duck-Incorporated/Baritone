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
    private int rightClickTimer;

    /**
     * Whether the previous tick was also asked to place, which is how the rising edge - the moment the
     * crosshair has just arrived on a face there is something to do with - is recognised. See
     * {@link Cadence#sightSettle}.
     */
    private boolean wasClicking;

    /**
     * Counts down the beat between arriving on a face and clicking it. See {@link Cadence#sightSettle}.
     */
    private int sightTimer;

    BlockPlaceHelper(IPlayerContext playerContext) {
        this.ctx = playerContext;
    }

    public void tick(boolean rightClickRequested) {
        if (!rightClickRequested) {
            // Nothing is being asked for, so there is nothing to have arrived on.
            this.wasClicking = false;
            this.sightTimer = 0;
        } else if (!this.wasClicking && ctx.player().onGround()) {
            // The first tick of a request. The callers only set this once the trace is on the face they
            // mean to use, so this is the tick the crosshair arrived, and the click comes a beat later -
            // the same beat the other mouse button is given. While airborne it is not spent: a block placed
            // under a jump is timed against the arc the player is already on, and a hand that waits there
            // lands the block after the foot has gone past it.
            this.sightTimer = Cadence.sightSettle(this.placeRand);
        }
        this.wasClicking = rightClickRequested;
        if (this.sightTimer > 0) {
            this.sightTimer--;
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
                return;
            }
        }
    }
}
