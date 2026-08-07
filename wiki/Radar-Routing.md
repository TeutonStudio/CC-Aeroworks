# RadarDisplay und Create: Radars

Ein RadarDisplay macht genau das Aeroworks-Pult, in dem es montiert ist, zu einem nativen Create:-Radars-Monitorendpoint. Es gibt keine automatische Controller-Nachbarschaft, keine Weiterleitung über das Pultnetz und keine eigene CC-Aeroworks-Linkdatenbank.

## Unterstützte Pulte

- Aeroworks-Steuerungspult
- Advanced Aeroworks-Steuerungspult
- ComputerControlDesk
- Advanced ComputerControlDesk

Ein Pult ohne kleines oder großes RadarDisplay wird vom Data Link nicht als Monitor akzeptiert.

## Verbindung einrichten

1. Radar und Network Filterer nach den Regeln von Create: Radars verbinden.
2. Den Create:-Radars-Data-Link-Gegenstand auf dem Network Filterer verwenden.
3. Dasselbe Item auf einer freien Seite des Pults mit RadarDisplay verwenden.
4. Create: Radars prüft die Reichweite und platziert den physischen Data-Link-Block am Pult.
5. Das RadarDisplay zeigt anschließend die gefilterten Tracks dieser Netzwerkgruppe.

Der Network Filterer muss nicht direkt neben dem Pult stehen. Für jedes weitere RadarDisplay-Pult wird ein eigener Data-Link-Block benötigt. Mehrere Endpoints dürfen derselben Create:-Radars-Gruppe angehören, sofern der native Netzwerkvertrag dies erlaubt.

## Was Create: Radars weiterhin selbst erledigt

CC-Aeroworks ergänzt nur die Monitor-Zielklassifizierung. Der originale Data-Link-Ablauf bleibt verantwortlich für:

- `SelectedFiltererPos` im Item,
- maximale Linkreichweite,
- Konflikte mit anderen Gruppen,
- Blockplatzierung und Ausrichtung,
- Erfolg- und Fehlermeldungen,
- Itemzustand,
- Registrierung in `NetworkData`,
- Cleanup beim Abbau des Data-Link-Blocks.

## Angezeigte Daten

Das Pult liest alle fünf Ticks seine eigene Endpoint-Zuordnung aus `NetworkData`. Es übernimmt:

- Radarposition und Radarzentrum,
- Reichweite und Betriebszustand,
- Detection-Filter,
- ausgewähltes Ziel,
- höchstens 256 gefilterte Radartracks.

Die Filterung verwendet dieselbe native `DetectionConfig` wie ein Create:-Radars-Monitor. Spieler, Mobs, Tiere, Items, Projektile, Contraptions und Sable/VS2-Schiffe erscheinen daher nur, wenn der Filter der Netzwerkgruppe sie zulässt.

## Trennen

Der physische Data-Link-Block ist Teil der Verbindung. Beim Abbau entfernt Create: Radars den Monitorendpoint aus `NetworkData`. Das RadarDisplay leert seinen Snapshot im nächsten Fünf-Tick-Zyklus und zeigt das Trennungs-X.

Das X erscheint außerdem bei fehlendem Radar, ungeladenem Radar-Chunk, gestopptem Radar, ungültiger Reichweite oder einem erkannten API-Fehler. Bei einer aktiven Verbindung zeigt die Oberfläche Hintergrund, Kreis, Sweep, Kontaktpunkte und gegebenenfalls die Zielmarkierung.

## Mehrere Pulte und Computer

Pult-Multiblocks und eingebettete Computer bestimmen nicht die Radarroute. Ein Snapshot wird nie automatisch auf andere Pulte verteilt. Das normale und das Advanced ComputerControlDesk verwenden ihren gemeinsamen BlockEntity-Typ und behalten sowohl im klassischen Renderer als auch im Flywheel-`ConsoleVisual` ihre Aeroworks-Steuereinheiten.

## Diagnose

Bei Zustandswechseln enthält das Log eine Zeile mit Pultposition, Filtererposition, Radarposition, Linkstatus und Anzahl gefilterter Tracks. Ein aktiver Test mit Zielen sollte `status=ACTIVE` und `filteredTracks` größer als null zeigen.

## Optionale Abhängigkeit

Ohne Create: Radars startet CC-Aeroworks weiterhin. RadarDisplay-Rezepte und optionale Ponder-Inhalte werden nicht geladen; normale Displays, Computerpulte und Steuereinheiten bleiben verfügbar.
