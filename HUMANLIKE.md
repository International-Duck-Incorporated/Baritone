# What makes this fork look human

Baritone is a pathfinder, and a pathfinder looks like a pathfinder: perfect aim, perfect timing,
perfect understanding of where it is going. Nothing in here changes *what* the bot does or how well
it does it. It changes how the doing looks to somebody standing next to it, which is the only part of
the behaviour a spectator ever sees.

Everything below is the default. No setting was added to switch any of it on, and nothing here is
opt-in: a bot that has to be told to look alive is a bot that looks dead by default.

## The camera

| what | where | the rule |
| --- | --- | --- |
| The aim eases instead of snapping | `behavior/look/AimDynamics.java` | Whole mouse pixels, converted with the player's own sensitivity, capped at 18°/tick of yaw and 9° of pitch. Turns spin up over their first two ticks and ease out as they arrive. |
| The hand takes a beat to notice | `behavior/look/ReactionDelay.java` | Two to four ticks before the aim starts moving to a target it was not already pointing at. Only while standing still: the walking code reads the facing, so a lagging aim mid-walk steers. |
| ... and goes a little past | `behavior/look/AimDynamics.java` | A hand is still moving when it reaches what it was reaching for: what it aims at starts a few percent of the turn past the target and walks back onto it. Measured on the hand at the default sensitivity, a quarter turn peaks 2.4 degrees past and is exactly on target ten ticks later. Only while walking, and only for a target that says where the bot is going - anything about to be clicked is aimed at exactly. |
| The hand calls itself arrived | `behavior/look/AimDynamics.java`, `ILookBehavior#hasArrivedAt` | "Is the aim here yet" is asked of the aim, not of a hundredth-of-a-degree comparison against the target: a settled hand sits a fraction of a degree off, because it is easing and the tremor is still shaking it. |
| A target with no opinion about the pitch | `behavior/look/PitchWander.java`, `LookBehavior#settledGaze` | The gaze settles a few degrees this way and that every few seconds - triangular around level, a degree a tick, ten degrees out at the extremes - instead of holding one angle for a whole journey, and it settles *on the aim*, which is the angle the server is told and the one the arm is traced along. Only where the pitch is decoration: a swim travels by the pitch it is looking along and a glide solves for it, so in water and in the air the pitch stays where the movement put it, and a target that holds an opinion about the pitch - a block to break, a face to place against - is aimed at exactly. |
| Something moved the mouse | `behavior/look/IdleGlance.java` | A glance is dropped the moment the view is taken over, and the angles are handed back exactly. |

## Standing around

The idle repertoire only runs while a process is in control: if Baritone has nothing to do, the person
at the keyboard owns the camera and nothing here touches it.

| what | where | the rule |
| --- | --- | --- |
| A standing bot drifts | `behavior/look/HeadWander.java` | A degree of yaw and six tenths of a degree of pitch, wandering over periods of two to five seconds - the same sway that runs under a walk, and under a swim, at `IDLE_FRACTION` of it when standing, so standing, walking and swimming hand it over rather than switching it on and off. |
| ... and looks around | `behavior/look/IdleGlance.java` | A glance every three to eight seconds, held for under a second and given back, bounded to 28° of yaw and 7° of pitch, never outliving 100 ticks. Taken on the ticks with nothing aimed at and on the ones where an aim is held but the bot is standing still waiting on it - a door it cannot open, a block that has not updated, a path with nowhere to go this tick (`LookBehavior#updateHeadWander`) - so waiting for something is not the same as staring at it. |
| ... and notices people | `behavior/LookBehavior.java#somethingNearby` | One glance in three goes to the nearest player or creature within twelve blocks that is neither invisible nor behind a wall. People look at people; a bot that never does is the thing that gets noticed. |
| ... and moves its hands | `behavior/look/IdleFidget.java` | A swing at the air every ten to sixty seconds. The animation packet only - no click goes with it, so it cannot mine, place or break anything. |
| The arm swings while it mines | `utils/BlockBreakHelper.java` | Vanilla's own two lines: the swing and the punch packet. Without them the arm never moves for anyone else, however good the camera is. |

## Timing

Nothing repeats on an interval that is always the same length. `utils/Cadence.java` holds the clocks:

| clock | used by | reads |
| --- | --- | --- |
| `Cadence.jitter` | every repeated interval | The setting, with a tick of slack either side, drawn afresh: the average stays on the setting. |
| `Cadence.Settle` | both mouse buttons | One to two ticks between the crosshair arriving and the swing. |
| `Cadence.retargetDelay` | breaking an instant block | One to three ticks before the next one. |
| `Cadence.Run` | the builder's hold-off after breaking | A rest timed from the thing that just happened; four to six ticks. |
| `Cadence.Repeat` | the three goal rescans (mining, getting to a block, farming) | The setting, drawn afresh each time, first scan immediate. |

Other things that were on a grid and are not any more: the sprint key trails off over one to three
ticks instead of stopping on the tick the movement stops asking (`Cadence.sprintHangover`,
`PathExecutor`); the tool in hand is kept unless the next tool is properly faster (`ToolSet.SWITCH_GAIN`,
a quarter faster); inventory moves are spaced by a redrawn interval rather than a fixed one.

## Steering

| what | where | the rule |
| --- | --- | --- |
| Which keys walk which way | `pathing/movement/MovementOption.java` | The eight combinations of four keys, compared by *angle* to the direction the movement asked for, never by a scaled distance. |
| The camera and the keys are chosen together | `MovementOption#lookYaw`, `MovementHelper#moveTowardsWithSlightRotation` | The camera turns in whole 45° steps and the keys cover the rest, both measured against the angle the camera is being told to hold - because that is the angle the game turns the pressed keys by. Chosen separately, the two roundings disagree by a whole step and the bot walks 45° off its own path. |

## Deliberate exceptions

- **Elytra flight** sets the aim straight to its target. It is a control loop: the flight solver
  assumes the angles it asked for last tick are the angles it has this tick, so a lagging aim there is
  a crash rather than a personality trait.
- **Under free look** (`freeLook`, `antiCheatCompatibility`), the rotations Baritone drives are sent to
  the server while the player's own camera is handed back untouched. Everyone else sees the aim move;
  the person at the keyboard sees their own view.
- **Anything about to be clicked** is aimed at exactly, whatever the rest of the aim is doing: a block to
  break, a face to place against, a door. The trace follows those angles to the centimetre, and the
  movement waits for them before it clicks - so the carry past a target and the one-tick sway and glance
  all stand aside for them.
- **The builder's transient rotations** (`BuilderProcess#hasAnyItemThatWouldPlace`) are written and
  restored inside one tick. Nothing outside that tick sees them.
- **Water and gliding** are the two states where the movement reads the look back: sprint-swimming
  drives the player's depth off the look vector's pitch, and the elytra solver solves for the attitude
  it asked for. Everything that steers the body in those states is sat out: the pitch wander keeps its
  pitch, the glance and the carry past a target are not asked for. What does not steer it is not - the
  stride sway runs while swimming, because the game turns a swim by the yaw exactly as it turns a walk,
  so a degree of drift in it is a degree of drift in the heading, which is what the sway already is.

## If you add something

- A new repeated interval goes through `Cadence`, and a new rule that can be expressed without a
  running game goes in its own class in `behavior/look` with a test next to it, the way `AimDynamics`,
  `IdleGlance`, `HeadWander`, `PitchWander`, `IdleFidget` and `ReactionDelay` are.
- The numbers are allowed to move; the invariants are not. `HumanlikeLookFuzzTest` runs the hand
  through twenty thousand hostile ticks and asserts that the aim never moves more than its tick limit,
  that a settled hand is always arrived, and that the sway never escapes its band. `AimDynamicsTest`,
  `IdleGlanceTest`, `PitchWanderTest`, `IdleFidgetTest` and `CadenceTest` cover the rules themselves.
- Behaviour that already exists as a setting - `randomLooking`, `randomLooking113`, `smoothLook`,
  `smoothLookTicks`, `blockBreakSpeed`, `mineGoalUpdateInterval`, `ticksBetweenInventoryMoves`,
  `autoTool` - keeps its meaning. Where a setting names an interval, the interval is now what the
  clock averages, not what it repeats.
