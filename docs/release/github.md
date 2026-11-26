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

Settings → General → Social preview → **Edit** → Upload: [`docs/media/sabercraft-social-preview.png`](../media/sabercraft-social-preview.png) (1280×640, unter 1 MB, erzeugt von `tools/gen_banner.py`, gleicher Aufbau wie das README-Banner). Das Bild erscheint, wenn der Link in Discord, Reddit, X usw. geteilt wird.

## Features (Settings → General → Features)

- **Issues:** an (die Vorlagen für Bug Report und Feature Request liegen in `.github/ISSUE_TEMPLATE/`).
- **Discussions:** optional; wenn an, im README unter „Contributing“ verlinken.
- **Wiki:** aus (die Dokumentation liegt im Repository).
- **Sponsorships:** einschalten, damit oben auf der Seite der Knopf „Sponsor“ erscheint. Er zeigt den Patreon-Link aus [`.github/FUNDING.yml`](../../.github/FUNDING.yml) (liegt im Repository). GitHub Sponsors selbst muss dafür nicht eingerichtet werden.

## Release v0.1.0

Alles liegt bereit; anlegen muss ihn Phiro auf GitHub (meine GitHub-Anbindung kann keine Releases oder Tags anlegen und keine Dateien an einen Release hängen).

1. Den PR mit diesem Stand mergen (damit README, Banner und Patreon-Link im Quellcode des Releases stecken).
2. Releases → **Draft a new release**.
3. **Choose a tag** → `v0.1.0` eintippen → „Create new tag: v0.1.0 on publish“, **Target:** `main`.
4. **Release title:** `SaberCraft 0.1.0`.
5. **Describe this release:** alles aus [`github-release-0.1.0.md`](github-release-0.1.0.md) unter der ersten Zeile einfügen (Englisch und Deutsch).
6. **Attach binaries:** `sabercraft-0.1.0.jar` aus dem Release-Ordner in den Projektdateien (die getestete JAR; die Prüfsumme steht am Ende des Textes). Keine `-sources`-JAR nötig.
7. **Set as a pre-release** nach Wunsch (erste öffentliche Version); **Set as the latest release** sonst angehakt lassen.
8. Erst als **Save draft** speichern, ansehen, dann **Publish release**.
9. Danach in README und README.de im Abschnitt „Support“ / „Unterstützen“ den Satz „Downloads will be on the Releases page …“ / „Downloads gibt es …, sobald …“ auf den Release verlinken.

## Was auf der Seite jetzt stimmt

- README auf Englisch und Deutsch von Grund auf neu geschrieben, in einem Stil und mit neuem Aufbau: Banner und vier Bilder zum Einstieg, Lichtschwerter und Kampf, die Story „Nightshade“ mit sechs Bildern, Raumschiffe mit sechs Bildern, Droiden und Blaster, Figuren, Himmel und Musik, Zusammen spielen, eine Galerie, Loslegen, Steuerung (Story, Schiffe, Befehle und Einstellungen aufklappbar), Gut zu wissen, Unterstützen, Lizenz.
- Alle 30 Bilder neu, im Spiel aufgenommen in 1920×1080 (Liste und Herkunft in [`screenshots.md`](screenshots.md)); die alten Bilder sind gelöscht oder liegen nur noch in den Entwicklungsnotizen.
- Banner und Social Preview neu und unter neuem Dateinamen (`sabercraft-banner.png`, `sabercraft-social-preview.png`), damit kein Browser mehr das alte X-Wing-Banner aus dem Zwischenspeicher zeigt: Icon, Titel und sechs beschriftete Spielbilder (Lichtschwert, X-Wing, TIE Interceptor, Story-Duell, Millennium Falcon, Sternzerstörer), `tools/gen_banner.py`. Die alten Dateien `banner.png` und `social-preview.png` sind gelöscht.
- Der Patreon-Link steht im README (EN und DE) unter „Support“ / „Unterstützen“ und in `.github/FUNDING.yml`.
- Die Entwicklungsnotizen samt ihren alten Bildern und Hörproben liegen in `docs/development/` und sind nicht Teil der öffentlichen Seite.
- Das Icon (`src/main/resources/assets/sabercraft/icon.png`, 512×512, zwei gekreuzte Klingen vor einem Planeten) passt weiter und bleibt.
- Beschreibung und Topics oben sind Vorschläge; GitHub übernimmt sie erst, wenn Phiro sie unter „About“ einträgt.
