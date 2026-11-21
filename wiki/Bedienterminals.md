# Bedienterminals

[[Deutsch|Bedienterminals]] · [[English|English#operator-terminals]]

![Beispielausgabe im tatsächlich geöffneten erweiterten CC:-Terminals-Bedienterminal eines Pults.](https://raw.githubusercontent.com/wiki/TeutonStudio/CC-Aeroworks/assets/screenshots/wiki-operator-terminal.png)


Mit optionalem **CC: Terminals 0.1.1** kann der gemeinsame Geräteslot ein Terminal aufnehmen. So lassen sich Cockpit-Bedienstationen bauen, während das Programm auf einem getrennten Computer läuft.

## Gerät wählen

| Slot-Gerät | Ausgabe | Eingabe | Führt CraftOS aus |
|---|---|---|---|
| Normaler CC:Tweaked-Computer | monochrom | Tastatur | ja |
| Erweiterter CC:Tweaked-Computer | Farbe | Tastatur und Maus | ja |
| Normales CC:-Terminals-Terminal | monochrom | Tastatur | nein |
| Erweitertes CC:-Terminals-Terminal | Farbe | Tastatur und Maus | nein |

Ein Terminal besitzt keine lokal änderbaren Programme und kein lokales Dateisystem. Die Stufe erweitert Ein- und Ausgabe, nicht Benutzerrechte. `Ctrl+T` am Terminal beendet das entfernte Programm nicht.

## Einrichten

1. Installiere CC: Terminals zusätzlich zur normalen Modkombination.
2. Öffne die Modulübersicht mit Schleichen + Rechtsklick.
3. Setze ein normales oder erweitertes Terminal in den gemeinsamen Geräteslot.
4. Verbinde den ausführenden Computer direkt oder über Peripheral Link nach dem Verfahren von CC: Terminals.
5. Öffne die Bedienstation über den Terminalknopf der Übersicht.

Es gibt weiterhin höchstens ein Slot-Gerät pro Reihe. Computer und Terminal können diesen Platz nicht gleichzeitig belegen.

## Zugriff aus Lua

Mit eingesetztem Terminal besitzt dasselbe Pult-Peripheral zusätzlich den Typ `terminal`. Der verbundene Computer kann die üblichen CC-Terminalmethoden wie `write`, `getSize`, `setCursorPos`, `blit` und Farb-/Palettenfunktionen verwenden.

Ein kurzer Ausgabetest auf dem externen Computer:

```lua
local station = peripheral.find("terminal")
assert(station, "Kein Terminal verbunden")
station.setCursorPos(1, 1)
station.write("Cockpit bereit")
```

Dies schreibt nur eine Zeile. Für ein vollständiges Bedienprogramm verarbeitet der Host die CC:-Terminals-Ereignisse `terminal_key`, `terminal_char`, `terminal_paste` und bei der erweiterten Stufe `terminal_mouse_*`.

Ein erweitertes Terminal aktiviert außerdem Touch der **erweiterten Pultdisplays** für den externen Computer. Es erzeugt keinen eingebetteten Lua-Computer und keine owner-lokale Skriptausführung.

## Status und Datenerhalt

`ControlDesk.getInfo()` meldet `device` als `none`, `computer`, `advanced_computer`, `terminal` oder `advanced_terminal`. `programmable` ist ausschließlich bei einem eingebetteten Computer wahr.

Beim Abbau des Geräteträgers wandert das Gerät ins größere Reststück; bei Gleichstand nach links. Die letzte Steuereinheit droppt es zusätzlich. Beim Zusammenführen belegter Reihen bleibt ein Computer vor einem Terminal, innerhalb derselben Geräteart die erweiterte Stufe, danach das erste Gerät in Reihenfolge. Weitere Geräte werden ausgeworfen.

Siehe auch [[Computer-Steuerungspulte]] und die [technischen Terminaldetails](https://github.com/TeutonStudio/CC-Aeroworks/blob/master/docs/operator-terminals.md).
