# Phase 21: enemy AI, droids and fights

Built on Phase 20 (branch `claude/phase-20-story`, PR #25, not yet merged into `main` when this was written). This branch contains Phase 20's commits; the pull request goes against `main`, so until #25 is merged it shows Phase 20 and Phase 21 together. No release, nothing merged.

Phiro played Phase 20 and asked for six things: smarter troopers, an alarm that is not forgotten, a new M-31 distraction, better droids, a real duel against the Schattenklinge with a scene before it, and tests. As before, the server decides everything from vanilla blocks and entities: **no new blocks, items or entity types are registered**. Four new sounds (a stun hit, M-31 rattling, the Focus in and out) are synthesised by `tools/synth_sounds.py --phase21`.

## What Phase 19/20 already did, and what was missing

Checked first, so that what worked stays as it was (checkpoints with the whole state, the repeatable cell escape, Hale's two ways, the arrival film):

| Already there (kept) | Missing or wrong (Phase 21) |
|---|---|
| Chasers find their way round walls (paths) | In a chase nobody ever shot: only the troopers of the final fight had blasters |
| Soldiers raise the alarm by comlink, others run to a wall comm | Chasers always knew where the player was (they ran to the player, not to where they last saw them) |
| Reinforcements come out of a door | After the search everything was as before: hiding reset the alarm completely |
| The crew stays a little more watchful after an alarm ("wary") | M-31's noise sent everyone within 8 blocks straight to one point, again and again |
| M-31's noise, with a mark where it went | The droids followed at the player's heels, into doorways, into people |
| Duel with wind-up, block, opening | A stroke early in his wind-up hit, and each hit reset his wind-up: hammering the attack key won |
| | Force push, grip, pull "did nothing" in the story (the people there are no entities) |
| | The duel began with a few lines of dialog, no scene |

## 1. The troopers

- **They shoot.** A trooper who chases the player now fires **stun bolts** (blue, weak: one heart) with an E-11. Three hits and the player is stunned and caught ("Hands up!", then back to the checkpoint as before). Being seized by a trooper who reaches the player still catches them too.
- **From a proper distance.** Each trooper has a role (fixed by his number, so a squad is mixed): one holds at about 9.5 blocks, one presses in to about 5.5 and fires twice in a row, one flanks at about 7.5. They aim before each shot (the rifle comes up to the shoulder, the client shows it), and they miss more at range, when the player sprints or moves. Between shots they step aside.
- **No blind running, no shooting through comrades.** A trooper only fires when he sees the player and no comrade stands in the line of fire. Out of sight he goes to where he **last saw** the player (the squad shares that place), not to where the player is.
- **Not on one spot.** Troopers with the same goal spread on a ring round it; no step brings a figure into another (they keep 0.7 blocks apart, and two who came close can always part).
- **Not unfair:** the first shot needs a long aim (14 ticks; 10 when the ship is very wary), the spread grows with distance, and a stun bolt is one heart.

## 2. Alarm and search

- **Seen:** chase, siren, reinforcements after 3 seconds (two troopers from the nearest door, at most four).
- **Lost (10 seconds out of sight):** the intercom calls a search: everyone in the chase goes to the **last known place** and searches 3 to 4 spots round it, faster than walking. Reinforcements that are done search again every 12 seconds while the search lasts. The alarm lasts 45 seconds after the player was last seen.
- **The search ends ("Search called off. Extra patrols stay in the corridors"):** the crew of this mission becomes more wary each time (3 levels: they see further, and from level 2 a suspicious one comes to look at once). Up to **two of the reinforcements stay as patrols** between their door and the place the player was last seen, until the mission ends. Both are saved with the checkpoint, so a capture or leaving the story does not reset them.
- **Consequences:** more eyes in the corridors, quicker suspicion, the searchers stay. Hiding helps to get away, but does not undo the alarm.

## 3. M-31's distraction

- **He drives there.** The droid key (N) sends M-31 to the spot on the floor the player looks at (up to 12 blocks, round walls, never through them; "M-31 finds no way there" otherwise). There he rattles and squeaks for 3.5 seconds.
- **Only those who hear it react:** within 7.5 blocks, 3.5 through a wall. The nearest goes to see, but listens first (a moment of "?"), then walks to a place just short of the noise and looks round. Up to two others only look over. Those who went do not stand in each other.
- **Not endless:** the same person goes the first time, only looks the second time ("That mouse droid again.") and ignores it from the third time. After each noise M-31 needs 20 seconds' rest ("M-31 is still worn out (12 s)").
- **Still useful:** one guard in the way (Kestrel's hatch in Mission 1, a single sentry) is drawn away reliably. R2-V7's scan shows who is about first.
- **Explained in the game:** Mira's radio in Mission 1, the hint over the hotbar and the journal entry describe the new rules (drives there, only hearers react, wears off, needs a rest).

## 4. The droids

- **Own places.** In the story each droid keeps its own place behind the player, measured from the way the player is going: M-31 close and low on the left (2.2 back, 1.1 aside), R2-V7 further back on the right (3.2 back, 1.4 aside). Never in front of the player, never in a doorway the player turns back to.
- **No needless following:** they set off only when their place is a few steps away (3.2 / 4.2 blocks), and stop within a block of it; looking round does not move them. Standing in each other or at the player's feet, they move to their places at once.
- **Narrow rooms:** a place must be free, on a floor, in sight of the player (not behind a wall) and a step away from the people of the ship; in a crawl way they follow in a line.
- **In a fight** they keep well back (5 and 6.5 blocks).
- **Personalities:** M-31 quick and close, the scout with the lure; R2-V7 steady, the one who opens doors and scans. Told to wait (U), they wait; after an errand M-31 goes back to where he was told to wait.

## 5. The duel with the Schattenklinge

**Before it, a scene** (11 seconds, skippable like the arrival film; shot in the hangar itself, with the player seen from outside): behind the player, the Schattenklinge with his back to them before the force field; closer on him as he turns round; low, as his red blade lights (title "Die Schattenklinge – Hangar der Tenebris"); over his shoulder onto the player. His two lines as subtitles. It plays once; after a defeat only his lines come.

**The fight** is a chain of moves that can each be seen coming:

- **Guard:** he circles at sword's length and parries every stroke (sparks, clash). **Three strokes into his guard in a row and he answers at once with a thrust**: hammering the attack key is the worst thing to do.
- **Blows with a wind-up:** overhead (blade raised over the shoulder), sweep (drawn back flat to the right), thrust (pulled in at the hip). The HUD names the blow while it winds up. He strikes in combos (1–2 blows, then 2–3, then 3).
- **Blocking** (guard, R, facing him) stops each blow. After the **last blow of a combo is blocked he staggers**: that is the opening (green "Open, strike now!"). Two strokes hit, then he spins away. A blow not blocked lands (4 / 3.5 / 3 damage), the screen edges flash red, he steps back.
- **Second phase (below 65 %)**: he steps back ("Not bad, spy. But that was only the beginning."), then longer combos, a **leap** (he crouches first, then jumps in with an overhead blow) and a **Force push** (he raises his hand first; the guard takes most of it).
- **Third phase (below 35 %)**: "Enough games!": three blows in a row, shorter openings.
- **Feedback:** his strength bar at the top with marks where his phases change and where he breaks off; sparks, sounds, a jolt of the figure when hit; red edges when the player is hit.
- **End (15 %)**: the fight stops at once, the troopers still about fall back, and a **short scene** shows it: he staggers, his blade goes out, "This is not the end, spy. Nightshade does not forget.", he runs for the flight control door and is gone. Then Dax over the comlink, and the Falcon step as before.

**Force abilities in the story:** only the guard and the dash work (the others would have nothing to act on; trying one tells the player so). In their place comes one skill that fits the story: the **Focus** (key G). Taking Ilyra's saber, the player learns it ("Ilyra's blade remembers"; journal entry). In a fight its meter fills slowly, and fast with each blocked blow; when full, the Focus slows all enemies to half speed for four seconds (the screen edges go cold and blue). It is full at the start of the duel. Old saves past the saber get it too.

## 6. Tests

### Automated (client game tests, run locally)

New: `Phase21GameTest` (Mission 6, both droids, the ship on alert):

- the droids keep their places behind the player, apart, not in front, also after walking into the hangar (`phase21_droids_*`);
- M-31's noise: the nearest goes the first time, only looks the second time, ignores it the third time; two quick presses send M-31 once; he comes back and then needs his rest;
- seen by a trooper at 9 blocks: the first stun bolt comes from a distance (more than 3 blocks; measured 7 blocks), and the player is caught (`phase21_chase_shot`);
- out of sight: they search the last place, the alarm stays meanwhile, reinforcements come; when it ends the crew is more wary and two patrols stay.

Changed: `StoryGameTest` (the chapter played through):

- the duel: the scene before it (the camera in the hangar, the player seen from outside, the blade lights at the right moment, the scene remembered), the music, the Focus known, push locked and guard/dash allowed, strokes into his guard parried and three in a row answered, a blocked blow leaves him staggering and then a stroke hits, a blow not blocked lands, the Focus key slows time (server and HUD), the second phase begins with him stepping back, he breaks off, the scene of his flight plays, and he is gone in the Falcon step (`story_duel_*`);
- Mission 1's lure and the hangar distraction now go through M-31 driving there.

Results of the local runs are in the pull request.

### By hand (not covered by the automated tests)

- How the fights **feel**: whether the troopers' accuracy, the stun hits, the duel's timings and openings are fair and challenging. The numbers are in `Combat.java` and `Finale.java`.
- The **poses** in motion (aiming, the three wind-ups, stagger, crouch, raised hand, spin) and the camera of the two scenes: the tests take pictures, but timing and smoothness need eyes.
- The **sounds** (stun hit, M-31's rattle, Focus): synthesised and played by the tests, but not listened to.
- Long play: patrols and wary levels across several alarms in one mission, and M-31 used often in narrow rooms.
