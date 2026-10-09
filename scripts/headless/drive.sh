#!/bin/bash
# Drives the headless client the way a person would - the mouse and the keyboard, through xdotool - and reads
# it the way a person would: screenshots, and the log. The screen is 1024x600 and the game window is 854x480
# at (85, 60); the coordinates below are on the screen, for a window of that size.
#
# usage: drive.sh chat TEXT | key KEY | click X Y | shot FILE [PERCENT] | wait-login | respawn | newworld
#   chat       opens the chat with t, types TEXT, sends it ('#goto ~30 ~ ~20', '/difficulty peaceful')
#   shot       the game window, optionally scaled down, as a PNG
#   newworld   on a fresh game directory only: creates a superflat world with commands on, waits for the
#              login, and sets the difficulty to peaceful (slimes spawn on a superflat surface by day)
# environment: BARITONE_HEADLESS (default ~/baritone-headless), HEADLESS_DISPLAY (:99)
set -eu
MC_DIR="${BARITONE_HEADLESS:-$HOME/baritone-headless}"
export DISPLAY="${HEADLESS_DISPLAY:-:99}"
LOG="$MC_DIR/client.log"

click() { xdotool mousemove "$1" "$2"; sleep 0.4; xdotool click 1; sleep 0.8; }
chat() { xdotool key t; sleep 1.2; xdotool type --delay 80 -- "$1"; sleep 0.6; xdotool key Return; }

case "${1:-}" in
    chat) chat "$2" ;;
    key) xdotool key "$2" ;;
    click) click "$2" "$3" ;;
    shot) import -window root png:- | convert png:- -crop 854x480+85+60 +repage -resize "${3:-100}%" "$2" ;;
    wait-login)
        for _ in $(seq 1 45); do
            grep -q 'logged in with entity id' "$LOG" 2>/dev/null && exit 0
            sleep 2
        done
        echo "drive.sh: no login in 90 seconds - see $LOG" >&2
        exit 1 ;;
    respawn) click 511 343 ;;
    newworld)
        click 511 296   # Singleplayer: with no world saved the game goes straight to Create New World
        sleep 2
        click 400 343   # Allow Commands: ON
        click 512 87    # the World tab
        click 351 149   # World Type: Superflat
        click 776 295   # Generate Structures: OFF
        click 353 507   # Create New World
        "$0" wait-login
        sleep 6
        chat '/difficulty peaceful' ;;
    *) sed -n '2,12p' "$0"; exit 2 ;;
esac
