# Phase 16: the interiors, perfected and usable

Branch `claude/project-thread-ixhb7o`, built on `claude/project-thread-4i7mkg` (PR #18, which now holds Phase 15). No release, nothing merged into `main`.

The goal of this phase: no more flickering or missing faces in the Falcon and the Star Destroyer, collision that follows the drawn shapes, and things to use aboard: seats, doors, lights, consoles, the Falcon's gun turrets and the jump to hyperspace from its cockpit.

Everything stays server-decided and built from vanilla blocks and entities. No new blocks, items or entity types are registered, so vanilla clients can still join (they sit, bump into doors and see the lights dim too, they just do not see the models).

## Flickering: the causes, not the symptoms

Instead of moving single objects until the flicker went away, a new audit (`MeshAudit`, used by the tests only) builds each ship's model and compares every opaque quad with every other one in nearly the same plane, and with the block faces right behind it. It lists each place where two surfaces fight over the same pixels. The causes it found, and how each was fixed:

- **Overlapping parts in the same plane.** Posts that reached into the beam above them, cylinders from corner to corner of a frame that overlapped at every corner, crossing struts, greebles of the same height on top of each other, and pipes bent sharply in one plane (the joints of two pieces met in the same face). Fixed by making parts end where the other begins, setting one a little behind the other, or using one band instead of several pieces.
- **Model faces lying on block faces.** Panels and machines that stood exactly on the face of a real block (the gun well's post on its ladder block, the bridge modules on the bridge walls, the edges of holes in the deck). Fixed by keeping every model face a little away from the blocks.
- **Flat panels standing out of curved walls.** The panelling put a flat panel in front of each wall block, also where a round wall was drawn, so the corners of the panels poked through the curve. The round walls are now shaped barriers (see Collision) that the panelling treats as covered, and only the doorways through them get their own lining.

Result of the audit at the end of this phase: **0 fighting pairs** in the Falcon (about 25 300 opaque quads) and in the Star Destroyer (about 78 500).

A second audit looks for block faces that can be seen from a room but are not covered by the model (raw blocks, missing panels, one-sided faces seen from behind). What it still lists is meant to be seen as vanilla blocks: the Falcon's ramp stairs and, in the Star Destroyer, the stairs into the bridge pits and the inner edge of the reactor catwalk.

Missing faces fixed: the curved couch in the Falcon's hold now has closed ends; door frames and sliding leaves are drawn from both sides.

## Collision

- **Round walls collide where they are drawn.** The barriers along the Falcon's hold, ring corridor and engine room and the Star Destroyer's reactor shaft are now shaped (a mixin gives a barrier the shape recorded for its cell, `ProxyShapes`), so you walk along the curve instead of bumping into a step a block in front of it.
- **Slim furniture.** Seats are slim boxes that follow the drawn seat; decorative parts no longer block.
- The visual model and the collision are still separate: the model is drawn by the client, the shapes come from the plan on both sides.

## No ships inside the interiors

Calling a ship in the interior dimension is refused, and so is any other way the mod puts a ship into the world there. A ship that turns up there by some other route (a command, a spawn egg, anything that loads one) is removed at once. This is decided by the server for every player, so it holds on multiplayer servers too. The player who called it, or the ship's owner, gets an overlay message saying why.

## Seats (both ships)

- A click on a seat sits you down: the Falcon's pilot and co-pilot, the two rear cockpit seats, the engineering chair, the couch (three places round the table), both gunners' seats; on the Star Destroyer the bridge pit chairs, the conference room and flight control.
- You sit facing the way the seat faces. Sneak to stand up: you are put on your feet in front of the seat (or beside it where there is room) with normal controls.
- One person per seat, any number of players at once. Under the hood each seat is an invisible vanilla item display that exists only while someone sits on it. Nobody gets stuck: a seat whose rider is gone (stood up, logged out, died, teleported) is removed the next tick; one left over from a restart is taken over by its rider or removed.
- While seated you cannot break blocks (the gunner's attack key fires the guns).

## The Falcon's gun turrets

- Sit in the upper or lower gunner's seat: the view looks out of the glass dome, the mouse aims, the attack key fires.
- The quad guns turn with the gunner (everyone sees them turn) and fire red bolts that fly out of the guns and are seen outside. A shot every 4 ticks, the guns take turns; after 24 shots in a row they overheat for 3 seconds.
- The aim is limited so the guns never point into the hull (the upper turret not lower than 8 degrees under the horizon, the lower one not higher than 8 above).
- If the Falcon stands in the world, the real ship fires a bolt from its upper or lower cannon in the same direction (turned with the ship).

## The Falcon's cockpit

- Rebuilt, not scaled: a narrow round pod longer than it is wide (radius 3.1, ten blocks long), with the window cone in front, arches, console bands along both walls, an overhead switch panel, the dashboard with the helm lever on the pilot's side and the hyperdrive lever on the co-pilot's, and two rows of seats.
- The tube from the ring corridor now meets the pod through a proper doorway; the pod is drawn with a hole where the tube comes in and the saucer band is cut round the tube.

## Hyperspace from the cockpit

- From the pilot's or co-pilot's seat: press the hyperspace key or pull the hyperdrive lever by the co-pilot.
- The server checks what a jump of the ship would need (hyperspace allowed, a hyperdrive, not cooling down) and then everyone in that interior gets the same three stages: the drive spools up (1.5 s, the stars stretch into lines), about 5.5 s in hyperspace (lines rushing past, rings of blue light), and the way back (1.2 s). Each stage has its sound, heard all through the ship.
- The dashboard light shows the state (green ready, blinking amber while charging, blue in hyperspace) and the hyperdrive in the engine room glows.
- Afterwards the owner's drive cools down as after any jump (`hyperspace_cooldown_seconds`).
- The ship standing in the world does not move: the interior is a place of its own, the jump is a journey for those aboard.

## Doors, lights, consoles

- **Sliding doors** at the Falcon's quarters, cockpit, gun wells and engine room and at the Star Destroyer's bridge, conference and sensor rooms, hangar, flight control and reactor gallery. They open when someone comes within about 2.6 blocks along the way through and close behind them, with a sound. A closed door is a row of barriers, so it blocks for everyone; the client slides two leaves with a red (closed) or green (open) edge.
- **Lights**: the switch at the Falcon's engineering bank and the Star Destroyer's officers' stations dims the lights of the whole ship (light blocks lowered to about a third) and turns them on again; the lamps in the model dim with them.
- **Status**: the Falcon's engineering station and the Star Destroyer's sensor table report the hull, energy and hyperdrive of the owner's ship.
- Every console that can be used does something; there are no dummy buttons.

## Decor

The crates, barrels and chests are where they make sense (the cargo hold, the hidden compartments under the floor, the galley, the hangar) and the barrels and chests are real storage; what is put into them is kept when a plot is rebuilt from a newer plan.

## Tests

- New client game test `Phase16GameTest` (registered in `fabric.mod.json`):
  - both audits (fighting surfaces, uncovered block faces) and pictures of every former flicker spot;
  - the cockpit from all sides, inside and outside;
  - no ship can be called inside the interiors;
  - seats: sitting down and standing up on the pilot's seat, the couch and a bridge pit chair, first and third person, the seat freed and removed, the player on their feet and not inside anything;
  - doors: closed while nobody is near, open when someone comes near, closed again behind them, on both ships;
  - lights dimmed and on again (server and client), the status console;
  - turrets: the guns fire, the bolts fly out of the guns, no faster than their rate;
  - hyperspace: refused standing and from a rear seat, started by the lever from the co-pilot's seat, one jump at a time, the client shows all stages, the jump ends, then the drive cools down.
- `Phase14GameTest`: its reachability check opens the doors first (they are barriers while nobody stands at them).
- `InteriorDesignGameTest`: the player leaves the plot before the migration check (a plot is only rebuilt while nobody is aboard; since this phase the player counts as aboard the plot they stand in).
- Local runs (2026-11-14, cloud container, software rendering): the full suite (`build` plus all client test classes) passed 1232 checks up to `AdvancementGameTest`, which then hung until the 90-minute limit. The classes from there on (`AdvancementGameTest`, `SaberHitGameTest`, `DroidGameTest`, `ShipGameTest`, `AtmosphereGameTest`, `Phase12GameTest`, `Phase13GameTest`, `Phase16GameTest`) were run again together and passed (385 checks, `AdvancementGameTest` included). `Phase14GameTest`, `ShipBoardingGameTest` and `InteriorDesignGameTest` passed in their own runs after the last code change. GitHub Actions has not run for this branch yet.

Pictures (all from the test client): [`docs/images/phase16`](images/phase16).

## Known limits

- Not tested with several real players at once (the test environment has one client). Seats, doors and jumps are kept per player and per plot on the server, and the stages of a jump are sent to everyone in the plot.
- Not tested on real graphics cards or with shader packs (Iris/Sodium); the test client renders in software.
- The bolts the real ship fires while someone sits at the guns aboard are not covered by a test (the test ship is not in the world then).
- The jump from the cockpit does not move the ship in the world.
- Vanilla clients see doors as invisible walls that disappear and the jump not at all.
- In the narrow cockpit the third-person camera can clip into the walls (as in any tight room in Minecraft).
- The hyperspace effect is seen through the cockpit window; from the turrets' domes only the streaks at the edge of the view show.
