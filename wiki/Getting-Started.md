# Getting Started

## Installation

### Prebuilt Releases

Download the mod jar for your Minecraft version from the [Releases](https://gitlab.com/international-duck-incorporated/Baritone/-/releases) page.

| Branch | Minecraft | Release |
|--------|-----------|---------|
| `26.1` | 26.1, 26.1.1, 26.1.2 | [v26.1](https://gitlab.com/international-duck-incorporated/Baritone/-/releases/v26.1) |
| `26.2` | 26.2 | [v26.2](https://gitlab.com/international-duck-incorporated/Baritone/-/releases/v26.2) |
| `26.3` | 26.3 | [v26.3](https://gitlab.com/international-duck-incorporated/Baritone/-/releases/v26.3) |

Drop the jar into your `mods/` folder. That's it.

### Which Jar?

Pick the jar for your loader - `fabric`, `forge`, `neoforge` or `tweaker` - and take the one with no
classifier suffix, which is the remapped jar a loader actually loads. The `-dev-shadow` jar has its
dependencies shadowed in but is not remapped, and the `-dev` jar is the plain compiled one for debugging;
neither is what goes in `mods/`.

The names upstream Baritone used - `baritone-api`, `baritone-standalone`, `baritone-unoptimized` - are not
built by this fork, so a release will not have them. See `wiki/Building.md` for the tasks and classifiers
each loader declares.

### Build From Source

```bash
git clone https://gitlab.com/international-duck-incorporated/Baritone.git
cd Baritone
git checkout 26.3
./gradlew build
```

Artifacts are in `dist/`. See [Building](Building.md) for memory-constrained builds.

## First Commands

Once in-game, open chat and type:

```
#goto 1000 500
```

This walks you to x=1000, z=500. That's it. Baritone is now working.

### Common Commands

| Command | What it does |
|---------|-------------|
| `#goto x z` | Walk to coordinates |
| `#goto x y z` | Walk to exact coordinates |
| `#mine diamond_ore` | Mine diamond ore |
| `#mine 64 diamond_ore` | Mine 64 diamond ore blocks |
| `#path` | Start pathing to the current goal |
| `#goal x z` | Set a goal without starting |
| `#goal clear` | Clear the goal |
| `#stop` | Stop everything |
| `#forcecancel` | Force stop |
| `#follow player Name` | Follow a player |
| `#click` | Click destination on screen |
| `#elytra` | Elytra fly with fireworks |
| `#build file.schematic` | Build a schematic |
| `#tunnel` | Dig a 1x2 tunnel |

### Settings

Toggle a boolean setting by saying its name:

```
allowBreak        # Toggle block breaking
allowSprint       # Toggle sprinting
chatControl       # Toggle direct chat control
```

Set a numeric value:

```
primaryTimeoutMS 250
```

Reset everything:

```
reset
```

See modified settings:

```
modified
```

## What Happens Next

Baritone handles everything: pathfinding, block breaking, block placing, sprint timing, tool switching, falling, bridging, parkour, and elytra flight. It also handles how it looks to other players: eased camera turns, natural timing, idle behavior, reactions to being hit, and more. See [Humanlike Features](Humanlike-Features.md) for details.

For the full command reference, see [Commands](Commands.md).
