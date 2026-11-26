# Release-Vorbereitung SaberCraft 0.1.0

Dieser Ordner sammelt alles, was für die erste öffentliche Version gebraucht wird: Texte für GitHub, Modrinth, ModDB und Patreon, die Release Notes, eine Installationsanleitung und den Bilderplan. **Nichts davon ist veröffentlicht.** Es gibt keinen GitHub-Release, kein Tag, keine Modrinth- und keine ModDB-Seite.

| Datei | Inhalt |
| --- | --- |
| [`github.md`](github.md) | Repository-Beschreibung, Website, Topics, Social Preview, Release-Text |
| [`modrinth.md`](modrinth.md) | Schritt-für-Schritt-Anleitung für den eigenen Modrinth-Upload: alle Felder, welche Datei wohin, Checkliste |
| [`modrinth-description.md`](modrinth-description.md) | Die englische Beschreibung für die Modrinth-Seite (Markdown, zum Einfügen) |
| [`moddb.md`](moddb.md) | Alle Felder des ModDB-Profils, erster Download, Ankündigung |
| [`patreon.md`](patreon.md) | Wo der Patreon-Link hinkommt (README, Modrinth, ModDB), sobald die Adresse geprüft ist |
| [`release-notes-0.1.0.md`](release-notes-0.1.0.md) | Release Notes und Changelog für 0.1.0 (GitHub-Release und Modrinth-Version) |
| [`installation.md`](installation.md) | Installationsanleitung zum Kopieren auf die Plattformen |
| [`screenshots.md`](screenshots.md) | Alle Bilder: was sie zeigen, wie sie entstanden sind, wo sie verwendet werden |

## Versionsdaten (aus den Projektdateien)

Alle Seiten verwenden dieselben Angaben. Quelle: `gradle.properties` und `src/main/resources/fabric.mod.json`.

| Angabe | Wert |
| --- | --- |
| Name | SaberCraft |
| Mod-ID | `sabercraft` |
| Version | **0.1.0** (Beta, erste öffentliche Version) |
| Minecraft | **Java Edition 26.2** (nur diese Version) |
| Loader | **Fabric**, Fabric Loader 0.19.5 oder neuer |
| Abhängigkeit | **Fabric API** (Pflicht), gebaut und getestet mit 0.161.0+26.2 |
| Java | 25 (bringt der Minecraft Launcher mit) |
| Umgebung | Client und Server (Einzelspieler: alles; Server: Mod auf Server und Clients) |
| Lizenz | MIT (Code und eigene Assets), siehe Abschnitt „Legal“ im README |
| Quellcode | https://github.com/Phiro4241/SaberCraft |
| Issues | https://github.com/Phiro4241/SaberCraft/issues |
| Datei | `sabercraft-0.1.0.jar` (nicht die `-sources.jar`) |

## Kurzbeschreibung (überall gleich)

**Englisch (Hauptsprache der Plattformen):**

> Lightsabers, starships and a six-mission story campaign for Minecraft 26.2. 15 fighting styles, the Force, droids, blasters and planet skies. Unofficial fan mod.

**Deutsch:**

> Lichtschwerter, Raumschiffe und eine Story-Kampagne in sechs Missionen für Minecraft 26.2. 15 Kampfstile, die Macht, Droiden, Blaster und Planetenhimmel. Inoffizielle Fan-Mod.

## Reihenfolge beim Veröffentlichen (wenn Phiro so weit ist)

1. PR dieser Phase prüfen und mergen.
2. Patreon-Link prüfen und in README, README.de, `modrinth.md`, `moddb.md` eintragen (siehe [`patreon.md`](patreon.md)).
3. GitHub-Einstellungen setzen (Beschreibung, Topics, Social Preview), siehe [`github.md`](github.md).
4. JAR aus dem gemergten `main` bauen (`./gradlew build`), einmal selbst starten.
5. GitHub-Release `v0.1.0` mit den Release Notes und der JAR anlegen.
6. Modrinth: der Anleitung [`modrinth.md`](modrinth.md) folgen, mit den Dateien aus dem Upload-Paket (Projekt anlegen, Offenlegung, Version 0.1.0, zur Prüfung einreichen).
7. ModDB-Profil anlegen, Bilder und Download hochladen, Ankündigung als Entwurf vorbereiten.
8. Links zu Release und Modrinth im README unter „Support“ und in README.de unter „Unterstützen“ nachtragen, die Modrinth-Adresse als Website auf GitHub.

## Offene Punkte, die nur Phiro klären kann

1. **Patreon-URL.** Ich konnte die Patreon-Seite nicht aufrufen (Patreon ist aus meiner Umgebung nicht erreichbar). Gebraucht wird die genaue Adresse der Seite, so wie sie ausgeloggt im Browser steht, zum Beispiel `https://www.patreon.com/Phiro42421` oder `https://www.patreon.com/c/Phiro42421`. Bis dahin stehen überall deutlich markierte Platzhalter.
2. **KI-Offenlegung auf Modrinth (wichtig).** Modrinth hat im August 2026 eine KI-Richtlinie eingeführt: Projekte müssen generative KI im Tab „Disclosures“ offenlegen, und ein Projekt, das vollständig oder überwiegend von generativer KI erstellt wurde, kann abgelehnt oder aus der Suche genommen werden. SaberCraft wurde unter Phiros Leitung weitgehend mit einem KI-Assistenten programmiert; auch die Skripte, die Modelle, Texturen, Klänge und Musik erzeugen, stammen daraus (es wurden keine Bild- oder Musik-KI-Generatoren verwendet, die Assets entstehen prozedural aus Code). Das muss ehrlich offengelegt werden, und es ist möglich, dass Modrinth das Projekt deshalb nicht annimmt. Quellen: [Modrinth: AI policy and disclosures](https://modrinth.com/news/article/ai-policy-and-disclosures/), [Modrinth Content Rules](https://modrinth.com/legal/rules). Ich konnte beide Seiten nicht direkt öffnen, nur über Suchergebnisse lesen; bitte vor dem Einreichen selbst lesen.
3. **Star-Wars-Namen und Patreon.** SaberCraft ist eine inoffizielle Fan-Mod und benutzt Namen von Lucasfilm/Disney (Star Wars, Darth Vader, X-Wing …). Plattformen können solche Projekte auf Hinweis der Rechteinhaber entfernen. Ein Patreon-Link neben einer Fan-Mod kann als kommerzielle Nutzung gesehen werden. Die Texte sagen deshalb klar: freiwillige Unterstützung für Phiros Arbeit, schaltet nichts frei, kein Inhalt hinter einer Bezahlschranke. Ob der Link trotzdem auf allen Plattformen stehen soll, entscheidet Phiro.
4. **Bilder.** Alle Bilder auf der Seite sind neu und im Spiel aufgenommen (1920×1080, automatisch, Software-Rendering ohne Grafikkarte). Auf einer echten Grafikkarte sieht das Spiel etwas schärfer aus; eigene Aufnahmen kann Phiro jederzeit ergänzen, siehe [`screenshots.md`](screenshots.md).
5. **Kategorien.** Die Modrinth-Kategorien stammen aus Modrinths eigener Liste (über die API geprüft); welche davon passen, ist ein Vorschlag. Die ModDB-Felder zeigt erst das Formular.

## Was die Seiten nicht versprechen

- Keine anderen Minecraft-Versionen, kein Forge, NeoForge oder Quilt.
- Kein Koop-Story-Modus (auf Servern spielt jeder seine eigene Story).
- Nur Kapitel 1 der Story.
- Keine Filmmusik, keine Filmsounds, keine Filmbilder.
- Keine Termine für weitere Kapitel oder Versionen.
