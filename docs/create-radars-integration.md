# Create: Radars integration

Die Integration ist optional und wird nur aktiv, wenn Create: Radars mit der Mod-ID `create_radar` geladen ist. Unterstützt und im Entwicklungsmanifest fest eingetragen ist Create: Radars `0.4.9.4-1.21.1` für Minecraft 1.21.1.

Ohne Create: Radars bleiben CC-Aeroworks und alle normalen Pultfunktionen startfähig. RadarDisplay-Rezepte und optionale Inhalte werden über `neoforge:mod_loaded` ausgeblendet. Der optionale Data-Link-Mixin ist `@Pseudo`, verwendet ein Klassennamenziel und enthält keine Create:-Radars-Typen in seiner Handler-Signatur.

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

Es gibt keine reflektive Konstruktion privater Create:-Radars-Zielobjekte, keine eigene Reichweitenprüfung, keine eigene Blockplatzierung und keine parallele Linkdatenbank.

Die geprüfte native Aufrufkette und der Zielmethoden-Deskriptor sind in `docs/create-radars-native-flow-analysis.md` dokumentiert.

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

Wie `MonitorBlockEntity` aktualisiert jedes RadarDisplay-Pult serverseitig bei `gameTime % 5 == 0` seinen Zustand. Es übernimmt:

- Filtererposition und Linkstatus,
- Radarposition und über `PhysicsHandler` aufgelöstes Weltzentrum,
- Reichweite und Betriebszustand aus `IRadar`,
- Detection-Filter aus `DetectionConfig.fromTag(group.detectionTag)`,
- ausgewähltes Ziel aus `group.selectedTargetId`,
- Radartracks aus `IRadar.getTracks()`.

Jeder Track wird mit dem nativen `DetectionConfig.test(RadarTrack)` gefiltert. Damit gelten dieselben Spieler-, Sable/VS2-, Contraption-, Mob-, Projektil-, Tier- und Itemfilter wie beim nativen Monitor. Synchronisiert werden höchstens 256 gefilterte Tracks mit ID, Position, Geschwindigkeit und nativer Trackkategorie.

Der Snapshot wird in das tatsächliche Clientupdate-NBT des `ConsoleBlockEntity` geschrieben. Geänderte Inhalte rufen `notifyUpdate()` auf; unveränderte aktive Zustände erhalten einen begrenzten Heartbeat.

Für die Freshness-Prüfung wird **nicht** mehr der Servertick direkt mit `client.level.gameTime` verglichen. Beim Decodieren merkt sich der Snapshot stattdessen den lokalen Client-Empfangstick. Das Trennungs-X kann damit nicht allein durch einen Versatz zwischen Server- und Clientuhr ausgelöst werden. `updatedAt` bleibt als Servertick für Diagnose und Transport erhalten, die Anzeigealterung verwendet aber den lokalen Client-Empfangstick.

## Nativer Cleanup

Beim Abbau des physischen Data-Link-Blocks führt `DataLinkBlock.onRemove(...)` den Create:-Radars-Cleanup aus:

- `removeDataLinkAndCleanup(...)` entfernt Link- und Endpointzuordnung,
- `onEndpointRemoved(...)` bereinigt den unterstützenden Endpoint zusätzlich,
- `getFiltererForEndpoint(...)` liefert anschließend keinen Filterer mehr.

CC-Aeroworks greift in diesen Ablauf nicht ein. Im nächsten Fünf-Tick-Zyklus wird der Pult-Snapshot als getrennt aktualisiert. Das orange Trennungs-X erscheint nur, wenn kein gültiger Endpoint, kein Radar, ein ungeladener oder gestoppter Radar, eine ungültige Reichweite, ein API-Fehler oder ein tatsächlich veralteter Client-Snapshot vorliegt.

## Radaroberfläche

Der klassische BlockEntityRenderer und der Flywheel-`ConsoleVisual` verwenden dieselben `RadarSurfaceRenderer`-Elemente.

Frühere Builds registrierten eigene Radar-`PartialModel`s, deren Texturen direkt auf `create_radar:monitor_sprite/*` zeigten. Diese PNGs werden von Create: Radars über den eigenen `MonitorRenderer` als direkte Renderertexturen benutzt und sind nicht als stabiler Blockatlas-Vertrag gedacht. Auf realen Clients konnte dieser Pfad deshalb als schwarz-pinke Missing-Texture-Fläche erscheinen.

Die Radaroberfläche verwendet nun ausschließlich bereits bewährte CC-Aeroworks-eigene Blockatlas-Partials:

- `display_segment_horizontal` und `display_segment_vertical` bilden den Radarrahmen,
- ein vertikales Segment bildet den rotierenden Sweep,
- `display_pixel` bildet Kontakte und Auswahlmarkierungen,
- `radar_disconnected` bleibt das Trennungs-X.

Damit hängt die Radaroberfläche nicht mehr von Create:-Radars-Monitor-Sprites im Minecraft-Blockatlas ab. Gleichzeitig bleibt derselbe Elementpfad für klassischen Renderer und Flywheel erhalten.

Die Kontaktformen sind bewusst aus denselben lokalen Pixeln aufgebaut:

- Entity: ein Pixel,
- Spieler: zwei vertikale Pixel,
- Projektil: zwei horizontale Pixel,
- Contraption/Sable: vier Eckpixel,
- ausgewähltes Ziel: vier zusätzliche Markierungspixel.

Trackpositionen werden relativ zu Radarzentrum und Reichweite auf die kleine oder große Modulfläche projiziert. Die programmierbare Pixelauflösung beeinflusst RadarDisplays nicht.

Normales und Advanced ComputerControlDesk verwenden denselben CC-Aeroworks-BlockEntity-Typ. Für diesen Typ wird der native Aeroworks-`ConsoleVisual` explizit registriert, damit Steuereinheiten mit Flywheel erhalten bleiben. Der klassische Renderer delegiert ebenfalls an den Aeroworks-Renderer und besitzt nur einen Display-Fallback für geänderte Renderer-Konstruktoren.

## Diagnose

CC-Aeroworks protokolliert serverseitig nur Zustandsänderungen, nicht jeden Tick. Eine Diagnosezeile enthält:

```text
Radar endpoint desk=<pos> filterer=<pos> radar=<pos> status=<status> filteredTracks=<n> reason=<text>
```

Für einen aktiven Aufbau mit Kontakten muss `status=ACTIVE` und `filteredTracks` größer als null erscheinen. API- und Reflexionsfehler werden dedupliziert mit Ursache protokolliert.

Zusätzlich protokolliert der Client bei einer Änderung des empfangenen Radarzustands:

```text
Radar client snapshot desk=<pos> status=<status> radar=<pos> tracks=<n> serverTick=<tick> clientTick=<tick>
```

Damit lassen sich drei Fehlerklassen getrennt beweisen: Server erzeugt keinen aktiven Zustand, Server ist aktiv aber der Client erhält ihn nicht, oder Server und Client sind aktiv und nur die Darstellung ist defekt.

## Laufzeitmatrix

Die verbindliche manuelle Matrix steht in `docs/radar-controller-test-plan.md`. Sie umfasst alle vier Pulttypen, beide Displaygrößen, alle vier Ausrichtungen, Filteränderungen, Zielauswahl, Linkabbau, klassischen Renderer, Flywheel sowie den Start ohne Create: Radars.

Der Entwicklungsclient wird mit folgendem Befehl gestartet:

```bash
./gradlew runClient --stacktrace
```

Ein grüner statischer Vertrag ersetzt den Ingame-Test nicht. Der Draft-PR darf erst als behoben gelten, wenn im Entwicklungsclient sichtbare Radartracks nachgewiesen wurden.
