# Bedienung

CC-Aeroworks unterscheidet Terminalzugriff, Modulbedienung und Einstellungen bewusst voneinander. Das verhindert, dass beim Öffnen des Computers gleichzeitig ein Hebel bewegt oder das Pult gedreht wird. Selten führt mehr Struktur tatsächlich zu weniger Chaos.

## Schnellübersicht

| Aktion | Eingabe | Voraussetzung |
|---|---|---|
| Modulübersicht und Computerslot öffnen | Schleichen + Rechtsklick | normale Steuereinheit, auch ohne Module |
| Computerterminal öffnen | Computerknopf in der Übersicht | Computer im gemeinsamen Slot |
| Steuerung bedienen | normaler Rechtsklick | montiertes Aeroworks-Modul direkt ansehen |
| Steuerungseinstellungen öffnen | Schraubenschlüssel + Rechtsklick | horizontale Seite eines Steuerungspults anklicken |
| Pult drehen | Schraubenschlüssel + Rechtsklick | Ober- oder Unterseite anklicken |
| Ponder-Erklärung öffnen | W halten | Maus über normale Steuereinheit, Computer, Display oder Radaritem |

## Computer öffnen

1. Mit leerer Haupthand schleichen und eine Steuereinheit rechtsklicken.
2. Einen normalen oder erweiterten CC:Tweaked-Computer im gemeinsamen Slot einsetzen. Spielerinventar und Shift-Klick sind verfügbar.
3. Den Computerknopf anklicken, um das Terminal zu öffnen.

Jede Steuereinheit der Reihe zeigt denselben Slot. Beim Abbau des Trägers wandert der Computer ins größere Reststück, bei Gleichstand nach links. Die letzte Steuereinheit droppt ihn zusätzlich zum Pult. Computer-ID und Dateien bleiben erhalten.

Ein externer Computer verwendet stattdessen sein normales CC:Tweaked-Terminal. Jedes direkt oder über ein Wired Modem verbundene Pult erscheint dort als eigener lokaler `ControlDesk`-Adapter. Ein einzelner Adapter vertritt nicht automatisch den gesamten Multiblock.

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

## Ein eingebetteter Computer pro Netzwerk

Für die globale `peripherals`-API wird ein Computer im gemeinsamen Slot benötigt. Alle Mitglieder bleiben normale Aeroworks-Steuereinheiten.

Externe Computer sind davon unabhängig und verwenden weiterhin lokale Desk-Adapter. Ein zweiter Computer lässt sich nicht per Shift-Klick einsetzen. Beim Zusammenführen belegter Reihen bleibt bevorzugt ein erweiterter Computer, danach der erste in Reihenfolge; weitere Computer werden mitsamt Daten ausgeworfen.

Alte Computerblöcke werden beim Laden, alte Computerpultitems beim Platzieren automatisch übernommen.

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
