# Phase 15: the starship interiors, rebuilt

Branch `claude/project-thread-ixhb7o`, built on the branch of PR #18 (`claude/project-thread-4i7mkg`), because Phase 14 (#17) and the boarding fix (#18) are not merged yet. No release, nothing merged into `main`.

The goal of this phase: the walkable Millennium Falcon and Star Destroyer from Phase 14 should look like the ships, not like rooms of concrete blocks. The void dimension, the plots, the way aboard and off (ramp, tractor beam, hangar button, lever) and all safety rules stay as they were.

## How it works

- **The server still builds the rooms from vanilla blocks.** They give the collision, the light, the things that can be used (helm lever, chests, barrels, ladders) and they are what a player without SaberCraft sees. No new blocks, items or entities, so vanilla clients can still join a server with the mod.
- **Furniture is made of invisible blocks.** Consoles, seats, tables, railings, crates, bunks and machines stand on barrier blocks: you bump into them as into real furniture, and the client draws the model over them.
- **The client draws everything else as models.** When a client with SaberCraft is in the interior dimension, the server sends it the list of built plots (new packet `InteriorPlotsPayload`, only to players in that dimension). The client rebuilds the plan in memory (`VirtualPlot`) and makes a model for each plot:
  - **Skin** (`Skin`): every face of a wall, floor or ceiling block that looks into a room gets a panel just in front of it, chosen from where it is (floor, ceiling, wall row, which room). Trims are added on top: skirting, the slanted strip under the ceiling, pillars in the corners, light panels where a light is near. So the panels always fit the blocks.
  - **Rooms** (`DestroyerRooms`, `FalconRooms`, shared parts in `Props`): consoles, screens, railings, seats, holograms, pipes, ribs, door frames, machines and so on, placed with the plan's own constants.
  - **Hulls** (`DestroyerVisuals`, `FalconVisuals`): the ship drawn around its rooms, so it can be seen out of every window and from outside in spectator mode.
  - **Textures**: one new atlas (`textures/interior/atlas.png`, 73 tiles of 64 x 64), all drawn by `tools/gen_interior_textures.py`. Nothing is copied from films or games; the shapes are original designs in the style of the ships.
- The models are cut into cells of 32 blocks and only the cells in view are drawn. They are lit by the blocks around them (looked up again every few seconds), screens and lamps glow in the dark, glass and holograms are see-through.
- New option `ship_interior_detail` (on by default, `ImmersionConfig`): off gives the plain block rooms of Phase 14.
- No fog in the interior dimension, so the whole Star Destroyer can be seen from its bridge.

## The Star Destroyer

- **Hull**: a wedge 620 blocks long and 380 wide at the stern, with an upper and lower hull, the trench along the rim, the stepped superstructure, the command tower with the bridge and its two domes, the engines with a glow, hull plating with windows and greebles. Seen from the bridge window it stretches out in front; in spectator mode it can be flown around.
- **Bridge** (at the top of the tower): the great window with mullions that match the outside, the raised walkway between two sunken crew pits, four rows of consoles in each pit with seats, the command platform with the helm (lever on a dark pedestal), officers' stations, railings with glass panels and lit edges, a hanging tactical screen, screens on the back wall, a big display over the door, ceiling beams.
- **Command deck**: corridor with arches, the conference room (stadium-shaped table, chairs, a hologram of a planet with a ring and a small Star Destroyer, wall screens, side windows onto the hull) and the sensor room (consoles, the tactical table with a moving fleet and a sweep), turbolift with frames.
- **Hangar deck** (new layout, in the belly): a tall hangar with girders and ribs, TIE fighters on their racks (original model), the force field in the floor (shimmering, with emitters and hazard edges), a round landing circle, crates; the flight control room behind its window (a 16-block desk with screens and seats); the corridor to the turbolift lobby.
- **Reactor room** (new): a round shaft with the glowing core (cones, collars, energy rings that move), conduits up the walls, glow bands, struts, the catwalk with a ring railing and a hazard edge, reached through a gallery from the lobby.

## The Millennium Falcon

- **Hull** (new): a round saucer with a raised middle, rim chamfers and a band with vents, two trench rings and radial seams; the two mandibles at the front; the cockpit pod on the right with its window cone and frames, the tube to it; the boarding ramp housing; the gun turrets above and below with glass domes and quad guns; the turning sensor dish; the engine grille glowing blue along the back; greebles and pipes.
- **Rooms are round now**: the ring corridor follows the hull with smooth curved walls, ribs every 30 degrees with lamps, pipes along the ceiling; the main hold is a round room with a ring of lights in the ceiling.
- **Main hold**: the round game table with a hologram of four original creatures moving on it, the curved couch, the engineering station with its instrument bank, screens and chair, a locker, the gun well with the ladder up and down.
- **Cockpit**: the dashboard, four seats, side consoles, an overhead panel, the window frames; the helm lever on its pedestal.
- **Engine room** (new shape, a curved room along the back): the hyperdrive in a glowing housing, machine banks along the outer wall, pipes.
- Crew quarters with bunks and a table, galley counter, a med bed with a scanner, cargo hold and storage with crates, the boarding ramp with rails, both turrets with seats and gun controls.

## Fixes

- Nothing floats: every block that needs support has it (checked for every block of both plans in the test).
- Clipping: furniture stands on invisible blocks, so you cannot walk into consoles, seats or the table; railings keep people off the reactor shaft and the edges of the bridge.
- In spectator mode the safety net that puts a falling player back at the entry no longer catches spectators, so the ships can be looked at from outside.
- The reactor gallery's door was walled up by the room next to it (found by the reachability test, fixed).

## Atmosphere and sound

- Animated: screens flicker and scroll, the reactor's energy rings move, the holograms turn, the sensor dish turns, the force field shimmers.
- New ambient sounds, played from where they are (louder when you are close, fading in and out): the reactor's hum, computer chatter at the bridge pits, the helm, the sensor room and flight control, the air of the hangar, the Falcon's hyperdrive hum and its console noises. All made with `tools/synth_sounds.py --phase15`.

## Tests

- New client game test `InteriorDesignGameTest`: pictures from many places and angles (first person, third person on the bridge, spectator outside around both ships, out of the windows, the turrets); checks that both plots are built and the client knows them; checks every block of both plans is held; checks an ambient sound plays near the reactor; logs the model size and build time; and checks the migration: a plot built from an older plan is rebuilt from the new one when it is next needed, and the items stored in its containers are kept.
- `Phase14GameTest` updated for the new layouts (camera spots, the turbolift going down to the hangar, the hangar exit). Its reachability check (every room, exit and lift can be reached on foot from the entry) now walks the plans' new volumes; it passes for both ships.
- `ShipBoardingGameTest` passes unchanged (boarding many times in a row, also on a dedicated server).
- Multiplayer: `Phase14GameTest` boards the Falcon on a dedicated server with a modded client and takes a picture inside (the models are drawn there too).
- Local runs (2026-11-14, cloud container with software rendering): the full suite (`build` plus all 33 client test classes) ran to `Phase12GameTest`, where one droid sound check heard nothing once ("three voices at once"); that class passes on its own (67 checks), and the classes after it (`Phase13GameTest`, `Phase14GameTest`, `ShipBoardingGameTest`, `InteriorDesignGameTest`) pass together (335 checks). No class failed on its own. GitHub Actions has not run for this branch yet.
- Bugs the tests found and that are fixed: the walled-up reactor door, the Falcon turret's collar closing the dome over the gunner, streaks on the Imperial floor texture.

Pictures (all from the test client): [`docs/images/phase15`](../images/phase15).

## Performance

Measured in the test client (software rendering, so absolute times are slow):

| | Star Destroyer | Falcon |
|---|---|---|
| Quads in the model | 72 167 in 347 cells | 22 478 in 9 cells |
| Building the model (once per plot) | about 370 ms | about 110 to 175 ms |
| Drawn per frame (bridge view) | about 42 000 quads, 181 cells | all of it |
| Memory (estimate) | about 15 to 30 MB | about 5 to 10 MB |

- The model is built once when the player enters the dimension (or when the plot list changes); this can give one short stutter. Nothing is built while nobody is in the interiors, and players outside the interior dimension are not affected at all.
- The light of the panels is looked up again only every few seconds, not per frame.
- On a normal graphics card 40 000 quads per frame are not much; with a weak computer the option `ship_interior_detail` can be turned off.

## Known limits

- Not tested on real graphics cards or with shader packs (Iris/Sodium); the test client renders in software.
- Players with a vanilla client see the block rooms with the invisible furniture blocks: they bump into tables and consoles they cannot see. The windows are barriers, so they look out into space.
- Two real players aboard at once was not tested (the test environment has one client); the models are per plot and the plot list is sent to every player in the dimension.
- The model is built on the render thread, so entering can stutter briefly on slow computers.
- The Star Destroyer's hull is seen from the inside only out of the windows; it is a model, not blocks, so nothing outside can be walked on.
- The Falcon's turrets still cannot fire.
