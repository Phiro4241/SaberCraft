# GitHub: Repository-Seite

Einstellungen, die nicht im Code stehen und von Phiro auf GitHub gesetzt werden (Repository → ⚙️ neben „About“, bzw. Settings → General). Nichts davon kostet etwas.

## About (rechte Spalte)

**Description** (max. 350 Zeichen, wird auch in Suchergebnissen gezeigt):

```
Fabric mod for Minecraft Java 26.2: film-style lightsabers, a six-mission stealth story aboard a Star Destroyer, blasters, droids and starships. Unofficial fan mod with original music and art.
```

**Website:** vorerst leer lassen. Sobald die Modrinth-Seite online ist, deren Adresse eintragen.

**Topics** (Kleinbuchstaben, Bindestriche; GitHub erlaubt bis zu 20):

```
minecraft  minecraft-mod  fabric  fabricmc  fabric-mod  minecraft-fabric  java  lightsaber  star-wars  fan-made  story-mode  stealth-game  starships  droids  blaster  procedural-audio
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
6. Danach im README unter „Downloads and links“ den Satz „once a version is published“ entfernen.

## Was auf der Seite schon stimmt (diese Phase)

- README auf Englisch und Deutsch, neu gegliedert: Story-Modus, Features mit Status, Screenshots aus dem Spiel, Anforderungen, Installation, Steuerung, bekannte Grenzen, Links, Unterstützung, Lizenz.
- Banner und Social Preview mit dem aktuellen Untertitel.
- Interne Phasen-Notizen liegen in `docs/development/` und sind nicht mehr Teil der öffentlichen Beschreibung.
