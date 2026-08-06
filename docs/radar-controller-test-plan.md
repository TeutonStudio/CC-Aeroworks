# Radar Network Controller regression plan

Diese Matrix prüft die automatische Erkennung eines direkt am Aeroworks-Pult platzierten Network Controllers und die direkte Create:-Radars-Monitoroberfläche mit Create: Radars `0.4.9.4-1.21.1`.

## Native Create:-Radars-Verbindungen

| Aufbau | Schritte | Erwartung |
| --- | --- | --- |
| Network Controller → Radar | Network Controller mit Data Link wählen, danach Radar anklicken | Native Radargruppe wird verbunden |
| Network Controller → Monitor | Network Controller wählen, danach Monitor anklicken | Native Monitorverbindung bleibt unverändert |
| Waffenhalterung → Controller | Halterung wählen, danach Yaw-, Pitch- oder Feuercontroller anklicken | Native Waffenverbindung bleibt unverändert |
| Data Link auf Pult | Network Controller wählen, danach Pult anklicken | Create: Radars behandelt das Pult nativ; CC-Aeroworks übernimmt den Klick nicht |

## Automatische Pultquelle

| Aufbau | Schritte | Erwartung |
| --- | --- | --- |
| Einzelnes Pult | RadarDisplay einsetzen; verbundenen Network Controller direkt daneben platzieren | Radar erscheint ohne Data-Link-Klick auf das Pult |
| Netz ohne Computer | Controller an irgendein Pult, Display an ein anderes | Snapshot wird ohne eingebetteten Computer verteilt |
| Netz mit einem Computer | Computerpult links, mittig und rechts testen | Identische Radarroute für jede Computerposition |
| Alle sechs Seiten | Controller nacheinander an jede direkte Nachbarposition eines Pults setzen | Jede direkte Seite wird erkannt |
| Diagonaler Controller | Controller nur diagonal zum Pult platzieren | Keine Quelle; Anzeige zeigt `X` |
| Abstand | Einen Luftblock zwischen Controller und Pult lassen | Keine Quelle; Anzeige zeigt `X` |
| Mehrere Anzeigen | Kleine und große RadarDisplays an mehreren Pulten | Alle Anzeigen zeigen dieselbe Quelle |
| Keine Anzeige | Controller direkt am Netz, aber kein RadarDisplay | Kein Fehler; normale Pulte ignorieren den Snapshot beim Rendern |
| Derselbe Controller an zwei Pulten | Controller so platzieren, dass er zwei Pulte berührt | Controller wird anhand seiner Position nur einmal gezählt |
| Zwei Controller | Zwei verschiedene Network Controller an das Pultnetz setzen | Mehrdeutige Quelle; alle Anzeigen zeigen `X` |
| Controller entfernt | Aktive Quelle abbauen | Spätestens nach 20 Ticks `X` |
| Radar entfernt | Radar aus dem nativen Controller-Netz entfernen | Spätestens nach fünf Ticks `X` |
| Zwei Computer | Konfliktnetz mit RadarDisplay und Controller | Anzeige bleibt getrennt |
| Teilweise geladen | Einen Teil des Pultnetzes entladen | Anzeige bleibt getrennt oder wird nach Ablauf alt |
| Überlanges Netz | Mehr als 64 Pulte verbinden | Anzeige bleibt getrennt |

## Direkte Monitoroberfläche

| Element | Schritte | Erwartung |
| --- | --- | --- |
| Hintergrund | Aktiven Radar ohne Kontakte verbinden | Hintergrundfüllung und Radarkreis aus Create: Radars sind sichtbar |
| Sweep | Aktive Anzeige mehrere Sekunden beobachten | Sweep rotiert kontinuierlich und unabhängig von der Display-Pixelauflösung |
| Normale Entität | Tier oder Hostile im Radarbereich erzeugen | `entity_hitbox`-Sprite erscheint an der kontinuierlich projizierten Position |
| Spieler | Spieler im Radarbereich | `player`-Sprite erscheint |
| Projektil | Projektil durch den Radarbereich bewegen | `projectile`-Sprite folgt dem Track |
| Contraption oder Sable-Schiff | Entsprechenden Track erzeugen | `contraption_hitbox`-Sprite erscheint |
| Zielauswahl | Track am Network Controller auswählen | `target_selected` liegt über dem ausgewählten Track |
| Außerhalb der Reichweite | Track aus dem Radarkreis bewegen | Track wird außerhalb der Kreisfläche nicht gerendert |
| Kleine und große Anzeige | Beide Größen nebeneinander einsetzen | Gleiche Radardaten, passend auf die jeweilige Modulfläche skaliert |
| Klassischer Renderer | Flywheel-Visualisierung deaktivieren | Identische Radar-Layer und Trackpositionen |
| Flywheel | Flywheel-Visualisierung aktivieren | Identische Radar-Layer und Trackpositionen; Sweep rotiert |

## Laufzeitbeobachtung

- Es existiert kein CC-Aeroworks-Mixin am Create:-Radars-Data-Link-Gegenstand.
- Am Pult wird keine Controllerposition gespeichert.
- Der Network Controller stößt die Aktualisierung in seinem bereits vorhandenen Fünf-Tick-Zyklus an.
- Seine sechs Nachbarpositionen werden auf Pulte geprüft; danach werden alle Pultnachbarn des aufgelösten Netzes nach Controllern durchsucht.
- Der Controller liefert den bereits nativ verbundenen Radar direkt; kein Monitorblock ist beteiligt.
- Reichweite, Radarzentrum, Auswahl und bis zu 256 Tracks samt Spritekategorie werden aktualisiert.
- RadarDisplays werden nicht über `DeskDisplayPixels` oder `RadarDisplayRaster` gerendert.
- Klassischer Renderer und Flywheel verwenden dieselben `RadarSurfaceRenderer`-Elemente.
- Nach Entfernen des Radars zeigt jede Anzeige spätestens nach fünf Ticks `X`; nach Entfernen des Controllers spätestens nach 20 Ticks.
