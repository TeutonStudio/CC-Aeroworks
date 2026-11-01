# Releaseprüfung 1.5.3

Datum: 1. November 2026. Minecraft 1.21.1, NeoForge 21.1.234, CC:Tweaked 1.120.2,
Sable 2.0.5, Create: Aeroworks 1.5.0 und TeutonLib 1.5.0.

## Änderungen

- Kompaktes Steuerungs-HUD für alle eingeblendeten Steuerkanäle des aktiven Multiblocks.
- Ein Widget pro montiertem Steuerobjekt einschließlich angehängter Knöpfe und unabhängiger Achsen.
- Kenney Input Prompts 1.5A unter CC0 für Tastatur, Maus, einzelne Achsen, Controller und rohe Joysticks.
- Negative Tastensymbole statt ausgeschriebenem Halten, kurze Positionsangaben, mehrspaltige
  Knopffelder, kleinere Achsenfelder und gemeinsame Symbolzeilen.
- Display-Zeiger mit korrektem Seitenverhältnis, Pixelbereichen und aktueller relativer Position.
- Kanonische native Steuergruppen auf Client und Server: vollständige Pultreihen über integrierte
  Create-Steuerungen hinweg und getrennte benachbarte UUID-Reihen.

## Bestanden

- Release-Build: `./gradlew test build runGameTestServer --offline` mit isoliertem GameTest-Verzeichnis.
- Alle 167 Unit-Tests und alle 31 erforderlichen GameTests erfolgreich.
- Alle 30 Python-Verträge aus der Verify-Workflow-Datei sowie beide Lua-Tests erfolgreich.
- Python-Werkzeuge kompilieren; Gradle-Bootstrap mit `javac --release 21` kompiliert.
- `git diff --check` ohne Fehler.
- Release-JAR enthält Version 1.5.3, 392 originale PNG-Icons und den CC0-Lizenzhinweis.
- Die CI stellt die TeutonLib-Prüfquellen über einen eigenen Checkout bereit, gepinnt auf
  `724674c7ac3ca69d232a8c250256e05a17a399b6` (1.5.0). Beide Display-Prüfskripte akzeptieren
  `TEUTONLIB_SOURCE_DIR`, sodass auf dem Runner kein lokales Nachbarprojekt vorausgesetzt wird.

## Artefakt

`build/libs/cc-aeroworks-1.5.3.jar`

SHA-256: `6ca59fb2510ca2d47c8c1c380cfe212d0fdcb9e53388b3f7684a818c408a7879`.

## Grenzen

Die manuelle Sichtprüfung des Steuerungs-HUDs im Spiel wurde nicht durchgeführt.
Die GameTests prüfen reale gemischte Pult-BlockEntities und Multiblock-Gruppen;
Layout, Symbolzuordnung, Anbauknopf-Gruppierung und Mixin-Verträge sind automatisiert geprüft.
