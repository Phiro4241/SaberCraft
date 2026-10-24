# Eigene Musik im Star-Wars-Modus

Stand: Phase 9. SaberCraft hat zwei Musikmodi. Beide bleiben erhalten und haben getrennte Einstellungen:

- **SaberCraft:** die eingebaute Musik der Mod (eigene Kompositionen). Optional mit eigenen Dateien in `config/sabercraft/music/<stimmung>/`.
- **Star Wars:** spielt **nur Dateien, die du selbst hinzufügst**, passend zur Situation, zum Planeten und zum Charakter.

**SaberCraft enthält keine Filmmusik.** Die JAR bringt keine Star-Wars-Stücke mit. Lege nur Dateien ab, die du nutzen darfst, zum Beispiel gekaufte Musik oder Rips deiner eigenen CDs. Die Dateien werden nur auf deinem Computer abgespielt und nie an einen Server oder andere Spieler geschickt.

## Schnellstart

1. Starte Minecraft einmal mit SaberCraft. Dabei entsteht der Ordner `config/sabercraft/starwars_music/` mit allen Unterordnern, einer `README.txt` und einer `mapping.json`.
2. Lege `.ogg`- oder `.wav`-Dateien in die passenden Ordner (siehe unten).
3. Im Spiel **V** drücken, dann **Musik**. Musikmodus auf **Star Wars** stellen und **Neu laden** drücken.
4. Das Menü zeigt, was gerade läuft und woher es kommt, wie viele Dateien jede Situation hat und welche Planeten und Charaktere eigene Dateien haben. Fehlende Dateien und falsche Formate stehen rot darunter.

Über **Ordner …** öffnet sich der Musikordner direkt.

## Formate

| Format | Unterstützt |
| --- | --- |
| Ogg Vorbis (`.ogg`) | ja, empfohlen |
| WAV (`.wav`, 16 oder 24 Bit PCM) | ja |
| MP3, FLAC, M4A, andere | nein, im Menü als „nicht unterstützt“ gemeldet |

Umwandeln geht zum Beispiel mit Audacity (Datei → Exportieren → Als OGG exportieren) oder mit ffmpeg:

```
ffmpeg -i "stueck.mp3" -c:a libvorbis -q:a 6 "stueck.ogg"
```

Grenzen: höchstens 64 Dateien pro Ordner, jede höchstens 128 MB.

## Situationen

| Ordner | Wann |
| --- | --- |
| `exploration/` | Erkunden am Tag |
| `calm/` | ruhige Momente: nachts und unter Tage |
| `duel/` | Lichtschwertduell: Kampf mit gezündeter Klinge |
| `dramatic/` | dramatische Begegnung: Kampf ohne gezündete Klinge |
| `boss/` | Bosskampf (Enderdrache, Wither, Wächter, …) |
| `victory/` | **neu in Phase 9:** einmal nach einem gewonnenen Kampf, danach geht es normal weiter |
| `characters/` | Thema eines Charakters, wenn er auftaucht (du wählst ihn oder ein Spieler in der Nähe trägt ihn) |

Charakterthemen heißen wie der Charakter: `vader.ogg`, `obi_wan.ogg`, `maul.ogg`, `luke.ogg`, `anakin.ogg`, `yoda.ogg`, `dooku.ogg`, `ahsoka.ogg`, `kylo.ogg`, `rey.ogg`. Statt einer Datei geht auch ein Ordner, zum Beispiel `characters/vader/` mit mehreren Dateien.

## Musik für Planeten und Charaktere (neu in Phase 9)

Zusätzlich zu den Situationsordnern kannst du Musik einem Planeten oder dem Charakter geben, den du spielst:

| Ordner | Wann |
| --- | --- |
| `planets/<planet>/` | Erkunden und ruhige Momente, solange dieser Planetenhimmel gewählt ist (Menü **Himmel**) |
| `planets/<planet>/<situation>/` | diese Situation auf diesem Planeten, zum Beispiel `planets/hoth/duel/` |
| `characters/<charakter>/<situation>/` | diese Situation, solange **du** diesen Charakter spielst, zum Beispiel `characters/vader/duel/` |

Planeten: `tatooine`, `coruscant`, `endor`, `hoth`, `mustafar`, `naboo`.
Charaktere: `vader`, `obi_wan`, `maul`, `luke`, `anakin`, `yoda`, `dooku`, `ahsoka`, `kylo`, `rey`.
Situationen: `exploration`, `calm`, `duel`, `dramatic`, `boss`, `victory`.

### Reihenfolge

Für jede Situation nimmt der Modus die **erste** Stelle, an der Dateien liegen:

1. `characters/<du>/<situation>/` (der Charakter, den du spielst)
2. `planets/<planet>/<situation>/` (der gewählte Planetenhimmel)
3. `planets/<planet>/` (nur für Erkunden und ruhige Momente)
4. `<situation>/` (der allgemeine Ordner)

Du musst also nichts doppelt ablegen. Fehlt für Hoth eine Duellmusik, läuft die aus `duel/`. Das Menü zeigt hinter dem laufenden Titel, woher er kommt: „(Planet Hoth)“, „(als Darth Vader)“, „(Thema Yoda)“ oder „(allgemein)“.

Ein Charakterthema (Auftritt) ist etwas anderes als die Kampfmusik eines Charakters. Das Thema liegt direkt in `characters/vader.ogg` oder in `characters/vader/`. Die Kampfmusik liegt in `characters/vader/duel/`.

## mapping.json

Statt mit Ordnern kannst du Dateien in `mapping.json` von Hand zuordnen. Pfade sind relativ zum Musikordner (bei Charakteren und Planeten auch relativ zu deren eigenem Ordner). Eine leere Liste heißt: der passende Ordner gilt.

```json
{
  "duel": ["duel/duell_der_schicksale.ogg"],
  "victory": ["victory/thronsaal.ogg"],
  "characters": {
    "vader": ["characters/imperialer_marsch.ogg"],
    "yoda": { "theme": ["characters/yoda.ogg"], "duel": ["duel/yoda_kampf.ogg"] }
  },
  "planets": {
    "hoth": { "exploration": ["planets/hoth/schnee.ogg"], "duel": ["duel/schlacht_um_hoth.ogg"] },
    "mustafar": ["planets/mustafar/lava.ogg"]
  }
}
```

- Ein Charakter als **Liste** ist sein Thema (die Form aus Phase 8 funktioniert weiter). Als **Objekt** nimmt er `theme` und Situationsnamen.
- Ein Planet als **Liste** gilt für Erkunden und ruhige Momente. Als **Objekt** nimmt er Situationsnamen.
- Einträge in `mapping.json` haben Vorrang vor dem gleichnamigen Ordner.
- Fehlende Dateien und Pfade außerhalb des Musikordners meldet das Menü unter „Probleme“.

Nach jeder Änderung im Menü **Neu laden** drücken.

## Schalter im Menü

| Schalter | Wirkung |
| --- | --- |
| Lautstärke Star Wars | eigene Lautstärke, zusätzlich zu Gesamt- und Musiklautstärke |
| Überblenden | weiche Übergänge (Länge wie im SaberCraft-Modus) oder kurzer Schnitt |
| Kampfmusik | Duelle, dramatische Begegnungen, Bosskämpfe und Siegmusik |
| Erkundungsmusik | Erkunden und ruhige Momente |
| Charakterthemen | Thema beim Auftritt eines Charakters (höchstens alle fünf Minuten pro Charakter) |

Der SaberCraft-Modus und seine Einstellungen bleiben dabei unverändert.
