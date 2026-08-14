# CC:Tweaked Peripheral-Netzwerk

CC-Aeroworks verwendet keine zentrale Multiblock-Peripheral-Fassade mehr. Stattdessen gelten zwei klare Ebenen:

1. Jedes Steuerungspult ist ein lokales Peripheral vom Typ `ControlDesk`.
2. Der eingebettete Computer stellt zusätzlich die globale API `peripherals` bereit, die alle Pulte und alle benachbarten Peripherals des vollständigen Pultnetzes indexiert.

Die frühere globale API `aeroworks` und die netzwerkweiten `getDesk...`-Methoden gehören nicht zum neuen Vertrag.

## Lokaler `ControlDesk`-Adapter

Ein normaler CC:Tweaked-Computer oder ein Wired Modem kann ein einzelnes Pult wie jedes andere Peripheral verbinden:

```lua
local desk = peripheral.find("ControlDesk")
assert(desk, "Kein lokales Steuerungspult verbunden")
```

Zusätzliche Typnamen sind:

- `control_desk`
- `cc_aeroworks:control_desk`
- `cc_aeroworks_control_desk`

Der Adapter arbeitet ausschließlich auf dem Pult, an dem er physisch hängt.

### Lokale Methoden

- `getInfo() -> table`
- `getSocketCount() -> number`
- `getSockets() -> table`
- `getModules() -> table`
- `getModule(socket) -> table|nil`
- `getInput(socket) -> number|table`
- `getInputs() -> table`
- `getDisplays() -> table`
- `getDisplay(socket) -> table`
- `setDisplayText(socket, text) -> string`
- `setDisplayNumber(socket, value, zeroPad?) -> string`
- `clearDisplay(socket)`
- `clearDisplays() -> number`
- `getDisplaySize(socket) -> table`
- `getDisplayPixel(socket, x, y) -> boolean`
- `setDisplayPixel(socket, x, y, enabled) -> boolean`
- `setDisplayPixels(socket, rows) -> table`
- `clearDisplayPixels(socket)`

Sockets akzeptieren Namen oder nullbasierte Indizes:

| Name | Index |
|---|---:|
| `left` | `0` |
| `right` | `1` |
| `big` | `2` |

## Globale `peripherals`-API

Nur der eingebettete Computer besitzt diese API:

```lua
local network = peripherals.getNetwork()
print(network.state, network.deskCount, network.peripheralCount)
```

Alternativ steht dasselbe Objekt als Modul bereit:

```lua
local peripherals = require("cc_aeroworks.peripherals")
```

### Methoden

- `find(type) -> nil|handle|table`
- `findAll(type) -> table`
- `wrap(x, y, z, type?) -> nil|handle`
- `wrap(position, type?) -> nil|handle`
- `getDesks() -> table`
- `getTypes() -> table`
- `getNetwork() -> table`
- `refresh() -> table`

## Suchregeln

### Kein Treffer

```lua
local radar = peripherals.find("radar")
-- nil
```

### Genau ein Treffer

Kommt eine Gattung im gesamten Pultnetz genau einmal vor, wird direkt das echte Methoden-Handle zurückgegeben:

```lua
local modem = peripherals.find("endermodem")
assert(modem, "Kein EnderModem im Pultnetz")
modem.open(42)
```

Das Handle delegiert die CC:Tweaked-Methoden des Ziel-Peripherals. `attach`, `detach`, Mounts, Ereignisse und Main-Thread-Aufrufe laufen über einen eigenen Netzwerk-Anschlussnamen.

Metadaten stehen bereit, sofern das Ziel nicht selbst bereits eine Methode `getPeripheralInfo` definiert:

```lua
local info = modem.getPeripheralInfo()
print(info.type, info.address, info.deskAddress, info.side)
```

### Mehrere Treffer

```lua
local modems = peripherals.find("endermodem")

for address, modem in pairs(modems) do
  print(address, modem.getPeripheralInfo().position.x)
end
```

Externe Geräte verwenden Adressen der Form:

```text
12,64,-7/north
```

Der erste Teil ist die Position des zugehörigen Pults, der zweite die Anschlussseite aus Sicht des Pults.

### Stabile Sammlungsform

`find` ändert seine Rückgabeform mit der Trefferzahl. Programme, die immer iterieren, verwenden deshalb `findAll`:

```lua
for address, modem in pairs(peripherals.findAll("endermodem")) do
  print(address)
end
```

Ohne Treffer liefert `findAll` eine leere Tabelle.

## Typnamen und Aliasse

Die Suche ignoriert Großschreibung und akzeptiert kompakte Schreibweisen. Ein gemeldeter Typ

```text
advanced_peripherals:ender_modem
```

kann beispielsweise über folgende Suchbegriffe gefunden werden:

```lua
peripherals.find("advanced_peripherals:ender_modem")
peripherals.find("ender_modem")
peripherals.find("EnderModem")
peripherals.find("endermodem")
```

Primärtyp und zusätzliche CC:Tweaked-Typen werden gemeinsam indexiert. Bei kollidierenden Kurznamen sollte der vollständige namespaced Typ verwendet werden.

## `ControlDesk` suchen

`ControlDesk` ist absichtlich eine Sammlungsausnahme. Der Aufruf liefert immer alle Pulte als Positionstabelle, auch bei nur einem Mitglied:

```lua
local desks = peripherals.find("ControlDesk")
local desk = desks["12,64,-7"]
```

`peripherals.getDesks()` liefert dieselbe Tabellenform.

### Desk-Metadaten

```lua
local info = desk.getInfo()
```

Beispiel:

```lua
{
  id = "stabile-uuid",
  address = "12,64,-7",
  index = 2,
  x = 12,
  y = 64,
  z = -7,
  dimension = "minecraft:overworld",
  computer = false,
  variant = "control_desk",
  facing = "north",
  loaded = true
}
```

Die UUID identifiziert das Pult dauerhaft. Die Koordinate ist seine aktuelle Netzwerkadresse.

### Desk-Methoden

Ein Desk-Handle besitzt die lokalen Display- und Modulmethoden des `ControlDesk`-Adapters sowie:

- `getPeripherals() -> table`
- `find(type) -> nil|handle|table`
- `findAll(type) -> table`
- `wrap(side) -> nil|handle`

Beispiele:

```lua
local localModem = desk.find("endermodem")
local northDevice = desk.wrap("north")

for address, device in pairs(desk.getPeripherals()) do
  print(address, device.getPeripheralInfo().type)
end
```

## Zugriff über Koordinaten

Desk über drei Zahlen:

```lua
local desk = peripherals.wrap(12, 64, -7)
```

Desk über Positionstabelle:

```lua
local desk = peripherals.wrap({ x = 12, y = 64, z = -7 })
```

Externes Peripheral über seine Blockposition:

```lua
local device = peripherals.wrap(12, 64, -8)
```

Stellt derselbe Block mehrere passende Peripherals bereit, muss der Typ angegeben werden:

```lua
local radar = peripherals.wrap(12, 64, -8, "radar")
```

Die API lädt keine Chunks nach und arbeitet nur in der Dimension des eingebetteten Computers.

## Netzwerkbeschreibung

```lua
{
  state = "active",
  revision = 42,
  dimension = "minecraft:overworld",
  deskCount = 3,
  peripheralCount = 4
}
```

Globale Graphzugriffe werden abgelehnt bei:

- mehreren eingebetteten Computern,
- teilweise geladenen Pultreihen,
- mehr als 64 verbundenen Pulten,
- einem Computer, der den aufgelösten Verbund nicht besitzt.

Die Position des Computer-Steuerungspults innerhalb der Reihe beeinflusst das Ergebnis nicht.

## Globale `controls`-API

Nur der eingebettete Computer besitzt die Control-Authority-API:

```lua
local controls = require("cc_aeroworks.controls")
```

Methoden:

- `getChannels() -> table`
- `getState(deskId, socket, channel) -> table`
- `override(deskId, socket, channel, value) -> table`
- `overrideBatch(commands) -> number`
- `release(deskId, socket, channel) -> boolean`
- `releaseAll() -> number`

`override` übernimmt einen kontinuierlichen Steuerkanal im Modus `hard`. Solange der Override aktiv ist, werden normale Aeroworks-Schreibversuche für genau diesen Kanal abgefangen. Der Computerwert selbst wird über Aeroworks' normalen Controller-Setter geschrieben; dadurch bleiben Fahrzeugwert und sichtbare Stellung des Steuerobjekts derselbe Zustand.

Unterstützt werden Lever, Joystick, Wheel, Yoke und die vier Kanäle des Throttle Quadrant. Die Display-X/Y-Kanäle des virtuellen Fingers sowie binäre Buttons gehören nicht zu diesem Vertrag. Werte müssen ganzzahlig in `-15..15` liegen.

Beispiel für gekoppelte Yoke-Achsen:

```lua
controls.overrideBatch({
  { desk = yokeDeskId, socket = "big", channel = "turn", value = rollCommand },
  { desk = yokeDeskId, socket = "big", channel = "pitch", value = pitchCommand },
})
```

Die komplette Batch-Liste wird vor dem ersten Write geprüft. Wiederholte identische Sollwerte lösen keinen erneuten Aeroworks-Write aus.

Overrides sind nicht persistent und werden bei Computer-Aus, BlockEntity-Invalidierung, ungültigem Multiblock oder verschwundenem Ziel automatisch freigegeben. `release` lässt den letzten effektiven Kanalwert stehen und gibt ab diesem Punkt die normale Eingabe wieder frei.

Ereignisse:

```text
cc_aeroworks_control_override(action, deskId, deskIndex, socket, socketName, channel, value, mode)
cc_aeroworks_control_release(deskId, socket, socketName, channel, reason)
```

Die ausführliche Beschreibung steht in `docs/control-overrides.md`; ein ausführbares Beispiel liegt unter `examples/cc/control-override-demo.lua`.

## Aktualisierung und Ereignisse

Block-, Nachbar- und Chunkänderungen invalidieren den Multiblock-Cache. Capability-Änderungen ohne sichtbare Blockänderung können explizit aktualisiert werden:

```lua
peripherals.refresh()
```

Nach der ersten Initialisierung entstehen bei einem geänderten Graphen:

```lua
local _, address, primaryType =
  os.pullEvent("cc_aeroworks_peripheral_attached")
```

oder:

```lua
local _, address, primaryType =
  os.pullEvent("cc_aeroworks_peripheral_detached")
```

Lokale Pulteingaben verwenden weiterhin:

```lua
local _, peripheralName, socket, moduleId, value, channel, socketName =
  os.pullEvent("cc_aeroworks_desk_input")
```

## Displayvertrag

Texte werden auf zwei beziehungsweise drei Zeichen begrenzt. Zahlen werden gegen null abgeschnitten und auf den darstellbaren Bereich begrenzt. NaN und Unendlich erzeugen einen Lua-Fehler.

Die Pixelauflösung wird serverseitig getrennt für kleine und große Displays konfiguriert. Vor einem vollständigen Raster muss die wirksame Größe gelesen werden:

```lua
local size = desk.getDisplaySize("big")
print(size.width, size.height)
```

Koordinaten beginnen bei `(1,1)` links oben. `setDisplayPixels` erwartet exakt `height` Strings aus `0` und `1`, jeweils exakt `width` Zeichen breit. Text- und Zahlmethoden wechseln in den Textmodus, Pixelmethoden in den Rastermodus.
