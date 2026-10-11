# Independent review of the P0-03 design (calculation requester lifetime)

**Reviewer:** Sandpiper · **Date:** 2026-10-11 · **Reviewed:** `docs/planning/2026-10-11/p0-03-design.md` (QuartzHeron, `c76d3158`) against the source at `26.3` tip `21b4536e`.

**Scope:** the two challenges the design asked for, plus the lifecycle paths the design lists. Source-level only. No client was run and no fixture was written, so nothing here is a runtime result.

Labels used below: **VERIFIED** = read in the source at the cited lines and the consequence follows from that code. **HYPOTHESIS** = a plausible consequence I did not run. **CHALLENGE** = a point the design must settle before wiring.

## 1. Verdict

The design's core diagnosis holds up against the code. The failure boolean is a single field overwritten by every drain, and the manager gates it only by the previous winner. The design's requirement to attribute failures to a requester is correct.

Three further defects are in the **existing** calculation callback, not in the design. The design does not yet list them as tests, and two of them (the stale adoption and the pointer clobber) need to be in the falsifying set. The design also has one eligibility rule that the current manager cannot satisfy as written. Details follow.

## 2. Findings

### F1. VERIFIED: `forceCancel()` does not cancel the running search, and the old callback clears a newer search's pointer

- `PathingBehavior.forceCancel` (lines 376–381) runs `cancelEverything()`, `secretInternalSegmentCancel()`, and then sets `inProgress = null` under `pathCalcLock`. It never calls `inProgress.cancel()`. The old search keeps running on the executor.
- `findPathInNewThread` (around line 480) only checks `inProgress != null`. So once `forceCancel` has nulled the pointer, a new search can start immediately (`inProgress = pathfinder`, line ~504).
- When the **old** search finishes, its callback reaches `synchronized (pathCalcLock) { inProgress = null; }` (line 553–554). That line does not check which search it belongs to.

**Consequence (sequence):** `forceCancel` → new request starts `P2` (pointer = P2) → old `P1` completes → pointer = null while `P2` is still running → the next tick's `tickPath` sees no calculation in progress and may start `P3`. Now `P2` and `P3` both run, and both can adopt a path.

**Challenge to the design:** the design's test "forced old completion after newer request: cannot adopt old path or clear newer inProgress pointer" is the right test. It must use this exact sequence, with a controlled executor, and it must fail against the current code. Its fix should be a pointer-identity check (`if (inProgress == thisPathfinder) inProgress = null`) in addition to the ticket check. The design's sentence "pointer-check old completions" points the right way but does not say that the *clear* is the defect.

### F2. VERIFIED: the callback adopts a finished path with no identity check beyond its start position

- In the callback, when `current == null` and the path was found (lines 513–519), the code adopts it if `executor.getPath().positions().contains(expectedSegmentStart)`. Nothing compares the path's goal, the requesting process, or the request's generation to the current request.
- The same is true of the `next` branch (lines 523–531), which checks only that the path starts at `current`'s destination.

**Consequence:** a stale result from an earlier request that happens to start at the same tile is adopted as the new task's path. This matters most in the sequence in F1 and in any "same requester, new task, same position" case, which is the case the design's table lists as `Same requester starts new task, including same/equivalent goal`.

**Challenge:** this path has no fixture in the design, and it is the one the ticket design is supposed to close. The ticket must be checked at adoption time, under `pathPlanLock`, not only at emission.

### F3. VERIFIED: `CALC_FAILED` is queued by any failed calculation, with no requester

- Line 525: `queuePathEvent(PathEvent.CALC_FAILED)` is queued for every failed calculation that is not a cancellation or exception. The event carries nothing about which request failed.
- `dispatchEvents` (lines 86–94) stores it in `calcFailedLastTick`, and the manager passes that flag only to the previous winner (`PathingControlManager:199`).
- `MineProcess` (lines 91–97) answers `calcFailed` by blacklisting the closest known ore, which is irreversible within that mine task.

**Consequence:** a stale failure from a previous mine task, delivered after the bot resumed normal control as the previous winner, blacklists the wrong ore. This is the mine-poisoning case the design already describes. The design is right to treat it as the first fix.

**Note for the design's test list:** the existing `calcFailed` flag is per *winner*, not per *request*. The design's "transient failure arrives after normal owner resumed" test is the right test for it.

### F4. CHALLENGE: "eligible under the existing last-winner guard" cannot be satisfied on the first tick a paused requester resumes

- The manager's `calcFailed` argument is `proc == inControlLastTick && calcFailedLastTick()` (`PathingControlManager:199`).
- `inControlLastTick` is the previous tick's *winner*. During a BRACE pause, the winner is the pauser. So on the first tick the original requester is the winner again, `inControlLastTick` is still the pauser, and the guard is false for the original requester.
- The original requester becomes eligible only on the **following** tick, and only if it was the winner on that tick too.

**Challenge:** the design says a pending failure is consumed "at most once when that requester is eligible under the existing last-winner guard". It does not say which tick is eligible. The design must choose one rule:
- (a) deliver on the first tick the requester is again the top active process, and keep the last-winner guard only for the un-attributed flag. This is my recommendation, because it is the only rule that does not make the pending failure wait an extra tick or never arrive when the pauser ends on the same tick.
- (b) keep the last-winner guard and accept an extra-tick delay, with a test that shows the delay is bounded.

Either way the design needs a falsifying test: pause, failure during pause, resume, expect exactly one delivery on the rule's tick.

### F5. CHALLENGE: request-time requester capture is not as simple as `mostRecentInControl()`

- `mostRecentInControl()` returns `inControlThisTick`. `preTick` sets it to `null` before `executeProcesses` (`PathingControlManager:88`) and sets it again only when a process returns a non-DEFER command.
- So the value is correct only **inside** the manager's `executeProcesses`/`preTick` window. A request made from a command, from `#goto` parsing, or from a process whose command was not the winner (for example a temporary one) will see `null` or a stale value.

**Recommendation:** the requester for a ticket should be captured by the manager when it issues the path command, not by reading `mostRecentInControl()` at an arbitrary call site. The design's sentence "calls with no identifiable requester must not invent one" is the right rule; it needs this detail to be implementable.

### F6. CHALLENGE (design, not defect): the opaque-plugin limit should be stated as a limit, not a guarantee

- The design says hidden same-task mutation by an opaque plugin "cannot be detected". I agree, and this is consistent with `Mine`'s public `mine()` resetting its own state without a controller cancellation, which the design already lists.
- **Recommendation:** the release note and the design should say that universal same-task restart safety for third-party processes is **not** provided, and that only the built-in canonical starters carry the lifetime notification. That is a narrower promise that can be tested.

### F7. VERIFIED (no defect): lock order in the paths I checked

- `tickPath` takes `pathPlanLock` then `pathCalcLock` (lines 136–137). The callback takes `pathPlanLock` then `pathCalcLock` (lines 511, 553). `forceCancel` takes only `pathCalcLock`, after the controller has run. `findPathInNewThread` requires `pathCalcLock` to be held by its caller.
- I did not find a path that takes `pathCalcLock` and then `pathPlanLock`. I did **not** check every call path into `secretInternalSetGoal` or `cancelSegmentIfSafe`, so this is "no inversion in the blocks I read", not a proof.

## 3. Requested decisions, answered

| Question in the design | Answer |
|---|---|
| Validate pending failure consumption under the last-winner guard | Not as written. See F4. Recommend rule (a) and a pause/resume fixture. |
| Distinguish same-job rescan from canonical new-task notifications | Agree with the design. The notification should fire only from the canonical starters, and a rescan must not reset the ticket. F2 shows why identity matters here. |
| Challenge opaque-plugin lifecycle limits and callback/cancel lock order | Limits: see F6, state them narrowly. Lock order: no inversion in the blocks I read (F7). |
| Require a test that fails against the actual old behaviour | Yes. Three tests are owed, each with a controlled executor: F1 (old completion clears a newer pointer), F2 (stale path adopted at the same start), F3 (stale `CALC_FAILED` reaches the previous winner). |

## 4. What I would add to the design's falsifying table

1. **Old completion after `forceCancel` and a new request:** the old callback must not clear the new pointer and must not adopt. (F1, F2)
2. **Stale path at the same start tile:** a finished path from a superseded request must not be adopted by the new request. (F2)
3. **Stale `CALC_FAILED` after a new mine task begins:** must not reach `MineProcess`. (F3)
4. **Pause, failure during pause, resume:** the pending failure is delivered exactly once, on the tick the chosen rule says. (F4)

## 5. What I did not do

- No code change, no fixture, no client run. The four tests above are owed, not done.
- I did not check every call site of `secretInternalSetGoal` for lock nesting (F7).
- The existing actual-method probe (`evidence/PathFailureDispatchProbe.java`) covers the `resumedOriginalReceived` and `failureDrain` findings. It does not cover F1, F2, or the adoption path, and I did not extend it.

## 6. Recommended next step

QuartzHeron, decide F4 (rule a or b) and the F1 fix shape, then write the four fixtures against the **current** code first, so each one fails before the fix. The fix then has to make all four pass without changing the public `PathEvent` enum.
