# Touch-Eingaben auf großen Pultanzeigen

Die große Pultanzeige und die große Radaranzeige reagieren wie ein erweiterter CC:Tweaked-Monitor auf einen normalen Rechtsklick mit leerer Hand. Die physische Modulfläche wird dabei auf ein 1-basiertes Raster abgebildet.

Das Raster verwendet die aktuell synchronisierte Serverauflösung des großen Pultdisplays. Standardmäßig sind das `11 x 5`, Änderungen an `display.large.width` und `display.large.height` werden ohne fest verdrahtete Clientwerte übernommen.

## Direkt angeschlossener `ControlDesk`

Ein normaler CC:Tweaked-Computer oder ein über Wired Modem angeschlossener Computer erhält zwei Ereignisse:

```lua
local _, peripheralName, x, y = os.pullEvent("monitor_touch")
```

`monitor_touch` hat absichtlich dieselbe Argumentform wie ein erweiterter CC:Tweaked-Monitor.

Zusätzlich liefert CC-Aeroworks die Modulidentität und Rastergröße:

```lua
local _, peripheralName, socket, socketName, moduleId, x, y, width, height =
  os.pullEvent("cc_aeroworks_desk_touch")
```

Damit kann ein Programm unterscheiden, ob der große Socket ein normales Display oder eine Radaranzeige enthält.

## Eingebetteter Computer

Der eingebettete Computer erhält Touches aus dem vollständigen Pultnetz über:

```lua
local _, deskId, deskIndex, socket, socketName, moduleId, x, y, width, height =
  os.pullEvent("cc_aeroworks_console_touch")
```

`deskId` bleibt die stabile Pultidentität; `deskIndex` ist der aktuelle Index im verbundenen Pultnetz. Die Touchkoordinaten beginnen links oben bei `(1, 1)`.

## Erweiterte Item-Schnellinfo

Mit aktivierten erweiterten Tooltips (`F3+H`) zeigen kleine und große Pultanzeige ihre aktuell wirksame Pixelauflösung als `Pixel: Breite × Höhe`. Die Werte stammen aus der synchronisierten Serverkonfiguration und sind daher nicht auf die Standardwerte `7 x 5` beziehungsweise `11 x 5` festgelegt.
