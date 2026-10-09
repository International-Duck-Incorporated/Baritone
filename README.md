# Baritone - International Duck Fork

A Minecraft pathfinder bot with humanlike behavior.

This is a fork of [Baritone](https://github.com/cabaletta/baritone) focused on making the bot look like a real player. The pathfinding is unchanged; the camera, timing, movement, idle behavior, and reactions are smoothed, varied, and contextual so a spectator standing next to the bot sees a person, not a machine.

[**Discord Server**](http://discord.gg/s6fRBAUpmr)

## Supported Minecraft Versions

| Branch | Minecraft | Mod Loader | Status |
|--------|-----------|------------|--------|
| `26.1` | 26.1, 26.1.1, 26.1.2 | Fabric | [Release v26.1](https://gitlab.com/international-duck-incorporated/Baritone/-/releases/v26.1) |
| `26.2` | 26.2 | Fabric | [Release v26.2](https://gitlab.com/international-duck-incorporated/Baritone/-/releases/v26.2) |
| `26.3` | 26.3 | Fabric | [Release v26.3](https://gitlab.com/international-duck-incorporated/Baritone/-/releases/v26.3) |

## Quick Start

Install the mod jar for your Minecraft version, then in-game:

```
#goto 1000 500        # Walk to x=1000, z=500
#mine diamond_ore     # Mine diamond ore
#path                 # Path to the current goal
#stop                 # Stop everything
#elytra               # Elytra fly with fireworks
```

For full command reference, see [USAGE.md](USAGE.md).

## What Makes This Fork Look Human

Everything below is the default. No setting turns it on; a bot that has to be told to look alive is a bot that looks dead by default. Nothing changes what the bot does or how well it does it. It changes how the doing looks.

### Camera

- **Eased aim** - The camera accelerates and decelerates, capped at 18/s yaw and 9/s pitch, instead of snapping.
- **Turn tempo** - Every turn draws its own speed (0.85x to 1.15x), so the same corner walked twice is never the same ticks of the same steps.
- **Reaction beat** - 2 to 4 ticks before the aim starts toward a new target. Only while standing still.
- **Overshoot** - The hand goes a few percent past the target and eases back, only while walking.
- **Two-axis sway** - Both yaw and pitch wander a fraction of a degree, smoothed so it drifts rather than shivers.
- **Aim scatter** - The crosshair lands on a point of the block, not the center, different each run.
- **Approach look** - The eyes turn toward the target block over the last 6 blocks of the walk.
- **Gaze lean** - The head leans around the corner before the feet get there.
- **Pitch wander** - The pitch settles a few degrees this way and that, pulled by the slope ahead.
- **Walk glance** - Every 5 to 15 seconds the head turns to the side; now and then the feet stop for it.
- **Flinch** - A blow turns the head at full speed, held for 10 to 30 ticks, tracked if the attacker moves.

### Idle Behavior

- **Post-job sway** - When a job ends, the body keeps swaying, glancing, and noticing until the human arrives.
- **Idle glance** - Every 3 to 8 seconds, bounded to 28 yaw / 7 pitch, one in four looks at the hand.
- **Notice people** - One glance in three goes to the nearest player or creature within 12 blocks.
- **Newcomer detection** - Someone arriving in view is looked at once, a quarter to seven tenths of a second later.
- **Idle fidget** - A swing at the air every 10 to 60 seconds. Animation only, no click.

### Timing

Nothing repeats on a fixed interval:

- **Jittered intervals** - Every repeated interval redraws its length, +/- 1 tick from the setting.
- **Settle beat** - 1 to 2 ticks between the crosshair arriving and the swing.
- **Sprint windup** - 2 to 4 ticks of walking before sprinting on a new run.
- **Key beat** - 1 to 2 ticks of no keys between a direction and its opposite.
- **Hotbar switch** - 2 to 5 ticks between deciding on a slot and pressing it, with no mouse button during.
- **Landing rest** - A jittered beat of standing after a fall. Longer for longer drops.
- **Sprint hangover** - The sprint key trails off over 1 to 3 ticks instead of stopping instantly.

### Movement

- **Key reversal beat** - W becoming S costs 1 to 2 ticks of no keys while the hand repositions.
- **Head leads feet** - The body waits for the head to turn within 60 of the way the feet are going.
- **Bridge crouch** - A crossing is walked crouched from one edge to the other, priced as crouched.
- **Eight-direction keys** - Keys are chosen by angle, not scaled distance, and camera + keys are chosen together.

### Other

- **Elytra auto-swap guard** - The three swap clicks wait for the player's own inventory; half-applied sequences finish.
- **Face trace** - Placement clicks ask the face they need, not the block center.
- **5 counted edges** - Tests that pin which files may write rotation, slot, buttons, clicks, and keys.

Full details: [HUMANLIKE.md](HUMANLIKE.md)

## Installation

See [SETUP.md](SETUP.md) for build-from-source instructions and loader-specific setup.

### Prebuilt Releases

Download from the [Releases](https://gitlab.com/international-duck-incorporated/Baritone/-/releases) page.

- **baritone-api-\*.jar** - Only non-API packages are obfuscated. Use when other mods integrate with Baritone.
- **baritone-standalone-\*.jar** - Everything obfuscated. Extra performance, no mod integration.
- **baritone-unoptimized-\*.jar** - Nothing obfuscated. For crash reports and debugging.

### Build From Source

```bash
git clone https://gitlab.com/international-duck-incorporated/Baritone.git
cd Baritone
git checkout 26.3   # or 26.1, 26.2
./gradlew build
```

Artifacts are in `dist/`.

Requires Java 25. On a box with less than 4 GB RAM, override the JVM args:

```bash
./gradlew build -Dorg.gradle.jvmargs="-Xmx640m -XX:MaxMetaspaceSize=256m -XX:+UseSerialGC" --no-daemon --max-workers=1
```

## API

```java
BaritoneAPI.getSettings().allowSprint.value = true;
BaritoneAPI.getSettings().primaryTimeoutMS.value = 2000L;
BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess()
    .setGoalAndPath(new GoalXZ(10000, 20000));
```

Javadocs: [baritone.leijurv.com](https://baritone.leijurv.com/)

Only the `baritone.api` package is part of the public API. Usage of anything outside it is not supported.

## Settings

All settings are documented at [Settings](https://baritone.leijurv.com/baritone/api/Settings.html#field.detail).

Toggle a boolean setting by saying its name in chat. Set a numeric value by saying its name then the number. Reset all settings with `reset`.

## Wiki

- [Getting Started](wiki/Getting-Started.md)
- [Commands](wiki/Commands.md)
- [Humanlike Features](wiki/Humanlike-Features.md)
- [Settings Reference](wiki/Settings.md)
- [Building from Source](wiki/Building.md)
- [API Guide](wiki/API.md)
- [Contributing](wiki/Contributing.md)

## License

LGPL 3.0 with anime exception. See [LICENSE](LICENSE).

## Credits

Originally by [leijurv](https://github.com/leijurv/). Humanlike behavior by the International Duck team.

YourKit supports open source projects with intelligent tools for monitoring and profiling Java and .NET applications. [YourKit Java Profiler](https://www.yourkit.com/java/profiler/)
