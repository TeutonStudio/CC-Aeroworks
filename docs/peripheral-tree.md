# Hierarchische Peripheral-Ansicht

Der eingebettete Computer eines ComputerControlDesk stellt zusätzlich zur bestehenden `peripherals`-API eine hierarchische Ansicht des vollständig geladenen Pultnetzes bereit.

## Terminalbefehl

Auf einem eingebetteten ComputerControlDesk wird beim CraftOS-Start ausschließlich für diesen Computer ein Alias für den Befehl `peripherals` gesetzt. Normale CC:Tweaked-Computer behalten das originale `peripherals`-Programm unverändert.

Beispielausgabe:

```text
Control Desk Peripherals:
ControlDesk 12,64,-7 [computer]
  north -> monitor [12,64,-8]
  up -> advanced_peripherals:ender_modem [12,65,-7]
ControlDesk 13,64,-7
  south -> speaker [13,64,-6]
```

Jedes Pult ist ein Root-Eintrag. Direkt angrenzende CC:Tweaked-Peripherals werden als Unterobjekte nach ihrer Anschlussseite aufgeführt.

## Lua-API

```lua
local tree = peripherals.getTree()
local desk = tree["12,64,-7"]
print(desk.x, desk.y, desk.z, desk.id, desk.variant)

local monitor = desk.peripherals.north
print(monitor.type, monitor.x, monitor.y, monitor.z)
monitor.handle.setTextScale(0.5)
```

Ein Desk-Eintrag enthält mindestens:

```lua
{
  id = "stable-desk-uuid",
  address = "12,64,-7",
  index = 1,
  x = 12,
  y = 64,
  z = -7,
  dimension = "minecraft:overworld",
  computer = true,
  variant = "computer",
  facing = "north",
  loaded = true,
  handle = deskHandle,
  peripherals = { ... }
}
```

Ein Child-Eintrag enthält mindestens:

```lua
{
  address = "12,64,-7/north",
  type = "monitor",
  types = { "monitor" },
  deskId = "stable-desk-uuid",
  deskAddress = "12,64,-7",
  side = "north",
  x = 12,
  y = 64,
  z = -8,
  position = { x = 12, y = 64, z = -8, dimension = "minecraft:overworld" },
  loaded = true,
  handle = peripheralHandle
}
```

Die Handles sind dieselben delegierenden Runtime-Handles wie bei `peripherals.find`, `findAll` und `wrap`. `getTree()` baut keine zweite Peripheral-Verbindung auf.

## Eindeutige Zuordnung

Ein physisches Peripheral wird im Graph nur einmal aufgenommen. Wenn dasselbe Ziel von mehreren Pultseiten erreichbar ist, gewinnt deterministisch das erste Pult in Netzwerkreihenfolge und anschließend die feste Scanreihenfolge `north`, `south`, `east`, `west`, `up`, `down`.

Die vollständige Adresse bleibt `desk-x,desk-y,desk-z/side`. Die Baumansicht verwendet innerhalb eines Desks die Seite als Child-Key.

## Lifecycle

Beim Graphwechsel werden entfernte Bindings zuerst geschlossen. Danach wird der neue vollständige Graph veröffentlicht, bevor neue `IPeripheral.attach(...)`-Aufrufe stattfinden. Neue Bindings werden vor dem Attach in das Runtime-Verzeichnis eingetragen. Dadurch sieht ein Peripheral, das während `attach()` bereits `getAvailablePeripherals()` abfragt, den neuen konsistenten Netzwerkzustand.

Bestehende Bindings werden bei unverändertem physischem Ziel weiterverwendet und nur auf die aktuellen Graph-Metadaten aktualisiert. Attach-/Detach-Ereignisse entstehen deshalb nicht bloß durch eine neue Multiblock-Revision.

## Kompatibilität

Die bestehenden Methoden bleiben erhalten:

- `peripherals.find(type)`
- `peripherals.findAll(type)`
- `peripherals.wrap(...)`
- `peripherals.getDesks()`
- `peripherals.getTypes()`
- `peripherals.getNetwork()`
- `peripherals.refresh()`
- `desk.getPeripherals()`
- `desk.find(...)`
- `desk.findAll(...)`
- `desk.wrap(side)`

`getTree()` ist eine zusätzliche Ansicht und kein Ersatz für die bestehenden Suchmethoden.

## Laufzeitprüfung

Für die Ingame-Prüfung sollte mindestens folgende Reihe aufgebaut werden:

```text
Desk A | ComputerDesk B | Desk C
  |            |            |
monitor      speaker     ender modem
```

Abnahmekriterien:

1. Das Terminal lässt sich über A, B und C öffnen.
2. `peripherals` zeigt drei ControlDesk-Roots.
3. Monitor, Speaker und Ender Modem erscheinen jeweils genau unter ihrem zugeordneten Desk.
4. `peripherals.getTree()` liefert dieselbe Struktur programmatisch.
5. Mindestens eine echte Child-Methode lässt sich über `child.handle` aufrufen.
6. Platzieren und Entfernen eines Child-Peripherals aktualisiert den Graph ohne Computerneustart.
7. Normale CC:Tweaked-Computer zeigen weiterhin ihr originales flaches `peripherals`-Programm.
