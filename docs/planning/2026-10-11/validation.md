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
| GitLab CI | **Not green**: inspected latest three pipelines failed before acquiring a runner | `evidence/ci-status.json`: `failure_reason=ci_quota_exceeded`, no start time, no runner. Includes evidence tip `85eda9f8` and Turnstone registration `b70b1064`. A local successful build is not a successful remote pipeline |

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

## CI and release limits

Public GitLab job API evidence, read 2026-10-11 13:38 UTC, reports a quota failure rather than a compiler trace. **Operator action is required** to restore permitted runner capacity/quota or approve a runner alternative. Do not weaken tests, replace dependencies, claim CI success, or retry identical doomed pipelines to conceal that infrastructure blocker.

The public release `v26.3` does not ancestor the committed path-bounds fix `901a999b`, Elytra teardown fix `29b70536`, or gaze-pitch fix `87581e58` (each `git merge-base --is-ancestor FIX v26.3` returned 1). That establishes a source-tag release gap, **not** the contents of separately uploaded binaries. Release asset hashes/contents and Forge/NeoForge runtime launch still need verification before distributing a new release. Never move an existing public tag silently.

## Validation still required after implementation approval

1. Reproduce hostile/neutral/passive scenarios with explicit policies, multiple mobs, low health/equipment, unloaded/despawned entities and entity-ID reuse.
2. Exercise task interruption during mining, building, following, farming, exploration, parkour, falling, swimming and flight; safe-point pause, replan, cancellation and world change.
3. Exercise inventory transaction teardown with a container opened mid-sequence and with disconnect/world replacement; prove no orphan cursor, stale-window click or duplicate item.
4. Capture local camera, server yaw/pitch, effective interaction ray, movement keys and task owner together through path end, chat/menu opening and user takeover.
5. Long, obstructed flight with launch/landing and low durability/rockets, not merely an active process at disconnect.
6. All four loader builds, actual runtime loading for supported loaders, API/integration compatibility, old settings/state roundtrip and licensed release contents.
7. Run meaningful independent review and full Gauntlet verification on each significant code change; this ledger does not approve future changes in advance.
