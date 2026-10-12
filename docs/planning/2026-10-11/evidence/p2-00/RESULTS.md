# P2-00 runtime A/B: results (Sandpiper)

**Evidence caveat.** These are readings I recorded while driving the run. The F3 screenshots and the raw trace extracts were lost in a sandbox reset before they were committed, so this file is the only durable record. Each reading is single, not repeated. Treat them as observations, not as a replicated measurement.

Environment: headless Fabric client, Minecraft 26.3, superflat world, software rendering, Baritone built from 45df704c with a dirty tree (mode-only changes). Server-side facing comes from the inherited `trace` datapack, read by `scripts/headless/trace.py`. Local facing comes from the F3 line `Facing: ... (yaw / pitch)`.

Settings unless stated: `freeLook=true`, `blockFreeLook=false`, `antiCheatCompatibility=true`, `elytraFreeLook=true`, `remainWithExistingLookDirection=true` (default). The 2026-10-11 validation run used `remainWithExistingLookDirection=false`.

| Case | Setting changed | Expected (resolve) | Server yaw during action (trace.py) | Local F3 facing | Result |
|---|---|---|---|---|---|
| 1. Default, walk 60 east | none | SERVER | yaw −92.0 to 0.2 while moving; pitch median 6.5 | `south (0.0 / 0.0)` at x 64.3, 22 s into the walk | Matches SERVER: local view unchanged, server aim moved. Reading taken near the end of the walk. |
| 2. antiCheat off, walk 60 east | `antiCheatCompatibility=false` | NONE | yaw −0.4 to 0.5; pitch −0.33 to 0.33 | `south (0.0 / 0.0)` at x 64.4 | Matches NONE: nothing sent for the aim. |
| 3. freeLook off, walk 60 east | `freeLook=false` | CLIENT | yaw −121.3 to −42.4; pitch median 1.9 | `east (−101.1 / 40.5)` at x 64.6 | Matches CLIENT: local view turned to the walk. |
| 4. Mining, blockFreeLook off | `blockFreeLook=false` (default) | CLIENT | yaw swings −179.6 to 177.7; pitch median 39.4 | `east (−105.1 / 52.3)` while digging down | Matches CLIENT: local view turned to the block. |
| 5. Mining, blockFreeLook on | `blockFreeLook=true` | SERVER | yaw swings −179.5 to 178.9; pitch median 36.6 | `south (0.0 / 0.0)` at x 75.8, y −63 | Matches SERVER: local view unchanged. |
| 6. Elytra, elytraFreeLook on/off | not run | SERVER / CLIENT | not measured | not measured | OPEN |

Notes:
- Case 1 read 22 s into the walk, at which point the player was already at x 64.3, so it is an end-of-walk reading.
- One slime killed the player during setup. That run was discarded and the player respawned. Cases 1 to 5 each started from a fresh position.
- Case 5's first screenshot had F3 off and no facing line, so it is not used. The F3 reading came from a later screenshot.
- Cases 1 to 3 each walked back west on default settings before the next case.
- No randomness was added. All setting changes were made with `#set` in chat.

## Status

P2-00 runtime A/B: 5 of the 6 items measured (default, antiCheat off, freeLook off, blockFreeLook off, blockFreeLook on). The elytra case is OPEN, so the card is NOT done.
