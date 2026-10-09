#!/usr/bin/env python3
"""Reads the per-tick trace out of the client log and says what the player did.

The trace is a datapack that drive.sh trace on writes into the world: a tick function that tells the player,
once a game tick, "tick|[x, y, z]|[yaw, pitch]" (it is tellraw and not data get, because the output of a tick
function is suppressed). The lines land in the log as [CHAT] lines.

usage: trace.py LOG [FROM_LINE [TO_LINE]] [--csv]
  FROM_LINE, TO_LINE   log line numbers (wc -l on the log before and after the thing you want to see)
  --csv                one row per sample - tick,x,y,z,yaw,pitch - instead of the summary

Read the numbers with one thing in mind. The positions are what the server was told, and a client that runs below
20 frames a second (this one, on software rendering) runs its 20 ticks a second in bursts: two movement packets
reach the server in one tick and none in the next. A per-tick speed therefore reads 0.56, 0.28, 0.00 for a walk
at 0.28 blocks a tick. Everything here that depends on speed is taken over windows of four ticks; yaw and pitch
are read as they are, since a rotation does not double.
"""
import math
import re
import statistics
import sys

SAMPLE = re.compile(r'\[CHAT\] (\d+)\|\[([-\d.eE]+)d, ([-\d.eE]+)d, ([-\d.eE]+)d\]\|\[([-\d.eE]+)f, ([-\d.eE]+)f\]')
WINDOW = 6


def read(path, start, stop):
    rows = []
    with open(path, errors='replace') as f:
        for i, line in enumerate(f):
            if i < start or (stop is not None and i >= stop):
                continue
            m = SAMPLE.search(line)
            if m:
                rows.append((int(m.group(1)),) + tuple(float(g) for g in m.groups()[1:]))
    return rows


def wrap(angle):
    return (angle + 180.0) % 360.0 - 180.0


def summary(rows):
    if len(rows) < 3 * WINDOW:
        sys.exit('trace.py: %d samples is too few to say anything' % len(rows))
    print('samples %d, ticks %d to %d' % (len(rows), rows[0][0], rows[-1][0]))
    # speed over WINDOW ticks, in blocks a tick, at the end of each window
    speed = [0.0] * len(rows)
    for i in range(WINDOW, len(rows)):
        a, b = rows[i - WINDOW], rows[i]
        ticks = max(1, b[0] - a[0])
        speed[i] = math.hypot(b[1] - a[1], b[3] - a[3]) / ticks
    top = max(speed)
    if top < 0.02:
        print('the player did not move; yaw %.1f..%.1f, pitch %.1f..%.1f'
              % (min(r[4] for r in rows), max(r[4] for r in rows), min(r[5] for r in rows), max(r[5] for r in rows)))
        return
    moving = [i for i, v in enumerate(speed) if v > 0.02]
    first, last = moving[0], moving[-1]
    # the middle half of the walk is where the speed is steady; the fastest window is inflated by bursts
    middle = speed[first + (last - first) // 4: last - (last - first) // 4] or speed[first:last + 1]
    cruise = statistics.median(middle)
    along = sum(math.hypot(b[1] - a[1], b[3] - a[3]) for a, b in zip(rows[first - WINDOW:last], rows[first - WINDOW + 1:last + 1]))
    seconds = (rows[last][0] - rows[max(0, first - WINDOW)][0]) / 20.0
    print('moving for about %d ticks (%.1f s); %.1f blocks covered, %.2f blocks/s on average'
          % (last - first, (last - first) / 20.0, along, along / seconds if seconds else 0))
    print('cruise (median over the middle of the walk) %.3f blocks/tick = %.2f blocks/s; the fastest window %.3f is inflated by bursts'
          % (cruise, cruise * 20, top))
    ramp = next(i for i in range(first, len(speed)) if speed[i] >= 0.9 * cruise) - first
    print('ticks from the first motion to 90%% of cruise: %d (%d-tick windows: a resolution of %d)' % (ramp, WINDOW, WINDOW))
    tail = [i for i in range(last, first, -1) if speed[i] >= 0.9 * cruise]
    print('ticks from the last 90%% of cruise to the stop: %d' % (last - (tail[0] if tail else last)))
    ys = [r[4] for r in rows[first:last + 1]]
    ps = [r[5] for r in rows[first:last + 1]]
    dy = [abs(wrap(rows[i][4] - rows[i - WINDOW][4])) for i in range(first, last + 1) if i >= WINDOW]
    dp = [abs(rows[i][5] - rows[i - WINDOW][5]) for i in range(first, last + 1) if i >= WINDOW]
    print('yaw   while moving: %.1f to %.1f (range %.1f), fastest turn %.1f degrees in %d ticks'
          % (min(ys), max(ys), max(ys) - min(ys), max(dy or [0]), WINDOW))
    print('pitch while moving: %.2f to %.2f (range %.2f), fastest change %.2f degrees in %d ticks, %d distinct values'
          % (min(ps), max(ps), max(ps) - min(ps), max(dp or [0]), WINDOW, len(set(round(p, 3) for p in ps))))
    print('pitch while moving: median %.1f, mean %.1f; %.0f%% of the samples more than 15 degrees down (positive is down)'
          % (statistics.median(ps), statistics.fmean(ps), 100.0 * sum(1 for p in ps if p > 15.0) / len(ps)))
    print('at rest before: yaw %.1f pitch %.1f; at rest after: yaw %.1f pitch %.1f'
          % (rows[0][4], rows[0][5], rows[-1][4], rows[-1][5]))


def main(argv):
    args = [a for a in argv if not a.startswith('--')]
    if not args:
        sys.exit(__doc__)
    path = args[0]
    start = int(args[1]) if len(args) > 1 else 0
    stop = int(args[2]) if len(args) > 2 else None
    rows = read(path, start, stop)
    if '--csv' in argv:
        print('tick,x,y,z,yaw,pitch')
        for r in rows:
            print('%d,%.4f,%.4f,%.4f,%.3f,%.3f' % r)
    else:
        summary(rows)


if __name__ == '__main__':
    main(sys.argv[1:])
