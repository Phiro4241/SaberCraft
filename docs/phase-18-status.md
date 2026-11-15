# Phase 18: a Falcon free to walk, a crew to talk to, crawl ways and a livelier Star Destroyer

Branch `claude/project-thread-fulag1`, built on `main` (f25df4e, which holds Phase 17). No release, nothing merged into `main`.

The goals of this phase, in Phiro's order: the Falcon's collision (no invisible walls or floors, seats and consoles in reach, nobody suffocating or stuck), the upper turret and the cockpit closed, natural stormtrooper animations, crawl ways one block high with a hidden hatch and a hidden room, livelier rooms (above all the reactor), more kinds of crew with fixed heads, vanilla blocks replaced by drawn models, and speaking to the crew.

As before, everything is decided by the server and built from vanilla blocks and entities. No new blocks, items or entity types are registered; the crew is still no entity.

## 1. The Falcon's collision

### Why Phase 17's audit said "10 invisible stops"

The walk audit of Phase 17 counted a stop as "seen" when any drawn surface was near the place the player was stopped, and the floor under the player's feet always was. Re-run with that fixed, the Phase 17 Falcon had **9 471** places where the player was stopped by something not drawn. The audit now ignores the floor (the box it looks in starts a little above the feet), and it got a second check: **invisible ground**, a player standing on a barrier where nothing is drawn under the feet.

### What was wrong and how it was fixed

- **The couch suffocated people.** The cap over the couch (one cannot climb onto it) reached from 2.0 to 2.5 blocks over the floor; a seated player's eyes are at 2.47. In creative (as the Phase 16 test ran) nothing happens; in survival one took suffocation damage. The couch now collides as drawn: the seat 0.45 high, the backrest a full block, the table round as drawn, no cap at all. A seated player is never "in a wall" (a mixin tells the game so while sitting on an interior seat), and standing up leaves the player free. All 10 seats of the Falcon are tested in survival: sitting 3 seconds without harm, standing up free.
- **Invisible blocks stole the clicks.** A barrier's outline (what the crosshair hits) was always a full block, even where the collision is shaped, so a click on the couch, a seat or a console often hit an invisible block in front of it. The outline now has the same shape as the collision, and the outline of barriers that do nothing is not drawn.
- **Old ships kept their old blocks.** A plot is rebuilt when its plan changes, but only when someone boards; a player who logged in aboard (or was aboard during the update) kept the old blocks: invisible walls in the ring corridor and towards the hyperdrive. Now a plot is also rebuilt when a player logs in aboard it (if nobody else is aboard), and the player is moved to the entry if the rebuilt plot has a wall where they stood.
- **Climbable furniture and caps.** Half-high caps let one step from one to the next and stand in the air; the caps over the dashboard, the engineering desk and the galley counter (and the barrel beside it) are now a whole block high. The medical bed collides as drawn (one may sit on it), the quarters' table too.
- **Turrets.** The barriers of the gun well's ring stayed over the gun controls; only the two cells round the ladder remain. The lower turret had no floor drawn (one stood on nothing): it has a floor plate now.
- **Cockpit.** Behind the cockpit door one could look down through the construction into the stars: the floor between the tube and the cockpit was missing. It is drawn now. The hyperspace lever is reachable from both front seats (tested by clicking it from the seat).

### Result

The corrected audit walks **181 785 places** of the Falcon (a 0.25 grid, doors open) and finds **0 in a wall** and **38 invisible stops**, down from 9 471:

- 8 single grid places at the edges of the hold's doorways (one is stopped a few centimetres before the drawn edge), as in Phase 17;
- 30 places standing on the edge of a rear cockpit seat, right by the round wall: there one is stopped where the wall curves in above the seat. One only gets there by stepping onto the seat's front edge.

The test now allows 40 such places (Phase 17's test uses the same limit), so any new invisible wall fails it.

Two more things the audit learnt: blocks the game draws itself (the galley's barrel, chests) count as drawn, and a crew member's column counts as the person standing there (one is stopped by a person, not by an invisible wall). The crew's columns are now as wide as they are drawn with their hands (a working pair of hands or a talking gesture reached out of the old slim column, one stood in them), and nobody can stand on the galley's chest any more (the shelf over the bed was then in the eyes).

### The collision debug view

`/sabercraft_collision` (client command, also `/sabercraft_collision true|false`) shows the collision boxes around the player in a ship's interior: red for full blocks, orange for shaped ones (drawn furniture, round walls), green for seats and consoles, yellow for doors. It is only a view: nothing changes for the server or other players.

## 2. Crew

### Figures and animations

- Arms and legs have two parts each (upper arm and forearm, thigh and shin), placed so that hands and feet end where they should (a small two-bone solver bends the elbows outwards and down).
- **Troopers** hold the rifle with both hands: the right hand on the grip, the left under the barrel, the rifle across the body pointing forward and a little left (no more floating rifle). Standing guard they hold it at low ready and look around now and then. Walking, the legs swing with bent knees, the body bobs a little with each step and turns with it, the rifle moves with the body. At a console the rifle is slung over the back.
- **Officers** clasp their hands behind the back when standing, swing their arms when walking (opposite to the legs) and gesture with one hand when talking. At consoles everyone types (the hands move a little).
- **Heads**: a face tile in front, the sides and back from the head tile, and now a top of their own (hair tinted to the hair colour, a helmet's dome) and a chin underneath, so the top of the head no longer shows the side texture. Four faces (skin tones, hair, moustaches) chosen per crew member.
- **More kinds**: officers in four uniforms (grey fleet, black security bureau, olive army, white admiral without cap), caps for most officers, TIE pilots (black flight suit, helmet with breathing hoses), honour guards in red robes with pikes, and on the Falcon a smuggler (vest, holster), a tall furry mechanic (a head taller, fur, bandolier) and a rebel pilot in an orange flight suit.

### Who is where (Star Destroyer)

The admiral and the officer of the watch at the bridge window, a fleet officer and a security officer talking on the platform, an officer walking the walkway, troopers guarding the bridge door, officers and technicians in turn in the pits, a trooper patrol in the command corridor, two honour guards by the command deck's lift, the sensor room's crew, an army and a security officer in conference, troopers at the hangar door and walking round the force field, a technician at the crates, an officer by the landing circle with two TIE pilots talking, flight control's crew under an army officer, a guard and technicians at the reactor, and (see below) someone in the hidden room.

### Speaking to the crew

Right-click someone of the crew (the use key, any item or none in hand): they answer with a short line in the chat, e.g. `[Stormtrooper] Move along.`

- Lines by role, i.e. by room and job: bridge guards, pit crew, sensor room, conference, hangar, flight control, reactor, maintenance, patrols, admiral, security bureau, army, pilots, honour guards (who say nothing: "The guard does not move."), the smuggler, the mechanic (growls described in words), the rebel pilot, the spy. 22 roles, 3 to 6 lines each, in English and German.
- Each answers in turn from a random place of their lines, so the same line does not come twice in a row; the same one answers the same player again after 4 seconds at the earliest (and any answer at most once a second).
- Now and then (every 1.5 to 3 minutes, not right after one spoke to someone) someone near a player in sight remarks on something by themselves (8 remarks on the Star Destroyer, 6 on the Falcon).
- Multiplayer: the answer goes to the player who asked only (no chat spam for others), cooldowns are per player. The client finds who is in the crosshair and asks the server, which checks again along the player's own view at the server's game time (and that no wall is between).
- Sound: a short helmet-radio click for helmeted crew, a soft console chirp for the others, both synthesized by `tools/synth_sounds.py --phase18` (no voices). The text works without sound.
- Those of the crew who stand in one place still collide (a slim column, as in Phase 17), but the column no longer catches the crosshair: a click there goes to the person, not to an invisible block.

## 3. Crawl ways and the hidden room (Star Destroyer)

- The maintenance ways of Phase 17 (two blocks high) are now **crawl ways one block high**: from the hangar corridor west and north into flight control, and under the bridge's walkway from one crew pit to the other.
- They start behind **small hatches** low in a wall: a grille in the wall's own vent pattern with a thin frame and a latch, easy to overlook. A click opens it (it swings up into the room); it closes by itself once nobody has been near it for a while.
- **Crawling**: crouching right in front of an open hatch puts the player down on all fours (the game's own crawling pose), so they fit into the way; inside, the low ceiling keeps them crawling, and they stand up again on leaving. Server and client work this out the same way.
- A side way from the hangar deck's crawl way leads to a **hidden room**: a forgotten store room with a cot, crates, a desk with a terminal and datapads, a lamp on a cable, and a rebel spy hiding there (who would rather you had not seen them).

### Found on the way: the hangar deck had no plan

Everything north of the Star Destroyer's plot corner (the whole hangar deck, flight control, the hangar corridor and the hidden room lie at negative z) was not recognised as part of the ship: the game looked for the plan in the row south of it and found none. So all shaped barriers there were full blocks (flight control's chairs could not be sat on, Phase 17's consoles and furniture there collided as whole blocks), and nothing there could be clicked: no hatch, no store, nobody of the crew. The plan of a spot is now looked up in the row whose corner is nearest (plots are 1024 blocks apart, no ship reaches 512 blocks from its corner). This bug was there since Phase 14; the Falcon was not affected.

## 4. The reactor and other rooms

- The reactor shaft has a **lower level** on the shaft floor round the core's base: a grated deck with a hazard band at the core's foot, **four energy converters** (tall machines up to the catwalk with pulsing light bands, a turning collar each and a pipe into the core), three **control consoles** along the wall, a **status hologram** (rings rising over a console), lights, a technician, an army officer and a guard.
- A **maintenance lift** connects it with the catwalk (a lit pad on each level, like the turbolifts: stand on it a moment).
- A **railing round the core's foot**: the core's cone could be climbed like a stair, up into the converters' pipes and the glowing column. The railing collides (a block high, half a block more that only collides, so nobody jumps over it).
- Everything on the lower level collides where it is drawn: the cone, the converters, the consoles (nobody climbs onto them), the lift's four posts, the ribs up the shaft wall (they stood half a block into the room), and the catwalk's struts now start above head height (they ran through the heads of people walking along the wall).
- **Other rooms of the Star Destroyer** (found by its walk audit, now that it walks the hangar deck too): the bridge pits' chairs collide as high as their backrests (one stepped onto a seat and stood in the armrests), the caps over the pit consoles are a block and a half high (from a chair one jumped onto the console and stood in the air over it), the helm and the officers' stations at the window have caps too, and nobody stands on a crew member's head any more (their columns reach a little over the head); the sensor room's consoles have caps (from one, one got onto the head of the technician beside it); in the hidden room nobody climbs onto the desk, and the cot's rolled-up jacket collides. The bridge railing got caps like a fence (one jumped onto it and walked along on air), the rifle rack and the security desk in the hangar corridor and the force field's emitters in the hangar collide as drawn.
- **Vanilla blocks replaced** by drawn models: the blackstone stairs down into the bridge pits (drawn steps with lit edges, collision like stairs), the hangar's barrels, chests and iron blocks (imperial cargo containers; a click on one opens the store under it, so nothing to store is lost), the chains holding the TIE racks (drawn hangers), the helm's lever (a drawn throttle), the stray chest in the sensor room. On the Falcon the barrels and chests one sees (cargo holds, galley, quarters, engineering) are all barrels now, each inside a drawn cargo container a little bigger than the barrel: a click still opens it, and what was stored in them moves over when the ship is rebuilt.

## 5. Tests

- New client game test `Phase18GameTest` (registered in `fabric.mod.json`):
  - the Falcon's walk audit (at most 40 places, see above) and, as a report, the Star Destroyer's;
  - all 10 seats of the Falcon in **survival**: a click sits one down, after 3 seconds full health and not in a wall, standing up leaves one free;
  - the crosshair reaches what one uses (from where one stands, looking at it): the hyperspace lever from both front seats, the pilot's and a rear seat, the couch from the front, the side and the other end, the engineer's seat, the status desk, the light switch, both gunners' seats;
  - pictures of the couch, both turrets, the cockpit door and floor, the dashboard and the collision debug view;
  - speaking to the crew with the use key, as a player does: the Falcon's three, the bridge guards, the honour guards, both TIE pilots, the technician on the reactor's lower level and the spy in the hidden room. Each answers exactly once, the answer is in the chat, a second click at once gets no answer (cooldown); someone near remarks on something by themselves;
  - the hangar deck: a seat in flight control can be sat on, a cargo container opens the store under it;
  - the crawl way: a click opens the hatch, crouching in front of it one gets down and crawls in (pose and position checked), out of the side way into the hidden room one stands up again, the hatch closes by itself once nobody is near;
  - the reactor's lift takes one from the catwalk down to the lower level;
  - pictures of the crew (troopers, patrol, honour guards, admiral, pilots, pit), the drawn steps, the helm, the hangar's containers, the hatch, the crawl way, the hidden room, the reactor's lower level, and the chat.
- `Phase17GameTest`: its Falcon walk audit uses the same limit as Phase 18 (the audit is stricter now).
- Local runs (2026-11-15, cloud container, software rendering):

  - `./gradlew build gametestClasses`: green.
  - **Run 1, the whole suite** (all 35 classes in `fabric.mod.json`): every class up to `Phase13GameTest` green (1 650 checks). `Phase14GameTest` then failed: its check that every room can be reached on foot did not know the crawl ways (it walks two blocks high and found neither the vents nor the hidden room). The check now also crawls: through a hatch or opening into a crawl way, one block high. The suite stops at the first failure, so the remaining classes ran in run 2.
  - **Run 2:** `Phase14GameTest`, `ShipBoardingGameTest`, `InteriorDesignGameTest`, `Phase16GameTest`, `Phase17GameTest`, `Phase18GameTest`: all green (408 checks), with both Falcon walk audits (181 785 places, 0 in a wall, 38 invisible stops) and the Star Destroyer's walk report.
  - **Run 3:** after one last change (a cap over the middle of the bridge's helm, see section 4), `Phase18GameTest`'s Star Destroyer part again: green (57 checks).
  - The changes after run 1 (the Falcon's stores, the helm's cap, the Phase 14 check) only touch the ship interiors, which the classes of run 2 test; the classes before them do not enter an interior.

The Star Destroyer's walk report (600 000 places, the walk's limit) went from **12 335 places in a wall and 64 922 invisible stops** (with the corrected audit, when this phase's work on it began) to **2 026 in a wall (768 at the eyes) and 2 573 invisible stops**. What is left is listed under the limitations.

Pictures (all from the test client): [`docs/images/phase18`](images/phase18).

## Known limitations and open points

- **Falcon:** 38 invisible stops remain (see above): one is stopped a few centimetres before the edge of a hold doorway, and standing on the front edge of a rear cockpit seat one is stopped where the round wall curves in above it.
- **Star Destroyer:** its walk audit is a report, not a limit (the walk stops after 600 000 places, so it does not reach every corner of this big ship). See the numbers above. What is left: at the bridge window one is stopped half a block before the glass (the window's frame stands out from the glass; about 1 300 places), at the top of the steps down into the pits one is stopped a little before the walkway's drawn railing (about 1 250), and the shoulder grazes the hangar's wall pillars, the arch posts of the corridors, the trim along the pits' back walls and the conference chairs (a few hundred places each, a few centimetres deep). The Star Destroyer was improved where its audit pointed, not rebuilt wall for wall.
- **Crew collision** is a box per person who stands still (as wide as the hands, a little higher than the head, so nobody stands on a head); patrols and walking crew do not collide (they would need entities, which this mod avoids).
- **Speaking to the crew** is text in the chat only: no voices, the sounds are a click and a chirp.
- **Multiplayer:** answers, cooldowns and remarks are per player, but the test client is one player; two real players at once were not tried.
- **Crawling** uses the game's own crawling pose; another mod that changes poses could interfere.
- **The Star Destroyer's stores:** the chest in the sensor room is gone; when an existing Star Destroyer is rebuilt for this version, what was in its chests moves into the new stores under the hangar's containers (the Phase 15 mechanism, whose test with the Falcon ran green again; the Star Destroyer's move was not tested on its own).
- **Vanilla blocks left:** the Falcon's ladder up to the gun wells (climbing needs it; it hangs on a drawn post) and the wooden trapdoors over the hidden compartments in the starboard hold (they open and close as before).
- **Not tested:** playing by hand, real graphics cards and shader packs (the tests use software rendering), a dedicated server with real players (only the automatic dedicated-server test), vanilla clients (they see neither crew nor hatches, as before).
