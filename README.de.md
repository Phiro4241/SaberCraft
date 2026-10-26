<p align="center">
  <img src="docs/media/banner.png" alt="SaberCraft: Lichtschwerter, Blaster, Droiden und ein X-Wing für Minecraft Java 26.2 mit Fabric" width="100%">
</p>

<p align="center">
  <a href="README.md">English</a> · <b>Deutsch</b>
</p>

**SaberCraft** ist eine Fabric-Mod für Minecraft Java 26.2, die Schwerter in filmreife Lichtschwerter verwandelt: Die Klinge fährt mit einer flüssigen Animation aus, leuchtet in ihrer Farbe, summt und fährt beim Wegstecken wieder ein. Dazu kommen Kampfstile, Machtkräfte, Film-Charaktere, Planetenhimmel, Musik, Blaster mit Pfeilen als Munition, Droiden-Begleiter und ein fliegbarer X-Wing.

> [!NOTE]
> SaberCraft ist ein **inoffizielles Fanprojekt** ohne Verbindung zu Lucasfilm, Disney oder Mojang. Star Wars und alle zugehörigen Namen sind Marken ihrer Inhaber. Die Mod enthält **keine Filmmusik, keine Filmgeräusche und keine Filmgrafiken**; alle Modelle, Texturen und Klänge sind selbst erstellt.

Die ausführliche Dokumentation ist auf Englisch: **[README.md](README.md)**. Hier das Wichtigste in Kürze.

## Funktionen

| Bereich | Stand | Inhalt |
| --- | --- | --- |
| Lichtschwerter | ✅ fertig | Zünden und Einfahren, Summen, 14 Griffe, 6 Farben, Griffe mit eigenen Eigenschaften, `/sabercraft convert` für jedes Schwert |
| Kampf | ✅ fertig | 15 Kampfstile, Abwehr, Parade, Konter, Ausweichen, Drehangriff, Machtstoß, -griff, -zug, -sprung, Lichtspuren, Clash, Finisher |
| Charaktere | ✅ fertig | 11 Film-Charaktere, 5 Outfits, eigene Profile |
| Blaster | 🧪 experimentell | 9 Blaster, Pfeile als Munition, Überhitzung |
| Droiden | 🧪 experimentell | 8 Droiden-Begleiter mit Fähigkeiten |
| Raumschiffe | 🧪 experimentell | fliegbarer X-Wing; TIE Fighter, TIE Interceptor, Falcon und Sternzerstörer sind nur geplant |
| Himmel und Planeten | ✅ fertig | Weltraumhimmel, 6 Planeten (nur Himmel und Klang, nie das Gelände) |
| Musik | ✅ fertig | eigene SaberCraft-Musik, Star-Wars-Modus nur mit deinen eigenen Dateien |
| Duelle und Erfolge | ✅ fertig | freiwillige Duelle (Best of three), 11 Erfolge |

🧪 heißt: gebaut und per Skript getestet, aber noch nicht von Hand gespielt.

## Installation

Es gibt noch kein veröffentlichtes Release. Die JAR baust du selbst mit `./gradlew build` (JDK 25), sie liegt dann unter `build/libs/sabercraft-<version>.jar`.

1. Fabric Loader **0.19.5 oder neuer** für Minecraft **26.2** mit dem [Fabric Installer](https://fabricmc.net/use/installer/) installieren.
2. [Fabric API](https://modrinth.com/mod/fabric-api) **0.161.0+26.2** und die SaberCraft-JAR in den Ordner `.minecraft/mods/` legen.
3. Minecraft mit dem Fabric-Profil starten, ein Netherite-Schwert in die Hand nehmen und **V** für das Menü drücken.

## Steuerung

| Taste | Aktion |
| --- | --- |
| **V** | SaberCraft-Menü |
| **R** (halten) | Abwehrhaltung, direkt nach dem Hochnehmen Parade |
| **Z** | Drehangriff / Klingenwirbel |
| **B** | Machtstoß |
| **Linke Alt-Taste** | Ausweichen / Ausfallschritt |
| **H** (halten) | Machtgriff |
| **J** | Machtzug |
| **K** | Machtsprung |

Alle Tasten lassen sich unter Optionen → Steuerung → Tastenbelegung → SaberCraft ändern. Doppelt belegte Tasten zeigt Minecraft dort rot an.

## Weiterlesen

- Einstellungen: [docs/configuration.md](docs/configuration.md) (Englisch)
- Eigene Musik: [docs/eigene-musik.md](docs/eigene-musik.md)
- Eigene Charakterprofile: [docs/custom-profiles.md](docs/custom-profiles.md)
- Entwicklungsnotizen je Phase: [`docs/`](docs/)
- Mitmachen: [CONTRIBUTING.md](CONTRIBUTING.md), Sicherheitsprobleme: [SECURITY.md](SECURITY.md)

## Lizenz

Quellcode und eigene Assets von SaberCraft (die per Skript erzeugten Modelle, Texturen, Klänge, Musik, Icon und Banner) stehen unter der [MIT-Lizenz](LICENSE), © 2026 Phiro4241. `fabric.mod.json` gibt dasselbe an (`"license": "MIT"`).

Die MIT-Lizenz vergibt nur Rechte, die der Autor selbst hat. Sie gewährt **keine Rechte an Star Wars**: Namen wie Star Wars, Darth Vader, R2-D2, X-Wing oder DL-44 und die an Filmfiguren angelehnten Charakter-Looks bleiben Marken und Rechte von Lucasfilm bzw. Disney. Wer SaberCraft weiterverwendet, vor allem öffentlich oder kommerziell, muss diese Rechte selbst beachten. Minecraft und die Mojang-Texturen auf Screenshots sind ebenfalls nicht erfasst.
