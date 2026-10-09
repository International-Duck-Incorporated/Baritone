# Settings Reference

All settings are documented in the [Javadocs](https://baritone.leijurv.com/baritone/api/Settings.html#field.detail).

> This page is a summary of a subset, and the defaults in it are copied from
> `src/api/java/baritone/api/Settings.java`, which is the source of truth. `SettingsWikiTest` checks every
> row below against that file, so a default that drifts fails the build rather than going quietly wrong.

## Changing Settings

In-game, open chat and type the setting name:

```
allowBreak              # Toggle block breaking on/off
allowSprint             # Toggle sprinting on/off
primaryTimeoutMS 250    # Set pathfinding timeout to 250ms
acceptableThrowawayItems reset  # Reset to default
reset                   # Reset all settings
modified                # Show settings changed from default
```

Settings are saved to `minecraft/baritone/settings.txt`. Delete this file to reset everything.

## Important Settings

### Movement

| Setting | Default | Description |
|---------|---------|-------------|
| `allowSprint` | true | Allow sprinting |
| `allowBreak` | true | Allow breaking blocks as part of pathing |
| `allowPlace` | true | Allow placing blocks as part of pathing |
| `allowParkour` | false | Allow sprint jump parkour |
| `allowParkourPlace` | false | Allow placing blocks mid-parkour jump |
| `allowWaterBucketFall` | true | Use water bucket for long falls |
| `allowVines` | false | Allow climbing and traversing vines |
| `allowDiagonalAscend` | false | Allow diagonal ascending movements |
| `assumeSafeWalk` | false | Assume safe walk (no crouch on bridges) |
| `sprintInWater` | true | Sprint while in water |

### Pathfinding

| Setting | Default | Description |
|---------|---------|-------------|
| `primaryTimeoutMS` | 500 | Primary pathfinding timeout in ms |
| `failureTimeoutMS` | 2000 | Failure timeout in ms |
| `planAheadPrimaryTimeoutMS` | 4000 | Timeout for planning next segment |
| `planAheadFailureTimeoutMS` | 5000 | Failure timeout for planning ahead |
| `movementTimeoutTicks` | 100 | Ticks to wait for a movement to complete (five seconds at 20 tps) |
| `pathingMaxChunkBorderFetch` | 50 | Max chunks to fetch for border pathing |

### Mining

| Setting | Default | Description |
|---------|---------|-------------|
| `legitMine` | false | Only mine ores you can actually see |
| `legitMineYLevel` | -59 | Y level for legit mining |
| `mineGoalUpdateInterval` | 5 | Ticks between mine goal rescans |
| `acceptableThrowawayItems` | dirt, cobblestone, netherrack, stone | Items for placing |

### Building

| Setting | Default | Description |
|---------|---------|-------------|
| `buildInLayers` | false | Build schematic layer by layer |
| `buildRepeat` | `0,0,0` | Offset to move before repeating the build; `0` disables one axis, `0,0,0` disables repeating entirely |
| `buildRepeatCount` | -1 | Number of times to repeat (-1 = infinite) |
| `buildRepeatSneaky` | true | Repeat by replacing the same spots each repetition (backwards compatibility) |

### Chat

| Setting | Default | Description |
|---------|---------|-------------|
| `chatControl` | true | Allow commands without prefix |
| `chatDebug` | false | Print debug info in chat |
| `prefixControl` | true | Allow commands with `#` prefix |

### Rendering

| Setting | Default | Description |
|---------|---------|-------------|
| `renderPath` | true | Render the current path |
| `renderGoal` | true | Render the current goal |
| `renderSelectionBoxes` | true | Render selection boxes |
| `renderCachedChunks` | false | Render cached chunks |

### Humanlike

The humanlike features are always on. There are no settings to toggle them. A bot that has to be told to look alive is a bot that looks dead by default.

The only humanlike-adjacent settings are:

| Setting | Default | Description |
|---------|---------|-------------|
| `elytraAutoSwap` | true | Auto-swap elytra when durability is low |
| `elytraMinimumDurability` | 5 | Minimum durability before the auto-swap asks |

## Tips

- `primaryTimeoutMS` is the most impactful setting. Lower = faster but less optimal paths. Higher = slower but better paths. It defaults to 500 ms, so "raise it for better paths" means raising it from 500, not from a larger figure.
- `allowParkour` can find shortcuts but may occasionally fail jumps.
- `legitMine` makes mining look more natural but is slower.
- `assumeSafeWalk` disables the bridge crouch but may cause falls.
