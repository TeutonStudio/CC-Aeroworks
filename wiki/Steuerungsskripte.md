# Steuerungsskripte

[[Deutsch|Steuerungsskripte]] · [[English|English#control-scripts]]

Normale Steuermodule wie Hebel, Joysticks, Pedale und Tasten können bei Eingabeänderungen ein Lua-Skript auf dem eingebetteten Computer ausführen. Das eignet sich für einzelne Aktionen wie Fahrwerk, Beleuchtung oder eine Meldung im Terminal.

## Skript zuweisen

1. Setze einen Computer in den gemeinsamen Geräteslot und schalte ihn ein.
2. Speichere darauf eine nicht leere `.lua`-Datei, zum Beispiel `/gear.lua`.
3. Öffne mit Schraubenschlüssel + Rechtsklick auf eine horizontale Pultseite die Moduleinstellungen.
4. Wähle beim betreffenden Modul unter **Skriptquelle** die Datei.
5. Zum Entfernen der Bindung wähle **Keine Skriptquelle**.

Steuerskripte benötigen keinen Display-Import. Der Katalog umfasst höchstens 256 Lua-Dateien, jeweils maximal 64 KiB und acht Unterverzeichnisebenen. Der Pfad und die Datei werden serverseitig geprüft.

## Kleiner Handler

```lua
local event = ...
if event.value > 0 and (event.previousValue or 0) <= 0 then
  print("Betätigt: " .. event.channel)
end
```

Speichere dies als `/gear.lua`. Der Handler wird bei Wertänderungen aufgerufen, einschließlich Loslassen und negativer Achsenwerte. Ohne Änderung gibt es keinen wiederholten Aufruf. Die Prüfung im Beispiel reagiert nur auf den Übergang in einen positiven Wert.

Alternativ kann eine Datei eine Funktion oder eine Tabelle mit `onInput` zurückgeben:

```lua
return {
  onInput = function(event)
    print(event.socketName, event.channel, event.previousValue, event.value)
  end,
}
```

## Ereignisdaten

| Feld | Inhalt |
|---|---|
| `deskId`, `deskIndex` | Herkunftspult |
| `socket`, `socketName` | nullbasierter Index und Name des Sockets |
| `moduleId`, `channel` | Modul und betroffener Kanal |
| `value`, `previousValue` | aktueller und vorheriger Wert |
| `handler` | Skriptpfad |

Das Ereignis steht außerdem als `controlEvent` zur Verfügung. Das Skript wird pro Aufruf frisch geladen; Dateiänderungen gelten beim nächsten Eingang.

Handler sollen zügig zurückkehren. Dauerhafte Schleifen und wartende Ereignisschleifen gehören in ein Hauptprogramm. Der Computer muss eingeschaltet sein und seine normale CraftOS-Ereignisschleife ausführen. Lade- und Laufzeitfehler werden ausgegeben; spätere Eingaben bleiben möglich.

Die Bindung startet das Skript ausschließlich auf dem eingebetteten Computer. Externe Computer empfangen weiter Eingabeereignisse, führen diese gespeicherten Pfade aber nicht automatisch aus. Ein externer lokaler `ControlDesk`-Adapter bietet `setControlScript(socket, path)`; diese Methode gehört nicht zum eingebetteten Desk-Handle.

Für Display-Touch gelten die eigenen Handler und Bindungen unter [[Programmierbare-Displays]]. Weitere Vertragsdetails: [Control Scripts](https://github.com/TeutonStudio/CC-Aeroworks/blob/master/docs/control-scripts.md).

![Das vorhandene Skript /gear.lua ist dem Hebelmodul tatsächlich zugewiesen.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-bound-control-script.png)
