# Radar Network Controller regression plan

Diese Matrix prüft die automatische Erkennung eines direkt am Aeroworks-Pult platzierten Network Controllers mit Create: Radars `0.4.9.4-1.21.1`.

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
| Keine Anzeige | Controller direkt am Netz, aber kein RadarDisplay | Kein unnötiger Snapshotversand und kein Fehler |
| Derselbe Controller an zwei Pulten | Controller so platzieren, dass er zwei Pulte berührt | Controller wird anhand seiner Position nur einmal gezählt |
| Zwei Controller | Zwei verschiedene Network Controller an das Pultnetz setzen | Mehrdeutige Quelle; alle Anzeigen zeigen `X` |
| Controller entfernt | Aktive Quelle abbauen | Spätestens nach 20 Ticks `X` |
| Radar entfernt | Radar aus dem nativen Controller-Netz entfernen | Spätestens nach fünf Ticks `X` |
| Zwei Computer | Konfliktnetz mit RadarDisplay und Controller | Anzeige bleibt getrennt |
| Teilweise geladen | Einen Teil des Pultnetzes entladen | Anzeige bleibt getrennt oder wird nach Ablauf alt |
| Überlanges Netz | Mehr als 64 Pulte verbinden | Anzeige bleibt getrennt |

## Laufzeitbeobachtung

- Es existiert kein CC-Aeroworks-Mixin am Create:-Radars-Data-Link-Gegenstand.
- Am Pult wird keine Controllerposition gespeichert.
- Der Network Controller stößt die Aktualisierung in seinem bereits vorhandenen Fünf-Tick-Zyklus an.
- Seine sechs Nachbarpositionen werden auf Pulte geprüft; danach werden alle Pultnachbarn des aufgelösten Netzes nach Controllern durchsucht.
- Der Controller liefert den bereits nativ verbundenen Radar direkt; kein Monitorblock ist beteiligt.
- Reichweite, Radarzentrum, Auswahl und bis zu 256 Tracks werden aktualisiert.
- Nach Entfernen des Radars zeigt jede Anzeige spätestens nach fünf Ticks `X`; nach Entfernen des Controllers spätestens nach 20 Ticks.
