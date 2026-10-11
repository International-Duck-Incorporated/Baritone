# Auto-eat: what landed, why it is shaped this way, and what it is worth

**Author:** Turnstone. **Date:** 2026-10-11. **Branch:** `26.3`.
**Authority:** the operator directed this work in conversation on 2026-10-11 — *"continue coding then. also add
in to auto fight mobs, auto eat. interact with players by crouching, etc."* That is the explicit human
approval the 2026-10-11 gate requires for feature implementation. The rebrand half of the gate is untouched:
nothing here renames anything.

**Divergence from the roadmap, recorded rather than hidden.** `roadmap.md` P1-04 says of its own approval
"automatic eating/healing is not implied", because the planned tranche was preconditions and alerts. The
operator has now asked for automatic eating by name, which is the higher authority. This document is the
scope statement for that one capability. `P1-03` (DEFEND) and the crouch greeting are **not** in it — see
"What this is not" below.

## What it does

When the bar drops below `autoEatThreshold` (default 15 of 20) the bot eats the most filling ordinary food it
is carrying — from the hand if it is already there, otherwise from the hotbar — and keeps eating until the bar
is back at the threshold. It then stops, and stays stopped until the bar drops again.

It runs as a **behaviour**, not a process, and that is the central design decision. Eating wants the hand for a
second and a half; it does not want the path, the goal or the task. A process would have to take control, stop
the walk, and hand it back, and every one of those handovers is a way to lose a path — which is a large price
for a sandwich. It also means this capability needs none of `P0-03`'s ownership work, which is why it could
land ahead of the response cards.

## The bounds, and the reason for each

| Bound | Why |
|---|---|
| Never changes what is in the hand while a process is in control (`mostRecentInControl()`) | The hotbar belongs to the task. A snack is not worth the pickaxe's slot in the middle of a mine. Food **already in the hand** is the exception, and it costs the task nothing. |
| Only ordinary food: feeds, non-zero stack, not `canAlwaysEat`, no consume effects | `alwaysEdible` is vanilla's own word for the specials — golden apple, enchanted golden apple, chorus fruit, honey bottle — and `Consumable#onConsumeEffects` is where the sting lives for the plain-looking ones: rotten flesh, spider eye, pufferfish, poisonous potato. A bot cannot *want* an effect, so it leaves all of them alone. |
| Waits for the hand to arrive before pressing | A right click with the wrong item in hand is not a missed meal, it is a thrown pearl. The switch is asked for through `InventoryBehavior` and the bite waits for `switchingHands()` to clear. |
| Three attempts per hunger episode, then quiet | A stall happens — hand busy, server refused, mouth interrupted — and a second attempt is the reasonable answer to it. Three is where that stops being an answer and starts being a bot clicking at nothing. A meal that lands resets the budget: a second helping for a bar that is still short is a new ask, not a retry. |
| Bounded wait for the bite (`consumeTicks + 10`) | A bite that never lands must not become a button held forever. |
| Hotbar first; the main inventory only under `allowInventory` | `allowInventory` is the user saying "do not move my items". Auto-eat does not override that. The move goes through `attemptToPutOnHotbar` and the food is picked up out of the hotbar a tick later. |
| Not in creative or spectator, not while gliding, not with a menu open, not while the hands are busy | A bar that never moves, a hand on a rocket, and a menu that is the player's. |

## The one thing that a press is not: a bite

A single `processRightClick` starts a mouthful and **the game drops it on the next tick**. The reason is vanilla
and it is in the client's own bytecode: `Minecraft` calls `MultiPlayerGameMode.releaseUsingItem` while the real
`Options.keyUse` is up, and nothing in a headless client holds that key. An instrumented run shows exactly that:

```
eatprobe f=0 held=minecraft:bread x5 using=false busy=false ... phase=EATING spent=1
```

the bite was started and was not being chewed. The mod's click helpers are the wrong shape for the fix — they
re-click on a cadence, and re-clicking a block is another block while re-clicking a mouthful is a mouthful
started over. So the button is **held**, through the input layer, as a one-tick lease:

* `InputOverrideHandler.holdTheUseButton()` asks for the game's use button for this tick. The ask is spent by the
  tick that reads it, so the button can only stay down while somebody is asking every tick, and a holder that
  stops asking — or stops ticking entirely — cannot leave it down.
* It is asked for rather than switched because **`clearAllKeys()` does not lift it**, and that is deliberate:
  every movement calls `clearAllKeys()` on every tick of a walk to rebuild the task's own keys. A meal eaten on
  the way somewhere would come off the player's mouth twenty times a second otherwise.
* It does not take a key that is already down, so a player eating at the same time keeps their own hold.

Both census tests caught this work, which is what they are for, and both were amended the way the tests
themselves prescribe: `ButtonWritesTest` with an entry explaining why a held item use cannot go through
`BlockPlaceHelper` (whose whole condition is a block under the crosshair) and shape pins for the wait;
`KeyWritesTest` needed no new file at all, because the key write went behind `InputOverrideHandler` — the
remedy that test's own failure message names.

## Evidence

Unit: `EatPolicyTest`, 8 cases — the specials refused however filling, the most filling chosen, ties always
resolving to the same slot, no candidate returning no choice, the budget bounding and resetting, the wait
outlasting the bite. Suite: **391 tests, 0 failures** (383 before this work).

In game, on this branch's jar, all via the game's own `/data get`:

| Scenario | Result |
|---|---|
| Hungry with bread in hand (idle) | bar 0 → 5 → 10 with bread 5 → 4 → 3, `using=true` throughout |
| Hungry, **walking** `#goto 0 -60 130` | moved z 0.5 → 130.4 while bread went **5 → 0** |
| Drain stopped, bread given | bar 0 → **15** — exactly `autoEatThreshold` — with bread **5 → 2**, then it stopped |

The third row is the whole behaviour in one line: three breads, no more and no fewer, and it stopped at the
setting rather than at the stack.

Settings in force for the runs: `autoEat=true`, `autoEatThreshold=15` (read back from the game), `allowInventory`
untouched at its default `false`, difficulty normal (peaceful refills the bar by itself and would mask this).

## What this is not

* **Auto-fight mobs** (`P1-03`, DEFEND) is not here. It is an XL card, the operator has asked for it explicitly,
  and the plan's own ordering puts `P0-03` — failure provenance and safe temporary ownership — in front of any
  response that takes the path over. That is the next piece of work, and it is the one where the operator's ask
  and the plan's bound rules have to be reconciled rather than one of them ignored.
* **The crouch greeting** is not here. It is small, it touches the look/input surface, and it should follow the
  per-tick input work above with its own bounds rather than being bolted on beside it.
* **Main-inventory food when `allowInventory` is off**, **eating to heal** (a golden apple is exactly the case
  the acceptance rule refuses), and **looting or crafting food** are all out of scope.
* It is not a resource planner: nothing here looks for food, plants it, cooks it, or tells the task layer that
  supplies are short. `P3-02` is where that lives.
