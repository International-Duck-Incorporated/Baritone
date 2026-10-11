# Baritone 26.3 — consolidated investigation and proposed engineering plan

**2026-10-11 · QuartzHeron · GitLab branch `26.3`**

## Decision-ready summary

1. **Keep the existing pathfinding and useful fork work.** Matching upstream `26.3` at `25111dae` is an ancestor of product baseline `f1058fa8`: 0 upstream-only / 281 fork-only commits. Inspected A* changes are logging, not a new solver. This fork already has substantial movement/look/interaction cadence, safe gates, tests and a real-client harness.
2. **The primary local walking/mining orientation discrepancy is reproduced and explained.** Default `freeLook=true` gives walking SERVER mode (local camera restored), while `blockFreeLook=false` gives mining CLIENT mode. Controlled eastward runs show local yaw 0 versus −89.7 when toggling free look, while server-facing motion follows the route. The existing `freeLook=false` option is the smallest visible-camera profile; do not create another look controller or silently change manual-control defaults.
3. **A separate pitch-retention issue has real peer evidence, not a uniquely confirmed root cause.** Turnstone's fall/goal traces and controls show retained steep pitch. Their bucket-only attribution was partly falsified. The actual PitchWander helper already returns toward a near-level preference; a noisy processed-versus-raw equality guard/ownership boundary needs instrumentation and a minimal fix. The historic one-tick snap and third-person body/head rendering remain open.
4. **Entity detection exists; autonomous entity combat does not in the inspected fork.** Look/Avoidance/Follow observe entities for distinct purposes; avoidance is optional broad-Mob path-cost bias, not a full classifier. A guessed damage-gaze culprit is not attack authorization. The explicit **30-answer combat table** covers passive/hostile/neutral classification, deliberate attacks, health/equipment/cooldown/multi-threat/task-resume and loop prevention.
5. **Build/test evidence is strong but scoped.** QuartzHeron executed **383 tests / 47 classes / zero failures/errors/skips**, full build exit 0, six scenes 6/6, disconnect smoke exit 0 and camera A/B. Turnstone independently reproduced tests, patches, ancestry/naming and scenes. Hostiles, full flight and all-loader runtime compatibility are not claimed validated. Current inspected CI failed pre-run because of **`ci_quota_exceeded`**; local green is not CI green.
6. **Implement reliability before fighting.** First milestone: Gauntlet, CI/release lineage, inventory cleanup ownership, safe temporary-goal/failure/resume contracts, sprint/air safety and pitch/profile correctness; then optional deterministic perception + AVOID. Restricted DEFEND, new resource actions/durable jobs and public namespace/state migration are additional explicitly gated scopes.
7. **Recommend one new product name: Wayquill.** Six consistently scored/researched candidates; strongest three **Wayquill (93), VoxelHelm (83), Trailwarden (76)**. Display/provenance-first branding preserves `baritone.*` API, Fabric `baritone`, Forge/NeoForge **`baritoe`**, commands/settings/cache paths, licences/authors/history. Research is not legal/registry/domain clearance.

**Status:** consolidated planning proposal, with substantive independent evidence review already incorporated. **The full new roadmap/combat/migration prose still needs independent review and human scope approval. No new product feature or applied rebrand is authorized or implemented by this package.** Authentication for publishing does not approve implementation.

## Read the package

| File | Contents |
|---|---|
| [capability-audit.md](capability-audit.md) | Architecture/current capabilities; detailed rotation/input/tick handoff; humanisation/sprint; **30 explicit entity/combat answers**; survival/threat design; task lifecycle/resources/risks |
| [roadmap.md](roadmap.md) | P0–P4 + Gauntlet dependency sequence; significant task cards with evidence, exact components, current/desired behaviour, alternatives, conflicts, proposed owners, complexity, acceptance/tests/budgets/compatibility and approval/completion gates |
| [identity.md](identity.md) | Six candidates, same weighted score, ranked strongest three and **Wayquill winner**; public conflicts/uncertainties, current-capability description/tagline/module convention/brand direction; full inventory, stages/gates and rollback |
| [validation.md](validation.md) | Exact executed results, baseline commands, A/B settings/instrument limits, independent peer results/corrections, CI quota and source-release gap, remaining runtime tests |
| [workflow.md](workflow.md) | Actual/proposed multi-agent allocation, integration locks, approval scopes, layered tests/performance, continuing Gauntlet, patch/author/push integrity, inactive recovery and redundant durable handoff |
| `evidence/` | Committed machine-readable baselines, build/test summaries, scene/smoke results, patch audit, naming queries/metadata, camera PNG/CSV, numerical/actual-helper probes and **Turnstone's separately authored evidence** |

Product baseline: **`f1058fa8345ff26a1c7e95cb1fd9f522bcc661d2`**. Initial shared evidence commit: `85eda9f8c4dd00f8e87a7fc6b2ba06f902bb51d8`. Controlled camera/CI ledger publication: `1f8c8496b988cf62da97e3fbc53e5cba75fcd414` (independently remote-verified). The current planning publication commit is identified by GitLab/AGENTS and the final status report; never infer remote delivery merely from the existence of local files.

## Evidence classifications and confirmed versus unconfirmed findings

| Category | Examples / decision consequence |
|---|---|
| **Runtime/test confirmed** | Existing baseline/build/peaceful scenes/smoke; local/server camera-mode split; peer fall/goal pitch retention within recorded windows |
| **Source confirmed** | Temporary priority/pause arbitration, food >6 sprint and item-saver/flight guards, broad optional Avoidance, guessed visual culprit, block-only raytrace, absent autonomous entity attack call path in inspected scope |
| **Confirmed expression-level defect** | Inherited optional arithmetic yaw average across 179/−179 gives opposite-side 0; default smoothing off, live optional-mode reproduction pending |
| **Source-confirmed risk, scenario unvalidated** | Begun inventory queue under a foreign menu can be preserved while its ticking Elytra owner is released; no duplicate/lost-item runtime outcome claimed |
| **Hypotheses requiring discriminating evidence** | Exact retained-pitch writer/processed equality gate; historic 89.9→0 snap; optional interest provoking neutral gaze; repeated task failure without shared budget |
| **Missing in inspected scope** | Unified conservative threat classifier, autonomous fight/flee task response, entity weapon/reach/cooldown loop, general ground health/food planner and universal durable task checkpointing |
| **Proposed, not implemented** | All new threat/response/checkpoint tasks and Wayquill application; no LLM/rewritten A*/competing controller requirement |
| **Operational blocker** | GitLab job `ci_quota_exceeded`; operator capacity required; `v26.3` source tag excludes three later fixes, uploaded asset contents not yet checked |

## 18-deliverable traceability index

These are coverage labels, not claims that proposed capabilities have been implemented.

| # | Required planning deliverable | Where / state |
|---:|---|---|
| D01 | Repository-specific architecture assessment | Capability audit §1; source-backed |
| D02 | Matching upstream and fork-delta/provenance | Validation baseline; measured ancestry and inspected solver delta |
| D03 | Verified capabilities, defects, gaps and uncertainty | Audit evidence vocabulary/tables + validation; explicit S/T/U/H/M/P/X distinctions |
| D04 | Actual yaw/pitch/camera/body/input/tick handoffs | Audit §2.1–2.3 + controlled A/B; body rendering still U |
| D05 | Existing humanisation/sprint wind-up review | Audit §2.4; roadmap P0-04/P2 cards; no duplicate delays |
| D06 | Task lifecycle/stuck/retry/cancel/recovery | Audit §5; P0-03/P3-01 contracts and limits |
| D07 | Resources/survival and existing guards | Audit §3/§5; P1-04; auto-food/health planner not invented |
| D08 | Nearby entity detection/type/classification | Audit **30-question table**, questions 1–6 and 25–26 |
| D09 | Deliberate combat, health/equipment/multi-threat/safety | Audit questions 7–24/28–30 and proposed design; not implemented |
| D10 | Safe suspend/goal replacement/revalidate/resume | Audit §4; P0-03/P1-02; failure ownership explicitly separated |
| D11 | Unified P0–P4 priorities and task cards | Roadmap; all significant cards have dependencies, conflicts, proposed owners, complexity and gates |
| D12 | Minimal implementation strategy/alternatives/constraints | Roadmap cards + audit design; existing owners/A* preserved, real-time LLM not required |
| D13 | Serious 5–8-candidate naming exercise | Identity **six** candidates, same weights, all scores/research |
| D14 | Ranked strongest three/one winner/reasons/conflicts | Identity: **Wayquill**, VoxelHelm, Trailwarden; conflict/legal uncertainty explicit |
| D15 | Name description/tagline/module/brand direction | Identity §2; conceptual direction, not produced visual assets or new modules |
| D16 | Complete rebrand inventory/migration/rollback | Identity §4–5; API/IDs/config/commands/loader/build/artifact/docs/CI/release/URLs/state/license/history |
| D17 | Multi-agent allocation/tests/Gauntlet/inactivity/handoff | Workflow + G-01; current facts versus proposed roles, meaningful review |
| D18 | Open questions/approval/delivery records | Workflow §6, decisions below and AGENTS; implementation/rebrand gate remains closed |

## 39-point planning-question coverage index

Audit-worded questions provide navigable coverage of the requested mission; they are not presented as verbatim quotations of the original prompt. Detailed answers and source anchors are in the linked documents, including the separate mandatory 30-combat-answer table.

| # | Question | Answer / source location |
|---:|---|---|
| 1 | Which repository/branch/baseline is authoritative? | GitLab fork, `26.3`, product `f1058fa8`; planning changes only. Validation baseline / AGENTS. |
| 2 | How does it differ from matching upstream? | Upstream `25111dae` ancestored, 0/281 divergence; substantial movement/look/hands/tests/harness, inspected A* remains logging-only delta. Validation / audit §1. |
| 3 | Which tasks are really implemented? | Mining, building, crop farming, following, exploring, getting/picking resources and Elytra processes/commands; not combat or general survival planner. Audit §1/§3. |
| 4 | What actually passed rather than merely exists? | 383 tests, full build, 6 scenes, teardown smoke, camera A/B; independent peer reproduction scopes named. Validation. |
| 5 | Which absent capabilities were searched/read? | No autonomous entity combat/health-aware ground coordination/universal checkpoints in inspected call paths; disjoint entity scans do exist. Audit §3/§5. |
| 6 | Why can walking look wrong locally while mining looks right? | SERVER versus CLIENT defaults and POST local restore, reproduced A/B; existing visible profile available. Audit §2.2. |
| 7 | Who pairs yaw and movement keys? | MovementHelper/MovementOption use head heading and grid/key pair; preserved look direction can deliberately strafe/back up. Audit §2.1. |
| 8 | Who owns pitch and precision? | LookBehavior/AimProcessor and exact movement/block/swim/flight targets; PitchWander only physically free context; retention gate still investigated. Audit §2. |
| 9 | Are local view, server yaw and body/head animation identical evidence? | No. F3 local versus server trace are distinct; actual remote rendered interpolation remains untested. Audit §2.1 / validation A/B. |
| 10 | Is block interaction a separate controller? | Same existing look owner with precise CLIENT/action gates; block helpers/controller execute interactions. Audit §2.1. |
| 11 | Does chatting select a conversational facing? | Chat command parser is not a look controller; screen/manual takeover matters; no dialogue-facing task established. Audit §2.3. |
| 12 | Is stale target ownership proved after path end? | Target clears each POST; indefinite target claim not established. Separate retained pitch and context handoff need raw/owner trace. Audit §2.3. |
| 13 | What humanisation already exists? | Aim easing/tempo, pitch/gaze/sway/glances/idle, dig/click/item/approach/landing/sprint/key/crouch/follow/work-order machinery; don't layer arbitrary mistakes. Audit §1/§2.4. |
| 14 | Does sprint wind-up already exempt hazards? | Yes, existing exclusions/tests; edge/hangover/flight scenario gaps remain. Audit §2.4 / P0-04. |
| 15 | Do green write-count tests prove natural/safe behaviour? | No, shapes/timing/intents and wiring need property/scenario evidence. Turnstone census incorporated with this limit. G-01 / workflow tests. |
| 16 | How are tasks activated and prioritised? | Registered IBaritoneProcess and existing priority/temporary manager; non-temporary replacement tears lower owners down. Audit §5 / P0-03. |
| 17 | What stuck detection exists? | Movement timeout/off-path cancellation, invalid-path discard/recalculation and process-specific handling; no universal success promise. Audit §5. |
| 18 | Are retries/recovery globally bounded? | Specific mine blacklist/rescan and movement guards exist; shared no-progress/task budget missing, unbounded runtime loop not assumed. P3-01. |
| 19 | Can current tasks be interrupted? | User pause/resume and temporary arbitration exist; no combat interrupt currently wired. Audit §3 Q27 / §5. |
| 20 | Can a replacing escape goal safely resume the old task? | Needs explicit owner/failure generation and fresh-goal/prerequisite/path revalidation; not blind executor restoration. P0-03/P1-02. |
| 21 | What are async/cancel/world/GUI risks? | Late task results, owner-released inventory queue and world identity must be tested; current stale one-click swap and null-player fixes retained. Audit §5 / P0 cards. |
| 22 | Does it already use food/tools/equipment constraints? | Food >6 sprint; item-saver tool durability; water/fall/flight guards. Those are not general eating/combat readiness. Audit §3/§5. |
| 23 | Does it automatically heal/eat/respawn/manage survival? | No general ground policy/task found; propose prerequisite/alert first and separately scoped resource actions. P1-04/P3-02. |
| 24 | Are nearby animals/monsters noticed? | Yes by particular Look/Avoidance/Follow consumers, not unified awareness. Audit Q1–3. |
| 25 | Are passive/hostile/neutral states reliably classified? | Partial type/special filters only; client/version uncertainty must fail closed. Audit Q4–6 and version constraints. |
| 26 | Does it intentionally attack animals or monsters? | No autonomous entity-attack loop/controller/command found; manual Minecraft/downstream actions are separate. Audit Q7–8/Q24. |
| 27 | Is a damage-gaze target a known attacker? | No, hurtTime plus visible bystander/type guess; never attack authorization. Audit Q9–11/P2-03. |
| 28 | Does it weigh multiple threats/fight/flee/ignore? | Not currently; cost spheres are not a full policy. Optional deterministic conservative policy proposed. Audit Q14–15 / P1 cards. |
| 29 | Does combat use health/armor/weapon/reach/LOS/cooldown? | No current combat loop; existing block/tool guards distinguished; actual 26.3 vanilla APIs checked for proposed adapter. Audit Q16–23. |
| 30 | How should combat/survival integrate with tasks? | Existing temporary process, safe handoff, single look/input/inventory owners, validated original-goal resume; OFF/AVOID first, restricted DEFEND separate. Audit §4 / roadmap. |
| 31 | How prevent stale targets/fight-resume oscillation/endless failure? | Scoped target generations, hysteresis/encounter budgets, bounded attempts, user/world cancellation and progress reset—proposed, not claimed present. Audit Q28–30/P0-03/P3-01. |
| 32 | What real-time/performance constraints apply? | No LLM/network in tick; bounded main-thread sampling/LOS and measured p95 budget; OFF-mode route cost/throughput regression tests. P1-01/G-01. |
| 33 | What build/version/API/loader compatibility is proven? | Java25/jmods/Gradle/full multi-loader build; only Fabric real-client checks here; packages/IDs/reflection protected, downstream/other boot still required. Validation / identity inventory. |
| 34 | What CI/docs/release defects remain? | Actual quota failure; source tag excludes three fixes, binaries not checked; stale historical notes indexed by current evidence, no destructive tag/author edits. P0-01 / validation. |
| 35 | What is the dependency-ordered roadmap? | P0 reliability → optional P1 threat → P2 shape/context → scoped P3 autonomy → separately P4 identity, G throughout; P2 pitch diagnosis brought forward. Roadmap. |
| 36 | Which name wins after serious research/scoring? | Six consistent candidates; **Wayquill 93** winner, VoxelHelm 83 fallback, Trailwarden 76 conflict-heavy third; no legal clearance claim. Identity. |
| 37 | How rebrand without breaking integrations/licences/state? | Display/provenance first; retain `baritone.*`, `baritone`/`baritoe`, commands/settings/state; aliases next, major migration deferred with backup/rollback; preserve licences/authors/history. Identity §4–5. |
| 38 | Who does what and how are checks/review/handoffs continuous? | Proposed roles/file locks, actual fresh agents, layered tests/Gauntlet, unique real patches, correct authors, fetch/reconcile/push/verify, inactive recovery and redundant handoff. Workflow. |
| 39 | What must the human approve and what is the next action? | Foundation scope, optional DEFEND/P3 capabilities, name stages/camera default, operator CI/hosting/release decisions; integrate independent full-plan review then claim next approved task. Decisions below / workflow §6. |

## Explicit approval request and remaining work

**Recommended engineering decision:** approve the foundation milestone (G-01, P0-01..04, P2-00/P2-01, P1-01/P1-02/P1-04), preserving defaults and enabling optional AVOID only by user policy. Consider P2-02/P2-03/P3-01 as the next reviewed tranche. Restricted DEFEND (P1-03), new resource/checkpoint actions (P3-02), and public namespace/state migration are separate scopes.

**Recommended identity decision:** approve **Wayquill Stage 1 display/provenance only**, or defer application while keeping this definite recommendation. Stage 2 artifact/hosting changes need explicit operator/compatibility approval; Stage 3 major technical rename is deferred. Keep `freeLook=true` default unless you explicitly want a visible-bot default.

**Operator blocker:** restore CI quota/runner capacity and authorize a new source-aligned release when validation is ready. Do not mistake the supplied publication credential for permission to change runner settings, tags/assets, features or brand.

**Still unverified:** full new-plan independent review; exact pitch retention/snap cause and full settings/owner traces; foreign-menu inventory cleanup; hostile/task-resume/cooldown/neutral scenarios; full flight; supported-loader/downstream runtime matrix; release asset contents; external brand registration/legal/domain checks. Each is assigned an evidence-driven gate, not swept into “done.”

## What changed / what did not

- QuartzHeron's changes: **AGENTS coordination and `docs/planning/2026-10-11/` planning/evidence only**. Turnstone's own committed review/scenario evidence is incorporated and remains attributed to them.
- Product `src`, build/loader configuration, harness implementation, public packages/IDs, settings/commands/state directories, licence files and inherited patch bodies have **not** been changed by QuartzHeron.
- **No new implementation patch**: existing 49 patches were audited; there is no fabricated feature/rebrand patch for planning prose.
- Latest remotely verified publication hash and clean/synchronized state must be reported after push/fetch/`ls-remote`; local documents alone are not the repository deliverable.

After explicit approved-scope decisions and substantive contract review, the team continues through the next unowned priority using the existing Gauntlet. Pending approval is a real gate, not a promise of invisible background implementation.
