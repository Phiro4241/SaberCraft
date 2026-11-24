# Phase 31: the last fixes before the final test

Built on `main` after Phase 30 (PR #35, merge commit `c5b1fe5`). No release, nothing merged. Phiro asked for eight fixes; everything else stays as it was. As before, the server decides everything from vanilla blocks and entities: **no new blocks, items or entity types are registered.** No new network message: one new action id (`StoryActionPayload.TAKEDOWN`) and two new figure flags (`StoryFiguresPayload.TWIN`, `TAKEDOWN`) in existing messages. Three small client mixins: `KeyboardHandlerMixin` and `KeyMappingMixin` (the story's key gate) and `LightmapRenderStateExtractorMixin` (the brightness "Maximal").

**Saves.** Old stories load as before. New plain flags only (`know.takedown`, `down/<mission>/<who>/x/y/z/yaw`). A file saved by an older version in the middle of the break-out from Hale's cell (at a checkpoint made by freeing R2-V7 or Kestrel) is put back into the cell when it is loaded (see section 6). The settings file (`config/sabercraft/immersion_client.json`) keeps `story_brightness`; the value 3 is new ("Maximal"). The removed hyperspace settings in `config/sabercraft/abilities.json` are ignored when an old file is read (and left out the next time it is written).

## 1. The boss fight starts fresh after being caught in her second phase

Before: dying in her second phase and going back to the checkpoint, the fight could begin again with things of the second phase still there. On the client, her second blade could still show: the client kept its own copy of the phase, and that copy alone decided the second blade. On the server, some of her timers, flags of the fight and the player's stricter guard, stamina and the guard's cooldown were kept; and the capture ran in the middle of the blow that killed the player.

Now:

- **One reset** (`Finale.resetFight`, `Figure.resetFight`) runs whenever the fight begins (also the first time), when the story goes back to a checkpoint, and when the player leaves the story. It sets her back to her first phase in every respect: full strength, saber out but not lit, guard; no pattern, combo, counted hits or parries, no reaction, push, throw (the staff back in her hand), ring, spin, slam, shove, lunge, rear attacks, path; her tempo, openness and power as at the start. The fight's flags and timers go (the scene at half strength, "boss done", the reveal, her fierce mood, the hints of the guard, every move's cooldown). The player: Focus full, no slowness, the stricter guard off, stamina full, the guard's cooldown and counter cleared (`SaberAbilities.freshForStory`).
- **The client** is told: the duel's phase back to 1, the Focus off, the blade-lock and ring effects cleared, the film of her second phase stopped if it still plays, her saber's state forgotten (so her second blade cannot show). Her second blade is now also tied to the server's word (`StoryFiguresPayload.TWIN`, set only while her strength is in the second phase), so a client that still remembered the second phase cannot draw it.
- **The capture after the fatal blow** waits for the story's next tick (at most one tick later; before, it ran inside the blow, with half of the blow's work still to come after it). An input of the player that arrives before that (a stroke, the Focus, a key of a scene) is not played out against the old fight any more: the capture comes first and the input is dropped (`Story.action`).

## 2. Films are skipped only with Space or Escape

`StoryCinema.FilmScreen`: only **Space** and **Escape** skip a film (after its first second, as before). Enter, the arrows, the other keys and the mouse (clicks, wheel) do nothing in a film. Nothing pressed during a film reaches the game: the player does not move, the hotbar does not change, no menu opens. A key pressed in a film and still held as it ends (E, Q, F, T...) does not repeat into the game either (no inventory, dropped item or chat right after the film); walking keys held stay down, so one walks on.

## 3. No jump after the dialog's last line; the arrows page through the lines

**The cause:** when a screen closes, Minecraft reads anew which keys are held (`KeyMapping.setAll`, as the mouse is caught again). Space held from the dialog's last line was therefore taken as the jump key held: the player jumped (and could walk into a door that opens for someone coming at it).

**The fix, at the keys themselves** (`StoryKeys`, with the two mixins): a key of the story's screens (Space, Escape, Enter, Tab, the arrows, the digits) pressed in a film, a dialog box or the screws' close-up stays that screen's until it is let go. When the screen closes while it is held, the game does not take it as held. A held key does not repeat in a film or a dialog box (in the screws' close-up, Space or Enter held down still turns the screw a quarter turn per repeat, as before). Nothing is delayed: the next fresh press works as always (a jump, Escape for the pause menu). Keys that are not the story's (walking, sneaking) stay down for the game; pressed in a film or a box, only their key repeats are kept from the game until they are let go.

**Dialog box** (`DialogScreen`, `StoryClient`):

- **Right arrow:** goes on like Space on a line without answers (a line still being written is shown whole first, then the next line).
- **Left arrow:** the line before, and so on back to the first line of the box. While an earlier line shows, its answers are dimmed (the one chosen is marked), and the box says "Frühere Zeile 2/3 · →/Leertaste: zurück". Right pages one line forward again (from the line just before the current one, back to the current line); Space, Enter, Escape or a click go straight back to the current line. The story itself does not go back (an earlier line is only read again).
- On lines with answers the up and down arrows choose, as before.
- Outside dialogs the arrows do nothing new.

## 4. No hyperspace for ships in the open world

Removed completely: the key, the jump, its charge and cooldown, its sounds (the failed-jump sound file deleted), its network message, its settings, its effects on the client, its tick on the server (`Hyperspace`, `HyperspacePayload`, `HyperspaceClient`, the code in `ShipEntity`, `ShipFeatures`, `Starships`, `ShipClient`). The hyperdrive lever by the co-pilot's seat is still there as part of the dashboard, but a click on it does nothing (in the open world and in the story), and its lamp is dark unless the story's Falcon is jumping (it showed green "ready" before). The status at the engineering station no longer reports a hyperdrive, and the cockpit seats no longer mention hyperspace. **The Millennium Falcon's jumps in the story stay** (`InteriorJump.story`): coming out of hyperspace at the Star Destroyer in Mission 1 (before the docking film), and the escape at the end of the chapter, with the tunnel, the sound and the arrival, as before.

## 5. Brightness: "Maximal"

A fourth level of the story's brightness (menu: Normal / Heller / Hell / **Maximal**), not the default, kept in the settings file like the others.

- At "Maximal", in the story's ships, the world's light is lifted to the brightest vanilla curve, with a raised floor for the darkest places (`StoryBrightness`, through the game's lightmap; never beyond white).
- The story's own darkening (emergency light, the alarm's pulse, failing lights) is lifted further than at "Hell" (0.85 instead of 0.7), and the rooms' resulting dimming then goes through a curve: a room at 17 % light shows at about 45 %, one at 50 % at about 73 %; full light stays full light.
- The levels below it are unchanged. Outside the story nothing changes. The Darkness effect (only by command or another mod; the story uses none) keeps its darkness: the lift of the world's light is off while the player has the effect (the curve on the rooms' dimming stays).

## 6. The prison: caught after the break-out, back in the cell as it was

Before: after the break-out from Hale's cell (the trap), freeing R2-V7 or Kestrel made checkpoints of their own behind the pillar in the block. Caught after that, the player was put there, not in the cell. And if M-31 was sent out quickly, the cell's checkpoint could be missing altogether.

Now (`Chapter1`):

- **The cell's checkpoint is made at once** when the trap shuts.
- **From the trap until the break-out ends** (the steps `m5.cell` with the field off, `m5.r2`, `m5.cell1`, `m5.back`) that is the only checkpoint: caught anywhere on the way (in the block, its lobby, Kestrel's crawl way), the player is back in the cell with everything as it was then: the field on, R2-V7 restrained, Kestrel in her cell, the guard at his post, the door locked, no alarm. Dax says over the comlink that they threw you back in the cell. What was done before the prison (the missions, M-31, the journal) stays.
- When the break-out ends (back at the hideout), checkpoints are made as always again.
- A save from an older version in the middle of the break-out (also one made on leaving at the cell's step with the field already off) is put back into the cell on loading.
- Leaving the game during the break-out keeps the cell's checkpoint (as leaving during an alarm, a chase or a fight keeps the last checkpoint), and still writes what is known for good since (the takedown learned there, the screwdriver): the file is written on every leave now, without moving the checkpoint.

**The detention block's main door** now opens from inside as well (it opened only from outside). It opens for the player standing at it to go through, closes behind them, and does not close on the player standing in its opening (also while hunted). During the break-out it opens from outside too (back in). Otherwise it stays shut from the lobby as before (Hale's trap without his trust: the way is the crawl way), also for a player pressed right against it. While the player is hunted it does not open (a hint says so). In Hale's scene it stays locked.

## 7. The silent takedown: a tip, then any unaware stormtrooper

Before: two guards of the story (the detention block's, the lab's) could be overpowered, each with a fixed point; nothing explained it.

**The tip.** The first time the player is out of the cell (or in from the crawl way), calm and unseen, a few steps behind the detention block's guard while he looks away, a card at the left says (with the player's own keys):

> **Lautlos ausschalten** – Der Sturmtruppler schaut weg. Geduckt ([Schleichen-Taste]) von hinten heran, dann [Rechte Maustaste]: lautlos ausgeschaltet. Wer dich gerade gesehen hat oder gewarnt ist, lässt sich nicht überrumpeln.

(The keys appear with the game's own names for the player's sneak and use keys.) Once per run of the story: kept at once (also after a capture or leaving). A Restart, or Mission 5 chosen again from the story menu, starts without it, so there the tip comes again (and other troopers can be overpowered only after it, or after a takedown). A player who does a takedown before it needs no tip.

**Then any stormtrooper** who has not noticed the player can be overpowered, the same short scene as before (grab, blow, he goes down and stays down). The rules (`Chapter1.takedownable`, `takedownReady`, `overpower`):

| | |
|---|---|
| Who | stormtroopers on watch or on their round (also one lured off by M-31 or going back); never officers, technicians, pilots, droids, the story's people, troopers the story needs standing (the lure's lesson in Mission 1, Hale's trap, the checkpoint, the service way), fighters, reinforcements |
| When | known (the tip, or one done; in Mission 6 always); no alarm, no fight, no dialog, no scene, not while cornered ("Hands up"), not in the 2 s after a takedown |
| He | not alerted, has not seen the player for 2 s |
| Where | close behind him (2 blocks, same floor, nothing between), at least 110 degrees round from where he looks |
| How | crouching (Shift); otherwise "Nur geduckt – sonst hört er dich kommen." and nothing happens |
| Not | while anyone else sees the player ("Nicht jetzt – jemand anderes sieht her.") |

- **The prompt** under the crosshair ("[Rechte Maustaste] Lautlos ausschalten") shows when he can be overpowered from where one stands (close behind him, he unaware). Standing, it is grey and adds that one must crouch. Whether someone else is watching is checked only on the click: the prompt can show then, and the click says "Nicht jetzt – jemand anderes sieht her." and nothing happens.
- From the front he still sees it coming and the alarm goes up (a click in the moment he turns).
- **He lies where he fell** for the rest of that mission, as the steps go on, and also after a capture or leaving, provided a checkpoint was made since. (During the break-out from Hale's cell there is none: caught there, all stand again. In the next mission everyone is back at his post.)
- The detention block's and the lab's guards keep their windows of the story, also before the takedown is known. The lab guard's is as in Phase 26/27 (Mission 6, from the reactor to the saber, while it lies there). The detention guard's starts once the player is out of Hale's cell (or in from the crawl way, on the way without the trap) and lasts until Mission 5 ends; in the cell itself he cannot be overpowered.
- Both guards now follow the same rules as everyone (before, standing worked, from 2.4 blocks and 100 degrees). Clicked from his side (85 to 110 degrees) one is told "Näher heran, direkt hinter ihn …" and nothing happens; within 85 degrees he sees it coming.

## 7a. Also fixed (found by the review of this phase)

Two older faults around the save, found while checking that the new "write on every leave" does not lose anything:

- **Restart or Play Mission right after entering the story** (from the in-story menu, before the first checkpoint was written): the new start read the save as it was before entering, and took the story's ship as the way back, Adventure as the game mode and the story's droids as one's own. Leaving afterwards put the player at the world's spawn, in Adventure, without their droids. Now the way back (where one came from, the game mode, one's own droids, the difficulty) is written into the save first when it is not there yet (`Story.keepWayBack`, for RESTART and MISSION); nothing else of the running story is written.
- **Out of the story's ship by a command** (`/tp` to another dimension): the story ended without its session, so a story entered without a checkpoint since left the player in Adventure with the story's things, and what was known for good since the last checkpoint was lost. Now it ends as leaving does.

## 8. Tests

New: `Phase31GameTest` (in the real game with a client); its parts can be run alone with `PHASE31_ONLY` (for example `PHASE31_ONLY=boss,prison`). It takes about 2.5 minutes, so the CI's gametest step (86 of 115 minutes in Phase 30) keeps its limit.

| Point | Part | What it checks |
|---|---|---|
| 4 | `hyperspace` | no hyperspace key in the controls, no setting, no sound of a failed jump, nothing of a jump kept on the ships (the story's jump sounds stay) |
| 5 | `brightness` | "Maximal" is not the default; chosen, it is kept across a restart (read back from `immersion_client.json`) and in effect; it lifts a dark room (light 0.17) clearly, the darkest light too, never beyond white, leaves a room in full light as it is, and the level below it is unchanged |
| 2 | `films` | in a long film (`duel`), once for Space and once for Escape: the four arrows, Enter, W, A, S, D, E, 3, W held, both mouse buttons and the wheel skip nothing; then Space (or Escape) skips it and counts as one skip; nothing pressed in it moves the player or changes the hotbar, the skip makes no jump and opens no screen (no pause menu); a key held in the film (E) does not repeat into the game and is free again once let go; "Maximal" lifts the light in the story's ship, not under the Darkness effect, and again after it |
| 3 | `dialog` | a real dialog in the prison: Right goes on to the next line, Left shows the one before (and does not change the story's line), Right comes back to the current line without skipping it, Space on an earlier line only comes back; Space (held) on the last line closes it with no jump, no use of what is under the crosshair, no door moved and no jump key held, also after Minecraft reads the held keys anew; a fresh Space then jumps as always; Enter and a mouse click on the last line the same (nothing used, no jump) |
| 1 | `boss` | two rounds in a row: her second phase (server and client, second blade out), the player killed there (after 3 s, then after 8 s of it), put back alive and well; the duel begun again as at its start in every respect checked (her health and phase, ring, staff, spin, slam, shove, every move and counter, her reactions, the story's flags and timers of the fight, the player's Focus, strict guard and stamina; the client back in phase 1, no second blade), and her second phase not coming back by itself; in the second round a stroke sent in the same tick as the lethal hit (the capture still comes first) |
| 8 | `tip` | the takedown is not known before; the tip comes behind the detention block's guard, not while a dialog is open, shows in the client with its words, and does not come again |
| 8 | `takedown` | before the tip nobody here can be overpowered; then the lobby's patrol (not the old guard): standing behind him, refused with the crouch hint and no alarm; crouching close behind him, the prompt shows, the use key takes him down, its scene plays, he is down afterwards (kept as a `down/` flag), no alarm, and he lies where he fell after a checkpoint is made again |
| 8 | `refuse` | the rules: not while he is alerted, not when he saw the player a moment ago (yes a while later), not while another trooper sees the player, not during an alarm; in Mission 6 only suitable stormtroopers (no one hostile, talking or needed standing, no officer, crew or droid, not the catwalk's technician); no fighter of the finale; before the tip (Mission 4) nobody |
| 7, 9 | `prison` | Hale's trap: a checkpoint in the cell; the break-out under way once out; caught at once and after moving on through the block (after freeing R2-V7, past the main door): back in the cell as it was (trap state, step `m5.cell`, R2-V7 not freed, no alarm), progress from before the prison kept (M-31); no checkpoint made during the break-out; the main door opens from inside, from the lobby side as before, closes behind the player; the break-out plays through to its end and the cell's checkpoint is no longer held |
| 4 | `falcon` | the story's jump of the Falcon starts, is counted, runs, no second one starts while it runs, it ends |
| 7a | `restart` | Restart right after entering a mission: the story starts anew and the game mode comes back on leaving; out of the story's ship by a `/tp` command: out of the story, game mode back, the save no longer says the story runs |

Changed tests (only where Phase 31 changed what they measure):

- `Phase14GameTest`, `Phase16GameTest`, `Phase17GameTest`: the open world's jump, its key, setting, cooldown and the Falcon's lever are gone; they now check that nothing of it is left and that the story's jump still runs.
- `Phase24GameTest`, `Phase26GameTest`, `Phase27GameTest`, `Phase29GameTest`: the takedown is now done crouching, as it has to be.
- `Phase28GameTest`: the Falcon's jump after the escape is checked to start and show in the client.
- Test helpers (`StoryTesting`): a checkpoint written for the tests in the prison's steps no longer carries `m5.trapped` (else the new prison migration moves it back into the cell); forgetting a downed guard also clears his new `down/` flag.

### Results (24.11.2026, in this cloud container: Xvfb, llvmpipe, no real GPU)

- **Full run** at commit `ec00a7b`: the 43 classes from `BladeScreenshotGameTest` to `Phase25GameTest` green (1 h 12 min). `Phase26GameTest` then stopped the run (the client gametests stop at the first failing class) on the test helper's old checkpoint with `m5.trapped` (see above, a test problem, not one of the game).
- **The rest after that fix**, in three runs: first `Phase26GameTest` and `Phase27GameTest` green, `Phase28GameTest` red because my first fix for 7a (saving the whole session before Restart and Play Mission) also kept the mission's start too early, so M-31 was missing in Mission 5. That fix now keeps only the way back (place, game mode, droids, difficulty). Then `Phase26GameTest`, `Phase27GameTest`, `Phase28GameTest` green, `Phase29GameTest` red on the old guard's new `down/` flag that the test helper did not clear (a test helper). After that fix `StoryGameTest`, `Phase21GameTest`, `Phase22GameTest`, `Phase23GameTest`, `Phase29GameTest`, `Phase30GameTest` and `Phase31GameTest` green (29 min).
- So **every one of the 49 classes ran green** on this phase's code: the first 43 at `ec00a7b` (those that use Restart or Play Mission again after the fix for it), the rest on the final code or the code just before it.
- **After the last small changes** (the Falcon's idle lever lamp, the tip's text, this document): the classes that test the ships' interiors, the Falcon's lever and jump, the guards' takedowns and Phase 31 again green on the final code: `Phase14GameTest`, `ShipBoardingGameTest`, `InteriorDesignGameTest`, `Phase16GameTest`, `Phase17GameTest`, `Phase26GameTest`, `Phase27GameTest`, `Phase31GameTest` (21 min). The other classes were not run again after these small changes.
- `Phase31GameTest` on the final code: all 11 parts with 0 problems (hyperspace 0 s, brightness 0 s, films 12 s, dialog 8 s, boss 33 s, tip 8 s, takedown 10 s, refuse 10 s, prison 28 s, falcon 8 s, restart 5 s).
- The CI run on GitHub starts with this pull request; its result is not part of this document.

**Not tested automatically:**

- playing by hand (how the fights, the takedown and the films feel), see the list below;
- a real keyboard: the tests press keys through the test client's input, which goes the same way into the game as a real key. The operating system's key repeats were not sent (the test checks that a held key is marked so that its repeats are kept from the game), and a window losing the focus while a key is held was checked only by reading the code and by the review;
- sound: nobody listened to it (the tests only check which sounds exist);
- a real GPU with shaders (Iris), and how bright "Maximal" looks on a real screen;
- a dedicated server with several players.

## 9. Manuelle Testliste (für Phiro)

Was die automatischen Tests nicht ersetzen können, im echten Spiel mit der JAR aus diesem PR. Am einfachsten über **Story → Kapitel 1** und die Checkpoints. Keiner dieser Punkte wurde von mir im echten Minecraft-Client gespielt; die automatischen Tests laufen in einem Test-Client ohne echte Tastatur, und den Ton hat niemand angehört.

**1. Bosskampf nach dem Tod in Phase 2**

1. Den Kampf bis in ihre zweite Phase bringen (zweite Klinge, Drehangriffe), dort absichtlich sterben.
2. Nach dem Neustart: Sie hat eine Klinge, ihr Balken oben ist wieder ganz voll (rechts der Marke in der Mitte), sie greift wie zu Beginn an (keine Drehangriffe, kein Wurf der zweiten Phase). Deine Ausdauer ist voll, keine Verlangsamung.
3. Das zwei-, dreimal wiederholen, auch einmal nach einem langen Kampf in Phase 1 sterben.

**2. Filme überspringen**

4. In einem Film: Pfeiltasten, Enter, W/A/S/D, E, Ziffern, Maus (Klick, Rad) drücken. Der Film läuft weiter, du bewegst dich nicht, die Hotbar bleibt.
5. Leertaste überspringt (frühestens eine Sekunde nach Filmbeginn; eine vom Dialog davor noch gedrückte Leertaste erst loslassen und neu drücken), ohne dass du danach springst. Escape überspringt ebenso, ohne dass das Pausemenü aufgeht.
6. E (oder Q) im Film gedrückt halten, bis er von selbst endet: danach geht kein Inventar auf und nichts wird fallen gelassen. W gedrückt halten: du läufst nach dem Film gleich weiter.

**3. Dialoge**

7. Letzte Textbox mit der Leertaste schließen und die Taste gedrückt halten: kein Sprung, keine Tür, die aufgeht. Danach normal springen: geht sofort.
8. Dasselbe vor einer Tür und vor etwas Benutzbarem (z. B. einem Terminal).
9. In einem längeren Dialog: → geht weiter wie die Leertaste, ← zeigt die vorige Zeile (mehrmals zurück bis zur ersten), → blättert Zeile für Zeile wieder vor; Leertaste, Enter, Escape oder ein Klick springen sofort zur aktuellen Zeile. Bei Antworten: Pfeil hoch/runter wählt wie bisher.
10. Außerhalb von Dialogen: Pfeiltasten machen nichts Ungewolltes.

**4. Hyperraum**

11. In der normalen Welt in X-Wing, TIE, Falcon steigen: kein Hyperraum mehr (keine Taste in den Steuerungs-Einstellungen, keine Effekte). Im Falcon-Cockpit ist der Hyperantrieb-Hebel noch als Teil des Armaturenbretts da, seine Lampe ist dunkel, ein Klick darauf tut nichts. Die Statusanzeige an der Ingenieursstation nennt keinen Hyperantrieb mehr.
12. In der Story: der Hyperraum-Austritt des Falcon zu Beginn von Mission 1 (vor dem Andock-Film) und der Sprung am Ende von Kapitel 1 wie bisher (Tunnel, Ton, Ankunft; die Lampe am Hebel blinkt gelb und wird blau).

**5. Helligkeit**

13. Story-Menü → Helligkeit auf **Maximal**: dunkle Räume (Reaktor, Lüftungsschächte, Gefängnis) deutlich heller, aber nicht weiß überstrahlt. Texte und HUD gut lesbar. Mit einem Shaderpack kann der Teil über das Weltlicht fehlen.
14. Spiel neu starten: Maximal ist noch eingestellt. Zurück auf „Normal“: alles wie vorher.

**6. Gefängnis**

15. Haless Falle (ihm vertrauen), in der Zelle: M-31 schaltet das Feld ab. Gleich nach dem Ausbruch entdecken lassen: zurück in der Zelle, Feld an, Wache auf Posten, keine Alarmsirene.
16. Noch mal ausbrechen, R2-V7 befreien, zu Kestrels Zelle, dann entdecken lassen: wieder in der Zelle, R2-V7 wieder gefesselt.
17. Die große Schiebetür des Zellenblocks: von innen hingehen (unentdeckt), sie öffnet sich und schließt hinter dir. Während des Ausbruchs aus Haless Zelle öffnet sie sich auch von außen (zurück hinein). Sonst bleibt sie von der Lobby aus zu wie bisher, auch wenn du direkt davor stehst (ohne Haless Vertrauen ist der Weg der Lüftungsschacht). Solange du verfolgt wirst, öffnet sie sich nicht (mit Hinweis). Sie bleibt nicht offen stehen, schließt aber nicht, während du in der Öffnung stehst.
18. Den Ausbruch einmal ganz durchspielen bis zurück ins Versteck.

**7. Takedown**

19. Die Karte kommt nur einmal pro Durchgang. Wer bei Punkt 15 bis 18 schon hinter der Wache war (z. B. bei R2-V7), hat sie dort schon gesehen. Darum vorher über das Story-Menü „Mission 5“ neu spielen (oder diesen Punkt vor 15 bis 18 testen): nach dem Ausbruch hinter die Wache des Zellenblocks schleichen, während sie zur Tür schaut, ohne vorher jemanden auszuschalten. Links erscheint die Karte „Lautlos ausschalten“ mit deinen Tasten.
20. Stehend hinter einem Sturmtruppler: grau „[Rechte Maustaste] Lautlos ausschalten (nur geduckt: <Schleichen-Taste>)“. Geduckt: weiß „[Rechte Maustaste] Lautlos ausschalten“, Klick: die kurze Szene, er bleibt liegen.
21. Danach bei anderen Sturmtruppen in Mission 5 und 6 (Patrouille in der Lobby, Wachen auf dem Deck): geht nur von hinten, geduckt, wenn er nicht gewarnt ist, dich seit 2 Sekunden nicht gesehen hat und kein anderer zusieht. Bei Offizieren, Technikern, Droiden, im Kampf, bei Alarm: nie.
22. Fühlt es sich zu leicht oder zu schwer an? Bitte melden.

**8. Speichern**

23. Story betreten und sofort im Story-Menü „Neustart“ (oder „Mission spielen“) wählen, dann die Story verlassen: du bist zurück am Ausgangsort, im eigenen Spielmodus, mit deinen eigenen Droiden.
24. Während des Ausbruchs (nach der Takedown-Karte) das Spiel verlassen und weiterspielen: wieder in der Zelle, und das Ausschalten ist noch bekannt (keine zweite Karte).

**Allgemein**

25. Alles andere wie in Phase 30: der übrige Bosskampf, die übrigen Filme, die Flucht. Bitte melden, wenn sich etwas anders anfühlt.
