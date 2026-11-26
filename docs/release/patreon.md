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
| `README.md` | Abschnitt „Support the project“: den HTML-Kommentar `PATREON LINK PLACEHOLDER` und die Zeile „placeholder“ durch den englischen Block unten ersetzen |
| `README.de.md` | Abschnitt „Projekt unterstützen“: genauso mit dem deutschen Block |
| Modrinth | Settings → Links → Donation links: Plattform „Patreon“, URL; in der Beschreibung `PATREON_URL_EINTRAGEN` ersetzen |
| ModDB | In der Beschreibung `PATREON_URL_EINTRAGEN` ersetzen |
| GitHub (optional) | Datei `.github/FUNDING.yml` mit `patreon: <Name aus der URL>`; zeigt den „Sponsor“-Knopf |

Abo-Stufen (Tiers) gibt es in diesen Texten bewusst nicht: Ich kenne keine, und erfunden wird nichts.

## Englischer Block (README, Modrinth, ModDB)

```markdown
## Support the project

SaberCraft is free and will stay free. If you would like to support its development, you can do so voluntarily on [Patreon](PATREON_URL). Support unlocks nothing in the mod: every feature is available to everyone.
```

Kurzfassung für Plattformen ohne Überschriften:

```
Support the project (voluntary, unlocks nothing): PATREON_URL
```

## Deutscher Block (README.de)

```markdown
## Projekt unterstützen

SaberCraft ist kostenlos und bleibt kostenlos. Wer die Entwicklung unterstützen möchte, kann das freiwillig auf [Patreon](PATREON_URL) tun. Eine Unterstützung schaltet nichts in der Mod frei: Alle Funktionen sind für alle da.
```

## Hinweis zu Fan-Mod und Geld

SaberCraft benutzt Namen aus Star Wars ohne Lizenz. Eine Spendenmöglichkeit neben einer Fan-Mod kann von Rechteinhabern als kommerzielle Nutzung gesehen werden. Die Texte sind deshalb so formuliert, dass Patreon die Arbeit von Phiro unterstützt und nichts verkauft wird: kein Inhalt hinter einer Bezahlschranke, keine Vorteile im Spiel. Modrinth verlangt ohnehin, dass alles auf der Seite Beworbene frei verfügbar ist. Ob der Link auf allen Plattformen stehen soll, entscheidet Phiro.
