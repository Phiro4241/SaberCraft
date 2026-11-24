# Phase 30: six fixes from Phiro's playtest (spin, her saber, troopers, reactor entrance, lift, maintenance door)

Built on `main` after Phase 29 (PR #34, merge commit `87ef232`). No release, nothing merged. Phiro asked for exactly six changes; everything else stays as it was. As before, the server decides everything from vanilla blocks and entities: **no new blocks, items or entity types are registered.** One new network message (`LiftPayload`, server to client) carries the lift's ride to the HUD. One small client mixin (`HudAccessor`, an accessor) reads how long Minecraft's line over the hotbar still shows, so the new boxes on the screen keep clear of it.

**Saves.** Old stories load as before; the save gets no new field. **The Star Destroyer's interior plan stays at version 13** (no block of the ship changed: the reactor fix is drawn by the client).

## 1. Her spin in the second phase: hit at once inside the red circle

Before: the whole spin was a wind-up of 20 ticks and one blow at its end. Her blades' light lay on the floor up to 5.1 blocks round her while the ring spun up, so one stood in a "red circle" for a second and more before anything happened, and the circle one saw was not the zone that hit.

Now (`Finale`, `Figure`, `StoryModels`):

- **Two parts.** First the ring spins up round the grip (8 ticks, 0.4 s): its whirr sounds at once, she raises it, and it turns faster and faster. When the circle appears it is spinning fast (about four fifths of full speed if it was turning already, a little over half if it was still), and at full speed a few ticks later. Nothing lies on the floor yet. This is the warning. Then the blades whirl round her for 16 ticks (0.8 s) and **the red circle lies on the floor**.
- **The circle is the hit zone:** both use the same number, 3.5 blocks round her (`StoryFiguresPayload.SPIN_RADIUS`). Whoever has the middle of their body inside the drawn rim counts (within 2 blocks up or down). The client draws the circle only while the server says the spin is live (a flag in the figures' message).
- **Hit at once:** whoever is in the circle while it is live is hit in the same tick. That includes one already standing in it when it begins, and one stepping into it while it whirls. Staying in it hits again every 10 of her ticks, weaker (60 %). Her ticks, not the world's, so the player's Focus (slow motion) does not make it hit more often. The hit throws the player out towards the rim.
- **Defence stays as before:**
  - A guard held facing her blocks it. It costs stamina and may break.
  - A guard raised just in time parries it and she reels.
  - The dash dodges it. A dash begun in the circle, or under way as it appears, dodges the blades when they reach the player (at once, or 10 of her ticks after they last did), counted once: the swish, the "dodged" counter. If the spin was the last blow of her combo and she has not hurt the player, she is open when it ends; otherwise her next blow follows, as after any dodged blow. If the player stays in the circle after the dash, it reaches them again 10 of her ticks later, as a first blow (full strength, and a guard raised just in time parries it perfectly).
  - A dash rolled into the circle from outside only passes through the blades: the player counts as outside until the dash is over. Then the circle hits as it hits anyone in it, and it earns no opening.
  - A spin that misses entirely keeps her count of blows parried before it, as a missed blow always did.
- **The floor light of her blades** now always reaches as far as it did with one blade and the ring still (3.2 blocks, fading to nothing). It no longer grows with both blades or the spinning ring (it reached 5.1), so it is not mistaken for the circle.

## 2. Her double-bladed saber no longer cuts into her body

Phiro saw a blade in her head or belly when she held the saber at a slant. The check that keeps the saber out of her body now uses her body exactly as it is drawn (`CrewModels.inDrawnBody`, `CrewModels.Lean`, `InquisitorSaber`):

- **The head** is the drawn cube, turned exactly as it is drawn for where she looks (the measurement below tries turns from 75 degrees left to 75 right). Before, it was a box only as deep as the torso.
- **The robe** over her legs is checked with its drawn size and the blade's margin, in the hips' space. Before, one box (the torso and a little more, from her feet to the top of her head) stood for the legs too: it left no room for a blade's glowing edge over the robe, and it leaned with the torso.
- **Leaning:** when she is thrown back, crouches or staggers, her torso leans over her hips while the robe and legs stay below. The point is now turned into the hips' space before the robe is checked, exactly as the model is drawn.
- **Every part of the saber** is checked: the grip, the ring all round, both emitters, the first blade, and the second blade only while it is lit. The spinning ring is checked as a disc at 96 angles. Her torso's top edge at the shoulders counts too (the test in the game found 20 poses where a blade grazed it; fixed).
- **How it is moved out:**
  - Where a part would be inside her, the saber is moved away from her, as before, and her hands follow its grip.
  - It moves out quickly and comes back slowly, so it does not jump from frame to frame.
  - It is never closer than the check allows: at the ring's angle always, and for the whole disc once the spinning ring's blur reaches a tenth of its strength (before that, it moves out towards the disc's distance quickly, 4 blocks a second).
  - The three-part check the Phase 25 staff uses is unchanged.
- **Two small changes to her poses were needed. Everything else in her animations is unchanged:**
  - While the ring spins fast, the grip is held level and slightly higher, so the disc of blades stands upright before her. Tilted back with the grip, its upper half swept through her head. Keeping it out that way would have held the saber so far off that her hands no longer reached the grip. When she holds the saber high (an overhead blow's wind-up), the grip is not levelled, because the disc is clear of her head there anyway.
  - Drawn back to throw, the ring is held a little higher above her head, not behind it.
- **Measured** with `MeshAudit.ringInDrawnBody` on the real compiled model: 2,208,000 poses (every blow, reaction, leap, stagger, the spin, the throw, the ring still and spinning, the second blade coming out, 12 angles of the ring, the head turned every way, the torso's sway for different figures and moments). For comparison, Phase 29's holding and clearing code was run on the same poses outside the game:

  | | Phase 29 | Phase 30 |
  |---|---|---|
  | Poses where the saber touches her drawn body | 870,153 | **0** |
  | First phase (one blade, ring still): a hand more than 0.1 blocks short of the grip | 59,891 of 220,800 | 60,950 of 220,800 |
  | Second phase, ring still: a hand more than 0.1 blocks short of the grip | 22,426 of 220,800 | 25,440 of 220,800 |
  | Second phase, ring spinning: a hand more than 0.1 blocks short of the grip | 60,012 of 220,800 | 12,553 of 220,800 |

  Each pose is checked with one of seven head turns (from 75 degrees left to 75 right, in turn). With the ring still her hands fall short of the grip a little more often than in Phase 29, because the saber is now kept out of her body where it used to cut in. With the ring spinning they fall short far less often. The same check with all seven head turns for every pose (15.5 million poses, run outside the game) found no touching either.

## 3. Troopers in the finale fire a little more often

`Finale`: a trooper's time between shots is 18 + up to 17 ticks (18 to 35 on Normal; was 22 to 43), a second shot right after the first a little more often (35 %, was 30 %). For one trooper that is about a quarter more shots (about 24 %). With many troopers firing at once the 7-tick gap between two troopers' shots is the limit, as before, so there the rate stays as it was. **What keeps it fair stays:** the rifle comes up before each shot (visible), two troopers never fire within 7 ticks of each other (each bolt can be turned aside), the difficulty still scales it (Leicht fewer, Schwer more).

## 4. No blackstone under the metal platform at the reactor's entrance

At the gallery's entrance into the reactor, the gallery's floor runs out over the open shaft under the landing's metal platform. Those floor blocks (polished blackstone bricks) stand in front of the shaft's round wall. Their sides were skipped as if they were the round wall, which has its own model. So from the working level below one saw their bare blackstone texture under the platform.

- **Fixed in the drawing** (`DestroyerRooms.skin`): every side and underside of a floor block standing out into the shaft in front of its round wall is now steel, like the platforms' own steel. The same applies to the floor blocks of the way to the lab, where they reach under the south platform (seen only from below).
- **So it cannot come back:** a new check (`MeshAudit.bareInShaft`) goes through every side and underside of the ship's blocks in the shaft in front of the round wall, from the working level up (not the tops one walks on, not the doorways), and lists any face nothing is drawn over.

## 5. The reactor's lift: a visible ride

Before, the slow lift (1.5 s, Phase 28) said only "hold still" in the line over the hotbar. Now (`LiftHud`, `ShipInteriors`):

- **A panel on the HUD** while it goes (from its third tick on, so it does not flash up while one moves about on the lift), in the reactor's look: dark, with a cyan frame and amber lamps blinking. It shows:
  - "Wartungslift fährt nach unten / oben";
  - under it "Bleib stehen … noch 0,9 s" (with a comma in German, a point in English);
  - three chevrons running the way it goes, and a ten-segment bar filling up.

  On arrival the lamps and the bar turn green ("Wartungslift angekommen · Reaktorboden / Deckebene"), then it fades.
- **In the shaft itself:** while one rides, bands of light run along the lift's four posts the way it goes, over the posts' whole height, and the pad's ring pulses. Both grow faster as the ride goes on. The lift hums on while it moves.
- **Smooth:** the client counts the ride on with its own ticks and follows the server's word only when the two differ by more than a tick, so the bar, the seconds and the lights run evenly.
- **Only for the rider:** the server tells only the player on the lift. Other players see the lift as it always was.
- **Ending the ride:**
  - Stepping off stops it (as before) and the panel goes away.
  - The panel also goes away when the player respawns, changes world or disconnects, or when the server says nothing for 1.5 s.
- **Where it sits:** above the crosshair, clear of it. It also stays clear of the "Use: …" line and the stun marks under the crosshair, of a hint, of the line over the hotbar where a room's name shows (while it shows) and of a mission's title card. If the title card covers the place above the crosshair, the panel goes under the crosshair. If not all of them fit (a small screen), it gives up the line over the hotbar first (gone after 3 s), then the title card (it fades soon). A story progress bar at the same time (rare) keeps clear of the panel.

## 6. The maintenance door in the escape: one right-click, then it runs by itself

Before, the emergency release had to be pulled for 40 ticks (2 s) standing beside it; it stopped as soon as one stepped more than 1.9 blocks away.

Now (`Scenes`, `Chapter1`, `StoryHud`):

- **Showing where:**
  - The objective says "Alarm! Entriegle die Wartungstür am Ende des Stegs: Rechtsklick auf das Bedienfeld links neben der Tür".
  - The marker points at the panel, and the panel's screen pulses amber.
  - On the last stretch of the walkway (from about 7 blocks before the door) a hint says "Rechtsklick aufs Bedienfeld: Tür entriegeln". It shows only there, not from the platform across the gap.
- **One right-click** on the panel, or on the shut door itself, starts it. Then it **runs by itself** in 28 ticks (1.4 s, was 2 s). There is no holding, no looking at it and no clicking again; one may walk on or turn round.
- **While it runs:**
  - The hint changes to "Die Tür entriegelt sich von selbst – lauf weiter!".
  - A bar shows "Notentriegelung läuft von selbst … 57 %" (in steps of about 7 %). It also shows while the mission's title card is still on screen.
  - The objective says "Die Wartungstür wird entriegelt … gleich ist sie offen".
- **When it is done:** the door is unlocked and the panel turns green. The door opens like any door as the player comes to it, with no second click. The objective and the marker move on to the bolt panel inside. Once the door is bolted from inside, they lead into the crawl way.
- **Bolting only from inside:** the bolt panel can be reached through the open doorway. Bolted from outside, the door would shut the player out of the crawl way, their way out. Now a click from outside does not bolt it. A hint says "Erst durch die Tür: Verriegeln kannst du sie nur von innen." If the door is ever shut with the player outside all the same, the objective and the marker are the step's own again, as in Phase 29: "Alarm! Erreiche den Wartungsschacht", the marker at the hideout (the old way out, which still ends the escape).
- **No overlaps:** a running action's bar (the release, a download, a hack) keeps clear of the crosshair, the "Use: …" line, the hint over the hotbar, the line over the hotbar (while it shows) and a mission's title card. It keeps clear of the line over the hotbar's words (and of the text background behind them only when Minecraft's settings draw one), so on a screen 240 high it stays where it was under the crosshair. If not all of them fit on a small screen, it gives up the line over the hotbar first (gone after 3 s), then the title card (it fades soon).
- **Troopers' shouts:** the troopers' shouts still waiting in the escape are no longer dropped each time the objective's wording changes (release, unlocking, bolt, shaft). As before, they are dropped only when the mission or the step changes, or the alarm ends.
- The rest of the escape is unchanged: troopers, the reinforcements, bolting, the crawl way.

## 7. Tests

New: `Phase30GameTest` (in the real game with a client); its parts can be run alone with `PHASE30_ONLY`:

| Section | Part | What it checks |
|---|---|---|
| 1 | `spin` | her spin with the player at 1.6, 2.6 and 3.2 blocks (in the circle): hit within one tick of the circle appearing; at 3.75 and 4.7 blocks (out of it): never; stepping in while it whirls: hit at once; a dash under way in the circle as it appears: dodged, counted once, no hit then; a dash rolled in from outside: not counted as dodged, hit once it is over (both with the server's side of a roll, `SaberAbilities.evadeForTests`); a held guard blocks it (no hurt); the client draws the circle only while it is live |
| 2 | `saberPoses` | every pose of her duel (blows, leaps, staggers, every reaction, the spin, the throw, the ring still and spinning, the second blade coming out), the head turned one of seven ways from 75 degrees left to 75 right in turn: 2,208,000 poses checked against her body as drawn (torso, head, robe and legs with the torso leaning); Phase 26's smaller set (746,496 poses) against the clearing's own margin; how often a hand falls short of the grip: with the ring still about Phase 29's level (at most 66,000 and 27,000 of 220,800), spinning at most half of it (30,000); pictures from the front in the second phase |
| 3 | `shots` | 20 s under fire in the reactor shaft during the finale's escape (`m6.escape1`): the timing (18 + up to 17), no two shots together, the gap between troopers kept |
| 4 | `landing` | the views from the working level up under the landing; no bare block face in the shaft |
| 5 | `lift` | down from the deck level: the panel shows the ride, its end and the arrival, the posts' light is drawn; the seconds written as the language writes them; moving on the lift holds it and the panel does not flash up; stepping off clears the panel; on a screen 240 high, with nothing else on it: a box that would cover the line over the hotbar moves for a room's name there, the lift's panel and the progress bar keep clear of it, and the progress bar (which does not reach it) does not move |
| 6 | `release` | the objective, the marker and the hint lead to the panel, the hint only on the walkway; one click (as the client sends it) while the mission's title card shows: the bar is drawn under it; the player walking away: the release finishes by itself in time (counted in server ticks from the click), counted once; the objective and marker move on to the bolt panel; the door then opens for the player coming to it (with no second click); a click on the bolt panel from outside does not bolt, one from inside does and the objective leads into the shaft; a click on the shut door starts it too |

Changed tests (only where Phase 30 changed what they measure):

- `Phase26GameTest`: its check of the troopers' timing allows the new, shorter time.
- `Phase25GameTest`, part throw: the spin now throws the player out towards the rim, so the spot it put the player at (6.5 blocks from her) could land over the hangar's force field and the player fell instead of being thrown at. The test now turns that spot round her until it is on the hangar floor and off the field; the throw itself is checked as before.

### Results (20.11.2026, in this cloud container: Xvfb, llvmpipe, no real GPU)

- **All 48 test classes** (the 47 before and `Phase30GameTest`) ran green in one run of 1 h 33 min. That run was at the code before the last small fixes that the review of this document found: the dash in the spin (counted when the blades reach the player; the hit after it counts as a first one), the line over the hotbar for the lift's panel and the progress bar, and three more checks in the test (the two dashes, the line over the hotbar, the door opening after the release).
- **After those fixes**, `Phase30GameTest` and the classes that test the finale, the duel and the escape (`Phase25GameTest`, `Phase26GameTest`, `Phase28GameTest`, `Phase29GameTest`) ran green again (22 min). The other 43 classes were not run again after them.
- What `Phase30GameTest` measured on the final code:
  - **Spin:** in the circle at 1.6, 2.6 and 3.2 blocks: hit within a tick of the circle appearing; at 3.75 and 4.7 blocks: never; stepped in while it whirled: hit at once; a dash under way as it appeared: dodged, counted once, reached again 9 ticks later; rolled in from outside: not counted as dodged, hit 7 ticks after the roll began (when it was over); guard held: blocked twice, hurt 0. The client drew the circle only while it was live (0 frames before).
  - **Her saber:** 0 of 2,208,000 poses touching her drawn body; 0 of 746,496 inside the clearing's margin; a hand more than 0.1 short of the grip in 60,950 / 25,440 / 12,553 of 220,800 poses (see the table in section 2).
  - **Troopers:** 51 bolts in 20 s, the least gap between two 7 ticks, a trooper's time between shots 18 to 35 ticks.
  - **Reactor entrance:** 0 bare block faces in the shaft.
  - **Lift:** the ride shown for 24 ticks, the arrival in green, nothing shown while moving about on it, gone after stepping off. On a screen 240 high with a room's name over the hotbar (from row 168): a box tried under the crosshair moved from 146 to 70, the progress bar stayed at 147 (clear of it), the lift's panel at 70.
  - **Release:** the door was released 28 server ticks after the one click with the player away from it, the bar was drawn under the mission's title card, the door then opened for the player coming to it, bolting from outside was refused and from inside worked.
- The CI run on GitHub starts with this pull request; its result is not part of this document.

**Not tested automatically:**

- playing by hand (how it feels);
- the dash against the spin as a player does it with the key (the test gives the server a roll's evading moment; the roll's movement on the client is not part of it), and the Focus during the spin (checked only by reading the code, and by the review);
- a real GPU with shaders;
- a dedicated server with several players (the lift panel for the rider only);
- window sizes other than the test's.

## 8. Manuelle Testliste (für Phiro)

Was die automatischen Tests nicht ersetzen können, im echten Spiel mit der JAR aus diesem PR. Am einfachsten über **Story → Kapitel 1** und die Checkpoints.

**1. Drehangriff (Bosskampf, zweite Phase)**

1. Abwarten, bis sie den Ring hochdreht (Surren, der Ring wird schneller, noch nichts auf dem Boden), dann erscheint der rote Kreis. In diesem Moment im Kreis stehen: Der Treffer kommt sofort, nicht erst nach einer Sekunde. Vorher liegt nur ihr gewohntes rotes Bodenlicht da; es ist in der zweiten Phase nicht größer als in der ersten, auch nicht, wenn sich der Ring dreht.
2. Schon im Kreis stehen, wenn er erscheint: sofort getroffen. Von außen hineinlaufen, während er sich dreht: sofort getroffen. Knapp außerhalb des roten Rands stehen: kein Treffer.
3. Deckung zu ihr gewandt halten: geblockt (Funken, Ausdauer sinkt), kein Schaden. Rechtzeitig gehoben: perfekte Parade, sie taumelt. Im Kreis stehend mit dem Ausweichsprung ausweichen, während er erscheint: kein Treffer, man hört das Zischen. War der Drehangriff der letzte Schlag ihrer Folge, ist sie danach offen; sonst folgt gleich ihr nächster Schlag. Nach dem Ausweichen im Kreis bleiben: Etwa eine halbe Sekunde später erreicht er einen wieder. Von außen mit dem Ausweichsprung in den Kreis hinein: Nach dem Sprung wird man getroffen.
4. Mit dem Fokus (Zeitlupe) im Kreis bleiben: Die Treffer kommen nicht öfter als ohne.

**2. Ihr Lichtschwert**

5. Den ganzen Kampf über (beide Phasen) darauf achten, ob eine Klinge oder der Ring in ihrem Kopf, Bauch oder in der Robe steckt: beim schrägen Halten, beim Zurückgeworfenwerden, beim Ducken vor dem Sprung, beim Drehen des Rings, beim Patt, beim Ausholen zum Wurf.
6. Die Bewegungen sollen aussehen wie vorher. Zwei kleine Änderungen sind Absicht: Dreht sich der Ring schnell, steht die Scheibe aus Klingen aufrecht vor ihr (außer wenn sie das Schwert hoch über dem Kopf hält). Beim Ausholen zum Wurf ist der Ring etwas höher über ihrem Kopf. Bitte melden, ob ihre Hände am Griff bleiben.

**3. Sturmtruppen im Finale**

7. Im Hangar unter Beschuss: Sie schießen spürbar etwas öfter als in Phase 29, aber nie mehrere Schüsse gleichzeitig. Jeder Schuss ist vorher am gehobenen Gewehr zu erkennen. Auf Leicht / Normal / Schwer vergleichen.

**4. Reaktoreingang**

8. Auf der unteren Etage (Arbeitsebene) unter die Metallplattform am Galerie-Eingang gehen und von mehreren Seiten nach oben schauen: keine Blackstone-Texturen mehr, nur Metall. Auch sonst im Schacht nach Minecraft-Blöcken schauen.

**5. Aufzug im Reaktor**

9. Auf den Aufzug stellen: Nach einem kurzen Augenblick erscheint das Panel über dem Fadenkreuz („Wartungslift fährt nach unten“, Sekunden, laufende Pfeile, Balken), und die Lichter an den vier Säulen laufen nach unten. Balken und Sekunden laufen gleichmäßig. Unten: grün „angekommen“, dann blendet es aus. Dasselbe nach oben.
10. Während der Fahrt umherlaufen oder heruntertreten: Der Aufzug wartet bzw. das Panel verschwindet. Beim Herumlaufen auf dem Aufzug blitzt das Panel nicht auf.

**6. Flucht durch die Wartungstür**

11. Mission 5, nach der Entdeckung im Reaktor: Ziel und Markierung zeigen auf das Bedienfeld links neben der Wartungstür. Auf dem Steg erscheint der Hinweis „Rechtsklick aufs Bedienfeld: Tür entriegeln“.
12. Einmal rechtsklicken und sofort weggehen oder sich umdrehen: Der Balken „Notentriegelung läuft von selbst …“ läuft in etwa 1,5 s durch, das Bedienfeld wird grün, und die Tür ist entriegelt, ohne weiteren Klick. Zur Tür gehen: Sie öffnet sich wie jede Tür. Danach zeigt das Ziel auf das Bedienfeld innen zum Verriegeln, nach dem Verriegeln in den Schacht.
13. Auch gleich zu Beginn der Mission probieren, solange der Missionstitel noch zu sehen ist: Der Balken ist trotzdem sichtbar.
14. Von außen durch die offene Tür auf das Verriegelungs-Bedienfeld klicken: Die Tür verriegelt nicht, ein Hinweis sagt, dass es nur von innen geht. Von innen verriegelt sie.

**Allgemein**

15. Alles andere wie in Phase 29 (außer dem oben Genannten): der übrige Bosskampf, die Filme, die übrige Flucht. Bitte melden, wenn sich etwas anders anfühlt.
