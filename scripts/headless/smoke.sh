#!/bin/bash
# The crashes the unit tests could not see, as a script. Run it against a client that is already up (run.sh in the
# background; about 25 s to the title screen), and it
#   1. makes a world if there is none, and waits for the login;
#   2. walks a 36-block #goto to its end;
#   3. leaves the world while the elytra process holds a live behaviour (#elytra waits there for a take-off) -
#      the way out that crashed the game once, from the first tick with no player;
# then looks, in the log from step 2 on, for the game's own catch-all for an exception thrown from the tick.
#
# usage: smoke.sh      exit 0: none; exit 1: one or more, and the lines are printed; exit 2: the elytra process
#                      did not take control, so the way out proved nothing
# environment: BARITONE_HEADLESS (default ~/baritone-headless), HEADLESS_WORLD ("New World"), HEADLESS_DISPLAY (:99)
set -eu
HERE="$(cd "$(dirname "$0")" && pwd)"
MC_DIR="${BARITONE_HEADLESS:-$HOME/baritone-headless}"
WORLD="${HEADLESS_WORLD:-New World}"
LOG="$MC_DIR/client.log"
D="$HERE/drive.sh"

if [ -d "$MC_DIR/game/saves/$WORLD" ]; then
    "$D" wait-login
    sleep 6
else
    "$D" newworld
fi
MARK=$(wc -l < "$LOG")

"$D" chat '#goto ~30 ~ ~20'
sleep 25
"$D" chat '/item replace entity @s armor.chest with minecraft:elytra'
sleep 1
"$D" chat '/give @s minecraft:firework_rocket 64'
sleep 1
"$D" chat '#set elytraTermsAccepted true'
sleep 1
"$D" chat '#goal ~300 ~ ~'
sleep 1
"$D" chat '#elytra'
sleep 3
"$D" chat '#proc'
sleep 2
if ! tail -n +$((MARK + 1)) "$LOG" | grep -q 'Display name: Elytra'; then
    echo "smoke.sh: the elytra process did not take control, so the way out below would prove nothing" >&2
    exit 2
fi
"$D" key Escape
sleep 1.5
"$D" click 510 407   # Save and Quit to Title, in the pause menu of an 854x480 window
sleep 6

BAD="$(tail -n +$((MARK + 1)) "$LOG" | grep -E 'Unreported exception thrown|Game crashed|Crash report saved' || true)"
if [ -n "$BAD" ]; then
    echo "$BAD"
    echo "smoke.sh: FAILED - an exception thrown from the tick" >&2
    exit 1
fi
echo "smoke.sh: ok - a 36-block walk to its end and a way out with the elytra process alive, no exception from the tick"
