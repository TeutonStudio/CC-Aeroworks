# Touch-Eingaben auf großen Pultanzeigen

Die große Pultanzeige und die große Radaranzeige verwenden ausschließlich den kombinierten Display-Eingabemodus. Ein normaler Leerhand-Rechtsklick wird nicht mehr als programmierbarer Touch interpretiert.

## Bedienung

1. Im Minecraft-Menü `Steuerung` unter `Sonstiges/Misc` die eigene Tastenbelegung `Display-Bedienung` / `Display interaction` belegen.
2. Das große Display oder große Radar mit dem Fadenkreuz ansehen.
3. Die Taste gedrückt halten.
4. Die Kamera wird eingefroren und ein halbtransparenter 3D-Zeiger erscheint orthogonal auf der Displayfläche.
5. Die Maus verschiebt den Zeiger über die Displayfläche.
6. Rechtsklick erzeugt `tap`.
7. Linksklick erzeugt `double_tap`.
8. Beim Loslassen der Taste endet die Sitzung sofort.

Der Zeiger bleibt auf die normierte Displayfläche `0..1` begrenzt. Das erste Maus-Sample beim Aktivieren wird verworfen, damit die Bewegung zum Anvisieren des Displays nicht als Zeigerbewegung übernommen wird. Wird das Display entfernt, der Spieler zu weit entfernt, ein Menü geöffnet oder der Fokus verloren, endet die Sitzung ebenfalls.

Die Zeigergeschwindigkeit kann über `displayPointerSensitivity` in `cc_aeroworks-client.toml` angepasst werden.

## CC:Tweaked-Ereignisse

### Direkt angeschlossener `ControlDesk`

Jede Displayaktion erzeugt:

```lua
local _, peripheralName, socket, socketName, moduleId, action, x, y, width, height =
  os.pullEvent("cc_aeroworks_desk_display_input")
```

`action` ist entweder `"tap"` oder `"double_tap"`.

Ein normaler `tap` erzeugt aus Kompatibilitätsgründen zusätzlich weiterhin:

```lua
local _, peripheralName, x, y = os.pullEvent("monitor_touch")
```

und:

```lua
local _, peripheralName, socket, socketName, moduleId, x, y, width, height =
  os.pullEvent("cc_aeroworks_desk_touch")
```

Ein `double_tap` erzeugt diese alten Touch-Ereignisse ausdrücklich nicht. Dadurch können Programme den Doppeltipp als eigenständige Eingabe behandeln, ohne zweimal auf `monitor_touch` zu reagieren.

### Eingebetteter Computer

Der eingebettete Computer erhält jede Displayaktion über:

```lua
local _, deskId, deskIndex, socket, socketName, moduleId, action, x, y, width, height =
  os.pullEvent("cc_aeroworks_console_display_input")
```

Für `tap` wird zusätzlich das kompatible Ereignis geliefert:

```lua
local _, deskId, deskIndex, socket, socketName, moduleId, x, y, width, height =
  os.pullEvent("cc_aeroworks_console_touch")
```

## Koordinaten und Sicherheit

Die Clientseite überträgt normierte Zeigerkoordinaten. Der Server prüft Desk, Socket, Modultyp, Controllerzugriff, Interaktionsreichweite und Koordinatenbereich und berechnet erst danach die aktuell konfigurierte 1-basierte Displayzelle.

Die große Pultanzeige und die große Radaranzeige verwenden damit dieselbe Geometrie und dieselbe dynamische Serverauflösung. Änderungen an `display.large.width` und `display.large.height` benötigen keine fest verdrahteten Clientwerte.
