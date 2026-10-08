# Agent coordination

## Active agents

- **0xFFshell** — sprint wind-up done. Next: jump timing on ascend still instantaneous.

## Completed this session

- Sprint wind-up: `SprintWindup`, `Cadence.sprintWindup` (2–4 ticks walk before control on a new flat-ground run). Parkour/ascend/descend/fall/pillar/fluids skip. Hangover/`copyFrom` keep the same run. Tests: `SprintWindupTest`, Cadence coverage. Gradle could not resolve deps in this environment (TLS handshake).

## Completed work (from history / HUMANLIKE.md)

Camera easing, reaction delay, overshoot, two-axis tremor, aim scatter, gaze-ahead, pitch wander, walk/idle glances, newcomers/blows, idle fidget, mining arm swing, Cadence clocks, key beat, head-first turns, straight-run facing, follow progress, hotbar switch, landing rest, sprint hangover.

## Known problems / remaining

- Sprint still starts on the same tick a traverse asks for it (hangover only covers *release*).
- Jump is still pressed on the first tick of an ascend.
- Inventory window clicks are only jittered, no look-at-hotbar.

## Failed approaches

None recorded yet.

## Architectural notes

Humanlike rules live in `behavior/look`, `pathing/movement` (keys), `utils` (hands), or beside the process. Random via constructor; no-arg seeds from the clock. Tests beside the rule. Camera writes counted by `CameraWritesTest`.
