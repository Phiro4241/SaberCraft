# SaberCraft auf Modrinth veröffentlichen: Schritt für Schritt

Diese Anleitung ist für den eigenen Upload gedacht, auch wenn du noch nie etwas auf Modrinth veröffentlicht hast. **Nichts davon ist bisher gemacht:** kein Projekt, keine Datei, keine Version auf Modrinth. Alle Dateien liegen fertig im Upload-Paket (Ordner `modrinth-upload` in den Projektdateien, Aufbau siehe unten); ihre Quellen stehen hier im Repository.

## Was geprüft ist und was nicht

Die Modrinth-Webseiten (modrinth.com, support.modrinth.com, docs.modrinth.com) sind aus meiner Arbeitsumgebung gesperrt. Geprüft habe ich deshalb so:

- **Direkt über die offizielle Modrinth-API (api.modrinth.com), am 26.11.2026:** die Kategorien für Mods, die Loader, dass **26.2** als Release-Version geführt wird, dass die Lizenz **MIT** in Modrinths Liste steht, dass **Patreon** als Spendenplattform angeboten wird, dass die Adresse **`sabercraft`** noch frei ist, und dass **Fabric API** (Projekt `fabric-api`) eine Version **0.161.0+26.2** für 26.2 hat.
- **Über Suchergebnisse aus Modrinths eigenen Seiten** (Support-Artikel, Neuigkeiten, Inhaltsregeln, API-Dokumentation): dass man ein Projekt über das Menü unter dem eigenen Profilbild mit **„Create a project“** anlegt; dass Beschreibung, Galerie, Versionen und die Veröffentlichungs-Checkliste in den **Projekteinstellungen** liegen; dass es dort einen Reiter **„Disclosures“** mit der Angabe **„Contains AI-generated content“** gibt; dass die Umgebung (Client/Server) **bei der Version** angegeben wird; dass ein Projekt zur Prüfung eingereicht wird und Moderatoren es prüfen. Diese Stellen sind unten mit *(geprüft)* markiert.
- **Nicht geprüft** sind die genauen Beschriftungen der übrigen Felder und Knöpfe. Wo ich sie nicht belegen kann, beschreibe ich, **was** einzutragen ist, nicht wie der Knopf heißt. Wenn das Formular etwas anders aussieht, gilt das Formular.

## Bevor du anfängst

1. **Den Pull Request dieser Phase prüfen und mergen.** Die Bilder in der Modrinth-Beschreibung werden direkt von GitHub geladen (`main`-Zweig) und erscheinen erst, wenn die neuen Bilder dort liegen.
2. **Über die KI-Offenlegung entscheiden (wichtig).** Seit August 2026 gilt auf Modrinth *(geprüft)*: Wer generative KI verwendet hat, muss in den Projekteinstellungen unter **Disclosures** die Angabe **„Contains AI-generated content“** einschalten, auch dann, wenn ein wesentlicher Teil des Codes KI-Ausgabe ist. Projekte, die **ganz oder fast ganz mit generativer KI und mit wenig menschlichem Beitrag über Prompts und Testen hinaus** entstanden sind, dürfen laut Modrinths Support-Artikel **nicht öffentlich veröffentlicht werden**, und überwiegend KI-erzeugte Bilder sind auf Projektseiten nicht erlaubt. SaberCraft wurde unter deiner Leitung weitgehend mit einem KI-Assistenten programmiert, auch die Skripte, die Modelle, Texturen, Klänge und Musik erzeugen. Die Screenshots sind echte Bilder aus dem Spiel, keine Bilder aus einem KI-Bildgenerator. **Es ist realistisch möglich, dass Modrinth das Projekt deshalb ablehnt oder nicht in der Suche zeigt.** Die Angabe muss trotzdem ehrlich sein; einen Textvorschlag findest du unten. Lies vorher selbst: [AI policy and disclosures](https://modrinth.com/news/article/ai-policy-and-disclosures/), [Disclosure and Usage of AI](https://support.modrinth.com/en/articles/16551575-disclosure-and-usage-of-ai), [Content Rules](https://modrinth.com/legal/rules).
3. **Patreon-Adresse bereithalten** (oder den Link weglassen). Ich konnte sie nicht prüfen; in der Beschreibung steht der Platzhalter `PATREON_URL_EINTRAGEN`.

## Das Upload-Paket

| Datei im Paket | Wofür | Quelle im Repository |
| --- | --- | --- |
| `00-ANLEITUNG-Modrinth-Upload.md` | diese Anleitung | `docs/release/modrinth.md` |
| `01-mod-datei/sabercraft-0.1.0.jar` | die Datei der Version | gebaut mit `./gradlew build` |
| `02-projekt/icon-512.png` | Projekt-Icon | `src/main/resources/assets/sabercraft/icon.png` |
| `02-projekt/zusammenfassung.txt` | Summary (Kurzbeschreibung) | unten |
| `02-projekt/beschreibung.md` | Description (Beschreibung, Markdown) | `docs/release/modrinth-description.md` |
| `03-galerie/01-…jpg` bis `10-…jpg` | Galerie-Bilder | `docs/media/screenshots/` |
| `03-galerie/galerie-titel.md` | Titel und Beschreibung jedes Bildes | unten |
| `04-version/changelog-0.1.0.md` | Changelog der Version | `docs/release/release-notes-0.1.0.md` |
| `04-version/angaben.md` | alle Felder der Version auf einen Blick | unten |
| `05-ki-offenlegung.txt` | Text für eine Freitext-Angabe zur KI | unten |
| `06-lizenz/LICENSE.txt` | der Lizenztext (MIT), nur zur Info: auf Modrinth wird die Lizenz ausgewählt, nicht hochgeladen | `LICENSE` |
| `07-github/` | Beschreibung, Topics und Social-Preview-Bild für die GitHub-Seite (kein Modrinth-Feld) | `docs/release/github.md`, `docs/media/social-preview.png` |

Das ganze Paket gibt es zusätzlich als **`modrinth-upload.zip`** zum Herunterladen. **Auf Modrinth wird nie die ZIP hochgeladen**, sondern jede Datei einzeln an ihrer Stelle: die JAR bei der Version, das Icon und die Bilder in den Einstellungen, die Texte per Kopieren und Einfügen.

## Schritt 1: Projekt anlegen

1. Auf [modrinth.com](https://modrinth.com) anmelden.
2. Oben rechts auf dein Profilbild klicken und **„Create a project“** wählen *(geprüft)*.
3. Im Dialog eintragen (Beschriftungen nicht geprüft):
   - **Name:** `SaberCraft`
   - **URL / Adresse:** `sabercraft` (am 26.11.2026 frei; die Seite wird dann `modrinth.com/mod/sabercraft`)
   - **Projektart:** Mod, falls gefragt
   - **Summary:** Inhalt von `02-projekt/zusammenfassung.txt`:
     > Lightsabers, starships and a six-mission story campaign for Minecraft 26.2. 15 fighting styles, the Force, droids, blasters and planet skies. Unofficial fan mod.
   - **Sichtbarkeit:** falls gefragt, so, dass das Projekt erst nach deiner Prüfung öffentlich wird (Entwurf/privat). Ein neues Projekt ist ohnehin erst öffentlich, wenn Modrinth es nach dem Einreichen freigegeben hat.
4. Anlegen. Du landest auf der (noch leeren) Projektseite.

## Schritt 2: Projekteinstellungen ausfüllen

Öffne die **Einstellungen des Projekts** *(geprüft: Beschreibung, Galerie, Lizenz, Versionen und Disclosures liegen dort)* und fülle Reiter für Reiter aus:

**Allgemein (General)**
- **Icon:** `02-projekt/icon-512.png` hochladen.
- Name und Summary prüfen (wie oben).

**Kategorien / Tags**
- Vorschlag aus Modrinths Kategorienliste *(geprüft über die API)*: **Adventure**, **Equipment**, **Transportation**; zusätzlich passend: **Game Mechanics**, **Mobs**. Erlaubt das Formular weniger, nimm die ersten drei.

**Beschreibung (Description)**
- Den ganzen Inhalt von `02-projekt/beschreibung.md` einfügen. Vorher `PATREON_URL_EINTRAGEN` durch deine geprüfte Patreon-Adresse ersetzen oder die letzte Zeile löschen.
- Die Beschreibung ist auf Englisch; Modrinth verlangt eine englische Fassung *(geprüft, Inhaltsregeln)*.

**Lizenz (License)**
- **MIT** auswählen *(in Modrinths Lizenzliste geprüft, Kennung `MIT`)*.

**Links**
- Quellcode: `https://github.com/Phiro4241/SaberCraft`
- Issues: `https://github.com/Phiro4241/SaberCraft/issues`
- Wiki: leer lassen (oder `https://github.com/Phiro4241/SaberCraft#readme`)
- Discord: leer lassen
- Spenden: **Patreon** *(als Plattform geprüft)* mit deiner Adresse, erst wenn sie geprüft ist. Modrinth verlangt, dass alles Beworbene frei verfügbar ist; das ist bei SaberCraft so.

**Galerie (Gallery)** *(geprüft: in den Projekteinstellungen)*
- Die Bilder aus `03-galerie/` in der Reihenfolge der Nummern hochladen, Titel und Beschreibung aus `galerie-titel.md` übernehmen, **Bild 01 als hervorgehobenes Bild (Featured)** markieren.
- Alle Bilder sind echte Aufnahmen aus dem Spiel (Szenen mit den Inhalten der Mod aufgebaut, nichts übermalt), keine KI-Bilder.

**Disclosures** *(geprüft)*
- **„Contains AI-generated content“** einschalten (siehe „Bevor du anfängst“, Punkt 2). Gibt es ein Freitextfeld, den Text aus `05-ki-offenlegung.txt` verwenden:
  > SaberCraft was developed with extensive help from an AI coding assistant: the source code and the scripts that generate its models, textures, sounds and music were written with it, directed, reviewed and play-tested by the author. No images, sounds or music come from AI image or music generators; all assets are generated procedurally by those scripts. The screenshots are taken in the game.
- Weitere Angaben dort (falls angeboten) wahrheitsgemäß: kein Inhalt hinter einer Bezahlschranke; Star-Wars-Namen werden beschreibend verwendet, keine Lizenz von Lucasfilm/Disney.

## Schritt 3: Die erste Version hochladen

In den Projekteinstellungen (oder auf der Projektseite) eine **neue Version** anlegen *(geprüft: Versionen werden in den Projekteinstellungen bearbeitet)* und eintragen:

| Feld | Eintrag |
| --- | --- |
| Datei | `01-mod-datei/sabercraft-0.1.0.jar` (nur diese, **nicht** eine `-sources.jar`) |
| Versionsnummer | `0.1.0` |
| Versionsname / Titel | `SaberCraft 0.1.0` |
| Kanal | **Beta** (erste öffentliche Version) |
| Loader | **Fabric** |
| Spielversionen | **26.2** (nur diese; keine Snapshots, nicht 26.3) |
| Umgebung | **Client und Server**: auf beiden Seiten nötig *(geprüft: wird bei der Version angegeben; die API kennt dafür den Wert `client_and_server`)*. Nicht „nur Client“ wählen: ohne die Mod auf dem Server fehlen Story, Fähigkeiten, Blaster, Droiden und Schiffe. |
| Abhängigkeit | **Fabric API**, Projekt `fabric-api`, **erforderlich (required)** |
| Changelog | Inhalt von `04-version/changelog-0.1.0.md` |

Modrinth kann Versionsnummer, Loader und Spielversion aus der Datei vorschlagen; prüfe die Vorschläge gegen die Tabelle.

## Schritt 4: Prüfen und einreichen

1. Die **Veröffentlichungs-Checkliste** in den Projekteinstellungen durchgehen *(geprüft: sie liegt dort)*; sie zeigt, was noch fehlt.
2. Die Projektseite einmal ansehen: Icon, Beschreibung mit Bildern, Galerie, Version mit Datei.
3. **Zur Prüfung einreichen.** Moderatoren prüfen das Projekt *(geprüft)*; laut Support-Artikel dauert das meist 24 bis 48 Stunden. Fragen oder Ablehnungsgründe kommen über Modrinth.

## Schritt 5: Nach der Freigabe

1. Die Modrinth-Adresse im GitHub-Repository als **Website** eintragen (siehe [`github.md`](github.md)).
2. In README und README.de im Abschnitt „Support“ / „Unterstützen“ den Download-Satz durch den Modrinth-Link ersetzen.
3. Später für neue Versionen: in den Projekteinstellungen eine neue Version hochladen, Versionsnummer erhöhen (`gradle.properties`, `mod_version`).

## Galerie: Titel und Beschreibungen

| # | Datei | Titel | Beschreibung |
| --- | --- | --- | --- |
| 01 | `01-hero-saber.jpg` | A lightsaber of your own | Any sword you choose becomes a lightsaber, here under the sky of Mustafar. |
| 02 | `02-ships-fleet.jpg` | Five starships | X-Wing, TIE Fighter, TIE Interceptor, Millennium Falcon and a Star Destroyer. |
| 03 | `03-story-duel.jpg` | The duel against the Shadowblade | The end of the story "Nightshade". |
| 04 | `04-interior-falcon.jpg` | Walk through the Falcon | The Millennium Falcon and the Star Destroyer can be walked through. |
| 05 | `05-story-disguise.jpg` | In disguise | Sneak aboard the Star Destroyer, in the troopers' own armour. |
| 06 | `06-sabers-colors.jpg` | Six blade colors | 14 hilts, 6 adjustable colors, 15 fighting styles. |
| 07 | `07-droids.jpg` | Droid companions | Eight droids, each with an ability. |
| 08 | `08-planet-tatooine.jpg` | Planet skies | Six planets change the sky, never the terrain. |
| 09 | `09-ship-xwing.jpg` | Fly them yourself | Lasers, a flight HUD and a chase camera; here an X-Wing over Endor. |
| 10 | `10-story-briefing.jpg` | They know your name | The characters of the story speak to you by your own Minecraft name. |

## Checkliste

- [ ] PR gemergt (Bilder in der Beschreibung laden)
- [ ] Entscheidung zur KI-Offenlegung getroffen, Modrinths Regeln selbst gelesen
- [ ] Patreon-Adresse geprüft und eingesetzt (oder Zeile gelöscht)
- [ ] Projekt angelegt: Name, Adresse `sabercraft`, Summary aus `02-projekt/zusammenfassung.txt`
- [ ] Icon, Kategorien, Beschreibung, Lizenz MIT, Links
- [ ] Galerie: 10 Bilder, Bild 01 hervorgehoben
- [ ] Disclosures: „Contains AI-generated content“ an
- [ ] Version 0.1.0: JAR, Beta, Fabric, 26.2, Client und Server, Fabric API erforderlich, Changelog
- [ ] Checkliste in den Einstellungen leer, zur Prüfung eingereicht
- [ ] Nach der Freigabe: Link auf GitHub und im README
