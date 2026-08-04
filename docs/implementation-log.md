# Implementation Log

## Integrationsbasis

`v4` verbindet den aktuellen Computer-Steuerungspult-/Multiblockstand mit dem nach `v3` entwickelten reproduzierbaren Build, Dependency-Manifest, CI-Vertrag, Unit-Testfundament und Integrationsharness.

## Computer-Steuerungspult

Die beiden Computer-Steuerungspulte sind Aeroworks-`ConsoleDeskBlock`-Unterklassen. Ihre BlockEntity erweitert `ConsoleBlockEntity` und hostet per Komposition einen CC:Tweaked-`ServerComputer`.

Die Nutzung interner CC:Tweaked-Klassen ist auf die Computer-, Rezept- und Registrierungsgrenzen konzentriert. Unterstützt wird der deklarierte Bereich 1.119.x bis vor 1.121, wobei jede konkrete Version ein ausgeführtes Profil benötigt.

## Multiblock

`ConsoleMultiblockResolver` leitet eine gleich ausgerichtete Links-rechts-Reihe aus geladenen Blöcken ab. Ausrichtung und gegebenenfalls Deckenstatus müssen übereinstimmen. `ConsoleMultiblockManager` cached Snapshots und invalidiert sie bei Block-, Nachbar-, Chunk- und Leveländerungen.

Der Resolver lädt keine Chunks und begrenzt die Struktur auf 64 Mitglieder. Mehrere eingebettete Computer erzeugen einen Konflikt; es wird kein positionsabhängiger Primärcomputer erfunden.

## Externes Peripheral

`ControlDeskPeripheral` behält seine bisherigen Methoden für das physisch angeschlossene Pult. Zusätzlich löst es vom Ankerpult den vollständigen Multiblock auf und bietet deskbezogene Methoden für Sockets, Module, Eingaben und Displays.

Dadurch genügt ein direkter Anschluss oder ein Wired Modem an einem einzigen Mitglied. Desk-Auswahl erfolgt über einen 1-basierten Index oder die stabile Desk-ID.

## Direkte Lua-API

`CCComputerComponents.CONSOLE` wird nur dem eingebetteten `ServerComputer` hinzugefügt. Die globale API `aeroworks` und das Modul `cc_aeroworks.aeroworks` werden daher nicht auf gewöhnlichen Computern erzeugt.

Die direkte API benötigt kein Peripheral und stellt `getNetwork`, Desk-Auswahl, Socketlisten, Module, Eingaben und Displayoperationen bereit.

## Ereignisse

Der testbare `InputSnapshotDiff` vergleicht Modul-ID, Kanäle und Werte deterministisch. Er meldet:

- neue Kanäle,
- geänderte Werte,
- entfernte Kanäle mit `value = nil`,
- Modulwechsel auch bei numerisch gleichem Kanalwert.

Das bestehende Einzelpultevent bleibt kompatibel. Externe Multiblock- und eingebettete Computerereignisse enthalten zusätzlich Desk-ID und Netzwerkindex.

## Gemeinsamer Desk-Service

`AeroworksDeskService` enthält Socketvalidierung, Modulbeschreibung, Eingabeabfrage und sämtliche Displayoperationen. Einzelpult-, Multiblock- und direkte API delegieren an diese Schicht, statt drei reizend unterschiedliche Wahrheiten zu pflegen.

## Persistenz und Rendering

Aeroworks behält die Verantwortung für `controller_contents`. Eigene Data Components speichern Desk-ID und Einschaltzustand; CC:Tweakeds Komponenten speichern Computer-ID, Terminalgröße und Kapazität. Das Spezialrezept kopiert die Komponenten beider Eingaben.

Der Renderpfad verwendet Aeroworks' Konsolenmodell als Grundlage und ergänzt gemischte Verbindungen sowie Computer-Skins. Flywheel, Fallback und Sable bleiben verpflichtende Laufzeitprüfungen.

## Prüfstatus

Quellstruktur, Textressourcen und Integrationsbaum wurden statisch geprüft. Der vollständige Modbuild und die Laufzeitmatrix benötigen die nicht redistribuierbaren Ziel-JARs und bleiben bis zu einem geschützten oder lokalen Lauf ausdrücklich offen.
