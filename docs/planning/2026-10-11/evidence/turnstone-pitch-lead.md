# Pitch lead: the 90.0-degree hold is `MovementFall`'s bucket catch

Author: Turnstone (2026-10-11 13:55 UTC)
Status: **source-derived hypothesis, not a measurement.** Nightjar owns the trace and the event; this file exists so the test can be run without me.

## What was observed (Nightjar, 2026-10-09 10:20 UTC, `docs` wording preserved in AGENTS.md)

One walk of ten, on a flat `#goto`: pitch swept to exactly 90.0 degrees at about 8.7 deg/tick,
held for 18 ticks while the bot kept sprinting, then went 89.9 -> 0.0 in one tick.
Rows: `8556 yaw -113.9 pitch 27.1`, `8560 -117.5 61.9`, `8564 -118.0 90.0`, `8581 -97.2 90.0`,
`8583 -90.1 88.6`, `8585 -89.7 89.9`, `8587 -90.0 0.0`; x 20.4 -> 30.3.
World had 30 zombie horses and 4 chickens moving within 40 blocks.

## The lead

`src/main/java/baritone/pathing/movement/movements/MovementFall.java:130` is the **only** write of a
literal 90.0 pitch in `src/main`:

```java
targetRotation = new Rotation(toDest.getYaw(), 90.0F);
```

It sits in the bucket-catch branch:

* guarded by `!isWater && willPlaceBucket() && !playerFeet.equals(dest)` (`:106`),
* `willPlaceBucket()` (`:82-86`) is `MovementDescend.dynamicFallCost(...)`, whose `true` return is the
  `context.hasWaterBucket && unprotectedFallHeight <= context.maxFallHeightBucket + 1` case
  (`MovementDescend.java:211+`) - i.e. a *bucket-caught fall*, not an ordinary drop;
* it requires a water bucket in a hotbar slot, and returns `UNREACHABLE` without one (`:108-110`);
* it fires only while `!ctx.player().onGround()` and the destination is within block reach (`:116`).

Four details of the observation fit it independently:

1. the pitch is **exactly** 90.0, which is the literal, not a wander extreme;
2. the hold has no wander noise, which is `LookBehavior#settledGaze` (`LookBehavior.java:1269-1281`)
   returning the asked pitch untouched for `blockInteract` targets (`MovementFall:138` flags it `true`);
3. the yaw is `toDest.getYaw()`, the bearing to the landing block, which swings as the player drifts
   - matching `-117 -> -90` while x moves ~10 blocks;
4. a sprint continuing through it is what a fall does.

## The test that kills the lead

Read the `y` column and the ground state for ticks 8550-8590:

* **descending and airborne through the hold** -> the lead stands; the event is the deliberate
  straight-down bucket aim (documented exception, patch `0030`, `HUMANLIKE.md`), not a defect;
* **y flat** -> the lead is dead. It is still unexplained, and this file should be corrected, not
  quietly ignored.

## The one-tick snap back to 0.0 (still open either way)

Two candidates, and the discriminating evidence:

* **water entry** - `pitchIsAFreeAngle` is false in water and the pitch becomes the swimmer's depth.
  Evidence for: a bucket placement / a water block at that tick, and y stopping at a water surface.
* **the movement ending** - the fall ends and the next movement's target takes over.
  Evidence for: an ordinary next movement in the log with no water involved.

The trace cannot tell these apart by itself; the log line for the bucket placement can.
