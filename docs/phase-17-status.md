# Phase 17: walking through the Falcon, lights, hyperspace and a living Star Destroyer

Branch `claude/project-thread-e35lqw`, built on `main` (1d4a339, which holds Phases 14 to 16). No release, nothing merged into `main`.

The goals of this phase, in order: no more walking into or through walls anywhere in the Millennium Falcon, a dimmer for the lights of both ships, slightly quieter doors, a hyperspace effect that fills the whole view, no Falcon outside the Star Destroyer's bridge windows, and a Star Destroyer with a crew, maintenance vents and furnished rooms.

As before, everything is decided by the server and built from vanilla blocks and entities. No new blocks, items or entity types are registered, and the crew is no entity at all (see below).

## Walking through the Falcon: tested by walking

Phase 16's audit compared surfaces with each other and found "0 overlaps", yet Phiro still walked into the walls of the ring corridor. So this phase tests what a player actually does.

**`WalkAudit`** (used by the tests only) takes a real server player and walks it, with the game's own movement and collision, over a grid of 0.25 blocks through every place it can reach from the ramp, the hold and the gun wells (doors open). At each place it checks:

- **in a wall**: the player's body or eyes are inside a drawn solid surface (one could see through the wall or stand inside a console);
- **invisible stop**: the player is stopped by collision where nothing is drawn (an invisible block).

The audit can write top views of each floor (PNG), list the class and line that drew each surface that was hit, and dump the plan's cells as text, which is how the causes below were found.

Result: the first run found **20 330 places** in a wall in the Falcon. At the end of this phase it walks **189 912 places** and finds **0 in a wall** and **10 invisible stops**: six single grid places at the edges of the hold's doorways and four at the cap of the lower gun well. The test now allows at most 15, so any new problem fails it.

What was wrong and how it was fixed:

- **The ring corridor's outer wall was open where it is drawn closed.** The passages of the hold's doorways reached 1.5 blocks beyond the doorway (needed where the hold's curved walls pass just outside a doorway), and north and west of the hold that reached into the corridor's outer wall, where there is no doorway. One could walk into the wall there. The passages now stop at every round wall the doorway does not go through.
- **Flat panels in front of the round walls.** The panelling still put a flat panel on each wall cell's face towards the ring corridor, the hold and the engine room, also where the smooth curved wall is drawn further out. The panel stood up to most of a block into the corridor: one walked into it, saw through it from behind and it flickered against the curve. A curved wall's cell is now panelled only towards a square room.
- **Doorways through round walls.** Where the curve passes just outside a doorway's cells, the passage is open up to it, but the doorway's lining stopped at the doorway's cells, so one could look into the wall from the side. The linings now go on along the curve. Corridor ribs no longer stand in the hold's doorways.
- **Engine room and hyperdrive.** The outer wall above the machines is round now (drawn and colliding like the other round walls). The machines along it, the hyperdrive (the window round its core and the wider collars) and the couch and game table in the hold collide exactly as drawn, cell by cell, instead of as whole blocks or not at all.
- **Furniture in a round wall's cell.** A cell can hold a curved wall and a piece of furniture at once. The builder kept only one shape per cell; shapes in one cell are now merged.
- **Climbing onto furniture.** Seats, desks, bunks, the counter and the bed have caps, so nobody stands on them with the head in the ceiling's fittings; the ceiling chamfers are smaller in rooms three blocks high.
- **The hull reaching into the rooms.** Parts of the outside model went through the interior: the saucer's rim through the forward pods, the saucer's underside through the ramp hall at head height, its side walls through the ring corridor, the ramp housing's walls into the corridor and across the doorway to the ramp, greebles under the saucer into the ramp hall, the end of the tube 0.3 blocks into the cockpit above its doorway. They now end where the rooms begin.
- **Cockpit.** The window cone is walkable as drawn (its collision follows the cone down to a floor ledge under the glass), the round wall collides column by column as it leans, one can no longer climb onto its lower lip (where the shoulder was in the arches) or onto the dashboard, and the overhead panel is flatter.

Collision and model are still separate: the model is drawn by the client, the collision shapes come from the plan on both sides (shaped barriers). No invisible full blocks were added to hide problems.

### A server freeze found by the full test run

The merged furniture shapes were first built by joining many little boxes one by one, which became so slow for the Falcon's shaped round walls that the dedicated-server test hit Minecraft's watchdog (one server tick of 60 seconds). The shapes are now built as one voxel grid per cell (heights in steps of 1/16 block) and merged without re-optimising. A new test checks that each ship's whole plan builds in less than 5 seconds (now 224 ms for the Falcon and 88 ms for the Star Destroyer in the test client).

## Dimmable lights (both ships)

- The light switch (the Falcon's engineering bank, the Star Destroyer's officers' stations) now steps through **five levels: 100, 74, 52, 34 and 20 %**. A click goes one step darker and back to full light after the darkest; sneaking while clicking goes one step brighter. The overlay message names the level.
- The step is kept in the light blocks themselves (their light levels), so it is the same for every player, survives a restart and needs no new data.
- The client dims everything that belongs to the rooms: panels lit by the world, glowing models, screens and lamps (lamps dim most, to about 6 % at the darkest level; screens and other glowing parts least, to about 44 %, so the consoles stay readable). Each change fades smoothly instead of jumping (most of the way within a second).
- While the lights are dimmed the client no longer re-reads the world's light for the models (that would darken them twice).
- The test checks every level on server and client (each clearly darker than the one before) and takes a picture of each.

## Doors

- The closing sound is quieter than the opening one (volume 0.32 instead of 0.55).
- Opening, closing and the collision of a closed door are unchanged and tested again: a closed door has collision, an open one has none.

## Hyperspace fills the view

In Phase 16 the hyperspace tunnel was drawn ahead of the cockpit only, so the stars and the void showed at the edges of the view and through the side windows. Now, while in hyperspace and at the start of the way back, a deep blue shell closes round the whole Falcon (it fades in over about 0.7 seconds and out over 0.3) and streaks of light rush past it on every side, so wherever one looks (cockpit, side windows, turret domes) one sees hyperspace and no stars. The charging and arrival stages, the lines and blue rings ahead and the sounds are as before.

Once closed the shell is drawn as a solid surface, so the lines and rings in front of it always show; while it fades it is drawn together with the glowing lines.

### Glass no longer hides what glows behind it

While testing this, the lines of light were there through the cockpit window in one game and gone in the next. The cause: Minecraft draws the translucent parts of the model (glass, glowing parts, holograms) in an order that changes from one game to the next (they are batched by render type in a hash map), and the glass wrote depth, so whatever glowed behind it and came after it was hidden. The glass of both ships (windows, domes, the hyperdrive's window, railings) is now drawn like banner patterns, translucent without writing depth, so glowing parts behind it always show. This also affected the hyperspace tunnel of Phase 16 and anything glowing seen through the Star Destroyer's bridge windows.

## The Falcon is no longer seen from the Star Destroyer's bridge

The Falcon's interior plot and the Star Destroyer's plot are 1024 blocks apart, but the models are drawn up to 1100 blocks away, so the Falcon hung outside the bridge windows. The client now draws only the interior of the plot the camera is in. This is decided per player and per frame, so it holds on servers with players in both ships; nothing is hidden from anyone else, and the Star Destroyer's own hull is still seen from its bridge.

## A living Star Destroyer

### Crew

31 people aboard:

- **Bridge**: officers at the windows, two officers talking, a patrol along the walkway, guards at the door, technicians working at the pit consoles and seated crew.
- **Command deck**: a corridor patrol, crew in the sensor room, officers in the conference room.
- **Hangar**: guards, a patrol of two troopers, a technician and an officer.
- **Flight control**: seated technicians and an officer. **Lobby**: a patrol and a guard. **Reactor**: a technician.

Troopers wear white armour and carry rifles, officers grey uniforms, technicians black. They breathe, look about, walk with swinging arms and legs, sit, work at the consoles with their hands moving and talk with gestures.

They are **not entities**: every client draws them from the plan's list, and those on patrol walk along their route by the level's game time. So every player sees the same patrol at the same place, the server has nothing to tick or send, and there is nothing to break or to leave behind. Those who stand or sit still have slim collision columns, so one cannot walk through them; those on patrol have none (see Known limits).

### Vents

Two maintenance ducts to walk through, two blocks high and one wide: an L-shaped one of about 50 blocks from the corridor by the hangar along the hangar's side, and one under the bridge walkway from one crew pit to the other. They have grated floors and ceilings, vent walls and a few dim lamps.

### Furnished rooms

The corridor by the hangar got a rifle rack and a security desk, the command corridor wall screens. The pit consoles and flight control consoles got caps so one cannot climb onto them.

## Tests

- New client game test `Phase17GameTest` (registered in `fabric.mod.json`):
  - plan build time of both ships (under 5 s each);
  - the walk audit of the Falcon (at most 15 problem places) and, as a report, of the Star Destroyer;
  - pictures of the ring corridor, engine room, hyperdrive, cockpit, hold, ramp and lower turret, and of the Star Destroyer's corridor and vents;
  - the dimmer on both ships: every level on server and client, each darker than the last, a picture of each, the fade back to full light;
  - doors: the closing sound quieter than the opening one; a closed door collides, an open one does not;
  - hyperspace from the co-pilot's seat: waits until the client is in hyperspace (the server can run behind and catch up, so fixed waits missed it), checks that the shell round the ship is closed, pictures ahead and to the side, the jump ends;
  - the bridge view: the Falcon is not drawn from the Star Destroyer's bridge, the Star Destroyer is, and it is not drawn from inside the Falcon;
  - the crew: at least 25 people, pictures of the bridge, pit, hangar (twice, the patrol moves), flight control, corridor and sensor room.
- `Phase16GameTest`: the light switch now needs several clicks to come back to full light. Its audit of fighting surfaces is at 0 again in both ships (the longer doorway linings first drew six slivers twice where two doorways meet the ring corridor's outer wall).
- Standing up from a seat now first looks for room close round the seat itself (the couch and table collide as drawn now, so the middle of the next cell was often taken and one ended up two blocks away).
- Local runs (2026-11-15, cloud container, software rendering):
  - `build` passed.
  - All client test classes up to `DroidGameTest` passed in one run (1 297 checks); `DroidGameTest` then hung while closing and reopening its world (that class is unchanged in this phase and passed in an earlier run on this branch).
  - `DroidGameTest` to `Phase16GameTest` passed in a second run (532 checks) until a seat check in `Phase16GameTest` failed (standing up from the couch put the player 2.01 blocks away, 1.9 allowed); fixed as above.
  - After that fix `Phase16GameTest` and `Phase17GameTest` passed (92 checks together, the walk audits included). `Phase16GameTest` passed again after the doorway linings were changed, `Phase17GameTest` after the linings (with the walk audits) and after the glass change (without them).
- GitHub Actions has not run for this branch yet; it runs once when the pull request is opened.

Pictures (all from the test client): [`docs/images/phase17`](images/phase17).

## Known limits

- **Falcon walk audit**: 10 invisible stops are left, each a single place of the 0.25 grid: six at the edges of the hold's doorways (one is stopped a few centimetres before the drawn edge) and four on the cap over the lower gun well's ladder. Nothing is left in a wall.
- **Star Destroyer walk audit** (report only): the ship is far larger than the Falcon (the audit stops after 600 000 places) and was not reworked wall by wall in this phase. Of those places it lists 9 732 in a wall (1 075 of them at the eyes) and 8 118 invisible stops, down from 17 978 in a wall earlier in this phase (one climbed onto the bridge pit's consoles; they have caps now). Most of these are by design or harmless: chair backs one stands against, posts of arches and lift frames one brushes with the shoulder, and the crew's collision columns (the audit only knows the static model, so a crew member counts as an "invisible stop").
- **Crew on patrol do not collide**; one walks through them. Giving moving figures collision would need entities, which this phase avoided on purpose.
- The crew does not react to players (no greeting, no alarm).
- Glowing parts that are not lamps (screens, the light strips along the Falcon's ceiling) still glow at about 44 % at the darkest level, so the strips stay fairly bright.
- Not tested with several real players at once (the test environment has one client). The dimmer step is kept in the world, the crew follows the game time and the plot filter is decided per client, so all of these are the same for everyone by design.
- Not tested on real graphics cards or with shader packs (Iris/Sodium); the test client renders in software.
- Vanilla clients see neither the crew nor the hyperspace effect and the dimmer only as darker light blocks.
