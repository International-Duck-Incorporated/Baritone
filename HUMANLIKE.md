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
| The same turn is not the same turn twice | `behavior/look/TurnPace.java`, `LookBehavior#humanStep` | The trajectory was the last thing in the camera that was a function of nothing but the angle: the same corner, walked twice, was the same ticks of the same steps, for as long as anyone watched. Every turn now draws a tempo of its own when it starts - 0.85 to 1.15 of the ordinary speed, once per turn rather than once a tick, or the hand would have no tempo at all - and that tempo scales how much of what is left it takes per tick and how quickly it spins up. Measured on a right angle at the default sensitivity, the flat walk now takes 11 ticks at the quick end of the band, 13 at the middle and 15 at the slow end, where it took 13 every time before. The flick ceiling is left exactly where it was whatever the pace, and only the spin-up is scaled while it is still ramping: a tempo decides how this turn is made, not how fast a hand can move, so nothing it draws can make the hand snap or turn the hurry a click waits on into a drift (the slowest tempo costs a clicking right angle two ticks, and the ceiling it is bound by is unmoved). A pace of zero or less is the ordinary turn rather than a stopped hand. Found in the doing: the whole-mouse-pixel snap in `AimDynamics#steppedAngle` could round a small fraction of what was left down to no pixel at all while more than a pixel of turn remained, and the aim then sat a fraction of a degree short of its target for as long as it was asked for it - a block face never reached, a click never made. The ordinary half could not do it and anything under a half could; the rule is now that a hand asked to move always takes the pixel. |
| The hand takes a beat to notice | `behavior/look/ReactionDelay.java` | Two to four ticks before the aim starts moving to a target it was not already pointing at. Only while standing still: the walking code reads the facing, so a lagging aim mid-walk steers. |
| ... and goes a little past | `behavior/look/AimDynamics.java` | A hand is still moving when it reaches what it was reaching for: what it aims at starts a few percent of the turn past the target and walks back onto it. Measured on the hand at the default sensitivity, a quarter turn peaks 2.4 degrees past and is exactly on target ten ticks later. Only while walking, and only for a target that says where the bot is going - anything about to be clicked is aimed at exactly. |
| The hand shakes in two directions, not one | `behavior/look/AimDynamics.java`, `LookBehavior.tick` | The aim adds a wander of `randomLooking113` degrees either side of where it is aiming, a fresh draw each tick smoothed so it drifts rather than shivers. Both angles draw it, which is what upstream's own note ("How many degrees to randomize the yaw") left to the yaw alone: the pitch took only the hundredth of a degree `randomLooking` adds, which the mouse grid swallows whole, so a bot breaking a block moved its head by a degree or two sideways and by nothing at all vertically - measured on a settled aim at the default sensitivity, the pitch wandered two hundredths of a degree, below a single mouse pixel at every sensitivity there is, and now wanders a third of a degree either side with three and a half degrees peak to peak, as the yaw always did. The two angles are drawn separately - a hand that moved along a line would be a hand that is being moved - and the pitch is left alone in water and in a glide, where it is a control and not a decoration. |
| The hand calls itself arrived | `behavior/look/AimDynamics.java`, `ILookBehavior#hasArrivedAt` | "Is the aim here yet" is asked of the aim, not of a hundredth-of-a-degree comparison against the target: a settled hand sits a fraction of a degree off, because it is easing and the tremor is still shaking it. |
| The hand lands on the block, not on its centre | `api/utils/AimScatter.java`, `RotationUtils#reachable` | Upstream aimed every block it broke, opened or used at the exact centre of its box. The hand now goes to a point of the block's own, inside the middle two thirds of its box on each axis, the same point for that block for the whole run (a hash of its position and a salt drawn once, never a draw per tick under a hand that is holding) and somewhere else next run. Where the point cannot be seen from where the player stands, the centre and the face centres are tried as before, and a block with no collision box - a crop, a torch, a fire - keeps the centre. `remainWithExistingLookDirection` used to freeze the hand the moment the crosshair touched the block, which with a hand that eases was always just inside the edge it came from; it now freezes it once the hand has got to its point, mining on the way. A face a throwaway block is placed against - under a bridge, a pillar, a step up, a jump - takes a point of its own too (`AimScatter#facePoint`, `MovementHelper#attemptToPlaceABlock`, `MovementTraverse`, `MovementPillar`): on the face and in the middle two thirds of it, keyed on where the block is going, with the trace that confirms the face run on the point actually aimed at and the exact centre tried when the point cannot be seen. Where the crosshair lands on a face only decides anything for a block that takes its shape from the landing, a slab or a stair, and dirt and cobble do not; the builder tries a point of the face's own first for a block that is a full cube whatever is clicked where (`BuilderProcess#placementMultipliers`) and only its fixed points, well clear of the halfway line, for a slab, a stair or a trapdoor, with what would actually be placed from the point checked as it always was. |
| The gaze leans onto the line ahead | `behavior/look/GazeAhead.java`, `PathExecutor#leanGazeAhead` | A person walking a route has their head around the corner before their feet are: in the last two blocks of a walk the aim leans up to halfway toward where the path goes next, more the closer it gets. On a straight line the two directions are the same and the lean is nothing. Never off the walking direction by more than 60° - a path that doubles back gets an over-the-shoulder glance, not a head facing backwards while the feet walk forwards - and never onto a target about to be clicked, which every rule in this fork aims at exactly. Falls, climbs, jumps and parkour run-ups steer on the angles they are given and take no lean at all. The lean is on the *gaze* only: the movement's own rotation is what the game turns the pressed keys by (`LookBehavior#onPlayerRotationMove`), and it is kept exactly as asked for, so the head leads the body round the corner and never drags it there. |
| A target with no opinion about the pitch | `behavior/look/PitchWander.java`, `LookBehavior#settledGaze` | The gaze settles a few degrees this way and that every few seconds - triangular around level, a degree a tick, ten degrees out at the extremes - instead of holding one angle for a whole journey, and it settles *on the aim*, which is the angle the server is told and the one the arm is traced along. Only where the pitch is decoration: a swim travels by the pitch it is looking along and a glide solves for it, so in water and in the air the pitch stays where the movement put it, and a target that holds an opinion about the pitch - a block to break, a face to place against - is aimed at exactly. The ground ahead pulls on where the gaze settles (`PitchWander#lean`, `PathExecutor#leanOntoTheSlope`): the rise over the run of the next five blocks of path, as the angle a person would look along it, and then part of that - six tenths of a way down, a third of a way up, never more than thirty-five degrees - offered every tick and gone the tick it is not, so a stair down is watched for its footing, a drop at the end of a corridor is looked down into as it comes, and a climb is glanced up; nothing on the flat, in water, in the air or on the run-up to a jump. |
| The eyes go to the side of the road | `behavior/look/WalkGlance.java`, `PathExecutor#welcomeGlance` | Every five to fifteen seconds of plain walking the head turns twelve to forty degrees to one side - more, at something actually there: the nearest animal or player in view, up to seventy degrees off the way - eases there over half a second, holds for anything from a flick to a second, and eases back to exactly nothing, at the speed of a look (`AimDynamics.IDLE_YAW_STEP`) and never faster. It is an offset on the aim rather than an angle of its own, so it rides round a corner with the head under it and cannot leave the head somewhere stale when it ends. Welcome only on a plain walk along the flat with two more plain ones to come - nothing to break, place or open, no jump, drop, water or parkour - and the moment the walk stops being plain, or anything is about to be clicked, it comes back at the full looking speed and the count starts over; a walk that is all corners and jumps never glances at all. The keys are chosen against the aim and not against the sent rotation (`ILookBehavior#headRotation`), so a look to the side is not read as a change of heading. Now and then the feet stop for the look (`WalkGlance#stopped`, `PathExecutor#standForTheLook`): once in ten glances at nothing and once in three at something, the walking keys come off halfway out, the body stands while the head is held there, and it sets off again as the head comes back - a pause of under a second once every couple of minutes of open walking, taken only on a walk the executor has already welcomed the glance on, so there is nothing under the feet it could cost. The idle glance keeps off the head while the walking one is out on a stopped foot. |
| Something moved the mouse | `behavior/look/IdleGlance.java` | A glance is dropped the moment the view is taken over - asked only of a camera somebody is looking through, because a camera of Baritone's own is moved by the glance itself - and the angles are handed back exactly. |

## Standing around

The idle repertoire used to run only while a process was in control, and that gate had the wrong
shape: it switched everything off at the exact moment Baritone ran out of work - the goal reached,
the mine finished, the follow lost - and left the body standing at the last walking heading,
perfectly still, for as long as nobody came back. A body that has stopped dead and stays dead is
the thing people point at. So the gap between one job and the next has a rule of its own:

| what | where | the rule |
| --- | --- | --- |
| The body outlives the job | `behavior/look/Unattended.java`, `LookBehavior#updateIdleLook` | When the last process lets go, the standing body stays ours and keeps every habit below - the sway, the glances, the fidget, the noticing - until the person at the keyboard actually arrives. Their arrival is any input at all: the view moving off what the idle look last wrote (a hundredth of a degree, less than one pixel of mouse at any sensitivity - a camera nobody is holding does not drift), a key or a mouse button down, a screen of any kind open. That is a latch and not a look: somebody who moved the view once and then holds still for an hour is a person aiming at something, not an absence, and the body stays theirs until the next job takes it back and the gap starts over. Nobody who shows up finds their camera fighting them - the first tick anything of theirs is noticed, the repertoire is already off - and a dimension change forgets whoever was thought to be there, since the view they were holding has gone with the old world. Water is still water: a jobless body standing in it is swimming, the pitch there is a control, and the repertoire waits on dry ground. |

The rest of the repertoire, which that rule now keeps alive across the gap:

| what | where | the rule |
| --- | --- | --- |
| A standing bot drifts | `behavior/look/HeadWander.java` | A degree of yaw and six tenths of a degree of pitch, wandering over periods of two to five seconds - the same sway that runs under a walk, and under a swim, at `IDLE_FRACTION` of it when standing, so standing, walking and swimming hand it over rather than switching it on and off. |
| ... and looks around | `behavior/look/IdleGlance.java` | A glance every three to eight seconds, held for under a second and given back, bounded to 28° of yaw and 7° of pitch, never outliving 100 ticks, and spun up over its first two ticks like any other turn (`AimDynamics.TURN_RAMP`) - both legs of it, out and home, since each is a turn of its own. Taken on the ticks with nothing aimed at and on the ones where an aim is held but the bot is standing still waiting on it - a door it cannot open, a block that has not updated, a path with nowhere to go this tick (`LookBehavior#updateHeadWander`) - so waiting for something is not the same as staring at it. One glance in four is down at the hand rather than around: a pitch gesture of 24 to 38 degrees below level, never up - somebody already looking at the floor checks what they are holding by doing nothing - held and given back like the rest, so an idle head moves in two dimensions instead of sweeping the horizon forever. |
| ... and notices people | `behavior/LookBehavior.java#somethingNearby` | One glance in three goes to the nearest player or creature within twelve blocks that is neither invisible nor behind a wall. People look at people; a bot that never does is the thing that gets noticed. |
| ... and sees who walks up | `behavior/look/Newcomer.java`, `LookBehavior#noticeArrivals` | Somebody arriving is not the same as somebody there. Another player coming into view - within twelve blocks, with a line of sight, in front of the head and no further round than a little past the shoulder - is looked at once, a quarter to seven tenths of a second after they appear, by whichever glance is running being prompted ahead of its clock: the idle one if the body is standing, the walking one if it is on a plain walk. The people in view on the first tick are not arrivals, somebody out of sight for under fifteen seconds has not arrived twice, a crowd arriving together is one look at the nearest, two looks are never under four seconds apart, somebody gone again before the head gets round to them is not looked at - a head that turns to where they were is a head that heard them - and a prompt not taken within two seconds is dropped. The prompted glance is the ordinary glance in all but when it starts, so it is still never taken while anything is being clicked, still never outlives its hold, still comes home, and the walking one still has to be welcome on the walk. Somebody arriving behind the head is not seen at all - but somebody who hits us is looked round at whichever way they are, two to six ticks after the blow (`Newcomer#struck`, `LookBehavior#noticeBlows`): the client is only told a blow landed, so the guess is a person's, the nearest player within ten blocks that can be seen or failing one the nearest hostile, and a fall with nobody near is not looked round at. A head that goes on staring at the horizon while it is punched fails the one test every suspected bot gets. |
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
| `Cadence.hotbarSwitch` | every change of the held item (`utils/HotbarSwitch.java`, `InventoryBehavior#hold`) | Two to five ticks between deciding on a slot - the best tool for a block, a block to throw down, the builder's next material - and pressing its key, during which neither mouse button is pressed: the click comes after the item is in the hand, never before, and a right click with the wrong item in hand is a bucket emptied. The beat before the click runs underneath, so a switch costs its own beat and no more. A mind changed while the beat runs presses the last key decided on when it is up, rather than starting over, so two callers taking turns cannot stop the hand. The water bucket under a fall used to be the one item still grabbed on the tick, as a reflex with a deadline; it is now asked for on the first tick of a fall that will need it, so the beat is spent on the way down, and the direct grab survives only as the landing guarantee - if the crosshair reaches the spot before the hand gets there, the bucket goes into the hand on that tick rather than the water being missed. What a person does with a bucket on the way down to a spot they mean to catch, the bot now does. |
| `Cadence.sprintWindup` | a new run on the flat (`SprintWindup`, `PathExecutor`) | Two to four ticks of walking before control goes down. Parkour and climbs skip it. |
| `Cadence.landingRest` | a fall that lands | A jittered beat of standing after the feet touch down, longer for a longer drop. A fall is the one moment stopping is free, and a path that runs straight on the landing tick is the one thing a person never does. Skipped for drops under three blocks - steps and hops - and for a landing in water, where standing is swimming. |

Other things that were on a grid and are not any more: the sprint key trails off over one to three
ticks instead of stopping on the tick the movement stops asking (`Cadence.sprintHangover`,
`PathExecutor`); the tool in hand is kept unless the next tool is properly faster (`ToolSet.SWITCH_GAIN`,
a quarter faster); inventory moves, including the queued Elytra auto-swap clicks, are spaced by a redrawn
interval rather than a fixed one.

## Steering

| what | where | the rule |
| --- | --- | --- |
| Which keys walk which way | `pathing/movement/MovementOption.java` | The eight combinations of four keys, compared by *angle* to the direction the movement asked for, never by a scaled distance. |
| The camera and the keys are chosen together | `MovementOption#lookYaw`, `MovementHelper#moveTowardsWithSlightRotation` | The camera turns in whole 45° steps and the keys cover the rest, both measured against the angle the camera is being told to hold - because that is the angle the game turns the pressed keys by. Chosen separately, the two roundings disagree by a whole step and the bot walks 45° off its own path. |
| The body goes where the eyes go | `MovementOption#lookYaw`, `pathing/movement/StraightRun.java` | The camera is never left more than one step - a diagonal - off the way the feet are going, so the keys a route is walked on are W, or W with A or D, and never S or a bare strafe: upstream anchored the camera to whichever step it was nearest, which walked a bot that was facing away when it was told to go the whole way there backwards. The diagonal is for the first steps of a heading and for the staircases and two-to-one slopes a path makes toward a goal off its axes, which are walked facing the diagonal with the keys alternating under a steady head, the way a person walks them; a straight is faced outright from its third block, with W alone. |
| A run walks before it sprints | `pathing/movement/SprintWindup.java`, `PathExecutor#shouldSprintNextTick` | Control used to go down on the same tick a traverse asked for a sprint. A new run now walks two to four ticks first (`Cadence.sprintWindup`). Parkour, climbs, drops, water and air skip it - the speed on those ticks is load bearing. A hangover still holding the key is the same run and does not wind up again. |
| A direction and its opposite are a beat apart | `pathing/movement/KeyBeat.java`, `InputOverrideHandler#beatTheKeys` | The four keys are decided fresh every tick, so a turn-around arrived as W becoming S in one tick - through the game's own input packet, to anybody watching. A reversal now costs one to two ticks of no keys while the hand repositions, and only a reversal: lifting a finger, landing a finger, and the W+S someone brakes with all answer the tick they are asked. Only at a run on the ground - sneaking along a bridge, backing away from a drop, water, air, and the elytra loop all get their keys immediately, because there the keys are a control loop and not fingers (`InputOverrideHandler#atAPace`). |
| The feet wait for the head | `pathing/movement/HeadFirst.java`, `PathExecutor#letTheHeadLead` | The body is steered by the angle a movement asks for on the tick it asks, while the head eases there over the ticks after - so at a sharp turn the body used to set off while the head still faced the old way, a half turn walked backwards for as long as the head took to come round. The walking keys and the jump are now kept off until the head is inside 60° of the way the feet are going from a standstill (a quarter turn costs three ticks, a half turn eight), and at a walk only a turn sharper than 100° - a turn-around - stops the feet, for six ticks on a half turn. Never in the air or in water, never past ten ticks, and never twice for one turn; the sneak and the mouse buttons go around it. |

## Following

| what | where | the rule |
| --- | --- | --- |
| A moving target does not take the walk apart | `process/FollowProgress.java`, `FollowProcess#walkOrTurnAround` | Following used to cancel the path every time the goal stepped clear of where it ended - a stop every second or two at the target's own walking pace. The walk under way now keeps going while its end is still a step toward the thing being followed (closer to it than the player is), and the plan-ahead chains the next stretch to wherever the goal has wandered by then. Only somebody who has actually gone somewhere else - doubled back, run off sideways - makes the bot stop, turn around, and path from where it stands. |
| Somebody being followed is faced | `FollowProcess#regardWhileStanding` | While the feet have nowhere to go - close enough already, or waiting on a way - the head is turned to the nearest of whoever is being followed, as a standing target: the turn at the hand's own speed with its beat before it, the sway at half, the idle glance off it and home again, and a slow track as they move about. The regarded entity stays selected across scans until it leaves the valid follow set, so two nearby matches do not make the head flick between faces when their distances cross. A walking tick's movement sets its own target afterwards and wins. A person following somebody who stops turns to them; one who goes on facing the last way they walked is a dog on a lead. |

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
  break, a face to place against, a door. "Exactly" is the point the hand was sent to - a point of the
  block's or the face's own, see the scatter above - and the trace follows those angles to the centimetre,
  and the movement waits for them before it clicks - so the carry past a target and the one-tick sway and glance
  all stand aside for them. "Exactly" is the angle, not the speed: once the crosshair is on the block and
  face being clicked, the last of the turn to the hand's point on it settles at the walking ease
  (`LookBehavior#crosshairIsOn`) rather than snapping, because from there nothing is waiting on the hand.
- **The builder's transient rotations** (`BuilderProcess#hasAnyItemThatWouldPlace`) are written and
  restored inside one tick. Nothing outside that tick sees them.
- **Water and gliding** are the two states where the movement reads the look back: sprint-swimming
  drives the player's depth off the look vector's pitch, and the elytra solver solves for the attitude
  it asked for. Everything that steers the body in those states is sat out: the pitch wander keeps its
  pitch, the glance and the carry past a target are not asked for. What does not steer it is not - the
  stride sway runs while swimming, because the game turns a swim by the yaw exactly as it turns a walk,
  so a degree of drift in it is a degree of drift in the heading, which is what the sway already is.

The camera is written in four files, and that is a rule rather than an observation: `LookBehavior` (the aim,
the glances and the sway, all of them eased), `BuilderProcess#hasAnyItemThatWouldPlace` (an angle borrowed and
given back inside the same tick so the game can be asked which way round a block would be placed), and the
two mixin bridges that carry the aim and the elytra solver, each of which puts the player's own angles back in
POST. `CameraWritesTest` counts them, so a fifth file fails the build with this list beside it.

The item in the hand has the same rule, and it is counted the same way. It is written in two files: `InventoryBehavior` (the beat, pressing the key that was asked for) and `MovementFall` (the fall's landing guarantee underneath it, written when the crosshair reaches the spot before the hand does, because the tick that needs the water is not a tick to wait a beat out). `HandWritesTest` counts them, so a third file fails the build with the reason beside it - and it has already been forgotten twice, which is why it is a test now rather than a note: the elytra firework and the bucket fall both clicked on the tick they asked for an item.

## What is not smoothed

- **Handing the camera back.** Under free look the head Baritone drives and the head the person is looking
  through are two different angles, and when the job ends the second one takes over: the server is told the
  player's own rotation and everyone else sees the head get there in one tick. Smoothing it would mean
  driving the head after Baritone has nothing left to do, which is the one time the person owns it.
- **Turning is not how every step is walked.** The 45 degree step in `MovementOption#lookYaw` is upstream's
  and it is kept, within a diagonal: the camera holds a grid angle and the keys cover the difference, so a
  diagonal is walked by pressing two keys rather than by turning onto it, which is what somebody with a
  keyboard does. What is not kept is the camera staying put while the keys do the whole of a turn - see
  `StraightRun`. The movements that walk by turning instead end up facing exactly where they are going.
- **A path is started on the tick it is handed over.** Upstream's splice sites begin the next movement in the
  tick the last one ended in rather than waiting one out. One tick of path is not worth changing it for: a
  person who has decided to walk starts walking.

## If you add something

- A new repeated interval goes through `Cadence`, and a new rule that can be expressed without a
  running game goes in its own class in `behavior/look` with a test next to it, the way `AimDynamics`,
  `IdleGlance`, `WalkGlance`, `Newcomer`, `HeadWander`, `PitchWander`, `IdleFidget`, `GazeAhead` and `ReactionDelay` are - or, for
  a rule about the keys rather than the camera, in `pathing/movement` beside `MovementOption`, the way
  `KeyBeat`, `HeadFirst`, `StraightRun` and `SprintWindup` are, for a rule about the hands in `utils` beside the helpers that press the buttons,
  the way `HotbarSwitch` is, and for a rule about what to do next rather than how to look doing it, beside the process
  that uses it, the way `FollowProgress` is.
- Everything with a random source takes it through its constructor - `HeadWander`, `PitchWander`,
  `IdleGlance`, `WalkGlance`, `Newcomer`, `IdleFidget`, `ReactionDelay`, `KeyBeat`, `SprintWindup` - and the constructor with no argument is the
  only one that seeds itself from the clock. Every machine in a test run draws from that run's seed,
  because a failure reported for a seed has to be reproducible by running that seed.
- The numbers are allowed to move; the invariants are not. `HumanlikeLookFuzzTest` runs the hand
  through twenty thousand hostile ticks and asserts that the aim never moves more than its tick limit,
  that a settled hand is always arrived, and that the sway never escapes its band. `AimDynamicsTest`,
  `IdleGlanceTest`, `WalkGlanceTest`, `NewcomerTest`, `PitchWanderTest`, `IdleFidgetTest`, `GazeAheadTest`, `KeyBeatTest`, `HeadFirstTest`,
  `StraightRunTest`, `SprintWindupTest`, `FollowProgressTest`, `HotbarSwitchTest`, `UnattendedTest`, `TurnPaceTest`, `HandWritesTest` and `CadenceTest` cover the rules themselves.
- Behaviour that already exists as a setting - `randomLooking`, `randomLooking113`, `smoothLook`,
  `smoothLookTicks`, `blockBreakSpeed`, `mineGoalUpdateInterval`, `ticksBetweenInventoryMoves`,
  `autoTool` - keeps its meaning. Where a setting names an interval, the interval is now what the
  clock averages, not what it repeats.
