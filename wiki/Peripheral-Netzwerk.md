# Peripheral-Netzwerk

Der eingebettete Computer behandelt den Pultverbund als Netzwerk aus einzelnen Desk-Adaptern. Er scannt die sechs Seiten jedes vollständig geladenen Pults und indexiert alle dort angebotenen CC:Tweaked-Peripherals.

## Netzwerkgraph

Ein Eintrag enthält:

- Primärtyp und zusätzliche CC:Tweaked-Typen,
- zugehörige Desk-ID,
- Desk-Adresse `x,y,z`,
- Blockposition des Geräts,
- Anschlussseite aus Sicht des Pults,
- echtes Ziel-Peripheral.

Andere Pulte desselben Verbunds werden beim Seitenscan ausgeschlossen. Das Netzwerk lädt keine Chunks nach.

```lua
local network = peripherals.getNetwork()
print(network.state, network.revision)
print(network.deskCount, network.peripheralCount)
```

## Pulte finden

```lua
local desks = peripherals.find("ControlDesk")
```

`ControlDesk` liefert immer eine Tabelle:

```lua
{
  ["12,64,-7"] = deskHandle,
  ["13,64,-7"] = deskHandle,
}
```

Ein Desk-Handle bietet lokale Module, Eingaben, Displays und Nachbargeräte:

```lua
local desk = desks["12,64,-7"]
print(textutils.serialize(desk.getInfo()))

local localDevices = desk.getPeripherals()
local north = desk.wrap("north")
local modem = desk.find("endermodem")
```

## Geräte nach Typ finden

### Kein Treffer

```lua
local radar = peripherals.find("radar")
-- nil
```

### Genau ein Treffer

```lua
local modem = peripherals.find("endermodem")
modem.open(42)
```

### Mehrere Treffer

```lua
local modems = peripherals.find("endermodem")

for address, modem in pairs(modems) do
  print(address)
end
```

`findAll` liefert immer eine Tabelle:

```lua
for address, modem in pairs(peripherals.findAll("endermodem")) do
  print(address)
end
```

## Typnormalisierung

Primärtyp und zusätzliche Peripheral-Typen werden gemeinsam indexiert. Suchbegriffe werden kleingeschrieben und zusätzlich ohne Leerzeichen, Bindestriche und Unterstriche verglichen.

Der Typ

```text
advanced_peripherals:ender_modem
```

ist damit über folgende Namen erreichbar:

```text
advanced_peripherals:ender_modem
ender_modem
EnderModem
endermodem
```

Bei kollidierenden Kurzformen sollte die vollständige namespaced ID verwendet werden.

## Echte Methoden-Handles

Ein gefundenes Gerät wird nicht kopiert. Das Handle delegiert die durch CC:Tweaked registrierten Methoden des echten `IPeripheral`.

Dadurch bleiben erhalten:

- Main-Thread-Aufrufe,
- `attach` und `detach`,
- Dateisystem-Mounts,
- Geräteereignisse,
- Zugriff auf andere erreichbare Peripherals,
- Work-Monitor-Limits.

Zusätzliche Metadaten stehen über `getPeripheralInfo()` bereit, sofern das Ziel nicht bereits eine eigene Methode dieses Namens besitzt.

```lua
local info = modem.getPeripheralInfo()
print(info.type)
print(info.deskAddress, info.side)
print(info.position.x, info.position.y, info.position.z)
```

## Koordinatenzugriff

```lua
local desk = peripherals.wrap(12, 64, -7)
local sameDesk = peripherals.wrap({ x = 12, y = 64, z = -7 })
local device = peripherals.wrap(12, 64, -8)
```

Bei mehreren Peripherals an derselben Blockposition wird ein Typ ergänzt:

```lua
local radar = peripherals.wrap(12, 64, -8, "radar")
```

## Aktualisierung

Der aktive eingebettete Computer prüft den Graphen alle fünf Ticks. Änderungen erzeugen sowohl die üblichen CC:Tweaked-Ereignisse `peripheral` und `peripheral_detach` als auch ausführlichere CC-Aeroworks-Ereignisse:

```lua
local _, address, primaryType =
  os.pullEvent("cc_aeroworks_peripheral_attached")
```

```lua
local _, address, primaryType =
  os.pullEvent("cc_aeroworks_peripheral_detached")
```

Eine reine Capability-Änderung kann sofort geprüft werden:

```lua
peripherals.refresh()
```

## Grenzen

Der globale Zugriff wird abgelehnt bei:

- mehreren eingebetteten Computern,
- mehr als 64 Pulten,
- teilweise geladenen Reihen,
- einem Computer, der den aufgelösten Verbund nicht besitzt.

Die sechs physischen Seiten des Computerblocks bleiben lokale CC:Tweaked-Seiten. Entfernte Geräte werden ausschließlich über `peripherals` adressiert.
