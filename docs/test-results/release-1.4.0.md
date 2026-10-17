# Releaseprüfung 1.4.0

Datum: 18. Oktober 2026. Gemeinsamer Computerslot normaler Aeroworks-Steuereinheiten, Migration bisheriger Computerpulte, Steuermodul-Skripte und Kanalziel-Editor. Minecraft 1.21.1, NeoForge 21.1.228; Abhängigkeiten aus `libs/dependencies.json` unverändert.

## Abgeschlossene Prüfungen

- `./gradlew clean build --offline --stacktrace`: erfolgreich; 95 Unit-Tests, keine Fehler und keine übersprungenen Tests.
- Alle 31 `tools/verify-*.py`-Prüfungen erfolgreich. Die vier Bytecode-Prüfungen liefen mit Netzwerkzugriff gegen die festgelegten öffentlichen Artefakte.
- `lua src/test/lua/control_handlers_test.lua` mit Lua 5.4.8: erfolgreich; einmalige Ausführung, Callbacks, Werte, Loslassen, Routing, Filter, bestehende Ereignisse und Fehlerbehandlung geprüft.
- `./gradlew runGameTestServer --offline --stacktrace -I /tmp/cc-aeroworks-release-gametest.gradle`: erfolgreich; alle 11 erforderlichen GameTests bestanden. Das temporäre Init-Skript setzt ausschließlich das Spielverzeichnis auf `build/gametest-server-1.4.0`.
- Die GameTests prüfen echte Steuereinheiten mit aktiven Mixins: alle Varianten und Computerfamilien, Speichern/Laden, gemeinsamer Inventarslot, ungültige und konkurrierende Zugriffe, Transfer zum größeren Reststück und Gleichstand, letzter Drop, alte Blöcke/Items, Zusammenführen belegter Reihen, abgebrochener Abbau, Chunkentladen, Schraubenschlüssel und Menü-Reichweite.
- `python3 tools/dedicated-server-smoke.py --dependency-dir libs --run-dir build/smoke-server-1.4.0 --timeout 240`: erfolgreich; isolierter Dedicated-Server-Start mit optionalen Integrationen erreicht `Done` ohne bekannten fatalen Marker.
- ZIP-Integrität der JAR und Metadaten geprüft: beide Mod-Einträge tragen Version 1.4.0; keine eingebetteten Minecraft-, Aeroworks- oder Create:-Radars-Klassen.

## Artefakt

Datei: `build/libs/cc-aeroworks-1.4.0.jar`

SHA-256:

```text
e0b2cf230754a3d15f0cfe9223d269dc4b29639a4b81d42dfd9c2af1781c67f3
```

Eine passende `.sha256`-Datei liegt neben der JAR.

## Grenzen und Belege

Keine interaktive Client-Sichtprüfung von Inventar, GUI-Skalierungen, Ponder oder Kanalziel-Editor und kein manueller Mehrspielertest mit Grafikclients. Diese Punkte bleiben im [Testplan](../computer-desk-guide-test-plan.md). Automatisierte Tests ersetzen diese Sichtprüfung nicht. Bestehende Compiler-/Gradle-Deprecation-Warnungen und Ressourcenmeldungen optionaler Drittmods wurden nicht verändert.

Die Tests verwenden isolierte Verzeichnisse unter `build/`; bestehende Spielstände wurden nicht verändert. Unit-Testbelege liegen unter `build/test-results/test/` und `build/reports/tests/test/`, GameTest-Laufzeitbelege unter `build/gametest-server-1.4.0/logs/`. Der Buildlog liegt lokal unter `/tmp/cc-aeroworks-release-1.4.0-build.log`, der GameTest-Log unter `/tmp/cc-aeroworks-release-1.4.0-gametest.log`.

Serverbelege: `build/smoke-server-1.4.0/logs/`, `build/test-results/server-smoke/gradle-output.log` und lokal `/tmp/cc-aeroworks-release-1.4.0-smoke.log`.
