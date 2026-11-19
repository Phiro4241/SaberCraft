# GitHub Actions: was wann läuft

Stand: 26.10.2026, ergänzt am 15.11.2026. Als dieses Dokument entstand, war SaberCraft ein privates Repository auf GitHub Free: **2.000 Actions-Minuten und 0,5 GB Artefaktspeicher pro Monat.** Im Oktober 2026 war beides aufgebraucht, vor allem durch hängende Client-Gametests, die erst nach 6 Stunden abgebrochen wurden. Seit dem 26.10.2026 ist das Repository öffentlich; für öffentliche Repositories rechnet GitHub die Minuten auf den Standard-Runnern nicht ab. Die Regeln unten gelten trotzdem weiter, damit ein Lauf nicht unnötig lange dauert.

## Was läuft wann

| Ereignis | Was läuft | Dauer etwa |
| --- | --- | --- |
| Pull Request (jeder Push darauf) | Build und alle Client-Gametests | 50 min (Phase 18) |
| Push auf `main` (meist der Merge) | nur Build und JAR | 3 min |
| Manuell: Actions > build > Run workflow | Build und alle Client-Gametests, Screenshots und Audio werden hochgeladen | 50 min |
| Push auf einen Branch ohne PR | nichts | – |
| Nur `*.md` oder `docs/` geändert | nichts | – |

Ein neuer Push auf einen PR bricht dessen noch laufenden Lauf ab. Auf `main` wird nie abgebrochen.

## Grenzen

- **Zeitlimit:** Der Test-Schritt bricht nach 95 Minuten ab, der ganze Job nach 110 Minuten (seit Phase 25: der Lauf von PR #29 mit Phase 24 brauchte 68 der damals erlaubten 75 Minuten im Test-Schritt, und Phase 25 bringt `Phase25GameTest` mit etwa 5 Minuten dazu). Davor waren es 75 und 85 Minuten (seit Phase 20; vorher 60 und 70: der Lauf von PR #24 mit Phase 19 brauchte schon 50,6 Minuten im Test-Schritt, und Phase 20 bringt den Film und weitere Tests dazu). Ohne Limit lief ein hängender Test bisher 6 Stunden. Bis Phase 17 waren es 45 und 55 Minuten; die Tests von Phase 17 brauchten aber schon fast 43 Minuten, und mit Phase 18 sind es etwa 50. Der erste Lauf von PR #23 brach deshalb zu Beginn von `Phase18GameTest` ab, nachdem alle Klassen davor grün waren.
- **Artefakte:** Hochgeladen wird nur die JAR der Mod (nicht die Quellen-JAR), sie bleibt 5 Tage (seit Phase 25; vorher 7 Tage mit beiden JARs). Screenshots (ca. 0,57 GB) und die Audioaufnahme (ca. 0,2 GB) werden nur bei einem Fehlschlag oder einem manuellen Lauf hochgeladen und bleiben 3 Tage.
- **Cache:** `setup-java` speichert den Gradle- und Loom-Cache. Er zählt nicht zum Artefaktspeicher.

## Regeln für die Entwicklung

- Vor jedem Push lokal bauen und die betroffenen Gametests laufen lassen (siehe die Statusdokumente der Phasen).
- Einen PR erst öffnen, wenn der Stand lokal grün ist. Jeder Push auf einen offenen PR kostet einen vollen Lauf.
- Keine Commits nur, um CI neu anzustoßen. Einen fehlgeschlagenen Lauf erst untersuchen, dann gezielt beheben.

## Behobene Ursachen für Hänger und Wackler

- **Hängende Welt beim Schließen:** Kam eine Chat-Nachricht (Spielmodus, Erfolg) genau beim Schließen der Testwelt an, hing der Client. `ManeuverGameTest` und `AdvancementGameTest` warten jetzt ein paar Ticks, bevor die Welt schließt. Lokal trat das Hängen nie auf; das Zeitlimit schützt unabhängig davon.
- **Bowcaster-Treffer im `ArsenalGameTest`:** Kein Testproblem, sondern ein Fehler im Spiel. Vanilla vergrößert den Trefferrand um Ziele bei jungen Geschossen jeden Tick. Endete ein Bolzenschritt knapp vor dem Ziel, begann der nächste schon innerhalb des vergrößerten Rands, und der Bolzen flog ohne Treffer durch. `BlasterBolt` zählt diesen Fall jetzt als Treffer. Vorher gingen in einem Wiederholungstest 13 von 40 Bowcaster-Schüssen durch das Ziel, danach 0 von 40.
