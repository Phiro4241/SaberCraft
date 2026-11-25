# Phase 32: the boss fight made sound again, and the last corrections

Built on `main` after Phase 31 (PR #36, merge commit `6e5b58a`). No release, nothing merged. Phiro tested the last version himself and found the boss fight (the Inquisitor, "die Schattenklinge", in the hangar) partly buggy and worse than before. This phase looks for the causes in the last phases' changes and fixes them there; the mechanics that worked stay. As before, the server decides everything from vanilla blocks and entities: **no new blocks, items or entity types are registered.** No new network message: two new figure flags (`StoryFiguresPayload.DISC`, `THROW_LOW`) and three numbers for her ring's disc (`DISC_AHEAD`, `DISC_HEIGHT`, `DISC_RADIUS`, constants both sides share), one derived client flag (`hangar.sealed`, never saved) and one new sound file (`story/ring_loop.ogg`, made by `tools/synth_sounds.py` like the others).

**Saves.** Old stories load as before. Nothing new is saved: the hangar's seal is derived from the story's step each time, her ring's mode and her guard's poise live only in the running fight and are reset with it.

## 0. What went wrong, and where it came from

The analysis compared the boss fight at `main` with the phases before it (Phases 25 to 31) and was checked a second time against the code. Phase 31 itself changed nothing in how the fight plays (its reset, the second blade's flag and the key gate were checked and are fine); but since Phase 31 a caught player can replay the second phase cleanly and over and over, and so met the problems Phases 28 to 30 had brought in. The causes found, each fixed at its root:

| What Phiro saw | Cause | Since |
|---|---|---|
| The whirr of the spinning double blade without any spin | The whirr was a sound the server played **once** at the start of some moves (her slam, her rush with one blade, the ring set going). It played where the ring did not turn, stopped while it still turned, and could outlast a reset, a capture or a film. | Phases 26 to 28 |
| The stamina bar in the boss's films | The ability HUD (stamina, cooldowns) was drawn after the film screen and had no rule for films; it came through the films' black bars (a film's screen counts as part of the game's view, so lines drawn after it stay; the same over the dialog box). | Phase 6 (the HUD), Phase 20 (the film screen) |
| Standing in the spinning blade's circle, little or no damage | In her second phase the ring **turned all the time** ("ring latch", Phase 28), but it hurt only in the short spin move (Phase 30); a stroke into the spin made the blades clash and **ended the spin**; staying in the circle was safe after the first blow. | Phases 28, 30 |
| The saber held far from her body; the throw not in her hand | The ring's hold could lie outside her arm's reach (the hand stopped short of the grip, the staff floated); new holds jumped in one frame; the throw was let go at chest height in front of her (the drawn release and the server's start did not match); a stagger flicked the staff round. | Phases 26 to 30 |
| The ring practically the whole second phase | The ring latch: once set going it kept turning for at least 400 more ticks with each ring move, and in her last quarter for good. | Phase 28 |
| She blocks nearly everything in phase 2 | Her parry put her into a reaction that **cancelled her riposte** and **froze her pause** while she was struck, so hammering at her guard locked her in parries; strokes from behind into her wind-up clashed as if from the front; strokes at her back as the player saw her were judged against a head already turned on (she turns naturally since Phase 29, the player sees her a tick or two late); her guard had no limit; hammering broke each of her answers with a clash. | Phases 23 to 31 |
| The hangar can be left in the fight | The hangar's doors were never locked in the duel; its hatch refused only while the player was hunted; the toolbox and the vent cover could be used; the step before the duel ended on a line across the hangar, also when reached through the crawl way. | Phases 24 to 29 |
| A text saying the maintenance door opens by itself | Phase 30's one-click release added three texts that said so (a hint, the bar's label, an objective) and a control hint on the walkway. | Phase 30 |

## 1. Her ring's whirr: only while it really turns (2.1)

- **A loop on the client** (`RingSound`), one per figure at most, fed every frame her ring is drawn with how fast it turns: it starts as the ring spins up, gets louder and higher the faster it turns, fades out as it slows, and stops when she is no longer drawn. It only starts when the ring is **made** to turn (her ring's mode, the spin, the rush, the thrown staff), never when a parried stroke only twitches it round.
- **It stops at once** when a film begins (`StoryCinema.start`), when the fight is reset (a capture, a checkpoint, the duel begun again) and when the story is left. While a film runs nothing starts it.
- **The thrown staff** carries its own whirr through the air (the ring in her hand is silent meanwhile).
- **A loop the sound engine drops by itself** (all its channels taken, the sounds reloaded) counts as gone, and a new one begins while the ring still turns; a loop that could not start is not kept.
- **The server no longer plays the whirr** at all (slam, rush, the ring set going, the spin's rise). Her slam has no whirr (the ring does not turn in it); with one blade her own swish only.
- Other sounds checked: the ignition only once when the duel begins (no second one after her film), the clash, hit and block sounds only in their own situations.

## 2. The stamina bar in films (2.2)

The ability HUD (stamina and cooldowns) is not drawn while a film runs or its screen is open (`StoryCinema.hidesHud`), nor while a story conversation is open (her words before the duel and after it), nor with the HUD hidden (F1). That covers every boss film: the duel's start, the scene at half her strength (the phase change), her defeat and flight, and the films of the other chapters. After a film it is drawn again as before. The story's own HUD (her strength, the Focus, the objective) is unchanged; it already hid itself in films.

## 3. Her spin hurts in its whole circle (3)

- **One circle, one rule:** while her spin is live (the red circle on the floor, 3.5 blocks round her), whoever stands in it is hit, at any distance from her and at any side, measured from the player's middle as one sees oneself in the drawn circle. Jumping, moving or running past her through it is hit as well (the check runs every tick, on the player's position then).
- **No safe stay:** staying in the circle (against the knock-back) is hit again after 10 of her ticks (a hit pause, not a safe zone). A guard held up in it holds the first blow; each next one comes through in part (40 %).
- **A stroke into the spin no longer ends it:** the whirling blades turn the stroke aside and the spin goes on (before, the blades clashed and the spin was gone, and striking at her was a way to stand in it unhurt).
- **Drawn as it hits:** the staff leaves her hands in the spin and is guided round her by her outstretched hand, twice round while the circle is live, its blades sweeping the whole circle to its rim.
- **Her ring's mode** (section 5) adds the spinning disc before her: it cuts whoever walks into it (2 damage, at most every 14 of her ticks; blocked by the guard).
- The dash keeps its rules from Phase 30 (a dash begun in the circle dodges one blow; rolled in from outside it only passes through).

## 4. Her animations (4)

Improved where they were wrong, not replaced:

- **In her hand:** a grip beyond her arm's reach is drawn in to it (the hand stays on the grip); holds change in a quick motion instead of one frame; after a stagger or a crouch the staff comes back to guard from where it was, without a flick round.
- **Blows in her ring's mode:** the ring slows to a turn while she strikes with a blade, so the striking blade points the way of the blow.
- **The throw:** drawn back to her right, let go at her right: **high** over her head (a horizontal disc above her, the server's start at the same place, 1.87 over her feet) or **low** at the legs, from beside her right hip (1.15 over her feet, the spinning disc upright out at her side and clear of the floor, thrown down at the shins and laid flat in its first ticks of flight; held flat at the shins, as first planned, the disc went through her legs and was drawn pushed a block out of her hand); high and low take turns. The staff comes back into her right hand **where it is now** (she turns after the player while it flies; it went back to where her hand had been as she let go), on the server and on the client alike, reckoned by the way her body faces, as her arms are drawn (her head may be turned further); catching it costs her a moment (an opening, see 6), and her hold on it is taken up from her outstretched hand, not in one frame.
- **Her ring's mode:** the disc is drawn exactly where the server cuts with it (it was drawn 0.18 higher).
- **Switches:** the ring spun up before her as she raises it (the switch into the ring's mode), lowered a moment as it winds down (out of it).

## 5. Phase 2: plain blade work and the ring's mode take turns (5)

- **Plain blade work:** the ring stands still, both blades out, her combos without the spin, plus the lunge, the leap, the push, the slam and the throw.
- **The ring's mode:** she spins the ring up before her (18 ticks, seen and heard, nothing hurts yet), it turns for **210 ticks** (10.5 s; its disc before her cuts, her spin and the ring's rush come only now), then it winds down while she lowers the staff (26 ticks, she is open meanwhile) and fights with plain blade work for **300 ticks** (15 s; in her last quarter 190) before it comes again. The first time 6 s after the scene at half her strength. All at her tempo of the chosen difficulty.
- **No hectic switching:** the mode ends only at a calm moment (never in the middle of a move). A stroke from before her while she spins the ring up is turned aside by the spinning ring and the switch goes on; struck from behind she reels (the switch is gone) and raises the ring again once she has stepped back.
- **Sounds, damage and animation follow the switch:** the whirr comes and goes with the ring, the disc cuts only while it turns, the client's ring stands still in her blade work.

## 6. Phase 2 is no longer a wall (6)

Not less health, not a weaker guard; limits and openings:

- **Her parry no longer locks her:** a parried stroke keeps her riposte, and her pause between attacks runs on while she turns strokes aside (before, a player hammering at her guard froze her in parries and she never attacked, nor did her riposte arrive).
- **Her guard can be worn down (poise, in her second phase; her first phase stays as it was):** each stroke she turns aside costs her poise (a hasty light stroke 0.15, a full heavy one 0.42, a full stroke clashing into her wind-up 0.16, the disc 0.12; on Easy ×1.3, on Hard ×0.8). Three full strokes break it. With none left her guard breaks: she reels, an opening of 22 ticks. Her poise comes back once she has been left alone for 2.5 s.
- **Watching her pays, hammering hardly does:** strokes into her guard in a row are answered as before Phase 32, three in her first phase, two in her second (a thrust, or a shove with the Force), full strokes as hasty ones; with her answer she gathers herself (+0.2 poise). So hasty strokes (0.15 each) hardly wear her guard down, while three full strokes set with pauses still break it (on Normal; two on Easy, four on Hard). A player who waits for the right moment breaks her guard clearly more often than one who hammers away (checked by the test, see 10).
- **Hammering is not the way through:** a hasty stroke (less than 0.8 s after the last) from before her into her wind-up (in the second phase) is beaten aside by her blow, which falls at once; only a full stroke meets it (the clash). Before, every stroke clashed: hammering away broke each of her answers and wore her guard down besides. A full stroke (after a pause) still clashes as before; a stroke while running or jumping still does its heavy damage where it hits, but counts as hasty for her defence (before, strokes hammered while running held her in parries for good).
- **Ways through:** a stroke at her side while she winds up a blow (more than 70° round) hits and her blow is gone; a stroke from behind into her wind-up hits (it clashed before); a blow dodged right under it in the second phase carries her past (an opening of 20 ticks); her pause after a combo is a little longer again (17 ticks); her openings after a blocked last blow, a dodge, a perfect parry, the ring's wind-down and the staff caught back (12 ticks).
- **Strokes at her back count as the player saw them:** she still turns naturally and still whirls round on one who keeps behind her (Phase 29), but a stroke at her back is judged by where she faced now or a tick or two ago, as she is drawn. In the fight her figure is sent to the client every tick (before every second tick), so her wind-ups and turns are seen as they are.
- **Her ring's mode comes when it is due** also under constant pressure: the switch is checked before her pause and before her answers to one behind her or crowding her (before, a player pressing her from every side kept it from ever coming).
- **A clashed lunge is over:** her next thrust from further off no longer closes in at the lunge's speed.
- **Varied reactions:** turned aside by the blade, by the disc in her ring's mode, a quick thrust as riposte, a reel when her guard breaks, a stumble after a perfect parry.
- **The player's breath:** at each opening of hers in the second phase the player's stamina starts coming back at once (the guard's strain of a whole combo blocked does not carry into the next one).

## 7. The hangar is sealed during the duel (7)

From the duel's start (`m6.duel`) until she is gone (`gone.shadow`):

- **Its two doors** (the main sliding door and the door to the flight control room) stay shut for everyone: walking at them, pressed against them, jumping, nothing opens them. The duel's paths do not use them. A player standing right in the doorway as the duel begins is not shut in the door.
- **In the world, not invisible:** hazard bars across both doors and red pulsing lamps over them (both faces) and at the hatch; a short lock sound when the player walks at a sealed door or clicks the hatch, no text.
- **The hatch** in the hangar's west wall: a click does not open it; a hatch left open is shut at the duel's start and kept shut. The toolbox and the vent cover are not offered.
- **Safety net:** a player who still gets out (into the corridor, the crawl way, flight control) is faded and put back into the hangar after half a second; she too is put back if she ends up outside. The black moment keeps the duel as it is on the client (her second phase, her second blade, the Focus); any other fade still resets it there as in Phase 31.
- **The step before the duel** now ends only inside the hangar (not on a line also reached through the crawl way).
- **After the duel or when it is aborted** everything is as ever at once: after her defeat the doors open (the hangar's crew come running in through them), the lamps go, the toolbox and the cover come back; leaving the story or playing another mission restores the doors at once (nothing is placed in the world, the seal is only derived from the step). A capture in the duel keeps the seal (the duel begins again).

## 8. No texts about the game's workings (8)

At the maintenance door after the reactor escape (Mission 5): the click on the panel no longer shows "Die Tür entriegelt sich von selbst – lauf weiter!", the bar is "Notentriegelung … N %" again, and the objective does not switch to "it is unlocking … it will open in a moment". The control hint on the walkway ("Rechtsklick aufs Bedienfeld …") is gone too; the objective now names the panel without a mouse button ("… das Bedienfeld ist links neben der Tür"); Kestrel's radio, the marker, the amber panel and its prompt (with the key actually bound) lead the way. The mechanic is unchanged (one use starts it, it finishes in 1.4 s wherever the player goes).

In the options and the hilt menu, four texts no longer refer to the mod's history ("aus Phase 12", "das alte Aussehen", "(wie bisher)", "der ersten Version"): they describe the setting as it is.

Also: the one-time toast about the menu key no longer tells the history of the change ("vorher V, das jetzt frei ist, z. B. für Simple Voice Chat"); five text keys no longer used are deleted. **New texts in this phase:** only the credit "Erstellt von Phiro" / "Created by Phiro". The hangar's seal and her new behaviour have no texts. The new and changed texts were reviewed; helpful mission hints stay.

## 9. The chapter's end (9)

Shown **15 s** instead of 18 (the text fully visible about 11.5 s, fading in and out as before). The credit **ERSTELLT VON PHIRO** comes in 3 s after the text (so the text is read first), small, in wide capitals in the gold of the mission cards with a gold rule over it, and fades out with the screen. The closing animation and the panel that stays after it are unchanged.

## 10. Tests

`Phase32GameTest` (new, about 6 min; env `PHASE32_ONLY` selects parts) checks Phiro's 15 points in the real game:

- **texts:** no story hint, objective, prompt, radio or HUD text with "von selbst", "automatisch", "by itself", "automatically", "Rechtsklick", "right-click" (control texts like the screws' and the dialog's excepted); no text at all (options included) that tells the mod's history ("Phase 12", "wie bisher", "as before", "first version", "old look" …); both languages have the same keys; the removed keys are gone; the credit is there.
- **end:** 240 to 359 ticks; the credit not drawn before age 50, drawn from about age 60; the screen gone after its time; screenshot.
- **hud:** the stamina bar (stamina held low) is not drawn in the duel's film at its start, in the film of the scene at half her strength and in her defeat's film, and is drawn again after each of them.
- **sound:** no whirr in her blade work (apart from a thrown staff), exactly one loop in her ring's mode, gone within 40 ticks of the ring stopping, stopped at a film's start and back after it; playing before a capture and gone after it; playing before leaving the story and gone within 3 ticks of it (before it could fade out by itself).
- **spin:** hit at once with health lost at 16 places in the circle (0.7 to 3.15 blocks, before her, at both sides, behind her); kept in it: hit twice; a stroke into it does not end it; jumping and running past her through it: hit; outside the circle: nothing; a guard held up in it lets the next blow through in part.
- **throw:** the release as the client draws it, high and low, at 12 angles of the spinning ring: clear of her body, in her hand (at most 0.12 short of the grip), within 0.25 of where the server lets the staff fly, its disc not into the floor; two throws: let go at her right, high (1.87) and low (1.15) in turn, back into a right hand; she is open as she catches the staff.
- **defence:** 55 s of her second phase against a player who strikes at the right moments: at least a fifth of the strokes hit, her guard is worn down at least once, she attacks at least 8 times, her ring's mode comes at least twice with stretches of 6 to 21 s and blade work of at least 8 s between them, the client's ring still in her blade work; a player hammering at her guard gets her riposte, which reaches the player, and not every stroke is turned aside; hasty strokes into her wind-up are beaten aside by her blow (and do not clash it away); of the hammering player's strokes clearly fewer hit her (under 85 % of the watching player's share) and her guard breaks less often in the same time; set back into the hangar in her second phase, the client still draws her second phase and her second blade.
- **hangar:** both doors stay shut (walked at, pressed against, jumped at) and nobody gets through; a click on the hatch is refused, an open hatch is shut; the toolbox and the vent cover are not offered; set back in from the corridor and from the crawl way; the client's flag and screenshots of the lamps; after her defeat the doors open and the flag is gone; leaving the story frees the doors.

Older tests adapted to the new rules (not loosened): `Phase29GameTest` (the hangar's door now stays shut in the duel; its check that doors open for a player passing in a fight and do not flap beside one runs with the seal lifted for that part, and checks first that the door is not locked, so it cannot pass vacuously), `Phase28GameTest` (she is set back into the sealed hangar), `Phase30GameTest` (no control hint on the walkway and no "by itself" texts at the maintenance door; the mission's own hint allowed; the mechanic's checks unchanged). `StoryGameTest`'s rule from Phase 21 (three strokes in a row into her guard are answered, there set with pauses) holds again: the first Phase 32 builds left full strokes out of the row.

**Results.** All runs were local, in this container, with the real Minecraft 26.2 client under a virtual screen (software rendering; the sound is recorded to a file, nobody listened to it).

- **On the final code (`8f2f8e9`): all 50 test classes green**, in two runs on 2026-11-25: `Phase25GameTest` to `Phase32GameTest` (8 classes, 31 min 24 s) and the 42 classes before them, `BladeScreenshotGameTest` to `Phase24GameTest` with `StoryGameTest` (1 h 7 min 52 s).
- **`Phase32GameTest` in that run:**
  - texts: 1545 keys in each language, no technical wording;
  - end: 300 ticks, the credit first drawn at age 61, the screen gone after 299 ticks;
  - HUD: 0 lines of the stamina bar drawn in the films of the duel's start, of half her strength and of her defeat (stamina held low, the film still on), drawn again after each;
  - sound: no whirr in her blade work; in her ring's mode heard 3 ticks after it begins, at most one loop; the ring stopped 16 ticks after the mode's end, quiet after 31; quiet at a film's start, after a capture and within 3 ticks of leaving the story;
  - spin: hit at all 16 places in the circle; kept in it: 2 hits (6.4 health); jumping: 1 hit (4.0); running past: 2 hits; a stroke into it: the spin goes on; guarded and kept in it: 1.6 health lost (one part let through);
  - throw: the release at 12 angles, high and low: 0 poses touching her, the hand 0.00 short of the grip, drawn 0.00 from the server's start, the disc not below her feet; one low and one high throw let go at her right and caught;
  - defence, a player who watches her: 131 strokes, 37 hits (0.28 per stroke), her guard broken 7 times (every 157 ticks), she attacked 33 times, her ring's mode twice (262 and 271 ticks) with 328 ticks of blade work between them; a player who hammers: 48 strokes, 28 parried, 7 hits (0.15 per stroke), 13 ripostes and all 13 reached the player, 13 hasty strokes beaten aside by her blow, 0 clashes, her guard broken once (every 336 ticks).
- **Found by the tests on the way and fixed:**
  - her ring's mode never came under the new test's constant pressure (five runs; the switch is now checked before her pause and her answers);
  - `StoryGameTest`'s rule from Phase 21 (three strokes into her guard are answered) failed in three runs: first because full strokes were left out of the row, then because her poise broke her guard on the third full stroke in her first phase. Her first phase now has no poise and plays as before.
- **A second, independent reading of the changes** (each claim checked against the code) found 20 points, all fixed (or the text corrected) before the runs above. Among them: strokes while running counted as full strokes for her defence (hammering while running held her in parries); the thrown staff came back to where her hand had been; the low throw at shin height was drawn a block from her hand; the ring-mode disc was drawn 0.18 higher than it cut; a loop the sound engine dropped was still counted as playing; figures sent every tick stuttered under the Focus; two old door checks of `Phase29GameTest` could no longer fail; and five test checks that could not fail or were missing.
- **CI on GitHub:** the pull request's first run on `8f2f8e9` (run 45677809569) did not get through: after 24 minutes the game stood still in `AdvancementGameTest`, a class this phase does not touch (it had just opened the advancements screen, which pauses a single-player game; the world was saved for the pause, and then nothing came any more until the step's limit of 115 minutes stopped it). No earlier pull request run is known to have stopped there since the CI fixes of PR #14 (the failed runs since then failed on other tests); here it passed in every run, also within the same 42 classes in the same order on the same code (above). Run alone six times more here afterwards, it passed each time (50 to 81 s, with the pause's save). The commit after it changes only this document and starts the pull request's run again on the same code; its result is on the pull request.

**Not tested automatically:**

- playing by hand: how the fight feels (fairness, difficulty, readability of her openings), see the list below;
- sound: the tests check which loops play and when; nobody listened to it;
- a real GPU with shaders (Iris);
- a dedicated server with several players.

## 11. Manuelle Testliste (für Phiro)

Mit der JAR aus diesem PR, am einfachsten über **Story → Kapitel 1** und den Checkpoint vor dem Bosskampf. Keiner dieser Punkte wurde von mir im echten Minecraft-Client gespielt; die automatischen Tests laufen in einem Test-Client, und den Ton hat niemand angehört.

**1. Sound der drehenden Doppelklinge**

1. Phase 1 und der Beginn von Phase 2: kein Drehgeräusch, solange sich der Ring nicht dreht (auch nicht bei ihrem Bodenschlag oder wenn du einen Schlag parierst).
2. In Phase 2 hebt sie den Ring vor sich und lässt ihn kreisen: das Geräusch beginnt mit der Drehung, wird bei der Wirbelattacke lauter und hört kurz nach dem Ende der Drehung auf. Nie zwei Loops übereinander.
3. Beim Wurf summt die fliegende Klinge, in ihrer Hand ist es still, bis sie sie fängt.
4. Während der Zwischensequenz bei halber Kraft, nach dem Tod (Neustart des Kampfes) und beim Verlassen der Story: sofort still.

**2. Ausdauerleiste**

5. Ausdauer verbrauchen (blocken, ausweichen), dann in eine Zwischensequenz kommen (Beginn des Kampfes, halbe Kraft, ihre Niederlage): keine Ausdauerleiste und keine Abklingzeiten im Bild. Danach wieder da.

**3. Wirbelattacke**

6. Im roten Kreis stehen bleiben, nah an ihr, mittig, am Rand, vor ihr, neben ihr, hinter ihr: jedes Mal Schaden.
7. Im Kreis springen oder seitlich an ihr vorbeilaufen: Schaden. Außerhalb des Kreises: keiner.
8. In die Wirbelattacke hineinschlagen: sie hört nicht auf. Mit erhobener Deckung im Kreis bleiben: der erste Treffer wird gehalten, danach kommt ein Teil durch.

**4. Animationen**

9. Das Schwert liegt in ihrer Hand, auch bei schnellen Wechseln; es schwebt nicht vor ihr.
10. Wurf: sie holt rechts aus, wirft einmal hoch über den Kopf, einmal tief auf die Beine (aus Hüfthöhe neben ihr); die Klinge kommt in ihre rechte Hand zurück, auch wenn sie sich inzwischen gedreht hat, danach ist sie kurz offen.
11. Schläge im Ringmodus: die schlagende Klinge zeigt in Schlagrichtung, der Ring dreht dabei langsamer.

**5. Wechsel Klinge / Rotation in Phase 2**

12. Phase 2 eine Weile spielen: etwa 15 s normale Klingenkombos, dann hebt sie den Ring und er dreht etwa 10 s (in dieser Zeit Wirbelattacke und Ringansturm), dann senkt sie ihn wieder. Das wiederholt sich, nicht hektisch. Im letzten Viertel kommt der Ring etwas öfter.

**6. Verteidigung in Phase 2**

13. Nur draufhauen: sie pariert, kontert und sammelt sich dabei; so bricht ihre Deckung nur selten. Mit Pausen gesetzte volle Schläge (etwa drei) brechen sie (sie taumelt): dann treffen. Abwarten und im richtigen Moment zuschlagen soll sich deutlich mehr lohnen.
14. Von der Seite in ihren ausholenden Schlag, von hinten, nach einem Ausweichen direkt unter ihrem Schlag, nach dem Fangen ihres Wurfs: Treffer möglich.
15. Fühlt sich Phase 2 fair, aber anspruchsvoll an? Zu leicht oder zu schwer? Bitte melden (auch auf Leicht und Schwer).

**7. Hangar**

16. Im Kampf zur großen Schiebetür und zur Tür des Flugkontrollraums laufen, dagegen drücken, springen: sie bleiben zu, rote Warnlampen und Warnbalken, ein kurzes Verriegelungsgeräusch, kein Text.
17. Die Lüftungsklappe in der Westwand anklicken: bleibt zu. Werkzeugkiste und Abdeckung sind nicht benutzbar.
18. Nach ihrer Niederlage: Türen gehen wieder auf, Lampen aus. Story verlassen und neu betreten, andere Mission spielen: alles normal.

**8. Texte**

19. Mission 5, Flucht nach dem Reaktor: an der Wartungstür das Bedienfeld benutzen. Nur der Balken „Notentriegelung … %“ läuft, kein Text „von selbst“, kein „Rechtsklick“ im Ziel. Die Tür öffnet sich, von innen verriegeln geht wie bisher.

**9. Kapitelabschluss**

20. Kapitel 1 abschließen: der Abschlusstext steht etwa 15 s (gut lesbar), nach etwa 3 s erscheint klein darunter „ERSTELLT VON PHIRO“ in Gold. Das Kapitelende-Feld danach wie bisher.

**Allgemein**

21. Alles andere wie in Phase 31. Bitte melden, wenn sich etwas anders anfühlt.
