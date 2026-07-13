# Manual Test Plan

1. Client und Dedicated Server starten; sicherstellen, dass keine Mixin- oder Registryfehler auftreten.
2. Zweistellige Displays in kleinen und großen Slots montieren; dreistellige Displays müssen kleine Slots ablehnen und in großen Slots montierbar sein. Demontage und Drops prüfen.
3. Text, ungültige Zeichen, Zahlen, negative Werte und Überlauf per Lua schreiben; Welt neu laden und Persistenz prüfen.
   Danach einzelne Randpixel sowie vollständige `7x5`-/`11x5`-Raster schreiben, Text- und Pixelmodus wechseln und Persistenz/Synchronisation prüfen.
4. Direkt angrenzenden Computer und kabelgebundenes Modem testen; Peripheral-Identität nach Chunk-Unload/Reload prüfen.
5. Alle Aeroworks-Eingabemodule bewegen; Werte und `cc_aeroworks_desk_input` auf zwei angehängten Computern prüfen.
6. Fallback-Rendering bei deaktiviertem Flywheel und Flywheel-Rendering bei aktiviertem Backend prüfen: Desk-Rotation, Decke, Licht, Rückseite und Z-Fighting.
7. Tests 2 bis 6 auf einem bewegten Sable-Schiff wiederholen.
8. Den Modulbildschirm eines Levers öffnen und das vorhandene Modussymbol anklicken. Die Folge muss `Buttons -> Analog -> Kombiniert -> Buttons` sein. Im Kombiniert-Zustand im mittleren Feld eine Taste erfassen, per Rechtsklick löschen und neu erfassen. Bildschirm erneut öffnen und Persistenz prüfen.
9. Dasselbe für alle vier Throttle-Quadrant-Achsen `red`, `amber`, `green`, `blue` mit unterschiedlichen Tasten wiederholen. Der zusätzliche Zustand darf bei nicht unterstützten Modulen nicht angeboten werden.
10. Beim Joystick `x` und `y` getrennt auf `Kombiniert` stellen und unterschiedliche Tasten erfassen. Bei `x` darf ausschließlich Maus X links/rechts verändern; bei `y` ausschließlich Maus Y vor/zurück.
11. Auf Lever, Joystick beziehungsweise Throttle Quadrant blicken und die für die gewünschte Achse konfigurierte Taste halten: Kamera, Mausakkumulator, Grenzen und Paketrate prüfen. Ohne gültiges Ziel oder bei `Buttons`/`Analog` darf nichts einfrieren. Beim Quadranten muss jede Taste ausschließlich ihren zugeordneten Hebel verändern.
12. Combined-Modus durch Menü, Fokusverlust, Tod, Dimensionwechsel, Desk-/Modulabbau und Trennung abbrechen; Kamera muss sofort frei sein.
13. CC:Tweaked 1.119.0 und 1.120.0 getrennt prüfen.
14. Aeroworks-Creative-Tab öffnen: die Schilder `Aeroworks` und `CC-Aeroworks`, Scrollposition und Itemreihenfolge prüfen. In den vollständig deckenden Kategoriezeilen dürfen keine Inventar-Slotgrafiken sichtbar sein; Displayitems dürfen nicht doppelt erscheinen. Das `CC-Aeroworks API-Handbuch` muss im zweiten Abschnitt liegen und per Rechtsklick die eigene API-Dokumentation öffnen. Alle sieben Kapitel, Codeblöcke, Hinweise, Sidebar, Scrollen, Vor/Zurück, Fertig und Escape bei kleiner sowie großer GUI-Skalierung prüfen. Der Hintergrund muss gleichmäßig abgedunkelt bleiben; kein Blur-Shader darf Text oder Oberfläche weichzeichnen.
15. Client und Dedicated Server jeweils mit und ohne Drive By Wire 0.2.9 starten. Mit Drive By Wire zusätzlich Aeroworks-Kanalwahl/-veröffentlichung prüfen; ohne die optionale Mod darf keine Abhängigkeitswarnung entstehen.
