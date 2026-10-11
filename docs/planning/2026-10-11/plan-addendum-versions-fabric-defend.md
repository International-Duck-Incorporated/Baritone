# Plan addition: the version line, Fabric only, and hostile-only defence

**Author:** Turnstone. **Date:** 2026-10-11. **Status:** plan, with the measurement for the first item already running.
**Authority:** the operator, in conversation on 2026-10-11, in three instructions: *"add into the plan: support for
26.1, 26.1.2, 26.2 and 26.3"*, *"focus solely on fabric"*, and *"attack hostile mobs (hostile only)"*. Those are
direct instructions to this agent and they are the human approval the gate requires for the work they name.

This file **adds to** `roadmap.md` and does not replace it. The cards stay where QuartzHeron put them; what follows
is new scope plus one narrowing of an existing card, and it is written here rather than edited into their document
so that their history and mine stay separable.

---

## 1. Multi-version Fabric support: 26.1, 26.1.2, 26.2, 26.3

### What is actually known, as opposed to assumed

Everything below was measured from this box on 2026-10-11, not taken from a wiki.

| Fact | Value | Why it matters |
|---|---|---|
| All four versions exist and are reachable from the version manifest | `26.1`, `26.1.2`, `26.2`, `26.3`; latest release is `26.3`, latest snapshot `26.4-snapshot-3` | The target list is real and the top of it is current, so this is not a back-port exercise. |
| Java requirement | **25 for all four** | One toolchain. There is no Java matrix, which removes the single most expensive axis of a multi-version build. |
| Client jar size | 26.1 38,113,398 · 26.1.2 38,113,927 · 26.2 39,193,383 · 26.3 41,483,720 bytes | 26.1→26.1.2 is **+529 bytes**: a patch release, and the pair should be treated as one target until proved otherwise. 26.1→26.3 is **+3.37 MB**, and that growth is the drift. |
| Library sets | ~91–103 entries differ between any two of these and 26.3 | The differences are **version bumps of the same dependencies** (gson 2.14.0, guava 33.6.0, icu4j 78.3, authlib 10.0.77, brigadier 1.3.11) plus genuinely new arrivals in 26.3 (`jtracy` 1.14.38 and its natives). Dependency churn is real and is handled per version by the resolver, not by hand. |
| How the build selects a version | one line: `minecraft_version=26.3` in `gradle.properties`, read by `unimined.minecraft(...) { version rootProject.minecraft_version }` in the root `build.gradle` | The version is already a single resolved property. It has never been varied, and nothing in the build assumes it will be. |
| Mappings | unimined fetches and caches them per version: `~/.gradle/caches/unimined/net/minecraft/minecraft/<version>/mappings/mappings-JOINED-.umf` | The named API surface for every target is obtainable by the same mechanism that already works for 26.3, which is what makes the drift measurable rather than guessed. |

### The measurement that decides the design

**A compile of the current source against 26.1 is running as this is written** (`./gradlew compileJava
-Pminecraft_version=26.1`, log `/home/user/logs/probe-26.1.log`). Its purpose is not to produce a jar; it is to
produce the answer to the only question that matters for choosing a design:

> how many distinct API differences are there between 26.1 and 26.3, and are they *symbols* (a method moved, a
> signature changed) or *structure* (a class gone, a concept reworked)?

The compiler's error list **is** the porting backlog. Two designs are on the table and the count decides between
them, so no design is committed to here:

- **If the differences are a handful of symbols**, the answer is the smallest seam that works: abstain from the
  changed symbols where the old form still exists, and put a narrow internal helper in front of the ones that
  genuinely differ. Baritone's own history supports this — the fork already carries version-conditional work
  (the elytra port TODOs Godwit audited), and the mapping seam is one file wide today.
- **If the differences are structural**, the answer is a multiversion build — one source tree, per-version
  conditionals, one subproject per target — which is the standard tool for exactly this and which this repo does
  not currently use.

**No recommendation is made in this document until that list exists.** Recording a design before the measurement
is what the plan's own vocabulary calls a guess.

### The staged path, once the design is chosen

Written now so the sequence is agreed even though the design is not:

- **V0 — measure.** Compile against each of the four; keep the error lists as the backlog. *In progress.*
- **V1 — the build.** A version matrix for **Fabric only** (see §2) that resolves and compiles each target. No
  behaviour change, no new features. Success is a green compile on all four, nothing more.
- **V2 — the seam.** Whatever V0 showed, behind a narrow internal abstraction. Every difference gets a written
  reason, the way the census tests require for their allowlists.
- **V3 — artefacts.** A jar per version, named so a user can tell them apart, with the version in the file name.
- **V4 — the harness.** `scripts/headless/fetch.py` already takes a version; the smoke and the scenes should run
  against each target, so that "supported" means *observed to work*, not *compiles*.

**Size, honestly: XL, and the largest item anyone has proposed on this branch.** It is also *cross-cutting*: from
the moment V1 lands, every card in the roadmap is constrained by it, because new code must compile on all four
targets. That is a real cost of this decision and it belongs in the record.

### Bounds this decision needs

- **Freeze the range.** Four named versions, and `26.4-snapshot-*` explicitly out. A moving target defeats the
  purpose of measuring drift at all.
- **26.1 and 26.1.2 are one target until measured otherwise.** The +529 bytes is the whole argument; if V0 shows
  no difference for these sources, supporting them is one target with two names, and pretending otherwise is
  busywork.
- **No version-specific features.** Support means the same mod on four versions, not a mod that behaves
  differently on each. Where a capability genuinely does not exist on an older version, it is disabled there and
  said so — never emulated.

---

## 2. Fabric only

**Decision:** the fork's platforms are Fabric. Forge and NeoForge stop being built, tested, released or maintained.

What that means concretely — this is the whole of it, because the platform trees are thin:

| Surface | Today | After |
|---|---|---|
| `gradle.properties` | `available_loaders=fabric,forge,neoforge,tweaker` | Fabric, and `tweaker` only if Fabric needs it — **to be confirmed before the change, not assumed** |
| `forge/`, `neoforge/` | 5 files and 17 KB each | Out of `settings.gradle`; the directories may stay in history, but nothing builds them |
| CI | `./gradlew build` — "builds the common code and every loader (fabric, forge, neoforge)" | Fabric only, which also cuts the job's work |
| Docs | README/wiki name the loaders | Rewritten for Fabric |
| `identity.md` | records the `baritoe`-vs-`baritone` mod-id mismatch, which lives in `forge/.../mods.toml` and the NeoForge equivalent | **The mismatch surface disappears with the platforms.** The only mod id left is Fabric's `baritone` in `fabric.mod.json`, which already agrees with the package. This narrows P4-01 and should be said plainly rather than left to be discovered later. |

Two things this decision does **not** authorise, and they are worth separating now because they are the obvious
next temptations: it does not authorise **deleting** the platform trees (that is a separate, reviewable change and
history already remembers them), and it does not authorise any **rename** — `baritone` stays, per the standing
gate on the identity work.

---

## 3. Hostile-only defence — the DEFEND card, narrowed by the operator

The operator has asked for *"attack hostile mobs (hostile only)"*. That is the explicit DEFEND approval the
roadmap's P1-03 was waiting for, and it arrives **narrower than the card**: hostile only.

**The narrowing agrees with the card's own bound rules, which is worth recording.** P1-03 already excludes PvP,
animal hunting, pet aggression, uncertain neutral retaliation, ranged combat and creeper melee. "Hostile only" is
the same boundary said in three words, so the operator's instruction and my review's amendments do not need
reconciling — they say the same thing, and the card can proceed with its bounds intact.

### The design consequence that makes this landable before the arbitration work

**Defence is delivered as a behaviour, not a process** — the same argument that let auto-eat land ahead of
P0-03. A process would take control, which means a handover, which means the resumed task's path is at risk, which
means the failure-provenance problem my review found (`calcFailed` has no requester identity, so a transient's
failure can be delivered to the resumed lower process and `MineProcess:91-97` answers it by irreversibly
blacklisting the nearest ore). A behaviour takes no path and hands nothing over.

**No pursuit is the price of that shape, and it is the right price for the first increment.** A bot that walks
toward a mob is a bot that needs the path, and therefore needs the process shape, and therefore needs the
arbitration work that QuartzHeron has claimed. So the first increment attacks what is **already in reach** and
otherwise does nothing. If pursuit is ever wanted it is a different card, with P0-03 in front of it.

This also keeps the work clear of `PathingBehavior`, `PathingControlManager` and `PathExecutor` — QuartzHeron's
claimed files — and of the look owner's reservation, which is a separate conversation and is why aiming is
described below as a dependency rather than absorbed silently.

### The bound rules, unchanged from the review

1. **Deny by default.** A mob is attacked only if it is *affirmatively* hostile. Unknown, unclassified and
   ambiguous all mean "do not attack".
2. **Exhaustive, never passive-only.** The rule names what is allowed, not what is forbidden — a passive
   allowlist would fail open on every mob added to the game after it was written.
3. **Bounded, with a return path.** A fixed attempt and time budget, then it stops and says so.
4. **No screen, no transaction, no held-item swap while suspended.** Defence uses what is in the hand; it never
   opens an inventory to fetch a sword.
5. **Failure provenance first** — for any card that takes the path. The behaviour shape above is what lets this
   one proceed without it, and that is the whole reason for the shape.

### What has to be decided before the swing, and is not decided here

- **Aiming.** Attacking requires the bot to be looking at the target, and the look surface is Nightjar's
  reservation. My review already recorded that `LookBehavior:419-456` turns the head toward a blow under
  `noticeBlows`; whether defence may use that path, or needs a new request, is a question for the look owner and
  is not taken here.
- **The classifier's inputs.** Vanilla will answer "is this mob hostile" from the entity's own category, and
  "is it angry at me" from mob-specific state. The card's bound rule forbids ServerLevel-only anger calls, so the
  observable client-side form of "is this mob currently coming for me" needs to be established for 26.3 before the
  classifier is written — and then re-established for each version in §1, which is the first real collision
  between this card and the version work.

### The first code step, which does not wait on any of that

A **Minecraft-free classifier** with its own tests, shaped exactly like `EatPolicy`: deny-by-default, named
allowances, and the refusals written down with their reasons — the golden-apple treatment applied to combat.
It can be written, tested and reviewed today, it is the gate every later piece must pass through, and it commits
the project to nothing about aiming, movement or the swing.

---

## 4. V0 result: the drift is six symbols, and this settles the design

The measurement described in §1 has now been run. **The design question is answered, and the answer is the
narrow seam.** Nothing in this section is inferred; every line is a compiler error or a `javap` read of the
26.1 named jar that the build itself downloaded.

### The probe

`./gradlew compileJava -Pminecraft_version=26.1` — the whole of Baritone, compiled against 26.1 instead of 26.3.
It could not be run at first, and **that failure is itself the finding for §2**:

```
* What went wrong:
A problem occurred evaluating project ':forge'.
> Could not resolve all files for configuration ':forge:forge'.
   > Could not find net.minecraftforge:forge:26.1-66.0.2.
```

The Forge platform pins `forge_version=66.0.2` in the root `gradle.properties`, and that Forge release exists
only against MC 26.3. So **the Forge and NeoForge platforms are not merely unnecessary for multi-version
support, they actively block it**: the build evaluates every loader before it compiles anything, so a version
outside 26.3 cannot get as far as a compile error while Forge is in the build. The operator's two instructions
therefore converge rather than conflict — Fabric-only is what *makes* the version line reachable, and it was
made first (`available_loaders=fabric`).

### The result: 6 errors, 3 files, all of them glue

With Fabric only, the compile got through to the source and stopped on **six symbols**:

| # | Where | 26.3 spelling | 26.1 spelling | Kind |
|---|---|---|---|---|
| 1–4 | `api/utils/BlockOptionalMeta.java:308,315,319,323` | `VanillaPackResources#fullResources()`, `RegistryLayer.WORLD` (×2), `RegistryDataLoader.WORLD_REGISTRIES` | `fullResources()` does not exist; `RegistryLayer` is at **`net.minecraft.server`** not `net.minecraft.core`; the registries are `WORLDGEN_`/`DIMENSION_`/`SYNCHRONIZED_REGISTRIES` | one added method, one package move, one constant regrouped |
| 5 | `api/Settings.java:1300` | `Minecraft.getInstance().gui.hud.getChat()` | `Minecraft.getInstance().gui.getChat()` | the chat component moved into a `hud` field |
| 6 | `api/utils/gui/BaritoneToast.java:27` | `Minecraft.getInstance().gui.toastManager()` | `Minecraft.getInstance().getToastManager()` | the toast manager moved from `Minecraft` to `Gui` |

**So the whole of the 26.1→26.3 drift, across every line of this fork, is six symbols in three files — and all
three files are peripheral glue.** `BlockOptionalMeta` is a block-metadata lookup; the other two are one chat
line and one toast line. The pathfinder, the movement rules, the look behaviour, the process framework and the
whole humanlike layer compile against 26.1 **with no changes at all**.

That result is worth stating in one sentence for the record, because it is the opposite of what "support four
versions" sounds like it costs:

> Supporting 26.1 through 26.3 is three files and a seam of six accessors, not a multiversion rebuild.

### What this changes in §1

- **V2 is now specified rather than open.** The seam is a narrow internal helper — the version-dependent
  spelling of each of those six symbols, and nothing else. `BaritoneToast` and the chat line are one call each;
  `BlockOptionalMeta`'s registry construction is the only one with any substance, and it is a single method.
- **The multiversion-build design is withdrawn** as unnecessary for this range. It remains the right answer if
  a future target moves something structural; it is not the right answer for 26.1/26.1.2/26.2/26.3, and
  adopting it now would be buying a large machine to hold six accessors.
- **26.1.2 is still assumed to be the same target as 26.1** (the +529-byte jar). The probe should be re-run
  against it to confirm rather than assume; that is cheap now that the mechanism exists.
- **26.2 is unmeasured.** 26.1 gave the *upper bound* of the drift (it is the furthest from 26.3), so 26.2 can
  only be smaller — but "can only be smaller" is an argument, not a measurement, and this plan does not accept
  the one in place of the other. A second probe against 26.2 is the obvious next step.
- **The end-to-end cost is still unmeasured.** Compiling is not running: V3/V4 (artefacts, and the headless
  harness against each target) remain, and a symbol that compiles can still be a symbol that behaves
  differently. The claim this section supports is exactly the claim the probe supports — *the source compiles*.
