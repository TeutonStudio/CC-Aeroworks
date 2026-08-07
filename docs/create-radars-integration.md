# Create: Radars integration

Die Integration ist optional und wird nur aktiv, wenn Create: Radars mit der Mod-ID `create_radar` geladen ist. Zielversion ist Create: Radars `0.4.9.4-1.21.1` auf Minecraft 1.21.1. Ohne die Fremdmod werden weder deren Klassen in normalen Signaturen referenziert noch optionale Radarrezepte geladen.

## Zweck der RadarDisplays

`cc_aeroworks:small_radar_display` und `cc_aeroworks:large_radar_display` ersetzen die sichtbare Monitorfläche am Aeroworks-Pult. Sie ersetzen **nicht** das Create:-Radars-Netzwerkmodell. Ein Pult mit RadarDisplay wird deshalb wie ein nativer Monitor über einen echten Data Link mit einem Network Controller verbunden.

- Das kleine RadarDisplay passt in kleine und große Sockets.
- Das große RadarDisplay passt nur in große Sockets.
- Die Radarfläche verwendet keine programmierbare Pixelmatrix.
- Jedes verknüpfte Pult ist ein eigener Create:-Radars-Monitorendpoint.

## Nativer Data-Link-Ablauf

CC-Aeroworks erweitert nur die Zielklassifizierung des vorhandenen `DataLinkBlockItem`. Platzierung, Reichweitenprüfung und Netzwerkmutation bleiben im Originalcode von Create: Radars.

1. Mit dem Create:-Radars-Data-Link den Network Controller anklicken. Das Item speichert nativ `SelectedFiltererPos`.
2. Danach den **Data Link auf das Pult** mit eingesetztem RadarDisplay anwenden.
3. Der optionale Mixin klassifiziert dieses Pult als denselben privaten Zieltyp `MONITOR`, den Create: Radars für `MonitorBlockEntity` verwendet.
4. Create: Radars prüft mit `NetworkData.canAttachMonitor(...)`, ob der Endpoint frei oder bereits derselben Filterer-Gruppe zugeordnet ist.
5. Create: Radars führt seine normale Reichweitenprüfung aus und platziert den **physischen Data-Link-Block** an der angeklickten Pultseite.
6. Der Originalpfad ruft `NetworkData.attachMonitor(serverLevel, group, deskPos)` auf und registriert die Data-Link-Position über `addDataLinkToGroup(...)`.

CC-Aeroworks ersetzt weder `DataLinkBlockItem.useOn(...)` noch die Blockplatzierung. Dadurch bleiben Erfolgsmeldungen, Fehlschläge, Linkstil, Reichweite und Itemverbrauch identisch mit dem nativen Monitorpfad.

## Endpoint-Synchronisierung

Ein verknüpftes Pult folgt dem Ablauf von `MonitorBlockEntity.tick()`:

- Synchronisierung alle **5 Ticks**.
- `NetworkData.get(level)` liefert die persistierte Create:-Radars-Netzwerkdatenbank.
- `getFiltererForEndpoint(dimension, deskPos)` bestimmt die zugeordnete Filtererposition.
- `getGroup(...)` liefert die Gruppe; `monitorEndpoints` muss die Pultposition enthalten.
- `radarPos` bestimmt den Radar der Gruppe.
- `detectionTag` wird mit `DetectionConfig.fromTag(...)` geladen.
- Jeder Radartrack wird mit `DetectionConfig.test(...)` genauso gefiltert wie beim nativen Monitor.
- `selectedTargetId` liefert die netzwerkweite Zielauswahl.
- `PhysicsHandler.getWorldVec(level, radarPos)` liefert das Radarzentrum einschließlich VS2-Welttransformation.

Der Adapter überträgt Reichweite, Betriebszustand, Radarzentrum, Zielauswahl und höchstens **256** gefilterte Tracks. Position, Geschwindigkeit und Spritekategorie werden in `RadarDisplaySnapshot` serialisiert. Geänderte Inhalte werden sofort im nächsten Fünf-Tick-Zyklus gesendet; unveränderte Inhalte erhalten spätestens nach 15 Ticks einen Heartbeat.

## Entfernen und Neuverbinden

Der physische Data-Link-Block bleibt Eigentümer der Verbindung. Beim Abbau ruft Create: Radars nativ `NetworkData.removeDataLinkAndCleanup(...)` und `onEndpointRemoved(...)` auf. Dadurch verschwinden Pultposition, Endpointindex und Data-Link-Zuordnung aus der Gruppe.

Beim nächsten Pulttick liefert `getFiltererForEndpoint(...)` keinen Filterer mehr. Das RadarDisplay wechselt auf `RADAR_NOT_LINKED` und synchronisiert diesen Zustand an den Client. CC-Aeroworks speichert keine Controllerposition und führt keine eigene Linkdatenbank.

Wird das RadarDisplay-Modul entfernt, bleibt die native Verbindung am Pult bestehen, solange der Data-Link-Block existiert. Das Pult sendet dann keine Radaroberfläche. Nach erneutem Einsetzen kann derselbe Endpoint wieder dargestellt werden. Zum vollständigen Trennen muss der Data-Link-Block entfernt werden.

## Keine Controller-Nachbarschaft

Ein Network Controller muss nicht neben dem Pult stehen. Direkte Nachbarschaft, Pult-Multiblockzustand und Anzahl angrenzender Controller sind für den Radarlink bedeutungslos. Die frühere automatische Controller-Erkennung und die besondere Rückseitenplatzierung wurden entfernt.

Die gültige Topologie ist ausschließlich:

`Network Controller → native Create:-Radars-Gruppe → physischer Data Link → Pultendpoint mit RadarDisplay`

Mehrere Pulte können jeweils mit einem eigenen Data-Link-Block derselben Gruppe als Monitorendpoints hinzugefügt werden. Ein einzelnes Pult kann über `NetworkData.canAttachMonitor(...)` nicht gleichzeitig mehreren Filterer-Gruppen gehören.

## Linkdiagnose

Der synchronisierte Snapshot verwendet folgende Zustände:

- `ACTIVE`: Endpoint, Gruppe und laufender Radar sind verfügbar.
- `RADAR_NOT_LINKED`: kein Endpoint, keine Gruppe oder kein Radar in der Gruppe.
- `RADAR_NOT_LOADED`: die Radarposition ist bekannt, ihr Chunk oder BlockEntity aber nicht geladen.
- `RADAR_NOT_RUNNING`: der Radar ist vorhanden, aber nicht aktiv.
- `INVALID_RANGE`: der Radar meldet keine positive Reichweite.
- `API_INCOMPATIBLE`: eine benötigte API-Oberfläche der unterstützten Create:-Radars-Version fehlt oder liefert einen falschen Typ.
- `STALE` und `DISCONNECTED`: defensive Clientzustände für alte oder fehlende Pakete.

Statuswechsel werden pro Pultendpoint einmalig protokolliert. Reflektive Fehler werden nach Signatur gedrosselt, statt alle 5 Ticks erneut ins Log geschrieben zu werden.

## Direkte Monitoroberfläche

RadarDisplays werden nicht über `RadarDisplayRaster` oder eine boolesche Pixelmatrix gerendert. Klassischer BlockEntity-Renderer und Flywheel-Visual verwenden dieselben flachen Oberflächenelemente.

Create: Radars stellt seine Monitor-PNGs nicht automatisch als gebackene Blockatlas-Sprites bereit. `assets/minecraft/atlases/blocks.json` ergänzt deshalb eine **Blockatlas**-Verzeichnisquelle für `textures/monitor_sprite` mit dem Präfix `monitor_sprite/`. Fremde PNGs werden nicht kopiert.

Verwendet werden:

- Hintergrundfüllung und Radarkreis,
- rotierender Sweep,
- Spieler-, Projektil-, Entitäts- und Contraption-Symbole,
- Zielmarkierung für `selectedTargetId`.

Die **Pultausrichtung** gehört zum Oberflächenzustand. Weltkoordinaten werden für Nord, Ost, Süd und West in lokale Bildschirmachsen projiziert. Hintergrund und Sweep verwenden den transparenten Renderpfad; Tracks und Zielmarkierung verwenden Cutout.

Der Flywheel-Pfad hält die Elemente in einem schlüsselbasierten **Instanzpool**. Bewegte Tracks ändern nur ihre Transformation. Neue oder entfernte Tracks erzeugen beziehungsweise löschen nur ihre eigenen Instanzen.

## ComputerControlDesk

Normales und Advanced ComputerControlDesk registrieren den nativen Aeroworks-`ConsoleVisual` ausdrücklich für ihren gemeinsamen eigenen BlockEntity-Typ. Dadurch bleiben Steuereinheiten mit Flywheel sichtbar. `ConsoleVisualMixin` ergänzt darauf die programmierbaren Display- und Radarflächen.

Der klassische `ComputerControlDeskRenderer` delegiert bevorzugt an `ConsoleRenderer`. Schlägt dessen Konstruktion wegen einer inkompatiblen Aeroworks-Version fehl, bleibt ein display-only Fallback für dynamische Oberflächen erhalten.

## Herstellung

- `cc_aeroworks:two_digit_display` plus `create_radar:monitor` ergibt `cc_aeroworks:small_radar_display`.
- `cc_aeroworks:three_digit_display` plus `create_radar:monitor` ergibt `cc_aeroworks:large_radar_display`.

Der Monitor ist Herstellungskomponente. In der Welt übernimmt das Pult den Monitorendpoint, benötigt dafür aber weiterhin den nativen Data-Link-Block.

## Ponder-Erklärungen

1. **Network und RadarDisplay verbinden** zeigt Filterer-Auswahl, Data Link und Pultendpoint.
2. **RadarDisplay als Monitorendpoint** erklärt `NetworkData`, Filterung, mehrere Endpoints und das native Trennen.

## Entwicklungsclient

`./gradlew runClient` löst Create: Radars, Create Big Cannons und Ritchie's Projectile Library als `localRuntime` auf. Offizielle lokale JARs dürfen nicht zusätzlich doppelt im allgemeinen Datei-Classpath liegen.

## Manuelle Prüfung

- Ohne Create: Radars starten: keine optionalen Mixinfehler.
- Network Controller mit dem Data Link auswählen: `SelectedFiltererPos` wird nativ gesetzt.
- Pult ohne RadarDisplay anklicken: Create: Radars lehnt das Ziel nativ ab.
- Pult mit kleinem oder großem RadarDisplay anklicken: der Data-Link-Block wird an der geklickten Fläche platziert und Erfolg gemeldet.
- Data Link außerhalb der konfigurierten Reichweite platzieren: native `too_far`-Ablehnung, keine Endpointzuordnung.
- Gruppe ohne Radar: Status `RADAR_NOT_LINKED`.
- Radar verbinden und einschalten: Status `ACTIVE`, Sweep sichtbar.
- Spieler, Entität, Projektil und Contraption erfassen: passende Symbole erscheinen.
- Detection-Filter am Network Controller ändern: RadarDisplay übernimmt dieselbe gefilterte Trackliste wie ein nativer Monitor.
- Ziel am Netzwerk auswählen: Zielmarkierung folgt `selectedTargetId`.
- Alle vier Pultausrichtungen prüfen.
- Data Link entfernen: Endpoint wird nativ aus `monitorEndpoints` entfernt und das Pult zeigt spätestens nach 5 Ticks `RADAR_NOT_LINKED`.
- Zwei RadarDisplay-Pulte mit zwei Data Links derselben Gruppe verbinden: beide zeigen dieselben gefilterten Daten.
- Dasselbe Pult an eine zweite Gruppe hängen: `canAttachMonitor` lehnt den zweiten Link ab.
- Radar-Chunk entladen: `RADAR_NOT_LOADED`.
- Radar stoppen: `RADAR_NOT_RUNNING`.
- Flywheel aktiviert und deaktiviert prüfen.
- ComputerControlDesk und Advanced ComputerControlDesk mit Steuereinheiten und RadarDisplay prüfen.
