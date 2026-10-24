# SaberCraft

Fabric-Mod für Minecraft Java 26.2, die das Netherite-Schwert in ein filmreifes Energieschwert verwandelt.

**Stand:** Phase 3. Hältst du ein Netherite-Schwert in der Hand, zeigt es einen Griff mit roter, leuchtender Energieklinge. Animationen, Sounds und weitere Farben folgen. Details: [docs/phase-3-status.md](docs/phase-3-status.md).

![Rote Klinge in der Third-Person-Ansicht bei Nacht](docs/images/phase3/third_person_nacht.png)

## Voraussetzungen

| Komponente | Version |
| --- | --- |
| Minecraft Java | 26.2 |
| Java (JDK) | 25 |
| Fabric Loader | 0.19.5 oder neuer |
| Fabric API | 0.161.0+26.2 |

Die Mod muss auf Client und Server installiert sein, damit andere Spieler dein Schwert sehen und hören. Im Einzelspieler reicht die Installation im Client.

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

Startet einen echten Minecraft-Client, legt eine Testwelt an, gibt dem Spieler zwei Netherite-Schwerter und prüft, dass die Klinge gerendert wird. Die Screenshots (Ego-Perspektive und Third Person, Tag und Nacht) landen in `build/run/clientGameTest/screenshots`. Auf GitHub läuft dieser Test bei jedem Push mit, die Bilder findest du im Lauf unter **Artifacts → Screenshots**. Ohne `xvfb-run` öffnet sich ein normales Spielfenster.

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
| `render/SaberBladeSpecialRenderer.java` | Item-Renderer `sabercraft:saber_blade`, im Item-Modell nutzbar |
| `resources/assets/minecraft/items/netherite_sword.json` | Ersetzt das Netherite-Schwert: Griff im Inventar, Griff mit Klinge in der Hand |

## Rechtliches

Alle Sounds und Texturen dieser Mod sind selbst erstellt. Sie enthalten keine Filmaufnahmen oder Originalmusik.
