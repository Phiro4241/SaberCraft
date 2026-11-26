# Folgephasen nach Phase 10

Stand: 25.10.2026. Phase 10 hat das **Duell-System** fertig gebaut und getestet (siehe `phase-10-status.md`, Abschnitt Duelle). Die drei anderen Punkte aus Priorität 8 sind zu groß, um sie in Phase 10 mit vollständigen Tests zu liefern. Statt halbfertiger Funktionen stehen hier ausgearbeitete Pläne, damit jede davon eine eigene, sauber getestete Phase werden kann. Nichts davon ist im Spiel aktiv.

Für alle gilt weiter: keine Änderung an Terrain oder Weltgenerierung, keine geschützten Modelle, Texturen oder Klänge aus Filmen, alles abschaltbar, alles serverseitig entschieden.

## Phase 11 (Vorschlag): Macht-Energie

**Ziel:** Machtkräfte (Stoß, Griff, Zug, Machtsprung) bekommen eine eigene Energie, getrennt von der Ausdauer, die weiter für Blocken, Ausweichen und Drehangriffe gilt. So lohnt sich ein Wechsel zwischen Klinge und Macht.

**Was es heute schon gibt:** Jede Fähigkeit kostet Ausdauer (`AbilityConfig`: `push_cost`, `grip_cost`, `pull_cost`, `jump_cost`), die Ausdauer wird über `StaminaPayload` an den Client geschickt und im HUD gezeigt. Darauf baut die Macht-Energie auf.

**Plan:**

1. Server: zweite Größe `force` im Spieler-Zustand von `SaberAbilities` (Maximum, Regeneration, Verzögerung nach Gebrauch), eigene Kosten je Kraft. Neue Werte in `abilities.json`: `force_enabled` (Standard **aus**, damit sich bestehende Welten nicht ändern), `force_max`, `force_regen`, `force_regen_delay`.
2. Charaktere und Seiten: helle Seite regeneriert ruhiger, dunkle Seite regeneriert im Kampf schneller (nur wenn ein Charakter gewählt ist; Werte im Profil, auch in eigenen Profilen nach `custom-profiles.md`).
3. Netzwerk: `ForceEnergyPayload` wie `StaminaPayload`; Spieler ohne Mod merken nichts.
4. HUD: zweiter Balken unter der Ausdauer, abschaltbar im Kampf-Menü.
5. Tests: Kosten und Regeneration auf dem Server, Kraft ohne Energie abgelehnt, HUD-Screenshot, Einstellung „aus“ verhält sich wie Phase 10.

**Aufwand:** mittel. **Risiko:** Balance; deshalb standardmäßig aus.

## Phase 12 (Vorschlag): Fahrzeuge

**Ziel:** Ein Speeder-Bike zum Reiten, ohne neue Blöcke oder Weltgenerierung.

**Plan:**

1. Eigenes Entity `speeder_bike` (fliegt knapp über dem Boden, folgt Gelände bis 1 Block Stufe, Geschwindigkeit über Konfiguration begrenzt). Herstellung über ein Rezept aus Eisen, Redstone und einer Netherite-Schwert-Energiezelle, oder per Befehl für Operatoren.
2. Modell aus eigenen Würfeln (Generator wie `tools/gen_hilts.py`), eigene synthetisierte Motorklänge (`tools/synth_sounds.py`).
3. Steuerung: Vanilla-Reiten (WASD, Springen = Boost mit Abklingzeit), Absteigen mit Schleichen. Kollisionen schaden dem Fahrer nur leicht.
4. Server-Regeln: `vehicles_enabled`, Höchstgeschwindigkeit, ob Fahrzeuge Blöcke beschädigen dürfen (Standard nein).
5. Vanilla-Clients: Das Entity braucht den Mod auf dem Client. Deshalb ist es ein **optionales** Modul, das ein Server nur einschaltet, wenn alle Spieler den Mod haben.
6. Tests: Spawnen, Reiten, Geschwindigkeitsgrenze, Gelände folgen, Absteigen, Speichern und Laden der Welt, Screenshots.

**Aufwand:** groß (eigenes Entity mit Physik, Netzwerk, Renderer). **Risiko:** Server-Last und Bewegungs-Prüfung (Anti-Cheat) bei schnellen Entities.

## Phase 13 (Vorschlag): Droiden

**Ziel:** Ein Begleiter-Droide (kleiner Astromech-ähnlicher Helfer, eigenes Design), der folgt, piept und kleine Aufgaben erledigt.

**Plan:**

1. Entity `astro_droid`, gezähmt wie ein Wolf (Rechtsklick mit Redstone), folgt dem Besitzer, bleibt auf Befehl stehen.
2. Fähigkeiten, einzeln abschaltbar: Licht in dunklen Höhlen (nur Partikel und Licht am Droiden, keine Blöcke), Warnton bei Monstern in der Nähe, kleiner Stauraum (9 Plätze).
3. Eigene, synthetisierte Pieptöne (keine Film-Samples), eigenes Würfelmodell.
4. Der Droide greift **nie** Spieler an und kann so eingestellt werden, dass er gar nicht kämpft.
5. Tests: Zähmen, Folgen, Sitzen, Stauraum speichern und laden, Warnton, kein Schaden an Spielern, Screenshots.

**Aufwand:** groß. **Risiko:** Pfadfindung und Leistung bei vielen Droiden; Begrenzung pro Spieler vorgesehen.

## Weitere offene Punkte aus Phase 10

- **Horizont als ferne Geometrie für Shader-Packs.** Complementary verdunkelt eigene Himmelstexturen am Horizont (siehe `phase-10-status.md`, Abschnitt Shader). Eine Lösung ist, die Planeten-Horizonte als weit entfernte, unbeleuchtete Geometrie zu zeichnen, die Shader-Packs wie Gelände behandeln. Das braucht eigene Render-Pässe für Iris und wird separat getestet.
- **Duelle erweitern:** Zuschauer-Anzeige, Arena-Grenze per Befehl, Rangliste. Die Grundlage (freiwillige Herausforderung, Countdown, Best of three) ist fertig.
- **Duell-Musik:** Im Star-Wars-Modus spielt der `duel/`-Ordner schon bei jedem Kampf mit leuchtender Klinge; eine eigene Situation nur für angenommene Duelle wäre eine kleine Folgearbeit.
