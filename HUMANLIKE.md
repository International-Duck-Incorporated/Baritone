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
| The hand shakes in two directions, not one | `behavior/look/AimDynamics.java`, `LookBehavior.tick` | The aim adds a wander of `randomLooking113` degrees either side of where it is aiming, a fresh draw each tick smoothed so it drifts rather than shivers. Both angles draw it, which is what upstream's own note ("How many degrees to randomize the yaw") left to the yaw alone: the pitch took only the hundredth of a degree `randomLooking` adds, which the mouse grid swallows whole, so a bot breaking a block moved its head by a degree or two sideways and by nothing at all vertically - measured on a settled aim at the default sensitivity, the pitch wandered two hundredths of a degree, below a single mouse pixel at every sensitivity there is, and now wanders a third of a degree either side with three and a half degrees peak to peak, as the yaw always did. The two angles are drawn separately - a hand that moved along a line would be a hand that is being moved - and the pitch is left alone in water and in a glide, where it is a control and not a decoration. |
| The hand calls itself arrived | `behavior/look/AimDynamics.java`, `ILookBehavior#hasArrivedAt` | "Is the aim here yet" is asked of the aim, not of a hundredth-of-a-degree comparison against the target: a settled hand sits a fraction of a degree off, because it is easing and the tremor is still shaking it. |
| The gaze leans onto the line ahead | `behavior/look/GazeAhead.java`, `PathExecutor#leanGazeAhead` | A person walking a route has their head around the corner before their feet are: in the last two blocks of a walk the aim leans up to halfway toward where the path goes next, more the closer it gets. On a straight line the two directions are the same and the lean is nothing. Never off the walking direction by more than 60° - a path that doubles back gets an over-the-shoulder glance, not a head facing backwards while the feet walk forwards - and never onto a target about to be clicked, which every rule in this fork aims at exactly. Falls, climbs, jumps and parkour run-ups steer on the angles they are given and take no lean at all. The lean is on the *gaze* only: the movement's own rotation is what the game turns the pressed keys by (`LookBehavior#onPlayerRotationMove`), and it is kept exactly as asked for, so the head leads the body round the corner and never drags it there. |
| A target with no opinion about the pitch | `behavior/look/PitchWander.java`, `LookBehavior#settledGaze` | The gaze settles a few degrees this way and that every few seconds - triangular around level, a degree a tick, ten degrees out at the extremes - instead of holding one angle for a whole journey, and it settles *on the aim*, which is the angle the server is told and the one the arm is traced along. Only where the pitch is decoration: a swim travels by the pitch it is looking along and a glide solves for it, so in water and in the air the pitch stays where the movement put it, and a target that holds an opinion about the pitch - a block to break, a face to place against - is aimed at exactly. |
| Something moved the mouse | `behavior/look/IdleGlance.java` | A glance is dropped the moment the view is taken over - asked only of a camera somebody is looking through, because a camera of Baritone's own is moved by the glance itself - and the angles are handed back exactly. |

## Standing around

The idle repertoire only runs while a process is in control: if Baritone has nothing to do, the person
at the keyboard owns the camera and nothing here touches it.

| what | where | the rule |
| --- | --- | --- |
| A standing bot drifts | `behavior/look/HeadWander.java` | A degree of yaw and six tenths of a degree of pitch, wandering over periods of two to five seconds - the same sway that runs under a walk, and under a swim, at `IDLE_FRACTION` of it when standing, so standing, walking and swimming hand it over rather than switching it on and off. |
| ... and looks around | `behavior/look/IdleGlance.java` | A glance every three to eight seconds, held for under a second and given back, bounded to 28° of yaw and 7° of pitch, never outliving 100 ticks. Taken on the ticks with nothing aimed at and on the ones where an aim is held but the bot is standing still waiting on it - a door it cannot open, a block that has not updated, a path with nowhere to go this tick (`LookBehavior#updateHeadWander`) - so waiting for something is not the same as staring at it. One glance in four is down at the hand rather than around: a pitch gesture of 24 to 38 degrees below level, never up - somebody already looking at the floor checks what they are holding by doing nothing - held and given back like the rest, so an idle head moves in two dimensions instead of sweeping the horizon forever. |
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
| `Cadence.keyBeat` | the walking keys | One to two ticks of no keys between one direction and its opposite. |
| `Cadence.landingRest` | a fall that lands | A jittered beat of standing after the feet touch down, longer for a longer drop. A fall is the one moment stopping is free, and a path that runs straight on the landing tick is the one thing a person never does. Skipped for drops under three blocks - steps and hops - and for a landing in water, where standing is swimming. |

Other things that were on a grid and are not any more: the sprint key trails off over one to three
ticks instead of stopping on the tick the movement stops asking (`Cadence.sprintHangover`,
`PathExecutor`); the tool in hand is kept unless the next tool is properly faster (`ToolSet.SWITCH_GAIN`,
a quarter faster); inventory moves are spaced by a redrawn interval rather than a fixed one.

## Steering

| what | where | the rule |
| --- | --- | --- |
| Which keys walk which way | `pathing/movement/MovementOption.java` | The eight combinations of four keys, compared by *angle* to the direction the movement asked for, never by a scaled distance. |
| The camera and the keys are chosen together | `MovementOption#lookYaw`, `MovementHelper#moveTowardsWithSlightRotation` | The camera turns in whole 45° steps and the keys cover the rest, both measured against the angle the camera is being told to hold - because that is the angle the game turns the pressed keys by. Chosen separately, the two roundings disagree by a whole step and the bot walks 45° off its own path. |
| A direction and its opposite are a beat apart | `pathing/movement/KeyBeat.java`, `InputOverrideHandler#beatTheKeys` | The four keys are decided fresh every tick, so a turn-around arrived as W becoming S in one tick - through the game's own input packet, to anybody watching. A reversal now costs one to two ticks of no keys while the hand repositions, and only a reversal: lifting a finger, landing a finger, and the W+S someone brakes with all answer the tick they are asked. Only at a run on the ground - sneaking along a bridge, backing away from a drop, water, air, and the elytra loop all get their keys immediately, because there the keys are a control loop and not fingers (`InputOverrideHandler#atAPace`). |

## Following

| what | where | the rule |
| --- | --- | --- |
| A moving target does not take the walk apart | `process/FollowProgress.java`, `FollowProcess#walkOrTurnAround` | Following used to cancel the path every time the goal stepped clear of where it ended - a stop every second or two at the target's own walking pace. The walk under way now keeps going while its end is still a step toward the thing being followed (closer to it than the player is), and the plan-ahead chains the next stretch to wherever the goal has wandered by then. Only somebody who has actually gone somewhere else - doubled back, run off sideways - makes the bot stop, turn around, and path from where it stands. |

## Deliberate exceptions

- **Elytra flight** sets the aim straight to its target. It is a control loop: the flight solver
  assumes the angles it asked for last tick are the angles it has this tick, so a lagging aim there is
  a crash rather than a personality trait.
- **Under free look** (`freeLook`, `antiCheatCompatibility`), the rotations Baritone drives are sent to
  the server while the player's own camera is handed back untouched. Everyone else sees the aim move;
  the person at the keyboard sees their own view. What is sent is the same either way, and so is the rule
  about a glance being taken over: only a view somebody is looking through can be taken over, so a
  free-look-off player watching the bot work sees the same sway and the same glances that everyone else
  does, on a camera the aim already owns.
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

The camera is written in four files, and that is a rule rather than an observation: `LookBehavior` (the aim,
the glance and the sway, all of them eased), `BuilderProcess#hasAnyItemThatWouldPlace` (an angle borrowed and
given back inside the same tick so the game can be asked which way round a block would be placed), and the
two mixin bridges that carry the aim and the elytra solver, each of which puts the player's own angles back in
POST. `CameraWritesTest` counts them, so a fifth file fails the build with this list beside it.

## What is not smoothed

- **Handing the camera back.** Under free look the head Baritone drives and the head the person is looking
  through are two different angles, and when the job ends the second one takes over: the server is told the
  player's own rotation and everyone else sees the head get there in one tick. Smoothing it would mean
  driving the head after Baritone has nothing left to do, which is the one time the person owns it.
- **Turning is not how every step is walked.** The 45 degree step in `MovementOption#lookYaw` is upstream's
  and it is kept: the camera holds a grid angle and the keys cover the difference, so a diagonal is walked by
  pressing two keys rather than by turning onto it, which is what somebody with a keyboard does. The
  movements that walk by turning instead end up facing exactly where they are going.
- **A path is started on the tick it is handed over.** Upstream's splice sites begin the next movement in the
  tick the last one ended in rather than waiting one out. One tick of path is not worth changing it for: a
  person who has decided to walk starts walking.

## If you add something

- A new repeated interval goes through `Cadence`, and a new rule that can be expressed without a
  running game goes in its own class in `behavior/look` with a test next to it, the way `AimDynamics`,
  `IdleGlance`, `HeadWander`, `PitchWander`, `IdleFidget`, `GazeAhead` and `ReactionDelay` are - or, for
  a rule about the keys rather than the camera, in `pathing/movement` beside `MovementOption`, the way
  `KeyBeat` is, and for a rule about what to do next rather than how to look doing it, beside the process
  that uses it, the way `FollowProgress` is.
- Everything with a random source takes it through its constructor - `HeadWander`, `PitchWander`,
  `IdleGlance`, `IdleFidget`, `ReactionDelay`, `KeyBeat` - and the constructor with no argument is the
  only one that seeds itself from the clock. Every machine in a test run draws from that run's seed,
  because a failure reported for a seed has to be reproducible by running that seed.
- The numbers are allowed to move; the invariants are not. `HumanlikeLookFuzzTest` runs the hand
  through twenty thousand hostile ticks and asserts that the aim never moves more than its tick limit,
  that a settled hand is always arrived, and that the sway never escapes its band. `AimDynamicsTest`,
  `IdleGlanceTest`, `PitchWanderTest`, `IdleFidgetTest`, `GazeAheadTest`, `KeyBeatTest`, `FollowProgressTest`
  and `CadenceTest` cover the rules themselves.
- Behaviour that already exists as a setting - `randomLooking`, `randomLooking113`, `smoothLook`,
  `smoothLookTicks`, `blockBreakSpeed`, `mineGoalUpdateInterval`, `ticksBetweenInventoryMoves`,
  `autoTool` - keeps its meaning. Where a setting names an interval, the interval is now what the
  clock averages, not what it repeats.
