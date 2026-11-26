# Bilder für GitHub, Modrinth und ModDB

Alle Bilder stammen aus dem Spiel mit SaberCraft. Aufgenommen hat sie der Client-Test `MediaGameTest` in einem virtuellen Bildschirm mit 1920×1080 und Software-Rendering; `tools/crop_screenshots.py` schneidet sie zu (meist den flachen Boden der Testwelt weg) und speichert sie als JPG. Nichts ist hineingemalt oder generiert. Die einzige Retusche: Auf zwei Filmbildern ist der kleine Hinweis „Space/Esc: skip“ auf dem schwarzen Balken geschwärzt. Keine Filmbilder, keine offiziellen Grafiken, keine KI-Bildgeneratoren. Wie man die Bilder neu aufnimmt, steht in [`docs/media/README.md`](../media/README.md).

Die alten Bilder (X-Wing allein, Droiden-Menü, Hangar-Menü mit gesperrten Schiffen, Blaster in der Ich-Ansicht, zwei Klingen, Sturmtruppler im Gang, alte Planeten-, Figuren- und Phasenbilder) sind gelöscht oder liegen nur noch in den Entwicklungsnotizen unter `docs/development/`; keines davon ist auf der Seite verlinkt.

## Grafiken

| Datei | Zeigt | Verwendet in |
| --- | --- | --- |
| `src/main/resources/assets/sabercraft/icon.png` | Mod-Icon 512×512: zwei gekreuzte Klingen vor einem Planeten | Modrinth-Icon, ModDB-Icon, Mod-Menü |
| `docs/media/sabercraft-banner.png` | Banner 1600×500: Icon, Titel, Untertitel, sechs beschriftete Spielbilder (Lichtschwert, X-Wing, TIE Interceptor, Story-Duell, Millennium Falcon, Sternzerstörer) | README oben (EN und DE), Modrinth-Beschreibung, ModDB-Header |
| `docs/media/sabercraft-social-preview.png` | 1280×640, gleicher Aufbau wie das Banner | GitHub → Settings → Social preview |

## Screenshots (`docs/media/screenshots/`)

| Datei | Zeigt | README | Modrinth-Galerie |
| --- | --- | --- | --- |
| `hero-saber.jpg` | Darth Vader mit roter Klinge unter dem Himmel von Mustafar | Einstieg | 01 (Featured) |
| `ships-fleet.jpg` | X-Wing, TIE Fighter, TIE Interceptor, Falcon am Boden, Sternzerstörer am Himmel | Einstieg | 02 |
| `story-duel.jpg` | Duell gegen die Schattenklinge, Klingen gekreuzt (Film mit Untertitel) | Einstieg | 03 |
| `interior-falcon.jpg` | Im Millennium Falcon, Blick nach vorn | Einstieg | 04 |
| `story-disguise.jpg` | In der Rüstung der Truppen, Sturmtruppler gehen vorbei | Story | 05 |
| `sabers-colors.jpg` | Sechs Griffe und Klingenfarben, Himmel von Naboo | Lichtschwerter | 06 |
| `droids.jpg` | Alle acht Droiden-Begleiter | Droiden und Blaster | 07 |
| `planet-tatooine.jpg` | Zwei Sonnen über Tatooine am Abend | Himmel und Musik | 08 |
| `ship-xwing.jpg` | X-Wing in der Kurve über Endor | Raumschiffe | 09 |
| `story-briefing.jpg` | Besprechung in Mission 1: Mira spricht den Spieler mit dem Minecraft-Namen an (im Test „Player0“) | Story | 10 |
| `combat-swing.jpg` | Luke Skywalker schlägt mit grüner Klinge, Leuchtspur, Himmel von Endor | Lichtschwerter | |
| `story-reactor.jpg` | Der Reaktorkern der Tenebris | Story | |
| `story-shadowblade.jpg` | Die Schattenklinge wartet im Hangar (Film mit Untertitel) | Story | |
| `story-finale.jpg` | Blasterschüsse mit der Klinge abgelenkt, auf dem Weg zum Hangar | Story | |
| `story-escape.jpg` | Der Falcon entkommt dem zerbrechenden Sternzerstörer (Film mit Untertitel) | Story | |
| `ship-tie-interceptor.jpg` | TIE Interceptor feuert über Mustafar | Raumschiffe | |
| `ship-tie-fighter.jpg` | TIE Fighter vor Coruscant | Raumschiffe | |
| `ship-falcon.jpg` | Millennium Falcon im Flug über Tatooine | Raumschiffe | |
| `ship-star-destroyer.jpg` | Sternzerstörer tief über Hoth | Raumschiffe | |
| `interior-destroyer-bridge.jpg` | Die Brücke des Sternzerstörers | Raumschiffe | |
| `blaster.jpg` | Blaster-Menü, E-11 in 3D mit Werten | Droiden und Blaster | |
| `character-kenobi.jpg` | Obi-Wan Kenobi mit blauer Klinge, Himmel von Tatooine | Figuren | |
| `menu-saber.jpg` | Lichtschwert-Menü: Griffe, Farben, Kampfstile | Figuren | |
| `planets.jpg` | Collage: Hoth, Endor, Mustafar, Naboo, Coruscant, Weltraum bei Nacht | Himmel und Musik | |
| `gallery-falcon.jpg` | Millennium Falcon am Boden bei Sonnenuntergang | Galerie | |
| `gallery-falcon-cockpit.jpg` | Cockpit des Falcon | Galerie | |
| `gallery-reactor.jpg` | Reaktorschacht des Sternzerstörers | Galerie | |
| `gallery-vader.jpg` | Darth Vader in Abwehrhaltung, Mustafar | Galerie | |
| `gallery-hangar-menu.jpg` | Hangar-Menü mit allen fünf Schiffen | Galerie | |
| `gallery-story-menu.jpg` | Story-Menü: Start, Missionen, Schwierigkeit, Sprache | Galerie | |

Für ModDB dieselben zehn Galeriebilder wie bei Modrinth, gern zusätzlich weitere aus der Liste.

## Was sich nicht automatisch aufnehmen ließ

Diese Aufnahmen fehlen, weil sie einen echten Spieler, eine echte Grafikkarte oder mehrere Spieler brauchen. Sie sind **nicht** auf der Seite eingebaut:

1. **Duell zweier echter Spieler** im Mehrspieler.
2. **Lichtschwerter mit einem Iris-Shaderpack** (in der Testumgebung gibt es keine Shader).
3. **Kurzes GIF oder Video** (5–10 s): Zündung und ein paar Schläge. Modrinth-Galerien nehmen nur Bilder; ein Video kann in der Beschreibung verlinkt werden (z. B. YouTube).
4. **Der Kampf der Ringklinge aus der Nähe**: Im Test geriet die Kamera bei den schnellen Drehungen in Wände oder Truppler; Bilder davon waren unbrauchbar und sind nicht verwendet.
5. **Das Labor mit dem Lichtschwert im Feld**: Im Test zu dunkel, die Klinge war nicht zu erkennen.

Am besten in einem echten Spiel mit **F2** aufnehmen (F1 blendet die Anzeige aus), 16:9, ohne Chat. Auf einer echten Grafikkarte sieht das Spiel schärfer aus als in den Testbildern.

## Regeln für neue Bilder

- Nur eigene Aufnahmen aus Minecraft mit SaberCraft.
- Keine Filmstills, keine Logos von Lucasfilm/Disney, keine Bilder anderer ohne Erlaubnis.
- Keine Bilder aus KI-Bildgeneratoren.
- Dateien unter etwa 1 MB, Namen klein mit Bindestrichen, Eintrag in [`docs/media/README.md`](../media/README.md).
