# Phase 13: Schiffe, Sounds, Angriffe, Skins, Rüstung, Droiden, Flugenergie

Stand: 02.11.2026, Minecraft 26.2, Fabric Loader 0.19.5, Fabric API 0.161.0+26.2, Java 25. **Keine neuen Abhängigkeiten.** Es wurde **kein Release** erstellt; 0.1.0 bleibt unveröffentlicht.

Alles aus Phase 1 bis 12 bleibt erhalten. Bewusste Verhaltensänderungen sind unten jeweils mit Begründung genannt und lassen sich per Einstellung zurückdrehen (z. B. `move_variety`, `ship_energy`, `ship_crash_damage`, `droid_limit`, `character_hide_armor`).

| Punkt | Ergebnis | Test |
| --- | --- | --- |
| 1 Schiffe | Pilot unsichtbar, kein Fallschaden, Aufprallschaden bis zum Wrack, Millennium Falcon und Sternzerstörer | `Phase13GameTest`, `ShipGameTest`, `Phase12GameTest` |
| 2 Sounds | ruhigere Triebwerke abhängig von Schub und Flugzustand, neue Schüsse, Treffer- und Explosionsklänge mit Begrenzer | `Phase13GameTest` (Begrenzer, Lautstärke-Obergrenze) |
| 3 Angriffsanimationen | Signature-Moves selten, mehrere Moves je Situation, keine direkte Wiederholung | `Phase13GameTest` (alle 15 Stile) |
| 4 Skins | Umhänge für 9 Charaktere, Vader mit erhabener Rüstung in der äußeren Schicht | `Phase13GameTest`, `SkinLayerGameTest` |
| 5 Rüstung | Charakter-Skin bleibt sichtbar, Rüstung schützt weiter, Elytren bleiben | `Phase13GameTest` |
| 6 Droiden | Limit 2 (alte Configs werden umgestellt), Werte pro Droide, Rollen, gemeinsame Abklingzeit, Gruppen-Malus | `Phase13GameTest`, `DroidGameTest` |
| 7 Flugzeit | Energie pro Schiff, Ausfall mit sicherem Gleitflug, Aufladen am Boden, HUD-Anzeige | `Phase13GameTest` |

## 1. Schiffe

### Pilot unsichtbar

- Wer in einem Schiff sitzt, wird nicht gezeichnet: weder in der Außenansicht noch die Hände in der Ich-Ansicht, für den eigenen Spieler und für alle anderen (`EntityRenderDispatcherMixin`, `ItemInHandRendererMixin`).
- **Nur das Zeichnen fällt weg.** Der Spieler bleibt für das Spiel vollständig da: Steuerung, Trefferprüfung, Kamera und Server-Logik ändern sich nicht.
- **Randfälle:** Die Prüfung wird jedes Bild neu aus dem Fahrzeug berechnet, das der Client kennt. Nach Aussteigen, Tod, Zerstörung des Schiffs, Verlassen der Welt oder Serverwechsel gibt es daher keinen Zustand, der zurückgesetzt werden müsste: Sobald der Spieler nicht mehr im Schiff sitzt, ist er sofort wieder sichtbar. Getestet: Aussteigen, Wrack, Tod im Cockpit, dedizierter Server.
- Abschaltbar: Grafik → „Piloten ausblenden“ (`ship_hide_pilot`).

### Landungen, harte Aufschläge, Abstürze

- **Ursache des Fallschadens:** Vanilla gibt den Fall eines Fahrzeugs an seine Passagiere weiter (`Entity.causeFallDamage` → `propagateFallToPassengers`). Schiffe nehmen jetzt nie Fallschaden, und ihre Fallhöhe wird jeden Tick zurückgesetzt, auch die der Passagiere. Der Pilot stirbt also nie an normalem Fallschaden durch das Schiff.
- **Aufprallstärke:** Der Server misst, wie viel Geschwindigkeit das Schiff an Boden, Decke oder Wand verloren hat. Jedes Schiff hat eine sichere Grenze und eine Wrack-Grenze (`ShipType.Handling`):
  - bis zur sicheren Grenze: sanfte Landung, leises Aufsetzen und etwas Staub, kein Schaden;
  - darüber: Hüllenschaden, der mit der Geschwindigkeit wächst (bis 90 % der Hülle knapp vor der Wrack-Grenze); unter etwa einem Drittel davon als „Harte Landung“ mit dumpfem Schlag und Staub, darüber als „Absturz“ mit Rauch, Funken, Flammen und Metallknall;
  - ab der Wrack-Grenze: das Schiff wird zerstört (Explosion ohne Blockschaden wie in Phase 12), der Pilot fällt mit Sanftem Fall heraus.
- Der Hüllenschaden ist mit `ship_crash_damage` skalierbar (0 = nie).
- Das alte Verhalten aus Phase 11/12 (Aufprall nur Rauch, kein Schaden) entfällt bewusst, weil Phiro Schaden je nach Aufprall wollte.

### Millennium Falcon

- Eigenes Modell im Minecraft-Stil (`tools/gen_ships.py`, 126 Quader, 1 Einheit = ¼ Block): runde Untertasse mit seitlichem Cockpit, vorderen Zinken, Antriebsband, Radarschüssel und Landebeinen.
- **Animationen:** Die Radarschüssel dreht sich, die Landebeine fahren nahe am Boden aus und im Flug ein, das Schiff legt sich weniger in die Kurve als die Jäger.
- **Flug:** schnell (1,85), aber träger als die Jäger (Wendigkeit 2,4, Beschleunigung 0,022), dickste Hülle der kleinen Schiffe (90), zwei Laserkanonen, längste Flugzeit (8 Minuten). Die Kamera bleibt 16 Blöcke hinter dem Schiff.
- **Abweichung vom Plan aus Phase 11:** Mitflieger-Sitze, Geschütztürme und Hyperraum-Effekt sind noch nicht gebaut. Der Falcon hat vorerst einen Sitz. Grund: Mehrere Passagiere an einem Schwein brauchen eigene Sitz-Entitäten. Das ist ein eigenes, größeres Paket, und es hätte die übrigen sechs Punkte dieser Phase verdrängt.

### Sternzerstörer

- Eigenes Modell (312 Quader, 1 Einheit = 1 Block): Keilrumpf mit gestuften Decks, Brückenturm mit zwei Kuppeln, Hauptantrieben und Batterien an den Kanten. Etwa 80 Blöcke lang, also viel größer als alle anderen Schiffe, aber noch zeichen- und ladbar.
- **Flug als Großkampfschiff:** Er neigt die Nase nie, steigt und sinkt gerade (Leertaste und Sprinten), wendet sehr langsam und kreuzt höchstens mit 0,85 Blöcken pro Tick. Er hält mindestens 14 Blöcke Abstand zum Boden; der Server misst die Höhe unter Bug, Heck, Mitte und Seiten. Sechs Turbolaser-Batterien feuern grüne, schwere Schüsse.
- Kamera: 72 Blöcke hinter dem Schiff. Minecraft begrenzt den Kameraabstand eines Reittiers auf 32 Blöcke; ein kleiner Mixin (`CameraMixin`) erlaubt für Schiffe mehr, Blöcke ziehen die Kamera weiterhin heran.
- **Abweichung vom Plan aus Phase 11:** Dort war der Sternzerstörer als reine Kulisse am Himmel geplant. Phiro wollte ihn diesmal mit passender Flugmechanik, deshalb ist es die dort beschriebene „fliegbare Kleinversion“ geworden. Filmgröße (1,6 km) bleibt in Minecraft unmöglich.

### Bestehende Schiffe

X-Wing, TIE Fighter und TIE Interceptor fliegen wie vorher. Neu für alle Schiffe sind nur die Punkte dieser Phase (unsichtbarer Pilot, Aufprall, Energie, Klänge). `ShipGameTest` und `Phase12GameTest` laufen weiter; der einzige angepasste Test prüfte, dass der Falcon „geplant“ ist.

## 2. Sounds

- **Triebwerke** (`ShipEngineSound`, neu geschrieben): ruhige 4-Sekunden-Schleifen mit Tiefpass statt der alten, schärferen Klänge (`tools/synth_sounds.py --phase13-ships`). Die Lautstärke hängt vom Zustand ab: geparkt sehr leise (0,07), im Flug 0,2 + 0,32 × Schub, ein kurzes Aufheulen beim Beschleunigen, nach 3 Sekunden gleichmäßigem Flug 20 % leiser. Das eigene Schiff ist um ein Viertel leiser als fremde, die Obergrenze liegt bei 0,6. Tonhöhe folgt der Geschwindigkeit mit leichter Schwankung, damit die Schleife nicht mechanisch klingt. Bei leerer Energie stottert das Triebwerk.
- **Vorbeiflug:** Rauscht ein fremdes Schiff schnell nah an der Kamera vorbei, gibt es einmal ein kurzes Rauschen (beim Sternzerstörer tiefer).
- **Schüsse:** drei weichere Laser-Varianten, ein eigener Turbolaser. Bei langen Feuerstößen werden die Schüsse leiser, damit Dauerfeuer nicht nervt.
- **Treffer:** Schiffslaser haben einen Einschlagklang und Funken. Nur wenn ein Schuss ein Ziel tötet, gibt es eine kleine Explosion (Partikel und Klang, ohne Blockschaden). So explodiert nicht jeder Treffer.
- **Kein Klangteppich:** Ein Begrenzer (`ImpactSounds`) lässt im Umkreis von 3 Blöcken höchstens einen Einschlagklang pro 3 Ticks zu, kleine Explosionen höchstens einmal pro halbe Sekunde im Umkreis von 6 Blöcken.
- Neu: Landen, harter Aufschlag, Triebwerke aus, Triebwerke an. Alle neuen Klänge haben Untertitel (Deutsch und Englisch) und folgen dem Regler „Schiffe“ (`ship_volume`).

## 3. Angriffsanimationen

- Jeder Kampfstil hat jetzt eigene Listen für Krit- und Sprintangriffe (`crit_moves`, `sprint_moves` in `moves.json`) und eine eigene Chance für seinen Signature-Move (20 bis 45 %, Ezra 30 %).
- **Signature-Moves** (Ezras 180°-Drehung, Saltos, Sprünge) kommen nur nach mindestens zwei anderen Angriffen und nie zweimal hintereinander. Gemessen: Ezras Drehung kommt bei etwa jedem fünften Krit (19 % von 600 simulierten Krits) statt bei jedem.
- **Mehr Abwechslung:** Sonst wählt ein Krit oder Sprintangriff gleichmäßig aus den anderen Moves des Stils, nie den gerade gespielten. Die normale Kombo überspringt manchmal (20 %) einen Schritt, sodass lange Kämpfe nicht dieselbe Folge wiederholen. Jeder Charakter behält seinen Stil, es werden nur seine eigenen Moves gemischt.
- **Natürlich statt zufällig:** Die Wahl hängt von Spieler, Spielzeit und Anzahl seiner Angriffe ab. Alle Clients berechnen denselben Move, ohne neue Netzwerkdaten.
- Einstellungen im Reiter „Kampf“: „Abwechslungsreiche Moves“ (`move_variety`, aus = Verhalten von Phase 12) und „Signature-Moves“ (`signature_moves`, 0 = nie, 1 = normal, 3 = oft).

## 4. Skins

- **Umhänge:** Vader, Dooku, Kylo Ren, Maul, Obi-Wan, Anakin, Yoda sowie die Outfits „Wanderer“ und „Schatten“ tragen einen echten Umhang (Minecrafts Umhang-Ebene mit eigener 64×32-Textur, `tools/paint_outfits.py`). Die Umhänge haben Falten, einen dunkleren Saum und je nach Figur eine Kapuze, einen Rand oder eine Schließe. Vaders Umhang glänzt auf den Faltenkämmen kühl, damit er sich vom schwarzen Anzug abhebt.
- Ein Charakter-Umhang wird auch dann gezeichnet, wenn im Spiel unter Skin-Anpassung „Umhang“ aus ist. Er gehört zum Kostüm. Abschaltbar mit `capes` in `character_client.json`.
- **Vader** hat in der äußeren Skin-Ebene jetzt erhabene Rüstungsteile: Schulterpanzer, Brustkasten, Gürtel mit Schnallen, Stulpen, Stiefel und Glanzlichter am Helm.
- Die Skins bleiben im Minecraft-Stil (64×64, klare Flächen, wenig Rauschen).
- Nicht neu gemalt wurden die übrigen Charaktere außerhalb ihrer Umhänge. Sie nutzen die äußere Schicht schon seit Phase 10.

## 5. Rüstung

- **Problem:** Rüstung wird über den Charakter-Skin gezeichnet und verdeckt ihn, z. B. Vaders Helm unter einem Diamanthelm.
- **Lösung:** Für Spieler mit Charakter oder Outfit lässt jeder Client beim Zeichnen die Rüstungsteile weg (`AvatarRendererMixin`, nur in den Zeichendaten des Bildes). Die Rüstung bleibt angezogen, im Inventar und **schützt wie immer** (getestet: volle Diamantrüstung = 20 Rüstungspunkte). Elytren bleiben sichtbar, weil sie keine Rüstung sind.
- **Kein Clipping,** weil nichts mehr übereinander liegt. **Mehrspieler:** Jeder Client kennt die Charakterwahl der anderen Spieler (seit Phase 8) und entscheidet für jeden Spieler selbst. Dafür braucht es keine Server-Änderung.
- Abwägung: Verzauberungsglanz und Rüstungsmaterial sieht man bei Charakteren nicht mehr. Wer das sehen will, schaltet Grafik → „Charakter über Rüstung“ (`character_hide_armor`) aus.

## 6. Droiden

- **Limit:** standardmäßig 2 gleichzeitig (vorher 4), einstellbar 1 bis 8 (`droid_limit`). Bestehende Server-Configs mit dem alten Standard 4 werden einmalig auf 2 umgestellt (neues Feld `config_version`). Wer danach bewusst wieder 4 einträgt, behält 4.
- **Werte pro Droide** in `abilities.json` unter `droid_balance`: Abklingzeit, Reichweite, Schaden und eine droidenspezifische Stärke. Tabelle und Beispiel: [configuration.md](configuration.md#droid_balance). `droid_cooldown` bleibt als Gesamtfaktor.
- **Abgeschwächt:** längere Abklingzeiten (B1 von 5 auf 12 s, Chopper 15 → 18 s, K-2SO 10 → 14 s, BB-8 20 → 25 s, Sonde 30 → 40 s, Maus 10 → 12 s), weniger Schaden (Chopper 4 → 3), kleinere Reichweiten (BB-8 24 → 20, K-2SO 6 → 5, Sonde 16 → 12, Maus 12 → 10), R2-D2 repariert 20 % statt 25 %.
- **Rollen:** Unterstützung (R2-D2, Maus), Information (C-3PO, BB-8, Sonde), Kampf (Chopper, B1, K-2SO).
- **Nicht zu stark zusammen:** Nach der Fähigkeit eines Kampfdroiden warten die anderen Kampfdroiden desselben Spielers 3 Sekunden (`droid_combat_shared_cooldown_seconds`). Ihr Schaden sinkt pro weiterem Kampfdroiden (`droid_group_penalty` 0,25: zwei Kampfdroiden treffen mit je 80 %).

## 7. Flugzeit und Energie

- Jedes Schiff hat Flugenergie: X-Wing 5 min, TIE Fighter 3 min, TIE Interceptor 2,5 min, Falcon 8 min, Sternzerstörer 15 min bei vollem Schub. Schweben und langsames Fliegen verbrauchen nur halb so viel. Einstellbar pro Schiff (`ship_flight_seconds`).
- **Leer:** Die Triebwerke fallen mit Ansage und Klang aus. Ein kleines Schiff gleitet langsam und sicher zu Boden (schneller als die sichere Landegrenze geht es nicht) und lässt sich noch etwas lenken; der Sternzerstörer bleibt stehen.
- **Aufladen:** am Boden, geparkt oder ohne Pilot (der Sternzerstörer auch im Stillstand), von leer bis voll in 30 s (TIEs) bis 2 min (Sternzerstörer), einstellbar (`ship_recharge_seconds`). Ab 25 % starten die Triebwerke wieder (`ship_restart_share`). Ein geparktes Schiff lädt auch weiter, solange es nicht in der Welt ist.
- **HUD:** eine Zeile mit Prozent, Balken und Restzeit („Energie 62 % · 3:05“). Unter 35 % wird sie gelb, unter 15 % blinkt sie, bei leerer Energie wird sie rot und zeigt „Energie leer · bereit in 0:12“.
- `ship_energy: false` schaltet das Ganze ab (unbegrenztes Fliegen wie vorher).

## Tests

- **Neu:** `Phase13GameTest` mit allen sieben Punkten, im Einzelspieler und auf dem dedizierten Server: Pilot unsichtbar und danach sichtbar (Aussteigen, Wrack, Tod im Cockpit, dedizierter Server), kein Fallschaden beim Gleiten ohne Energie, sanfte, harte, schwere und zerstörende Aufschläge, Energie verbrauchen, leer, HUD, Aufladen, Falcon-Flug, Sternzerstörer (Höhe, keine Neigung, Geschwindigkeit, Aussteigen mit Sanftem Fall), Klang-Begrenzer, Moves aller 15 Stile, Umhänge, Rüstung (verborgen, schützt, Elytra sichtbar, Option aus), Droiden (Limit, Umstellung alter Configs, eigene Werte aus der Datei, gemeinsame Abklingzeit, Gruppen-Malus, Rollen).
- **Angepasst:** `ShipGameTest` (Falcon ist jetzt rufbar), `DroidGameTest` (hebt das Limit für seine Viererteams auf 4 und prüft R2-D2 mit dem neuen Anteil), `SkinLayerGameTest` (ein Charakter darf seinen eigenen Umhang haben).
- Ergebnisse des vollständigen lokalen Laufs: siehe Pull Request.

## Offene Punkte

- **Nicht von Hand gespielt:** Flugverhalten, Lautstärken, Balance der Droiden und Häufigkeit der Moves sind nur per Skript getestet, nicht mit echten Spielern.
- Falcon: keine Mitflieger-Sitze, keine Geschütztürme, kein Hyperraum-Effekt.
- Sternzerstörer: Die Trefferbox ist die eines großen Schiffs (14 × 7 Blöcke), nicht die des ganzen 80-Block-Modells. Schüsse auf Bug oder Heck gehen daher durch.
- Mit Charakter sieht man Rüstung und Verzauberungsglanz nicht (abschaltbar).
- Nicht getestet: echte Mehrspieler-Runden mit mehreren Menschen, Shader-Packs mit den neuen Schiffen und Umhängen.
