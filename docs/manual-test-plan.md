# Manual Test Plan

1. `test`, `build`, Client und Dedicated Server mit den Ziel-JARs starten.
2. Normales Pult + normaler Computer craften; Advanced-Pult + Advanced-Computer craften.
3. Computer-ID, Label, Dateien und montierte Aeroworks-Module durch Crafting, Abbau, Platzieren und Neustart erhalten.
4. Computerpult links, mittig und rechts in einer Reihe testen.
5. Von jedem Mitglied mit Schleichen + leerer Haupthand das Terminal öffnen.
6. Zwei Computer verbinden: normale Pulte müssen Konflikt melden; beide Computer müssen direkt erreichbar bleiben.
7. Reihe trennen und verbinden; Snapshot und Terminalbesitzer müssen sofort stimmen.
8. Unterschiedliche Ausrichtung, Höhe, Chunkgrenze und mehr als 64 Pulte prüfen.
9. `aeroworks.getDesks()` sowie alle Modul-, Input-, Text- und Pixelmethoden testen.
10. Eingabeereignisse einschließlich Modul- und Kanalentfernung prüfen.
11. Externes `cc_aeroworks_control_desk`-Peripheral und Create Display Link an beiden Computervarianten testen.
12. Redstone, gebündeltes Redstone und fremde Peripherals an allen freien Seiten testen.
13. Fallback-Rendering und Flywheel aktiv testen.
14. Gesamten Ablauf auf einem Sable-Schiff wiederholen.
15. CC:Tweaked 1.119.0 und 1.120.0 getrennt testen.
16. Mit und ohne Drive By Wire starten.
17. Creative Tab, Modelle, Drops, Übersetzungen und Ingame-Handbuch prüfen.
