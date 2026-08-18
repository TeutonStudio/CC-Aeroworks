# Touch-Eingaben auf großen Pultanzeigen

Die große Pultanzeige und die große Radaranzeige verwenden den kombinierten Display-Eingabemodus. Während die konfigurierte Display-Bedienungstaste gehalten wird, bewegt die Maus einen virtuellen Finger auf der Displayfläche. Linksklick erzeugt `tap`, Rechtsklick startet eine `draw`-Geste und Loslassen beendet sie mit `isEnd = true`.

Die primären Maustasten werden weiterhin über Raw-`MouseHandler`, NeoForge-Fallback und direktes GLFW-Polling abgesichert. Die Bewegung des virtuellen Fingers wird dagegen höherfrequent im Player-Turn-Pfad erfasst und erst anschließend tickweise zum Server gebündelt.

## Draw-Datenmodell

Die bisherigen Top-Level-Felder bleiben erhalten:

- `gestureId`: stabile Kennung der Geste;
- `sequence`: `0` beim Start, danach streng aufsteigend;
- `startX`, `startY`: serverseitig aufgelöster Startpunkt;
- `x`, `y`: aktueller Endpunkt des Events;
- `deltaX`, `deltaY`: Differenz zwischen aktuellem Endpunkt und Endpunkt des vorherigen akzeptierten Events;
- `directionU`, `directionV`: normierte aktuelle Bewegungsrichtung des virtuellen Fingers in Display-U/V;
- `speed`: Betrag der geglätteten Display-Velocity in normierten Displayeinheiten pro Sekunde;
- `isEnd`: Abschlussmarkierung der Geste.

`deltaX/deltaY` und `directionU/directionV` beschreiben absichtlich verschiedene Dinge. Das Delta ist der grobe serverseitige Event-zu-Event-Vektor in Pixeln. Die Direction entsteht aus der höherfrequenten tatsächlichen Fingerbewegung und beschreibt die lokale Bewegungsrichtung.

Die Velocity wird leicht exponentiell geglättet: 65 % neues Sample und 35 % vorherige Velocity. Erst danach wird sie normiert. Nullbewegung beendet die Glättungsserie, behält aber die zuletzt veröffentlichte Richtung und Geschwindigkeit für ein direkt folgendes Release-Event.

## Sub-Tick-Pfad

Eine schöne Kurve lässt sich nicht aus einem einzelnen 20-Hz-Endpunkt rekonstruieren. Deshalb sammelt der Client während einer aktiven Draw-Geste die höherfrequenten Pointerpositionen in einem begrenzten Pfadpuffer.

Pro Clienttick wird weiterhin höchstens **ein Draw-Paket** gesendet. Dieses enthält jedoch bis zu **16 Sub-Tick-Samples**. Falls bei sehr hoher Bildrate mehr Punkte anfallen, entfernt der Client bevorzugt geometrisch unwichtige Innenpunkte und erhält Endpunkte sowie markante Kurvenänderungen.

Jedes Sample enthält:

```text
x/y werden erst serverseitig bestimmt
u/v
normalized directionU/directionV
speed in U/V pro Sekunde
```

Der Server validiert jedes Sample einzeln und löst `u/v` gegen die aktuell konfigurierte Displayauflösung in Pixelkoordinaten auf. Bei jedem nicht-ersten Event wird der vorherige akzeptierte Endpunkt vor den neuen Batch gestellt. Dadurch enthält `event.samples` normalerweise die vollständige Teilstrecke, die das aktuelle Event zeichnen soll, ohne dass ein Lua-Handler den letzten Punkt global speichern muss.

`touchdisplay.drawSamples(event)` besitzt zusätzlich einen defensiven Fallback: Fehlt die Sample-Tabelle, ist sie leer oder enthält sie bei einem bewegten Folgeevent nur den aktuellen Punkt, wird aus `x/y` und `deltaX/deltaY` mindestens das Segment `previous -> current` rekonstruiert. Ein Datenverlust im optionalen Sub-Tick-Pfad darf damit nicht mehr still zu einem einzelnen Punkt werden.

## Lua-API

`touchdisplay` stellt für Draw unter anderem bereit:

```lua
local touch = require("touchdisplay")

local dx, dy = touch.drawDelta(event)
local du, dv = touch.drawDirection(event)
local speed = touch.drawSpeed(event)
local samples = touch.drawSamples(event)
local changedPixels = touch.drawStroke(event)
```

`drawSamples(event)` liefert die serverseitig aufgelöste Sample-Tabelle. Ein Eintrag besitzt:

```lua
{
    x = 42,
    y = 31,
    u = 0.2625,
    v = 0.2768,
    directionU = 0.81,
    directionV = 0.58,
    speed = 0.74
}
```

`drawStroke(event)` ist die bevorzugte High-Level-Zeichenfunktion. Sie verbindet benachbarte Samples mit kubischen Hermite-Kurven. Die Tangenten stammen aus `directionU/directionV`; falls an einem Punkt keine Richtung vorliegt, wird der lokale Verbindungsvektor als Fallback verwendet.

Die Hermite-Tangentenlänge wird an die Entfernung der beiden Samplepunkte gekoppelt. Dadurch bestimmt die gemessene Mausbewegungsrichtung die Kurvenform, ohne dass eine hohe Geschwindigkeit unkontrollierte Überschwinger erzeugt.

## Nativer Pixel-Batch

Das frühere Testskript rief für jeden einzelnen Rasterpunkt `setPixel()` auf. Jeder dieser Aufrufe konnte das vollständige Displayraster kopieren, serialisieren und erneut speichern. Die erste Stroke-Fassung vermied zwar die Einzelwrites, zog dafür aber das vollständige Raster über `getDisplay()` nach Lua, baute dort alle Zeilen neu auf und sendete sie über `setDisplayPixels()` zurück.

Die aktuelle Fassung verwendet stattdessen:

```lua
display.setPixelBatch(event, points, enabled?)
```

beziehungsweise auf dem Desk direkt:

```lua
desk.setDisplayPixelBatch(socket, points, enabled?)
```

Dabei ist `points` eine Liste aus `{x=..., y=...}`. Kotlin lädt das vorhandene gepackte Raster, kopiert das Bytearray genau einmal, wendet alle Punkte auf diese Kopie an und persistiert nur dann einmal, wenn sich tatsächlich mindestens ein Pixel geändert hat.

Damit gilt pro Draw-Event grob:

```text
Sub-Tick-Samples
    -> Hermite-Kurve
    -> kontinuierliche Rasterisierung
    -> Pixel deduplizieren
    -> ein nativer Packed-Raster-Patch
    -> höchstens ein persistierter Raster-Write
```

## Ereignisargumente

### Direkt angeschlossener ControlDesk

Alle bisherigen Positionen bleiben bestehen. Nach `speed` wird die neue Sample-Tabelle angehängt:

```lua
local _, peripheralName, socket, socketName, moduleId, action, x, y, width, height,
      handlerPath, u, v,
      gestureId, sequence, startX, startY, deltaX, deltaY, isEnd,
      directionU, directionV, speed, samples =
  os.pullEvent("cc_aeroworks_desk_display_input")
```

### Eingebetteter Computer

```lua
local _, deskId, deskIndex, socket, socketName, moduleId, action, x, y, width, height,
      handlerPath, u, v, deskX, deskY, deskZ,
      gestureId, sequence, startX, startY, deltaX, deltaY, isEnd,
      directionU, directionV, speed, samples =
  os.pullEvent("cc_aeroworks_console_display_input")
```

Der automatische Display-Handler wandelt das letzte Argument in `event.samples` um.

## Empfohlenes Draw-Skript

Für normales Freihandzeichnen genügt jetzt:

```lua
local touch = require("touchdisplay")

return {
    onDraw = function(event)
        local _, sequence = touch.drawIdentity(event)
        if sequence == 0 then touch.clear(event) end
        touch.drawStroke(event)
    end
}
```

Das Skript benötigt keinen eigenen vorherigen Punkt und keine eigene Kurveninterpolation.

## Sicherheit

Der Client überträgt nur normierte Displaykoordinaten und Bewegungsmetadaten. Der Server prüft weiterhin Zielblock, Socket, Controllerzugriff, Sable-/Welt-Reichweite, Gestenreihenfolge und Wertebereiche. Alle Pixelkoordinaten werden erst serverseitig aus der tatsächlich aktuellen Displayauflösung berechnet. Ein Paket darf höchstens 16 Samples enthalten; verwaiste Gesten werden nach kurzer Zeit verworfen. Der native Pixel-Batch begrenzt zusätzlich die Anzahl der Punkte pro Lua-Aufruf.
