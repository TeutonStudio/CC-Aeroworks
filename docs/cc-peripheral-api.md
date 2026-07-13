# CC:Tweaked Peripheral API

Peripheral-Typ: `cc_aeroworks_control_desk`. Socketindizes sind die nativen nullbasierten Aeroworks-Indizes.

Eine ausführlichere deutschsprachige Einführung mit vollständigem Beispiel steht in [`peripheral-programming.md`](peripheral-programming.md).

```lua
local desk = peripheral.find("cc_aeroworks_control_desk")
assert(desk, "Kein Aeroworks Control Desk gefunden")
```

## Methoden

- `getSocketCount() -> number`
- `getModules() -> table`
- `getModule(socket) -> table|nil`
- `getInput(socket) -> number|table`: ein Kanal wird als Zahl, mehrere Kanäle als Tabelle nach Aeroworks-Kanal-ID geliefert.
- `getInputs() -> table`: Zuordnung Socket zu Zahl/Kanaltabelle.
- `getDisplays() -> table`
- `getDisplay(socket) -> table`
- `setDisplayText(socket, text) -> string`
- `setDisplayNumber(socket, value, zeroPad?) -> string`
- `clearDisplay(socket)`
- `clearDisplays() -> number`: Anzahl geleerter Displays.
- `getDisplaySize(socket) -> table`: Pixelgröße als `{ width, height }`.
- `getDisplayPixel(socket, x, y) -> boolean`
- `setDisplayPixel(socket, x, y, enabled) -> boolean`
- `setDisplayPixels(socket, rows) -> table`: schreibt das vollständige Raster mit einer Synchronisation.
- `clearDisplayPixels(socket)`: wechselt in den Pixelmodus und löscht das Raster.

Texte werden links beginnend auf zwei beziehungsweise drei Zeichen begrenzt. Zulässig sind `0-9`, Minus und Leerzeichen; jedes andere Zeichen wird konsistent zu einem Leerzeichen. Zahlen werden gegen null abgeschnitten und auf `-9..99` beziehungsweise `-99..999` begrenzt. `zeroPad` füllt die Ziffern rechts vom Vorzeichen mit Nullen. NaN und Unendlich erzeugen einen Lua-Fehler.

Der Pixelmodus verwendet beim Zweisteller ein Raster von `7x5`, beim Dreisteller `11x5`. Pixelkoordinaten beginnen bei `(1,1)` links oben. `setDisplayPixels` erwartet genau fünf Strings aus `0` und `1` mit der passenden Breite. Text-/Zahlenschreiben wechselt in den Textmodus; Pixelmethoden wechseln in den Pixelmodus. Das Feld `mode` eines Displayeintrags ist entsprechend `text` oder `pixels`.

Eingabewerte sind rohe Aeroworks-Kanalwerte. Die verifizierte Kanalbegrenzung ist `-15..15`; Buttonmodule verwenden ihre diskreten Kanalwerte. Mehrkanalmodule behalten ihre von Aeroworks vergebenen Kanal-IDs.

## Ereignis

Nur solange Computer angehängt sind, vergleicht der Server einmal pro Tick Eingabewerte. Bei Änderung:

```lua
local event, peripheralName, socket, moduleId, value, channel =
  os.pullEvent("cc_aeroworks_desk_input")
```

Der erste Snapshot erzeugt kein Ereignis; unveränderte Werte werden nicht gesendet.
