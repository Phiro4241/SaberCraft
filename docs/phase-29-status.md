# Phase 29: a harder boss fight, clean film cameras, hover texts, doors that open only for whoever passes

Built on `main` after Phase 28 (PR #33, merge commit `163a7c1`). No release, nothing merged. From Phiro's Phase 28 playtest: the boss fight was too easy to cheese (strokes from behind, a guard held up the whole time), the film cameras went through walls and into the player, the cover and the toolbox showed a raw key under the crosshair, and doors opened for people only walking past. As before, the server decides everything from vanilla blocks and entities: **no new blocks, items or entity types are registered**.

**Saves.** Old stories load as before; the save gets no new field. The stricter guard, her new moves and the camera system act only while they run.

**The Star Destroyer's interior plan stays at version 13.**

## 1. Hover texts: never a raw key

Under the crosshair the cover in Mission 2's side way and the technicians' toolbox (both Phase 28) showed `sabercraft.story.point.vent_cover` and `sabercraft.story.point.toolbox`: their words were never written into the language files.

- **Root cause fixed:** both have their words now (German and English): "Abdeckung untersuchen", "Schraubendreher nehmen".
- **So it cannot happen again:** every thing to use in a ship (`Mission.Point`) must be declared by the chapter that uses it (`Mission.Point.declare`); one used without being declared is logged at once. The test checks that every declared point has its words in every language of the mod (23 points).
- **A missing word never shows as a key:** the client shows the plain "Benutzen" instead and logs the key once (`StoryHud.words`).
- Checked in the game: the text under the crosshair at the cover and at the toolbox.

## 2. Doors: open only for whoever really goes through

The corridor's trooper patrol opened a side door it only turned in front of at the end of its round (so it looked as if a passer-by opened it); in the boss fight the doors by the arena opened and shut again and again as the two fought beside them. Now (`InteriorSystems`):

- **Story figures** (troopers, officers, the boss, the crew) open a door only when **their way goes through it**: the way they are walking (their scripted way, their path, or the next 4 s of their round) crosses the doorway within 2.15 blocks, or they stand in it (within 0.6 blocks; an open door stays open while one is within 0.85, so it never shuts on someone in it). Walking along a wall past a door opens nothing.
- **The player** opens doors as before, by walking up to them, except **in a fight** (Mission 6 from taking the saber on: the escape and the boss duel; an alarm chase before that does not count): then a door opens only for a player right in the doorway (within 0.9 blocks) or walking into it (within 1.6 blocks, coming closer two looks in a row), not for one fighting beside it.
- **A door that just shut** rests 10 ticks before it opens again for anyone only nearby (no flapping); a player who keeps walking into it still opens it at once.
- Story doors (locked, bolted, the hatch, Hale's trap, the lift bay) are not touched: their own rules come first, as before.

## 3. The film cameras: never through a wall, never into a person

All the story's films shot where the story happens (the takedowns, the Hale scene, the maintenance way, the saber in the lab, the boss's blade lock, her defeat and flight, the duel; all but the look from high in the open hangar at her TIE lifting off) now go through one camera system (`FilmCamera`); each film still plans its shots as before.

- **Before a shot begins** its whole glide is looked at (ten moments of it), from the point the shot is about (between the two, a person's chest: a point in the room) out to the camera. If a wall or anything solid stands between at any moment, or the camera would be in a person, other places are tried: turned round that point (25, 50, 80, 115, 150, 180 degrees each way) or raised. The best of them is kept for the whole shot (clear all the way, turned or raised as little as can be, the people in view; if none is clear, the least blocked); a shot that is clear as planned stays as planned.
- **Each frame** the camera stays on the room's side of everything between it and the point: pulled in at once in front of a surface (never first into it), out again smoothly, 0.24 blocks off every surface, out of the people's bodies, at least 0.6 blocks from the player's eyes.
- **If the shot must change mid-way** (people moved), its new place is reached by gliding round the point, not by a cut.
- **Never a jump within a shot:** from one frame to the next the camera moves at most 0.8 blocks a tick. Where the fitted place leaps further, it follows that fast, but only through places that are themselves clear (in nothing, in nobody, nothing between it and the point, room round it): straight on, or round what stands in the way (a little to a side, up or down). Where no step is clear it holds its last clear place and turns to look, at most 0.3 s; then it cuts to the fitted place (only to a clear one: a fitted place that is not clear is never taken while the last one still is).
- **Walls** are the blocks one collides with (the ship's barriers, closed doors) and the drawn rooms (`MeshRays`: the detailed models' walls, which are drawn one-sided, so a camera behind one is "outside" even where no block is).
- **The point a shot is about** is moved out of anything solid first (towards the camera, else up), where people stand near it only to a place they can be seen from or towards the nearest of them (through the wall into the next room only as a last resort), and never into a person.

## 4. The saber scene in the lab

The camera glided into the player's head and the figure vanished. The scene's shots were redrawn: it opens from black behind the player, over the left shoulder; then from the side by the holder (more than two blocks from the player); wide over the lab; out through the door to the shaft; back over the player's shoulder; into the eyes only through a moment of black. **No shot glides out of the eyes or back into them any more**, and the camera system keeps it out of the player (section 3).

## 5. The boss

### 5.1 Strokes from behind

One could jump behind her and strike again and again while she did nothing (her head turned to the player at once each tick, so every stroke counted as one from the front, while her body, drawn as it turned slowly, showed her back).

- **She turns naturally:** 16 degrees a tick, about 21 towards someone more than 60 degrees to her side, 26 once she has noticed someone behind her (after 14 / 9 / 6 ticks on Leicht / Normal / Schwer), her body close behind her head. Her head no longer swings round by her own steps.
- **A stroke from behind hits once.** The next one within 50 / 70 / 90 ticks she **turns aside, whirling round**, and strikes back at once with a **turn-about blow** that reaches behind her.
- **Standing behind her** she turns round, steps round, or strikes back; standing in her or on her one is pushed out. Her plain blows reach only before her and to the sides: behind her one is safe from a plain blow, but not for long.
- **Close pressure:** one closer than 1.5 blocks is **shoved away** with the Force (a quick, visible hand; at most every 4.4 / 3.5 / 2.8 s on Leicht / Normal / Schwer).

### 5.2 Blocking

Holding the guard up the whole time is no longer a defence (`SaberAbilities.StrictGuard`, only in her fight; free play keeps the settings' rules):

| | Leicht | Normal | Schwer |
|---|---|---|---|
| holding costs (stamina a tick) | 0.35 | 0.55 | 0.75 |
| each blow in a row costs more (per blow, within) | +20 % (1.5 s) | +30 % (1.8 s) | +40 % (2.1 s) |
| at most | ×1.6 | ×2.0 | ×2.4 |
| stamina comes back after the guard came down | 0.9 s (+ half the time it was held, at most 1 s) | 1.2 s (+ half, at most 1.6 s) | 1.5 s (+ half, at most 2.2 s) |
| and at (of the usual speed) | 75 % | 60 % | 50 % |
| broken by her blow: locked out (held until empty) | 1.5 s (1.2 s) | 2.0 s (1.6 s) | 2.75 s (2.0 s) |
| then worn out (costs more, no perfect parry, slower stamina) until back to | 40 % | 50 % | 55 % |

- **A perfect parry still matters:** the guard raised just before her blow lands (6 ticks) parries perfectly (she reels, a longer opening) and costs little, and it does so every time while the guard is not shaking (35 % stamina or more) and one is not worn out (two timed parries in a row: both perfect, both open her up).
- **No parrying by mashing:** the guard key cannot be raised again right after lowering (its pause, as in free play); a block raised again within 6 ticks of coming down parries nothing perfectly (her own rule, for a block on the use key; in this game version the saber has no block on the use key, the test found, so only the guard key counts).
- A block held with the use key, where an item has one, follows the same strict rules.
- The settings' longest hold still lowers the guard (as in free play); it can be raised again after its short pause.

### 5.3 More active, more varied, still fair

- **The slam** (once she has lost a fifth of her strength, well before her second phase): she raises the blade high in both hands and crouches, the floor before her glows where it will strike (1.2 s of warning on Normal, heard and seen). She aims only at the start; **0.9 s before it falls its way is set** and the glowing arc stays where it is (35 degrees to each side, 3.9 blocks). **A step aside, walking, gets out of the arc in time**, even without leaving its reach; stepping back out of its reach, a dash or a jump work too; the guard holds it at a high cost; standing still before her it hits hard. After one that misses or is blocked her blade is stuck a moment (an opening); after one that hits she steps back.
- **The lunge:** from 3.4 to 5.8 blocks she lunges in with a thrust (to be blocked or sidestepped).
- **Her moves are chosen by weight**, not in a fixed order: what the player does matters (a guard held long: she goes for what breaks it, the push or the slam; far away: lunge, leap, throw; close: shove, combos). The pressure test measured her attacks (section 7).
- No unblockable or unseen attack: every move has its wind-up.

### 5.4 Her throw

Kept and varied: **high** (to be blocked or dashed under, as before) and **low** (her throws in a fight, up to three now, are never the same way twice running):

- The low throw is let go from low down and skims at shin height past the player (about 0.6 blocks above the floor as it passes): **jumped over at the right moment, it misses**; standing, guard down and not dashing, it strikes the legs (a guard raised towards it or a dash stops it too).
- On its way back she **pulls it up over the player's head** (2.4 blocks over its straight way back, up within its first sixth, down into her hand in its last fifth). It used to come back low, from behind, into the legs of a player who had just jumped it on its way out: jumped once, it is escaped now.
- The low throw **hits at most once** (struck on its way out, one is not struck again from behind as it comes back).

### 5.5 The scene at half her strength

The scene (the blade lock) began with the player frozen in the air. Now it **waits until the player really stands on the floor** (by the world under their feet, not by the game's flag, which can be stale, for example after being moved); she steps back if the player is closer than 2 blocks, and nothing hurts meanwhile; after 2 s at most they are set down on the floor under them. It is shot from whatever side the two stand (her side, behind her, by a wall), with the camera system of section 3.

### 5.6 Smart and fair

- She does not teleport in the fight: every move is walked, leapt or lunged. Only as the half-strength scene opens (from black, 0.3 s) is she set to the lock distance before the player, as before, hidden by the black.
- Her openings after a missed or blocked slam, a parried blow, a missed rush stay.
- Leicht / Normal / Schwer scale her noticing, her memory of strokes from behind and the guard's rules.

## 6. Quality

- No debug texts in the game (only the log).
- No new softlock: the scene waits at most 2 s; the doors' story rules come first; the camera falls back to its last clear place, never to a wall.
- Saves and checkpoints unchanged.

## 7. Tests

New: `Phase29GameTest` (in the real game with a client); its parts can be run alone with `PHASE29_ONLY`:

| Section | Part | What it checks |
|---|---|---|
| 1 | `hover` | every declared point has its words in both languages; the text under the crosshair at the cover and the toolbox (never a key) |
| 2 | `patrolDoors` | Missions 2 and 5, 32 s with the player far off: a door that opens must lie on someone's way through it; in Mission 2 the corridor's patrol walks its whole round |
| 2 | `fightDoors` | the doors by the arena while the two fight beside them: none opens, but one still opens for the player walking through; the door rules (a player in a fight, a figure in an open door, a passer-by, the rest after shutting) |
| 4 | `saberCamera` | the lab's saber scene: never inside, never blocked, never in a person, no jump within a shot, never nearer than 1 block to the player's eyes, the figure drawn all the time |
| 3 | `sceneCameras` | the Hale scene and the maintenance way's scene: never inside, never blocked, never in a person, no jump within a shot |
| 3 | `takedownCamera` | the takedown in the detention block at three moments of the guard's look round (three directions) and in the lab |
| 5.1 | `rear` | strokes from behind: the first lands, then turned aside with turn-about blows; standing behind her she faces the player soon |
| 5.2 | `guard` | holding drains; long held it gives way (raised again after the settings' pause); stamina comes back only later; blows in a row cost more; two timed parries: perfect, she open each time |
| 5.3/5.4 | `slam` | warned, falls; on guard costly; a step aside escapes it; the low throw hits one standing, not one jumping over it |
| 5.3 | `pressure` | her attacks in both phases (how many, how varied, the longest calm) |
| 5.5 | `sceneInAir` | the scene with the player in the air: it waits, begins on the floor, its camera clean |
| 5.5 | `sceneSides` | the scene with the player at her side, behind her, by the wall: the camera clean |
| 3 | `defeatCamera` | her defeat by the arena's wall: the film's camera clean |

Changed tests: `Phase26GameTest` and `Phase28GameTest` (small adjustments to the new door and guard rules).

### Results (20.11.2026, in this cloud container: Xvfb, llvmpipe, no real GPU)

- **The whole suite (47 classes), in two parts:** the full run on commit `36ea23a` passed all 45 classes before `Phase28GameTest` (1 h 20 min) and stopped there on one check: "on Hard the guard breaks after too few blocks (2)". The cause was in the stricter guard, not in the test: resetting it forgot the run of blows but not the time of the last blow, so the first blow after a reset counted as the second in a row (`d7eb54e`, one line). After that fix `Phase28GameTest` and `Phase29GameTest` ran again together, both green (11 min); the fresh guard now holds 6 / 4 / 3 blows on Leicht / Normal / Schwer (before: 6 / 3 / 2). The 45 classes before were not run again after this one-line fix (it touches only that reset, at the duel's start and end); CI runs the whole suite on the pull request.
- **All 13 parts of `Phase29GameTest` green** in that last run, measured:

| Part | Measured |
|---|---|
| `hover` | 23 things to use declared, all with their words; under the crosshair: "[Right Button] Take the screwdriver", "[Right Button] Examine the cover" |
| `patrolDoors` | Missions 2 and 5: the patrol walked the corridor from z −13.5 to 1.5, no side door opened |
| `fightDoors` | 2 doors by the arena, she came within 0.8 blocks of the wall: opened 0 times |
| cameras | every film: inside 0, blocked 0, in a body 0, jumps 0 frames; game camera inside a wall 0 ticks. Nearest the player's eyes: saber scene 2.36 (no tick in the eyes, the figure drawn), takedowns 1.61 to 2.09, Hale 2.07, maintenance way 3.60, boss scene 0.96, her defeat 0.66 (outside the body) |
| `defeatCamera` | her defeat by the arena's wall after a jump: the camera trailed its fitted place in 16 frames, up to 9.98 blocks behind, and caught up without a jump (it moves at most 0.8 blocks a tick); the player at most 7 ticks in the air |
| `rear` | 6 strokes from behind in a row: 1 landed, 4 turned aside, 5 turn-about blows; standing behind her she faced the player after 6 ticks |
| `guard` | held 40 ticks: stamina 94.4 → 72.4; gave way after 163 ticks; stamina came back 24 ticks after; blows in a row cost 16.2, 20.4, 24.6; the guard key pressed again at once is refused; two timed parries, the guard down 2 s between: both perfect, she open after each |
| `slam` | warned 23 ticks before it fell; on guard stamina 88.9 → 24.9; walked aside to 3.61 blocks, 40.9° off its way (inside its reach): missed |
| throw | low, standing: hit once; low, jumped over (feet 1.17 up as it passed): hit 0 times, on its way back 2.94 blocks above the feet |
| `pressure` | first phase 7 attacks in 15 s (longest calm 1.4 s: blades 5, lunge 2); second phase 14 in 30 s (longest calm 1.7 s: blades 3, lunge 2, push 2, rush 2, slam 3, throw 2) |
| `sceneInAir` | the scene with the player in the air: it waited, began on the floor, set down once, 0.00 above the floor |
| `sceneSides` | from her side, behind her, by her wall: 1.45 apart, both facing each other, camera clean |

- **Not tested** (not possible here): playing it by hand; a real graphics card, Iris shaders; a dedicated server with two players in the boss fight; other window sizes than the test's. The saber has no block on the use key in this game version, so the stricter rules for a use-key block are written but could not be tried.
- **Known:** in her defeat the film's camera trails its planned place for a moment when the player was in the air as she fell (it follows smoothly, never through a wall); that film's 3 s look from outside at the fleeing TIE is shot far from the rooms as before, not by the camera system (the test sees those 60 ticks as "at the eyes"; nothing stands between there).

## 8. CI

The test step's limit is raised from 95 to **115 minutes**, the job's from 110 to **130 minutes** (Phase 28's run needed 79 of 95; Phase 29 adds a test class). `docs/ci.md` updated.

## 9. Manuelle Testliste (für Phiro)

Was die automatischen Tests nicht ersetzen können, im echten Spiel mit der JAR aus diesem PR. Am einfachsten über **Story → Kapitel 1** und die Checkpoints; in Klammern, worauf zu achten ist.

**Hover-Texte**

1. Mission 2: Fadenkreuz auf die Abdeckung in der Lüftung und auf den Werkzeugkasten bei den Containern: dort steht „[Rechte Maustaste] Abdeckung untersuchen“ bzw. „… Schraubendreher nehmen“, nie ein Schlüssel wie `sabercraft.story.point…`. Auch andere Dinge zum Benutzen (Terminals, Luken, Konsolen) kurz ansehen.

**Türen**

2. Mission 2 und 5: im Korridor mit der Trooper-Streife warten, bis sie an den Seitentüren vorbeigeht: die Türen bleiben zu. Geht ein Trooper wirklich durch eine Tür, geht sie auf und erst hinter ihm wieder zu.
3. Bosskampf direkt neben den Türen am Hangar: sie gehen nicht ständig auf und zu. Läuft man selbst auf eine Tür zu, öffnet sie sich trotzdem sofort.

**Filmszenen und Kamera**

4. Wachmann im Arrestblock überwältigen, auch mit dem Rücken zur Zellenwand und aus verschiedenen Richtungen: die Kamera bleibt im Raum, man sieht nie durch eine Wand nach draußen.
5. Labor: das Lichtschwert aus der Halterung nehmen: die Kamera geht nie in den eigenen Kopf, die eigene Figur ist die ganze Szene über zu sehen; am Ende über einen kurzen Schwarzblick zurück in die eigene Sicht.
6. Hale-Szene in der Waffenkammer und die Szene an der Wartungstür: keine Sprünge, nichts durch Wände.
7. Boss bei halber Kraft (Klingen-Patt) aus verschiedenen Lagen auslösen: neben ihr, hinter ihr, an der Wand, und einmal mitten im Sprung: die Szene beginnt erst, wenn man steht (nie in der Luft eingefroren), die Kamera bleibt im Raum.
8. Ihre Niederlage am Rand der Arena (nahe der Wand) und ihre Flucht zum TIE: Kamera nie hinter der Wand, keine harten Sprünge innerhalb einer Einstellung; wenn etwas dazwischenkommt, gleitet die Kamera.

**Bosskampf**

9. Hinter sie springen und mehrmals zuschlagen: der erste Schlag trifft, danach wehrt sie die Schläge ab (Funken, Klirren), wirbelt zu einem herum und schlägt zurück; sie dreht sich natürlich, nicht ruckartig. Ganz nah an ihr: sie stößt einen mit der Macht weg.
10. Deckung dauerhaft halten: der Ausdauerbalken sinkt schon beim Halten, Schläge hintereinander kosten immer mehr, die Ausdauer kommt erst nach einer Weile zurück; ist die Deckung gebrochen, lässt sie sich kurz nicht heben. Rechtzeitig gehobene Deckung (perfekte Parade) öffnet sie weiterhin jedes Mal, solange die Deckung nicht wackelt und man nicht erschöpft ist. Nach 5 s Halten senkt sich die Deckung wie im freien Spiel von selbst (Taste loslassen und neu drücken). Auf Leicht / Normal / Schwer vergleichen.
11. Ihr Schmetterschlag (Klinge hoch über dem Kopf, der Boden vor ihr glüht): sie zielt nur am Anfang, dann bleibt der glühende Bereich stehen; seitlich aus ihm herausgehen reicht; stehen bleiben tut weh; Deckung hält, kostet aber viel.
12. Ihr Wurf: mal hoch, mal flach. Den flachen Wurf überspringen: er verfehlt; auf dem Rückweg zieht sie den Stab im hohen Bogen über den Kopf zurück, nicht von hinten in die Beine. Stehen bleiben: ein Treffer, kein zweiter von hinten.
13. Insgesamt: sie greift häufiger und abwechslungsreicher an (Ausfallschritt, Sprung, Stoß, Schmetterschlag, Wurf, Kombos), bleibt aber fair: jeder Angriff ist vorher zu sehen oder zu hören.

**Allgemein**

14. Alter Spielstand aus Phase 28 weiterspielen (lädt, Checkpoints wie vorher).
15. Keine Debug-Texte im Spiel, kein Hängenbleiben in einer Szene.
