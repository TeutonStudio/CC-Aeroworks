# Releaseprüfung 1.5.5

Datum: 21. November 2026. Minecraft 1.21.1, NeoForge 21.1.251, Java 21,
CC:Tweaked 1.120.2, Create 6.0.10, Create: Aeroworks 1.5.0,
TeutonLib 1.6.0 und Sable 2.0.6.

Quellbasis: `a3e7710c23562d2fe329d6011b370109e46b23e0`, einschließlich der
Reparaturen aus `a8466f5` und der NeoForge-Umstellung aus `bc8e127`.
Für diesen Release wurden anschließend nur Version, Changelog und Dokumentation angepasst.
Der endgültige Quellstand ist über den Release-Tag `v1.5.5` verfügbar.

## Änderungen

- NeoForge ab 21.1.251; TeutonLib weiterhin `[1.6.0,1.7.0)`, Sable ab 2.0.6.
- Wiederholtes Speichern bewegter Computer erhält spätere NBT-Änderungen.
- Bewegte Pulte berücksichtigen getrennte Assemblies und Deckenreihen.
- Lua-Steuerungsskripte führen ein Eingabeereignis auch unter CraftOS `parallel` nur einmal aus.
- Gelöschte Wire-Kanäle bleiben nach Neuladen oder Computerentnahme gelöscht.
- Materialrückgaben integrierter Create-Steuerungen beachten abgebrochene Abbauereignisse,
  Kreativmodus und Contraption-Bewegungen.
- Ein Channel-Snapshot pro Override-Batch; veralteten Wire-Runtime-Zustand entfernt.
- Aktuelle zweisprachige Dokumentation vom Remote-master übernommen und Installationshinweise aktualisiert.

## Prüfungen

- `./gradlew test build runGameTestServer --offline --no-daemon --init-script build/test-results/release-1.5.5/gametest.gradle`:
  167 Unit-Tests und alle 35 erforderlichen GameTests erfolgreich.
  Das Init-Skript setzt ausschließlich `neoForge.runs.gameTestServer.gameDirectory`
  auf `build/gametest-release-1.5.5`, um eine isolierte Testwelt zu verwenden.
- Alle 32 `tools/verify-*.py` erfolgreich, einschließlich der vier Prüfungen offizieller
  Abhängigkeits-Bytecodes und `verify-wiki.py --require-all-media`.
- Beide Lua-Suites in `src/test/lua` erfolgreich.
- Separater Dedicated-Server-Smoke-Test mit `tools/dedicated-server-smoke.py`.
- Release-Metadaten aller drei Mod-IDs enthalten Version 1.5.5.
- JAR-Metadaten: NeoForge `[21.1.251,)`, TeutonLib `[1.6.0,1.7.0)`, Sable `[2.0.6,)`.
- Keine Fremdmod-JARs im Release-Artefakt eingebettet.
- `git diff --check` ohne Fehler.

Vor der Versionsanhebung wurden zusätzlich die lokalen TeutonLib-JARs 1.6.0 bis 1.6.3
jeweils mit NeoForge 21.1.251 geprüft: pro Version 167 Unit-Tests und 35 GameTests erfolgreich.
Die 13 vorhandenen TeutonLib-Versionen 1.5.3 bis 1.5.15 wurden erwartungsgemäß vom Loader abgelehnt.

## Artefakt

`build/libs/cc-aeroworks-1.5.5.jar`

SHA-256: `b11adc407e1a56d8cbec856f80d6f4b1f81991d63e3609209cab4f3067fe52f5`.

Build-Abhängigkeit `teutonlib-1.6.0.jar`, SHA-256:
`bbd46f76d579cd6f17f2c575fa503b8697fadb43c3064d04338d0ded5bcddc16`.

Die ausführlichen lokalen Nachweise liegen unter `build/test-results/release-1.5.5/`.

## Grenzen

Eine manuelle Client-Sichtprüfung im Spiel wurde für diesen Release nicht durchgeführt.
