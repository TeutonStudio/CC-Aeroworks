# Multiblock-Testplan

Diese Fälle ergänzen `manual-test-plan.md`. Jeder Fall erhält `PASS`, `FAIL`, `BLOCKED` oder `NOT RUN` und nennt Commit, Modversionen und Testumgebung.

## MB-BUILD-01: Statische Prüfung

1. Repositoryvertrag ausführen.
2. Abhängigkeitsmanifest validieren.
3. Mit den Baseline-JARs `clean test build` ausführen.
4. Dedicated-Server-Smoke-Test ausführen.

Erwartung: keine Kotlin-, Mixin-, Ressourcen- oder Clientklassentrennungsfehler.

## MB-TOPOLOGY-01: Einfache Reihe

1. Vier gleich ausgerichtete Steuerungspulte direkt nebeneinander platzieren.
2. Einen Computer oder ein Wired Modem nur am ersten Pult anschließen.
3. `getDesks()` aufrufen.

Erwartung:

- vier Einträge,
- deterministische Reihenfolge von lokal links nach rechts,
- Indizes `1..4`,
- stabile und eindeutige IDs,
- keine zusätzliche Verkabelung.

## MB-PERIPHERAL-01: Entfernte Eingabe

1. Eingabemodul am vierten Pult montieren.
2. Über das Peripheral am ersten Pult `getDeskInput(4, socket)` aufrufen.
3. Eingabe verändern.

Erwartung: Wert ist lesbar und `cc_aeroworks_multiblock_input` nennt Desk-ID, Index, Socket, Modul, Wert und Kanal.

## MB-PERIPHERAL-02: Entferntes Display

1. Display am vierten Pult montieren.
2. `setDeskDisplayText(4, "big", "123")` ausführen.
3. Pixelmethoden ausführen.

Erwartung: ausschließlich das gewählte entfernte Display ändert sich.

## MB-COMPAT-01: Alte Einzelpultmethoden

1. Mehrere Pulte verbinden.
2. `getModules()`, `getInput()` und `setDisplayText()` ohne Deskparameter verwenden.

Erwartung: Die Methoden betreffen weiterhin nur das physisch angeschlossene Pult.

## MB-DIRECT-01: Eingebettete API

1. Genau ein Computer-Steuerungspult in eine Reihe normaler Pulte setzen.
2. Dessen Terminal öffnen.
3. Ohne jeden Peripheral-Aufruf `aeroworks.getDesks()` verwenden.
4. `getSocketCount(desk)`, `getSockets(desk)` und Displaymethoden für entfernte Mitglieder aufrufen.

Erwartung: vollständiger Zugriff ohne Modem, `peripheral.find`, `peripheral.wrap` oder `peripheral.call`.

## MB-DIRECT-02: Terminalweiterleitung

1. Terminal von jedem Mitglied der Reihe mit Schleichen und leerer Haupthand öffnen.
2. Vom Computerblock weg zum entfernten Mitglied wechseln.
3. Menüreichweite prüfen.

Erwartung: dasselbe Terminal öffnet sich; das Menü bleibt nur offen, solange ein Mitglied des gültigen Multiblocks erreichbar ist.

## MB-CONFLICT-01: Zwei Computer-Steuerungspulte

1. Zwei Computer-Steuerungspulte verbinden.
2. Direkte API und Terminalweiterleitung von normalen Pulten prüfen.
3. Beide Computerblöcke direkt öffnen.
4. Externes Peripheral prüfen.

Erwartung:

- kein zufällig gewählter Besitzer,
- direkte Multiblock-API meldet Konflikt,
- beide Dateisysteme bleiben getrennt erreichbar,
- externe deskbezogene Peripheral-Methoden bleiben verfügbar.

## MB-SPLIT-01: Trennen und Verbinden

1. Eine Reihe während des Betriebs in zwei Teile trennen.
2. `cc_aeroworks_multiblock_changed` beobachten.
3. Reihe wieder verbinden.

Erwartung: Mitgliederlisten und Ereignisse aktualisieren sich ohne Neustart oder veraltete Deskzuordnung.

## MB-REMOVE-01: Kanal und Modul entfernen

1. Eingabewert erfassen.
2. Kanal beziehungsweise Modul entfernen.

Erwartung: Einzelpult- und Multiblockereignis werden mit `value = nil` erzeugt; kein alter Wert bleibt im Snapshot.

## MB-CHUNK-01: Chunkgrenze

1. Reihe über eine Chunkgrenze bauen.
2. Randchunk entladen.
3. API aufrufen und anschließend Chunk wieder laden.

Erwartung: Teilzustand wird als Fehler gemeldet, es werden keine Chunks nachgeladen und nach dem Laden wird die vollständige Struktur neu erkannt.

## MB-LIMIT-01: Größenlimit

1. 64 kompatible Pulte verbinden.
2. 65. Pult ergänzen.

Erwartung: 64 Mitglieder funktionieren; der übergroße Multiblock wird eindeutig abgelehnt und verursacht keine unbegrenzte Suche.

## MB-PERSIST-01: Crafting und Abbau

1. Pult mit Modulen und Displayzustand mit einem bereits verwendeten Computer craften.
2. Block platzieren, verwenden, abbauen und erneut platzieren.

Erwartung: Module, Displays, Computer-ID, Label, Speicher- und Terminaldaten bleiben erhalten; kein doppelter Drop.

## MB-RENDER-01: Modelle

Prüfen:

- normales Computer-Steuerungspult,
- Advanced-Variante,
- gemischte Reihen,
- Inventarmodelle,
- Flywheel aktiv,
- Fallback-Renderer,
- Innen- und Außenverbindungen.

Erwartung: keine fehlenden Modelle, Texturen oder doppelten Pultflächen.

## MB-SABLE-01: Statisches und bewegtes Schiff

1. Multiblock auf Sable montieren.
2. statisch und während realer Bewegung testen.
3. Terminal, direkte API, externes Peripheral, Eingaben und Displays prüfen.

Erwartung: Position, Computer-ID, Netzwerkmitgliedschaft, Rendering und Ereignisse bleiben stabil.

## MB-VERSIONS-01: CC:Tweaked

Die Kernfälle mindestens mit CC:Tweaked 1.119.0 und 1.120.0 ausführen. Eine Version wird nur als unterstützt beworben, wenn ihr Profil vollständig `PASS` ist.
