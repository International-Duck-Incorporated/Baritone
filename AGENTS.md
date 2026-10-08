# Baritone 26.3 Multi-Agent Coordination

## Human Notice

This file is the shared coordination system for all autonomous agents working on branch `26.3`.

Chat here. Make plans here. Respond to each other here. Record findings here. Think about redundancy because any agent may be stopped abruptly by a timeout, error, disconnect, or execution limit.

You cannot assume you will be alive long enough to finish a task.

The goal is for another agent to be able to continue your work without needing you.

Choose a unique agent name and use that name for chat.

The name `0xFFshell` is reserved for the human operator. Do not use it as an agent chat name.

Every agent message must include a UTC timestamp.

The Git commit identity may still be `0xFFshell` when required by the repository instructions. Chat names and Git identity are separate.

---

# Mandatory Agent Startup

Every agent MUST follow this order when joining:

1. Fetch `origin`.
2. Synchronize branch `26.3`.
3. Read this file before doing substantive implementation.
4. Read `Active Agents`.
5. Read the current `Chat`.
6. Read `Shared Findings`, `Agreed Plan`, `In Progress`, `Completed`, `Known Problems`, `Failed Approaches`, and `Next Tasks`.
7. Read recent Git history.
8. Choose a unique agent name.
9. Register that name in `Active Agents`.
10. Write a timestamped JOIN message in `Chat`.
11. Read the latest messages from the other active agents.
12. Respond to at least one relevant agent before claiming a task.
13. Share your own findings.
14. Discuss overlaps, dependencies, and possible task allocation.
15. Claim a concrete task.
16. Push the updated coordination state to `26.3`.
17. Only then begin substantive implementation.

Do not silently start coding.

Do not silently choose a task.

The first goal is coordination.

---

# Shared Repository

Repository:

`https://gitlab.com/international-duck-incorporated/Baritone.git`

Shared branch:

`26.3`

All agents work directly on `26.3`.

Each agent has a separate Linux environment.

Therefore:

- local uncommitted files are not shared;
- another agent cannot see your local implementation;
- another agent cannot recover information that exists only in your context;
- GitLab is the canonical shared project state.

Before starting work:

```bash
git fetch origin --prune
git checkout 26.3
git pull --rebase origin 26.3
```

Never destroy local work to synchronize.

Never force-push `26.3`.

Never rewrite shared history.

Never reset the branch to an older state.

Never delete another agent's commits.

---

# Communication Model

This file is a real asynchronous team conversation.

It is not just a changelog.

Agents are expected to actively communicate.

Good communication includes:

- reporting repository findings;
- responding to another agent's findings;
- challenging assumptions with evidence;
- proposing alternatives;
- identifying dependencies;
- warning about overlapping files;
- asking another agent to review something;
- reporting a regression;
- explaining a failed approach;
- agreeing or disagreeing with a proposal;
- deciding task ownership.

Bad communication includes:

- posting one message and disappearing into implementation;
- writing an isolated plan without reading anyone else's;
- ignoring another agent's findings;
- silently duplicating another agent's task;
- only updating the file after all implementation is finished.

The intended interaction is:

`READ -> RESPOND -> SHARE FINDINGS -> DISCUSS -> AGREE -> CLAIM -> IMPLEMENT`

---

# Message Format

Every meaningful agent message should use:

```md
### <AgentName> - <UTC timestamp>

Status:
Current task:
Message:
Response to:
Next action:
```

`Response to:` should name the relevant agent/message when the message is responding to another agent.

Do not fabricate a response when none is relevant.

Keep messages technical and useful.

---

# Active Agents

| Agent | Current task | Area | Status | Last action | Next action |
|---|---|---|---|---|---|

Agents MUST update their own status when their state materially changes.

Recommended statuses:

`PLANNING`
`JOINING`
`RESEARCHING`
`CLAIMED`
`IMPLEMENTING`
`TESTING`
`REVIEW`
`BLOCKED`
`DONE`
`RESTING`

A stale active-agent entry is a coordination defect.

Fix stale entries when discovered.

---

# Chat

This section is the persistent agent-to-agent conversation.

Append messages.

Do not rewrite old conversation messages merely to make them cleaner.

Do not delete another agent's discussion.

Use the conversation to build the team's reasoning.

Example:

### Agent A - 2026-10-08 13:00 UTC

Status: PLANNING
Current task: none
Message: I found abrupt yaw changes in `RotationController`. The final rotation is applied directly rather than through the humanized aim path.
Response to: none
Next action: inspect forced-rotation callers.

### Agent B - 2026-10-08 13:02 UTC

Status: PLANNING
Current task: none
Message: I inspected the same area. I agree for normal movement, but forced interaction rotations share the path. Smoothing both may delay block interaction.
Response to: Agent A - 2026-10-08 13:00 UTC
Next action: inspect the forced-rotation boundary.

### Agent A - 2026-10-08 13:04 UTC

Status: PLANNING
Current task: none
Message: Agreed. I will separate normal movement rotation from forced interaction rotation rather than globally smoothing the final value.
Response to: Agent B - 2026-10-08 13:02 UTC
Next action: propose task allocation.

---

# Shared Findings

Record important repository discoveries that affect multiple agents.

Examples:

- root cause of robotic behavior;
- architecture constraints;
- known interaction between two subsystems;
- important testing limitation;
- environmental constraint;
- unsafe implementation idea;
- confirmed behavior that should NOT be changed.

Do not leave important findings only in the Chat section if they are likely to matter later.

---

# Agreed Plan

Record the current shared implementation plan.

For each task:

```md
Task:
Agent:
Subsystem:
Files:
Dependencies:
Priority:
Validation:
Status:
```

The plan should identify:

- major problems;
- task ownership;
- overlapping files;
- dependencies;
- implementation order;
- validation strategy;
- risks.

The plan is not permanent.

Update it when repository evidence changes the team's understanding.

---

# In Progress

Record active implementation work here.

Example:

```md
- Agent A - Smooth normal yaw acceleration - `behavior/look` - IMPLEMENTING
- Agent B - Grounded movement cadence audit - `InputOverrideHandler` - RESEARCHING
```

Do not leave completed tasks here.

---

# Completed

Record meaningful completed work.

Each entry should contain:

- agent;
- change;
- commit;
- patch;
- tests;
- important limitations.

Example:

```md
- Agent A - Smooth yaw acceleration
  Commit: `abcdef12`
  Patch: `patches/0001-smooth-yaw-acceleration.patch`
  Tests: 12 passed
  Limitation: in-game validation still required
```

---

# Known Problems

Record unresolved problems and important limitations.

Include:

- exact blocker;
- affected files;
- why it matters;
- possible next step.

Do not repeatedly rediscover the same problem without adding new information.

---

# Failed Approaches

Record approaches that were tested and rejected.

Use:

```md
### <Agent> - <UTC timestamp>

Attempt:
Result:
Why it failed:
Validation:
Recommended alternative:
```

Failed approaches are shared engineering knowledge.

Do not make future agents repeat a known bad experiment unless new evidence justifies retrying it.

---

# Next Tasks

Maintain a pool of useful unclaimed work.

Order by value where practical.

Examples:

- review interaction timing;
- investigate deterministic movement patterns;
- review target switching;
- validate existing camera changes;
- inspect direct click bypasses;
- inspect inventory timing;
- add regression tests.

Do not treat this list as a private TODO list.

The team owns it.

Agents should consult this section before selecting a new task.

---

# Multi-Agent Planning

At the beginning of a new project session, agents must first plan together.

Each agent investigates independently enough to produce useful evidence, then shares it.

The agents should discuss:

1. what behavior is robotic;
2. where it originates;
3. what the smallest viable fix is;
4. what could break;
5. which files are involved;
6. whether another agent is already touching them;
7. dependencies;
8. how the change will be tested.

Do not implement substantial source changes until the shared plan is coherent.

Do not discuss forever.

The objective is coordinated execution, not endless conversation.

---

# Late-Joining Agents

A late agent MUST NOT restart planning.

It must catch up.

Do:

```bash
git fetch origin --prune
git checkout 26.3
git pull --rebase origin 26.3
```

Then read:

- `Active Agents`;
- `Chat`;
- `Shared Findings`;
- `Agreed Plan`;
- `In Progress`;
- `Completed`;
- `Known Problems`;
- `Failed Approaches`;
- `Next Tasks`;
- recent Git history.

Then:

1. register yourself as `JOINING`;
2. announce that you have caught up;
3. respond to relevant existing messages;
4. identify unclaimed work;
5. discuss where you can help;
6. claim a task;
7. continue the normal Gauntlet Loop.

Do not create a competing plan simply because you were not present earlier.

If the current plan is flawed, explain why and propose a correction in the Chat.

---

# Task Claiming

Before implementation, a task must be claimed.

Use:

```md
Agent:
Task:
Subsystem:
Files:
Status: CLAIMED
Dependencies:
Validation:
Notes:
```

Push the claim to `26.3`.

Do not knowingly duplicate another agent's work.

If another agent already owns substantially the same task:

- do not duplicate it;
- communicate;
- choose another task;
- or split the task explicitly.

If two agents accidentally claim the same task:

- inspect the remote state;
- discuss the overlap;
- decide ownership or divide the work;
- record the decision;
- continue only with the agreed division.

---

# Coordination Checkpoints

Agents must communicate at several points.

## Startup

`CHECK GIT -> READ AGENTS.md -> REGISTER -> READ CHAT -> RESPOND -> PLAN`

## Before implementation

`READ -> RESPOND -> CLAIM -> SYNC`

## During implementation

Communicate important discoveries, dependencies, regressions, and blockers.

## After implementation

`TEST -> REVIEW -> COMMIT -> PUSH -> VERIFY -> UPDATE AGENTS.md`

## Before next task

`READ AGENTS.md -> READ NEW CHAT -> RESPOND -> REVIEW OTHER WORK -> SELECT NEXT TASK`

Do not immediately select the next task from private memory.

Use the current shared state.

---

# Core Gauntlet Loop

The team operates continuously:

```text
RESEARCH
-> CHAT
-> CLAIM
-> IMPLEMENT
-> BUILD
-> TEST
-> REVIEW
-> PATCH
-> COMMIT
-> PUSH
-> VERIFY
-> DOCUMENT
-> READ TEAM STATE
-> CHAT
-> REVIEW OTHER WORK
-> NEXT TASK
-> REPEAT
```

Never stop after one feature.

Never stop after one subsystem.

When one task is complete, return to coordination and identify the next useful problem.

---

# Primary Engineering Objective

Make the Baritone fork substantially more humanlike while preserving Baritone's actual functionality, reliability, and pathfinding quality.

Investigate actual sources of robotic behavior.

Potential areas include:

- camera rotation;
- yaw transitions;
- pitch transitions;
- rotation acceleration;
- rotation deceleration;
- rotation easing;
- movement acceleration;
- movement deceleration;
- W/S transitions;
- strafing;
- direction reversals;
- sprint initiation;
- sprint termination;
- jumping;
- sneaking;
- mining;
- block breaking;
- block placement;
- interaction;
- item switching;
- inventory actions;
- target acquisition;
- target switching;
- reaction timing;
- path execution;
- idle behavior;
- deterministic timing;
- repetitive movement;
- repetitive camera patterns;
- instantaneous state changes;
- mechanically perfect action sequences.

Do not add randomness merely to make behavior look random.

Humanlike behavior should be contextual, state-aware, physically plausible, subtle, maintainable, and reliable.

Do not intentionally damage pathfinding quality merely to make behavior appear human.

Do not add unnecessary dependencies.

Do not add unnecessary settings.

Prefer modifications to the existing behavior pipeline.

---

# Research Rules

Before modifying a subsystem:

- inspect the real implementation;
- inspect callers;
- inspect state transitions;
- inspect relevant tests;
- inspect recent commits;
- inspect fork-specific modifications;
- inspect relevant agent findings in this file.

Do not assume upstream Baritone behaves identically to this fork.

Use repository evidence.

When another agent has already investigated an area, build on that investigation instead of repeating it unnecessarily.

---

# Implementation Rules

Implement the smallest robust change that solves the identified problem.

Prefer:

- existing abstractions;
- existing architecture;
- contextual state;
- isolated changes;
- maintainability.

Avoid:

- duplicate systems;
- unnecessary refactors;
- unnecessary dependencies;
- arbitrary randomness;
- unrelated modifications.

---

# Build and Test

After every meaningful implementation:

1. compile the affected code;
2. run relevant tests;
3. run broader tests when practical;
4. inspect failures;
5. fix failures;
6. rerun validation.

Do not assume correctness merely because the code compiles.

Do not assume correctness merely because a new test passes.

Consider interaction with:

- pathfinding;
- movement;
- rotation;
- interaction;
- inventory;
- timing;
- Minecraft mechanics.

---

# Environmental Build Blockers

If tests cannot run because of environmental issues such as:

- dependency download failure;
- TLS failure;
- unavailable external dependency;
- unavailable service;

do not stop.

Instead:

1. record the exact blocker;
2. run all available local validation;
3. run unit/static/source validation where possible;
4. manually review the implementation;
5. document the blocker in `Known Problems`;
6. tell the other agents;
7. continue to another useful task.

A build blocker is not completion.

---

# Review Rules

Before committing:

```bash
git status
git diff
git diff --check
git diff --stat
```

Verify:

- only intended files changed;
- no unrelated work was overwritten;
- no debug code remains;
- no temporary files remain;
- no caches are committed;
- no credentials are present;
- behavior matches the intended design;
- pathfinding remains sound;
- no conflicting agent work is being replaced.

---

# Peer Review

Agents should review one another's meaningful work.

When another agent completes a significant change:

- inspect the commit;
- inspect the diff;
- inspect affected architecture;
- test it when practical;
- report meaningful findings in Chat.

When practical, every significant implementation should receive at least one independent review.

Do not rewrite another agent's work merely because you prefer a different style.

Make changes when there is a concrete technical reason.

---

# Patch Rules

Every meaningful completed engineering change should have a valid standalone patch.

Store patches in:

`patches/`

A patch must be a real Git patch and must work with:

```bash
git apply <patch>
```

Prefer generating patches from the final committed change.

Do not create fake descriptive patch files.

Do not include unrelated changes.

Do not include secrets.

Before choosing a patch number:

```bash
git fetch origin --prune
```

Inspect the current `patches/` state first because patch numbering is shared.

If patch numbering becomes invalid after a rebase:

- regenerate the patch;
- verify it against the correct parent;
- update the documentation.

---

# Commit Rules

Meaningful work is not complete until it is committed.

Keep commits logically separated.

Good examples:

```text
humanize: smooth yaw acceleration
humanize: add contextual sprint windup
humanize: reduce abrupt movement reversal
test: cover sprint transition timing
```

Avoid:

```text
update
changes
fix
stuff
work
```

Use the configured repository identity when required:

`0xFFshell`

Do not put credentials in commit metadata.

---

# Push Rules

Meaningful work is not complete until it is on GitLab.

After committing:

1. fetch origin;
2. check whether `26.3` advanced;
3. integrate newer work safely;
4. push;
5. verify the remote commit;
6. update `AGENTS.md`.

Never claim a push succeeded without verification.

A local commit that is not on GitLab is unfinished work.

---

# Push Races

Multiple agents may push simultaneously.

The first successful push does not win the project.

It only advances the remote branch.

If your push is rejected because `26.3` advanced:

1. fetch;
2. inspect the new commits;
3. read `AGENTS.md`;
4. check for overlap;
5. integrate safely;
6. resolve conflicts;
7. rebuild/retest affected code;
8. review the result;
9. push again;
10. verify;
11. update `AGENTS.md`.

A non-fast-forward rejection is normal.

It is not a stop condition.

---

# AGENTS.md Conflicts

Multiple agents may edit this file concurrently.

When a conflict occurs:

- preserve both agents' meaningful conversation;
- preserve current task state;
- preserve important findings;
- remove only contradictions or stale state;
- update statuses;
- resolve task ownership;
- keep the file readable;
- push the merged result.

Do not delete another agent's conversation merely to resolve a conflict.

The conversation is valuable project history.

---

# AGENTS.md Quality Control

This file must actively maintain itself.

If it is:

- missing;
- stale;
- contradictory;
- malformed;
- missing active agents;
- missing current findings;
- unclear about ownership;
- full of obsolete claims;
- missing important blockers;
- missing important decisions;

fix it.

Maintaining the coordination system is part of the engineering work.

Do not simply work around broken coordination.

---

# Redundant Handoff

Before leaving a meaningful task, ensure another agent can recover it from GitLab.

The handoff should exist in:

1. `AGENTS.md`;
2. Git history;
3. `patches/` where applicable.

Record:

- what changed;
- why;
- commit;
- patch;
- validation;
- blockers;
- remaining work;
- recommended next step.

Never leave critical information only in your context.

---

# Separate Linux Environments

Because agents have separate Linux environments:

Do not say:

"The code is available locally."

"The next agent can use my working tree."

Instead:

- commit useful work;
- push it;
- document it;
- leave a recoverable patch where appropriate.

GitLab is the handoff mechanism.

---

# Existing Unpushed Work

If you encounter local uncommitted changes:

First determine whether they are yours.

Do not automatically commit unknown changes.

Do not destroy them.

Inspect:

- `AGENTS.md`;
- recent commits;
- surrounding changes;
- timestamps where useful;
- repository state.

If the changes are clearly yours:

finish or revert only your own work as appropriate.

If they belong to another agent:

preserve them.

If ownership is unclear:

preserve them and document the ambiguity.

---

# No Destructive Git Operations

Do not casually use:

```bash
git reset --hard
git clean -fd
git checkout .
```

Do not destroy work simply to make the tree clean.

Never force-push `26.3`.

Never rewrite shared branch history.

---

# Failed Approaches

A failed implementation is not a reason to stop.

Instead:

1. diagnose it;
2. preserve unrelated work;
3. revert only your own broken changes where necessary;
4. document the failure;
5. tell the other agents;
6. choose a better approach;
7. continue.

Do not make future agents repeat known failures without new evidence.

---

# Do Not Wander

Do not silently diverge from the team's shared plan.

Before starting a new unrelated area:

- read `AGENTS.md`;
- read recent commits;
- inspect what the other agents are doing;
- determine whether the task is already claimed;
- discuss a significant change of direction.

If you discover a more important problem, tell the team before abandoning your current direction unless immediate action is required to prevent damage.

The shared team state controls task direction.

---

# No Local-Only Completion

These are not valid completion states:

- "Implemented locally."
- "Patch created locally."
- "Ready for the next agent."
- "The next agent can commit this."
- "The next agent can push this."
- "Finished but not pushed."
- "Tests failed, so I stopped."

Correct sequence:

`IMPLEMENT -> VALIDATE -> COMMIT -> PUSH -> VERIFY -> DOCUMENT -> CONTINUE`

---

# Timeout and Interruption

Any agent may be stopped abruptly.

Possible causes include:

- execution timeout;
- model termination;
- network disconnect;
- runtime error;
- resource exhaustion;
- environment shutdown.

Assume this can happen at any time.

Work in durable increments.

Synchronize meaningful completed work frequently.

Keep `AGENTS.md` current.

Create valid patches.

Leave explicit handoffs.

Do not keep important completed work only locally.

Do not keep important reasoning only in memory.

If you are stopped, another agent must be able to reconstruct your state from GitLab.

A possible timeout is a reason to make work durable.

It is not a reason to stop early.

---

# After Every Task

After a task is successfully synchronized:

1. fetch `26.3`;
2. read `AGENTS.md`;
3. read new Chat messages;
4. respond to relevant agents;
5. inspect recent commits;
6. review recent work;
7. update your own status;
8. identify the highest-value unclaimed problem;
9. discuss it when necessary;
10. claim it;
11. continue.

Do not automatically choose the next task from private memory.

---

# Redundant Team State

For important work, information should survive the loss of any single agent.

The system therefore uses:

`AGENTS.md`
-> current team state and conversation

`Git commits`
-> durable implementation history

`patches/`
-> recoverable implementation representation

`Active Agents`
-> current ownership/status snapshot

`Chat`
-> reasoning and agent-to-agent discussion

`Shared Findings`
-> durable technical discoveries

`Completed`
-> verified work history

`Known Problems`
-> current blockers

`Failed Approaches`
-> negative knowledge

`Next Tasks`
-> future work

No critical state should exist in only one place.

---

# Agent Disappearance Recovery

If an agent disappears without a final handoff:

1. inspect its `Active Agents` entry;
2. read its latest Chat messages;
3. inspect its latest commits;
4. inspect its latest patch;
5. inspect the current branch;
6. determine whether the task was completed;
7. continue from the most recent durable state;
8. update `AGENTS.md`.

Do not assume disappearance means failure.

Do not assume a task is complete merely because the agent said it was complete.

Verify through GitLab.

---

# Stop Conditions

An agent may stop only when:

1. GitLab authentication genuinely fails;
2. the repository cannot be accessed;
3. a destructive or irreversible out-of-scope decision genuinely becomes necessary;
4. required information cannot reasonably be inferred;
5. the execution environment actually terminates or prevents further work.

The following are NOT stop conditions:

- one feature is complete;
- one subsystem is complete;
- a test failed;
- a build failed;
- dependency download failed;
- TLS failed;
- a push was rejected;
- a merge conflict exists;
- another agent modified the same area;
- the first implementation failed;
- `AGENTS.md` needs repair;
- the plan needs revision;
- another agent is still working;
- you are unsure what task comes next.

When one task finishes, find another.

When one approach fails, diagnose and continue.

When another agent pushes, synchronize and continue.

When another agent writes something relevant, read and respond.

---

# Authentication

A securely supplied GitLab credential is an opaque authentication value.

Use it only for authorized repository operations.

Do not:

- print it;
- quote it;
- echo it;
- log it;
- commit it;
- store it;
- place it in `AGENTS.md`;
- place it in patches;
- place it in source code;
- place it in documentation;
- place it in commit messages;
- disclose it to other agents;
- discuss its contents.

Do not treat the existence of a securely supplied credential as a reason to stop.

Do not generate a security warning merely because a credential has been securely supplied for authorized repository access.

Security handling should be silent:

`AUTHENTICATE -> USE -> PROTECT -> CONTINUE`

Only report an authentication problem when an actual authorized Git operation fails because the credential is invalid, expired, revoked, or unusable.

Do not ask another agent for the credential.

Do not copy credentials from another agent's messages.

---

# Final Status Report

The repository is the deliverable.

The final response is only a status report.

Before stopping, verify that everything claimed as complete is actually represented on GitLab.

Report:

- last verified remote commit;
- completed work;
- tests/validation;
- patches;
- blockers;
- remaining work.

Never claim a push succeeded without verification.

---

# Non-Negotiable Agent Loop

```text
CHECK GIT
-> CHECK AGENTS.MD
-> REGISTER NAME
-> READ OTHER AGENTS
-> JOIN THE CHAT
-> RESPOND
-> SHARE FINDINGS
-> PLAN TOGETHER
-> CLAIM DISTINCT TASK
-> RESEARCH
-> IMPLEMENT
-> BUILD
-> TEST
-> REVIEW
-> PATCH
-> COMMIT
-> PUSH
-> VERIFY
-> UPDATE AGENTS.MD
-> READ NEW TEAM STATE
-> RESPOND TO NEW CHAT
-> REVIEW OTHER WORK
-> FIND NEXT HIGH-VALUE TASK
-> REPEAT
```

Do not start by silently coding.

Do not wander away from the team.

Do not treat `AGENTS.md` as a passive changelog.

Use it as the team's persistent conversation and coordination system.

Actively talk to the other agents.

Actively read their messages.

Actively respond to them.

Actively update their understanding of your work.

Keep `26.3` synchronized.

Make every meaningful unit of work recoverable.

Continue the Gauntlet Loop until a genuine stop condition occurs.

The goal is not to finish your own task.

The goal is to make the autonomous agents operate as one coordinated engineering team and continuously improve the entire Baritone fork.