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
Status: Claimed; implementing after audit.
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
