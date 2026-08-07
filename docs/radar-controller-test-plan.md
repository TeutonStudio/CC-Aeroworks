# Native Radar Data-Link regression matrix

Diese Matrix prüft Create: Radars `0.4.9.4-1.21.1` gegen den nativen Filterer-zu-Monitor-Ablauf. Ein erfolgreicher statischer Vertrag genügt nicht; sichtbare Tracks im Entwicklungsclient und der direkte Vergleich mit einem echten `create_radar:monitor` sind der Abschlussnachweis.

## Voraussetzungen und Versionen

Pro Lauf dokumentieren:

- CC-Aeroworks Commit,
- Aeroworks-Version,
- Create-Version,
- Create: Radars-Version,
- Create Big Cannons und Ritchie's Projectile Library,
- Flywheel aktiviert oder deaktiviert,
- optionale VS2/Sable-Versionen,
- relevante Logzeilen.

Vor dem Sichttest müssen Serverzustand, Clienttransport und nativer MonitorRenderer getrennt belegt werden.

## Fehlerisolation vor dem Sichttest

| Stufe | Beweis | Erwartung |
| --- | --- | --- |
| Native Registrierung | Pult-Data-Link steht physisch und `getFiltererForEndpoint(dimension, deskPos)` wird geprüft | Liefert den ausgewählten Network Filterer |
| Server-Snapshot | Serverlog `Radar endpoint ...` prüfen | Aktiver Radar liefert `status=ACTIVE`; ohne Kontakte `filteredTracks=0`, mit Kontakt größer als null |
| Client-Snapshot | Clientlog `Radar client snapshot ...` prüfen | Derselbe Status, Radarposition und Trackanzahl kommen auf dem Client an |
| Freshness | `serverTick` und `clientTick` dürfen verschieden sein | Heartbeats halten `ACTIVE`; Freshness basiert auf lokalem Client-Empfangstick |
| Native Payload | Client erhält `tracks` als Create:-Radars-`RadarTrackUtil`-CompoundTag | Keine CC-Aeroworks-eigene Track-/Sprite-Rekonstruktion |
| Native Renderer | Log nach `Native Create: Radars monitor rendering failed` prüfen | Keine Meldung; virtueller Monitor und registrierter `MonitorRenderer` sind verwendbar |
| Ressourcen | Clientlog nach `missing texture`, `missing model` durchsuchen | Keine Missing-Texture-/Missing-Model-Meldung |
| Referenzmonitor | echten `create_radar:monitor` neben RadarDisplay betreiben | Hintergrund, Grid, Sweep und Tracks stimmen überein |
| Kontakt | Spieler/Mob in Reichweite erzeugen | Nativer Kontakt erscheint auf beiden Anzeigen |

## Data-Link- und Netzwerkvertrag

| Fall | Schritte | Erwartung |
| --- | --- | --- |
| Filterer auswählen | Data Link auf Network Filterer verwenden | Item speichert nativ die Filterer-Auswahl und zeigt die native Meldung |
| Normales Pult ohne RadarDisplay | Dasselbe Item auf Pult verwenden | Kein Monitorziel; nativer Fehler; kein Data-Link-Block |
| Advanced Pult ohne RadarDisplay | Dasselbe Item auf Pult verwenden | Kein Monitorziel; nativer Fehler; kein Data-Link-Block |
| ComputerControlDesk ohne RadarDisplay | Dasselbe Item auf Pult verwenden | Kein Monitorziel; nativer Fehler; kein Data-Link-Block |
| Advanced ComputerControlDesk ohne RadarDisplay | Dasselbe Item auf Pult verwenden | Kein Monitorziel; nativer Fehler; kein Data-Link-Block |
| Pult mit kleinem RadarDisplay | Filterer wählen, danach Pult anklicken | Create: Radars platziert physischen Data-Link-Block und meldet Erfolg |
| Pult mit großem RadarDisplay | Filterer wählen, danach Pult anklicken | Identischer nativer Monitorpfad |
| Linkreichweite | Pult beziehungsweise Filterer außerhalb der nativen Reichweite | Native Reichweitenmeldung; kein Block und keine Endpointregistrierung |
| Fremde Gruppe | Bereits anderweitig registrierten Endpoint mit zweitem Filterer verbinden | `canAttachMonitor` lehnt nativ ab |
| NetworkData | Aktiven Aufbau prüfen | `getFiltererForEndpoint(dimension, deskPos)` liefert den gewählten Filterer |
| monitorEndpoints | Zugehörige Gruppe prüfen | `monitorEndpoints` enthält exakt die Pultposition |
| Physischer Link vorhanden | Welt speichern/laden, Linkblock stehen lassen | Endpoint bleibt registriert |
| Link entfernen | Physischen Data-Link-Block abbauen | Native Zuordnung verschwindet; native Radaroberfläche verschwindet innerhalb von fünf Ticks |
| Mehrere Endpoints | Zwei RadarDisplay-Pulte jeweils mit eigenem Data Link an dieselbe Gruppe | Beide Positionen stehen in `monitorEndpoints`; keine Snapshotverteilung ohne Link |
| Kein Nachbarschaftspfad | Network Filterer weit entfernt, aber innerhalb Linkreichweite | Verbindung funktioniert ohne angrenzenden Controller |

## Datenauflösung und Filter

| Fall | Schritte | Erwartung |
| --- | --- | --- |
| Gruppe ohne Radar | Pultendpoint verbinden, Gruppe besitzt kein `radarPos` | Keine Radaroberfläche; Diagnose `RADAR_NOT_ASSIGNED` |
| Ungeladener Radar | Radar-Chunk entladen | Keine Radaroberfläche; Diagnose `RADAR_NOT_LOADED` |
| Gestoppter Radar | Radar abschalten | Keine Radaroberfläche; Diagnose `RADAR_STOPPED` |
| Aktiver Radar ohne Kontakte | Radar betreiben, Bereich leer | Native Monitorfläche mit Grid/Hintergrund/Sweep; Server und Client `ACTIVE` |
| Spieler | Spieler im Bereich | derselbe native Spieler-Sprite wie am echten Monitor |
| Mob/Hostile | Mob im Bereich | derselbe native Entity-Sprite/Farbwert wie am echten Monitor |
| Tier | Tier im Bereich | natives Verhalten entsprechend Detection-Filter |
| Item | Item im Bereich | natives Verhalten entsprechend Detection-Filter |
| Projektil | Projektil durch Bereich bewegen | nativer Projektil-Sprite folgt dem Track |
| Contraption | Create-Contraption in Reichweite | nativer Contraption-Sprite sichtbar |
| Sable/VS2 | Schiffstrack in Reichweite | nativer Sable/Contraption-Sprite und Weltbezug stimmen mit Referenzmonitor überein |
| Spielerfilter aus | Spieler-Detection deaktivieren | Spielertrack verschwindet im nächsten Update |
| Spielerfilter an | Spieler-Detection aktivieren | Spielertrack erscheint erneut |
| Projektilfilter | Projektil-Detection umschalten | Nur Projektilkontakte ändern sich |
| Mobfilter | Mob-Detection umschalten | Nur Mobkontakte ändern sich |
| Auswahl | Track im nativen Monitor-/Filtererablauf auswählen | `selectedTargetId` stimmt; `TARGET_SELECTED` entspricht Referenzmonitor |
| Trackalter | Ziel einige Ticks nicht neu erfassen lassen | Fade entspricht dem echten Monitor |
| Track entfernt | Ziel verlässt Bereich oder wird gelöscht | Kontakt und Auswahl verschwinden |
| Diagnose aktiv | Ziele vorhanden | Server `filteredTracks>0`, Client `tracks>0`, keine Renderer-Bridge-Warnung |

## Pulttypen, Größen und Ausrichtungen

Jede Kombination mindestens einmal prüfen:

| Pulttyp | Kleines Display | Großes Display | Nord | Ost | Süd | West |
| --- | :---: | :---: | :---: | :---: | :---: | :---: |
| Aeroworks-Steuerungspult | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |
| Advanced Aeroworks-Steuerungspult | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |
| ComputerControlDesk | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |
| Advanced ComputerControlDesk | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |

Erwartung:

- native quadratische Monitorfläche bleibt proportional und wird nicht horizontal verzerrt,
- Nord/Ost/Süd/West entsprechen der Orientierung eines gleich ausgerichteten nativen Monitors,
- Sweepwinkel kommt aus `IRadar.getGlobalAngle()` beziehungsweise nativer Radarart, nicht aus einem CC-Aeroworks-Timer,
- kleines und großes RadarDisplay zeigen denselben Netzwerkzustand passend zentriert/skaliert.

## Referenztest gegen echten Create:-Radars-Monitor

Für diese Tests werden ein echter `create_radar:monitor` und ein RadarDisplay-Pult über eigene physische Data Links mit **derselben NetworkData-Gruppe und demselben Radar** verbunden.

| Test | Erwartung |
| --- | --- |
| Hintergrund | `RADAR_BG_FILLER` und `RADAR_BG_CIRCLE` wirken gleich |
| Grid | identische Grid-Textur, Farbe und Skalierungslogik |
| Sweep | identischer Sprite, Farbwert und Winkel |
| Track-Sprite | identische `MonitorSprite`-Auswahl |
| Detection-Farbe | identische `DetectionConfig.getColor(track)`-Farbe |
| Fade | identischer Verlauf aus `scannedTime` |
| Selection | identisches `TARGET_SELECTED` |
| Label | identischer Trackslug/Label-Renderer |
| `groundRadarColor` ändern | beide Anzeigen reagieren auf dieselbe Create:-Radars-Clientconfig |
| Resource Pack ersetzt `create_radar:textures/monitor_sprite/*` | beide Anzeigen verwenden die ersetzten nativen Texturen |

## Renderer und Computerpulte

| Fall | Schritte | Erwartung |
| --- | --- | --- |
| Klassischer Renderer | Flywheel für Pultvisualisierung deaktivieren | Aeroworks-Steuereinheiten und native Monitoroberfläche sichtbar |
| Flywheel | Flywheel aktivieren | Native `ConsoleVisual`-Steuereinheiten plus derselbe gemeinsame Radar-Overlay-Pass sichtbar |
| Einmaliges Rendering | Beide Pfade beobachten | keine doppelte Radarfläche/Z-Fighting; Overlay zeichnet je Pultfläche einmal |
| Missing-Texture-Regression | beide Renderpfade laden | keine schwarz-pinke Fläche; Create:-Radars-Sprites werden direkt durch `MonitorRenderer` benutzt, nicht als Blockatlas-PartialModels |
| ComputerControlDesk | normales Computerpult mit Hebel/Joystick und RadarDisplay | Steuereinheiten bleiben sichtbar; Radaroverlay liegt korrekt auf dem Socket |
| Advanced ComputerControlDesk | Advanced-Variante wiederholen | identisches Verhalten über gemeinsamen BlockEntity-Typ |
| Linkabbau bei Flywheel | aktiven Data Link entfernen | Overlay verschwindet; keine stale Flywheel-Radarinstanz existiert |
| Heartbeat ohne Inhaltsänderung | aktiven Radar beobachten | kein unnötiges Neubauen eigener Radarinstanzen, da es keine solchen Instanzen mehr gibt |

## Optionale Modkompatibilität

| Fall | Schritte | Erwartung |
| --- | --- | --- |
| Ohne Create: Radars | Runtime ohne `create_radar` starten | Kein Klassenlade- oder Mixinfehler; normale CC-Aeroworks-Funktionen verfügbar |
| Mit Create: Radars | gepinnte Version laden | Data-Link-Mixin und privater MonitorRenderer-Vertrag werden gefunden |
| Inkompatibler Renderer simuliert | private Signatur im Verifier ändern | CI rot; Runtime-Bridge würde dedupliziert loggen und Overlay auslassen statt zu crashen |
| Ohne VS2/Sable | Radar betreiben | normale Weltkoordinaten funktionieren |
| Mit VS2/Sable | bewegte Plattform oder Schiff testen | native Monitorlogik und Overlayposition gegen echten Monitor vergleichen |

## Relevante Logfilter

```bash
grep -E "CC-Aeroworks.*Radar endpoint|CC-Aeroworks.*Radar client snapshot|Create: Radars API access failed|Native Create: Radars monitor rendering failed|missing texture|missing model" run/logs/latest.log
```

Ein aktiver leerer Aufbau soll eine Server- und Clientzeile mit `status=ACTIVE` liefern. Bei einem Kontakt muss `filteredTracks` beziehungsweise `tracks` größer als null werden.

## Auszuführende Befehle

```bash
python3 -m py_compile tools/*.py
python3 tools/verify-create-radars-bytecode.py
python3 tools/verify-repository.py
python3 tools/verify-guide.py
python3 tools/verify-peripheral-network.py
python3 tools/verify-radar.py
python3 tools/verify-radar-link.py
./gradlew clean test --stacktrace
./gradlew runClient --stacktrace
```

## Ergebnisprotokoll

Für jeden nicht ausgeführten Fall ausdrücklich `NICHT GETESTET` notieren. Der PR bleibt Draft, solange nicht mindestens folgende Kette im Entwicklungsclient bestätigt ist:

1. Filterer auswählen.
2. Pult mit RadarDisplay verbinden.
3. Physischer Data-Link-Block sichtbar.
4. Serverlog meldet `status=ACTIVE`.
5. Clientlog empfängt `status=ACTIVE`.
6. Nativer `create_radar:monitor` wird als Referenz mit derselben Gruppe verbunden.
7. RadarDisplay zeigt denselben Hintergrund, Grid und Sweep wie der Referenzmonitor.
8. Spieler oder Mob erzeugen; derselbe native Track erscheint auf beiden Anzeigen.
9. Detection-Filter ändert beide Anzeigen gleich.
10. Zielmarkierung/Fade/Label vergleichen.
11. `groundRadarColor` ändern und beide Anzeigen vergleichen.
12. Linkblockabbau entfernt Overlay und Endpointzuordnung.
13. ComputerControlDesk und Advanced ComputerControlDesk wiederholen.
14. Flywheel an und aus wiederholen.
15. Start ohne Create: Radars erfolgreich.
