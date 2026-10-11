# Validation ledger — 2026-10-11

Author: **QuartzHeron** (`QuartzHeron <quartzheron@agents.invalid>`). Investigation/testing only. This is evidence for a proposed plan, **not implementation approval**.

## Baseline and attribution

- Product baseline: `f1058fa8345ff26a1c7e95cb1fd9f522bcc661d2` on GitLab branch `26.3`. The tested tree was coordination tip `b5642c630a2a835da51810505a8a1b34168f66f0`; its product source/build/loader trees are identical to the product baseline. Later evidence and team-registration commits do not implement product changes.
- Official upstream `cabaletta/baritone` branch `26.3`, independently queried with `git ls-remote`, was `25111daedf1d59e6a8dfb5a3e61885cdb8d953df`. That object is the merge-base and an ancestor of the product baseline: **0 upstream-only / 281 fork-only commits**. This is a comparison with the matching version branch, not a claim that upstream master has no subsequent work.
- The fork's change set outside coordination/patch archives spans 127 files, approximately +19,149/−460 lines. Inspected `AStarPathFinder`, `AbstractNodeCostSearch`, and `CachedWorld` changes route logging; they are not evidence of a replacement solver. Most investigated new machinery concerns aiming, movement/interaction cadence, lifecycle guards, testing, documentation and the headless harness.
- All inherited implementation and tests remain attributed to their original authors. Running a test or reviewing a patch is not authorship of the underlying engineering.

## Results actually executed on this box

| Check | Result | Scope / evidence |
|---|---|---|
| Java / build environment | Zulu JDK **25.0.4.1**, jmods checked; Gradle **8.14.4** | `evidence/baseline.json`; explicit JDK, not the system JDK 11 |
| Existing unit suite | **383 tests / 47 classes; 0 failures, errors or skips**, exit 0, 7m30s | Aggregate from generated JUnit XML; `evidence/baseline-test-summary.log` |
| Full `build` | **exit 0**, 5m10s; 36 actionable tasks, 23 executed / 13 up-to-date | Fabric, Forge, NeoForge, tweaker, remapping, ProGuard and distributions; `evidence/baseline-build-summary.log` |
| Inherited real-game scenes | **6/6 PASS**, 0 caught tick exceptions | Stairs; four-wide bridge; three-wide parkour; mining (6 collected from 27 placed ore); farming (24 harvested, 23 replanted); 25-second explore. `evidence/scenes.log` |
| Inherited smoke | **exit 0**, 0 caught tick exceptions | 36-block walk reaches its end; Save and Quit with `ElytraProcess` alive. `evidence/smoke.log` |
| Controlled camera A/B | **local camera/server-facing distinction reproduced** | Screenshots and per-tick server trace below |
| Existing patch integrity | **49/49 apply-checks pass at their declared commit parent; 48 reproduce exact commit trees** | `evidence/patch-audit.json`; patch 0022 differs only because its commit also includes its own patch file. All 49 header commits are ancestors of `origin/26.3` |
| Optional smooth-yaw expression | `average(179, −179) = 0`, rather than the nearby seam direction ±180 | `evidence/yaw-seam-expression.json`; numerical expression reproduction, **not** an in-game smoothing reproduction. The averaging code is inherited from upstream |
| GitLab CI | **Not green**: inspected latest three pipelines failed before acquiring a runner | `evidence/ci-status.json`: `failure_reason=ci_quota_exceeded`, no start time, no runner. Latest read includes `f570460d`/`071411ff`/`e6b179e5`; earlier publication/85eda/b70 results remain in that file’s history. A local successful build is not a successful remote pipeline |

The client was **Minecraft 26.3 / Fabric 0.19.5 / Java 25**, using the locally built Fabric dev-shadow jar (5,108,243 bytes). It was an offline, software-rendered, integrated-server client with a fresh peaceful flat survival world. It has been stopped after the smoke test; there is no promised unattended execution.

The smoke test proves teardown did not throw on this run. It does **not** prove a successful flight, an auto-swap finishing across every GUI boundary, or flight take-off from a standing teleport. The scene assertions are useful but limited: notably farm PASS does not mean every harvested crop was replanted (the recorded count was 23 of 24).

## Reproducible baseline commands

A resource-limited box used one Gradle worker and a non-repository init script to cap the test JVM (320 MiB, SerialGC, one fork). No source, tests, wrapper, dependency or checked-in build configuration was altered to make the run pass.

```sh
export JAVA_HOME=/home/user/.cache/engineering-jdk/current
export PATH="$JAVA_HOME/bin:$PATH"
export GRADLE_USER_HOME=/home/user/.cache/engineering-gradle
./gradlew test --no-daemon --max-workers=1 --console=plain \
  --project-cache-dir /home/user/.cache/engineering-project \
  -I /home/user/.cache/engineering-lean.gradle \
  -Dorg.gradle.jvmargs='-Xmx640m -XX:MaxMetaspaceSize=256m -XX:+UseSerialGC -XX:ReservedCodeCacheSize=64m -XX:ActiveProcessorCount=2'
# Then the same invocation with build in place of test.

export BARITONE_HEADLESS=/home/user/.cache/baritone-audit-client
bash scripts/headless/scenes.sh
bash scripts/headless/smoke.sh
```

On a larger machine, follow the existing `SETUP.md`/build instructions rather than depending on these ephemeral cache paths. Full local raw logs remain in `/home/user/engineering-logs`; the committed aggregates are the durable, portable evidence. Before rerunning, verify installed tools/caches rather than assume they persist. Do not run Gradle concurrently with the software-rendered client on a 1.9 GiB/no-swap box.

## Controlled camera experiment

Both runs used:

- `antiCheatCompatibility=true`, `blockFreeLook=false`, `remainWithExistingLookDirection=false`;
- flat ground, eastward 60-block goals, initial yaw/pitch `0/0` (south);
- identical client/world/build; only `freeLook` changed between the compared runs;
- F3 screenshot for **local** facing and inherited `trace.py` datapack for **server** facing.

| Mode | Local F3 facing during eastward movement | Contemporaneous server trace | Evidence |
|---|---|---|---|
| `freeLook=true` | yaw **0**, pitch **0**, south; X ≈20.927 | yaw ≈**−71.8**, pitch ≈**4.3** | `evidence/camera-server-mode.png`, `evidence/camera-server-mode.csv` |
| `freeLook=false` | yaw **−89.7**, pitch **−5.6**, east; X ≈20.927 | yaw ≈**−90.9** | `evidence/camera-client-mode.png`, `evidence/camera-client-mode.csv` |

This runtime result matches `LookBehavior.Target.Mode.resolve` and PRE/POST restoration: navigation is SERVER with default free look, but block interaction is CLIENT with default `blockFreeLook=false`. A local view that remains south while walking east does **not** prove the server receives a south-facing player, nor prove the humanlike aim is absent. Turnstone independently re-derived the source citations and sharpened this instrument distinction in Chat (registration `b70b1064`). A second remote client's actual head/body interpolation remains untested.

The first client-mode CSV row is a **pre-teleport** position. The untrimmed aggregate would falsely report 127.2 blocks and 9.18 blocks/s. `evidence/camera-client-mode-trimmed-summary.log` removes that row, records approximately 60 blocks and 5.31 blocks/s, and explicitly names the exclusion. Trace speed is a six-tick-window estimate on software rendering, not a calibrated performance benchmark. Do not compare the two runs as matched-random-seed humanisation experiments.

The test disabled `remainWithExistingLookDirection` in **both** runs to isolate the camera split; it is not an experiment on every factory-default setting combination. Settings were restored to `freeLook=true` and `remainWithExistingLookDirection=true`, tracing and F3 were disabled, and the path was stopped before the smoke test. Smoke then changed `elytraTermsAccepted` in this disposable test world only.

**Not resolved by this test:** Nightjar's unexplained post-path pitch snap, optional smoothing across ±180, precise rendered body/head relationships, hostile-task interruption, neutral-mob gaze provocation, or long-flight behaviour. These remain separately labelled in the audit/roadmap.

## Independent peer validation and a substantive new finding

Turnstone has since published independent evidence (`29d2c3fd` and later own evidence commits): `evidence/turnstone-baseline.json` reports **47 classes / 383 tests / zero failures/errors/skips**, matching class-by-class; their separately implemented patch audit reproduces 49 apply passes / 48 exact trees / the same 0022 self-patch exception; matching upstream ancestry and naming counts reproduce. Their `evidence/turnstone-scenes.log` independently reports **6/6 PASS**. This review is more than a verbal agreement and is credited to Turnstone, not to QuartzHeron.

**Peer-review correction about format, not application:** 0001 and 0022 have no email-format `From` header, but both already have **git-show `commit <full hash>` headers**. QuartzHeron reread the files: 0001 identifies `3fc86ae1ae2a1e7de84b69d55ed5fb52d34e63eb`; 0022 identifies `9ca8323ca04557eb3b8180304478b468f0482e17`. They are therefore self-identifying in that format; a parser restricted to `From` needs to recognize `commit`, not infer a missing identity or require regeneration. Both independent apply/tree results are accepted; the “cannot be audited from themselves” inference is not. Preserve bodies/authors/hashes and the 0022 self-patch exception. [Workflow](workflow.md) documents both header forms; no inherited patch was rewritten.

Turnstone's `evidence/turnstone-trace-extracts.md` provides new pitch-retention evidence: fall/goal pitch ≈90, a reported follow-up flat-walk hold, forced-level control, and forced idle pitch near 40 for approximately 50 seconds. Their original bucket-only attribution is partly falsified and the historical Nightjar one-tick snap remains unexplained. QuartzHeron inspected these excerpts and the actual helper: `PitchWander#step` **does** approach a near-level preference (the existing-helper probe compiles/runs that exact source under Java 11 because it is dependency-free). A possible raw-versus-processed equality gate in `settledGaze` needs instrumentation, not a rewrite of the helper. Full run-3/4 rows/settings and actual owner/camera-mode state are still needed; run 5's post-force rows are largely stationary. See [the audit](capability-audit.md#23-other-orientation-findings-are-separate). No fix is claimed.

The peer review accepted the earlier evidence bundle; independent **proposal** review and lifecycle addendum are now delivered at `dd9ab8fd`/`071411ff`, with four required amendments incorporated. Their 30-line-by-line/legal/registry/future-code/runtime exclusions and pending confirmation are not silently broadened into blanket sign-off.

Final source review preserves additional existing protections: manager `:199` gates immediate failure to the previous winner, and pathing `:473-557` already captures a goal, locks result adoption, suppresses cancellation/exception primary failure and rejects wrong-start orphan paths. Late-event/replacement timing remains unvalidated, not a claim that no ownership check exists. Full `HUMANLIKE.md` was reread, including its deliberate non-smoothed free-look handback; this is a competing explanation to instrument, not proof of the historic snap.

The existing-helper probe source is committed as `evidence/PitchWanderProbe.java` for reproducibility. It is investigation-only, not a new product helper or an addition to the reported 383-test JUnit suite:

```sh
mkdir -p .cache/pitch-probe
javac -d .cache/pitch-probe src/main/java/baritone/behavior/look/PitchWander.java \
  docs/planning/2026-10-11/evidence/PitchWanderProbe.java
java -cp .cache/pitch-probe PitchWanderProbe
```

This dependency-free helper probe can run under Java 11; **the whole Minecraft 26.3 project still requires Java 25**. Its constructed processed-angle example shows what the current equality expression can do, not how often that value occurs in a live client.

## CI and release limits

Public GitLab job API evidence, latest read 2026-10-11T15:48:51.692538+00:00, reports a quota failure rather than a compiler trace. **Operator action is required** to restore permitted runner capacity/quota or approve a runner alternative. Do not weaken tests, replace dependencies, claim CI success, or retry identical doomed pipelines to conceal that infrastructure blocker.

The public release `v26.3` does not ancestor the committed path-bounds fix `901a999b`, Elytra teardown fix `29b70536`, or gaze-pitch fix `87581e58` (each `git merge-base --is-ancestor FIX v26.3` returned 1). That establishes a source-tag release gap, **not** the contents of separately uploaded binaries. Release asset hashes/contents and Forge/NeoForge runtime launch still need verification before distributing a new release. Never move an existing public tag silently.

## Validation still required after implementation approval

1. Reproduce hostile/neutral/passive scenarios with explicit policies, multiple mobs, low health/equipment, unloaded/despawned entities and entity-ID reuse.
2. Exercise task interruption during mining, building, following, farming, exploration, parkour, falling, swimming and flight; safe-point pause, replan, cancellation and world change.
3. Exercise inventory transaction teardown with a container opened mid-sequence and with disconnect/world replacement; prove no orphan cursor, stale-window click or duplicate item.
4. Capture local camera, server yaw/pitch, effective interaction ray, movement keys and task owner together through path end, chat/menu opening and user takeover.
5. Long, obstructed flight with launch/landing and low durability/rockets, not merely an active process at disconnect.
6. All four loader builds, actual runtime loading for supported loaders, API/integration compatibility, old settings/state roundtrip and licensed release contents.
7. Run meaningful independent review and full Gauntlet verification on each significant code change; this ledger does not approve future changes in advance.

### New independent proposal review and run-7 moving-window correction

`peer-review-turnstone.md` (review at `57822a49`, addendum `071411ff`) verifies async event/last-winner risk, BRACE/FLEE state-versus-path costs, five conservative response bounds, hard-coded API/ID/state compatibility, and five lifecycle source claims. Four required amendments are incorporated in audit/roadmap/identity/master; no feature approval or future-runtime validation is implied.

Peer run7 (`e6b179e5`) covers a reported50.1-block flat walk with freeLook/antiCheat true, blockFreeLook/smoothLook false. The committed every-five-tick rows include stationary startup through counter331, motion by336, traversal through511 and goal arrival516. Independent source-row selection **336–511, 36 samples** gives min **27.37**, max **39.12**, mean **33.02**, median **32.45**, **zero below20**. Peer 515-sample median40/350near40 is a startup-inclusive window, not moving-only. Full515-row source CSV and raw/processed/guard/owner fields are not committed. Retained steep pitch is supported; gate cause/cadence/indefinite exact40 remains unproved. Run3 distance is corrected to ~5 post-fall/~15 total; run5 stationary control remains weak. See `evidence/qh-run7-moving-window.json`.

Peer independently re-queried pipeline symptoms but could not read job reasons under their credential’s granular scope. This box’s ledger uses **unauthenticated public GET** with actual global pipeline IDs and records `ci_quota_exceeded`/no runner/start. Preserve that provenance difference; no need to print/share/change credentials.
