# Native Radar Data-Link regression matrix

Diese Matrix prüft Create: Radars `0.4.9.4-1.21.1` gegen den nativen Filterer-zu-Monitor-Ablauf. Ein erfolgreicher statischer Vertrag genügt nicht; sichtbare Tracks im Entwicklungsclient sind der Abschlussnachweis.

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
| NetworkData | Aktiven Aufbau im Debugger oder Diagnosebefehl prüfen | `getFiltererForEndpoint(dimension, deskPos)` liefert den gewählten Filterer |
| monitorEndpoints | Zugehörige Gruppe prüfen | `monitorEndpoints` enthält exakt die Pultposition |
| Physischer Link vorhanden | Welt speichern/laden, Linkblock stehen lassen | Endpoint bleibt registriert |
| Link entfernen | Physischen Data-Link-Block abbauen | Native Zuordnung verschwindet; Display trennt innerhalb von fünf Ticks |
| Mehrere Endpoints | Zwei RadarDisplay-Pulte jeweils mit eigenem Data Link an dieselbe Gruppe | Beide Positionen stehen in `monitorEndpoints`; keine Snapshotverteilung ohne Link |
| Kein Nachbarschaftspfad | Network Filterer weit entfernt, aber innerhalb Linkreichweite | Verbindung funktioniert ohne angrenzenden Controller |

## Datenauflösung und Filter

| Fall | Schritte | Erwartung |
| --- | --- | --- |
| Gruppe ohne Radar | Pultendpoint verbinden, Gruppe besitzt kein `radarPos` | Trennungs-X; Diagnose `RADAR_NOT_ASSIGNED` |
| Ungeladener Radar | Radar-Chunk entladen | Trennungs-X; Diagnose `RADAR_NOT_LOADED` |
| Gestoppter Radar | Radar abschalten | Trennungs-X; Diagnose `RADAR_STOPPED` |
| Aktiver Radar ohne Kontakte | Radar betreiben, Bereich leer | Hintergrund, Kreis und Sweep; kein X |
| Spieler | Spieler im Bereich | Spieler-Sprite sichtbar und aktuell |
| Mob/Hostile | Mob im Bereich | Entity-Sprite sichtbar, sofern Detection-Filter aktiv |
| Tier | Tier im Bereich | Entity-Sprite entsprechend Filter |
| Item | Item im Bereich | Entity-Sprite entsprechend Filter |
| Projektil | Projektil durch den Bereich bewegen | Projektil-Sprite folgt dem Track |
| Contraption | Create-Contraption in Reichweite | Contraption-Sprite sichtbar |
| Sable/VS2 | Schiffstrack in Reichweite | Contraption-Sprite mit korrektem Weltzentrum sichtbar |
| Spielerfilter aus | Spieler-Detection deaktivieren | Spielertrack verschwindet im nächsten Update |
| Spielerfilter an | Spieler-Detection aktivieren | Spielertrack erscheint erneut |
| Projektilfilter | Projektil-Detection umschalten | Nur Projektilkontakte ändern sich |
| Mobfilter | Mob-Detection umschalten | Nur Mobkontakte ändern sich |
| Auswahl | Track im nativen Monitor-/Filtererablauf auswählen | `selectedTargetId` stimmt; Zielmarkierung liegt über Kontakt |
| Trackwechsel | Ziel bewegt sich oder wird ersetzt | Client-Snapshot und Symbolposition ändern sich innerhalb des Fünf-Tick-Zyklus |
| Track entfernt | Ziel verlässt Bereich oder wird gelöscht | Kontakt und Auswahlmarkierung verschwinden |
| Diagnose aktiv | Ziele vorhanden | Log enthält `status=ACTIVE` und `filteredTracks` größer als null |

## Pulttypen, Größen und Ausrichtungen

Jede Kombination mindestens einmal prüfen:

| Pulttyp | Kleines Display | Großes Display | Nord | Ost | Süd | West |
| --- | :---: | :---: | :---: | :---: | :---: | :---: |
| Aeroworks-Steuerungspult | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |
| Advanced Aeroworks-Steuerungspult | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |
| ComputerControlDesk | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |
| Advanced ComputerControlDesk | ☐ | ☐ | ☐ | ☐ | ☐ | ☐ |

Erwartung:

- Projektion bleibt innerhalb des Radarkreises,
- Nord/Ost/Süd/West spiegeln oder drehen Tracks nicht falsch,
- Sweep rotiert um die Displaymitte,
- kleines und großes RadarDisplay zeigen denselben Netzwerkzustand passend skaliert.

## Renderer und Computerpulte

| Fall | Schritte | Erwartung |
| --- | --- | --- |
| Klassischer Renderer | Flywheel für Pultvisualisierung deaktivieren | Aeroworks-Steuereinheiten, Radarfläche, Sweep und Tracks sichtbar |
| Flywheel | Flywheel aktivieren | Native `ConsoleVisual`-Steuereinheiten und dieselben Radar-Layer sichtbar |
| ComputerControlDesk | Normales Computerpult mit Hebel/Joystick und RadarDisplay | Steuereinheiten bleiben in beiden Renderpfaden sichtbar |
| Advanced ComputerControlDesk | Advanced-Variante wiederholen | Identisches Verhalten über gemeinsamen BlockEntity-Typ |
| Linkabbau im Flywheel-Pfad | Aktiven Data Link entfernen | Flywheel-Instanzen wechseln zu Trennungs-X; keine alten Tracks |
| Trackwechsel im Flywheel-Pfad | Bewegtes Ziel beobachten | Instanzpool wird aktualisiert; Sweep bleibt animiert |

## Optionale Modkompatibilität

| Fall | Schritte | Erwartung |
| --- | --- | --- |
| Ohne Create: Radars | Runtime ohne `create_radar` starten | Kein Klassenlade- oder Mixinfehler; normale CC-Aeroworks-Funktionen verfügbar |
| Mit Create: Radars | Gepinnte Version laden | Optionaler Mixin findet exakt eine Monitor-`INSTANCEOF`-Instruktion |
| Ohne VS2/Sable | Radar betreiben | Normale Weltkoordinaten funktionieren |
| Mit VS2/Sable | bewegte Plattform oder Schiff testen | `PhysicsHandler` liefert korrektes Weltzentrum |

## Auszuführende Befehle

```bash
python3 -m py_compile tools/*.py
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
4. Spieler oder Mob erzeugen.
5. Kontaktpunkt sichtbar und aktuell.
6. Detection-Filter ändert den Kontakt.
7. Zielmarkierung funktioniert.
8. Linkblockabbau entfernt den Kontakt und die Endpointzuordnung.
9. ComputerControlDesk und Advanced ComputerControlDesk wiederholt.
10. Start ohne Create: Radars erfolgreich.
