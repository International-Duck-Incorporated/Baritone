# Humanlike Features

This fork makes Baritone look like a real player. Nothing changes what the bot does or how well it does it. It changes how the doing looks to somebody standing next to it.

Everything is on by default. No setting turns it on; a bot that has to be told to look alive is a bot that looks dead by default.

## Camera

### Aim Easing

The camera accelerates and decelerates instead of snapping. Capped at 18/tick of yaw and 9/tick of pitch. Turns spin up over their first two ticks and ease out as they arrive.

**File:** `behavior/look/AimDynamics.java`

### Turn Tempo

Every turn draws its own speed (0.85x to 1.15x of normal). The same corner walked twice is never the same ticks of the same steps. A right angle at default sensitivity takes 11 ticks at the fast end, 13 in the middle, 15 at the slow end. It used to take 13 every time.

**File:** `behavior/look/TurnPace.java`

### Reaction Beat

2 to 4 ticks before the aim starts toward a new target. Only while standing still; the walking code reads the facing, so a lagging aim mid-walk steers wrong.

**File:** `behavior/look/ReactionDelay.java`

### Overshoot

The hand goes a few percent past the target and eases back. A quarter turn peaks 2.4 degrees past and is on target 10 ticks later. Only while walking, and only for a target that says where the bot is going; anything about to be clicked is aimed at exactly.

**File:** `behavior/look/AimDynamics.java`

### Two-Axis Sway

Both yaw and pitch wander a fraction of a degree, smoothed so it drifts rather than shivers. The two axes draw separately, which is what a hand being moved looks like. Pitch is left alone in water and in a glide.

**Files:** `behavior/look/AimDynamics.java`, `LookBehavior.java`

### Aim Scatter

The crosshair lands on a point of the block, not the center. Different point each run, same point for the whole run. Face placement also gets its own point.

**File:** `api/utils/AimScatter.java`

### Approach Look

The eyes turn toward the target block over the last 6 blocks of a walk. Eases in over 4 blocks, at most halfway to it. Only when the goal names a block (goto, chest, table). An ordinary walk to coordinates gets nothing.

**Files:** `behavior/look/ApproachLook.java`, `pathing/path/PathExecutor.java`

### Gaze Lean

The head leans around the corner before the feet get there. In the last 2 blocks of a walk the aim leans up to halfway toward where the path goes next. Never off the walking direction by more than 60 degrees.

**File:** `behavior/look/GazeAhead.java`

### Pitch Wander

The pitch settles a few degrees this way and that every few seconds instead of holding one angle. The slope ahead pulls on where it settles: a stair down is watched for footing, a drop is looked into as it comes.

**File:** `behavior/look/PitchWander.java`

### Walk Glance

Every 5 to 15 seconds of plain walking the head turns 12 to 40 degrees to one side, more at something actually there (nearest animal or player, up to 70 degrees off). Now and then the feet stop for the look: once in ten glances at nothing, once in three at something.

**File:** `behavior/look/WalkGlance.java`

### Flinch

A blow turns the head at the hand's own ceiling (18/tick), held for 10 to 30 ticks, tracking the attacker if they move. Unbounded while standing, capped at 70 degrees (a neck's quarter turn) while walking. Measured against the head it is added to, so it lands on the attacker, not on the attacker plus whatever the road was showing.

**File:** `behavior/look/Flinch.java`

## Idle Behavior

### Post-Job Sway

When the last process lets go, the body keeps swaying, glancing, and noticing until the human arrives. Their arrival is any input at all: a mouse move, a key, a screen open. A body that stopped dead and stays dead is the thing people point at.

**File:** `behavior/look/Unattended.java`

### Idle Glance

Every 3 to 8 seconds, held for under a second, bounded to 28 yaw / 7 pitch. One in four looks at the hand (pitch gesture of 24 to 38 degrees below level). Taken on ticks with nothing aimed at and on ticks where an aim is held but the bot is standing still.

**File:** `behavior/look/IdleGlance.java`

### Notice People

One glance in three goes to the nearest player or creature within 12 blocks that is neither invisible nor behind a wall. People look at people; a bot that never does is the thing that gets noticed.

**File:** `behavior/LookBehavior.java`

### Newcomer Detection

Someone arriving in view is looked at once, a quarter to seven tenths of a second after they appear. Someone behind the head is not seen at all. A crowd arriving together is one look at the nearest.

**File:** `behavior/look/Newcomer.java`

### Idle Fidget

A swing at the air every 10 to 60 seconds. The animation packet only; no click goes with it, so it cannot mine, place, or break anything.

**File:** `behavior/look/IdleFidget.java`

## Timing

Nothing repeats on a fixed interval. Every interval redraws its length.

| Clock | Used by | Interval |
|-------|---------|----------|
| Jitter | Every repeated interval | Setting, +/- 1 tick |
| Settle | Both mouse buttons | 1-2 ticks between arrival and swing |
| Retarget | Breaking an instant block | 1-3 ticks before next |
| Run | Builder hold-off after breaking | 4-6 ticks |
| Repeat | Goal rescans (mining, goto, farming) | Setting, redrawn each time |
| Key beat | Walking keys | 1-2 ticks of no keys between reversal |
| Hotbar switch | Changing held item | 2-5 ticks between deciding and pressing |
| Sprint windup | New run on flat | 2-4 ticks of walking before sprint |
| Landing rest | Fall that lands | Jittered beat, longer for longer drops |
| Sprint hangover | Sprint trailing off | 1-3 ticks instead of instant stop |

**File:** `utils/Cadence.java`

## Movement

### Key Reversal Beat

W becoming S costs 1 to 2 ticks of no keys while the hand repositions. Only at a run on the ground; sneaking, water, air, and elytra get their keys immediately, because there the keys are a control loop and not fingers.

**Files:** `pathing/movement/KeyBeat.java`, `utils/InputOverrideHandler.java`

### Head Leads Feet

The body waits for the head to turn within 60 degrees of the way the feet are going from a standstill. A quarter turn costs 3 ticks, a half turn 8. Only on the ground; never in air or water.

**Files:** `pathing/movement/HeadFirst.java`, `utils/InputOverrideHandler.java`

### Bridge Crouch

A crossing is walked crouched from one edge to the other. The crouch goes down when the movement has to place the block it is stepping onto, stays down while the block after has no floor, comes up when the ground is back. No timer, no draw, no randomness. Priced as crouched.

**Files:** `pathing/movement/CarefulFooting.java`, `pathing/movement/movements/MovementTraverse.java`

### Sprint Windup

A new run walks 2 to 4 ticks before sprinting. Parkour, climbs, drops, water, and air skip it. A hangover still holding the key is the same run and does not wind up again.

**File:** `pathing/movement/SprintWindup.java`

### Eight-Direction Keys

Keys are chosen by angle to the movement direction, not by scaled distance. Camera and keys are chosen together so the two roundings cannot disagree by a whole step.

**File:** `pathing/movement/MovementOption.java`

## Safety

### Elytra Auto-Swap Guard

The three swap clicks (pick up, swap, put back) wait for the player's own inventory to be the menu in front. A sequence that has not begun when its window is gone is dropped silently. One that has begun finishes.

**File:** `utils/InventoryTransaction.java`

### Face Trace

Placement clicks ask the face they need, not the block center. Farm plantings and bridge steps were sending items into faces that did not take them.

**File:** `utils/RayTraceUtils.java`

## Guard Rails

Five tests pin which files may write to player state:

| Test | What it counts |
|------|---------------|
| CameraWritesTest | Files that may write rotation |
| HandWritesTest | Files that may write selected slot |
| ButtonWritesTest | Files that may press a button or swing |
| ClickGatesTest | Where a click may go and what question it asks first |
| KeyWritesTest | Files that may write movement input |

Every entry carries a reason and a shape pin. A new entry on the list is a new decision about how the bot behaves, and it should not be possible to make one without writing it down.

**Files:** `src/test/java/baritone/utils/CameraWritesTest.java`, `HandWritesTest.java`, `ButtonWritesTest.java`, `ClickGatesTest.java`, `KeyWritesTest.java`

## Full Details

Everything above is a summary. The complete specification is in [HUMANLIKE.md](../HUMANLIKE.md) in the repository root. It includes the exact rules, the files they live in, the tests that pin them, and what is deliberately not smoothed.
