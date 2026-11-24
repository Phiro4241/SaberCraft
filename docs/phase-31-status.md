# Phase 31: the last fixes before the final test

Built on `main` after Phase 30 (PR #35, merge commit `c5b1fe5`). No release, nothing merged. Phiro asked for eight fixes; everything else stays as it was. As before, the server decides everything from vanilla blocks and entities: **no new blocks, items or entity types are registered.** No new network message: one new action id (`StoryActionPayload.TAKEDOWN`) and two new figure flags (`StoryFiguresPayload.TWIN`, `TAKEDOWN`) in existing messages. Three small client mixins: `KeyboardHandlerMixin` and `KeyMappingMixin` (the story's key gate) and `LightmapRenderStateExtractorMixin` (the brightness "Maximal").

**Saves.** Old stories load as before. New plain flags only (`know.takedown`, `down/<mission>/<who>/x/y/z/yaw`). A file saved by an older version in the middle of the break-out from Hale's cell (at a checkpoint made by freeing R2-V7 or Kestrel) is put back into the cell when it is loaded (see section 6). The settings file (`config/sabercraft/immersion_client.json`) keeps `story_brightness`; the value 3 is new ("Maximal"). The removed hyperspace settings in `config/sabercraft/abilities.json` are ignored when an old file is read (and left out the next time it is written).

## 1. The boss fight starts fresh after being caught in her second phase

Before: dying in her second phase and going back to the checkpoint, the fight could begin again with things of the second phase still there. On the client, her second blade could still show, and the HUD still said "phase 2" (the client kept its own copy of the phase). On the server, some of her timers, flags of the fight and the player's stricter guard, stamina and the guard's cooldown were kept; and the capture ran in the middle of the blow that killed the player.

Now:

- **One reset** (`Finale.resetFight`, `Figure.resetFight`) runs whenever the fight begins (also the first time), when the story goes back to a checkpoint, and when the player leaves the story. It sets her back to her first phase in every respect: full strength, saber out but not lit, guard; no pattern, combo, counted hits or parries, no reaction, push, throw (the staff back in her hand), ring, spin, slam, shove, lunge, rear attacks, path; her tempo, openness and power as at the start. The fight's flags and timers go (the scene at half strength, "boss done", the reveal, her fierce mood, the hints of the guard, every move's cooldown). The player: Focus full, no slowness, the stricter guard off, stamina full, the guard's cooldown and counter cleared (`SaberAbilities.freshForStory`).
- **The client** is told: the duel's phase back to 1, the Focus off, the blade-lock and ring effects cleared, the film of her second phase stopped if it still plays, her saber's state forgotten (so her second blade cannot show). Her second blade is now also tied to the server's word (`StoryFiguresPayload.TWIN`, set only while her strength is in the second phase), so a client that still remembered the second phase cannot draw it.
- **The capture after the fatal blow** waits for the end of the server's tick (it ran inside the blow before, with half of the blow's work still to come after it). An input of the player that arrives before that (a stroke, the Focus, a key of a scene) is not played out against the old fight any more: the capture comes first and the input is dropped (`Story.action`).

## 2. Films are skipped only with Space or Escape

`StoryCinema.FilmScreen`: only **Space** and **Escape** skip a film (after its first second, as before). Enter, the arrows, the other keys and the mouse (clicks, wheel) do nothing in a film. Nothing pressed during a film reaches the game: the player does not move, the hotbar does not change, no menu opens. A key pressed in a film and still held as it ends (E, Q, F, T...) does not repeat into the game either (no inventory, dropped item or chat right after the film); walking keys held stay down, so one walks on.

## 3. No jump after the dialog's last line; the arrows page through the lines

**The cause:** when a screen closes, Minecraft reads anew which keys are held (`KeyMapping.setAll`, as the mouse is caught again). Space held from the dialog's last line was therefore taken as the jump key held: the player jumped (and could walk into a door that opens for someone coming at it).

**The fix, at the keys themselves** (`StoryKeys`, with the two mixins): a key of the story's screens (Space, Escape, Enter, Tab, the arrows, the digits) pressed in a film, a dialog box or the screws' close-up stays that screen's until it is let go. When the screen closes while it is held, the game does not take it as held. A held key does not repeat in those screens either. Nothing is delayed: the next fresh press works as always (a jump, Escape for the pause menu). Keys that are not the story's (walking, sneaking) stay down for the game; pressed in a film or a box, only their key repeats are kept from the game until they are let go.

**Dialog box** (`DialogScreen`, `StoryClient`):

- **Right arrow:** the next line (as Space on a line without answers; a line still being written is shown whole first).
- **Left arrow:** the line before, and so on back to the first line of the box. While an earlier line shows, its answers are dimmed (the one chosen is marked), and the box says "Frühere Zeile 2/3 · →/Leertaste: zurück". Right, Space, Enter, Escape or a click go back to the current line. The story itself does not go back (an earlier line is only read again).
- On lines with answers the up and down arrows choose, as before.
- Outside dialogs the arrows do nothing new.

## 4. No hyperspace for ships in the open world

Removed completely: the key, the jump, its charge and cooldown, its sounds (the failed-jump sound file deleted), its network message, its settings, its effects on the client, its tick on the server (`Hyperspace`, `HyperspacePayload`, `HyperspaceClient`, the code in `ShipEntity`, `ShipFeatures`, `Starships`, `ShipClient`). The lever in the Falcon's cockpit outside the story does nothing now. **The Millennium Falcon's jump in the story stays** (`InteriorJump.story`): the escape at the end of the chapter, with the tunnel, the sound and the arrival, as before.

## 5. Brightness: "Maximal"

A fourth level of the story's brightness (menu: Normal / Heller / Hell / **Maximal**), not the default, kept in the settings file like the others.

- At "Maximal", in the story's ships, the world's light is lifted to the brightest vanilla curve, with a raised floor for the darkest places (`StoryBrightness`, through the game's lightmap; never beyond white). The story's own darkening of rooms is lifted on a curve (a room at 17 % light shows at about 45 %, one at 50 % at about 73 %; full light stays full light).
- The levels below it are unchanged. Outside the story nothing changes. The Darkness effect (only by command or another mod; the story uses none) keeps its darkness: the lift is off while the player has the effect.

## 6. The prison: caught after the break-out, back in the cell as it was

Before: after the break-out from Hale's cell (the trap), freeing R2-V7 or Kestrel made checkpoints of their own behind the pillar in the block. Caught after that, the player was put there, not in the cell. And if M-31 was sent out quickly, the cell's checkpoint could be missing altogether.

Now (`Chapter1`):

- **The cell's checkpoint is made at once** when the trap shuts.
- **From the trap until the break-out ends** (the steps `m5.cell` with the field off, `m5.r2`, `m5.cell1`, `m5.back`) that is the only checkpoint: caught anywhere on the way (in the block, its lobby, Kestrel's crawl way), the player is back in the cell with everything as it was then: the field on, R2-V7 restrained, Kestrel in her cell, the guard at his post, the door locked, no alarm. Dax says over the comlink that they threw you back in the cell. What was done before the prison (the missions, M-31, the journal) stays.
- When the break-out ends (back at the hideout), checkpoints are made as always again.
- A save from an older version in the middle of the break-out (also one made on leaving at the cell's step with the field already off) is put back into the cell on loading.
- Leaving the game during the break-out (or during an alarm or a chase) keeps the cell's checkpoint, and still writes what is known for good since (the takedown learned there, the screwdriver): the file is written on every leave now, without moving the checkpoint.

**The detention block's main door** now opens from inside as well (it opened only from outside). It opens for the player standing at it to go through, closes behind them, and does not close on someone standing in its opening. During the break-out it opens from outside too (back in). Otherwise it stays shut from the lobby as before (Hale's trap without his trust: the way is the crawl way), also for a player pressed right against it. While the player is hunted it stays shut as before (a hint says so). In Hale's scene it stays locked.

## 7. The silent takedown: a tip, then any unaware stormtrooper

Before: two guards of the story (the detention block's, the lab's) could be overpowered, each with a fixed point; nothing explained it.

**The tip.** The first time the player is out of the cell (or in from the crawl way), calm and unseen, a few steps behind the detention block's guard while he looks away, a card at the left says (with the player's own keys):

> **Lautlos ausschalten** – Der Sturmtruppler schaut weg. Geduckt ([Umschalt]) von hinten heran, dann [Rechtsklick]: lautlos ausgeschaltet. Wer dich bemerkt hat, lässt sich nicht mehr überrumpeln.

Once per game (kept at once, also after a capture); a player who does a takedown before it needs no tip.

**Then any stormtrooper** who has not noticed the player can be overpowered, the same short scene as before (grab, blow, he goes down and stays down). The rules (`Chapter1.takedownable`, `takedownReady`, `overpower`):

| | |
|---|---|
| Who | stormtroopers on watch or on their round (also one lured off by M-31 or going back); never officers, technicians, pilots, droids, the story's people, troopers the story needs standing (the lure's lesson in Mission 1, Hale's trap, the checkpoint, the service way), fighters, reinforcements |
| When | known (the tip, or one done; in Mission 6 always); no alarm, no fight, no dialog, no scene |
| He | not alerted, has not seen the player for 2 s |
| Where | close behind him (2 blocks, same floor, nothing between), at least 110 degrees round from where he looks |
| How | crouching (Shift); otherwise "Nur geduckt – sonst hört er dich kommen." and nothing happens |
| Not | while anyone else sees the player ("Nicht jetzt – jemand anderes sieht her.") |

- **The prompt** under the crosshair ("[Rechtsklick] Lautlos ausschalten") shows only where it would work. Standing, it is grey and says to crouch.
- From the front he still sees it coming and the alarm goes up (a click in the moment he turns).
- **He lies where he fell**, also after a capture (once a checkpoint was made since) and as the steps go on.
- The detention block's and the lab's guards keep their windows in the story; the lab guard's from Phase 26/27, the detention guard's after the break-out.

## 8. Tests

See below (filled in after the test runs).

## 9. Manuelle Testliste (für Phiro)

Was die automatischen Tests nicht ersetzen können, im echten Spiel mit der JAR aus diesem PR. Am einfachsten über **Story → Kapitel 1** und die Checkpoints.

**1. Bosskampf nach dem Tod in Phase 2**

1. Den Kampf bis in ihre zweite Phase bringen (zweite Klinge, Drehangriffe), dort absichtlich sterben.
2. Nach dem Neustart: Sie hat eine Klinge, volle Kraft, die Anzeige sagt Phase 1, sie greift wie zu Beginn an (keine Drehangriffe, kein Wurf der zweiten Phase). Deine Ausdauer ist voll, keine Verlangsamung.
3. Das zwei-, dreimal wiederholen, auch einmal nach einem langen Kampf in Phase 1 sterben.

**2. Filme überspringen**

4. In einem Film: Pfeiltasten, Enter, W/A/S/D, E, Ziffern, Maus (Klick, Rad) drücken. Der Film läuft weiter, du bewegst dich nicht, die Hotbar bleibt.
5. Leertaste überspringt (ohne dass du danach springst), Escape überspringt (ohne dass das Pausemenü aufgeht).

**3. Dialoge**

6. Letzte Textbox mit der Leertaste schließen und die Taste gedrückt halten: kein Sprung, keine Tür, die aufgeht. Danach normal springen: geht sofort.
7. Dasselbe vor einer Tür und vor etwas Benutzbarem (z. B. einem Terminal).
8. In einem längeren Dialog: → geht zur nächsten Zeile, ← zeigt die vorige (mehrmals zurück bis zur ersten), → oder Leertaste wieder zur aktuellen. Bei Antworten: Pfeil hoch/runter wählt wie bisher.
9. Außerhalb von Dialogen: Pfeiltasten machen nichts Ungewolltes.

**4. Hyperraum**

10. In der normalen Welt in X-Wing, TIE, Falcon steigen: kein Hyperraum mehr (keine Taste in den Steuerungs-Einstellungen, kein Hebel im Falcon-Cockpit, keine Effekte).
11. In der Story: Der Sprung des Falcon am Ende von Kapitel 1 funktioniert wie bisher (Tunnel, Ton, Ankunft).

**5. Helligkeit**

12. Story-Menü → Helligkeit auf **Maximal**: dunkle Räume (Reaktor, Lüftungsschächte, Gefängnis) deutlich heller, aber nicht weiß überstrahlt. Texte und HUD gut lesbar.
13. Spiel neu starten: Maximal ist noch eingestellt. Zurück auf „Normal“: alles wie vorher.

**6. Gefängnis**

14. Haless Falle (ihm vertrauen), in der Zelle: M-31 schaltet das Feld ab. Gleich nach dem Ausbruch entdecken lassen: zurück in der Zelle, Feld an, Wache auf Posten, keine Alarmsirene.
15. Noch mal ausbrechen, R2-V7 befreien, zu Kestrels Zelle, dann entdecken lassen: wieder in der Zelle, R2-V7 wieder gefesselt.
16. Die große Schiebetür des Zellenblocks: von innen hingehen, sie öffnet sich und schließt hinter dir. Von außen wie bisher. Sie bleibt nicht offen stehen.
17. Den Ausbruch einmal ganz durchspielen bis zurück ins Versteck.

**7. Takedown**

18. Nach dem Ausbruch hinter die Wache des Zellenblocks schleichen, während sie zur Tür schaut: links erscheint die Karte „Lautlos ausschalten“ mit deinen Tasten.
19. Stehend hinter einem Sturmtruppler: grauer Hinweis „nur geduckt“. Geduckt: „[Rechtsklick] Lautlos ausschalten“, Klick: die kurze Szene, er bleibt liegen.
20. Danach bei anderen Sturmtruppen in Mission 5 und 6 (Patrouille in der Lobby, Wachen auf dem Deck): geht nur von hinten, geduckt, wenn er dich nicht bemerkt hat und kein anderer zusieht. Bei Offizieren, Technikern, Droiden, im Kampf, bei Alarm: nie.
21. Fühlt es sich zu leicht oder zu schwer an? Bitte melden.

**Allgemein**

22. Alles andere wie in Phase 30: der übrige Bosskampf, die übrigen Filme, die Flucht, die Checkpoints. Bitte melden, wenn sich etwas anders anfühlt.
