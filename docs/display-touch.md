# Touch-Eingaben auf großen Pultanzeigen

Die große Pultanzeige und die große Radaranzeige verwenden ausschließlich den kombinierten Display-Eingabemodus. Ein normaler Leerhand-Rechtsklick wird nicht mehr als programmierbarer Touch interpretiert.

## Bedienung

1. Im Minecraft-Menü `Steuerung` unter `Sonstiges/Misc` die eigene Tastenbelegung `Display-Bedienung` / `Display interaction` belegen.
2. Das große Display oder große Radar mit dem Fadenkreuz ansehen.
3. Die Taste gedrückt halten.
4. Die Kamera wird eingefroren und ein halbtransparenter 3D-Zeiger erscheint orthogonal auf der Displayfläche.
5. Die Maus verschiebt den Zeiger über die Displayfläche.
6. Rechtsklick startet eine `draw`-Geste. Bewegung bei gehaltener rechter Maustaste erzeugt geordnete Draw-Samples; Loslassen erzeugt das abschließende Draw-Event mit `isEnd = true`.
7. Linksklick erzeugt einen einzelnen `tap`.
8. Beim Loslassen der Display-Bedienungstaste endet die Sitzung sofort; eine noch aktive Draw-Geste wird nach Möglichkeit sauber beendet.

Während die Display-Bedienungstaste gehalten wird, besitzen die beiden primären Maustasten Vorrang vor der normalen kombinierten Binding-Verarbeitung und vor Vanilla-Aktionen. Die funktionierende Eingabeerfassung bleibt absichtlich dreifach abgesichert: früher Raw-`MouseHandler`-Intercept, NeoForge-`MouseButton.Pre`-Fallback und direktes `GLFW.glfwGetMouseButton(...)`-Polling teilen denselben Buttonzustand. Draw baut erst hinter dieser Erfassung auf, damit die Touchfähigkeit nicht wieder vom Erfolg eines einzelnen Callbacks abhängt.

Der Zeiger bleibt auf die normierte Displayfläche `0..1` begrenzt. Das erste Maus-Sample beim Aktivieren wird verworfen, damit die Bewegung zum Anvisieren des Displays nicht als Zeigerbewegung übernommen wird. Wird das Display entfernt, der Spieler zu weit entfernt, ein Menü geöffnet oder der Fokus verloren, endet die Sitzung ebenfalls.

Die Zeigergeschwindigkeit kann über `displayPointerSensitivity` in `cc_aeroworks-client.toml` angepasst werden.

## Tap und Draw

`tap` ist zustandslos und enthält Displayidentität sowie die serverseitig aufgelöste aktuelle Pixelposition.

`draw` ist eine geordnete Geste. Jedes Draw-Event enthält zusätzlich:

- `gestureId`: stabile Kennung der aktuellen Geste;
- `sequence`: `0` beim Start, danach streng aufsteigend;
- `startX`, `startY`: serverseitig aufgelöste Startkoordinate der Geste;
- `deltaX`, `deltaY`: Differenz der aktuellen Pixelkoordinate zum **unmittelbar vorherigen akzeptierten Draw-Event**;
- `isEnd`: `true` ausschließlich beim abschließenden Event.

Die vorhandenen `x`/`y` bleiben die aktuelle Position. Ein Handler kann daher ohne eigenen vorherigen Eventzustand direkt das Segment `x-deltaX, y-deltaY -> x,y` zeichnen. Das Delta zum Startpunkt wird absichtlich nicht separat übertragen, weil es jederzeit aus aktueller Position und `startX/startY` berechnet werden kann.

Die Clientseite sendet während einer aktiven Draw-Geste höchstens ein Bewegungssample pro Clienttick. Der Server hält pro Geste den zuletzt akzeptierten Punkt, prüft die Reihenfolge und berechnet daraus das Delta in der tatsächlich aktuellen Displayauflösung. Verwaiste Gesten werden nach kurzer Zeit verworfen.

## TouchTrace-Diagnose

Der Diagnose-Branch protokolliert den vollständigen Touch-Pfad absichtlich auf `INFO`/`WARN`. Dafür muss `runClient` **nicht** im Debug-Modus gestartet werden. Alle relevanten Zeilen tragen den stabilen Präfix:

```text
[TouchTrace]
```

Die Stufen bedeuten:

- `[button-sample]`: physische LEFT/RIGHT-Flanken aus Raw/Event/Poll;
- `[client]`: Display-Sitzung, Tap-Versand und Draw-Start/Sample/Ende;
- `[server]`: Paketempfang, Sicherheits-/Reichweitenprüfung, Sequenzprüfung und Pixelauflösung;
- `[dispatch]`: Multiblock-Auflösung, gespeichertes Display-Binding und Event-Weiterleitung;
- `[peripheral]`: Weiterleitung an normale/verkabelte ComputerCraft-Computer;
- `[catalog]`: Scan und Auflösung der auf dem eingebetteten Computer vorhandenen Lua-Skriptdateien;
- `[binding]`: Lesen, Setzen oder ungültiges Zurückfallen einer Skriptbindung;
- `[lua]`: `loadfile`, Handler-Aufbau, Callback-Auswahl, Callback-Erfolg oder Lua-Fehler;
- `[pixels]`: tatsächlicher Raster-Write in das Displaymodul und unmittelbarer Readback.

Für eine Reproduktion genügt es, die Skriptquelle einzustellen, die Display-Bedienungstaste zu halten, einmal links zu tippen und rechts gedrückt eine Linie zu ziehen. Ein vollständiger Draw-Lauf zeigt `drawEdge=true`, `send draw stage=start`, optionale Samples, anschließend `send draw stage=end` und dieselbe `gestureId` mit steigender `sequence` bis Lua und Pixel-Write.

## CC:Tweaked-Ereignisse

### Direkt angeschlossener `ControlDesk`

Jede Displayaktion erzeugt weiterhin zuerst die bisherigen Felder:

```lua
local _, peripheralName, socket, socketName, moduleId, action, x, y, width, height,
      handlerPath, u, v,
      gestureId, sequence, startX, startY, deltaX, deltaY, isEnd =
  os.pullEvent("cc_aeroworks_desk_display_input")
```

Die aktuelle kombinierte Mausbedienung erzeugt `action = "tap"` oder `action = "draw"`. Die alten Protokollwerte `"hold"` und `"double_tap"` bleiben intern aus Kompatibilitätsgründen reserviert, werden von der neuen Mausbedienung aber nicht mehr erzeugt.

Ein normaler `tap` erzeugt aus Kompatibilitätsgründen zusätzlich weiterhin:

```lua
local _, peripheralName, x, y = os.pullEvent("monitor_touch")
```

und:

```lua
local _, peripheralName, socket, socketName, moduleId, x, y, width, height =
  os.pullEvent("cc_aeroworks_desk_touch")
```

`draw` erzeugt diese alten Touch-Ereignisse ausdrücklich nicht. Damit bleibt `monitor_touch` ein einfacher Tap-Kompatibilitätspfad und eine Draw-Geste wird nicht versehentlich doppelt verarbeitet.

### Eingebetteter Computer

Der eingebettete Computer erhält jede Displayaktion über:

```lua
local _, deskId, deskIndex, socket, socketName, moduleId, action, x, y, width, height,
      handlerPath, u, v, deskX, deskY, deskZ,
      gestureId, sequence, startX, startY, deltaX, deltaY, isEnd =
  os.pullEvent("cc_aeroworks_console_display_input")
```

Für `tap` wird zusätzlich das kompatible Ereignis geliefert:

```lua
local _, deskId, deskIndex, socket, socketName, moduleId, x, y, width, height =
  os.pullEvent("cc_aeroworks_console_touch")
```

Automatische Display-Handler können `onTap`, `onDraw` oder als allgemeinen Fallback `onPointer` bereitstellen. Die alten `onHold`/`onDoubleTap`-Pfade bleiben nur für Legacy-Ereignisse erhalten. Das Modul `touchdisplay` stellt unter anderem `isTap(event)`, `isDraw(event)`, `drawStart(event)`, `drawDelta(event)`, `drawIdentity(event)` und `drawEnded(event)` bereit.

## Koordinaten und Sicherheit

Die Clientseite überträgt normierte Zeigerkoordinaten. Der Server prüft Desk, Socket, Controllerzugriff, Interaktionsreichweite und Koordinatenbereich und berechnet erst danach die aktuell konfigurierte 1-basierte Displayzelle. Für Draw wird auch das Delta erst aus zwei serverseitig aufgelösten Pixelpunkten berechnet.

Die große Pultanzeige und die große Radaranzeige verwenden damit dieselbe Geometrie und dieselbe dynamische Serverauflösung. Änderungen an `display.large.width` und `display.large.height` benötigen keine fest verdrahteten Clientwerte.
