# Patreon: Unterstützungs-Abschnitt

**Creator:** Phiro · **Kennung laut Phiro:** `Phiro42421`

## Offener Punkt: die genaue URL

Ich konnte die Patreon-Seite nicht prüfen: patreon.com ist aus meiner Arbeitsumgebung nicht erreichbar, und eine Websuche nach der Kennung hat keine Seite gefunden. Deshalb steht **nirgends ein Patreon-Link**, nur deutlich markierte Platzhalter. An der Patreon-Seite selbst wurde nichts geändert.

Gebraucht wird die Adresse, die im Browser steht, wenn man die Seite **ausgeloggt** öffnet. Üblich sind diese Formen (bitte nicht raten, sondern die echte kopieren):

- `https://www.patreon.com/Phiro42421`
- `https://www.patreon.com/c/Phiro42421`

Wo der Link eingetragen wird, sobald er feststeht:

| Ort | Stelle |
| --- | --- |
| `README.md` | Abschnitt „Support“: den HTML-Kommentar `PATREON LINK PLACEHOLDER` und die Zeile „**Patreon:** *the link will be added here.*“ durch den englischen Block unten ersetzen |
| `README.de.md` | Abschnitt „Unterstützen“: den Kommentar `PATREON-LINK-PLATZHALTER` und die Zeile „**Patreon:** *Der Link folgt hier.*“ durch den deutschen Block unten ersetzen |
| Modrinth | In den Projekteinstellungen unter den Links bei den Spendenlinks die Plattform „Patreon“ wählen (die Plattform gibt es laut Modrinth-API) und die URL eintragen; in der Beschreibung `PATREON_URL_EINTRAGEN` ersetzen. Genaueres in [`modrinth.md`](modrinth.md) |
| ModDB | In der Beschreibung `PATREON_URL_EINTRAGEN` ersetzen |
| GitHub (optional) | Datei `.github/FUNDING.yml` mit `patreon: <Name aus der URL>`; zeigt den „Sponsor“-Knopf |

Abo-Stufen (Tiers) gibt es in diesen Texten bewusst nicht: Ich kenne keine, und erfunden wird nichts.

## Englischer Block (README)

Ersetzt im README die beiden Platzhalterzeilen; der Satz davor bleibt stehen.

```markdown
**[Support SaberCraft on Patreon](PATREON_URL)**: voluntary, it unlocks nothing in the mod.
```

Kurzfassung für Modrinth und ModDB (steht dort schon mit Platzhalter):

```
Support the project (voluntary, unlocks nothing): PATREON_URL
```

## Deutscher Block (README.de)

```markdown
**[SaberCraft auf Patreon unterstützen](PATREON_URL)**: freiwillig, es schaltet nichts in der Mod frei.
```

## Hinweis zu Fan-Mod und Geld

SaberCraft benutzt Namen aus Star Wars ohne Lizenz. Eine Spendenmöglichkeit neben einer Fan-Mod kann von Rechteinhabern als kommerzielle Nutzung gesehen werden. Die Texte sind deshalb so formuliert, dass Patreon die Arbeit von Phiro unterstützt und nichts verkauft wird: kein Inhalt hinter einer Bezahlschranke, keine Vorteile im Spiel. Modrinth verlangt ohnehin, dass alles auf der Seite Beworbene frei verfügbar ist. Ob der Link auf allen Plattformen stehen soll, entscheidet Phiro.
