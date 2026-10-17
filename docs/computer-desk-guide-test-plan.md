# Computerslot, Handbuch und Ponder: Testplan

## Automatisierte Integration

`./gradlew runGameTestServer` prüft echte Aeroworks-Steuereinheiten mit aktiven Mixins: alle vier Varianten und Computerfamilien beim Speichern/Laden; gemeinsame Inventarbedienung und konkurrierende Zugriffe; Transfer zum größeren Reststück und Gleichstand; einmaligen letzten Drop; Migration und Zusammenführen belegter Reihen.

## Übersicht und Bedienung (Client)

- Bei leerer Steuereinheit, einem und mehreren Modulen mit Schleichen + Rechtsklick die Übersicht öffnen. Modulliste, Vorschau, Spielerinventar und CC-Slot müssen gemeinsam sichtbar sein.
- Normalen/erweiterten Computer mit Klick, Shift-Klick, Hotbar-Taste und Drag einsetzen/entnehmen. Fremde Items ablehnen; kein Verlust und keine Duplikate, auch bei zwei Spielern.
- Von jedem Mitglied denselben Slot sehen; Terminalknopf erscheint nach dem Einsetzen. Terminal, Channels, Informationsquellen und exakte Rückkehr zur Übersicht/Modulkonfiguration prüfen.
- API-Handbuch öffnen und zurückkehren: Der Computerslot und das Inventarmenü müssen erhalten bleiben. Done und Escape schließen auch das serverseitige Menü.
- Kleine Fenster und verschiedene GUI-Skalierungen prüfen, einschließlich Item- und Button-Tooltips.

## Abbau und Datenerhalt

- Alle vier Varianten und beide Computerfamilien durch Abbau links/rechts/mittig bis zur letzten Steuereinheit prüfen. Bei Teilung gewinnt das größere Reststück, bei Gleichstand links; die letzte Einheit droppt den Computer zusätzlich.
- Computer-ID, angelegte Dateien, Label, Terminalgröße, Kapazität, Wire-Kanäle und Kanalgruppen vor/nach Transfer und Neuladen vergleichen. Module, Copycat-Material und Desk-IDs verbleiben lokal.
- Abgebrochenen Abbau, Survival-/Creative-Abbau, Create-Schraubenschlüssel und Chunkentladen prüfen. Abbruch und Chunkentladen dürfen weder verschieben noch droppen.
- Alte Blöcke und Items aller acht bisherigen Varianten übernehmen; anschließend ausschließlich normale Aeroworks-Blöcke und einen separaten Computer vorfinden.
- Zwei belegte Reihen verbinden: erweitert vor normal, bei gleicher Familie erster in Reihenfolge; übrige Computer einmalig mit ihren Daten auswerfen.

## Dokumentation und Darstellung

- Alte Computerpultitems fehlen in Creative und JEI, das Kombinationsrezept ist entfernt.
- Ponder über normale Steuereinheiten und CC:Tweaked-Computer öffnen; Einsetzen im gemeinsamen Slot und Abbauverhalten auf Deutsch und Englisch beschrieben.
- Externe Peripherals, Lua-APIs, Telemetrie sowie optionale Radar-/Drive-By-Wire-Integration mit eingesetztem Computer prüfen.
