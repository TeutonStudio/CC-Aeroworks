# RadarDisplay und Create: Radars

Ein RadarDisplay macht das Aeroworks-Pult, in dem es montiert ist, weiterhin zu einem nativen Create:-Radars-Monitorendpoint. Diese direkte Verbindung ist die lokale Standardquelle des Displays. Zusätzlich können RadarDisplays innerhalb desselben CC-Aeroworks-Pultmultiblocks den bereits synchronisierten Radar-Snapshot eines anderen RadarDisplay-Pults als Quelle auswählen.

## Unterstützte Pulte

- Aeroworks-Steuerungspult
- Advanced Aeroworks-Steuerungspult
- ComputerControlDesk
- Advanced ComputerControlDesk

Ein Pult ohne kleines oder großes RadarDisplay wird vom Data Link nicht als Monitor akzeptiert.

## Verbindung einrichten

1. Radar und Network Filterer nach den Regeln von Create: Radars verbinden.
2. Den Create:-Radars-Data-Link-Gegenstand auf dem Network Filterer verwenden.
3. Dasselbe Item auf einer freien Seite eines Pults mit RadarDisplay verwenden.
4. Create: Radars prüft die Reichweite und platziert den physischen Data-Link-Block am Pult.
5. Dieses Pult ist nun ein Radar-Ingress und synchronisiert die gefilterten Tracks seiner Netzwerkgruppe.

Der Network Filterer muss nicht direkt neben dem Pult stehen. Für jede unabhängige Create:-Radars-Gruppe wird weiterhin ein eigener nativer Monitorendpoint benötigt. Mehrere Pulte können als unterschiedliche Radar-Ingresses in demselben CC-Aeroworks-Multiblock stehen.

## Radarquelle eines Displays auswählen

Im Modulfenster eines RadarDisplays erscheint zusätzlich eine Quellenzeile. `local` bedeutet das bisherige Verhalten: Das Display verwendet den Snapshot des eigenen Pults. Weitere Einträge entsprechen RadarDisplay-Pulten im selben Multiblock.

Die Auswahl wird pro Display-Socket gespeichert. Zwei RadarDisplays am selben Pult können daher unterschiedliche Ingresses anzeigen. Wird die Auswahl entfernt oder die alte Welt enthält noch keine Binding-Daten, bleibt automatisch `local` aktiv.

Die Auswahl ist absichtlich kein Aeroworks-`ControlChannel`. Sie ist Display-Konfiguration und erscheint deshalb weder als Fahrzeugsteuerung noch als normaler Eingabekanal.

## Warum ein Radar-Ingress statt einer Kopie pro Display?

Jedes direkt verbundene RadarDisplay-Pult synchronisiert seinen nativen Create:-Radars-Snapshot weiterhin genau einmal. Andere Displays referenzieren diesen bereits vorhandenen Snapshot. Dadurch werden bis zu 256 Radartracks nicht erneut pro Anzeige serialisiert und übertragen.

Der Datenfluss lautet damit:

```text
Create: Radars Gruppe A -> Data Link -> Desk A -> Snapshot A
Create: Radars Gruppe B -> Data Link -> Desk B -> Snapshot B
                                      |
                         CC-Aeroworks Multiblock
                                      |
                     Display 1 -> Desk A / Snapshot A
                     Display 2 -> Desk B / Snapshot B
                     Display 3 -> Desk A / Snapshot A
```

## Was Create: Radars weiterhin selbst erledigt

CC-Aeroworks ergänzt nur die Monitor-Zielklassifizierung und die interne Display-Routing-Schicht. Der originale Data-Link-Ablauf bleibt verantwortlich für:

- `SelectedFiltererPos` im Item,
- maximale Linkreichweite,
- Konflikte mit anderen Gruppen,
- Blockplatzierung und Ausrichtung,
- Erfolg- und Fehlermeldungen,
- Itemzustand,
- Registrierung in `NetworkData`,
- Cleanup beim Abbau des Data-Link-Blocks.

CC-Aeroworks registriert ein Display nicht künstlich bei mehreren Create:-Radars-Gruppen. Ein Pult bleibt genau der native Endpoint, den Create: Radars kennt; die Mehrfachauswahl findet erst innerhalb des Pultmultiblocks statt.

## Angezeigte Daten

Jeder Radar-Ingress liest alle fünf Ticks seine eigene Endpoint-Zuordnung aus `NetworkData`. Er übernimmt:

- Radarposition und Radarzentrum,
- Reichweite und Betriebszustand,
- Detection-Filter,
- ausgewähltes Ziel,
- höchstens 256 gefilterte Radartracks.

Die Filterung verwendet dieselbe native `DetectionConfig` wie ein Create:-Radars-Monitor. Spieler, Mobs, Tiere, Items, Projektile, Contraptions und Sable/VS2-Schiffe erscheinen daher nur, wenn der Filter der Netzwerkgruppe sie zulässt.

## ComputerCraft-API

Ein `ControlDesk` stellt zusätzlich bereit:

```lua
local sources = desk.getRadarSources()
local binding = desk.getDisplayBinding("big")

desk.setRadarSource("big", sources[1].id)
desk.setRadarSource("big", "local")
desk.clearDisplayBinding("big")
```

`getRadarSources()` liefert pro Ingress eine stabile ID für die aktuelle Multiblockstruktur, Pultindex, Pult-ID, Position, Status und, falls bekannt, die Radarposition.

Große normale Desk Displays verwenden dieselbe Binding-Schicht für optionale Touch-Handler:

```lua
desk.setDisplayTouchScript("big", "/ui/main.lua")
```

Der Handlerpfad wird als zusätzliches letztes Argument der bestehenden `cc_aeroworks_console_display_input`- beziehungsweise `cc_aeroworks_desk_display_input`-Events geliefert. Das Beispiel `examples/cc/display-binding-router.lua` zeigt einen zentralen Router, der Handlerdateien cached und `tap`/`double_tap` verteilt. Die bisherigen Touch- und `monitor_touch`-Events bleiben kompatibel erhalten.

## Trennen

Der physische Data-Link-Block ist Teil der Verbindung. Beim Abbau entfernt Create: Radars den Monitorendpoint aus `NetworkData`. Der betreffende Ingress leert seinen Snapshot im nächsten Fünf-Tick-Zyklus. Displays, die diesen Ingress ausgewählt haben, zeigen damit ebenfalls den getrennten Zustand, ohne automatisch auf eine andere Quelle umzuschalten.

Das X erscheint außerdem bei fehlendem Radar, ungeladenem Radar-Chunk, gestopptem Radar, ungültiger Reichweite oder einem erkannten API-Fehler. Bei einer aktiven Verbindung zeigt die Oberfläche Hintergrund, Kreis, Sweep, Kontaktpunkte und gegebenenfalls die Zielmarkierung.

## Diagnose

Bei Zustandswechseln enthält das Log eine Zeile mit Pultposition, Filtererposition, Radarposition, Linkstatus und Anzahl gefilterter Tracks. Ein aktiver Test mit Zielen sollte `status=ACTIVE` und `filteredTracks` größer als null zeigen.

Bei Routing-Problemen zuerst `desk.getRadarSources()` und `desk.getDisplayBinding(socket)` prüfen. Eine gespeicherte Quelle wird nur akzeptiert, solange ihr Ingress weiterhin Teil desselben geladenen Pultmultiblocks ist.

## Optionale Abhängigkeit

Ohne Create: Radars startet CC-Aeroworks weiterhin. RadarDisplay-Rezepte und optionale Ponder-Inhalte werden nicht geladen; normale Displays, Computerpulte und Steuereinheiten bleiben verfügbar.
