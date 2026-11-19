# Phase 27: a story that explains itself — checkpoints, difficulty, crawl ways, the lab's guard and the way back in Mission 2

Built on `main` after Phase 26 (PR #31). No release, nothing merged. As before, the server decides everything from vanilla blocks and entities: **no new blocks, items or entity types are registered**. The focus of this phase is Phiro's playtest: the story should be understandable, fair and reliable, without guessing the right key or the right container.

**Saves.** Old stories load. The save gets one new field, the story's **difficulty** (`StoryProgress.difficulty`); a save from before Phase 27 has none and plays on **Normal**. Each player has their own save, progress and difficulty, as before. The new marks that live only during a scene start with `tmp.` and are never saved.

**The Star Destroyer's interior plan is now version 13** (a new maintenance hatch and a short crawl way in the hangar's west wall, section 6). As with every earlier version, a world saved before is rebuilt to it the next time nobody is aboard; the story's progress is not touched by that.

## 1. Checkpoints and where one comes back

An audit of every checkpoint and every place the story puts the player back. What was wrong and what changed:

- **Checkpoints in crawl ways** were made at the very spot the player was, also in a hatch's opening or right beside it. A hatch is shut again when a checkpoint is restored, so one could come back inside a closed hatch or with a wall in one's face. Now a checkpoint in a crawl way is only made **well inside the way** (at least 2.5 blocks from every hatch, in the middle of the way across, on its floor). In the hideout, the hideout's own safe place is used.
- **Kept places are checked on loading.** A place kept with a checkpoint is used again only if it is the step's safe place, the hideout's, the second cell's (after the trap) or a place well inside a crawl way. Anything else (a save from before Phase 27 with a spot in a hatch, on the cot, or a safe place that has since moved) gives way to the step's safe place. If there is no room at all (not even on all fours), the step's safe place is used too.
- **Respawn by the cell console** (Mission 3/5): the guard at his post by the door looked straight at it and caught the player within moments. The safe place moved **behind the pillar on the detention block's west side**, the block's own respawn likewise.
- **The airlock's checkpoint** (Mission 1) was within reach of the inner door, which opened at once. It is now a little farther away.
- **Bearings after a checkpoint.** While the player stays near where they were put (3 blocks) and does nothing, nobody sees them for up to 20 seconds. Moving off, using something, a takedown or a fight ends it at once. Steps that begin in a staged chase or fight keep only the short grace as before.
- **Search patrols left from an alarm** are not placed where their route passes within a few blocks of the checkpoint (they walked to the spot again and again and saw the player at once).
- **A checkpoint with the data stick** in Mission 2 (section 6).
- **A server stopped with players still in the story** keeps how far they got (as leaving does: only when all is calm, at the step's safe place).

## 2. Officers popping in at the prison door

The detention block's people only appeared when its door opened, on the threshold, while the step went on as the player stepped in. Now they are **there before the door opens**; on the way there they wait (nobody sees through the closed door yet, nobody talks). The troopers who come after the boss's flight now **run in through the hangar's door** from the corridor behind it instead of standing in the hangar from nothing. Kestrel waits in her hideout in **Mission 1 too** (one crawling in found her appearing as they came out of the way).

## 3. Ship damage from hard landings

A hard landing often cost only half a heart: the damage was measured from the bit of the last step that did not fit any more, which could be anything between nothing and the real speed. Now the **speed the ship came in with** counts:

- along each blocked direction, the speed it was flying with against the obstacle, at most as fast as it really moved that way the tick before;
- a ship **resting on the ground**, pushed down by the pilot, or skimming along a wall hits nothing (it was not moving into it);
- sideways, the speed **into** the wall counts (85 %), not along it: glancing touches are mild, a straight hit is hard;
- the damage curve is a little steeper (exponent 1.2), so a hard landing is felt. Below the safe speed nothing happens, as before; the wreck speed is unchanged.

Measured in the test (X-Wing, safe up to 0.5 blocks/tick, wreck at 1.75): see the results in section 13.

## 4. Dax's objective after the hyperspace film

After the hyperspace film the objective stayed "talk to Dax" and nothing went on. The cause: a dialog was still open on the server when the film began; the film's screen took its place on the client, nobody could answer it any more, and the story waited for it forever. Now:

- a film that begins **ends an open dialog first**;
- a dialog the server still has open when the player's screen is gone (they could not click) **comes back** after the film, instead of nothing happening when one uses or talks;
- the dialog screen closes by itself after two seconds if the server's answer is lost, instead of holding the player until Escape.

## 5. The marker in the crawl ways

The marker stayed on the hatch behind the player, in the shaft. Now it follows where the player is: from the first crawl way on it leads **to Kestrel's hideout**, past the north hatch on to the containers, and it follows a guard who moves. It is checked a few times a second and sent again only when it moved. On the way back in Mission 2: to flight control's door, there to the crawl way's hatch, in the crawl way on to the hideout (it pointed at Kestrel through the walls before).

## 6. Mission 2: the way back from the data section

Phiro: the way back was too hard and the N-lure of the flight control officer unreliable. Changed:

- **The officer walks a round** between the consoles (north–south, z −55 to −43, slow), instead of standing still facing the door and the hatch. Going north, the door and the crawl way's hatch are behind him. One can watch his round and pass when he walks away.
- **He hears M-31 further** (10 blocks instead of 7.5), so a lure from the door reaches him.
- **A lure that works.** A guard who hears M-31 goes **right up to the spot** (a step short of the noise), looks round there for 5 seconds (his head sweeping towards the noise, not back towards his post), then **searches on past it**, away from where he stood, for another 4 seconds, and only then goes back. In the test, the lured officer stayed on the far side of the room for the time logged in section 13. Seated crew only look; they do not get up.
- **A second noise at once is not believed** (he only looks); after a minute he believes one again. Before, every noise drew him, so a lure could also pull him back to the door.
- **A checkpoint with the stick**, in the hangar's south-west corner beside the containers, out of the patrols' way. Before, being caught on the way back meant finding the right container all over again.
- **An alternative route (checked and added):** a second maintenance hatch **low in the hangar's west wall, beside the crate stack**, with a short crawl way into the existing one to the hideout. It avoids flight control altogether, but it lies in the hangar, so one still has to slip past the patrols. Kestrel mentions it over the comm once the stick is taken. Both ways stay stealth: there is no way past without being unseen.
- **The marker** leads the way back (section 5).

## 7. Crawling faster

Crawling in a ship's crawl way is **40 % faster** (crouch speed 0.3 → 0.42 while crawling in a ship's interior, out of water). Standing again, everything is as before. Measured: 3.6 blocks in 2 seconds.

## 8. Dialogs and menus close when the player has to act

Seen, the alarm, hit or caught: whatever screen of the game's own is open **closes** (a dialog, the SaberCraft menu, the inventory), so the player can act at once. A **hit** only ends a dialog; menus stay, so the story's menu can still be used in a chase. The pause menu, the chat and a film are left alone. A dialog with someone who runs to the wall comm or after the player does not hold up the call any more. In the boss fight, her last words stay open while the duel begins (as before).

## 9. Story brightness

A new setting in the story's menu: **Story-Helligkeit** (Normal / Heller / Hell). It brightens the story's own darkening (the emergency lights, the alarm's pulsing, the lights failing as the ship shudders); the colours stay (red stays red), only less dark. **Default: Normal**, as made. It is saved in the immersion settings and kept between games.

## 10. The menu key

The SaberCraft menu is now on **I** (it was **V**, which Simple Voice Chat uses for its own menu). I is not used by vanilla Minecraft 26.2.

- A menu key still on V (the old default, kept in the game's options) **moves to I once**, and the player is told so in a toast. A key the player chose themselves (anything but V) stays.
- At the first start in a world, a toast shows **how to open the menu** with the key as it is bound now (10 seconds, once).
- Every text that named V now shows the real key as bound (the story's end, "locked during a mission", the chapter's hint, the convert command, the menu's own line "Menü: [I]").
- The key stays freely configurable under Options → Controls → Key Binds → SaberCraft.

## 11. Crawl ways: recognisable, one click, real keys

- **Usable hatches are marked** as maintenance access, as on any ship: a **yellow and black frame**, a **pull handle** on the grille and a small **amber lamp** above it (green while it stands open). The walls' own vent pattern has none of that.
- **One right-click** on a shut hatch opens it and gets the player down on all fours in front of it for a few seconds: one simply walks in. Walking up to an open hatch facing it does the same. Holding the crouch key still works.
- **Hints with the keys as bound:** looking at a hatch the first time, how one gets in; the first time on all fours in a crawl way, how to go on and shut the hatch behind. Later, looking at a shut hatch a long while without using it, a short reminder in the action bar (at most every three minutes).

## 12. Story difficulty

Before the first start of a story, the menu asks for the difficulty: **Leicht, Normal, Schwer**, each with a short description. It can be changed at any time in the story's menu (the button beside the journal). It is kept **per player** with their save, so it is the same after a capture, a relog or the next mission. In a running mission the change takes effect at once from the last checkpoint (like being caught, without counting as caught), so no enemy appears or vanishes in the middle of a scene. It changes how the enemies behave, not only how hard they hit:

| | Leicht | Normal | Schwer |
|---|---|---|---|
| Enemies | fewer (the hangar's second patrol trooper and two of the finale's fighters stay away) | as made | more (a second patrol on each hangar route half a round behind, extra fighters in the finale where there is room) |
| Sight | 0.8× | 1× | 1.2× |
| Noticing | 0.65× | 1× | 1.4× |
| Walking/running | 0.9× | 1× | 1.12× |
| Search time | 0.7× | 1× | 1.35× |
| Aim time, time between shots, scatter | 1.35× | 1× | 0.75× |
| Stun bolts until caught | 4 | 3 | 2 |
| Help at a call (at once / at most) | 1 / 3 | 2 / 4 | 3 / 6 |
| Boss strength / tempo / openings / damage | 0.8 / 0.8 / 1.4 / 0.75 | 1 / 1 / 1 / 1 | 1.3 / 1.25 / 0.7 / 1.3 |

The stun marks under the crosshair show as many as the difficulty allows. Measured in the hangar mission: 9 enemies on Easy, 10 on Normal, 13 on Hard.

## 13. The boss: harder

On Normal she is now **15 % stronger** (her strokes take less of her strength), she presses on sooner in the first phase (pause 28 instead of 34 ticks), and the first phase has longer, mixed combos. The **ring's rush** comes in the first phase too, rarer (every 15 s at most) and only once she has lost some strength (below 85 %); her **push** likewise (every 17 s at most). The difficulty scales all of it further (section 12). Her openings never get shorter than a stroke needs.

## 14. The Millennium Falcon after the boss

The Falcon was there from the start of the escape, and her TIE flew through it. Now it comes **only after the duel**: once the boss has flown off through the hangar's field and her film is over. On the client it then **rises out of the dark below** to its place over a few seconds (nose high, levelling out, engines bright while it climbs), never popping up. Loaded at the escape after that, it simply waits in its place.

## 15. The lab's guard

Phiro: the guard stood like a statue right of the entrance; he should have full enemy AI and stand a little further left beside the door, so the player has to notice him, distract him or get round him.

- **New post:** west of the way to the lab's door, where one comes along the platform. Seen from the platform facing the door that is **left** of the way (seen from the lab looking out, right). Phase 26's post east of the way had the only way there and the door always behind him: he never saw anyone. The screenshots `phase27_lab_guard_facing_door` and `phase27_lab_guard_from_door` show both views. If "left" was meant the other way, it is one constant (`Chapter1.LAB_GUARD`).
- **His watch:** in turn, along the platform to the west (who comes over the bridges?) for 4 seconds, over the shaft (2 s), **to the east bridge for 7 seconds**, over the shaft again (2 s), with smooth turns between (a round of about 20 seconds); his head moves within each look. While he looks east, one gets by behind him.
- **Full enemy AI:** what he sees and hears he reacts to as anyone: he goes to look, raises the alarm, fires. As a **sentry** he has his rifle ready: seeing someone close, he **fires first** instead of running at them, and again at most every 3 seconds while he sees them.
- **Distraction:** he hears M-31 a little further than most (9 blocks), goes to the spot, looks there, searches on past it (onto the west bridge in the test), then comes back to his post and his round of looks.
- **Takedown from behind** works as before; now the player is never put into the console behind him (if there is no room right behind him, on the side they came from).
- He is **not removed**; once down he stays down (as in Phase 26).

## 16. The bridge

The story has nothing on the bridge, and players got lost up there. Through the **whole story** now (not only the finale), a **security barrier** stands across the turbolift's bay in the lobby: two pylons with hazard bands and red lamps blinking in turn, a red force field between them that collides like a wall. In front of it, now and then, a short line says why it stands there. The lift refuses to go up as before. Out of the story, the bridge is open as always.

## 17. Tests

Automated tests run in the cloud container with a real client (Xvfb, software rendering), **not in CI**. New: **`Phase27GameTest`**, one part per point of Phiro's list (14 parts; `PHASE27_ONLY` runs single parts). A green automated test shows that the logic works; it does **not** replace playing it.

| # | Point | Automated | What is checked | Still by hand |
|---|---|---|---|---|
| 1 | Checkpoints and respawns | yes, `Phase27GameTest.checkpoints`, `oldSave`, `consoleSafe` | no checkpoint in or by a hatch; one deep in a crawl way, back there on all fours with room; a save from before with a spot in a hatch is put at the step's safe place; nobody sees the player in the bearings; the cell console's respawn: suspicion stays 0 | playing through and being caught in many places |
| 2 | Officers at the prison door | yes, `popin` | the block's people are there before the door opens | how it looks |
| 3 | Hard landings | yes, `shipDamage` (real X-Wing, dropped at three speeds) | the impact measured is the landing speed; hull lost grows with speed; resting on the ground costs nothing | flying and landing by hand |
| 4 | Dax after the film | yes, `interrupt` | a dialog open when a film begins is ended; a hidden dialog comes back | the hyperspace film by hand |
| 5 | Marker to Kestrel | yes, `ventMarker`, `mission2` | in the crawl way the marker points to the hideout's mouth; on the way back to the door, the hatch, the hideout | – |
| 6 | Mission 2 way back | yes, `mission2` | the officer's round (more than 6 blocks, on its line); he hears 10 blocks; the lure: he goes, the seated crew stay; he gets far enough from the door and stays away long enough; a second noise at once only makes him look, a minute later he goes again; the checkpoint with the stick (with room, keeps the stick); the second hatch in the hangar opens with one click and leads into the crawl way | **yes**: whether it feels fair |
| 7 | Crawling faster | yes, `mission2` | the crawl bonus is on in the crawl way; distance per second | – |
| 8 | Screens close when seen | yes, `interrupt` | seen: the dialog ends on the server and its screen closes on the client | menus and inventory by hand |
| 9 | Brightness | yes, `brightness` | default Normal; kept after save and load; the brightest setting lifts enough | **yes**: how it looks |
| 10 | Menu key | yes, `keys` | default I, not V; a key on V moves to I; the move happens only once (a player who sets V again keeps it); the hint names the key as bound | the toasts; with Simple Voice Chat installed |
| 11 | Crawl ways | yes, `mission2` | one click on a hatch opens it and is taken as wanting in; walking into an open one facing it gets the player crawling | **yes**: whether the hatches are recognisable, the hints |
| 12 | Difficulty | yes, `difficulty`, `StoryGameTest` | a new story asks for it first (StoryGameTest picks Normal on that page); a change in a running mission goes back to the checkpoint exactly once; kept in the player's save after leaving and continuing; enemies in the hangar mission 9/10/13 | **yes**: playing on all three, the stun marks, two players with different difficulties |
| 13 | Boss harder | partly, `Phase26GameTest.boss`, `StoryGameTest` | the duel still runs through, she still loses strength by hits | **yes**: the fight on all three difficulties |
| 14 | Falcon after the boss | yes, `falcon`, `Phase26GameTest.falcon` | not there in the escape; there after the duel and her film; loaded at the escape, it does not rise again | **yes**: the rise |
| 15 | Lab's guard | yes, `labGuard`, `Phase25GameTest.labGuard` | his post; the view from the door and the west entry while he looks east (unseen) and west (seen); he detects the player coming along the platform and fires; M-31's noise draws him off his post and he searches on past it; he comes back and keeps watch; takedown from behind without the player put into the console | **yes**: sneaking past him by hand |
| 16 | Bridge barrier | yes, `bridge`, `Phase26GameTest.lift` | the barrier stands and collides; the lift refuses in mission 4 | how it looks |
| 17 | QA | the suite, see below | – | – |

**Results (2026-11-19, local, not CI):** _filled in after the full run._

**Found and fixed by the tests in this phase:**

1. **New fields were lost for numbered figures** (the lab guard's sentry and the officer's hearing): the story copies each figure from its template, and the copy did not take the new fields. Fixed; the test checks both.
2. **A lured guard stopped short twice:** the lure's spot was already a step short of the noise, and he then stopped another step short of that. The lab's guard moved only 2.1 blocks. Now a lured guard goes right up to the spot (2.8 blocks there, then 6.8 blocks while searching on).
3. **StoryGameTest clicked "Start"**, which now asks for the difficulty first; the test picks Normal.

**Not tested automatically:** how everything plays by hand (the lure's timing, sneaking past the lab's guard and the officer, the boss on all three difficulties, how the brightness looks), the films, music and sounds, Simple Voice Chat together with SaberCraft, Iris and shader packs, a second real player.

**Known issues and notes:**

- **Other keys and Simple Voice Chat:** SaberCraft also uses **N** (droid ability), **H** (force grip) and **G** (story focus). Simple Voice Chat's defaults for its other keys could not be checked from here (its pages could not be reached); if one overlaps, it can be changed in the key settings. **G** also overlaps with vanilla 26.2's *quick actions* key; that was so since Phase 21 and is unchanged in this phase.
- **The lab guard's side** follows the reading above (left when facing the door from the platform).
- **The alternative hatch in the hangar** needs the interior rebuilt to version 13 (when nobody is aboard).
- `SkinLayerGameTest` depends on the order of the test classes (as before); the committed order is unchanged and `Phase27GameTest` comes last.

## 18. CI

Unchanged from Phase 25 (test step 95 minutes, job 110 minutes, `docs/ci.md`). `Phase27GameTest` takes about four minutes locally.

## 19. Manuelle Testliste (für Phiro)

Was die automatischen Tests nicht ersetzen können, im echten Spiel mit der JAR aus diesem PR. Am einfachsten über **Story → Kapitel 1** und die Checkpoints; in Klammern, worauf zu achten ist.

**Start und Einstellungen**

1. Neue Welt oder neues Profil: Hinweis „SaberCraft-Menü öffnen: [I]“ erscheint einmal; **I** öffnet das Menü. Mit einem alten Profil, in dem das Menü auf V lag: einmalig auf I verlegt, mit Hinweis. Mit Simple Voice Chat zusammen: V öffnet nur noch dessen Menü.
2. Story neu starten: zuerst die Schwierigkeitsauswahl (Leicht/Normal/Schwer mit Erklärung), danach beginnt die Geschichte.
3. Schwierigkeit während einer Mission im Story-Menü umstellen: beim Schließen ein Hinweis, weiter ab dem letzten Checkpoint, ohne Gefangennahme.
4. Story-Helligkeit Normal/Heller/Hell in dunklen Szenen (Notbeleuchtung, Alarm, Reaktor) vergleichen (Farben bleiben, nur weniger dunkel; bleibt nach Neustart erhalten).

**Mission 1 bis 3**

5. Erste Lüftung: Luken mit gelb-schwarzem Rahmen und Lämpchen erkennen; einmal Rechtsklick, dann hineingehen (ohne Schleichtaste); Hinweise zeigen die eigenen Tasten.
6. Kriechen fühlt sich schneller an, aber nicht zu schnell.
7. Wegpunkt ab der ersten Lüftung bis zu Kestrels Versteck (zeigt nicht mehr auf die Luke hinter dir).
8. Nach dem Hyperraum-Film: Gespräch mit Dax geht weiter, das Ziel bleibt nicht hängen.
9. Mission 2, Rückweg aus der Datensektion: Checkpoint am Datenstick (bei den Containern); Offizier in der Flugleitung läuft seine Runde; mit M-31 (N) ablenken: er geht weit genug weg und bleibt lange genug; es bleibt Schleichen. Danach die Ausweichluke in der Westwand des Hangars neben dem Kistenstapel ausprobieren (Kestrel erwähnt sie per Funk).
10. Gefängnistür: die Offiziere sind schon da, bevor die Tür aufgeht (kein Aufploppen).
11. Respawn an der Zellenkonsole und im Zellenblock (hinter der Säule): nicht sofort entdeckt.

**Allgemein in der Story**

12. Entdeckt werden, Alarm, Treffer, Gefangennahme: offene Dialoge, Menüs und das Inventar schließen sich sofort.
13. An mehreren Stellen gefangen werden, auch in Lüftungen: Rückkehr an einen sinnvollen Ort, nie in einer Luke oder Wand, kurz unentdeckt zum Orientieren.
14. Lift zur Brücke: in jeder Mission eine sichtbare Sicherheitssperre davor (rote Barriere), Meldung beim Näherkommen.

**Mission 5 und 6**

15. Laborwache: Posten links neben dem Weg zur Labortür (von der Plattform aus gesehen); sie schaut abwechselnd nach Westen, über den Schacht und lange zur Ostbrücke. Bemerken, ablenken (M-31), umgehen oder von hinten ausschalten. Sie schießt, wenn sie dich sieht.
16. Bosskampf auf allen drei Schwierigkeiten (Normal spürbar härter als Phase 26; Ring-Ansturm schon in Phase 1, aber selten).
17. Nach dem Boss und ihrem Film: die Falcon steigt aus der Tiefe unter dem Hangar auf, nicht vorher, ohne Aufploppen.

**Schiffe (außerhalb der Story)**

18. Mit einem Schiff unterschiedlich hart landen: sanft kostet nichts, hart deutlich mehr, je schneller desto mehr; auf dem Boden stehen und nach unten drücken kostet nichts.

**Allgemein**

19. Alter Spielstand aus Phase 26 weiterspielen (lädt, Schwierigkeit Normal, das Innere wird auf Version 13 umgebaut, wenn niemand an Bord ist).
20. Mehrspieler: jeder Spieler hat seine eigene Schwierigkeit und seinen eigenen Fortschritt.
