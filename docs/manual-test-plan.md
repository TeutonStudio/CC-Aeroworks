# Manual Test Plan

1. Client und Dedicated Server starten; sicherstellen, dass keine Mixin- oder Registryfehler auftreten.
2. Zweistellige Displays in kleinen und großen Slots montieren; dreistellige Displays müssen kleine Slots ablehnen und in großen Slots montierbar sein. Demontage und Drops prüfen.
3. Text, ungültige Zeichen, Zahlen, negative Werte und Überlauf per Lua schreiben; Welt neu laden und Persistenz prüfen.
4. Direkt angrenzenden Computer und kabelgebundenes Modem testen; Peripheral-Identität nach Chunk-Unload/Reload prüfen.
5. Alle Aeroworks-Eingabemodule bewegen; Werte und `cc_aeroworks_desk_input` auf zwei angehängten Computern prüfen.
6. Fallback-Rendering bei deaktiviertem Flywheel und Flywheel-Rendering bei aktiviertem Backend prüfen: Desk-Rotation, Decke, Licht, Rückseite und Z-Fighting.
7. Tests 2 bis 6 auf einem bewegten Sable-Schiff wiederholen.
8. Standardtaste `K` für `Kombiniert (Lever, Maus Y)` drücken: ohne gültiges Ziel darf nichts einfrieren. Mit Lever müssen Kamera, Y-Akkumulator, Grenzen und Paketrate stimmen; anschließend eine abweichende Tastenbelegung testen.
9. Combined-Modus durch Menü, Fokusverlust, Tod, Dimensionwechsel, Desk-/Lever-Abbau und Trennung abbrechen; Kamera muss sofort frei sein.
10. CC:Tweaked 1.119.0 und 1.120.0 getrennt prüfen.
11. Aeroworks-Creative-Tab öffnen: die Schilder `Aeroworks` und `CC-Aeroworks`, Leerzeilen, Scrollposition und Itemreihenfolge prüfen. Insbesondere dürfen Displayitems nicht doppelt erscheinen.
12. Client und Dedicated Server jeweils mit und ohne Drive By Wire 0.2.9 starten. Mit Drive By Wire zusätzlich Aeroworks-Kanalwahl/-veröffentlichung prüfen; ohne die optionale Mod darf keine Abhängigkeitswarnung entstehen.
