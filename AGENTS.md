Chat under here, make plans, communicate, and think about redundancy, because you may get stopped by an error abruptly.
you cannot live forever sadly.
chose a name and chat under here:

===start===

## Active Agents

| Agent | Current task | Area | Status |
|---|---|---|---|
| Tempo | Audit and improve grounded movement cadence after existing camera/input work | `InputOverrideHandler`, `PlayerMovementInput`, path execution | Planning; no source changes yet |

## Multi-Agent Planning

### Tempo

Findings: The branch already contains substantial humanlike camera work (aim dynamics, reaction delay, gaze, pitch wander, glances, idle behavior), path-facing/landing/sprint wind-up, inventory swap wind-up, and a `KeyBeat` that inserts a safe beat between opposite walking directions. `KeyBeat` is integrated at `PlayerMovementInput`, but grounded movement otherwise still changes its requested key state directly every tick; sprint and jump controls are set by many movement implementations and must not be globally delayed. The input layer is the safest shared seam for contextual movement cadence.

Proposal: First preserve existing work and audit the actual movement-input pipeline and tests. Add only a small state-aware improvement that does not interfere with airborne, sneak, water, parkour, or interaction controls; test it in isolation and via the existing Gradle test suite. Separate future work into movement cadence/reversal, interaction timing, and mining/placement review rather than duplicating camera changes.

## Agreed Implementation Plan

1. Tempo: finish the grounded movement-cadence audit, claim a concrete minimal task after reviewing other agents' notes, implement and test without changing path costs.
2. Other agents: camera/look, inventory, sprint and path-execution changes already present on `26.3`; review for regressions before touching overlapping files.
3. All changes: use standalone real patches in `patches/`, logically separate commits, and document tests/limitations here after verified push.

## Known Problems

- Coordination history from earlier agents is not yet recorded; do not erase their work. The current branch contains many preceding humanlike commits, so inspect before editing overlapping files.
- Full Gradle validation may require downloading Minecraft/Forge/Fabric dependencies; record exact environment blockers if encountered.

## Completed Work

- Initial coordination structure added by Tempo; prior branch history retained unchanged.

## Future Tasks

- Review grounded acceleration/deceleration beyond direction-reversal beats.
- Review block-breaking/placing and interaction action cadence for abrupt repeated clicks.
- Review item switching and path handoff integration after the inventory wind-up.
- Review deterministic/repetitive idle and target-acquisition behavior without adding gratuitous randomness.

### Tempo (concrete claim)

Task: Make direct firework use honor the existing hotbar/inventory hand wind-up instead of clicking in the same tick as an item-selection request.
Subsystem: Elytra item-use handoff and inventory behavior integration.
Files: `src/main/java/baritone/process/elytra/ElytraBehavior.java`; tests/docs as needed.
Status: Complete; pushed in `5e905d8e` and documented in `8b7b76f1`.
Started: 2026-10-08.
Dependencies: Existing `InventoryBehavior#switchingHands`, `HotbarSwitch`, and `BlockPlaceHelper` hand-ready gate.
Notes: Farm/path clicks already flow through `InputOverrideHandler`; Elytra's direct `processRightClick` bypasses that gate. Preserve flight safety by waiting for the requested firework to be in the main hand and only marking boost state after a successful use.

## Completed Work

### Tempo — elytra firework handoff (verified remote `a11f6d22`)

- Changed `ElytraBehavior#tickUseFireworks` so direct rocket use waits for `InventoryBehavior`'s hotbar wind-up, selects the main or off hand that actually contains the rocket, and only records boost/cooldown state after `processRightClick` succeeds.
- This closes a concrete bypass introduced by delayed item selection: the old direct main-hand click could use the previous item, could not launch an off-hand rocket, and could mark a failed use as boosted.
- Added standalone real patch `patches/0002-honor-hotbar-windup-before-elytra-firework-use.patch`; verified it with `git apply --check` against the commit's parent.
- Validation: `git diff --check` passed; Gradle configuration began successfully but `./gradlew test` was blocked before project tests by dependency TLS/peer-authentication failures while resolving artifacts from Forge/Fabric/NeoForge/Maven repositories. No source compilation result is available in this environment.
- Limitation: the Elytra runtime path needs an in-game/manual check for a hotbar rocket, an off-hand rocket, and a failed item-use response once dependencies are available.

### Follow-up for next agent

- Review other direct `processRightClick` callers (especially farm/elytra-adjacent paths) for bypasses of `InventoryBehavior#switchingHands`; forced clicks already flow through `InputOverrideHandler`'s hand-ready gate.
- Keep the `ElytraBehavior` change separate from future flight-control timing work; do not add a global click delay that can interfere with emergency landing or firework conservation.

### Tempo (new concrete claim)

Task: Cancel a pending inventory swap when its execution context disappears instead of executing a stale window click after a process handoff or GUI change.
Subsystem: Inventory swap state machine.
Files: `src/main/java/baritone/behavior/InventoryBehavior.java`, `src/main/java/baritone/utils/InventorySwap.java`, `src/test/java/baritone/utils/InventorySwapTest.java`.
Status: Complete; pushed in `0c503c74` and patch in `cfebf6a6`.
Started: 2026-10-08.
Dependencies: Existing `InventorySwap` wind-up and inventory-menu/allow-inventory guards.
Notes: The current `onTick` executes `pendingSwap` before checking `allowInventory` or whether the player is still in the player inventory menu. A handoff during the one-to-two-tick wind-up can therefore produce an old slot swap in a newly opened context.

### Tempo — stale inventory swap cancellation (verified remote `cfebf6a6`)

- `InventoryBehavior` now tracks whether the live path/process requested a pending swap in the current tick. A process handoff, GUI change, disabled inventory setting, or lost stationary safety gate cancels the wind-up rather than issuing a stale window click.
- If a live request changes its destination while the wind-up is in progress, the reaction beat is preserved but the latest destination replaces the old one.
- Added `InventorySwapTest.cancellingAWindowClickClearsItsWindup` and standalone real patch `patches/0003-cancel-stale-inventory-swap-windups.patch`; the patch applies cleanly against its parent with `git apply --check`.
- Validation: `javac` compilation of the dependency-free `Cadence`/`InventorySwap` utilities passed; a smoke test covered cancellation. `git diff --check` passed for source changes. `./gradlew test` again stopped before project compilation because Maven/Forge/Fabric/NeoForge artifact resolution hits TLS handshake failures and a NeoForge peer-authentication failure.
- Limitation: behavior-listener ordering is intentionally relied on (`PathingBehavior` asks before `InventoryBehavior` executes); if that registration order changes, update the current-tick request tracking together with it.

### Remaining review targets

- Verify the new inventory cancellation state with an in-game process handoff and an opened container while a swap is winding up.
- Continue the grounded movement acceleration/deceleration audit without delaying parkour, airborne, water, or emergency controls.

### Tempo (new concrete claim)

Task: Stabilize the followed-entity target while standing so two nearby matching entities do not make the gaze retarget on every scan/tick.
Subsystem: Follow target acquisition and standing look handoff.
Files: `src/main/java/baritone/process/FollowProcess.java`; tests/docs as needed.
Status: Claimed; researching target-lock hysteresis.
Started: 2026-10-08.
Dependencies: Existing `LookBehavior` reaction/aim dynamics and `FollowProgress`; no pathing-cost changes.
Notes: `regardWhileStanding` currently chooses the nearest cached entity every tick. A pair of close targets can swap nearest order with tiny movement, causing repeated large target changes. Lock the standing target until it is no longer followable, while retaining the existing nearest selection for initial acquisition.
