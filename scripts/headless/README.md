# A Minecraft client with no screen

Runs Minecraft 26.3 with this checkout's Baritone in it, on a machine with no display and no GPU, so a change can be
walked in a real game instead of only being read. It exists because the first walk in one found a crash that every
unit test had missed (`PathExecutorBoundsTest` is the record of it).

What is in it: Xvfb for the display, Mesa's software Vulkan (lavapipe) for the renderer, the vanilla client, Fabric
Loader, and `fabric/build/libs/*-dev-shadow.jar`. Nothing here touches the build or the product.

## What it needs

- Debian or Ubuntu packages: `xvfb libgl1 libgl1-mesa-dri libglx-mesa0 libegl1 mesa-vulkan-drivers libvulkan1 xdotool
  imagemagick libxrandr2 libxcursor1 libxxf86vm1 libxi6 libxrender1 libxext6 libxtst6 libxinerama1 libxkbcommon0`
- Java 25 (`JAVA=` if it is not the `java` on the path), and Python 3.
- Measured on 2 vCPU, 1.9 GB, no swap, Debian 13: with `-Xmx640m` the client is **1.25 to 1.3 GB resident**, runs at
  about 13 fps (render distance 2, software Vulkan), and the integrated server spends **2.5 of its 50 ms** a tick.
  A Gradle build (about 1 GB) does not fit beside it: build, stop Gradle, then start the client. `run.sh` refuses to
  start while Gradle is alive.

## The steps

```
python3 scripts/headless/fetch.py              # 227 MB, about 4 s; versions come from gradle.properties
./gradlew :fabric:shadowJar                    # 25-30 s warm; no client running
scripts/headless/run.sh                        # in the background; the log is $BARITONE_HEADLESS/client.log
                                               # about 25 s to the title screen
scripts/headless/drive.sh newworld             # once: a superflat world, commands on, peaceful
scripts/headless/drive.sh chat '#goto ~30 ~ ~20'
scripts/headless/drive.sh key F3               # the debug overlay: position, facing, ms per tick
scripts/headless/drive.sh shot /tmp/a.png 60   # the game window, scaled to 60%
grep CHAT "$BARITONE_HEADLESS/client.log"      # what Baritone and the game said
```

`BARITONE_HEADLESS` is where everything lives (default `~/baritone-headless`). Later starts enter the saved world at
once. The default window is 854x480 on a 1024x600 screen; `drive.sh` clicks at coordinates for exactly that.

## The smoke test

`smoke.sh` is what this harness found, kept as a check. Against a client that is up it walks a 36-block `#goto` to its
end, then leaves the world with the elytra process holding a live behaviour, and fails (exit 1) if the log from there on
holds the game's catch-all for an exception thrown from the tick (`Unreported exception thrown`, `Game crashed`). Exit 2
means the elytra process never took control, so the way out proved nothing. It takes about a minute; run it before
landing a change to how the bot walks, looks, or lets go of a process. It found two crashes the unit tests had passed:
the path executor's look-aheads reading one movement past the end of a path, and the elytra teardown reading a player
who is not there on the way out of a world.

## The scenes

`scenes.sh` runs six scenes in the real game - `stairs` (a two-high bump, up and down), `bridge` (a 4-wide, 3-deep
trench), `parkour` (a 3-wide one, with `allowParkour`), `mine` (6 of 27 iron ore), `farm` (24 ripe wheat round a water
source), `explore` - about 7 minutes in all, or any of them by name. Each teleports to a spot of its own, builds what it
needs with `/fill`, runs one Baritone command, and asserts what the game itself reports: arrival is a
`/execute positioned X Y Z if entity @s[distance=..2.5] run say TOKEN` and a count is the number `/fill ... replace`
prints, so nothing is read off a screenshot. Every scene also fails on `Unreported exception thrown` in the log.
It records two pitfalls that made scenes fail for the scene's own reasons: state leaks between runs (six raw iron
already in the inventory ends `#mine 6` before it begins, so the scene clears it), and `#farm 16` measures its range
from where it is told (a scene that starts 14 blocks from the field's centre leaves the far corner out of reach).

## The per-tick trace

`drive.sh trace on` writes a datapack into the world whose tick function tells the player, once a game tick,
`tick|[x, y, z]|[yaw, pitch]`; the lines land in the log as `[CHAT]` lines, and `trace.py LOG FROM TO` reads the ones
between two log line numbers (`wc -l` on the log before and after) into a summary, or `--csv`. It is `tellraw` with
`nbt` components and not `data get`, because the output of a tick function is suppressed. `drive.sh trace off`
stops it; the lines are twenty a second and the chat fills with them.

Read it with one thing in mind: a client below 20 frames a second runs its ticks in bursts, so two movement packets
reach the server in one tick and none in the next. A per-tick speed reads 0.56, 0.28, 0.00 for a walk at 0.28 blocks a
tick, so `trace.py` takes speed over windows of six ticks and reports the median over the middle of a walk as the
cruise speed. Yaw and pitch are not doubled by a burst and are read as they are.

## What cost a run each, so it need not again

- **OpenGL does not come up under Xvfb** ("Couldn't find matching GLX visual"). The client falls back to Vulkan by
  itself; the log says `Using graphics backend Vulkan ... llvmpipe`.
- **The title screen needs the asset objects**: its panorama is an asset, not part of the jar, and a client without
  them crashes at the first reload. `fetch.py` takes every object except the sounds.
- **The first-run screen** ("Would you like to enable the Narrator") takes the first clicks. `run.sh` sets
  `onboardAccessibility:false`.
- **A graphics preset puts its own distances back at every start.** A game-written `options.txt` says
  `graphicsPreset:"fancy"`, which means render distance 16 whatever the file says: the heap fills, the client drops to
  8 fps and a walk never finishes. `run.sh` pins `graphicsPreset:"custom"`, render distance 2, simulation distance 5.
- **Slimes spawn on a superflat surface by day** and killed the player 40 seconds into the first run. `newworld` sets
  peaceful. A player who dies stays dead in the save, and the next start opens on the death screen:
  `drive.sh respawn`.
- **A crash in the tick** logs `Unreported exception thrown!`, writes `game/crash-reports/`, and may leave the
  process alive with a black screen. Count `Unreported`, `IndexOutOfBounds`, `NoClassDefFound`, `MixinApply` in
  the log after every run.
- **`T: 20` on the first line of F3 is the frame cap** (`maxFps`), not the tick rate. The server's cost is the
  `Integrated server @ x/50.0 ms` line on the right of the overlay.
- The shadow jar does not carry `nether-pathfinder` (only `remapJar` nests it) and Baritone asks for it at start-up,
  so `run.sh` adds it from the Gradle cache; `fabric/build/libs` can hold several shadow jars, and it takes the newest.

## What it can and cannot see

It shows that the mod loads, that commands run, that a path can be walked to its end, mined, bridged, jumped and
farmed, and what the player's position and rotation did tick by tick. It has **no record of the keys**: which of W, S,
sprint, sneak, jump and the buttons were down, and when, is out of its reach, and so is anything that needs the
client's own ticks rather than the server's view of them. Rendering is software, so frame timing says nothing about a
real machine; tick timing does.
