# Phase 11: Raumschiffe, Aufbau und Plan

Stand: 25.10.2026, Minecraft 26.2, Fabric Loader 0.19.5, Fabric API 0.161.0+26.2, Java 25.

Der **X-Wing ist fliegbar**. TIE Fighter, TIE Interceptor, Millennium Falcon und Sternzerstörer stehen im Hangar (Menü, Reiter „Schiffe“) als „Geplant“ und lassen sich noch nicht rufen. Dieses Dokument beschreibt, wie der X-Wing gebaut ist, und wie die anderen Schiffe auf demselben Aufbau entstehen.

## Wie der X-Wing gebaut ist

### Ein Schiff ist für das Netzwerk ein Schwein

Wie Blaster-Schüsse (Schneebälle) und Droiden wird kein neuer Entitätstyp registriert. Ein Schiff ist auf dem Server ein `ShipEntity` (Unterklasse von `Pig`), das im Kopf-Slot einen markierten Gegenstand trägt (`ShipMarker`). Spieler ohne SaberCraft können dem Server weiter beitreten und sehen ein Schwein. Clients mit SaberCraft zeichnen statt des Schweins das Schiffsmodell (`DroidRenderer`, Zweig für Schiffe, Modell aus `ShipModels`).

### Gespeichert wird der Parkplatz, nicht das Schiff

Schiffe werden nie mit der Welt gespeichert, denn nach einem Laden wären sie nur Schweine. Stattdessen hält ein Spieler-Anhang (`Starships.PARKED`) fest, welches Schiff wo parkt und ob der Spieler darin sitzt.

- Ist der Besitzer näher als 160 Blöcke am Parkplatz, entsteht das Schiff. Ist er weiter als 192 Blöcke weg und sitzt nicht darin, verschwindet es wieder.
- Wer im Schiff ausloggt, sitzt beim nächsten Einloggen wieder im Cockpit. Ein Mixin (`ServerPlayerMixin`) verhindert, dass Minecraft das Schiff als normales Schwein mit dem Spieler speichert.
- Schiff-Schweine, die doch einmal aus einer Welt geladen werden, entfernt der Server sofort.

### Steuerung

Die Steuerung liegt komplett auf dem Server (`ShipEntity.travel`). Der Client schickt nur seine normalen Tasten.

| Taste | Wirkung |
| --- | --- |
| W / S | Schub geben und bremsen. Mit S bis zum Schweben und darunter langsam rückwärts. |
| A / D | Links und rechts drehen. Das Schiff legt sich in die Kurve (nur Darstellung). |
| Leertaste / Sprinten | Nase hoch und runter. |
| Angriff (linke Maustaste) | Laser feuern, statt zu schlagen oder abzubauen. |
| Schleichen | Aussteigen. In der Luft gibt es Sanften Fall. |
| Rechtsklick auf das eigene Schiff | Einsteigen. |

- **Höchstgeschwindigkeit:** 1,8 Blöcke pro Tick (36 m/s), einstellbar mit `ship_speed` (0,2 bis 2,0).
- **Kollision:** Fliegt das Schiff schneller als ein Drittel seiner Höchstgeschwindigkeit gegen einen Block, gibt es Rauch, Funken und einen Knall, und das Schiff verliert den größten Teil seines Schwungs. Weder Blöcke noch Schiff noch Pilot nehmen dabei Schaden.
- **Leer:** Ein leeres Schiff sinkt langsam zu Boden und klappt die S-Flügel ein.

### Laser

Es gibt vier Kanonen an den Flügelspitzen, die der Reihe nach feuern. Zwischen zwei Schüssen liegen mindestens 4 Ticks. Der Server prüft den Abstand und ob der Spieler wirklich Pilot dieses Schiffs ist.

- **Schüsse:** Jeder Schuss ist ein `BlasterBolt` vom Typ `XWING`, also ein sichtbarer roter Bolzen. Er hat eine Reichweite von 128 Blöcken, verursacht keinen Blockschaden und trifft nie das eigene Schiff.
- **Rückmeldung:** Alle Clients in Sichtweite bekommen den Schuss mitgeteilt (`ShipShotPayload`) und zeigen Mündungsblitz, Funken und Ton.
- **Einstellungen:** Den Schaden regelt `ship_laser_damage`. Mit `ship_lasers_hit_players` lassen sich Spieler verschonen.

### Mehrspieler

- **Position:** Der Server schickt die Position jeden Tick, und Clients folgen Schiffen in einem Schritt statt in drei. So bleibt die Kamera des Piloten auch bei voller Fahrt nah an der wahren Position. Der Test mit dediziertem Server misst unter 5 Blöcke Abstand bei voller Fahrt.
- **Besitz:** Nur der Besitzer kann sein Schiff rufen, besteigen und wegschicken. Jeder Spieler hat höchstens ein Schiff draußen.

## Plan für die nächsten Schiffe

Alle neuen Schiffe nutzen denselben Aufbau. Ein Schiff ist ein Eintrag in `ShipType` mit Maßen, Höchstgeschwindigkeit, Beschleunigung, Wendigkeit, Feuerrate und Kanonen. Dazu kommen ein Modell in `tools/gen_ships.py`, ein Eintrag in `ShipModels` und eigene, berechnete Klänge in `tools/synth_sounds.py`. Was über diesen Eintrag hinausgeht, steht bei jedem Schiff.

### TIE Fighter

- **Werte:** Kleiner und wendiger als der X-Wing. Breite 2,4, Höchstgeschwindigkeit etwa 1,9, Wendigkeit 4,5 Grad pro Tick, Feuerrate alle 3 Ticks.
- **Kanonen:** zwei grüne Kanonen unter dem Cockpit, die gleichzeitig feuern. Dafür braucht `ShipType` ein Feld „Salve“, und `ShipEntity.fire` feuert dann alle Kanonen einer Salve auf einmal.
- **Bolzen:** neuer Typ `TIE` mit grüner Farbe.
- **Klang:** das typische Heulen nachgebaut als eigener Synthese-Klang, keine Filmaufnahme.
- **Modell:** Kugel-Cockpit mit zwei sechseckigen Flügeln, ohne bewegliche Teile. Rollen in der Kurve wie beim X-Wing.
- **Aufwand:** klein. Das ist das nächste Schiff.

### TIE Interceptor

- **Werte:** wie der TIE Fighter mit eigenem Modell (gezackte Flügel) und vier Kanonen an den Flügelspitzen. Etwas schneller (2,0) und wendiger (5 Grad), dafür schwächere Panzerung.
- **Panzerung:** braucht ein neues Feld in `ShipType` für die Gesundheit. Bisher lassen sich Schiffe nicht beschädigen. Mit Panzerung kommt dazu:
  - Schiffe nehmen Treffer.
  - Ein Schiff mit 0 Gesundheit explodiert. Die Explosion zerstört keine Blöcke, und der Pilot fällt mit Sanftem Fall heraus.
  - Ein zerstörtes Schiff ist eine Minute nicht rufbar.
- **Aufwand:** mittel, hauptsächlich wegen der Panzerung.

### Millennium Falcon

- **Mehrere Sitze:** Pilot vorne, bis zu drei Mitflieger. Das braucht mehrere Passagiere am Schiff, was Vanilla bei Schweinen nicht kennt. Geplant ist, die Sitze als unsichtbare Sitz-Entitäten zu bauen, die dem Schiff folgen. Auch das sind für Vanilla-Clients bekannte Typen, etwa ein Rüstungsständer ohne Körper.
- **Geschütztürme:** Mitflieger können einen der zwei Geschütztürme bedienen und zielen mit der eigenen Blickrichtung. Der Server prüft, wer an welchem Turm sitzt.
- **Werte:** träger als der X-Wing, Wendigkeit 2 Grad. Dafür ist er schnell (2,0) und hat einen Hyperraum-Effekt, aber keinen echten Sprung: kurzer Lichtstreifen-Effekt und ein Sprung um 200 Blöcke in Blickrichtung, wenn der Platz frei ist.
- **Modell:** groß, Breite 6. Der Hitbox reicht ein flacher Kasten. Für Kollisionen mit Blöcken prüft der Server zusätzlich drei Punkte an Nase und Seiten.
- **Aufwand:** groß, wegen der Sitze und Türme.

### Sternzerstörer

Ein fliegbarer Sternzerstörer in Filmgröße (1,6 km) ist in Minecraft nicht machbar, weder für den Renderer noch für die Chunks. Geplant ist er deshalb nicht als Spielerschiff, sondern als **Kulisse am Himmel**:

- **Nur gezeichnet:** Er wird über den Planeten-Himmel gezeichnet (wie die Planeten in Phase 9 und 10), sehr weit weg und langsam ziehend, ohne Entität und ohne Kollision. Shader-Verträglichkeit wie beim Weltraumhimmel: ohne Iris normal, mit Iris im gemessenen Rahmen von Phase 10.
- **Erscheinen:** optional bei Situationen der Musik, etwa einem Kampf gegen Imperiale oder der Begegnung mit Vader. Abschaltbar in den Grafik-Einstellungen.
- **Klein fliegbar, wenn gewünscht:** eine fliegbare Version im Maßstab eines kleinen Schiffs (Breite 12, sehr träge) mit Hangar, aus dem TIE Fighter starten. Erst nach Falcon und Panzerung sinnvoll.
- **Aufwand:** Kulisse mittel, fliegbare Kleinversion groß.

### Reihenfolge

1. TIE Fighter: Salve, grüne Bolzen, Modell, Klang.
2. Panzerung und Zerstörung für alle Schiffe, dann TIE Interceptor.
3. Millennium Falcon: Sitze, Geschütztürme, Hyperraum-Effekt.
4. Sternzerstörer als Kulisse am Himmel.

Jeder Schritt kommt mit eigenem Test in `ShipGameTest` und einem Durchlauf auf dem dedizierten Server.
