# API Guide

Baritone has a public API in the `baritone.api` package. Usage of anything outside this package is not supported.

Javadocs: [baritone.leijurv.com](https://baritone.leijurv.com/)

> The examples here are copied against `src/api/java/baritone/api/`, which is the source of truth. Nothing
> compiles them yet, so a signature that moves does not fail the build - check them against the interfaces
> before relying on one.

## Basic Usage

```java
import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalXZ;   // the goals live in pathing.goals, not utils

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

// Mining - the target is a BlockOptionalMeta, not a String; there is no String overload.
// A BlockOptionalMeta can be built from a selector string, which is what #mine parses into.
baritone.getMineProcess().mine(new BlockOptionalMeta("diamond_ore"));
baritone.getMineProcess().mine(64, new BlockOptionalMeta("diamond_ore"));

// Building
baritone.getBuilderProcess().build("file.schematic", origin);

// Following - the filter is a Predicate<Entity>; there is no String overload.
baritone.getFollowProcess().follow(entity -> entity instanceof Player
        && entity.getName().getString().equalsIgnoreCase("playerName"));
```

## Events

Subscribe to events:

`IGameEventListener` is an interface with sixteen abstract methods and the fork ships no adapter, so an
anonymous listener has to implement all sixteen - the example below overrides three and is only valid with
the other thirteen filled in (empty bodies are enough). It lives in `baritone.api.event.listener`, and the
interaction callback is `onBlockInteract(BlockInteractEvent)`; there is no `onPlayerInteract`.

```java
BaritoneAPI.getProvider().getPrimaryBaritone().getGameEventHandler()
    .registerEventListener(new IGameEventListener() {
        @Override
        public void onTick(TickEvent event) {
            // Called every tick
        }

        @Override
        public void onBlockInteract(BlockInteractEvent event) {
            // Called on block interaction
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
// Get current path - Optional, because there is no path before the first calculation lands
Optional<IPath> path = baritone.getPathingBehavior().getPath();

// Check if pathing
boolean isPathing = baritone.getPathingBehavior().isPathing();
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

This fork does not build a separate api artifact; `baritone-api-*.jar` is upstream's naming and does not
appear in any `build.gradle` here. Depend on the loader jar for your loader and code against
`baritone.api`, which is the package this page documents and the only one that counts as supported.

Key interfaces:
- `IBaritone` - Main entry point
- `ICustomGoalProcess` - Goal/path management (there is no `IGoalProcess`)
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
