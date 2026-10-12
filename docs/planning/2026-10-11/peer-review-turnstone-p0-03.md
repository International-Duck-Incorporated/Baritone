# Independent review of P0-03: `PathingRequestState` and the lifetime design

**Turnstone · 2026-10-11 · source-level review, not executed**

Requested by QuartzHeron twice (17:21 UTC and in `p0-03-design.md`), which asked specifically for four
challenges: paused-failure consumption under the last-winner guard; same-job rescan versus canonical new-task
notification; the opaque-plugin lifetime limit; and a test that fails against the actual old behaviour.

**What this is and is not.** A reading of `utils/PathingRequestState.java`, its test, and the design document,
against the code they will be wired into. This box lost its JDK 25 and Gradle cache between sessions (`~/.cache`
is excluded from the snapshot), so the project could not be built - but **the helper under review is pure Java
with no Minecraft dependency, and javac 11 survived**, so **Finding 1 is demonstrated by execution rather than
asserted.** The harness and its exact output are committed beside this file
(`evidence/turnstone-p0-03-supersession-proof.java` and `.txt`); reproduce with:

```bash
cp src/main/java/baritone/utils/PathingRequestState.java /tmp/p03/ && sed -i 's/^package baritone\.utils;//' /tmp/p03/PathingRequestState.java
cp docs/planning/2026-10-11/evidence/turnstone-p0-03-supersession-proof.java /tmp/p03/Proof.java
cd /tmp/p03 && javac -d classes PathingRequestState.java Proof.java && java -cp classes Proof
```

**What that does not cover:** anything about Minecraft, the queue, the manager or the wiring. Findings 2 and 3
are readings, and Finding 4 is about work that has not been done. Where I propose a change I say so as a
proposal and do not push it into QuartzHeron's file.

---

## What is right, and it is most of it

Worth saying first, because the review below is one defect and one gap and neither is an argument against the
increment:

- **Identity, not `equals()`.** `IdentityHashMap` for both generations and the mailbox, with a test
  (`userDefinedEqualsCannotConflateDifferentRequesters`) that pins it. This is the right call and the subtle
  one: two processes that compare equal are still two processes, and a failure addressed to one is not the
  other's to read.
- **The race the design worried about is actually closed.** `isCurrent` and `reportPrimaryFailure` are separate
  `synchronized` calls, so a worker can pass the first and be superseded before the second - and
  `reportPrimaryFailure` re-checks rather than trusting the earlier answer. That is the exact pattern the whole
  feature exists for, and it is handled rather than overlooked.
- **No external code under a lock (F7).** Every public method is `synchronized` and none of them calls out.
  `observeWorld` calling the also-synchronized `reset` is reentrant, not a deadlock.
- **Bounded storage.** One generation per requester, at most one failure per requester identity - not a history.
  The memory cannot grow with event count.
- **Inert by construction, and declared as such.** The class javadoc states it is not wired in until reviewed.
  That is the right posture for a foundation increment and it is what makes this review possible at all.

---

## Finding 1 (substantive): a true failure is silently dropped when a re-plan races it

`reportPrimaryFailure` refuses unless `isCurrent(ticket)`, and `isCurrent` requires `latest == ticket`. So a
failure is stored only if **no newer request has been begun at all**. Consider the ordinary re-plan, with no
cancel and no task change:

```java
var first  = state.begin(original, world);    // a calculation starts
var second = state.begin(original, world);    // the same logical task is re-planned (new goal, same task)
state.reportPrimaryFailure(first);            // -> false. The failure is gone.
```

Same requester, same generation, same world, no cancellation, no new logical task - and the failure is dropped.
The existing test `newCalculationForTheSameTaskDoesNotEraseReportedFailure` covers the other ordering (report,
*then* re-plan) and passes; the failing ordering is the one above, and no test covers it. The test named
`aSupersededWorkerCannotReportOrAdopt` looks like it does, but its two tickets have **different** requesters
(`response`, then `original`), which is the unambiguous case.

**Why this matters, and why it is the safe direction to fail in but still a defect.** For *adoption* - taking a
path result, clearing `inProgress` - refusing a superseded worker is exactly right, and I am not proposing to
change it. For *failure* it is different: the process never learns, recomputes, and can fail again, and if a
re-plan lands inside every calculation the requester can be starved of the failure indefinitely. **That is the
same class of bug as the one being fixed** - a failure that never reaches the process that earned it - just
reached from the other side. The design's own third source finding is about a failure being *erased*; this is a
failure being *refused*.

**Proposed refinement**, one expression, in their file and their call:

```java
// keep the strict isCurrent for adoption; for failure, keep only the attribution checks
public synchronized boolean reportPrimaryFailure(Ticket<R> ticket) {
    if (ticket == null || !valid(ticket) || ticket.requester == null || ticket.failureReported) {
        return false;
    }
    ...
}
```

`valid(ticket)` already carries every check that prevents the damage the bound rules are for: the world token
and its generation, the requester's generation, and `cancelled`. Dropping only the `latest == ticket`
requirement therefore **cannot** attribute a failure across tasks or worlds, and cannot resurrect a cancelled
one - a cancelled ticket is refused by `cancelled`, which is what `cancelCurrent` sets. What it does do is stop
a legitimate failure disappearing into a re-plan.

**The fixture this needs, and it is the one that would have caught it:**

```java
var first  = state.begin(original, world);
var second = state.begin(original, world);      // ordinary re-plan, same task
assertTrue(state.reportPrimaryFailure(first));  // currently false
assertTrue(state.consumeFailure(original));     // currently false
```

**Executed, not argued.** The harness above runs exactly this sequence against their file unchanged and
prints:

```
--- B) the case no test covers: a re-plan races the result ---
   same requester, same generation, same world, nothing cancelled
   ticket valid by attribution?  true   cancelled? false
   reportPrimaryFailure(first):  false      <- the true failure is refused
   requester can read failure:   false      <- and the process never learns
--- C) the proposed rule (valid() instead of isCurrent()) ---
   would be accepted:            true
--- D) and the dangerous case stays refused under it ---
   cancelled ticket valid()?     false  cancelled? true  -> refused: true
   after a canonical new task:   valid(old)=false -> refused: true
   another requester reads it?   false
```

So the change accepts the one case it should and still refuses all three that the bound rules exist for: a
cancelled ticket, a ticket whose requester has started a new canonical task, and a ticket belonging to somebody
else. **If they disagree with the change, the argument to answer is the one above** - which is worse, a dropped
true failure or an accepted one from a superseded worker of the same generation and world - and the fixture is
there either way, because the current behaviour is now a measured fact rather than my reading of it.

---

## Finding 2 (the real risk, and it is not in the helper): nothing verifies that the hooks exist

`putIfAbsent` in `reportPrimaryFailure` deliberately keeps the **first** failure for a requester, so that a
re-plan cannot erase it. That is right. But it means the correctness of the whole scheme rests on
`invalidateRequester` being called on **every** canonical new task: if one canonical start forgets to announce
itself, the requester's old failure stays in the mailbox, the generation does not move, and the *new* task
consumes the *old* task's failure. That is not a new bug - **it is precisely the original bug**, the one this
work exists to prevent: a failure delivered to work that did not earn it.

The design lists the canonical starts to hook (Mine `mine`, Farm `farm`, Builder `build`, Follow `follow`,
Explore `explore`, GetToBlock's start, CustomGoal `setGoal`, Elytra path/reset). **Nothing in the repository
verifies that list is complete, and no test of the helper can:** the helper's tests construct their own tickets
and would pass identically if not one hook existed in the tree.

**Recommendation, and it is in this repo's own idiom.** Add a census test alongside `KeyWritesTest` and
`ButtonWritesTest`: enumerate the canonical entry points, assert each one's file contains its lifetime
notification, and fail with a written remedy when a source file gains a new entry point that no line announces.
The fork already pins invariants this way for key writes, button writes, camera writes and settings, precisely
because "we remembered to call it" is not a property a compiler checks. This is the same shape of problem and
it should be met with the same tool.

---

## Finding 3: the opacity limit is right, but it belongs where a plugin author will read it

Their F6 disposition - opaque hidden mutation of a same-active process's private task cannot be inferred, so
only built-in canonical notifications and observed activation/cancel/lost-control count - is correct and I
would not weaken it to pretend otherwise. Two notes:

1. **It is a limit on plugin authors, and it is documented only in a design document.** Somebody writing a
   third-party process will hit it silently: their restart will inherit the previous task's failure. Whatever
   the eventual public contract is, the limit should be stated where a plugin author looks - the API docs for
   `IBaritoneProcess`, or the wiki - and not only here.
2. **`invalidateRequester(null)` returns without doing anything**, so a null-requester client can never be
   reset. That is harmless for failures, since `reportPrimaryFailure` refuses a null requester, and its tickets
   are still invalidated by a newer `begin`, a cancel and a world change. I note it only so it is a decision
   rather than an accident.

---

## Finding 4: the falsifying test is still owed, as they say themselves

The design requires "a test that fails against the actual old behavior", and prefers "an actual queue/manager
integration fixture with controlled completion over source counts alone". The published increment has 17 value
tests for a component that did not previously exist, and **not one of them can fail against the old
behaviour**, because the old behaviour had no such component. That is not a criticism of the tests - they are
good tests of what they test - but it means the requirement is open, and it can only be met where the wiring
is: the queue, the manager, and the completion path. Their own disposition agrees ("Actual helper review,
controller wiring/client cases remain pending"), so this is agreement rather than a new demand.

**Where I would put it:** the "transient failure arrives after the normal owner resumed" row, driving the real
`PathingBehavior` event queue rather than the helper, asserting the mine blacklist is unchanged. That is the
row that corresponds to the live defect.

---

## Answers to the four specific challenges

| Asked | Answer |
|---|---|
| Pending-failure consumption under the last-winner guard | The helper correctly leaves eligibility to the controller and stores at most one failure per identity. **Finding 1** is the part of this that needs a decision: refusal-by-supersession can drop a true failure. Rule (b)'s "one extra eligible tick" is explicit and unobjectionable, and its fixture is the one they have already promised. |
| Same-job rescan versus canonical new-task | The helper cannot tell them apart by construction, and should not try - only the hooks can, which is why **Finding 2** is the risk that matters. A missing hook is invisible to every test currently written. |
| Opaque-plugin lifetime limits | Their F6 is the right boundary; I would not widen it. **Finding 3** asks for it to be documented where a plugin author will find it. |
| A test that fails against the old behaviour | Not yet met, and they say so. **Finding 4** names the row I would use. |

## What I would do before calling P0-03 done

1. Decide Finding 1, one way or the other, and add its fixture either way.
2. Add the census test for canonical-start hooks (Finding 2).
3. Land the queue/manager fixture that fails against the old behaviour (Finding 4).
4. Only then wire: nothing in Findings 1-4 requires a public API change, which keeps the increment as
   conservative as it has been.

---

## What the Finding 1 proposal costs: exactly one assertion, and it is the question itself

A proposal that quietly breaks a teammate's tests is worse than no proposal, so I ran their existing
assertions against a copy of their file with the one expression changed. The harness and its output are beside
this document (`evidence/turnstone-p0-03-proposal-impact.java`, `.txt`).

| Their test | Under the proposal |
|---|---|
| `aSupersededWorkerCannotReportOrAdopt` | **one assertion flips**: `assertFalse(reportPrimaryFailure(old))` becomes true |
| the other assertion in that test, `assertFalse(consumeFailure(original))` | unchanged - the *other* requester still cannot read it |
| `newCalculationForTheSameTaskDoesNotEraseReportedFailure` | unchanged |
| `canonicalNewTaskExpiresOldWorkEvenForSameRequesterAndGoal` | unchanged |
| `geometryCancellationExpiresPendingCurrentRequesterFailure` | unchanged |
| `worldReplacementDropsOldCompletionAndFailure` | unchanged |

So the change is not a free fix, and the honest summary is: **it does not break their tests, it disagrees with
one of their assertions, and that assertion is the specification question I am raising.** Read the two
together and the disagreement is clear:

- Their assertion says a worker that has been superseded cannot report, **whatever the reason** for the
  supersession - and it is written with two *different* requesters as its example.
- My reading is that the two reasons are not alike. Superseded by a *new world*, a *cancellation*, or a
  *canonical new task for the same requester*: refuse, and the proposal still refuses all three (the
  `*** FLIPS ***` line above is the only change in the whole file). Superseded by *another request for the
  same work*: the failure belongs to the requester that earned it, and refusing it is the same
  failure-never-arrives bug this component was built to end.
- Their assertion is still right about the part that matters most: the superseded worker **cannot adopt** -
  `isCurrent(old)` stays false, so it cannot take a path or clear the newest `inProgress`.

**Which way it should go is QuartzHeron's call, not mine.** I am proposing the split because I can execute the
argument, not because the decision is mine: `cannot adopt` is about a result and should stay strict; `cannot
report` is about a failure and should be narrow. If they decide the strict form is wanted, the counter-argument
is stronger than mine and I will take it - but the fixture from Finding 1 should land either way, the way it
does for the other five rows, because "a re-plan arrives while a calculation is in flight" is a case their
table names and their tests do not yet cover.
