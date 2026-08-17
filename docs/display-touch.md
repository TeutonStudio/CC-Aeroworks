# Touch-Eingaben auf großen Pultanzeigen

Die große Pultanzeige und die große Radaranzeige verwenden ausschließlich den kombinierten Display-Eingabemodus. Ein normaler Leerhand-Rechtsklick wird nicht mehr als programmierbarer Touch interpretiert.

## Bedienung

1. Im Minecraft-Menü `Steuerung` unter `Sonstiges/Misc` die eigene Tastenbelegung `Display-Bedienung` / `Display interaction` belegen.
2. Das große Display oder große Radar mit dem Fadenkreuz ansehen.
3. Die Taste gedrückt halten.
4. Die Kamera wird eingefroren und ein halbtransparenter 3D-Zeiger erscheint orthogonal auf der Displayfläche.
5. Die Maus verschiebt den Zeiger über die Displayfläche.
6. Rechtsklick erzeugt `tap`.
7. Linksklick aktiviert den Display-Hold und erzeugt `hold`; beim Loslassen wird der interne Hold-Zustand beendet.
8. Beim Loslassen der Display-Bedienungstaste endet die Sitzung sofort.

Während die Display-Bedienungstaste gehalten wird, besitzen die beiden primären Maustasten Vorrang vor der normalen kombinierten Binding-Verarbeitung und vor Vanilla-Aktionen. Damit können Mausbewegung und Touchaktion innerhalb derselben Display-Sitzung parallel ausgewertet werden, ohne dass ein Klick als Angriff, Benutzung oder anderes Steuerobjekt verloren geht.

Der Zeiger bleibt auf die normierte Displayfläche `0..1` begrenzt. Das erste Maus-Sample beim Aktivieren wird verworfen, damit die Bewegung zum Anvisieren des Displays nicht als Zeigerbewegung übernommen wird. Wird das Display entfernt, der Spieler zu weit entfernt, ein Menü geöffnet oder der Fokus verloren, endet die Sitzung ebenfalls.

Die Zeigergeschwindigkeit kann über `displayPointerSensitivity` in `cc_aeroworks-client.toml` angepasst werden.

## TouchTrace-Diagnose

Der Diagnose-Branch protokolliert den vollständigen Touch-Pfad absichtlich auf `INFO`/`WARN`. Dafür muss `runClient` **nicht** im Debug-Modus gestartet werden. Alle relevanten Zeilen tragen den stabilen Präfix:

```text
[TouchTrace]
```

Die Stufen bedeuten:

- `[client]`: Display-Sitzung, Mausaktion und Paketversand;
- `[server]`: Paketempfang, Sicherheits-/Reichweitenprüfung und Pixelauflösung;
- `[dispatch]`: Multiblock-Auflösung, gespeichertes Display-Binding und Event-Weiterleitung;
- `[peripheral]`: Weiterleitung an normale/verkabelte ComputerCraft-Computer;
- `[catalog]`: Scan und Auflösung der auf dem eingebetteten Computer vorhandenen Lua-Skriptdateien;
- `[binding]`: Lesen, Setzen oder ungültiges Zurückfallen einer Skriptbindung;
- `[lua]`: `loadfile`, Handler-Aufbau, Callback-Auswahl, Callback-Erfolg oder Lua-Fehler;
- `[pixels]`: tatsächlicher Raster-Write in das Displaymodul und unmittelbarer Readback.

Für eine Reproduktion genügt es, die Skriptquelle einzustellen, die Display-Bedienungstaste zu halten, den Pseudo-Finger zu bewegen und mindestens einmal rechts sowie links zu klicken. Danach lassen sich die `[TouchTrace]`-Zeilen aus `logs/latest.log` chronologisch lesen. Die letzte erreichte Stufe zeigt unmittelbar, in welcher Schicht die Verarbeitung abbricht.

## CC:Tweaked-Ereignisse

### Direkt angeschlossener `ControlDesk`

Jede Displayaktion erzeugt:

```lua
local _, peripheralName, socket, socketName, moduleId, action, x, y, width, height =
  os.pullEvent("cc_aeroworks_desk_display_input")
```

Die aktuelle kombinierte Mausbedienung erzeugt `action = "tap"` oder `action = "hold"`. Der ältere Wert `"double_tap"` bleibt aus Protokollkompatibilität erhalten, wird durch die aktuelle Rechts-/Linksklick-Belegung aber nicht mehr erzeugt.

Ein normaler `tap` erzeugt aus Kompatibilitätsgründen zusätzlich weiterhin:

```lua
local _, peripheralName, x, y = os.pullEvent("monitor_touch")
```

und:

```lua
local _, peripheralName, socket, socketName, moduleId, x, y, width, height =
  os.pullEvent("cc_aeroworks_desk_touch")
```

`hold` und `double_tap` erzeugen diese alten Touch-Ereignisse ausdrücklich nicht. Damit bleibt `monitor_touch` ein einfacher Tap-Kompatibilitätspfad und speziellere Displayaktionen können nicht versehentlich doppelt verarbeitet werden.

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

Automatische Display-Handler können `onTap`, `onHold`, `onDoubleTap` oder als allgemeinen Fallback `onPointer` bereitstellen. Das Modul `touchdisplay` stellt entsprechend `isTap(event)`, `isHold(event)` und `isDoubleTap(event)` bereit.

## Koordinaten und Sicherheit

Die Clientseite überträgt normierte Zeigerkoordinaten. Der Server prüft Desk, Socket, Modultyp, Controllerzugriff, Interaktionsreichweite und Koordinatenbereich und berechnet erst danach die aktuell konfigurierte 1-basierte Displayzelle.

Die große Pultanzeige und die große Radaranzeige verwenden damit dieselbe Geometrie und dieselbe dynamische Serverauflösung. Änderungen an `display.large.width` und `display.large.height` benötigen keine fest verdrahteten Clientwerte.