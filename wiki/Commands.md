# Commands

All commands use the `#` prefix by default. In Impact, `.b` also works.

To disable direct chat control (no prefix), turn off `chatControl`. To disable the `#` prefix, turn off `prefixControl`.

## Navigation

| Command | Description |
|---------|-------------|
| `#goto x z` | Walk to x, z coordinates |
| `#goto x y z` | Walk to exact coordinates |
| `#goto y` | Walk to a y level |
| `#goto block_type` | Walk to the nearest block of that type (e.g. `#goto portal`, `#goto ender_chest`) |
| `#goal x z` | Set a goal at coordinates, then `#path` to start |
| `#goal x y z` | Set a goal at exact coordinates |
| `#goal` | Set the goal to your feet |
| `#goal clear` | Clear the current goal |
| `#path` | Start pathing to the current goal |
| `#thisway 1000` | Path 1000 blocks in the direction you're facing, then `#path` |
| `#cancel` / `#stop` | Stop everything |
| `#forcecancel` | Force stop |

## Mining

| Command | Description |
|---------|-------------|
| `#mine diamond_ore` | Mine diamond ore |
| `#mine diamond_ore iron_ore` | Mine multiple ore types |
| `#mine 64 diamond_ore` | Mine 64 blocks of diamond ore |
| `#mine` | Stop mining |

The `legitMine` setting makes Baritone only mine ores it can actually see.

## Building

| Command | Description |
|---------|-------------|
| `#build file.schematic` | Build from `schematics/file.schematic`, origin at your feet |
| `#build file.schematic x y z` | Build at specific coordinates |
| `#build file.schematic ~ 69 ~-420` | Build with relative coordinates |
| `#schematica` | Build the schematic currently open in Schematica |

## Following

| Command | Description |
|---------|-------------|
| `#follow player playerName` | Follow a specific player |
| `#follow players` | Follow any players in range |
| `#follow entities` | Follow any entities |
| `#follow entity pig` | Follow entities of a specific type |

## Selection

| Command | Description |
|---------|-------------|
| `#click` | Click destination on screen. Right click = path on top, left click = path into |
| `#sel` | Selection commands for area operations |

Left click and drag to select an area. Use `#help sel` for selection operations.

## Waypoints

| Command | Description |
|---------|-------------|
| `#wp save user name` | Save a waypoint |
| `#wp goal name` | Set goal to a waypoint |
| `#wp goal death` | List death waypoints |
| `#wp goal home` | Go to your bed |

Waypoint tags: `home` (auto-created on bed click), `death` (auto-created on death), `user` (manual).

## Elytra

| Command | Description |
|---------|-------------|
| `#elytra` | Start elytra flight with fireworks |

Works in the Nether. The bot handles firework timing, altitude, and landing.

## Tunneling

| Command | Description |
|---------|-------------|
| `#tunnel` | Dig a 1x2 tunnel straight ahead |
| `#tunnel 3 2 100` | Clear a 3 high, 2 wide, 100 deep area |

## Settings

| Command | Description |
|---------|-------------|
| `settingName` | Toggle a boolean setting |
| `settingName value` | Set a numeric setting |
| `settingName reset` | Reset a setting to default |
| `reset` | Reset all settings |
| `modified` | Show modified settings |

## Help

| Command | Description |
|---------|-------------|
| `#help` | List all commands (clickable, with tab completion) |
| `#help command` | Get help for a specific command |

## Tips

- Use the `#` prefix to avoid typos going into public chat.
- `#goto portal` finds the nearest portal block.
- `#mine` respects your tool set: it picks the best tool and considers break time.
- Baritone handles bridging, parkour jumps, falling (with water bucket), and ladder/vine climbing automatically.
- The bot looks like a real player while doing all of this. See [Humanlike Features](Humanlike-Features.md).
