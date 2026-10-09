#!/bin/bash
# Real-game scenes for what the unit tests cannot run: climbing, bridging, parkour, mining, the farm, exploring.
# Each scene teleports to a spot of its own on the superflat world, builds what it needs with /fill, runs one Baritone
# command, and asserts what the game reports - arrival is a /execute ... run say token, a count is the number /fill
# prints for the blocks it replaced - and that the log holds no exception thrown from the tick.
#
# Run it against a client that is already up (run.sh in the background) with a world made by drive.sh newworld.
# usage: scenes.sh [stairs] [bridge] [parkour] [mine] [farm] [explore]      (default: all of them, about 7 minutes)
# exit: the number of scenes that failed
# environment: BARITONE_HEADLESS (default ~/baritone-headless), HEADLESS_DISPLAY (:99)
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
MC_DIR="${BARITONE_HEADLESS:-$HOME/baritone-headless}"
LOG="$MC_DIR/client.log"
D="$HERE/drive.sh"
pass=0
fail=0

lines() { wc -l < "$LOG"; }
since() { tail -n +$(( $1 + 1 )) "$LOG"; }
tick_exceptions() { since "$1" | grep -c -E 'Unreported exception thrown|Game crashed' || true; }
# the last "Successfully filled N" (N) or "No blocks were filled" (0) after line $1
filled() { since "$1" | grep -E 'CHAT.*(Successfully filled [0-9]+|No blocks were filled)' | tail -1 | sed -E 's/.*Successfully filled ([0-9]+).*/\1/; s/.*No blocks were filled.*/0/'; }
arrived() { # X Y Z MARK: was the player within 2.5 blocks of the block, according to the game
    local token="ARR$RANDOM$RANDOM"
    "$D" chat "/execute positioned $1 $2 $3 if entity @s[distance=..2.5] run say $token"
    sleep 2.5
    since "$4" | grep -q "\[${HEADLESS_USER:-Headless}\] $token"   # as say output, never as the typed command
}
go() { "$D" chat "/tp @s $1 -60 $2"; sleep 5; } # to a scene's start, and let the chunks around it come in
report() { # NAME OK DETAIL
    if [ "$2" = 1 ]; then echo "PASS  $1 - $3"; pass=$((pass + 1)); else echo "FAIL  $1 - $3"; fail=$((fail + 1)); fi
}

scene_stairs() { # a two-high bump, wide enough that climbing is cheaper than going round it
    go 0 0; local m; m=$(lines)
    "$D" chat '/fill 4 -60 -12 4 -60 12 minecraft:stone'
    "$D" chat '/fill 5 -60 -12 7 -59 12 minecraft:stone'
    "$D" chat '/fill 8 -60 -12 8 -60 12 minecraft:stone'
    "$D" chat '#goto 12 -60 0'; sleep 25
    local ok=1 x; arrived 12 -60 0 "$m" || ok=0; x=$(tick_exceptions "$m"); [ "$x" = 0 ] || ok=0
    report stairs "$ok" "up two, along, down two; tick exceptions $x"
}

scene_bridge() { # a trench four wide and three deep, the whole length of what is loaded: it has to be bridged
    go 12 60; local m; m=$(lines)
    "$D" chat '/fill 14 -63 36 17 -61 84 minecraft:air'
    "$D" chat '/give @s minecraft:stone 64'
    "$D" chat '#goto 22 -60 60'; sleep 55
    local ok=1 x; arrived 22 -60 60 "$m" || ok=0; x=$(tick_exceptions "$m"); [ "$x" = 0 ] || ok=0
    report bridge "$ok" "across a 4-wide trench; tick exceptions $x"
}

scene_parkour() { # a trench three wide, with parkour allowed
    go 24 120; local m; m=$(lines)
    "$D" chat '/fill 26 -63 96 28 -61 144 minecraft:air'
    "$D" chat '#set allowParkour true'
    "$D" chat '#goto 33 -60 120'; sleep 40
    local ok=1 x; arrived 33 -60 120 "$m" || ok=0; x=$(tick_exceptions "$m"); [ "$x" = 0 ] || ok=0
    "$D" chat '#set allowParkour false'
    report parkour "$ok" "across a 3-wide trench; tick exceptions $x"
}

scene_mine() { # six of twenty-seven
    go 33 180; local m; m=$(lines)
    "$D" chat '/give @s minecraft:iron_pickaxe'
    "$D" chat '/clear @s minecraft:raw_iron' # a mined six from an earlier run would end #mine 6 before it began
    "$D" chat '/fill 38 -60 179 40 -58 181 minecraft:iron_ore'
    "$D" chat '#mine 6 iron_ore'; sleep 55
    "$D" chat '#stop'; sleep 2
    local after; after=$(lines)
    "$D" chat '/fill 38 -60 179 40 -58 181 minecraft:air replace minecraft:iron_ore'; sleep 2.5
    local left x ok=1; left=$(filled "$after"); x=$(tick_exceptions "$m")
    since "$m" | grep -q 'Have 6 valid items' || ok=0
    { [ -n "$left" ] && [ "$left" -le 21 ] && [ "$left" -ge 18 ]; } || ok=0
    [ "$x" = 0 ] || ok=0
    report mine "$ok" "27 ore built, ${left:-?} left standing, baritone said 'Have 6 valid items': $(since "$m" | grep -c 'Have 6 valid items'); tick exceptions $x"
}

scene_farm() { # twenty-four ripe wheat round a water source: all harvested, and replanted
    go 38 244; local m; m=$(lines) # inside the range of #farm 16 from the far corner: the range is from where it is told
    "$D" chat '/fill 40 -61 248 44 -61 252 minecraft:farmland'
    "$D" chat '/setblock 42 -61 250 minecraft:water'
    "$D" chat '/fill 40 -60 248 44 -60 252 minecraft:wheat[age=7]'
    "$D" chat '/give @s minecraft:wheat_seeds 64'
    "$D" chat '#farm 16'; sleep 70
    "$D" chat '#stop'; sleep 2
    local a b; a=$(lines)
    "$D" chat '/fill 40 -60 248 44 -60 252 minecraft:air replace minecraft:wheat[age=7]'; sleep 2.5
    local ripe; ripe=$(filled "$a"); b=$(lines)
    "$D" chat '/fill 40 -60 248 44 -60 252 minecraft:air replace minecraft:wheat'; sleep 2.5
    local planted x ok=1; planted=$(filled "$b"); x=$(tick_exceptions "$m")
    [ "${ripe:-1}" = 0 ] || ok=0
    { [ -n "$planted" ] && [ "$planted" -ge 15 ]; } || ok=0
    [ "$x" = 0 ] || ok=0
    report farm "$ok" "ripe wheat left standing ${ripe:-?} (24 before), replanted ${planted:-?}; tick exceptions $x"
}

scene_explore() { # twenty seconds of #explore
    go 33 300; local m; m=$(lines)
    "$D" chat '#explore'; sleep 25
    "$D" chat '#stop'; sleep 2
    local x ok=1; x=$(tick_exceptions "$m"); [ "$x" = 0 ] || ok=0
    report explore "$ok" "twenty-five seconds; tick exceptions $x"
}

"$D" wait-login
sleep 3
scenes=("$@")
[ ${#scenes[@]} -gt 0 ] || scenes=(stairs bridge parkour mine farm explore)
for s in "${scenes[@]}"; do
    "$D" chat '#stop'; sleep 1
    "scene_$s"
done
echo "scenes: $pass passed, $fail failed"
exit "$fail"
