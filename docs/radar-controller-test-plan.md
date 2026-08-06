# Radar Network Controller regression plan

Diese Matrix prüft die automatische Erkennung eines direkt am Aeroworks-Pult platzierten Network Controllers, die synchronisierte Fehlerdiagnose und die direkte Create:-Radars-Monitoroberfläche mit Create: Radars `0.4.9.4-1.21.1`.

## Start- und Ressourcenmatrix

| Aufbau | Schritte | Erwartung |
| --- | --- | --- |
| Ohne Create: Radars | Entwicklungsclient ohne `create_radar` starten | Kein optionaler Mixinfehler; keine Radaritems oder Rezepte; die leere `monitor_sprite`-Atlasquelle erzeugt keinen Absturz |
| Mit Create: Radars | Vollständige lokale Runtime starten und Ressourcen neu laden | Keine Missing-Texture-Warnung für `create_radar:monitor_sprite/*` |
| Ressourcenreload | Bei sichtbarem RadarDisplay `F3+T` ausführen | Radaroberfläche erscheint nach Reload erneut; keine schwarz-pinke Fläche |
| Dedicated Server | Serverprofil ohne Clientklassen starten | Atlas-, Renderer- und Spritecode werden nicht auf dem physischen Server geladen |

## Native Create:-Radars-Verbindungen

| Aufbau | Schritte | Erwartung |
| --- | --- | --- |
| Network Controller → Radar | Network Controller mit Data Link wählen, danach Radar anklicken | Native Radargruppe wird verbunden |
| Network Controller → Monitor | Network Controller wählen, danach Monitor anklicken | Native Monitorverbindung bleibt unverändert |
| Waffenhalterung → Controller | Halterung wählen, danach Yaw-, Pitch- oder Feuercontroller anklicken | Native Waffenverbindung bleibt unverändert |
| Data Link auf Pult | Network Controller wählen, danach Pult anklicken | Create: Radars behandelt das Pult nativ; CC-Aeroworks übernimmt den Klick nicht |

## Automatische Pultquelle und Status

| Aufbau | Schritte | Erwartung |
| --- | --- | --- |
| Einzelnes Pult | RadarDisplay einsetzen; verbundenen Network Controller direkt daneben platzieren | Status `ACTIVE`; Radar erscheint ohne Data-Link-Klick auf das Pult |
| Netz ohne Computer | Controller an irgendein Pult, Display an ein anderes | Snapshot wird ohne eingebetteten Computer verteilt |
| Netz mit einem Computer | Computerpult links, mittig und rechts testen | Identische Radarroute für jede Computerposition |
| Alle sechs Seiten | Controller nacheinander an jede direkte Nachbarposition eines Pults setzen | Jede direkte Seite wird erkannt |
| Alle vier Pultausrichtungen | Einen festen Track nördlich des Radars halten und dasselbe Pult nacheinander nach Norden, Osten, Süden und Westen ausrichten | Der Track wird jeweils in die lokale Bildschirmachse projiziert; Welt- und Bildrichtung bleiben konsistent |
| Diagonaler Controller | Controller nur diagonal zum Pult platzieren | Keine neue Quelle; vorhandener Snapshot wird nach spätestens 20 Ticks `STALE` |
| Abstand | Einen Luftblock zwischen Controller und Pult lassen | Keine neue Quelle; Anzeige wird nach spätestens 20 Ticks `STALE` |
| Mehrere Anzeigen | Kleine und große RadarDisplays an mehreren Pulten | Alle Anzeigen zeigen dieselbe Quelle |
| Keine Anzeige | Controller direkt am Netz, aber kein RadarDisplay | Kein Clientupdate an normale Pulte; kein Renderfehler |
| Derselbe Controller an zwei Pulten | Controller so platzieren, dass er zwei Pulte berührt | Controller wird anhand seiner Position nur einmal gezählt |
| Zwei Controller | Zwei verschiedene Network Controller an das Pultnetz setzen | Status `MULTIPLE_CONTROLLERS`; alle Anzeigen zeigen `X` |
| Controller entfernt | Aktive Quelle abbauen | Spätestens nach 20 Ticks Status `STALE` und `X` |
| Radar nicht verbunden | Data Link zum Radar entfernen | Status `RADAR_NOT_LINKED`; Anzeige zeigt `X` |
| Radarposition entladen | Radar-Chunk entladen, Controller und Pult geladen lassen | Status `RADAR_NOT_LOADED`; Anzeige zeigt `X` |
| Radar gestoppt | Verbundenen Radar abschalten | Status `RADAR_NOT_RUNNING`; Anzeige zeigt `X` |
| Reichweite null | Radarzustand mit Reichweite 0 herstellen | Status `INVALID_RANGE`; Anzeige zeigt `X` |
| Zwei Computer | Konfliktnetz mit RadarDisplay und Controller | Status `NETWORK_UNAVAILABLE`; Anzeige bleibt getrennt |
| Teilweise geladen | Einen Teil des Pultnetzes entladen | Status `NETWORK_UNAVAILABLE` oder nach Ausfall des Tickgebers `STALE` |
| Überlanges Netz | Mehr als 64 Pulte verbinden | Status `NETWORK_UNAVAILABLE`; Anzeige bleibt getrennt |
| Inkompatible Create:-Radars-API | Testweise unterstützte Feld- oder Methodensignatur verändern | Status `API_INCOMPATIBLE`; Ursache einmalig im Log, keine Fünf-Tick-Logflut |

## Controller-Platzierung am ComputerControlDesk

| Aufbau | Schritte | Erwartung |
| --- | --- | --- |
| Rückseite frei | Network Controller in der Haupthand halten und die Oberseite des ComputerControlDesk anklicken | Der Klick wird gezielt auf die freie Rückseite umgeleitet; der Controller steht nicht auf dem Pult |
| Rückseite belegt | Rückseite blockieren und dieselbe Oberseiteninteraktion wiederholen | Keine Umleitung; das native Platzierungsverhalten bleibt zuständig und es wird kein vorhandener Block ersetzt |
| Seitenklick | Network Controller direkt auf eine horizontale Pultseite setzen | Keine Umleitung; normales Minecraft-/Create:-Radars-Verhalten |
| Normales Aeroworks-Pult | Oberseite eines nicht eingebetteten Pults anklicken | Keine CC-Aeroworks-Umleitung; nur ComputerControlDesk erhält die Platzierungshilfe |
| Anderer Gegenstand | Beliebigen anderen Block oder ein Modul auf der Oberseite verwenden | Keine Umleitung und keine veränderte Aeroworks-Interaktion |

## Direkte Monitoroberfläche

| Element | Schritte | Erwartung |
| --- | --- | --- |
| Hintergrund | Aktiven Radar ohne Kontakte verbinden | Originale Hintergrundfüllung und Radarkreis sind ohne Missing-Texture sichtbar |
| Sweep | Aktive Anzeige mehrere Sekunden beobachten | Sweep rotiert kontinuierlich und unabhängig von der Display-Pixelauflösung |
| Normale Entität | Tier oder Hostile im Radarbereich erzeugen | `entity_hitbox`-Sprite erscheint an der kontinuierlich projizierten Position |
| Spieler | Spieler im Radarbereich | `player`-Sprite erscheint |
| Projektil | Projektil durch den Radarbereich bewegen | `projectile`-Sprite folgt dem Track |
| Contraption | Create-Contraption im Radarbereich erzeugen | `contraption_hitbox`-Sprite erscheint |
| VS2/Sable-Schiff | Schiffstrack mit Kategorie `VS2` erzeugen | `contraption_hitbox`-Sprite erscheint, nicht `entity_hitbox` |
| Zielauswahl | Track am Network Controller auswählen | `target_selected` liegt über dem ausgewählten Track |
| Außerhalb der Reichweite | Track aus dem Radarkreis bewegen | Track wird außerhalb der Kreisfläche nicht gerendert |
| Kleine und große Anzeige | Beide Größen nebeneinander einsetzen | Gleiche Radardaten, passend auf die jeweilige Modulfläche skaliert |
| Klassischer Renderer | Flywheel-Visualisierung deaktivieren | Hintergrund/Sweep nutzen Translucent; Tracks und Statusmarkierung Cutout |
| Flywheel | Flywheel-Visualisierung aktivieren | Identische Layer und Trackpositionen; Sweep rotiert |
| Heartbeat ohne Inhaltsänderung | Leeren aktiven Radar mindestens 60 Ticks beobachten | Anzeige bleibt frisch; kein periodischer Instanz-Neuaufbau nur durch `updatedAt` |

## ComputerControlDesk

| Aufbau | Schritte | Erwartung |
| --- | --- | --- |
| Nativer Delegate | RadarDisplay am ComputerControlDesk einsetzen | Aeroworks-Modulgehäuse und Radaroberfläche sind sichtbar |
| Kleines und großes Modul | Beide Displaygrößen in passende Sockets einsetzen | Gehäuse, Oberfläche und Skalierung stimmen mit normalen Pulten überein |
| Delegate-Fehler | `ConsoleRenderer`-Konstruktor im Test absichtlich inkompatibel machen | Einmalige Fehlermeldung; display-only fallback zeigt Text-, Pixel- und Radaroberflächen weiter |
| Delegate-Fehler, Modulgehäuse | Obigen Fehlerfall beobachten | Native animierte Modulgeometrie darf als bekannte Delegate-Grenze fehlen; dieser Zustand blockiert die Freigabe gegen eine inkompatible Aeroworks-Version |
| Flywheel an/aus | Beide Modi am ComputerControlDesk prüfen | Radaroberfläche bleibt sichtbar; keine doppelte Oberfläche beim vorhandenen Delegate |

## Synchronisierung und Last

| Fall | Beobachtung | Erwartung |
| --- | --- | --- |
| Unveränderter Snapshot | Netzwerkpakete beziehungsweise `notifyUpdate()` protokollieren | Höchstens ein Heartbeat je 15 Ticks und Zielpult |
| Bewegte Tracks | Mehrere Kontakte bewegen | Inhaltsänderungen werden im Fünf-Tick-Zyklus synchronisiert |
| 256 Tracks | Mindestens 256 Kontakte bereitstellen | Höchstens 256 nächstgelegene Tracks werden übertragen |
| Mehr als 256 Tracks | Zusätzliche Kontakte außerhalb/innerhalb erzeugen | Liste bleibt auf 256 begrenzt und wird nach Entfernung neu nach Entfernung sortiert |
| Pult ohne RadarDisplay | Controller am großen Netz betreiben | Dieses Pult erhält keine Radar-Snapshot-Updates |
| Status bleibt gleich | Dauerhaft gestoppten Radar beobachten | Statusursache wird nicht alle fünf Ticks erneut geloggt |
| Statuswechsel | Radar stoppen, starten und Link entfernen | Je tatsächlichem Übergang genau eine Statusmeldung |

## Laufzeitbeobachtung

- Es existiert kein CC-Aeroworks-Mixin am Create:-Radars-Data-Link-Gegenstand.
- Am Pult wird keine Controllerposition gespeichert.
- Der öffentliche Controller-Tick ruft den Adapter auf; der vollständige CC-Aeroworks-Scan läuft höchstens einmal je fünf Ticks.
- Seine sechs Nachbarpositionen werden auf Pulte geprüft; danach werden alle Pultnachbarn des aufgelösten Netzes nach Controllern durchsucht.
- Der Controller liefert den bereits nativ verbundenen Radar direkt; kein Monitorblock ist beteiligt.
- Reichweite, Radarzentrum, Auswahl und bis zu 256 Tracks samt Spritekategorie werden aktualisiert.
- RadarDisplays werden nicht über `DeskDisplayPixels` oder `RadarDisplayRaster` gerendert.
- Die originalen Monitor-Sprites werden über `assets/minecraft/atlases/blocks.json` aus `textures/monitor_sprite` in den Blockatlas aufgenommen.
- Klassischer Renderer und Flywheel verwenden dieselben `RadarSurfaceRenderer`-Elemente.
- Die Pultausrichtung ist Bestandteil des Oberflächenzustands und des Flywheel-Schlüssels.
- `updatedAt` ist nicht Bestandteil des Flywheel-Inhaltsschlüssels.
- Die Rückseitenplatzierung greift ausschließlich beim ComputerControlDesk, dem Network Controller und einem Oberseitenklick mit freiem Zielblock.
- Nach Entfernen des Radars zeigt jede Anzeige spätestens nach fünf Ticks einen konkreten getrennten Status; nach Entfernen des Controllers spätestens nach 20 Ticks `STALE`.
