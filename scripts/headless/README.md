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

It shows that the mod loads (every mixin applies), that commands run, and that a path can be walked to its end,
by position, facing and screenshots. It has **no per-tick record**: nothing here logs keys, yaw or pitch per tick, so
a rhythm or an angle over time is out of its reach until something writes one. Rendering is software, so frame
timing says nothing about a real machine; tick timing does.
