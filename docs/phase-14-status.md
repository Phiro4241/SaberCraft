# Phase 14: flight, cockpit, damage, hyperspace, walkable ships

Branch `claude/project-thread-od7jdm`. No release.

## What is new

### 1. Flight music
- Four new original pieces, made with `tools/synth_music.py` (no film music): `flight_cruise` (warm, wide, for rebel ships), `flight_soar` (open and rising, for all ships), `flight_imperial` (a dark march for TIEs and the Star Destroyer) and `flight_pursuit` (fast, for space battles).
- New music moods `FLIGHT` and `SPACE_BATTLE` (`SaberMusic`). Boarding starts the flight music at once, climbing out lets it fade at once (crossfade as set), and calm music waits a few seconds after that. A space battle means the own lasers fired in the last 12 s or the own ship was hit in the last 15 s.
- Order: defeat, boss, space battle, ground fight, flight, victory, night, exploration. As before, at most the current piece and one fading piece play.
- The Star Wars mode has the folders `flight/` and `space_battle/` for your own files; if they are empty, the usual situations play.
- Option `music_flight` (menu **Music**), plus the existing music volume and crossfade sliders.

### 2. Flight sounds
- Every ship now has two engine loops: the engine drone as before, plus a new **thrust** layer that swells while speeding up, holds a quiet rumble while cruising and nearly vanishes while braking.
- Hard speeding up from slow plays a **boost**, hard braking from speed plays a **brake** sound, each at most every 3 s.
- The Millennium Falcon has its own engine (rewritten: a rough, uneven sublight drive with crackle), its own thrust, boost and brake. The X-Wing, the TIEs and the Star Destroyer have their own thrust loops.
- All made with `tools/synth_sounds.py --phase14`. Volume caps and the ship volume slider stay.

### 3. Cockpit view (priority 1 reached)
- `ship_camera` = `cockpit` (default), `chase` or `keep` (menu **Graphics**, replaces the old third-person toggle; an old `ship_third_person: false` becomes `keep`).
- In the first-person view the camera sits where the pilot sits (`ShipFeatures.cockpit`): in the X-Wing canopy, in the TIE ball, in the Falcon's cockpit tube on the right, on the Star Destroyer's bridge tower. It moves, pitches and banks with the ship (`ship_cockpit_roll`); the mouse still looks around.
- A cockpit frame per ship is drawn around the view (`ship_cockpit_frame`): X-Wing struts and dashboard, the TIE's round window with spokes, the Falcon's window panes and consoles, the bridge window frames.
- F5 works as always while flying; climbing out restores the view from before boarding. Only this client's camera changes (works the same in multiplayer, nothing is sent).

### 4. Ship damage
- The hull mends only after `ship_repair_delay_seconds` (45) without damage, at `ship_repair_per_minute` (10 % of the hull per minute), and by default only when landed or resting.
- Crashes now also reset the repair delay. The hull is kept when the ship is sent away, called again, parked far away or when the owner logs out (before, it came back full).
- Weapons: `ship_weapon_damage` per kind (saber, blaster, ship laser, explosion, other) times the ship's toughness (`ShipFeatures.smallArms` and `heavy`). No hit takes more than `ship_max_hit_share` (25 %) of the hull.
- Big ships are hit on their whole hull: bolts are checked against hull boxes as big as the model (`ShipFeatures.hullBoxes`), not only the small collision box.
- Below 25 % hull a soft alarm sounds in the cockpit.
- At 0 hull: a big explosion with secondary blasts, debris and smoke; the wreck is removed at once (nothing can steer it, nothing drops). The pilot is thrown clear with slow falling, resistance and fire resistance; the crew of an interior gets out in an escape pod next to the wreck.

### 5. Darth Vader
- Only the head of `vader.png` was redrawn (`tools/paint_outfits.py`): slanted eye lenses with a red sheen, nose bridge, cheek tubes, a pointed silver mouth grille, a ridge on the dome and a rim that flares out at the sides and back. The rest of the skin is unchanged (checked pixel by pixel: only rows 0 to 15 differ).

### 6. Hyperspace
- Key **X** (rebindable, "Hyperspace jump"). The server checks: hyperdrive present, in flight, not falling or just crashed, hull above 10 %, enough energy (`hyperspace_energy_share`), cooldown (`hyperspace_cooldown_seconds`).
- The target lies straight ahead along the heading at most `hyperspace_distance` (160) away (TIEs 85 %, Star Destroyer 60 %). The way is swept with the ship's box: it stops short of blocks, never leaves loaded chunks, the world border or the height limits. Under 24 blocks of free way, no jump.
- 1.5 s of charging (light gathers, the pilot sees the stars stretch), the way is checked again, then the jump: streaks of light where it leaves and where it arrives, the pilot sees the tunnel and a flash. A hit or a new obstacle while charging calls it off.
- Everyone who sees the ship (and everyone near the target) gets the same packet with the same positions, so all clients show the same.

### 7. Walkable interiors
- A dimension of its own, `sabercraft:ship_interiors` (data pack; void with the space sky, no mobs, no beds). Each owner gets one plot per ship type, 1024 blocks apart, built once from vanilla blocks and kept (chests keep their contents, a new ship of the same type has the same rooms).
- **Millennium Falcon**: boarding ramp, ring corridor, cockpit tube and cockpit (four seats, consoles, the window onto space), main hold with the round game table and the curved couch, gun well with ladders to the upper and lower turret, crew quarters, galley, engine room with the hyperdrive, cargo hold, storage with hidden compartments.
- **Star Destroyer**: hangar deck with a force field onto space, landing pads, a TIE on its rack, crates and the flight control room; turbolift up to the command deck (corridor, conference room, sensor room); the bridge with the raised walkway, the two crew pits with consoles, the great window and the helm.
- Getting on: sneak and right-click your landed Falcon; stand sneaking below a Star Destroyer's hangar (its tractor beam lifts you in); or the hangar button **Walk aboard** (from the cockpit of a Star Destroyer straight onto its bridge). Guests may come aboard too (`ship_interior_guests`).
- Getting off: stand a moment on the ramp (Falcon) or at the hangar exit (Star Destroyer); you come out next to the ship (below the Star Destroyer with slow falling). The lever at the helm puts the owner into the pilot's seat.
- Safety: the rooms cannot be broken or built in (except in creative mode); falling out puts you back at the entry; logging out keeps you aboard and logging in finds you there; a destroyed ship sends everyone out in an escape pod; a ship that cannot be found sends you to the world spawn. The owner's ship never disappears while they are aboard.
- Room names appear when you walk in; inside there is a quiet ambient hum (`ship_interior_ambient`).
- Saved in `world/sabercraft/interiors.json` (plots and last known ship positions).

## Decisions where a different solution was needed
- **Interiors as a dimension instead of rooms inside the ship model**: the ships are moving entities drawn as models; walking around inside a moving model would need a whole physics system. A fixed interior in its own dimension is reliable, works with vanilla clients and many players at once, and costs nothing while nobody is aboard.
- **Plot coordinates tell the plan**: the client knows which ambient sound to play from the position alone, so no extra packet is needed.
- **No new entity types or blocks**: everything uses vanilla blocks, the LIGHT block for hidden light, and the existing ship entity.
- **Cockpit frame as an overlay**: drawing a separate cockpit model would need a model per ship; the overlay is cheap and fits every ship. The own ship model is still drawn, so the nose, wings and hull are seen from inside.

## Tests
See the PR for the results of this run.

## Known limits and open points
- Multiplayer: two players aboard at once was not tested with two real clients (the test environment has one client). The code keeps one entry per player and was written for it.
- The cockpit position was set by eye per ship; it may want fine tuning in game.
- The Falcon's turrets cannot fire, and the co-pilot seat is decoration.
- Interiors are not rebuilt when their plan changes in a later version (the plan has a version number for that, unused so far).
- Hyperspace keeps the height: a jump over a mountain range stops in front of it.
