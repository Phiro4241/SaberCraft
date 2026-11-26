# Phase 33: last gameplay changes and release preparation

Built on `main` after Phase 32 (PR #37, merge commit `57061d5`). No release, no tag, nothing published on Modrinth or ModDB, nothing merged. As before, the server decides everything from vanilla blocks and entities: **no new blocks, items or entity types are registered**, and no new network message. New files: five pieces of music (`story_relic`, `story_relic_take`, `story_reactor`, `story_captured`, `story_epilogue`, made by `tools/synth_music.py` like the others) and the release documents in `docs/release/`.

**Saves.** Old stories load as before. Nothing new is saved.

## 1. The troopers between the saber and the boss

Phiro's playtest: from the lab with the lightsaber to the hangar one could run past all of them unhurt. The causes, measured with the same test on `57061d5` (before) and on this branch (after):

| | Before (57061d5) | After |
| --- | --- | --- |
| A guard at his post, the player steps into view at 9 blocks: first bolt | after 29 ticks | after 5 ticks |
| A guard at his post, the player 8 blocks **behind** him | he turns round and fires (2 bolts) | stays at his post, does not see them |
| Coming out of the lab into the passage: first bolt after being seen | 30 ticks | 16 to 17 ticks |
| The whole way, lab to hangar, run straight through without the saber's guard: first bolt after the player is first seen | 29 ticks | 11 ticks |
| The same run: bolts / hit share | 28 / 54 % | see below |
| Standing on the reactor's lift under fire | the lift did not go (every hit's push counted as stepping off; 7 to 8 hits on the pad) | goes after about 27 ticks |

**What changed** (`Finale.java`, `ShipInteriors.java`):

- **Reaction:** a trooper who sees the player fires after `REACT` = 6 ticks (Normal; the difficulty scales it), round a corner again after at most 6 more. Before, each section began with a wait of 30 ticks and 17 more per trooper (the sixth waited almost six seconds), and a guard at his post waited at least 18. Only the first 20 ticks of a section (`SECTION_GRACE`) and while he still turns towards the player (`AIM_CONE` 35°, turning 24° a tick) he holds his fire.
- **Fields of view:** a guard at his post sees what is before him (75° to each side of his look, up to 22 blocks) and hears the player all round only within 5 blocks; shots or a shout near him (14 blocks, 3 seconds) turn him towards the player at his post.
- **No shots through walls:** besides his eyes' line of sight, the muzzle's own line to the player must be free, or he holds the shot (`heldBack`); a shot aimed ahead of the player only where that line is free too.
- **A little more often, taking turns:** each trooper fires every 16 + up to 11 ticks (was 18 + up to 17), a second shot right after a little more often (0.4, was 0.35); but two troopers fire at least 9 ticks apart (`VOLLEY_GAP`, was 7), so several in the way do not fire all at once.
- **Aim (new):** each shot is first a good one or a near miss. A good one is aimed where the player will be if they keep going (so changing direction or stopping still makes it miss); a miss flies 1 to 1.7 blocks past them, to be heard and seen. Good shots: 55 % at 3 blocks or closer, falling to 15 % from 20 blocks; ×0.8 at a player on the move, ×0.75 more when moving across the line of fire, ×0.7 for the first shot after catching sight of them; divided by the difficulty's scatter (Easy fewer, Hard more). Before, every bolt was aimed at where the player stood: one running across was never hit, one running at them nearly always.
- **The reactor's lift** (Phase 28's slow lift, stand still 30 ticks): a hit's push (`player.hurtTime`) no longer counts as stepping off.

**The run straight through** (Phase33GameTest, part `path`): from the saber's field out of the lab, round the shaft to the lift, up, across the hangar deck to the duel, at a run (0.26 blocks a tick), never stopping, never raising the saber's guard, never striking, with natural regeneration off. It is the worst case: a player who uses the guard (it sends bolts from the front back), cover or the saber is hit far less. Several runs after the last change:

RESULTS_TABLE

Most hits on the hangar deck come from troopers the way passes at 1 to 5 blocks. The test checks: the player reaches the duel; at least 8 bolts, at least 3 hits and 6 damage on the way; the way from the lab to the lift costs at least 3 and less than 20 (a full health bar: hurt there, not caught); at most 65 % of bolts hit; from 8 blocks or further at most half; the first bolt at most 30 ticks after the player is first seen; no bolt without a free line to the player; no bolt while no trooper sees them; the lift goes under fire within 50 ticks.

Not changed: the bolt's damage (3, an officer's pistol 2.4), natural regeneration, the boss and her fight (Phase 32), what happens when the player is caught. The player is **not** healed when the duel begins (a player who arrives hurt fights hurt; caught in the duel, they start it again with full health at its checkpoint). If that turns out too hard in playtests, healing at the duel's start would be a one-line change.

## 2. The player's own name instead of "Funke"

The story called the player "Funke" (English "Spark"). Every text that speaks to the player or names them now holds `{player}` (14 texts in each language: Mira's briefing, Kestrel's recording and her words in the hideout, radio messages, the dialog name over the player's own lines, Dax after the escape, the epilogue). The client's language layer (`SaberLanguage`, Phase 19) puts in **the player's own Minecraft name** when the text is shown (the account's name before a world is joined), so in a game of several everyone reads their own name; the server only ever sends the text's key. Other uses of "Funke(n)" (sparks of the blade in the settings and a title-screen splash) are not names and stay.

Tested with the real client (its name in the test is `Player0`) and with other names through `SaberLanguage.named` ("Max", "Lena"). **Not tested:** two real clients with different names on one server at the same time (the test environment runs one client).

## 3. Music

New pieces (all original, synthesized by `tools/synth_music.py`, no film music):

| Piece | Where | Character |
| --- | --- | --- |
| `story_relic` | the lab under the reactor while the saber is still in its field (Mission 6), from the moment the player steps in | mysterious first (low drone, a choir holding its breath, crystal pings), then the saber's figure in a lone horn, then majestic (brass, choir, timpani) into D major, sinking back to loop. D minor, 60 bpm |
| `story_relic_take` | the film where the player takes the saber (from its start) | the figure over a growing roll, a D major chord as the hand closes on the hilt, two dark blows as the field breaks and the core flares, a racing pulse into the escape |
| `story_reactor` | down at the reactor (Missions 4 and 6, below the hangar deck) | a machine pulse in 3+3+2 over a dark drone, relay ticks, a warning bell now and then; soft enough to sneak to (sits low like the sneaking music, the danger's moods lay themselves over it). F phrygian, 96 bpm |
| `story_captured` | the hideout after Kestrel's capture (Mission 5) | sorrow with a clock under it: held low strings, a cello line, a soft tick, a heartbeat on the timpani. G minor, 70 bpm |
| `story_epilogue` | the way home in the Falcon's lounge (it was the hideout's piece) | the saber's figure warm in the flute in D major over harp and strings, a shadow of the lab's mystery in the middle. 66 bpm |

- The lab's piece sets in the moment the player steps through its door (the story asks for its music twice a second now, was once).
- Pieces that were given way before the sound engine started them no longer stay on the fading list (`MusicPiece.idle`); leaving the story forgets the danger of before (no calming-down piece from an old chase when coming back).
- Checked by the test: one change into the lab within 30 ticks with a crossfade of at least 30 ticks, the old piece fading; never more than one piece audible after the fade (lab, after taking the saber, epilogue); the right piece for the reactor, the lab, the saber film, the escape, Kestrel's capture and the epilogue; no old piece after a scene change.

## 4. Public pages and release preparation

- `README.md` and `README.de.md` rewritten: what the mod is, the story mode and its six missions, how it plays, features with status labels (stable / experimental), real screenshots from the game, requirements, installation and client/server table, controls, commands, configuration, known limitations, links, support (Patreon placeholder), building, license and the fan-project notice.
- Banner and social preview regenerated with the current subtitle; four story screenshots in `docs/media/screenshots/` (all taken in the game by the tests).
- Internal notes (phase status, plans) moved to `docs/development/`; links in the code and docs updated.
- `docs/release/`: GitHub settings, Modrinth and ModDB page content, Patreon section, release notes 0.1.0, installation guide, image plan. Nothing published.
- `fabric.mod.json` description: no "Star Wars for Minecraft" any more, mentions the story and that it is an unofficial fan mod.

Open points that only Phiro can settle are listed in [`docs/release/README.md`](../release/README.md): the exact Patreon URL, the Modrinth AI disclosure (Modrinth may reject projects made mostly with generative AI), the Patreon link next to a Star Wars fan mod.

## 5. Tests

New: `Phase33GameTest` with five parts (`names`, `music`, `post`, `path`, `cover`; `PHASE33_ONLY=<parts>` runs only some). Changed: `Phase30GameTest` expects the new shot cooldown (16 + 12). Links to moved docs updated in the tests of Phases 25 to 32.

TEST_RESULTS

## 6. Please check by hand

1. Mission 6 from the lab to the hangar, once without using the saber and once with the guard (R): does the way feel dangerous but fair? Are near misses visible and audible?
2. Stand on the reactor's lift while troopers fire: it should still go.
3. The lab's music the moment you step in; the saber film; the switch to the escape.
4. Mira's briefing, Kestrel's lines and radio messages: your own name. If possible, on a server with a second player.
5. The boss fight as in Phase 32 (unchanged).
