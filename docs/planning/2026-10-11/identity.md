# Identity recommendation and compatibility-preserving migration

Author: **QuartzHeron**. Research dated **2026-10-11**, with independent naming checks by Turnstone. **Recommended winner: Wayquill.** This selects a proposed name; it does **not** rename the repository, packages, loader IDs, configuration, commands, artifacts or product. Application requires explicit human approval.

## 1. Should the fork have its own identity?

**Yes — recommend a distinct user-facing identity, applied in stages after approval.** The matching upstream branch is an ancestor, not an unrelated codebase; its solver/history/attribution remain foundational. But this fork has 281 product-baseline fork-only commits, substantial context-aware look/movement/interaction machinery, dedicated regression/fuzz/write-surface tests and a real-client harness. Independent branding can accurately communicate that maintained behavioural focus and direct support to the fork, without claiming newly invented pathfinding or already-implemented survival/combat.

A staged cosmetic identity is worth doing; a breaking public API/mod-ID/state rename is **not** necessary to earn that identity. The project should plainly say **Baritone-derived**, not present inherited work as a clean-room agent. Optional threat response and broader autonomy are roadmap features, not current branding claims.

## 2. Six candidates, same criteria and weights

Scores are subjective comparative judgments **1 (weak) to 5 (strong)**, not market research, legal opinions or measurements. Every candidate uses the same weights:

- Memorability/pronunciation **20%** (M)
- Fit with purposeful navigation, interaction and broader task behaviour **20%** (F)
- Product distinctiveness/searchability **20%** (D)
- Technical practicality (ASCII slug, module/artifact readability) **15%** (T)
- Public conflict signal in the researched scope **15%** (C; high is less concerning, never “legally clear”)
- Extensibility beyond mining/pathfinding **10%** (E)

Weighted score = `(20M + 20F + 20D + 15T + 15C + 10E) / 5`, out of 100. A serious close-domain collision can veto a name despite a good semantic score.

| Rank | Candidate | M | F | D | T | C | E | Weighted /100 | Judgment |
|---:|---|---:|---:|---:|---:|---:|---:|---:|---|
| **1** | **Wayquill** | 4 | 5 | 5 | 5 | 4 | 5 | **93** | Winner: purpose/path (“way”) plus deliberate action/script (“quill”); distinctive compound with room for more than movement |
| **2** | **VoxelHelm** | 4 | 4 | 4 | 5 | 4 | 4 | **83** | Strong fallback; steering/coordination and voxel context, but more generic/descriptive and can sound like an armor item |
| **3** | **Trailwarden** | 4 | 5 | 3 | 4 | 2 | 5 | **76** | Good survival/navigation meaning; exact software and close route-safety collisions materially weaken it |
| 4 | Pathloom | 4 | 5 | 3 | 5 | 1 | 4 | 74 | Elegant task/path metaphor, rejected because an existing outdoor trip-planning product occupies the same semantic space |
| 5 | Strider | 5 | 5 | 1 | 5 | 1 | 3 | 68 | Easy, appropriate movement word, rejected as saturated software/Minecraft terminology |
| 6 | Cairn | 5 | 4 | 1 | 5 | 1 | 4 | 66 | Excellent wayfinding metaphor, rejected for broad saturation and a direct Minecraft-agent name collision |

### Strongest three, ranked decisively

1. **Wayquill — recommend approval.** Short, pronounceable “WAY-kwill,” lowercase `wayquill`, ASCII-safe and memorable without being restricted to combat/mining. Quill is a generic/root-adjacent word in existing software/Minecraft uses, so the compound rather than the root must be the brand. A zero-result search is only a bounded low-collision signal. Its purpose/action metaphor fits the *implemented* fork as well as careful future autonomy.
2. **VoxelHelm — retain as the practical fallback.** Strong navigation/control meaning with low obvious exact-name collision in checked public software indices. It is less original as a voxel compound, and “helm” can imply an equipment item rather than an agent; it also ties the identity tightly to voxel games. Choose it if the human dislikes Wayquill or registration/legal checks find a serious Wayquill conflict.
3. **Trailwarden — acceptable only after a deliberate conflict decision.** Very clear survival/route guardian positioning, but one exact-name repository describes a **Route Safety Application**, and another an AWS CloudTrail compliance validator. The strong metaphor does not erase that collision. Do not use it as a supposedly uncontested alternative.

### Winner's proposed product language and direction

- **Name:** Wayquill. Canonical display spelling, not inconsistent WayQuill/Way Quill variants.
- **Current-capability description:** “A Baritone-derived Minecraft navigation and automation agent with context-aware movement, aiming and interaction.”
- **Tagline:** **“Purposeful paths. Deliberate actions.”**
- **Provenance line:** “Built on Baritone; independently maintained in this GitLab fork. Upstream licenses, authors and history retained.”
- **Brand direction (proposal, no asset created):** calm slate/teal navigation palette with a restrained amber action cue; waypoint/route or quill-stroke motif; practical developer-facing diagrams and readable typography. Avoid a claim of omniscient AI, full survival, combat readiness or “human indistinguishable” behaviour.
- **Module convention:** future logical capability labels `wayquill-navigation`, `wayquill-awareness`, `wayquill-survival`, `wayquill-behavior`, `wayquill-validation`. These are documentation/distribution conventions, **not newly created Gradle subprojects**. Keep actual `fabric`, `forge`, `neoforge`, `tweaker` build structure and `baritone.*` public packages during compatibility stages.
- **Why not keep Baritone as the sole name?** It obscures the independently maintained behavioural fork and directs inherited support metadata to upstream. Keep Baritone in provenance/compatibility/API references, not erase it.

## 3. Public collision research and its limits

Committed machine-readable evidence:

- `evidence/naming-api.json`: exact timestamped GitHub repository-name and Modrinth searches, 12 successful queries at approximately 13:22 UTC.
- `evidence/additional-naming-api.json`: successful public GitLab queries for the two leaders, Maven Central VoxelHelm result, and explicit Wayquill Maven timeout.
- `evidence/naming-conflicts.json`: targeted public repository metadata, including close-domain descriptions; metadata claims are not functionality validation.
- `evidence/turnstone-naming-review.json`: independently repeated repository counts and exact endpoint/account checks, with their HTTP 500 GitLab limitation kept visible.

| Candidate | GitHub repo-name hits | Modrinth search hits | Concrete concern / additional check |
|---|---:|---:|---|
| Wayquill | **0** | **0** | Public GitLab search 0 in QuartzHeron's successful run; Maven Central query **timed out**, not 0. Turnstone's exact Modrinth slug/GitHub account probes found no public object; they do not reserve availability. Root/word adjacency remains. |
| VoxelHelm | **0** | **0** | Public GitLab search 0, Maven Central query 0 in successful runs; exact public endpoints unoccupied at peer query time, not proof of registration rights. General web results included unrelated voxel/helmet terminology. |
| Trailwarden | **3** | **0** | Exact-name software: [`randerson-trailwarden/trailwarden`](https://github.com/randerson-trailwarden/trailwarden), route-safety application; [`AnandSundar/TrailWarden`](https://github.com/AnandSundar/TrailWarden), CloudTrail tool; plus `brunoribeiro2k/trailwarden` without description. No inspected private/registry/trademark status. |
| Pathloom | **19** | **0** | Exact software repositories and documented outdoor trip-planning application; a close semantic collision, not just a random similarly spelled repo. |
| Cairn | **2038** | **1** | [`VasilisDragon/cairn`](https://github.com/VasilisDragon/cairn) describes an AI Minecraft agent using Mineflayer; this is a direct name/domain collision regardless of whether its claimed features work. Broader short-word saturation. |
| Strider | **1361** | **32** | [`Strider-CD/strider`](https://github.com/Strider-CD/strider) describes a CI/deployment server; multiple Minecraft uses and the game's own Strider terminology make discovery poor. |

Pathloom's named outdoor-planning product is independently documented in a 2021 report describing route/trip planning and its 2019 founding; that is evidence of prior product use, **not a claim about its 2026 operating status**. [1](https://gearjunkie.com/news/pathloom-app-all-in-one-trip-planning)

Quoted general web searches for the leaders did not surface an obvious same-name software/Minecraft product in the returned results. Wayquill also has unrelated personal-name usage and “K-Way Quill” retail word adjacency; the latter appears in a clothing listing, not an exact compound software project. This is a limitation on “newly coined/unique,” not a claim about any person's affiliation or a legal conflict. [1](https://westsidenewsny.com/schools/2024-03-31/cte-students-shine-at-skillsusa-regional-competition/) [3](https://www.lyst.com/shop/mens-k-way-puffer-jackets/)

**Uncertainty:** searches are bounded public indices and may miss private, unindexed, very recent, trademark, app-store, domain, npm/PyPI/Maven or jurisdiction-specific uses. Turnstone's GitLab query returned HTTP 500 later; preserve both timestamps rather than relabel an endpoint error as no conflicts. A 404 exact-slug/account response means no public resource at that time, **not guaranteed availability, reservation, legal clearance or transfer rights**. Domains, commercial trademarks and actual publishing namespace registrations remain **unchecked**. Perform the operator's appropriate registry/trademark/domain review and namespace reservation before external launch. Do not buy/register/publish anything without authorization.

## 4. Actual rebrand inventory (not blanket rename)

| Surface | Current inspected references | Planned treatment / compatibility risk | Validation / rollback |
|---|---|---|---|
| Repository hosting | GitLab `international-duck-incorporated/Baritone`, branch `26.3`; existing tags/releases/history | Operator may approve project display/slug later. Keep branch/version lineage and authors. Clone/issue/wiki/release links need explicit inventory and redirect check. | Old/new fetch and API URLs; release/source links; revert hosting metadata if redirects fail, never rewrite commits. |
| Root build identity | `settings.gradle:42` `rootProject.name='baritone'`; `gradle.properties` `maven_group=baritone`, `archives_base_name=baritone`; `build.gradle` uses them | Stage 1 leaves group/artifact coordinates alone. Stage 2 can change display/archive naming with compatibility aliases; group remains pending explicit publication decision. | Full loader build, task/artifact names and downstream scripts; forward revert only intended build changes. |
| Java API/internal names | `baritone.*`, `baritone.api.BaritoneAPI`, `IBaritone*`, `BaritoneProvider`; reflection `BaritoneAPI.java:38` → `baritone.BaritoneProvider` | **Retain.** Cosmetic branding is not justification for a public/binary namespace break. Do not invent a current ServiceLoader registration: this provider is reflectively loaded. | API consumer/reflection fixture and class/method descriptors; retaining package names alone is not proof all upstream implementors work (fork API already has additions). |
| Fabric metadata | `fabric/src/main/resources/fabric.mod.json`: ID `baritone`, Baritone name, inherited authors/contact/description/license | Change approved display/description/fork support URLs only; keep ID/entrypoints/dependencies/license and upstream credits. | JSON parse, boot/provider discovery, dependency fixture, one mod instance. Roll back metadata via a new commit. |
| Forge metadata/annotation | `forge/.../META-INF/mods.toml`; `BaritoneForgeModXD.java` uses **`@Mod("baritoe")`** | Existing ID is **baritoe**, not silently “fixable” to baritone. Preserve during branding stages. Display/description/contact changes only on allowlist. | Annotation/TOML agree; Forge boot and dependent-ID fixtures. No duplicate legacy/new ID registration. |
| NeoForge metadata/annotation | `neoforge/.../META-INF/neoforge.mods.toml`; `@Mod(value="baritoe", dist=Dist.CLIENT)` | Same retained compatibility ID, separate loader verification; don't assume Fabric naming rules. | NeoForge boot, client-only loading, manifest/dependency checks; metadata rollback. |
| Tweaker/launch/mixins | `tweaker/.../BaritoneTweaker`; launch class names, mixin JSON/refmap resource names, loader entrypoints | Retain technical names/paths through stages 1–2; class/resource renames need all string references and remapping reviewed. | Tweaker build/load where supported, mixin application/refmap tests, no broken launch strings. |
| ProGuard/API keeps | `scripts/proguard.pro`, buildSrc remap/ProGuard tasks and `baritone.api` keep rules | Retain and verify; do not obfuscate renamed provider/bridge/API accidentally. | Obfuscated and API jars actually load provider; class descriptor comparison and source/notice inclusion. |
| Artifacts/distributions | buildSrc distribution names, API/standalone/unoptimised/loader jars, hash/mapping outputs, CI artifact patterns | Stage 2 only: agreed Wayquill filenames with legacy download aliases of the **same jar hash**. Do not co-install both aliases. | File list/hash manifest and all-loader contents; old URLs/assets kept, rollback points to old known-good artifact. |
| Commands/settings | `#` prefix/settings, `baritone` modified-settings alias, command/default setting names, integration calls | Retain command syntax and keys; new brand alias is optional future scope, not a compulsory rename. Never reset settings to create a new identity. | Old scripts/commands and settings roundtrip, alias conflict tests if approved, old prefix still works. |
| Logs/headless parsers | `[Baritone]` display in API Helper, logger settings and harness/trace message patterns | Approved display-only change must audit parser assumptions; don't rename protocol/debug fields automatically. | `scenes.sh`/`smoke.sh`/trace under new display and old logs; bounded diagnostic schema compatibility. |
| Global configuration | `Baritone.java:97` game-directory `baritone`; `SettingsUtil.java:54,116` `baritone/settings.txt` | **Retain stages 1–2.** Later path migration requires explicit backup/dual-read/precedence and consent; never silent empty settings. | Existing file loads identically; state path untouched in cosmetic rollback. |
| World/cache/waypoint state | `cache/WorldProvider.java:137,155-157`, per-world `baritone` and server-derived storage, readme URL; region/waypoint formats | Keep names/formats and world separation. Update only approved readme/support prose; no merging caches across servers/worlds or destructive copy/delete. | Existing settings/cache/waypoint fixtures; identity/world checks; no rewrite on startup. |
| User/developer docs | README, SETUP/FEATURES/USAGE, HUMANLIKE, wiki/build/API docs, screenshots/examples | Update display/product/support distinctions; retain actual command/API examples and upstream links where they explain provenance. Keep history/AGENTS/patch subjects as historical identities. | Documentation against code/settings; link checks; no roadmap fiction listed as implemented. |
| CI/release/URLs | `.gitlab-ci.yml`, `.github/workflows`, GitLab release `v26.3`, inherited GitHub homepage/issues/contact URLs | Fix approved fork support links without erasing upstream credit; release branding gated by P0 CI/source/artifact verification. Capacity failure is not cured by renaming. | Actual pipeline/report/assets; clean source SHA/version; no silently moved tags or overwritten old artifacts. |
| License/attribution/assets | LICENSE, source/build headers, `LICENSE-Part-2.jpg` (“No anime”), LGPL wording/README references, inherited authors/history | **Preserve.** Image is inherited and unchanged; no legal conclusion about an “anime exception.” No licence text/header/author replacement under a rebrand. Proposed new art only after approval. | SHA-256/source diff unchanged for protected licence files; release notices/source offer; operator/legal review for any ambiguity. |
| External namespaces | Modrinth/Maven/app accounts/domains not currently reserved by this work | Human/operator confirms rights and actual reservation before publishing. Public 0/404 checks are not ownership. | Record actual authorized registration/publication results; don't promise a URL that does not exist. |

A committed read-only census, `evidence/identity-references.json`, maps **475 tracked reference-bearing files** with matched line numbers, category and short samples, and protected licence SHA-256 values. Historical coordination/patch/planning references are excluded from that census and remain protected; generated release assets are covered separately above. This is a reference inventory, **not a rename allowlist**.

This inventory distinguishes implemented technical identity from proposed display identity. It deliberately leaves legacy `baritone` and `baritoe` visible where compatibility/attribution needs them. No rename tool has been run.

## 5. Staged migration, gates and rollback

### Stage 0 — Decide and inventory (this planning phase)

Select Wayquill as the proposed winner; record conflicts/uncertainty, exact references and protected surfaces. Seek explicit human name/scope approval and independent compatibility review. Reserve external namespaces only when separately authorized. **Rollback:** no applied identity/state changes to undo.

### Stage 1 — Cosmetic display/provenance only

Approved allowlist: README/about/title/metadata display names and truthful descriptions, fork support links, optional log display text with parser audit. Keep source packages/class names, provider reflection, loader IDs/annotations, commands, settings keys/directory, caches, waypoints, group coordinates, version branch and licences/history. Clear “Wayquill (Baritone-derived)” transition wording.

**Gate:** full build + supported-loader boot, same API/provider/commands/settings/state, scene/smoke/parser checks, protected licence hashes, substantive independent review. **Rollback:** a forward revert of the scoped display/metadata commit; same state and API continue to work. Never reset shared history.

### Stage 2 — Compatible distribution/hosting transition

If separately approved, artifact display/download names may use `wayquill-*`; retain existing release links and legacy filename aliases of identical payloads for **at least one documented release cycle**, with an operator-approved longer support window if integrations require it. Repository slug/display/redirect, registry coordinates and public issue links are operator decisions, not automatic text replacements. Users install one jar, not the old and new aliases together.

**Gate:** source-tag ancestry, actual CI capacity/run, asset hashes/manifests, release licence/source materials, old/new URL checks and downstream fixture checks. **Rollback:** publish a new corrective release/metadata commit and restore approved pointers; preserve old assets/tags/history. No state conversion means existing configurations remain usable.

### Stage 3 — Optional major public namespace/state migration (deferred)

Only if the operator later has a concrete benefit that outweighs ecosystem breakage: new mod IDs/packages/group/API bridges or data directory names. First design legacy adapters/deprecation period and dependency aliases per loader; keep one provider, not two installed agents. For state, back up originals, use versioned manifest/world identity, explicit precedence when both old/new files exist, copy-on-migrate/dual-read as reviewed, atomically commit new files, retain originals until proven; no merge across worlds or silent delete.

**Gate:** separate major-scope approval, independent API/state design review, external consumer matrix, old/new-state roundtrip and rollback/crash tests, documented retirement timeline. **Rollback:** old jar reads preserved original data; new state never destructively overwrites it. This stage is **not authorized** by cosmetic-name or initial engineering approval.

## 6. Human decisions requested

1. Approve **Wayquill** as the product's new display identity, choose VoxelHelm instead, or defer application while retaining the required recommendation?
2. Approve Stage 1 only (recommended first), also approve compatible Stage 2 distributions/hosting, or keep all technical branding unchanged for now?
3. Who owns namespace/legal/hosting/release permission checks and the compatibility window? No assumed domain/registry ownership.
4. Keep current free-look default versus opt into a visible-bot profile? This is behaviour, **not** an identity migration and needs its own decision.
5. Restore CI quota/capacity and authorize a new source-aligned release? Identity approval alone cannot authorize destructive tag/asset/state changes.
