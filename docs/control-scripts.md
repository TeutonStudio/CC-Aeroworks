# Skripte durch normale Steuermodule auslösen

Im Einstellungsfenster eines normalen Steuermoduls steht jetzt die Zeile **Skriptquelle** zur
Verfügung. Sie gilt für Module mit Eingabekanälen, etwa Steuerknüppel, Hebel, Pedale und Tasten.
Die Dateien liegen auf dem eingebauten ComputerControlDesk des aktiven Pultverbunds. Eine normale,
nicht leere `.lua`-Datei genügt; ein Display-Import ist nicht erforderlich. Mit **Keine Skriptquelle**
wird die Zuordnung entfernt. Die Auswahl wird gespeichert, mit dem Client synchronisiert und beim
Entfernen des Moduls gelöscht. Displays behalten ihre bisherigen eigenen Skriptregeln.

Ändert sich ein Kanal des Moduls, führt der eingebaute Computer das gewählte Skript aus. Dazu gehören
Betätigen, Loslassen und Änderungen analoger Werte. Ohne Änderung erfolgt kein wiederholter Aufruf.
Es gelten die nativen Modulwerte, einschließlich negativer Achsenwerte. Ereignisse werden wie bisher
serverseitig pro Tick aus den Kanalwerten ermittelt; dies ist keine Sub-Tick-Tastenaufzeichnung.
Der Computer muss eingeschaltet sein und seine normale CraftOS-Ereignisschleife ausführen.

Ein einfaches Skript erhält die Eingabe als erstes Argument und zusätzlich als `controlEvent`:

```lua
local event = ...
if event.value > 0 and (event.previousValue or 0) <= 0 then
    print("Betätigt: " .. event.channel)
end
```

Alternativ kann die Datei eine Funktion oder eine Tabelle mit `onInput` zurückgeben:

```lua
return {
    onInput = function(event)
        print(event.socketName, event.channel, event.previousValue, event.value)
    end,
}
```

Die Eingabe enthält `deskId`, `deskIndex`, `socket` (nullbasiert), `socketName`, `moduleId`, `channel`,
`value`, `previousValue` und `handler` (Dateipfad). Bei mehrkanaligen Modulen lässt sich mit `channel`
zwischen Achsen oder Tasten unterscheiden. Das Skript wird bei jedem Aufruf frisch geladen; Änderungen
an der Datei greifen beim nächsten Eingabeereignis. Lang laufende Schleifen und eigene wartende
Ereignisschleifen gehören in das Hauptprogramm. Die gebundenen Skripte sollten zügig zurückkehren.
Lade- und Ausführungsfehler werden ausgegeben; spätere Eingaben bleiben funktionsfähig.

Die vorhandenen sieben Argumente von `cc_aeroworks_console_input` bleiben in derselben Reihenfolge.
Angehängt werden der Skriptpfad (leer bei fehlender Zuordnung) und der vorherige Kanalwert. Programme
können diese Ereignisse weiterhin selbst empfangen, auch wenn ein gebundenes Skript ausgeführt wird.
Eine Zuordnung startet das Skript auf dem eingebauten Computer, nicht auf extern angeschlossenen
Computern. Über einen Desk-Handle ist dieselbe Zuordnung programmatisch möglich:

```lua
local desk = peripherals.find("ControlDesk")
desk.setControlScript("left", "/gear.lua")
desk.setControlScript("left", "") -- Zuordnung entfernen
```

Dateiauswahl und programmatische Zuordnung prüfen den Pfad und das Vorhandensein der Datei auf dem
Server. Die Kataloggrenzen entsprechen dem Displaykatalog: höchstens 256 Lua-Dateien, maximal 64 KiB
pro Datei und acht Unterverzeichnisebenen.
