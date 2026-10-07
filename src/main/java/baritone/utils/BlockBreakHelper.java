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

import baritone.api.BaritoneAPI;
import baritone.api.utils.IPlayerContext;
import baritone.utils.accessor.IPlayerControllerMP;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Random;

/**
 * @author Brady
 * @since 8/25/2018
 */
public final class BlockBreakHelper {
    // base ticks between block breaks caused by tick logic
    private static final int BASE_BREAK_DELAY = 1;

    private final IPlayerContext ctx;
    private final Random retargetRand = new Random();
    private final Cadence.Settle sightSettle = new Cadence.Settle(this.retargetRand);
    private boolean wasHitting;
    private int breakDelayTimer = 0;

    BlockBreakHelper(IPlayerContext ctx) {
        this.ctx = ctx;
    }

    public void stopBreakingBlock() {
        // The player controller will never be null, but the player can be
        if (ctx.player() != null && wasHitting) {
            ctx.playerController().setHittingBlock(false);
            ctx.playerController().resetBlockRemoving();
            wasHitting = false;
        }
    }

    /**
     * How long to pause after an instant break before starting the next block. See {@link Cadence}.
     */
    int retargetDelay() {
        return Cadence.retargetDelay(this.retargetRand);
    }

    /**
     * The same for a break that took time: {@code blockBreakSpeed} is how long the next block is left
     * alone for, and leaving it alone for exactly that long, every block, is the metronome again.
     */
    int breakDelay() {
        return Cadence.jitter(this.retargetRand,
                BaritoneAPI.getSettings().blockBreakSpeed.value - BASE_BREAK_DELAY);
    }

    /**
     * Swings the arm at the block being broken, and tells the server about it.
     * <p>
     * The swing on its own animates the hand locally and nothing else: the server is what shows the arm to
     * the players around us, and it learns about a swing from the punch packet, not from the swing. Vanilla
     * sends one on both of the paths this class mirrors - the tick a block is started on, and every tick it
     * keeps being hit - so a bot without it breaks blocks in front of an arm that never moves, which is the
     * one part of a person that no amount of camera work can stand in for.
     */
    private void swingArm() {
        ctx.player().swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT, false);
        ctx.player().connection.send(ServerboundPunchPacket.INSTANCE);
    }

    public void tick(boolean isLeftClick) {
        // The callers only ask for this once the trace lands on the block, so the tick the request starts is
        // the tick the crosshair arrived, and the swing comes a beat after that rather than in the same
        // tick. A run of blocks clicked without the crosshair ever leaving a face pays the beat once.
        if (this.sightSettle.waiting(isLeftClick, true)) {
            return;
        }
        if (breakDelayTimer > 0) {
            breakDelayTimer--;
            return;
        }
        HitResult trace = ctx.objectMouseOver();
        boolean isBlockTrace = trace != null && trace.getType() == HitResult.Type.BLOCK;

        if (isLeftClick && isBlockTrace) {
            ctx.playerController().setHittingBlock(wasHitting);
            if (ctx.playerController().hasBrokenBlock()) {
                ctx.playerController().syncHeldItem();
                ctx.playerController().clickBlock(((BlockHitResult) trace).getBlockPos(), ((BlockHitResult) trace).getDirection());
                this.swingArm();
                // and then take a beat before the next one, rather than chaining them tick after tick
                breakDelayTimer = Math.max(breakDelayTimer, this.retargetDelay());
            } else {
                if (ctx.playerController().onPlayerDamageBlock(((BlockHitResult) trace).getBlockPos(), ((BlockHitResult) trace).getDirection())) {
                    this.swingArm();
                }
                if (ctx.playerController().hasBrokenBlock()) { // block broken this tick
                    // break delay timer only applies for multi-tick block breaks like vanilla
                    breakDelayTimer = this.breakDelay();
                    // must reset controller's destroy delay to prevent the client from delaying itself unnecessarily
                    ((IPlayerControllerMP) ctx.minecraft().gameMode).setDestroyDelay(0);
                }
            }
            // if true, we're breaking a block. if false, we broke the block this tick
            wasHitting = !ctx.playerController().hasBrokenBlock();
            // this value will be reset by the MC client handling mouse keys
            // since we're not spoofing the click keybind to the client, the client will stop the break if isDestroyingBlock is true
            // we store and restore this value on the next tick to determine if we're breaking a block
            ctx.playerController().setHittingBlock(false);
        } else {
            wasHitting = false;
        }
    }
}
