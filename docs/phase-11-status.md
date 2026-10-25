# Phase 11: Blaster, Droiden, Raumschiffe und Immersion

Stand: 25.10.2026, Minecraft 26.2, Fabric Loader 0.19.5, Fabric API 0.161.0+26.2, Java 25. **Keine neuen Abhängigkeiten.** Iris und Sodium bleiben optional.

Alles aus Phase 1 bis 10 bleibt erhalten: Schwerter, Charaktere, Planeten, Musik, Shader-Klinge, Duelle und die Behebung der zweiten Skin-Ebene. Die Tests aus Phase 1 bis 10 laufen alle weiter mit, darunter `SkinLayerGameTest` (siehe [Tests](#tests)).

**In der JAR ist keine Filmmusik, kein Film-Sound und keine geschützte Grafik.**

- **Klänge:** Alle neuen Klänge berechnet `tools/synth_sounds.py`: Blaster, Droiden-Stimmen (Piepen, Pfeifen und Murmeln ohne Sprache), X-Wing-Triebwerk, Laser, Absturz, S-Flügel, Ziehen, Wegstecken und das Menü-Rauschen.
- **Modelle und Texturen:** Sie sind eigene Würfelmodelle aus `tools/gen_blasters.py`, `tools/gen_droids.py` und `tools/gen_ships.py`.
- **Icon und Erfolge:** `tools/gen_icon.py` zeichnet das Mod-Icon, `tools/gen_advancements.py` erzeugt die Erfolge.

| Schritt | Ergebnis | Commit | Test |
| --- | --- | --- | --- |
| A Blaster und Blaster-Menü | fertig, 9 Blaster mit eigenen 3D-Modellen | `338e106` | `BlasterAmmoGameTest`, `ArsenalGameTest` |
| B Pfeile als Munition | fertig | `338e106` | `BlasterAmmoGameTest` |
| C Mod-Icon und Erfolge | fertig, 11 Erfolge in eigenem Reiter | `918e40f` | `AdvancementGameTest` |
| D Treffer-Spuren und Finisher | fertig | `22d3a5e` | `SaberHitGameTest` |
| E Droiden und Droiden-Menü | fertig, 8 Droiden | `bfe5631` | `DroidGameTest` |
| F X-Wing fliegbar | fertig | `274ad24` | `ShipGameTest` |
| F TIE Fighter, TIE Interceptor, Falcon, Sternzerstörer | **geplant, nicht gebaut**, im Hangar als „Geplant“ | `274ad24` | – ([phase-11-starships.md](phase-11-starships.md)) |
| G Ziehen und Wegstecken, Menü-Übergänge | fertig | `274ad24` | `AtmosphereGameTest` |

## A und B: Blaster und Munition

- **Blaster:** Neun Blaster sind als detaillierte 3D-Modelle neu gebaut: DL-44, E-11, DC-15A, SE-14C, Bowcaster, A280, Repetierblaster, Rebellen-Blaster und Ezras Blaster. DC-15A, SE-14C und Bowcaster sind neu, mit eigenen Werten, Reichweiten und Klängen.
- **Mündungsfeuer und Rückstoß:** Mündungsfeuer in Bolzenfarbe, das jeder Spieler sieht, dazu Rückstoß und Abkühlhaltung beim Überhitzen.
- **Blaster-Menü (V mit Bogen in der Hand):** Galerie, drehbare 3D-Vorschau und Werte. Umwandeln und Zurückwandeln prüft der Server.
- **Munition:**
  - Jeder Schuss kostet einen normalen Pfeil, zuerst aus der Nebenhand. Getränkte und Spektralpfeile zählen nicht.
  - Unendlichkeit behält den Pfeil, wenn der Server das erlaubt (`blaster_infinity`). Im Kreativmodus braucht es keine Pfeile.
  - `blaster_ammo: false` schaltet die Munition ganz ab.
  - Ohne Pfeil gibt es einen trockenen Klick und einen Hinweis. Die Anzeige zählt die Pfeile.

## C: Mod-Icon und Erfolge

Das neue Icon zeigt gekreuzte Klingen, einen Planeten und Sterne. Es gibt **11 Erfolge** in einem eigenen Reiter mit Sternenhintergrund. Der Server vergibt sie bei den echten Ereignissen:

- Schwert in der Hand und erster Blasterschuss.
- Treffer über 30 Blöcke und zwei Gegner mit einer Drehung.
- Alle sieben Fähigkeiten genutzt.
- Duellbeginn, Duellsieg auf der dunklen Seite und Finisher.
- Erster Droide, treuer Begleiter und erster Flug („Rot Fünf, bereit“).

## D: Treffer-Spuren und Finisher

Ein Schwerttreffer hinterlässt eine kurz glühende Spur. Es sind höchstens sechs pro Ziel, und nach vier Sekunden sind sie weg.

Der entscheidende Schlag ist ein **Finisher**:

- **Effekt:** Blitz und Schockring in Klingenfarbe, eigener Klang.
- **Zeitlupe:** kurz, nur im Einzelspieler.
- **Abklingzeit:** pro Spieler, einstellbar. Schalter im Kampf-Menü.

## E: Droiden

Es gibt acht Droiden: R2-D2, C-3PO, BB-8, Chopper, Maus-Droide, Sonden-Droide, B1 und K-2SO. Jeder hat eigene bewegte Teile, eine Stimme und eine Fähigkeit.

- **Steuerung:** Im Menü-Reiter „Droiden“ und mit `/sabercraft droid` lassen sie sich rufen, zurückrufen, warten lassen, folgen lassen und nach Hause schicken.
- **Server:** Er prüft jeden Befehl, hält ein Limit pro Spieler ein und erzeugt nie zwei gleiche Droiden.
- **Nach einem Neustart:** Die Droiden kommen wieder.

## F: X-Wing

Aufbau, Steuerung, Laser, Mehrspieler und der Plan für die weiteren Schiffe stehen in **[phase-11-starships.md](phase-11-starships.md)**. Kurz:

- **Rufen:** im Hangar (V > Schiffe) oder mit `/sabercraft ship call|board|dismiss`. Nur der Besitzer fliegt sein Schiff.
- **Steuerung:**

  | Taste | Wirkung |
  | --- | --- |
  | W / S | Schub und Bremse |
  | A / D | drehen |
  | Leertaste / Sprinten | Nase hoch und runter |
  | linke Maustaste | Laser |
  | Schleichen | aussteigen, in der Luft mit Sanftem Fall |

- **Laser:** vier Kanonen an den Flügelspitzen, sichtbare rote Bolzen, Mündungsblitz bei allen Zuschauern, Feuerrate und Reichweite begrenzt. Kein Blockschaden.
- **Kollision:** Rauch, Funken und ein Knall, aber kein Schaden an Blöcken, Schiff oder Pilot.
- **Darstellung:**
  - Das Schiff legt sich in Kurven, die S-Flügel öffnen sich in der Luft.
  - Triebwerk und Flügel haben eigene Klänge.
  - Eine Fluganzeige zeigt Geschwindigkeit, Höhe, Laser und Tasten.
  - Die Kamera wechselt hinter das Schiff.
- **Speichern:** Wer im Cockpit ausloggt, sitzt beim Einloggen wieder darin.

Neue Server-Einstellungen in `config/sabercraft/abilities.json`:

| Einstellung | Standard | Bereich |
| --- | --- | --- |
| `ships_enabled` | true | |
| `ship_speed` | 1.0 | 0,2 bis 2,0 |
| `ship_laser_damage` | 1.0 | 0 bis 10 |
| `ship_lasers_hit_players` | true | |

## G: Atmosphäre

- **Blaster ziehen:**
  - Ein Blaster, den du in die Hand nimmst, schwingt mit der Mündung nach unten hoch und rollt in den Anschlag, mit einem Klick.
  - Wegstecken hat einen eigenen Klang.
  - Ein Wechsel von Blaster zu Blaster zieht den neuen, ohne wegzustecken. Beim Betreten der Welt gibt es keinen Klang.
- **Menü-Übergänge:** Jede Seite des SaberCraft-Menüs blendet in 0,22 Sekunden ein, mit einer Scanlinie und einem leisen Rauschen.
- **Grafik-Menü:** Es hat vier neue Schalter: „Blaster ziehen“, „Menü-Übergänge“, „Schiffskamera hinten“ und „Fluganzeige“.
- **Droiden und Schiffe:** Die Klänge kamen schon mit E und F.
- **Schwerter:** Für sie gibt es seit Phase 4 Zünden und Einfahren.

## Tests

Hier ist streng getrennt, wie etwas geprüft wurde.

### 1. Automatisierte Tests (ausgeführt)

Alle laufen als Fabric Client Gametests: Ein echter Minecraft-Client startet unter Xvfb mit llvmpipe (Software-Rendering). Die Tests drücken echte Tasten und Maustasten, klicken Menü-Knöpfe und machen Screenshots. **Gesteuert wird das von einem Skript, nicht von einem Menschen.**

| Lauf | Ergebnis |
| --- | --- |
| Alle 26 Testklassen ohne Iris (Phase 1 bis 11 mit X-Wing, vor G) | **1343 Prüfungen grün**, Exit 0, 31 Minuten |
| Nach G: `AtmosphereGameTest`, `ShipGameTest`, `BlasterAmmoGameTest`, `CharacterGameTest`, `SettingsGameTest`, `SkinLayerGameTest` | **497 Prüfungen grün**, Exit 0 |
| GitHub CI (Build und alle Client-Gametests) | für `eeedcd5` grün, für `274ad24` siehe PR |

Neu in Phase 11:

- **`BlasterAmmoGameTest`:** Munition per echtem Rechtsklick, Unendlichkeit, Kreativmodus, Schnellklicks, Repetierblaster, Blaster-Menü (umwandeln, wechseln, zurück zum exakt gleichen Bogen) und dedizierter Server.
- **`AdvancementGameTest`:** echte Ereignisse, keine doppelte Vergabe, Fortschritt nach dem Neuladen der Welt, Mod-Icon.
- **`SaberHitGameTest`:** Treffer-Spuren, Finisher und Abklingzeit.
- **`DroidGameTest`:** alle Droiden, Befehle, Limit, Rückkehr nach dem Neuladen und dedizierter Server.
- **`ShipGameTest`:**
  - Hangar: geplante Schiffe sind gesperrt, auch auf dem Server.
  - Einsteigen per Rechtsklick und Kamera.
  - Flug: Abheben, Flügel, Schub, Höchstgeschwindigkeit, Erfolg, Kurven, Neigen, Steigen und Bremsen.
  - Laser: fünf Schüsse in einer Sekunde treffen zwei Ziele, das Glas bleibt heil.
  - Absturz: Die Mauer bleibt heil.
  - Aussteigen mit Sanftem Fall, das Schiff landet und klappt die Flügel ein.
  - Ein normales Schwein bleibt ein Schwein.
  - Welt neu laden: Der Spieler sitzt wieder im Cockpit, mit genau einem Schiff.
  - Dedizierter Server: Flug, Position von Client und Server unter 5 Blöcken Abstand, das Schiff geht mit dem Spieler.
- **`AtmosphereGameTest`:** Ziehen, Wechseln und Wegstecken über die echten Hotbar-Tasten, Schalter aus, Menü-Übergänge an und aus.

### 2. Im Spiel angesehen

Alle Screenshots dieser Tests sind angesehen und auf Fehler geprüft: Hangar, Cockpit, Flug, Laser, Absturz, gelandetes Schiff, Ziehen in drei Stufen, Grafik-Menü und Menü-Übergang. Dabei sind drei Fehler aufgefallen und behoben:

- Die Neigung in der Kurve war spiegelverkehrt.
- Die Laser kamen von unten statt von den Flügelspitzen.
- Das Ziehen drehte den Blaster aus dem Bild.

**Ein Mensch hat Phase 11 nicht von Hand gespielt.** Ein echtes Spielgefühl gibt es also noch nicht: Flugsteuerung mit der Maus, Balance der Blaster und Lautstärke der neuen Klänge im Mix.

### 3. Nicht getestet

- **Mehrspieler mit zwei echten Clients:** Ein zweiter Spieler sieht ein fremdes Schiff fliegen, feuern und sich neigen, ein Spieler trifft einen anderen mit Schiffslasern, und Droiden zweier Spieler begegnen sich. Gebaut ist es so, dass alle Zuschauer Schüsse und Bewegung bekommen. Geprüft ist nur ein Client, auch auf dem dedizierten Server.
- **Fremdes Schiff:** Ein anderer Spieler versucht, es zu besteigen. Der Server lehnt das im Code ab, ein Test mit zweitem Spieler fehlt.
- **Iris und Shaderpacks mit Phase 11:** Blaster-Modelle, Droiden, X-Wing, Mündungsfeuer, Menü-Übergänge. Alles nutzt die normalen Zeichenwege für Items und Gegenstände (keine eigenen Shader). Die Klinge mit Shadern aus Phase 10 ist unverändert, gemessen ist es aber nicht.
- **Spieler ohne Mod auf dem Server:** Sie sollten Schweine statt Droiden und Schiffe sehen und Schneebälle statt Bolzen. So ist es gebaut, mit einem Vanilla-Client ausprobiert ist es nicht.
- **Eine echte Grafikkarte**, andere Bildschirmgrößen als 1280×720 und Linkshänder-Einstellung beim Ziehen.

## Bekannte Grenzen

- **Schiffe:** TIE Fighter, TIE Interceptor, Falcon und Sternzerstörer sind nur geplant. Schiffe haben noch keine Panzerung und lassen sich nicht zerstören.
- **Blasternamen:** Ein umgewandelter Bogen heißt in der Hotbar weiter „Bow“ (wie seit Phase 10). Ein eigener Name ist eine kleine Folgeaufgabe, ändert aber das exakte Zurückwandeln und ist deshalb nicht nebenbei gemacht.
- **Testumgebung:** In der Cloud fehlen einige Vanilla-Texturen und Sounds (Platzhalter auf manchen Screenshots). Im echten Spiel nicht.

## Installation und Test

1. Die SaberCraft-JAR aus diesem PR und Fabric API 0.161.0+26.2 in `.minecraft/mods/` legen. Alte SaberCraft-JARs entfernen.
2. **Blaster:** Einen Bogen in die Hand, V drücken, Blaster wählen, „Umwandeln“. Pfeile ins Inventar, Rechtsklick. Hotbar wechseln: Ziehen und Wegstecken.
3. **Droiden:** V > Droiden > R2-D2 > Rufen.
4. **X-Wing:**
   - V > Schiffe > X-Wing > Fliegen.
   - W gibt Schub. Leertaste und Sprinten heben und senken die Nase, die linke Maustaste feuert, Schleichen steigt aus.
5. **Erfolge:** L, Reiter SaberCraft.
6. **Einstellungen:** V > Grafik für Ziehen, Menü-Übergänge, Schiffskamera und Fluganzeige.
