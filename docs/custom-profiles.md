# Eigene Charakterprofile (Phase 10)

Neue Charaktere brauchen keine Änderung am Mod. Jede Datei `config/sabercraft/profiles/<name>.json`
beschreibt einen Charakter. Das Charaktermenü (Taste V, Bereich „Charakter“, Liste „Outfits“) liest
den Ordner bei jedem Öffnen neu ein; der Knopf „Profil“ schaltet durch die Profile.

```json
{
  "name": "Cal Kestis",
  "skin": "cal.png",
  "hilt": "sabercraft:skywalker",
  "color": "sabercraft:blue",
  "style": "sabercraft:flowing",
  "side": "light"
}
```

| Feld | Pflicht | Bedeutung |
|---|---|---|
| `name` | nein | Name im Menü. Ohne ihn der Dateiname. |
| `skin` | nein | Skin-Datei (64 x 64 PNG im Skin-Format) im selben Ordner. Dateien außerhalb des Ordners werden nicht gelesen. |
| `base` | nein | Ein eingebauter Charakter oder ein Outfit, z. B. `"sabercraft:obi_wan"` oder kurz `"obi_wan"`. Ohne `skin` wird dessen Aussehen benutzt; andere Spieler sehen diesen Charakter. |
| `hilt`, `color`, `style` | nein | Das Lichtschwert des Profils. Beim Wählen bekommt das Schwert in der Hand diese Variante. Ohne `color` rot, ohne `style` der übliche Stil des Griffs. Passt die Kombination nicht (z. B. Doppelklinge mit einem Einhand-Stil), wird nur das Schwert weggelassen und im Log gewarnt. |
| `side` | nein | `light`, `dark`, `grey` oder `none`. |
| `scale` | nein | 0.5 bis 1: kleinere Figur wie Yoda (nur das Bild, nicht die Hitbox). |

Gültige Ids:

- Griffe: `standard`, `guardian`, `dark_lord`, `sentinel`, `kenobi`, `skywalker`, `anakin`, `yoda`, `dooku`, `tano`, `crossguard`, `rey`, `ezra`, `double`
- Farben: `red`, `blue`, `green`, `purple`, `yellow`, `white`
- Stile: `flowing`, `heavy`, `defensive`, `agile`, `djem_so`, `soresu`, `onslaught`, `ataru`, `makashi`, `reverse_shien`, `unbridled`, `shii_cho`, `improvised` (Einhand) sowie `staff`, `juyo` (Doppelklinge)

Was andere sehen: Die Skin-Datei bleibt auf diesem Computer, wie jede eigene Datei. Andere Spieler
sehen den `base`-Charakter, falls angegeben, sonst den normalen Skin. Das Lichtschwert ist eine
normale Variante und für alle sichtbar. Der Server muss nichts kennen.

Fehlerhafte Dateien (kein gültiges JSON, unbekannte Ids) werden übersprungen und im Log
(`latest.log`, Zeilen mit „profile“) mit dem Grund genannt. Es werden höchstens 32 Profile gelesen.
