# Radar Network Controller regression plan

Diese Matrix prüft die direkte Verbindung `Network Controller → Aeroworks-Steuerungspult` mit Create: Radars `0.4.9.4-1.21.1`.

## Native Create:-Radars-Verbindungen

| Aufbau | Schritte | Erwartung |
| --- | --- | --- |
| Network Controller → Radar | Network Controller mit Data Link wählen, danach Radar anklicken | Native Radargruppe wird verbunden |
| Network Controller → Monitor | Network Controller wählen, danach Monitor anklicken | Native Monitorverbindung bleibt unverändert |
| Waffenhalterung → Controller | Halterung wählen, danach Yaw-, Pitch- oder Feuercontroller anklicken | Native Waffenverbindung bleibt unverändert |
| Native Auswahl abbrechen | Nach dem ersten nativen Klick schleichen und rechtsklicken | Create: Radars löscht seine Auswahl |

## Direkte Pultverbindung

| Aufbau | Schritte | Erwartung |
| --- | --- | --- |
| Einzelnes Pult | RadarDisplay einsetzen; Network Controller wählen; Pult anklicken | Controllerposition wird gespeichert, kein Data-Link-Block wird platziert |
| Netz ohne Computer | Mehrere Pulte und mindestens ein RadarDisplay | Snapshot wird ohne eingebetteten Computer verteilt |
| Netz mit einem Computer | Computerpult links, mittig und rechts testen | Identische Radarroute für jede Computerposition |
| Anzeige in anderem Pult | Controller an Pult A binden, RadarDisplay in Pult B | Anzeige in Pult B erhält Tracks |
| Mehrere Anzeigen | Kleine und große RadarDisplays an mehreren Pulten | Alle Anzeigen zeigen dieselbe Quelle; keine Mehrdeutigkeitsmeldung |
| Keine Anzeige | Controller wählen, Pultnetz ohne RadarDisplay anklicken | Verständliche Meldung; keine Verbindung wird gespeichert |
| Quelle ersetzen | Controller A verbinden, danach Controller B an dasselbe Netz binden | Nur Controller B bleibt als Quelle aktiv |
| Zwei Computer | Controller auswählen und Konfliktnetz anklicken | Konfliktmeldung; keine Verbindung |
| Teilweise geladen | Einen Teil des Pultnetzes entladen | Verbindung wird abgewiesen oder bestehende Anzeige wird nach Ablauf alt |
| Überlanges Netz | Mehr als 64 Pulte verbinden | Verbindung wird abgewiesen |

## Laufzeitbeobachtung

- Der Klick auf das Pult darf keinen Data-Link-Block platzieren und kein Item verbrauchen.
- Auf dem Data-Link-Gegenstand wird nur `SelectedFiltererPos` entfernt.
- Das Quellpult speichert Position und Dimension des Network Controllers.
- Der Controller liefert den bereits nativ verbundenen Radar direkt; kein Monitorblock ist beteiligt.
- Reichweite, Radarzentrum, Auswahl und bis zu 256 Tracks werden alle fünf Ticks aktualisiert.
- Nach Entfernen des Controllers oder Radars zeigt jede Anzeige spätestens nach 20 Ticks `X`.
- Client und dedizierter Server dürfen keine Auswahlzustände zwischen Spielern oder Itemstacks teilen.
