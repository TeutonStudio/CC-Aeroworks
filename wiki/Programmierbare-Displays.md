# Programmierbare Displays

CC-Aeroworks ergänzt zwei Displaymodule für Aeroworks-Steuerungspulte. Beide können entweder Zeichen anzeigen oder als frei beschreibbares Pixelraster verwendet werden.

## Displaytypen

| Display | Passende Sockets | Zeichen | Pixelraster |
|---|---|---:|---:|
| Zweistelliges Display | `left`, `right`, `big` | 2 | `7x5` |
| Dreistelliges Display | nur `big` | 3 | `11x5` |

Das dreistellige Display passt nicht in die beiden kleinen Sockets.

## Sockets

Lua akzeptiert Namen oder die nativen Aeroworks-Indizes:

| Name | Index | Position |
|---|---:|---|
| `left` | `0` | linker kleiner Socket |
| `right` | `1` | rechter kleiner Socket |
| `big` | `2` | großer mittlerer Socket |

Namen sind lesbarer und sollten in neuen Programmen bevorzugt werden.

## Textmodus

Ein Display kann direkt mit Text beschrieben werden:

```lua
local console = peripheral.find("cc_aeroworks_control_desk")
assert(console, "Kein Steuerungspult verbunden")

console.setDisplayText("big", "42")
```

Beim externen Multiblockzugriff wird zusätzlich ein Desk angegeben:

```lua
console.setDeskDisplayText(1, "big", "123")
```

Im eingebetteten Computer:

```lua
aeroworks.setDisplayText(1, "big", "123")
```

### Gültige Zeichen

Unterstützt werden:

- Ziffern `0` bis `9`
- Minuszeichen `-`
- Leerzeichen

Andere Zeichen werden zu Leerzeichen. Der Text beginnt links und wird auf zwei beziehungsweise drei Zeichen begrenzt.

```lua
console.setDisplayText("left", "-7")
console.setDisplayText("big", "123")
```

Ein String wie `"A2"` zeigt deshalb nur die unterstützten Stellen sinnvoll an. Das Display ist ein Zahlen- und Pixelmodul, kein winziger Romanleser, auch wenn Menschen so etwas erfahrungsgemäß trotzdem versuchen.

## Zahlenmodus

Zahlen können ohne eigene Formatierung geschrieben werden:

```lua
console.setDisplayNumber("left", 7, false)
console.setDisplayNumber("big", 42, true)
```

Der dritte Parameter aktiviert Nullauffüllung.

| Display | Wertebereich |
|---|---:|
| Zweistellig | `-9` bis `99` |
| Dreistellig | `-99` bis `999` |

Regeln:

- Dezimalwerte werden gegen null abgeschnitten.
- Werte außerhalb des Bereichs werden begrenzt.
- `zeroPad = true` füllt Ziffern rechts vom Vorzeichen mit Nullen.
- NaN und Unendlich erzeugen einen Lua-Fehler.

Beispiele für ein dreistelliges Display:

```lua
console.setDisplayNumber("big", 7, true)   -- "007"
console.setDisplayNumber("big", -7, true)  -- "-07"
```

## Pixelmodus

Pixelkoordinaten beginnen bei `(1, 1)` links oben.

```lua
console.setDisplayPixel("big", 1, 1, true)
console.setDisplayPixel("big", 2, 1, true)
console.setDisplayPixel("big", 3, 1, true)
```

Ein einzelnes Pixel abfragen:

```lua
local enabled = console.getDisplayPixel("big", 1, 1)
print(enabled)
```

### Vollständiges Raster schreiben

`setDisplayPixels` erwartet genau fünf Strings. Jeder String besteht ausschließlich aus `0` und `1` und muss zur Displaybreite passen.

Dreistelliges Display, `11x5`:

```lua
console.setDisplayPixels("big", {
  "11111111111",
  "10000000001",
  "10111111101",
  "10000000001",
  "11111111111",
})
```

Zweistelliges Display, `7x5`:

```lua
console.setDisplayPixels("left", {
  "0111110",
  "1100011",
  "1010101",
  "1100011",
  "0111110",
})
```

Im externen Multiblockzugriff:

```lua
console.setDeskDisplayPixels(deskId, "big", rows)
```

Im eingebetteten Computer:

```lua
aeroworks.setDisplayPixels(deskId, "big", rows)
```

## Moduswechsel

- Text- und Zahlenmethoden wechseln in den Textmodus.
- Pixelmethoden wechseln in den Pixelmodus.
- Der zuletzt geschriebene Modus bestimmt die Darstellung.

Das Löschen ist ebenfalls modusbezogen:

```lua
console.clearDisplay("big")       -- Textdarstellung leeren
console.clearDisplayPixels("big") -- Pixelraster leeren
```

Alle Displays des physisch angeschlossenen Pults löschen:

```lua
local count = console.clearDisplays()
print("Gelöscht:", count)
```

Für ein bestimmtes Pult im Multiblock:

```lua
console.clearDeskDisplays(deskId)
```

## Displayinformationen lesen

```lua
local info = console.getDisplay("big")
local size = console.getDisplaySize("big")

print(size.width, size.height)
```

Für den Multiblock stehen die entsprechenden `getDesk...`-Methoden bereit:

```lua
local displays = console.getDeskDisplays(deskId)
local display = console.getDeskDisplay(deskId, "big")
```

Im eingebetteten Computer werden dieselben Methoden ohne `Desk` im Namen verwendet, aber mit dem Desk als erstem Parameter:

```lua
local displays = aeroworks.getDisplays(deskId)
local display = aeroworks.getDisplay(deskId, "big")
```

## Beispiel: Balkenanzeige

Dieses Beispiel zeichnet einen horizontalen Balken auf einem dreistelligen Display. Der Eingang wird von `-15..15` auf `0..11` Pixel abgebildet.

```lua
local console = peripheral.find("cc_aeroworks_control_desk")
assert(console, "Kein Steuerungspult verbunden")

local function drawBar(value)
  value = math.max(-15, math.min(15, value))
  local filled = math.floor(((value + 15) / 30) * 11 + 0.5)
  local row = string.rep("1", filled) .. string.rep("0", 11 - filled)

  console.setDisplayPixels("big", {
    row,
    row,
    row,
    row,
    row,
  })
end

while true do
  local _, _, _, _, value = os.pullEvent("cc_aeroworks_desk_input")
  if type(value) == "number" then
    drawBar(value)
  end
end
```

## Häufige Fehler

- Fünf Rasterzeilen sind Pflicht.
- Die Zeilenbreite muss exakt `7` oder `11` betragen.
- Andere Zeichen als `0` und `1` sind ungültig.
- Das dreistellige Display kann nicht in `left` oder `right` montiert werden.
- Pixelkoordinaten sind 1-basiert, nicht 0-basiert.
- Ein ungültiger Socket erzeugt einen Lua-Fehler.
