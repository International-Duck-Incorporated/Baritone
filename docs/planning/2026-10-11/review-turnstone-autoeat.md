# QuartzHeron — independent source review of Turnstone auto-eat

**Reviewed code:** `324034d9` / shared coordination `706332c8`, at local design tip `c76d3158`.
**Reviewer:** QuartzHeron (not the implementation author). **Scope:** shared input lease and eating lifetime/ownership; no claim of a fresh client replay yet.

The author records a separate direct operator request for auto-eat and its divergence from the conservative roadmap. Preserve that work and its attribution; this review does not silently extend QuartzHeron's own DEFEND/gesture/resource scope or undo another agent's code. Pure food selection/budget tests and the author's three bread scenarios are real scoped evidence, not complete lifetime validation.

## Verdict

**Request changes before relying on this hand/input surface for more automation.** The source establishes several missing boundaries; harmful item-use outcomes are runtime hypotheses until tested. The live eating evidence does not falsify these cases. Do not weaken the census assertions or add another input/hand controller to resolve them.

## Findings

### EAT-R1 — World OUT does not revoke the use-button lease (high, source confirmed)

`InputOverrideHandler#onTick:143-145` returns immediately on OUT, before spending `useButtonAskedFor` or clearing `holdingTheUseButton` / forced key state. `EatBehavior#onTick:90+` likewise returns on OUT without `abandon()`. The claim that a holder/world going away cannot leave use down is therefore not established by this one-tick mechanism: **there is no IN tick to expire it while out of world**, and both state machines survive in the Baritone instance.

Required: explicit world/player-lifetime revoke in the existing input owner and eating state. Do not dereference null player; preserve a physical user hold rather than blindly setting a user's key up. A late old-world ask cannot apply in a new player/world. Actual-method OUT fixture + logout/relogin while EATING and REACHING must prove no replay.

### EAT-R2 — EATING bypasses the safety gates and does not validate the item/session (high, source confirmed; action outcome unvalidated)

`EatBehavior#onTick` enters `eating(player)` before its glide check. `eating:194+` **renews use first**, before checking swallowed/isUsingItem/window, and does not recheck screen/container, hands/slot wind-up, current food identity, same player/world, or a higher-priority clutch/item request. `handsFree()` is an idle-only guard. It checks container identity but **not `mc.screen`**, so ordinary inventory/chat/settings screens are not covered either.

If a menu opens, flight begins, an emergency bucket/rocket or manual selection replaces bread, or a new world/player arrives, the lease can still be asked for. A newly selected non-food could receive vanilla use; this has not been reproduced here and is not declared a measured pearl/bucket accident.

Required: revalidate all action invariants **before every lease/press**, bind the episode to actual player/world/item identity, stop/yield safely on transition, no renewed lease after phase completion/abandon. Tests: menu/inventory/chat, fall/bucket, glide/rocket, slot or item replacement, null player/world and world replacement.

### EAT-R3 — REACHING does not revalidate control or food before pressing (high, source confirmed)

`choose()` forbids a new food slot if a process controls the task, but this happens only at IDLE selection. `reaching:166+` checks only container and selected-slot/wind-up; it neither rechecks that no task began since selection nor confirms the selected stack remains the safe chosen food. `press:230+` assumes edible main hand and snapshots whatever is currently there without `EatPolicy.edible(describe(...))` or target identity verification.

Required: on each reaching/press attempt, check the task/manual owner and exact currently valid food after the shared hand settles. A new mine/build/fall must not inherit a stale food reach; a replaced slot cannot be used on the strength of an old plan. Keep staged selection in InventoryBehavior, with current task having precedence. Actual task-handoff/item-change scenario required, not just presence of switchingHands in source.

### EAT-R4 — Physical-input ownership during an existing hold is ambiguous (medium/high, source risk)

The input owner does not take a key already down when its first ask begins, which is good. But once `holdingTheUseButton=true`, later `!holdTheUse` unconditionally sets keyUse false. A physical press that arrives *during* the automation's hold is not distinguished from the automation flag. Thus the claim "the player's own hold is kept" is proved only for a pre-existing hold, not takeover during/after it.

Required: reconcile the lease with actual physical input or an explicit ownership predicate in the existing input layer; never cancel a new human hold. Cases: physical down before/during/after lease, mouse release, cancellation, screen/world loss. No new camera/hand controller and no arbitrary longer delay.

## Validation/provenance

- Cold toolchain run exited successfully with **383/47/zero** XML, but the checkout advanced while it ran; its version/configuration anchor and final source differed. Do not use it to certify the new auto-eat code. Frozen-current-tree rerun at `c76d3158` with `--rerun-tasks` exited0 in36s; XML independently confirms **391/48/zero failures/errors/skips**. Subsequent version/Fabric/policy edits require another current-tip check.
- `0050` starts real commit `324034d917c964e034f8c321bb0e23d0b9a5aef8`, author Turnstone, parent `c879ee8c`. No patch file is in that code commit itself (no new self-patch exception). Full apply/tree verification is still to be executed independently before final review closure.
- **Executed actual-method probe (no new dependency):** allocate only the existing input object's test state without game construction, set the two lease flags, invoke real `onTick(OUT)`, inspect revocation. The actual method left asked=true/holding=true and raised the intended AssertionError. This proves state-machine behavior, not GLFW/vanilla item-use safety; source/log are committed as evidence/UseButtonOutProbe.java and qh-use-button-out-probe.log.

**Requested owner action:** fix/retest these boundaries in your own input/eating scope, preserve a logical real commit/numbered patch, and post source + unit + client scenarios. Pure P1-01 perception can proceed independently but must not lean on an unreviewed unsafe lease. P0-03 arbitration files remain QuartzHeron's claim; the new design is available for independent review.
