# Bedienung

CC-Aeroworks unterscheidet drei Interaktionen, die bewusst nicht dieselbe Maustaste mit denselben Voraussetzungen verwenden. Das verhindert, dass beim Öffnen des Computers gleichzeitig ein Hebel bewegt oder das Pult gedreht wird. Ein seltenes Beispiel dafür, dass zusätzliche Regeln tatsächlich weniger Chaos erzeugen.

## Schnellübersicht

| Aktion | Eingabe | Voraussetzung |
|---|---|---|
| Computerterminal öffnen | Schleichen + Rechtsklick | leere Haupthand, aktiver Multiblock mit einem eingebetteten Computer |
| Steuerung bedienen | normaler Rechtsklick | montiertes Aeroworks-Modul direkt ansehen |
| Steuerungseinstellungen öffnen | Schraubenschlüssel + Rechtsklick | horizontale Seite eines Steuerungspults anklicken |
| Pult drehen | Schraubenschlüssel + Rechtsklick | Ober- oder Unterseite anklicken |
| Ponder-Erklärung öffnen | W halten | Maus über normalem oder erweitertem Computer-Steuerungspult |

## Computer öffnen

Bei einem gültigen Multiblock mit genau einem eingebetteten Computer:

1. Haupthand leeren.
2. Schleichen.
3. Ein beliebiges geladenes Pult des Multiblocks rechtsklicken.

Das angeklickte Pult muss nicht selbst das Computer-Steuerungspult sein. Der Computer wird eingeschaltet und sein Terminal geöffnet.

Bei einem externen Computer wird stattdessen dessen normales CC:Tweaked-Terminal verwendet. Das Steuerungspult ist dann ein Peripheral und besitzt kein eigenes zu öffnendes Terminal.

## Steuerung bedienen

Montierte Lever, Joysticks, Throttle Quadrants und andere Aeroworks-Module behalten ihre normale Aeroworks-Interaktion:

1. Modul direkt ansehen.
2. Ohne Schraubenschlüssel normal rechtsklicken oder die konfigurierte Eingabe verwenden.
3. Bei **Kombiniert** die konfigurierte Taste halten und die vorgesehene Mausachse bewegen.

Die Details des kombinierten Modus stehen unter [[Kombinierte-Eingabe]].

## Steuerungseinstellungen öffnen

1. Einen Create-Schraubenschlüssel halten.
2. Eine **horizontale Seite** des Steuerungspults rechtsklicken.
3. Im Aeroworks-Modulbildschirm Modul, Kanal und Eingabetyp konfigurieren.

Ober- und Unterseite bleiben für die normale Create-Schraubenschlüsselrotation reserviert.

Im Modus **Kombiniert** zeigt das mittlere Feld die Aktivierungstaste. Linksklick startet die Erfassung; Rechtsklick löscht die Belegung.

## Ein Computer pro Multiblock

Für einen eingebetteten Zugriffsweg wird genau ein Computer-Steuerungspult benötigt. Alle anderen Mitglieder bleiben normale Aeroworks-Steuerungspulte.

Alternativ genügt ein externer Computer an einem beliebigen Mitglied. Ein eingebetteter Computer und ein externer Computer sind keine gemeinsame Voraussetzung.

Wird in Survival versehentlich ein zweites Computer-Steuerungspult an denselben vollständig geladenen Multiblock gesetzt:

- das neu platzierte Pult wird zu einem normalen Aeroworks-Pult,
- seine Aeroworks-Module und Einstellungen bleiben erhalten,
- der zusätzliche normale oder erweiterte CC:Tweaked-Computer wird ausgeworfen,
- Computer-ID, Label, Terminalgröße und Speicherkapazität bleiben erhalten.

Im Creative-Modus wird die konfliktverursachende Platzierung abgebrochen, damit die Computer-ID nicht dupliziert wird.

## Ponder

Beide Computer-Steuerungspultvarianten besitzen dieselbe Create-Ponder-Szene. Sie zeigt:

- Aufbau eines linearen Multiblocks,
- genau einen eingebetteten Computer,
- Terminalzugriff von jedem Pult,
- normale Steuerung,
- Öffnen der Steuerungseinstellungen,
- externen Computer als Alternative,
- Verhalten bei versehentlicher Doppelplatzierung.

Die Szene wird über die übliche Ponder-Taste **W** aus Inventar oder Rezeptbetrachter geöffnet.
