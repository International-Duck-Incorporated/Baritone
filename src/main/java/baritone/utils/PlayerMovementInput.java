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

import baritone.api.utils.input.Input;
import baritone.pathing.movement.KeyBeat;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;

public class PlayerMovementInput extends ClientInput {

    private final InputOverrideHandler handler;

    PlayerMovementInput(InputOverrideHandler handler) {
        this.handler = handler;
    }

    @Override
    public void tick() {
        float leftImpulse = 0.0F;
        float forwardImpulse = 0.0F;
        boolean jumping = handler.isInputForcedDown(Input.JUMP); // oppa gangnam style

        // The four keys through the hand, not straight from the movement: a direction that reverses gets a
        // beat of nothing in between, the way a person lets go and finds the other key. See KeyBeat.
        final KeyBeat.Keys walking = handler.beatTheKeys(new KeyBeat.Keys(
                handler.isInputForcedDown(Input.MOVE_FORWARD),
                handler.isInputForcedDown(Input.MOVE_BACK),
                handler.isInputForcedDown(Input.MOVE_LEFT),
                handler.isInputForcedDown(Input.MOVE_RIGHT)));

        boolean up = walking.forward();
        if (up) {
            forwardImpulse++;
        }

        boolean down = walking.back();
        if (down) {
            forwardImpulse--;
        }

        boolean left = walking.left();
        if (left) {
            leftImpulse++;
        }

        boolean right = walking.right();
        if (right) {
            leftImpulse--;
        }

        boolean sneaking = handler.isInputForcedDown(Input.SNEAK);

        // Not scaled down here on purpose. This used to multiply the impulses by 0.3, which is what the key
        // handler did when this class was written; the game no longer scales crouching into the impulse.
        // LocalPlayer.modifyInput still scales every crouched move vector, by the player's SNEAKING_SPEED
        // attribute - 0.3 at its base value, and up to 0.75 with Swift Sneak - and forcing SNEAK is what
        // makes the player crouch, since crouching is computed from isShiftKeyDown, which reads the
        // keyPresses.shift() set below. Scaling here as well meant crouching twice: a sneaking walk went at
        // 0.09 of walk speed, and the attribute that is supposed to decide the factor only ever scaled
        // that. The impulses stay at full strength and the game applies the attribute once.
        this.moveVector = new Vec2(leftImpulse, forwardImpulse);

        boolean sprinting = handler.isInputForcedDown(Input.SPRINT);

        this.keyPresses = new net.minecraft.world.entity.player.Input(up, down, left, right, jumping, sneaking, sprinting);
    }
}
