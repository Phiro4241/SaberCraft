# SaberCraft

Fabric-Mod für Minecraft Java 26.2, die das Netherite-Schwert in ein filmreifes Energieschwert verwandelt.

**Stand:** Phase 5. Nimmst du ein Netherite-Schwert in die Hand, kommt der Griff hoch und die Energieklinge fährt mit einem Zündgeräusch aus. Solange sie leuchtet, summt sie, und Schwünge machen ein eigenes Geräusch. Beim Wegstecken fährt sie hörbar wieder in den Griff. Mit der Taste **V** wählst du für jedes Schwert einzeln einen Griff (Standard, Hüter, Dunkler Lord, Wächter, Doppelklinge) und eine von sechs Klingenfarben (Rot, Blau, Grün, Violett, Gelb, Weiß). Das Konfigurationsmenü folgt. Details: [docs/phase-5-status.md](docs/phase-5-status.md), zur Animation [docs/phase-4-status.md](docs/phase-4-status.md), zur Klinge selbst [docs/phase-3-status.md](docs/phase-3-status.md).

![Alle sechs Klingenfarben](docs/images/phase5/aufstellung_nacht.png)

## Voraussetzungen

| Komponente | Version |
| --- | --- |
| Minecraft Java | 26.2 |
| Java (JDK) | 25 |
| Fabric Loader | 0.19.5 oder neuer |
| Fabric API | 0.161.0+26.2 |

Auf dem Server musst du die Mod nicht installieren. Griff und Farbe stehen im Vanilla-Feld `minecraft:custom_model_data` des Schwerts, das Minecraft ohnehin an alle Spieler schickt. Andere Spieler sehen und hören dein Lichtschwert, wenn sie SaberCraft ebenfalls installiert haben. Nur wer auf einem Server im Überlebensmodus Griff oder Farbe ändern will, braucht SaberCraft auch auf dem Server. Im Einzelspieler und im Kreativmodus geht es immer.

## Lichtschwert anpassen

1. Nimm ein Netherite-Schwert in die Hand.
2. Drück **V** (änderbar unter Optionen → Steuerung → SaberCraft).
3. Wähle links den Griff und rechts die Klingenfarbe, dann **Übernehmen**.

Die Werte jeder Farbe (Kern-, Bloom- und Halofarbe, Länge, Dicke, Leuchtstärke, Flimmern) stehen in `config/sabercraft/blade_styles.json`.

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

Startet einen echten Minecraft-Client, legt eine Testwelt an und prüft die Klinge: Darstellung, Ein- und Ausfahren, Sounds, Itemwechsel, Tod und Respawn, Mobs mit Schwert, alle Griffe und Farben, das Auswahlmenü und das Spiel auf einem echten Dedicated Server. Die Screenshots (Ego-Perspektive und Third Person, Tag und Nacht, Bild für Bild beim Ein- und Ausfahren, jede Farbe und jeder Griff, Inventar und Menü) landen in `build/run/clientGameTest/screenshots`. Auf GitHub läuft dieser Test bei jedem Push mit, die Bilder findest du im Lauf unter **Artifacts → Screenshots**. Ohne `xvfb-run` öffnet sich ein normales Spielfenster.

## Installieren

1. Installiere Fabric Loader für Minecraft 26.2 mit dem [Fabric Installer](https://fabricmc.net/use/installer/).
2. Lege die [Fabric API](https://modrinth.com/mod/fabric-api) für 26.2 in den Ordner `mods` deiner Minecraft-Installation.
3. Lege `sabercraft-<version>.jar` in denselben Ordner `mods`.
4. Starte Minecraft mit dem Fabric-Profil.

## Projektstruktur

```
src/main/      Code für Client und Server (Variante am Item, Auswahl-Paket)
src/client/    Nur Client (Rendering, Animation, Audio, Menü, Tasten)
src/gametest/  Automatischer Client-Test mit Screenshots
```

### Klingen-Rendering (src/client)

| Datei | Aufgabe |
| --- | --- |
| `blade/BladeStyle.java` | Beschreibt eine Klinge: Kern-, Bloom- und Halofarbe, Länge, Dicke, Leuchtstärke, Flimmern |
| `blade/BladeStyles.java` | Die sechs Klingenfarben |
| `blade/BladeStyleConfig.java` | Liest und schreibt `config/sabercraft/blade_styles.json` |
| `render/BladeMesh.java` | Baut die Geometrie: weißer Kern, Bloom, weicher Farbschein |
| `render/SaberRenderTypes.java` | Eigene Render-Pipelines (unbeleuchtet, additiv, transparent) |
| `render/SaberBladeSpecialRenderer.java` | Zeichnet die Klinge in einer bestimmten Länge |
| `render/SaberBladeItemModel.java` | Item-Modell `sabercraft:saber_blade`: holt pro Spieler und Hand, wie weit die Klinge draußen ist |
| `resources/assets/minecraft/items/netherite_sword.json` | Ersetzt das Netherite-Schwert: wählt den Griff, zeigt im Inventar nur den Griff, in der Hand Griff und Klinge(n) |
| `resources/assets/sabercraft/models/item/hilt/`, `textures/item/hilt/` | Die 3D-Griffe |
| `tools/gen_hilts.py` | Erzeugt Griffmodelle, Texturen und das Item-Modell neu (`python3 tools/gen_hilts.py`, braucht numpy und Pillow) |

### Varianten

| Datei | Aufgabe |
| --- | --- |
| `src/main/.../saber/SaberVariant.java` | Griff und Farbe eines Schwerts, gespeichert in `minecraft:custom_model_data` |
| `src/main/.../network/` | Das Auswahl-Paket und seine Prüfung auf dem Server |
| `saber/SaberHilt.java`, `saber/SaberHilts.java` | Die Griffe mit ihrem Animations- und Soundprofil |
| `select/SaberCustomizeScreen.java` | Das Menü „Lichtschwert anpassen" (Taste V) |
| `select/SaberSelection.java` | Schickt die Auswahl an den Server oder nutzt im Kreativmodus das Vanilla-Paket |

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

Alle Sounds, Modelle und Texturen dieser Mod sind selbst erstellt. Die Griffe und ihre Texturen erzeugt `tools/gen_hilts.py`. Die Namen der Griffe im Spiel sind bewusst neutral gehalten (Hüter, Dunkler Lord, Wächter). Die Sounds werden mit `tools/synth_sounds.py` vollständig aus Sinuswellen, Rauschen und Hüllkurven berechnet. Sie enthalten keine Filmaufnahmen, Samples oder Originalmusik.
