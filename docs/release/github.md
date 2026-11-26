# GitHub: Repository-Seite

Einstellungen, die nicht im Code stehen und von Phiro auf GitHub gesetzt werden (Repository → ⚙️ neben „About“, bzw. Settings → General). Nichts davon kostet etwas.

## About (rechte Spalte)

**Description** (max. 350 Zeichen, wird auch in Suchergebnissen gezeigt; dieser Text hat 279):

```
Lightsabers, starships and a six-mission stealth story for Minecraft 26.2 (Fabric): 15 fighting styles and the Force, five starships to fly, a Millennium Falcon and Star Destroyer to walk through, droids, blasters and planet skies. Unofficial fan mod with original music and art.
```

**Website:** vorerst leer lassen. Sobald die Modrinth-Seite online ist, deren Adresse eintragen.

**Topics** (Kleinbuchstaben, Bindestriche; GitHub erlaubt bis zu 20):

```
minecraft  minecraft-mod  fabric  fabricmc  fabric-mod  minecraft-fabric  java  lightsaber  star-wars  fan-made  story-mode  stealth  starships  spaceships  droids  blaster  minecraft-26
```

Häkchen bei „Include in the home page“: **Releases** an (sobald es einen gibt), **Packages** und **Deployments** aus.

## Social Preview

Settings → General → Social preview → **Edit** → Upload: [`docs/media/social-preview.png`](../media/social-preview.png) (1280×640, erzeugt von `tools/gen_banner.py`). Das Bild erscheint, wenn der Link in Discord, Reddit, X usw. geteilt wird.

## Features (Settings → General → Features)

- **Issues:** an (die Vorlagen für Bug Report und Feature Request liegen in `.github/ISSUE_TEMPLATE/`).
- **Discussions:** optional; wenn an, im README unter „Contributing“ verlinken.
- **Wiki:** aus (die Dokumentation liegt im Repository).
- **Sponsorships:** nur einschalten, wenn Phiro GitHub Sponsors einrichten will. Für Patreon genügt eine `.github/FUNDING.yml` mit `patreon: <name>`; sie ist **noch nicht angelegt**, weil der Patreon-Name auf GitHub genau der in der Patreon-URL sein muss (siehe [`patreon.md`](patreon.md)).

## Release v0.1.0 (erst wenn Phiro es will)

Nicht in dieser Phase. Wenn es so weit ist:

1. Releases → **Draft a new release**.
2. Tag: `v0.1.0` (neu, auf `main`), Titel: `SaberCraft 0.1.0`.
3. Text: Inhalt von [`release-notes-0.1.0.md`](release-notes-0.1.0.md).
4. Datei anhängen: `sabercraft-0.1.0.jar` aus `./gradlew build` (ohne `-sources`).
5. **Set as a pre-release** anhaken (erste öffentliche Version, Beta).
6. Danach in README und README.de im Abschnitt „Support“ / „Unterstützen“ den Satz über die Downloads durch die Links zum Release und zur Modrinth-Seite ersetzen.

## Was auf der Seite jetzt stimmt

- README auf Englisch und Deutsch von Grund auf neu geschrieben, in einem Stil und mit neuem Aufbau: Banner und vier Bilder zum Einstieg, Lichtschwerter und Kampf, die Story „Nightshade“ mit sechs Bildern, Raumschiffe mit sechs Bildern, Droiden und Blaster, Figuren, Himmel und Musik, Zusammen spielen, eine Galerie, Loslegen, Steuerung (Story, Schiffe, Befehle und Einstellungen aufklappbar), Gut zu wissen, Unterstützen, Lizenz.
- Alle 30 Bilder neu, im Spiel aufgenommen in 1920×1080 (Liste und Herkunft in [`screenshots.md`](screenshots.md)); die alten Bilder sind gelöscht oder liegen nur noch in den Entwicklungsnotizen.
- Banner und Social Preview neu, aus drei echten Spielbildern zusammengesetzt (`tools/gen_banner.py`).
- Die Entwicklungsnotizen samt ihren alten Bildern und Hörproben liegen in `docs/development/` und sind nicht Teil der öffentlichen Seite.
- Das Icon (`src/main/resources/assets/sabercraft/icon.png`, 512×512, zwei gekreuzte Klingen vor einem Planeten) passt weiter und bleibt.
- Beschreibung und Topics oben sind Vorschläge; GitHub übernimmt sie erst, wenn Phiro sie unter „About“ einträgt.
