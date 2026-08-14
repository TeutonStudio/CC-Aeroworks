# Bedienung

CC-Aeroworks unterscheidet Terminalzugriff, Modulbedienung und Einstellungen bewusst voneinander. Das verhindert, dass beim Öffnen des Computers gleichzeitig ein Hebel bewegt oder das Pult gedreht wird. Selten führt mehr Struktur tatsächlich zu weniger Chaos.

## Schnellübersicht

| Aktion | Eingabe | Voraussetzung |
|---|---|---|
| Steuerungseinstellungen öffnen | Schleichen + Rechtsklick | Steuerungspult ansehen; native Aeroworks-Interaktion |
| Computerterminal öffnen | Schaltfläche **Computer** | zuerst die Aeroworks-Steuerungseinstellungen öffnen; aktives Pultnetz mit eingebettetem Computer |
| Steuerung bedienen | normaler Rechtsklick | montiertes Aeroworks-Modul direkt ansehen |
| Steuerungseinstellungen alternativ öffnen | Schraubenschlüssel + Rechtsklick | horizontale Seite eines Steuerungspults anklicken |
| Pult drehen | Schraubenschlüssel + Rechtsklick | Ober- oder Unterseite anklicken |
| Ponder-Erklärung öffnen | W halten | Maus über Computerpult-, Display- oder Radaritem |

## Steuerungseinstellungen öffnen

Aeroworks behält seine native Bedienung:

1. Ein Steuerungspult ansehen.
2. Schleichen beziehungsweise Shift halten.
3. Mit leerer Hand rechtsklicken.
4. Im Aeroworks-Modulbildschirm Modul, Kanal und Eingabetyp konfigurieren.

CC-Aeroworks fängt diese Kombination nicht ab. Insbesondere dient Shift während einer aktiven **Kombiniert**-Session nur als Kamera-Override; ein normaler Shift+Rechtsklick außerhalb einer solchen Session bleibt eine Weltinteraktion und öffnet die Aeroworks-Konfiguration.

Alternativ kann ein Create-Schraubenschlüssel auf einer horizontalen Pultseite verwendet werden. Ober- und Unterseite bleiben für die normale Create-Schraubenschlüsselrotation reserviert.

Im Modus **Kombiniert** zeigt das mittlere Feld die Aktivierungstaste. Linksklick startet die Erfassung; Rechtsklick löscht die Belegung.

## Computer öffnen

Bei einem gültigen Pultnetz mit genau einem eingebetteten Computer:

1. Mit Shift+Rechtsklick die Aeroworks-Steuerungseinstellungen öffnen.
2. Dort die Schaltfläche **Computer** verwenden.
3. Das eingebettete CC:Tweaked-Terminal wird geöffnet.

Wurde das Terminal aus einem konkreten Aeroworks-Modulbildschirm geöffnet, bleibt dessen `ConsoleSocket` erhalten. Dadurch kann die Steuerungsansicht anschließend wieder auf genau dasselbe Modul zurückwechseln, statt irgendein Pult oder einen veralteten Socket zu erraten.

Ein externer Computer verwendet stattdessen sein normales CC:Tweaked-Terminal. Jedes direkt oder über ein Wired Modem verbundene Pult erscheint dort als eigener lokaler `ControlDesk`-Adapter. Ein einzelner Adapter vertritt nicht automatisch den gesamten Multiblock.

## Steuerung bedienen

Montierte Lever, Joysticks, Throttle Quadrants und andere Aeroworks-Module behalten ihre normale Aeroworks-Interaktion:

1. Modul direkt ansehen.
2. Ohne Schraubenschlüssel normal rechtsklicken oder die konfigurierte Eingabe verwenden.
3. Bei **Kombiniert** die konfigurierte Taste halten und die vorgesehene Mausachse bewegen.

Die Details des kombinierten Modus stehen unter [[Kombinierte-Eingabe]].

## Ein eingebetteter Computer pro Netzwerk

Für die globale `peripherals`-API wird genau ein Computer-Steuerungspult benötigt. Alle anderen Mitglieder dürfen normale Aeroworks-Steuerungspulte sein.

Externe Computer sind davon unabhängig. Sie können einzelne Desk-Adapter direkt oder über ein Wired-Modem-Netz verwenden. Die Ein-Computer-Regel betrifft ausschließlich eingebettete Computer-Steuerungspulte im selben Pultnetz.

Wird in Survival versehentlich ein zweites Computer-Steuerungspult an dieselbe vollständig geladene Reihe gesetzt:

- das neu platzierte Pult wird zu einem normalen Aeroworks-Pult,
- seine Aeroworks-Module und Einstellungen bleiben erhalten,
- der zusätzliche normale oder erweiterte CC:Tweaked-Computer wird ausgeworfen,
- Computer-ID, Label, Terminalgröße und Speicherkapazität bleiben erhalten.

Im Creative-Modus wird die konfliktverursachende Platzierung abgebrochen, damit die Computer-ID nicht dupliziert wird.

## Ponder

Die Erklärung ist auf acht lokalisierte Storyboards verteilt:

### Computer-Steuerungspulte

1. Aufbau des Desk-Adapter-Netzwerks und beliebige Computerposition,
2. netzwerkweite Peripheral-Suche mit direkter Rückgabe eindeutiger Typen,
3. Diagnose von Mehrcomputer-, Lade- und Größenfehlern.

### Displays

4. Herstellung programmierbarer Displays,
5. Socketkompatibilität und Montage,
6. Text-, Zahlen- und Pixelprogrammierung über Desk-Handles.

### Radar

7. automatisches Routing zwischen verschiedenen Pulten,
8. Create:-Radars-Data-Link als optionaler Quellenadapter.

Die Szenen werden über die übliche Ponder-Taste **W** aus Inventar oder Rezeptbetrachter geöffnet. Radar-Szenen werden nur registriert, wenn Create: Radars geladen ist.
