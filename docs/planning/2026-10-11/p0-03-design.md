# P0-03 design for independent review — calculation requester lifetime

**QuartzHeron · 2026-10-11 · authorized foundation · design before structural wiring**

This is the concrete design proposed after human scope delegation recorded in `c879ee8c`. It is not implemented/validated simply because it is written. Preserve the existing manager last-winner guard, primary cancellation/exception suppression, safe movement pause, plan locks and orphan-start checks. Public `PathEvent`, `IBaritoneProcess`, `IPathingBehavior` and provider/package/mod-ID/state contracts remain unchanged.

## Source findings that constrain the fix

- `PathingBehavior:77,83-94,98-109` queues a bare PathEvent and drains both before and after path ticking; each drain overwrites one failure boolean. An empty next drain can erase an end-of-tick failure before any process observes it.
- Manager `executeProcesses:199` masks that boolean by previous winner, not calculation requester. A delayed transient failure can coincide with a resumed normal previous winner; source requester metadata is absent. The actual adversarial timing must be tested, not mislabeled already reproduced in a client.
- Calculation result callback `:510-555` runs on the executor under plan lock. Force cancel clears inProgress and permits a new search; the old callback currently unconditionally clears inProgress, so pointer lifetime must be checked before clearing/adopting. Do not change A* search/costs.
- `secretInternalSetGoalAndPath:262+` runs repeatedly. Mine returns a freshly constructed GoalComposite in `MineProcess:193` each tick. **Goal object identity must not be used as task lifetime** or every tick would invalidate useful work. Follow/explore goals may change as part of the same logical job.
- Mine's public `mine(quantity, filter):511+` resets its task state without a controller cancellation; CustomGoal `setGoal:59+` can replace an active goal. Merely tagging a process object misses same-process task replacement. Standard task starts need an internal lifetime notification, without a new mandatory public interface method.
- Pure BRACE uses REQUEST_PAUSE, keeping original path and job alive. FLEE replaces/cancels geometry at a safe boundary; returning asks the original process for a fresh goal and normal re-plan. Neither response may convert re-plan into a failure.

## Proposed minimal private mechanism

1. One narrow **pure internal calculation-lifetime helper** (proposed, not a new task scheduler) stores world/session epoch, requester identity generations and monotonically increasing request IDs. A ticket records requester identity, requester-generation, world/session generation and request ID. No live Minecraft reads occur in the worker's metadata checks.
2. Standard public job entrypoints notify lifetime change through an additive **protected internal BaritoneProcessHelper method**, implemented by existing Baritone/PathingBehavior internals. Touch only canonical starts: Mine `mine`, Farm `farm`, Builder canonical `build`, Follow canonical `follow` (pickup delegates), Explore `explore`, GetToBlock canonical start, CustomGoal `setGoal`/explicit new path request as appropriate, and Elytra path/reset where it requests a ground path. Exact delegation chains must be checked before choosing hooks, to avoid resetting on ordinary ticks/resume. Manager also invalidates on actual lost-control/inactivity and whole cancel; pause does not invalidate an unchanged original job.
3. A new request captures current process from existing manager's mostRecentInControl, and current client-thread world/session token. Calls with no identifiable requester must not invent one or deliver an automatic failure to an unrelated process. Diagnostics/public event can remain generic; process failure requires attributable ticket.
4. Each executor completion checks ticket/job lifetime under the existing plan lock before adopting/emitting. After callback, clear inProgress **only if it still refers to this pathfinder**, never a newer request. Cancellation invalidates the current request even if its failure has queued but not yet dispatched. World/whole-cancel invalidates all tickets; same-requester new task invalidates that requester's old tickets. No world-null teardown dereference.
5. Private queue envelopes carry event + optional calculation ticket. Public listeners still receive unchanged PathEvent enum. Bare CALC_FAILED must not authorize a process failure. Only a valid attributable primary failure is stored pending for its requester; cancellation/exception/next-path failure do not become primary failure.
6. Pending failures survive an empty drain and a legitimate BRACE pause, but are consumed **at most once** when that requester is eligible under the existing last-winner guard. New calculations for the same logical task must not erase a valid pending failure simply by increasing request ID. New task/cancel/world reset expires it; a different requester cannot read/consume it. Bound storage to registered/requesting process identities, not event history.

**Opaque third-party process boundary:** keep legacy signatures and controller behavior, attribute by actual requester identity, and observe registered activation/cancel/lost-control. A plugin that mutates a same-active process's private task without informing existing controller/helper cannot have that hidden mutation magically detected. Review whether an optional additive public lifecycle hook is required before promising universal plugin same-task restart safety. Do not add such an API or serialize process private fields without agreement; standard built-in starters can use the protected helper.

## Required falsifying tests before claiming correctness

| Case | Required result |
|---|---|
| Failure after tick then empty next drain | Not silently lost; eligible original requester sees it exactly once |
| Transient failure arrives after normal owner resumed | Normal owner never receives/consumes transient failure; mine blacklist unchanged |
| Valid original failure while BRACE paused | Remains pending until original is eligible; not attributed to pauser or lost due to re-plan/request serial alone |
| Same requester starts new task, including same/equivalent goal | Old failure/result cannot affect new task; normal same-job rescans don't reset lifetime |
| User/world/death cancel with null player | Tickets/pending actions expire; no null dereference or later replay |
| Forced old completion after newer request | Cannot adopt old path or clear newer inProgress pointer |
| Primary cancellation/exception and next-path failure | Existing distinctions preserved; not delivered as primary mining failure |
| Normal un-interrupted calculation and failure | Same path/cost/owner semantics; legitimate failure still reaches original process |
| Public event/API compatibility | Event enum/signatures unchanged; metadata envelopes remain private |

Pure helper tests are value/timing tests, not proof of Minecraft integration. Prefer an actual queue/manager integration fixture with controlled completion over source counts alone. Baritone construction depends on a live Minecraft game directory; no new mocking dependency or public production constructor solely for tests. A test-only reflective allocation/field fixture is an option if it can invoke actual methods without launching a client; label its scope honestly. Real Fabric scenes/smoke plus a transient/no-blacklist scenario must validate actual wiring after code review. Source guards complement, never replace, value/runtime checks.

## Locks, compatibility and gates

QuartzHeron holds arbitration/pathing + helper/fixture integration; coordinate one-line canonical starter hooks with any fresh owner of those files. No overlapping look/parkour edits, no second movement/rotation/task manager, no new real-time LLM/network or user settings. Ticket memory/lookup bounded and O(1) per ordinary call. Full unit suite, all-loader build, source/API/schema comparison, meaningful independent review and real numbered patch/remote verification are required before task completion.

**Requested decision from Turnstone/independent reviewer:** validate pending failure consumption under the last-winner guard; distinguish same-job rescan from canonical new-task notifications; challenge opaque-plugin lifecycle limits and callback/cancel lock order; require a test that fails against the actual old behavior. If this needs broader API architecture, return that affected scope to shared agreement rather than silently expanding the plan.

## Executed old-behaviour reproduction (G-01)

`evidence/PathFailureDispatchProbe.java` uses test-only reflective allocation/dynamic proxies to invoke the **actual current** PathingBehavior queue/drain and PathingControlManager. Controlled old completion yields resumedOriginalReceived=true; failure drain true is overwritten false by the next empty drain. Intended AssertionError/exit1 in `evidence/qh-path-failure-dispatch-probe.log`. These are actual-method state/dispatch findings, not a measured live A* worker or mine-blacklist outcome. No production test constructor/mocking dependency and no input/game action was added. Use this seam for falsifying integration tests rather than source-count-only assertions.
