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

---

## 5. CORRECTION to §4: the drift is 71 error sites, not six. The earlier number was a measurement artefact.

**My own correction, 2026-10-11, and it matters because §4 above is wrong.** I published "six symbols in three
files, all peripheral glue" and withdrew the multiversion-build design on the strength of it. That number came
from `./gradlew compileJava -Pminecraft_version=26.1`, and **that task does not compile the whole tree.**

The root build declares `sourceSets { api { ... } main { compileClasspath += api.output } }`, so `compileJava` has
to resolve the `api` project first. It stopped at the six `api` errors and **never compiled `src/main/java` at
all**. The six errors were real; they were the `api` project's six. The main tree was never asked.

### The corrected measurement

`./gradlew compileJava compileLaunchJava -Pminecraft_version=26.1 --continue` — `--continue` so that the task
that fails first does not hide the rest. Raw log kept at
`docs/planning/2026-10-11/evidence/turnstone-261-main-compile.log`.

| | |
|---|---|
| error lines in the log | **142** (the compiler prints each twice) |
| **distinct error sites** | **71** |
| **distinct causes** | **20** |
| files | **13** |

By file, and the shape of it is the important part:

| file | sites | what it is |
|---|---|---|
| `utils/IRenderer.java` | 43 | the **whole rendering pipeline API** |
| `utils/accessor/IRenderPipelines.java` | 5 | the same |
| `utils/PathRenderer.java` | 3 | the same |
| `command/defaults/RenderCommand.java` | 1 | the same |
| `behavior/LookBehavior.java` | 4 | `swing(...)`, the punch packet, `screen()` (see below) |
| `utils/BlockBreakHelper.java` | 3 | `swing(...)`, the punch packet |
| `utils/pathing/Avoidance.java` | 3 | `Enderman` |
| `cache/CachedChunk.java` | 2 | `DYED_SHULKER_BOX`, `BED` |
| `process/FarmProcess.java` | 2 | `BonemealSource` |
| `utils/BlockPlaceHelper.java` | 2 | `swing(...)` |
| `process/elytra/ElytraBehavior.java` | 1 | `swing(...)` |
| `Baritone.java` | 1 | `setScreen(...)` signature |
| `command/ExampleBaritoneControl.java` | 1 | `openUri(...)` |

**52 of the 71 are one subsystem** — the renderer. And it is not a rename: 26.1 has no
`net.minecraft.client.renderer.BindGroupLayouts`, no `StagedVertexBuffer`, and no
`com.mojang.renderpearl.api.pipeline.RenderPipeline` at all, while 26.3's IRenderer is built on all three.
That is a rendering port, not a spelling difference, and it is by far the largest single piece of the version
work. The remaining 19 sites are a genuine mix of renames (`swing` gained an argument; `ServerboundPunchPacket`
was `ServerboundSwingPacket`; `screen()`; `openUri`), constants that moved (`DYED_SHULKER_BOX`, `BED`,
`BonemealSource`, `levelExtractor`) and one entity class (`Enderman`).

### What is actually done, and what the honest state is

**The five `api` symbols are fixed and verified** (§4's list was right as far as it went). The seam is
`api/utils/VersionCompat.java`: `vanillaPack()`, `worldDataLayer()`, `worldDataRegistries()`, `chat(Gui)` and
`toasts(Minecraft)`, each one shape-based rather than name-based, with every lookup asserting that it found
exactly one candidate rather than taking the first. All three of the seams reachable from a Fabric jar were then
**verified in a live client on 26.3**: Baritone's own chat lines render through `chat()`, `#mine minecraft:stone`
parsed a block and printed it through the registry path (which is `worldDataLayer()` and
`worldDataRegistries()` doing their job - the block-parse path is the one that needs a registry access), and the
toast path put "[Baritone] true" on the screen through `toasts()`. No new exception appears anywhere in the log.

**The other 19 symbols are inventoried, not fixed.** So:

- **The `api` half of multi-version support is done.** `./gradlew compileApiJava -Pminecraft_version=26.1` is
  green.
- **The `main` half is not.** `compileJava` against 26.1 is 71 error sites, 52 of them the renderer.
- **V2 is therefore L, not the S/M that §4 implied**, and the rendering subsystem is its own piece of work with
  its own size. Anyone picking this up should start from the log rather than from §4's table.
- **The multiversion-build design stays withdrawn, but for a different reason than §4 gave.** Not because the
  drift is trivial - it is not - but because 52 of 71 sites are in one subsystem, which a per-version
  abstraction handles and a second build system does not. If the renderer turns out to need genuinely different
  code per version rather than a different name, that conclusion should be revisited rather than inherited.
- **What is unchanged:** the pathfinder, the path execution, the movement rules, the process framework and the
  whole humanlike layer are **not** in the 71. That part of §4 holds, and it is the part that matters for the
  project's future - the rendering layer is paint, and the walk is the product.

## 6. V0 completion: compile probes for 26.2 and 26.1.2 (Sandpiper, 2026-10-11)

Method, as Turnstone recorded it: `./gradlew compileJava compileLaunchJava -Pminecraft_version=<V> --no-daemon --max-workers=1 -Dorg.gradle.jvmargs="-Xmx1200m -XX:MaxMetaspaceSize=448m" --continue`, on JDK 25 (`openjdk-25-jdk-headless`, Debian 25.0.4.1), Gradle 8.14.4 wrapper, `GRADLE_USER_HOME=/home/user/.cache/gradle`, fabric-only build. Raw logs are committed next to this file: `evidence/sandpiper-probe-263-control.log`, `evidence/sandpiper-probe-262.log`, `evidence/sandpiper-probe-2612.log`.

| Version | Result | javac errors | Where |
|---|---|---|---|
| 26.3 (control) | **BUILD SUCCESSFUL** in 1m 08s | 0 | - |
| 26.2 | `compileApiJava` ok; `compileJava` **FAILED** | 56 | renderer 40 (`IRenderer` 35, `IRenderPipelines` 5); other 16 across 8 files (includes `PathRenderer` 1) |
| 26.1.2 | `compileApiJava` ok; `compileJava` **FAILED** | 71 | renderer 51 (`IRenderer` 43, `IRenderPipelines` 5, `PathRenderer` 3); `RenderCommand` 1; other 19 across 9 files |

What the two probes establish:

- **The harness works.** The 26.3 control compiles clean with the same command, so the failures are the versions' and not the probe's.
- **26.1.2 is the same target as 26.1, by measurement and not by assumption.** Comparing the distinct error messages (path normalised, line numbers dropped) of `turnstone-261-main-compile.log` and `sandpiper-probe-2612.log` gives **40 and 40 distinct messages with an empty difference in both directions.** The raw error counts also agree in shape (71 for 26.1.2 against Turnstone's 71 for 26.1).
- **26.2 is not 26.1 with less drift.** It has one message that 26.1 does not (`IRenderer`: `no suitable method found for drawFromBuffer(ExecuteInfo,RenderPass)`), and it lacks the 26.1-only messages for `Baritone.java`, `cache/CachedChunk.java`, `command/defaults/RenderCommand.java` and `StagedVertexBuffer`. So 26.2 has its own, mostly renderer, drift. This was **not** visible in the earlier 26.1 analysis and is why the 26.2 figure is a measurement here.
- **Non-renderer drift is smaller than the renderer's in every probe**, but it is not zero: `Avoidance`, `BlockBreakHelper`, `BlockPlaceHelper`, `LookBehavior`, `FarmProcess`, `ElytraBehavior` and `ExampleBaritoneControl` fail in 26.2 as well. Those are the six-accessor seam's territory (Turnstone's item 2), not the renderer's.

What these probes do **not** establish: a compile is not a run. Neither 26.2 nor 26.1.2 has been launched, and no game, no harness scene and no artefact has been produced for either. Under the agreed rule ("supported" means observed to work) **neither version is supported yet**; both are "source does not compile against this version", with the errors above.

Consequence for the plan: the renderer port (about 40 to 51 sites in every version probed) is the single largest item for all three non-26.3 targets. The seam (item 2) cannot make any version "supported" by itself.

## 7. Queue item 2 (the non-renderer seam): the measured mapping, and the four decisions it needs (Sandpiper)

Every row below was read from the 26.1.2 jar (`minecraft-26.1.2-fabric-merged-official.jar`, official names) with `javap`, against the line the 26.1.2 compiler rejected. None of these is a rename that the source can absorb: the source must compile on **both** versions, so a difference in a class, a method's arity, or a constant needs a bridge, and the bridge has to be written by type (as `VersionCompat` already does), not by name. Nothing in the shared main code has been changed for this item yet.

| Site (main/java/baritone/...) | 26.3 source uses | 26.1.2 has | Bridge needed | Decision? |
|---|---|---|---|---|
| `behavior/LookBehavior:784`, `process/elytra/ElytraBehavior:805`, `utils/BlockBreakHelper:84`, `utils/BlockPlaceHelper:73,81` | `player.swing(hand, SwingAnimation.DEFAULT, false)` | `LocalPlayer.swing(InteractionHand)` only (`Player.swing(InteractionHand)`) | a reflective `swing(hand)` bridge that calls the 1-argument form on 26.1 and the 3-argument form with the default animation on 26.3 | **D1**: is `swing(hand)` on 26.1 the same animation as `SwingAnimation.DEFAULT` on 26.3? Not measurable from the jar; needs a 26.1 run or the vanilla changelog |
| `behavior/LookBehavior:46,785`, `utils/BlockBreakHelper:23,85` | `ServerboundPunchPacket.INSTANCE` | no `ServerboundPunchPacket`; `ServerboundSwingPacket` exists with constructor `(InteractionHand)` | a bridge that sends `ServerboundPunchPacket.INSTANCE` on 26.3 and `new ServerboundSwingPacket(MAIN_HAND)` on 26.1 | **D2**: the punch packet and the swing packet are different packets on the wire. Choosing the swing packet on 26.1 changes what a remote observer receives. This is the anti-cheat-sensitive one: it needs the owner's ruling, not a guess |
| `behavior/LookBehavior:1118` | `ctx.minecraft().gui.screen()` | `Minecraft.screen` is a public field (type `Screen`); `Gui` has no `screen()` | a bridge that reads the `Screen`-typed field or the zero-argument `Screen`-returning method, asserting exactly one | none (mechanical) |
| `command/defaults/RenderCommand:41` | `levelExtractor.setBlocksDirty(...)` | not present (renderer-side API) | **moved to queue item 3 (renderer).** Not counted as a non-renderer symbol | - |
| `Baritone:251` | `mc.gui.setScreen(new GuiClick())` | `Minecraft.setScreen(Screen)` exists; `gui.setScreen` does not | a bridge calling `setScreen` on the client or on `gui`, whichever the version has, by type | none (mechanical) |
| `utils/pathing/Avoidance:26,79` | `import ...monster.Enderman`; `((Enderman) entity).isCreepy()` | class is `monster.EnderMan` (`extends Monster`, `NeutralMob`) | `isCreepy` has no common compile-time form. A bridge that checks `EntityType`-equality (`EntityType.ENDERMAN`, present in 26.1.2) and then calls `isCreepy` through the type | **D3**: whether an `Avoidance` filter should keep "creepy only" semantics on 26.1 if the method is absent there. Propose: keep it, bridged by reflection; if it cannot be found, fail to "not creepy" (fewer avoidances, never more) |
| `cache/CachedChunk:82,83` | `Blocks.DYED_SHULKER_BOX.asList()`, `Blocks.BED.asList()` | neither `Blocks.BED` nor `Blocks.DYED_SHULKER_BOX` exists in 26.1.2 (per-colour bed and shulker constants instead) | use the **tags** (`BlockTags.BEDS`, the shulker-box tag) if both versions have them. Needs a jar check of the two tags | **D4**: which block set "bed" and "dyed shulker box" must mean for the cache, if one of them has no single tag. Propose: the tag, and if no tag exists, the explicit colour list written out once, with a test |
| `process/FarmProcess:266` | `ig.isValidBonemealTarget(...)`, `ig.isBonemealSuccess(..., BonemealSource.INTERACTION)` | `BonemealableBlock` has `isBonemealSuccess(Level, RandomSource, BlockPos, BlockState)` with **no source argument** and `getType()` returning `BonemealableBlock$Type` | a bridge that calls the 4-argument form on 26.1 and the 5-argument form with `INTERACTION` on 26.3 | none (mechanical), but it changes the bonemeal argument's meaning on 26.1 - to be checked against the source of `getType()` |
| `command/ExampleBaritoneControl:100` | `Blaze3D.openUri(URI)` | `net.minecraft.client.renderer.Blaze3D` is absent; the method is not in `Util` either | a bridge that opens the URI through whichever class holds it, or skips with a log line on 26.1. This is a demo command, so skipping is acceptable | none (low-stakes) |

**Summary of the item:** 19 sites measured. 1 site moves to the renderer item. 3 sites are mechanical bridges. 1 is a bridge with a semantic question (D3), 2 are behaviour questions of the same kind as the animation (D1, D4), and **1 is the wire-level question (D2)**, which I do not think should be decided by the person who wrote the bridge. D1 through D4 are listed for the owner's ruling before any bridge is written.

**What this item does not yet establish:** that any bridge compiles on 26.1.2, that any bridge is behaviour-preserving, and that the 26.3 build is still green after the change. All three are owed to the implementation commit.
