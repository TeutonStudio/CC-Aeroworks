# Radar Data Link regression plan

Diese Matrix prüft den Fix auf Basis von Commit `v7` mit Create: Radars `0.4.4-1.21.1`.

## Native Create:-Radars-Verbindungen

| Aufbau | Schritte | Erwartung |
| --- | --- | --- |
| Network Controller → Monitor | Network Controller mit Data Link wählen, danach Monitor anklicken | Create: Radars platziert und registriert den nativen Link; keine CC-Aeroworks-Auswahlmeldung |
| Network Controller → Radar | Network Controller wählen, danach Radar anklicken | Native Radargruppe wird verbunden |
| Waffenhalterung → Controller | Halterung wählen, danach Yaw-, Pitch- oder Feuercontroller anklicken | Native Waffenverbindung bleibt unverändert |
| Native Auswahl abbrechen | Nach dem ersten nativen Klick schleichen und rechtsklicken | Create: Radars löscht seine Auswahl |

## CC-Aeroworks-Radarpult

| Aufbau | Schritte | Erwartung |
| --- | --- | --- |
| Netz ohne Computer | Genau eine Radaranzeige einsetzen; unbenutzten Data Link auf Monitor, danach Pultseite klicken | Link wird platziert und Anzeige empfängt Tracks |
| Netz mit einem Computer | Wie oben, Computerpult an linker, mittlerer und rechter Position | Identisches Routing für jede Computerposition |
| Anzeige in anderem Pult | Link an Pult A, Anzeige in verbundenem Pult B | Snapshot wird zu Pult B geroutet |
| Kleine Anzeige außen | Kleine Anzeige im linken oder rechten Socket | Anzeige wird erkannt |
| Kleine Anzeige mittig | Kleine Anzeige im großen Socket | Anzeige wird erkannt |
| Große Anzeige mittig | Große Anzeige im großen Socket | Anzeige wird erkannt |
| Keine Anzeige | Monitor auswählen, danach Pultseite klicken | Verständliche Meldung; kein Link wird platziert |
| Zwei Anzeigen | Monitor auswählen, danach Pultseite klicken | Mehrdeutigkeitsmeldung; keine zufällige Auswahl |
| Zwei Computer | Monitor auswählen, danach Pultseite klicken | Konfliktmeldung; kein Routing |
| Zwei Data-Link-Stacks | Monitor mit Stack A auswählen, danach Stack B verwenden | Stack B besitzt keine Auswahl von Stack A |
| CC-Auswahl abbrechen | Monitor mit unbenutztem Stack wählen, dann schleichen und rechtsklicken | Nur die CC-Auswahl dieses Stacks wird gelöscht |
| Ungültiger Monitor | Monitor wählen, Monitor entfernen, danach Pult klicken | Auswahl wird verworfen; kein Link wird platziert |
| Fehlkonfiguration | Platzierung oder Zielsetzung absichtlich verhindern | Linkblock wird entfernt und Item im Überlebensmodus erstattet |

## Laufzeitbeobachtung

- Der Data Link muss mit seiner Quellseite auf das angeklickte Pult zeigen.
- `getTargetPosition()` muss dem Controllerblock des gewählten Monitors entsprechen.
- Ein verbundener Monitor aktualisiert die Anzeige spätestens nach fünf Ticks.
- Nach Entfernen des Data Links zeigt die Anzeige spätestens nach 20 Ticks wieder `X`.
- Client und dedizierter Server dürfen keine Auswahl zwischen Spielern oder Itemstacks teilen.
