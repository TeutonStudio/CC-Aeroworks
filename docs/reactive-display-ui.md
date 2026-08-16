# Reactive Display UI

Große programmierbare Desk Displays können entweder imperativ oder als reaktive UI betrieben werden. Beide Modi verwenden dieselbe physische Pixelgeometrie, besitzen den Bildschirm aber niemals gleichzeitig.

## Besitzmodell

Es gibt zwei klar getrennte Wege:

- **Legacy/imperativ:** `display`, `touchdisplay` und die `ControlDesk`-Methoden schreiben Text oder Pixel direkt in den normalen Displayzustand. Ein optionaler Legacy-Touch-Handler kann `onTap`, `onDoubleTap` oder `onPointer` bereitstellen.
- **Reactive UI:** Eine mit `require("cc_aeroworks.ui")` geschriebene Application besitzt einen `ReactiveDisplayFrame`. Darstellung entsteht ausschließlich aus Composition, Layout und Draw.

Sobald eine Reactive Application aktiv ist, werden imperative Text-/Pixel-Schreibversuche auf dieses Display mit einem Lua-Fehler abgelehnt. Wird die Reactive Application entfernt, wird ihr Frame gelöscht und der normale Displayzustand ist wieder zuständig.

Ein reiner Legacy-Touch-Handler startet keine leere Reactive Application und erzeugt keinen Reactive Frame.

## Minimale Application

```lua
local ui = require("cc_aeroworks.ui")

return ui.app(function()
    ui.Text("READY")
end)
```

Im ModuleScreen des großen Displays wird dieses Skript als **Application** angeboten. Der Server akzeptiert in dieser Rolle nur Skripte, die die Reactive-UI-API tatsächlich importieren.

## State und Invalidierung

`ui.state()` registriert beim Lesen eine Dependency des aktuell ausgeführten Composition-, Layout- oder Draw-Scopes. Eine Änderung invalidiert nur die Scopes, die diesen State gelesen haben.

```lua
return ui.app(function()
    local enabled = ui.state("enabled", false)

    ui.Button {
        text = function()
            return enabled.get() and "ON" or "OFF"
        end,
        onTap = function()
            enabled.set(not enabled.get())
        end
    }
end)
```

Ein Tap ist weiterhin ein einmaliges Ereignis. Der Callback verändert State; der State erzeugt anschließend die reaktive Invalidierung.

## Pointer als reaktive Eingabequelle

Für die kontinuierliche Sicht auf den letzten Display-Input stellt die Runtime bereit:

```lua
local pointer = ui.input.pointer()
local event = pointer.get()
```

`pointer.get()` verhält sich wie eine andere reaktive Datenquelle. Wird es während Draw gelesen, invalidiert der nächste Pointer-Input nur den abhängigen Draw-Scope. Wird es während Composition gelesen, kann die Component-Struktur neu zusammengesetzt werden.

Der Snapshot enthält aktuell:

```lua
{
    revision = 12,
    action = "tap", -- oder "double_tap"
    x = 80,
    y = 42,
    width = 160,
    height = 112,
    u = 0.5,
    v = 0.375,
    deskId = "...",
    deskIndex = 1,
    socket = 2,
    socketName = "big",
    moduleId = "...",
    deskX = 10,
    deskY = 64,
    deskZ = -4
}
```

`revision` wird für jeden Pointer-Input erhöht. Zwei identische Taps auf dieselbe Zelle bleiben dadurch zwei verschiedene Eingaben.

## Pointer-State und Gesten

Pointer-State und Gesture-Callbacks erfüllen unterschiedliche Aufgaben:

- `ui.input.pointer()` ist beobachtbarer Zustand und eignet sich beispielsweise für Cursorposition, Marker oder Koordinatenanzeige.
- `onTap`, `onDoubleTap` und `onPointer` sind einmalige Aktionen und eignen sich für Buttons, Navigation und State-Änderungen.

Beispiel:

```lua
local ui = require("cc_aeroworks.ui")

return ui.app(function()
    local pointer = ui.input.pointer()
    local selected = ui.state("selected", false)

    ui.Column({}, function()
        ui.Button {
            text = function() return selected.get() and "SELECTED" or "SELECT" end,
            onTap = function() selected.set(true) end
        }

        ui.Text {
            text = function()
                local event = pointer.get()
                if not event then return "NO INPUT" end
                return ("%d,%d"):format(event.x, event.y)
            end
        }
    end)
end)
```

## Rendering-Transaktion bei Eingabe

Eine Displayaktion wird in dieser Reihenfolge verarbeitet:

1. Server validiert Display, Reichweite, Sable-Projektion und normierte Koordinaten.
2. Der eingebettete Computer erhält `cc_aeroworks_console_display_input`.
3. Der Runtime-Pointer-Snapshot wird aktualisiert und seine `revision` erhöht.
4. Die Pointer-Dependency wird invalidiert.
5. Controller- und Node-Gesture-Callbacks laufen einmalig.
6. Dabei geänderte `ui.state()`-Werte erzeugen weitere Invalidierungen.
7. Die Runtime konsumiert die zusammengefassten Invalidierungen und führt den minimal notwendigen Composition-/Layout-/Draw-Pass aus.
8. Dirty Rectangles werden in den Reactive Frame geschrieben und committed.

Dadurch erzeugt ein Tap, der gleichzeitig Pointer-State und lokalen UI-State verändert, keinen absichtlich getrennten Doppel-Render.

## Automatische Runtime und CraftOS

Automatisch gebundene Applications werden vom CraftOS-Autorun über `ui.createSupervisor()` verwaltet. Der Supervisor besitzt keine private Lua-Coroutine. Seine Arbeit läuft auf derselben CraftOS-Coroutine, die das Ereignis abholt.

Das ist wichtig, weil `listDisplays()`, `beginFrame()`, `clearFrame()` und `commit()` auf dem Minecraft-Hauptthread ausgeführt werden müssen und dabei über den normalen CC:Tweaked-Scheduler yielden können. Die Runtime baut keinen zweiten Scheduler und behandelt keine `task_completed`-Ereignisse selbst.

`ui.supervise()` bleibt als öffentlicher Blocking-Wrapper verfügbar und verwendet intern denselben Supervisor.

## Legacy-Touch-Handler

Ein Legacy-Handler bleibt für bestehende Programme verfügbar:

```lua
local touchdisplay = require("touchdisplay")

return {
    onTap = function(event)
        touchdisplay.setPixel(event, event.x, event.y, true)
    end
}
```

Er wird im ModuleScreen unter **Legacy Touch** ausgewählt. Ist keine Reactive Application aktiv, schreibt er in den normalen Displayzustand. Ist eine Reactive Application aktiv, sollte der Handler nur globale Aktionen oder Navigation übernehmen und nicht imperativ auf dasselbe Display schreiben.

## Beispiel

`examples/cc/reactive-touch-marker.lua` zeichnet die letzte Pointerposition reaktiv und markiert einen normalen Tap zusätzlich mit einem Kreis.
