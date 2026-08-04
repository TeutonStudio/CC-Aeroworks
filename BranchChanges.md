# Branch-Änderungen: Computer-Steuerungspult und Steuerungspult-Multiblock

## Stand dieses Commits

Dieser Commit implementiert die im vorherigen Plan festgelegten Kernfähigkeiten für Computer-Steuerungspulte. Die Implementierung ist auf Minecraft 1.21.1, Aeroworks 1.3.0 und CC:Tweaked 1.119.x bis vor 1.121 ausgerichtet.

Die lokalen proprietären beziehungsweise nicht redistribuierbaren Mod-JARs stehen in der Ausführungsumgebung dieses Commits nicht zur Verfügung. Deshalb wurden Quellstruktur, Signaturen und Ressourcen statisch geprüft; Client-, Dedicated-Server- und Ingame-Tests bleiben entsprechend dem Testplan auszuführen.

## Neue Blöcke und Items

Registriert wurden:

- `cc_aeroworks:computer_control_desk`
- `cc_aeroworks:advanced_computer_control_desk`

Beide Blöcke erben das Aeroworks-Steuerungspultverhalten und verwenden denselben Aeroworks-Desk-Typ. Sie können daher dieselben Module, Displays und Socketpositionen wie ein normales Steuerungspult verwenden.

Der funktionale Unterschied ist ausschließlich die CC:Tweaked-Computerfamilie:

- Computer-Steuerungspult: `ComputerFamily.NORMAL`
- Erweitertes Computer-Steuerungspult: `ComputerFamily.ADVANCED`

Die Advanced-Variante erhält damit das farbfähige Advanced-Terminal. Multiblock-, Modul-, Display- und Lua-Verhalten sind ansonsten identisch.

## Herstellung

Ein neues spezielles Crafting-Rezept akzeptiert genau:

- ein Aeroworks-Steuerungspult,
- einen normalen oder erweiterten CC:Tweaked-Computer.

Das Ergebnis richtet sich nach der Computerart. Das Rezept übernimmt zuerst sämtliche Komponenten des Steuerungspults und danach sämtliche Komponenten des Computers. Dadurch bleiben insbesondere erhalten:

- Aeroworks-`controller_contents` mit montierten Modulen,
- Displaytexte und Pixelzustände,
- Computer-ID und zugehöriges Dateisystem,
- Computerbeschriftung,
- Speicherkapazität,
- Terminalgröße.

Andere Zutaten, doppelte Pulte oder doppelte Computer werden abgelehnt.

## BlockEntity und Computerlebenszyklus

`ComputerControlDeskBlockEntity` erweitert Aeroworks' `ConsoleBlockEntity` und hostet einen vollständigen CC:Tweaked-`ServerComputer`.

Persistiert werden:

- stabile Desk-UUID,
- Computer-ID,
- Beschriftung,
- Ein-/Aus-Zustand,
- Speicherkapazität,
- Terminalbreite und -höhe,
- sämtliche durch Aeroworks geerbten Modul- und Controllerdaten.

Beim Laden wird ein eingeschalteter Computer erneut gestartet. Beim Chunk-Unload oder Entfernen wird die laufende Serverinstanz geschlossen; Computer-ID und Dateisystem bleiben erhalten. Auf einem bewegten Sublevel wird die Position der laufenden Computerinstanz aktualisiert.

Beim Abbau wird ein ItemStack der korrekten normalen oder Advanced-Variante erzeugt und mit den kombinierten Aeroworks- und Computerkomponenten versehen. Die eigenen Loot Tables sind leer, damit dieser komponentenerhaltende Drop nicht verdoppelt wird.

## Steuerungspult-Multiblock

Ein Multiblock wird aus dem aktuellen Weltzustand abgeleitet und nicht als fehleranfällige Positionsliste gespeichert.

Mitglieder müssen:

- direkt links oder rechts aneinander angrenzen,
- dieselbe Höhe besitzen,
- dieselbe horizontale Ausrichtung besitzen,
- ein normales Aeroworks-Steuerungspult oder eine der beiden Computervarianten sein,
- geladen sein.

Die Reihenfolge wird lokal von links nach rechts bestimmt. Das Limit beträgt 64 Pulte. Der Resolver lädt keine Chunks nach.

Mögliche Zustände:

- `NONE`: kein Computer im Multiblock,
- `ACTIVE`: genau ein Computer,
- `CONFLICT`: mehrere Computer,
- `TOO_LARGE`: mehr als 64 Mitglieder,
- `PARTIALLY_LOADED`: ein benötigter Bereich oder eine BlockEntity ist nicht geladen.

Snapshots werden zwischengespeichert. Platzieren, Abbauen, Chunk-Laden und Chunk-Unload invalidieren den Cache; es findet keine vollständige Multiblocksuche in jedem Server-Tick statt.

## Terminalzugriff

Die konfliktfreie Interaktion lautet für alle Multiblockmitglieder:

> Schleichen und mit leerer Haupthand rechtsklicken.

Dadurch bleiben Aeroworks-Modulmontage, Modulmenüs, Wrench-Aktionen und normale Interaktionen unangetastet.

Bei genau einem Computer öffnet jedes Pult dasselbe Terminal. Die Menüprüfung akzeptiert den Spieler, solange er ein geladenes Mitglied desselben gültigen Multiblocks erreicht.

Bei mehreren Computern öffnet ein normales Pult kein zufällig ausgewähltes Terminal. Stattdessen wird eine lokalisierte Konfliktmeldung angezeigt. Ein Computer-Steuerungspult kann direkt weiterhin sein eigenes Terminal öffnen, damit Dateien erreichbar bleiben und der Konflikt durch Umbau behoben werden kann.

Für übergroße und teilweise geladene Multiblocks existieren eigene lokalisierte Meldungen.

## Eingebettete Lua-API

Nur der eingebettete Computer erhält über eine eigene `ComputerComponent` die globale API `aeroworks` und das Modul `cc_aeroworks.aeroworks`.

Gewöhnliche Computer, Turtles und Pocket Computer erhalten diese API nicht.

Methoden:

- `getDesks()`
- `getDesk(desk)`
- `getModules(desk)`
- `getModule(desk, socket)`
- `getInput(desk, socket)`
- `getInputs(desk)`
- `getDisplays(desk)`
- `getDisplay(desk, socket)`
- `setDisplayText(desk, socket, text)`
- `setDisplayNumber(desk, socket, value, zeroPad?)`
- `clearDisplay(desk, socket)`
- `clearDisplays(desk)`
- `getDisplaySize(desk, socket)`
- `getDisplayPixel(desk, socket, x, y)`
- `setDisplayPixel(desk, socket, x, y, enabled)`
- `setDisplayPixels(desk, socket, rows)`
- `clearDisplayPixels(desk, socket)`

Ein Pult kann durch seinen 1-basierten Netzwerkindex oder seine stabile UUID ausgewählt werden. Socketparameter akzeptieren weiterhin `left`, `right`, `big` sowie `0`, `1`, `2`.

Desk-Beschreibungen enthalten ID, Index, Blockposition, Variante, Ausrichtung und Besitzerstatus.

## Ereignisse

Die bestehende Einzelperipheral-Veranstaltung bleibt erhalten:

- `cc_aeroworks_desk_input`

Der eingebettete Computer ergänzt:

- `cc_aeroworks_console_input`
- `cc_aeroworks_console_changed`

`cc_aeroworks_console_input` enthält Desk-ID, Netzwerkindex, Socketindex, Socketname, Modul-ID, Wert und Kanal.

Der Vergleich erkennt nicht nur geänderte Werte, sondern auch entfernte Kanäle und Module. Für eine Entfernung wird der neue Wert als `nil` übergeben. Der erste Snapshot erzeugt weiterhin kein Eingabeereignis.

## Gemeinsamer Desk-Service

Die komplette Socket-, Modul-, Display- und Pixelaufbereitung wurde aus `ControlDeskPeripheral` in `AeroworksDeskService` verschoben.

Davon profitieren:

- das bestehende Einzelpult-Peripheral,
- die neue Multiblock-Lua-API,
- der gemeinsame Eingabesnapshot.

Damit existieren keine getrennten Validierungsregeln für Einzelpult und Multiblock.

## CC-Seiten, Redstone und Peripherals

Der eingebettete Computer aktualisiert seine sechs lokalen CC-Seiten relativ zur Pultausrichtung.

Unterstützt werden:

- normale Redstoneeingänge und -ausgänge,
- gebündelte Redstoneeingänge und -ausgänge,
- angrenzende fremde Peripherals.

Direkt angrenzende Steuerungspultmitglieder werden absichtlich nicht zusätzlich als linkes beziehungsweise rechtes CC-Seitenperipheral eingebunden. Der Zugriff auf sie erfolgt ausschließlich über die Multiblock-API.

## Externe Peripheral- und Display-Link-Kompatibilität

Computer-Steuerungspulte bleiben selbst `cc_aeroworks_control_desk`-Peripherals für externe Computer.

`ControlDeskPeripheralRegistry` und `CCDisplayTargets` registrieren jetzt sowohl:

- Aeroworks' `aeroworks:console`,
- den neuen Computer-Steuerungspult-BlockEntityType.

Die Auflösung von `aeroworks:console` wurde in `AeroworksTypes` zentralisiert. Der frühere ungesicherte Cast im Display-Target-Pfad wurde entfernt.

## Rendering

Für den neuen BlockEntityType wird ein eigener Renderer registriert, der Aeroworks' vorhandenen `ConsoleRenderer` delegiert. Dadurch bleibt die Modul- und Displaydarstellung im klassischen BlockEntity-Rendererpfad erhalten.

Die Renderer-Erzeugung liegt hinter einer kleinen reflektiven Compat-Grenze, da Aeroworks für den Renderer keinen öffentlichen, versionsstabilen Fabrikvertrag bereitstellt. Ein Fehlschlag wird deutlich protokolliert.

Eine native Flywheel-Visual-Registrierung für den neuen BlockEntityType ist in diesem Commit nicht behauptet. Der Fallback-Renderer ist der sichere Pfad und muss zusammen mit Sable und aktiviertem Flywheel manuell geprüft werden.

## Ressourcen und Oberfläche

Ergänzt wurden:

- Blockstates für beide Varianten,
- Blockmodelle auf Basis des Aeroworks-Control-Desk-Modells,
- Itemmodelle,
- komponentenerhaltende leere Loot Tables,
- das spezielle Crafting-Rezept,
- deutsche und englische Übersetzungen,
- Konflikt-, Größen- und Ladehinweise,
- Creative-Tab-Einträge,
- aktualisierte Ingame-Handbuchtexte.

## Korrigierte bestehende Probleme

Zusätzlich zur neuen Funktion wurden folgende bestehende Schwächen korrigiert:

1. `aeroworks:console` wird nicht mehr an mehreren Stellen getrennt und unterschiedlich sicher aufgelöst.
2. `CCDisplayTargets` verwendet keinen ungesicherten Registry-Cast mehr.
3. Einzelperipheral und Multiblock verwenden dieselbe Desk-Implementierung.
4. Entfernte Eingabekanäle beziehungsweise Module hinterlassen keinen dauerhaft veralteten Zustand.
5. Die Creative-Tab-Anordnung ergänzt die beiden Blockitems genau einmal.
6. Die interne CC:Tweaked-Abhängigkeit ist auf die `computer`- und Compat-Pakete begrenzt und dokumentiert.

## Noch auszuführende Laufzeitprüfung

Vor Freigabe müssen mit den lokalen Ziel-JARs ausgeführt werden:

- `./gradlew test`
- `./gradlew build`
- `./gradlew runClient`
- Dedicated-Server-Start
- normale und Advanced-Variante
- Crafting mit neuer sowie bestehender Computer-ID
- Modul- und Displaydaten beim Crafting und Abbau
- Terminalöffnung von jedem Multiblockmitglied
- Konflikt, Größenlimit und Chunkgrenze
- CC:Tweaked 1.119.0 und 1.120.0
- Flywheel an und aus
- Sable-Schiff
- mit und ohne Drive By Wire
