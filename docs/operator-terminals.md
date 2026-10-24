# Geschützte Bedienstationen mit CC: Terminals

Ein ComputerPult oder ComputerStand besitzt einen gemeinsamen Geräteslot für die gesamte verbundene Reihe. In diesen Slot passt entweder ein normaler/erweiterter CC:Tweaked-Computer oder – wenn `ccterminals` 0.1.1 installiert ist – ein normales/erweitertes CC: Terminals-Terminal.

## Computer und Terminal

Ein Computer ist der programmierbare Eigentümer der Reihe. Er speichert Programme und Einstellungen, führt CraftOS aus und erhält über die netzwerkweite `peripherals`-API Zugriff auf die verbundenen Pulte und Nachbargeräte.

Ein Terminal ist dagegen nur eine Bedienstation. Es besitzt kein lokales Dateisystem und führt kein Programm aus. Ein über Peripheral Link oder direkt angeschlossener, geschützter Computer findet das Pult zusätzlich als Peripheral vom Typ `terminal`, zeichnet mit der normalen `term`-API und erhält die von CC: Terminals erzeugten `terminal_*`-Ereignisse. `Ctrl+T` beendet das entfernte Programm nicht.

## Normal und erweitert

Die Stufe beschreibt die Ein-/Ausgabe, nicht die Berechtigung:

| Gerät | Ausgabe | Eingabe | Programmierbar |
|---|---|---|---|
| normaler Computer | monochrom | Tastatur | ja |
| erweiterter Computer | Farbe | Tastatur und Maus | ja |
| normales Terminal | monochrom | Tastatur | nein |
| erweitertes Terminal | Farbe | Tastatur und Maus | nein |

Ein erweitertes Terminal aktiviert außerdem Touch-Ereignisse der erweiterten Pultdisplays für den extern angeschlossenen Computer. Ein normales Terminal tut dies nicht.

## Peripheral-Informationen

`getInfo()` des `ControlDesk`-Peripherals meldet mit `device` einen der Werte `none`, `computer`, `advanced_computer`, `terminal` oder `advanced_terminal`. `programmable` ist nur bei einem eingebetteten Computer `true`. Das Terminal bleibt gleichzeitig Teil desselben `ControlDesk`-Peripherals, damit Pult-, Radar- und Terminalfunktionen gemeinsam verfügbar sind.
