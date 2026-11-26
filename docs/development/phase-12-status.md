# Phase 12: Verbesserungen vor Release 0.1.0

Stand: 01.11.2026, Minecraft 26.2, Fabric Loader 0.19.5, Fabric API 0.161.0+26.2, Java 25. **Keine neuen Abhängigkeiten**, Iris und Sodium bleiben optional. Release 0.1.0 ist **nicht** veröffentlicht.

Alles aus Phase 1 bis 11 bleibt erhalten. Einzige bewusste Verhaltensänderung: Ein Netherite-Schwert ist nicht mehr von selbst ein Lichtschwert (siehe Punkt 4, mit Server-Option für das alte Verhalten).

| Punkt | Ergebnis | Test |
| --- | --- | --- |
| 1 Droiden direkt steuern | fertig: Droiden anschauen, **N** Fähigkeit, **U** warten/folgen, Hinweis unter dem Fadenkreuz | `Phase12GameTest` |
| 2 Droiden-Klänge | fertig: alle Droiden-Stimmen neu, weicher und tiefer, Pausen zwischen Klängen | `Phase12GameTest`, Messung |
| 3 Lichtschwert-Lautstärke | fertig: neuer Reiter „Klang“ mit eigenen Reglern, Summen leiser | `Phase12GameTest` |
| 4 Nur ausgewählte Schwerter | fertig: Knopf „Zum Lichtschwert machen“ / „Normales Schwert“, Server-Liste erlaubter Schwerter | `Phase12GameTest`, `ArsenalGameTest` |
| 5 Neue Raumschiffe | fertig: TIE Fighter und TIE Interceptor, Hülle, Treffer, Explosion | `Phase12GameTest`, `ShipGameTest` |

## 1. Droiden direkt steuern

- **Anschauen und Taste drücken:** Schaust du einen deiner Droiden an (bis 12 Blöcke, nicht durch Wände), führt **N** seine Fähigkeit aus (R2-D2 repariert, C-3PO meldet, BB-8 scannt, …) und **U** lässt ihn warten oder wieder folgen. Beide Tasten sind frei belegbar (Optionen → Steuerung → SaberCraft) und stehen auch im Kampf-Reiter des Menüs.
- **Hinweis:** Unter dem Fadenkreuz stehen Name, Fähigkeit mit Taste und Abklingzeit sowie die Warten/Folgen-Taste. Abschaltbar unter Grafik → „Droiden-Hinweis“ (`droid_target_hud`).
- **Gegen versehentliches Auslösen:** Die Tasten wirken nur ohne offenes Menü, nicht im Raumschiff, nicht während Angriff, Benutzen oder Abwehrhaltung gedrückt sind, und höchstens alle 0,25 s. Fremde Droiden reagieren nicht („Das ist nicht dein Droide.“). Der Server prüft noch einmal: nur der eigene Droide, nah genug, in Sichtlinie, nur Fähigkeit oder Warten/Folgen, mit denselben Abklingzeiten wie im Menü.
- Das Droiden-Menü bleibt unverändert nutzbar.

## 2. Droiden-Klänge

- Alle 8 Droiden haben neue, berechnete Stimmen (`tools/synth_sounds.py`): weichere Töne mit Tiefpass statt harter Rechteck-Pieptöne, tiefere Tonlagen, sanfte Ein- und Ausblendung. Die mittlere Tonhöhe sank deutlich, z. B. R2-D2 von etwa 2650 Hz auf 1540 Hz, die Maus-Droide von 6700 Hz auf 1840 Hz.
- Jede Fähigkeit hat weiter ihren eigenen, erkennbaren Klang. Je Droide gibt es jetzt drei verschiedene Leerlauf-Klänge statt zwei.
- **Keine Klänge direkt hintereinander:** Ein Droide spricht höchstens alle 1,5 s; Leerlauf-Geplauder kommt nur, wenn 15 s lang keiner deiner Droiden etwas gesagt hat, nur in deiner Nähe und im Mittel nur alle 45 s (`droid_chatter_seconds`). Fähigkeiten sind immer zu hören. Neu gerufene Droiden, die nach einem Tod automatisch zurückkommen, kommen leise.
- Schritte sind halb so laut. Eigener Regler „Droiden“ und Schalter „Droiden-Geplauder“ im Reiter „Klang“.

## 3. Lichtschwert-Lautstärke

- Neuer Menü-Reiter **„Klang“**: Lichtschwerter (alle Schwert- und Machtklänge), Summen (zusätzlich), Leiser im Stillstand, Droiden, Droiden-Geplauder, Raumschiffe. Die Regler wirken zusätzlich zu den Minecraft-Reglern, sofort (auch auf ein gerade summendes Schwert) und werden in `immersion_client.json` gespeichert.
- Das Summen ist standardmäßig leiser (60 %). Hältst du die Klinge etwa 4 s still, sinkt es auf die Hälfte; jede Bewegung bringt es zurück.
- Technik: ein Mixin am `SoundEngine` (`SoundEngineMixin`) skaliert nur SaberCraft-Klänge, nach ihrem Namen (`saber.*`, `force.*`, `droid.*`, `ship.*`). Andere Klänge und Musik bleiben unberührt.

## 4. Nur ausgewählte Schwerter sind Lichtschwerter

- Jedes Schwert, auch Netherite, ist zunächst ein **ganz normales Minecraft-Schwert**: keine Klinge, kein Leuchten, keine Animationen, kein Summen, keine Schwert-Fähigkeiten.
- **Auswählen:** Mit einem Schwert in der Hand **V** drücken → Reiter „Lichtschwert“ zeigt „Lichtschwert wählen“ mit dem Knopf **Zum Lichtschwert machen**; danach geht es direkt zu Griff, Farbe und Stil. Oder `/sabercraft convert`.
- **Zurück:** Im Lichtschwert-Reiter **Normales Schwert** (oder `/sabercraft revert`) macht exakt das Schwert von vorher daraus (Material, Haltbarkeit, Name, Verzauberungen und fremde Modelldaten bleiben).
- **Server-Einstellungen** (`abilities.json`): `saber_swords` (welche Schwertarten gewählt werden dürfen, standardmäßig alle 7), `netherite_swords_are_sabers` (altes Verhalten, standardmäßig aus). `commands_for_everyone: false` beschränkt das Wählen auch im Menü auf Operatoren.
- **Bestehende Welten:** Bereits umgewandelte Schwerter bleiben Lichtschwerter. Netherite-Schwerter, die vor Phase 12 nur durch die alte Regel Lichtschwerter waren, sind jetzt normale Schwerter, bis man sie wählt, außer der Server stellt `netherite_swords_are_sabers` an.
- Für das Item-Modell von `netherite_sword` gilt jetzt dieselbe Regel wie für die anderen Schwerter: Lichtschwert-Modell nur mit gespeichertem Griff, sonst das Vanilla-Modell.

## 5. Neue Raumschiffe

Nach dem Plan in [phase-11-starships.md](phase-11-starships.md) sind die Schritte 1 und 2 umgesetzt:

- **TIE Fighter:** eigenes Modell (Kugel-Cockpit, sechseckige Solarflügel mit Gitter, Streben), eigene Texturen, heulendes Triebwerk, eigener Laserklang. Zwei Kanonen unter dem Fenster feuern **gleichzeitig grüne Bolzen** (neuer Bolzentyp `tie`). Wendiger (4,5°/Tick) und etwas schneller als der X-Wing, Hülle 24.
- **TIE Interceptor:** eigenes Modell mit Dolchflügeln, vier Kanonen an den Flügelspitzen (feuern im Wechsel, alle 3 Ticks), schnellstes Schiff (2,0 Blöcke/Tick, 5°/Tick), schwächste Hülle (18), eigenes Triebwerk und eigener Laser.
- **Hülle für alle Schiffe** (X-Wing 40): Treffer von anderen Spielern, Mobs, Bolzen und Explosionen ziehen Hülle ab, der eigene Pilot nie. Treffer machen Funken und einen Klang; unter 35 % raucht das Schiff; nach 10 s ohne Treffer repariert es sich langsam (1 Punkt pro Sekunde). Bei 0 explodiert es ohne Blockschaden, der Pilot fällt mit Sanftem Fall heraus, und ein neues Schiff ist erst nach 60 s rufbar. Schalter `ship_damage`, Wartezeit `ship_respawn_seconds`.
- Flug-Anzeige mit Hüllen-Balken; Hangar mit drehbarer Vorschau beider TIEs; `/sabercraft ship call|board|dismiss tie_fighter|tie_interceptor`.
- **Abweichung vom Plan:** Der TIE Fighter feuert seine Salve alle 6 Ticks statt alle 3, weil jede Salve zwei Bolzen hat; so bleibt die Feuerkraft mit dem X-Wing vergleichbar.
- Millennium Falcon und Sternzerstörer bleiben geplant (Sitze, Geschütztürme bzw. Himmelskulisse).

## Tests

Ausgeführt lokal im Cloud-Container am 01.11.2026 (echter Minecraft-26.2-Client mit Software-Grafik unter Xvfb, echte Tasten und Mausklicks, dazu ein echter dedizierter Server):

- `./gradlew build gametestClasses`: erfolgreich, JAR `sabercraft-0.1.0.jar` gebaut.
- **Alle 28 Client-Gametest-Klassen grün**, in zwei Läufen: `BladeScreenshotGameTest` und `SaberAnimationGameTest` im ersten Lauf, danach `SaberVariantGameTest` bis `Phase12GameTest` am Stück (26 Klassen, 1399 bestandene Einzelprüfungen, 29 Minuten). Dazwischen wurde nur eine veraltete Erwartung in `SaberVariantGameTest` korrigiert (ein normales Netherite-Schwert hat jetzt kein Lichtschwert-Profil).
- **Neu: `Phase12GameTest`** prüft: normale Schwerter (auch Netherite) ohne Klinge und Summen; Menü-Knopf macht ein Lichtschwert, „Normales Schwert“ gibt exakt das alte Schwert zurück; `saber_swords` verbietet eine Schwertart; `netherite_swords_are_sabers`; Lautstärke-Faktoren; leiser werdendes Summen im Stillstand und volles Summen beim Schwingen; Droide anschauen + echte Taste N (R2-D2 repariert), U (folgen); keine Auslösung ohne Droide im Blick oder mit offenem Menü; Server lehnt fremde Ziele, zu weite Entfernung und andere Befehle ab; Droiden-Stimmen nie direkt hintereinander; TIE Fighter fliegt und feuert zwei grüne Bolzen gleichzeitig; Hülle: Pilot macht keinen Schaden, Gegner schon, Zerstörung mit Explosion, Auswurf und Wartezeit; TIE Interceptor ist schneller als der X-Wing; dedizierter Server: Schwert per Menü wählen und TIE Fighter fliegen.
- Angepasst: Alle Tests, die bisher ein normales Netherite-Schwert als Lichtschwert nutzten, nehmen jetzt ein markiertes Lichtschwert; `ArsenalGameTest` erwartet, dass alle sieben Schwerter erst nach der Auswahl Lichtschwerter sind; `ShipGameTest` prüft das „Geplant“-Schiff jetzt mit dem Falcon.
- **GitHub Actions:** siehe PR-Beschreibung; ein CI-Ergebnis wird hier nicht behauptet.

## Bekannte Grenzen und offene Punkte

- **Nicht von Hand gespielt:** Alles aus Phase 12 ist nur per Skript getestet (echter Client mit Software-Grafik, echte Tasten), nicht mit Maus und Gehör im normalen Spiel.
- **Klang:** Die neuen Droiden-, TIE- und Explosionsklänge sind berechnet und nur per Messung (Tonhöhe, Lautstärke) und Abspielen im Test geprüft. Ob sie „gut klingen“, muss jemand hören.
- **Vanilla-Server:** Auf einem Server ohne SaberCraft kann ein Schwert nur im Kreativmodus gewählt werden. Die alte „Netherite ist immer ein Lichtschwert“-Regel gibt es dort nicht mehr.
- **Mehrspieler:** Schiffs-Schaden durch andere Spieler und fremde Droiden („nicht dein Droide“) sind nur mit einem Spieler und Mobs getestet, nicht mit zwei echten Spielern.
- **Shader:** Die TIE-Modelle sind mit Iris/Sodium nicht im Spiel angesehen. Sie nutzen dieselben Item-Modelle und Renderwege wie der X-Wing.
- Der Erfolg „Red Five, Standing By“ (volle Geschwindigkeit) zählt auch mit den TIEs.
- Millennium Falcon und Sternzerstörer sind nicht gebaut.

## Selbst im Spiel testen

1. Netherite- und Eisenschwert nehmen: beide sind normale Schwerter. **V** → „Zum Lichtschwert machen“ → Griff/Farbe wählen. Danach im selben Reiter „Normales Schwert“.
2. **V** → „Klang“: Regler ausprobieren, Klinge still halten und auf das leiser werdende Summen achten.
3. Droiden rufen (Reiter „Droiden“), Menü schließen, einen Droiden anschauen, **N** und **U** drücken. Auf Geplauder und Pausen achten.
4. Reiter „Schiffe“: TIE Fighter und TIE Interceptor fliegen, mit Linksklick feuern; mit einem Skelett oder `/damage` die Hülle testen.
