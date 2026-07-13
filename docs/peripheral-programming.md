# Control Desk mit CC:Tweaked programmieren

CC-Aeroworks macht jeden geladenen Aeroworks Control Desk direkt zu einem CC:Tweaked-Peripheral. Es ist kein zusätzlicher Peripheral-Block nötig. Ein Computer erkennt das Pult, wenn er direkt daneben steht oder über ein kabelgebundenes Modem damit verbunden ist.

## Peripheral finden

```lua
local desk = peripheral.find("cc_aeroworks_control_desk")
assert(desk, "Kein Aeroworks Control Desk gefunden")
```

Sind mehrere Pulte verbunden, kann man sie mit `peripheral.getNames()` und `peripheral.wrap(name)` gezielt auswählen. Die von CC:Tweaked vergebene Anschlussbezeichnung wird auch im Eingabeereignis geliefert.

## Wichtig: Socketnummern beginnen bei null

Aeroworks nummeriert seine Sockets ab `0`. Das ist absichtlich anders als die üblichen Lua-Tabellenindizes, die bei `1` beginnen. `getModules()` liefert zwar eine normale Lua-Liste, das Feld `socket` darin bleibt aber nullbasiert.

```lua
print("Socketanzahl:", desk.getSocketCount())

for _, module in ipairs(desk.getModules()) do
  print(module.socket, module.id, module.kind)
end
```

Ein Moduleintrag enthält mindestens:

- `socket`: nullbasierter Aeroworks-Socketindex,
- `id`: Registry-ID des Moduls, zum Beispiel `aeroworks:lever`,
- `kind`: vereinfachte Art wie `lever`, `joystick`, `button` oder `display`,
- `display`: `true` nur für CC-Aeroworks-Anzeigen.

Eingabemodule enthalten zusätzlich `value`, wenn sie genau einen Kanal haben. Mehrkanalmodule enthalten `values`, eine Tabelle nach der tatsächlichen Aeroworks-Kanal-ID. Displays enthalten `width` und `text`.

## Eingaben lesen

```lua
local socket = 0
local module = desk.getModule(socket)

if module then
  print("Modul:", module.id)
  local value = desk.getInput(socket)
  if type(value) == "table" then
    for channel, channelValue in pairs(value) do
      print(channel, channelValue)
    end
  else
    print("Wert:", value)
  end
end
```

`getInput(socket)` erzeugt einen verständlichen Fehler, wenn der Socket ungültig, leer oder kein Eingabemodul ist. `getInputs()` liefert alle momentan lesbaren Eingaben nach Socketindex. Die rohen Aeroworks-Kanalwerte liegen im verifizierten Bereich `-15` bis `15`; mehrkanalige Joysticks, Yokes und Throttle Quadrants behalten ihre Aeroworks-Kanalnamen.

## Displays beschreiben

Das zweistellige Display kann in kleinen und großen Desk-Slots montiert werden. Das dreistellige Display passt nur in große Slots.

```lua
desk.setDisplayText(2, "42")
desk.setDisplayNumber(5, 7, true) -- bei drei Stellen: "007"

local display = desk.getDisplay(2)
print(display.width, display.text)
```

Unterstützt werden die Zeichen `0` bis `9`, Minus und Leerzeichen. Andere Zeichen werden zu Leerzeichen. Zu langer Text wird rechts abgeschnitten. Dezimalzahlen werden gegen null abgeschnitten, nicht gerundet. Werte werden auf den darstellbaren Bereich begrenzt:

- zwei Stellen: `-9` bis `99`,
- drei Stellen: `-99` bis `999`.

`zeroPad=true` füllt links mit Nullen auf und lässt ein negatives Vorzeichen vorne stehen. NaN und unendliche Werte erzeugen einen Lua-Fehler. `clearDisplay(socket)` leert eine Anzeige; `clearDisplays()` leert alle Anzeigen des Pults und liefert deren Anzahl.

## Einzelne Pixel zeichnen

Neben dem Ziffernmodus besitzt jedes Display einen Pixelmodus. Der Zweisteller hat `7x5`, der Dreisteller `11x5` Pixel. Anders als die Desk-Sockets sind Pixelkoordinaten Lua-typisch **1-basiert**: `(1,1)` ist links oben.

```lua
local size = desk.getDisplaySize(2)
print(size.width, size.height)

desk.clearDisplayPixels(2)
desk.setDisplayPixel(2, 1, 1, true)
desk.setDisplayPixel(2, size.width, size.height, true)
print(desk.getDisplayPixel(2, 1, 1)) -- true
```

Für ein ganzes Bild ist ein einzelner Aufruf effizienter, weil er nur eine Zustandsänderung synchronisiert:

```lua
desk.setDisplayPixels(2, {
  "1000001",
  "0100010",
  "0010100",
  "0001000",
  "0010100"
})
```

Die Tabelle muss genau fünf Zeilen enthalten. Jede Zeile besteht ausschließlich aus `0` und `1` und muss exakt zur Displaybreite passen. `setDisplayText` und `setDisplayNumber` wechseln zurück in den Textmodus. Die Pixelmethoden wechseln in den Pixelmodus. `getDisplay(socket)` meldet dies über `mode`, `pixelWidth`, `pixelHeight` und `pixels`.

## Änderungen als Ereignis empfangen

Solange mindestens ein Computer angehängt ist, überwacht CC-Aeroworks die Eingabemodule des Pults. Nur geänderte Kanalwerte erzeugen ein Ereignis:

```lua
while true do
  local _, peripheralName, socket, moduleId, value, channel =
    os.pullEvent("cc_aeroworks_desk_input")

  print(peripheralName, socket, moduleId, channel, value)
end
```

Der erste gelesene Zustand dient als Ausgangspunkt und löst kein Ereignis aus. Ein Mehrkanalmodul kann für eine Bewegung mehrere Ereignisse mit unterschiedlichen `channel`-Werten erzeugen.

## Vollständiges kleines Beispiel

Dieses Programm schreibt den Wert des ersten gefundenen Eingabemoduls auf das erste Display:

```lua
local desk = peripheral.find("cc_aeroworks_control_desk")
assert(desk, "Kein Aeroworks Control Desk gefunden")

local displays = desk.getDisplays()
assert(#displays > 0, "Kein CC-Aeroworks-Display montiert")
local displaySocket = displays[1].socket

while true do
  local _, _, _, _, value = os.pullEvent("cc_aeroworks_desk_input")
  desk.setDisplayNumber(displaySocket, value, false)
end
```

Weitere vollständige Programme, einschließlich `pixel_display_demo.lua`, liegen unter `examples/cc/`. Die knappe Methodensignatur-Referenz steht in `docs/cc-peripheral-api.md`.
