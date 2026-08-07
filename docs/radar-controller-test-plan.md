# Radar Data Link regression plan

Diese Matrix prüft ein Aeroworks-Pult mit RadarDisplay als echten Create:-Radars-`MONITOR`-Endpoint. Zielversion ist Create: Radars `0.4.9.4-1.21.1`.

## Start- und Ressourcenmatrix

| Aufbau | Schritte | Erwartung |
| --- | --- | --- |
| Ohne Create: Radars | Entwicklungsclient ohne `create_radar` starten | Kein optionaler Mixinfehler; keine Radaritems oder Radarrezepte |
| Mit Create: Radars | Vollständige Runtime starten | Data-Link-Mixin wird angewendet; keine Missing-Texture für `create_radar:monitor_sprite/*` |
| Ressourcenreload | Bei sichtbarem RadarDisplay `F3+T` | Radaroberfläche erscheint erneut |
| Dedicated Server | Serverprofil starten | Keine Clientrenderer auf dem Server; Data-Link- und Endpointlogik lädt ohne Clientklassen |

## Native Filterer-first-Verbindung

| Fall | Schritte | Erwartung |
| --- | --- | --- |
| Filterer auswählen | Create:-Radars-Data-Link auf Network Controller anwenden | Item speichert nativ `SelectedFiltererPos` und zeigt die native Auswahlmeldung |
| Pult ohne RadarDisplay | Danach ein normales Pult ohne RadarDisplay anklicken | Ziel bleibt ungültig; keine CC-Aeroworks-Sonderplatzierung |
| Data-Link-Endpoint | Danach ein Pult mit RadarDisplay anklicken | Pult wird als `MONITOR` klassifiziert; native Reichweitenprüfung und Platzierung laufen |
| Physischer Link | Eine freie Pultseite anklicken | Der Data-Link-Block steht an dieser Fläche mit Radar-Linkstil |
| Blockiert | Zielposition für den Data-Link-Block blockieren | Native Platzierung schlägt fehl; keine Endpointregistrierung |
| Zu weit entfernt | Pult außerhalb der konfigurierten Linkreichweite | Native `too_far`-Meldung; Itemauswahl wird zurückgesetzt |
| Zweite Gruppe | Dasselbe Pult aus einer anderen Filterer-Gruppe verbinden | `NetworkData.canAttachMonitor` lehnt die fremde Gruppe ab |
| Zweiter Endpoint | Zweites Pult mit RadarDisplay an dieselbe Gruppe hängen | Beide Pultpositionen stehen in `monitorEndpoints` |
| Data Link entfernen | Den physischen Data-Link-Block abbauen | Create: Radars entfernt Data-Link- und Endpointindex nativ; Anzeige wird spätestens nach 5 Ticks getrennt |
| Pult abbauen | Verknüpftes Pult abbauen | Gestützter Data-Link-Block wird entfernt; native Cleanup-Pfade bleiben konsistent |

## Endpointzustände

| Zustand | Aufbau | Erwartung |
| --- | --- | --- |
| `RADAR_NOT_LINKED` | RadarDisplay vorhanden, aber kein Data Link | Orange Statuskreuz, keine Tracks |
| `RADAR_NOT_LINKED` | Endpointgruppe vorhanden, aber kein Radar in der Gruppe | Orange Statuskreuz |
| `RADAR_NOT_LOADED` | Radarposition bekannt, Radar-Chunk entladen | Orange Statuskreuz, kein erzwungenes Chunkladen |
| `RADAR_NOT_RUNNING` | Verbundenen Radar stoppen | Orange Statuskreuz |
| `INVALID_RANGE` | Laufender Radar meldet Reichweite 0 | Orange Statuskreuz |
| `ACTIVE` | Endpoint, Gruppe und laufender Radar vorhanden | Kreuz verschwindet; Sweep und gefilterte Tracks erscheinen |
| `API_INCOMPATIBLE` | Benötigte NetworkData-, DetectionConfig- oder IRadar-Signatur verändern | Einmalige Fehlerursache im Log, keine Fünf-Tick-Logflut |

## Übereinstimmung mit MonitorBlockEntity

| Datenquelle | Schritte | Erwartung |
| --- | --- | --- |
| Gruppenauflösung | Verknüpftes Pult beobachten | `getFiltererForEndpoint(dimension, deskPos)` und danach `getGroup(...)` bestimmen die Quelle |
| Endpointvalidierung | Pultposition aus `monitorEndpoints` entfernen | Anzeige wechselt auf `RADAR_NOT_LINKED` |
| Radarposition | Gruppe auf einen anderen Radar umhängen | `radarPos` wird im nächsten Fünf-Tick-Zyklus übernommen |
| Detection-Filter | Spieler, Mob, Tier, Item, Projektil, Contraption und VS2 erzeugen; Filter umschalten | RadarDisplay zeigt exakt die durch `DetectionConfig.test(...)` erlaubten Kategorien |
| Zielauswahl | Ziel im Create:-Radars-Netz auswählen | `selectedTargetId` erzeugt die Zielmarkierung über demselben Track |
| VS2-Zentrum | Radar oder Pult auf bewegtem Schiff betreiben | Zentrum kommt aus `PhysicsHandler.getWorldVec(...)`, nicht aus rohen Shipyard-Koordinaten |
| Takt | Netzwerkpakete protokollieren | Snapshotberechnung alle 5 Ticks wie beim nativen Monitor |
| Heartbeat | Leeren aktiven Radar beobachten | Unveränderter Inhalt wird spätestens nach 15 Ticks erneut synchronisiert |
| Tracklimit | Mehr als 256 erkannte Ziele erzeugen | Höchstens 256 nächstgelegene gefilterte Tracks werden übertragen |

## Direkte Monitoroberfläche

| Element | Schritte | Erwartung |
| --- | --- | --- |
| Hintergrund | Aktive Gruppe ohne Kontakte | Hintergrund und Radarkreis ohne Missing-Texture |
| Sweep | Anzeige beobachten | Sweep rotiert kontinuierlich |
| Normale Entität | Entität im Bereich | `entity_hitbox` erscheint |
| Spieler | Spieler im Bereich | `player` erscheint |
| Projektil | Projektil im Bereich | `projectile` erscheint |
| Contraption/VS2 | Contraption oder Schiff im Bereich | `contraption_hitbox` erscheint |
| Zielauswahl | Track auswählen | `target_selected` liegt über dem Track |
| Außerhalb der Reichweite | Track außerhalb des Kreises | Track wird nicht gerendert |
| Kleine und große Anzeige | Beide Größen verwenden | Gleiche Daten, passende Skalierung |
| Alle vier Pultausrichtungen | Pult nach Norden, Osten, Süden und Westen | Welttrack bleibt in korrekter lokaler Bildschirmrichtung |
| Klassischer Renderer | Flywheel deaktivieren | Translucent-Hintergrund/Sweep und Cutout-Tracks |
| Flywheel | Flywheel aktivieren | Identische Elemente; stabile Trackinstanzen |

## ComputerControlDesk

| Aufbau | Schritte | Erwartung |
| --- | --- | --- |
| Normales Computerpult | Steuereinheiten und RadarDisplay einsetzen | Native Aeroworks-Einheiten und Radaroberfläche sichtbar |
| Advanced Computerpult | Gleicher Aufbau | Native Einheiten und Radaroberfläche sichtbar |
| Data Link | Filterer auswählen und Computerpult mit RadarDisplay anklicken | Pult wird wie normales Pult als `MONITOR`-Endpoint registriert |
| Flywheel an/aus | Beide Modi prüfen | Steuereinheiten und Radarfläche bleiben sichtbar; keine doppelte Oberfläche |
| Delegatefehler | Klassischen ConsoleRenderer absichtlich inkompatibel machen | display-only Fallback erhält dynamische Oberflächen |

## Laufzeitbeobachtung

- Es existiert genau ein optionaler Mixin am privaten `DataLinkBlockItem.getFilterTarget(...)`.
- Der Mixin ersetzt nicht `useOn(...)`, platziert keinen Block selbst und speichert keine eigene Verbindung.
- Ein Pult mit RadarDisplay wird als nativer `MONITOR`-Zieltyp zurückgegeben.
- Create: Radars führt `canAttachMonitor`, Reichweitenprüfung, Blockplatzierung, `NetworkData.attachMonitor` und `addDataLinkToGroup` selbst aus.
- `ConsoleBlockEntityRadarMixin` ruft den Endpointadapter im Pulttick auf.
- Der Adapter liest `NetworkData`, `monitorEndpoints`, `radarPos`, `detectionTag` und `selectedTargetId`.
- Der frühere Network-Controller-Tick-Hook und jede automatische Nachbarschaftssuche sind entfernt.
- Es wird keine Controllerposition im Pult gespeichert.
- Data-Link-Entfernung verwendet ausschließlich Create:-Radars-Cleanup.
- RadarDisplays verwenden `RadarSurfaceRenderer`, nicht `DeskDisplayPixels` oder `RadarDisplayRaster`.
