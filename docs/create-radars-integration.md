# Create: Radars integration

Die Integration ist optional und wird nur aktiv, wenn Create: Radars mit der Mod-ID `create_radar` geladen ist. Unterstützt und im Entwicklungsmanifest fest eingetragen ist Create: Radars `0.4.9.4-1.21.1` für Minecraft 1.21.1.

Ohne Create: Radars bleiben CC-Aeroworks und alle normalen Pultfunktionen startfähig. RadarDisplay-Rezepte und optionale Inhalte werden über `neoforge:mod_loaded` ausgeblendet. Der optionale Data-Link-Mixin ist `@Pseudo`, verwendet ein Klassennamenziel und enthält keine Create:-Radars-Typen in seiner Handler-Signatur. Auch der clientseitige Renderer-Bridge enthält Create:-Radars-Klassen ausschließlich als zur Laufzeit aufgelöste Klassennamen.

## Zweck der RadarDisplays

`cc_aeroworks:small_radar_display` und `cc_aeroworks:large_radar_display` machen genau das Pult, in dem sie montiert sind, zu einem Create:-Radars-Monitorendpoint.

Unterstützt werden:

- normales Aeroworks-Steuerungspult,
- Advanced Aeroworks-Steuerungspult,
- ComputerControlDesk,
- Advanced ComputerControlDesk.

Ein Pult ohne kleines oder großes RadarDisplay ist kein gültiger Monitorendpoint. Eine Verbindung wird nicht automatisch auf benachbarte Pulte oder ein komplettes Pultnetz verteilt. Jedes gewünschte RadarDisplay-Pult erhält seinen eigenen physischen Create:-Radars-Data-Link-Block.

## Herstellung

- `cc_aeroworks:two_digit_display` plus `create_radar:monitor` ergibt `cc_aeroworks:small_radar_display`.
- `cc_aeroworks:three_digit_display` plus `create_radar:monitor` ergibt `cc_aeroworks:large_radar_display`.

Der native Monitor wird als Herstellungskomponente verwendet. Im fertigen Aufbau übernimmt das Pult mit RadarDisplay den Monitorendpoint.

## Nativer Aufbau

1. Ein Radar wird nach den Regeln von Create: Radars mit einem Network Filterer verbunden.
2. Der Spieler verwendet den Create:-Radars-Data-Link-Gegenstand auf dem Network Filterer. Das Item speichert nativ `SelectedFiltererPos`.
3. Der Spieler verwendet dasselbe Item auf einer freien Seite des Pults, in dem ein RadarDisplay montiert ist.
4. Create: Radars klassifiziert das Pult als `MONITOR`, prüft die Linkreichweite und bestehende Gruppenzuordnungen und platziert den physischen Data-Link-Block.
5. Der Originalcode ruft `NetworkData.canAttachMonitor(...)`, `attachMonitor(...)` und `addDataLinkToGroup(...)` auf, zeigt seine normalen Meldungen und bereinigt den Itemzustand.
6. Der Data-Link-Block bleibt sichtbar am Pult und ist Eigentümer der Verbindung.

Der Network Filterer muss nicht angrenzend zum Pult stehen. CC-Aeroworks sucht weder benachbarte Controller noch Radarblöcke.

## Minimale Erweiterungsstelle

CC-Aeroworks ersetzt `DataLinkBlockItem.useOn(...)` nicht. Sponge Mixin 0.8.7 besitzt keinen gültigen `@At("INSTANCEOF")`-Injection-Point. Deshalb verändert der optionale Mixin mit MixinExtras 0.5.0 ausschließlich das Ergebnis der ersten `instanceof`-Expression in der privaten Hilfsmethode `getFilterTarget(BlockEntity, BlockState)`:

- die erste `instanceof`-Expression ist im gepinnten Runtime-JAR die native `MonitorBlockEntity`-Prüfung,
- native `MonitorBlockEntity`-Instanzen bleiben durch den ursprünglichen booleschen Wert gültig,
- ein `ConsoleBlockEntity` wird nur mit tatsächlich montiertem RadarDisplay zusätzlich als Monitor erkannt,
- Create: Radars erzeugt seinen privaten `FilterTarget(MONITOR)` weiterhin selbst.

Der Mixin verwendet `@ModifyExpressionValue`, `@Expression("? instanceof ?")` und `ordinal = 0`. Der Bytecode-Vertrag lädt CurseForge-Datei `8227753` und bricht ab, falls `MonitorBlockEntity` nicht mehr die erste `INSTANCEOF`-Instruktion in `getFilterTarget(...)` ist. Damit kann der Hook nicht still auf eine spätere Typprüfung verrutschen.

Es gibt keine reflektive Konstruktion privater Create:-Radars-Zielobjekte, keine eigene Reichweitenprüfung, keine eigene Blockplatzierung und keine parallele Linkdatenbank. Die geprüfte native Aufrufkette und der Zielmethoden-Deskriptor sind in `docs/create-radars-native-flow-analysis.md` dokumentiert.

## Autoritativer Netzwerkzustand

Serverseitig ist `NetworkData` die einzige Verbindungsquelle. Für ein verbundenes Pult gilt:

```text
NetworkData.getFiltererForEndpoint(level.dimension(), deskPos) == filtererPos
```

Die zugehörige Gruppe enthält `deskPos` in `monitorEndpoints`. Aus dieser Gruppe liest das Pult:

- `radarPos`,
- `detectionTag`,
- `selectedTargetId`.

Der Radar wird ausschließlich an `radarPos` aufgelöst. CC-Aeroworks speichert keine Controllerposition und verteilt keinen Controller-Snapshot an ein Pultnetz.

## Monitoridentische Aktualisierung

Wie `MonitorBlockEntity` aktualisiert jedes RadarDisplay-Pult serverseitig im nativen Fünf-Tick-Zyklus bei `gameTime % 5 == 0` seinen Zustand. Es übernimmt Linkstatus, Radarposition, Reichweite und Betriebszustand aus dem nativen Netzwerk beziehungsweise `IRadar`.

Der Detection-Filter wird mit `DetectionConfig.fromTag(group.detectionTag)` erzeugt. Jeder Track aus `IRadar.getTracks()` wird mit dem nativen `DetectionConfig.test(RadarTrack)` gefiltert. Höchstens 256 akzeptierte Tracks werden anschließend **nicht** in einen CC-Aeroworks-Tracktyp umgewandelt. Stattdessen delegiert CC-Aeroworks an `RadarTrackUtil.serializeNBTList(...)` und transportiert genau diesen nativen CompoundTag zum Client.

Dadurch bleiben unter anderem `scannedTime`, native TrackCategory, Entity-/Sable-Metadaten, Größeninformationen und die native Spritezuordnung vollständig Eigentum von Create: Radars. CC-Aeroworks definiert keine `RadarDisplayTrack`- oder `RadarDisplayTrackSprite`-Parallelstruktur mehr.

Der Snapshot wird in das tatsächliche Clientupdate-NBT des `ConsoleBlockEntity` geschrieben. Geänderte Inhalte rufen `notifyUpdate()` auf; unveränderte aktive Zustände erhalten einen begrenzten Heartbeat.

Für die Freshness-Prüfung wird der Servertick nicht direkt mit `client.level.gameTime` verglichen. Beim Decodieren merkt sich der Snapshot den lokalen Client-Empfangstick. `updatedAt` bleibt als Servertick für Diagnose und Transport erhalten.

## Native Monitoroberfläche statt Nachbau

RadarDisplay zeichnet keine eigene Radar-Grafik mehr. Es gibt weder CC-Aeroworks-Radarringe noch einen eigenen Sweep, eigene Trackglyphen oder ein eigenes Trennungs-X.

Clientseitig erzeugt CC-Aeroworks pro sichtbarer RadarDisplay-Fläche einen **virtuellen, nicht in die Welt eingesetzten `MonitorBlockEntity`** der installierten Create:-Radars-Version. Dieser Monitor wird über seine eigene geschützte Methode `MonitorBlockEntity.read(..., clientPacket=true)` mit einem nativen Monitor-NBT befüllt:

- `HasRadarPos` und `radarPos`,
- `Filter`,
- `SelectedEntity`,
- `Size`,
- `tracks` aus `RadarTrackUtil.serializeNBTList(...)`,
- leere `SafeZones`, solange CC-Aeroworks keine Safe-Zone-Eingabe anbietet.

Anschließend ruft der Bridge reflektiv die private Methode `MonitorRenderer.renderRadarDisplay(...)` des echten registrierten Create:-Radars-Renderers auf. Damit rendert Create: Radars selbst:

- `GRID_SQUARE`,
- `RADAR_BG_FILLER`,
- `RADAR_BG_CIRCLE`,
- `RADAR_SWEEP`,
- die nativen Track-Sprites,
- Farben aus `DetectionConfig.getColor(...)`,
- Track-Fading über `scannedTime`,
- `TARGET_HOVERED` und `TARGET_SELECTED`,
- Tracklabels,
- Winkel und Radarart aus `IRadar`.

CC-Aeroworks setzt nur die Pose auf den jeweiligen Pult-Socket und skaliert die native quadratische Monitorfläche proportional auf die RadarDisplay-Fläche. Das große Display streckt den nativen Kreis nicht horizontal.

### Ein Renderpfad für Classic und Flywheel

Der native Monitor verwendet direkte `VertexConsumer`-/`RenderType`-Geometrie und lässt sich nicht verlustfrei in Flywheel-`PartialModel`-Instanzen übersetzen. Deshalb wird die Radaroberfläche weder im klassischen `ConsoleRenderer` noch im `ConsoleVisual` selbst gezeichnet.

Beide Renderpfade registrieren das sichtbare Pult lediglich beim `RadarOverlayRenderer`. Ein gemeinsamer `RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES`-Pass zeichnet anschließend jede RadarDisplay-Fläche genau einmal über `MonitorRenderer`. Normale programmierbare Ziffern-/Pixeldisplays verbleiben im bisherigen Classic-/Flywheel-Pfad.

Dadurch gibt es nur noch eine Radarimplementierung, unabhängig davon, ob Flywheel aktiv ist.

### Optional-Mod-Grenze

Der native Renderer-Bridge importiert keine `com.happysg.radar.*`-Klassen. Erst nach `ModList.isLoaded("create_radar")` werden `MonitorBlockEntity`, `MonitorRenderer`, `IRadar`, `ModBlocks.MONITOR` und `ModBlockEntityTypes.MONITOR` über Klassennamen aufgelöst und ihre Reflection-Handles gecacht.

Wenn dieser private Vertrag in einer späteren Create:-Radars-Version nicht mehr passt, wird die Radaroberfläche übersprungen und ein deduplizierter Fehler geloggt, statt den Client beim Klassenladen zu zerstören.

## Exakter Runtime-Vertrag

`tools/verify-create-radars-bytecode.py` prüft die veröffentlichte Datei `create_radar-0.4.9.4-1.21.1.jar` mit CurseForge File-ID `8227753` und zusätzlich zum Data-Link-Vertrag nun auch:

- den exakten Deskriptor von `MonitorRenderer.renderRadarDisplay(...)`,
- `MonitorBlockEntity.read(CompoundTag, HolderLookup.Provider, boolean)`,
- `getRadar()`, `getTracks()`, `getSize()` und `getShip()`,
- `RadarTrackUtil.serializeNBTList(...)` und `deserializeListNBT(...)`,
- die nativen `MonitorSprite`-Konstanten,
- alle zehn verwendeten `assets/create_radar/textures/monitor_sprite/*.png`-Ressourcen.

Damit kann ein Update des privaten Renderer-Vertrags nicht still durch eine grüne Repository-Prüfung rutschen.

## Nativer Cleanup

Beim Abbau des physischen Data-Link-Blocks führt `DataLinkBlock.onRemove(...)` den Create:-Radars-Cleanup aus:

- `removeDataLinkAndCleanup(...)` entfernt Link- und Endpointzuordnung,
- `onEndpointRemoved(...)` bereinigt den unterstützenden Endpoint zusätzlich,
- `getFiltererForEndpoint(...)` liefert anschließend keinen Filterer mehr.

CC-Aeroworks greift in diesen Ablauf nicht ein. Im nächsten Fünf-Tick-Zyklus wird der Pult-Snapshot als getrennt aktualisiert. Ein nicht aktiver Snapshot erzeugt keine erfundene CC-Aeroworks-Radarwarnung; der native Monitorinhalt wird dann schlicht nicht gezeichnet.

## Diagnose

CC-Aeroworks protokolliert serverseitig nur Zustandsänderungen, nicht jeden Tick:

```text
Radar endpoint desk=<pos> filterer=<pos> radar=<pos> status=<status> filteredTracks=<n> reason=<text>
```

Für einen aktiven Aufbau mit Kontakten muss `status=ACTIVE` und `filteredTracks` größer als null erscheinen. API- und Reflexionsfehler werden dedupliziert mit Ursache protokolliert.

Zusätzlich protokolliert der Client bei einer Änderung des empfangenen Radarzustands:

```text
Radar client snapshot desk=<pos> status=<status> radar=<pos> tracks=<n> serverTick=<tick> clientTick=<tick>
```

Der native Renderer-Bridge loggt separat, wenn der virtuelle Monitor, sein registrierter Renderer oder `renderRadarDisplay(...)` nicht verwendbar ist.

## Laufzeitmatrix

Die verbindliche manuelle Matrix steht in `docs/radar-controller-test-plan.md`. Neben allen vier Pulttypen, beiden Displaygrößen, Ausrichtungen, Filteränderungen, Zielauswahl, Linkabbau, Classic/Flywheel und Start ohne Create: Radars ist nun ein direkter Referenztest Pflicht:

1. echter `create_radar:monitor` und RadarDisplay hängen am selben Filterer/Radar,
2. Hintergrund, Grid, Sweep, Track-Sprite, Farbe, Fade und Selection müssen übereinstimmen,
3. eine Änderung von `groundRadarColor` muss auf beiden Anzeigen erscheinen,
4. ein Resource Pack, das `create_radar:textures/monitor_sprite/*` ersetzt, muss beide Anzeigen gleichermaßen verändern.

Der Entwicklungsclient wird mit folgendem Befehl gestartet:

```bash
./gradlew runClient --stacktrace
```

Ein grüner statischer Vertrag ersetzt den Ingame-Test nicht. Der Draft-PR darf erst als behoben gelten, wenn `ACTIVE`, sichtbare native Radartracks und der Referenzvergleich mit dem echten Monitor im Entwicklungsclient nachgewiesen wurden.
