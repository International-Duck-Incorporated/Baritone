#!/bin/bash
# Starts the Fabric client with the Baritone mod you built, with no screen: Xvfb for the display and Mesa's
# software Vulkan (lavapipe) for the renderer. OpenGL does not come up under Xvfb ("Couldn't find matching GLX
# visual"); the client falls back to Vulkan by itself, so nothing here has to ask for it.
#
# Needs: fetch.py already run, ./gradlew :fabric:shadowJar already built, and NO Gradle alive - on a 2 GB box
# the client (about 1.3 GB resident) and a Gradle build (about 1 GB) do not fit together.
#
# usage: run.sh [extra game arguments]        the log is $BARITONE_HEADLESS/client.log
# environment: BARITONE_HEADLESS (default ~/baritone-headless), HEADLESS_DISPLAY (:99), HEADLESS_WORLD
#   ("New World": entered at once if it exists), HEADLESS_HEAP (640m), HEADLESS_USER, JAVA (java, 25 or later)
set -eu
REPO="$(cd "$(dirname "$0")/../.." && pwd)"
MC_DIR="${BARITONE_HEADLESS:-$HOME/baritone-headless}"
DISPLAY_NAME="${HEADLESS_DISPLAY:-:99}"
WORLD="${HEADLESS_WORLD:-New World}"
JAVA="${JAVA:-java}"

for f in classpath.txt fabric-classpath.txt asset-index.txt minecraft-version.txt; do
    [ -f "$MC_DIR/$f" ] || { echo "run.sh: $MC_DIR/$f is missing - run scripts/headless/fetch.py first" >&2; exit 1; }
done
MOD="$(ls -t "$REPO"/fabric/build/libs/*-dev-shadow.jar 2>/dev/null | head -1 || true)"
[ -n "$MOD" ] || { echo "run.sh: no fabric/build/libs/*-dev-shadow.jar - run ./gradlew :fabric:shadowJar first" >&2; exit 1; }
# the shadow jar does not carry nether-pathfinder (only remapJar nests it), and Baritone asks for it at start-up
NETHER="$(find "${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1/dev.babbaj/nether-pathfinder" -name 'nether-pathfinder-*.jar' ! -name '*-sources.jar' 2>/dev/null | head -1 || true)"
[ -n "$NETHER" ] || { echo "run.sh: nether-pathfinder is not in the Gradle cache - build once first" >&2; exit 1; }
if pgrep -f 'gradlew|GradleWrapperMain|GradleDaemon|gradle-launcher' >/dev/null; then
    echo "run.sh: Gradle is running; the client will not fit beside it" >&2
    exit 1
fi

export DISPLAY="$DISPLAY_NAME"
export LIBGL_ALWAYS_SOFTWARE=1
export LP_NUM_THREADS=1
mkdir -p "$MC_DIR/natives/lwjgl" "$MC_DIR/natives/jna" "$MC_DIR/natives/netty" "$MC_DIR/natives/java" "$MC_DIR/game/mods"
pgrep -x Xvfb >/dev/null || { Xvfb "$DISPLAY_NAME" -screen 0 1024x600x24 +extension GLX +render -noreset >"$MC_DIR/xvfb.log" 2>&1 & sleep 2; }
cp -f "$MOD" "$MC_DIR/game/mods/baritone.jar"

# Set on every start: the first-run screen asks about the narrator and would take the first clicks, and the
# rest keeps the client inside a 2 GB box. Edit them in game/options.txt only if you also change this.
OPTIONS="$MC_DIR/game/options.txt"
touch "$OPTIONS"
set_option() { # key value
    if grep -q "^$1:" "$OPTIONS"; then sed -i "s|^$1:.*|$1:$2|" "$OPTIONS"; else echo "$1:$2" >> "$OPTIONS"; fi
}
set_option onboardAccessibility false
set_option skipMultiplayerWarning true
set_option preferredGraphicsBackend '"vulkan"'
set_option graphicsPreset '"custom"' # a preset puts its own render and simulation distance back at every start
set_option renderDistance 2
set_option simulationDistance 5
set_option maxFps 20
set_option soundCategory_master 0.0
set_option tutorialStep '"none"'

QUICKPLAY=()
[ -d "$MC_DIR/game/saves/$WORLD" ] && QUICKPLAY=(--quickPlaySingleplayer "$WORLD")
cd "$MC_DIR/game"
exec "$JAVA" -Xmx"${HEADLESS_HEAP:-640m}" -Xms96m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 \
    -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=64m -XX:StackShadowPages=32 \
    --enable-native-access=ALL-UNNAMED --add-exports java.base/jdk.internal.misc=ALL-UNNAMED \
    -Djava.library.path="$MC_DIR/natives/java" \
    -Dorg.lwjgl.system.SharedLibraryExtractPath="$MC_DIR/natives/lwjgl" \
    -Djna.tmpdir="$MC_DIR/natives/jna" -Dio.netty.native.workdir="$MC_DIR/natives/netty" \
    "-DFabricMcEmu= net.minecraft.client.main.Main " \
    -cp "$(cat "$MC_DIR/fabric-classpath.txt"):$(cat "$MC_DIR/classpath.txt"):$NETHER" \
    net.fabricmc.loader.impl.launch.knot.KnotClient \
    --username "${HEADLESS_USER:-Headless}" --version "$(cat "$MC_DIR/minecraft-version.txt")" \
    --gameDir "$MC_DIR/game" --assetsDir "$MC_DIR/assets" --assetIndex "$(cat "$MC_DIR/asset-index.txt")" \
    --uuid 00000000-0000-0000-0000-000000000001 --accessToken 0 --versionType release \
    --width 854 --height 480 ${QUICKPLAY[@]+"${QUICKPLAY[@]}"} "$@" >"$MC_DIR/client.log" 2>&1
