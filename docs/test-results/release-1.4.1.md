# Releaseprüfung 1.4.1

Datum: 18. Oktober 2026. Minecraft 1.21.1, NeoForge 21.1.234, TeutonLib 1.2.0.

## Bestanden

- `./gradlew --offline test build`: erfolgreich; 95 Tests, keine Fehler oder übersprungenen Tests.
- Alle 31 `tools/verify-*.py`: erfolgreich, einschließlich der vier Bytecodeprüfungen mit offiziellen Downloads. Veraltete UI-Vertragsprüfungen wurden an den TeutonLib-Adapter angepasst.
- `python3 tools/dedicated-server-smoke.py --dependency-dir libs --run-dir build/smoke-server-1.4.1 --timeout 240`: Done-Marker erreicht, kein bekannter fataler Marker.
- Offizielle TeutonLib-1.2.0-JAR heruntergeladen und gegen die veröffentlichte SHA-256 geprüft.
- Release-JAR: ZIP-Integrität und Metadaten geprüft; Modversion 1.4.1, TeutonLib-Versionsbereich `[1.2.0,1.3.0)`.

## Artefakt

`build/libs/cc-aeroworks-1.4.1.jar`

SHA-256: `469c0aa3ed0ffb7c3754d3fda1e9e3fb445f67e16f92dc291eb0693f777542ef`

## Grenzen

Keine neue interaktive Client-Sichtprüfung der Hintergrundkorrektur oder manuelle Mehrspielerprüfung. Die früheren TeutonLib-UI-Prüfungen sind separat in `teutonlib-computer-ui.md` dokumentiert und wurden für diesen Release nicht erneut ausgeführt. Der Server meldet weiterhin einen Ressourcenfehler einer optionalen Drittmod-Loot-Tabelle; der Start ist erfolgreich.
