# Consolidated engineering roadmap — proposal for approval

Author/consolidator: **QuartzHeron**. Incorporates source-checked inherited findings and Turnstone's independent validation and pitch traces. **Shared proposal, not an assertion of unanimous team agreement or implementation approval.** Product baseline `f1058fa8`; no product change is made by this document.

Evidence labels and exact call-path conclusions: [capability audit](capability-audit.md). Executed results and limitations: [validation](validation.md). Identity proposal: [identity](identity.md). Coordination/continuous execution: [workflow](workflow.md).

## Priority, sequence and scope

| Priority | Objective | Initial tranche |
|---|---|---|
| **P0 correctness/reliability** | Preserve useful pathfinding; close unsafe ownership/cleanup boundaries; make releases verifiable | CI capacity/source-release lineage; guarded inventory cleanup; temporary goal/failure ownership; sprint/fall/parkour safety trials |
| **P1 survival/threat** | Optional, deterministic awareness and least-disruptive safe response | Perception/classification → safe suspend/avoid/resume → separately consented restricted self-defence; existing resource guards retained |
| **P2 humanlike movement/interaction** | Correct context and value shape rather than adding more randomness | Existing visible-camera profile; pitch-retention gate/ownership diagnosis and minimal fix; circular smoothing; safe damage/interest gaze |
| **P3 broader autonomy** | Bounded recovery/resources/checkpoints with evidence, not another competing scheduler | Task progress/retry budget; explicit prerequisite/resource management; only then approved durable job recovery |
| **P4 identity/release** | Clear independent identity without breaking Baritone compatibility or attribution | Wayquill cosmetic/provenance stage → verified artifact/distribution aliases → deferred major compatibility changes |
| **G cross-cutting Gauntlet** | Reproducible contracts/scenarios, meaningful review and remote durability | Runs throughout every tranche; never replaces source/runtime reasoning with write-count tests alone |

**Recommended first implementation milestone:** G-01 + P0-01..04 + P2-00/P2-01, and P1-01/P1-02 only after P0-03's resume contract. This gives reliable ownership and optional avoidance before attacking anything. It does not implement a general fighter, auto-hunting, PvP, ranged combat, a package/mod-ID migration or LLM-driven real-time decisions.

P1-03 (restricted DEFEND) and P3-02 (durable jobs/new resource actions) are explicit additional approval scopes, not silently implied by “fix orientation.” P4 is independently approvable/deferable. Once a tranche is approved, agents execute its queued tasks through Gauntlet cycles continuously; they do not ask permission again for every unchanged approved task or stop after one patch.

```text
G-01 runs throughout
P0-01 (operator CI capacity) ───────────────> release gates
P0-02 cleanup ──> P0-03 ownership/resume ──> P1-02 AVOID ──> P1-03 DEFEND (extra scope)
                       └─────────────────> P3-01 bounded recovery ──> P3-02 jobs/resources (extra scope)
P1-01 perception ─────────────────────────> P1-02 / P1-03 / P2-03 safe interest
P2-00 existing profile ──> P2-01 pitch evidence/minimal fix ──> further movement refinements
P0-04 sprint/edge trials ─────────────────> any movement/humanisation changes
P2-02 optional smoothing may run independently under its look-file reservation
P4-01 cosmetic identity ──> P4-02 compatible distributions (name approval required)
```

Complexity: **S** ≈ half to two focused engineering days; **M** ≈ two to five; **L** ≈ one to two weeks; **XL** needs milestones and re-estimation. These are uncertainty bands, not autonomous completion promises or a substitute for evidence. Approval delays, environment downloads and unavailable peers are excluded.

## Common completion definition (applies to every card)

A significant implementation task is DONE only after: accepted scoped claim and design; exact source/callers/settings/tests inspected; a regression or requirement test that can fail for the intended reason; focused checks plus full suite/build as relevant; runtime scenarios for wiring; meaningful independent review with findings addressed; source/test/doc changes in a logical, correctly authored commit; a unique numbered **real** standalone patch generated from that commit and apply-checked at its actual parent; fetched/reconciled concurrent changes; push; independent remote hash/ancestry verification; updated Chat/Active Agents/task/patch records and handoff. Fetch before selecting the next free patch number (currently 0049 is the last inherited numbered patch). Planning-only documents do **not** need fabricated implementation patches.

Do not weaken assertions, count a skipped run as pass, rubber-stamp a peer, move public tags, force-push/rewrite shared history, or treat a rejected push/temporary build failure as a stop condition. An exact environment/operator blocker is recorded while independent approved work continues. Cross-cutting budgets below are proposed acceptance targets until measured on named hardware.

## P0 cards

### P0-01 — Restore trustworthy CI and source-aligned release validation

- **Evidence/classification:** **T/S** local suite/build pass; inspected GitLab jobs fail pre-run with `ci_quota_exceeded`; `v26.3` source tag lacks `901a999b`, `29b70536`, `87581e58`. Uploaded binaries have not been verified.
- **Components:** `.gitlab-ci.yml`, `.github/workflows/`, `buildSrc` distribution/ProGuard tasks, loader outputs, GitLab runner settings/release assets (operator-owned).
- **Current → desired:** locally green but remote quota-red/old tag → runnable independent pipeline and precisely identified, hashed, tested release source and assets.
- **Approach / alternative:** operator restores permitted quota or authorizes a suitable runner; retain current Java 25/jmods and tests; verify artifact SHA-256/license contents and loader boot, then create a **new** approved release tag. No compiler/dependency “fix” for a quota error; no silent tag movement or historical asset replacement.
- **Dependencies/conflicts:** operator capacity and release permissions; release finalisation waits for approved P0 repairs and supported-loader runtime checks. Exclusive CI/release metadata claim, separate from identity edits.
- **Proposed owner / size:** operator for quota/tag authorization; next release-validation volunteer (Swift only if they reconfirm); **M**, externally blocked.
- **Acceptance/tests:** actual GitLab job starts and succeeds with JUnit report/artifacts; source SHA and each asset hash recorded; Fabric/Forge/NeoForge/tweaker build, runtime launch where supported, scene/smoke checks; tag ancestors contain required fixes.
- **Performance/compatibility:** no A* or user-setting change; existing release URL/tag remains valid; version identifies Minecraft 26.3 separately from fork release revision.
- **Approval/completion:** operator runner/release decision plus common DoD; record CI as blocked rather than green until capacity and successful execution exist.

### P0-02 — Preserve cleanup ownership for an already-begun Elytra inventory transaction

- **Evidence/classification:** **S risk/U runtime scenario**: `InventoryTransaction#finishNow(false)` preserves a begun queue; `ElytraBehavior#finishTheSwapBeforeGoing` ignores that return; `ElytraProcess#onLostControl` releases its owner. Ordinary disconnect smoke passes but does not cover a foreign menu mid-swap.
- **Components:** `utils/InventoryTransaction.java`, `behavior/InventoryBehavior.java`, `process/elytra/ElytraBehavior.java`, `process/ElytraProcess.java`; existing `InventoryTransactionTest` and a proposed lifecycle fixture.
- **Current → desired:** menu-blocked unfinished clicks can lose their ticking owner → completion/cancellation remains explicitly owned and reported without stale-window clicks or orphan cursor state.
- **Approach / alternative:** first reproduce the combined queue/window/owner transition; transfer only a begun valid transaction to the existing client-thread inventory owner, or retain a bounded teardown owner until its original player inventory is available. Scope by player/world/connection/menu generation. Reject blindly draining a foreign menu, arbitrary force-closing a user GUI, dropping a half-applied queue or introducing another inventory controller.
- **Dependencies/conflicts:** reserve InventoryBehavior/Elytra files jointly; conflicts with resource/weapon integration. Finish before new threat interrupts increase teardown frequency.
- **Proposed owner / size:** Redshank if reconfirmed, otherwise unassigned inventory volunteer; independent reviewer Turnstone/next volunteer; **M**.
- **Acceptance/tests:** all begin/not-begin × menu open/closed × cancel/world/logout boundaries; changed cursor/slot or server-rejected click cannot be blindly replayed just because menu ID returns; only correct-window clicks; no duplicate items; valid same-world cleanup survives owner exit; different-world/login never replays old clicks; explicit bounded unresolved-cleanup alert if no safe window returns.
- **Performance/compatibility:** existing cadence for active swaps retained; correctness may bypass cosmetic gaps at teardown; no extra network polling/thread; `allowInventory=false` cannot erase already-started cleanup obligations.
- **Approval/completion:** initial engineering approval and evidence of the combined scenario; if reproduction disproves the risk, close with reviewed evidence rather than force a code change. Otherwise common DoD.

### P0-03 — Formalise temporary ownership, failure provenance and safe resume

- **Evidence/classification:** **S** existing temporary priority/pause seam **and last-winner failure guard** (`PathingControlManager:199`), captured calc goal/plan locks/cancellation/orphan-start checks (`PathingBehavior:473-557`). **H/U** late queued event/replacement timing; **M/P** universal fresh-goal resume contract. Do not describe all failure ownership as missing; immediate cross-owner protection already exists.
- **Components:** `utils/PathingControlManager.java`, `behavior/PathingBehavior.java`, `pathing/path/PathExecutor.java`, `api/process/IBaritoneProcess.java`/`PathingCommandType.java`, selected original processes and inventory/input/look boundary checks.
- **Current → desired:** existing control/last-winner/calc-cancellation protections → structural requester-aware async failure and tested safe BRACE/FLEE resume; minimal internal metadata, no competing scheduler. BRACE retains original path; FLEE safely discards it, and re-plan after return is expected normal operation.
- **Approach / alternative:** document/test pause-only and existing failure guard first; test late async result/event dispatch and replacing-goal resume. Enforce a minimal internal requester/task/world/calc ticket or per-requester failure channel before FLEE; stamps originate at calculation request, not event-dispatch tick. Keep public PathEvent/API signatures and existing last-winner/cancellation/orphan-start protections. Adversarial fixtures must prove late delivery cannot poison another process, legitimate original failure survives BRACE correctly, and normal re-plan is not failure; then fresh-goal revalidation for temporary evasion. Prefer internal/additive contracts; default interface hooks only if genuinely needed and reviewed for plugin implementors. Do not serialize/reuse old path executors or reset every process wholesale.
- **Dependencies/conflicts:** P0-02; shared Baritone/Settings registration owned by one integrator. Serial lock over arbitration/pathing files; Nightjar look/parkour reservations respected.
- **Proposed owner / size:** QuartzHeron volunteering after approval; Turnstone independent contract reviewer; **L**.
- **Acceptance/tests:** mining/farm/build/follow/explore → temporary pause → same owner/valid fresh task; existing immediate failure guard unchanged; failure queued before/after tick and late worker/replacing-goal cases cannot be lost or delivered as original `calcFailed`; nested user pause/Backfill/InventoryPauser remains authoritative; temporary response clears itself when `cancelEverything` permits temporary activity; goal changes cancel old tokens; user cancel/death/world change prevents resume/late async result; air/fall/parkour waits until safe physical boundary; flight stays with flight safety owner.
- **Performance/compatibility:** bounded O(1) owner metadata; no search-core change, no public method removal, no additional periodic controller; OFF mode preserves legacy goal/input behaviour.
- **Approval/completion:** approved resume contract before P1 response wiring, independent architecture review and common DoD.

### P0-04 — Validate sprint wind-up/hangover and dangerous movement handoffs

- **Evidence/classification:** **S/T** wind-up already excludes Parkour/Ascend/Descend/Fall/Pillar/vertical; **U** launch/edge/hangover transitions. Nightjar announced parkour work; this is a validation reservation, not a duplicate implementation.
- **Components:** `PathExecutor.java:597-654`, `pathing/movement/SprintWindup.java`, `SprintWindupTest`, `HeadFirst`, movement parkour/fall/flight transitions and headless scenarios.
- **Current → desired:** existing safe exclusions → measured proof that presentation timing never withholds critical controls at a take-off/landing/edge.
- **Approach / alternative:** coordinate with Nightjar/reclaim only after explicit recovery; trace jump/sprint keys, position and owner through short-run-up and chained hazards. Fix only a reproduced edge using the existing machine; no global key delay or second sprint controller.
- **Dependencies/conflicts:** look/path executor/movement reservation; baseline G-01; informs P0-03 and P2 work.
- **Proposed owner / size:** Nightjar if reconfirmed; Turnstone/next scenario volunteer for independent trials; **S–M**.
- **Acceptance/tests:** standing/short approach/chained parkour, sprint disable, low hunger, underwater, fall and Elytra launch; legal critical sprint takes effect immediately where `mayWait=false`; no inherited parkour/bridge regression; seed/property tests plus real client.
- **Performance/compatibility:** same A* movement cost model and existing timing ranges unless a validated mismatch requires a scoped correction; no added delay on critical paths.
- **Approval/completion:** validation may proceed during planning; any product fix requires approval and common DoD. Passing evidence is a valid closure without a manufactured patch.

## P1 cards

### P1-01 — Bounded perception and conservative pure threat policy

- **Evidence/classification:** **S** separate Look/Avoidance/Follow entity scans; **M/P** unified classifier/risk snapshot. Neutral state is client/version-limited; flinch culprit is not authoritative.
- **Components:** current Look/Avoidance/Follow consumers; proposed `utils/threat` pure snapshot/policy helpers and tests; existing Settings and event registration via the integrator. Proposed paths/classes are **new**, not claimed existing APIs.
- **Current → desired:** disjoint visual/path sensors → stable scoped observations with passive/hostile/stateful-neutral/unknown distinctions and confidence, health/equipment/hunger/multiple-threat context.
- **Approach / alternative:** client-thread sampling, immutable snapshots, deterministic policy fixtures; foundation exposes only implemented OFF/AVOID, with DEFEND added in its own approved tranche. Use actual 26.3 observable APIs; ServerLevel-only anger calls forbidden. Unknowns avoid/alert, never authorize an attack; do not add an LLM or background live-entity access.
- **Dependencies/conflicts:** perception helpers can proceed in parallel with P0; owner-aware consumers wait for P0-03. Coordinate replacements, not duplicate scans/controllers; look wiring remains look-owner territory.
- **Proposed owner / size:** unassigned perception volunteer; QuartzHeron integration, Turnstone adversarial review if accepted; **L**.
- **Acceptance/tests:** animal/hostile/neutral-state/unknown-mod entity/player/pet; friendly bystander during environmental/projectile damage; dead/despawned/unloaded entity and ID reuse; crowds and low resources; identical snapshot/config always yields identical decision.
- **Performance/compatibility:** proposed scan ≤5 Hz on owned tasks, ≤4 expensive candidate LOS checks/tick with rotating coverage; cheap current-threat/damage checks each tick; p95 added tick cost <1 ms at 100 living entities on declared hardware; OFF default and no idle takeover.
- **Approval/completion:** initial survival-awareness scope approval; classifier table and budget reviewed; common DoD, with measured rather than assumed performance.

### P1-02 — Optional safe AVOID interrupt and verified original-task resume

- **Evidence/classification:** **M/P** integrated ground threat response; existing Avoidance/Favoring and temporary process contracts are available.
- **Components:** approved perception/policy helpers; proposed temporary response process; existing `PathingControlManager`, PathingBehavior/goals, `Avoidance`/`Favoring`, input/look/inventory ownership; Settings/Baritone registration.
- **Current → desired:** no automatic task defence → least-disruptive policy: ignore harmless observation, safe pause/alert for uncertainty, bounded safe-ground evasion for a real danger, then fresh task revalidation/resume.
- **Approach / alternative:** label each response **BRACE** (REQUEST_PAUSE, path retained/no travel) or **FLEE** (normal evasion goal at safe boundary, original path cancelled/state retained, fresh re-plan is normal not failure). Reuse normal goals/path calculation with approved temporary owner; hysteresis, encounter deadline and limited replans. Never kill a blocking animal, stop parkour midair, replace the flight solver or repeatedly oscillate between fight/task. Flight initially alerts/uses approved existing emergency-landing seam, not ground evasion.
- **Dependencies/conflicts:** P0-02/P0-03/P0-04 + P1-01; integrator owns shared Settings/Baritone; response and original-process file changes serialized.
- **Proposed owner / size:** QuartzHeron volunteering after approval; independent scenario reviewer unassigned; **L**.
- **Acceptance/tests:** attacked mining/building/farming/follow/explore; one and many hostiles; critical health/poor footing; temporary path failure, blocked escape, entity gone, user cancel/new goal/world replacement; only valid original task resumes; no passive/player attack packets; repeated failures terminate with a reason.
- **Performance/compatibility:** proposed `threatResponse=OFF` default; initial implemented enum OFF/AVOID, DEFEND added only in its approved completed tranche; no settings proliferation. Ground-only priority −0.5 is proposed below manual pause/Elytra 0, Backfill 5 and InventoryPauser 5.1; defer with an alert rather than steal their inputs. OFF differential runs preserve original goals/keys; live costs affect only opted-in response, A* core untouched.
- **Approval/completion:** AVOID approval plus accepted P0 resume contract, hostile runtime evidence, independent review and common DoD.

### P1-03 — Restricted, optional ground-melee DEFEND MVP

- **Evidence/classification:** **M/P**, no current attack loop. This is substantial new scope, not an orientation bugfix.
- **Components:** approved policy/response; narrow vanilla player-controller attack adapter; existing LookBehavior and staged inventory owner; item-aware entity-ray/reach/LOS/cooldown tests; new proposed combat fixtures. Do not misuse block-only RayTraceUtils.
- **Current → desired:** no autonomous combat → only an explicitly opted-in, bounded self-defence encounter against one unambiguous allowed hostile on safe ground, with suitable equipment/health; otherwise AVOID/alert.
- **Approach / alternative:** **five mandatory bounds:** OFF default/no new default-on flags; exhaustive deny-by-default allowed-hostile/visible/live policy (never players/pets/passive/unknown neutrals or visual damage guesses); finite BRACE/FLEE encounter and valid original-goal return with normal re-plan; no screen/container/queued transaction/unsettled hand and **MVP uses already-held suitable weapon without slot/inventory ownership**; structural P0-03 failure attribution lands first. Stable target/connection identity; precise existing aim owner; this version's vanilla item-aware attack-range and cooldown APIs; no persistent attack key, packet spam, unrestricted pursuit, PvP, animal hunting, pet aggression, uncertain neutral retaliation, creeper melee, ranged combat or inventory auto-crafting. Clear queued actions on every boundary.
- **Dependencies/conflicts:** P1-01/P1-02 and resource prerequisites P1-04; independent combat design review; look/input/controller allowlist changes require explicit explanation, never bypassing guards to make tests pass.
- **Proposed owner / size:** unassigned combat volunteer with vanilla 26.3 client experience; independent safety reviewer not the author; **XL**, split selection/aim/action/outcome/runtime milestones.
- **Acceptance/tests:** one controlled allowed zombie encounter with an already-held ready suitable weapon; no screen/container/transaction/hotbar mutation during suspension, no action with null player/changed world/user pause; cooldown and entity occlusion; neutral/passive/player/pet deny cases; crowd/low-health/unequipped hazards select AVOID; target disappears/ID reused; capped failed attacks and encounter duration; observed outcome not falsely called authoritative hit attribution; all prior tasks resume or bounded-fail safely.
- **Performance/compatibility:** DEFEND remains opt-in, OFF default; no network service/LLM/dependency; no unchecked literal old-version reach range; preserve block precision and vanilla attack semantics.
- **Approval/completion:** **separate explicit DEFEND scope approval**; reviewed species/risk/timeout table and runtime safety evidence plus common DoD. Do not enable DEFEND by default when merely approving perception.

### P1-04 — Preserve survival guards and expose task resource prerequisites

- **Evidence/classification:** **S** sprint food >6, item-saver tools, water/fall constraints and Elytra durability/rocket landing; **M/P** general health-aware prerequisites/automatic ground eating.
- **Components:** `CalculationContext`, `ToolSet`, InventoryBehavior, Mine/Builder/Elytra processes and proposed policy requirement records.
- **Current → desired:** individual guards → explicit actionable preconditions and low-resource stop/alert; no false claim of full auto-food/healing.
- **Approach / alternative:** reuse inventory/tool selection and existing settings; test required tool/blocks/food/flight supplies at task entry and safe checkpoints. Initial tranche stops/alerts instead of silently crafting, eating, looting or switching away a needed task item.
- **Dependencies/conflicts:** P0 inventory/ownership and P1 policy; reserve shared inventory/tool files; full new resource actions deferred to P3-02 approval.
- **Proposed owner / size:** inventory/survival volunteer, Redshank if reconfirmed; **M**.
- **Acceptance/tests:** food 6/7 boundary; item-saver threshold; missing/changed tool/block stack; low durability/rockets; no repeated failed swap or use; response cannot consume an unavailable item or strand task inventory state.
- **Performance/compatibility:** cheap checkpoint checks, no every-tick full inventory search/new resource controller; legacy settings/defaults retained; resource policy OFF leaves old behaviour.
- **Approval/completion:** prerequisite/alert scope approval and common DoD; automatic eating/healing is not implied.

## P2 cards

### P2-00 — Document/test the already-existing visible-bot camera profile

- **Evidence/classification:** **T/S** free-look A/B reproduces local walking/mining split. This is an existing option, not missing aiming functionality.
- **Components:** existing Settings/freeLook/blockFreeLook/remainWithExistingLookDirection, LookBehavior PRE/POST; user docs and camera scenario ledger.
- **Current → desired:** confusing local/observer expectations → clear manual-free-look versus bot-visible camera profiles with truthful behaviour.
- **Approach / alternative:** demonstrate `freeLook=false` with existing settings; document precise mining and context-aware strafe/corner/flight exceptions. No blanket `setYaw` loop, new setting alias, always-forward rule or forced default change.
- **Dependencies/conflicts:** look/documentation owner; G-01 observer/local trace instruments; P2-01 for state-retention quality, not for the basic profile to work.
- **Proposed owner / size:** QuartzHeron docs, Turnstone independent scenario review; **S**.
- **Acceptance/tests:** local F3 and server trace recorded together under both modes; mining/navigation/chat/menu/path-end/user takeover; old setting files and user mouse intent preserved; remote rendering separately labelled.
- **Performance/compatibility:** no production tick overhead; current default stays `freeLook=true` unless the operator explicitly chooses otherwise.
- **Approval/completion:** documentation of actual behaviour allowed now; changing defaults requires separate camera decision. Published reviewed docs/runtime evidence complete this card without an implementation patch.

### P2-01 — Diagnose and minimally fix bot-owned free-pitch retention

- **Evidence/classification:** **Peer-T** fall/goal retention and controls in Turnstone's traces; **H** raw-vs-processed equality guard, not a uniquely established writer. Existing PitchWander already relaxes near level; forced manual idle pitch is not automatically defective.
- **Components:** `LookBehavior#humanStep/#settledGaze/#AbstractAimProcessor`, `MovementHelper` raw echo target, PitchWander/PitchWanderTest, movement fall/idle/handoff states and G-01 trace.
- **Current → desired:** a steep bot-generated angle can persist into plain movement → free bot-owned dry-ground gaze relaxes smoothly toward the existing bounded preference; exact block/swim/flight/user angles remain untouched.
- **Approach / alternative:** capture raw target versus processed tremor/quantised target and guard execution, plus menu/owner/free-look state. Prefer checking semantic raw target intent or an internal explicit free-pitch intent over an equality test on noisy output. No second smoothing machine, rewriting a correct helper or hard reset to zero.
- **Dependencies/conflicts:** Nightjar look reservation reconfirm/recover; P0 ownership and G trace; request full run-3/4 traces/settings and moving continuation after forced angle. Look-file edits serialized with P2-02/03.
- **Proposed owner / size:** Nightjar if reconfirmed, otherwise look-owner volunteer; Turnstone repeats scenarios and reviews; **M** with root-cause uncertainty.
- **Acceptance/tests:** level/control, fall→flat, mine→walk, target handoff, forced angle during substantial remaining walk, idle with/without owned job; confirm eligible PitchWander progression and no one-tick 90→0 snap introduced; block hit/face precision, water depth, Elytra attitude and manual view preservation unchanged.
- **Performance/compatibility:** reuse existing 1°/tick settle/preference bounds and AimDynamics limits; no added randomness/delay/settings; camera-mode contract retained.
- **Approval/completion:** scoped product-fix approval and confirmed gate/owner cause first; addressed independent objections plus common DoD. Historical single-tick snap remains open unless actually reproduced/explained.

### P2-02 — Make optional camera yaw smoothing wrap-safe

- **Evidence/classification:** **S/T expression** inherited arithmetic average maps 179/−179 to 0; optional smoothLook default is off; live seam rendering **U**.
- **Components:** `LookBehavior` CLIENT smoothYawBuffer branch, Rotation normalisation, existing camera/aim tests and proposed seam-average property test.
- **Current → desired:** average across ±180 can choose the opposite compass direction → wrap-safe, continuous camera mean with a defined antipodal fallback.
- **Approach / alternative:** small unwrap-around-reference or circular mean using existing math; retain previous/reference direction for ambiguous cancellation. Avoid added dependency, controller or repeated normalization that changes the packet-facing aim.
- **Dependencies/conflicts:** look-file lock after P2-01 or independently agreed patch order; smoothLook/elytraSmoothLook compatibility review.
- **Proposed owner / size:** unassigned look math volunteer; independent reviewer not author; **S**.
- **Acceptance/tests:** ±180 seam, full-turn equivalents, reversal/antipodal, empty/single buffer, block precise aim and optional Elytra camera average; pure property cases and client seam capture; no NaN/opposite-direction spike.
- **Performance/compatibility:** O(existing buffer length), default-off semantics and server aim unchanged; no API/config rename.
- **Approval/completion:** initial scoped correctness approval; common DoD, with live optional-mode limits named if runtime unavailable.

### P2-03 — Safe contextual visual interest and damage reflex

- **Evidence/classification:** **S** guessed player/Enemy culprit, no moving pitch clamp; **H** neutral gaze provocation and historic snap attribution. Visual interest must not authorize attacks.
- **Components:** LookBehavior interest/damage candidates and interaction gates, `look/Flinch`, existing Flinch/WalkGlance/IdleGlance/fuzz tests; approved P1 classification consumer.
- **Current → desired:** a harmless bystander can attract damage gaze; permissive pitch/eyes can be inappropriate → bounded, uncertainty-labelled, non-provocative look with correct action handoff.
- **Approach / alternative:** retain visual purpose, exclude known provocative/friendly-uncertain targets as policy requires, bound moving pitch only where physically free, record candidate provenance; fade through existing mechanisms. Reject using hurtTime + closest player as “attacker,” fake misses/random hesitation or global pitch clamps that break bucket/swim/flight.
- **Dependencies/conflicts:** P1-01 and P2-01 diagnostics; look-file lock and Nightjar findings; no combat-target shortcut.
- **Proposed owner / size:** look owner plus independent perception reviewer; **M**.
- **Acceptance/tests:** fall/environmental damage near player, projectile with unknown source, LOS loss/dead target/world change, Enderman safe-interest scenario, mining/placement precision, moving versus stationary limits, no spontaneous PvP/animal attack.
- **Performance/compatibility:** existing scan radius/timing unless profiled need; no extra random draws/settings; optional presentation yields to safety/precision.
- **Approval/completion:** approve narrowed visual-safety scope; neutral-provocation outcome measured before calling it a runtime defect; common DoD.

## P3 cards

### P3-01 — Bounded task recovery and actionable no-progress diagnostics

- **Evidence/classification:** **S** movement timeout/off-path cancellation, mine blacklist/rescan, stable follow progress; **M/P** shared task-level retry/no-progress circuit breaker. A universal infinite loop has not been reproduced.
- **Components:** PathingBehavior/PathExecutor, MineProcess/FollowProcess/ExploreProcess, approved owner-generation record, bounded diagnostics; relevant existing progress tests and new fault fixtures.
- **Current → desired:** useful per-process retries → explicit failure provenance, progressing versus stalled task distinction and bounded safe failure for approved autonomy mode.
- **Approach / alternative:** scenario-first repeated identical-world failure budget with progress reset, cooldown and explicit reason; generation-tag asynchronous rescan/calc results. Preserve deliberately persistent follow semantics unless opt-in recovery policy says otherwise. No blanket timeout on all long builds or duplicate scheduler.
- **Dependencies/conflicts:** P0-03 and G-01; process file locks; async lifetime review; response retry budgets integrated rather than competing.
- **Proposed owner / size:** unassigned reliability volunteer; QuartzHeron contract integration; **M–L**.
- **Acceptance/tests:** unreachable goal, inaccessible followed entity, unchanged blocked resource, invalid next path, late scan result after new task, reconnect/world switch; bounded attempts/notifications; progress resets budget; no accidental termination of healthy long job; no fight/resume oscillation.
- **Performance/compatibility:** counters/rolling progress metadata bounded; no full cached-world scan each tick; legacy persistent behaviour unchanged while disabled.
- **Approval/completion:** approve task-recovery semantics/config first; evidence-defined budgets, independent review and common DoD.

### P3-02 — Resource actions and durable job checkpoints, one capability at a time

- **Evidence/classification:** **M/P/X** broad resource planner/persistent resumable jobs absent; existing task and inventory abstractions are usable. This is larger new scope.
- **Components:** existing process requirements/inventory owner plus proposed versioned task checkpoint records, settings/state handling and opt-in resource subtask; no replacement of all processes.
- **Current → desired:** user supplies resources/restarts jobs → explicit bounded acquisition/eating action or resumable job objective with operator-visible prerequisites and consent.
- **Approach / alternative:** first pick one approved resource action or one process checkpoint, not both a generic planner and every task; snapshot objective/progress/world identity, never executor/entity pointers; revalidate inventory/geometry/connection before resume. No automatic cross-server resumption or implicit animal hunting/PvP.
- **Dependencies/conflicts:** P0-03/P1-04/P3-01; state schema/identity migration coordinated; inventory and API integration serialized.
- **Proposed owner / size:** unassigned autonomy volunteer; **L–XL**, discovery/design milestone before coding.
- **Acceptance/tests:** schema version/old state roundtrip, crash/write interruption, task cancellation, world identity mismatch, missing item/food/action denied, task prerequisite changed, repeated acquisition failure; deterministic bounded terminate/alert and no silent lost original task.
- **Performance/compatibility:** checkpoints at safe milestones, atomic bounded local persistence; no every-tick disk I/O/new dependency/LLM requirement; retained legacy state paths until migration consent.
- **Approval/completion:** **separate explicit resource-action/checkpoint scope and schema approval**, independent architecture review and capability-sized DoD. Full survival/hunting/planning remains deferred until separately specified.

## P4 cards

### P4-01 — Apply approved cosmetic Wayquill identity with honest provenance

- **Evidence/classification:** **S research / P application**; [identity](identity.md) ranks six candidates and recommends **Wayquill**, not an applied rename. Public checks are not legal clearance.
- **Components:** user-facing README/docs/about/log display, supported-loader display names/descriptions and fork support URLs; LICENSE/upstream credits retained unchanged; repository slug only operator-owned.
- **Current → desired:** inherited name/support metadata obscures the fork → recognisable independently maintained product, explicitly Baritone-derived, without pretending combat/autonomy is implemented.
- **Approach / alternative:** staged display/provenance-only change, review exact allowlist of intended strings; preserve packages, IDs (`baritone` Fabric, `baritoe` Forge/NeoForge), commands, settings/state paths, attribution and upstream history. No global replacement or anime-license alteration.
- **Dependencies/conflicts:** explicit name approval, namespace/legal review appropriate to distribution; exclusively claim metadata/docs during release changes; no product behaviour dependency on branding.
- **Proposed owner / size:** QuartzHeron if approved; independent compatibility reviewer Turnstone/next volunteer; **S–M**.
- **Acceptance/tests:** allowlisted diff, license hash unchanged, metadata JSON/TOML valid, all-loader build/boot, existing commands/settings/API/provider load, no duplicate mod; new text describes actual capabilities, old support/provenance links distinguished.
- **Performance/compatibility:** zero runtime-control change; unchanged binary/config/command identities; repository redirects verified before changing clone URLs.
- **Approval/completion:** **separate Wayquill application approval** and common DoD. Proposed logo/palette/module labels do not count as produced assets.

### P4-02 — Compatible distributions, release aliases and staged migration/rollback

- **Evidence/classification:** **S** artifact/group/provider/mixin/ProGuard/state identities are compatibility surfaces; **P** staged distribution aliases. Full public package/mod-ID rename is deferred.
- **Components:** Gradle/root artifact naming, buildSrc distribution/ProGuard, CI release links, loader manifests, Maven/Modrinth/operator namespaces, compatibility docs and verified rollback manifest.
- **Current → desired:** old metadata/artifact references → approved new branding/artifact names with legacy aliases and source/asset compatibility proof.
- **Approach / alternative:** stage 2 produces one underlying mod jar with optional old filename/download aliases of identical hash; do not install both aliases. Keep group/API/packages/loader IDs/config paths during the compatibility window; any later namespace/data migration is an explicit major transition with backups, dual-read policy and tested rollback, not approved by this card.
- **Dependencies/conflicts:** P4-01 and P0-01 release/CI validation; operator namespace/publishing rights; exclusive build/metadata file lock.
- **Proposed owner / size:** release volunteer after reconfirmation, operator owns registries/tags; **M**.
- **Acceptance/tests:** alias hashes equal, manifest/mixins/reflection/ProGuard survive each loader; old client/API/settings/cache/waypoint fixture works; source SHA and license/source offer match release; rollback uses original state without a destructive conversion; external registrations are actually confirmed, not inferred from zero search results.
- **Performance/compatibility:** single installed mod/provider; retained public names/state schema; no silent configuration reset or simultaneous renamed namespace duplicate.
- **Approval/completion:** explicit artifact/hosting stage approval, operator release permission, migration manifest reviewed and common DoD. Public mod-ID/package/data-path migration needs a further decision.

## G-01 — Test/observability contracts and continuous Gauntlet

- **Evidence/classification:** **T/S** 383 tests, full build, 6/6 scenes and smoke; Turnstone independently reproduced tests/patches/scenes. **S limit:** write-census tests cover known surfaces but do not prove shapes, rates, handoffs or hostile safety. No uncounted write is a reason to add another redundant census.
- **Components:** existing Camera/Key/Hand/Button/RandomSource guard tests, seeded helper/fuzz tests, `scripts/headless/{scenes,smoke,trace,drive,run}.sh/py`, bounded opt-in owner/rotation/policy trace and documentation.
- **Current → desired:** isolated source/helper guards and peaceful scenes → correlated, replayable task/rotation/input/inventory/threat traces and contract/property/scenario assertions.
- **Approach / alternative:** extend existing harness, not a new testing framework. Record raw requested/processed/effective/local/server angles, owner generation, keys, target identity/provenance, progress/retry/cancel reason; include settings/seed/commit/runtime environment. Separate teleport transitions, paused/manual states and frame-burst speed; opt-in bounded ring buffer/export, not unlimited chat/disk spam.
- **Dependencies/conflicts:** integration lock for instrumentation hooks; every task consumes its fixtures; independent reviewer challenges value/timing, not only file write counts.
- **Proposed owner / size:** Turnstone/next test volunteer if explicitly accepted; integration owner QuartzHeron; **M**, incremental across tranches.
- **Acceptance/tests:** before/after baseline 100 fixed non-threat route cases preserve goal success/path cost; all six peaceful scenes and smoke stay green; adversarial cases per task; mutation/property tests fail for intended boundary violation; imported snippets are not called full raw traces; full JUnit/build report and source/artifact hashes saved.
- **Performance/compatibility:** diagnostics off has no trace I/O and negligible bounded check; proposed OFF-mode route/throughput regression ≤5% on declared hardware (noise-controlled); threat policy p95 budget measured; search core unchanged. Warn and bound an overfilled trace, never grow indefinitely.
- **Approval/completion:** existing validation/documentation is authorized now; new persistent instrumentation or test/product hooks follow initial scope approval. Each finished increment has common DoD and returns to the next unowned approved priority, with durable recovery records.

## Explicitly deferred / rejected

- General PvP/ranged combat/automatic livestock hunting/full survival/crafting/LLM strategic planner: **X**, not in foundation or restricted DEFEND approval.
- Rewritten A*, blanket rewritten process framework, competing look/input/inventory/combat movement controllers: rejected absent a separately justified architectural need.
- More fake mistakes, randomness, reaction delays or universal sprint/key delays for their own sake: rejected; existing context-sensitive timing is substantial.
- Global Baritone→Wayquill string replacement, forced history/tag rewrite, license/author rewrite, silent public package/mod-ID/config-cache rename: rejected.
- Repeated quota-doomed CI retries or tests weakened to get a green badge: rejected; solve the actual capacity blocker.
