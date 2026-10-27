# Playtest Notes

## Issues Observed

1. **Player doesn't look forwards when walking** - The camera/aim doesn't point forward during normal walking movement. The bot's head/rotation doesn't align with the movement direction while walking. The gaze should lead the walk direction, not stay at whatever angle was last set.

2. **Camera is very jittery when mining** - When the bot is mining/breaking blocks, the camera shakes/jitters noticeably. This is likely related to the rapid target changes between blocks as the mining process picks new blocks and the look machinery re-aims every tick. The ReactionDelay hold (2-4 ticks) should smooth this but may not be sufficient.

3. **Simple GUI to configure things like block placing** - Need a simple in-game GUI to configure settings like block placing (allowPlace, etc.) without typing commands. Would make configuration much easier during playtesting and general use.

4. **Camera swings side to side when placing blocks** - When the bot is placing blocks (building), the camera swings left and right repeatedly. This is likely related to the placement process rapidly switching between target blocks, causing the aim to oscillate between positions.

## Features Tested

- `#help` - Works, shows command list
- `#goto` - Works, paths to goal block. Camera doesn't look forward while walking (issue #1)
- `#mine` - Works (mined 5 grass_block). Camera jittery during mining (issue #2)
- `#build` - Requires schematic file; not tested with actual schematic
- `#explore` - Works, explores from current position
- `#farm` - Failed (no farmland/crops in test area)
- `#tunnel` - Works, digs in a direction
- `#proc` - Works, shows process state
- `#cancel` - Works
- `#set` - Works (tested autoEat, allowPlace)
- Auto-eat - Enabled (autoEat=true). Tested with hunger effect (amplifier 10); food depletion observed but auto-eat trigger not clearly seen in log. Bread in inventory.
- Auto-defend - Not tested (peaceful mode, no hostile mobs)

## Context
- Instance: "workspace for opencode" (Minecraft 26.2, Fabric Loader 0.19.5)
- Build: 26.2-102-g43436f1f-dirty
- Game mode: Survival
- Difficulty: Easy
- Test world: New World (superflat-ish, birch forest area at ~0, 70, 0)
- Screenshots: .playtest/shot-*.png (22+ screenshots taken)