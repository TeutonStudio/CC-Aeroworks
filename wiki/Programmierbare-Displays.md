# Programmierbare Displays

CC-Aeroworks ergänzt zwei Displaymodule für Aeroworks-Steuerungspulte. Beide können Text und Zahlen anzeigen oder als frei beschreibbares Pixelraster verwendet werden.

## Displaytypen

| Display | Passende Sockets | Zeichen | Fläche | Standardraster bei 256 PPB |
|---|---|---:|---:|---:|
| Zweistelliges Display | `left`, `right`, `big` | 2 | `7/16 × 7/16` Block | `112x112` |
| Dreistelliges Display | nur `big` | 3 | `10/16 × 7/16` Block | `160x112` |

Die Rastergröße wird serverseitig über `display.ppb` in **Parts per Block** bestimmt. `16 PPB` entspricht der normalen Minecraft-Texturdichte von 16 Teilen pro Blockkante, Standard sind `256 PPB`. Breite und Höhe werden aus derselben Dichte und der tatsächlichen Displayfläche abgeleitet, damit jeder dargestellte Pixel physisch quadratisch bleibt.

Programme müssen die wirksame Breite und Höhe mit `getDisplaySize` lesen und dürfen auch die Standardwerte nicht fest voraussetzen. Die Rückgabe enthält zusätzlich `ppb`, `surfaceWidthParts` und `surfaceHeightParts`.

## Pult auswählen

Am eingebetteten Computer:

```lua
local desks = peripherals.find("ControlDesk")
local desk = desks["12,64,-7"]
assert(desk, "Displaypult fehlt")
```

An einem externen Computer wird das direkt verbundene Pult lokal gefunden:

```lua
local desk = peripheral.find("ControlDesk")
assert(desk, "Kein lokales Pult verbunden")
```

Ab diesem Punkt sind die Displaymethoden identisch, weil ein Desk-Handle genau ein Pult vertritt.

## Sockets

| Name | Index | Position |
|---|---:|---|
| `left` | `0` | linker kleiner Socket |
| `right` | `1` | rechter kleiner Socket |
| `big` | `2` | großer mittlerer Socket |

## Textmodus

```lua
desk.setDisplayText("left", "-7")
desk.setDisplayText("big", "123")
```

Unterstützt werden Ziffern, Minuszeichen und Leerzeichen. Andere Zeichen werden zu Leerzeichen. Der Text beginnt links und wird auf zwei beziehungsweise drei Zeichen begrenzt.

## Zahlenmodus

```lua
desk.setDisplayNumber("left", 7, false)
desk.setDisplayNumber("big", 42, true)
```

| Display | Wertebereich |
|---|---:|
| Zweistellig | `-9` bis `99` |
| Dreistellig | `-99` bis `999` |

Regeln:

- Dezimalwerte werden gegen null abgeschnitten.
- Werte außerhalb des Bereichs werden begrenzt.
- `zeroPad = true` füllt Ziffern rechts vom Vorzeichen mit Nullen.
- NaN und Unendlich erzeugen einen Lua-Fehler.

## Pixelmodus

Pixelkoordinaten beginnen bei `(1,1)` links oben:

```lua
desk.setDisplayPixel("big", 1, 1, true)
local enabled = desk.getDisplayPixel("big", 1, 1)
```

### Rastergröße lesen

```lua
local size = desk.getDisplaySize("big")
print(size.width, size.height, size.ppb)
```

`setDisplayPixels` erwartet exakt `size.height` Strings. Jede Zeile besteht ausschließlich aus `0` und `1` und ist exakt `size.width` Zeichen breit.

```lua
local size = desk.getDisplaySize("big")
local rows = {}

for y = 1, size.height do
  local row = {}
  for x = 1, size.width do
    row[x] = (x == 1 or x == size.width or y == 1 or y == size.height) and "1" or "0"
  end
  rows[y] = table.concat(row)
end

desk.setDisplayPixels("big", rows)
```

## Touch und PPB-unabhängige Bedienfelder

Touchereignisse liefern weiterhin `x`, `y`, `width` und `height` im aktuellen Pixelraster. Bei 256 PPB besitzt ein großes Display also `160x112` statt eines groben historischen Rasters.

Für Bedienelemente, die unabhängig von der eingestellten PPB-Dichte bleiben sollen, stellt `touchdisplay` normierte Koordinaten bereit:

```lua
local touchdisplay = require("touchdisplay")

local function onTap(event)
  local u, v = touchdisplay.normalizedPosition(event)
  if u < 0.5 then
    -- linke Displayhälfte
  else
    -- rechte Displayhälfte
  end
end

return { onTap = onTap }
```

`u` und `v` liegen über die physische Displayfläche im Bereich `0..1`. Damit muss ein Skript seine Schaltflächen nicht für jede Auflösung neu mit festen Pixelkoordinaten definieren.

## Moduswechsel

- Text- und Zahlenmethoden wechseln in den Textmodus.
- Pixelmethoden wechseln in den Rastermodus.
- Der zuletzt geschriebene Modus bestimmt die Darstellung.

```lua
desk.clearDisplay("big")
desk.clearDisplayPixels("big")
local count = desk.clearDisplays()
```

Gespeicherte Raster sind bitgepackt und tragen ihre Rastermaße. Wird `display.ppb` geändert und passt ein alter Pixelzustand deshalb nicht mehr zur neuen Auflösung, bleibt das Display im Pixelmodus, startet aber mit einem leeren Raster. Das Skript muss anschließend neu zeichnen. Der serialisierte Zustand wird nicht als Text dargestellt.

## Informationen lesen

```lua
for _, display in ipairs(desk.getDisplays()) do
  print(
    display.socketName,
    display.width,
    display.pixelWidth,
    display.pixelHeight,
    display.ppb,
    display.mode
  )
end
```

Ein einzelnes Display:

```lua
local info = desk.getDisplay("big")
```

`getDisplay` erzeugt einen Lua-Fehler, wenn der angegebene Socket kein CC-Aeroworks-Display enthält. Für optionale Displays sollte deshalb zuerst `getDisplays()` geprüft werden.

## Beispiel: dynamische Balkenanzeige

```lua
local desks = peripherals.find("ControlDesk")
local inputDesk = desks["11,64,-7"]
local displayDesk = desks["12,64,-7"]

local function drawBar(value)
  local size = displayDesk.getDisplaySize("big")
  value = math.max(-15, math.min(15, value))
  local filled = math.floor(((value + 15) / 30) * size.width + 0.5)
  local row = string.rep("1", filled) .. string.rep("0", size.width - filled)
  local rows = {}

  for y = 1, size.height do
    rows[y] = row
  end

  displayDesk.setDisplayPixels("big", rows)
end

while true do
  local value = inputDesk.getInput("left")
  if type(value) == "number" then drawBar(value) end
  sleep(0.1)
end
```

Quelle und Display dürfen an verschiedenen Pulten liegen. Beide werden über ihre Desk-Handles adressiert; zentrale `setDeskDisplay...`-Methoden existieren nicht.

## Ponder-Erklärungen

Die Displayitems besitzen getrennte Storyboards für:

1. Herstellung unter Presse beziehungsweise im Einsatzgerät,
2. Socketkompatibilität und Montage,
3. Text-, Zahlen- und Pixelprogrammierung über Desk-Handles.

## Häufige Fehler

- Rastermaße fest im Skript annehmen statt `getDisplaySize` zu verwenden,
- feste Touch-Pixelgrenzen verwenden, obwohl das Bedienfeld PPB-unabhängig sein soll,
- falsche Zeilenanzahl oder Zeilenbreite,
- andere Rasterzeichen als `0` und `1`,
- dreistelliges Display in `left` oder `right`,
- Pixelkoordinaten mit null statt eins beginnen,
- `getDisplay` auf einem leeren Socket aufrufen,
- ein Pult über einen veralteten Positionsschlüssel adressieren.
