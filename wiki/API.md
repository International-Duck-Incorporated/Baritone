# API Guide

Baritone has a public API in the `baritone.api` package. Usage of anything outside this package is not supported.

Javadocs: [baritone.leijurv.com](https://baritone.leijurv.com/)

## Basic Usage

```java
import baritone.api.BaritoneAPI;
import baritone.api.utils.GoalXZ;

// Enable sprinting
BaritoneAPI.getSettings().allowSprint.value = true;

// Set pathfinding timeout
BaritoneAPI.getSettings().primaryTimeoutMS.value = 2000L;

// Path to coordinates
BaritoneAPI.getProvider().getPrimaryBaritone()
    .getCustomGoalProcess()
    .setGoalAndPath(new GoalXZ(10000, 20000));
```

## Goals

| Goal | Description |
|------|-------------|
| `GoalBlock(x, y, z)` | Stand at exact position |
| `GoalXZ(x, z)` | Walk to x, z (y ignored) |
| `GoalYLevel(y)` | Reach a y level |
| `GoalTwoBlocks(x, y, z)` | Stand at foot or eye level |
| `GoalGetToBlock(x, y, z)` | Stand adjacent to, below, or on top of |
| `GoalNear(x, y, z, radius)` | Get within radius |
| `GoalAxis` | Get to an axis or diagonal |
| `GoalComposite(goals)` | Any one of a list of goals |

## Processes

Baritone has several processes that can be accessed:

```java
IBaritone baritone = BaritoneAPI.getProvider().getPrimaryBaritone();

// Pathfinding
baritone.getCustomGoalProcess().setGoalAndPath(goal);
baritone.getCustomGoalProcess().setGoal(goal);  // set without pathing
baritone.getPathingBehavior().cancelEverything();

// Mining
baritone.getMineProcess().mine("diamond_ore");
baritone.getMineProcess().mine(64, "diamond_ore");

// Building
baritone.getBuilderProcess().build("file.schematic", origin);

// Following
baritone.getFollowProcess().follow("playerName");
```

## Events

Subscribe to events:

```java
BaritoneAPI.getProvider().getPrimaryBaritone().getGameEventHandler()
    .registerEventListener(new IGameEventListener() {
        @Override
        public void onTick(TickEvent event) {
            // Called every tick
        }

        @Override
        public void onPlayerInteract(PlayerInteractEvent event) {
            // Called on player interaction
        }

        @Override
        public void onSendChatMessage(ChatEvent event) {
            // Called when a chat message is sent
        }
    });
```

## Multiple Instances

Each world gets its own Baritone instance:

```java
IBaritone primary = BaritoneAPI.getProvider().getPrimaryBaritone();
// Or get by player context
IBaritone baritone = BaritoneAPI.getProvider().getBaritoneForPlayer(player);
```

## Pathing

```java
// Get current path
IPath path = baritone.getPathingBehavior().getPath();

// Check if pathing
boolean isPathing = baritone.getPathingBehavior().isPathing();

// Get path calculation progress
PathCalculationResult result = baritone.getPathingBehavior().getPathfinder().calculate();
```

## Settings

All settings are fields on the `Settings` object:

```java
Settings settings = BaritoneAPI.getSettings();

// Boolean settings
settings.allowSprint.value = true;
settings.allowBreak.value = false;

// Numeric settings
settings.primaryTimeoutMS.value = 2000L;

// List settings (like acceptableThrowawayItems)
settings.acceptableThrowawayItems.value.add(Items.STONE);
```

## Mod Integration

If you are integrating Baritone into your mod, use the `baritone-api-*.jar` artifact. This keeps the API classes unobfuscated while obfuscating internals for performance.

Key interfaces:
- `IBaritone` - Main entry point
- `IGoalProcess` - Goal/path management
- `IMineProcess` - Mining
- `IBuilderProcess` - Building
- `IFollowProcess` - Following
- `IPathingBehavior` - Path control
- `Settings` - All settings
- `IGameEventListener` - Event hooks

## Notes

- The API is the supported interface. Anything outside `baritone.api` may change without notice.
- The humanlike behavior is internal and not exposed through the API. It is always on.
- Pathfinding quality is not affected by the humanlike features. The same A* algorithm runs.
