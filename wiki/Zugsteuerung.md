# Zugsteuerung und Create-Vorrichtungen

[[Deutsch|Zugsteuerung]] · [[English|English#trains-and-contraptions]]

CC-Aeroworks integriert Creates native Zugsteuerungen und Vorrichtungs-Steuerungen in passende Pultreihen. Optional ergänzt Create: Train Functionality einen Dashboard Controller als explizite Steuerquelle und CC:Tweaked-Peripheral.

## Native Create-Steuerungen integrieren

Platziere eine Create-Zugsteuerung oder Vorrichtungs-Steuerung an der linken oder rechten Seite eines Bodenpults beziehungsweise Steuerstands. Passende Segmente übernehmen Ausrichtung und Chassis; die Integration funktioniert auch bei umgekehrter Platzierungsreihenfolge. Verwende dieselbe Geometriefamilie und die gemeinsame Pultreihe.

Beide Steuerungen können gemischt Pultabschnitte überbrücken. Sie zählen zur Grenze von 64 physischen Segmenten, sind aber keine `ControlDesk`-Pulte mit Modul-Sockets. Direkt angrenzende Peripherals, Radarfilter und Data Links können trotzdem gemeinsame Quellen bereitstellen. Deckenpulte integrieren diese Create-Steuerungen nicht.

Ohne verbleibendes passendes Ankerpult fällt die zusammenhängende Steuerungsgruppe auf ihre normale Create-Darstellung zurück. Die native Zugbedienung bleibt erhalten. Das frühere separate `cc_aeroworks:train_controls`-Modul wird nicht mehr verwendet.

## Vorrichtungs-Steuerung aus Lua

Eine integrierte Vorrichtungs-Steuerung ist als `ContraptionControl` erreichbar, stationär auch für externe Computer und Modems.

```lua
local all = peripherals.findAll("ContraptionControl")
for address, control in pairs(all) do
  local info = control.getInfo()
  print(address, info.id, info.mode)
end
```

Die Modi heißen `actors`, `doors` und `elevator`:

| Modus | Methoden |
|---|---|
| `actors`, `doors` | `isEnabled`, `setEnabled(boolean)`, `toggle` |
| `elevator` | `getFloors`, `getSelectedFloor`, `selectFloor(indexOrName)` |
| alle | `getInfo`, `getState`, `getFilter` |

Unpassende Methoden erzeugen einen Lua-Fehler. Ein leerer Filter bedeutet alle Akteure. Änderungen melden `cc_aeroworks_contraption_control(address, id, mode, state, cause)`.

## Optional: Dashboard Controller

Für diese Integration wird **genau Create: Train Functionality 0.1.47** unterstützt.

1. Setze einen Dashboard Controller unter ein Segment der Pultzeile.
2. Öffne die Modulkonfiguration und wähle den Controller als Quelle.
3. Weise gewünschte Kanäle ausdrücklich zu: Achsen können Schub und Gleisrichtung liefern; Achsen oder Pedale außerdem Bremse, Buttons Bremse oder „Nächster Bahnhof“.

Nach der Quellenwahl bleiben Kanäle zunächst unzugeordnet. Mehrere Controller, eine unvollständig geladene Reihe oder der Redstone-Link-Frequenzmodus suspendieren Zuordnungen, ohne sie zu löschen.

## Controller direkt ansprechen

Auf einem externen CC:Tweaked-Computer mit verbundenem Controller:

```lua
local train = peripheral.find("DashboardController")
assert(train, "Kein Dashboard Controller verbunden")
print(textutils.serialize(train.getState()))
print(textutils.serialize(train.getInfo()))
```

Schreibmethoden beeinflussen den Zug:

```text
setThrottle(value)  -- -1 rückwärts bis +1 vorwärts
setSteer(value)     -- -1 links bis +1 rechts
setBrake(boolean)
setStation(boolean)
```

`setThrottle` und `setSteer` akzeptieren nur endliche Werte von -1 bis 1. Im Frequenzmodus sind Setter nicht verfügbar. Lua-Werte werden mit gebundenen Pultkanälen deterministisch zusammengeführt und beim Trennen des letzten Computers, Entladen oder Serverneustart neutralisiert.

Weitere Details: [[API-Schnellreferenz]] und [Lua-API](https://github.com/TeutonStudio/CC-Aeroworks/blob/master/docs/cc-peripheral-api.md).

![Create-Zugsteuerung und Vorrichtungs-Steuerung zwischen Pultsegmenten.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-native-create-controls.png)

![Dashboard Controller im bereiten Panelmodus; der Hebel ist ausdrücklich dem Schub zugeordnet.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-bound-dashboard.png)
