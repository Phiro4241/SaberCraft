# Phase 10: Star Wars Ultimate Expansion

Stand: 25.10.2026, Minecraft 26.2, Fabric Loader 0.19.5, Fabric API 0.161.0+26.2, Java 25. **Keine neuen Abhängigkeiten.** Iris und Sodium bleiben optional.

Alles aus Phase 1 bis 9 bleibt erhalten: Klinge, Zünden und Summen, alle Griffe, Farben und Kampfstile, Ausweichen, Abwehr, Machtkräfte, Weltraumhimmel, alle Charaktere und Outfits, beide Musikmodi und das Menü (Taste V). Die Tests aus Phase 1 bis 9 laufen alle weiter mit (siehe [Tests](#tests)).

**In der JAR ist keine Filmmusik, kein Film-Sound und keine geschützte Grafik.** Die zwei neuen Musikstücke, die sechs Planeten-Klänge, alle Blaster- und Schwertgeräusche sind von `tools/synth_music.py` und `tools/synth_sounds.py` berechnet. Griffe und Blaster sind eigene Würfelmodelle aus `tools/gen_hilts.py` und `tools/gen_blasters.py`. **Gelände und Weltgenerierung bleiben unverändert**, die Planeten ändern nur den Himmel, das Licht der Sonne und des Mondes und den Klang.

Die Arbeit folgt deiner Reihenfolge. Jede Priorität ist ein eigener Commit mit eigenem Test.

| Priorität | Ergebnis | Commit | Test |
| --- | --- | --- | --- |
| 1 Skin-Layer-Fehler | behoben an der Ursache | `d55bca0` | `SkinLayerGameTest` |
| 2 `/sabercraft convert` | fertig | `173863d` | `ArsenalGameTest` |
| 3 Blaster | sechs Blaster, fertig | `173863d` | `ArsenalGameTest` |
| 4 Ezra Bridger | fertig | `7a7689b` | `EzraGameTest` |
| 5 Planeten-Himmel, Sonne, Mond, Shader | fertig, Shader-Grenzen gemessen | `cd019fd` | `PlanetSkyGameTest`, `PlanetVisibilityGameTest` |
| 6 Schwerttypen, Zündung, Clash, Profile | fertig | `97831a4` | `SaberTypesGameTest` |
| 7 Musik-Situationen, Planeten-Klänge | fertig | `eecd509` | `SoundscapeGameTest` |
| 8 Duelle | fertig | `32d6d7f` | `DuelGameTest` |
| 8 Macht-Energie, Fahrzeuge, Droiden | **nicht gebaut**, als Folgephasen geplant | `32d6d7f` | – ([folgephasen.md](folgephasen.md)) |
| 9 Einstellungen nach Neustart | fertig | `28be52b` | `SettingsGameTest` |

## 1. Skin-Layer-Fehler

**Ursache:** Charaktere und Outfits wurden bisher als zweiter Körper über den eigenen Skin gezeichnet. Was von der zweiten Schicht des eigenen Skins (Hut, Jacke, Ärmel, Hosenbeine) nicht absichtlich ausgeblendet war, blieb sichtbar, dazu der eigene Umhang hinter jedem Charakter und der eigene Ärmel in der Ego-Perspektive.

**Behebung:** Kein Überdecken mehr. Das `PlayerSkin`, das Minecraft für den Spieler abfragt (`AbstractClientPlayer.getSkin`), ist jetzt das des Charakters: seine Textur, sein Modell (breit), kein Umhang, keine Elytra-Textur des Spielers. Damit zeichnet Minecraft genau einen Körper mit beiden Schichten des Charakters. Die alte Überlagerungsschicht ist entfernt.

**Test:** `SkinLayerGameTest` stellt den Fehler nach: ein Skin mit knallmagenta zweiter Schicht und magenta Umhang. Vor der Behebung zeigten die Bilder bis zu 33 000 magenta Pixel, jetzt keinen einzigen. Geprüft für jeden Charakter und jedes Outfit, breite und schmale Skins, schnelles Wechseln, einen Skin ohne zweite Schicht und die Hut-Option, in Ego-Perspektive und Third Person.

## 2. `/sabercraft convert` und 3. Blaster

| Befehl | Wirkung |
| --- | --- |
| `/sabercraft convert [farbe] [griff]` | Das Schwert in der Hand (Holz bis Netherite) wird zum Lichtschwert. Ohne Angaben passt der Griff zum Material, Netherite wird rot, die anderen blau. |
| `/sabercraft revert` | Macht das Item wieder genau zu dem, was es war (Name, Verzauberungen, Haltbarkeit bleiben). |
| `/sabercraft blaster [typ]` | Der Bogen in der Hand wird zum Blaster: `e11`, `dl44`, `a280`, `repeater`, `rebel`, `ezra`. Auf einem Blaster wechselt der Befehl den Typ. |
| `/sabercraft blaster revert` | Blaster wird wieder zum Bogen. |

Blaster schießen per Rechtsklick echte Bolzen vom Server: Schaden, Hitze, Überhitzung, Betäubung (Ezra), Bogen-Verzauberungen wirken. Spieler ohne Mod können weiter auf den Server, Bolzen kommen bei ihnen als Schneebälle an.

Server-Einstellungen in `config/sabercraft/abilities.json`: `commands_for_everyone` (Standard `true`, sonst nur Operatoren), `blasters_enabled`, `blaster_damage`, `blaster_heat`, `blaster_cool_rate`, `blaster_vent_rate`.

## 4. Ezra Bridger

Ezra (Rebels, Staffel 1 und 2) ist im Charaktermenü: eigener Skin, grauer Griff mit orangen Bändern und Klappgriff, blaue Klinge, neuer Kampfstil **Improvisiert** (schnelle einhändige Hiebe, Rutschen, Radschlag, Rollen, Rückwärtssalto, schwache Abwehr, günstiges Ausweichen). Rechtsklick mit seinem Griff feuert den eingebauten Betäubungsblaster, auch mit gezündeter Klinge.

## 5. Planeten: Himmel, Sonne, Mond, Shader

- Sonne und Mond je Planet in eigener Größe und Farbe, Tatooine mit zweiter Sonne (`planet_celestials`, Menü Himmel > „Planet sun and moon“).
- Dritte Reihe in der Horizont-Landschaft, Himmelsqualität „Ultra“ mit 1536 Pixeln.
- Weicher Übergang beim Planetenwechsel (`planet_transition_seconds`).

### Shader-Untersuchung (gemessen mit Iris 1.11.4)

Gemessen wird der mittlere Farbabstand zwischen „kein Planet“ und „Planet“ im gleichen Bild (0 = keine Änderung). Rohdaten: `planet_visibility_iris.csv`.

| Pack | Himmel mit Tönung | Horizont | Himmel ohne Tönung | Nacht |
| --- | --- | --- | --- | --- |
| Complementary Reimagined r5.9.3 | etwa 6 bis 50 | etwa 51 bis 75 | etwa 0,1 bis 1,7 | – |
| BSL v10.1.8 | etwa 17 bis 26 | etwa 15 bis 85 | – | etwa 2 bis 7 |

**Ursache bei Complementary:** Der Shader `gbuffers_skytextured` quadriert die Farbe eigener Himmelstexturen und blendet sie zum Horizont auf Schwarz aus (`color.rgb *= color.rgb * smoothstep(VdotU)`). Die Sonnenfarbe ersetzt er durch seine eigene. Dafür gibt es keine Pack-Option. Die Tönung aus Phase 9 bleibt deshalb der Weg, der wirkt.

**Ursache bei BSL:** `SKYBOX_OPACITY` steht standardmäßig auf 0,5, und `SKY_DESATURATION` entfärbt die Nacht. Beides lässt sich in den Pack-Einstellungen ändern (Opacity hoch, Desaturation aus).

Eine echte Lösung für Complementary wäre, den Horizont als ferne Geometrie zu zeichnen, die das Pack wie Gelände beleuchtet. Das steht als Folgearbeit in [folgephasen.md](folgephasen.md).

## 6. Schwerttypen, Zündung, Clash, Profile

**Griffe spielen sich unterschiedlich** (Server, `hilt_traits`, Standard an):

| Griff | Schaden | Abwehr | Parade-Fenster | Rückstoß | Ausweichen |
| --- | --- | --- | --- | --- | --- |
| Kreuzparier | ×1,12 | +6 % | 1 Tick kürzer | +0,12 | Abklingzeit ×1,15 |
| Yoda | ×0,88 | – | 1 Tick länger | – | Abklingzeit ×0,8 |
| Dooku (gebogen) | – | – | 3 Ticks länger | – | – |
| Kenobi | – | +6 % | 2 Ticks länger | – | – |
| Doppelklinge | – | +8 % | – | – | – |

Die Werte stehen im Tooltip und in der Beschreibung des Griffs im Menü.

**Eigene Zündungen:** Kreuzparier stottert beim Zünden (instabil), die Doppelklinge zündet erst die eine, dann die andere Seite, Yoda und Ahsoka zünden schnell, Kylos Seitenklingen kommen nach der Hauptklinge.

**Clash:** Trifft ein Schlag auf eine Abwehr oder Parade, sprühen die Funken an der Stelle, wo die Klingen sich treffen, in beiden Farben, und beide Klingen leuchten kurz auf.

**Eigene Profile:** JSON-Dateien in `config/sabercraft/profiles/` (Name, Skin, Basis-Charakter, Griff, Farbe, Stil, Seite, Größe). Anleitung: [custom-profiles.md](custom-profiles.md).

## 7. Musik-Situationen und Planeten-Klänge

- Nach einem gewonnenen Kampf spielt einmal eine Sieges-Fanfare (13 s), wenn du fällst ein Klagelied (26 s), danach geht die Musik zurück zur Lage. Schalter `music_stingers` im Musikmenü.
- Star-Wars-Modus: neuer Ordner `defeat/` für deine eigene Musik beim Sterben.
- Jeder Planet hat einen eigenen Umgebungsklang (Tatooine Wüstenwind, Hoth Schneesturm, Mustafar Lava, Endor Wald, Naboo See, Coruscant Stadt). Unter einem Dach wird er auf ein Viertel leiser, beim Planetenwechsel blendet er über. Schalter `planet_ambient` und Lautstärke `planet_ambient_volume` im Himmelsmenü; er läuft zusätzlich über den Regler „Umgebung“ von Minecraft.

## 8. Duelle

Nur freiwillig: Niemand wird angegriffen oder zum Kampf gezwungen.

| Befehl | Wirkung |
| --- | --- |
| `/sabercraft duel <spieler>` | Fordert heraus. Der andere bekommt eine Chatnachricht mit [Annehmen] und [Ablehnen]. Die Herausforderung verfällt nach 60 Sekunden. |
| `/sabercraft duel accept [von]` / `decline [von]` | Annehmen oder ablehnen. |
| `/sabercraft duel leave` | Duell verlassen. |
| `/sabercraft duel ignore` | Herausforderungen nicht mehr empfangen (noch einmal: wieder empfangen). |

Ablauf: Countdown 3, 2, 1 (in dieser Zeit kein Schaden zwischen den beiden), Best of three. Ein tödlicher Treffer des Gegners tötet nicht, er beendet die Runde. Das Duell endet bei zwei Siegen, beim Verlassen, Ausloggen, Tod durch etwas anderes, mehr als 64 Blöcken Abstand oder wenn der Server `duels_enabled` ausschaltet. Nur Chat und Titel aus Vanilla, Spieler ohne Mod sehen alles.

Macht-Energie, Fahrzeuge und Droiden sind **nicht gebaut**. Sie sind zu groß, um sie mit vollständigen Tests in dieser Phase zu liefern. Ausgearbeitete Pläne: [folgephasen.md](folgephasen.md).

## 9. Einstellungen

Alle neuen Einstellungen stehen im Menü (V) und in den Dateien unter `config/sabercraft/`:

| Datei | Neu in Phase 10 |
| --- | --- |
| `abilities.json` (Server) | `hilt_traits`, `duels_enabled`, `commands_for_everyone`, `blasters_enabled`, `blaster_damage`, `blaster_heat`, `blaster_cool_rate`, `blaster_vent_rate` |
| `immersion_client.json` | `planet_celestials`, `planet_transition_seconds`, `music_stingers`, `planet_ambient`, `planet_ambient_volume`, Qualität 3 (Ultra) in `space_sky_quality` |
| `character_client.json` | Profilwahl `profile:<name>` in `outfit` |
| `profiles/*.json` | eigene Profile |

`SettingsGameTest` klickt im echten Menü, liest danach die Datei und lädt sie neu wie beim Spielstart. Alles bleibt so, wie gewählt.

## Tests

Alle Tests laufen im echten Spielclient (Fabric Client Gametest) unter Xvfb mit llvmpipe (Software-Rendering).

| Lauf | Ergebnis |
| --- | --- |
| Alle 21 Client-Gametests ohne Iris (Phase 1 bis 10, Endstand `28be52b` plus Doku) | **1092 Prüfungen grün**, Exit 0, 24 Minuten |
| `ShaderBladeGameTest` und `PlanetVisibilityGameTest` mit Iris 1.11.4 + Sodium 0.9.2, Complementary r5.9.3 und BSL v10.1.8 | **57 Prüfungen grün**, Iris meldet beide Packs als geladen |
| `SkinLayerGameTest` im selben Client mit Iris und Sodium, Shaderpack dabei **aus** | **287 Prüfungen grün** |

Neu in Phase 10 (Prüfungen im Einzellauf):

- `SkinLayerGameTest`: Skin-Fehler nachgestellt, alle Charaktere und Outfits, breit und schmal, Ego und Third Person.
- `ArsenalGameTest`: Umwandeln jedes Schwerts, Daten bleiben erhalten, jeder Blaster per echtem Rechtsklick, Überhitzung, Zurückwandeln, normale Bögen, Dedicated Server.
- `EzraGameTest`: Menüwahl, Schwert, Griff-Blaster, Kampfstil.
- `PlanetSkyGameTest` (37): Sonne und Mond je Planet, zweite Sonne, Übergänge, Ultra-Qualität.
- `SaberTypesGameTest` (42): Griff-Eigenschaften auf dem Server, Zündungen, Clash-Funken an der Kontaktstelle, eigene Profile.
- `SoundscapeGameTest` (26): Sieg, Niederlage, `defeat/`-Ordner, Planeten-Klänge drinnen und draußen, Schalter und Lautstärke.
- `DuelGameTest` (35): sich selbst herausfordern geht nicht, Ignorieren, Herausforderung und Annehmen, Ablehnen, Countdown ohne Schaden, Runden bis zum Sieg, Ende bei 100 Blöcken Abstand, beim Abschalten und bei Tod durch etwas anderes, außerhalb eines Duells ändert sich nichts. Das Verfallen nach 60 Sekunden und das Ausloggen sind gebaut, aber nicht im Test.
- `SettingsGameTest` (9): echte Klicks im Menü, Datei, Neuladen.

Die Shaderpacks wurden nur bei Klinge und Planeten-Himmel geprüft. Skin, Blaster, Clash und Duell mit aktivem Shaderpack sind nicht getestet.

Was nicht getestet wurde:

- Andere Shaderpacks als Complementary r5.9.3 und BSL v10.1.8, andere Iris-Versionen als 1.11.4.
- Eine echte Grafikkarte.
- Ein Mehrspieler-Server mit mehreren echten Menschen. Duelle sind mit einem zweiten, serverseitigen Testspieler (Fabric `FakePlayer`) geprüft. **Solche Testspieler schickt der Server nicht an den Client**, deshalb ist der Gegner auf den Duell-Screenshots nicht zu sehen. Chat, Titel, Schaden und Rundenzählung sind trotzdem echt geprüft. Blaster und Befehle sind zusätzlich auf einem echten Dedicated Server geprüft (`ArsenalGameTest`).
- Echte Filmmusik (Tests nutzen eigene Sounds der Mod als Platzhalter-Dateien).
- Ein Spieler ohne Mod, der auf den Server kommt. Die Wege dafür (Bolzen als Schneebälle, nur Vanilla-Chat und -Titel) sind so gebaut, aber nicht mit einem Vanilla-Client ausprobiert.

## Bekannte Grenzen

- **Shaderpacks:** siehe Messung oben. Mit Complementary bleibt der Planetenhimmel blass, BSL zeigt nachts wenig.
- Macht-Energie, Fahrzeuge und Droiden fehlen (Pläne in [folgephasen.md](folgephasen.md)).
- Die Griff-Eigenschaften sind bewusst klein gehalten. Die Balance ist nicht mit echten Spielern ausprobiert, `hilt_traits: false` schaltet sie ab.
- Duelle haben noch keine Arena-Grenze, Zuschauer-Anzeige oder Rangliste.
- In der Cloud-Testumgebung fehlen einige Vanilla-Texturen und Sounds (lila Platzhalter auf manchen Screenshots). Im echten Spiel nicht.

## Installation und Test

1. `sabercraft-0.1.0.jar` (Phase 10) und Fabric API 0.161.0+26.2 in `.minecraft/mods/` legen. Alte SaberCraft-JARs entfernen.
2. **Skin:** Einen Charakter wählen (V > Charakter), F5. Von deinem eigenen Skin, Hut oder Umhang ist nichts mehr zu sehen.
3. **Umwandeln:** Ein Eisenschwert in die Hand, `/sabercraft convert green`. Zurück mit `/sabercraft revert`.
4. **Blaster:** Einen Bogen in die Hand, `/sabercraft blaster dl44`, Rechtsklick. Dauerfeuer mit `repeater` bis zur Überhitzung.
5. **Ezra:** V > Charakter > Ezra Bridger, Rechtsklick mit dem Griff für den Betäubungsschuss.
6. **Planeten:** V > Himmel > Planet Tatooine, tagsüber zur Sonne schauen (zwei Sonnen). Hoth für den Schneesturm-Klang, unter einem Dach wird er leiser.
7. **Clash:** Mit einem zweiten Spieler oder einem Mob mit Schwert: R halten, wenn der Schlag kommt.
8. **Duell:** `/sabercraft duel <name>`, der andere klickt [Annehmen].
