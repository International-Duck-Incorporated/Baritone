# P2-00: Visible-bot camera profile (documentation, no behaviour change)

Owner: Sandpiper. Card: P2-00 (QuartzHeron's approved scope). Status: documentation written from source. Runtime A/B evidence is still required before the card can be marked done.

## Scope

Document the camera profile that Baritone already uses, and check the docs against the code. No code, no default, and no behaviour change. This is not a new feature, so no patch under `patches/` is needed.

## Sources read

- `src/api/java/baritone/api/Settings.java`, lines 775–814: `freeLook` (default true), `blockFreeLook` (false), `elytraFreeLook` (true), `smoothLook` (false), `elytraSmoothLook` (false), `smoothLookTicks` (5), `remainWithExistingLookDirection` (true), `antiCheatCompatibility` (true).
- `src/main/java/baritone/behavior/LookBehavior.java`: `Target.Mode` (CLIENT, SERVER, NONE) and `Target.Mode.resolve(ctx, blockInteract)` at lines 1562–1581. The SERVER restore of the player's own rotation is in the POST handler, around lines 598–612.
- `HUMANLIKE.md` lines 142 and 210.
- `wiki/Settings.md`: before this change it had no camera rows. A Camera section was added (see "Docs changed" below).

## The decision

`resolve()` has four branches. It is evaluated per tick, and the first matching branch wins.

| Condition (checked in order) | Mode | What the server receives | What the person at the keyboard sees |
|---|---|---|---|
| Player is fall-flying (elytra), `elytraFreeLook` = true | SERVER | Baritone's aim | Own view unchanged |
| Player is fall-flying, `elytraFreeLook` = false | CLIENT | Baritone's aim | Own view turns to every aim |
| Not flying, `freeLook` = true, `blockInteract` = true, `blockFreeLook` = true | SERVER | Baritone's aim | Own view unchanged |
| Not flying, `freeLook` = true, `blockInteract` = true, `blockFreeLook` = false | CLIENT | Baritone's aim | Own view turns (default profile when mining or placing) |
| Not flying, `freeLook` = true, `blockInteract` = false, `antiCheatCompatibility` = true | SERVER | Baritone's aim | Own view unchanged |
| Not flying, `freeLook` = true, `blockInteract` = false, `antiCheatCompatibility` = false | NONE | Nothing sent for the aim | Own view unchanged |
| `freeLook` = false (any state not covered above) | CLIENT | Baritone's aim | Own view turns to every aim |

`blockInteract` is true when the aim is for a block to break, a face to place against, or a door (anything about to be clicked). It is false for walking and other aims.

Under SERVER, the player's own rotation is restored in POST, so the view that the person sees is what they had before. Under NONE, nothing is written for the aim.

## The default profile

With the defaults (`freeLook` true, `blockFreeLook` false, `antiCheatCompatibility` true, `elytraFreeLook` true):

- **Walking:** the aim goes to the server only (SERVER). The person's view stays where they put it.
- **Mining or placing a block:** the view turns visibly (CLIENT). This is because `blockFreeLook` is off. The source comment in `resolve()` gives the reason: with a block interaction, `objectMouseOver()` reads whatever the player's own view is pointing at, so the rotation has to be set somehow. With `blockFreeLook` on, it is set silently (SERVER).
- **Flying with an elytra:** the aim goes to the server only (SERVER).
- **Handing the view back:** on the SERVER branches, POST restores the person's own rotation when the aim ends.

## Checks against the docs

- **`HUMANLIKE.md` line 142** says that under free look "the rotations Baritone drives are sent to the server while the player's own camera is handed back untouched." This is true for the SERVER branches. It is not true for the CLIENT branch that applies to block interaction under the default profile, and it is not true for NONE (nothing is sent). The paragraph should say the profile depends on `blockInteract` and `antiCheatCompatibility`. **Mismatch (doc only).**
- **`HUMANLIKE.md` line 210** says that when the job ends "the server is told the player's own rotation and everyone else sees the head get there in one tick." This matches the POST restore on the SERVER branches. It does not apply under NONE, where the server is not told anything, or under CLIENT, where the view was never handed off. **Narrow, not wrong.** It should name the SERVER branch.
- **`wiki/Settings.md`:** had no camera rows before this change. A Camera section now lists the four settings and their defaults. `SettingsWikiTest` checks each row's default against `Settings.java`. **Gap closed by this change (pending the test run).**
- **Javadoc in `Settings.java`:** `freeLook`, `blockFreeLook`, and `antiCheatCompatibility` are consistent with `resolve()`. The `blockFreeLook` description says it requires `freeLook`, and the code does that (`freeLook` false returns CLIENT before `blockFreeLook` is read). No change.

## What is verified and what is not

- **Verified from source:** the decision table above, which is a direct reading of `resolve()` and the settings defaults.
- **Not verified at runtime:** that the person's view is unchanged under SERVER, that the server sees the bot's aim under SERVER and NONE, and that the default profile looks as described to a second observer. These need an A/B run, which has not been done. The card cannot be marked done until it is.

## Runtime A/B plan (owed)

Run the same short walk-and-mine route on a local server, once per profile. Record the local view and a second observer's view, and check the server-side rotation packets.

1. Default profile (`freeLook` true, `blockFreeLook` false, `antiCheatCompatibility` true).
2. `blockFreeLook` true.
3. `antiCheatCompatibility` false (the NONE branch).
4. `freeLook` false (the CLIENT branch).
5. Elytra flight with `elytraFreeLook` on and off.

Expected per the table above, to be confirmed or refuted by the run. Evidence goes under `docs/planning/2026-10-11/evidence/`.

## Docs changed

- `wiki/Settings.md`: new "Camera" section before "Tips" with `freeLook`, `blockFreeLook`, `antiCheatCompatibility`, and `elytraFreeLook`, with defaults copied from `Settings.java`.
- `docs/planning/2026-10-11/p2-00-camera-profiles.md`: this document.

Not changed: `HUMANLIKE.md`. The two mismatches above are noted here for its owner to fix. Editing it is not part of this card, since it is the humanlike source document and its owner is not Sandpiper.
