# SaberCraft

Fabric-Mod für Minecraft Java 26.2, die das Netherite-Schwert in ein filmreifes Energieschwert verwandelt.

**Stand:** Phase 4. Nimmst du ein Netherite-Schwert in die Hand, kommt der Griff hoch und die rote Energieklinge fährt mit einem Zündgeräusch aus. Solange sie leuchtet, summt sie, und Schwünge machen ein eigenes Geräusch. Beim Wegstecken fährt sie hörbar wieder in den Griff. Weitere Farben und das Konfigurationsmenü folgen. Details: [docs/phase-4-status.md](docs/phase-4-status.md), zur Klinge selbst [docs/phase-3-status.md](docs/phase-3-status.md).

![Die Klinge fährt aus (Third Person)](docs/images/phase4/ignite_third_person.png)

## Voraussetzungen

| Komponente | Version |
| --- | --- |
| Minecraft Java | 26.2 |
| Java (JDK) | 25 |
| Fabric Loader | 0.19.5 oder neuer |
| Fabric API | 0.161.0+26.2 |

Die Mod läuft komplett im Client und schickt keine eigenen Netzwerkpakete. Auf dem Server musst du sie nicht installieren. Andere Spieler sehen und hören dein Lichtschwert, wenn sie SaberCraft ebenfalls installiert haben.

## Bauen

```bash
./gradlew build
```

Die fertige Mod liegt danach unter `build/libs/sabercraft-<version>.jar`. Die Datei mit `-sources` im Namen brauchst du nicht.

Jeder Push auf GitHub baut die Mod auch automatisch über GitHub Actions. Die JAR findest du dann im Reiter **Actions**, im jeweiligen Lauf unter **Artifacts**.

## Im Entwicklungsmodus starten

```bash
./gradlew runClient
```

## Automatischer Spieltest

```bash
xvfb-run -a ./gradlew runClientGameTest
```

Startet einen echten Minecraft-Client, legt eine Testwelt an und prüft die Klinge: Darstellung, Ein- und Ausfahren, Sounds, Itemwechsel, Tod und Respawn, Mobs mit Schwert und das Spiel auf einem echten Dedicated Server. Die Screenshots (Ego-Perspektive und Third Person, Tag und Nacht, Bild für Bild beim Ein- und Ausfahren) landen in `build/run/clientGameTest/screenshots`. Auf GitHub läuft dieser Test bei jedem Push mit, die Bilder findest du im Lauf unter **Artifacts → Screenshots**. Ohne `xvfb-run` öffnet sich ein normales Spielfenster.

## Installieren

1. Installiere Fabric Loader für Minecraft 26.2 mit dem [Fabric Installer](https://fabricmc.net/use/installer/).
2. Lege die [Fabric API](https://modrinth.com/mod/fabric-api) für 26.2 in den Ordner `mods` deiner Minecraft-Installation.
3. Lege `sabercraft-<version>.jar` in denselben Ordner `mods`.
4. Starte Minecraft mit dem Fabric-Profil.

## Projektstruktur

```
src/main/      Code für Client und Server (Zustand, Netzwerk, Sounds)
src/client/    Nur Client (Rendering, Animation, Audio, Menü, Tasten)
src/gametest/  Automatischer Client-Test mit Screenshots
```

### Klingen-Rendering (src/client)

| Datei | Aufgabe |
| --- | --- |
| `blade/BladeStyle.java` | Beschreibt eine Klinge: Kernfarbe, Leuchtfarbe, Länge, Dicke, Leuchtstärke, Flimmern |
| `blade/BladeStyles.java` | Liste aller Klingen. Neue Farbe = eine neue Zeile |
| `render/BladeMesh.java` | Baut die Geometrie: weißer Kern, Bloom, weicher Farbschein |
| `render/SaberRenderTypes.java` | Eigene Render-Pipelines (unbeleuchtet, additiv, transparent) |
| `render/SaberBladeSpecialRenderer.java` | Zeichnet die Klinge in einer bestimmten Länge |
| `render/SaberBladeItemModel.java` | Item-Modell `sabercraft:saber_blade`: holt pro Spieler und Hand, wie weit die Klinge draußen ist |
| `resources/assets/minecraft/items/netherite_sword.json` | Ersetzt das Netherite-Schwert: Griff im Inventar, Griff mit Klinge in der Hand |

### Animation und Sound (src/client)

| Datei | Aufgabe |
| --- | --- |
| `saber/SaberAnimation.java`, `saber/SaberAnimations.java` | Zeitablauf pro Lichtschwertmodell: Verzögerung, Ausfahr- und Einfahrdauer, Bewegungskurve, Aufblitzen |
| `saber/Easing.java` | Bewegungskurven (linear, schnell-dann-sanft, ...) |
| `saber/SaberProfiles.java` | Welches Item ein Lichtschwert ist und welche Animation und Sounds es hat |
| `saber/SaberTracker.java` | Verfolgt jede Hand jedes Spielers und Mobs mit Lichtschwert, steuert Animation und Sounds |
| `saber/SaberHandState.java` | Zustand einer Hand: wie weit die Klinge draußen ist, Summen |
| `saber/HolsterController.java` | Lässt die Klinge erst einfahren, bevor der eigene Spieler den Slot wechselt |
| `sound/SaberSoundSet.java` | Die Sounds eines Lichtschwertmodells |
| `sound/SaberHumSoundInstance.java` | Das Summen: läuft in Schleife, blendet weich ein und aus, reagiert auf Bewegung |
| `resources/assets/sabercraft/sounds.json` | Sound-Definitionen, vier Varianten für Schwünge |
| `tools/synth_sounds.py` | Erzeugt alle Sounds neu (`python3 tools/synth_sounds.py`, braucht numpy und ffmpeg) |

## Rechtliches

Alle Sounds und Texturen dieser Mod sind selbst erstellt. Die Sounds werden mit `tools/synth_sounds.py` vollständig aus Sinuswellen, Rauschen und Hüllkurven berechnet. Sie enthalten keine Filmaufnahmen, Samples oder Originalmusik.
