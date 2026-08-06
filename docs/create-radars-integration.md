# Create: Radars integration

Die Integration ist optional und wird nur aktiv, wenn Create: Radars mit der Mod-ID `create_radar` geladen ist. CC-Aeroworks registriert die Item-IDs immer stabil, blendet die Radar-Displays ohne Create: Radars jedoch aus dem Aeroworks-Creative-Tab aus und lädt ihre Rezepte nur unter der NeoForge-Bedingung `neoforge:mod_loaded`.

## Displays

- `cc_aeroworks:small_radar_display` passt in kleine und große Aeroworks-Sockets.
- `cc_aeroworks:large_radar_display` passt ausschließlich in große Aeroworks-Sockets.
- Beide verwenden die konfigurierte kleine beziehungsweise große Pixelauflösung.

## Aufbau

1. Radar-Display in das Steuerungspult einsetzen.
2. Einen Create: Radars Data Link so ausrichten, dass dessen Quellseite direkt auf genau dieses Steuerungspult zeigt.
3. Das Ziel des Data Links auf den Controller eines Create: Radars Monitors setzen.
4. Den Monitor mit einem funktionierenden Create: Radars Radarnetzwerk verbinden.

Der Data Link ist eine echte Laufzeitbedingung. CC-Aeroworks sucht keine Radarblöcke in der Welt und übernimmt keine Tracks ohne einen passend ausgerichteten Data Link. Ein `X` auf dem Display bedeutet, dass keine aktuelle Verbindung verfügbar ist.

## Synchronisierung

Der optionale Pseudo-Mixin beobachtet die Rückkehrpunkte von `DataLinkBlockEntity.updateGatheredData`. Dadurch funktioniert die Brücke auch dann, wenn Create: Radars das Aeroworks-Pult nicht als eigene `DataPeripheral` kennt. Alle fünf Spielticks wird ein flüchtiger Snapshot an das Pult übertragen. Er enthält Radarzentrum, Reichweite, ausgewählte Track-ID und höchstens die 256 nächstgelegenen Tracks. Die Daten werden nur in Client-Updatepakete geschrieben und nicht im Weltstand gespeichert. Nach 20 Ticks ohne Aktualisierung gilt der Snapshot als veraltet.

Die Integration referenziert keine Create:-Radars-Klasse in normalen Methodensignaturen. Ohne die optionale Mod wird der `@Pseudo`-Mixin übersprungen und der Reflexionsadapter nie aufgerufen.

## Manuelle Prüfung

- Start ohne Create: Radars: keine Radar-Items im Creative-Tab, keine Radarrezepte, kein Mixinfehler.
- Start mit Create: Radars 0.4.4 für Minecraft 1.21.1: beide Items und Ponder-Szene sichtbar.
- Display ohne Data Link: `X`.
- Data Link auf falsches Ziel: `X`.
- Data Link auf unverbundenen Monitor: `X`.
- Aktiver Monitor: Zentrum und Kontakte sichtbar; ausgewählter Kontakt als Kreuz markiert.
- Data Link entfernen: spätestens nach 20 Ticks wieder `X`.
- Fallback-Renderer und Flywheel-Renderer getrennt prüfen.
