# SaberCraft

Fabric-Mod für Minecraft Java 26.2, die das Netherite-Schwert in ein filmreifes Energieschwert verwandelt.

**Stand:** Phase 7. Nimmst du ein Netherite-Schwert in die Hand, kommt der Griff hoch und die Energieklinge fährt mit einem Zündgeräusch aus. Solange sie leuchtet, summt sie. Griff, Klingenfarbe und einer von fünf Kampfstilen sind pro Schwert wählbar. Du kämpfst mit eigenen Bewegungen samt Lichtspur und Funken, mit Ganzkörper-Ausweichen und Abwehr samt Parade und Konter, alles mit Ausdauer. Dazu kommen Machtkräfte (Stoß, Griff, Zug, Sprung) und das Abwehren von Geschossen. Optional gibt es einen Weltraum-Himmel und eigene Musik. Fünf Outfits liegen über deinem eigenen Skin. Die Taste **V** öffnet das SaberCraft-Menü mit allen Einstellungen. Details: [docs/phase-7-status.md](docs/phase-7-status.md), zum Kampf [docs/phase-6-status.md](docs/phase-6-status.md), zu Farben und Griffen [docs/phase-5-status.md](docs/phase-5-status.md), zur Animation [docs/phase-4-status.md](docs/phase-4-status.md), zur Klinge selbst [docs/phase-3-status.md](docs/phase-3-status.md).

![Alle sechs Klingenfarben](docs/images/phase5/aufstellung_nacht.png)

## Voraussetzungen

| Komponente | Version |
| --- | --- |
| Minecraft Java | 26.2 |
| Java (JDK) | 25 |
| Fabric Loader | 0.19.5 oder neuer |
| Fabric API | 0.161.0+26.2 |

Für Aussehen, Animationen, Effekte, Himmel, Musik und dein eigenes Outfit musst du die Mod nicht auf dem Server installieren. Ohne die Mod auf dem Server sieht nur du dein Outfit. Kräfte, Abwehr, Ausdauer, abgewehrte Geschosse und das Teilen der Outfits entscheidet der Server, dafür braucht er SaberCraft. Griff und Farbe stehen im Vanilla-Feld `minecraft:custom_model_data` des Schwerts, das Minecraft ohnehin an alle Spieler schickt. Andere Spieler sehen und hören dein Lichtschwert, wenn sie SaberCraft ebenfalls installiert haben. Nur wer auf einem Server im Überlebensmodus Griff oder Farbe ändern will, braucht SaberCraft auch auf dem Server. Im Einzelspieler und im Kreativmodus geht es immer. Eine genaue Tabelle steht in [docs/phase-7-status.md](docs/phase-7-status.md#was-der-client-macht-und-was-der-server).

## Lichtschwert anpassen

1. Nimm ein Netherite-Schwert in die Hand.
2. Drück **V** (änderbar unter Optionen → Steuerung → SaberCraft). Das SaberCraft-Menü öffnet sich im Bereich **Schwert**.
3. Wähle den Griff, die Klingenfarbe und den Kampfstil, dann **Übernehmen**.

Die anderen Bereiche des Menüs: **Kampf** (Tasten und Kampfoptionen), **Charakter** (Outfits mit Vorschau), **Weltraum** (Himmel), **Musik**, **Grafik** (Effekte und Qualität).

Die Werte jeder Farbe (Kern-, Bloom- und Halofarbe, Länge, Dicke, Leuchtstärke, Flimmern) stehen in `config/sabercraft/blade_styles.json`.

## Kämpfen

| Taste | Aktion |
| --- | --- |
| Linke Maustaste | Angriff, die Bewegung hängt vom Kampfstil ab (im Sprint ein Stich) |
| **R** (halten) | Abwehrhaltung, direkt nach dem Hochnehmen eine Parade |
| **Z** | Drehangriff, mit der Doppelklinge der Klingenwirbel |
| **B** | Machtstoß |
| **Linke Alt-Taste** | Ausweichen: Rolle, Seitschritt, Rückschritt, Ausfallschritt oder Salto, je nach Stil und gedrückter Laufrichtung (WASD) |
| **H** (halten) | Machtgriff |
| **J** | Machtzug |
| **K** | Machtsprung |

Alle Tasten sind unter Optionen → Steuerung → SaberCraft änderbar. Abklingzeiten, Stärken und Ausdauer stehen in `config/sabercraft/abilities.json` (Server). Effekte und Animationen stehen in `config/sabercraft/combat_client.json`, Himmel und Musik in `immersion_client.json`, das Outfit in `character_client.json`. Fast alles davon lässt sich auch im Menü einstellen.

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

Startet einen echten Minecraft-Client, legt eine Testwelt an und prüft die Klinge: Darstellung, Ein- und Ausfahren, Sounds, Itemwechsel, Tod und Respawn, Mobs mit Schwert, alle Griffe und Farben, das Auswahlmenü, alle Kampfbewegungen, Lichtspuren und Funken, die Fähigkeiten mit Schaden und Abklingzeiten und das Spiel auf einem echten Dedicated Server. Die Screenshots (Ego-Perspektive und Third Person, Tag und Nacht, Bild für Bild beim Ein- und Ausfahren, jede Farbe und jeder Griff, Inventar und Menü) landen in `build/run/clientGameTest/screenshots`. Auf GitHub läuft dieser Test bei jedem Push mit, die Bilder findest du im Lauf unter **Artifacts → Screenshots**. Ohne `xvfb-run` öffnet sich ein normales Spielfenster.

## Installieren

1. Installiere Fabric Loader für Minecraft 26.2 mit dem [Fabric Installer](https://fabricmc.net/use/installer/).
2. Lege die [Fabric API](https://modrinth.com/mod/fabric-api) für 26.2 in den Ordner `mods` deiner Minecraft-Installation.
3. Lege `sabercraft-<version>.jar` in denselben Ordner `mods`.
4. Starte Minecraft mit dem Fabric-Profil.

## Projektstruktur

```
src/main/      Code für Client und Server (Variante am Item, Pakete, Fähigkeiten)
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

### Phase 7: Immersion

| Datei | Aufgabe |
| --- | --- |
| `client/sky/` | Weltraum-Himmel je Dimension |
| `client/music/` | Musik mit Stimmungen und Überblendung, eigene Dateien |
| `client/character/`, `src/main/.../character/` | Outfits über dem eigenen Skin, Abgleich über den Server |
| `client/menu/` | SaberCraft-Menü (Taste V) |
| `tools/synth_music.py`, `tools/paint_outfits.py` | erzeugen Musik und Outfits neu |

### Kampf und Fähigkeiten

| Datei | Aufgabe |
| --- | --- |
| `resources/assets/sabercraft/combat/moves.json` | Alle Bewegungen und Kampfstile als Keyframes (Ego-Perspektive und Third Person) |
| `combat/CombatLibrary.java`, `CombatMove.java`, `CombatStyle.java` | Lädt die Bewegungen und Stile |
| `combat/CombatState.java` | Welche Bewegung eine Figur gerade macht, Kombination, Übergänge, Abwehrhaltung |
| `combat/SaberCombat.java`, `mixin/client/` | Wendet die Posen in Ego-Perspektive und Third Person an |
| `render/BladeTrails.java` | Lichtspur der Klinge |
| `effect/SaberEffects.java` | Funken, Rauch, Luftstoß |
| `ability/SaberAbilityClient.java`, `ability/AbilityHud.java` | Tasten, Animation und Sound der Fähigkeiten, Abklingzeit-Anzeige |
| `src/main/.../ability/SaberAbilities.java` | Server: Schaden, Abwehr, Machtstoß, Ausfallschritt, Abklingzeiten |
| `src/main/.../mixin/LivingEntityMixin.java` | Lässt die Abwehrhaltung Schaden blocken |
| `tools/preview_moves.py` | Zeichnet Strichfiguren-Vorschauen der Bewegungen nach `build/move-previews` |

## Rechtliches

Alle Sounds, Modelle und Texturen dieser Mod sind selbst erstellt. Die Griffe und ihre Texturen erzeugt `tools/gen_hilts.py`. Die Namen der Griffe im Spiel sind bewusst neutral gehalten (Hüter, Dunkler Lord, Wächter). Die Sounds werden mit `tools/synth_sounds.py` vollständig aus Sinuswellen, Rauschen und Hüllkurven berechnet. Sie enthalten keine Filmaufnahmen, Samples oder Originalmusik. Die sechs Musikstücke sind eigene Kompositionen aus `tools/synth_music.py`, die Outfits eigene Entwürfe aus `tools/paint_outfits.py`. Kein Spieler-Skin wird ersetzt.
