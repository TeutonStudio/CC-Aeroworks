# Multiblock-Testplan

Diese Fälle ergänzen `manual-test-plan.md`. Jeder Fall erhält `PASS`, `FAIL`, `BLOCKED` oder `NOT RUN` und nennt Commit, Modversionen und Testumgebung.

## MB-BUILD-01: Statische Prüfung und Vollbuild

1. `python3 tools/verify-repository.py` ausführen.
2. `python3 tools/verify-guide.py` ausführen.
3. `python3 tools/verify-peripheral-network.py` ausführen.
4. Radar- und Rezeptverifikatoren ausführen.
5. Mit den Baseline-JARs `clean test build` ausführen.
6. Dedicated-Server-Smoke-Test ausführen.

Erwartung: keine Kotlin-, Mixin-, Ressourcen-, Übersetzungs- oder Clientklassentrennungsfehler.

## MB-TOPOLOGY-01: Einfache Reihe

1. Vier gleich ausgerichtete Steuerungspulte direkt nebeneinander platzieren.
2. Genau ein Computer-Steuerungspult nacheinander an erster, mittlerer und letzter Position verwenden.
3. `peripherals.find("ControlDesk")` aufrufen.

Erwartung:

- vier Einträge mit kanonischen `x,y,z`-Schlüsseln,
- deterministische Indizes `1..4`,
- stabile und eindeutige Desk-IDs,
- identische Ergebnisse für jede Computerposition,
- keine zusätzliche Verkabelung.

## MB-TOPOLOGY-02: Native Zugsteuerung als Brückensegment

1. Create-Zugsteuerung durch Klick auf die linke oder rechte Seite eines Boden-Control-Desks platzieren.
2. Weitere Zugsteuerungen seitlich an die integrierte Zugsteuerung setzen und am anderen Ende ein gleich ausgerichtetes Pult ergänzen.
3. Den Aufbau jeweils mit Control Desk, Copycat Control Desk, Control Stand und Copycat Control Stand wiederholen.
4. Eine Zugsteuerung frei, an einer Vorder-/Rückseite und an einem Deckenpult platzieren.
5. Alle passenden Ankerpulte einer zusammenhängenden Zugsteuerungsgruppe abbauen.

Erwartung:

- seitliche Platzierung erbt Ausrichtung, Desk-/Stand-Geometrie und Computer-Skin;
- passende Zugsteuerungen verbinden beide Pultabschnitte, erscheinen im gemeinsamen Preview und in den Außenkonturen;
- freie, vordere, hintere und Deckenplatzierung bleibt vollständig nativ;
- ohne passendes Ankerpult fällt die gesamte Gruppe auf die native Create-Darstellung zurück;
- Create erkennt und bedient die Zugsteuerung nach Fahrzeugmontage weiterhin nativ.

## MB-TOPOLOGY-03: Zugsteuerung bleibt transparenter Knoten

1. Ein Netz aus zwei Pulten mit einer integrierten Zugsteuerung dazwischen und genau einem eingebetteten Computer bauen.
2. `peripherals.getNetwork()`, `peripherals.getDesks()`, `channels` und `controls` prüfen.
3. Modem beziehungsweise anderes CC:Tweaked-Peripheral, Create:-Radars-Network-Filterer und Radar-Data-Link nacheinander direkt an die Zugsteuerung setzen.

Erwartung:

- drei physische Segmente, aber genau zwei adressierbare Desks;
- keine Desk-ID, kein `ControlDesk`-Handle und keine Modul-/Kanaleinträge für die Zugsteuerung;
- alle direkt angrenzenden Geräte werden mit ihrer echten Position und Seite als Multiblockquellen gefunden;
- Radarcontroller und Data-Link-Endpunkt funktionieren ebenso wie bei direkter Montage am Pult.

## MB-LOCAL-01: Lokale Desk-Adapter

1. Mehrere Pulte über Wired Modems an einen externen Computer anschließen.
2. Alle Namen und Typen ausgeben.
3. Jeden `ControlDesk` einzeln wrappen.
4. Module, Eingaben und Displays aufrufen.

Erwartung:

- jedes Pult ist ein eigenes Peripheral,
- Primärtyp `ControlDesk`, zusätzliche Aliasse vorhanden,
- lokale Methoden betreffen ausschließlich das gewrappte Pult,
- keine alten netzwerkweiten `getDesk...`-Methoden.

## MB-GRAPH-01: Geräte an entfernten Pulten

1. Ein EnderModem am ersten, einen Speaker am zweiten und ein weiteres Peripheral am vierten Pult platzieren.
2. Vom eingebetteten Computer `peripherals.getTypes()` und `peripherals.findAll()` verwenden.
3. Geräte über Typ, Weltposition und Desk-Seite wrappen.

Erwartung:

- alle geladenen Geräte werden gefunden,
- jedes Gerät bleibt dem richtigen Desk und der richtigen Seite zugeordnet,
- andere Pulte werden nicht als externe Peripherals indexiert,
- keine Chunks werden nachgeladen.

## MB-GRAPH-02: Eindeutige Gattung

1. Genau ein EnderModem im gesamten Pultnetz platzieren.
2. `peripherals.find("endermodem")` aufrufen und eine echte Modemmethode verwenden.
3. Ein zweites EnderModem hinzufügen.
4. `find` und `findAll` erneut aufrufen.

Erwartung:

- kein Treffer ergibt `nil`,
- genau ein Treffer ergibt direkt das Methoden-Handle,
- mehrere Treffer ergeben eine Tabelle nach Desk-Adresse und Seite,
- `findAll` ergibt immer eine Tabelle,
- `EnderModem`, `ender_modem`, `endermodem` und namespaced Typ sind äquivalent.

## MB-GRAPH-03: Peripheral-Lifecycle

1. Ein Gerät mit Mount und Ereignissen an ein Pult setzen.
2. Methoden aufrufen und Mount prüfen.
3. Gerät abbauen, Chunk entladen und Netzwerk trennen.
4. Gerät erneut verbinden.

Erwartung:

- `attach` und `detach` werden korrekt aufgerufen,
- Mounts werden beim Detach entfernt,
- verspätete Lua-Aufrufe werden durch einen ungültigen Guard verworfen,
- `peripheral`, `peripheral_detach` und die CC-Aeroworks-Ereignisse verwenden dieselbe Adresse,
- Reconnect erzeugt keine doppelten Bindings.

## MB-GRAPH-04: Aktualisierung

1. Gerät platzieren und entfernen.
2. Fünf Ticks abwarten.
3. Eine reine Capability-Änderung ohne Blockwechsel erzeugen.
4. `peripherals.refresh()` ausführen.

Erwartung:

- normale Änderungen werden spätestens nach fünf Ticks erkannt,
- `refresh()` erzwingt eine sofortige Neuauswertung,
- `getNetwork().revision`, Desk- und Peripheral-Anzahl sind konsistent,
- keine veralteten Handles bleiben funktionsfähig.

## MB-DISPLAY-01: Entferntes Display über Desk-Handle

1. Display am vierten Pult montieren.
2. Dieses Pult aus `peripherals.find("ControlDesk")` über seinen Positionsschlüssel wählen.
3. Text, Zahl und Pixelraster schreiben.

Erwartung: ausschließlich das Display des gewählten Desk-Handles ändert sich. Die Rastergröße wird zuvor über `getDisplaySize` gelesen.

## MB-COMPUTER-01: Eingebettete API

1. Genau ein Computer-Steuerungspult in eine Reihe normaler Pulte setzen.
2. Dessen Terminal öffnen.
3. Ohne Modem `peripherals.getDesks()`, `find`, `findAll` und `wrap` verwenden.
4. Computerposition innerhalb der Reihe ändern.

Erwartung: vollständiger Graphzugriff ohne `peripheral.find` oder Wired Modem; die Computerposition verändert das Ergebnis nicht.

## MB-COMPUTER-02: Terminalweiterleitung

1. Terminal von jedem Mitglied der Reihe mit Schleichen und leerer Haupthand öffnen.
2. Vom Computerblock weg zum entfernten Mitglied wechseln.
3. Menüreichweite prüfen.

Erwartung: dasselbe Terminal öffnet sich; das Menü bleibt nur offen, solange ein Mitglied des gültigen Netzwerks erreichbar ist.

## MB-CONFLICT-01: Zwei Computer-Steuerungspulte

1. Zwei Computer-Steuerungspulte per Strukturwerkzeug oder Altwelt verbinden.
2. Globale `peripherals`-API und Terminalweiterleitung prüfen.
3. Beide Computerblöcke direkt öffnen.
4. Lokale `ControlDesk`-Adapter über externe Computer prüfen.

Erwartung:

- kein zufällig gewählter Besitzer,
- globale Graph-API meldet Konflikt,
- beide Dateisysteme bleiben getrennt direkt erreichbar,
- lokale Desk-Adapter bleiben für geladene Einzelpulte verfügbar,
- Radar-Routing bleibt deaktiviert.

## MB-SPLIT-01: Trennen und Verbinden

1. Eine Reihe während des Betriebs in zwei Teile trennen.
2. Standard- und CC-Aeroworks-Peripheral-Ereignisse beobachten.
3. Reihe wieder verbinden.

Erwartung: Desk- und Gerätelisten aktualisieren sich ohne Neustart; entfernte Geräte werden detached, zurückkehrende Geräte genau einmal attached.

## MB-CHUNK-01: Chunkgrenze

1. Reihe über eine Chunkgrenze bauen.
2. Randchunk entladen.
3. `peripherals.getNetwork()` aufrufen und anschließend Chunk wieder laden.

Erwartung: Teilzustand wird als Fehler gemeldet, es werden keine Chunks nachgeladen und nach dem Laden wird die vollständige Struktur neu erkannt.

## MB-LIMIT-01: Größenlimit

1. Insgesamt 64 kompatible Pulte und integrierte Zugsteuerungssegmente verbinden.
2. Ein 65. physisches Segment ergänzen.

Erwartung: 64 physische Segmente funktionieren; der übergroße Verbund wird eindeutig abgelehnt und verursacht keine unbegrenzte Suche oder Radarroute. Desk-Zähler und APIs enthalten weiterhin ausschließlich echte Pulte.

## MB-RADAR-01: Eindeutige automatische Route

1. Data Link an einem Pult platzieren.
2. Genau ein erweitertes Display an einem anderen Pult montieren und dort eine Radarquelle als Pseudo-Skript auswählen.
3. Computer-Steuerungspult links, mittig und rechts testen.
4. Radar-Monitor verbinden und Tracks erzeugen.

Erwartung:

- Quelle und Ziel werden über das aktive Pultnetz verbunden,
- Computerposition ist irrelevant,
- alle fünf Ticks werden frische Snapshots übertragen,
- nach 20 Ticks ohne Quelle zeigt das Display `X`,
- kein Lua-Programm erforderlich.

## MB-RADAR-02: Mehrdeutige oder ungültige Route

1. Kein Radarziel, danach zwei Radarziele verwenden.
2. Mehrere Computer, Teilbeladung und Übergröße testen.
3. Data-Link-Quelle entfernen.

Erwartung:

- kein Ziel erzeugt eine fehlende Route,
- mehrere Ziele erzeugen eine Mehrdeutigkeitsmeldung,
- ungültige Topologien verwenden lokalisierte Netzwerkdiagnosen,
- es wird nie zufällig ein Ziel ausgewählt.

## MB-PERSIST-01: Crafting und Abbau

1. Pult mit Modulen und Displayzustand mit einem bereits verwendeten Computer craften.
2. Block platzieren, verwenden, abbauen und erneut platzieren.

Erwartung: Module, Displays, Desk-ID, Computer-ID, Label, Speicher- und Terminaldaten bleiben erhalten; kein doppelter Drop.

## MB-RENDER-01: Block- und Multiblock-Overlay

1. Normales und Advanced-Computer-Steuerungspult einzeln platzieren.
2. Beide Varianten mit normalen Aeroworks-Pulten verbinden.
3. Innen- und Außenverbindungen, Decke/Boden und alle vier Ausrichtungen prüfen.
4. Flywheel- und Fallback-Renderer prüfen.

Erwartung:

- Geometrie und Grundtextur entsprechen dem normalen Aeroworks Control Desk,
- die jeweilige Computertextur wird transparent darübergelegt,
- alle Mitglieder eines Computer-Netzwerks erhalten das passende Overlay,
- kein Z-Flimmern, keine doppelte Grundtextur und keine fehlenden Flächen.

## MB-SABLE-01: Statisches und bewegtes Schiff

1. Pultnetz auf Sable montieren.
2. Statisch, während Translation, während Rotation und während kombinierter Bewegung testen.
3. Ein Aeroworks-Steuermodul zunächst auf einem normalen Control Desk, danach auf normalem und Advanced-Computer-ControlDesk in der Hand halten und über alle freien Sockel bewegen.
4. Ghost-Vorschau und Platzierungs-/Entfernungs-Outlines mit den tatsächlichen Sockeln vergleichen und das Modul montieren.
5. Terminal, Desk-Adressen, globale Suche, Eingaben, Displays und Radar prüfen.

Erwartung:

- Ghost-Vorschau und Outlines liegen bei statischem und bewegtem SubLevel ohne Versatz und ohne doppelte Rotation auf dem tatsächlich gewählten Sockel,
- nach der Montage befindet sich das Modul genau dort, wo die Vorschau es angezeigt hat,
- normales Aeroworks-Pult sowie normale und Advanced-Computer-Pulte verhalten sich identisch,
- Desk-ID, Computer-ID, übriges Rendering und Daten bleiben stabil,
- Positionsschlüssel dürfen sich entsprechend der tatsächlichen Weltposition ändern und müssen danach neu aufgelöst werden.

## MB-VERSIONS-01: CC:Tweaked

Die Kernfälle mindestens mit CC:Tweaked 1.119.0 und 1.120.2 ausführen. Eine Version wird nur als unterstützt beworben, wenn ihr Profil vollständig `PASS` ist.
