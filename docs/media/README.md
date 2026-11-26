# Media

Images used by the main [README](../../README.md), the GitHub page and the Modrinth and ModDB texts.

```
docs/media/
├── banner.png          README banner, made by tools/gen_banner.py from three screenshots below
├── social-preview.png  1280×640 image for GitHub's social preview, same script
└── screenshots/        the README screenshots (JPG)
```

## How the screenshots are made

Every screenshot is taken in the game with SaberCraft by the client game test `MediaGameTest` (`src/gametest/java/com/phiro/sabercraft/gametest/MediaGameTest.java`). It only runs when the environment variable `SABERCRAFT_MEDIA` names an output folder; a normal test run skips it. In a 1920×1080 virtual display:

```bash
SABERCRAFT_MEDIA=/tmp/sabercraft-media xvfb-run -a -s "-screen 0 1920x1080x24" ./gradlew runClientGameTest
```

`SABERCRAFT_MEDIA_ONLY=fleet,story` limits the run to some scenes. The scenes are `blades`, `hero`, `combat`, `menus`, `blaster`, `droids`, `fleet`, `flight`, `destroyer`, `interiors`, `planets`, `briefing` and `story`. The test builds each scene in a flat test world (models, skies and story rooms are the mod's own), hides chat, toasts and, where it would cover the picture, the HUD, and saves PNGs at 1920×1080.

`python3 tools/crop_screenshots.py /tmp/sabercraft-media` then crops the PNGs (mostly to cut away the flat test ground) and saves them here as JPG, at most 1600 pixels wide; the script holds the crop of every file. Nothing is painted in or generated; the one retouch is that the small "skip" hint on the black bar of two cutscene pictures is blacked out. The pictures come from software rendering in a virtual display, so the game looks sharper on a real graphics card.

## Current screenshots

| File | Shows | Scene |
| --- | --- | --- |
| `hero-saber.jpg` | Darth Vader with a red lightsaber, Mustafar sky | `hero` |
| `ships-fleet.jpg` | X-Wing, TIE Fighter, TIE Interceptor and Millennium Falcon on the ground, a Star Destroyer above | `fleet` |
| `story-duel.jpg` | Story, Mission 6: the duel against the Shadowblade, blades locked | `story` |
| `interior-falcon.jpg` | Inside the Millennium Falcon, looking forward | `interiors` |
| `sabers-colors.jpg` | Six hilts and blade colors, Naboo sky | `blades` |
| `combat-swing.jpg` | Luke Skywalker swinging a green blade with its trail, Endor sky | `combat` |
| `story-briefing.jpg` | Story, Mission 1: Mira speaks to the player by their Minecraft name (the test account's "Player0") | `briefing` |
| `story-disguise.jpg` | Story: in stormtrooper armour, troopers walk past | `story` |
| `story-reactor.jpg` | Story, Mission 4: the reactor core of the *Tenebris* | `story` |
| `story-shadowblade.jpg` | Story, Mission 6: the Shadowblade waits in the hangar | `story` |
| `story-finale.jpg` | Story, Mission 6: blaster bolts turned with the blade on the way to the hangar | `story` |
| `story-escape.jpg` | Story ending: the Falcon escapes the breaking Star Destroyer | `story` |
| `ship-xwing.jpg` | X-Wing in a turn over Endor | `flight` |
| `ship-tie-interceptor.jpg` | TIE Interceptor firing over Mustafar | `flight` |
| `ship-tie-fighter.jpg` | TIE Fighter in front of Coruscant | `flight` |
| `ship-falcon.jpg` | Millennium Falcon flying over Tatooine | `flight` |
| `ship-star-destroyer.jpg` | Star Destroyer low over Hoth | `destroyer` |
| `interior-destroyer-bridge.jpg` | The bridge of the Star Destroyer | `interiors` |
| `droids.jpg` | All eight droid companions | `droids` |
| `blaster.jpg` | Blaster menu with the E-11 in 3D and its values | `menus` |
| `character-kenobi.jpg` | Obi-Wan Kenobi with a blue blade, Tatooine sky | `hero` |
| `menu-saber.jpg` | Saber menu: hilts, colors, fighting styles | `menus` |
| `planet-tatooine.jpg` | Two suns over Tatooine in the evening | `planets` |
| `planets.jpg` | Collage: Hoth, Endor, Mustafar, Naboo, Coruscant and the night space sky | `planets` |
| `gallery-falcon.jpg` | Millennium Falcon on the ground at sunset | `fleet` |
| `gallery-falcon-cockpit.jpg` | The Falcon's cockpit | `interiors` |
| `gallery-reactor.jpg` | The Star Destroyer's reactor shaft | `interiors` |
| `gallery-vader.jpg` | Darth Vader on guard, Mustafar | `hero` |
| `gallery-hangar-menu.jpg` | Hangar menu with all five ships | `menus` |
| `gallery-story-menu.jpg` | Story menu: start, missions, difficulty, language | `menus` |

## Adding your own screenshots

1. Take the screenshot in game with **F2** (it lands in `.minecraft/screenshots/`). A clean shot: press **F1** to hide the HUD, no other resource packs, a 16:9 window.
2. Save it as JPG (or PNG) in `docs/media/screenshots/` with a short, lowercase, hyphenated name that says what it shows.
3. Keep files under about 1 MB. 1600×900 or 1920×1080 works well on GitHub.
4. Add a row to the table above and reference it in the README.
5. Only use images you made yourself in Minecraft with SaberCraft. No film stills, official artwork, logos, images from AI image generators or images from other people without their permission.

Not in the pictures yet: a duel between two real players, the sabers with an Iris shader pack, and a short ignition clip.

## Visual style

- Dark space background with blue and red blade light, as in the mod icon and banner.
- Captions in the README are short.
- No chat messages, toasts or test overlays in the pictures.
